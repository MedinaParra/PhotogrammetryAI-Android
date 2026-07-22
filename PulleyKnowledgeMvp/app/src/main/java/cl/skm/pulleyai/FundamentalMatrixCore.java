package cl.skm.pulleyai;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

/** Normalized eight-point fundamental matrix with deterministic RANSAC and Sampson error. */
public final class FundamentalMatrixCore {
    private static final Method CHECKPOINT = findCheckpoint();

    private FundamentalMatrixCore() {
    }

    public static Result estimate(List<PointPair> pairs, double thresholdPx, int iterations) {
        if (pairs == null || pairs.size() < 8) return Result.failed("INSUFFICIENT_MATCHES");
        int n = pairs.size();
        long state = 0x6a09e667f3bcc909L ^ n;
        double threshold2 = thresholdPx * thresholdPx;
        double[][] bestF = null;
        List<Integer> bestInliers = Collections.emptyList();
        double bestError = Double.POSITIVE_INFINITY;
        int loops = Math.max(80, Math.min(2500, iterations));
        for (int it = 0; it < loops; it++) {
            if ((it & 15) == 0) checkpoint("FUNDAMENTAL_RANSAC_" + it);
            List<PointPair> sample = new ArrayList<PointPair>(8);
            int[] indices = new int[8];
            for (int k = 0; k < 8; k++) {
                int attempts = 0;
                do {
                    state = xorshift(state);
                    indices[k] = (int) Math.floorMod(state, n);
                    attempts++;
                } while (contains(indices, k, indices[k]) && attempts < n * 2);
                sample.add(pairs.get(indices[k]));
            }
            double[][] f = fit(sample, "FUNDAMENTAL_SAMPLE_FIT_" + it);
            if (f == null) continue;
            List<Integer> inliers = new ArrayList<Integer>();
            double error = 0.0;
            for (int i = 0; i < n; i++) {
                if ((i & 127) == 0) checkpoint("FUNDAMENTAL_SCORE_" + it + "_" + i);
                double d = sampsonSquared(f, pairs.get(i));
                if (Double.isFinite(d) && d <= threshold2) {
                    inliers.add(i);
                    error += d;
                }
            }
            if (inliers.size() > bestInliers.size()
                    || (inliers.size() == bestInliers.size() && error < bestError)) {
                bestF = f;
                bestInliers = inliers;
                bestError = error;
            }
        }
        checkpoint("FUNDAMENTAL_REFINEMENT_START");
        if (bestF == null || bestInliers.size() < 8) return Result.failed("NO_MODEL");
        List<PointPair> refinement = new ArrayList<PointPair>(bestInliers.size());
        for (int index : bestInliers) refinement.add(pairs.get(index));
        double[][] refined = fit(refinement, "FUNDAMENTAL_REFINEMENT_FIT");
        if (refined != null) bestF = refined;
        List<Integer> finalInliers = new ArrayList<Integer>();
        double sum = 0.0;
        for (int i = 0; i < n; i++) {
            if ((i & 127) == 0) checkpoint("FUNDAMENTAL_FINAL_SCORE_" + i);
            double d = sampsonSquared(bestF, pairs.get(i));
            if (Double.isFinite(d) && d <= threshold2) {
                finalInliers.add(i);
                sum += d;
            }
        }
        double rms = finalInliers.isEmpty() ? Double.POSITIVE_INFINITY : Math.sqrt(sum / finalInliers.size());
        double ratio = (double) finalInliers.size() / n;
        String status = finalInliers.size() >= 30 && ratio >= 0.45 && rms <= thresholdPx * 0.8
                ? "STRONG" : finalInliers.size() >= 12 && ratio >= 0.25 && rms <= thresholdPx
                ? "USABLE" : "WEAK";
        checkpoint("FUNDAMENTAL_COMPLETE");
        return new Result(true, status, bestF, finalInliers, ratio, rms);
    }

    private static double[][] fit(List<PointPair> pairs, String stage) {
        if (pairs.size() < 8) return null;
        checkpoint(stage + "_NORMALIZE");
        Normalization n1 = normalize(pairs, false, stage + "_FIRST");
        Normalization n2 = normalize(pairs, true, stage + "_SECOND");
        double[][] ata = new double[9][9];
        int pairIndex = 0;
        for (PointPair pair : pairs) {
            if ((pairIndex++ & 127) == 0) checkpoint(stage + "_ACCUMULATE_" + pairIndex);
            double x = n1.scale * pair.x + n1.tx;
            double y = n1.scale * pair.y + n1.ty;
            double u = n2.scale * pair.u + n2.tx;
            double v = n2.scale * pair.v + n2.ty;
            double[] row = {u * x, u * y, u, v * x, v * y, v, x, y, 1.0};
            for (int i = 0; i < 9; i++) {
                for (int j = i; j < 9; j++) {
                    ata[i][j] += row[i] * row[j];
                    if (i != j) ata[j][i] = ata[i][j];
                }
            }
        }
        Eigen eigen = jacobi(ata, 100, stage + "_EIGEN");
        if (eigen == null) return null;
        int smallest = 0;
        for (int i = 1; i < 9; i++) if (eigen.values[i] < eigen.values[smallest]) smallest = i;
        double[][] f = new double[3][3];
        for (int i = 0; i < 9; i++) f[i / 3][i % 3] = eigen.vectors[i][smallest];
        f = rank2(f, stage + "_RANK2");
        if (f == null) return null;
        double[][] t1 = {{n1.scale, 0, n1.tx}, {0, n1.scale, n1.ty}, {0, 0, 1}};
        double[][] t2 = {{n2.scale, 0, n2.tx}, {0, n2.scale, n2.ty}, {0, 0, 1}};
        double[][] denormalized = multiply(transpose(t2), multiply(f, t1));
        double norm = frobenius(denormalized);
        if (!Double.isFinite(norm) || norm < 1e-12) return null;
        for (int r = 0; r < 3; r++) for (int c = 0; c < 3; c++) denormalized[r][c] /= norm;
        return denormalized;
    }

    private static Normalization normalize(List<PointPair> pairs, boolean second, String stage) {
        double mx = 0.0;
        double my = 0.0;
        int index = 0;
        for (PointPair pair : pairs) {
            if ((index++ & 255) == 0) checkpoint(stage + "_MEAN_" + index);
            mx += second ? pair.u : pair.x;
            my += second ? pair.v : pair.y;
        }
        mx /= pairs.size();
        my /= pairs.size();
        double meanDistance = 0.0;
        index = 0;
        for (PointPair pair : pairs) {
            if ((index++ & 255) == 0) checkpoint(stage + "_DISTANCE_" + index);
            double x = (second ? pair.u : pair.x) - mx;
            double y = (second ? pair.v : pair.y) - my;
            meanDistance += Math.sqrt(x * x + y * y);
        }
        meanDistance /= pairs.size();
        double scale = meanDistance < 1e-9 ? 1.0 : Math.sqrt(2.0) / meanDistance;
        return new Normalization(scale, -scale * mx, -scale * my);
    }

    private static double[][] rank2(double[][] f, String stage) {
        checkpoint(stage + "_START");
        double[][] ftf = multiply(transpose(f), f);
        Eigen eigen = jacobi(ftf, 80, stage + "_EIGEN");
        if (eigen == null) return null;
        List<Integer> order = new ArrayList<Integer>();
        for (int i = 0; i < 3; i++) order.add(i);
        Collections.sort(order, new Comparator<Integer>() {
            @Override public int compare(Integer a, Integer b) {
                return Double.compare(eigen.values[b], eigen.values[a]);
            }
        });
        double[][] v = new double[3][3];
        double[] s = new double[3];
        for (int col = 0; col < 3; col++) {
            int source = order.get(col);
            s[col] = Math.sqrt(Math.max(0.0, eigen.values[source]));
            for (int row = 0; row < 3; row++) v[row][col] = eigen.vectors[row][source];
        }
        if (s[0] < 1e-12 || s[1] < 1e-12) return null;
        double[][] u = new double[3][3];
        for (int col = 0; col < 2; col++) {
            for (int row = 0; row < 3; row++) {
                u[row][col] = (f[row][0] * v[0][col] + f[row][1] * v[1][col] + f[row][2] * v[2][col]) / s[col];
            }
            normalizeColumn(u, col);
        }
        double dot = u[0][0] * u[0][1] + u[1][0] * u[1][1] + u[2][0] * u[2][1];
        for (int row = 0; row < 3; row++) u[row][1] -= dot * u[row][0];
        normalizeColumn(u, 1);
        u[0][2] = u[1][0] * u[2][1] - u[2][0] * u[1][1];
        u[1][2] = u[2][0] * u[0][1] - u[0][0] * u[2][1];
        u[2][2] = u[0][0] * u[1][1] - u[1][0] * u[0][1];
        s[2] = 0.0;
        double[][] sigma = {{s[0], 0, 0}, {0, s[1], 0}, {0, 0, 0}};
        return multiply(u, multiply(sigma, transpose(v)));
    }

    private static void normalizeColumn(double[][] matrix, int column) {
        double norm = 0.0;
        for (int row = 0; row < 3; row++) norm += matrix[row][column] * matrix[row][column];
        norm = Math.sqrt(norm);
        if (norm < 1e-12) return;
        for (int row = 0; row < 3; row++) matrix[row][column] /= norm;
    }

    public static double sampsonSquared(double[][] f, PointPair pair) {
        double fx0 = f[0][0] * pair.x + f[0][1] * pair.y + f[0][2];
        double fx1 = f[1][0] * pair.x + f[1][1] * pair.y + f[1][2];
        double fx2 = f[2][0] * pair.x + f[2][1] * pair.y + f[2][2];
        double ftu0 = f[0][0] * pair.u + f[1][0] * pair.v + f[2][0];
        double ftu1 = f[0][1] * pair.u + f[1][1] * pair.v + f[2][1];
        double numerator = pair.u * fx0 + pair.v * fx1 + fx2;
        double denominator = fx0 * fx0 + fx1 * fx1 + ftu0 * ftu0 + ftu1 * ftu1;
        return denominator < 1e-15 ? Double.POSITIVE_INFINITY : numerator * numerator / denominator;
    }

    private static Eigen jacobi(double[][] source, int sweeps, String stage) {
        int n = source.length;
        double[][] a = new double[n][n];
        double[][] v = new double[n][n];
        for (int i = 0; i < n; i++) {
            System.arraycopy(source[i], 0, a[i], 0, n);
            v[i][i] = 1.0;
        }
        int maxIterations = Math.max(20, sweeps * n * n);
        for (int iteration = 0; iteration < maxIterations; iteration++) {
            if ((iteration & 15) == 0) checkpoint(stage + "_" + iteration);
            int p = 0;
            int q = 1;
            double largest = 0.0;
            for (int i = 0; i < n; i++) {
                for (int j = i + 1; j < n; j++) {
                    double value = Math.abs(a[i][j]);
                    if (value > largest) {
                        largest = value;
                        p = i;
                        q = j;
                    }
                }
            }
            if (largest < 1e-10) break;
            double phi = 0.5 * Math.atan2(2.0 * a[p][q], a[q][q] - a[p][p]);
            double c = Math.cos(phi);
            double s = Math.sin(phi);
            double app = c * c * a[p][p] - 2.0 * s * c * a[p][q] + s * s * a[q][q];
            double aqq = s * s * a[p][p] + 2.0 * s * c * a[p][q] + c * c * a[q][q];
            for (int k = 0; k < n; k++) {
                if (k == p || k == q) continue;
                double akp = a[k][p];
                double akq = a[k][q];
                a[k][p] = a[p][k] = c * akp - s * akq;
                a[k][q] = a[q][k] = s * akp + c * akq;
            }
            a[p][p] = app;
            a[q][q] = aqq;
            a[p][q] = a[q][p] = 0.0;
            for (int k = 0; k < n; k++) {
                double vkp = v[k][p];
                double vkq = v[k][q];
                v[k][p] = c * vkp - s * vkq;
                v[k][q] = s * vkp + c * vkq;
            }
        }
        double[] values = new double[n];
        for (int i = 0; i < n; i++) values[i] = a[i][i];
        return new Eigen(values, v);
    }

    private static double[][] multiply(double[][] a, double[][] b) {
        double[][] result = new double[a.length][b[0].length];
        for (int i = 0; i < a.length; i++) {
            for (int k = 0; k < b.length; k++) {
                for (int j = 0; j < b[0].length; j++) result[i][j] += a[i][k] * b[k][j];
            }
        }
        return result;
    }

    private static double[][] transpose(double[][] matrix) {
        double[][] result = new double[matrix[0].length][matrix.length];
        for (int i = 0; i < matrix.length; i++) for (int j = 0; j < matrix[0].length; j++) result[j][i] = matrix[i][j];
        return result;
    }

    private static double frobenius(double[][] matrix) {
        double sum = 0.0;
        for (double[] row : matrix) for (double value : row) sum += value * value;
        return Math.sqrt(sum);
    }

    private static boolean contains(int[] values, int length, int target) {
        for (int i = 0; i < length; i++) if (values[i] == target) return true;
        return false;
    }

    private static long xorshift(long value) {
        value ^= value << 13;
        value ^= value >>> 7;
        value ^= value << 17;
        return value;
    }

    private static Method findCheckpoint() {
        try {
            Class<?> bridge = Class.forName("cl.skm.pulleyai.RuntimeCancellationBridge");
            return bridge.getMethod("checkpoint", String.class);
        } catch (ReflectiveOperationException ignored) {
            return null;
        }
    }

    private static void checkpoint(String stage) {
        if (CHECKPOINT == null) return;
        try {
            CHECKPOINT.invoke(null, stage);
        } catch (InvocationTargetException error) {
            Throwable cause = error.getCause();
            if (cause instanceof RuntimeException) throw (RuntimeException) cause;
            if (cause instanceof Error) throw (Error) cause;
            throw new IllegalStateException("geometry checkpoint failed", cause);
        } catch (ReflectiveOperationException error) {
            throw new IllegalStateException("geometry checkpoint unavailable", error);
        }
    }

    private static final class Normalization {
        final double scale;
        final double tx;
        final double ty;
        Normalization(double scale, double tx, double ty) { this.scale = scale; this.tx = tx; this.ty = ty; }
    }

    private static final class Eigen {
        final double[] values;
        final double[][] vectors;
        Eigen(double[] values, double[][] vectors) { this.values = values; this.vectors = vectors; }
    }

    public static final class PointPair {
        public final double x;
        public final double y;
        public final double u;
        public final double v;
        public PointPair(double x, double y, double u, double v) { this.x = x; this.y = y; this.u = u; this.v = v; }
    }

    public static final class Result {
        public final boolean solved;
        public final String status;
        public final double[][] matrix;
        public final List<Integer> inliers;
        public final double inlierRatio;
        public final double rmsPx;

        Result(boolean solved, String status, double[][] matrix, List<Integer> inliers, double inlierRatio, double rmsPx) {
            this.solved = solved;
            this.status = status;
            this.matrix = matrix;
            this.inliers = Collections.unmodifiableList(new ArrayList<Integer>(inliers));
            this.inlierRatio = inlierRatio;
            this.rmsPx = rmsPx;
        }

        static Result failed(String status) {
            return new Result(false, status, null, Collections.<Integer>emptyList(), 0.0, Double.POSITIVE_INFINITY);
        }
    }
}

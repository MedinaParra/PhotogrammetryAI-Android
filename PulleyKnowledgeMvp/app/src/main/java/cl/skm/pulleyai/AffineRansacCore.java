package cl.skm.pulleyai;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** Deterministic affine RANSAC used to reject false visual correspondences before pose estimation. */
public final class AffineRansacCore {
    private AffineRansacCore() {
    }

    public static Result estimate(List<PointPair> pairs, double thresholdPx, int iterations) {
        if (pairs == null || pairs.size() < 3) return Result.failed("INSUFFICIENT_MATCHES");
        int count = pairs.size();
        double threshold2 = thresholdPx * thresholdPx;
        long state = 0x9e3779b97f4a7c15L ^ count;
        Model best = null;
        List<Integer> bestInliers = Collections.emptyList();
        double bestError = Double.POSITIVE_INFINITY;
        int loops = Math.max(40, Math.min(1500, iterations));
        for (int iteration = 0; iteration < loops; iteration++) {
            int[] sample = new int[3];
            for (int k = 0; k < 3; k++) {
                int attempts = 0;
                do {
                    state = xorshift(state);
                    sample[k] = (int) Math.floorMod(state, count);
                    attempts++;
                } while (contains(sample, k, sample[k]) && attempts < count * 2);
            }
            Model model = fromThree(pairs.get(sample[0]), pairs.get(sample[1]), pairs.get(sample[2]));
            if (model == null || !model.plausible()) continue;
            List<Integer> inliers = new ArrayList<Integer>();
            double error = 0.0;
            for (int i = 0; i < count; i++) {
                double residual = model.residualSquared(pairs.get(i));
                if (residual <= threshold2) {
                    inliers.add(i);
                    error += residual;
                }
            }
            if (inliers.size() > bestInliers.size()
                    || (inliers.size() == bestInliers.size() && error < bestError)) {
                best = model;
                bestInliers = inliers;
                bestError = error;
            }
        }
        if (best == null || bestInliers.size() < 3) return Result.failed("NO_MODEL");
        Model refined = refine(pairs, bestInliers);
        if (refined != null && refined.plausible()) best = refined;
        List<Integer> finalInliers = new ArrayList<Integer>();
        double sum = 0.0;
        for (int i = 0; i < count; i++) {
            double residual = best.residualSquared(pairs.get(i));
            if (residual <= threshold2) {
                finalInliers.add(i);
                sum += residual;
            }
        }
        double rms = finalInliers.isEmpty() ? Double.POSITIVE_INFINITY : Math.sqrt(sum / finalInliers.size());
        double ratio = (double) finalInliers.size() / count;
        String status = finalInliers.size() >= 24 && ratio >= 0.45 && rms <= thresholdPx * 0.8
                ? "STRONG" : finalInliers.size() >= 10 && ratio >= 0.25 && rms <= thresholdPx
                ? "USABLE" : "WEAK";
        return new Result(true, status, best, finalInliers, ratio, rms);
    }

    private static Model fromThree(PointPair p0, PointPair p1, PointPair p2) {
        double[][] a = new double[6][7];
        fillRows(a, 0, p0);
        fillRows(a, 2, p1);
        fillRows(a, 4, p2);
        double[] solved = solve(a);
        return solved == null ? null : new Model(solved);
    }

    private static Model refine(List<PointPair> pairs, List<Integer> inliers) {
        double[][] normal = new double[6][7];
        for (int index : inliers) {
            PointPair pair = pairs.get(index);
            double[] rowX = {pair.x, pair.y, 1.0, 0.0, 0.0, 0.0};
            double[] rowY = {0.0, 0.0, 0.0, pair.x, pair.y, 1.0};
            accumulate(normal, rowX, pair.u);
            accumulate(normal, rowY, pair.v);
        }
        double[] solved = solve(normal);
        return solved == null ? null : new Model(solved);
    }

    private static void accumulate(double[][] normal, double[] row, double target) {
        for (int i = 0; i < 6; i++) {
            for (int j = 0; j < 6; j++) normal[i][j] += row[i] * row[j];
            normal[i][6] += row[i] * target;
        }
    }

    private static void fillRows(double[][] matrix, int row, PointPair pair) {
        matrix[row][0] = pair.x;
        matrix[row][1] = pair.y;
        matrix[row][2] = 1.0;
        matrix[row][6] = pair.u;
        matrix[row + 1][3] = pair.x;
        matrix[row + 1][4] = pair.y;
        matrix[row + 1][5] = 1.0;
        matrix[row + 1][6] = pair.v;
    }

    private static double[] solve(double[][] augmented) {
        int n = 6;
        double[][] a = new double[n][n + 1];
        for (int i = 0; i < n; i++) System.arraycopy(augmented[i], 0, a[i], 0, n + 1);
        for (int column = 0; column < n; column++) {
            int pivot = column;
            for (int row = column + 1; row < n; row++) {
                if (Math.abs(a[row][column]) > Math.abs(a[pivot][column])) pivot = row;
            }
            if (Math.abs(a[pivot][column]) < 1e-9) return null;
            double[] temp = a[column];
            a[column] = a[pivot];
            a[pivot] = temp;
            double divisor = a[column][column];
            for (int j = column; j <= n; j++) a[column][j] /= divisor;
            for (int row = 0; row < n; row++) {
                if (row == column) continue;
                double factor = a[row][column];
                for (int j = column; j <= n; j++) a[row][j] -= factor * a[column][j];
            }
        }
        double[] result = new double[n];
        for (int i = 0; i < n; i++) result[i] = a[i][n];
        return result;
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

    public static final class PointPair {
        public final double x;
        public final double y;
        public final double u;
        public final double v;

        public PointPair(double x, double y, double u, double v) {
            this.x = x;
            this.y = y;
            this.u = u;
            this.v = v;
        }
    }

    public static final class Model {
        public final double a;
        public final double b;
        public final double tx;
        public final double c;
        public final double d;
        public final double ty;

        Model(double[] values) {
            a = values[0];
            b = values[1];
            tx = values[2];
            c = values[3];
            d = values[4];
            ty = values[5];
        }

        public double residualSquared(PointPair pair) {
            double du = a * pair.x + b * pair.y + tx - pair.u;
            double dv = c * pair.x + d * pair.y + ty - pair.v;
            return du * du + dv * dv;
        }

        public double determinant() {
            return a * d - b * c;
        }

        boolean plausible() {
            double det = determinant();
            double scaleX = Math.sqrt(a * a + c * c);
            double scaleY = Math.sqrt(b * b + d * d);
            return Double.isFinite(det) && det > 0.25 && det < 4.0
                    && scaleX > 0.45 && scaleX < 2.1 && scaleY > 0.45 && scaleY < 2.1;
        }
    }

    public static final class Result {
        public final boolean solved;
        public final String status;
        public final Model model;
        public final List<Integer> inliers;
        public final double inlierRatio;
        public final double rmsPx;

        Result(boolean solved, String status, Model model, List<Integer> inliers,
               double inlierRatio, double rmsPx) {
            this.solved = solved;
            this.status = status;
            this.model = model;
            this.inliers = Collections.unmodifiableList(new ArrayList<Integer>(inliers));
            this.inlierRatio = inlierRatio;
            this.rmsPx = rmsPx;
        }

        static Result failed(String status) {
            return new Result(false, status, null, Collections.<Integer>emptyList(), 0.0,
                    Double.POSITIVE_INFINITY);
        }
    }
}

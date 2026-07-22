package cl.skm.pulleyai;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

/** Recovers calibrated two-view rotation and translation direction with a cheirality test. */
public final class EssentialPoseCore {
    private static final Method CHECKPOINT = findCheckpoint();

    private EssentialPoseCore() {
    }

    public static Result recover(double[][] fundamental, List<FundamentalMatrixCore.PointPair> pairs,
                                 List<Integer> inlierIndices, Intrinsics first, Intrinsics second) {
        if (fundamental == null || pairs == null || inlierIndices == null || inlierIndices.size() < 8
                || first == null || second == null || !first.valid() || !second.valid()) {
            return Result.failed("MISSING_INTRINSICS_OR_INLIERS");
        }
        checkpoint("ESSENTIAL_INPUT_READY");
        double[][] k1 = first.matrix();
        double[][] k2 = second.matrix();
        double[][] essential = multiply(transpose(k2), multiply(fundamental, k1));
        Svd3 svd = svd3(essential, "ESSENTIAL_INITIAL_SVD");
        if (svd == null) return Result.failed("ESSENTIAL_SVD_FAILED");
        double sigma = (svd.s[0] + svd.s[1]) * 0.5;
        double[][] corrected = multiply(svd.u,
                multiply(new double[][]{{sigma, 0, 0}, {0, sigma, 0}, {0, 0, 0}}, transpose(svd.v)));
        Svd3 poseSvd = svd3(corrected, "ESSENTIAL_POSE_SVD");
        if (poseSvd == null) return Result.failed("ESSENTIAL_DECOMPOSITION_FAILED");
        double[][] u = poseSvd.u;
        double[][] v = poseSvd.v;
        if (determinant(u) < 0) negateColumn(u, 2);
        if (determinant(v) < 0) negateColumn(v, 2);
        double[][] w = {{0, -1, 0}, {1, 0, 0}, {0, 0, 1}};
        double[][] r1 = multiply(u, multiply(w, transpose(v)));
        double[][] r2 = multiply(u, multiply(transpose(w), transpose(v)));
        if (determinant(r1) < 0) negateAll(r1);
        if (determinant(r2) < 0) negateAll(r2);
        double[] t = {u[0][2], u[1][2], u[2][2]};
        normalize(t);

        checkpoint("ESSENTIAL_CANDIDATES_START");
        Candidate[] candidates = {
                evaluate(r1, t, pairs, inlierIndices, first, second, "ESSENTIAL_CANDIDATE_R1_T"),
                evaluate(r1, negate(t), pairs, inlierIndices, first, second, "ESSENTIAL_CANDIDATE_R1_NEG_T"),
                evaluate(r2, t, pairs, inlierIndices, first, second, "ESSENTIAL_CANDIDATE_R2_T"),
                evaluate(r2, negate(t), pairs, inlierIndices, first, second, "ESSENTIAL_CANDIDATE_R2_NEG_T")
        };
        Candidate best = candidates[0];
        for (int i = 1; i < candidates.length; i++) {
            if (candidates[i].positiveCount > best.positiveCount
                    || (candidates[i].positiveCount == best.positiveCount
                    && candidates[i].medianParallaxDegrees > best.medianParallaxDegrees)) best = candidates[i];
        }
        double positiveRatio = (double) best.positiveCount / Math.max(1, best.testedCount);
        double rotationDegrees = rotationAngleDegrees(best.rotation);
        String status = best.positiveCount >= 20 && positiveRatio >= 0.70 && best.medianParallaxDegrees >= 0.35
                ? "STRONG" : best.positiveCount >= 8 && positiveRatio >= 0.55 && best.medianParallaxDegrees >= 0.15
                ? "USABLE" : "WEAK";
        checkpoint("ESSENTIAL_COMPLETE");
        return new Result(true, status, corrected, best.rotation, best.translation,
                best.positiveCount, best.testedCount, positiveRatio,
                best.medianParallaxDegrees, rotationDegrees);
    }

    private static Candidate evaluate(double[][] rotation, double[] translation,
                                      List<FundamentalMatrixCore.PointPair> pairs,
                                      List<Integer> indices, Intrinsics first, Intrinsics second,
                                      String stage) {
        int limit = Math.min(indices.size(), 80);
        int positive = 0;
        int tested = 0;
        List<Double> parallaxes = new ArrayList<Double>();
        for (int sample = 0; sample < limit; sample++) {
            if ((sample & 7) == 0) checkpoint(stage + "_" + sample);
            int index = indices.get(sample * indices.size() / limit);
            FundamentalMatrixCore.PointPair pair = pairs.get(index);
            double x1 = (pair.x - first.cx) / first.fx;
            double y1 = (pair.y - first.cy) / first.fy;
            double x2 = (pair.u - second.cx) / second.fx;
            double y2 = (pair.v - second.cy) / second.fy;
            double[] point = triangulate(x1, y1, x2, y2, rotation, translation, stage + "_DLT_" + sample);
            if (point == null) continue;
            tested++;
            double z1 = point[2];
            double z2 = rotation[2][0] * point[0] + rotation[2][1] * point[1]
                    + rotation[2][2] * point[2] + translation[2];
            if (z1 > 1e-6 && z2 > 1e-6) positive++;
            double[] ray1 = normalized(new double[]{point[0], point[1], point[2]});
            double[] camera2Center = multiplyTranspose(rotation,
                    new double[]{-translation[0], -translation[1], -translation[2]});
            double[] ray2 = normalized(new double[]{point[0] - camera2Center[0],
                    point[1] - camera2Center[1], point[2] - camera2Center[2]});
            double dot = clamp(ray1[0] * ray2[0] + ray1[1] * ray2[1] + ray1[2] * ray2[2], -1.0, 1.0);
            parallaxes.add(Math.toDegrees(Math.acos(dot)));
        }
        return new Candidate(rotation, translation.clone(), positive, tested, median(parallaxes));
    }

    private static double[] triangulate(double x1, double y1, double x2, double y2,
                                        double[][] r, double[] t, String stage) {
        checkpoint(stage + "_START");
        double[][] a = new double[4][4];
        a[0][0] = -1; a[0][2] = x1;
        a[1][1] = -1; a[1][2] = y1;
        for (int c = 0; c < 3; c++) {
            a[2][c] = x2 * r[2][c] - r[0][c];
            a[3][c] = y2 * r[2][c] - r[1][c];
        }
        a[2][3] = x2 * t[2] - t[0];
        a[3][3] = y2 * t[2] - t[1];
        double[][] ata = multiply(transpose(a), a);
        Eigen eigen = jacobi(ata, 80, stage + "_EIGEN");
        if (eigen == null) return null;
        int smallest = 0;
        for (int i = 1; i < 4; i++) if (eigen.values[i] < eigen.values[smallest]) smallest = i;
        double w = eigen.vectors[3][smallest];
        if (Math.abs(w) < 1e-10) return null;
        return new double[]{eigen.vectors[0][smallest] / w,
                eigen.vectors[1][smallest] / w, eigen.vectors[2][smallest] / w};
    }

    private static Svd3 svd3(double[][] matrix, String stage) {
        checkpoint(stage + "_START");
        double[][] mtm = multiply(transpose(matrix), matrix);
        Eigen eigen = jacobi(mtm, 100, stage + "_EIGEN");
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
        double[][] u = new double[3][3];
        for (int col = 0; col < 2; col++) {
            if (s[col] < 1e-12) return null;
            for (int row = 0; row < 3; row++) {
                u[row][col] = (matrix[row][0] * v[0][col] + matrix[row][1] * v[1][col]
                        + matrix[row][2] * v[2][col]) / s[col];
            }
            normalizeColumn(u, col);
        }
        double dot = u[0][0] * u[0][1] + u[1][0] * u[1][1] + u[2][0] * u[2][1];
        for (int row = 0; row < 3; row++) u[row][1] -= dot * u[row][0];
        normalizeColumn(u, 1);
        u[0][2] = u[1][0] * u[2][1] - u[2][0] * u[1][1];
        u[1][2] = u[2][0] * u[0][1] - u[0][0] * u[2][1];
        u[2][2] = u[0][0] * u[1][1] - u[1][0] * u[0][1];
        normalizeColumn(u, 2);
        return new Svd3(u, s, v);
    }

    private static double rotationAngleDegrees(double[][] r) {
        double value = clamp((r[0][0] + r[1][1] + r[2][2] - 1.0) * 0.5, -1.0, 1.0);
        return Math.toDegrees(Math.acos(value));
    }

    private static double determinant(double[][] m) {
        return m[0][0] * (m[1][1] * m[2][2] - m[1][2] * m[2][1])
                - m[0][1] * (m[1][0] * m[2][2] - m[1][2] * m[2][0])
                + m[0][2] * (m[1][0] * m[2][1] - m[1][1] * m[2][0]);
    }

    private static void negateColumn(double[][] m, int col) {
        for (int row = 0; row < m.length; row++) m[row][col] = -m[row][col];
    }

    private static void negateAll(double[][] m) {
        for (int row = 0; row < m.length; row++)
            for (int col = 0; col < m[0].length; col++) m[row][col] = -m[row][col];
    }

    private static double[] negate(double[] v) {
        return new double[]{-v[0], -v[1], -v[2]};
    }

    private static double[] multiplyTranspose(double[][] r, double[] v) {
        return new double[]{r[0][0] * v[0] + r[1][0] * v[1] + r[2][0] * v[2],
                r[0][1] * v[0] + r[1][1] * v[1] + r[2][1] * v[2],
                r[0][2] * v[0] + r[1][2] * v[1] + r[2][2] * v[2]};
    }

    private static double[] normalized(double[] v) {
        double[] copy = v.clone();
        normalize(copy);
        return copy;
    }

    private static void normalize(double[] v) {
        double norm = Math.sqrt(v[0] * v[0] + v[1] * v[1] + v[2] * v[2]);
        if (norm < 1e-12) return;
        v[0] /= norm; v[1] /= norm; v[2] /= norm;
    }

    private static void normalizeColumn(double[][] m, int col) {
        double norm = Math.sqrt(m[0][col] * m[0][col] + m[1][col] * m[1][col]
                + m[2][col] * m[2][col]);
        if (norm < 1e-12) return;
        for (int row = 0; row < 3; row++) m[row][col] /= norm;
    }

    private static double clamp(double value, double min, double max) {
        return Math.max(min, Math.min(max, value));
    }

    private static double median(List<Double> values) {
        if (values.isEmpty()) return 0.0;
        Collections.sort(values);
        int mid = values.size() / 2;
        return values.size() % 2 == 0
                ? (values.get(mid - 1) + values.get(mid)) * 0.5 : values.get(mid);
    }

    private static Eigen jacobi(double[][] source, int sweeps, String stage) {
        int n = source.length;
        double[][] a = new double[n][n];
        double[][] v = new double[n][n];
        for (int i = 0; i < n; i++) {
            System.arraycopy(source[i], 0, a[i], 0, n);
            v[i][i] = 1.0;
        }
        int max = Math.max(20, sweeps * n * n);
        for (int iteration = 0; iteration < max; iteration++) {
            if ((iteration & 15) == 0) checkpoint(stage + "_" + iteration);
            int p = 0, q = 1;
            double largest = 0.0;
            for (int i = 0; i < n; i++) {
                for (int j = i + 1; j < n; j++) {
                    double value = Math.abs(a[i][j]);
                    if (value > largest) { largest = value; p = i; q = j; }
                }
            }
            if (largest < 1e-11) break;
            double phi = 0.5 * Math.atan2(2.0 * a[p][q], a[q][q] - a[p][p]);
            double c = Math.cos(phi), s = Math.sin(phi);
            double app = c * c * a[p][p] - 2 * s * c * a[p][q] + s * s * a[q][q];
            double aqq = s * s * a[p][p] + 2 * s * c * a[p][q] + c * c * a[q][q];
            for (int k = 0; k < n; k++) {
                if (k == p || k == q) continue;
                double akp = a[k][p], akq = a[k][q];
                a[k][p] = a[p][k] = c * akp - s * akq;
                a[k][q] = a[q][k] = s * akp + c * akq;
            }
            a[p][p] = app; a[q][q] = aqq; a[p][q] = a[q][p] = 0.0;
            for (int k = 0; k < n; k++) {
                double vkp = v[k][p], vkq = v[k][q];
                v[k][p] = c * vkp - s * vkq;
                v[k][q] = s * vkp + c * vkq;
            }
        }
        double[] values = new double[n];
        for (int i = 0; i < n; i++) values[i] = a[i][i];
        return new Eigen(values, v);
    }

    private static double[][] multiply(double[][] a, double[][] b) {
        double[][] out = new double[a.length][b[0].length];
        for (int i = 0; i < a.length; i++)
            for (int k = 0; k < b.length; k++)
                for (int j = 0; j < b[0].length; j++) out[i][j] += a[i][k] * b[k][j];
        return out;
    }

    private static double[][] transpose(double[][] m) {
        double[][] out = new double[m[0].length][m.length];
        for (int i = 0; i < m.length; i++)
            for (int j = 0; j < m[0].length; j++) out[j][i] = m[i][j];
        return out;
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

    public static final class Intrinsics {
        public final double fx, fy, cx, cy;
        public Intrinsics(double fx, double fy, double cx, double cy) {
            this.fx = fx; this.fy = fy; this.cx = cx; this.cy = cy;
        }
        public boolean valid() {
            return fx > 1 && fy > 1 && Double.isFinite(fx) && Double.isFinite(fy);
        }
        double[][] matrix() {
            return new double[][]{{fx, 0, cx}, {0, fy, cy}, {0, 0, 1}};
        }
    }

    private static final class Svd3 {
        final double[][] u; final double[] s; final double[][] v;
        Svd3(double[][] u, double[] s, double[][] v) { this.u = u; this.s = s; this.v = v; }
    }

    private static final class Eigen {
        final double[] values; final double[][] vectors;
        Eigen(double[] values, double[][] vectors) { this.values = values; this.vectors = vectors; }
    }

    private static final class Candidate {
        final double[][] rotation; final double[] translation;
        final int positiveCount; final int testedCount; final double medianParallaxDegrees;
        Candidate(double[][] rotation, double[] translation, int positiveCount,
                  int testedCount, double medianParallaxDegrees) {
            this.rotation = rotation; this.translation = translation;
            this.positiveCount = positiveCount; this.testedCount = testedCount;
            this.medianParallaxDegrees = medianParallaxDegrees;
        }
    }

    public static final class Result {
        public final boolean solved;
        public final String status;
        public final double[][] essential;
        public final double[][] rotation;
        public final double[] translation;
        public final int positiveCount;
        public final int testedCount;
        public final double positiveRatio;
        public final double medianParallaxDegrees;
        public final double rotationDegrees;

        Result(boolean solved, String status, double[][] essential, double[][] rotation,
               double[] translation, int positiveCount, int testedCount,
               double positiveRatio, double medianParallaxDegrees, double rotationDegrees) {
            this.solved = solved; this.status = status; this.essential = essential;
            this.rotation = rotation; this.translation = translation;
            this.positiveCount = positiveCount; this.testedCount = testedCount;
            this.positiveRatio = positiveRatio;
            this.medianParallaxDegrees = medianParallaxDegrees;
            this.rotationDegrees = rotationDegrees;
        }

        static Result failed(String status) {
            return new Result(false, status, null, null, null, 0, 0, 0, 0, 0);
        }
    }
}

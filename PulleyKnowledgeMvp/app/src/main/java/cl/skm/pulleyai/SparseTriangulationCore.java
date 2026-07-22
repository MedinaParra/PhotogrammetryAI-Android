package cl.skm.pulleyai;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** Triangulates calibrated two-view inliers and filters them by depth, parallax and reprojection. */
public final class SparseTriangulationCore {
    private static final Method CHECKPOINT = findCheckpoint();

    private SparseTriangulationCore() {
    }

    public static Result triangulate(List<FundamentalMatrixCore.PointPair> pairs,
                                     List<Integer> inlierIndices,
                                     EssentialPoseCore.Intrinsics first,
                                     EssentialPoseCore.Intrinsics second,
                                     EssentialPoseCore.Result pose,
                                     double maxReprojectionPx) {
        if (pairs == null || inlierIndices == null || first == null || second == null
                || pose == null || !pose.solved || pose.rotation == null
                || pose.translation == null || inlierIndices.size() < 4) {
            return Result.failed("MISSING_POSE_OR_INLIERS");
        }
        checkpoint("SPARSE_TRIANGULATION_START");
        List<Point3> points = new ArrayList<Point3>();
        double errorSum = 0.0;
        int tested = 0;
        int positive = 0;
        int sequence = 0;
        for (int index : inlierIndices) {
            if ((sequence++ & 15) == 0) checkpoint("SPARSE_TRIANGULATION_POINT_" + sequence);
            if (index < 0 || index >= pairs.size()) continue;
            tested++;
            FundamentalMatrixCore.PointPair pair = pairs.get(index);
            double x1 = (pair.x - first.cx) / first.fx;
            double y1 = (pair.y - first.cy) / first.fy;
            double x2 = (pair.u - second.cx) / second.fx;
            double y2 = (pair.v - second.cy) / second.fy;
            double[] point = dlt(x1, y1, x2, y2, pose.rotation, pose.translation,
                    "SPARSE_DLT_" + sequence);
            if (point == null || !finite(point)) continue;
            double[] inSecond = transform(pose.rotation, pose.translation, point);
            if (point[2] <= 1e-7 || inSecond[2] <= 1e-7) continue;
            positive++;
            double parallax = parallaxDegrees(point, pose.rotation, pose.translation);
            if (parallax < 0.12) continue;
            double error = reprojectionError(pair, point, inSecond, first, second);
            if (!Double.isFinite(error) || error > maxReprojectionPx) continue;
            points.add(new Point3(point[0], point[1], point[2], error, parallax, index));
            errorSum += error * error;
        }
        double rms = points.isEmpty() ? Double.POSITIVE_INFINITY
                : Math.sqrt(errorSum / points.size());
        double positiveRatio = tested == 0 ? 0.0 : (double) positive / tested;
        String status = points.size() >= 24 && positiveRatio >= 0.70
                && rms <= maxReprojectionPx * 0.65 ? "STRONG"
                : points.size() >= 10 && positiveRatio >= 0.55
                && rms <= maxReprojectionPx ? "USABLE" : "WEAK";
        checkpoint("SPARSE_TRIANGULATION_COMPLETE");
        return new Result(true, status, tested, positive, positiveRatio, rms, points);
    }

    private static double[] dlt(double x1, double y1, double x2, double y2,
                                double[][] rotation, double[] translation, String stage) {
        checkpoint(stage + "_START");
        double[][] a = new double[4][4];
        a[0][0] = -1.0;
        a[0][2] = x1;
        a[1][1] = -1.0;
        a[1][2] = y1;
        for (int c = 0; c < 3; c++) {
            a[2][c] = x2 * rotation[2][c] - rotation[0][c];
            a[3][c] = y2 * rotation[2][c] - rotation[1][c];
        }
        a[2][3] = x2 * translation[2] - translation[0];
        a[3][3] = y2 * translation[2] - translation[1];
        double[][] ata = multiply(transpose(a), a);
        Eigen eigen = jacobi(ata, 100, stage + "_EIGEN");
        if (eigen == null) return null;
        int smallest = 0;
        for (int i = 1; i < 4; i++) {
            if (eigen.values[i] < eigen.values[smallest]) smallest = i;
        }
        double w = eigen.vectors[3][smallest];
        if (Math.abs(w) < 1e-11) return null;
        return new double[]{eigen.vectors[0][smallest] / w,
                eigen.vectors[1][smallest] / w,
                eigen.vectors[2][smallest] / w};
    }

    private static double reprojectionError(FundamentalMatrixCore.PointPair observation,
                                            double[] firstPoint, double[] secondPoint,
                                            EssentialPoseCore.Intrinsics first,
                                            EssentialPoseCore.Intrinsics second) {
        double u1 = first.fx * firstPoint[0] / firstPoint[2] + first.cx;
        double v1 = first.fy * firstPoint[1] / firstPoint[2] + first.cy;
        double u2 = second.fx * secondPoint[0] / secondPoint[2] + second.cx;
        double v2 = second.fy * secondPoint[1] / secondPoint[2] + second.cy;
        double e1 = squared(u1 - observation.x, v1 - observation.y);
        double e2 = squared(u2 - observation.u, v2 - observation.v);
        return Math.sqrt((e1 + e2) * 0.5);
    }

    private static double parallaxDegrees(double[] point, double[][] rotation,
                                          double[] translation) {
        double[] camera2Center = multiplyTranspose(rotation,
                new double[]{-translation[0], -translation[1], -translation[2]});
        double[] firstRay = normalized(point);
        double[] secondRay = normalized(new double[]{point[0] - camera2Center[0],
                point[1] - camera2Center[1], point[2] - camera2Center[2]});
        double dot = clamp(firstRay[0] * secondRay[0]
                + firstRay[1] * secondRay[1] + firstRay[2] * secondRay[2], -1.0, 1.0);
        return Math.toDegrees(Math.acos(dot));
    }

    private static double[] transform(double[][] rotation, double[] translation, double[] point) {
        return new double[]{rotation[0][0] * point[0] + rotation[0][1] * point[1]
                + rotation[0][2] * point[2] + translation[0],
                rotation[1][0] * point[0] + rotation[1][1] * point[1]
                        + rotation[1][2] * point[2] + translation[1],
                rotation[2][0] * point[0] + rotation[2][1] * point[1]
                        + rotation[2][2] * point[2] + translation[2]};
    }

    private static boolean finite(double[] values) {
        for (double value : values) if (!Double.isFinite(value)) return false;
        return true;
    }

    private static double squared(double x, double y) {
        return x * x + y * y;
    }

    private static double[] normalized(double[] source) {
        double norm = Math.sqrt(source[0] * source[0] + source[1] * source[1]
                + source[2] * source[2]);
        if (norm < 1e-12) return new double[]{0, 0, 0};
        return new double[]{source[0] / norm, source[1] / norm, source[2] / norm};
    }

    private static double[] multiplyTranspose(double[][] rotation, double[] vector) {
        return new double[]{rotation[0][0] * vector[0] + rotation[1][0] * vector[1]
                + rotation[2][0] * vector[2],
                rotation[0][1] * vector[0] + rotation[1][1] * vector[1]
                        + rotation[2][1] * vector[2],
                rotation[0][2] * vector[0] + rotation[1][2] * vector[1]
                        + rotation[2][2] * vector[2]};
    }

    private static double clamp(double value, double minimum, double maximum) {
        return Math.max(minimum, Math.min(maximum, value));
    }

    private static Eigen jacobi(double[][] source, int sweeps, String stage) {
        int n = source.length;
        double[][] matrix = new double[n][n];
        double[][] vectors = new double[n][n];
        for (int i = 0; i < n; i++) {
            System.arraycopy(source[i], 0, matrix[i], 0, n);
            vectors[i][i] = 1.0;
        }
        for (int iteration = 0; iteration < sweeps * n * n; iteration++) {
            if ((iteration & 15) == 0) checkpoint(stage + "_" + iteration);
            int p = 0;
            int q = 1;
            double largest = 0.0;
            for (int row = 0; row < n; row++) {
                for (int column = row + 1; column < n; column++) {
                    double candidate = Math.abs(matrix[row][column]);
                    if (candidate > largest) {
                        largest = candidate;
                        p = row;
                        q = column;
                    }
                }
            }
            if (largest < 1e-11) break;
            double angle = 0.5 * Math.atan2(2.0 * matrix[p][q],
                    matrix[q][q] - matrix[p][p]);
            double cosine = Math.cos(angle);
            double sine = Math.sin(angle);
            double pp = cosine * cosine * matrix[p][p]
                    - 2.0 * sine * cosine * matrix[p][q]
                    + sine * sine * matrix[q][q];
            double qq = sine * sine * matrix[p][p]
                    + 2.0 * sine * cosine * matrix[p][q]
                    + cosine * cosine * matrix[q][q];
            for (int k = 0; k < n; k++) {
                if (k == p || k == q) continue;
                double kp = matrix[k][p];
                double kq = matrix[k][q];
                matrix[k][p] = matrix[p][k] = cosine * kp - sine * kq;
                matrix[k][q] = matrix[q][k] = sine * kp + cosine * kq;
            }
            matrix[p][p] = pp;
            matrix[q][q] = qq;
            matrix[p][q] = matrix[q][p] = 0.0;
            for (int k = 0; k < n; k++) {
                double vp = vectors[k][p];
                double vq = vectors[k][q];
                vectors[k][p] = cosine * vp - sine * vq;
                vectors[k][q] = sine * vp + cosine * vq;
            }
        }
        double[] values = new double[n];
        for (int i = 0; i < n; i++) values[i] = matrix[i][i];
        return new Eigen(values, vectors);
    }

    private static double[][] transpose(double[][] matrix) {
        double[][] result = new double[matrix[0].length][matrix.length];
        for (int row = 0; row < matrix.length; row++) {
            for (int column = 0; column < matrix[0].length; column++) {
                result[column][row] = matrix[row][column];
            }
        }
        return result;
    }

    private static double[][] multiply(double[][] left, double[][] right) {
        double[][] result = new double[left.length][right[0].length];
        for (int row = 0; row < left.length; row++) {
            for (int shared = 0; shared < right.length; shared++) {
                for (int column = 0; column < right[0].length; column++) {
                    result[row][column] += left[row][shared] * right[shared][column];
                }
            }
        }
        return result;
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

    private static final class Eigen {
        final double[] values;
        final double[][] vectors;
        Eigen(double[] values, double[][] vectors) {
            this.values = values;
            this.vectors = vectors;
        }
    }

    public static final class Point3 {
        public final double x;
        public final double y;
        public final double z;
        public final double reprojectionErrorPx;
        public final double parallaxDegrees;
        public final int sourcePairIndex;

        Point3(double x, double y, double z, double reprojectionErrorPx,
               double parallaxDegrees, int sourcePairIndex) {
            this.x = x;
            this.y = y;
            this.z = z;
            this.reprojectionErrorPx = reprojectionErrorPx;
            this.parallaxDegrees = parallaxDegrees;
            this.sourcePairIndex = sourcePairIndex;
        }
    }

    public static final class Result {
        public final boolean solved;
        public final String status;
        public final int testedCount;
        public final int positiveDepthCount;
        public final double positiveDepthRatio;
        public final double rmsReprojectionPx;
        public final List<Point3> points;

        Result(boolean solved, String status, int testedCount, int positiveDepthCount,
               double positiveDepthRatio, double rmsReprojectionPx, List<Point3> points) {
            this.solved = solved;
            this.status = status;
            this.testedCount = testedCount;
            this.positiveDepthCount = positiveDepthCount;
            this.positiveDepthRatio = positiveDepthRatio;
            this.rmsReprojectionPx = rmsReprojectionPx;
            this.points = Collections.unmodifiableList(new ArrayList<Point3>(points));
        }

        static Result failed(String status) {
            return new Result(false, status, 0, 0, 0.0,
                    Double.POSITIVE_INFINITY, Collections.<Point3>emptyList());
        }
    }
}

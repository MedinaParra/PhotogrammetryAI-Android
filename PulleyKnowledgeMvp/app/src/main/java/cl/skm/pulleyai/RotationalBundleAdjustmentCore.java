package cl.skm.pulleyai;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Conservative second-stage local BA. It keeps camera 0 fixed, holds intrinsics fixed,
 * bounds small camera rotations and falls back to the stable translation/point result.
 */
public final class RotationalBundleAdjustmentCore {
    public static final String STATISTICAL_LABEL =
            "EMPIRICAL_RESIDUAL_INTERVAL_NOT_METROLOGICAL";

    private RotationalBundleAdjustmentCore() {}

    public static Result optimize(LocalBundleAdjustmentCore.Problem problem,
                                  PhotogrammetrySafetyGateCore.Result admission) {
        return optimize(problem, admission, 12, 8, 2.0, 0.02,
                2500.0, 3.0, 0.35);
    }

    public static Result optimize(LocalBundleAdjustmentCore.Problem problem,
                                  PhotogrammetrySafetyGateCore.Result admission,
                                  int baseIterations,
                                  int rotationIterations,
                                  double huberPx,
                                  double translationPriorWeight,
                                  double rotationPriorWeight,
                                  double maximumRotationDegrees,
                                  double maximumStepDegrees) {
        LocalBundleAdjustmentCore.Result base = LocalBundleAdjustmentCore.optimize(
                problem, admission, baseIterations, huberPx, translationPriorWeight);
        if (!base.solved || !base.ready()) {
            return Result.baseOnly("BASE_" + base.status, base,
                    ResidualStatistics.from(base.cameras, base.points,
                            problem == null ? Collections.<LocalBundleAdjustmentCore.Observation>emptyList()
                                    : problem.observations));
        }

        int iterations = Math.max(1, Math.min(12, rotationIterations));
        double huber = Math.max(0.5, Math.min(8.0, huberPx));
        double priorWeight = Math.max(1e-3, Math.min(1e7, rotationPriorWeight));
        double maximumRadians = Math.toRadians(Math.max(0.10,
                Math.min(5.0, maximumRotationDegrees)));
        double maximumStepRadians = Math.toRadians(Math.max(0.02,
                Math.min(0.75, maximumStepDegrees)));

        List<LocalBundleAdjustmentCore.Camera> priorCameras = copyCameras(base.cameras);
        List<double[]> centers = new ArrayList<double[]>();
        List<double[]> offsets = new ArrayList<double[]>();
        for (LocalBundleAdjustmentCore.Camera camera : priorCameras) {
            centers.add(cameraCenter(camera));
            offsets.add(new double[]{0.0, 0.0, 0.0});
        }
        List<List<LocalBundleAdjustmentCore.Observation>> byCamera =
                groupByCamera(priorCameras.size(), problem.observations);
        List<LocalBundleAdjustmentCore.Camera> cameras = copyCameras(priorCameras);
        List<LocalBundleAdjustmentCore.Camera> bestCameras = copyCameras(cameras);
        Score baseScore = score(base.cameras, base.points, problem.observations, huber);
        Score bestScore = score(cameras, base.points, problem.observations, huber);
        double damping = 10.0;
        int accepted = 0;
        int used = 0;

        for (int iteration = 0; iteration < iterations; iteration++) {
            used = iteration + 1;
            List<double[]> candidateOffsets = copyVectors(offsets);
            List<LocalBundleAdjustmentCore.Camera> candidateCameras = copyCameras(cameras);
            boolean proposed = false;
            for (int cameraIndex = 1; cameraIndex < candidateCameras.size(); cameraIndex++) {
                List<LocalBundleAdjustmentCore.Observation> local = byCamera.get(cameraIndex);
                if (local.size() < 6) continue;
                double[] delta = solveRotationIncrement(candidateCameras.get(cameraIndex),
                        base.points, local, candidateOffsets.get(cameraIndex),
                        huber, priorWeight, damping);
                if (delta == null) continue;
                clampNorm(delta, maximumStepRadians);
                double[] next = add(candidateOffsets.get(cameraIndex), delta);
                clampNorm(next, maximumRadians);
                candidateOffsets.set(cameraIndex, next);
                candidateCameras.set(cameraIndex, cameraFromOffset(
                        priorCameras.get(cameraIndex), centers.get(cameraIndex), next));
                proposed = true;
            }
            if (!proposed) break;
            Score candidate = score(candidateCameras, base.points, problem.observations, huber);
            boolean depthSafe = candidate.positiveDepthRatio >= 0.80
                    && candidate.positiveDepthRatio + 0.005 >= bestScore.positiveDepthRatio;
            if (candidate.finite() && depthSafe
                    && candidate.robustCost < bestScore.robustCost - 1e-9) {
                cameras = candidateCameras;
                offsets = candidateOffsets;
                bestCameras = copyCameras(cameras);
                bestScore = candidate;
                accepted++;
                damping = Math.max(1e-5, damping * 0.45);
            } else {
                damping = Math.min(1e8, damping * 8.0);
            }
        }

        LocalBundleAdjustmentCore.Result rebalanced = LocalBundleAdjustmentCore.optimize(
                new LocalBundleAdjustmentCore.Problem(bestCameras, base.points,
                        problem.observations), admission, 8, huber, translationPriorWeight);
        List<LocalBundleAdjustmentCore.Camera> finalCameras = bestCameras;
        List<LocalBundleAdjustmentCore.Point3> finalPoints = base.points;
        Score finalScore = bestScore;
        int extraIterations = 0;
        int extraAccepted = 0;
        if (rebalanced.solved) {
            Score rebalancedScore = score(rebalanced.cameras, rebalanced.points,
                    problem.observations, huber);
            if (rebalancedScore.finite()
                    && rebalancedScore.robustCost <= finalScore.robustCost
                    && rebalancedScore.positiveDepthRatio + 0.005
                    >= finalScore.positiveDepthRatio) {
                finalCameras = rebalanced.cameras;
                finalPoints = rebalanced.points;
                finalScore = rebalancedScore;
                extraIterations = rebalanced.iterations;
                extraAccepted = rebalanced.acceptedIterations;
            }
        }

        RotationSummary rotations = rotationSummary(priorCameras, finalCameras);
        double requiredRmsGain = Math.max(0.01, base.finalRmsPx * 0.003);
        boolean improved = accepted > 0
                && finalScore.finite()
                && finalScore.robustCost < baseScore.robustCost * 0.995
                && finalScore.rmsPx <= base.finalRmsPx - requiredRmsGain
                && finalScore.positiveDepthRatio >= 0.80
                && finalScore.positiveDepthRatio + 0.01 >= base.positiveDepthRatio
                && rotations.maximumDegrees <= Math.toDegrees(maximumRadians) + 1e-9
                && sameCamera(base.cameras.get(0), finalCameras.get(0), 1e-12);

        ResidualStatistics residuals = ResidualStatistics.from(
                improved ? finalCameras : base.cameras,
                improved ? finalPoints : base.points,
                problem.observations);
        if (!improved) {
            String status = accepted == 0 ? "ROTATION_STALLED"
                    : finalScore.positiveDepthRatio + 0.01 < base.positiveDepthRatio
                    ? "ROTATION_REJECTED_DEPTH" : "ROTATION_REJECTED_GAIN";
            return new Result(true, status, false, base, base,
                    used, accepted, 0.0, 0.0,
                    base.finalRmsPx, base.finalRmsPx,
                    base.positiveDepthRatio, residuals);
        }

        double overallImprovement = base.initialRmsPx > 0.0
                ? Math.max(0.0, (base.initialRmsPx - finalScore.rmsPx) / base.initialRmsPx)
                : 0.0;
        String baStatus = finalScore.rmsPx <= base.initialRmsPx * 0.70
                && finalScore.rmsPx <= 2.0 ? "CONVERGED" : "IMPROVED";
        LocalBundleAdjustmentCore.Result combined = new LocalBundleAdjustmentCore.Result(
                true, baStatus, finalCameras, finalPoints,
                base.initialRmsPx, finalScore.rmsPx,
                base.initialMedianPx, finalScore.medianPx,
                finalScore.p90Px, overallImprovement,
                finalScore.positiveDepthRatio, finalScore.validObservations,
                base.iterations + used + extraIterations,
                base.acceptedIterations + accepted + extraAccepted,
                finalScore.robustCost, base.fixedGaugeTranslation);
        return new Result(true, "ROTATION_ACCEPTED", true, base, combined,
                used, accepted, rotations.maximumDegrees, rotations.medianDegrees,
                base.finalRmsPx, finalScore.rmsPx,
                finalScore.positiveDepthRatio, residuals);
    }

    private static double[] solveRotationIncrement(
            LocalBundleAdjustmentCore.Camera camera,
            List<LocalBundleAdjustmentCore.Point3> points,
            List<LocalBundleAdjustmentCore.Observation> observations,
            double[] currentOffset,
            double huber,
            double priorWeight,
            double damping) {
        double[][] normal = new double[3][3];
        double[] rhs = new double[3];
        int valid = 0;
        for (LocalBundleAdjustmentCore.Observation observation : observations) {
            LocalBundleAdjustmentCore.Point3 point = points.get(observation.pointIndex);
            Projection projection = project(camera, point);
            if (projection == null || projection.z <= 1e-7) continue;
            valid++;
            double ru = observation.u - projection.u;
            double rv = observation.v - projection.v;
            double weight = observation.weight
                    * huberWeight(Math.hypot(ru, rv), huber);
            double x = projection.x, y = projection.y, z = projection.z;
            double z2 = z * z;
            double[] ju = new double[]{
                    -camera.fx * x * y / z2,
                    camera.fx * (z2 + x * x) / z2,
                    -camera.fx * y / z};
            double[] jv = new double[]{
                    -camera.fy * (z2 + y * y) / z2,
                    camera.fy * x * y / z2,
                    camera.fy * x / z};
            accumulate(normal, rhs, ju, ru, weight);
            accumulate(normal, rhs, jv, rv, weight);
        }
        if (valid < 6) return null;
        for (int axis = 0; axis < 3; axis++) {
            normal[axis][axis] += damping + priorWeight;
            rhs[axis] += -priorWeight * currentOffset[axis];
        }
        double[] delta = solve3(normal, rhs);
        return delta == null || !finite(delta) ? null : delta;
    }

    private static LocalBundleAdjustmentCore.Camera cameraFromOffset(
            LocalBundleAdjustmentCore.Camera prior, double[] center, double[] offset) {
        double[][] rotation = multiply(exp(offset), prior.rotation);
        double[] rc = multiply(rotation, center);
        double[] translation = new double[]{-rc[0], -rc[1], -rc[2]};
        return new LocalBundleAdjustmentCore.Camera(rotation, translation,
                prior.fx, prior.fy, prior.cx, prior.cy);
    }

    private static RotationSummary rotationSummary(
            List<LocalBundleAdjustmentCore.Camera> prior,
            List<LocalBundleAdjustmentCore.Camera> current) {
        List<Double> angles = new ArrayList<Double>();
        double maximum = 0.0;
        for (int i = 1; i < Math.min(prior.size(), current.size()); i++) {
            double angle = rotationDistanceDegrees(current.get(i).rotation,
                    prior.get(i).rotation);
            angles.add(angle);
            maximum = Math.max(maximum, angle);
        }
        return new RotationSummary(maximum, percentile(angles, 0.5));
    }

    private static double rotationDistanceDegrees(double[][] a, double[][] b) {
        double[][] relative = multiply(a, transpose(b));
        double cosine = (relative[0][0] + relative[1][1] + relative[2][2] - 1.0) * 0.5;
        cosine = Math.max(-1.0, Math.min(1.0, cosine));
        return Math.toDegrees(Math.acos(cosine));
    }

    private static Score score(List<LocalBundleAdjustmentCore.Camera> cameras,
                               List<LocalBundleAdjustmentCore.Point3> points,
                               List<LocalBundleAdjustmentCore.Observation> observations,
                               double huber) {
        List<Double> errors = new ArrayList<Double>();
        double robust = 0.0;
        int positive = 0;
        for (LocalBundleAdjustmentCore.Observation observation : observations) {
            Projection projection = project(cameras.get(observation.cameraIndex),
                    points.get(observation.pointIndex));
            if (projection == null) continue;
            if (projection.z > 1e-7) positive++;
            double error = Math.hypot(projection.u - observation.u,
                    projection.v - observation.v);
            if (!Double.isFinite(error)) continue;
            errors.add(error);
            robust += observation.weight * huberLoss(error, huber);
        }
        if (errors.isEmpty()) return Score.invalid();
        double median = percentile(errors, 0.5);
        double threshold = Math.max(huber * 3.0, median * 3.5 + 0.5);
        double squared = 0.0;
        int inliers = 0;
        for (double error : errors) {
            if (error <= threshold) {
                squared += error * error;
                inliers++;
            }
        }
        double rms = inliers == 0 ? Double.POSITIVE_INFINITY
                : Math.sqrt(squared / inliers);
        return new Score(rms, median, percentile(errors, 0.90),
                robust / errors.size(), errors.size(),
                positive / (double) observations.size());
    }

    private static Projection project(LocalBundleAdjustmentCore.Camera camera,
                                      LocalBundleAdjustmentCore.Point3 point) {
        double x = camera.rotation[0][0] * point.x
                + camera.rotation[0][1] * point.y
                + camera.rotation[0][2] * point.z + camera.translation[0];
        double y = camera.rotation[1][0] * point.x
                + camera.rotation[1][1] * point.y
                + camera.rotation[1][2] * point.z + camera.translation[1];
        double z = camera.rotation[2][0] * point.x
                + camera.rotation[2][1] * point.y
                + camera.rotation[2][2] * point.z + camera.translation[2];
        if (!Double.isFinite(z) || Math.abs(z) < 1e-10) return null;
        return new Projection(camera.fx * x / z + camera.cx,
                camera.fy * y / z + camera.cy, x, y, z);
    }

    private static double[] cameraCenter(LocalBundleAdjustmentCore.Camera camera) {
        double[][] rt = transpose(camera.rotation);
        double[] negativeT = new double[]{-camera.translation[0],
                -camera.translation[1], -camera.translation[2]};
        return multiply(rt, negativeT);
    }

    private static double[][] exp(double[] vector) {
        double angle = norm(vector);
        double[][] identity = new double[][]{{1,0,0},{0,1,0},{0,0,1}};
        if (angle < 1e-12) {
            return new double[][]{
                    {1, -vector[2], vector[1]},
                    {vector[2], 1, -vector[0]},
                    {-vector[1], vector[0], 1}};
        }
        double x = vector[0] / angle;
        double y = vector[1] / angle;
        double z = vector[2] / angle;
        double c = Math.cos(angle), s = Math.sin(angle), one = 1.0 - c;
        return new double[][]{
                {c + x*x*one, x*y*one - z*s, x*z*one + y*s},
                {y*x*one + z*s, c + y*y*one, y*z*one - x*s},
                {z*x*one - y*s, z*y*one + x*s, c + z*z*one}};
    }

    private static List<List<LocalBundleAdjustmentCore.Observation>> groupByCamera(
            int count, List<LocalBundleAdjustmentCore.Observation> observations) {
        List<List<LocalBundleAdjustmentCore.Observation>> result =
                new ArrayList<List<LocalBundleAdjustmentCore.Observation>>();
        for (int i = 0; i < count; i++) {
            result.add(new ArrayList<LocalBundleAdjustmentCore.Observation>());
        }
        for (LocalBundleAdjustmentCore.Observation observation : observations) {
            result.get(observation.cameraIndex).add(observation);
        }
        return result;
    }

    private static List<LocalBundleAdjustmentCore.Camera> copyCameras(
            List<LocalBundleAdjustmentCore.Camera> source) {
        List<LocalBundleAdjustmentCore.Camera> result =
                new ArrayList<LocalBundleAdjustmentCore.Camera>();
        for (LocalBundleAdjustmentCore.Camera camera : source) {
            result.add(new LocalBundleAdjustmentCore.Camera(camera.rotation,
                    camera.translation, camera.fx, camera.fy, camera.cx, camera.cy));
        }
        return result;
    }

    private static List<double[]> copyVectors(List<double[]> source) {
        List<double[]> result = new ArrayList<double[]>();
        for (double[] vector : source) result.add(vector.clone());
        return result;
    }

    private static void accumulate(double[][] normal, double[] rhs,
                                   double[] jacobian, double residual, double weight) {
        for (int i = 0; i < 3; i++) {
            rhs[i] += weight * jacobian[i] * residual;
            for (int j = 0; j < 3; j++) {
                normal[i][j] += weight * jacobian[i] * jacobian[j];
            }
        }
    }

    private static double[] solve3(double[][] source, double[] values) {
        double[][] matrix = new double[3][4];
        for (int i = 0; i < 3; i++) {
            System.arraycopy(source[i], 0, matrix[i], 0, 3);
            matrix[i][3] = values[i];
        }
        for (int column = 0; column < 3; column++) {
            int pivot = column;
            for (int row = column + 1; row < 3; row++) {
                if (Math.abs(matrix[row][column]) > Math.abs(matrix[pivot][column])) {
                    pivot = row;
                }
            }
            if (Math.abs(matrix[pivot][column]) < 1e-12) return null;
            double[] swap = matrix[column]; matrix[column] = matrix[pivot]; matrix[pivot] = swap;
            double divisor = matrix[column][column];
            for (int j = column; j < 4; j++) matrix[column][j] /= divisor;
            for (int row = 0; row < 3; row++) {
                if (row == column) continue;
                double factor = matrix[row][column];
                for (int j = column; j < 4; j++) {
                    matrix[row][j] -= factor * matrix[column][j];
                }
            }
        }
        return new double[]{matrix[0][3], matrix[1][3], matrix[2][3]};
    }

    private static double[][] multiply(double[][] a, double[][] b) {
        double[][] result = new double[3][3];
        for (int i = 0; i < 3; i++) {
            for (int k = 0; k < 3; k++) {
                for (int j = 0; j < 3; j++) result[i][j] += a[i][k] * b[k][j];
            }
        }
        return result;
    }

    private static double[] multiply(double[][] a, double[] vector) {
        return new double[]{
                a[0][0]*vector[0] + a[0][1]*vector[1] + a[0][2]*vector[2],
                a[1][0]*vector[0] + a[1][1]*vector[1] + a[1][2]*vector[2],
                a[2][0]*vector[0] + a[2][1]*vector[1] + a[2][2]*vector[2]};
    }

    private static double[][] transpose(double[][] source) {
        double[][] result = new double[3][3];
        for (int i = 0; i < 3; i++) for (int j = 0; j < 3; j++) result[i][j] = source[j][i];
        return result;
    }

    private static double[] add(double[] a, double[] b) {
        return new double[]{a[0]+b[0], a[1]+b[1], a[2]+b[2]};
    }

    private static void clampNorm(double[] vector, double maximum) {
        double length = norm(vector);
        if (length <= maximum || length < 1e-15) return;
        double scale = maximum / length;
        for (int i = 0; i < vector.length; i++) vector[i] *= scale;
    }

    private static double norm(double[] vector) {
        return Math.sqrt(vector[0]*vector[0] + vector[1]*vector[1] + vector[2]*vector[2]);
    }

    private static boolean finite(double[] values) {
        for (double value : values) if (!Double.isFinite(value)) return false;
        return true;
    }

    private static boolean sameCamera(LocalBundleAdjustmentCore.Camera a,
                                      LocalBundleAdjustmentCore.Camera b,
                                      double tolerance) {
        for (int i = 0; i < 3; i++) {
            if (Math.abs(a.translation[i] - b.translation[i]) > tolerance) return false;
            for (int j = 0; j < 3; j++) {
                if (Math.abs(a.rotation[i][j] - b.rotation[i][j]) > tolerance) return false;
            }
        }
        return true;
    }

    private static double huberWeight(double error, double threshold) {
        return error <= threshold ? 1.0 : threshold / Math.max(error, 1e-12);
    }

    private static double huberLoss(double error, double threshold) {
        return error <= threshold ? 0.5 * error * error
                : threshold * (error - 0.5 * threshold);
    }

    private static double percentile(List<Double> source, double quantile) {
        if (source == null || source.isEmpty()) return Double.POSITIVE_INFINITY;
        List<Double> values = new ArrayList<Double>(source);
        Collections.sort(values);
        double position = Math.max(0.0, Math.min(1.0, quantile)) * (values.size() - 1);
        int low = (int) Math.floor(position), high = (int) Math.ceil(position);
        if (low == high) return values.get(low);
        double fraction = position - low;
        return values.get(low) * (1.0 - fraction) + values.get(high) * fraction;
    }

    public static final class Result {
        public final boolean solved;
        public final String status;
        public final boolean rotationApplied;
        public final LocalBundleAdjustmentCore.Result baseBundleAdjustment;
        public final LocalBundleAdjustmentCore.Result bundleAdjustment;
        public final int rotationIterations, acceptedRotationIterations;
        public final double maximumRotationDegrees, medianRotationDegrees;
        public final double baseFinalRmsPx, finalRmsPx, positiveDepthRatio;
        public final ResidualStatistics residualStatistics;

        Result(boolean solved, String status, boolean rotationApplied,
               LocalBundleAdjustmentCore.Result baseBundleAdjustment,
               LocalBundleAdjustmentCore.Result bundleAdjustment,
               int rotationIterations, int acceptedRotationIterations,
               double maximumRotationDegrees, double medianRotationDegrees,
               double baseFinalRmsPx, double finalRmsPx,
               double positiveDepthRatio, ResidualStatistics residualStatistics) {
            this.solved = solved; this.status = status;
            this.rotationApplied = rotationApplied;
            this.baseBundleAdjustment = baseBundleAdjustment;
            this.bundleAdjustment = bundleAdjustment;
            this.rotationIterations = rotationIterations;
            this.acceptedRotationIterations = acceptedRotationIterations;
            this.maximumRotationDegrees = maximumRotationDegrees;
            this.medianRotationDegrees = medianRotationDegrees;
            this.baseFinalRmsPx = baseFinalRmsPx;
            this.finalRmsPx = finalRmsPx;
            this.positiveDepthRatio = positiveDepthRatio;
            this.residualStatistics = residualStatistics;
        }

        static Result baseOnly(String status, LocalBundleAdjustmentCore.Result base,
                               ResidualStatistics residuals) {
            return new Result(base.solved, status, false, base, base,
                    0, 0, 0.0, 0.0,
                    base.finalRmsPx, base.finalRmsPx,
                    base.positiveDepthRatio, residuals);
        }

        public String summary() {
            return status + " · rotación " + (rotationApplied ? "APLICADA" : "NO APLICADA")
                    + " · RMS " + format(baseFinalRmsPx) + "→" + format(finalRmsPx)
                    + " px · rot max " + format(maximumRotationDegrees) + "° · "
                    + residualStatistics.summary();
        }

        public String canonicalJson() {
            return "{\n\"schema\":\"skm-rotational-ba/1\""
                    + ",\n\"status\":\"" + status + "\""
                    + ",\n\"rotationApplied\":" + rotationApplied
                    + ",\n\"rotationIterations\":" + rotationIterations
                    + ",\n\"acceptedRotationIterations\":" + acceptedRotationIterations
                    + ",\n\"maximumRotationDegrees\":" + number(maximumRotationDegrees)
                    + ",\n\"medianRotationDegrees\":" + number(medianRotationDegrees)
                    + ",\n\"baseFinalRmsPx\":" + number(baseFinalRmsPx)
                    + ",\n\"finalRmsPx\":" + number(finalRmsPx)
                    + ",\n\"positiveDepthRatio\":" + number(positiveDepthRatio)
                    + ",\n\"residualStatistics\":" + residualStatistics.canonicalJson()
                    + "\n}";
        }
    }

    public static final class ResidualStatistics {
        public final int count;
        public final double medianPx, madPx, p90Px;
        public final double empirical95LowPx, empirical95HighPx;
        public final String label;

        ResidualStatistics(int count, double medianPx, double madPx, double p90Px,
                           double empirical95LowPx, double empirical95HighPx) {
            this.count = count; this.medianPx = medianPx; this.madPx = madPx;
            this.p90Px = p90Px; this.empirical95LowPx = empirical95LowPx;
            this.empirical95HighPx = empirical95HighPx;
            this.label = STATISTICAL_LABEL;
        }

        static ResidualStatistics from(List<LocalBundleAdjustmentCore.Camera> cameras,
                                       List<LocalBundleAdjustmentCore.Point3> points,
                                       List<LocalBundleAdjustmentCore.Observation> observations) {
            List<Double> errors = new ArrayList<Double>();
            if (cameras != null && points != null && observations != null) {
                for (LocalBundleAdjustmentCore.Observation observation : observations) {
                    if (observation.cameraIndex < 0 || observation.cameraIndex >= cameras.size()
                            || observation.pointIndex < 0 || observation.pointIndex >= points.size()) continue;
                    Projection projection = project(cameras.get(observation.cameraIndex),
                            points.get(observation.pointIndex));
                    if (projection == null) continue;
                    double error = Math.hypot(projection.u - observation.u,
                            projection.v - observation.v);
                    if (Double.isFinite(error)) errors.add(error);
                }
            }
            if (errors.isEmpty()) {
                return new ResidualStatistics(0, Double.POSITIVE_INFINITY,
                        Double.POSITIVE_INFINITY, Double.POSITIVE_INFINITY,
                        Double.POSITIVE_INFINITY, Double.POSITIVE_INFINITY);
            }
            double median = percentile(errors, 0.5);
            List<Double> deviations = new ArrayList<Double>();
            for (double error : errors) deviations.add(Math.abs(error - median));
            return new ResidualStatistics(errors.size(), median,
                    percentile(deviations, 0.5), percentile(errors, 0.90),
                    percentile(errors, 0.025), percentile(errors, 0.975));
        }

        public String summary() {
            return "residuos n=" + count + " · mediana " + format(medianPx)
                    + " px · intervalo empírico 95% [" + format(empirical95LowPx)
                    + ", " + format(empirical95HighPx) + "] px · NO METROLÓGICO";
        }

        public String canonicalJson() {
            return "{\"label\":\"" + label + "\",\"count\":" + count
                    + ",\"medianPx\":" + number(medianPx)
                    + ",\"madPx\":" + number(madPx)
                    + ",\"p90Px\":" + number(p90Px)
                    + ",\"empirical95LowPx\":" + number(empirical95LowPx)
                    + ",\"empirical95HighPx\":" + number(empirical95HighPx) + "}";
        }
    }

    private static final class Score {
        final double rmsPx, medianPx, p90Px, robustCost;
        final int validObservations;
        final double positiveDepthRatio;
        Score(double rmsPx, double medianPx, double p90Px, double robustCost,
              int validObservations, double positiveDepthRatio) {
            this.rmsPx = rmsPx; this.medianPx = medianPx; this.p90Px = p90Px;
            this.robustCost = robustCost; this.validObservations = validObservations;
            this.positiveDepthRatio = positiveDepthRatio;
        }
        boolean finite() {
            return Double.isFinite(rmsPx) && Double.isFinite(robustCost)
                    && Double.isFinite(positiveDepthRatio);
        }
        static Score invalid() {
            return new Score(Double.POSITIVE_INFINITY, Double.POSITIVE_INFINITY,
                    Double.POSITIVE_INFINITY, Double.POSITIVE_INFINITY, 0, 0.0);
        }
    }

    private static final class Projection {
        final double u, v, x, y, z;
        Projection(double u, double v, double x, double y, double z) {
            this.u = u; this.v = v; this.x = x; this.y = y; this.z = z;
        }
    }

    private static final class RotationSummary {
        final double maximumDegrees, medianDegrees;
        RotationSummary(double maximumDegrees, double medianDegrees) {
            this.maximumDegrees = maximumDegrees; this.medianDegrees = medianDegrees;
        }
    }

    private static String format(double value) {
        return Double.isFinite(value)
                ? String.format(java.util.Locale.ROOT, "%.3f", value) : "N/D";
    }

    private static String number(double value) {
        return Double.isFinite(value) ? Double.toString(value) : "null";
    }
}
package cl.skm.pulleyai;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Conservative focal-only refinement after rotational BA. Camera 0, principal points,
 * distortion and the fx/fy ratio remain fixed. Sensitivity is empirical, not calibration.
 */
public final class ConditionedFocalBundleAdjustmentCore {
    public static final String SENSITIVITY_LABEL =
            "EMPIRICAL_FOCAL_SENSITIVITY_NOT_CALIBRATION";

    private ConditionedFocalBundleAdjustmentCore() {}

    public static Result optimize(LocalBundleAdjustmentCore.Problem problem,
                                  PhotogrammetrySafetyGateCore.Result admission) {
        return optimize(problem, admission, 8, 2.0,
                80_000.0, 0.025, 0.005, 0.02);
    }

    public static Result optimize(LocalBundleAdjustmentCore.Problem problem,
                                  PhotogrammetrySafetyGateCore.Result admission,
                                  int focalIterations,
                                  double huberPx,
                                  double focalPriorWeight,
                                  double maximumScaleFraction,
                                  double maximumStepFraction,
                                  double translationPriorWeight) {
        RotationalBundleAdjustmentCore.Result rotational =
                RotationalBundleAdjustmentCore.optimize(problem, admission);
        LocalBundleAdjustmentCore.Result base = rotational.bundleAdjustment;
        if (!rotational.solved || base == null || !base.solved || !base.ready()) {
            return Result.baseOnly("FOCAL_BASE_" + rotational.status,
                    rotational, sensitivityUnavailable());
        }

        int iterations = Math.max(1, Math.min(12, focalIterations));
        double huber = Math.max(0.5, Math.min(8.0, huberPx));
        double priorWeight = Math.max(1.0, Math.min(1e8, focalPriorWeight));
        double maximumLogScale = Math.log1p(Math.max(0.002,
                Math.min(0.05, maximumScaleFraction)));
        double maximumStep = Math.log1p(Math.max(0.0005,
                Math.min(0.01, maximumStepFraction)));

        List<LocalBundleAdjustmentCore.Camera> priors = copyCameras(base.cameras);
        List<LocalBundleAdjustmentCore.Camera> cameras = copyCameras(priors);
        List<double[]> observationsByCamera = observationIndices(priors.size(), problem.observations);
        List<Observability> observability = new ArrayList<Observability>();
        int observableCount = 0;
        for (int camera = 0; camera < priors.size(); camera++) {
            Observability value = camera == 0
                    ? Observability.reference(camera)
                    : observe(priors.get(camera), base.points,
                    problem.observations, observationsByCamera.get(camera), priorWeight);
            observability.add(value);
            if (value.observable) observableCount++;
        }
        int requiredObservable = Math.max(2, Math.min(4, (priors.size() - 1 + 1) / 2));
        if (observableCount < requiredObservable) {
            return Result.baseOnly("FOCAL_UNOBSERVABLE", rotational,
                    sensitivity(observability, Collections.<Double>emptyList()));
        }

        double[] offsets = new double[priors.size()];
        double[] bestOffsets = offsets.clone();
        List<LocalBundleAdjustmentCore.Camera> bestCameras = copyCameras(cameras);
        Score baseScore = score(base.cameras, base.points, problem.observations, huber);
        Score bestScore = score(cameras, base.points, problem.observations, huber);
        double damping = 5_000.0;
        int accepted = 0;
        int used = 0;
        boolean touchedLimit = false;

        for (int iteration = 0; iteration < iterations; iteration++) {
            used = iteration + 1;
            double[] candidateOffsets = offsets.clone();
            List<LocalBundleAdjustmentCore.Camera> candidateCameras = copyCameras(cameras);
            boolean proposed = false;
            for (int camera = 1; camera < candidateCameras.size(); camera++) {
                Observability observable = observability.get(camera);
                if (!observable.observable) continue;
                double delta = solveIncrement(candidateCameras.get(camera), base.points,
                        problem.observations, observationsByCamera.get(camera),
                        candidateOffsets[camera], huber, priorWeight, damping);
                if (!Double.isFinite(delta)) continue;
                delta = clamp(delta, -maximumStep, maximumStep);
                double next = clamp(candidateOffsets[camera] + delta,
                        -maximumLogScale, maximumLogScale);
                if (Math.abs(next) >= maximumLogScale - 1e-10) touchedLimit = true;
                if (Math.abs(next - candidateOffsets[camera]) < 1e-12) continue;
                candidateOffsets[camera] = next;
                candidateCameras.set(camera, scaled(priors.get(camera), Math.exp(next)));
                proposed = true;
            }
            if (!proposed) break;
            Score candidate = score(candidateCameras, base.points,
                    problem.observations, huber);
            boolean depthSafe = candidate.positiveDepthRatio >= 0.80
                    && candidate.positiveDepthRatio + 0.005 >= bestScore.positiveDepthRatio;
            if (candidate.finite() && depthSafe
                    && candidate.robustCost < bestScore.robustCost - 1e-9) {
                offsets = candidateOffsets;
                cameras = candidateCameras;
                bestOffsets = offsets.clone();
                bestCameras = copyCameras(cameras);
                bestScore = candidate;
                accepted++;
                damping = Math.max(1.0, damping * 0.45);
            } else {
                damping = Math.min(1e8, damping * 8.0);
            }
        }

        LocalBundleAdjustmentCore.Result rebalanced = LocalBundleAdjustmentCore.optimize(
                new LocalBundleAdjustmentCore.Problem(bestCameras, base.points,
                        problem.observations), admission, 8, huber,
                translationPriorWeight);
        List<LocalBundleAdjustmentCore.Camera> finalCameras = bestCameras;
        List<LocalBundleAdjustmentCore.Point3> finalPoints = base.points;
        Score finalScore = bestScore;
        int extraIterations = 0;
        int extraAccepted = 0;
        if (rebalanced.solved) {
            Score candidate = score(rebalanced.cameras, rebalanced.points,
                    problem.observations, huber);
            if (candidate.finite()
                    && candidate.robustCost <= finalScore.robustCost
                    && candidate.positiveDepthRatio + 0.005 >= finalScore.positiveDepthRatio) {
                finalCameras = rebalanced.cameras;
                finalPoints = rebalanced.points;
                finalScore = candidate;
                extraIterations = rebalanced.iterations;
                extraAccepted = rebalanced.acceptedIterations;
            }
        }

        ScaleSummary scales = scaleSummary(priors, finalCameras);
        List<Double> empiricalSensitivity = empiricalSensitivity(finalCameras,
                finalPoints, problem.observations, observationsByCamera, priorWeight);
        Sensitivity sensitivity = sensitivity(observability, empiricalSensitivity);
        double requiredRmsGain = Math.max(0.008, base.finalRmsPx * 0.0025);
        boolean cameraZeroFixed = sameCamera(priors.get(0), finalCameras.get(0), 1e-12);
        boolean principalPointsFixed = principalPointsFixed(priors, finalCameras, 1e-12);
        boolean ratioFixed = focalRatiosFixed(priors, finalCameras, 1e-12);
        boolean improved = accepted > 0
                && finalScore.finite()
                && finalScore.robustCost < baseScore.robustCost * 0.995
                && finalScore.rmsPx <= base.finalRmsPx - requiredRmsGain
                && finalScore.positiveDepthRatio >= 0.80
                && finalScore.positiveDepthRatio + 0.01 >= base.positiveDepthRatio
                && scales.maximumAbsoluteFraction <= Math.expm1(maximumLogScale) + 1e-9
                && cameraZeroFixed && principalPointsFixed && ratioFixed;

        if (!improved) {
            String status = observableCount < requiredObservable ? "FOCAL_UNOBSERVABLE"
                    : accepted == 0 ? "FOCAL_STALLED"
                    : finalScore.positiveDepthRatio + 0.01 < base.positiveDepthRatio
                    ? "FOCAL_REJECTED_DEPTH"
                    : touchedLimit ? "FOCAL_LIMIT_REACHED"
                    : "FOCAL_REJECTED_GAIN";
            return new Result(true, status, false, rotational, base,
                    used, accepted, observableCount, requiredObservable,
                    0.0, 0.0, base.finalRmsPx, base.finalRmsPx,
                    base.positiveDepthRatio, sensitivity);
        }

        double overallImprovement = base.initialRmsPx > 0.0
                ? Math.max(0.0, (base.initialRmsPx - finalScore.rmsPx)
                / base.initialRmsPx) : 0.0;
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
        return new Result(true, "FOCAL_ACCEPTED", true, rotational, combined,
                used, accepted, observableCount, requiredObservable,
                scales.maximumAbsoluteFraction, scales.medianAbsoluteFraction,
                base.finalRmsPx, finalScore.rmsPx,
                finalScore.positiveDepthRatio, sensitivity);
    }

    private static Observability observe(LocalBundleAdjustmentCore.Camera camera,
                                         List<LocalBundleAdjustmentCore.Point3> points,
                                         List<LocalBundleAdjustmentCore.Observation> observations,
                                         double[] indices, double priorWeight) {
        List<Double> radii = new ArrayList<Double>();
        List<Double> inverseDepths = new ArrayList<Double>();
        double information = 0.0;
        for (double encoded : indices) {
            int index = (int) encoded;
            LocalBundleAdjustmentCore.Observation observation = observations.get(index);
            Projection projection = project(camera, points.get(observation.pointIndex));
            if (projection == null || projection.z <= 1e-7) continue;
            double normalizedRadius = Math.hypot(projection.x / projection.z,
                    projection.y / projection.z);
            radii.add(normalizedRadius);
            inverseDepths.add(1.0 / projection.z);
            double du = projection.u - camera.cx;
            double dv = projection.v - camera.cy;
            information += observation.weight * (du * du + dv * dv);
        }
        double radialP10 = percentile(radii, 0.10);
        double radialP90 = percentile(radii, 0.90);
        double inverseP10 = percentile(inverseDepths, 0.10);
        double inverseP90 = percentile(inverseDepths, 0.90);
        double conditionRatio = information <= 0.0 ? 0.0
                : information / (information + priorWeight);
        boolean observable = radii.size() >= 20
                && Double.isFinite(radialP90)
                && radialP90 >= 0.16
                && radialP90 - radialP10 >= 0.075
                && Double.isFinite(inverseP90)
                && inverseP90 - inverseP10 >= 0.012
                && conditionRatio >= 0.65;
        return new Observability(-1, observable, radii.size(),
                radialP10, radialP90, inverseP10, inverseP90,
                information, conditionRatio);
    }

    private static double solveIncrement(LocalBundleAdjustmentCore.Camera camera,
                                         List<LocalBundleAdjustmentCore.Point3> points,
                                         List<LocalBundleAdjustmentCore.Observation> observations,
                                         double[] indices,
                                         double currentOffset,
                                         double huber,
                                         double priorWeight,
                                         double damping) {
        double normal = priorWeight + damping;
        double rhs = -priorWeight * currentOffset;
        int valid = 0;
        for (double encoded : indices) {
            LocalBundleAdjustmentCore.Observation observation =
                    observations.get((int) encoded);
            Projection projection = project(camera, points.get(observation.pointIndex));
            if (projection == null || projection.z <= 1e-7) continue;
            double ru = observation.u - projection.u;
            double rv = observation.v - projection.v;
            double weight = observation.weight
                    * huberWeight(Math.hypot(ru, rv), huber);
            double ju = projection.u - camera.cx;
            double jv = projection.v - camera.cy;
            normal += weight * (ju * ju + jv * jv);
            rhs += weight * (ju * ru + jv * rv);
            valid++;
        }
        if (valid < 20 || !Double.isFinite(normal) || normal <= 1e-12) {
            return Double.NaN;
        }
        return rhs / normal;
    }

    private static List<Double> empiricalSensitivity(
            List<LocalBundleAdjustmentCore.Camera> cameras,
            List<LocalBundleAdjustmentCore.Point3> points,
            List<LocalBundleAdjustmentCore.Observation> observations,
            List<double[]> byCamera,
            double priorWeight) {
        List<Double> result = new ArrayList<Double>();
        for (int camera = 1; camera < cameras.size(); camera++) {
            double information = priorWeight;
            double squared = 0.0;
            int count = 0;
            LocalBundleAdjustmentCore.Camera value = cameras.get(camera);
            for (double encoded : byCamera.get(camera)) {
                LocalBundleAdjustmentCore.Observation observation =
                        observations.get((int) encoded);
                Projection projection = project(value, points.get(observation.pointIndex));
                if (projection == null || projection.z <= 1e-7) continue;
                double ru = observation.u - projection.u;
                double rv = observation.v - projection.v;
                squared += ru * ru + rv * rv;
                double ju = projection.u - value.cx;
                double jv = projection.v - value.cy;
                information += observation.weight * (ju * ju + jv * jv);
                count += 2;
            }
            if (count > 3 && information > 0.0) {
                double variance = squared / (count - 1.0);
                result.add(Math.sqrt(Math.max(0.0, variance / information)));
            }
        }
        return result;
    }

    private static Sensitivity sensitivity(List<Observability> observability,
                                           List<Double> empirical) {
        List<Double> conditions = new ArrayList<Double>();
        int observable = 0;
        for (Observability value : observability) {
            if (value.reference) continue;
            conditions.add(value.conditionRatio);
            if (value.observable) observable++;
        }
        return new Sensitivity(observable,
                conditions.isEmpty() ? 0.0 : percentile(conditions, 0.5),
                empirical.isEmpty() ? Double.POSITIVE_INFINITY
                        : percentile(empirical, 0.5) * 100.0,
                empirical.isEmpty() ? Double.POSITIVE_INFINITY
                        : percentile(empirical, 0.90) * 100.0);
    }

    private static Sensitivity sensitivityUnavailable() {
        return new Sensitivity(0, 0.0,
                Double.POSITIVE_INFINITY, Double.POSITIVE_INFINITY);
    }

    private static List<double[]> observationIndices(int cameraCount,
                                                      List<LocalBundleAdjustmentCore.Observation> observations) {
        List<List<Double>> temporary = new ArrayList<List<Double>>();
        for (int i = 0; i < cameraCount; i++) temporary.add(new ArrayList<Double>());
        for (int i = 0; i < observations.size(); i++) {
            LocalBundleAdjustmentCore.Observation observation = observations.get(i);
            temporary.get(observation.cameraIndex).add((double) i);
        }
        List<double[]> result = new ArrayList<double[]>();
        for (List<Double> values : temporary) {
            double[] indices = new double[values.size()];
            for (int i = 0; i < values.size(); i++) indices[i] = values.get(i);
            result.add(indices);
        }
        return result;
    }

    private static LocalBundleAdjustmentCore.Camera scaled(
            LocalBundleAdjustmentCore.Camera prior, double scale) {
        return new LocalBundleAdjustmentCore.Camera(prior.rotation,
                prior.translation, prior.fx * scale, prior.fy * scale,
                prior.cx, prior.cy);
    }

    private static ScaleSummary scaleSummary(
            List<LocalBundleAdjustmentCore.Camera> priors,
            List<LocalBundleAdjustmentCore.Camera> current) {
        List<Double> values = new ArrayList<Double>();
        double maximum = 0.0;
        for (int i = 1; i < Math.min(priors.size(), current.size()); i++) {
            double scale = current.get(i).fx / priors.get(i).fx - 1.0;
            double absolute = Math.abs(scale);
            values.add(absolute);
            maximum = Math.max(maximum, absolute);
        }
        return new ScaleSummary(maximum,
                values.isEmpty() ? 0.0 : percentile(values, 0.5));
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

    private static List<LocalBundleAdjustmentCore.Camera> copyCameras(
            List<LocalBundleAdjustmentCore.Camera> source) {
        List<LocalBundleAdjustmentCore.Camera> result =
                new ArrayList<LocalBundleAdjustmentCore.Camera>();
        for (LocalBundleAdjustmentCore.Camera camera : source) {
            result.add(new LocalBundleAdjustmentCore.Camera(camera.rotation,
                    camera.translation, camera.fx, camera.fy,
                    camera.cx, camera.cy));
        }
        return result;
    }

    private static boolean sameCamera(LocalBundleAdjustmentCore.Camera a,
                                      LocalBundleAdjustmentCore.Camera b,
                                      double tolerance) {
        if (Math.abs(a.fx - b.fx) > tolerance
                || Math.abs(a.fy - b.fy) > tolerance
                || Math.abs(a.cx - b.cx) > tolerance
                || Math.abs(a.cy - b.cy) > tolerance) return false;
        for (int i = 0; i < 3; i++) {
            if (Math.abs(a.translation[i] - b.translation[i]) > tolerance) return false;
            for (int j = 0; j < 3; j++) {
                if (Math.abs(a.rotation[i][j] - b.rotation[i][j]) > tolerance) return false;
            }
        }
        return true;
    }

    private static boolean principalPointsFixed(
            List<LocalBundleAdjustmentCore.Camera> priors,
            List<LocalBundleAdjustmentCore.Camera> current,
            double tolerance) {
        for (int i = 0; i < Math.min(priors.size(), current.size()); i++) {
            if (Math.abs(priors.get(i).cx - current.get(i).cx) > tolerance
                    || Math.abs(priors.get(i).cy - current.get(i).cy) > tolerance) return false;
        }
        return true;
    }

    private static boolean focalRatiosFixed(
            List<LocalBundleAdjustmentCore.Camera> priors,
            List<LocalBundleAdjustmentCore.Camera> current,
            double tolerance) {
        for (int i = 0; i < Math.min(priors.size(), current.size()); i++) {
            double before = priors.get(i).fx / priors.get(i).fy;
            double after = current.get(i).fx / current.get(i).fy;
            if (Math.abs(before - after) > tolerance) return false;
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
        double position = Math.max(0.0, Math.min(1.0, quantile))
                * (values.size() - 1);
        int low = (int) Math.floor(position), high = (int) Math.ceil(position);
        if (low == high) return values.get(low);
        double fraction = position - low;
        return values.get(low) * (1.0 - fraction)
                + values.get(high) * fraction;
    }

    private static double clamp(double value, double minimum, double maximum) {
        return Math.max(minimum, Math.min(maximum, value));
    }

    public static final class Result {
        public final boolean solved;
        public final String status;
        public final boolean focalApplied;
        public final RotationalBundleAdjustmentCore.Result rotationalBundleAdjustment;
        public final LocalBundleAdjustmentCore.Result bundleAdjustment;
        public final int focalIterations, acceptedFocalIterations;
        public final int observableCameras, requiredObservableCameras;
        public final double maximumScaleFraction, medianScaleFraction;
        public final double baseFinalRmsPx, finalRmsPx, positiveDepthRatio;
        public final Sensitivity sensitivity;

        Result(boolean solved, String status, boolean focalApplied,
               RotationalBundleAdjustmentCore.Result rotationalBundleAdjustment,
               LocalBundleAdjustmentCore.Result bundleAdjustment,
               int focalIterations, int acceptedFocalIterations,
               int observableCameras, int requiredObservableCameras,
               double maximumScaleFraction, double medianScaleFraction,
               double baseFinalRmsPx, double finalRmsPx,
               double positiveDepthRatio, Sensitivity sensitivity) {
            this.solved = solved; this.status = status;
            this.focalApplied = focalApplied;
            this.rotationalBundleAdjustment = rotationalBundleAdjustment;
            this.bundleAdjustment = bundleAdjustment;
            this.focalIterations = focalIterations;
            this.acceptedFocalIterations = acceptedFocalIterations;
            this.observableCameras = observableCameras;
            this.requiredObservableCameras = requiredObservableCameras;
            this.maximumScaleFraction = maximumScaleFraction;
            this.medianScaleFraction = medianScaleFraction;
            this.baseFinalRmsPx = baseFinalRmsPx;
            this.finalRmsPx = finalRmsPx;
            this.positiveDepthRatio = positiveDepthRatio;
            this.sensitivity = sensitivity;
        }

        static Result baseOnly(String status,
                               RotationalBundleAdjustmentCore.Result rotational,
                               Sensitivity sensitivity) {
            LocalBundleAdjustmentCore.Result base = rotational.bundleAdjustment;
            return new Result(rotational.solved, status, false,
                    rotational, base, 0, 0, 0, 2,
                    0.0, 0.0,
                    base == null ? Double.NaN : base.finalRmsPx,
                    base == null ? Double.NaN : base.finalRmsPx,
                    base == null ? 0.0 : base.positiveDepthRatio,
                    sensitivity);
        }

        public String summary() {
            return status + " · focal " + (focalApplied ? "APLICADO" : "NO APLICADO")
                    + " · RMS " + format(baseFinalRmsPx) + "→" + format(finalRmsPx)
                    + " px · escala máx " + format(maximumScaleFraction * 100.0)
                    + "% · observables " + observableCameras + "/"
                    + requiredObservableCameras + " · " + sensitivity.summary();
        }

        public String canonicalJson() {
            return "{\n\"schema\":\"skm-conditioned-focal-ba/1\""
                    + ",\n\"status\":\"" + status + "\""
                    + ",\n\"focalApplied\":" + focalApplied
                    + ",\n\"focalIterations\":" + focalIterations
                    + ",\n\"acceptedFocalIterations\":" + acceptedFocalIterations
                    + ",\n\"observableCameras\":" + observableCameras
                    + ",\n\"requiredObservableCameras\":" + requiredObservableCameras
                    + ",\n\"maximumScaleFraction\":" + number(maximumScaleFraction)
                    + ",\n\"medianScaleFraction\":" + number(medianScaleFraction)
                    + ",\n\"baseFinalRmsPx\":" + number(baseFinalRmsPx)
                    + ",\n\"finalRmsPx\":" + number(finalRmsPx)
                    + ",\n\"positiveDepthRatio\":" + number(positiveDepthRatio)
                    + ",\n\"sensitivity\":" + sensitivity.canonicalJson()
                    + "\n}";
        }
    }

    public static final class Sensitivity {
        public final int observableCameras;
        public final double medianConditionRatio;
        public final double medianEmpiricalScaleSigmaPercent;
        public final double p90EmpiricalScaleSigmaPercent;
        public final String label;

        Sensitivity(int observableCameras, double medianConditionRatio,
                    double medianEmpiricalScaleSigmaPercent,
                    double p90EmpiricalScaleSigmaPercent) {
            this.observableCameras = observableCameras;
            this.medianConditionRatio = medianConditionRatio;
            this.medianEmpiricalScaleSigmaPercent =
                    medianEmpiricalScaleSigmaPercent;
            this.p90EmpiricalScaleSigmaPercent =
                    p90EmpiricalScaleSigmaPercent;
            this.label = SENSITIVITY_LABEL;
        }

        public String summary() {
            return "condición " + format(medianConditionRatio)
                    + " · sensibilidad empírica P90 "
                    + format(p90EmpiricalScaleSigmaPercent)
                    + "% · NO CALIBRACIÓN";
        }

        public String canonicalJson() {
            return "{\"label\":\"" + label + "\",\"observableCameras\":"
                    + observableCameras + ",\"medianConditionRatio\":"
                    + number(medianConditionRatio)
                    + ",\"medianEmpiricalScaleSigmaPercent\":"
                    + number(medianEmpiricalScaleSigmaPercent)
                    + ",\"p90EmpiricalScaleSigmaPercent\":"
                    + number(p90EmpiricalScaleSigmaPercent) + "}";
        }
    }

    private static final class Observability {
        final int cameraIndex;
        final boolean observable, reference;
        final int observations;
        final double radialP10, radialP90;
        final double inverseDepthP10, inverseDepthP90;
        final double information, conditionRatio;

        Observability(int cameraIndex, boolean observable, int observations,
                      double radialP10, double radialP90,
                      double inverseDepthP10, double inverseDepthP90,
                      double information, double conditionRatio) {
            this.cameraIndex = cameraIndex; this.observable = observable;
            this.reference = false; this.observations = observations;
            this.radialP10 = radialP10; this.radialP90 = radialP90;
            this.inverseDepthP10 = inverseDepthP10;
            this.inverseDepthP90 = inverseDepthP90;
            this.information = information; this.conditionRatio = conditionRatio;
        }

        private Observability(int cameraIndex) {
            this.cameraIndex = cameraIndex; this.observable = false;
            this.reference = true; this.observations = 0;
            this.radialP10 = 0.0; this.radialP90 = 0.0;
            this.inverseDepthP10 = 0.0; this.inverseDepthP90 = 0.0;
            this.information = 0.0; this.conditionRatio = 1.0;
        }

        static Observability reference(int cameraIndex) {
            return new Observability(cameraIndex);
        }
    }

    private static final class Score {
        final double rmsPx, medianPx, p90Px, robustCost;
        final int validObservations;
        final double positiveDepthRatio;
        Score(double rmsPx, double medianPx, double p90Px,
              double robustCost, int validObservations,
              double positiveDepthRatio) {
            this.rmsPx = rmsPx; this.medianPx = medianPx;
            this.p90Px = p90Px; this.robustCost = robustCost;
            this.validObservations = validObservations;
            this.positiveDepthRatio = positiveDepthRatio;
        }
        boolean finite() {
            return Double.isFinite(rmsPx) && Double.isFinite(robustCost)
                    && Double.isFinite(positiveDepthRatio);
        }
        static Score invalid() {
            return new Score(Double.POSITIVE_INFINITY,
                    Double.POSITIVE_INFINITY, Double.POSITIVE_INFINITY,
                    Double.POSITIVE_INFINITY, 0, 0.0);
        }
    }

    private static final class Projection {
        final double u, v, x, y, z;
        Projection(double u, double v, double x, double y, double z) {
            this.u = u; this.v = v; this.x = x; this.y = y; this.z = z;
        }
    }

    private static final class ScaleSummary {
        final double maximumAbsoluteFraction, medianAbsoluteFraction;
        ScaleSummary(double maximumAbsoluteFraction,
                     double medianAbsoluteFraction) {
            this.maximumAbsoluteFraction = maximumAbsoluteFraction;
            this.medianAbsoluteFraction = medianAbsoluteFraction;
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
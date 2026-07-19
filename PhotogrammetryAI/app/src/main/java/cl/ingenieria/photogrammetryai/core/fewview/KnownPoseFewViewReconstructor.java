package cl.ingenieria.photogrammetryai.core.fewview;

import cl.ingenieria.photogrammetryai.core.foundation.CoreMath;
import cl.ingenieria.photogrammetryai.core.foundation.CoreMath.Vec3;
import cl.ingenieria.photogrammetryai.core.fewview.FewViewGeometry.CameraView;
import cl.ingenieria.photogrammetryai.core.fewview.FewViewGeometry.FeatureObservation;
import cl.ingenieria.photogrammetryai.core.fewview.FewViewGeometry.FeatureTrack;
import cl.ingenieria.photogrammetryai.core.fewview.FewViewGeometry.ProjectedPoint;
import cl.ingenieria.photogrammetryai.core.fewview.FewViewGeometry.Ray;
import cl.ingenieria.photogrammetryai.core.fewview.FewViewGeometry.ReconstructedPoint;
import cl.ingenieria.photogrammetryai.core.fewview.FewViewGeometry.ReconstructionReport;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

/**
 * Robust sparse reconstruction when target detection already provides calibrated camera poses.
 *
 * <p>The solver is intentionally small enough for a phone. It generates hypotheses from all
 * geometrically useful view pairs, scores them against every available view, rejects outliers and
 * refines each accepted 3D point with robust Gauss-Newton reprojection minimisation.</p>
 */
public final class KnownPoseFewViewReconstructor {
    public static final String REJECT_MISSING_VIEWS = "MISSING_VIEWS";
    public static final String REJECT_LOW_PARALLAX = "LOW_PARALLAX";
    public static final String REJECT_NO_CONSENSUS = "NO_CONSENSUS";
    public static final String REJECT_REFINEMENT_FAILED = "REFINEMENT_FAILED";
    public static final String REJECT_BEHIND_CAMERA = "BEHIND_CAMERA";

    public static final class Config {
        private final int minimumInlierViews;
        private final double minimumTriangulationAngleDeg;
        private final double initialInlierThresholdPx;
        private final double finalInlierThresholdPx;
        private final double huberDeltaPx;
        private final int maximumRefinementIterations;
        private final double refinementDamping;
        private final double convergenceStepMm;
        private final double maximumStepMm;
        private final double fullConfidenceAngleDeg;

        public Config(
                int minimumInlierViews,
                double minimumTriangulationAngleDeg,
                double initialInlierThresholdPx,
                double finalInlierThresholdPx,
                double huberDeltaPx,
                int maximumRefinementIterations,
                double refinementDamping,
                double convergenceStepMm,
                double maximumStepMm,
                double fullConfidenceAngleDeg
        ) {
            if (minimumInlierViews < 2) {
                throw new IllegalArgumentException("minimumInlierViews must be at least 2");
            }
            this.minimumInlierViews = minimumInlierViews;
            this.minimumTriangulationAngleDeg = requirePositive(
                    minimumTriangulationAngleDeg,
                    "minimumTriangulationAngleDeg"
            );
            this.initialInlierThresholdPx = requirePositive(
                    initialInlierThresholdPx,
                    "initialInlierThresholdPx"
            );
            this.finalInlierThresholdPx = requirePositive(
                    finalInlierThresholdPx,
                    "finalInlierThresholdPx"
            );
            if (finalInlierThresholdPx > initialInlierThresholdPx) {
                throw new IllegalArgumentException(
                        "finalInlierThresholdPx must not exceed initialInlierThresholdPx"
                );
            }
            this.huberDeltaPx = requirePositive(huberDeltaPx, "huberDeltaPx");
            if (maximumRefinementIterations <= 0) {
                throw new IllegalArgumentException("maximumRefinementIterations must be positive");
            }
            this.maximumRefinementIterations = maximumRefinementIterations;
            this.refinementDamping = requirePositive(refinementDamping, "refinementDamping");
            this.convergenceStepMm = requirePositive(convergenceStepMm, "convergenceStepMm");
            this.maximumStepMm = requirePositive(maximumStepMm, "maximumStepMm");
            this.fullConfidenceAngleDeg = requirePositive(
                    fullConfidenceAngleDeg,
                    "fullConfidenceAngleDeg"
            );
        }

        public static Config mobileDefaults() {
            return new Config(
                    2,
                    1.25,
                    6.0,
                    2.75,
                    2.0,
                    15,
                    1.0e-5,
                    1.0e-4,
                    100.0,
                    8.0
            );
        }

        public int minimumInlierViews() {
            return minimumInlierViews;
        }

        public double minimumTriangulationAngleDeg() {
            return minimumTriangulationAngleDeg;
        }

        public double initialInlierThresholdPx() {
            return initialInlierThresholdPx;
        }

        public double finalInlierThresholdPx() {
            return finalInlierThresholdPx;
        }

        public double huberDeltaPx() {
            return huberDeltaPx;
        }

        public int maximumRefinementIterations() {
            return maximumRefinementIterations;
        }

        public double refinementDamping() {
            return refinementDamping;
        }

        public double convergenceStepMm() {
            return convergenceStepMm;
        }

        public double maximumStepMm() {
            return maximumStepMm;
        }

        public double fullConfidenceAngleDeg() {
            return fullConfidenceAngleDeg;
        }
    }

    private final Config config;

    public KnownPoseFewViewReconstructor(Config config) {
        this.config = Objects.requireNonNull(config, "config");
    }

    public ReconstructionReport reconstruct(
            List<CameraView> views,
            List<FeatureTrack> tracks
    ) {
        Map<String, CameraView> viewsById = FewViewGeometry.indexViews(views);
        Objects.requireNonNull(tracks, "tracks");

        List<ReconstructedPoint> points = new ArrayList<>();
        Map<String, Integer> rejectionReasons = new LinkedHashMap<>();

        for (FeatureTrack track : tracks) {
            Objects.requireNonNull(track, "track");
            TrackSolution solution = reconstructTrack(viewsById, track);
            if (solution.point != null) {
                points.add(solution.point);
            } else {
                rejectionReasons.put(
                        solution.rejectionReason,
                        rejectionReasons.getOrDefault(solution.rejectionReason, 0) + 1
                );
            }
        }

        return new ReconstructionReport(
                points,
                tracks.size(),
                tracks.size() - points.size(),
                rejectionReasons
        );
    }

    private TrackSolution reconstructTrack(
            Map<String, CameraView> viewsById,
            FeatureTrack track
    ) {
        List<ObservationRay> observations = new ArrayList<>();
        for (FeatureObservation observation : track.observations()) {
            CameraView view = viewsById.get(observation.viewId());
            if (view != null) {
                observations.add(new ObservationRay(
                        observation,
                        view,
                        view.worldRay(observation.u(), observation.v())
                ));
            }
        }

        if (observations.size() < config.minimumInlierViews()) {
            return TrackSolution.rejected(REJECT_MISSING_VIEWS);
        }

        List<Candidate> candidates = generateCandidates(observations);
        if (candidates.isEmpty()) {
            return TrackSolution.rejected(REJECT_LOW_PARALLAX);
        }

        candidates.sort(
                Comparator.comparingInt((Candidate candidate) -> candidate.evaluation.inliers.size())
                        .reversed()
                        .thenComparingDouble(candidate -> candidate.evaluation.robustCost)
                        .thenComparing(
                                Comparator.comparingDouble(
                                        (Candidate candidate) -> candidate.triangulationAngleDeg
                                ).reversed()
                        )
        );

        Candidate best = candidates.get(0);
        if (best.evaluation.inliers.size() < config.minimumInlierViews()) {
            return TrackSolution.rejected(REJECT_NO_CONSENSUS);
        }

        Optional<Vec3> refinedOptional = refinePoint(best.point, best.evaluation.inliers);
        if (!refinedOptional.isPresent()) {
            return TrackSolution.rejected(REJECT_REFINEMENT_FAILED);
        }

        Vec3 refined = refinedOptional.get();
        Evaluation finalEvaluation = evaluate(
                refined,
                observations,
                config.finalInlierThresholdPx()
        );
        if (finalEvaluation.inliers.size() < config.minimumInlierViews()) {
            return TrackSolution.rejected(REJECT_NO_CONSENSUS);
        }

        Optional<Vec3> secondRefinement = refinePoint(refined, finalEvaluation.inliers);
        if (secondRefinement.isPresent()) {
            refined = secondRefinement.get();
            finalEvaluation = evaluate(
                    refined,
                    observations,
                    config.finalInlierThresholdPx()
            );
        }

        if (finalEvaluation.inliers.size() < config.minimumInlierViews()) {
            return TrackSolution.rejected(REJECT_NO_CONSENSUS);
        }
        if (!allInFront(refined, finalEvaluation.inliers)) {
            return TrackSolution.rejected(REJECT_BEHIND_CAMERA);
        }

        double bestAngle = bestTriangulationAngle(finalEvaluation.inliers);
        double rms = finalEvaluation.rmsErrorPx;
        double maximum = finalEvaluation.maximumErrorPx;
        double confidence = confidence(
                observations.size(),
                finalEvaluation.inliers,
                bestAngle,
                rms
        );
        Set<String> inlierViewIds = new LinkedHashSet<>();
        for (ObservationRay inlier : finalEvaluation.inliers) {
            inlierViewIds.add(inlier.view.id());
        }

        return TrackSolution.accepted(new ReconstructedPoint(
                track.id(),
                refined,
                inlierViewIds,
                rms,
                maximum,
                bestAngle,
                confidence
        ));
    }

    private List<Candidate> generateCandidates(List<ObservationRay> observations) {
        List<Candidate> candidates = new ArrayList<>();
        for (int firstIndex = 0; firstIndex < observations.size(); firstIndex++) {
            for (int secondIndex = firstIndex + 1; secondIndex < observations.size(); secondIndex++) {
                ObservationRay first = observations.get(firstIndex);
                ObservationRay second = observations.get(secondIndex);
                double angleDeg = angleDegrees(first.ray.direction(), second.ray.direction());
                if (angleDeg < config.minimumTriangulationAngleDeg()) {
                    continue;
                }
                Optional<ClosestApproach> approachOptional = closestApproach(first.ray, second.ray);
                if (!approachOptional.isPresent()) {
                    continue;
                }
                ClosestApproach approach = approachOptional.get();
                if (approach.distanceAlongFirst <= 0.0 || approach.distanceAlongSecond <= 0.0) {
                    continue;
                }
                Vec3 midpoint = approach.pointOnFirst.add(approach.pointOnSecond).scale(0.5);
                Evaluation evaluation = evaluate(
                        midpoint,
                        observations,
                        config.initialInlierThresholdPx()
                );
                candidates.add(new Candidate(midpoint, angleDeg, evaluation));
            }
        }
        return candidates;
    }

    private Evaluation evaluate(
            Vec3 point,
            List<ObservationRay> observations,
            double inlierThresholdPx
    ) {
        List<ObservationRay> inliers = new ArrayList<>();
        double robustCost = 0.0;
        double squaredErrorSum = 0.0;
        double maximumError = 0.0;

        for (ObservationRay observation : observations) {
            Optional<ProjectedPoint> projectedOptional = observation.view.project(point);
            if (!projectedOptional.isPresent()) {
                robustCost += inlierThresholdPx * inlierThresholdPx * 4.0;
                continue;
            }
            ProjectedPoint projected = projectedOptional.get();
            double du = observation.observation.u() - projected.u();
            double dv = observation.observation.v() - projected.v();
            double error = Math.hypot(du, dv);
            double sourceWeight = observation.sourceWeight();
            robustCost += sourceWeight * huberCost(error, config.huberDeltaPx());
            if (error <= inlierThresholdPx) {
                inliers.add(observation);
                squaredErrorSum += error * error;
                maximumError = Math.max(maximumError, error);
            }
        }

        double rms = inliers.isEmpty()
                ? Double.POSITIVE_INFINITY
                : Math.sqrt(squaredErrorSum / inliers.size());
        return new Evaluation(inliers, robustCost, rms, maximumError);
    }

    private Optional<Vec3> refinePoint(Vec3 initial, List<ObservationRay> observations) {
        Vec3 point = initial;
        for (int iteration = 0; iteration < config.maximumRefinementIterations(); iteration++) {
            double[][] hessian = new double[3][3];
            double[] gradient = new double[3];
            int usable = 0;

            for (ObservationRay observation : observations) {
                Vec3 cameraPoint = observation.view.cameraFromWorld().transformPoint(point);
                if (cameraPoint.z <= 1.0e-8) {
                    continue;
                }

                double[] rotation = observation.view.cameraFromWorld().rotationRowMajor();
                double x = cameraPoint.x;
                double y = cameraPoint.y;
                double z = cameraPoint.z;
                double inverseZ = 1.0 / z;
                double inverseZSquared = inverseZ * inverseZ;
                double fx = observation.view.intrinsics().fx();
                double fy = observation.view.intrinsics().fy();

                double predictedU = fx * x * inverseZ + observation.view.intrinsics().cx();
                double predictedV = fy * y * inverseZ + observation.view.intrinsics().cy();
                double residualU = observation.observation.u() - predictedU;
                double residualV = observation.observation.v() - predictedV;
                double error = Math.hypot(residualU, residualV);

                double[] jacobianU = new double[3];
                double[] jacobianV = new double[3];
                for (int column = 0; column < 3; column++) {
                    double dx = rotation[column];
                    double dy = rotation[3 + column];
                    double dz = rotation[6 + column];
                    jacobianU[column] = fx * (dx * inverseZ - x * dz * inverseZSquared);
                    jacobianV[column] = fy * (dy * inverseZ - y * dz * inverseZSquared);
                }

                double weight = observation.sourceWeight() * huberWeight(error, config.huberDeltaPx());
                accumulateNormalEquation(
                        hessian,
                        gradient,
                        jacobianU,
                        residualU,
                        weight
                );
                accumulateNormalEquation(
                        hessian,
                        gradient,
                        jacobianV,
                        residualV,
                        weight
                );
                usable++;
            }

            if (usable < config.minimumInlierViews()) {
                return Optional.empty();
            }
            for (int diagonal = 0; diagonal < 3; diagonal++) {
                hessian[diagonal][diagonal] += config.refinementDamping();
            }

            Optional<double[]> stepOptional = solve3x3(hessian, gradient);
            if (!stepOptional.isPresent()) {
                return Optional.empty();
            }
            double[] step = stepOptional.get();
            Vec3 delta = new Vec3(step[0], step[1], step[2]);
            double length = delta.norm();
            if (length > config.maximumStepMm()) {
                delta = delta.scale(config.maximumStepMm() / length);
                length = config.maximumStepMm();
            }
            point = point.add(delta);
            if (length <= config.convergenceStepMm()) {
                break;
            }
        }
        return Optional.of(point);
    }

    private static void accumulateNormalEquation(
            double[][] hessian,
            double[] gradient,
            double[] jacobian,
            double residual,
            double weight
    ) {
        for (int row = 0; row < 3; row++) {
            gradient[row] += weight * jacobian[row] * residual;
            for (int column = 0; column < 3; column++) {
                hessian[row][column] += weight * jacobian[row] * jacobian[column];
            }
        }
    }

    private static Optional<double[]> solve3x3(double[][] matrix, double[] vector) {
        double[][] augmented = new double[3][4];
        for (int row = 0; row < 3; row++) {
            System.arraycopy(matrix[row], 0, augmented[row], 0, 3);
            augmented[row][3] = vector[row];
        }

        for (int pivotColumn = 0; pivotColumn < 3; pivotColumn++) {
            int pivotRow = pivotColumn;
            double pivotMagnitude = Math.abs(augmented[pivotRow][pivotColumn]);
            for (int row = pivotColumn + 1; row < 3; row++) {
                double magnitude = Math.abs(augmented[row][pivotColumn]);
                if (magnitude > pivotMagnitude) {
                    pivotMagnitude = magnitude;
                    pivotRow = row;
                }
            }
            if (pivotMagnitude < 1.0e-12 || !Double.isFinite(pivotMagnitude)) {
                return Optional.empty();
            }
            if (pivotRow != pivotColumn) {
                double[] temporary = augmented[pivotRow];
                augmented[pivotRow] = augmented[pivotColumn];
                augmented[pivotColumn] = temporary;
            }

            double pivot = augmented[pivotColumn][pivotColumn];
            for (int column = pivotColumn; column < 4; column++) {
                augmented[pivotColumn][column] /= pivot;
            }
            for (int row = 0; row < 3; row++) {
                if (row == pivotColumn) {
                    continue;
                }
                double factor = augmented[row][pivotColumn];
                for (int column = pivotColumn; column < 4; column++) {
                    augmented[row][column] -= factor * augmented[pivotColumn][column];
                }
            }
        }

        double[] result = new double[]{augmented[0][3], augmented[1][3], augmented[2][3]};
        for (double value : result) {
            if (!Double.isFinite(value)) {
                return Optional.empty();
            }
        }
        return Optional.of(result);
    }

    private static Optional<ClosestApproach> closestApproach(Ray first, Ray second) {
        Vec3 firstDirection = first.direction();
        Vec3 secondDirection = second.direction();
        Vec3 betweenOrigins = first.origin().subtract(second.origin());
        double b = firstDirection.dot(secondDirection);
        double d = firstDirection.dot(betweenOrigins);
        double e = secondDirection.dot(betweenOrigins);
        double denominator = 1.0 - b * b;
        if (Math.abs(denominator) < 1.0e-10) {
            return Optional.empty();
        }
        double firstDistance = (b * e - d) / denominator;
        double secondDistance = (e - b * d) / denominator;
        return Optional.of(new ClosestApproach(
                first.at(firstDistance),
                second.at(secondDistance),
                firstDistance,
                secondDistance
        ));
    }

    private static boolean allInFront(Vec3 point, List<ObservationRay> observations) {
        for (ObservationRay observation : observations) {
            if (!observation.view.project(point).isPresent()) {
                return false;
            }
        }
        return true;
    }

    private static double bestTriangulationAngle(List<ObservationRay> observations) {
        double best = 0.0;
        for (int first = 0; first < observations.size(); first++) {
            for (int second = first + 1; second < observations.size(); second++) {
                best = Math.max(
                        best,
                        angleDegrees(
                                observations.get(first).ray.direction(),
                                observations.get(second).ray.direction()
                        )
                );
            }
        }
        return best;
    }

    private double confidence(
            int totalObservationCount,
            List<ObservationRay> inliers,
            double bestAngleDeg,
            double rmsErrorPx
    ) {
        double coverage = inliers.size() / (double) totalObservationCount;
        double viewSupport = Math.min(1.0, inliers.size() / 3.0);
        double angleQuality = CoreMath.clamp01(bestAngleDeg / config.fullConfidenceAngleDeg());
        double errorQuality = Math.exp(-rmsErrorPx / config.finalInlierThresholdPx());
        double sourceQuality = 0.0;
        for (ObservationRay inlier : inliers) {
            sourceQuality += Math.sqrt(inlier.sourceWeight());
        }
        sourceQuality /= inliers.size();
        return CoreMath.clamp01(
                coverage
                        * (0.25 + 0.75 * angleQuality)
                        * errorQuality
                        * sourceQuality
                        * (0.80 + 0.20 * viewSupport)
        );
    }

    private static double angleDegrees(Vec3 first, Vec3 second) {
        double cosine = Math.max(-1.0, Math.min(1.0, first.normalized().dot(second.normalized())));
        return Math.toDegrees(Math.acos(cosine));
    }

    private static double huberWeight(double error, double delta) {
        if (error <= delta || error <= 1.0e-12) {
            return 1.0;
        }
        return delta / error;
    }

    private static double huberCost(double error, double delta) {
        if (error <= delta) {
            return 0.5 * error * error;
        }
        return delta * (error - 0.5 * delta);
    }

    private static double requirePositive(double value, String name) {
        if (!Double.isFinite(value) || value <= 0.0) {
            throw new IllegalArgumentException(name + " must be positive and finite");
        }
        return value;
    }

    private static final class ObservationRay {
        private final FeatureObservation observation;
        private final CameraView view;
        private final Ray ray;

        private ObservationRay(FeatureObservation observation, CameraView view, Ray ray) {
            this.observation = observation;
            this.view = view;
            this.ray = ray;
        }

        private double sourceWeight() {
            return Math.max(1.0e-6, observation.confidence() * view.poseConfidence());
        }
    }

    private static final class ClosestApproach {
        private final Vec3 pointOnFirst;
        private final Vec3 pointOnSecond;
        private final double distanceAlongFirst;
        private final double distanceAlongSecond;

        private ClosestApproach(
                Vec3 pointOnFirst,
                Vec3 pointOnSecond,
                double distanceAlongFirst,
                double distanceAlongSecond
        ) {
            this.pointOnFirst = pointOnFirst;
            this.pointOnSecond = pointOnSecond;
            this.distanceAlongFirst = distanceAlongFirst;
            this.distanceAlongSecond = distanceAlongSecond;
        }
    }

    private static final class Evaluation {
        private final List<ObservationRay> inliers;
        private final double robustCost;
        private final double rmsErrorPx;
        private final double maximumErrorPx;

        private Evaluation(
                List<ObservationRay> inliers,
                double robustCost,
                double rmsErrorPx,
                double maximumErrorPx
        ) {
            this.inliers = Collections.unmodifiableList(new ArrayList<>(inliers));
            this.robustCost = robustCost;
            this.rmsErrorPx = rmsErrorPx;
            this.maximumErrorPx = maximumErrorPx;
        }
    }

    private static final class Candidate {
        private final Vec3 point;
        private final double triangulationAngleDeg;
        private final Evaluation evaluation;

        private Candidate(Vec3 point, double triangulationAngleDeg, Evaluation evaluation) {
            this.point = point;
            this.triangulationAngleDeg = triangulationAngleDeg;
            this.evaluation = evaluation;
        }
    }

    private static final class TrackSolution {
        private final ReconstructedPoint point;
        private final String rejectionReason;

        private TrackSolution(ReconstructedPoint point, String rejectionReason) {
            this.point = point;
            this.rejectionReason = rejectionReason;
        }

        private static TrackSolution accepted(ReconstructedPoint point) {
            return new TrackSolution(Objects.requireNonNull(point, "point"), null);
        }

        private static TrackSolution rejected(String reason) {
            return new TrackSolution(null, Objects.requireNonNull(reason, "reason"));
        }
    }
}

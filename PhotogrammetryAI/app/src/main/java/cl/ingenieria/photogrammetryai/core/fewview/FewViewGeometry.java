package cl.ingenieria.photogrammetryai.core.fewview;

import cl.ingenieria.photogrammetryai.core.foundation.CoreMath;
import cl.ingenieria.photogrammetryai.core.foundation.CoreMath.RigidPose;
import cl.ingenieria.photogrammetryai.core.foundation.CoreMath.Vec3;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

/**
 * Dependency-free data model for calibrated reconstruction from two to eight known camera poses.
 *
 * <p>Pixels are assumed to be already undistorted. Camera poses map camera coordinates into the
 * shared target/world coordinate system, expressed in millimetres.</p>
 */
public final class FewViewGeometry {
    private FewViewGeometry() {
    }

    public static final class CameraIntrinsics {
        private final int imageWidth;
        private final int imageHeight;
        private final double fx;
        private final double fy;
        private final double cx;
        private final double cy;

        public CameraIntrinsics(
                int imageWidth,
                int imageHeight,
                double fx,
                double fy,
                double cx,
                double cy
        ) {
            if (imageWidth <= 0 || imageHeight <= 0) {
                throw new IllegalArgumentException("Image dimensions must be positive");
            }
            this.imageWidth = imageWidth;
            this.imageHeight = imageHeight;
            this.fx = requirePositiveFinite(fx, "fx");
            this.fy = requirePositiveFinite(fy, "fy");
            this.cx = requireFinite(cx, "cx");
            this.cy = requireFinite(cy, "cy");
        }

        public int imageWidth() {
            return imageWidth;
        }

        public int imageHeight() {
            return imageHeight;
        }

        public double fx() {
            return fx;
        }

        public double fy() {
            return fy;
        }

        public double cx() {
            return cx;
        }

        public double cy() {
            return cy;
        }

        public Vec3 pixelToCameraRay(double u, double v) {
            requireFinite(u, "u");
            requireFinite(v, "v");
            return new Vec3((u - cx) / fx, (v - cy) / fy, 1.0).normalized();
        }
    }

    public static final class CameraView {
        private final String id;
        private final CameraIntrinsics intrinsics;
        private final RigidPose worldFromCamera;
        private final RigidPose cameraFromWorld;
        private final long timestampNanos;
        private final double poseConfidence;

        public CameraView(
                String id,
                CameraIntrinsics intrinsics,
                RigidPose worldFromCamera,
                long timestampNanos,
                double poseConfidence
        ) {
            this.id = requireText(id, "id");
            this.intrinsics = Objects.requireNonNull(intrinsics, "intrinsics");
            this.worldFromCamera = Objects.requireNonNull(worldFromCamera, "worldFromCamera");
            this.cameraFromWorld = worldFromCamera.inverse();
            if (timestampNanos < 0L) {
                throw new IllegalArgumentException("timestampNanos must be non-negative");
            }
            this.timestampNanos = timestampNanos;
            this.poseConfidence = requireConfidence(poseConfidence, "poseConfidence");
        }

        public String id() {
            return id;
        }

        public CameraIntrinsics intrinsics() {
            return intrinsics;
        }

        public RigidPose worldFromCamera() {
            return worldFromCamera;
        }

        public RigidPose cameraFromWorld() {
            return cameraFromWorld;
        }

        public long timestampNanos() {
            return timestampNanos;
        }

        public double poseConfidence() {
            return poseConfidence;
        }

        public Ray worldRay(double u, double v) {
            Vec3 cameraDirection = intrinsics.pixelToCameraRay(u, v);
            Vec3 worldDirection = worldFromCamera.rotateVector(cameraDirection).normalized();
            return new Ray(worldFromCamera.translation(), worldDirection);
        }

        public Optional<ProjectedPoint> project(Vec3 worldPoint) {
            Objects.requireNonNull(worldPoint, "worldPoint");
            Vec3 cameraPoint = cameraFromWorld.transformPoint(worldPoint);
            if (cameraPoint.z <= 1.0e-8) {
                return Optional.empty();
            }
            double u = intrinsics.fx() * cameraPoint.x / cameraPoint.z + intrinsics.cx();
            double v = intrinsics.fy() * cameraPoint.y / cameraPoint.z + intrinsics.cy();
            return Optional.of(new ProjectedPoint(u, v, cameraPoint.z));
        }
    }

    public static final class Ray {
        private final Vec3 origin;
        private final Vec3 direction;

        public Ray(Vec3 origin, Vec3 direction) {
            this.origin = Objects.requireNonNull(origin, "origin");
            this.direction = Objects.requireNonNull(direction, "direction").normalized();
        }

        public Vec3 origin() {
            return origin;
        }

        public Vec3 direction() {
            return direction;
        }

        public Vec3 at(double distanceMm) {
            requireFinite(distanceMm, "distanceMm");
            return origin.add(direction.scale(distanceMm));
        }
    }

    public static final class ProjectedPoint {
        private final double u;
        private final double v;
        private final double depthMm;

        public ProjectedPoint(double u, double v, double depthMm) {
            this.u = requireFinite(u, "u");
            this.v = requireFinite(v, "v");
            this.depthMm = requirePositiveFinite(depthMm, "depthMm");
        }

        public double u() {
            return u;
        }

        public double v() {
            return v;
        }

        public double depthMm() {
            return depthMm;
        }
    }

    public static final class FeatureObservation {
        private final String viewId;
        private final double u;
        private final double v;
        private final double confidence;

        public FeatureObservation(String viewId, double u, double v, double confidence) {
            this.viewId = requireText(viewId, "viewId");
            this.u = requireFinite(u, "u");
            this.v = requireFinite(v, "v");
            this.confidence = requireConfidence(confidence, "confidence");
        }

        public String viewId() {
            return viewId;
        }

        public double u() {
            return u;
        }

        public double v() {
            return v;
        }

        public double confidence() {
            return confidence;
        }
    }

    public static final class FeatureTrack {
        private final String id;
        private final List<FeatureObservation> observations;

        public FeatureTrack(String id, List<FeatureObservation> observations) {
            this.id = requireText(id, "id");
            Objects.requireNonNull(observations, "observations");
            if (observations.size() < 2) {
                throw new IllegalArgumentException("A feature track requires at least two observations");
            }
            Map<String, FeatureObservation> byView = new LinkedHashMap<>();
            for (FeatureObservation observation : observations) {
                Objects.requireNonNull(observation, "observation");
                FeatureObservation previous = byView.put(observation.viewId(), observation);
                if (previous != null) {
                    throw new IllegalArgumentException(
                            "Track " + id + " contains more than one observation for view "
                                    + observation.viewId()
                    );
                }
            }
            this.observations = Collections.unmodifiableList(new ArrayList<>(byView.values()));
        }

        public String id() {
            return id;
        }

        public List<FeatureObservation> observations() {
            return observations;
        }
    }

    public static final class ReconstructedPoint {
        private final String trackId;
        private final Vec3 worldPointMm;
        private final Set<String> inlierViewIds;
        private final double rmsReprojectionErrorPx;
        private final double maximumReprojectionErrorPx;
        private final double bestTriangulationAngleDeg;
        private final double confidence;

        public ReconstructedPoint(
                String trackId,
                Vec3 worldPointMm,
                Set<String> inlierViewIds,
                double rmsReprojectionErrorPx,
                double maximumReprojectionErrorPx,
                double bestTriangulationAngleDeg,
                double confidence
        ) {
            this.trackId = requireText(trackId, "trackId");
            this.worldPointMm = Objects.requireNonNull(worldPointMm, "worldPointMm");
            Objects.requireNonNull(inlierViewIds, "inlierViewIds");
            if (inlierViewIds.size() < 2) {
                throw new IllegalArgumentException("At least two inlier views are required");
            }
            this.inlierViewIds = Collections.unmodifiableSet(new LinkedHashSet<>(inlierViewIds));
            this.rmsReprojectionErrorPx = requireNonNegativeFinite(
                    rmsReprojectionErrorPx,
                    "rmsReprojectionErrorPx"
            );
            this.maximumReprojectionErrorPx = requireNonNegativeFinite(
                    maximumReprojectionErrorPx,
                    "maximumReprojectionErrorPx"
            );
            this.bestTriangulationAngleDeg = requireNonNegativeFinite(
                    bestTriangulationAngleDeg,
                    "bestTriangulationAngleDeg"
            );
            this.confidence = requireConfidence(confidence, "confidence");
        }

        public String trackId() {
            return trackId;
        }

        public Vec3 worldPointMm() {
            return worldPointMm;
        }

        public Set<String> inlierViewIds() {
            return inlierViewIds;
        }

        public double rmsReprojectionErrorPx() {
            return rmsReprojectionErrorPx;
        }

        public double maximumReprojectionErrorPx() {
            return maximumReprojectionErrorPx;
        }

        public double bestTriangulationAngleDeg() {
            return bestTriangulationAngleDeg;
        }

        public double confidence() {
            return confidence;
        }
    }

    public static final class ReconstructionReport {
        private final List<ReconstructedPoint> points;
        private final int inputTrackCount;
        private final int rejectedTrackCount;
        private final Map<String, Integer> rejectionReasons;

        public ReconstructionReport(
                List<ReconstructedPoint> points,
                int inputTrackCount,
                int rejectedTrackCount,
                Map<String, Integer> rejectionReasons
        ) {
            Objects.requireNonNull(points, "points");
            Objects.requireNonNull(rejectionReasons, "rejectionReasons");
            if (inputTrackCount < 0 || rejectedTrackCount < 0
                    || points.size() + rejectedTrackCount != inputTrackCount) {
                throw new IllegalArgumentException("Inconsistent reconstruction counters");
            }
            this.points = Collections.unmodifiableList(new ArrayList<>(points));
            this.inputTrackCount = inputTrackCount;
            this.rejectedTrackCount = rejectedTrackCount;
            this.rejectionReasons = Collections.unmodifiableMap(new LinkedHashMap<>(rejectionReasons));
        }

        public List<ReconstructedPoint> points() {
            return points;
        }

        public int inputTrackCount() {
            return inputTrackCount;
        }

        public int rejectedTrackCount() {
            return rejectedTrackCount;
        }

        public Map<String, Integer> rejectionReasons() {
            return rejectionReasons;
        }

        public double meanConfidence() {
            if (points.isEmpty()) {
                return 0.0;
            }
            double sum = 0.0;
            for (ReconstructedPoint point : points) {
                sum += point.confidence();
            }
            return sum / points.size();
        }
    }

    public static Map<String, CameraView> indexViews(List<CameraView> views) {
        Objects.requireNonNull(views, "views");
        Map<String, CameraView> indexed = new LinkedHashMap<>();
        for (CameraView view : views) {
            Objects.requireNonNull(view, "view");
            CameraView previous = indexed.put(view.id(), view);
            if (previous != null) {
                throw new IllegalArgumentException("Duplicate camera view id: " + view.id());
            }
        }
        return Collections.unmodifiableMap(indexed);
    }

    static double requireConfidence(double value, String name) {
        if (!Double.isFinite(value) || value < 0.0 || value > 1.0) {
            throw new IllegalArgumentException(name + " must be between 0 and 1");
        }
        return value;
    }

    static double requirePositiveFinite(double value, String name) {
        if (!Double.isFinite(value) || value <= 0.0) {
            throw new IllegalArgumentException(name + " must be positive and finite");
        }
        return value;
    }

    static double requireNonNegativeFinite(double value, String name) {
        if (!Double.isFinite(value) || value < 0.0) {
            throw new IllegalArgumentException(name + " must be non-negative and finite");
        }
        return value;
    }

    static double requireFinite(double value, String name) {
        if (!Double.isFinite(value)) {
            throw new IllegalArgumentException(name + " must be finite");
        }
        return value;
    }

    static String requireText(String value, String name) {
        if (value == null || value.trim().isEmpty()) {
            throw new IllegalArgumentException(name + " must not be blank");
        }
        return value;
    }
}

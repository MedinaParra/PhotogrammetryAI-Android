package cl.ingenieria.photogrammetryai.core.foundation;

import static cl.ingenieria.photogrammetryai.core.foundation.CoreMath.RigidPose;
import static cl.ingenieria.photogrammetryai.core.foundation.CoreMath.clamp01;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/** Timestamped observations contributed by the Galaxy master and remote capture nodes. */
public final class TrackingFrame {
    public enum SupportRole {
        LEFT,
        RIGHT,
        UNASSIGNED
    }

    public static final class DeviceObservation {
        private final String deviceId;
        private final String referenceId;
        private final SupportRole supportRole;
        private final long timestampNanos;
        private final RigidPose worldFromCamera;
        private final RigidPose cameraFromReference;
        private final double targetConfidence;
        private final double modelConfidence;
        private final double reprojectionErrorPx;

        public DeviceObservation(
                String deviceId,
                String referenceId,
                SupportRole supportRole,
                long timestampNanos,
                RigidPose worldFromCamera,
                RigidPose cameraFromReference,
                double targetConfidence,
                double modelConfidence,
                double reprojectionErrorPx
        ) {
            this.deviceId = requireText(deviceId, "deviceId");
            this.referenceId = requireText(referenceId, "referenceId");
            this.supportRole = Objects.requireNonNull(supportRole, "supportRole");
            if (timestampNanos < 0L) {
                throw new IllegalArgumentException("timestampNanos must be non-negative");
            }
            this.timestampNanos = timestampNanos;
            this.worldFromCamera = Objects.requireNonNull(worldFromCamera, "worldFromCamera");
            this.cameraFromReference = Objects.requireNonNull(cameraFromReference, "cameraFromReference");
            this.targetConfidence = clamp01(targetConfidence);
            this.modelConfidence = clamp01(modelConfidence);
            if (!Double.isFinite(reprojectionErrorPx) || reprojectionErrorPx < 0.0) {
                throw new IllegalArgumentException("reprojectionErrorPx must be finite and non-negative");
            }
            this.reprojectionErrorPx = reprojectionErrorPx;
        }

        public String deviceId() {
            return deviceId;
        }

        public String referenceId() {
            return referenceId;
        }

        public SupportRole supportRole() {
            return supportRole;
        }

        public long timestampNanos() {
            return timestampNanos;
        }

        public RigidPose worldFromCamera() {
            return worldFromCamera;
        }

        public RigidPose cameraFromReference() {
            return cameraFromReference;
        }

        public RigidPose worldFromReference() {
            return worldFromCamera.compose(cameraFromReference);
        }

        public double targetConfidence() {
            return targetConfidence;
        }

        public double modelConfidence() {
            return modelConfidence;
        }

        public double reprojectionErrorPx() {
            return reprojectionErrorPx;
        }

        public double fusionWeight() {
            double errorPenalty = 1.0 / (1.0 + reprojectionErrorPx);
            return Math.max(1.0e-6, targetConfidence * modelConfidence * errorPenalty);
        }
    }

    private final long frameTimestampNanos;
    private final Map<String, DeviceObservation> latestByDevice;

    private TrackingFrame(long frameTimestampNanos, Map<String, DeviceObservation> latestByDevice) {
        this.frameTimestampNanos = frameTimestampNanos;
        this.latestByDevice = Collections.unmodifiableMap(new LinkedHashMap<>(latestByDevice));
    }

    public static TrackingFrame empty(long timestampNanos) {
        if (timestampNanos < 0L) {
            throw new IllegalArgumentException("timestampNanos must be non-negative");
        }
        return new TrackingFrame(timestampNanos, Collections.emptyMap());
    }

    public TrackingFrame withObservation(DeviceObservation observation) {
        Objects.requireNonNull(observation, "observation");
        Map<String, DeviceObservation> updated = new LinkedHashMap<>(latestByDevice);
        DeviceObservation previous = updated.get(observation.deviceId());
        if (previous == null || observation.timestampNanos() >= previous.timestampNanos()) {
            updated.put(observation.deviceId(), observation);
        }
        long newTimestamp = Math.max(frameTimestampNanos, observation.timestampNanos());
        return new TrackingFrame(newTimestamp, updated);
    }

    public long frameTimestampNanos() {
        return frameTimestampNanos;
    }

    public int deviceCount() {
        return latestByDevice.size();
    }

    public Optional<DeviceObservation> observationForDevice(String deviceId) {
        return Optional.ofNullable(latestByDevice.get(deviceId));
    }

    public Collection<DeviceObservation> observations() {
        return latestByDevice.values();
    }

    public List<DeviceObservation> freshObservations(long nowNanos, long maximumAgeNanos) {
        if (nowNanos < 0L || maximumAgeNanos < 0L) {
            throw new IllegalArgumentException("timestamps and ages must be non-negative");
        }
        List<DeviceObservation> fresh = new ArrayList<>();
        for (DeviceObservation observation : latestByDevice.values()) {
            long age = Math.max(0L, nowNanos - observation.timestampNanos());
            if (age <= maximumAgeNanos) {
                fresh.add(observation);
            }
        }
        return Collections.unmodifiableList(fresh);
    }

    private static String requireText(String value, String name) {
        if (value == null || value.trim().isEmpty()) {
            throw new IllegalArgumentException(name + " must not be blank");
        }
        return value;
    }
}

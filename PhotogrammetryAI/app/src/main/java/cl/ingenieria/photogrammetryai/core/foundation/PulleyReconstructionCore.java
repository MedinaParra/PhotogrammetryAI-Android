package cl.ingenieria.photogrammetryai.core.foundation;

import cl.ingenieria.photogrammetryai.core.foundation.CadReferenceModel.Feature;
import cl.ingenieria.photogrammetryai.core.foundation.CoreMath.RigidPose;
import cl.ingenieria.photogrammetryai.core.foundation.CoreMath.Vec3;
import cl.ingenieria.photogrammetryai.core.foundation.CorePorts.CadReferenceRepository;
import cl.ingenieria.photogrammetryai.core.foundation.CorePorts.CoreEvent;
import cl.ingenieria.photogrammetryai.core.foundation.CorePorts.EventSink;
import cl.ingenieria.photogrammetryai.core.foundation.CorePorts.NanoClock;
import cl.ingenieria.photogrammetryai.core.foundation.TrackingFrame.DeviceObservation;
import cl.ingenieria.photogrammetryai.core.foundation.TrackingFrame.SupportRole;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

/**
 * Pure-Java coordinator for STEP-assisted multi-device pulley tracking.
 *
 * <p>This class intentionally has no Android, OpenCV, ARCore, networking or OpenCASCADE
 * dependencies. Adapters provide imported CAD references and camera observations.</p>
 */
public final class PulleyReconstructionCore {
    public enum State {
        EMPTY,
        REFERENCES_READY,
        SESSION_READY,
        TRACKING,
        FROZEN,
        FAILED
    }

    public static final class Config {
        private final long maximumObservationAgeNanos;
        private final double minimumTargetConfidence;
        private final double minimumModelConfidence;
        private final int minimumDevicesForStableEstimate;

        public Config(
                long maximumObservationAgeNanos,
                double minimumTargetConfidence,
                double minimumModelConfidence,
                int minimumDevicesForStableEstimate
        ) {
            if (maximumObservationAgeNanos <= 0L) {
                throw new IllegalArgumentException("maximumObservationAgeNanos must be positive");
            }
            this.maximumObservationAgeNanos = maximumObservationAgeNanos;
            this.minimumTargetConfidence = requireConfidence(minimumTargetConfidence, "minimumTargetConfidence");
            this.minimumModelConfidence = requireConfidence(minimumModelConfidence, "minimumModelConfidence");
            if (minimumDevicesForStableEstimate <= 0) {
                throw new IllegalArgumentException("minimumDevicesForStableEstimate must be positive");
            }
            this.minimumDevicesForStableEstimate = minimumDevicesForStableEstimate;
        }

        public static Config realTimeDefaults() {
            return new Config(
                    750_000_000L,
                    0.45,
                    0.45,
                    2
            );
        }

        public long maximumObservationAgeNanos() {
            return maximumObservationAgeNanos;
        }

        public double minimumTargetConfidence() {
            return minimumTargetConfidence;
        }

        public double minimumModelConfidence() {
            return minimumModelConfidence;
        }

        public int minimumDevicesForStableEstimate() {
            return minimumDevicesForStableEstimate;
        }
    }

    public static final class PulleyEstimate {
        private final Vec3 shaftOriginWorldMm;
        private final Vec3 shaftDirectionWorld;
        private final Vec3 leftSupportCentreWorldMm;
        private final Vec3 rightSupportCentreWorldMm;
        private final double supportCentreDistanceMm;
        private final double confidence;
        private final int observationCount;
        private final boolean stable;

        private PulleyEstimate(
                Vec3 shaftOriginWorldMm,
                Vec3 shaftDirectionWorld,
                Vec3 leftSupportCentreWorldMm,
                Vec3 rightSupportCentreWorldMm,
                double supportCentreDistanceMm,
                double confidence,
                int observationCount,
                boolean stable
        ) {
            this.shaftOriginWorldMm = Objects.requireNonNull(shaftOriginWorldMm, "shaftOriginWorldMm");
            this.shaftDirectionWorld = Objects.requireNonNull(shaftDirectionWorld, "shaftDirectionWorld").normalized();
            this.leftSupportCentreWorldMm = leftSupportCentreWorldMm;
            this.rightSupportCentreWorldMm = rightSupportCentreWorldMm;
            this.supportCentreDistanceMm = supportCentreDistanceMm;
            this.confidence = CoreMath.clamp01(confidence);
            this.observationCount = observationCount;
            this.stable = stable;
        }

        public Vec3 shaftOriginWorldMm() {
            return shaftOriginWorldMm;
        }

        public Vec3 shaftDirectionWorld() {
            return shaftDirectionWorld;
        }

        public Optional<Vec3> leftSupportCentreWorldMm() {
            return Optional.ofNullable(leftSupportCentreWorldMm);
        }

        public Optional<Vec3> rightSupportCentreWorldMm() {
            return Optional.ofNullable(rightSupportCentreWorldMm);
        }

        public double supportCentreDistanceMm() {
            return supportCentreDistanceMm;
        }

        public double confidence() {
            return confidence;
        }

        public int observationCount() {
            return observationCount;
        }

        public boolean stable() {
            return stable;
        }
    }

    public static final class Snapshot {
        private final State state;
        private final String sessionId;
        private final Set<String> expectedDeviceIds;
        private final int registeredReferenceCount;
        private final int observedDeviceCount;
        private final PulleyEstimate estimate;
        private final String failureMessage;

        private Snapshot(
                State state,
                String sessionId,
                Set<String> expectedDeviceIds,
                int registeredReferenceCount,
                int observedDeviceCount,
                PulleyEstimate estimate,
                String failureMessage
        ) {
            this.state = state;
            this.sessionId = sessionId;
            this.expectedDeviceIds = Collections.unmodifiableSet(new LinkedHashSet<>(expectedDeviceIds));
            this.registeredReferenceCount = registeredReferenceCount;
            this.observedDeviceCount = observedDeviceCount;
            this.estimate = estimate;
            this.failureMessage = failureMessage;
        }

        public State state() {
            return state;
        }

        public Optional<String> sessionId() {
            return Optional.ofNullable(sessionId);
        }

        public Set<String> expectedDeviceIds() {
            return expectedDeviceIds;
        }

        public int registeredReferenceCount() {
            return registeredReferenceCount;
        }

        public int observedDeviceCount() {
            return observedDeviceCount;
        }

        public Optional<PulleyEstimate> estimate() {
            return Optional.ofNullable(estimate);
        }

        public Optional<String> failureMessage() {
            return Optional.ofNullable(failureMessage);
        }
    }

    private final Config config;
    private final CadReferenceRepository referenceRepository;
    private final NanoClock clock;
    private final EventSink eventSink;
    private final Set<String> registeredReferenceIds = new LinkedHashSet<>();
    private final Set<String> expectedDeviceIds = new LinkedHashSet<>();

    private State state = State.EMPTY;
    private String sessionId;
    private String failureMessage;
    private TrackingFrame frame = TrackingFrame.empty(0L);
    private PulleyEstimate latestEstimate;

    public PulleyReconstructionCore(
            Config config,
            CadReferenceRepository referenceRepository,
            NanoClock clock,
            EventSink eventSink
    ) {
        this.config = Objects.requireNonNull(config, "config");
        this.referenceRepository = Objects.requireNonNull(referenceRepository, "referenceRepository");
        this.clock = Objects.requireNonNull(clock, "clock");
        this.eventSink = Objects.requireNonNull(eventSink, "eventSink");
    }

    public synchronized void registerReference(CadReferenceModel model) {
        requireMutable();
        Objects.requireNonNull(model, "model");
        if (!model.primaryBore().isPresent()) {
            throw new IllegalArgumentException("Reference " + model.id() + " has no primary cylindrical bore");
        }
        referenceRepository.put(model);
        registeredReferenceIds.add(model.id());
        state = State.REFERENCES_READY;
        emit(CoreEvent.Type.REFERENCE_REGISTERED, "Registered CAD reference " + model.id());
    }

    public synchronized void startSession(String sessionId, Set<String> expectedDeviceIds) {
        requireMutable();
        if (registeredReferenceIds.isEmpty()) {
            throw new IllegalStateException("At least one CAD reference must be registered first");
        }
        this.sessionId = requireText(sessionId, "sessionId");
        Objects.requireNonNull(expectedDeviceIds, "expectedDeviceIds");
        if (expectedDeviceIds.isEmpty()) {
            throw new IllegalArgumentException("At least one device is required");
        }
        this.expectedDeviceIds.clear();
        for (String deviceId : expectedDeviceIds) {
            this.expectedDeviceIds.add(requireText(deviceId, "deviceId"));
        }
        this.frame = TrackingFrame.empty(clock.nowNanos());
        this.latestEstimate = null;
        this.failureMessage = null;
        this.state = State.SESSION_READY;
        emit(CoreEvent.Type.SESSION_STARTED, "Session " + sessionId + " started with "
                + this.expectedDeviceIds.size() + " devices");
    }

    public synchronized boolean submitObservation(DeviceObservation observation) {
        Objects.requireNonNull(observation, "observation");
        if (state == State.FROZEN || state == State.FAILED || state == State.EMPTY
                || state == State.REFERENCES_READY) {
            emit(CoreEvent.Type.OBSERVATION_REJECTED, "Observation rejected in state " + state);
            return false;
        }
        if (!expectedDeviceIds.contains(observation.deviceId())) {
            emit(CoreEvent.Type.OBSERVATION_REJECTED,
                    "Unexpected device " + observation.deviceId());
            return false;
        }
        if (!registeredReferenceIds.contains(observation.referenceId())) {
            emit(CoreEvent.Type.OBSERVATION_REJECTED,
                    "Unknown reference " + observation.referenceId());
            return false;
        }
        if (observation.targetConfidence() < config.minimumTargetConfidence()) {
            emit(CoreEvent.Type.OBSERVATION_REJECTED,
                    "Target confidence below threshold for " + observation.deviceId());
            return false;
        }
        if (observation.modelConfidence() < config.minimumModelConfidence()) {
            emit(CoreEvent.Type.OBSERVATION_REJECTED,
                    "Model confidence below threshold for " + observation.deviceId());
            return false;
        }

        frame = frame.withObservation(observation);
        emit(CoreEvent.Type.OBSERVATION_ACCEPTED,
                "Accepted observation from " + observation.deviceId());
        latestEstimate = solve(clock.nowNanos());
        if (latestEstimate != null) {
            state = State.TRACKING;
            emit(CoreEvent.Type.ESTIMATE_UPDATED,
                    "Updated shaft estimate from " + latestEstimate.observationCount() + " observations");
        }
        return true;
    }

    public synchronized Optional<PulleyEstimate> refreshEstimate() {
        if (state == State.FROZEN) {
            return Optional.ofNullable(latestEstimate);
        }
        latestEstimate = solve(clock.nowNanos());
        if (latestEstimate != null) {
            state = State.TRACKING;
        } else if (sessionId != null) {
            state = State.SESSION_READY;
        }
        return Optional.ofNullable(latestEstimate);
    }

    public synchronized Optional<PulleyEstimate> freeze() {
        if (state != State.TRACKING || latestEstimate == null) {
            return Optional.empty();
        }
        state = State.FROZEN;
        emit(CoreEvent.Type.SESSION_FROZEN, "Session frozen with confidence " + latestEstimate.confidence());
        return Optional.of(latestEstimate);
    }

    public synchronized void fail(String message) {
        failureMessage = requireText(message, "message");
        state = State.FAILED;
        emit(CoreEvent.Type.FAILURE, failureMessage);
    }

    public synchronized Snapshot snapshot() {
        return new Snapshot(
                state,
                sessionId,
                expectedDeviceIds,
                registeredReferenceIds.size(),
                frame.deviceCount(),
                latestEstimate,
                failureMessage
        );
    }

    private PulleyEstimate solve(long nowNanos) {
        List<DeviceObservation> observations = frame.freshObservations(
                nowNanos,
                config.maximumObservationAgeNanos()
        );
        if (observations.isEmpty()) {
            return null;
        }

        List<AxisSample> leftSamples = new ArrayList<>();
        List<AxisSample> rightSamples = new ArrayList<>();
        List<AxisSample> allSamples = new ArrayList<>();

        for (DeviceObservation observation : observations) {
            Optional<CadReferenceModel> modelOptional = referenceRepository.findById(observation.referenceId());
            if (!modelOptional.isPresent()) {
                continue;
            }
            Optional<Feature> boreOptional = modelOptional.get().primaryBore();
            if (!boreOptional.isPresent()) {
                continue;
            }
            Feature bore = boreOptional.get();
            RigidPose worldFromReference = observation.worldFromReference();
            Vec3 centre = worldFromReference.transformPoint(bore.origin());
            Vec3 direction = worldFromReference.rotateVector(bore.direction()).normalized();
            double weight = Math.max(1.0e-6, observation.fusionWeight() * bore.trackingWeight());
            AxisSample sample = new AxisSample(centre, direction, weight,
                    observation.targetConfidence() * observation.modelConfidence());
            allSamples.add(sample);
            if (observation.supportRole() == SupportRole.LEFT) {
                leftSamples.add(sample);
            } else if (observation.supportRole() == SupportRole.RIGHT) {
                rightSamples.add(sample);
            }
        }

        if (allSamples.isEmpty()) {
            return null;
        }

        AxisAverage all = average(allSamples);
        AxisAverage left = leftSamples.isEmpty() ? null : average(leftSamples);
        AxisAverage right = rightSamples.isEmpty() ? null : average(rightSamples);

        Vec3 shaftOrigin = all.centre;
        Vec3 shaftDirection = all.direction;
        Vec3 leftCentre = left == null ? null : left.centre;
        Vec3 rightCentre = right == null ? null : right.centre;
        double centreDistance = Double.NaN;

        if (left != null && right != null) {
            Vec3 betweenSupports = right.centre.subtract(left.centre);
            centreDistance = betweenSupports.norm();
            if (centreDistance > 1.0e-6) {
                shaftDirection = betweenSupports.normalized().alignedWith(all.direction);
                shaftOrigin = left.centre.add(right.centre).scale(0.5);
            }
        }

        double averageRawConfidence = 0.0;
        for (AxisSample sample : allSamples) {
            averageRawConfidence += sample.rawConfidence;
        }
        averageRawConfidence /= allSamples.size();
        double deviceCoverage = Math.min(1.0,
                allSamples.size() / (double) config.minimumDevicesForStableEstimate());
        double twoSupportFactor = left != null && right != null ? 1.0 : 0.78;
        double confidence = CoreMath.clamp01(averageRawConfidence * deviceCoverage * twoSupportFactor);
        boolean stable = allSamples.size() >= config.minimumDevicesForStableEstimate()
                && confidence >= 0.55;

        return new PulleyEstimate(
                shaftOrigin,
                shaftDirection,
                leftCentre,
                rightCentre,
                centreDistance,
                confidence,
                allSamples.size(),
                stable
        );
    }

    private static AxisAverage average(List<AxisSample> samples) {
        AxisSample first = samples.get(0);
        Vec3 referenceDirection = first.direction;
        Vec3 weightedCentre = Vec3.ZERO;
        Vec3 weightedDirection = Vec3.ZERO;
        double weightSum = 0.0;
        for (AxisSample sample : samples) {
            Vec3 alignedDirection = sample.direction.alignedWith(referenceDirection);
            weightedCentre = weightedCentre.add(sample.centre.scale(sample.weight));
            weightedDirection = weightedDirection.add(alignedDirection.scale(sample.weight));
            weightSum += sample.weight;
        }
        return new AxisAverage(
                weightedCentre.scale(1.0 / weightSum),
                weightedDirection.normalizedOr(referenceDirection)
        );
    }

    private void requireMutable() {
        if (state == State.FROZEN) {
            throw new IllegalStateException("The session is frozen");
        }
        if (state == State.FAILED) {
            throw new IllegalStateException("The core is in a failed state: " + failureMessage);
        }
    }

    private void emit(CoreEvent.Type type, String message) {
        eventSink.onEvent(new CoreEvent(type, clock.nowNanos(), message));
    }

    private static double requireConfidence(double value, String name) {
        if (!Double.isFinite(value) || value < 0.0 || value > 1.0) {
            throw new IllegalArgumentException(name + " must be between 0 and 1");
        }
        return value;
    }

    private static String requireText(String value, String name) {
        if (value == null || value.trim().isEmpty()) {
            throw new IllegalArgumentException(name + " must not be blank");
        }
        return value;
    }

    private static final class AxisSample {
        private final Vec3 centre;
        private final Vec3 direction;
        private final double weight;
        private final double rawConfidence;

        private AxisSample(Vec3 centre, Vec3 direction, double weight, double rawConfidence) {
            this.centre = centre;
            this.direction = direction;
            this.weight = weight;
            this.rawConfidence = rawConfidence;
        }
    }

    private static final class AxisAverage {
        private final Vec3 centre;
        private final Vec3 direction;

        private AxisAverage(Vec3 centre, Vec3 direction) {
            this.centre = centre;
            this.direction = direction;
        }
    }
}

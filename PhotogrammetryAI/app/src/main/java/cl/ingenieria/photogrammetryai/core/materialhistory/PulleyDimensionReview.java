package cl.ingenieria.photogrammetryai.core.materialhistory;

import cl.ingenieria.photogrammetryai.core.materialhistory.MaterialPulleyKnowledgeBase.DimensionKind;
import cl.ingenieria.photogrammetryai.core.materialhistory.PulleyMaterialIdentificationEngine.Action;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * Guided post-identification dimensional validation.
 *
 * <p>Historical or scan-derived values are suggestions, never measurements. A value becomes usable
 * for a rigid overlay only after the operator confirms it or supplies a corrected field value.</p>
 */
public final class PulleyDimensionReview {
    public enum Priority {
        CRITICAL,
        IMPORTANT,
        OPTIONAL
    }

    public enum Origin {
        USER_INPUT,
        HISTORICAL_CONSENSUS,
        SCAN_ESTIMATE,
        FUSED_HISTORY_AND_SCAN
    }

    public enum ResponseStatus {
        PENDING,
        CONFIRMED_MATCH,
        CORRECTED,
        DOES_NOT_MATCH,
        NOT_MEASURED
    }

    public static final class Suggestion {
        private final DimensionKind kind;
        private final String label;
        private final double recommendedValueMm;
        private final Double historicalValueMm;
        private final Double scanValueMm;
        private final double toleranceMm;
        private final double confidence;
        private final Priority priority;
        private final Origin origin;
        private final String sourceSummary;
        private final ResponseStatus responseStatus;
        private final Double operatorValueMm;
        private final Double deviationMm;
        private final String note;
        private final Long answeredAtEpochMs;

        public Suggestion(
                DimensionKind kind,
                String label,
                double recommendedValueMm,
                Double historicalValueMm,
                Double scanValueMm,
                double toleranceMm,
                double confidence,
                Priority priority,
                Origin origin,
                String sourceSummary,
                ResponseStatus responseStatus,
                Double operatorValueMm,
                Double deviationMm,
                String note,
                Long answeredAtEpochMs
        ) {
            this.kind = Objects.requireNonNull(kind, "kind");
            this.label = requireText(label, "label");
            this.recommendedValueMm = requirePositive(recommendedValueMm, "recommendedValueMm");
            this.historicalValueMm = optionalPositive(historicalValueMm, "historicalValueMm");
            this.scanValueMm = optionalPositive(scanValueMm, "scanValueMm");
            this.toleranceMm = requirePositive(toleranceMm, "toleranceMm");
            this.confidence = requireConfidence(confidence, "confidence");
            this.priority = Objects.requireNonNull(priority, "priority");
            this.origin = Objects.requireNonNull(origin, "origin");
            this.sourceSummary = sourceSummary == null ? "" : sourceSummary.trim();
            this.responseStatus = Objects.requireNonNull(responseStatus, "responseStatus");
            this.operatorValueMm = optionalPositive(operatorValueMm, "operatorValueMm");
            this.deviationMm = deviationMm;
            this.note = note == null ? "" : note.trim();
            this.answeredAtEpochMs = answeredAtEpochMs;
            validateResponseState();
        }

        public static Suggestion pending(
                DimensionKind kind,
                String label,
                double recommendedValueMm,
                Double historicalValueMm,
                Double scanValueMm,
                double toleranceMm,
                double confidence,
                Priority priority,
                Origin origin,
                String sourceSummary
        ) {
            return new Suggestion(
                    kind,
                    label,
                    recommendedValueMm,
                    historicalValueMm,
                    scanValueMm,
                    toleranceMm,
                    confidence,
                    priority,
                    origin,
                    sourceSummary,
                    ResponseStatus.PENDING,
                    null,
                    null,
                    "",
                    null
            );
        }

        public static Suggestion confirmedInput(
                DimensionKind kind,
                String label,
                double valueMm,
                Double historicalValueMm,
                double toleranceMm,
                String sourceSummary,
                long answeredAtEpochMs
        ) {
            return new Suggestion(
                    kind,
                    label,
                    valueMm,
                    historicalValueMm,
                    valueMm,
                    toleranceMm,
                    1.0,
                    Priority.CRITICAL,
                    Origin.USER_INPUT,
                    sourceSummary,
                    ResponseStatus.CONFIRMED_MATCH,
                    valueMm,
                    0.0,
                    "Dato obligatorio ingresado por el operador",
                    answeredAtEpochMs
            );
        }

        public Suggestion answer(
                ResponseStatus status,
                Double measuredValueMm,
                String responseNote,
                long answeredAtEpochMs
        ) {
            Objects.requireNonNull(status, "status");
            if (status == ResponseStatus.PENDING) {
                throw new IllegalArgumentException("A response cannot return a suggestion to PENDING");
            }
            Double value = optionalPositive(measuredValueMm, "measuredValueMm");
            if (status == ResponseStatus.CONFIRMED_MATCH) {
                if (value == null) value = recommendedValueMm;
                if (Math.abs(value - recommendedValueMm) > toleranceMm) {
                    throw new IllegalArgumentException(
                            "CONFIRMED_MATCH value is outside tolerance; use CORRECTED or DOES_NOT_MATCH"
                    );
                }
            } else if (status == ResponseStatus.CORRECTED && value == null) {
                throw new IllegalArgumentException("CORRECTED requires the measured field value");
            } else if (status == ResponseStatus.NOT_MEASURED) {
                value = null;
            }
            Double deviation = value == null ? null : value - recommendedValueMm;
            return new Suggestion(
                    kind,
                    label,
                    recommendedValueMm,
                    historicalValueMm,
                    scanValueMm,
                    toleranceMm,
                    confidence,
                    priority,
                    origin,
                    sourceSummary,
                    status,
                    value,
                    deviation,
                    responseNote,
                    answeredAtEpochMs
            );
        }

        private void validateResponseState() {
            if (responseStatus == ResponseStatus.PENDING && answeredAtEpochMs != null) {
                throw new IllegalArgumentException("Pending suggestion cannot have answeredAtEpochMs");
            }
            if (responseStatus == ResponseStatus.CORRECTED && operatorValueMm == null) {
                throw new IllegalArgumentException("Corrected suggestion requires operatorValueMm");
            }
            if (responseStatus == ResponseStatus.CONFIRMED_MATCH && operatorValueMm == null) {
                throw new IllegalArgumentException("Confirmed suggestion requires operatorValueMm");
            }
        }

        public DimensionKind kind() { return kind; }
        public String label() { return label; }
        public double recommendedValueMm() { return recommendedValueMm; }
        public Optional<Double> historicalValueMm() { return Optional.ofNullable(historicalValueMm); }
        public Optional<Double> scanValueMm() { return Optional.ofNullable(scanValueMm); }
        public double toleranceMm() { return toleranceMm; }
        public double confidence() { return confidence; }
        public Priority priority() { return priority; }
        public Origin origin() { return origin; }
        public String sourceSummary() { return sourceSummary; }
        public ResponseStatus responseStatus() { return responseStatus; }
        public Optional<Double> operatorValueMm() { return Optional.ofNullable(operatorValueMm); }
        public Optional<Double> deviationMm() { return Optional.ofNullable(deviationMm); }
        public String note() { return note; }
        public Optional<Long> answeredAtEpochMs() { return Optional.ofNullable(answeredAtEpochMs); }

        public boolean resolved() {
            return responseStatus != ResponseStatus.PENDING;
        }

        public boolean acceptedForGeometry() {
            return responseStatus == ResponseStatus.CONFIRMED_MATCH
                    || responseStatus == ResponseStatus.CORRECTED;
        }

        public double effectiveValueMm() {
            return acceptedForGeometry() && operatorValueMm != null
                    ? operatorValueMm
                    : recommendedValueMm;
        }
    }

    private final String sessionId;
    private final String materialCode;
    private final Action identificationAction;
    private final long createdAtEpochMs;
    private final List<Suggestion> suggestions;

    public PulleyDimensionReview(
            String sessionId,
            String materialCode,
            Action identificationAction,
            long createdAtEpochMs,
            List<Suggestion> suggestions
    ) {
        this.sessionId = requireText(sessionId, "sessionId");
        this.materialCode = MaterialPulleyKnowledgeBase.normalizeMaterialCode(materialCode);
        this.identificationAction = Objects.requireNonNull(identificationAction, "identificationAction");
        if (createdAtEpochMs < 0L) throw new IllegalArgumentException("createdAtEpochMs must be >= 0");
        this.createdAtEpochMs = createdAtEpochMs;
        Objects.requireNonNull(suggestions, "suggestions");
        List<Suggestion> copy = new ArrayList<>();
        for (Suggestion suggestion : suggestions) {
            if (findByKind(copy, suggestion.kind()) != null) {
                throw new IllegalArgumentException("Duplicate dimension kind: " + suggestion.kind());
            }
            copy.add(Objects.requireNonNull(suggestion, "suggestion"));
        }
        this.suggestions = Collections.unmodifiableList(copy);
    }

    public PulleyDimensionReview answer(
            DimensionKind kind,
            ResponseStatus status,
            Double measuredValueMm,
            String note,
            long answeredAtEpochMs
    ) {
        List<Suggestion> updated = new ArrayList<>();
        boolean found = false;
        for (Suggestion suggestion : suggestions) {
            if (suggestion.kind() == kind) {
                updated.add(suggestion.answer(status, measuredValueMm, note, answeredAtEpochMs));
                found = true;
            } else {
                updated.add(suggestion);
            }
        }
        if (!found) throw new IllegalArgumentException("Unknown suggested dimension: " + kind);
        return new PulleyDimensionReview(
                sessionId,
                materialCode,
                identificationAction,
                createdAtEpochMs,
                updated
        );
    }

    public Optional<Suggestion> suggestion(DimensionKind kind) {
        for (Suggestion suggestion : suggestions) {
            if (suggestion.kind() == kind) return Optional.of(suggestion);
        }
        return Optional.empty();
    }

    public boolean allCriticalResolved() {
        for (Suggestion suggestion : suggestions) {
            if (suggestion.priority() == Priority.CRITICAL && !suggestion.resolved()) return false;
        }
        return true;
    }

    public boolean canUseRigidOverlay() {
        if (identificationAction == Action.VERIFY_VARIANT
                || identificationAction == Action.NO_MATCH
                || identificationAction == Action.VISUAL_ONLY) {
            return false;
        }
        boolean hasCritical = false;
        for (Suggestion suggestion : suggestions) {
            if (suggestion.priority() != Priority.CRITICAL) continue;
            hasCritical = true;
            if (!suggestion.acceptedForGeometry()) return false;
        }
        return hasCritical;
    }

    public int pendingCount() {
        int count = 0;
        for (Suggestion suggestion : suggestions) {
            if (suggestion.responseStatus() == ResponseStatus.PENDING) count++;
        }
        return count;
    }

    public String sessionId() { return sessionId; }
    public String materialCode() { return materialCode; }
    public Action identificationAction() { return identificationAction; }
    public long createdAtEpochMs() { return createdAtEpochMs; }
    public List<Suggestion> suggestions() { return suggestions; }

    private static Suggestion findByKind(List<Suggestion> values, DimensionKind kind) {
        for (Suggestion value : values) if (value.kind() == kind) return value;
        return null;
    }

    private static String requireText(String value, String name) {
        if (value == null || value.trim().isEmpty()) {
            throw new IllegalArgumentException(name + " must not be blank");
        }
        return value.trim();
    }

    private static double requirePositive(double value, String name) {
        if (!Double.isFinite(value) || value <= 0.0) {
            throw new IllegalArgumentException(name + " must be positive and finite");
        }
        return value;
    }

    private static Double optionalPositive(Double value, String name) {
        if (value == null) return null;
        requirePositive(value, name);
        return value;
    }

    private static double requireConfidence(double value, String name) {
        if (!Double.isFinite(value) || value < 0.0 || value > 1.0) {
            throw new IllegalArgumentException(name + " must be between 0 and 1");
        }
        return value;
    }
}

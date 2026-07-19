package cl.ingenieria.photogrammetryai.core.materialhistory;

import cl.ingenieria.photogrammetryai.core.materialhistory.PulleyMaterialIdentificationEngine.Action;
import cl.ingenieria.photogrammetryai.core.materialhistory.PulleyMaterialIdentificationEngine.LengthStatus;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/** Immutable trace of one local pulley-identification decision. */
public final class IdentificationAudit {
    public static final class RankedCandidate {
        private final int rank;
        private final String materialCode;
        private final double score;
        private final LengthStatus lengthStatus;
        private final Action action;
        private final List<String> reasons;
        private final List<String> warnings;

        public RankedCandidate(
                int rank,
                String materialCode,
                double score,
                LengthStatus lengthStatus,
                Action action,
                List<String> reasons,
                List<String> warnings
        ) {
            if (rank <= 0) throw new IllegalArgumentException("rank must be positive");
            if (!Double.isFinite(score) || score < 0.0 || score > 1.0) {
                throw new IllegalArgumentException("score must be between 0 and 1");
            }
            this.rank = rank;
            this.materialCode = MaterialPulleyKnowledgeBase.normalizeMaterialCode(materialCode);
            this.score = score;
            this.lengthStatus = Objects.requireNonNull(lengthStatus, "lengthStatus");
            this.action = Objects.requireNonNull(action, "action");
            this.reasons = immutableTextList(reasons);
            this.warnings = immutableTextList(warnings);
        }

        public int rank() { return rank; }
        public String materialCode() { return materialCode; }
        public double score() { return score; }
        public LengthStatus lengthStatus() { return lengthStatus; }
        public Action action() { return action; }
        public List<String> reasons() { return reasons; }
        public List<String> warnings() { return warnings; }
    }

    private final String sessionId;
    private final long createdAtEpochMs;
    private final double shellLengthMm;
    private final String enteredMaterialCode;
    private final String enteredOt;
    private final Double measuredShellDiameterMm;
    private final String description;
    private final String selectedMaterialCode;
    private final Action decision;
    private final boolean operatorConfirmed;
    private final List<RankedCandidate> candidates;

    public IdentificationAudit(
            String sessionId,
            long createdAtEpochMs,
            double shellLengthMm,
            String enteredMaterialCode,
            String enteredOt,
            Double measuredShellDiameterMm,
            String description,
            String selectedMaterialCode,
            Action decision,
            boolean operatorConfirmed,
            List<RankedCandidate> candidates
    ) {
        if (sessionId == null || sessionId.trim().isEmpty()) {
            throw new IllegalArgumentException("sessionId must not be blank");
        }
        if (createdAtEpochMs <= 0L) throw new IllegalArgumentException("createdAtEpochMs must be positive");
        if (!Double.isFinite(shellLengthMm) || shellLengthMm <= 0.0) {
            throw new IllegalArgumentException("shellLengthMm must be positive and finite");
        }
        if (measuredShellDiameterMm != null
                && (!Double.isFinite(measuredShellDiameterMm) || measuredShellDiameterMm <= 0.0)) {
            throw new IllegalArgumentException("measuredShellDiameterMm must be positive when present");
        }
        this.sessionId = sessionId.trim();
        this.createdAtEpochMs = createdAtEpochMs;
        this.shellLengthMm = shellLengthMm;
        this.enteredMaterialCode = normalizeOptionalCode(enteredMaterialCode);
        this.enteredOt = normalizeOptionalOt(enteredOt);
        this.measuredShellDiameterMm = measuredShellDiameterMm;
        this.description = description == null ? "" : description.trim();
        this.selectedMaterialCode = normalizeOptionalCode(selectedMaterialCode);
        this.decision = Objects.requireNonNull(decision, "decision");
        this.operatorConfirmed = operatorConfirmed;
        this.candidates = Collections.unmodifiableList(new ArrayList<>(
                Objects.requireNonNull(candidates, "candidates")
        ));
    }

    public String sessionId() { return sessionId; }
    public long createdAtEpochMs() { return createdAtEpochMs; }
    public double shellLengthMm() { return shellLengthMm; }
    public Optional<String> enteredMaterialCode() { return Optional.ofNullable(enteredMaterialCode); }
    public Optional<String> enteredOt() { return Optional.ofNullable(enteredOt); }
    public Optional<Double> measuredShellDiameterMm() {
        return Optional.ofNullable(measuredShellDiameterMm);
    }
    public String description() { return description; }
    public Optional<String> selectedMaterialCode() {
        return Optional.ofNullable(selectedMaterialCode);
    }
    public Action decision() { return decision; }
    public boolean operatorConfirmed() { return operatorConfirmed; }
    public List<RankedCandidate> candidates() { return candidates; }

    private static String normalizeOptionalCode(String value) {
        return value == null || value.trim().isEmpty()
                ? null
                : MaterialPulleyKnowledgeBase.normalizeMaterialCode(value);
    }

    private static String normalizeOptionalOt(String value) {
        return value == null || value.trim().isEmpty()
                ? null
                : MaterialPulleyKnowledgeBase.normalizeOt(value);
    }

    private static List<String> immutableTextList(List<String> values) {
        Objects.requireNonNull(values, "values");
        List<String> result = new ArrayList<>();
        for (String value : values) {
            if (value != null && !value.trim().isEmpty()) result.add(value.trim());
        }
        return Collections.unmodifiableList(result);
    }
}

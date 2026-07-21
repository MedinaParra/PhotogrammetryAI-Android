package cl.skm.pulleyai;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/** Combines the hard radius gate with revision-scoped axial dimensional evidence. */
public final class PulleyIdentificationDecisionCore {
    private PulleyIdentificationDecisionCore() {}

    public enum ResidualState { PASS, WARNING, CONTRADICTION, MISSING_REFERENCE }

    public static final class Measurement {
        public final RevisionedPulleyKnowledgeCore.DimensionKind kind;
        public final RevisionedPulleyKnowledgeCore.SurfaceKind surface;
        public final double valueMm;

        public Measurement(RevisionedPulleyKnowledgeCore.DimensionKind kind,
                           RevisionedPulleyKnowledgeCore.SurfaceKind surface,
                           double valueMm) {
            if (kind == null || surface == null) throw new IllegalArgumentException("kind and surface are required");
            if (!(valueMm > 0.0) || !Double.isFinite(valueMm)) throw new IllegalArgumentException("valueMm must be finite and > 0");
            this.kind = kind;
            this.surface = surface;
            this.valueMm = valueMm;
        }
    }

    public static final class MeasurementSet {
        public final RevisionedPulleyKnowledgeCore.Observation radius;
        private final Map<RevisionedPulleyKnowledgeCore.DimensionKind, Measurement> dimensions;

        private MeasurementSet(Builder builder) {
            if (builder.radius == null) throw new IllegalArgumentException("radius observation is required");
            this.radius = builder.radius;
            this.dimensions = Collections.unmodifiableMap(
                    new EnumMap<RevisionedPulleyKnowledgeCore.DimensionKind, Measurement>(builder.dimensions));
        }

        public List<Measurement> dimensions() {
            return Collections.unmodifiableList(new ArrayList<Measurement>(dimensions.values()));
        }

        public static Builder builder(RevisionedPulleyKnowledgeCore.Observation radius) {
            return new Builder(radius);
        }

        public static final class Builder {
            private final RevisionedPulleyKnowledgeCore.Observation radius;
            private final Map<RevisionedPulleyKnowledgeCore.DimensionKind, Measurement> dimensions =
                    new EnumMap<RevisionedPulleyKnowledgeCore.DimensionKind, Measurement>(
                            RevisionedPulleyKnowledgeCore.DimensionKind.class);

            private Builder(RevisionedPulleyKnowledgeCore.Observation radius) { this.radius = radius; }

            public Builder add(RevisionedPulleyKnowledgeCore.DimensionKind kind,
                               RevisionedPulleyKnowledgeCore.SurfaceKind surface,
                               double valueMm) {
                if (kind == RevisionedPulleyKnowledgeCore.DimensionKind.BARE_SHELL_RADIUS
                        || kind == RevisionedPulleyKnowledgeCore.DimensionKind.OUTER_LAGGING_RADIUS) {
                    throw new IllegalArgumentException("radius must be supplied through the radius observation");
                }
                dimensions.put(kind, new Measurement(kind, surface, valueMm));
                return this;
            }

            public MeasurementSet build() { return new MeasurementSet(this); }
        }
    }

    public static final class DimensionResidual {
        public final RevisionedPulleyKnowledgeCore.DimensionKind kind;
        public final double observedMm;
        public final Double referenceMm;
        public final Double absoluteErrorMm;
        public final Double allowedToleranceMm;
        public final ResidualState state;
        public final String sourceUri;
        public final String drawingNumber;
        public final String revision;

        DimensionResidual(RevisionedPulleyKnowledgeCore.DimensionKind kind,
                          double observedMm, Double referenceMm, Double absoluteErrorMm,
                          Double allowedToleranceMm, ResidualState state, String sourceUri,
                          String drawingNumber, String revision) {
            this.kind = kind;
            this.observedMm = observedMm;
            this.referenceMm = referenceMm;
            this.absoluteErrorMm = absoluteErrorMm;
            this.allowedToleranceMm = allowedToleranceMm;
            this.state = state;
            this.sourceUri = sourceUri;
            this.drawingNumber = drawingNumber;
            this.revision = revision;
        }

        public String summary() {
            if (state == ResidualState.MISSING_REFERENCE) {
                return kind + ": sin referencia para OT/plano/revisión";
            }
            return String.format(Locale.ROOT,
                    "%s: observado %.2f mm, referencia %.2f mm, error %.2f mm, límite %.2f mm, %s",
                    kind, observedMm, referenceMm, absoluteErrorMm, allowedToleranceMm, state);
        }
    }

    public static final class Decision {
        public final RevisionedPulleyKnowledgeCore.DecisionState state;
        public final double score;
        public final RevisionedPulleyKnowledgeCore.Decision radiusDecision;
        public final List<DimensionResidual> residuals;
        public final List<String> reasons;
        public final String fingerprint;

        Decision(RevisionedPulleyKnowledgeCore.DecisionState state, double score,
                 RevisionedPulleyKnowledgeCore.Decision radiusDecision,
                 List<DimensionResidual> residuals, List<String> reasons,
                 String fingerprint) {
            this.state = state;
            this.score = score;
            this.radiusDecision = radiusDecision;
            this.residuals = Collections.unmodifiableList(new ArrayList<DimensionResidual>(residuals));
            this.reasons = Collections.unmodifiableList(new ArrayList<String>(reasons));
            this.fingerprint = fingerprint;
        }

        public String explanation() {
            StringBuilder out = new StringBuilder();
            out.append(state).append(" · score ").append(String.format(Locale.ROOT, "%.3f", score));
            for (String reason : reasons) out.append("\n- ").append(reason);
            for (DimensionResidual residual : residuals) out.append("\n- ").append(residual.summary());
            return out.toString();
        }
    }

    public static Decision evaluate(RevisionedPulleyKnowledgeCore.Catalog catalog, MeasurementSet measurements) {
        if (catalog == null || measurements == null) throw new IllegalArgumentException("catalog and measurements are required");
        RevisionedPulleyKnowledgeCore.Decision radius = catalog.evaluateRadius(measurements.radius);
        List<String> reasons = new ArrayList<String>(radius.reasons);
        List<DimensionResidual> residuals = new ArrayList<DimensionResidual>();

        if (radius.state == RevisionedPulleyKnowledgeCore.DecisionState.BLOCKED) {
            reasons.add("La puerta primaria de radio bloqueó la identificación");
            return finish(RevisionedPulleyKnowledgeCore.DecisionState.BLOCKED, 0.0,
                    radius, residuals, reasons, measurements);
        }

        List<RevisionedPulleyKnowledgeCore.DimensionEvidence> scoped = catalog.evidenceFor(
                measurements.radius.materialCode, measurements.radius.otNumber);
        int pass = 0;
        int warning = 0;
        int contradiction = 0;
        int missing = 0;
        double weightedQuality = radiusQuality(radius);
        double totalWeight = 1.0;

        for (Measurement measurement : measurements.dimensions()) {
            List<RevisionedPulleyKnowledgeCore.DimensionEvidence> candidates = selectEvidence(
                    scoped, measurements.radius, measurement);
            if (candidates.isEmpty()) {
                residuals.add(new DimensionResidual(measurement.kind, measurement.valueMm,
                        null, null, null, ResidualState.MISSING_REFERENCE,
                        "", measurements.radius.drawingNumber, measurements.radius.revision));
                missing++;
                continue;
            }

            Set<String> revisions = new LinkedHashSet<String>();
            for (RevisionedPulleyKnowledgeCore.DimensionEvidence candidate : candidates) revisions.add(candidate.revision);
            if (measurements.radius.revision.isEmpty() && revisions.size() > 1) {
                residuals.add(new DimensionResidual(measurement.kind, measurement.valueMm,
                        null, null, null, ResidualState.MISSING_REFERENCE,
                        "", measurements.radius.drawingNumber, ""));
                reasons.add(measurement.kind + " posee revisiones activas múltiples: " + revisions);
                missing++;
                continue;
            }

            Collections.sort(candidates, new Comparator<RevisionedPulleyKnowledgeCore.DimensionEvidence>() {
                @Override public int compare(RevisionedPulleyKnowledgeCore.DimensionEvidence a,
                                             RevisionedPulleyKnowledgeCore.DimensionEvidence b) {
                    int approval = Integer.compare(approvalRank(b.approval), approvalRank(a.approval));
                    if (approval != 0) return approval;
                    return Double.compare(b.confidence, a.confidence);
                }
            });
            RevisionedPulleyKnowledgeCore.DimensionEvidence reference = candidates.get(0);
            double error = Math.abs(measurement.valueMm - reference.valueMm);
            double tolerance = Math.max(reference.drawingToleranceMm,
                    defaultTolerance(measurement.kind, reference.valueMm));
            ResidualState residualState;
            if (error <= tolerance) {
                residualState = ResidualState.PASS;
                pass++;
            } else if (error <= tolerance * 2.0) {
                residualState = ResidualState.WARNING;
                warning++;
            } else {
                residualState = ResidualState.CONTRADICTION;
                contradiction++;
            }
            residuals.add(new DimensionResidual(measurement.kind, measurement.valueMm,
                    reference.valueMm, error, tolerance, residualState,
                    reference.sourceUri, reference.drawingNumber, reference.revision));
            double weight = dimensionWeight(measurement.kind);
            totalWeight += weight;
            weightedQuality += weight * Math.max(0.0, 1.0 - error / Math.max(tolerance * 2.0, 1.0));
        }

        if (measurements.dimensions().isEmpty()) {
            reasons.add("El radio cumple, pero no se entregó ninguna cota axial para confirmar identidad");
            return finish(RevisionedPulleyKnowledgeCore.DecisionState.REVIEW,
                    radiusQuality(radius), radius, residuals, reasons, measurements);
        }

        double score = Math.max(0.0, Math.min(1.0, weightedQuality / totalWeight));
        if (contradiction > 0) {
            reasons.add("Existe al menos una contradicción dimensional superior a dos tolerancias");
            return finish(RevisionedPulleyKnowledgeCore.DecisionState.BLOCKED,
                    score, radius, residuals, reasons, measurements);
        }
        if (radius.state == RevisionedPulleyKnowledgeCore.DecisionState.REVIEW
                || warning > 0 || missing > 0 || pass == 0) {
            if (warning > 0) reasons.add(warning + " cota(s) requieren revisión por exceder la tolerancia nominal");
            if (missing > 0) reasons.add(missing + " cota(s) no tienen referencia exacta en la OT/plano/revisión");
            if (pass == 0) reasons.add("Ninguna cota axial quedó confirmada");
            return finish(RevisionedPulleyKnowledgeCore.DecisionState.REVIEW,
                    score, radius, residuals, reasons, measurements);
        }
        reasons.add("Radio y " + pass + " cota(s) axial(es) coinciden dentro de sus límites");
        return finish(RevisionedPulleyKnowledgeCore.DecisionState.MATCH,
                score, radius, residuals, reasons, measurements);
    }

    private static List<RevisionedPulleyKnowledgeCore.DimensionEvidence> selectEvidence(
            List<RevisionedPulleyKnowledgeCore.DimensionEvidence> scoped,
            RevisionedPulleyKnowledgeCore.Observation radius, Measurement measurement) {
        List<RevisionedPulleyKnowledgeCore.DimensionEvidence> result =
                new ArrayList<RevisionedPulleyKnowledgeCore.DimensionEvidence>();
        for (RevisionedPulleyKnowledgeCore.DimensionEvidence evidence : scoped) {
            if (evidence.kind != measurement.kind || evidence.surface != measurement.surface) continue;
            if (!radius.drawingNumber.isEmpty() && !evidence.drawingNumber.equals(radius.drawingNumber)) continue;
            if (!radius.revision.isEmpty() && !evidence.revision.equals(radius.revision)) continue;
            if (evidence.approval == RevisionedPulleyKnowledgeCore.ApprovalState.REJECTED
                    || evidence.approval == RevisionedPulleyKnowledgeCore.ApprovalState.SUPERSEDED) continue;
            result.add(evidence);
        }
        return result;
    }

    private static Decision finish(RevisionedPulleyKnowledgeCore.DecisionState state, double score,
                                   RevisionedPulleyKnowledgeCore.Decision radius,
                                   List<DimensionResidual> residuals, List<String> reasons,
                                   MeasurementSet measurements) {
        return new Decision(state, score, radius, residuals, reasons,
                fingerprint(state, score, residuals, measurements));
    }

    private static double radiusQuality(RevisionedPulleyKnowledgeCore.Decision radius) {
        if (!Double.isFinite(radius.radiusErrorMm)) return 0.25;
        return Math.max(0.0, 1.0 - radius.radiusErrorMm / RevisionedPulleyKnowledgeCore.MAX_RADIUS_ERROR_MM);
    }

    private static double defaultTolerance(RevisionedPulleyKnowledgeCore.DimensionKind kind, double reference) {
        switch (kind) {
            case SHELL_LENGTH:
            case FACE_WIDTH:
                return Math.max(12.0, reference * 0.0125);
            case SUPPORT_CENTRE_DISTANCE:
            case BEARING_CENTRE_DISTANCE:
                return Math.max(15.0, reference * 0.0100);
            case SHAFT_TOTAL_LENGTH:
                return Math.max(20.0, reference * 0.0100);
            case SHAFT_BEARING_DIAMETER:
            case SHAFT_LOCKING_DIAMETER:
            case SUPPORT_BORE_DIAMETER:
                return Math.max(2.0, reference * 0.0050);
            case LAGGING_THICKNESS:
            case SHELL_THICKNESS:
                return Math.max(2.0, reference * 0.0500);
            default:
                return Math.max(5.0, reference * 0.0100);
        }
    }

    private static double dimensionWeight(RevisionedPulleyKnowledgeCore.DimensionKind kind) {
        switch (kind) {
            case SHELL_LENGTH: return 1.5;
            case FACE_WIDTH: return 1.2;
            case SUPPORT_CENTRE_DISTANCE:
            case BEARING_CENTRE_DISTANCE: return 1.2;
            case SHAFT_TOTAL_LENGTH: return 1.0;
            default: return 0.7;
        }
    }

    private static int approvalRank(RevisionedPulleyKnowledgeCore.ApprovalState state) {
        if (state == RevisionedPulleyKnowledgeCore.ApprovalState.APPROVED) return 5;
        if (state == RevisionedPulleyKnowledgeCore.ApprovalState.REVIEWED) return 4;
        if (state == RevisionedPulleyKnowledgeCore.ApprovalState.DRAFT) return 3;
        if (state == RevisionedPulleyKnowledgeCore.ApprovalState.SUPERSEDED) return 2;
        return 1;
    }

    private static String fingerprint(RevisionedPulleyKnowledgeCore.DecisionState state, double score,
                                      List<DimensionResidual> residuals, MeasurementSet measurements) {
        StringBuilder source = new StringBuilder();
        source.append(measurements.radius.materialCode).append('|')
                .append(measurements.radius.otNumber).append('|')
                .append(measurements.radius.drawingNumber).append('|')
                .append(measurements.radius.revision).append('|')
                .append(measurements.radius.radiusKind).append('|')
                .append(measurements.radius.surface).append('|')
                .append(String.format(Locale.ROOT, "%.6f", measurements.radius.observedRadiusMm)).append('|')
                .append(state).append('|').append(String.format(Locale.ROOT, "%.6f", score));
        for (DimensionResidual residual : residuals) {
            source.append('|').append(residual.kind).append(':')
                    .append(String.format(Locale.ROOT, "%.6f", residual.observedMm)).append(':')
                    .append(residual.referenceMm).append(':').append(residual.state);
        }
        long hash = 0xcbf29ce484222325L;
        for (int i = 0; i < source.length(); i++) { hash ^= source.charAt(i); hash *= 0x100000001b3L; }
        String hex = Long.toHexString(hash);
        StringBuilder out = new StringBuilder(64);
        while (out.length() < 64) out.append(hex);
        return out.substring(0, 64);
    }
}

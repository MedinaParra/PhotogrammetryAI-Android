package cl.skm.pulleyai;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * Pure-Java revisioned pulley knowledge model.
 * Every dimensional fact is scoped to material code, work order, drawing, revision,
 * dimension kind and surface kind. Missing or contradictory evidence can never be
 * promoted to an automatic match.
 */
public final class RevisionedPulleyKnowledgeCore {
    public static final double MAX_RADIUS_ERROR_MM = 30.0;

    private RevisionedPulleyKnowledgeCore() {}

    public enum Unit {
        MM(1.0), INCH(25.4);
        final double toMillimetres;
        Unit(double toMillimetres) { this.toMillimetres = toMillimetres; }
        public double toMm(double value) { return value * toMillimetres; }
    }

    public enum DimensionKind {
        BARE_SHELL_RADIUS,
        OUTER_LAGGING_RADIUS,
        SHELL_LENGTH,
        FACE_WIDTH,
        SUPPORT_CENTRE_DISTANCE,
        BEARING_CENTRE_DISTANCE,
        SHAFT_TOTAL_LENGTH,
        SHAFT_BEARING_DIAMETER,
        SHAFT_LOCKING_DIAMETER,
        SUPPORT_BORE_DIAMETER,
        LAGGING_THICKNESS,
        SHELL_THICKNESS
    }

    public enum SurfaceKind { BARE_SHELL, OUTER_LAGGING, AXIAL, SHAFT, SUPPORT, NOT_APPLICABLE }
    public enum ApprovalState { DRAFT, REVIEWED, APPROVED, REJECTED, SUPERSEDED }
    public enum ConflictSeverity { INFO, WARNING, BLOCKING }
    public enum DecisionState { MATCH, REVIEW, BLOCKED }

    public static final class DimensionEvidence {
        public final String materialCode;
        public final String otNumber;
        public final String drawingNumber;
        public final String revision;
        public final DimensionKind kind;
        public final SurfaceKind surface;
        public final double originalValue;
        public final Unit originalUnit;
        public final double valueMm;
        public final double drawingToleranceMm;
        public final ApprovalState approval;
        public final String sourceUri;
        public final String sourceSha256;
        public final String extractionMethod;
        public final double confidence;

        private DimensionEvidence(Builder builder) {
            materialCode = normalizeCode(builder.materialCode);
            otNumber = normalizeOt(builder.otNumber);
            drawingNumber = required(builder.drawingNumber, "drawingNumber");
            revision = required(builder.revision, "revision");
            kind = required(builder.kind, "kind");
            surface = required(builder.surface, "surface");
            originalValue = positive(builder.originalValue, "originalValue");
            originalUnit = required(builder.originalUnit, "originalUnit");
            valueMm = originalUnit.toMm(originalValue);
            drawingToleranceMm = nonNegative(builder.drawingToleranceMm, "drawingToleranceMm");
            approval = required(builder.approval, "approval");
            sourceUri = required(builder.sourceUri, "sourceUri");
            sourceSha256 = required(builder.sourceSha256, "sourceSha256");
            extractionMethod = required(builder.extractionMethod, "extractionMethod");
            confidence = bounded(builder.confidence, 0.0, 1.0, "confidence");
            validateSurface(kind, surface);
        }

        public String logicalKey() {
            return materialCode + "|" + otNumber + "|" + drawingNumber + "|" + revision
                    + "|" + kind + "|" + surface;
        }

        public static Builder builder() { return new Builder(); }

        public static final class Builder {
            private String materialCode;
            private String otNumber;
            private String drawingNumber;
            private String revision;
            private DimensionKind kind;
            private SurfaceKind surface;
            private double originalValue;
            private Unit originalUnit = Unit.MM;
            private double drawingToleranceMm;
            private ApprovalState approval = ApprovalState.REVIEWED;
            private String sourceUri;
            private String sourceSha256;
            private String extractionMethod = "MANUAL_AUDIT";
            private double confidence = 1.0;

            public Builder material(String value) { materialCode = value; return this; }
            public Builder ot(String value) { otNumber = value; return this; }
            public Builder drawing(String value) { drawingNumber = value; return this; }
            public Builder revision(String value) { revision = value; return this; }
            public Builder kind(DimensionKind value) { kind = value; return this; }
            public Builder surface(SurfaceKind value) { surface = value; return this; }
            public Builder value(double value, Unit unit) { originalValue = value; originalUnit = unit; return this; }
            public Builder toleranceMm(double value) { drawingToleranceMm = value; return this; }
            public Builder approval(ApprovalState value) { approval = value; return this; }
            public Builder source(String uri, String sha256) { sourceUri = uri; sourceSha256 = sha256; return this; }
            public Builder extraction(String value) { extractionMethod = value; return this; }
            public Builder confidence(double value) { confidence = value; return this; }
            public DimensionEvidence build() { return new DimensionEvidence(this); }
        }
    }

    public static final class ValidationConflict {
        public final String id;
        public final String materialCode;
        public final ConflictSeverity severity;
        public final String description;
        public final Set<String> affectedOts;

        public ValidationConflict(String id, String materialCode, ConflictSeverity severity,
                                  String description, Set<String> affectedOts) {
            this.id = required(id, "id");
            this.materialCode = normalizeCode(materialCode);
            this.severity = required(severity, "severity");
            this.description = required(description, "description");
            Set<String> normalized = new LinkedHashSet<String>();
            for (String ot : affectedOts == null ? Collections.<String>emptySet() : affectedOts) {
                normalized.add(normalizeOt(ot));
            }
            this.affectedOts = Collections.unmodifiableSet(normalized);
        }

        public boolean appliesTo(String ot) {
            return affectedOts.isEmpty() || affectedOts.contains(normalizeOt(ot));
        }
    }

    public static final class Observation {
        public final String materialCode;
        public final String otNumber;
        public final String drawingNumber;
        public final String revision;
        public final DimensionKind radiusKind;
        public final SurfaceKind surface;
        public final double observedRadiusMm;

        public Observation(String materialCode, String otNumber, String drawingNumber,
                           String revision, DimensionKind radiusKind, SurfaceKind surface,
                           double observedRadiusMm) {
            this.materialCode = normalizeCode(materialCode);
            this.otNumber = normalizeOt(otNumber);
            this.drawingNumber = drawingNumber == null ? "" : drawingNumber.trim();
            this.revision = revision == null ? "" : revision.trim();
            this.radiusKind = required(radiusKind, "radiusKind");
            this.surface = required(surface, "surface");
            this.observedRadiusMm = positive(observedRadiusMm, "observedRadiusMm");
            if (radiusKind != DimensionKind.BARE_SHELL_RADIUS
                    && radiusKind != DimensionKind.OUTER_LAGGING_RADIUS) {
                throw new IllegalArgumentException("radiusKind must be a radius dimension");
            }
            validateSurface(radiusKind, surface);
        }
    }

    public static final class Decision {
        public final DecisionState state;
        public final double radiusErrorMm;
        public final DimensionEvidence reference;
        public final List<String> reasons;

        Decision(DecisionState state, double radiusErrorMm, DimensionEvidence reference,
                 List<String> reasons) {
            this.state = state;
            this.radiusErrorMm = radiusErrorMm;
            this.reference = reference;
            this.reasons = Collections.unmodifiableList(new ArrayList<String>(reasons));
        }

        public String explanation() {
            StringBuilder out = new StringBuilder(state.name());
            for (String reason : reasons) out.append(" | ").append(reason);
            return out.toString();
        }
    }

    public static final class Catalog {
        private final Map<String, DimensionEvidence> evidenceByKey = new LinkedHashMap<String, DimensionEvidence>();
        private final List<ValidationConflict> conflicts = new ArrayList<ValidationConflict>();

        public boolean upsert(DimensionEvidence evidence) {
            DimensionEvidence previous = evidenceByKey.put(evidence.logicalKey(), evidence);
            return previous == null;
        }

        public void addConflict(ValidationConflict conflict) { conflicts.add(conflict); }
        public int evidenceCount() { return evidenceByKey.size(); }
        public int conflictCount() { return conflicts.size(); }

        public List<DimensionEvidence> allEvidence() {
            return Collections.unmodifiableList(new ArrayList<DimensionEvidence>(evidenceByKey.values()));
        }

        public List<ValidationConflict> allConflicts() {
            return Collections.unmodifiableList(new ArrayList<ValidationConflict>(conflicts));
        }

        public List<DimensionEvidence> evidenceFor(String materialCode, String otNumber) {
            String code = normalizeCode(materialCode);
            String ot = normalizeOt(otNumber);
            List<DimensionEvidence> result = new ArrayList<DimensionEvidence>();
            for (DimensionEvidence evidence : evidenceByKey.values()) {
                if (evidence.materialCode.equals(code) && evidence.otNumber.equals(ot)) result.add(evidence);
            }
            Collections.sort(result, new Comparator<DimensionEvidence>() {
                @Override public int compare(DimensionEvidence a, DimensionEvidence b) {
                    int drawing = a.drawingNumber.compareTo(b.drawingNumber);
                    if (drawing != 0) return drawing;
                    int revision = a.revision.compareTo(b.revision);
                    if (revision != 0) return revision;
                    return a.kind.compareTo(b.kind);
                }
            });
            return result;
        }

        public Decision evaluateRadius(Observation observation) {
            List<String> reasons = new ArrayList<String>();
            List<ValidationConflict> applicableConflicts = new ArrayList<ValidationConflict>();
            for (ValidationConflict conflict : conflicts) {
                if (conflict.materialCode.equals(observation.materialCode)
                        && conflict.appliesTo(observation.otNumber)) {
                    applicableConflicts.add(conflict);
                    if (conflict.severity == ConflictSeverity.BLOCKING) {
                        reasons.add(conflict.id + ": " + conflict.description);
                    }
                }
            }
            if (!reasons.isEmpty()) return new Decision(DecisionState.BLOCKED, Double.NaN, null, reasons);

            List<DimensionEvidence> candidates = new ArrayList<DimensionEvidence>();
            for (DimensionEvidence evidence : evidenceByKey.values()) {
                if (!evidence.materialCode.equals(observation.materialCode)) continue;
                if (!evidence.otNumber.equals(observation.otNumber)) continue;
                if (evidence.kind != observation.radiusKind || evidence.surface != observation.surface) continue;
                if (!observation.drawingNumber.isEmpty()
                        && !evidence.drawingNumber.equals(observation.drawingNumber)) continue;
                if (!observation.revision.isEmpty() && !evidence.revision.equals(observation.revision)) continue;
                if (evidence.approval == ApprovalState.APPROVED
                        || evidence.approval == ApprovalState.REVIEWED) candidates.add(evidence);
            }

            if (candidates.isEmpty()) {
                reasons.add("No existe radio trazable para OT/plano/revisión/superficie solicitados");
                return new Decision(DecisionState.BLOCKED, Double.NaN, null, reasons);
            }

            if (observation.revision.isEmpty()) {
                Set<String> activeRevisions = new LinkedHashSet<String>();
                for (DimensionEvidence candidate : candidates) activeRevisions.add(candidate.revision);
                if (activeRevisions.size() > 1) {
                    reasons.add("Existen revisiones activas múltiples: " + activeRevisions);
                    return new Decision(DecisionState.REVIEW, Double.NaN, null, reasons);
                }
            }

            Collections.sort(candidates, new Comparator<DimensionEvidence>() {
                @Override public int compare(DimensionEvidence a, DimensionEvidence b) {
                    int approval = Integer.compare(approvalRank(b.approval), approvalRank(a.approval));
                    if (approval != 0) return approval;
                    return Double.compare(b.confidence, a.confidence);
                }
            });
            DimensionEvidence reference = candidates.get(0);
            double error = Math.abs(observation.observedRadiusMm - reference.valueMm);
            reasons.add(String.format(Locale.ROOT,
                    "radio observado %.2f mm vs referencia %.2f mm; error %.2f mm",
                    observation.observedRadiusMm, reference.valueMm, error));
            reasons.add("fuente=" + reference.sourceUri + " sha256=" + reference.sourceSha256);

            if (error > MAX_RADIUS_ERROR_MM) {
                reasons.add("supera el máximo de 30 mm en radio");
                return new Decision(DecisionState.BLOCKED, error, reference, reasons);
            }
            if (reference.approval != ApprovalState.APPROVED || reference.confidence < 0.95) {
                reasons.add("la evidencia no está plenamente aprobada o su confianza es inferior a 0,95");
                return new Decision(DecisionState.REVIEW, error, reference, reasons);
            }
            for (ValidationConflict conflict : applicableConflicts) {
                if (conflict.severity == ConflictSeverity.WARNING) {
                    reasons.add(conflict.id + ": " + conflict.description);
                    return new Decision(DecisionState.REVIEW, error, reference, reasons);
                }
            }
            reasons.add("cumple la puerta de radio y no presenta contradicciones críticas");
            return new Decision(DecisionState.MATCH, error, reference, reasons);
        }
    }

    public static Catalog auditedSeed() {
        Catalog catalog = new Catalog();
        seed10415863(catalog);
        seed10415860(catalog);
        seed4162054(catalog);
        seed4196149(catalog);
        seed1462827(catalog);
        catalog.addConflict(new ValidationConflict(
                "DATA-002", "4162045", ConflictSeverity.BLOCKING,
                "El código aparece asociado a equipos y descripciones incompatibles",
                set("OT-343", "OT-366", "OT-425", "OT-445", "OT-1436", "OT-1512")));
        catalog.addConflict(new ValidationConflict(
                "DATA-006", "4196111", ConflictSeverity.BLOCKING,
                "Posible conflicto o alias no resuelto con 4162038; falta plano dimensional suficiente",
                Collections.<String>emptySet()));
        catalog.addConflict(new ValidationConflict(
                "DATA-008", "10510386", ConflictSeverity.BLOCKING,
                "OT-270 no tiene radio de manto trazable en la evidencia auditada", set("OT-270")));
        return catalog;
    }

    private static void seed10415863(Catalog catalog) {
        addMm(catalog, "10415863", "OT-262", "OT262-EVAL", "0", DimensionKind.BARE_SHELL_RADIUS,
                SurfaceKind.BARE_SHELL, 700.0, ApprovalState.REVIEWED, "drive://ot-262/evaluation", 0.98);
        addMm(catalog, "10415863", "OT-262", "OT262-EVAL", "0", DimensionKind.OUTER_LAGGING_RADIUS,
                SurfaceKind.OUTER_LAGGING, 722.0, ApprovalState.REVIEWED, "drive://ot-262/evaluation", 0.96);
        addMm(catalog, "10415863", "OT-262", "OT262-EVAL", "0", DimensionKind.SHELL_LENGTH,
                SurfaceKind.AXIAL, 1520.0, ApprovalState.REVIEWED, "drive://ot-262/evaluation", 0.99);
        addMm(catalog, "10415863", "OT-1702", "SKM-1702-03", "0", DimensionKind.BARE_SHELL_RADIUS,
                SurfaceKind.BARE_SHELL, 700.0, ApprovalState.APPROVED, "drive://1NUouvTYi_6xYBi1-3yswyVL9KvAtxwpJ", 1.0);
        addMm(catalog, "10415863", "OT-1702", "SKM-1702-03", "0", DimensionKind.OUTER_LAGGING_RADIUS,
                SurfaceKind.OUTER_LAGGING, 720.0, ApprovalState.APPROVED, "drive://1NUouvTYi_6xYBi1-3yswyVL9KvAtxwpJ", 1.0);
        addMm(catalog, "10415863", "OT-1702", "SKM-1702-03", "0", DimensionKind.SHELL_LENGTH,
                SurfaceKind.AXIAL, 1520.0, ApprovalState.APPROVED, "drive://1NUouvTYi_6xYBi1-3yswyVL9KvAtxwpJ", 1.0);
        addMm(catalog, "10415863", "OT-1702", "SKM-1702-03", "0", DimensionKind.SUPPORT_CENTRE_DISTANCE,
                SurfaceKind.AXIAL, 2100.0, ApprovalState.APPROVED, "drive://1NUouvTYi_6xYBi1-3yswyVL9KvAtxwpJ", 1.0);
        addMm(catalog, "10415863", "OT-1702", "SKM-1702-03", "0", DimensionKind.BEARING_CENTRE_DISTANCE,
                SurfaceKind.AXIAL, 2054.0, ApprovalState.APPROVED, "drive://1NUouvTYi_6xYBi1-3yswyVL9KvAtxwpJ", 1.0);
        addMm(catalog, "10415863", "OT-1702", "SKM-1702-03", "0", DimensionKind.SHAFT_TOTAL_LENGTH,
                SurfaceKind.SHAFT, 2388.0, ApprovalState.APPROVED, "drive://1NUouvTYi_6xYBi1-3yswyVL9KvAtxwpJ", 1.0);
    }

    private static void seed10415860(Catalog catalog) {
        addMm(catalog, "10415860", "OT-243", "OT243-EVAL", "0", DimensionKind.BARE_SHELL_RADIUS,
                SurfaceKind.BARE_SHELL, 400.0, ApprovalState.REVIEWED, "drive://ot-243/evaluation", 0.98);
        addMm(catalog, "10415860", "OT-243", "OT243-EVAL", "0", DimensionKind.OUTER_LAGGING_RADIUS,
                SurfaceKind.OUTER_LAGGING, 420.0, ApprovalState.REVIEWED, "drive://ot-243/evaluation", 0.96);
        addMm(catalog, "10415860", "OT-1645", "ARMADO-OT-1645", "00", DimensionKind.BARE_SHELL_RADIUS,
                SurfaceKind.BARE_SHELL, 400.0, ApprovalState.APPROVED, "drive://1PRTz88U6c2WMN2uQ4qmvFdnm7gkznp8O", 1.0);
        addMm(catalog, "10415860", "OT-1645", "ARMADO-OT-1645", "00", DimensionKind.OUTER_LAGGING_RADIUS,
                SurfaceKind.OUTER_LAGGING, 420.25, ApprovalState.APPROVED, "drive://1PRTz88U6c2WMN2uQ4qmvFdnm7gkznp8O", 1.0);
        addMm(catalog, "10415860", "OT-1645", "ARMADO-OT-1645", "00", DimensionKind.SHELL_LENGTH,
                SurfaceKind.AXIAL, 1520.0, ApprovalState.APPROVED, "drive://1PRTz88U6c2WMN2uQ4qmvFdnm7gkznp8O", 1.0);
        addMm(catalog, "10415860", "OT-1645", "ARMADO-OT-1645", "00", DimensionKind.SUPPORT_CENTRE_DISTANCE,
                SurfaceKind.AXIAL, 2108.0, ApprovalState.APPROVED, "drive://1PRTz88U6c2WMN2uQ4qmvFdnm7gkznp8O", 1.0);
        addMm(catalog, "10415860", "OT-1645", "ARMADO-OT-1645", "00", DimensionKind.BEARING_CENTRE_DISTANCE,
                SurfaceKind.AXIAL, 2084.0, ApprovalState.APPROVED, "drive://1PRTz88U6c2WMN2uQ4qmvFdnm7gkznp8O", 1.0);
        addMm(catalog, "10415860", "OT-1645", "ARMADO-OT-1645", "00", DimensionKind.SHAFT_TOTAL_LENGTH,
                SurfaceKind.SHAFT, 2292.0, ApprovalState.APPROVED, "drive://1PRTz88U6c2WMN2uQ4qmvFdnm7gkznp8O", 1.0);
    }

    private static void seed4162054(Catalog catalog) {
        String uri = "drive://11JT4Igi-Hd0W_9yHSmbrm3nImzZ29yxK";
        addMm(catalog, "4162054", "OT-651", "147CV014", "0", DimensionKind.BARE_SHELL_RADIUS,
                SurfaceKind.BARE_SHELL, 304.80, ApprovalState.APPROVED, uri, 1.0);
        addMm(catalog, "4162054", "OT-651", "147CV014", "0", DimensionKind.OUTER_LAGGING_RADIUS,
                SurfaceKind.OUTER_LAGGING, 336.55, ApprovalState.APPROVED, uri, 1.0);
        addMm(catalog, "4162054", "OT-651", "147CV014", "0", DimensionKind.FACE_WIDTH,
                SurfaceKind.AXIAL, 1524.0, ApprovalState.APPROVED, uri, 1.0);
        addMm(catalog, "4162054", "OT-651", "147CV014", "0", DimensionKind.SHELL_LENGTH,
                SurfaceKind.AXIAL, 2032.0, ApprovalState.APPROVED, uri, 1.0);
        addMm(catalog, "4162054", "OT-651", "147CV014", "0", DimensionKind.SHAFT_TOTAL_LENGTH,
                SurfaceKind.SHAFT, 3051.18, ApprovalState.APPROVED, uri, 1.0);
    }

    private static void seed4196149(Catalog catalog) {
        catalog.upsert(DimensionEvidence.builder().material("4196149").ot("OT-781")
                .drawing("2-POLEA-MOTRIZ-4196149").revision("0")
                .kind(DimensionKind.OUTER_LAGGING_RADIUS).surface(SurfaceKind.OUTER_LAGGING)
                .value(15.0, Unit.INCH).approval(ApprovalState.APPROVED)
                .source("drive://1i99kKPDTQqKbhRu1ULNBcRpZqnPnVakT", syntheticSha("4196149-radius"))
                .confidence(1.0).build());
        catalog.upsert(DimensionEvidence.builder().material("4196149").ot("OT-781")
                .drawing("2-POLEA-MOTRIZ-4196149").revision("0")
                .kind(DimensionKind.FACE_WIDTH).surface(SurfaceKind.AXIAL)
                .value(90.0, Unit.INCH).approval(ApprovalState.APPROVED)
                .source("drive://1i99kKPDTQqKbhRu1ULNBcRpZqnPnVakT", syntheticSha("4196149-face"))
                .confidence(1.0).build());
    }

    private static void seed1462827(Catalog catalog) {
        String uri = "drive://1mRnfRlIzKPN6R6ux_FQi9d3DpfQ-bxdu";
        addMm(catalog, "1462827", "OT-1570", "SKM-1570", "1", DimensionKind.BARE_SHELL_RADIUS,
                SurfaceKind.BARE_SHELL, 508.0, ApprovalState.APPROVED, uri, 1.0);
        addMm(catalog, "1462827", "OT-1570", "SKM-1570", "1", DimensionKind.OUTER_LAGGING_RADIUS,
                SurfaceKind.OUTER_LAGGING, 523.0, ApprovalState.APPROVED, uri, 1.0);
        addMm(catalog, "1462827", "OT-1570", "SKM-1570", "1", DimensionKind.SHELL_LENGTH,
                SurfaceKind.AXIAL, 2100.0, ApprovalState.APPROVED, uri, 1.0);
        addMm(catalog, "1462827", "OT-1570", "SKM-1570", "1", DimensionKind.SUPPORT_CENTRE_DISTANCE,
                SurfaceKind.AXIAL, 2630.0, ApprovalState.APPROVED, uri, 1.0);
        addMm(catalog, "1462827", "OT-1570", "SKM-1570", "1", DimensionKind.BEARING_CENTRE_DISTANCE,
                SurfaceKind.AXIAL, 2630.0, ApprovalState.APPROVED, uri, 1.0);
        addMm(catalog, "1462827", "OT-1570", "SKM-1570", "1", DimensionKind.SHAFT_TOTAL_LENGTH,
                SurfaceKind.SHAFT, 2870.0, ApprovalState.APPROVED, uri, 1.0);
    }

    private static void addMm(Catalog catalog, String material, String ot, String drawing, String revision,
                              DimensionKind kind, SurfaceKind surface, double value,
                              ApprovalState approval, String sourceUri, double confidence) {
        catalog.upsert(DimensionEvidence.builder().material(material).ot(ot).drawing(drawing).revision(revision)
                .kind(kind).surface(surface).value(value, Unit.MM).approval(approval)
                .source(sourceUri, syntheticSha(material + ot + drawing + revision + kind + value))
                .confidence(confidence).build());
    }

    private static int approvalRank(ApprovalState state) {
        if (state == ApprovalState.APPROVED) return 5;
        if (state == ApprovalState.REVIEWED) return 4;
        if (state == ApprovalState.DRAFT) return 3;
        if (state == ApprovalState.SUPERSEDED) return 2;
        return 1;
    }

    private static void validateSurface(DimensionKind kind, SurfaceKind surface) {
        if (kind == DimensionKind.BARE_SHELL_RADIUS && surface != SurfaceKind.BARE_SHELL) {
            throw new IllegalArgumentException("BARE_SHELL_RADIUS requires BARE_SHELL surface");
        }
        if (kind == DimensionKind.OUTER_LAGGING_RADIUS && surface != SurfaceKind.OUTER_LAGGING) {
            throw new IllegalArgumentException("OUTER_LAGGING_RADIUS requires OUTER_LAGGING surface");
        }
    }

    private static String normalizeCode(String raw) {
        String value = required(raw, "materialCode").toUpperCase(Locale.ROOT);
        value = value.replaceFirst("^(?:STOCK\\s*CODE|C[ÓO]DIGO\\s*(?:DE\\s*)?MATERIAL|C[ÓO]DIGO\\s*SAP|SAP|SC)\\s*[:#-]?\\s*", "");
        value = value.replaceAll("[^A-Z0-9]", "");
        if (value.isEmpty()) throw new IllegalArgumentException("materialCode is empty after normalization");
        return value;
    }

    private static String normalizeOt(String raw) {
        String value = required(raw, "otNumber").toUpperCase(Locale.ROOT).replaceAll("[^0-9]", "");
        value = value.replaceFirst("^0+(?!$)", "");
        if (value.isEmpty()) throw new IllegalArgumentException("otNumber has no digits");
        return "OT-" + value;
    }

    private static String syntheticSha(String value) {
        long a = 0xcbf29ce484222325L;
        for (int i = 0; i < value.length(); i++) { a ^= value.charAt(i); a *= 0x100000001b3L; }
        String hex = Long.toHexString(a);
        StringBuilder out = new StringBuilder(64);
        while (out.length() < 64) out.append(hex);
        return out.substring(0, 64);
    }

    private static Set<String> set(String... values) {
        Set<String> result = new LinkedHashSet<String>();
        Collections.addAll(result, values);
        return result;
    }

    private static <T> T required(T value, String name) {
        if (value == null) throw new IllegalArgumentException(name + " is required");
        if (value instanceof String && ((String) value).trim().isEmpty()) throw new IllegalArgumentException(name + " is required");
        return value;
    }

    private static double positive(double value, String name) {
        if (!(value > 0.0) || Double.isNaN(value) || Double.isInfinite(value)) throw new IllegalArgumentException(name + " must be finite and > 0");
        return value;
    }

    private static double nonNegative(double value, String name) {
        if (value < 0.0 || Double.isNaN(value) || Double.isInfinite(value)) throw new IllegalArgumentException(name + " must be finite and >= 0");
        return value;
    }

    private static double bounded(double value, double min, double max, String name) {
        if (value < min || value > max || Double.isNaN(value) || Double.isInfinite(value)) throw new IllegalArgumentException(name + " outside [" + min + "," + max + "]");
        return value;
    }
}

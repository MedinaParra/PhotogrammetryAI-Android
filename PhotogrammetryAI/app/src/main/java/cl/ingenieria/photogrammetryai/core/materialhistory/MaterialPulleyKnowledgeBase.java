package cl.ingenieria.photogrammetryai.core.materialhistory;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Versioned, dependency-free graph relating pulley material codes to their intervention history.
 *
 * <p>A material/SAP/stock code identifies a technical family. An OT identifies one intervention.
 * Equipment positions and descriptions are aliases/instances and must not be treated as unique
 * family identifiers. Every fact retains its source so current measurements can be separated from
 * inherited historical evidence.</p>
 */
public final class MaterialPulleyKnowledgeBase {
    public static final String VERSION = "MATERIAL_PULLEY_KB_V1";

    private static final Pattern OT_DIGITS = Pattern.compile("(?i)(?:OT[^0-9]*)?([0-9]{2,6})");

    public enum ReportPhase {
        RECEIPT,
        EVALUATION,
        FINDINGS,
        REPAIR,
        ASSEMBLY,
        PRESERVATION,
        DRAWING,
        WIP,
        OTHER
    }

    public enum DimensionKind {
        SHELL_LENGTH,
        SHELL_DIAMETER,
        SHELL_THICKNESS,
        LAGGING_THICKNESS,
        BELT_FACE_WIDTH,
        SHAFT_TOTAL_LENGTH,
        BEARING_CENTRE_DISTANCE,
        SUPPORT_CENTRE_DISTANCE,
        SHAFT_BEARING_DIAMETER,
        SHAFT_LOCKING_DIAMETER,
        SUPPORT_BORE_DIAMETER
    }

    public enum ComponentKind {
        SHELL,
        SHAFT,
        SUPPORT,
        BEARING,
        BEARING_ADAPTER,
        LOCKING_ELEMENT,
        SEAL,
        LAGGING,
        COUPLING,
        BACKSTOP,
        TRANSPORT_FRAME,
        FASTENER,
        OTHER
    }

    public enum Condition {
        REUSE,
        REPAIR,
        REPLACE,
        REJECT,
        UNKNOWN
    }

    public static final class SourceDocument {
        private final String id;
        private final String title;
        private final String uri;
        private final ReportPhase phase;
        private final String ot;
        private final String materialCode;
        private final String documentDate;

        public SourceDocument(
                String id,
                String title,
                String uri,
                ReportPhase phase,
                String ot,
                String materialCode,
                String documentDate
        ) {
            this.id = requireText(id, "id");
            this.title = requireText(title, "title");
            this.uri = requireText(uri, "uri");
            this.phase = Objects.requireNonNull(phase, "phase");
            this.ot = ot == null || ot.trim().isEmpty() ? null : normalizeOt(ot);
            this.materialCode = materialCode == null || materialCode.trim().isEmpty()
                    ? null
                    : normalizeMaterialCode(materialCode);
            this.documentDate = documentDate == null ? "" : documentDate.trim();
        }

        public String id() { return id; }
        public String title() { return title; }
        public String uri() { return uri; }
        public ReportPhase phase() { return phase; }
        public Optional<String> ot() { return Optional.ofNullable(ot); }
        public Optional<String> materialCode() { return Optional.ofNullable(materialCode); }
        public String documentDate() { return documentDate; }
    }

    public static final class DimensionEvidence {
        private final DimensionKind kind;
        private final double valueMm;
        private final String semanticLabel;
        private final String sourceId;
        private final double confidence;

        public DimensionEvidence(
                DimensionKind kind,
                double valueMm,
                String semanticLabel,
                String sourceId,
                double confidence
        ) {
            this.kind = Objects.requireNonNull(kind, "kind");
            this.valueMm = requirePositive(valueMm, "valueMm");
            this.semanticLabel = requireText(semanticLabel, "semanticLabel");
            this.sourceId = requireText(sourceId, "sourceId");
            this.confidence = requireConfidence(confidence, "confidence");
        }

        public DimensionKind kind() { return kind; }
        public double valueMm() { return valueMm; }
        public String semanticLabel() { return semanticLabel; }
        public String sourceId() { return sourceId; }
        public double confidence() { return confidence; }
    }

    public static final class ComponentEvidence {
        private final ComponentKind kind;
        private final String manufacturer;
        private final String model;
        private final Condition condition;
        private final String note;
        private final String sourceId;
        private final double confidence;

        public ComponentEvidence(
                ComponentKind kind,
                String manufacturer,
                String model,
                Condition condition,
                String note,
                String sourceId,
                double confidence
        ) {
            this.kind = Objects.requireNonNull(kind, "kind");
            this.manufacturer = manufacturer == null ? "" : manufacturer.trim();
            this.model = model == null ? "" : model.trim();
            this.condition = Objects.requireNonNull(condition, "condition");
            this.note = note == null ? "" : note.trim();
            this.sourceId = requireText(sourceId, "sourceId");
            this.confidence = requireConfidence(confidence, "confidence");
        }

        public ComponentKind kind() { return kind; }
        public String manufacturer() { return manufacturer; }
        public String model() { return model; }
        public Condition condition() { return condition; }
        public String note() { return note; }
        public String sourceId() { return sourceId; }
        public double confidence() { return confidence; }
    }

    public static final class InterventionEvent {
        private final String ot;
        private final int year;
        private final ReportPhase phase;
        private final String client;
        private final String componentDescription;
        private final String purchaseOrder;
        private final Set<String> sourceIds;
        private final List<String> notes;

        public InterventionEvent(
                String ot,
                int year,
                ReportPhase phase,
                String client,
                String componentDescription,
                String purchaseOrder,
                Set<String> sourceIds,
                List<String> notes
        ) {
            this.ot = normalizeOt(ot);
            if (year < 1900 || year > 2200) {
                throw new IllegalArgumentException("year outside supported range");
            }
            this.year = year;
            this.phase = Objects.requireNonNull(phase, "phase");
            this.client = client == null ? "" : client.trim();
            this.componentDescription = componentDescription == null
                    ? ""
                    : componentDescription.trim();
            this.purchaseOrder = purchaseOrder == null ? "" : purchaseOrder.trim();
            this.sourceIds = immutableTextSet(sourceIds, "sourceIds");
            this.notes = immutableTextList(notes, "notes");
        }

        public String ot() { return ot; }
        public int year() { return year; }
        public ReportPhase phase() { return phase; }
        public String client() { return client; }
        public String componentDescription() { return componentDescription; }
        public String purchaseOrder() { return purchaseOrder; }
        public Set<String> sourceIds() { return sourceIds; }
        public List<String> notes() { return notes; }
    }

    public static final class MaterialFamily {
        private final String materialCode;
        private final Set<String> aliases;
        private final Set<String> clients;
        private final Set<String> roleHints;
        private final List<DimensionEvidence> dimensions;
        private final List<ComponentEvidence> components;
        private final List<InterventionEvent> events;
        private final Map<String, SourceDocument> sources;

        private MaterialFamily(Builder builder) {
            this.materialCode = normalizeMaterialCode(builder.materialCode);
            this.aliases = immutableTextSet(builder.aliases, "aliases");
            this.clients = immutableTextSet(builder.clients, "clients");
            this.roleHints = immutableTextSet(builder.roleHints, "roleHints");
            this.dimensions = Collections.unmodifiableList(new ArrayList<>(builder.dimensions));
            this.components = Collections.unmodifiableList(new ArrayList<>(builder.components));
            List<InterventionEvent> eventCopy = new ArrayList<>(builder.events);
            eventCopy.sort(
                    Comparator.comparingInt(InterventionEvent::year)
                            .thenComparing(InterventionEvent::ot)
                            .thenComparing(event -> event.phase().name())
            );
            this.events = Collections.unmodifiableList(eventCopy);
            this.sources = Collections.unmodifiableMap(new LinkedHashMap<>(builder.sources));
            validateSourceReferences();
        }

        private void validateSourceReferences() {
            for (DimensionEvidence dimension : dimensions) {
                if (!sources.containsKey(dimension.sourceId())) {
                    throw new IllegalArgumentException(
                            "Dimension references unknown source: " + dimension.sourceId()
                    );
                }
            }
            for (ComponentEvidence component : components) {
                if (!sources.containsKey(component.sourceId())) {
                    throw new IllegalArgumentException(
                            "Component references unknown source: " + component.sourceId()
                    );
                }
            }
            for (InterventionEvent event : events) {
                for (String sourceId : event.sourceIds()) {
                    if (!sources.containsKey(sourceId)) {
                        throw new IllegalArgumentException(
                                "Event references unknown source: " + sourceId
                        );
                    }
                }
            }
        }

        public String materialCode() { return materialCode; }
        public Set<String> aliases() { return aliases; }
        public Set<String> clients() { return clients; }
        public Set<String> roleHints() { return roleHints; }
        public List<DimensionEvidence> dimensions() { return dimensions; }
        public List<ComponentEvidence> components() { return components; }
        public List<InterventionEvent> events() { return events; }
        public Map<String, SourceDocument> sources() { return sources; }

        public List<DimensionEvidence> dimensions(DimensionKind kind) {
            List<DimensionEvidence> filtered = new ArrayList<>();
            for (DimensionEvidence evidence : dimensions) {
                if (evidence.kind() == kind) filtered.add(evidence);
            }
            return Collections.unmodifiableList(filtered);
        }

        public Optional<Double> consensusDimensionMm(DimensionKind kind) {
            List<DimensionEvidence> filtered = new ArrayList<>(dimensions(kind));
            if (filtered.isEmpty()) return Optional.empty();
            filtered.sort(Comparator.comparingDouble(DimensionEvidence::valueMm));
            double totalWeight = 0.0;
            for (DimensionEvidence evidence : filtered) {
                totalWeight += Math.max(0.01, evidence.confidence());
            }
            double accumulated = 0.0;
            for (DimensionEvidence evidence : filtered) {
                accumulated += Math.max(0.01, evidence.confidence());
                if (accumulated >= totalWeight * 0.5) {
                    return Optional.of(evidence.valueMm());
                }
            }
            return Optional.of(filtered.get(filtered.size() - 1).valueMm());
        }

        public Set<String> ots() {
            Set<String> result = new LinkedHashSet<>();
            for (InterventionEvent event : events) result.add(event.ot());
            return Collections.unmodifiableSet(result);
        }

        public List<ComponentEvidence> latestComponents(ComponentKind kind) {
            List<ComponentEvidence> filtered = new ArrayList<>();
            for (ComponentEvidence component : components) {
                if (component.kind() == kind) filtered.add(component);
            }
            return Collections.unmodifiableList(filtered);
        }

        public static Builder builder(String materialCode) {
            return new Builder(materialCode);
        }

        public static final class Builder {
            private final String materialCode;
            private final Set<String> aliases = new LinkedHashSet<>();
            private final Set<String> clients = new LinkedHashSet<>();
            private final Set<String> roleHints = new LinkedHashSet<>();
            private final List<DimensionEvidence> dimensions = new ArrayList<>();
            private final List<ComponentEvidence> components = new ArrayList<>();
            private final List<InterventionEvent> events = new ArrayList<>();
            private final Map<String, SourceDocument> sources = new LinkedHashMap<>();

            private Builder(String materialCode) {
                this.materialCode = normalizeMaterialCode(materialCode);
            }

            public Builder alias(String alias) {
                if (alias != null && !alias.trim().isEmpty()) aliases.add(alias.trim());
                return this;
            }

            public Builder client(String client) {
                if (client != null && !client.trim().isEmpty()) clients.add(client.trim());
                return this;
            }

            public Builder roleHint(String roleHint) {
                if (roleHint != null && !roleHint.trim().isEmpty()) roleHints.add(roleHint.trim());
                return this;
            }

            public Builder source(SourceDocument source) {
                Objects.requireNonNull(source, "source");
                if (source.materialCode().isPresent()
                        && !materialCode.equals(source.materialCode().get())) {
                    throw new IllegalArgumentException(
                            "Source material code does not match family " + materialCode
                    );
                }
                SourceDocument previous = sources.put(source.id(), source);
                if (previous != null) {
                    throw new IllegalArgumentException("Duplicate source id: " + source.id());
                }
                return this;
            }

            public Builder dimension(DimensionEvidence evidence) {
                dimensions.add(Objects.requireNonNull(evidence, "evidence"));
                return this;
            }

            public Builder component(ComponentEvidence evidence) {
                components.add(Objects.requireNonNull(evidence, "evidence"));
                return this;
            }

            public Builder event(InterventionEvent event) {
                events.add(Objects.requireNonNull(event, "event"));
                return this;
            }

            public MaterialFamily build() {
                if (sources.isEmpty()) {
                    throw new IllegalArgumentException("A material family requires at least one source");
                }
                return new MaterialFamily(this);
            }
        }
    }

    private final Map<String, MaterialFamily> familiesByCode = new LinkedHashMap<>();
    private final Map<String, Set<String>> materialCodesByOt = new LinkedHashMap<>();

    public synchronized void register(MaterialFamily family) {
        Objects.requireNonNull(family, "family");
        if (familiesByCode.containsKey(family.materialCode())) {
            throw new IllegalArgumentException(
                    "Material code already registered: " + family.materialCode()
            );
        }
        familiesByCode.put(family.materialCode(), family);
        for (String ot : family.ots()) {
            materialCodesByOt
                    .computeIfAbsent(ot, ignored -> new LinkedHashSet<>())
                    .add(family.materialCode());
        }
    }

    public synchronized Optional<MaterialFamily> findByMaterialCode(String materialCode) {
        if (materialCode == null || materialCode.trim().isEmpty()) return Optional.empty();
        return Optional.ofNullable(familiesByCode.get(normalizeMaterialCode(materialCode)));
    }

    public synchronized List<MaterialFamily> findByOt(String ot) {
        if (ot == null || ot.trim().isEmpty()) return Collections.emptyList();
        Set<String> codes = materialCodesByOt.get(normalizeOt(ot));
        if (codes == null || codes.isEmpty()) return Collections.emptyList();
        List<MaterialFamily> result = new ArrayList<>();
        for (String code : codes) {
            MaterialFamily family = familiesByCode.get(code);
            if (family != null) result.add(family);
        }
        result.sort(Comparator.comparing(MaterialFamily::materialCode));
        return Collections.unmodifiableList(result);
    }

    public synchronized Set<String> materialCodesForOt(String ot) {
        if (ot == null || ot.trim().isEmpty()) return Collections.emptySet();
        Set<String> codes = materialCodesByOt.get(normalizeOt(ot));
        return codes == null
                ? Collections.emptySet()
                : Collections.unmodifiableSet(new LinkedHashSet<>(codes));
    }

    public synchronized List<MaterialFamily> families() {
        List<MaterialFamily> result = new ArrayList<>(familiesByCode.values());
        result.sort(Comparator.comparing(MaterialFamily::materialCode));
        return Collections.unmodifiableList(result);
    }

    public synchronized int size() {
        return familiesByCode.size();
    }

    public static String normalizeMaterialCode(String raw) {
        String normalized = requireText(raw, "materialCode")
                .toUpperCase(Locale.ROOT)
                .replaceAll("[^A-Z0-9]", "");
        if (normalized.isEmpty()) {
            throw new IllegalArgumentException("materialCode contains no alphanumeric characters");
        }
        return normalized;
    }

    public static String normalizeOt(String raw) {
        String value = requireText(raw, "ot");
        Matcher matcher = OT_DIGITS.matcher(value);
        String digits = null;
        while (matcher.find()) digits = matcher.group(1);
        if (digits == null) {
            throw new IllegalArgumentException("Unable to extract OT number from: " + raw);
        }
        digits = digits.replaceFirst("^0+(?!$)", "");
        return "OT-" + digits;
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

    private static double requireConfidence(double value, String name) {
        if (!Double.isFinite(value) || value < 0.0 || value > 1.0) {
            throw new IllegalArgumentException(name + " must be between 0 and 1");
        }
        return value;
    }

    private static Set<String> immutableTextSet(Set<String> values, String name) {
        Objects.requireNonNull(values, name);
        Set<String> result = new LinkedHashSet<>();
        for (String value : values) {
            if (value != null && !value.trim().isEmpty()) result.add(value.trim());
        }
        return Collections.unmodifiableSet(result);
    }

    private static List<String> immutableTextList(List<String> values, String name) {
        Objects.requireNonNull(values, name);
        List<String> result = new ArrayList<>();
        for (String value : values) {
            if (value != null && !value.trim().isEmpty()) result.add(value.trim());
        }
        return Collections.unmodifiableList(result);
    }
}

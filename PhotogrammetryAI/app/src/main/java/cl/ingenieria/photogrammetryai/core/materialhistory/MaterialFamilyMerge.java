package cl.ingenieria.photogrammetryai.core.materialhistory;

import cl.ingenieria.photogrammetryai.core.materialhistory.MaterialPulleyKnowledgeBase.ComponentEvidence;
import cl.ingenieria.photogrammetryai.core.materialhistory.MaterialPulleyKnowledgeBase.DimensionEvidence;
import cl.ingenieria.photogrammetryai.core.materialhistory.MaterialPulleyKnowledgeBase.InterventionEvent;
import cl.ingenieria.photogrammetryai.core.materialhistory.MaterialPulleyKnowledgeBase.MaterialFamily;
import cl.ingenieria.photogrammetryai.core.materialhistory.MaterialPulleyKnowledgeBase.SourceDocument;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/** Deterministic union used by SQLite imports, bundled seeds and test stores. */
public final class MaterialFamilyMerge {
    private MaterialFamilyMerge() {}

    public static MaterialFamily merge(MaterialFamily first, MaterialFamily second) {
        Objects.requireNonNull(first, "first");
        Objects.requireNonNull(second, "second");
        if (!first.materialCode().equals(second.materialCode())) {
            throw new IllegalArgumentException("Cannot merge different material codes");
        }

        MaterialFamily.Builder builder = MaterialFamily.builder(first.materialCode());
        for (String alias : union(first.aliases(), second.aliases())) builder.alias(alias);
        for (String client : union(first.clients(), second.clients())) builder.client(client);
        for (String role : union(first.roleHints(), second.roleHints())) builder.roleHint(role);

        Map<String, SourceDocument> sources = new LinkedHashMap<>();
        addSources(sources, first);
        addSources(sources, second);
        for (SourceDocument source : sources.values()) builder.source(source);

        Map<String, DimensionEvidence> dimensions = new LinkedHashMap<>();
        addDimensions(dimensions, first.dimensions());
        addDimensions(dimensions, second.dimensions());
        for (DimensionEvidence evidence : dimensions.values()) builder.dimension(evidence);

        Map<String, ComponentEvidence> components = new LinkedHashMap<>();
        addComponents(components, first.components());
        addComponents(components, second.components());
        for (ComponentEvidence evidence : components.values()) builder.component(evidence);

        Map<String, MutableEvent> events = new LinkedHashMap<>();
        addEvents(events, first.events());
        addEvents(events, second.events());
        for (MutableEvent event : events.values()) builder.event(event.toImmutable());

        return builder.build();
    }

    private static void addSources(Map<String, SourceDocument> target, MaterialFamily family) {
        for (SourceDocument source : family.sources().values()) {
            SourceDocument previous = target.get(source.id());
            if (previous == null) {
                target.put(source.id(), source);
            } else if (!sourceSignature(previous).equals(sourceSignature(source))) {
                throw new IllegalArgumentException(
                        "Conflicting source id " + source.id() + " in family " + family.materialCode()
                );
            }
        }
    }

    private static void addDimensions(
            Map<String, DimensionEvidence> target,
            List<DimensionEvidence> values
    ) {
        for (DimensionEvidence value : values) {
            String key = value.kind().name()
                    + '|' + rounded(value.valueMm())
                    + '|' + normalized(value.semanticLabel())
                    + '|' + value.sourceId();
            DimensionEvidence previous = target.get(key);
            if (previous == null || value.confidence() > previous.confidence()) target.put(key, value);
        }
    }

    private static void addComponents(
            Map<String, ComponentEvidence> target,
            List<ComponentEvidence> values
    ) {
        for (ComponentEvidence value : values) {
            String key = value.kind().name()
                    + '|' + normalized(value.manufacturer())
                    + '|' + normalized(value.model())
                    + '|' + value.condition().name()
                    + '|' + normalized(value.note())
                    + '|' + value.sourceId();
            ComponentEvidence previous = target.get(key);
            if (previous == null || value.confidence() > previous.confidence()) target.put(key, value);
        }
    }

    private static void addEvents(Map<String, MutableEvent> target, List<InterventionEvent> values) {
        for (InterventionEvent value : values) {
            String key = value.ot()
                    + '|' + value.year()
                    + '|' + value.phase().name()
                    + '|' + normalized(value.client())
                    + '|' + normalized(value.componentDescription())
                    + '|' + normalized(value.purchaseOrder());
            MutableEvent merged = target.get(key);
            if (merged == null) {
                merged = new MutableEvent(value);
                target.put(key, merged);
            } else {
                merged.sourceIds.addAll(value.sourceIds());
                merged.notes.addAll(value.notes());
            }
        }
    }

    /** URI is intentionally excluded: identical reports copied to another Drive folder remain one source. */
    private static String sourceSignature(SourceDocument source) {
        return normalized(source.title())
                + '|' + source.phase().name()
                + '|' + source.ot().orElse("")
                + '|' + source.materialCode().orElse("")
                + '|' + normalized(source.documentDate());
    }

    private static Set<String> union(Set<String> first, Set<String> second) {
        Set<String> result = new LinkedHashSet<>(first);
        result.addAll(second);
        return result;
    }

    private static String rounded(double value) {
        return String.format(Locale.ROOT, "%.6f", value);
    }

    private static String normalized(String value) {
        return value == null ? "" : value.trim().replaceAll("\\s+", " ").toUpperCase(Locale.ROOT);
    }

    private static final class MutableEvent {
        private final String ot;
        private final int year;
        private final MaterialPulleyKnowledgeBase.ReportPhase phase;
        private final String client;
        private final String componentDescription;
        private final String purchaseOrder;
        private final Set<String> sourceIds = new LinkedHashSet<>();
        private final Set<String> notes = new LinkedHashSet<>();

        private MutableEvent(InterventionEvent source) {
            this.ot = source.ot();
            this.year = source.year();
            this.phase = source.phase();
            this.client = source.client();
            this.componentDescription = source.componentDescription();
            this.purchaseOrder = source.purchaseOrder();
            this.sourceIds.addAll(source.sourceIds());
            this.notes.addAll(source.notes());
        }

        private InterventionEvent toImmutable() {
            return new InterventionEvent(
                    ot,
                    year,
                    phase,
                    client,
                    componentDescription,
                    purchaseOrder,
                    sourceIds,
                    new ArrayList<>(notes)
            );
        }
    }
}

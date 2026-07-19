package cl.ingenieria.photogrammetryai.core.pulleyknowledge;

import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

/**
 * Versioned and dependency-free knowledge base for conveyor pulleys.
 *
 * <p>The built-in entries are intentionally priors rather than absolute rules. Field-verified
 * reference cases can be added later and are kept separate from manufacturer/general knowledge.</p>
 */
public final class PulleyKnowledgeBase {
    public static final String VERSION = "PULLEY_KB_V1";

    public enum Role {
        DRIVE,
        HEAD,
        TAIL,
        TAKE_UP,
        SNUB,
        BEND,
        GEARLESS_DRIVE,
        UNKNOWN
    }

    public enum ShaftArrangement {
        LIVE_SHAFT,
        DEAD_SHAFT,
        UNKNOWN
    }

    public enum LaggingType {
        NONE,
        RUBBER_SMOOTH,
        RUBBER_DIAMOND,
        RUBBER_PROFILED,
        CERAMIC_DIMPLED,
        CERAMIC_SMOOTH,
        DIRECT_BOND_CERAMIC,
        POLYURETHANE,
        UNKNOWN
    }

    public enum Component {
        SHELL,
        END_DISC,
        SHAFT,
        HUB,
        LOCKING_ELEMENT,
        BEARING,
        BEARING_HOUSING,
        SEAL,
        LAGGING,
        DRIVE_CONNECTION
    }

    public enum FactKind {
        DEFINITION,
        COMPONENT_RELATION,
        CLASSIFICATION_PRIOR,
        TRACKING_PRIOR,
        MAINTENANCE_PRIOR,
        ENVIRONMENT_PRIOR
    }

    public enum EvidenceOrigin {
        MANUFACTURER,
        USER_CONFIRMED,
        INSPECTION_INFERRED
    }

    public static final class SourceRecord {
        private final String id;
        private final String organisation;
        private final String title;
        private final String uri;

        public SourceRecord(String id, String organisation, String title, String uri) {
            this.id = requireText(id, "id");
            this.organisation = requireText(organisation, "organisation");
            this.title = requireText(title, "title");
            this.uri = requireText(uri, "uri");
        }

        public String id() { return id; }
        public String organisation() { return organisation; }
        public String title() { return title; }
        public String uri() { return uri; }
    }

    public static final class Fact {
        private final String id;
        private final FactKind kind;
        private final String statement;
        private final double confidence;
        private final Set<String> sourceIds;
        private final Set<String> tags;

        public Fact(
                String id,
                FactKind kind,
                String statement,
                double confidence,
                Set<String> sourceIds,
                Set<String> tags
        ) {
            this.id = requireText(id, "id");
            this.kind = Objects.requireNonNull(kind, "kind");
            this.statement = requireText(statement, "statement");
            this.confidence = requireConfidence(confidence);
            this.sourceIds = immutableTextSet(sourceIds, "sourceIds");
            this.tags = immutableTextSet(tags, "tags");
        }

        public String id() { return id; }
        public FactKind kind() { return kind; }
        public String statement() { return statement; }
        public double confidence() { return confidence; }
        public Set<String> sourceIds() { return sourceIds; }
        public Set<String> tags() { return tags; }
    }

    /** Generic family prior. Numeric dimensions are deliberately absent until field evidence exists. */
    public static final class PulleyTemplate {
        private final String id;
        private final String displayName;
        private final Set<Role> likelyRoles;
        private final ShaftArrangement shaftArrangement;
        private final Set<LaggingType> compatibleLagging;
        private final Set<Component> expectedComponents;
        private final Set<String> positiveCues;
        private final Set<String> negativeCues;
        private final Set<String> sourceIds;
        private final double priorWeight;

        public PulleyTemplate(
                String id,
                String displayName,
                Set<Role> likelyRoles,
                ShaftArrangement shaftArrangement,
                Set<LaggingType> compatibleLagging,
                Set<Component> expectedComponents,
                Set<String> positiveCues,
                Set<String> negativeCues,
                Set<String> sourceIds,
                double priorWeight
        ) {
            this.id = requireText(id, "id");
            this.displayName = requireText(displayName, "displayName");
            this.likelyRoles = immutableEnumSet(likelyRoles, Role.class, "likelyRoles");
            this.shaftArrangement = Objects.requireNonNull(shaftArrangement, "shaftArrangement");
            this.compatibleLagging = immutableEnumSet(
                    compatibleLagging,
                    LaggingType.class,
                    "compatibleLagging"
            );
            this.expectedComponents = immutableEnumSet(
                    expectedComponents,
                    Component.class,
                    "expectedComponents"
            );
            this.positiveCues = immutableTextSet(positiveCues, "positiveCues");
            this.negativeCues = immutableTextSet(negativeCues, "negativeCues");
            this.sourceIds = immutableTextSet(sourceIds, "sourceIds");
            this.priorWeight = requireConfidence(priorWeight);
        }

        public String id() { return id; }
        public String displayName() { return displayName; }
        public Set<Role> likelyRoles() { return likelyRoles; }
        public ShaftArrangement shaftArrangement() { return shaftArrangement; }
        public Set<LaggingType> compatibleLagging() { return compatibleLagging; }
        public Set<Component> expectedComponents() { return expectedComponents; }
        public Set<String> positiveCues() { return positiveCues; }
        public Set<String> negativeCues() { return negativeCues; }
        public Set<String> sourceIds() { return sourceIds; }
        public double priorWeight() { return priorWeight; }
    }

    /** Field-confirmed pulley used for dimensional retrieval and future supervised adaptation. */
    public static final class ReferenceCase {
        private final String id;
        private final String displayName;
        private final EvidenceOrigin origin;
        private final Role role;
        private final ShaftArrangement shaftArrangement;
        private final LaggingType laggingType;
        private final Map<String, Double> dimensionsMm;
        private final Set<Component> confirmedComponents;
        private final Set<String> tags;
        private final String stepReferenceId;
        private final double verificationConfidence;

        public ReferenceCase(
                String id,
                String displayName,
                EvidenceOrigin origin,
                Role role,
                ShaftArrangement shaftArrangement,
                LaggingType laggingType,
                Map<String, Double> dimensionsMm,
                Set<Component> confirmedComponents,
                Set<String> tags,
                String stepReferenceId,
                double verificationConfidence
        ) {
            this.id = requireText(id, "id");
            this.displayName = requireText(displayName, "displayName");
            this.origin = Objects.requireNonNull(origin, "origin");
            this.role = Objects.requireNonNull(role, "role");
            this.shaftArrangement = Objects.requireNonNull(
                    shaftArrangement,
                    "shaftArrangement"
            );
            this.laggingType = Objects.requireNonNull(laggingType, "laggingType");
            this.dimensionsMm = immutableDimensions(dimensionsMm);
            this.confirmedComponents = immutableEnumSet(
                    confirmedComponents,
                    Component.class,
                    "confirmedComponents"
            );
            this.tags = immutableTextSet(tags, "tags");
            this.stepReferenceId = stepReferenceId == null || stepReferenceId.trim().isEmpty()
                    ? null
                    : stepReferenceId.trim();
            this.verificationConfidence = requireConfidence(verificationConfidence);
        }

        public String id() { return id; }
        public String displayName() { return displayName; }
        public EvidenceOrigin origin() { return origin; }
        public Role role() { return role; }
        public ShaftArrangement shaftArrangement() { return shaftArrangement; }
        public LaggingType laggingType() { return laggingType; }
        public Map<String, Double> dimensionsMm() { return dimensionsMm; }
        public Set<Component> confirmedComponents() { return confirmedComponents; }
        public Set<String> tags() { return tags; }
        public Optional<String> stepReferenceId() { return Optional.ofNullable(stepReferenceId); }
        public double verificationConfidence() { return verificationConfidence; }
    }

    public static final String DIM_SHELL_DIAMETER = "shellDiameter";
    public static final String DIM_FACE_WIDTH = "faceWidth";
    public static final String DIM_SHAFT_DIAMETER = "shaftDiameter";
    public static final String DIM_BEARING_CENTER_DISTANCE = "bearingCenterDistance";
    public static final String DIM_OVERALL_SHAFT_LENGTH = "overallShaftLength";
    public static final String DIM_LAGGING_THICKNESS = "laggingThickness";
    public static final String DIM_HUB_DIAMETER = "hubDiameter";
    public static final String DIM_HUB_LENGTH = "hubLength";

    private final Map<String, SourceRecord> sources;
    private final Map<String, Fact> facts;
    private final Map<String, PulleyTemplate> templates;
    private final Map<String, ReferenceCase> referenceCases;

    private PulleyKnowledgeBase(
            Map<String, SourceRecord> sources,
            Map<String, Fact> facts,
            Map<String, PulleyTemplate> templates,
            Map<String, ReferenceCase> referenceCases
    ) {
        this.sources = Collections.unmodifiableMap(new LinkedHashMap<>(sources));
        this.facts = Collections.unmodifiableMap(new LinkedHashMap<>(facts));
        this.templates = Collections.unmodifiableMap(new LinkedHashMap<>(templates));
        this.referenceCases = Collections.unmodifiableMap(new LinkedHashMap<>(referenceCases));
    }

    public static PulleyKnowledgeBase defaultIndustrial() {
        Builder builder = new Builder();
        addOfficialSources(builder);
        addCoreFacts(builder);
        addGenericTemplates(builder);
        return builder.build();
    }

    public String version() { return VERSION; }
    public Map<String, SourceRecord> sources() { return sources; }
    public Map<String, Fact> facts() { return facts; }
    public Map<String, PulleyTemplate> templates() { return templates; }
    public Map<String, ReferenceCase> referenceCases() { return referenceCases; }

    public Optional<PulleyTemplate> template(String id) {
        return Optional.ofNullable(templates.get(id));
    }

    public Optional<ReferenceCase> referenceCase(String id) {
        return Optional.ofNullable(referenceCases.get(id));
    }

    public PulleyKnowledgeBase withReferenceCase(ReferenceCase referenceCase) {
        Objects.requireNonNull(referenceCase, "referenceCase");
        Map<String, ReferenceCase> updated = new LinkedHashMap<>(referenceCases);
        updated.put(referenceCase.id(), referenceCase);
        return new PulleyKnowledgeBase(sources, facts, templates, updated);
    }

    public List<Fact> factsTagged(String tag) {
        String normalized = requireText(tag, "tag").toLowerCase();
        List<Fact> matches = new ArrayList<>();
        for (Fact fact : facts.values()) {
            for (String factTag : fact.tags()) {
                if (factTag.equalsIgnoreCase(normalized)) {
                    matches.add(fact);
                    break;
                }
            }
        }
        return Collections.unmodifiableList(matches);
    }

    private static void addOfficialSources(Builder builder) {
        builder.addSource(new SourceRecord(
                "PROK_TYPES_2022",
                "PROK",
                "Common types of pulleys found in a conveyor belt system",
                "https://prok.com/common-types-of-pulleys-found-in-a-conveyor-belt-system/"
        ));
        builder.addSource(new SourceRecord(
                "PROK_ENGINEERED_PULLEYS",
                "PROK",
                "Engineered pulleys and lagging options",
                "https://prok.com/pulleys/"
        ));
        builder.addSource(new SourceRecord(
                "PROK_GEARLESS_END_DISC",
                "PROK",
                "Gearless drive pulley: end disc design and fabrication",
                "https://prok.com/global-product/gearless-drive-pulley/"
        ));
        builder.addSource(new SourceRecord(
                "CONTINENTAL_LAGGING",
                "Continental",
                "Pulley lagging",
                "https://www.continental-industry.com/global/en/products-solutions/conveying-solutions/conveyor-components/pulley-lagging"
        ));
        builder.addSource(new SourceRecord(
                "SKF_THREE_BARRIER",
                "SKF",
                "Three-barrier solution for conveyor pulley bearings",
                "https://ro.promo.skf.com/acton/fs/blocks/showLandingPage/a/22868/p/p-00a9/t/page/fm/5"
        ));
        builder.addSource(new SourceRecord(
                "SKF_TACONITE",
                "SKF",
                "Sealing solutions for challenging environments",
                "https://evolution.skf.com/sealing-solutions-for-challenging-environments/"
        ));
    }

    private static void addCoreFacts(Builder builder) {
        builder.addFact(fact(
                "F_COMPONENTS",
                FactKind.COMPONENT_RELATION,
                "A conventional conveyor pulley is represented by shell, end discs, shaft, locking element and bearing assembly; hubs, housings, seals, lagging and drive connection are optional or arrangement-dependent.",
                0.96,
                set("PROK_TYPES_2022"),
                set("components", "topology")
        ));
        builder.addFact(fact(
                "F_LIVE_SHAFT",
                FactKind.CLASSIFICATION_PRIOR,
                "In a live-shaft pulley the shaft and pulley body rotate together and the bearing assemblies are external to the rotating body.",
                0.95,
                set("PROK_TYPES_2022"),
                set("live-shaft", "bearing", "classification")
        ));
        builder.addFact(fact(
                "F_DEAD_SHAFT",
                FactKind.CLASSIFICATION_PRIOR,
                "In a dead-shaft pulley the shaft remains fixed while bearings are arranged within the rotating pulley body.",
                0.93,
                set("PROK_TYPES_2022"),
                set("dead-shaft", "bearing", "classification")
        ));
        builder.addFact(fact(
                "F_END_DISC_LOAD_PATH",
                FactKind.COMPONENT_RELATION,
                "The end disc transfers load between shaft and shell and its flexibility is part of controlling shell and shaft stress.",
                0.95,
                set("PROK_GEARLESS_END_DISC"),
                set("end-disc", "load-path", "constraint")
        ));
        builder.addFact(fact(
                "F_LAGGING_PURPOSE",
                FactKind.TRACKING_PRIOR,
                "Lagging is evidence of friction, slip-control or wear-protection requirements; it is a strong but non-exclusive cue for a drive pulley.",
                0.90,
                set("CONTINENTAL_LAGGING", "PROK_ENGINEERED_PULLEYS"),
                set("lagging", "drive", "classification")
        ));
        builder.addFact(fact(
                "F_CERAMIC_PATTERN",
                FactKind.CLASSIFICATION_PRIOR,
                "Dimpled ceramic lagging is associated with drive duty while smooth ceramic lagging can be used on non-driven applications; treat the pattern as probabilistic evidence.",
                0.88,
                set("PROK_ENGINEERED_PULLEYS"),
                set("ceramic", "lagging", "drive", "non-drive")
        ));
        builder.addFact(fact(
                "F_BEARING_CONTAMINATION",
                FactKind.ENVIRONMENT_PRIOR,
                "Taconite-style sealing, grease barriers and sealed spherical roller bearings indicate a highly contaminated or wet conveyor environment, not a pulley role by themselves.",
                0.94,
                set("SKF_THREE_BARRIER", "SKF_TACONITE"),
                set("bearing", "housing", "seal", "environment")
        ));
        builder.addFact(fact(
                "F_COAXIAL_CHAIN",
                FactKind.TRACKING_PRIOR,
                "Shaft, locking element or hub, end-disc centres, shell and lagging should be treated as a coaxial assembly unless inspection evidence proves an offset or special design.",
                0.90,
                set("PROK_TYPES_2022", "PROK_GEARLESS_END_DISC"),
                set("coaxial", "overlay", "constraint")
        ));
        builder.addFact(fact(
                "F_SUPPORTS_NOT_ROLE",
                FactKind.TRACKING_PRIOR,
                "Bearing housing family and seal type help recover shaft centre and environment, but should not alone decide whether the pulley is drive, tail, bend, snub or take-up.",
                0.91,
                set("SKF_THREE_BARRIER", "SKF_TACONITE"),
                set("support", "classification", "anti-bias")
        ));
    }

    private static void addGenericTemplates(Builder builder) {
        Set<Component> liveComponents = EnumSet.of(
                Component.SHELL,
                Component.END_DISC,
                Component.SHAFT,
                Component.LOCKING_ELEMENT,
                Component.BEARING,
                Component.BEARING_HOUSING
        );
        builder.addTemplate(template(
                "LIVE_DRIVE_LAGGED",
                "Live-shaft drive pulley with lagging",
                EnumSet.of(Role.DRIVE, Role.HEAD),
                ShaftArrangement.LIVE_SHAFT,
                EnumSet.of(
                        LaggingType.RUBBER_DIAMOND,
                        LaggingType.RUBBER_PROFILED,
                        LaggingType.CERAMIC_DIMPLED,
                        LaggingType.DIRECT_BOND_CERAMIC
                ),
                plus(liveComponents, Component.LAGGING, Component.DRIVE_CONNECTION),
                set("visible lagging", "drive connection", "external bearing housings"),
                set("fixed shaft", "internal bearings"),
                set("PROK_TYPES_2022", "PROK_ENGINEERED_PULLEYS", "CONTINENTAL_LAGGING"),
                0.82
        ));
        builder.addTemplate(template(
                "LIVE_NON_DRIVE",
                "Live-shaft non-driven pulley",
                EnumSet.of(Role.TAIL, Role.BEND, Role.SNUB, Role.TAKE_UP),
                ShaftArrangement.LIVE_SHAFT,
                EnumSet.of(
                        LaggingType.NONE,
                        LaggingType.RUBBER_SMOOTH,
                        LaggingType.CERAMIC_SMOOTH,
                        LaggingType.POLYURETHANE
                ),
                liveComponents,
                set("external bearing housings", "no drive connection"),
                set("motor or gearbox connection"),
                set("PROK_TYPES_2022", "PROK_ENGINEERED_PULLEYS"),
                0.74
        ));
        builder.addTemplate(template(
                "DEAD_SHAFT_INTERNAL_BEARING",
                "Dead-shaft pulley with internal bearings",
                EnumSet.of(Role.UNKNOWN, Role.TAIL, Role.BEND),
                ShaftArrangement.DEAD_SHAFT,
                EnumSet.of(LaggingType.NONE, LaggingType.UNKNOWN),
                EnumSet.of(Component.SHELL, Component.END_DISC, Component.SHAFT, Component.BEARING),
                set("stationary shaft", "internal bearing location"),
                set("external split housings rotating with shaft"),
                set("PROK_TYPES_2022"),
                0.55
        ));
        builder.addTemplate(template(
                "GEARLESS_DRIVE",
                "Gearless drive pulley",
                EnumSet.of(Role.GEARLESS_DRIVE, Role.DRIVE),
                ShaftArrangement.LIVE_SHAFT,
                EnumSet.of(
                        LaggingType.RUBBER_PROFILED,
                        LaggingType.CERAMIC_DIMPLED,
                        LaggingType.DIRECT_BOND_CERAMIC
                ),
                plus(liveComponents, Component.LAGGING, Component.DRIVE_CONNECTION),
                set("integrated direct drive", "high-duty end-disc load path"),
                set("conventional gearbox output coupling only"),
                set("PROK_GEARLESS_END_DISC", "PROK_ENGINEERED_PULLEYS"),
                0.42
        ));
    }

    public static final class Builder {
        private final Map<String, SourceRecord> sources = new LinkedHashMap<>();
        private final Map<String, Fact> facts = new LinkedHashMap<>();
        private final Map<String, PulleyTemplate> templates = new LinkedHashMap<>();
        private final Map<String, ReferenceCase> referenceCases = new LinkedHashMap<>();

        public Builder addSource(SourceRecord source) {
            Objects.requireNonNull(source, "source");
            sources.put(source.id(), source);
            return this;
        }

        public Builder addFact(Fact fact) {
            Objects.requireNonNull(fact, "fact");
            facts.put(fact.id(), fact);
            return this;
        }

        public Builder addTemplate(PulleyTemplate template) {
            Objects.requireNonNull(template, "template");
            templates.put(template.id(), template);
            return this;
        }

        public Builder addReferenceCase(ReferenceCase referenceCase) {
            Objects.requireNonNull(referenceCase, "referenceCase");
            referenceCases.put(referenceCase.id(), referenceCase);
            return this;
        }

        public PulleyKnowledgeBase build() {
            for (Fact fact : facts.values()) {
                validateSources(fact.sourceIds(), "fact " + fact.id());
            }
            for (PulleyTemplate template : templates.values()) {
                validateSources(template.sourceIds(), "template " + template.id());
            }
            return new PulleyKnowledgeBase(sources, facts, templates, referenceCases);
        }

        private void validateSources(Set<String> sourceIds, String owner) {
            for (String sourceId : sourceIds) {
                if (!sources.containsKey(sourceId)) {
                    throw new IllegalStateException(owner + " references unknown source " + sourceId);
                }
            }
        }
    }

    private static Fact fact(
            String id,
            FactKind kind,
            String statement,
            double confidence,
            Set<String> sourceIds,
            Set<String> tags
    ) {
        return new Fact(id, kind, statement, confidence, sourceIds, tags);
    }

    private static PulleyTemplate template(
            String id,
            String displayName,
            Set<Role> likelyRoles,
            ShaftArrangement shaftArrangement,
            Set<LaggingType> compatibleLagging,
            Set<Component> expectedComponents,
            Set<String> positiveCues,
            Set<String> negativeCues,
            Set<String> sourceIds,
            double priorWeight
    ) {
        return new PulleyTemplate(
                id,
                displayName,
                likelyRoles,
                shaftArrangement,
                compatibleLagging,
                expectedComponents,
                positiveCues,
                negativeCues,
                sourceIds,
                priorWeight
        );
    }

    private static Set<Component> plus(Set<Component> original, Component... additions) {
        EnumSet<Component> result = original.isEmpty()
                ? EnumSet.noneOf(Component.class)
                : EnumSet.copyOf(original);
        Collections.addAll(result, additions);
        return result;
    }

    private static Set<String> set(String... values) {
        LinkedHashSet<String> result = new LinkedHashSet<>();
        Collections.addAll(result, values);
        return result;
    }

    private static String requireText(String value, String name) {
        if (value == null || value.trim().isEmpty()) {
            throw new IllegalArgumentException(name + " must not be blank");
        }
        return value.trim();
    }

    private static double requireConfidence(double value) {
        if (!Double.isFinite(value) || value < 0.0 || value > 1.0) {
            throw new IllegalArgumentException("confidence must be between 0 and 1");
        }
        return value;
    }

    private static Set<String> immutableTextSet(Set<String> values, String name) {
        Objects.requireNonNull(values, name);
        LinkedHashSet<String> copy = new LinkedHashSet<>();
        for (String value : values) {
            copy.add(requireText(value, name));
        }
        return Collections.unmodifiableSet(copy);
    }

    private static <E extends Enum<E>> Set<E> immutableEnumSet(
            Set<E> values,
            Class<E> enumType,
            String name
    ) {
        Objects.requireNonNull(values, name);
        EnumSet<E> copy = values.isEmpty()
                ? EnumSet.noneOf(enumType)
                : EnumSet.copyOf(values);
        return Collections.unmodifiableSet(copy);
    }

    private static Map<String, Double> immutableDimensions(Map<String, Double> values) {
        Objects.requireNonNull(values, "dimensionsMm");
        Map<String, Double> copy = new LinkedHashMap<>();
        for (Map.Entry<String, Double> entry : values.entrySet()) {
            String key = requireText(entry.getKey(), "dimension name");
            Double value = Objects.requireNonNull(entry.getValue(), "dimension value");
            if (!Double.isFinite(value) || value <= 0.0) {
                throw new IllegalArgumentException("dimension " + key + " must be positive");
            }
            copy.put(key, value);
        }
        return Collections.unmodifiableMap(copy);
    }
}

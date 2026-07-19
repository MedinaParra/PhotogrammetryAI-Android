package cl.ingenieria.photogrammetryai.core.pulleyknowledge;

import cl.ingenieria.photogrammetryai.core.pulleyknowledge.PulleyKnowledgeBase.Component;
import cl.ingenieria.photogrammetryai.core.pulleyknowledge.PulleyKnowledgeBase.LaggingType;
import cl.ingenieria.photogrammetryai.core.pulleyknowledge.PulleyKnowledgeBase.PulleyTemplate;
import cl.ingenieria.photogrammetryai.core.pulleyknowledge.PulleyKnowledgeBase.ReferenceCase;
import cl.ingenieria.photogrammetryai.core.pulleyknowledge.PulleyKnowledgeBase.Role;
import cl.ingenieria.photogrammetryai.core.pulleyknowledge.PulleyKnowledgeBase.ShaftArrangement;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

/** Ranks generic pulley families and verified historical cases against partial field evidence. */
public final class PulleySimilarityEngine {
    public enum CandidateKind {
        GENERIC_TEMPLATE,
        VERIFIED_REFERENCE
    }

    public static final class Config {
        private final double roleWeight;
        private final double shaftWeight;
        private final double laggingWeight;
        private final double componentWeight;
        private final double dimensionWeight;
        private final double ratioWeight;
        private final double cueWeight;
        private final double verifiedCaseBonus;
        private final double dimensionLogTolerance;
        private final double ratioLogTolerance;

        public Config(
                double roleWeight,
                double shaftWeight,
                double laggingWeight,
                double componentWeight,
                double dimensionWeight,
                double ratioWeight,
                double cueWeight,
                double verifiedCaseBonus,
                double dimensionLogTolerance,
                double ratioLogTolerance
        ) {
            this.roleWeight = requireNonNegative(roleWeight, "roleWeight");
            this.shaftWeight = requireNonNegative(shaftWeight, "shaftWeight");
            this.laggingWeight = requireNonNegative(laggingWeight, "laggingWeight");
            this.componentWeight = requireNonNegative(componentWeight, "componentWeight");
            this.dimensionWeight = requireNonNegative(dimensionWeight, "dimensionWeight");
            this.ratioWeight = requireNonNegative(ratioWeight, "ratioWeight");
            this.cueWeight = requireNonNegative(cueWeight, "cueWeight");
            this.verifiedCaseBonus = requireRange(verifiedCaseBonus, 0.0, 1.0, "verifiedCaseBonus");
            this.dimensionLogTolerance = requirePositive(
                    dimensionLogTolerance,
                    "dimensionLogTolerance"
            );
            this.ratioLogTolerance = requirePositive(ratioLogTolerance, "ratioLogTolerance");
            if (totalWeight() <= 0.0) {
                throw new IllegalArgumentException("At least one similarity weight must be positive");
            }
        }

        public static Config industrialDefaults() {
            return new Config(
                    1.20,
                    1.15,
                    0.90,
                    1.15,
                    2.00,
                    1.35,
                    0.70,
                    0.08,
                    0.18,
                    0.14
            );
        }

        private double totalWeight() {
            return roleWeight + shaftWeight + laggingWeight + componentWeight
                    + dimensionWeight + ratioWeight + cueWeight;
        }
    }

    public static final class ObservedPulleySignature {
        private final Map<Role, Double> roleConfidence;
        private final ShaftArrangement shaftArrangement;
        private final double shaftArrangementConfidence;
        private final LaggingType laggingType;
        private final double laggingConfidence;
        private final Set<Component> observedComponents;
        private final Map<String, Double> dimensionsMm;
        private final Set<String> cues;
        private final double evidenceQuality;

        public ObservedPulleySignature(
                Map<Role, Double> roleConfidence,
                ShaftArrangement shaftArrangement,
                double shaftArrangementConfidence,
                LaggingType laggingType,
                double laggingConfidence,
                Set<Component> observedComponents,
                Map<String, Double> dimensionsMm,
                Set<String> cues,
                double evidenceQuality
        ) {
            this.roleConfidence = immutableRoleConfidence(roleConfidence);
            this.shaftArrangement = Objects.requireNonNull(
                    shaftArrangement,
                    "shaftArrangement"
            );
            this.shaftArrangementConfidence = requireConfidence(
                    shaftArrangementConfidence,
                    "shaftArrangementConfidence"
            );
            this.laggingType = Objects.requireNonNull(laggingType, "laggingType");
            this.laggingConfidence = requireConfidence(laggingConfidence, "laggingConfidence");
            this.observedComponents = immutableComponents(observedComponents);
            this.dimensionsMm = immutableDimensions(dimensionsMm);
            this.cues = immutableCues(cues);
            this.evidenceQuality = requireConfidence(evidenceQuality, "evidenceQuality");
        }

        public static ObservedPulleySignature sparse(
                Map<String, Double> dimensionsMm,
                Set<Component> observedComponents
        ) {
            return new ObservedPulleySignature(
                    Collections.emptyMap(),
                    ShaftArrangement.UNKNOWN,
                    0.0,
                    LaggingType.UNKNOWN,
                    0.0,
                    observedComponents,
                    dimensionsMm,
                    Collections.emptySet(),
                    0.65
            );
        }

        public Map<Role, Double> roleConfidence() { return roleConfidence; }
        public ShaftArrangement shaftArrangement() { return shaftArrangement; }
        public double shaftArrangementConfidence() { return shaftArrangementConfidence; }
        public LaggingType laggingType() { return laggingType; }
        public double laggingConfidence() { return laggingConfidence; }
        public Set<Component> observedComponents() { return observedComponents; }
        public Map<String, Double> dimensionsMm() { return dimensionsMm; }
        public Set<String> cues() { return cues; }
        public double evidenceQuality() { return evidenceQuality; }
    }

    public static final class Match {
        private final String candidateId;
        private final String displayName;
        private final CandidateKind candidateKind;
        private final double score;
        private final double evidenceCoverage;
        private final List<String> explanations;
        private final String stepReferenceId;

        private Match(
                String candidateId,
                String displayName,
                CandidateKind candidateKind,
                double score,
                double evidenceCoverage,
                List<String> explanations,
                String stepReferenceId
        ) {
            this.candidateId = candidateId;
            this.displayName = displayName;
            this.candidateKind = candidateKind;
            this.score = score;
            this.evidenceCoverage = evidenceCoverage;
            this.explanations = Collections.unmodifiableList(new ArrayList<>(explanations));
            this.stepReferenceId = stepReferenceId;
        }

        public String candidateId() { return candidateId; }
        public String displayName() { return displayName; }
        public CandidateKind candidateKind() { return candidateKind; }
        public double score() { return score; }
        public double evidenceCoverage() { return evidenceCoverage; }
        public List<String> explanations() { return explanations; }
        public Optional<String> stepReferenceId() { return Optional.ofNullable(stepReferenceId); }
    }

    private static final class ScoreAccumulator {
        private double weightedScore;
        private double usedWeight;
        private final List<String> explanations = new ArrayList<>();

        void add(double weight, double score, String explanation) {
            if (weight <= 0.0) return;
            weightedScore += weight * clamp01(score);
            usedWeight += weight;
            if (explanation != null && !explanation.isEmpty()) {
                explanations.add(explanation);
            }
        }

        double normalizedScore() {
            return usedWeight <= 0.0 ? 0.0 : clamp01(weightedScore / usedWeight);
        }
    }

    private final Config config;

    public PulleySimilarityEngine(Config config) {
        this.config = Objects.requireNonNull(config, "config");
    }

    public List<Match> rank(
            PulleyKnowledgeBase knowledgeBase,
            ObservedPulleySignature observed,
            int maximumResults
    ) {
        Objects.requireNonNull(knowledgeBase, "knowledgeBase");
        Objects.requireNonNull(observed, "observed");
        if (maximumResults <= 0) {
            throw new IllegalArgumentException("maximumResults must be positive");
        }

        List<Match> matches = new ArrayList<>();
        for (PulleyTemplate template : knowledgeBase.templates().values()) {
            matches.add(scoreTemplate(template, observed));
        }
        for (ReferenceCase referenceCase : knowledgeBase.referenceCases().values()) {
            matches.add(scoreReference(referenceCase, observed));
        }
        matches.sort(
                Comparator.comparingDouble(Match::score).reversed()
                        .thenComparing(
                                Comparator.comparingDouble(Match::evidenceCoverage).reversed()
                        )
                        .thenComparing(Match::candidateId)
        );
        if (matches.size() > maximumResults) {
            return Collections.unmodifiableList(new ArrayList<>(matches.subList(0, maximumResults)));
        }
        return Collections.unmodifiableList(matches);
    }

    private Match scoreTemplate(PulleyTemplate template, ObservedPulleySignature observed) {
        ScoreAccumulator score = new ScoreAccumulator();
        int usedSignals = 0;
        int possibleSignals = 7;

        Optional<Double> role = roleScore(observed.roleConfidence(), template.likelyRoles());
        if (role.isPresent()) {
            score.add(config.roleWeight, role.get(), "role compatibility=" + round(role.get()));
            usedSignals++;
        }

        if (observed.shaftArrangement() != ShaftArrangement.UNKNOWN) {
            double value = template.shaftArrangement() == observed.shaftArrangement() ? 1.0 : 0.0;
            score.add(
                    config.shaftWeight * observed.shaftArrangementConfidence(),
                    value,
                    value > 0.5 ? "shaft arrangement agrees" : "shaft arrangement conflicts"
            );
            usedSignals++;
        }

        if (observed.laggingType() != LaggingType.UNKNOWN) {
            double value = template.compatibleLagging().contains(observed.laggingType()) ? 1.0 : 0.10;
            score.add(
                    config.laggingWeight * observed.laggingConfidence(),
                    value,
                    value > 0.5 ? "lagging family compatible" : "lagging family unlikely"
            );
            usedSignals++;
        }

        if (!observed.observedComponents().isEmpty()) {
            double value = asymmetricComponentScore(
                    observed.observedComponents(),
                    template.expectedComponents()
            );
            score.add(config.componentWeight, value, "component topology=" + round(value));
            usedSignals++;
        }

        if (!observed.cues().isEmpty()) {
            double value = cueScore(
                    observed.cues(),
                    template.positiveCues(),
                    template.negativeCues()
            );
            score.add(config.cueWeight, value, "visual/operational cues=" + round(value));
            usedSignals++;
        }

        score.add(0.30, template.priorWeight(), "generic prior=" + round(template.priorWeight()));
        double coverage = usedSignals / (double) possibleSignals;
        double finalScore = reliabilityAdjusted(
                score.normalizedScore(),
                observed.evidenceQuality(),
                coverage
        );
        return new Match(
                template.id(),
                template.displayName(),
                CandidateKind.GENERIC_TEMPLATE,
                finalScore,
                coverage,
                score.explanations,
                null
        );
    }

    private Match scoreReference(ReferenceCase reference, ObservedPulleySignature observed) {
        ScoreAccumulator score = new ScoreAccumulator();
        int usedSignals = 0;
        int possibleSignals = 7;

        Optional<Double> role = roleScore(
                observed.roleConfidence(),
                Collections.singleton(reference.role())
        );
        if (role.isPresent()) {
            score.add(config.roleWeight, role.get(), "same operational role=" + round(role.get()));
            usedSignals++;
        }

        if (observed.shaftArrangement() != ShaftArrangement.UNKNOWN) {
            double value = reference.shaftArrangement() == observed.shaftArrangement() ? 1.0 : 0.0;
            score.add(
                    config.shaftWeight * observed.shaftArrangementConfidence(),
                    value,
                    value > 0.5 ? "same shaft arrangement" : "different shaft arrangement"
            );
            usedSignals++;
        }

        if (observed.laggingType() != LaggingType.UNKNOWN) {
            double value = laggingSimilarity(reference.laggingType(), observed.laggingType());
            score.add(
                    config.laggingWeight * observed.laggingConfidence(),
                    value,
                    "lagging similarity=" + round(value)
            );
            usedSignals++;
        }

        if (!observed.observedComponents().isEmpty()) {
            double value = asymmetricComponentScore(
                    observed.observedComponents(),
                    reference.confirmedComponents()
            );
            score.add(config.componentWeight, value, "component topology=" + round(value));
            usedSignals++;
        }

        DimensionComparison dimensions = compareDimensions(
                observed.dimensionsMm(),
                reference.dimensionsMm()
        );
        if (dimensions.comparedCount > 0) {
            score.add(
                    config.dimensionWeight,
                    dimensions.dimensionScore,
                    "direct dimensions=" + round(dimensions.dimensionScore)
                            + " from " + dimensions.comparedCount + " values"
            );
            usedSignals++;
        }
        if (dimensions.ratioCount > 0) {
            score.add(
                    config.ratioWeight,
                    dimensions.ratioScore,
                    "dimension ratios=" + round(dimensions.ratioScore)
                            + " from " + dimensions.ratioCount + " ratios"
            );
            usedSignals++;
        }

        if (!observed.cues().isEmpty() && !reference.tags().isEmpty()) {
            double value = textOverlap(observed.cues(), reference.tags());
            score.add(config.cueWeight, value, "case tags=" + round(value));
            usedSignals++;
        }

        double coverage = usedSignals / (double) possibleSignals;
        double verificationMultiplier = 0.70 + 0.30 * reference.verificationConfidence();
        double finalScore = reliabilityAdjusted(
                score.normalizedScore(),
                observed.evidenceQuality(),
                coverage
        );
        finalScore = clamp01(finalScore * verificationMultiplier + config.verifiedCaseBonus);
        return new Match(
                reference.id(),
                reference.displayName(),
                CandidateKind.VERIFIED_REFERENCE,
                finalScore,
                coverage,
                score.explanations,
                reference.stepReferenceId().orElse(null)
        );
    }

    private Optional<Double> roleScore(Map<Role, Double> observed, Set<Role> candidateRoles) {
        if (observed.isEmpty()) return Optional.empty();
        double best = 0.0;
        for (Map.Entry<Role, Double> entry : observed.entrySet()) {
            if (candidateRoles.contains(entry.getKey()) || entry.getKey() == Role.UNKNOWN) {
                best = Math.max(best, entry.getValue());
            }
        }
        return Optional.of(best);
    }

    private DimensionComparison compareDimensions(
            Map<String, Double> observed,
            Map<String, Double> reference
    ) {
        double dimensionSum = 0.0;
        int dimensionCount = 0;
        for (Map.Entry<String, Double> entry : observed.entrySet()) {
            Double referenceValue = reference.get(entry.getKey());
            if (referenceValue != null) {
                dimensionSum += logSimilarity(
                        entry.getValue(),
                        referenceValue,
                        config.dimensionLogTolerance
                );
                dimensionCount++;
            }
        }

        Map<String, Double> observedRatios = ratios(observed);
        Map<String, Double> referenceRatios = ratios(reference);
        double ratioSum = 0.0;
        int ratioCount = 0;
        for (Map.Entry<String, Double> entry : observedRatios.entrySet()) {
            Double referenceValue = referenceRatios.get(entry.getKey());
            if (referenceValue != null) {
                ratioSum += logSimilarity(
                        entry.getValue(),
                        referenceValue,
                        config.ratioLogTolerance
                );
                ratioCount++;
            }
        }
        return new DimensionComparison(
                dimensionCount == 0 ? 0.0 : dimensionSum / dimensionCount,
                dimensionCount,
                ratioCount == 0 ? 0.0 : ratioSum / ratioCount,
                ratioCount
        );
    }

    private static Map<String, Double> ratios(Map<String, Double> dimensions) {
        Map<String, Double> ratios = new LinkedHashMap<>();
        addRatio(
                ratios,
                "face/shell",
                dimensions.get(PulleyKnowledgeBase.DIM_FACE_WIDTH),
                dimensions.get(PulleyKnowledgeBase.DIM_SHELL_DIAMETER)
        );
        addRatio(
                ratios,
                "shaft/shell",
                dimensions.get(PulleyKnowledgeBase.DIM_SHAFT_DIAMETER),
                dimensions.get(PulleyKnowledgeBase.DIM_SHELL_DIAMETER)
        );
        addRatio(
                ratios,
                "bearing-span/face",
                dimensions.get(PulleyKnowledgeBase.DIM_BEARING_CENTER_DISTANCE),
                dimensions.get(PulleyKnowledgeBase.DIM_FACE_WIDTH)
        );
        addRatio(
                ratios,
                "shaft-length/face",
                dimensions.get(PulleyKnowledgeBase.DIM_OVERALL_SHAFT_LENGTH),
                dimensions.get(PulleyKnowledgeBase.DIM_FACE_WIDTH)
        );
        addRatio(
                ratios,
                "hub/shell",
                dimensions.get(PulleyKnowledgeBase.DIM_HUB_DIAMETER),
                dimensions.get(PulleyKnowledgeBase.DIM_SHELL_DIAMETER)
        );
        return ratios;
    }

    private static void addRatio(
            Map<String, Double> ratios,
            String name,
            Double numerator,
            Double denominator
    ) {
        if (numerator != null && denominator != null && denominator > 0.0) {
            ratios.put(name, numerator / denominator);
        }
    }

    private static double asymmetricComponentScore(
            Set<Component> observed,
            Set<Component> expected
    ) {
        if (observed.isEmpty()) return 0.0;
        double matched = 0.0;
        for (Component component : observed) {
            if (expected.contains(component)) matched += 1.0;
        }
        return matched / observed.size();
    }

    private static double cueScore(
            Set<String> observed,
            Set<String> positive,
            Set<String> negative
    ) {
        double positiveScore = textOverlap(observed, positive);
        double negativeScore = textOverlap(observed, negative);
        return clamp01(0.50 + 0.50 * positiveScore - 0.65 * negativeScore);
    }

    private static double textOverlap(Set<String> first, Set<String> second) {
        if (first.isEmpty() || second.isEmpty()) return 0.0;
        int matched = 0;
        for (String firstValue : first) {
            String normalizedFirst = normalize(firstValue);
            for (String secondValue : second) {
                String normalizedSecond = normalize(secondValue);
                if (normalizedFirst.contains(normalizedSecond)
                        || normalizedSecond.contains(normalizedFirst)) {
                    matched++;
                    break;
                }
            }
        }
        return matched / (double) first.size();
    }

    private static double laggingSimilarity(LaggingType first, LaggingType second) {
        if (first == second) return 1.0;
        if (first == LaggingType.UNKNOWN || second == LaggingType.UNKNOWN) return 0.45;
        if (isRubber(first) && isRubber(second)) return 0.70;
        if (isCeramic(first) && isCeramic(second)) return 0.72;
        if (first == LaggingType.NONE || second == LaggingType.NONE) return 0.05;
        return 0.25;
    }

    private static boolean isRubber(LaggingType type) {
        return type == LaggingType.RUBBER_SMOOTH
                || type == LaggingType.RUBBER_DIAMOND
                || type == LaggingType.RUBBER_PROFILED;
    }

    private static boolean isCeramic(LaggingType type) {
        return type == LaggingType.CERAMIC_DIMPLED
                || type == LaggingType.CERAMIC_SMOOTH
                || type == LaggingType.DIRECT_BOND_CERAMIC;
    }

    private static double logSimilarity(double first, double second, double tolerance) {
        double distance = Math.abs(Math.log(first / second));
        return Math.exp(-distance / tolerance);
    }

    private static double reliabilityAdjusted(
            double rawScore,
            double evidenceQuality,
            double coverage
    ) {
        double reliability = 0.50 + 0.30 * evidenceQuality + 0.20 * coverage;
        return clamp01(0.50 + (rawScore - 0.50) * reliability);
    }

    private static Map<Role, Double> immutableRoleConfidence(Map<Role, Double> values) {
        Objects.requireNonNull(values, "roleConfidence");
        EnumMap<Role, Double> copy = new EnumMap<>(Role.class);
        for (Map.Entry<Role, Double> entry : values.entrySet()) {
            copy.put(
                    Objects.requireNonNull(entry.getKey(), "role"),
                    requireConfidence(entry.getValue(), "role confidence")
            );
        }
        return Collections.unmodifiableMap(copy);
    }

    private static Set<Component> immutableComponents(Set<Component> values) {
        Objects.requireNonNull(values, "observedComponents");
        return Collections.unmodifiableSet(new LinkedHashSet<>(values));
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

    private static Set<String> immutableCues(Set<String> values) {
        Objects.requireNonNull(values, "cues");
        Set<String> copy = new LinkedHashSet<>();
        for (String value : values) copy.add(normalize(requireText(value, "cue")));
        return Collections.unmodifiableSet(copy);
    }

    private static String normalize(String value) {
        return value.trim().toLowerCase().replace('_', ' ').replace('-', ' ');
    }

    private static String requireText(String value, String name) {
        if (value == null || value.trim().isEmpty()) {
            throw new IllegalArgumentException(name + " must not be blank");
        }
        return value.trim();
    }

    private static double requireConfidence(double value, String name) {
        return requireRange(value, 0.0, 1.0, name);
    }

    private static double requireNonNegative(double value, String name) {
        if (!Double.isFinite(value) || value < 0.0) {
            throw new IllegalArgumentException(name + " must be finite and non-negative");
        }
        return value;
    }

    private static double requirePositive(double value, String name) {
        if (!Double.isFinite(value) || value <= 0.0) {
            throw new IllegalArgumentException(name + " must be positive and finite");
        }
        return value;
    }

    private static double requireRange(double value, double min, double max, String name) {
        if (!Double.isFinite(value) || value < min || value > max) {
            throw new IllegalArgumentException(name + " must be between " + min + " and " + max);
        }
        return value;
    }

    private static double clamp01(double value) {
        return Math.max(0.0, Math.min(1.0, value));
    }

    private static String round(double value) {
        return String.format(java.util.Locale.ROOT, "%.3f", value);
    }

    private static final class DimensionComparison {
        private final double dimensionScore;
        private final int comparedCount;
        private final double ratioScore;
        private final int ratioCount;

        private DimensionComparison(
                double dimensionScore,
                int comparedCount,
                double ratioScore,
                int ratioCount
        ) {
            this.dimensionScore = dimensionScore;
            this.comparedCount = comparedCount;
            this.ratioScore = ratioScore;
            this.ratioCount = ratioCount;
        }
    }
}

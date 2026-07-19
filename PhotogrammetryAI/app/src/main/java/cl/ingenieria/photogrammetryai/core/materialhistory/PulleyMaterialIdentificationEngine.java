package cl.ingenieria.photogrammetryai.core.materialhistory;

import cl.ingenieria.photogrammetryai.core.materialhistory.MaterialPulleyKnowledgeBase.ComponentEvidence;
import cl.ingenieria.photogrammetryai.core.materialhistory.MaterialPulleyKnowledgeBase.DimensionKind;
import cl.ingenieria.photogrammetryai.core.materialhistory.MaterialPulleyKnowledgeBase.MaterialFamily;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

/** Identifies a pulley family from material/OT history and mandatory shell length. */
public final class PulleyMaterialIdentificationEngine {
    public enum LengthStatus {
        MATCH,
        NEAR,
        CONFLICT,
        NO_HISTORY
    }

    public enum Action {
        DIRECT_FAMILY_MATCH,
        VERIFY_VARIANT,
        HISTORICAL_FAMILY_NO_LENGTH,
        VISUAL_ONLY,
        NO_MATCH
    }

    public static final class Config {
        private final double lengthMatchFraction;
        private final double lengthNearFraction;
        private final double directThreshold;
        private final double visualThreshold;

        public Config(
                double lengthMatchFraction,
                double lengthNearFraction,
                double directThreshold,
                double visualThreshold
        ) {
            this.lengthMatchFraction = requireFraction(lengthMatchFraction, "lengthMatchFraction");
            this.lengthNearFraction = requireFraction(lengthNearFraction, "lengthNearFraction");
            if (lengthNearFraction < lengthMatchFraction) {
                throw new IllegalArgumentException("lengthNearFraction must be >= lengthMatchFraction");
            }
            this.directThreshold = requireConfidence(directThreshold, "directThreshold");
            this.visualThreshold = requireConfidence(visualThreshold, "visualThreshold");
        }

        public static Config industrialDefaults() {
            return new Config(0.025, 0.08, 0.82, 0.58);
        }
    }

    public static final class Query {
        private final double shellLengthMm;
        private final String materialCode;
        private final String ot;
        private final Double shellDiameterMm;
        private final String description;
        private final Set<String> observedModels;

        public Query(
                double shellLengthMm,
                String materialCode,
                String ot,
                Double shellDiameterMm,
                String description,
                Set<String> observedModels
        ) {
            this.shellLengthMm = requirePositive(shellLengthMm, "shellLengthMm");
            this.materialCode = materialCode == null || materialCode.trim().isEmpty()
                    ? null
                    : MaterialPulleyKnowledgeBase.normalizeMaterialCode(materialCode);
            this.ot = ot == null || ot.trim().isEmpty()
                    ? null
                    : MaterialPulleyKnowledgeBase.normalizeOt(ot);
            if (shellDiameterMm != null) {
                requirePositive(shellDiameterMm, "shellDiameterMm");
            }
            this.shellDiameterMm = shellDiameterMm;
            this.description = description == null ? "" : description.trim();
            this.observedModels = immutableTokens(observedModels);
        }

        public static Query byMaterialCode(String materialCode, double shellLengthMm) {
            return new Query(
                    shellLengthMm,
                    materialCode,
                    null,
                    null,
                    "",
                    Collections.emptySet()
            );
        }

        public double shellLengthMm() { return shellLengthMm; }
        public Optional<String> materialCode() { return Optional.ofNullable(materialCode); }
        public Optional<String> ot() { return Optional.ofNullable(ot); }
        public Optional<Double> shellDiameterMm() { return Optional.ofNullable(shellDiameterMm); }
        public String description() { return description; }
        public Set<String> observedModels() { return observedModels; }
    }

    public static final class Candidate {
        private final MaterialFamily family;
        private final double score;
        private final LengthStatus lengthStatus;
        private final Double historicalShellLengthMm;
        private final Action action;
        private final List<String> reasons;
        private final List<String> warnings;

        private Candidate(
                MaterialFamily family,
                double score,
                LengthStatus lengthStatus,
                Double historicalShellLengthMm,
                Action action,
                List<String> reasons,
                List<String> warnings
        ) {
            this.family = family;
            this.score = score;
            this.lengthStatus = lengthStatus;
            this.historicalShellLengthMm = historicalShellLengthMm;
            this.action = action;
            this.reasons = Collections.unmodifiableList(new ArrayList<>(reasons));
            this.warnings = Collections.unmodifiableList(new ArrayList<>(warnings));
        }

        public MaterialFamily family() { return family; }
        public double score() { return score; }
        public LengthStatus lengthStatus() { return lengthStatus; }
        public Optional<Double> historicalShellLengthMm() {
            return Optional.ofNullable(historicalShellLengthMm);
        }
        public Action action() { return action; }
        public List<String> reasons() { return reasons; }
        public List<String> warnings() { return warnings; }
    }

    public static final class Result {
        private final Query query;
        private final List<Candidate> candidates;

        private Result(Query query, List<Candidate> candidates) {
            this.query = query;
            this.candidates = Collections.unmodifiableList(new ArrayList<>(candidates));
        }

        public Query query() { return query; }
        public List<Candidate> candidates() { return candidates; }
        public Optional<Candidate> best() {
            return candidates.isEmpty() ? Optional.empty() : Optional.of(candidates.get(0));
        }
    }

    private final MaterialPulleyKnowledgeBase knowledge;
    private final Config config;

    public PulleyMaterialIdentificationEngine(
            MaterialPulleyKnowledgeBase knowledge,
            Config config
    ) {
        this.knowledge = Objects.requireNonNull(knowledge, "knowledge");
        this.config = Objects.requireNonNull(config, "config");
    }

    public Result identify(Query query, int limit) {
        Objects.requireNonNull(query, "query");
        if (limit <= 0) throw new IllegalArgumentException("limit must be positive");
        List<Candidate> candidates = new ArrayList<>();
        for (MaterialFamily family : knowledge.families()) {
            Candidate candidate = score(family, query);
            if (candidate.score() > 0.01) candidates.add(candidate);
        }
        candidates.sort(
                Comparator.comparingDouble(Candidate::score).reversed()
                        .thenComparing(candidate -> candidate.family().materialCode())
        );
        if (candidates.size() > limit) {
            candidates = new ArrayList<>(candidates.subList(0, limit));
        }
        return new Result(query, candidates);
    }

    private Candidate score(MaterialFamily family, Query query) {
        List<String> reasons = new ArrayList<>();
        List<String> warnings = new ArrayList<>();
        double weightedSum = 0.0;
        double weightSum = 0.0;

        boolean exactCode = query.materialCode().isPresent()
                && family.materialCode().equals(query.materialCode().get());
        if (query.materialCode().isPresent()) {
            double value = exactCode ? 1.0 : 0.0;
            weightedSum += 4.0 * value;
            weightSum += 4.0;
            reasons.add(exactCode
                    ? "exact material-code match"
                    : "material code differs");
        }

        boolean exactOt = query.ot().isPresent() && family.ots().contains(query.ot().get());
        if (query.ot().isPresent()) {
            double value = exactOt ? 1.0 : 0.0;
            weightedSum += 3.5 * value;
            weightSum += 3.5;
            reasons.add(exactOt ? "OT linked to family" : "OT not linked to family");
        }

        Optional<Double> historicalLength = family.consensusDimensionMm(DimensionKind.SHELL_LENGTH);
        LengthStatus lengthStatus;
        if (historicalLength.isPresent()) {
            double relative = relativeError(query.shellLengthMm(), historicalLength.get());
            lengthStatus = relative <= config.lengthMatchFraction
                    ? LengthStatus.MATCH
                    : relative <= config.lengthNearFraction
                    ? LengthStatus.NEAR
                    : LengthStatus.CONFLICT;
            double lengthScore = Math.exp(-relative / 0.055);
            weightedSum += 4.5 * lengthScore;
            weightSum += 4.5;
            reasons.add(String.format(
                    Locale.ROOT,
                    "shell length %.1f mm vs historical %.1f mm (%s, %.2f%% error)",
                    query.shellLengthMm(),
                    historicalLength.get(),
                    lengthStatus,
                    relative * 100.0
            ));
        } else {
            lengthStatus = LengthStatus.NO_HISTORY;
            weightSum += 4.5;
            weightedSum += (exactCode || exactOt) ? 3.15 : 0.0;
            warnings.add(
                    "No historical shell length: operator-entered length remains authoritative"
            );
        }

        if (query.shellDiameterMm().isPresent()) {
            Optional<Double> historicalDiameter = family.consensusDimensionMm(
                    DimensionKind.SHELL_DIAMETER
            );
            weightSum += 1.8;
            if (historicalDiameter.isPresent()) {
                double relative = relativeError(
                        query.shellDiameterMm().get(),
                        historicalDiameter.get()
                );
                weightedSum += 1.8 * Math.exp(-relative / 0.08);
                reasons.add(String.format(
                        Locale.ROOT,
                        "shell diameter %.1f vs historical %.1f mm",
                        query.shellDiameterMm().get(),
                        historicalDiameter.get()
                ));
            } else {
                warnings.add("No historical shell diameter for this family");
            }
        }

        Set<String> queryDescriptionTokens = tokens(query.description());
        if (!queryDescriptionTokens.isEmpty()) {
            double bestText = 0.0;
            for (String alias : family.aliases()) {
                bestText = Math.max(bestText, jaccard(queryDescriptionTokens, tokens(alias)));
            }
            for (String role : family.roleHints()) {
                bestText = Math.max(bestText, jaccard(queryDescriptionTokens, tokens(role)));
            }
            weightedSum += 1.25 * bestText;
            weightSum += 1.25;
            reasons.add(String.format(Locale.ROOT, "description similarity %.3f", bestText));
        }

        if (!query.observedModels().isEmpty()) {
            Set<String> historicalModels = new LinkedHashSet<>();
            for (ComponentEvidence component : family.components()) {
                historicalModels.addAll(tokens(component.manufacturer()));
                historicalModels.addAll(tokens(component.model()));
            }
            double modelScore = jaccard(query.observedModels(), historicalModels);
            weightedSum += 1.5 * modelScore;
            weightSum += 1.5;
            reasons.add(String.format(Locale.ROOT, "component-model similarity %.3f", modelScore));
        }

        double score = weightSum <= 0.0 ? 0.0 : weightedSum / weightSum;
        Action action;
        if ((exactCode || exactOt) && lengthStatus == LengthStatus.CONFLICT) {
            score = Math.min(score, 0.72);
            action = Action.VERIFY_VARIANT;
            warnings.add(
                    "Exact historical identity conflicts with mandatory shell length; verify variant,"
                            + " report extraction or field measurement before overlay"
            );
        } else if ((exactCode || exactOt) && lengthStatus == LengthStatus.NO_HISTORY) {
            action = Action.HISTORICAL_FAMILY_NO_LENGTH;
        } else if ((exactCode || exactOt)
                && (lengthStatus == LengthStatus.MATCH || lengthStatus == LengthStatus.NEAR)
                && score >= config.directThreshold) {
            action = Action.DIRECT_FAMILY_MATCH;
        } else if (score >= config.visualThreshold
                && lengthStatus != LengthStatus.CONFLICT) {
            action = Action.VISUAL_ONLY;
            warnings.add("Candidate requires visual/STEP confirmation before metrological use");
        } else {
            action = Action.NO_MATCH;
        }

        return new Candidate(
                family,
                clamp01(score),
                lengthStatus,
                historicalLength.orElse(null),
                action,
                reasons,
                warnings
        );
    }

    private static double relativeError(double observed, double expected) {
        return Math.abs(observed - expected) / Math.max(1.0, expected);
    }

    private static Set<String> immutableTokens(Set<String> raw) {
        if (raw == null || raw.isEmpty()) return Collections.emptySet();
        Set<String> result = new LinkedHashSet<>();
        for (String value : raw) result.addAll(tokens(value));
        return Collections.unmodifiableSet(result);
    }

    private static Set<String> tokens(String raw) {
        if (raw == null || raw.trim().isEmpty()) return Collections.emptySet();
        String[] pieces = raw.toUpperCase(Locale.ROOT).split("[^A-Z0-9]+");
        Set<String> result = new LinkedHashSet<>();
        for (String piece : pieces) {
            if (piece.length() >= 2) result.add(piece);
        }
        return result;
    }

    private static double jaccard(Set<String> first, Set<String> second) {
        if (first.isEmpty() || second.isEmpty()) return 0.0;
        int intersection = 0;
        for (String token : first) if (second.contains(token)) intersection++;
        int union = first.size() + second.size() - intersection;
        return union == 0 ? 0.0 : intersection / (double) union;
    }

    private static double requirePositive(double value, String name) {
        if (!Double.isFinite(value) || value <= 0.0) {
            throw new IllegalArgumentException(name + " must be positive and finite");
        }
        return value;
    }

    private static double requireFraction(double value, String name) {
        if (!Double.isFinite(value) || value < 0.0 || value > 1.0) {
            throw new IllegalArgumentException(name + " must be between 0 and 1");
        }
        return value;
    }

    private static double requireConfidence(double value, String name) {
        return requireFraction(value, name);
    }

    private static double clamp01(double value) {
        return Math.max(0.0, Math.min(1.0, value));
    }
}

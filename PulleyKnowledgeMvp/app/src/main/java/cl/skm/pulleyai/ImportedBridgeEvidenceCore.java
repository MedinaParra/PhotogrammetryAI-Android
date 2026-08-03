package cl.skm.pulleyai;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** Ranks measured cross-component pair evidence while keeping all bridges diagnostic. */
public final class ImportedBridgeEvidenceCore {
    private ImportedBridgeEvidenceCore() {}

    public static Result analyze(ImportedComponentGeometryCore.Result topology,
                                 List<Candidate> input) {
        if (topology == null || !topology.solved) {
            return Result.blocked("COMPONENT_TOPOLOGY_MISSING");
        }
        Map<Integer, Integer> componentByFrame = new HashMap<Integer, Integer>();
        for (int index = 0; index < topology.components.size(); index++) {
            for (int frame : topology.components.get(index).frames) {
                componentByFrame.put(frame, index);
            }
        }
        List<Evidence> ranked = new ArrayList<Evidence>();
        int evaluated = 0;
        int crossComponent = 0;
        if (input != null) {
            for (Candidate candidate : input) {
                if (candidate == null) continue;
                evaluated++;
                Integer leftComponent = componentByFrame.get(candidate.leftFrame);
                Integer rightComponent = componentByFrame.get(candidate.rightFrame);
                if (leftComponent == null || rightComponent == null
                        || leftComponent.intValue() == rightComponent.intValue()) continue;
                crossComponent++;
                String tier = tier(candidate);
                if ("REJECT".equals(tier)) continue;
                double score = score(candidate, tier);
                ranked.add(new Evidence(candidate, leftComponent, rightComponent, tier, score));
            }
        }
        Collections.sort(ranked, new Comparator<Evidence>() {
            @Override public int compare(Evidence a, Evidence b) {
                int compare = Double.compare(b.score, a.score);
                if (compare != 0) return compare;
                compare = Integer.compare(b.candidate.fundamentalInliers,
                        a.candidate.fundamentalInliers);
                if (compare != 0) return compare;
                compare = Integer.compare(a.candidate.leftFrame, b.candidate.leftFrame);
                return compare != 0 ? compare
                        : Integer.compare(a.candidate.rightFrame, b.candidate.rightFrame);
            }
        });
        if (ranked.size() > 8) ranked = new ArrayList<Evidence>(ranked.subList(0, 8));
        Evidence recommendation = ranked.isEmpty() ? null : ranked.get(0);
        String state = recommendation == null ? "NO_CROSS_COMPONENT_EVIDENCE"
                : "HIGH_CAPTURE_BRIDGE".equals(recommendation.tier)
                ? "CAPTURE_BRIDGE_READY" : "CAPTURE_BRIDGE_REVIEW";
        return new Result(true, state, evaluated, crossComponent,
                ranked, recommendation, false);
    }

    private static String tier(Candidate candidate) {
        if (!candidate.fundamentalSolved || candidate.fundamentalInliers < 6
                || !Double.isFinite(candidate.fundamentalRmsPx)) return "REJECT";
        if (candidate.fundamentalInliers >= 9
                && candidate.fundamentalInlierRatio >= 0.35
                && candidate.fundamentalRmsPx <= 1.5) {
            return "HIGH_CAPTURE_BRIDGE";
        }
        if (candidate.fundamentalInliers >= 7
                && candidate.fundamentalInlierRatio >= 0.25
                && candidate.fundamentalRmsPx <= 2.5) {
            return "MEDIUM_CAPTURE_BRIDGE";
        }
        if (candidate.fundamentalInliers >= 6
                && candidate.fundamentalInlierRatio >= 0.20
                && candidate.fundamentalRmsPx <= 3.2) {
            return "LOW_CAPTURE_BRIDGE";
        }
        return "REJECT";
    }

    private static double score(Candidate candidate, String tier) {
        double tierScore = "HIGH_CAPTURE_BRIDGE".equals(tier) ? 1000.0
                : "MEDIUM_CAPTURE_BRIDGE".equals(tier) ? 500.0 : 100.0;
        return tierScore + candidate.fundamentalInliers * 18.0
                + candidate.fundamentalInlierRatio * 180.0
                + Math.min(1.0, candidate.roiCoverage) * 45.0
                + Math.min(1.0, candidate.mutualFraction) * 30.0
                + (candidate.crossRing() ? 24.0 : 0.0)
                - candidate.fundamentalRmsPx * 35.0;
    }

    public static final class Candidate {
        public final int leftFrame, rightFrame;
        public final String leftBand, rightBand, sourceStatus;
        public final boolean fundamentalSolved;
        public final int fundamentalInliers;
        public final double fundamentalInlierRatio, fundamentalRmsPx;
        public final double roiCoverage, mutualFraction;

        public Candidate(int leftFrame, int rightFrame,
                         String leftBand, String rightBand, String sourceStatus,
                         boolean fundamentalSolved, int fundamentalInliers,
                         double fundamentalInlierRatio, double fundamentalRmsPx,
                         double roiCoverage, double mutualFraction) {
            this.leftFrame = leftFrame;
            this.rightFrame = rightFrame;
            this.leftBand = "HIGH".equals(leftBand) ? "HIGH" : "LOW";
            this.rightBand = "HIGH".equals(rightBand) ? "HIGH" : "LOW";
            this.sourceStatus = sourceStatus == null ? "WEAK" : sourceStatus;
            this.fundamentalSolved = fundamentalSolved;
            this.fundamentalInliers = Math.max(0, fundamentalInliers);
            this.fundamentalInlierRatio = fundamentalInlierRatio;
            this.fundamentalRmsPx = fundamentalRmsPx;
            this.roiCoverage = roiCoverage;
            this.mutualFraction = mutualFraction;
        }

        public boolean crossRing() { return !leftBand.equals(rightBand); }
    }

    public static final class Evidence {
        public final Candidate candidate;
        public final int leftComponent, rightComponent;
        public final String tier;
        public final double score;

        Evidence(Candidate candidate, int leftComponent, int rightComponent,
                 String tier, double score) {
            this.candidate = candidate;
            this.leftComponent = leftComponent;
            this.rightComponent = rightComponent;
            this.tier = tier;
            this.score = score;
        }
    }

    public static final class Result {
        public final boolean solved;
        public final String state;
        public final int evaluatedPairs, crossComponentPairs;
        public final List<Evidence> ranked;
        public final Evidence recommendation;
        public final boolean autoPromoted;

        Result(boolean solved, String state, int evaluatedPairs,
               int crossComponentPairs, List<Evidence> ranked,
               Evidence recommendation, boolean autoPromoted) {
            this.solved = solved;
            this.state = state;
            this.evaluatedPairs = evaluatedPairs;
            this.crossComponentPairs = crossComponentPairs;
            this.ranked = Collections.unmodifiableList(new ArrayList<Evidence>(ranked));
            this.recommendation = recommendation;
            this.autoPromoted = autoPromoted;
        }

        static Result blocked(String state) {
            return new Result(false, state, 0, 0,
                    Collections.<Evidence>emptyList(), null, false);
        }

        public String summary() {
            if (recommendation == null) {
                return "PUENTE GEOMÉTRICO BLOQUEADO · " + state
                        + " · pares evaluados " + evaluatedPairs
                        + " · cruces " + crossComponentPairs;
            }
            Candidate pair = recommendation.candidate;
            return "PUENTE RECOMENDADO · fotos " + pair.leftFrame + " ↔ "
                    + pair.rightFrame + " · " + recommendation.tier
                    + " · inliers " + pair.fundamentalInliers
                    + " · ratio " + format(pair.fundamentalInlierRatio)
                    + " · RMS " + format(pair.fundamentalRmsPx) + " px"
                    + (pair.crossRing() ? " · cross-ring" : "")
                    + "\nRealice 3 capturas intermedias manteniendo 70–80 % de solape."
                    + " El par no se promueve automáticamente a geometría global.";
        }

        public String canonicalJson() {
            StringBuilder json = new StringBuilder();
            json.append("{\n")
                    .append("\"schema\":\"skm-imported-bridge-evidence/1\",")
                    .append("\n\"state\":\"").append(state).append("\",")
                    .append("\n\"evaluatedPairs\":").append(evaluatedPairs).append(',')
                    .append("\n\"crossComponentPairs\":").append(crossComponentPairs).append(',')
                    .append("\n\"autoPromoted\":false,")
                    .append("\n\"globalReconstruction\":false,")
                    .append("\n\"ranked\":[");
            for (int i = 0; i < ranked.size(); i++) {
                if (i > 0) json.append(',');
                Evidence evidence = ranked.get(i);
                Candidate pair = evidence.candidate;
                json.append("{\"leftFrame\":").append(pair.leftFrame)
                        .append(",\"rightFrame\":").append(pair.rightFrame)
                        .append(",\"leftComponent\":").append(evidence.leftComponent)
                        .append(",\"rightComponent\":").append(evidence.rightComponent)
                        .append(",\"tier\":\"").append(evidence.tier).append("\"")
                        .append(",\"score\":").append(evidence.score)
                        .append(",\"crossRing\":").append(pair.crossRing())
                        .append(",\"fundamentalInliers\":")
                        .append(pair.fundamentalInliers)
                        .append(",\"fundamentalInlierRatio\":")
                        .append(pair.fundamentalInlierRatio)
                        .append(",\"fundamentalRmsPx\":")
                        .append(pair.fundamentalRmsPx)
                        .append(",\"roiCoverage\":").append(pair.roiCoverage)
                        .append(",\"mutualFraction\":").append(pair.mutualFraction)
                        .append('}');
            }
            return json.append("]\n}").toString();
        }
    }

    private static String format(double value) {
        return String.format(java.util.Locale.ROOT, "%.3f", value);
    }
}

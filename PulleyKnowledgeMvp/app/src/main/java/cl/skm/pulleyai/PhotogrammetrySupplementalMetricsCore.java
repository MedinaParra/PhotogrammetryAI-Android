package cl.skm.pulleyai;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** Session-level supplemental metrics without Android or report dependencies. */
public final class PhotogrammetrySupplementalMetricsCore {
    private PhotogrammetrySupplementalMetricsCore() {}

    public static Result aggregate(List<HomographyModelCompetitionCore.Result> competitions,
                                   VisualDegradationAggregationCore.Result degradation) {
        List<HomographyModelCompetitionCore.Result> solved =
                new ArrayList<HomographyModelCompetitionCore.Result>();
        if (competitions != null) {
            for (HomographyModelCompetitionCore.Result result : competitions) {
                if (result != null && result.solved && Double.isFinite(result.homographySupportRatio)) {
                    solved.add(result);
                }
            }
        }
        double weighted = 0.0;
        int totalPairs = 0;
        int planarDominant = 0;
        for (HomographyModelCompetitionCore.Result result : solved) {
            weighted += result.homographySupportRatio * result.pairCount;
            totalPairs += result.pairCount;
            if ("PLANAR_DOMINANT".equals(result.status)) planarDominant++;
        }
        double homographyDominance = totalPairs == 0 ? Double.NaN : weighted / totalPairs;
        double planarDominantFraction = solved.isEmpty() ? Double.NaN
                : planarDominant / (double) solved.size();
        List<String> gaps = new ArrayList<String>();
        if (solved.size() < 4) gaps.add("MODEL_COMPETITION_SAMPLE_TOO_SMALL");
        if (degradation == null) gaps.add("VISUAL_DEGRADATION_MISSING");
        else gaps.addAll(degradation.evidenceGaps);
        return new Result(solved.size(), homographyDominance, planarDominantFraction,
                degradation == null ? Double.NaN : degradation.blurryFrameFraction,
                degradation == null ? Double.NaN : degradation.reflectiveFrameFraction,
                degradation == null ? Double.NaN : degradation.repetitiveAmbiguityFraction,
                gaps);
    }

    public static final class Result {
        public final int solvedPairModels;
        public final double homographyDominanceRatio, planarDominantPairFraction;
        public final double blurryFrameFraction, reflectiveFrameFraction;
        public final double repetitiveAmbiguityFraction;
        public final List<String> evidenceGaps;

        Result(int solvedPairModels, double homographyDominanceRatio,
               double planarDominantPairFraction, double blurryFrameFraction,
               double reflectiveFrameFraction, double repetitiveAmbiguityFraction,
               List<String> evidenceGaps) {
            this.solvedPairModels = solvedPairModels;
            this.homographyDominanceRatio = homographyDominanceRatio;
            this.planarDominantPairFraction = planarDominantPairFraction;
            this.blurryFrameFraction = blurryFrameFraction;
            this.reflectiveFrameFraction = reflectiveFrameFraction;
            this.repetitiveAmbiguityFraction = repetitiveAmbiguityFraction;
            this.evidenceGaps = Collections.unmodifiableList(new ArrayList<String>(evidenceGaps));
        }

        public boolean complete() { return evidenceGaps.isEmpty(); }
    }
}

package cl.skm.pulleyai;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** Produces fail-closed supplemental safety metrics from real frame and pair samples. */
public final class RuntimeSupplementalMetricsCore {
    private RuntimeSupplementalMetricsCore() {}

    public static Result evaluate(List<FrameSample> frames, List<PairSample> pairs) {
        List<VisualDegradationAggregationCore.FrameMetric> frameMetrics =
                new ArrayList<VisualDegradationAggregationCore.FrameMetric>();
        if (frames != null) {
            for (FrameSample frame : frames) {
                if (frame != null && frame.valid()) {
                    frameMetrics.add(new VisualDegradationAggregationCore.FrameMetric(
                            frame.blurScore, frame.minimumBlurScore,
                            frame.highlightFraction, frame.clippedChannelFraction));
                }
            }
        }
        List<VisualDegradationAggregationCore.PairMetric> pairMetrics =
                new ArrayList<VisualDegradationAggregationCore.PairMetric>();
        List<HomographyModelCompetitionCore.Result> competitions =
                new ArrayList<HomographyModelCompetitionCore.Result>();
        if (pairs != null) {
            for (PairSample pair : pairs) {
                if (pair == null || !pair.valid()) continue;
                pairMetrics.add(new VisualDegradationAggregationCore.PairMetric(
                        pair.secondBestRatio, pair.mutualMatchFraction, pair.spatialCoverage));
                competitions.add(HomographyModelCompetitionCore.evaluate(
                        pair.pointPairs, 2.0, Math.max(100, Math.min(300, pair.pointPairs.size() * 3)),
                        pair.fundamentalInliers, pair.fundamentalRmsPx));
            }
        }
        VisualDegradationAggregationCore.Result degradation =
                VisualDegradationAggregationCore.aggregate(frameMetrics, pairMetrics);
        PhotogrammetrySupplementalMetricsCore.Result supplemental =
                PhotogrammetrySupplementalMetricsCore.aggregate(competitions, degradation);
        String status = supplemental.complete() ? "COMPLETE" : "INCOMPLETE";
        return new Result(status, supplemental, frameMetrics.size(), pairMetrics.size(),
                competitions.size());
    }

    public static final class FrameSample {
        public final double blurScore, minimumBlurScore, highlightFraction, clippedChannelFraction;
        public FrameSample(double blurScore, double minimumBlurScore,
                           double highlightFraction, double clippedChannelFraction) {
            this.blurScore = blurScore; this.minimumBlurScore = minimumBlurScore;
            this.highlightFraction = highlightFraction;
            this.clippedChannelFraction = clippedChannelFraction;
        }
        boolean valid() {
            return Double.isFinite(blurScore) && Double.isFinite(minimumBlurScore)
                    && minimumBlurScore > 0 && fraction(highlightFraction)
                    && fraction(clippedChannelFraction);
        }
    }

    public static final class PairSample {
        public final List<HomographyModelCompetitionCore.PointPair> pointPairs;
        public final int fundamentalInliers;
        public final double fundamentalRmsPx, secondBestRatio, mutualMatchFraction, spatialCoverage;
        public PairSample(List<HomographyModelCompetitionCore.PointPair> pointPairs,
                          int fundamentalInliers, double fundamentalRmsPx,
                          double secondBestRatio, double mutualMatchFraction,
                          double spatialCoverage) {
            this.pointPairs = Collections.unmodifiableList(
                    new ArrayList<HomographyModelCompetitionCore.PointPair>(
                            pointPairs == null
                                    ? Collections.<HomographyModelCompetitionCore.PointPair>emptyList()
                                    : pointPairs));
            this.fundamentalInliers = fundamentalInliers;
            this.fundamentalRmsPx = fundamentalRmsPx;
            this.secondBestRatio = secondBestRatio;
            this.mutualMatchFraction = mutualMatchFraction;
            this.spatialCoverage = spatialCoverage;
        }
        boolean valid() {
            return pointPairs.size() >= 4 && fundamentalInliers >= 0
                    && (Double.isFinite(fundamentalRmsPx) || Double.isInfinite(fundamentalRmsPx))
                    && fraction(secondBestRatio) && fraction(mutualMatchFraction)
                    && fraction(spatialCoverage);
        }
    }

    public static final class Result {
        public final String status;
        public final PhotogrammetrySupplementalMetricsCore.Result supplemental;
        public final int frameSamples, pairSamples, solvedPairModels;
        Result(String status, PhotogrammetrySupplementalMetricsCore.Result supplemental,
               int frameSamples, int pairSamples, int solvedPairModels) {
            this.status = status; this.supplemental = supplemental;
            this.frameSamples = frameSamples; this.pairSamples = pairSamples;
            this.solvedPairModels = solvedPairModels;
        }
        public boolean complete() { return supplemental != null && supplemental.complete(); }
        public PhotogrammetrySafetyGateAdapter.SupplementalMetrics toSafetyGate() {
            return PhotogrammetrySupplementalMetricsAdapter.toSafetyGate(supplemental);
        }
        public String summary() {
            return "Métricas " + status + " · frames " + frameSamples + " · pares " + pairSamples
                    + " · H/F " + solvedPairModels
                    + " · blur " + percent(supplemental.blurryFrameFraction)
                    + " · reflejos " + percent(supplemental.reflectiveFrameFraction)
                    + " · repetición " + percent(supplemental.repetitiveAmbiguityFraction)
                    + " · homografía " + percent(supplemental.homographyDominanceRatio);
        }
        public String canonicalJson() {
            StringBuilder json = new StringBuilder(1024);
            json.append("{\n\"schema\":\"skm-runtime-supplemental/1\"")
                    .append(",\n\"status\":\"").append(status).append("\"")
                    .append(",\n\"complete\":").append(complete())
                    .append(",\n\"frameSamples\":").append(frameSamples)
                    .append(",\n\"pairSamples\":").append(pairSamples)
                    .append(",\n\"solvedPairModels\":").append(solvedPairModels)
                    .append(",\n\"homographyDominanceRatio\":").append(number(supplemental.homographyDominanceRatio))
                    .append(",\n\"planarDominantPairFraction\":").append(number(supplemental.planarDominantPairFraction))
                    .append(",\n\"blurryFrameFraction\":").append(number(supplemental.blurryFrameFraction))
                    .append(",\n\"reflectiveFrameFraction\":").append(number(supplemental.reflectiveFrameFraction))
                    .append(",\n\"repetitiveAmbiguityFraction\":").append(number(supplemental.repetitiveAmbiguityFraction))
                    .append(",\n\"evidenceGaps\":[");
            for (int i = 0; i < supplemental.evidenceGaps.size(); i++) {
                if (i > 0) json.append(',');
                json.append('"').append(escape(supplemental.evidenceGaps.get(i))).append('"');
            }
            return json.append("]\n}").toString();
        }
    }

    private static boolean fraction(double value) {
        return Double.isFinite(value) && value >= 0.0 && value <= 1.0;
    }
    private static String percent(double value) {
        return Double.isFinite(value) ? Math.round(value * 100.0) + "%" : "N/D";
    }
    private static String number(double value) {
        return Double.isFinite(value) ? Double.toString(value) : "null";
    }
    private static String escape(String value) {
        return value == null ? "" : value.replace("\\", "\\\\").replace("\"", "\\\"");
    }
}
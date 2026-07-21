package cl.skm.pulleyai;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** Conservative session aggregation for blur, highlights and repetitive ambiguity. */
public final class VisualDegradationAggregationCore {
    private VisualDegradationAggregationCore() {}

    public static Result aggregate(List<FrameMetric> frames, List<PairMetric> pairs) {
        List<FrameMetric> validFrames = new ArrayList<FrameMetric>();
        if (frames != null) for (FrameMetric metric : frames) if (metric != null && metric.valid()) validFrames.add(metric);
        List<PairMetric> validPairs = new ArrayList<PairMetric>();
        if (pairs != null) for (PairMetric metric : pairs) if (metric != null && metric.valid()) validPairs.add(metric);

        double blurryFraction = fractionFrames(validFrames, 0);
        double reflectiveFraction = fractionFrames(validFrames, 1);
        double repetitiveFraction = fractionPairs(validPairs);
        List<String> gaps = new ArrayList<String>();
        if (validFrames.size() < 12) gaps.add("FRAME_DEGRADATION_SAMPLE_TOO_SMALL");
        if (validPairs.size() < 8) gaps.add("PAIR_AMBIGUITY_SAMPLE_TOO_SMALL");
        return new Result(validFrames.size(), validPairs.size(), blurryFraction,
                reflectiveFraction, repetitiveFraction, gaps);
    }

    private static double fractionFrames(List<FrameMetric> frames, int kind) {
        if (frames.isEmpty()) return Double.NaN;
        int count = 0;
        for (FrameMetric frame : frames) {
            boolean hit = kind == 0
                    ? frame.blurScore < frame.minimumBlurScore
                    : frame.highlightFraction >= 0.18 || frame.saturationFraction >= 0.12;
            if (hit) count++;
        }
        return count / (double) frames.size();
    }

    private static double fractionPairs(List<PairMetric> pairs) {
        if (pairs.isEmpty()) return Double.NaN;
        int count = 0;
        for (PairMetric pair : pairs) {
            if (pair.secondBestRatio >= 0.88 || pair.mutualMatchFraction < 0.45
                    || pair.spatialCoverage < 0.30) count++;
        }
        return count / (double) pairs.size();
    }

    public static final class FrameMetric {
        public final double blurScore, minimumBlurScore, highlightFraction, saturationFraction;
        public FrameMetric(double blurScore, double minimumBlurScore,
                           double highlightFraction, double saturationFraction) {
            this.blurScore = blurScore; this.minimumBlurScore = minimumBlurScore;
            this.highlightFraction = highlightFraction; this.saturationFraction = saturationFraction;
        }
        boolean valid() {
            return Double.isFinite(blurScore) && Double.isFinite(minimumBlurScore)
                    && minimumBlurScore > 0 && fraction(highlightFraction) && fraction(saturationFraction);
        }
    }

    public static final class PairMetric {
        public final double secondBestRatio, mutualMatchFraction, spatialCoverage;
        public PairMetric(double secondBestRatio, double mutualMatchFraction, double spatialCoverage) {
            this.secondBestRatio = secondBestRatio; this.mutualMatchFraction = mutualMatchFraction;
            this.spatialCoverage = spatialCoverage;
        }
        boolean valid() {
            return fraction(secondBestRatio) && fraction(mutualMatchFraction) && fraction(spatialCoverage);
        }
    }

    public static final class Result {
        public final int frameCount, pairCount;
        public final double blurryFrameFraction, reflectiveFrameFraction, repetitiveAmbiguityFraction;
        public final List<String> evidenceGaps;

        Result(int frameCount, int pairCount, double blurryFrameFraction,
               double reflectiveFrameFraction, double repetitiveAmbiguityFraction,
               List<String> evidenceGaps) {
            this.frameCount = frameCount; this.pairCount = pairCount;
            this.blurryFrameFraction = blurryFrameFraction;
            this.reflectiveFrameFraction = reflectiveFrameFraction;
            this.repetitiveAmbiguityFraction = repetitiveAmbiguityFraction;
            this.evidenceGaps = Collections.unmodifiableList(new ArrayList<String>(evidenceGaps));
        }

        public boolean complete() { return evidenceGaps.isEmpty(); }
    }

    private static boolean fraction(double value) {
        return Double.isFinite(value) && value >= 0.0 && value <= 1.0;
    }
}

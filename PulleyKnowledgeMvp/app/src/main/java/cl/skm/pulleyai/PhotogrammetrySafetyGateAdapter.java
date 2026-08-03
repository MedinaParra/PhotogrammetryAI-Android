package cl.skm.pulleyai;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** Converts an existing reconstruction report into the fail-closed safety gate metrics. */
public final class PhotogrammetrySafetyGateAdapter {
    private PhotogrammetrySafetyGateAdapter() {}

    public static PhotogrammetrySafetyGateCore.Result evaluate(SessionOverlapAnalyzer.Report report) {
        return evaluate(report, SupplementalMetrics.unknown());
    }

    public static PhotogrammetrySafetyGateCore.Result evaluate(
            SessionOverlapAnalyzer.Report report, SupplementalMetrics supplemental) {
        if (report == null) throw new IllegalArgumentException("report is required");
        if (supplemental == null) supplemental = SupplementalMetrics.unknown();

        List<Double> parallaxes = new ArrayList<Double>();
        List<Double> reprojections = new ArrayList<Double>();
        double supportSum = 0.0;
        int supportCount = 0;
        for (SessionOverlapAnalyzer.Pair pair : report.pairs) {
            if (!pair.usable()) continue;
            if (Double.isFinite(pair.parallaxDegrees)) parallaxes.add(pair.parallaxDegrees);
            if (Double.isFinite(pair.reprojectionRmsPx)) reprojections.add(pair.reprojectionRmsPx);
            if (pair.rawMatches > 0) {
                supportSum += Math.max(0.0, Math.min(1.0, pair.inliers/(double) pair.rawMatches));
                supportCount++;
            }
        }
        double medianParallax = percentile(parallaxes, 0.50);
        double p10Parallax = percentile(parallaxes, 0.10);
        double medianReprojection = percentile(reprojections, 0.50);
        double p90Reprojection = percentile(reprojections, 0.90);
        double fundamentalSupport = supportCount == 0 ? Double.NaN : supportSum/supportCount;
        GlobalPoseGraphCore.Result poses = report.globalPoseGraph;
        boolean connected = poses != null && poses.totalNodes > 0
                && poses.reachedNodes == poses.totalNodes
                && !"DISCONNECTED".equals(poses.status);

        PhotogrammetrySafetyGateCore.Metrics metrics = PhotogrammetrySafetyGateCore.Metrics.builder()
                .frames(report.analyzedFrames,
                        Integer.bitCount(report.selection.lowMask),
                        Integer.bitCount(report.selection.highMask))
                .pairs(report.candidatePairs, report.usablePairs, report.strongPairs)
                .missing(report.missingIntrinsicsFrames, report.missingOrbitPriors)
                .parallax(medianParallax, p10Parallax)
                .modelCompetition(fundamentalSupport, supplemental.homographyDominanceRatio)
                .poseGraph(connected,
                        poses == null ? 0 : poses.trustedCycleEdges,
                        poses == null ? Double.NaN : poses.p90RotationResidualDegrees,
                        poses == null ? Double.NaN : poses.p90TranslationResidualDegrees)
                .reprojection(medianReprojection, p90Reprojection)
                .degradation(supplemental.blurryFrameFraction,
                        supplemental.reflectiveFrameFraction,
                        supplemental.repetitiveAmbiguityFraction)
                .build();
        return PhotogrammetrySafetyGateCore.evaluate(metrics);
    }

    private static double percentile(List<Double> source, double quantile) {
        if (source == null || source.isEmpty()) return Double.NaN;
        List<Double> values = new ArrayList<Double>(source);
        Collections.sort(values);
        double position = Math.max(0.0, Math.min(1.0, quantile))*(values.size()-1);
        int low = (int)Math.floor(position);
        int high = (int)Math.ceil(position);
        if (low == high) return values.get(low);
        double fraction = position-low;
        return values.get(low)*(1.0-fraction)+values.get(high)*fraction;
    }

    public static final class SupplementalMetrics {
        public final double homographyDominanceRatio;
        public final double blurryFrameFraction;
        public final double reflectiveFrameFraction;
        public final double repetitiveAmbiguityFraction;

        public SupplementalMetrics(double homographyDominanceRatio,
                                   double blurryFrameFraction,
                                   double reflectiveFrameFraction,
                                   double repetitiveAmbiguityFraction) {
            this.homographyDominanceRatio = homographyDominanceRatio;
            this.blurryFrameFraction = blurryFrameFraction;
            this.reflectiveFrameFraction = reflectiveFrameFraction;
            this.repetitiveAmbiguityFraction = repetitiveAmbiguityFraction;
        }

        public static SupplementalMetrics unknown() {
            return new SupplementalMetrics(Double.NaN, Double.NaN, Double.NaN, Double.NaN);
        }
    }
}

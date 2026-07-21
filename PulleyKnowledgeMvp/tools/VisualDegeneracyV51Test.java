import cl.skm.pulleyai.HomographyModelCompetitionCore;
import cl.skm.pulleyai.PhotogrammetrySafetyGateCore;
import cl.skm.pulleyai.PhotogrammetrySupplementalMetricsCore;
import cl.skm.pulleyai.VisualDegradationAggregationCore;

import java.util.ArrayList;
import java.util.List;

public final class VisualDegeneracyV51Test {
    public static void main(String[] args) {
        testPlanarSceneDominates();
        testMixedTransformsDoNotFakePlanarity();
        testDegradationAggregationBlocksUnsafeSession();
        testMissingSamplesRemainEvidenceGaps();
        System.out.println("VisualDegeneracyV51Test OK");
    }

    private static void testPlanarSceneDominates() {
        List<HomographyModelCompetitionCore.PointPair> pairs = planarPairs(false);
        HomographyModelCompetitionCore.Result result = HomographyModelCompetitionCore.evaluate(
                pairs, 1.0, 240, 38, 1.4);
        assertTrue(result.solved, "planar homography must solve");
        assertTrue("PLANAR_DOMINANT".equals(result.status), "planar scene not detected: " + result.status);
        assertTrue(result.homographySupportRatio > 0.94, "planar support too low");
        assertTrue(result.homographyRmsPx < 0.4, "planar RMS too high");
    }

    private static void testMixedTransformsDoNotFakePlanarity() {
        List<HomographyModelCompetitionCore.PointPair> pairs = planarPairs(true);
        HomographyModelCompetitionCore.Result result = HomographyModelCompetitionCore.evaluate(
                pairs, 1.0, 300, 55, 0.9);
        assertTrue(result.solved, "mixed model should still produce bounded result");
        assertTrue(!"PLANAR_DOMINANT".equals(result.status), "mixed depth was falsely planar");
        assertTrue(result.homographySupportRatio < 0.72, "mixed support should stay below planar gate");
    }

    private static void testDegradationAggregationBlocksUnsafeSession() {
        List<VisualDegradationAggregationCore.FrameMetric> frames =
                new ArrayList<VisualDegradationAggregationCore.FrameMetric>();
        for (int i = 0; i < 20; i++) {
            double blur = i < 8 ? 40 : 140;
            double highlights = i < 10 ? 0.24 : 0.04;
            frames.add(new VisualDegradationAggregationCore.FrameMetric(blur, 80, highlights, 0.05));
        }
        List<VisualDegradationAggregationCore.PairMetric> pairMetrics =
                new ArrayList<VisualDegradationAggregationCore.PairMetric>();
        for (int i = 0; i < 20; i++) {
            pairMetrics.add(i < 10
                    ? new VisualDegradationAggregationCore.PairMetric(0.94, 0.35, 0.22)
                    : new VisualDegradationAggregationCore.PairMetric(0.55, 0.78, 0.72));
        }
        VisualDegradationAggregationCore.Result degradation =
                VisualDegradationAggregationCore.aggregate(frames, pairMetrics);
        assertNear(degradation.blurryFrameFraction, 0.40, 1e-12, "blur fraction");
        assertNear(degradation.reflectiveFrameFraction, 0.50, 1e-12, "reflection fraction");
        assertNear(degradation.repetitiveAmbiguityFraction, 0.50, 1e-12, "repetitive fraction");

        List<HomographyModelCompetitionCore.Result> competitions =
                new ArrayList<HomographyModelCompetitionCore.Result>();
        for (int i = 0; i < 5; i++) competitions.add(HomographyModelCompetitionCore.evaluate(
                planarPairs(false), 1.0, 180, 38, 1.4));
        PhotogrammetrySupplementalMetricsCore.Result supplemental =
                PhotogrammetrySupplementalMetricsCore.aggregate(competitions, degradation);
        assertTrue(supplemental.complete(), "supplemental metrics should be complete");

        PhotogrammetrySafetyGateCore.Result gate = PhotogrammetrySafetyGateCore.evaluate(
                PhotogrammetrySafetyGateCore.Metrics.builder()
                        .frames(36, 12, 12).pairs(60, 32, 14).missing(0, 0)
                        .parallax(2.5, 0.8)
                        .modelCompetition(0.75, supplemental.homographyDominanceRatio)
                        .poseGraph(true, 6, 3, 20).reprojection(1.2, 2.5)
                        .degradation(supplemental.blurryFrameFraction,
                                supplemental.reflectiveFrameFraction,
                                supplemental.repetitiveAmbiguityFraction).build());
        assertTrue(gate.state == PhotogrammetrySafetyGateCore.State.BLOCKED,
                "unsafe degradation and planarity must block");
    }

    private static void testMissingSamplesRemainEvidenceGaps() {
        VisualDegradationAggregationCore.Result degradation =
                VisualDegradationAggregationCore.aggregate(
                        new ArrayList<VisualDegradationAggregationCore.FrameMetric>(),
                        new ArrayList<VisualDegradationAggregationCore.PairMetric>());
        PhotogrammetrySupplementalMetricsCore.Result result =
                PhotogrammetrySupplementalMetricsCore.aggregate(
                        new ArrayList<HomographyModelCompetitionCore.Result>(), degradation);
        assertTrue(!result.complete(), "missing samples cannot be complete");
        assertTrue(Double.isNaN(result.homographyDominanceRatio),
                "missing model data must remain NaN");
    }

    private static List<HomographyModelCompetitionCore.PointPair> planarPairs(boolean mixed) {
        List<HomographyModelCompetitionCore.PointPair> pairs =
                new ArrayList<HomographyModelCompetitionCore.PointPair>();
        for (int i = 0; i < 60; i++) {
            double x = 40 + (i % 10) * 52;
            double y = 35 + (i / 10) * 58;
            boolean second = mixed && i >= 30;
            double[] mapped = second ? mapSecond(x, y) : mapFirst(x, y);
            double noiseX = ((i * 17) % 7 - 3) * 0.025;
            double noiseY = ((i * 23) % 9 - 4) * 0.020;
            pairs.add(new HomographyModelCompetitionCore.PointPair(
                    x, y, mapped[0] + noiseX, mapped[1] + noiseY));
        }
        return pairs;
    }

    private static double[] mapFirst(double x, double y) {
        double d = 0.0008 * x + 0.0004 * y + 1.0;
        return new double[]{(1.05 * x + 0.04 * y + 25) / d,
                (-0.02 * x + 0.98 * y + 18) / d};
    }

    private static double[] mapSecond(double x, double y) {
        double d = -0.0006 * x + 0.0009 * y + 1.0;
        return new double[]{(0.91 * x - 0.08 * y + 76) / d,
                (0.06 * x + 1.08 * y - 31) / d};
    }

    private static void assertNear(double actual, double expected, double tolerance, String message) {
        if (Math.abs(actual - expected) > tolerance) {
            throw new AssertionError(message + ": " + actual + " != " + expected);
        }
    }

    private static void assertTrue(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }
}

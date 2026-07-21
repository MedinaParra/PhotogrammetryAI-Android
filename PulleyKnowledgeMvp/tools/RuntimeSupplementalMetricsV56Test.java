import cl.skm.pulleyai.HomographyModelCompetitionCore;
import cl.skm.pulleyai.PhotogrammetrySafetyGateCore;
import cl.skm.pulleyai.RuntimeSupplementalMetricsCore;

import java.util.ArrayList;
import java.util.List;

public final class RuntimeSupplementalMetricsV56Test {
    public static void main(String[] args) {
        testCompleteVolumetricEvidenceAllowsReadyGate();
        testPlanarReflectiveRepetitiveEvidenceBlocks();
        testMissingEvidenceRemainsIncomplete();
        System.out.println("RuntimeSupplementalMetricsV56Test OK");
    }

    private static void testCompleteVolumetricEvidenceAllowsReadyGate() {
        List<RuntimeSupplementalMetricsCore.FrameSample> frames = frames(false);
        List<RuntimeSupplementalMetricsCore.PairSample> pairs = new ArrayList<RuntimeSupplementalMetricsCore.PairSample>();
        for (int i = 0; i < 6; i++) {
            pairs.add(new RuntimeSupplementalMetricsCore.PairSample(
                    pointPairs(true), 56, 0.8, 0.58, 0.82, 0.70));
        }
        RuntimeSupplementalMetricsCore.Result supplemental =
                RuntimeSupplementalMetricsCore.evaluate(frames, pairs);
        assertTrue(supplemental.complete(), "volumetric evidence must be complete");
        assertTrue(supplemental.supplemental.homographyDominanceRatio < 0.72,
                "mixed depth must not be planar dominant");
        assertTrue(supplemental.canonicalJson().contains("\"complete\":true"),
                "complete JSON missing");

        PhotogrammetrySafetyGateCore.Result gate = PhotogrammetrySafetyGateCore.evaluate(
                PhotogrammetrySafetyGateCore.Metrics.builder()
                        .frames(36, 12, 12).pairs(60, 36, 16).missing(0, 0)
                        .parallax(2.8, 0.9)
                        .modelCompetition(0.82, supplemental.supplemental.homographyDominanceRatio)
                        .poseGraph(true, 8, 3.0, 20.0)
                        .reprojection(1.1, 2.3)
                        .degradation(supplemental.supplemental.blurryFrameFraction,
                                supplemental.supplemental.reflectiveFrameFraction,
                                supplemental.supplemental.repetitiveAmbiguityFraction)
                        .build());
        assertTrue(gate.state == PhotogrammetrySafetyGateCore.State.READY,
                "complete safe evidence should be READY: " + gate.summary());
    }

    private static void testPlanarReflectiveRepetitiveEvidenceBlocks() {
        List<RuntimeSupplementalMetricsCore.FrameSample> frames = frames(true);
        List<RuntimeSupplementalMetricsCore.PairSample> pairs = new ArrayList<RuntimeSupplementalMetricsCore.PairSample>();
        for (int i = 0; i < 6; i++) {
            pairs.add(new RuntimeSupplementalMetricsCore.PairSample(
                    pointPairs(false), 38, 1.4, 0.94, 0.34, 0.22));
        }
        RuntimeSupplementalMetricsCore.Result supplemental =
                RuntimeSupplementalMetricsCore.evaluate(frames, pairs);
        assertTrue(supplemental.complete(), "unsafe evidence can still be complete");
        assertTrue(supplemental.supplemental.homographyDominanceRatio > 0.85,
                "planar evidence not detected");
        PhotogrammetrySafetyGateCore.Result gate = PhotogrammetrySafetyGateCore.evaluate(
                PhotogrammetrySafetyGateCore.Metrics.builder()
                        .frames(36, 12, 12).pairs(60, 36, 16).missing(0, 0)
                        .parallax(2.8, 0.9)
                        .modelCompetition(0.55, supplemental.supplemental.homographyDominanceRatio)
                        .poseGraph(true, 8, 3.0, 20.0)
                        .reprojection(1.1, 2.3)
                        .degradation(supplemental.supplemental.blurryFrameFraction,
                                supplemental.supplemental.reflectiveFrameFraction,
                                supplemental.supplemental.repetitiveAmbiguityFraction)
                        .build());
        assertTrue(gate.state == PhotogrammetrySafetyGateCore.State.BLOCKED,
                "planar/reflection/repetition must block");
    }

    private static void testMissingEvidenceRemainsIncomplete() {
        RuntimeSupplementalMetricsCore.Result result = RuntimeSupplementalMetricsCore.evaluate(
                new ArrayList<RuntimeSupplementalMetricsCore.FrameSample>(),
                new ArrayList<RuntimeSupplementalMetricsCore.PairSample>());
        assertTrue(!result.complete(), "empty runtime evidence cannot be complete");
        assertTrue("INCOMPLETE".equals(result.status), "missing evidence status");
    }

    private static List<RuntimeSupplementalMetricsCore.FrameSample> frames(boolean unsafe) {
        List<RuntimeSupplementalMetricsCore.FrameSample> frames =
                new ArrayList<RuntimeSupplementalMetricsCore.FrameSample>();
        for (int i = 0; i < 20; i++) {
            frames.add(unsafe
                    ? new RuntimeSupplementalMetricsCore.FrameSample(
                    i < 8 ? 45 : 145, 105, i < 11 ? 0.25 : 0.04, i < 11 ? 0.18 : 0.03)
                    : new RuntimeSupplementalMetricsCore.FrameSample(145, 105, 0.03, 0.02));
        }
        return frames;
    }

    private static List<HomographyModelCompetitionCore.PointPair> pointPairs(boolean mixed) {
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
    private static void assertTrue(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }
}

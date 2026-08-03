import cl.skm.pulleyai.PhotogrammetrySafetyGateCore;

public final class PhotogrammetrySafetyGateV48Test {
    public static void main(String[] args) {
        PhotogrammetrySafetyGateCore.Result ready =
                PhotogrammetrySafetyGateCore.evaluate(strong().build());
        assertState(ready, PhotogrammetrySafetyGateCore.State.READY);
        assertTrue(ready.qualityScore > 0.85, "strong session should have high quality");

        PhotogrammetrySafetyGateCore.Result lowParallax =
                PhotogrammetrySafetyGateCore.evaluate(strong().parallax(0.8, 0.15).build());
        assertState(lowParallax, PhotogrammetrySafetyGateCore.State.BLOCKED);
        assertContains(lowParallax, "paralaje insuficiente");

        PhotogrammetrySafetyGateCore.Result planar =
                PhotogrammetrySafetyGateCore.evaluate(strong().modelCompetition(0.48, 0.91).build());
        assertState(planar, PhotogrammetrySafetyGateCore.State.BLOCKED);
        assertContains(planar, "dominancia planar");

        PhotogrammetrySafetyGateCore.Result disconnected =
                PhotogrammetrySafetyGateCore.evaluate(strong().poseGraph(false, 0, Double.NaN, Double.NaN).build());
        assertState(disconnected, PhotogrammetrySafetyGateCore.State.BLOCKED);
        assertContains(disconnected, "grafo de poses desconectado");

        PhotogrammetrySafetyGateCore.Result reflections =
                PhotogrammetrySafetyGateCore.evaluate(strong().degradation(0.05, 0.31, 0.10).build());
        assertState(reflections, PhotogrammetrySafetyGateCore.State.REVIEW);
        assertContains(reflections, "reflejos especulares");

        PhotogrammetrySafetyGateCore.Result reprojection =
                PhotogrammetrySafetyGateCore.evaluate(strong().reprojection(4.2, 8.5).build());
        assertState(reprojection, PhotogrammetrySafetyGateCore.State.BLOCKED);
        assertContains(reprojection, "reproyección excesivo");

        PhotogrammetrySafetyGateCore.Result unknownPlanarity =
                PhotogrammetrySafetyGateCore.evaluate(strong()
                        .modelCompetition(0.75, Double.NaN)
                        .degradation(Double.NaN, Double.NaN, Double.NaN).build());
        assertState(unknownPlanarity, PhotogrammetrySafetyGateCore.State.REVIEW);
        assertTrue(!unknownPlanarity.evidenceGaps.isEmpty(),
                "unknown metrics must not be silently accepted");

        PhotogrammetrySafetyGateCore.Result poorCoverage =
                PhotogrammetrySafetyGateCore.evaluate(strong().frames(36, 7, 12).build());
        assertState(poorCoverage, PhotogrammetrySafetyGateCore.State.BLOCKED);
        assertContains(poorCoverage, "cobertura orbital");

        System.out.println("PhotogrammetrySafetyGateV48Test OK quality=" + ready.qualityScore);
    }

    private static PhotogrammetrySafetyGateCore.Metrics.Builder strong() {
        return PhotogrammetrySafetyGateCore.Metrics.builder()
                .frames(36, 12, 12)
                .pairs(60, 32, 14)
                .missing(0, 0)
                .parallax(2.5, 0.8)
                .modelCompetition(0.75, 0.35)
                .poseGraph(true, 6, 3.0, 20.0)
                .reprojection(1.2, 2.5)
                .degradation(0.05, 0.10, 0.10);
    }

    private static void assertState(PhotogrammetrySafetyGateCore.Result result,
                                    PhotogrammetrySafetyGateCore.State expected) {
        if (result.state != expected) {
            throw new AssertionError("expected " + expected + " but got " + result.summary());
        }
    }

    private static void assertContains(PhotogrammetrySafetyGateCore.Result result, String value) {
        if (!result.summary().contains(value)) {
            throw new AssertionError("missing reason '" + value + "': " + result.summary());
        }
    }

    private static void assertTrue(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }
}

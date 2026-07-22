import cl.skm.pulleyai.CoveragePlanner;
import cl.skm.pulleyai.GuidedCaptureAdmissionCore;

import java.util.ArrayList;
import java.util.List;

public final class GuidedCaptureAdmissionV62Test {
    public static void main(String[] args) {
        testDuplicateAndCellLimit();
        testObstructionAndDetail();
        testGuidance();
        System.out.println("GuidedCaptureAdmissionV62Test OK");
    }

    private static void testDuplicateAndCellLimit() {
        List<GuidedCaptureAdmissionCore.ExistingFrame> frames =
                new ArrayList<GuidedCaptureAdmissionCore.ExistingFrame>();
        frames.add(new GuidedCaptureAdmissionCore.ExistingFrame("LOW", 2, 62.0));
        GuidedCaptureAdmissionCore.Decision duplicate = GuidedCaptureAdmissionCore.evaluate(
                true, "ok", "LOW", 2, 65.0, frames, 0.1, 0.2);
        require(!duplicate.accepted && duplicate.reason.contains("redundante"),
                "duplicate must block");
        GuidedCaptureAdmissionCore.Decision distinct = GuidedCaptureAdmissionCore.evaluate(
                true, "ok", "LOW", 2, 72.0, frames, 0.1, 0.2);
        require(distinct.accepted, "distinct second view must pass");
        frames.add(new GuidedCaptureAdmissionCore.ExistingFrame("LOW", 2, 72.0));
        GuidedCaptureAdmissionCore.Decision full = GuidedCaptureAdmissionCore.evaluate(
                true, "ok", "LOW", 2, 80.0, frames, 0.1, 0.2);
        require(!full.accepted && full.reason.contains("dos vistas"),
                "cell limit must block");
    }

    private static void testObstructionAndDetail() {
        int width = 60, height = 40;
        double[] clear = new double[width * height];
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                clear[y * width + x] = 40 + ((x * 17 + y * 31) % 170);
            }
        }
        GuidedCaptureAdmissionCore.FrameMetrics clearMetrics =
                GuidedCaptureAdmissionCore.analyzeGrid(clear, width, height);
        require(clearMetrics.centerDetailRatio > 0.025, "textured center expected");

        double[] blocked = clear.clone();
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width / 5; x++) blocked[y * width + x] = 90;
        }
        GuidedCaptureAdmissionCore.FrameMetrics blockedMetrics =
                GuidedCaptureAdmissionCore.analyzeGrid(blocked, width, height);
        require(blockedMetrics.borderObstructionScore >= 0.82,
                "uniform border obstruction expected: " + blockedMetrics.borderObstructionScore);
        GuidedCaptureAdmissionCore.Decision rejected = GuidedCaptureAdmissionCore.evaluate(
                true, "ok", "LOW", 0, 0,
                new ArrayList<GuidedCaptureAdmissionCore.ExistingFrame>(),
                blockedMetrics.borderObstructionScore, blockedMetrics.centerDetailRatio);
        require(!rejected.accepted && rejected.reason.contains("obstruida"),
                "obstruction must block");
    }

    private static void testGuidance() {
        int complete = CoveragePlanner.COMPLETE_MASK;
        String high = GuidedCaptureAdmissionCore.nextInstruction(complete, 0, "LOW", 12, false);
        require(high.contains("ALTA"), "must request high ring");
        String extra = GuidedCaptureAdmissionCore.nextInstruction(complete, complete, "HIGH", 24, false);
        require(extra.contains("6 vistas"), "must request 30 accepted");
        String overlap = GuidedCaptureAdmissionCore.nextInstruction(complete, complete, "HIGH", 30, false);
        require(overlap.contains("VERIFICAR SOLAPE"), "must request overlap");
        String ready = GuidedCaptureAdmissionCore.nextInstruction(complete, complete, "HIGH", 30, true);
        require(ready.contains("listo"), "ready guidance");
    }

    private static void require(boolean value, String message) {
        if (!value) throw new AssertionError(message);
    }
}

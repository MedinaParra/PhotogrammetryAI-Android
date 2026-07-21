import cl.skm.pulleyai.DeviceDiagnosticsCore;
import cl.skm.pulleyai.InitialDeviceCampaignCore;
import cl.skm.pulleyai.PhotogrammetrySafetyGateCore;
import cl.skm.pulleyai.RuntimeExecutionControlCore;
import cl.skm.pulleyai.RuntimeReconstructionDecisionCore;

public final class RuntimeControlDeviceDiagnosticsV57Test {
    public static void main(String[] args) {
        testDeadlineAbortsAtCheckpoint();
        testUserCancellationAbortsAtCheckpoint();
        testAutomaticDiagnosticsStates();
        testCampaignConsumesDiagnostics();
        testInterruptedDecisionUsesFallback();
        System.out.println("RuntimeControlDeviceDiagnosticsV57Test OK");
    }

    private static void testDeadlineAbortsAtCheckpoint() {
        RuntimeExecutionControlCore.Token token = RuntimeExecutionControlCore.startAt(1_000L, 2_000L);
        token.checkpointAt(2_900L, "BEFORE_LIMIT");
        try {
            token.checkpointAt(3_001L, "PAIR_LOOP");
            throw new AssertionError("deadline must abort");
        } catch (RuntimeExecutionControlCore.AbortedException error) {
            assertTrue(error.state == RuntimeExecutionControlCore.State.TIMED_OUT, "timeout state");
            assertTrue("PAIR_LOOP".equals(error.stage), "timeout stage");
        }
    }

    private static void testUserCancellationAbortsAtCheckpoint() {
        RuntimeExecutionControlCore.Token token = RuntimeExecutionControlCore.startAt(10L, 30_000L);
        token.cancel("USER_CANCELLED");
        try {
            token.checkpointAt(20L, "CACHE_FRAME_2");
            throw new AssertionError("cancel must abort");
        } catch (RuntimeExecutionControlCore.AbortedException error) {
            assertTrue(error.state == RuntimeExecutionControlCore.State.CANCELLED, "cancel state");
            assertTrue("USER_CANCELLED".equals(error.reason), "cancel reason");
        }
    }

    private static void testAutomaticDiagnosticsStates() {
        DeviceDiagnosticsCore.Result complete = DeviceDiagnosticsCore.evaluate(
                new DeviceDiagnosticsCore.Record(100L, "Samsung A15", 35,
                        39.5, 1, 820, 220, 2400, true, 0, 0));
        assertTrue(complete.state == DeviceDiagnosticsCore.State.COMPLETE, "complete diagnostics");
        assertTrue(complete.canonicalJson().contains("\"nativeCrashExitCount\":0"), "diagnostic json");

        DeviceDiagnosticsCore.Result partial = DeviceDiagnosticsCore.evaluate(
                new DeviceDiagnosticsCore.Record(100L, "Honor X5C", 29,
                        Double.NaN, -1, 640, 180, 1700, false, 0, 0));
        assertTrue(partial.state == DeviceDiagnosticsCore.State.PARTIAL, "partial diagnostics");
        assertTrue(!partial.gaps.isEmpty(), "partial gaps");
    }

    private static void testCampaignConsumesDiagnostics() {
        InitialDeviceCampaignCore.Record partial = new InitialDeviceCampaignCore.Record(
                "Honor X5C", true, 35, 41.0, 900, 0, 6, 4, true,
                true, "PARTIAL", 2);
        InitialDeviceCampaignCore.Result review = InitialDeviceCampaignCore.evaluate(partial);
        assertTrue(review.state == InitialDeviceCampaignCore.State.REVIEW,
                "partial diagnostics must be review");

        InitialDeviceCampaignCore.Record nativeFailure = new InitialDeviceCampaignCore.Record(
                "Samsung A15", true, 35, 41.0, 900, 1, 6, 4, true,
                true, "COMPLETE", 1);
        InitialDeviceCampaignCore.Result blocked = InitialDeviceCampaignCore.evaluate(nativeFailure);
        assertTrue(blocked.state == InitialDeviceCampaignCore.State.BLOCKED,
                "native crash must block");
    }

    private static void testInterruptedDecisionUsesFallback() {
        PhotogrammetrySafetyGateCore.Result gate = PhotogrammetrySafetyGateCore.evaluate(
                PhotogrammetrySafetyGateCore.Metrics.builder()
                        .frames(36, 12, 12).pairs(60, 36, 16).missing(0, 0)
                        .parallax(2.8, 0.9).modelCompetition(0.82, 0.42)
                        .poseGraph(true, 8, 3.0, 20.0)
                        .reprojection(1.1, 2.3).degradation(0.02, 0.03, 0.04).build());
        assertTrue(gate.state == PhotogrammetrySafetyGateCore.State.READY, "test gate ready");
        RuntimeReconstructionDecisionCore.Result decision =
                RuntimeReconstructionDecisionCore.decide(gate, null, true, true, true);
        assertTrue(decision.state == RuntimeReconstructionDecisionCore.State.REVIEW,
                "interruption must review");
        assertTrue(decision.useUnoptimizedFallback, "interruption fallback");
        assertTrue(!decision.useOptimizedGeometry, "interruption cannot optimize");
        assertTrue("OPTIMIZATION_INTERRUPTED".equals(decision.reason), "interruption reason");
    }

    private static void assertTrue(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }
}
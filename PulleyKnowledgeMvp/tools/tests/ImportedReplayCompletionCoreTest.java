package cl.skm.pulleyai;

public final class ImportedReplayCompletionCoreTest {
    public static void main(String[] args) {
        ImportedReplayCompletionCore.Input demo = new ImportedReplayCompletionCore.Input();
        demo.packageValid = true;
        demo.matchingComplete = true;
        demo.tracksComplete = true;
        demo.seedStageComplete = true;
        demo.componentStageComplete = true;
        demo.bridgeStageComplete = true;
        demo.globalConnected = false;
        demo.localGeometryReady = true;
        demo.metricScaleReady = false;
        demo.metrologyValidated = false;
        demo.deviceCampaignPassed = false;
        demo.corporateIdentitySigned = false;
        demo.acceptedFrames = 36;
        demo.representedFrames = 36;
        demo.trackCount = 1044;
        demo.triangulatedSeedPoints = 63;
        demo.bridgeLeftFrame = 14;
        demo.bridgeRightFrame = 41;
        ImportedReplayCompletionCore.Result result =
                ImportedReplayCompletionCore.evaluate(demo);
        check(result.softwareReplayComplete, "software complete");
        check(result.softwareRoadmapPercent == 100, "software 100 percent");
        check("SOFTWARE_REPLAY_COMPLETE_RECAPTURE_REQUIRED".equals(result.state),
                "recapture state");
        check(!result.industrialReady, "industrial blocked");
        check(!result.globalConnected, "global blocked");
        check(result.summary().contains("fotos 14 ↔ 41"), "bridge frames");
        check(result.canonicalJson().contains("skm-imported-replay-completion/1"),
                "schema");

        ImportedReplayCompletionCore.Input missing = new ImportedReplayCompletionCore.Input();
        missing.packageValid = true;
        ImportedReplayCompletionCore.Result incomplete =
                ImportedReplayCompletionCore.evaluate(missing);
        check(!incomplete.softwareReplayComplete, "missing stages blocked");
        check(incomplete.softwareRoadmapPercent < 100, "missing below 100");
        System.out.println("ImportedReplayCompletionCoreTest PASS · " + result.summary());
    }

    private static void check(boolean value, String label) {
        if (!value) throw new AssertionError(label);
    }
}

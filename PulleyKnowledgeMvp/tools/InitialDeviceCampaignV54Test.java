import cl.skm.pulleyai.InitialDeviceCampaignCore;

public final class InitialDeviceCampaignV54Test {
    public static void main(String[] args) {
        testReadyCampaign();
        testReviewCampaign();
        testBlockedCampaign();
        testCanonicalJson();
        System.out.println("InitialDeviceCampaignV54Test OK");
    }

    private static void testReadyCampaign() {
        InitialDeviceCampaignCore.Result result = InitialDeviceCampaignCore.evaluate(
                new InitialDeviceCampaignCore.Record("Samsung A15", true, 35, 41.5,
                        980, 0, 6, 4, true));
        assertTrue(result.state == InitialDeviceCampaignCore.State.READY, "ready campaign expected");
    }

    private static void testReviewCampaign() {
        InitialDeviceCampaignCore.Result result = InitialDeviceCampaignCore.evaluate(
                new InitialDeviceCampaignCore.Record("Honor X5C", true, 25, 44.0,
                        1350, 0, 4, 2, true));
        assertTrue(result.state == InitialDeviceCampaignCore.State.REVIEW, "review campaign expected");
        assertTrue(!result.warnings.isEmpty(), "warnings expected");
    }

    private static void testBlockedCampaign() {
        InitialDeviceCampaignCore.Result result = InitialDeviceCampaignCore.evaluate(
                new InitialDeviceCampaignCore.Record("Samsung A15", false, 10, 49.0,
                        1950, 1, 1, 0, false));
        assertTrue(result.state == InitialDeviceCampaignCore.State.BLOCKED, "blocked campaign expected");
        assertTrue(result.blockers.size() >= 5, "multiple blockers expected");
    }

    private static void testCanonicalJson() {
        InitialDeviceCampaignCore.Record record = new InitialDeviceCampaignCore.Record(
                "HONOR \"X5C\"", true, 30, 42.0, 1000, 0, 5, 3, true);
        InitialDeviceCampaignCore.Result result = InitialDeviceCampaignCore.evaluate(record);
        String json = result.canonicalJson(record);
        assertTrue(json.contains("HONOR \\\"X5C\\\""), "device must be escaped");
        assertTrue(json.contains("\"state\":\"READY\""), "state must be serialized");
    }

    private static void assertTrue(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }
}

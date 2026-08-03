import cl.skm.pulleyai.DeviceQualificationCore;
import cl.skm.pulleyai.EngineeringReleaseGateCore;
import cl.skm.pulleyai.QualificationEvidenceManifestCore;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public final class EngineeringQualificationV53Test {
    public static void main(String[] args) {
        testMissingPhysicalEvidenceBlocksIndustrialClaim();
        testPassingDevicesWithoutMetrologyAllowOnlyPilotReview();
        testDeviceFailureBlocksPilot();
        testCompleteSyntheticEvidenceCanSatisfyGate();
        testManifestFingerprintIsOrderIndependent();
        System.out.println("EngineeringQualificationV53Test OK");
    }

    private static void testMissingPhysicalEvidenceBlocksIndustrialClaim() {
        EngineeringReleaseGateCore.Result result = EngineeringReleaseGateCore.evaluate(
                new EngineeringReleaseGateCore.Input(true, true, true,
                        new ArrayList<DeviceQualificationCore.Result>(), 2, 0, Double.NaN,
                        null, false));
        assertTrue(result.alphaReady, "CI-qualified alpha should remain alpha-ready");
        assertTrue(!result.fieldPilotAllowed && !result.industrialQualified,
                "missing physical evidence must block pilot/industrial");
        assertTrue(result.state == EngineeringReleaseGateCore.State.ALPHA_READY,
                "expected alpha-ready only");
    }

    private static void testPassingDevicesWithoutMetrologyAllowOnlyPilotReview() {
        List<DeviceQualificationCore.Result> devices = Arrays.asList(passDevice("A15"), passDevice("X5C"));
        EngineeringReleaseGateCore.Result result = EngineeringReleaseGateCore.evaluate(
                new EngineeringReleaseGateCore.Input(true, true, true, devices, 2,
                        30, 0.97, null, false));
        assertTrue(result.fieldPilotAllowed, "passing device campaigns should allow pilot review");
        assertTrue(!result.industrialQualified, "missing metrology must block industrial qualification");
        assertTrue(result.state == EngineeringReleaseGateCore.State.FIELD_PILOT_REVIEW,
                "expected field pilot review");
    }

    private static void testDeviceFailureBlocksPilot() {
        DeviceQualificationCore.Run bad = new DeviceQualificationCore.Run(
                true, true, true, false, true, 25, 51, 1600,
                1, 6, 2, 3, 1);
        DeviceQualificationCore.Result blocked = DeviceQualificationCore.evaluate(
                new DeviceQualificationCore.Campaign("BAD", 1200, Arrays.asList(bad, bad, bad)));
        EngineeringReleaseGateCore.Result result = EngineeringReleaseGateCore.evaluate(
                new EngineeringReleaseGateCore.Input(true, true, true,
                        Arrays.asList(passDevice("A15"), blocked), 2, 30, 0.97,
                        null, false));
        assertTrue(!result.fieldPilotAllowed, "blocked device must block pilot");
        assertTrue(!result.industrialQualified, "blocked device must block industrial");
    }

    private static void testCompleteSyntheticEvidenceCanSatisfyGate() {
        EngineeringReleaseGateCore.MetrologyEvidence metrology =
                new EngineeringReleaseGateCore.MetrologyEvidence(true, 6, 6,
                        7.5, 24.0, 5.5);
        EngineeringReleaseGateCore.Result result = EngineeringReleaseGateCore.evaluate(
                new EngineeringReleaseGateCore.Input(true, true, true,
                        Arrays.asList(passDevice("A15"), passDevice("X5C")), 2,
                        40, 0.99, metrology, true));
        assertTrue(result.industrialQualified, "complete synthetic evidence should satisfy logic");
        assertTrue(result.state == EngineeringReleaseGateCore.State.INDUSTRIAL_QUALIFIED,
                "qualified state expected");
    }

    private static void testManifestFingerprintIsOrderIndependent() {
        String hash = repeat('a', 64);
        QualificationEvidenceManifestCore.Entry a = new QualificationEvidenceManifestCore.Entry(
                "CI", "run-512", "PASS", hash, "github-actions");
        QualificationEvidenceManifestCore.Entry b = new QualificationEvidenceManifestCore.Entry(
                "DEVICE", "A15-run-1", "NOT_EXECUTED", "", "physical-campaign");
        QualificationEvidenceManifestCore.Manifest first = QualificationEvidenceManifestCore.create(
                "alpha28", Arrays.asList(a, b));
        QualificationEvidenceManifestCore.Manifest second = QualificationEvidenceManifestCore.create(
                "alpha28", Arrays.asList(b, a));
        assertTrue(first.fingerprint.equals(second.fingerprint), "manifest must be order independent");
        assertTrue(first.fingerprint.length() == 64, "manifest fingerprint must be SHA-256");
    }

    private static DeviceQualificationCore.Result passDevice(String id) {
        List<DeviceQualificationCore.Run> runs = new ArrayList<DeviceQualificationCore.Run>();
        for (int i = 0; i < 3; i++) runs.add(new DeviceQualificationCore.Run(
                true, true, true, true, true, 35, 40.5, 850,
                0, 8, 8, 4, 4));
        DeviceQualificationCore.Result result = DeviceQualificationCore.evaluate(
                new DeviceQualificationCore.Campaign(id, 1200, runs));
        assertTrue(result.state == DeviceQualificationCore.State.PASS,
                "synthetic passing campaign rejected: " + result.state);
        return result;
    }

    private static String repeat(char value, int count) {
        StringBuilder out = new StringBuilder();
        for (int i = 0; i < count; i++) out.append(value);
        return out.toString();
    }

    private static void assertTrue(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }
}

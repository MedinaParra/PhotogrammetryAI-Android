import cl.skm.pulleyai.LocalEvidenceSignatureCore;
import cl.skm.pulleyai.RuntimeExecutionEvidenceCore;

import java.security.KeyPair;

public final class LocalEvidenceSignatureAutomaticExecutionV62Test {
    public static void main(String[] args) throws Exception {
        testAutomaticExecutionEvidence();
        testEd25519SignatureAndTampering();
        testWrongPublicKeyIsRejected();
        testUnavailableStateIsExplicit();
        System.out.println("LocalEvidenceSignatureAutomaticExecutionV62Test OK");
    }

    private static void testAutomaticExecutionEvidence() {
        RuntimeExecutionEvidenceCore.Result first = RuntimeExecutionEvidenceCore.build(
                "session-01", "runtime-100", "READY",
                1000L, 2500L, 1500L, 32, 3,
                "READY", 6, 80, 620, "PASS", "READY",
                "GEOMETRY_ACCEPTED", true, false, false, "RUNNING");
        RuntimeExecutionEvidenceCore.Result second = RuntimeExecutionEvidenceCore.build(
                "session-01", "runtime-100", "READY",
                1000L, 2500L, 1500L, 32, 3,
                "READY", 6, 80, 620, "PASS", "READY",
                "GEOMETRY_ACCEPTED", true, false, false, "RUNNING");
        assertTrue(first.complete, "automatic execution evidence should be complete");
        assertTrue(first.canonicalJson.contains("\"source\":\"AUTOMATIC_RUNTIME\""),
                "automatic source marker missing");
        assertTrue(first.canonicalJson.contains("\"manualEntry\":false"),
                "manual entry must be false");
        assertTrue(first.fingerprint.equals(second.fingerprint),
                "execution fingerprint must be deterministic");
    }

    private static void testEd25519SignatureAndTampering() throws Exception {
        KeyPair keyPair = LocalEvidenceSignatureCore.generateSoftwareKeyPair();
        String payload = "{\"schema\":\"campaign\",\"state\":\"COMPLETE\"}";
        LocalEvidenceSignatureCore.Result result = LocalEvidenceSignatureCore.sign(
                payload, keyPair, "SOFTWARE_APP_PRIVATE", false, false);
        assertTrue(result.verified, "signature must self-verify");
        assertTrue(result.canonicalJson.contains("LOCAL_DEVICE_KEY_NOT_CORPORATE_IDENTITY"),
                "identity warning missing");
        assertTrue(result.canonicalJson.contains("\"corporateIdentity\":false"),
                "corporate identity must remain false");
        assertTrue(LocalEvidenceSignatureCore.verify(payload, result.canonicalJson).valid,
                "valid signature rejected");
        assertTrue(!LocalEvidenceSignatureCore.verify(payload + "x", result.canonicalJson).valid,
                "tampered payload accepted");
    }

    private static void testWrongPublicKeyIsRejected() throws Exception {
        String payload = "manifest-payload";
        LocalEvidenceSignatureCore.Result first = LocalEvidenceSignatureCore.sign(
                payload, LocalEvidenceSignatureCore.generateSoftwareKeyPair(),
                "SOFTWARE_APP_PRIVATE", false, false);
        LocalEvidenceSignatureCore.Result second = LocalEvidenceSignatureCore.sign(
                payload, LocalEvidenceSignatureCore.generateSoftwareKeyPair(),
                "SOFTWARE_APP_PRIVATE", false, false);
        String replaced = first.canonicalJson.replace(first.publicKeyBase64, second.publicKeyBase64);
        assertTrue(!LocalEvidenceSignatureCore.verify(payload, replaced).valid,
                "signature with wrong public key accepted");
    }

    private static void testUnavailableStateIsExplicit() {
        LocalEvidenceSignatureCore.Result unavailable =
                LocalEvidenceSignatureCore.unavailable("payload", "NO_PROVIDER");
        assertTrue(!unavailable.verified, "unavailable signature cannot be verified");
        assertTrue(unavailable.canonicalJson.contains("\"keyOrigin\":\"UNAVAILABLE\""),
                "unavailable origin missing");
        assertTrue(!LocalEvidenceSignatureCore.verify("payload", unavailable.canonicalJson).valid,
                "unavailable signature must not verify");
    }

    private static void assertTrue(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }
}

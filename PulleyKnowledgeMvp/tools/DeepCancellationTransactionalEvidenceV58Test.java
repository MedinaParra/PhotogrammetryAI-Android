import cl.skm.pulleyai.CampaignEvidenceManifestCore;
import cl.skm.pulleyai.RuntimeCancellationBridge;
import cl.skm.pulleyai.RuntimeEvidenceTransactionCore;
import cl.skm.pulleyai.RuntimeExecutionControlCore;
import cl.skm.pulleyai.VisualFeatureCore;

import java.io.File;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public final class DeepCancellationTransactionalEvidenceV58Test {
    public static void main(String[] args) throws Exception {
        testCancellationInsideFeatureDetection();
        testCancellationInsideFeatureMatching();
        testAtomicGenerationAndRollback();
        testAbortGenerationRemovesUnsafePartialEvidence();
        testCampaignManifestIsDeterministic();
        System.out.println("DeepCancellationTransactionalEvidenceV58Test OK");
    }

    private static void testCancellationInsideFeatureDetection() {
        RuntimeExecutionControlCore.Token token = RuntimeExecutionControlCore.start(60_000L);
        token.cancel("TEST_DETECT_CANCEL");
        RuntimeCancellationBridge.install(token);
        try {
            VisualFeatureCore.detect(texturedGray(192, 144), 192, 144, 300);
            throw new AssertionError("feature detection must observe cancellation");
        } catch (RuntimeExecutionControlCore.AbortedException error) {
            assertTrue(error.state == RuntimeExecutionControlCore.State.CANCELLED,
                    "detection cancellation state");
            assertTrue(error.stage.startsWith("FEATURE_DETECT"),
                    "detection cancellation stage: " + error.stage);
        } finally {
            RuntimeCancellationBridge.clear();
        }
    }

    private static void testCancellationInsideFeatureMatching() {
        RuntimeCancellationBridge.clear();
        byte[] gray = texturedGray(192, 144);
        VisualFeatureCore.FeatureSet features = VisualFeatureCore.detect(gray, 192, 144, 300);
        assertTrue(!features.features.isEmpty(), "synthetic texture must generate features");
        RuntimeExecutionControlCore.Token token = RuntimeExecutionControlCore.start(60_000L);
        token.cancel("TEST_MATCH_CANCEL");
        RuntimeCancellationBridge.install(token);
        try {
            VisualFeatureCore.match(features, features);
            throw new AssertionError("feature matching must observe cancellation");
        } catch (RuntimeExecutionControlCore.AbortedException error) {
            assertTrue(error.stage.startsWith("FEATURE_MATCH"),
                    "matching cancellation stage: " + error.stage);
        } finally {
            RuntimeCancellationBridge.clear();
        }
    }

    private static void testAtomicGenerationAndRollback() throws Exception {
        File root = Files.createTempDirectory("skm-runtime-tx").toFile();
        try {
            RuntimeEvidenceTransactionCore tx = new RuntimeEvidenceTransactionCore(root, "run-a");
            tx.stageText("runtime_ba_window.json", "{\"ready\":true}");
            tx.stageText("runtime_telemetry.json", "{\"durationMs\":123}");
            assertTrue(RuntimeEvidenceTransactionCore.activeDirectory(root) == null,
                    "pending generation cannot be active");
            RuntimeEvidenceTransactionCore.Result committed = tx.commit("READY");
            File active = RuntimeEvidenceTransactionCore.activeDirectory(root);
            assertTrue(active != null && active.equals(committed.directory),
                    "committed generation must become active");
            assertTrue(new File(active, "generation_manifest.json").isFile(),
                    "generation manifest missing");

            RuntimeEvidenceTransactionCore pending =
                    new RuntimeEvidenceTransactionCore(root, "run-b");
            pending.stageText("runtime_ba_window.json", "unsafe partial");
            pending.rollback();
            assertTrue(RuntimeEvidenceTransactionCore.activeDirectory(root).equals(active),
                    "rollback must preserve previous committed generation");
            assertTrue(!new File(new File(root, "runtime-generations"), "run-b.pending").exists(),
                    "rolled back pending directory remains");
        } finally {
            delete(root);
        }
    }

    private static void testAbortGenerationRemovesUnsafePartialEvidence() throws Exception {
        File root = Files.createTempDirectory("skm-runtime-abort").toFile();
        try {
            RuntimeEvidenceTransactionCore tx = new RuntimeEvidenceTransactionCore(root, "run-abort");
            tx.stageText("runtime_ba_window.json", "unsafe partial window");
            RuntimeEvidenceTransactionCore.Result result =
                    tx.commitAbort("USER_CANCELLED", "FEATURE_MATCH_FORWARD_32");
            File active = RuntimeEvidenceTransactionCore.activeDirectory(root);
            assertTrue(active != null && active.equals(result.directory), "abort generation not active");
            assertTrue(new File(active, "runtime_abort.json").isFile(), "abort evidence missing");
            assertTrue(!new File(active, "runtime_ba_window.json").exists(),
                    "partial BA window survived abort promotion");
        } finally {
            delete(root);
        }
    }

    private static void testCampaignManifestIsDeterministic() {
        String apk = repeat('a', 64);
        CampaignEvidenceManifestCore.Item frame = new CampaignEvidenceManifestCore.Item(
                "FRAME", "frames/frame_0001.jpg", 1024, repeat('b', 64));
        CampaignEvidenceManifestCore.Item runtime = new CampaignEvidenceManifestCore.Item(
                "RUNTIME", "runtime/runtime_audit.json", 300, repeat('c', 64));
        List<CampaignEvidenceManifestCore.Item> first = new ArrayList<CampaignEvidenceManifestCore.Item>();
        first.add(runtime);
        first.add(frame);
        List<CampaignEvidenceManifestCore.Item> second = new ArrayList<CampaignEvidenceManifestCore.Item>(first);
        Collections.reverse(second);

        CampaignEvidenceManifestCore.Result a = CampaignEvidenceManifestCore.build(
                "0.18.0-alpha33", apk, "Samsung A15", 35,
                "session-01", "10415863", "OT-1641", first);
        CampaignEvidenceManifestCore.Result b = CampaignEvidenceManifestCore.build(
                "0.18.0-alpha33", apk, "Samsung A15", 35,
                "session-01", "10415863", "OT-1641", second);
        assertTrue(a.complete(), "complete campaign manifest expected");
        assertTrue(a.fingerprint.equals(b.fingerprint),
                "manifest fingerprint depends on input order");
        assertTrue(a.canonicalJson.equals(b.canonicalJson),
                "canonical manifest depends on input order");

        CampaignEvidenceManifestCore.Result incomplete = CampaignEvidenceManifestCore.build(
                "0.18.0-alpha33", "", "", 0, "session-01", "", "",
                Collections.<CampaignEvidenceManifestCore.Item>emptyList());
        assertTrue(!incomplete.complete(), "missing evidence cannot be complete");
        assertTrue(incomplete.gaps.contains("APK_SHA256_MISSING"), "APK gap missing");
        assertTrue(incomplete.gaps.contains("FRAME_HASHES_MISSING"), "frame gap missing");
    }

    private static byte[] texturedGray(int width, int height) {
        byte[] gray = new byte[width * height];
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                int checker = ((x / 8) + (y / 8)) & 1;
                int value = checker == 0 ? 25 : 225;
                value = Math.max(0, Math.min(255, value + ((x * 17 + y * 31) % 21) - 10));
                gray[y * width + x] = (byte) value;
            }
        }
        return gray;
    }

    private static String repeat(char value, int count) {
        StringBuilder out = new StringBuilder(count);
        for (int i = 0; i < count; i++) out.append(value);
        return out.toString();
    }

    private static void delete(File file) {
        if (file == null || !file.exists()) return;
        if (file.isDirectory()) {
            File[] children = file.listFiles();
            if (children != null) for (File child : children) delete(child);
        }
        file.delete();
    }

    private static void assertTrue(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }
}
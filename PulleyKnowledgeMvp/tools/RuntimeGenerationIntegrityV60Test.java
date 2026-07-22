import cl.skm.pulleyai.RuntimeEvidenceTransactionCore;
import cl.skm.pulleyai.RuntimeGenerationIntegrityCore;

import java.io.File;
import java.io.FileOutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;

public final class RuntimeGenerationIntegrityV60Test {
    public static void main(String[] args) throws Exception {
        testValidCommittedGeneration();
        testTamperedFileIsBlocked();
        testUnlistedFileIsBlocked();
        testMissingFileIsBlocked();
        System.out.println("RuntimeGenerationIntegrityV60Test OK");
    }

    private static void testValidCommittedGeneration() throws Exception {
        File root = Files.createTempDirectory("skm-integrity-valid").toFile();
        try {
            RuntimeEvidenceTransactionCore tx = new RuntimeEvidenceTransactionCore(root, "valid-run");
            tx.stageText("runtime_audit.json", "{\"state\":\"READY\"}");
            tx.stageText("nested/runtime_telemetry.json", "{\"durationMs\":123}");
            RuntimeEvidenceTransactionCore.Result committed = tx.commit("READY");
            RuntimeGenerationIntegrityCore.Result result =
                    RuntimeGenerationIntegrityCore.verify(committed.directory);
            assertTrue(result.valid, "valid generation rejected: " + result.summary());
            assertTrue(result.verifiedFiles == 2, "verified file count");
            assertTrue(result.manifestSha256.matches("[0-9a-f]{64}"), "manifest hash missing");
            assertTrue(result.canonicalJson().contains("\"valid\":true"), "valid JSON missing");
        } finally {
            delete(root);
        }
    }

    private static void testTamperedFileIsBlocked() throws Exception {
        File root = Files.createTempDirectory("skm-integrity-tamper").toFile();
        try {
            RuntimeEvidenceTransactionCore tx = new RuntimeEvidenceTransactionCore(root, "tamper-run");
            tx.stageText("runtime_audit.json", "original");
            RuntimeEvidenceTransactionCore.Result committed = tx.commit("READY");
            write(new File(committed.directory, "runtime_audit.json"), "modified");
            RuntimeGenerationIntegrityCore.Result result =
                    RuntimeGenerationIntegrityCore.verify(committed.directory);
            assertTrue(!result.valid, "tampered generation accepted");
            assertContains(result, "SIZE_MISMATCH:runtime_audit.json");
        } finally {
            delete(root);
        }
    }

    private static void testUnlistedFileIsBlocked() throws Exception {
        File root = Files.createTempDirectory("skm-integrity-extra").toFile();
        try {
            RuntimeEvidenceTransactionCore tx = new RuntimeEvidenceTransactionCore(root, "extra-run");
            tx.stageText("runtime_audit.json", "stable");
            RuntimeEvidenceTransactionCore.Result committed = tx.commit("READY");
            write(new File(committed.directory, "injected.json"), "{}");
            RuntimeGenerationIntegrityCore.Result result =
                    RuntimeGenerationIntegrityCore.verify(committed.directory);
            assertTrue(!result.valid, "unlisted file accepted");
            assertContains(result, "UNLISTED_FILE:injected.json");
        } finally {
            delete(root);
        }
    }

    private static void testMissingFileIsBlocked() throws Exception {
        File root = Files.createTempDirectory("skm-integrity-missing").toFile();
        try {
            RuntimeEvidenceTransactionCore tx = new RuntimeEvidenceTransactionCore(root, "missing-run");
            tx.stageText("runtime_audit.json", "stable");
            RuntimeEvidenceTransactionCore.Result committed = tx.commit("READY");
            assertTrue(new File(committed.directory, "runtime_audit.json").delete(),
                    "test file deletion failed");
            RuntimeGenerationIntegrityCore.Result result =
                    RuntimeGenerationIntegrityCore.verify(committed.directory);
            assertTrue(!result.valid, "missing file accepted");
            assertContains(result, "MISSING_FILE:runtime_audit.json");
        } finally {
            delete(root);
        }
    }

    private static void write(File file, String value) throws Exception {
        try (FileOutputStream output = new FileOutputStream(file)) {
            output.write(value.getBytes(StandardCharsets.UTF_8));
        }
    }

    private static void assertContains(RuntimeGenerationIntegrityCore.Result result,
                                       String expected) {
        for (String issue : result.issues) {
            if (expected.equals(issue)) return;
        }
        throw new AssertionError("missing issue " + expected + " in " + result.issues);
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
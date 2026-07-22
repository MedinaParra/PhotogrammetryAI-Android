package cl.skm.pulleyai;

import java.io.File;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;

public final class GeometryCancellationPublicationJournalV61Test {
    public static void main(String[] args) throws Exception {
        testFundamentalCancellation();
        testEssentialCancellation();
        testSparseCancellation();
        testJournalNormalCommit();
        testJournalRecoversPromotedGeneration();
        testJournalRollsBackMissingGeneration();
        System.out.println("GeometryCancellationPublicationJournalV61Test OK");
    }

    private static void testFundamentalCancellation() {
        RuntimeExecutionControlCore.Token token = RuntimeExecutionControlCore.start(60_000L);
        token.cancel("TEST_FUNDAMENTAL_CANCEL");
        RuntimeCancellationBridge.install(token);
        try {
            FundamentalMatrixCore.estimate(pairs(20), 2.0, 500);
            throw new AssertionError("fundamental cancellation not observed");
        } catch (RuntimeExecutionControlCore.AbortedException error) {
            assertTrue(error.stage.startsWith("FUNDAMENTAL_RANSAC_"), error.stage);
        } finally { RuntimeCancellationBridge.clear(); }
    }

    private static void testEssentialCancellation() {
        RuntimeExecutionControlCore.Token token = RuntimeExecutionControlCore.start(60_000L);
        token.cancel("TEST_ESSENTIAL_CANCEL");
        RuntimeCancellationBridge.install(token);
        try {
            EssentialPoseCore.recover(new double[][]{{0,0,0},{0,0,-1},{0,1,0}},
                    pairs(12), indices(12),
                    new EssentialPoseCore.Intrinsics(800, 805, 320, 240),
                    new EssentialPoseCore.Intrinsics(800, 805, 320, 240));
            throw new AssertionError("essential cancellation not observed");
        } catch (RuntimeExecutionControlCore.AbortedException error) {
            assertTrue(error.stage.startsWith("ESSENTIAL_"), error.stage);
        } finally { RuntimeCancellationBridge.clear(); }
    }

    private static void testSparseCancellation() {
        RuntimeExecutionControlCore.Token token = RuntimeExecutionControlCore.start(60_000L);
        token.cancel("TEST_SPARSE_CANCEL");
        RuntimeCancellationBridge.install(token);
        try {
            EssentialPoseCore.Result pose = new EssentialPoseCore.Result(true, "USABLE", null,
                    new double[][]{{1,0,0},{0,1,0},{0,0,1}}, new double[]{-0.5,0,0},
                    4,4,1.0,1.0,0.0);
            SparseTriangulationCore.triangulate(pairs(8), indices(8),
                    new EssentialPoseCore.Intrinsics(800, 805, 320, 240),
                    new EssentialPoseCore.Intrinsics(800, 805, 320, 240), pose, 3.0);
            throw new AssertionError("sparse cancellation not observed");
        } catch (RuntimeExecutionControlCore.AbortedException error) {
            assertTrue(error.stage.startsWith("SPARSE_TRIANGULATION_"), error.stage);
        } finally { RuntimeCancellationBridge.clear(); }
    }

    private static void testJournalNormalCommit() throws Exception {
        File root = Files.createTempDirectory("skm-journal-normal").toFile();
        FakeJournal journal = new FakeJournal();
        RuntimePublicationJournalBridge.install(journal);
        try {
            RuntimeEvidenceTransactionCore tx = new RuntimeEvidenceTransactionCore(root, "run-ok");
            tx.stageText("runtime_audit.json", "{}");
            tx.commit("READY");
            assertTrue(journal.record != null && journal.record.phase == RuntimePublicationJournalBridge.Phase.COMPLETE,
                    "normal publication not complete");
            assertTrue("run-ok.committed".equals(RuntimeEvidenceTransactionCore.activeGenerationName(root)),
                    "normal active pointer");
        } finally {
            RuntimePublicationJournalBridge.clear();
            delete(root);
        }
    }

    private static void testJournalRecoversPromotedGeneration() throws Exception {
        File root = Files.createTempDirectory("skm-journal-recover").toFile();
        FakeJournal journal = new FakeJournal();
        journal.failFilesCommittedOnce = true;
        RuntimePublicationJournalBridge.install(journal);
        try {
            RuntimeEvidenceTransactionCore tx = new RuntimeEvidenceTransactionCore(root, "run-crash");
            tx.stageText("runtime_audit.json", "{\"ready\":true}");
            try {
                tx.commit("READY");
                throw new AssertionError("injected journal failure missing");
            } catch (IllegalStateException expected) {
                assertTrue(expected.getMessage().contains("INJECTED"), expected.getMessage());
            }
            assertTrue(RuntimeEvidenceTransactionCore.activeDirectory(root) == null,
                    "pointer published despite injected failure");
            RuntimeEvidenceTransactionCore next = new RuntimeEvidenceTransactionCore(root, "run-next");
            next.rollback();
            assertTrue("run-crash.committed".equals(RuntimeEvidenceTransactionCore.activeGenerationName(root)),
                    "recovery did not publish committed generation");
            assertTrue(journal.record.phase == RuntimePublicationJournalBridge.Phase.COMPLETE,
                    "recovered journal not complete");
        } finally {
            RuntimePublicationJournalBridge.clear();
            delete(root);
        }
    }

    private static void testJournalRollsBackMissingGeneration() throws Exception {
        File root = Files.createTempDirectory("skm-journal-rollback").toFile();
        FakeJournal journal = new FakeJournal();
        RuntimePublicationJournalBridge.install(journal);
        try {
            journal.begin(root.getName(), "run-missing", "READY", System.currentTimeMillis());
            File pending = new File(new File(root, "runtime-generations"), "run-missing.pending");
            assertTrue(pending.mkdirs(), "pending fixture");
            RuntimePublicationJournalBridge.RecoveryResult recovery =
                    RuntimePublicationJournalBridge.recover(root);
            assertTrue("ROLLED_BACK".equals(recovery.status), recovery.summary());
            assertTrue(!pending.exists(), "orphan pending directory remains");
            assertTrue(journal.record.phase == RuntimePublicationJournalBridge.Phase.ROLLED_BACK,
                    "rollback phase missing");
        } finally {
            RuntimePublicationJournalBridge.clear();
            delete(root);
        }
    }

    private static List<FundamentalMatrixCore.PointPair> pairs(int count) {
        List<FundamentalMatrixCore.PointPair> out = new ArrayList<FundamentalMatrixCore.PointPair>();
        for (int i = 0; i < count; i++) {
            double x = 40 + (i % 5) * 70;
            double y = 30 + (i / 5) * 55;
            out.add(new FundamentalMatrixCore.PointPair(x, y, x - 12.0, y + 0.3 * (i % 3)));
        }
        return out;
    }

    private static List<Integer> indices(int count) {
        List<Integer> out = new ArrayList<Integer>();
        for (int i = 0; i < count; i++) out.add(i);
        return out;
    }

    private static final class FakeJournal implements RuntimePublicationJournalBridge.Journal {
        RuntimePublicationJournalBridge.Record record;
        boolean failFilesCommittedOnce;

        @Override public void begin(String sessionId, String runId, String requestedState, long nowEpochMs) {
            record = new RuntimePublicationJournalBridge.Record(sessionId, runId, requestedState,
                    RuntimePublicationJournalBridge.Phase.PREPARED, "", "");
        }

        @Override public void update(String sessionId, String runId, String requestedState,
                                     RuntimePublicationJournalBridge.Phase phase,
                                     String generationName, String detail, long nowEpochMs) {
            if (phase == RuntimePublicationJournalBridge.Phase.FILES_COMMITTED && failFilesCommittedOnce) {
                failFilesCommittedOnce = false;
                throw new IllegalStateException("INJECTED_FILES_COMMITTED_FAILURE");
            }
            record = new RuntimePublicationJournalBridge.Record(sessionId, runId, requestedState,
                    phase, generationName, detail);
        }

        @Override public RuntimePublicationJournalBridge.Record latestIncomplete(String sessionId) {
            if (record == null || !record.sessionId.equals(sessionId)
                    || record.phase == RuntimePublicationJournalBridge.Phase.COMPLETE
                    || record.phase == RuntimePublicationJournalBridge.Phase.ROLLED_BACK) return null;
            return record;
        }

        @Override public void closeJournal() {}
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

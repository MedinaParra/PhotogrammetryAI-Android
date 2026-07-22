package cl.skm.pulleyai;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileOutputStream;
import java.io.FileReader;
import java.nio.charset.StandardCharsets;

/** Coordinates a recoverable publication journal with runtime generation files. */
public final class RuntimePublicationJournalBridge {
    private static final String ROOT = "runtime-generations";
    private static final String POINTER = "runtime_active_generation.txt";
    private static volatile Journal journal;

    private RuntimePublicationJournalBridge() {}

    public enum Phase {
        PREPARED,
        FILES_COMMITTED,
        POINTER_PUBLISHED,
        COMPLETE,
        ROLLED_BACK
    }

    public interface Journal {
        void begin(String sessionId, String runId, String requestedState, long nowEpochMs);
        void update(String sessionId, String runId, String requestedState, Phase phase,
                    String generationName, String detail, long nowEpochMs);
        Record latestIncomplete(String sessionId);
        void closeJournal();
    }

    public static final class Record {
        public final String sessionId;
        public final String runId;
        public final String requestedState;
        public final Phase phase;
        public final String generationName;
        public final String detail;

        public Record(String sessionId, String runId, String requestedState, Phase phase,
                      String generationName, String detail) {
            this.sessionId = clean(sessionId);
            this.runId = clean(runId);
            this.requestedState = clean(requestedState);
            this.phase = phase == null ? Phase.PREPARED : phase;
            this.generationName = clean(generationName);
            this.detail = clean(detail);
        }
    }

    public static final class RecoveryResult {
        public final String status;
        public final String runId;
        public final String generationName;

        RecoveryResult(String status, String runId, String generationName) {
            this.status = status;
            this.runId = runId;
            this.generationName = generationName;
        }

        public String summary() {
            return status + (runId.isEmpty() ? "" : " · " + runId)
                    + (generationName.isEmpty() ? "" : " · " + generationName);
        }
    }

    public static synchronized void install(Journal replacement) {
        Journal previous = journal;
        journal = replacement;
        if (previous != null && previous != replacement) {
            try { previous.closeJournal(); } catch (Exception ignored) {}
        }
    }

    public static synchronized void clear() {
        Journal previous = journal;
        journal = null;
        if (previous != null) {
            try { previous.closeJournal(); } catch (Exception ignored) {}
        }
    }

    public static boolean installed() {
        return journal != null;
    }

    public static void begin(File sessionDir, String runId, String requestedState) {
        Journal active = journal;
        if (active == null || sessionDir == null) return;
        active.begin(sessionDir.getName(), runId, requestedState, System.currentTimeMillis());
    }

    public static void mark(File sessionDir, String runId, String requestedState,
                            String phase, String generationName, String detail) {
        Journal active = journal;
        if (active == null || sessionDir == null) return;
        active.update(sessionDir.getName(), runId, requestedState, parsePhase(phase),
                generationName, detail, System.currentTimeMillis());
    }

    public static RecoveryResult recover(File sessionDir) throws Exception {
        Journal active = journal;
        if (active == null || sessionDir == null) return new RecoveryResult("NO_JOURNAL", "", "");
        String sessionId = sessionDir.getName();
        Record record = active.latestIncomplete(sessionId);
        if (record == null) return new RecoveryResult("NO_INCOMPLETE_PUBLICATION", "", "");

        String generationName = validGenerationName(record.generationName)
                ? record.generationName : record.runId + ".committed";
        File generations = new File(sessionDir, ROOT);
        File committed = new File(generations, generationName);
        File manifest = new File(committed, "generation_manifest.json");
        if (committed.isDirectory() && manifest.isFile()) {
            publishPointer(sessionDir, generationName);
            active.update(sessionId, record.runId, record.requestedState,
                    Phase.POINTER_PUBLISHED, generationName, "RECOVERED_POINTER",
                    System.currentTimeMillis());
            active.update(sessionId, record.runId, record.requestedState,
                    Phase.COMPLETE, generationName, "RECOVERED_COMPLETE",
                    System.currentTimeMillis());
            return new RecoveryResult("RECOVERED", record.runId, generationName);
        }

        deleteRecursively(new File(generations, record.runId + ".pending"));
        active.update(sessionId, record.runId, record.requestedState,
                Phase.ROLLED_BACK, "", "NO_COMMITTED_GENERATION",
                System.currentTimeMillis());
        return new RecoveryResult("ROLLED_BACK", record.runId, "");
    }

    private static Phase parsePhase(String value) {
        try { return Phase.valueOf(clean(value)); }
        catch (Exception ignored) { return Phase.PREPARED; }
    }

    private static void publishPointer(File sessionDir, String generationName) throws Exception {
        if (!validGenerationName(generationName)) throw new IllegalArgumentException("invalid generation name");
        File committed = new File(new File(sessionDir, ROOT), generationName);
        if (!committed.isDirectory() || !new File(committed, "generation_manifest.json").isFile()) {
            throw new IllegalStateException("committed generation is incomplete");
        }
        File pointer = new File(sessionDir, POINTER);
        if (generationName.equals(firstLine(pointer))) return;
        File pointerTmp = new File(sessionDir, POINTER + ".tmp");
        writeSynced(pointerTmp, generationName + "\n");
        File backup = new File(sessionDir, POINTER + ".bak");
        if (backup.exists() && !backup.delete()) throw new IllegalStateException("stale pointer backup cannot be removed");
        boolean hadPointer = pointer.exists();
        if (hadPointer && !pointer.renameTo(backup)) {
            throw new IllegalStateException("active generation pointer cannot be backed up");
        }
        if (!pointerTmp.renameTo(pointer)) {
            if (hadPointer) backup.renameTo(pointer);
            throw new IllegalStateException("generation pointer recovery failed");
        }
        if (backup.exists()) backup.delete();
    }

    private static boolean validGenerationName(String value) {
        return value != null && value.endsWith(".committed")
                && !value.contains("/") && !value.contains("\\") && !value.contains("..");
    }

    private static String firstLine(File file) {
        if (file == null || !file.isFile()) return "";
        try (BufferedReader reader = new BufferedReader(new FileReader(file))) {
            String line = reader.readLine();
            return line == null ? "" : line.trim();
        } catch (Exception ignored) {
            return "";
        }
    }

    private static void writeSynced(File target, String content) throws Exception {
        File parent = target.getParentFile();
        if (!parent.isDirectory() && !parent.mkdirs()) throw new IllegalStateException("cannot create journal parent");
        try (FileOutputStream output = new FileOutputStream(target)) {
            output.write(content.getBytes(StandardCharsets.UTF_8));
            output.flush();
            output.getFD().sync();
        }
    }

    private static void deleteRecursively(File file) {
        if (file == null || !file.exists()) return;
        if (file.isDirectory()) {
            File[] children = file.listFiles();
            if (children != null) for (File child : children) deleteRecursively(child);
        }
        file.delete();
    }

    private static String clean(String value) {
        return value == null ? "" : value.trim();
    }
}

package cl.skm.pulleyai;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.FileReader;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

/** Publishes a complete runtime evidence generation by same-filesystem directory and pointer renames. */
public final class RuntimeEvidenceTransactionCore {
    private static final String ROOT = "runtime-generations";
    private static final String POINTER = "runtime_active_generation.txt";

    private final File sessionDir;
    private final File generationsDir;
    private final String runId;
    private final File pendingDir;
    private boolean closed;

    public RuntimeEvidenceTransactionCore(File sessionDir, String requestedRunId) throws Exception {
        if (sessionDir == null) throw new IllegalArgumentException("sessionDir is required");
        this.sessionDir = sessionDir;
        this.runId = safeRunId(requestedRunId);
        this.generationsDir = new File(sessionDir, ROOT);
        if (!generationsDir.isDirectory() && !generationsDir.mkdirs()) {
            throw new IllegalStateException("cannot create runtime generation directory");
        }
        cleanupPending(generationsDir);
        this.pendingDir = new File(generationsDir, runId + ".pending");
        deleteRecursively(pendingDir);
        if (!pendingDir.mkdirs()) throw new IllegalStateException("cannot create pending generation");
    }

    public File pendingDirectory() { return pendingDir; }
    public String runId() { return runId; }

    public File stageText(String relativeName, String content) throws Exception {
        ensureOpen();
        File target = target(relativeName);
        writeSynced(target, content == null ? "" : content);
        return target;
    }

    public File stageCopy(String relativeName, File source) throws Exception {
        ensureOpen();
        if (source == null || !source.isFile()) throw new IllegalArgumentException("source file missing");
        File target = target(relativeName);
        File parent = target.getParentFile();
        if (!parent.isDirectory() && !parent.mkdirs()) throw new IllegalStateException("cannot create staged parent");
        try (BufferedInputStream input = new BufferedInputStream(new FileInputStream(source));
             FileOutputStream file = new FileOutputStream(target);
             BufferedOutputStream output = new BufferedOutputStream(file)) {
            byte[] buffer = new byte[64 * 1024];
            int read;
            while ((read = input.read(buffer)) >= 0) output.write(buffer, 0, read);
            output.flush();
            file.getFD().sync();
        }
        return target;
    }

    public List<Entry> snapshotEntries() throws Exception {
        ensureOpen();
        List<Entry> entries = new ArrayList<Entry>();
        collect(pendingDir, pendingDir, entries);
        Collections.sort(entries, new Comparator<Entry>() {
            @Override public int compare(Entry a, Entry b) { return a.path.compareTo(b.path); }
        });
        return Collections.unmodifiableList(entries);
    }

    public Result commit(String state) throws Exception {
        ensureOpen();
        String normalizedState = clean(state, "COMMITTED");
        List<Entry> entries = snapshotEntries();
        stageText("generation_manifest.json", manifestJson(normalizedState, entries));
        File committed = new File(generationsDir, runId + ".committed");
        deleteRecursively(committed);
        if (!pendingDir.renameTo(committed)) {
            throw new IllegalStateException("generation directory promotion failed");
        }
        File pointerTmp = new File(sessionDir, POINTER + ".tmp");
        writeSynced(pointerTmp, committed.getName() + "\n");
        File pointer = new File(sessionDir, POINTER);
        if (pointer.exists() && !pointer.delete()) {
            throw new IllegalStateException("old generation pointer cannot be removed");
        }
        if (!pointerTmp.renameTo(pointer)) {
            throw new IllegalStateException("generation pointer promotion failed");
        }
        closed = true;
        return new Result(runId, normalizedState, committed, entries.size());
    }

    public Result commitAbort(String reason, String stage) throws Exception {
        ensureOpen();
        deleteChildren(pendingDir);
        stageText("runtime_abort.json", "{\"schema\":\"skm-runtime-abort/2\","
                + "\"reason\":\"" + escape(clean(reason, "ABORTED")) + "\","
                + "\"stage\":\"" + escape(clean(stage, "UNKNOWN")) + "\","
                + "\"fallbackUnoptimized\":true,\"optimizedGeometryAccepted\":false}");
        return commit("ABORTED");
    }

    public void rollback() {
        if (closed) return;
        deleteRecursively(pendingDir);
        closed = true;
    }

    public static File activeDirectory(File sessionDir) {
        if (sessionDir == null) return null;
        File pointer = new File(sessionDir, POINTER);
        if (!pointer.isFile()) return null;
        String name = firstLine(pointer);
        if (name == null || !name.endsWith(".committed") || name.contains("/") || name.contains("\\")) return null;
        File active = new File(new File(sessionDir, ROOT), name);
        return active.isDirectory() && new File(active, "generation_manifest.json").isFile() ? active : null;
    }

    public static String activeGenerationName(File sessionDir) {
        File active = activeDirectory(sessionDir);
        return active == null ? null : active.getName();
    }

    private File target(String relativeName) {
        String safe = relativeName == null ? "" : relativeName.replace('\\', '/');
        if (safe.isEmpty() || safe.startsWith("/") || safe.contains("../") || safe.equals("..")) {
            throw new IllegalArgumentException("unsafe evidence path");
        }
        File target = new File(pendingDir, safe);
        File parent = target.getParentFile();
        if (!parent.isDirectory() && !parent.mkdirs()) throw new IllegalStateException("cannot create evidence parent");
        return target;
    }

    private void ensureOpen() {
        if (closed) throw new IllegalStateException("transaction is closed");
    }

    private static String manifestJson(String state, List<Entry> entries) {
        StringBuilder json = new StringBuilder(1024 + entries.size() * 150);
        json.append("{\n\"schema\":\"skm-runtime-generation/1\",\n\"state\":\"")
                .append(escape(state)).append("\",\n\"files\":[");
        for (int i = 0; i < entries.size(); i++) {
            Entry entry = entries.get(i);
            if (i > 0) json.append(',');
            json.append("{\"path\":\"").append(escape(entry.path))
                    .append("\",\"sizeBytes\":").append(entry.sizeBytes)
                    .append(",\"sha256\":\"").append(entry.sha256).append("\"}");
        }
        return json.append("]\n}").toString();
    }

    private static void collect(File root, File current, List<Entry> output) throws Exception {
        File[] children = current.listFiles();
        if (children == null) return;
        for (File child : children) {
            if (child.isDirectory()) collect(root, child, output);
            else if (child.isFile()) {
                String path = root.toURI().relativize(child.toURI()).getPath();
                output.add(new Entry(path, child.length(), sha256(child)));
            }
        }
    }

    private static String sha256(File file) throws Exception {
        MessageDigest digest = MessageDigest.getInstance("SHA-256");
        try (BufferedInputStream input = new BufferedInputStream(new FileInputStream(file))) {
            byte[] buffer = new byte[64 * 1024];
            int read;
            while ((read = input.read(buffer)) >= 0) digest.update(buffer, 0, read);
        }
        StringBuilder out = new StringBuilder(64);
        for (byte value : digest.digest()) out.append(String.format(java.util.Locale.ROOT, "%02x", value & 0xff));
        return out.toString();
    }

    private static void writeSynced(File target, String content) throws Exception {
        File parent = target.getParentFile();
        if (!parent.isDirectory() && !parent.mkdirs()) throw new IllegalStateException("cannot create parent");
        try (FileOutputStream output = new FileOutputStream(target)) {
            output.write(content.getBytes(StandardCharsets.UTF_8));
            output.flush();
            output.getFD().sync();
        }
    }

    private static String firstLine(File file) {
        try (BufferedReader reader = new BufferedReader(new FileReader(file))) {
            String line = reader.readLine();
            return line == null ? null : line.trim();
        } catch (Exception error) {
            return null;
        }
    }

    private static void cleanupPending(File root) {
        File[] children = root.listFiles();
        if (children == null) return;
        for (File child : children) if (child.getName().endsWith(".pending")) deleteRecursively(child);
    }

    private static void deleteChildren(File directory) {
        File[] children = directory.listFiles();
        if (children == null) return;
        for (File child : children) deleteRecursively(child);
    }

    private static void deleteRecursively(File file) {
        if (file == null || !file.exists()) return;
        if (file.isDirectory()) deleteChildren(file);
        file.delete();
    }

    private static String safeRunId(String value) {
        String safe = clean(value, "runtime-" + System.currentTimeMillis())
                .replaceAll("[^A-Za-z0-9._-]+", "-");
        return safe.length() > 80 ? safe.substring(0, 80) : safe;
    }
    private static String clean(String value, String fallback) {
        return value == null || value.trim().isEmpty() ? fallback : value.trim();
    }
    private static String escape(String value) {
        return value == null ? "" : value.replace("\\", "\\\\").replace("\"", "\\\"")
                .replace("\n", "\\n").replace("\r", "\\r");
    }

    public static final class Entry {
        public final String path;
        public final long sizeBytes;
        public final String sha256;
        Entry(String path, long sizeBytes, String sha256) {
            this.path = path; this.sizeBytes = sizeBytes; this.sha256 = sha256;
        }
    }

    public static final class Result {
        public final String runId, state;
        public final File directory;
        public final int fileCount;
        Result(String runId, String state, File directory, int fileCount) {
            this.runId = runId; this.state = state; this.directory = directory; this.fileCount = fileCount;
        }
        public String summary() {
            return "Generación " + runId + " · " + state + " · " + fileCount + " archivos";
        }
    }
}
package cl.skm.pulleyai;

import java.io.BufferedInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Verifies paths, sizes and SHA-256 values before a committed generation can be exported. */
public final class RuntimeGenerationIntegrityCore {
    private static final String MANIFEST = "generation_manifest.json";
    private static final Pattern ENTRY = Pattern.compile(
            "\\{\\\"path\\\":\\\"((?:\\\\.|[^\\\"])*)\\\","
                    + "\\\"sizeBytes\\\":([0-9]+),"
                    + "\\\"sha256\\\":\\\"([0-9a-fA-F]{64})\\\"\\}");

    private RuntimeGenerationIntegrityCore() {}

    public static Result verify(File generationDirectory) {
        List<String> issues = new ArrayList<String>();
        if (generationDirectory == null || !generationDirectory.isDirectory()) {
            issues.add("GENERATION_DIRECTORY_MISSING");
            return result(false, 0, "", issues);
        }
        File manifest = new File(generationDirectory, MANIFEST);
        if (!manifest.isFile()) {
            issues.add("GENERATION_MANIFEST_MISSING");
            return result(false, 0, "", issues);
        }

        String json;
        try {
            json = readText(manifest);
        } catch (Exception error) {
            issues.add("GENERATION_MANIFEST_UNREADABLE");
            return result(false, 0, "", issues);
        }
        if (!json.contains("\"schema\":\"skm-runtime-generation/1\"")) {
            issues.add("GENERATION_SCHEMA_UNSUPPORTED");
        }

        Set<String> listed = new HashSet<String>();
        int verified = 0;
        Matcher matcher = ENTRY.matcher(json);
        while (matcher.find()) {
            String path = unescape(matcher.group(1));
            long expectedSize;
            try {
                expectedSize = Long.parseLong(matcher.group(2));
            } catch (NumberFormatException error) {
                issues.add("INVALID_SIZE:" + path);
                continue;
            }
            String expectedSha = matcher.group(3).toLowerCase(java.util.Locale.ROOT);
            if (!safe(path) || MANIFEST.equals(path)) {
                issues.add("UNSAFE_PATH:" + path);
                continue;
            }
            if (!listed.add(path)) {
                issues.add("DUPLICATE_PATH:" + path);
                continue;
            }
            File file = new File(generationDirectory, path);
            try {
                String root = generationDirectory.getCanonicalPath() + File.separator;
                String candidate = file.getCanonicalPath();
                if (!candidate.startsWith(root)) {
                    issues.add("PATH_ESCAPE:" + path);
                    continue;
                }
            } catch (Exception error) {
                issues.add("PATH_UNRESOLVED:" + path);
                continue;
            }
            if (!file.isFile()) {
                issues.add("MISSING_FILE:" + path);
                continue;
            }
            if (file.length() != expectedSize) {
                issues.add("SIZE_MISMATCH:" + path);
                continue;
            }
            try {
                String actual = sha256(file);
                if (!expectedSha.equals(actual)) {
                    issues.add("SHA256_MISMATCH:" + path);
                    continue;
                }
            } catch (Exception error) {
                issues.add("HASH_UNREADABLE:" + path);
                continue;
            }
            verified++;
        }

        if (listed.isEmpty()) issues.add("GENERATION_ENTRIES_MISSING");
        Set<String> actual = new HashSet<String>();
        collect(generationDirectory, generationDirectory, actual);
        actual.remove(MANIFEST);
        for (String path : actual) {
            if (!listed.contains(path)) issues.add("UNLISTED_FILE:" + path);
        }
        for (String path : listed) {
            if (!actual.contains(path)) issues.add("LISTED_FILE_NOT_PRESENT:" + path);
        }

        String manifestSha = "";
        try {
            manifestSha = sha256(manifest);
        } catch (Exception error) {
            issues.add("MANIFEST_HASH_UNREADABLE");
        }
        return result(issues.isEmpty(), verified, manifestSha, issues);
    }

    private static Result result(boolean valid, int verifiedFiles,
                                 String manifestSha256, List<String> issues) {
        return new Result(valid, verifiedFiles, manifestSha256,
                Collections.unmodifiableList(new ArrayList<String>(issues)));
    }

    private static boolean safe(String path) {
        if (path == null || path.isEmpty()) return false;
        String normalized = path.replace('\\', '/');
        return !normalized.startsWith("/")
                && !normalized.equals("..")
                && !normalized.contains("../")
                && !normalized.contains("//")
                && normalized.indexOf('\u0000') < 0;
    }

    private static void collect(File root, File current, Set<String> output) {
        File[] children = current.listFiles();
        if (children == null) return;
        for (File child : children) {
            if (child.isDirectory()) collect(root, child, output);
            else if (child.isFile()) {
                String relative = root.toURI().relativize(child.toURI()).getPath();
                output.add(relative);
            }
        }
    }

    private static String readText(File file) throws Exception {
        try (BufferedInputStream input = new BufferedInputStream(new FileInputStream(file));
             ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            byte[] buffer = new byte[16 * 1024];
            int read;
            while ((read = input.read(buffer)) >= 0) output.write(buffer, 0, read);
            return new String(output.toByteArray(), StandardCharsets.UTF_8);
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
        for (byte value : digest.digest()) {
            out.append(String.format(java.util.Locale.ROOT, "%02x", value & 0xff));
        }
        return out.toString();
    }

    private static String unescape(String value) {
        StringBuilder out = new StringBuilder(value.length());
        boolean escaped = false;
        for (int i = 0; i < value.length(); i++) {
            char current = value.charAt(i);
            if (!escaped && current == '\\') {
                escaped = true;
                continue;
            }
            if (escaped) {
                if (current == 'n') out.append('\n');
                else if (current == 'r') out.append('\r');
                else if (current == 't') out.append('\t');
                else out.append(current);
                escaped = false;
            } else {
                out.append(current);
            }
        }
        if (escaped) out.append('\\');
        return out.toString();
    }

    private static String escape(String value) {
        return value == null ? "" : value.replace("\\", "\\\\")
                .replace("\"", "\\\"").replace("\n", "\\n")
                .replace("\r", "\\r");
    }

    public static final class Result {
        public final boolean valid;
        public final int verifiedFiles;
        public final String manifestSha256;
        public final List<String> issues;

        Result(boolean valid, int verifiedFiles, String manifestSha256,
               List<String> issues) {
            this.valid = valid;
            this.verifiedFiles = verifiedFiles;
            this.manifestSha256 = manifestSha256 == null ? "" : manifestSha256;
            this.issues = issues;
        }

        public String summary() {
            return valid
                    ? "VALID · " + verifiedFiles + " archivos verificados"
                    : "INVALID · " + issues;
        }

        public String canonicalJson() {
            StringBuilder json = new StringBuilder(512);
            json.append("{\n\"schema\":\"skm-export-integrity/1\",\n")
                    .append("\"valid\":").append(valid).append(",\n")
                    .append("\"verifiedFiles\":").append(verifiedFiles).append(",\n")
                    .append("\"manifestSha256\":\"")
                    .append(escape(manifestSha256)).append("\",\n")
                    .append("\"issues\":[");
            for (int i = 0; i < issues.size(); i++) {
                if (i > 0) json.append(',');
                json.append('"').append(escape(issues.get(i))).append('"');
            }
            return json.append("]\n}").toString();
        }
    }
}
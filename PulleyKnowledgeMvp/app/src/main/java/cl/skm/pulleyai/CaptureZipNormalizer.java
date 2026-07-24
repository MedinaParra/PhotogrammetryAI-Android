package cl.skm.pulleyai;

import android.content.Context;
import android.graphics.BitmapFactory;
import android.net.Uri;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import java.util.zip.ZipOutputStream;

/** Canonicalizes field ZIP packages and exposes exact per-frame import failures. */
public final class CaptureZipNormalizer {
    private static final long MAX_ZIP_BYTES = 1024L * 1024L * 1024L;
    private static final long MAX_ENTRY_BYTES = 64L * 1024L * 1024L;

    private CaptureZipNormalizer() {}

    public interface Progress { void onProgress(String message); }

    public static Result normalize(Context context, Uri sourceUri, Progress progress) throws Exception {
        if (context == null || sourceUri == null) throw new IllegalArgumentException("ZIP ausente");
        long started = System.currentTimeMillis();
        File root = new File(context.getCacheDir(), "zip_normalized/" + started);
        if (!root.mkdirs() && !root.isDirectory()) {
            throw new IllegalStateException("No se pudo crear carpeta de normalización");
        }
        File source = new File(root, "source.zip");
        notify(progress, "alpha49 · copiando ZIP y calculando huella…");
        copyUri(context, sourceUri, source);
        if (source.length() <= 0 || source.length() > MAX_ZIP_BYTES) {
            throw new IllegalStateException("ZIP vacío o mayor que 1 GiB");
        }
        String sourceSha = sha256(source);
        File normalized = new File(root, "normalized_capture.zip");
        File preflightFile = new File(root, "capture_zip_preflight.json");

        try (ZipFile zip = new ZipFile(source)) {
            ZipEntry manifestEntry = zip.getEntry("manifest.json");
            if (manifestEntry == null) throw new IllegalStateException("Falta manifest.json");
            JSONObject manifest = new JSONObject(readText(zip, manifestEntry, 4L * 1024L * 1024L));
            String schema = manifest.optString("schema", "");
            if (!schema.startsWith("skm-polea-capture/")) {
                throw new IllegalStateException("Esquema no compatible: " + schema);
            }
            JSONArray frames = manifest.optJSONArray("frames");
            if (frames == null || frames.length() == 0) {
                throw new IllegalStateException("Manifiesto sin fotogramas");
            }
            List<FrameSpec> accepted = new ArrayList<FrameSpec>();
            for (int i = 0; i < frames.length(); i++) {
                FrameSpec spec = FrameSpec.from(frames.getJSONObject(i));
                if ("ACCEPTED".equals(spec.quality)) accepted.add(spec);
            }
            Collections.sort(accepted, (a, b) -> Integer.compare(a.sequence, b.sequence));
            CaptureZipFrameResolverCore.Preflight preflight =
                    new CaptureZipFrameResolverCore.Preflight(frames.length(), accepted.size());
            Set<String> names = new HashSet<String>();
            java.util.Enumeration<? extends ZipEntry> entries = zip.entries();
            while (entries.hasMoreElements()) names.add(entries.nextElement().getName());

            Set<String> sourceFrameNames = new HashSet<String>();
            List<ResolvedFrame> resolved = new ArrayList<ResolvedFrame>();
            for (int i = 0; i < accepted.size(); i++) {
                FrameSpec spec = accepted.get(i);
                notify(progress, String.format(Locale.ROOT,
                        "alpha49 · preflight foto %d/%d…", i + 1, accepted.size()));
                String entryName = CaptureZipFrameResolverCore.resolveEntryName(names, spec.sequence);
                if (entryName == null) {
                    preflight.fail(spec.sequence, "ENTRY_MISSING");
                    continue;
                }
                ZipEntry entry = zip.getEntry(entryName);
                if (entry == null) {
                    preflight.fail(spec.sequence, "ENTRY_UNRESOLVED");
                    continue;
                }
                preflight.entriesFound++;
                try {
                    validateEntry(entry);
                    byte[] jpeg = readBytes(zip, entry, MAX_ENTRY_BYTES);
                    String actualSha = sha256(jpeg);
                    if (!spec.sha256.isEmpty() && !actualSha.equalsIgnoreCase(spec.sha256)) {
                        preflight.fail(spec.sequence, "SHA256_MISMATCH");
                        continue;
                    }
                    preflight.hashesVerified++;
                    BitmapFactory.Options bounds = new BitmapFactory.Options();
                    bounds.inJustDecodeBounds = true;
                    BitmapFactory.decodeByteArray(jpeg, 0, jpeg.length, bounds);
                    if (bounds.outWidth <= 0 || bounds.outHeight <= 0) {
                        preflight.fail(spec.sequence, "JPEG_BOUNDS_INVALID");
                        continue;
                    }
                    preflight.decodedFrames++;
                    sourceFrameNames.add(entryName);
                    resolved.add(new ResolvedFrame(spec.sequence, entryName, jpeg));
                } catch (Throwable failure) {
                    String reason = failure.getMessage() == null
                            ? failure.getClass().getSimpleName() : failure.getMessage();
                    preflight.fail(spec.sequence, "READ_FAILED:" + reason);
                }
            }
            writeText(preflightFile, preflight.canonicalJson());
            if (resolved.size() < 8) {
                throw new IllegalStateException("ZIP PRECHECK BLOQUEADO · " + preflight.concise());
            }

            notify(progress, "alpha49 · creando ZIP canónico…");
            try (ZipOutputStream output = new ZipOutputStream(
                    new BufferedOutputStream(new FileOutputStream(normalized)))) {
                java.util.Enumeration<? extends ZipEntry> all = zip.entries();
                Set<String> written = new HashSet<String>();
                while (all.hasMoreElements()) {
                    ZipEntry entry = all.nextElement();
                    String name = entry.getName();
                    if (sourceFrameNames.contains(name)) continue;
                    if (name.startsWith("frames/") && (name.endsWith(".jpg") || name.endsWith(".jpeg"))) {
                        continue;
                    }
                    if (!written.add(name)) continue;
                    copyEntry(zip, entry, output, name);
                }
                for (ResolvedFrame frame : resolved) {
                    String canonical = String.format(Locale.ROOT,
                            "frames/frame_%04d.jpg", frame.sequence);
                    if (!written.add(canonical)) continue;
                    output.putNextEntry(new ZipEntry(canonical));
                    output.write(frame.jpeg);
                    output.closeEntry();
                }
                output.putNextEntry(new ZipEntry("alpha49_capture_zip_preflight.json"));
                output.write(preflight.canonicalJson().getBytes(StandardCharsets.UTF_8));
                output.closeEntry();
            }
            String summary = "ZIP CANÓNICO LISTO · " + preflight.concise()
                    + " · SHA origen " + sourceSha.substring(0, 12);
            return new Result(normalized, preflightFile, summary, sourceSha,
                    schema, resolved.size(), preflight);
        }
    }

    private static void copyUri(Context context, Uri uri, File target) throws Exception {
        try (InputStream input = context.getContentResolver().openInputStream(uri);
             OutputStream output = new BufferedOutputStream(new FileOutputStream(target))) {
            if (input == null) throw new IllegalStateException("No se pudo abrir el ZIP");
            copy(input, output, MAX_ZIP_BYTES);
        }
    }

    private static byte[] readBytes(ZipFile zip, ZipEntry entry, long limit) throws Exception {
        validateEntry(entry);
        try (InputStream input = new BufferedInputStream(zip.getInputStream(entry));
             ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            copy(input, output, limit);
            return output.toByteArray();
        }
    }

    private static String readText(ZipFile zip, ZipEntry entry, long limit) throws Exception {
        return new String(readBytes(zip, entry, limit), StandardCharsets.UTF_8);
    }

    private static void copyEntry(ZipFile source, ZipEntry entry,
                                  ZipOutputStream target, String name) throws Exception {
        validateEntry(entry);
        target.putNextEntry(new ZipEntry(name));
        try (InputStream input = new BufferedInputStream(source.getInputStream(entry))) {
            copy(input, target, MAX_ENTRY_BYTES);
        }
        target.closeEntry();
    }

    private static void copy(InputStream input, OutputStream output, long limit) throws Exception {
        byte[] buffer = new byte[64 * 1024];
        long total = 0;
        int read;
        while ((read = input.read(buffer)) >= 0) {
            total += read;
            if (total > limit) throw new IllegalStateException("Entrada excede límite de seguridad");
            output.write(buffer, 0, read);
        }
    }

    private static void validateEntry(ZipEntry entry) {
        String name = entry.getName();
        if (name == null || name.startsWith("/") || name.contains("../")
                || name.contains("\\..\\")) {
            throw new IllegalStateException("Ruta ZIP no segura");
        }
        if (entry.getSize() > MAX_ENTRY_BYTES) {
            throw new IllegalStateException("Entrada ZIP demasiado grande: " + name);
        }
    }

    private static void writeText(File file, String text) throws Exception {
        try (OutputStream output = new BufferedOutputStream(new FileOutputStream(file))) {
            output.write(text.getBytes(StandardCharsets.UTF_8));
        }
    }

    private static String sha256(File file) throws Exception {
        MessageDigest digest = MessageDigest.getInstance("SHA-256");
        try (InputStream input = new BufferedInputStream(new FileInputStream(file))) {
            byte[] buffer = new byte[64 * 1024];
            int read;
            while ((read = input.read(buffer)) >= 0) digest.update(buffer, 0, read);
        }
        return hex(digest.digest());
    }

    private static String sha256(byte[] bytes) throws Exception {
        return hex(MessageDigest.getInstance("SHA-256").digest(bytes));
    }

    private static String hex(byte[] bytes) {
        StringBuilder value = new StringBuilder(bytes.length * 2);
        for (byte b : bytes) value.append(String.format(Locale.ROOT, "%02x", b & 0xff));
        return value.toString();
    }

    private static void notify(Progress progress, String message) {
        if (progress != null) progress.onProgress(message);
    }

    private static final class FrameSpec {
        final int sequence;
        final String quality;
        final String sha256;

        FrameSpec(int sequence, String quality, String sha256) {
            this.sequence = sequence;
            this.quality = quality == null ? "UNKNOWN" : quality;
            this.sha256 = sha256 == null ? "" : sha256;
        }

        static FrameSpec from(JSONObject json) {
            return new FrameSpec(json.optInt("sequence", -1),
                    json.optString("quality", "UNKNOWN"),
                    json.optString("sha256", ""));
        }
    }

    private static final class ResolvedFrame {
        final int sequence;
        final String sourceName;
        final byte[] jpeg;

        ResolvedFrame(int sequence, String sourceName, byte[] jpeg) {
            this.sequence = sequence;
            this.sourceName = sourceName;
            this.jpeg = jpeg;
        }
    }

    public static final class Result {
        public final File normalizedZip;
        public final File preflightFile;
        public final String summary;
        public final String sourceSha256;
        public final String sourceSchema;
        public final int usableFrames;
        public final CaptureZipFrameResolverCore.Preflight preflight;

        Result(File normalizedZip, File preflightFile, String summary,
               String sourceSha256, String sourceSchema, int usableFrames,
               CaptureZipFrameResolverCore.Preflight preflight) {
            this.normalizedZip = normalizedZip;
            this.preflightFile = preflightFile;
            this.summary = summary;
            this.sourceSha256 = sourceSha256;
            this.sourceSchema = sourceSchema;
            this.usableFrames = usableFrames;
            this.preflight = preflight;
        }
    }
}

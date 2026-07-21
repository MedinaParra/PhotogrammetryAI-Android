package cl.skm.pulleyai;

import android.content.Context;
import android.os.Build;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

/** Creates a portable package using only the active committed runtime evidence generation. */
public final class SessionPackageExporter {
    private SessionPackageExporter() {}

    public static File build(Context context, CaptureStore store, String sessionId) throws Exception {
        CaptureStore.Session session = store.getSession(sessionId);
        if (session == null) throw new IllegalStateException("Sesión inexistente");
        List<CaptureStore.Frame> frames = store.frames(sessionId);
        File exportDir = new File(context.getCacheDir(), "session_exports");
        if (!exportDir.exists() && !exportDir.mkdirs() && !exportDir.isDirectory()) {
            throw new IllegalStateException("No se pudo crear la carpeta temporal de exportación");
        }
        String safe = safeName(session.label);
        File output = new File(exportDir, safe + "_" + session.id.substring(0, 8) + ".zip");
        File sessionDir = store.sessionDir(sessionId);
        File activeRuntime = RuntimeEvidenceTransactionCore.activeDirectory(sessionDir);
        String activeGeneration = activeRuntime == null ? null : activeRuntime.getName();
        try (ZipOutputStream zip = new ZipOutputStream(
                new BufferedOutputStream(new FileOutputStream(output)))) {
            putText(zip, "manifest.json", manifest(session, frames, activeGeneration));
            if (activeRuntime != null) {
                copyDirectory(zip, activeRuntime, activeRuntime, "runtime/");
                copyIfExists(zip, new File(activeRuntime, "overlap_report.json"),
                        "overlap_report.json");
            } else {
                copyLegacyRuntime(zip, sessionDir);
            }
            for (CaptureStore.Frame frame : frames) {
                File source = new File(frame.filePath);
                if (!source.isFile()) continue;
                copyEntry(zip, source,
                        String.format(Locale.ROOT, "frames/frame_%04d.jpg", frame.sequence));
            }
        }
        return output;
    }

    private static void copyLegacyRuntime(ZipOutputStream zip, File sessionDir) throws Exception {
        copyIfExists(zip, new File(sessionDir, "overlap_report.json"), "overlap_report.json");
        copyIfExists(zip, new File(sessionDir, "runtime_frame_cache.json"),
                "runtime/runtime_frame_cache.json");
        copyIfExists(zip, new File(sessionDir, "runtime_supplemental_metrics.json"),
                "runtime/runtime_supplemental_metrics.json");
        copyIfExists(zip, new File(sessionDir, "runtime_ba_window.json"),
                "runtime/runtime_ba_window.json");
        copyIfExists(zip, new File(sessionDir, "runtime_telemetry.json"),
                "runtime/runtime_telemetry.json");
        copyIfExists(zip, new File(sessionDir, "runtime_device_diagnostics.json"),
                "runtime/runtime_device_diagnostics.json");
        copyIfExists(zip, new File(sessionDir, "runtime_abort.json"),
                "runtime/runtime_abort.json");
        copyIfExists(zip, new File(sessionDir, "runtime_audit.json"),
                "runtime/runtime_audit.json");
    }

    private static void copyDirectory(ZipOutputStream zip, File root, File current,
                                      String prefix) throws Exception {
        File[] children = current.listFiles();
        if (children == null) return;
        Arrays.sort(children, new Comparator<File>() {
            @Override public int compare(File a, File b) {
                return a.getName().compareTo(b.getName());
            }
        });
        for (File child : children) {
            if (child.isDirectory()) {
                copyDirectory(zip, root, child, prefix);
            } else if (child.isFile()) {
                String relative = root.toURI().relativize(child.toURI()).getPath();
                copyEntry(zip, child, prefix + relative);
            }
        }
    }

    private static void copyIfExists(ZipOutputStream zip, File source, String name) throws Exception {
        if (source.isFile()) copyEntry(zip, source, name);
    }

    private static void copyEntry(ZipOutputStream zip, File source, String name) throws Exception {
        zip.putNextEntry(new ZipEntry(name));
        byte[] buffer = new byte[64 * 1024];
        try (BufferedInputStream input = new BufferedInputStream(new FileInputStream(source))) {
            int read;
            while ((read = input.read(buffer)) >= 0) zip.write(buffer, 0, read);
        }
        zip.closeEntry();
    }

    private static String manifest(CaptureStore.Session session, List<CaptureStore.Frame> frames,
                                   String activeGeneration) {
        long freeBytes = frames.isEmpty() ? 1024L * 1024L * 1024L
                : new File(frames.get(0).filePath).getUsableSpace();
        CaptureReadiness.Result readiness = CaptureReadiness.evaluate(
                session.accepted, session.rejected, session.lowMask, session.highMask,
                session.shellLengthMm, freeBytes);
        StringBuilder json = new StringBuilder(4096 + frames.size() * 500);
        json.append("{\n");
        field(json, "schema", "skm-polea-capture/5", true);
        field(json, "sessionId", session.id, true);
        field(json, "label", session.label, true);
        field(json, "status", session.status, true);
        field(json, "materialCode", session.code, true);
        field(json, "ot", session.ot, true);
        numberOrNull(json, "shellLengthMm", session.shellLengthMm, true);
        field(json, "orientationMode", "sensorLandscape", true);
        field(json, "deviceManufacturer", Build.MANUFACTURER, true);
        field(json, "deviceModel", Build.MODEL, true);
        number(json, "sdkInt", Build.VERSION.SDK_INT, true);
        number(json, "accepted", session.accepted, true);
        number(json, "rejected", session.rejected, true);
        field(json, "reconstructionReady", Boolean.toString(readiness.ready()), true, false);
        field(json, "runtimeEvidenceDirectory", "runtime/", true);
        field(json, "runtimeActiveGeneration", activeGeneration, true);
        field(json, "runtimeEvidenceMode", activeGeneration == null ? "LEGACY" : "COMMITTED_GENERATION", true);
        field(json, "runtimeBudgetMs", "180000", true, false);
        json.append("  \"readinessSummary\": \"").append(escape(readiness.summary())).append("\",\n");
        json.append("  \"frames\": [\n");
        for (int i = 0; i < frames.size(); i++) {
            CaptureStore.Frame frame = frames.get(i);
            json.append("    {");
            inlineNumber(json, "sequence", frame.sequence, true);
            inlineNumber(json, "createdAt", frame.createdAt, true);
            inlineNumber(json, "yaw", frame.yaw, true);
            inlineNumber(json, "pitch", frame.pitch, true);
            inlineNumber(json, "roll", frame.roll, true);
            inlineString(json, "band", frame.band, true);
            inlineNumber(json, "sector", frame.sector, true);
            inlineString(json, "quality", frame.quality, true);
            inlineString(json, "reason", frame.reason, true);
            inlineNumber(json, "blur", frame.blur, true);
            inlineNumber(json, "luma", frame.luma, true);
            inlineNumber(json, "motion", frame.motion, true);
            inlineString(json, "sha256", frame.sha256, true);
            inlineString(json, "cameraId", frame.cameraId, true);
            inlineNullableNumber(json, "sensorOrientation", frame.sensorOrientation, true);
            inlineNullableNumber(json, "jpegOrientation", frame.jpegOrientation, true);
            inlineNullableNumber(json, "focalLengthMm", frame.focalLengthMm, false);
            json.append("}");
            if (i + 1 < frames.size()) json.append(',');
            json.append('\n');
        }
        json.append("  ]\n}");
        return json.toString();
    }

    private static void putText(ZipOutputStream zip, String name, String text) throws Exception {
        zip.putNextEntry(new ZipEntry(name));
        zip.write(text.getBytes(StandardCharsets.UTF_8));
        zip.closeEntry();
    }
    private static void field(StringBuilder json, String key, String value, boolean comma) {
        field(json, key, value, comma, true);
    }
    private static void field(StringBuilder json, String key, String value,
                              boolean comma, boolean quoted) {
        json.append("  \"").append(key).append("\": ");
        if (value == null || value.isEmpty()) json.append("null");
        else if (quoted) json.append('"').append(escape(value)).append('"');
        else json.append(value);
        if (comma) json.append(',');
        json.append('\n');
    }
    private static void number(StringBuilder json, String key, Number value, boolean comma) {
        json.append("  \"").append(key).append("\": ").append(value);
        if (comma) json.append(',');
        json.append('\n');
    }
    private static void numberOrNull(StringBuilder json, String key, Number value, boolean comma) {
        json.append("  \"").append(key).append("\": ")
                .append(value == null ? "null" : value.toString());
        if (comma) json.append(',');
        json.append('\n');
    }
    private static void inlineString(StringBuilder json, String key, String value, boolean comma) {
        json.append('"').append(key).append("\":");
        if (value == null) json.append("null");
        else json.append('"').append(escape(value)).append('"');
        if (comma) json.append(',');
    }
    private static void inlineNumber(StringBuilder json, String key, Number value, boolean comma) {
        json.append('"').append(key).append("\":").append(value);
        if (comma) json.append(',');
    }
    private static void inlineNullableNumber(StringBuilder json, String key,
                                             Number value, boolean comma) {
        json.append('"').append(key).append("\":")
                .append(value == null ? "null" : value.toString());
        if (comma) json.append(',');
    }
    private static String escape(String value) {
        if (value == null) return "";
        return value.replace("\\", "\\\\").replace("\"", "\\\"")
                .replace("\n", "\\n").replace("\r", "\\r").replace("\t", "\\t");
    }
    private static String safeName(String raw) {
        String safe = raw == null ? "captura_polea"
                : raw.trim().replaceAll("[^A-Za-z0-9._-]+", "_");
        return safe.isEmpty() ? "captura_polea" : safe;
    }
}
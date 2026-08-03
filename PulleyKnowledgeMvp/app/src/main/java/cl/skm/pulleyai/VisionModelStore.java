package cl.skm.pulleyai;

import android.content.Context;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

/** Installs signed-by-hash custom vision model packages without replacing a valid package on failure. */
public final class VisionModelStore {
    public static final String SCHEMA = "skm-pulley-vision-models/1";
    public static final String MANIFEST_FILE = "vision_manifest.json";
    public static final String YOLO_FILE = "pulley_yolo11n_seg_int8.tflite";
    public static final String CLASSIFIER_FILE = "capture_quality_mobilenetv3_small_int8.tflite";
    private static final long MAX_PACKAGE_BYTES = 128L * 1024L * 1024L;
    private static final long MAX_ENTRY_BYTES = 96L * 1024L * 1024L;

    private final File root;
    private final File current;

    public VisionModelStore(Context context) {
        root = new File(context.getApplicationContext().getFilesDir(), "vision_models");
        current = new File(root, "current");
    }

    public synchronized Status status() {
        if (!current.isDirectory()) {
            return Status.missing("Paquete IA no instalado");
        }
        try {
            return validateDirectory(current);
        } catch (Exception error) {
            return Status.invalid(message(error));
        }
    }

    public synchronized Status install(InputStream source) throws Exception {
        if (source == null) throw new IllegalArgumentException("ZIP de modelos ausente");
        if (!root.exists() && !root.mkdirs() && !root.isDirectory()) {
            throw new IllegalStateException("No se pudo crear el almacén de modelos");
        }
        File staging = new File(root, "staging-" + System.currentTimeMillis());
        File backup = new File(root, "backup-" + System.currentTimeMillis());
        if (!staging.mkdirs() && !staging.isDirectory()) {
            throw new IllegalStateException("No se pudo crear staging de modelos");
        }
        try {
            extractPackage(source, staging);
            Status validated = validateDirectory(staging);
            if (!validated.ready) throw new IllegalStateException(validated.message);
            if (current.exists() && !current.renameTo(backup)) {
                throw new IllegalStateException("No se pudo proteger el paquete IA anterior");
            }
            if (!staging.renameTo(current)) {
                if (backup.exists()) backup.renameTo(current);
                throw new IllegalStateException("No se pudo activar el nuevo paquete IA");
            }
            deleteRecursively(backup);
            return validateDirectory(current);
        } catch (Exception error) {
            deleteRecursively(staging);
            if (!current.exists() && backup.exists()) backup.renameTo(current);
            throw error;
        }
    }

    public File yoloModel() { return new File(current, YOLO_FILE); }
    public File classifierModel() { return new File(current, CLASSIFIER_FILE); }
    public File manifestFile() { return new File(current, MANIFEST_FILE); }

    private void extractPackage(InputStream source, File staging) throws Exception {
        Set<String> accepted = new HashSet<String>();
        accepted.add(MANIFEST_FILE);
        accepted.add(YOLO_FILE);
        accepted.add(CLASSIFIER_FILE);
        Set<String> found = new HashSet<String>();
        long total = 0L;
        try (ZipInputStream zip = new ZipInputStream(new BufferedInputStream(source))) {
            ZipEntry entry;
            while ((entry = zip.getNextEntry()) != null) {
                if (entry.isDirectory()) { zip.closeEntry(); continue; }
                String name = entry.getName();
                if (name == null || name.contains("/") || name.contains("\\")
                        || name.contains("..") || !accepted.contains(name)) {
                    throw new IllegalStateException("Entrada no permitida en paquete IA: " + name);
                }
                if (!found.add(name)) throw new IllegalStateException("Entrada duplicada: " + name);
                File output = new File(staging, name);
                long written = 0L;
                try (BufferedOutputStream sink = new BufferedOutputStream(new FileOutputStream(output))) {
                    byte[] buffer = new byte[64 * 1024];
                    int read;
                    while ((read = zip.read(buffer)) >= 0) {
                        written += read;
                        total += read;
                        if (written > MAX_ENTRY_BYTES || total > MAX_PACKAGE_BYTES) {
                            throw new IllegalStateException("Paquete IA supera el tamaño permitido");
                        }
                        sink.write(buffer, 0, read);
                    }
                }
                zip.closeEntry();
            }
        }
        if (!found.equals(accepted)) {
            Set<String> missing = new HashSet<String>(accepted);
            missing.removeAll(found);
            throw new IllegalStateException("Paquete IA incompleto; faltan " + missing);
        }
    }

    private Status validateDirectory(File directory) throws Exception {
        File manifest = new File(directory, MANIFEST_FILE);
        File yolo = new File(directory, YOLO_FILE);
        File classifier = new File(directory, CLASSIFIER_FILE);
        if (!manifest.isFile() || !yolo.isFile() || !classifier.isFile()) {
            return Status.invalid("Faltan archivos obligatorios del paquete IA");
        }
        JSONObject json = new JSONObject(readText(manifest, 2L * 1024L * 1024L));
        if (!SCHEMA.equals(json.optString("schema", ""))) {
            return Status.invalid("Esquema IA incompatible: " + json.optString("schema", ""));
        }
        String packageId = json.optString("packageId", "unnamed");
        JSONObject yoloJson = json.optJSONObject("segmentation");
        JSONObject classifierJson = json.optJSONObject("classification");
        if (yoloJson == null || classifierJson == null) {
            return Status.invalid("Manifiesto sin segmentation/classification");
        }
        List<String> issues = new ArrayList<String>();
        validateModel(yoloJson, YOLO_FILE, 512, 512, VisionTensorContractCore.SEGMENTATION_CLASSES,
                yolo, issues);
        validateModel(classifierJson, CLASSIFIER_FILE, 224, 224,
                VisionTensorContractCore.QUALITY_CLASSES, classifier, issues);
        if (!issues.isEmpty()) return Status.invalid(issues.toString());
        return new Status(true, true, "READY", packageId,
                "YOLO11n-seg INT8 512×512 + MobileNetV3-Small INT8 listos",
                sha256(yolo), sha256(classifier), yolo.length(), classifier.length());
    }

    private static void validateModel(JSONObject json, String expectedFile,
                                      int expectedWidth, int expectedHeight,
                                      List<String> expectedClasses, File file,
                                      List<String> issues) throws Exception {
        if (!expectedFile.equals(json.optString("file", ""))) {
            issues.add("Archivo esperado " + expectedFile);
        }
        if (json.optInt("inputWidth", -1) != expectedWidth
                || json.optInt("inputHeight", -1) != expectedHeight) {
            issues.add(expectedFile + " tamaño de entrada incompatible");
        }
        if (!"INT8".equalsIgnoreCase(json.optString("quantization", ""))) {
            issues.add(expectedFile + " debe declarar cuantización INT8");
        }
        JSONArray classes = json.optJSONArray("classes");
        if (classes == null || classes.length() != expectedClasses.size()) {
            issues.add(expectedFile + " clases incompatibles");
        } else {
            for (int i = 0; i < expectedClasses.size(); i++) {
                if (!expectedClasses.get(i).equals(classes.optString(i, ""))) {
                    issues.add(expectedFile + " clase " + i + " debe ser " + expectedClasses.get(i));
                }
            }
        }
        String declared = json.optString("sha256", "").toLowerCase(Locale.ROOT);
        String actual = sha256(file);
        if (declared.length() != 64 || !declared.equals(actual)) {
            issues.add(expectedFile + " SHA-256 no coincide");
        }
        if (file.length() < 1024L) issues.add(expectedFile + " demasiado pequeño");
    }

    private static String readText(File file, long limit) throws Exception {
        if (file.length() > limit) throw new IllegalStateException("Manifiesto demasiado grande");
        byte[] data = new byte[(int) file.length()];
        int offset = 0;
        try (BufferedInputStream input = new BufferedInputStream(new FileInputStream(file))) {
            while (offset < data.length) {
                int read = input.read(data, offset, data.length - offset);
                if (read < 0) break;
                offset += read;
            }
        }
        return new String(data, 0, offset, java.nio.charset.StandardCharsets.UTF_8);
    }

    private static String sha256(File file) throws Exception {
        MessageDigest digest = MessageDigest.getInstance("SHA-256");
        try (BufferedInputStream input = new BufferedInputStream(new FileInputStream(file))) {
            byte[] buffer = new byte[64 * 1024];
            int read;
            while ((read = input.read(buffer)) >= 0) digest.update(buffer, 0, read);
        }
        StringBuilder hex = new StringBuilder(64);
        for (byte value : digest.digest()) hex.append(String.format(Locale.ROOT, "%02x", value));
        return hex.toString();
    }

    private static void deleteRecursively(File file) {
        if (file == null || !file.exists()) return;
        if (file.isDirectory()) {
            File[] children = file.listFiles();
            if (children != null) for (File child : children) deleteRecursively(child);
        }
        file.delete();
    }

    private static String message(Throwable error) {
        if (error == null) return "Error desconocido";
        return error.getMessage() == null ? error.getClass().getSimpleName() : error.getMessage();
    }

    public static final class Status {
        public final boolean installed;
        public final boolean ready;
        public final String state;
        public final String packageId;
        public final String message;
        public final String yoloSha256;
        public final String classifierSha256;
        public final long yoloBytes;
        public final long classifierBytes;

        Status(boolean installed, boolean ready, String state, String packageId,
               String message, String yoloSha256, String classifierSha256,
               long yoloBytes, long classifierBytes) {
            this.installed = installed;
            this.ready = ready;
            this.state = state;
            this.packageId = packageId;
            this.message = message;
            this.yoloSha256 = yoloSha256;
            this.classifierSha256 = classifierSha256;
            this.yoloBytes = yoloBytes;
            this.classifierBytes = classifierBytes;
        }

        static Status missing(String message) {
            return new Status(false, false, "MISSING", "", message, "", "", 0L, 0L);
        }

        static Status invalid(String message) {
            return new Status(true, false, "INVALID", "", message, "", "", 0L, 0L);
        }

        public String summary() {
            if (!ready) return "IA VISUAL " + state + " · " + message;
            return String.format(Locale.ROOT,
                    "IA VISUAL READY · %s\nYOLO %.1f MiB · MobileNet %.1f MiB\nSHA YOLO %s… · CLAS %s…",
                    packageId, yoloBytes / 1048576.0, classifierBytes / 1048576.0,
                    prefix(yoloSha256), prefix(classifierSha256));
        }

        private static String prefix(String value) {
            return value == null || value.length() < 12 ? value : value.substring(0, 12);
        }
    }
}

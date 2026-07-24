package cl.skm.pulleyai;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Color;
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
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import java.util.zip.ZipOutputStream;

/** Replays primary pairs and publishes only a bounded two-view seed geometry diagnostic. */
public final class ImportedSeedGeometryZipAnalyzer {
    private static final long MAX_ZIP_BYTES = 1024L * 1024L * 1024L;
    private static final long MAX_ENTRY_BYTES = 64L * 1024L * 1024L;
    private static final int MAX_FRAMES = 96;
    private static final int LONG_EDGE = 1024;
    private static final int FEATURES_PER_FRAME = 1200;
    private static final int MAX_PAIRS = 320;

    private ImportedSeedGeometryZipAnalyzer() {}

    public interface Progress { void onProgress(String message); }

    public static Result process(Context context, Uri sourceUri,
                                 ImportedTrackZipAnalyzer.Result tracks,
                                 Progress progress) throws Exception {
        if (context == null || sourceUri == null) throw new IllegalArgumentException("ZIP ausente");
        long started = System.currentTimeMillis();
        File root = new File(context.getCacheDir(), "zip_seed_geometry/" + started);
        if (!root.mkdirs() && !root.isDirectory()) {
            throw new IllegalStateException("No se pudo crear carpeta de geometría semilla");
        }
        File source = new File(root, "source_capture.zip");
        notify(progress, "alpha50 · copiando ZIP canónico para pose semilla…");
        copyUri(context, sourceUri, source);
        if (source.length() <= 0 || source.length() > MAX_ZIP_BYTES) {
            throw new IllegalStateException("ZIP vacío o mayor que 1 GiB");
        }
        String sourceSha = sha256(source);
        try (ZipFile zip = new ZipFile(source)) {
            ZipEntry manifestEntry = zip.getEntry("manifest.json");
            if (manifestEntry == null) throw new IllegalStateException("Falta manifest.json");
            JSONObject manifest = new JSONObject(readText(zip, manifestEntry,
                    4L * 1024L * 1024L));
            String schema = manifest.optString("schema", "");
            if (!schema.startsWith("skm-polea-capture/")) {
                throw new IllegalStateException("Esquema incompatible: " + schema);
            }
            JSONArray frameArray = manifest.optJSONArray("frames");
            if (frameArray == null || frameArray.length() == 0) {
                throw new IllegalStateException("Manifiesto sin fotogramas");
            }
            List<FrameSpec> specs = new ArrayList<FrameSpec>();
            for (int i = 0; i < frameArray.length(); i++) {
                FrameSpec spec = FrameSpec.from(frameArray.getJSONObject(i));
                if ("ACCEPTED".equals(spec.quality)) specs.add(spec);
            }
            Collections.sort(specs, (a, b) -> Integer.compare(a.sequence, b.sequence));
            if (specs.size() > MAX_FRAMES) specs = new ArrayList<FrameSpec>(specs.subList(0, MAX_FRAMES));
            if (specs.size() < 8) throw new IllegalStateException("Menos de 8 fotos aceptadas");

            File frameDir = new File(root, "frames");
            if (!frameDir.mkdirs() && !frameDir.isDirectory()) {
                throw new IllegalStateException("No se pudo crear carpeta temporal de fotos");
            }
            List<PreparedFrame> frames = new ArrayList<PreparedFrame>();
            int failures = 0;
            for (int i = 0; i < specs.size(); i++) {
                FrameSpec spec = specs.get(i);
                notify(progress, String.format(Locale.ROOT,
                        "alpha50 · características %d/%d…", i + 1, specs.size()));
                try {
                    ZipEntry entry = zip.getEntry(String.format(Locale.ROOT,
                            "frames/frame_%04d.jpg", spec.sequence));
                    if (entry == null) { failures++; continue; }
                    validateEntry(entry);
                    File image = new File(frameDir, String.format(Locale.ROOT,
                            "frame_%04d.jpg", spec.sequence));
                    copyEntry(zip, entry, image);
                    String actualSha = sha256(image);
                    if (!spec.sha256.isEmpty() && !actualSha.equalsIgnoreCase(spec.sha256)) {
                        failures++; image.delete(); continue;
                    }
                    Gray gray = decode(image, LONG_EDGE);
                    MultiScaleOrientedFeatureCore.FeatureSet features =
                            MultiScaleOrientedFeatureCore.detect(gray.pixels,
                                    gray.width, gray.height, FEATURES_PER_FRAME);
                    frames.add(new PreparedFrame(spec, gray.width, gray.height, features));
                } catch (Throwable error) {
                    failures++;
                }
            }
            if (frames.size() < 8) {
                throw new IllegalStateException("Menos de 8 fotos decodificadas para pose · fallas " + failures);
            }

            List<PairIndex> pairs = candidates(frames);
            if (pairs.size() > MAX_PAIRS) pairs = new ArrayList<PairIndex>(pairs.subList(0, MAX_PAIRS));
            List<ImportedSeedGeometryCore.Candidate> seedCandidates =
                    new ArrayList<ImportedSeedGeometryCore.Candidate>();
            int primary = 0;
            int weak = 0;
            for (int i = 0; i < pairs.size(); i++) {
                PairIndex pair = pairs.get(i);
                if ((i & 3) == 0) notify(progress, String.format(Locale.ROOT,
                        "alpha50 · buscando par semilla %d/%d…", i + 1, pairs.size()));
                PreparedFrame left = frames.get(pair.left);
                PreparedFrame right = frames.get(pair.right);
                MultiScaleOrientedFeatureCore.MatchResult match =
                        MultiScaleOrientedFeatureCore.match(left.features, right.features);
                List<FundamentalMatrixCore.PointPair> observations =
                        new ArrayList<FundamentalMatrixCore.PointPair>(match.observations.size());
                for (MultiScaleOrientedFeatureCore.Observation observation : match.observations) {
                    observations.add(new FundamentalMatrixCore.PointPair(
                            observation.x, observation.y, observation.u, observation.v));
                }
                FundamentalMatrixCore.Result fundamental = observations.size() >= 8
                        ? FundamentalMatrixCore.estimate(observations, 3.2,
                        Math.max(180, Math.min(720, observations.size() * 4))) : null;
                MultiScaleGeometricDiagnosticsCore.PairDiagnostic diagnostic =
                        MultiScaleGeometricDiagnosticsCore.classify(
                                left.spec.sequence, right.spec.sequence,
                                left.spec.band, right.spec.band,
                                sectorGap(left.spec.sector, right.spec.sector), match,
                                fundamental != null && fundamental.solved,
                                fundamental == null ? 0 : fundamental.inliers.size(),
                                fundamental == null ? 0.0 : fundamental.inlierRatio,
                                fundamental == null ? Double.POSITIVE_INFINITY : fundamental.rmsPx);
                if ("STRONG".equals(diagnostic.status) || "USABLE".equals(diagnostic.status)) {
                    primary++;
                    seedCandidates.add(new ImportedSeedGeometryCore.Candidate(
                            left.spec.sequence, right.spec.sequence,
                            left.width, left.height, left.spec.band, right.spec.band,
                            diagnostic.status, observations, fundamental));
                } else {
                    weak++;
                }
            }
            ImportedSeedGeometryCore.Result geometry = ImportedSeedGeometryCore.solve(seedCandidates);
            File report = new File(root, "imported_seed_geometry.json");
            writeText(report, geometry.canonicalJson());
            File packageFile = new File(root,
                    safeName(manifest.optString("label", "capture"))
                            + "_alpha50_seed_geometry.zip");
            buildPackage(zip, manifestEntry, report, packageFile, tracks,
                    sourceSha, schema, primary, weak, failures);
            long duration = System.currentTimeMillis() - started;
            String summary = geometry.summary()
                    + "\nPares candidatos " + pairs.size()
                    + " · primarios " + primary + " · débiles/puente " + weak
                    + " · fotos " + frames.size() + " · fallas " + failures
                    + "\nDuración pose semilla "
                    + String.format(Locale.ROOT, "%.1f s", duration / 1000.0)
                    + "\nNo existe escala métrica ni reconstrucción global.";
            notify(progress, "alpha50 · geometría semilla completa");
            return new Result(report, packageFile, summary, geometry,
                    frames.size(), pairs.size(), primary, weak, failures, sourceSha);
        }
    }

    private static List<PairIndex> candidates(final List<PreparedFrame> frames) {
        List<PairIndex> output = new ArrayList<PairIndex>();
        Set<Long> seen = new HashSet<Long>();
        for (int left = 0; left < frames.size(); left++) {
            for (int right = left + 1; right < frames.size(); right++) {
                FrameSpec a = frames.get(left).spec;
                FrameSpec b = frames.get(right).spec;
                int gap = sectorGap(a.sector, b.sector);
                boolean orbit = a.band.equals(b.band) ? gap >= 1 && gap <= 2 : gap <= 1;
                boolean sequential = Math.abs(left - right) <= 2;
                double yawGap = Math.abs(wrapDegrees(a.yaw - b.yaw));
                boolean yaw = a.band.equals(b.band)
                        ? yawGap >= 6.0 && yawGap <= 75.0 : yawGap <= 35.0;
                if (!orbit && !sequential && !yaw) continue;
                long key = ((long) left << 32) ^ (right & 0xffffffffL);
                if (seen.add(key)) output.add(new PairIndex(left, right));
            }
        }
        Collections.sort(output, new Comparator<PairIndex>() {
            @Override public int compare(PairIndex a, PairIndex b) {
                int compare = Integer.compare(Math.abs(a.left - a.right),
                        Math.abs(b.left - b.right));
                if (compare != 0) return compare;
                compare = Integer.compare(a.left, b.left);
                return compare != 0 ? compare : Integer.compare(a.right, b.right);
            }
        });
        return output;
    }

    private static void buildPackage(ZipFile sourceZip, ZipEntry manifestEntry,
                                     File report, File output,
                                     ImportedTrackZipAnalyzer.Result tracks,
                                     String sourceSha, String sourceSchema,
                                     int primary, int weak, int failures) throws Exception {
        try (ZipOutputStream zip = new ZipOutputStream(
                new BufferedOutputStream(new FileOutputStream(output)))) {
            copyFile(zip, report, "imported_seed_geometry.json");
            if (tracks != null && tracks.trackFile != null && tracks.trackFile.isFile()) {
                copyFile(zip, tracks.trackFile, "imported_multiview_tracks.json");
            }
            copyZipEntry(sourceZip, manifestEntry, zip, "source_manifest.json");
            String provenance = "{\n"
                    + "\"schema\":\"skm-imported-seed-provenance/1\",\n"
                    + "\"sourceSchema\":\"" + escape(sourceSchema) + "\",\n"
                    + "\"sourceZipSha256\":\"" + sourceSha + "\",\n"
                    + "\"primaryPairs\":" + primary + ",\n"
                    + "\"weakPairsExcluded\":" + weak + ",\n"
                    + "\"frameFailures\":" + failures + ",\n"
                    + "\"metricScale\":false,\n"
                    + "\"globalReconstruction\":false,\n"
                    + "\"industrialRelease\":false\n}";
            putText(zip, "provenance.json", provenance);
        }
    }

    private static Gray decode(File file, int longEdge) {
        BitmapFactory.Options bounds = new BitmapFactory.Options();
        bounds.inJustDecodeBounds = true;
        BitmapFactory.decodeFile(file.getAbsolutePath(), bounds);
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) {
            throw new IllegalStateException("JPEG inválido: " + file.getName());
        }
        int sample = 1;
        while (Math.max(bounds.outWidth, bounds.outHeight) / sample > longEdge) sample *= 2;
        BitmapFactory.Options options = new BitmapFactory.Options();
        options.inSampleSize = Math.max(1, sample);
        options.inPreferredConfig = Bitmap.Config.ARGB_8888;
        Bitmap bitmap = BitmapFactory.decodeFile(file.getAbsolutePath(), options);
        if (bitmap == null) throw new IllegalStateException("No se pudo decodificar " + file.getName());
        try {
            int width = bitmap.getWidth();
            int height = bitmap.getHeight();
            byte[] gray = new byte[width * height];
            int[] row = new int[width];
            for (int y = 0; y < height; y++) {
                bitmap.getPixels(row, 0, width, 0, y, width, 1);
                for (int x = 0; x < width; x++) {
                    int color = row[x];
                    gray[y * width + x] = (byte) Math.round(
                            0.2126 * Color.red(color) + 0.7152 * Color.green(color)
                                    + 0.0722 * Color.blue(color));
                }
            }
            return new Gray(width, height, gray);
        } finally {
            bitmap.recycle();
        }
    }

    private static void copyUri(Context context, Uri uri, File target) throws Exception {
        try (InputStream input = context.getContentResolver().openInputStream(uri);
             OutputStream output = new BufferedOutputStream(new FileOutputStream(target))) {
            if (input == null) throw new IllegalStateException("No se pudo abrir el ZIP");
            copy(input, output, MAX_ZIP_BYTES);
        }
    }

    private static void copyEntry(ZipFile zip, ZipEntry entry, File target) throws Exception {
        try (InputStream input = new BufferedInputStream(zip.getInputStream(entry));
             OutputStream output = new BufferedOutputStream(new FileOutputStream(target))) {
            copy(input, output, MAX_ENTRY_BYTES);
        }
    }

    private static String readText(ZipFile zip, ZipEntry entry, long limit) throws Exception {
        validateEntry(entry);
        try (InputStream input = new BufferedInputStream(zip.getInputStream(entry));
             ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            copy(input, output, limit);
            return new String(output.toByteArray(), StandardCharsets.UTF_8);
        }
    }

    private static void copy(InputStream input, OutputStream output, long limit) throws Exception {
        byte[] buffer = new byte[64 * 1024];
        long total = 0;
        int read;
        while ((read = input.read(buffer)) >= 0) {
            total += read;
            if (total > limit) throw new IllegalStateException("Entrada excede límite");
            output.write(buffer, 0, read);
        }
    }

    private static void validateEntry(ZipEntry entry) {
        String name = entry.getName();
        if (name == null || name.startsWith("/") || name.contains("../")
                || name.contains("\\..\\")) throw new IllegalStateException("Ruta ZIP no segura");
        if (entry.getSize() > MAX_ENTRY_BYTES) {
            throw new IllegalStateException("Entrada demasiado grande: " + name);
        }
    }

    private static void copyZipEntry(ZipFile source, ZipEntry entry,
                                     ZipOutputStream target, String name) throws Exception {
        validateEntry(entry);
        target.putNextEntry(new ZipEntry(name));
        try (InputStream input = new BufferedInputStream(source.getInputStream(entry))) {
            copy(input, target, MAX_ENTRY_BYTES);
        }
        target.closeEntry();
    }

    private static void copyFile(ZipOutputStream zip, File source, String name) throws Exception {
        zip.putNextEntry(new ZipEntry(name));
        try (InputStream input = new BufferedInputStream(new FileInputStream(source))) {
            copy(input, zip, MAX_ENTRY_BYTES);
        }
        zip.closeEntry();
    }

    private static void putText(ZipOutputStream zip, String name, String text) throws Exception {
        zip.putNextEntry(new ZipEntry(name));
        zip.write(text.getBytes(StandardCharsets.UTF_8));
        zip.closeEntry();
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
        StringBuilder value = new StringBuilder(64);
        for (byte b : digest.digest()) value.append(String.format(Locale.ROOT, "%02x", b & 0xff));
        return value.toString();
    }

    private static int sectorGap(int first, int second) {
        int gap = Math.abs(first - second);
        return Math.min(gap, 12 - gap);
    }

    private static double wrapDegrees(double value) {
        double wrapped = value % 360.0;
        if (wrapped > 180.0) wrapped -= 360.0;
        if (wrapped < -180.0) wrapped += 360.0;
        return wrapped;
    }

    private static String safeName(String raw) {
        String safe = raw == null ? "capture" : raw.trim().replaceAll("[^A-Za-z0-9._-]+", "_");
        return safe.isEmpty() ? "capture" : safe;
    }

    private static String escape(String value) {
        if (value == null) return "";
        return value.replace("\\", "\\\\").replace("\"", "\\\"")
                .replace("\n", "\\n").replace("\r", "\\r");
    }

    private static void notify(Progress progress, String message) {
        if (progress != null) progress.onProgress(message);
    }

    private static final class FrameSpec {
        final int sequence, sector;
        final String band, quality, sha256;
        final double yaw;

        FrameSpec(int sequence, int sector, String band, String quality,
                  String sha256, double yaw) {
            this.sequence = sequence;
            this.sector = Math.max(0, Math.min(11, sector));
            this.band = "HIGH".equals(band) ? "HIGH" : "LOW";
            this.quality = quality == null ? "UNKNOWN" : quality;
            this.sha256 = sha256 == null ? "" : sha256;
            this.yaw = yaw;
        }

        static FrameSpec from(JSONObject json) {
            return new FrameSpec(json.optInt("sequence", -1),
                    json.optInt("sector", 0), json.optString("band", "LOW"),
                    json.optString("quality", "UNKNOWN"),
                    json.optString("sha256", ""), json.optDouble("yaw", 0.0));
        }
    }

    private static final class PreparedFrame {
        final FrameSpec spec;
        final int width, height;
        final MultiScaleOrientedFeatureCore.FeatureSet features;

        PreparedFrame(FrameSpec spec, int width, int height,
                      MultiScaleOrientedFeatureCore.FeatureSet features) {
            this.spec = spec;
            this.width = width;
            this.height = height;
            this.features = features;
        }
    }

    private static final class PairIndex {
        final int left, right;
        PairIndex(int left, int right) { this.left = left; this.right = right; }
    }

    private static final class Gray {
        final int width, height;
        final byte[] pixels;
        Gray(int width, int height, byte[] pixels) {
            this.width = width; this.height = height; this.pixels = pixels;
        }
    }

    public static final class Result {
        public final File reportFile, packageFile;
        public final String summary;
        public final ImportedSeedGeometryCore.Result geometry;
        public final int frames, candidatePairs, primaryPairs, weakPairs, frameFailures;
        public final String sourceSha256;

        Result(File reportFile, File packageFile, String summary,
               ImportedSeedGeometryCore.Result geometry,
               int frames, int candidatePairs, int primaryPairs,
               int weakPairs, int frameFailures, String sourceSha256) {
            this.reportFile = reportFile;
            this.packageFile = packageFile;
            this.summary = summary;
            this.geometry = geometry;
            this.frames = frames;
            this.candidatePairs = candidatePairs;
            this.primaryPairs = primaryPairs;
            this.weakPairs = weakPairs;
            this.frameFailures = frameFailures;
            this.sourceSha256 = sourceSha256;
        }
    }
}

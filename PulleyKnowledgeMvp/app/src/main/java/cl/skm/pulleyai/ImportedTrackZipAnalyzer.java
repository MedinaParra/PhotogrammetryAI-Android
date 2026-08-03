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

/** Replays primary multiscale pairs and persists collision-safe tracks from fundamental inliers. */
public final class ImportedTrackZipAnalyzer {
    private static final long MAX_ZIP_BYTES = 1024L * 1024L * 1024L;
    private static final long MAX_ENTRY_BYTES = 64L * 1024L * 1024L;
    private static final int MAX_FRAMES = 96;
    private static final int LONG_EDGE = 1024;
    private static final int FEATURES_PER_FRAME = 1100;
    private static final int MAX_PAIRS = 320;

    private ImportedTrackZipAnalyzer() {}

    public interface Progress {
        void onProgress(String message);
    }

    public static Result process(Context context, Uri sourceUri,
                                 MultiScaleZipReprocessor.Result alpha47,
                                 Progress progress) throws Exception {
        if (context == null || sourceUri == null) {
            throw new IllegalArgumentException("ZIP ausente para tracks");
        }
        long started = System.currentTimeMillis();
        File root = new File(context.getCacheDir(), "zip_track_reprocess/" + started);
        if (!root.mkdirs() && !root.isDirectory()) {
            throw new IllegalStateException("No se pudo crear carpeta de tracks");
        }
        File sourceFile = new File(root, "source_capture.zip");
        notify(progress, "alpha48 · copiando ZIP para tracks…");
        copyUri(context, sourceUri, sourceFile);
        if (sourceFile.length() <= 0L || sourceFile.length() > MAX_ZIP_BYTES) {
            throw new IllegalStateException("ZIP vacío o mayor que 1 GiB");
        }
        String sourceSha = sha256(sourceFile);

        try (ZipFile zip = new ZipFile(sourceFile)) {
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
            Collections.sort(specs, new Comparator<FrameSpec>() {
                @Override public int compare(FrameSpec a, FrameSpec b) {
                    return Integer.compare(a.sequence, b.sequence);
                }
            });
            if (specs.size() > MAX_FRAMES) {
                specs = new ArrayList<FrameSpec>(specs.subList(0, MAX_FRAMES));
            }
            if (specs.size() < 8) throw new IllegalStateException("Menos de 8 fotos aceptadas");

            File frameDir = new File(root, "frames");
            if (!frameDir.mkdirs() && !frameDir.isDirectory()) {
                throw new IllegalStateException("No se pudo crear carpeta de fotogramas");
            }
            List<PreparedFrame> frames = new ArrayList<PreparedFrame>();
            int integrityFailures = 0;
            for (int i = 0; i < specs.size(); i++) {
                FrameSpec spec = specs.get(i);
                notify(progress, String.format(Locale.ROOT,
                        "alpha48 · características %d/%d…", i + 1, specs.size()));
                ZipEntry entry = zip.getEntry(String.format(Locale.ROOT,
                        "frames/frame_%04d.jpg", spec.sequence));
                if (entry == null) {
                    integrityFailures++;
                    continue;
                }
                validateEntry(entry);
                File image = new File(frameDir, String.format(Locale.ROOT,
                        "frame_%04d.jpg", spec.sequence));
                copyEntry(zip, entry, image);
                String actualSha = sha256(image);
                if (!spec.sha256.isEmpty() && !actualSha.equalsIgnoreCase(spec.sha256)) {
                    integrityFailures++;
                    image.delete();
                    continue;
                }
                Gray gray = decode(image, LONG_EDGE);
                MultiScaleOrientedFeatureCore.FeatureSet features =
                        MultiScaleOrientedFeatureCore.detect(gray.pixels,
                                gray.width, gray.height, FEATURES_PER_FRAME);
                frames.add(new PreparedFrame(spec, features));
            }
            if (frames.size() < 8) {
                throw new IllegalStateException("Menos de 8 fotografías íntegras para tracks");
            }

            List<PairIndex> candidatePairs = candidates(frames);
            if (candidatePairs.size() > MAX_PAIRS) {
                candidatePairs = new ArrayList<PairIndex>(candidatePairs.subList(0, MAX_PAIRS));
            }
            List<ImportedTrackAssemblerCore.PairEvidence> pairEvidence =
                    new ArrayList<ImportedTrackAssemblerCore.PairEvidence>();
            int strongPairs = 0;
            int usablePairs = 0;
            int bridgePairs = 0;
            int weakPairs = 0;
            int fundamentalFailures = 0;
            for (int i = 0; i < candidatePairs.size(); i++) {
                PairIndex pair = candidatePairs.get(i);
                if ((i & 3) == 0) {
                    notify(progress, String.format(Locale.ROOT,
                            "alpha48 · inliers y tracks %d/%d…", i + 1, candidatePairs.size()));
                }
                PreparedFrame left = frames.get(pair.left);
                PreparedFrame right = frames.get(pair.right);
                MultiScaleOrientedFeatureCore.MatchResult matches =
                        MultiScaleOrientedFeatureCore.match(left.features, right.features);
                List<FundamentalMatrixCore.PointPair> points =
                        new ArrayList<FundamentalMatrixCore.PointPair>(matches.observations.size());
                for (MultiScaleOrientedFeatureCore.Observation observation : matches.observations) {
                    points.add(new FundamentalMatrixCore.PointPair(
                            observation.x, observation.y, observation.u, observation.v));
                }
                FundamentalMatrixCore.Result fundamental = points.size() >= 8
                        ? FundamentalMatrixCore.estimate(points, 3.2,
                        Math.max(180, Math.min(720, points.size() * 4))) : null;
                if (fundamental == null || !fundamental.solved) fundamentalFailures++;
                MultiScaleGeometricDiagnosticsCore.PairDiagnostic diagnostic =
                        MultiScaleGeometricDiagnosticsCore.classify(
                                left.spec.sequence, right.spec.sequence,
                                left.spec.band, right.spec.band,
                                sectorGap(left.spec.sector, right.spec.sector), matches,
                                fundamental != null && fundamental.solved,
                                fundamental == null ? 0 : fundamental.inliers.size(),
                                fundamental == null ? 0.0 : fundamental.inlierRatio,
                                fundamental == null ? Double.POSITIVE_INFINITY : fundamental.rmsPx);
                if ("STRONG".equals(diagnostic.status)) strongPairs++;
                else if ("USABLE".equals(diagnostic.status)) usablePairs++;
                else if ("BRIDGE".equals(diagnostic.status)) bridgePairs++;
                else weakPairs++;

                List<ImportedTrackAssemblerCore.Correspondence> inliers =
                        new ArrayList<ImportedTrackAssemblerCore.Correspondence>();
                if (fundamental != null && fundamental.solved) {
                    for (int index : fundamental.inliers) {
                        if (index < 0 || index >= matches.observations.size()) continue;
                        MultiScaleOrientedFeatureCore.Observation observation =
                                matches.observations.get(index);
                        inliers.add(new ImportedTrackAssemblerCore.Correspondence(
                                observation.leftIndex, observation.rightIndex,
                                observation.x, observation.y,
                                observation.u, observation.v));
                    }
                }
                String pairId = String.format(Locale.ROOT, "%04d-%04d",
                        left.spec.sequence, right.spec.sequence);
                pairEvidence.add(new ImportedTrackAssemblerCore.PairEvidence(
                        pairId, left.spec.sequence, right.spec.sequence,
                        left.spec.band, right.spec.band, diagnostic.status, inliers));
            }

            ImportedTrackAssemblerCore.Result tracks =
                    ImportedTrackAssemblerCore.assemble(pairEvidence, 3);
            File trackFile = new File(root, "imported_multiview_tracks.json");
            writeText(trackFile, tracks.canonicalJson());
            File packageFile = new File(root,
                    safeName(manifest.optString("label", "capture"))
                            + "_alpha48_tracks.zip");
            buildPackage(zip, manifestEntry, trackFile, packageFile,
                    alpha47, sourceSha, schema);
            long durationMs = System.currentTimeMillis() - started;
            String summary = "TRACKS MULTIVISTA PERSISTIDOS"
                    + "\nTracks ≥3 vistas: " + tracks.tracks.size()
                    + " · observaciones " + tracks.observationCount
                    + " · cross-ring " + tracks.crossRingTracks
                    + "\nFotogramas representados: " + tracks.framesRepresented
                    + "/" + frames.size()
                    + " · longitud mediana " + String.format(Locale.ROOT,
                    "%.1f", tracks.medianTrackLength)
                    + " · máxima " + tracks.maximumTrackLength
                    + "\nPares primarios: " + tracks.primaryPairs
                    + " · fuertes " + strongPairs + " · utilizables " + usablePairs
                    + "\nPuentes excluidos: " + tracks.bridgePairsExcluded
                    + " · colisiones rechazadas " + tracks.frameCollisionRejects
                    + " · conflictos coordenados " + tracks.coordinateConflicts
                    + "\nFallas fundamentales: " + fundamentalFailures
                    + " · integridad " + integrityFailures
                    + "\nDuración de pasada tracks: " + String.format(Locale.ROOT,
                    "%.1f s", durationMs / 1000.0)
                    + "\n\nLos tracks son evidencia diagnóstica. No publican pose global,"
                    + " nube métrica, CAD ni liberación industrial.";
            notify(progress, "alpha48 · paquete de tracks completo");
            return new Result(trackFile, packageFile, summary, tracks,
                    frames.size(), candidatePairs.size(), strongPairs,
                    usablePairs, bridgePairs, weakPairs, fundamentalFailures,
                    integrityFailures, sourceSha);
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
                boolean orbit = a.band.equals(b.band)
                        ? gap >= 1 && gap <= 2 : gap <= 1;
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
            @Override public int compare(PairIndex first, PairIndex second) {
                int firstGap = Math.abs(first.left - first.right);
                int secondGap = Math.abs(second.left - second.right);
                int compare = Integer.compare(firstGap, secondGap);
                if (compare != 0) return compare;
                PreparedFrame fa = frames.get(first.left);
                PreparedFrame fb = frames.get(first.right);
                PreparedFrame sa = frames.get(second.left);
                PreparedFrame sb = frames.get(second.right);
                compare = Integer.compare(sectorGap(fa.spec.sector, fb.spec.sector),
                        sectorGap(sa.spec.sector, sb.spec.sector));
                if (compare != 0) return compare;
                compare = Integer.compare(first.left, second.left);
                return compare != 0 ? compare : Integer.compare(first.right, second.right);
            }
        });
        return output;
    }

    private static void buildPackage(ZipFile sourceZip, ZipEntry manifestEntry,
                                     File trackFile, File output,
                                     MultiScaleZipReprocessor.Result alpha47,
                                     String sourceSha, String sourceSchema) throws Exception {
        try (ZipOutputStream zip = new ZipOutputStream(
                new BufferedOutputStream(new FileOutputStream(output)))) {
            copyFile(zip, trackFile, "imported_multiview_tracks.json");
            if (alpha47 != null && alpha47.reportFile != null
                    && alpha47.reportFile.isFile()) {
                copyFile(zip, alpha47.reportFile,
                        "zip_multiscale_reprocess_diagnostics.json");
            }
            copyZipEntry(sourceZip, manifestEntry, zip, "source_manifest.json");
            copyIfExists(sourceZip, "overlap_report.json", zip,
                    "source_overlap_report.json");
            copyIfExists(sourceZip, "runtime/runtime_supplemental_metrics.json", zip,
                    "source_runtime_supplemental_metrics.json");
            String provenance = "{\n"
                    + "\"schema\":\"skm-imported-track-provenance/1\",\n"
                    + "\"sourceSchema\":\"" + escape(sourceSchema) + "\",\n"
                    + "\"sourceZipSha256\":\"" + sourceSha + "\",\n"
                    + "\"trackSource\":\"PRIMARY_FUNDAMENTAL_INLIERS_ONLY\",\n"
                    + "\"bridgeEvidenceUsedForGeometry\":false,\n"
                    + "\"containsSourceImages\":false,\n"
                    + "\"metricGeometryPublished\":false,\n"
                    + "\"industrialRelease\":false\n}";
            putText(zip, "track_provenance.json", provenance);
        }
    }

    private static void copyIfExists(ZipFile source, String sourceName,
                                     ZipOutputStream target, String targetName) throws Exception {
        ZipEntry entry = source.getEntry(sourceName);
        if (entry != null) copyZipEntry(source, entry, target, targetName);
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

    private static void copyFile(ZipOutputStream zip, File source,
                                 String name) throws Exception {
        zip.putNextEntry(new ZipEntry(name));
        try (InputStream input = new BufferedInputStream(new FileInputStream(source))) {
            copy(input, zip, MAX_ENTRY_BYTES);
        }
        zip.closeEntry();
    }

    private static void putText(ZipOutputStream zip, String name,
                                String text) throws Exception {
        zip.putNextEntry(new ZipEntry(name));
        zip.write(text.getBytes(StandardCharsets.UTF_8));
        zip.closeEntry();
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

    private static void copy(InputStream input, OutputStream output, long limit) throws Exception {
        byte[] buffer = new byte[64 * 1024];
        long total = 0L;
        int read;
        while ((read = input.read(buffer)) >= 0) {
            total += read;
            if (total > limit) throw new IllegalStateException("Entrada excede límite");
            output.write(buffer, 0, read);
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

    private static void validateEntry(ZipEntry entry) {
        String name = entry.getName();
        if (name == null || name.startsWith("/")
                || name.contains("../") || name.contains("\\..\\")) {
            throw new IllegalStateException("Ruta ZIP no segura");
        }
        if (entry.getSize() > MAX_ENTRY_BYTES) {
            throw new IllegalStateException("Entrada demasiado grande: " + name);
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
        Bitmap scaled = bitmap;
        try {
            int width = bitmap.getWidth();
            int height = bitmap.getHeight();
            double scale = Math.min(1.0, longEdge / (double) Math.max(width, height));
            if (scale < 0.999) {
                int targetWidth = Math.max(1, (int) Math.round(width * scale));
                int targetHeight = Math.max(1, (int) Math.round(height * scale));
                scaled = Bitmap.createScaledBitmap(bitmap, targetWidth, targetHeight, true);
                width = targetWidth;
                height = targetHeight;
            }
            byte[] gray = new byte[width * height];
            int[] row = new int[width];
            for (int y = 0; y < height; y++) {
                scaled.getPixels(row, 0, width, 0, y, width, 1);
                for (int x = 0; x < width; x++) {
                    int color = row[x];
                    gray[y * width + x] = (byte) Math.round(
                            0.2126 * Color.red(color)
                                    + 0.7152 * Color.green(color)
                                    + 0.0722 * Color.blue(color));
                }
            }
            return new Gray(width, height, gray);
        } finally {
            if (scaled != bitmap) scaled.recycle();
            bitmap.recycle();
        }
    }

    private static void writeText(File file, String value) throws Exception {
        try (OutputStream output = new BufferedOutputStream(new FileOutputStream(file))) {
            output.write(value.getBytes(StandardCharsets.UTF_8));
        }
    }

    private static String sha256(File file) throws Exception {
        MessageDigest digest = MessageDigest.getInstance("SHA-256");
        try (InputStream input = new BufferedInputStream(new FileInputStream(file))) {
            byte[] buffer = new byte[64 * 1024];
            int read;
            while ((read = input.read(buffer)) >= 0) digest.update(buffer, 0, read);
        }
        StringBuilder output = new StringBuilder(64);
        for (byte value : digest.digest()) {
            output.append(String.format(Locale.ROOT, "%02x", value & 0xff));
        }
        return output.toString();
    }

    private static int sectorGap(int first, int second) {
        int gap = Math.abs(first - second);
        return Math.min(gap, 12 - gap);
    }

    private static double wrapDegrees(double value) {
        while (value > 180.0) value -= 360.0;
        while (value < -180.0) value += 360.0;
        return value;
    }

    private static String safeName(String raw) {
        String safe = raw == null ? "capture"
                : raw.trim().replaceAll("[^A-Za-z0-9._-]+", "_");
        return safe.isEmpty() ? "capture" : safe;
    }

    private static String escape(String value) {
        if (value == null) return "";
        return value.replace("\\", "\\\\")
                .replace("\"", "\\\"")
                .replace("\n", "\\n")
                .replace("\r", "\\r");
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
                    json.optInt("sector", 0),
                    json.optString("band", "LOW"),
                    json.optString("quality", "UNKNOWN"),
                    json.optString("sha256", ""),
                    json.optDouble("yaw", 0.0));
        }
    }

    private static final class PreparedFrame {
        final FrameSpec spec;
        final MultiScaleOrientedFeatureCore.FeatureSet features;
        PreparedFrame(FrameSpec spec, MultiScaleOrientedFeatureCore.FeatureSet features) {
            this.spec = spec;
            this.features = features;
        }
    }

    private static final class PairIndex {
        final int left, right;
        PairIndex(int left, int right) {
            this.left = left;
            this.right = right;
        }
    }

    private static final class Gray {
        final int width, height;
        final byte[] pixels;
        Gray(int width, int height, byte[] pixels) {
            this.width = width;
            this.height = height;
            this.pixels = pixels;
        }
    }

    public static final class Result {
        public final File trackFile, packageFile;
        public final String summary;
        public final ImportedTrackAssemblerCore.Result tracks;
        public final int preparedFrames, evaluatedPairs;
        public final int strongPairs, usablePairs, bridgePairs, weakPairs;
        public final int fundamentalFailures, integrityFailures;
        public final String sourceZipSha256;
        Result(File trackFile, File packageFile, String summary,
               ImportedTrackAssemblerCore.Result tracks,
               int preparedFrames, int evaluatedPairs,
               int strongPairs, int usablePairs, int bridgePairs, int weakPairs,
               int fundamentalFailures, int integrityFailures,
               String sourceZipSha256) {
            this.trackFile = trackFile;
            this.packageFile = packageFile;
            this.summary = summary;
            this.tracks = tracks;
            this.preparedFrames = preparedFrames;
            this.evaluatedPairs = evaluatedPairs;
            this.strongPairs = strongPairs;
            this.usablePairs = usablePairs;
            this.bridgePairs = bridgePairs;
            this.weakPairs = weakPairs;
            this.fundamentalFailures = fundamentalFailures;
            this.integrityFailures = integrityFailures;
            this.sourceZipSha256 = sourceZipSha256;
        }
    }
}

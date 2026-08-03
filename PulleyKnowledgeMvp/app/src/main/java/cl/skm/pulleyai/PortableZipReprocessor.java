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

/** Offline importer and diagnostic reprocessor for exported SKM capture ZIP packages. */
public final class PortableZipReprocessor {
    private static final long MAX_ZIP_BYTES = 1024L * 1024L * 1024L;
    private static final long MAX_ENTRY_BYTES = 64L * 1024L * 1024L;
    private static final int MAX_FRAMES = 96;
    private static final int ANALYSIS_LONG_EDGE = 960;
    private static final int FEATURES_PER_FRAME = 700;
    private static final int MAX_PAIR_COUNT = 360;

    private PortableZipReprocessor() {}

    public interface Progress {
        void onProgress(String message);
    }

    public static Result process(Context context, Uri sourceUri, Progress progress) throws Exception {
        if (context == null || sourceUri == null) throw new IllegalArgumentException("ZIP ausente");
        long started = System.currentTimeMillis();
        File root = new File(context.getCacheDir(), "zip_reprocess/" + started);
        if (!root.mkdirs() && !root.isDirectory()) {
            throw new IllegalStateException("No se pudo crear carpeta temporal");
        }
        File localZip = new File(root, "source_capture.zip");
        notify(progress, "Copiando ZIP de captura…");
        copyUri(context, sourceUri, localZip);
        if (localZip.length() <= 0 || localZip.length() > MAX_ZIP_BYTES) {
            throw new IllegalStateException("ZIP vacío o mayor que 1 GiB");
        }
        String sourceZipSha256 = sha256(localZip);

        try (ZipFile zip = new ZipFile(localZip)) {
            ZipEntry manifestEntry = zip.getEntry("manifest.json");
            if (manifestEntry == null) {
                throw new IllegalStateException("El ZIP no contiene manifest.json");
            }
            JSONObject manifest = new JSONObject(readText(zip, manifestEntry,
                    4L * 1024L * 1024L));
            String schema = manifest.optString("schema", "");
            if (!schema.startsWith("skm-polea-capture/")) {
                throw new IllegalStateException("Esquema de captura no compatible: " + schema);
            }
            JSONArray framesJson = manifest.optJSONArray("frames");
            if (framesJson == null || framesJson.length() == 0) {
                throw new IllegalStateException("El manifiesto no contiene fotogramas");
            }

            List<FrameSpec> accepted = new ArrayList<FrameSpec>();
            int rejectedCount = 0;
            for (int i = 0; i < framesJson.length(); i++) {
                JSONObject frame = framesJson.getJSONObject(i);
                FrameSpec spec = FrameSpec.from(frame);
                if ("ACCEPTED".equals(spec.quality)) accepted.add(spec);
                else rejectedCount++;
            }
            Collections.sort(accepted, new Comparator<FrameSpec>() {
                @Override public int compare(FrameSpec a, FrameSpec b) {
                    return Integer.compare(a.sequence, b.sequence);
                }
            });
            if (accepted.isEmpty()) {
                throw new IllegalStateException("No hay fotografías aceptadas en el ZIP");
            }
            if (accepted.size() > MAX_FRAMES) {
                accepted = new ArrayList<FrameSpec>(accepted.subList(0, MAX_FRAMES));
            }

            File frameDir = new File(root, "frames");
            if (!frameDir.mkdirs() && !frameDir.isDirectory()) {
                throw new IllegalStateException("No se pudo crear carpeta de imágenes");
            }
            List<PreparedFrame> prepared = new ArrayList<PreparedFrame>();
            int integrityFailures = 0;
            for (int i = 0; i < accepted.size(); i++) {
                FrameSpec spec = accepted.get(i);
                notify(progress, String.format(Locale.ROOT,
                        "Validando y preparando foto %d/%d…", i + 1, accepted.size()));
                String name = String.format(Locale.ROOT,
                        "frames/frame_%04d.jpg", spec.sequence);
                ZipEntry entry = zip.getEntry(name);
                if (entry == null) {
                    integrityFailures++;
                    continue;
                }
                validateEntry(entry);
                File target = new File(frameDir, String.format(Locale.ROOT,
                        "frame_%04d.jpg", spec.sequence));
                copyEntry(zip, entry, target);
                String actualSha = sha256(target);
                if (spec.sha256 != null && !spec.sha256.isEmpty()
                        && !actualSha.equalsIgnoreCase(spec.sha256)) {
                    integrityFailures++;
                    target.delete();
                    continue;
                }
                Gray gray = decode(target);
                VisualFeatureCore.FeatureSet features = VisualFeatureCore.detect(
                        gray.pixels, gray.width, gray.height, FEATURES_PER_FRAME);
                prepared.add(new PreparedFrame(spec, target, actualSha,
                        gray.width, gray.height, features));
            }
            if (prepared.size() < 8) {
                throw new IllegalStateException("Menos de 8 fotografías íntegras y decodificables");
            }

            List<PairIndex> candidates = candidates(prepared);
            if (candidates.size() > MAX_PAIR_COUNT) {
                candidates = new ArrayList<PairIndex>(candidates.subList(0, MAX_PAIR_COUNT));
            }
            List<AdaptivePairDiagnosticsCore.PairDiagnostic> diagnostics =
                    new ArrayList<AdaptivePairDiagnosticsCore.PairDiagnostic>();
            int baselineUsable = 0;
            int baselineStrong = 0;
            List<PairExtra> extras = new ArrayList<PairExtra>();
            for (int index = 0; index < candidates.size(); index++) {
                PairIndex candidate = candidates.get(index);
                if (index % 4 == 0) {
                    notify(progress, String.format(Locale.ROOT,
                            "Analizando correspondencias %d/%d…",
                            index + 1, candidates.size()));
                }
                PreparedFrame left = prepared.get(candidate.left);
                PreparedFrame right = prepared.get(candidate.right);
                VisualFeatureCore.PairResult baseline =
                        VisualFeatureCore.match(left.features, right.features);
                if ("USABLE".equals(baseline.status)) baselineUsable++;
                if ("STRONG".equals(baseline.status)) baselineStrong++;

                AdaptivePairDiagnosticsCore.MatchResult adaptive =
                        AdaptivePairDiagnosticsCore.match(left.features, right.features);
                List<FundamentalMatrixCore.PointPair> points =
                        new ArrayList<FundamentalMatrixCore.PointPair>(adaptive.observations.size());
                for (AdaptivePairDiagnosticsCore.Observation observation : adaptive.observations) {
                    points.add(new FundamentalMatrixCore.PointPair(
                            observation.x, observation.y, observation.u, observation.v));
                }
                FundamentalMatrixCore.Result fundamental = points.size() >= 8
                        ? FundamentalMatrixCore.estimate(points, 3.2,
                        Math.max(120, Math.min(420, points.size() * 3)))
                        : null;
                int gap = sectorGap(left.spec.sector, right.spec.sector);
                AdaptivePairDiagnosticsCore.PairDiagnostic diagnostic =
                        AdaptivePairDiagnosticsCore.classify(
                                left.spec.sequence, right.spec.sequence,
                                left.spec.band, right.spec.band, gap, adaptive,
                                fundamental != null && fundamental.solved,
                                fundamental == null ? 0 : fundamental.inliers.size(),
                                fundamental == null ? 0.0 : fundamental.inlierRatio,
                                fundamental == null ? Double.POSITIVE_INFINITY : fundamental.rmsPx);
                diagnostics.add(diagnostic);
                extras.add(new PairExtra(baseline.matches.size(), baseline.spatialCoverage,
                        baseline.translationCoherentMatches,
                        baseline.translationCoherenceRatio, baseline.status));
            }

            List<Integer> sequences = new ArrayList<Integer>();
            for (PreparedFrame frame : prepared) sequences.add(frame.spec.sequence);
            AdaptivePairDiagnosticsCore.GraphResult graph =
                    AdaptivePairDiagnosticsCore.graph(sequences, diagnostics);
            int adaptiveUsable = 0;
            int adaptiveStrong = 0;
            for (AdaptivePairDiagnosticsCore.PairDiagnostic diagnostic : diagnostics) {
                if ("USABLE".equals(diagnostic.status)) adaptiveUsable++;
                if ("STRONG".equals(diagnostic.status)) adaptiveStrong++;
            }

            long durationMs = System.currentTimeMillis() - started;
            String reportJson = reportJson(manifest, sourceZipSha256,
                    accepted.size(), rejectedCount, prepared, integrityFailures,
                    diagnostics, extras, baselineUsable, baselineStrong,
                    adaptiveUsable, adaptiveStrong, graph, durationMs);
            File reportFile = new File(root, "zip_reprocess_diagnostics.json");
            writeText(reportFile, reportJson);
            File packageFile = new File(root,
                    safeName(manifest.optString("label", "capture"))
                            + "_alpha46_diagnostics.zip");
            buildDiagnosticPackage(zip, manifestEntry, reportFile, packageFile,
                    sourceZipSha256, schema);
            String summary = "REPROCESAMIENTO COMPLETO"
                    + "\nZIP: " + schema
                    + "\nFotografías íntegras: " + prepared.size() + "/" + accepted.size()
                    + "\nPares evaluados: " + diagnostics.size()
                    + "\nMatcher alpha44: fuertes " + baselineStrong
                    + " · utilizables " + baselineUsable
                    + "\nMatcher diagnóstico alpha46: fuertes " + adaptiveStrong
                    + " · utilizables " + adaptiveUsable
                    + "\nGrafo: componentes " + graph.componentCount
                    + " · mayor " + graph.largestComponent + "/" + graph.nodeCount
                    + "\nConectado: " + (graph.connected() ? "SÍ" : "NO")
                    + "\nDuración: " + String.format(Locale.ROOT,
                    "%.1f s", durationMs / 1000.0)
                    + "\n\nEste resultado es diagnóstico. No publica geometría métrica ni reemplaza validación industrial.";
            notify(progress, "Generando paquete de diagnóstico…");
            return new Result(reportFile, packageFile, summary, graph.connected(),
                    adaptiveUsable, adaptiveStrong, prepared.size(), diagnostics.size());
        }
    }

    private static List<PairIndex> candidates(final List<PreparedFrame> frames) {
        List<PairIndex> pairs = new ArrayList<PairIndex>();
        Set<Long> seen = new HashSet<Long>();
        for (int left = 0; left < frames.size(); left++) {
            for (int right = left + 1; right < frames.size(); right++) {
                FrameSpec a = frames.get(left).spec;
                FrameSpec b = frames.get(right).spec;
                int gap = sectorGap(a.sector, b.sector);
                boolean orbitCandidate = a.band.equals(b.band)
                        ? gap >= 1 && gap <= 2 : gap <= 1;
                boolean sequenceBridge = Math.abs(a.sequence - b.sequence) <= 2;
                if (!orbitCandidate && !sequenceBridge) continue;
                long key = ((long) left << 32) ^ (right & 0xffffffffL);
                if (seen.add(key)) pairs.add(new PairIndex(left, right));
            }
        }
        Collections.sort(pairs, new Comparator<PairIndex>() {
            @Override public int compare(PairIndex a, PairIndex b) {
                int da = Math.abs(frames.get(a.left).spec.sequence
                        - frames.get(a.right).spec.sequence);
                int db = Math.abs(frames.get(b.left).spec.sequence
                        - frames.get(b.right).spec.sequence);
                int compare = Integer.compare(da, db);
                if (compare != 0) return compare;
                compare = Integer.compare(a.left, b.left);
                return compare != 0 ? compare : Integer.compare(a.right, b.right);
            }
        });
        return pairs;
    }

    private static String reportJson(JSONObject manifest, String sourceZipSha256,
                                     int acceptedManifest, int rejectedManifest,
                                     List<PreparedFrame> frames, int integrityFailures,
                                     List<AdaptivePairDiagnosticsCore.PairDiagnostic> diagnostics,
                                     List<PairExtra> extras,
                                     int baselineUsable, int baselineStrong,
                                     int adaptiveUsable, int adaptiveStrong,
                                     AdaptivePairDiagnosticsCore.GraphResult graph,
                                     long durationMs) {
        StringBuilder json = new StringBuilder(16384 + diagnostics.size() * 480);
        json.append("{\n")
                .append("\"schema\":\"skm-zip-reprocess-diagnostics/1\",")
                .append("\n\"sourceSchema\":\"")
                .append(escape(manifest.optString("schema", ""))).append("\",")
                .append("\n\"sourceSessionId\":\"")
                .append(escape(manifest.optString("sessionId", ""))).append("\",")
                .append("\n\"sourceLabel\":\"")
                .append(escape(manifest.optString("label", ""))).append("\",")
                .append("\n\"sourceZipSha256\":\"").append(sourceZipSha256).append("\",")
                .append("\n\"acceptedManifest\":").append(acceptedManifest).append(',')
                .append("\n\"rejectedManifest\":").append(rejectedManifest).append(',')
                .append("\n\"preparedFrames\":").append(frames.size()).append(',')
                .append("\n\"integrityFailures\":").append(integrityFailures).append(',')
                .append("\n\"analysisLongEdge\":").append(ANALYSIS_LONG_EDGE).append(',')
                .append("\n\"featuresPerFrame\":").append(FEATURES_PER_FRAME).append(',')
                .append("\n\"candidatePairs\":").append(diagnostics.size()).append(',')
                .append("\n\"baselineUsablePairs\":").append(baselineUsable).append(',')
                .append("\n\"baselineStrongPairs\":").append(baselineStrong).append(',')
                .append("\n\"adaptiveUsablePairs\":").append(adaptiveUsable).append(',')
                .append("\n\"adaptiveStrongPairs\":").append(adaptiveStrong).append(',')
                .append("\n\"durationMs\":").append(durationMs).append(',')
                .append("\n\"graph\":{")
                .append("\"nodes\":").append(graph.nodeCount).append(',')
                .append("\"acceptedEdges\":").append(graph.acceptedEdges).append(',')
                .append("\"components\":").append(graph.componentCount).append(',')
                .append("\"largestComponent\":").append(graph.largestComponent).append(',')
                .append("\"connected\":").append(graph.connected()).append(',')
                .append("\"componentSizes\":[");
        for (int i = 0; i < graph.componentSizes.size(); i++) {
            if (i > 0) json.append(',');
            json.append(graph.componentSizes.get(i));
        }
        json.append("]},\n\"frameDiagnostics\":[");
        for (int i = 0; i < frames.size(); i++) {
            if (i > 0) json.append(',');
            PreparedFrame frame = frames.get(i);
            json.append("\n{")
                    .append("\"sequence\":").append(frame.spec.sequence).append(',')
                    .append("\"band\":\"").append(frame.spec.band).append("\",")
                    .append("\"sector\":").append(frame.spec.sector).append(',')
                    .append("\"width\":").append(frame.width).append(',')
                    .append("\"height\":").append(frame.height).append(',')
                    .append("\"featureCount\":").append(frame.features.features.size()).append(',')
                    .append("\"featureCoverage\":")
                    .append(number(frame.features.spatialCoverage)).append(',')
                    .append("\"blur\":").append(number(frame.spec.blur)).append(',')
                    .append("\"luma\":").append(number(frame.spec.luma)).append(',')
                    .append("\"motion\":").append(number(frame.spec.motion)).append(',')
                    .append("\"sha256\":\"").append(frame.sha256).append("\"}");
        }
        json.append("\n],\n\"pairDiagnostics\":[");
        for (int i = 0; i < diagnostics.size(); i++) {
            if (i > 0) json.append(',');
            AdaptivePairDiagnosticsCore.PairDiagnostic pair = diagnostics.get(i);
            PairExtra extra = extras.get(i);
            json.append("\n{")
                    .append("\"left\":").append(pair.leftSequence).append(',')
                    .append("\"right\":").append(pair.rightSequence).append(',')
                    .append("\"leftBand\":\"").append(pair.leftBand).append("\",")
                    .append("\"rightBand\":\"").append(pair.rightBand).append("\",")
                    .append("\"sectorGap\":").append(pair.sectorGap).append(',')
                    .append("\"baselineMatches\":").append(extra.baselineMatches).append(',')
                    .append("\"baselineCoverage\":")
                    .append(number(extra.baselineCoverage)).append(',')
                    .append("\"baselineCoherent\":").append(extra.baselineCoherent).append(',')
                    .append("\"baselineCoherenceRatio\":")
                    .append(number(extra.baselineCoherenceRatio)).append(',')
                    .append("\"baselineStatus\":\"").append(extra.baselineStatus).append("\",")
                    .append("\"adaptiveObservations\":").append(pair.observations).append(',')
                    .append("\"ratioPassed\":").append(pair.ratioPassed).append(',')
                    .append("\"p75SecondBestRatio\":")
                    .append(number(pair.p75SecondBestRatio)).append(',')
                    .append("\"mutualFraction\":").append(number(pair.mutualFraction)).append(',')
                    .append("\"roiCoverage\":").append(number(pair.roiCoverage)).append(',')
                    .append("\"fundamentalSolved\":").append(pair.fundamentalSolved).append(',')
                    .append("\"fundamentalInliers\":").append(pair.fundamentalInliers).append(',')
                    .append("\"fundamentalInlierRatio\":")
                    .append(number(pair.fundamentalInlierRatio)).append(',')
                    .append("\"fundamentalRmsPx\":")
                    .append(numberOrNull(pair.fundamentalRmsPx)).append(',')
                    .append("\"repetitiveUnsupported\":")
                    .append(pair.repetitiveUnsupported).append(',')
                    .append("\"status\":\"").append(pair.status).append("\",")
                    .append("\"reason\":\"").append(pair.reason).append("\"}");
        }
        return json.append("\n]\n}").toString();
    }

    private static void buildDiagnosticPackage(ZipFile sourceZip,
                                               ZipEntry manifestEntry,
                                               File report, File output,
                                               String sourceZipSha256,
                                               String sourceSchema) throws Exception {
        try (ZipOutputStream zip = new ZipOutputStream(
                new BufferedOutputStream(new FileOutputStream(output)))) {
            copyFile(zip, report, "zip_reprocess_diagnostics.json");
            copyZipEntry(sourceZip, manifestEntry, zip, "source_manifest.json");
            copyIfExists(sourceZip, "overlap_report.json", zip,
                    "source_overlap_report.json");
            copyIfExists(sourceZip, "runtime/runtime_supplemental_metrics.json", zip,
                    "source_runtime_supplemental_metrics.json");
            String provenance = "{\n\"schema\":\"skm-zip-reprocess-provenance/1\","
                    + "\n\"sourceSchema\":\"" + escape(sourceSchema) + "\","
                    + "\n\"sourceZipSha256\":\"" + sourceZipSha256 + "\","
                    + "\n\"containsSourceImages\":false,"
                    + "\n\"industrialRelease\":false\n}";
            putText(zip, "provenance.json", provenance);
        }
    }

    private static void copyIfExists(ZipFile source, String sourceName,
                                     ZipOutputStream target,
                                     String targetName) throws Exception {
        ZipEntry entry = source.getEntry(sourceName);
        if (entry != null) copyZipEntry(source, entry, target, targetName);
    }

    private static void copyZipEntry(ZipFile source, ZipEntry entry,
                                     ZipOutputStream target,
                                     String name) throws Exception {
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

    private static void copyUri(Context context, Uri uri,
                                File target) throws Exception {
        try (InputStream input = context.getContentResolver().openInputStream(uri);
             OutputStream output = new BufferedOutputStream(new FileOutputStream(target))) {
            if (input == null) throw new IllegalStateException("No se pudo abrir el ZIP");
            copy(input, output, MAX_ZIP_BYTES);
        }
    }

    private static void copyEntry(ZipFile zip, ZipEntry entry,
                                  File target) throws Exception {
        try (InputStream input = new BufferedInputStream(zip.getInputStream(entry));
             OutputStream output = new BufferedOutputStream(new FileOutputStream(target))) {
            copy(input, output, MAX_ENTRY_BYTES);
        }
    }

    private static void copy(InputStream input, OutputStream output,
                             long limit) throws Exception {
        byte[] buffer = new byte[64 * 1024];
        long total = 0;
        int read;
        while ((read = input.read(buffer)) >= 0) {
            total += read;
            if (total > limit) {
                throw new IllegalStateException("Entrada excede límite de seguridad");
            }
            output.write(buffer, 0, read);
        }
    }

    private static String readText(ZipFile zip, ZipEntry entry,
                                   long limit) throws Exception {
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
            throw new IllegalStateException("Entrada ZIP demasiado grande: " + name);
        }
    }

    private static Gray decode(File file) {
        BitmapFactory.Options bounds = new BitmapFactory.Options();
        bounds.inJustDecodeBounds = true;
        BitmapFactory.decodeFile(file.getAbsolutePath(), bounds);
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) {
            throw new IllegalStateException("JPEG inválido: " + file.getName());
        }
        int sample = 1;
        while (Math.max(bounds.outWidth, bounds.outHeight) / sample
                > ANALYSIS_LONG_EDGE) sample *= 2;
        BitmapFactory.Options options = new BitmapFactory.Options();
        options.inSampleSize = Math.max(1, sample);
        options.inPreferredConfig = Bitmap.Config.ARGB_8888;
        Bitmap bitmap = BitmapFactory.decodeFile(file.getAbsolutePath(), options);
        if (bitmap == null) {
            throw new IllegalStateException("No se pudo decodificar " + file.getName());
        }
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
                            0.2126 * Color.red(color)
                                    + 0.7152 * Color.green(color)
                                    + 0.0722 * Color.blue(color));
                }
            }
            return new Gray(width, height, gray);
        } finally {
            bitmap.recycle();
        }
    }

    private static int sectorGap(int first, int second) {
        int gap = Math.abs(first - second);
        return Math.min(gap, 12 - gap);
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
        StringBuilder hex = new StringBuilder(64);
        for (byte value : digest.digest()) {
            hex.append(String.format(Locale.ROOT, "%02x", value & 0xff));
        }
        return hex.toString();
    }

    private static String safeName(String raw) {
        String safe = raw == null ? "capture"
                : raw.trim().replaceAll("[^A-Za-z0-9._-]+", "_");
        return safe.isEmpty() ? "capture" : safe;
    }

    private static String number(double value) {
        return Double.isFinite(value) ? Double.toString(value) : "0.0";
    }

    private static String numberOrNull(double value) {
        return Double.isFinite(value) ? Double.toString(value) : "null";
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
        final int sequence;
        final int sector;
        final String band;
        final String quality;
        final String sha256;
        final double blur;
        final double luma;
        final double motion;

        FrameSpec(int sequence, int sector, String band, String quality,
                  String sha256, double blur, double luma, double motion) {
            this.sequence = sequence;
            this.sector = Math.max(0, Math.min(11, sector));
            this.band = "HIGH".equals(band) ? "HIGH" : "LOW";
            this.quality = quality == null ? "UNKNOWN" : quality;
            this.sha256 = sha256;
            this.blur = blur;
            this.luma = luma;
            this.motion = motion;
        }

        static FrameSpec from(JSONObject json) {
            return new FrameSpec(json.optInt("sequence", -1),
                    json.optInt("sector", 0),
                    json.optString("band", "LOW"),
                    json.optString("quality", "UNKNOWN"),
                    json.optString("sha256", ""),
                    json.optDouble("blur", 0.0),
                    json.optDouble("luma", 0.0),
                    json.optDouble("motion", 0.0));
        }
    }

    private static final class PreparedFrame {
        final FrameSpec spec;
        final File file;
        final String sha256;
        final int width;
        final int height;
        final VisualFeatureCore.FeatureSet features;

        PreparedFrame(FrameSpec spec, File file, String sha256,
                      int width, int height,
                      VisualFeatureCore.FeatureSet features) {
            this.spec = spec;
            this.file = file;
            this.sha256 = sha256;
            this.width = width;
            this.height = height;
            this.features = features;
        }
    }

    private static final class PairIndex {
        final int left;
        final int right;
        PairIndex(int left, int right) {
            this.left = left;
            this.right = right;
        }
    }

    private static final class PairExtra {
        final int baselineMatches;
        final int baselineCoherent;
        final double baselineCoverage;
        final double baselineCoherenceRatio;
        final String baselineStatus;

        PairExtra(int baselineMatches, double baselineCoverage,
                  int baselineCoherent, double baselineCoherenceRatio,
                  String baselineStatus) {
            this.baselineMatches = baselineMatches;
            this.baselineCoverage = baselineCoverage;
            this.baselineCoherent = baselineCoherent;
            this.baselineCoherenceRatio = baselineCoherenceRatio;
            this.baselineStatus = baselineStatus;
        }
    }

    private static final class Gray {
        final int width;
        final int height;
        final byte[] pixels;
        Gray(int width, int height, byte[] pixels) {
            this.width = width;
            this.height = height;
            this.pixels = pixels;
        }
    }

    public static final class Result {
        public final File reportFile;
        public final File packageFile;
        public final String summary;
        public final boolean graphConnected;
        public final int usablePairs;
        public final int strongPairs;
        public final int preparedFrames;
        public final int evaluatedPairs;

        Result(File reportFile, File packageFile, String summary,
               boolean graphConnected, int usablePairs, int strongPairs,
               int preparedFrames, int evaluatedPairs) {
            this.reportFile = reportFile;
            this.packageFile = packageFile;
            this.summary = summary;
            this.graphConnected = graphConnected;
            this.usablePairs = usablePairs;
            this.strongPairs = strongPairs;
            this.preparedFrames = preparedFrames;
            this.evaluatedPairs = evaluatedPairs;
        }
    }
}

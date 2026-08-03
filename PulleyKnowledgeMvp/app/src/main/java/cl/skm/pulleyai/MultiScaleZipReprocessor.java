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

/**
 * ITER-024 portable ZIP reprocessor.
 *
 * It preserves the alpha46 diagnostic result, then runs a second independent
 * multiscale/oriented binary pipeline. Two graphs are emitted:
 * - primary graph: only STRONG/USABLE edges, suitable for later geometric work;
 * - diagnostic graph: also includes low-count BRIDGE edges, useful for proving
 *   continuity but explicitly forbidden from publishing metric geometry.
 */
public final class MultiScaleZipReprocessor {
    private static final long MAX_ZIP_BYTES = 1024L * 1024L * 1024L;
    private static final long MAX_ENTRY_BYTES = 64L * 1024L * 1024L;
    private static final int MAX_FRAMES = 96;
    private static final int ANALYSIS_LONG_EDGE = 1280;
    private static final int FEATURES_PER_FRAME = 1400;
    private static final int MAX_PAIR_COUNT = 420;

    private MultiScaleZipReprocessor() {}

    public interface Progress {
        void onProgress(String message);
    }

    public static Result process(Context context, Uri sourceUri, Progress progress) throws Exception {
        if (context == null || sourceUri == null) throw new IllegalArgumentException("ZIP ausente");
        long started = System.currentTimeMillis();

        PortableZipReprocessor.Result alpha46 = null;
        String alpha46Failure = null;
        try {
            notify(progress, "Ejecutando comparación alpha46…");
            alpha46 = PortableZipReprocessor.process(context, sourceUri,
                    new PortableZipReprocessor.Progress() {
                        @Override public void onProgress(String message) {
                            MultiScaleZipReprocessor.notify(progress, "alpha46 · " + message);
                        }
                    });
        } catch (Exception failure) {
            alpha46Failure = failure.getMessage() == null
                    ? failure.getClass().getSimpleName() : failure.getMessage();
            notify(progress, "alpha46 no concluyó; alpha47 continuará de forma independiente…");
        }

        File root = new File(context.getCacheDir(), "zip_multiscale_reprocess/" + started);
        if (!root.mkdirs() && !root.isDirectory()) {
            throw new IllegalStateException("No se pudo crear carpeta temporal multiescala");
        }
        File localZip = new File(root, "source_capture.zip");
        notify(progress, "Copiando ZIP para análisis multiescala…");
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
                FrameSpec spec = FrameSpec.from(framesJson.getJSONObject(i));
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
                        "alpha47 · preparando foto %d/%d…", i + 1, accepted.size()));
                String entryName = String.format(Locale.ROOT,
                        "frames/frame_%04d.jpg", spec.sequence);
                ZipEntry entry = zip.getEntry(entryName);
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
                Gray gray = decode(target, ANALYSIS_LONG_EDGE);
                MultiScaleOrientedFeatureCore.FeatureSet features =
                        MultiScaleOrientedFeatureCore.detect(
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
            List<MultiScaleGeometricDiagnosticsCore.PairDiagnostic> diagnostics =
                    new ArrayList<MultiScaleGeometricDiagnosticsCore.PairDiagnostic>();
            for (int index = 0; index < candidates.size(); index++) {
                PairIndex candidate = candidates.get(index);
                if ((index & 3) == 0) {
                    notify(progress, String.format(Locale.ROOT,
                            "alpha47 · geometría multiescala %d/%d…",
                            index + 1, candidates.size()));
                }
                PreparedFrame left = prepared.get(candidate.left);
                PreparedFrame right = prepared.get(candidate.right);
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
                        Math.max(180, Math.min(800, points.size() * 4)))
                        : null;
                diagnostics.add(MultiScaleGeometricDiagnosticsCore.classify(
                        left.spec.sequence, right.spec.sequence,
                        left.spec.band, right.spec.band,
                        sectorGap(left.spec.sector, right.spec.sector), matches,
                        fundamental != null && fundamental.solved,
                        fundamental == null ? 0 : fundamental.inliers.size(),
                        fundamental == null ? 0.0 : fundamental.inlierRatio,
                        fundamental == null ? Double.POSITIVE_INFINITY : fundamental.rmsPx));
            }

            List<Integer> sequences = new ArrayList<Integer>();
            for (PreparedFrame frame : prepared) sequences.add(frame.spec.sequence);
            MultiScaleGeometricDiagnosticsCore.GraphResult primaryGraph =
                    MultiScaleGeometricDiagnosticsCore.graph(sequences, diagnostics, false);
            MultiScaleGeometricDiagnosticsCore.GraphResult diagnosticGraph =
                    MultiScaleGeometricDiagnosticsCore.graph(sequences, diagnostics, true);
            int strong = 0;
            int usable = 0;
            int bridges = 0;
            for (MultiScaleGeometricDiagnosticsCore.PairDiagnostic pair : diagnostics) {
                if ("STRONG".equals(pair.status)) strong++;
                else if ("USABLE".equals(pair.status)) usable++;
                else if ("BRIDGE".equals(pair.status)) bridges++;
            }

            long durationMs = System.currentTimeMillis() - started;
            String reportJson = reportJson(manifest, sourceZipSha256,
                    accepted.size(), rejectedCount, prepared, integrityFailures,
                    diagnostics, primaryGraph, diagnosticGraph,
                    strong, usable, bridges, alpha46, alpha46Failure, durationMs);
            File reportFile = new File(root, "zip_multiscale_reprocess_diagnostics.json");
            writeText(reportFile, reportJson);
            File packageFile = new File(root,
                    safeName(manifest.optString("label", "capture"))
                            + "_alpha47_multiscale_diagnostics.zip");
            buildDiagnosticPackage(zip, manifestEntry, reportFile, packageFile,
                    alpha46, sourceZipSha256, schema);

            String alpha46Summary = alpha46 == null
                    ? "alpha46: no concluyó · " + alpha46Failure
                    : "alpha46: fuertes " + alpha46.strongPairs
                    + " · utilizables " + alpha46.usablePairs
                    + " · conectado " + yesNo(alpha46.graphConnected);
            String summary = "REPROCESAMIENTO MULTIESCALA COMPLETO"
                    + "\n" + alpha46Summary
                    + "\nalpha47: fuertes " + strong + " · utilizables " + usable
                    + " · puentes diagnósticos " + bridges
                    + "\nGrafo primario: componentes " + primaryGraph.componentCount
                    + " · mayor " + primaryGraph.largestComponent + "/" + primaryGraph.nodeCount
                    + " · conectado " + yesNo(primaryGraph.connected())
                    + "\nGrafo con puentes: componentes " + diagnosticGraph.componentCount
                    + " · mayor " + diagnosticGraph.largestComponent + "/" + diagnosticGraph.nodeCount
                    + " · conectado " + yesNo(diagnosticGraph.connected())
                    + "\nCruces entre anillos: " + diagnosticGraph.crossRingEdges
                    + "\nFotografías íntegras: " + prepared.size() + "/" + accepted.size()
                    + " · pares " + diagnostics.size()
                    + "\nDuración total: " + String.format(Locale.ROOT,
                    "%.1f s", durationMs / 1000.0)
                    + "\n\nLos puentes de 9–13 inliers solo demuestran continuidad probable."
                    + " No habilitan BA, nube métrica, CAD ni liberación industrial.";
            notify(progress, "Generando paquete comparativo alpha46/alpha47…");
            return new Result(reportFile, packageFile, summary,
                    primaryGraph.connected(), diagnosticGraph.connected(),
                    usable, strong, bridges, prepared.size(), diagnostics.size());
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
                boolean sequenceBridge = Math.abs(left - right) <= 2;
                double yawGap = Math.abs(wrapDegrees(a.yaw - b.yaw));
                boolean yawCandidate = a.band.equals(b.band)
                        ? yawGap >= 6.0 && yawGap <= 75.0 : yawGap <= 35.0;
                if (!orbitCandidate && !sequenceBridge && !yawCandidate) continue;
                long key = ((long) left << 32) ^ (right & 0xffffffffL);
                if (seen.add(key)) pairs.add(new PairIndex(left, right));
            }
        }
        Collections.sort(pairs, new Comparator<PairIndex>() {
            @Override public int compare(PairIndex first, PairIndex second) {
                PreparedFrame fa = frames.get(first.left);
                PreparedFrame fb = frames.get(first.right);
                PreparedFrame sa = frames.get(second.left);
                PreparedFrame sb = frames.get(second.right);
                int firstRankGap = Math.abs(first.left - first.right);
                int secondRankGap = Math.abs(second.left - second.right);
                int compare = Integer.compare(firstRankGap, secondRankGap);
                if (compare != 0) return compare;
                compare = Integer.compare(sectorGap(fa.spec.sector, fb.spec.sector),
                        sectorGap(sa.spec.sector, sb.spec.sector));
                if (compare != 0) return compare;
                compare = Double.compare(Math.abs(wrapDegrees(fa.spec.yaw - fb.spec.yaw)),
                        Math.abs(wrapDegrees(sa.spec.yaw - sb.spec.yaw)));
                if (compare != 0) return compare;
                compare = Integer.compare(first.left, second.left);
                return compare != 0 ? compare : Integer.compare(first.right, second.right);
            }
        });
        return pairs;
    }

    private static String reportJson(JSONObject manifest, String sourceZipSha256,
                                     int acceptedManifest, int rejectedManifest,
                                     List<PreparedFrame> frames, int integrityFailures,
                                     List<MultiScaleGeometricDiagnosticsCore.PairDiagnostic> pairs,
                                     MultiScaleGeometricDiagnosticsCore.GraphResult primaryGraph,
                                     MultiScaleGeometricDiagnosticsCore.GraphResult diagnosticGraph,
                                     int strong, int usable, int bridges,
                                     PortableZipReprocessor.Result alpha46,
                                     String alpha46Failure, long durationMs) {
        StringBuilder json = new StringBuilder(24000 + pairs.size() * 700);
        json.append("{\n")
                .append("\"schema\":\"skm-zip-multiscale-reprocess/1\",")
                .append("\n\"sourceSchema\":\"")
                .append(escape(manifest.optString("schema", ""))).append("\",")
                .append("\n\"sourceSessionId\":\"")
                .append(escape(manifest.optString("sessionId", ""))).append("\",")
                .append("\n\"sourceLabel\":\"")
                .append(escape(manifest.optString("label", ""))).append("\",")
                .append("\n\"sourceZipSha256\":\"").append(sourceZipSha256).append("\",")
                .append("\n\"analysisLongEdge\":").append(ANALYSIS_LONG_EDGE).append(',')
                .append("\n\"pyramidScales\":[1.0,0.8,0.64],")
                .append("\n\"descriptorBits\":256,")
                .append("\n\"orientationNormalized\":true,")
                .append("\n\"featuresPerFrameLimit\":").append(FEATURES_PER_FRAME).append(',')
                .append("\n\"acceptedManifest\":").append(acceptedManifest).append(',')
                .append("\n\"rejectedManifest\":").append(rejectedManifest).append(',')
                .append("\n\"preparedFrames\":").append(frames.size()).append(',')
                .append("\n\"integrityFailures\":").append(integrityFailures).append(',')
                .append("\n\"candidatePairs\":").append(pairs.size()).append(',')
                .append("\n\"strongPairs\":").append(strong).append(',')
                .append("\n\"usablePairs\":").append(usable).append(',')
                .append("\n\"diagnosticBridgePairs\":").append(bridges).append(',')
                .append("\n\"durationMs\":").append(durationMs).append(',')
                .append("\n\"industrialRelease\":false,")
                .append("\n\"metricGeometryPublished\":false,")
                .append("\n\"alpha46Comparison\":{")
                .append("\"completed\":").append(alpha46 != null).append(',')
                .append("\"failure\":").append(quotedOrNull(alpha46Failure)).append(',')
                .append("\"usablePairs\":").append(alpha46 == null ? 0 : alpha46.usablePairs).append(',')
                .append("\"strongPairs\":").append(alpha46 == null ? 0 : alpha46.strongPairs).append(',')
                .append("\"graphConnected\":").append(alpha46 != null && alpha46.graphConnected)
                .append("},")
                .append("\n\"primaryGraph\":")
                .append(graphJson(primaryGraph)).append(',')
                .append("\n\"diagnosticBridgeGraph\":")
                .append(graphJson(diagnosticGraph)).append(',')
                .append("\n\"frameDiagnostics\":[");
        for (int i = 0; i < frames.size(); i++) {
            if (i > 0) json.append(',');
            PreparedFrame frame = frames.get(i);
            json.append("\n{")
                    .append("\"sequence\":").append(frame.spec.sequence).append(',')
                    .append("\"band\":\"").append(frame.spec.band).append("\",")
                    .append("\"sector\":").append(frame.spec.sector).append(',')
                    .append("\"yaw\":").append(number(frame.spec.yaw)).append(',')
                    .append("\"width\":").append(frame.width).append(',')
                    .append("\"height\":").append(frame.height).append(',')
                    .append("\"featureCount\":").append(frame.features.features.size()).append(',')
                    .append("\"featureCoverage\":")
                    .append(number(frame.features.spatialCoverage)).append(',')
                    .append("\"featuresPerLevel\":[");
            for (int level = 0; level < frame.features.featuresPerLevel.length; level++) {
                if (level > 0) json.append(',');
                json.append(frame.features.featuresPerLevel[level]);
            }
            json.append("],\"sha256\":\"").append(frame.sha256).append("\"}");
        }
        json.append("\n],\n\"pairDiagnostics\":[");
        for (int i = 0; i < pairs.size(); i++) {
            if (i > 0) json.append(',');
            MultiScaleGeometricDiagnosticsCore.PairDiagnostic pair = pairs.get(i);
            json.append("\n{")
                    .append("\"left\":").append(pair.leftSequence).append(',')
                    .append("\"right\":").append(pair.rightSequence).append(',')
                    .append("\"leftBand\":\"").append(pair.leftBand).append("\",")
                    .append("\"rightBand\":\"").append(pair.rightBand).append("\",")
                    .append("\"sectorGap\":").append(pair.sectorGap).append(',')
                    .append("\"observations\":").append(pair.observations).append(',')
                    .append("\"ratioPassed\":").append(pair.ratioPassed).append(',')
                    .append("\"p75SecondBestRatio\":")
                    .append(number(pair.p75SecondBestRatio)).append(',')
                    .append("\"mutualFraction\":").append(number(pair.mutualFraction)).append(',')
                    .append("\"roiCoverage\":").append(number(pair.roiCoverage)).append(',')
                    .append("\"orientationConcentration\":")
                    .append(number(pair.orientationConcentration)).append(',')
                    .append("\"medianScaleRatio\":")
                    .append(number(pair.medianScaleRatio)).append(',')
                    .append("\"strictObservations\":").append(pair.strictObservations).append(',')
                    .append("\"guidedAdded\":").append(pair.guidedAdded).append(',')
                    .append("\"affineSolved\":").append(pair.affineSolved).append(',')
                    .append("\"affineInliers\":").append(pair.affineInliers).append(',')
                    .append("\"affineInlierRatio\":")
                    .append(number(pair.affineInlierRatio)).append(',')
                    .append("\"affineRmsPx\":")
                    .append(numberOrNull(pair.affineRmsPx)).append(',')
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

    private static String graphJson(MultiScaleGeometricDiagnosticsCore.GraphResult graph) {
        StringBuilder json = new StringBuilder();
        json.append('{')
                .append("\"nodes\":").append(graph.nodeCount).append(',')
                .append("\"acceptedEdges\":").append(graph.acceptedEdges).append(',')
                .append("\"primaryEdges\":").append(graph.primaryEdges).append(',')
                .append("\"bridgeEdges\":").append(graph.bridgeEdges).append(',')
                .append("\"crossRingEdges\":").append(graph.crossRingEdges).append(',')
                .append("\"components\":").append(graph.componentCount).append(',')
                .append("\"largestComponent\":").append(graph.largestComponent).append(',')
                .append("\"connected\":").append(graph.connected()).append(',')
                .append("\"componentSizes\":[");
        for (int i = 0; i < graph.componentSizes.size(); i++) {
            if (i > 0) json.append(',');
            json.append(graph.componentSizes.get(i));
        }
        return json.append("]}").toString();
    }

    private static void buildDiagnosticPackage(ZipFile sourceZip,
                                               ZipEntry manifestEntry,
                                               File report, File output,
                                               PortableZipReprocessor.Result alpha46,
                                               String sourceZipSha256,
                                               String sourceSchema) throws Exception {
        try (ZipOutputStream zip = new ZipOutputStream(
                new BufferedOutputStream(new FileOutputStream(output)))) {
            copyFile(zip, report, "zip_multiscale_reprocess_diagnostics.json");
            if (alpha46 != null && alpha46.reportFile != null && alpha46.reportFile.isFile()) {
                copyFile(zip, alpha46.reportFile, "zip_alpha46_pair_diagnostics.json");
            }
            copyZipEntry(sourceZip, manifestEntry, zip, "source_manifest.json");
            copyIfExists(sourceZip, "overlap_report.json", zip,
                    "source_overlap_report.json");
            copyIfExists(sourceZip, "runtime/runtime_supplemental_metrics.json", zip,
                    "source_runtime_supplemental_metrics.json");
            String provenance = "{\n\"schema\":\"skm-zip-multiscale-provenance/1\","
                    + "\n\"sourceSchema\":\"" + escape(sourceSchema) + "\","
                    + "\n\"sourceZipSha256\":\"" + sourceZipSha256 + "\","
                    + "\n\"containsSourceImages\":false,"
                    + "\n\"diagnosticBridgeMayPublishMetricGeometry\":false,"
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
        long total = 0L;
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
        if (bitmap == null) {
            throw new IllegalStateException("No se pudo decodificar " + file.getName());
        }
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

    private static int sectorGap(int first, int second) {
        int gap = Math.abs(first - second);
        return Math.min(gap, 12 - gap);
    }

    private static double wrapDegrees(double value) {
        while (value > 180.0) value -= 360.0;
        while (value < -180.0) value += 360.0;
        return value;
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

    private static String quotedOrNull(String value) {
        return value == null || value.isEmpty() ? "null" : "\"" + escape(value) + "\"";
    }

    private static String escape(String value) {
        if (value == null) return "";
        return value.replace("\\", "\\\\")
                .replace("\"", "\\\"")
                .replace("\n", "\\n")
                .replace("\r", "\\r");
    }

    private static String yesNo(boolean value) {
        return value ? "SÍ" : "NO";
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
            this.sha256 = sha256;
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
        final File file;
        final String sha256;
        final int width, height;
        final MultiScaleOrientedFeatureCore.FeatureSet features;
        PreparedFrame(FrameSpec spec, File file, String sha256,
                      int width, int height,
                      MultiScaleOrientedFeatureCore.FeatureSet features) {
            this.spec = spec;
            this.file = file;
            this.sha256 = sha256;
            this.width = width;
            this.height = height;
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
        public final File reportFile, packageFile;
        public final String summary;
        public final boolean primaryGraphConnected, diagnosticGraphConnected;
        public final int usablePairs, strongPairs, bridgePairs;
        public final int preparedFrames, evaluatedPairs;
        Result(File reportFile, File packageFile, String summary,
               boolean primaryGraphConnected, boolean diagnosticGraphConnected,
               int usablePairs, int strongPairs, int bridgePairs,
               int preparedFrames, int evaluatedPairs) {
            this.reportFile = reportFile;
            this.packageFile = packageFile;
            this.summary = summary;
            this.primaryGraphConnected = primaryGraphConnected;
            this.diagnosticGraphConnected = diagnosticGraphConnected;
            this.usablePairs = usablePairs;
            this.strongPairs = strongPairs;
            this.bridgePairs = bridgePairs;
            this.preparedFrames = preparedFrames;
            this.evaluatedPairs = evaluatedPairs;
        }
    }
}

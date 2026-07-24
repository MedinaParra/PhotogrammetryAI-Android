package cl.skm.pulleyai;

import android.content.Context;

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
import java.util.ArrayList;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

/** Reads pair diagnostics and ranks measured cross-component capture bridges. */
public final class ImportedBridgeEvidenceAnalyzer {
    private static final long MAX_REPORT_BYTES = 32L * 1024L * 1024L;

    private ImportedBridgeEvidenceAnalyzer() {}

    public static Result process(Context context,
                                 MultiScaleZipReprocessor.Result multiscale,
                                 ImportedComponentGeometryAnalyzer.Result components) throws Exception {
        if (context == null) throw new IllegalArgumentException("Contexto ausente");
        if (multiscale == null || multiscale.reportFile == null
                || !multiscale.reportFile.isFile()) {
            throw new IllegalStateException("Diagnóstico multiescala ausente");
        }
        if (components == null || components.topology == null) {
            throw new IllegalStateException("Topología de componentes ausente");
        }
        JSONObject report = new JSONObject(readText(multiscale.reportFile, MAX_REPORT_BYTES));
        JSONArray pairs = report.optJSONArray("pairDiagnostics");
        List<ImportedBridgeEvidenceCore.Candidate> candidates =
                new ArrayList<ImportedBridgeEvidenceCore.Candidate>();
        if (pairs != null) {
            for (int i = 0; i < pairs.length(); i++) {
                JSONObject pair = pairs.optJSONObject(i);
                if (pair == null) continue;
                candidates.add(new ImportedBridgeEvidenceCore.Candidate(
                        pair.optInt("left", -1), pair.optInt("right", -1),
                        pair.optString("leftBand", "LOW"),
                        pair.optString("rightBand", "LOW"),
                        pair.optString("status", "WEAK"),
                        pair.optBoolean("fundamentalSolved", false),
                        pair.optInt("fundamentalInliers", 0),
                        pair.optDouble("fundamentalInlierRatio", 0.0),
                        nullableDouble(pair, "fundamentalRmsPx"),
                        pair.optDouble("roiCoverage", 0.0),
                        pair.optDouble("mutualFraction", 0.0)));
            }
        }
        ImportedBridgeEvidenceCore.Result evidence =
                ImportedBridgeEvidenceCore.analyze(components.topology, candidates);
        long now = System.currentTimeMillis();
        File root = new File(context.getCacheDir(), "imported_bridge_evidence/" + now);
        if (!root.mkdirs() && !root.isDirectory()) {
            throw new IllegalStateException("No se pudo crear carpeta de evidencia puente");
        }
        File evidenceFile = new File(root, "imported_bridge_evidence.json");
        writeText(evidenceFile, evidence.canonicalJson());
        File packageFile = new File(root, "alpha52_bridge_evidence.zip");
        try (ZipOutputStream zip = new ZipOutputStream(
                new BufferedOutputStream(new FileOutputStream(packageFile)))) {
            copyFile(zip, evidenceFile, "imported_bridge_evidence.json");
            copyFile(zip, components.reportFile, "imported_component_geometry.json");
            copyFile(zip, multiscale.reportFile, "zip_multiscale_reprocess_diagnostics.json");
            String state = "{\n"
                    + "\"schema\":\"skm-bridge-release-state/1\",\n"
                    + "\"autoPromoted\":false,\n"
                    + "\"globalReconstruction\":false,\n"
                    + "\"metricScale\":false,\n"
                    + "\"industrialRelease\":false\n}";
            putText(zip, "release_state.json", state);
        }
        return new Result(evidenceFile, packageFile, evidence.summary(), evidence);
    }

    private static double nullableDouble(JSONObject object, String key) {
        if (object == null || object.isNull(key)) return Double.POSITIVE_INFINITY;
        double value = object.optDouble(key, Double.POSITIVE_INFINITY);
        return Double.isFinite(value) ? value : Double.POSITIVE_INFINITY;
    }

    private static String readText(File file, long limit) throws Exception {
        try (InputStream input = new BufferedInputStream(new FileInputStream(file));
             ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            byte[] buffer = new byte[64 * 1024];
            long total = 0;
            int read;
            while ((read = input.read(buffer)) >= 0) {
                total += read;
                if (total > limit) throw new IllegalStateException("Informe excede límite");
                output.write(buffer, 0, read);
            }
            return new String(output.toByteArray(), StandardCharsets.UTF_8);
        }
    }

    private static void writeText(File file, String text) throws Exception {
        try (OutputStream output = new BufferedOutputStream(new FileOutputStream(file))) {
            output.write(text.getBytes(StandardCharsets.UTF_8));
        }
    }

    private static void copyFile(ZipOutputStream zip, File source, String name) throws Exception {
        if (source == null || !source.isFile()) return;
        zip.putNextEntry(new ZipEntry(name));
        try (InputStream input = new BufferedInputStream(new FileInputStream(source))) {
            byte[] buffer = new byte[64 * 1024];
            int read;
            while ((read = input.read(buffer)) >= 0) zip.write(buffer, 0, read);
        }
        zip.closeEntry();
    }

    private static void putText(ZipOutputStream zip, String name, String text) throws Exception {
        zip.putNextEntry(new ZipEntry(name));
        zip.write(text.getBytes(StandardCharsets.UTF_8));
        zip.closeEntry();
    }

    public static final class Result {
        public final File reportFile, packageFile;
        public final String summary;
        public final ImportedBridgeEvidenceCore.Result evidence;

        Result(File reportFile, File packageFile, String summary,
               ImportedBridgeEvidenceCore.Result evidence) {
            this.reportFile = reportFile;
            this.packageFile = packageFile;
            this.summary = summary;
            this.evidence = evidence;
        }
    }
}

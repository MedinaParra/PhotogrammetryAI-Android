package cl.skm.pulleyai;

import android.content.Context;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

/** Consolidates every imported replay stage into one honest completion decision. */
public final class ImportedReplayCompletionAnalyzer {
    private ImportedReplayCompletionAnalyzer() {}

    public static Result process(Context context,
                                 CaptureZipNormalizer.Result preflight,
                                 MultiScaleZipReprocessor.Result multiscale,
                                 ImportedTrackZipAnalyzer.Result tracks,
                                 ImportedSeedGeometryZipAnalyzer.Result seed,
                                 ImportedComponentGeometryAnalyzer.Result components,
                                 ImportedBridgeEvidenceAnalyzer.Result bridge) throws Exception {
        if (context == null) throw new IllegalArgumentException("Contexto ausente");
        ImportedReplayCompletionCore.Input input = new ImportedReplayCompletionCore.Input();
        input.packageValid = preflight != null && preflight.usableFrames >= 8
                && preflight.preflight.failures.isEmpty();
        input.matchingComplete = multiscale != null;
        input.tracksComplete = tracks != null && tracks.tracks != null;
        input.seedStageComplete = seed != null && seed.geometry != null;
        input.componentStageComplete = components != null && components.topology != null;
        input.bridgeStageComplete = bridge != null && bridge.evidence != null;
        input.bridgeRecommendationAvailable = bridge != null && bridge.evidence != null
                && bridge.evidence.recommendation != null;
        input.globalConnected = components != null && components.topology.globalConnected;
        input.localGeometryReady = components != null
                && components.topology.localGeometryReady;
        input.metricScaleReady = false;
        input.metrologyValidated = false;
        input.deviceCampaignPassed = false;
        input.corporateIdentitySigned = false;
        input.acceptedFrames = preflight == null ? 0 : preflight.usableFrames;
        input.representedFrames = components == null ? 0
                : components.topology.representedFrames;
        input.trackCount = tracks == null || tracks.tracks == null
                ? 0 : tracks.tracks.tracks.size();
        input.triangulatedSeedPoints = seed == null || seed.geometry == null
                || seed.geometry.cloud == null ? 0 : seed.geometry.cloud.points.size();
        if (input.bridgeRecommendationAvailable) {
            input.bridgeLeftFrame = bridge.evidence.recommendation.candidate.leftFrame;
            input.bridgeRightFrame = bridge.evidence.recommendation.candidate.rightFrame;
        }
        ImportedReplayCompletionCore.Result completion =
                ImportedReplayCompletionCore.evaluate(input);
        long now = System.currentTimeMillis();
        File root = new File(context.getCacheDir(), "imported_replay_completion/" + now);
        if (!root.mkdirs() && !root.isDirectory()) {
            throw new IllegalStateException("No se pudo crear carpeta de cierre");
        }
        File report = new File(root, "imported_replay_completion.json");
        writeText(report, completion.canonicalJson());
        File packageFile = new File(root, "alpha53_software_roadmap_completion.zip");
        try (ZipOutputStream zip = new ZipOutputStream(
                new BufferedOutputStream(new FileOutputStream(packageFile)))) {
            copyFile(zip, report, "imported_replay_completion.json");
            if (preflight != null) copyFile(zip, preflight.preflightFile,
                    "capture_zip_preflight.json");
            if (multiscale != null) copyFile(zip, multiscale.reportFile,
                    "zip_multiscale_reprocess_diagnostics.json");
            if (tracks != null) copyFile(zip, tracks.trackFile,
                    "imported_multiview_tracks.json");
            if (seed != null) copyFile(zip, seed.reportFile,
                    "imported_seed_geometry.json");
            if (components != null) copyFile(zip, components.reportFile,
                    "imported_component_geometry.json");
            if (bridge != null) copyFile(zip, bridge.reportFile,
                    "imported_bridge_evidence.json");
            String boundary = "{\n"
                    + "\"schema\":\"skm-roadmap-boundary/1\",\n"
                    + "\"softwareReplayRoadmapComplete\":"
                    + completion.softwareReplayComplete + ",\n"
                    + "\"softwareRoadmapPercent\":"
                    + completion.softwareRoadmapPercent + ",\n"
                    + "\"globalMetricReconstructionReady\":false,\n"
                    + "\"industrialReady\":false,\n"
                    + "\"physicalWorkRequired\":true\n}";
            putText(zip, "roadmap_boundary.json", boundary);
        }
        return new Result(report, packageFile, completion.summary(), completion);
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
        public final ImportedReplayCompletionCore.Result completion;

        Result(File reportFile, File packageFile, String summary,
               ImportedReplayCompletionCore.Result completion) {
            this.reportFile = reportFile;
            this.packageFile = packageFile;
            this.summary = summary;
            this.completion = completion;
        }
    }
}

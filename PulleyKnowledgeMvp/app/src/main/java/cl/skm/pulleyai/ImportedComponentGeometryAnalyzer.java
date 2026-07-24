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

/** Persists component topology after tracks and local seed geometry. */
public final class ImportedComponentGeometryAnalyzer {
    private static final long MAX_ENTRY_BYTES = 64L * 1024L * 1024L;

    private ImportedComponentGeometryAnalyzer() {}

    public static Result process(Context context,
                                 ImportedTrackZipAnalyzer.Result tracks,
                                 ImportedSeedGeometryZipAnalyzer.Result seed,
                                 int acceptedFrames) throws Exception {
        if (context == null) throw new IllegalArgumentException("Contexto ausente");
        if (tracks == null || tracks.tracks == null) {
            throw new IllegalStateException("Tracks importados ausentes");
        }
        ImportedSeedGeometryCore.Result seedGeometry = seed == null ? null : seed.geometry;
        ImportedComponentGeometryCore.Result topology =
                ImportedComponentGeometryCore.analyze(
                        tracks.tracks, seedGeometry, acceptedFrames);
        long now = System.currentTimeMillis();
        File root = new File(context.getCacheDir(), "imported_components/" + now);
        if (!root.mkdirs() && !root.isDirectory()) {
            throw new IllegalStateException("No se pudo crear carpeta de componentes");
        }
        File report = new File(root, "imported_component_geometry.json");
        writeText(report, topology.canonicalJson());
        File packageFile = new File(root, "alpha51_component_geometry.zip");
        try (ZipOutputStream zip = new ZipOutputStream(
                new BufferedOutputStream(new FileOutputStream(packageFile)))) {
            copyFile(zip, report, "imported_component_geometry.json");
            if (tracks.trackFile != null && tracks.trackFile.isFile()) {
                copyFile(zip, tracks.trackFile, "imported_multiview_tracks.json");
            }
            if (seed != null && seed.reportFile != null && seed.reportFile.isFile()) {
                copyFile(zip, seed.reportFile, "imported_seed_geometry.json");
            }
            String release = "{\n"
                    + "\"schema\":\"skm-component-release-state/1\",\n"
                    + "\"globalConnected\":" + topology.globalConnected + ",\n"
                    + "\"localGeometryReady\":" + topology.localGeometryReady + ",\n"
                    + "\"metricScale\":false,\n"
                    + "\"industrialRelease\":false\n}";
            putText(zip, "release_state.json", release);
        }
        return new Result(report, packageFile, topology.summary(), topology);
    }

    private static void writeText(File file, String text) throws Exception {
        try (OutputStream output = new BufferedOutputStream(new FileOutputStream(file))) {
            output.write(text.getBytes(StandardCharsets.UTF_8));
        }
    }

    private static void copyFile(ZipOutputStream zip, File source, String name) throws Exception {
        if (source.length() > MAX_ENTRY_BYTES) {
            throw new IllegalStateException("Evidencia demasiado grande: " + source.getName());
        }
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
        public final ImportedComponentGeometryCore.Result topology;

        Result(File reportFile, File packageFile, String summary,
               ImportedComponentGeometryCore.Result topology) {
            this.reportFile = reportFile;
            this.packageFile = packageFile;
            this.summary = summary;
            this.topology = topology;
        }
    }
}

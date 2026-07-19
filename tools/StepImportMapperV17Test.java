import cl.ingenieria.photogrammetryai.core.foundation.CadReferenceModel;
import cl.ingenieria.photogrammetryai.core.foundation.CorePorts.ImportRequest;
import cl.ingenieria.photogrammetryai.core.foundation.CorePorts.ImportResult;
import cl.ingenieria.photogrammetryai.core.foundation.NativeStepReferenceImporter;
import cl.ingenieria.photogrammetryai.core.foundation.StepImportWireCodec;
import cl.ingenieria.photogrammetryai.core.foundation.StepReferenceImportMapper;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

public final class StepImportMapperV17Test {
    public static void main(String[] args) throws Exception {
        testNominalDiameterSelectsPrimaryBore();
        testExplicitFeatureIdOverridesAutomaticSelection();
        testMissingCylinderFailsSafely();
        testMissingFileIsRejectedBeforeNativeCall();
        testWireCodecPreservesUtf8Names();
        System.out.println("STEP import mapper v0.17 tests passed");
    }

    private static void testNominalDiameterSelectsPrimaryBore() throws Exception {
        Path step = temporaryStep("synthetic housing");
        NativeStepReferenceImporter importer = importerReturning(validPayload());
        Map<String, String> hints = new LinkedHashMap<>();
        hints.put(StepReferenceImportMapper.HINT_EXPECTED_BORE_DIAMETER_MM, "300.0");
        hints.put(StepReferenceImportMapper.HINT_REFERENCE_ID, "housing-right");

        ImportResult result = importer.importStep(new ImportRequest(
                step.toString(),
                "Soporte derecho",
                hints
        ));

        assertTrue(result.success(), result.message());
        CadReferenceModel model = result.model().orElseThrow(AssertionError::new);
        assertEquals("housing-right", model.id(), "reference id");
        assertNear(150.0, model.primaryBore().orElseThrow(AssertionError::new).radiusMm(), 1.0e-9,
                "expected diameter selection");
        assertTrue(model.sourceSha256().matches("[0-9a-f]{64}"), "SHA-256 must be persisted");
        assertEquals(3, model.features().size(), "all native CAD features must be retained");
    }

    private static void testExplicitFeatureIdOverridesAutomaticSelection() throws Exception {
        Path step = temporaryStep("explicit feature");
        NativeStepReferenceImporter importer = importerReturning(validPayload());
        Map<String, String> hints = new LinkedHashMap<>();
        hints.put(StepReferenceImportMapper.HINT_PRIMARY_BORE_FEATURE_ID, "cylinder_small");
        hints.put(StepReferenceImportMapper.HINT_EXPECTED_BORE_DIAMETER_MM, "300.0");

        ImportResult result = importer.importStep(new ImportRequest(
                step.toString(),
                "Soporte izquierdo",
                hints
        ));

        assertTrue(result.success(), result.message());
        assertNear(100.0,
                result.model().orElseThrow(AssertionError::new)
                        .primaryBore().orElseThrow(AssertionError::new).radiusMm(),
                1.0e-9,
                "explicit feature id must win"
        );
    }

    private static void testMissingCylinderFailsSafely() throws Exception {
        Path step = temporaryStep("plane only");
        String payload = header("OK", "", "Solo planos", "plane.step", "mesh:plane")
                + feature("plane_1", "Base", "PLANE", 0, 0, 0, 0, 0, 1, 0, 0, 0.6);
        NativeStepReferenceImporter importer = importerReturning(payload);
        ImportResult result = importer.importStep(new ImportRequest(
                step.toString(), "Sin alojamiento", Collections.emptyMap()
        ));
        assertFalse(result.success(), "STEP without a cylindrical bore must fail");
        assertEquals("NO_PRIMARY_BORE", result.errorCode().orElse(""), "error code");
    }

    private static void testMissingFileIsRejectedBeforeNativeCall() {
        final boolean[] called = {false};
        NativeStepReferenceImporter importer = new NativeStepReferenceImporter(
                (path, name) -> {
                    called[0] = true;
                    return validPayload();
                },
                new StepReferenceImportMapper()
        );
        ImportResult result = importer.importStep(new ImportRequest(
                "/definitely/not/present/model.step", "Ausente", Collections.emptyMap()
        ));
        assertFalse(result.success(), "missing file must fail");
        assertFalse(called[0], "native bridge must not run for invalid paths");
        assertEquals("STEP_NOT_FOUND", result.errorCode().orElse(""), "missing file error");
    }

    private static void testWireCodecPreservesUtf8Names() {
        String modelName = "Soporte transmisión Ñandú";
        String payload = header("OK", "", "Importación válida", "soporte ñ.step", "mesh:ñ")
                + feature("cylinder_1", modelName, "CYLINDER", 1, 2, 3, 1, 0, 0, 75, 220, 0.9);
        StepImportWireCodec.NativePayload decoded = StepImportWireCodec.decode(payload);
        assertEquals("soporte ñ.step", decoded.sourceFileName(), "UTF-8 filename");
        assertEquals(modelName, decoded.features().get(0).name(), "UTF-8 feature name");
    }

    private static NativeStepReferenceImporter importerReturning(String payload) {
        return new NativeStepReferenceImporter(
                (path, displayName) -> payload,
                new StepReferenceImportMapper()
        );
    }

    private static Path temporaryStep(String body) throws Exception {
        Path file = Files.createTempFile("pgai-step-v17-", ".step");
        Files.write(file, body.getBytes(StandardCharsets.UTF_8));
        file.toFile().deleteOnExit();
        return file;
    }

    private static String validPayload() {
        return header("OK", "", "Tres rasgos", "housing.step", "mesh:housing")
                + feature("cylinder_small", "Alojamiento secundario", "CYLINDER",
                0, 0, 0, 1, 0, 0, 100, 280, 0.85)
                + feature("cylinder_primary", "Alojamiento principal", "CYLINDER",
                0, 0, 0, 1, 0, 0, 150, 420, 0.95)
                + feature("plane_base", "Plano de apoyo", "PLANE",
                0, 0, -200, 0, 0, 1, 0, 0, 0.60);
    }

    private static String header(
            String status,
            String errorCode,
            String message,
            String sourceFile,
            String meshKey
    ) {
        return StepImportWireCodec.VERSION + "\nSTATUS\t" + status + "\t"
                + encoded(errorCode) + "\t" + encoded(message) + "\t"
                + encoded(sourceFile) + "\t" + encoded(meshKey) + "\n";
    }

    private static String feature(
            String id,
            String name,
            String type,
            double ox,
            double oy,
            double oz,
            double dx,
            double dy,
            double dz,
            double radius,
            double extent,
            double weight
    ) {
        return "FEATURE\t" + encoded(id) + "\t" + encoded(name) + "\t" + type + "\t"
                + ox + "\t" + oy + "\t" + oz + "\t"
                + dx + "\t" + dy + "\t" + dz + "\t"
                + radius + "\t" + extent + "\t" + weight + "\n";
    }

    private static String encoded(String value) {
        return StepImportWireCodec.encodeField(value);
    }

    private static void assertNear(double expected, double actual, double tolerance, String label) {
        if (Math.abs(expected - actual) > tolerance) {
            throw new AssertionError(label + ": expected " + expected + " but was " + actual);
        }
    }

    private static void assertEquals(Object expected, Object actual, String label) {
        if (!expected.equals(actual)) {
            throw new AssertionError(label + ": expected " + expected + " but was " + actual);
        }
    }

    private static void assertTrue(boolean condition, String message) {
        if (!condition) {
            throw new AssertionError(message);
        }
    }

    private static void assertFalse(boolean condition, String message) {
        assertTrue(!condition, message);
    }
}

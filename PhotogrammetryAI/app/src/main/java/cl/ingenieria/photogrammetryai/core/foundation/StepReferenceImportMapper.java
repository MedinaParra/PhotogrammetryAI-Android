package cl.ingenieria.photogrammetryai.core.foundation;

import cl.ingenieria.photogrammetryai.core.foundation.CadReferenceModel.Feature;
import cl.ingenieria.photogrammetryai.core.foundation.CadReferenceModel.FeatureType;
import cl.ingenieria.photogrammetryai.core.foundation.CorePorts.ImportRequest;
import cl.ingenieria.photogrammetryai.core.foundation.CorePorts.ImportResult;
import cl.ingenieria.photogrammetryai.core.foundation.StepImportWireCodec.NativeFeature;
import cl.ingenieria.photogrammetryai.core.foundation.StepImportWireCodec.NativePayload;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/** Converts native OpenCASCADE output into a validated, tracking-oriented CAD reference. */
public final class StepReferenceImportMapper {
    public static final String HINT_REFERENCE_ID = "referenceId";
    public static final String HINT_PRIMARY_BORE_FEATURE_ID = "primaryBoreFeatureId";
    public static final String HINT_EXPECTED_BORE_DIAMETER_MM = "expectedBoreDiameterMm";
    public static final String HINT_MINIMUM_BORE_DIAMETER_MM = "minimumBoreDiameterMm";
    public static final String HINT_MAXIMUM_BORE_DIAMETER_MM = "maximumBoreDiameterMm";

    public ImportResult map(NativePayload payload, ImportRequest request, String sourceSha256) {
        if (payload == null) {
            return ImportResult.failure("NATIVE_NULL_PAYLOAD", "El importador STEP no devolvió datos.");
        }
        if (!payload.success()) {
            return ImportResult.failure(
                    payload.errorCode() == null ? "STEP_IMPORT_FAILED" : payload.errorCode(),
                    payload.message()
            );
        }
        if (payload.features().isEmpty()) {
            return ImportResult.failure("NO_CAD_FEATURES", "El STEP no contiene superficies CAD utilizables.");
        }

        Selection selection;
        try {
            selection = selectPrimaryBore(payload.features(), request.hints());
        } catch (IllegalArgumentException error) {
            return ImportResult.failure("INVALID_IMPORT_HINT", error.getMessage());
        }
        if (selection == null) {
            return ImportResult.failure(
                    "NO_PRIMARY_BORE",
                    "No se encontró un cilindro de alojamiento compatible con los criterios indicados."
            );
        }

        List<Feature> mappedFeatures = new ArrayList<>();
        try {
            for (NativeFeature nativeFeature : payload.features()) {
                FeatureType type = FeatureType.valueOf(nativeFeature.type().toUpperCase(Locale.ROOT));
                boolean primary = nativeFeature.id().equals(selection.feature.id());
                mappedFeatures.add(new Feature(
                        nativeFeature.id(),
                        nativeFeature.name(),
                        type,
                        nativeFeature.origin(),
                        nativeFeature.direction(),
                        Math.max(0.0, nativeFeature.radiusMm()),
                        Math.max(0.0, nativeFeature.extentMm()),
                        clamp(nativeFeature.trackingWeight(), 0.05, 1.0),
                        primary
                ));
            }
        } catch (IllegalArgumentException error) {
            return ImportResult.failure("INVALID_NATIVE_FEATURE", error.getMessage());
        }

        String sourceFileName = payload.sourceFileName().trim().isEmpty()
                ? new File(request.localFilePath()).getName()
                : payload.sourceFileName();
        String referenceId = request.hints().get(HINT_REFERENCE_ID);
        if (referenceId == null || referenceId.trim().isEmpty()) {
            referenceId = defaultReferenceId(request.displayName(), sourceSha256);
        }
        String renderMeshKey = payload.renderMeshKey().trim().isEmpty()
                ? "step-mesh:" + sourceSha256
                : payload.renderMeshKey();

        CadReferenceModel model = new CadReferenceModel(
                referenceId,
                request.displayName(),
                sourceFileName,
                sourceSha256,
                CadReferenceModel.Unit.MILLIMETRE,
                mappedFeatures,
                renderMeshKey
        );
        return ImportResult.success(
                model,
                "STEP importado: " + mappedFeatures.size() + " rasgos; alojamiento primario Ø"
                        + format(selection.feature.radiusMm() * 2.0) + " mm."
        );
    }

    private static Selection selectPrimaryBore(List<NativeFeature> features, Map<String, String> hints) {
        List<NativeFeature> cylinders = new ArrayList<>();
        for (NativeFeature feature : features) {
            if ("CYLINDER".equalsIgnoreCase(feature.type()) && feature.radiusMm() > 0.0) {
                cylinders.add(feature);
            }
        }
        if (cylinders.isEmpty()) {
            return null;
        }

        String requestedId = trimToNull(hints.get(HINT_PRIMARY_BORE_FEATURE_ID));
        if (requestedId != null) {
            for (NativeFeature cylinder : cylinders) {
                if (requestedId.equals(cylinder.id())) {
                    return new Selection(cylinder, 0.0);
                }
            }
            throw new IllegalArgumentException(
                    "El rasgo solicitado como alojamiento primario no existe: " + requestedId
            );
        }

        Double expectedDiameter = optionalPositive(hints, HINT_EXPECTED_BORE_DIAMETER_MM);
        Double minimumDiameter = optionalPositive(hints, HINT_MINIMUM_BORE_DIAMETER_MM);
        Double maximumDiameter = optionalPositive(hints, HINT_MAXIMUM_BORE_DIAMETER_MM);
        if (minimumDiameter != null && maximumDiameter != null && minimumDiameter > maximumDiameter) {
            throw new IllegalArgumentException("El diámetro mínimo no puede superar al máximo.");
        }

        Selection best = null;
        for (NativeFeature cylinder : cylinders) {
            double diameter = cylinder.radiusMm() * 2.0;
            if (minimumDiameter != null && diameter < minimumDiameter) {
                continue;
            }
            if (maximumDiameter != null && diameter > maximumDiameter) {
                continue;
            }

            double score;
            if (expectedDiameter != null) {
                score = Math.abs(diameter - expectedDiameter) / Math.max(1.0, expectedDiameter);
            } else {
                // Without a nominal diameter, prefer large and long cylindrical faces. Bearing
                // housing bores generally dominate other exact cylindrical details in the STEP.
                score = -(cylinder.radiusMm() * Math.max(1.0, cylinder.extentMm())
                        * clamp(cylinder.trackingWeight(), 0.05, 1.0));
            }
            if (best == null || score < best.score) {
                best = new Selection(cylinder, score);
            }
        }
        return best;
    }

    private static Double optionalPositive(Map<String, String> hints, String key) {
        String raw = trimToNull(hints.get(key));
        if (raw == null) {
            return null;
        }
        try {
            double value = Double.parseDouble(raw);
            if (!Double.isFinite(value) || value <= 0.0) {
                throw new IllegalArgumentException(key + " debe ser un número positivo.");
            }
            return value;
        } catch (NumberFormatException error) {
            throw new IllegalArgumentException(key + " no es un número válido.", error);
        }
    }

    private static String defaultReferenceId(String displayName, String sha256) {
        String normalized = displayName.toLowerCase(Locale.ROOT)
                .replaceAll("[^a-z0-9]+", "-")
                .replaceAll("(^-+|-+$)", "");
        if (normalized.isEmpty()) {
            normalized = "step-reference";
        }
        String suffix = sha256.length() <= 12 ? sha256 : sha256.substring(0, 12);
        return normalized + "-" + suffix;
    }

    private static String trimToNull(String value) {
        if (value == null || value.trim().isEmpty()) {
            return null;
        }
        return value.trim();
    }

    private static double clamp(double value, double minimum, double maximum) {
        if (!Double.isFinite(value)) {
            throw new IllegalArgumentException("El peso de tracking debe ser finito.");
        }
        return Math.max(minimum, Math.min(maximum, value));
    }

    private static String format(double value) {
        return String.format(Locale.ROOT, "%.3f", value);
    }

    private static final class Selection {
        private final NativeFeature feature;
        private final double score;

        private Selection(NativeFeature feature, double score) {
            this.feature = feature;
            this.score = score;
        }
    }
}

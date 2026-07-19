package cl.ingenieria.photogrammetryai.core.foundation;

import static cl.ingenieria.photogrammetryai.core.foundation.CoreMath.Vec3;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * Lightweight representation extracted once from a STEP model.
 *
 * <p>The original STEP/B-Rep stays outside the real-time loop. The tracker consumes these
 * exact semantic features plus a separately generated render mesh.</p>
 */
public final class CadReferenceModel {
    public enum Unit {
        MILLIMETRE
    }

    public enum FeatureType {
        CYLINDER,
        PLANE,
        CIRCLE,
        AXIS,
        POINT
    }

    public static final class Feature {
        private final String id;
        private final String name;
        private final FeatureType type;
        private final Vec3 origin;
        private final Vec3 direction;
        private final double radiusMm;
        private final double extentMm;
        private final double trackingWeight;
        private final boolean primary;

        public Feature(
                String id,
                String name,
                FeatureType type,
                Vec3 origin,
                Vec3 direction,
                double radiusMm,
                double extentMm,
                double trackingWeight,
                boolean primary
        ) {
            this.id = requireText(id, "id");
            this.name = requireText(name, "name");
            this.type = Objects.requireNonNull(type, "type");
            this.origin = Objects.requireNonNull(origin, "origin");
            this.direction = normalizeDirection(type, direction);
            this.radiusMm = requireNonNegativeFinite(radiusMm, "radiusMm");
            this.extentMm = requireNonNegativeFinite(extentMm, "extentMm");
            this.trackingWeight = requireRange(trackingWeight, 0.0, 1.0, "trackingWeight");
            this.primary = primary;
        }

        public static Feature primaryBore(
                String id,
                String name,
                Vec3 centre,
                Vec3 axis,
                double radiusMm,
                double lengthMm
        ) {
            return new Feature(
                    id,
                    name,
                    FeatureType.CYLINDER,
                    centre,
                    axis,
                    radiusMm,
                    lengthMm,
                    1.0,
                    true
            );
        }

        public static Feature mountingPlane(
                String id,
                String name,
                Vec3 point,
                Vec3 normal,
                double trackingWeight
        ) {
            return new Feature(
                    id,
                    name,
                    FeatureType.PLANE,
                    point,
                    normal,
                    0.0,
                    0.0,
                    trackingWeight,
                    false
            );
        }

        public String id() {
            return id;
        }

        public String name() {
            return name;
        }

        public FeatureType type() {
            return type;
        }

        public Vec3 origin() {
            return origin;
        }

        public Vec3 direction() {
            return direction;
        }

        public double radiusMm() {
            return radiusMm;
        }

        public double extentMm() {
            return extentMm;
        }

        public double trackingWeight() {
            return trackingWeight;
        }

        public boolean primary() {
            return primary;
        }

        private static Vec3 normalizeDirection(FeatureType type, Vec3 direction) {
            if (type == FeatureType.POINT) {
                return direction == null ? Vec3.Z : direction.normalized();
            }
            return Objects.requireNonNull(direction, "direction").normalized();
        }
    }

    private final String id;
    private final String displayName;
    private final String sourceFileName;
    private final String sourceSha256;
    private final Unit unit;
    private final List<Feature> features;
    private final String renderMeshKey;

    public CadReferenceModel(
            String id,
            String displayName,
            String sourceFileName,
            String sourceSha256,
            Unit unit,
            List<Feature> features,
            String renderMeshKey
    ) {
        this.id = requireText(id, "id");
        this.displayName = requireText(displayName, "displayName");
        this.sourceFileName = requireText(sourceFileName, "sourceFileName");
        this.sourceSha256 = requireText(sourceSha256, "sourceSha256");
        this.unit = Objects.requireNonNull(unit, "unit");
        Objects.requireNonNull(features, "features");
        if (features.isEmpty()) {
            throw new IllegalArgumentException("A CAD reference requires at least one feature");
        }
        this.features = Collections.unmodifiableList(new ArrayList<>(features));
        this.renderMeshKey = requireText(renderMeshKey, "renderMeshKey");
    }

    public String id() {
        return id;
    }

    public String displayName() {
        return displayName;
    }

    public String sourceFileName() {
        return sourceFileName;
    }

    public String sourceSha256() {
        return sourceSha256;
    }

    public Unit unit() {
        return unit;
    }

    public List<Feature> features() {
        return features;
    }

    public String renderMeshKey() {
        return renderMeshKey;
    }

    public Optional<Feature> primaryBore() {
        for (Feature feature : features) {
            if (feature.primary() && feature.type() == FeatureType.CYLINDER) {
                return Optional.of(feature);
            }
        }
        return Optional.empty();
    }

    public List<Feature> featuresOfType(FeatureType type) {
        Objects.requireNonNull(type, "type");
        List<Feature> matches = new ArrayList<>();
        for (Feature feature : features) {
            if (feature.type() == type) {
                matches.add(feature);
            }
        }
        return Collections.unmodifiableList(matches);
    }

    private static String requireText(String value, String name) {
        if (value == null || value.trim().isEmpty()) {
            throw new IllegalArgumentException(name + " must not be blank");
        }
        return value;
    }

    private static double requireNonNegativeFinite(double value, String name) {
        if (!Double.isFinite(value) || value < 0.0) {
            throw new IllegalArgumentException(name + " must be finite and non-negative");
        }
        return value;
    }

    private static double requireRange(double value, double min, double max, String name) {
        if (!Double.isFinite(value) || value < min || value > max) {
            throw new IllegalArgumentException(name + " must be between " + min + " and " + max);
        }
        return value;
    }
}

package cl.ingenieria.photogrammetryai.core.fewview;

import cl.ingenieria.photogrammetryai.core.foundation.CoreMath;
import cl.ingenieria.photogrammetryai.core.foundation.CoreMath.Vec3;
import cl.ingenieria.photogrammetryai.core.fewview.FewViewGeometry.ReconstructedPoint;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * Fits a pulley shell while keeping the shaft axis fixed by the imported bearing-support STEP.
 *
 * <p>This constraint converts a difficult free-cylinder problem into robust one-dimensional radius
 * and axial-span estimation, which is substantially better conditioned for sparse mobile views.</p>
 */
public final class AxisConstrainedPulleyEstimator {
    public static final class Config {
        private final int minimumPointCount;
        private final double madMultiplier;
        private final double minimumRadialToleranceMm;
        private final double axialTrimFraction;
        private final double maximumRelativeResidual;

        public Config(
                int minimumPointCount,
                double madMultiplier,
                double minimumRadialToleranceMm,
                double axialTrimFraction,
                double maximumRelativeResidual
        ) {
            if (minimumPointCount < 4) {
                throw new IllegalArgumentException("minimumPointCount must be at least 4");
            }
            this.minimumPointCount = minimumPointCount;
            this.madMultiplier = requirePositive(madMultiplier, "madMultiplier");
            this.minimumRadialToleranceMm = requirePositive(
                    minimumRadialToleranceMm,
                    "minimumRadialToleranceMm"
            );
            if (!Double.isFinite(axialTrimFraction)
                    || axialTrimFraction < 0.0
                    || axialTrimFraction >= 0.25) {
                throw new IllegalArgumentException("axialTrimFraction must be in [0, 0.25)");
            }
            this.axialTrimFraction = axialTrimFraction;
            this.maximumRelativeResidual = requirePositive(
                    maximumRelativeResidual,
                    "maximumRelativeResidual"
            );
        }

        public static Config mobileDefaults() {
            return new Config(12, 3.5, 3.0, 0.02, 0.05);
        }
    }

    public static final class PulleyCylinder {
        private final Vec3 centreWorldMm;
        private final Vec3 axisWorld;
        private final double radiusMm;
        private final double lengthMm;
        private final double startCoordinateMm;
        private final double endCoordinateMm;
        private final double radialRmsMm;
        private final int inlierPointCount;
        private final int inputPointCount;
        private final double confidence;

        private PulleyCylinder(
                Vec3 centreWorldMm,
                Vec3 axisWorld,
                double radiusMm,
                double lengthMm,
                double startCoordinateMm,
                double endCoordinateMm,
                double radialRmsMm,
                int inlierPointCount,
                int inputPointCount,
                double confidence
        ) {
            this.centreWorldMm = centreWorldMm;
            this.axisWorld = axisWorld;
            this.radiusMm = radiusMm;
            this.lengthMm = lengthMm;
            this.startCoordinateMm = startCoordinateMm;
            this.endCoordinateMm = endCoordinateMm;
            this.radialRmsMm = radialRmsMm;
            this.inlierPointCount = inlierPointCount;
            this.inputPointCount = inputPointCount;
            this.confidence = confidence;
        }

        public Vec3 centreWorldMm() {
            return centreWorldMm;
        }

        public Vec3 axisWorld() {
            return axisWorld;
        }

        public double radiusMm() {
            return radiusMm;
        }

        public double diameterMm() {
            return 2.0 * radiusMm;
        }

        public double lengthMm() {
            return lengthMm;
        }

        public double startCoordinateMm() {
            return startCoordinateMm;
        }

        public double endCoordinateMm() {
            return endCoordinateMm;
        }

        public double radialRmsMm() {
            return radialRmsMm;
        }

        public int inlierPointCount() {
            return inlierPointCount;
        }

        public int inputPointCount() {
            return inputPointCount;
        }

        public double confidence() {
            return confidence;
        }
    }

    private final Config config;

    public AxisConstrainedPulleyEstimator(Config config) {
        this.config = Objects.requireNonNull(config, "config");
    }

    public Optional<PulleyCylinder> estimate(
            Vec3 shaftOriginWorldMm,
            Vec3 shaftAxisWorld,
            List<ReconstructedPoint> shellPoints
    ) {
        Objects.requireNonNull(shaftOriginWorldMm, "shaftOriginWorldMm");
        Vec3 axis = Objects.requireNonNull(shaftAxisWorld, "shaftAxisWorld").normalized();
        Objects.requireNonNull(shellPoints, "shellPoints");
        if (shellPoints.size() < config.minimumPointCount) {
            return Optional.empty();
        }

        List<Sample> samples = new ArrayList<>();
        for (ReconstructedPoint point : shellPoints) {
            Objects.requireNonNull(point, "point");
            Vec3 relative = point.worldPointMm().subtract(shaftOriginWorldMm);
            double axial = relative.dot(axis);
            Vec3 radialVector = relative.subtract(axis.scale(axial));
            double radialDistance = radialVector.norm();
            if (radialDistance > 1.0e-6 && Double.isFinite(radialDistance)) {
                samples.add(new Sample(
                        axial,
                        radialDistance,
                        Math.max(0.05, point.confidence())
                ));
            }
        }
        if (samples.size() < config.minimumPointCount) {
            return Optional.empty();
        }

        List<Double> radii = new ArrayList<>();
        for (Sample sample : samples) {
            radii.add(sample.radius);
        }
        double medianRadius = median(radii);
        List<Double> absoluteDeviations = new ArrayList<>();
        for (double radius : radii) {
            absoluteDeviations.add(Math.abs(radius - medianRadius));
        }
        double mad = median(absoluteDeviations);
        double robustSigma = 1.4826 * mad;
        double tolerance = Math.max(
                config.minimumRadialToleranceMm,
                config.madMultiplier * robustSigma
        );

        List<Sample> inliers = new ArrayList<>();
        for (Sample sample : samples) {
            if (Math.abs(sample.radius - medianRadius) <= tolerance) {
                inliers.add(sample);
            }
        }
        if (inliers.size() < config.minimumPointCount) {
            return Optional.empty();
        }

        double weightedRadiusSum = 0.0;
        double weightSum = 0.0;
        for (Sample sample : inliers) {
            double residual = Math.abs(sample.radius - medianRadius);
            double robustWeight = residual <= tolerance * 0.5
                    ? 1.0
                    : Math.max(0.05, (tolerance - residual) / (tolerance * 0.5));
            double weight = sample.confidence * robustWeight;
            weightedRadiusSum += weight * sample.radius;
            weightSum += weight;
        }
        if (weightSum <= 1.0e-12) {
            return Optional.empty();
        }
        double radius = weightedRadiusSum / weightSum;

        double squaredResidualSum = 0.0;
        for (Sample sample : inliers) {
            double residual = sample.radius - radius;
            squaredResidualSum += residual * residual;
        }
        double radialRms = Math.sqrt(squaredResidualSum / inliers.size());
        if (radialRms / radius > config.maximumRelativeResidual) {
            return Optional.empty();
        }

        List<Double> axialCoordinates = new ArrayList<>();
        for (Sample sample : inliers) {
            axialCoordinates.add(sample.axial);
        }
        Collections.sort(axialCoordinates);
        double start = quantile(axialCoordinates, config.axialTrimFraction);
        double end = quantile(axialCoordinates, 1.0 - config.axialTrimFraction);
        double length = end - start;
        if (!Double.isFinite(length) || length <= 1.0e-6) {
            return Optional.empty();
        }

        double centreCoordinate = 0.5 * (start + end);
        Vec3 centre = shaftOriginWorldMm.add(axis.scale(centreCoordinate));
        double coverage = inliers.size() / (double) samples.size();
        double residualQuality = Math.exp(-radialRms / Math.max(1.0, radius * 0.01));
        double averageInputConfidence = 0.0;
        for (Sample inlier : inliers) {
            averageInputConfidence += inlier.confidence;
        }
        averageInputConfidence /= inliers.size();
        double countQuality = Math.min(1.0, inliers.size() / 30.0);
        double confidence = CoreMath.clamp01(
                coverage
                        * residualQuality
                        * averageInputConfidence
                        * (0.65 + 0.35 * countQuality)
        );

        return Optional.of(new PulleyCylinder(
                centre,
                axis,
                radius,
                length,
                start,
                end,
                radialRms,
                inliers.size(),
                samples.size(),
                confidence
        ));
    }

    private static double median(List<Double> values) {
        if (values.isEmpty()) {
            throw new IllegalArgumentException("Cannot calculate median of an empty list");
        }
        List<Double> sorted = new ArrayList<>(values);
        Collections.sort(sorted);
        int middle = sorted.size() / 2;
        return (sorted.size() & 1) == 1
                ? sorted.get(middle)
                : 0.5 * (sorted.get(middle - 1) + sorted.get(middle));
    }

    private static double quantile(List<Double> sortedValues, double fraction) {
        if (sortedValues.isEmpty()) {
            throw new IllegalArgumentException("Cannot calculate quantile of an empty list");
        }
        if (!Double.isFinite(fraction) || fraction < 0.0 || fraction > 1.0) {
            throw new IllegalArgumentException("fraction must be between 0 and 1");
        }
        double position = fraction * (sortedValues.size() - 1);
        int lower = (int) Math.floor(position);
        int upper = (int) Math.ceil(position);
        if (lower == upper) {
            return sortedValues.get(lower);
        }
        double alpha = position - lower;
        return sortedValues.get(lower) * (1.0 - alpha) + sortedValues.get(upper) * alpha;
    }

    private static double requirePositive(double value, String name) {
        if (!Double.isFinite(value) || value <= 0.0) {
            throw new IllegalArgumentException(name + " must be positive and finite");
        }
        return value;
    }

    private static final class Sample {
        private final double axial;
        private final double radius;
        private final double confidence;

        private Sample(double axial, double radius, double confidence) {
            this.axial = axial;
            this.radius = radius;
            this.confidence = confidence;
        }
    }
}

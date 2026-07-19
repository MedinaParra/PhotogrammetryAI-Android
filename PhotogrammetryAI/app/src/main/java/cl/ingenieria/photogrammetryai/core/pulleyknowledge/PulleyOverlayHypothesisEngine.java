package cl.ingenieria.photogrammetryai.core.pulleyknowledge;

import cl.ingenieria.photogrammetryai.core.foundation.CoreMath.RigidPose;
import cl.ingenieria.photogrammetryai.core.foundation.CoreMath.Vec3;
import cl.ingenieria.photogrammetryai.core.fewview.AxisConstrainedPulleyEstimator.PulleyCylinder;
import cl.ingenieria.photogrammetryai.core.pulleyknowledge.PulleyKnowledgeBase.Component;
import cl.ingenieria.photogrammetryai.core.pulleyknowledge.PulleyKnowledgeBase.ReferenceCase;
import cl.ingenieria.photogrammetryai.core.pulleyknowledge.PulleySimilarityEngine.CandidateKind;
import cl.ingenieria.photogrammetryai.core.pulleyknowledge.PulleySimilarityEngine.Match;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/**
 * Builds renderer-neutral overlay hypotheses from a reconstructed shell and a similar pulley case.
 * Similar STEP files may be uniformly scaled for preview only; metrology stays parametric/observed.
 */
public final class PulleyOverlayHypothesisEngine {
    public enum OverlayMode {
        RIGID_REFERENCE,
        UNIFORM_PREVIEW_SCALE,
        PARAMETRIC_REBUILD
    }

    public enum PrimitiveType {
        SHELL_CYLINDER,
        LAGGING_CYLINDER,
        SHAFT_CYLINDER,
        LEFT_END_DISC,
        RIGHT_END_DISC,
        LEFT_HUB,
        RIGHT_HUB,
        LEFT_BEARING_CENTRE,
        RIGHT_BEARING_CENTRE
    }

    public enum ConstraintType {
        COAXIAL_WITH_SHAFT,
        PERPENDICULAR_TO_SHAFT,
        CONCENTRIC_WITH_SHELL,
        CENTRED_BETWEEN_END_DISCS,
        SUPPORT_CENTRES_ON_SHAFT,
        REFERENCE_STEP_REQUIRES_ANCHOR
    }

    public static final class Config {
        private final double rigidMaximumDimensionError;
        private final double previewMaximumDimensionError;
        private final double minimumRigidMatchScore;
        private final double minimumPreviewMatchScore;

        public Config(
                double rigidMaximumDimensionError,
                double previewMaximumDimensionError,
                double minimumRigidMatchScore,
                double minimumPreviewMatchScore
        ) {
            this.rigidMaximumDimensionError = requireFraction(
                    rigidMaximumDimensionError,
                    "rigidMaximumDimensionError"
            );
            this.previewMaximumDimensionError = requireFraction(
                    previewMaximumDimensionError,
                    "previewMaximumDimensionError"
            );
            if (previewMaximumDimensionError < rigidMaximumDimensionError) {
                throw new IllegalArgumentException(
                        "previewMaximumDimensionError must be >= rigidMaximumDimensionError"
                );
            }
            this.minimumRigidMatchScore = requireConfidence(
                    minimumRigidMatchScore,
                    "minimumRigidMatchScore"
            );
            this.minimumPreviewMatchScore = requireConfidence(
                    minimumPreviewMatchScore,
                    "minimumPreviewMatchScore"
            );
        }

        public static Config industrialDefaults() {
            return new Config(0.025, 0.18, 0.86, 0.66);
        }
    }

    public static final class ShellObservation {
        private final Vec3 centreWorldMm;
        private final Vec3 axisWorld;
        private final double diameterMm;
        private final double faceWidthMm;
        private final double confidence;

        public ShellObservation(
                Vec3 centreWorldMm,
                Vec3 axisWorld,
                double diameterMm,
                double faceWidthMm,
                double confidence
        ) {
            this.centreWorldMm = Objects.requireNonNull(centreWorldMm, "centreWorldMm");
            this.axisWorld = Objects.requireNonNull(axisWorld, "axisWorld").normalized();
            this.diameterMm = requirePositive(diameterMm, "diameterMm");
            this.faceWidthMm = requirePositive(faceWidthMm, "faceWidthMm");
            this.confidence = requireConfidence(confidence, "confidence");
        }

        public static ShellObservation from(PulleyCylinder cylinder) {
            Objects.requireNonNull(cylinder, "cylinder");
            return new ShellObservation(
                    cylinder.centreWorldMm(),
                    cylinder.axisWorld(),
                    cylinder.diameterMm(),
                    cylinder.lengthMm(),
                    cylinder.confidence()
            );
        }

        public Vec3 centreWorldMm() { return centreWorldMm; }
        public Vec3 axisWorld() { return axisWorld; }
        public double diameterMm() { return diameterMm; }
        public double faceWidthMm() { return faceWidthMm; }
        public double confidence() { return confidence; }
    }

    public static final class SupportEvidence {
        private final Vec3 leftBearingCentreWorldMm;
        private final Vec3 rightBearingCentreWorldMm;
        private final double confidence;

        public SupportEvidence(
                Vec3 leftBearingCentreWorldMm,
                Vec3 rightBearingCentreWorldMm,
                double confidence
        ) {
            this.leftBearingCentreWorldMm = leftBearingCentreWorldMm;
            this.rightBearingCentreWorldMm = rightBearingCentreWorldMm;
            if (leftBearingCentreWorldMm == null && rightBearingCentreWorldMm == null) {
                throw new IllegalArgumentException("At least one bearing centre is required");
            }
            this.confidence = requireConfidence(confidence, "confidence");
        }

        public Optional<Vec3> leftBearingCentreWorldMm() {
            return Optional.ofNullable(leftBearingCentreWorldMm);
        }

        public Optional<Vec3> rightBearingCentreWorldMm() {
            return Optional.ofNullable(rightBearingCentreWorldMm);
        }

        public double confidence() { return confidence; }
    }

    public static final class PrimitiveHypothesis {
        private final PrimitiveType type;
        private final Vec3 centreWorldMm;
        private final Vec3 axisWorld;
        private final double diameterMm;
        private final double lengthMm;
        private final double confidence;
        private final boolean measured;

        private PrimitiveHypothesis(
                PrimitiveType type,
                Vec3 centreWorldMm,
                Vec3 axisWorld,
                double diameterMm,
                double lengthMm,
                double confidence,
                boolean measured
        ) {
            this.type = Objects.requireNonNull(type, "type");
            this.centreWorldMm = Objects.requireNonNull(centreWorldMm, "centreWorldMm");
            this.axisWorld = Objects.requireNonNull(axisWorld, "axisWorld").normalized();
            this.diameterMm = requirePositive(diameterMm, "diameterMm");
            if (!Double.isFinite(lengthMm) || lengthMm < 0.0) {
                throw new IllegalArgumentException("lengthMm must be finite and non-negative");
            }
            this.lengthMm = lengthMm;
            this.confidence = requireConfidence(confidence, "confidence");
            this.measured = measured;
        }

        public PrimitiveType type() { return type; }
        public Vec3 centreWorldMm() { return centreWorldMm; }
        public Vec3 axisWorld() { return axisWorld; }
        public double diameterMm() { return diameterMm; }
        public double lengthMm() { return lengthMm; }
        public double confidence() { return confidence; }
        public boolean measured() { return measured; }
    }

    public static final class ReferencePlacement {
        private final String stepReferenceId;
        private final RigidPose worldFromReferenceAxisFrame;
        private final double uniformPreviewScale;
        private final boolean previewOnly;

        private ReferencePlacement(
                String stepReferenceId,
                RigidPose worldFromReferenceAxisFrame,
                double uniformPreviewScale,
                boolean previewOnly
        ) {
            this.stepReferenceId = requireText(stepReferenceId, "stepReferenceId");
            this.worldFromReferenceAxisFrame = Objects.requireNonNull(
                    worldFromReferenceAxisFrame,
                    "worldFromReferenceAxisFrame"
            );
            this.uniformPreviewScale = requirePositive(
                    uniformPreviewScale,
                    "uniformPreviewScale"
            );
            this.previewOnly = previewOnly;
        }

        public String stepReferenceId() { return stepReferenceId; }
        public RigidPose worldFromReferenceAxisFrame() { return worldFromReferenceAxisFrame; }
        public double uniformPreviewScale() { return uniformPreviewScale; }
        public boolean previewOnly() { return previewOnly; }
    }

    public static final class OverlayHypothesis {
        private final String candidateId;
        private final OverlayMode mode;
        private final List<PrimitiveHypothesis> primitives;
        private final List<ConstraintType> constraints;
        private final List<String> warnings;
        private final ReferencePlacement referencePlacement;
        private final double confidence;

        private OverlayHypothesis(
                String candidateId,
                OverlayMode mode,
                List<PrimitiveHypothesis> primitives,
                List<ConstraintType> constraints,
                List<String> warnings,
                ReferencePlacement referencePlacement,
                double confidence
        ) {
            this.candidateId = candidateId;
            this.mode = mode;
            this.primitives = Collections.unmodifiableList(new ArrayList<>(primitives));
            this.constraints = Collections.unmodifiableList(new ArrayList<>(constraints));
            this.warnings = Collections.unmodifiableList(new ArrayList<>(warnings));
            this.referencePlacement = referencePlacement;
            this.confidence = confidence;
        }

        public String candidateId() { return candidateId; }
        public OverlayMode mode() { return mode; }
        public List<PrimitiveHypothesis> primitives() { return primitives; }
        public List<ConstraintType> constraints() { return constraints; }
        public List<String> warnings() { return warnings; }
        public Optional<ReferencePlacement> referencePlacement() {
            return Optional.ofNullable(referencePlacement);
        }
        public double confidence() { return confidence; }
    }

    private final Config config;

    public PulleyOverlayHypothesisEngine(Config config) {
        this.config = Objects.requireNonNull(config, "config");
    }

    public OverlayHypothesis build(
            PulleyKnowledgeBase knowledgeBase,
            ShellObservation shell,
            Optional<SupportEvidence> supportEvidence,
            Match match
    ) {
        Objects.requireNonNull(knowledgeBase, "knowledgeBase");
        Objects.requireNonNull(shell, "shell");
        Objects.requireNonNull(supportEvidence, "supportEvidence");
        Objects.requireNonNull(match, "match");

        Optional<ReferenceCase> referenceOptional = match.candidateKind() == CandidateKind.VERIFIED_REFERENCE
                ? knowledgeBase.referenceCase(match.candidateId())
                : Optional.empty();
        Map<String, Double> dimensions = referenceOptional
                .map(ReferenceCase::dimensionsMm)
                .orElse(Collections.emptyMap());

        DimensionFit fit = dimensionFit(shell, dimensions);
        OverlayMode mode = chooseMode(match, referenceOptional, fit);
        List<String> warnings = new ArrayList<>();
        if (mode == OverlayMode.UNIFORM_PREVIEW_SCALE) {
            warnings.add(
                    "The similar STEP is uniformly scaled for visual guidance only; do not use its scaled dimensions as metrology."
            );
        }
        if (mode == OverlayMode.PARAMETRIC_REBUILD) {
            warnings.add(
                    "The reference is used only as a topology prior; measured shell and support evidence control the reconstruction."
            );
        }

        List<PrimitiveHypothesis> primitives = new ArrayList<>();
        primitives.add(primitive(
                PrimitiveType.SHELL_CYLINDER,
                shell.centreWorldMm(),
                shell.axisWorld(),
                shell.diameterMm(),
                shell.faceWidthMm(),
                shell.confidence(),
                true
        ));
        addEndDiscs(primitives, shell);
        addLagging(primitives, shell, dimensions, match.score());
        addShaft(primitives, shell, dimensions, match.score());
        addHubs(primitives, shell, dimensions, match.score());
        supportEvidence.ifPresent(evidence -> addSupports(primitives, shell, evidence));

        List<ConstraintType> constraints = new ArrayList<>();
        constraints.add(ConstraintType.COAXIAL_WITH_SHAFT);
        constraints.add(ConstraintType.PERPENDICULAR_TO_SHAFT);
        constraints.add(ConstraintType.CONCENTRIC_WITH_SHELL);
        constraints.add(ConstraintType.CENTRED_BETWEEN_END_DISCS);
        if (supportEvidence.isPresent()) {
            constraints.add(ConstraintType.SUPPORT_CENTRES_ON_SHAFT);
        }

        ReferencePlacement placement = null;
        if (match.stepReferenceId().isPresent()) {
            constraints.add(ConstraintType.REFERENCE_STEP_REQUIRES_ANCHOR);
            placement = new ReferencePlacement(
                    match.stepReferenceId().get(),
                    axisFramePose(shell.centreWorldMm(), shell.axisWorld()),
                    mode == OverlayMode.UNIFORM_PREVIEW_SCALE ? fit.uniformScale : 1.0,
                    mode != OverlayMode.RIGID_REFERENCE
            );
            warnings.add(
                    "The STEP local origin must be mapped to its declared shaft-axis anchor before rendering."
            );
        }

        double supportConfidence = supportEvidence.map(SupportEvidence::confidence).orElse(0.55);
        double modeFactor = mode == OverlayMode.RIGID_REFERENCE
                ? 1.0
                : mode == OverlayMode.UNIFORM_PREVIEW_SCALE ? 0.82 : 0.70;
        double confidence = clamp01(
                shell.confidence()
                        * (0.55 + 0.25 * match.score() + 0.20 * supportConfidence)
                        * modeFactor
        );
        return new OverlayHypothesis(
                match.candidateId(),
                mode,
                primitives,
                constraints,
                warnings,
                placement,
                confidence
        );
    }

    private OverlayMode chooseMode(
            Match match,
            Optional<ReferenceCase> reference,
            DimensionFit fit
    ) {
        if (reference.isPresent()
                && match.score() >= config.minimumRigidMatchScore
                && fit.comparedDimensions >= 2
                && fit.maximumRelativeError <= config.rigidMaximumDimensionError) {
            return OverlayMode.RIGID_REFERENCE;
        }
        if (reference.isPresent()
                && match.score() >= config.minimumPreviewMatchScore
                && fit.comparedDimensions >= 1
                && fit.maximumRelativeError <= config.previewMaximumDimensionError) {
            return OverlayMode.UNIFORM_PREVIEW_SCALE;
        }
        return OverlayMode.PARAMETRIC_REBUILD;
    }

    private static DimensionFit dimensionFit(
            ShellObservation shell,
            Map<String, Double> referenceDimensions
    ) {
        List<Double> scales = new ArrayList<>();
        List<Double> relativeErrors = new ArrayList<>();
        Double referenceDiameter = referenceDimensions.get(PulleyKnowledgeBase.DIM_SHELL_DIAMETER);
        if (referenceDiameter != null) {
            scales.add(shell.diameterMm() / referenceDiameter);
            relativeErrors.add(relativeError(shell.diameterMm(), referenceDiameter));
        }
        Double referenceWidth = referenceDimensions.get(PulleyKnowledgeBase.DIM_FACE_WIDTH);
        if (referenceWidth != null) {
            scales.add(shell.faceWidthMm() / referenceWidth);
            relativeErrors.add(relativeError(shell.faceWidthMm(), referenceWidth));
        }
        if (scales.isEmpty()) {
            return new DimensionFit(1.0, Double.POSITIVE_INFINITY, 0);
        }
        Collections.sort(scales);
        double scale = scales.size() == 1
                ? scales.get(0)
                : 0.5 * (scales.get(0) + scales.get(scales.size() - 1));
        double maximumError = 0.0;
        for (double error : relativeErrors) maximumError = Math.max(maximumError, error);
        return new DimensionFit(scale, maximumError, scales.size());
    }

    private static void addEndDiscs(
            List<PrimitiveHypothesis> primitives,
            ShellObservation shell
    ) {
        Vec3 halfSpan = shell.axisWorld().scale(0.5 * shell.faceWidthMm());
        double discVisualThickness = Math.max(1.0, shell.faceWidthMm() * 0.002);
        primitives.add(primitive(
                PrimitiveType.LEFT_END_DISC,
                shell.centreWorldMm().subtract(halfSpan),
                shell.axisWorld(),
                shell.diameterMm(),
                discVisualThickness,
                shell.confidence() * 0.72,
                false
        ));
        primitives.add(primitive(
                PrimitiveType.RIGHT_END_DISC,
                shell.centreWorldMm().add(halfSpan),
                shell.axisWorld(),
                shell.diameterMm(),
                discVisualThickness,
                shell.confidence() * 0.72,
                false
        ));
    }

    private static void addLagging(
            List<PrimitiveHypothesis> primitives,
            ShellObservation shell,
            Map<String, Double> dimensions,
            double referenceConfidence
    ) {
        Double thickness = dimensions.get(PulleyKnowledgeBase.DIM_LAGGING_THICKNESS);
        if (thickness == null || thickness <= 0.0) return;
        primitives.add(primitive(
                PrimitiveType.LAGGING_CYLINDER,
                shell.centreWorldMm(),
                shell.axisWorld(),
                shell.diameterMm() + 2.0 * thickness,
                shell.faceWidthMm(),
                shell.confidence() * referenceConfidence * 0.80,
                false
        ));
    }

    private static void addShaft(
            List<PrimitiveHypothesis> primitives,
            ShellObservation shell,
            Map<String, Double> dimensions,
            double referenceConfidence
    ) {
        Double diameter = dimensions.get(PulleyKnowledgeBase.DIM_SHAFT_DIAMETER);
        Double length = dimensions.get(PulleyKnowledgeBase.DIM_OVERALL_SHAFT_LENGTH);
        if (diameter == null || length == null) return;
        primitives.add(primitive(
                PrimitiveType.SHAFT_CYLINDER,
                shell.centreWorldMm(),
                shell.axisWorld(),
                diameter,
                length,
                shell.confidence() * referenceConfidence * 0.84,
                false
        ));
    }

    private static void addHubs(
            List<PrimitiveHypothesis> primitives,
            ShellObservation shell,
            Map<String, Double> dimensions,
            double referenceConfidence
    ) {
        Double diameter = dimensions.get(PulleyKnowledgeBase.DIM_HUB_DIAMETER);
        Double length = dimensions.get(PulleyKnowledgeBase.DIM_HUB_LENGTH);
        if (diameter == null || length == null) return;
        Vec3 leftEnd = shell.centreWorldMm().subtract(
                shell.axisWorld().scale(0.5 * shell.faceWidthMm() + 0.5 * length)
        );
        Vec3 rightEnd = shell.centreWorldMm().add(
                shell.axisWorld().scale(0.5 * shell.faceWidthMm() + 0.5 * length)
        );
        double confidence = shell.confidence() * referenceConfidence * 0.72;
        primitives.add(primitive(
                PrimitiveType.LEFT_HUB,
                leftEnd,
                shell.axisWorld(),
                diameter,
                length,
                confidence,
                false
        ));
        primitives.add(primitive(
                PrimitiveType.RIGHT_HUB,
                rightEnd,
                shell.axisWorld(),
                diameter,
                length,
                confidence,
                false
        ));
    }

    private static void addSupports(
            List<PrimitiveHypothesis> primitives,
            ShellObservation shell,
            SupportEvidence evidence
    ) {
        double markerDiameter = Math.max(5.0, shell.diameterMm() * 0.02);
        evidence.leftBearingCentreWorldMm().ifPresent(point -> primitives.add(primitive(
                PrimitiveType.LEFT_BEARING_CENTRE,
                projectToAxis(point, shell.centreWorldMm(), shell.axisWorld()),
                shell.axisWorld(),
                markerDiameter,
                0.0,
                evidence.confidence(),
                true
        )));
        evidence.rightBearingCentreWorldMm().ifPresent(point -> primitives.add(primitive(
                PrimitiveType.RIGHT_BEARING_CENTRE,
                projectToAxis(point, shell.centreWorldMm(), shell.axisWorld()),
                shell.axisWorld(),
                markerDiameter,
                0.0,
                evidence.confidence(),
                true
        )));
    }

    private static PrimitiveHypothesis primitive(
            PrimitiveType type,
            Vec3 centre,
            Vec3 axis,
            double diameter,
            double length,
            double confidence,
            boolean measured
    ) {
        return new PrimitiveHypothesis(
                type,
                centre,
                axis,
                diameter,
                length,
                clamp01(confidence),
                measured
        );
    }

    private static Vec3 projectToAxis(Vec3 point, Vec3 axisOrigin, Vec3 axis) {
        double coordinate = point.subtract(axisOrigin).dot(axis);
        return axisOrigin.add(axis.scale(coordinate));
    }

    /** Local X axis is the pulley shaft axis; local origin is the shell centre anchor. */
    private static RigidPose axisFramePose(Vec3 centre, Vec3 axis) {
        Vec3 x = axis.normalized();
        Vec3 helper = Math.abs(x.z) < 0.90 ? Vec3.Z : Vec3.Y;
        Vec3 y = helper.cross(x).normalized();
        Vec3 z = x.cross(y).normalized();
        return new RigidPose(new double[]{
                x.x, y.x, z.x,
                x.y, y.y, z.y,
                x.z, y.z, z.z
        }, centre);
    }

    private static double relativeError(double first, double second) {
        return Math.abs(first - second) / Math.max(first, second);
    }

    private static String requireText(String value, String name) {
        if (value == null || value.trim().isEmpty()) {
            throw new IllegalArgumentException(name + " must not be blank");
        }
        return value.trim();
    }

    private static double requirePositive(double value, String name) {
        if (!Double.isFinite(value) || value <= 0.0) {
            throw new IllegalArgumentException(name + " must be positive and finite");
        }
        return value;
    }

    private static double requireConfidence(double value, String name) {
        if (!Double.isFinite(value) || value < 0.0 || value > 1.0) {
            throw new IllegalArgumentException(name + " must be between 0 and 1");
        }
        return value;
    }

    private static double requireFraction(double value, String name) {
        if (!Double.isFinite(value) || value < 0.0 || value >= 1.0) {
            throw new IllegalArgumentException(name + " must be in [0, 1)");
        }
        return value;
    }

    private static double clamp01(double value) {
        return Math.max(0.0, Math.min(1.0, value));
    }

    private static final class DimensionFit {
        private final double uniformScale;
        private final double maximumRelativeError;
        private final int comparedDimensions;

        private DimensionFit(
                double uniformScale,
                double maximumRelativeError,
                int comparedDimensions
        ) {
            this.uniformScale = uniformScale;
            this.maximumRelativeError = maximumRelativeError;
            this.comparedDimensions = comparedDimensions;
        }
    }
}

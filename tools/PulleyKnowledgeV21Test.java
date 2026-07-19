import cl.ingenieria.photogrammetryai.core.foundation.CoreMath.Vec3;
import cl.ingenieria.photogrammetryai.core.pulleyknowledge.PulleyKnowledgeBase;
import cl.ingenieria.photogrammetryai.core.pulleyknowledge.PulleyKnowledgeBase.Component;
import cl.ingenieria.photogrammetryai.core.pulleyknowledge.PulleyKnowledgeBase.EvidenceOrigin;
import cl.ingenieria.photogrammetryai.core.pulleyknowledge.PulleyKnowledgeBase.LaggingType;
import cl.ingenieria.photogrammetryai.core.pulleyknowledge.PulleyKnowledgeBase.ReferenceCase;
import cl.ingenieria.photogrammetryai.core.pulleyknowledge.PulleyKnowledgeBase.Role;
import cl.ingenieria.photogrammetryai.core.pulleyknowledge.PulleyKnowledgeBase.ShaftArrangement;
import cl.ingenieria.photogrammetryai.core.pulleyknowledge.PulleyOverlayHypothesisEngine;
import cl.ingenieria.photogrammetryai.core.pulleyknowledge.PulleyOverlayHypothesisEngine.OverlayHypothesis;
import cl.ingenieria.photogrammetryai.core.pulleyknowledge.PulleyOverlayHypothesisEngine.OverlayMode;
import cl.ingenieria.photogrammetryai.core.pulleyknowledge.PulleyOverlayHypothesisEngine.PrimitiveType;
import cl.ingenieria.photogrammetryai.core.pulleyknowledge.PulleyOverlayHypothesisEngine.ShellObservation;
import cl.ingenieria.photogrammetryai.core.pulleyknowledge.PulleyOverlayHypothesisEngine.SupportEvidence;
import cl.ingenieria.photogrammetryai.core.pulleyknowledge.PulleySimilarityEngine;
import cl.ingenieria.photogrammetryai.core.pulleyknowledge.PulleySimilarityEngine.Match;
import cl.ingenieria.photogrammetryai.core.pulleyknowledge.PulleySimilarityEngine.ObservedPulleySignature;

import java.util.Arrays;
import java.util.Collections;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public final class PulleyKnowledgeV21Test {
    public static void main(String[] args) {
        PulleyKnowledgeBase knowledgeBase = PulleyKnowledgeBase.defaultIndustrial();
        require(knowledgeBase.sources().size() >= 6, "Expected official knowledge sources");
        require(knowledgeBase.facts().size() >= 8, "Expected pulley domain facts");
        require(knowledgeBase.templates().size() >= 4, "Expected generic pulley families");

        knowledgeBase = knowledgeBase
                .withReferenceCase(referenceDrive1000())
                .withReferenceCase(referenceTail800())
                .withReferenceCase(referenceDrive1250());

        PulleySimilarityEngine similarity = new PulleySimilarityEngine(
                PulleySimilarityEngine.Config.industrialDefaults()
        );
        ObservedPulleySignature exactSignature = observedSignature(
                1008.0,
                1742.0,
                226.5,
                2395.0,
                2990.0,
                LaggingType.RUBBER_DIAMOND,
                0.96
        );
        List<Match> ranked = similarity.rank(knowledgeBase, exactSignature, 5);
        require(!ranked.isEmpty(), "Expected similarity results");
        Match best = ranked.get(0);
        require(best.candidateId().equals("CASE_DRIVE_1000"),
                "Expected CASE_DRIVE_1000 first, got " + best.candidateId());
        require(best.score() > 0.86, "Expected strong verified-case match");
        require(best.stepReferenceId().orElse("").equals("STEP_SUPPORT_DRIVE_1000"),
                "Expected STEP reference to propagate");

        PulleyOverlayHypothesisEngine overlayEngine = new PulleyOverlayHypothesisEngine(
                PulleyOverlayHypothesisEngine.Config.industrialDefaults()
        );
        ShellObservation exactShell = new ShellObservation(
                new Vec3(100.0, 200.0, 300.0),
                new Vec3(0.0, 1.0, 0.0),
                1008.0,
                1742.0,
                0.94
        );
        SupportEvidence supports = new SupportEvidence(
                new Vec3(100.0, -998.0, 300.0),
                new Vec3(100.0, 1397.0, 300.0),
                0.92
        );
        OverlayHypothesis exactOverlay = overlayEngine.build(
                knowledgeBase,
                exactShell,
                java.util.Optional.of(supports),
                best
        );
        require(exactOverlay.mode() == OverlayMode.RIGID_REFERENCE,
                "Near-identical dimensions should allow rigid reference overlay");
        require(exactOverlay.referencePlacement().isPresent(), "Expected STEP placement");
        require(!exactOverlay.referencePlacement().get().previewOnly(),
                "Rigid placement must not be preview-only");
        require(hasPrimitive(exactOverlay, PrimitiveType.SHELL_CYLINDER), "Missing shell");
        require(hasPrimitive(exactOverlay, PrimitiveType.SHAFT_CYLINDER), "Missing shaft prior");
        require(hasPrimitive(exactOverlay, PrimitiveType.LAGGING_CYLINDER), "Missing lagging prior");
        require(hasPrimitive(exactOverlay, PrimitiveType.LEFT_END_DISC), "Missing end disc");
        require(hasPrimitive(exactOverlay, PrimitiveType.LEFT_BEARING_CENTRE),
                "Missing support centre");

        ObservedPulleySignature previewSignature = observedSignature(
                1130.0,
                1950.0,
                245.0,
                2670.0,
                3320.0,
                LaggingType.RUBBER_DIAMOND,
                0.90
        );
        Match previewMatch = similarity.rank(knowledgeBase, previewSignature, 5).stream()
                .filter(match -> match.candidateId().equals("CASE_DRIVE_1000"))
                .findFirst()
                .orElseThrow(() -> new AssertionError("Expected drive reference candidate"));
        OverlayHypothesis previewOverlay = overlayEngine.build(
                knowledgeBase,
                new ShellObservation(Vec3.ZERO, Vec3.X, 1130.0, 1950.0, 0.88),
                java.util.Optional.empty(),
                previewMatch
        );
        require(previewOverlay.mode() == OverlayMode.UNIFORM_PREVIEW_SCALE,
                "Moderate dimension difference should use preview scaling");
        require(previewOverlay.referencePlacement().get().previewOnly(),
                "Scaled STEP must be marked preview-only");
        require(!previewOverlay.warnings().isEmpty(), "Preview mode requires warnings");

        PulleyKnowledgeBase genericOnly = PulleyKnowledgeBase.defaultIndustrial();
        Match genericMatch = similarity.rank(genericOnly, exactSignature, 1).get(0);
        OverlayHypothesis genericOverlay = overlayEngine.build(
                genericOnly,
                exactShell,
                java.util.Optional.empty(),
                genericMatch
        );
        require(genericOverlay.mode() == OverlayMode.PARAMETRIC_REBUILD,
                "Generic family must remain a parametric topology prior");
        require(!genericOverlay.referencePlacement().isPresent(),
                "Generic family should not invent a STEP reference");

        System.out.println("PulleyKnowledgeV21Test OK");
        System.out.println("best=" + best.candidateId()
                + " score=" + best.score()
                + " exactMode=" + exactOverlay.mode()
                + " previewMode=" + previewOverlay.mode()
                + " genericMode=" + genericOverlay.mode());
    }

    private static ReferenceCase referenceDrive1000() {
        return new ReferenceCase(
                "CASE_DRIVE_1000",
                "Verified drive pulley 1000 x 1750",
                EvidenceOrigin.USER_CONFIRMED,
                Role.DRIVE,
                ShaftArrangement.LIVE_SHAFT,
                LaggingType.RUBBER_DIAMOND,
                dimensions(1000.0, 1750.0, 226.0, 2400.0, 3000.0, 12.0, 500.0, 250.0),
                standardDriveComponents(),
                set("drive connection", "visible lagging", "external bearing housings"),
                "STEP_SUPPORT_DRIVE_1000",
                0.98
        );
    }

    private static ReferenceCase referenceTail800() {
        return new ReferenceCase(
                "CASE_TAIL_800",
                "Verified tail pulley 800 x 1400",
                EvidenceOrigin.USER_CONFIRMED,
                Role.TAIL,
                ShaftArrangement.LIVE_SHAFT,
                LaggingType.NONE,
                dimensions(800.0, 1400.0, 180.0, 2010.0, 2500.0, null, 390.0, 205.0),
                EnumSet.of(
                        Component.SHELL,
                        Component.END_DISC,
                        Component.SHAFT,
                        Component.HUB,
                        Component.LOCKING_ELEMENT,
                        Component.BEARING,
                        Component.BEARING_HOUSING
                ),
                set("no drive connection", "external bearing housings"),
                "STEP_SUPPORT_TAIL_800",
                0.96
        );
    }

    private static ReferenceCase referenceDrive1250() {
        return new ReferenceCase(
                "CASE_DRIVE_1250",
                "Verified drive pulley 1250 x 2100",
                EvidenceOrigin.USER_CONFIRMED,
                Role.DRIVE,
                ShaftArrangement.LIVE_SHAFT,
                LaggingType.CERAMIC_DIMPLED,
                dimensions(1250.0, 2100.0, 280.0, 2900.0, 3600.0, 15.0, 620.0, 300.0),
                standardDriveComponents(),
                set("drive connection", "ceramic dimpled", "external bearing housings"),
                "STEP_SUPPORT_DRIVE_1250",
                0.97
        );
    }

    private static ObservedPulleySignature observedSignature(
            double shellDiameter,
            double faceWidth,
            double shaftDiameter,
            double bearingSpan,
            double shaftLength,
            LaggingType lagging,
            double quality
    ) {
        EnumMap<Role, Double> roles = new EnumMap<>(Role.class);
        roles.put(Role.DRIVE, 0.97);
        roles.put(Role.HEAD, 0.72);
        Map<String, Double> dimensions = new LinkedHashMap<>();
        dimensions.put(PulleyKnowledgeBase.DIM_SHELL_DIAMETER, shellDiameter);
        dimensions.put(PulleyKnowledgeBase.DIM_FACE_WIDTH, faceWidth);
        dimensions.put(PulleyKnowledgeBase.DIM_SHAFT_DIAMETER, shaftDiameter);
        dimensions.put(PulleyKnowledgeBase.DIM_BEARING_CENTER_DISTANCE, bearingSpan);
        dimensions.put(PulleyKnowledgeBase.DIM_OVERALL_SHAFT_LENGTH, shaftLength);
        return new ObservedPulleySignature(
                roles,
                ShaftArrangement.LIVE_SHAFT,
                0.95,
                lagging,
                0.94,
                standardDriveComponents(),
                dimensions,
                set("drive connection", "visible lagging", "external bearing housings"),
                quality
        );
    }

    private static Map<String, Double> dimensions(
            double shellDiameter,
            double faceWidth,
            double shaftDiameter,
            double bearingSpan,
            double shaftLength,
            Double laggingThickness,
            double hubDiameter,
            double hubLength
    ) {
        Map<String, Double> values = new LinkedHashMap<>();
        values.put(PulleyKnowledgeBase.DIM_SHELL_DIAMETER, shellDiameter);
        values.put(PulleyKnowledgeBase.DIM_FACE_WIDTH, faceWidth);
        values.put(PulleyKnowledgeBase.DIM_SHAFT_DIAMETER, shaftDiameter);
        values.put(PulleyKnowledgeBase.DIM_BEARING_CENTER_DISTANCE, bearingSpan);
        values.put(PulleyKnowledgeBase.DIM_OVERALL_SHAFT_LENGTH, shaftLength);
        if (laggingThickness != null) {
            values.put(PulleyKnowledgeBase.DIM_LAGGING_THICKNESS, laggingThickness);
        }
        values.put(PulleyKnowledgeBase.DIM_HUB_DIAMETER, hubDiameter);
        values.put(PulleyKnowledgeBase.DIM_HUB_LENGTH, hubLength);
        return values;
    }

    private static Set<Component> standardDriveComponents() {
        return EnumSet.of(
                Component.SHELL,
                Component.END_DISC,
                Component.SHAFT,
                Component.HUB,
                Component.LOCKING_ELEMENT,
                Component.BEARING,
                Component.BEARING_HOUSING,
                Component.LAGGING,
                Component.DRIVE_CONNECTION
        );
    }

    private static Set<String> set(String... values) {
        return new LinkedHashSet<>(Arrays.asList(values));
    }

    private static boolean hasPrimitive(OverlayHypothesis hypothesis, PrimitiveType type) {
        return hypothesis.primitives().stream().anyMatch(primitive -> primitive.type() == type);
    }

    private static void require(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }
}

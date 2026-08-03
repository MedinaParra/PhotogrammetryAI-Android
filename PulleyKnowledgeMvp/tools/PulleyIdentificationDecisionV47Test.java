import cl.skm.pulleyai.PulleyIdentificationDecisionCore;
import cl.skm.pulleyai.RevisionedPulleyKnowledgeCore;

public final class PulleyIdentificationDecisionV47Test {
    public static void main(String[] args) {
        RevisionedPulleyKnowledgeCore.Catalog catalog = RevisionedPulleyKnowledgeCore.auditedSeed();

        PulleyIdentificationDecisionCore.MeasurementSet exact = base1702()
                .add(RevisionedPulleyKnowledgeCore.DimensionKind.SHELL_LENGTH,
                        RevisionedPulleyKnowledgeCore.SurfaceKind.AXIAL, 1520.0)
                .add(RevisionedPulleyKnowledgeCore.DimensionKind.SUPPORT_CENTRE_DISTANCE,
                        RevisionedPulleyKnowledgeCore.SurfaceKind.AXIAL, 2100.0)
                .add(RevisionedPulleyKnowledgeCore.DimensionKind.BEARING_CENTRE_DISTANCE,
                        RevisionedPulleyKnowledgeCore.SurfaceKind.AXIAL, 2054.0)
                .add(RevisionedPulleyKnowledgeCore.DimensionKind.SHAFT_TOTAL_LENGTH,
                        RevisionedPulleyKnowledgeCore.SurfaceKind.SHAFT, 2388.0)
                .build();
        PulleyIdentificationDecisionCore.Decision match =
                PulleyIdentificationDecisionCore.evaluate(catalog, exact);
        assertState(match, RevisionedPulleyKnowledgeCore.DecisionState.MATCH);
        assertTrue(match.residuals.size() == 4, "all axial residuals must be reported");
        assertTrue(match.score > 0.95, "exact match must have high score");
        assertTrue(match.explanation().contains("SKM-1702-03") ||
                match.explanation().contains("drive://"), "sources must remain visible");

        PulleyIdentificationDecisionCore.MeasurementSet partial = base1702().build();
        PulleyIdentificationDecisionCore.Decision review =
                PulleyIdentificationDecisionCore.evaluate(catalog, partial);
        assertState(review, RevisionedPulleyKnowledgeCore.DecisionState.REVIEW);
        assertTrue(review.explanation().contains("ninguna cota axial")
                        || review.explanation().contains("ninguna cota axial".substring(1)),
                "partial identity must explain missing axial evidence");

        PulleyIdentificationDecisionCore.MeasurementSet crossedOtDimensions = base1702()
                .add(RevisionedPulleyKnowledgeCore.DimensionKind.SHELL_LENGTH,
                        RevisionedPulleyKnowledgeCore.SurfaceKind.AXIAL, 1520.0)
                .add(RevisionedPulleyKnowledgeCore.DimensionKind.SUPPORT_CENTRE_DISTANCE,
                        RevisionedPulleyKnowledgeCore.SurfaceKind.AXIAL, 2080.0)
                .add(RevisionedPulleyKnowledgeCore.DimensionKind.BEARING_CENTRE_DISTANCE,
                        RevisionedPulleyKnowledgeCore.SurfaceKind.AXIAL, 2030.0)
                .add(RevisionedPulleyKnowledgeCore.DimensionKind.SHAFT_TOTAL_LENGTH,
                        RevisionedPulleyKnowledgeCore.SurfaceKind.SHAFT, 2300.0)
                .build();
        PulleyIdentificationDecisionCore.Decision blocked =
                PulleyIdentificationDecisionCore.evaluate(catalog, crossedOtDimensions);
        assertState(blocked, RevisionedPulleyKnowledgeCore.DecisionState.BLOCKED);
        assertTrue(blocked.explanation().contains("contradicción"),
                "cross-OT dimensions must expose contradiction");

        RevisionedPulleyKnowledgeCore.Observation imperialRadius =
                new RevisionedPulleyKnowledgeCore.Observation("4196149", "OT-781",
                        "2-POLEA-MOTRIZ-4196149", "0",
                        RevisionedPulleyKnowledgeCore.DimensionKind.OUTER_LAGGING_RADIUS,
                        RevisionedPulleyKnowledgeCore.SurfaceKind.OUTER_LAGGING, 381.0);
        PulleyIdentificationDecisionCore.MeasurementSet imperial =
                PulleyIdentificationDecisionCore.MeasurementSet.builder(imperialRadius)
                        .add(RevisionedPulleyKnowledgeCore.DimensionKind.FACE_WIDTH,
                                RevisionedPulleyKnowledgeCore.SurfaceKind.AXIAL, 2286.0)
                        .build();
        PulleyIdentificationDecisionCore.Decision imperialMatch =
                PulleyIdentificationDecisionCore.evaluate(catalog, imperial);
        assertState(imperialMatch, RevisionedPulleyKnowledgeCore.DecisionState.MATCH);

        RevisionedPulleyKnowledgeCore.Observation conflictRadius =
                new RevisionedPulleyKnowledgeCore.Observation("4162045", "OT-343", "UNKNOWN", "0",
                        RevisionedPulleyKnowledgeCore.DimensionKind.OUTER_LAGGING_RADIUS,
                        RevisionedPulleyKnowledgeCore.SurfaceKind.OUTER_LAGGING, 500.0);
        PulleyIdentificationDecisionCore.Decision conflict =
                PulleyIdentificationDecisionCore.evaluate(catalog,
                        PulleyIdentificationDecisionCore.MeasurementSet.builder(conflictRadius).build());
        assertState(conflict, RevisionedPulleyKnowledgeCore.DecisionState.BLOCKED);

        PulleyIdentificationDecisionCore.Decision repeated =
                PulleyIdentificationDecisionCore.evaluate(catalog, exact);
        assertTrue(match.fingerprint.equals(repeated.fingerprint),
                "same evidence and observations must create the same fingerprint");

        System.out.println("PulleyIdentificationDecisionV47Test OK score=" + match.score
                + " fingerprint=" + match.fingerprint.substring(0, 12));
    }

    private static PulleyIdentificationDecisionCore.MeasurementSet.Builder base1702() {
        RevisionedPulleyKnowledgeCore.Observation radius =
                new RevisionedPulleyKnowledgeCore.Observation("10415863", "OT-1702",
                        "SKM-1702-03", "0",
                        RevisionedPulleyKnowledgeCore.DimensionKind.OUTER_LAGGING_RADIUS,
                        RevisionedPulleyKnowledgeCore.SurfaceKind.OUTER_LAGGING, 720.0);
        return PulleyIdentificationDecisionCore.MeasurementSet.builder(radius);
    }

    private static void assertState(PulleyIdentificationDecisionCore.Decision decision,
                                    RevisionedPulleyKnowledgeCore.DecisionState expected) {
        if (decision.state != expected) {
            throw new AssertionError("expected " + expected + " but got " + decision.explanation());
        }
    }

    private static void assertTrue(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }
}

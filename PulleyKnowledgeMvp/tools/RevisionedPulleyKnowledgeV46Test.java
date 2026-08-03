import cl.skm.pulleyai.RevisionedPulleyKnowledgeCore;
import cl.skm.pulleyai.RevisionedPulleyKnowledgeCore.Decision;
import cl.skm.pulleyai.RevisionedPulleyKnowledgeCore.DecisionState;
import cl.skm.pulleyai.RevisionedPulleyKnowledgeCore.DimensionKind;
import cl.skm.pulleyai.RevisionedPulleyKnowledgeCore.Observation;
import cl.skm.pulleyai.RevisionedPulleyKnowledgeCore.SurfaceKind;

public final class RevisionedPulleyKnowledgeV46Test {
    public static void main(String[] args) {
        RevisionedPulleyKnowledgeCore.Catalog catalog = RevisionedPulleyKnowledgeCore.auditedSeed();
        assertTrue(catalog.evidenceCount() >= 25, "audited evidence was not loaded");
        assertTrue(catalog.conflictCount() == 3, "expected three explicit blocking conflicts");

        assertState(catalog, 10415863, "1702", "SKM-1702-03", "0", 749.0, DecisionState.MATCH, 29.0);
        assertState(catalog, 10415863, "1702", "SKM-1702-03", "0", 750.0, DecisionState.MATCH, 30.0);
        assertState(catalog, 10415863, "1702", "SKM-1702-03", "0", 751.0, DecisionState.BLOCKED, 31.0);

        Decision inches = catalog.evaluateRadius(new Observation("4196149", "OT-781",
                "2-POLEA-MOTRIZ-4196149", "0", DimensionKind.OUTER_LAGGING_RADIUS,
                SurfaceKind.OUTER_LAGGING, 381.0));
        assertTrue(inches.state == DecisionState.MATCH, "inch conversion must yield 381 mm radius");
        assertNear(inches.reference.originalValue, 15.0, 1e-9, "original inch value must be retained");
        assertNear(inches.reference.valueMm, 381.0, 1e-9, "inch conversion incorrect");

        Decision blockedCode = catalog.evaluateRadius(new Observation("4162045", "OT-343", "UNKNOWN", "0",
                DimensionKind.OUTER_LAGGING_RADIUS, SurfaceKind.OUTER_LAGGING, 500.0));
        assertTrue(blockedCode.state == DecisionState.BLOCKED, "conflicted material must stay blocked");

        Decision missingRadius = catalog.evaluateRadius(new Observation("10510386", "OT-270", "UNKNOWN", "0",
                DimensionKind.OUTER_LAGGING_RADIUS, SurfaceKind.OUTER_LAGGING, 500.0));
        assertTrue(missingRadius.state == DecisionState.BLOCKED, "missing radius must stay blocked");

        boolean surfaceRejected = false;
        try {
            new Observation("10415863", "OT-1702", "SKM-1702-03", "0",
                    DimensionKind.BARE_SHELL_RADIUS, SurfaceKind.OUTER_LAGGING, 700.0);
        } catch (IllegalArgumentException expected) {
            surfaceRejected = true;
        }
        assertTrue(surfaceRejected, "bare shell radius cannot use lagging surface");

        int before = catalog.evidenceCount();
        RevisionedPulleyKnowledgeCore.DimensionEvidence first = catalog.evidenceFor("10415863", "OT-1702").get(0);
        boolean inserted = catalog.upsert(first);
        assertTrue(!inserted && catalog.evidenceCount() == before, "migration/upsert must be idempotent");

        System.out.println("RevisionedPulleyKnowledgeV46Test OK evidence=" + catalog.evidenceCount());
    }

    private static void assertState(RevisionedPulleyKnowledgeCore.Catalog catalog, int code, String ot,
                                    String drawing, String revision, double radius,
                                    DecisionState expected, double expectedError) {
        Decision result = catalog.evaluateRadius(new Observation(String.valueOf(code), ot, drawing, revision,
                DimensionKind.OUTER_LAGGING_RADIUS, SurfaceKind.OUTER_LAGGING, radius));
        assertTrue(result.state == expected, "expected " + expected + " but got " + result.explanation());
        assertNear(result.radiusErrorMm, expectedError, 1e-9, "radius boundary error");
        assertTrue(result.explanation().contains("fuente="), "decision must expose source");
    }

    private static void assertNear(double actual, double expected, double tolerance, String message) {
        if (Math.abs(actual - expected) > tolerance) throw new AssertionError(message + ": " + actual + " != " + expected);
    }

    private static void assertTrue(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }
}

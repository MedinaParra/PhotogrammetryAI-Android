import cl.skm.pulleyai.BridgeCapturePlanCore;
import cl.skm.pulleyai.PulleyTargetLockCore;

import java.util.ArrayList;
import java.util.List;

public final class TargetLockBridgeRemediationV64Test {
    public static void main(String[] args) {
        testTargetLockContinuityAndSerialization();
        testDifferentTargetAndBacklightBlocked();
        testBridgePlanAcrossDisconnectedComponents();
        testCrossRingBridgePlan();
        System.out.println("TargetLockBridgeRemediationV64Test OK");
    }

    private static void testTargetLockContinuityAndSerialization() {
        PulleyTargetLockCore.Signature reference = PulleyTargetLockCore.analyze(targetGrid(80, 60, 0, false), 80, 60);
        PulleyTargetLockCore.Signature current = PulleyTargetLockCore.analyze(targetGrid(80, 60, 5, false), 80, 60);
        assertTrue(PulleyTargetLockCore.evaluate(null, reference).accepted, "reference must lock");
        PulleyTargetLockCore.Decision decision = PulleyTargetLockCore.evaluate(reference, current);
        assertTrue(decision.accepted, "same target after viewpoint change must pass: " + decision.reason);
        PulleyTargetLockCore.Signature decoded = PulleyTargetLockCore.decode(PulleyTargetLockCore.encode(reference));
        assertTrue(decoded != null, "signature decode failed");
        assertTrue(PulleyTargetLockCore.similarity(reference, decoded) > 0.999, "signature roundtrip drift");
    }

    private static void testDifferentTargetAndBacklightBlocked() {
        PulleyTargetLockCore.Signature reference = PulleyTargetLockCore.analyze(targetGrid(80, 60, 0, false), 80, 60);
        PulleyTargetLockCore.Decision changed = PulleyTargetLockCore.evaluate(reference,
                PulleyTargetLockCore.analyze(differentGrid(80, 60), 80, 60));
        assertTrue(!changed.accepted, "different scene must be blocked");
        PulleyTargetLockCore.Decision backlight = PulleyTargetLockCore.evaluate(reference,
                PulleyTargetLockCore.analyze(targetGrid(80, 60, 0, true), 80, 60));
        assertTrue(!backlight.accepted, "severe backlight must be blocked");
    }

    private static void testBridgePlanAcrossDisconnectedComponents() {
        List<BridgeCapturePlanCore.Node> nodes = new ArrayList<BridgeCapturePlanCore.Node>();
        nodes.add(new BridgeCapturePlanCore.Node(0, 1, "LOW", 0));
        nodes.add(new BridgeCapturePlanCore.Node(1, 2, "LOW", 1));
        nodes.add(new BridgeCapturePlanCore.Node(2, 3, "LOW", 2));
        nodes.add(new BridgeCapturePlanCore.Node(3, 4, "LOW", 3));
        List<BridgeCapturePlanCore.Edge> edges = new ArrayList<BridgeCapturePlanCore.Edge>();
        edges.add(new BridgeCapturePlanCore.Edge(0, 1, true, true, 80, 45));
        edges.add(new BridgeCapturePlanCore.Edge(2, 3, true, true, 70, 40));
        edges.add(new BridgeCapturePlanCore.Edge(1, 2, false, false, 36, 7));
        BridgeCapturePlanCore.Plan plan = BridgeCapturePlanCore.build(nodes, edges, "DISCONNECTED", 0);
        assertTrue(plan.required, "disconnected graph needs bridge");
        assertTrue(plan.sector == 1 || plan.sector == 2, "bridge must target component boundary");
        assertTrue(plan.instruction.contains("CAPTURA PUENTE"), "bridge instruction missing");
    }

    private static void testCrossRingBridgePlan() {
        List<BridgeCapturePlanCore.Node> nodes = new ArrayList<BridgeCapturePlanCore.Node>();
        nodes.add(new BridgeCapturePlanCore.Node(0, 1, "LOW", 4));
        nodes.add(new BridgeCapturePlanCore.Node(1, 2, "HIGH", 4));
        List<BridgeCapturePlanCore.Edge> edges = new ArrayList<BridgeCapturePlanCore.Edge>();
        edges.add(new BridgeCapturePlanCore.Edge(0, 1, false, false, 45, 9));
        BridgeCapturePlanCore.Plan plan = BridgeCapturePlanCore.build(nodes, edges, "NO_CROSS_RING_LINKS", 0);
        assertTrue(plan.required, "cross-ring gap needs bridge");
        assertTrue("HIGH".equals(plan.band), "prefer high-ring recapture");
        assertTrue(plan.sector == 4, "cross-ring sector mismatch");
    }

    private static double[] targetGrid(int width, int height, int shift, boolean backlit) {
        double[] grid = new double[width * height];
        for (int y = 0; y < height; y++) for (int x = 0; x < width; x++) {
            int cx = width / 2 + shift, cy = height / 2;
            double dx = (x - cx) / 24.0, dy = (y - cy) / 19.0;
            boolean target = dx * dx + dy * dy <= 1.0;
            double value = target ? 75 + ((x * 17 + y * 29) % 95) : 45 + ((x + y) % 9);
            if (backlit && y < height / 4) value = 245;
            if (backlit && target) value = 22 + ((x + y) % 12);
            grid[y * width + x] = value;
        }
        return grid;
    }

    private static double[] differentGrid(int width, int height) {
        double[] grid = new double[width * height];
        for (int y = 0; y < height; y++) for (int x = 0; x < width; x++)
            grid[y * width + x] = x < width / 2 ? 20 + (y % 4) : 225 - (y % 4);
        return grid;
    }

    private static void assertTrue(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }
}

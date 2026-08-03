import cl.skm.pulleyai.BoundedPoseGraphRefinementCore;

import java.util.ArrayList;
import java.util.List;

public final class PoseGraphRefinementV52Test {
    public static void main(String[] args) {
        testRefinementReducesResidualAndFixesGauge();
        testDisconnectedGraphBlocks();
        testLimitsBlockOversizedProblems();
        System.out.println("PoseGraphRefinementV52Test OK");
    }

    private static void testRefinementReducesResidualAndFixesGauge() {
        List<BoundedPoseGraphRefinementCore.Node> truth = new ArrayList<BoundedPoseGraphRefinementCore.Node>();
        List<BoundedPoseGraphRefinementCore.Node> initial = new ArrayList<BoundedPoseGraphRefinementCore.Node>();
        for (int i = 0; i < 10; i++) {
            double x = i * 0.75;
            double y = 0.15 * Math.sin(i * 0.7);
            double z = 0.08 * Math.cos(i * 0.5);
            truth.add(new BoundedPoseGraphRefinementCore.Node(x, y, z));
            initial.add(i == 0
                    ? new BoundedPoseGraphRefinementCore.Node(x, y, z)
                    : new BoundedPoseGraphRefinementCore.Node(
                            x + 0.16 * Math.sin(i * 1.3),
                            y - 0.12 * Math.cos(i * 0.8),
                            z + 0.09 * Math.sin(i * 0.4)));
        }
        List<BoundedPoseGraphRefinementCore.Edge> edges = new ArrayList<BoundedPoseGraphRefinementCore.Edge>();
        for (int i = 0; i < truth.size() - 1; i++) edges.add(edge(truth, i, i + 1, 1.0));
        for (int i = 0; i < truth.size() - 2; i++) edges.add(edge(truth, i, i + 2, 0.8));
        edges.add(edge(truth, 0, 5, 0.9));
        edges.add(edge(truth, 2, 8, 0.9));
        edges.add(edge(truth, 0, 9, 1.0));
        edges.add(new BoundedPoseGraphRefinementCore.Edge(1, 8, 8.5, -3.0, 2.4, 0.75));

        BoundedPoseGraphRefinementCore.Result result = BoundedPoseGraphRefinementCore.refine(
                new BoundedPoseGraphRefinementCore.Problem(initial, edges), 25, 0.18, 0.025);
        assertTrue(result.solved, "pose graph should solve: " + result.status);
        assertTrue(result.ready(), "pose graph should improve: " + result.status);
        assertTrue(result.finalRms < result.initialRms * 0.72,
                "residual improvement insufficient: " + result.initialRms + " -> " + result.finalRms);
        assertTrue(result.rejectedEdges >= 1, "gross outlier should be rejected");
        assertNear(result.nodes.get(0).x, initial.get(0).x, 1e-12, "gauge x moved");
        assertNear(result.nodes.get(0).y, initial.get(0).y, 1e-12, "gauge y moved");
        assertNear(result.nodes.get(0).z, initial.get(0).z, 1e-12, "gauge z moved");
        assertTrue(result.acceptedIterations > 0, "no iteration accepted");
    }

    private static void testDisconnectedGraphBlocks() {
        List<BoundedPoseGraphRefinementCore.Node> nodes = new ArrayList<BoundedPoseGraphRefinementCore.Node>();
        nodes.add(new BoundedPoseGraphRefinementCore.Node(0, 0, 0));
        nodes.add(new BoundedPoseGraphRefinementCore.Node(1, 0, 0));
        nodes.add(new BoundedPoseGraphRefinementCore.Node(5, 0, 0));
        List<BoundedPoseGraphRefinementCore.Edge> edges = new ArrayList<BoundedPoseGraphRefinementCore.Edge>();
        edges.add(new BoundedPoseGraphRefinementCore.Edge(0, 1, 1, 0, 0, 1));
        edges.add(new BoundedPoseGraphRefinementCore.Edge(0, 1, 1, 0, 0, 0.5));
        BoundedPoseGraphRefinementCore.Result result = BoundedPoseGraphRefinementCore.refine(
                new BoundedPoseGraphRefinementCore.Problem(nodes, edges), 10, 0.2, 0.1);
        assertTrue(!result.solved && "DISCONNECTED_GRAPH".equals(result.status),
                "disconnected graph must block: " + result.status);
    }

    private static void testLimitsBlockOversizedProblems() {
        List<BoundedPoseGraphRefinementCore.Node> nodes = new ArrayList<BoundedPoseGraphRefinementCore.Node>();
        for (int i = 0; i < BoundedPoseGraphRefinementCore.MAX_NODES + 1; i++) {
            nodes.add(new BoundedPoseGraphRefinementCore.Node(i, 0, 0));
        }
        List<BoundedPoseGraphRefinementCore.Edge> edges = new ArrayList<BoundedPoseGraphRefinementCore.Edge>();
        for (int i = 0; i < nodes.size() - 1; i++) {
            edges.add(new BoundedPoseGraphRefinementCore.Edge(i, i + 1, 1, 0, 0, 1));
        }
        BoundedPoseGraphRefinementCore.Result result = BoundedPoseGraphRefinementCore.refine(
                new BoundedPoseGraphRefinementCore.Problem(nodes, edges), 10, 0.2, 0.1);
        assertTrue(!result.solved && "NODE_LIMIT_EXCEEDED".equals(result.status),
                "node limit must block");
    }

    private static BoundedPoseGraphRefinementCore.Edge edge(
            List<BoundedPoseGraphRefinementCore.Node> nodes, int from, int to, double weight) {
        BoundedPoseGraphRefinementCore.Node a = nodes.get(from), b = nodes.get(to);
        return new BoundedPoseGraphRefinementCore.Edge(from, to,
                b.x - a.x, b.y - a.y, b.z - a.z, weight);
    }

    private static void assertNear(double actual, double expected, double tolerance, String message) {
        if (Math.abs(actual - expected) > tolerance) {
            throw new AssertionError(message + ": " + actual + " != " + expected);
        }
    }

    private static void assertTrue(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }
}

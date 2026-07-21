package cl.skm.pulleyai;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Bounded robust refinement of pose-graph translations. Node 0 is the fixed gauge;
 * rotations and intrinsics are not optimized, so this is not global bundle adjustment.
 */
public final class BoundedPoseGraphRefinementCore {
    public static final int MAX_NODES = 48;
    public static final int MAX_EDGES = 240;
    public static final int MAX_ITERATIONS = 30;

    private BoundedPoseGraphRefinementCore() {}

    public static Result refine(Problem problem, int requestedIterations,
                                double huberUnits, double priorWeight) {
        String validation = validate(problem);
        if (validation != null) return Result.failed(validation);
        int iterations = Math.max(2, Math.min(MAX_ITERATIONS, requestedIterations));
        double huber = Math.max(0.01, Math.min(20.0, huberUnits));
        double prior = Math.max(1e-6, Math.min(10.0, priorWeight));
        List<Node> initial = copyNodes(problem.nodes);
        List<Node> current = copyNodes(problem.nodes);
        Stats before = stats(current, problem.edges, huber);
        if (!Double.isFinite(before.robustCost)) return Result.failed("INVALID_INITIAL_GEOMETRY");

        List<Node> best = copyNodes(current);
        double bestCost = before.robustCost;
        int accepted = 0;
        int used = 0;
        double relaxation = 0.65;
        for (int iteration = 0; iteration < iterations; iteration++) {
            used = iteration + 1;
            Stats currentStats = stats(current, problem.edges, huber);
            double robustScale = Math.max(huber, currentStats.medianResidual * 2.5 + 1e-6);
            List<Node> candidate = updateAll(current, initial, problem.edges,
                    robustScale, prior, relaxation);
            candidate.set(0, initial.get(0));
            Stats candidateStats = stats(candidate, problem.edges, huber);
            if (Double.isFinite(candidateStats.robustCost) && candidateStats.robustCost < bestCost - 1e-12) {
                best = candidate;
                current = candidate;
                bestCost = candidateStats.robustCost;
                accepted++;
                relaxation = Math.min(0.92, relaxation * 1.05);
                if (currentStats.robustCost - candidateStats.robustCost < 1e-9) break;
            } else {
                relaxation *= 0.45;
                if (relaxation < 0.03) break;
            }
        }

        Stats after = stats(best, problem.edges, huber);
        double improvement = before.rmsResidual > 0 && Double.isFinite(before.rmsResidual)
                ? Math.max(0.0, (before.rmsResidual - after.rmsResidual) / before.rmsResidual) : 0.0;
        String status;
        if (!Double.isFinite(after.rmsResidual) || after.rmsResidual > before.rmsResidual * 1.02) {
            status = "DIVERGED";
            best = initial;
            after = before;
        } else if (accepted > 0 && improvement >= 0.25) {
            status = "CONVERGED";
        } else if (accepted > 0 && improvement >= 0.02) {
            status = "IMPROVED";
        } else {
            status = "STALLED";
        }
        return new Result(true, status, best, before.rmsResidual, after.rmsResidual,
                before.medianResidual, after.medianResidual, after.p90Residual,
                improvement, after.rejectedEdges, problem.edges.size(), used, accepted,
                initial.get(0).vector());
    }

    private static List<Node> updateAll(List<Node> nodes, List<Node> priors,
                                        List<Edge> edges, double robustScale,
                                        double priorWeight, double relaxation) {
        List<Node> next = copyNodes(nodes);
        for (int index = 1; index < nodes.size(); index++) {
            double sx = priors.get(index).x * priorWeight;
            double sy = priors.get(index).y * priorWeight;
            double sz = priors.get(index).z * priorWeight;
            double sw = priorWeight;
            for (Edge edge : edges) {
                if (edge.from != index && edge.to != index) continue;
                Node from = nodes.get(edge.from);
                Node to = nodes.get(edge.to);
                double rx = (to.x - from.x) - edge.dx;
                double ry = (to.y - from.y) - edge.dy;
                double rz = (to.z - from.z) - edge.dz;
                double residual = Math.sqrt(rx * rx + ry * ry + rz * rz);
                double robust = residual <= robustScale ? 1.0 : robustScale / Math.max(residual, 1e-12);
                double weight = edge.weight * robust;
                if (edge.to == index) {
                    sx += weight * (from.x + edge.dx);
                    sy += weight * (from.y + edge.dy);
                    sz += weight * (from.z + edge.dz);
                } else {
                    sx += weight * (to.x - edge.dx);
                    sy += weight * (to.y - edge.dy);
                    sz += weight * (to.z - edge.dz);
                }
                sw += weight;
            }
            if (sw > 0) {
                Node old = nodes.get(index);
                double tx = sx / sw, ty = sy / sw, tz = sz / sw;
                next.set(index, new Node(old.x + relaxation * (tx - old.x),
                        old.y + relaxation * (ty - old.y),
                        old.z + relaxation * (tz - old.z)));
            }
        }
        return next;
    }

    private static Stats stats(List<Node> nodes, List<Edge> edges, double huber) {
        List<Double> residuals = new ArrayList<Double>();
        double robustCost = 0.0;
        for (Edge edge : edges) {
            Node a = nodes.get(edge.from), b = nodes.get(edge.to);
            double rx = (b.x - a.x) - edge.dx;
            double ry = (b.y - a.y) - edge.dy;
            double rz = (b.z - a.z) - edge.dz;
            double residual = Math.sqrt(rx * rx + ry * ry + rz * rz);
            residuals.add(residual);
            robustCost += edge.weight * huberLoss(residual, huber);
        }
        if (residuals.isEmpty()) return Stats.invalid();
        double median = percentile(residuals, 0.5);
        double threshold = Math.max(huber * 3.0, median * 4.0 + 1e-6);
        double squared = 0.0;
        int inliers = 0, rejected = 0;
        for (double residual : residuals) {
            if (residual <= threshold) { squared += residual * residual; inliers++; }
            else rejected++;
        }
        double rms = inliers == 0 ? Double.POSITIVE_INFINITY : Math.sqrt(squared / inliers);
        return new Stats(rms, median, percentile(residuals, 0.9),
                robustCost / residuals.size(), rejected);
    }

    private static String validate(Problem problem) {
        if (problem == null) return "NO_PROBLEM";
        if (problem.nodes.size() < 2) return "INSUFFICIENT_NODES";
        if (problem.nodes.size() > MAX_NODES) return "NODE_LIMIT_EXCEEDED";
        if (problem.edges.size() < problem.nodes.size() - 1) return "INSUFFICIENT_EDGES";
        if (problem.edges.size() > MAX_EDGES) return "EDGE_LIMIT_EXCEEDED";
        for (Node node : problem.nodes) if (node == null || !node.valid()) return "INVALID_NODE";
        List<List<Integer>> adjacency = new ArrayList<List<Integer>>();
        for (int i = 0; i < problem.nodes.size(); i++) adjacency.add(new ArrayList<Integer>());
        for (Edge edge : problem.edges) {
            if (edge == null || !edge.valid(problem.nodes.size())) return "INVALID_EDGE";
            adjacency.get(edge.from).add(edge.to);
            adjacency.get(edge.to).add(edge.from);
        }
        boolean[] reached = new boolean[problem.nodes.size()];
        ArrayDeque<Integer> queue = new ArrayDeque<Integer>();
        reached[0] = true; queue.add(0);
        while (!queue.isEmpty()) {
            int node = queue.removeFirst();
            for (int other : adjacency.get(node)) if (!reached[other]) {
                reached[other] = true; queue.addLast(other);
            }
        }
        for (boolean value : reached) if (!value) return "DISCONNECTED_GRAPH";
        return null;
    }

    private static double huberLoss(double residual, double threshold) {
        return residual <= threshold ? 0.5 * residual * residual
                : threshold * (residual - 0.5 * threshold);
    }

    private static double percentile(List<Double> source, double q) {
        List<Double> values = new ArrayList<Double>(source);
        Collections.sort(values);
        double position = Math.max(0, Math.min(1, q)) * (values.size() - 1);
        int low = (int) Math.floor(position), high = (int) Math.ceil(position);
        if (low == high) return values.get(low);
        return values.get(low) * (high - position) + values.get(high) * (position - low);
    }

    private static List<Node> copyNodes(List<Node> source) {
        List<Node> result = new ArrayList<Node>();
        for (Node node : source) result.add(new Node(node.x, node.y, node.z));
        return result;
    }

    public static final class Node {
        public final double x, y, z;
        public Node(double x, double y, double z) { this.x = x; this.y = y; this.z = z; }
        boolean valid() { return Double.isFinite(x) && Double.isFinite(y) && Double.isFinite(z); }
        public double[] vector() { return new double[]{x, y, z}; }
    }

    public static final class Edge {
        public final int from, to;
        public final double dx, dy, dz, weight;
        public Edge(int from, int to, double dx, double dy, double dz, double weight) {
            this.from = from; this.to = to; this.dx = dx; this.dy = dy; this.dz = dz; this.weight = weight;
        }
        boolean valid(int count) {
            return from >= 0 && to >= 0 && from < count && to < count && from != to
                    && Double.isFinite(dx) && Double.isFinite(dy) && Double.isFinite(dz)
                    && Double.isFinite(weight) && weight > 0;
        }
    }

    public static final class Problem {
        public final List<Node> nodes;
        public final List<Edge> edges;
        public Problem(List<Node> nodes, List<Edge> edges) {
            this.nodes = Collections.unmodifiableList(copyNodes(nodes == null
                    ? Collections.<Node>emptyList() : nodes));
            this.edges = Collections.unmodifiableList(new ArrayList<Edge>(edges == null
                    ? Collections.<Edge>emptyList() : edges));
        }
    }

    public static final class Result {
        public final boolean solved;
        public final String status;
        public final List<Node> nodes;
        public final double initialRms, finalRms, initialMedian, finalMedian, finalP90;
        public final double improvementRatio;
        public final int rejectedEdges, edgeCount, iterations, acceptedIterations;
        public final double[] fixedGauge;

        Result(boolean solved, String status, List<Node> nodes, double initialRms,
               double finalRms, double initialMedian, double finalMedian, double finalP90,
               double improvementRatio, int rejectedEdges, int edgeCount,
               int iterations, int acceptedIterations, double[] fixedGauge) {
            this.solved = solved; this.status = status;
            this.nodes = Collections.unmodifiableList(copyNodes(nodes));
            this.initialRms = initialRms; this.finalRms = finalRms;
            this.initialMedian = initialMedian; this.finalMedian = finalMedian;
            this.finalP90 = finalP90; this.improvementRatio = improvementRatio;
            this.rejectedEdges = rejectedEdges; this.edgeCount = edgeCount;
            this.iterations = iterations; this.acceptedIterations = acceptedIterations;
            this.fixedGauge = fixedGauge == null ? null : fixedGauge.clone();
        }

        static Result failed(String status) {
            return new Result(false, status, Collections.<Node>emptyList(),
                    Double.POSITIVE_INFINITY, Double.POSITIVE_INFINITY,
                    Double.POSITIVE_INFINITY, Double.POSITIVE_INFINITY,
                    Double.POSITIVE_INFINITY, 0, 0, 0, 0, 0, null);
        }

        public boolean ready() {
            return solved && ("CONVERGED".equals(status) || "IMPROVED".equals(status));
        }
    }

    private static final class Stats {
        final double rmsResidual, medianResidual, p90Residual, robustCost;
        final int rejectedEdges;
        Stats(double rmsResidual, double medianResidual, double p90Residual,
              double robustCost, int rejectedEdges) {
            this.rmsResidual = rmsResidual; this.medianResidual = medianResidual;
            this.p90Residual = p90Residual; this.robustCost = robustCost;
            this.rejectedEdges = rejectedEdges;
        }
        static Stats invalid() {
            return new Stats(Double.POSITIVE_INFINITY, Double.POSITIVE_INFINITY,
                    Double.POSITIVE_INFINITY, Double.POSITIVE_INFINITY, 0);
        }
    }
}

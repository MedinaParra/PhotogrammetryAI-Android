package cl.skm.pulleyai;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

/** Produces a bounded recapture instruction from a disconnected or weak visual graph. */
public final class BridgeCapturePlanCore {
    private BridgeCapturePlanCore() {}

    public static Plan build(List<Node> nodes, List<Edge> edges, String graphStatus, int crossBandEdges) {
        if (nodes == null || nodes.isEmpty()) return Plan.none("Sin fotogramas para remediar");
        List<Edge> safeEdges = edges == null ? Collections.<Edge>emptyList() : edges;
        int[] component = components(nodes.size(), safeEdges);
        int componentCount = 0;
        for (int value : component) componentCount = Math.max(componentCount, value + 1);
        Candidate best = null;
        for (Edge edge : safeEdges) {
            if (edge.left < 0 || edge.right < 0 || edge.left >= nodes.size() || edge.right >= nodes.size() || edge.usable) continue;
            Node left = nodes.get(edge.left), right = nodes.get(edge.right);
            int gap = sectorGap(left.sector, right.sector);
            boolean crossRing = !sameBand(left.band, right.band);
            boolean componentGap = component[edge.left] != component[edge.right];
            if (!componentGap && !(crossRing && crossBandEdges < 3)) continue;
            if (crossRing && gap > 1) continue;
            if (!crossRing && (gap < 1 || gap > 2)) continue;
            double score = edge.inliers * 4.0 + edge.rawMatches + (componentGap ? 70.0 : 0.0)
                    + (crossRing ? 35.0 : 0.0) - gap * 5.0;
            Candidate candidate = new Candidate(left, right, crossRing, score, edge.inliers, edge.rawMatches);
            if (best == null || candidate.score > best.score) best = candidate;
        }
        if (best == null && componentCount > 1) {
            for (int i = 0; i < nodes.size(); i++) for (int j = i + 1; j < nodes.size(); j++) {
                Node left = nodes.get(i), right = nodes.get(j);
                if (component[i] == component[j]) continue;
                int gap = sectorGap(left.sector, right.sector);
                boolean crossRing = !sameBand(left.band, right.band);
                if ((!crossRing && gap <= 2) || (crossRing && gap <= 1)) {
                    Candidate candidate = new Candidate(left, right, crossRing,
                            50.0 - gap * 5.0 + (crossRing ? 20.0 : 0.0), 0, 0);
                    if (best == null || candidate.score > best.score) best = candidate;
                }
            }
        }
        if (best == null && crossBandEdges < 3) {
            Node low = null, high = null; int bestGap = Integer.MAX_VALUE;
            for (Node first : nodes) for (Node second : nodes) {
                if (sameBand(first.band, second.band)) continue;
                int gap = sectorGap(first.sector, second.sector);
                if (gap < bestGap) {
                    bestGap = gap;
                    low = "HIGH".equals(first.band) ? second : first;
                    high = "HIGH".equals(first.band) ? first : second;
                }
            }
            if (low != null && high != null) best = new Candidate(low, high, true, 1.0, 0, 0);
        }
        if (best == null) {
            Node weakest = weakestNode(nodes, safeEdges);
            if (weakest == null) return Plan.none("No se pudo aislar un sector puente");
            return new Plan(true, weakest.band, weakest.sector, "Grafo débil",
                    "Capture una vista puente en " + bandLabel(weakest.band) + " · " + sectorLabel(weakest.sector)
                            + "; acérquese, mantenga la polea bloqueada al centro y excluya otros tambores.",
                    componentCount, graphStatus);
        }
        Node target = chooseTarget(best.left, best.right, safeEdges);
        String reason = best.crossRing ? "Faltan enlaces entre anillos" : "Componentes visuales desconectados";
        String instruction = "CAPTURA PUENTE: " + bandLabel(target.band) + " · " + sectorLabel(target.sector)
                + ". Muévase 8–15°, mantenga 70% de solape, acerque la polea bloqueada y evite otros cilindros."
                + (best.rawMatches > 0 ? " Par débil previo: " + best.inliers + "/" + best.rawMatches + " inliers/matches." : "");
        return new Plan(true, target.band, target.sector, reason, instruction, componentCount, graphStatus);
    }

    private static int[] components(int count, List<Edge> edges) {
        List<List<Integer>> adjacency = new ArrayList<List<Integer>>(count);
        for (int i = 0; i < count; i++) adjacency.add(new ArrayList<Integer>());
        for (Edge edge : edges) if (edge.usable && edge.left >= 0 && edge.right >= 0 && edge.left < count && edge.right < count) {
            adjacency.get(edge.left).add(edge.right); adjacency.get(edge.right).add(edge.left);
        }
        int[] component = new int[count]; for (int i = 0; i < count; i++) component[i] = -1;
        int next = 0;
        for (int i = 0; i < count; i++) if (component[i] < 0) {
            ArrayDeque<Integer> queue = new ArrayDeque<Integer>(); queue.add(i); component[i] = next;
            while (!queue.isEmpty()) {
                int current = queue.removeFirst();
                for (int neighbor : adjacency.get(current)) if (component[neighbor] < 0) {
                    component[neighbor] = next; queue.addLast(neighbor);
                }
            }
            next++;
        }
        return component;
    }

    private static Node chooseTarget(Node first, Node second, List<Edge> edges) {
        int firstDegree = degree(first.index, edges), secondDegree = degree(second.index, edges);
        if (firstDegree != secondDegree) return firstDegree < secondDegree ? first : second;
        if ("HIGH".equals(first.band) != "HIGH".equals(second.band)) return "HIGH".equals(first.band) ? first : second;
        return first.sequence > second.sequence ? first : second;
    }

    private static Node weakestNode(List<Node> nodes, final List<Edge> edges) {
        List<Node> copy = new ArrayList<Node>(nodes);
        Collections.sort(copy, new Comparator<Node>() {
            @Override public int compare(Node a, Node b) {
                int value = Integer.compare(degree(a.index, edges), degree(b.index, edges));
                return value != 0 ? value : Integer.compare(a.sequence, b.sequence);
            }
        });
        return copy.isEmpty() ? null : copy.get(0);
    }

    private static int degree(int node, List<Edge> edges) {
        int degree = 0; for (Edge edge : edges) if (edge.usable && (edge.left == node || edge.right == node)) degree++;
        return degree;
    }
    private static int sectorGap(int first, int second) { int gap = Math.abs(first - second); return Math.min(gap, 12 - gap); }
    private static boolean sameBand(String first, String second) { return "HIGH".equals(first) == "HIGH".equals(second); }
    private static String bandLabel(String band) { return "HIGH".equals(band) ? "ALTURA ALTA" : "ALTURA EJE"; }
    private static String sectorLabel(int sector) { return "sector " + (sector + 1) + "/12"; }

    private static final class Candidate {
        final Node left, right; final boolean crossRing; final double score; final int inliers, rawMatches;
        Candidate(Node left, Node right, boolean crossRing, double score, int inliers, int rawMatches) {
            this.left = left; this.right = right; this.crossRing = crossRing; this.score = score; this.inliers = inliers; this.rawMatches = rawMatches;
        }
    }

    public static final class Node {
        public final int index, sequence, sector; public final String band;
        public Node(int index, int sequence, String band, int sector) {
            this.index = index; this.sequence = sequence; this.band = "HIGH".equals(band) ? "HIGH" : "LOW";
            this.sector = Math.max(0, Math.min(11, sector));
        }
    }
    public static final class Edge {
        public final int left, right, rawMatches, inliers; public final boolean usable, strong;
        public Edge(int left, int right, boolean usable, boolean strong, int rawMatches, int inliers) {
            this.left = left; this.right = right; this.usable = usable; this.strong = strong;
            this.rawMatches = Math.max(0, rawMatches); this.inliers = Math.max(0, inliers);
        }
    }
    public static final class Plan {
        public final boolean required; public final String band, reason, instruction, graphStatus;
        public final int sector, componentCount;
        Plan(boolean required, String band, int sector, String reason, String instruction, int componentCount, String graphStatus) {
            this.required = required; this.band = band; this.sector = sector; this.reason = reason; this.instruction = instruction;
            this.componentCount = componentCount; this.graphStatus = graphStatus == null ? "UNKNOWN" : graphStatus;
        }
        static Plan none(String reason) { return new Plan(false, "LOW", -1, reason, reason, 0, "NONE"); }
    }
}

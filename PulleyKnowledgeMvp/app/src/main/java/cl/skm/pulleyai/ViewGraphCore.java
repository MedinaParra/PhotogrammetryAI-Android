package cl.skm.pulleyai;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/** Pure multiview graph diagnostics. Nodes are accepted images; usable visual pairs are edges. */
public final class ViewGraphCore {
    private ViewGraphCore() {
    }

    public static Result analyze(List<Node> nodes, List<Edge> edges) {
        if (nodes == null || nodes.isEmpty()) {
            return new Result(0, 0, 0, 0, 0, Collections.<Integer>emptyList(), false, "NO_NODES");
        }
        int n = nodes.size();
        List<List<Integer>> adjacency = new ArrayList<List<Integer>>(n);
        for (int i = 0; i < n; i++) adjacency.add(new ArrayList<Integer>());
        int strongEdges = 0;
        int crossBandEdges = 0;
        if (edges != null) {
            for (Edge edge : edges) {
                if (edge.left < 0 || edge.right < 0 || edge.left >= n || edge.right >= n || edge.left == edge.right) continue;
                if (!edge.usable) continue;
                adjacency.get(edge.left).add(edge.right);
                adjacency.get(edge.right).add(edge.left);
                if (edge.strong) strongEdges++;
                if (!nodes.get(edge.left).band.equals(nodes.get(edge.right).band)) crossBandEdges++;
            }
        }

        boolean[] visited = new boolean[n];
        int components = 0;
        int largest = 0;
        List<Integer> isolatedSequences = new ArrayList<Integer>();
        for (int i = 0; i < n; i++) {
            if (adjacency.get(i).isEmpty()) isolatedSequences.add(nodes.get(i).sequence);
            if (visited[i]) continue;
            components++;
            int size = 0;
            ArrayDeque<Integer> queue = new ArrayDeque<Integer>();
            queue.add(i);
            visited[i] = true;
            while (!queue.isEmpty()) {
                int current = queue.removeFirst();
                size++;
                for (int neighbor : adjacency.get(current)) {
                    if (!visited[neighbor]) {
                        visited[neighbor] = true;
                        queue.addLast(neighbor);
                    }
                }
            }
            if (size > largest) largest = size;
        }

        int lowNodes = 0;
        int highNodes = 0;
        Set<Integer> lowSectors = new HashSet<Integer>();
        Set<Integer> highSectors = new HashSet<Integer>();
        for (Node node : nodes) {
            if ("HIGH".equals(node.band)) {
                highNodes++;
                highSectors.add(node.sector);
            } else {
                lowNodes++;
                lowSectors.add(node.sector);
            }
        }
        boolean bothRings = lowNodes >= 8 && highNodes >= 8 && lowSectors.size() >= 8 && highSectors.size() >= 8;
        boolean connectedEnough = largest >= Math.ceil(n * 0.90) && components <= Math.max(2, n / 20 + 1);
        boolean crossLinked = crossBandEdges >= 3;
        boolean strongEnough = strongEdges >= Math.max(6, n / 4);
        boolean ready = bothRings && connectedEnough && crossLinked && strongEnough && isolatedSequences.size() <= 1;
        String status;
        if (!bothRings) status = "RINGS_INCOMPLETE";
        else if (!connectedEnough) status = "DISCONNECTED";
        else if (!crossLinked) status = "NO_CROSS_RING_LINKS";
        else if (!strongEnough) status = "WEAK_GRAPH";
        else if (isolatedSequences.size() > 1) status = "ISOLATED_VIEWS";
        else status = "READY";
        return new Result(n, components, largest, strongEdges, crossBandEdges,
                isolatedSequences, ready, status);
    }

    public static final class Node {
        public final int sequence;
        public final String band;
        public final int sector;

        public Node(int sequence, String band, int sector) {
            this.sequence = sequence;
            this.band = band == null ? "LOW" : band;
            this.sector = sector;
        }
    }

    public static final class Edge {
        public final int left;
        public final int right;
        public final boolean usable;
        public final boolean strong;

        public Edge(int left, int right, boolean usable, boolean strong) {
            this.left = left;
            this.right = right;
            this.usable = usable;
            this.strong = strong;
        }
    }

    public static final class Result {
        public final int nodeCount;
        public final int components;
        public final int largestComponent;
        public final int strongEdges;
        public final int crossBandEdges;
        public final List<Integer> isolatedSequences;
        public final boolean ready;
        public final String status;

        Result(int nodeCount, int components, int largestComponent, int strongEdges,
               int crossBandEdges, List<Integer> isolatedSequences, boolean ready, String status) {
            this.nodeCount = nodeCount;
            this.components = components;
            this.largestComponent = largestComponent;
            this.strongEdges = strongEdges;
            this.crossBandEdges = crossBandEdges;
            this.isolatedSequences = Collections.unmodifiableList(new ArrayList<Integer>(isolatedSequences));
            this.ready = ready;
            this.status = status;
        }

        public String summary() {
            return "Grafo " + status + " · componente " + largestComponent + "/" + nodeCount
                    + " · enlaces fuertes " + strongEdges + " · cruces de anillo " + crossBandEdges
                    + " · aisladas " + isolatedSequences.size();
        }
    }
}

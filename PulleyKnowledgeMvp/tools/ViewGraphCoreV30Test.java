import cl.skm.pulleyai.ViewGraphCore;
import java.util.ArrayList;
import java.util.List;

public final class ViewGraphCoreV30Test {
    public static void main(String[] args) {
        List<ViewGraphCore.Node> nodes = new ArrayList<ViewGraphCore.Node>();
        for (int i = 0; i < 12; i++) nodes.add(new ViewGraphCore.Node(i + 1, "LOW", i));
        for (int i = 0; i < 12; i++) nodes.add(new ViewGraphCore.Node(i + 13, "HIGH", i));
        List<ViewGraphCore.Edge> edges = new ArrayList<ViewGraphCore.Edge>();
        for (int ring = 0; ring < 2; ring++) {
            int offset = ring * 12;
            for (int i = 0; i < 12; i++) {
                edges.add(new ViewGraphCore.Edge(offset + i, offset + ((i + 1) % 12), true, true));
            }
        }
        for (int i = 0; i < 12; i += 3) edges.add(new ViewGraphCore.Edge(i, 12 + i, true, true));
        ViewGraphCore.Result ready = ViewGraphCore.analyze(nodes, edges);
        if (!ready.ready) throw new AssertionError(ready.summary());
        edges.remove(edges.size() - 1);
        edges.remove(edges.size() - 1);
        ViewGraphCore.Result weakCross = ViewGraphCore.analyze(nodes, edges);
        if (weakCross.ready || !"NO_CROSS_RING_LINKS".equals(weakCross.status)) {
            throw new AssertionError("Expected cross-ring failure: " + weakCross.summary());
        }
        System.out.println("ViewGraphCoreV30Test OK " + ready.summary());
    }
}

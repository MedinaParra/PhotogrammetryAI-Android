import cl.skm.pulleyai.ViewBaselineSelector;
import java.util.ArrayList;
import java.util.List;

public final class ViewBaselineV27Test {
    public static void main(String[] args) {
        List<ViewBaselineSelector.View> views = new ArrayList<ViewBaselineSelector.View>();
        views.add(new ViewBaselineSelector.View("A", 350, 0, 0.95, "LOW"));
        views.add(new ViewBaselineSelector.View("B", 20, 2, 0.92, "LOW"));
        views.add(new ViewBaselineSelector.View("C", 80, 1, 0.90, "LOW"));
        views.add(new ViewBaselineSelector.View("D", 22, 22, 0.88, "HIGH"));
        List<ViewBaselineSelector.Pair> pairs = ViewBaselineSelector.select(views, 3);
        require(!pairs.isEmpty(), "pairs expected");
        ViewBaselineSelector.Pair best = pairs.get(0);
        require(best.first.id.equals("A") && best.second.id.equals("B"), "wrap-around 30 degree pair should rank first");
        require(Math.abs(best.angularSeparationDegrees - 30.0) < 0.001, "circular separation");
        require(ViewBaselineSelector.circularDifference(355, 5) == 10.0, "circular difference");
        System.out.println("ViewBaselineV27Test OK pair=" + best.first.id + best.second.id + " score=" + best.score);
    }
    private static void require(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }
}

import cl.skm.pulleyai.CaptureReadiness;
import cl.skm.pulleyai.CoveragePlanner;

public final class CaptureReadinessV28Test {
    public static void main(String[] args) {
        CaptureReadiness.Result ready = CaptureReadiness.evaluate(
                36, 4, CoveragePlanner.COMPLETE_MASK, CoveragePlanner.COMPLETE_MASK,
                1520.0, 1024L * 1024L * 1024L);
        if (!ready.ready()) throw new AssertionError(ready.summary());
        CaptureReadiness.Result strictBlocked = CaptureReadiness.evaluate(
                36, 4, CoveragePlanner.COMPLETE_MASK, CoveragePlanner.COMPLETE_MASK,
                1520.0, 1024L * 1024L * 1024L, true, false, "STALE");
        if (strictBlocked.ready()) throw new AssertionError("Strict gate must require overlap");

        CaptureReadiness.Result blocked = CaptureReadiness.evaluate(18, 20, 3, 0, null, 10L);
        if (blocked.ready()) throw new AssertionError("Incomplete session must be blocked");
        if (blocked.blockers.size() < 5) throw new AssertionError("Expected independent blockers: " + blocked.summary());
        if (blocked.warnings.isEmpty()) throw new AssertionError("High rejection ratio should warn");
        System.out.println("CaptureReadinessV28Test OK");
    }
}

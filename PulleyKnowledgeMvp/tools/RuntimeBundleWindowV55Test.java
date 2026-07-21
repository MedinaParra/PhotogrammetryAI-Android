import cl.skm.pulleyai.RuntimeBundleWindowCore;
import cl.skm.pulleyai.RuntimeBundleWindowSerializer;
import cl.skm.pulleyai.RuntimeTelemetryCore;

import java.util.ArrayList;
import java.util.List;

public final class RuntimeBundleWindowV55Test {
    public static void main(String[] args) {
        testBoundedWindowUsesRealObservations();
        testGaugeAndInsufficientEvidenceFailClosed();
        testTelemetryStates();
        System.out.println("RuntimeBundleWindowV55Test OK");
    }

    private static void testBoundedWindowUsesRealObservations() {
        List<RuntimeBundleWindowCore.FrameSnapshot> frames = new ArrayList<RuntimeBundleWindowCore.FrameSnapshot>();
        for (int camera = 0; camera < 10; camera++) {
            frames.add(new RuntimeBundleWindowCore.FrameSnapshot(camera, identity(),
                    new double[]{-0.35 * camera, 0.01 * (camera % 2), 0},
                    820, 825, 320, 240));
        }
        List<RuntimeBundleWindowCore.TrackSnapshot> tracks = new ArrayList<RuntimeBundleWindowCore.TrackSnapshot>();
        List<RuntimeBundleWindowCore.PointSnapshot> points = new ArrayList<RuntimeBundleWindowCore.PointSnapshot>();
        for (int point = 0; point < 150; point++) {
            double x = -1.0 + (point % 15) * 0.14;
            double y = -0.55 + (point % 9) * 0.12;
            double z = 5.0 + (point % 11) * 0.08;
            points.add(new RuntimeBundleWindowCore.PointSnapshot(point, x, y, z,
                    5, 0.008, 0.7));
            List<RuntimeBundleWindowCore.ObservationSnapshot> observations =
                    new ArrayList<RuntimeBundleWindowCore.ObservationSnapshot>();
            for (int camera = 0; camera < 10; camera++) {
                double u = 820 * (x - 0.35 * camera) / z + 320;
                double v = 825 * (y + 0.01 * (camera % 2)) / z + 240;
                observations.add(new RuntimeBundleWindowCore.ObservationSnapshot(
                        camera, point, u, v, 0.9));
            }
            tracks.add(new RuntimeBundleWindowCore.TrackSnapshot(point, 4.0, observations));
        }
        RuntimeBundleWindowCore.Result result = RuntimeBundleWindowCore.build(frames, tracks, points);
        assertTrue(result.ready, "window should be ready: " + result.status);
        assertTrue(result.cameraCount == 8, "camera cap must be 8");
        assertTrue(result.pointCount == 120, "point cap must be 120");
        assertTrue(result.observationCount == 960, "observation count must preserve selected evidence");
        assertTrue(result.cameraGlobalIndices.get(0) == 0, "gauge camera must stay first");
        assertTrue(result.problem.cameras.get(0).translation[0] == 0.0, "gauge translation changed");
        String json = RuntimeBundleWindowSerializer.canonicalJson(result);
        assertTrue(json.contains("\"observations\":[{"), "full observations missing");
        assertTrue(json.contains("\"rotation\":[["), "camera matrices missing");
        assertTrue(RuntimeBundleWindowSerializer.fingerprint(result).length() == 64,
                "fingerprint must be SHA-256");
        assertTrue(RuntimeBundleWindowSerializer.fingerprint(result)
                        .equals(RuntimeBundleWindowSerializer.fingerprint(result)),
                "fingerprint must be deterministic");
    }

    private static void testGaugeAndInsufficientEvidenceFailClosed() {
        List<RuntimeBundleWindowCore.FrameSnapshot> frames = new ArrayList<RuntimeBundleWindowCore.FrameSnapshot>();
        for (int camera = 1; camera < 5; camera++) {
            frames.add(new RuntimeBundleWindowCore.FrameSnapshot(camera, identity(),
                    new double[]{-camera, 0, 0}, 800, 800, 320, 240));
        }
        RuntimeBundleWindowCore.Result missingGauge = RuntimeBundleWindowCore.build(
                frames, new ArrayList<RuntimeBundleWindowCore.TrackSnapshot>(),
                new ArrayList<RuntimeBundleWindowCore.PointSnapshot>());
        assertTrue(!missingGauge.ready && "GAUGE_CAMERA_MISSING".equals(missingGauge.status),
                "missing gauge must block");

        frames.add(0, new RuntimeBundleWindowCore.FrameSnapshot(0, identity(),
                new double[]{0, 0, 0}, 800, 800, 320, 240));
        RuntimeBundleWindowCore.Result insufficient = RuntimeBundleWindowCore.build(
                frames, new ArrayList<RuntimeBundleWindowCore.TrackSnapshot>(),
                new ArrayList<RuntimeBundleWindowCore.PointSnapshot>());
        assertTrue(!insufficient.ready && "INSUFFICIENT_FUSED_POINTS".equals(insufficient.status),
                "insufficient evidence must block");
    }

    private static void testTelemetryStates() {
        RuntimeTelemetryCore.Record readyRecord = record(true, false, "READY");
        RuntimeTelemetryCore.Result ready = RuntimeTelemetryCore.evaluate(readyRecord);
        assertTrue(ready.state == RuntimeTelemetryCore.State.READY, "complete telemetry should be ready");
        assertTrue(ready.canonicalJson().contains("\"peakPssKb\":210000"), "PSS not serialized");

        RuntimeTelemetryCore.Result review = RuntimeTelemetryCore.evaluate(record(false, false, "REVIEW"));
        assertTrue(review.state == RuntimeTelemetryCore.State.REVIEW, "missing window should review");
        RuntimeTelemetryCore.Result interrupted = RuntimeTelemetryCore.evaluate(record(true, true, "REVIEW"));
        assertTrue(interrupted.state == RuntimeTelemetryCore.State.REVIEW, "interruption should review");
    }

    private static RuntimeTelemetryCore.Record record(boolean window, boolean interrupted,
                                                       String decision) {
        return new RuntimeTelemetryCore.Record(1000, 2500,
                100_000_000, 120_000_000, 130_000_000,
                180_000, 200_000, 210_000,
                500_000_000, 450_000_000, 1_000_000_000,
                window, interrupted, window ? "READY" : "INSUFFICIENT",
                "0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef",
                window ? 8 : 0, window ? 30 : 0, window ? 180 : 0,
                "READY", decision, decision.equals("READY")
                ? "OPTIMIZED_GEOMETRY_ACCEPTED" : "BA_PROBLEM_UNAVAILABLE", "IMPROVED");
    }

    private static double[][] identity() {
        return new double[][]{{1,0,0},{0,1,0},{0,0,1}};
    }
    private static void assertTrue(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }
}

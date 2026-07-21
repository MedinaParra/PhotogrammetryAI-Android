import cl.skm.pulleyai.ConditionedFocalBundleAdjustmentCore;
import cl.skm.pulleyai.LocalBundleAdjustmentCore;
import cl.skm.pulleyai.PhotogrammetrySafetyGateCore;

import java.util.ArrayList;
import java.util.List;

public final class ConditionedFocalBundleAdjustmentV60Test {
    public static void main(String[] args) {
        testObservableFocalBiasIsCorrected();
        testCentralConstantDepthSceneIsUnobservable();
        testUnsafeGateBlocksFocalStage();
        System.out.println("ConditionedFocalBundleAdjustmentV60Test OK");
    }

    private static void testObservableFocalBiasIsCorrected() {
        Synthetic scene = scene(true, false);
        ConditionedFocalBundleAdjustmentCore.Result result =
                ConditionedFocalBundleAdjustmentCore.optimize(scene.problem, readyGate(),
                        10, 2.0, 30_000.0, 0.035, 0.007, 0.06);
        assertTrue(result.solved, "conditioned focal stage should solve: " + result.status);
        assertTrue(result.focalApplied,
                "observable focal bias should be accepted: " + result.summary());
        assertTrue("FOCAL_ACCEPTED".equals(result.status), "focal status");
        assertTrue(result.observableCameras >= result.requiredObservableCameras,
                "observable camera count");
        assertTrue(result.finalRmsPx < result.baseFinalRmsPx * 0.94,
                "focal RMS gain insufficient: " + result.baseFinalRmsPx + " -> " + result.finalRmsPx);
        assertTrue(result.maximumScaleFraction > 0.004
                        && result.maximumScaleFraction <= 0.035 + 1e-9,
                "focal correction bound invalid: " + result.maximumScaleFraction);
        assertTrue(result.positiveDepthRatio > 0.98, "depth degraded");
        assertCameraSame(scene.initialCameras.get(0),
                result.bundleAdjustment.cameras.get(0), 1e-12,
                "camera 0 intrinsics/gauge moved");
        for (int camera = 1; camera < scene.initialCameras.size(); camera++) {
            LocalBundleAdjustmentCore.Camera before = scene.initialCameras.get(camera);
            LocalBundleAdjustmentCore.Camera after = result.bundleAdjustment.cameras.get(camera);
            assertNear(after.cx, before.cx, 1e-12, "cx changed");
            assertNear(after.cy, before.cy, 1e-12, "cy changed");
            assertNear(after.fx / after.fy, before.fx / before.fy, 1e-12,
                    "fx/fy ratio changed");
        }
        assertTrue(ConditionedFocalBundleAdjustmentCore.SENSITIVITY_LABEL.equals(
                        result.sensitivity.label), "sensitivity label missing");
        assertTrue(result.canonicalJson().contains("NOT_CALIBRATION"),
                "JSON must reject calibration interpretation");
    }

    private static void testCentralConstantDepthSceneIsUnobservable() {
        Synthetic scene = scene(true, true);
        ConditionedFocalBundleAdjustmentCore.Result result =
                ConditionedFocalBundleAdjustmentCore.optimize(scene.problem, readyGate(),
                        8, 2.0, 30_000.0, 0.035, 0.007, 0.06);
        assertTrue(result.solved, "base stages should solve");
        assertTrue(!result.focalApplied, "central scene cannot apply focal correction");
        assertTrue("FOCAL_UNOBSERVABLE".equals(result.status),
                "unobservable status: " + result.status);
        assertTrue(result.bundleAdjustment
                        == result.rotationalBundleAdjustment.bundleAdjustment,
                "unobservable fallback must preserve previous result object");
    }

    private static void testUnsafeGateBlocksFocalStage() {
        Synthetic scene = scene(true, false);
        PhotogrammetrySafetyGateCore.Result blocked = PhotogrammetrySafetyGateCore.evaluate(
                PhotogrammetrySafetyGateCore.Metrics.builder()
                        .frames(12, 4, 4).pairs(8, 3, 1).missing(0, 0)
                        .parallax(0.3, 0.05).modelCompetition(0.4, 0.92)
                        .poseGraph(false, 0, Double.NaN, Double.NaN)
                        .reprojection(5.0, 10.0).degradation(0.4, 0.5, 0.5).build());
        ConditionedFocalBundleAdjustmentCore.Result result =
                ConditionedFocalBundleAdjustmentCore.optimize(scene.problem, blocked);
        assertTrue(!result.solved, "unsafe gate must block focal BA");
        assertTrue(result.status.contains("SAFETY_GATE_NOT_READY"),
                "blocked status not propagated: " + result.status);
    }

    private static Synthetic scene(boolean focalBias, boolean central) {
        double[][] identity = identity();
        List<LocalBundleAdjustmentCore.Camera> trueCameras =
                new ArrayList<LocalBundleAdjustmentCore.Camera>();
        List<LocalBundleAdjustmentCore.Camera> initialCameras =
                new ArrayList<LocalBundleAdjustmentCore.Camera>();
        double[] biases = new double[]{1.0, 1.020, 0.982, 1.015};
        for (int camera = 0; camera < 4; camera++) {
            double[] translation = new double[]{-0.68 * camera,
                    camera % 2 == 0 ? 0.0 : 0.04, 0.0};
            trueCameras.add(new LocalBundleAdjustmentCore.Camera(
                    identity, translation, 840, 845, 320, 240));
            double scale = focalBias ? biases[camera] : 1.0;
            double[] initialTranslation = translation.clone();
            if (camera > 0) {
                initialTranslation[0] += camera % 2 == 0 ? -0.012 : 0.014;
                initialTranslation[1] -= 0.009;
                initialTranslation[2] += 0.008;
            }
            initialCameras.add(new LocalBundleAdjustmentCore.Camera(
                    identity, initialTranslation, 840 * scale, 845 * scale, 320, 240));
        }

        List<LocalBundleAdjustmentCore.Point3> truePoints =
                new ArrayList<LocalBundleAdjustmentCore.Point3>();
        List<LocalBundleAdjustmentCore.Point3> initialPoints =
                new ArrayList<LocalBundleAdjustmentCore.Point3>();
        int pointCount = central ? 28 : 48;
        for (int index = 0; index < pointCount; index++) {
            double x, y, z;
            if (central) {
                x = -0.10 + (index % 7) * 0.032;
                y = -0.08 + (index / 7) * 0.045;
                z = 6.0 + (index % 3) * 0.006;
            } else {
                x = -1.35 + (index % 8) * 0.39;
                y = -0.95 + (index / 8) * 0.34;
                z = 4.6 + (index % 9) * 0.29 + (index / 16) * 0.22;
            }
            truePoints.add(new LocalBundleAdjustmentCore.Point3(x, y, z));
            initialPoints.add(new LocalBundleAdjustmentCore.Point3(
                    x + 0.018 * Math.sin(index * 0.41),
                    y - 0.016 * Math.cos(index * 0.37),
                    z + 0.035 * Math.sin(index * 0.23 + 0.2)));
        }

        List<LocalBundleAdjustmentCore.Observation> observations =
                new ArrayList<LocalBundleAdjustmentCore.Observation>();
        int sequence = 0;
        for (int camera = 0; camera < trueCameras.size(); camera++) {
            for (int point = 0; point < truePoints.size(); point++) {
                double[] uv = project(trueCameras.get(camera), truePoints.get(point));
                double noiseU = ((sequence * 19) % 11 - 5) * 0.018;
                double noiseV = ((sequence * 31) % 13 - 6) * 0.017;
                if (!central && sequence % 67 == 0) {
                    noiseU += 3.2;
                    noiseV -= 2.7;
                }
                observations.add(new LocalBundleAdjustmentCore.Observation(
                        camera, point, uv[0] + noiseU, uv[1] + noiseV, 1.0));
                sequence++;
            }
        }
        return new Synthetic(new LocalBundleAdjustmentCore.Problem(
                initialCameras, initialPoints, observations), initialCameras);
    }

    private static PhotogrammetrySafetyGateCore.Result readyGate() {
        return PhotogrammetrySafetyGateCore.evaluate(
                PhotogrammetrySafetyGateCore.Metrics.builder()
                        .frames(36, 12, 12).pairs(60, 36, 16).missing(0, 0)
                        .parallax(2.8, 0.9).modelCompetition(0.82, 0.42)
                        .poseGraph(true, 8, 3.0, 20.0)
                        .reprojection(1.1, 2.3).degradation(0.02, 0.03, 0.04).build());
    }

    private static double[] project(LocalBundleAdjustmentCore.Camera camera,
                                    LocalBundleAdjustmentCore.Point3 point) {
        double x = camera.rotation[0][0] * point.x + camera.rotation[0][1] * point.y
                + camera.rotation[0][2] * point.z + camera.translation[0];
        double y = camera.rotation[1][0] * point.x + camera.rotation[1][1] * point.y
                + camera.rotation[1][2] * point.z + camera.translation[1];
        double z = camera.rotation[2][0] * point.x + camera.rotation[2][1] * point.y
                + camera.rotation[2][2] * point.z + camera.translation[2];
        return new double[]{camera.fx * x / z + camera.cx,
                camera.fy * y / z + camera.cy};
    }

    private static double[][] identity() {
        return new double[][]{{1,0,0},{0,1,0},{0,0,1}};
    }

    private static void assertCameraSame(LocalBundleAdjustmentCore.Camera expected,
                                         LocalBundleAdjustmentCore.Camera actual,
                                         double tolerance, String message) {
        assertNear(actual.fx, expected.fx, tolerance, message + " fx");
        assertNear(actual.fy, expected.fy, tolerance, message + " fy");
        assertNear(actual.cx, expected.cx, tolerance, message + " cx");
        assertNear(actual.cy, expected.cy, tolerance, message + " cy");
        for (int i = 0; i < 3; i++) {
            assertNear(actual.translation[i], expected.translation[i], tolerance,
                    message + " translation");
            for (int j = 0; j < 3; j++) {
                assertNear(actual.rotation[i][j], expected.rotation[i][j], tolerance,
                        message + " rotation");
            }
        }
    }

    private static void assertNear(double actual, double expected,
                                   double tolerance, String message) {
        if (Math.abs(actual - expected) > tolerance) {
            throw new AssertionError(message + ": " + actual + " != " + expected);
        }
    }

    private static void assertTrue(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }

    private static final class Synthetic {
        final LocalBundleAdjustmentCore.Problem problem;
        final List<LocalBundleAdjustmentCore.Camera> initialCameras;
        Synthetic(LocalBundleAdjustmentCore.Problem problem,
                  List<LocalBundleAdjustmentCore.Camera> initialCameras) {
            this.problem = problem;
            this.initialCameras = initialCameras;
        }
    }
}
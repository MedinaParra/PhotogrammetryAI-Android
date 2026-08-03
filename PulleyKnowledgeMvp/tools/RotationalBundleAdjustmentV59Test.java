import cl.skm.pulleyai.LocalBundleAdjustmentCore;
import cl.skm.pulleyai.PhotogrammetrySafetyGateCore;
import cl.skm.pulleyai.RotationalBundleAdjustmentCore;

import java.util.ArrayList;
import java.util.List;

public final class RotationalBundleAdjustmentV59Test {
    public static void main(String[] args) {
        testRotationalRefinementImprovesBiasedCameras();
        testStrongPriorPreservesStableBaseFallback();
        testUnsafeGateBlocksBothStages();
        System.out.println("RotationalBundleAdjustmentV59Test OK");
    }

    private static void testRotationalRefinementImprovesBiasedCameras() {
        Synthetic scene = scene(true);
        RotationalBundleAdjustmentCore.Result result =
                RotationalBundleAdjustmentCore.optimize(scene.problem, readyGate(),
                        12, 10, 2.0, 0.06, 1800.0, 3.0, 0.45);
        assertTrue(result.solved, "rotational BA should solve: " + result.status);
        assertTrue(result.rotationApplied,
                "synthetic rotational bias should be accepted: " + result.summary());
        assertTrue("ROTATION_ACCEPTED".equals(result.status), "rotation status");
        assertTrue(result.bundleAdjustment.ready(), "accepted BA must remain ready");
        assertTrue(result.finalRmsPx < result.baseFinalRmsPx * 0.94,
                "rotation RMS gain insufficient: " + result.baseFinalRmsPx + " -> " + result.finalRmsPx);
        assertTrue(result.maximumRotationDegrees > 0.15
                        && result.maximumRotationDegrees <= 3.0 + 1e-9,
                "rotation bound invalid: " + result.maximumRotationDegrees);
        assertTrue(result.positiveDepthRatio > 0.98, "positive depth degraded");
        assertCameraSame(scene.initialCameras.get(0),
                result.bundleAdjustment.cameras.get(0), 1e-12, "camera 0 gauge moved");
        assertTrue(result.residualStatistics.count == scene.observations.size(),
                "residual count mismatch");
        assertTrue(result.residualStatistics.empirical95LowPx
                        <= result.residualStatistics.empirical95HighPx,
                "empirical interval invalid");
        assertTrue(RotationalBundleAdjustmentCore.STATISTICAL_LABEL.equals(
                        result.residualStatistics.label),
                "statistical label missing");
        assertTrue(result.canonicalJson().contains("NOT_METROLOGICAL"),
                "JSON must reject metrological interpretation");
    }

    private static void testStrongPriorPreservesStableBaseFallback() {
        Synthetic scene = scene(false);
        RotationalBundleAdjustmentCore.Result result =
                RotationalBundleAdjustmentCore.optimize(scene.problem, readyGate(),
                        12, 4, 2.0, 0.08, 1e7, 0.10, 0.02);
        assertTrue(result.solved, "base BA should solve");
        assertTrue(!result.rotationApplied,
                "strong prior on correctly oriented cameras should retain base");
        assertTrue(result.bundleAdjustment == result.baseBundleAdjustment,
                "fallback must preserve exact base result object");
        assertCameraSame(scene.initialCameras.get(0),
                result.bundleAdjustment.cameras.get(0), 1e-12,
                "fallback camera 0 gauge moved");
    }

    private static void testUnsafeGateBlocksBothStages() {
        Synthetic scene = scene(true);
        PhotogrammetrySafetyGateCore.Result blocked = PhotogrammetrySafetyGateCore.evaluate(
                PhotogrammetrySafetyGateCore.Metrics.builder()
                        .frames(12, 4, 4).pairs(8, 3, 1).missing(0, 0)
                        .parallax(0.3, 0.05).modelCompetition(0.4, 0.92)
                        .poseGraph(false, 0, Double.NaN, Double.NaN)
                        .reprojection(5.0, 10.0).degradation(0.4, 0.5, 0.5).build());
        RotationalBundleAdjustmentCore.Result result =
                RotationalBundleAdjustmentCore.optimize(scene.problem, blocked);
        assertTrue(!result.solved, "unsafe gate must block rotational BA");
        assertTrue(result.status.contains("SAFETY_GATE_NOT_READY"),
                "unsafe status not propagated: " + result.status);
    }

    private static Synthetic scene(boolean rotationalBias) {
        double[][] identity = identity();
        List<LocalBundleAdjustmentCore.Camera> trueCameras =
                new ArrayList<LocalBundleAdjustmentCore.Camera>();
        List<LocalBundleAdjustmentCore.Camera> initialCameras =
                new ArrayList<LocalBundleAdjustmentCore.Camera>();
        for (int camera = 0; camera < 4; camera++) {
            double[] trueTranslation = new double[]{-0.62 * camera,
                    camera % 2 == 0 ? 0.0 : 0.035, 0.0};
            LocalBundleAdjustmentCore.Camera truth = new LocalBundleAdjustmentCore.Camera(
                    identity, trueTranslation, 840, 845, 320, 240);
            trueCameras.add(truth);
            double[] center = cameraCenter(truth);
            double[][] initialRotation = identity;
            if (rotationalBias && camera > 0) {
                double yaw = Math.toRadians((camera % 2 == 0 ? -1.0 : 1.0)
                        * (0.9 + camera * 0.18));
                double roll = Math.toRadians((camera % 2 == 0 ? 0.55 : -0.45));
                initialRotation = multiply(rotationZ(roll), rotationY(yaw));
            }
            double[] rc = multiply(initialRotation, center);
            double[] translation = new double[]{-rc[0], -rc[1], -rc[2]};
            if (camera > 0) {
                translation[0] += 0.035 * (camera % 2 == 0 ? -1 : 1);
                translation[1] -= 0.022;
                translation[2] += 0.018;
            }
            initialCameras.add(new LocalBundleAdjustmentCore.Camera(
                    initialRotation, translation, 840, 845, 320, 240));
        }

        List<LocalBundleAdjustmentCore.Point3> truePoints =
                new ArrayList<LocalBundleAdjustmentCore.Point3>();
        List<LocalBundleAdjustmentCore.Point3> initialPoints =
                new ArrayList<LocalBundleAdjustmentCore.Point3>();
        for (int index = 0; index < 36; index++) {
            double x = -1.05 + (index % 6) * 0.42;
            double y = -0.72 + (index / 6) * 0.29;
            double z = 5.0 + (index % 7) * 0.27 + (index / 12) * 0.18;
            truePoints.add(new LocalBundleAdjustmentCore.Point3(x, y, z));
            initialPoints.add(new LocalBundleAdjustmentCore.Point3(
                    x + 0.045 * Math.sin(index * 0.61),
                    y - 0.038 * Math.cos(index * 0.43),
                    z + 0.11 * Math.sin(index * 0.29 + 0.3)));
        }

        List<LocalBundleAdjustmentCore.Observation> observations =
                new ArrayList<LocalBundleAdjustmentCore.Observation>();
        int sequence = 0;
        for (int camera = 0; camera < trueCameras.size(); camera++) {
            for (int point = 0; point < truePoints.size(); point++) {
                double[] uv = project(trueCameras.get(camera), truePoints.get(point));
                double noiseU = ((sequence * 29) % 13 - 6) * 0.025;
                double noiseV = ((sequence * 17) % 11 - 5) * 0.028;
                if (sequence % 47 == 0) {
                    noiseU += 4.5;
                    noiseV -= 3.8;
                }
                observations.add(new LocalBundleAdjustmentCore.Observation(
                        camera, point, uv[0] + noiseU, uv[1] + noiseV, 1.0));
                sequence++;
            }
        }
        return new Synthetic(new LocalBundleAdjustmentCore.Problem(
                initialCameras, initialPoints, observations),
                initialCameras, observations);
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

    private static double[] cameraCenter(LocalBundleAdjustmentCore.Camera camera) {
        double[][] rt = transpose(camera.rotation);
        return multiply(rt, new double[]{-camera.translation[0],
                -camera.translation[1], -camera.translation[2]});
    }

    private static double[][] rotationY(double angle) {
        double c = Math.cos(angle), s = Math.sin(angle);
        return new double[][]{{c, 0, s}, {0, 1, 0}, {-s, 0, c}};
    }

    private static double[][] rotationZ(double angle) {
        double c = Math.cos(angle), s = Math.sin(angle);
        return new double[][]{{c, -s, 0}, {s, c, 0}, {0, 0, 1}};
    }

    private static double[][] identity() {
        return new double[][]{{1,0,0},{0,1,0},{0,0,1}};
    }

    private static double[][] transpose(double[][] source) {
        double[][] result = new double[3][3];
        for (int i = 0; i < 3; i++) for (int j = 0; j < 3; j++) result[i][j] = source[j][i];
        return result;
    }

    private static double[][] multiply(double[][] a, double[][] b) {
        double[][] result = new double[3][3];
        for (int i = 0; i < 3; i++) for (int k = 0; k < 3; k++)
            for (int j = 0; j < 3; j++) result[i][j] += a[i][k] * b[k][j];
        return result;
    }

    private static double[] multiply(double[][] a, double[] v) {
        return new double[]{
                a[0][0]*v[0] + a[0][1]*v[1] + a[0][2]*v[2],
                a[1][0]*v[0] + a[1][1]*v[1] + a[1][2]*v[2],
                a[2][0]*v[0] + a[2][1]*v[1] + a[2][2]*v[2]};
    }

    private static void assertCameraSame(LocalBundleAdjustmentCore.Camera expected,
                                         LocalBundleAdjustmentCore.Camera actual,
                                         double tolerance, String message) {
        for (int i = 0; i < 3; i++) {
            assertNear(actual.translation[i], expected.translation[i], tolerance, message + " translation");
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
        final List<LocalBundleAdjustmentCore.Observation> observations;
        Synthetic(LocalBundleAdjustmentCore.Problem problem,
                  List<LocalBundleAdjustmentCore.Camera> initialCameras,
                  List<LocalBundleAdjustmentCore.Observation> observations) {
            this.problem = problem;
            this.initialCameras = initialCameras;
            this.observations = observations;
        }
    }
}
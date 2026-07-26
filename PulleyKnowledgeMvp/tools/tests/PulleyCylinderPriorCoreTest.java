package cl.skm.pulleyai;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

public final class PulleyCylinderPriorCoreTest {
    public static void main(String[] args) {
        double length = 1600.0;
        double diameter = 800.0;
        double rawScale = 1.0 / 500.0;
        double[] axis = normalize(new double[]{0.82, 0.31, 0.48});
        double[] radialU = normalize(cross(axis, new double[]{0.0, 1.0, 0.0}));
        double[] radialV = normalize(cross(axis, radialU));
        double[] origin = {3.2, -1.4, 2.1};
        List<PointCloudExportCore.Point> points = new ArrayList<PointCloudExportCore.Point>();
        Random random = new Random(59L);
        for (int i = 0; i < 240; i++) {
            double x = -length * 0.5 + length * (i % 24) / 23.0;
            double angle = 2.0 * Math.PI * (i / 24) / 10.0 + (i % 24) * 0.03;
            double radius = diameter * 0.5 + random.nextGaussian() * 3.0;
            double[] point = add(origin, add(scale(axis, x * rawScale),
                    add(scale(radialU, radius * Math.cos(angle) * rawScale),
                            scale(radialV, radius * Math.sin(angle) * rawScale))));
            points.add(new PointCloudExportCore.Point(point[0], point[1], point[2],
                    1.2 + random.nextDouble(), 1.5 + random.nextDouble()));
        }
        for (int i = 0; i < 25; i++) {
            points.add(new PointCloudExportCore.Point(
                    8.0 * random.nextGaussian(), 8.0 * random.nextGaussian(),
                    8.0 * random.nextGaussian(), 8.0, 0.03));
        }

        PulleyCylinderPriorCore.Result fit = PulleyCylinderPriorCore.fit(points, length, diameter);
        System.out.println(fit.summary());
        require(fit.fitAccepted, "synthetic cylinder must be accepted: " + fit.status);
        require(fit.inlierCount >= 190, "too few inliers: " + fit.inlierCount);
        require(fit.radialRmsMm <= 20.0, "radial RMS too large: " + fit.radialRmsMm);
        require(Math.abs(dot(axis, fit.sourceAxis)) >= 0.90, "axis mismatch");

        List<PointCloudExportCore.Point> randomCloud = new ArrayList<PointCloudExportCore.Point>();
        for (int i = 0; i < 40; i++) {
            randomCloud.add(new PointCloudExportCore.Point(random.nextGaussian(),
                    random.nextGaussian(), random.nextGaussian(), 1.0, 1.0));
        }
        PulleyCylinderPriorCore.Result rejected =
                PulleyCylinderPriorCore.fit(randomCloud, length, diameter);
        require(!rejected.fitAccepted, "random cloud must not be accepted");
        System.out.println("PulleyCylinderPriorCore alpha59 PASS");
    }

    private static void require(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }
    private static double[] add(double[] a, double[] b) {
        return new double[]{a[0] + b[0], a[1] + b[1], a[2] + b[2]};
    }
    private static double[] scale(double[] a, double value) {
        return new double[]{a[0] * value, a[1] * value, a[2] * value};
    }
    private static double[] cross(double[] a, double[] b) {
        return new double[]{a[1] * b[2] - a[2] * b[1],
                a[2] * b[0] - a[0] * b[2], a[0] * b[1] - a[1] * b[0]};
    }
    private static double dot(double[] a, double[] b) {
        return a[0] * b[0] + a[1] * b[1] + a[2] * b[2];
    }
    private static double[] normalize(double[] a) {
        return scale(a, 1.0 / Math.sqrt(dot(a, a)));
    }
}

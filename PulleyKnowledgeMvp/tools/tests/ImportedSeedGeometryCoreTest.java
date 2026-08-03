package cl.skm.pulleyai;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public final class ImportedSeedGeometryCoreTest {
    public static void main(String[] args) {
        int width = 1280;
        int height = 960;
        double focal = 920.0;
        double cx = width * 0.5;
        double cy = height * 0.5;
        double angle = Math.toRadians(6.0);
        double c = Math.cos(angle);
        double s = Math.sin(angle);
        double[][] rotation = {{c, 0, s}, {0, 1, 0}, {-s, 0, c}};
        double[] translation = {0.85, 0.04, 0.02};
        List<FundamentalMatrixCore.PointPair> pairs = new ArrayList<FundamentalMatrixCore.PointPair>();
        for (int row = 0; row < 7; row++) {
            for (int col = 0; col < 9; col++) {
                double x = -1.8 + col * 0.45;
                double y = -1.2 + row * 0.40;
                double z = 6.5 + 0.15 * ((row + col) % 5);
                double u1 = focal * x / z + cx;
                double v1 = focal * y / z + cy;
                double x2 = rotation[0][0] * x + rotation[0][2] * z + translation[0];
                double y2 = y + translation[1];
                double z2 = rotation[2][0] * x + rotation[2][2] * z + translation[2];
                double noise = ((row * 11 + col * 7) % 5 - 2) * 0.06;
                double u2 = focal * x2 / z2 + cx + noise;
                double v2 = focal * y2 / z2 + cy - noise * 0.6;
                pairs.add(new FundamentalMatrixCore.PointPair(u1, v1, u2, v2));
            }
        }
        FundamentalMatrixCore.Result fundamental =
                FundamentalMatrixCore.estimate(pairs, 1.8, 1200);
        System.out.println("fundamental solved=" + fundamental.solved
                + " status=" + fundamental.status
                + " inliers=" + fundamental.inliers.size()
                + " ratio=" + fundamental.inlierRatio
                + " rms=" + fundamental.rmsPx);
        check(fundamental.solved, "fundamental solved");
        check(fundamental.inliers.size() >= 40, "fundamental inliers");
        ImportedSeedGeometryCore.Candidate candidate =
                new ImportedSeedGeometryCore.Candidate(1, 2, width, height,
                        "LOW", "LOW", "STRONG", pairs, fundamental);
        ImportedSeedGeometryCore.Result result = ImportedSeedGeometryCore.solve(
                Collections.singletonList(candidate));
        System.out.println("seed solved=" + result.solved
                + " ready=" + result.geometryReady
                + " state=" + result.state
                + " focalRatio=" + result.focalLongEdgeRatio
                + " focalPx=" + result.focalPx
                + " pose=" + (result.pose == null ? "null" : result.pose.status)
                + " positive=" + (result.pose == null ? 0.0 : result.pose.positiveRatio)
                + " parallax=" + (result.pose == null ? 0.0 : result.pose.medianParallaxDegrees)
                + " points=" + (result.cloud == null ? 0 : result.cloud.points.size())
                + " depthRatio=" + (result.cloud == null ? 0.0
                : result.cloud.positiveDepthRatio)
                + " rms=" + (result.cloud == null ? Double.POSITIVE_INFINITY
                : result.cloud.rmsReprojectionPx));
        check(result.solved, "seed solved");
        check(result.geometryReady, "seed ready");
        check(result.pose != null && !"WEAK".equals(result.pose.status), "pose admitted");
        check(result.cloud != null && result.cloud.points.size() >= 20, "cloud points");
        check(result.cloud.rmsReprojectionPx < 3.0, "cloud rms");
        check(result.canonicalJson().contains("skm-imported-seed-geometry/1"), "schema");

        ImportedSeedGeometryCore.Result blocked = ImportedSeedGeometryCore.solve(
                Collections.<ImportedSeedGeometryCore.Candidate>emptyList());
        check(!blocked.solved && !blocked.geometryReady, "empty fail closed");
        System.out.println("ImportedSeedGeometryCoreTest PASS · " + result.summary());
    }

    private static void check(boolean value, String label) {
        if (!value) throw new AssertionError(label);
    }
}

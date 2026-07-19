import cl.skm.pulleyai.AffineRansacCore;
import java.util.ArrayList;
import java.util.List;

public final class AffineRansacCoreV31Test {
    public static void main(String[] args) {
        List<AffineRansacCore.PointPair> pairs = new ArrayList<AffineRansacCore.PointPair>();
        double angle = Math.toRadians(4.0);
        double cos = Math.cos(angle) * 1.03;
        double sin = Math.sin(angle) * 1.03;
        for (int y = 0; y < 8; y++) {
            for (int x = 0; x < 10; x++) {
                double px = x * 17.0 + 4.0;
                double py = y * 15.0 + 7.0;
                double u = cos * px - sin * py + 8.0 + ((x + y) % 3 - 1) * 0.15;
                double v = sin * px + cos * py - 5.0 + ((x * 2 + y) % 3 - 1) * 0.15;
                pairs.add(new AffineRansacCore.PointPair(px, py, u, v));
            }
        }
        for (int i = 0; i < 24; i++) {
            pairs.add(new AffineRansacCore.PointPair(i * 3.0, i * 2.0, 200 - i * 5.0, 30 + i * 7.0));
        }
        AffineRansacCore.Result result = AffineRansacCore.estimate(pairs, 2.5, 700);
        if (!result.solved || !"STRONG".equals(result.status)) {
            throw new AssertionError("RANSAC failed: " + result.status);
        }
        if (result.inliers.size() < 75 || result.rmsPx > 0.5) {
            throw new AssertionError("Bad inliers/rms: " + result.inliers.size() + "/" + result.rmsPx);
        }
        if (Math.abs(result.model.tx - 8.0) > 0.5 || Math.abs(result.model.ty + 5.0) > 0.5) {
            throw new AssertionError("Bad translation: " + result.model.tx + "," + result.model.ty);
        }
        System.out.println("AffineRansacCoreV31Test OK inliers=" + result.inliers.size()
                + " ratio=" + result.inlierRatio + " rms=" + result.rmsPx);
    }
}

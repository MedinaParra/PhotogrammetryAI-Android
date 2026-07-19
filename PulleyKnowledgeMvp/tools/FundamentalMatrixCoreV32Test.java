import cl.skm.pulleyai.FundamentalMatrixCore;
import java.util.ArrayList;
import java.util.List;

public final class FundamentalMatrixCoreV32Test {
    public static void main(String[] args) {
        List<FundamentalMatrixCore.PointPair> pairs = new ArrayList<FundamentalMatrixCore.PointPair>();
        double fx = 820.0, fy = 815.0, cx = 320.0, cy = 240.0;
        double angle = Math.toRadians(6.0);
        double ca = Math.cos(angle), sa = Math.sin(angle);
        long state = 23L;
        for (int i = 0; i < 110; i++) {
            state = state * 6364136223846793005L + 1;
            double x = (((state >>> 17) & 0xffff) / 65535.0 - 0.5) * 3.2;
            state = state * 6364136223846793005L + 1;
            double y = (((state >>> 17) & 0xffff) / 65535.0 - 0.5) * 2.2;
            state = state * 6364136223846793005L + 1;
            double z = 4.5 + (((state >>> 17) & 0xffff) / 65535.0) * 3.5;
            double u1 = fx * x / z + cx;
            double v1 = fy * y / z + cy;
            double x2 = ca * x + sa * z + 0.32;
            double y2 = y + 0.03;
            double z2 = -sa * x + ca * z + 0.08;
            double u2 = fx * x2 / z2 + cx;
            double v2 = fy * y2 / z2 + cy;
            double noise = ((i % 5) - 2) * 0.06;
            pairs.add(new FundamentalMatrixCore.PointPair(u1 + noise, v1 - noise, u2 - noise, v2 + noise));
        }
        for (int i = 0; i < 28; i++) {
            pairs.add(new FundamentalMatrixCore.PointPair(20 + i * 9, 30 + i * 5, 600 - i * 7, 40 + i * 11));
        }
        FundamentalMatrixCore.Result result = FundamentalMatrixCore.estimate(pairs, 1.8, 300);
        if (!result.solved || !"STRONG".equals(result.status)) {
            throw new AssertionError("Fundamental RANSAC failed: " + result.status);
        }
        if (result.inliers.size() < 100 || result.rmsPx > 0.7) {
            throw new AssertionError("Bad epipolar fit: " + result.inliers.size() + "/" + result.rmsPx);
        }
        System.out.println("FundamentalMatrixCoreV32Test OK inliers=" + result.inliers.size()
                + " ratio=" + result.inlierRatio + " rms=" + result.rmsPx);
    }
}

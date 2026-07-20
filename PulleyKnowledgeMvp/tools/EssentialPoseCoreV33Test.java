import cl.skm.pulleyai.EssentialPoseCore;
import cl.skm.pulleyai.FundamentalMatrixCore;
import java.util.ArrayList;
import java.util.List;

public final class EssentialPoseCoreV33Test {
    public static void main(String[] args) {
        List<FundamentalMatrixCore.PointPair> pairs = new ArrayList<FundamentalMatrixCore.PointPair>();
        double fx = 820.0, fy = 815.0, cx = 320.0, cy = 240.0;
        double angle = Math.toRadians(6.0), ca = Math.cos(angle), sa = Math.sin(angle);
        long state = 29L;
        for (int i = 0; i < 120; i++) {
            state = state * 6364136223846793005L + 1;
            double x = (((state >>> 17) & 0xffff) / 65535.0 - 0.5) * 3.0;
            state = state * 6364136223846793005L + 1;
            double y = (((state >>> 17) & 0xffff) / 65535.0 - 0.5) * 2.0;
            state = state * 6364136223846793005L + 1;
            double z = 4.0 + (((state >>> 17) & 0xffff) / 65535.0) * 4.0;
            double u1 = fx * x / z + cx, v1 = fy * y / z + cy;
            double x2 = ca * x + sa * z + 0.35, y2 = y + 0.02, z2 = -sa * x + ca * z + 0.08;
            double u2 = fx * x2 / z2 + cx, v2 = fy * y2 / z2 + cy;
            pairs.add(new FundamentalMatrixCore.PointPair(u1, v1, u2, v2));
        }
        FundamentalMatrixCore.Result f = FundamentalMatrixCore.estimate(pairs, 1.0, 250);
        EssentialPoseCore.Result pose = EssentialPoseCore.recover(f.matrix, pairs, f.inliers,
                new EssentialPoseCore.Intrinsics(fx, fy, cx, cy),
                new EssentialPoseCore.Intrinsics(fx, fy, cx, cy));
        if (!pose.solved || !"STRONG".equals(pose.status)) {
            throw new AssertionError("Pose failed: " + pose.status);
        }
        if (Math.abs(pose.rotationDegrees - 6.0) > 1.0 || pose.positiveRatio < 0.85
                || pose.medianParallaxDegrees < 1.0) {
            throw new AssertionError("Bad pose rot=" + pose.rotationDegrees
                    + " positive=" + pose.positiveRatio + " parallax=" + pose.medianParallaxDegrees);
        }
        double[] expected = {0.35, 0.02, 0.08};
        double norm = Math.sqrt(expected[0] * expected[0] + expected[1] * expected[1]
                + expected[2] * expected[2]);
        double dot = Math.abs((pose.translation[0] * expected[0] + pose.translation[1] * expected[1]
                + pose.translation[2] * expected[2]) / norm);
        if (dot < 0.85) throw new AssertionError("Bad translation direction dot=" + dot);
        System.out.println("EssentialPoseCoreV33Test OK rotation=" + pose.rotationDegrees
                + " parallax=" + pose.medianParallaxDegrees + " dot=" + dot);
    }
}

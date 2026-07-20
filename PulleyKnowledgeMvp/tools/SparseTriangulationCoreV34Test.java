import cl.skm.pulleyai.EssentialPoseCore;
import cl.skm.pulleyai.FundamentalMatrixCore;
import cl.skm.pulleyai.SparseTriangulationCore;
import java.util.ArrayList;
import java.util.List;

public final class SparseTriangulationCoreV34Test {
    public static void main(String[] args) {
        List<FundamentalMatrixCore.PointPair> pairs = new ArrayList<FundamentalMatrixCore.PointPair>();
        double fx = 900.0, fy = 895.0, cx = 320.0, cy = 240.0;
        double angle = Math.toRadians(7.0), ca = Math.cos(angle), sa = Math.sin(angle);
        long state = 41L;
        for (int i = 0; i < 130; i++) {
            state = state * 6364136223846793005L + 1;
            double x = (((state >>> 18) & 0xffff) / 65535.0 - 0.5) * 3.4;
            state = state * 6364136223846793005L + 1;
            double y = (((state >>> 18) & 0xffff) / 65535.0 - 0.5) * 2.4;
            state = state * 6364136223846793005L + 1;
            double z = 4.2 + (((state >>> 18) & 0xffff) / 65535.0) * 4.5;
            double u1 = fx * x / z + cx;
            double v1 = fy * y / z + cy;
            double x2 = ca * x + sa * z + 0.42;
            double y2 = y + 0.03;
            double z2 = -sa * x + ca * z + 0.09;
            double u2 = fx * x2 / z2 + cx;
            double v2 = fy * y2 / z2 + cy;
            double noise = ((i % 7) - 3) * 0.025;
            pairs.add(new FundamentalMatrixCore.PointPair(
                    u1 + noise, v1 - noise, u2 - noise, v2 + noise));
        }
        FundamentalMatrixCore.Result fundamental = FundamentalMatrixCore.estimate(pairs, 1.2, 280);
        EssentialPoseCore.Intrinsics intrinsics = new EssentialPoseCore.Intrinsics(fx, fy, cx, cy);
        EssentialPoseCore.Result pose = EssentialPoseCore.recover(
                fundamental.matrix, pairs, fundamental.inliers, intrinsics, intrinsics);
        SparseTriangulationCore.Result cloud = SparseTriangulationCore.triangulate(
                pairs, fundamental.inliers, intrinsics, intrinsics, pose, 1.5);
        if (!cloud.solved || !"STRONG".equals(cloud.status)) {
            throw new AssertionError("Triangulation failed: " + cloud.status);
        }
        if (cloud.points.size() < 115 || cloud.rmsReprojectionPx > 0.25
                || cloud.positiveDepthRatio < 0.9) {
            throw new AssertionError("Bad cloud count=" + cloud.points.size()
                    + " rms=" + cloud.rmsReprojectionPx
                    + " positive=" + cloud.positiveDepthRatio);
        }
        System.out.println("SparseTriangulationCoreV34Test OK points=" + cloud.points.size()
                + " rms=" + cloud.rmsReprojectionPx);
    }
}

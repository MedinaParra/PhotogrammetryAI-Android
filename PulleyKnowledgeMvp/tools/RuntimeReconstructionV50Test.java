import cl.skm.pulleyai.LocalBundleAdjustmentCore;
import cl.skm.pulleyai.PhotogrammetrySafetyGateCore;
import cl.skm.pulleyai.RuntimeReconstructionDecisionCore;

import java.util.ArrayList;
import java.util.List;

public final class RuntimeReconstructionV50Test {
    public static void main(String[] args) {
        testOptimizedGeometryAccepted();
        testReviewUsesFallbackWhenProblemMissing();
        testBlockedGateNeverFallsBackAsApproved();
        testInterruptionAndResourceFailureAreFailClosed();
        System.out.println("RuntimeReconstructionV50Test OK");
    }

    private static void testOptimizedGeometryAccepted() {
        LocalBundleAdjustmentCore.Problem problem = noisyProblem();
        LocalBundleAdjustmentCore.Result ba = LocalBundleAdjustmentCore.optimize(
                problem, readyGate(), 15, 2.0, 0.08);
        RuntimeReconstructionDecisionCore.Result decision =
                RuntimeReconstructionDecisionCore.decide(readyGate(), ba, true, true, false);
        assertTrue(decision.canPublishOptimizedGeometry(), "optimized result should be publishable");
        assertTrue(decision.finalRmsPx < decision.initialRmsPx, "RMS must improve");
        assertTrue(!decision.useUnoptimizedFallback, "accepted BA must not use fallback");
    }

    private static void testReviewUsesFallbackWhenProblemMissing() {
        RuntimeReconstructionDecisionCore.Result decision =
                RuntimeReconstructionDecisionCore.decide(readyGate(), null, false, true, false);
        assertTrue(decision.state == RuntimeReconstructionDecisionCore.State.REVIEW,
                "missing BA problem must require review");
        assertTrue(decision.useUnoptimizedFallback, "missing BA problem must preserve fallback");
        assertTrue(!decision.canPublishOptimizedGeometry(), "fallback cannot be labeled optimized");
    }

    private static void testBlockedGateNeverFallsBackAsApproved() {
        RuntimeReconstructionDecisionCore.Result decision =
                RuntimeReconstructionDecisionCore.decide(blockedGate(), null, false, true, false);
        assertTrue(decision.state == RuntimeReconstructionDecisionCore.State.BLOCKED,
                "blocked gate must block runtime");
        assertTrue(!decision.useOptimizedGeometry && !decision.useUnoptimizedFallback,
                "blocked geometry must not be promoted");
    }

    private static void testInterruptionAndResourceFailureAreFailClosed() {
        RuntimeReconstructionDecisionCore.Result interrupted =
                RuntimeReconstructionDecisionCore.decide(readyGate(), null, true, true, true);
        assertTrue(interrupted.state == RuntimeReconstructionDecisionCore.State.REVIEW
                && interrupted.useUnoptimizedFallback, "interruption must use review fallback");
        RuntimeReconstructionDecisionCore.Result budget =
                RuntimeReconstructionDecisionCore.decide(readyGate(), null, true, false, false);
        assertTrue(budget.state == RuntimeReconstructionDecisionCore.State.REVIEW
                && budget.useUnoptimizedFallback, "resource failure must use review fallback");
    }

    private static LocalBundleAdjustmentCore.Problem noisyProblem() {
        double[][] identity={{1,0,0},{0,1,0},{0,0,1}};
        List<LocalBundleAdjustmentCore.Camera> truth=new ArrayList<LocalBundleAdjustmentCore.Camera>();
        List<LocalBundleAdjustmentCore.Camera> initial=new ArrayList<LocalBundleAdjustmentCore.Camera>();
        for(int c=0;c<4;c++) {
            double[] t={-0.5*c,0,0};
            truth.add(new LocalBundleAdjustmentCore.Camera(identity,t,800,800,320,240));
            initial.add(new LocalBundleAdjustmentCore.Camera(identity,
                    c==0?t:new double[]{t[0]+0.06,t[1]-0.03,0.02},800,800,320,240));
        }
        List<LocalBundleAdjustmentCore.Point3> points=new ArrayList<LocalBundleAdjustmentCore.Point3>();
        List<LocalBundleAdjustmentCore.Point3> initialPoints=new ArrayList<LocalBundleAdjustmentCore.Point3>();
        List<LocalBundleAdjustmentCore.Observation> observations=new ArrayList<LocalBundleAdjustmentCore.Observation>();
        for(int p=0;p<18;p++) {
            LocalBundleAdjustmentCore.Point3 point=new LocalBundleAdjustmentCore.Point3(
                    -0.7+(p%6)*0.28,-0.4+(p/6)*0.35,5.0+(p%4)*0.25);
            points.add(point);
            initialPoints.add(new LocalBundleAdjustmentCore.Point3(
                    point.x+0.04*Math.sin(p),point.y-0.03*Math.cos(p),point.z+0.12*Math.sin(p*0.4)));
            for(int c=0;c<truth.size();c++) {
                double[] uv=project(truth.get(c),point);
                observations.add(new LocalBundleAdjustmentCore.Observation(c,p,
                        uv[0]+0.04*((p+c)%5-2),uv[1]+0.04*((p*2+c)%5-2),1.0));
            }
        }
        return new LocalBundleAdjustmentCore.Problem(initial,initialPoints,observations);
    }

    private static double[] project(LocalBundleAdjustmentCore.Camera camera,
                                    LocalBundleAdjustmentCore.Point3 point) {
        double x=point.x+camera.translation[0];
        double y=point.y+camera.translation[1];
        double z=point.z+camera.translation[2];
        return new double[]{camera.fx*x/z+camera.cx,camera.fy*y/z+camera.cy};
    }

    private static PhotogrammetrySafetyGateCore.Result readyGate() {
        return PhotogrammetrySafetyGateCore.evaluate(PhotogrammetrySafetyGateCore.Metrics.builder()
                .frames(36,12,12).pairs(60,32,14).missing(0,0)
                .parallax(2.5,0.8).modelCompetition(0.75,0.35)
                .poseGraph(true,6,3,20).reprojection(1.2,2.5)
                .degradation(0.05,0.1,0.1).build());
    }

    private static PhotogrammetrySafetyGateCore.Result blockedGate() {
        return PhotogrammetrySafetyGateCore.evaluate(PhotogrammetrySafetyGateCore.Metrics.builder()
                .frames(18,5,5).pairs(12,3,1).missing(2,4)
                .parallax(0.4,0.1).modelCompetition(0.4,0.9)
                .poseGraph(false,0,20,80).reprojection(6,12)
                .degradation(0.4,0.5,0.5).build());
    }

    private static void assertTrue(boolean condition,String message){
        if(!condition)throw new AssertionError(message);
    }
}

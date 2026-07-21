import cl.skm.pulleyai.CameraCalibrationProfileCore;
import cl.skm.pulleyai.LocalBundleAdjustmentCore;
import cl.skm.pulleyai.PhotogrammetrySafetyGateCore;

import java.util.ArrayList;
import java.util.List;

public final class LocalBundleAdjustmentV49Test {
    public static void main(String[] args) {
        testBundleAdjustmentImprovesReprojection();
        testSafetyGateBlocksOptimization();
        testInsufficientProblemBlocks();
        testCameraDistortionRoundTripAndScaling();
        System.out.println("LocalBundleAdjustmentV49Test OK");
    }

    private static void testBundleAdjustmentImprovesReprojection() {
        double[][] identity={{1,0,0},{0,1,0},{0,0,1}};
        List<LocalBundleAdjustmentCore.Camera> trueCameras=new ArrayList<LocalBundleAdjustmentCore.Camera>();
        List<LocalBundleAdjustmentCore.Camera> initialCameras=new ArrayList<LocalBundleAdjustmentCore.Camera>();
        for(int c=0;c<4;c++) {
            double[] truth={-0.55*c,0.025*(c%2),0};
            trueCameras.add(new LocalBundleAdjustmentCore.Camera(identity,truth,800,805,320,240));
            double[] initial=c==0?truth.clone():new double[]{truth[0]+0.08*(c%2==0?-1:1),truth[1]-0.05,0.035};
            initialCameras.add(new LocalBundleAdjustmentCore.Camera(identity,initial,800,805,320,240));
        }
        List<LocalBundleAdjustmentCore.Point3> truePoints=new ArrayList<LocalBundleAdjustmentCore.Point3>();
        List<LocalBundleAdjustmentCore.Point3> initialPoints=new ArrayList<LocalBundleAdjustmentCore.Point3>();
        for(int i=0;i<24;i++) {
            double x=-0.9+(i%6)*0.36;
            double y=-0.55+(i/6)*0.36;
            double z=5.2+(i%5)*0.32;
            truePoints.add(new LocalBundleAdjustmentCore.Point3(x,y,z));
            initialPoints.add(new LocalBundleAdjustmentCore.Point3(
                    x+0.055*Math.sin(i*0.7),y-0.045*Math.cos(i*0.4),z+0.16*Math.sin(i*0.3+0.2)));
        }
        List<LocalBundleAdjustmentCore.Observation> observations=new ArrayList<LocalBundleAdjustmentCore.Observation>();
        int sequence=0;
        for(int c=0;c<trueCameras.size();c++) {
            for(int p=0;p<truePoints.size();p++) {
                double[] uv=project(trueCameras.get(c),truePoints.get(p));
                double noiseU=((sequence*37)%11-5)*0.035;
                double noiseV=((sequence*19)%9-4)*0.040;
                if(sequence%31==0){noiseU+=7.5;noiseV-=5.5;}
                observations.add(new LocalBundleAdjustmentCore.Observation(c,p,uv[0]+noiseU,uv[1]+noiseV,1.0));
                sequence++;
            }
        }
        LocalBundleAdjustmentCore.Problem problem=new LocalBundleAdjustmentCore.Problem(
                initialCameras,initialPoints,observations);
        LocalBundleAdjustmentCore.Result result=LocalBundleAdjustmentCore.optimize(
                problem,readyGate(),15,2.0,0.08);
        assertTrue(result.solved,"BA should solve: "+result.status);
        assertTrue(result.ready(),"BA should improve/converge: "+result.status);
        assertTrue(result.finalRmsPx<result.initialRmsPx*0.82,
                "RMS improvement insufficient: "+result.initialRmsPx+" -> "+result.finalRmsPx);
        assertTrue(result.positiveDepthRatio>0.98,"positive depth degraded");
        assertNear(result.cameras.get(0).translation[0],initialCameras.get(0).translation[0],1e-12,"camera 0 gauge moved");
        assertNear(result.cameras.get(0).translation[1],initialCameras.get(0).translation[1],1e-12,"camera 0 gauge moved");
    }

    private static void testSafetyGateBlocksOptimization() {
        LocalBundleAdjustmentCore.Problem problem=minimalValidProblem();
        PhotogrammetrySafetyGateCore.Result blocked=PhotogrammetrySafetyGateCore.evaluate(
                PhotogrammetrySafetyGateCore.Metrics.builder()
                        .frames(12,4,4).pairs(8,3,1).missing(0,0)
                        .parallax(0.4,0.1).modelCompetition(0.4,0.9)
                        .poseGraph(false,0,Double.NaN,Double.NaN)
                        .reprojection(5,10).degradation(0.1,0.1,0.1).build());
        LocalBundleAdjustmentCore.Result result=LocalBundleAdjustmentCore.optimize(problem,blocked,8,2,0.1);
        assertTrue(!result.solved&&"SAFETY_GATE_NOT_READY".equals(result.status),"unsafe session must not optimize");
    }

    private static void testInsufficientProblemBlocks() {
        List<LocalBundleAdjustmentCore.Camera> cameras=new ArrayList<LocalBundleAdjustmentCore.Camera>();
        cameras.add(camera(0));cameras.add(camera(-0.5));
        List<LocalBundleAdjustmentCore.Point3> points=new ArrayList<LocalBundleAdjustmentCore.Point3>();
        points.add(new LocalBundleAdjustmentCore.Point3(0,0,5));
        LocalBundleAdjustmentCore.Result result=LocalBundleAdjustmentCore.optimize(
                new LocalBundleAdjustmentCore.Problem(cameras,points,new ArrayList<LocalBundleAdjustmentCore.Observation>()),
                readyGate(),8,2,0.1);
        assertTrue(!result.solved&&"INSUFFICIENT_POINTS".equals(result.status),"insufficient problem should block");
    }

    private static void testCameraDistortionRoundTripAndScaling() {
        CameraCalibrationProfileCore.Profile profile=new CameraCalibrationProfileCore.Profile(
                "honor-x5c-main-001","HONOR X5C","0","0.1.0",1280,960,
                980,985,640,480,-0.12,0.035,0.0008,-0.0005,-0.004,
                CameraCalibrationProfileCore.ValidationState.UNCALIBRATED,Double.NaN,
                "synthetic-test-only");
        CameraCalibrationProfileCore.Point distorted=profile.distortPixel(1030,720);
        CameraCalibrationProfileCore.Point restored=profile.undistortPixel(distorted.x,distorted.y);
        assertNear(restored.x,1030,0.03,"distortion round trip u");
        assertNear(restored.y,720,0.03,"distortion round trip v");
        CameraCalibrationProfileCore.Profile half=profile.scaled(640,480);
        assertNear(half.fx,490,1e-12,"scaled fx");
        assertNear(half.cx,320,1e-12,"scaled cx");
        assertTrue(!profile.suitableForAutomaticCorrection(),"uncalibrated profile must not auto-correct");
        assertTrue(profile.fingerprint().length()==64,"profile fingerprint must be stable");
    }

    private static LocalBundleAdjustmentCore.Problem minimalValidProblem() {
        List<LocalBundleAdjustmentCore.Camera> cameras=new ArrayList<LocalBundleAdjustmentCore.Camera>();
        cameras.add(camera(0));cameras.add(camera(-0.5));cameras.add(camera(-1.0));
        List<LocalBundleAdjustmentCore.Point3> points=new ArrayList<LocalBundleAdjustmentCore.Point3>();
        List<LocalBundleAdjustmentCore.Observation> observations=new ArrayList<LocalBundleAdjustmentCore.Observation>();
        for(int p=0;p<6;p++){
            LocalBundleAdjustmentCore.Point3 point=new LocalBundleAdjustmentCore.Point3(-0.5+p*0.2,0.1*(p%2),5+p*0.1);points.add(point);
            for(int c=0;c<cameras.size();c++){double[]uv=project(cameras.get(c),point);observations.add(new LocalBundleAdjustmentCore.Observation(c,p,uv[0],uv[1],1));}
        }
        return new LocalBundleAdjustmentCore.Problem(cameras,points,observations);
    }

    private static LocalBundleAdjustmentCore.Camera camera(double tx) {
        return new LocalBundleAdjustmentCore.Camera(new double[][]{{1,0,0},{0,1,0},{0,0,1}},new double[]{tx,0,0},800,800,320,240);
    }

    private static double[] project(LocalBundleAdjustmentCore.Camera camera,LocalBundleAdjustmentCore.Point3 point) {
        double x=camera.rotation[0][0]*point.x+camera.rotation[0][1]*point.y+camera.rotation[0][2]*point.z+camera.translation[0];
        double y=camera.rotation[1][0]*point.x+camera.rotation[1][1]*point.y+camera.rotation[1][2]*point.z+camera.translation[1];
        double z=camera.rotation[2][0]*point.x+camera.rotation[2][1]*point.y+camera.rotation[2][2]*point.z+camera.translation[2];
        return new double[]{camera.fx*x/z+camera.cx,camera.fy*y/z+camera.cy};
    }

    private static PhotogrammetrySafetyGateCore.Result readyGate() {
        return PhotogrammetrySafetyGateCore.evaluate(PhotogrammetrySafetyGateCore.Metrics.builder()
                .frames(36,12,12).pairs(60,32,14).missing(0,0)
                .parallax(2.5,0.8).modelCompetition(0.75,0.35)
                .poseGraph(true,6,3,20).reprojection(1.2,2.5)
                .degradation(0.05,0.1,0.1).build());
    }

    private static void assertNear(double actual,double expected,double tolerance,String message){if(Math.abs(actual-expected)>tolerance)throw new AssertionError(message+": "+actual+" != "+expected);}
    private static void assertTrue(boolean condition,String message){if(!condition)throw new AssertionError(message);}
}

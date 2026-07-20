import cl.skm.pulleyai.MultiViewTrackCore;
import cl.skm.pulleyai.GlobalPoseGraphCore;
import cl.skm.pulleyai.GlobalSparseCloudCore;
import cl.skm.pulleyai.CylinderFitCore;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Random;

public final class GlobalReconstructionV34V36Test {
    public static void main(String[] args) {
        testTracks();
        testPoseGraph();
        testSparseCloudAndCylinder();
        System.out.println("GlobalReconstructionV34V36Test OK");
    }

    private static void testTracks() {
        List<MultiViewTrackCore.MatchEdge> edges = new ArrayList<MultiViewTrackCore.MatchEdge>();
        for (int track=0; track<100; track++) {
            for (int frame=0; frame<7; frame++) {
                edges.add(new MultiViewTrackCore.MatchEdge(
                        frame, track, frame+1, track, 1.0-frame*0.02));
            }
        }
        edges.add(new MultiViewTrackCore.MatchEdge(0,0,3,999,0.1));
        edges.add(new MultiViewTrackCore.MatchEdge(3,999,4,0,0.1));
        MultiViewTrackCore.Result result = MultiViewTrackCore.build(edges,3);
        if (!result.ready() || result.tracks.size()!=100 || result.tracksFourPlus!=100
                || result.coveredFrames!=8 || result.rejectedConflicts<1) {
            throw new AssertionError(result.summary());
        }
        for (MultiViewTrackCore.Track track : result.tracks) {
            HashSet<Integer> seen = new HashSet<Integer>();
            for (MultiViewTrackCore.Observation observation : track.observations) {
                if (!seen.add(observation.frameIndex)) throw new AssertionError("duplicate frame in track");
            }
        }
    }

    private static void testPoseGraph() {
        int count=12;
        double[][][] rotations=new double[count][][];
        double[][] translations=new double[count][];
        for(int i=0;i<count;i++) {
            rotations[i]=rotY(Math.toRadians(i*2.0));
            translations[i]=new double[]{i,0.04*Math.sin(i),0.02*Math.cos(i)};
        }
        List<GlobalPoseGraphCore.Edge> edges=new ArrayList<GlobalPoseGraphCore.Edge>();
        for(int i=0;i<count-1;i++) edges.add(relative(i,i+1,rotations,translations,1.0));
        for(int i=0;i<count-2;i++) edges.add(relative(i,i+2,rotations,translations,0.7));
        edges.add(new GlobalPoseGraphCore.Edge(1,9,rotY(Math.toRadians(40)),
                new double[]{0,1,0},0.1));
        GlobalPoseGraphCore.Result result=GlobalPoseGraphCore.solve(count,edges);
        if(!result.ready() || result.reachedNodes!=count || result.trustedCycleEdges<8
                || result.medianRotationResidualDegrees>0.01
                || result.medianTranslationResidualDegrees>1.0) {
            throw new AssertionError(result.summary());
        }
    }

    private static void testSparseCloudAndCylinder() {
        Random random=new Random(42);
        List<GlobalSparseCloudCore.Sample> samples=new ArrayList<GlobalSparseCloudCore.Sample>();
        double radius=1.25;
        double length=3.8;
        int tracks=240;
        for(int id=0;id<tracks;id++) {
            double angle=2.0*Math.PI*(id%60)/60.0;
            double axial=-length/2.0+length*(id/60)/3.0;
            double x=axial;
            double y=radius*Math.cos(angle);
            double z=radius*Math.sin(angle);
            for(int support=0;support<4;support++) {
                samples.add(new GlobalSparseCloudCore.Sample(id,
                        x+random.nextGaussian()*0.004,
                        y+random.nextGaussian()*0.004,
                        z+random.nextGaussian()*0.004,
                        0.45+0.1*random.nextDouble()));
            }
            if(id%20==0) samples.add(new GlobalSparseCloudCore.Sample(id,x+0.8,y-0.7,z+0.4,2.0));
        }
        GlobalSparseCloudCore.Result cloud=GlobalSparseCloudCore.fuse(samples,3);
        if(!cloud.ready() || cloud.points.size()!=tracks) throw new AssertionError(cloud.summary());
        List<CylinderFitCore.Point> points=new ArrayList<CylinderFitCore.Point>();
        for(GlobalSparseCloudCore.FusedPoint point:cloud.points) {
            points.add(new CylinderFitCore.Point(point.x,point.y,point.z));
        }
        for(int i=0;i<45;i++) points.add(new CylinderFitCore.Point(
                -3+6*random.nextDouble(),-3+6*random.nextDouble(),-3+6*random.nextDouble()));
        CylinderFitCore.Result fit=CylinderFitCore.fit(points);
        if(!fit.ready() || Math.abs(fit.radius-radius)>0.025
                || Math.abs(fit.length-length)>0.18 || Math.abs(fit.axis[0])<0.98) {
            throw new AssertionError(fit.summary());
        }
    }

    private static GlobalPoseGraphCore.Edge relative(int i,int j,double[][][] r,double[][] t,double weight) {
        double[][] relR=mul(r[j],transpose(r[i]));
        double[] relT=sub(t[j],mul(relR,t[i]));
        double norm=Math.sqrt(relT[0]*relT[0]+relT[1]*relT[1]+relT[2]*relT[2]);
        for(int k=0;k<3;k++) relT[k]/=norm;
        return new GlobalPoseGraphCore.Edge(i,j,relR,relT,weight);
    }
    private static double[][] rotY(double a) { return new double[][]{{Math.cos(a),0,Math.sin(a)},{0,1,0},{-Math.sin(a),0,Math.cos(a)}}; }
    private static double[][] transpose(double[][] a) { double[][]r=new double[3][3];for(int i=0;i<3;i++)for(int j=0;j<3;j++)r[i][j]=a[j][i];return r; }
    private static double[][] mul(double[][]a,double[][]b) { double[][]r=new double[3][3];for(int i=0;i<3;i++)for(int k=0;k<3;k++)for(int j=0;j<3;j++)r[i][j]+=a[i][k]*b[k][j];return r; }
    private static double[] mul(double[][]a,double[]v) { return new double[]{a[0][0]*v[0]+a[0][1]*v[1]+a[0][2]*v[2],a[1][0]*v[0]+a[1][1]*v[1]+a[1][2]*v[2],a[2][0]*v[0]+a[2][1]*v[1]+a[2][2]*v[2]}; }
    private static double[] sub(double[]a,double[]b) { return new double[]{a[0]-b[0],a[1]-b[1],a[2]-b[2]}; }
}

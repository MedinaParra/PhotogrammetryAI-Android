import cl.skm.pulleyai.ReconstructionFrameSelectorCore;
import cl.skm.pulleyai.TrackPointRefinementCore;
import cl.skm.pulleyai.IndustrialAnalysisBudgetCore;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;

public final class ReconstructionMaturityV38V39Test {
    public static void main(String[] args) {
        testSelector();
        testPointRefinement();
        testBudget();
        System.out.println("ReconstructionMaturityV38V39Test OK");
    }

    private static void testSelector() {
        List<ReconstructionFrameSelectorCore.Candidate> values =
                new ArrayList<ReconstructionFrameSelectorCore.Candidate>();
        int id=0;
        for(String band:new String[]{"LOW","HIGH"}) {
            for(int sector=0;sector<12;sector++) {
                values.add(new ReconstructionFrameSelectorCore.Candidate(id++,band,sector,160,128,0.03,id,true));
                values.add(new ReconstructionFrameSelectorCore.Candidate(id++,band,sector,520,126,0.02,id,true));
                values.add(new ReconstructionFrameSelectorCore.Candidate(id++,band,sector,900,45,1.3,id,true));
            }
        }
        ReconstructionFrameSelectorCore.Result result =
                ReconstructionFrameSelectorCore.select(values,2,48);
        if(!"BALANCED".equals(result.status) || result.selected.size()!=48 || result.discarded!=24)
            throw new AssertionError(result.summary());
        for(ReconstructionFrameSelectorCore.Candidate candidate:result.selected)
            if(candidate.motion>1.0)throw new AssertionError("unstable frame selected");
    }

    private static void testPointRefinement() {
        Random random=new Random(77);
        double[] truth={0.35,-0.22,5.8};
        List<TrackPointRefinementCore.Observation> observations=
                new ArrayList<TrackPointRefinementCore.Observation>();
        for(int i=0;i<7;i++) {
            double angle=Math.toRadians(-18+i*6);
            double[][] rotation=rotY(angle);
            double[] center={-1.2+i*0.4,0.05*Math.sin(i),0};
            double[] translation=negative(mul(rotation,center));
            TrackPointRefinementCore.Camera camera=new TrackPointRefinementCore.Camera(
                    rotation,translation,950,945,320,240);
            double[] p=add(mul(rotation,truth),translation);
            double u=950*p[0]/p[2]+320+random.nextGaussian()*0.35;
            double v=945*p[1]/p[2]+240+random.nextGaussian()*0.35;
            if(i==5){u+=18;v-=14;}
            observations.add(new TrackPointRefinementCore.Observation(camera,u,v));
        }
        TrackPointRefinementCore.Result result=TrackPointRefinementCore.refine(
                new double[]{0.55,-0.05,6.25},observations,25,1.5);
        double error=Math.sqrt(squared(result.point[0]-truth[0])+squared(result.point[1]-truth[1])+squared(result.point[2]-truth[2]));
        if(!result.ready() || error>0.06 || result.medianPx>0.8)
            throw new AssertionError(result.summary()+" error="+error);
    }

    private static void testBudget() {
        IndustrialAnalysisBudgetCore.Result normal=IndustrialAnalysisBudgetCore.evaluate(
                48,640,480,420,190,512L*1024L*1024L,2L*1024L*1024L*1024L);
        if(!normal.ready())throw new AssertionError(normal.summary());
        IndustrialAnalysisBudgetCore.Result blocked=IndustrialAnalysisBudgetCore.evaluate(
                70,1280,960,800,500,128L*1024L*1024L,100L*1024L*1024L);
        if(blocked.ready() || blocked.blockers.size()<4)throw new AssertionError("budget must block");
    }

    private static double[][] rotY(double a){return new double[][]{{Math.cos(a),0,Math.sin(a)},{0,1,0},{-Math.sin(a),0,Math.cos(a)}};}
    private static double[] mul(double[][]a,double[]v){return new double[]{a[0][0]*v[0]+a[0][1]*v[1]+a[0][2]*v[2],a[1][0]*v[0]+a[1][1]*v[1]+a[1][2]*v[2],a[2][0]*v[0]+a[2][1]*v[1]+a[2][2]*v[2]};}
    private static double[] negative(double[]v){return new double[]{-v[0],-v[1],-v[2]};}
    private static double[] add(double[]a,double[]b){return new double[]{a[0]+b[0],a[1]+b[1],a[2]+b[2]};}
    private static double squared(double v){return v*v;}
}

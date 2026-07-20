import cl.skm.pulleyai.PulleyShellRansacCore;
import cl.skm.pulleyai.PulleyMetricScaleCore;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;

public final class PulleyShellSegmentationV37Test {
    public static void main(String[] args) {
        Random random=new Random(123);
        List<PulleyShellRansacCore.Point> points=new ArrayList<PulleyShellRansacCore.Point>();
        double radius=1.4;
        double length=3.2;
        double[] axis=normalize(new double[]{0.92,0.25,0.30});
        double[] u=normalize(cross(Math.abs(axis[2])<0.8?new double[]{0,0,1}:new double[]{0,1,0},axis));
        double[] v=cross(axis,u);
        for(int ring=0;ring<7;ring++){
            double axial=-length/2.0+length*ring/6.0;
            for(int i=0;i<48;i++){
                double angle=2*Math.PI*i/48.0;
                double rr=radius+random.nextGaussian()*0.008;
                points.add(new PulleyShellRansacCore.Point(
                        axis[0]*axial+u[0]*rr*Math.cos(angle)+v[0]*rr*Math.sin(angle)+0.2,
                        axis[1]*axial+u[1]*rr*Math.cos(angle)+v[1]*rr*Math.sin(angle)-0.1,
                        axis[2]*axial+u[2]*rr*Math.cos(angle)+v[2]*rr*Math.sin(angle)+0.4));
            }
        }
        for(int i=0;i<190;i++) points.add(new PulleyShellRansacCore.Point(
                -4+8*random.nextDouble(),-4+8*random.nextDouble(),-4+8*random.nextDouble()));
        PulleyShellRansacCore.Result shell=PulleyShellRansacCore.fit(points,900);
        if(!shell.ready()) throw new AssertionError(shell.summary());
        if(Math.abs(shell.radius-radius)>0.04) throw new AssertionError("radius="+shell.radius);
        if(Math.abs(shell.length-length)>0.20) throw new AssertionError("length="+shell.length);
        double alignment=Math.abs(shell.axis[0]*axis[0]+shell.axis[1]*axis[1]+shell.axis[2]*axis[2]);
        if(alignment<0.97) throw new AssertionError("alignment="+alignment);
        PulleyMetricScaleCore.Result metric=PulleyMetricScaleCore.resolve(shell,1600.0);
        if(!metric.ready()) throw new AssertionError(metric.summary());
        double expectedDiameter=2*radius*(1600.0/length);
        if(Math.abs(metric.shellDiameterMm-expectedDiameter)>35.0) throw new AssertionError(metric.summary());
        System.out.println("PulleyShellSegmentationV37Test OK: "+shell.summary()+" | "+metric.summary());
    }
    static double[] normalize(double[]a){double n=Math.sqrt(a[0]*a[0]+a[1]*a[1]+a[2]*a[2]);return new double[]{a[0]/n,a[1]/n,a[2]/n};}
    static double[] cross(double[]a,double[]b){return new double[]{a[1]*b[2]-a[2]*b[1],a[2]*b[0]-a[0]*b[2],a[0]*b[1]-a[1]*b[0]};}
}

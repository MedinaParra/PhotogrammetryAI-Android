import cl.skm.pulleyai.StepMeshPlacementCore;

public final class StepMeshPlacementV42Test {
    public static void main(String[] args){
        StepMeshPlacementCore.Suggestion yAxis=StepMeshPlacementCore.suggest(
                "SHAFT",new double[]{10,-120,-20,110,880,80});
        require("Y→X".equals(yAxis.axis),"Y longest dimension must align to X");
        require(Math.abs(yAxis.rz+90)<1e-9,"Y→X requires -90° around Z");
        double[] center=transform(60,380,30,yAxis);
        require(norm(center)<1e-8,"rotated shaft center must reach origin");

        StepMeshPlacementCore.Suggestion zAxis=StepMeshPlacementCore.suggest(
                "LOCKING_SLEEVE",new double[]{-20,-30,100,40,50,900});
        require("Z→X".equals(zAxis.axis),"Z longest dimension must align to X");
        require(Math.abs(zAxis.ry-90)<1e-9,"Z→X requires +90° around Y");
        center=transform(10,10,500,zAxis);
        require(norm(center)<1e-8,"rotated sleeve center must reach origin");

        StepMeshPlacementCore.Suggestion support=StepMeshPlacementCore.suggest(
                "SUPPORT",new double[]{200,-50,20,600,350,520});
        require("X".equals(support.axis),"support orientation must be preserved");
        require(support.rx==0&&support.ry==0&&support.rz==0,"support must not rotate automatically");
        center=transform(400,150,270,support);
        require(norm(center)<1e-8,"support center must reach origin");
        System.out.println("STEP placement v42 OK · axial orientation and rigid centering verified");
    }

    private static double[] transform(double x,double y,double z,StepMeshPlacementCore.Suggestion s){
        double ax=Math.toRadians(s.rx),ay=Math.toRadians(s.ry),az=Math.toRadians(s.rz);
        double c1=Math.cos(ax),q1=Math.sin(ax);double y1=c1*y-q1*z,z1=q1*y+c1*z;
        double c2=Math.cos(ay),q2=Math.sin(ay);double x2=c2*x+q2*z1,z2=-q2*x+c2*z1;
        double c3=Math.cos(az),q3=Math.sin(az);return new double[]{c3*x2-q3*y1+s.tx,q3*x2+c3*y1+s.ty,z2+s.tz};
    }
    private static double norm(double[] v){return Math.sqrt(v[0]*v[0]+v[1]*v[1]+v[2]*v[2]);}
    private static void require(boolean condition,String message){if(!condition)throw new AssertionError(message);}
}

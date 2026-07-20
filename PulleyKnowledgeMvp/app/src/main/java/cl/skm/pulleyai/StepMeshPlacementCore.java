package cl.skm.pulleyai;

/** Pure rigid placement suggestions from a native STEP bounding box. */
public final class StepMeshPlacementCore {
    private StepMeshPlacementCore() { }

    public static final class Suggestion {
        public final double tx,ty,tz,rx,ry,rz;
        public final String axis;
        public final String reason;

        Suggestion(double tx,double ty,double tz,double rx,double ry,double rz,
                   String axis,String reason){
            this.tx=tx;this.ty=ty;this.tz=tz;this.rx=rx;this.ry=ry;this.rz=rz;
            this.axis=axis;this.reason=reason;
        }
    }

    public static Suggestion suggest(String type,double[] bounds){
        if(bounds==null||bounds.length!=6||!finite(bounds)){
            throw new IllegalArgumentException("Bounding box STEP inválido");
        }
        double sx=bounds[3]-bounds[0],sy=bounds[4]-bounds[1],sz=bounds[5]-bounds[2];
        if(!(sx>0&&sy>0&&sz>0))throw new IllegalArgumentException("Bounding box STEP degenerado");
        double cx=(bounds[0]+bounds[3])*0.5,cy=(bounds[1]+bounds[4])*0.5,cz=(bounds[2]+bounds[5])*0.5;
        boolean axial=isAxial(type);
        double rx=0,ry=0,rz=0;
        String axis="X";
        if(axial){
            if(sy>=sx&&sy>=sz){rz=-90;axis="Y→X";}
            else if(sz>=sx&&sz>=sy){ry=90;axis="Z→X";}
        }
        double[] transformed=rotate(cx,cy,cz,rx,ry,rz);
        String reason=axial
                ?"Centrar malla y orientar su dimensión mayor sobre el eje X"
                :"Centrar la caja envolvente sin cambiar orientación";
        return new Suggestion(-transformed[0],-transformed[1],-transformed[2],
                rx,ry,rz,axis,reason);
    }

    private static boolean isAxial(String type){
        return "SHELL".equals(type)||"SHAFT".equals(type)
                ||"LOCKING_SLEEVE".equals(type)||"BEARING".equals(type)
                ||"HUB".equals(type)||"COUPLING".equals(type);
    }

    private static double[] rotate(double x,double y,double z,
                                   double rx,double ry,double rz){
        double ax=Math.toRadians(rx),ay=Math.toRadians(ry),az=Math.toRadians(rz);
        double c1=Math.cos(ax),s1=Math.sin(ax);double y1=c1*y-s1*z,z1=s1*y+c1*z;
        double c2=Math.cos(ay),s2=Math.sin(ay);double x2=c2*x+s2*z1,z2=-s2*x+c2*z1;
        double c3=Math.cos(az),s3=Math.sin(az);return new double[]{c3*x2-s3*y1,s3*x2+c3*y1,z2};
    }

    private static boolean finite(double[] values){for(double value:values)if(Double.isNaN(value)||Double.isInfinite(value))return false;return true;}
}

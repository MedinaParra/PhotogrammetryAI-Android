package cl.skm.pulleyai;

import java.util.Locale;

/** Quantitative rigid-overlay validation between a STEP shell and metric reconstruction. */
public final class CadOverlayValidationCore {
    private CadOverlayValidationCore() { }

    public enum Status { MATCH, REVIEW, BLOCKED, NOT_AVAILABLE }

    public static final class Result {
        public final Status status;
        public final double cadLengthMm,cadDiameterMm,ovalityMm;
        public final double lengthErrorMm,diameterErrorMm,radialCenterOffsetMm,axialCenterOffsetMm,axisAngleDeg;
        public final double score;
        public final String diagnostic;
        Result(Status status,double cadLength,double cadDiameter,double ovality,
               double lengthError,double diameterError,double radialOffset,double axialOffset,
               double axisAngle,double score,String diagnostic){
            this.status=status;this.cadLengthMm=cadLength;this.cadDiameterMm=cadDiameter;
            this.ovalityMm=ovality;this.lengthErrorMm=lengthError;this.diameterErrorMm=diameterError;
            this.radialCenterOffsetMm=radialOffset;this.axialCenterOffsetMm=axialOffset;
            this.axisAngleDeg=axisAngle;this.score=score;this.diagnostic=diagnostic;
        }
        public String summary(){
            if(status==Status.NOT_AVAILABLE)return diagnostic;
            return String.format(Locale.ROOT,
                    "%s · coincidencia %.0f%%\nCAD %.1f × Ø%.1f mm · errores L %.1f / Ø %.1f mm\nEje %.2f° · centro radial %.1f mm · axial %.1f mm · ovalidad %.1f mm",
                    status,score*100.0,cadLengthMm,cadDiameterMm,lengthErrorMm,diameterErrorMm,
                    axisAngleDeg,radialCenterOffsetMm,axialCenterOffsetMm,ovalityMm);
        }
    }

    public static Result evaluate(double referenceLengthMm,double referenceDiameterMm,double[] bounds,
                                  double tx,double ty,double tz,double rxDeg,double ryDeg,double rzDeg){
        if(!(referenceLengthMm>0&&referenceDiameterMm>0)||bounds==null||bounds.length!=6){
            return unavailable("Falta manto métrico o bounding box STEP");
        }
        double[] extents={bounds[3]-bounds[0],bounds[4]-bounds[1],bounds[5]-bounds[2]};
        for(double extent:extents)if(!(extent>0)||Double.isInfinite(extent)||Double.isNaN(extent))return unavailable("Bounding box STEP inválido");
        int major=extents[1]>extents[0]?1:0;if(extents[2]>extents[major])major=2;
        int first=(major+1)%3,second=(major+2)%3;
        double cadLength=extents[major];
        double crossA=extents[first],crossB=extents[second];
        double cadDiameter=(crossA+crossB)/2.0;
        double ovality=Math.abs(crossA-crossB);

        double[] localCenter={(bounds[0]+bounds[3])/2.0,(bounds[1]+bounds[4])/2.0,(bounds[2]+bounds[5])/2.0};
        double[] center=transformPoint(localCenter[0],localCenter[1],localCenter[2],tx,ty,tz,rxDeg,ryDeg,rzDeg);
        double[] axis=transformVector(major==0?1:0,major==1?1:0,major==2?1:0,rxDeg,ryDeg,rzDeg);
        double axisAngle=Math.toDegrees(Math.acos(clamp(Math.abs(axis[0]),0,1)));
        double radialOffset=Math.sqrt(center[1]*center[1]+center[2]*center[2]);
        double axialOffset=Math.abs(center[0]);
        double lengthError=Math.abs(cadLength-referenceLengthMm);
        double diameterError=Math.abs(cadDiameter-referenceDiameterMm);

        double blockLength=Math.max(15.0,referenceLengthMm*0.010);
        double blockDiameter=Math.max(15.0,referenceDiameterMm*0.020);
        double blockCenter=Math.max(12.0,referenceDiameterMm*0.020);
        double blockAxial=Math.max(15.0,referenceLengthMm*0.010);
        double blockOvality=Math.max(12.0,referenceDiameterMm*0.020);
        double blockAngle=5.0;
        double reviewLength=blockLength*0.50,reviewDiameter=blockDiameter*0.50;
        double reviewCenter=blockCenter*0.50,reviewAxial=blockAxial*0.50,reviewOvality=blockOvality*0.50,reviewAngle=2.0;

        Status status=Status.MATCH;
        String diagnostic="Superposición dentro de tolerancia de prevalidación";
        if(lengthError>blockLength||diameterError>blockDiameter||radialOffset>blockCenter
                ||axialOffset>blockAxial||ovality>blockOvality||axisAngle>blockAngle){
            status=Status.BLOCKED;diagnostic="La geometría STEP no coincide con el manto métrico";
        }else if(lengthError>reviewLength||diameterError>reviewDiameter||radialOffset>reviewCenter
                ||axialOffset>reviewAxial||ovality>reviewOvality||axisAngle>reviewAngle){
            status=Status.REVIEW;diagnostic="Coincidencia cercana; requiere revisión del operador";
        }
        double normalized=Math.max(lengthError/blockLength,Math.max(diameterError/blockDiameter,
                Math.max(radialOffset/blockCenter,Math.max(axialOffset/blockAxial,
                        Math.max(ovality/blockOvality,axisAngle/blockAngle)))));
        double score=clamp(1.0-normalized*0.55,0,1);
        return new Result(status,cadLength,cadDiameter,ovality,lengthError,diameterError,
                radialOffset,axialOffset,axisAngle,score,diagnostic);
    }

    private static Result unavailable(String message){return new Result(Status.NOT_AVAILABLE,
            Double.NaN,Double.NaN,Double.NaN,Double.NaN,Double.NaN,Double.NaN,Double.NaN,Double.NaN,0,message);}
    private static double[] transformPoint(double x,double y,double z,double tx,double ty,double tz,double rx,double ry,double rz){double[] q=rotateRaw(x,y,z,rx,ry,rz);return new double[]{q[0]+tx,q[1]+ty,q[2]+tz};}
    private static double[] transformVector(double x,double y,double z,double rx,double ry,double rz){double[] q=rotateRaw(x,y,z,rx,ry,rz);double norm=Math.sqrt(q[0]*q[0]+q[1]*q[1]+q[2]*q[2]);return norm>0?new double[]{q[0]/norm,q[1]/norm,q[2]/norm}:new double[]{0,0,0};}
    private static double[] rotateRaw(double x,double y,double z,double rx,double ry,double rz){
        double ax=Math.toRadians(rx),ay=Math.toRadians(ry),az=Math.toRadians(rz);
        double cx=Math.cos(ax),sx=Math.sin(ax);double y1=cx*y-sx*z,z1=sx*y+cx*z;
        double cy=Math.cos(ay),sy=Math.sin(ay);double x2=cy*x+sy*z1,z2=-sy*x+cy*z1;
        double cz=Math.cos(az),sz=Math.sin(az);double x3=cz*x2-sz*y1,y3=sz*x2+cz*y1;
        return new double[]{x3,y3,z2};
    }
    private static double clamp(double value,double min,double max){return Math.max(min,Math.min(max,value));}
}

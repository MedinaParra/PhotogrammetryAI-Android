package cl.skm.pulleyai;

import java.util.Locale;

/** Bounded tessellation settings chosen from mechanical role, file size and memory budget. */
public final class StepTessellationPolicyCore {
    private StepTessellationPolicyCore() { }

    public static final class Plan {
        public final double linearDeflectionMm,angularDeflectionDegrees;
        public final int expectedTriangleBudget;
        public final String quality,reason;
        Plan(double linear,double angular,int budget,String quality,String reason){
            this.linearDeflectionMm=linear;this.angularDeflectionDegrees=angular;
            this.expectedTriangleBudget=budget;this.quality=quality;this.reason=reason;
        }
        public String summary(){return String.format(Locale.ROOT,
                "%s · deflexión %.3f mm · ángulo %.1f° · presupuesto %,d triángulos · %s",
                quality,linearDeflectionMm,angularDeflectionDegrees,expectedTriangleBudget,reason);}
    }

    public static Plan select(String componentType,long fileBytes,long maxMemoryBytes){
        String type=componentType==null?"OTHER":componentType.toUpperCase(Locale.ROOT);
        double linear=0.25,angular=15.0;
        int budget=700_000;
        String role="geometría general";
        if(type.contains("SHAFT")||type.contains("SHELL")||type.contains("LOCKING")
                ||type.contains("BEARING")||type.contains("HUB")||type.contains("COUPLING")){
            linear=0.15;angular=12.0;budget=900_000;role="pieza axial";
        }else if(type.contains("SUPPORT")){
            linear=0.30;angular=17.0;budget=650_000;role="soporte con detalles fundidos";
        }

        double sizeFactor=1.0;
        String pressure="archivo normal";
        if(fileBytes>150L*1024L*1024L){sizeFactor=2.5;budget=Math.min(budget,350_000);pressure="archivo mayor a 150 MB";}
        else if(fileBytes>50L*1024L*1024L){sizeFactor=1.65;budget=Math.min(budget,500_000);pressure="archivo mayor a 50 MB";}
        else if(fileBytes>15L*1024L*1024L){sizeFactor=1.25;budget=Math.min(budget,650_000);pressure="archivo mayor a 15 MB";}

        double memoryFactor=1.0;
        if(maxMemoryBytes>0&&maxMemoryBytes<384L*1024L*1024L){memoryFactor=1.5;budget=Math.min(budget,300_000);pressure+=", memoria limitada";}
        else if(maxMemoryBytes>0&&maxMemoryBytes<768L*1024L*1024L){memoryFactor=1.2;budget=Math.min(budget,500_000);pressure+=", memoria moderada";}

        linear=clamp(linear*sizeFactor*memoryFactor,0.10,1.20);
        angular=clamp(angular*Math.sqrt(sizeFactor*memoryFactor),10.0,28.0);
        String quality=linear<=0.20?"DETALLE":linear<=0.45?"EQUILIBRADA":"MEMORIA SEGURA";
        return new Plan(linear,angular,budget,quality,role+"; "+pressure);
    }

    public static boolean withinBudget(int triangleCount,Plan plan){
        return plan!=null&&triangleCount>0&&triangleCount<=plan.expectedTriangleBudget;
    }

    private static double clamp(double value,double min,double max){return Math.max(min,Math.min(max,value));}
}

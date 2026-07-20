import cl.skm.pulleyai.StepTessellationPolicyCore;

public final class StepTessellationPolicyV44Test {
    public static void main(String[] args){
        StepTessellationPolicyCore.Plan shaft=StepTessellationPolicyCore.select(
                "SHAFT",2L*1024L*1024L,1024L*1024L*1024L);
        require(close(shaft.linearDeflectionMm,0.15),"eje pequeño debe conservar detalle");
        require(shaft.expectedTriangleBudget==900_000,"presupuesto eje incorrecto");
        require("DETALLE".equals(shaft.quality),"calidad eje incorrecta");

        StepTessellationPolicyCore.Plan support=StepTessellationPolicyCore.select(
                "SUPPORT",180L*1024L*1024L,320L*1024L*1024L);
        require(support.linearDeflectionMm>=1.0&&support.linearDeflectionMm<=1.2,
                "soporte grande debe usar malla segura");
        require(support.angularDeflectionDegrees==28.0,"ángulo debe quedar limitado");
        require(support.expectedTriangleBudget==300_000,"presupuesto de memoria limitada incorrecto");
        require("MEMORIA SEGURA".equals(support.quality),"calidad soporte grande incorrecta");

        StepTessellationPolicyCore.Plan generic=StepTessellationPolicyCore.select(
                "OTHER",20L*1024L*1024L,600L*1024L*1024L);
        require(generic.linearDeflectionMm>0.25,"archivo mediano debe relajar deflexión");
        require(generic.expectedTriangleBudget==500_000,"memoria moderada debe limitar triángulos");
        require(StepTessellationPolicyCore.withinBudget(499_999,generic),"conteo dentro de presupuesto rechazado");
        require(!StepTessellationPolicyCore.withinBudget(500_001,generic),"conteo excesivo aceptado");
        require(!StepTessellationPolicyCore.withinBudget(0,generic),"malla vacía aceptada");
        System.out.println("STEP tessellation policy OK: "+shaft.summary()+" | "+support.summary());
    }
    private static boolean close(double a,double b){return Math.abs(a-b)<1e-9;}
    private static void require(boolean condition,String message){if(!condition)throw new AssertionError(message);}
}

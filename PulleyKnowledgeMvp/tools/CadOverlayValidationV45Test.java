import cl.skm.pulleyai.CadOverlayValidationCore;

public final class CadOverlayValidationV45Test {
    public static void main(String[] args){
        double[] aligned={-760,-400,-400,760,400,400};
        CadOverlayValidationCore.Result match=CadOverlayValidationCore.evaluate(
                1520,800,aligned,0,0,0,0,0,0);
        require(match.status==CadOverlayValidationCore.Status.MATCH,"coincidencia exacta rechazada");
        require(close(match.score,1.0),"score exacto incorrecto");
        require(close(match.axisAngleDeg,0),"ángulo exacto incorrecto");

        CadOverlayValidationCore.Result review=CadOverlayValidationCore.evaluate(
                1520,800,aligned,0,9,0,0,0,0);
        require(review.status==CadOverlayValidationCore.Status.REVIEW,"desplazamiento moderado no marcado REVIEW");

        CadOverlayValidationCore.Result blocked=CadOverlayValidationCore.evaluate(
                1520,800,aligned,0,30,0,0,0,8);
        require(blocked.status==CadOverlayValidationCore.Status.BLOCKED,"geometría desviada no bloqueada");

        double[] axialY={-400,-760,-400,400,760,400};
        CadOverlayValidationCore.Result rotated=CadOverlayValidationCore.evaluate(
                1520,800,axialY,0,0,0,0,0,-90);
        require(rotated.status==CadOverlayValidationCore.Status.MATCH,"eje Y rigidamente orientado no reconocido");
        require(rotated.axisAngleDeg<1e-6,"eje Y no quedó alineado a X");

        CadOverlayValidationCore.Result invalid=CadOverlayValidationCore.evaluate(
                0,800,aligned,0,0,0,0,0,0);
        require(invalid.status==CadOverlayValidationCore.Status.NOT_AVAILABLE,"referencia faltante no detectada");
        System.out.println("CAD overlay validation OK: "+match.summary().replace('\n',' ') + " | " + review.status + " | " + blocked.status);
    }
    private static boolean close(double a,double b){return Math.abs(a-b)<1e-9;}
    private static void require(boolean condition,String message){if(!condition)throw new AssertionError(message);}
}

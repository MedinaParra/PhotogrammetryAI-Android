import cl.skm.pulleyai.StepSelfTestModel;

public final class StepSelfTestModelV43Test {
    public static void main(String[] args){
        String first=StepSelfTestModel.boxStep();
        String second=StepSelfTestModel.boxStep();
        require(first.equals(second),"STEP patrón no es determinista");
        require(StepSelfTestModel.structurallyValid(first),"STEP patrón no supera validación estructural");
        require(count(first,"ADVANCED_FACE(")==6,"debe contener seis caras avanzadas");
        require(count(first,"POLY_LOOP(")==6,"debe contener seis contornos");
        require(count(first,"CARTESIAN_POINT(")==8,"debe contener ocho vértices");
        require(first.contains("100.000000,50.000000,20.000000"),"falta vértice dimensional máximo");
        require(first.contains("SI_UNIT(.MILLI.,.METRE.)"),"la unidad debe ser milímetro");
        String sha=StepSelfTestModel.sha256();
        require(sha.length()==64&&sha.matches("[0-9a-f]{64}"),"SHA-256 inválido");
        require(sha.equals(StepSelfTestModel.sha256()),"SHA-256 no es estable");
        System.out.println("STEP self-test model OK: bytes="+first.getBytes(java.nio.charset.StandardCharsets.UTF_8).length+" sha256="+sha);
    }
    private static int count(String value,String token){int result=0,index=0;while((index=value.indexOf(token,index))>=0){result++;index+=token.length();}return result;}
    private static void require(boolean condition,String message){if(!condition)throw new AssertionError(message);}
}

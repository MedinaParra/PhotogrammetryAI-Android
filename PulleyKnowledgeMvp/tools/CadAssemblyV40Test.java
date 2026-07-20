import cl.skm.pulleyai.FreeCadNativeBridge;

import java.io.File;
import java.io.FileOutputStream;
import java.nio.charset.StandardCharsets;

public final class CadAssemblyV40Test {
    public static void main(String[] args) throws Exception {
        double[] rigid={
                0,-1,0,125,
                1,0,0,-40,
                0,0,1,80,
                0,0,0,1
        };
        require(FreeCadNativeBridge.rigid(rigid,1e-8),"rotation and translation must be accepted");
        double[] scaled=rigid.clone();scaled[0]*=1.1;scaled[1]*=1.1;
        require(!FreeCadNativeBridge.rigid(scaled,1e-6),"scale must be rejected");

        File valid=File.createTempFile("support_", ".step");
        try(FileOutputStream output=new FileOutputStream(valid)){
            output.write(("ISO-10303-21;\nHEADER;\nFILE_DESCRIPTION(('SKF support'),'2;1');\n"+
                    "ENDSEC;\nDATA;\nENDSEC;\nEND-ISO-10303-21;\n").getBytes(StandardCharsets.US_ASCII));
        }
        String hash=FreeCadNativeBridge.validateStep(valid);
        require(hash.length()==64,"STEP must have SHA-256");

        File invalid=File.createTempFile("invalid_", ".step");
        try(FileOutputStream output=new FileOutputStream(invalid)){
            output.write("not a STEP document".getBytes(StandardCharsets.US_ASCII));
        }
        boolean rejected=false;
        try{FreeCadNativeBridge.validateStep(invalid);}catch(IllegalArgumentException expected){rejected=true;}
        require(rejected,"invalid STEP header must be rejected");
        valid.delete();invalid.delete();
        System.out.println("CAD assembly v40 OK · STEP hash "+hash.substring(0,12)+" · rigid alignment enforced");
    }

    private static void require(boolean condition,String message){if(!condition)throw new AssertionError(message);}
}

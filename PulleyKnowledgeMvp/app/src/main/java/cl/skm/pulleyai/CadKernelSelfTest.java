package cl.skm.pulleyai;

import android.content.Context;
import android.content.SharedPreferences;

import java.io.File;
import java.io.FileOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.Locale;

/** Executes a real OCCT STEP import and tessellation smoke test on the Android device. */
public final class CadKernelSelfTest {
    private static final String PREFS="cad_kernel_self_test";
    private static final String KEY_LAST="last_result";
    private CadKernelSelfTest() { }

    public static final class Result {
        public final boolean success;
        public final String runtime,diagnostic,sourceSha256;
        public final int vertexCount,triangleCount;
        public final double sizeX,sizeY,sizeZ,maxDimensionErrorMm;
        public final long elapsedMs,completedAt;

        Result(boolean success,String runtime,String diagnostic,String sourceSha256,
               int vertexCount,int triangleCount,double sizeX,double sizeY,double sizeZ,
               double maxDimensionErrorMm,long elapsedMs,long completedAt){
            this.success=success;this.runtime=runtime;this.diagnostic=diagnostic;
            this.sourceSha256=sourceSha256;this.vertexCount=vertexCount;
            this.triangleCount=triangleCount;this.sizeX=sizeX;this.sizeY=sizeY;
            this.sizeZ=sizeZ;this.maxDimensionErrorMm=maxDimensionErrorMm;
            this.elapsedMs=elapsedMs;this.completedAt=completedAt;
        }

        public String summary(){
            String state=success?"APROBADA":"FALLIDA";
            return state+" · "+runtime+"\n"
                    +vertexCount+" vértices · "+triangleCount+" triángulos · "+elapsedMs+" ms\n"
                    +String.format(Locale.ROOT,"Caja %.3f × %.3f × %.3f mm · error máx. %.3f mm",
                    sizeX,sizeY,sizeZ,maxDimensionErrorMm)
                    +(diagnostic==null||diagnostic.isEmpty()?"":"\n"+diagnostic);
        }

        String serialize(){
            return (success?"1":"0")+"\t"+clean(runtime)+"\t"+clean(diagnostic)+"\t"
                    +clean(sourceSha256)+"\t"+vertexCount+"\t"+triangleCount+"\t"
                    +sizeX+"\t"+sizeY+"\t"+sizeZ+"\t"+maxDimensionErrorMm+"\t"
                    +elapsedMs+"\t"+completedAt;
        }
    }

    public static Result run(Context context){
        long started=System.nanoTime();
        long completedAt=System.currentTimeMillis();
        File file=new File(context.getCacheDir(),"skm_cad_kernel_self_test.step");
        try{
            String step=StepSelfTestModel.boxStep();
            if(!StepSelfTestModel.structurallyValid(step))throw new IllegalStateException("Modelo STEP patrón inválido");
            try(FileOutputStream output=new FileOutputStream(file,false)){
                output.write(step.getBytes(StandardCharsets.UTF_8));output.getFD().sync();
            }
            CadCoreStepImporter.ImportResult imported=CadCoreStepImporter.importStep(
                    context.getApplicationContext(),file,0.10,12.0);
            CadMeshCache.Mesh mesh=imported.mesh;
            if(mesh==null||mesh.bounds==null||mesh.bounds.length!=6)throw new IllegalStateException("El kernel no devolvió bounding box");
            double sx=mesh.bounds[3]-mesh.bounds[0];
            double sy=mesh.bounds[4]-mesh.bounds[1];
            double sz=mesh.bounds[5]-mesh.bounds[2];
            double error=Math.max(Math.abs(sx-StepSelfTestModel.SIZE_X_MM),
                    Math.max(Math.abs(sy-StepSelfTestModel.SIZE_Y_MM),Math.abs(sz-StepSelfTestModel.SIZE_Z_MM)));
            if(mesh.vertexCount()<8||mesh.triangleCount()<12)throw new IllegalStateException("Teselación insuficiente");
            if(error>0.50)throw new IllegalStateException(String.format(Locale.ROOT,
                    "Bounding box fuera de tolerancia: %.3f mm",error));
            String expected=StepSelfTestModel.sha256();
            if(imported.sourceSha256!=null&&!imported.sourceSha256.isEmpty()
                    &&!expected.equalsIgnoreCase(imported.sourceSha256)){
                throw new IllegalStateException("SHA-256 del STEP patrón no coincide");
            }
            Result result=new Result(true,imported.runtime,"Kernel STEP, teselación y bounding box operativos",
                    expected,mesh.vertexCount(),mesh.triangleCount(),sx,sy,sz,error,
                    elapsed(started),completedAt);
            save(context,result);return result;
        }catch(Throwable error){
            CadCoreStepImporter.Status status=CadCoreStepImporter.status(context);
            Result result=new Result(false,status.runtime,message(error),StepSelfTestModel.sha256(),
                    0,0,Double.NaN,Double.NaN,Double.NaN,Double.POSITIVE_INFINITY,
                    elapsed(started),completedAt);
            save(context,result);return result;
        }finally{if(file.isFile())file.delete();}
    }

    public static String lastSummary(Context context){
        String raw=context.getSharedPreferences(PREFS,Context.MODE_PRIVATE).getString(KEY_LAST,null);
        if(raw==null||raw.isEmpty())return "Autoprueba aún no ejecutada";
        try{
            String[] p=raw.split("\\t",-1);if(p.length!=12)return "Registro de autoprueba incompatible";
            Result result=new Result("1".equals(p[0]),p[1],p[2],p[3],Integer.parseInt(p[4]),
                    Integer.parseInt(p[5]),Double.parseDouble(p[6]),Double.parseDouble(p[7]),
                    Double.parseDouble(p[8]),Double.parseDouble(p[9]),Long.parseLong(p[10]),Long.parseLong(p[11]));
            return result.summary();
        }catch(Exception ignored){return "Registro de autoprueba corrupto";}
    }

    private static void save(Context context,Result result){
        SharedPreferences preferences=context.getSharedPreferences(PREFS,Context.MODE_PRIVATE);
        preferences.edit().putString(KEY_LAST,result.serialize()).apply();
    }
    private static long elapsed(long started){return Math.max(0L,(System.nanoTime()-started)/1_000_000L);}
    private static String clean(String value){return value==null?"":value.replace('\t',' ').replace('\n',' ').replace('\r',' ');}
    private static String message(Throwable error){Throwable current=error;while(current.getCause()!=null&&current.getCause()!=current)current=current.getCause();String text=current.getMessage();return current.getClass().getSimpleName()+(text==null?"":": "+text);}
}

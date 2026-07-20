package cl.skm.pulleyai;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;

import java.io.File;

/** Coordinates auditable STEP validation, OCCT tessellation and atomic mesh caching. */
public final class StepMeshImportController {
    private StepMeshImportController() { }

    public interface Callback {
        void onStarted(CadCoreStepImporter.Status status);
        void onSuccess(CadMeshCache.Record record,CadCoreStepImporter.ImportResult result);
        void onFailure(String status,String diagnostic);
    }

    public static void importAsync(Context context,CadAssemblyStore assemblyStore,
                                   CadMeshCache meshCache,CadAssemblyStore.Component component,
                                   Callback callback){
        final Context app=context.getApplicationContext();
        final Handler main=new Handler(Looper.getMainLooper());
        final CadCoreStepImporter.Status availability=CadCoreStepImporter.status(app);
        if(callback!=null)callback.onStarted(availability);
        new Thread(new Runnable(){
            @Override public void run(){
                try{
                    if(component==null||component.sourceKind!=CadAssemblyStore.SourceKind.STEP){
                        throw new IllegalArgumentException("El componente no corresponde a un STEP");
                    }
                    File file=new File(component.sourcePath==null?"":component.sourcePath);
                    String validatedSha=FreeCadNativeBridge.validateStep(file);
                    if(component.sourceSha256!=null&&!component.sourceSha256.equals(validatedSha)){
                        throw new IllegalStateException("SHA-256 cambió desde la importación documental");
                    }
                    if(!availability.aarPresent){
                        throw new IllegalStateException("El AAR cadcore no está empaquetado en esta APK");
                    }
                    if(!availability.stepReady){
                        throw new IllegalStateException(availability.diagnostic.isEmpty()
                                ?"El kernel OCCT STEP aún no está disponible":availability.diagnostic);
                    }
                    CadCoreStepImporter.ImportResult result=CadCoreStepImporter.importStep(
                            app,file,0.20,15.0);
                    if(result.sourceSha256!=null&&!result.sourceSha256.equals(validatedSha)){
                        throw new IllegalStateException("El kernel devolvió un hash distinto del archivo validado");
                    }
                    CadMeshCache.Record record=meshCache.save(component.id,validatedSha,result.mesh);
                    assemblyStore.updateKernelStatus(component.id,"MESH_READY · "+result.runtime
                            +" · "+record.vertexCount+" vértices · "+record.triangleCount+" triángulos");
                    main.post(new Runnable(){@Override public void run(){if(callback!=null)callback.onSuccess(record,result);}});
                }catch(Throwable error){
                    String diagnostic=message(error);
                    String status=availability.aarPresent?"STEP_KERNEL_ERROR":"STEP_PENDIENTE_KERNEL";
                    meshCache.saveFailure(component==null?"unknown":component.id,
                            component==null?null:component.sourceSha256,status,diagnostic);
                    if(component!=null)assemblyStore.updateKernelStatus(component.id,status+" · "+diagnostic);
                    main.post(new Runnable(){@Override public void run(){if(callback!=null)callback.onFailure(status,diagnostic);}});
                }
            }
        },"SkmStepImport").start();
    }

    private static String message(Throwable error){
        Throwable current=error;
        while(current.getCause()!=null&&current.getCause()!=current)current=current.getCause();
        String text=current.getMessage();
        return current.getClass().getSimpleName()+(text==null?"":": "+text);
    }
}

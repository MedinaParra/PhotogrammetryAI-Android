package cl.skm.pulleyai;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;

import java.io.File;

/** Coordinates audited STEP validation, kernel approval, bounded tessellation and atomic mesh caching. */
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
                String failureStatus="STEP_KERNEL_ERROR";
                try{
                    if(component==null||component.sourceKind!=CadAssemblyStore.SourceKind.STEP){
                        throw new KernelGateException("STEP_COMPONENT_INVALID","El componente no corresponde a un STEP");
                    }
                    File file=new File(component.sourcePath==null?"":component.sourcePath);
                    String validatedSha=FreeCadNativeBridge.validateStep(file);
                    if(component.sourceSha256!=null&&!component.sourceSha256.equals(validatedSha)){
                        throw new KernelGateException("STEP_EVIDENCE_CHANGED","SHA-256 cambió desde la importación documental");
                    }
                    if(!availability.aarPresent){
                        throw new KernelGateException("STEP_PENDIENTE_KERNEL","El AAR cadcore no está empaquetado en esta APK");
                    }
                    if(!availability.stepReady){
                        throw new KernelGateException("STEP_KERNEL_UNAVAILABLE",availability.diagnostic.isEmpty()
                                ?"El kernel OCCT STEP aún no está disponible":availability.diagnostic);
                    }
                    if(!CadKernelSelfTest.approvedFor(app,availability.runtime)){
                        throw new KernelGateException("STEP_SELF_TEST_REQUIRED",
                                CadKernelSelfTest.approvalDiagnostic(app,availability.runtime)
                                        +". Ejecute AUTOPRUEBA KERNEL STEP antes de procesar piezas de taller.");
                    }

                    StepTessellationPolicyCore.Plan plan=StepTessellationPolicyCore.select(
                            component.type.name(),file.length(),Runtime.getRuntime().maxMemory());
                    double linear=plan.linearDeflectionMm;
                    double angular=plan.angularDeflectionDegrees;
                    CadCoreStepImporter.ImportResult result=null;
                    int attempts=0;
                    for(int attempt=1;attempt<=3;attempt++){
                        attempts=attempt;
                        result=CadCoreStepImporter.importStep(app,file,linear,angular);
                        if(result.sourceSha256!=null&&!result.sourceSha256.equals(validatedSha)){
                            throw new KernelGateException("STEP_KERNEL_HASH_MISMATCH","El kernel devolvió un hash distinto del archivo validado");
                        }
                        if(StepTessellationPolicyCore.withinBudget(result.mesh.triangleCount(),plan))break;
                        if(attempt==3){
                            throw new KernelGateException("STEP_MESH_BUDGET_EXCEEDED",
                                    "La teselación generó "+result.mesh.triangleCount()+" triángulos; presupuesto "+plan.expectedTriangleBudget);
                        }
                        linear=Math.min(1.50,linear*1.80);
                        angular=Math.min(30.0,angular*1.25);
                    }
                    if(result==null)throw new IllegalStateException("El kernel no devolvió resultado");
                    CadMeshCache.Record record=meshCache.save(component.id,validatedSha,result.mesh);
                    final String policy=plan.summary()+" · intento "+attempts;
                    assemblyStore.updateKernelStatus(component.id,"MESH_READY · "+result.runtime
                            +" · "+record.vertexCount+" vértices · "+record.triangleCount+" triángulos · "+policy);
                    final CadCoreStepImporter.ImportResult delivered=result;
                    main.post(new Runnable(){@Override public void run(){if(callback!=null)callback.onSuccess(record,delivered);}});
                }catch(Throwable error){
                    if(error instanceof KernelGateException)failureStatus=((KernelGateException)error).status;
                    String diagnostic=message(error);
                    meshCache.saveFailure(component==null?"unknown":component.id,
                            component==null?null:component.sourceSha256,failureStatus,diagnostic);
                    if(component!=null)assemblyStore.updateKernelStatus(component.id,failureStatus+" · "+diagnostic);
                    final String reportedStatus=failureStatus;
                    main.post(new Runnable(){@Override public void run(){if(callback!=null)callback.onFailure(reportedStatus,diagnostic);}});
                }
            }
        },"SkmStepImport").start();
    }

    private static final class KernelGateException extends IllegalStateException {
        final String status;
        KernelGateException(String status,String message){super(message);this.status=status;}
    }

    private static String message(Throwable error){
        Throwable current=error;
        while(current.getCause()!=null&&current.getCause()!=current)current=current.getCause();
        String text=current.getMessage();
        return current.getClass().getSimpleName()+(text==null?"":": "+text);
    }
}

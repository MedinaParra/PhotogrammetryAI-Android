package cl.skm.pulleyai;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.graphics.Color;
import android.os.Bundle;
import android.view.Gravity;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/** Processes registered STEP evidence through the OCCT cadcore AAR. */
public final class StepKernelActivity extends Activity {
    public static final String EXTRA_SESSION_ID="session_id";
    private CadAssemblyStore assemblyStore;
    private CadMeshCache meshCache;
    private CaptureStore captureStore;
    private ReconstructionResultStore resultStore;
    private String sessionId,assemblyId;
    private CaptureStore.Session session;
    private ReconstructionResultStore.Snapshot reconstruction;
    private TextView runtimeView,progressView,selfTestView;
    private LinearLayout listContainer;
    private boolean busy,destroyed;

    @Override protected void onCreate(Bundle state){
        super.onCreate(state);
        sessionId=getIntent().getStringExtra(EXTRA_SESSION_ID);
        if(sessionId==null||sessionId.trim().isEmpty())sessionId="standalone";
        assemblyStore=new CadAssemblyStore(this);
        meshCache=new CadMeshCache(this);
        captureStore=new CaptureStore(this);
        resultStore=new ReconstructionResultStore(this);
        session=captureStore.getSession(sessionId);
        reconstruction=resultStore.find(sessionId);
        CadCoreStepImporter.Status runtime=CadCoreStepImporter.status(this);
        assemblyId=assemblyStore.ensureAssembly(sessionId,
                session==null?"Ensamblaje de polea":session.label,runtime.runtime);
        buildUi();refresh();
    }

    @Override protected void onDestroy(){
        destroyed=true;
        assemblyStore.close();meshCache.close();captureStore.close();resultStore.close();super.onDestroy();
    }

    private void buildUi(){
        LinearLayout root=new LinearLayout(this);root.setOrientation(LinearLayout.HORIZONTAL);
        root.setPadding(dp(16),dp(14),dp(16),dp(14));root.setBackgroundColor(Color.rgb(239,243,246));
        LinearLayout controls=vertical();controls.setPadding(dp(12),dp(10),dp(16),dp(10));
        root.addView(controls,new LinearLayout.LayoutParams(0,-1,0.85f));
        TextView title=text("KERNEL STEP NATIVO",24,true);title.setTextColor(Color.rgb(20,62,89));controls.addView(title);
        runtimeView=text("",13,true);runtimeView.setPadding(dp(10),dp(10),dp(10),dp(10));controls.addView(runtimeView);
        selfTestView=text("",12,false);selfTestView.setPadding(dp(10),dp(8),dp(10),dp(8));selfTestView.setBackgroundColor(Color.rgb(225,231,235));controls.addView(selfTestView);
        progressView=text("Sin procesamiento activo",13,false);progressView.setPadding(0,dp(12),0,dp(10));controls.addView(progressView);
        Button selfTest=button("AUTOPRUEBA KERNEL STEP");selfTest.setOnClickListener(v->runSelfTest());controls.addView(selfTest);
        Button processAll=button("PROCESAR TODOS LOS STEP");processAll.setOnClickListener(v->processAll());controls.addView(processAll);
        Button audit=button("AUDITAR SUPERPOSICIÓN");audit.setOnClickListener(v->auditOverlay());controls.addView(audit);
        Button assembly=button("VOLVER A ENSAMBLAJE");assembly.setOnClickListener(v->openAssembly());controls.addView(assembly);
        TextView note=text("El kernel no escala modelos. Importa, tesela y conserva las dimensiones STEP; CENTRAR/ORIENTAR aplica solo rotación y traslación rígidas. Ejecute la autoprueba después de instalar o actualizar la APK.",12,false);note.setPadding(0,dp(14),0,0);controls.addView(note);

        ScrollView scroll=new ScrollView(this);listContainer=vertical();listContainer.setPadding(dp(12),dp(6),dp(12),dp(20));scroll.addView(listContainer);
        root.addView(scroll,new LinearLayout.LayoutParams(0,-1,1.55f));
        setContentView(root);
    }

    private void refresh(){
        CadCoreStepImporter.Status runtime=CadCoreStepImporter.status(this);
        runtimeView.setText("AAR: "+(runtime.aarPresent?"presente":"ausente")
                +"\nRuntime: "+runtime.runtime
                +"\nSTEP: "+(runtime.stepReady?"LISTO":"NO DISPONIBLE")
                +"\nPuerta industrial: "+CadKernelSelfTest.approvalDiagnostic(this,runtime.runtime)
                +(runtime.diagnostic==null||runtime.diagnostic.isEmpty()?"":"\n"+runtime.diagnostic));
        boolean approved=runtime.stepReady&&CadKernelSelfTest.approvedFor(this,runtime.runtime);
        runtimeView.setTextColor(approved?Color.rgb(20,115,65):Color.rgb(155,83,0));
        runtimeView.setBackgroundColor(approved?Color.rgb(215,246,226):Color.rgb(255,239,205));
        selfTestView.setText("ÚLTIMA AUTOPRUEBA\n"+CadKernelSelfTest.lastSummary(this));
        listContainer.removeAllViews();
        List<CadAssemblyStore.Component> steps=stepComponents();
        if(steps.isEmpty()){
            listContainer.addView(text("No hay componentes STEP en este ensamblaje. Agréguelos desde la pantalla CAD.",14,false));
            return;
        }
        for(CadAssemblyStore.Component component:steps)listContainer.addView(card(component));
    }

    private void runSelfTest(){
        if(busy){Toast.makeText(this,"Hay otro proceso CAD activo",Toast.LENGTH_SHORT).show();return;}
        busy=true;progressView.setText("Generando STEP patrón y comprobando OCCT…");refresh();
        new Thread(new Runnable(){
            @Override public void run(){
                final CadKernelSelfTest.Result result=CadKernelSelfTest.run(getApplicationContext());
                if(destroyed)return;
                runOnUiThread(new Runnable(){@Override public void run(){
                    if(destroyed)return;
                    busy=false;progressView.setText(result.summary());refresh();
                    Toast.makeText(StepKernelActivity.this,result.success?
                            "Kernel STEP aprobado":"Autoprueba STEP fallida: revise diagnóstico",Toast.LENGTH_LONG).show();
                }});
            }
        },"cad-kernel-self-test").start();
    }

    private void auditOverlay(){
        Double length=session==null?null:session.shellLengthMm;
        Double diameter=reconstruction==null?null:reconstruction.diameterMm;
        if(length==null||diameter==null||!(length>0&&diameter>0)){
            new AlertDialog.Builder(this).setTitle("Auditoría de superposición")
                    .setMessage("Se requiere largo real del manto y diámetro métrico reconstruido antes de auditar un STEP.")
                    .setPositiveButton("CERRAR",null).show();return;
        }
        StringBuilder body=new StringBuilder();int evaluated=0;
        for(CadAssemblyStore.Component component:stepComponents()){
            if(component.type!=CadAssemblyStore.Type.SHELL)continue;
            try{
                CadMeshCache.Mesh mesh=meshCache.load(component.id);if(mesh==null)continue;
                CadOverlayValidationCore.Result result=CadOverlayValidationCore.evaluate(length,diameter,mesh.bounds,
                        component.txMm,component.tyMm,component.tzMm,
                        component.rxDeg,component.ryDeg,component.rzDeg);
                if(evaluated++>0)body.append("\n\n");
                body.append(component.name).append("\n").append(result.summary())
                        .append("\n").append(result.diagnostic);
            }catch(Exception error){
                if(evaluated++>0)body.append("\n\n");
                body.append(component.name).append("\nERROR · ").append(error.getMessage());
            }
        }
        if(evaluated==0)body.append("No hay un componente STEP de tipo MANTO con malla procesada.");
        body.append(String.format(Locale.ROOT,"\n\nReferencia fotogramétrica: L %.1f mm · Ø %.1f mm",length,diameter));
        new AlertDialog.Builder(this).setTitle("Auditoría cuantitativa STEP")
                .setMessage(body.toString()).setPositiveButton("CERRAR",null).show();
    }

    private LinearLayout card(final CadAssemblyStore.Component component){
        LinearLayout card=vertical();card.setPadding(dp(12),dp(10),dp(12),dp(10));
        LinearLayout.LayoutParams params=new LinearLayout.LayoutParams(-1,-2);params.setMargins(0,dp(7),0,0);card.setLayoutParams(params);card.setBackgroundColor(Color.WHITE);
        TextView name=text(component.name+" · "+component.type.name(),16,true);name.setTextColor(component.colorArgb);card.addView(name);
        CadMeshCache.Record record=meshCache.find(component.id);
        String mesh=record==null?"Sin malla":record.status+(record.ready()?" · "+record.vertexCount+" vértices · "+record.triangleCount+" triángulos":"")
                +(record.diagnostic==null?"":"\n"+record.diagnostic);
        card.addView(text("SHA-256: "+shortHash(component.sourceSha256)+"\n"+component.kernelStatus+"\n"+mesh,12,false));
        LinearLayout actions=new LinearLayout(this);actions.setOrientation(LinearLayout.HORIZONTAL);actions.setGravity(Gravity.CENTER_VERTICAL);
        Button process=button(record!=null&&record.ready()?"RETESSELAR":"PROCESAR");
        process.setEnabled(!busy);process.setOnClickListener(v->processOne(component,null));actions.addView(process,new LinearLayout.LayoutParams(0,-2,1f));
        Button place=button("CENTRAR / ORIENTAR");
        place.setEnabled(record!=null&&record.ready()&&!component.locked&&!busy);
        place.setOnClickListener(v->autoPlace(component));actions.addView(place,new LinearLayout.LayoutParams(0,-2,1f));
        Button show=button("VER CONJUNTO");show.setOnClickListener(v->openAssembly());actions.addView(show,new LinearLayout.LayoutParams(0,-2,1f));card.addView(actions);
        return card;
    }

    private void autoPlace(CadAssemblyStore.Component component){
        try{
            CadMeshCache.Mesh mesh=meshCache.load(component.id);
            if(mesh==null)throw new IllegalStateException("Primero procese el STEP");
            StepMeshPlacementCore.Suggestion suggestion=StepMeshPlacementCore.suggest(component.type.name(),mesh.bounds);
            assemblyStore.updateTransform(component.id,suggestion.tx,suggestion.ty,suggestion.tz,
                    suggestion.rx,suggestion.ry,suggestion.rz);
            progressView.setText(component.name+": "+suggestion.reason+" · "+suggestion.axis);
            Toast.makeText(this,"Transformación rígida aplicada. Revise la superposición antes de bloquear la pieza.",Toast.LENGTH_LONG).show();
            refresh();
        }catch(Exception error){Toast.makeText(this,"No se pudo autoorientar: "+error.getMessage(),Toast.LENGTH_LONG).show();}
    }

    private void processAll(){
        if(busy)return;
        List<CadAssemblyStore.Component> pending=new ArrayList<CadAssemblyStore.Component>();
        for(CadAssemblyStore.Component component:stepComponents()){
            CadMeshCache.Record record=meshCache.find(component.id);
            if(record==null||!record.ready())pending.add(component);
        }
        if(pending.isEmpty()){
            Toast.makeText(this,"Todos los STEP ya tienen malla. Use RETESSELAR para actualizar uno.",Toast.LENGTH_LONG).show();return;
        }
        busy=true;processQueue(pending,0,0);
    }

    private void processQueue(List<CadAssemblyStore.Component> queue,int index,int success){
        if(index>=queue.size()){
            busy=false;progressView.setText("Proceso terminado: "+success+" de "+queue.size()+" STEP teselados.");refresh();return;
        }
        CadAssemblyStore.Component component=queue.get(index);
        progressView.setText("Procesando "+(index+1)+"/"+queue.size()+": "+component.name);
        processOne(component,new Completion(){
            @Override public void done(boolean ok){processQueue(queue,index+1,success+(ok?1:0));}
        });
    }

    private void processOne(CadAssemblyStore.Component component,Completion completion){
        if(busy&&completion==null)return;
        if(completion==null)busy=true;
        StepMeshImportController.importAsync(this,assemblyStore,meshCache,component,
                new StepMeshImportController.Callback(){
                    @Override public void onStarted(CadCoreStepImporter.Status status){
                        progressView.setText("Inicializando "+status.runtime+" para "+component.name+"…");
                    }
                    @Override public void onSuccess(CadMeshCache.Record record,CadCoreStepImporter.ImportResult result){
                        if(completion==null)busy=false;
                        progressView.setText(component.name+": malla lista · "+record.vertexCount+" vértices · "+record.triangleCount+" triángulos");
                        refresh();if(completion!=null)completion.done(true);
                    }
                    @Override public void onFailure(String status,String diagnostic){
                        if(completion==null)busy=false;
                        progressView.setText(component.name+": "+status+"\n"+diagnostic);
                        refresh();if(completion!=null)completion.done(false);
                    }
                });
    }

    private List<CadAssemblyStore.Component> stepComponents(){
        List<CadAssemblyStore.Component> result=new ArrayList<CadAssemblyStore.Component>();
        for(CadAssemblyStore.Component component:assemblyStore.components(assemblyId)){
            if(component.sourceKind==CadAssemblyStore.SourceKind.STEP)result.add(component);
        }
        return result;
    }

    private void openAssembly(){
        Intent intent=new Intent(this,CadAssemblyActivity.class);
        intent.putExtra(CadAssemblyActivity.EXTRA_SESSION_ID,sessionId);startActivity(intent);
    }

    private interface Completion{void done(boolean ok);}
    private LinearLayout vertical(){LinearLayout layout=new LinearLayout(this);layout.setOrientation(LinearLayout.VERTICAL);return layout;}
    private TextView text(String value,int sp,boolean bold){TextView view=new TextView(this);view.setText(value);view.setTextSize(sp);view.setTextColor(Color.rgb(35,39,42));if(bold)view.setTypeface(android.graphics.Typeface.DEFAULT_BOLD);return view;}
    private Button button(String label){Button button=new Button(this);button.setText(label);button.setAllCaps(false);button.setMinHeight(dp(44));return button;}
    private int dp(int value){return Math.round(value*getResources().getDisplayMetrics().density);}
    private static String shortHash(String value){return value==null?"sin hash":value.substring(0,Math.min(16,value.length()));}
}

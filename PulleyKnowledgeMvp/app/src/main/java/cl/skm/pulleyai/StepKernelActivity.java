package cl.skm.pulleyai;

import android.app.Activity;
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

/** Processes registered STEP evidence through the optional OCCT cadcore AAR. */
public final class StepKernelActivity extends Activity {
    public static final String EXTRA_SESSION_ID="session_id";
    private CadAssemblyStore assemblyStore;
    private CadMeshCache meshCache;
    private CaptureStore captureStore;
    private String sessionId,assemblyId;
    private CaptureStore.Session session;
    private TextView runtimeView,progressView;
    private LinearLayout listContainer;
    private boolean busy;

    @Override protected void onCreate(Bundle state){
        super.onCreate(state);
        sessionId=getIntent().getStringExtra(EXTRA_SESSION_ID);
        if(sessionId==null||sessionId.trim().isEmpty())sessionId="standalone";
        assemblyStore=new CadAssemblyStore(this);
        meshCache=new CadMeshCache(this);
        captureStore=new CaptureStore(this);
        session=captureStore.getSession(sessionId);
        CadCoreStepImporter.Status runtime=CadCoreStepImporter.status(this);
        assemblyId=assemblyStore.ensureAssembly(sessionId,
                session==null?"Ensamblaje de polea":session.label,runtime.runtime);
        buildUi();refresh();
    }

    @Override protected void onDestroy(){
        assemblyStore.close();meshCache.close();captureStore.close();super.onDestroy();
    }

    private void buildUi(){
        LinearLayout root=new LinearLayout(this);root.setOrientation(LinearLayout.HORIZONTAL);
        root.setPadding(dp(16),dp(14),dp(16),dp(14));root.setBackgroundColor(Color.rgb(239,243,246));
        LinearLayout controls=vertical();controls.setPadding(dp(12),dp(10),dp(16),dp(10));
        root.addView(controls,new LinearLayout.LayoutParams(0,-1,0.85f));
        TextView title=text("KERNEL STEP NATIVO",24,true);title.setTextColor(Color.rgb(20,62,89));controls.addView(title);
        runtimeView=text("",13,true);runtimeView.setPadding(dp(10),dp(10),dp(10),dp(10));controls.addView(runtimeView);
        progressView=text("Sin procesamiento activo",13,false);progressView.setPadding(0,dp(12),0,dp(10));controls.addView(progressView);
        Button processAll=button("PROCESAR TODOS LOS STEP");processAll.setOnClickListener(v->processAll());controls.addView(processAll);
        Button assembly=button("VOLVER A ENSAMBLAJE");assembly.setOnClickListener(v->openAssembly());controls.addView(assembly);
        TextView note=text("El kernel no escala modelos. Importa, tesela y conserva las dimensiones STEP; la superposición usa únicamente rotación y traslación.",12,false);note.setPadding(0,dp(14),0,0);controls.addView(note);

        ScrollView scroll=new ScrollView(this);listContainer=vertical();listContainer.setPadding(dp(12),dp(6),dp(12),dp(20));scroll.addView(listContainer);
        root.addView(scroll,new LinearLayout.LayoutParams(0,-1,1.55f));
        setContentView(root);
    }

    private void refresh(){
        CadCoreStepImporter.Status runtime=CadCoreStepImporter.status(this);
        runtimeView.setText("AAR: "+(runtime.aarPresent?"presente":"ausente")
                +"\nRuntime: "+runtime.runtime
                +"\nSTEP: "+(runtime.stepReady?"LISTO":"NO DISPONIBLE")
                +(runtime.diagnostic==null||runtime.diagnostic.isEmpty()?"":"\n"+runtime.diagnostic));
        runtimeView.setTextColor(runtime.stepReady?Color.rgb(20,115,65):Color.rgb(155,83,0));
        runtimeView.setBackgroundColor(runtime.stepReady?Color.rgb(215,246,226):Color.rgb(255,239,205));
        listContainer.removeAllViews();
        List<CadAssemblyStore.Component> steps=stepComponents();
        if(steps.isEmpty()){
            listContainer.addView(text("No hay componentes STEP en este ensamblaje. Agréguelos desde la pantalla CAD.",14,false));
            return;
        }
        for(CadAssemblyStore.Component component:steps)listContainer.addView(card(component));
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
        Button show=button("VER CONJUNTO");show.setOnClickListener(v->openAssembly());actions.addView(show,new LinearLayout.LayoutParams(0,-2,1f));card.addView(actions);
        return card;
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

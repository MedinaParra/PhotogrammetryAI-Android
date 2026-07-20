package cl.skm.pulleyai;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.ContentResolver;
import android.content.DialogInterface;
import android.content.Intent;
import android.graphics.Color;
import android.net.Uri;
import android.os.Bundle;
import android.provider.OpenableColumns;
import android.text.InputType;
import android.view.Gravity;
import android.view.View;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

/** Horizontal assembly station for parametric parts, STEP evidence and rigid overlay. */
public final class CadAssemblyActivity extends Activity {
    public static final String EXTRA_SESSION_ID = "session_id";
    private static final int PICK_STEP = 4102;

    private CadAssemblyStore store;
    private CaptureStore captureStore;
    private ReconstructionResultStore resultStore;
    private String sessionId;
    private String assemblyId;
    private CaptureStore.Session session;
    private ReconstructionResultStore.Snapshot reconstruction;
    private FreeCadNativeBridge.Status coreStatus;
    private CadOverlayView overlay;
    private LinearLayout componentList;
    private TextView summary;
    private Uri pendingStepUri;

    @Override protected void onCreate(Bundle state) {
        super.onCreate(state);
        sessionId=getIntent().getStringExtra(EXTRA_SESSION_ID);
        if(sessionId==null||sessionId.trim().isEmpty())sessionId="standalone";
        store=new CadAssemblyStore(this);captureStore=new CaptureStore(this);
        resultStore=new ReconstructionResultStore(this);session=captureStore.getSession(sessionId);
        reconstruction=resultStore.find(sessionId);coreStatus=FreeCadNativeBridge.status();
        assemblyId=store.ensureAssembly(sessionId,session==null?"Ensamblaje de polea":session.label,coreStatus.label());
        buildUi();refresh();
    }

    @Override protected void onDestroy(){store.close();captureStore.close();resultStore.close();super.onDestroy();}

    private void buildUi(){
        LinearLayout root=new LinearLayout(this);root.setOrientation(LinearLayout.HORIZONTAL);root.setBackgroundColor(Color.rgb(235,239,242));
        LinearLayout left=new LinearLayout(this);left.setOrientation(LinearLayout.VERTICAL);left.setPadding(dp(8),dp(8),dp(8),dp(8));
        root.addView(left,new LinearLayout.LayoutParams(0,-1,1.75f));
        overlay=new CadOverlayView(this);left.addView(overlay,new LinearLayout.LayoutParams(-1,0,1f));
        LinearLayout tools=new LinearLayout(this);tools.setOrientation(LinearLayout.HORIZONTAL);tools.setGravity(Gravity.CENTER_VERTICAL);
        Button reset=button("REINICIAR VISTA");reset.setOnClickListener(v->overlay.resetView());tools.addView(reset,new LinearLayout.LayoutParams(0,-2,1f));
        Button base=button("CONJUNTO BASE");base.setOnClickListener(v->createBaseAssembly());tools.addView(base,new LinearLayout.LayoutParams(0,-2,1f));
        left.addView(tools);

        ScrollView scroll=new ScrollView(this);LinearLayout panel=vertical();panel.setPadding(dp(14),dp(12),dp(14),dp(24));scroll.addView(panel);
        root.addView(scroll,new LinearLayout.LayoutParams(0,-1,1.05f));
        TextView title=text("ENSAMBLAJE CAD",23,true);title.setTextColor(Color.rgb(22,59,82));panel.addView(title);
        summary=text("",12,false);summary.setPadding(0,dp(5),0,dp(8));panel.addView(summary);
        TextView core=text(coreDescription(),12,true);core.setPadding(dp(10),dp(8),dp(10),dp(8));core.setBackgroundColor(coreStatus.supportsStep()?Color.rgb(210,245,220):Color.rgb(255,239,205));panel.addView(core);

        panel.addView(section("AGREGAR COMPONENTE"));
        LinearLayout row1=new LinearLayout(this);row1.setOrientation(LinearLayout.HORIZONTAL);
        addTypeButton(row1,"MANTO",CadAssemblyStore.Type.SHELL);addTypeButton(row1,"EJE",CadAssemblyStore.Type.SHAFT);panel.addView(row1);
        LinearLayout row2=new LinearLayout(this);row2.setOrientation(LinearLayout.HORIZONTAL);
        addTypeButton(row2,"SOPORTE",CadAssemblyStore.Type.SUPPORT);addTypeButton(row2,"MANGUITO",CadAssemblyStore.Type.LOCKING_SLEEVE);panel.addView(row2);
        LinearLayout row3=new LinearLayout(this);row3.setOrientation(LinearLayout.HORIZONTAL);
        addTypeButton(row3,"RODAMIENTO",CadAssemblyStore.Type.BEARING);addTypeButton(row3,"CUBO/ACOPLE",CadAssemblyStore.Type.COUPLING);panel.addView(row3);
        Button step=button("IMPORTAR STEP / STP");step.setOnClickListener(v->pickStep());panel.addView(step);
        panel.addView(section("COMPONENTES DEL CONJUNTO"));componentList=vertical();panel.addView(componentList);
        setContentView(root);
    }

    private void addTypeButton(LinearLayout row,String label,CadAssemblyStore.Type type){Button button=button(label);button.setOnClickListener(v->showPrimitiveDialog(type));row.addView(button,new LinearLayout.LayoutParams(0,-2,1f));}

    private void refresh(){
        List<CadAssemblyStore.Component> components=store.components(assemblyId);
        Double referenceLength=session==null?null:session.shellLengthMm;
        Double referenceDiameter=reconstruction==null?null:reconstruction.diameterMm;
        overlay.setAssembly(components,referenceLength,referenceDiameter);
        componentList.removeAllViews();
        if(components.isEmpty())componentList.addView(text("Aún no hay piezas. Puede crear el conjunto base o agregar componentes individualmente.",12,false));
        for(CadAssemblyStore.Component component:components)componentList.addView(componentCard(component));
        String reconstructionText=reconstruction==null?"sin reconstrucción guardada":reconstruction.status+(reconstruction.diameterMm==null?"":String.format(Locale.ROOT," · Ø %.1f ± %.1f mm",reconstruction.diameterMm,reconstruction.diameterUncertaintyMm==null?0:reconstruction.diameterUncertaintyMm));
        summary.setText((session==null?"Ensamblaje independiente":session.label)+"\nReconstrucción: "+reconstructionText+"\nComponentes: "+components.size());
    }

    private View componentCard(final CadAssemblyStore.Component component){
        LinearLayout card=vertical();card.setPadding(dp(10),dp(8),dp(10),dp(8));LinearLayout.LayoutParams cp=new LinearLayout.LayoutParams(-1,-2);cp.setMargins(0,dp(6),0,0);card.setLayoutParams(cp);card.setBackgroundColor(Color.WHITE);
        TextView name=text(component.name+" · "+component.type.name(),15,true);name.setTextColor(component.colorArgb);card.addView(name);
        String dims=component.sourceKind==CadAssemblyStore.SourceKind.STEP
                ? "STEP: "+shortHash(component.sourceSha256)+"\n"+component.kernelStatus
                : dimensions(component);
        dims+=String.format(Locale.ROOT,"\nT [%.1f, %.1f, %.1f] mm · R [%.1f°, %.1f°, %.1f°]",component.txMm,component.tyMm,component.tzMm,component.rxDeg,component.ryDeg,component.rzDeg);
        card.addView(text(dims,11,false));
        LinearLayout flags=new LinearLayout(this);flags.setOrientation(LinearLayout.HORIZONTAL);
        CheckBox visible=new CheckBox(this);visible.setText("Visible");visible.setChecked(component.visible);visible.setOnCheckedChangeListener((button,checked)->{store.setVisible(component.id,checked);refresh();});flags.addView(visible,new LinearLayout.LayoutParams(0,-2,1f));
        CheckBox locked=new CheckBox(this);locked.setText("Bloqueado");locked.setChecked(component.locked);locked.setOnCheckedChangeListener((button,checked)->{store.setLocked(component.id,checked);refresh();});flags.addView(locked,new LinearLayout.LayoutParams(0,-2,1f));card.addView(flags);
        LinearLayout actions=new LinearLayout(this);actions.setOrientation(LinearLayout.HORIZONTAL);
        Button edit=button("TRANSFORMAR");edit.setEnabled(!component.locked);edit.setOnClickListener(v->showTransformDialog(component));actions.addView(edit,new LinearLayout.LayoutParams(0,-2,1f));
        Button delete=button("ELIMINAR");delete.setEnabled(!component.locked);delete.setOnClickListener(v->confirmDelete(component));actions.addView(delete,new LinearLayout.LayoutParams(0,-2,1f));card.addView(actions);return card;
    }

    private void showPrimitiveDialog(final CadAssemblyStore.Type type){
        final boolean cylinder=type!=CadAssemblyStore.Type.SUPPORT&&type!=CadAssemblyStore.Type.STEP_OTHER;
        LinearLayout form=vertical();form.setPadding(dp(16),0,dp(16),0);
        EditText name=input("Nombre",InputType.TYPE_CLASS_TEXT);form.addView(name);
        EditText a=input(cylinder?"Largo axial (mm)":"Ancho X (mm)",numberType());form.addView(a);
        EditText b=input(cylinder?"Diámetro exterior (mm)":"Alto Y (mm)",numberType());form.addView(b);
        EditText c=input(cylinder?"Diámetro interior / agujero (mm, opcional)":"Profundidad Z (mm)",numberType());form.addView(c);
        prefill(type,a,b,c);
        AlertDialog dialog=new AlertDialog.Builder(this).setTitle("Agregar "+type.name()).setView(form).setPositiveButton("AGREGAR",null).setNegativeButton("CANCELAR",null).create();
        dialog.setOnShowListener(ignored->dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v->{
            Double first=parsePositive(a.getText().toString()),second=parsePositive(b.getText().toString());Double third=parseNonNegative(c.getText().toString());
            if(first==null){a.setError("Ingrese una dimensión válida");return;}if(second==null){b.setError("Ingrese una dimensión válida");return;}if(third==null)third=0.0;
            if(cylinder&&third>=second){c.setError("El agujero debe ser menor que el diámetro exterior");return;}
            int color=colorFor(type);if(cylinder)store.addParametric(assemblyId,type,name.getText().toString(),first,second,0,0,0,third,color);
            else store.addParametric(assemblyId,type,name.getText().toString(),0,0,first,second,third,0,color);
            dialog.dismiss();refresh();
        }));dialog.show();
    }

    private void showTransformDialog(final CadAssemblyStore.Component component){
        LinearLayout form=vertical();form.setPadding(dp(16),0,dp(16),0);EditText[] values=new EditText[6];String[] hints={"Traslación X mm","Traslación Y mm","Traslación Z mm","Rotación X °","Rotación Y °","Rotación Z °"};double[] current={component.txMm,component.tyMm,component.tzMm,component.rxDeg,component.ryDeg,component.rzDeg};
        for(int i=0;i<values.length;i++){values[i]=input(hints[i],signedNumberType());values[i].setText(String.format(Locale.ROOT,"%.3f",current[i]));form.addView(values[i]);}
        AlertDialog dialog=new AlertDialog.Builder(this).setTitle("Transformación rígida").setMessage("Solo traslación y rotación. No se permite escalar piezas ni STEP para forzar coincidencia.").setView(form).setPositiveButton("APLICAR",null).setNegativeButton("CANCELAR",null).create();
        dialog.setOnShowListener(ignored->dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v->{double[] parsed=new double[6];for(int i=0;i<6;i++){Double value=parseFinite(values[i].getText().toString());if(value==null){values[i].setError("Valor inválido");return;}parsed[i]=value;}store.updateTransform(component.id,parsed[0],parsed[1],parsed[2],parsed[3],parsed[4],parsed[5]);dialog.dismiss();refresh();}));dialog.show();
    }

    private void createBaseAssembly(){
        if(!store.components(assemblyId).isEmpty()){Toast.makeText(this,"El conjunto ya contiene piezas.",Toast.LENGTH_LONG).show();return;}
        double length=session!=null&&session.shellLengthMm!=null?session.shellLengthMm:1520;
        double diameter=reconstruction!=null&&reconstruction.diameterMm!=null?reconstruction.diameterMm:800;
        store.addParametric(assemblyId,CadAssemblyStore.Type.SHELL,"Manto",length,diameter,0,0,0,0,colorFor(CadAssemblyStore.Type.SHELL));
        store.addParametric(assemblyId,CadAssemblyStore.Type.SHAFT,"Eje",length+900,Math.max(80,diameter*0.22),0,0,0,0,colorFor(CadAssemblyStore.Type.SHAFT));
        String left=store.addParametric(assemblyId,CadAssemblyStore.Type.SUPPORT,"Soporte izquierdo",0,0,diameter*0.42,diameter*0.62,diameter*0.28,0,colorFor(CadAssemblyStore.Type.SUPPORT));
        String right=store.addParametric(assemblyId,CadAssemblyStore.Type.SUPPORT,"Soporte derecho",0,0,diameter*0.42,diameter*0.62,diameter*0.28,0,colorFor(CadAssemblyStore.Type.SUPPORT));
        store.updateTransform(left,-length/2-280,0,-diameter*0.35,0,0,0);store.updateTransform(right,length/2+280,0,-diameter*0.35,0,0,0);refresh();
    }

    private void pickStep(){Intent intent=new Intent(Intent.ACTION_OPEN_DOCUMENT);intent.addCategory(Intent.CATEGORY_OPENABLE);intent.setType("*/*");intent.putExtra(Intent.EXTRA_MIME_TYPES,new String[]{"application/step","application/x-step","model/step","application/octet-stream"});startActivityForResult(intent,PICK_STEP);}

    @Override protected void onActivityResult(int requestCode,int resultCode,Intent data){super.onActivityResult(requestCode,resultCode,data);if(requestCode!=PICK_STEP||resultCode!=RESULT_OK||data==null||data.getData()==null)return;pendingStepUri=data.getData();try{getContentResolver().takePersistableUriPermission(pendingStepUri,Intent.FLAG_GRANT_READ_URI_PERMISSION);}catch(Exception ignored){}showStepTypeDialog();}

    private void showStepTypeDialog(){final CadAssemblyStore.Type[] types={CadAssemblyStore.Type.SUPPORT,CadAssemblyStore.Type.SHAFT,CadAssemblyStore.Type.SHELL,CadAssemblyStore.Type.LOCKING_SLEEVE,CadAssemblyStore.Type.BEARING,CadAssemblyStore.Type.HUB,CadAssemblyStore.Type.COUPLING,CadAssemblyStore.Type.STEP_OTHER};String[] labels={"Soporte","Eje","Manto","Manguito de fijación","Rodamiento","Cubo","Acople","Otro STEP"};new AlertDialog.Builder(this).setTitle("Tipo del componente STEP").setItems(labels,(dialog,which)->importStep(types[which])).setNegativeButton("CANCELAR",null).show();}

    private void importStep(CadAssemblyStore.Type type){if(pendingStepUri==null)return;try{String displayName=queryName(pendingStepUri);File target=new File(store.importDir(assemblyId),UUID.randomUUID()+"_"+sanitize(displayName));copy(pendingStepUri,target);String sha=FreeCadNativeBridge.validateStep(target);String kernel=coreStatus.supportsStep()?"STEP_KERNEL_DISPONIBLE_PENDIENTE_TESELAR":"STEP_PENDIENTE_KERNEL";store.addStep(assemblyId,type,displayName,target,sha,kernel,colorFor(type));Toast.makeText(this,"STEP registrado con SHA-256 "+shortHash(sha),Toast.LENGTH_LONG).show();refresh();}catch(Exception error){Toast.makeText(this,"No se pudo importar STEP: "+error.getMessage(),Toast.LENGTH_LONG).show();}finally{pendingStepUri=null;}}

    private void confirmDelete(final CadAssemblyStore.Component component){new AlertDialog.Builder(this).setTitle("Eliminar componente").setMessage(component.name).setPositiveButton("ELIMINAR",(dialog,which)->{store.delete(component.id);refresh();}).setNegativeButton("CANCELAR",null).show();}

    private String coreDescription(){String capabilities=coreStatus.supportsStep()?"STEP + teselación disponibles":"STEP se registra; kernel OpenCascade pendiente";return "Motor CAD: "+coreStatus.runtimeInfo+"\n"+capabilities+(coreStatus.diagnostic.isEmpty()?"":"\n"+coreStatus.diagnostic);}
    private String dimensions(CadAssemblyStore.Component c){if(c.type==CadAssemblyStore.Type.SUPPORT)return String.format(Locale.ROOT,"%.1f × %.1f × %.1f mm",c.widthMm,c.heightMm,c.depthMm);return String.format(Locale.ROOT,"L %.1f · Ø %.1f%s",c.lengthMm,c.diameterMm,c.boreMm>0?String.format(Locale.ROOT," · agujero Ø %.1f",c.boreMm):"");}
    private void prefill(CadAssemblyStore.Type type,EditText a,EditText b,EditText c){double length=session!=null&&session.shellLengthMm!=null?session.shellLengthMm:1520;double diameter=reconstruction!=null&&reconstruction.diameterMm!=null?reconstruction.diameterMm:800;if(type==CadAssemblyStore.Type.SHELL){a.setText(fmt(length));b.setText(fmt(diameter));c.setText("0");}else if(type==CadAssemblyStore.Type.SHAFT){a.setText(fmt(length+900));b.setText(fmt(Math.max(80,diameter*0.22)));c.setText("0");}else if(type==CadAssemblyStore.Type.SUPPORT){a.setText(fmt(diameter*0.42));b.setText(fmt(diameter*0.62));c.setText(fmt(diameter*0.28));}else if(type==CadAssemblyStore.Type.LOCKING_SLEEVE){a.setText("180");b.setText(fmt(Math.max(120,diameter*0.28)));c.setText(fmt(Math.max(80,diameter*0.22)));}else{a.setText("180");b.setText("260");c.setText("0");}}
    private static int colorFor(CadAssemblyStore.Type type){switch(type){case SHELL:return Color.rgb(65,155,220);case SHAFT:return Color.rgb(220,185,75);case SUPPORT:return Color.rgb(185,105,220);case LOCKING_SLEEVE:return Color.rgb(235,125,70);case BEARING:return Color.rgb(95,210,165);case HUB:return Color.rgb(235,95,125);case COUPLING:return Color.rgb(125,175,245);default:return Color.LTGRAY;}}
    private String queryName(Uri uri){String name=null;ContentResolver resolver=getContentResolver();android.database.Cursor cursor=resolver.query(uri,new String[]{OpenableColumns.DISPLAY_NAME},null,null,null);if(cursor!=null){try{if(cursor.moveToFirst())name=cursor.getString(0);}finally{cursor.close();}}return name==null?"componente.step":name;}
    private void copy(Uri uri,File target)throws Exception{try(InputStream input=getContentResolver().openInputStream(uri);FileOutputStream output=new FileOutputStream(target)){if(input==null)throw new IllegalStateException("No se pudo abrir el documento");byte[] buffer=new byte[65536];int count;long total=0;while((count=input.read(buffer))>=0){if(count==0)continue;total+=count;if(total>300L*1024L*1024L)throw new IllegalArgumentException("STEP supera el límite de 300 MB");output.write(buffer,0,count);}output.getFD().sync();}if(target.length()<32)throw new IllegalArgumentException("STEP vacío o incompleto");}
    private LinearLayout vertical(){LinearLayout layout=new LinearLayout(this);layout.setOrientation(LinearLayout.VERTICAL);return layout;}
    private TextView section(String value){TextView view=text(value,12,true);view.setTextColor(Color.rgb(35,84,117));view.setPadding(0,dp(14),0,dp(4));return view;}
    private TextView text(String value,int sp,boolean bold){TextView view=new TextView(this);view.setText(value);view.setTextSize(sp);view.setTextColor(Color.rgb(35,39,42));if(bold)view.setTypeface(android.graphics.Typeface.DEFAULT_BOLD);return view;}
    private Button button(String label){Button button=new Button(this);button.setText(label);button.setAllCaps(false);button.setMinHeight(dp(42));return button;}
    private EditText input(String hint,int type){EditText edit=new EditText(this);edit.setHint(hint);edit.setInputType(type);edit.setSingleLine(true);return edit;}
    private int numberType(){return InputType.TYPE_CLASS_NUMBER|InputType.TYPE_NUMBER_FLAG_DECIMAL;}
    private int signedNumberType(){return InputType.TYPE_CLASS_NUMBER|InputType.TYPE_NUMBER_FLAG_DECIMAL|InputType.TYPE_NUMBER_FLAG_SIGNED;}
    private int dp(int value){return Math.round(value*getResources().getDisplayMetrics().density);}
    private static Double parsePositive(String raw){Double value=parseFinite(raw);return value!=null&&value>0?value:null;}
    private static Double parseNonNegative(String raw){if(raw==null||raw.trim().isEmpty())return 0.0;Double value=parseFinite(raw);return value!=null&&value>=0?value:null;}
    private static Double parseFinite(String raw){try{double value=Double.parseDouble(raw.trim().replace(',','.'));return Double.isNaN(value)||Double.isInfinite(value)?null:value;}catch(Exception ignored){return null;}}
    private static String sanitize(String value){String clean=value==null?"componente.step":value.replaceAll("[^A-Za-z0-9._-]","_");String lower=clean.toLowerCase(Locale.ROOT);return lower.endsWith(".step")||lower.endsWith(".stp")?clean:clean+".step";}
    private static String shortHash(String value){return value==null?"sin hash":value.substring(0,Math.min(12,value.length()));}
    private static String fmt(double value){return String.format(Locale.ROOT,"%.1f",value);}
}

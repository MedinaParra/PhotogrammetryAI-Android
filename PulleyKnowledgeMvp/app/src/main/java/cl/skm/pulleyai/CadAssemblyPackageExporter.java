package cl.skm.pulleyai;

import android.content.Context;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Locale;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

/** Produces a portable assembly manifest while preserving original STEP evidence. */
public final class CadAssemblyPackageExporter {
    private CadAssemblyPackageExporter() { }

    public static File build(Context context, CadAssemblyStore store, String assemblyId,
                             String sessionId, CaptureStore.Session session,
                             ReconstructionResultStore.Snapshot reconstruction,
                             FreeCadNativeBridge.Status core) throws Exception {
        List<CadAssemblyStore.Component> components=store.components(assemblyId);
        AssemblyConstraintCore.Result constraints=AssemblyConstraintCore.evaluate(toParts(components));
        File root=new File(context.getCacheDir(),"cad_exports");
        if(!root.exists()&&!root.mkdirs())throw new IllegalStateException("No se pudo crear carpeta de exportación CAD");
        File output=new File(root,"SKM_CAD_"+safeFile(session==null?assemblyId:session.label)+"_"+System.currentTimeMillis()+".zip");
        byte[] manifest=manifest(assemblyId,sessionId,session,reconstruction,core,components,constraints)
                .getBytes(StandardCharsets.UTF_8);
        try(ZipOutputStream zip=new ZipOutputStream(new BufferedOutputStream(new FileOutputStream(output)))){
            put(zip,"assembly_manifest.json",manifest);
            for(CadAssemblyStore.Component component:components){
                if(component.sourceKind!=CadAssemblyStore.SourceKind.STEP||component.sourcePath==null)continue;
                File source=new File(component.sourcePath);if(!source.isFile())continue;
                putFile(zip,"step/"+safeFile(component.id+"_"+source.getName()),source);
            }
            File overlap=sessionId==null?null:new File(new File(context.getFilesDir(),"capture_sessions/"+sessionId),"overlap_report.json");
            if(overlap!=null&&overlap.isFile())putFile(zip,"reconstruction/overlap_report.json",overlap);
        }
        return output;
    }

    public static List<AssemblyConstraintCore.Part> toParts(List<CadAssemblyStore.Component> components){
        java.util.ArrayList<AssemblyConstraintCore.Part> parts=new java.util.ArrayList<AssemblyConstraintCore.Part>();
        if(components==null)return parts;
        for(CadAssemblyStore.Component c:components){
            if(c.sourceKind!=CadAssemblyStore.SourceKind.PARAMETRIC)continue;
            parts.add(new AssemblyConstraintCore.Part(c.id,c.type.name(),c.lengthMm,c.diameterMm,
                    c.widthMm,c.heightMm,c.depthMm,c.boreMm,c.txMm,c.tyMm,c.tzMm,
                    c.rxDeg,c.ryDeg,c.rzDeg,c.locked));
        }
        return parts;
    }

    private static String manifest(String assemblyId,String sessionId,CaptureStore.Session session,
                                   ReconstructionResultStore.Snapshot reconstruction,
                                   FreeCadNativeBridge.Status core,
                                   List<CadAssemblyStore.Component> components,
                                   AssemblyConstraintCore.Result constraints){
        StringBuilder json=new StringBuilder(4096+components.size()*620);
        json.append("{\n  \"schema\":\"skm-cad-assembly/1\",\n")
                .append("  \"assemblyId\":").append(q(assemblyId)).append(",\n")
                .append("  \"sessionId\":").append(q(sessionId)).append(",\n")
                .append("  \"exportedAt\":").append(System.currentTimeMillis()).append(",\n")
                .append("  \"cadCore\":{\"loaded\":").append(core!=null&&core.loaded)
                .append(",\"runtime\":").append(q(core==null?null:core.runtimeInfo))
                .append(",\"capabilities\":").append(core==null?0:core.capabilities)
                .append(",\"stepImport\":").append(core!=null&&core.supportsStep()).append("},\n")
                .append("  \"capture\":{\"label\":").append(q(session==null?null:session.label))
                .append(",\"materialCode\":").append(q(session==null?null:session.code))
                .append(",\"ot\":").append(q(session==null?null:session.ot))
                .append(",\"shellLengthMm\":").append(number(session==null?null:session.shellLengthMm)).append("},\n")
                .append("  \"reconstruction\":{\"status\":").append(q(reconstruction==null?null:reconstruction.status))
                .append(",\"diameterMm\":").append(number(reconstruction==null?null:reconstruction.diameterMm))
                .append(",\"diameterUncertaintyMm\":").append(number(reconstruction==null?null:reconstruction.diameterUncertaintyMm))
                .append(",\"confidence\":").append(number(reconstruction==null?null:reconstruction.confidence)).append("},\n")
                .append("  \"constraints\":{\"blocking\":").append(constraints.blocking)
                .append(",\"warnings\":").append(constraints.warnings).append(",\"issues\":[");
        for(int i=0;i<constraints.issues.size();i++){AssemblyConstraintCore.Issue issue=constraints.issues.get(i);
            if(i>0)json.append(',');json.append("{\"severity\":").append(q(issue.severity.name()))
                    .append(",\"code\":").append(q(issue.code)).append(",\"partId\":").append(q(issue.partId))
                    .append(",\"message\":").append(q(issue.message)).append('}');}
        json.append("]},\n  \"components\":[\n");
        for(int i=0;i<components.size();i++){CadAssemblyStore.Component c=components.get(i);
            json.append("    {\"id\":").append(q(c.id)).append(",\"name\":").append(q(c.name))
                    .append(",\"type\":").append(q(c.type.name())).append(",\"sourceKind\":").append(q(c.sourceKind.name()))
                    .append(",\"sourceSha256\":").append(q(c.sourceSha256)).append(",\"kernelStatus\":").append(q(c.kernelStatus))
                    .append(",\"dimensionsMm\":{\"length\":").append(f(c.lengthMm)).append(",\"diameter\":").append(f(c.diameterMm))
                    .append(",\"width\":").append(f(c.widthMm)).append(",\"height\":").append(f(c.heightMm))
                    .append(",\"depth\":").append(f(c.depthMm)).append(",\"bore\":").append(f(c.boreMm)).append("}")
                    .append(",\"transform\":{\"translationMm\":[").append(f(c.txMm)).append(',').append(f(c.tyMm)).append(',').append(f(c.tzMm))
                    .append("],\"rotationDeg\":[").append(f(c.rxDeg)).append(',').append(f(c.ryDeg)).append(',').append(f(c.rzDeg)).append("]}")
                    .append(",\"visible\":").append(c.visible).append(",\"locked\":").append(c.locked).append('}');
            if(i+1<components.size())json.append(',');json.append('\n');}
        json.append("  ]\n}\n");return json.toString();
    }

    private static void put(ZipOutputStream zip,String name,byte[] bytes)throws Exception{ZipEntry entry=new ZipEntry(name);entry.setTime(0);zip.putNextEntry(entry);zip.write(bytes);zip.closeEntry();}
    private static void putFile(ZipOutputStream zip,String name,File file)throws Exception{ZipEntry entry=new ZipEntry(name);entry.setTime(0);zip.putNextEntry(entry);byte[] buffer=new byte[65536];try(BufferedInputStream input=new BufferedInputStream(new FileInputStream(file))){int count;while((count=input.read(buffer))>=0)if(count>0)zip.write(buffer,0,count);}zip.closeEntry();}
    private static String q(String value){if(value==null)return "null";return "\""+value.replace("\\","\\\\").replace("\"","\\\"").replace("\n","\\n").replace("\r","\\r")+"\"";}
    private static String number(Double value){return value==null||value.isNaN()||value.isInfinite()?"null":f(value);}
    private static String f(double value){return Double.isNaN(value)||Double.isInfinite(value)?"null":String.format(Locale.ROOT,"%.8f",value);}
    private static String safeFile(String value){String clean=value==null?"assembly":value.replaceAll("[^A-Za-z0-9._-]","_");return clean.isEmpty()?"assembly":clean;}
}

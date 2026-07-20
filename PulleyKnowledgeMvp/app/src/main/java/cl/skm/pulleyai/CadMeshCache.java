package cl.skm.pulleyai;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** Binary cache for tessellated STEP meshes, independently replaceable from assembly metadata. */
public final class CadMeshCache extends SQLiteOpenHelper {
    private static final String DB_NAME="cad_mesh_cache.db";
    private static final int DB_VERSION=1;
    private static final int MAGIC=0x534B4D31; // SKM1
    private static final int FORMAT_VERSION=1;
    private static final int MAX_VERTICES=2_000_000;
    private static final int MAX_TRIANGLES=4_000_000;
    private final Context context;

    public static final class Mesh {
        public final float[] vertices;
        public final float[] normals;
        public final int[] triangles;
        public final double[] bounds;

        public Mesh(float[] vertices,float[] normals,int[] triangles,double[] bounds){
            if(vertices==null||vertices.length<9||vertices.length%3!=0)throw new IllegalArgumentException("Malla sin vértices válidos");
            if(triangles==null||triangles.length<3||triangles.length%3!=0)throw new IllegalArgumentException("Malla sin triángulos válidos");
            if(vertices.length/3>MAX_VERTICES||triangles.length/3>MAX_TRIANGLES)throw new IllegalArgumentException("Malla supera límites industriales");
            this.vertices=vertices.clone();
            this.normals=normals!=null&&normals.length==vertices.length?normals.clone():new float[vertices.length];
            this.triangles=triangles.clone();
            this.bounds=bounds!=null&&bounds.length==6?bounds.clone():computeBounds(vertices);
            validateIndices(this.triangles,vertices.length/3);
        }
        public int vertexCount(){return vertices.length/3;}
        public int triangleCount(){return triangles.length/3;}
    }

    public static final class Record {
        public final String componentId,sourceSha256,status,filePath,diagnostic;
        public final int vertexCount,triangleCount;
        public final long updatedAt;
        Record(String componentId,String sourceSha256,String status,String filePath,
               String diagnostic,int vertexCount,int triangleCount,long updatedAt){
            this.componentId=componentId;this.sourceSha256=sourceSha256;this.status=status;
            this.filePath=filePath;this.diagnostic=diagnostic;this.vertexCount=vertexCount;
            this.triangleCount=triangleCount;this.updatedAt=updatedAt;
        }
        public boolean ready(){return "MESH_READY".equals(status)&&filePath!=null;}
    }

    public CadMeshCache(Context context){
        super(context.getApplicationContext(),DB_NAME,null,DB_VERSION);
        this.context=context.getApplicationContext();
    }

    @Override public void onCreate(SQLiteDatabase db){
        db.execSQL("CREATE TABLE mesh(component_id TEXT PRIMARY KEY,source_sha256 TEXT,"+
                "status TEXT NOT NULL,file_path TEXT,diagnostic TEXT,"+
                "vertex_count INTEGER NOT NULL DEFAULT 0,triangle_count INTEGER NOT NULL DEFAULT 0,"+
                "updated_at INTEGER NOT NULL)");
        db.execSQL("CREATE INDEX mesh_hash_idx ON mesh(source_sha256)");
    }
    @Override public void onUpgrade(SQLiteDatabase db,int oldVersion,int newVersion){
        if(newVersion>DB_VERSION)throw new IllegalStateException("Missing CAD mesh migration "+oldVersion+" -> "+newVersion);
    }

    public synchronized Record save(String componentId,String sourceSha256,Mesh mesh)throws Exception{
        if(componentId==null||componentId.trim().isEmpty())throw new IllegalArgumentException("componentId requerido");
        File target=new File(root(),componentId+".skmesh");
        File temp=new File(root(),componentId+".tmp");
        write(temp,mesh);
        if(target.exists()&&!target.delete())throw new IllegalStateException("No se pudo reemplazar malla anterior");
        if(!temp.renameTo(target))throw new IllegalStateException("No se pudo confirmar malla importada");
        ContentValues row=base(componentId,sourceSha256,"MESH_READY",null);
        row.put("file_path",target.getAbsolutePath());row.put("vertex_count",mesh.vertexCount());row.put("triangle_count",mesh.triangleCount());
        getWritableDatabase().insertWithOnConflict("mesh",null,row,SQLiteDatabase.CONFLICT_REPLACE);
        return find(componentId);
    }

    public synchronized Record saveFailure(String componentId,String sourceSha256,String status,String diagnostic){
        ContentValues row=base(componentId,sourceSha256,status==null?"MESH_FAILED":status,diagnostic);
        getWritableDatabase().insertWithOnConflict("mesh",null,row,SQLiteDatabase.CONFLICT_REPLACE);
        return find(componentId);
    }

    public Record find(String componentId){
        Cursor c=getReadableDatabase().query("mesh",null,"component_id=?",new String[]{componentId},null,null,null,"1");
        try{return c.moveToFirst()?read(c):null;}finally{c.close();}
    }

    public Mesh load(String componentId)throws Exception{
        Record record=find(componentId);if(record==null||!record.ready())return null;
        File file=new File(record.filePath);if(!file.isFile()){saveFailure(componentId,record.sourceSha256,"MESH_FILE_MISSING","Archivo de malla eliminado");return null;}
        return readMesh(file);
    }

    public Map<String,Mesh> loadReady(List<CadAssemblyStore.Component> components){
        if(components==null||components.isEmpty())return Collections.emptyMap();
        Map<String,Mesh> result=new HashMap<String,Mesh>();
        for(CadAssemblyStore.Component component:components){
            try{Mesh mesh=load(component.id);if(mesh!=null)result.put(component.id,mesh);}
            catch(Exception error){saveFailure(component.id,component.sourceSha256,"MESH_CORRUPT",error.getMessage());}
        }
        return result;
    }

    public synchronized void delete(String componentId){
        Record record=find(componentId);if(record!=null&&record.filePath!=null)new File(record.filePath).delete();
        getWritableDatabase().delete("mesh","component_id=?",new String[]{componentId});
    }

    private ContentValues base(String componentId,String sha,String status,String diagnostic){
        ContentValues row=new ContentValues();row.put("component_id",componentId);
        if(sha==null)row.putNull("source_sha256");else row.put("source_sha256",sha);
        row.put("status",status);if(diagnostic==null)row.putNull("diagnostic");else row.put("diagnostic",diagnostic);
        row.put("updated_at",System.currentTimeMillis());return row;
    }

    private File root(){File dir=new File(context.getFilesDir(),"cad_meshes");if(!dir.exists()&&!dir.mkdirs())throw new IllegalStateException("No se pudo crear caché CAD");return dir;}

    private static void write(File file,Mesh mesh)throws Exception{
        try(DataOutputStream out=new DataOutputStream(new BufferedOutputStream(new FileOutputStream(file)))){
            out.writeInt(MAGIC);out.writeInt(FORMAT_VERSION);out.writeInt(mesh.vertices.length);out.writeInt(mesh.normals.length);out.writeInt(mesh.triangles.length);
            for(double value:mesh.bounds)out.writeDouble(value);
            for(float value:mesh.vertices)out.writeFloat(value);
            for(float value:mesh.normals)out.writeFloat(value);
            for(int value:mesh.triangles)out.writeInt(value);
        }
    }

    private static Mesh readMesh(File file)throws Exception{
        try(DataInputStream in=new DataInputStream(new BufferedInputStream(new FileInputStream(file)))){
            if(in.readInt()!=MAGIC)throw new IllegalArgumentException("Cabecera SKMESH inválida");
            if(in.readInt()!=FORMAT_VERSION)throw new IllegalArgumentException("Versión SKMESH incompatible");
            int vertices=in.readInt(),normals=in.readInt(),triangles=in.readInt();
            if(vertices<9||vertices%3!=0||vertices/3>MAX_VERTICES)throw new IllegalArgumentException("Conteo de vértices inválido");
            if(normals!=vertices)throw new IllegalArgumentException("Conteo de normales inválido");
            if(triangles<3||triangles%3!=0||triangles/3>MAX_TRIANGLES)throw new IllegalArgumentException("Conteo de triángulos inválido");
            double[] bounds=new double[6];for(int i=0;i<6;i++)bounds[i]=in.readDouble();
            float[] v=new float[vertices];for(int i=0;i<vertices;i++)v[i]=in.readFloat();
            float[] n=new float[normals];for(int i=0;i<normals;i++)n[i]=in.readFloat();
            int[] t=new int[triangles];for(int i=0;i<triangles;i++)t[i]=in.readInt();
            if(in.read()!=-1)throw new IllegalArgumentException("Datos residuales en SKMESH");
            return new Mesh(v,n,t,bounds);
        }
    }

    private static Record read(Cursor c){return new Record(c.getString(c.getColumnIndexOrThrow("component_id")),c.getString(c.getColumnIndexOrThrow("source_sha256")),c.getString(c.getColumnIndexOrThrow("status")),c.getString(c.getColumnIndexOrThrow("file_path")),c.getString(c.getColumnIndexOrThrow("diagnostic")),c.getInt(c.getColumnIndexOrThrow("vertex_count")),c.getInt(c.getColumnIndexOrThrow("triangle_count")),c.getLong(c.getColumnIndexOrThrow("updated_at")));}
    private static void validateIndices(int[] triangles,int vertexCount){for(int value:triangles)if(value<0||value>=vertexCount)throw new IllegalArgumentException("Índice de triángulo fuera de rango");}
    private static double[] computeBounds(float[] vertices){double minX=vertices[0],minY=vertices[1],minZ=vertices[2],maxX=minX,maxY=minY,maxZ=minZ;for(int i=3;i<vertices.length;i+=3){minX=Math.min(minX,vertices[i]);maxX=Math.max(maxX,vertices[i]);minY=Math.min(minY,vertices[i+1]);maxY=Math.max(maxY,vertices[i+1]);minZ=Math.min(minZ,vertices[i+2]);maxZ=Math.max(maxZ,vertices[i+2]);}return new double[]{minX,minY,minZ,maxX,maxY,maxZ};}
}

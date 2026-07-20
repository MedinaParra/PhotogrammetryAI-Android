package cl.skm.pulleyai;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/** Local traceable store for pulley CAD assemblies. */
public final class CadAssemblyStore extends SQLiteOpenHelper {
    private static final String DB_NAME = "cad_assemblies.db";
    private static final int DB_VERSION = 1;
    private final Context appContext;

    public enum Type {
        SHELL,
        SHAFT,
        SUPPORT,
        LOCKING_SLEEVE,
        BEARING,
        HUB,
        COUPLING,
        STEP_OTHER
    }

    public enum SourceKind {
        PARAMETRIC,
        STEP
    }

    public static final class Component {
        public final String id;
        public final String assemblyId;
        public final Type type;
        public final SourceKind sourceKind;
        public final String name;
        public final String sourcePath;
        public final String sourceSha256;
        public final String kernelStatus;
        public final double lengthMm;
        public final double diameterMm;
        public final double widthMm;
        public final double heightMm;
        public final double depthMm;
        public final double boreMm;
        public final double txMm, tyMm, tzMm;
        public final double rxDeg, ryDeg, rzDeg;
        public final boolean visible;
        public final boolean locked;
        public final int colorArgb;

        Component(String id, String assemblyId, Type type, SourceKind sourceKind,
                  String name, String sourcePath, String sourceSha256,
                  String kernelStatus, double lengthMm, double diameterMm,
                  double widthMm, double heightMm, double depthMm, double boreMm,
                  double txMm, double tyMm, double tzMm,
                  double rxDeg, double ryDeg, double rzDeg,
                  boolean visible, boolean locked, int colorArgb) {
            this.id=id; this.assemblyId=assemblyId; this.type=type;
            this.sourceKind=sourceKind; this.name=name; this.sourcePath=sourcePath;
            this.sourceSha256=sourceSha256; this.kernelStatus=kernelStatus;
            this.lengthMm=lengthMm; this.diameterMm=diameterMm;
            this.widthMm=widthMm; this.heightMm=heightMm; this.depthMm=depthMm;
            this.boreMm=boreMm; this.txMm=txMm; this.tyMm=tyMm; this.tzMm=tzMm;
            this.rxDeg=rxDeg; this.ryDeg=ryDeg; this.rzDeg=rzDeg;
            this.visible=visible; this.locked=locked; this.colorArgb=colorArgb;
        }

        public boolean renderableParametric() {
            return sourceKind == SourceKind.PARAMETRIC && lengthMm > 0
                    || sourceKind == SourceKind.PARAMETRIC && widthMm > 0;
        }
    }

    public CadAssemblyStore(Context context) {
        super(context.getApplicationContext(), DB_NAME, null, DB_VERSION);
        appContext = context.getApplicationContext();
    }

    @Override public void onConfigure(SQLiteDatabase db) {
        super.onConfigure(db);
        db.setForeignKeyConstraintsEnabled(true);
    }

    @Override public void onCreate(SQLiteDatabase db) {
        db.execSQL("CREATE TABLE assembly(id TEXT PRIMARY KEY,session_id TEXT UNIQUE," +
                "name TEXT NOT NULL,created_at INTEGER NOT NULL,updated_at INTEGER NOT NULL," +
                "core_backend TEXT NOT NULL DEFAULT 'PARAMETRIC_PREVIEW'," +
                "alignment_status TEXT NOT NULL DEFAULT 'UNALIGNED')");
        db.execSQL("CREATE TABLE component(id TEXT PRIMARY KEY,assembly_id TEXT NOT NULL," +
                "type TEXT NOT NULL,source_kind TEXT NOT NULL,name TEXT NOT NULL," +
                "source_path TEXT,source_sha256 TEXT,kernel_status TEXT NOT NULL," +
                "length_mm REAL NOT NULL DEFAULT 0,diameter_mm REAL NOT NULL DEFAULT 0," +
                "width_mm REAL NOT NULL DEFAULT 0,height_mm REAL NOT NULL DEFAULT 0," +
                "depth_mm REAL NOT NULL DEFAULT 0,bore_mm REAL NOT NULL DEFAULT 0," +
                "tx_mm REAL NOT NULL DEFAULT 0,ty_mm REAL NOT NULL DEFAULT 0,tz_mm REAL NOT NULL DEFAULT 0," +
                "rx_deg REAL NOT NULL DEFAULT 0,ry_deg REAL NOT NULL DEFAULT 0,rz_deg REAL NOT NULL DEFAULT 0," +
                "visible INTEGER NOT NULL DEFAULT 1,locked INTEGER NOT NULL DEFAULT 0," +
                "color_argb INTEGER NOT NULL,created_at INTEGER NOT NULL,updated_at INTEGER NOT NULL," +
                "FOREIGN KEY(assembly_id) REFERENCES assembly(id) ON DELETE CASCADE)");
        db.execSQL("CREATE INDEX component_assembly_idx ON component(assembly_id,created_at)");
        db.execSQL("CREATE INDEX component_hash_idx ON component(source_sha256)");
    }

    @Override public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
        if (newVersion > DB_VERSION) throw new IllegalStateException(
                "Missing CAD assembly migration " + oldVersion + " -> " + newVersion);
    }

    public String ensureAssembly(String sessionId, String label, String coreBackend) {
        String stableSession = clean(sessionId, "standalone");
        Cursor cursor = getReadableDatabase().query("assembly", new String[]{"id"},
                "session_id=?", new String[]{stableSession}, null, null, null, "1");
        try {
            if (cursor.moveToFirst()) return cursor.getString(0);
        } finally { cursor.close(); }
        String id = UUID.randomUUID().toString();
        long now = System.currentTimeMillis();
        ContentValues row = new ContentValues();
        row.put("id",id); row.put("session_id",stableSession);
        row.put("name",clean(label,"Ensamblaje de polea"));
        row.put("created_at",now); row.put("updated_at",now);
        row.put("core_backend",clean(coreBackend,"PARAMETRIC_PREVIEW"));
        getWritableDatabase().insertOrThrow("assembly",null,row);
        assemblyDir(id);
        return id;
    }

    public String addParametric(String assemblyId, Type type, String name,
                                double length, double diameter, double width,
                                double height, double depth, double bore, int colorArgb) {
        return insert(assemblyId,type,SourceKind.PARAMETRIC,name,null,null,
                "PARAMETRIC_READY",length,diameter,width,height,depth,bore,colorArgb);
    }

    public String addStep(String assemblyId, Type type, String name, File source,
                          String sha256, String kernelStatus, int colorArgb) {
        return insert(assemblyId,type,SourceKind.STEP,name,
                source == null ? null : source.getAbsolutePath(),sha256,
                clean(kernelStatus,"STEP_PENDIENTE_KERNEL"),0,0,0,0,0,0,colorArgb);
    }

    private String insert(String assemblyId, Type type, SourceKind sourceKind,
                          String name, String path, String sha, String kernelStatus,
                          double length, double diameter, double width, double height,
                          double depth, double bore, int colorArgb) {
        String id=UUID.randomUUID().toString(); long now=System.currentTimeMillis();
        ContentValues row=new ContentValues(); row.put("id",id);row.put("assembly_id",assemblyId);
        row.put("type",type.name());row.put("source_kind",sourceKind.name());
        row.put("name",clean(name,type.name()));putNullable(row,"source_path",path);
        putNullable(row,"source_sha256",sha);row.put("kernel_status",kernelStatus);
        row.put("length_mm",length);row.put("diameter_mm",diameter);row.put("width_mm",width);
        row.put("height_mm",height);row.put("depth_mm",depth);row.put("bore_mm",bore);
        row.put("color_argb",colorArgb);row.put("created_at",now);row.put("updated_at",now);
        getWritableDatabase().insertOrThrow("component",null,row);touchAssembly(assemblyId);return id;
    }

    public List<Component> components(String assemblyId) {
        List<Component> result=new ArrayList<Component>();
        Cursor c=getReadableDatabase().query("component",null,"assembly_id=?",
                new String[]{assemblyId},null,null,"created_at,id");
        try { while(c.moveToNext()) result.add(read(c)); } finally { c.close(); }
        return result;
    }

    public void updateTransform(String id,double tx,double ty,double tz,
                                double rx,double ry,double rz) {
        ContentValues row=new ContentValues();row.put("tx_mm",tx);row.put("ty_mm",ty);row.put("tz_mm",tz);
        row.put("rx_deg",normalizeAngle(rx));row.put("ry_deg",normalizeAngle(ry));row.put("rz_deg",normalizeAngle(rz));
        row.put("updated_at",System.currentTimeMillis());getWritableDatabase().update("component",row,"id=?",new String[]{id});
    }

    public void setVisible(String id,boolean visible) { updateFlag(id,"visible",visible); }
    public void setLocked(String id,boolean locked) { updateFlag(id,"locked",locked); }
    public void delete(String id) { getWritableDatabase().delete("component","id=?",new String[]{id}); }

    public File assemblyDir(String assemblyId) {
        File root=new File(appContext.getFilesDir(),"cad_assemblies");
        File dir=new File(root,assemblyId);if(!dir.exists()&&!dir.mkdirs())throw new IllegalStateException("No se pudo crear "+dir);
        return dir;
    }

    public File importDir(String assemblyId) {
        File dir=new File(assemblyDir(assemblyId),"imports");if(!dir.exists()&&!dir.mkdirs())throw new IllegalStateException("No se pudo crear "+dir);return dir;
    }

    private void touchAssembly(String id){ContentValues row=new ContentValues();row.put("updated_at",System.currentTimeMillis());getWritableDatabase().update("assembly",row,"id=?",new String[]{id});}
    private void updateFlag(String id,String column,boolean value){ContentValues row=new ContentValues();row.put(column,value?1:0);row.put("updated_at",System.currentTimeMillis());getWritableDatabase().update("component",row,"id=?",new String[]{id});}
    private static Component read(Cursor c){return new Component(c.getString(c.getColumnIndexOrThrow("id")),c.getString(c.getColumnIndexOrThrow("assembly_id")),
            Type.valueOf(c.getString(c.getColumnIndexOrThrow("type"))),SourceKind.valueOf(c.getString(c.getColumnIndexOrThrow("source_kind"))),
            c.getString(c.getColumnIndexOrThrow("name")),c.getString(c.getColumnIndexOrThrow("source_path")),c.getString(c.getColumnIndexOrThrow("source_sha256")),
            c.getString(c.getColumnIndexOrThrow("kernel_status")),c.getDouble(c.getColumnIndexOrThrow("length_mm")),c.getDouble(c.getColumnIndexOrThrow("diameter_mm")),
            c.getDouble(c.getColumnIndexOrThrow("width_mm")),c.getDouble(c.getColumnIndexOrThrow("height_mm")),c.getDouble(c.getColumnIndexOrThrow("depth_mm")),c.getDouble(c.getColumnIndexOrThrow("bore_mm")),
            c.getDouble(c.getColumnIndexOrThrow("tx_mm")),c.getDouble(c.getColumnIndexOrThrow("ty_mm")),c.getDouble(c.getColumnIndexOrThrow("tz_mm")),
            c.getDouble(c.getColumnIndexOrThrow("rx_deg")),c.getDouble(c.getColumnIndexOrThrow("ry_deg")),c.getDouble(c.getColumnIndexOrThrow("rz_deg")),
            c.getInt(c.getColumnIndexOrThrow("visible"))!=0,c.getInt(c.getColumnIndexOrThrow("locked"))!=0,c.getInt(c.getColumnIndexOrThrow("color_argb")));}
    private static void putNullable(ContentValues row,String key,String value){if(value==null)row.putNull(key);else row.put(key,value);}
    private static String clean(String value,String fallback){return value==null||value.trim().isEmpty()?fallback:value.trim();}
    private static double normalizeAngle(double value){double result=value%360.0;return result>180?result-360:result<-180?result+360:result;}
}

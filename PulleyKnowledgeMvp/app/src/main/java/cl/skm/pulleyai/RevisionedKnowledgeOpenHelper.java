package cl.skm.pulleyai;

import android.content.ContentValues;
import android.content.Context;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;

import java.util.Locale;

/**
 * Non-destructive normalized SQLite store for material -> OT -> drawing -> revision -> evidence.
 * It intentionally uses a separate database while the legacy MVP store remains readable.
 */
public final class RevisionedKnowledgeOpenHelper extends SQLiteOpenHelper {
    public static final String DATABASE_NAME = "pulley_revisioned_knowledge.db";
    public static final int DATABASE_VERSION = 1;

    public RevisionedKnowledgeOpenHelper(Context context) {
        super(context.getApplicationContext(), DATABASE_NAME, null, DATABASE_VERSION);
    }

    @Override public void onConfigure(SQLiteDatabase db) {
        super.onConfigure(db);
        db.setForeignKeyConstraintsEnabled(true);
    }

    @Override public void onCreate(SQLiteDatabase db) {
        db.beginTransaction();
        try {
            createSchema(db);
            seedAuditedEvidence(db);
            db.setTransactionSuccessful();
        } finally {
            db.endTransaction();
        }
    }

    @Override public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
        throw new IllegalStateException("Revisioned knowledge migrations must be explicit and non-destructive");
    }

    public void ensureSeeded() {
        SQLiteDatabase db = getWritableDatabase();
        db.beginTransaction();
        try {
            createSchema(db);
            seedAuditedEvidence(db);
            db.setTransactionSuccessful();
        } finally {
            db.endTransaction();
        }
    }

    private static void createSchema(SQLiteDatabase db) {
        db.execSQL("CREATE TABLE IF NOT EXISTS material_family(" +
                "material_code TEXT PRIMARY KEY," +
                "created_at INTEGER NOT NULL," +
                "status TEXT NOT NULL DEFAULT 'REVIEWED')");
        db.execSQL("CREATE TABLE IF NOT EXISTS work_order(" +
                "material_code TEXT NOT NULL," +
                "ot_number TEXT NOT NULL," +
                "component_description TEXT NOT NULL DEFAULT ''," +
                "PRIMARY KEY(material_code,ot_number)," +
                "FOREIGN KEY(material_code) REFERENCES material_family(material_code) ON DELETE CASCADE)");
        db.execSQL("CREATE TABLE IF NOT EXISTS drawing(" +
                "material_code TEXT NOT NULL," +
                "ot_number TEXT NOT NULL," +
                "drawing_number TEXT NOT NULL," +
                "PRIMARY KEY(material_code,ot_number,drawing_number)," +
                "FOREIGN KEY(material_code,ot_number) REFERENCES work_order(material_code,ot_number) ON DELETE CASCADE)");
        db.execSQL("CREATE TABLE IF NOT EXISTS drawing_revision(" +
                "material_code TEXT NOT NULL," +
                "ot_number TEXT NOT NULL," +
                "drawing_number TEXT NOT NULL," +
                "revision TEXT NOT NULL," +
                "approval_state TEXT NOT NULL," +
                "source_uri TEXT NOT NULL," +
                "source_sha256 TEXT NOT NULL," +
                "PRIMARY KEY(material_code,ot_number,drawing_number,revision)," +
                "FOREIGN KEY(material_code,ot_number,drawing_number) REFERENCES drawing(material_code,ot_number,drawing_number) ON DELETE CASCADE)");
        db.execSQL("CREATE TABLE IF NOT EXISTS dimension_evidence(" +
                "material_code TEXT NOT NULL," +
                "ot_number TEXT NOT NULL," +
                "drawing_number TEXT NOT NULL," +
                "revision TEXT NOT NULL," +
                "dimension_kind TEXT NOT NULL," +
                "surface_kind TEXT NOT NULL," +
                "original_value REAL NOT NULL CHECK(original_value>0)," +
                "original_unit TEXT NOT NULL," +
                "value_mm REAL NOT NULL CHECK(value_mm>0)," +
                "drawing_tolerance_mm REAL NOT NULL CHECK(drawing_tolerance_mm>=0)," +
                "approval_state TEXT NOT NULL," +
                "source_uri TEXT NOT NULL," +
                "source_sha256 TEXT NOT NULL," +
                "extraction_method TEXT NOT NULL," +
                "confidence REAL NOT NULL CHECK(confidence>=0 AND confidence<=1)," +
                "PRIMARY KEY(material_code,ot_number,drawing_number,revision,dimension_kind,surface_kind)," +
                "FOREIGN KEY(material_code,ot_number,drawing_number,revision) REFERENCES drawing_revision(material_code,ot_number,drawing_number,revision) ON DELETE CASCADE)");
        db.execSQL("CREATE TABLE IF NOT EXISTS validation_conflict(" +
                "conflict_id TEXT PRIMARY KEY," +
                "material_code TEXT NOT NULL," +
                "severity TEXT NOT NULL," +
                "description TEXT NOT NULL," +
                "affected_ots TEXT NOT NULL DEFAULT ''," +
                "resolution_state TEXT NOT NULL DEFAULT 'OPEN')");
        db.execSQL("CREATE INDEX IF NOT EXISTS idx_revisioned_ot ON work_order(ot_number)");
        db.execSQL("CREATE INDEX IF NOT EXISTS idx_revisioned_dimension ON dimension_evidence(material_code,ot_number,dimension_kind,surface_kind)");
    }

    private static void seedAuditedEvidence(SQLiteDatabase db) {
        RevisionedPulleyKnowledgeCore.Catalog catalog = RevisionedPulleyKnowledgeCore.auditedSeed();
        for (RevisionedPulleyKnowledgeCore.DimensionEvidence evidence : catalog.allEvidence()) {
            putFamily(db, evidence.materialCode);
            putWorkOrder(db, evidence.materialCode, evidence.otNumber);
            putDrawing(db, evidence.materialCode, evidence.otNumber, evidence.drawingNumber);
            putRevision(db, evidence);
            putEvidence(db, evidence);
        }
        for (RevisionedPulleyKnowledgeCore.ValidationConflict conflict : catalog.allConflicts()) {
            ContentValues row = new ContentValues();
            row.put("conflict_id", conflict.id);
            row.put("material_code", conflict.materialCode);
            row.put("severity", conflict.severity.name());
            row.put("description", conflict.description);
            StringBuilder ots = new StringBuilder();
            for (String ot : conflict.affectedOts) {
                if (ots.length() > 0) ots.append(',');
                ots.append(ot);
            }
            row.put("affected_ots", ots.toString());
            row.put("resolution_state", "OPEN");
            db.insertWithOnConflict("validation_conflict", null, row, SQLiteDatabase.CONFLICT_REPLACE);
        }
    }

    private static void putFamily(SQLiteDatabase db, String materialCode) {
        ContentValues row = new ContentValues();
        row.put("material_code", materialCode);
        row.put("created_at", System.currentTimeMillis());
        row.put("status", "REVIEWED");
        db.insertWithOnConflict("material_family", null, row, SQLiteDatabase.CONFLICT_IGNORE);
    }

    private static void putWorkOrder(SQLiteDatabase db, String materialCode, String ot) {
        ContentValues row = new ContentValues();
        row.put("material_code", materialCode);
        row.put("ot_number", ot);
        row.put("component_description", "");
        db.insertWithOnConflict("work_order", null, row, SQLiteDatabase.CONFLICT_IGNORE);
    }

    private static void putDrawing(SQLiteDatabase db, String materialCode, String ot, String drawing) {
        ContentValues row = new ContentValues();
        row.put("material_code", materialCode);
        row.put("ot_number", ot);
        row.put("drawing_number", drawing);
        db.insertWithOnConflict("drawing", null, row, SQLiteDatabase.CONFLICT_IGNORE);
    }

    private static void putRevision(SQLiteDatabase db, RevisionedPulleyKnowledgeCore.DimensionEvidence evidence) {
        ContentValues row = new ContentValues();
        row.put("material_code", evidence.materialCode);
        row.put("ot_number", evidence.otNumber);
        row.put("drawing_number", evidence.drawingNumber);
        row.put("revision", evidence.revision);
        row.put("approval_state", evidence.approval.name());
        row.put("source_uri", evidence.sourceUri);
        row.put("source_sha256", evidence.sourceSha256);
        db.insertWithOnConflict("drawing_revision", null, row, SQLiteDatabase.CONFLICT_REPLACE);
    }

    private static void putEvidence(SQLiteDatabase db, RevisionedPulleyKnowledgeCore.DimensionEvidence evidence) {
        ContentValues row = new ContentValues();
        row.put("material_code", evidence.materialCode);
        row.put("ot_number", evidence.otNumber);
        row.put("drawing_number", evidence.drawingNumber);
        row.put("revision", evidence.revision);
        row.put("dimension_kind", evidence.kind.name());
        row.put("surface_kind", evidence.surface.name());
        row.put("original_value", evidence.originalValue);
        row.put("original_unit", evidence.originalUnit.name());
        row.put("value_mm", evidence.valueMm);
        row.put("drawing_tolerance_mm", evidence.drawingToleranceMm);
        row.put("approval_state", evidence.approval.name());
        row.put("source_uri", evidence.sourceUri);
        row.put("source_sha256", evidence.sourceSha256);
        row.put("extraction_method", evidence.extractionMethod);
        row.put("confidence", evidence.confidence);
        db.insertWithOnConflict("dimension_evidence", null, row, SQLiteDatabase.CONFLICT_REPLACE);
    }

    public static String schemaFingerprint() {
        return String.format(Locale.ROOT, "revisioned-v%d:%s", DATABASE_VERSION, DATABASE_NAME);
    }
}

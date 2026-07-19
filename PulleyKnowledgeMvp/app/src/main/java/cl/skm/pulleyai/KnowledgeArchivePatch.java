package cl.skm.pulleyai;

import android.content.ContentValues;
import android.database.sqlite.SQLiteDatabase;

/** Idempotently promotes only explicitly audited archive facts into the existing knowledge DB. */
public final class KnowledgeArchivePatch {
    private KnowledgeArchivePatch() {
    }

    public static void apply(MainActivity.DbHelper helper) {
        SQLiteDatabase database = helper.getWritableDatabase();
        database.beginTransaction();
        try {
            addFamily(database, "4162018", "Polea motriz 150CV017 · evaluación OT-781", "MOTRIZ", new String[]{"OT-781"});
            addDimension(database, "4162018", "SHELL_LENGTH", 2286, "OT-781 control de espesores · 06-08-2021", 0.99);
            addDimension(database, "4162018", "SHELL_DIAMETER", 760, "OT-781 control de espesores · 06-08-2021", 0.99);
            addDimension(database, "4162018", "SHELL_THICKNESS", 22, "OT-781 espesor original de manto", 0.98);

            addFamily(database, "4162038", "Polea de cola 140CV008 · evaluación OT-867", "COLA", new String[]{"OT-867"});
            addDimension(database, "4162038", "SHELL_LENGTH", 1375, "OT-867 control de espesores", 0.99);
            addDimension(database, "4162038", "SHELL_DIAMETER", 610, "OT-867 control de espesores", 0.99);
            addDimension(database, "4162038", "SHAFT_TOTAL_LENGTH", 1935, "OT-867 informe UT de eje", 0.97);

            updateFamily(database, "4196111", "Código de armado/final asociado a OT-867; verificar variante frente a 4162038", "POLEA");
            updateFamily(database, "4196149", "Código de armado/final OT-781; verificar variante frente a 4162018", "MOTRIZ");
            database.setTransactionSuccessful();
        } finally {
            database.endTransaction();
        }
    }

    private static void addFamily(SQLiteDatabase database, String code, String alias, String role, String[] ots) {
        ContentValues row = new ContentValues();
        row.put("code", code);
        row.put("alias", alias);
        row.put("role", role);
        database.insertWithOnConflict("family", null, row, SQLiteDatabase.CONFLICT_IGNORE);
        updateFamily(database, code, alias, role);
        for (String ot : ots) {
            ContentValues link = new ContentValues();
            link.put("code", code);
            link.put("ot", ot);
            database.insertWithOnConflict("family_ot", null, link, SQLiteDatabase.CONFLICT_IGNORE);
        }
    }

    private static void updateFamily(SQLiteDatabase database, String code, String alias, String role) {
        ContentValues update = new ContentValues();
        update.put("alias", alias);
        update.put("role", role);
        database.update("family", update, "code=?", new String[]{code});
    }

    private static void addDimension(SQLiteDatabase database, String code, String kind, double value,
                                     String source, double confidence) {
        ContentValues row = new ContentValues();
        row.put("code", code);
        row.put("kind", kind);
        row.put("value_mm", value);
        row.put("source", source);
        row.put("confidence", confidence);
        database.insertWithOnConflict("dimension", null, row, SQLiteDatabase.CONFLICT_IGNORE);
    }
}

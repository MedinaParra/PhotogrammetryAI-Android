package cl.ingenieria.photogrammetryai.core.materialhistory.sqlite;

import android.content.Context;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;

/** Owns the on-device SQLite database. No UI or camera dependency is allowed here. */
public final class AndroidPulleyKnowledgeOpenHelper extends SQLiteOpenHelper {
    public AndroidPulleyKnowledgeOpenHelper(Context context) {
        super(
                context.getApplicationContext(),
                SqlitePulleyKnowledgeSchema.DATABASE_NAME,
                null,
                SqlitePulleyKnowledgeSchema.DATABASE_VERSION
        );
    }

    @Override
    public void onConfigure(SQLiteDatabase db) {
        super.onConfigure(db);
        db.setForeignKeyConstraintsEnabled(true);
    }

    @Override
    public void onCreate(SQLiteDatabase db) {
        for (String statement : SqlitePulleyKnowledgeSchema.CREATE_STATEMENTS) {
            db.execSQL(statement);
        }
    }

    @Override
    public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
        if (oldVersion == newVersion) return;
        throw new IllegalStateException(
                "Unsupported pulley knowledge migration " + oldVersion + " -> " + newVersion
                        + ". Add an explicit non-destructive migration before increasing the version."
        );
    }

    @Override
    public void onDowngrade(SQLiteDatabase db, int oldVersion, int newVersion) {
        throw new IllegalStateException(
                "Downgrading pulley knowledge database is not supported: "
                        + oldVersion + " -> " + newVersion
        );
    }
}

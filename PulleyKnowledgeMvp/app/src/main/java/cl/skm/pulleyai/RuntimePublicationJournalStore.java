package cl.skm.pulleyai;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;

/** SQLite-backed journal for coordinating runtime evidence publication with filesystem promotion. */
public final class RuntimePublicationJournalStore extends SQLiteOpenHelper
        implements RuntimePublicationJournalBridge.Journal {
    private static final String DB_NAME = "runtime_publication_journal.db";
    private static final int DB_VERSION = 1;

    public RuntimePublicationJournalStore(Context context) {
        super(context.getApplicationContext(), DB_NAME, null, DB_VERSION);
    }

    @Override public void onCreate(SQLiteDatabase db) {
        db.execSQL("CREATE TABLE publication_journal("
                + "session_id TEXT NOT NULL,"
                + "run_id TEXT NOT NULL,"
                + "requested_state TEXT NOT NULL,"
                + "phase TEXT NOT NULL,"
                + "generation_name TEXT,"
                + "detail TEXT,"
                + "created_at INTEGER NOT NULL,"
                + "updated_at INTEGER NOT NULL,"
                + "PRIMARY KEY(session_id,run_id))");
        db.execSQL("CREATE INDEX publication_journal_open_idx "
                + "ON publication_journal(session_id,phase,updated_at)");
    }

    @Override public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
        if (newVersion > DB_VERSION) {
            throw new IllegalStateException("Missing runtime publication journal migration "
                    + oldVersion + " -> " + newVersion);
        }
    }

    @Override public synchronized void begin(String sessionId, String runId,
                                             String requestedState, long nowEpochMs) {
        ContentValues row = new ContentValues();
        row.put("session_id", clean(sessionId));
        row.put("run_id", clean(runId));
        row.put("requested_state", fallback(requestedState, "COMMITTED"));
        row.put("phase", RuntimePublicationJournalBridge.Phase.PREPARED.name());
        row.putNull("generation_name");
        row.put("detail", "PUBLICATION_PREPARED");
        row.put("created_at", nowEpochMs);
        row.put("updated_at", nowEpochMs);
        long inserted = getWritableDatabase().insertWithOnConflict(
                "publication_journal", null, row, SQLiteDatabase.CONFLICT_REPLACE);
        if (inserted < 0) throw new IllegalStateException("cannot begin publication journal");
    }

    @Override public synchronized void update(String sessionId, String runId,
                                              String requestedState,
                                              RuntimePublicationJournalBridge.Phase phase,
                                              String generationName, String detail,
                                              long nowEpochMs) {
        ContentValues row = new ContentValues();
        row.put("requested_state", fallback(requestedState, "COMMITTED"));
        row.put("phase", (phase == null
                ? RuntimePublicationJournalBridge.Phase.PREPARED : phase).name());
        if (generationName == null || generationName.trim().isEmpty()) {
            row.putNull("generation_name");
        } else {
            row.put("generation_name", generationName.trim());
        }
        row.put("detail", detail == null ? "" : detail.trim());
        row.put("updated_at", nowEpochMs);
        int changed = getWritableDatabase().update("publication_journal", row,
                "session_id=? AND run_id=?", new String[]{clean(sessionId), clean(runId)});
        if (changed != 1) throw new IllegalStateException("publication journal row missing");
    }

    @Override public synchronized RuntimePublicationJournalBridge.Record latestIncomplete(
            String sessionId) {
        Cursor cursor = getReadableDatabase().rawQuery(
                "SELECT session_id,run_id,requested_state,phase,generation_name,detail "
                        + "FROM publication_journal WHERE session_id=? "
                        + "AND phase NOT IN ('COMPLETE','ROLLED_BACK') "
                        + "ORDER BY updated_at DESC LIMIT 1",
                new String[]{clean(sessionId)});
        try {
            if (!cursor.moveToFirst()) return null;
            RuntimePublicationJournalBridge.Phase phase;
            try {
                phase = RuntimePublicationJournalBridge.Phase.valueOf(cursor.getString(3));
            } catch (Exception ignored) {
                phase = RuntimePublicationJournalBridge.Phase.PREPARED;
            }
            return new RuntimePublicationJournalBridge.Record(
                    cursor.getString(0), cursor.getString(1), cursor.getString(2), phase,
                    cursor.isNull(4) ? "" : cursor.getString(4),
                    cursor.isNull(5) ? "" : cursor.getString(5));
        } finally {
            cursor.close();
        }
    }

    @Override public synchronized void closeJournal() {
        close();
    }

    private static String clean(String value) {
        if (value == null || value.trim().isEmpty()) {
            throw new IllegalArgumentException("journal identifier is required");
        }
        return value.trim();
    }

    private static String fallback(String value, String fallback) {
        return value == null || value.trim().isEmpty() ? fallback : value.trim();
    }
}

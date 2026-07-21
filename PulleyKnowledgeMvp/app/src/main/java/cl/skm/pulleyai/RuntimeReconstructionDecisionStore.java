package cl.skm.pulleyai;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;

/** Append-only audit store for runtime reconstruction and fallback decisions. */
public final class RuntimeReconstructionDecisionStore extends SQLiteOpenHelper {
    private static final String DB_NAME = "runtime_reconstruction_decisions.db";
    private static final int DB_VERSION = 1;

    public RuntimeReconstructionDecisionStore(Context context) {
        super(context.getApplicationContext(), DB_NAME, null, DB_VERSION);
    }

    @Override public void onCreate(SQLiteDatabase db) {
        db.execSQL("CREATE TABLE decision(id INTEGER PRIMARY KEY AUTOINCREMENT,"+
                "session_id TEXT NOT NULL,created_at INTEGER NOT NULL,gate_state TEXT NOT NULL,"+
                "decision_state TEXT NOT NULL,reason TEXT NOT NULL,ba_status TEXT NOT NULL,"+
                "optimized INTEGER NOT NULL,fallback INTEGER NOT NULL,initial_rms REAL,"+
                "final_rms REAL,improvement REAL NOT NULL,positive_depth REAL NOT NULL)");
        db.execSQL("CREATE INDEX idx_runtime_decision_session ON decision(session_id,created_at)");
    }

    @Override public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
        if (oldVersion != newVersion) {
            throw new IllegalStateException("Missing runtime decision migration " + oldVersion + " -> " + newVersion);
        }
    }

    public long append(String sessionId, PhotogrammetrySafetyGateCore.Result gate,
                       RuntimeReconstructionDecisionCore.Result decision) {
        if (sessionId == null || sessionId.trim().isEmpty()) {
            throw new IllegalArgumentException("sessionId is required");
        }
        if (gate == null || decision == null) {
            throw new IllegalArgumentException("gate and decision are required");
        }
        ContentValues row = new ContentValues();
        row.put("session_id", sessionId);
        row.put("created_at", System.currentTimeMillis());
        row.put("gate_state", gate.state.name());
        row.put("decision_state", decision.state.name());
        row.put("reason", decision.reason);
        row.put("ba_status", decision.baStatus);
        row.put("optimized", decision.useOptimizedGeometry ? 1 : 0);
        row.put("fallback", decision.useUnoptimizedFallback ? 1 : 0);
        if (Double.isFinite(decision.initialRmsPx)) row.put("initial_rms", decision.initialRmsPx);
        else row.putNull("initial_rms");
        if (Double.isFinite(decision.finalRmsPx)) row.put("final_rms", decision.finalRmsPx);
        else row.putNull("final_rms");
        row.put("improvement", decision.improvementRatio);
        row.put("positive_depth", decision.positiveDepthRatio);
        return getWritableDatabase().insertOrThrow("decision", null, row);
    }

    public Snapshot latest(String sessionId) {
        Cursor cursor = getReadableDatabase().rawQuery(
                "SELECT created_at,gate_state,decision_state,reason,ba_status,optimized,fallback,"+
                        "initial_rms,final_rms,improvement,positive_depth FROM decision "+
                        "WHERE session_id=? ORDER BY id DESC LIMIT 1", new String[]{sessionId});
        try {
            if (!cursor.moveToFirst()) return null;
            return new Snapshot(cursor.getLong(0), cursor.getString(1), cursor.getString(2),
                    cursor.getString(3), cursor.getString(4), cursor.getInt(5) != 0,
                    cursor.getInt(6) != 0, cursor.isNull(7) ? null : cursor.getDouble(7),
                    cursor.isNull(8) ? null : cursor.getDouble(8), cursor.getDouble(9),
                    cursor.getDouble(10));
        } finally { cursor.close(); }
    }

    public static final class Snapshot {
        public final long createdAt;
        public final String gateState, decisionState, reason, baStatus;
        public final boolean optimized, fallback;
        public final Double initialRmsPx, finalRmsPx;
        public final double improvementRatio, positiveDepthRatio;

        Snapshot(long createdAt, String gateState, String decisionState, String reason,
                 String baStatus, boolean optimized, boolean fallback, Double initialRmsPx,
                 Double finalRmsPx, double improvementRatio, double positiveDepthRatio) {
            this.createdAt = createdAt;
            this.gateState = gateState;
            this.decisionState = decisionState;
            this.reason = reason;
            this.baStatus = baStatus;
            this.optimized = optimized;
            this.fallback = fallback;
            this.initialRmsPx = initialRmsPx;
            this.finalRmsPx = finalRmsPx;
            this.improvementRatio = improvementRatio;
            this.positiveDepthRatio = positiveDepthRatio;
        }
    }
}

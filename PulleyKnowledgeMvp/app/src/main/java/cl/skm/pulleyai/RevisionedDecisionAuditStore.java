package cl.skm.pulleyai;

import android.content.ContentValues;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;

import java.util.Locale;
import java.util.UUID;

/** Append-only audit log for multivariable identification decisions. */
public final class RevisionedDecisionAuditStore {
    private final RevisionedKnowledgeOpenHelper helper;

    public RevisionedDecisionAuditStore(RevisionedKnowledgeOpenHelper helper) {
        if (helper == null) throw new IllegalArgumentException("helper is required");
        this.helper = helper;
        ensureSchema(helper.getWritableDatabase());
    }

    public String append(PulleyIdentificationDecisionCore.MeasurementSet measurements,
                         PulleyIdentificationDecisionCore.Decision decision) {
        if (measurements == null || decision == null) throw new IllegalArgumentException("measurements and decision are required");
        SQLiteDatabase db = helper.getWritableDatabase();
        ensureSchema(db);
        String id = UUID.randomUUID().toString();
        ContentValues row = new ContentValues();
        row.put("id", id);
        row.put("decision_fingerprint", decision.fingerprint);
        row.put("created_at", System.currentTimeMillis());
        row.put("material_code", measurements.radius.materialCode);
        row.put("ot_number", measurements.radius.otNumber);
        row.put("drawing_number", measurements.radius.drawingNumber);
        row.put("revision", measurements.radius.revision);
        row.put("radius_kind", measurements.radius.radiusKind.name());
        row.put("surface_kind", measurements.radius.surface.name());
        row.put("observed_radius_mm", measurements.radius.observedRadiusMm);
        if (decision.radiusDecision.reference == null) {
            row.putNull("reference_radius_mm");
            row.putNull("radius_error_mm");
        } else {
            row.put("reference_radius_mm", decision.radiusDecision.reference.valueMm);
            row.put("radius_error_mm", decision.radiusDecision.radiusErrorMm);
        }
        row.put("decision_state", decision.state.name());
        row.put("score", decision.score);
        row.put("reasons", joinReasons(decision));
        row.put("residuals", encodeResiduals(decision));
        db.insertOrThrow("identification_decision_audit", null, row);
        return id;
    }

    public int count() {
        Cursor cursor = helper.getReadableDatabase().rawQuery(
                "SELECT COUNT(*) FROM identification_decision_audit", null);
        try { return cursor.moveToFirst() ? cursor.getInt(0) : 0; }
        finally { cursor.close(); }
    }

    private static void ensureSchema(SQLiteDatabase db) {
        db.execSQL("CREATE TABLE IF NOT EXISTS identification_decision_audit(" +
                "id TEXT PRIMARY KEY," +
                "decision_fingerprint TEXT NOT NULL," +
                "created_at INTEGER NOT NULL," +
                "material_code TEXT NOT NULL," +
                "ot_number TEXT NOT NULL," +
                "drawing_number TEXT NOT NULL," +
                "revision TEXT NOT NULL," +
                "radius_kind TEXT NOT NULL," +
                "surface_kind TEXT NOT NULL," +
                "observed_radius_mm REAL NOT NULL," +
                "reference_radius_mm REAL," +
                "radius_error_mm REAL," +
                "decision_state TEXT NOT NULL," +
                "score REAL NOT NULL," +
                "reasons TEXT NOT NULL," +
                "residuals TEXT NOT NULL)");
        db.execSQL("CREATE INDEX IF NOT EXISTS idx_decision_audit_lookup ON identification_decision_audit(material_code,ot_number,created_at)");
        db.execSQL("CREATE INDEX IF NOT EXISTS idx_decision_audit_fingerprint ON identification_decision_audit(decision_fingerprint)");
    }

    private static String joinReasons(PulleyIdentificationDecisionCore.Decision decision) {
        StringBuilder out = new StringBuilder();
        for (String reason : decision.reasons) {
            if (out.length() > 0) out.append('\n');
            out.append(clean(reason));
        }
        return out.toString();
    }

    private static String encodeResiduals(PulleyIdentificationDecisionCore.Decision decision) {
        StringBuilder out = new StringBuilder();
        for (PulleyIdentificationDecisionCore.DimensionResidual residual : decision.residuals) {
            if (out.length() > 0) out.append('\n');
            out.append(residual.kind).append('|')
                    .append(String.format(Locale.ROOT, "%.6f", residual.observedMm)).append('|')
                    .append(residual.referenceMm == null ? "" : String.format(Locale.ROOT, "%.6f", residual.referenceMm)).append('|')
                    .append(residual.absoluteErrorMm == null ? "" : String.format(Locale.ROOT, "%.6f", residual.absoluteErrorMm)).append('|')
                    .append(residual.allowedToleranceMm == null ? "" : String.format(Locale.ROOT, "%.6f", residual.allowedToleranceMm)).append('|')
                    .append(residual.state).append('|')
                    .append(clean(residual.sourceUri)).append('|')
                    .append(clean(residual.drawingNumber)).append('|')
                    .append(clean(residual.revision));
        }
        return out.toString();
    }

    private static String clean(String value) {
        if (value == null) return "";
        return value.replace('|', '/').replace('\n', ' ').replace('\r', ' ');
    }
}

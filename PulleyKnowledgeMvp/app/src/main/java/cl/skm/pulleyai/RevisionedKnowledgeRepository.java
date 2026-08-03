package cl.skm.pulleyai;

import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;

import java.util.LinkedHashSet;
import java.util.Set;

/** Reads the normalized SQLite evidence store back into the pure decision domain. */
public final class RevisionedKnowledgeRepository {
    private final RevisionedKnowledgeOpenHelper helper;

    public RevisionedKnowledgeRepository(RevisionedKnowledgeOpenHelper helper) {
        if (helper == null) throw new IllegalArgumentException("helper is required");
        this.helper = helper;
    }

    public RevisionedPulleyKnowledgeCore.Catalog loadCatalog() {
        helper.ensureSeeded();
        SQLiteDatabase db = helper.getReadableDatabase();
        RevisionedPulleyKnowledgeCore.Catalog catalog = new RevisionedPulleyKnowledgeCore.Catalog();
        Cursor evidence = db.rawQuery(
                "SELECT material_code,ot_number,drawing_number,revision,dimension_kind,surface_kind," +
                        "original_value,original_unit,drawing_tolerance_mm,approval_state,source_uri," +
                        "source_sha256,extraction_method,confidence FROM dimension_evidence " +
                        "ORDER BY material_code,ot_number,drawing_number,revision,dimension_kind,surface_kind",
                null);
        try {
            while (evidence.moveToNext()) {
                catalog.upsert(RevisionedPulleyKnowledgeCore.DimensionEvidence.builder()
                        .material(evidence.getString(0))
                        .ot(evidence.getString(1))
                        .drawing(evidence.getString(2))
                        .revision(evidence.getString(3))
                        .kind(RevisionedPulleyKnowledgeCore.DimensionKind.valueOf(evidence.getString(4)))
                        .surface(RevisionedPulleyKnowledgeCore.SurfaceKind.valueOf(evidence.getString(5)))
                        .value(evidence.getDouble(6), RevisionedPulleyKnowledgeCore.Unit.valueOf(evidence.getString(7)))
                        .toleranceMm(evidence.getDouble(8))
                        .approval(RevisionedPulleyKnowledgeCore.ApprovalState.valueOf(evidence.getString(9)))
                        .source(evidence.getString(10), evidence.getString(11))
                        .extraction(evidence.getString(12))
                        .confidence(evidence.getDouble(13))
                        .build());
            }
        } finally {
            evidence.close();
        }

        Cursor conflicts = db.rawQuery(
                "SELECT conflict_id,material_code,severity,description,affected_ots " +
                        "FROM validation_conflict WHERE resolution_state!='RESOLVED' ORDER BY conflict_id",
                null);
        try {
            while (conflicts.moveToNext()) {
                Set<String> ots = new LinkedHashSet<String>();
                String raw = conflicts.getString(4);
                if (raw != null && !raw.trim().isEmpty()) {
                    for (String item : raw.split(",")) if (!item.trim().isEmpty()) ots.add(item.trim());
                }
                catalog.addConflict(new RevisionedPulleyKnowledgeCore.ValidationConflict(
                        conflicts.getString(0), conflicts.getString(1),
                        RevisionedPulleyKnowledgeCore.ConflictSeverity.valueOf(conflicts.getString(2)),
                        conflicts.getString(3), ots));
            }
        } finally {
            conflicts.close();
        }
        return catalog;
    }
}

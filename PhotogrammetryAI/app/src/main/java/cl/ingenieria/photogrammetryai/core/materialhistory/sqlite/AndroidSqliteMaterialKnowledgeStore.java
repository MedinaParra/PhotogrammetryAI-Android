package cl.ingenieria.photogrammetryai.core.materialhistory.sqlite;

import android.content.ContentValues;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;

import cl.ingenieria.photogrammetryai.core.materialhistory.IdentificationAudit;
import cl.ingenieria.photogrammetryai.core.materialhistory.MaterialKnowledgeStore;
import cl.ingenieria.photogrammetryai.core.materialhistory.MaterialPulleyKnowledgeBase;
import cl.ingenieria.photogrammetryai.core.materialhistory.MaterialPulleyKnowledgeBase.ComponentEvidence;
import cl.ingenieria.photogrammetryai.core.materialhistory.MaterialPulleyKnowledgeBase.ComponentKind;
import cl.ingenieria.photogrammetryai.core.materialhistory.MaterialPulleyKnowledgeBase.Condition;
import cl.ingenieria.photogrammetryai.core.materialhistory.MaterialPulleyKnowledgeBase.DimensionEvidence;
import cl.ingenieria.photogrammetryai.core.materialhistory.MaterialPulleyKnowledgeBase.DimensionKind;
import cl.ingenieria.photogrammetryai.core.materialhistory.MaterialPulleyKnowledgeBase.InterventionEvent;
import cl.ingenieria.photogrammetryai.core.materialhistory.MaterialPulleyKnowledgeBase.MaterialFamily;
import cl.ingenieria.photogrammetryai.core.materialhistory.MaterialPulleyKnowledgeBase.ReportPhase;
import cl.ingenieria.photogrammetryai.core.materialhistory.MaterialPulleyKnowledgeBase.SourceDocument;
import cl.ingenieria.photogrammetryai.core.materialhistory.PulleyMaterialIdentificationEngine.Action;
import cl.ingenieria.photogrammetryai.core.materialhistory.PulleyMaterialIdentificationEngine.LengthStatus;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

/** Production local store backed by Android's built-in SQLite engine. */
public final class AndroidSqliteMaterialKnowledgeStore implements MaterialKnowledgeStore {
    private final AndroidPulleyKnowledgeOpenHelper helper;

    public AndroidSqliteMaterialKnowledgeStore(AndroidPulleyKnowledgeOpenHelper helper) {
        this.helper = Objects.requireNonNull(helper, "helper");
    }

    @Override
    public synchronized MaterialPulleyKnowledgeBase snapshot() {
        MaterialPulleyKnowledgeBase knowledge = new MaterialPulleyKnowledgeBase();
        for (MaterialFamily family : families()) knowledge.register(family);
        return knowledge;
    }

    @Override
    public synchronized void upsertFamily(MaterialFamily family) {
        Objects.requireNonNull(family, "family");
        SQLiteDatabase db = helper.getWritableDatabase();
        db.beginTransaction();
        try {
            upsertFamily(db, family);
            db.setTransactionSuccessful();
        } finally {
            db.endTransaction();
        }
    }

    @Override
    public synchronized void replaceAll(MaterialPulleyKnowledgeBase knowledgeBase) {
        Objects.requireNonNull(knowledgeBase, "knowledgeBase");
        SQLiteDatabase db = helper.getWritableDatabase();
        db.beginTransaction();
        try {
            clearKnowledgeTables(db);
            for (MaterialFamily family : knowledgeBase.families()) upsertFamily(db, family);
            db.setTransactionSuccessful();
        } finally {
            db.endTransaction();
        }
    }

    @Override
    public synchronized Optional<MaterialFamily> findByMaterialCode(String materialCode) {
        if (materialCode == null || materialCode.trim().isEmpty()) return Optional.empty();
        String code = MaterialPulleyKnowledgeBase.normalizeMaterialCode(materialCode);
        SQLiteDatabase db = helper.getReadableDatabase();
        if (!exists(db, "SELECT 1 FROM material_family WHERE material_code=?", code)) {
            return Optional.empty();
        }
        return Optional.of(loadFamily(db, code));
    }

    @Override
    public synchronized List<MaterialFamily> findByOt(String ot) {
        if (ot == null || ot.trim().isEmpty()) return Collections.emptyList();
        String normalized = MaterialPulleyKnowledgeBase.normalizeOt(ot);
        SQLiteDatabase db = helper.getReadableDatabase();
        List<MaterialFamily> result = new ArrayList<>();
        String sql = "SELECT material_code FROM intervention WHERE ot=? "
                + "UNION SELECT material_code FROM source_document "
                + "WHERE ot=? AND material_code IS NOT NULL ORDER BY material_code";
        try (Cursor cursor = db.rawQuery(sql, new String[]{normalized, normalized})) {
            while (cursor.moveToNext()) result.add(loadFamily(db, cursor.getString(0)));
        }
        return Collections.unmodifiableList(result);
    }

    @Override
    public synchronized Set<String> materialCodesForOt(String ot) {
        Set<String> result = new LinkedHashSet<>();
        for (MaterialFamily family : findByOt(ot)) result.add(family.materialCode());
        return Collections.unmodifiableSet(result);
    }

    @Override
    public synchronized List<MaterialFamily> families() {
        SQLiteDatabase db = helper.getReadableDatabase();
        List<MaterialFamily> result = new ArrayList<>();
        try (Cursor cursor = db.rawQuery(
                "SELECT material_code FROM material_family ORDER BY material_code",
                null
        )) {
            while (cursor.moveToNext()) result.add(loadFamily(db, cursor.getString(0)));
        }
        return Collections.unmodifiableList(result);
    }

    @Override
    public synchronized int size() {
        SQLiteDatabase db = helper.getReadableDatabase();
        try (Cursor cursor = db.rawQuery("SELECT COUNT(*) FROM material_family", null)) {
            return cursor.moveToFirst() ? cursor.getInt(0) : 0;
        }
    }

    @Override
    public synchronized void saveIdentificationAudit(IdentificationAudit audit) {
        Objects.requireNonNull(audit, "audit");
        SQLiteDatabase db = helper.getWritableDatabase();
        db.beginTransaction();
        try {
            ContentValues session = new ContentValues();
            session.put("session_id", audit.sessionId());
            session.put("created_at_epoch_ms", audit.createdAtEpochMs());
            session.put("shell_length_mm", audit.shellLengthMm());
            putNullable(session, "entered_material_code", audit.enteredMaterialCode().orElse(null));
            putNullable(session, "entered_ot", audit.enteredOt().orElse(null));
            putNullableDouble(
                    session,
                    "measured_shell_diameter_mm",
                    audit.measuredShellDiameterMm().orElse(null)
            );
            session.put("description", audit.description());
            putNullable(session, "selected_material_code", audit.selectedMaterialCode().orElse(null));
            session.put("decision", audit.decision().name());
            session.put("operator_confirmed", audit.operatorConfirmed() ? 1 : 0);
            db.insertWithOnConflict(
                    "identification_session",
                    null,
                    session,
                    SQLiteDatabase.CONFLICT_REPLACE
            );
            db.delete(
                    "identification_candidate",
                    "session_id=?",
                    new String[]{audit.sessionId()}
            );
            for (IdentificationAudit.RankedCandidate candidate : audit.candidates()) {
                ContentValues row = new ContentValues();
                row.put("session_id", audit.sessionId());
                row.put("rank", candidate.rank());
                row.put("material_code", candidate.materialCode());
                row.put("score", candidate.score());
                row.put("length_status", candidate.lengthStatus().name());
                row.put("action", candidate.action().name());
                row.put("reasons_text", TextListCodec.encode(candidate.reasons()));
                row.put("warnings_text", TextListCodec.encode(candidate.warnings()));
                db.insertOrThrow("identification_candidate", null, row);
            }
            db.setTransactionSuccessful();
        } finally {
            db.endTransaction();
        }
    }

    @Override
    public synchronized List<IdentificationAudit> recentIdentificationAudits(int limit) {
        if (limit <= 0) throw new IllegalArgumentException("limit must be positive");
        SQLiteDatabase db = helper.getReadableDatabase();
        List<IdentificationAudit> result = new ArrayList<>();
        String sql = "SELECT session_id,created_at_epoch_ms,shell_length_mm,"
                + "entered_material_code,entered_ot,measured_shell_diameter_mm,description,"
                + "selected_material_code,decision,operator_confirmed "
                + "FROM identification_session ORDER BY created_at_epoch_ms DESC LIMIT ?";
        try (Cursor cursor = db.rawQuery(sql, new String[]{Integer.toString(limit)})) {
            while (cursor.moveToNext()) {
                String sessionId = cursor.getString(0);
                result.add(new IdentificationAudit(
                        sessionId,
                        cursor.getLong(1),
                        cursor.getDouble(2),
                        nullableString(cursor, 3),
                        nullableString(cursor, 4),
                        nullableDouble(cursor, 5),
                        cursor.getString(6),
                        nullableString(cursor, 7),
                        Action.valueOf(cursor.getString(8)),
                        cursor.getInt(9) != 0,
                        loadCandidates(db, sessionId)
                ));
            }
        }
        return Collections.unmodifiableList(result);
    }

    private static void upsertFamily(SQLiteDatabase db, MaterialFamily family) {
        long now = System.currentTimeMillis();
        ContentValues familyRow = new ContentValues();
        familyRow.put("material_code", family.materialCode());
        familyRow.put("created_at_epoch_ms", now);
        familyRow.put("updated_at_epoch_ms", now);
        db.insertWithOnConflict(
                "material_family",
                null,
                familyRow,
                SQLiteDatabase.CONFLICT_IGNORE
        );
        ContentValues updated = new ContentValues();
        updated.put("updated_at_epoch_ms", now);
        db.update(
                "material_family",
                updated,
                "material_code=?",
                new String[]{family.materialCode()}
        );

        insertStrings(db, "family_alias", "alias", family.materialCode(), family.aliases());
        insertStrings(db, "family_client", "client", family.materialCode(), family.clients());
        insertStrings(
                db,
                "family_role_hint",
                "role_hint",
                family.materialCode(),
                family.roleHints()
        );

        for (SourceDocument source : family.sources().values()) {
            ContentValues row = new ContentValues();
            row.put("source_id", source.id());
            row.put("title", source.title());
            row.put("uri", source.uri());
            row.put("phase", source.phase().name());
            putNullable(row, "ot", source.ot().orElse(null));
            row.put("material_code", family.materialCode());
            row.put("document_date", source.documentDate());
            row.put("content_hash", "");
            row.put("revision_label", "");
            row.put("ingested_at_epoch_ms", now);
            db.insertWithOnConflict(
                    "source_document",
                    null,
                    row,
                    SQLiteDatabase.CONFLICT_IGNORE
            );
        }

        for (DimensionEvidence evidence : family.dimensions()) {
            ContentValues row = new ContentValues();
            row.put("material_code", family.materialCode());
            row.put("kind", evidence.kind().name());
            row.put("value_mm", evidence.valueMm());
            row.put("semantic_label", evidence.semanticLabel());
            row.put("source_id", evidence.sourceId());
            row.put("confidence", evidence.confidence());
            db.insertWithOnConflict(
                    "dimension_evidence",
                    null,
                    row,
                    SQLiteDatabase.CONFLICT_IGNORE
            );
        }

        for (ComponentEvidence evidence : family.components()) {
            ContentValues row = new ContentValues();
            row.put("material_code", family.materialCode());
            row.put("kind", evidence.kind().name());
            row.put("manufacturer", evidence.manufacturer());
            row.put("model", evidence.model());
            row.put("condition", evidence.condition().name());
            row.put("note", evidence.note());
            row.put("source_id", evidence.sourceId());
            row.put("confidence", evidence.confidence());
            db.insertWithOnConflict(
                    "component_evidence",
                    null,
                    row,
                    SQLiteDatabase.CONFLICT_IGNORE
            );
        }

        for (InterventionEvent event : family.events()) {
            long interventionId = findOrInsertIntervention(db, family.materialCode(), event);
            for (String sourceId : event.sourceIds()) {
                ContentValues row = new ContentValues();
                row.put("intervention_id", interventionId);
                row.put("source_id", sourceId);
                db.insertWithOnConflict(
                        "intervention_source",
                        null,
                        row,
                        SQLiteDatabase.CONFLICT_IGNORE
                );
            }
            for (String note : event.notes()) {
                ContentValues row = new ContentValues();
                row.put("intervention_id", interventionId);
                row.put("note", note);
                db.insertWithOnConflict(
                        "intervention_note",
                        null,
                        row,
                        SQLiteDatabase.CONFLICT_IGNORE
                );
            }
        }
    }

    private static MaterialFamily loadFamily(SQLiteDatabase db, String materialCode) {
        MaterialFamily.Builder builder = MaterialFamily.builder(materialCode);
        for (String value : loadStrings(
                db,
                "SELECT alias FROM family_alias WHERE material_code=? ORDER BY alias",
                materialCode
        )) builder.alias(value);
        for (String value : loadStrings(
                db,
                "SELECT client FROM family_client WHERE material_code=? ORDER BY client",
                materialCode
        )) builder.client(value);
        for (String value : loadStrings(
                db,
                "SELECT role_hint FROM family_role_hint WHERE material_code=? ORDER BY role_hint",
                materialCode
        )) builder.roleHint(value);

        try (Cursor cursor = db.rawQuery(
                "SELECT source_id,title,uri,phase,ot,material_code,document_date "
                        + "FROM source_document WHERE material_code=? ORDER BY source_id",
                new String[]{materialCode}
        )) {
            while (cursor.moveToNext()) {
                builder.source(new SourceDocument(
                        cursor.getString(0),
                        cursor.getString(1),
                        cursor.getString(2),
                        ReportPhase.valueOf(cursor.getString(3)),
                        nullableString(cursor, 4),
                        nullableString(cursor, 5),
                        cursor.getString(6)
                ));
            }
        }

        try (Cursor cursor = db.rawQuery(
                "SELECT kind,value_mm,semantic_label,source_id,confidence "
                        + "FROM dimension_evidence WHERE material_code=? ORDER BY dimension_id",
                new String[]{materialCode}
        )) {
            while (cursor.moveToNext()) {
                builder.dimension(new DimensionEvidence(
                        DimensionKind.valueOf(cursor.getString(0)),
                        cursor.getDouble(1),
                        cursor.getString(2),
                        cursor.getString(3),
                        cursor.getDouble(4)
                ));
            }
        }

        try (Cursor cursor = db.rawQuery(
                "SELECT kind,manufacturer,model,condition,note,source_id,confidence "
                        + "FROM component_evidence WHERE material_code=? ORDER BY component_id",
                new String[]{materialCode}
        )) {
            while (cursor.moveToNext()) {
                builder.component(new ComponentEvidence(
                        ComponentKind.valueOf(cursor.getString(0)),
                        cursor.getString(1),
                        cursor.getString(2),
                        Condition.valueOf(cursor.getString(3)),
                        cursor.getString(4),
                        cursor.getString(5),
                        cursor.getDouble(6)
                ));
            }
        }

        try (Cursor cursor = db.rawQuery(
                "SELECT intervention_id,ot,year,phase,client,component_description,purchase_order "
                        + "FROM intervention WHERE material_code=? ORDER BY year,ot,phase",
                new String[]{materialCode}
        )) {
            while (cursor.moveToNext()) {
                long id = cursor.getLong(0);
                builder.event(new InterventionEvent(
                        cursor.getString(1),
                        cursor.getInt(2),
                        ReportPhase.valueOf(cursor.getString(3)),
                        cursor.getString(4),
                        cursor.getString(5),
                        cursor.getString(6),
                        new LinkedHashSet<>(loadStrings(
                                db,
                                "SELECT source_id FROM intervention_source "
                                        + "WHERE intervention_id=? ORDER BY source_id",
                                Long.toString(id)
                        )),
                        loadStrings(
                                db,
                                "SELECT note FROM intervention_note "
                                        + "WHERE intervention_id=? ORDER BY note",
                                Long.toString(id)
                        )
                ));
            }
        }
        return builder.build();
    }

    private static List<IdentificationAudit.RankedCandidate> loadCandidates(
            SQLiteDatabase db,
            String sessionId
    ) {
        List<IdentificationAudit.RankedCandidate> result = new ArrayList<>();
        try (Cursor cursor = db.rawQuery(
                "SELECT rank,material_code,score,length_status,action,reasons_text,warnings_text "
                        + "FROM identification_candidate WHERE session_id=? ORDER BY rank",
                new String[]{sessionId}
        )) {
            while (cursor.moveToNext()) {
                result.add(new IdentificationAudit.RankedCandidate(
                        cursor.getInt(0),
                        cursor.getString(1),
                        cursor.getDouble(2),
                        LengthStatus.valueOf(cursor.getString(3)),
                        Action.valueOf(cursor.getString(4)),
                        TextListCodec.decode(cursor.getString(5)),
                        TextListCodec.decode(cursor.getString(6))
                ));
            }
        }
        return result;
    }

    private static long findOrInsertIntervention(
            SQLiteDatabase db,
            String materialCode,
            InterventionEvent event
    ) {
        ContentValues row = new ContentValues();
        row.put("material_code", materialCode);
        row.put("ot", event.ot());
        row.put("year", event.year());
        row.put("phase", event.phase().name());
        row.put("client", event.client());
        row.put("component_description", event.componentDescription());
        row.put("purchase_order", event.purchaseOrder());
        long id = db.insertWithOnConflict(
                "intervention",
                null,
                row,
                SQLiteDatabase.CONFLICT_IGNORE
        );
        if (id >= 0) return id;
        String sql = "SELECT intervention_id FROM intervention WHERE material_code=? AND ot=? "
                + "AND year=? AND phase=? AND client=? AND component_description=? "
                + "AND purchase_order=?";
        String[] args = {
                materialCode,
                event.ot(),
                Integer.toString(event.year()),
                event.phase().name(),
                event.client(),
                event.componentDescription(),
                event.purchaseOrder()
        };
        try (Cursor cursor = db.rawQuery(sql, args)) {
            if (!cursor.moveToFirst()) {
                throw new IllegalStateException("Unable to resolve intervention after upsert");
            }
            return cursor.getLong(0);
        }
    }

    private static void insertStrings(
            SQLiteDatabase db,
            String table,
            String valueColumn,
            String materialCode,
            Set<String> values
    ) {
        for (String value : values) {
            ContentValues row = new ContentValues();
            row.put("material_code", materialCode);
            row.put(valueColumn, value);
            db.insertWithOnConflict(table, null, row, SQLiteDatabase.CONFLICT_IGNORE);
        }
    }

    private static List<String> loadStrings(SQLiteDatabase db, String sql, String argument) {
        List<String> result = new ArrayList<>();
        try (Cursor cursor = db.rawQuery(sql, new String[]{argument})) {
            while (cursor.moveToNext()) result.add(cursor.getString(0));
        }
        return result;
    }

    private static boolean exists(SQLiteDatabase db, String sql, String argument) {
        try (Cursor cursor = db.rawQuery(sql, new String[]{argument})) {
            return cursor.moveToFirst();
        }
    }

    private static void clearKnowledgeTables(SQLiteDatabase db) {
        String[] tables = {
                "component_evidence",
                "dimension_evidence",
                "intervention_note",
                "intervention_source",
                "intervention",
                "source_document",
                "family_role_hint",
                "family_client",
                "family_alias",
                "material_family"
        };
        for (String table : tables) db.delete(table, null, null);
    }

    private static void putNullable(ContentValues values, String key, String value) {
        if (value == null) values.putNull(key); else values.put(key, value);
    }

    private static void putNullableDouble(ContentValues values, String key, Double value) {
        if (value == null) values.putNull(key); else values.put(key, value);
    }

    private static String nullableString(Cursor cursor, int index) {
        return cursor.isNull(index) ? null : cursor.getString(index);
    }

    private static Double nullableDouble(Cursor cursor, int index) {
        return cursor.isNull(index) ? null : cursor.getDouble(index);
    }
}

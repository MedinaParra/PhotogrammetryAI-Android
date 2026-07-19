package cl.ingenieria.photogrammetryai.core.materialhistory.sqlite;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/** SQLite schema shared by the Android adapter and schema validation tools. */
public final class SqlitePulleyKnowledgeSchema {
    public static final String DATABASE_NAME = "pulley_knowledge.db";
    public static final int DATABASE_VERSION = 2;

    private static final String CREATE_DIMENSION_REVIEW =
            "CREATE TABLE IF NOT EXISTS dimension_review ("
                    + "session_id TEXT PRIMARY KEY NOT NULL,"
                    + "material_code TEXT NOT NULL,"
                    + "identification_action TEXT NOT NULL,"
                    + "created_at_epoch_ms INTEGER NOT NULL,"
                    + "FOREIGN KEY(session_id) REFERENCES identification_session(session_id) ON DELETE CASCADE)";

    private static final String CREATE_DIMENSION_REVIEW_ITEM =
            "CREATE TABLE IF NOT EXISTS dimension_review_item ("
                    + "session_id TEXT NOT NULL,"
                    + "kind TEXT NOT NULL,"
                    + "label TEXT NOT NULL,"
                    + "recommended_value_mm REAL NOT NULL CHECK(recommended_value_mm > 0),"
                    + "historical_value_mm REAL,"
                    + "scan_value_mm REAL,"
                    + "tolerance_mm REAL NOT NULL CHECK(tolerance_mm > 0),"
                    + "confidence REAL NOT NULL CHECK(confidence >= 0 AND confidence <= 1),"
                    + "priority TEXT NOT NULL,"
                    + "origin TEXT NOT NULL,"
                    + "source_summary TEXT NOT NULL DEFAULT '',"
                    + "response_status TEXT NOT NULL,"
                    + "operator_value_mm REAL,"
                    + "deviation_mm REAL,"
                    + "note TEXT NOT NULL DEFAULT '',"
                    + "answered_at_epoch_ms INTEGER,"
                    + "PRIMARY KEY(session_id, kind),"
                    + "FOREIGN KEY(session_id) REFERENCES dimension_review(session_id) ON DELETE CASCADE)";

    public static final List<String> CREATE_STATEMENTS = Collections.unmodifiableList(Arrays.asList(
            "CREATE TABLE IF NOT EXISTS schema_meta ("
                    + "key TEXT PRIMARY KEY NOT NULL,"
                    + "value TEXT NOT NULL)",
            "CREATE TABLE IF NOT EXISTS material_family ("
                    + "material_code TEXT PRIMARY KEY NOT NULL,"
                    + "created_at_epoch_ms INTEGER NOT NULL,"
                    + "updated_at_epoch_ms INTEGER NOT NULL)",
            "CREATE TABLE IF NOT EXISTS family_alias ("
                    + "material_code TEXT NOT NULL,"
                    + "alias TEXT NOT NULL COLLATE NOCASE,"
                    + "PRIMARY KEY(material_code, alias),"
                    + "FOREIGN KEY(material_code) REFERENCES material_family(material_code) ON DELETE CASCADE)",
            "CREATE TABLE IF NOT EXISTS family_client ("
                    + "material_code TEXT NOT NULL,"
                    + "client TEXT NOT NULL COLLATE NOCASE,"
                    + "PRIMARY KEY(material_code, client),"
                    + "FOREIGN KEY(material_code) REFERENCES material_family(material_code) ON DELETE CASCADE)",
            "CREATE TABLE IF NOT EXISTS family_role_hint ("
                    + "material_code TEXT NOT NULL,"
                    + "role_hint TEXT NOT NULL COLLATE NOCASE,"
                    + "PRIMARY KEY(material_code, role_hint),"
                    + "FOREIGN KEY(material_code) REFERENCES material_family(material_code) ON DELETE CASCADE)",
            "CREATE TABLE IF NOT EXISTS source_document ("
                    + "source_id TEXT PRIMARY KEY NOT NULL,"
                    + "title TEXT NOT NULL,"
                    + "uri TEXT NOT NULL,"
                    + "phase TEXT NOT NULL,"
                    + "ot TEXT,"
                    + "material_code TEXT,"
                    + "document_date TEXT NOT NULL DEFAULT '',"
                    + "content_hash TEXT NOT NULL DEFAULT '',"
                    + "revision_label TEXT NOT NULL DEFAULT '',"
                    + "ingested_at_epoch_ms INTEGER NOT NULL,"
                    + "FOREIGN KEY(material_code) REFERENCES material_family(material_code) ON DELETE CASCADE)",
            "CREATE INDEX IF NOT EXISTS idx_source_material_code ON source_document(material_code)",
            "CREATE INDEX IF NOT EXISTS idx_source_ot ON source_document(ot)",
            "CREATE INDEX IF NOT EXISTS idx_source_hash ON source_document(content_hash)",
            "CREATE TABLE IF NOT EXISTS intervention ("
                    + "intervention_id INTEGER PRIMARY KEY AUTOINCREMENT,"
                    + "material_code TEXT NOT NULL,"
                    + "ot TEXT NOT NULL,"
                    + "year INTEGER NOT NULL CHECK(year BETWEEN 1900 AND 2200),"
                    + "phase TEXT NOT NULL,"
                    + "client TEXT NOT NULL DEFAULT '',"
                    + "component_description TEXT NOT NULL DEFAULT '',"
                    + "purchase_order TEXT NOT NULL DEFAULT '',"
                    + "UNIQUE(material_code, ot, year, phase, client, component_description, purchase_order),"
                    + "FOREIGN KEY(material_code) REFERENCES material_family(material_code) ON DELETE CASCADE)",
            "CREATE INDEX IF NOT EXISTS idx_intervention_ot ON intervention(ot)",
            "CREATE INDEX IF NOT EXISTS idx_intervention_material_code ON intervention(material_code)",
            "CREATE TABLE IF NOT EXISTS intervention_source ("
                    + "intervention_id INTEGER NOT NULL,"
                    + "source_id TEXT NOT NULL,"
                    + "PRIMARY KEY(intervention_id, source_id),"
                    + "FOREIGN KEY(intervention_id) REFERENCES intervention(intervention_id) ON DELETE CASCADE,"
                    + "FOREIGN KEY(source_id) REFERENCES source_document(source_id) ON DELETE CASCADE)",
            "CREATE TABLE IF NOT EXISTS intervention_note ("
                    + "intervention_id INTEGER NOT NULL,"
                    + "note TEXT NOT NULL,"
                    + "PRIMARY KEY(intervention_id, note),"
                    + "FOREIGN KEY(intervention_id) REFERENCES intervention(intervention_id) ON DELETE CASCADE)",
            "CREATE TABLE IF NOT EXISTS dimension_evidence ("
                    + "dimension_id INTEGER PRIMARY KEY AUTOINCREMENT,"
                    + "material_code TEXT NOT NULL,"
                    + "kind TEXT NOT NULL,"
                    + "value_mm REAL NOT NULL CHECK(value_mm > 0),"
                    + "semantic_label TEXT NOT NULL,"
                    + "source_id TEXT NOT NULL,"
                    + "confidence REAL NOT NULL CHECK(confidence >= 0 AND confidence <= 1),"
                    + "UNIQUE(material_code, kind, value_mm, semantic_label, source_id),"
                    + "FOREIGN KEY(material_code) REFERENCES material_family(material_code) ON DELETE CASCADE,"
                    + "FOREIGN KEY(source_id) REFERENCES source_document(source_id) ON DELETE CASCADE)",
            "CREATE INDEX IF NOT EXISTS idx_dimension_lookup "
                    + "ON dimension_evidence(material_code, kind, value_mm)",
            "CREATE TABLE IF NOT EXISTS component_evidence ("
                    + "component_id INTEGER PRIMARY KEY AUTOINCREMENT,"
                    + "material_code TEXT NOT NULL,"
                    + "kind TEXT NOT NULL,"
                    + "manufacturer TEXT NOT NULL DEFAULT '',"
                    + "model TEXT NOT NULL DEFAULT '',"
                    + "condition TEXT NOT NULL,"
                    + "note TEXT NOT NULL DEFAULT '',"
                    + "source_id TEXT NOT NULL,"
                    + "confidence REAL NOT NULL CHECK(confidence >= 0 AND confidence <= 1),"
                    + "UNIQUE(material_code, kind, manufacturer, model, condition, note, source_id),"
                    + "FOREIGN KEY(material_code) REFERENCES material_family(material_code) ON DELETE CASCADE,"
                    + "FOREIGN KEY(source_id) REFERENCES source_document(source_id) ON DELETE CASCADE)",
            "CREATE INDEX IF NOT EXISTS idx_component_model "
                    + "ON component_evidence(model COLLATE NOCASE)",
            "CREATE INDEX IF NOT EXISTS idx_component_material "
                    + "ON component_evidence(material_code, kind)",
            "CREATE TABLE IF NOT EXISTS ingestion_run ("
                    + "ingestion_id TEXT PRIMARY KEY NOT NULL,"
                    + "source_id TEXT,"
                    + "started_at_epoch_ms INTEGER NOT NULL,"
                    + "completed_at_epoch_ms INTEGER,"
                    + "status TEXT NOT NULL,"
                    + "material_code TEXT,"
                    + "ot TEXT,"
                    + "warning_text TEXT NOT NULL DEFAULT '',"
                    + "error_text TEXT NOT NULL DEFAULT '')",
            "CREATE INDEX IF NOT EXISTS idx_ingestion_status "
                    + "ON ingestion_run(status, started_at_epoch_ms)",
            "CREATE TABLE IF NOT EXISTS identification_session ("
                    + "session_id TEXT PRIMARY KEY NOT NULL,"
                    + "created_at_epoch_ms INTEGER NOT NULL,"
                    + "shell_length_mm REAL NOT NULL CHECK(shell_length_mm > 0),"
                    + "entered_material_code TEXT,"
                    + "entered_ot TEXT,"
                    + "measured_shell_diameter_mm REAL,"
                    + "description TEXT NOT NULL DEFAULT '',"
                    + "selected_material_code TEXT,"
                    + "decision TEXT NOT NULL,"
                    + "operator_confirmed INTEGER NOT NULL DEFAULT 0 "
                    + "CHECK(operator_confirmed IN(0,1)))",
            "CREATE INDEX IF NOT EXISTS idx_identification_created "
                    + "ON identification_session(created_at_epoch_ms DESC)",
            "CREATE INDEX IF NOT EXISTS idx_identification_selected "
                    + "ON identification_session(selected_material_code)",
            "CREATE TABLE IF NOT EXISTS identification_candidate ("
                    + "session_id TEXT NOT NULL,"
                    + "rank INTEGER NOT NULL CHECK(rank > 0),"
                    + "material_code TEXT NOT NULL,"
                    + "score REAL NOT NULL CHECK(score >= 0 AND score <= 1),"
                    + "length_status TEXT NOT NULL,"
                    + "action TEXT NOT NULL,"
                    + "reasons_text TEXT NOT NULL DEFAULT '',"
                    + "warnings_text TEXT NOT NULL DEFAULT '',"
                    + "PRIMARY KEY(session_id, rank),"
                    + "FOREIGN KEY(session_id) REFERENCES identification_session(session_id) ON DELETE CASCADE)",
            "CREATE TABLE IF NOT EXISTS knowledge_confirmation ("
                    + "confirmation_id TEXT PRIMARY KEY NOT NULL,"
                    + "session_id TEXT NOT NULL,"
                    + "material_code TEXT NOT NULL,"
                    + "ot TEXT,"
                    + "confirmed_at_epoch_ms INTEGER NOT NULL,"
                    + "confirmed_by TEXT NOT NULL DEFAULT '',"
                    + "note TEXT NOT NULL DEFAULT '',"
                    + "FOREIGN KEY(session_id) REFERENCES identification_session(session_id) ON DELETE CASCADE)",
            CREATE_DIMENSION_REVIEW,
            CREATE_DIMENSION_REVIEW_ITEM,
            "CREATE INDEX IF NOT EXISTS idx_dimension_review_material "
                    + "ON dimension_review(material_code, created_at_epoch_ms DESC)",
            "CREATE INDEX IF NOT EXISTS idx_dimension_review_pending "
                    + "ON dimension_review_item(response_status, priority)",
            "INSERT OR REPLACE INTO schema_meta(key, value) VALUES('schema_version', '2')",
            "INSERT OR REPLACE INTO schema_meta(key, value) VALUES('knowledge_format', 'MATERIAL_PULLEY_KB_V1')"
    ));

    public static final List<String> MIGRATION_1_TO_2 = Collections.unmodifiableList(Arrays.asList(
            CREATE_DIMENSION_REVIEW,
            CREATE_DIMENSION_REVIEW_ITEM,
            "CREATE INDEX IF NOT EXISTS idx_dimension_review_material "
                    + "ON dimension_review(material_code, created_at_epoch_ms DESC)",
            "CREATE INDEX IF NOT EXISTS idx_dimension_review_pending "
                    + "ON dimension_review_item(response_status, priority)",
            "INSERT OR REPLACE INTO schema_meta(key, value) VALUES('schema_version', '2')"
    ));

    /** Clears imported knowledge only. Field sessions, answers and confirmations are preserved. */
    public static final List<String> CLEAR_KNOWLEDGE_STATEMENTS = Collections.unmodifiableList(
            Arrays.asList(
                    "DELETE FROM component_evidence",
                    "DELETE FROM dimension_evidence",
                    "DELETE FROM intervention_note",
                    "DELETE FROM intervention_source",
                    "DELETE FROM intervention",
                    "DELETE FROM source_document",
                    "DELETE FROM family_role_hint",
                    "DELETE FROM family_client",
                    "DELETE FROM family_alias",
                    "DELETE FROM material_family"
            )
    );

    private SqlitePulleyKnowledgeSchema() {}
}

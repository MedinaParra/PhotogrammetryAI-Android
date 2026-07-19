package cl.ingenieria.photogrammetryai.core.materialhistory;

import cl.ingenieria.photogrammetryai.core.materialhistory.MaterialPulleyKnowledgeBase.MaterialFamily;

import java.util.List;
import java.util.Optional;

/**
 * Mutable persistence boundary for the local pulley knowledge engine.
 *
 * <p>SQLite is the production implementation. The domain only requests atomic family upserts,
 * complete snapshots for scoring, and durable identification audits.</p>
 */
public interface MaterialKnowledgeStore extends MaterialKnowledgeCatalog {
    /** Returns a complete immutable in-memory snapshot suitable for deterministic scoring. */
    MaterialPulleyKnowledgeBase snapshot();

    /** Inserts or merges a family and all its source-linked evidence atomically. */
    void upsertFamily(MaterialFamily family);

    /** Replaces the trusted knowledge package while preserving field-identification audits. */
    void replaceAll(MaterialPulleyKnowledgeBase knowledgeBase);

    /** Saves the query, ranked candidates and final decision for traceability and later learning. */
    void saveIdentificationAudit(IdentificationAudit audit);

    default Optional<IdentificationAudit> findIdentificationAudit(String sessionId) {
        if (sessionId == null || sessionId.trim().isEmpty()) return Optional.empty();
        for (IdentificationAudit audit : recentIdentificationAudits(1000)) {
            if (audit.sessionId().equals(sessionId.trim())) return Optional.of(audit);
        }
        return Optional.empty();
    }

    List<IdentificationAudit> recentIdentificationAudits(int limit);
}

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

    /** Replaces the database with a trusted versioned package, normally the bundled seed. */
    void replaceAll(MaterialPulleyKnowledgeBase knowledgeBase);

    /** Saves the query, ranked candidates and final decision for traceability and later learning. */
    void saveIdentificationAudit(IdentificationAudit audit);

    Optional<IdentificationAudit> findIdentificationAudit(String sessionId);

    List<IdentificationAudit> recentIdentificationAudits(int limit);
}

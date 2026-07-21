package cl.skm.pulleyai;

import android.content.Context;

/** End-to-end local service: SQLite evidence -> decision core -> append-only audit. */
public final class RevisionedIdentificationService implements AutoCloseable {
    private final RevisionedKnowledgeOpenHelper helper;
    private final RevisionedKnowledgeRepository repository;
    private final RevisionedDecisionAuditStore auditStore;

    public RevisionedIdentificationService(Context context) {
        helper = new RevisionedKnowledgeOpenHelper(context);
        helper.ensureSeeded();
        repository = new RevisionedKnowledgeRepository(helper);
        auditStore = new RevisionedDecisionAuditStore(helper);
    }

    public Result evaluateAndAudit(PulleyIdentificationDecisionCore.MeasurementSet measurements) {
        RevisionedPulleyKnowledgeCore.Catalog catalog = repository.loadCatalog();
        PulleyIdentificationDecisionCore.Decision decision =
                PulleyIdentificationDecisionCore.evaluate(catalog, measurements);
        String auditId = auditStore.append(measurements, decision);
        return new Result(auditId, decision);
    }

    public int auditCount() { return auditStore.count(); }

    @Override public void close() { helper.close(); }

    public static final class Result {
        public final String auditId;
        public final PulleyIdentificationDecisionCore.Decision decision;

        Result(String auditId, PulleyIdentificationDecisionCore.Decision decision) {
            this.auditId = auditId;
            this.decision = decision;
        }
    }
}

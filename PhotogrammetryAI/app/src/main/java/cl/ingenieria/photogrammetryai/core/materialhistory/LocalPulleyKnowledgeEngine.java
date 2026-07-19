package cl.ingenieria.photogrammetryai.core.materialhistory;

import cl.ingenieria.photogrammetryai.core.materialhistory.IdentificationAudit.RankedCandidate;
import cl.ingenieria.photogrammetryai.core.materialhistory.PulleyMaterialIdentificationEngine.Action;
import cl.ingenieria.photogrammetryai.core.materialhistory.PulleyMaterialIdentificationEngine.Candidate;
import cl.ingenieria.photogrammetryai.core.materialhistory.PulleyMaterialIdentificationEngine.Query;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/**
 * Application service coordinating seed installation, report ingestion, identification and audit.
 * This is the main logical entrypoint that the future Android GUI will call.
 */
public final class LocalPulleyKnowledgeEngine {
    public interface EpochClock {
        long nowEpochMs();
    }

    public interface SessionIdGenerator {
        String nextSessionId();
    }

    public static final class IdentificationOutcome {
        private final String sessionId;
        private final PulleyMaterialIdentificationEngine.Result result;

        private IdentificationOutcome(
                String sessionId,
                PulleyMaterialIdentificationEngine.Result result
        ) {
            this.sessionId = sessionId;
            this.result = result;
        }

        public String sessionId() { return sessionId; }
        public PulleyMaterialIdentificationEngine.Result result() { return result; }
    }

    private final MaterialKnowledgeStore store;
    private final QualityKnowledgeIngestionService ingestion;
    private final PulleyMaterialIdentificationEngine.Config identificationConfig;
    private final EpochClock clock;
    private final SessionIdGenerator ids;

    public LocalPulleyKnowledgeEngine(MaterialKnowledgeStore store) {
        this(
                store,
                new QualityReportTextParser(),
                PulleyMaterialIdentificationEngine.Config.industrialDefaults(),
                System::currentTimeMillis,
                () -> UUID.randomUUID().toString()
        );
    }

    public LocalPulleyKnowledgeEngine(
            MaterialKnowledgeStore store,
            QualityReportTextParser parser,
            PulleyMaterialIdentificationEngine.Config identificationConfig,
            EpochClock clock,
            SessionIdGenerator ids
    ) {
        this.store = Objects.requireNonNull(store, "store");
        this.ingestion = new QualityKnowledgeIngestionService(
                store,
                Objects.requireNonNull(parser, "parser")
        );
        this.identificationConfig = Objects.requireNonNull(
                identificationConfig,
                "identificationConfig"
        );
        this.clock = Objects.requireNonNull(clock, "clock");
        this.ids = Objects.requireNonNull(ids, "ids");
    }

    /** Installs the bundled verified seed only when the local database is empty. */
    public synchronized boolean initializeSeedIfEmpty() {
        if (store.size() > 0) return false;
        store.replaceAll(DriveQualityKnowledgeSeed.create());
        return true;
    }

    public synchronized QualityKnowledgeIngestionService.Result ingestQualityReport(
            QualityKnowledgeIngestionService.ReportInput input
    ) {
        return ingestion.ingest(input);
    }

    /**
     * Identifies candidates and persists the complete decision trace before returning.
     * Shell length remains mandatory because Query rejects zero or missing values.
     */
    public synchronized IdentificationOutcome identify(Query query, int limit) {
        Objects.requireNonNull(query, "query");
        PulleyMaterialIdentificationEngine identification =
                new PulleyMaterialIdentificationEngine(store.snapshot(), identificationConfig);
        PulleyMaterialIdentificationEngine.Result result = identification.identify(query, limit);
        String sessionId = ids.nextSessionId();
        if (sessionId == null || sessionId.trim().isEmpty()) {
            throw new IllegalStateException("SessionIdGenerator returned a blank id");
        }
        store.saveIdentificationAudit(toAudit(sessionId.trim(), query, result, false, null));
        return new IdentificationOutcome(sessionId.trim(), result);
    }

    /**
     * Confirms the operator-selected family without modifying historical facts automatically.
     * A later controlled-learning process may promote this confirmed session into new evidence.
     */
    public synchronized IdentificationAudit confirm(
            String sessionId,
            String selectedMaterialCode
    ) {
        IdentificationAudit previous = store.findIdentificationAudit(sessionId)
                .orElseThrow(() -> new IllegalArgumentException(
                        "Unknown identification session: " + sessionId
                ));
        String normalizedCode = MaterialPulleyKnowledgeBase.normalizeMaterialCode(
                selectedMaterialCode
        );
        if (!store.findByMaterialCode(normalizedCode).isPresent()) {
            throw new IllegalArgumentException(
                    "Selected material code is not present in local knowledge: " + normalizedCode
            );
        }
        Action decision = Action.VISUAL_ONLY;
        for (RankedCandidate candidate : previous.candidates()) {
            if (candidate.materialCode().equals(normalizedCode)) {
                decision = candidate.action();
                break;
            }
        }
        IdentificationAudit confirmed = new IdentificationAudit(
                previous.sessionId(),
                previous.createdAtEpochMs(),
                previous.shellLengthMm(),
                previous.enteredMaterialCode().orElse(null),
                previous.enteredOt().orElse(null),
                previous.measuredShellDiameterMm().orElse(null),
                previous.description(),
                normalizedCode,
                decision,
                true,
                previous.candidates()
        );
        store.saveIdentificationAudit(confirmed);
        return confirmed;
    }

    public MaterialKnowledgeStore store() {
        return store;
    }

    private IdentificationAudit toAudit(
            String sessionId,
            Query query,
            PulleyMaterialIdentificationEngine.Result result,
            boolean confirmed,
            String selectedOverride
    ) {
        List<RankedCandidate> candidates = new ArrayList<>();
        int rank = 1;
        for (Candidate candidate : result.candidates()) {
            candidates.add(new RankedCandidate(
                    rank++,
                    candidate.family().materialCode(),
                    candidate.score(),
                    candidate.lengthStatus(),
                    candidate.action(),
                    candidate.reasons(),
                    candidate.warnings()
            ));
        }
        Optional<Candidate> best = result.best();
        String selected = selectedOverride != null
                ? selectedOverride
                : best.map(candidate -> candidate.family().materialCode()).orElse(null);
        Action decision = best.map(Candidate::action).orElse(Action.NO_MATCH);
        return new IdentificationAudit(
                sessionId,
                clock.nowEpochMs(),
                query.shellLengthMm(),
                query.materialCode().orElse(null),
                query.ot().orElse(null),
                query.shellDiameterMm().orElse(null),
                query.description(),
                selected,
                decision,
                confirmed,
                candidates
        );
    }
}

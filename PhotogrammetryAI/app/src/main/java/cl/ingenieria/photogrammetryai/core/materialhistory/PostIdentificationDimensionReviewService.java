package cl.ingenieria.photogrammetryai.core.materialhistory;

import cl.ingenieria.photogrammetryai.core.materialhistory.MaterialPulleyKnowledgeBase.DimensionKind;
import cl.ingenieria.photogrammetryai.core.materialhistory.MaterialPulleyKnowledgeBase.MaterialFamily;
import cl.ingenieria.photogrammetryai.core.materialhistory.PulleyDimensionReview.ResponseStatus;
import cl.ingenieria.photogrammetryai.core.materialhistory.PulleyMaterialIdentificationEngine.Action;
import cl.ingenieria.photogrammetryai.core.materialhistory.PulleyMaterialIdentificationEngine.Query;

import java.util.Collections;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/** Coordinates suggestion generation, operator answers and durable review state. */
public final class PostIdentificationDimensionReviewService {
    public interface EpochClock {
        long nowEpochMs();
    }

    private final MaterialKnowledgeCatalog catalog;
    private final DimensionReviewStore store;
    private final PulleyDimensionSuggestionEngine suggestions;
    private final EpochClock clock;

    public PostIdentificationDimensionReviewService(
            MaterialKnowledgeCatalog catalog,
            DimensionReviewStore store,
            EpochClock clock
    ) {
        this.catalog = Objects.requireNonNull(catalog, "catalog");
        this.store = Objects.requireNonNull(store, "store");
        this.suggestions = new PulleyDimensionSuggestionEngine();
        this.clock = Objects.requireNonNull(clock, "clock");
    }

    public PulleyDimensionReview create(
            String sessionId,
            String materialCode,
            Action action,
            Query query
    ) {
        return create(sessionId, materialCode, action, query, Collections.emptyMap());
    }

    public PulleyDimensionReview create(
            String sessionId,
            String materialCode,
            Action action,
            Query query,
            Map<DimensionKind, PulleyDimensionSuggestionEngine.Observation> observations
    ) {
        MaterialFamily family = catalog.findByMaterialCode(materialCode)
                .orElseThrow(() -> new IllegalArgumentException(
                        "Unknown material family for dimensional review: " + materialCode
                ));
        PulleyDimensionReview review = suggestions.generate(
                sessionId,
                family,
                action,
                query,
                observations,
                clock.nowEpochMs()
        );
        store.save(review);
        return review;
    }

    public PulleyDimensionReview answer(
            String sessionId,
            DimensionKind kind,
            ResponseStatus status,
            Double measuredValueMm,
            String note
    ) {
        PulleyDimensionReview existing = store.find(sessionId)
                .orElseThrow(() -> new IllegalArgumentException(
                        "Unknown dimensional review session: " + sessionId
                ));
        PulleyDimensionReview updated = existing.answer(
                kind,
                status,
                measuredValueMm,
                note,
                clock.nowEpochMs()
        );
        store.save(updated);
        return updated;
    }

    public Optional<PulleyDimensionReview> find(String sessionId) {
        return store.find(sessionId);
    }
}

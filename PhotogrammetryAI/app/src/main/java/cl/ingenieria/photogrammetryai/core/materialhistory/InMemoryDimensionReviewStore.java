package cl.ingenieria.photogrammetryai.core.materialhistory;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

/** Deterministic review store for pure-Java tests. */
public final class InMemoryDimensionReviewStore implements DimensionReviewStore {
    private final Map<String, PulleyDimensionReview> reviews = new LinkedHashMap<>();

    @Override
    public synchronized void save(PulleyDimensionReview review) {
        if (review == null) throw new NullPointerException("review");
        reviews.put(review.sessionId(), review);
    }

    @Override
    public synchronized Optional<PulleyDimensionReview> find(String sessionId) {
        if (sessionId == null || sessionId.trim().isEmpty()) return Optional.empty();
        return Optional.ofNullable(reviews.get(sessionId.trim()));
    }
}

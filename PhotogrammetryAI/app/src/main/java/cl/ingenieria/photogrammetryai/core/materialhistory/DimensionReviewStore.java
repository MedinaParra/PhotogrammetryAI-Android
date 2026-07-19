package cl.ingenieria.photogrammetryai.core.materialhistory;

import java.util.Optional;

/** Persistence boundary for guided dimensional validation sessions. */
public interface DimensionReviewStore {
    void save(PulleyDimensionReview review);
    Optional<PulleyDimensionReview> find(String sessionId);
}

import cl.ingenieria.photogrammetryai.core.materialhistory.DriveQualityKnowledgeSeed;
import cl.ingenieria.photogrammetryai.core.materialhistory.InMemoryDimensionReviewStore;
import cl.ingenieria.photogrammetryai.core.materialhistory.InMemoryMaterialKnowledgeStore;
import cl.ingenieria.photogrammetryai.core.materialhistory.LocalPulleyKnowledgeEngine;
import cl.ingenieria.photogrammetryai.core.materialhistory.MaterialPulleyKnowledgeBase.DimensionKind;
import cl.ingenieria.photogrammetryai.core.materialhistory.PulleyDimensionReview;
import cl.ingenieria.photogrammetryai.core.materialhistory.PulleyDimensionReview.Priority;
import cl.ingenieria.photogrammetryai.core.materialhistory.PulleyDimensionReview.ResponseStatus;
import cl.ingenieria.photogrammetryai.core.materialhistory.PulleyDimensionReview.Suggestion;
import cl.ingenieria.photogrammetryai.core.materialhistory.PulleyMaterialIdentificationEngine;
import cl.ingenieria.photogrammetryai.core.materialhistory.PulleyMaterialIdentificationEngine.Action;
import cl.ingenieria.photogrammetryai.core.materialhistory.PulleyMaterialIdentificationEngine.Query;
import cl.ingenieria.photogrammetryai.core.materialhistory.QualityReportTextParser;
import cl.ingenieria.photogrammetryai.core.materialhistory.sqlite.SqlitePulleyKnowledgeSchema;

import java.util.Collections;

public final class DimensionReviewV24Test {
    public static void main(String[] args) {
        InMemoryMaterialKnowledgeStore knowledge = new InMemoryMaterialKnowledgeStore();
        knowledge.replaceAll(DriveQualityKnowledgeSeed.create());
        InMemoryDimensionReviewStore reviews = new InMemoryDimensionReviewStore();
        int[] sequence = {0};
        LocalPulleyKnowledgeEngine engine = new LocalPulleyKnowledgeEngine(
                knowledge,
                reviews,
                new QualityReportTextParser(),
                PulleyMaterialIdentificationEngine.Config.industrialDefaults(),
                () -> 1_721_390_400_000L + sequence[0],
                () -> "dimension-session-" + (++sequence[0])
        );

        LocalPulleyKnowledgeEngine.IdentificationOutcome outcome = engine.identify(
                new Query(
                        1520.0,
                        "10415863",
                        "OT-1702",
                        1395.0,
                        "POLEA BP LORBRAND BP5MSCP00273",
                        Collections.singleton("SD 3164")
                ),
                5
        );
        require(outcome.result().best().isPresent(), "Candidate missing");
        require(outcome.result().best().get().action() == Action.DIRECT_FAMILY_MATCH,
                "Expected direct family match");
        PulleyDimensionReview review = outcome.dimensionReview()
                .orElseThrow(() -> new AssertionError("Dimension review missing"));
        require(review.materialCode().equals("10415863"), "Wrong review family");
        require(review.suggestion(DimensionKind.SHELL_LENGTH).isPresent(), "Shell length missing");
        require(review.suggestion(DimensionKind.SHELL_LENGTH).get().responseStatus()
                        == ResponseStatus.CONFIRMED_MATCH,
                "Mandatory shell length should already be confirmed");
        requireNear(
                review.suggestion(DimensionKind.SHELL_DIAMETER).orElseThrow(AssertionError::new)
                        .historicalValueMm().orElse(0.0),
                1400.0,
                0.01,
                "Historical shell diameter"
        );
        requireNear(
                review.suggestion(DimensionKind.SUPPORT_CENTRE_DISTANCE)
                        .orElseThrow(AssertionError::new).recommendedValueMm(),
                2080.0,
                0.01,
                "Support centre distance"
        );
        requireNear(
                review.suggestion(DimensionKind.BEARING_CENTRE_DISTANCE)
                        .orElseThrow(AssertionError::new).recommendedValueMm(),
                2030.0,
                0.01,
                "Bearing centre distance"
        );
        require(!review.canUseRigidOverlay(), "Pending critical dimensions must block rigid overlay");

        PulleyDimensionReview answered = review;
        for (Suggestion suggestion : review.suggestions()) {
            if (suggestion.priority() != Priority.CRITICAL
                    || suggestion.responseStatus() != ResponseStatus.PENDING) {
                continue;
            }
            double fieldValue = suggestion.recommendedValueMm();
            ResponseStatus status = ResponseStatus.CONFIRMED_MATCH;
            if (suggestion.kind() == DimensionKind.SUPPORT_CENTRE_DISTANCE) fieldValue = 2084.0;
            if (suggestion.kind() == DimensionKind.BEARING_CENTRE_DISTANCE) {
                fieldValue = 2020.0;
                status = ResponseStatus.CORRECTED;
            }
            answered = engine.answerDimension(
                    review.sessionId(),
                    suggestion.kind(),
                    status,
                    fieldValue,
                    status == ResponseStatus.CORRECTED
                            ? "Corregido según levantamiento en terreno"
                            : "Coincide con levantamiento"
            );
        }
        require(answered.allCriticalResolved(), "All critical questions should be resolved");
        require(answered.canUseRigidOverlay(), "Confirmed critical dimensions should enable overlay");
        requireNear(
                answered.suggestion(DimensionKind.BEARING_CENTRE_DISTANCE)
                        .orElseThrow(AssertionError::new).effectiveValueMm(),
                2020.0,
                0.01,
                "Corrected bearing centre distance must become effective"
        );
        require(
                reviews.find(review.sessionId()).orElseThrow(AssertionError::new).canUseRigidOverlay(),
                "Review answers were not persisted"
        );

        LocalPulleyKnowledgeEngine.IdentificationOutcome conflicting = engine.identify(
                Query.byMaterialCode("10415863", 2032.0),
                3
        );
        require(conflicting.result().best().orElseThrow(AssertionError::new).action()
                        == Action.VERIFY_VARIANT,
                "Conflicting length must request variant verification");
        PulleyDimensionReview conflictReview = conflicting.dimensionReview()
                .orElseThrow(() -> new AssertionError("Conflict review missing"));
        require(!conflictReview.canUseRigidOverlay(),
                "VERIFY_VARIANT must block rigid overlay regardless of questions");

        require(SqlitePulleyKnowledgeSchema.DATABASE_VERSION == 2, "Expected SQLite schema v2");
        require(
                SqlitePulleyKnowledgeSchema.CREATE_STATEMENTS.stream()
                        .anyMatch(sql -> sql.contains("dimension_review_item")),
                "dimension_review_item table missing"
        );
        require(!SqlitePulleyKnowledgeSchema.MIGRATION_1_TO_2.isEmpty(),
                "Explicit v1 to v2 migration missing");

        System.out.println("DimensionReviewV24Test OK");
        System.out.println("questions=" + review.suggestions().size()
                + " pendingInitial=" + review.pendingCount()
                + " overlayReady=" + answered.canUseRigidOverlay());
    }

    private static void requireNear(double actual, double expected, double tolerance, String message) {
        if (Math.abs(actual - expected) > tolerance) {
            throw new AssertionError(message + ": expected=" + expected + " actual=" + actual);
        }
    }

    private static void require(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }
}

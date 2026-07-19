package cl.ingenieria.photogrammetryai.core.materialhistory.sqlite;

import android.content.ContentValues;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;

import cl.ingenieria.photogrammetryai.core.materialhistory.DimensionReviewStore;
import cl.ingenieria.photogrammetryai.core.materialhistory.MaterialPulleyKnowledgeBase.DimensionKind;
import cl.ingenieria.photogrammetryai.core.materialhistory.PulleyDimensionReview;
import cl.ingenieria.photogrammetryai.core.materialhistory.PulleyDimensionReview.Origin;
import cl.ingenieria.photogrammetryai.core.materialhistory.PulleyDimensionReview.Priority;
import cl.ingenieria.photogrammetryai.core.materialhistory.PulleyDimensionReview.ResponseStatus;
import cl.ingenieria.photogrammetryai.core.materialhistory.PulleyDimensionReview.Suggestion;
import cl.ingenieria.photogrammetryai.core.materialhistory.PulleyMaterialIdentificationEngine.Action;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/** Persists guided dimensional questions and operator answers in the local SQLite database. */
public final class AndroidSqliteDimensionReviewStore implements DimensionReviewStore {
    private final AndroidPulleyKnowledgeOpenHelper helper;

    public AndroidSqliteDimensionReviewStore(AndroidPulleyKnowledgeOpenHelper helper) {
        this.helper = Objects.requireNonNull(helper, "helper");
    }

    @Override
    public synchronized void save(PulleyDimensionReview review) {
        Objects.requireNonNull(review, "review");
        SQLiteDatabase db = helper.getWritableDatabase();
        db.beginTransaction();
        try {
            ContentValues header = new ContentValues();
            header.put("session_id", review.sessionId());
            header.put("material_code", review.materialCode());
            header.put("identification_action", review.identificationAction().name());
            header.put("created_at_epoch_ms", review.createdAtEpochMs());
            db.insertWithOnConflict(
                    "dimension_review",
                    null,
                    header,
                    SQLiteDatabase.CONFLICT_REPLACE
            );
            db.delete(
                    "dimension_review_item",
                    "session_id=?",
                    new String[]{review.sessionId()}
            );
            for (Suggestion suggestion : review.suggestions()) {
                ContentValues row = new ContentValues();
                row.put("session_id", review.sessionId());
                row.put("kind", suggestion.kind().name());
                row.put("label", suggestion.label());
                row.put("recommended_value_mm", suggestion.recommendedValueMm());
                putNullableDouble(row, "historical_value_mm", suggestion.historicalValueMm().orElse(null));
                putNullableDouble(row, "scan_value_mm", suggestion.scanValueMm().orElse(null));
                row.put("tolerance_mm", suggestion.toleranceMm());
                row.put("confidence", suggestion.confidence());
                row.put("priority", suggestion.priority().name());
                row.put("origin", suggestion.origin().name());
                row.put("source_summary", suggestion.sourceSummary());
                row.put("response_status", suggestion.responseStatus().name());
                putNullableDouble(row, "operator_value_mm", suggestion.operatorValueMm().orElse(null));
                putNullableDouble(row, "deviation_mm", suggestion.deviationMm().orElse(null));
                row.put("note", suggestion.note());
                putNullableLong(row, "answered_at_epoch_ms", suggestion.answeredAtEpochMs().orElse(null));
                db.insertOrThrow("dimension_review_item", null, row);
            }
            db.setTransactionSuccessful();
        } finally {
            db.endTransaction();
        }
    }

    @Override
    public synchronized Optional<PulleyDimensionReview> find(String sessionId) {
        if (sessionId == null || sessionId.trim().isEmpty()) return Optional.empty();
        SQLiteDatabase db = helper.getReadableDatabase();
        String normalized = sessionId.trim();
        String materialCode;
        Action action;
        long createdAt;
        try (Cursor cursor = db.rawQuery(
                "SELECT material_code,identification_action,created_at_epoch_ms "
                        + "FROM dimension_review WHERE session_id=?",
                new String[]{normalized}
        )) {
            if (!cursor.moveToFirst()) return Optional.empty();
            materialCode = cursor.getString(0);
            action = Action.valueOf(cursor.getString(1));
            createdAt = cursor.getLong(2);
        }

        List<Suggestion> suggestions = new ArrayList<>();
        try (Cursor cursor = db.rawQuery(
                "SELECT kind,label,recommended_value_mm,historical_value_mm,scan_value_mm,"
                        + "tolerance_mm,confidence,priority,origin,source_summary,response_status,"
                        + "operator_value_mm,deviation_mm,note,answered_at_epoch_ms "
                        + "FROM dimension_review_item WHERE session_id=? "
                        + "ORDER BY CASE priority WHEN 'CRITICAL' THEN 0 "
                        + "WHEN 'IMPORTANT' THEN 1 ELSE 2 END, rowid",
                new String[]{normalized}
        )) {
            while (cursor.moveToNext()) {
                suggestions.add(new Suggestion(
                        DimensionKind.valueOf(cursor.getString(0)),
                        cursor.getString(1),
                        cursor.getDouble(2),
                        nullableDouble(cursor, 3),
                        nullableDouble(cursor, 4),
                        cursor.getDouble(5),
                        cursor.getDouble(6),
                        Priority.valueOf(cursor.getString(7)),
                        Origin.valueOf(cursor.getString(8)),
                        cursor.getString(9),
                        ResponseStatus.valueOf(cursor.getString(10)),
                        nullableDouble(cursor, 11),
                        nullableDouble(cursor, 12),
                        cursor.getString(13),
                        nullableLong(cursor, 14)
                ));
            }
        }
        return Optional.of(new PulleyDimensionReview(
                normalized,
                materialCode,
                action,
                createdAt,
                suggestions
        ));
    }

    private static void putNullableDouble(ContentValues values, String key, Double value) {
        if (value == null) values.putNull(key); else values.put(key, value);
    }

    private static void putNullableLong(ContentValues values, String key, Long value) {
        if (value == null) values.putNull(key); else values.put(key, value);
    }

    private static Double nullableDouble(Cursor cursor, int index) {
        return cursor.isNull(index) ? null : cursor.getDouble(index);
    }

    private static Long nullableLong(Cursor cursor, int index) {
        return cursor.isNull(index) ? null : cursor.getLong(index);
    }
}

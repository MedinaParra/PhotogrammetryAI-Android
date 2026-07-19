package cl.ingenieria.photogrammetryai.core.foundation;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/** Interfaces that isolate the pure reconstruction core from Android, OpenCASCADE and storage. */
public final class CorePorts {
    private CorePorts() {
    }

    public interface StepReferenceImporter {
        ImportResult importStep(ImportRequest request);
    }

    public interface CadReferenceRepository {
        void put(CadReferenceModel model);

        Optional<CadReferenceModel> findById(String referenceId);

        Optional<CadReferenceModel> findBySha256(String sourceSha256);
    }

    public interface EventSink {
        void onEvent(CoreEvent event);
    }

    public interface NanoClock {
        long nowNanos();
    }

    public static final class ImportRequest {
        private final String localFilePath;
        private final String displayName;
        private final Map<String, String> hints;

        public ImportRequest(String localFilePath, String displayName, Map<String, String> hints) {
            this.localFilePath = requireText(localFilePath, "localFilePath");
            this.displayName = requireText(displayName, "displayName");
            Map<String, String> copy = new LinkedHashMap<>();
            if (hints != null) {
                copy.putAll(hints);
            }
            this.hints = Collections.unmodifiableMap(copy);
        }

        public String localFilePath() {
            return localFilePath;
        }

        public String displayName() {
            return displayName;
        }

        public Map<String, String> hints() {
            return hints;
        }
    }

    public static final class ImportResult {
        private final boolean success;
        private final CadReferenceModel model;
        private final String errorCode;
        private final String message;

        private ImportResult(boolean success, CadReferenceModel model, String errorCode, String message) {
            this.success = success;
            this.model = model;
            this.errorCode = errorCode;
            this.message = requireText(message, "message");
            if (success && model == null) {
                throw new IllegalArgumentException("Successful imports require a model");
            }
            if (!success && (errorCode == null || errorCode.trim().isEmpty())) {
                throw new IllegalArgumentException("Failed imports require an errorCode");
            }
        }

        public static ImportResult success(CadReferenceModel model, String message) {
            return new ImportResult(true, Objects.requireNonNull(model, "model"), null, message);
        }

        public static ImportResult failure(String errorCode, String message) {
            return new ImportResult(false, null, requireText(errorCode, "errorCode"), message);
        }

        public boolean success() {
            return success;
        }

        public Optional<CadReferenceModel> model() {
            return Optional.ofNullable(model);
        }

        public Optional<String> errorCode() {
            return Optional.ofNullable(errorCode);
        }

        public String message() {
            return message;
        }
    }

    public static final class CoreEvent {
        public enum Type {
            REFERENCE_REGISTERED,
            SESSION_STARTED,
            OBSERVATION_ACCEPTED,
            OBSERVATION_REJECTED,
            ESTIMATE_UPDATED,
            SESSION_FROZEN,
            FAILURE
        }

        private final Type type;
        private final long timestampNanos;
        private final String message;

        public CoreEvent(Type type, long timestampNanos, String message) {
            this.type = Objects.requireNonNull(type, "type");
            if (timestampNanos < 0L) {
                throw new IllegalArgumentException("timestampNanos must be non-negative");
            }
            this.timestampNanos = timestampNanos;
            this.message = requireText(message, "message");
        }

        public Type type() {
            return type;
        }

        public long timestampNanos() {
            return timestampNanos;
        }

        public String message() {
            return message;
        }
    }

    public static final EventSink NO_OP_EVENT_SINK = event -> {
    };

    public static final NanoClock SYSTEM_NANO_CLOCK = System::nanoTime;

    private static String requireText(String value, String name) {
        if (value == null || value.trim().isEmpty()) {
            throw new IllegalArgumentException(name + " must not be blank");
        }
        return value;
    }
}

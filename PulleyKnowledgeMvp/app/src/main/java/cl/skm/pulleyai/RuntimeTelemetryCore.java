package cl.skm.pulleyai;

import java.util.Locale;

/** Automatic, non-metrological runtime telemetry for one reconstruction attempt. */
public final class RuntimeTelemetryCore {
    private RuntimeTelemetryCore() {}

    public enum State { READY, REVIEW, BLOCKED }

    public static Result evaluate(Record record) {
        if (record == null || !record.valid()) {
            return new Result(State.BLOCKED, "TELEMETRY_INVALID", record);
        }
        if (record.availableMemoryBeforeBytes < 128L * 1024L * 1024L
                || record.usableStorageBytes < 300L * 1024L * 1024L) {
            return new Result(State.BLOCKED, "RESOURCE_FLOOR_NOT_MET", record);
        }
        if (record.interrupted) return new Result(State.REVIEW, "EXECUTION_INTERRUPTED", record);
        if (!record.windowReady) return new Result(State.REVIEW, "BA_WINDOW_NOT_READY", record);
        if (!"READY".equals(record.decisionState)) {
            return new Result(State.REVIEW, "RUNTIME_DECISION_" + record.decisionState, record);
        }
        return new Result(State.READY, "RUNTIME_EVIDENCE_COMPLETE", record);
    }

    public static final class Record {
        public final long startedAtEpochMs, durationMs;
        public final long heapBeforeBytes, heapAfterBytes, peakHeapBytes;
        public final long pssBeforeKb, pssAfterKb, peakPssKb;
        public final long availableMemoryBeforeBytes, availableMemoryAfterBytes;
        public final long usableStorageBytes;
        public final boolean windowReady, interrupted;
        public final String windowStatus, windowFingerprint;
        public final int cameraCount, pointCount, observationCount;
        public final String gateState, decisionState, decisionReason, baStatus;

        public Record(long startedAtEpochMs, long durationMs,
                      long heapBeforeBytes, long heapAfterBytes, long peakHeapBytes,
                      long pssBeforeKb, long pssAfterKb, long peakPssKb,
                      long availableMemoryBeforeBytes, long availableMemoryAfterBytes,
                      long usableStorageBytes, boolean windowReady, boolean interrupted,
                      String windowStatus, String windowFingerprint,
                      int cameraCount, int pointCount, int observationCount,
                      String gateState, String decisionState, String decisionReason,
                      String baStatus) {
            this.startedAtEpochMs = startedAtEpochMs; this.durationMs = durationMs;
            this.heapBeforeBytes = heapBeforeBytes; this.heapAfterBytes = heapAfterBytes;
            this.peakHeapBytes = peakHeapBytes; this.pssBeforeKb = pssBeforeKb;
            this.pssAfterKb = pssAfterKb; this.peakPssKb = peakPssKb;
            this.availableMemoryBeforeBytes = availableMemoryBeforeBytes;
            this.availableMemoryAfterBytes = availableMemoryAfterBytes;
            this.usableStorageBytes = usableStorageBytes; this.windowReady = windowReady;
            this.interrupted = interrupted; this.windowStatus = safe(windowStatus);
            this.windowFingerprint = safe(windowFingerprint); this.cameraCount = cameraCount;
            this.pointCount = pointCount; this.observationCount = observationCount;
            this.gateState = safe(gateState); this.decisionState = safe(decisionState);
            this.decisionReason = safe(decisionReason); this.baStatus = safe(baStatus);
        }

        boolean valid() {
            return startedAtEpochMs > 0 && durationMs >= 0 && heapBeforeBytes >= 0
                    && heapAfterBytes >= 0 && peakHeapBytes >= Math.max(heapBeforeBytes, heapAfterBytes)
                    && pssBeforeKb >= 0 && pssAfterKb >= 0
                    && peakPssKb >= Math.max(pssBeforeKb, pssAfterKb)
                    && availableMemoryBeforeBytes >= 0 && availableMemoryAfterBytes >= 0
                    && usableStorageBytes >= 0 && cameraCount >= 0 && pointCount >= 0
                    && observationCount >= 0 && !gateState.isEmpty()
                    && !decisionState.isEmpty() && !decisionReason.isEmpty();
        }
    }

    public static final class Result {
        public final State state;
        public final String reason;
        public final Record record;
        Result(State state, String reason, Record record) {
            this.state = state; this.reason = reason; this.record = record;
        }
        public String summary() {
            if (record == null) return state + " · " + reason;
            return state + " · " + reason
                    + String.format(Locale.ROOT,
                    " · %.2f s · heap %.1f MB · PSS %.1f MB · BA %d/%d/%d",
                    record.durationMs / 1000.0, record.peakHeapBytes / 1048576.0,
                    record.peakPssKb / 1024.0, record.cameraCount,
                    record.pointCount, record.observationCount);
        }
        public String canonicalJson() {
            if (record == null) return "{\"schema\":\"skm-runtime-telemetry/1\",\"state\":\"BLOCKED\",\"reason\":\"TELEMETRY_MISSING\"}";
            StringBuilder json = new StringBuilder(1024);
            json.append("{\n\"schema\":\"skm-runtime-telemetry/1\"")
                    .append(",\n\"state\":\"").append(state).append("\"")
                    .append(",\n\"reason\":\"").append(escape(reason)).append("\"")
                    .append(",\n\"startedAtEpochMs\":").append(record.startedAtEpochMs)
                    .append(",\n\"durationMs\":").append(record.durationMs)
                    .append(",\n\"heapBeforeBytes\":").append(record.heapBeforeBytes)
                    .append(",\n\"heapAfterBytes\":").append(record.heapAfterBytes)
                    .append(",\n\"peakHeapBytes\":").append(record.peakHeapBytes)
                    .append(",\n\"pssBeforeKb\":").append(record.pssBeforeKb)
                    .append(",\n\"pssAfterKb\":").append(record.pssAfterKb)
                    .append(",\n\"peakPssKb\":").append(record.peakPssKb)
                    .append(",\n\"availableMemoryBeforeBytes\":").append(record.availableMemoryBeforeBytes)
                    .append(",\n\"availableMemoryAfterBytes\":").append(record.availableMemoryAfterBytes)
                    .append(",\n\"usableStorageBytes\":").append(record.usableStorageBytes)
                    .append(",\n\"windowReady\":").append(record.windowReady)
                    .append(",\n\"windowStatus\":\"").append(escape(record.windowStatus)).append("\"")
                    .append(",\n\"windowFingerprint\":\"").append(escape(record.windowFingerprint)).append("\"")
                    .append(",\n\"cameraCount\":").append(record.cameraCount)
                    .append(",\n\"pointCount\":").append(record.pointCount)
                    .append(",\n\"observationCount\":").append(record.observationCount)
                    .append(",\n\"gateState\":\"").append(escape(record.gateState)).append("\"")
                    .append(",\n\"decisionState\":\"").append(escape(record.decisionState)).append("\"")
                    .append(",\n\"decisionReason\":\"").append(escape(record.decisionReason)).append("\"")
                    .append(",\n\"baStatus\":\"").append(escape(record.baStatus)).append("\"")
                    .append(",\n\"interrupted\":").append(record.interrupted).append("\n}");
            return json.toString();
        }
    }

    private static String safe(String value) { return value == null ? "" : value; }
    private static String escape(String value) {
        return safe(value).replace("\\", "\\\\").replace("\"", "\\\"")
                .replace("\n", "\\n").replace("\r", "\\r");
    }
}

package cl.skm.pulleyai;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;

/** Canonical proof that a runtime execution was generated automatically by the application. */
public final class RuntimeExecutionEvidenceCore {
    private RuntimeExecutionEvidenceCore() {}

    public static Result build(String sessionId, String runId, String publicationState,
                               long startedAtEpochMs, long completedAtEpochMs, long durationMs,
                               int acceptedFrames, int rejectedFrames,
                               String windowStatus, int cameraCount, int pointCount,
                               int observationCount, String gateState, String decisionState,
                               String decisionReason, boolean optimizedGeometryAccepted,
                               boolean fallbackUnoptimized, boolean interrupted,
                               String controlState) {
        String canonical = canonical(sessionId, runId, publicationState,
                startedAtEpochMs, completedAtEpochMs, durationMs,
                acceptedFrames, rejectedFrames, windowStatus, cameraCount,
                pointCount, observationCount, gateState, decisionState,
                decisionReason, optimizedGeometryAccepted, fallbackUnoptimized,
                interrupted, controlState, null);
        String fingerprint = sha256(canonical);
        String json = canonical(sessionId, runId, publicationState,
                startedAtEpochMs, completedAtEpochMs, durationMs,
                acceptedFrames, rejectedFrames, windowStatus, cameraCount,
                pointCount, observationCount, gateState, decisionState,
                decisionReason, optimizedGeometryAccepted, fallbackUnoptimized,
                interrupted, controlState, fingerprint);
        boolean complete = !blank(sessionId) && !blank(runId)
                && startedAtEpochMs > 0L && completedAtEpochMs >= startedAtEpochMs
                && durationMs >= 0L && !blank(publicationState);
        return new Result(complete, fingerprint, json);
    }

    private static String canonical(String sessionId, String runId, String publicationState,
                                    long startedAtEpochMs, long completedAtEpochMs, long durationMs,
                                    int acceptedFrames, int rejectedFrames,
                                    String windowStatus, int cameraCount, int pointCount,
                                    int observationCount, String gateState, String decisionState,
                                    String decisionReason, boolean optimizedGeometryAccepted,
                                    boolean fallbackUnoptimized, boolean interrupted,
                                    String controlState, String fingerprint) {
        StringBuilder json = new StringBuilder(900);
        json.append("{\n\"schema\":\"skm-runtime-execution-evidence/1\"")
                .append(",\n\"source\":\"AUTOMATIC_RUNTIME\"")
                .append(",\n\"automated\":true")
                .append(",\n\"manualEntry\":false")
                .append(",\n\"sessionId\":\"").append(escape(value(sessionId))).append("\"")
                .append(",\n\"runId\":\"").append(escape(value(runId))).append("\"")
                .append(",\n\"publicationState\":\"").append(escape(value(publicationState))).append("\"")
                .append(",\n\"startedAtEpochMs\":").append(startedAtEpochMs)
                .append(",\n\"completedAtEpochMs\":").append(completedAtEpochMs)
                .append(",\n\"durationMs\":").append(Math.max(0L, durationMs))
                .append(",\n\"acceptedFrames\":").append(Math.max(0, acceptedFrames))
                .append(",\n\"rejectedFrames\":").append(Math.max(0, rejectedFrames))
                .append(",\n\"windowStatus\":\"").append(escape(value(windowStatus))).append("\"")
                .append(",\n\"cameraCount\":").append(Math.max(0, cameraCount))
                .append(",\n\"pointCount\":").append(Math.max(0, pointCount))
                .append(",\n\"observationCount\":").append(Math.max(0, observationCount))
                .append(",\n\"gateState\":\"").append(escape(value(gateState))).append("\"")
                .append(",\n\"decisionState\":\"").append(escape(value(decisionState))).append("\"")
                .append(",\n\"decisionReason\":\"").append(escape(value(decisionReason))).append("\"")
                .append(",\n\"optimizedGeometryAccepted\":").append(optimizedGeometryAccepted)
                .append(",\n\"fallbackUnoptimized\":").append(fallbackUnoptimized)
                .append(",\n\"interrupted\":").append(interrupted)
                .append(",\n\"controlState\":\"").append(escape(value(controlState))).append("\"");
        if (fingerprint != null) {
            json.append(",\n\"fingerprint\":\"").append(fingerprint).append("\"");
        }
        return json.append("\n}").toString();
    }

    private static String sha256(String value) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] bytes = digest.digest(value.getBytes(StandardCharsets.UTF_8));
            StringBuilder out = new StringBuilder(64);
            for (byte item : bytes) out.append(String.format(java.util.Locale.ROOT, "%02x", item & 0xff));
            return out.toString();
        } catch (Exception error) {
            throw new IllegalStateException(error);
        }
    }

    private static boolean blank(String value) {
        return value == null || value.trim().isEmpty();
    }

    private static String value(String value) { return value == null ? "" : value; }

    private static String escape(String value) {
        return value(value).replace("\\", "\\\\").replace("\"", "\\\"")
                .replace("\n", "\\n").replace("\r", "\\r");
    }

    public static final class Result {
        public final boolean complete;
        public final String fingerprint;
        public final String canonicalJson;

        Result(boolean complete, String fingerprint, String canonicalJson) {
            this.complete = complete;
            this.fingerprint = fingerprint;
            this.canonicalJson = canonicalJson;
        }

        public String summary() {
            return "Ejecución automática " + (complete ? "COMPLETE" : "INCOMPLETE")
                    + " · huella " + fingerprint.substring(0, Math.min(12, fingerprint.length()));
        }
    }
}

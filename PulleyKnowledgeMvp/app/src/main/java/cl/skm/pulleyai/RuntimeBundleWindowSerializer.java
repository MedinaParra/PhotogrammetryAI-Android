package cl.skm.pulleyai;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Locale;

/** Canonical full serialization of a bounded runtime BA problem. */
public final class RuntimeBundleWindowSerializer {
    private RuntimeBundleWindowSerializer() {}

    public static String canonicalJson(RuntimeBundleWindowCore.Result result) {
        if (result == null) throw new IllegalArgumentException("result is required");
        StringBuilder json = new StringBuilder(4096 + result.observationCount * 80);
        json.append("{\n\"schema\":\"skm-runtime-ba-window/2\"")
                .append(",\n\"status\":\"").append(escape(result.status)).append("\"")
                .append(",\n\"ready\":").append(result.ready)
                .append(",\n\"cameraCount\":").append(result.cameraCount)
                .append(",\n\"pointCount\":").append(result.pointCount)
                .append(",\n\"observationCount\":").append(result.observationCount)
                .append(",\n\"cameraGlobalIndices\":").append(integerArray(result.cameraGlobalIndices))
                .append(",\n\"pointTrackIds\":").append(integerArray(result.pointTrackIds));
        if (!result.ready || result.problem == null) {
            json.append(",\n\"cameras\":[]")
                    .append(",\n\"points\":[]")
                    .append(",\n\"observations\":[]")
                    .append(",\n\"sha256\":\"").append(sha256(payload(result))).append("\"\n}");
            return json.toString();
        }
        json.append(",\n\"cameras\":[");
        for (int i = 0; i < result.problem.cameras.size(); i++) {
            LocalBundleAdjustmentCore.Camera camera = result.problem.cameras.get(i);
            if (i > 0) json.append(',');
            json.append("{\"localIndex\":").append(i)
                    .append(",\"globalIndex\":").append(result.cameraGlobalIndices.get(i))
                    .append(",\"rotation\":").append(matrix(camera.rotation))
                    .append(",\"translation\":").append(vector(camera.translation))
                    .append(",\"fx\":").append(number(camera.fx))
                    .append(",\"fy\":").append(number(camera.fy))
                    .append(",\"cx\":").append(number(camera.cx))
                    .append(",\"cy\":").append(number(camera.cy)).append('}');
        }
        json.append(']');
        json.append(",\n\"points\":[");
        for (int i = 0; i < result.problem.points.size(); i++) {
            LocalBundleAdjustmentCore.Point3 point = result.problem.points.get(i);
            if (i > 0) json.append(',');
            json.append("{\"localIndex\":").append(i)
                    .append(",\"trackId\":").append(result.pointTrackIds.get(i))
                    .append(",\"x\":").append(number(point.x))
                    .append(",\"y\":").append(number(point.y))
                    .append(",\"z\":").append(number(point.z)).append('}');
        }
        json.append(']');
        json.append(",\n\"observations\":[");
        for (int i = 0; i < result.problem.observations.size(); i++) {
            LocalBundleAdjustmentCore.Observation observation = result.problem.observations.get(i);
            if (i > 0) json.append(',');
            json.append("{\"cameraIndex\":").append(observation.cameraIndex)
                    .append(",\"pointIndex\":").append(observation.pointIndex)
                    .append(",\"u\":").append(number(observation.u))
                    .append(",\"v\":").append(number(observation.v))
                    .append(",\"weight\":").append(number(observation.weight)).append('}');
        }
        json.append(']');
        String payload = payload(result);
        json.append(",\n\"sha256\":\"").append(sha256(payload)).append("\"\n}");
        return json.toString();
    }

    public static String fingerprint(RuntimeBundleWindowCore.Result result) {
        return sha256(payload(result));
    }

    private static String payload(RuntimeBundleWindowCore.Result result) {
        StringBuilder value = new StringBuilder();
        value.append(result.status).append('|').append(result.ready)
                .append('|').append(result.cameraGlobalIndices)
                .append('|').append(result.pointTrackIds);
        if (result.problem != null) {
            for (LocalBundleAdjustmentCore.Camera camera : result.problem.cameras) {
                value.append('|').append(matrix(camera.rotation)).append('|').append(vector(camera.translation))
                        .append('|').append(number(camera.fx)).append('|').append(number(camera.fy))
                        .append('|').append(number(camera.cx)).append('|').append(number(camera.cy));
            }
            for (LocalBundleAdjustmentCore.Point3 point : result.problem.points) {
                value.append('|').append(number(point.x)).append('|').append(number(point.y))
                        .append('|').append(number(point.z));
            }
            for (LocalBundleAdjustmentCore.Observation observation : result.problem.observations) {
                value.append('|').append(observation.cameraIndex).append(':').append(observation.pointIndex)
                        .append(':').append(number(observation.u)).append(':').append(number(observation.v))
                        .append(':').append(number(observation.weight));
            }
        }
        return value.toString();
    }

    private static String matrix(double[][] value) {
        StringBuilder text = new StringBuilder("[");
        for (int i = 0; i < value.length; i++) {
            if (i > 0) text.append(',');
            text.append(vector(value[i]));
        }
        return text.append(']').toString();
    }
    private static String vector(double[] value) {
        StringBuilder text = new StringBuilder("[");
        for (int i = 0; i < value.length; i++) {
            if (i > 0) text.append(',');
            text.append(number(value[i]));
        }
        return text.append(']').toString();
    }
    private static String integerArray(java.util.List<Integer> values) {
        StringBuilder text = new StringBuilder("[");
        for (int i = 0; i < values.size(); i++) {
            if (i > 0) text.append(',');
            text.append(values.get(i));
        }
        return text.append(']').toString();
    }
    private static String number(double value) {
        if (!Double.isFinite(value)) return "null";
        return String.format(Locale.ROOT, "%.12g", value);
    }
    private static String escape(String value) {
        return value == null ? "" : value.replace("\\", "\\\\").replace("\"", "\\\"");
    }
    private static String sha256(String value) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest(value.getBytes(StandardCharsets.UTF_8));
            StringBuilder output = new StringBuilder(64);
            for (byte item : digest) output.append(String.format(Locale.ROOT, "%02x", item & 0xff));
            return output.toString();
        } catch (Exception error) {
            throw new IllegalStateException(error);
        }
    }
}

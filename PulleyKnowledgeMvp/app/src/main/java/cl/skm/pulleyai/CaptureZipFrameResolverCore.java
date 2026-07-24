package cl.skm.pulleyai;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * Resolves exported frame entries without assuming a single exact spelling and
 * keeps an auditable preflight summary for field ZIP packages.
 */
public final class CaptureZipFrameResolverCore {
    private CaptureZipFrameResolverCore() {}

    public static String resolveEntryName(Set<String> entryNames, int sequence) {
        if (entryNames == null || entryNames.isEmpty() || sequence < 0) return null;
        List<String> candidates = candidates(sequence);
        for (String candidate : candidates) {
            if (entryNames.contains(candidate)) return candidate;
        }
        Map<String, String> lower = new HashMap<String, String>();
        for (String name : entryNames) {
            if (name != null) lower.put(name.toLowerCase(Locale.ROOT), name);
        }
        for (String candidate : candidates) {
            String actual = lower.get(candidate.toLowerCase(Locale.ROOT));
            if (actual != null) return actual;
        }
        String suffix4 = String.format(Locale.ROOT, "frame_%04d.jpg", sequence);
        String suffixRaw = "frame_" + sequence + ".jpg";
        for (String name : entryNames) {
            if (name == null) continue;
            String normalized = name.replace('\\', '/').toLowerCase(Locale.ROOT);
            if (normalized.endsWith(suffix4) || normalized.endsWith(suffixRaw)) return name;
        }
        return null;
    }

    public static List<String> candidates(int sequence) {
        if (sequence < 0) return Collections.emptyList();
        List<String> names = new ArrayList<String>();
        names.add(String.format(Locale.ROOT, "frames/frame_%04d.jpg", sequence));
        names.add(String.format(Locale.ROOT, "frames/frame_%04d.jpeg", sequence));
        names.add("frames/frame_" + sequence + ".jpg");
        names.add("frames/frame_" + sequence + ".jpeg");
        names.add(String.format(Locale.ROOT, "frame_%04d.jpg", sequence));
        names.add("frame_" + sequence + ".jpg");
        return names;
    }

    public static final class Preflight {
        public final int manifestFrames;
        public final int acceptedFrames;
        public int entriesFound;
        public int hashesVerified;
        public int decodedFrames;
        public final Map<Integer, String> failures = new LinkedHashMap<Integer, String>();

        public Preflight(int manifestFrames, int acceptedFrames) {
            this.manifestFrames = Math.max(0, manifestFrames);
            this.acceptedFrames = Math.max(0, acceptedFrames);
        }

        public void fail(int sequence, String reason) {
            if (!failures.containsKey(sequence)) {
                failures.put(sequence, reason == null || reason.trim().isEmpty()
                        ? "UNKNOWN" : reason.trim());
            }
        }

        public String concise() {
            StringBuilder text = new StringBuilder();
            text.append("manifest ").append(manifestFrames)
                    .append(" · aceptadas ").append(acceptedFrames)
                    .append(" · entradas ").append(entriesFound)
                    .append(" · SHA ").append(hashesVerified)
                    .append(" · decodificadas ").append(decodedFrames);
            if (!failures.isEmpty()) {
                text.append(" · fallas ").append(failures.size()).append(" [");
                int shown = 0;
                for (Map.Entry<Integer, String> failure : failures.entrySet()) {
                    if (shown++ > 0) text.append("; ");
                    text.append('#').append(failure.getKey()).append(' ')
                            .append(failure.getValue());
                    if (shown >= 4) break;
                }
                if (failures.size() > shown) text.append("; …");
                text.append(']');
            }
            return text.toString();
        }

        public String canonicalJson() {
            StringBuilder json = new StringBuilder();
            json.append("{\n")
                    .append("\"schema\":\"skm-capture-zip-preflight/1\",")
                    .append("\n\"manifestFrames\":").append(manifestFrames).append(',')
                    .append("\n\"acceptedFrames\":").append(acceptedFrames).append(',')
                    .append("\n\"entriesFound\":").append(entriesFound).append(',')
                    .append("\n\"hashesVerified\":").append(hashesVerified).append(',')
                    .append("\n\"decodedFrames\":").append(decodedFrames).append(',')
                    .append("\n\"failures\":[");
            int index = 0;
            for (Map.Entry<Integer, String> failure : failures.entrySet()) {
                if (index++ > 0) json.append(',');
                json.append("{\"sequence\":").append(failure.getKey())
                        .append(",\"reason\":\"")
                        .append(escape(failure.getValue())).append("\"}");
            }
            return json.append("]\n}").toString();
        }
    }

    private static String escape(String value) {
        if (value == null) return "";
        return value.replace("\\", "\\\\")
                .replace("\"", "\\\"")
                .replace("\n", "\\n")
                .replace("\r", "\\r");
    }
}

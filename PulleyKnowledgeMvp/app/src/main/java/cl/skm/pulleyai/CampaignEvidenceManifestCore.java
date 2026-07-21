package cl.skm.pulleyai;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

/** Canonical manifest binding app, device, session, frames and runtime evidence hashes. */
public final class CampaignEvidenceManifestCore {
    private CampaignEvidenceManifestCore() {}

    public static Result build(String appVersion, String apkSha256,
                               String device, int sdkInt,
                               String sessionId, String materialCode, String ot,
                               List<Item> sourceItems) {
        List<String> gaps = new ArrayList<String>();
        if (blank(appVersion)) gaps.add("APP_VERSION_MISSING");
        if (!sha(apkSha256)) gaps.add("APK_SHA256_MISSING");
        if (blank(device)) gaps.add("DEVICE_MISSING");
        if (sdkInt <= 0) gaps.add("SDK_MISSING");
        if (blank(sessionId)) gaps.add("SESSION_ID_MISSING");
        List<Item> items = new ArrayList<Item>();
        if (sourceItems != null) {
            for (Item item : sourceItems) if (item != null && item.valid()) items.add(item);
        }
        Collections.sort(items, new Comparator<Item>() {
            @Override public int compare(Item a, Item b) { return a.path.compareTo(b.path); }
        });
        if (items.isEmpty()) gaps.add("EVIDENCE_ITEMS_MISSING");
        boolean hasFrame = false, hasRuntime = false;
        for (Item item : items) {
            if ("FRAME".equals(item.kind)) hasFrame = true;
            if ("RUNTIME".equals(item.kind)) hasRuntime = true;
        }
        if (!hasFrame) gaps.add("FRAME_HASHES_MISSING");
        if (!hasRuntime) gaps.add("RUNTIME_HASHES_MISSING");
        String state = gaps.isEmpty() ? "COMPLETE" : "INCOMPLETE";
        String canonical = canonical(state, appVersion, apkSha256, device, sdkInt,
                sessionId, materialCode, ot, items, gaps, null);
        String fingerprint = digest(canonical);
        String finalJson = canonical(state, appVersion, apkSha256, device, sdkInt,
                sessionId, materialCode, ot, items, gaps, fingerprint);
        return new Result(state, fingerprint, items, gaps, finalJson);
    }

    private static String canonical(String state, String appVersion, String apkSha256,
                                    String device, int sdkInt, String sessionId,
                                    String materialCode, String ot, List<Item> items,
                                    List<String> gaps, String fingerprint) {
        StringBuilder json = new StringBuilder(1400 + items.size() * 150);
        json.append("{\n\"schema\":\"skm-campaign-evidence/1\"")
                .append(",\n\"state\":\"").append(escape(state)).append("\"")
                .append(",\n\"appVersion\":\"").append(escape(value(appVersion))).append("\"")
                .append(",\n\"apkSha256\":\"").append(escape(value(apkSha256))).append("\"")
                .append(",\n\"device\":\"").append(escape(value(device))).append("\"")
                .append(",\n\"sdkInt\":").append(sdkInt)
                .append(",\n\"sessionId\":\"").append(escape(value(sessionId))).append("\"")
                .append(",\n\"materialCode\":\"").append(escape(value(materialCode))).append("\"")
                .append(",\n\"ot\":\"").append(escape(value(ot))).append("\"")
                .append(",\n\"items\":[");
        for (int i = 0; i < items.size(); i++) {
            Item item = items.get(i);
            if (i > 0) json.append(',');
            json.append("{\"kind\":\"").append(escape(item.kind))
                    .append("\",\"path\":\"").append(escape(item.path))
                    .append("\",\"sizeBytes\":").append(item.sizeBytes)
                    .append(",\"sha256\":\"").append(item.sha256).append("\"}");
        }
        json.append("]").append(",\n\"gaps\":[");
        for (int i = 0; i < gaps.size(); i++) {
            if (i > 0) json.append(',');
            json.append('"').append(escape(gaps.get(i))).append('"');
        }
        json.append(']');
        if (fingerprint != null) json.append(",\n\"fingerprint\":\"").append(fingerprint).append("\"");
        return json.append("\n}").toString();
    }

    public static final class Item {
        public final String kind, path, sha256;
        public final long sizeBytes;
        public Item(String kind, String path, long sizeBytes, String sha256) {
            this.kind = value(kind).trim().toUpperCase(java.util.Locale.ROOT);
            this.path = value(path).replace('\\', '/');
            this.sizeBytes = Math.max(0L, sizeBytes);
            this.sha256 = value(sha256).trim().toLowerCase(java.util.Locale.ROOT);
        }
        boolean valid() {
            return !blank(kind) && !blank(path) && !path.startsWith("/")
                    && !path.contains("../") && sha(sha256);
        }
    }

    public static final class Result {
        public final String state, fingerprint, canonicalJson;
        public final List<Item> items;
        public final List<String> gaps;
        Result(String state, String fingerprint, List<Item> items,
               List<String> gaps, String canonicalJson) {
            this.state = state; this.fingerprint = fingerprint;
            this.items = Collections.unmodifiableList(new ArrayList<Item>(items));
            this.gaps = Collections.unmodifiableList(new ArrayList<String>(gaps));
            this.canonicalJson = canonicalJson;
        }
        public boolean complete() { return "COMPLETE".equals(state); }
        public String summary() {
            return "Manifiesto " + state + " · elementos " + items.size()
                    + " · huella " + fingerprint.substring(0, Math.min(12, fingerprint.length()));
        }
    }

    private static boolean blank(String value) { return value == null || value.trim().isEmpty(); }
    private static boolean sha(String value) { return value != null && value.matches("[0-9a-fA-F]{64}"); }
    private static String value(String value) { return value == null ? "" : value; }
    private static String digest(String content) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] bytes = digest.digest(content.getBytes(StandardCharsets.UTF_8));
            StringBuilder out = new StringBuilder(64);
            for (byte b : bytes) out.append(String.format(java.util.Locale.ROOT, "%02x", b & 0xff));
            return out.toString();
        } catch (Exception error) {
            throw new IllegalStateException(error);
        }
    }
    private static String escape(String value) {
        return value(value).replace("\\", "\\\\").replace("\"", "\\\"")
                .replace("\n", "\\n").replace("\r", "\\r");
    }
}
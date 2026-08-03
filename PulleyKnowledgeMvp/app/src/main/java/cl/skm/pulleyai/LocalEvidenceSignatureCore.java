package cl.skm.pulleyai;

import java.nio.charset.StandardCharsets;
import java.security.KeyFactory;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.MessageDigest;
import java.security.PublicKey;
import java.security.Signature;
import java.security.spec.X509EncodedKeySpec;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Signs canonical evidence locally while explicitly denying corporate identity semantics. */
public final class LocalEvidenceSignatureCore {
    public static final String IDENTITY_LABEL = "LOCAL_DEVICE_KEY_NOT_CORPORATE_IDENTITY";
    private static final char[] BASE64 =
            "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789+/".toCharArray();

    private LocalEvidenceSignatureCore() {}

    public static KeyPair generateSoftwareKeyPair() throws Exception {
        return KeyPairGenerator.getInstance("Ed25519").generateKeyPair();
    }

    public static Result sign(String payload, KeyPair keyPair, String keyOrigin,
                              boolean hardwareBacked, boolean strongBoxBacked) throws Exception {
        if (payload == null) throw new IllegalArgumentException("payload is required");
        if (keyPair == null || keyPair.getPrivate() == null || keyPair.getPublic() == null) {
            throw new IllegalArgumentException("complete key pair is required");
        }
        Signature signer = signatureEngine();
        signer.initSign(keyPair.getPrivate());
        signer.update(payload.getBytes(StandardCharsets.UTF_8));
        String payloadSha256 = sha256(payload);
        String publicKey = encodeBase64(keyPair.getPublic().getEncoded());
        String signature = encodeBase64(signer.sign());
        String origin = clean(keyOrigin, "SOFTWARE_APP_PRIVATE");
        String json = canonical(origin, hardwareBacked, strongBoxBacked,
                payloadSha256, publicKey, signature, true, "");
        Verification verification = verify(payload, json);
        if (!verification.valid) {
            throw new IllegalStateException("local signature self-verification failed: "
                    + verification.issues);
        }
        return new Result(true, origin, hardwareBacked, strongBoxBacked,
                payloadSha256, publicKey, signature, json, "");
    }

    public static Result unavailable(String payload, String reason) {
        String hash = sha256(payload == null ? "" : payload);
        String cleanReason = clean(reason, "ED25519_UNAVAILABLE");
        String json = canonical("UNAVAILABLE", false, false, hash,
                "", "", false, cleanReason);
        return new Result(false, "UNAVAILABLE", false, false,
                hash, "", "", json, cleanReason);
    }

    public static Verification verify(String payload, String signatureJson) {
        List<String> issues = new ArrayList<String>();
        if (payload == null) issues.add("PAYLOAD_MISSING");
        if (signatureJson == null || signatureJson.trim().isEmpty()) {
            issues.add("SIGNATURE_EVIDENCE_MISSING");
            return new Verification(false, "UNKNOWN", false, false, "", issues);
        }
        String algorithm = stringField(signatureJson, "algorithm");
        String origin = stringField(signatureJson, "keyOrigin");
        String label = stringField(signatureJson, "identityLabel");
        String expectedHash = stringField(signatureJson, "payloadSha256");
        String publicKeyBase64 = stringField(signatureJson, "publicKeyBase64");
        String signatureBase64 = stringField(signatureJson, "signatureBase64");
        boolean corporateIdentity = booleanField(signatureJson, "corporateIdentity", true);
        boolean hardwareBacked = booleanField(signatureJson, "hardwareBacked", false);
        boolean strongBoxBacked = booleanField(signatureJson, "strongBoxBacked", false);
        if (!"Ed25519".equals(algorithm)) issues.add("ALGORITHM_MISMATCH");
        if (!IDENTITY_LABEL.equals(label)) issues.add("IDENTITY_LABEL_MISMATCH");
        if (corporateIdentity) issues.add("CORPORATE_IDENTITY_MUST_BE_FALSE");
        String actualHash = sha256(payload == null ? "" : payload);
        if (!actualHash.equals(expectedHash)) issues.add("PAYLOAD_SHA256_MISMATCH");
        if (publicKeyBase64.isEmpty()) issues.add("PUBLIC_KEY_MISSING");
        if (signatureBase64.isEmpty()) issues.add("SIGNATURE_MISSING");
        if (issues.isEmpty()) {
            try {
                PublicKey publicKey = keyFactory().generatePublic(
                        new X509EncodedKeySpec(decodeBase64(publicKeyBase64)));
                Signature verifier = signatureEngine();
                verifier.initVerify(publicKey);
                verifier.update(payload.getBytes(StandardCharsets.UTF_8));
                if (!verifier.verify(decodeBase64(signatureBase64))) {
                    issues.add("SIGNATURE_INVALID");
                }
            } catch (Exception error) {
                issues.add("SIGNATURE_VERIFY_ERROR:" + error.getClass().getSimpleName());
            }
        }
        return new Verification(issues.isEmpty(), clean(origin, "UNKNOWN"),
                hardwareBacked, strongBoxBacked, actualHash, issues);
    }

    private static String canonical(String origin, boolean hardwareBacked,
                                    boolean strongBoxBacked, String payloadSha256,
                                    String publicKeyBase64, String signatureBase64,
                                    boolean verified, String reason) {
        return "{\n\"schema\":\"skm-local-evidence-signature/1\""
                + ",\n\"algorithm\":\"Ed25519\""
                + ",\n\"keyOrigin\":\"" + escape(origin) + "\""
                + ",\n\"hardwareBacked\":" + hardwareBacked
                + ",\n\"strongBoxBacked\":" + strongBoxBacked
                + ",\n\"corporateIdentity\":false"
                + ",\n\"identityLabel\":\"" + IDENTITY_LABEL + "\""
                + ",\n\"payloadSha256\":\"" + payloadSha256 + "\""
                + ",\n\"publicKeyBase64\":\"" + publicKeyBase64 + "\""
                + ",\n\"signatureBase64\":\"" + signatureBase64 + "\""
                + ",\n\"selfVerified\":" + verified
                + ",\n\"reason\":\"" + escape(reason) + "\"\n}";
    }

    private static Signature signatureEngine() throws Exception {
        try { return Signature.getInstance("Ed25519"); }
        catch (Exception first) { return Signature.getInstance("EdDSA"); }
    }

    private static KeyFactory keyFactory() throws Exception {
        try { return KeyFactory.getInstance("Ed25519"); }
        catch (Exception first) { return KeyFactory.getInstance("EdDSA"); }
    }

    static String encodeBase64(byte[] bytes) {
        if (bytes == null || bytes.length == 0) return "";
        StringBuilder out = new StringBuilder(((bytes.length + 2) / 3) * 4);
        for (int i = 0; i < bytes.length; i += 3) {
            int a = bytes[i] & 0xff;
            int b = i + 1 < bytes.length ? bytes[i + 1] & 0xff : 0;
            int c = i + 2 < bytes.length ? bytes[i + 2] & 0xff : 0;
            out.append(BASE64[a >>> 2]);
            out.append(BASE64[((a & 3) << 4) | (b >>> 4)]);
            out.append(i + 1 < bytes.length ? BASE64[((b & 15) << 2) | (c >>> 6)] : '=');
            out.append(i + 2 < bytes.length ? BASE64[c & 63] : '=');
        }
        return out.toString();
    }

    static byte[] decodeBase64(String value) {
        String clean = value == null ? "" : value.replaceAll("\\s+", "");
        if (clean.isEmpty()) return new byte[0];
        if ((clean.length() & 3) != 0) throw new IllegalArgumentException("invalid base64 length");
        int padding = clean.endsWith("==") ? 2 : clean.endsWith("=") ? 1 : 0;
        byte[] out = new byte[clean.length() / 4 * 3 - padding];
        int output = 0;
        for (int i = 0; i < clean.length(); i += 4) {
            int a = base64Value(clean.charAt(i));
            int b = base64Value(clean.charAt(i + 1));
            int c = clean.charAt(i + 2) == '=' ? 0 : base64Value(clean.charAt(i + 2));
            int d = clean.charAt(i + 3) == '=' ? 0 : base64Value(clean.charAt(i + 3));
            int combined = (a << 18) | (b << 12) | (c << 6) | d;
            if (output < out.length) out[output++] = (byte) (combined >>> 16);
            if (output < out.length) out[output++] = (byte) (combined >>> 8);
            if (output < out.length) out[output++] = (byte) combined;
        }
        return out;
    }

    private static int base64Value(char value) {
        if (value >= 'A' && value <= 'Z') return value - 'A';
        if (value >= 'a' && value <= 'z') return value - 'a' + 26;
        if (value >= '0' && value <= '9') return value - '0' + 52;
        if (value == '+') return 62;
        if (value == '/') return 63;
        throw new IllegalArgumentException("invalid base64 character");
    }

    private static String stringField(String json, String key) {
        Matcher matcher = Pattern.compile("\\\"" + Pattern.quote(key)
                + "\\\"\\s*:\\s*\\\"([^\\\"]*)\\\"").matcher(json);
        return matcher.find() ? matcher.group(1) : "";
    }

    private static boolean booleanField(String json, String key, boolean fallback) {
        Matcher matcher = Pattern.compile("\\\"" + Pattern.quote(key)
                + "\\\"\\s*:\\s*(true|false)").matcher(json);
        return matcher.find() ? Boolean.parseBoolean(matcher.group(1)) : fallback;
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

    private static String clean(String value, String fallback) {
        return value == null || value.trim().isEmpty() ? fallback : value.trim();
    }

    private static String escape(String value) {
        return value == null ? "" : value.replace("\\", "\\\\").replace("\"", "\\\"")
                .replace("\n", "\\n").replace("\r", "\\r");
    }

    public static final class Result {
        public final boolean verified;
        public final String keyOrigin;
        public final boolean hardwareBacked, strongBoxBacked;
        public final String payloadSha256, publicKeyBase64, signatureBase64;
        public final String canonicalJson, reason;

        Result(boolean verified, String keyOrigin, boolean hardwareBacked,
               boolean strongBoxBacked, String payloadSha256, String publicKeyBase64,
               String signatureBase64, String canonicalJson, String reason) {
            this.verified = verified; this.keyOrigin = keyOrigin;
            this.hardwareBacked = hardwareBacked; this.strongBoxBacked = strongBoxBacked;
            this.payloadSha256 = payloadSha256; this.publicKeyBase64 = publicKeyBase64;
            this.signatureBase64 = signatureBase64; this.canonicalJson = canonicalJson;
            this.reason = reason;
        }

        public String summary() {
            return "Firma local " + (verified ? "VERIFIED" : "UNAVAILABLE")
                    + " · " + keyOrigin + " · identidad corporativa=false";
        }
    }

    public static final class Verification {
        public final boolean valid;
        public final String keyOrigin;
        public final boolean hardwareBacked, strongBoxBacked;
        public final String payloadSha256;
        public final List<String> issues;

        Verification(boolean valid, String keyOrigin, boolean hardwareBacked,
                     boolean strongBoxBacked, String payloadSha256, List<String> issues) {
            this.valid = valid; this.keyOrigin = keyOrigin;
            this.hardwareBacked = hardwareBacked; this.strongBoxBacked = strongBoxBacked;
            this.payloadSha256 = payloadSha256;
            this.issues = Collections.unmodifiableList(new ArrayList<String>(issues));
        }

        public String canonicalJson() {
            StringBuilder json = new StringBuilder(300);
            json.append("{\n\"schema\":\"skm-local-evidence-verification/1\"")
                    .append(",\n\"valid\":").append(valid)
                    .append(",\n\"keyOrigin\":\"").append(escape(keyOrigin)).append("\"")
                    .append(",\n\"hardwareBacked\":").append(hardwareBacked)
                    .append(",\n\"strongBoxBacked\":").append(strongBoxBacked)
                    .append(",\n\"corporateIdentity\":false")
                    .append(",\n\"identityLabel\":\"").append(IDENTITY_LABEL).append("\"")
                    .append(",\n\"payloadSha256\":\"").append(payloadSha256).append("\"")
                    .append(",\n\"issues\":[");
            for (int i = 0; i < issues.size(); i++) {
                if (i > 0) json.append(',');
                json.append('\"').append(escape(issues.get(i))).append('\"');
            }
            return json.append("]\n}").toString();
        }
    }
}

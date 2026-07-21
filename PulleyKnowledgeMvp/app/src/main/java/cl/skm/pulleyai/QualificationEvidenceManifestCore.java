package cl.skm.pulleyai;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

/** Canonical, order-independent evidence manifest with deterministic SHA-256 fingerprint. */
public final class QualificationEvidenceManifestCore {
    private QualificationEvidenceManifestCore() {}

    public static Manifest create(String version, List<Entry> source) {
        List<Entry> entries = new ArrayList<Entry>();
        if (source != null) for (Entry entry : source) if (entry != null && entry.valid()) entries.add(entry);
        Collections.sort(entries, new Comparator<Entry>() {
            @Override public int compare(Entry a, Entry b) {
                int type = a.type.compareTo(b.type);
                if (type != 0) return type;
                int id = a.id.compareTo(b.id);
                return id != 0 ? id : a.status.compareTo(b.status);
            }
        });
        String canonical = canonical(version, entries);
        return new Manifest(version == null ? "" : version, entries, canonical, sha256(canonical));
    }

    private static String canonical(String version, List<Entry> entries) {
        StringBuilder out = new StringBuilder();
        out.append("schema=skm-qualification-evidence/1\nversion=")
                .append(escape(version == null ? "" : version)).append('\n');
        for (Entry entry : entries) {
            out.append(escape(entry.type)).append('|').append(escape(entry.id)).append('|')
                    .append(escape(entry.status)).append('|').append(escape(entry.sha256)).append('|')
                    .append(escape(entry.source)).append('\n');
        }
        return out.toString();
    }

    private static String escape(String value) {
        return value.replace("\\", "\\\\").replace("|", "\\|").replace("\n", "\\n");
    }

    private static String sha256(String value) {
        try {
            byte[] hash = MessageDigest.getInstance("SHA-256")
                    .digest(value.getBytes(StandardCharsets.UTF_8));
            StringBuilder out = new StringBuilder();
            for (byte b : hash) out.append(String.format(java.util.Locale.ROOT, "%02x", b & 0xff));
            return out.toString();
        } catch (Exception error) {
            throw new IllegalStateException("SHA-256 unavailable", error);
        }
    }

    public static final class Entry {
        public final String type, id, status, sha256, source;
        public Entry(String type, String id, String status, String sha256, String source) {
            this.type = type == null ? "" : type;
            this.id = id == null ? "" : id;
            this.status = status == null ? "" : status;
            this.sha256 = sha256 == null ? "" : sha256.toLowerCase(java.util.Locale.ROOT);
            this.source = source == null ? "" : source;
        }
        boolean valid() {
            return !type.trim().isEmpty() && !id.trim().isEmpty() && !status.trim().isEmpty()
                    && (sha256.isEmpty() || sha256.matches("[0-9a-f]{64}"));
        }
    }

    public static final class Manifest {
        public final String version, canonicalText, fingerprint;
        public final List<Entry> entries;
        Manifest(String version, List<Entry> entries, String canonicalText, String fingerprint) {
            this.version = version;
            this.entries = Collections.unmodifiableList(new ArrayList<Entry>(entries));
            this.canonicalText = canonicalText;
            this.fingerprint = fingerprint;
        }
    }
}

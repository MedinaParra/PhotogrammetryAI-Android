package cl.ingenieria.photogrammetryai.core.materialhistory;

import cl.ingenieria.photogrammetryai.core.materialhistory.MaterialPulleyKnowledgeBase.DimensionEvidence;
import cl.ingenieria.photogrammetryai.core.materialhistory.MaterialPulleyKnowledgeBase.DimensionKind;
import cl.ingenieria.photogrammetryai.core.materialhistory.MaterialPulleyKnowledgeBase.InterventionEvent;
import cl.ingenieria.photogrammetryai.core.materialhistory.MaterialPulleyKnowledgeBase.MaterialFamily;
import cl.ingenieria.photogrammetryai.core.materialhistory.MaterialPulleyKnowledgeBase.SourceDocument;
import cl.ingenieria.photogrammetryai.core.materialhistory.QualityReportTextParser.ParsedReport;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

/** Converts one quality report into source-traceable domain facts and merges them atomically. */
public final class QualityKnowledgeIngestionService {
    public static final class ReportInput {
        private final String title;
        private final String uri;
        private final String text;
        private final String documentDate;

        public ReportInput(String title, String uri, String text, String documentDate) {
            if (title == null || title.trim().isEmpty()) {
                throw new IllegalArgumentException("title must not be blank");
            }
            if (text == null || text.trim().isEmpty()) {
                throw new IllegalArgumentException("text must not be blank");
            }
            this.title = title.trim();
            this.text = text;
            this.uri = uri == null || uri.trim().isEmpty() ? "" : uri.trim();
            this.documentDate = documentDate == null ? "" : documentDate.trim();
        }

        public String title() { return title; }
        public String uri() { return uri; }
        public String text() { return text; }
        public String documentDate() { return documentDate; }
    }

    public static final class Result {
        private final boolean accepted;
        private final String sourceId;
        private final String materialCode;
        private final String ot;
        private final List<String> warnings;

        private Result(
                boolean accepted,
                String sourceId,
                String materialCode,
                String ot,
                List<String> warnings
        ) {
            this.accepted = accepted;
            this.sourceId = sourceId;
            this.materialCode = materialCode;
            this.ot = ot;
            this.warnings = Collections.unmodifiableList(new ArrayList<>(warnings));
        }

        public boolean accepted() { return accepted; }
        public String sourceId() { return sourceId; }
        public Optional<String> materialCode() { return Optional.ofNullable(materialCode); }
        public Optional<String> ot() { return Optional.ofNullable(ot); }
        public List<String> warnings() { return warnings; }
    }

    private final MaterialKnowledgeStore store;
    private final QualityReportTextParser parser;

    public QualityKnowledgeIngestionService(
            MaterialKnowledgeStore store,
            QualityReportTextParser parser
    ) {
        this.store = Objects.requireNonNull(store, "store");
        this.parser = Objects.requireNonNull(parser, "parser");
    }

    public Result ingest(ReportInput input) {
        Objects.requireNonNull(input, "input");
        ParsedReport parsed = parser.parse(input.title(), input.text());
        List<String> warnings = new ArrayList<>(parsed.warnings());
        String sourceId = "quality-sha256-" + sha256(input.title() + "\n" + input.text());
        if (!parsed.materialCode().isPresent()) {
            warnings.add("Report was not ingested because no material/SAP/stock code was found");
            return new Result(false, sourceId, null, parsed.ot().orElse(null), warnings);
        }

        String code = parsed.materialCode().get();
        MaterialFamily.Builder builder = MaterialFamily.builder(code);
        parsed.component().ifPresent(builder::alias);
        parsed.client().ifPresent(builder::client);
        for (String role : inferRoleHints(parsed.component().orElse(""))) builder.roleHint(role);

        String sourceUri = input.uri().isEmpty()
                ? "local://quality/" + sourceId
                : input.uri();
        builder.source(new SourceDocument(
                sourceId,
                input.title(),
                sourceUri,
                parsed.phase(),
                parsed.ot().orElse(null),
                code,
                input.documentDate()
        ));

        for (Map.Entry<DimensionKind, List<Double>> entry : parsed.dimensionsMm().entrySet()) {
            for (double value : entry.getValue()) {
                builder.dimension(new DimensionEvidence(
                        entry.getKey(),
                        value,
                        semanticLabel(entry.getKey()),
                        sourceId,
                        0.90
                ));
            }
        }

        if (parsed.ot().isPresent() && parsed.year().isPresent()) {
            Set<String> sourceIds = new LinkedHashSet<>();
            sourceIds.add(sourceId);
            builder.event(new InterventionEvent(
                    parsed.ot().get(),
                    parsed.year().get(),
                    parsed.phase(),
                    parsed.client().orElse(""),
                    parsed.component().orElse(""),
                    parsed.purchaseOrder().orElse(""),
                    sourceIds,
                    warnings
            ));
        } else if (parsed.ot().isPresent()) {
            warnings.add("OT was linked through the source, but intervention year was not explicit");
        }

        store.upsertFamily(builder.build());
        return new Result(true, sourceId, code, parsed.ot().orElse(null), warnings);
    }

    private static Set<String> inferRoleHints(String component) {
        String value = component.toUpperCase(Locale.ROOT);
        Set<String> result = new LinkedHashSet<>();
        if (value.contains("MOTRIZ") || value.contains("DRIVE")) result.add("MOTRIZ");
        if (value.contains("COLA") || value.contains("TAIL")) result.add("COLA");
        if (value.contains("DEFLECT") || value.contains("BEND")) result.add("DEFLECTORA");
        if (value.contains("TENSOR") || value.contains("TAKE-UP")) result.add("TENSORA");
        if (value.contains("TRIPPER")) result.add("TRIPPER");
        return result;
    }

    private static String semanticLabel(DimensionKind kind) {
        switch (kind) {
            case SHELL_LENGTH: return "largo de manto";
            case SHELL_DIAMETER: return "diámetro de manto";
            case SHELL_THICKNESS: return "espesor de manto";
            case LAGGING_THICKNESS: return "espesor de revestimiento";
            case BEARING_CENTRE_DISTANCE: return "distancia entre rodamientos";
            case SUPPORT_CENTRE_DISTANCE: return "distancia entre soportes";
            case SHAFT_TOTAL_LENGTH: return "largo total de eje";
            case SUPPORT_BORE_DIAMETER: return "diámetro de alojamiento de soporte";
            default: return kind.name().toLowerCase(Locale.ROOT);
        }
    }

    private static String sha256(String value) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(value.getBytes(StandardCharsets.UTF_8));
            StringBuilder result = new StringBuilder(hash.length * 2);
            for (byte item : hash) result.append(String.format(Locale.ROOT, "%02x", item & 0xff));
            return result.toString();
        } catch (NoSuchAlgorithmException impossible) {
            throw new IllegalStateException("SHA-256 is unavailable", impossible);
        }
    }
}

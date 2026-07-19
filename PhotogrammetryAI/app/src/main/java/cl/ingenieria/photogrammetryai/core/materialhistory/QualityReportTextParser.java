package cl.ingenieria.photogrammetryai.core.materialhistory;

import cl.ingenieria.photogrammetryai.core.materialhistory.MaterialPulleyKnowledgeBase.DimensionKind;
import cl.ingenieria.photogrammetryai.core.materialhistory.MaterialPulleyKnowledgeBase.ReportPhase;

import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Extracts explicit identifiers and labelled dimensions from quality-report text. */
public final class QualityReportTextParser {
    private static final Pattern MATERIAL_CODE = Pattern.compile(
            "(?im)(?:c[oó]digo\\s+(?:de\\s+)?material|stock\\s+code|c[oó]digo\\s+sap|\\bSC)"
                    + "\\s*[:#-]?\\s*([A-Z0-9][A-Z0-9._/-]{2,})"
    );
    private static final Pattern OT_INTERNAL = Pattern.compile(
            "(?im)\\bOT\\s*(?:interna)?\\s*[:#-]?\\s*((?:20\\d{2}\\s*[-–]\\s*)?\\d{2,6})"
    );
    private static final Pattern OT_TITLE = Pattern.compile(
            "(?i)(?:\\(|\\b)OT[ _-]*([0-9]{2,6})(?:\\)|\\b)"
    );
    private static final Pattern CLIENT = Pattern.compile(
            "(?im)^\\s*Cliente\\s*:\\s*(.+?)\\s*$"
    );
    private static final Pattern COMPONENT = Pattern.compile(
            "(?im)^\\s*Componente\\s*:\\s*(.+?)\\s*$"
    );
    private static final Pattern PURCHASE_ORDER = Pattern.compile(
            "(?im)(?:N[°º]\\s+de\\s+Orden\\s+de\\s+compra|Orden\\s+de\\s+Compra|OC\\s+MEL|\\bOC)"
                    + "\\s*[:#-]?\\s*([0-9][0-9-]{5,})"
    );

    private static final Map<DimensionKind, List<Pattern>> DIMENSION_PATTERNS;
    static {
        Map<DimensionKind, List<Pattern>> patterns = new EnumMap<>(DimensionKind.class);
        patterns.put(DimensionKind.SHELL_LENGTH, patterns(
                "Largo\\s+del\\s+Manto(?:\\s+[A-Z])?\\s*[:=]?\\s*([0-9.,]+)\\s*mm",
                "Largo\\s+Manto\\s*[:=]?\\s*([0-9.,]+)\\s*mm"
        ));
        patterns.put(DimensionKind.SHELL_DIAMETER, patterns(
                "Di[aá]metro\\s+del\\s+Manto(?:\\s+[A-Z])?\\s*[:=]?\\s*([0-9.,]+)\\s*mm",
                "Di[aá]metro\\s+Manto\\s*[:=]?\\s*([0-9.,]+)\\s*mm"
        ));
        patterns.put(DimensionKind.SHELL_THICKNESS, patterns(
                "Espesor\\s+del\\s+Manto(?:\\s+[A-Z])?\\s*[:=]?\\s*([0-9.,]+)\\s*mm",
                "(?:medici[oó]n\\s+de\\s+espesores|espesores\\s+del\\s+manto)"
                        + "[^\\n]{0,120}?([0-9.,]+)\\s*mm"
        ));
        patterns.put(DimensionKind.LAGGING_THICKNESS, patterns(
                "Espesor\\s+del\\s+Revestimiento(?:\\s+[A-Z])?\\s*[:=]?\\s*([0-9.,]+)\\s*mm"
        ));
        patterns.put(DimensionKind.BEARING_CENTRE_DISTANCE, patterns(
                "Distancia\\s+entre\\s+rodamientos\\s*[:=]?\\s*([0-9.,]+)\\s*mm"
        ));
        patterns.put(DimensionKind.SUPPORT_CENTRE_DISTANCE, patterns(
                "Distancia\\s+entre\\s+soportes\\s*[:=]?\\s*([0-9.,]+)\\s*mm"
        ));
        patterns.put(DimensionKind.SHAFT_TOTAL_LENGTH, patterns(
                "Largo\\s+(?:total\\s+)?de\\s+eje\\s*[:=]?\\s*([0-9.,]+)\\s*mm",
                "L10\\s*([0-9.,]+)\\s*mm"
        ));
        patterns.put(DimensionKind.SUPPORT_BORE_DIAMETER, patterns(
                "Di[aá]metro\\s+Nominal[^\\n]{0,60}?([0-9.,]+)\\s*mm\\s+H7"
        ));
        DIMENSION_PATTERNS = Collections.unmodifiableMap(patterns);
    }

    public static final class ParsedReport {
        private final String sourceTitle;
        private final String materialCode;
        private final String ot;
        private final Integer year;
        private final String client;
        private final String component;
        private final String purchaseOrder;
        private final ReportPhase phase;
        private final Map<DimensionKind, List<Double>> dimensionsMm;
        private final List<String> warnings;

        private ParsedReport(
                String sourceTitle,
                String materialCode,
                String ot,
                Integer year,
                String client,
                String component,
                String purchaseOrder,
                ReportPhase phase,
                Map<DimensionKind, List<Double>> dimensionsMm,
                List<String> warnings
        ) {
            this.sourceTitle = sourceTitle;
            this.materialCode = materialCode;
            this.ot = ot;
            this.year = year;
            this.client = client;
            this.component = component;
            this.purchaseOrder = purchaseOrder;
            this.phase = phase;
            Map<DimensionKind, List<Double>> copy = new EnumMap<>(DimensionKind.class);
            for (Map.Entry<DimensionKind, List<Double>> entry : dimensionsMm.entrySet()) {
                copy.put(entry.getKey(), Collections.unmodifiableList(new ArrayList<>(entry.getValue())));
            }
            this.dimensionsMm = Collections.unmodifiableMap(copy);
            this.warnings = Collections.unmodifiableList(new ArrayList<>(warnings));
        }

        public String sourceTitle() { return sourceTitle; }
        public Optional<String> materialCode() { return Optional.ofNullable(materialCode); }
        public Optional<String> ot() { return Optional.ofNullable(ot); }
        public Optional<Integer> year() { return Optional.ofNullable(year); }
        public Optional<String> client() { return Optional.ofNullable(client); }
        public Optional<String> component() { return Optional.ofNullable(component); }
        public Optional<String> purchaseOrder() { return Optional.ofNullable(purchaseOrder); }
        public ReportPhase phase() { return phase; }
        public Map<DimensionKind, List<Double>> dimensionsMm() { return dimensionsMm; }
        public List<String> warnings() { return warnings; }

        public Optional<Double> firstDimensionMm(DimensionKind kind) {
            List<Double> values = dimensionsMm.get(kind);
            return values == null || values.isEmpty()
                    ? Optional.empty()
                    : Optional.of(values.get(0));
        }
    }

    public ParsedReport parse(String sourceTitle, String text) {
        String title = sourceTitle == null ? "" : sourceTitle.trim();
        String content = Objects.requireNonNull(text, "text");
        String combined = title + "\n" + content;
        List<String> warnings = new ArrayList<>();

        String materialCode = matchFirst(MATERIAL_CODE, combined);
        if (materialCode != null) {
            materialCode = trimIdentifier(materialCode);
            try {
                materialCode = MaterialPulleyKnowledgeBase.normalizeMaterialCode(materialCode);
            } catch (IllegalArgumentException invalid) {
                warnings.add("Material code could not be normalised: " + materialCode);
                materialCode = null;
            }
        }

        String rawOt = matchFirst(OT_INTERNAL, combined);
        if (rawOt == null) rawOt = matchFirst(OT_TITLE, title);
        String ot = null;
        Integer year = null;
        if (rawOt != null) {
            try {
                ot = MaterialPulleyKnowledgeBase.normalizeOt(rawOt);
                year = extractYear(rawOt, content);
            } catch (IllegalArgumentException invalid) {
                warnings.add("OT could not be normalised: " + rawOt);
            }
        }

        String client = cleanLine(matchFirst(CLIENT, content));
        String component = cleanLine(matchFirst(COMPONENT, content));
        String purchaseOrder = trimIdentifier(matchFirst(PURCHASE_ORDER, combined));
        ReportPhase phase = detectPhase(title, content);

        Map<DimensionKind, List<Double>> dimensions = new EnumMap<>(DimensionKind.class);
        for (Map.Entry<DimensionKind, List<Pattern>> entry : DIMENSION_PATTERNS.entrySet()) {
            List<Double> values = new ArrayList<>();
            for (Pattern pattern : entry.getValue()) {
                Matcher matcher = pattern.matcher(content);
                while (matcher.find()) {
                    try {
                        double value = parseMillimetres(matcher.group(1));
                        if (!containsNear(values, value, 0.01)) values.add(value);
                    } catch (IllegalArgumentException invalid) {
                        warnings.add(
                                "Invalid " + entry.getKey() + " value: " + matcher.group(1)
                        );
                    }
                }
            }
            if (!values.isEmpty()) dimensions.put(entry.getKey(), values);
        }

        if (materialCode == null) warnings.add("No material/stock/SAP code found");
        if (ot == null) warnings.add("No OT found");
        if (!dimensions.containsKey(DimensionKind.SHELL_LENGTH)) {
            warnings.add("No explicit labelled shell length found");
        }

        return new ParsedReport(
                title,
                materialCode,
                ot,
                year,
                client,
                component,
                purchaseOrder,
                phase,
                dimensions,
                warnings
        );
    }

    public static double parseMillimetres(String raw) {
        if (raw == null) throw new IllegalArgumentException("number is null");
        String value = raw.trim().replace(" ", "");
        if (value.isEmpty()) throw new IllegalArgumentException("number is blank");
        if (value.contains(",")) {
            value = value.replace(".", "").replace(',', '.');
        } else if (value.matches("[0-9]{1,3}\\.[0-9]{3}")) {
            value = value.replace(".", "");
        }
        double parsed;
        try {
            parsed = Double.parseDouble(value);
        } catch (NumberFormatException invalid) {
            throw new IllegalArgumentException("invalid number: " + raw, invalid);
        }
        if (!Double.isFinite(parsed) || parsed <= 0.0) {
            throw new IllegalArgumentException("number must be positive and finite: " + raw);
        }
        return parsed;
    }

    private static ReportPhase detectPhase(String title, String content) {
        String titleUpper = title.toUpperCase(Locale.ROOT);
        String value = (title + "\n" + firstLines(content, 24)).toUpperCase(Locale.ROOT);
        if (titleUpper.contains("WIP")) return ReportPhase.WIP;
        if (titleUpper.contains("PLANO") || titleUpper.endsWith(".SLDDRW")) {
            return ReportPhase.DRAWING;
        }
        if (value.contains("INFORME DE HALLAZGO")) return ReportPhase.FINDINGS;
        if (value.contains("INFORME DE EVALUACI") || value.contains("EVALUACIÓN X")) {
            return ReportPhase.EVALUATION;
        }
        if (value.contains("INFORME DE RECEPCI")) return ReportPhase.RECEIPT;
        if (value.contains("PRESERVACIÓN X") || value.contains("PRESERVACION X")) {
            return ReportPhase.PRESERVATION;
        }
        if (value.contains("INFORME FINAL") || titleUpper.contains("ARMADO")) {
            return ReportPhase.ASSEMBLY;
        }
        if (value.contains("REPARACIÓN X") || value.contains("REPARACION X")) {
            return ReportPhase.REPAIR;
        }
        return ReportPhase.OTHER;
    }

    private static String firstLines(String content, int maximumLines) {
        String[] lines = content.split("\\R");
        StringBuilder builder = new StringBuilder();
        int count = Math.min(lines.length, maximumLines);
        for (int index = 0; index < count; index++) {
            builder.append(lines[index]).append('\n');
        }
        return builder.toString();
    }

    private static Integer extractYear(String rawOt, String content) {
        Matcher direct = Pattern.compile("\\b(20[0-9]{2})\\s*[-–]").matcher(rawOt);
        if (direct.find()) return Integer.parseInt(direct.group(1));
        Matcher internal = Pattern.compile(
                "(?im)OT\\s*Interna\\s*[:#-]?\\s*(20[0-9]{2})\\s*[-–]"
        ).matcher(content);
        if (internal.find()) return Integer.parseInt(internal.group(1));
        return null;
    }

    private static List<Pattern> patterns(String... expressions) {
        List<Pattern> result = new ArrayList<>();
        for (String expression : expressions) {
            result.add(Pattern.compile(expression, Pattern.CASE_INSENSITIVE | Pattern.MULTILINE));
        }
        return Collections.unmodifiableList(result);
    }

    private static String matchFirst(Pattern pattern, String value) {
        Matcher matcher = pattern.matcher(value);
        return matcher.find() ? matcher.group(1).trim() : null;
    }

    private static String cleanLine(String value) {
        if (value == null) return null;
        String cleaned = value.replaceAll("\\s+", " ").trim();
        return cleaned.isEmpty() ? null : cleaned;
    }

    private static String trimIdentifier(String value) {
        if (value == null) return null;
        String cleaned = value.trim().replaceAll("[.,;:]+$", "");
        return cleaned.isEmpty() ? null : cleaned;
    }

    private static boolean containsNear(List<Double> values, double candidate, double tolerance) {
        for (double value : values) {
            if (Math.abs(value - candidate) <= tolerance) return true;
        }
        return false;
    }
}

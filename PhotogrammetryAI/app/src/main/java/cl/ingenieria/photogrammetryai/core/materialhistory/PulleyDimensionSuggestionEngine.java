package cl.ingenieria.photogrammetryai.core.materialhistory;

import cl.ingenieria.photogrammetryai.core.materialhistory.MaterialPulleyKnowledgeBase.DimensionEvidence;
import cl.ingenieria.photogrammetryai.core.materialhistory.MaterialPulleyKnowledgeBase.DimensionKind;
import cl.ingenieria.photogrammetryai.core.materialhistory.MaterialPulleyKnowledgeBase.MaterialFamily;
import cl.ingenieria.photogrammetryai.core.materialhistory.PulleyDimensionReview.Origin;
import cl.ingenieria.photogrammetryai.core.materialhistory.PulleyDimensionReview.Priority;
import cl.ingenieria.photogrammetryai.core.materialhistory.PulleyDimensionReview.Suggestion;
import cl.ingenieria.photogrammetryai.core.materialhistory.PulleyMaterialIdentificationEngine.Action;
import cl.ingenieria.photogrammetryai.core.materialhistory.PulleyMaterialIdentificationEngine.Query;

import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/** Creates ordered, explainable dimensional questions after a pulley family has been identified. */
public final class PulleyDimensionSuggestionEngine {
    public static final class Observation {
        private final double valueMm;
        private final double confidence;
        private final String source;

        public Observation(double valueMm, double confidence, String source) {
            if (!Double.isFinite(valueMm) || valueMm <= 0.0) {
                throw new IllegalArgumentException("valueMm must be positive and finite");
            }
            if (!Double.isFinite(confidence) || confidence < 0.0 || confidence > 1.0) {
                throw new IllegalArgumentException("confidence must be between 0 and 1");
            }
            this.valueMm = valueMm;
            this.confidence = confidence;
            this.source = source == null ? "" : source.trim();
        }

        public double valueMm() { return valueMm; }
        public double confidence() { return confidence; }
        public String source() { return source; }
    }

    private static final DimensionKind[] ORDER = {
            DimensionKind.SHELL_DIAMETER,
            DimensionKind.SUPPORT_CENTRE_DISTANCE,
            DimensionKind.BEARING_CENTRE_DISTANCE,
            DimensionKind.SHAFT_TOTAL_LENGTH,
            DimensionKind.SHAFT_BEARING_DIAMETER,
            DimensionKind.SHAFT_LOCKING_DIAMETER,
            DimensionKind.SUPPORT_BORE_DIAMETER,
            DimensionKind.LAGGING_THICKNESS,
            DimensionKind.SHELL_THICKNESS
    };

    public PulleyDimensionReview generate(
            String sessionId,
            MaterialFamily family,
            Action identificationAction,
            Query query,
            Map<DimensionKind, Observation> observations,
            long createdAtEpochMs
    ) {
        Objects.requireNonNull(family, "family");
        Objects.requireNonNull(identificationAction, "identificationAction");
        Objects.requireNonNull(query, "query");
        Map<DimensionKind, Observation> observed = new EnumMap<>(DimensionKind.class);
        if (observations != null) observed.putAll(observations);
        if (query.shellDiameterMm().isPresent()
                && !observed.containsKey(DimensionKind.SHELL_DIAMETER)) {
            observed.put(
                    DimensionKind.SHELL_DIAMETER,
                    new Observation(query.shellDiameterMm().get(), 0.80, "estimación del escaneo")
            );
        }

        List<Suggestion> suggestions = new ArrayList<>();
        Optional<Double> historicalLength = family.consensusDimensionMm(DimensionKind.SHELL_LENGTH);
        suggestions.add(Suggestion.confirmedInput(
                DimensionKind.SHELL_LENGTH,
                "Largo del manto",
                query.shellLengthMm(),
                historicalLength.orElse(null),
                toleranceMm(DimensionKind.SHELL_LENGTH, query.shellLengthMm()),
                historicalSummary(family, DimensionKind.SHELL_LENGTH),
                createdAtEpochMs
        ));

        for (DimensionKind kind : ORDER) {
            Optional<Double> historical = family.consensusDimensionMm(kind);
            Observation scan = observed.get(kind);
            if (!historical.isPresent() && scan == null) continue;

            double recommended;
            Origin origin;
            double confidence;
            String sourceSummary;
            Double historicalValue = historical.orElse(null);
            Double scanValue = scan == null ? null : scan.valueMm();

            if (historical.isPresent() && scan != null) {
                double tolerance = toleranceMm(kind, historical.get());
                double difference = Math.abs(historical.get() - scan.valueMm());
                double historyConfidence = historicalConfidence(family, kind);
                if (difference <= tolerance) {
                    double total = Math.max(0.01, historyConfidence) + Math.max(0.01, scan.confidence());
                    recommended = (
                            historical.get() * Math.max(0.01, historyConfidence)
                                    + scan.valueMm() * Math.max(0.01, scan.confidence())
                    ) / total;
                    origin = Origin.FUSED_HISTORY_AND_SCAN;
                    confidence = clamp01((historyConfidence + scan.confidence()) / 2.0 + 0.10);
                    sourceSummary = historicalSummary(family, kind)
                            + "; " + safeSource(scan.source())
                            + "; diferencia " + formatMm(difference);
                } else {
                    recommended = historical.get();
                    origin = Origin.HISTORICAL_CONSENSUS;
                    confidence = Math.min(historicalConfidence(family, kind), 0.75);
                    sourceSummary = historicalSummary(family, kind)
                            + "; escaneo " + formatMm(scan.valueMm())
                            + " difiere " + formatMm(difference)
                            + ": requiere verificación manual";
                }
            } else if (historical.isPresent()) {
                recommended = historical.get();
                origin = Origin.HISTORICAL_CONSENSUS;
                confidence = historicalConfidence(family, kind);
                sourceSummary = historicalSummary(family, kind);
            } else {
                recommended = scan.valueMm();
                origin = Origin.SCAN_ESTIMATE;
                confidence = scan.confidence();
                sourceSummary = safeSource(scan.source());
            }

            suggestions.add(Suggestion.pending(
                    kind,
                    label(kind),
                    recommended,
                    historicalValue,
                    scanValue,
                    toleranceMm(kind, recommended),
                    confidence,
                    priority(kind),
                    origin,
                    sourceSummary
            ));
        }

        return new PulleyDimensionReview(
                sessionId,
                family.materialCode(),
                identificationAction,
                createdAtEpochMs,
                suggestions
        );
    }

    public PulleyDimensionReview generate(
            String sessionId,
            MaterialFamily family,
            Action identificationAction,
            Query query,
            long createdAtEpochMs
    ) {
        return generate(
                sessionId,
                family,
                identificationAction,
                query,
                Collections.emptyMap(),
                createdAtEpochMs
        );
    }

    private static String historicalSummary(MaterialFamily family, DimensionKind kind) {
        List<DimensionEvidence> evidence = family.dimensions(kind);
        if (evidence.isEmpty()) return "sin historial consolidado";
        return evidence.size() + " evidencia(s) histórica(s); OTs " + family.ots();
    }

    private static double historicalConfidence(MaterialFamily family, DimensionKind kind) {
        List<DimensionEvidence> evidence = family.dimensions(kind);
        if (evidence.isEmpty()) return 0.0;
        double weighted = 0.0;
        double weight = 0.0;
        for (DimensionEvidence item : evidence) {
            double w = Math.max(0.10, item.confidence());
            weighted += item.confidence() * w;
            weight += w;
        }
        return clamp01(weighted / Math.max(0.01, weight));
    }

    public static double toleranceMm(DimensionKind kind, double valueMm) {
        switch (kind) {
            case SHELL_LENGTH:
                return Math.max(10.0, valueMm * 0.010);
            case SHELL_DIAMETER:
                return Math.max(10.0, valueMm * 0.015);
            case SUPPORT_CENTRE_DISTANCE:
            case BEARING_CENTRE_DISTANCE:
                return Math.max(10.0, valueMm * 0.010);
            case SHAFT_TOTAL_LENGTH:
                return Math.max(15.0, valueMm * 0.010);
            case SHAFT_BEARING_DIAMETER:
            case SHAFT_LOCKING_DIAMETER:
            case SUPPORT_BORE_DIAMETER:
                return Math.max(2.0, valueMm * 0.005);
            case SHELL_THICKNESS:
            case LAGGING_THICKNESS:
                return Math.max(2.0, valueMm * 0.080);
            default:
                return Math.max(5.0, valueMm * 0.015);
        }
    }

    private static Priority priority(DimensionKind kind) {
        switch (kind) {
            case SHELL_LENGTH:
            case SHELL_DIAMETER:
            case SUPPORT_CENTRE_DISTANCE:
            case BEARING_CENTRE_DISTANCE:
                return Priority.CRITICAL;
            case SHAFT_TOTAL_LENGTH:
            case SHAFT_BEARING_DIAMETER:
            case SHAFT_LOCKING_DIAMETER:
            case SUPPORT_BORE_DIAMETER:
                return Priority.IMPORTANT;
            default:
                return Priority.OPTIONAL;
        }
    }

    private static String label(DimensionKind kind) {
        switch (kind) {
            case SHELL_LENGTH: return "Largo del manto";
            case SHELL_DIAMETER: return "Diámetro del manto";
            case SUPPORT_CENTRE_DISTANCE: return "Distancia entre centros de soportes";
            case BEARING_CENTRE_DISTANCE: return "Distancia entre centros de rodamientos";
            case SHAFT_TOTAL_LENGTH: return "Largo total del eje";
            case SHAFT_BEARING_DIAMETER: return "Diámetro del eje en rodamientos";
            case SHAFT_LOCKING_DIAMETER: return "Diámetro del eje en manguitos de expansión";
            case SUPPORT_BORE_DIAMETER: return "Diámetro del alojamiento del soporte";
            case LAGGING_THICKNESS: return "Espesor del revestimiento";
            case SHELL_THICKNESS: return "Espesor del manto";
            default: return kind.name();
        }
    }

    private static String safeSource(String source) {
        return source == null || source.trim().isEmpty() ? "estimación visual" : source.trim();
    }

    private static String formatMm(double value) {
        return String.format(Locale.ROOT, "%.1f mm", value);
    }

    private static double clamp01(double value) {
        return Math.max(0.0, Math.min(1.0, value));
    }
}

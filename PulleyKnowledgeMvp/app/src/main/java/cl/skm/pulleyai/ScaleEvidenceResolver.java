package cl.skm.pulleyai;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

/** Resolves metric scale from traceable observations without silently averaging contradictions. */
public final class ScaleEvidenceResolver {
    public enum Kind {
        CALIBRATED_MARKER(1.00),
        SHELL_LENGTH(0.95),
        MANUAL_DISTANCE(0.90),
        ARCORE_DEPTH(0.65);

        final double authority;
        Kind(double authority) { this.authority = authority; }
    }

    public static final class Evidence {
        public final Kind kind;
        public final double realMillimetres;
        public final double reconstructedUnits;
        public final double confidence;
        public final String source;

        public Evidence(Kind kind, double realMillimetres, double reconstructedUnits,
                        double confidence, String source) {
            if (kind == null) throw new IllegalArgumentException("kind");
            if (!(realMillimetres > 0.0) || !(reconstructedUnits > 0.0)) {
                throw new IllegalArgumentException("Scale dimensions must be positive");
            }
            if (Double.isNaN(confidence) || confidence < 0.0 || confidence > 1.0) {
                throw new IllegalArgumentException("confidence must be 0..1");
            }
            this.kind = kind;
            this.realMillimetres = realMillimetres;
            this.reconstructedUnits = reconstructedUnits;
            this.confidence = confidence;
            this.source = source == null ? "" : source;
        }

        public double millimetresPerUnit() {
            return realMillimetres / reconstructedUnits;
        }

        double weight() {
            return kind.authority * Math.max(0.05, confidence);
        }
    }

    public static final class Resolution {
        public final boolean resolved;
        public final double millimetresPerUnit;
        public final double confidence;
        public final List<Evidence> accepted;
        public final List<Evidence> rejected;
        public final List<String> warnings;

        Resolution(boolean resolved, double millimetresPerUnit, double confidence,
                   List<Evidence> accepted, List<Evidence> rejected, List<String> warnings) {
            this.resolved = resolved;
            this.millimetresPerUnit = millimetresPerUnit;
            this.confidence = confidence;
            this.accepted = Collections.unmodifiableList(new ArrayList<Evidence>(accepted));
            this.rejected = Collections.unmodifiableList(new ArrayList<Evidence>(rejected));
            this.warnings = Collections.unmodifiableList(new ArrayList<String>(warnings));
        }
    }

    private ScaleEvidenceResolver() {
    }

    public static Resolution resolve(List<Evidence> input) {
        List<Evidence> evidence = input == null
                ? new ArrayList<Evidence>()
                : new ArrayList<Evidence>(input);
        List<Evidence> rejected = new ArrayList<Evidence>();
        List<String> warnings = new ArrayList<String>();
        if (evidence.isEmpty()) {
            warnings.add("No existe evidencia métrica para resolver la escala");
            return new Resolution(false, Double.NaN, 0.0, evidence, rejected, warnings);
        }

        Collections.sort(evidence, new Comparator<Evidence>() {
            @Override public int compare(Evidence left, Evidence right) {
                return Double.compare(left.millimetresPerUnit(), right.millimetresPerUnit());
            }
        });
        double median = medianScale(evidence);
        List<Evidence> accepted = new ArrayList<Evidence>();
        for (Evidence item : evidence) {
            double relativeError = Math.abs(item.millimetresPerUnit() - median) / Math.max(1e-9, median);
            double threshold = item.kind == Kind.ARCORE_DEPTH ? 0.12 : 0.055;
            if (relativeError <= threshold || evidence.size() == 1) {
                accepted.add(item);
            } else {
                rejected.add(item);
                warnings.add("Escala contradictoria excluida: " + item.kind + " · " + item.source);
            }
        }

        if (accepted.isEmpty()) {
            warnings.add("Todas las evidencias métricas son contradictorias");
            return new Resolution(false, Double.NaN, 0.0, accepted, rejected, warnings);
        }

        double weightedScale = 0.0;
        double weight = 0.0;
        double authorityProduct = 1.0;
        for (Evidence item : accepted) {
            weightedScale += item.millimetresPerUnit() * item.weight();
            weight += item.weight();
            authorityProduct *= (1.0 - Math.min(0.99, item.weight()));
        }
        double scale = weightedScale / weight;
        double spread = relativeSpread(accepted, scale);
        double confidence = Math.min(0.99, (1.0 - authorityProduct) * Math.max(0.0, 1.0 - spread * 4.0));
        boolean resolved = confidence >= 0.60;
        if (!resolved) warnings.add("La evidencia existe, pero su confianza no alcanza el umbral de escala");
        if (rejected.size() > 0) warnings.add("La escala requiere revisión por evidencia contradictoria");
        return new Resolution(resolved, scale, confidence, accepted, rejected, warnings);
    }

    private static double medianScale(List<Evidence> sorted) {
        int middle = sorted.size() / 2;
        if ((sorted.size() & 1) == 1) return sorted.get(middle).millimetresPerUnit();
        return 0.5 * (sorted.get(middle - 1).millimetresPerUnit()
                + sorted.get(middle).millimetresPerUnit());
    }

    private static double relativeSpread(List<Evidence> evidence, double centre) {
        if (evidence.size() <= 1) return 0.0;
        double weighted = 0.0;
        double weight = 0.0;
        for (Evidence item : evidence) {
            weighted += item.weight() * Math.abs(item.millimetresPerUnit() - centre) / Math.max(1e-9, centre);
            weight += item.weight();
        }
        return weighted / Math.max(1e-9, weight);
    }
}

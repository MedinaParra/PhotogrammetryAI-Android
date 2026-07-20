package cl.skm.pulleyai;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** Conservative limits used before starting reconstruction on a phone. */
public final class IndustrialAnalysisBudgetCore {
    public static final int MAX_FRAMES = 48;
    public static final int MAX_FEATURES = 420;
    public static final int MAX_PAIRS = 240;
    public static final long MIN_HEADROOM = 96L * 1024L * 1024L;

    private IndustrialAnalysisBudgetCore() {}

    public static Result evaluate(int frames, int width, int height, int features,
                                  int pairs, long availableMemory, long freeStorage) {
        List<String> blockers = new ArrayList<String>();
        List<String> warnings = new ArrayList<String>();
        if (frames < 8) blockers.add("Faltan fotogramas para reconstrucción");
        if (frames > MAX_FRAMES) blockers.add("Exceso de fotogramas seleccionados");
        if (width <= 0 || height <= 0) blockers.add("Resolución de análisis inválida");
        if (features <= 0 || features > MAX_FEATURES) blockers.add("Exceso de características por imagen");
        if (pairs < 0 || pairs > MAX_PAIRS) blockers.add("Exceso de pares candidatos");
        long gray = multiply(frames, multiply(width, height));
        long descriptors = multiply(frames, multiply(features, 96L));
        long matches = multiply(pairs, multiply(Math.max(32, features / 2), 80L));
        long geometry = multiply(Math.max(1, pairs), 64L * 1024L);
        long estimated = add(add(gray, descriptors), add(matches, geometry));
        estimated = add(estimated, 24L * 1024L * 1024L);
        long required = add(estimated, MIN_HEADROOM);
        if (availableMemory < required) blockers.add("Memoria disponible insuficiente");
        if (freeStorage < CaptureReadiness.MIN_FREE_BYTES) blockers.add("Espacio libre inferior a 250 MB");
        if (frames > 40) warnings.add("Más de 40 fotogramas aumentan el tiempo de proceso");
        if (pairs > 160) warnings.add("Grafo visual denso");
        String tier = estimated <= 72L * 1024L * 1024L ? "LIGHT"
                : estimated <= 128L * 1024L * 1024L ? "STANDARD" : "HEAVY";
        return new Result(blockers, warnings, estimated, required, tier);
    }

    private static long multiply(long a, long b) {
        if (a < 0 || b < 0) return Long.MAX_VALUE;
        if (a == 0 || b == 0) return 0;
        return a > Long.MAX_VALUE / b ? Long.MAX_VALUE : a * b;
    }

    private static long add(long a, long b) {
        return a > Long.MAX_VALUE - b ? Long.MAX_VALUE : a + b;
    }

    public static final class Result {
        public final List<String> blockers;
        public final List<String> warnings;
        public final long estimatedBytes;
        public final long requiredBytes;
        public final String tier;

        Result(List<String> blockers, List<String> warnings, long estimatedBytes,
               long requiredBytes, String tier) {
            this.blockers = Collections.unmodifiableList(new ArrayList<String>(blockers));
            this.warnings = Collections.unmodifiableList(new ArrayList<String>(warnings));
            this.estimatedBytes = estimatedBytes;
            this.requiredBytes = requiredBytes;
            this.tier = tier;
        }

        public boolean ready() {
            return blockers.isEmpty();
        }

        public String summary() {
            return "Presupuesto " + tier + " · "
                    + Math.round(estimatedBytes / (1024.0 * 1024.0)) + " MiB";
        }
    }
}

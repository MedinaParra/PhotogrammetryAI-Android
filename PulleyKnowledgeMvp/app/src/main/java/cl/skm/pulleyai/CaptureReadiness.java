package cl.skm.pulleyai;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** Pure quality gate for deciding whether a capture session can proceed to reconstruction. */
public final class CaptureReadiness {
    public static final int MIN_ACCEPTED = 30;
    public static final long MIN_FREE_BYTES = 250L * 1024L * 1024L;

    private CaptureReadiness() {
    }

    public static Result evaluate(int accepted, int rejected, int lowMask, int highMask,
                                  Double shellLengthMm, long freeBytes) {
        return evaluate(accepted, rejected, lowMask, highMask, shellLengthMm, freeBytes,
                false, false, null);
    }

    public static Result evaluate(int accepted, int rejected, int lowMask, int highMask,
                                  Double shellLengthMm, long freeBytes, boolean requireOverlap,
                                  boolean overlapReady, String overlapStatus) {
        List<String> blockers = new ArrayList<String>();
        List<String> warnings = new ArrayList<String>();
        if (accepted < MIN_ACCEPTED) blockers.add("Faltan " + (MIN_ACCEPTED - accepted) + " fotografías aceptadas");
        if (!CoveragePlanner.isComplete(lowMask)) blockers.add("Anillo a altura de eje incompleto");
        if (!CoveragePlanner.isComplete(highMask)) blockers.add("Anillo alto incompleto");
        if (shellLengthMm == null || shellLengthMm <= 0.0) blockers.add("Falta el largo real del manto para definir escala");
        if (freeBytes < MIN_FREE_BYTES) blockers.add("Espacio libre inferior a 250 MB");
        if (requireOverlap && !overlapReady) {
            blockers.add("Solape multivista no aprobado"
                    + (overlapStatus == null || overlapStatus.isEmpty() ? "" : " (" + overlapStatus + ")"));
        }
        int total = accepted + rejected;
        if (total >= 10 && rejected > 0) {
            double ratio = (double) rejected / total;
            if (ratio > 0.45) warnings.add("Tasa de rechazo elevada: " + Math.round(ratio * 100.0) + "%");
        }
        if (accepted >= MIN_ACCEPTED && accepted < 40) warnings.add("Cobertura mínima alcanzada; 40 fotos mejoran robustez");
        return new Result(blockers, warnings);
    }

    public static final class Result {
        public final List<String> blockers;
        public final List<String> warnings;

        Result(List<String> blockers, List<String> warnings) {
            this.blockers = Collections.unmodifiableList(new ArrayList<String>(blockers));
            this.warnings = Collections.unmodifiableList(new ArrayList<String>(warnings));
        }

        public boolean ready() {
            return blockers.isEmpty();
        }

        public String summary() {
            StringBuilder text = new StringBuilder();
            if (ready()) text.append("Lista para reconstrucción");
            else {
                text.append("Bloqueos:");
                for (String blocker : blockers) text.append("\n• ").append(blocker);
            }
            if (!warnings.isEmpty()) {
                text.append("\nAdvertencias:");
                for (String warning : warnings) text.append("\n• ").append(warning);
            }
            return text.toString();
        }
    }
}

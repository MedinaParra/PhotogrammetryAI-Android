package cl.skm.pulleyai;

import java.util.Collections;
import java.util.List;

/** Fail-closed admission and operator guidance for balanced two-ring capture. */
public final class GuidedCaptureAdmissionCore {
    public static final int MAX_ACCEPTED_PER_CELL = 2;
    public static final double MIN_DISTINCT_YAW_DEGREES = 6.0;
    public static final double MAX_BORDER_OBSTRUCTION_SCORE = 0.82;
    public static final double MIN_CENTER_DETAIL_RATIO = 0.025;

    private GuidedCaptureAdmissionCore() {}

    public static Decision evaluate(boolean baseAccepted, String baseReason,
                                    String band, int sector, double yawDegrees,
                                    List<ExistingFrame> acceptedFrames,
                                    double borderObstructionScore,
                                    double centerDetailRatio) {
        if (!baseAccepted) return Decision.rejected(clean(baseReason, "Calidad base insuficiente"));
        if (sector < 0 || sector >= CoveragePlanner.SECTOR_COUNT) {
            return Decision.rejected("Sector angular inválido");
        }
        if (borderObstructionScore >= MAX_BORDER_OBSTRUCTION_SCORE) {
            return Decision.rejected("Lente parcialmente obstruida o borde dominante; despeje la cámara");
        }
        if (centerDetailRatio < MIN_CENTER_DETAIL_RATIO) {
            return Decision.rejected("Encuadre central sin detalle suficiente; centre la polea completa");
        }
        int sameCell = 0;
        double nearestYaw = Double.POSITIVE_INFINITY;
        List<ExistingFrame> frames = acceptedFrames == null
                ? Collections.<ExistingFrame>emptyList() : acceptedFrames;
        for (ExistingFrame frame : frames) {
            if (frame == null || !sameBand(band, frame.band) || frame.sector != sector) continue;
            sameCell++;
            nearestYaw = Math.min(nearestYaw, angularDistance(yawDegrees, frame.yawDegrees));
        }
        if (sameCell >= MAX_ACCEPTED_PER_CELL) {
            return Decision.rejected("Sector ya cubierto con dos vistas; avance al siguiente sector");
        }
        if (sameCell > 0 && nearestYaw < MIN_DISTINCT_YAW_DEGREES) {
            return Decision.rejected("Captura redundante; muévase físicamente antes de repetir el sector");
        }
        return Decision.accepted();
    }

    public static FrameMetrics analyzeGrid(double[] luma, int width, int height) {
        if (luma == null || width < 12 || height < 12 || luma.length < width * height) {
            return new FrameMetrics(1.0, 0.0);
        }
        Region center = region(luma, width, height,
                width / 5, height / 6, width - width / 5, height - height / 6);
        int stripX = Math.max(2, width / 5);
        int stripY = Math.max(2, height / 5);
        Region left = region(luma, width, height, 0, 0, stripX, height);
        Region right = region(luma, width, height, width - stripX, 0, width, height);
        Region top = region(luma, width, height, 0, 0, width, stripY);
        Region bottom = region(luma, width, height, 0, height - stripY, width, height);
        double obstruction = Math.max(Math.max(obstruction(center, left), obstruction(center, right)),
                Math.max(obstruction(center, top), obstruction(center, bottom)));
        return new FrameMetrics(clamp(obstruction, 0.0, 1.0), center.detailRatio);
    }

    public static String nextInstruction(int lowMask, int highMask, String activeBand,
                                         int accepted, boolean overlapReady) {
        boolean lowComplete = CoveragePlanner.isComplete(lowMask);
        boolean highComplete = CoveragePlanner.isComplete(highMask);
        if (!lowComplete && "HIGH".equals(activeBand)) return "Cambie a ALTURA: EJE y complete el anillo";
        if (lowComplete && !highComplete && !"HIGH".equals(activeBand)) return "Cambie a ALTURA: ALTA y complete el segundo anillo";
        if (!lowComplete) return "Complete los sectores faltantes del anillo EJE";
        if (!highComplete) return "Complete los sectores faltantes del anillo ALTA";
        if (accepted < CaptureReadiness.MIN_ACCEPTED) {
            return "Ambos anillos completos; agregue " + (CaptureReadiness.MIN_ACCEPTED - accepted)
                    + " vistas distintas en sectores ya cubiertos";
        }
        if (!overlapReady) return "Ambos anillos completos; ejecute VERIFICAR SOLAPE";
        return "Recorrido listo para finalizar";
    }

    private static Region region(double[] luma, int width, int height,
                                 int x0, int y0, int x1, int y1) {
        x0 = Math.max(0, Math.min(width - 2, x0));
        y0 = Math.max(0, Math.min(height - 2, y0));
        x1 = Math.max(x0 + 2, Math.min(width, x1));
        y1 = Math.max(y0 + 2, Math.min(height, y1));
        double sum = 0.0, square = 0.0;
        int count = 0, detailed = 0;
        for (int y = y0; y < y1 - 1; y++) {
            for (int x = x0; x < x1 - 1; x++) {
                double value = luma[y * width + x];
                sum += value;
                square += value * value;
                count++;
                double gradient = Math.abs(value - luma[y * width + x + 1])
                        + Math.abs(value - luma[(y + 1) * width + x]);
                if (gradient >= 22.0) detailed++;
            }
        }
        double mean = count == 0 ? 0.0 : sum / count;
        double variance = count == 0 ? 0.0 : Math.max(0.0, square / count - mean * mean);
        return new Region(Math.sqrt(variance), count == 0 ? 0.0 : (double) detailed / count);
    }

    private static double obstruction(Region center, Region border) {
        double uniformity = 1.0 - clamp(border.standardDeviation / 34.0, 0.0, 1.0);
        double lowDetail = 1.0 - clamp(border.detailRatio / 0.16, 0.0, 1.0);
        double contrast = clamp((center.detailRatio - border.detailRatio) / 0.12, 0.0, 1.0);
        return 0.42 * uniformity + 0.38 * lowDetail + 0.20 * contrast;
    }

    private static boolean sameBand(String first, String second) {
        return "HIGH".equals(first) == "HIGH".equals(second);
    }

    private static double angularDistance(double first, double second) {
        double delta = Math.abs(CoveragePlanner.normalizeYaw(first)
                - CoveragePlanner.normalizeYaw(second));
        return Math.min(delta, 360.0 - delta);
    }

    private static String clean(String value, String fallback) {
        return value == null || value.trim().isEmpty() ? fallback : value.trim();
    }

    private static double clamp(double value, double minimum, double maximum) {
        return Math.max(minimum, Math.min(maximum, value));
    }

    private static final class Region {
        final double standardDeviation;
        final double detailRatio;
        Region(double standardDeviation, double detailRatio) {
            this.standardDeviation = standardDeviation;
            this.detailRatio = detailRatio;
        }
    }

    public static final class ExistingFrame {
        public final String band;
        public final int sector;
        public final double yawDegrees;
        public ExistingFrame(String band, int sector, double yawDegrees) {
            this.band = band;
            this.sector = sector;
            this.yawDegrees = yawDegrees;
        }
    }

    public static final class FrameMetrics {
        public final double borderObstructionScore;
        public final double centerDetailRatio;
        FrameMetrics(double borderObstructionScore, double centerDetailRatio) {
            this.borderObstructionScore = borderObstructionScore;
            this.centerDetailRatio = centerDetailRatio;
        }
    }

    public static final class Decision {
        public final boolean accepted;
        public final String reason;
        private Decision(boolean accepted, String reason) {
            this.accepted = accepted;
            this.reason = reason;
        }
        static Decision accepted() { return new Decision(true, "Calidad y diversidad suficientes"); }
        static Decision rejected(String reason) { return new Decision(false, reason); }
    }
}

package cl.skm.pulleyai;

import java.util.Locale;

/** Conservative center-locked visual target signature for field capture without a semantic ML model. */
public final class PulleyTargetLockCore {
    private static final int HISTOGRAM_BINS = 12;
    private static final int RADIAL_BINS = 3;
    public static final double MIN_REFERENCE_DETAIL = 0.030;
    public static final double MIN_TARGET_DOMINANCE = 0.58;
    public static final double MAX_SCENE_AMBIGUITY = 0.86;
    public static final double MAX_BACKLIGHT_SCORE = 0.70;
    public static final double MIN_CONTINUITY = 0.42;

    private PulleyTargetLockCore() {}

    public static Signature analyze(double[] luma, int width, int height) {
        if (luma == null || width < 12 || height < 12 || luma.length < width * height) return Signature.invalid();
        int x0 = width / 5, x1 = width - width / 5;
        int y0 = height / 6, y1 = height - height / 6;
        Region center = region(luma, width, height, x0, y0, x1, y1, true);
        Region left = region(luma, width, height, 0, 0, Math.max(2, width / 5), height, false);
        Region right = region(luma, width, height, width - Math.max(2, width / 5), 0, width, height, false);
        Region top = region(luma, width, height, 0, 0, width, Math.max(2, height / 5), false);
        Region bottom = region(luma, width, height, 0, height - Math.max(2, height / 5), width, height, false);
        double maxBorderDetail = Math.max(Math.max(left.detailRatio, right.detailRatio), Math.max(top.detailRatio, bottom.detailRatio));
        double maxBorderMean = Math.max(Math.max(left.mean, right.mean), Math.max(top.mean, bottom.mean));
        double dominance = clamp(center.detailRatio / Math.max(0.025, maxBorderDetail), 0.0, 3.0) / 3.0;
        double ambiguity = clamp((maxBorderDetail - center.detailRatio * 0.80) / Math.max(0.04, center.detailRatio), 0.0, 1.0);
        double backlight = clamp((maxBorderMean - center.mean - 48.0) / 95.0, 0.0, 1.0)
                * clamp((105.0 - center.mean) / 65.0, 0.0, 1.0);
        return new Signature(center.histogram, center.radial, center.quadrants,
                center.detailRatio, dominance, ambiguity, backlight, center.mean, true);
    }

    public static Decision evaluate(Signature reference, Signature current) {
        if (current == null || !current.valid) return Decision.rejected("Firma visual no disponible", 0.0);
        if (current.centerDetailRatio < MIN_REFERENCE_DETAIL)
            return Decision.rejected("Polea objetivo sin detalle suficiente; acérquese y centre el manto", 0.0);
        if (current.backlightScore >= MAX_BACKLIGHT_SCORE)
            return Decision.rejected("Contraluz severo sobre la polea; cambie el ángulo o la exposición", 0.0);
        if (current.targetDominance < MIN_TARGET_DOMINANCE && current.sceneAmbiguity > MAX_SCENE_AMBIGUITY * 0.72)
            return Decision.rejected("La polea no domina el encuadre; excluya otros tambores y acérquese", 0.0);
        if (current.sceneAmbiguity >= MAX_SCENE_AMBIGUITY)
            return Decision.rejected("Escena ambigua: otros objetos compiten con la polea centrada", 0.0);
        if (reference == null) return Decision.accepted("OBJETIVO_LOCKED", 1.0);
        if (!reference.valid) return Decision.rejected("Bloqueo visual almacenado inválido", 0.0);
        double continuity = similarity(reference, current);
        if (continuity < MIN_CONTINUITY)
            return Decision.rejected("Objetivo visual cambió; vuelva a encuadrar la polea bloqueada", continuity);
        if (continuity < 0.52 && current.sceneAmbiguity > 0.58)
            return Decision.rejected("Continuidad débil y fondo ambiguo; capture más cerca de la misma polea", continuity);
        return Decision.accepted("TARGET_CONTINUITY_OK", continuity);
    }

    public static double similarity(Signature a, Signature b) {
        if (a == null || b == null || !a.valid || !b.valid) return 0.0;
        double hist = 0.0;
        for (int i = 0; i < HISTOGRAM_BINS; i++) hist += Math.min(a.histogram[i], b.histogram[i]);
        double radialL1 = 0.0;
        for (int i = 0; i < RADIAL_BINS; i++) radialL1 += Math.abs(a.radial[i] - b.radial[i]);
        double quadrantL1 = 0.0;
        for (int i = 0; i < 4; i++) quadrantL1 += Math.abs(a.quadrants[i] - b.quadrants[i]);
        double detail = clamp(1.0 - Math.abs(a.centerDetailRatio - b.centerDetailRatio)
                / Math.max(0.06, Math.max(a.centerDetailRatio, b.centerDetailRatio)), 0.0, 1.0);
        return clamp(0.50 * hist + 0.20 * clamp(1.0 - radialL1 * 0.5, 0.0, 1.0)
                + 0.18 * clamp(1.0 - quadrantL1 * 0.5, 0.0, 1.0) + 0.12 * detail, 0.0, 1.0);
    }

    public static String encode(Signature signature) {
        if (signature == null || !signature.valid) return "";
        StringBuilder out = new StringBuilder("skm-target-lock/1");
        append(out, signature.histogram); append(out, signature.radial); append(out, signature.quadrants);
        out.append('|').append(format(signature.centerDetailRatio)).append('|').append(format(signature.targetDominance))
                .append('|').append(format(signature.sceneAmbiguity)).append('|').append(format(signature.backlightScore))
                .append('|').append(format(signature.centerMean));
        return out.toString();
    }

    public static Signature decode(String encoded) {
        if (encoded == null || encoded.trim().isEmpty()) return null;
        try {
            String[] values = encoded.trim().split("\\|");
            int expected = 1 + HISTOGRAM_BINS + RADIAL_BINS + 4 + 5;
            if (values.length != expected || !"skm-target-lock/1".equals(values[0])) return null;
            int index = 1;
            double[] histogram = new double[HISTOGRAM_BINS];
            for (int i = 0; i < histogram.length; i++) histogram[i] = Double.parseDouble(values[index++]);
            double[] radial = new double[RADIAL_BINS];
            for (int i = 0; i < radial.length; i++) radial[i] = Double.parseDouble(values[index++]);
            double[] quadrants = new double[4];
            for (int i = 0; i < quadrants.length; i++) quadrants[i] = Double.parseDouble(values[index++]);
            return new Signature(histogram, radial, quadrants, Double.parseDouble(values[index++]),
                    Double.parseDouble(values[index++]), Double.parseDouble(values[index++]),
                    Double.parseDouble(values[index++]), Double.parseDouble(values[index]), true);
        } catch (RuntimeException error) { return null; }
    }

    private static Region region(double[] luma, int width, int height, int x0, int y0, int x1, int y1, boolean signature) {
        x0 = Math.max(0, Math.min(width - 2, x0)); y0 = Math.max(0, Math.min(height - 2, y0));
        x1 = Math.max(x0 + 2, Math.min(width, x1)); y1 = Math.max(y0 + 2, Math.min(height, y1));
        double[] histogram = new double[HISTOGRAM_BINS], radial = new double[RADIAL_BINS], quadrants = new double[4];
        int count = 0, detailed = 0; int[] radialCount = new int[RADIAL_BINS], radialDetailed = new int[RADIAL_BINS];
        int[] quadrantCount = new int[4], quadrantDetailed = new int[4]; double sum = 0.0;
        double cx = (x0 + x1 - 1) * 0.5, cy = (y0 + y1 - 1) * 0.5;
        double rx = Math.max(1.0, (x1 - x0) * 0.5), ry = Math.max(1.0, (y1 - y0) * 0.5);
        for (int y = y0; y < y1 - 1; y++) for (int x = x0; x < x1 - 1; x++) {
            double value = luma[y * width + x]; sum += value; count++;
            if (signature) histogram[Math.min(HISTOGRAM_BINS - 1, Math.max(0, (int) (value * HISTOGRAM_BINS / 256.0)))]++;
            double gradient = Math.abs(value - luma[y * width + x + 1]) + Math.abs(value - luma[(y + 1) * width + x]);
            boolean isDetailed = gradient >= 22.0; if (isDetailed) detailed++;
            double nx = (x - cx) / rx, ny = (y - cy) / ry;
            int ring = Math.min(RADIAL_BINS - 1, (int) (Math.sqrt(nx * nx + ny * ny) * RADIAL_BINS));
            radialCount[ring]++; if (isDetailed) radialDetailed[ring]++;
            int quadrant = (y < cy ? 0 : 2) + (x < cx ? 0 : 1);
            quadrantCount[quadrant]++; if (isDetailed) quadrantDetailed[quadrant]++;
        }
        if (count > 0 && signature) for (int i = 0; i < histogram.length; i++) histogram[i] /= count;
        double sumRadial = 0.0; for (int i = 0; i < RADIAL_BINS; i++) { radial[i] = radialCount[i] == 0 ? 0.0 : (double) radialDetailed[i] / radialCount[i]; sumRadial += radial[i]; }
        if (sumRadial > 1e-9) for (int i = 0; i < RADIAL_BINS; i++) radial[i] /= sumRadial;
        double sumQuadrants = 0.0; for (int i = 0; i < 4; i++) { quadrants[i] = quadrantCount[i] == 0 ? 0.0 : (double) quadrantDetailed[i] / quadrantCount[i]; sumQuadrants += quadrants[i]; }
        if (sumQuadrants > 1e-9) for (int i = 0; i < 4; i++) quadrants[i] /= sumQuadrants;
        return new Region(count == 0 ? 0.0 : sum / count, count == 0 ? 0.0 : (double) detailed / count, histogram, radial, quadrants);
    }

    private static void append(StringBuilder out, double[] values) { for (double value : values) out.append('|').append(format(value)); }
    private static String format(double value) { return String.format(Locale.ROOT, "%.8f", value); }
    private static double clamp(double value, double min, double max) { return Math.max(min, Math.min(max, value)); }

    private static final class Region {
        final double mean, detailRatio; final double[] histogram, radial, quadrants;
        Region(double mean, double detailRatio, double[] histogram, double[] radial, double[] quadrants) {
            this.mean = mean; this.detailRatio = detailRatio; this.histogram = histogram; this.radial = radial; this.quadrants = quadrants;
        }
    }

    public static final class Signature {
        public final double[] histogram, radial, quadrants;
        public final double centerDetailRatio, targetDominance, sceneAmbiguity, backlightScore, centerMean;
        public final boolean valid;
        Signature(double[] histogram, double[] radial, double[] quadrants, double centerDetailRatio,
                  double targetDominance, double sceneAmbiguity, double backlightScore, double centerMean, boolean valid) {
            this.histogram = histogram.clone(); this.radial = radial.clone(); this.quadrants = quadrants.clone();
            this.centerDetailRatio = centerDetailRatio; this.targetDominance = targetDominance;
            this.sceneAmbiguity = sceneAmbiguity; this.backlightScore = backlightScore; this.centerMean = centerMean; this.valid = valid;
        }
        static Signature invalid() { return new Signature(new double[HISTOGRAM_BINS], new double[RADIAL_BINS], new double[4], 0, 0, 1, 1, 0, false); }
    }

    public static final class Decision {
        public final boolean accepted; public final String reason; public final double continuity;
        private Decision(boolean accepted, String reason, double continuity) { this.accepted = accepted; this.reason = reason; this.continuity = continuity; }
        static Decision accepted(String reason, double continuity) { return new Decision(true, reason, continuity); }
        static Decision rejected(String reason, double continuity) { return new Decision(false, reason, continuity); }
    }
}

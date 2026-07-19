package cl.skm.pulleyai;

/** Pure quality metrics and deterministic acceptance gate for sampled luma grids. */
public final class ImageQualityMath {
    public static final class Result {
        public final double blurScore;
        public final double meanLuma;
        public final double darkFraction;
        public final double brightFraction;
        public final double motionScore;
        public final String status;
        public final String reason;

        Result(double blurScore, double meanLuma, double darkFraction, double brightFraction,
               double motionScore, String status, String reason) {
            this.blurScore = blurScore;
            this.meanLuma = meanLuma;
            this.darkFraction = darkFraction;
            this.brightFraction = brightFraction;
            this.motionScore = motionScore;
            this.status = status;
            this.reason = reason;
        }

        public boolean accepted() {
            return "ACCEPTED".equals(status);
        }
    }

    private ImageQualityMath() {
    }

    public static Result analyze(double[] luma, int width, int height, double motionScore) {
        if (luma == null || width < 3 || height < 3 || luma.length < width * height) {
            return new Result(0, 0, 1, 0, motionScore, "REJECTED", "Muestra luminosa inválida");
        }

        long dark = 0;
        long bright = 0;
        double sum = 0.0;
        int count = width * height;
        for (int i = 0; i < count; i++) {
            double value = luma[i];
            sum += value;
            if (value < 24.0) dark++;
            if (value > 246.0) bright++;
        }

        double lapSum = 0.0;
        double lapSquareSum = 0.0;
        long lapCount = 0;
        for (int y = 1; y < height - 1; y++) {
            for (int x = 1; x < width - 1; x++) {
                double center = luma[y * width + x];
                double laplacian = 4.0 * center
                        - luma[y * width + x - 1]
                        - luma[y * width + x + 1]
                        - luma[(y - 1) * width + x]
                        - luma[(y + 1) * width + x];
                lapSum += laplacian;
                lapSquareSum += laplacian * laplacian;
                lapCount++;
            }
        }

        double lapMean = lapCount == 0 ? 0.0 : lapSum / lapCount;
        double blurScore = lapCount == 0
                ? 0.0
                : Math.max(0.0, lapSquareSum / lapCount - lapMean * lapMean);
        double mean = sum / count;
        double darkFraction = (double) dark / count;
        double brightFraction = (double) bright / count;

        String status = "ACCEPTED";
        String reason = "Calidad suficiente";
        if (motionScore > 1.35) {
            status = "REJECTED";
            reason = "Movimiento excesivo durante la captura";
        } else if (blurScore < 105.0) {
            status = "REJECTED";
            reason = "Imagen desenfocada o con poco detalle";
        } else if (mean < 38.0 || darkFraction > 0.62) {
            status = "REJECTED";
            reason = "Imagen demasiado oscura";
        } else if (mean > 224.0 || brightFraction > 0.42) {
            status = "REJECTED";
            reason = "Imagen sobreexpuesta o con reflejos dominantes";
        }

        return new Result(blurScore, mean, darkFraction, brightFraction, motionScore, status, reason);
    }
}

package cl.skm.pulleyai;

/** Converts an arbitrary-unit shell fit into millimetres using the measured shell length. */
public final class PulleyMetricScaleCore {
    private PulleyMetricScaleCore() {}

    public static Result resolve(PulleyShellRansacCore.Result shell, Double shellLengthMm) {
        if (shell == null || !shell.solved || !shell.ready()
                || !Double.isFinite(shell.length) || shell.length <= 1e-9) {
            return Result.failed("SHELL_FIT_UNAVAILABLE");
        }
        if (shellLengthMm == null || !Double.isFinite(shellLengthMm) || shellLengthMm <= 0.0) {
            return Result.failed("MEASURED_LENGTH_REQUIRED");
        }
        double scale = shellLengthMm / shell.length;
        double diameter = shell.radius * 2.0 * scale;
        double radialRmsMm = shell.radialRms * scale;
        double relativeResidual = shell.radius <= 1e-9 ? 1.0 : shell.radialRms / shell.radius;
        double confidence = clamp(0.30 + 0.50 * shell.inlierRatio
                + 0.20 * Math.max(0.0, 1.0 - relativeResidual / 0.08), 0.0, 0.99);
        double diameterUncertainty = Math.max(2.0 * radialRmsMm,
                diameter * (0.015 + 0.04 * (1.0 - confidence)));
        String status = confidence >= 0.82 && diameterUncertainty <= Math.max(8.0, diameter * 0.035)
                ? "STRONG" : confidence >= 0.60
                && diameterUncertainty <= Math.max(18.0, diameter * 0.08)
                ? "USABLE" : "WEAK";
        return new Result(true,status,scale,shellLengthMm,diameter,
                radialRmsMm,diameterUncertainty,confidence);
    }

    private static double clamp(double value,double min,double max) {
        return Math.max(min,Math.min(max,value));
    }

    public static final class Result {
        public final boolean solved;
        public final String status;
        public final double millimetresPerUnit;
        public final double shellLengthMm;
        public final double shellDiameterMm;
        public final double radialRmsMm;
        public final double diameterUncertaintyMm;
        public final double confidence;
        Result(boolean solved,String status,double millimetresPerUnit,double shellLengthMm,
               double shellDiameterMm,double radialRmsMm,double diameterUncertaintyMm,
               double confidence) {
            this.solved=solved;this.status=status;this.millimetresPerUnit=millimetresPerUnit;
            this.shellLengthMm=shellLengthMm;this.shellDiameterMm=shellDiameterMm;
            this.radialRmsMm=radialRmsMm;this.diameterUncertaintyMm=diameterUncertaintyMm;
            this.confidence=confidence;
        }
        static Result failed(String status) {
            return new Result(false,status,Double.NaN,Double.NaN,Double.NaN,
                    Double.POSITIVE_INFINITY,Double.POSITIVE_INFINITY,0.0);
        }
        public boolean ready() { return "STRONG".equals(status)||"USABLE".equals(status); }
        public String summary() {
            return "Escala "+format(millimetresPerUnit)+" mm/u · Ø "
                    +format(shellDiameterMm)+" ± "+format(diameterUncertaintyMm)
                    +" mm · "+Math.round(confidence*100.0)+"% · "+status;
        }
        private static String format(double value) {
            return String.format(java.util.Locale.ROOT,"%.2f",value);
        }
    }
}

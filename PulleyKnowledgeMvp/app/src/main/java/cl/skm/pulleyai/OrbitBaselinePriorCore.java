package cl.skm.pulleyai;

/** Uses the guided orbit and IMU angles to assign a consistent relative baseline scale. */
public final class OrbitBaselinePriorCore {
    private static final double CROSS_BAND_HEIGHT_UNITS = 0.38;

    private OrbitBaselinePriorCore() {}

    public static Result resolve(double leftYawDeg, double rightYawDeg,
                                 double leftPitchDeg, double rightPitchDeg,
                                 String leftBand, String rightBand) {
        if (!finite(leftYawDeg,rightYawDeg,leftPitchDeg,rightPitchDeg)
                || leftBand == null || rightBand == null) {
            return Result.failed("ORIENTATION_UNAVAILABLE");
        }
        double yawDelta = Math.abs(wrap(rightYawDeg-leftYawDeg));
        double pitchDelta = Math.abs(rightPitchDeg-leftPitchDeg);
        boolean crossBand = !leftBand.equals(rightBand);
        double chord = 2.0*Math.sin(Math.toRadians(yawDelta)*0.5);
        double vertical = crossBand ? CROSS_BAND_HEIGHT_UNITS : 0.0;
        double baseline = Math.sqrt(chord*chord+vertical*vertical);
        if (baseline<0.10) baseline=crossBand?CROSS_BAND_HEIGHT_UNITS:0.10;
        double yawScore = crossBand
                ? clamp(1.0-yawDelta/75.0,0.0,1.0)
                : clamp(1.0-Math.abs(yawDelta-30.0)/55.0,0.0,1.0);
        double pitchScore = clamp(1.0-pitchDelta/35.0,0.0,1.0);
        double confidence = clamp(0.25+0.55*yawScore+0.20*pitchScore,0.0,0.97);
        String status = confidence>=0.78 && baseline>=0.25 ? "STRONG"
                : confidence>=0.52 && baseline>=0.15 ? "USABLE" : "WEAK";
        return new Result(true,status,baseline,yawDelta,pitchDelta,crossBand,confidence);
    }

    public static double[] scaleDirection(double[] direction, Result prior) {
        if (direction == null || direction.length != 3 || prior == null || !prior.solved)
            return direction == null ? null : direction.clone();
        double norm=Math.sqrt(direction[0]*direction[0]+direction[1]*direction[1]
                +direction[2]*direction[2]);
        if(norm<1e-12)return new double[]{0,0,0};
        double scale=prior.baselineUnits/norm;
        return new double[]{direction[0]*scale,direction[1]*scale,direction[2]*scale};
    }

    private static double wrap(double degrees) {
        double value=degrees%360.0;
        if(value>180.0)value-=360.0;
        if(value<-180.0)value+=360.0;
        return value;
    }
    private static boolean finite(double... values){for(double value:values)if(!Double.isFinite(value))return false;return true;}
    private static double clamp(double value,double min,double max){return Math.max(min,Math.min(max,value));}

    public static final class Result {
        public final boolean solved;
        public final String status;
        public final double baselineUnits;
        public final double yawDeltaDegrees;
        public final double pitchDeltaDegrees;
        public final boolean crossBand;
        public final double confidence;
        Result(boolean solved,String status,double baselineUnits,double yawDeltaDegrees,
               double pitchDeltaDegrees,boolean crossBand,double confidence) {
            this.solved=solved;this.status=status;this.baselineUnits=baselineUnits;
            this.yawDeltaDegrees=yawDeltaDegrees;this.pitchDeltaDegrees=pitchDeltaDegrees;
            this.crossBand=crossBand;this.confidence=confidence;
        }
        static Result failed(String status){return new Result(false,status,Double.NaN,
                Double.NaN,Double.NaN,false,0.0);}
        public boolean ready(){return "STRONG".equals(status)||"USABLE".equals(status);}
        public String summary(){return "Baseline "+format(baselineUnits)+" u · Δyaw "
                +format(yawDeltaDegrees)+"° · "+Math.round(confidence*100.0)+"% · "+status;}
        private static String format(double value){return String.format(java.util.Locale.ROOT,"%.3f",value);}
    }
}

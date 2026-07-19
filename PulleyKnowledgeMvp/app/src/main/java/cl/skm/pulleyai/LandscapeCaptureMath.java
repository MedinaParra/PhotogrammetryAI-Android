package cl.skm.pulleyai;

/** Pure orientation and stability policy for the industrial landscape capture workflow. */
public final class LandscapeCaptureMath {
    private LandscapeCaptureMath() {
    }

    public static int displayRotationDegrees(int surfaceRotation) {
        switch (surfaceRotation) {
            case 1: return 90;
            case 2: return 180;
            case 3: return 270;
            default: return 0;
        }
    }

    public static int jpegOrientation(int sensorOrientation, int displayRotationDegrees, boolean frontFacing) {
        int sensor = normalize360(sensorOrientation);
        int display = normalize360(displayRotationDegrees);
        return frontFacing
                ? normalize360(sensor + display)
                : normalize360(sensor - display);
    }

    /** Error in degrees from either valid landscape roll (+90 or -90). */
    public static double landscapeRollError(double rollDegrees) {
        double positive = Math.abs(normalize180(rollDegrees - 90.0));
        double negative = Math.abs(normalize180(rollDegrees + 90.0));
        return Math.min(positive, negative);
    }

    public static Stability evaluate(double pitchDegrees, double rollDegrees, double motionRadPerSec,
                                     boolean hasOrientation, boolean hasGyroscope) {
        if (!hasOrientation) {
            return new Stability(false, false, false, "Sensor de orientación no disponible");
        }
        boolean landscape = landscapeRollError(rollDegrees) <= 24.0;
        boolean pitchOk = Math.abs(pitchDegrees) <= 32.0;
        boolean stable = !hasGyroscope || motionRadPerSec <= 1.25;
        String reason;
        if (!landscape) reason = "Gire el teléfono a horizontal";
        else if (!pitchOk) reason = "Reduzca la inclinación vertical";
        else if (!stable) reason = "Mantenga el teléfono inmóvil";
        else reason = "Posición estable";
        return new Stability(landscape, pitchOk, stable, reason);
    }

    public static int normalize360(int degrees) {
        int value = degrees % 360;
        return value < 0 ? value + 360 : value;
    }

    public static double normalize180(double degrees) {
        double value = degrees % 360.0;
        if (value > 180.0) value -= 360.0;
        if (value < -180.0) value += 360.0;
        return value;
    }

    public static final class Stability {
        public final boolean landscape;
        public final boolean pitchOk;
        public final boolean stable;
        public final String reason;

        Stability(boolean landscape, boolean pitchOk, boolean stable, String reason) {
            this.landscape = landscape;
            this.pitchOk = pitchOk;
            this.stable = stable;
            this.reason = reason;
        }

        public boolean ready() {
            return landscape && pitchOk && stable;
        }
    }
}

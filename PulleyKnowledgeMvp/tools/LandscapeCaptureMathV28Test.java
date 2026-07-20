import cl.skm.pulleyai.LandscapeCaptureMath;

public final class LandscapeCaptureMathV28Test {
    public static void main(String[] args) {
        expect(0, LandscapeCaptureMath.jpegOrientation(90, 90, false), "sensor90 landscape-right");
        expect(180, LandscapeCaptureMath.jpegOrientation(90, 270, false), "sensor90 landscape-left");
        expect(180, LandscapeCaptureMath.jpegOrientation(270, 90, false), "sensor270 landscape-right");
        near(0.0, LandscapeCaptureMath.landscapeRollError(90.0), "roll +90");
        near(0.0, LandscapeCaptureMath.landscapeRollError(-90.0), "roll -90");
        if (!LandscapeCaptureMath.evaluate(4.0, 88.0, 0.2, true, true).ready()) {
            throw new AssertionError("Stable landscape pose should be ready");
        }
        if (LandscapeCaptureMath.evaluate(2.0, 5.0, 0.2, true, true).ready()) {
            throw new AssertionError("Portrait pose must not be ready");
        }
        if (LandscapeCaptureMath.evaluate(2.0, 90.0, 2.0, true, true).ready()) {
            throw new AssertionError("Moving pose must not be ready");
        }
        System.out.println("LandscapeCaptureMathV28Test OK");
    }

    private static void expect(int expected, int actual, String message) {
        if (expected != actual) throw new AssertionError(message + ": expected " + expected + " got " + actual);
    }

    private static void near(double expected, double actual, String message) {
        if (Math.abs(expected - actual) > 1e-9) throw new AssertionError(message + ": " + actual);
    }
}

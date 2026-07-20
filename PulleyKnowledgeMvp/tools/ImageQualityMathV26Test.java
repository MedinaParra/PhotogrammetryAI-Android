import cl.skm.pulleyai.ImageQualityMath;

public final class ImageQualityMathV26Test {
    public static void main(String[] args) {
        int w = 32;
        int h = 32;
        double[] detailed = new double[w * h];
        double[] flat = new double[w * h];
        double[] dark = new double[w * h];
        for (int y = 0; y < h; y++) {
            for (int x = 0; x < w; x++) {
                detailed[y * w + x] = ((x / 2 + y / 2) & 1) == 0 ? 55.0 : 205.0;
                flat[y * w + x] = 125.0;
                dark[y * w + x] = 12.0;
            }
        }
        ImageQualityMath.Result accepted = ImageQualityMath.analyze(detailed, w, h, 0.1);
        require(accepted.accepted(), "detailed image should pass");
        require(accepted.blurScore > 105.0, "detail score");
        ImageQualityMath.Result blurred = ImageQualityMath.analyze(flat, w, h, 0.1);
        require(!blurred.accepted() && blurred.reason.contains("desenfocada"), "blur reason");
        ImageQualityMath.Result underexposed = ImageQualityMath.analyze(dark, w, h, 0.1);
        require(!underexposed.accepted(), "dark image should fail");
        ImageQualityMath.Result moving = ImageQualityMath.analyze(detailed, w, h, 2.0);
        require(!moving.accepted() && moving.reason.contains("Movimiento"), "motion reason");
        System.out.println("ImageQualityMathV26Test OK blur=" + Math.round(accepted.blurScore));
    }
    private static void require(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }
}

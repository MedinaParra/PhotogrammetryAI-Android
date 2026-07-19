import cl.skm.pulleyai.VisualFeatureCore;

public final class VisualFeatureCoreV29Test {
    public static void main(String[] args) {
        int width = 180;
        int height = 120;
        byte[] first = texture(width, height);
        byte[] second = shift(first, width, height, 6, 4);
        VisualFeatureCore.FeatureSet a = VisualFeatureCore.detect(first, width, height, 350);
        VisualFeatureCore.FeatureSet b = VisualFeatureCore.detect(second, width, height, 350);
        VisualFeatureCore.PairResult result = VisualFeatureCore.match(a, b);
        if (a.features.size() < 40 || b.features.size() < 40) {
            throw new AssertionError("Insufficient features: " + a.features.size() + "/" + b.features.size());
        }
        if (result.matches.size() < 12) {
            throw new AssertionError("Insufficient matches: " + result.matches.size());
        }
        if (Math.abs(result.medianDx - 6.0) > 1.5 || Math.abs(result.medianDy - 4.0) > 1.5) {
            throw new AssertionError("Wrong translation: " + result.medianDx + "," + result.medianDy);
        }
        System.out.println("VisualFeatureCoreV29Test OK features=" + a.features.size()
                + " matches=" + result.matches.size() + " shift=" + result.medianDx + "," + result.medianDy);
    }

    private static byte[] texture(int width, int height) {
        byte[] data = new byte[width * height];
        long state = 17L;
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                state = state * 1103515245L + 12345L;
                int noise = (int) ((state >>> 18) & 63);
                int grid = ((x / 13 + y / 11) & 1) == 0 ? 65 : 185;
                int circle = ((x - 70) * (x - 70) + (y - 55) * (y - 55) < 27 * 27) ? 45 : 0;
                data[y * width + x] = (byte) Math.max(0, Math.min(255, grid + noise - circle));
            }
        }
        return data;
    }

    private static byte[] shift(byte[] source, int width, int height, int dx, int dy) {
        byte[] output = new byte[source.length];
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                int sx = x - dx;
                int sy = y - dy;
                output[y * width + x] = sx >= 0 && sy >= 0 && sx < width && sy < height
                        ? source[sy * width + sx] : 0;
            }
        }
        return output;
    }
}

import cl.skm.pulleyai.VisualFeatureCore;

public final class VisualFeatureCoreV29Test {
    public static void main(String[] args) {
        translatedTextureKeepsDistributedMatches();
        localizedOverlapDoesNotPassAsUsableGeometry();
        System.out.println("VisualFeatureCoreV29Test OK");
    }

    private static void translatedTextureKeepsDistributedMatches() {
        int width = 240;
        int height = 160;
        byte[] first = texture(width, height);
        byte[] second = shift(first, width, height, 7, 5);
        VisualFeatureCore.FeatureSet a = VisualFeatureCore.detect(first, width, height, 420);
        VisualFeatureCore.FeatureSet b = VisualFeatureCore.detect(second, width, height, 420);
        VisualFeatureCore.PairResult result = VisualFeatureCore.match(a, b);
        if (a.features.size() < 70 || b.features.size() < 70) {
            throw new AssertionError("Insufficient features: " + a.features.size() + "/" + b.features.size());
        }
        if (a.spatialCoverage < 0.55 || b.spatialCoverage < 0.55) {
            throw new AssertionError("Feature distribution too concentrated: "
                    + a.spatialCoverage + "/" + b.spatialCoverage);
        }
        if (result.matches.size() < 20 || result.spatialCoverage < 0.25) {
            throw new AssertionError("Insufficient distributed matches: "
                    + result.matches.size() + " coverage=" + result.spatialCoverage);
        }
        if ("WEAK".equals(result.status)) {
            throw new AssertionError("Translated scene should be geometrically usable");
        }
        if (Math.abs(result.medianDx - 7.0) > 1.5 || Math.abs(result.medianDy - 5.0) > 1.5) {
            throw new AssertionError("Wrong translation: " + result.medianDx + "," + result.medianDy);
        }
    }

    private static void localizedOverlapDoesNotPassAsUsableGeometry() {
        int width = 240;
        int height = 160;
        byte[] first = localizedTexture(width, height);
        byte[] second = shift(first, width, height, 5, 3);
        VisualFeatureCore.FeatureSet a = VisualFeatureCore.detect(first, width, height, 420);
        VisualFeatureCore.FeatureSet b = VisualFeatureCore.detect(second, width, height, 420);
        VisualFeatureCore.PairResult result = VisualFeatureCore.match(a, b);
        if (result.matches.size() >= 12 && result.spatialCoverage >= 0.17) {
            throw new AssertionError("Fixture no longer represents localized overlap");
        }
        if (!"WEAK".equals(result.status)) {
            throw new AssertionError("Localized matches must remain WEAK, got " + result.status
                    + " coverage=" + result.spatialCoverage);
        }
    }

    private static byte[] texture(int width, int height) {
        byte[] data = new byte[width * height];
        long state = 17L;
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                state = state * 1103515245L + 12345L;
                int noise = (int) ((state >>> 18) & 63);
                int grid = ((x / 13 + y / 11) & 1) == 0 ? 65 : 185;
                int circle = ((x - width / 3) * (x - width / 3)
                        + (y - height / 2) * (y - height / 2) < 27 * 27) ? 45 : 0;
                data[y * width + x] = (byte) Math.max(0, Math.min(255, grid + noise - circle));
            }
        }
        return data;
    }

    private static byte[] localizedTexture(int width, int height) {
        byte[] data = new byte[width * height];
        long state = 91L;
        int startX = width * 3 / 4;
        int startY = height * 3 / 5;
        for (int y = startY; y < height; y++) {
            for (int x = startX; x < width; x++) {
                state = state * 1103515245L + 12345L;
                int noise = (int) ((state >>> 18) & 127);
                int grid = ((x / 7 + y / 9) & 1) == 0 ? 80 : 180;
                data[y * width + x] = (byte) Math.max(0, Math.min(255, grid + noise - 50));
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

package cl.skm.pulleyai;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public final class MultiScaleOrientedFeatureCoreV67Test {
    public static void main(String[] args) {
        int width = 420;
        int height = 300;
        byte[] base = synthetic(width, height);
        byte[] transformed = transform(base, width, height,
                Math.toRadians(9.0), 0.84, 24.0, 18.0);

        MultiScaleOrientedFeatureCore.FeatureSet left =
                MultiScaleOrientedFeatureCore.detect(base, width, height, 1000);
        MultiScaleOrientedFeatureCore.FeatureSet right =
                MultiScaleOrientedFeatureCore.detect(transformed, width, height, 1000);
        require(left.features.size() >= 120, "left features " + left.features.size());
        require(right.features.size() >= 90, "right features " + right.features.size());
        require(nonZeroLevels(left.featuresPerLevel) >= 2, "multiscale left");
        require(nonZeroLevels(right.featuresPerLevel) >= 2, "multiscale right");

        MultiScaleOrientedFeatureCore.MatchResult matches =
                MultiScaleOrientedFeatureCore.match(left, right);
        require(matches.observations.size() >= 18,
                "multiscale observations " + matches.observations.size());
        require(matches.roiCoverage >= 0.10, "coverage " + matches.roiCoverage);
        require(matches.mutualFraction >= 0.12, "mutual " + matches.mutualFraction);
        require(matches.p75SecondBestRatio < 0.97, "ratio " + matches.p75SecondBestRatio);
        require(matches.strictObservations > 0, "strict observations recorded");

        MultiScaleGeometricDiagnosticsCore.PairDiagnostic accepted =
                MultiScaleGeometricDiagnosticsCore.classify(
                        1, 2, "LOW", "LOW", 1, matches,
                        true, 31, 0.41, 2.1);
        require(accepted.usable(), "usable classification " + accepted.reason);
        require(!accepted.repetitiveUnsupported, "not repetitive with geometry");
        require(accepted.strictObservations == matches.strictObservations,
                "strict evidence propagated");

        MultiScaleGeometricDiagnosticsCore.PairDiagnostic rejected =
                MultiScaleGeometricDiagnosticsCore.classify(
                        2, 3, "LOW", "HIGH", 0,
                        MultiScaleOrientedFeatureCore.MatchResult.empty("NO_FEATURES"),
                        false, 0, 0.0, Double.POSITIVE_INFINITY);
        require(!rejected.usable(), "empty rejected");

        List<Integer> nodes = Arrays.asList(1, 2, 3, 4);
        List<MultiScaleGeometricDiagnosticsCore.PairDiagnostic> edges =
                new ArrayList<MultiScaleGeometricDiagnosticsCore.PairDiagnostic>();
        edges.add(accepted);
        edges.add(MultiScaleGeometricDiagnosticsCore.classify(
                2, 3, "LOW", "HIGH", 0, matches, true, 26, 0.35, 2.5));
        edges.add(MultiScaleGeometricDiagnosticsCore.classify(
                3, 4, "HIGH", "HIGH", 1, matches, true, 24, 0.33, 2.7));
        MultiScaleGeometricDiagnosticsCore.GraphResult graph =
                MultiScaleGeometricDiagnosticsCore.graph(nodes, edges, false);
        require(graph.connected(), "graph connected " + graph.componentSizes);
        require(graph.crossRingReady(), "cross ring edge");
        MultiScaleGeometricDiagnosticsCore.PairDiagnostic bridge =
                MultiScaleGeometricDiagnosticsCore.classify(
                        4, 5, "HIGH", "LOW", 0, matches,
                        true, 10, 0.31, 0.7);
        require(bridge.diagnosticBridge(), "diagnostic bridge classification");
        List<Integer> bridgeNodes = Arrays.asList(1, 2, 3, 4, 5);
        edges.add(bridge);
        MultiScaleGeometricDiagnosticsCore.GraphResult primaryOnly =
                MultiScaleGeometricDiagnosticsCore.graph(bridgeNodes, edges, false);
        MultiScaleGeometricDiagnosticsCore.GraphResult withBridge =
                MultiScaleGeometricDiagnosticsCore.graph(bridgeNodes, edges, true);
        require(!primaryOnly.connected(), "bridge excluded from primary graph");
        require(withBridge.connected(), "bridge connects diagnostic graph");
        require(withBridge.bridgeEdges == 1, "one bridge edge");
        System.out.println("MultiScaleOrientedFeatureCoreV67Test PASS features="
                + left.features.size() + "/" + right.features.size()
                + " matches=" + matches.observations.size()
                + " coverage=" + matches.roiCoverage);
    }

    private static int nonZeroLevels(int[] values) {
        int count = 0;
        for (int value : values) if (value > 0) count++;
        return count;
    }

    private static byte[] synthetic(int width, int height) {
        byte[] image = new byte[width * height];
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                int checker = ((x / 17) + (y / 13)) & 1;
                int wave = (int) Math.round(35.0 * Math.sin(x * 0.085)
                        + 28.0 * Math.cos(y * 0.071));
                int radial = (int) Math.round(24.0 * Math.sin(
                        Math.hypot(x - width * 0.52, y - height * 0.48) * 0.11));
                int value = 112 + checker * 52 + wave + radial;
                image[y * width + x] = (byte) clamp(value, 0, 255);
            }
        }
        for (int i = 0; i < 70; i++) {
            int cx = 18 + (i * 53) % (width - 36);
            int cy = 18 + (i * 97) % (height - 36);
            int radius = 3 + i % 8;
            drawDisc(image, width, height, cx, cy, radius,
                    i % 2 == 0 ? 232 : 24);
        }
        return image;
    }

    private static byte[] transform(byte[] source, int width, int height,
                                    double angle, double scale,
                                    double tx, double ty) {
        byte[] target = new byte[width * height];
        double cos = Math.cos(angle);
        double sin = Math.sin(angle);
        double cx = width * 0.5;
        double cy = height * 0.5;
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                double dx = x - cx - tx;
                double dy = y - cy - ty;
                double sx = (cos * dx + sin * dy) / scale + cx;
                double sy = (-sin * dx + cos * dy) / scale + cy;
                int value = bilinear(source, width, height, sx, sy);
                target[y * width + x] = (byte) value;
            }
        }
        return target;
    }

    private static int bilinear(byte[] image, int width, int height,
                                double x, double y) {
        if (x < 0 || y < 0 || x >= width - 1 || y >= height - 1) return 96;
        int x0 = (int) Math.floor(x);
        int y0 = (int) Math.floor(y);
        int x1 = x0 + 1;
        int y1 = y0 + 1;
        double fx = x - x0;
        double fy = y - y0;
        double top = value(image, width, x0, y0) * (1.0 - fx)
                + value(image, width, x1, y0) * fx;
        double bottom = value(image, width, x0, y1) * (1.0 - fx)
                + value(image, width, x1, y1) * fx;
        return clamp((int) Math.round(top * (1.0 - fy) + bottom * fy), 0, 255);
    }

    private static void drawDisc(byte[] image, int width, int height,
                                 int cx, int cy, int radius, int value) {
        int r2 = radius * radius;
        for (int y = Math.max(0, cy - radius); y <= Math.min(height - 1, cy + radius); y++) {
            for (int x = Math.max(0, cx - radius); x <= Math.min(width - 1, cx + radius); x++) {
                int dx = x - cx;
                int dy = y - cy;
                if (dx * dx + dy * dy <= r2) image[y * width + x] = (byte) value;
            }
        }
    }

    private static int value(byte[] image, int width, int x, int y) {
        return image[y * width + x] & 0xff;
    }

    private static int clamp(int value, int minimum, int maximum) {
        return Math.max(minimum, Math.min(maximum, value));
    }

    private static void require(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }
}

package cl.skm.pulleyai;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

/** Deterministic ASCII export for non-metric seed point clouds. */
public final class PointCloudExportCore {
    private PointCloudExportCore() {}

    public static final class Point {
        public final double x;
        public final double y;
        public final double z;
        public final double errorPx;
        public final double parallaxDegrees;

        public Point(double x, double y, double z, double errorPx, double parallaxDegrees) {
            this.x = finite(x);
            this.y = finite(y);
            this.z = finite(z);
            this.errorPx = finiteOr(errorPx, -1.0);
            this.parallaxDegrees = finiteOr(parallaxDegrees, -1.0);
        }
    }

    public static String toPly(List<Point> input) {
        List<Point> points = safe(input);
        double maxError = maxError(points);
        StringBuilder text = new StringBuilder(256 + points.size() * 96);
        text.append("ply\n")
                .append("format ascii 1.0\n")
                .append("comment SKM Polea AI alpha54 seed cloud; coordinates are not metric\n")
                .append("element vertex ").append(points.size()).append('\n')
                .append("property double x\n")
                .append("property double y\n")
                .append("property double z\n")
                .append("property uchar red\n")
                .append("property uchar green\n")
                .append("property uchar blue\n")
                .append("property double reprojection_error_px\n")
                .append("property double parallax_degrees\n")
                .append("end_header\n");
        for (Point point : points) {
            int[] rgb = errorColor(point.errorPx, maxError);
            text.append(number(point.x)).append(' ')
                    .append(number(point.y)).append(' ')
                    .append(number(point.z)).append(' ')
                    .append(rgb[0]).append(' ')
                    .append(rgb[1]).append(' ')
                    .append(rgb[2]).append(' ')
                    .append(number(point.errorPx)).append(' ')
                    .append(number(point.parallaxDegrees)).append('\n');
        }
        return text.toString();
    }

    public static String toXyz(List<Point> input) {
        List<Point> points = safe(input);
        StringBuilder text = new StringBuilder(160 + points.size() * 80);
        text.append("# SKM Polea AI alpha54 seed cloud; coordinates are not metric\n")
                .append("# x y z reprojection_error_px parallax_degrees\n");
        for (Point point : points) {
            text.append(number(point.x)).append(' ')
                    .append(number(point.y)).append(' ')
                    .append(number(point.z)).append(' ')
                    .append(number(point.errorPx)).append(' ')
                    .append(number(point.parallaxDegrees)).append('\n');
        }
        return text.toString();
    }

    public static int[] errorColor(double errorPx, double maxErrorPx) {
        if (!Double.isFinite(errorPx) || errorPx < 0.0) return new int[]{165, 188, 204};
        double denominator = Double.isFinite(maxErrorPx) && maxErrorPx > 1e-9
                ? maxErrorPx : Math.max(1.0, errorPx);
        double t = clamp(errorPx / denominator, 0.0, 1.0);
        if (t <= 0.5) {
            double local = t * 2.0;
            return new int[]{
                    (int) Math.round(45 + 210 * local),
                    (int) Math.round(210 + 30 * local),
                    (int) Math.round(120 - 80 * local)};
        }
        double local = (t - 0.5) * 2.0;
        return new int[]{255, (int) Math.round(240 - 185 * local), (int) Math.round(40 - 20 * local)};
    }

    private static List<Point> safe(List<Point> input) {
        if (input == null || input.isEmpty()) return Collections.emptyList();
        List<Point> output = new ArrayList<Point>(input.size());
        for (Point point : input) if (point != null) output.add(point);
        return output;
    }

    private static double maxError(List<Point> points) {
        double max = 0.0;
        for (Point point : points) {
            if (Double.isFinite(point.errorPx) && point.errorPx >= 0.0) max = Math.max(max, point.errorPx);
        }
        return max;
    }

    private static String number(double value) {
        if (!Double.isFinite(value)) return "0";
        return String.format(Locale.ROOT, "%.9g", value);
    }

    private static double finite(double value) {
        return Double.isFinite(value) ? value : 0.0;
    }

    private static double finiteOr(double value, double fallback) {
        return Double.isFinite(value) ? value : fallback;
    }

    private static double clamp(double value, double min, double max) {
        return Math.max(min, Math.min(max, value));
    }
}

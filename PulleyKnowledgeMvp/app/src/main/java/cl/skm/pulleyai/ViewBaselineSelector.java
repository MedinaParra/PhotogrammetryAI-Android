package cl.skm.pulleyai;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

/** Selects useful image pairs before feature matching, based on pose and quality metadata. */
public final class ViewBaselineSelector {
    public static final class View {
        public final String id;
        public final double yawDegrees;
        public final double pitchDegrees;
        public final double quality;
        public final String band;

        public View(String id, double yawDegrees, double pitchDegrees, double quality, String band) {
            this.id = id == null ? "" : id;
            this.yawDegrees = CoveragePlanner.normalizeYaw(yawDegrees);
            this.pitchDegrees = pitchDegrees;
            this.quality = clamp01(quality);
            this.band = band == null ? "LOW" : band;
        }
    }

    public static final class Pair {
        public final View first;
        public final View second;
        public final double angularSeparationDegrees;
        public final double score;

        Pair(View first, View second, double angularSeparationDegrees, double score) {
            this.first = first;
            this.second = second;
            this.angularSeparationDegrees = angularSeparationDegrees;
            this.score = score;
        }
    }

    private ViewBaselineSelector() {
    }

    public static List<Pair> select(List<View> views, int limit) {
        List<Pair> pairs = new ArrayList<Pair>();
        if (views == null || views.size() < 2 || limit <= 0) return pairs;
        for (int i = 0; i < views.size(); i++) {
            for (int j = i + 1; j < views.size(); j++) {
                View left = views.get(i);
                View right = views.get(j);
                double separation = circularDifference(left.yawDegrees, right.yawDegrees);
                if (separation < 8.0 || separation > 80.0) continue;
                double desired = 32.0;
                double baselineScore = Math.exp(-Math.abs(separation - desired) / 24.0);
                double qualityScore = Math.sqrt(left.quality * right.quality);
                double bandScore = left.band.equals(right.band) ? 1.0 : 0.82;
                double pitchPenalty = Math.exp(-Math.abs(left.pitchDegrees - right.pitchDegrees) / 35.0);
                double score = baselineScore * qualityScore * bandScore * pitchPenalty;
                pairs.add(new Pair(left, right, separation, score));
            }
        }
        Collections.sort(pairs, new Comparator<Pair>() {
            @Override public int compare(Pair left, Pair right) {
                return Double.compare(right.score, left.score);
            }
        });
        if (pairs.size() > limit) return new ArrayList<Pair>(pairs.subList(0, limit));
        return pairs;
    }

    public static double circularDifference(double left, double right) {
        double difference = Math.abs(CoveragePlanner.normalizeYaw(left) - CoveragePlanner.normalizeYaw(right));
        return Math.min(difference, 360.0 - difference);
    }

    private static double clamp01(double value) {
        if (Double.isNaN(value)) return 0.0;
        return Math.max(0.0, Math.min(1.0, value));
    }
}

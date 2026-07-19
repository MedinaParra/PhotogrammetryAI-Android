package cl.skm.pulleyai;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

/** Small dependency-free feature/correspondence core used to qualify image overlap offline. */
public final class VisualFeatureCore {
    private static final int RADIUS = 8;
    private static final int[][] PAIRS = buildPairs();

    private VisualFeatureCore() {
    }

    public static FeatureSet detect(byte[] gray, int width, int height, int maxFeatures) {
        if (gray == null || width < 24 || height < 24 || gray.length < width * height) {
            return new FeatureSet(width, height, Collections.<Feature>emptyList());
        }
        List<Candidate> candidates = new ArrayList<Candidate>();
        for (int y = RADIUS + 2; y < height - RADIUS - 2; y += 2) {
            for (int x = RADIUS + 2; x < width - RADIUS - 2; x += 2) {
                double score = harris(gray, width, x, y);
                if (score > 2_500_000.0 && localMaximum(gray, width, height, x, y, score)) {
                    candidates.add(new Candidate(x, y, score));
                }
            }
        }
        Collections.sort(candidates, new Comparator<Candidate>() {
            @Override public int compare(Candidate a, Candidate b) {
                return Double.compare(b.score, a.score);
            }
        });
        List<Feature> features = new ArrayList<Feature>();
        int limit = Math.max(20, Math.min(1200, maxFeatures));
        for (Candidate candidate : candidates) {
            if (features.size() >= limit) break;
            if (tooClose(features, candidate.x, candidate.y, 7)) continue;
            features.add(new Feature(candidate.x, candidate.y,
                    descriptor(gray, width, candidate.x, candidate.y), candidate.score));
        }
        return new FeatureSet(width, height, features);
    }

    public static PairResult match(FeatureSet left, FeatureSet right) {
        if (left == null || right == null || left.features.isEmpty() || right.features.isEmpty()) {
            return new PairResult(Collections.<Match>emptyList(), 0.0, 0.0, "NO_FEATURES");
        }
        int[] leftBestForRight = new int[right.features.size()];
        for (int j = 0; j < right.features.size(); j++) {
            leftBestForRight[j] = best(right.features.get(j), left.features).index;
        }
        List<Match> matches = new ArrayList<Match>();
        for (int i = 0; i < left.features.size(); i++) {
            Feature a = left.features.get(i);
            Best best = best(a, right.features);
            if (best.index < 0 || best.distance > 24) continue;
            if (best.secondDistance < 64 && best.distance * 100 > best.secondDistance * 82) continue;
            if (leftBestForRight[best.index] != i) continue;
            Feature b = right.features.get(best.index);
            matches.add(new Match(i, best.index, best.distance, b.x - a.x, b.y - a.y));
        }
        double dx = median(matches, true);
        double dy = median(matches, false);
        int coherent = 0;
        for (Match match : matches) {
            if (Math.abs(match.dx - dx) <= 5.0 && Math.abs(match.dy - dy) <= 5.0) coherent++;
        }
        String status = coherent >= 24 ? "STRONG" : coherent >= 10 ? "USABLE" : "WEAK";
        return new PairResult(matches, dx, dy, status);
    }

    private static Best best(Feature query, List<Feature> candidates) {
        int bestIndex = -1;
        int best = Integer.MAX_VALUE;
        int second = Integer.MAX_VALUE;
        for (int i = 0; i < candidates.size(); i++) {
            int distance = Long.bitCount(query.descriptor ^ candidates.get(i).descriptor);
            if (distance < best) {
                second = best;
                best = distance;
                bestIndex = i;
            } else if (distance < second) second = distance;
        }
        return new Best(bestIndex, best, second);
    }

    private static double harris(byte[] gray, int width, int x, int y) {
        double xx = 0.0;
        double yy = 0.0;
        double xy = 0.0;
        for (int oy = -2; oy <= 2; oy++) {
            for (int ox = -2; ox <= 2; ox++) {
                int px = x + ox;
                int py = y + oy;
                int gx = value(gray, width, px + 1, py) - value(gray, width, px - 1, py);
                int gy = value(gray, width, px, py + 1) - value(gray, width, px, py - 1);
                xx += gx * gx;
                yy += gy * gy;
                xy += gx * gy;
            }
        }
        double determinant = xx * yy - xy * xy;
        double trace = xx + yy;
        return determinant - 0.045 * trace * trace;
    }

    private static boolean localMaximum(byte[] gray, int width, int height, int x, int y, double score) {
        for (int oy = -2; oy <= 2; oy += 2) {
            for (int ox = -2; ox <= 2; ox += 2) {
                if (ox == 0 && oy == 0) continue;
                int px = x + ox;
                int py = y + oy;
                if (px <= RADIUS || py <= RADIUS || px >= width - RADIUS || py >= height - RADIUS) continue;
                if (harris(gray, width, px, py) > score) return false;
            }
        }
        return true;
    }

    private static long descriptor(byte[] gray, int width, int x, int y) {
        long bits = 0L;
        for (int i = 0; i < PAIRS.length; i++) {
            int[] pair = PAIRS[i];
            int a = value(gray, width, x + pair[0], y + pair[1]);
            int b = value(gray, width, x + pair[2], y + pair[3]);
            if (a < b) bits |= 1L << i;
        }
        return bits;
    }

    private static int[][] buildPairs() {
        int[][] pairs = new int[64][4];
        long state = 0x5deece66dL;
        for (int i = 0; i < pairs.length; i++) {
            for (int j = 0; j < 4; j++) {
                state = (state * 25214903917L + 11L) & ((1L << 48) - 1L);
                pairs[i][j] = (int) ((state >>> 16) % (2 * RADIUS + 1)) - RADIUS;
            }
        }
        return pairs;
    }

    private static boolean tooClose(List<Feature> features, int x, int y, int radius) {
        int squared = radius * radius;
        for (Feature feature : features) {
            int dx = feature.x - x;
            int dy = feature.y - y;
            if (dx * dx + dy * dy < squared) return true;
        }
        return false;
    }

    private static int value(byte[] gray, int width, int x, int y) {
        return gray[y * width + x] & 0xff;
    }

    private static double median(List<Match> matches, boolean x) {
        if (matches.isEmpty()) return 0.0;
        List<Double> values = new ArrayList<Double>(matches.size());
        for (Match match : matches) values.add(x ? match.dx : match.dy);
        Collections.sort(values);
        int middle = values.size() / 2;
        return values.size() % 2 == 0
                ? (values.get(middle - 1) + values.get(middle)) / 2.0
                : values.get(middle);
    }

    private static final class Candidate {
        final int x;
        final int y;
        final double score;
        Candidate(int x, int y, double score) { this.x = x; this.y = y; this.score = score; }
    }

    private static final class Best {
        final int index;
        final int distance;
        final int secondDistance;
        Best(int index, int distance, int secondDistance) {
            this.index = index;
            this.distance = distance;
            this.secondDistance = secondDistance;
        }
    }

    public static final class Feature {
        public final int x;
        public final int y;
        public final long descriptor;
        public final double score;
        Feature(int x, int y, long descriptor, double score) {
            this.x = x;
            this.y = y;
            this.descriptor = descriptor;
            this.score = score;
        }
    }

    public static final class FeatureSet {
        public final int width;
        public final int height;
        public final List<Feature> features;
        FeatureSet(int width, int height, List<Feature> features) {
            this.width = width;
            this.height = height;
            this.features = Collections.unmodifiableList(new ArrayList<Feature>(features));
        }
    }

    public static final class Match {
        public final int leftIndex;
        public final int rightIndex;
        public final int distance;
        public final double dx;
        public final double dy;
        Match(int leftIndex, int rightIndex, int distance, double dx, double dy) {
            this.leftIndex = leftIndex;
            this.rightIndex = rightIndex;
            this.distance = distance;
            this.dx = dx;
            this.dy = dy;
        }
    }

    public static final class PairResult {
        public final List<Match> matches;
        public final double medianDx;
        public final double medianDy;
        public final String status;
        PairResult(List<Match> matches, double medianDx, double medianDy, String status) {
            this.matches = Collections.unmodifiableList(new ArrayList<Match>(matches));
            this.medianDx = medianDx;
            this.medianDy = medianDy;
            this.status = status;
        }
    }
}

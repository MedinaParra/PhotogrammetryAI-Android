package cl.skm.pulleyai;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** Deterministic bounded homography RANSAC and competition against fundamental support. */
public final class HomographyModelCompetitionCore {
    private HomographyModelCompetitionCore() {}

    public static Result evaluate(List<PointPair> source, double thresholdPx,
                                  int requestedIterations, int fundamentalInliers,
                                  double fundamentalRmsPx) {
        List<PointPair> pairs = validPairs(source);
        if (pairs.size() < 4) return Result.failed("INSUFFICIENT_PAIRS", pairs.size());
        double threshold = Math.max(0.5, Math.min(12.0, thresholdPx));
        int iterations = Math.max(32, Math.min(600, requestedIterations));
        Candidate best = null;
        long state = 0x51f15eL + pairs.size() * 104729L;
        for (int iteration = 0; iteration < iterations; iteration++) {
            int[] sample = sample4(pairs.size(), state + iteration * 2654435761L);
            double[] h = solve(pairs, sample);
            if (h == null) continue;
            Candidate candidate = score(h, pairs, threshold);
            if (best == null || candidate.betterThan(best)) best = candidate;
        }
        if (best == null || best.inliers.size() < 4) {
            return Result.failed("HOMOGRAPHY_UNSOLVED", pairs.size());
        }
        double[] refined = solveLeastSquares(pairs, best.inliers);
        Candidate finalCandidate = refined == null ? best : score(refined, pairs, threshold);
        int total = pairs.size();
        double homographyRatio = finalCandidate.inliers.size() / (double) total;
        double fundamentalRatio = Math.max(0, Math.min(total, fundamentalInliers)) / (double) total;
        double supportAdvantage = homographyRatio - fundamentalRatio;
        double rmsAdvantage = Double.isFinite(fundamentalRmsPx)
                ? fundamentalRmsPx - finalCandidate.rmsPx : Double.NaN;
        String status = homographyRatio >= 0.85 && supportAdvantage >= 0.12
                && (!Double.isFinite(fundamentalRmsPx) || finalCandidate.rmsPx <= fundamentalRmsPx * 0.85)
                ? "PLANAR_DOMINANT"
                : homographyRatio >= 0.72 ? "PLANAR_POSSIBLE" : "VOLUMETRIC_SUPPORTED";
        return new Result(true, status, total, finalCandidate.inliers.size(),
                homographyRatio, fundamentalRatio, supportAdvantage,
                finalCandidate.rmsPx, rmsAdvantage, finalCandidate.homography);
    }

    private static List<PointPair> validPairs(List<PointPair> source) {
        List<PointPair> result = new ArrayList<PointPair>();
        if (source != null) for (PointPair pair : source) if (pair != null && pair.valid()) result.add(pair);
        return result;
    }

    private static int[] sample4(int size, long seed) {
        int[] out = new int[4];
        long value = seed;
        for (int i = 0; i < 4; i++) {
            int candidate;
            boolean duplicate;
            do {
                value = value * 6364136223846793005L + 1442695040888963407L;
                candidate = (int) Math.floorMod(value >>> 16, size);
                duplicate = false;
                for (int j = 0; j < i; j++) if (out[j] == candidate) duplicate = true;
            } while (duplicate);
            out[i] = candidate;
        }
        return out;
    }

    private static double[] solve(List<PointPair> pairs, int[] indices) {
        double[][] a = new double[8][8];
        double[] b = new double[8];
        for (int i = 0; i < 4; i++) {
            PointPair p = pairs.get(indices[i]);
            fillRows(a, b, i * 2, p);
        }
        return solveLinear(a, b);
    }

    private static double[] solveLeastSquares(List<PointPair> pairs, List<Integer> inliers) {
        double[][] normal = new double[8][8];
        double[] rhs = new double[8];
        for (int index : inliers) {
            PointPair p = pairs.get(index);
            double[][] rows = rows(p);
            double[] values = new double[]{p.x2, p.y2};
            for (int r = 0; r < 2; r++) {
                for (int i = 0; i < 8; i++) {
                    rhs[i] += rows[r][i] * values[r];
                    for (int j = 0; j < 8; j++) normal[i][j] += rows[r][i] * rows[r][j];
                }
            }
        }
        for (int i = 0; i < 8; i++) normal[i][i] += 1e-9;
        return solveLinear(normal, rhs);
    }

    private static void fillRows(double[][] a, double[] b, int row, PointPair p) {
        double[][] rows = rows(p);
        System.arraycopy(rows[0], 0, a[row], 0, 8);
        System.arraycopy(rows[1], 0, a[row + 1], 0, 8);
        b[row] = p.x2;
        b[row + 1] = p.y2;
    }

    private static double[][] rows(PointPair p) {
        return new double[][]{
                {p.x1, p.y1, 1, 0, 0, 0, -p.x1 * p.x2, -p.y1 * p.x2},
                {0, 0, 0, p.x1, p.y1, 1, -p.x1 * p.y2, -p.y1 * p.y2}
        };
    }

    private static Candidate score(double[] h, List<PointPair> pairs, double threshold) {
        List<Integer> inliers = new ArrayList<Integer>();
        double squared = 0.0;
        for (int i = 0; i < pairs.size(); i++) {
            double error = error(h, pairs.get(i));
            if (Double.isFinite(error) && error <= threshold) {
                inliers.add(i);
                squared += error * error;
            }
        }
        double rms = inliers.isEmpty() ? Double.POSITIVE_INFINITY : Math.sqrt(squared / inliers.size());
        return new Candidate(h, inliers, rms);
    }

    private static double error(double[] h, PointPair p) {
        double denominator = h[6] * p.x1 + h[7] * p.y1 + 1.0;
        if (!Double.isFinite(denominator) || Math.abs(denominator) < 1e-10) return Double.POSITIVE_INFINITY;
        double x = (h[0] * p.x1 + h[1] * p.y1 + h[2]) / denominator;
        double y = (h[3] * p.x1 + h[4] * p.y1 + h[5]) / denominator;
        return Math.hypot(x - p.x2, y - p.y2);
    }

    private static double[] solveLinear(double[][] sourceA, double[] sourceB) {
        int n = sourceB.length;
        double[][] m = new double[n][n + 1];
        for (int i = 0; i < n; i++) {
            System.arraycopy(sourceA[i], 0, m[i], 0, n);
            m[i][n] = sourceB[i];
        }
        for (int column = 0; column < n; column++) {
            int pivot = column;
            for (int row = column + 1; row < n; row++) {
                if (Math.abs(m[row][column]) > Math.abs(m[pivot][column])) pivot = row;
            }
            if (Math.abs(m[pivot][column]) < 1e-11) return null;
            double[] swap = m[column]; m[column] = m[pivot]; m[pivot] = swap;
            double divisor = m[column][column];
            for (int j = column; j <= n; j++) m[column][j] /= divisor;
            for (int row = 0; row < n; row++) if (row != column) {
                double factor = m[row][column];
                for (int j = column; j <= n; j++) m[row][j] -= factor * m[column][j];
            }
        }
        double[] result = new double[n];
        for (int i = 0; i < n; i++) result[i] = m[i][n];
        return result;
    }

    public static final class PointPair {
        public final double x1, y1, x2, y2;
        public PointPair(double x1, double y1, double x2, double y2) {
            this.x1 = x1; this.y1 = y1; this.x2 = x2; this.y2 = y2;
        }
        boolean valid() {
            return Double.isFinite(x1) && Double.isFinite(y1)
                    && Double.isFinite(x2) && Double.isFinite(y2);
        }
    }

    public static final class Result {
        public final boolean solved;
        public final String status;
        public final int pairCount, homographyInliers;
        public final double homographySupportRatio, fundamentalSupportRatio;
        public final double supportAdvantage, homographyRmsPx, rmsAdvantagePx;
        public final double[] homography;

        Result(boolean solved, String status, int pairCount, int homographyInliers,
               double homographySupportRatio, double fundamentalSupportRatio,
               double supportAdvantage, double homographyRmsPx, double rmsAdvantagePx,
               double[] homography) {
            this.solved = solved; this.status = status; this.pairCount = pairCount;
            this.homographyInliers = homographyInliers;
            this.homographySupportRatio = homographySupportRatio;
            this.fundamentalSupportRatio = fundamentalSupportRatio;
            this.supportAdvantage = supportAdvantage;
            this.homographyRmsPx = homographyRmsPx;
            this.rmsAdvantagePx = rmsAdvantagePx;
            this.homography = homography == null ? null : homography.clone();
        }

        static Result failed(String status, int count) {
            return new Result(false, status, count, 0, Double.NaN, Double.NaN,
                    Double.NaN, Double.POSITIVE_INFINITY, Double.NaN, null);
        }

        public double dominanceRatio() {
            return solved ? homographySupportRatio : Double.NaN;
        }
    }

    private static final class Candidate {
        final double[] homography;
        final List<Integer> inliers;
        final double rmsPx;
        Candidate(double[] homography, List<Integer> inliers, double rmsPx) {
            this.homography = homography.clone();
            this.inliers = Collections.unmodifiableList(new ArrayList<Integer>(inliers));
            this.rmsPx = rmsPx;
        }
        boolean betterThan(Candidate other) {
            return inliers.size() > other.inliers.size()
                    || (inliers.size() == other.inliers.size() && rmsPx < other.rmsPx);
        }
    }
}

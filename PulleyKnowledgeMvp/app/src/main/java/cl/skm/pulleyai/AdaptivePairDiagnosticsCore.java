package cl.skm.pulleyai;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Diagnostic matcher used when reprocessing exported capture ZIPs.
 * It records why a pair is accepted or rejected instead of collapsing every weak
 * industrial pair into a single repetitive-ambiguity flag.
 */
public final class AdaptivePairDiagnosticsCore {
    private static final int GRID_X = 6;
    private static final int GRID_Y = 4;
    private static final int MAX_DESCRIPTOR_DISTANCE = 34;
    private static final double MAX_RATIO = 0.92;

    private AdaptivePairDiagnosticsCore() {}

    public static MatchResult match(VisualFeatureCore.FeatureSet left,
                                    VisualFeatureCore.FeatureSet right) {
        if (left == null || right == null || left.features.isEmpty() || right.features.isEmpty()) {
            return MatchResult.empty("NO_FEATURES");
        }

        Best[] reverse = new Best[right.features.size()];
        for (int j = 0; j < right.features.size(); j++) {
            reverse[j] = best(right.features.get(j), left.features);
        }

        List<Double> ratios = new ArrayList<Double>();
        List<Observation> observations = new ArrayList<Observation>();
        int ratioPassed = 0;
        for (int i = 0; i < left.features.size(); i++) {
            VisualFeatureCore.Feature query = left.features.get(i);
            Best best = best(query, right.features);
            if (best.index < 0 || best.distance == Integer.MAX_VALUE) continue;
            double ratio = best.secondDistance == Integer.MAX_VALUE || best.secondDistance <= 0
                    ? 1.0 : best.distance / (double) best.secondDistance;
            ratios.add(ratio);
            boolean passesRatio = best.distance <= MAX_DESCRIPTOR_DISTANCE && ratio <= MAX_RATIO;
            if (!passesRatio) continue;
            ratioPassed++;
            Best back = reverse[best.index];
            if (back == null || back.index != i) continue;
            VisualFeatureCore.Feature target = right.features.get(best.index);
            observations.add(new Observation(i, best.index, query.x, query.y,
                    target.x, target.y, best.distance, ratio));
        }

        Collections.sort(ratios);
        double p75Ratio = percentile(ratios, 0.75);
        double mutualFraction = ratioPassed == 0 ? 0.0
                : observations.size() / (double) ratioPassed;
        double roiCoverage = relativeCoverage(left, right, observations);
        return new MatchResult(observations, ratioPassed, p75Ratio,
                mutualFraction, roiCoverage, "COMPLETE");
    }

    public static PairDiagnostic classify(int leftSequence, int rightSequence,
                                          String leftBand, String rightBand,
                                          int sectorGap, MatchResult matches,
                                          boolean fundamentalSolved,
                                          int fundamentalInliers,
                                          double fundamentalInlierRatio,
                                          double fundamentalRmsPx) {
        MatchResult safe = matches == null ? MatchResult.empty("NO_MATCH_RESULT") : matches;
        int observationCount = safe.observations.size();
        boolean repetitiveUnsupported = safe.p75SecondBestRatio >= 0.94
                && safe.mutualFraction < 0.25
                && safe.roiCoverage < 0.12;

        String status = "WEAK";
        String reason;
        if (observationCount < 8) {
            reason = "OBSERVATIONS_LT_8";
        } else if (!fundamentalSolved) {
            reason = "FUNDAMENTAL_FAILED";
        } else if (repetitiveUnsupported) {
            reason = "REPETITIVE_AND_GEOMETRICALLY_UNSUPPORTED";
        } else if (fundamentalInliers >= 30 && fundamentalInlierRatio >= 0.35
                && finiteAtMost(fundamentalRmsPx, 2.5)
                && (safe.roiCoverage >= 0.15 || fundamentalInliers >= 50)) {
            status = "STRONG";
            reason = "ACCEPTED_STRONG";
        } else if (fundamentalInliers >= 12 && fundamentalInlierRatio >= 0.20
                && finiteAtMost(fundamentalRmsPx, 3.5)
                && (safe.roiCoverage >= 0.08 || fundamentalInliers >= 24)) {
            status = "USABLE";
            reason = "ACCEPTED_USABLE";
        } else if (fundamentalInliers < 12) {
            reason = "INLIERS_LT_12";
        } else if (fundamentalInlierRatio < 0.20) {
            reason = "INLIER_RATIO_LT_020";
        } else if (!finiteAtMost(fundamentalRmsPx, 3.5)) {
            reason = "FUNDAMENTAL_RMS_GT_35";
        } else {
            reason = "ROI_SUPPORT_TOO_LOW";
        }

        return new PairDiagnostic(leftSequence, rightSequence,
                normalizeBand(leftBand), normalizeBand(rightBand),
                sectorGap, observationCount, safe.ratioPassed,
                safe.p75SecondBestRatio, safe.mutualFraction, safe.roiCoverage,
                fundamentalSolved, fundamentalInliers,
                fundamentalInlierRatio, fundamentalRmsPx,
                repetitiveUnsupported, status, reason);
    }

    public static GraphResult graph(List<Integer> frameSequences,
                                    List<PairDiagnostic> diagnostics) {
        List<Integer> nodes = new ArrayList<Integer>();
        if (frameSequences != null) {
            Set<Integer> unique = new HashSet<Integer>(frameSequences);
            nodes.addAll(unique);
            Collections.sort(nodes);
        }
        Map<Integer,Set<Integer>> adjacency = new HashMap<Integer,Set<Integer>>();
        for (int sequence : nodes) adjacency.put(sequence, new HashSet<Integer>());
        int acceptedEdges = 0;
        if (diagnostics != null) {
            for (PairDiagnostic pair : diagnostics) {
                if (pair == null || !pair.usable()) continue;
                if (!adjacency.containsKey(pair.leftSequence)
                        || !adjacency.containsKey(pair.rightSequence)) continue;
                if (adjacency.get(pair.leftSequence).add(pair.rightSequence)) {
                    adjacency.get(pair.rightSequence).add(pair.leftSequence);
                    acceptedEdges++;
                }
            }
        }

        Set<Integer> visited = new HashSet<Integer>();
        List<Integer> componentSizes = new ArrayList<Integer>();
        for (int node : nodes) {
            if (visited.contains(node)) continue;
            int size = 0;
            ArrayDeque<Integer> queue = new ArrayDeque<Integer>();
            queue.add(node);
            visited.add(node);
            while (!queue.isEmpty()) {
                int current = queue.removeFirst();
                size++;
                for (int next : adjacency.get(current)) {
                    if (visited.add(next)) queue.addLast(next);
                }
            }
            componentSizes.add(size);
        }
        Collections.sort(componentSizes, Collections.reverseOrder());
        int largest = componentSizes.isEmpty() ? 0 : componentSizes.get(0);
        return new GraphResult(nodes.size(), acceptedEdges,
                componentSizes.size(), largest, componentSizes);
    }

    private static String normalizeBand(String band) {
        return "HIGH".equals(band) ? "HIGH" : "LOW";
    }

    private static boolean finiteAtMost(double value, double max) {
        return Double.isFinite(value) && value <= max;
    }

    private static Best best(VisualFeatureCore.Feature query,
                             List<VisualFeatureCore.Feature> candidates) {
        int bestIndex = -1;
        int bestDistance = Integer.MAX_VALUE;
        int secondDistance = Integer.MAX_VALUE;
        for (int i = 0; i < candidates.size(); i++) {
            int distance = Long.bitCount(query.descriptor ^ candidates.get(i).descriptor);
            if (distance < bestDistance) {
                secondDistance = bestDistance;
                bestDistance = distance;
                bestIndex = i;
            } else if (distance < secondDistance) {
                secondDistance = distance;
            }
        }
        return new Best(bestIndex, bestDistance, secondDistance);
    }

    private static double relativeCoverage(VisualFeatureCore.FeatureSet left,
                                           VisualFeatureCore.FeatureSet right,
                                           List<Observation> observations) {
        if (observations.isEmpty()) return 0.0;
        boolean[] leftSupport = featureCells(left);
        boolean[] rightSupport = featureCells(right);
        boolean[] leftMatches = new boolean[GRID_X * GRID_Y];
        boolean[] rightMatches = new boolean[GRID_X * GRID_Y];
        for (Observation observation : observations) {
            leftMatches[cell(observation.x, observation.y, left.width, left.height)] = true;
            rightMatches[cell(observation.u, observation.v, right.width, right.height)] = true;
        }
        int available = Math.max(1, Math.min(count(leftSupport), count(rightSupport)));
        int covered = Math.min(count(leftMatches), count(rightMatches));
        return Math.max(0.0, Math.min(1.0, covered / (double) available));
    }

    private static boolean[] featureCells(VisualFeatureCore.FeatureSet set) {
        boolean[] cells = new boolean[GRID_X * GRID_Y];
        for (VisualFeatureCore.Feature feature : set.features) {
            cells[cell(feature.x, feature.y, set.width, set.height)] = true;
        }
        return cells;
    }

    private static int cell(double x, double y, int width, int height) {
        int column = Math.min(GRID_X - 1, Math.max(0,
                (int) Math.floor(x * GRID_X / Math.max(1.0, width))));
        int row = Math.min(GRID_Y - 1, Math.max(0,
                (int) Math.floor(y * GRID_Y / Math.max(1.0, height))));
        return row * GRID_X + column;
    }

    private static int count(boolean[] values) {
        int count = 0;
        for (boolean value : values) if (value) count++;
        return count;
    }

    private static double percentile(List<Double> sorted, double q) {
        if (sorted == null || sorted.isEmpty()) return 1.0;
        double position = Math.max(0.0, Math.min(1.0, q)) * (sorted.size() - 1);
        int low = (int) Math.floor(position);
        int high = (int) Math.ceil(position);
        if (low == high) return sorted.get(low);
        double fraction = position - low;
        return sorted.get(low) * (1.0 - fraction) + sorted.get(high) * fraction;
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

    public static final class Observation {
        public final int leftIndex, rightIndex;
        public final double x, y, u, v;
        public final int descriptorDistance;
        public final double secondBestRatio;
        Observation(int leftIndex, int rightIndex,
                    double x, double y, double u, double v,
                    int descriptorDistance, double secondBestRatio) {
            this.leftIndex = leftIndex;
            this.rightIndex = rightIndex;
            this.x = x;
            this.y = y;
            this.u = u;
            this.v = v;
            this.descriptorDistance = descriptorDistance;
            this.secondBestRatio = secondBestRatio;
        }
    }

    public static final class MatchResult {
        public final List<Observation> observations;
        public final int ratioPassed;
        public final double p75SecondBestRatio;
        public final double mutualFraction;
        public final double roiCoverage;
        public final String status;

        MatchResult(List<Observation> observations, int ratioPassed,
                    double p75SecondBestRatio, double mutualFraction,
                    double roiCoverage, String status) {
            this.observations = Collections.unmodifiableList(
                    new ArrayList<Observation>(observations));
            this.ratioPassed = ratioPassed;
            this.p75SecondBestRatio = p75SecondBestRatio;
            this.mutualFraction = mutualFraction;
            this.roiCoverage = roiCoverage;
            this.status = status;
        }

        static MatchResult empty(String status) {
            return new MatchResult(Collections.<Observation>emptyList(), 0,
                    1.0, 0.0, 0.0, status);
        }
    }

    public static final class PairDiagnostic {
        public final int leftSequence, rightSequence;
        public final String leftBand, rightBand;
        public final int sectorGap;
        public final int observations, ratioPassed;
        public final double p75SecondBestRatio, mutualFraction, roiCoverage;
        public final boolean fundamentalSolved;
        public final int fundamentalInliers;
        public final double fundamentalInlierRatio, fundamentalRmsPx;
        public final boolean repetitiveUnsupported;
        public final String status, reason;

        PairDiagnostic(int leftSequence, int rightSequence,
                       String leftBand, String rightBand, int sectorGap,
                       int observations, int ratioPassed,
                       double p75SecondBestRatio, double mutualFraction,
                       double roiCoverage, boolean fundamentalSolved,
                       int fundamentalInliers, double fundamentalInlierRatio,
                       double fundamentalRmsPx, boolean repetitiveUnsupported,
                       String status, String reason) {
            this.leftSequence = leftSequence;
            this.rightSequence = rightSequence;
            this.leftBand = leftBand;
            this.rightBand = rightBand;
            this.sectorGap = sectorGap;
            this.observations = observations;
            this.ratioPassed = ratioPassed;
            this.p75SecondBestRatio = p75SecondBestRatio;
            this.mutualFraction = mutualFraction;
            this.roiCoverage = roiCoverage;
            this.fundamentalSolved = fundamentalSolved;
            this.fundamentalInliers = fundamentalInliers;
            this.fundamentalInlierRatio = fundamentalInlierRatio;
            this.fundamentalRmsPx = fundamentalRmsPx;
            this.repetitiveUnsupported = repetitiveUnsupported;
            this.status = status;
            this.reason = reason;
        }

        public boolean usable() {
            return "USABLE".equals(status) || "STRONG".equals(status);
        }
    }

    public static final class GraphResult {
        public final int nodeCount, acceptedEdges, componentCount, largestComponent;
        public final List<Integer> componentSizes;
        GraphResult(int nodeCount, int acceptedEdges, int componentCount,
                    int largestComponent, List<Integer> componentSizes) {
            this.nodeCount = nodeCount;
            this.acceptedEdges = acceptedEdges;
            this.componentCount = componentCount;
            this.largestComponent = largestComponent;
            this.componentSizes = Collections.unmodifiableList(
                    new ArrayList<Integer>(componentSizes));
        }

        public boolean connected() {
            return nodeCount > 0 && componentCount == 1 && largestComponent == nodeCount;
        }
    }
}

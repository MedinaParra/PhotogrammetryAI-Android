package cl.skm.pulleyai;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Classifies multiscale matches after fundamental-matrix verification and builds the view graph. */
public final class MultiScaleGeometricDiagnosticsCore {
    private MultiScaleGeometricDiagnosticsCore() {}

    public static PairDiagnostic classify(int leftSequence, int rightSequence,
                                          String leftBand, String rightBand,
                                          int sectorGap,
                                          MultiScaleOrientedFeatureCore.MatchResult matches,
                                          boolean fundamentalSolved,
                                          int fundamentalInliers,
                                          double fundamentalInlierRatio,
                                          double fundamentalRmsPx) {
        MultiScaleOrientedFeatureCore.MatchResult safe = matches == null
                ? MultiScaleOrientedFeatureCore.MatchResult.empty("NO_MATCH_RESULT") : matches;
        int observations = safe.observations.size();
        boolean repetitiveUnsupported = safe.p75SecondBestRatio >= 0.95
                && safe.mutualFraction < 0.20
                && safe.roiCoverage < 0.08
                && fundamentalInliers < 12;
        String status = "WEAK";
        String reason;
        if (observations < 8) {
            reason = "OBSERVATIONS_LT_8";
        } else if (!fundamentalSolved) {
            reason = "FUNDAMENTAL_FAILED";
        } else if (repetitiveUnsupported) {
            reason = "REPETITIVE_WITHOUT_GEOMETRIC_SUPPORT";
        } else if (fundamentalInliers >= 34 && fundamentalInlierRatio >= 0.34
                && finiteAtMost(fundamentalRmsPx, 2.8)
                && (safe.roiCoverage >= 0.16 || fundamentalInliers >= 58)) {
            status = "STRONG";
            reason = "ACCEPTED_MULTISCALE_STRONG";
        } else if (fundamentalInliers >= 14 && fundamentalInlierRatio >= 0.22
                && finiteAtMost(fundamentalRmsPx, 3.8)
                && (safe.roiCoverage >= 0.09 || fundamentalInliers >= 28)) {
            status = "USABLE";
            reason = "ACCEPTED_MULTISCALE_USABLE";
        } else if (fundamentalInliers >= 9 && fundamentalInlierRatio >= 0.25
                && finiteAtMost(fundamentalRmsPx, 1.2)
                && safe.roiCoverage >= 0.45 && safe.mutualFraction >= 0.50) {
            status = "BRIDGE";
            reason = "ACCEPTED_DIAGNOSTIC_BRIDGE";
        } else if (fundamentalInliers < 14) {
            reason = "INLIERS_LT_14";
        } else if (fundamentalInlierRatio < 0.22) {
            reason = "INLIER_RATIO_LT_022";
        } else if (!finiteAtMost(fundamentalRmsPx, 3.8)) {
            reason = "FUNDAMENTAL_RMS_GT_38";
        } else {
            reason = "MULTISCALE_ROI_SUPPORT_TOO_LOW";
        }
        return new PairDiagnostic(leftSequence, rightSequence,
                normalizeBand(leftBand), normalizeBand(rightBand), sectorGap,
                observations, safe.ratioPassed, safe.p75SecondBestRatio,
                safe.mutualFraction, safe.roiCoverage,
                safe.orientationConcentration, safe.medianScaleRatio,
                safe.strictObservations, safe.guidedAdded,
                safe.affineSolved, safe.affineInliers,
                safe.affineInlierRatio, safe.affineRmsPx,
                fundamentalSolved, fundamentalInliers,
                fundamentalInlierRatio, fundamentalRmsPx,
                repetitiveUnsupported, status, reason);
    }

    public static GraphResult graph(List<Integer> frameSequences,
                                    List<PairDiagnostic> diagnostics,
                                    boolean includeDiagnosticBridges) {
        List<Integer> nodes = new ArrayList<Integer>();
        if (frameSequences != null) {
            Set<Integer> unique = new HashSet<Integer>(frameSequences);
            nodes.addAll(unique);
            Collections.sort(nodes);
        }
        Map<Integer,Set<Integer>> adjacency = new HashMap<Integer,Set<Integer>>();
        for (int sequence : nodes) adjacency.put(sequence, new HashSet<Integer>());
        int acceptedEdges = 0;
        int primaryEdges = 0;
        int bridgeEdges = 0;
        int crossRingEdges = 0;
        if (diagnostics != null) {
            for (PairDiagnostic pair : diagnostics) {
                if (pair == null || !pair.graphUsable(includeDiagnosticBridges)) continue;
                if (!adjacency.containsKey(pair.leftSequence)
                        || !adjacency.containsKey(pair.rightSequence)) continue;
                if (adjacency.get(pair.leftSequence).add(pair.rightSequence)) {
                    adjacency.get(pair.rightSequence).add(pair.leftSequence);
                    acceptedEdges++;
                    if (pair.primaryUsable()) primaryEdges++;
                    else if (pair.diagnosticBridge()) bridgeEdges++;
                    if (!pair.leftBand.equals(pair.rightBand)) crossRingEdges++;
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
        return new GraphResult(nodes.size(), acceptedEdges, primaryEdges,
                bridgeEdges, crossRingEdges, componentSizes.size(), largest,
                componentSizes);
    }

    private static String normalizeBand(String band) {
        return "HIGH".equals(band) ? "HIGH" : "LOW";
    }

    private static boolean finiteAtMost(double value, double maximum) {
        return Double.isFinite(value) && value <= maximum;
    }

    public static final class PairDiagnostic {
        public final int leftSequence, rightSequence;
        public final String leftBand, rightBand;
        public final int sectorGap, observations, ratioPassed;
        public final double p75SecondBestRatio, mutualFraction, roiCoverage;
        public final double orientationConcentration, medianScaleRatio;
        public final int strictObservations, guidedAdded;
        public final boolean affineSolved;
        public final int affineInliers;
        public final double affineInlierRatio, affineRmsPx;
        public final boolean fundamentalSolved;
        public final int fundamentalInliers;
        public final double fundamentalInlierRatio, fundamentalRmsPx;
        public final boolean repetitiveUnsupported;
        public final String status, reason;

        PairDiagnostic(int leftSequence, int rightSequence,
                       String leftBand, String rightBand, int sectorGap,
                       int observations, int ratioPassed,
                       double p75SecondBestRatio, double mutualFraction,
                       double roiCoverage, double orientationConcentration,
                       double medianScaleRatio, int strictObservations,
                       int guidedAdded, boolean affineSolved, int affineInliers,
                       double affineInlierRatio, double affineRmsPx,
                       boolean fundamentalSolved,
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
            this.orientationConcentration = orientationConcentration;
            this.medianScaleRatio = medianScaleRatio;
            this.strictObservations = strictObservations;
            this.guidedAdded = guidedAdded;
            this.affineSolved = affineSolved;
            this.affineInliers = affineInliers;
            this.affineInlierRatio = affineInlierRatio;
            this.affineRmsPx = affineRmsPx;
            this.fundamentalSolved = fundamentalSolved;
            this.fundamentalInliers = fundamentalInliers;
            this.fundamentalInlierRatio = fundamentalInlierRatio;
            this.fundamentalRmsPx = fundamentalRmsPx;
            this.repetitiveUnsupported = repetitiveUnsupported;
            this.status = status;
            this.reason = reason;
        }

        public boolean primaryUsable() {
            return "USABLE".equals(status) || "STRONG".equals(status);
        }
        public boolean diagnosticBridge() {
            return "BRIDGE".equals(status);
        }
        public boolean graphUsable(boolean includeDiagnosticBridges) {
            return primaryUsable() || (includeDiagnosticBridges && diagnosticBridge());
        }
        public boolean usable() {
            return primaryUsable();
        }
    }

    public static final class GraphResult {
        public final int nodeCount, acceptedEdges, primaryEdges, bridgeEdges, crossRingEdges;
        public final int componentCount, largestComponent;
        public final List<Integer> componentSizes;
        GraphResult(int nodeCount, int acceptedEdges, int primaryEdges,
                    int bridgeEdges, int crossRingEdges, int componentCount,
                    int largestComponent, List<Integer> componentSizes) {
            this.nodeCount = nodeCount;
            this.acceptedEdges = acceptedEdges;
            this.primaryEdges = primaryEdges;
            this.bridgeEdges = bridgeEdges;
            this.crossRingEdges = crossRingEdges;
            this.componentCount = componentCount;
            this.largestComponent = largestComponent;
            this.componentSizes = Collections.unmodifiableList(
                    new ArrayList<Integer>(componentSizes));
        }
        public boolean connected() {
            return nodeCount > 0 && componentCount == 1 && largestComponent == nodeCount;
        }
        public boolean crossRingReady() {
            return crossRingEdges > 0;
        }
    }
}

package cl.ingenieria.photogrammetryai.core.fewview;

import cl.ingenieria.photogrammetryai.core.fewview.FewViewGeometry.FeatureObservation;
import cl.ingenieria.photogrammetryai.core.fewview.FewViewGeometry.FeatureTrack;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * Builds consistent multi-view tracks from pairwise descriptor matches.
 *
 * <p>The builder applies a descriptor ratio test, optional reciprocal matching and spatial quotas.
 * Union operations that would place two different keypoints from the same view in one track are
 * rejected, preventing a common source of corrupted sparse reconstructions.</p>
 */
public final class FewViewTrackBuilder {
    public static final class Config {
        private final double maximumDistanceRatio;
        private final boolean requireReciprocal;
        private final int gridColumns;
        private final int gridRows;
        private final int maximumMatchesPerCellPair;
        private final int minimumTrackViews;
        private final int maximumTrackViews;

        public Config(
                double maximumDistanceRatio,
                boolean requireReciprocal,
                int gridColumns,
                int gridRows,
                int maximumMatchesPerCellPair,
                int minimumTrackViews,
                int maximumTrackViews
        ) {
            if (!Double.isFinite(maximumDistanceRatio)
                    || maximumDistanceRatio <= 0.0
                    || maximumDistanceRatio >= 1.0) {
                throw new IllegalArgumentException("maximumDistanceRatio must be between 0 and 1");
            }
            if (gridColumns <= 0 || gridRows <= 0 || maximumMatchesPerCellPair <= 0) {
                throw new IllegalArgumentException("Grid dimensions and quotas must be positive");
            }
            if (minimumTrackViews < 2 || maximumTrackViews < minimumTrackViews) {
                throw new IllegalArgumentException("Invalid track view limits");
            }
            this.maximumDistanceRatio = maximumDistanceRatio;
            this.requireReciprocal = requireReciprocal;
            this.gridColumns = gridColumns;
            this.gridRows = gridRows;
            this.maximumMatchesPerCellPair = maximumMatchesPerCellPair;
            this.minimumTrackViews = minimumTrackViews;
            this.maximumTrackViews = maximumTrackViews;
        }

        public static Config mobileDefaults() {
            return new Config(0.78, true, 8, 6, 6, 2, 8);
        }
    }

    public static final class FeatureKey {
        private final String viewId;
        private final int featureIndex;

        public FeatureKey(String viewId, int featureIndex) {
            this.viewId = requireText(viewId, "viewId");
            if (featureIndex < 0) {
                throw new IllegalArgumentException("featureIndex must be non-negative");
            }
            this.featureIndex = featureIndex;
        }

        public String viewId() {
            return viewId;
        }

        public int featureIndex() {
            return featureIndex;
        }

        @Override
        public boolean equals(Object value) {
            if (this == value) return true;
            if (!(value instanceof FeatureKey)) return false;
            FeatureKey other = (FeatureKey) value;
            return featureIndex == other.featureIndex && viewId.equals(other.viewId);
        }

        @Override
        public int hashCode() {
            return 31 * viewId.hashCode() + featureIndex;
        }

        @Override
        public String toString() {
            return viewId + ':' + featureIndex;
        }
    }

    public static final class PairMatch {
        private final FeatureKey first;
        private final FeatureKey second;
        private final double firstU;
        private final double firstV;
        private final double secondU;
        private final double secondV;
        private final int firstImageWidth;
        private final int firstImageHeight;
        private final int secondImageWidth;
        private final int secondImageHeight;
        private final double bestDistance;
        private final double secondBestDistance;
        private final boolean reciprocal;
        private final double detectorConfidence;

        public PairMatch(
                FeatureKey first,
                FeatureKey second,
                double firstU,
                double firstV,
                double secondU,
                double secondV,
                int firstImageWidth,
                int firstImageHeight,
                int secondImageWidth,
                int secondImageHeight,
                double bestDistance,
                double secondBestDistance,
                boolean reciprocal,
                double detectorConfidence
        ) {
            this.first = Objects.requireNonNull(first, "first");
            this.second = Objects.requireNonNull(second, "second");
            if (first.viewId().equals(second.viewId())) {
                throw new IllegalArgumentException("A pair match must connect two different views");
            }
            this.firstU = requireFinite(firstU, "firstU");
            this.firstV = requireFinite(firstV, "firstV");
            this.secondU = requireFinite(secondU, "secondU");
            this.secondV = requireFinite(secondV, "secondV");
            if (firstImageWidth <= 0 || firstImageHeight <= 0
                    || secondImageWidth <= 0 || secondImageHeight <= 0) {
                throw new IllegalArgumentException("Image dimensions must be positive");
            }
            this.firstImageWidth = firstImageWidth;
            this.firstImageHeight = firstImageHeight;
            this.secondImageWidth = secondImageWidth;
            this.secondImageHeight = secondImageHeight;
            if (!Double.isFinite(bestDistance) || bestDistance < 0.0
                    || !Double.isFinite(secondBestDistance) || secondBestDistance <= 0.0) {
                throw new IllegalArgumentException("Descriptor distances must be valid");
            }
            this.bestDistance = bestDistance;
            this.secondBestDistance = secondBestDistance;
            this.reciprocal = reciprocal;
            if (!Double.isFinite(detectorConfidence)
                    || detectorConfidence < 0.0
                    || detectorConfidence > 1.0) {
                throw new IllegalArgumentException("detectorConfidence must be between 0 and 1");
            }
            this.detectorConfidence = detectorConfidence;
        }

        public double distanceRatio() {
            return bestDistance / secondBestDistance;
        }

        private double quality() {
            return detectorConfidence * Math.max(0.0, 1.0 - distanceRatio());
        }
    }

    public static final class BuildReport {
        private final List<FeatureTrack> tracks;
        private final int inputMatchCount;
        private final int acceptedMatchCount;
        private final int ratioRejectedCount;
        private final int reciprocityRejectedCount;
        private final int spatiallyRejectedCount;
        private final int conflictRejectedCount;

        private BuildReport(
                List<FeatureTrack> tracks,
                int inputMatchCount,
                int acceptedMatchCount,
                int ratioRejectedCount,
                int reciprocityRejectedCount,
                int spatiallyRejectedCount,
                int conflictRejectedCount
        ) {
            this.tracks = Collections.unmodifiableList(new ArrayList<>(tracks));
            this.inputMatchCount = inputMatchCount;
            this.acceptedMatchCount = acceptedMatchCount;
            this.ratioRejectedCount = ratioRejectedCount;
            this.reciprocityRejectedCount = reciprocityRejectedCount;
            this.spatiallyRejectedCount = spatiallyRejectedCount;
            this.conflictRejectedCount = conflictRejectedCount;
        }

        public List<FeatureTrack> tracks() {
            return tracks;
        }

        public int inputMatchCount() {
            return inputMatchCount;
        }

        public int acceptedMatchCount() {
            return acceptedMatchCount;
        }

        public int ratioRejectedCount() {
            return ratioRejectedCount;
        }

        public int reciprocityRejectedCount() {
            return reciprocityRejectedCount;
        }

        public int spatiallyRejectedCount() {
            return spatiallyRejectedCount;
        }

        public int conflictRejectedCount() {
            return conflictRejectedCount;
        }
    }

    private final Config config;

    public FewViewTrackBuilder(Config config) {
        this.config = Objects.requireNonNull(config, "config");
    }

    public BuildReport build(List<PairMatch> matches) {
        Objects.requireNonNull(matches, "matches");
        List<PairMatch> filtered = new ArrayList<>();
        int ratioRejected = 0;
        int reciprocityRejected = 0;

        for (PairMatch match : matches) {
            Objects.requireNonNull(match, "match");
            if (match.distanceRatio() > config.maximumDistanceRatio) {
                ratioRejected++;
                continue;
            }
            if (config.requireReciprocal && !match.reciprocal) {
                reciprocityRejected++;
                continue;
            }
            filtered.add(match);
        }

        filtered.sort(
                Comparator.comparingDouble(PairMatch::quality).reversed()
                        .thenComparingDouble(PairMatch::distanceRatio)
        );

        List<PairMatch> spatiallyBalanced = new ArrayList<>();
        Map<String, Integer> cellPairCounts = new HashMap<>();
        int spatiallyRejected = 0;
        for (PairMatch match : filtered) {
            String cellPairKey = cellPairKey(match);
            int count = cellPairCounts.getOrDefault(cellPairKey, 0);
            if (count >= config.maximumMatchesPerCellPair) {
                spatiallyRejected++;
                continue;
            }
            cellPairCounts.put(cellPairKey, count + 1);
            spatiallyBalanced.add(match);
        }

        DisjointTracks disjoint = new DisjointTracks(config.maximumTrackViews);
        Map<FeatureKey, NodeObservation> nodeObservations = new LinkedHashMap<>();
        int conflictRejected = 0;
        int acceptedMatches = 0;

        for (PairMatch match : spatiallyBalanced) {
            nodeObservations.putIfAbsent(
                    match.first,
                    new NodeObservation(match.firstU, match.firstV, match.quality())
            );
            nodeObservations.putIfAbsent(
                    match.second,
                    new NodeObservation(match.secondU, match.secondV, match.quality())
            );
            disjoint.add(match.first);
            disjoint.add(match.second);
            if (disjoint.union(match.first, match.second)) {
                acceptedMatches++;
            } else {
                conflictRejected++;
            }
        }

        Map<FeatureKey, List<FeatureKey>> components = new LinkedHashMap<>();
        for (FeatureKey key : nodeObservations.keySet()) {
            FeatureKey root = disjoint.find(key);
            components.computeIfAbsent(root, ignored -> new ArrayList<>()).add(key);
        }

        List<FeatureTrack> tracks = new ArrayList<>();
        int trackIndex = 0;
        for (List<FeatureKey> component : components.values()) {
            if (component.size() < config.minimumTrackViews
                    || component.size() > config.maximumTrackViews) {
                continue;
            }
            component.sort(Comparator.comparing(FeatureKey::viewId));
            List<FeatureObservation> observations = new ArrayList<>();
            for (FeatureKey key : component) {
                NodeObservation node = nodeObservations.get(key);
                observations.add(new FeatureObservation(
                        key.viewId(),
                        node.u,
                        node.v,
                        Math.max(0.05, Math.min(1.0, node.quality))
                ));
            }
            tracks.add(new FeatureTrack("track_" + trackIndex++, observations));
        }

        return new BuildReport(
                tracks,
                matches.size(),
                acceptedMatches,
                ratioRejected,
                reciprocityRejected,
                spatiallyRejected,
                conflictRejected
        );
    }

    private String cellPairKey(PairMatch match) {
        int firstColumn = gridCoordinate(match.firstU, match.firstImageWidth, config.gridColumns);
        int firstRow = gridCoordinate(match.firstV, match.firstImageHeight, config.gridRows);
        int secondColumn = gridCoordinate(match.secondU, match.secondImageWidth, config.gridColumns);
        int secondRow = gridCoordinate(match.secondV, match.secondImageHeight, config.gridRows);
        String firstView = match.first.viewId();
        String secondView = match.second.viewId();
        if (firstView.compareTo(secondView) <= 0) {
            return firstView + '|' + secondView + '|'
                    + firstColumn + ',' + firstRow + '|'
                    + secondColumn + ',' + secondRow;
        }
        return secondView + '|' + firstView + '|'
                + secondColumn + ',' + secondRow + '|'
                + firstColumn + ',' + firstRow;
    }

    private static int gridCoordinate(double coordinate, int size, int cells) {
        double normalized = Math.max(0.0, Math.min(0.999999, coordinate / size));
        return (int) Math.floor(normalized * cells);
    }

    private static final class NodeObservation {
        private final double u;
        private final double v;
        private final double quality;

        private NodeObservation(double u, double v, double quality) {
            this.u = u;
            this.v = v;
            this.quality = quality;
        }
    }

    private static final class DisjointTracks {
        private final int maximumTrackViews;
        private final Map<FeatureKey, FeatureKey> parent = new LinkedHashMap<>();
        private final Map<FeatureKey, Integer> rank = new LinkedHashMap<>();
        private final Map<FeatureKey, Set<String>> views = new LinkedHashMap<>();

        private DisjointTracks(int maximumTrackViews) {
            this.maximumTrackViews = maximumTrackViews;
        }

        private void add(FeatureKey key) {
            if (parent.containsKey(key)) {
                return;
            }
            parent.put(key, key);
            rank.put(key, 0);
            Set<String> componentViews = new LinkedHashSet<>();
            componentViews.add(key.viewId());
            views.put(key, componentViews);
        }

        private FeatureKey find(FeatureKey key) {
            FeatureKey currentParent = parent.get(key);
            if (currentParent == null) {
                throw new IllegalStateException("Unknown feature key: " + key);
            }
            if (!currentParent.equals(key)) {
                currentParent = find(currentParent);
                parent.put(key, currentParent);
            }
            return currentParent;
        }

        private boolean union(FeatureKey first, FeatureKey second) {
            FeatureKey firstRoot = find(first);
            FeatureKey secondRoot = find(second);
            if (firstRoot.equals(secondRoot)) {
                return true;
            }
            Set<String> firstViews = views.get(firstRoot);
            Set<String> secondViews = views.get(secondRoot);
            for (String viewId : firstViews) {
                if (secondViews.contains(viewId)) {
                    return false;
                }
            }
            if (firstViews.size() + secondViews.size() > maximumTrackViews) {
                return false;
            }

            int firstRank = rank.get(firstRoot);
            int secondRank = rank.get(secondRoot);
            if (firstRank < secondRank) {
                FeatureKey temporary = firstRoot;
                firstRoot = secondRoot;
                secondRoot = temporary;
                Set<String> temporaryViews = firstViews;
                firstViews = secondViews;
                secondViews = temporaryViews;
                int temporaryRank = firstRank;
                firstRank = secondRank;
                secondRank = temporaryRank;
            }
            parent.put(secondRoot, firstRoot);
            firstViews.addAll(secondViews);
            views.remove(secondRoot);
            if (firstRank == secondRank) {
                rank.put(firstRoot, firstRank + 1);
            }
            return true;
        }
    }

    private static double requireFinite(double value, String name) {
        if (!Double.isFinite(value)) {
            throw new IllegalArgumentException(name + " must be finite");
        }
        return value;
    }

    private static String requireText(String value, String name) {
        if (value == null || value.trim().isEmpty()) {
            throw new IllegalArgumentException(name + " must not be blank");
        }
        return value;
    }
}

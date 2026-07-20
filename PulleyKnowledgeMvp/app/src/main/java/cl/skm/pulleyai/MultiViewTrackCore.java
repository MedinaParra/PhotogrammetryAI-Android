package cl.skm.pulleyai;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Builds conflict-free feature tracks from pairwise geometric inliers. */
public final class MultiViewTrackCore {
    private MultiViewTrackCore() {}

    public static Result build(List<MatchEdge> edges, int minimumViews) {
        if (edges == null) edges = Collections.emptyList();
        int minViews = Math.max(2, minimumViews);
        List<MatchEdge> ordered = new ArrayList<MatchEdge>();
        for (MatchEdge edge : edges) {
            if (edge != null && edge.leftFrame != edge.rightFrame
                    && edge.leftFeature >= 0 && edge.rightFeature >= 0
                    && Double.isFinite(edge.confidence) && edge.confidence > 0.0) {
                ordered.add(edge);
            }
        }
        Collections.sort(ordered, new Comparator<MatchEdge>() {
            @Override public int compare(MatchEdge a, MatchEdge b) {
                return Double.compare(b.confidence, a.confidence);
            }
        });

        Union union = new Union();
        int acceptedEdges = 0;
        int rejectedConflicts = 0;
        int redundantEdges = 0;
        for (MatchEdge edge : ordered) {
            ObservationKey left = new ObservationKey(edge.leftFrame, edge.leftFeature);
            ObservationKey right = new ObservationKey(edge.rightFrame, edge.rightFeature);
            int a = union.ensure(left);
            int b = union.ensure(right);
            int rootA = union.find(a);
            int rootB = union.find(b);
            if (rootA == rootB) {
                redundantEdges++;
                union.addSupport(rootA, edge.confidence);
                continue;
            }
            if (!disjoint(union.frames(rootA), union.frames(rootB))) {
                rejectedConflicts++;
                continue;
            }
            union.merge(rootA, rootB, edge.confidence);
            acceptedEdges++;
        }

        Map<Integer, List<Observation>> grouped = new HashMap<Integer, List<Observation>>();
        for (int i = 0; i < union.size(); i++) {
            int root = union.find(i);
            List<Observation> values = grouped.get(root);
            if (values == null) {
                values = new ArrayList<Observation>();
                grouped.put(root, values);
            }
            ObservationKey key = union.key(i);
            values.add(new Observation(key.frame, key.feature));
        }

        List<Track> tracks = new ArrayList<Track>();
        Set<Integer> coveredFrames = new HashSet<Integer>();
        int observationsInTracks = 0;
        for (Map.Entry<Integer, List<Observation>> entry : grouped.entrySet()) {
            List<Observation> values = entry.getValue();
            if (values.size() < minViews) continue;
            Collections.sort(values, new Comparator<Observation>() {
                @Override public int compare(Observation a, Observation b) {
                    int frame = Integer.compare(a.frameIndex, b.frameIndex);
                    return frame != 0 ? frame : Integer.compare(a.featureIndex, b.featureIndex);
                }
            });
            int root = union.find(entry.getKey());
            Track track = new Track(tracks.size(), values, union.support(root));
            tracks.add(track);
            observationsInTracks += values.size();
            for (Observation observation : values) coveredFrames.add(observation.frameIndex);
        }
        Collections.sort(tracks, new Comparator<Track>() {
            @Override public int compare(Track a, Track b) {
                int length = Integer.compare(b.observations.size(), a.observations.size());
                return length != 0 ? length : Double.compare(b.support, a.support);
            }
        });
        List<Integer> lengths = new ArrayList<Integer>();
        int tracksFourPlus = 0;
        for (Track track : tracks) {
            lengths.add(track.observations.size());
            if (track.observations.size() >= 4) tracksFourPlus++;
        }
        Collections.sort(lengths);
        double medianLength = lengths.isEmpty() ? 0.0
                : lengths.size() % 2 == 1 ? lengths.get(lengths.size()/2)
                : (lengths.get(lengths.size()/2-1)+lengths.get(lengths.size()/2))*0.5;
        String status = tracks.size() >= 80 && tracksFourPlus >= 24 && coveredFrames.size() >= 12
                ? "STRONG" : tracks.size() >= 30 && tracksFourPlus >= 8 && coveredFrames.size() >= 6
                ? "USABLE" : "WEAK";
        return new Result(tracks, ordered.size(), acceptedEdges, rejectedConflicts,
                redundantEdges, observationsInTracks, coveredFrames.size(),
                tracksFourPlus, medianLength, status);
    }

    private static boolean disjoint(Set<Integer> a, Set<Integer> b) {
        Set<Integer> smaller = a.size() <= b.size() ? a : b;
        Set<Integer> larger = smaller == a ? b : a;
        for (Integer value : smaller) if (larger.contains(value)) return false;
        return true;
    }

    public static final class MatchEdge {
        public final int leftFrame;
        public final int leftFeature;
        public final int rightFrame;
        public final int rightFeature;
        public final double confidence;
        public MatchEdge(int leftFrame, int leftFeature, int rightFrame, int rightFeature,
                         double confidence) {
            this.leftFrame = leftFrame;
            this.leftFeature = leftFeature;
            this.rightFrame = rightFrame;
            this.rightFeature = rightFeature;
            this.confidence = confidence;
        }
    }

    public static final class Observation {
        public final int frameIndex;
        public final int featureIndex;
        Observation(int frameIndex, int featureIndex) {
            this.frameIndex = frameIndex;
            this.featureIndex = featureIndex;
        }
    }

    public static final class Track {
        public final int id;
        public final List<Observation> observations;
        public final double support;
        Track(int id, List<Observation> observations, double support) {
            this.id = id;
            this.observations = Collections.unmodifiableList(
                    new ArrayList<Observation>(observations));
            this.support = support;
        }
    }

    public static final class Result {
        public final List<Track> tracks;
        public final int candidateEdges;
        public final int acceptedEdges;
        public final int rejectedConflicts;
        public final int redundantEdges;
        public final int observationsInTracks;
        public final int coveredFrames;
        public final int tracksFourPlus;
        public final double medianTrackLength;
        public final String status;
        Result(List<Track> tracks, int candidateEdges, int acceptedEdges,
               int rejectedConflicts, int redundantEdges, int observationsInTracks,
               int coveredFrames, int tracksFourPlus, double medianTrackLength,
               String status) {
            this.tracks = Collections.unmodifiableList(new ArrayList<Track>(tracks));
            this.candidateEdges = candidateEdges;
            this.acceptedEdges = acceptedEdges;
            this.rejectedConflicts = rejectedConflicts;
            this.redundantEdges = redundantEdges;
            this.observationsInTracks = observationsInTracks;
            this.coveredFrames = coveredFrames;
            this.tracksFourPlus = tracksFourPlus;
            this.medianTrackLength = medianTrackLength;
            this.status = status;
        }
        public boolean ready() {
            return "STRONG".equals(status) || "USABLE".equals(status);
        }
        public String summary() {
            return "Tracks " + tracks.size() + " · >=4 vistas " + tracksFourPlus
                    + " · fotogramas " + coveredFrames + " · " + status;
        }
    }

    private static final class ObservationKey {
        final int frame;
        final int feature;
        ObservationKey(int frame, int feature) {
            this.frame = frame;
            this.feature = feature;
        }
        @Override public boolean equals(Object value) {
            if (this == value) return true;
            if (!(value instanceof ObservationKey)) return false;
            ObservationKey other = (ObservationKey) value;
            return frame == other.frame && feature == other.feature;
        }
        @Override public int hashCode() {
            return 31 * frame + feature;
        }
    }

    private static final class Union {
        private final List<Integer> parent = new ArrayList<Integer>();
        private final List<Integer> rank = new ArrayList<Integer>();
        private final List<ObservationKey> keys = new ArrayList<ObservationKey>();
        private final List<Set<Integer>> frameSets = new ArrayList<Set<Integer>>();
        private final List<Double> supports = new ArrayList<Double>();
        private final Map<ObservationKey, Integer> indices = new HashMap<ObservationKey, Integer>();

        int ensure(ObservationKey key) {
            Integer existing = indices.get(key);
            if (existing != null) return existing;
            int index = parent.size();
            parent.add(index);
            rank.add(0);
            keys.add(key);
            Set<Integer> frames = new HashSet<Integer>();
            frames.add(key.frame);
            frameSets.add(frames);
            supports.add(0.0);
            indices.put(key, index);
            return index;
        }
        int find(int value) {
            int p = parent.get(value);
            if (p != value) {
                p = find(p);
                parent.set(value, p);
            }
            return p;
        }
        void merge(int first, int second, double confidence) {
            int a = find(first);
            int b = find(second);
            if (a == b) return;
            if (rank.get(a) < rank.get(b)) {
                int tmp = a; a = b; b = tmp;
            }
            parent.set(b, a);
            if (rank.get(a).equals(rank.get(b))) rank.set(a, rank.get(a)+1);
            frameSets.get(a).addAll(frameSets.get(b));
            supports.set(a, supports.get(a) + supports.get(b) + confidence);
        }
        void addSupport(int root, double confidence) {
            int value = find(root);
            supports.set(value, supports.get(value) + confidence);
        }
        Set<Integer> frames(int root) { return frameSets.get(find(root)); }
        double support(int root) { return supports.get(find(root)); }
        int size() { return parent.size(); }
        ObservationKey key(int index) { return keys.get(index); }
    }
}

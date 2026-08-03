package cl.skm.pulleyai;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

/** Builds deterministic multiview tracks only from primary geometric inlier evidence. */
public final class ImportedTrackAssemblerCore {
    private static final double COORDINATE_CONFLICT_PX = 2.5;

    private ImportedTrackAssemblerCore() {}

    public static Result assemble(List<PairEvidence> evidence, int minimumTrackLength) {
        int minLength = Math.max(3, minimumTrackLength);
        List<PairEvidence> ordered = evidence == null
                ? new ArrayList<PairEvidence>() : new ArrayList<PairEvidence>(evidence);
        Collections.sort(ordered, new Comparator<PairEvidence>() {
            @Override public int compare(PairEvidence a, PairEvidence b) {
                int compare = a.pairId.compareTo(b.pairId);
                if (compare != 0) return compare;
                compare = Integer.compare(a.leftFrame, b.leftFrame);
                return compare != 0 ? compare : Integer.compare(a.rightFrame, b.rightFrame);
            }
        });

        Registry registry = new Registry();
        List<Edge> edges = new ArrayList<Edge>();
        int inputPairs = 0;
        int primaryPairs = 0;
        int bridgePairsExcluded = 0;
        int weakPairsExcluded = 0;
        int malformedCorrespondences = 0;
        int coordinateConflicts = 0;
        for (PairEvidence pair : ordered) {
            if (pair == null) continue;
            inputPairs++;
            if (pair.diagnosticBridge()) {
                bridgePairsExcluded++;
                continue;
            }
            if (!pair.primaryUsable()) {
                weakPairsExcluded++;
                continue;
            }
            if (pair.leftFrame == pair.rightFrame || pair.correspondences.isEmpty()) {
                weakPairsExcluded++;
                continue;
            }
            primaryPairs++;
            List<Correspondence> correspondences = new ArrayList<Correspondence>(pair.correspondences);
            Collections.sort(correspondences, new Comparator<Correspondence>() {
                @Override public int compare(Correspondence a, Correspondence b) {
                    int compare = Integer.compare(a.leftFeature, b.leftFeature);
                    return compare != 0 ? compare : Integer.compare(a.rightFeature, b.rightFeature);
                }
            });
            for (Correspondence correspondence : correspondences) {
                if (correspondence == null || correspondence.leftFeature < 0
                        || correspondence.rightFeature < 0
                        || !finite(correspondence.x) || !finite(correspondence.y)
                        || !finite(correspondence.u) || !finite(correspondence.v)) {
                    malformedCorrespondences++;
                    continue;
                }
                Registry.Lookup left = registry.lookup(pair.leftFrame,
                        correspondence.leftFeature, correspondence.x, correspondence.y,
                        pair.leftBand);
                Registry.Lookup right = registry.lookup(pair.rightFrame,
                        correspondence.rightFeature, correspondence.u, correspondence.v,
                        pair.rightBand);
                if (left.conflict || right.conflict) {
                    coordinateConflicts++;
                    continue;
                }
                edges.add(new Edge(left.index, right.index, pair.pairId));
            }
        }

        Union union = new Union(registry.nodes);
        int acceptedLinks = 0;
        int duplicateLinks = 0;
        int frameCollisionRejects = 0;
        for (Edge edge : edges) {
            Union.Outcome outcome = union.connect(edge.left, edge.right, edge.pairId);
            if (outcome == Union.Outcome.ACCEPTED) acceptedLinks++;
            else if (outcome == Union.Outcome.DUPLICATE) duplicateLinks++;
            else frameCollisionRejects++;
        }

        Map<Integer,List<Node>> members = new HashMap<Integer,List<Node>>();
        for (int i = 0; i < registry.nodes.size(); i++) {
            int root = union.find(i);
            List<Node> list = members.get(root);
            if (list == null) {
                list = new ArrayList<Node>();
                members.put(root, list);
            }
            list.add(registry.nodes.get(i));
        }

        List<Track> tracks = new ArrayList<Track>();
        int discardedShort = 0;
        for (Map.Entry<Integer,List<Node>> entry : members.entrySet()) {
            List<Node> observations = entry.getValue();
            if (observations.size() < minLength) {
                discardedShort++;
                continue;
            }
            Collections.sort(observations, NODE_ORDER);
            Set<Integer> frames = new HashSet<Integer>();
            boolean duplicateFrame = false;
            boolean low = false;
            boolean high = false;
            for (Node observation : observations) {
                if (!frames.add(observation.frame)) duplicateFrame = true;
                low |= "LOW".equals(observation.band);
                high |= "HIGH".equals(observation.band);
            }
            if (duplicateFrame) {
                frameCollisionRejects++;
                continue;
            }
            List<String> provenance = new ArrayList<String>(union.sourcePairs(entry.getKey()));
            Collections.sort(provenance);
            tracks.add(new Track("", observations, provenance, low && high));
        }
        Collections.sort(tracks, new Comparator<Track>() {
            @Override public int compare(Track a, Track b) {
                int compare = Integer.compare(a.observations.get(0).frame,
                        b.observations.get(0).frame);
                if (compare != 0) return compare;
                compare = Integer.compare(a.observations.get(0).feature,
                        b.observations.get(0).feature);
                if (compare != 0) return compare;
                return Integer.compare(b.observations.size(), a.observations.size());
            }
        });
        List<Track> numbered = new ArrayList<Track>(tracks.size());
        TreeMap<Integer,Integer> histogram = new TreeMap<Integer,Integer>();
        List<Integer> lengths = new ArrayList<Integer>();
        int observationCount = 0;
        int crossRingTracks = 0;
        Set<Integer> framesRepresented = new HashSet<Integer>();
        for (int i = 0; i < tracks.size(); i++) {
            Track source = tracks.get(i);
            Track track = new Track(String.format(java.util.Locale.ROOT,
                    "track-%04d", i + 1), source.observations,
                    source.sourcePairs, source.crossRing);
            numbered.add(track);
            int length = track.observations.size();
            lengths.add(length);
            observationCount += length;
            histogram.put(length, histogram.containsKey(length)
                    ? histogram.get(length) + 1 : 1);
            if (track.crossRing) crossRingTracks++;
            for (Node observation : track.observations) framesRepresented.add(observation.frame);
        }
        Collections.sort(lengths);
        double medianLength = lengths.isEmpty() ? 0.0
                : lengths.size() % 2 == 1 ? lengths.get(lengths.size() / 2)
                : 0.5 * (lengths.get(lengths.size() / 2 - 1)
                + lengths.get(lengths.size() / 2));
        int maximumLength = lengths.isEmpty() ? 0 : lengths.get(lengths.size() - 1);
        return new Result(inputPairs, primaryPairs, bridgePairsExcluded,
                weakPairsExcluded, edges.size(), acceptedLinks, duplicateLinks,
                frameCollisionRejects, malformedCorrespondences,
                coordinateConflicts, discardedShort, numbered,
                observationCount, crossRingTracks, framesRepresented.size(),
                histogram, medianLength, maximumLength, minLength);
    }

    private static final Comparator<Node> NODE_ORDER = new Comparator<Node>() {
        @Override public int compare(Node a, Node b) {
            int compare = Integer.compare(a.frame, b.frame);
            return compare != 0 ? compare : Integer.compare(a.feature, b.feature);
        }
    };

    private static boolean finite(double value) {
        return Double.isFinite(value);
    }

    private static String normalizeBand(String value) {
        return "HIGH".equals(value) ? "HIGH" : "LOW";
    }

    private static String escape(String value) {
        if (value == null) return "";
        return value.replace("\\", "\\\\")
                .replace("\"", "\\\"")
                .replace("\n", "\\n")
                .replace("\r", "\\r");
    }

    private static String number(double value) {
        return Double.isFinite(value) ? Double.toString(value) : "null";
    }

    public static final class Correspondence {
        public final int leftFeature, rightFeature;
        public final double x, y, u, v;
        public Correspondence(int leftFeature, int rightFeature,
                              double x, double y, double u, double v) {
            this.leftFeature = leftFeature;
            this.rightFeature = rightFeature;
            this.x = x;
            this.y = y;
            this.u = u;
            this.v = v;
        }
    }

    public static final class PairEvidence {
        public final String pairId;
        public final int leftFrame, rightFrame;
        public final String leftBand, rightBand, status;
        public final List<Correspondence> correspondences;
        public PairEvidence(String pairId, int leftFrame, int rightFrame,
                            String leftBand, String rightBand, String status,
                            List<Correspondence> correspondences) {
            this.pairId = pairId == null || pairId.trim().isEmpty()
                    ? leftFrame + "-" + rightFrame : pairId.trim();
            this.leftFrame = leftFrame;
            this.rightFrame = rightFrame;
            this.leftBand = normalizeBand(leftBand);
            this.rightBand = normalizeBand(rightBand);
            this.status = status == null ? "WEAK" : status;
            this.correspondences = Collections.unmodifiableList(
                    correspondences == null ? new ArrayList<Correspondence>()
                            : new ArrayList<Correspondence>(correspondences));
        }
        public boolean primaryUsable() {
            return "STRONG".equals(status) || "USABLE".equals(status);
        }
        public boolean diagnosticBridge() {
            return "BRIDGE".equals(status);
        }
    }

    public static final class Node {
        public final int frame, feature;
        public final double x, y;
        public final String band;
        Node(int frame, int feature, double x, double y, String band) {
            this.frame = frame;
            this.feature = feature;
            this.x = x;
            this.y = y;
            this.band = normalizeBand(band);
        }
    }

    public static final class Track {
        public final String id;
        public final List<Node> observations;
        public final List<String> sourcePairs;
        public final boolean crossRing;
        Track(String id, List<Node> observations, List<String> sourcePairs,
              boolean crossRing) {
            this.id = id;
            this.observations = Collections.unmodifiableList(
                    new ArrayList<Node>(observations));
            this.sourcePairs = Collections.unmodifiableList(
                    new ArrayList<String>(sourcePairs));
            this.crossRing = crossRing;
        }
    }

    public static final class Result {
        public final int inputPairs, primaryPairs, bridgePairsExcluded;
        public final int weakPairsExcluded, candidateLinks, acceptedLinks;
        public final int duplicateLinks, frameCollisionRejects;
        public final int malformedCorrespondences, coordinateConflicts;
        public final int discardedShortComponents;
        public final List<Track> tracks;
        public final int observationCount, crossRingTracks, framesRepresented;
        public final Map<Integer,Integer> lengthHistogram;
        public final double medianTrackLength;
        public final int maximumTrackLength, minimumTrackLength;

        Result(int inputPairs, int primaryPairs, int bridgePairsExcluded,
               int weakPairsExcluded, int candidateLinks, int acceptedLinks,
               int duplicateLinks, int frameCollisionRejects,
               int malformedCorrespondences, int coordinateConflicts,
               int discardedShortComponents, List<Track> tracks,
               int observationCount, int crossRingTracks,
               int framesRepresented, Map<Integer,Integer> lengthHistogram,
               double medianTrackLength, int maximumTrackLength,
               int minimumTrackLength) {
            this.inputPairs = inputPairs;
            this.primaryPairs = primaryPairs;
            this.bridgePairsExcluded = bridgePairsExcluded;
            this.weakPairsExcluded = weakPairsExcluded;
            this.candidateLinks = candidateLinks;
            this.acceptedLinks = acceptedLinks;
            this.duplicateLinks = duplicateLinks;
            this.frameCollisionRejects = frameCollisionRejects;
            this.malformedCorrespondences = malformedCorrespondences;
            this.coordinateConflicts = coordinateConflicts;
            this.discardedShortComponents = discardedShortComponents;
            this.tracks = Collections.unmodifiableList(new ArrayList<Track>(tracks));
            this.observationCount = observationCount;
            this.crossRingTracks = crossRingTracks;
            this.framesRepresented = framesRepresented;
            this.lengthHistogram = Collections.unmodifiableMap(
                    new LinkedHashMap<Integer,Integer>(lengthHistogram));
            this.medianTrackLength = medianTrackLength;
            this.maximumTrackLength = maximumTrackLength;
            this.minimumTrackLength = minimumTrackLength;
        }

        public String canonicalJson() {
            StringBuilder json = new StringBuilder(4096 + tracks.size() * 700);
            json.append("{\n")
                    .append("\"schema\":\"skm-imported-multiview-tracks/1\",")
                    .append("\n\"inputPairs\":").append(inputPairs).append(',')
                    .append("\n\"primaryPairs\":").append(primaryPairs).append(',')
                    .append("\n\"bridgePairsExcluded\":").append(bridgePairsExcluded).append(',')
                    .append("\n\"weakPairsExcluded\":").append(weakPairsExcluded).append(',')
                    .append("\n\"candidateLinks\":").append(candidateLinks).append(',')
                    .append("\n\"acceptedLinks\":").append(acceptedLinks).append(',')
                    .append("\n\"duplicateLinks\":").append(duplicateLinks).append(',')
                    .append("\n\"frameCollisionRejects\":").append(frameCollisionRejects).append(',')
                    .append("\n\"malformedCorrespondences\":").append(malformedCorrespondences).append(',')
                    .append("\n\"coordinateConflicts\":").append(coordinateConflicts).append(',')
                    .append("\n\"discardedShortComponents\":").append(discardedShortComponents).append(',')
                    .append("\n\"minimumTrackLength\":").append(minimumTrackLength).append(',')
                    .append("\n\"trackCount\":").append(tracks.size()).append(',')
                    .append("\n\"observationCount\":").append(observationCount).append(',')
                    .append("\n\"crossRingTracks\":").append(crossRingTracks).append(',')
                    .append("\n\"framesRepresented\":").append(framesRepresented).append(',')
                    .append("\n\"medianTrackLength\":").append(number(medianTrackLength)).append(',')
                    .append("\n\"maximumTrackLength\":").append(maximumTrackLength).append(',')
                    .append("\n\"bridgeEvidenceUsedForGeometry\":false,")
                    .append("\n\"lengthHistogram\":{");
            int histogramIndex = 0;
            for (Map.Entry<Integer,Integer> entry : lengthHistogram.entrySet()) {
                if (histogramIndex++ > 0) json.append(',');
                json.append('\"').append(entry.getKey()).append("\":").append(entry.getValue());
            }
            json.append("},\n\"tracks\":[");
            for (int i = 0; i < tracks.size(); i++) {
                if (i > 0) json.append(',');
                Track track = tracks.get(i);
                json.append("\n{\"id\":\"").append(escape(track.id)).append("\",")
                        .append("\"length\":").append(track.observations.size()).append(',')
                        .append("\"crossRing\":").append(track.crossRing).append(',')
                        .append("\"sourcePairs\":[");
                for (int p = 0; p < track.sourcePairs.size(); p++) {
                    if (p > 0) json.append(',');
                    json.append('\"').append(escape(track.sourcePairs.get(p))).append('\"');
                }
                json.append("],\"observations\":[");
                for (int o = 0; o < track.observations.size(); o++) {
                    if (o > 0) json.append(',');
                    Node observation = track.observations.get(o);
                    json.append("{\"frame\":").append(observation.frame).append(',')
                            .append("\"feature\":").append(observation.feature).append(',')
                            .append("\"x\":").append(number(observation.x)).append(',')
                            .append("\"y\":").append(number(observation.y)).append(',')
                            .append("\"band\":\"").append(observation.band).append("\"}");
                }
                json.append("]}");
            }
            return json.append("\n]\n}").toString();
        }
    }

    private static final class NodeKey {
        final int frame, feature;
        NodeKey(int frame, int feature) {
            this.frame = frame;
            this.feature = feature;
        }
        @Override public boolean equals(Object other) {
            if (!(other instanceof NodeKey)) return false;
            NodeKey key = (NodeKey) other;
            return frame == key.frame && feature == key.feature;
        }
        @Override public int hashCode() {
            return 31 * frame + feature;
        }
    }

    private static final class Registry {
        final List<Node> nodes = new ArrayList<Node>();
        final Map<NodeKey,Integer> indexes = new HashMap<NodeKey,Integer>();
        Lookup lookup(int frame, int feature, double x, double y, String band) {
            NodeKey key = new NodeKey(frame, feature);
            Integer index = indexes.get(key);
            if (index == null) {
                int created = nodes.size();
                nodes.add(new Node(frame, feature, x, y, band));
                indexes.put(key, created);
                return new Lookup(created, false);
            }
            Node existing = nodes.get(index);
            double distance = Math.hypot(existing.x - x, existing.y - y);
            boolean conflict = distance > COORDINATE_CONFLICT_PX
                    || !existing.band.equals(normalizeBand(band));
            return new Lookup(index, conflict);
        }
        static final class Lookup {
            final int index;
            final boolean conflict;
            Lookup(int index, boolean conflict) {
                this.index = index;
                this.conflict = conflict;
            }
        }
    }

    private static final class Edge {
        final int left, right;
        final String pairId;
        Edge(int left, int right, String pairId) {
            this.left = left;
            this.right = right;
            this.pairId = pairId;
        }
    }

    private static final class Union {
        enum Outcome { ACCEPTED, DUPLICATE, FRAME_COLLISION }
        final int[] parent, size;
        final Map<Integer,Set<Integer>> frames = new HashMap<Integer,Set<Integer>>();
        final Map<Integer,Set<String>> sources = new HashMap<Integer,Set<String>>();
        Union(List<Node> nodes) {
            parent = new int[nodes.size()];
            size = new int[nodes.size()];
            for (int i = 0; i < nodes.size(); i++) {
                parent[i] = i;
                size[i] = 1;
                Set<Integer> componentFrames = new HashSet<Integer>();
                componentFrames.add(nodes.get(i).frame);
                frames.put(i, componentFrames);
                sources.put(i, new HashSet<String>());
            }
        }
        int find(int value) {
            int root = value;
            while (parent[root] != root) root = parent[root];
            while (parent[value] != value) {
                int next = parent[value];
                parent[value] = root;
                value = next;
            }
            return root;
        }
        Outcome connect(int left, int right, String pairId) {
            int a = find(left);
            int b = find(right);
            if (a == b) {
                sources.get(a).add(pairId);
                return Outcome.DUPLICATE;
            }
            Set<Integer> framesA = frames.get(a);
            Set<Integer> framesB = frames.get(b);
            for (int frame : framesA) {
                if (framesB.contains(frame)) return Outcome.FRAME_COLLISION;
            }
            if (size[a] < size[b]) {
                int swap = a;
                a = b;
                b = swap;
                framesA = frames.get(a);
                framesB = frames.get(b);
            }
            parent[b] = a;
            size[a] += size[b];
            framesA.addAll(framesB);
            frames.remove(b);
            Set<String> sourceA = sources.get(a);
            sourceA.addAll(sources.get(b));
            sourceA.add(pairId);
            sources.remove(b);
            return Outcome.ACCEPTED;
        }
        Set<String> sourcePairs(int value) {
            int root = find(value);
            Set<String> valueSet = sources.get(root);
            return valueSet == null ? Collections.<String>emptySet() : valueSet;
        }
    }
}

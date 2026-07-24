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

/**
 * Builds a frame graph only from persisted primary tracks and reports each
 * connected component without promoting disconnected local geometry to global.
 */
public final class ImportedComponentGeometryCore {
    private ImportedComponentGeometryCore() {}

    public static Result analyze(ImportedTrackAssemblerCore.Result tracks,
                                 ImportedSeedGeometryCore.Result seed,
                                 int acceptedFrameCount) {
        if (tracks == null) {
            return Result.blocked("TRACKS_MISSING", acceptedFrameCount);
        }
        Map<Integer, FrameMeta> metadata = new HashMap<Integer, FrameMeta>();
        Union union = new Union();
        for (ImportedTrackAssemblerCore.Track track : tracks.tracks) {
            if (track == null || track.observations.size() < 2) continue;
            int firstFrame = track.observations.get(0).frame;
            union.add(firstFrame);
            for (ImportedTrackAssemblerCore.Node node : track.observations) {
                union.add(node.frame);
                FrameMeta meta = metadata.get(node.frame);
                if (meta == null) metadata.put(node.frame, new FrameMeta(node.frame, node.band));
                else meta.observeBand(node.band);
                union.connect(firstFrame, node.frame);
            }
        }

        Map<Integer, List<Integer>> grouped = new HashMap<Integer, List<Integer>>();
        for (int frame : union.frames()) {
            int root = union.find(frame);
            List<Integer> members = grouped.get(root);
            if (members == null) {
                members = new ArrayList<Integer>();
                grouped.put(root, members);
            }
            members.add(frame);
        }
        List<Component> components = new ArrayList<Component>();
        for (List<Integer> members : grouped.values()) {
            Collections.sort(members);
            boolean low = false;
            boolean high = false;
            for (int frame : members) {
                FrameMeta meta = metadata.get(frame);
                if (meta != null) {
                    low |= meta.low;
                    high |= meta.high;
                }
            }
            components.add(new Component("", members, low, high));
        }
        Collections.sort(components, new Comparator<Component>() {
            @Override public int compare(Component a, Component b) {
                int compare = Integer.compare(b.frames.size(), a.frames.size());
                if (compare != 0) return compare;
                int af = a.frames.isEmpty() ? Integer.MAX_VALUE : a.frames.get(0);
                int bf = b.frames.isEmpty() ? Integer.MAX_VALUE : b.frames.get(0);
                return Integer.compare(af, bf);
            }
        });
        List<Component> numbered = new ArrayList<Component>();
        int seedComponent = -1;
        for (int i = 0; i < components.size(); i++) {
            Component source = components.get(i);
            Component component = new Component(String.format(java.util.Locale.ROOT,
                    "component-%02d", i + 1), source.frames, source.hasLow, source.hasHigh);
            numbered.add(component);
            if (seed != null && seed.solved
                    && component.frames.contains(seed.leftFrame)
                    && component.frames.contains(seed.rightFrame)) {
                seedComponent = i;
            }
        }
        BridgeRecommendation bridge = numbered.size() >= 2
                ? recommend(numbered.get(0), numbered.get(1), metadata) : null;
        int represented = metadata.size();
        int accepted = Math.max(acceptedFrameCount, represented);
        boolean globalConnected = numbered.size() == 1 && represented == accepted;
        boolean localGeometry = seed != null && seed.solved && seed.geometryReady
                && seedComponent >= 0;
        String state = globalConnected && localGeometry
                ? "GLOBAL_COMPONENT_CONNECTED"
                : localGeometry ? "LOCAL_COMPONENT_GEOMETRY_ONLY"
                : represented > 0 ? "COMPONENTS_WITHOUT_ADMISSIBLE_SEED"
                : "NO_TRACK_COMPONENTS";
        return new Result(true, state, accepted, represented,
                accepted - represented, numbered, seedComponent,
                globalConnected, localGeometry, bridge);
    }

    private static BridgeRecommendation recommend(Component first, Component second,
                                                   Map<Integer, FrameMeta> metadata) {
        BridgeRecommendation best = null;
        for (int left : first.frames) {
            for (int right : second.frames) {
                FrameMeta a = metadata.get(left);
                FrameMeta b = metadata.get(right);
                boolean crossRing = a != null && b != null
                        && ((a.low && b.high) || (a.high && b.low));
                int sequenceGap = Math.abs(left - right);
                double score = sequenceGap + (crossRing ? -4.0 : 0.0);
                BridgeRecommendation candidate = new BridgeRecommendation(
                        left, right, first.id, second.id, crossRing, score);
                if (best == null || candidate.score < best.score
                        || (candidate.score == best.score && candidate.leftFrame < best.leftFrame)) {
                    best = candidate;
                }
            }
        }
        return best;
    }

    private static final class FrameMeta {
        final int frame;
        boolean low;
        boolean high;

        FrameMeta(int frame, String band) {
            this.frame = frame;
            observeBand(band);
        }

        void observeBand(String band) {
            if ("HIGH".equals(band)) high = true;
            else low = true;
        }
    }

    private static final class Union {
        final Map<Integer, Integer> parent = new LinkedHashMap<Integer, Integer>();
        final Map<Integer, Integer> rank = new HashMap<Integer, Integer>();

        void add(int frame) {
            if (!parent.containsKey(frame)) {
                parent.put(frame, frame);
                rank.put(frame, 0);
            }
        }

        int find(int frame) {
            add(frame);
            int p = parent.get(frame);
            if (p != frame) {
                p = find(p);
                parent.put(frame, p);
            }
            return p;
        }

        void connect(int a, int b) {
            int ra = find(a);
            int rb = find(b);
            if (ra == rb) return;
            int ar = rank.get(ra);
            int br = rank.get(rb);
            if (ar < br) parent.put(ra, rb);
            else if (br < ar) parent.put(rb, ra);
            else {
                parent.put(rb, ra);
                rank.put(ra, ar + 1);
            }
        }

        Set<Integer> frames() {
            return new HashSet<Integer>(parent.keySet());
        }
    }

    public static final class Component {
        public final String id;
        public final List<Integer> frames;
        public final boolean hasLow, hasHigh, crossRing;

        Component(String id, List<Integer> frames, boolean hasLow, boolean hasHigh) {
            this.id = id;
            this.frames = Collections.unmodifiableList(new ArrayList<Integer>(frames));
            this.hasLow = hasLow;
            this.hasHigh = hasHigh;
            this.crossRing = hasLow && hasHigh;
        }
    }

    public static final class BridgeRecommendation {
        public final int leftFrame, rightFrame;
        public final String leftComponent, rightComponent;
        public final boolean crossRingPreferred;
        public final double score;

        BridgeRecommendation(int leftFrame, int rightFrame,
                             String leftComponent, String rightComponent,
                             boolean crossRingPreferred, double score) {
            this.leftFrame = leftFrame;
            this.rightFrame = rightFrame;
            this.leftComponent = leftComponent;
            this.rightComponent = rightComponent;
            this.crossRingPreferred = crossRingPreferred;
            this.score = score;
        }
    }

    public static final class Result {
        public final boolean solved;
        public final String state;
        public final int acceptedFrames, representedFrames, isolatedFrames;
        public final List<Component> components;
        public final int seedComponentIndex;
        public final boolean globalConnected, localGeometryReady;
        public final BridgeRecommendation bridge;

        Result(boolean solved, String state, int acceptedFrames,
               int representedFrames, int isolatedFrames,
               List<Component> components, int seedComponentIndex,
               boolean globalConnected, boolean localGeometryReady,
               BridgeRecommendation bridge) {
            this.solved = solved;
            this.state = state;
            this.acceptedFrames = acceptedFrames;
            this.representedFrames = representedFrames;
            this.isolatedFrames = Math.max(0, isolatedFrames);
            this.components = Collections.unmodifiableList(new ArrayList<Component>(components));
            this.seedComponentIndex = seedComponentIndex;
            this.globalConnected = globalConnected;
            this.localGeometryReady = localGeometryReady;
            this.bridge = bridge;
        }

        static Result blocked(String state, int accepted) {
            return new Result(false, state, Math.max(0, accepted), 0,
                    Math.max(0, accepted), Collections.<Component>emptyList(),
                    -1, false, false, null);
        }

        public String summary() {
            StringBuilder text = new StringBuilder();
            text.append("COMPONENTES ").append(state)
                    .append(" · ").append(components.size())
                    .append(" componentes · representadas ")
                    .append(representedFrames).append('/').append(acceptedFrames)
                    .append(" · aisladas ").append(isolatedFrames);
            for (int i = 0; i < components.size() && i < 4; i++) {
                Component component = components.get(i);
                text.append("\n").append(component.id).append(": ")
                        .append(component.frames.size()).append(" fotos")
                        .append(component.crossRing ? " · cross-ring" : " · un anillo");
                if (i == seedComponentIndex) text.append(" · contiene semilla 3D");
            }
            if (bridge != null) {
                text.append("\nUNIÓN RECOMENDADA: foto ").append(bridge.leftFrame)
                        .append(" ↔ foto ").append(bridge.rightFrame)
                        .append(bridge.crossRingPreferred ? " · preferir cruce entre anillos" : "")
                        .append(" · verificar solape antes de promover");
            }
            text.append("\nReconstrucción global: ")
                    .append(globalConnected ? "CONECTADA" : "BLOQUEADA");
            return text.toString();
        }

        public String canonicalJson() {
            StringBuilder json = new StringBuilder();
            json.append("{\n")
                    .append("\"schema\":\"skm-imported-components/1\",")
                    .append("\n\"state\":\"").append(state).append("\",")
                    .append("\n\"acceptedFrames\":").append(acceptedFrames).append(',')
                    .append("\n\"representedFrames\":").append(representedFrames).append(',')
                    .append("\n\"isolatedFrames\":").append(isolatedFrames).append(',')
                    .append("\n\"globalConnected\":").append(globalConnected).append(',')
                    .append("\n\"localGeometryReady\":").append(localGeometryReady).append(',')
                    .append("\n\"metricScale\":false,")
                    .append("\n\"industrialRelease\":false,")
                    .append("\n\"components\":[");
            for (int i = 0; i < components.size(); i++) {
                if (i > 0) json.append(',');
                Component component = components.get(i);
                json.append("{\"id\":\"").append(component.id).append("\",")
                        .append("\"crossRing\":").append(component.crossRing).append(',')
                        .append("\"containsSeed\":").append(i == seedComponentIndex).append(',')
                        .append("\"frames\":[");
                for (int j = 0; j < component.frames.size(); j++) {
                    if (j > 0) json.append(',');
                    json.append(component.frames.get(j));
                }
                json.append("]}");
            }
            json.append(']');
            if (bridge != null) {
                json.append(",\n\"bridgeRecommendation\":{")
                        .append("\"leftFrame\":").append(bridge.leftFrame).append(',')
                        .append("\"rightFrame\":").append(bridge.rightFrame).append(',')
                        .append("\"leftComponent\":\"").append(bridge.leftComponent).append("\",")
                        .append("\"rightComponent\":\"").append(bridge.rightComponent).append("\",")
                        .append("\"crossRingPreferred\":")
                        .append(bridge.crossRingPreferred).append('}');
            }
            return json.append("\n}").toString();
        }
    }
}

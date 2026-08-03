package cl.skm.pulleyai;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/** Builds a deterministic, bounded local-BA problem from real runtime snapshots. */
public final class RuntimeBundleWindowCore {
    public static final int MAX_CAMERAS = 8;
    public static final int MAX_POINTS = 120;
    public static final int MAX_OBSERVATIONS = 1500;

    private RuntimeBundleWindowCore() {}

    public static Result build(List<FrameSnapshot> sourceFrames,
                               List<TrackSnapshot> sourceTracks,
                               List<PointSnapshot> sourcePoints) {
        List<FrameSnapshot> frames = validFrames(sourceFrames);
        if (frames.isEmpty()) return Result.failed("NO_VALID_CAMERAS");
        FrameSnapshot gauge = null;
        for (FrameSnapshot frame : frames) if (frame.globalIndex == 0) gauge = frame;
        if (gauge == null) return Result.failed("GAUGE_CAMERA_MISSING");

        Map<Integer,PointSnapshot> pointByTrack = new HashMap<Integer,PointSnapshot>();
        if (sourcePoints != null) {
            for (PointSnapshot point : sourcePoints) {
                if (point != null && point.valid()) pointByTrack.put(point.trackId, point);
            }
        }
        if (pointByTrack.size() < 6) return Result.failed("INSUFFICIENT_FUSED_POINTS");

        List<TrackSnapshot> tracks = new ArrayList<TrackSnapshot>();
        Map<Integer,Integer> observationsPerFrame = new HashMap<Integer,Integer>();
        if (sourceTracks != null) {
            for (TrackSnapshot track : sourceTracks) {
                if (track == null || !track.valid() || !pointByTrack.containsKey(track.trackId)) continue;
                tracks.add(track);
                for (ObservationSnapshot observation : track.observations) {
                    Integer count = observationsPerFrame.get(observation.frameIndex);
                    observationsPerFrame.put(observation.frameIndex, count == null ? 1 : count + 1);
                }
            }
        }
        if (tracks.size() < 6) return Result.failed("INSUFFICIENT_TRACKS");

        final Map<Integer,Integer> counts = observationsPerFrame;
        List<FrameSnapshot> ranked = new ArrayList<FrameSnapshot>(frames);
        Collections.sort(ranked, new Comparator<FrameSnapshot>() {
            @Override public int compare(FrameSnapshot a, FrameSnapshot b) {
                int ca = counts.containsKey(a.globalIndex) ? counts.get(a.globalIndex) : 0;
                int cb = counts.containsKey(b.globalIndex) ? counts.get(b.globalIndex) : 0;
                int byCount = Integer.compare(cb, ca);
                return byCount != 0 ? byCount : Integer.compare(a.globalIndex, b.globalIndex);
            }
        });
        List<FrameSnapshot> selectedFrames = new ArrayList<FrameSnapshot>();
        selectedFrames.add(gauge);
        for (FrameSnapshot frame : ranked) {
            if (selectedFrames.size() >= MAX_CAMERAS) break;
            if (frame.globalIndex != 0 && !containsFrame(selectedFrames, frame.globalIndex)) {
                selectedFrames.add(frame);
            }
        }
        if (selectedFrames.size() < 3) return Result.failed("INSUFFICIENT_CAMERAS");

        final Set<Integer> selectedIndices = new HashSet<Integer>();
        for (FrameSnapshot frame : selectedFrames) selectedIndices.add(frame.globalIndex);
        final Map<Integer,TrackSnapshot> trackById = new HashMap<Integer,TrackSnapshot>();
        for (TrackSnapshot track : tracks) trackById.put(track.trackId, track);

        List<PointCandidate> candidates = new ArrayList<PointCandidate>();
        for (PointSnapshot point : pointByTrack.values()) {
            TrackSnapshot track = trackById.get(point.trackId);
            if (track == null) continue;
            int visible = 0;
            for (ObservationSnapshot observation : track.observations) {
                if (selectedIndices.contains(observation.frameIndex)) visible++;
            }
            if (visible >= 2) candidates.add(new PointCandidate(point, track, visible));
        }
        Collections.sort(candidates, new Comparator<PointCandidate>() {
            @Override public int compare(PointCandidate a, PointCandidate b) {
                int byViews = Integer.compare(b.visibleViews, a.visibleViews);
                if (byViews != 0) return byViews;
                int bySupport = Integer.compare(b.point.support, a.point.support);
                if (bySupport != 0) return bySupport;
                int byTrackSupport = Double.compare(b.track.support, a.track.support);
                if (byTrackSupport != 0) return byTrackSupport;
                int byError = Double.compare(a.point.reprojectionRmsPx, b.point.reprojectionRmsPx);
                return byError != 0 ? byError : Integer.compare(a.point.trackId, b.point.trackId);
            }
        });
        if (candidates.size() > MAX_POINTS) {
            candidates = new ArrayList<PointCandidate>(candidates.subList(0, MAX_POINTS));
        }
        if (candidates.size() < 6) return Result.failed("INSUFFICIENT_VISIBLE_POINTS");

        Map<Integer,Integer> localCamera = new HashMap<Integer,Integer>();
        List<LocalBundleAdjustmentCore.Camera> cameras = new ArrayList<LocalBundleAdjustmentCore.Camera>();
        List<Integer> cameraGlobalIndices = new ArrayList<Integer>();
        for (int i = 0; i < selectedFrames.size(); i++) {
            FrameSnapshot frame = selectedFrames.get(i);
            localCamera.put(frame.globalIndex, i);
            cameraGlobalIndices.add(frame.globalIndex);
            cameras.add(new LocalBundleAdjustmentCore.Camera(frame.rotation, frame.translation,
                    frame.fx, frame.fy, frame.cx, frame.cy));
        }

        List<LocalBundleAdjustmentCore.Point3> points = new ArrayList<LocalBundleAdjustmentCore.Point3>();
        List<Integer> pointTrackIds = new ArrayList<Integer>();
        List<LocalBundleAdjustmentCore.Observation> observations =
                new ArrayList<LocalBundleAdjustmentCore.Observation>();
        for (PointCandidate candidate : candidates) {
            if (observations.size() >= MAX_OBSERVATIONS) break;
            List<ObservationSnapshot> accepted = new ArrayList<ObservationSnapshot>();
            for (ObservationSnapshot observation : candidate.track.observations) {
                if (localCamera.containsKey(observation.frameIndex) && observation.valid()) accepted.add(observation);
            }
            if (accepted.size() < 2 || observations.size() + accepted.size() > MAX_OBSERVATIONS) continue;
            int pointIndex = points.size();
            points.add(new LocalBundleAdjustmentCore.Point3(
                    candidate.point.x, candidate.point.y, candidate.point.z));
            pointTrackIds.add(candidate.point.trackId);
            for (ObservationSnapshot observation : accepted) {
                observations.add(new LocalBundleAdjustmentCore.Observation(
                        localCamera.get(observation.frameIndex), pointIndex,
                        observation.u, observation.v, observation.weight));
            }
        }
        if (points.size() < 6 || observations.size() < 18) {
            return Result.failed("INSUFFICIENT_BA_OBSERVATIONS");
        }
        LocalBundleAdjustmentCore.Problem problem =
                new LocalBundleAdjustmentCore.Problem(cameras, points, observations);
        return new Result(true, "READY", problem, cameraGlobalIndices, pointTrackIds,
                cameras.size(), points.size(), observations.size());
    }

    private static List<FrameSnapshot> validFrames(List<FrameSnapshot> source) {
        List<FrameSnapshot> result = new ArrayList<FrameSnapshot>();
        if (source != null) {
            for (FrameSnapshot frame : source) if (frame != null && frame.valid()) result.add(frame);
        }
        Collections.sort(result, new Comparator<FrameSnapshot>() {
            @Override public int compare(FrameSnapshot a, FrameSnapshot b) {
                return Integer.compare(a.globalIndex, b.globalIndex);
            }
        });
        return result;
    }

    private static boolean containsFrame(List<FrameSnapshot> frames, int index) {
        for (FrameSnapshot frame : frames) if (frame.globalIndex == index) return true;
        return false;
    }

    public static final class FrameSnapshot {
        public final int globalIndex;
        public final double[][] rotation;
        public final double[] translation;
        public final double fx, fy, cx, cy;
        public FrameSnapshot(int globalIndex, double[][] rotation, double[] translation,
                             double fx, double fy, double cx, double cy) {
            this.globalIndex = globalIndex;
            this.rotation = copy(rotation);
            this.translation = translation == null ? null : translation.clone();
            this.fx = fx; this.fy = fy; this.cx = cx; this.cy = cy;
        }
        boolean valid() {
            return globalIndex >= 0 && rotation != null && rotation.length == 3
                    && rotation[0].length == 3 && rotation[1].length == 3 && rotation[2].length == 3
                    && translation != null && translation.length == 3 && finite(rotation) && finite(translation)
                    && Double.isFinite(fx) && Double.isFinite(fy) && fx > 0 && fy > 0
                    && Double.isFinite(cx) && Double.isFinite(cy);
        }
    }

    public static final class ObservationSnapshot {
        public final int frameIndex, featureIndex;
        public final double u, v, weight;
        public ObservationSnapshot(int frameIndex, int featureIndex, double u, double v, double weight) {
            this.frameIndex = frameIndex; this.featureIndex = featureIndex;
            this.u = u; this.v = v; this.weight = weight;
        }
        boolean valid() {
            return frameIndex >= 0 && featureIndex >= 0 && Double.isFinite(u) && Double.isFinite(v)
                    && Double.isFinite(weight) && weight > 0.0;
        }
    }

    public static final class TrackSnapshot {
        public final int trackId;
        public final double support;
        public final List<ObservationSnapshot> observations;
        public TrackSnapshot(int trackId, double support, List<ObservationSnapshot> observations) {
            this.trackId = trackId; this.support = support;
            this.observations = Collections.unmodifiableList(new ArrayList<ObservationSnapshot>(
                    observations == null ? Collections.<ObservationSnapshot>emptyList() : observations));
        }
        boolean valid() {
            if (trackId < 0 || !Double.isFinite(support) || support < 0 || observations.size() < 2) return false;
            for (ObservationSnapshot observation : observations) if (observation == null || !observation.valid()) return false;
            return true;
        }
    }

    public static final class PointSnapshot {
        public final int trackId, support;
        public final double x, y, z, spatialSpread, reprojectionRmsPx;
        public PointSnapshot(int trackId, double x, double y, double z, int support,
                             double spatialSpread, double reprojectionRmsPx) {
            this.trackId = trackId; this.x = x; this.y = y; this.z = z; this.support = support;
            this.spatialSpread = spatialSpread; this.reprojectionRmsPx = reprojectionRmsPx;
        }
        boolean valid() {
            return trackId >= 0 && support >= 2 && Double.isFinite(x) && Double.isFinite(y)
                    && Double.isFinite(z) && Double.isFinite(spatialSpread) && spatialSpread >= 0
                    && Double.isFinite(reprojectionRmsPx) && reprojectionRmsPx >= 0;
        }
    }

    public static final class Result {
        public final boolean ready;
        public final String status;
        public final LocalBundleAdjustmentCore.Problem problem;
        public final List<Integer> cameraGlobalIndices;
        public final List<Integer> pointTrackIds;
        public final int cameraCount, pointCount, observationCount;
        Result(boolean ready, String status, LocalBundleAdjustmentCore.Problem problem,
               List<Integer> cameraGlobalIndices, List<Integer> pointTrackIds,
               int cameraCount, int pointCount, int observationCount) {
            this.ready = ready; this.status = status; this.problem = problem;
            this.cameraGlobalIndices = Collections.unmodifiableList(new ArrayList<Integer>(cameraGlobalIndices));
            this.pointTrackIds = Collections.unmodifiableList(new ArrayList<Integer>(pointTrackIds));
            this.cameraCount = cameraCount; this.pointCount = pointCount;
            this.observationCount = observationCount;
        }
        static Result failed(String status) {
            return new Result(false, status, null, Collections.<Integer>emptyList(),
                    Collections.<Integer>emptyList(), 0, 0, 0);
        }
        public String summary() {
            return ready ? "BA runtime " + cameraCount + " cámaras · " + pointCount
                    + " puntos · " + observationCount + " observaciones"
                    : "BA runtime bloqueado · " + status;
        }
        public String canonicalJson() {
            StringBuilder json = new StringBuilder(1024);
            json.append("{\n\"schema\":\"skm-runtime-ba-window/1\"")
                    .append(",\n\"status\":\"").append(escape(status)).append("\"")
                    .append(",\n\"ready\":").append(ready)
                    .append(",\n\"cameraCount\":").append(cameraCount)
                    .append(",\n\"pointCount\":").append(pointCount)
                    .append(",\n\"observationCount\":").append(observationCount)
                    .append(",\n\"cameraGlobalIndices\":").append(integerArray(cameraGlobalIndices))
                    .append(",\n\"pointTrackIds\":").append(integerArray(pointTrackIds))
                    .append(",\n\"fingerprint\":\"").append(fingerprintPayload()).append("\"\n}");
            return json.toString();
        }
        public String fingerprint() { return fingerprintPayload(); }
        private String fingerprintPayload() {
            String payload = status + "|" + ready + "|" + cameraGlobalIndices + "|" + pointTrackIds
                    + "|" + cameraCount + "|" + pointCount + "|" + observationCount;
            try {
                byte[] digest = MessageDigest.getInstance("SHA-256")
                        .digest(payload.getBytes(StandardCharsets.UTF_8));
                StringBuilder value = new StringBuilder(64);
                for (byte item : digest) value.append(String.format(Locale.ROOT, "%02x", item & 0xff));
                return value.toString();
            } catch (Exception error) { throw new IllegalStateException(error); }
        }
    }

    private static final class PointCandidate {
        final PointSnapshot point; final TrackSnapshot track; final int visibleViews;
        PointCandidate(PointSnapshot point, TrackSnapshot track, int visibleViews) {
            this.point = point; this.track = track; this.visibleViews = visibleViews;
        }
    }

    private static String integerArray(List<Integer> values) {
        StringBuilder text = new StringBuilder("[");
        for (int i = 0; i < values.size(); i++) {
            if (i > 0) text.append(',');
            text.append(values.get(i));
        }
        return text.append(']').toString();
    }
    private static String escape(String value) {
        return value == null ? "" : value.replace("\\", "\\\\").replace("\"", "\\\"");
    }
    private static double[][] copy(double[][] source) {
        if (source == null) return null;
        double[][] result = new double[source.length][];
        for (int i = 0; i < source.length; i++) result[i] = source[i].clone();
        return result;
    }
    private static boolean finite(double[][] values) {
        for (double[] row : values) for (double value : row) if (!Double.isFinite(value)) return false;
        return true;
    }
    private static boolean finite(double[] values) {
        for (double value : values) if (!Double.isFinite(value)) return false;
        return true;
    }
}

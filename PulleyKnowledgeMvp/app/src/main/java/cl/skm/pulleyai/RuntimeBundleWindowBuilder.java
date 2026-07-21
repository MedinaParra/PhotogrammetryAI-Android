package cl.skm.pulleyai;

import android.content.Context;

import java.io.File;
import java.io.FileOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** Builds the bounded BA window from the shared runtime frame preparation cache. */
public final class RuntimeBundleWindowBuilder {
    private RuntimeBundleWindowBuilder() {}

    /** Compatibility entrypoint; product runtime should prepare and reuse one cache. */
    public static RuntimeBundleWindowCore.Result build(Context context, CaptureStore store,
                                                        String sessionId,
                                                        SessionOverlapAnalyzer.Report report) {
        RuntimeExecutionControlCore.Token control = RuntimeExecutionControlCore.start(10L * 60L * 1000L);
        RuntimeFramePreparationCache.Result cache = RuntimeFramePreparationCache.prepare(
                context, store, sessionId, report, control);
        return build(cache, report, control);
    }

    public static RuntimeBundleWindowCore.Result build(
            RuntimeFramePreparationCache.Result cache,
            SessionOverlapAnalyzer.Report report,
            RuntimeExecutionControlCore.Token control) {
        RuntimeExecutionControlCore.Token token = control == null
                ? RuntimeExecutionControlCore.start(10L * 60L * 1000L) : control;
        if (cache == null || !cache.ready || report == null) {
            return RuntimeBundleWindowCore.Result.failed(cache == null
                    ? "RUNTIME_CACHE_MISSING" : cache.status);
        }
        if (!report.globalPoseGraph.ready() || !report.tracks.ready() || !report.globalCloud.ready()) {
            return RuntimeBundleWindowCore.Result.failed("GLOBAL_RECONSTRUCTION_NOT_READY");
        }
        if (cache.entries.size() != report.globalPoseGraph.totalNodes
                || report.globalPoseGraph.poses.size() != cache.entries.size()) {
            return RuntimeBundleWindowCore.Result.failed("FRAME_POSE_COUNT_MISMATCH");
        }
        try {
            token.checkpoint("WINDOW_FRAMES");
            List<RuntimeBundleWindowCore.FrameSnapshot> frames =
                    new ArrayList<RuntimeBundleWindowCore.FrameSnapshot>();
            for (int index = 0; index < cache.entries.size(); index++) {
                token.checkpoint("WINDOW_FRAME_" + index);
                RuntimeFramePreparationCache.Entry entry = cache.entries.get(index);
                GlobalPoseGraphCore.Pose pose = report.globalPoseGraph.poses.get(index);
                CameraIntrinsicsProvider.Resolution resolution = entry.intrinsics;
                if (pose != null && resolution.available && resolution.intrinsics != null) {
                    EssentialPoseCore.Intrinsics k = resolution.intrinsics;
                    frames.add(new RuntimeBundleWindowCore.FrameSnapshot(index,
                            pose.rotation, pose.translation, k.fx, k.fy, k.cx, k.cy));
                }
            }

            Map<Integer,GlobalSparseCloudCore.FusedPoint> pointByTrack =
                    new HashMap<Integer,GlobalSparseCloudCore.FusedPoint>();
            List<RuntimeBundleWindowCore.PointSnapshot> points =
                    new ArrayList<RuntimeBundleWindowCore.PointSnapshot>();
            for (GlobalSparseCloudCore.FusedPoint point : report.globalCloud.points) {
                pointByTrack.put(point.trackId, point);
                points.add(new RuntimeBundleWindowCore.PointSnapshot(point.trackId,
                        point.x, point.y, point.z, point.support,
                        point.spatialSpread, point.reprojectionRmsPx));
            }

            List<RuntimeBundleWindowCore.TrackSnapshot> tracks =
                    new ArrayList<RuntimeBundleWindowCore.TrackSnapshot>();
            for (MultiViewTrackCore.Track track : report.tracks.tracks) {
                token.checkpoint("WINDOW_TRACK_" + track.id);
                GlobalSparseCloudCore.FusedPoint point = pointByTrack.get(track.id);
                if (point == null) continue;
                double weight = weight(point, track.support);
                List<RuntimeBundleWindowCore.ObservationSnapshot> observations =
                        new ArrayList<RuntimeBundleWindowCore.ObservationSnapshot>();
                for (MultiViewTrackCore.Observation observation : track.observations) {
                    if (observation.frameIndex < 0 || observation.frameIndex >= cache.entries.size()) continue;
                    RuntimeFramePreparationCache.Entry entry = cache.entries.get(observation.frameIndex);
                    if (!entry.intrinsics.available || observation.featureIndex < 0
                            || observation.featureIndex >= entry.features.features.size()) continue;
                    VisualFeatureCore.Feature feature = entry.features.features.get(observation.featureIndex);
                    observations.add(new RuntimeBundleWindowCore.ObservationSnapshot(
                            observation.frameIndex, observation.featureIndex,
                            feature.x, feature.y, weight));
                }
                if (observations.size() >= 2) {
                    tracks.add(new RuntimeBundleWindowCore.TrackSnapshot(
                            track.id, track.support, observations));
                }
            }
            token.checkpoint("WINDOW_READY");
            return RuntimeBundleWindowCore.build(frames, tracks, points);
        } catch (RuntimeExecutionControlCore.AbortedException aborted) {
            throw aborted;
        } catch (Exception error) {
            return RuntimeBundleWindowCore.Result.failed(
                    "WINDOW_BUILD_" + error.getClass().getSimpleName().toUpperCase());
        }
    }

    public static File persist(CaptureStore store, String sessionId,
                               RuntimeBundleWindowCore.Result result) throws Exception {
        File target = new File(store.sessionDir(sessionId), "runtime_ba_window.json");
        try (FileOutputStream output = new FileOutputStream(target)) {
            output.write(result.canonicalJson().getBytes(StandardCharsets.UTF_8));
        }
        return target;
    }

    private static double weight(GlobalSparseCloudCore.FusedPoint point, double support) {
        double raw = Math.max(0.0, support)
                / (1.0 + point.reprojectionRmsPx * point.reprojectionRmsPx
                + 50.0 * point.spatialSpread * point.spatialSpread);
        return Math.max(0.05, Math.min(1.0, raw));
    }
}
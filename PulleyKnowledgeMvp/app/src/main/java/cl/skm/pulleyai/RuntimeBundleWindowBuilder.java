package cl.skm.pulleyai;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Color;

import java.io.File;
import java.io.FileOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** Recreates deterministic feature coordinates and builds the BA window from a real session report. */
public final class RuntimeBundleWindowBuilder {
    private static final int ANALYSIS_WIDTH = 640;
    private static final int FEATURES_PER_FRAME = 420;

    private RuntimeBundleWindowBuilder() {}

    public static RuntimeBundleWindowCore.Result build(Context context, CaptureStore store,
                                                        String sessionId,
                                                        SessionOverlapAnalyzer.Report report) {
        if (context == null || store == null || sessionId == null || report == null) {
            return RuntimeBundleWindowCore.Result.failed("RUNTIME_INPUT_MISSING");
        }
        if (!report.globalPoseGraph.ready() || !report.tracks.ready() || !report.globalCloud.ready()) {
            return RuntimeBundleWindowCore.Result.failed("GLOBAL_RECONSTRUCTION_NOT_READY");
        }
        try {
            List<CaptureStore.Frame> selected = selectFrames(store.frames(sessionId));
            if (selected.size() != report.globalPoseGraph.totalNodes
                    || report.globalPoseGraph.poses.size() != selected.size()) {
                return RuntimeBundleWindowCore.Result.failed("FRAME_POSE_COUNT_MISMATCH");
            }
            CameraIntrinsicsProvider provider = new CameraIntrinsicsProvider(context);
            List<CachedFrame> cached = new ArrayList<CachedFrame>();
            List<RuntimeBundleWindowCore.FrameSnapshot> frames =
                    new ArrayList<RuntimeBundleWindowCore.FrameSnapshot>();
            for (int index = 0; index < selected.size(); index++) {
                CaptureStore.Frame frame = selected.get(index);
                Gray gray = decode(frame.filePath);
                VisualFeatureCore.FeatureSet features = VisualFeatureCore.detect(
                        gray.pixels, gray.width, gray.height, FEATURES_PER_FRAME);
                CameraIntrinsicsProvider.Resolution resolution = provider.resolve(
                        frame, gray.width, gray.height);
                cached.add(new CachedFrame(features, resolution));
                GlobalPoseGraphCore.Pose pose = report.globalPoseGraph.poses.get(index);
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
                GlobalSparseCloudCore.FusedPoint point = pointByTrack.get(track.id);
                if (point == null) continue;
                double weight = weight(point, track.support);
                List<RuntimeBundleWindowCore.ObservationSnapshot> observations =
                        new ArrayList<RuntimeBundleWindowCore.ObservationSnapshot>();
                for (MultiViewTrackCore.Observation observation : track.observations) {
                    if (observation.frameIndex < 0 || observation.frameIndex >= cached.size()) continue;
                    CachedFrame frame = cached.get(observation.frameIndex);
                    if (!frame.intrinsics.available || observation.featureIndex < 0
                            || observation.featureIndex >= frame.features.features.size()) continue;
                    VisualFeatureCore.Feature feature =
                            frame.features.features.get(observation.featureIndex);
                    observations.add(new RuntimeBundleWindowCore.ObservationSnapshot(
                            observation.frameIndex, observation.featureIndex,
                            feature.x, feature.y, weight));
                }
                if (observations.size() >= 2) {
                    tracks.add(new RuntimeBundleWindowCore.TrackSnapshot(
                            track.id, track.support, observations));
                }
            }
            return RuntimeBundleWindowCore.build(frames, tracks, points);
        } catch (Exception error) {
            String name = error.getClass().getSimpleName();
            return RuntimeBundleWindowCore.Result.failed("WINDOW_BUILD_" + name.toUpperCase());
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

    private static List<CaptureStore.Frame> selectFrames(List<CaptureStore.Frame> all) {
        Map<Integer,CaptureStore.Frame> bySequence = new HashMap<Integer,CaptureStore.Frame>();
        List<ReconstructionFrameSelectorCore.Candidate> candidates =
                new ArrayList<ReconstructionFrameSelectorCore.Candidate>();
        for (CaptureStore.Frame frame : all) {
            if (!"ACCEPTED".equals(frame.quality) || !new File(frame.filePath).isFile()) continue;
            bySequence.put(frame.sequence, frame);
            candidates.add(new ReconstructionFrameSelectorCore.Candidate(
                    frame.sequence, frame.band, frame.sector, frame.blur,
                    frame.luma, frame.motion, frame.createdAt, true));
        }
        ReconstructionFrameSelectorCore.Result selection =
                ReconstructionFrameSelectorCore.select(candidates, 2, 48);
        List<CaptureStore.Frame> selected = new ArrayList<CaptureStore.Frame>();
        for (ReconstructionFrameSelectorCore.Candidate candidate : selection.selected) {
            CaptureStore.Frame frame = bySequence.get(candidate.id);
            if (frame != null) selected.add(frame);
        }
        Collections.sort(selected, new Comparator<CaptureStore.Frame>() {
            @Override public int compare(CaptureStore.Frame a, CaptureStore.Frame b) {
                return Integer.compare(a.sequence, b.sequence);
            }
        });
        return selected;
    }

    private static Gray decode(String path) {
        BitmapFactory.Options bounds = new BitmapFactory.Options();
        bounds.inJustDecodeBounds = true;
        BitmapFactory.decodeFile(path, bounds);
        int sample = 1;
        while (Math.max(bounds.outWidth, bounds.outHeight) / sample > ANALYSIS_WIDTH) sample *= 2;
        BitmapFactory.Options options = new BitmapFactory.Options();
        options.inSampleSize = Math.max(1, sample);
        options.inPreferredConfig = Bitmap.Config.ARGB_8888;
        Bitmap bitmap = BitmapFactory.decodeFile(path, options);
        if (bitmap == null) throw new IllegalStateException("IMAGE_DECODE_FAILED");
        try {
            int width = bitmap.getWidth(), height = bitmap.getHeight();
            byte[] gray = new byte[width * height];
            int[] row = new int[width];
            for (int y = 0; y < height; y++) {
                bitmap.getPixels(row, 0, width, 0, y, width, 1);
                for (int x = 0; x < width; x++) {
                    int color = row[x];
                    gray[y * width + x] = (byte) Math.round(0.2126 * Color.red(color)
                            + 0.7152 * Color.green(color) + 0.0722 * Color.blue(color));
                }
            }
            return new Gray(width, height, gray);
        } finally {
            bitmap.recycle();
        }
    }

    private static double weight(GlobalSparseCloudCore.FusedPoint point, double support) {
        double raw = Math.max(0.0, support)
                / (1.0 + point.reprojectionRmsPx * point.reprojectionRmsPx
                + 50.0 * point.spatialSpread * point.spatialSpread);
        return Math.max(0.05, Math.min(1.0, raw));
    }

    private static final class Gray {
        final int width, height; final byte[] pixels;
        Gray(int width, int height, byte[] pixels) {
            this.width = width; this.height = height; this.pixels = pixels;
        }
    }

    private static final class CachedFrame {
        final VisualFeatureCore.FeatureSet features;
        final CameraIntrinsicsProvider.Resolution intrinsics;
        CachedFrame(VisualFeatureCore.FeatureSet features,
                    CameraIntrinsicsProvider.Resolution intrinsics) {
            this.features = features; this.intrinsics = intrinsics;
        }
    }
}

package cl.skm.pulleyai;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Color;

import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** One bounded decode/feature/intrinsics pass shared by supplemental metrics and BA window builders. */
public final class RuntimeFramePreparationCache {
    private static final int ANALYSIS_WIDTH = 640;
    private static final int FEATURES_PER_FRAME = 420;

    private RuntimeFramePreparationCache() {}

    public static Result prepare(Context context, CaptureStore store, String sessionId,
                                 SessionOverlapAnalyzer.Report report,
                                 RuntimeExecutionControlCore.Token control) {
        if (store == null || sessionId == null || report == null) return Result.failed("CACHE_INPUT_MISSING");
        RuntimeExecutionControlCore.Token token = control == null
                ? RuntimeExecutionControlCore.start(10L * 60L * 1000L) : control;
        try {
            token.checkpoint("CACHE_SELECT_FRAMES");
            List<CaptureStore.Frame> selected = selectFrames(store.frames(sessionId));
            if (selected.size() != report.globalPoseGraph.totalNodes) {
                return Result.failed("CACHE_FRAME_POSE_COUNT_MISMATCH");
            }
            CameraIntrinsicsProvider provider = context == null ? null : new CameraIntrinsicsProvider(context);
            List<Entry> entries = new ArrayList<Entry>();
            Map<Integer,Entry> bySequence = new HashMap<Integer,Entry>();
            long decodedBytes = 0L;
            for (int index = 0; index < selected.size(); index++) {
                token.checkpoint("CACHE_FRAME_" + index);
                CaptureStore.Frame source = selected.get(index);
                Decoded decoded = decode(source.filePath);
                VisualFeatureCore.FeatureSet features = VisualFeatureCore.detect(
                        decoded.gray, decoded.width, decoded.height, FEATURES_PER_FRAME);
                CameraIntrinsicsProvider.Resolution intrinsics = provider == null
                        ? CameraIntrinsicsProvider.Resolution.failed("CONTEXT_UNAVAILABLE")
                        : provider.resolve(source, decoded.width, decoded.height);
                Entry entry = new Entry(index, source, decoded.width, decoded.height,
                        decoded.gray, decoded.highlightFraction, decoded.clippedChannelFraction,
                        features, intrinsics);
                entries.add(entry);
                bySequence.put(source.sequence, entry);
                decodedBytes += decoded.gray.length;
            }
            token.checkpoint("CACHE_READY");
            return new Result(true, "READY", entries, bySequence, decodedBytes, entries.size());
        } catch (RuntimeExecutionControlCore.AbortedException aborted) {
            throw aborted;
        } catch (Exception error) {
            return Result.failed("CACHE_" + error.getClass().getSimpleName().toUpperCase());
        }
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

    private static Decoded decode(String path) {
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
            long highlights = 0L, clipped = 0L, count = 0L;
            for (int y = 0; y < height; y++) {
                bitmap.getPixels(row, 0, width, 0, y, width, 1);
                for (int x = 0; x < width; x++) {
                    int color = row[x];
                    int r = Color.red(color), g = Color.green(color), b = Color.blue(color);
                    double luma = 0.2126 * r + 0.7152 * g + 0.0722 * b;
                    gray[y * width + x] = (byte) Math.round(luma);
                    if (luma > 246.0) highlights++;
                    if (r >= 252 || g >= 252 || b >= 252) clipped++;
                    count++;
                }
            }
            return new Decoded(width, height, gray,
                    count == 0 ? 0.0 : highlights / (double) count,
                    count == 0 ? 0.0 : clipped / (double) count);
        } finally {
            bitmap.recycle();
        }
    }

    public static final class Entry {
        public final int frameIndex;
        public final CaptureStore.Frame source;
        public final int width, height;
        public final byte[] gray;
        public final double highlightFraction, clippedChannelFraction;
        public final VisualFeatureCore.FeatureSet features;
        public final CameraIntrinsicsProvider.Resolution intrinsics;

        Entry(int frameIndex, CaptureStore.Frame source, int width, int height, byte[] gray,
              double highlightFraction, double clippedChannelFraction,
              VisualFeatureCore.FeatureSet features,
              CameraIntrinsicsProvider.Resolution intrinsics) {
            this.frameIndex = frameIndex; this.source = source;
            this.width = width; this.height = height; this.gray = gray;
            this.highlightFraction = highlightFraction;
            this.clippedChannelFraction = clippedChannelFraction;
            this.features = features; this.intrinsics = intrinsics;
        }
    }

    public static final class Result {
        public final boolean ready;
        public final String status;
        public final List<Entry> entries;
        public final Map<Integer,Entry> bySequence;
        public final long decodedGrayBytes;
        public final int decodePasses;

        Result(boolean ready, String status, List<Entry> entries, Map<Integer,Entry> bySequence,
               long decodedGrayBytes, int decodePasses) {
            this.ready = ready; this.status = status;
            this.entries = Collections.unmodifiableList(new ArrayList<Entry>(entries));
            this.bySequence = Collections.unmodifiableMap(new HashMap<Integer,Entry>(bySequence));
            this.decodedGrayBytes = decodedGrayBytes; this.decodePasses = decodePasses;
        }

        static Result failed(String status) {
            return new Result(false, status, Collections.<Entry>emptyList(),
                    Collections.<Integer,Entry>emptyMap(), 0L, 0);
        }

        public String summary() {
            return "Caché " + status + " · frames " + entries.size()
                    + " · decodificaciones " + decodePasses
                    + " · gris " + Math.round(decodedGrayBytes / 1024.0) + " KiB";
        }

        public String canonicalJson() {
            return "{\"schema\":\"skm-runtime-frame-cache/1\",\"status\":\"" + status
                    + "\",\"ready\":" + ready + ",\"frames\":" + entries.size()
                    + ",\"decodePasses\":" + decodePasses
                    + ",\"decodedGrayBytes\":" + decodedGrayBytes + "}";
        }
    }

    private static final class Decoded {
        final int width, height; final byte[] gray;
        final double highlightFraction, clippedChannelFraction;
        Decoded(int width, int height, byte[] gray,
                double highlightFraction, double clippedChannelFraction) {
            this.width = width; this.height = height; this.gray = gray;
            this.highlightFraction = highlightFraction;
            this.clippedChannelFraction = clippedChannelFraction;
        }
    }
}
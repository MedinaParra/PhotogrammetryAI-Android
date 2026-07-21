package cl.skm.pulleyai;

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

/** Recomputes bounded visual evidence from the exact frames used by the runtime report. */
public final class RuntimeSupplementalMetricsBuilder {
    private static final int ANALYSIS_WIDTH = 640;
    private static final int FEATURES_PER_FRAME = 420;
    private static final double MINIMUM_BLUR_SCORE = 105.0;

    private RuntimeSupplementalMetricsBuilder() {}

    public static RuntimeSupplementalMetricsCore.Result build(CaptureStore store, String sessionId,
                                                               SessionOverlapAnalyzer.Report report) {
        if (store == null || sessionId == null || report == null) {
            return RuntimeSupplementalMetricsCore.evaluate(
                    Collections.<RuntimeSupplementalMetricsCore.FrameSample>emptyList(),
                    Collections.<RuntimeSupplementalMetricsCore.PairSample>emptyList());
        }
        List<CaptureStore.Frame> selected = selectFrames(store.frames(sessionId));
        List<CachedFrame> frames = new ArrayList<CachedFrame>();
        List<RuntimeSupplementalMetricsCore.FrameSample> frameSamples =
                new ArrayList<RuntimeSupplementalMetricsCore.FrameSample>();
        for (CaptureStore.Frame source : selected) {
            Decoded decoded = decode(source.filePath);
            VisualFeatureCore.FeatureSet features = VisualFeatureCore.detect(
                    decoded.gray, decoded.width, decoded.height, FEATURES_PER_FRAME);
            frames.add(new CachedFrame(source, features));
            frameSamples.add(new RuntimeSupplementalMetricsCore.FrameSample(
                    source.blur, MINIMUM_BLUR_SCORE,
                    decoded.highlightFraction, decoded.clippedChannelFraction));
        }

        Map<Long,SessionOverlapAnalyzer.Pair> reportPairs =
                new HashMap<Long,SessionOverlapAnalyzer.Pair>();
        for (SessionOverlapAnalyzer.Pair pair : report.pairs) {
            reportPairs.put(pairKey(pair.leftSequence, pair.rightSequence), pair);
        }
        List<RuntimeSupplementalMetricsCore.PairSample> pairSamples =
                new ArrayList<RuntimeSupplementalMetricsCore.PairSample>();
        for (int left = 0; left < frames.size(); left++) {
            for (int right = left + 1; right < frames.size(); right++) {
                CachedFrame a = frames.get(left), b = frames.get(right);
                if (!candidate(a.source, b.source)) continue;
                SessionOverlapAnalyzer.Pair reportPair = reportPairs.get(
                        pairKey(a.source.sequence, b.source.sequence));
                if (reportPair == null) continue;
                VisualFeatureCore.PairResult matches = VisualFeatureCore.match(a.features, b.features);
                if (matches.matches.size() < 4) continue;
                List<HomographyModelCompetitionCore.PointPair> points =
                        new ArrayList<HomographyModelCompetitionCore.PointPair>();
                for (VisualFeatureCore.Match match : matches.matches) {
                    VisualFeatureCore.Feature first = a.features.features.get(match.leftIndex);
                    VisualFeatureCore.Feature second = b.features.features.get(match.rightIndex);
                    points.add(new HomographyModelCompetitionCore.PointPair(
                            first.x, first.y, second.x, second.y));
                }
                Ambiguity ambiguity = ambiguity(a.features, b.features, matches.matches.size());
                pairSamples.add(new RuntimeSupplementalMetricsCore.PairSample(
                        points, reportPair.inliers, reportPair.epipolarRmsPx,
                        ambiguity.p75SecondBestRatio, ambiguity.mutualFraction,
                        matches.spatialCoverage));
            }
        }
        return RuntimeSupplementalMetricsCore.evaluate(frameSamples, pairSamples);
    }

    public static File persist(CaptureStore store, String sessionId,
                               RuntimeSupplementalMetricsCore.Result result) throws Exception {
        File target = new File(store.sessionDir(sessionId), "runtime_supplemental_metrics.json");
        try (FileOutputStream output = new FileOutputStream(target)) {
            output.write(result.canonicalJson().getBytes(StandardCharsets.UTF_8));
        }
        return target;
    }

    private static Ambiguity ambiguity(VisualFeatureCore.FeatureSet left,
                                       VisualFeatureCore.FeatureSet right,
                                       int mutualMatches) {
        List<Double> ratios = new ArrayList<Double>();
        int ratioPassed = 0;
        for (VisualFeatureCore.Feature feature : left.features) {
            int best = Integer.MAX_VALUE, second = Integer.MAX_VALUE;
            for (VisualFeatureCore.Feature candidate : right.features) {
                int distance = Long.bitCount(feature.descriptor ^ candidate.descriptor);
                if (distance < best) { second = best; best = distance; }
                else if (distance < second) second = distance;
            }
            if (best == Integer.MAX_VALUE || second == Integer.MAX_VALUE || second <= 0) continue;
            double ratio = Math.max(0.0, Math.min(1.0, best / (double) second));
            ratios.add(ratio);
            if (best <= 24 && best * 100 <= second * 82) ratioPassed++;
        }
        Collections.sort(ratios);
        double p75 = percentile(ratios, 0.75);
        double mutual = ratioPassed == 0 ? 0.0
                : Math.max(0.0, Math.min(1.0, mutualMatches / (double) ratioPassed));
        return new Ambiguity(p75, mutual);
    }

    private static double percentile(List<Double> values, double q) {
        if (values.isEmpty()) return 1.0;
        double position = Math.max(0.0, Math.min(1.0, q)) * (values.size() - 1);
        int low = (int) Math.floor(position), high = (int) Math.ceil(position);
        if (low == high) return values.get(low);
        double fraction = position - low;
        return values.get(low) * (1.0 - fraction) + values.get(high) * fraction;
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

    private static boolean candidate(CaptureStore.Frame left, CaptureStore.Frame right) {
        int gap = Math.abs(left.sector - right.sector);
        gap = Math.min(gap, CoveragePlanner.SECTOR_COUNT - gap);
        return left.band.equals(right.band) ? gap >= 1 && gap <= 2 : gap <= 1;
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
            long highlights = 0, clipped = 0, count = 0;
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
        } finally { bitmap.recycle(); }
    }

    private static long pairKey(int first, int second) {
        int low = Math.min(first, second), high = Math.max(first, second);
        return ((long) low << 32) ^ (high & 0xffffffffL);
    }

    private static final class CachedFrame {
        final CaptureStore.Frame source; final VisualFeatureCore.FeatureSet features;
        CachedFrame(CaptureStore.Frame source, VisualFeatureCore.FeatureSet features) {
            this.source = source; this.features = features;
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
    private static final class Ambiguity {
        final double p75SecondBestRatio, mutualFraction;
        Ambiguity(double p75SecondBestRatio, double mutualFraction) {
            this.p75SecondBestRatio = p75SecondBestRatio; this.mutualFraction = mutualFraction;
        }
    }
}

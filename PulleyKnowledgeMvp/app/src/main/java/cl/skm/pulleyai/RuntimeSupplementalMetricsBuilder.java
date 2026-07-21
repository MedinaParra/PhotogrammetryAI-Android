package cl.skm.pulleyai;

import java.io.File;
import java.io.FileOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** Builds bounded visual safety evidence from a shared runtime preparation cache. */
public final class RuntimeSupplementalMetricsBuilder {
    private static final double MINIMUM_BLUR_SCORE = 105.0;

    private RuntimeSupplementalMetricsBuilder() {}

    /** Compatibility entrypoint; runtime product flow should prepare and reuse one shared cache. */
    public static RuntimeSupplementalMetricsCore.Result build(CaptureStore store, String sessionId,
                                                                SessionOverlapAnalyzer.Report report) {
        RuntimeExecutionControlCore.Token control = RuntimeExecutionControlCore.start(10L * 60L * 1000L);
        RuntimeFramePreparationCache.Result cache = RuntimeFramePreparationCache.prepare(
                null, store, sessionId, report, control);
        return build(cache, report, control);
    }

    public static RuntimeSupplementalMetricsCore.Result build(
            RuntimeFramePreparationCache.Result cache,
            SessionOverlapAnalyzer.Report report,
            RuntimeExecutionControlCore.Token control) {
        RuntimeExecutionControlCore.Token token = control == null
                ? RuntimeExecutionControlCore.start(10L * 60L * 1000L) : control;
        if (cache == null || !cache.ready || report == null) {
            return RuntimeSupplementalMetricsCore.evaluate(
                    Collections.<RuntimeSupplementalMetricsCore.FrameSample>emptyList(),
                    Collections.<RuntimeSupplementalMetricsCore.PairSample>emptyList());
        }
        token.checkpoint("SUPPLEMENTAL_FRAMES");
        List<RuntimeSupplementalMetricsCore.FrameSample> frameSamples =
                new ArrayList<RuntimeSupplementalMetricsCore.FrameSample>();
        for (RuntimeFramePreparationCache.Entry entry : cache.entries) {
            token.checkpoint("SUPPLEMENTAL_FRAME_" + entry.frameIndex);
            frameSamples.add(new RuntimeSupplementalMetricsCore.FrameSample(
                    entry.source.blur, MINIMUM_BLUR_SCORE,
                    entry.highlightFraction, entry.clippedChannelFraction));
        }

        Map<Long,SessionOverlapAnalyzer.Pair> reportPairs =
                new HashMap<Long,SessionOverlapAnalyzer.Pair>();
        for (SessionOverlapAnalyzer.Pair pair : report.pairs) {
            reportPairs.put(pairKey(pair.leftSequence, pair.rightSequence), pair);
        }
        List<RuntimeSupplementalMetricsCore.PairSample> pairSamples =
                new ArrayList<RuntimeSupplementalMetricsCore.PairSample>();
        for (int left = 0; left < cache.entries.size(); left++) {
            for (int right = left + 1; right < cache.entries.size(); right++) {
                token.checkpoint("SUPPLEMENTAL_PAIR_" + left + "_" + right);
                RuntimeFramePreparationCache.Entry a = cache.entries.get(left);
                RuntimeFramePreparationCache.Entry b = cache.entries.get(right);
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
        token.checkpoint("SUPPLEMENTAL_READY");
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

    private static boolean candidate(CaptureStore.Frame left, CaptureStore.Frame right) {
        int gap = Math.abs(left.sector - right.sector);
        gap = Math.min(gap, CoveragePlanner.SECTOR_COUNT - gap);
        return left.band.equals(right.band) ? gap >= 1 && gap <= 2 : gap <= 1;
    }

    private static long pairKey(int first, int second) {
        int low = Math.min(first, second), high = Math.max(first, second);
        return ((long) low << 32) ^ (high & 0xffffffffL);
    }

    private static final class Ambiguity {
        final double p75SecondBestRatio, mutualFraction;
        Ambiguity(double p75SecondBestRatio, double mutualFraction) {
            this.p75SecondBestRatio = p75SecondBestRatio;
            this.mutualFraction = mutualFraction;
        }
    }
}
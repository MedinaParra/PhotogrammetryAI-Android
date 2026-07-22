package cl.skm.pulleyai;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public final class AdaptivePairDiagnosticsCoreV66Test {
    public static void main(String[] args) {
        List<VisualFeatureCore.Feature> leftFeatures =
                new ArrayList<VisualFeatureCore.Feature>();
        List<VisualFeatureCore.Feature> rightFeatures =
                new ArrayList<VisualFeatureCore.Feature>();
        for (int i = 0; i < 24; i++) {
            long descriptor = 0x0101010101010101L * (i + 1L);
            leftFeatures.add(new VisualFeatureCore.Feature(
                    20 + (i % 6) * 60,
                    20 + (i / 6) * 70,
                    descriptor, 1000 + i));
            rightFeatures.add(new VisualFeatureCore.Feature(
                    24 + (i % 6) * 60,
                    23 + (i / 6) * 70,
                    descriptor ^ (1L << (i % 63)), 1000 + i));
        }
        VisualFeatureCore.FeatureSet left = new VisualFeatureCore.FeatureSet(
                400, 320, leftFeatures, 24, 1.0);
        VisualFeatureCore.FeatureSet right = new VisualFeatureCore.FeatureSet(
                400, 320, rightFeatures, 24, 1.0);
        AdaptivePairDiagnosticsCore.MatchResult match =
                AdaptivePairDiagnosticsCore.match(left, right);
        require(match.observations.size() >= 20, "adaptive mutual matches");
        require(match.roiCoverage >= 0.70, "relative ROI coverage");

        AdaptivePairDiagnosticsCore.PairDiagnostic usable =
                AdaptivePairDiagnosticsCore.classify(
                        1, 2, "LOW", "LOW", 1,
                        match, true, 20, 0.50, 1.2);
        require(usable.usable(), "geometrically supported pair must be usable");
        require(!usable.repetitiveUnsupported,
                "repetition needs combined evidence");

        AdaptivePairDiagnosticsCore.MatchResult repetitive =
                new AdaptivePairDiagnosticsCore.MatchResult(
                        match.observations, 80, 0.97, 0.10, 0.05, "COMPLETE");
        AdaptivePairDiagnosticsCore.PairDiagnostic blocked =
                AdaptivePairDiagnosticsCore.classify(
                        2, 3, "LOW", "LOW", 1,
                        repetitive, true, 10, 0.15, 2.0);
        require(!blocked.usable(),
                "unsupported repetitive pair must remain weak");
        require(blocked.repetitiveUnsupported,
                "combined repetition evidence");

        List<AdaptivePairDiagnosticsCore.PairDiagnostic> pairs = Arrays.asList(
                usable,
                AdaptivePairDiagnosticsCore.classify(
                        2, 3, "LOW", "HIGH", 0,
                        match, true, 18, 0.45, 1.4),
                AdaptivePairDiagnosticsCore.classify(
                        3, 4, "HIGH", "HIGH", 1,
                        match, true, 17, 0.40, 1.5));
        AdaptivePairDiagnosticsCore.GraphResult graph =
                AdaptivePairDiagnosticsCore.graph(
                        Arrays.asList(1, 2, 3, 4), pairs);
        require(graph.connected(),
                "usable pair graph must connect all frames");
        require(graph.acceptedEdges == 3, "accepted edge count");
        System.out.println("adaptive ZIP pair diagnostics v66: OK");
    }

    private static void require(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }
}

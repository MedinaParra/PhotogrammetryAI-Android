package cl.skm.pulleyai;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public final class ImportedBridgeEvidenceCoreTest {
    public static void main(String[] args) {
        ImportedComponentGeometryCore.Component first =
                new ImportedComponentGeometryCore.Component("component-01",
                        Arrays.asList(1, 3, 4, 7, 24, 32, 33, 41), true, true);
        ImportedComponentGeometryCore.Component second =
                new ImportedComponentGeometryCore.Component("component-02",
                        Arrays.asList(10, 11, 13, 14, 16, 23), false, true);
        ImportedComponentGeometryCore.Result topology =
                new ImportedComponentGeometryCore.Result(true,
                        "LOCAL_COMPONENT_GEOMETRY_ONLY", 14, 14, 0,
                        Arrays.asList(first, second), 0,
                        false, true,
                        new ImportedComponentGeometryCore.BridgeRecommendation(
                                41, 14, "component-01", "component-02", true, 0.0));

        List<ImportedBridgeEvidenceCore.Candidate> candidates =
                new ArrayList<ImportedBridgeEvidenceCore.Candidate>();
        candidates.add(new ImportedBridgeEvidenceCore.Candidate(
                14, 41, "HIGH", "HIGH", "WEAK", true,
                10, 0.526, 0.413, 0.48, 0.61));
        candidates.add(new ImportedBridgeEvidenceCore.Candidate(
                13, 33, "HIGH", "HIGH", "WEAK", true,
                9, 0.60, 0.456, 0.42, 0.58));
        candidates.add(new ImportedBridgeEvidenceCore.Candidate(
                7, 16, "LOW", "HIGH", "BRIDGE", true,
                9, 0.42, 0.80, 0.51, 0.55));
        candidates.add(new ImportedBridgeEvidenceCore.Candidate(
                1, 4, "LOW", "LOW", "STRONG", true,
                50, 0.75, 0.30, 0.70, 0.80));
        candidates.add(new ImportedBridgeEvidenceCore.Candidate(
                10, 13, "HIGH", "HIGH", "STRONG", true,
                45, 0.70, 0.40, 0.65, 0.75));

        ImportedBridgeEvidenceCore.Result result =
                ImportedBridgeEvidenceCore.analyze(topology, candidates);
        check(result.solved, "analysis solved");
        check(result.crossComponentPairs == 3, "three cross-component pairs");
        check(result.recommendation != null, "recommendation present");
        check(result.recommendation.candidate.leftFrame == 14
                        && result.recommendation.candidate.rightFrame == 41,
                "best measured bridge selected");
        check("HIGH_CAPTURE_BRIDGE".equals(result.recommendation.tier),
                "high capture tier");
        check(!result.autoPromoted, "never auto-promoted");
        check(result.summary().contains("3 capturas intermedias"),
                "field instruction");
        check(result.canonicalJson().contains("skm-imported-bridge-evidence/1"),
                "schema");
        System.out.println("ImportedBridgeEvidenceCoreTest PASS · " + result.summary());
    }

    private static void check(boolean value, String label) {
        if (!value) throw new AssertionError(label);
    }
}

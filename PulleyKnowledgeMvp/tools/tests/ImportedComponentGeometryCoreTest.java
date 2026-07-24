package cl.skm.pulleyai;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public final class ImportedComponentGeometryCoreTest {
    public static void main(String[] args) {
        List<ImportedTrackAssemblerCore.PairEvidence> evidence =
                new ArrayList<ImportedTrackAssemblerCore.PairEvidence>();
        evidence.add(pair("1-2", 1, 2, "LOW", "LOW", 0));
        evidence.add(pair("2-3", 2, 3, "LOW", "LOW", 0));
        evidence.add(pair("3-4", 3, 4, "LOW", "LOW", 0));
        evidence.add(pair("10-11", 10, 11, "HIGH", "HIGH", 100));
        evidence.add(pair("11-12", 11, 12, "HIGH", "HIGH", 100));
        evidence.add(new ImportedTrackAssemblerCore.PairEvidence(
                "bridge-excluded", 4, 10, "LOW", "HIGH", "BRIDGE",
                Arrays.asList(new ImportedTrackAssemblerCore.Correspondence(
                        99, 99, 10, 10, 12, 12))));

        ImportedTrackAssemblerCore.Result tracks =
                ImportedTrackAssemblerCore.assemble(evidence, 3);
        check(tracks.tracks.size() == 8, "eight point tracks across two components");
        check(tracks.bridgePairsExcluded == 1, "bridge excluded");
        ImportedSeedGeometryCore.Result seed = new ImportedSeedGeometryCore.Result(
                true, true, "SEED_GEOMETRY_READY", 1, 7,
                1, 2, 0.75, 960.0, null, null);
        ImportedComponentGeometryCore.Result result =
                ImportedComponentGeometryCore.analyze(tracks, seed, 8);
        check(result.solved, "component analysis solved");
        check(result.components.size() == 2, "two components");
        check(result.components.get(0).frames.size() == 4, "largest four");
        check(result.components.get(1).frames.size() == 3, "second three");
        check(result.seedComponentIndex == 0, "seed association");
        check(result.localGeometryReady, "local geometry ready");
        check(!result.globalConnected, "global blocked");
        check(result.isolatedFrames == 1, "one isolated");
        check(result.bridge != null && result.bridge.crossRingPreferred,
                "cross-ring bridge recommendation");
        check(result.canonicalJson().contains("skm-imported-components/1"), "schema");
        System.out.println("ImportedComponentGeometryCoreTest PASS · " + result.summary());
    }

    private static ImportedTrackAssemblerCore.PairEvidence pair(
            String id, int left, int right, String leftBand, String rightBand,
            int featureBase) {
        List<ImportedTrackAssemblerCore.Correspondence> correspondences =
                new ArrayList<ImportedTrackAssemblerCore.Correspondence>();
        for (int i = 0; i < 4; i++) {
            correspondences.add(new ImportedTrackAssemblerCore.Correspondence(
                    featureBase + i, featureBase + i,
                    100 + i * 7, 120 + i * 5,
                    105 + i * 7, 121 + i * 5));
        }
        return new ImportedTrackAssemblerCore.PairEvidence(id, left, right,
                leftBand, rightBand, "STRONG", correspondences);
    }

    private static void check(boolean value, String label) {
        if (!value) throw new AssertionError(label);
    }
}

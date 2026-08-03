package cl.skm.pulleyai;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public final class ImportedTrackAssemblerCoreV68Test {
    public static void main(String[] args) {
        List<ImportedTrackAssemblerCore.PairEvidence> pairs =
                new ArrayList<ImportedTrackAssemblerCore.PairEvidence>();
        pairs.add(pair("01-02", 1, 2, "LOW", "LOW", "STRONG",
                correspondence(10, 20, 10, 10, 12, 10),
                correspondence(11, 21, 30, 10, 32, 10)));
        pairs.add(pair("02-03", 2, 3, "LOW", "LOW", "USABLE",
                correspondence(20, 30, 12, 10, 14, 11),
                correspondence(21, 31, 32, 10, 34, 11)));
        pairs.add(pair("03-04", 3, 4, "LOW", "HIGH", "STRONG",
                correspondence(30, 40, 14, 11, 16, 12),
                correspondence(31, 41, 34, 11, 36, 12)));
        pairs.add(pair("04-05-bridge", 4, 5, "HIGH", "HIGH", "BRIDGE",
                correspondence(40, 50, 16, 12, 18, 13)));
        pairs.add(pair("collision", 1, 3, "LOW", "LOW", "USABLE",
                correspondence(99, 30, 80, 80, 14, 11)));
        pairs.add(pair("weak", 5, 6, "HIGH", "HIGH", "WEAK",
                correspondence(50, 60, 18, 13, 20, 14)));

        ImportedTrackAssemblerCore.Result result =
                ImportedTrackAssemblerCore.assemble(pairs, 3);
        require(result.inputPairs == 6, "input pairs");
        require(result.primaryPairs == 4, "primary pairs " + result.primaryPairs);
        require(result.bridgePairsExcluded == 1, "bridge excluded");
        require(result.weakPairsExcluded == 1, "weak excluded");
        require(result.frameCollisionRejects == 1,
                "collision rejected " + result.frameCollisionRejects);
        require(result.tracks.size() == 2, "two tracks " + result.tracks.size());
        require(result.crossRingTracks == 2, "cross-ring tracks");
        require(result.maximumTrackLength == 4, "max length");
        require(result.observationCount == 8, "observation count");
        require(result.framesRepresented == 4, "frames represented");
        for (ImportedTrackAssemblerCore.Track track : result.tracks) {
            require(track.observations.size() == 4, "track length");
            require(track.crossRing, "track crosses rings");
            Set<Integer> frames = new HashSet<Integer>();
            for (ImportedTrackAssemblerCore.Node observation : track.observations) {
                require(frames.add(observation.frame), "no duplicate frame in track");
                require(observation.frame != 5, "bridge frame excluded");
            }
            require(track.sourcePairs.containsAll(
                    Arrays.asList("01-02", "02-03", "03-04")),
                    "provenance retained " + track.sourcePairs);
        }
        String json = result.canonicalJson();
        require(json.contains("\"bridgeEvidenceUsedForGeometry\":false"),
                "fail-closed marker");
        require(json.contains("\"crossRingTracks\":2"), "cross-ring JSON");
        require(!json.contains("04-05-bridge"), "bridge provenance absent");
        System.out.println("ImportedTrackAssemblerCoreV68Test PASS tracks="
                + result.tracks.size() + " observations=" + result.observationCount
                + " collisionRejects=" + result.frameCollisionRejects);
    }

    private static ImportedTrackAssemblerCore.PairEvidence pair(
            String id, int left, int right, String leftBand, String rightBand,
            String status, ImportedTrackAssemblerCore.Correspondence... values) {
        return new ImportedTrackAssemblerCore.PairEvidence(id, left, right,
                leftBand, rightBand, status, Arrays.asList(values));
    }

    private static ImportedTrackAssemblerCore.Correspondence correspondence(
            int leftFeature, int rightFeature,
            double x, double y, double u, double v) {
        return new ImportedTrackAssemblerCore.Correspondence(
                leftFeature, rightFeature, x, y, u, v);
    }

    private static void require(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }
}

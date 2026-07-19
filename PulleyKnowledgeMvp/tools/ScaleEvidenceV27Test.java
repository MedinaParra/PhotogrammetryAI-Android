import cl.skm.pulleyai.ScaleEvidenceResolver;
import java.util.ArrayList;
import java.util.List;

public final class ScaleEvidenceV27Test {
    public static void main(String[] args) {
        List<ScaleEvidenceResolver.Evidence> evidence = new ArrayList<ScaleEvidenceResolver.Evidence>();
        evidence.add(new ScaleEvidenceResolver.Evidence(
                ScaleEvidenceResolver.Kind.SHELL_LENGTH, 1520.0, 152.0, 0.98, "largo de manto"));
        evidence.add(new ScaleEvidenceResolver.Evidence(
                ScaleEvidenceResolver.Kind.CALIBRATED_MARKER, 500.0, 49.8, 0.99, "marcador 500 mm"));
        evidence.add(new ScaleEvidenceResolver.Evidence(
                ScaleEvidenceResolver.Kind.ARCORE_DEPTH, 1000.0, 103.0, 0.70, "depth"));
        evidence.add(new ScaleEvidenceResolver.Evidence(
                ScaleEvidenceResolver.Kind.MANUAL_DISTANCE, 1200.0, 100.0, 0.95, "cota contradictoria"));
        ScaleEvidenceResolver.Resolution result = ScaleEvidenceResolver.resolve(evidence);
        require(result.resolved, "scale should resolve");
        require(Math.abs(result.millimetresPerUnit - 10.0) < 0.15, "resolved scale");
        require(result.rejected.size() == 1, "one contradiction should be excluded");
        require(result.confidence > 0.80, "combined confidence");
        ScaleEvidenceResolver.Resolution empty = ScaleEvidenceResolver.resolve(new ArrayList<ScaleEvidenceResolver.Evidence>());
        require(!empty.resolved, "empty evidence must not resolve");
        System.out.println("ScaleEvidenceV27Test OK scale=" + result.millimetresPerUnit
                + " confidence=" + result.confidence);
    }
    private static void require(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }
}

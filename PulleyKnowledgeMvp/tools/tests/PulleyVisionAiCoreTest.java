package cl.skm.pulleyai;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public final class PulleyVisionAiCoreTest {
    public static void main(String[] args) {
        testTensorContracts();
        testGoodScene();
        testCompetingPulley();
        testObstruction();
        System.out.println("PulleyVisionAiCoreTest PASS");
    }

    private static void testTensorContracts() {
        List<int[]> yoloOutputs = new ArrayList<int[]>();
        yoloOutputs.add(new int[]{1, 43, 5376});
        yoloOutputs.add(new int[]{1, 32, 128, 128});
        VisionTensorContractCore.Validation yolo = VisionTensorContractCore.validateYolo(
                new int[]{1, 512, 512, 3}, "INT8", yoloOutputs);
        require(yolo.valid, yolo.issues.toString());

        VisionTensorContractCore.Validation classifier =
                VisionTensorContractCore.validateClassifier(
                        new int[]{1, 224, 224, 3}, "UINT8",
                        Arrays.asList(new int[]{1, 11}));
        require(classifier.valid, classifier.issues.toString());

        VisionTensorContractCore.Validation bad = VisionTensorContractCore.validateYolo(
                new int[]{1, 640, 640, 3}, "FLOAT32",
                Arrays.asList(new int[]{1, 20, 100}));
        require(!bad.valid, "Invalid YOLO contract was accepted");
    }

    private static void testGoodScene() {
        List<PulleySceneAwarenessCore.Instance> instances = Arrays.asList(
                new PulleySceneAwarenessCore.Instance("pulley_shell", 0.93f, 0.58f, 0.50f, 0.52f),
                new PulleySceneAwarenessCore.Instance("pulley_end_disc", 0.82f, 0.08f, 0.22f, 0.50f),
                new PulleySceneAwarenessCore.Instance("pulley_shaft", 0.75f, 0.05f, 0.50f, 0.52f)
        );
        Map<String, Float> scores = new HashMap<String, Float>();
        scores.put("good_capture", 0.91f);
        PulleySceneAwarenessCore.State state = PulleySceneAwarenessCore.evaluate(
                instances, new PulleySceneAwarenessCore.Quality(scores));
        require("ACCEPT".equals(state.decision), state.summary());
        require(state.shellDetected, "Shell not detected");
        require(state.leftEndDetected, "Left end not detected");
        require(state.competingPulleyCount == 0, "Unexpected competitor");
    }

    private static void testCompetingPulley() {
        List<PulleySceneAwarenessCore.Instance> instances = Arrays.asList(
                new PulleySceneAwarenessCore.Instance("pulley_shell", 0.90f, 0.45f, 0.45f, 0.50f),
                new PulleySceneAwarenessCore.Instance("other_pulley", 0.88f, 0.20f, 0.80f, 0.45f)
        );
        PulleySceneAwarenessCore.State state = PulleySceneAwarenessCore.evaluate(
                instances, new PulleySceneAwarenessCore.Quality(new HashMap<String, Float>()));
        require("REJECT".equals(state.decision), state.summary());
        require(state.competingPulleyCount == 1, "Competitor not counted");
    }

    private static void testObstruction() {
        List<PulleySceneAwarenessCore.Instance> instances = Arrays.asList(
                new PulleySceneAwarenessCore.Instance("pulley_shell", 0.95f, 0.50f, 0.50f, 0.50f),
                new PulleySceneAwarenessCore.Instance("person", 0.80f, 0.12f, 0.55f, 0.50f)
        );
        Map<String, Float> scores = new HashMap<String, Float>();
        scores.put("person_obstruction", 0.72f);
        PulleySceneAwarenessCore.State state = PulleySceneAwarenessCore.evaluate(
                instances, new PulleySceneAwarenessCore.Quality(scores));
        require("REJECT".equals(state.decision), state.summary());
        require(state.personDetected, "Person not detected");
    }

    private static void require(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }
}

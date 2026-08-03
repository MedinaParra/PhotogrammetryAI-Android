package cl.skm.pulleyai;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/**
 * Fail-closed tensor contract for the two custom INT8 models used by the Android vision pipeline.
 * It deliberately accepts the two common NHWC/NCHW and transposed YOLO export layouts, while
 * rejecting unknown outputs before any capture decision can depend on them.
 */
public final class VisionTensorContractCore {
    public static final int YOLO_WIDTH = 512;
    public static final int YOLO_HEIGHT = 512;
    public static final int CLASSIFIER_WIDTH = 224;
    public static final int CLASSIFIER_HEIGHT = 224;
    public static final int YOLO_MASK_COEFFICIENTS = 32;

    public static final List<String> SEGMENTATION_CLASSES = Collections.unmodifiableList(Arrays.asList(
            "pulley_shell",
            "pulley_end_disc",
            "pulley_shaft",
            "bearing_housing",
            "other_pulley",
            "person",
            "obstruction"
    ));

    public static final List<String> QUALITY_CLASSES = Collections.unmodifiableList(Arrays.asList(
            "good_capture",
            "wrong_target",
            "multiple_pulleys",
            "partial_shell",
            "person_obstruction",
            "tool_obstruction",
            "motion_blur",
            "strong_reflection",
            "too_dark",
            "too_far",
            "too_close"
    ));

    private VisionTensorContractCore() {}

    public static Validation validateYolo(int[] inputShape, String inputType,
                                          List<int[]> outputShapes) {
        List<String> issues = new ArrayList<String>();
        if (!isImageInput(inputShape, YOLO_HEIGHT, YOLO_WIDTH)) {
            issues.add("YOLO input must be [1,512,512,3] or [1,3,512,512], got "
                    + shape(inputShape));
        }
        if (!isInt8Type(inputType)) {
            issues.add("YOLO input must be INT8/UINT8, got " + safe(inputType));
        }
        int channels = 4 + SEGMENTATION_CLASSES.size() + YOLO_MASK_COEFFICIENTS;
        boolean detections = false;
        boolean prototypes = false;
        if (outputShapes != null) {
            for (int[] output : outputShapes) {
                if (output == null) continue;
                if (output.length == 3 && output[0] == 1) {
                    int a = output[1];
                    int b = output[2];
                    if ((a == channels && b >= 500) || (b == channels && a >= 500)) {
                        detections = true;
                    }
                }
                if (output.length == 4 && output[0] == 1) {
                    int maskAxis = -1;
                    for (int i = 1; i < output.length; i++) {
                        if (output[i] == YOLO_MASK_COEFFICIENTS) maskAxis = i;
                    }
                    int spatialProduct = 1;
                    for (int i = 1; i < output.length; i++) {
                        if (i != maskAxis) spatialProduct *= Math.max(1, output[i]);
                    }
                    if (maskAxis >= 0 && spatialProduct >= 80 * 80) prototypes = true;
                }
            }
        }
        if (!detections) {
            issues.add("YOLO detection output missing; expected 43 channels and >=500 candidates");
        }
        if (!prototypes) {
            issues.add("YOLO mask prototype output missing; expected 32 prototype channels");
        }
        return new Validation(issues.isEmpty(), issues);
    }

    public static Validation validateClassifier(int[] inputShape, String inputType,
                                                List<int[]> outputShapes) {
        List<String> issues = new ArrayList<String>();
        if (!isImageInput(inputShape, CLASSIFIER_HEIGHT, CLASSIFIER_WIDTH)) {
            issues.add("MobileNet input must be [1,224,224,3] or [1,3,224,224], got "
                    + shape(inputShape));
        }
        if (!isInt8Type(inputType)) {
            issues.add("MobileNet input must be INT8/UINT8, got " + safe(inputType));
        }
        boolean classes = false;
        if (outputShapes != null) {
            for (int[] output : outputShapes) {
                if (output == null) continue;
                if (output.length == 2 && output[0] == 1
                        && output[1] == QUALITY_CLASSES.size()) classes = true;
                if (output.length == 1 && output[0] == QUALITY_CLASSES.size()) classes = true;
            }
        }
        if (!classes) {
            issues.add("MobileNet output must contain exactly " + QUALITY_CLASSES.size()
                    + " quality logits/probabilities");
        }
        return new Validation(issues.isEmpty(), issues);
    }

    private static boolean isImageInput(int[] shape, int height, int width) {
        if (shape == null || shape.length != 4 || shape[0] != 1) return false;
        return (shape[1] == height && shape[2] == width && shape[3] == 3)
                || (shape[1] == 3 && shape[2] == height && shape[3] == width);
    }

    private static boolean isInt8Type(String type) {
        String value = safe(type).toUpperCase(java.util.Locale.ROOT);
        return value.contains("INT8") || value.contains("UINT8");
    }

    private static String safe(String value) {
        return value == null ? "" : value;
    }

    public static String shape(int[] values) {
        return values == null ? "null" : Arrays.toString(values);
    }

    public static final class Validation {
        public final boolean valid;
        public final List<String> issues;

        Validation(boolean valid, List<String> issues) {
            this.valid = valid;
            this.issues = Collections.unmodifiableList(new ArrayList<String>(issues));
        }

        public String summary(String modelName) {
            if (valid) return modelName + " tensor contract PASS";
            return modelName + " tensor contract FAIL: " + issues;
        }
    }
}

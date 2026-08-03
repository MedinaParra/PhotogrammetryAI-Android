package cl.skm.pulleyai;

import android.content.Context;

import com.google.android.gms.tflite.java.TfLite;

import org.tensorflow.lite.InterpreterApi;
import org.tensorflow.lite.InterpreterApi.Options.TfLiteRuntime;
import org.tensorflow.lite.Tensor;

import java.io.File;
import java.io.FileInputStream;
import java.nio.MappedByteBuffer;
import java.nio.channels.FileChannel;
import java.util.ArrayList;
import java.util.List;

/** Initializes both custom INT8 models through Google Play services LiteRT and validates tensors. */
public final class VisionAiRuntime implements AutoCloseable {
    public interface Callback {
        void onComplete(Report report);
    }

    private final Context context;
    private final VisionModelStore store;
    private InterpreterApi yolo;
    private InterpreterApi classifier;

    public VisionAiRuntime(Context context) {
        this.context = context.getApplicationContext();
        this.store = new VisionModelStore(this.context);
    }

    public void initialize(Callback callback) {
        final Callback safeCallback = callback == null ? report -> { } : callback;
        VisionModelStore.Status status = store.status();
        if (!status.ready) {
            safeCallback.onComplete(Report.failed("MODEL_PACKAGE_NOT_READY", status.summary()));
            return;
        }
        TfLite.initialize(context)
                .addOnSuccessListener(ignored -> {
                    try {
                        close();
                        InterpreterApi.Options options = new InterpreterApi.Options()
                                .setRuntime(TfLiteRuntime.FROM_SYSTEM_ONLY)
                                .setNumThreads(4);
                        yolo = InterpreterApi.create(map(store.yoloModel()), options);
                        classifier = InterpreterApi.create(map(store.classifierModel()),
                                new InterpreterApi.Options()
                                        .setRuntime(TfLiteRuntime.FROM_SYSTEM_ONLY)
                                        .setNumThreads(4));
                        safeCallback.onComplete(inspect(status));
                    } catch (Throwable error) {
                        close();
                        safeCallback.onComplete(Report.failed("LITERT_INIT_FAILED", message(error)));
                    }
                })
                .addOnFailureListener(error ->
                        safeCallback.onComplete(Report.failed("PLAY_SERVICES_LITERT_FAILED", message(error))));
    }

    private Report inspect(VisionModelStore.Status packageStatus) {
        if (yolo == null || classifier == null) {
            return Report.failed("INTERPRETER_MISSING", "Intérpretes no inicializados");
        }
        int[] yoloInput = yolo.getInputTensor(0).shape();
        String yoloType = yolo.getInputTensor(0).dataType().toString();
        List<int[]> yoloOutputs = shapes(yolo);
        VisionTensorContractCore.Validation yoloValidation =
                VisionTensorContractCore.validateYolo(yoloInput, yoloType, yoloOutputs);

        int[] classifierInput = classifier.getInputTensor(0).shape();
        String classifierType = classifier.getInputTensor(0).dataType().toString();
        List<int[]> classifierOutputs = shapes(classifier);
        VisionTensorContractCore.Validation classifierValidation =
                VisionTensorContractCore.validateClassifier(
                        classifierInput, classifierType, classifierOutputs);

        boolean ready = yoloValidation.valid && classifierValidation.valid;
        String summary = packageStatus.summary()
                + "\n" + yoloValidation.summary("YOLO11n-seg")
                + " · input " + VisionTensorContractCore.shape(yoloInput)
                + " · outputs " + shapesText(yoloOutputs)
                + "\n" + classifierValidation.summary("MobileNetV3-Small")
                + " · input " + VisionTensorContractCore.shape(classifierInput)
                + " · outputs " + shapesText(classifierOutputs)
                + "\nRuntime: Google Play services LiteRT · CPU 4 hilos · GPU deshabilitada hasta benchmark físico";
        return new Report(ready, ready ? "READY" : "TENSOR_CONTRACT_FAILED", summary,
                yoloValidation, classifierValidation);
    }

    private static List<int[]> shapes(InterpreterApi interpreter) {
        List<int[]> values = new ArrayList<int[]>();
        int count = interpreter.getOutputTensorCount();
        for (int i = 0; i < count; i++) {
            Tensor tensor = interpreter.getOutputTensor(i);
            values.add(tensor.shape());
        }
        return values;
    }

    private static String shapesText(List<int[]> shapes) {
        StringBuilder text = new StringBuilder();
        for (int i = 0; i < shapes.size(); i++) {
            if (i > 0) text.append(' ');
            text.append(VisionTensorContractCore.shape(shapes.get(i)));
        }
        return text.toString();
    }

    private static MappedByteBuffer map(File file) throws Exception {
        try (FileInputStream input = new FileInputStream(file);
             FileChannel channel = input.getChannel()) {
            return channel.map(FileChannel.MapMode.READ_ONLY, 0, channel.size());
        }
    }

    @Override public void close() {
        if (yolo != null) {
            try { yolo.close(); } catch (Throwable ignored) { }
            yolo = null;
        }
        if (classifier != null) {
            try { classifier.close(); } catch (Throwable ignored) { }
            classifier = null;
        }
    }

    private static String message(Throwable error) {
        return error == null ? "Error desconocido"
                : error.getMessage() == null ? error.getClass().getSimpleName() : error.getMessage();
    }

    public static final class Report {
        public final boolean ready;
        public final String state;
        public final String summary;
        public final VisionTensorContractCore.Validation yolo;
        public final VisionTensorContractCore.Validation classifier;

        Report(boolean ready, String state, String summary,
               VisionTensorContractCore.Validation yolo,
               VisionTensorContractCore.Validation classifier) {
            this.ready = ready;
            this.state = state;
            this.summary = summary;
            this.yolo = yolo;
            this.classifier = classifier;
        }

        static Report failed(String state, String summary) {
            return new Report(false, state, summary, null, null);
        }
    }
}

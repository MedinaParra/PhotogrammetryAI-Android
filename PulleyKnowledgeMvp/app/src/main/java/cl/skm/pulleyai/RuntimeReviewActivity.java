package cl.skm.pulleyai;

import android.app.Activity;
import android.graphics.Color;
import android.os.Bundle;
import android.os.Debug;
import android.os.SystemClock;
import android.view.Gravity;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;

import java.io.File;
import java.io.FileOutputStream;
import java.nio.charset.StandardCharsets;

/** Runs the real session report, reconstructs a bounded BA window and persists runtime evidence. */
public final class RuntimeReviewActivity extends Activity {
    public static final String EXTRA_SESSION_ID = "runtime_session_id";

    private CaptureStore store;
    private String sessionId;
    private TextView status;
    private Button runButton;

    @Override protected void onCreate(Bundle state) {
        super.onCreate(state);
        sessionId = getIntent().getStringExtra(EXTRA_SESSION_ID);
        store = new CaptureStore(this);
        buildUi();
    }

    @Override protected void onDestroy() {
        store.close();
        super.onDestroy();
    }

    private void buildUi() {
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(22), dp(20), dp(22), dp(24));
        root.setGravity(Gravity.TOP);
        root.setBackgroundColor(Color.rgb(244, 247, 249));

        TextView title = text("VALIDACIÓN RUNTIME", 25, true);
        title.setTextColor(Color.rgb(18, 52, 73));
        root.addView(title);
        TextView subtitle = text(
                "La sesión se evalúa con el safety gate real. La ventana BA se construye desde poses, tracks, puntos y píxeles reales; una métrica faltante mantiene fallback explícito.",
                14, false);
        subtitle.setPadding(0, dp(5), 0, dp(15));
        root.addView(subtitle);

        status = text("Seleccione EJECUTAR VALIDACIÓN.", 14, false);
        status.setPadding(dp(14), dp(13), dp(14), dp(13));
        status.setBackgroundColor(Color.WHITE);
        root.addView(status);

        runButton = new Button(this);
        runButton.setText("EJECUTAR SAFETY GATE, BA Y TELEMETRÍA");
        runButton.setAllCaps(false);
        runButton.setOnClickListener(view -> runValidation());
        root.addView(runButton);

        Button close = new Button(this);
        close.setText("VOLVER");
        close.setAllCaps(false);
        close.setOnClickListener(view -> finish());
        root.addView(close);
        setContentView(root);
    }

    private void runValidation() {
        if (sessionId == null || store.getSession(sessionId) == null) {
            status.setText("BLOCKED · no existe una sesión válida para analizar.");
            status.setTextColor(Color.rgb(150, 30, 30));
            return;
        }
        runButton.setEnabled(false);
        status.setText("Analizando correspondencias y construyendo ventana BA real…");
        final long startedAtEpochMs = System.currentTimeMillis();
        final long startedElapsedMs = SystemClock.elapsedRealtime();
        final long heapBefore = usedHeapBytes();
        final long pssBefore = Debug.getPss();
        final long availableBefore = availableMemoryBytes();
        final long storage = store.sessionDir(sessionId).getUsableSpace();

        new Thread(() -> {
            long peakHeap = heapBefore;
            long peakPss = pssBefore;
            try {
                SessionOverlapAnalyzer.Report report = SessionOverlapAnalyzer.analyze(store, sessionId);
                peakHeap = Math.max(peakHeap, usedHeapBytes());
                peakPss = Math.max(peakPss, Debug.getPss());

                RuntimeBundleWindowCore.Result window = RuntimeBundleWindowBuilder.build(
                        RuntimeReviewActivity.this, store, sessionId, report);
                peakHeap = Math.max(peakHeap, usedHeapBytes());
                peakPss = Math.max(peakPss, Debug.getPss());
                File sessionDir = store.sessionDir(sessionId);
                write(new File(sessionDir, "runtime_ba_window.json"),
                        RuntimeBundleWindowSerializer.canonicalJson(window));

                boolean resources = storage >= 300L * 1024L * 1024L
                        && availableBefore >= 256L * 1024L * 1024L;
                RuntimeReconstructionCoordinator.Outcome outcome =
                        RuntimeReconstructionCoordinator.evaluate(
                                RuntimeReviewActivity.this,
                                sessionId,
                                report,
                                PhotogrammetrySafetyGateAdapter.SupplementalMetrics.unknown(),
                                window.ready ? window.problem : null,
                                resources,
                                false);
                peakHeap = Math.max(peakHeap, usedHeapBytes());
                peakPss = Math.max(peakPss, Debug.getPss());

                long heapAfter = usedHeapBytes();
                long pssAfter = Debug.getPss();
                peakHeap = Math.max(peakHeap, heapAfter);
                peakPss = Math.max(peakPss, pssAfter);
                RuntimeTelemetryCore.Record telemetryRecord = new RuntimeTelemetryCore.Record(
                        startedAtEpochMs,
                        Math.max(0L, SystemClock.elapsedRealtime() - startedElapsedMs),
                        heapBefore, heapAfter, peakHeap,
                        pssBefore, pssAfter, peakPss,
                        availableBefore, availableMemoryBytes(), storage,
                        window.ready, false, window.status,
                        RuntimeBundleWindowSerializer.fingerprint(window),
                        window.cameraCount, window.pointCount, window.observationCount,
                        outcome.gate.state.name(), outcome.decision.state.name(),
                        outcome.decision.reason, outcome.decision.baStatus);
                RuntimeTelemetryCore.Result telemetry = RuntimeTelemetryCore.evaluate(telemetryRecord);
                write(new File(sessionDir, "runtime_telemetry.json"), telemetry.canonicalJson());
                write(new File(sessionDir, "runtime_audit.json"),
                        auditJson(outcome, window, telemetry));

                runOnUiThread(() -> {
                    runButton.setEnabled(true);
                    status.setText(outcome.summary()
                            + "\n\n" + window.summary()
                            + "\n" + telemetry.summary()
                            + "\n\nEvidencia: runtime_ba_window.json, runtime_telemetry.json y runtime_audit.json"
                            + (outcome.gate.state == PhotogrammetrySafetyGateCore.State.REVIEW
                            ? "\nEl BA no se ejecutó porque el safety gate exige completar métricas faltantes."
                            : ""));
                    status.setTextColor(outcome.decision.canPublishOptimizedGeometry()
                            ? Color.rgb(25, 108, 65)
                            : outcome.decision.useUnoptimizedFallback
                            ? Color.rgb(145, 82, 0)
                            : Color.rgb(150, 30, 30));
                });
            } catch (Exception error) {
                final String message = error.getMessage() == null
                        ? error.getClass().getSimpleName() : error.getMessage();
                runOnUiThread(() -> {
                    runButton.setEnabled(true);
                    status.setText("BLOCKED · " + message);
                    status.setTextColor(Color.rgb(150, 30, 30));
                });
            }
        }, "RuntimeReview").start();
    }

    private static String auditJson(RuntimeReconstructionCoordinator.Outcome outcome,
                                    RuntimeBundleWindowCore.Result window,
                                    RuntimeTelemetryCore.Result telemetry) {
        return "{\n\"schema\":\"skm-runtime-audit/1\""
                + ",\n\"auditId\":" + outcome.auditId
                + ",\n\"gateState\":\"" + outcome.gate.state + "\""
                + ",\n\"gateQuality\":" + outcome.gate.qualityScore
                + ",\n\"decisionState\":\"" + outcome.decision.state + "\""
                + ",\n\"decisionReason\":\"" + escape(outcome.decision.reason) + "\""
                + ",\n\"baStatus\":\"" + escape(outcome.decision.baStatus) + "\""
                + ",\n\"optimized\":" + outcome.decision.useOptimizedGeometry
                + ",\n\"fallback\":" + outcome.decision.useUnoptimizedFallback
                + ",\n\"windowStatus\":\"" + escape(window.status) + "\""
                + ",\n\"windowSha256\":\"" + RuntimeBundleWindowSerializer.fingerprint(window) + "\""
                + ",\n\"telemetryState\":\"" + telemetry.state + "\""
                + ",\n\"telemetryReason\":\"" + escape(telemetry.reason) + "\"\n}";
    }

    private static void write(File target, String content) throws Exception {
        try (FileOutputStream output = new FileOutputStream(target)) {
            output.write(content.getBytes(StandardCharsets.UTF_8));
        }
    }

    private static long usedHeapBytes() {
        Runtime runtime = Runtime.getRuntime();
        return Math.max(0L, runtime.totalMemory() - runtime.freeMemory());
    }

    private static long availableMemoryBytes() {
        Runtime runtime = Runtime.getRuntime();
        return Math.max(0L, runtime.maxMemory() - usedHeapBytes());
    }

    private static String escape(String value) {
        return value == null ? "" : value.replace("\\", "\\\\").replace("\"", "\\\"")
                .replace("\n", "\\n").replace("\r", "\\r");
    }

    private TextView text(String value, int sp, boolean bold) {
        TextView view = new TextView(this);
        view.setText(value);
        view.setTextSize(sp);
        view.setTextColor(Color.rgb(35, 39, 42));
        if (bold) view.setTypeface(android.graphics.Typeface.DEFAULT_BOLD);
        return view;
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }
}

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

/** Runs real safety metrics and bounded BA with shared preparation, timeout and safe cancellation. */
public final class RuntimeReviewActivity extends Activity {
    public static final String EXTRA_SESSION_ID = "runtime_session_id";
    private static final long RUNTIME_BUDGET_MS = 180_000L;

    private CaptureStore store;
    private String sessionId;
    private TextView status;
    private Button runButton;
    private Button cancelButton;
    private volatile RuntimeExecutionControlCore.Token activeControl;

    @Override protected void onCreate(Bundle state) {
        super.onCreate(state);
        sessionId = getIntent().getStringExtra(EXTRA_SESSION_ID);
        store = new CaptureStore(this);
        buildUi();
    }

    @Override protected void onDestroy() {
        RuntimeExecutionControlCore.Token control = activeControl;
        if (control != null) control.cancel("ACTIVITY_DESTROYED");
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
                "Frames, features e intrínsecos se preparan una vez. El proceso tiene 180 s de presupuesto; cancelar o excederlo conserva fallback sin optimizar.",
                14, false);
        subtitle.setPadding(0, dp(5), 0, dp(15));
        root.addView(subtitle);

        status = text("Seleccione EJECUTAR VALIDACIÓN.", 14, false);
        status.setPadding(dp(14), dp(13), dp(14), dp(13));
        status.setBackgroundColor(Color.WHITE);
        root.addView(status);

        runButton = new Button(this);
        runButton.setText("EJECUTAR VALIDACIÓN ACOTADA");
        runButton.setAllCaps(false);
        runButton.setOnClickListener(view -> runValidation());
        root.addView(runButton);

        cancelButton = new Button(this);
        cancelButton.setText("CANCELAR Y CONSERVAR FALLBACK");
        cancelButton.setAllCaps(false);
        cancelButton.setEnabled(false);
        cancelButton.setOnClickListener(view -> requestCancellation());
        root.addView(cancelButton);

        Button close = new Button(this);
        close.setText("VOLVER");
        close.setAllCaps(false);
        close.setOnClickListener(view -> finish());
        root.addView(close);
        setContentView(root);
    }

    private void requestCancellation() {
        RuntimeExecutionControlCore.Token control = activeControl;
        if (control == null) return;
        control.cancel("USER_CANCELLED");
        cancelButton.setEnabled(false);
        status.setText("Cancelación solicitada. Se detendrá en el siguiente checkpoint y se conservará geometría sin optimizar.");
        status.setTextColor(Color.rgb(145, 82, 0));
    }

    private void runValidation() {
        if (sessionId == null || store.getSession(sessionId) == null) {
            status.setText("BLOCKED · no existe una sesión válida para analizar.");
            status.setTextColor(Color.rgb(150, 30, 30));
            return;
        }
        final RuntimeExecutionControlCore.Token control =
                RuntimeExecutionControlCore.start(RUNTIME_BUDGET_MS);
        activeControl = control;
        runButton.setEnabled(false);
        cancelButton.setEnabled(true);
        status.setText("Preparando análisis acotado y diagnóstico automático…");

        final long startedAtEpochMs = System.currentTimeMillis();
        final long startedElapsedMs = SystemClock.elapsedRealtime();
        final long heapBefore = usedHeapBytes();
        final long pssBefore = Debug.getPss();
        final long availableBefore = availableMemoryBytes();
        final long storage = store.sessionDir(sessionId).getUsableSpace();

        new Thread(() -> execute(control, startedAtEpochMs, startedElapsedMs,
                heapBefore, pssBefore, availableBefore, storage), "RuntimeReview").start();
    }

    private void execute(RuntimeExecutionControlCore.Token control,
                         long startedAtEpochMs, long startedElapsedMs,
                         long heapBefore, long pssBefore,
                         long availableBefore, long storage) {
        long peakHeap = heapBefore;
        long peakPss = pssBefore;
        SessionOverlapAnalyzer.Report report = null;
        RuntimeSupplementalMetricsCore.Result supplemental = null;
        RuntimeBundleWindowCore.Result window = RuntimeBundleWindowCore.Result.failed("NOT_BUILT");
        RuntimeFramePreparationCache.Result cache = null;
        File sessionDir = store.sessionDir(sessionId);
        try {
            control.checkpoint("SESSION_ANALYSIS_START");
            report = SessionOverlapAnalyzer.analyze(store, sessionId);
            control.checkpoint("SESSION_ANALYSIS_COMPLETE");
            peakHeap = Math.max(peakHeap, usedHeapBytes());
            peakPss = Math.max(peakPss, Debug.getPss());

            cache = RuntimeFramePreparationCache.prepare(
                    RuntimeReviewActivity.this, store, sessionId, report, control);
            if (!cache.ready) throw new IllegalStateException(cache.status);
            write(new File(sessionDir, "runtime_frame_cache.json"), cache.canonicalJson());
            peakHeap = Math.max(peakHeap, usedHeapBytes());
            peakPss = Math.max(peakPss, Debug.getPss());

            supplemental = RuntimeSupplementalMetricsBuilder.build(cache, report, control);
            RuntimeSupplementalMetricsBuilder.persist(store, sessionId, supplemental);
            window = RuntimeBundleWindowBuilder.build(cache, report, control);
            RuntimeBundleWindowBuilder.persist(store, sessionId, window);
            peakHeap = Math.max(peakHeap, usedHeapBytes());
            peakPss = Math.max(peakPss, Debug.getPss());

            DeviceDiagnosticsCore.Result diagnostics = AndroidDeviceDiagnosticsCollector.collect(this);
            write(new File(sessionDir, "runtime_device_diagnostics.json"), diagnostics.canonicalJson());
            boolean resources = storage >= 300L * 1024L * 1024L
                    && availableBefore >= 256L * 1024L * 1024L;
            control.checkpoint("BEFORE_BUNDLE_ADJUSTMENT");

            PhotogrammetrySupplementalMetricsCore.Result metrics = supplemental.supplemental;
            PhotogrammetrySafetyGateAdapter.SupplementalMetrics gateMetrics =
                    new PhotogrammetrySafetyGateAdapter.SupplementalMetrics(
                            metrics.homographyDominanceRatio, metrics.blurryFrameFraction,
                            metrics.reflectiveFrameFraction, metrics.repetitiveAmbiguityFraction);
            RuntimeReconstructionCoordinator.Outcome outcome =
                    RuntimeReconstructionCoordinator.evaluate(
                            this, sessionId, report, gateMetrics,
                            window.ready ? window.problem : null, resources, false);

            long heapAfter = usedHeapBytes();
            long pssAfter = Debug.getPss();
            peakHeap = Math.max(peakHeap, heapAfter);
            peakPss = Math.max(peakPss, pssAfter);
            RuntimeTelemetryCore.Result telemetry = telemetry(startedAtEpochMs, startedElapsedMs,
                    heapBefore, heapAfter, peakHeap, pssBefore, pssAfter, peakPss,
                    availableBefore, storage, window, false, outcome);
            write(new File(sessionDir, "runtime_telemetry.json"), telemetry.canonicalJson());
            write(new File(sessionDir, "runtime_audit.json"),
                    auditJson(outcome, window, cache, supplemental, diagnostics, telemetry, control));

            final String summary = outcome.summary() + "\n\n" + cache.summary()
                    + "\n" + supplemental.summary() + "\n" + window.summary()
                    + "\n" + diagnostics.summary() + "\n" + telemetry.summary()
                    + "\n\nEvidencia runtime guardada en la sesión.";
            runOnUiThread(() -> showCompleted(summary, outcome.decision.canPublishOptimizedGeometry(),
                    outcome.decision.useUnoptimizedFallback));
        } catch (RuntimeExecutionControlCore.AbortedException aborted) {
            handleAbort(aborted, control, report, supplemental, window, cache,
                    startedAtEpochMs, startedElapsedMs, heapBefore, pssBefore,
                    peakHeap, peakPss, availableBefore, storage, sessionDir);
        } catch (Exception error) {
            final String message = error.getMessage() == null
                    ? error.getClass().getSimpleName() : error.getMessage();
            runOnUiThread(() -> showFailure("BLOCKED · " + message));
        } finally {
            activeControl = null;
        }
    }

    private void handleAbort(RuntimeExecutionControlCore.AbortedException aborted,
                             RuntimeExecutionControlCore.Token control,
                             SessionOverlapAnalyzer.Report report,
                             RuntimeSupplementalMetricsCore.Result supplemental,
                             RuntimeBundleWindowCore.Result window,
                             RuntimeFramePreparationCache.Result cache,
                             long startedAtEpochMs, long startedElapsedMs,
                             long heapBefore, long pssBefore, long peakHeap, long peakPss,
                             long availableBefore, long storage, File sessionDir) {
        try {
            DeviceDiagnosticsCore.Result diagnostics = AndroidDeviceDiagnosticsCollector.collect(this);
            write(new File(sessionDir, "runtime_device_diagnostics.json"), diagnostics.canonicalJson());
            write(new File(sessionDir, "runtime_abort.json"),
                    "{\"schema\":\"skm-runtime-abort/1\",\"state\":\"" + aborted.state
                            + "\",\"reason\":\"" + escape(aborted.reason)
                            + "\",\"stage\":\"" + escape(aborted.stage)
                            + "\",\"fallbackUnoptimized\":true,\"optimizedGeometryAccepted\":false}");
            RuntimeReconstructionCoordinator.Outcome fallback = null;
            if (report != null) {
                PhotogrammetrySafetyGateAdapter.SupplementalMetrics metrics =
                        supplemental == null
                                ? PhotogrammetrySafetyGateAdapter.SupplementalMetrics.unknown()
                                : supplementalMetrics(supplemental.supplemental);
                fallback = RuntimeReconstructionCoordinator.evaluate(
                        this, sessionId, report, metrics, null, false, true);
            }
            long heapAfter = usedHeapBytes(), pssAfter = Debug.getPss();
            RuntimeTelemetryCore.Result telemetry = telemetry(startedAtEpochMs, startedElapsedMs,
                    heapBefore, heapAfter, Math.max(peakHeap, heapAfter),
                    pssBefore, pssAfter, Math.max(peakPss, pssAfter), availableBefore,
                    storage, window, true, fallback);
            write(new File(sessionDir, "runtime_telemetry.json"), telemetry.canonicalJson());
        } catch (Exception ignored) {
            // The UI still reports fail-closed even if secondary evidence persistence fails.
        }
        final String message = "REVIEW · " + aborted.reason + " @ " + aborted.stage
                + "\nFALLBACK SIN OPTIMIZAR · no se acepta geometría BA.";
        runOnUiThread(() -> showCompleted(message, false, true));
    }

    private static PhotogrammetrySafetyGateAdapter.SupplementalMetrics supplementalMetrics(
            PhotogrammetrySupplementalMetricsCore.Result values) {
        if (values == null) return PhotogrammetrySafetyGateAdapter.SupplementalMetrics.unknown();
        return new PhotogrammetrySafetyGateAdapter.SupplementalMetrics(
                values.homographyDominanceRatio,
                values.blurryFrameFraction,
                values.reflectiveFrameFraction,
                values.repetitiveAmbiguityFraction);
    }

    private RuntimeTelemetryCore.Result telemetry(long startedAtEpochMs, long startedElapsedMs,
                                                   long heapBefore, long heapAfter, long peakHeap,
                                                   long pssBefore, long pssAfter, long peakPss,
                                                   long availableBefore, long storage,
                                                   RuntimeBundleWindowCore.Result window,
                                                   boolean interrupted,
                                                   RuntimeReconstructionCoordinator.Outcome outcome) {
        RuntimeTelemetryCore.Record record = new RuntimeTelemetryCore.Record(
                startedAtEpochMs, Math.max(0L, SystemClock.elapsedRealtime() - startedElapsedMs),
                heapBefore, heapAfter, peakHeap, pssBefore, pssAfter, peakPss,
                availableBefore, availableMemoryBytes(), storage,
                window != null && window.ready, interrupted,
                window == null ? "NOT_BUILT" : window.status,
                window == null ? "" : RuntimeBundleWindowSerializer.fingerprint(window),
                window == null ? 0 : window.cameraCount,
                window == null ? 0 : window.pointCount,
                window == null ? 0 : window.observationCount,
                outcome == null ? "NOT_EVALUATED" : outcome.gate.state.name(),
                outcome == null ? "REVIEW" : outcome.decision.state.name(),
                outcome == null ? "OPTIMIZATION_INTERRUPTED" : outcome.decision.reason,
                outcome == null ? "NOT_RUN" : outcome.decision.baStatus);
        return RuntimeTelemetryCore.evaluate(record);
    }

    private static String auditJson(RuntimeReconstructionCoordinator.Outcome outcome,
                                    RuntimeBundleWindowCore.Result window,
                                    RuntimeFramePreparationCache.Result cache,
                                    RuntimeSupplementalMetricsCore.Result supplemental,
                                    DeviceDiagnosticsCore.Result diagnostics,
                                    RuntimeTelemetryCore.Result telemetry,
                                    RuntimeExecutionControlCore.Token control) {
        return "{\n\"schema\":\"skm-runtime-audit/3\""
                + ",\n\"auditId\":" + outcome.auditId
                + ",\n\"cacheStatus\":\"" + escape(cache.status) + "\""
                + ",\n\"decodePasses\":" + cache.decodePasses
                + ",\n\"supplementalStatus\":\"" + escape(supplemental.status) + "\""
                + ",\n\"gateState\":\"" + outcome.gate.state + "\""
                + ",\n\"decisionState\":\"" + outcome.decision.state + "\""
                + ",\n\"decisionReason\":\"" + escape(outcome.decision.reason) + "\""
                + ",\n\"optimized\":" + outcome.decision.useOptimizedGeometry
                + ",\n\"fallback\":" + outcome.decision.useUnoptimizedFallback
                + ",\n\"windowStatus\":\"" + escape(window.status) + "\""
                + ",\n\"diagnosticsState\":\"" + diagnostics.state + "\""
                + ",\n\"telemetryState\":\"" + telemetry.state + "\""
                + ",\n\"controlState\":\"" + control.state() + "\"\n}";
    }

    private void showCompleted(String message, boolean optimized, boolean fallback) {
        runButton.setEnabled(true); cancelButton.setEnabled(false);
        status.setText(message);
        status.setTextColor(optimized ? Color.rgb(25, 108, 65)
                : fallback ? Color.rgb(145, 82, 0) : Color.rgb(150, 30, 30));
    }
    private void showFailure(String message) {
        runButton.setEnabled(true); cancelButton.setEnabled(false);
        status.setText(message); status.setTextColor(Color.rgb(150, 30, 30));
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
        view.setText(value); view.setTextSize(sp); view.setTextColor(Color.rgb(35, 39, 42));
        if (bold) view.setTypeface(android.graphics.Typeface.DEFAULT_BOLD);
        return view;
    }
    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }
}
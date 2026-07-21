package cl.skm.pulleyai;

import android.app.Activity;
import android.graphics.Color;
import android.os.Bundle;
import android.view.Gravity;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;

/** Runs the real session report through the fail-closed runtime decision layer. */
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
                "La sesión se evalúa con el safety gate real. Sin una ventana BA serializada se conserva fallback y nunca se declara optimización.",
                14, false);
        subtitle.setPadding(0, dp(5), 0, dp(15));
        root.addView(subtitle);

        status = text("Seleccione EJECUTAR VALIDACIÓN.", 14, false);
        status.setPadding(dp(14), dp(13), dp(14), dp(13));
        status.setBackgroundColor(Color.WHITE);
        root.addView(status);

        runButton = new Button(this);
        runButton.setText("EJECUTAR SAFETY GATE Y DECISIÓN");
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
        status.setText("Analizando correspondencias, safety gate y fallback…");
        new Thread(() -> {
            try {
                SessionOverlapAnalyzer.Report report = SessionOverlapAnalyzer.analyze(store, sessionId);
                boolean resources = getFilesDir().getUsableSpace() >= 300L * 1024L * 1024L
                        && Runtime.getRuntime().maxMemory() >= 256L * 1024L * 1024L;
                RuntimeReconstructionCoordinator.Outcome outcome =
                        RuntimeReconstructionCoordinator.evaluate(
                                RuntimeReviewActivity.this,
                                sessionId,
                                report,
                                PhotogrammetrySafetyGateAdapter.SupplementalMetrics.unknown(),
                                null,
                                resources,
                                false);
                runOnUiThread(() -> {
                    runButton.setEnabled(true);
                    status.setText(outcome.summary()
                            + "\n\nBA runtime: ventana de observaciones aún no serializada; se exige fallback explícito."
                            + "\nRecursos: " + (resources ? "aptos" : "insuficientes"));
                    status.setTextColor(outcome.decision.canPublishOptimizedGeometry()
                            ? Color.rgb(25, 108, 65)
                            : outcome.decision.useUnoptimizedFallback
                            ? Color.rgb(145, 82, 0)
                            : Color.rgb(150, 30, 30));
                });
            } catch (Exception error) {
                runOnUiThread(() -> {
                    runButton.setEnabled(true);
                    status.setText("BLOCKED · " + (error.getMessage() == null ? "fallo de análisis" : error.getMessage()));
                    status.setTextColor(Color.rgb(150, 30, 30));
                });
            }
        }, "RuntimeReview").start();
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

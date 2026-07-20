package cl.skm.pulleyai;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.DialogInterface;
import android.content.Intent;
import android.graphics.Color;
import android.os.Bundle;
import android.text.InputType;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import java.util.List;
import java.util.Locale;

/** Single product entry point for capture, reconstruction, CAD assembly and knowledge. */
public final class LauncherActivity extends Activity {
    private CaptureStore captureStore;
    private TextView stateView;
    private LinearLayout recentContainer;

    @Override protected void onCreate(Bundle state) {
        super.onCreate(state);
        captureStore = new CaptureStore(this);
        MainActivity.DbHelper knowledge = new MainActivity.DbHelper(this);
        KnowledgeArchivePatch.apply(knowledge);
        knowledge.close();
        buildUi();
    }

    @Override protected void onResume() { super.onResume(); refresh(); }
    @Override protected void onDestroy() { captureStore.close(); super.onDestroy(); }

    private void buildUi() {
        ScrollView scroll = new ScrollView(this);
        LinearLayout root = vertical();
        root.setPadding(dp(18), dp(22), dp(18), dp(30));
        root.setBackgroundColor(Color.rgb(244, 247, 249));
        scroll.addView(root);

        TextView title = text("SKM Polea AI", 28, true);
        title.setTextColor(Color.rgb(18, 52, 73));
        root.addView(title);
        TextView subtitle = text("Captura horizontal, fotogrametría, ensamblaje CAD/STEP y validación trazable.", 15, false);
        subtitle.setPadding(0, dp(4), 0, dp(14));
        root.addView(subtitle);

        stateView = text("Preparando bases locales…", 13, true);
        stateView.setPadding(dp(12), dp(10), dp(12), dp(10));
        stateView.setBackgroundColor(Color.WHITE);
        root.addView(stateView);

        Button create = button("NUEVA SESIÓN DE CAPTURA");
        create.setOnClickListener(view -> showCreateDialog());
        root.addView(create);

        Button resume = button("CONTINUAR ÚLTIMA CAPTURA");
        resume.setOnClickListener(view -> {
            CaptureStore.Session session = captureStore.latestOpen();
            if (session == null) Toast.makeText(this, "No hay una sesión abierta.", Toast.LENGTH_LONG).show();
            else openCapture(session.id);
        });
        root.addView(resume);

        Button cad = button("ENSAMBLAJE CAD / IMPORTAR STEP");
        cad.setOnClickListener(view -> {
            CaptureStore.Session latest = captureStore.latestOpen();
            if (latest == null) {
                List<CaptureStore.Session> recent = captureStore.recent(1);
                openCad(recent.isEmpty() ? "standalone" : recent.get(0).id);
            } else openCad(latest.id);
        });
        root.addView(cad);

        Button knowledge = button("CONOCIMIENTO Y VALIDACIÓN DE COTAS");
        knowledge.setOnClickListener(view -> startActivity(new Intent(this, MainActivity.class)));
        root.addView(knowledge);

        TextView warning = text(
                "La superposición CAD usa transformaciones rígidas. Ningún STEP se escala para forzar coincidencia; si el kernel nativo no está enlazado, el archivo queda registrado pero no se dibuja.",
                12, false);
        warning.setTextColor(Color.DKGRAY);
        warning.setPadding(0, dp(15), 0, 0);
        root.addView(warning);

        TextView recentTitle = text("SESIONES RECIENTES", 13, true);
        recentTitle.setTextColor(Color.rgb(35, 84, 117));
        recentTitle.setPadding(0, dp(20), 0, dp(5));
        root.addView(recentTitle);
        recentContainer = vertical();
        root.addView(recentContainer);
        setContentView(scroll);
    }

    private void showCreateDialog() {
        LinearLayout form = vertical();
        form.setPadding(dp(18), 0, dp(18), 0);
        final EditText label = input("Nombre de la sesión", InputType.TYPE_CLASS_TEXT);
        final EditText code = input("Código material / SAP / SC (opcional)", InputType.TYPE_CLASS_TEXT);
        final EditText ot = input("OT (opcional)", InputType.TYPE_CLASS_TEXT);
        final EditText length = input("Largo real del manto en mm (obligatorio)",
                InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_FLAG_DECIMAL);
        form.addView(label); form.addView(code); form.addView(ot); form.addView(length);

        final AlertDialog dialog = new AlertDialog.Builder(this)
                .setTitle("Nueva sesión")
                .setMessage("Use el teléfono horizontal. El largo real del manto es obligatorio para resolver escala métrica.")
                .setView(form)
                .setPositiveButton("CREAR Y ABRIR", null)
                .setNegativeButton("CANCELAR", null)
                .create();
        dialog.setOnShowListener(new DialogInterface.OnShowListener() {
            @Override public void onShow(DialogInterface ignored) {
                dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(view -> {
                    Double shellLength = parsePositive(length.getText().toString());
                    if (shellLength == null) {
                        length.setError("Ingrese un largo válido mayor que cero");
                        length.requestFocus();
                        return;
                    }
                    String id = captureStore.createSession(label.getText().toString(),
                            code.getText().toString(), ot.getText().toString(), shellLength);
                    dialog.dismiss();
                    openCapture(id);
                });
            }
        });
        dialog.show();
    }

    private void openCapture(String id) {
        Intent intent = new Intent(this, CaptureActivity.class);
        intent.putExtra(CaptureActivity.EXTRA_SESSION_ID, id);
        startActivity(intent);
    }

    private void openCad(String id) {
        Intent intent = new Intent(this, CadAssemblyActivity.class);
        intent.putExtra(CadAssemblyActivity.EXTRA_SESSION_ID, id);
        startActivity(intent);
    }

    private void refresh() {
        CaptureStore.Session open = captureStore.latestOpen();
        FreeCadNativeBridge.Status core = FreeCadNativeBridge.status();
        stateView.setText((open == null
                ? "Bases locales listas · no hay captura abierta"
                : "Captura abierta: " + open.label + " · " + open.accepted + " fotos aceptadas")
                + "\nCAD: " + core.runtimeInfo
                + (core.supportsStep() ? " · STEP nativo disponible" : " · STEP pendiente de kernel OpenCascade"));
        stateView.setTextColor(open == null ? Color.rgb(35, 84, 117) : Color.rgb(145, 82, 0));

        recentContainer.removeAllViews();
        List<CaptureStore.Session> recent = captureStore.recent(8);
        if (recent.isEmpty()) {
            recentContainer.addView(text("Aún no existen sesiones de captura.", 13, false));
            return;
        }
        for (final CaptureStore.Session session : recent) {
            LinearLayout card = vertical();
            card.setPadding(dp(12), dp(10), dp(12), dp(10));
            LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(-1, -2);
            params.setMargins(0, dp(6), 0, 0);
            card.setLayoutParams(params);
            card.setBackgroundColor(Color.WHITE);
            card.addView(text(session.label, 16, true));
            String details = "Estado: " + session.status
                    + "\nAceptadas: " + session.accepted + " · rechazadas: " + session.rejected
                    + "\nCobertura eje: " + CoveragePlanner.coveredCount(session.lowMask) + "/12"
                    + " · alta: " + CoveragePlanner.coveredCount(session.highMask) + "/12";
            if (session.shellLengthMm != null) {
                details += String.format(Locale.ROOT, "\nLargo de referencia: %.1f mm", session.shellLengthMm);
            }
            card.addView(text(details, 13, false));
            LinearLayout actions = new LinearLayout(this);
            actions.setOrientation(LinearLayout.HORIZONTAL);
            Button openButton = button("ABRIR CAPTURA");
            openButton.setOnClickListener(view -> openCapture(session.id));
            actions.addView(openButton, new LinearLayout.LayoutParams(0, -2, 1f));
            Button cadButton = button("ENSAMBLAJE CAD");
            cadButton.setOnClickListener(view -> openCad(session.id));
            actions.addView(cadButton, new LinearLayout.LayoutParams(0, -2, 1f));
            card.addView(actions);
            recentContainer.addView(card);
        }
    }

    private LinearLayout vertical() { LinearLayout layout = new LinearLayout(this); layout.setOrientation(LinearLayout.VERTICAL); return layout; }
    private TextView text(String value, int sp, boolean bold) { TextView view = new TextView(this); view.setText(value); view.setTextSize(sp); view.setTextColor(Color.rgb(35, 39, 42)); if (bold) view.setTypeface(android.graphics.Typeface.DEFAULT_BOLD); return view; }
    private Button button(String label) { Button button = new Button(this); button.setText(label); button.setAllCaps(false); LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(-1, -2); params.setMargins(0, dp(8), 0, 0); button.setLayoutParams(params); return button; }
    private EditText input(String hint, int type) { EditText edit = new EditText(this); edit.setHint(hint); edit.setInputType(type); edit.setSingleLine(true); return edit; }
    private int dp(int value) { return Math.round(value * getResources().getDisplayMetrics().density); }
    private static Double parsePositive(String raw) { if (raw == null || raw.trim().isEmpty()) return null; try { double value = Double.parseDouble(raw.trim().replace(',', '.')); return value > 0.0 && !Double.isNaN(value) && !Double.isInfinite(value) ? value : null; } catch (NumberFormatException ignored) { return null; } }
}

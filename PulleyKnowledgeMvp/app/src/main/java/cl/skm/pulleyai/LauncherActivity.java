package cl.skm.pulleyai;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.DialogInterface;
import android.content.Intent;
import android.graphics.Color;
import android.net.Uri;
import android.os.Bundle;
import android.text.InputType;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import java.io.File;
import java.io.FileInputStream;
import java.util.List;
import java.util.Locale;

/** Single product entry point for capture, ZIP reprocessing, CAD assembly and native STEP. */
public final class LauncherActivity extends Activity {
    private static final int EXPORT_DOCUMENT = 4310;

    private CaptureStore captureStore;
    private RevisionedKnowledgeOpenHelper revisionedKnowledge;
    private TextView stateView;
    private LinearLayout recentContainer;
    private File pendingExport;
    private boolean exportInProgress;

    @Override protected void onCreate(Bundle state) {
        super.onCreate(state);
        RuntimePublicationJournalBridge.install(new RuntimePublicationJournalStore(this));
        captureStore = new CaptureStore(this);
        MainActivity.DbHelper knowledge = new MainActivity.DbHelper(this);
        KnowledgeArchivePatch.apply(knowledge);
        knowledge.close();
        revisionedKnowledge = new RevisionedKnowledgeOpenHelper(this);
        revisionedKnowledge.ensureSeeded();
        buildUi();
    }

    @Override protected void onResume() { super.onResume(); refresh(); }

    @Override protected void onDestroy() {
        captureStore.close();
        if (revisionedKnowledge != null) revisionedKnowledge.close();
        super.onDestroy();
    }

    private void buildUi() {
        ScrollView scroll = new ScrollView(this);
        LinearLayout root = vertical();
        root.setPadding(dp(18), dp(22), dp(18), dp(30));
        root.setBackgroundColor(Color.rgb(244, 247, 249));
        scroll.addView(root);

        TextView title = text("SKM Polea AI Lab", 28, true);
        title.setTextColor(Color.rgb(18, 52, 73));
        root.addView(title);
        TextView subtitle = text(
                "Captura horizontal, reprocesamiento de ZIP, fotogrametría, ensamblaje CAD, STEP nativo y validación trazable.",
                15, false);
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
            if (session == null) {
                Toast.makeText(this, "No hay una sesión abierta.", Toast.LENGTH_LONG).show();
            } else {
                openCapture(session.id);
            }
        });
        root.addView(resume);

        Button reprocess = button("IMPORTAR Y REPROCESAR ZIP DE CAPTURA");
        reprocess.setOnClickListener(view ->
                startActivity(new Intent(this, ZipReprocessActivity.class)));
        root.addView(reprocess);

        Button export = button("EXPORTAR ÚLTIMA SESIÓN (ZIP)");
        export.setOnClickListener(view -> exportSession(latestSessionId()));
        root.addView(export);

        Button runtime = button("VALIDACIÓN RUNTIME / SAFETY GATE");
        runtime.setOnClickListener(view -> openRuntimeReview(latestSessionId()));
        root.addView(runtime);

        Button campaign = button("CAMPAÑA FÍSICA DE DISPOSITIVO");
        campaign.setOnClickListener(view ->
                startActivity(new Intent(this, DeviceCampaignActivity.class)));
        root.addView(campaign);

        Button cad = button("ENSAMBLAJE CAD / IMPORTAR STEP");
        cad.setOnClickListener(view -> openCad(latestSessionId()));
        root.addView(cad);

        Button kernel = button("PROCESAR STEP CON KERNEL NATIVO");
        kernel.setOnClickListener(view -> openStepKernel(latestSessionId()));
        root.addView(kernel);

        Button knowledge = button("CONOCIMIENTO Y VALIDACIÓN DE COTAS");
        knowledge.setOnClickListener(view ->
                startActivity(new Intent(this, MainActivity.class)));
        root.addView(knowledge);

        TextView warning = text(
                "Alpha46 Lab se instala como paquete separado para evitar el conflicto de firma de alpha44/45. El reprocesamiento de ZIP genera evidencia diagnóstica y no publica geometría industrial ni calibración metrológica.",
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
        form.addView(label);
        form.addView(code);
        form.addView(ot);
        form.addView(length);

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

    private String latestSessionId() {
        CaptureStore.Session latest = captureStore.latestOpen();
        if (latest != null) return latest.id;
        List<CaptureStore.Session> recent = captureStore.recent(1);
        return recent.isEmpty() ? "standalone" : recent.get(0).id;
    }

    private void openCapture(String id) {
        Intent intent = new Intent(this, CaptureActivity.class);
        intent.putExtra(CaptureActivity.EXTRA_SESSION_ID, id);
        startActivity(intent);
    }

    private void openRuntimeReview(String id) {
        Intent intent = new Intent(this, RuntimeReviewActivity.class);
        intent.putExtra(RuntimeReviewActivity.EXTRA_SESSION_ID, id);
        startActivity(intent);
    }

    private void openCad(String id) {
        Intent intent = new Intent(this, CadAssemblyActivity.class);
        intent.putExtra(CadAssemblyActivity.EXTRA_SESSION_ID, id);
        startActivity(intent);
    }

    private void openStepKernel(String id) {
        Intent intent = new Intent(this, StepKernelActivity.class);
        intent.putExtra(StepKernelActivity.EXTRA_SESSION_ID, id);
        startActivity(intent);
    }

    private void exportSession(String sessionId) {
        if (exportInProgress) {
            Toast.makeText(this, "Ya se está construyendo un paquete.", Toast.LENGTH_LONG).show();
            return;
        }
        if (sessionId == null || "standalone".equals(sessionId)
                || captureStore.getSession(sessionId) == null) {
            Toast.makeText(this, "No existe una sesión para exportar.", Toast.LENGTH_LONG).show();
            return;
        }
        exportInProgress = true;
        Toast.makeText(this, "Construyendo ZIP auditable…", Toast.LENGTH_LONG).show();
        new Thread(() -> {
            try {
                final File packageFile = SessionPackageExporter.build(
                        LauncherActivity.this, captureStore, sessionId);
                runOnUiThread(() -> {
                    exportInProgress = false;
                    pendingExport = packageFile;
                    Intent intent = new Intent(Intent.ACTION_CREATE_DOCUMENT);
                    intent.addCategory(Intent.CATEGORY_OPENABLE);
                    intent.setType("application/zip");
                    intent.putExtra(Intent.EXTRA_TITLE, packageFile.getName());
                    startActivityForResult(intent, EXPORT_DOCUMENT);
                });
            } catch (Exception e) {
                runOnUiThread(() -> {
                    exportInProgress = false;
                    String message = e.getMessage() == null
                            ? "No se pudo construir el ZIP" : e.getMessage();
                    Toast.makeText(LauncherActivity.this, message, Toast.LENGTH_LONG).show();
                });
            }
        }, "PoleaLauncherSessionExport").start();
    }

    @Override protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode != EXPORT_DOCUMENT || resultCode != RESULT_OK || data == null
                || data.getData() == null || pendingExport == null) return;
        Uri target = data.getData();
        try (FileInputStream input = new FileInputStream(pendingExport);
             java.io.OutputStream output = getContentResolver().openOutputStream(target, "w")) {
            if (output == null) throw new IllegalStateException("No se pudo abrir el destino");
            byte[] buffer = new byte[64 * 1024];
            int read;
            while ((read = input.read(buffer)) >= 0) output.write(buffer, 0, read);
            Toast.makeText(this, "ZIP exportado correctamente", Toast.LENGTH_LONG).show();
        } catch (Exception e) {
            String message = e.getMessage() == null
                    ? "No se pudo guardar el ZIP" : e.getMessage();
            Toast.makeText(this, message, Toast.LENGTH_LONG).show();
        } finally {
            pendingExport = null;
        }
    }

    private void refresh() {
        CaptureStore.Session open = captureStore.latestOpen();
        CadCoreStepImporter.Status core = CadCoreStepImporter.status(this);
        stateView.setText((open == null
                ? "Bases locales listas · evidencia revisionada "
                + RevisionedKnowledgeOpenHelper.schemaFingerprint()
                : "Captura abierta: " + open.label + " · " + open.accepted + " fotos aceptadas")
                + "\nCAD: " + core.runtime
                + (core.stepReady ? " · STEP NATIVO LISTO" : " · STEP pendiente")
                + "\nRuntime: safety gate, journal recuperable, campaña física y reprocesamiento ZIP disponibles"
                + (core.diagnostic == null || core.diagnostic.isEmpty()
                ? "" : "\n" + core.diagnostic));
        stateView.setTextColor(core.stepReady ? Color.rgb(25, 108, 65)
                : open == null ? Color.rgb(35, 84, 117) : Color.rgb(145, 82, 0));

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
                details += String.format(Locale.ROOT,
                        "\nLargo de referencia: %.1f mm", session.shellLengthMm);
            }
            card.addView(text(details, 13, false));
            LinearLayout actions = new LinearLayout(this);
            actions.setOrientation(LinearLayout.HORIZONTAL);
            Button openButton = button("CAPTURA");
            openButton.setOnClickListener(view -> openCapture(session.id));
            actions.addView(openButton, new LinearLayout.LayoutParams(0, -2, 1f));
            Button runtimeButton = button("VALIDAR");
            runtimeButton.setOnClickListener(view -> openRuntimeReview(session.id));
            actions.addView(runtimeButton, new LinearLayout.LayoutParams(0, -2, 1f));
            Button cadButton = button("CAD");
            cadButton.setOnClickListener(view -> openCad(session.id));
            actions.addView(cadButton, new LinearLayout.LayoutParams(0, -2, 1f));
            Button stepButton = button("STEP");
            stepButton.setOnClickListener(view -> openStepKernel(session.id));
            actions.addView(stepButton, new LinearLayout.LayoutParams(0, -2, 1f));
            card.addView(actions);
            Button exportButton = button("EXPORTAR ESTA SESIÓN (ZIP)");
            exportButton.setOnClickListener(view -> exportSession(session.id));
            card.addView(exportButton);
            recentContainer.addView(card);
        }
    }

    private LinearLayout vertical() {
        LinearLayout layout = new LinearLayout(this);
        layout.setOrientation(LinearLayout.VERTICAL);
        return layout;
    }

    private TextView text(String value, int sp, boolean bold) {
        TextView view = new TextView(this);
        view.setText(value);
        view.setTextSize(sp);
        view.setTextColor(Color.rgb(35, 39, 42));
        if (bold) view.setTypeface(android.graphics.Typeface.DEFAULT_BOLD);
        return view;
    }

    private Button button(String label) {
        Button button = new Button(this);
        button.setText(label);
        button.setAllCaps(false);
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(-1, -2);
        params.setMargins(0, dp(8), 0, 0);
        button.setLayoutParams(params);
        return button;
    }

    private EditText input(String hint, int type) {
        EditText edit = new EditText(this);
        edit.setHint(hint);
        edit.setInputType(type);
        edit.setSingleLine(true);
        return edit;
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }

    private static Double parsePositive(String raw) {
        if (raw == null || raw.trim().isEmpty()) return null;
        try {
            double value = Double.parseDouble(raw.trim().replace(',', '.'));
            return value > 0.0 && !Double.isNaN(value) && !Double.isInfinite(value)
                    ? value : null;
        } catch (NumberFormatException ignored) {
            return null;
        }
    }
}

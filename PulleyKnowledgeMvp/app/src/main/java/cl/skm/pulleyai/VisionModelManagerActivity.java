package cl.skm.pulleyai;

import android.app.Activity;
import android.content.Intent;
import android.graphics.Color;
import android.net.Uri;
import android.os.Bundle;
import android.view.Gravity;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import java.io.InputStream;

/** Installs the two custom model files and validates their actual LiteRT tensor contracts. */
public final class VisionModelManagerActivity extends Activity {
    private static final int OPEN_MODEL_PACKAGE = 6110;

    private TextView statusView;
    private Button importButton;
    private Button validateButton;
    private VisionModelStore store;
    private VisionAiRuntime runtime;
    private boolean busy;

    @Override protected void onCreate(Bundle state) {
        super.onCreate(state);
        store = new VisionModelStore(this);
        buildUi();
        refresh();
    }

    @Override protected void onDestroy() {
        if (runtime != null) runtime.close();
        super.onDestroy();
    }

    private void buildUi() {
        ScrollView scroll = new ScrollView(this);
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setGravity(Gravity.TOP);
        root.setPadding(dp(22), dp(18), dp(22), dp(28));
        root.setBackgroundColor(Color.rgb(244, 247, 249));
        scroll.addView(root);

        TextView title = text("IA VISUAL DE POLEAS · ALPHA60", 25, true);
        title.setTextColor(Color.rgb(18, 52, 73));
        root.addView(title);

        TextView description = text(
                "Arquitectura seleccionada: YOLO11n-seg INT8 512×512 para segmentación de instancias "
                        + "y MobileNetV3-Small INT8 224×224 para calidad/contexto. Los modelos deben estar "
                        + "entrenados con fotografías reales de poleas; un modelo COCO genérico no se acepta "
                        + "como reconocimiento industrial.", 14, false);
        description.setPadding(0, dp(6), 0, dp(14));
        root.addView(description);

        statusView = text("Revisando paquete IA…", 13, true);
        statusView.setPadding(dp(14), dp(13), dp(14), dp(13));
        statusView.setBackgroundColor(Color.WHITE);
        root.addView(statusView);

        importButton = button("IMPORTAR PAQUETE IA VERIFICADO");
        importButton.setOnClickListener(view -> openPackage());
        root.addView(importButton);

        validateButton = button("VALIDAR MODELOS EN LITERT");
        validateButton.setOnClickListener(view -> validateRuntime());
        root.addView(validateButton);

        TextView contract = text(
                "Contenido obligatorio del ZIP:\n"
                        + "• vision_manifest.json\n"
                        + "• pulley_yolo11n_seg_int8.tflite\n"
                        + "• capture_quality_mobilenetv3_small_int8.tflite\n\n"
                        + "Clases YOLO: pulley_shell, pulley_end_disc, pulley_shaft, bearing_housing, "
                        + "other_pulley, person, obstruction.\n\n"
                        + "Clases MobileNet: good_capture, wrong_target, multiple_pulleys, partial_shell, "
                        + "person_obstruction, tool_obstruction, motion_blur, strong_reflection, too_dark, "
                        + "too_far, too_close.\n\n"
                        + "La app verifica SHA-256, dimensiones, cuantización y tensores reales. La inferencia "
                        + "sobre la cámara se habilitará solamente cuando el paquete supere este contrato.",
                12, false);
        contract.setPadding(0, dp(16), 0, dp(10));
        root.addView(contract);

        Button back = button("VOLVER");
        back.setOnClickListener(view -> finish());
        root.addView(back);
        setContentView(scroll);
    }

    private void openPackage() {
        if (busy) return;
        Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        intent.setType("application/zip");
        intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
        intent.putExtra(Intent.EXTRA_MIME_TYPES,
                new String[]{"application/zip", "application/octet-stream"});
        startActivityForResult(intent, OPEN_MODEL_PACKAGE);
    }

    private void install(Uri uri) {
        if (uri == null || busy) return;
        busy = true;
        renderBusy("Instalando y verificando SHA-256 del paquete IA…");
        new Thread(() -> {
            try (InputStream input = getContentResolver().openInputStream(uri)) {
                if (input == null) throw new IllegalStateException("No se pudo abrir el ZIP");
                final VisionModelStore.Status result = store.install(input);
                runOnUiThread(() -> {
                    busy = false;
                    refresh();
                    Toast.makeText(this, result.ready
                            ? "Paquete IA instalado" : result.message, Toast.LENGTH_LONG).show();
                });
            } catch (Exception error) {
                runOnUiThread(() -> {
                    busy = false;
                    statusView.setText("INSTALACIÓN BLOQUEADA\n" + message(error));
                    statusView.setTextColor(Color.rgb(150, 30, 30));
                    importButton.setEnabled(true);
                    validateButton.setEnabled(store.status().ready);
                });
            }
        }, "Alpha60VisionModelInstaller").start();
    }

    private void validateRuntime() {
        if (busy) return;
        VisionModelStore.Status status = store.status();
        if (!status.ready) {
            Toast.makeText(this, status.message, Toast.LENGTH_LONG).show();
            return;
        }
        busy = true;
        renderBusy("Inicializando Google Play services LiteRT y leyendo tensores reales…");
        if (runtime != null) runtime.close();
        runtime = new VisionAiRuntime(this);
        runtime.initialize(report -> runOnUiThread(() -> {
            busy = false;
            statusView.setText(report.summary);
            statusView.setTextColor(report.ready
                    ? Color.rgb(25, 108, 65) : Color.rgb(150, 30, 30));
            importButton.setEnabled(true);
            validateButton.setEnabled(true);
        }));
    }

    private void refresh() {
        VisionModelStore.Status status = store.status();
        statusView.setText(status.summary());
        statusView.setTextColor(status.ready
                ? Color.rgb(25, 108, 65)
                : status.installed ? Color.rgb(150, 30, 30) : Color.rgb(145, 82, 0));
        importButton.setEnabled(!busy);
        validateButton.setEnabled(!busy && status.ready);
    }

    private void renderBusy(String message) {
        statusView.setText(message);
        statusView.setTextColor(Color.rgb(35, 84, 117));
        importButton.setEnabled(false);
        validateButton.setEnabled(false);
    }

    @Override protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == OPEN_MODEL_PACKAGE && resultCode == RESULT_OK
                && data != null && data.getData() != null) {
            install(data.getData());
        }
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

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }

    private static String message(Throwable error) {
        return error == null ? "Error desconocido"
                : error.getMessage() == null ? error.getClass().getSimpleName() : error.getMessage();
    }
}

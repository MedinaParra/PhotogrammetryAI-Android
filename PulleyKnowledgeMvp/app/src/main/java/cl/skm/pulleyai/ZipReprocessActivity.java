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

import java.io.BufferedInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.OutputStream;

/** UI for importing an exported capture ZIP and producing multiscale pair diagnostics. */
public final class ZipReprocessActivity extends Activity {
    private static final int OPEN_ZIP = 4510;
    private static final int SAVE_JSON = 4511;
    private static final int SAVE_PACKAGE = 4512;

    private TextView status;
    private Button selectButton;
    private Button saveJsonButton;
    private Button savePackageButton;
    private volatile boolean processing;
    private MultiScaleZipReprocessor.Result result;

    @Override protected void onCreate(Bundle state) {
        super.onCreate(state);
        buildUi();
    }

    private void buildUi() {
        ScrollView scroll = new ScrollView(this);
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setGravity(Gravity.TOP);
        root.setPadding(dp(22), dp(18), dp(22), dp(26));
        root.setBackgroundColor(Color.rgb(244, 247, 249));
        scroll.addView(root);

        TextView title = text("REPROCESAR ZIP MULTIESCALA · ALPHA47", 25, true);
        title.setTextColor(Color.rgb(18, 52, 73));
        root.addView(title);

        TextView description = text(
                "Importa un ZIP exportado por SKM Polea AI, valida SHA-256, compara alpha46 y ejecuta una pirámide 1.0/0.8/0.64 con orientación y descriptor binario de 256 bits. El informe separa el grafo primario de los puentes diagnósticos. Los puentes nunca habilitan BA, nube métrica, CAD ni liberación industrial.",
                14, false);
        description.setPadding(0, dp(5), 0, dp(14));
        root.addView(description);

        status = text("Seleccione el ZIP demo_3 u otro paquete de captura.", 14, false);
        status.setPadding(dp(14), dp(13), dp(14), dp(13));
        status.setBackgroundColor(Color.WHITE);
        root.addView(status);

        selectButton = button("SELECCIONAR Y REPROCESAR ZIP");
        selectButton.setOnClickListener(view -> openZipPicker());
        root.addView(selectButton);

        saveJsonButton = button("GUARDAR DIAGNÓSTICO MULTIESCALA JSON");
        saveJsonButton.setEnabled(false);
        saveJsonButton.setOnClickListener(view -> saveResult(false));
        root.addView(saveJsonButton);

        savePackageButton = button("GUARDAR PAQUETE COMPARATIVO ZIP");
        savePackageButton.setEnabled(false);
        savePackageButton.setOnClickListener(view -> saveResult(true));
        root.addView(savePackageButton);

        Button close = button("VOLVER");
        close.setOnClickListener(view -> finish());
        root.addView(close);
        setContentView(scroll);
    }

    private void openZipPicker() {
        if (processing) return;
        Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        intent.setType("application/zip");
        intent.putExtra(Intent.EXTRA_MIME_TYPES,
                new String[]{"application/zip", "application/octet-stream"});
        startActivityForResult(intent, OPEN_ZIP);
    }

    private void startProcessing(Uri uri) {
        processing = true;
        result = null;
        selectButton.setEnabled(false);
        saveJsonButton.setEnabled(false);
        savePackageButton.setEnabled(false);
        status.setText("Abriendo ZIP y ejecutando comparación alpha46…");
        status.setTextColor(Color.rgb(35, 84, 117));
        new Thread(() -> {
            try {
                final MultiScaleZipReprocessor.Result completed =
                        MultiScaleZipReprocessor.process(
                                ZipReprocessActivity.this, uri,
                                message -> runOnUiThread(() -> status.setText(message)));
                runOnUiThread(() -> {
                    processing = false;
                    result = completed;
                    selectButton.setEnabled(true);
                    saveJsonButton.setEnabled(true);
                    savePackageButton.setEnabled(true);
                    status.setText(completed.summary);
                    if (completed.primaryGraphConnected) {
                        status.setTextColor(Color.rgb(25, 108, 65));
                    } else if (completed.diagnosticGraphConnected) {
                        status.setTextColor(Color.rgb(145, 82, 0));
                    } else {
                        status.setTextColor(Color.rgb(150, 30, 30));
                    }
                });
            } catch (Exception error) {
                runOnUiThread(() -> {
                    processing = false;
                    selectButton.setEnabled(true);
                    String message = error.getMessage() == null
                            ? error.getClass().getSimpleName() : error.getMessage();
                    status.setText("REPROCESAMIENTO BLOQUEADO\n" + message);
                    status.setTextColor(Color.rgb(150, 30, 30));
                });
            }
        }, "MultiScaleZipReprocessor").start();
    }

    private void saveResult(boolean packageZip) {
        MultiScaleZipReprocessor.Result current = result;
        if (current == null) return;
        File source = packageZip ? current.packageFile : current.reportFile;
        if (source == null || !source.isFile()) {
            Toast.makeText(this, "El archivo de diagnóstico no existe.",
                    Toast.LENGTH_LONG).show();
            return;
        }
        Intent intent = new Intent(Intent.ACTION_CREATE_DOCUMENT);
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        intent.setType(packageZip ? "application/zip" : "application/json");
        intent.putExtra(Intent.EXTRA_TITLE, source.getName());
        startActivityForResult(intent, packageZip ? SAVE_PACKAGE : SAVE_JSON);
    }

    @Override protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (resultCode != RESULT_OK || data == null || data.getData() == null) return;
        Uri uri = data.getData();
        if (requestCode == OPEN_ZIP) {
            try {
                getContentResolver().takePersistableUriPermission(uri,
                        data.getFlags() & (Intent.FLAG_GRANT_READ_URI_PERMISSION
                                | Intent.FLAG_GRANT_WRITE_URI_PERMISSION));
            } catch (SecurityException ignored) {
                // The current read grant is sufficient for immediate processing.
            }
            startProcessing(uri);
            return;
        }
        MultiScaleZipReprocessor.Result current = result;
        if (current == null) return;
        File source = requestCode == SAVE_PACKAGE ? current.packageFile
                : requestCode == SAVE_JSON ? current.reportFile : null;
        if (source == null) return;
        try (BufferedInputStream input = new BufferedInputStream(new FileInputStream(source));
             OutputStream output = getContentResolver().openOutputStream(uri, "w")) {
            if (output == null) throw new IllegalStateException("No se pudo abrir el destino");
            byte[] buffer = new byte[64 * 1024];
            int read;
            while ((read = input.read(buffer)) >= 0) output.write(buffer, 0, read);
            Toast.makeText(this, "Diagnóstico guardado correctamente",
                    Toast.LENGTH_LONG).show();
        } catch (Exception error) {
            String message = error.getMessage() == null
                    ? "No se pudo guardar" : error.getMessage();
            Toast.makeText(this, message, Toast.LENGTH_LONG).show();
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
}

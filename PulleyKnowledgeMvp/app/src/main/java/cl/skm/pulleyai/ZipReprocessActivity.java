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

/** UI for canonical ZIP preflight, multiscale tracks and bounded seed geometry. */
public final class ZipReprocessActivity extends Activity {
    private static final int OPEN_ZIP = 4510;
    private static final int SAVE_JSON = 4511;
    private static final int SAVE_PACKAGE = 4512;

    private TextView status;
    private Button selectButton;
    private Button saveJsonButton;
    private Button savePackageButton;
    private volatile boolean processing;
    private CaptureZipNormalizer.Result normalizedResult;
    private MultiScaleZipReprocessor.Result multiscaleResult;
    private ImportedTrackZipAnalyzer.Result trackResult;
    private ImportedSeedGeometryZipAnalyzer.Result seedResult;

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

        TextView title = text("REPROCESAR ZIP / GEOMETRÍA · ALPHA50 LAB2", 25, true);
        title.setTextColor(Color.rgb(18, 52, 73));
        root.addView(title);

        TextView description = text(
                "Versión 0.18.0-alpha50. Normaliza el ZIP y registra la causa por fotografía; después ejecuta matching multiescala, tracks de tres o más vistas y una pose semilla con barrido focal acotado. La geometría semilla no equivale a reconstrucción global, escala métrica ni liberación industrial.",
                14, false);
        description.setPadding(0, dp(5), 0, dp(14));
        root.addView(description);

        status = text("Seleccione demo_3_00b90b75.zip u otro paquete skm-polea-capture/*.",
                14, false);
        status.setPadding(dp(14), dp(13), dp(14), dp(13));
        status.setBackgroundColor(Color.WHITE);
        root.addView(status);

        selectButton = button("SELECCIONAR Y REPROCESAR ZIP · ALPHA50");
        selectButton.setOnClickListener(view -> openZipPicker());
        root.addView(selectButton);

        saveJsonButton = button("GUARDAR GEOMETRÍA / TRACKS / PREFLIGHT JSON");
        saveJsonButton.setEnabled(false);
        saveJsonButton.setOnClickListener(view -> saveResult(false));
        root.addView(saveJsonButton);

        savePackageButton = button("GUARDAR PAQUETE TRAZABLE ZIP");
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

    private void startProcessing(Uri sourceUri) {
        processing = true;
        normalizedResult = null;
        multiscaleResult = null;
        trackResult = null;
        seedResult = null;
        selectButton.setEnabled(false);
        saveJsonButton.setEnabled(false);
        savePackageButton.setEnabled(false);
        status.setText("alpha50 · iniciando preflight del ZIP…");
        status.setTextColor(Color.rgb(35, 84, 117));
        new Thread(() -> {
            CaptureZipNormalizer.Result normalized = null;
            MultiScaleZipReprocessor.Result multiscale = null;
            ImportedTrackZipAnalyzer.Result tracks = null;
            try {
                normalized = CaptureZipNormalizer.normalize(
                        ZipReprocessActivity.this, sourceUri,
                        message -> runOnUiThread(() -> status.setText(message)));
                normalizedResult = normalized;
                final CaptureZipNormalizer.Result preflight = normalized;
                runOnUiThread(() -> {
                    saveJsonButton.setEnabled(true);
                    status.setText(preflight.summary
                            + "\n\nPreflight aprobado. Iniciando matching multiescala…");
                });

                Uri canonicalUri = Uri.fromFile(normalized.normalizedZip);
                multiscale = MultiScaleZipReprocessor.process(
                        ZipReprocessActivity.this, canonicalUri,
                        message -> runOnUiThread(() -> status.setText(
                                preflight.summary + "\n\n" + message)));
                multiscaleResult = multiscale;
                final MultiScaleZipReprocessor.Result alpha47 = multiscale;
                runOnUiThread(() -> status.setText(preflight.summary + "\n\n"
                        + alpha47.summary
                        + "\n\nMatching completo. Iniciando tracks…"));

                tracks = ImportedTrackZipAnalyzer.process(
                        ZipReprocessActivity.this, canonicalUri, alpha47,
                        message -> runOnUiThread(() -> status.setText(
                                preflight.summary + "\n\n" + message)));
                trackResult = tracks;
                final ImportedTrackZipAnalyzer.Result alpha48 = tracks;
                runOnUiThread(() -> status.setText(preflight.summary + "\n\n"
                        + alpha47.summary + "\n\n" + alpha48.summary
                        + "\n\nTracks completos. Buscando pose y nube semilla…"));

                final ImportedSeedGeometryZipAnalyzer.Result seed =
                        ImportedSeedGeometryZipAnalyzer.process(
                                ZipReprocessActivity.this, canonicalUri, alpha48,
                                message -> runOnUiThread(() -> status.setText(
                                        preflight.summary + "\n\n" + message)));
                runOnUiThread(() -> showCompleted(preflight, alpha47, alpha48, seed));
            } catch (Exception error) {
                final CaptureZipNormalizer.Result availablePreflight = normalized;
                final MultiScaleZipReprocessor.Result availableMultiscale = multiscale;
                final ImportedTrackZipAnalyzer.Result availableTracks = tracks;
                runOnUiThread(() -> showFailure(availablePreflight,
                        availableMultiscale, availableTracks, error));
            }
        }, "Alpha50ImportedGeometryReprocessor").start();
    }

    private void showCompleted(CaptureZipNormalizer.Result preflight,
                               MultiScaleZipReprocessor.Result alpha47,
                               ImportedTrackZipAnalyzer.Result tracks,
                               ImportedSeedGeometryZipAnalyzer.Result seed) {
        processing = false;
        normalizedResult = preflight;
        multiscaleResult = alpha47;
        trackResult = tracks;
        seedResult = seed;
        selectButton.setEnabled(true);
        saveJsonButton.setEnabled(true);
        savePackageButton.setEnabled(true);
        status.setText(preflight.summary + "\n\n" + alpha47.summary
                + "\n\n" + tracks.summary + "\n\n" + seed.summary);
        boolean globalConnected = alpha47.primaryGraphConnected;
        if (globalConnected && seed.geometry.geometryReady) {
            status.setTextColor(Color.rgb(25, 108, 65));
        } else if (seed.geometry.solved || !tracks.tracks.tracks.isEmpty()
                || alpha47.diagnosticGraphConnected) {
            status.setTextColor(Color.rgb(145, 82, 0));
        } else {
            status.setTextColor(Color.rgb(150, 30, 30));
        }
    }

    private void showFailure(CaptureZipNormalizer.Result preflight,
                             MultiScaleZipReprocessor.Result multiscale,
                             ImportedTrackZipAnalyzer.Result tracks,
                             Exception error) {
        processing = false;
        normalizedResult = preflight;
        multiscaleResult = multiscale;
        trackResult = tracks;
        seedResult = null;
        selectButton.setEnabled(true);
        saveJsonButton.setEnabled(preflight != null || multiscale != null || tracks != null);
        savePackageButton.setEnabled(multiscale != null || tracks != null);
        String message = error.getMessage() == null
                ? error.getClass().getSimpleName() : error.getMessage();
        StringBuilder available = new StringBuilder();
        if (preflight != null) available.append(preflight.summary);
        if (multiscale != null) {
            if (available.length() > 0) available.append("\n\n");
            available.append(multiscale.summary);
        }
        if (tracks != null) {
            if (available.length() > 0) available.append("\n\n");
            available.append(tracks.summary);
        }
        if (available.length() > 0) available.append("\n\n");
        available.append("ETAPA SIGUIENTE BLOQUEADA\n").append(message)
                .append("\nPuede guardar la evidencia de la última etapa completada.");
        status.setText(available.toString());
        status.setTextColor(tracks != null || multiscale != null
                ? Color.rgb(145, 82, 0) : Color.rgb(150, 30, 30));
    }

    private void saveResult(boolean packageZip) {
        File source;
        if (seedResult != null) {
            source = packageZip ? seedResult.packageFile : seedResult.reportFile;
        } else if (trackResult != null) {
            source = packageZip ? trackResult.packageFile : trackResult.trackFile;
        } else if (multiscaleResult != null) {
            source = packageZip ? multiscaleResult.packageFile : multiscaleResult.reportFile;
        } else if (!packageZip && normalizedResult != null) {
            source = normalizedResult.preflightFile;
        } else {
            return;
        }
        if (source == null || !source.isFile()) {
            Toast.makeText(this, "El archivo de evidencia no existe.", Toast.LENGTH_LONG).show();
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
        File source = null;
        if (seedResult != null) {
            source = requestCode == SAVE_PACKAGE ? seedResult.packageFile
                    : requestCode == SAVE_JSON ? seedResult.reportFile : null;
        } else if (trackResult != null) {
            source = requestCode == SAVE_PACKAGE ? trackResult.packageFile
                    : requestCode == SAVE_JSON ? trackResult.trackFile : null;
        } else if (multiscaleResult != null) {
            source = requestCode == SAVE_PACKAGE ? multiscaleResult.packageFile
                    : requestCode == SAVE_JSON ? multiscaleResult.reportFile : null;
        } else if (normalizedResult != null && requestCode == SAVE_JSON) {
            source = normalizedResult.preflightFile;
        }
        if (source == null) return;
        try (BufferedInputStream input = new BufferedInputStream(new FileInputStream(source));
             OutputStream output = getContentResolver().openOutputStream(uri, "w")) {
            if (output == null) throw new IllegalStateException("No se pudo abrir el destino");
            byte[] buffer = new byte[64 * 1024];
            int read;
            while ((read = input.read(buffer)) >= 0) output.write(buffer, 0, read);
            Toast.makeText(this, "Evidencia guardada correctamente", Toast.LENGTH_LONG).show();
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

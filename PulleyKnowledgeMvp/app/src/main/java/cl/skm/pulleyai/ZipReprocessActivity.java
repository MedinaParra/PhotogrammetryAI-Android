package cl.skm.pulleyai;

import android.Manifest;
import android.app.Activity;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.IBinder;
import android.text.InputType;
import android.view.Gravity;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import java.io.BufferedInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.OutputStream;
import java.util.Locale;

/** UI client for persistent ZIP reprocessing and alpha59 prior-constrained cylinder inspection. */
public final class ZipReprocessActivity extends Activity
        implements ZipReprocessForegroundService.Listener {
    private static final int OPEN_ZIP = 4510;
    private static final int SAVE_JSON = 4511;
    private static final int SAVE_PACKAGE = 4512;
    private static final int NOTIFICATION_PERMISSION = 4513;
    private static final String DIMENSION_PREFS = "pulley_dimension_priors_alpha59";
    private static final String PREF_LENGTH = "shellLengthMm";
    private static final String PREF_DIAMETER = "shellDiameterMm";

    private TextView status;
    private EditText lengthInput;
    private EditText diameterInput;
    private Button selectButton;
    private Button retryButton;
    private Button cylinderButton;
    private Button rawCloudButton;
    private Button saveJsonButton;
    private Button savePackageButton;

    private ZipReprocessForegroundService service;
    private boolean bound;
    private ZipReprocessForegroundService.Snapshot current;

    private final ServiceConnection connection = new ServiceConnection() {
        @Override public void onServiceConnected(ComponentName name, IBinder binder) {
            service = ((ZipReprocessForegroundService.LocalBinder) binder).service();
            bound = true;
            service.registerListener(ZipReprocessActivity.this);
            current = service.snapshot();
            render(current);
            service.resumePersistedIfNeeded();
        }

        @Override public void onServiceDisconnected(ComponentName name) {
            bound = false;
            service = null;
        }
    };

    @Override protected void onCreate(Bundle state) {
        super.onCreate(state);
        buildUi();
        restoreDimensions();
        requestNotificationPermission();
    }

    @Override protected void onStart() {
        super.onStart();
        Intent intent = new Intent(this, ZipReprocessForegroundService.class);
        bindService(intent, connection, Context.BIND_AUTO_CREATE);
    }

    @Override protected void onStop() {
        if (bound && service != null) {
            service.unregisterListener(this);
            unbindService(connection);
        }
        bound = false;
        service = null;
        super.onStop();
    }

    @Override public void onSnapshot(ZipReprocessForegroundService.Snapshot snapshot) {
        current = snapshot;
        render(snapshot);
    }

    private void buildUi() {
        ScrollView scroll = new ScrollView(this);
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setGravity(Gravity.TOP);
        root.setPadding(dp(22), dp(18), dp(22), dp(26));
        root.setBackgroundColor(Color.rgb(244, 247, 249));
        scroll.addView(root);

        TextView title = text("REPROCESAR ZIP / CILINDRO · ALPHA59 LAB2", 24, true);
        title.setTextColor(Color.rgb(18, 52, 73));
        root.addView(title);

        TextView description = text(
                "El ZIP continúa procesándose en el servicio persistente. El visor alpha59 mantiene la nube semilla cruda, "
                        + "pero agrega un ajuste cilíndrico robusto alineado al eje del manto. Largo y diámetro son restricciones "
                        + "conocidas: permiten orientar y escalar el modelo, pero no constituyen validación metrológica.",
                13, false);
        description.setPadding(0, dp(5), 0, dp(8));
        root.addView(description);

        lengthInput = input("Largo conocido del manto [mm]");
        diameterInput = input("Diámetro conocido del manto [mm]");
        root.addView(lengthInput);
        root.addView(diameterInput);

        TextView priorWarning = text(
                "Use dimensiones nominales o medidas previamente con instrumento. El ajuste mostrará FIT_ACCEPTED, FIT_WEAK o PRIOR_ONLY.",
                11, true);
        priorWarning.setTextColor(Color.rgb(125, 78, 0));
        priorWarning.setPadding(0, dp(3), 0, dp(8));
        root.addView(priorWarning);

        status = text("Conectando con el servicio de análisis…", 14, false);
        status.setPadding(dp(14), dp(13), dp(14), dp(13));
        status.setBackgroundColor(Color.WHITE);
        root.addView(status);

        selectButton = button("SELECCIONAR Y ANALIZAR ZIP · ALPHA59");
        selectButton.setOnClickListener(view -> openZipPicker());
        root.addView(selectButton);

        retryButton = button("REPROCESAR ÚLTIMO ZIP SIN SELECCIONARLO");
        retryButton.setEnabled(false);
        retryButton.setOnClickListener(view -> retryLastZip());
        root.addView(retryButton);

        cylinderButton = button("AJUSTAR CILINDRO 3D · NUBE NO DISPONIBLE");
        cylinderButton.setEnabled(false);
        cylinderButton.setOnClickListener(view -> openCylinderModel());
        root.addView(cylinderButton);

        rawCloudButton = button("VER NUBE SEMILLA CRUDA");
        rawCloudButton.setEnabled(false);
        rawCloudButton.setOnClickListener(view -> openRawPointCloud());
        root.addView(rawCloudButton);

        saveJsonButton = button("GUARDAR DECISIÓN FINAL JSON");
        saveJsonButton.setEnabled(false);
        saveJsonButton.setOnClickListener(view -> saveResult(false));
        root.addView(saveJsonButton);

        savePackageButton = button("GUARDAR PAQUETE FINAL TRAZABLE ZIP");
        savePackageButton.setEnabled(false);
        savePackageButton.setOnClickListener(view -> saveResult(true));
        root.addView(savePackageButton);

        Button close = button("VOLVER");
        close.setOnClickListener(view -> finish());
        root.addView(close);
        setContentView(scroll);
    }

    private EditText input(String hint) {
        EditText input = new EditText(this);
        input.setHint(hint);
        input.setSingleLine(true);
        input.setInputType(InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_FLAG_DECIMAL);
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(-1, -2);
        params.setMargins(0, dp(5), 0, 0);
        input.setLayoutParams(params);
        return input;
    }

    private void restoreDimensions() {
        SharedPreferences prefs = getSharedPreferences(DIMENSION_PREFS, MODE_PRIVATE);
        double length = Double.longBitsToDouble(prefs.getLong(PREF_LENGTH,
                Double.doubleToRawLongBits(Double.NaN)));
        double diameter = Double.longBitsToDouble(prefs.getLong(PREF_DIAMETER,
                Double.doubleToRawLongBits(Double.NaN)));
        if (Double.isFinite(length) && length > 0.0) {
            lengthInput.setText(String.format(Locale.ROOT, "%.1f", length));
        }
        if (Double.isFinite(diameter) && diameter > 0.0) {
            diameterInput.setText(String.format(Locale.ROOT, "%.1f", diameter));
        }
    }

    private double[] requireDimensions() {
        Double length = parsePositive(lengthInput.getText().toString());
        Double diameter = parsePositive(diameterInput.getText().toString());
        if (length == null) {
            lengthInput.setError("Ingrese un largo mayor que cero");
            lengthInput.requestFocus();
            return null;
        }
        if (diameter == null) {
            diameterInput.setError("Ingrese un diámetro mayor que cero");
            diameterInput.requestFocus();
            return null;
        }
        getSharedPreferences(DIMENSION_PREFS, MODE_PRIVATE).edit()
                .putLong(PREF_LENGTH, Double.doubleToRawLongBits(length))
                .putLong(PREF_DIAMETER, Double.doubleToRawLongBits(diameter))
                .apply();
        return new double[]{length, diameter};
    }

    private Double parsePositive(String text) {
        try {
            double value = Double.parseDouble(text == null ? "" : text.trim().replace(',', '.'));
            return Double.isFinite(value) && value > 0.0 ? value : null;
        } catch (RuntimeException ignored) {
            return null;
        }
    }

    private void requestNotificationPermission() {
        if (Build.VERSION.SDK_INT >= 33
                && checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS)
                != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]{Manifest.permission.POST_NOTIFICATIONS},
                    NOTIFICATION_PERMISSION);
        }
    }

    private void openZipPicker() {
        if (current != null && current.running) return;
        Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        intent.setType("application/zip");
        intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION
                | Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION);
        intent.putExtra(Intent.EXTRA_MIME_TYPES,
                new String[]{"application/zip", "application/octet-stream"});
        startActivityForResult(intent, OPEN_ZIP);
    }

    private void startProcessing(Uri sourceUri) {
        if (sourceUri == null) return;
        Intent intent = new Intent(this, ZipReprocessForegroundService.class)
                .setAction(ZipReprocessForegroundService.ACTION_START)
                .putExtra(ZipReprocessForegroundService.EXTRA_SOURCE_URI, sourceUri.toString());
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) startForegroundService(intent);
        else startService(intent);
        status.setText("alpha59 · análisis iniciado en segundo plano. Puede apagar la pantalla.");
        status.setTextColor(Color.rgb(35, 84, 117));
        selectButton.setEnabled(false);
        retryButton.setEnabled(false);
    }

    private void retryLastZip() {
        if (current == null || current.running || current.sourceUri.isEmpty()) return;
        startProcessing(Uri.parse(current.sourceUri));
    }

    private void openCylinderModel() {
        if (current == null || !current.hasSeedCloud()) {
            Toast.makeText(this, "La etapa semilla no produjo coordenadas 3D.", Toast.LENGTH_LONG).show();
            return;
        }
        double[] dimensions = requireDimensions();
        if (dimensions == null) return;
        Intent intent = new Intent(this, PulleyCylinderViewerActivity.class);
        intent.putExtra(PulleyCylinderViewerActivity.EXTRA_REPORT_PATH, current.seedReportPath);
        intent.putExtra(PulleyCylinderViewerActivity.EXTRA_SHELL_LENGTH_MM, dimensions[0]);
        intent.putExtra(PulleyCylinderViewerActivity.EXTRA_SHELL_DIAMETER_MM, dimensions[1]);
        startActivity(intent);
    }

    private void openRawPointCloud() {
        if (current == null || !current.hasSeedCloud()) {
            Toast.makeText(this, "La etapa semilla no produjo coordenadas 3D visibles.", Toast.LENGTH_LONG).show();
            return;
        }
        Intent intent = new Intent(this, PointCloudViewerActivity.class);
        intent.putExtra(PointCloudViewerActivity.EXTRA_REPORT_PATH, current.seedReportPath);
        intent.putExtra(PointCloudViewerActivity.EXTRA_SUMMARY, current.seedSummary);
        startActivity(intent);
    }

    private void render(ZipReprocessForegroundService.Snapshot snapshot) {
        if (snapshot == null || status == null) return;
        boolean running = snapshot.running;
        selectButton.setEnabled(!running);
        retryButton.setEnabled(!running && !snapshot.sourceUri.isEmpty());
        cylinderButton.setEnabled(snapshot.hasSeedCloud());
        rawCloudButton.setEnabled(snapshot.hasSeedCloud());
        saveJsonButton.setEnabled(snapshot.hasReport());
        savePackageButton.setEnabled(snapshot.hasPackage());

        selectButton.setText(running
                ? "ANÁLISIS ACTIVO EN SEGUNDO PLANO"
                : "SELECCIONAR Y ANALIZAR OTRO ZIP · ALPHA59");
        retryButton.setText(snapshot.sourceUri.isEmpty()
                ? "REPROCESAR ÚLTIMO ZIP · NO DISPONIBLE"
                : "REPROCESAR ÚLTIMO ZIP SIN SELECCIONARLO");
        cylinderButton.setText(snapshot.hasSeedCloud()
                ? "AJUSTAR CILINDRO 3D · " + snapshot.seedPointCount + " PUNTOS CRUDOS"
                : "AJUSTAR CILINDRO 3D · NUBE NO DISPONIBLE");
        rawCloudButton.setText(snapshot.hasSeedCloud()
                ? "VER NUBE SEMILLA CRUDA · " + snapshot.seedPointCount + " PUNTOS"
                : "VER NUBE SEMILLA CRUDA · NO DISPONIBLE");

        String lifecycle = running
                ? "\n\nPROCESAMIENTO PERSISTENTE ACTIVO\nPuede girar el teléfono, bloquear la pantalla o usar otra aplicación."
                : snapshot.completed
                ? "\n\nANÁLISIS FINALIZADO Y ESTADO RECUPERABLE."
                : snapshot.failed
                ? "\n\nEL ZIP SE CONSERVA: puede reintentar sin seleccionarlo nuevamente."
                : "";
        status.setText(snapshot.message + lifecycle);
        status.setTextColor(running ? Color.rgb(35, 84, 117)
                : snapshot.failed ? Color.rgb(150, 30, 30)
                : snapshot.completed ? Color.rgb(25, 108, 65)
                : Color.rgb(35, 39, 42));
    }

    private void saveResult(boolean packageZip) {
        if (current == null) return;
        String path = packageZip ? current.packagePath : current.reportPath;
        File source = path == null || path.isEmpty() ? null : new File(path);
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
            int flags = data.getFlags() & (Intent.FLAG_GRANT_READ_URI_PERMISSION
                    | Intent.FLAG_GRANT_WRITE_URI_PERMISSION);
            try {
                getContentResolver().takePersistableUriPermission(uri,
                        flags | Intent.FLAG_GRANT_READ_URI_PERMISSION);
            } catch (SecurityException ignored) {
                // Immediate read access still permits this run; many providers omit persistable flags.
            }
            startProcessing(uri);
            return;
        }
        if (current == null) return;
        String path = requestCode == SAVE_PACKAGE
                ? current.packagePath : requestCode == SAVE_JSON ? current.reportPath : "";
        File source = path == null || path.isEmpty() ? null : new File(path);
        if (source == null || !source.isFile()) return;
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

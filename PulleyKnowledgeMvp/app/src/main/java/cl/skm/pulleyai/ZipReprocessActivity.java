package cl.skm.pulleyai;

import android.Manifest;
import android.app.Activity;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.IBinder;
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

/** UI client for the persistent alpha55 foreground ZIP reprocessor. */
public final class ZipReprocessActivity extends Activity
        implements ZipReprocessForegroundService.Listener {
    private static final int OPEN_ZIP = 4510;
    private static final int SAVE_JSON = 4511;
    private static final int SAVE_PACKAGE = 4512;
    private static final int NOTIFICATION_PERMISSION = 4513;

    private TextView status;
    private Button selectButton;
    private Button retryButton;
    private Button viewCloudButton;
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

        TextView title = text("REPROCESAR ZIP / RESULTADOS · ALPHA55 LAB2", 25, true);
        title.setTextColor(Color.rgb(18, 52, 73));
        root.addView(title);

        TextView description = text(
                "Versión 0.18.0-alpha55. El análisis se ejecuta como servicio persistente: "
                        + "puede girar el equipo, cambiar de aplicación o apagar la pantalla sin perder "
                        + "el ZIP ni reiniciar el roadmap. Una notificación muestra el progreso. "
                        + "La nube semilla continúa sin escala métrica y no representa liberación industrial.",
                14, false);
        description.setPadding(0, dp(5), 0, dp(14));
        root.addView(description);

        status = text("Conectando con el servicio de análisis…", 14, false);
        status.setPadding(dp(14), dp(13), dp(14), dp(13));
        status.setBackgroundColor(Color.WHITE);
        root.addView(status);

        selectButton = button("SELECCIONAR Y ANALIZAR ZIP · ALPHA55");
        selectButton.setOnClickListener(view -> openZipPicker());
        root.addView(selectButton);

        retryButton = button("REPROCESAR ÚLTIMO ZIP SIN SELECCIONARLO");
        retryButton.setEnabled(false);
        retryButton.setOnClickListener(view -> retryLastZip());
        root.addView(retryButton);

        viewCloudButton = button("VER RESULTADOS 3D / NUBE NO DISPONIBLE");
        viewCloudButton.setEnabled(false);
        viewCloudButton.setOnClickListener(view -> openPointCloud());
        root.addView(viewCloudButton);

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
        status.setText("alpha55 · análisis iniciado en segundo plano. Puede apagar la pantalla.");
        status.setTextColor(Color.rgb(35, 84, 117));
        selectButton.setEnabled(false);
        retryButton.setEnabled(false);
    }

    private void retryLastZip() {
        if (current == null || current.running || current.sourceUri.isEmpty()) return;
        startProcessing(Uri.parse(current.sourceUri));
    }

    private void openPointCloud() {
        if (current == null || !current.hasSeedCloud()) {
            Toast.makeText(this,
                    "La etapa semilla no produjo coordenadas 3D visibles.",
                    Toast.LENGTH_LONG).show();
            return;
        }
        Intent intent = new Intent(this, PointCloudViewerActivity.class);
        intent.putExtra(PointCloudViewerActivity.EXTRA_REPORT_PATH,
                current.seedReportPath);
        intent.putExtra(PointCloudViewerActivity.EXTRA_SUMMARY,
                current.seedSummary);
        startActivity(intent);
    }

    private void render(ZipReprocessForegroundService.Snapshot snapshot) {
        if (snapshot == null || status == null) return;
        boolean running = snapshot.running;
        selectButton.setEnabled(!running);
        retryButton.setEnabled(!running && !snapshot.sourceUri.isEmpty());
        viewCloudButton.setEnabled(snapshot.hasSeedCloud());
        saveJsonButton.setEnabled(snapshot.hasReport());
        savePackageButton.setEnabled(snapshot.hasPackage());

        selectButton.setText(running
                ? "ANÁLISIS ACTIVO EN SEGUNDO PLANO"
                : "SELECCIONAR Y ANALIZAR OTRO ZIP · ALPHA55");
        retryButton.setText(snapshot.sourceUri.isEmpty()
                ? "REPROCESAR ÚLTIMO ZIP · NO DISPONIBLE"
                : "REPROCESAR ÚLTIMO ZIP SIN SELECCIONARLO");
        viewCloudButton.setText(snapshot.hasSeedCloud()
                ? "VER RESULTADOS 3D · " + snapshot.seedPointCount + " PUNTOS"
                : "VER RESULTADOS 3D / NUBE NO DISPONIBLE");

        String lifecycle = running
                ? "\n\nPROCESAMIENTO PERSISTENTE ACTIVO\n"
                + "Puede girar el teléfono, bloquear la pantalla o usar otra aplicación."
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
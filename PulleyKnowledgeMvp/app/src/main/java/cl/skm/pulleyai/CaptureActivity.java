package cl.skm.pulleyai;

import android.Manifest;
import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.provider.Settings;
import android.view.Gravity;
import android.view.TextureView;
import android.view.View;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.security.MessageDigest;
import java.util.Locale;

/** Industrial landscape capture station. It records evidence without claiming reconstruction results. */
public final class CaptureActivity extends Activity {
    public static final String EXTRA_SESSION_ID = "capture_session_id";
    private static final int CAMERA_PERMISSION = 4102;
    private static final int EXPORT_DOCUMENT = 4208;

    private CaptureStore store;
    private CaptureStore.Session session;
    private String sessionId;
    private TextureView preview;
    private CaptureGuideView guide;
    private TextView status;
    private TextView coverage;
    private TextView poseHint;
    private TextView storage;
    private Button captureButton;
    private Button bandButton;
    private Button overlapButton;
    private Button exportButton;
    private String band = "LOW";
    private DevicePoseTracker poseTracker;
    private Camera2CaptureController camera;
    private DevicePoseTracker.Snapshot pendingPose;
    private String pendingBand;
    private boolean cameraReady;
    private boolean processing;
    private File pendingExport;
    private final Handler ui = new Handler(Looper.getMainLooper());
    private final Runnable ticker = new Runnable() {
        @Override public void run() {
            refresh();
            ui.postDelayed(this, 250L);
        }
    };

    @Override protected void onCreate(Bundle state) {
        super.onCreate(state);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        sessionId = getIntent().getStringExtra(EXTRA_SESSION_ID);
        store = new CaptureStore(this);
        session = store.getSession(sessionId);
        if (session == null) {
            Toast.makeText(this, "Sesión inexistente", Toast.LENGTH_LONG).show();
            finish();
            return;
        }
        poseTracker = new DevicePoseTracker(this);
        buildUi();
    }

    @Override protected void onResume() {
        super.onResume();
        poseTracker.start();
        ui.removeCallbacks(ticker);
        ui.post(ticker);
        if (checkSelfPermission(Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) startCamera();
        else requestPermissions(new String[]{Manifest.permission.CAMERA}, CAMERA_PERMISSION);
    }

    @Override protected void onPause() {
        ui.removeCallbacks(ticker);
        cameraReady = false;
        if (camera != null) {
            camera.stop();
            camera = null;
        }
        poseTracker.stop();
        super.onPause();
    }

    @Override protected void onDestroy() {
        store.close();
        super.onDestroy();
    }

    private void buildUi() {
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.HORIZONTAL);
        root.setBackgroundColor(Color.BLACK);

        FrameLayout cameraPane = new FrameLayout(this);
        root.addView(cameraPane, new LinearLayout.LayoutParams(0, -1, 1f));

        preview = new TextureView(this);
        cameraPane.addView(preview, new FrameLayout.LayoutParams(-1, -1));
        guide = new CaptureGuideView(this);
        cameraPane.addView(guide, new FrameLayout.LayoutParams(-1, -1));

        status = label("Preparando cámara…", 13, Color.WHITE);
        status.setPadding(dp(12), dp(8), dp(12), dp(8));
        status.setBackgroundColor(Color.argb(180, 10, 14, 17));
        FrameLayout.LayoutParams statusParams = new FrameLayout.LayoutParams(-1, -2, Gravity.TOP);
        statusParams.setMargins(dp(8), dp(8), dp(8), 0);
        cameraPane.addView(status, statusParams);

        LinearLayout panel = new LinearLayout(this);
        panel.setOrientation(LinearLayout.VERTICAL);
        panel.setGravity(Gravity.CENTER_HORIZONTAL);
        panel.setPadding(dp(12), dp(10), dp(12), dp(10));
        panel.setBackgroundColor(Color.rgb(25, 31, 35));
        int panelWidth = Math.max(dp(260), Math.min(dp(330), getResources().getDisplayMetrics().widthPixels / 3));
        root.addView(panel, new LinearLayout.LayoutParams(panelWidth, -1));

        TextView title = label("CAPTURA INDUSTRIAL", 15, Color.rgb(135, 210, 255));
        title.setTypeface(android.graphics.Typeface.DEFAULT_BOLD);
        panel.addView(title);

        coverage = label("Cobertura pendiente", 14, Color.WHITE);
        coverage.setPadding(0, dp(8), 0, dp(6));
        panel.addView(coverage);

        poseHint = label("Use el teléfono horizontal", 13, Color.rgb(255, 190, 100));
        panel.addView(poseHint);

        storage = label("Espacio: calculando…", 12, Color.LTGRAY);
        storage.setPadding(0, dp(4), 0, dp(8));
        panel.addView(storage);

        bandButton = button("ALTURA: EJE", 54);
        bandButton.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View view) {
                band = "LOW".equals(band) ? "HIGH" : "LOW";
                bandButton.setText("HIGH".equals(band) ? "ALTURA: ALTA" : "ALTURA: EJE");
                refresh();
            }
        });
        panel.addView(bandButton);

        captureButton = button("CAPTURAR", 76);
        captureButton.setTextSize(20);
        captureButton.setTypeface(android.graphics.Typeface.DEFAULT_BOLD);
        captureButton.setEnabled(false);
        captureButton.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View view) { capture(); }
        });
        panel.addView(captureButton);

        overlapButton = button("VERIFICAR SOLAPE", 48);
        overlapButton.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View view) { analyzeOverlap(); }
        });
        panel.addView(overlapButton);

        exportButton = button("EXPORTAR PAQUETE", 48);
        exportButton.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View view) { exportPackage(); }
        });
        panel.addView(exportButton);

        Button finish = button("FINALIZAR RECORRIDO", 48);
        finish.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View view) { finishDialog(); }
        });
        panel.addView(finish);

        TextView instruction = label(
                "Mantenga distancia constante, sin zoom digital y con solape entre vistas. Capture ambos anillos completos.",
                11, Color.LTGRAY);
        instruction.setPadding(0, dp(8), 0, 0);
        panel.addView(instruction);
        setContentView(root);
    }

    private void startCamera() {
        if (camera != null) return;
        camera = new Camera2CaptureController(this, preview, new Camera2CaptureController.Callback() {
            @Override public void onReady() {
                runOnUiThread(new Runnable() {
                    @Override public void run() {
                        cameraReady = true;
                        status.setText("Cámara lista · encuadre la polea completa");
                        status.setTextColor(Color.WHITE);
                        refresh();
                    }
                });
            }

            @Override public void onCaptured(byte[] jpeg, Camera2CaptureController.Metadata metadata) {
                processCapturedImage(jpeg, metadata);
            }

            @Override public void onError(String message) {
                cameraReady = false;
                processing = false;
                showError(message);
            }
        });
        camera.start();
    }

    private void capture() {
        if (camera == null || processing) return;
        DevicePoseTracker.Snapshot pose = poseTracker.snapshot();
        LandscapeCaptureMath.Stability stability = LandscapeCaptureMath.evaluate(
                pose.pitch, pose.roll, pose.motion,
                poseTracker.hasOrientationSensor(), poseTracker.hasGyroscope());
        if (!stability.ready()) {
            status.setText(stability.reason);
            return;
        }
        if (freeBytes() < CaptureReadiness.MIN_FREE_BYTES) {
            showError("Espacio insuficiente: libere al menos 250 MB");
            return;
        }
        pendingPose = pose;
        pendingBand = band;
        processing = true;
        refresh();
        status.setText("Capturando y evaluando calidad…");
        if (!camera.capture()) {
            processing = false;
            status.setText("La cámara todavía no está lista");
            refresh();
        }
    }

    private void processCapturedImage(byte[] jpeg, Camera2CaptureController.Metadata metadata) {
        try {
            DevicePoseTracker.Snapshot pose = pendingPose == null ? poseTracker.snapshot() : pendingPose;
            String shotBand = pendingBand == null ? band : pendingBand;
            int sequence = store.nextSequence(sessionId);
            File file = store.frameFile(sessionId, sequence);
            try (FileOutputStream output = new FileOutputStream(file)) {
                output.write(jpeg);
            }
            ImageQualityAnalyzer.Result quality = ImageQualityAnalyzer.analyze(jpeg, pose.motion);
            int sector = CoveragePlanner.sectorForYaw(pose.yaw);
            store.saveFrame(
                    sessionId, sequence, file,
                    pose.yaw, pose.pitch, pose.roll,
                    metadata.exposureNs, metadata.iso, metadata.focusDistance,
                    quality, shotBand, sector, sha256(jpeg),
                    metadata.cameraId, metadata.sensorOrientation, metadata.jpegOrientation,
                    metadata.nominalFocalLengthMm
            );
            runOnUiThread(new Runnable() {
                @Override public void run() {
                    processing = false;
                    status.setText((quality.accepted() ? "ACEPTADA · " : "RECHAZADA · ") + quality.reason
                            + String.format(Locale.ROOT, "\nNitidez %.0f · luz %.0f · %s",
                            quality.blurScore, quality.meanLuma, CoveragePlanner.sectorLabel(sector)));
                    status.setTextColor(quality.accepted()
                            ? Color.rgb(130, 235, 155)
                            : Color.rgb(255, 155, 130));
                    refresh();
                }
            });
        } catch (Exception e) {
            runOnUiThread(new Runnable() {
                @Override public void run() {
                    processing = false;
                    showError(e.getMessage());
                    refresh();
                }
            });
        } finally {
            pendingPose = null;
            pendingBand = null;
        }
    }

    private void refresh() {
        session = store.getSession(sessionId);
        if (session == null || poseTracker == null || guide == null) return;
        DevicePoseTracker.Snapshot pose = poseTracker.snapshot();
        LandscapeCaptureMath.Stability stability = LandscapeCaptureMath.evaluate(
                pose.pitch, pose.roll, pose.motion,
                poseTracker.hasOrientationSensor(), poseTracker.hasGyroscope());
        int activeMask = "HIGH".equals(band) ? session.highMask : session.lowMask;
        int currentSector = CoveragePlanner.sectorForYaw(pose.yaw);
        int next = CoveragePlanner.nextMissingSector(activeMask, currentSector);
        guide.update(activeMask, currentSector, next, band, stability.ready());

        coverage.setText(session.label + "\nAceptadas " + session.accepted + " · rechazadas " + session.rejected
                + "\nEje " + CoveragePlanner.coveredCount(session.lowMask) + "/12 · alta "
                + CoveragePlanner.coveredCount(session.highMask) + "/12"
                + "\nSiguiente: " + CoveragePlanner.sectorLabel(next));
        poseHint.setText(stability.reason + String.format(Locale.ROOT,
                " · movimiento %.2f rad/s", pose.motion));
        poseHint.setTextColor(stability.ready()
                ? Color.rgb(130, 235, 155)
                : Color.rgb(255, 190, 100));
        long free = freeBytes();
        storage.setText("Espacio libre: " + formatBytes(free));
        storage.setTextColor(free >= CaptureReadiness.MIN_FREE_BYTES ? Color.LTGRAY : Color.rgb(255, 145, 125));
        captureButton.setEnabled(cameraReady && !processing && stability.ready()
                && free >= CaptureReadiness.MIN_FREE_BYTES);
        captureButton.setText(processing ? "PROCESANDO…" : "CAPTURAR");
        overlapButton.setEnabled(!processing && session.accepted >= 2);
        exportButton.setEnabled(!processing && session.accepted + session.rejected > 0);
    }

    private void finishDialog() {
        session = store.getSession(sessionId);
        if (session == null) return;
        CaptureReadiness.Result readiness = CaptureReadiness.evaluate(
                session.accepted, session.rejected, session.lowMask, session.highMask,
                session.shellLengthMm, freeBytes());
        new AlertDialog.Builder(this)
                .setTitle(readiness.ready() ? "Recorrido listo" : "Recorrido incompleto")
                .setMessage(readiness.summary()
                        + "\n\nLa sesión se puede cerrar, pero la reconstrucción permanecerá bloqueada si existen bloqueos.")
                .setPositiveButton("FINALIZAR", (dialog, which) -> {
                    store.finishSession(sessionId);
                    finish();
                })
                .setNegativeButton("SEGUIR", null)
                .show();
    }

    private void analyzeOverlap() {
        if (processing) return;
        processing = true;
        status.setText("Analizando correspondencias y solape…");
        refresh();
        new Thread(new Runnable() {
            @Override public void run() {
                try {
                    final SessionOverlapAnalyzer.Report report = SessionOverlapAnalyzer.analyze(store, sessionId);
                    runOnUiThread(new Runnable() {
                        @Override public void run() {
                            processing = false;
                            status.setText(report.summary());
                            status.setTextColor(report.ready
                                    ? Color.rgb(130, 235, 155)
                                    : Color.rgb(255, 190, 100));
                            refresh();
                        }
                    });
                } catch (Exception e) {
                    runOnUiThread(new Runnable() {
                        @Override public void run() {
                            processing = false;
                            showError(e.getMessage());
                            refresh();
                        }
                    });
                }
            }
        }, "PoleaOverlapAnalysis").start();
    }

    private void exportPackage() {
        if (processing) return;
        processing = true;
        status.setText("Construyendo paquete auditable…");
        refresh();
        new Thread(new Runnable() {
            @Override public void run() {
                try {
                    final File packageFile = SessionPackageExporter.build(CaptureActivity.this, store, sessionId);
                    runOnUiThread(new Runnable() {
                        @Override public void run() {
                            processing = false;
                            pendingExport = packageFile;
                            Intent intent = new Intent(Intent.ACTION_CREATE_DOCUMENT);
                            intent.addCategory(Intent.CATEGORY_OPENABLE);
                            intent.setType("application/zip");
                            intent.putExtra(Intent.EXTRA_TITLE, packageFile.getName());
                            startActivityForResult(intent, EXPORT_DOCUMENT);
                            refresh();
                        }
                    });
                } catch (Exception e) {
                    runOnUiThread(new Runnable() {
                        @Override public void run() {
                            processing = false;
                            showError(e.getMessage());
                            refresh();
                        }
                    });
                }
            }
        }, "PoleaSessionExport").start();
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
            Toast.makeText(this, "Paquete exportado correctamente", Toast.LENGTH_LONG).show();
        } catch (Exception e) {
            showError(e.getMessage());
        } finally {
            pendingExport = null;
        }
    }

    @Override public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grants) {
        super.onRequestPermissionsResult(requestCode, permissions, grants);
        if (requestCode == CAMERA_PERMISSION && grants.length > 0
                && grants[0] == PackageManager.PERMISSION_GRANTED) {
            startCamera();
        } else if (requestCode == CAMERA_PERMISSION) {
            new AlertDialog.Builder(this)
                    .setTitle("Permiso de cámara requerido")
                    .setMessage("La captura industrial no puede funcionar sin acceso a la cámara.")
                    .setPositiveButton("ABRIR AJUSTES", (dialog, which) -> {
                        Intent intent = new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                                Uri.parse("package:" + getPackageName()));
                        startActivity(intent);
                    })
                    .setNegativeButton("SALIR", (dialog, which) -> finish())
                    .show();
        }
    }

    private long freeBytes() {
        try { return store.sessionDir(sessionId).getUsableSpace(); }
        catch (Exception ignored) { return 0L; }
    }

    private void showError(String message) {
        runOnUiThread(new Runnable() {
            @Override public void run() {
                String text = message == null || message.trim().isEmpty() ? "Error de captura" : message;
                status.setText(text);
                status.setTextColor(Color.rgb(255, 145, 125));
                Toast.makeText(CaptureActivity.this, text, Toast.LENGTH_LONG).show();
            }
        });
    }

    private TextView label(String value, int sp, int color) {
        TextView view = new TextView(this);
        view.setText(value);
        view.setTextSize(sp);
        view.setTextColor(color);
        return view;
    }

    private Button button(String value, int heightDp) {
        Button button = new Button(this);
        button.setText(value);
        button.setAllCaps(false);
        button.setMinHeight(dp(heightDp));
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(-1, -2);
        params.setMargins(0, dp(5), 0, 0);
        button.setLayoutParams(params);
        return button;
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }

    private static String formatBytes(long bytes) {
        double mb = bytes / (1024.0 * 1024.0);
        return mb >= 1024.0
                ? String.format(Locale.ROOT, "%.1f GB", mb / 1024.0)
                : String.format(Locale.ROOT, "%.0f MB", mb);
    }

    private static String sha256(byte[] bytes) throws Exception {
        byte[] hash = MessageDigest.getInstance("SHA-256").digest(bytes);
        StringBuilder output = new StringBuilder();
        for (byte value : hash) output.append(String.format(Locale.ROOT, "%02x", value & 0xff));
        return output.toString();
    }
}

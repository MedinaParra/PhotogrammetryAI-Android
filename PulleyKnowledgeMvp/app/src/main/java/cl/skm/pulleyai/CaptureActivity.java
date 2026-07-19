package cl.skm.pulleyai;

import android.Manifest;
import android.app.Activity;
import android.app.AlertDialog;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.os.Bundle;
import android.view.TextureView;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import java.io.File;
import java.io.FileOutputStream;
import java.security.MessageDigest;
import java.util.Locale;

/** Coordinates a real single-device capture session without claiming reconstruction results. */
public final class CaptureActivity extends Activity {
    public static final String EXTRA_SESSION_ID = "capture_session_id";
    private static final int CAMERA_PERMISSION = 4102;

    private CaptureStore store;
    private CaptureStore.Session session;
    private String sessionId;
    private TextureView preview;
    private TextView status;
    private TextView coverage;
    private Button captureButton;
    private Button bandButton;
    private String band = "LOW";
    private DevicePoseTracker poseTracker;
    private Camera2CaptureController camera;
    private DevicePoseTracker.Snapshot pendingPose;
    private String pendingBand;

    @Override protected void onCreate(Bundle state) {
        super.onCreate(state);
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
        if (checkSelfPermission(Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) startCamera();
        else requestPermissions(new String[]{Manifest.permission.CAMERA}, CAMERA_PERMISSION);
        refresh();
    }

    @Override protected void onPause() {
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
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(Color.BLACK);

        status = label("Preparando cámara…", 13, Color.WHITE);
        status.setPadding(dp(12), dp(8), dp(12), dp(8));
        root.addView(status);

        preview = new TextureView(this);
        root.addView(preview, new LinearLayout.LayoutParams(-1, 0, 1f));

        LinearLayout panel = new LinearLayout(this);
        panel.setOrientation(LinearLayout.VERTICAL);
        panel.setPadding(dp(12), dp(8), dp(12), dp(12));
        panel.setBackgroundColor(Color.rgb(25, 31, 35));
        root.addView(panel);

        coverage = label("Cobertura pendiente", 14, Color.WHITE);
        panel.addView(coverage);

        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        panel.addView(row);

        bandButton = button("ALTURA: EJE");
        bandButton.setOnClickListener(view -> {
            band = "LOW".equals(band) ? "HIGH" : "LOW";
            bandButton.setText("HIGH".equals(band) ? "ALTURA: ALTA" : "ALTURA: EJE");
            refresh();
        });
        row.addView(bandButton, new LinearLayout.LayoutParams(0, -2, 1f));

        captureButton = button("CAPTURAR");
        captureButton.setEnabled(false);
        captureButton.setOnClickListener(view -> capture());
        row.addView(captureButton, new LinearLayout.LayoutParams(0, -2, 1f));

        Button finish = button("FINALIZAR RECORRIDO");
        finish.setOnClickListener(view -> finishDialog());
        panel.addView(finish);
        setContentView(root);
    }

    private void startCamera() {
        if (camera != null) return;
        camera = new Camera2CaptureController(this, preview, new Camera2CaptureController.Callback() {
            @Override public void onReady() {
                runOnUiThread(() -> {
                    captureButton.setEnabled(true);
                    status.setText("Cámara lista · mantenga el teléfono estable");
                    status.setTextColor(Color.WHITE);
                });
            }

            @Override public void onCaptured(byte[] jpeg, Camera2CaptureController.Metadata metadata) {
                processCapturedImage(jpeg, metadata);
            }

            @Override public void onError(String message) {
                showError(message);
            }
        });
        camera.start();
    }

    private void capture() {
        if (camera == null) return;
        pendingPose = poseTracker.snapshot();
        pendingBand = band;
        captureButton.setEnabled(false);
        status.setText("Capturando y evaluando calidad…");
        if (!camera.capture()) {
            captureButton.setEnabled(true);
            status.setText("La cámara todavía no está lista");
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
                    sessionId,
                    sequence,
                    file,
                    pose.yaw,
                    pose.pitch,
                    pose.roll,
                    metadata.exposureNs,
                    metadata.iso,
                    metadata.focusDistance,
                    quality,
                    shotBand,
                    sector,
                    sha256(jpeg)
            );
            runOnUiThread(() -> {
                captureButton.setEnabled(true);
                status.setText((quality.accepted() ? "ACEPTADA · " : "RECHAZADA · ") + quality.reason
                        + String.format(Locale.ROOT, "\nNitidez %.0f · luz %.0f · %s",
                        quality.blurScore, quality.meanLuma, CoveragePlanner.sectorLabel(sector)));
                status.setTextColor(quality.accepted()
                        ? Color.rgb(130, 235, 155)
                        : Color.rgb(255, 155, 130));
                refresh();
            });
        } catch (Exception e) {
            runOnUiThread(() -> {
                captureButton.setEnabled(true);
                showError(e.getMessage());
            });
        } finally {
            pendingPose = null;
            pendingBand = null;
        }
    }

    private void refresh() {
        session = store.getSession(sessionId);
        if (session == null) return;
        DevicePoseTracker.Snapshot pose = poseTracker.snapshot();
        int activeMask = "HIGH".equals(band) ? session.highMask : session.lowMask;
        int currentSector = CoveragePlanner.sectorForYaw(pose.yaw);
        int next = CoveragePlanner.nextMissingSector(activeMask, currentSector);
        coverage.setText(session.label + " · aceptadas " + session.accepted + " · rechazadas " + session.rejected
                + "\nEje " + CoveragePlanner.coveredCount(session.lowMask) + "/12 · alta "
                + CoveragePlanner.coveredCount(session.highMask) + "/12 · siguiente "
                + CoveragePlanner.sectorLabel(next));
    }

    private void finishDialog() {
        session = store.getSession(sessionId);
        boolean enough = session != null
                && session.accepted >= 30
                && CoveragePlanner.isComplete(session.lowMask)
                && CoveragePlanner.isComplete(session.highMask);
        new AlertDialog.Builder(this)
                .setTitle("Finalizar recorrido")
                .setMessage(enough
                        ? "Cobertura mínima alcanzada: dos anillos completos y al menos 30 fotografías aceptadas."
                        : "Cobertura incompleta. Se requieren 12 sectores a altura de eje, 12 sectores altos y al menos 30 fotografías aceptadas. La reconstrucción quedará bloqueada o limitada.")
                .setPositiveButton("FINALIZAR", (dialog, which) -> {
                    store.finishSession(sessionId);
                    finish();
                })
                .setNegativeButton("SEGUIR", null)
                .show();
    }

    @Override public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grants) {
        super.onRequestPermissionsResult(requestCode, permissions, grants);
        if (requestCode == CAMERA_PERMISSION && grants.length > 0
                && grants[0] == PackageManager.PERMISSION_GRANTED) startCamera();
        else if (requestCode == CAMERA_PERMISSION) finish();
    }

    private void showError(String message) {
        runOnUiThread(() -> {
            String text = message == null ? "Error de cámara" : message;
            status.setText(text);
            status.setTextColor(Color.rgb(255, 145, 125));
            Toast.makeText(this, text, Toast.LENGTH_LONG).show();
        });
    }

    private TextView label(String value, int sp, int color) {
        TextView view = new TextView(this);
        view.setText(value);
        view.setTextSize(sp);
        view.setTextColor(color);
        return view;
    }

    private Button button(String value) {
        Button button = new Button(this);
        button.setText(value);
        button.setAllCaps(false);
        return button;
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }

    private static String sha256(byte[] bytes) throws Exception {
        byte[] hash = MessageDigest.getInstance("SHA-256").digest(bytes);
        StringBuilder output = new StringBuilder();
        for (byte value : hash) output.append(String.format(Locale.ROOT, "%02x", value & 0xff));
        return output.toString();
    }
}

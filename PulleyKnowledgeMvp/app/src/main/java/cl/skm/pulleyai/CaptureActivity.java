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
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/** Industrial landscape capture station with visual target lock and graph remediation. */
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
    private Button targetButton;
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
    private boolean autoSwitchedToHigh;
    private boolean targetLockCapture;
    private boolean pendingBridgeCapture;
    private String bridgeBand;
    private int bridgeSector = -1;
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
        if (camera != null) { camera.stop(); camera = null; }
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

        targetButton = button("BLOQUEAR POLEA OBJETIVO", 48);
        targetButton.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View view) { lockTarget(); }
        });
        panel.addView(targetButton);
        bandButton = button("ALTURA: EJE", 54);
        bandButton.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View view) {
                band = "LOW".equals(band) ? "HIGH" : "LOW";
                if ("HIGH".equals(band)) autoSwitchedToHigh = true;
                updateBandButton();
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
        overlapButton = button("COMPLETE AMBOS ANILLOS", 48);
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
                "Primero bloquee una sola polea centrada. Mantenga el mismo objetivo ocupando 60–80% del encuadre, "
                        + "excluya otros tambores y complete ambos anillos. Si el grafo falla, la app indicará una captura puente.",
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
                        status.setText("Cámara lista · bloquee la polea objetivo antes de capturar");
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
                targetLockCapture = false;
                showError(message);
            }
        });
        camera.start();
    }

    private void lockTarget() {
        if (camera == null || processing) return;
        session = store.getSession(sessionId);
        if (session == null) return;
        if (session.accepted > 0 && PulleyTargetLockStore.has(store.sessionDir(sessionId))) {
            showError("El objetivo ya está bloqueado y existen fotos aceptadas. Cree una sesión nueva para cambiar de polea.");
            return;
        }
        DevicePoseTracker.Snapshot pose = poseTracker.snapshot();
        LandscapeCaptureMath.Stability stability = LandscapeCaptureMath.evaluate(
                pose.pitch, pose.roll, pose.motion,
                poseTracker.hasOrientationSensor(), poseTracker.hasGyroscope());
        if (!stability.ready()) { status.setText(stability.reason); return; }
        processing = true;
        targetLockCapture = true;
        pendingPose = pose;
        status.setText("Analizando polea centrada, fondo, contraluz y firma visual…");
        refresh();
        if (!camera.capture()) {
            processing = false;
            targetLockCapture = false;
            status.setText("La cámara todavía no está lista");
            refresh();
        }
    }

    private void capture() {
        if (camera == null || processing) return;
        if (!PulleyTargetLockStore.has(store.sessionDir(sessionId))) {
            showError("Bloquee primero una sola polea objetivo");
            return;
        }
        DevicePoseTracker.Snapshot pose = poseTracker.snapshot();
        LandscapeCaptureMath.Stability stability = LandscapeCaptureMath.evaluate(
                pose.pitch, pose.roll, pose.motion,
                poseTracker.hasOrientationSensor(), poseTracker.hasGyroscope());
        if (!stability.ready()) { status.setText(stability.reason); return; }
        int sector = CoveragePlanner.sectorForYaw(pose.yaw);
        boolean bridgeAtSector = bridgeSector >= 0 && bridgeSector == sector && sameBand(bridgeBand, band);
        if (store.acceptedInSector(sessionId, band, sector) >= GuidedCaptureAdmissionCore.MAX_ACCEPTED_PER_CELL
                && !bridgeAtSector) {
            status.setText("SECTOR COMPLETO · avance físicamente al siguiente sector");
            status.setTextColor(Color.rgb(255, 190, 100));
            return;
        }
        if (freeBytes() < CaptureReadiness.MIN_FREE_BYTES) {
            showError("Espacio insuficiente: libere al menos 250 MB");
            return;
        }
        pendingPose = pose;
        pendingBand = band;
        pendingBridgeCapture = bridgeAtSector;
        processing = true;
        refresh();
        status.setText(bridgeAtSector
                ? "Capturando puente y verificando continuidad de la polea…"
                : "Capturando y evaluando objetivo, nitidez, obstrucción y diversidad…");
        if (!camera.capture()) {
            processing = false;
            pendingBridgeCapture = false;
            status.setText("La cámara todavía no está lista");
            refresh();
        }
    }

    private void processCapturedImage(byte[] jpeg, Camera2CaptureController.Metadata metadata) {
        try {
            DevicePoseTracker.Snapshot pose = pendingPose == null ? poseTracker.snapshot() : pendingPose;
            ImageQualityAnalyzer.Result quality = ImageQualityAnalyzer.analyze(jpeg, pose.motion);
            if (targetLockCapture) {
                PulleyTargetLockCore.Decision lockDecision = quality.accepted()
                        ? PulleyTargetLockCore.evaluate(null, quality.targetSignature)
                        : PulleyTargetLockCore.Decision.rejected(quality.reason, 0.0);
                quality.applyTargetLock(lockDecision);
                if (quality.accepted())
                    PulleyTargetLockStore.save(store.sessionDir(sessionId), quality.targetSignature, jpeg);
                runOnUiThread(new Runnable() {
                    @Override public void run() {
                        processing = false;
                        targetLockCapture = false;
                        status.setText((quality.accepted() ? "POLEA OBJETIVO BLOQUEADA" : "OBJETIVO RECHAZADO · " + quality.reason)
                                + String.format(Locale.ROOT,
                                "\nDominancia %.0f%% · ambigüedad %.0f%% · contraluz %.0f%% · detalle %.0f%%",
                                quality.targetSignature.targetDominance * 100.0,
                                quality.targetSignature.sceneAmbiguity * 100.0,
                                quality.targetSignature.backlightScore * 100.0,
                                quality.targetSignature.centerDetailRatio * 100.0));
                        status.setTextColor(quality.accepted() ? Color.rgb(130, 235, 155) : Color.rgb(255, 155, 130));
                        refresh();
                    }
                });
                return;
            }

            String shotBand = pendingBand == null ? band : pendingBand;
            int sequence = store.nextSequence(sessionId);
            File file = store.frameFile(sessionId, sequence);
            try (FileOutputStream output = new FileOutputStream(file)) { output.write(jpeg); }
            PulleyTargetLockCore.Signature reference = PulleyTargetLockStore.read(store.sessionDir(sessionId));
            quality.applyTargetLock(reference == null
                    ? PulleyTargetLockCore.Decision.rejected("Bloqueo de objetivo ausente", 0.0)
                    : PulleyTargetLockCore.evaluate(reference, quality.targetSignature));
            int sector = CoveragePlanner.sectorForYaw(pose.yaw);
            store.saveFrame(sessionId, sequence, file,
                    pose.yaw, pose.pitch, pose.roll,
                    metadata.exposureNs, metadata.iso, metadata.focusDistance,
                    quality, shotBand, sector, sha256(jpeg),
                    metadata.cameraId, metadata.sensorOrientation, metadata.jpegOrientation,
                    metadata.nominalFocalLengthMm);
            final boolean bridgeAccepted = quality.accepted() && pendingBridgeCapture;
            runOnUiThread(new Runnable() {
                @Override public void run() {
                    processing = false;
                    if (bridgeAccepted) {
                        bridgeSector = -1;
                        bridgeBand = null;
                    }
                    status.setText((quality.accepted() ? "ACEPTADA · " : "RECHAZADA · ") + quality.reason
                            + String.format(Locale.ROOT,
                            "\nNitidez %.0f · luz %.0f · continuidad %.0f%% · dominancia %.0f%% · ambigüedad %.0f%% · contraluz %.0f%% · %s",
                            quality.blurScore, quality.meanLuma,
                            Double.isFinite(quality.targetContinuity) ? quality.targetContinuity * 100.0 : 0.0,
                            quality.targetSignature.targetDominance * 100.0,
                            quality.targetSignature.sceneAmbiguity * 100.0,
                            quality.targetSignature.backlightScore * 100.0,
                            CoveragePlanner.sectorLabel(sector))
                            + (bridgeAccepted ? "\nPuente aceptado; ejecute VERIFICAR SOLAPE nuevamente" : ""));
                    status.setTextColor(quality.accepted() ? Color.rgb(130, 235, 155) : Color.rgb(255, 155, 130));
                    refresh();
                }
            });
        } catch (Exception e) {
            runOnUiThread(new Runnable() {
                @Override public void run() {
                    processing = false;
                    targetLockCapture = false;
                    showError(e.getMessage());
                    refresh();
                }
            });
        } finally {
            pendingPose = null;
            pendingBand = null;
            pendingBridgeCapture = false;
        }
    }

    private void refresh() {
        session = store.getSession(sessionId);
        if (session == null || poseTracker == null || guide == null) return;
        boolean targetLocked = PulleyTargetLockStore.has(store.sessionDir(sessionId));
        boolean lowComplete = CoveragePlanner.isComplete(session.lowMask);
        boolean highComplete = CoveragePlanner.isComplete(session.highMask);
        if (lowComplete && !highComplete && "LOW".equals(band) && !autoSwitchedToHigh) {
            band = "HIGH"; autoSwitchedToHigh = true; updateBandButton();
        }
        if (bridgeSector >= 0 && bridgeBand != null && !sameBand(bridgeBand, band)) {
            band = bridgeBand; updateBandButton();
        }
        DevicePoseTracker.Snapshot pose = poseTracker.snapshot();
        LandscapeCaptureMath.Stability stability = LandscapeCaptureMath.evaluate(
                pose.pitch, pose.roll, pose.motion,
                poseTracker.hasOrientationSensor(), poseTracker.hasGyroscope());
        int activeMask = "HIGH".equals(band) ? session.highMask : session.lowMask;
        int currentSector = CoveragePlanner.sectorForYaw(pose.yaw);
        int next = bridgeSector >= 0 ? bridgeSector : CoveragePlanner.nextMissingSector(activeMask, currentSector);
        guide.update(activeMask, currentSector, next, band, stability.ready());
        String nextText = bridgeSector >= 0
                ? "PUENTE " + CoveragePlanner.sectorLabel(bridgeSector)
                : next >= 0 ? CoveragePlanner.sectorLabel(next)
                : GuidedCaptureAdmissionCore.nextInstruction(
                session.lowMask, session.highMask, band, session.accepted, session.overlapReady);
        coverage.setText(session.label + "\nObjetivo: " + (targetLocked ? "BLOQUEADO" : "SIN BLOQUEAR")
                + "\nAceptadas " + session.accepted + " · rechazadas " + session.rejected
                + "\nEje " + CoveragePlanner.coveredCount(session.lowMask) + "/12 · alta "
                + CoveragePlanner.coveredCount(session.highMask) + "/12"
                + "\nSiguiente: " + nextText
                + "\nSolape: " + session.overlapStatus
                + (session.overlapUpdatedAt == null ? "" : " · pares " + session.overlapUsablePairs));
        poseHint.setText(stability.reason + String.format(Locale.ROOT, " · movimiento %.2f rad/s", pose.motion));
        poseHint.setTextColor(stability.ready() ? Color.rgb(130, 235, 155) : Color.rgb(255, 190, 100));
        long free = freeBytes();
        storage.setText("Espacio libre: " + formatBytes(free));
        storage.setTextColor(free >= CaptureReadiness.MIN_FREE_BYTES ? Color.LTGRAY : Color.rgb(255, 145, 125));

        targetButton.setEnabled(cameraReady && !processing && session.accepted == 0);
        targetButton.setText(targetLocked ? "OBJETIVO BLOQUEADO" : "BLOQUEAR POLEA OBJETIVO");
        int acceptedInSector = store.acceptedInSector(sessionId, band, currentSector);
        boolean bridgeAtSector = bridgeSector >= 0 && bridgeSector == currentSector && sameBand(bridgeBand, band);
        boolean sectorOpen = acceptedInSector < GuidedCaptureAdmissionCore.MAX_ACCEPTED_PER_CELL || bridgeAtSector;
        captureButton.setEnabled(targetLocked && cameraReady && !processing && stability.ready()
                && free >= CaptureReadiness.MIN_FREE_BYTES && sectorOpen
                && (bridgeSector < 0 || bridgeAtSector));
        captureButton.setText(processing ? "PROCESANDO…"
                : bridgeSector >= 0 ? (bridgeAtSector ? "CAPTURAR PUENTE" : "VAYA AL SECTOR PUENTE")
                : sectorOpen ? "CAPTURAR" : "SECTOR COMPLETO");
        boolean ringsComplete = lowComplete && highComplete;
        overlapButton.setEnabled(targetLocked && !processing && ringsComplete && session.accepted >= 24);
        overlapButton.setText(ringsComplete ? "VERIFICAR SOLAPE" : "COMPLETE AMBOS ANILLOS");
        exportButton.setEnabled(!processing && (session.accepted + session.rejected > 0 || targetLocked));
    }

    private void finishDialog() {
        session = store.getSession(sessionId);
        if (session == null) return;
        if (!PulleyTargetLockStore.has(store.sessionDir(sessionId))) {
            showError("No puede finalizar sin bloquear una polea objetivo");
            return;
        }
        CaptureReadiness.Result readiness = CaptureReadiness.evaluate(
                session.accepted, session.rejected, session.lowMask, session.highMask,
                session.shellLengthMm, freeBytes(), true, session.overlapReady, session.overlapStatus);
        AlertDialog.Builder builder = new AlertDialog.Builder(this)
                .setTitle(readiness.ready() ? "Recorrido listo" : "Recorrido incompleto")
                .setMessage(readiness.summary() + (readiness.ready()
                        ? "\n\nLa sesión puede cerrarse y continuar a reconstrucción."
                        : "\n\nNo se marcará como finalizada. Puede salir y continuar esta misma sesión después."));
        if (readiness.ready()) {
            builder.setPositiveButton("FINALIZAR", (dialog, which) -> {
                if (store.finishSession(sessionId)) finish();
                else showError("La sesión cambió y ya no cumple los requisitos de finalización");
            }).setNegativeButton("SEGUIR", null);
        } else {
            builder.setPositiveButton("SEGUIR CAPTURANDO", null)
                    .setNegativeButton("SALIR SIN FINALIZAR", (dialog, which) -> finish());
        }
        builder.show();
    }

    private void analyzeOverlap() {
        if (processing) return;
        session = store.getSession(sessionId);
        if (session == null) return;
        if (!PulleyTargetLockStore.has(store.sessionDir(sessionId))) {
            showError("Bloquee la polea objetivo antes de verificar solape");
            return;
        }
        if (!CoveragePlanner.isComplete(session.lowMask) || !CoveragePlanner.isComplete(session.highMask)) {
            showError("Complete los anillos EJE y ALTA antes de verificar el solape");
            return;
        }
        processing = true;
        status.setText("Analizando correspondencias, objetivo y grafo de solape…");
        refresh();
        new Thread(new Runnable() {
            @Override public void run() {
                try {
                    final SessionOverlapAnalyzer.Report report = SessionOverlapAnalyzer.analyze(store, sessionId);
                    final BridgeCapturePlanCore.Plan plan = report.ready ? null : buildBridgePlan(report);
                    runOnUiThread(new Runnable() {
                        @Override public void run() {
                            processing = false;
                            String message = report.summary();
                            if (plan != null && plan.required) {
                                int open = findOpenBridgeSector(plan.band, plan.sector);
                                if (open >= 0) {
                                    bridgeBand = plan.band;
                                    bridgeSector = open;
                                    band = bridgeBand;
                                    updateBandButton();
                                    message += "\n" + plan.reason + "\nCAPTURA PUENTE: "
                                            + ("HIGH".equals(bridgeBand) ? "ALTURA ALTA" : "ALTURA EJE")
                                            + " · " + CoveragePlanner.sectorLabel(bridgeSector)
                                            + "\nMantenga la polea bloqueada ocupando 60–80%, muévase 8–15° y excluya otros tambores.";
                                } else {
                                    message += "\nNo quedan celdas abiertas para un puente seguro. Cree una sesión nueva más cerca de la polea y con fondo más limpio.";
                                }
                            } else if (report.ready) {
                                bridgeBand = null;
                                bridgeSector = -1;
                            }
                            status.setText(message);
                            status.setTextColor(report.ready ? Color.rgb(130, 235, 155) : Color.rgb(255, 190, 100));
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

    private BridgeCapturePlanCore.Plan buildBridgePlan(SessionOverlapAnalyzer.Report report) {
        List<CaptureStore.Frame> frames = store.frames(sessionId);
        Map<Integer, CaptureStore.Frame> bySequence = new HashMap<Integer, CaptureStore.Frame>();
        for (CaptureStore.Frame frame : frames) if ("ACCEPTED".equals(frame.quality)) bySequence.put(frame.sequence, frame);
        Set<Integer> used = new HashSet<Integer>();
        for (SessionOverlapAnalyzer.Pair pair : report.pairs) { used.add(pair.leftSequence); used.add(pair.rightSequence); }
        List<CaptureStore.Frame> selected = new ArrayList<CaptureStore.Frame>();
        for (Integer sequence : used) if (bySequence.containsKey(sequence)) selected.add(bySequence.get(sequence));
        if (selected.isEmpty()) selected.addAll(bySequence.values());
        Collections.sort(selected, new Comparator<CaptureStore.Frame>() {
            @Override public int compare(CaptureStore.Frame a, CaptureStore.Frame b) {
                return Integer.compare(a.sequence, b.sequence);
            }
        });
        Map<Integer, Integer> index = new HashMap<Integer, Integer>();
        List<BridgeCapturePlanCore.Node> nodes = new ArrayList<BridgeCapturePlanCore.Node>();
        for (int i = 0; i < selected.size(); i++) {
            CaptureStore.Frame frame = selected.get(i);
            index.put(frame.sequence, i);
            nodes.add(new BridgeCapturePlanCore.Node(i, frame.sequence, frame.band, frame.sector));
        }
        List<BridgeCapturePlanCore.Edge> edges = new ArrayList<BridgeCapturePlanCore.Edge>();
        for (SessionOverlapAnalyzer.Pair pair : report.pairs) {
            Integer left = index.get(pair.leftSequence), right = index.get(pair.rightSequence);
            if (left == null || right == null) continue;
            edges.add(new BridgeCapturePlanCore.Edge(left, right, pair.usable(),
                    "STRONG".equals(pair.status), pair.rawMatches, pair.inliers));
        }
        return BridgeCapturePlanCore.build(nodes, edges,
                report.localGraph == null ? "UNKNOWN" : report.localGraph.status,
                report.localGraph == null ? 0 : report.localGraph.crossBandEdges);
    }

    private int findOpenBridgeSector(String requestedBand, int preferred) {
        int[] offsets = {0, 1, -1, 2, -2};
        for (int offset : offsets) {
            int sector = Math.floorMod(preferred + offset, CoveragePlanner.SECTOR_COUNT);
            if (store.acceptedInSector(sessionId, requestedBand, sector)
                    < GuidedCaptureAdmissionCore.MAX_ACCEPTED_PER_CELL) return sector;
        }
        return -1;
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
            byte[] buffer = new byte[64 * 1024]; int read;
            while ((read = input.read(buffer)) >= 0) output.write(buffer, 0, read);
            Toast.makeText(this, "Paquete exportado correctamente", Toast.LENGTH_LONG).show();
        } catch (Exception e) { showError(e.getMessage()); }
        finally { pendingExport = null; }
    }

    @Override public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grants) {
        super.onRequestPermissionsResult(requestCode, permissions, grants);
        if (requestCode == CAMERA_PERMISSION && grants.length > 0 && grants[0] == PackageManager.PERMISSION_GRANTED) startCamera();
        else if (requestCode == CAMERA_PERMISSION) {
            new AlertDialog.Builder(this)
                    .setTitle("Permiso de cámara requerido")
                    .setMessage("La captura industrial no puede funcionar sin acceso a la cámara.")
                    .setPositiveButton("ABRIR AJUSTES", (dialog, which) -> startActivity(
                            new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:" + getPackageName()))))
                    .setNegativeButton("SALIR", (dialog, which) -> finish())
                    .show();
        }
    }

    private void updateBandButton() { bandButton.setText("HIGH".equals(band) ? "ALTURA: ALTA" : "ALTURA: EJE"); }
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
        TextView view = new TextView(this); view.setText(value); view.setTextSize(sp); view.setTextColor(color); return view;
    }
    private Button button(String value, int heightDp) {
        Button button = new Button(this); button.setText(value); button.setAllCaps(false); button.setMinHeight(dp(heightDp));
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(-1, -2); params.setMargins(0, dp(5), 0, 0);
        button.setLayoutParams(params); return button;
    }
    private int dp(int value) { return Math.round(value * getResources().getDisplayMetrics().density); }
    private static String formatBytes(long bytes) {
        double mb = bytes / (1024.0 * 1024.0);
        return mb >= 1024.0 ? String.format(Locale.ROOT, "%.1f GB", mb / 1024.0)
                : String.format(Locale.ROOT, "%.0f MB", mb);
    }
    private static String sha256(byte[] bytes) throws Exception {
        byte[] hash = MessageDigest.getInstance("SHA-256").digest(bytes);
        StringBuilder output = new StringBuilder();
        for (byte value : hash) output.append(String.format(Locale.ROOT, "%02x", value & 0xff));
        return output.toString();
    }
    private static boolean sameBand(String first, String second) {
        return "HIGH".equals(first) == "HIGH".equals(second);
    }
}

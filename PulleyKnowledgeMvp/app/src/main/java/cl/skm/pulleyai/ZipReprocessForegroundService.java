package cl.skm.pulleyai;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.net.Uri;
import android.os.Binder;
import android.os.Build;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.os.PowerManager;

import java.io.File;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArraySet;

/**
 * Runs imported ZIP photogrammetry independently from the Activity lifecycle.
 * A foreground notification and partial wake lock keep CPU work alive while the
 * screen is off. Progress and output paths are persisted so a recreated UI can
 * reconnect without asking for the source ZIP again.
 */
public final class ZipReprocessForegroundService extends Service {
    public static final String ACTION_START =
            "cl.skm.pulleyai.action.START_ZIP_REPROCESS";
    public static final String ACTION_RESUME =
            "cl.skm.pulleyai.action.RESUME_ZIP_REPROCESS";
    public static final String EXTRA_SOURCE_URI = "sourceUri";

    private static final String CHANNEL_ID = "skm_zip_reprocess";
    private static final int NOTIFICATION_ID = 5410;
    private static final String PREFS = "zip_reprocess_alpha55";
    private static final long WAKE_LOCK_TIMEOUT_MS = 6L * 60L * 60L * 1000L;

    public interface Listener {
        void onSnapshot(Snapshot snapshot);
    }

    public final class LocalBinder extends Binder {
        public ZipReprocessForegroundService service() {
            return ZipReprocessForegroundService.this;
        }
    }

    public static final class Snapshot {
        public final boolean running;
        public final boolean completed;
        public final boolean failed;
        public final String phase;
        public final String message;
        public final String sourceUri;
        public final String reportPath;
        public final String packagePath;
        public final String seedReportPath;
        public final String seedSummary;
        public final int seedPointCount;
        public final long updatedAt;

        Snapshot(boolean running, boolean completed, boolean failed,
                 String phase, String message, String sourceUri,
                 String reportPath, String packagePath,
                 String seedReportPath, String seedSummary,
                 int seedPointCount, long updatedAt) {
            this.running = running;
            this.completed = completed;
            this.failed = failed;
            this.phase = clean(phase, "IDLE");
            this.message = clean(message, "Seleccione un ZIP de captura.");
            this.sourceUri = clean(sourceUri, "");
            this.reportPath = clean(reportPath, "");
            this.packagePath = clean(packagePath, "");
            this.seedReportPath = clean(seedReportPath, "");
            this.seedSummary = clean(seedSummary, "");
            this.seedPointCount = Math.max(0, seedPointCount);
            this.updatedAt = updatedAt;
        }

        static Snapshot idle() {
            return new Snapshot(false, false, false, "IDLE",
                    "Seleccione un ZIP de captura.", "", "", "", "", "", 0,
                    System.currentTimeMillis());
        }

        public boolean hasReport() {
            return isFile(reportPath);
        }

        public boolean hasPackage() {
            return isFile(packagePath);
        }

        public boolean hasSeedCloud() {
            return seedPointCount > 0 && isFile(seedReportPath);
        }

        private static boolean isFile(String path) {
            return path != null && !path.isEmpty() && new File(path).isFile();
        }

        private static String clean(String value, String fallback) {
            return value == null || value.trim().isEmpty() ? fallback : value;
        }
    }

    private final IBinder binder = new LocalBinder();
    private final Set<Listener> listeners = new CopyOnWriteArraySet<Listener>();
    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private final Object workerLock = new Object();

    private SharedPreferences preferences;
    private volatile Snapshot snapshot = Snapshot.idle();
    private volatile boolean processing;
    private Thread worker;
    private PowerManager.WakeLock wakeLock;

    @Override public void onCreate() {
        super.onCreate();
        preferences = getSharedPreferences(PREFS, MODE_PRIVATE);
        snapshot = loadSnapshot();
        createNotificationChannel();
    }

    @Override public IBinder onBind(Intent intent) {
        return binder;
    }

    @Override public int onStartCommand(Intent intent, int flags, int startId) {
        String action = intent == null ? ACTION_RESUME : intent.getAction();
        String source = intent == null ? null : intent.getStringExtra(EXTRA_SOURCE_URI);
        if (ACTION_START.equals(action) && source != null && !source.trim().isEmpty()) {
            startProcessing(Uri.parse(source));
        } else if (ACTION_RESUME.equals(action) || intent == null) {
            resumePersistedIfNeeded();
        }
        return START_REDELIVER_INTENT;
    }

    public Snapshot snapshot() {
        return snapshot;
    }

    public void registerListener(Listener listener) {
        if (listener == null) return;
        listeners.add(listener);
        final Snapshot current = snapshot;
        mainHandler.post(() -> listener.onSnapshot(current));
    }

    public void unregisterListener(Listener listener) {
        if (listener != null) listeners.remove(listener);
    }

    public boolean workerActive() {
        return processing;
    }

    public void resumePersistedIfNeeded() {
        Snapshot current = snapshot;
        if (current.running && !processing && !current.sourceUri.isEmpty()) {
            startProcessing(Uri.parse(current.sourceUri));
        }
    }

    private void startProcessing(Uri sourceUri) {
        if (sourceUri == null) return;
        synchronized (workerLock) {
            if (processing) return;
            processing = true;
            update(new Snapshot(true, false, false, "STARTING",
                    "alpha55 · preparando análisis persistente…",
                    sourceUri.toString(), "", "", "", "", 0,
                    System.currentTimeMillis()));
            startForeground(NOTIFICATION_ID, buildNotification(snapshot));
            acquireWakeLock();
            worker = new Thread(() -> runPipeline(sourceUri),
                    "Alpha55BackgroundZipReprocessor");
            worker.start();
        }
    }

    private void runPipeline(Uri sourceUri) {
        CaptureZipNormalizer.Result normalized = null;
        MultiScaleZipReprocessor.Result multiscale = null;
        ImportedTrackZipAnalyzer.Result tracks = null;
        ImportedSeedGeometryZipAnalyzer.Result seed = null;
        ImportedComponentGeometryAnalyzer.Result components = null;
        ImportedBridgeEvidenceAnalyzer.Result bridge = null;
        String latestReport = "";
        String latestPackage = "";
        String seedReport = "";
        String seedSummary = "";
        int seedPoints = 0;
        try {
            progress("PREFLIGHT", "alpha55 · iniciando preflight del ZIP…",
                    latestReport, latestPackage, seedReport, seedSummary, seedPoints);
            normalized = CaptureZipNormalizer.normalize(this, sourceUri,
                    message -> progress("PREFLIGHT", message,
                            path(normalizedResultFile()), "", "", "", 0));
            latestReport = path(normalized.preflightFile);
            progress("PREFLIGHT_COMPLETE", normalized.summary
                            + "\n\nPreflight aprobado. Iniciando matching multiescala…",
                    latestReport, latestPackage, seedReport, seedSummary, seedPoints);

            Uri canonicalUri = Uri.fromFile(normalized.normalizedZip);
            final String preflightSummary = normalized.summary;
            multiscale = MultiScaleZipReprocessor.process(this, canonicalUri,
                    message -> progress("MATCHING", preflightSummary + "\n\n" + message,
                            latestReportValue(), latestPackageValue(),
                            seedReportValue(), seedSummaryValue(), seedPointValue()));
            latestReport = path(multiscale.reportFile);
            latestPackage = path(multiscale.packageFile);
            progress("MATCHING_COMPLETE", preflightSummary + "\n\n" + multiscale.summary
                            + "\n\nMatching completo. Iniciando tracks…",
                    latestReport, latestPackage, seedReport, seedSummary, seedPoints);

            final MultiScaleZipReprocessor.Result alpha47 = multiscale;
            tracks = ImportedTrackZipAnalyzer.process(this, canonicalUri, alpha47,
                    message -> progress("TRACKS", preflightSummary + "\n\n" + message,
                            latestReportValue(), latestPackageValue(),
                            seedReportValue(), seedSummaryValue(), seedPointValue()));
            latestReport = path(tracks.trackFile);
            latestPackage = path(tracks.packageFile);
            progress("TRACKS_COMPLETE", preflightSummary + "\n\n" + alpha47.summary
                            + "\n\n" + tracks.summary
                            + "\n\nTracks completos. Buscando pose y nube semilla…",
                    latestReport, latestPackage, seedReport, seedSummary, seedPoints);

            final ImportedTrackZipAnalyzer.Result alpha48 = tracks;
            seed = ImportedSeedGeometryZipAnalyzer.process(this, canonicalUri, alpha48,
                    message -> progress("SEED_GEOMETRY", preflightSummary + "\n\n" + message,
                            latestReportValue(), latestPackageValue(),
                            seedReportValue(), seedSummaryValue(), seedPointValue()));
            latestReport = path(seed.reportFile);
            latestPackage = path(seed.packageFile);
            seedReport = latestReport;
            seedSummary = seed.summary;
            seedPoints = seed.geometry != null && seed.geometry.cloud != null
                    && seed.geometry.cloud.points != null
                    ? seed.geometry.cloud.points.size() : 0;
            progress("SEED_COMPLETE", preflightSummary + "\n\n" + alpha47.summary
                            + "\n\n" + alpha48.summary + "\n\n" + seed.summary
                            + (seedPoints > 0
                            ? "\n\nNube semilla disponible: " + seedPoints + " puntos."
                            : "\n\nLa etapa semilla no produjo coordenadas 3D válidas.")
                            + "\n\nAnalizando componentes del grafo…",
                    latestReport, latestPackage, seedReport, seedSummary, seedPoints);

            final ImportedSeedGeometryZipAnalyzer.Result alpha50 = seed;
            components = ImportedComponentGeometryAnalyzer.process(
                    this, alpha48, alpha50, normalized.usableFrames);
            latestReport = path(components.reportFile);
            latestPackage = path(components.packageFile);
            progress("COMPONENTS_COMPLETE", preflightSummary + "\n\n" + alpha47.summary
                            + "\n\n" + alpha48.summary + "\n\n" + alpha50.summary
                            + "\n\n" + components.summary
                            + "\n\nClasificando evidencia entre componentes…",
                    latestReport, latestPackage, seedReport, seedSummary, seedPoints);

            final ImportedComponentGeometryAnalyzer.Result alpha51 = components;
            bridge = ImportedBridgeEvidenceAnalyzer.process(this, alpha47, alpha51);
            latestReport = path(bridge.reportFile);
            latestPackage = path(bridge.packageFile);
            progress("BRIDGE_COMPLETE", preflightSummary + "\n\n" + alpha47.summary
                            + "\n\n" + alpha48.summary + "\n\n" + alpha50.summary
                            + "\n\n" + alpha51.summary + "\n\n" + bridge.summary
                            + "\n\nConsolidando decisión final del roadmap…",
                    latestReport, latestPackage, seedReport, seedSummary, seedPoints);

            final ImportedBridgeEvidenceAnalyzer.Result alpha52 = bridge;
            ImportedReplayCompletionAnalyzer.Result completion =
                    ImportedReplayCompletionAnalyzer.process(this, normalized, alpha47,
                            alpha48, alpha50, alpha51, alpha52);
            latestReport = path(completion.reportFile);
            latestPackage = path(completion.packageFile);
            String cloud = seedPoints > 0
                    ? "\n\nNUBE 3D DISPONIBLE: " + seedPoints
                    + " puntos semilla, sin escala métrica."
                    : "\n\nNUBE 3D NO DISPONIBLE: no existen coordenadas trianguladas válidas.";
            update(new Snapshot(false, true, false, "COMPLETED",
                    preflightSummary + "\n\n" + alpha47.summary + "\n\n" + alpha48.summary
                            + "\n\n" + alpha50.summary + "\n\n" + alpha51.summary
                            + "\n\n" + alpha52.summary + "\n\n" + completion.summary + cloud,
                    sourceUri.toString(), latestReport, latestPackage,
                    seedReport, seedSummary, seedPoints, System.currentTimeMillis()));
        } catch (Throwable error) {
            String message = error.getMessage() == null
                    ? error.getClass().getSimpleName() : error.getMessage();
            update(new Snapshot(false, false, true, "FAILED",
                    snapshot.message + "\n\nANÁLISIS INTERRUMPIDO\n" + message
                            + "\nEl ZIP permanece guardado y puede volver a ejecutarse sin seleccionarlo nuevamente.",
                    sourceUri.toString(), latestReport, latestPackage,
                    seedReport, seedSummary, seedPoints, System.currentTimeMillis()));
        } finally {
            processing = false;
            releaseWakeLock();
            stopForeground(STOP_FOREGROUND_REMOVE);
            stopSelf();
        }
    }

    /* Progress callbacks can occur inside lambdas where local mutable paths are unavailable.
       These accessors always expose the latest persisted values. */
    private File normalizedResultFile() {
        return file(snapshot.reportPath);
    }

    private String latestReportValue() { return snapshot.reportPath; }
    private String latestPackageValue() { return snapshot.packagePath; }
    private String seedReportValue() { return snapshot.seedReportPath; }
    private String seedSummaryValue() { return snapshot.seedSummary; }
    private int seedPointValue() { return snapshot.seedPointCount; }

    private void progress(String phase, String message, String reportPath,
                          String packagePath, String seedReportPath,
                          String seedSummary, int seedPoints) {
        update(new Snapshot(true, false, false, phase, message,
                snapshot.sourceUri, reportPath, packagePath,
                seedReportPath, seedSummary, seedPoints,
                System.currentTimeMillis()));
    }

    private void update(Snapshot next) {
        snapshot = next;
        persist(next);
        if (next.running) {
            NotificationManager manager =
                    (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
            if (manager != null) manager.notify(NOTIFICATION_ID, buildNotification(next));
        }
        for (Listener listener : listeners) {
            mainHandler.post(() -> listener.onSnapshot(next));
        }
    }

    private void persist(Snapshot value) {
        preferences.edit()
                .putBoolean("running", value.running)
                .putBoolean("completed", value.completed)
                .putBoolean("failed", value.failed)
                .putString("phase", value.phase)
                .putString("message", value.message)
                .putString("sourceUri", value.sourceUri)
                .putString("reportPath", value.reportPath)
                .putString("packagePath", value.packagePath)
                .putString("seedReportPath", value.seedReportPath)
                .putString("seedSummary", value.seedSummary)
                .putInt("seedPointCount", value.seedPointCount)
                .putLong("updatedAt", value.updatedAt)
                .apply();
    }

    private Snapshot loadSnapshot() {
        return new Snapshot(
                preferences.getBoolean("running", false),
                preferences.getBoolean("completed", false),
                preferences.getBoolean("failed", false),
                preferences.getString("phase", "IDLE"),
                preferences.getString("message", "Seleccione un ZIP de captura."),
                preferences.getString("sourceUri", ""),
                preferences.getString("reportPath", ""),
                preferences.getString("packagePath", ""),
                preferences.getString("seedReportPath", ""),
                preferences.getString("seedSummary", ""),
                preferences.getInt("seedPointCount", 0),
                preferences.getLong("updatedAt", System.currentTimeMillis()));
    }

    private void createNotificationChannel() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return;
        NotificationChannel channel = new NotificationChannel(CHANNEL_ID,
                "Análisis fotogramétrico", NotificationManager.IMPORTANCE_LOW);
        channel.setDescription("Mantiene activo el reprocesamiento del ZIP con la pantalla apagada.");
        channel.setShowBadge(false);
        NotificationManager manager =
                (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
        if (manager != null) manager.createNotificationChannel(channel);
    }

    private Notification buildNotification(Snapshot current) {
        Intent open = new Intent(this, ZipReprocessActivity.class)
                .addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP | Intent.FLAG_ACTIVITY_CLEAR_TOP);
        PendingIntent pending = PendingIntent.getActivity(this, 0, open,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        Notification.Builder builder = Build.VERSION.SDK_INT >= Build.VERSION_CODES.O
                ? new Notification.Builder(this, CHANNEL_ID)
                : new Notification.Builder(this);
        return builder.setSmallIcon(android.R.drawable.stat_sys_download)
                .setContentTitle(current.running
                        ? "SKM Polea AI · análisis en curso"
                        : "SKM Polea AI · análisis finalizado")
                .setContentText(shortText(current.message))
                .setStyle(new Notification.BigTextStyle().bigText(shortText(current.message)))
                .setContentIntent(pending)
                .setOngoing(current.running)
                .setOnlyAlertOnce(true)
                .setCategory(Notification.CATEGORY_PROGRESS)
                .setProgress(0, 0, current.running)
                .build();
    }

    private void acquireWakeLock() {
        PowerManager manager = (PowerManager) getSystemService(Context.POWER_SERVICE);
        if (manager == null) return;
        wakeLock = manager.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK,
                getPackageName() + ":zip-reprocess");
        wakeLock.setReferenceCounted(false);
        wakeLock.acquire(WAKE_LOCK_TIMEOUT_MS);
    }

    private void releaseWakeLock() {
        if (wakeLock != null && wakeLock.isHeld()) wakeLock.release();
        wakeLock = null;
    }

    @Override public void onDestroy() {
        if (worker != null && worker.isAlive() && !processing) worker.interrupt();
        releaseWakeLock();
        super.onDestroy();
    }

    private static String path(File file) {
        return file == null ? "" : file.getAbsolutePath();
    }

    private static File file(String path) {
        return path == null || path.isEmpty() ? null : new File(path);
    }

    private static String shortText(String value) {
        if (value == null) return "Procesando…";
        String clean = value.replace('\n', ' ').trim();
        return clean.length() <= 120 ? clean : clean.substring(0, 117) + "…";
    }
}
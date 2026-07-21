package cl.skm.pulleyai;

import android.app.ActivityManager;
import android.app.ApplicationExitInfo;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.BatteryManager;
import android.os.Build;
import android.os.Debug;
import android.os.PowerManager;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.util.List;

/** Collects diagnostics available without privileged permissions; battery temperature is not CPU metrology. */
public final class AndroidDeviceDiagnosticsCollector {
    private AndroidDeviceDiagnosticsCollector() {}

    public static DeviceDiagnosticsCore.Result collect(Context context) {
        if (context == null) return DeviceDiagnosticsCore.evaluate(null);
        Context app = context.getApplicationContext();
        double batteryTemperatureC = batteryTemperature(app);
        int thermalStatus = thermalStatus(app);
        int pssMb = (int) Math.min(Integer.MAX_VALUE, Math.max(0L, Debug.getPss() / 1024L));
        Runtime runtime = Runtime.getRuntime();
        int heapMb = toMb(Math.max(0L, runtime.totalMemory() - runtime.freeMemory()));
        int availableMemoryMb = availableMemoryMb(app);
        ExitHistory exits = nativeExitHistory(app);
        int localNativeEvents = countLocalNativeEvents(app);
        DeviceDiagnosticsCore.Record record = new DeviceDiagnosticsCore.Record(
                System.currentTimeMillis(), Build.MANUFACTURER + " " + Build.MODEL,
                Build.VERSION.SDK_INT, batteryTemperatureC, thermalStatus,
                pssMb, heapMb, availableMemoryMb, exits.available,
                exits.nativeCrashCount, localNativeEvents);
        return DeviceDiagnosticsCore.evaluate(record);
    }

    private static double batteryTemperature(Context context) {
        try {
            Intent battery = context.registerReceiver(null,
                    new IntentFilter(Intent.ACTION_BATTERY_CHANGED));
            if (battery == null) return Double.NaN;
            int raw = battery.getIntExtra(BatteryManager.EXTRA_TEMPERATURE, Integer.MIN_VALUE);
            return raw == Integer.MIN_VALUE ? Double.NaN : raw / 10.0;
        } catch (RuntimeException error) {
            return Double.NaN;
        }
    }

    private static int thermalStatus(Context context) {
        if (Build.VERSION.SDK_INT < 29) return -1;
        try {
            PowerManager manager = (PowerManager) context.getSystemService(Context.POWER_SERVICE);
            return manager == null ? -1 : manager.getCurrentThermalStatus();
        } catch (RuntimeException error) {
            return -1;
        }
    }

    private static int availableMemoryMb(Context context) {
        try {
            ActivityManager manager = (ActivityManager) context.getSystemService(Context.ACTIVITY_SERVICE);
            if (manager == null) return 0;
            ActivityManager.MemoryInfo info = new ActivityManager.MemoryInfo();
            manager.getMemoryInfo(info);
            return toMb(info.availMem);
        } catch (RuntimeException error) {
            return 0;
        }
    }

    private static ExitHistory nativeExitHistory(Context context) {
        if (Build.VERSION.SDK_INT < 30) return new ExitHistory(false, 0);
        try {
            ActivityManager manager = (ActivityManager) context.getSystemService(Context.ACTIVITY_SERVICE);
            if (manager == null) return new ExitHistory(false, 0);
            List<ApplicationExitInfo> history = manager.getHistoricalProcessExitReasons(null, 0, 32);
            int nativeCrashes = 0;
            for (ApplicationExitInfo info : history) {
                if (info.getReason() == ApplicationExitInfo.REASON_CRASH_NATIVE) nativeCrashes++;
            }
            return new ExitHistory(true, nativeCrashes);
        } catch (RuntimeException error) {
            return new ExitHistory(false, 0);
        }
    }

    private static int countLocalNativeEvents(Context context) {
        File source = new File(context.getFilesDir(), "native-events.log");
        if (!source.isFile()) return 0;
        int count = 0;
        try (BufferedReader reader = new BufferedReader(new FileReader(source))) {
            while (reader.readLine() != null) count++;
        } catch (Exception ignored) {
            return 0;
        }
        return count;
    }

    private static int toMb(long bytes) {
        return (int) Math.min(Integer.MAX_VALUE, Math.max(0L, bytes / (1024L * 1024L)));
    }

    private static final class ExitHistory {
        final boolean available;
        final int nativeCrashCount;
        ExitHistory(boolean available, int nativeCrashCount) {
            this.available = available;
            this.nativeCrashCount = nativeCrashCount;
        }
    }
}
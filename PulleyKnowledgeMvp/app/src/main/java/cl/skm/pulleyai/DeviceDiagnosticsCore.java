package cl.skm.pulleyai;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** Pure fail-closed representation of automatically collected Android diagnostics. */
public final class DeviceDiagnosticsCore {
    private DeviceDiagnosticsCore() {}

    public enum State { COMPLETE, PARTIAL, UNAVAILABLE }

    public static Result evaluate(Record record) {
        if (record == null) return new Result(State.UNAVAILABLE,
                Collections.singletonList("diagnóstico ausente"), null);
        List<String> gaps = new ArrayList<String>();
        if (!Double.isFinite(record.batteryTemperatureC)) gaps.add("temperatura de batería no disponible");
        if (record.thermalStatus < 0) gaps.add("estado térmico del sistema no disponible");
        if (record.pssMb <= 0) gaps.add("PSS no disponible");
        if (record.availableMemoryMb <= 0) gaps.add("memoria disponible no medida");
        if (!record.exitHistoryAvailable) gaps.add("historial de salidas nativas no disponible");
        State state = gaps.isEmpty() ? State.COMPLETE : State.PARTIAL;
        return new Result(state, gaps, record);
    }

    public static final class Record {
        public final long collectedAtEpochMs;
        public final String device;
        public final int sdkInt;
        public final double batteryTemperatureC;
        public final int thermalStatus;
        public final int pssMb;
        public final int heapMb;
        public final int availableMemoryMb;
        public final boolean exitHistoryAvailable;
        public final int nativeCrashExitCount;
        public final int localNativeEventCount;

        public Record(long collectedAtEpochMs, String device, int sdkInt,
                      double batteryTemperatureC, int thermalStatus,
                      int pssMb, int heapMb, int availableMemoryMb,
                      boolean exitHistoryAvailable, int nativeCrashExitCount,
                      int localNativeEventCount) {
            this.collectedAtEpochMs = Math.max(0L, collectedAtEpochMs);
            this.device = device == null ? "" : device.trim();
            this.sdkInt = Math.max(0, sdkInt);
            this.batteryTemperatureC = batteryTemperatureC;
            this.thermalStatus = thermalStatus;
            this.pssMb = Math.max(0, pssMb);
            this.heapMb = Math.max(0, heapMb);
            this.availableMemoryMb = Math.max(0, availableMemoryMb);
            this.exitHistoryAvailable = exitHistoryAvailable;
            this.nativeCrashExitCount = Math.max(0, nativeCrashExitCount);
            this.localNativeEventCount = Math.max(0, localNativeEventCount);
        }

        public int totalNativeEvents() {
            return nativeCrashExitCount + localNativeEventCount;
        }
    }

    public static final class Result {
        public final State state;
        public final List<String> gaps;
        public final Record record;

        Result(State state, List<String> gaps, Record record) {
            this.state = state;
            this.gaps = Collections.unmodifiableList(new ArrayList<String>(gaps));
            this.record = record;
        }

        public boolean automatic() { return record != null; }
        public boolean complete() { return state == State.COMPLETE; }

        public String summary() {
            if (record == null) return "Diagnóstico UNAVAILABLE";
            return "Diagnóstico " + state + " · batería " + number(record.batteryTemperatureC)
                    + " °C · térmico " + thermalName(record.thermalStatus)
                    + " · PSS " + record.pssMb + " MB · RAM libre " + record.availableMemoryMb
                    + " MB · eventos nativos " + record.totalNativeEvents();
        }

        public String canonicalJson() {
            if (record == null) return "{\"schema\":\"skm-device-diagnostics/1\",\"state\":\"UNAVAILABLE\"}";
            StringBuilder json = new StringBuilder(700);
            json.append("{\n\"schema\":\"skm-device-diagnostics/1\"")
                    .append(",\n\"state\":\"").append(state).append("\"")
                    .append(",\n\"collectedAtEpochMs\":").append(record.collectedAtEpochMs)
                    .append(",\n\"device\":\"").append(escape(record.device)).append("\"")
                    .append(",\n\"sdkInt\":").append(record.sdkInt)
                    .append(",\n\"batteryTemperatureC\":").append(numberJson(record.batteryTemperatureC))
                    .append(",\n\"thermalStatus\":").append(record.thermalStatus)
                    .append(",\n\"thermalStatusName\":\"").append(thermalName(record.thermalStatus)).append("\"")
                    .append(",\n\"pssMb\":").append(record.pssMb)
                    .append(",\n\"heapMb\":").append(record.heapMb)
                    .append(",\n\"availableMemoryMb\":").append(record.availableMemoryMb)
                    .append(",\n\"exitHistoryAvailable\":").append(record.exitHistoryAvailable)
                    .append(",\n\"nativeCrashExitCount\":").append(record.nativeCrashExitCount)
                    .append(",\n\"localNativeEventCount\":").append(record.localNativeEventCount)
                    .append(",\n\"gaps\":[");
            for (int i = 0; i < gaps.size(); i++) {
                if (i > 0) json.append(',');
                json.append('\"').append(escape(gaps.get(i))).append('\"');
            }
            return json.append("]\n}").toString();
        }
    }

    public static String thermalName(int value) {
        switch (value) {
            case 0: return "NONE";
            case 1: return "LIGHT";
            case 2: return "MODERATE";
            case 3: return "SEVERE";
            case 4: return "CRITICAL";
            case 5: return "EMERGENCY";
            case 6: return "SHUTDOWN";
            default: return "UNKNOWN";
        }
    }

    private static String number(double value) {
        return Double.isFinite(value) ? String.format(java.util.Locale.ROOT, "%.1f", value) : "N/D";
    }
    private static String numberJson(double value) {
        return Double.isFinite(value) ? Double.toString(value) : "null";
    }
    private static String escape(String value) {
        return value == null ? "" : value.replace("\\", "\\\\").replace("\"", "\\\"");
    }
}
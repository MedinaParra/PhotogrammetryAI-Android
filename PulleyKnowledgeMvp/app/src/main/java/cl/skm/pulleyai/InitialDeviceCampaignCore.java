package cl.skm.pulleyai;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

/** Fail-closed qualification for the first physical device campaign. */
public final class InitialDeviceCampaignCore {
    private InitialDeviceCampaignCore() {}

    public enum State { READY, REVIEW, BLOCKED }

    public static Result evaluate(Record record) {
        if (record == null) throw new IllegalArgumentException("record is required");
        List<String> blockers = new ArrayList<String>();
        List<String> warnings = new ArrayList<String>();

        if (record.device == null || record.device.trim().isEmpty()) blockers.add("dispositivo no identificado");
        if (!record.apkHashVerified) blockers.add("hash de APK no verificado");
        if (record.captureMinutes < 20) blockers.add("campaña menor a 20 minutos");
        else if (record.captureMinutes < 30) warnings.add("campaña menor a 30 minutos");
        if (!Double.isFinite(record.peakTemperatureC)) blockers.add("temperatura no medida");
        else if (record.peakTemperatureC > 47.0) blockers.add("temperatura máxima > 47 °C");
        else if (record.peakTemperatureC > 43.0) warnings.add("temperatura máxima > 43 °C");
        if (record.peakRssMb <= 0) blockers.add("memoria no medida");
        else if (record.peakRssMb > 1800) blockers.add("RSS máxima > 1800 MB");
        else if (record.peakRssMb > 1200) warnings.add("RSS máxima > 1200 MB");
        if (record.jniCrashes > 0) blockers.add("fallos JNI o salidas nativas detectados");
        if (record.stepImports < 3) blockers.add("menos de 3 STEP importados");
        else if (record.stepImports < 5) warnings.add("corpus STEP inicial menor a 5");
        if (record.reconstructions < 2) blockers.add("menos de 2 reconstrucciones completas");
        else if (record.reconstructions < 3) warnings.add("menos de 3 reconstrucciones completas");
        if (!record.recoveryPassed) blockers.add("recuperación tras interrupción no aprobada");

        if (!record.diagnosticsAutomatic) blockers.add("diagnóstico automático no adjunto");
        else if ("UNAVAILABLE".equals(record.diagnosticsState)) blockers.add("diagnóstico automático no disponible");
        else if ("PARTIAL".equals(record.diagnosticsState)) warnings.add("diagnóstico automático parcial");
        if (record.thermalStatus >= 4) blockers.add("estado térmico crítico o superior");
        else if (record.thermalStatus == 3) warnings.add("estado térmico severo");

        State state = !blockers.isEmpty() ? State.BLOCKED
                : !warnings.isEmpty() ? State.REVIEW : State.READY;
        return new Result(state, blockers, warnings);
    }

    public static final class Record {
        public final String device;
        public final boolean apkHashVerified;
        public final int captureMinutes;
        public final double peakTemperatureC;
        public final int peakRssMb;
        public final int jniCrashes;
        public final int stepImports;
        public final int reconstructions;
        public final boolean recoveryPassed;
        public final boolean diagnosticsAutomatic;
        public final String diagnosticsState;
        public final int thermalStatus;

        /** Legacy/synthetic constructor retained for deterministic gates. */
        public Record(String device, boolean apkHashVerified, int captureMinutes,
                      double peakTemperatureC, int peakRssMb, int jniCrashes,
                      int stepImports, int reconstructions, boolean recoveryPassed) {
            this(device, apkHashVerified, captureMinutes, peakTemperatureC, peakRssMb,
                    jniCrashes, stepImports, reconstructions, recoveryPassed,
                    true, "COMPLETE", 0);
        }

        public Record(String device, boolean apkHashVerified, int captureMinutes,
                      double peakTemperatureC, int peakRssMb, int jniCrashes,
                      int stepImports, int reconstructions, boolean recoveryPassed,
                      boolean diagnosticsAutomatic, String diagnosticsState,
                      int thermalStatus) {
            this.device = device;
            this.apkHashVerified = apkHashVerified;
            this.captureMinutes = Math.max(0, captureMinutes);
            this.peakTemperatureC = peakTemperatureC;
            this.peakRssMb = Math.max(0, peakRssMb);
            this.jniCrashes = Math.max(0, jniCrashes);
            this.stepImports = Math.max(0, stepImports);
            this.reconstructions = Math.max(0, reconstructions);
            this.recoveryPassed = recoveryPassed;
            this.diagnosticsAutomatic = diagnosticsAutomatic;
            this.diagnosticsState = diagnosticsState == null ? "UNAVAILABLE" : diagnosticsState;
            this.thermalStatus = thermalStatus;
        }
    }

    public static final class Result {
        public final State state;
        public final List<String> blockers;
        public final List<String> warnings;

        Result(State state, List<String> blockers, List<String> warnings) {
            this.state = state;
            this.blockers = Collections.unmodifiableList(new ArrayList<String>(blockers));
            this.warnings = Collections.unmodifiableList(new ArrayList<String>(warnings));
        }

        public String summary() {
            StringBuilder out = new StringBuilder();
            out.append("Campaña: ").append(state);
            for (String value : blockers) out.append("\nBLOQUEO: ").append(value);
            for (String value : warnings) out.append("\nREVISAR: ").append(value);
            if (blockers.isEmpty() && warnings.isEmpty()) {
                out.append("\nCampaña inicial aprobada para piloto técnico; no implica metrología industrial.");
            }
            return out.toString();
        }

        public String canonicalJson(Record record) {
            return String.format(Locale.ROOT,
                    "{\"device\":\"%s\",\"apkHashVerified\":%s,\"captureMinutes\":%d,"+
                            "\"peakTemperatureC\":%.2f,\"peakRssMb\":%d,\"jniCrashes\":%d,"+
                            "\"stepImports\":%d,\"reconstructions\":%d,\"recoveryPassed\":%s,"+
                            "\"diagnosticsAutomatic\":%s,\"diagnosticsState\":\"%s\","+
                            "\"thermalStatus\":%d,\"state\":\"%s\"}",
                    escape(record.device), record.apkHashVerified, record.captureMinutes,
                    record.peakTemperatureC, record.peakRssMb, record.jniCrashes,
                    record.stepImports, record.reconstructions, record.recoveryPassed,
                    record.diagnosticsAutomatic, escape(record.diagnosticsState),
                    record.thermalStatus, state);
        }
    }

    private static String escape(String value) {
        return value == null ? "" : value.replace("\\", "\\\\").replace("\"", "\\\"");
    }
}
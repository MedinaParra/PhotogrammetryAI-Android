package cl.skm.pulleyai;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** Fail-closed qualification of a physical Android device campaign. */
public final class DeviceQualificationCore {
    private DeviceQualificationCore() {}

    public enum State { PASS, REVIEW, BLOCKED, NOT_EXECUTED }

    public static Result evaluate(Campaign campaign) {
        if (campaign == null || campaign.runs.isEmpty()) {
            return new Result(State.NOT_EXECUTED, "NO_PHYSICAL_RUNS",
                    Collections.singletonList("campaña física no ejecutada"),
                    Collections.<String>emptyList(), 0, 0.0, 0.0);
        }
        List<String> blockers = new ArrayList<String>();
        List<String> warnings = new ArrayList<String>();
        int completed = 0, stepAttempts = 0, stepSuccesses = 0;
        int reconstructionAttempts = 0, reconstructionSuccesses = 0;
        double maxTemperature = Double.NEGATIVE_INFINITY;
        for (Run run : campaign.runs) {
            if (run == null || !run.valid()) {
                blockers.add("ejecución inválida");
                continue;
            }
            if (run.installSucceeded && run.launchSucceeded && run.captureCompleted) completed++;
            if (!run.artifactHashVerified) blockers.add("artefacto sin hash verificado");
            if (!run.installSucceeded || !run.launchSucceeded) blockers.add("instalación o apertura fallida");
            if (run.jniCrashCount > 0) blockers.add("fallo JNI detectado");
            if (!run.interruptionRecoverySucceeded) warnings.add("recuperación tras interrupción no aprobada");
            if (run.runtimeMinutes < 20) warnings.add("ejecución menor a 20 minutos");
            if (run.peakMemoryMb > campaign.memoryBudgetMb) blockers.add("memoria sobre presupuesto");
            if (run.maxTemperatureC >= 48.0) blockers.add("temperatura >= 48 °C");
            else if (run.maxTemperatureC >= 43.0) warnings.add("temperatura elevada");
            maxTemperature = Math.max(maxTemperature, run.maxTemperatureC);
            stepAttempts += run.stepAttempts; stepSuccesses += run.stepSuccesses;
            reconstructionAttempts += run.reconstructionAttempts;
            reconstructionSuccesses += run.reconstructionSuccesses;
        }
        if (campaign.runs.size() < 3) warnings.add("menos de tres ejecuciones");
        if (completed < Math.min(3, campaign.runs.size())) blockers.add("recorridos completos insuficientes");
        double stepRatio = ratio(stepSuccesses, stepAttempts);
        double reconstructionRatio = ratio(reconstructionSuccesses, reconstructionAttempts);
        if (stepAttempts < 5) warnings.add("corpus STEP insuficiente");
        else if (stepRatio < 0.80) blockers.add("éxito STEP inferior a 80 %");
        else if (stepRatio < 0.95) warnings.add("éxito STEP inferior a 95 %");
        if (reconstructionAttempts < 3) warnings.add("reconstrucciones insuficientes");
        else if (reconstructionRatio < 0.67) blockers.add("éxito de reconstrucción inferior a 67 %");
        else if (reconstructionRatio < 0.90) warnings.add("éxito de reconstrucción inferior a 90 %");
        State state = !blockers.isEmpty() ? State.BLOCKED
                : !warnings.isEmpty() ? State.REVIEW : State.PASS;
        return new Result(state, state.name(), blockers, warnings,
                campaign.runs.size(), stepRatio, reconstructionRatio,
                maxTemperature == Double.NEGATIVE_INFINITY ? Double.NaN : maxTemperature);
    }

    private static double ratio(int success, int attempts) {
        return attempts <= 0 ? Double.NaN : success / (double) attempts;
    }

    public static final class Campaign {
        public final String deviceId;
        public final double memoryBudgetMb;
        public final List<Run> runs;
        public Campaign(String deviceId, double memoryBudgetMb, List<Run> runs) {
            this.deviceId = deviceId == null ? "" : deviceId;
            this.memoryBudgetMb = memoryBudgetMb;
            this.runs = Collections.unmodifiableList(new ArrayList<Run>(runs == null
                    ? Collections.<Run>emptyList() : runs));
        }
    }

    public static final class Run {
        public final boolean installSucceeded, launchSucceeded, captureCompleted;
        public final boolean interruptionRecoverySucceeded, artifactHashVerified;
        public final double runtimeMinutes, maxTemperatureC, peakMemoryMb;
        public final int jniCrashCount, stepAttempts, stepSuccesses;
        public final int reconstructionAttempts, reconstructionSuccesses;

        public Run(boolean installSucceeded, boolean launchSucceeded, boolean captureCompleted,
                   boolean interruptionRecoverySucceeded, boolean artifactHashVerified,
                   double runtimeMinutes, double maxTemperatureC, double peakMemoryMb,
                   int jniCrashCount, int stepAttempts, int stepSuccesses,
                   int reconstructionAttempts, int reconstructionSuccesses) {
            this.installSucceeded = installSucceeded; this.launchSucceeded = launchSucceeded;
            this.captureCompleted = captureCompleted;
            this.interruptionRecoverySucceeded = interruptionRecoverySucceeded;
            this.artifactHashVerified = artifactHashVerified;
            this.runtimeMinutes = runtimeMinutes; this.maxTemperatureC = maxTemperatureC;
            this.peakMemoryMb = peakMemoryMb; this.jniCrashCount = jniCrashCount;
            this.stepAttempts = stepAttempts; this.stepSuccesses = stepSuccesses;
            this.reconstructionAttempts = reconstructionAttempts;
            this.reconstructionSuccesses = reconstructionSuccesses;
        }

        boolean valid() {
            return Double.isFinite(runtimeMinutes) && runtimeMinutes >= 0
                    && Double.isFinite(maxTemperatureC) && maxTemperatureC >= 0
                    && Double.isFinite(peakMemoryMb) && peakMemoryMb >= 0
                    && jniCrashCount >= 0 && stepAttempts >= 0 && stepSuccesses >= 0
                    && stepSuccesses <= stepAttempts && reconstructionAttempts >= 0
                    && reconstructionSuccesses >= 0
                    && reconstructionSuccesses <= reconstructionAttempts;
        }
    }

    public static final class Result {
        public final State state;
        public final String status;
        public final List<String> blockers, warnings;
        public final int runs;
        public final double stepSuccessRatio, reconstructionSuccessRatio, maxTemperatureC;

        Result(State state, String status, List<String> blockers, List<String> warnings,
               int runs, double stepSuccessRatio, double reconstructionSuccessRatio) {
            this(state, status, blockers, warnings, runs, stepSuccessRatio,
                    reconstructionSuccessRatio, Double.NaN);
        }

        Result(State state, String status, List<String> blockers, List<String> warnings,
               int runs, double stepSuccessRatio, double reconstructionSuccessRatio,
               double maxTemperatureC) {
            this.state = state; this.status = status;
            this.blockers = Collections.unmodifiableList(new ArrayList<String>(blockers));
            this.warnings = Collections.unmodifiableList(new ArrayList<String>(warnings));
            this.runs = runs; this.stepSuccessRatio = stepSuccessRatio;
            this.reconstructionSuccessRatio = reconstructionSuccessRatio;
            this.maxTemperatureC = maxTemperatureC;
        }
    }
}

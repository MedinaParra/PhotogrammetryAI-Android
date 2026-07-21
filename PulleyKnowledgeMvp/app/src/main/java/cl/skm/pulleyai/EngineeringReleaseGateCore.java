package cl.skm.pulleyai;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** Professional release gate separating alpha, field-pilot and industrial evidence. */
public final class EngineeringReleaseGateCore {
    private EngineeringReleaseGateCore() {}

    public enum State { ALPHA_BLOCKED, ALPHA_READY, FIELD_PILOT_REVIEW, INDUSTRIAL_QUALIFIED }

    public static Result evaluate(Input input) {
        if (input == null) return Result.blocked("NO_QUALIFICATION_INPUT");
        List<String> blockers = new ArrayList<String>();
        List<String> warnings = new ArrayList<String>();
        if (!input.ciPassed) blockers.add("CI no aprobado");
        if (!input.historyPassed) blockers.add("historial de iteraciones no aprobado");
        if (!input.dataGovernancePassed) blockers.add("gobierno de evidencia no aprobado");
        boolean alphaReady = blockers.isEmpty();
        if (!alphaReady) return new Result(State.ALPHA_BLOCKED, false, false, false,
                blockers, warnings, "ALPHA_BLOCKED");

        int passedDevices = 0;
        boolean anyDeviceBlocked = false;
        for (DeviceQualificationCore.Result device : input.devices) {
            if (device == null || device.state == DeviceQualificationCore.State.NOT_EXECUTED) continue;
            if (device.state == DeviceQualificationCore.State.PASS) passedDevices++;
            if (device.state == DeviceQualificationCore.State.BLOCKED) anyDeviceBlocked = true;
            if (device.state == DeviceQualificationCore.State.REVIEW) warnings.add("campaña de dispositivo en revisión");
        }
        if (anyDeviceBlocked) blockers.add("campaña de dispositivo bloqueada");
        if (passedDevices < input.requiredDevices) warnings.add("dispositivos aprobados insuficientes");
        if (input.stepCorpusAttempts < 20) warnings.add("corpus STEP inferior a 20 archivos");
        else if (input.stepCorpusSuccessRatio < 0.90) blockers.add("éxito del corpus STEP inferior a 90 %");
        else if (input.stepCorpusSuccessRatio < 0.98) warnings.add("éxito del corpus STEP inferior a 98 %");

        boolean fieldPilotAllowed = blockers.isEmpty() && passedDevices >= input.requiredDevices
                && input.stepCorpusAttempts >= 20 && input.stepCorpusSuccessRatio >= 0.90;
        if (!fieldPilotAllowed) {
            return new Result(State.ALPHA_READY, true, false, false,
                    blockers, warnings, "ALPHA_READY_ONLY");
        }

        MetrologyEvidence m = input.metrology;
        boolean metrologyReady = m != null && m.traceableInstruments
                && m.specimens >= 5 && m.repeatMeasurementsPerSpecimen >= 5
                && Double.isFinite(m.radiusRmseMm) && m.radiusRmseMm <= 10.0
                && Double.isFinite(m.maxRadiusErrorMm) && m.maxRadiusErrorMm <= 30.0
                && Double.isFinite(m.lengthRmseMm) && m.lengthRmseMm <= 8.0;
        if (!metrologyReady) {
            warnings.add("metrología trazable no aprobada");
            return new Result(State.FIELD_PILOT_REVIEW, true, true, false,
                    blockers, warnings, "INDUSTRIAL_BLOCKED_BY_METROLOGY");
        }
        if (!input.formalEngineeringApproval) {
            warnings.add("aprobación formal de ingeniería pendiente");
            return new Result(State.FIELD_PILOT_REVIEW, true, true, false,
                    blockers, warnings, "FORMAL_APPROVAL_PENDING");
        }
        return new Result(State.INDUSTRIAL_QUALIFIED, true, true, true,
                blockers, warnings, "INDUSTRIAL_EVIDENCE_COMPLETE");
    }

    public static final class Input {
        public final boolean ciPassed, historyPassed, dataGovernancePassed;
        public final List<DeviceQualificationCore.Result> devices;
        public final int requiredDevices, stepCorpusAttempts;
        public final double stepCorpusSuccessRatio;
        public final MetrologyEvidence metrology;
        public final boolean formalEngineeringApproval;

        public Input(boolean ciPassed, boolean historyPassed, boolean dataGovernancePassed,
                     List<DeviceQualificationCore.Result> devices, int requiredDevices,
                     int stepCorpusAttempts, double stepCorpusSuccessRatio,
                     MetrologyEvidence metrology, boolean formalEngineeringApproval) {
            this.ciPassed = ciPassed; this.historyPassed = historyPassed;
            this.dataGovernancePassed = dataGovernancePassed;
            this.devices = Collections.unmodifiableList(new ArrayList<DeviceQualificationCore.Result>(
                    devices == null ? Collections.<DeviceQualificationCore.Result>emptyList() : devices));
            this.requiredDevices = Math.max(1, requiredDevices);
            this.stepCorpusAttempts = Math.max(0, stepCorpusAttempts);
            this.stepCorpusSuccessRatio = stepCorpusSuccessRatio;
            this.metrology = metrology;
            this.formalEngineeringApproval = formalEngineeringApproval;
        }
    }

    public static final class MetrologyEvidence {
        public final boolean traceableInstruments;
        public final int specimens, repeatMeasurementsPerSpecimen;
        public final double radiusRmseMm, maxRadiusErrorMm, lengthRmseMm;
        public MetrologyEvidence(boolean traceableInstruments, int specimens,
                                 int repeatMeasurementsPerSpecimen, double radiusRmseMm,
                                 double maxRadiusErrorMm, double lengthRmseMm) {
            this.traceableInstruments = traceableInstruments;
            this.specimens = specimens;
            this.repeatMeasurementsPerSpecimen = repeatMeasurementsPerSpecimen;
            this.radiusRmseMm = radiusRmseMm;
            this.maxRadiusErrorMm = maxRadiusErrorMm;
            this.lengthRmseMm = lengthRmseMm;
        }
    }

    public static final class Result {
        public final State state;
        public final boolean alphaReady, fieldPilotAllowed, industrialQualified;
        public final List<String> blockers, warnings;
        public final String reason;

        Result(State state, boolean alphaReady, boolean fieldPilotAllowed,
               boolean industrialQualified, List<String> blockers,
               List<String> warnings, String reason) {
            this.state = state; this.alphaReady = alphaReady;
            this.fieldPilotAllowed = fieldPilotAllowed;
            this.industrialQualified = industrialQualified;
            this.blockers = Collections.unmodifiableList(new ArrayList<String>(blockers));
            this.warnings = Collections.unmodifiableList(new ArrayList<String>(warnings));
            this.reason = reason;
        }

        static Result blocked(String reason) {
            return new Result(State.ALPHA_BLOCKED, false, false, false,
                    Collections.singletonList(reason), Collections.<String>emptyList(), reason);
        }
    }
}

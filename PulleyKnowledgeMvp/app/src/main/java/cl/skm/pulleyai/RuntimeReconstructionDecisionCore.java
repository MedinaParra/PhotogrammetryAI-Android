package cl.skm.pulleyai;

import java.util.Locale;

/**
 * Fail-closed runtime decision after photogrammetry qualification and optional local BA.
 * The unoptimized geometry remains the only fallback and can never be relabeled as optimized.
 */
public final class RuntimeReconstructionDecisionCore {
    private RuntimeReconstructionDecisionCore() {}

    public enum State { READY, REVIEW, BLOCKED }

    public static Result decide(PhotogrammetrySafetyGateCore.Result gate,
                                LocalBundleAdjustmentCore.Result ba,
                                boolean problemAvailable,
                                boolean resourceBudgetReady,
                                boolean interrupted) {
        if (gate == null) return blocked("SAFETY_GATE_MISSING");
        if (gate.state == PhotogrammetrySafetyGateCore.State.BLOCKED) {
            return blocked("SAFETY_GATE_BLOCKED");
        }
        if (gate.state == PhotogrammetrySafetyGateCore.State.REVIEW) {
            return review("SAFETY_GATE_REVIEW", false, true, ba);
        }
        if (!resourceBudgetReady) {
            return review("RESOURCE_BUDGET_NOT_READY", false, true, ba);
        }
        if (interrupted) {
            return review("OPTIMIZATION_INTERRUPTED", false, true, ba);
        }
        if (!problemAvailable) {
            return review("BA_PROBLEM_UNAVAILABLE", false, true, ba);
        }
        if (ba == null || !ba.solved) {
            return review(ba == null ? "BA_RESULT_MISSING" : "BA_" + ba.status,
                    false, true, ba);
        }
        if (!ba.ready()) {
            return review("BA_" + ba.status, false, true, ba);
        }
        if (!Double.isFinite(ba.finalRmsPx) || ba.finalRmsPx > ba.initialRmsPx
                || ba.positiveDepthRatio < 0.80) {
            return review("BA_ACCEPTANCE_FAILED", false, true, ba);
        }
        return new Result(State.READY, "OPTIMIZED_GEOMETRY_ACCEPTED", true, false,
                ba.status, ba.initialRmsPx, ba.finalRmsPx, ba.improvementRatio,
                ba.positiveDepthRatio);
    }

    private static Result blocked(String reason) {
        return new Result(State.BLOCKED, reason, false, false, "NOT_RUN",
                Double.NaN, Double.NaN, 0.0, 0.0);
    }

    private static Result review(String reason, boolean optimized, boolean fallback,
                                 LocalBundleAdjustmentCore.Result ba) {
        return new Result(State.REVIEW, reason, optimized, fallback,
                ba == null ? "NOT_RUN" : ba.status,
                ba == null ? Double.NaN : ba.initialRmsPx,
                ba == null ? Double.NaN : ba.finalRmsPx,
                ba == null ? 0.0 : ba.improvementRatio,
                ba == null ? 0.0 : ba.positiveDepthRatio);
    }

    public static final class Result {
        public final State state;
        public final String reason;
        public final boolean useOptimizedGeometry;
        public final boolean useUnoptimizedFallback;
        public final String baStatus;
        public final double initialRmsPx;
        public final double finalRmsPx;
        public final double improvementRatio;
        public final double positiveDepthRatio;

        Result(State state, String reason, boolean useOptimizedGeometry,
               boolean useUnoptimizedFallback, String baStatus,
               double initialRmsPx, double finalRmsPx, double improvementRatio,
               double positiveDepthRatio) {
            this.state = state;
            this.reason = reason;
            this.useOptimizedGeometry = useOptimizedGeometry;
            this.useUnoptimizedFallback = useUnoptimizedFallback;
            this.baStatus = baStatus;
            this.initialRmsPx = initialRmsPx;
            this.finalRmsPx = finalRmsPx;
            this.improvementRatio = improvementRatio;
            this.positiveDepthRatio = positiveDepthRatio;
        }

        public boolean canPublishOptimizedGeometry() {
            return state == State.READY && useOptimizedGeometry && !useUnoptimizedFallback;
        }

        public String summary() {
            return state + " · " + reason + " · BA " + baStatus
                    + (Double.isFinite(finalRmsPx)
                    ? String.format(Locale.ROOT, " · RMS %.3f→%.3f px", initialRmsPx, finalRmsPx)
                    : "")
                    + (useUnoptimizedFallback ? " · FALLBACK SIN OPTIMIZAR" : "");
        }
    }
}

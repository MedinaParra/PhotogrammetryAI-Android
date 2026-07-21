package cl.skm.pulleyai;

import android.content.Context;

/**
 * Runtime service that joins report qualification, bounded BA and append-only audit.
 * A missing BA problem produces a reviewable unoptimized fallback, never a false success.
 */
public final class RuntimeReconstructionCoordinator {
    private RuntimeReconstructionCoordinator() {}

    public static Outcome evaluate(Context context, String sessionId,
                                   SessionOverlapAnalyzer.Report report,
                                   PhotogrammetrySafetyGateAdapter.SupplementalMetrics supplemental,
                                   LocalBundleAdjustmentCore.Problem problem,
                                   boolean resourceBudgetReady,
                                   boolean interrupted) {
        if (context == null) throw new IllegalArgumentException("context is required");
        if (report == null) throw new IllegalArgumentException("report is required");
        PhotogrammetrySafetyGateCore.Result gate = PhotogrammetrySafetyGateAdapter.evaluate(
                report, supplemental == null
                        ? PhotogrammetrySafetyGateAdapter.SupplementalMetrics.unknown()
                        : supplemental);

        LocalBundleAdjustmentCore.Result ba = null;
        if (gate.canReconstructAutomatically() && resourceBudgetReady && !interrupted
                && problem != null) {
            ba = LocalBundleAdjustmentCore.optimize(problem, gate, 10, 2.0, 0.02);
        }
        RuntimeReconstructionDecisionCore.Result decision =
                RuntimeReconstructionDecisionCore.decide(gate, ba, problem != null,
                        resourceBudgetReady, interrupted);
        RuntimeReconstructionDecisionStore store =
                new RuntimeReconstructionDecisionStore(context);
        long auditId;
        try {
            auditId = store.append(sessionId, gate, decision);
        } finally {
            store.close();
        }
        return new Outcome(gate, ba, decision, auditId);
    }

    public static final class Outcome {
        public final PhotogrammetrySafetyGateCore.Result gate;
        public final LocalBundleAdjustmentCore.Result bundleAdjustment;
        public final RuntimeReconstructionDecisionCore.Result decision;
        public final long auditId;

        Outcome(PhotogrammetrySafetyGateCore.Result gate,
                LocalBundleAdjustmentCore.Result bundleAdjustment,
                RuntimeReconstructionDecisionCore.Result decision, long auditId) {
            this.gate = gate;
            this.bundleAdjustment = bundleAdjustment;
            this.decision = decision;
            this.auditId = auditId;
        }

        public String summary() {
            return gate.summary() + "\n" + decision.summary() + "\nAuditoría #" + auditId;
        }
    }
}

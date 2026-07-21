package cl.skm.pulleyai;

import android.content.Context;

/**
 * Runtime service joining report qualification, bounded BA, conservative rotational refinement
 * and append-only audit. Missing or rejected rotation refinement preserves the stable base BA.
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
        RotationalBundleAdjustmentCore.Result rotational = null;
        if (gate.canReconstructAutomatically() && resourceBudgetReady && !interrupted
                && problem != null) {
            rotational = RotationalBundleAdjustmentCore.optimize(problem, gate);
            ba = rotational.bundleAdjustment;
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
        return new Outcome(gate, ba, rotational, decision, auditId);
    }

    public static final class Outcome {
        public final PhotogrammetrySafetyGateCore.Result gate;
        public final LocalBundleAdjustmentCore.Result bundleAdjustment;
        public final RotationalBundleAdjustmentCore.Result rotationalBundleAdjustment;
        public final RuntimeReconstructionDecisionCore.Result decision;
        public final long auditId;

        Outcome(PhotogrammetrySafetyGateCore.Result gate,
                LocalBundleAdjustmentCore.Result bundleAdjustment,
                RotationalBundleAdjustmentCore.Result rotationalBundleAdjustment,
                RuntimeReconstructionDecisionCore.Result decision, long auditId) {
            this.gate = gate;
            this.bundleAdjustment = bundleAdjustment;
            this.rotationalBundleAdjustment = rotationalBundleAdjustment;
            this.decision = decision;
            this.auditId = auditId;
        }

        public String summary() {
            return gate.summary() + "\n" + decision.summary()
                    + (rotationalBundleAdjustment == null ? ""
                    : "\n" + rotationalBundleAdjustment.summary())
                    + "\nAuditoría #" + auditId;
        }
    }
}
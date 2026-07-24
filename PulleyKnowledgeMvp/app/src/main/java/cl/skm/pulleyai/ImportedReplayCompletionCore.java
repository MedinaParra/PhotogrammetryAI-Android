package cl.skm.pulleyai;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** Produces a final software-roadmap decision without conflating it with industrial readiness. */
public final class ImportedReplayCompletionCore {
    private ImportedReplayCompletionCore() {}

    public static Result evaluate(Input input) {
        if (input == null) return Result.blocked("INPUT_MISSING");
        List<String> missing = new ArrayList<String>();
        if (!input.packageValid) missing.add("PACKAGE_PREFLIGHT");
        if (!input.matchingComplete) missing.add("MULTISCALE_MATCHING");
        if (!input.tracksComplete) missing.add("MULTIVIEW_TRACKS");
        if (!input.seedStageComplete) missing.add("SEED_GEOMETRY_STAGE");
        if (!input.componentStageComplete) missing.add("COMPONENT_TOPOLOGY");
        if (!input.bridgeStageComplete) missing.add("BRIDGE_EVIDENCE");
        boolean softwareComplete = missing.isEmpty();
        String state;
        String requiredAction;
        if (!softwareComplete) {
            state = "SOFTWARE_REPLAY_INCOMPLETE";
            requiredAction = "COMPLETE_MISSING_STAGES";
        } else if (input.globalConnected && input.localGeometryReady) {
            state = "SOFTWARE_REPLAY_COMPLETE_GLOBAL_GEOMETRY_DIAGNOSTIC";
            requiredAction = input.metricScaleReady
                    ? "VALIDATE_METROLOGY_AND_DEVICE_CAMPAIGN"
                    : "RESOLVE_METRIC_SCALE_AND_VALIDATE_METROLOGY";
        } else if (input.localGeometryReady && input.bridgeRecommendationAvailable) {
            state = "SOFTWARE_REPLAY_COMPLETE_RECAPTURE_REQUIRED";
            requiredAction = "CAPTURE_RECOMMENDED_BRIDGE_AND_REPROCESS";
        } else if (input.bridgeRecommendationAvailable) {
            state = "SOFTWARE_REPLAY_COMPLETE_BRIDGE_ONLY";
            requiredAction = "CAPTURE_RECOMMENDED_BRIDGE_AND_REPROCESS";
        } else {
            state = "SOFTWARE_REPLAY_COMPLETE_NO_GEOMETRIC_REMEDIATION";
            requiredAction = "RECAPTURE_FULL_TWO_RING_SESSION";
        }
        int softwarePercent = softwareComplete ? 100
                : Math.max(0, 100 - missing.size() * 16);
        boolean industrialReady = softwareComplete && input.globalConnected
                && input.localGeometryReady && input.metricScaleReady
                && input.metrologyValidated && input.deviceCampaignPassed
                && input.corporateIdentitySigned;
        return new Result(softwareComplete, softwarePercent, state,
                requiredAction, missing, input.globalConnected,
                input.localGeometryReady, input.metricScaleReady,
                input.metrologyValidated, input.deviceCampaignPassed,
                input.corporateIdentitySigned, industrialReady,
                input.acceptedFrames, input.representedFrames,
                input.trackCount, input.triangulatedSeedPoints,
                input.bridgeLeftFrame, input.bridgeRightFrame);
    }

    public static final class Input {
        public boolean packageValid;
        public boolean matchingComplete;
        public boolean tracksComplete;
        public boolean seedStageComplete;
        public boolean componentStageComplete;
        public boolean bridgeStageComplete;
        public boolean bridgeRecommendationAvailable;
        public boolean globalConnected;
        public boolean localGeometryReady;
        public boolean metricScaleReady;
        public boolean metrologyValidated;
        public boolean deviceCampaignPassed;
        public boolean corporateIdentitySigned;
        public int acceptedFrames;
        public int representedFrames;
        public int trackCount;
        public int triangulatedSeedPoints;
        public int bridgeLeftFrame = -1;
        public int bridgeRightFrame = -1;
    }

    public static final class Result {
        public final boolean softwareReplayComplete;
        public final int softwareRoadmapPercent;
        public final String state, requiredAction;
        public final List<String> missingStages;
        public final boolean globalConnected, localGeometryReady, metricScaleReady;
        public final boolean metrologyValidated, deviceCampaignPassed;
        public final boolean corporateIdentitySigned, industrialReady;
        public final int acceptedFrames, representedFrames, trackCount;
        public final int triangulatedSeedPoints, bridgeLeftFrame, bridgeRightFrame;

        Result(boolean softwareReplayComplete, int softwareRoadmapPercent,
               String state, String requiredAction, List<String> missingStages,
               boolean globalConnected, boolean localGeometryReady,
               boolean metricScaleReady, boolean metrologyValidated,
               boolean deviceCampaignPassed, boolean corporateIdentitySigned,
               boolean industrialReady, int acceptedFrames,
               int representedFrames, int trackCount,
               int triangulatedSeedPoints, int bridgeLeftFrame,
               int bridgeRightFrame) {
            this.softwareReplayComplete = softwareReplayComplete;
            this.softwareRoadmapPercent = softwareRoadmapPercent;
            this.state = state;
            this.requiredAction = requiredAction;
            this.missingStages = Collections.unmodifiableList(
                    new ArrayList<String>(missingStages));
            this.globalConnected = globalConnected;
            this.localGeometryReady = localGeometryReady;
            this.metricScaleReady = metricScaleReady;
            this.metrologyValidated = metrologyValidated;
            this.deviceCampaignPassed = deviceCampaignPassed;
            this.corporateIdentitySigned = corporateIdentitySigned;
            this.industrialReady = industrialReady;
            this.acceptedFrames = acceptedFrames;
            this.representedFrames = representedFrames;
            this.trackCount = trackCount;
            this.triangulatedSeedPoints = triangulatedSeedPoints;
            this.bridgeLeftFrame = bridgeLeftFrame;
            this.bridgeRightFrame = bridgeRightFrame;
        }

        static Result blocked(String state) {
            return new Result(false, 0, state, "COMPLETE_MISSING_STAGES",
                    Collections.singletonList(state), false, false, false,
                    false, false, false, false, 0, 0, 0, 0, -1, -1);
        }

        public String summary() {
            StringBuilder text = new StringBuilder();
            text.append("ROADMAP SOFTWARE ").append(softwareRoadmapPercent).append(" %")
                    .append(" · ").append(state)
                    .append("\nGlobal: ").append(globalConnected ? "CONECTADA" : "BLOQUEADA")
                    .append(" · geometría local: ")
                    .append(localGeometryReady ? "READY" : "NO")
                    .append(" · escala métrica: ")
                    .append(metricScaleReady ? "READY" : "NO")
                    .append("\nAcción siguiente: ").append(requiredAction);
            if (bridgeLeftFrame >= 0 && bridgeRightFrame >= 0) {
                text.append(" · fotos ").append(bridgeLeftFrame)
                        .append(" ↔ ").append(bridgeRightFrame);
            }
            text.append("\nPreparación industrial: ")
                    .append(industrialReady ? "READY" : "BLOQUEADA")
                    .append(" · metrología ")
                    .append(metrologyValidated ? "sí" : "no")
                    .append(" · campaña física ")
                    .append(deviceCampaignPassed ? "sí" : "no")
                    .append(" · identidad corporativa ")
                    .append(corporateIdentitySigned ? "sí" : "no");
            return text.toString();
        }

        public String canonicalJson() {
            StringBuilder json = new StringBuilder();
            json.append("{\n")
                    .append("\"schema\":\"skm-imported-replay-completion/1\",")
                    .append("\n\"softwareReplayComplete\":")
                    .append(softwareReplayComplete).append(',')
                    .append("\n\"softwareRoadmapPercent\":")
                    .append(softwareRoadmapPercent).append(',')
                    .append("\n\"state\":\"").append(state).append("\",")
                    .append("\n\"requiredAction\":\"").append(requiredAction).append("\",")
                    .append("\n\"globalConnected\":").append(globalConnected).append(',')
                    .append("\n\"localGeometryReady\":").append(localGeometryReady).append(',')
                    .append("\n\"metricScaleReady\":").append(metricScaleReady).append(',')
                    .append("\n\"metrologyValidated\":").append(metrologyValidated).append(',')
                    .append("\n\"deviceCampaignPassed\":").append(deviceCampaignPassed).append(',')
                    .append("\n\"corporateIdentitySigned\":")
                    .append(corporateIdentitySigned).append(',')
                    .append("\n\"industrialReady\":").append(industrialReady).append(',')
                    .append("\n\"acceptedFrames\":").append(acceptedFrames).append(',')
                    .append("\n\"representedFrames\":").append(representedFrames).append(',')
                    .append("\n\"trackCount\":").append(trackCount).append(',')
                    .append("\n\"triangulatedSeedPoints\":")
                    .append(triangulatedSeedPoints).append(',')
                    .append("\n\"bridgeLeftFrame\":").append(bridgeLeftFrame).append(',')
                    .append("\n\"bridgeRightFrame\":").append(bridgeRightFrame).append(',')
                    .append("\n\"missingStages\":[");
            for (int i = 0; i < missingStages.size(); i++) {
                if (i > 0) json.append(',');
                json.append('"').append(missingStages.get(i)).append('"');
            }
            return json.append("]\n}").toString();
        }
    }
}

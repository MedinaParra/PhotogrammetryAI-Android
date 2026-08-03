package cl.skm.pulleyai;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Converts segmentation instances and MobileNet capture-quality scores into an explicit,
 * inspectable scene state. This is functional scene awareness, not human consciousness.
 */
public final class PulleySceneAwarenessCore {
    private PulleySceneAwarenessCore() {}

    public static State evaluate(List<Instance> instances, Quality quality) {
        List<Instance> safe = instances == null
                ? Collections.<Instance>emptyList() : instances;
        Quality q = quality == null ? new Quality(Collections.<String, Float>emptyMap()) : quality;

        Instance target = null;
        int shellCount = 0;
        int otherPulleyCount = 0;
        int endDiscCount = 0;
        boolean shaft = false;
        boolean bearing = false;
        boolean person = false;
        boolean obstruction = false;

        for (Instance item : safe) {
            if (item == null || item.confidence < 0.20f) continue;
            if ("pulley_shell".equals(item.label)) {
                shellCount++;
                if (target == null || item.targetScore() > target.targetScore()) target = item;
            } else if ("other_pulley".equals(item.label)) {
                otherPulleyCount++;
            } else if ("pulley_end_disc".equals(item.label)) {
                endDiscCount++;
            } else if ("pulley_shaft".equals(item.label)) {
                shaft = true;
            } else if ("bearing_housing".equals(item.label)) {
                bearing = true;
            } else if ("person".equals(item.label)) {
                person = true;
            } else if ("obstruction".equals(item.label)) {
                obstruction = true;
            }
        }
        int competitors = otherPulleyCount + Math.max(0, shellCount - 1);
        float targetConfidence = target == null ? 0f : target.confidence;
        float shellVisibility = target == null ? 0f : clamp(target.areaFraction, 0f, 1f);
        boolean leftEnd = false;
        boolean rightEnd = false;
        for (Instance item : safe) {
            if (item == null || !"pulley_end_disc".equals(item.label) || item.confidence < 0.35f) continue;
            if (item.centerX < 0.5f) leftEnd = true;
            else rightEnd = true;
        }

        String view;
        if (leftEnd && rightEnd) view = "AXIAL_COMPLETA";
        else if (leftEnd) view = "OBLICUA_EXTREMO_IZQUIERDO";
        else if (rightEnd) view = "OBLICUA_EXTREMO_DERECHO";
        else if (shaft) view = "LATERAL_CON_EJE";
        else if (target != null) view = "LATERAL_MANTO";
        else view = "OBJETIVO_NO_LOCALIZADO";

        String decision = "ACCEPT";
        String reason = "Escena compatible con captura";
        String instruction = "Mantenga la polea ocupando 60–80% del encuadre y avance al siguiente sector.";

        if (target == null || targetConfidence < 0.45f) {
            decision = "REJECT";
            reason = "Manto objetivo no detectado con confianza suficiente";
            instruction = "Centre una sola polea y acerque el teléfono hasta distinguir claramente el manto.";
        } else if (q.score("wrong_target") >= 0.55f) {
            decision = "REJECT";
            reason = "El clasificador considera que no corresponde a la polea bloqueada";
            instruction = "Vuelva a la polea objetivo y bloquéela nuevamente antes de capturar.";
        } else if (competitors > 0 || q.score("multiple_pulleys") >= 0.55f) {
            decision = "REJECT";
            reason = "Hay más de una polea candidata en la escena";
            instruction = "Cambie el encuadre para excluir las poleas del fondo.";
        } else if (person || obstruction || q.score("person_obstruction") >= 0.50f
                || q.score("tool_obstruction") >= 0.50f) {
            decision = "REJECT";
            reason = "La polea está obstruida por persona, herramienta u objeto";
            instruction = "Despeje el campo visual y repita la fotografía.";
        } else if (q.score("motion_blur") >= 0.50f) {
            decision = "REJECT";
            reason = "Desenfoque por movimiento";
            instruction = "Detenga el movimiento, estabilice el teléfono y vuelva a capturar.";
        } else if (q.score("too_dark") >= 0.55f) {
            decision = "REJECT";
            reason = "Iluminación insuficiente";
            instruction = "Ilumine homogéneamente el manto sin producir reflejo directo.";
        } else if (q.score("strong_reflection") >= 0.55f) {
            decision = "REVIEW";
            reason = "Reflejo intenso sobre el manto";
            instruction = "Cambie ligeramente el ángulo o use iluminación difusa.";
        } else if (shellVisibility < 0.18f || q.score("too_far") >= 0.55f) {
            decision = "REVIEW";
            reason = "La polea ocupa una fracción demasiado pequeña del encuadre";
            instruction = "Acérquese conservando visible el largo útil del manto.";
        } else if (shellVisibility > 0.82f || q.score("too_close") >= 0.55f) {
            decision = "REVIEW";
            reason = "La polea está recortada o demasiado cerca";
            instruction = "Retroceda hasta incluir los bordes del manto.";
        } else if (q.score("partial_shell") >= 0.55f || shellVisibility < 0.35f) {
            decision = "REVIEW";
            reason = "Cobertura parcial del manto";
            instruction = "Reencuadre para incluir más superficie longitudinal y circunferencial.";
        }

        return new State(target != null, targetConfidence, shellVisibility,
                leftEnd, rightEnd, shaft, bearing, person, obstruction,
                competitors, view, decision, reason, instruction, q.topLabel(), q.topScore());
    }

    private static float clamp(float value, float min, float max) {
        return Math.max(min, Math.min(max, value));
    }

    public static final class Instance {
        public final String label;
        public final float confidence;
        public final float areaFraction;
        public final float centerX;
        public final float centerY;

        public Instance(String label, float confidence, float areaFraction,
                        float centerX, float centerY) {
            this.label = label == null ? "" : label;
            this.confidence = clamp(confidence, 0f, 1f);
            this.areaFraction = clamp(areaFraction, 0f, 1f);
            this.centerX = clamp(centerX, 0f, 1f);
            this.centerY = clamp(centerY, 0f, 1f);
        }

        float targetScore() {
            float centered = 1f - Math.min(1f, Math.abs(centerX - 0.5f) * 1.6f
                    + Math.abs(centerY - 0.5f) * 0.8f);
            return confidence * 0.65f + areaFraction * 0.25f + centered * 0.10f;
        }
    }

    public static final class Quality {
        private final Map<String, Float> scores;

        public Quality(Map<String, Float> input) {
            Map<String, Float> copy = new HashMap<String, Float>();
            if (input != null) {
                for (Map.Entry<String, Float> entry : input.entrySet()) {
                    if (entry.getKey() == null || entry.getValue() == null) continue;
                    copy.put(entry.getKey(), clamp(entry.getValue(), 0f, 1f));
                }
            }
            scores = Collections.unmodifiableMap(copy);
        }

        public float score(String label) {
            Float value = scores.get(label);
            return value == null ? 0f : value;
        }

        public String topLabel() {
            String label = "unknown";
            float best = -1f;
            for (Map.Entry<String, Float> entry : scores.entrySet()) {
                if (entry.getValue() > best) {
                    best = entry.getValue();
                    label = entry.getKey();
                }
            }
            return label;
        }

        public float topScore() {
            float best = 0f;
            for (Float value : scores.values()) best = Math.max(best, value);
            return best;
        }
    }

    public static final class State {
        public final boolean shellDetected;
        public final float targetConfidence;
        public final float shellVisibility;
        public final boolean leftEndDetected;
        public final boolean rightEndDetected;
        public final boolean shaftDetected;
        public final boolean bearingDetected;
        public final boolean personDetected;
        public final boolean obstructionDetected;
        public final int competingPulleyCount;
        public final String currentView;
        public final String decision;
        public final String reason;
        public final String nextInstruction;
        public final String qualityLabel;
        public final float qualityConfidence;

        State(boolean shellDetected, float targetConfidence, float shellVisibility,
              boolean leftEndDetected, boolean rightEndDetected,
              boolean shaftDetected, boolean bearingDetected,
              boolean personDetected, boolean obstructionDetected,
              int competingPulleyCount, String currentView, String decision,
              String reason, String nextInstruction,
              String qualityLabel, float qualityConfidence) {
            this.shellDetected = shellDetected;
            this.targetConfidence = targetConfidence;
            this.shellVisibility = shellVisibility;
            this.leftEndDetected = leftEndDetected;
            this.rightEndDetected = rightEndDetected;
            this.shaftDetected = shaftDetected;
            this.bearingDetected = bearingDetected;
            this.personDetected = personDetected;
            this.obstructionDetected = obstructionDetected;
            this.competingPulleyCount = Math.max(0, competingPulleyCount);
            this.currentView = currentView;
            this.decision = decision;
            this.reason = reason;
            this.nextInstruction = nextInstruction;
            this.qualityLabel = qualityLabel;
            this.qualityConfidence = qualityConfidence;
        }

        public String summary() {
            return String.format(Locale.ROOT,
                    "IA %s · manto %.0f%% · confianza %.0f%% · vista %s · competidoras %d"
                            + "\n%s\nSiguiente: %s\nClasificador: %s %.0f%%",
                    decision, shellVisibility * 100f, targetConfidence * 100f,
                    currentView, competingPulleyCount, reason, nextInstruction,
                    qualityLabel, qualityConfidence * 100f);
        }
    }
}

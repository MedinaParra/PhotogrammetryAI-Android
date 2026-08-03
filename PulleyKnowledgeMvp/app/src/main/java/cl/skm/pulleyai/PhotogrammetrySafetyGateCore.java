package cl.skm.pulleyai;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

/**
 * Fail-closed safety gate before promoting a capture to 3D reconstruction.
 * It combines coverage, pair geometry, parallax, planar dominance, pose graph,
 * image degradation and reprojection instead of averaging them into one opaque score.
 */
public final class PhotogrammetrySafetyGateCore {
    private PhotogrammetrySafetyGateCore() {}

    public enum State { READY, REVIEW, BLOCKED }

    public static final class Metrics {
        public final int selectedFrames;
        public final int lowRingSectors;
        public final int highRingSectors;
        public final int candidatePairs;
        public final int usablePairs;
        public final int strongPairs;
        public final int missingIntrinsicsFrames;
        public final int missingOrbitPriors;
        public final double medianParallaxDegrees;
        public final double p10ParallaxDegrees;
        public final double fundamentalSupportRatio;
        public final double homographyDominanceRatio;
        public final boolean poseGraphConnected;
        public final int trustedCycleEdges;
        public final double p90CycleRotationDegrees;
        public final double p90CycleTranslationDegrees;
        public final double medianReprojectionPx;
        public final double p90ReprojectionPx;
        public final double blurryFrameFraction;
        public final double reflectiveFrameFraction;
        public final double repetitiveAmbiguityFraction;

        private Metrics(Builder b) {
            selectedFrames = nonNegative(b.selectedFrames, "selectedFrames");
            lowRingSectors = range(b.lowRingSectors, 0, 12, "lowRingSectors");
            highRingSectors = range(b.highRingSectors, 0, 12, "highRingSectors");
            candidatePairs = nonNegative(b.candidatePairs, "candidatePairs");
            usablePairs = nonNegative(b.usablePairs, "usablePairs");
            strongPairs = nonNegative(b.strongPairs, "strongPairs");
            missingIntrinsicsFrames = nonNegative(b.missingIntrinsicsFrames, "missingIntrinsicsFrames");
            missingOrbitPriors = nonNegative(b.missingOrbitPriors, "missingOrbitPriors");
            medianParallaxDegrees = finiteOrNaN(b.medianParallaxDegrees, "medianParallaxDegrees");
            p10ParallaxDegrees = finiteOrNaN(b.p10ParallaxDegrees, "p10ParallaxDegrees");
            fundamentalSupportRatio = fractionOrNaN(b.fundamentalSupportRatio, "fundamentalSupportRatio");
            homographyDominanceRatio = fractionOrNaN(b.homographyDominanceRatio, "homographyDominanceRatio");
            poseGraphConnected = b.poseGraphConnected;
            trustedCycleEdges = nonNegative(b.trustedCycleEdges, "trustedCycleEdges");
            p90CycleRotationDegrees = finiteOrNaN(b.p90CycleRotationDegrees, "p90CycleRotationDegrees");
            p90CycleTranslationDegrees = finiteOrNaN(b.p90CycleTranslationDegrees, "p90CycleTranslationDegrees");
            medianReprojectionPx = finiteOrNaN(b.medianReprojectionPx, "medianReprojectionPx");
            p90ReprojectionPx = finiteOrNaN(b.p90ReprojectionPx, "p90ReprojectionPx");
            blurryFrameFraction = fractionOrNaN(b.blurryFrameFraction, "blurryFrameFraction");
            reflectiveFrameFraction = fractionOrNaN(b.reflectiveFrameFraction, "reflectiveFrameFraction");
            repetitiveAmbiguityFraction = fractionOrNaN(b.repetitiveAmbiguityFraction, "repetitiveAmbiguityFraction");
            if (usablePairs > candidatePairs || strongPairs > usablePairs) {
                throw new IllegalArgumentException("pair counts are inconsistent");
            }
        }

        public static Builder builder() { return new Builder(); }

        public static final class Builder {
            private int selectedFrames;
            private int lowRingSectors;
            private int highRingSectors;
            private int candidatePairs;
            private int usablePairs;
            private int strongPairs;
            private int missingIntrinsicsFrames;
            private int missingOrbitPriors;
            private double medianParallaxDegrees = Double.NaN;
            private double p10ParallaxDegrees = Double.NaN;
            private double fundamentalSupportRatio = Double.NaN;
            private double homographyDominanceRatio = Double.NaN;
            private boolean poseGraphConnected;
            private int trustedCycleEdges;
            private double p90CycleRotationDegrees = Double.NaN;
            private double p90CycleTranslationDegrees = Double.NaN;
            private double medianReprojectionPx = Double.NaN;
            private double p90ReprojectionPx = Double.NaN;
            private double blurryFrameFraction = Double.NaN;
            private double reflectiveFrameFraction = Double.NaN;
            private double repetitiveAmbiguityFraction = Double.NaN;

            public Builder frames(int selected, int lowSectors, int highSectors) { selectedFrames=selected; lowRingSectors=lowSectors; highRingSectors=highSectors; return this; }
            public Builder pairs(int candidate, int usable, int strong) { candidatePairs=candidate; usablePairs=usable; strongPairs=strong; return this; }
            public Builder missing(int intrinsics, int orbitPriors) { missingIntrinsicsFrames=intrinsics; missingOrbitPriors=orbitPriors; return this; }
            public Builder parallax(double median, double p10) { medianParallaxDegrees=median; p10ParallaxDegrees=p10; return this; }
            public Builder modelCompetition(double fundamentalSupport, double homographyDominance) { fundamentalSupportRatio=fundamentalSupport; homographyDominanceRatio=homographyDominance; return this; }
            public Builder poseGraph(boolean connected, int cycleEdges, double p90Rotation, double p90Translation) { poseGraphConnected=connected; trustedCycleEdges=cycleEdges; p90CycleRotationDegrees=p90Rotation; p90CycleTranslationDegrees=p90Translation; return this; }
            public Builder reprojection(double median, double p90) { medianReprojectionPx=median; p90ReprojectionPx=p90; return this; }
            public Builder degradation(double blurryFraction, double reflectiveFraction, double repetitiveFraction) { blurryFrameFraction=blurryFraction; reflectiveFrameFraction=reflectiveFraction; repetitiveAmbiguityFraction=repetitiveFraction; return this; }
            public Metrics build() { return new Metrics(this); }
        }
    }

    public static final class Result {
        public final State state;
        public final List<String> blockers;
        public final List<String> warnings;
        public final List<String> evidenceGaps;
        public final double qualityScore;

        Result(State state, List<String> blockers, List<String> warnings,
               List<String> evidenceGaps, double qualityScore) {
            this.state = state;
            this.blockers = Collections.unmodifiableList(new ArrayList<String>(blockers));
            this.warnings = Collections.unmodifiableList(new ArrayList<String>(warnings));
            this.evidenceGaps = Collections.unmodifiableList(new ArrayList<String>(evidenceGaps));
            this.qualityScore = qualityScore;
        }

        public boolean canReconstructAutomatically() { return state == State.READY; }

        public String summary() {
            StringBuilder out = new StringBuilder();
            out.append(state).append(" · calidad ").append(String.format(Locale.ROOT, "%.0f%%", qualityScore*100.0));
            for (String value : blockers) out.append("\nBLOQUEO: ").append(value);
            for (String value : warnings) out.append("\nREVISAR: ").append(value);
            for (String value : evidenceGaps) out.append("\nFALTA MÉTRICA: ").append(value);
            return out.toString();
        }
    }

    public static Result evaluate(Metrics m) {
        if (m == null) throw new IllegalArgumentException("metrics are required");
        List<String> blockers = new ArrayList<String>();
        List<String> warnings = new ArrayList<String>();
        List<String> gaps = new ArrayList<String>();

        if (m.selectedFrames < 24) blockers.add("menos de 24 cuadros seleccionados");
        else if (m.selectedFrames < 30) warnings.add("menos de 30 cuadros seleccionados");
        if (m.lowRingSectors < 8 || m.highRingSectors < 8) blockers.add("cobertura orbital inferior a 8/12 en un anillo");
        else if (m.lowRingSectors < 11 || m.highRingSectors < 11) warnings.add("cobertura orbital incompleta");

        int requiredUsable = Math.max(10, m.selectedFrames/2);
        if (m.usablePairs < requiredUsable) blockers.add("pares utilizables insuficientes: " + m.usablePairs + "/" + requiredUsable);
        if (m.strongPairs < Math.max(4, requiredUsable/3)) warnings.add("pocos pares fuertes");
        if (m.missingIntrinsicsFrames > 0) blockers.add("faltan intrínsecos en " + m.missingIntrinsicsFrames + " cuadros");
        if (m.missingOrbitPriors > Math.max(2, m.candidatePairs/5)) warnings.add("prior orbital incompleto");

        if (Double.isNaN(m.medianParallaxDegrees) || Double.isNaN(m.p10ParallaxDegrees)) {
            gaps.add("distribución de paralaje");
        } else {
            if (m.medianParallaxDegrees < 1.0 || m.p10ParallaxDegrees < 0.20) {
                blockers.add("paralaje insuficiente para profundidad estable");
            } else if (m.medianParallaxDegrees < 1.8 || m.p10ParallaxDegrees < 0.45) {
                warnings.add("paralaje marginal");
            }
        }

        if (Double.isNaN(m.homographyDominanceRatio) || Double.isNaN(m.fundamentalSupportRatio)) {
            gaps.add("competencia homografía/fundamental");
        } else {
            if (m.homographyDominanceRatio >= 0.85 && m.fundamentalSupportRatio < 0.65) {
                blockers.add("dominancia planar/homográfica incompatible con reconstrucción volumétrica");
            } else if (m.homographyDominanceRatio >= 0.72) {
                warnings.add("escena mayoritariamente planar; revisar profundidad");
            }
        }

        if (!m.poseGraphConnected) blockers.add("grafo de poses desconectado");
        if (m.poseGraphConnected && m.trustedCycleEdges < 1) warnings.add("sin cierres de ciclo confiables");
        if (!Double.isNaN(m.p90CycleRotationDegrees) && m.p90CycleRotationDegrees > 14.0) blockers.add("inconsistencia rotacional de ciclos > 14°");
        else if (!Double.isNaN(m.p90CycleRotationDegrees) && m.p90CycleRotationDegrees > 6.0) warnings.add("residuo rotacional de ciclos elevado");
        if (!Double.isNaN(m.p90CycleTranslationDegrees) && m.p90CycleTranslationDegrees > 65.0) blockers.add("inconsistencia direccional de ciclos > 65°");
        else if (!Double.isNaN(m.p90CycleTranslationDegrees) && m.p90CycleTranslationDegrees > 35.0) warnings.add("residuo direccional de ciclos elevado");

        if (Double.isNaN(m.medianReprojectionPx) || Double.isNaN(m.p90ReprojectionPx)) {
            gaps.add("distribución de error de reproyección");
        } else {
            if (m.medianReprojectionPx > 4.0 || m.p90ReprojectionPx > 8.0) blockers.add("error de reproyección excesivo");
            else if (m.medianReprojectionPx > 2.0 || m.p90ReprojectionPx > 4.5) warnings.add("error de reproyección marginal");
        }

        if (!Double.isNaN(m.blurryFrameFraction)) {
            if (m.blurryFrameFraction > 0.35) blockers.add("más de 35% de cuadros desenfocados");
            else if (m.blurryFrameFraction > 0.18) warnings.add("proporción de desenfoque elevada");
        } else gaps.add("fracción de desenfoque");

        if (!Double.isNaN(m.reflectiveFrameFraction)) {
            if (m.reflectiveFrameFraction > 0.45) blockers.add("reflejos dominan más de 45% de cuadros");
            else if (m.reflectiveFrameFraction > 0.25) warnings.add("reflejos especulares elevados");
        } else gaps.add("fracción de reflejos");

        if (!Double.isNaN(m.repetitiveAmbiguityFraction)) {
            if (m.repetitiveAmbiguityFraction > 0.45) blockers.add("ambigüedad repetitiva en más de 45% de pares");
            else if (m.repetitiveAmbiguityFraction > 0.25) warnings.add("textura repetitiva significativa");
        } else gaps.add("fracción de ambigüedad repetitiva");

        double score = score(m, blockers.size(), warnings.size(), gaps.size());
        State state = !blockers.isEmpty() ? State.BLOCKED
                : !warnings.isEmpty() || !gaps.isEmpty() ? State.REVIEW : State.READY;
        return new Result(state, blockers, warnings, gaps, score);
    }

    private static double score(Metrics m, int blockers, int warnings, int gaps) {
        double score = 1.0;
        score -= blockers * 0.22;
        score -= warnings * 0.07;
        score -= gaps * 0.04;
        score -= Math.max(0.0, 30-m.selectedFrames)*0.005;
        if (!Double.isNaN(m.medianReprojectionPx)) score -= Math.min(0.18, m.medianReprojectionPx*0.025);
        if (!Double.isNaN(m.medianParallaxDegrees)) score += Math.min(0.08, m.medianParallaxDegrees*0.015);
        return Math.max(0.0, Math.min(1.0, score));
    }

    private static int nonNegative(int value, String name) {
        if (value < 0) throw new IllegalArgumentException(name + " must be >= 0");
        return value;
    }

    private static int range(int value, int min, int max, String name) {
        if (value < min || value > max) throw new IllegalArgumentException(name + " outside range");
        return value;
    }

    private static double finiteOrNaN(double value, String name) {
        if (Double.isInfinite(value)) throw new IllegalArgumentException(name + " must not be infinite");
        return value;
    }

    private static double fractionOrNaN(double value, String name) {
        if (Double.isNaN(value)) return value;
        if (!Double.isFinite(value) || value < 0.0 || value > 1.0) throw new IllegalArgumentException(name + " must be in [0,1]");
        return value;
    }
}

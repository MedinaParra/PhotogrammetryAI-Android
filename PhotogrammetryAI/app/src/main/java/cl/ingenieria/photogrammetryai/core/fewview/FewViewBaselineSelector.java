package cl.ingenieria.photogrammetryai.core.fewview;

import cl.ingenieria.photogrammetryai.core.fewview.FewViewGeometry.CameraView;
import cl.ingenieria.photogrammetryai.core.fewview.FewViewGeometry.FeatureObservation;
import cl.ingenieria.photogrammetryai.core.fewview.FewViewGeometry.FeatureTrack;
import cl.ingenieria.photogrammetryai.core.fewview.FewViewGeometry.Ray;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/** Selects informative seed pairs before sparse reconstruction from a small image set. */
public final class FewViewBaselineSelector {
    public static final class ViewPairScore {
        private final String firstViewId;
        private final String secondViewId;
        private final int commonTrackCount;
        private final double medianTriangulationAngleDeg;
        private final double informationScore;

        private ViewPairScore(
                String firstViewId,
                String secondViewId,
                int commonTrackCount,
                double medianTriangulationAngleDeg,
                double informationScore
        ) {
            this.firstViewId = firstViewId;
            this.secondViewId = secondViewId;
            this.commonTrackCount = commonTrackCount;
            this.medianTriangulationAngleDeg = medianTriangulationAngleDeg;
            this.informationScore = informationScore;
        }

        public String firstViewId() {
            return firstViewId;
        }

        public String secondViewId() {
            return secondViewId;
        }

        public int commonTrackCount() {
            return commonTrackCount;
        }

        public double medianTriangulationAngleDeg() {
            return medianTriangulationAngleDeg;
        }

        public double informationScore() {
            return informationScore;
        }
    }

    public List<ViewPairScore> rankPairs(
            List<CameraView> views,
            List<FeatureTrack> tracks,
            double minimumUsefulAngleDeg
    ) {
        if (!Double.isFinite(minimumUsefulAngleDeg) || minimumUsefulAngleDeg < 0.0) {
            throw new IllegalArgumentException("minimumUsefulAngleDeg must be finite and non-negative");
        }
        Map<String, CameraView> viewsById = FewViewGeometry.indexViews(views);
        Objects.requireNonNull(tracks, "tracks");
        Map<PairKey, PairAccumulator> accumulators = new LinkedHashMap<>();

        for (FeatureTrack track : tracks) {
            List<FeatureObservation> observations = track.observations();
            for (int firstIndex = 0; firstIndex < observations.size(); firstIndex++) {
                for (int secondIndex = firstIndex + 1; secondIndex < observations.size(); secondIndex++) {
                    FeatureObservation firstObservation = observations.get(firstIndex);
                    FeatureObservation secondObservation = observations.get(secondIndex);
                    CameraView firstView = viewsById.get(firstObservation.viewId());
                    CameraView secondView = viewsById.get(secondObservation.viewId());
                    if (firstView == null || secondView == null) {
                        continue;
                    }
                    PairKey key = PairKey.of(firstView.id(), secondView.id());
                    PairAccumulator accumulator = accumulators.computeIfAbsent(
                            key,
                            ignored -> new PairAccumulator(key)
                    );
                    Ray firstRay = firstView.worldRay(firstObservation.u(), firstObservation.v());
                    Ray secondRay = secondView.worldRay(secondObservation.u(), secondObservation.v());
                    double angleDeg = angleDegrees(firstRay, secondRay);
                    double sourceConfidence = Math.sqrt(
                            firstObservation.confidence()
                                    * secondObservation.confidence()
                                    * firstView.poseConfidence()
                                    * secondView.poseConfidence()
                    );
                    accumulator.add(angleDeg, sourceConfidence, minimumUsefulAngleDeg);
                }
            }
        }

        List<ViewPairScore> scores = new ArrayList<>();
        for (PairAccumulator accumulator : accumulators.values()) {
            if (accumulator.commonTrackCount == 0) {
                continue;
            }
            scores.add(accumulator.toScore());
        }
        scores.sort(
                Comparator.comparingDouble(ViewPairScore::informationScore)
                        .reversed()
                        .thenComparing(
                                Comparator.comparingInt(ViewPairScore::commonTrackCount).reversed()
                        )
                        .thenComparing(
                                Comparator.comparingDouble(
                                        ViewPairScore::medianTriangulationAngleDeg
                                ).reversed()
                        )
        );
        return Collections.unmodifiableList(scores);
    }

    private static double angleDegrees(Ray first, Ray second) {
        double cosine = Math.max(-1.0, Math.min(1.0,
                first.direction().dot(second.direction())));
        return Math.toDegrees(Math.acos(cosine));
    }

    private static final class PairKey {
        private final String first;
        private final String second;

        private PairKey(String first, String second) {
            this.first = first;
            this.second = second;
        }

        private static PairKey of(String first, String second) {
            return first.compareTo(second) <= 0
                    ? new PairKey(first, second)
                    : new PairKey(second, first);
        }

        @Override
        public boolean equals(Object value) {
            if (this == value) return true;
            if (!(value instanceof PairKey)) return false;
            PairKey other = (PairKey) value;
            return first.equals(other.first) && second.equals(other.second);
        }

        @Override
        public int hashCode() {
            return 31 * first.hashCode() + second.hashCode();
        }
    }

    private static final class PairAccumulator {
        private final PairKey key;
        private final List<Double> angles = new ArrayList<>();
        private int commonTrackCount;
        private double informationScore;

        private PairAccumulator(PairKey key) {
            this.key = key;
        }

        private void add(double angleDeg, double sourceConfidence, double minimumUsefulAngleDeg) {
            commonTrackCount++;
            angles.add(angleDeg);
            double parallax = Math.sin(Math.toRadians(Math.min(90.0, angleDeg)));
            double gate = angleDeg >= minimumUsefulAngleDeg ? 1.0 : 0.15;
            informationScore += sourceConfidence * parallax * gate;
        }

        private ViewPairScore toScore() {
            Collections.sort(angles);
            double median;
            int middle = angles.size() / 2;
            if ((angles.size() & 1) == 1) {
                median = angles.get(middle);
            } else {
                median = 0.5 * (angles.get(middle - 1) + angles.get(middle));
            }
            return new ViewPairScore(
                    key.first,
                    key.second,
                    commonTrackCount,
                    median,
                    informationScore
            );
        }
    }
}

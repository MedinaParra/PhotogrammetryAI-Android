package cl.skm.pulleyai;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

/** Selects a primary two-view seed, sweeps bounded focal hypotheses and triangulates fail-closed. */
public final class ImportedSeedGeometryCore {
    private static final double[] FOCAL_LONG_EDGE_RATIOS = {
            0.55, 0.65, 0.75, 0.90, 1.05, 1.20, 1.40
    };

    private ImportedSeedGeometryCore() {}

    public static Result solve(List<Candidate> input) {
        List<Candidate> candidates = input == null
                ? new ArrayList<Candidate>() : new ArrayList<Candidate>(input);
        Collections.sort(candidates, new Comparator<Candidate>() {
            @Override public int compare(Candidate a, Candidate b) {
                int compare = Integer.compare(b.fundamental.inliers.size(),
                        a.fundamental.inliers.size());
                if (compare != 0) return compare;
                compare = Double.compare(a.fundamental.rmsPx, b.fundamental.rmsPx);
                if (compare != 0) return compare;
                compare = Integer.compare(a.leftFrame, b.leftFrame);
                return compare != 0 ? compare : Integer.compare(a.rightFrame, b.rightFrame);
            }
        });
        Trial best = null;
        int testedCandidates = 0;
        int testedFocals = 0;
        int candidateLimit = Math.min(12, candidates.size());
        for (int index = 0; index < candidateLimit; index++) {
            Candidate candidate = candidates.get(index);
            if (!candidate.valid()) continue;
            testedCandidates++;
            double longEdge = Math.max(candidate.width, candidate.height);
            for (double ratio : FOCAL_LONG_EDGE_RATIOS) {
                testedFocals++;
                double focal = longEdge * ratio;
                EssentialPoseCore.Intrinsics intrinsics = new EssentialPoseCore.Intrinsics(
                        focal, focal, candidate.width * 0.5, candidate.height * 0.5);
                EssentialPoseCore.Result pose = EssentialPoseCore.recover(
                        candidate.fundamental.matrix, candidate.pairs,
                        candidate.fundamental.inliers, intrinsics, intrinsics);
                if (!pose.solved || "WEAK".equals(pose.status)) continue;
                SparseTriangulationCore.Result cloud = SparseTriangulationCore.triangulate(
                        candidate.pairs, candidate.fundamental.inliers,
                        intrinsics, intrinsics, pose, 4.0);
                if (!cloud.solved) continue;
                Trial trial = new Trial(candidate, ratio, focal, pose, cloud);
                if (best == null || trial.score() > best.score()) best = trial;
            }
        }
        if (best == null) {
            return Result.blocked("NO_ADMISSIBLE_SEED", testedCandidates, testedFocals);
        }
        boolean ready = best.cloud.points.size() >= 12
                && best.cloud.positiveDepthRatio >= 0.55
                && Double.isFinite(best.cloud.rmsReprojectionPx)
                && best.cloud.rmsReprojectionPx <= 4.0
                && best.pose.medianParallaxDegrees >= 0.15;
        String state = ready ? "SEED_GEOMETRY_READY" : "SEED_GEOMETRY_REVIEW";
        return new Result(true, ready, state, testedCandidates, testedFocals,
                best.candidate.leftFrame, best.candidate.rightFrame,
                best.focalRatio, best.focalPx, best.pose, best.cloud);
    }

    public static final class Candidate {
        public final int leftFrame, rightFrame, width, height;
        public final String leftBand, rightBand, status;
        public final List<FundamentalMatrixCore.PointPair> pairs;
        public final FundamentalMatrixCore.Result fundamental;

        public Candidate(int leftFrame, int rightFrame, int width, int height,
                         String leftBand, String rightBand, String status,
                         List<FundamentalMatrixCore.PointPair> pairs,
                         FundamentalMatrixCore.Result fundamental) {
            this.leftFrame = leftFrame;
            this.rightFrame = rightFrame;
            this.width = width;
            this.height = height;
            this.leftBand = "HIGH".equals(leftBand) ? "HIGH" : "LOW";
            this.rightBand = "HIGH".equals(rightBand) ? "HIGH" : "LOW";
            this.status = status == null ? "WEAK" : status;
            this.pairs = Collections.unmodifiableList(pairs == null
                    ? new ArrayList<FundamentalMatrixCore.PointPair>()
                    : new ArrayList<FundamentalMatrixCore.PointPair>(pairs));
            this.fundamental = fundamental;
        }

        boolean valid() {
            return leftFrame != rightFrame && width > 64 && height > 64
                    && ("STRONG".equals(status) || "USABLE".equals(status))
                    && fundamental != null && fundamental.solved
                    && fundamental.matrix != null
                    && fundamental.inliers.size() >= 12 && pairs.size() >= 12;
        }
    }

    private static final class Trial {
        final Candidate candidate;
        final double focalRatio, focalPx;
        final EssentialPoseCore.Result pose;
        final SparseTriangulationCore.Result cloud;

        Trial(Candidate candidate, double focalRatio, double focalPx,
              EssentialPoseCore.Result pose, SparseTriangulationCore.Result cloud) {
            this.candidate = candidate;
            this.focalRatio = focalRatio;
            this.focalPx = focalPx;
            this.pose = pose;
            this.cloud = cloud;
        }

        double score() {
            double status = "STRONG".equals(cloud.status) ? 4000.0
                    : "USABLE".equals(cloud.status) ? 2000.0 : 0.0;
            return status + cloud.points.size() * 24.0
                    + pose.positiveRatio * 500.0
                    + Math.min(4.0, pose.medianParallaxDegrees) * 80.0
                    - (Double.isFinite(cloud.rmsReprojectionPx)
                    ? cloud.rmsReprojectionPx * 120.0 : 10000.0)
                    - Math.abs(focalRatio - 0.9) * 8.0;
        }
    }

    public static final class Result {
        public final boolean solved, geometryReady;
        public final String state;
        public final int testedCandidates, testedFocalHypotheses;
        public final int leftFrame, rightFrame;
        public final double focalLongEdgeRatio, focalPx;
        public final EssentialPoseCore.Result pose;
        public final SparseTriangulationCore.Result cloud;

        Result(boolean solved, boolean geometryReady, String state,
               int testedCandidates, int testedFocalHypotheses,
               int leftFrame, int rightFrame,
               double focalLongEdgeRatio, double focalPx,
               EssentialPoseCore.Result pose, SparseTriangulationCore.Result cloud) {
            this.solved = solved;
            this.geometryReady = geometryReady;
            this.state = state;
            this.testedCandidates = testedCandidates;
            this.testedFocalHypotheses = testedFocalHypotheses;
            this.leftFrame = leftFrame;
            this.rightFrame = rightFrame;
            this.focalLongEdgeRatio = focalLongEdgeRatio;
            this.focalPx = focalPx;
            this.pose = pose;
            this.cloud = cloud;
        }

        static Result blocked(String state, int candidates, int focals) {
            return new Result(false, false, state, candidates, focals,
                    -1, -1, 0.0, 0.0, null, null);
        }

        public String summary() {
            if (!solved) return "GEOMETRÍA SEMILLA BLOQUEADA · " + state
                    + " · candidatos " + testedCandidates
                    + " · focales " + testedFocalHypotheses;
            return "GEOMETRÍA SEMILLA " + (geometryReady ? "READY" : "REVIEW")
                    + " · par " + leftFrame + "-" + rightFrame
                    + " · focal " + String.format(Locale.ROOT, "%.0f px", focalPx)
                    + " (" + String.format(Locale.ROOT, "%.2f", focalLongEdgeRatio) + "× long edge)"
                    + " · pose " + pose.status
                    + " · parallax " + String.format(Locale.ROOT, "%.2f°", pose.medianParallaxDegrees)
                    + " · puntos " + cloud.points.size()
                    + " · RMS " + String.format(Locale.ROOT, "%.2f px",
                    cloud.rmsReprojectionPx);
        }

        public String canonicalJson() {
            StringBuilder json = new StringBuilder();
            json.append("{\n")
                    .append("\"schema\":\"skm-imported-seed-geometry/1\",")
                    .append("\n\"solved\":").append(solved).append(',')
                    .append("\n\"geometryReady\":").append(geometryReady).append(',')
                    .append("\n\"state\":\"").append(state).append("\",")
                    .append("\n\"testedCandidates\":").append(testedCandidates).append(',')
                    .append("\n\"testedFocalHypotheses\":").append(testedFocalHypotheses).append(',')
                    .append("\n\"leftFrame\":").append(leftFrame).append(',')
                    .append("\n\"rightFrame\":").append(rightFrame).append(',')
                    .append("\n\"focalLongEdgeRatio\":").append(focalLongEdgeRatio).append(',')
                    .append("\n\"focalPx\":").append(focalPx).append(',')
                    .append("\n\"metricScale\":false,")
                    .append("\n\"globalReconstruction\":false,")
                    .append("\n\"industrialRelease\":false");
            if (pose != null) {
                json.append(",\n\"poseStatus\":\"").append(pose.status).append("\"")
                        .append(",\n\"positiveRatio\":").append(pose.positiveRatio)
                        .append(",\n\"medianParallaxDegrees\":")
                        .append(pose.medianParallaxDegrees)
                        .append(",\n\"rotationDegrees\":").append(pose.rotationDegrees);
            }
            if (cloud != null) {
                json.append(",\n\"triangulationStatus\":\"").append(cloud.status).append("\"")
                        .append(",\n\"positiveDepthRatio\":")
                        .append(cloud.positiveDepthRatio)
                        .append(",\n\"triangulatedPoints\":").append(cloud.points.size())
                        .append(",\n\"reprojectionRmsPx\":")
                        .append(cloud.rmsReprojectionPx)
                        .append(",\n\"points\":[");
                int limit = Math.min(200, cloud.points.size());
                for (int i = 0; i < limit; i++) {
                    if (i > 0) json.append(',');
                    SparseTriangulationCore.Point3 point = cloud.points.get(i);
                    json.append("{\"x\":").append(point.x)
                            .append(",\"y\":").append(point.y)
                            .append(",\"z\":").append(point.z)
                            .append(",\"errorPx\":").append(point.reprojectionErrorPx)
                            .append(",\"parallaxDegrees\":")
                            .append(point.parallaxDegrees).append('}');
                }
                json.append(']');
            }
            return json.append("\n}").toString();
        }
    }
}

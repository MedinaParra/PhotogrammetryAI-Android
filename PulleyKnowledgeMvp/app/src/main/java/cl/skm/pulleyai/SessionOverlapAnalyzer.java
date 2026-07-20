package cl.skm.pulleyai;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Color;

import java.io.File;
import java.io.FileOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/** Calibrated pair analysis promoted into tracks, global poses and a fused sparse cloud. */
public final class SessionOverlapAnalyzer {
    private SessionOverlapAnalyzer() {
    }

    public static Report analyze(CaptureStore store, String sessionId) throws Exception {
        return analyze(CaptureStoreContextResolver.resolve(store), store, sessionId);
    }

    public static Report analyze(Context context, CaptureStore store, String sessionId) throws Exception {
        CameraIntrinsicsProvider provider = context == null ? null : new CameraIntrinsicsProvider(context);
        List<CachedFrame> frames = loadFrames(store, sessionId, provider);
        List<ViewGraphCore.Node> nodes = new ArrayList<ViewGraphCore.Node>();
        for (CachedFrame frame : frames) {
            nodes.add(new ViewGraphCore.Node(frame.source.sequence,
                    frame.source.band, frame.source.sector));
        }

        List<Pair> pairReports = new ArrayList<Pair>();
        List<ViewGraphCore.Edge> localEdges = new ArrayList<ViewGraphCore.Edge>();
        List<MultiViewTrackCore.MatchEdge> trackEdges = new ArrayList<MultiViewTrackCore.MatchEdge>();
        List<GlobalPoseGraphCore.Edge> poseEdges = new ArrayList<GlobalPoseGraphCore.Edge>();
        List<PairPointSample> pairPoints = new ArrayList<PairPointSample>();
        int missingIntrinsics = 0;
        int strong = 0;
        int usable = 0;
        int totalTriangulated = 0;
        for (CachedFrame frame : frames) if (!frame.intrinsics.available) missingIntrinsics++;

        for (int leftIndex = 0; leftIndex < frames.size(); leftIndex++) {
            for (int rightIndex = leftIndex + 1; rightIndex < frames.size(); rightIndex++) {
                CachedFrame left = frames.get(leftIndex);
                CachedFrame right = frames.get(rightIndex);
                if (!candidate(left.source, right.source)) continue;
                PairSolution solution = solvePair(leftIndex, rightIndex, left, right);
                pairReports.add(solution.report);
                boolean edgeUsable = "STRONG".equals(solution.report.status)
                        || "USABLE".equals(solution.report.status);
                boolean edgeStrong = "STRONG".equals(solution.report.status);
                localEdges.add(new ViewGraphCore.Edge(leftIndex, rightIndex,
                        edgeUsable, edgeStrong));
                if (edgeStrong) strong++;
                if (edgeUsable) usable++;
                totalTriangulated += solution.report.triangulatedPoints;
                trackEdges.addAll(solution.trackEdges);
                pairPoints.addAll(solution.points);
                if (solution.poseEdge != null) poseEdges.add(solution.poseEdge);
            }
        }

        ViewGraphCore.Result localGraph = ViewGraphCore.analyze(nodes, localEdges);
        int required = Math.max(10, Math.min(30, frames.size() / 2));
        boolean localReady = missingIntrinsics == 0 && usable >= required
                && strong >= Math.max(5, required / 3)
                && totalTriangulated >= required * 14 && localGraph.ready;

        MultiViewTrackCore.Result tracks = MultiViewTrackCore.build(trackEdges, 3);
        GlobalPoseGraphCore.Result poseGraph = GlobalPoseGraphCore.solve(frames.size(), poseEdges);
        GlobalSparseCloudCore.Result globalCloud = fuseCloud(tracks, poseGraph, pairPoints);
        CylinderFitCore.Result cylinder = fitCylinder(globalCloud);
        boolean globalReady = localReady && tracks.ready() && poseGraph.ready() && globalCloud.ready();
        String persistedStatus = globalReady ? "GLOBAL_SPARSE_READY"
                : missingIntrinsics > 0 ? "INTRINSICS_MISSING"
                : localReady ? "GLOBAL_INCOMPLETE" : localGraph.status;

        Report report = new Report(frames.size(), pairReports, strong, usable,
                required, missingIntrinsics, totalTriangulated, localGraph,
                tracks, poseGraph, globalCloud, cylinder, localReady, globalReady);
        File output = new File(store.sessionDir(sessionId), "overlap_report.json");
        try (FileOutputStream stream = new FileOutputStream(output)) {
            stream.write(report.toJson().getBytes(StandardCharsets.UTF_8));
        }
        store.saveOverlapResult(sessionId, persistedStatus, globalReady,
                usable, localGraph.components);
        return report;
    }

    private static GlobalSparseCloudCore.Result fuseCloud(MultiViewTrackCore.Result tracks,
                                                           GlobalPoseGraphCore.Result poseGraph,
                                                           List<PairPointSample> points) {
        if (!tracks.ready() || !poseGraph.ready()) {
            return GlobalSparseCloudCore.fuse(new ArrayList<GlobalSparseCloudCore.Sample>(), 2);
        }
        Map<Long, Integer> trackByObservation = new HashMap<Long, Integer>();
        for (MultiViewTrackCore.Track track : tracks.tracks) {
            for (MultiViewTrackCore.Observation observation : track.observations) {
                trackByObservation.put(key(observation.frameIndex, observation.featureIndex), track.id);
            }
        }
        List<GlobalSparseCloudCore.Sample> samples =
                new ArrayList<GlobalSparseCloudCore.Sample>();
        for (PairPointSample point : points) {
            Integer trackId = trackByObservation.get(key(point.leftFrame, point.leftFeature));
            if (trackId == null) trackId = trackByObservation.get(key(point.rightFrame, point.rightFeature));
            if (trackId == null || point.leftFrame < 0
                    || point.leftFrame >= poseGraph.poses.size()) continue;
            GlobalPoseGraphCore.Pose pose = poseGraph.poses.get(point.leftFrame);
            if (pose == null) continue;
            double[] world = toWorld(pose, point.point);
            samples.add(new GlobalSparseCloudCore.Sample(trackId,
                    world[0], world[1], world[2], point.point.reprojectionErrorPx));
        }
        return GlobalSparseCloudCore.fuse(samples, 2);
    }

    private static CylinderFitCore.Result fitCylinder(GlobalSparseCloudCore.Result cloud) {
        List<CylinderFitCore.Point> points = new ArrayList<CylinderFitCore.Point>();
        for (GlobalSparseCloudCore.FusedPoint point : cloud.points) {
            points.add(new CylinderFitCore.Point(point.x, point.y, point.z));
        }
        return CylinderFitCore.fit(points);
    }

    private static double[] toWorld(GlobalPoseGraphCore.Pose pose,
                                    SparseTriangulationCore.Point3 point) {
        double[] shifted = new double[]{point.x - pose.translation[0],
                point.y - pose.translation[1], point.z - pose.translation[2]};
        double[][] r = pose.rotation;
        return new double[]{
                r[0][0] * shifted[0] + r[1][0] * shifted[1] + r[2][0] * shifted[2],
                r[0][1] * shifted[0] + r[1][1] * shifted[1] + r[2][1] * shifted[2],
                r[0][2] * shifted[0] + r[1][2] * shifted[1] + r[2][2] * shifted[2]};
    }

    private static long key(int frame, int feature) {
        return ((long) frame << 32) ^ (feature & 0xffffffffL);
    }

    private static List<CachedFrame> loadFrames(CaptureStore store, String sessionId,
                                                 CameraIntrinsicsProvider provider) {
        List<CachedFrame> result = new ArrayList<CachedFrame>();
        for (CaptureStore.Frame frame : store.frames(sessionId)) {
            if (!"ACCEPTED".equals(frame.quality)
                    || !new File(frame.filePath).isFile()) continue;
            Gray gray = decode(frame.filePath);
            VisualFeatureCore.FeatureSet features = VisualFeatureCore.detect(
                    gray.pixels, gray.width, gray.height, 420);
            CameraIntrinsicsProvider.Resolution intrinsics = provider == null
                    ? CameraIntrinsicsProvider.Resolution.failed("CONTEXT_UNAVAILABLE")
                    : provider.resolve(frame, gray.width, gray.height);
            result.add(new CachedFrame(frame, features, intrinsics));
        }
        return result;
    }

    private static PairSolution solvePair(int leftFrameIndex, int rightFrameIndex,
                                          CachedFrame left, CachedFrame right) {
        VisualFeatureCore.PairResult descriptorMatch = VisualFeatureCore.match(
                left.features, right.features);
        List<FundamentalMatrixCore.PointPair> imagePairs = imagePairs(
                left.features, right.features, descriptorMatch);
        FundamentalMatrixCore.Result fundamental = FundamentalMatrixCore.estimate(
                imagePairs, 2.2,
                Math.max(100, Math.min(220, imagePairs.size() * 2)));

        EssentialPoseCore.Result pose = fundamental.solved
                && left.intrinsics.available && right.intrinsics.available
                ? EssentialPoseCore.recover(fundamental.matrix, imagePairs,
                fundamental.inliers, left.intrinsics.intrinsics,
                right.intrinsics.intrinsics)
                : EssentialPoseCore.Result.failed(!fundamental.solved
                ? "FUNDAMENTAL_FAILED" : "INTRINSICS_UNAVAILABLE");

        boolean poseUsable = pose.solved
                && ("STRONG".equals(pose.status)
                || "USABLE".equals(pose.status));
        SparseTriangulationCore.Result cloud = poseUsable
                ? SparseTriangulationCore.triangulate(imagePairs,
                fundamental.inliers, left.intrinsics.intrinsics,
                right.intrinsics.intrinsics, pose, 2.5)
                : SparseTriangulationCore.Result.failed("POSE_NOT_USABLE");

        AffineRansacCore.Result affine = null;
        if (!poseUsable) {
            affine = AffineRansacCore.estimate(affinePairs(
                    left.features, right.features, descriptorMatch), 3.5, 260);
        }
        boolean cloudStrong = cloud.solved && "STRONG".equals(cloud.status);
        boolean cloudUsable = cloudStrong
                || (cloud.solved && "USABLE".equals(cloud.status));
        String model = cloudUsable ? "SPARSE_TRIANGULATION"
                : poseUsable ? "ESSENTIAL_POSE_ONLY"
                : fundamental.solved ? "EPIPOLAR_ONLY"
                : affine != null && affine.solved ? "AFFINE_DIAGNOSTIC"
                : "NO_GEOMETRY";
        int inliers = fundamental.solved ? fundamental.inliers.size()
                : affine == null ? 0 : affine.inliers.size();
        double rms = fundamental.solved ? fundamental.rmsPx
                : affine == null ? Double.POSITIVE_INFINITY : affine.rmsPx;
        double ratio = fundamental.solved ? fundamental.inlierRatio
                : affine == null ? 0.0 : affine.inlierRatio;
        Pair report = new Pair(left.source.sequence, right.source.sequence,
                left.source.band, right.source.band,
                left.source.sector, right.source.sector,
                descriptorMatch.matches.size(), inliers, rms, ratio, model,
                pose.status, pose.rotationDegrees, pose.medianParallaxDegrees,
                pose.positiveRatio, pose.translation,
                cloud.status, cloud.points.size(), cloud.rmsReprojectionPx,
                cloud.positiveDepthRatio,
                cloudStrong ? "STRONG" : cloudUsable ? "USABLE" : "WEAK");

        List<MultiViewTrackCore.MatchEdge> trackEdges =
                new ArrayList<MultiViewTrackCore.MatchEdge>();
        List<PairPointSample> points = new ArrayList<PairPointSample>();
        for (SparseTriangulationCore.Point3 point : cloud.points) {
            int pairIndex = point.sourcePairIndex;
            if (pairIndex < 0 || pairIndex >= descriptorMatch.matches.size()) continue;
            VisualFeatureCore.Match match = descriptorMatch.matches.get(pairIndex);
            double confidence = Math.max(0.05,
                    ratio / (1.0 + point.reprojectionErrorPx * point.reprojectionErrorPx));
            trackEdges.add(new MultiViewTrackCore.MatchEdge(
                    leftFrameIndex, match.leftIndex,
                    rightFrameIndex, match.rightIndex, confidence));
            points.add(new PairPointSample(leftFrameIndex, match.leftIndex,
                    rightFrameIndex, match.rightIndex, point));
        }
        GlobalPoseGraphCore.Edge poseEdge = cloudUsable
                ? new GlobalPoseGraphCore.Edge(leftFrameIndex, rightFrameIndex,
                pose.rotation, pose.translation,
                Math.max(0.10, ratio * Math.min(1.0,
                        pose.medianParallaxDegrees / 2.0)
                        / (1.0 + cloud.rmsReprojectionPx)))
                : null;
        return new PairSolution(report, trackEdges, points, poseEdge);
    }

    private static boolean candidate(CaptureStore.Frame left,
                                     CaptureStore.Frame right) {
        int gap = Math.abs(left.sector - right.sector);
        gap = Math.min(gap, CoveragePlanner.SECTOR_COUNT - gap);
        return left.band.equals(right.band) ? gap >= 1 && gap <= 2 : gap <= 1;
    }

    private static Gray decode(String path) {
        BitmapFactory.Options bounds = new BitmapFactory.Options();
        bounds.inJustDecodeBounds = true;
        BitmapFactory.decodeFile(path, bounds);
        int sample = 1;
        while (Math.max(bounds.outWidth, bounds.outHeight) / sample > 640) sample *= 2;
        BitmapFactory.Options options = new BitmapFactory.Options();
        options.inSampleSize = Math.max(1, sample);
        options.inPreferredConfig = Bitmap.Config.ARGB_8888;
        Bitmap bitmap = BitmapFactory.decodeFile(path, options);
        if (bitmap == null) throw new IllegalStateException(
                "No se pudo decodificar " + path);
        try {
            int width = bitmap.getWidth();
            int height = bitmap.getHeight();
            byte[] gray = new byte[width * height];
            int[] row = new int[width];
            for (int y = 0; y < height; y++) {
                bitmap.getPixels(row, 0, width, 0, y, width, 1);
                for (int x = 0; x < width; x++) {
                    int color = row[x];
                    gray[y * width + x] = (byte) Math.round(
                            0.2126 * Color.red(color)
                                    + 0.7152 * Color.green(color)
                                    + 0.0722 * Color.blue(color));
                }
            }
            return new Gray(width, height, gray);
        } finally {
            bitmap.recycle();
        }
    }

    private static List<FundamentalMatrixCore.PointPair> imagePairs(
            VisualFeatureCore.FeatureSet left,
            VisualFeatureCore.FeatureSet right,
            VisualFeatureCore.PairResult matches) {
        List<FundamentalMatrixCore.PointPair> result =
                new ArrayList<FundamentalMatrixCore.PointPair>();
        for (VisualFeatureCore.Match match : matches.matches) {
            VisualFeatureCore.Feature a = left.features.get(match.leftIndex);
            VisualFeatureCore.Feature b = right.features.get(match.rightIndex);
            result.add(new FundamentalMatrixCore.PointPair(a.x, a.y, b.x, b.y));
        }
        return result;
    }

    private static List<AffineRansacCore.PointPair> affinePairs(
            VisualFeatureCore.FeatureSet left,
            VisualFeatureCore.FeatureSet right,
            VisualFeatureCore.PairResult matches) {
        List<AffineRansacCore.PointPair> result =
                new ArrayList<AffineRansacCore.PointPair>();
        for (VisualFeatureCore.Match match : matches.matches) {
            VisualFeatureCore.Feature a = left.features.get(match.leftIndex);
            VisualFeatureCore.Feature b = right.features.get(match.rightIndex);
            result.add(new AffineRansacCore.PointPair(a.x, a.y, b.x, b.y));
        }
        return result;
    }

    private static final class Gray {
        final int width;
        final int height;
        final byte[] pixels;
        Gray(int width, int height, byte[] pixels) {
            this.width = width;
            this.height = height;
            this.pixels = pixels;
        }
    }

    private static final class CachedFrame {
        final CaptureStore.Frame source;
        final VisualFeatureCore.FeatureSet features;
        final CameraIntrinsicsProvider.Resolution intrinsics;
        CachedFrame(CaptureStore.Frame source,
                    VisualFeatureCore.FeatureSet features,
                    CameraIntrinsicsProvider.Resolution intrinsics) {
            this.source = source;
            this.features = features;
            this.intrinsics = intrinsics;
        }
    }

    private static final class PairPointSample {
        final int leftFrame;
        final int leftFeature;
        final int rightFrame;
        final int rightFeature;
        final SparseTriangulationCore.Point3 point;
        PairPointSample(int leftFrame, int leftFeature, int rightFrame,
                        int rightFeature, SparseTriangulationCore.Point3 point) {
            this.leftFrame = leftFrame;
            this.leftFeature = leftFeature;
            this.rightFrame = rightFrame;
            this.rightFeature = rightFeature;
            this.point = point;
        }
    }

    private static final class PairSolution {
        final Pair report;
        final List<MultiViewTrackCore.MatchEdge> trackEdges;
        final List<PairPointSample> points;
        final GlobalPoseGraphCore.Edge poseEdge;
        PairSolution(Pair report, List<MultiViewTrackCore.MatchEdge> trackEdges,
                     List<PairPointSample> points, GlobalPoseGraphCore.Edge poseEdge) {
            this.report = report;
            this.trackEdges = trackEdges;
            this.points = points;
            this.poseEdge = poseEdge;
        }
    }

    public static final class Pair {
        public final int leftSequence;
        public final int rightSequence;
        public final String leftBand;
        public final String rightBand;
        public final int leftSector;
        public final int rightSector;
        public final int rawMatches;
        public final int inliers;
        public final double epipolarRmsPx;
        public final double inlierRatio;
        public final String geometryModel;
        public final String poseStatus;
        public final double rotationDegrees;
        public final double parallaxDegrees;
        public final double positiveDepthRatio;
        public final double[] translationDirection;
        public final String cloudStatus;
        public final int triangulatedPoints;
        public final double reprojectionRmsPx;
        public final double triangulationPositiveRatio;
        public final String status;

        Pair(int leftSequence, int rightSequence, String leftBand,
             String rightBand, int leftSector, int rightSector,
             int rawMatches, int inliers, double epipolarRmsPx,
             double inlierRatio, String geometryModel, String poseStatus,
             double rotationDegrees, double parallaxDegrees,
             double positiveDepthRatio, double[] translationDirection,
             String cloudStatus, int triangulatedPoints,
             double reprojectionRmsPx, double triangulationPositiveRatio,
             String status) {
            this.leftSequence = leftSequence;
            this.rightSequence = rightSequence;
            this.leftBand = leftBand;
            this.rightBand = rightBand;
            this.leftSector = leftSector;
            this.rightSector = rightSector;
            this.rawMatches = rawMatches;
            this.inliers = inliers;
            this.epipolarRmsPx = epipolarRmsPx;
            this.inlierRatio = inlierRatio;
            this.geometryModel = geometryModel;
            this.poseStatus = poseStatus;
            this.rotationDegrees = rotationDegrees;
            this.parallaxDegrees = parallaxDegrees;
            this.positiveDepthRatio = positiveDepthRatio;
            this.translationDirection = translationDirection == null
                    ? null : translationDirection.clone();
            this.cloudStatus = cloudStatus;
            this.triangulatedPoints = triangulatedPoints;
            this.reprojectionRmsPx = reprojectionRmsPx;
            this.triangulationPositiveRatio = triangulationPositiveRatio;
            this.status = status;
        }
    }

    public static final class Report {
        public final int acceptedFrames;
        public final List<Pair> pairs;
        public final int strongPairs;
        public final int usablePairs;
        public final int requiredPairs;
        public final int missingIntrinsicsFrames;
        public final int totalTriangulatedPoints;
        public final ViewGraphCore.Result localGraph;
        public final MultiViewTrackCore.Result tracks;
        public final GlobalPoseGraphCore.Result globalPoseGraph;
        public final GlobalSparseCloudCore.Result globalCloud;
        public final CylinderFitCore.Result cylinder;
        public final boolean localReady;
        public final boolean ready;

        Report(int acceptedFrames, List<Pair> pairs, int strongPairs,
               int usablePairs, int requiredPairs,
               int missingIntrinsicsFrames, int totalTriangulatedPoints,
               ViewGraphCore.Result localGraph,
               MultiViewTrackCore.Result tracks,
               GlobalPoseGraphCore.Result globalPoseGraph,
               GlobalSparseCloudCore.Result globalCloud,
               CylinderFitCore.Result cylinder,
               boolean localReady, boolean ready) {
            this.acceptedFrames = acceptedFrames;
            this.pairs = pairs;
            this.strongPairs = strongPairs;
            this.usablePairs = usablePairs;
            this.requiredPairs = requiredPairs;
            this.missingIntrinsicsFrames = missingIntrinsicsFrames;
            this.totalTriangulatedPoints = totalTriangulatedPoints;
            this.localGraph = localGraph;
            this.tracks = tracks;
            this.globalPoseGraph = globalPoseGraph;
            this.globalCloud = globalCloud;
            this.cylinder = cylinder;
            this.localReady = localReady;
            this.ready = ready;
        }

        public String summary() {
            return "Aristas " + usablePairs + "/" + requiredPairs
                    + " · triangulados locales " + totalTriangulatedPoints
                    + "\n" + tracks.summary()
                    + "\n" + globalPoseGraph.summary()
                    + "\n" + globalCloud.summary()
                    + "\nCilindro: " + cylinder.summary()
                    + (ready ? "\nNUBE DISPERSA GLOBAL APROBADA"
                    : "\nRECONSTRUCCIÓN GLOBAL INCOMPLETA");
        }

        String toJson() {
            StringBuilder json = new StringBuilder(4096 + pairs.size() * 440);
            json.append("{\n  \"schema\":\"skm-polea-global-sparse/1\",\n")
                    .append("  \"acceptedFrames\":").append(acceptedFrames).append(",\n")
                    .append("  \"strongPairs\":").append(strongPairs).append(",\n")
                    .append("  \"usablePairs\":").append(usablePairs).append(",\n")
                    .append("  \"requiredPairs\":").append(requiredPairs).append(",\n")
                    .append("  \"totalTriangulatedPoints\":")
                    .append(totalTriangulatedPoints).append(",\n")
                    .append("  \"missingIntrinsicsFrames\":")
                    .append(missingIntrinsicsFrames).append(",\n")
                    .append("  \"localGraphStatus\":\"").append(localGraph.status).append("\",\n")
                    .append("  \"localReady\":").append(localReady).append(",\n")
                    .append("  \"tracks\":{\"status\":\"").append(tracks.status)
                    .append("\",\"count\":").append(tracks.tracks.size())
                    .append(",\"fourPlus\":").append(tracks.tracksFourPlus)
                    .append(",\"coveredFrames\":").append(tracks.coveredFrames).append("},\n")
                    .append("  \"globalPoseGraph\":{\"status\":\"").append(globalPoseGraph.status)
                    .append("\",\"reached\":").append(globalPoseGraph.reachedNodes)
                    .append(",\"cycles\":").append(globalPoseGraph.trustedCycleEdges)
                    .append(",\"rotationMedianDeg\":").append(finite(globalPoseGraph.medianRotationResidualDegrees))
                    .append(",\"translationMedianDeg\":").append(finite(globalPoseGraph.medianTranslationResidualDegrees))
                    .append("},\n")
                    .append("  \"globalCloud\":{\"status\":\"").append(globalCloud.status)
                    .append("\",\"points\":").append(globalCloud.points.size())
                    .append(",\"averageSupport\":").append(finite(globalCloud.averageSupport))
                    .append(",\"rmsReprojectionPx\":").append(finite(globalCloud.rmsReprojectionPx))
                    .append("},\n")
                    .append("  \"cylinder\":{\"status\":\"").append(cylinder.status)
                    .append("\",\"diameter\":").append(finite(cylinder.radius * 2.0))
                    .append(",\"length\":").append(finite(cylinder.length))
                    .append(",\"radialRms\":").append(finite(cylinder.radialRms))
                    .append("},\n")
                    .append("  \"ready\":").append(ready)
                    .append(",\n  \"pairs\":[\n");
            for (int i = 0; i < pairs.size(); i++) {
                Pair p = pairs.get(i);
                String translation = p.translationDirection == null ? "null"
                        : String.format(Locale.ROOT, "[%.6f,%.6f,%.6f]",
                        p.translationDirection[0], p.translationDirection[1],
                        p.translationDirection[2]);
                json.append(String.format(Locale.ROOT,
                        "    {\"left\":%d,\"right\":%d,"
                                + "\"bands\":[\"%s\",\"%s\"],"
                                + "\"sectors\":[%d,%d],"
                                + "\"matches\":%d,\"inliers\":%d,"
                                + "\"epipolarRmsPx\":%.4f,"
                                + "\"inlierRatio\":%.5f,"
                                + "\"model\":\"%s\","
                                + "\"poseStatus\":\"%s\","
                                + "\"rotationDegrees\":%.5f,"
                                + "\"parallaxDegrees\":%.5f,"
                                + "\"positiveDepthRatio\":%.5f,"
                                + "\"translationDirection\":%s,"
                                + "\"cloudStatus\":\"%s\","
                                + "\"triangulatedPoints\":%d,"
                                + "\"reprojectionRmsPx\":%.5f,"
                                + "\"triangulationPositiveRatio\":%.5f,"
                                + "\"status\":\"%s\"}",
                        p.leftSequence, p.rightSequence,
                        p.leftBand, p.rightBand,
                        p.leftSector, p.rightSector,
                        p.rawMatches, p.inliers,
                        p.epipolarRmsPx, p.inlierRatio,
                        p.geometryModel, p.poseStatus,
                        p.rotationDegrees, p.parallaxDegrees,
                        p.positiveDepthRatio, translation,
                        p.cloudStatus, p.triangulatedPoints,
                        p.reprojectionRmsPx,
                        p.triangulationPositiveRatio, p.status));
                if (i + 1 < pairs.size()) json.append(',');
                json.append('\n');
            }
            json.append("  ]\n}");
            return json.toString();
        }

        private static String finite(double value) {
            return Double.isFinite(value)
                    ? String.format(Locale.ROOT, "%.8f", value) : "null";
        }
    }
}

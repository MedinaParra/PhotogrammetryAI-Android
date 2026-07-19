package cl.skm.pulleyai;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Color;

import java.io.File;
import java.io.FileOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/** Computes calibrated relative poses, a multiview graph and an auditable overlap report. */
public final class SessionOverlapAnalyzer {
    private SessionOverlapAnalyzer() {
    }

    public static Report analyze(Context context, CaptureStore store, String sessionId) throws Exception {
        CameraIntrinsicsProvider intrinsicsProvider = new CameraIntrinsicsProvider(context);
        List<CaptureStore.Frame> all = store.frames(sessionId);
        List<CachedFrame> accepted = new ArrayList<CachedFrame>();
        for (CaptureStore.Frame frame : all) {
            if (!"ACCEPTED".equals(frame.quality) || !new File(frame.filePath).isFile()) continue;
            Gray gray = decode(frame.filePath);
            VisualFeatureCore.FeatureSet features = VisualFeatureCore.detect(
                    gray.pixels, gray.width, gray.height, 420);
            CameraIntrinsicsProvider.Resolution intrinsics = intrinsicsProvider.resolve(
                    frame, gray.width, gray.height);
            accepted.add(new CachedFrame(frame, features, intrinsics));
        }

        List<Pair> pairs = new ArrayList<Pair>();
        List<ViewGraphCore.Node> graphNodes = new ArrayList<ViewGraphCore.Node>();
        for (CachedFrame cached : accepted) {
            graphNodes.add(new ViewGraphCore.Node(
                    cached.frame.sequence, cached.frame.band, cached.frame.sector));
        }
        List<ViewGraphCore.Edge> graphEdges = new ArrayList<ViewGraphCore.Edge>();
        int poseStrong = 0;
        int poseUsable = 0;
        int missingIntrinsics = 0;
        for (CachedFrame cached : accepted) if (!cached.intrinsics.available) missingIntrinsics++;

        for (int i = 0; i < accepted.size(); i++) {
            for (int j = i + 1; j < accepted.size(); j++) {
                CachedFrame left = accepted.get(i);
                CachedFrame right = accepted.get(j);
                if (!candidate(left.frame, right.frame)) continue;
                VisualFeatureCore.PairResult match = VisualFeatureCore.match(left.features, right.features);
                List<FundamentalMatrixCore.PointPair> epipolarPairs = epipolarPairs(
                        left.features, right.features, match);
                int fundamentalIterations = Math.max(100, Math.min(220, epipolarPairs.size() * 2));
                FundamentalMatrixCore.Result epipolar = FundamentalMatrixCore.estimate(
                        epipolarPairs, 2.2, fundamentalIterations);

                EssentialPoseCore.Result pose;
                if (epipolar.solved && left.intrinsics.available && right.intrinsics.available) {
                    pose = EssentialPoseCore.recover(epipolar.matrix, epipolarPairs,
                            epipolar.inliers, left.intrinsics.intrinsics, right.intrinsics.intrinsics);
                } else {
                    pose = EssentialPoseCore.Result.failed(
                            !epipolar.solved ? "FUNDAMENTAL_FAILED" : "INTRINSICS_UNAVAILABLE");
                }

                AffineRansacCore.Result affine = null;
                if (!pose.solved || "WEAK".equals(pose.status)) {
                    List<AffineRansacCore.PointPair> affinePairs = affinePairs(
                            left.features, right.features, match);
                    affine = AffineRansacCore.estimate(affinePairs, 3.5, 260);
                }

                boolean isStrong = pose.solved && "STRONG".equals(pose.status);
                boolean isUsable = isStrong || (pose.solved && "USABLE".equals(pose.status));
                String geometryModel = isUsable ? "ESSENTIAL_POSE"
                        : epipolar.solved ? "EPIPOLAR_ONLY"
                        : affine != null && affine.solved ? "AFFINE_DIAGNOSTIC"
                        : "NO_GEOMETRY";
                int inliers = isUsable ? epipolar.inliers.size()
                        : affine == null ? epipolar.inliers.size() : affine.inliers.size();
                double rms = isUsable ? epipolar.rmsPx
                        : affine == null ? epipolar.rmsPx : affine.rmsPx;
                double inlierRatio = isUsable ? epipolar.inlierRatio
                        : affine == null ? epipolar.inlierRatio : affine.inlierRatio;
                Pair pair = new Pair(left.frame.sequence, right.frame.sequence,
                        left.frame.band, right.frame.band,
                        left.frame.sector, right.frame.sector,
                        left.features.features.size(), right.features.features.size(),
                        match.matches.size(), inliers, match.medianDx, match.medianDy,
                        rms, inlierRatio, geometryModel,
                        affine == null || affine.model == null ? Double.NaN : affine.model.determinant(),
                        pose.status, pose.rotationDegrees, pose.medianParallaxDegrees,
                        pose.positiveRatio, pose.translation,
                        isStrong ? "STRONG" : isUsable ? "USABLE" : "WEAK");
                pairs.add(pair);
                graphEdges.add(new ViewGraphCore.Edge(i, j, isUsable, isStrong));
                if (isStrong) poseStrong++;
                if (isUsable) poseUsable++;
            }
        }

        ViewGraphCore.Result graph = ViewGraphCore.analyze(graphNodes, graphEdges);
        int requiredPairs = Math.max(10, Math.min(30, accepted.size() / 2));
        boolean ready = missingIntrinsics == 0
                && poseUsable >= requiredPairs
                && poseStrong >= Math.max(5, requiredPairs / 3)
                && graph.ready;
        String persistedStatus = ready ? "POSE_READY"
                : missingIntrinsics > 0 ? "INTRINSICS_MISSING"
                : graph.status;
        Report report = new Report(accepted.size(), pairs, poseStrong, poseUsable,
                requiredPairs, missingIntrinsics, graph, ready);
        File output = new File(store.sessionDir(sessionId), "overlap_report.json");
        try (FileOutputStream stream = new FileOutputStream(output)) {
            stream.write(report.toJson().getBytes(StandardCharsets.UTF_8));
        }
        store.saveOverlapResult(sessionId, persistedStatus, ready, poseUsable, graph.components);
        return report;
    }

    private static boolean candidate(CaptureStore.Frame left, CaptureStore.Frame right) {
        int sectorGap = circularGap(left.sector, right.sector);
        if (left.band.equals(right.band)) return sectorGap >= 1 && sectorGap <= 2;
        return sectorGap <= 1;
    }

    private static Gray decode(String path) {
        BitmapFactory.Options bounds = new BitmapFactory.Options();
        bounds.inJustDecodeBounds = true;
        BitmapFactory.decodeFile(path, bounds);
        int sample = 1;
        int max = Math.max(bounds.outWidth, bounds.outHeight);
        while (max / sample > 640) sample *= 2;
        BitmapFactory.Options options = new BitmapFactory.Options();
        options.inSampleSize = Math.max(1, sample);
        options.inPreferredConfig = Bitmap.Config.ARGB_8888;
        Bitmap bitmap = BitmapFactory.decodeFile(path, options);
        if (bitmap == null) throw new IllegalStateException("No se pudo decodificar " + path);
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

    private static List<AffineRansacCore.PointPair> affinePairs(
            VisualFeatureCore.FeatureSet left, VisualFeatureCore.FeatureSet right,
            VisualFeatureCore.PairResult matches) {
        List<AffineRansacCore.PointPair> pairs = new ArrayList<AffineRansacCore.PointPair>();
        for (VisualFeatureCore.Match match : matches.matches) {
            VisualFeatureCore.Feature a = left.features.get(match.leftIndex);
            VisualFeatureCore.Feature b = right.features.get(match.rightIndex);
            pairs.add(new AffineRansacCore.PointPair(a.x, a.y, b.x, b.y));
        }
        return pairs;
    }

    private static List<FundamentalMatrixCore.PointPair> epipolarPairs(
            VisualFeatureCore.FeatureSet left, VisualFeatureCore.FeatureSet right,
            VisualFeatureCore.PairResult matches) {
        List<FundamentalMatrixCore.PointPair> pairs = new ArrayList<FundamentalMatrixCore.PointPair>();
        for (VisualFeatureCore.Match match : matches.matches) {
            VisualFeatureCore.Feature a = left.features.get(match.leftIndex);
            VisualFeatureCore.Feature b = right.features.get(match.rightIndex);
            pairs.add(new FundamentalMatrixCore.PointPair(a.x, a.y, b.x, b.y));
        }
        return pairs;
    }

    private static int circularGap(int a, int b) {
        int gap = Math.abs(a - b);
        return Math.min(gap, CoveragePlanner.SECTOR_COUNT - gap);
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
        final CaptureStore.Frame frame;
        final VisualFeatureCore.FeatureSet features;
        final CameraIntrinsicsProvider.Resolution intrinsics;
        CachedFrame(CaptureStore.Frame frame, VisualFeatureCore.FeatureSet features,
                    CameraIntrinsicsProvider.Resolution intrinsics) {
            this.frame = frame;
            this.features = features;
            this.intrinsics = intrinsics;
        }
    }

    public static final class Pair {
        public final int leftSequence;
        public final int rightSequence;
        public final String leftBand;
        public final String rightBand;
        public final int leftSector;
        public final int rightSector;
        public final int leftFeatures;
        public final int rightFeatures;
        public final int matches;
        public final int geometryInliers;
        public final double medianDx;
        public final double medianDy;
        public final double geometryRmsPx;
        public final double inlierRatio;
        public final String geometryModel;
        public final double affineDeterminant;
        public final String poseStatus;
        public final double rotationDegrees;
        public final double parallaxDegrees;
        public final double positiveDepthRatio;
        public final double[] translationDirection;
        public final String status;

        Pair(int leftSequence, int rightSequence, String leftBand, String rightBand,
             int leftSector, int rightSector, int leftFeatures, int rightFeatures,
             int matches, int geometryInliers, double medianDx, double medianDy,
             double geometryRmsPx, double inlierRatio, String geometryModel,
             double affineDeterminant, String poseStatus, double rotationDegrees,
             double parallaxDegrees, double positiveDepthRatio,
             double[] translationDirection, String status) {
            this.leftSequence = leftSequence;
            this.rightSequence = rightSequence;
            this.leftBand = leftBand;
            this.rightBand = rightBand;
            this.leftSector = leftSector;
            this.rightSector = rightSector;
            this.leftFeatures = leftFeatures;
            this.rightFeatures = rightFeatures;
            this.matches = matches;
            this.geometryInliers = geometryInliers;
            this.medianDx = medianDx;
            this.medianDy = medianDy;
            this.geometryRmsPx = geometryRmsPx;
            this.inlierRatio = inlierRatio;
            this.geometryModel = geometryModel;
            this.affineDeterminant = affineDeterminant;
            this.poseStatus = poseStatus;
            this.rotationDegrees = rotationDegrees;
            this.parallaxDegrees = parallaxDegrees;
            this.positiveDepthRatio = positiveDepthRatio;
            this.translationDirection = translationDirection == null ? null : translationDirection.clone();
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
        public final ViewGraphCore.Result graph;
        public final boolean ready;

        Report(int acceptedFrames, List<Pair> pairs, int strongPairs, int usablePairs,
               int requiredPairs, int missingIntrinsicsFrames,
               ViewGraphCore.Result graph, boolean ready) {
            this.acceptedFrames = acceptedFrames;
            this.pairs = pairs;
            this.strongPairs = strongPairs;
            this.usablePairs = usablePairs;
            this.requiredPairs = requiredPairs;
            this.missingIntrinsicsFrames = missingIntrinsicsFrames;
            this.graph = graph;
            this.ready = ready;
        }

        public String summary() {
            return "Poses fuertes " + strongPairs + " · utilizables "
                    + usablePairs + "/" + requiredPairs
                    + "\nIntrínsecos faltantes: " + missingIntrinsicsFrames
                    + "\n" + graph.summary()
                    + (ready ? "\nGRAFO DE POSES APROBADO"
                    : "\nRECORRIDO NO APTO PARA TRIANGULACIÓN");
        }

        String toJson() {
            StringBuilder json = new StringBuilder(4096 + pairs.size() * 420);
            json.append("{\n  \"schema\": \"skm-polea-pose-graph/1\",\n")
                    .append("  \"acceptedFrames\": ").append(acceptedFrames).append(",\n")
                    .append("  \"strongPosePairs\": ").append(strongPairs).append(",\n")
                    .append("  \"usablePosePairs\": ").append(usablePairs).append(",\n")
                    .append("  \"requiredPairs\": ").append(requiredPairs).append(",\n")
                    .append("  \"missingIntrinsicsFrames\": ").append(missingIntrinsicsFrames).append(",\n")
                    .append("  \"ready\": ").append(ready).append(",\n")
                    .append("  \"graph\": {\"status\":\"").append(graph.status)
                    .append("\",\"components\":").append(graph.components)
                    .append(",\"largestComponent\":").append(graph.largestComponent)
                    .append(",\"nodeCount\":").append(graph.nodeCount)
                    .append(",\"strongEdges\":").append(graph.strongEdges)
                    .append(",\"crossBandEdges\":").append(graph.crossBandEdges)
                    .append(",\"isolatedSequences\":").append(graph.isolatedSequences.toString())
                    .append("},\n  \"pairs\": [\n");
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
                                + "\"features\":[%d,%d],\"matches\":%d,"
                                + "\"geometryInliers\":%d,"
                                + "\"geometryRmsPx\":%.4f,\"inlierRatio\":%.5f,"
                                + "\"geometryModel\":\"%s\","
                                + "\"poseStatus\":\"%s\","
                                + "\"rotationDegrees\":%.5f,"
                                + "\"parallaxDegrees\":%.5f,"
                                + "\"positiveDepthRatio\":%.5f,"
                                + "\"translationDirection\":%s,"
                                + "\"status\":\"%s\"}",
                        p.leftSequence, p.rightSequence, p.leftBand, p.rightBand,
                        p.leftSector, p.rightSector, p.leftFeatures, p.rightFeatures,
                        p.matches, p.geometryInliers, p.geometryRmsPx, p.inlierRatio,
                        p.geometryModel, p.poseStatus, p.rotationDegrees,
                        p.parallaxDegrees, p.positiveDepthRatio, translation, p.status));
                if (i + 1 < pairs.size()) json.append(',');
                json.append('\n');
            }
            json.append("  ]\n}");
            return json.toString();
        }
    }
}

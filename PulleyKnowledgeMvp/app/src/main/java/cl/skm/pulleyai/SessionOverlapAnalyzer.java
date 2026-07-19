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

/** Calibrated two-view analysis, pose-graph qualification and auditable reporting. */
public final class SessionOverlapAnalyzer {
    private SessionOverlapAnalyzer() {
    }

    /** Compatibility entry point retained while capture storage is refactored behind a repository. */
    public static Report analyze(CaptureStore store, String sessionId) throws Exception {
        return analyze(CaptureStoreContextResolver.resolve(store), store, sessionId);
    }

    public static Report analyze(Context context, CaptureStore store, String sessionId) throws Exception {
        CameraIntrinsicsProvider provider = context == null ? null : new CameraIntrinsicsProvider(context);
        List<CachedFrame> frames = loadFrames(store, sessionId, provider);
        List<ViewGraphCore.Node> nodes = new ArrayList<ViewGraphCore.Node>();
        for (CachedFrame frame : frames) {
            nodes.add(new ViewGraphCore.Node(frame.source.sequence, frame.source.band, frame.source.sector));
        }

        List<Pair> pairReports = new ArrayList<Pair>();
        List<ViewGraphCore.Edge> edges = new ArrayList<ViewGraphCore.Edge>();
        int missingIntrinsics = 0;
        int strong = 0;
        int usable = 0;
        for (CachedFrame frame : frames) if (!frame.intrinsics.available) missingIntrinsics++;

        for (int leftIndex = 0; leftIndex < frames.size(); leftIndex++) {
            for (int rightIndex = leftIndex + 1; rightIndex < frames.size(); rightIndex++) {
                CachedFrame left = frames.get(leftIndex);
                CachedFrame right = frames.get(rightIndex);
                if (!candidate(left.source, right.source)) continue;
                Pair pair = solvePair(left, right);
                pairReports.add(pair);
                boolean edgeUsable = "STRONG".equals(pair.status) || "USABLE".equals(pair.status);
                boolean edgeStrong = "STRONG".equals(pair.status);
                edges.add(new ViewGraphCore.Edge(leftIndex, rightIndex, edgeUsable, edgeStrong));
                if (edgeStrong) strong++;
                if (edgeUsable) usable++;
            }
        }

        ViewGraphCore.Result graph = ViewGraphCore.analyze(nodes, edges);
        int required = Math.max(10, Math.min(30, frames.size() / 2));
        boolean ready = missingIntrinsics == 0 && usable >= required
                && strong >= Math.max(5, required / 3) && graph.ready;
        String persistedStatus = ready ? "POSE_READY"
                : missingIntrinsics > 0 ? "INTRINSICS_MISSING" : graph.status;
        Report report = new Report(frames.size(), pairReports, strong, usable,
                required, missingIntrinsics, graph, ready);
        File output = new File(store.sessionDir(sessionId), "overlap_report.json");
        try (FileOutputStream stream = new FileOutputStream(output)) {
            stream.write(report.toJson().getBytes(StandardCharsets.UTF_8));
        }
        store.saveOverlapResult(sessionId, persistedStatus, ready, usable, graph.components);
        return report;
    }

    private static List<CachedFrame> loadFrames(CaptureStore store, String sessionId,
                                                 CameraIntrinsicsProvider provider) {
        List<CachedFrame> result = new ArrayList<CachedFrame>();
        for (CaptureStore.Frame frame : store.frames(sessionId)) {
            if (!"ACCEPTED".equals(frame.quality) || !new File(frame.filePath).isFile()) continue;
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

    private static Pair solvePair(CachedFrame left, CachedFrame right) {
        VisualFeatureCore.PairResult descriptorMatch = VisualFeatureCore.match(
                left.features, right.features);
        List<FundamentalMatrixCore.PointPair> imagePairs = imagePairs(
                left.features, right.features, descriptorMatch);
        FundamentalMatrixCore.Result fundamental = FundamentalMatrixCore.estimate(
                imagePairs, 2.2, Math.max(100, Math.min(220, imagePairs.size() * 2)));

        EssentialPoseCore.Result pose = fundamental.solved
                && left.intrinsics.available && right.intrinsics.available
                ? EssentialPoseCore.recover(fundamental.matrix, imagePairs, fundamental.inliers,
                left.intrinsics.intrinsics, right.intrinsics.intrinsics)
                : EssentialPoseCore.Result.failed(!fundamental.solved
                ? "FUNDAMENTAL_FAILED" : "INTRINSICS_UNAVAILABLE");

        AffineRansacCore.Result affine = null;
        if (!pose.solved || "WEAK".equals(pose.status)) {
            affine = AffineRansacCore.estimate(affinePairs(
                    left.features, right.features, descriptorMatch), 3.5, 260);
        }
        boolean poseUsable = pose.solved
                && ("STRONG".equals(pose.status) || "USABLE".equals(pose.status));
        String model = poseUsable ? "ESSENTIAL_POSE"
                : fundamental.solved ? "EPIPOLAR_ONLY"
                : affine != null && affine.solved ? "AFFINE_DIAGNOSTIC" : "NO_GEOMETRY";
        int inliers = poseUsable ? fundamental.inliers.size()
                : affine != null ? affine.inliers.size() : fundamental.inliers.size();
        double rms = poseUsable ? fundamental.rmsPx
                : affine != null ? affine.rmsPx : fundamental.rmsPx;
        double ratio = poseUsable ? fundamental.inlierRatio
                : affine != null ? affine.inlierRatio : fundamental.inlierRatio;
        return new Pair(left.source.sequence, right.source.sequence,
                left.source.band, right.source.band, left.source.sector, right.source.sector,
                descriptorMatch.matches.size(), inliers, rms, ratio, model,
                pose.status, pose.rotationDegrees, pose.medianParallaxDegrees,
                pose.positiveRatio, pose.translation,
                poseUsable ? pose.status : "WEAK");
    }

    private static boolean candidate(CaptureStore.Frame left, CaptureStore.Frame right) {
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
                            0.2126 * Color.red(color) + 0.7152 * Color.green(color)
                                    + 0.0722 * Color.blue(color));
                }
            }
            return new Gray(width, height, gray);
        } finally {
            bitmap.recycle();
        }
    }

    private static List<FundamentalMatrixCore.PointPair> imagePairs(
            VisualFeatureCore.FeatureSet left, VisualFeatureCore.FeatureSet right,
            VisualFeatureCore.PairResult matches) {
        List<FundamentalMatrixCore.PointPair> result = new ArrayList<FundamentalMatrixCore.PointPair>();
        for (VisualFeatureCore.Match match : matches.matches) {
            VisualFeatureCore.Feature a = left.features.get(match.leftIndex);
            VisualFeatureCore.Feature b = right.features.get(match.rightIndex);
            result.add(new FundamentalMatrixCore.PointPair(a.x, a.y, b.x, b.y));
        }
        return result;
    }

    private static List<AffineRansacCore.PointPair> affinePairs(
            VisualFeatureCore.FeatureSet left, VisualFeatureCore.FeatureSet right,
            VisualFeatureCore.PairResult matches) {
        List<AffineRansacCore.PointPair> result = new ArrayList<AffineRansacCore.PointPair>();
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
        CachedFrame(CaptureStore.Frame source, VisualFeatureCore.FeatureSet features,
                    CameraIntrinsicsProvider.Resolution intrinsics) {
            this.source = source;
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
        public final int rawMatches;
        public final int inliers;
        public final double rmsPx;
        public final double inlierRatio;
        public final String geometryModel;
        public final String poseStatus;
        public final double rotationDegrees;
        public final double parallaxDegrees;
        public final double positiveDepthRatio;
        public final double[] translationDirection;
        public final String status;

        Pair(int leftSequence, int rightSequence, String leftBand, String rightBand,
             int leftSector, int rightSector, int rawMatches, int inliers,
             double rmsPx, double inlierRatio, String geometryModel, String poseStatus,
             double rotationDegrees, double parallaxDegrees, double positiveDepthRatio,
             double[] translationDirection, String status) {
            this.leftSequence = leftSequence;
            this.rightSequence = rightSequence;
            this.leftBand = leftBand;
            this.rightBand = rightBand;
            this.leftSector = leftSector;
            this.rightSector = rightSector;
            this.rawMatches = rawMatches;
            this.inliers = inliers;
            this.rmsPx = rmsPx;
            this.inlierRatio = inlierRatio;
            this.geometryModel = geometryModel;
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
            StringBuilder json = new StringBuilder(2048 + pairs.size() * 350);
            json.append("{\n  \"schema\":\"skm-polea-pose-graph/1\",\n")
                    .append("  \"acceptedFrames\":").append(acceptedFrames).append(",\n")
                    .append("  \"strongPosePairs\":").append(strongPairs).append(",\n")
                    .append("  \"usablePosePairs\":").append(usablePairs).append(",\n")
                    .append("  \"requiredPairs\":").append(requiredPairs).append(",\n")
                    .append("  \"missingIntrinsicsFrames\":").append(missingIntrinsicsFrames).append(",\n")
                    .append("  \"graphStatus\":\"").append(graph.status).append("\",\n")
                    .append("  \"ready\":").append(ready).append(",\n  \"pairs\":[\n");
            for (int i = 0; i < pairs.size(); i++) {
                Pair p = pairs.get(i);
                String translation = p.translationDirection == null ? "null"
                        : String.format(Locale.ROOT, "[%.6f,%.6f,%.6f]",
                        p.translationDirection[0], p.translationDirection[1], p.translationDirection[2]);
                json.append(String.format(Locale.ROOT,
                        "    {\"left\":%d,\"right\":%d,\"bands\":[\"%s\",\"%s\"],"
                                + "\"sectors\":[%d,%d],\"matches\":%d,\"inliers\":%d,"
                                + "\"rmsPx\":%.4f,\"inlierRatio\":%.5f,"
                                + "\"model\":\"%s\",\"poseStatus\":\"%s\","
                                + "\"rotationDegrees\":%.5f,\"parallaxDegrees\":%.5f,"
                                + "\"positiveDepthRatio\":%.5f,\"translationDirection\":%s,"
                                + "\"status\":\"%s\"}",
                        p.leftSequence, p.rightSequence, p.leftBand, p.rightBand,
                        p.leftSector, p.rightSector, p.rawMatches, p.inliers,
                        p.rmsPx, p.inlierRatio, p.geometryModel, p.poseStatus,
                        p.rotationDegrees, p.parallaxDegrees, p.positiveDepthRatio,
                        translation, p.status));
                if (i + 1 < pairs.size()) json.append(',');
                json.append('\n');
            }
            json.append("  ]\n}");
            return json.toString();
        }
    }
}

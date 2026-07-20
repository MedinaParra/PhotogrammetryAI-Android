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

/** Promotes calibrated two-view results into tracks, global poses, a fused cloud and a metric shell. */
public final class SessionOverlapAnalyzer {
    private SessionOverlapAnalyzer() {}

    public static Report analyze(CaptureStore store, String sessionId) throws Exception {
        return analyze(CaptureStoreContextResolver.resolve(store), store, sessionId);
    }

    public static Report analyze(Context context, CaptureStore store, String sessionId) throws Exception {
        CaptureStore.Session session = store.getSession(sessionId);
        Double measuredLengthMm = session == null ? null : session.shellLengthMm;
        CameraIntrinsicsProvider provider = context == null ? null : new CameraIntrinsicsProvider(context);
        List<CachedFrame> frames = loadFrames(store, sessionId, provider);
        List<ViewGraphCore.Node> localNodes = new ArrayList<ViewGraphCore.Node>();
        for (CachedFrame frame : frames) {
            localNodes.add(new ViewGraphCore.Node(
                    frame.source.sequence, frame.source.band, frame.source.sector));
        }

        List<Pair> pairs = new ArrayList<Pair>();
        List<ViewGraphCore.Edge> localEdges = new ArrayList<ViewGraphCore.Edge>();
        List<MultiViewTrackCore.MatchEdge> trackEdges = new ArrayList<MultiViewTrackCore.MatchEdge>();
        List<GlobalPoseGraphCore.Edge> poseEdges = new ArrayList<GlobalPoseGraphCore.Edge>();
        List<PairPoint> pairPoints = new ArrayList<PairPoint>();
        int missingIntrinsics = 0;
        int strongPairs = 0;
        int usablePairs = 0;
        int localPointCount = 0;
        for (CachedFrame frame : frames) if (!frame.intrinsics.available) missingIntrinsics++;

        for (int left=0;left<frames.size();left++) {
            for (int right=left+1;right<frames.size();right++) {
                if (!candidate(frames.get(left).source, frames.get(right).source)) continue;
                PairSolution solution = solvePair(left, right, frames.get(left), frames.get(right));
                pairs.add(solution.report);
                boolean usable = solution.report.usable();
                boolean strong = "STRONG".equals(solution.report.status);
                localEdges.add(new ViewGraphCore.Edge(left, right, usable, strong));
                if (usable) usablePairs++;
                if (strong) strongPairs++;
                localPointCount += solution.report.triangulatedPoints;
                trackEdges.addAll(solution.trackEdges);
                pairPoints.addAll(solution.points);
                if (solution.poseEdge != null) poseEdges.add(solution.poseEdge);
            }
        }

        ViewGraphCore.Result localGraph = ViewGraphCore.analyze(localNodes, localEdges);
        int requiredPairs = Math.max(10, Math.min(30, frames.size()/2));
        boolean localReady = missingIntrinsics == 0
                && usablePairs >= requiredPairs
                && strongPairs >= Math.max(5, requiredPairs/3)
                && localPointCount >= requiredPairs*14
                && localGraph.ready;

        MultiViewTrackCore.Result tracks = MultiViewTrackCore.build(trackEdges, 3);
        GlobalPoseGraphCore.Result poseGraph = GlobalPoseGraphCore.solve(frames.size(), poseEdges);
        GlobalSparseCloudCore.Result cloud = fuseCloud(tracks, poseGraph, pairPoints);
        PulleyShellRansacCore.Result shell = fitShell(cloud);
        PulleyMetricScaleCore.Result metric = PulleyMetricScaleCore.resolve(shell, measuredLengthMm);

        boolean globalReady = localReady && tracks.ready() && poseGraph.ready() && cloud.ready();
        boolean modelReady = globalReady && shell.ready() && metric.ready();
        String status = modelReady ? "METRIC_SHELL_READY"
                : globalReady ? "GLOBAL_SPARSE_READY"
                : missingIntrinsics > 0 ? "INTRINSICS_MISSING"
                : localReady ? "GLOBAL_INCOMPLETE" : localGraph.status;

        Report report = new Report(frames.size(), pairs, strongPairs, usablePairs,
                requiredPairs, missingIntrinsics, localPointCount, localGraph,
                tracks, poseGraph, cloud, shell, metric, localReady, globalReady, modelReady);
        File output = new File(store.sessionDir(sessionId), "overlap_report.json");
        try (FileOutputStream stream = new FileOutputStream(output)) {
            stream.write(report.toJson().getBytes(StandardCharsets.UTF_8));
        }
        store.saveOverlapResult(sessionId, status, globalReady, usablePairs, localGraph.components);
        return report;
    }

    private static PairSolution solvePair(int leftFrame, int rightFrame,
                                          CachedFrame left, CachedFrame right) {
        VisualFeatureCore.PairResult descriptors = VisualFeatureCore.match(
                left.features, right.features);
        List<FundamentalMatrixCore.PointPair> observations = imagePairs(
                left.features, right.features, descriptors);
        FundamentalMatrixCore.Result fundamental = FundamentalMatrixCore.estimate(
                observations, 2.2, Math.max(100, Math.min(220, observations.size()*2)));
        EssentialPoseCore.Result pose = fundamental.solved
                && left.intrinsics.available && right.intrinsics.available
                ? EssentialPoseCore.recover(fundamental.matrix, observations,
                fundamental.inliers, left.intrinsics.intrinsics, right.intrinsics.intrinsics)
                : EssentialPoseCore.Result.failed(!fundamental.solved
                ? "FUNDAMENTAL_FAILED" : "INTRINSICS_UNAVAILABLE");
        boolean poseUsable = pose.solved
                && ("STRONG".equals(pose.status) || "USABLE".equals(pose.status));
        SparseTriangulationCore.Result cloud = poseUsable
                ? SparseTriangulationCore.triangulate(observations, fundamental.inliers,
                left.intrinsics.intrinsics, right.intrinsics.intrinsics, pose, 2.5)
                : SparseTriangulationCore.Result.failed("POSE_NOT_USABLE");
        boolean cloudUsable = cloud.solved
                && ("STRONG".equals(cloud.status) || "USABLE".equals(cloud.status));
        String status = "STRONG".equals(cloud.status) ? "STRONG"
                : cloudUsable ? "USABLE" : "WEAK";
        Pair report = new Pair(left.source.sequence, right.source.sequence,
                descriptors.matches.size(),
                fundamental.solved ? fundamental.inliers.size() : 0,
                fundamental.solved ? fundamental.rmsPx : Double.POSITIVE_INFINITY,
                pose.status, pose.rotationDegrees, pose.medianParallaxDegrees,
                cloud.status, cloud.points.size(), cloud.rmsReprojectionPx, status);

        List<MultiViewTrackCore.MatchEdge> trackEdges =
                new ArrayList<MultiViewTrackCore.MatchEdge>();
        List<PairPoint> points = new ArrayList<PairPoint>();
        for (SparseTriangulationCore.Point3 point : cloud.points) {
            int matchIndex = point.sourcePairIndex;
            if (matchIndex < 0 || matchIndex >= descriptors.matches.size()) continue;
            VisualFeatureCore.Match match = descriptors.matches.get(matchIndex);
            double confidence = Math.max(0.05, fundamental.inlierRatio
                    / (1.0 + point.reprojectionErrorPx*point.reprojectionErrorPx));
            trackEdges.add(new MultiViewTrackCore.MatchEdge(
                    leftFrame, match.leftIndex, rightFrame, match.rightIndex, confidence));
            points.add(new PairPoint(leftFrame, match.leftIndex,
                    rightFrame, match.rightIndex, point));
        }
        GlobalPoseGraphCore.Edge poseEdge = cloudUsable
                ? new GlobalPoseGraphCore.Edge(leftFrame, rightFrame,
                pose.rotation, pose.translation, Math.max(0.10,
                fundamental.inlierRatio * Math.min(1.0, pose.medianParallaxDegrees/2.0)
                        / (1.0 + cloud.rmsReprojectionPx)))
                : null;
        return new PairSolution(report, trackEdges, points, poseEdge);
    }

    private static GlobalSparseCloudCore.Result fuseCloud(
            MultiViewTrackCore.Result tracks,
            GlobalPoseGraphCore.Result poses,
            List<PairPoint> points) {
        if (!tracks.ready() || !poses.ready()) {
            return GlobalSparseCloudCore.fuse(
                    new ArrayList<GlobalSparseCloudCore.Sample>(), 2);
        }
        Map<Long,Integer> observationTracks = new HashMap<Long,Integer>();
        for (MultiViewTrackCore.Track track : tracks.tracks) {
            for (MultiViewTrackCore.Observation observation : track.observations) {
                observationTracks.put(key(observation.frameIndex, observation.featureIndex), track.id);
            }
        }
        List<GlobalSparseCloudCore.Sample> samples =
                new ArrayList<GlobalSparseCloudCore.Sample>();
        for (PairPoint point : points) {
            Integer track = observationTracks.get(key(point.leftFrame, point.leftFeature));
            if (track == null) track = observationTracks.get(key(point.rightFrame, point.rightFeature));
            if (track == null || point.leftFrame >= poses.poses.size()) continue;
            GlobalPoseGraphCore.Pose pose = poses.poses.get(point.leftFrame);
            if (pose == null) continue;
            double[] world = toWorld(pose, point.point);
            samples.add(new GlobalSparseCloudCore.Sample(track,
                    world[0], world[1], world[2], point.point.reprojectionErrorPx));
        }
        return GlobalSparseCloudCore.fuse(samples, 2);
    }

    private static PulleyShellRansacCore.Result fitShell(GlobalSparseCloudCore.Result cloud) {
        List<PulleyShellRansacCore.Point> points =
                new ArrayList<PulleyShellRansacCore.Point>();
        for (GlobalSparseCloudCore.FusedPoint point : cloud.points) {
            points.add(new PulleyShellRansacCore.Point(point.x, point.y, point.z));
        }
        return PulleyShellRansacCore.fit(points, 900);
    }

    private static double[] toWorld(GlobalPoseGraphCore.Pose pose,
                                    SparseTriangulationCore.Point3 point) {
        double x = point.x-pose.translation[0];
        double y = point.y-pose.translation[1];
        double z = point.z-pose.translation[2];
        double[][] r = pose.rotation;
        return new double[]{r[0][0]*x+r[1][0]*y+r[2][0]*z,
                r[0][1]*x+r[1][1]*y+r[2][1]*z,
                r[0][2]*x+r[1][2]*y+r[2][2]*z};
    }

    private static List<CachedFrame> loadFrames(CaptureStore store, String sessionId,
                                                 CameraIntrinsicsProvider provider) {
        List<CachedFrame> result = new ArrayList<CachedFrame>();
        for (CaptureStore.Frame frame : store.frames(sessionId)) {
            if (!"ACCEPTED".equals(frame.quality) || !new File(frame.filePath).isFile()) continue;
            Gray gray = decode(frame.filePath);
            VisualFeatureCore.FeatureSet features =
                    VisualFeatureCore.detect(gray.pixels, gray.width, gray.height, 420);
            CameraIntrinsicsProvider.Resolution intrinsics = provider == null
                    ? CameraIntrinsicsProvider.Resolution.failed("CONTEXT_UNAVAILABLE")
                    : provider.resolve(frame, gray.width, gray.height);
            result.add(new CachedFrame(frame, features, intrinsics));
        }
        return result;
    }

    private static Gray decode(String path) {
        BitmapFactory.Options bounds = new BitmapFactory.Options();
        bounds.inJustDecodeBounds = true;
        BitmapFactory.decodeFile(path, bounds);
        int sample = 1;
        while (Math.max(bounds.outWidth, bounds.outHeight)/sample > 640) sample*=2;
        BitmapFactory.Options options = new BitmapFactory.Options();
        options.inSampleSize = Math.max(1, sample);
        options.inPreferredConfig = Bitmap.Config.ARGB_8888;
        Bitmap bitmap = BitmapFactory.decodeFile(path, options);
        if (bitmap == null) throw new IllegalStateException("No se pudo decodificar " + path);
        try {
            int width=bitmap.getWidth(),height=bitmap.getHeight();
            byte[] gray=new byte[width*height];
            int[] row=new int[width];
            for(int y=0;y<height;y++) {
                bitmap.getPixels(row,0,width,0,y,width,1);
                for(int x=0;x<width;x++) {
                    int color=row[x];
                    gray[y*width+x]=(byte)Math.round(0.2126*Color.red(color)
                            +0.7152*Color.green(color)+0.0722*Color.blue(color));
                }
            }
            return new Gray(width,height,gray);
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
            VisualFeatureCore.Feature a=left.features.get(match.leftIndex);
            VisualFeatureCore.Feature b=right.features.get(match.rightIndex);
            result.add(new FundamentalMatrixCore.PointPair(a.x,a.y,b.x,b.y));
        }
        return result;
    }

    private static boolean candidate(CaptureStore.Frame left, CaptureStore.Frame right) {
        int gap=Math.abs(left.sector-right.sector);
        gap=Math.min(gap,CoveragePlanner.SECTOR_COUNT-gap);
        return left.band.equals(right.band) ? gap>=1 && gap<=2 : gap<=1;
    }

    private static long key(int frame,int feature) {
        return ((long)frame<<32)^(feature&0xffffffffL);
    }

    private static final class Gray {
        final int width,height; final byte[] pixels;
        Gray(int width,int height,byte[] pixels){this.width=width;this.height=height;this.pixels=pixels;}
    }
    private static final class CachedFrame {
        final CaptureStore.Frame source;
        final VisualFeatureCore.FeatureSet features;
        final CameraIntrinsicsProvider.Resolution intrinsics;
        CachedFrame(CaptureStore.Frame source,VisualFeatureCore.FeatureSet features,
                    CameraIntrinsicsProvider.Resolution intrinsics) {
            this.source=source;this.features=features;this.intrinsics=intrinsics;
        }
    }
    private static final class PairPoint {
        final int leftFrame,leftFeature,rightFrame,rightFeature;
        final SparseTriangulationCore.Point3 point;
        PairPoint(int leftFrame,int leftFeature,int rightFrame,int rightFeature,
                  SparseTriangulationCore.Point3 point) {
            this.leftFrame=leftFrame;this.leftFeature=leftFeature;
            this.rightFrame=rightFrame;this.rightFeature=rightFeature;this.point=point;
        }
    }
    private static final class PairSolution {
        final Pair report;
        final List<MultiViewTrackCore.MatchEdge> trackEdges;
        final List<PairPoint> points;
        final GlobalPoseGraphCore.Edge poseEdge;
        PairSolution(Pair report,List<MultiViewTrackCore.MatchEdge> trackEdges,
                     List<PairPoint> points,GlobalPoseGraphCore.Edge poseEdge) {
            this.report=report;this.trackEdges=trackEdges;this.points=points;this.poseEdge=poseEdge;
        }
    }

    public static final class Pair {
        public final int leftSequence,rightSequence,rawMatches,inliers,triangulatedPoints;
        public final double epipolarRmsPx,rotationDegrees,parallaxDegrees,reprojectionRmsPx;
        public final String poseStatus,cloudStatus,status;
        Pair(int leftSequence,int rightSequence,int rawMatches,int inliers,
             double epipolarRmsPx,String poseStatus,double rotationDegrees,
             double parallaxDegrees,String cloudStatus,int triangulatedPoints,
             double reprojectionRmsPx,String status) {
            this.leftSequence=leftSequence;this.rightSequence=rightSequence;
            this.rawMatches=rawMatches;this.inliers=inliers;this.epipolarRmsPx=epipolarRmsPx;
            this.poseStatus=poseStatus;this.rotationDegrees=rotationDegrees;
            this.parallaxDegrees=parallaxDegrees;this.cloudStatus=cloudStatus;
            this.triangulatedPoints=triangulatedPoints;
            this.reprojectionRmsPx=reprojectionRmsPx;this.status=status;
        }
        boolean usable(){return "STRONG".equals(status)||"USABLE".equals(status);}
    }

    public static final class Report {
        public final int acceptedFrames,strongPairs,usablePairs,requiredPairs;
        public final int missingIntrinsicsFrames,totalTriangulatedPoints;
        public final List<Pair> pairs;
        public final ViewGraphCore.Result localGraph;
        public final MultiViewTrackCore.Result tracks;
        public final GlobalPoseGraphCore.Result globalPoseGraph;
        public final GlobalSparseCloudCore.Result globalCloud;
        public final PulleyShellRansacCore.Result shell;
        public final PulleyMetricScaleCore.Result metric;
        public final boolean localReady,ready,modelReady;

        Report(int acceptedFrames,List<Pair> pairs,int strongPairs,int usablePairs,
               int requiredPairs,int missingIntrinsicsFrames,int totalTriangulatedPoints,
               ViewGraphCore.Result localGraph,MultiViewTrackCore.Result tracks,
               GlobalPoseGraphCore.Result globalPoseGraph,
               GlobalSparseCloudCore.Result globalCloud,
               PulleyShellRansacCore.Result shell,PulleyMetricScaleCore.Result metric,
               boolean localReady,boolean ready,boolean modelReady) {
            this.acceptedFrames=acceptedFrames;this.pairs=pairs;this.strongPairs=strongPairs;
            this.usablePairs=usablePairs;this.requiredPairs=requiredPairs;
            this.missingIntrinsicsFrames=missingIntrinsicsFrames;
            this.totalTriangulatedPoints=totalTriangulatedPoints;this.localGraph=localGraph;
            this.tracks=tracks;this.globalPoseGraph=globalPoseGraph;this.globalCloud=globalCloud;
            this.shell=shell;this.metric=metric;this.localReady=localReady;
            this.ready=ready;this.modelReady=modelReady;
        }

        public String summary() {
            return "Aristas "+usablePairs+"/"+requiredPairs+" · puntos locales "
                    +totalTriangulatedPoints+"\n"+tracks.summary()+"\n"
                    +globalPoseGraph.summary()+"\n"+globalCloud.summary()+"\n"
                    +shell.summary()+"\n"+metric.summary()
                    +(modelReady?"\nMANTO MÉTRICO APROBADO"
                    :ready?"\nNUBE GLOBAL APROBADA; FALTA MODELO MÉTRICO"
                    :"\nRECONSTRUCCIÓN GLOBAL INCOMPLETA");
        }

        String toJson() {
            StringBuilder json=new StringBuilder(4096+pairs.size()*220);
            json.append("{\n\"schema\":\"skm-polea-metric-shell/1\",")
                    .append("\n\"acceptedFrames\":").append(acceptedFrames)
                    .append(",\n\"strongPairs\":").append(strongPairs)
                    .append(",\n\"usablePairs\":").append(usablePairs)
                    .append(",\n\"requiredPairs\":").append(requiredPairs)
                    .append(",\n\"localPoints\":").append(totalTriangulatedPoints)
                    .append(",\n\"missingIntrinsics\":").append(missingIntrinsicsFrames)
                    .append(",\n\"localGraph\":\"").append(localGraph.status).append("\"")
                    .append(",\n\"tracks\":{\"status\":\"").append(tracks.status)
                    .append("\",\"count\":").append(tracks.tracks.size())
                    .append(",\"fourPlus\":").append(tracks.tracksFourPlus).append("}")
                    .append(",\n\"poseGraph\":{\"status\":\"").append(globalPoseGraph.status)
                    .append("\",\"reached\":").append(globalPoseGraph.reachedNodes)
                    .append(",\"cycles\":").append(globalPoseGraph.trustedCycleEdges).append("}")
                    .append(",\n\"cloud\":{\"status\":\"").append(globalCloud.status)
                    .append("\",\"points\":").append(globalCloud.points.size())
                    .append(",\"support\":").append(number(globalCloud.averageSupport))
                    .append(",\"reprojectionRmsPx\":").append(number(globalCloud.rmsReprojectionPx)).append("}")
                    .append(",\n\"shell\":{\"status\":\"").append(shell.status)
                    .append("\",\"diameterUnits\":").append(number(shell.radius*2.0))
                    .append(",\"lengthUnits\":").append(number(shell.length))
                    .append(",\"inliers\":").append(shell.inlierIndices.size()).append("}")
                    .append(",\n\"metric\":{\"status\":\"").append(metric.status)
                    .append("\",\"millimetresPerUnit\":").append(number(metric.millimetresPerUnit))
                    .append(",\"shellDiameterMm\":").append(number(metric.shellDiameterMm))
                    .append(",\"diameterUncertaintyMm\":").append(number(metric.diameterUncertaintyMm))
                    .append(",\"confidence\":").append(number(metric.confidence)).append("}")
                    .append(",\n\"ready\":").append(ready)
                    .append(",\n\"modelReady\":").append(modelReady)
                    .append(",\n\"pairs\":[\n");
            for(int i=0;i<pairs.size();i++) {
                Pair p=pairs.get(i);
                json.append(String.format(Locale.ROOT,
                        "{\"left\":%d,\"right\":%d,\"matches\":%d,\"inliers\":%d,"
                                +"\"epipolarRmsPx\":%.4f,\"pose\":\"%s\","
                                +"\"rotationDeg\":%.4f,\"parallaxDeg\":%.4f,"
                                +"\"cloud\":\"%s\",\"points\":%d,"
                                +"\"reprojectionRmsPx\":%.4f,\"status\":\"%s\"}",
                        p.leftSequence,p.rightSequence,p.rawMatches,p.inliers,
                        p.epipolarRmsPx,p.poseStatus,p.rotationDegrees,p.parallaxDegrees,
                        p.cloudStatus,p.triangulatedPoints,p.reprojectionRmsPx,p.status));
                if(i+1<pairs.size())json.append(',');
                json.append('\n');
            }
            json.append("]\n}");
            return json.toString();
        }

        private static String number(double value) {
            return Double.isFinite(value)
                    ? String.format(Locale.ROOT,"%.8f",value) : "null";
        }
    }
}

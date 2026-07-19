import cl.ingenieria.photogrammetryai.core.foundation.CoreMath.RigidPose;
import cl.ingenieria.photogrammetryai.core.foundation.CoreMath.Vec3;
import cl.ingenieria.photogrammetryai.core.fewview.AxisConstrainedPulleyEstimator;
import cl.ingenieria.photogrammetryai.core.fewview.AxisConstrainedPulleyEstimator.PulleyCylinder;
import cl.ingenieria.photogrammetryai.core.fewview.FewViewBaselineSelector;
import cl.ingenieria.photogrammetryai.core.fewview.FewViewBaselineSelector.ViewPairScore;
import cl.ingenieria.photogrammetryai.core.fewview.FewViewGeometry.CameraIntrinsics;
import cl.ingenieria.photogrammetryai.core.fewview.FewViewGeometry.CameraView;
import cl.ingenieria.photogrammetryai.core.fewview.FewViewGeometry.FeatureObservation;
import cl.ingenieria.photogrammetryai.core.fewview.FewViewGeometry.FeatureTrack;
import cl.ingenieria.photogrammetryai.core.fewview.FewViewGeometry.ProjectedPoint;
import cl.ingenieria.photogrammetryai.core.fewview.FewViewGeometry.ReconstructedPoint;
import cl.ingenieria.photogrammetryai.core.fewview.FewViewGeometry.ReconstructionReport;
import cl.ingenieria.photogrammetryai.core.fewview.FewViewTrackBuilder;
import cl.ingenieria.photogrammetryai.core.fewview.FewViewTrackBuilder.FeatureKey;
import cl.ingenieria.photogrammetryai.core.fewview.FewViewTrackBuilder.PairMatch;
import cl.ingenieria.photogrammetryai.core.fewview.KnownPoseFewViewReconstructor;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.List;

public final class FewViewReconstructionV18Test {
    public static void main(String[] args) {
        CameraIntrinsics intrinsics = new CameraIntrinsics(1280, 960, 900.0, 900.0, 640.0, 480.0);
        List<CameraView> views = Arrays.asList(
                view("A", intrinsics, -300.0, 0.0, 0.0),
                view("B", intrinsics, 300.0, 0.0, 0.0),
                view("C", intrinsics, 0.0, 250.0, 20.0),
                view("D", intrinsics, 0.0, -220.0, -10.0)
        );

        List<Vec3> truth = Arrays.asList(
                new Vec3(-180.0, -120.0, 1800.0),
                new Vec3(-60.0, 80.0, 1950.0),
                new Vec3(90.0, -40.0, 2100.0),
                new Vec3(210.0, 130.0, 2250.0),
                new Vec3(0.0, 0.0, 1700.0)
        );
        List<FeatureTrack> tracks = new ArrayList<>();
        for (int pointIndex = 0; pointIndex < truth.size(); pointIndex++) {
            List<FeatureObservation> observations = new ArrayList<>();
            for (int viewIndex = 0; viewIndex < views.size(); viewIndex++) {
                CameraView view = views.get(viewIndex);
                ProjectedPoint projected = view.project(truth.get(pointIndex)).orElseThrow(
                        () -> new AssertionError("Synthetic point behind camera")
                );
                double noiseU = (((pointIndex + 2) * (viewIndex + 3)) % 5 - 2) * 0.09;
                double noiseV = (((pointIndex + 5) * (viewIndex + 1)) % 7 - 3) * 0.07;
                if (pointIndex == 2 && view.id().equals("C")) {
                    noiseU += 75.0;
                    noiseV -= 55.0;
                }
                observations.add(new FeatureObservation(
                        view.id(),
                        projected.u() + noiseU,
                        projected.v() + noiseV,
                        0.96
                ));
            }
            tracks.add(new FeatureTrack("P" + pointIndex, observations));
        }

        FewViewBaselineSelector selector = new FewViewBaselineSelector();
        List<ViewPairScore> rankedPairs = selector.rankPairs(views, tracks, 1.25);
        require(!rankedPairs.isEmpty(), "Expected ranked view pairs");
        ViewPairScore bestPair = rankedPairs.get(0);
        require(
                (bestPair.firstViewId().equals("A") && bestPair.secondViewId().equals("B"))
                        || (bestPair.firstViewId().equals("B") && bestPair.secondViewId().equals("A")),
                "Expected widest A-B pair, got "
                        + bestPair.firstViewId() + '-' + bestPair.secondViewId()
        );

        KnownPoseFewViewReconstructor reconstructor = new KnownPoseFewViewReconstructor(
                KnownPoseFewViewReconstructor.Config.mobileDefaults()
        );
        ReconstructionReport report = reconstructor.reconstruct(views, tracks);
        require(report.points().size() == truth.size(), "All synthetic tracks should reconstruct");
        require(report.rejectedTrackCount() == 0, "Unexpected rejected synthetic tracks");

        for (ReconstructedPoint reconstructed : report.points()) {
            Vec3 expected = truth.get(Integer.parseInt(reconstructed.trackId().substring(1)));
            double positionErrorMm = reconstructed.worldPointMm().distanceTo(expected);
            require(positionErrorMm < 2.5,
                    reconstructed.trackId() + " position error too high: " + positionErrorMm + " mm");
            require(reconstructed.rmsReprojectionErrorPx() < 0.8,
                    reconstructed.trackId() + " reprojection RMS too high");
            require(reconstructed.bestTriangulationAngleDeg() > 10.0,
                    reconstructed.trackId() + " triangulation angle too small");
        }

        ReconstructedPoint outlierPoint = report.points().stream()
                .filter(point -> point.trackId().equals("P2"))
                .findFirst()
                .orElseThrow(() -> new AssertionError("P2 missing"));
        require(!outlierPoint.inlierViewIds().contains("C"),
                "Gross C-view outlier should have been rejected");

        testLowParallaxRejection(intrinsics, reconstructor);
        testTrackBuilder();
        testAxisConstrainedCylinder();

        System.out.println("FewViewReconstructionV18Test OK");
        System.out.println("points=" + report.points().size()
                + " meanConfidence=" + report.meanConfidence()
                + " bestPair=" + bestPair.firstViewId() + '-' + bestPair.secondViewId()
                + " medianAngle=" + bestPair.medianTriangulationAngleDeg());
    }

    private static void testLowParallaxRejection(
            CameraIntrinsics intrinsics,
            KnownPoseFewViewReconstructor reconstructor
    ) {
        CameraView first = view("E", intrinsics, 0.0, 0.0, 0.0);
        CameraView second = view("F", intrinsics, 1.0, 0.0, 0.0);
        Vec3 distantPoint = new Vec3(0.0, 0.0, 5000.0);
        ProjectedPoint firstProjection = first.project(distantPoint).orElseThrow(AssertionError::new);
        ProjectedPoint secondProjection = second.project(distantPoint).orElseThrow(AssertionError::new);
        FeatureTrack track = new FeatureTrack("degenerate", Arrays.asList(
                new FeatureObservation("E", firstProjection.u(), firstProjection.v(), 1.0),
                new FeatureObservation("F", secondProjection.u(), secondProjection.v(), 1.0)
        ));
        ReconstructionReport report = reconstructor.reconstruct(
                Arrays.asList(first, second),
                Arrays.asList(track)
        );
        require(report.points().isEmpty(), "Low-parallax track should be rejected");
        require(report.rejectionReasons().containsKey(
                        KnownPoseFewViewReconstructor.REJECT_LOW_PARALLAX),
                "Expected LOW_PARALLAX rejection");
    }

    private static void testTrackBuilder() {
        FewViewTrackBuilder builder = new FewViewTrackBuilder(
                FewViewTrackBuilder.Config.mobileDefaults()
        );
        FeatureKey a0 = new FeatureKey("A", 0);
        FeatureKey b0 = new FeatureKey("B", 0);
        FeatureKey c0 = new FeatureKey("C", 0);
        FeatureKey a1 = new FeatureKey("A", 1);
        FeatureKey b1 = new FeatureKey("B", 1);

        List<PairMatch> matches = Arrays.asList(
                match(a0, b0, 420.0, 360.0, 390.0, 360.0, 20.0, 55.0, true, 0.95),
                match(b0, c0, 390.0, 360.0, 405.0, 330.0, 18.0, 51.0, true, 0.94),
                match(c0, a1, 405.0, 330.0, 610.0, 500.0, 22.0, 58.0, true, 0.92),
                match(a1, b1, 610.0, 500.0, 600.0, 500.0, 49.0, 55.0, true, 0.90),
                match(new FeatureKey("A", 2), new FeatureKey("B", 2),
                        100.0, 100.0, 110.0, 100.0, 15.0, 50.0, false, 0.90)
        );
        FewViewTrackBuilder.BuildReport report = builder.build(matches);
        require(report.ratioRejectedCount() == 1, "Expected one ratio rejection");
        require(report.reciprocityRejectedCount() == 1, "Expected one reciprocity rejection");
        require(report.conflictRejectedCount() == 1,
                "Expected duplicate-view component conflict rejection");
        require(report.tracks().size() == 1, "Expected one valid three-view track");
        require(report.tracks().get(0).observations().size() == 3,
                "Expected A-B-C observations in valid track");
    }

    private static void testAxisConstrainedCylinder() {
        Vec3 origin = new Vec3(0.0, 0.0, 2000.0);
        Vec3 axis = Vec3.X;
        double expectedRadius = 500.0;
        double expectedLength = 1500.0;
        List<ReconstructedPoint> points = new ArrayList<>();
        LinkedHashSet<String> viewIds = new LinkedHashSet<>(Arrays.asList("A", "B", "C"));

        for (int index = 0; index < 40; index++) {
            double axial = -expectedLength * 0.5 + expectedLength * index / 39.0;
            double angle = 2.0 * Math.PI * (index % 10) / 10.0;
            double radialNoise = ((index * 7) % 9 - 4) * 0.16;
            if (index == 5 || index == 31) {
                radialNoise += 55.0;
            }
            double radius = expectedRadius + radialNoise;
            Vec3 point = origin
                    .add(axis.scale(axial))
                    .add(Vec3.Y.scale(radius * Math.cos(angle)))
                    .add(Vec3.Z.scale(radius * Math.sin(angle)));
            points.add(new ReconstructedPoint(
                    "shell_" + index,
                    point,
                    viewIds,
                    0.35,
                    0.70,
                    16.0,
                    0.95
            ));
        }

        AxisConstrainedPulleyEstimator estimator = new AxisConstrainedPulleyEstimator(
                AxisConstrainedPulleyEstimator.Config.mobileDefaults()
        );
        PulleyCylinder cylinder = estimator.estimate(origin, axis, points)
                .orElseThrow(() -> new AssertionError("Expected pulley cylinder estimate"));
        require(Math.abs(cylinder.radiusMm() - expectedRadius) < 1.0,
                "Pulley radius error too high: " + cylinder.radiusMm());
        require(Math.abs(cylinder.lengthMm() - expectedLength) < 80.0,
                "Pulley length error too high: " + cylinder.lengthMm());
        require(cylinder.radialRmsMm() < 1.0,
                "Pulley radial RMS too high: " + cylinder.radialRmsMm());
        require(cylinder.inlierPointCount() == 38,
                "Expected two radial outliers to be rejected");
    }

    private static PairMatch match(
            FeatureKey first,
            FeatureKey second,
            double firstU,
            double firstV,
            double secondU,
            double secondV,
            double bestDistance,
            double secondBestDistance,
            boolean reciprocal,
            double confidence
    ) {
        return new PairMatch(
                first,
                second,
                firstU,
                firstV,
                secondU,
                secondV,
                1280,
                960,
                1280,
                960,
                bestDistance,
                secondBestDistance,
                reciprocal,
                confidence
        );
    }

    private static CameraView view(
            String id,
            CameraIntrinsics intrinsics,
            double x,
            double y,
            double z
    ) {
        return new CameraView(
                id,
                intrinsics,
                RigidPose.translation(new Vec3(x, y, z)),
                1_000_000L,
                0.98
        );
    }

    private static void require(boolean condition, String message) {
        if (!condition) {
            throw new AssertionError(message);
        }
    }
}

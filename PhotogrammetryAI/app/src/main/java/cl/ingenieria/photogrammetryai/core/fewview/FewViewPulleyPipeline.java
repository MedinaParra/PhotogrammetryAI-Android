package cl.ingenieria.photogrammetryai.core.fewview;

import cl.ingenieria.photogrammetryai.core.foundation.CoreMath.Vec3;
import cl.ingenieria.photogrammetryai.core.fewview.AxisConstrainedPulleyEstimator.PulleyCylinder;
import cl.ingenieria.photogrammetryai.core.fewview.FewViewBaselineSelector.ViewPairScore;
import cl.ingenieria.photogrammetryai.core.fewview.FewViewGeometry.CameraView;
import cl.ingenieria.photogrammetryai.core.fewview.FewViewGeometry.ReconstructedPoint;
import cl.ingenieria.photogrammetryai.core.fewview.FewViewGeometry.ReconstructionReport;
import cl.ingenieria.photogrammetryai.core.fewview.FewViewTrackBuilder.BuildReport;
import cl.ingenieria.photogrammetryai.core.fewview.FewViewTrackBuilder.PairMatch;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

/** End-to-end pure core pipeline from pairwise matches to an axis-constrained pulley cylinder. */
public final class FewViewPulleyPipeline {
    public enum Status {
        NO_TRACKS,
        NO_3D_POINTS,
        INSUFFICIENT_SHELL_POINTS,
        CYLINDER_READY
    }

    public static final class Config {
        private final FewViewTrackBuilder.Config trackBuilderConfig;
        private final KnownPoseFewViewReconstructor.Config reconstructionConfig;
        private final AxisConstrainedPulleyEstimator.Config cylinderConfig;
        private final double minimumUsefulPairAngleDeg;

        public Config(
                FewViewTrackBuilder.Config trackBuilderConfig,
                KnownPoseFewViewReconstructor.Config reconstructionConfig,
                AxisConstrainedPulleyEstimator.Config cylinderConfig,
                double minimumUsefulPairAngleDeg
        ) {
            this.trackBuilderConfig = Objects.requireNonNull(
                    trackBuilderConfig,
                    "trackBuilderConfig"
            );
            this.reconstructionConfig = Objects.requireNonNull(
                    reconstructionConfig,
                    "reconstructionConfig"
            );
            this.cylinderConfig = Objects.requireNonNull(cylinderConfig, "cylinderConfig");
            if (!Double.isFinite(minimumUsefulPairAngleDeg) || minimumUsefulPairAngleDeg < 0.0) {
                throw new IllegalArgumentException(
                        "minimumUsefulPairAngleDeg must be finite and non-negative"
                );
            }
            this.minimumUsefulPairAngleDeg = minimumUsefulPairAngleDeg;
        }

        public static Config mobileDefaults() {
            return new Config(
                    FewViewTrackBuilder.Config.mobileDefaults(),
                    KnownPoseFewViewReconstructor.Config.mobileDefaults(),
                    AxisConstrainedPulleyEstimator.Config.mobileDefaults(),
                    1.25
            );
        }
    }

    public static final class Input {
        private final List<CameraView> views;
        private final List<PairMatch> pairMatches;
        private final Vec3 shaftOriginWorldMm;
        private final Vec3 shaftAxisWorld;
        private final Set<String> shellTrackIds;

        public Input(
                List<CameraView> views,
                List<PairMatch> pairMatches,
                Vec3 shaftOriginWorldMm,
                Vec3 shaftAxisWorld,
                Set<String> shellTrackIds
        ) {
            Objects.requireNonNull(views, "views");
            Objects.requireNonNull(pairMatches, "pairMatches");
            if (views.size() < 2 || views.size() > 8) {
                throw new IllegalArgumentException("Few-view pipeline supports between 2 and 8 views");
            }
            this.views = Collections.unmodifiableList(new ArrayList<>(views));
            this.pairMatches = Collections.unmodifiableList(new ArrayList<>(pairMatches));
            this.shaftOriginWorldMm = Objects.requireNonNull(
                    shaftOriginWorldMm,
                    "shaftOriginWorldMm"
            );
            this.shaftAxisWorld = Objects.requireNonNull(
                    shaftAxisWorld,
                    "shaftAxisWorld"
            ).normalized();
            this.shellTrackIds = shellTrackIds == null
                    ? Collections.emptySet()
                    : Collections.unmodifiableSet(new LinkedHashSet<>(shellTrackIds));
        }

        public List<CameraView> views() {
            return views;
        }

        public List<PairMatch> pairMatches() {
            return pairMatches;
        }

        public Vec3 shaftOriginWorldMm() {
            return shaftOriginWorldMm;
        }

        public Vec3 shaftAxisWorld() {
            return shaftAxisWorld;
        }

        public Set<String> shellTrackIds() {
            return shellTrackIds;
        }
    }

    public static final class Result {
        private final Status status;
        private final BuildReport trackBuildReport;
        private final List<ViewPairScore> rankedPairs;
        private final ReconstructionReport reconstructionReport;
        private final List<ReconstructedPoint> shellPoints;
        private final PulleyCylinder cylinder;

        private Result(
                Status status,
                BuildReport trackBuildReport,
                List<ViewPairScore> rankedPairs,
                ReconstructionReport reconstructionReport,
                List<ReconstructedPoint> shellPoints,
                PulleyCylinder cylinder
        ) {
            this.status = status;
            this.trackBuildReport = trackBuildReport;
            this.rankedPairs = Collections.unmodifiableList(new ArrayList<>(rankedPairs));
            this.reconstructionReport = reconstructionReport;
            this.shellPoints = Collections.unmodifiableList(new ArrayList<>(shellPoints));
            this.cylinder = cylinder;
        }

        public Status status() {
            return status;
        }

        public BuildReport trackBuildReport() {
            return trackBuildReport;
        }

        public List<ViewPairScore> rankedPairs() {
            return rankedPairs;
        }

        public ReconstructionReport reconstructionReport() {
            return reconstructionReport;
        }

        public List<ReconstructedPoint> shellPoints() {
            return shellPoints;
        }

        public Optional<PulleyCylinder> cylinder() {
            return Optional.ofNullable(cylinder);
        }
    }

    private final FewViewTrackBuilder trackBuilder;
    private final FewViewBaselineSelector baselineSelector;
    private final KnownPoseFewViewReconstructor reconstructor;
    private final AxisConstrainedPulleyEstimator cylinderEstimator;
    private final double minimumUsefulPairAngleDeg;

    public FewViewPulleyPipeline(Config config) {
        Objects.requireNonNull(config, "config");
        this.trackBuilder = new FewViewTrackBuilder(config.trackBuilderConfig);
        this.baselineSelector = new FewViewBaselineSelector();
        this.reconstructor = new KnownPoseFewViewReconstructor(config.reconstructionConfig);
        this.cylinderEstimator = new AxisConstrainedPulleyEstimator(config.cylinderConfig);
        this.minimumUsefulPairAngleDeg = config.minimumUsefulPairAngleDeg;
    }

    public Result run(Input input) {
        Objects.requireNonNull(input, "input");
        BuildReport trackReport = trackBuilder.build(input.pairMatches());
        List<ViewPairScore> rankedPairs = baselineSelector.rankPairs(
                input.views(),
                trackReport.tracks(),
                minimumUsefulPairAngleDeg
        );
        ReconstructionReport reconstruction = reconstructor.reconstruct(
                input.views(),
                trackReport.tracks()
        );

        if (trackReport.tracks().isEmpty()) {
            return new Result(
                    Status.NO_TRACKS,
                    trackReport,
                    rankedPairs,
                    reconstruction,
                    Collections.emptyList(),
                    null
            );
        }
        if (reconstruction.points().isEmpty()) {
            return new Result(
                    Status.NO_3D_POINTS,
                    trackReport,
                    rankedPairs,
                    reconstruction,
                    Collections.emptyList(),
                    null
            );
        }

        List<ReconstructedPoint> shellPoints = selectShellPoints(
                reconstruction.points(),
                input.shellTrackIds()
        );
        Optional<PulleyCylinder> cylinder = cylinderEstimator.estimate(
                input.shaftOriginWorldMm(),
                input.shaftAxisWorld(),
                shellPoints
        );
        if (!cylinder.isPresent()) {
            return new Result(
                    Status.INSUFFICIENT_SHELL_POINTS,
                    trackReport,
                    rankedPairs,
                    reconstruction,
                    shellPoints,
                    null
            );
        }
        return new Result(
                Status.CYLINDER_READY,
                trackReport,
                rankedPairs,
                reconstruction,
                shellPoints,
                cylinder.get()
        );
    }

    private static List<ReconstructedPoint> selectShellPoints(
            List<ReconstructedPoint> reconstructedPoints,
            Set<String> shellTrackIds
    ) {
        if (shellTrackIds.isEmpty()) {
            return new ArrayList<>(reconstructedPoints);
        }
        List<ReconstructedPoint> selected = new ArrayList<>();
        for (ReconstructedPoint point : reconstructedPoints) {
            if (shellTrackIds.contains(point.trackId())) {
                selected.add(point);
            }
        }
        return selected;
    }
}

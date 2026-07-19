import cl.ingenieria.photogrammetryai.core.foundation.CadReferenceModel;
import cl.ingenieria.photogrammetryai.core.foundation.CoreMath.RigidPose;
import cl.ingenieria.photogrammetryai.core.foundation.CoreMath.Vec3;
import cl.ingenieria.photogrammetryai.core.foundation.CorePorts;
import cl.ingenieria.photogrammetryai.core.foundation.InMemoryCadReferenceRepository;
import cl.ingenieria.photogrammetryai.core.foundation.PulleyReconstructionCore;
import cl.ingenieria.photogrammetryai.core.foundation.TrackingFrame.DeviceObservation;
import cl.ingenieria.photogrammetryai.core.foundation.TrackingFrame.SupportRole;

import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.List;

public final class CoreFoundationV16Test {
    private static final long NOW = 10_000_000_000L;

    public static void main(String[] args) {
        testRigidPoseComposition();
        testThreeDeviceSupportFusion();
        testLowConfidenceObservationIsRejected();
        System.out.println("CoreFoundationV16Test: OK");
    }

    private static void testRigidPoseComposition() {
        RigidPose worldFromCamera = RigidPose.translation(new Vec3(100.0, 20.0, 5.0));
        RigidPose cameraFromReference = RigidPose.translation(new Vec3(50.0, -20.0, 15.0));
        Vec3 worldPoint = worldFromCamera.compose(cameraFromReference).transformPoint(Vec3.ZERO);
        assertNear(worldPoint.x, 150.0, 1.0e-9, "composed x");
        assertNear(worldPoint.y, 0.0, 1.0e-9, "composed y");
        assertNear(worldPoint.z, 20.0, 1.0e-9, "composed z");
    }

    private static void testThreeDeviceSupportFusion() {
        InMemoryCadReferenceRepository repository = new InMemoryCadReferenceRepository();
        PulleyReconstructionCore core = new PulleyReconstructionCore(
                PulleyReconstructionCore.Config.realTimeDefaults(),
                repository,
                () -> NOW,
                CorePorts.NO_OP_EVENT_SINK
        );

        CadReferenceModel support = createSupportReference();
        core.registerReference(support);
        core.startSession(
                "session-001",
                new LinkedHashSet<>(Arrays.asList("GALAXY_A26", "HONOR_X5C", "INFINIX_SMART9"))
        );

        assertTrue(core.submitObservation(observation(
                "GALAXY_A26",
                SupportRole.LEFT,
                new Vec3(0.0, 0.0, 200.0),
                0.94,
                0.92,
                0.45
        )), "Galaxy observation should be accepted");

        assertTrue(core.submitObservation(observation(
                "HONOR_X5C",
                SupportRole.LEFT,
                new Vec3(2.0, -1.0, 201.0),
                0.90,
                0.88,
                0.60
        )), "Honor observation should be accepted");

        assertTrue(core.submitObservation(observation(
                "INFINIX_SMART9",
                SupportRole.RIGHT,
                new Vec3(1000.0, 0.0, 200.0),
                0.91,
                0.89,
                0.55
        )), "Infinix observation should be accepted");

        PulleyReconstructionCore.PulleyEstimate estimate = core.snapshot().estimate()
                .orElseThrow(() -> new AssertionError("Expected a pulley estimate"));

        assertTrue(estimate.stable(), "Three-device estimate should be stable");
        assertTrue(estimate.observationCount() == 3, "Expected three fused observations");
        assertNear(estimate.supportCentreDistanceMm(), 999.0, 3.0,
                "support centre distance");
        assertTrue(Math.abs(estimate.shaftDirectionWorld().dot(Vec3.X)) > 0.999,
                "shaft axis should be aligned with world X");
        assertTrue(estimate.confidence() > 0.70,
                "confidence should reflect consistent three-device evidence");

        assertTrue(core.freeze().isPresent(), "A tracking estimate should freeze");
        assertTrue(core.snapshot().state() == PulleyReconstructionCore.State.FROZEN,
                "Core should enter FROZEN state");
    }

    private static void testLowConfidenceObservationIsRejected() {
        InMemoryCadReferenceRepository repository = new InMemoryCadReferenceRepository();
        PulleyReconstructionCore core = new PulleyReconstructionCore(
                PulleyReconstructionCore.Config.realTimeDefaults(),
                repository,
                () -> NOW,
                CorePorts.NO_OP_EVENT_SINK
        );
        core.registerReference(createSupportReference());
        core.startSession("session-low-confidence",
                new LinkedHashSet<>(Arrays.asList("GALAXY_A26")));

        boolean accepted = core.submitObservation(observation(
                "GALAXY_A26",
                SupportRole.LEFT,
                Vec3.ZERO,
                0.20,
                0.90,
                0.50
        ));
        assertTrue(!accepted, "Low target confidence must be rejected");
        assertTrue(!core.snapshot().estimate().isPresent(),
                "Rejected observations must not update the estimate");
    }

    private static CadReferenceModel createSupportReference() {
        List<CadReferenceModel.Feature> features = Arrays.asList(
                CadReferenceModel.Feature.primaryBore(
                        "bore-main",
                        "Primary bearing bore",
                        Vec3.ZERO,
                        Vec3.X,
                        150.0,
                        220.0
                ),
                CadReferenceModel.Feature.mountingPlane(
                        "base-plane",
                        "Mounting plane",
                        new Vec3(0.0, 0.0, -200.0),
                        Vec3.Z,
                        0.8
                )
        );
        return new CadReferenceModel(
                "support-snl-3268",
                "SNL 3268 reference",
                "snl_3268.step",
                "0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef",
                CadReferenceModel.Unit.MILLIMETRE,
                features,
                "mesh/support-snl-3268-lod0"
        );
    }

    private static DeviceObservation observation(
            String deviceId,
            SupportRole role,
            Vec3 worldReferenceTranslation,
            double targetConfidence,
            double modelConfidence,
            double reprojectionErrorPx
    ) {
        return new DeviceObservation(
                deviceId,
                "support-snl-3268",
                role,
                NOW,
                RigidPose.identity(),
                RigidPose.translation(worldReferenceTranslation),
                targetConfidence,
                modelConfidence,
                reprojectionErrorPx
        );
    }

    private static void assertNear(double actual, double expected, double tolerance, String label) {
        if (!Double.isFinite(actual) || Math.abs(actual - expected) > tolerance) {
            throw new AssertionError(label + ": expected " + expected + " ± " + tolerance
                    + ", got " + actual);
        }
    }

    private static void assertTrue(boolean condition, String message) {
        if (!condition) {
            throw new AssertionError(message);
        }
    }
}

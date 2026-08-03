package cl.skm.pulleyai;

/** Converts pure supplemental metrics into the existing safety-gate adapter input. */
public final class PhotogrammetrySupplementalMetricsAdapter {
    private PhotogrammetrySupplementalMetricsAdapter() {}

    public static PhotogrammetrySafetyGateAdapter.SupplementalMetrics toSafetyGate(
            PhotogrammetrySupplementalMetricsCore.Result result) {
        if (result == null) return PhotogrammetrySafetyGateAdapter.SupplementalMetrics.unknown();
        return new PhotogrammetrySafetyGateAdapter.SupplementalMetrics(
                result.homographyDominanceRatio,
                result.blurryFrameFraction,
                result.reflectiveFrameFraction,
                result.repetitiveAmbiguityFraction);
    }
}

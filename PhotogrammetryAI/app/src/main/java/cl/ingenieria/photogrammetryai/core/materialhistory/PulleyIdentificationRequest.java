package cl.ingenieria.photogrammetryai.core.materialhistory;

import cl.ingenieria.photogrammetryai.core.materialhistory.PulleyMaterialIdentificationEngine.Query;

import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Set;

/** Operator-facing request that normalises human-entered identifiers before domain scoring. */
public final class PulleyIdentificationRequest {
    private final double shellLengthMm;
    private final String rawMaterialCode;
    private final String rawOt;
    private final Double shellDiameterMm;
    private final String description;
    private final Set<String> observedModels;

    public PulleyIdentificationRequest(
            double shellLengthMm,
            String rawMaterialCode,
            String rawOt,
            Double shellDiameterMm,
            String description,
            Set<String> observedModels
    ) {
        if (!Double.isFinite(shellLengthMm) || shellLengthMm <= 0.0) {
            throw new IllegalArgumentException("shellLengthMm is mandatory and must be positive");
        }
        if (shellDiameterMm != null
                && (!Double.isFinite(shellDiameterMm) || shellDiameterMm <= 0.0)) {
            throw new IllegalArgumentException("shellDiameterMm must be positive when present");
        }
        this.shellLengthMm = shellLengthMm;
        this.rawMaterialCode = blankToNull(rawMaterialCode);
        this.rawOt = blankToNull(rawOt);
        this.shellDiameterMm = shellDiameterMm;
        this.description = description == null ? "" : description.trim();
        Set<String> models = new LinkedHashSet<>();
        if (observedModels != null) {
            for (String model : observedModels) {
                if (model != null && !model.trim().isEmpty()) models.add(model.trim());
            }
        }
        this.observedModels = Collections.unmodifiableSet(models);
    }

    public Query toQuery() {
        return new Query(
                shellLengthMm,
                rawMaterialCode == null ? null : MaterialCodeInput.normalize(rawMaterialCode),
                rawOt == null ? null : MaterialPulleyKnowledgeBase.normalizeOt(rawOt),
                shellDiameterMm,
                description,
                observedModels
        );
    }

    public double shellLengthMm() { return shellLengthMm; }
    public String rawMaterialCode() { return rawMaterialCode; }
    public String rawOt() { return rawOt; }
    public Double shellDiameterMm() { return shellDiameterMm; }
    public String description() { return description; }
    public Set<String> observedModels() { return observedModels; }

    private static String blankToNull(String value) {
        return value == null || value.trim().isEmpty() ? null : value.trim();
    }
}

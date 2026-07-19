package cl.ingenieria.photogrammetryai.core.materialhistory;

import cl.ingenieria.photogrammetryai.core.materialhistory.PulleyMaterialIdentificationEngine.Query;

import java.util.Collections;
import java.util.Locale;

/** Normalises operator inputs such as SC, SAP, Stock Code or Código de Material. */
public final class MaterialCodeInput {
    private MaterialCodeInput() {
    }

    public static String normalize(String raw) {
        if (raw == null || raw.trim().isEmpty()) {
            throw new IllegalArgumentException("material code must not be blank");
        }
        String value = raw.toUpperCase(Locale.ROOT).trim();
        value = value.replaceFirst(
                "^(?:STOCK\\s*CODE|C[ÓO]DIGO\\s*(?:DE\\s*)?MATERIAL|C[ÓO]DIGO\\s*SAP|SAP|SC)"
                        + "\\s*[:#-]?\\s*",
                ""
        );
        return MaterialPulleyKnowledgeBase.normalizeMaterialCode(value);
    }

    public static Query query(String rawMaterialCode, double shellLengthMm) {
        return new Query(
                shellLengthMm,
                normalize(rawMaterialCode),
                null,
                null,
                "",
                Collections.emptySet()
        );
    }
}

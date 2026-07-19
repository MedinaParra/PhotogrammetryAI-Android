import cl.ingenieria.photogrammetryai.core.materialhistory.DriveQualityKnowledgeSeed;
import cl.ingenieria.photogrammetryai.core.materialhistory.MaterialPulleyKnowledgeBase;
import cl.ingenieria.photogrammetryai.core.materialhistory.MaterialPulleyKnowledgeBase.DimensionKind;
import cl.ingenieria.photogrammetryai.core.materialhistory.MaterialPulleyKnowledgeBase.MaterialFamily;
import cl.ingenieria.photogrammetryai.core.materialhistory.PulleyMaterialIdentificationEngine;
import cl.ingenieria.photogrammetryai.core.materialhistory.PulleyMaterialIdentificationEngine.Action;
import cl.ingenieria.photogrammetryai.core.materialhistory.PulleyMaterialIdentificationEngine.Candidate;
import cl.ingenieria.photogrammetryai.core.materialhistory.PulleyMaterialIdentificationEngine.LengthStatus;
import cl.ingenieria.photogrammetryai.core.materialhistory.PulleyMaterialIdentificationEngine.Query;
import cl.ingenieria.photogrammetryai.core.materialhistory.QualityReportTextParser;
import cl.ingenieria.photogrammetryai.core.materialhistory.QualityReportTextParser.ParsedReport;

import java.util.Collections;

public final class MaterialHistoryV22Test {
    public static void main(String[] args) {
        MaterialPulleyKnowledgeBase knowledge = DriveQualityKnowledgeSeed.create();
        require(knowledge.size() >= 8, "Expected seeded material families");

        MaterialFamily family10415863 = knowledge.findByMaterialCode("SC 10415863")
                .orElseThrow(() -> new AssertionError("10415863 missing"));
        require(family10415863.ots().contains("OT-262"), "OT-262 reverse history missing");
        require(family10415863.ots().contains("OT-1702"), "OT-1702 reverse history missing");
        require(
                knowledge.materialCodesForOt("2026-1702").contains("10415863"),
                "OT-1702 should resolve to 10415863"
        );
        require(
                family10415863.consensusDimensionMm(DimensionKind.SHELL_LENGTH)
                        .orElseThrow(AssertionError::new) == 1520.0,
                "Expected 1520 mm shell length"
        );

        PulleyMaterialIdentificationEngine engine = new PulleyMaterialIdentificationEngine(
                knowledge,
                PulleyMaterialIdentificationEngine.Config.industrialDefaults()
        );

        Candidate exact = engine.identify(new Query(
                1520.0,
                "10415863",
                "OT-1702",
                1400.0,
                "POLEA BP LORBRAND BP5MSCP00273",
                Collections.singleton("SD 3164")
        ), 5).best().orElseThrow(() -> new AssertionError("No exact candidate"));
        require(exact.family().materialCode().equals("10415863"), "Wrong exact family");
        require(exact.lengthStatus() == LengthStatus.MATCH, "Expected length match");
        require(exact.action() == Action.DIRECT_FAMILY_MATCH, "Expected direct family match");
        require(exact.score() > 0.82, "Exact family score too low: " + exact.score());

        Candidate visual = engine.identify(new Query(
                1520.0,
                null,
                null,
                800.0,
                "Polea deflectora 041 CV 022",
                Collections.singleton("OH 3148 H")
        ), 5).best().orElseThrow(() -> new AssertionError("No visual candidate"));
        require(visual.family().materialCode().equals("10415860"),
                "Diameter and model should distinguish 10415860");
        require(visual.lengthStatus() == LengthStatus.MATCH, "Expected visual length match");

        Candidate conflict = engine.identify(Query.byMaterialCode("10415863", 2032.0), 3)
                .best()
                .orElseThrow(() -> new AssertionError("No conflict candidate"));
        require(conflict.family().materialCode().equals("10415863"), "Exact code must remain first");
        require(conflict.lengthStatus() == LengthStatus.CONFLICT, "Expected length conflict");
        require(conflict.action() == Action.VERIFY_VARIANT, "Expected variant verification");
        require(!conflict.warnings().isEmpty(), "Conflict should have a warning");

        Candidate noLength = engine.identify(Query.byMaterialCode("4196111", 1520.0), 3)
                .best()
                .orElseThrow(() -> new AssertionError("No 4196111 candidate"));
        require(noLength.family().materialCode().equals("4196111"), "Wrong no-length family");
        require(noLength.lengthStatus() == LengthStatus.NO_HISTORY, "Expected no length history");
        require(noLength.action() == Action.HISTORICAL_FAMILY_NO_LENGTH,
                "Exact code without historical length should preserve family");

        Candidate drawing = engine.identify(Query.byMaterialCode("4162054", 2032.0), 3)
                .best()
                .orElseThrow(() -> new AssertionError("No drawing candidate"));
        require(drawing.family().materialCode().equals("4162054"), "Wrong drawing family");
        require(drawing.lengthStatus() == LengthStatus.MATCH, "Expected drawing length match");

        testParser();

        System.out.println("MaterialHistoryV22Test OK");
        System.out.println("families=" + knowledge.size()
                + " exactScore=" + exact.score()
                + " visual=" + visual.family().materialCode()
                + " conflictAction=" + conflict.action());
    }

    private static void testParser() {
        String text = "SISTEMA DE GESTIÓN DE LA CALIDAD ISO 9001\n"
                + "INFORME DE EVALUACIÓN DEL COMPONENTE PARA CLIENTE\n"
                + "Cliente : Minera Spence\n"
                + "Componente : Polea N°6 CV 12\n"
                + "Stock Code : 10415863\n"
                + "OC MEL : 4507181083\n"
                + "OT Interna : 2018-262\n"
                + "Diámetro del Manto A 1400 mm Largo del Manto C 1520 mm\n"
                + "Espesor del Revestimiento B 22 mm Espesor del Manto D 35 mm\n"
                + "Distancia entre soportes 2.080 mm Distancia entre rodamientos 2.030 mm\n";
        ParsedReport parsed = new QualityReportTextParser().parse(
                "(OT 262) Informe de Evaluación Polea N°6 CV 12.pdf",
                text
        );
        require(parsed.materialCode().orElse("").equals("10415863"), "Parser material code");
        require(parsed.ot().orElse("").equals("OT-262"), "Parser OT");
        require(parsed.year().orElse(0) == 2018, "Parser year");
        require(parsed.client().orElse("").equals("Minera Spence"), "Parser client");
        require(parsed.firstDimensionMm(DimensionKind.SHELL_LENGTH).orElse(0.0) == 1520.0,
                "Parser shell length");
        require(parsed.firstDimensionMm(DimensionKind.SHELL_DIAMETER).orElse(0.0) == 1400.0,
                "Parser shell diameter");
        require(parsed.firstDimensionMm(DimensionKind.SUPPORT_CENTRE_DISTANCE).orElse(0.0)
                        == 2080.0,
                "Parser support distance with thousands separator");
        require(parsed.firstDimensionMm(DimensionKind.BEARING_CENTRE_DISTANCE).orElse(0.0)
                        == 2030.0,
                "Parser bearing distance with thousands separator");
        require(QualityReportTextParser.parseMillimetres("36,5") == 36.5,
                "Parser decimal comma");
    }

    private static void require(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }
}

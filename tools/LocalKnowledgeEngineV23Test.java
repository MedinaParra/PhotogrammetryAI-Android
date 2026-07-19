import cl.ingenieria.photogrammetryai.core.materialhistory.DriveQualityKnowledgeSeed;
import cl.ingenieria.photogrammetryai.core.materialhistory.IdentificationAudit;
import cl.ingenieria.photogrammetryai.core.materialhistory.InMemoryMaterialKnowledgeStore;
import cl.ingenieria.photogrammetryai.core.materialhistory.LocalPulleyKnowledgeEngine;
import cl.ingenieria.photogrammetryai.core.materialhistory.MaterialPulleyKnowledgeBase.DimensionKind;
import cl.ingenieria.photogrammetryai.core.materialhistory.MaterialPulleyKnowledgeBase.MaterialFamily;
import cl.ingenieria.photogrammetryai.core.materialhistory.PulleyIdentificationRequest;
import cl.ingenieria.photogrammetryai.core.materialhistory.PulleyMaterialIdentificationEngine;
import cl.ingenieria.photogrammetryai.core.materialhistory.PulleyMaterialIdentificationEngine.Action;
import cl.ingenieria.photogrammetryai.core.materialhistory.QualityKnowledgeIngestionService;
import cl.ingenieria.photogrammetryai.core.materialhistory.QualityReportTextParser;
import cl.ingenieria.photogrammetryai.core.materialhistory.sqlite.SqlitePulleyKnowledgeSchema;
import cl.ingenieria.photogrammetryai.core.materialhistory.sqlite.TextListCodec;

import java.util.Arrays;
import java.util.Collections;

public final class LocalKnowledgeEngineV23Test {
    public static void main(String[] args) {
        InMemoryMaterialKnowledgeStore store = new InMemoryMaterialKnowledgeStore();
        LocalPulleyKnowledgeEngine engine = new LocalPulleyKnowledgeEngine(
                store,
                new QualityReportTextParser(),
                PulleyMaterialIdentificationEngine.Config.industrialDefaults(),
                () -> 1_721_390_400_000L,
                () -> "session-v23-001"
        );

        require(engine.initializeSeedIfEmpty(), "Seed should install into an empty store");
        require(!engine.initializeSeedIfEmpty(), "Seed installation must be idempotent");
        require(store.size() == DriveQualityKnowledgeSeed.create().size(), "Seed family count");

        LocalPulleyKnowledgeEngine.IdentificationOutcome outcome = engine.identify(
                new PulleyIdentificationRequest(
                        1520.0,
                        "SC 10415863",
                        "2026-1702",
                        1400.0,
                        "POLEA BP LORBRAND BP5MSCP00273",
                        Collections.singleton("SD 3164")
                ),
                5
        );
        require(outcome.sessionId().equals("session-v23-001"), "Deterministic session id");
        require(outcome.result().best().isPresent(), "Identification should have a candidate");
        require(
                outcome.result().best().get().family().materialCode().equals("10415863"),
                "Expected 10415863"
        );
        require(
                outcome.result().best().get().action() == Action.DIRECT_FAMILY_MATCH,
                "Expected direct match"
        );

        IdentificationAudit audit = store.findIdentificationAudit("session-v23-001")
                .orElseThrow(() -> new AssertionError("Audit was not saved"));
        require(!audit.operatorConfirmed(), "New identification must not self-confirm");
        require(audit.candidates().size() > 0, "Audit candidates missing");

        IdentificationAudit confirmed = engine.confirm("session-v23-001", "Código SAP 10415863");
        require(confirmed.operatorConfirmed(), "Operator confirmation not persisted");
        require(
                confirmed.selectedMaterialCode().orElse("").equals("10415863"),
                "Confirmed family missing"
        );

        String report = "SISTEMA DE GESTIÓN DE LA CALIDAD ISO 9001\n"
                + "INFORME DE EVALUACIÓN DEL COMPONENTE PARA CLIENTE\n"
                + "Cliente : Minera Spence BHP\n"
                + "Componente : Polea N°6 CV 12\n"
                + "Código de Material : 10415863\n"
                + "N° de Orden de compra : 4519999999-10\n"
                + "OT Interna : 2026-1800\n"
                + "Diámetro del Manto A 1400 mm Largo del Manto C 1520 mm\n"
                + "Espesor del Revestimiento B 22 mm Espesor del Manto D 35 mm\n"
                + "Distancia entre soportes 2.080 mm Distancia entre rodamientos 2.030 mm\n";
        QualityKnowledgeIngestionService.ReportInput reportInput =
                new QualityKnowledgeIngestionService.ReportInput(
                        "Informe Evaluación OT-1800 Polea N°6 CV12.pdf",
                        "drive://quality/ot-1800/evaluation",
                        report,
                        "2026-07-19"
                );
        QualityKnowledgeIngestionService.Result firstIngest = engine.ingestQualityReport(reportInput);
        require(firstIngest.accepted(), "Valid report should be accepted");
        require(firstIngest.materialCode().orElse("").equals("10415863"), "Ingested code");
        require(firstIngest.ot().orElse("").equals("OT-1800"), "Ingested OT");

        MaterialFamily afterFirst = store.findByMaterialCode("10415863")
                .orElseThrow(() -> new AssertionError("Family missing after ingest"));
        int sourceCount = afterFirst.sources().size();
        int lengthEvidenceCount = afterFirst.dimensions(DimensionKind.SHELL_LENGTH).size();
        require(afterFirst.ots().contains("OT-1800"), "New OT must be linked bidirectionally");
        require(store.materialCodesForOt("OT 1800").contains("10415863"), "Reverse OT lookup");

        QualityKnowledgeIngestionService.Result duplicate = engine.ingestQualityReport(reportInput);
        require(duplicate.accepted(), "Duplicate should be a successful idempotent ingest");
        MaterialFamily afterDuplicate = store.findByMaterialCode("10415863")
                .orElseThrow(AssertionError::new);
        require(afterDuplicate.sources().size() == sourceCount, "Duplicate source was inserted");
        require(
                afterDuplicate.dimensions(DimensionKind.SHELL_LENGTH).size() == lengthEvidenceCount,
                "Duplicate dimension was inserted"
        );

        QualityKnowledgeIngestionService.Result rejected = engine.ingestQualityReport(
                new QualityKnowledgeIngestionService.ReportInput(
                        "Informe sin identidad.pdf",
                        "drive://quality/unidentified",
                        "INFORME DE EVALUACIÓN\nOT Interna : 2026-1900\nLargo del Manto 1500 mm",
                        "2026-07-19"
                )
        );
        require(!rejected.accepted(), "Report without material code must be quarantined");

        String encoded = TextListCodec.encode(Arrays.asList(
                "largo compatible",
                "verificar código: 10415863"
        ));
        require(TextListCodec.decode(encoded).size() == 2, "SQLite text-list round trip");
        require(
                SqlitePulleyKnowledgeSchema.CREATE_STATEMENTS.stream()
                        .anyMatch(sql -> sql.contains("identification_session")),
                "Identification table missing from SQLite schema"
        );
        require(
                SqlitePulleyKnowledgeSchema.CREATE_STATEMENTS.stream()
                        .anyMatch(sql -> sql.contains("dimension_evidence")),
                "Dimension evidence table missing from SQLite schema"
        );

        System.out.println("LocalKnowledgeEngineV23Test OK");
        System.out.println("families=" + store.size()
                + " sources10415863=" + afterDuplicate.sources().size()
                + " audits=" + store.recentIdentificationAudits(10).size());
    }

    private static void require(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }
}

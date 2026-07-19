package cl.ingenieria.photogrammetryai.core.materialhistory;

import cl.ingenieria.photogrammetryai.core.materialhistory.MaterialPulleyKnowledgeBase.ComponentEvidence;
import cl.ingenieria.photogrammetryai.core.materialhistory.MaterialPulleyKnowledgeBase.ComponentKind;
import cl.ingenieria.photogrammetryai.core.materialhistory.MaterialPulleyKnowledgeBase.Condition;
import cl.ingenieria.photogrammetryai.core.materialhistory.MaterialPulleyKnowledgeBase.DimensionEvidence;
import cl.ingenieria.photogrammetryai.core.materialhistory.MaterialPulleyKnowledgeBase.DimensionKind;
import cl.ingenieria.photogrammetryai.core.materialhistory.MaterialPulleyKnowledgeBase.InterventionEvent;
import cl.ingenieria.photogrammetryai.core.materialhistory.MaterialPulleyKnowledgeBase.MaterialFamily;
import cl.ingenieria.photogrammetryai.core.materialhistory.MaterialPulleyKnowledgeBase.ReportPhase;
import cl.ingenieria.photogrammetryai.core.materialhistory.MaterialPulleyKnowledgeBase.SourceDocument;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * First curated seed extracted from the connected SKM quality archive.
 *
 * <p>Only explicitly labelled dimensions are stored as metrological evidence. Drawing dimensions
 * whose semantics need confirmation are labelled and assigned lower confidence. Missing shell
 * lengths remain missing: the mandatory operator value is authoritative in that case.</p>
 */
public final class DriveQualityKnowledgeSeed {
    private DriveQualityKnowledgeSeed() {
    }

    public static MaterialPulleyKnowledgeBase create() {
        MaterialPulleyKnowledgeBase knowledge = new MaterialPulleyKnowledgeBase();
        knowledge.register(code10415863());
        knowledge.register(code10415860());
        knowledge.register(code4162054());
        knowledge.register(code4196111());
        knowledge.register(code4196149());
        knowledge.register(code10510386());
        knowledge.register(code1462827());
        knowledge.register(code4162045());
        return knowledge;
    }

    private static MaterialFamily code10415863() {
        String ot262 = "drive:1Uw19FbxhsSJTlkDAxE6Rlh_AQFZ_ydEs";
        String ot1702 = "drive:1UvZFOTm6UPRR5m-1iugNfABky1bUAue3";
        String wip = "drive:1UWjW8KiLL6_qpUJDnfd_xuETFwOGcfX3";

        MaterialFamily.Builder builder = MaterialFamily.builder("10415863")
                .client("Minera Spence BHP")
                .roleHint("bend/deflectora")
                .alias("Polea N°6 CV 12")
                .alias("Polea Deflectora N°11 CV 011")
                .alias("Polea N°10 CV 011")
                .alias("POLEA,BP,LORBRAND BP5MSCP00273")
                .source(source(
                        ot262,
                        "(OT 262) Informe de Evaluación Polea N°6 CV 12 Minera Spence",
                        "https://drive.google.com/file/d/1Uw19FbxhsSJTlkDAxE6Rlh_AQFZ_ydEs",
                        ReportPhase.EVALUATION,
                        "OT-262",
                        "10415863",
                        "2018-12-03"
                ))
                .source(source(
                        ot1702,
                        "Informe Evaluación OT-1702 LORBRAND BP5MSCP00273",
                        "https://drive.google.com/file/d/1UvZFOTm6UPRR5m-1iugNfABky1bUAue3",
                        ReportPhase.EVALUATION,
                        "OT-1702",
                        "10415863",
                        "2026-06-08"
                ))
                .source(source(
                        wip,
                        "WIP SKM POLEAS CINTA TRANSPORTADORA SGO V23",
                        "https://drive.google.com/file/d/1UWjW8KiLL6_qpUJDnfd_xuETFwOGcfX3",
                        ReportPhase.WIP,
                        null,
                        "10415863",
                        "2026-07-16"
                ));

        addDimension(builder, DimensionKind.SHELL_DIAMETER, 1400.0,
                "Diámetro del Manto A", ot262, 0.99);
        addDimension(builder, DimensionKind.SHELL_LENGTH, 1520.0,
                "Largo del Manto C", ot262, 0.99);
        addDimension(builder, DimensionKind.LAGGING_THICKNESS, 22.0,
                "Espesor del Revestimiento B", ot262, 0.98);
        addDimension(builder, DimensionKind.SHELL_THICKNESS, 35.0,
                "Espesor del Manto D", ot262, 0.98);
        addDimension(builder, DimensionKind.SUPPORT_CENTRE_DISTANCE, 2080.0,
                "Distancia entre soportes", ot262, 0.98);
        addDimension(builder, DimensionKind.BEARING_CENTRE_DISTANCE, 2030.0,
                "Distancia entre rodamientos", ot262, 0.98);
        addDimension(builder, DimensionKind.SUPPORT_BORE_DIAMETER, 540.0,
                "Diámetro nominal soporte H7", ot262, 0.98);
        addDimension(builder, DimensionKind.SHAFT_TOTAL_LENGTH, 2355.0,
                "L10 largo total eje", ot262, 0.96);
        addDimension(builder, DimensionKind.SHAFT_BEARING_DIAMETER, 300.0,
                "Diámetro nominal zona de rodamientos", ot262, 0.98);
        addDimension(builder, DimensionKind.SHAFT_LOCKING_DIAMETER, 320.0,
                "Diámetro nominal zona manguitos de expansión", ot262, 0.98);
        addDimension(builder, DimensionKind.SHELL_THICKNESS, 36.5,
                "Medición de espesores OT-1702", ot1702, 0.92);

        addComponent(builder, ComponentKind.SUPPORT, "", "SDS 3164",
                Condition.REPLACE, "Soportes fuera de tolerancia en OT-262", ot262, 0.96);
        addComponent(builder, ComponentKind.BEARING, "", "23164 EMK W33 C3",
                Condition.REPLACE, "Fin de vida útil en OT-262", ot262, 0.95);
        addComponent(builder, ComponentKind.BEARING_ADAPTER, "", "OH 3164 H",
                Condition.REPLACE, "Corrosión de contacto en OT-262", ot262, 0.94);
        addComponent(builder, ComponentKind.SUPPORT, "FSQ", "SD 3164",
                Condition.REPLACE, "Fuera de tolerancia y con desgarros en OT-1702", ot1702, 0.98);
        addComponent(builder, ComponentKind.BEARING, "NSK", "23164 CAMKE4C3S11",
                Condition.REPLACE, "Desgaste y grasa contaminada", ot1702, 0.98);
        addComponent(builder, ComponentKind.BEARING_ADAPTER, "NSK", "OH3164H",
                Condition.REPLACE, "Dañado durante desmontaje", ot1702, 0.96);
        addComponent(builder, ComponentKind.LOCKING_ELEMENT, "", "B-115 320x405 mm",
                Condition.REPLACE, "Pitting", ot1702, 0.98);
        addComponent(builder, ComponentKind.SHAFT, "", "SAE 4140",
                Condition.REJECT, "Material y desgarros no cumplen estándar del cliente", ot1702, 0.98);
        addComponent(builder, ComponentKind.SHELL, "", "Manto soldado",
                Condition.REPAIR, "Reutilizar previa reparación de falta de fusión", ot1702, 0.97);
        addComponent(builder, ComponentKind.SEAL, "", "TS 64",
                Condition.REPLACE, "Desgaste abrasivo", ot1702, 0.97);

        builder.event(event("OT-262", 2018, ReportPhase.EVALUATION,
                "Minera Spence", "Polea N°6 CV 12", "4507181083",
                set(ot262), list("Levantamiento dimensional completo")));
        builder.event(event("OT-1702", 2026, ReportPhase.EVALUATION,
                "Minera Spence BHP", "LORBRAND BP5MSCP00273", "4519630127-10",
                set(ot1702, wip), list("Manto reutilizable; eje y soportes rechazados")));
        return builder.build();
    }

    private static MaterialFamily code10415860() {
        String ot243 = "drive:1O1m-dGoQq-kdgpyqiWM45SFgQPwm7Exj";
        String ot470 = "drive:1mFhrRoaSjzPmq7z3gg2v3h2HhyLycNeE";
        String ot471 = "drive:1myXPxV-PrTXSmbh7lNext8YRJhEDMIBf";
        String ot1645 = "drive:1hLpBeLYaehN9lsQ8dgVUm0ZlyLaMrtwE";

        MaterialFamily.Builder builder = MaterialFamily.builder("10415860")
                .client("Minera Spence BHP")
                .roleHint("deflectora")
                .alias("Polea N°4 CV-13")
                .alias("Polea Deflectora 041 CV 022")
                .alias("POLEA,BP,LORBRAND BP1MSCP00269")
                .source(source(ot243, "OT 243 Informe Evaluación Polea 4 CV-13",
                        "https://drive.google.com/file/d/1O1m-dGoQq-kdgpyqiWM45SFgQPwm7Exj",
                        ReportPhase.EVALUATION, "OT-243", "10415860", "2018-09-14"))
                .source(source(ot470, "OT 470 Informe Final Polea Deflectora 041 CV 022",
                        "https://drive.google.com/file/d/1mFhrRoaSjzPmq7z3gg2v3h2HhyLycNeE",
                        ReportPhase.ASSEMBLY, "OT-470", "10415860", "2019-09-17"))
                .source(source(ot471, "OT 471 Informe Final Polea Deflectora 041 CV 022",
                        "https://drive.google.com/file/d/1myXPxV-PrTXSmbh7lNext8YRJhEDMIBf",
                        ReportPhase.ASSEMBLY, "OT-471", "10415860", "2019-09-17"))
                .source(source(ot1645, "OT-1645 Informe inspección LORBRAND BP1MSCP00269",
                        "https://drive.google.com/file/d/1hLpBeLYaehN9lsQ8dgVUm0ZlyLaMrtwE",
                        ReportPhase.EVALUATION, "OT-1645", "10415860", "2026-03-05"));

        addDimension(builder, DimensionKind.SHELL_DIAMETER, 800.0,
                "Diámetro del Manto A", ot243, 0.99);
        addDimension(builder, DimensionKind.SHELL_LENGTH, 1520.0,
                "Largo del Manto C", ot243, 0.99);
        addDimension(builder, DimensionKind.LAGGING_THICKNESS, 20.0,
                "Espesor revestimiento liso", ot243, 0.98);
        addDimension(builder, DimensionKind.SHELL_THICKNESS, 30.0,
                "Espesor del Manto D", ot243, 0.98);
        addDimension(builder, DimensionKind.SUPPORT_CENTRE_DISTANCE, 2087.0,
                "Distancia entre soportes", ot243, 0.98);
        addDimension(builder, DimensionKind.BEARING_CENTRE_DISTANCE, 2067.0,
                "Distancia entre rodamientos", ot243, 0.98);
        addDimension(builder, DimensionKind.SUPPORT_BORE_DIAMETER, 400.0,
                "Diámetro nominal soporte H7", ot243, 0.98);
        addDimension(builder, DimensionKind.SHAFT_TOTAL_LENGTH, 2288.0,
                "L10 largo total eje", ot243, 0.96);
        addDimension(builder, DimensionKind.SHAFT_BEARING_DIAMETER, 220.0,
                "Zona de rodamiento", ot243, 0.98);
        addDimension(builder, DimensionKind.SHAFT_LOCKING_DIAMETER, 260.0,
                "Zona de manguitos de expansión", ot243, 0.98);

        addComponent(builder, ComponentKind.SUPPORT, "", "SD 3148",
                Condition.REPLACE, "Modelo recomendado por soportes fuera de tolerancia", ot243, 0.96);
        addComponent(builder, ComponentKind.BEARING, "", "23148 CCK/C3W33",
                Condition.REPLACE, "Fin de vida útil", ot243, 0.95);
        addComponent(builder, ComponentKind.BEARING_ADAPTER, "SKF", "OH 3148 H",
                Condition.REPLACE, "Corrosión por contacto", ot243, 0.96);
        addComponent(builder, ComponentKind.LAGGING, "", "Liso 20 mm",
                Condition.REPLACE, "Retiro de engomado dentro de evaluación", ot243, 0.88);

        builder.event(event("OT-243", 2018, ReportPhase.EVALUATION,
                "Minera Spence", "Polea N°4 CV-13", "4506992233",
                set(ot243), list("Levantamiento dimensional completo")));
        builder.event(event("OT-470", 2019, ReportPhase.ASSEMBLY,
                "Minera Spence", "Polea Deflectora 041 CV 022", "",
                set(ot470), list("Armado final")));
        builder.event(event("OT-471", 2019, ReportPhase.ASSEMBLY,
                "Minera Spence", "Polea Deflectora 041 CV 022", "",
                set(ot471), list("Armado final")));
        builder.event(event("OT-1645", 2026, ReportPhase.EVALUATION,
                "Minera Spence BHP", "LORBRAND BP1MSCP00269", "",
                set(ot1645), list("Nueva intervención de la familia")));
        return builder.build();
    }

    private static MaterialFamily code4162054() {
        String drawing651 = "drive:11JT4Igi-Hd0W_9yHSmbrm3nImzZ29yxK";
        String ot847 = "drive:1ufUXBTlpjNR80rfUSeAl5X6vPXUcyezQ";
        String ot865 = "drive:1ViZTAVEjFrjJcPyc6qpQKuF71K1S6VaX";

        MaterialFamily.Builder builder = MaterialFamily.builder("4162054")
                .client("Minera Gabriela Mistral")
                .roleHint("motriz")
                .alias("Polea Motriz Pos. 2 147CV014")
                .alias("Polea Motriz 147CV013-014")
                .alias("Polea Motriz 147CV013-015")
                .source(source(drawing651, "OT 651 Plano Polea Motriz Pos. 2 147CV014",
                        "https://drive.google.com/file/d/11JT4Igi-Hd0W_9yHSmbrm3nImzZ29yxK",
                        ReportPhase.DRAWING, "OT-651", "4162054", "2020-08-19"))
                .source(source(ot847, "OT-847 Informe final preservación Polea Motriz",
                        "https://drive.google.com/file/d/1ufUXBTlpjNR80rfUSeAl5X6vPXUcyezQ",
                        ReportPhase.PRESERVATION, "OT-847", "4162054", "2021-06-18"))
                .source(source(ot865, "OT-865 Informe Evaluación Polea Motriz",
                        "https://drive.google.com/file/d/1ViZTAVEjFrjJcPyc6qpQKuF71K1S6VaX",
                        ReportPhase.EVALUATION, "OT-865", "4162054", "2021-08-16"));

        addDimension(builder, DimensionKind.SHELL_LENGTH, 2032.0,
                "Longitud total del cuerpo indicada en plano; semántica a confirmar", drawing651, 0.82);
        addDimension(builder, DimensionKind.BELT_FACE_WIDTH, 1524.0,
                "Ancho central 1524 indicado en plano", drawing651, 0.85);
        addDimension(builder, DimensionKind.SHAFT_TOTAL_LENGTH, 3051.18,
                "Largo total eje en plano", drawing651, 0.98);
        addDimension(builder, DimensionKind.SHAFT_BEARING_DIAMETER, 150.81,
                "Zona de rodamiento h9", drawing651, 0.96);
        addDimension(builder, DimensionKind.SHAFT_LOCKING_DIAMETER, 170.0,
                "Zona de manguito de expansión h8", drawing651, 0.96);

        addComponent(builder, ComponentKind.SUPPORT, "SKF", "SAFS 534",
                Condition.REPLACE, "Soportes fuera de tolerancia en OT-865", ot865, 0.98);
        addComponent(builder, ComponentKind.BEARING, "SKF", "22234 CCK/W33",
                Condition.REPLACE, "Mal contacto y contaminación", ot865, 0.98);
        addComponent(builder, ComponentKind.BEARING_ADAPTER, "", "H3134.515",
                Condition.REPLACE, "Marcas de mal ajuste", ot865, 0.97);
        addComponent(builder, ComponentKind.LOCKING_ELEMENT, "", "B115 170x225 mm",
                Condition.REPLACE, "Deformado y sin trazabilidad", ot865, 0.98);
        addComponent(builder, ComponentKind.SHAFT, "", "SAE 4340",
                Condition.REPAIR, "Reutilizable con rectificado y metalizado", ot865, 0.98);
        addComponent(builder, ComponentKind.COUPLING, "FALK", "1140T10",
                Condition.UNKNOWN, "Indicado en plano de armado", drawing651, 0.96);
        addComponent(builder, ComponentKind.BACKSTOP, "FALK", "1095 NRT",
                Condition.UNKNOWN, "Indicado en plano de armado", drawing651, 0.96);
        addComponent(builder, ComponentKind.SEAL, "", "TER-140",
                Condition.REPLACE, "Mal estado en OT-865", ot865, 0.96);
        addComponent(builder, ComponentKind.SHELL, "", "Manto polea",
                Condition.REPLACE, "Espesor/alojamientos fuera de tolerancia", ot865, 0.97);

        builder.event(event("OT-651", 2020, ReportPhase.DRAWING,
                "Minera Gaby", "Polea Motriz Pos. 2 147CV014", "",
                set(drawing651), list("Plano de armado, eje y acoplamiento")));
        builder.event(event("OT-847", 2021, ReportPhase.PRESERVATION,
                "Minera DGM", "Polea Motriz 147CV013-014", "",
                set(ot847), list("Preservación")));
        builder.event(event("OT-865", 2021, ReportPhase.EVALUATION,
                "Minera DGM", "Polea Motriz 147CV013-015", "",
                set(ot865), list("Evaluación con fabricación de manto")));
        return builder.build();
    }

    private static MaterialFamily code4196111() {
        String ot867 = "drive:1wecS2IvTfRgAJ25FgRMyolpH11yWMQkJ";
        String ot868 = "drive:1kKgpMaAuL3kGGM2knyD_a67UUYbnZwcr";
        String ot920 = "drive:11TYb4WtmjxcIzelXWqTv5h6WvMZukWAI";
        String ot954 = "drive:12C6GD4kUlSDSCYM-aeh2m_fy-JOvhU6m";
        String ot963 = "drive:1edIFyVrbCWwqT5yNIT0KZgWx2CBS0Kb0";
        String ot1047 = "drive:1DIpRE_dRK9EEVOvr7D7KiwahyAS-SUjg";
        String weekly = "drive:1-SrOjBBjLCbFoZFpQKhu-XUYydu6fyJv";

        MaterialFamily.Builder builder = MaterialFamily.builder("4196111")
                .client("Minera Gabriela Mistral")
                .roleHint("cola")
                .alias("Polea de Cola 140CV006-008")
                .source(source(ot867, "OT-867 Informe final Polea de Cola",
                        "https://drive.google.com/file/d/1wecS2IvTfRgAJ25FgRMyolpH11yWMQkJ",
                        ReportPhase.ASSEMBLY, "OT-867", "4196111", "2021-09-14"))
                .source(source(ot868, "OT-868 Informe final Polea de Cola",
                        "https://drive.google.com/file/d/1kKgpMaAuL3kGGM2knyD_a67UUYbnZwcr",
                        ReportPhase.ASSEMBLY, "OT-868", "4196111", "2021-10-08"))
                .source(source(ot920, "OT-920 Informe final Polea de Cola",
                        "https://drive.google.com/file/d/11TYb4WtmjxcIzelXWqTv5h6WvMZukWAI",
                        ReportPhase.ASSEMBLY, "OT-920", "4196111", "2022"))
                .source(source(ot954, "OT-954 Informe recepción Polea de Cola",
                        "https://drive.google.com/file/d/12C6GD4kUlSDSCYM-aeh2m_fy-JOvhU6m",
                        ReportPhase.RECEIPT, "OT-954", "4196111", "2022"))
                .source(source(ot963, "OT-963 Informe final Polea de Cola",
                        "https://drive.google.com/file/d/1edIFyVrbCWwqT5yNIT0KZgWx2CBS0Kb0",
                        ReportPhase.ASSEMBLY, "OT-963", "4196111", "2022"))
                .source(source(ot1047, "OT-1047 Informe evaluación Polea de Cola",
                        "https://drive.google.com/file/d/1DIpRE_dRK9EEVOvr7D7KiwahyAS-SUjg",
                        ReportPhase.EVALUATION, "OT-1047", "4196111", "2022"))
                .source(source(weekly, "Reporte Control y Calidad 08-06-2022",
                        "https://drive.google.com/file/d/1-SrOjBBjLCbFoZFpQKhu-XUYydu6fyJv",
                        ReportPhase.WIP, "OT-1043", "4196111", "2022-06-08"));

        builder.event(event("OT-867", 2021, ReportPhase.ASSEMBLY,
                "Minera DGM", "Polea de Cola 140CV006-008", "", set(ot867), list()));
        builder.event(event("OT-868", 2021, ReportPhase.ASSEMBLY,
                "Minera DGM", "Polea de Cola 140CV006-008", "", set(ot868), list()));
        builder.event(event("OT-920", 2022, ReportPhase.ASSEMBLY,
                "Minera DGM", "Polea de Cola 140CV006-008", "", set(ot920), list()));
        builder.event(event("OT-954", 2022, ReportPhase.RECEIPT,
                "Minera DGM", "Polea de Cola 140CV006-008", "", set(ot954), list()));
        builder.event(event("OT-963", 2022, ReportPhase.ASSEMBLY,
                "Minera DGM", "Polea de Cola 140CV006-008", "", set(ot963), list()));
        builder.event(event("OT-1043", 2022, ReportPhase.ASSEMBLY,
                "Minera DGM", "Polea DGM", "", set(weekly),
                list("Informe final registrado como cerrado")));
        builder.event(event("OT-1047", 2022, ReportPhase.EVALUATION,
                "Minera Gabriela Mistral", "Polea de Cola 140CV006-008", "",
                set(ot1047, weekly), list("Informe final registrado como cerrado")));
        return builder.build();
    }

    private static MaterialFamily code4196149() {
        String ot781 = "drive:1ZZr3AChhRH0AS0ihr113boXkJo_vxzSd";
        String drawing = "drive:1i99kKPDTQqKbhRu1ULNBcRpZqnPnVakT";
        return MaterialFamily.builder("4196149")
                .client("Minera DGM")
                .roleHint("motriz")
                .alias("Polea Motriz 150CV017")
                .source(source(ot781, "OT-781 Informe final Polea Motriz 150CV017",
                        "https://drive.google.com/file/d/1ZZr3AChhRH0AS0ihr113boXkJo_vxzSd",
                        ReportPhase.ASSEMBLY, "OT-781", "4196149", "2021-10-25"))
                .source(source(drawing, "2.- Polea motriz_4196149",
                        "https://drive.google.com/file/d/1i99kKPDTQqKbhRu1ULNBcRpZqnPnVakT",
                        ReportPhase.DRAWING, "OT-781", "4196149", "2024-11-04"))
                .event(event("OT-781", 2021, ReportPhase.ASSEMBLY,
                        "Minera DGM", "Polea Motriz 150CV017", "",
                        set(ot781, drawing), list("Familia con plano disponible")))
                .build();
    }

    private static MaterialFamily code10510386() {
        String ot270 = "drive:1qcH7Oe6o5ocq0PXUH7_yiXz7Q-itkZXp";
        MaterialFamily.Builder builder = MaterialFamily.builder("10510386")
                .client("Minera Spence")
                .roleHint("motriz")
                .alias("Polea Motriz CV 26")
                .source(source(ot270, "OT 270 Informe Evaluación Polea Motriz CV 26",
                        "https://drive.google.com/file/d/1qcH7Oe6o5ocq0PXUH7_yiXz7Q-itkZXp",
                        ReportPhase.EVALUATION, "OT-270", "10510386", "2018-12-07"));
        addDimension(builder, DimensionKind.LAGGING_THICKNESS, 25.0,
                "Espesor del revestimiento B", ot270, 0.98);
        addDimension(builder, DimensionKind.SUPPORT_CENTRE_DISTANCE, 2080.0,
                "Distancia entre soportes", ot270, 0.98);
        addDimension(builder, DimensionKind.BEARING_CENTRE_DISTANCE, 2054.0,
                "Distancia entre rodamientos", ot270, 0.98);
        addDimension(builder, DimensionKind.SUPPORT_BORE_DIAMETER, 540.0,
                "Diámetro nominal soporte H7", ot270, 0.98);
        addDimension(builder, DimensionKind.SHAFT_TOTAL_LENGTH, 3320.0,
                "L10 largo total eje", ot270, 0.96);
        addDimension(builder, DimensionKind.SHAFT_BEARING_DIAMETER, 300.0,
                "Zona de rodamiento", ot270, 0.98);
        addDimension(builder, DimensionKind.SHAFT_LOCKING_DIAMETER, 340.0,
                "Zona de manguitos de expansión", ot270, 0.98);
        addComponent(builder, ComponentKind.SUPPORT, "", "SD 3164",
                Condition.REPLACE, "Fuera de tolerancia", ot270, 0.97);
        addComponent(builder, ComponentKind.BEARING, "", "23164 CCK W33 C3",
                Condition.REPLACE, "Fin de vida útil", ot270, 0.96);
        addComponent(builder, ComponentKind.BEARING_ADAPTER, "SKF", "OH 3164 H",
                Condition.REPLACE, "Corrosión de contacto", ot270, 0.96);
        addComponent(builder, ComponentKind.SEAL, "", "TNF 64",
                Condition.REUSE, "Sin observaciones", ot270, 0.88);
        builder.event(event("OT-270", 2018, ReportPhase.EVALUATION,
                "Minera Spence", "Polea Motriz CV 26", "4507252320",
                set(ot270), list("El informe no consigna largo ni diámetro del manto")));
        return builder.build();
    }

    private static MaterialFamily code1462827() {
        String ot1570 = "drive:1mRnfRlIzKPN6R6ux_FQi9d3DpfQ-bxdu";
        String ot1691 = "drive:1Nf3bFlVYphW0wmkIl-lL7vVNFtRgYGsq";
        return MaterialFamily.builder("1462827")
                .roleHint("deflectora")
                .alias("Polea Deflectora Tripper BM 202")
                .source(source(ot1570, "OT-1570 Plano Polea Deflectora Tripper BM 202",
                        "https://drive.google.com/file/d/1mRnfRlIzKPN6R6ux_FQi9d3DpfQ-bxdu",
                        ReportPhase.DRAWING, "OT-1570", "1462827", "2026-07-16"))
                .source(source(ot1691, "OT-1691 Plano Polea Deflectora Tripper BM 202",
                        "https://drive.google.com/file/d/1Nf3bFlVYphW0wmkIl-lL7vVNFtRgYGsq",
                        ReportPhase.DRAWING, "OT-1691", "1462827", "2026-05-26"))
                .event(event("OT-1570", 2026, ReportPhase.DRAWING,
                        "", "Polea Deflectora Tripper BM 202", "",
                        set(ot1570), list("Plano revisado")))
                .event(event("OT-1691", 2026, ReportPhase.DRAWING,
                        "", "Polea Deflectora Tripper BM 202", "",
                        set(ot1691), list("Mismo código en una OT posterior")))
                .build();
    }

    private static MaterialFamily code4162045() {
        String ot343 = "drive:167or-wZIguJAmDqWPBl6GsRdxrmmeXKP";
        return MaterialFamily.builder("4162045")
                .client("Minera Gaby")
                .roleHint("cola")
                .alias("Polea de Cola")
                .source(source(ot343, "OT 343 Informe Final Polea de Cola",
                        "https://drive.google.com/file/d/167or-wZIguJAmDqWPBl6GsRdxrmmeXKP",
                        ReportPhase.ASSEMBLY, "OT-343", "4162045", "2019-04-02"))
                .event(event("OT-343", 2019, ReportPhase.ASSEMBLY,
                        "Minera Gaby", "Polea de Cola", "",
                        set(ot343), list("Informe final de armado")))
                .build();
    }

    private static SourceDocument source(
            String id,
            String title,
            String uri,
            ReportPhase phase,
            String ot,
            String materialCode,
            String date
    ) {
        return new SourceDocument(id, title, uri, phase, ot, materialCode, date);
    }

    private static InterventionEvent event(
            String ot,
            int year,
            ReportPhase phase,
            String client,
            String component,
            String purchaseOrder,
            Set<String> sources,
            List<String> notes
    ) {
        return new InterventionEvent(
                ot,
                year,
                phase,
                client,
                component,
                purchaseOrder,
                sources,
                notes
        );
    }

    private static void addDimension(
            MaterialFamily.Builder builder,
            DimensionKind kind,
            double value,
            String label,
            String source,
            double confidence
    ) {
        builder.dimension(new DimensionEvidence(kind, value, label, source, confidence));
    }

    private static void addComponent(
            MaterialFamily.Builder builder,
            ComponentKind kind,
            String manufacturer,
            String model,
            Condition condition,
            String note,
            String source,
            double confidence
    ) {
        builder.component(new ComponentEvidence(
                kind,
                manufacturer,
                model,
                condition,
                note,
                source,
                confidence
        ));
    }

    private static Set<String> set(String... values) {
        return new LinkedHashSet<>(Arrays.asList(values));
    }

    private static List<String> list(String... values) {
        return new ArrayList<>(Arrays.asList(values));
    }
}

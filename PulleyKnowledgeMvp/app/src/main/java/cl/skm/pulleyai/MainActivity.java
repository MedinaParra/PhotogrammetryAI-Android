package cl.skm.pulleyai;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;
import android.graphics.Color;
import android.os.Bundle;
import android.text.InputType;
import android.view.Gravity;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class MainActivity extends Activity {
    private static final String[] CRITICAL = {
            "SHELL_LENGTH", "SHELL_DIAMETER", "SUPPORT_CENTRE_DISTANCE", "BEARING_CENTRE_DISTANCE"
    };

    private DbHelper db;
    private EditText codeInput;
    private EditText otInput;
    private EditText lengthInput;
    private EditText diameterInput;
    private TextView resultText;
    private TextView overlayText;
    private LinearLayout reviewContainer;
    private String activeSessionId;
    private String activeMaterialCode;
    private String activeAction;

    @Override
    protected void onCreate(Bundle state) {
        super.onCreate(state);
        db = new DbHelper(this);
        db.getWritableDatabase();
        buildUi();
    }

    @Override
    protected void onDestroy() {
        db.close();
        super.onDestroy();
    }

    private void buildUi() {
        ScrollView scroll = new ScrollView(this);
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(18), dp(18), dp(18), dp(28));
        root.setBackgroundColor(Color.rgb(246, 248, 250));
        scroll.addView(root);

        TextView title = text("SKM Polea AI", 26, true);
        title.setTextColor(Color.rgb(20, 44, 63));
        root.addView(title);

        TextView subtitle = text(
                "Motor local SQLite para identificar familias de poleas y validar cotas antes de superponer un modelo.",
                15,
                false
        );
        subtitle.setPadding(0, dp(4), 0, dp(16));
        root.addView(subtitle);

        root.addView(section("DATOS DEL LEVANTAMIENTO"));
        codeInput = input("Código de material / SAP / SC (opcional)", InputType.TYPE_CLASS_TEXT);
        otInput = input("OT (opcional, por ejemplo 1702)", InputType.TYPE_CLASS_TEXT);
        lengthInput = input("Largo del manto en mm (obligatorio)", decimalType());
        diameterInput = input("Diámetro del manto en mm (opcional)", decimalType());
        root.addView(codeInput);
        root.addView(otInput);
        root.addView(lengthInput);
        root.addView(diameterInput);

        Button analyze = button("ANALIZAR E IDENTIFICAR");
        analyze.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View view) { analyze(); }
        });
        root.addView(analyze);

        resultText = text("Ingrese al menos el largo del manto para comenzar.", 15, false);
        resultText.setPadding(dp(12), dp(14), dp(12), dp(14));
        resultText.setBackgroundColor(Color.WHITE);
        root.addView(resultText);

        root.addView(section("VALIDACIÓN GUIADA DE COTAS"));
        overlayText = text("El overlay rígido permanecerá bloqueado hasta validar las cotas críticas.", 14, true);
        overlayText.setPadding(dp(12), dp(10), dp(12), dp(10));
        root.addView(overlayText);

        reviewContainer = new LinearLayout(this);
        reviewContainer.setOrientation(LinearLayout.VERTICAL);
        root.addView(reviewContainer);

        TextView footer = text(
                "MVP lógico v0.1: funciona sin Internet. La captura fotográfica y el overlay 3D se integrarán en la siguiente etapa.",
                12,
                false
        );
        footer.setTextColor(Color.DKGRAY);
        footer.setPadding(0, dp(24), 0, 0);
        root.addView(footer);

        setContentView(scroll);
    }

    private void analyze() {
        Double shellLength = parsePositive(lengthInput.getText().toString());
        if (shellLength == null) {
            Toast.makeText(this, "Ingrese un largo de manto válido.", Toast.LENGTH_LONG).show();
            lengthInput.requestFocus();
            return;
        }
        Double diameter = parsePositive(diameterInput.getText().toString());
        String code = normalizeCode(codeInput.getText().toString());
        String ot = normalizeOt(otInput.getText().toString());

        List<Candidate> candidates = db.rankCandidates(code, ot, shellLength.doubleValue(), diameter);
        if (candidates.isEmpty()) {
            activeSessionId = UUID.randomUUID().toString();
            activeMaterialCode = null;
            activeAction = "NO_MATCH";
            db.saveSession(activeSessionId, code, ot, shellLength.doubleValue(), diameter, null, 0.0, activeAction);
            resultText.setText("No se encontró una familia suficientemente compatible. Se requiere reconstrucción paramétrica y revisión manual.");
            reviewContainer.removeAllViews();
            refreshOverlayStatus();
            return;
        }

        Candidate best = candidates.get(0);
        activeSessionId = UUID.randomUUID().toString();
        activeMaterialCode = best.code;
        activeAction = best.action;
        db.saveSession(
                activeSessionId,
                code,
                ot,
                shellLength.doubleValue(),
                diameter,
                best.code,
                best.score,
                best.action
        );

        StringBuilder report = new StringBuilder();
        report.append("Familia probable: ").append(best.code)
                .append("\nConfianza lógica: ").append(Math.round(best.score * 100.0)).append(" %")
                .append("\nEstado: ").append(actionLabel(best.action))
                .append("\nTipo/alias: ").append(best.alias)
                .append("\nOTs relacionadas: ").append(db.otsForCode(best.code));
        if (!best.warning.isEmpty()) report.append("\nAdvertencia: ").append(best.warning);
        resultText.setText(report.toString());

        seedCurrentMeasurements(shellLength.doubleValue(), diameter);
        renderSuggestions();
        refreshOverlayStatus();
    }

    private void seedCurrentMeasurements(double shellLength, Double diameter) {
        DimensionRecord historicalLength = db.dimension(activeMaterialCode, "SHELL_LENGTH");
        if (historicalLength != null) {
            double rel = relativeError(shellLength, historicalLength.value);
            db.saveResponse(
                    activeSessionId,
                    "SHELL_LENGTH",
                    historicalLength.value,
                    rel <= 0.025 ? "CONFIRMED_MATCH" : "CORRECTED",
                    shellLength,
                    rel <= 0.025 ? "Largo obligatorio compatible" : "Largo ingresado difiere del histórico"
            );
        }
        if (diameter != null) {
            DimensionRecord historicalDiameter = db.dimension(activeMaterialCode, "SHELL_DIAMETER");
            if (historicalDiameter != null) {
                double rel = relativeError(diameter.doubleValue(), historicalDiameter.value);
                db.saveResponse(
                        activeSessionId,
                        "SHELL_DIAMETER",
                        historicalDiameter.value,
                        rel <= 0.03 ? "CONFIRMED_MATCH" : "CORRECTED",
                        diameter,
                        rel <= 0.03 ? "Diámetro compatible con historial" : "Diámetro medido reemplaza sugerencia"
                );
            }
        }
    }

    private void renderSuggestions() {
        reviewContainer.removeAllViews();
        if (activeMaterialCode == null) return;
        List<DimensionRecord> dimensions = db.dimensions(activeMaterialCode);
        for (final DimensionRecord dimension : dimensions) {
            LinearLayout card = new LinearLayout(this);
            card.setOrientation(LinearLayout.VERTICAL);
            card.setPadding(dp(12), dp(10), dp(12), dp(10));
            LinearLayout.LayoutParams cardParams = new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
            );
            cardParams.setMargins(0, dp(7), 0, 0);
            card.setLayoutParams(cardParams);
            card.setBackgroundColor(Color.WHITE);

            ResponseRecord response = db.response(activeSessionId, dimension.kind);
            String status = response == null ? "PENDIENTE" : response.status;
            double effective = response != null && response.measuredValue != null
                    ? response.measuredValue.doubleValue()
                    : dimension.value;

            TextView label = text(labelForKind(dimension.kind), 16, true);
            card.addView(label);
            TextView value = text(
                    String.format(
                            Locale.ROOT,
                            "Sugerida: %.1f mm  |  Efectiva: %.1f mm\nOrigen: %s  |  Confianza: %d %%\nEstado: %s",
                            dimension.value,
                            effective,
                            dimension.source,
                            Math.round(dimension.confidence * 100.0),
                            responseLabel(status)
                    ),
                    13,
                    false
            );
            card.addView(value);

            Button validate = button(response == null ? "VALIDAR COTA" : "CAMBIAR RESPUESTA");
            validate.setOnClickListener(new View.OnClickListener() {
                @Override public void onClick(View view) { askDimension(dimension); }
            });
            card.addView(validate);
            reviewContainer.addView(card);
        }
    }

    private void askDimension(final DimensionRecord dimension) {
        String[] choices = {
                "Coincide con el levantamiento",
                "Corregir valor",
                "No coincide",
                "No fue posible medir"
        };
        new AlertDialog.Builder(this)
                .setTitle(labelForKind(dimension.kind))
                .setMessage(String.format(
                        Locale.ROOT,
                        "Valor sugerido: %.1f mm\nTolerancia de revisión: ±%.1f mm\nFuente: %s",
                        dimension.value,
                        toleranceFor(dimension.kind, dimension.value),
                        dimension.source
                ))
                .setItems(choices, new android.content.DialogInterface.OnClickListener() {
                    @Override public void onClick(android.content.DialogInterface dialog, int which) {
                        if (which == 0) {
                            db.saveResponse(activeSessionId, dimension.kind, dimension.value,
                                    "CONFIRMED_MATCH", dimension.value, "Confirmado por operador");
                            afterAnswer();
                        } else if (which == 1) {
                            askCorrection(dimension);
                        } else if (which == 2) {
                            db.saveResponse(activeSessionId, dimension.kind, dimension.value,
                                    "DOES_NOT_MATCH", null, "No coincide; requiere nueva medición o variante");
                            afterAnswer();
                        } else {
                            db.saveResponse(activeSessionId, dimension.kind, dimension.value,
                                    "NOT_MEASURED", null, "No fue posible medir en terreno");
                            afterAnswer();
                        }
                    }
                })
                .show();
    }

    private void askCorrection(final DimensionRecord dimension) {
        final EditText input = input("Valor real medido en mm", decimalType());
        input.setText(String.format(Locale.ROOT, "%.1f", dimension.value));
        int pad = dp(20);
        LinearLayout wrapper = new LinearLayout(this);
        wrapper.setPadding(pad, 0, pad, 0);
        wrapper.addView(input);
        new AlertDialog.Builder(this)
                .setTitle("Corregir " + labelForKind(dimension.kind))
                .setView(wrapper)
                .setPositiveButton("GUARDAR", new android.content.DialogInterface.OnClickListener() {
                    @Override public void onClick(android.content.DialogInterface dialog, int which) {
                        Double measured = parsePositive(input.getText().toString());
                        if (measured == null) {
                            Toast.makeText(MainActivity.this, "Valor inválido; no se guardó.", Toast.LENGTH_LONG).show();
                            return;
                        }
                        double delta = measured.doubleValue() - dimension.value;
                        db.saveResponse(
                                activeSessionId,
                                dimension.kind,
                                dimension.value,
                                "CORRECTED",
                                measured,
                                String.format(Locale.ROOT, "Corrección operador: %+.1f mm", delta)
                        );
                        afterAnswer();
                    }
                })
                .setNegativeButton("CANCELAR", null)
                .show();
    }

    private void afterAnswer() {
        renderSuggestions();
        refreshOverlayStatus();
    }

    private void refreshOverlayStatus() {
        if (activeSessionId == null) {
            overlayText.setText("Overlay rígido bloqueado: aún no existe una identificación.");
            overlayText.setTextColor(Color.rgb(150, 30, 30));
            return;
        }
        if (!"DIRECT_FAMILY_MATCH".equals(activeAction) && !"HISTORICAL_FAMILY_NO_LENGTH".equals(activeAction)) {
            overlayText.setText("Overlay rígido bloqueado por estado de identificación: " + actionLabel(activeAction));
            overlayText.setTextColor(Color.rgb(150, 30, 30));
            return;
        }
        List<String> pending = new ArrayList<String>();
        for (String kind : CRITICAL) {
            DimensionRecord d = activeMaterialCode == null ? null : db.dimension(activeMaterialCode, kind);
            if (d == null) {
                pending.add(labelForKind(kind) + " (sin historial)");
                continue;
            }
            ResponseRecord r = db.response(activeSessionId, kind);
            if (r == null || !("CONFIRMED_MATCH".equals(r.status) || "CORRECTED".equals(r.status))) {
                pending.add(labelForKind(kind));
            }
        }
        if (pending.isEmpty()) {
            overlayText.setText("OVERLAY RÍGIDO HABILITADO: todas las cotas críticas están confirmadas o corregidas.");
            overlayText.setTextColor(Color.rgb(20, 115, 55));
        } else {
            overlayText.setText("Overlay rígido bloqueado. Falta validar: " + join(pending));
            overlayText.setTextColor(Color.rgb(160, 95, 0));
        }
    }

    private TextView section(String text) {
        TextView view = text(text, 13, true);
        view.setTextColor(Color.rgb(35, 84, 117));
        view.setPadding(0, dp(18), 0, dp(5));
        return view;
    }

    private TextView text(String value, int sp, boolean bold) {
        TextView view = new TextView(this);
        view.setText(value);
        view.setTextSize(sp);
        view.setTextColor(Color.rgb(35, 39, 42));
        if (bold) view.setTypeface(android.graphics.Typeface.DEFAULT_BOLD);
        return view;
    }

    private EditText input(String hint, int type) {
        EditText edit = new EditText(this);
        edit.setHint(hint);
        edit.setInputType(type);
        edit.setSingleLine(true);
        edit.setTextSize(16);
        edit.setPadding(dp(10), dp(10), dp(10), dp(10));
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
        );
        params.setMargins(0, dp(5), 0, 0);
        edit.setLayoutParams(params);
        return edit;
    }

    private Button button(String label) {
        Button button = new Button(this);
        button.setText(label);
        button.setAllCaps(false);
        button.setTextSize(14);
        button.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
        );
        params.setMargins(0, dp(8), 0, 0);
        button.setLayoutParams(params);
        return button;
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }

    private static int decimalType() {
        return InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_FLAG_DECIMAL;
    }

    private static Double parsePositive(String raw) {
        if (raw == null) return null;
        String value = raw.trim().replace(',', '.');
        if (value.isEmpty()) return null;
        try {
            double parsed = Double.parseDouble(value);
            return parsed > 0.0 && Double.isFinite(parsed) ? Double.valueOf(parsed) : null;
        } catch (NumberFormatException ignored) {
            return null;
        }
    }

    private static String normalizeCode(String raw) {
        if (raw == null || raw.trim().isEmpty()) return "";
        String value = raw.toUpperCase(Locale.ROOT).trim();
        value = value.replaceFirst(
                "^(?:STOCK\\s*CODE|C[ÓO]DIGO\\s*(?:DE\\s*)?MATERIAL|C[ÓO]DIGO\\s*SAP|SAP|SC)\\s*[:#-]?\\s*",
                ""
        );
        return value.replaceAll("[^A-Z0-9]", "");
    }

    private static String normalizeOt(String raw) {
        if (raw == null || raw.trim().isEmpty()) return "";
        Matcher matcher = Pattern.compile("([0-9]{2,6})").matcher(raw);
        String digits = "";
        while (matcher.find()) digits = matcher.group(1);
        if (digits.isEmpty()) return "";
        digits = digits.replaceFirst("^0+(?!$)", "");
        return "OT-" + digits;
    }

    private static double relativeError(double observed, double expected) {
        return Math.abs(observed - expected) / Math.max(1.0, expected);
    }

    private static String join(List<String> values) {
        StringBuilder out = new StringBuilder();
        for (int i = 0; i < values.size(); i++) {
            if (i > 0) out.append(", ");
            out.append(values.get(i));
        }
        return out.toString();
    }

    private static String labelForKind(String kind) {
        if ("SHELL_LENGTH".equals(kind)) return "Largo del manto";
        if ("SHELL_DIAMETER".equals(kind)) return "Diámetro del manto";
        if ("SUPPORT_CENTRE_DISTANCE".equals(kind)) return "Distancia entre centros de soportes";
        if ("BEARING_CENTRE_DISTANCE".equals(kind)) return "Distancia entre centros de rodamientos";
        if ("SHAFT_TOTAL_LENGTH".equals(kind)) return "Largo total del eje";
        if ("SHAFT_BEARING_DIAMETER".equals(kind)) return "Diámetro de eje en rodamientos";
        if ("SHAFT_LOCKING_DIAMETER".equals(kind)) return "Diámetro de eje en manguitos";
        if ("SUPPORT_BORE_DIAMETER".equals(kind)) return "Diámetro del alojamiento de soporte";
        if ("LAGGING_THICKNESS".equals(kind)) return "Espesor del revestimiento";
        if ("SHELL_THICKNESS".equals(kind)) return "Espesor del manto";
        return kind;
    }

    private static double toleranceFor(String kind, double value) {
        double fraction = ("SHELL_LENGTH".equals(kind) || "SHELL_DIAMETER".equals(kind)) ? 0.025 : 0.01;
        return Math.max(2.0, value * fraction);
    }

    private static String actionLabel(String action) {
        if ("DIRECT_FAMILY_MATCH".equals(action)) return "Coincidencia directa";
        if ("VERIFY_VARIANT".equals(action)) return "Verificar variante";
        if ("HISTORICAL_FAMILY_NO_LENGTH".equals(action)) return "Familia histórica sin largo consolidado";
        if ("VISUAL_ONLY".equals(action)) return "Coincidencia orientativa";
        return "Sin coincidencia";
    }

    private static String responseLabel(String status) {
        if ("CONFIRMED_MATCH".equals(status)) return "COINCIDE";
        if ("CORRECTED".equals(status)) return "CORREGIDA";
        if ("DOES_NOT_MATCH".equals(status)) return "NO COINCIDE";
        if ("NOT_MEASURED".equals(status)) return "NO MEDIDA";
        return "PENDIENTE";
    }

    static final class Candidate {
        final String code;
        final String alias;
        final double score;
        final String action;
        final String warning;

        Candidate(String code, String alias, double score, String action, String warning) {
            this.code = code;
            this.alias = alias;
            this.score = score;
            this.action = action;
            this.warning = warning;
        }
    }

    static final class DimensionRecord {
        final String kind;
        final double value;
        final String source;
        final double confidence;

        DimensionRecord(String kind, double value, String source, double confidence) {
            this.kind = kind;
            this.value = value;
            this.source = source;
            this.confidence = confidence;
        }
    }

    static final class ResponseRecord {
        final String status;
        final Double measuredValue;
        final String note;

        ResponseRecord(String status, Double measuredValue, String note) {
            this.status = status;
            this.measuredValue = measuredValue;
            this.note = note;
        }
    }

    static final class DbHelper extends SQLiteOpenHelper {
        private static final String DB = "pulley_knowledge.db";
        private static final int VERSION = 1;

        DbHelper(Context context) {
            super(context.getApplicationContext(), DB, null, VERSION);
        }

        @Override public void onConfigure(SQLiteDatabase database) {
            super.onConfigure(database);
            database.setForeignKeyConstraintsEnabled(true);
        }

        @Override public void onCreate(SQLiteDatabase database) {
            database.execSQL("CREATE TABLE family(code TEXT PRIMARY KEY, alias TEXT NOT NULL, role TEXT NOT NULL DEFAULT '')");
            database.execSQL("CREATE TABLE family_ot(code TEXT NOT NULL, ot TEXT NOT NULL, PRIMARY KEY(code,ot), FOREIGN KEY(code) REFERENCES family(code) ON DELETE CASCADE)");
            database.execSQL("CREATE INDEX idx_family_ot ON family_ot(ot)");
            database.execSQL("CREATE TABLE dimension(code TEXT NOT NULL, kind TEXT NOT NULL, value_mm REAL NOT NULL CHECK(value_mm>0), source TEXT NOT NULL, confidence REAL NOT NULL, PRIMARY KEY(code,kind), FOREIGN KEY(code) REFERENCES family(code) ON DELETE CASCADE)");
            database.execSQL("CREATE TABLE identification_session(id TEXT PRIMARY KEY, created_at INTEGER NOT NULL, input_code TEXT, input_ot TEXT, shell_length_mm REAL NOT NULL, diameter_mm REAL, selected_code TEXT, score REAL NOT NULL, action TEXT NOT NULL)");
            database.execSQL("CREATE TABLE dimension_response(session_id TEXT NOT NULL, kind TEXT NOT NULL, suggested_value REAL NOT NULL, status TEXT NOT NULL, measured_value REAL, note TEXT NOT NULL DEFAULT '', updated_at INTEGER NOT NULL, PRIMARY KEY(session_id,kind), FOREIGN KEY(session_id) REFERENCES identification_session(id) ON DELETE CASCADE)");
            seed(database);
        }

        @Override public void onUpgrade(SQLiteDatabase database, int oldVersion, int newVersion) {
            throw new IllegalStateException("No destructive migration is allowed");
        }

        private static void seed(SQLiteDatabase database) {
            addFamily(database, "10415863", "Polea BP LORBRAND BP5MSCP00273 / Polea N°6 CV12", "DEFLECTORA", new String[]{"OT-262", "OT-1702"});
            addDimension(database, "10415863", "SHELL_LENGTH", 1520, "OT-262 Informe de Evaluación", 0.99);
            addDimension(database, "10415863", "SHELL_DIAMETER", 1400, "OT-262 Informe de Evaluación", 0.99);
            addDimension(database, "10415863", "SUPPORT_CENTRE_DISTANCE", 2080, "OT-262 Informe de Evaluación", 0.98);
            addDimension(database, "10415863", "BEARING_CENTRE_DISTANCE", 2030, "OT-262 Informe de Evaluación", 0.98);
            addDimension(database, "10415863", "SHAFT_TOTAL_LENGTH", 2355, "OT-262 Levantamiento dimensional", 0.96);
            addDimension(database, "10415863", "SHAFT_BEARING_DIAMETER", 300, "OT-262 Levantamiento dimensional", 0.98);
            addDimension(database, "10415863", "SHAFT_LOCKING_DIAMETER", 320, "OT-262 Levantamiento dimensional", 0.98);
            addDimension(database, "10415863", "SUPPORT_BORE_DIAMETER", 540, "OT-262 Informe de Evaluación", 0.98);
            addDimension(database, "10415863", "LAGGING_THICKNESS", 22, "OT-262 Informe de Evaluación", 0.98);
            addDimension(database, "10415863", "SHELL_THICKNESS", 35, "OT-262 Informe de Evaluación", 0.98);

            addFamily(database, "10415860", "Polea BP LORBRAND BP1MSCP00269 / Deflectora 041 CV022", "DEFLECTORA", new String[]{"OT-243", "OT-470", "OT-471", "OT-1645"});
            addDimension(database, "10415860", "SHELL_LENGTH", 1520, "OT-243 Informe de Evaluación", 0.99);
            addDimension(database, "10415860", "SHELL_DIAMETER", 800, "OT-243 Informe de Evaluación", 0.99);
            addDimension(database, "10415860", "SUPPORT_CENTRE_DISTANCE", 2087, "OT-243 Informe de Evaluación", 0.98);
            addDimension(database, "10415860", "BEARING_CENTRE_DISTANCE", 2067, "OT-243 Informe de Evaluación", 0.98);
            addDimension(database, "10415860", "SHAFT_TOTAL_LENGTH", 2288, "OT-243 Levantamiento dimensional", 0.96);
            addDimension(database, "10415860", "SHAFT_BEARING_DIAMETER", 220, "OT-243 Levantamiento dimensional", 0.98);
            addDimension(database, "10415860", "SHAFT_LOCKING_DIAMETER", 260, "OT-243 Levantamiento dimensional", 0.98);
            addDimension(database, "10415860", "SUPPORT_BORE_DIAMETER", 400, "OT-243 Informe de Evaluación", 0.98);
            addDimension(database, "10415860", "LAGGING_THICKNESS", 20, "OT-243 Informe de Evaluación", 0.98);
            addDimension(database, "10415860", "SHELL_THICKNESS", 30, "OT-243 Informe de Evaluación", 0.98);

            addFamily(database, "4162054", "Polea con SAFS 534 / rodamiento 22234 / B115", "POLEA", new String[]{"OT-651", "OT-847", "OT-865"});
            addDimension(database, "4162054", "SHELL_LENGTH", 2032, "Plano histórico; verificar semántica", 0.72);

            addFamily(database, "4196111", "Familia histórica con múltiples intervenciones", "POLEA", new String[]{"OT-867", "OT-868", "OT-920", "OT-954", "OT-963", "OT-1043", "OT-1047"});
            addFamily(database, "4196149", "Familia histórica OT-781", "POLEA", new String[]{"OT-781"});
            addFamily(database, "10510386", "Familia histórica OT-270", "POLEA", new String[]{"OT-270"});
            addFamily(database, "1462827", "Familia histórica OT-1570 / OT-1691", "POLEA", new String[]{"OT-1570", "OT-1691"});
            addFamily(database, "4162045", "Familia histórica OT-343", "POLEA", new String[]{"OT-343"});
        }

        private static void addFamily(SQLiteDatabase database, String code, String alias, String role, String[] ots) {
            ContentValues row = new ContentValues();
            row.put("code", code);
            row.put("alias", alias);
            row.put("role", role);
            database.insertOrThrow("family", null, row);
            for (String ot : ots) {
                ContentValues link = new ContentValues();
                link.put("code", code);
                link.put("ot", ot);
                database.insertOrThrow("family_ot", null, link);
            }
        }

        private static void addDimension(SQLiteDatabase database, String code, String kind, double value, String source, double confidence) {
            ContentValues row = new ContentValues();
            row.put("code", code);
            row.put("kind", kind);
            row.put("value_mm", value);
            row.put("source", source);
            row.put("confidence", confidence);
            database.insertOrThrow("dimension", null, row);
        }

        List<Candidate> rankCandidates(String inputCode, String inputOt, double length, Double diameter) {
            SQLiteDatabase database = getReadableDatabase();
            List<Candidate> result = new ArrayList<Candidate>();
            Cursor cursor = database.rawQuery("SELECT code,alias FROM family ORDER BY code", null);
            try {
                while (cursor.moveToNext()) {
                    String code = cursor.getString(0);
                    String alias = cursor.getString(1);
                    boolean exactCode = !inputCode.isEmpty() && inputCode.equals(code);
                    boolean exactOt = !inputOt.isEmpty() && hasOt(database, code, inputOt);
                    DimensionRecord historicalLength = dimension(database, code, "SHELL_LENGTH");
                    DimensionRecord historicalDiameter = dimension(database, code, "SHELL_DIAMETER");

                    double sum = 0.0;
                    double weights = 0.0;
                    if (!inputCode.isEmpty()) {
                        weights += 5.0;
                        if (exactCode) sum += 5.0;
                    }
                    if (!inputOt.isEmpty()) {
                        weights += 4.0;
                        if (exactOt) sum += 4.0;
                    }
                    weights += 5.0;
                    double lengthError = Double.NaN;
                    if (historicalLength != null) {
                        lengthError = relativeError(length, historicalLength.value);
                        sum += 5.0 * Math.exp(-lengthError / 0.055);
                    } else if (exactCode || exactOt) {
                        sum += 5.0 * 0.70;
                    }
                    if (diameter != null) {
                        weights += 2.0;
                        if (historicalDiameter != null) {
                            double error = relativeError(diameter.doubleValue(), historicalDiameter.value);
                            sum += 2.0 * Math.exp(-error / 0.08);
                        }
                    }
                    double score = weights <= 0 ? 0 : sum / weights;
                    String action;
                    String warning = "";
                    if ((exactCode || exactOt) && historicalLength != null && lengthError > 0.08) {
                        score = Math.min(score, 0.72);
                        action = "VERIFY_VARIANT";
                        warning = "El código/OT coincide, pero el largo obligatorio contradice el historial.";
                    } else if ((exactCode || exactOt) && historicalLength == null) {
                        action = "HISTORICAL_FAMILY_NO_LENGTH";
                        warning = "La familia existe, pero no posee largo histórico consolidado.";
                    } else if ((exactCode || exactOt) && historicalLength != null && lengthError <= 0.08 && score >= 0.70) {
                        action = "DIRECT_FAMILY_MATCH";
                    } else if (score >= 0.55 && historicalLength != null && lengthError <= 0.08) {
                        action = "VISUAL_ONLY";
                        warning = "Candidato orientativo; requiere confirmación antes de uso metrológico.";
                    } else {
                        action = "NO_MATCH";
                    }
                    if (score > 0.10) result.add(new Candidate(code, alias, score, action, warning));
                }
            } finally {
                cursor.close();
            }
            Collections.sort(result, new Comparator<Candidate>() {
                @Override public int compare(Candidate a, Candidate b) {
                    int scoreCompare = Double.compare(b.score, a.score);
                    return scoreCompare != 0 ? scoreCompare : a.code.compareTo(b.code);
                }
            });
            return result;
        }

        private boolean hasOt(SQLiteDatabase database, String code, String ot) {
            Cursor cursor = database.rawQuery("SELECT 1 FROM family_ot WHERE code=? AND ot=? LIMIT 1", new String[]{code, ot});
            try { return cursor.moveToFirst(); } finally { cursor.close(); }
        }

        String otsForCode(String code) {
            Cursor cursor = getReadableDatabase().rawQuery("SELECT ot FROM family_ot WHERE code=? ORDER BY ot", new String[]{code});
            List<String> values = new ArrayList<String>();
            try { while (cursor.moveToNext()) values.add(cursor.getString(0)); } finally { cursor.close(); }
            return values.isEmpty() ? "sin OT" : join(values);
        }

        List<DimensionRecord> dimensions(String code) {
            Cursor cursor = getReadableDatabase().rawQuery(
                    "SELECT kind,value_mm,source,confidence FROM dimension WHERE code=? ORDER BY CASE kind "
                            + "WHEN 'SHELL_LENGTH' THEN 1 WHEN 'SHELL_DIAMETER' THEN 2 "
                            + "WHEN 'SUPPORT_CENTRE_DISTANCE' THEN 3 WHEN 'BEARING_CENTRE_DISTANCE' THEN 4 "
                            + "WHEN 'SHAFT_TOTAL_LENGTH' THEN 5 ELSE 10 END, kind",
                    new String[]{code}
            );
            List<DimensionRecord> result = new ArrayList<DimensionRecord>();
            try {
                while (cursor.moveToNext()) {
                    result.add(new DimensionRecord(cursor.getString(0), cursor.getDouble(1), cursor.getString(2), cursor.getDouble(3)));
                }
            } finally { cursor.close(); }
            return result;
        }

        DimensionRecord dimension(String code, String kind) {
            if (code == null) return null;
            return dimension(getReadableDatabase(), code, kind);
        }

        private static DimensionRecord dimension(SQLiteDatabase database, String code, String kind) {
            Cursor cursor = database.rawQuery("SELECT kind,value_mm,source,confidence FROM dimension WHERE code=? AND kind=?", new String[]{code, kind});
            try {
                return cursor.moveToFirst()
                        ? new DimensionRecord(cursor.getString(0), cursor.getDouble(1), cursor.getString(2), cursor.getDouble(3))
                        : null;
            } finally { cursor.close(); }
        }

        void saveSession(String id, String inputCode, String inputOt, double length, Double diameter, String selectedCode, double score, String action) {
            ContentValues row = new ContentValues();
            row.put("id", id);
            row.put("created_at", System.currentTimeMillis());
            if (inputCode.isEmpty()) row.putNull("input_code"); else row.put("input_code", inputCode);
            if (inputOt.isEmpty()) row.putNull("input_ot"); else row.put("input_ot", inputOt);
            row.put("shell_length_mm", length);
            if (diameter == null) row.putNull("diameter_mm"); else row.put("diameter_mm", diameter.doubleValue());
            if (selectedCode == null) row.putNull("selected_code"); else row.put("selected_code", selectedCode);
            row.put("score", score);
            row.put("action", action);
            getWritableDatabase().insertOrThrow("identification_session", null, row);
        }

        void saveResponse(String sessionId, String kind, double suggested, String status, Double measured, String note) {
            ContentValues row = new ContentValues();
            row.put("session_id", sessionId);
            row.put("kind", kind);
            row.put("suggested_value", suggested);
            row.put("status", status);
            if (measured == null) row.putNull("measured_value"); else row.put("measured_value", measured.doubleValue());
            row.put("note", note == null ? "" : note);
            row.put("updated_at", System.currentTimeMillis());
            getWritableDatabase().insertWithOnConflict("dimension_response", null, row, SQLiteDatabase.CONFLICT_REPLACE);
        }

        ResponseRecord response(String sessionId, String kind) {
            Cursor cursor = getReadableDatabase().rawQuery(
                    "SELECT status,measured_value,note FROM dimension_response WHERE session_id=? AND kind=?",
                    new String[]{sessionId, kind}
            );
            try {
                if (!cursor.moveToFirst()) return null;
                Double measured = cursor.isNull(1) ? null : Double.valueOf(cursor.getDouble(1));
                return new ResponseRecord(cursor.getString(0), measured, cursor.getString(2));
            } finally { cursor.close(); }
        }
    }
}

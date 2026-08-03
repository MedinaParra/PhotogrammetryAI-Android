package cl.skm.pulleyai;

import android.app.Activity;
import android.graphics.Color;
import android.os.Build;
import android.os.Bundle;
import android.text.InputType;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import java.io.File;
import java.io.FileOutputStream;
import java.nio.charset.StandardCharsets;

/** Physical campaign form enriched with automatically collected non-privileged diagnostics. */
public final class DeviceCampaignActivity extends Activity {
    private EditText device;
    private EditText minutes;
    private EditText temperature;
    private EditText rss;
    private EditText crashes;
    private EditText stepImports;
    private EditText reconstructions;
    private CheckBox hashVerified;
    private CheckBox recoveryPassed;
    private TextView diagnosticView;
    private TextView result;
    private DeviceDiagnosticsCore.Result diagnostics;

    @Override protected void onCreate(Bundle state) {
        super.onCreate(state);
        buildUi();
        refreshDiagnostics();
    }

    private void buildUi() {
        ScrollView scroll = new ScrollView(this);
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(20), dp(18), dp(20), dp(28));
        root.setBackgroundColor(Color.rgb(244, 247, 249));
        scroll.addView(root);

        TextView title = text("CAMPAÑA FÍSICA INICIAL", 25, true);
        title.setTextColor(Color.rgb(18, 52, 73));
        root.addView(title);
        TextView note = text(
                "Registre solo pruebas ejecutadas en el teléfono real. La temperatura automática corresponde a batería/estado térmico disponible y no reemplaza termografía ni metrología.",
                13, false);
        note.setPadding(0, dp(4), 0, dp(12));
        root.addView(note);

        diagnosticView = text("Recolectando diagnóstico automático…", 13, false);
        diagnosticView.setPadding(dp(12), dp(10), dp(12), dp(10));
        diagnosticView.setBackgroundColor(Color.WHITE);
        root.addView(diagnosticView);

        Button refresh = new Button(this);
        refresh.setText("ACTUALIZAR DIAGNÓSTICO AUTOMÁTICO");
        refresh.setAllCaps(false);
        refresh.setOnClickListener(view -> refreshDiagnostics());
        root.addView(refresh);

        device = input("Dispositivo", InputType.TYPE_CLASS_TEXT);
        device.setText(Build.MANUFACTURER + " " + Build.MODEL);
        minutes = input("Duración real de captura en minutos", number());
        temperature = input("Temperatura máxima °C (autorrellenada desde batería)", decimal());
        rss = input("RSS máxima MB (autorrellenada)", number());
        crashes = input("Fallos JNI/salidas nativas detectadas", number());
        stepImports = input("STEP reales importados", number());
        reconstructions = input("Reconstrucciones completas", number());
        root.addView(device); root.addView(minutes); root.addView(temperature); root.addView(rss);
        root.addView(crashes); root.addView(stepImports); root.addView(reconstructions);

        hashVerified = new CheckBox(this);
        hashVerified.setText("Hash de APK verificado");
        root.addView(hashVerified);
        recoveryPassed = new CheckBox(this);
        recoveryPassed.setText("Recuperación tras interrupción aprobada");
        root.addView(recoveryPassed);

        Button save = new Button(this);
        save.setText("EVALUAR Y GUARDAR EVIDENCIA");
        save.setAllCaps(false);
        save.setOnClickListener(view -> evaluateAndSave());
        root.addView(save);

        result = text("Sin evidencia registrada.", 14, false);
        result.setPadding(dp(12), dp(12), dp(12), dp(12));
        result.setBackgroundColor(Color.WHITE);
        root.addView(result);

        Button close = new Button(this);
        close.setText("VOLVER");
        close.setAllCaps(false);
        close.setOnClickListener(view -> finish());
        root.addView(close);
        setContentView(scroll);
    }

    private void refreshDiagnostics() {
        diagnostics = AndroidDeviceDiagnosticsCollector.collect(this);
        diagnosticView.setText(diagnostics.summary()
                + (diagnostics.gaps.isEmpty() ? "" : "\nBrechas: " + diagnostics.gaps));
        diagnosticView.setTextColor(diagnostics.state == DeviceDiagnosticsCore.State.COMPLETE
                ? Color.rgb(25, 108, 65) : Color.rgb(145, 82, 0));
        if (diagnostics.record != null) {
            DeviceDiagnosticsCore.Record record = diagnostics.record;
            device.setText(record.device);
            if (Double.isFinite(record.batteryTemperatureC)) {
                temperature.setText(String.format(java.util.Locale.ROOT, "%.1f", record.batteryTemperatureC));
            }
            if (record.pssMb > 0) rss.setText(Integer.toString(record.pssMb));
            crashes.setText(Integer.toString(record.totalNativeEvents()));
        }
    }

    private void evaluateAndSave() {
        try {
            refreshDiagnostics();
            DeviceDiagnosticsCore.Record auto = diagnostics.record;
            InitialDeviceCampaignCore.Record record = new InitialDeviceCampaignCore.Record(
                    device.getText().toString(), hashVerified.isChecked(), integer(minutes),
                    real(temperature), integer(rss), integer(crashes), integer(stepImports),
                    integer(reconstructions), recoveryPassed.isChecked(), diagnostics.automatic(),
                    diagnostics.state.name(), auto == null ? -1 : auto.thermalStatus);
            InitialDeviceCampaignCore.Result evaluation = InitialDeviceCampaignCore.evaluate(record);
            File dir = new File(getFilesDir(), "device-campaigns");
            if (!dir.exists() && !dir.mkdirs()) throw new IllegalStateException("No se pudo crear carpeta de campaña");
            long stamp = System.currentTimeMillis();
            File campaign = new File(dir, "campaign-" + stamp + ".json");
            File diagnostic = new File(dir, "diagnostics-" + stamp + ".json");
            write(campaign, evaluation.canonicalJson(record));
            write(diagnostic, diagnostics.canonicalJson());
            result.setText(evaluation.summary() + "\nDiagnóstico: " + diagnostics.state
                    + "\nArchivos: " + campaign.getName() + " / " + diagnostic.getName());
            result.setTextColor(evaluation.state == InitialDeviceCampaignCore.State.READY
                    ? Color.rgb(25, 108, 65)
                    : evaluation.state == InitialDeviceCampaignCore.State.REVIEW
                    ? Color.rgb(145, 82, 0) : Color.rgb(150, 30, 30));
            Toast.makeText(this, "Evidencia y diagnóstico guardados localmente", Toast.LENGTH_LONG).show();
        } catch (Exception error) {
            result.setText("BLOCKED · complete valores numéricos válidos. " + error.getMessage());
            result.setTextColor(Color.rgb(150, 30, 30));
        }
    }

    private static void write(File target, String content) throws Exception {
        try (FileOutputStream output = new FileOutputStream(target)) {
            output.write(content.getBytes(StandardCharsets.UTF_8));
        }
    }

    private int integer(EditText input) {
        String raw = input.getText().toString().trim();
        return raw.isEmpty() ? 0 : Integer.parseInt(raw);
    }
    private double real(EditText input) {
        String raw = input.getText().toString().trim().replace(',', '.');
        return raw.isEmpty() ? Double.NaN : Double.parseDouble(raw);
    }
    private EditText input(String hint, int type) {
        EditText view = new EditText(this);
        view.setHint(hint); view.setInputType(type); view.setSingleLine(true); return view;
    }
    private TextView text(String value, int sp, boolean bold) {
        TextView view = new TextView(this);
        view.setText(value); view.setTextSize(sp); view.setTextColor(Color.rgb(35, 39, 42));
        if (bold) view.setTypeface(android.graphics.Typeface.DEFAULT_BOLD);
        return view;
    }
    private int number() { return InputType.TYPE_CLASS_NUMBER; }
    private int decimal() { return InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_FLAG_DECIMAL; }
    private int dp(int value) { return Math.round(value * getResources().getDisplayMetrics().density); }
}
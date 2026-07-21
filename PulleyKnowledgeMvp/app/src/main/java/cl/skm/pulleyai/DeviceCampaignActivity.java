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

/** Manual evidence form for the first real Samsung/Honor campaign. */
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
    private TextView result;

    @Override protected void onCreate(Bundle state) {
        super.onCreate(state);
        buildUi();
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
                "Registre solo mediciones obtenidas en el teléfono real. Este formulario no convierte pruebas sintéticas en evidencia física.",
                13, false);
        note.setPadding(0, dp(4), 0, dp(12));
        root.addView(note);

        device = input("Dispositivo", InputType.TYPE_CLASS_TEXT);
        device.setText(Build.MANUFACTURER + " " + Build.MODEL);
        minutes = input("Duración de captura en minutos", number());
        temperature = input("Temperatura máxima °C", decimal());
        rss = input("RSS máxima MB", number());
        crashes = input("Fallos JNI", number());
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

    private void evaluateAndSave() {
        try {
            InitialDeviceCampaignCore.Record record = new InitialDeviceCampaignCore.Record(
                    device.getText().toString(),
                    hashVerified.isChecked(),
                    integer(minutes),
                    real(temperature),
                    integer(rss),
                    integer(crashes),
                    integer(stepImports),
                    integer(reconstructions),
                    recoveryPassed.isChecked());
            InitialDeviceCampaignCore.Result evaluation = InitialDeviceCampaignCore.evaluate(record);
            File dir = new File(getFilesDir(), "device-campaigns");
            if (!dir.exists() && !dir.mkdirs()) throw new IllegalStateException("No se pudo crear carpeta de campaña");
            File target = new File(dir, "campaign-" + System.currentTimeMillis() + ".json");
            try (FileOutputStream output = new FileOutputStream(target)) {
                output.write(evaluation.canonicalJson(record).getBytes(StandardCharsets.UTF_8));
            }
            result.setText(evaluation.summary() + "\nArchivo: " + target.getName());
            result.setTextColor(evaluation.state == InitialDeviceCampaignCore.State.READY
                    ? Color.rgb(25, 108, 65)
                    : evaluation.state == InitialDeviceCampaignCore.State.REVIEW
                    ? Color.rgb(145, 82, 0)
                    : Color.rgb(150, 30, 30));
            Toast.makeText(this, "Evidencia guardada localmente", Toast.LENGTH_LONG).show();
        } catch (Exception error) {
            result.setText("BLOCKED · complete valores numéricos válidos. " + error.getMessage());
            result.setTextColor(Color.rgb(150, 30, 30));
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
        view.setHint(hint);
        view.setInputType(type);
        view.setSingleLine(true);
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

    private int number() { return InputType.TYPE_CLASS_NUMBER; }
    private int decimal() { return InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_FLAG_DECIMAL; }
    private int dp(int value) { return Math.round(value * getResources().getDisplayMetrics().density); }
}

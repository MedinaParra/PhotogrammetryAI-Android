package cl.skm.pulleyai;

import android.app.Activity;
import android.content.Intent;
import android.graphics.Color;
import android.os.Bundle;
import android.view.Gravity;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;

/** Alpha60 entry point exposing the existing product and the custom on-device vision runtime. */
public final class VisionEntryActivity extends Activity {
    private TextView status;

    @Override protected void onCreate(Bundle state) {
        super.onCreate(state);
        buildUi();
    }

    @Override protected void onResume() {
        super.onResume();
        VisionModelStore.Status model = new VisionModelStore(this).status();
        status.setText(model.summary()
                + "\n\nLa IA todavía no interviene en la aceptación de fotografías hasta que "
                + "ambos modelos personalizados superen la validación LiteRT y una campaña física.");
        status.setTextColor(model.ready
                ? Color.rgb(25, 108, 65) : Color.rgb(145, 82, 0));
    }

    private void buildUi() {
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setGravity(Gravity.CENTER_HORIZONTAL);
        root.setPadding(dp(26), dp(24), dp(26), dp(30));
        root.setBackgroundColor(Color.rgb(244, 247, 249));

        TextView title = text("SKM POLEA AI · ALPHA60", 28, true);
        title.setTextColor(Color.rgb(18, 52, 73));
        root.addView(title);

        TextView subtitle = text(
                "Fotogrametría para poleas con visión semántica local: YOLO11n-seg INT8 + MobileNetV3-Small INT8.",
                15, false);
        subtitle.setPadding(0, dp(6), 0, dp(16));
        root.addView(subtitle);

        status = text("Revisando modelos IA…", 13, true);
        status.setPadding(dp(14), dp(13), dp(14), dp(13));
        status.setBackgroundColor(Color.WHITE);
        root.addView(status, new LinearLayout.LayoutParams(-1, -2));

        Button app = button("ABRIR APLICACIÓN DE FOTOGRAMETRÍA");
        app.setOnClickListener(view -> startActivity(new Intent(this, LauncherActivity.class)));
        root.addView(app);

        Button ai = button("INSTALAR / VALIDAR MODELOS IA");
        ai.setOnClickListener(view ->
                startActivity(new Intent(this, VisionModelManagerActivity.class)));
        root.addView(ai);

        TextView scope = text(
                "Alpha60 instala la infraestructura y el contrato de los modelos. No incluye pesos genéricos "
                        + "disfrazados de modelo industrial. El paquete definitivo debe entrenarse con máscaras "
                        + "y etiquetas de poleas reales.", 12, false);
        scope.setPadding(0, dp(16), 0, 0);
        scope.setTextColor(Color.DKGRAY);
        root.addView(scope);
        setContentView(root);
    }

    private TextView text(String value, int sp, boolean bold) {
        TextView view = new TextView(this);
        view.setText(value);
        view.setTextSize(sp);
        view.setTextColor(Color.rgb(35, 39, 42));
        if (bold) view.setTypeface(android.graphics.Typeface.DEFAULT_BOLD);
        return view;
    }

    private Button button(String label) {
        Button button = new Button(this);
        button.setText(label);
        button.setAllCaps(false);
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(-1, -2);
        params.setMargins(0, dp(10), 0, 0);
        button.setLayoutParams(params);
        return button;
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }
}

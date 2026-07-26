package cl.skm.pulleyai;

import android.app.Activity;
import android.content.Intent;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Insets;
import android.graphics.Paint;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.WindowInsets;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Alpha57 point-cloud viewer.
 *
 * The previous OpenGL surface could remain black on some Samsung/Honor drivers even
 * when the JSON contained valid points. This viewer performs the 3D rotation and
 * orthographic projection in Java and draws with Android Canvas, avoiding shaders,
 * EGL configuration and device-specific GL surface composition.
 */
public final class PointCloudViewerActivity extends Activity {
    public static final String EXTRA_REPORT_PATH = "cl.skm.pulleyai.extra.SEED_REPORT_PATH";
    public static final String EXTRA_SUMMARY = "cl.skm.pulleyai.extra.SEED_SUMMARY";
    private static final int SAVE_PLY = 5801;
    private static final int SAVE_XYZ = 5802;

    private CloudData cloud;
    private CloudCanvasView surface;
    private TextView information;

    @Override protected void onCreate(Bundle state) {
        super.onCreate(state);
        getWindow().setStatusBarColor(Color.BLACK);
        getWindow().setNavigationBarColor(Color.BLACK);
        try {
            String path = getIntent().getStringExtra(EXTRA_REPORT_PATH);
            if (path == null || path.trim().isEmpty()) {
                throw new IllegalStateException("No se recibió el resultado de geometría semilla");
            }
            cloud = CloudData.read(new File(path));
            buildUi();
        } catch (Exception error) {
            String message = error.getMessage() == null
                    ? error.getClass().getSimpleName() : error.getMessage();
            TextView failure = new TextView(this);
            failure.setPadding(dp(24), dp(24), dp(24), dp(24));
            failure.setTextSize(18);
            failure.setTextColor(Color.WHITE);
            failure.setBackgroundColor(Color.BLACK);
            failure.setText("NO SE PUDO ABRIR LA NUBE 3D\n\n" + message);
            setContentView(failure);
        }
    }

    private void buildUi() {
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(Color.BLACK);
        applySystemBarInsets(root);

        TextView title = text("RESULTADOS 3D · ALPHA57 LAB2", 20, true);
        title.setTextColor(Color.WHITE);
        root.addView(title);

        information = text(cloud.summary(), 13, false);
        information.setTextColor(Color.WHITE);
        information.setPadding(0, dp(3), 0, dp(4));
        root.addView(information);

        TextView legend = text(
                "VISOR CANVAS COMPATIBLE  ·  PUNTOS BLANCOS  ·  X ROJO  ·  Y VERDE  ·  Z AZUL",
                12, true);
        legend.setTextColor(Color.LTGRAY);
        legend.setGravity(Gravity.CENTER_HORIZONTAL);
        legend.setPadding(0, 0, 0, dp(3));
        root.addView(legend);

        surface = new CloudCanvasView(cloud);
        root.addView(surface, new LinearLayout.LayoutParams(-1, 0, 1f));

        if (cloud.points.isEmpty()) {
            TextView warning = text(
                    "SIN PUNTOS 3D VÁLIDOS: los ejes y la rejilla siguen visibles. "
                            + "El archivo contenía " + cloud.rawPointCount + " registros y se descartaron "
                            + cloud.invalidPointCount + " coordenadas inválidas.",
                    13, true);
            warning.setTextColor(Color.rgb(255, 190, 150));
            warning.setGravity(Gravity.CENTER_HORIZONTAL);
            warning.setPadding(0, dp(3), 0, dp(2));
            root.addView(warning);
        }

        LinearLayout controls = row();
        Button reset = button("CENTRAR");
        reset.setOnClickListener(view -> surface.resetView());
        controls.addView(reset, weight());
        Button smaller = button("PUNTO −");
        smaller.setOnClickListener(view -> surface.changePointSize(-1.5f));
        controls.addView(smaller, weight());
        Button larger = button("PUNTO +");
        larger.setOnClickListener(view -> surface.changePointSize(1.5f));
        controls.addView(larger, weight());
        root.addView(controls);

        LinearLayout exports = row();
        Button ply = button("EXPORTAR PLY");
        ply.setEnabled(!cloud.points.isEmpty());
        ply.setOnClickListener(view -> chooseExport(SAVE_PLY, "nube_semilla_alpha57.ply"));
        exports.addView(ply, weight());
        Button xyz = button("EXPORTAR XYZ");
        xyz.setEnabled(!cloud.points.isEmpty());
        xyz.setOnClickListener(view -> chooseExport(SAVE_XYZ, "nube_semilla_alpha57.xyz"));
        exports.addView(xyz, weight());
        Button close = button("VOLVER");
        close.setOnClickListener(view -> finish());
        exports.addView(close, weight());
        root.addView(exports);

        setContentView(root);
        root.requestApplyInsets();
    }

    private void applySystemBarInsets(LinearLayout root) {
        final int baseHorizontal = dp(10);
        final int baseVertical = dp(8);
        root.setPadding(baseHorizontal, baseVertical, baseHorizontal, baseVertical);
        root.setOnApplyWindowInsetsListener((view, insets) -> {
            int left;
            int top;
            int right;
            int bottom;
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                Insets bars = insets.getInsets(WindowInsets.Type.systemBars());
                left = bars.left;
                top = bars.top;
                right = bars.right;
                bottom = bars.bottom;
            } else {
                left = insets.getSystemWindowInsetLeft();
                top = insets.getSystemWindowInsetTop();
                right = insets.getSystemWindowInsetRight();
                bottom = insets.getSystemWindowInsetBottom();
            }
            view.setPadding(baseHorizontal + left, baseVertical + top,
                    baseHorizontal + right, baseVertical + bottom);
            return insets;
        });
    }

    private void chooseExport(int requestCode, String name) {
        Intent intent = new Intent(Intent.ACTION_CREATE_DOCUMENT);
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        intent.setType("text/plain");
        intent.putExtra(Intent.EXTRA_TITLE, name);
        startActivityForResult(intent, requestCode);
    }

    @Override protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (resultCode != RESULT_OK || data == null || data.getData() == null) return;
        if (requestCode != SAVE_PLY && requestCode != SAVE_XYZ) return;
        String payload = requestCode == SAVE_PLY
                ? PointCloudExportCore.toPly(cloud.points)
                : PointCloudExportCore.toXyz(cloud.points);
        Uri destination = data.getData();
        try (OutputStream output = new BufferedOutputStream(
                getContentResolver().openOutputStream(destination, "w"))) {
            if (output == null) throw new IllegalStateException("No se pudo abrir el destino");
            output.write(payload.getBytes(StandardCharsets.UTF_8));
            Toast.makeText(this, requestCode == SAVE_PLY
                    ? "Nube PLY guardada" : "Nube XYZ guardada", Toast.LENGTH_LONG).show();
        } catch (Exception error) {
            Toast.makeText(this, error.getMessage() == null
                    ? "No se pudo exportar" : error.getMessage(), Toast.LENGTH_LONG).show();
        }
    }

    private LinearLayout row() {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        return row;
    }

    private LinearLayout.LayoutParams weight() {
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(0, -2, 1f);
        params.setMargins(dp(3), dp(3), dp(3), 0);
        return params;
    }

    private TextView text(String value, int sp, boolean bold) {
        TextView view = new TextView(this);
        view.setText(value);
        view.setTextSize(sp);
        if (bold) view.setTypeface(android.graphics.Typeface.DEFAULT_BOLD);
        return view;
    }

    private Button button(String label) {
        Button button = new Button(this);
        button.setText(label);
        button.setAllCaps(false);
        return button;
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }

    private final class CloudCanvasView extends View {
        private static final float AXIS_LENGTH = 1.65f;
        private final float[] normalizedPoints;
        private final Paint gridPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint xPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint yPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint zPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint pointPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint originPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint labelPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint statusPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private float yaw = -35f;
        private float pitch = 22f;
        private float zoom = 1f;
        private float pointRadius;
        private float previousX;
        private float previousY;
        private float previousDistance;

        CloudCanvasView(CloudData data) {
            super(PointCloudViewerActivity.this);
            setBackgroundColor(Color.BLACK);
            setFocusable(true);
            normalizedPoints = normalize(data.points);
            float density = getResources().getDisplayMetrics().density;
            pointRadius = 3.2f * density;

            gridPaint.setColor(Color.rgb(65, 65, 65));
            gridPaint.setStyle(Paint.Style.STROKE);
            gridPaint.setStrokeWidth(Math.max(1f, density));

            configureAxisPaint(xPaint, Color.rgb(255, 55, 55), 2.2f * density);
            configureAxisPaint(yPaint, Color.rgb(70, 255, 95), 2.2f * density);
            configureAxisPaint(zPaint, Color.rgb(65, 135, 255), 2.2f * density);

            pointPaint.setColor(Color.WHITE);
            pointPaint.setStyle(Paint.Style.FILL);
            originPaint.setColor(Color.rgb(255, 215, 40));
            originPaint.setStyle(Paint.Style.FILL);

            labelPaint.setStyle(Paint.Style.FILL);
            labelPaint.setTextSize(14f * density);
            labelPaint.setFakeBoldText(true);
            statusPaint.setColor(Color.LTGRAY);
            statusPaint.setTextSize(10f * density);
        }

        private void configureAxisPaint(Paint paint, int color, float width) {
            paint.setColor(color);
            paint.setStyle(Paint.Style.STROKE);
            paint.setStrokeWidth(width);
            paint.setStrokeCap(Paint.Cap.ROUND);
        }

        @Override protected void onDraw(Canvas canvas) {
            super.onDraw(canvas);
            canvas.drawColor(Color.BLACK);
            int width = getWidth();
            int height = getHeight();
            if (width <= 1 || height <= 1) return;

            float centerX = width * 0.5f;
            float centerY = height * 0.5f;
            float scale = Math.min(width, height) * 0.31f * zoom;

            drawGrid(canvas, centerX, centerY, scale);
            drawCloud(canvas, centerX, centerY, scale);
            drawAxes(canvas, centerX, centerY, scale);

            String status = "CANVAS 3D · " + (normalizedPoints.length / 3)
                    + " PUNTOS · ARRASTRAR=GIRAR · PINZA=ZOOM";
            canvas.drawText(status, dp(8), height - dp(8), statusPaint);
        }

        private void drawGrid(Canvas canvas, float centerX, float centerY, float scale) {
            float limit = 1.5f;
            float step = 0.25f;
            for (float value = -limit; value <= limit + 0.001f; value += step) {
                drawLine3d(canvas, -limit, 0f, value, limit, 0f, value,
                        centerX, centerY, scale, gridPaint);
                drawLine3d(canvas, value, 0f, -limit, value, 0f, limit,
                        centerX, centerY, scale, gridPaint);
            }
        }

        private void drawCloud(Canvas canvas, float centerX, float centerY, float scale) {
            for (int i = 0; i < normalizedPoints.length; i += 3) {
                ScreenPoint point = project(normalizedPoints[i], normalizedPoints[i + 1],
                        normalizedPoints[i + 2], centerX, centerY, scale);
                canvas.drawCircle(point.x, point.y, pointRadius, pointPaint);
            }
        }

        private void drawAxes(Canvas canvas, float centerX, float centerY, float scale) {
            drawLine3d(canvas, -AXIS_LENGTH, 0f, 0f, AXIS_LENGTH, 0f, 0f,
                    centerX, centerY, scale, xPaint);
            drawLine3d(canvas, 0f, -AXIS_LENGTH, 0f, 0f, AXIS_LENGTH, 0f,
                    centerX, centerY, scale, yPaint);
            drawLine3d(canvas, 0f, 0f, -AXIS_LENGTH, 0f, 0f, AXIS_LENGTH,
                    centerX, centerY, scale, zPaint);

            ScreenPoint origin = project(0f, 0f, 0f, centerX, centerY, scale);
            canvas.drawCircle(origin.x, origin.y, Math.max(dp(4), pointRadius * 0.8f), originPaint);

            drawAxisLabel(canvas, "X", AXIS_LENGTH, 0f, 0f,
                    centerX, centerY, scale, xPaint.getColor());
            drawAxisLabel(canvas, "Y", 0f, AXIS_LENGTH, 0f,
                    centerX, centerY, scale, yPaint.getColor());
            drawAxisLabel(canvas, "Z", 0f, 0f, AXIS_LENGTH,
                    centerX, centerY, scale, zPaint.getColor());
        }

        private void drawAxisLabel(Canvas canvas, String label, float x, float y, float z,
                                   float centerX, float centerY, float scale, int color) {
            ScreenPoint point = project(x, y, z, centerX, centerY, scale);
            labelPaint.setColor(color);
            canvas.drawText(label, point.x + dp(5), point.y - dp(5), labelPaint);
        }

        private void drawLine3d(Canvas canvas,
                                float x1, float y1, float z1,
                                float x2, float y2, float z2,
                                float centerX, float centerY, float scale, Paint paint) {
            ScreenPoint a = project(x1, y1, z1, centerX, centerY, scale);
            ScreenPoint b = project(x2, y2, z2, centerX, centerY, scale);
            canvas.drawLine(a.x, a.y, b.x, b.y, paint);
        }

        private ScreenPoint project(float x, float y, float z,
                                    float centerX, float centerY, float scale) {
            double yawRadians = Math.toRadians(yaw);
            double pitchRadians = Math.toRadians(pitch);
            double cosYaw = Math.cos(yawRadians);
            double sinYaw = Math.sin(yawRadians);
            double cosPitch = Math.cos(pitchRadians);
            double sinPitch = Math.sin(pitchRadians);

            double rotatedX = cosYaw * x + sinYaw * z;
            double yawZ = -sinYaw * x + cosYaw * z;
            double rotatedY = cosPitch * y - sinPitch * yawZ;
            double rotatedZ = sinPitch * y + cosPitch * yawZ;
            return new ScreenPoint(
                    centerX + (float) rotatedX * scale,
                    centerY - (float) rotatedY * scale,
                    (float) rotatedZ);
        }

        @Override public boolean onTouchEvent(MotionEvent event) {
            getParent().requestDisallowInterceptTouchEvent(true);
            if (event.getPointerCount() >= 2) {
                float dx = event.getX(0) - event.getX(1);
                float dy = event.getY(0) - event.getY(1);
                float distance = (float) Math.sqrt(dx * dx + dy * dy);
                if (event.getActionMasked() == MotionEvent.ACTION_POINTER_DOWN
                        || event.getActionMasked() == MotionEvent.ACTION_DOWN) {
                    previousDistance = distance;
                } else if (event.getActionMasked() == MotionEvent.ACTION_MOVE
                        && previousDistance > 1f && distance > 1f) {
                    zoom = clamp(zoom * distance / previousDistance, 0.25f, 7f);
                    previousDistance = distance;
                    invalidate();
                }
                return true;
            }

            float x = event.getX();
            float y = event.getY();
            if (event.getActionMasked() == MotionEvent.ACTION_DOWN) {
                previousX = x;
                previousY = y;
                return true;
            }
            if (event.getActionMasked() == MotionEvent.ACTION_MOVE) {
                yaw += (x - previousX) * 0.35f;
                pitch = clamp(pitch + (y - previousY) * 0.35f, -89f, 89f);
                previousX = x;
                previousY = y;
                invalidate();
                return true;
            }
            previousX = x;
            previousY = y;
            return true;
        }

        void resetView() {
            yaw = -35f;
            pitch = 22f;
            zoom = 1f;
            pointRadius = 3.2f * getResources().getDisplayMetrics().density;
            information.setText(cloud.summary() + "\nVisor Canvas centrado automáticamente");
            invalidate();
        }

        void changePointSize(float deltaDp) {
            float density = getResources().getDisplayMetrics().density;
            pointRadius = clamp(pointRadius + deltaDp * density, 1.5f * density, 15f * density);
            information.setText(cloud.summary() + String.format(Locale.ROOT,
                    "\nDiámetro visual de punto %.1f px · Canvas 3D", pointRadius * 2f));
            invalidate();
        }

        private float[] normalize(List<PointCloudExportCore.Point> input) {
            if (input == null || input.isEmpty()) return new float[0];
            double minX = Double.POSITIVE_INFINITY;
            double minY = Double.POSITIVE_INFINITY;
            double minZ = Double.POSITIVE_INFINITY;
            double maxX = Double.NEGATIVE_INFINITY;
            double maxY = Double.NEGATIVE_INFINITY;
            double maxZ = Double.NEGATIVE_INFINITY;
            for (PointCloudExportCore.Point point : input) {
                minX = Math.min(minX, point.x);
                maxX = Math.max(maxX, point.x);
                minY = Math.min(minY, point.y);
                maxY = Math.max(maxY, point.y);
                minZ = Math.min(minZ, point.z);
                maxZ = Math.max(maxZ, point.z);
            }
            double centerX = (minX + maxX) * 0.5;
            double centerY = (minY + maxY) * 0.5;
            double centerZ = (minZ + maxZ) * 0.5;
            double span = Math.max(maxX - minX, Math.max(maxY - minY, maxZ - minZ));
            double scale = span > 1e-12 ? 2.45 / span : 1.0;
            float[] output = new float[input.size() * 3];
            for (int i = 0; i < input.size(); i++) {
                PointCloudExportCore.Point point = input.get(i);
                output[i * 3] = (float) ((point.x - centerX) * scale);
                output[i * 3 + 1] = (float) ((point.y - centerY) * scale);
                output[i * 3 + 2] = (float) ((point.z - centerZ) * scale);
            }
            return output;
        }
    }

    private static final class ScreenPoint {
        final float x;
        final float y;
        final float depth;

        ScreenPoint(float x, float y, float depth) {
            this.x = x;
            this.y = y;
            this.depth = depth;
        }
    }

    private static float clamp(float value, float min, float max) {
        return Math.max(min, Math.min(max, value));
    }

    private static final class CloudData {
        final String schema;
        final boolean solved;
        final boolean geometryReady;
        final boolean metricScale;
        final boolean globalReconstruction;
        final String state;
        final int leftFrame;
        final int rightFrame;
        final double reprojectionRmsPx;
        final double parallaxDegrees;
        final int rawPointCount;
        final int invalidPointCount;
        final List<PointCloudExportCore.Point> points;

        CloudData(String schema, boolean solved, boolean geometryReady,
                  boolean metricScale, boolean globalReconstruction, String state,
                  int leftFrame, int rightFrame, double reprojectionRmsPx,
                  double parallaxDegrees, int rawPointCount, int invalidPointCount,
                  List<PointCloudExportCore.Point> points) {
            this.schema = schema;
            this.solved = solved;
            this.geometryReady = geometryReady;
            this.metricScale = metricScale;
            this.globalReconstruction = globalReconstruction;
            this.state = state;
            this.leftFrame = leftFrame;
            this.rightFrame = rightFrame;
            this.reprojectionRmsPx = reprojectionRmsPx;
            this.parallaxDegrees = parallaxDegrees;
            this.rawPointCount = rawPointCount;
            this.invalidPointCount = invalidPointCount;
            this.points = points;
        }

        static CloudData read(File report) throws Exception {
            if (report == null || !report.isFile()) {
                throw new IllegalStateException("No existe imported_seed_geometry.json");
            }
            JSONObject json = new JSONObject(readText(report));
            JSONArray array = json.optJSONArray("points");
            int raw = array == null ? 0 : array.length();
            int invalid = 0;
            List<PointCloudExportCore.Point> points = new ArrayList<PointCloudExportCore.Point>();
            if (array != null) {
                for (int i = 0; i < array.length(); i++) {
                    JSONObject point = array.optJSONObject(i);
                    if (point == null) {
                        invalid++;
                        continue;
                    }
                    double x = point.optDouble("x", Double.NaN);
                    double y = point.optDouble("y", Double.NaN);
                    double z = point.optDouble("z", Double.NaN);
                    double error = point.optDouble("errorPx", -1.0);
                    double parallax = point.optDouble("parallaxDegrees", -1.0);
                    if (!Double.isFinite(x) || !Double.isFinite(y) || !Double.isFinite(z)) {
                        invalid++;
                        continue;
                    }
                    points.add(new PointCloudExportCore.Point(x, y, z, error, parallax));
                }
            }
            return new CloudData(
                    json.optString("schema", "unknown"),
                    json.optBoolean("solved", false),
                    json.optBoolean("geometryReady", false),
                    json.optBoolean("metricScale", false),
                    json.optBoolean("globalReconstruction", false),
                    json.optString("state", "UNKNOWN"),
                    json.optInt("leftFrame", -1),
                    json.optInt("rightFrame", -1),
                    json.optDouble("reprojectionRmsPx", Double.NaN),
                    json.optDouble("medianParallaxDegrees", Double.NaN),
                    raw, invalid, points);
        }

        String summary() {
            String classification = globalReconstruction
                    ? "NUBE GLOBAL" : "NUBE SEMILLA LOCAL";
            String scale = metricScale ? "ESCALA MÉTRICA" : "SIN ESCALA MÉTRICA";
            String quality = geometryReady ? "READY" : solved ? "REVIEW" : "BLOQUEADA";
            return classification + " · " + scale + " · " + quality
                    + "\nPuntos JSON " + rawPointCount + " · válidos visor " + points.size()
                    + (invalidPointCount > 0 ? " · descartados " + invalidPointCount : "")
                    + (leftFrame >= 0 && rightFrame >= 0
                    ? " · par " + leftFrame + "-" + rightFrame : "")
                    + (Double.isFinite(reprojectionRmsPx)
                    ? String.format(Locale.ROOT, " · RMS %.2f px", reprojectionRmsPx) : "")
                    + (Double.isFinite(parallaxDegrees)
                    ? String.format(Locale.ROOT, " · paralaje %.2f°", parallaxDegrees) : "");
        }
    }

    private static String readText(File file) throws Exception {
        try (BufferedInputStream input = new BufferedInputStream(new FileInputStream(file));
             ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            byte[] buffer = new byte[32 * 1024];
            int read;
            while ((read = input.read(buffer)) >= 0) output.write(buffer, 0, read);
            return output.toString(StandardCharsets.UTF_8.name());
        }
    }
}

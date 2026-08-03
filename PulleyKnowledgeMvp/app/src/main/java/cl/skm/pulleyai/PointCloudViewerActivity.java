package cl.skm.pulleyai;

import android.app.Activity;
import android.content.Intent;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Insets;
import android.graphics.Paint;
import android.graphics.Typeface;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.SystemClock;
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
 * Alpha58 diagnostic point-cloud viewer.
 *
 * The renderer remains fully independent from OpenGL/EGL. It projects points in Java,
 * draws them through Android Canvas and exposes enough live diagnostics to distinguish
 * an empty cloud, an off-screen projection and a Canvas that has not received onDraw.
 */
public final class PointCloudViewerActivity extends Activity {
    public static final String EXTRA_REPORT_PATH = "cl.skm.pulleyai.extra.SEED_REPORT_PATH";
    public static final String EXTRA_SUMMARY = "cl.skm.pulleyai.extra.SEED_SUMMARY";

    private static final int SAVE_PLY = 5801;
    private static final int SAVE_XYZ = 5802;
    private static final String STATE_YAW = "canvas.yaw";
    private static final String STATE_PITCH = "canvas.pitch";
    private static final String STATE_ZOOM = "canvas.zoom";
    private static final String STATE_POINT_RADIUS = "canvas.pointRadius";

    private CloudData cloud;
    private CloudCanvasView surface;
    private TextView information;
    private TextView diagnostics;

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
            buildUi(state);
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

    @Override protected void onResume() {
        super.onResume();
        if (surface != null) surface.postInvalidateOnAnimation();
    }

    @Override public void onWindowFocusChanged(boolean hasFocus) {
        super.onWindowFocusChanged(hasFocus);
        if (hasFocus && surface != null) surface.postInvalidateOnAnimation();
    }

    @Override protected void onSaveInstanceState(Bundle outState) {
        if (surface != null) surface.saveState(outState);
        super.onSaveInstanceState(outState);
    }

    private void buildUi(Bundle state) {
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(Color.BLACK);
        applySystemBarInsets(root);

        TextView title = text("RESULTADOS 3D · ALPHA58 LAB2", 20, true);
        title.setTextColor(Color.WHITE);
        root.addView(title);

        information = text(cloud.summary(), 13, false);
        information.setTextColor(Color.WHITE);
        information.setPadding(0, dp(3), 0, dp(4));
        root.addView(information);

        TextView legend = text(
                "VISOR CANVAS DIAGNÓSTICO · PUNTOS BLANCOS · X ROJO · Y VERDE · Z AZUL · ORIGEN AMARILLO",
                12, true);
        legend.setTextColor(Color.LTGRAY);
        legend.setGravity(Gravity.CENTER_HORIZONTAL);
        legend.setPadding(0, 0, 0, dp(3));
        root.addView(legend);

        surface = new CloudCanvasView(cloud);
        if (state != null) surface.restoreState(state);
        root.addView(surface, new LinearLayout.LayoutParams(-1, 0, 1f));

        diagnostics = text("DIAGNÓSTICO CANVAS: esperando primera llamada a onDraw…", 10, false);
        diagnostics.setTextColor(Color.WHITE);
        diagnostics.setTypeface(Typeface.MONOSPACE);
        diagnostics.setBackgroundColor(Color.rgb(28, 28, 28));
        diagnostics.setPadding(dp(8), dp(5), dp(8), dp(5));
        root.addView(diagnostics);

        if (cloud.points.isEmpty()) {
            TextView warning = text(
                    "SIN PUNTOS 3D VÁLIDOS: los ejes, la rejilla y el origen deben seguir visibles. "
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
        ply.setOnClickListener(view -> chooseExport(SAVE_PLY, "nube_semilla_alpha58.ply"));
        exports.addView(ply, weight());
        Button xyz = button("EXPORTAR XYZ");
        xyz.setEnabled(!cloud.points.isEmpty());
        xyz.setOnClickListener(view -> chooseExport(SAVE_XYZ, "nube_semilla_alpha58.xyz"));
        exports.addView(xyz, weight());
        Button close = button("VOLVER");
        close.setOnClickListener(view -> finish());
        exports.addView(close, weight());
        root.addView(exports);

        setContentView(root);
        root.requestApplyInsets();
        surface.postInvalidateOnAnimation();
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
                Insets bars = insets.getInsets(WindowInsets.Type.systemBars()
                        | WindowInsets.Type.displayCutout());
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
            if (surface != null) surface.postInvalidateOnAnimation();
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
        if (bold) view.setTypeface(Typeface.DEFAULT_BOLD);
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
        private final Bounds originalBounds;
        private final Bounds normalizedBounds;
        private final Paint gridPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint xPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint yPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint zPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint pointPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint originPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint labelPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint statusPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint warningPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint warningTextPaint = new Paint(Paint.ANTI_ALIAS_FLAG);

        private float yaw = -35f;
        private float pitch = 22f;
        private float zoom = 1f;
        private float pointRadius;
        private float previousX;
        private float previousY;
        private float previousDistance;
        private long drawCallCount;
        private long lastDiagnosticUpdateMs;
        private String lastDiagnosticText = "";

        CloudCanvasView(CloudData data) {
            super(PointCloudViewerActivity.this);
            setBackgroundColor(Color.BLACK);
            setFocusable(true);
            setSaveEnabled(true);
            normalizedPoints = normalize(data.points);
            originalBounds = Bounds.fromPoints(data.points);
            normalizedBounds = Bounds.fromArray(normalizedPoints);
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

            warningPaint.setColor(Color.rgb(120, 0, 0));
            warningPaint.setStyle(Paint.Style.FILL);
            warningTextPaint.setColor(Color.WHITE);
            warningTextPaint.setTextSize(12f * density);
            warningTextPaint.setFakeBoldText(true);
        }

        private void configureAxisPaint(Paint paint, int color, float width) {
            paint.setColor(color);
            paint.setStyle(Paint.Style.STROKE);
            paint.setStrokeWidth(width);
            paint.setStrokeCap(Paint.Cap.ROUND);
        }

        @Override protected void onAttachedToWindow() {
            super.onAttachedToWindow();
            postInvalidateOnAnimation();
        }

        @Override protected void onWindowVisibilityChanged(int visibility) {
            super.onWindowVisibilityChanged(visibility);
            if (visibility == VISIBLE) postInvalidateOnAnimation();
        }

        @Override protected void onSizeChanged(int width, int height, int oldWidth, int oldHeight) {
            super.onSizeChanged(width, height, oldWidth, oldHeight);
            postInvalidateOnAnimation();
        }

        @Override protected void onDraw(Canvas canvas) {
            long startedNs = System.nanoTime();
            super.onDraw(canvas);
            drawCallCount++;
            canvas.drawColor(Color.BLACK);
            int width = getWidth();
            int height = getHeight();
            if (width <= 1 || height <= 1) {
                publishDiagnostics(buildDiagnostics(width, height, 0, null, 0.0));
                return;
            }

            float centerX = width * 0.5f;
            float centerY = height * 0.5f;
            float scale = Math.min(width, height) * 0.31f * zoom;

            drawGrid(canvas, centerX, centerY, scale);
            ProjectionResult projection = drawCloud(canvas, centerX, centerY, scale, width, height);
            drawAxes(canvas, centerX, centerY, scale);

            if (normalizedPoints.length > 0 && projection.visiblePointCount == 0) {
                float warningHeight = dp(42);
                canvas.drawRect(0f, 0f, width, warningHeight, warningPaint);
                canvas.drawText("ALERTA: Canvas recibió puntos pero proyectó 0 dentro del área visible.",
                        dp(8), dp(18), warningTextPaint);
                canvas.drawText("Pulse CENTRAR y envíe captura del panel diagnóstico.",
                        dp(8), dp(35), warningTextPaint);
            }

            String status = "CANVAS 3D · " + (normalizedPoints.length / 3)
                    + " LEÍDOS · " + projection.visiblePointCount
                    + " VISIBLES · ARRASTRAR=GIRAR · PINZA=ZOOM";
            canvas.drawText(status, dp(8), height - dp(8), statusPaint);

            double renderMs = (System.nanoTime() - startedNs) / 1_000_000.0;
            publishDiagnostics(buildDiagnostics(width, height, projection.visiblePointCount,
                    projection.firstProjected, renderMs));
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

        private ProjectionResult drawCloud(Canvas canvas, float centerX, float centerY,
                                           float scale, int width, int height) {
            int visible = 0;
            ScreenPoint firstProjected = null;
            for (int i = 0; i < normalizedPoints.length; i += 3) {
                ScreenPoint point = project(normalizedPoints[i], normalizedPoints[i + 1],
                        normalizedPoints[i + 2], centerX, centerY, scale);
                if (firstProjected == null) firstProjected = point;
                if (!point.isFinite()) continue;
                boolean inside = point.x >= -pointRadius && point.x <= width + pointRadius
                        && point.y >= -pointRadius && point.y <= height + pointRadius;
                if (inside) visible++;
                canvas.drawCircle(point.x, point.y, pointRadius, pointPaint);
            }
            return new ProjectionResult(visible, firstProjected);
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
            if (getParent() != null) getParent().requestDisallowInterceptTouchEvent(true);
            int action = event.getActionMasked();

            if (action == MotionEvent.ACTION_CANCEL || action == MotionEvent.ACTION_UP) {
                previousDistance = 0f;
                return true;
            }

            if (action == MotionEvent.ACTION_POINTER_UP) {
                previousDistance = 0f;
                int remainingIndex = event.getActionIndex() == 0 ? 1 : 0;
                if (remainingIndex < event.getPointerCount()) {
                    previousX = event.getX(remainingIndex);
                    previousY = event.getY(remainingIndex);
                }
                return true;
            }

            if (event.getPointerCount() >= 2) {
                float dx = event.getX(0) - event.getX(1);
                float dy = event.getY(0) - event.getY(1);
                float distance = (float) Math.sqrt(dx * dx + dy * dy);
                if (action == MotionEvent.ACTION_POINTER_DOWN || previousDistance <= 1f) {
                    previousDistance = distance;
                } else if (action == MotionEvent.ACTION_MOVE && distance > 1f) {
                    zoom = clamp(zoom * distance / previousDistance, 0.25f, 7f);
                    previousDistance = distance;
                    postInvalidateOnAnimation();
                }
                return true;
            }

            float x = event.getX();
            float y = event.getY();
            if (action == MotionEvent.ACTION_DOWN) {
                previousX = x;
                previousY = y;
                return true;
            }
            if (action == MotionEvent.ACTION_MOVE) {
                yaw += (x - previousX) * 0.35f;
                pitch = clamp(pitch + (y - previousY) * 0.35f, -89f, 89f);
                previousX = x;
                previousY = y;
                postInvalidateOnAnimation();
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
            postInvalidateOnAnimation();
        }

        void changePointSize(float deltaDp) {
            float density = getResources().getDisplayMetrics().density;
            pointRadius = clamp(pointRadius + deltaDp * density, 1.5f * density, 15f * density);
            information.setText(cloud.summary() + String.format(Locale.ROOT,
                    "\nDiámetro visual de punto %.1f px · Canvas 3D", pointRadius * 2f));
            postInvalidateOnAnimation();
        }

        void saveState(Bundle outState) {
            outState.putFloat(STATE_YAW, yaw);
            outState.putFloat(STATE_PITCH, pitch);
            outState.putFloat(STATE_ZOOM, zoom);
            outState.putFloat(STATE_POINT_RADIUS, pointRadius);
        }

        void restoreState(Bundle state) {
            yaw = state.getFloat(STATE_YAW, yaw);
            pitch = clamp(state.getFloat(STATE_PITCH, pitch), -89f, 89f);
            zoom = clamp(state.getFloat(STATE_ZOOM, zoom), 0.25f, 7f);
            float density = getResources().getDisplayMetrics().density;
            pointRadius = clamp(state.getFloat(STATE_POINT_RADIUS, pointRadius),
                    1.5f * density, 15f * density);
        }

        private String buildDiagnostics(int width, int height, int visiblePointCount,
                                        ScreenPoint firstProjected, double renderMs) {
            String firstOriginal = cloud.points.isEmpty()
                    ? "sin datos" : formatPoint(cloud.points.get(0));
            String firstNormalized = normalizedPoints.length < 3
                    ? "sin datos" : formatPoint(normalizedPoints[0], normalizedPoints[1],
                    normalizedPoints[2]);
            String firstScreen = firstProjected == null
                    ? "sin datos" : String.format(Locale.ROOT, "(%.1f, %.1f, z=%.3f)",
                    firstProjected.x, firstProjected.y, firstProjected.depth);
            return String.format(Locale.ROOT,
                    "Canvas %d×%d · puntos leídos %d · dentro del área %d · onDraw %d · render %.2f ms\n"
                            + "BBox original %s\nBBox normalizado %s\n"
                            + "zoom %.3f · yaw %.1f° · pitch %.1f° · diámetro punto %.1f px\n"
                            + "P0 original %s · normalizado %s · proyectado %s",
                    width, height, normalizedPoints.length / 3, visiblePointCount,
                    drawCallCount, renderMs, originalBounds.format(), normalizedBounds.format(),
                    zoom, yaw, pitch, pointRadius * 2f,
                    firstOriginal, firstNormalized, firstScreen);
        }

        private void publishDiagnostics(String value) {
            long now = SystemClock.uptimeMillis();
            if (value.equals(lastDiagnosticText) || now - lastDiagnosticUpdateMs < 250L) return;
            lastDiagnosticText = value;
            lastDiagnosticUpdateMs = now;
            if (diagnostics != null) diagnostics.setText(value);
        }

        private float[] normalize(List<PointCloudExportCore.Point> input) {
            if (input == null || input.isEmpty()) return new float[0];
            Bounds bounds = Bounds.fromPoints(input);
            double centerX = (bounds.minX + bounds.maxX) * 0.5;
            double centerY = (bounds.minY + bounds.maxY) * 0.5;
            double centerZ = (bounds.minZ + bounds.maxZ) * 0.5;
            double span = Math.max(bounds.maxX - bounds.minX,
                    Math.max(bounds.maxY - bounds.minY, bounds.maxZ - bounds.minZ));
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

    private static String formatPoint(PointCloudExportCore.Point point) {
        return formatPoint(point.x, point.y, point.z);
    }

    private static String formatPoint(double x, double y, double z) {
        return String.format(Locale.ROOT, "(%.5f, %.5f, %.5f)", x, y, z);
    }

    private static final class ProjectionResult {
        final int visiblePointCount;
        final ScreenPoint firstProjected;

        ProjectionResult(int visiblePointCount, ScreenPoint firstProjected) {
            this.visiblePointCount = visiblePointCount;
            this.firstProjected = firstProjected;
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

        boolean isFinite() {
            return !Float.isNaN(x) && !Float.isInfinite(x)
                    && !Float.isNaN(y) && !Float.isInfinite(y)
                    && !Float.isNaN(depth) && !Float.isInfinite(depth);
        }
    }

    private static final class Bounds {
        final boolean valid;
        final double minX;
        final double minY;
        final double minZ;
        final double maxX;
        final double maxY;
        final double maxZ;

        Bounds(boolean valid, double minX, double minY, double minZ,
               double maxX, double maxY, double maxZ) {
            this.valid = valid;
            this.minX = minX;
            this.minY = minY;
            this.minZ = minZ;
            this.maxX = maxX;
            this.maxY = maxY;
            this.maxZ = maxZ;
        }

        static Bounds fromPoints(List<PointCloudExportCore.Point> points) {
            if (points == null || points.isEmpty()) return empty();
            double minX = Double.POSITIVE_INFINITY;
            double minY = Double.POSITIVE_INFINITY;
            double minZ = Double.POSITIVE_INFINITY;
            double maxX = Double.NEGATIVE_INFINITY;
            double maxY = Double.NEGATIVE_INFINITY;
            double maxZ = Double.NEGATIVE_INFINITY;
            for (PointCloudExportCore.Point point : points) {
                minX = Math.min(minX, point.x);
                minY = Math.min(minY, point.y);
                minZ = Math.min(minZ, point.z);
                maxX = Math.max(maxX, point.x);
                maxY = Math.max(maxY, point.y);
                maxZ = Math.max(maxZ, point.z);
            }
            return new Bounds(true, minX, minY, minZ, maxX, maxY, maxZ);
        }

        static Bounds fromArray(float[] values) {
            if (values == null || values.length < 3) return empty();
            double minX = Double.POSITIVE_INFINITY;
            double minY = Double.POSITIVE_INFINITY;
            double minZ = Double.POSITIVE_INFINITY;
            double maxX = Double.NEGATIVE_INFINITY;
            double maxY = Double.NEGATIVE_INFINITY;
            double maxZ = Double.NEGATIVE_INFINITY;
            for (int i = 0; i < values.length; i += 3) {
                minX = Math.min(minX, values[i]);
                minY = Math.min(minY, values[i + 1]);
                minZ = Math.min(minZ, values[i + 2]);
                maxX = Math.max(maxX, values[i]);
                maxY = Math.max(maxY, values[i + 1]);
                maxZ = Math.max(maxZ, values[i + 2]);
            }
            return new Bounds(true, minX, minY, minZ, maxX, maxY, maxZ);
        }

        static Bounds empty() {
            return new Bounds(false, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0);
        }

        String format() {
            if (!valid) return "sin datos";
            return String.format(Locale.ROOT,
                    "X[%.5f, %.5f] Y[%.5f, %.5f] Z[%.5f, %.5f]",
                    minX, maxX, minY, maxY, minZ, maxZ);
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

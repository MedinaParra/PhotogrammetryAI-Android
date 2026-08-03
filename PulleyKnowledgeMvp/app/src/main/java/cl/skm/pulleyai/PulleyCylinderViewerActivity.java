package cl.skm.pulleyai;

import android.app.Activity;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Insets;
import android.graphics.Paint;
import android.os.Build;
import android.os.Bundle;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.WindowInsets;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/** Prior-constrained pulley cylinder inspection view; it never promotes the model to metrology. */
public final class PulleyCylinderViewerActivity extends Activity {
    public static final String EXTRA_REPORT_PATH = "cl.skm.pulleyai.extra.CYLINDER_SOURCE_REPORT";
    public static final String EXTRA_SHELL_LENGTH_MM = "cl.skm.pulleyai.extra.SHELL_LENGTH_MM";
    public static final String EXTRA_SHELL_DIAMETER_MM = "cl.skm.pulleyai.extra.SHELL_DIAMETER_MM";

    private static final String STATE_YAW = "cylinder_yaw";
    private static final String STATE_PITCH = "cylinder_pitch";
    private static final String STATE_ZOOM = "cylinder_zoom";
    private static final String STATE_POINT_RADIUS = "cylinder_point_radius";
    private static final String STATE_MODE = "cylinder_mode";

    private PulleyCylinderPriorCore.Result fit;
    private CylinderCanvasView canvasView;
    private TextView summaryView;

    @Override protected void onCreate(Bundle state) {
        super.onCreate(state);
        getWindow().setStatusBarColor(Color.BLACK);
        getWindow().setNavigationBarColor(Color.BLACK);
        try {
            String path = getIntent().getStringExtra(EXTRA_REPORT_PATH);
            double lengthMm = getIntent().getDoubleExtra(EXTRA_SHELL_LENGTH_MM, Double.NaN);
            double diameterMm = getIntent().getDoubleExtra(EXTRA_SHELL_DIAMETER_MM, Double.NaN);
            if (path == null || path.trim().isEmpty()) {
                throw new IllegalStateException("No se recibió la nube semilla");
            }
            List<PointCloudExportCore.Point> raw = readPoints(new File(path));
            fit = PulleyCylinderPriorCore.fit(raw, lengthMm, diameterMm);
            buildUi(state);
        } catch (Exception error) {
            String message = error.getMessage() == null
                    ? error.getClass().getSimpleName() : error.getMessage();
            TextView failure = new TextView(this);
            failure.setPadding(dp(24), dp(24), dp(24), dp(24));
            failure.setTextSize(18);
            failure.setTextColor(Color.WHITE);
            failure.setBackgroundColor(Color.BLACK);
            failure.setText("NO SE PUDO AJUSTAR EL CILINDRO\n\n" + message);
            setContentView(failure);
        }
    }

    @Override protected void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);
        if (canvasView != null) canvasView.saveState(outState);
    }

    @Override protected void onResume() {
        super.onResume();
        if (canvasView != null) canvasView.postInvalidateOnAnimation();
    }

    @Override public void onWindowFocusChanged(boolean hasFocus) {
        super.onWindowFocusChanged(hasFocus);
        if (hasFocus && canvasView != null) canvasView.postInvalidateOnAnimation();
    }

    private void buildUi(Bundle state) {
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(Color.BLACK);
        applySystemBarInsets(root);

        TextView title = text("AJUSTE CILÍNDRICO · ALPHA59 LAB2", 20, true);
        title.setTextColor(Color.WHITE);
        root.addView(title);

        summaryView = text(fit.summary(), 12, false);
        summaryView.setTextColor(fit.fitAccepted ? Color.rgb(155, 235, 180)
                : fit.solved ? Color.rgb(255, 205, 120) : Color.rgb(255, 145, 145));
        summaryView.setPadding(0, dp(3), 0, dp(3));
        root.addView(summaryView);

        TextView legend = text(
                "CILINDRO CELESTE=PRIOR · BLANCO=INLIER MEDIDO · NARANJA=OUTLIER · EJE X=ROJO",
                11, true);
        legend.setTextColor(Color.LTGRAY);
        legend.setGravity(Gravity.CENTER_HORIZONTAL);
        root.addView(legend);

        canvasView = new CylinderCanvasView(fit, state);
        root.addView(canvasView, new LinearLayout.LayoutParams(-1, 0, 1f));

        if (!fit.fitAccepted) {
            TextView warning = text(
                    "RESULTADO EXPERIMENTAL: el cilindro usa largo y diámetro conocidos como restricción. "
                            + "Los puntos no deben interpretarse como medición validada mientras el ajuste no sea aceptado.",
                    12, true);
            warning.setTextColor(Color.rgb(255, 175, 120));
            warning.setGravity(Gravity.CENTER_HORIZONTAL);
            warning.setPadding(0, dp(2), 0, dp(2));
            root.addView(warning);
        }

        LinearLayout controls = row();
        Button reset = button("CENTRAR");
        reset.setOnClickListener(view -> canvasView.resetView());
        controls.addView(reset, weight());
        Button mode = button("MODELO + MEDIDOS");
        mode.setOnClickListener(view -> {
            canvasView.nextMode();
            mode.setText(canvasView.modeLabel());
        });
        controls.addView(mode, weight());
        Button point = button("PUNTO +");
        point.setOnClickListener(view -> canvasView.changePointSize());
        controls.addView(point, weight());
        root.addView(controls);

        LinearLayout closeRow = row();
        Button close = button("VOLVER");
        close.setOnClickListener(view -> finish());
        closeRow.addView(close, weight());
        root.addView(closeRow);

        setContentView(root);
        root.requestApplyInsets();
    }

    private void applySystemBarInsets(LinearLayout root) {
        final int horizontal = dp(10);
        final int vertical = dp(7);
        root.setPadding(horizontal, vertical, horizontal, vertical);
        root.setOnApplyWindowInsetsListener((view, insets) -> {
            int left, top, right, bottom;
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                Insets bars = insets.getInsets(WindowInsets.Type.systemBars()
                        | WindowInsets.Type.displayCutout());
                left = bars.left; top = bars.top; right = bars.right; bottom = bars.bottom;
            } else {
                left = insets.getSystemWindowInsetLeft();
                top = insets.getSystemWindowInsetTop();
                right = insets.getSystemWindowInsetRight();
                bottom = insets.getSystemWindowInsetBottom();
            }
            view.setPadding(horizontal + left, vertical + top,
                    horizontal + right, vertical + bottom);
            return insets;
        });
    }

    private List<PointCloudExportCore.Point> readPoints(File report) throws Exception {
        if (report == null || !report.isFile()) {
            throw new IllegalStateException("No existe imported_seed_geometry.json");
        }
        JSONObject json = new JSONObject(readText(report));
        JSONArray points = json.optJSONArray("points");
        List<PointCloudExportCore.Point> output = new ArrayList<PointCloudExportCore.Point>();
        if (points == null) return output;
        for (int i = 0; i < points.length(); i++) {
            JSONObject point = points.optJSONObject(i);
            if (point == null) continue;
            double x = point.optDouble("x", Double.NaN);
            double y = point.optDouble("y", Double.NaN);
            double z = point.optDouble("z", Double.NaN);
            if (!Double.isFinite(x) || !Double.isFinite(y) || !Double.isFinite(z)) continue;
            output.add(new PointCloudExportCore.Point(x, y, z,
                    point.optDouble("errorPx", -1.0),
                    point.optDouble("parallaxDegrees", -1.0)));
        }
        return output;
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

    private final class CylinderCanvasView extends View {
        private final PulleyCylinderPriorCore.Result result;
        private final Paint gridPaint = paint(Color.rgb(58, 58, 58), Paint.Style.STROKE, 1.0f);
        private final Paint modelPaint = paint(Color.rgb(55, 210, 235), Paint.Style.STROKE, 1.6f);
        private final Paint inlierPaint = paint(Color.WHITE, Paint.Style.FILL, 1.0f);
        private final Paint outlierPaint = paint(Color.rgb(255, 145, 55), Paint.Style.FILL, 1.0f);
        private final Paint xPaint = paint(Color.rgb(255, 65, 65), Paint.Style.STROKE, 2.1f);
        private final Paint yPaint = paint(Color.rgb(65, 245, 100), Paint.Style.STROKE, 2.1f);
        private final Paint zPaint = paint(Color.rgb(75, 145, 255), Paint.Style.STROKE, 2.1f);
        private final Paint originPaint = paint(Color.rgb(255, 215, 40), Paint.Style.FILL, 1.0f);
        private final Paint statusPaint = paint(Color.LTGRAY, Paint.Style.FILL, 1.0f);
        private float yaw = -25f;
        private float pitch = 18f;
        private float zoom = 1f;
        private float pointRadius;
        private float previousX;
        private float previousY;
        private float previousDistance;
        private boolean pinching;
        private int mode;
        private long drawCalls;

        CylinderCanvasView(PulleyCylinderPriorCore.Result result, Bundle state) {
            super(PulleyCylinderViewerActivity.this);
            this.result = result;
            setBackgroundColor(Color.BLACK);
            pointRadius = 2.8f * getResources().getDisplayMetrics().density;
            statusPaint.setTextSize(10f * getResources().getDisplayMetrics().density);
            if (state != null) {
                yaw = state.getFloat(STATE_YAW, yaw);
                pitch = state.getFloat(STATE_PITCH, pitch);
                zoom = state.getFloat(STATE_ZOOM, zoom);
                pointRadius = state.getFloat(STATE_POINT_RADIUS, pointRadius);
                mode = state.getInt(STATE_MODE, 0);
            }
        }

        private Paint paint(int color, Paint.Style style, float widthDp) {
            Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
            paint.setColor(color);
            paint.setStyle(style);
            paint.setStrokeWidth(widthDp * getResources().getDisplayMetrics().density);
            paint.setStrokeCap(Paint.Cap.ROUND);
            return paint;
        }

        void saveState(Bundle state) {
            state.putFloat(STATE_YAW, yaw);
            state.putFloat(STATE_PITCH, pitch);
            state.putFloat(STATE_ZOOM, zoom);
            state.putFloat(STATE_POINT_RADIUS, pointRadius);
            state.putInt(STATE_MODE, mode);
        }

        @Override protected void onDraw(Canvas canvas) {
            long started = System.nanoTime();
            drawCalls++;
            canvas.drawColor(Color.BLACK);
            int width = getWidth();
            int height = getHeight();
            if (width <= 1 || height <= 1) return;
            float centerX = width * 0.5f;
            float centerY = height * 0.5f;
            double extent = Math.max(result.shellLengthMm, result.shellDiameterMm);
            float scale = (float) (Math.min(width, height) * 0.74 / Math.max(1.0, extent) * zoom);
            drawGrid(canvas, centerX, centerY, scale, extent);
            if (mode != 1) drawCylinder(canvas, centerX, centerY, scale);
            if (mode != 2) drawMeasured(canvas, centerX, centerY, scale);
            drawAxes(canvas, centerX, centerY, scale, extent);
            long micros = (System.nanoTime() - started) / 1000L;
            String status = String.format(Locale.ROOT,
                    "%s · %d/%d INLIERS · ZOOM %.2f · YAW %.1f° · PITCH %.1f° · onDraw %d · %d µs",
                    result.status, result.inlierCount, result.usedPointCount,
                    zoom, yaw, pitch, drawCalls, micros);
            canvas.drawText(status, dp(7), height - dp(7), statusPaint);
        }

        private void drawGrid(Canvas canvas, float cx, float cy, float scale, double extent) {
            double limit = extent * 0.65;
            double step = niceStep(extent / 8.0);
            for (double value = -limit; value <= limit + 0.5 * step; value += step) {
                line3d(canvas, -limit, 0, value, limit, 0, value, cx, cy, scale, gridPaint);
                line3d(canvas, value, 0, -limit, value, 0, limit, cx, cy, scale, gridPaint);
            }
        }

        private void drawCylinder(Canvas canvas, float cx, float cy, float scale) {
            double half = result.shellLengthMm * 0.5;
            double radius = result.shellDiameterMm * 0.5;
            int segments = 36;
            int stations = 8;
            for (int station = 0; station <= stations; station++) {
                double x = -half + result.shellLengthMm * station / stations;
                ScreenPoint previous = null;
                for (int segment = 0; segment <= segments; segment++) {
                    double angle = 2.0 * Math.PI * segment / segments;
                    ScreenPoint point = project(x, radius * Math.cos(angle),
                            radius * Math.sin(angle), cx, cy, scale);
                    if (previous != null) canvas.drawLine(previous.x, previous.y, point.x, point.y, modelPaint);
                    previous = point;
                }
            }
            for (int segment = 0; segment < 12; segment++) {
                double angle = 2.0 * Math.PI * segment / 12.0;
                line3d(canvas, -half, radius * Math.cos(angle), radius * Math.sin(angle),
                        half, radius * Math.cos(angle), radius * Math.sin(angle),
                        cx, cy, scale, modelPaint);
            }
        }

        private void drawMeasured(Canvas canvas, float cx, float cy, float scale) {
            for (PulleyCylinderPriorCore.AlignedPoint point : result.points) {
                ScreenPoint screen = project(point.xMm, point.yMm, point.zMm, cx, cy, scale);
                canvas.drawCircle(screen.x, screen.y, pointRadius,
                        point.inlier ? inlierPaint : outlierPaint);
            }
        }

        private void drawAxes(Canvas canvas, float cx, float cy, float scale, double extent) {
            double axis = extent * 0.7;
            line3d(canvas, -axis, 0, 0, axis, 0, 0, cx, cy, scale, xPaint);
            line3d(canvas, 0, -axis, 0, 0, axis, 0, cx, cy, scale, yPaint);
            line3d(canvas, 0, 0, -axis, 0, 0, axis, cx, cy, scale, zPaint);
            ScreenPoint origin = project(0, 0, 0, cx, cy, scale);
            canvas.drawCircle(origin.x, origin.y, Math.max(dp(4), pointRadius), originPaint);
        }

        private void line3d(Canvas canvas, double x1, double y1, double z1,
                            double x2, double y2, double z2,
                            float cx, float cy, float scale, Paint paint) {
            ScreenPoint a = project(x1, y1, z1, cx, cy, scale);
            ScreenPoint b = project(x2, y2, z2, cx, cy, scale);
            canvas.drawLine(a.x, a.y, b.x, b.y, paint);
        }

        private ScreenPoint project(double x, double y, double z, float cx, float cy, float scale) {
            double yawRadians = Math.toRadians(yaw);
            double pitchRadians = Math.toRadians(pitch);
            double rotatedX = Math.cos(yawRadians) * x + Math.sin(yawRadians) * z;
            double yawZ = -Math.sin(yawRadians) * x + Math.cos(yawRadians) * z;
            double rotatedY = Math.cos(pitchRadians) * y - Math.sin(pitchRadians) * yawZ;
            double depth = Math.sin(pitchRadians) * y + Math.cos(pitchRadians) * yawZ;
            return new ScreenPoint(cx + (float) rotatedX * scale,
                    cy - (float) rotatedY * scale, (float) depth);
        }

        @Override public boolean onTouchEvent(MotionEvent event) {
            getParent().requestDisallowInterceptTouchEvent(true);
            int action = event.getActionMasked();
            if (event.getPointerCount() >= 2) {
                float distance = distance(event);
                if (action == MotionEvent.ACTION_POINTER_DOWN || !pinching) {
                    previousDistance = distance;
                    pinching = true;
                } else if (action == MotionEvent.ACTION_MOVE && previousDistance > 1f && distance > 1f) {
                    zoom = clamp(zoom * distance / previousDistance, 0.25f, 8f);
                    previousDistance = distance;
                    postInvalidateOnAnimation();
                }
                return true;
            }
            if (action == MotionEvent.ACTION_POINTER_UP) {
                pinching = false;
                int remaining = event.getActionIndex() == 0 ? 1 : 0;
                if (remaining < event.getPointerCount()) {
                    previousX = event.getX(remaining);
                    previousY = event.getY(remaining);
                }
                return true;
            }
            float x = event.getX();
            float y = event.getY();
            if (action == MotionEvent.ACTION_DOWN || pinching) {
                pinching = false;
                previousX = x;
                previousY = y;
                return true;
            }
            if (action == MotionEvent.ACTION_MOVE) {
                yaw += (x - previousX) * 0.32f;
                pitch = clamp(pitch + (y - previousY) * 0.32f, -89f, 89f);
                previousX = x;
                previousY = y;
                postInvalidateOnAnimation();
                return true;
            }
            previousX = x;
            previousY = y;
            return true;
        }

        private float distance(MotionEvent event) {
            float dx = event.getX(0) - event.getX(1);
            float dy = event.getY(0) - event.getY(1);
            return (float) Math.sqrt(dx * dx + dy * dy);
        }

        void resetView() {
            yaw = -25f;
            pitch = 18f;
            zoom = 1f;
            postInvalidateOnAnimation();
        }

        void nextMode() {
            mode = (mode + 1) % 3;
            postInvalidateOnAnimation();
        }

        String modeLabel() {
            return mode == 0 ? "MODELO + MEDIDOS" : mode == 1 ? "SOLO MEDIDOS" : "SOLO MODELO";
        }

        void changePointSize() {
            float density = getResources().getDisplayMetrics().density;
            pointRadius += 1.4f * density;
            if (pointRadius > 9.0f * density) pointRadius = 1.8f * density;
            postInvalidateOnAnimation();
        }

        private double niceStep(double value) {
            if (!(value > 0.0)) return 1.0;
            double power = Math.pow(10.0, Math.floor(Math.log10(value)));
            double normalized = value / power;
            return (normalized < 2.0 ? 1.0 : normalized < 5.0 ? 2.0 : 5.0) * power;
        }
    }

    private static final class ScreenPoint {
        final float x, y, depth;
        ScreenPoint(float x, float y, float depth) { this.x = x; this.y = y; this.depth = depth; }
    }

    private static float clamp(float value, float min, float max) {
        return Math.max(min, Math.min(max, value));
    }
}

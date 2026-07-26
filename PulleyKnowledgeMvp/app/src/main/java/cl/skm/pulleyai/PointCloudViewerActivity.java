package cl.skm.pulleyai;

import android.app.Activity;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.PixelFormat;
import android.net.Uri;
import android.opengl.GLES20;
import android.opengl.GLSurfaceView;
import android.opengl.Matrix;
import android.os.Bundle;
import android.view.Gravity;
import android.view.MotionEvent;
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
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.FloatBuffer;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import javax.microedition.khronos.egl.EGLConfig;
import javax.microedition.khronos.opengles.GL10;

/**
 * High-contrast OpenGL ES viewer for bounded, non-metric seed point clouds.
 * Alpha56 always renders a black environment, white points, a reference grid
 * and conventional X/Y/Z axes so an empty cloud cannot look like a renderer failure.
 */
public final class PointCloudViewerActivity extends Activity {
    public static final String EXTRA_REPORT_PATH = "cl.skm.pulleyai.extra.SEED_REPORT_PATH";
    public static final String EXTRA_SUMMARY = "cl.skm.pulleyai.extra.SEED_SUMMARY";
    private static final int SAVE_PLY = 5801;
    private static final int SAVE_XYZ = 5802;

    private CloudData cloud;
    private CloudSurfaceView surface;
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
        root.setPadding(dp(10), dp(8), dp(10), dp(8));

        TextView title = text("RESULTADOS 3D · ALPHA56 LAB2", 21, true);
        title.setTextColor(Color.WHITE);
        root.addView(title);

        information = text(cloud.summary(), 13, false);
        information.setTextColor(Color.WHITE);
        information.setPadding(0, dp(3), 0, dp(5));
        root.addView(information);

        TextView legend = text(
                "PUNTOS BLANCOS  ·  X ROJO  ·  Y VERDE  ·  Z AZUL  ·  REJILLA GRIS  ·  ORIGEN 0,0,0",
                12, true);
        legend.setTextColor(Color.LTGRAY);
        legend.setGravity(Gravity.CENTER_HORIZONTAL);
        legend.setPadding(0, 0, 0, dp(4));
        root.addView(legend);

        surface = new CloudSurfaceView(cloud);
        root.addView(surface, new LinearLayout.LayoutParams(-1, 0, 1f));

        if (cloud.points.isEmpty()) {
            TextView warning = text(
                    "SIN PUNTOS 3D VÁLIDOS: se muestran los ejes para confirmar que el visor funciona. "
                            + "El archivo contenía " + cloud.rawPointCount + " registros y se descartaron "
                            + cloud.invalidPointCount + " coordenadas NaN/∞ o inválidas.",
                    13, true);
            warning.setTextColor(Color.rgb(255, 190, 150));
            warning.setGravity(Gravity.CENTER_HORIZONTAL);
            warning.setPadding(0, dp(4), 0, dp(2));
            root.addView(warning);
        }

        LinearLayout controls = row();
        Button reset = button("CENTRAR");
        reset.setOnClickListener(view -> surface.resetView());
        controls.addView(reset, weight());
        Button smaller = button("PUNTO −");
        smaller.setOnClickListener(view -> surface.changePointSize(-2f));
        controls.addView(smaller, weight());
        Button larger = button("PUNTO +");
        larger.setOnClickListener(view -> surface.changePointSize(2f));
        controls.addView(larger, weight());
        root.addView(controls);

        LinearLayout exports = row();
        Button ply = button("EXPORTAR PLY");
        ply.setEnabled(!cloud.points.isEmpty());
        ply.setOnClickListener(view -> chooseExport(SAVE_PLY, "nube_semilla_alpha56.ply"));
        exports.addView(ply, weight());
        Button xyz = button("EXPORTAR XYZ");
        xyz.setEnabled(!cloud.points.isEmpty());
        xyz.setOnClickListener(view -> chooseExport(SAVE_XYZ, "nube_semilla_alpha56.xyz"));
        exports.addView(xyz, weight());
        Button close = button("VOLVER");
        close.setOnClickListener(view -> finish());
        exports.addView(close, weight());
        root.addView(exports);

        setContentView(root);
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

    @Override protected void onResume() {
        super.onResume();
        if (surface != null) surface.onResume();
    }

    @Override protected void onPause() {
        if (surface != null) surface.onPause();
        super.onPause();
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

    private final class CloudSurfaceView extends GLSurfaceView {
        private final CloudRenderer renderer;
        private float previousX;
        private float previousY;
        private float previousDistance;

        CloudSurfaceView(CloudData data) {
            super(PointCloudViewerActivity.this);
            getHolder().setFormat(PixelFormat.OPAQUE);
            setEGLContextClientVersion(2);
            setEGLConfigChooser(8, 8, 8, 8, 16, 0);
            setPreserveEGLContextOnPause(true);
            setBackgroundColor(Color.BLACK);
            renderer = new CloudRenderer(data);
            setRenderer(renderer);
            // Continuous rendering avoids device-specific first-frame loss after resume/rotation.
            setRenderMode(GLSurfaceView.RENDERMODE_CONTINUOUSLY);
        }

        @Override public boolean onTouchEvent(MotionEvent event) {
            if (event.getPointerCount() >= 2) {
                float dx = event.getX(0) - event.getX(1);
                float dy = event.getY(0) - event.getY(1);
                float distance = (float) Math.sqrt(dx * dx + dy * dy);
                if (event.getActionMasked() == MotionEvent.ACTION_POINTER_DOWN
                        || event.getActionMasked() == MotionEvent.ACTION_DOWN) {
                    previousDistance = distance;
                } else if (event.getActionMasked() == MotionEvent.ACTION_MOVE
                        && previousDistance > 1f && distance > 1f) {
                    renderer.zoom(distance / previousDistance);
                    previousDistance = distance;
                }
                return true;
            }
            float x = event.getX();
            float y = event.getY();
            if (event.getActionMasked() == MotionEvent.ACTION_MOVE) {
                renderer.rotate((x - previousX) * 0.45f, (y - previousY) * 0.45f);
            }
            previousX = x;
            previousY = y;
            return true;
        }

        void resetView() {
            renderer.reset();
            information.setText(cloud.summary() + "\nVista centrada automáticamente");
        }

        void changePointSize(float delta) {
            renderer.changePointSize(delta);
            information.setText(cloud.summary() + String.format(Locale.ROOT,
                    "\nTamaño visual de punto %.1f px", renderer.pointSize()));
        }
    }

    private static final class CloudRenderer implements GLSurfaceView.Renderer {
        private static final float AXIS_LENGTH = 1.65f;
        private static final String VERTEX_SHADER =
                "uniform mat4 u_Mvp;\n"
                        + "uniform float u_PointSize;\n"
                        + "attribute vec3 a_Position;\n"
                        + "void main(){ gl_Position=u_Mvp*vec4(a_Position,1.0);"
                        + " gl_PointSize=u_PointSize; }";
        private static final String POINT_FRAGMENT_SHADER =
                "precision mediump float;\n"
                        + "uniform vec4 u_Color;\n"
                        + "void main(){ vec2 p=gl_PointCoord-vec2(0.5);"
                        + " if(dot(p,p)>0.25) discard; gl_FragColor=u_Color; }";
        private static final String LINE_FRAGMENT_SHADER =
                "precision mediump float;\n"
                        + "uniform vec4 u_Color;\n"
                        + "void main(){ gl_FragColor=u_Color; }";

        private final FloatBuffer points;
        private final FloatBuffer grid;
        private final FloatBuffer xAxis;
        private final FloatBuffer yAxis;
        private final FloatBuffer zAxis;
        private final FloatBuffer origin;
        private final int pointCount;
        private final int gridVertexCount;
        private final float[] projection = new float[16];
        private final float[] model = new float[16];
        private final float[] mvp = new float[16];
        private int pointProgram;
        private int lineProgram;
        private float yaw = -35f;
        private float pitch = 22f;
        private float zoom = 1f;
        private float pointSize = 10f;
        private float aspect = 1f;

        CloudRenderer(CloudData data) {
            float[] normalized = normalize(data.points);
            pointCount = normalized.length / 3;
            points = direct(normalized);
            float[] gridValues = createGrid();
            gridVertexCount = gridValues.length / 3;
            grid = direct(gridValues);
            xAxis = direct(new float[]{-AXIS_LENGTH, 0, 0, AXIS_LENGTH, 0, 0});
            yAxis = direct(new float[]{0, -AXIS_LENGTH, 0, 0, AXIS_LENGTH, 0});
            zAxis = direct(new float[]{0, 0, -AXIS_LENGTH, 0, 0, AXIS_LENGTH});
            origin = direct(new float[]{0, 0, 0});
        }

        @Override public void onSurfaceCreated(GL10 gl, EGLConfig config) {
            GLES20.glClearColor(0f, 0f, 0f, 1f);
            GLES20.glDisable(GLES20.GL_CULL_FACE);
            GLES20.glEnable(GLES20.GL_DEPTH_TEST);
            GLES20.glDepthFunc(GLES20.GL_LEQUAL);
            pointProgram = link(VERTEX_SHADER, POINT_FRAGMENT_SHADER);
            lineProgram = link(VERTEX_SHADER, LINE_FRAGMENT_SHADER);
        }

        @Override public void onSurfaceChanged(GL10 gl, int width, int height) {
            int safeWidth = Math.max(1, width);
            int safeHeight = Math.max(1, height);
            GLES20.glViewport(0, 0, safeWidth, safeHeight);
            aspect = (float) safeWidth / (float) safeHeight;
        }

        @Override public void onDrawFrame(GL10 gl) {
            GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT | GLES20.GL_DEPTH_BUFFER_BIT);
            updateMvp();

            drawLines(grid, gridVertexCount, 0.20f, 0.20f, 0.20f, 1f, 1f);
            drawLines(xAxis, 2, 1f, 0.15f, 0.15f, 1f, 3f);
            drawLines(yAxis, 2, 0.15f, 1f, 0.25f, 1f, 3f);
            drawLines(zAxis, 2, 0.15f, 0.45f, 1f, 1f, 3f);

            // Draw the origin and cloud without depth rejection so they remain visible.
            GLES20.glDisable(GLES20.GL_DEPTH_TEST);
            drawPoints(origin, 1, 13f, 1f, 0.85f, 0.15f, 1f);
            if (pointCount > 0) {
                drawPoints(points, pointCount, pointSize, 1f, 1f, 1f, 1f);
            }
            GLES20.glEnable(GLES20.GL_DEPTH_TEST);
        }

        private void updateMvp() {
            float extent = 2.05f / Math.max(0.20f, zoom);
            float horizontal = aspect >= 1f ? extent * aspect : extent;
            float vertical = aspect >= 1f ? extent : extent / Math.max(0.20f, aspect);
            Matrix.orthoM(projection, 0,
                    -horizontal, horizontal, -vertical, vertical, -8f, 8f);
            Matrix.setIdentityM(model, 0);
            Matrix.rotateM(model, 0, pitch, 1f, 0f, 0f);
            Matrix.rotateM(model, 0, yaw, 0f, 1f, 0f);
            Matrix.multiplyMM(mvp, 0, projection, 0, model, 0);
        }

        private void drawLines(FloatBuffer buffer, int vertices,
                               float r, float g, float b, float a, float width) {
            if (lineProgram == 0 || vertices <= 0) return;
            GLES20.glUseProgram(lineProgram);
            int position = GLES20.glGetAttribLocation(lineProgram, "a_Position");
            int matrix = GLES20.glGetUniformLocation(lineProgram, "u_Mvp");
            int size = GLES20.glGetUniformLocation(lineProgram, "u_PointSize");
            int color = GLES20.glGetUniformLocation(lineProgram, "u_Color");
            GLES20.glUniformMatrix4fv(matrix, 1, false, mvp, 0);
            GLES20.glUniform1f(size, 1f);
            GLES20.glUniform4f(color, r, g, b, a);
            GLES20.glLineWidth(width);
            buffer.position(0);
            GLES20.glEnableVertexAttribArray(position);
            GLES20.glVertexAttribPointer(position, 3, GLES20.GL_FLOAT, false, 0, buffer);
            GLES20.glDrawArrays(GLES20.GL_LINES, 0, vertices);
            GLES20.glDisableVertexAttribArray(position);
        }

        private void drawPoints(FloatBuffer buffer, int vertices, float sizePx,
                                float r, float g, float b, float a) {
            if (pointProgram == 0 || vertices <= 0) return;
            GLES20.glUseProgram(pointProgram);
            int position = GLES20.glGetAttribLocation(pointProgram, "a_Position");
            int matrix = GLES20.glGetUniformLocation(pointProgram, "u_Mvp");
            int size = GLES20.glGetUniformLocation(pointProgram, "u_PointSize");
            int color = GLES20.glGetUniformLocation(pointProgram, "u_Color");
            GLES20.glUniformMatrix4fv(matrix, 1, false, mvp, 0);
            GLES20.glUniform1f(size, sizePx);
            GLES20.glUniform4f(color, r, g, b, a);
            buffer.position(0);
            GLES20.glEnableVertexAttribArray(position);
            GLES20.glVertexAttribPointer(position, 3, GLES20.GL_FLOAT, false, 0, buffer);
            GLES20.glDrawArrays(GLES20.GL_POINTS, 0, vertices);
            GLES20.glDisableVertexAttribArray(position);
        }

        void rotate(float dx, float dy) {
            yaw += dx;
            pitch = clamp(pitch + dy, -89f, 89f);
        }

        void zoom(float ratio) {
            if (!Float.isFinite(ratio) || ratio <= 0f) return;
            zoom = clamp(zoom * ratio, 0.25f, 7f);
        }

        void reset() {
            yaw = -35f;
            pitch = 22f;
            zoom = 1f;
            pointSize = 10f;
        }

        void changePointSize(float delta) {
            pointSize = clamp(pointSize + delta, 3f, 30f);
        }

        float pointSize() { return pointSize; }

        private static float[] normalize(List<PointCloudExportCore.Point> input) {
            if (input == null || input.isEmpty()) return new float[0];
            double minX = Double.POSITIVE_INFINITY, minY = Double.POSITIVE_INFINITY;
            double minZ = Double.POSITIVE_INFINITY, maxX = Double.NEGATIVE_INFINITY;
            double maxY = Double.NEGATIVE_INFINITY, maxZ = Double.NEGATIVE_INFINITY;
            for (PointCloudExportCore.Point point : input) {
                minX = Math.min(minX, point.x); maxX = Math.max(maxX, point.x);
                minY = Math.min(minY, point.y); maxY = Math.max(maxY, point.y);
                minZ = Math.min(minZ, point.z); maxZ = Math.max(maxZ, point.z);
            }
            double cx = (minX + maxX) * 0.5;
            double cy = (minY + maxY) * 0.5;
            double cz = (minZ + maxZ) * 0.5;
            double span = Math.max(maxX - minX, Math.max(maxY - minY, maxZ - minZ));
            double scale = span > 1e-12 ? 2.45 / span : 1.0;
            float[] output = new float[input.size() * 3];
            for (int i = 0; i < input.size(); i++) {
                PointCloudExportCore.Point point = input.get(i);
                output[i * 3] = (float) ((point.x - cx) * scale);
                output[i * 3 + 1] = (float) ((point.y - cy) * scale);
                output[i * 3 + 2] = (float) ((point.z - cz) * scale);
            }
            return output;
        }

        private static float[] createGrid() {
            List<Float> values = new ArrayList<Float>();
            float limit = 1.5f;
            float step = 0.25f;
            for (float value = -limit; value <= limit + 0.001f; value += step) {
                addLine(values, -limit, 0f, value, limit, 0f, value);
                addLine(values, value, 0f, -limit, value, 0f, limit);
            }
            float[] output = new float[values.size()];
            for (int i = 0; i < values.size(); i++) output[i] = values.get(i);
            return output;
        }

        private static void addLine(List<Float> values,
                                    float x1, float y1, float z1,
                                    float x2, float y2, float z2) {
            values.add(x1); values.add(y1); values.add(z1);
            values.add(x2); values.add(y2); values.add(z2);
        }

        private static FloatBuffer direct(float[] values) {
            FloatBuffer buffer = ByteBuffer.allocateDirect(Math.max(1, values.length) * 4)
                    .order(ByteOrder.nativeOrder()).asFloatBuffer();
            if (values.length > 0) buffer.put(values);
            buffer.position(0);
            return buffer;
        }

        private static int link(String vertex, String fragment) {
            int vertexShader = compile(GLES20.GL_VERTEX_SHADER, vertex);
            int fragmentShader = compile(GLES20.GL_FRAGMENT_SHADER, fragment);
            int program = GLES20.glCreateProgram();
            GLES20.glAttachShader(program, vertexShader);
            GLES20.glAttachShader(program, fragmentShader);
            GLES20.glLinkProgram(program);
            int[] linked = new int[1];
            GLES20.glGetProgramiv(program, GLES20.GL_LINK_STATUS, linked, 0);
            if (linked[0] == 0) {
                String log = GLES20.glGetProgramInfoLog(program);
                GLES20.glDeleteProgram(program);
                throw new IllegalStateException("No se pudo enlazar visor OpenGL: " + log);
            }
            GLES20.glDeleteShader(vertexShader);
            GLES20.glDeleteShader(fragmentShader);
            return program;
        }

        private static int compile(int type, String source) {
            int shader = GLES20.glCreateShader(type);
            GLES20.glShaderSource(shader, source);
            GLES20.glCompileShader(shader);
            int[] compiled = new int[1];
            GLES20.glGetShaderiv(shader, GLES20.GL_COMPILE_STATUS, compiled, 0);
            if (compiled[0] == 0) {
                String log = GLES20.glGetShaderInfoLog(shader);
                GLES20.glDeleteShader(shader);
                throw new IllegalStateException("No se pudo compilar visor OpenGL: " + log);
            }
            return shader;
        }

        private static float clamp(float value, float min, float max) {
            return Math.max(min, Math.min(max, value));
        }
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
                    if (point == null) { invalid++; continue; }
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
                    + "\nPuntos JSON " + rawPointCount + " · válidos GPU " + points.size()
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

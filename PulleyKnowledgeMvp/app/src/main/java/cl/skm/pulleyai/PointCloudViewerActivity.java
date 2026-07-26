package cl.skm.pulleyai;

import android.app.Activity;
import android.content.Intent;
import android.graphics.Color;
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

/** Interactive OpenGL ES viewer for bounded, non-metric seed point clouds. */
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
        try {
            String path = getIntent().getStringExtra(EXTRA_REPORT_PATH);
            if (path == null || path.trim().isEmpty()) {
                throw new IllegalStateException("No se recibió el resultado de geometría semilla");
            }
            cloud = CloudData.read(new File(path));
            buildUi();
        } catch (Exception error) {
            String message = error.getMessage() == null ? error.getClass().getSimpleName() : error.getMessage();
            TextView failure = new TextView(this);
            failure.setPadding(dp(24), dp(24), dp(24), dp(24));
            failure.setTextSize(18);
            failure.setTextColor(Color.rgb(145, 28, 28));
            failure.setBackgroundColor(Color.rgb(244, 247, 249));
            failure.setText("NO SE PUDO ABRIR LA NUBE 3D\n\n" + message);
            setContentView(failure);
        }
    }

    private void buildUi() {
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(Color.rgb(16, 23, 29));
        root.setPadding(dp(12), dp(10), dp(12), dp(10));

        TextView title = text("RESULTADOS 3D · ALPHA54 LAB2", 22, true);
        title.setTextColor(Color.WHITE);
        root.addView(title);

        information = text(cloud.summary(), 13, false);
        information.setTextColor(cloud.points.isEmpty()
                ? Color.rgb(255, 155, 130) : Color.rgb(170, 225, 190));
        information.setPadding(0, dp(4), 0, dp(8));
        root.addView(information);

        if (cloud.points.isEmpty()) {
            TextView empty = text(
                    "NO HAY COORDENADAS 3D PARA MOSTRAR\n\n"
                            + "El análisis terminó sin una geometría semilla triangulada. Revise pares utilizables, "
                            + "solape, paralaje y error de reproyección antes de repetir la captura.",
                    18, true);
            empty.setGravity(Gravity.CENTER);
            empty.setTextColor(Color.rgb(255, 180, 145));
            empty.setBackgroundColor(Color.rgb(28, 39, 48));
            root.addView(empty, new LinearLayout.LayoutParams(-1, 0, 1f));
        } else {
            surface = new CloudSurfaceView(cloud);
            root.addView(surface, new LinearLayout.LayoutParams(-1, 0, 1f));

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
        }

        LinearLayout exports = row();
        Button ply = button("EXPORTAR PLY");
        ply.setEnabled(!cloud.points.isEmpty());
        ply.setOnClickListener(view -> chooseExport(SAVE_PLY, "nube_semilla_alpha54.ply"));
        exports.addView(ply, weight());
        Button xyz = button("EXPORTAR XYZ");
        xyz.setEnabled(!cloud.points.isEmpty());
        xyz.setOnClickListener(view -> chooseExport(SAVE_XYZ, "nube_semilla_alpha54.xyz"));
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
        params.setMargins(dp(3), dp(4), dp(3), 0);
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
            setEGLContextClientVersion(2);
            setPreserveEGLContextOnPause(true);
            renderer = new CloudRenderer(data);
            setRenderer(renderer);
            setRenderMode(GLSurfaceView.RENDERMODE_WHEN_DIRTY);
            setBackgroundColor(Color.rgb(12, 18, 23));
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
                    requestRender();
                }
                return true;
            }
            float x = event.getX();
            float y = event.getY();
            if (event.getActionMasked() == MotionEvent.ACTION_MOVE) {
                renderer.rotate((x - previousX) * 0.45f, (y - previousY) * 0.45f);
                requestRender();
            }
            previousX = x;
            previousY = y;
            return true;
        }

        void resetView() {
            renderer.reset();
            requestRender();
        }

        void changePointSize(float delta) {
            renderer.changePointSize(delta);
            information.setText(cloud.summary() + String.format(Locale.ROOT,
                    "\nTamaño visual de punto %.1f px", renderer.pointSize()));
            requestRender();
        }
    }

    private static final class CloudRenderer implements GLSurfaceView.Renderer {
        private static final String VERTEX_SHADER =
                "uniform mat4 u_Mvp;\n"
                        + "uniform float u_PointSize;\n"
                        + "attribute vec3 a_Position;\n"
                        + "attribute vec4 a_Color;\n"
                        + "varying vec4 v_Color;\n"
                        + "void main(){ gl_Position=u_Mvp*vec4(a_Position,1.0);"
                        + " gl_PointSize=u_PointSize; v_Color=a_Color; }";
        private static final String FRAGMENT_SHADER =
                "precision mediump float;\n"
                        + "varying vec4 v_Color;\n"
                        + "void main(){ gl_FragColor=v_Color; }";

        private final FloatBuffer positions;
        private final FloatBuffer colors;
        private final int count;
        private final float[] projection = new float[16];
        private final float[] view = new float[16];
        private final float[] model = new float[16];
        private final float[] temporary = new float[16];
        private final float[] mvp = new float[16];
        private int program;
        private int positionLocation;
        private int colorLocation;
        private int mvpLocation;
        private int pointSizeLocation;
        private float yaw = -28f;
        private float pitch = 14f;
        private float zoom = 1f;
        private float pointSize = 6f;

        CloudRenderer(CloudData data) {
            count = data.points.size();
            float[] normalized = normalize(data.points);
            float[] rgba = colors(data.points);
            positions = direct(normalized);
            colors = direct(rgba);
        }

        @Override public void onSurfaceCreated(GL10 gl, EGLConfig config) {
            GLES20.glClearColor(0.045f, 0.07f, 0.09f, 1f);
            GLES20.glEnable(GLES20.GL_DEPTH_TEST);
            program = link(VERTEX_SHADER, FRAGMENT_SHADER);
            positionLocation = GLES20.glGetAttribLocation(program, "a_Position");
            colorLocation = GLES20.glGetAttribLocation(program, "a_Color");
            mvpLocation = GLES20.glGetUniformLocation(program, "u_Mvp");
            pointSizeLocation = GLES20.glGetUniformLocation(program, "u_PointSize");
        }

        @Override public void onSurfaceChanged(GL10 gl, int width, int height) {
            GLES20.glViewport(0, 0, width, height);
            float aspect = height == 0 ? 1f : (float) width / (float) height;
            Matrix.perspectiveM(projection, 0, 44f, aspect, 0.1f, 30f);
            Matrix.setLookAtM(view, 0, 0f, 0f, 4.2f, 0f, 0f, 0f, 0f, 1f, 0f);
        }

        @Override public void onDrawFrame(GL10 gl) {
            GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT | GLES20.GL_DEPTH_BUFFER_BIT);
            if (count <= 0 || program == 0) return;
            Matrix.setIdentityM(model, 0);
            Matrix.scaleM(model, 0, zoom, zoom, zoom);
            Matrix.rotateM(model, 0, pitch, 1f, 0f, 0f);
            Matrix.rotateM(model, 0, yaw, 0f, 1f, 0f);
            Matrix.multiplyMM(temporary, 0, view, 0, model, 0);
            Matrix.multiplyMM(mvp, 0, projection, 0, temporary, 0);

            GLES20.glUseProgram(program);
            GLES20.glUniformMatrix4fv(mvpLocation, 1, false, mvp, 0);
            GLES20.glUniform1f(pointSizeLocation, pointSize);
            positions.position(0);
            colors.position(0);
            GLES20.glEnableVertexAttribArray(positionLocation);
            GLES20.glVertexAttribPointer(positionLocation, 3, GLES20.GL_FLOAT, false, 0, positions);
            GLES20.glEnableVertexAttribArray(colorLocation);
            GLES20.glVertexAttribPointer(colorLocation, 4, GLES20.GL_FLOAT, false, 0, colors);
            GLES20.glDrawArrays(GLES20.GL_POINTS, 0, count);
            GLES20.glDisableVertexAttribArray(positionLocation);
            GLES20.glDisableVertexAttribArray(colorLocation);
        }

        void rotate(float dx, float dy) {
            yaw += dx;
            pitch = clamp(pitch + dy, -89f, 89f);
        }

        void zoom(float ratio) {
            if (!Float.isFinite(ratio) || ratio <= 0f) return;
            zoom = clamp(zoom * ratio, 0.25f, 8f);
        }

        void reset() {
            yaw = -28f;
            pitch = 14f;
            zoom = 1f;
            pointSize = 6f;
        }

        void changePointSize(float delta) {
            pointSize = clamp(pointSize + delta, 2f, 18f);
        }

        float pointSize() { return pointSize; }

        private static float[] normalize(List<PointCloudExportCore.Point> points) {
            double cx = 0, cy = 0, cz = 0;
            for (PointCloudExportCore.Point point : points) {
                cx += point.x; cy += point.y; cz += point.z;
            }
            int size = Math.max(1, points.size());
            cx /= size; cy /= size; cz /= size;
            double radius = 0;
            for (PointCloudExportCore.Point point : points) {
                double dx = point.x - cx, dy = point.y - cy, dz = point.z - cz;
                radius = Math.max(radius, Math.sqrt(dx * dx + dy * dy + dz * dz));
            }
            double scale = radius > 1e-12 ? 1.35 / radius : 1.0;
            float[] output = new float[points.size() * 3];
            for (int i = 0; i < points.size(); i++) {
                PointCloudExportCore.Point point = points.get(i);
                output[i * 3] = (float) ((point.x - cx) * scale);
                output[i * 3 + 1] = (float) ((point.y - cy) * scale);
                output[i * 3 + 2] = (float) ((point.z - cz) * scale);
            }
            return output;
        }

        private static float[] colors(List<PointCloudExportCore.Point> points) {
            double maxError = 0;
            for (PointCloudExportCore.Point point : points) {
                if (Double.isFinite(point.errorPx) && point.errorPx >= 0) {
                    maxError = Math.max(maxError, point.errorPx);
                }
            }
            float[] output = new float[points.size() * 4];
            for (int i = 0; i < points.size(); i++) {
                int[] rgb = PointCloudExportCore.errorColor(points.get(i).errorPx, maxError);
                output[i * 4] = rgb[0] / 255f;
                output[i * 4 + 1] = rgb[1] / 255f;
                output[i * 4 + 2] = rgb[2] / 255f;
                output[i * 4 + 3] = 1f;
            }
            return output;
        }

        private static FloatBuffer direct(float[] values) {
            FloatBuffer buffer = ByteBuffer.allocateDirect(values.length * 4)
                    .order(ByteOrder.nativeOrder()).asFloatBuffer();
            buffer.put(values).position(0);
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
        final List<PointCloudExportCore.Point> points;

        CloudData(String schema, boolean solved, boolean geometryReady,
                  boolean metricScale, boolean globalReconstruction, String state,
                  int leftFrame, int rightFrame, double reprojectionRmsPx,
                  double parallaxDegrees, List<PointCloudExportCore.Point> points) {
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
            this.points = points;
        }

        static CloudData read(File report) throws Exception {
            if (report == null || !report.isFile()) {
                throw new IllegalStateException("No existe imported_seed_geometry.json");
            }
            JSONObject json = new JSONObject(readText(report));
            JSONArray array = json.optJSONArray("points");
            List<PointCloudExportCore.Point> points = new ArrayList<PointCloudExportCore.Point>();
            if (array != null) {
                for (int i = 0; i < array.length(); i++) {
                    JSONObject point = array.optJSONObject(i);
                    if (point == null) continue;
                    points.add(new PointCloudExportCore.Point(
                            point.optDouble("x", 0.0),
                            point.optDouble("y", 0.0),
                            point.optDouble("z", 0.0),
                            point.optDouble("errorPx", -1.0),
                            point.optDouble("parallaxDegrees", -1.0)));
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
                    points);
        }

        String summary() {
            String classification = globalReconstruction
                    ? "NUBE GLOBAL" : "NUBE SEMILLA LOCAL";
            String scale = metricScale ? "ESCALA MÉTRICA" : "SIN ESCALA MÉTRICA";
            String quality = geometryReady ? "READY" : solved ? "REVIEW" : "BLOQUEADA";
            return classification + " · " + scale + " · " + quality
                    + "\nPuntos visibles " + points.size()
                    + (leftFrame >= 0 && rightFrame >= 0 ? " · par " + leftFrame + "-" + rightFrame : "")
                    + (Double.isFinite(reprojectionRmsPx)
                    ? String.format(Locale.ROOT, " · RMS %.2f px", reprojectionRmsPx) : "")
                    + (Double.isFinite(parallaxDegrees)
                    ? String.format(Locale.ROOT, " · paralaje %.2f°", parallaxDegrees) : "")
                    + "\nResultado de diagnóstico; no constituye medición ni liberación industrial.";
        }

        private static String readText(File file) throws Exception {
            try (BufferedInputStream input = new BufferedInputStream(new FileInputStream(file));
                 ByteArrayOutputStream output = new ByteArrayOutputStream(
                         (int) Math.min(file.length(), 2L * 1024L * 1024L))) {
                byte[] buffer = new byte[16 * 1024];
                int read;
                while ((read = input.read(buffer)) >= 0) output.write(buffer, 0, read);
                return new String(output.toByteArray(), StandardCharsets.UTF_8);
            }
        }
    }
}

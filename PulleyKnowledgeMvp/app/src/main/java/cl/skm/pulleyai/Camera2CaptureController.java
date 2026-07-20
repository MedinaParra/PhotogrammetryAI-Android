package cl.skm.pulleyai;

import android.app.Activity;
import android.graphics.Matrix;
import android.graphics.RectF;
import android.graphics.SurfaceTexture;
import android.hardware.camera2.CameraAccessException;
import android.hardware.camera2.CameraCaptureSession;
import android.hardware.camera2.CameraCharacteristics;
import android.hardware.camera2.CameraDevice;
import android.hardware.camera2.CameraManager;
import android.hardware.camera2.CaptureRequest;
import android.hardware.camera2.CaptureResult;
import android.hardware.camera2.TotalCaptureResult;
import android.hardware.camera2.params.StreamConfigurationMap;
import android.media.Image;
import android.media.ImageReader;
import android.os.Handler;
import android.os.HandlerThread;
import android.util.Size;
import android.view.Surface;
import android.view.TextureView;

import java.nio.ByteBuffer;
import java.util.Arrays;
import java.util.Comparator;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

/** Camera2 lifecycle owner for a fixed industrial landscape capture station. */
public final class Camera2CaptureController {
    public interface Callback {
        void onReady();
        void onCaptured(byte[] jpeg, Metadata metadata);
        void onError(String message);
    }

    public static final class Metadata {
        public final Long exposureNs;
        public final Integer iso;
        public final Float focusDistance;
        public final String cameraId;
        public final int sensorOrientation;
        public final int jpegOrientation;
        public final Float nominalFocalLengthMm;

        Metadata(Long exposureNs, Integer iso, Float focusDistance, String cameraId,
                 int sensorOrientation, int jpegOrientation, Float nominalFocalLengthMm) {
            this.exposureNs = exposureNs;
            this.iso = iso;
            this.focusDistance = focusDistance;
            this.cameraId = cameraId;
            this.sensorOrientation = sensorOrientation;
            this.jpegOrientation = jpegOrientation;
            this.nominalFocalLengthMm = nominalFocalLengthMm;
        }
    }

    private final Activity activity;
    private final TextureView preview;
    private final Callback callback;
    private final Semaphore cameraLock = new Semaphore(1);
    private final Object captureLock = new Object();

    private HandlerThread thread;
    private Handler handler;
    private CameraDevice camera;
    private CameraCaptureSession session;
    private ImageReader reader;
    private Size previewSize;
    private String cameraId;
    private int sensorOrientation = 90;
    private int autofocusMode = CaptureRequest.CONTROL_AF_MODE_OFF;
    private boolean frontFacing;
    private Float nominalFocalLengthMm;
    private byte[] pendingJpeg;
    private Metadata pendingMetadata;
    private boolean capturePending;

    public Camera2CaptureController(Activity activity, TextureView preview, Callback callback) {
        this.activity = activity;
        this.preview = preview;
        this.callback = callback;
    }

    public void start() {
        if (thread != null) return;
        thread = new HandlerThread("PoleaCamera2");
        thread.start();
        handler = new Handler(thread.getLooper());
        if (preview.isAvailable()) openCamera();
        else preview.setSurfaceTextureListener(new TextureView.SurfaceTextureListener() {
            @Override public void onSurfaceTextureAvailable(SurfaceTexture surface, int width, int height) { openCamera(); }
            @Override public void onSurfaceTextureSizeChanged(SurfaceTexture surface, int width, int height) {
                configureTransform(width, height);
            }
            @Override public boolean onSurfaceTextureDestroyed(SurfaceTexture surface) { return true; }
            @Override public void onSurfaceTextureUpdated(SurfaceTexture surface) { }
        });
    }

    public void stop() {
        closeCamera();
        if (thread != null) {
            thread.quitSafely();
            try { thread.join(); }
            catch (InterruptedException e) { Thread.currentThread().interrupt(); }
            thread = null;
            handler = null;
        }
    }

    public boolean capture() {
        if (camera == null || session == null || reader == null) return false;
        synchronized (captureLock) {
            if (capturePending) return false;
            capturePending = true;
            pendingJpeg = null;
            pendingMetadata = null;
        }
        try {
            CaptureRequest.Builder request = camera.createCaptureRequest(CameraDevice.TEMPLATE_STILL_CAPTURE);
            request.addTarget(reader.getSurface());
            request.set(CaptureRequest.CONTROL_AF_MODE, autofocusMode);
            request.set(CaptureRequest.CONTROL_AE_MODE, CaptureRequest.CONTROL_AE_MODE_ON);
            final int jpegOrientation = jpegOrientation();
            request.set(CaptureRequest.JPEG_ORIENTATION, jpegOrientation);
            session.capture(request.build(), new CameraCaptureSession.CaptureCallback() {
                @Override public void onCaptureCompleted(CameraCaptureSession value, CaptureRequest captureRequest,
                                                         TotalCaptureResult result) {
                    synchronized (captureLock) {
                        pendingMetadata = new Metadata(
                                result.get(CaptureResult.SENSOR_EXPOSURE_TIME),
                                result.get(CaptureResult.SENSOR_SENSITIVITY),
                                result.get(CaptureResult.LENS_FOCUS_DISTANCE),
                                cameraId, sensorOrientation, jpegOrientation, nominalFocalLengthMm
                        );
                    }
                    dispatchIfReady(false);
                }
            }, handler);
            return true;
        } catch (Exception e) {
            clearPending();
            callback.onError(message(e));
            return false;
        }
    }

    private void openCamera() {
        CameraManager manager = (CameraManager) activity.getSystemService(Activity.CAMERA_SERVICE);
        final AtomicBoolean permitHeld = new AtomicBoolean(false);
        try {
            chooseCamera(manager);
            if (!cameraLock.tryAcquire(2500, TimeUnit.MILLISECONDS)) {
                throw new IllegalStateException("Cámara ocupada");
            }
            permitHeld.set(true);
            manager.openCamera(cameraId, new CameraDevice.StateCallback() {
                @Override public void onOpened(CameraDevice value) {
                    releasePermit(permitHeld);
                    camera = value;
                    startPreview();
                }

                @Override public void onDisconnected(CameraDevice value) {
                    releasePermit(permitHeld);
                    value.close();
                    camera = null;
                    callback.onError("Cámara desconectada");
                }

                @Override public void onError(CameraDevice value, int error) {
                    releasePermit(permitHeld);
                    value.close();
                    camera = null;
                    callback.onError("Error de cámara " + error);
                }
            }, handler);
        } catch (Exception e) {
            releasePermit(permitHeld);
            callback.onError(message(e));
        }
    }

    private void chooseCamera(CameraManager manager) throws CameraAccessException {
        for (String id : manager.getCameraIdList()) {
            CameraCharacteristics characteristics = manager.getCameraCharacteristics(id);
            Integer facing = characteristics.get(CameraCharacteristics.LENS_FACING);
            if (facing != null && facing == CameraCharacteristics.LENS_FACING_FRONT) continue;
            StreamConfigurationMap map = characteristics.get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP);
            if (map == null) continue;
            Size[] jpegSizes = map.getOutputSizes(android.graphics.ImageFormat.JPEG);
            Size[] previewSizes = map.getOutputSizes(SurfaceTexture.class);
            if (jpegSizes == null || jpegSizes.length == 0 || previewSizes == null || previewSizes.length == 0) continue;

            Arrays.sort(jpegSizes, new Comparator<Size>() {
                @Override public int compare(Size left, Size right) {
                    return Long.compare(area(right), area(left));
                }
            });
            Size photoSize = jpegSizes[jpegSizes.length - 1];
            for (Size candidate : jpegSizes) {
                long pixels = area(candidate);
                if (pixels >= 3_000_000L && pixels <= 12_500_000L) {
                    photoSize = candidate;
                    break;
                }
            }
            previewSize = choosePreview(previewSizes, photoSize);
            reader = ImageReader.newInstance(photoSize.getWidth(), photoSize.getHeight(),
                    android.graphics.ImageFormat.JPEG, 2);
            reader.setOnImageAvailableListener(this::onImageAvailable, handler);
            Integer orientation = characteristics.get(CameraCharacteristics.SENSOR_ORIENTATION);
            sensorOrientation = orientation == null ? 90 : orientation;
            frontFacing = facing != null && facing == CameraCharacteristics.LENS_FACING_FRONT;
            float[] focalLengths = characteristics.get(CameraCharacteristics.LENS_INFO_AVAILABLE_FOCAL_LENGTHS);
            nominalFocalLengthMm = focalLengths == null || focalLengths.length == 0 ? null : focalLengths[0];
            int[] modes = characteristics.get(CameraCharacteristics.CONTROL_AF_AVAILABLE_MODES);
            autofocusMode = contains(modes, CaptureRequest.CONTROL_AF_MODE_CONTINUOUS_PICTURE)
                    ? CaptureRequest.CONTROL_AF_MODE_CONTINUOUS_PICTURE
                    : contains(modes, CaptureRequest.CONTROL_AF_MODE_AUTO)
                    ? CaptureRequest.CONTROL_AF_MODE_AUTO
                    : CaptureRequest.CONTROL_AF_MODE_OFF;
            cameraId = id;
            return;
        }
        throw new IllegalStateException("No hay cámara posterior compatible");
    }

    private void startPreview() {
        try {
            SurfaceTexture texture = preview.getSurfaceTexture();
            if (texture == null) throw new IllegalStateException("Preview no disponible");
            texture.setDefaultBufferSize(previewSize.getWidth(), previewSize.getHeight());
            Surface surface = new Surface(texture);
            CaptureRequest.Builder request = camera.createCaptureRequest(CameraDevice.TEMPLATE_PREVIEW);
            request.addTarget(surface);
            request.set(CaptureRequest.CONTROL_AF_MODE, autofocusMode);
            request.set(CaptureRequest.CONTROL_AE_MODE, CaptureRequest.CONTROL_AE_MODE_ON);
            configureTransform(preview.getWidth(), preview.getHeight());
            camera.createCaptureSession(Arrays.asList(surface, reader.getSurface()),
                    new CameraCaptureSession.StateCallback() {
                        @Override public void onConfigured(CameraCaptureSession configured) {
                            session = configured;
                            try {
                                configured.setRepeatingRequest(request.build(), null, handler);
                                callback.onReady();
                            } catch (CameraAccessException e) {
                                callback.onError(message(e));
                            }
                        }

                        @Override public void onConfigureFailed(CameraCaptureSession configured) {
                            callback.onError("No se pudo iniciar el preview");
                        }
                    }, handler);
        } catch (Exception e) {
            callback.onError(message(e));
        }
    }

    private void onImageAvailable(ImageReader source) {
        Image image = null;
        try {
            image = source.acquireNextImage();
            if (image == null) return;
            ByteBuffer buffer = image.getPlanes()[0].getBuffer();
            byte[] bytes = new byte[buffer.remaining()];
            buffer.get(bytes);
            synchronized (captureLock) { pendingJpeg = bytes; }
            dispatchIfReady(false);
            Handler current = handler;
            if (current != null) current.postDelayed(new Runnable() {
                @Override public void run() { dispatchIfReady(true); }
            }, 450L);
        } catch (Exception e) {
            clearPending();
            callback.onError(message(e));
        } finally {
            if (image != null) image.close();
        }
    }

    private void dispatchIfReady(boolean allowMetadataFallback) {
        byte[] jpeg;
        Metadata metadata;
        synchronized (captureLock) {
            if (!capturePending || pendingJpeg == null) return;
            if (pendingMetadata == null && !allowMetadataFallback) return;
            jpeg = pendingJpeg;
            metadata = pendingMetadata == null
                    ? new Metadata(null, null, null, cameraId, sensorOrientation,
                    jpegOrientation(), nominalFocalLengthMm)
                    : pendingMetadata;
            pendingJpeg = null;
            pendingMetadata = null;
            capturePending = false;
        }
        callback.onCaptured(jpeg, metadata);
    }

    private void clearPending() {
        synchronized (captureLock) {
            pendingJpeg = null;
            pendingMetadata = null;
            capturePending = false;
        }
    }

    private int jpegOrientation() {
        int surfaceRotation = activity.getWindowManager().getDefaultDisplay().getRotation();
        int displayDegrees = LandscapeCaptureMath.displayRotationDegrees(surfaceRotation);
        return LandscapeCaptureMath.jpegOrientation(sensorOrientation, displayDegrees, frontFacing);
    }

    private void configureTransform(int viewWidth, int viewHeight) {
        if (previewSize == null || viewWidth <= 0 || viewHeight <= 0) return;
        int rotation = activity.getWindowManager().getDefaultDisplay().getRotation();
        Matrix matrix = new Matrix();
        RectF viewRect = new RectF(0, 0, viewWidth, viewHeight);
        RectF bufferRect = new RectF(0, 0, previewSize.getHeight(), previewSize.getWidth());
        float centerX = viewRect.centerX();
        float centerY = viewRect.centerY();
        if (rotation == Surface.ROTATION_90 || rotation == Surface.ROTATION_270) {
            bufferRect.offset(centerX - bufferRect.centerX(), centerY - bufferRect.centerY());
            matrix.setRectToRect(viewRect, bufferRect, Matrix.ScaleToFit.FILL);
            float scale = Math.max((float) viewHeight / previewSize.getHeight(),
                    (float) viewWidth / previewSize.getWidth());
            matrix.postScale(scale, scale, centerX, centerY);
            matrix.postRotate(90f * (rotation - 2), centerX, centerY);
        } else if (rotation == Surface.ROTATION_180) {
            matrix.postRotate(180f, centerX, centerY);
        }
        preview.setTransform(matrix);
    }

    private void closeCamera() {
        boolean locked = false;
        try {
            locked = cameraLock.tryAcquire(2500, TimeUnit.MILLISECONDS);
            if (!locked) {
                callback.onError("No fue posible cerrar la cámara de forma segura");
                return;
            }
            if (session != null) session.close();
            if (camera != null) camera.close();
            if (reader != null) reader.close();
            session = null;
            camera = null;
            reader = null;
            clearPending();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        } finally {
            if (locked) cameraLock.release();
        }
    }

    private void releasePermit(AtomicBoolean held) {
        if (held.compareAndSet(true, false)) cameraLock.release();
    }

    private static Size choosePreview(Size[] sizes, Size photoSize) {
        Size best = null;
        Size smallest = sizes[0];
        double photoRatio = (double) photoSize.getWidth() / photoSize.getHeight();
        double bestRatioError = Double.MAX_VALUE;
        for (Size candidate : sizes) {
            long candidateArea = area(candidate);
            if (candidateArea < area(smallest)) smallest = candidate;
            if (candidate.getWidth() > 1920 || candidate.getHeight() > 1080) continue;
            double ratio = (double) candidate.getWidth() / candidate.getHeight();
            double error = Math.abs(ratio - photoRatio);
            if (best == null || error < bestRatioError - 1e-6
                    || (Math.abs(error - bestRatioError) < 1e-6 && candidateArea > area(best))) {
                best = candidate;
                bestRatioError = error;
            }
        }
        return best == null ? smallest : best;
    }

    private static long area(Size size) {
        return 1L * size.getWidth() * size.getHeight();
    }

    private static boolean contains(int[] values, int target) {
        if (values == null) return false;
        for (int value : values) if (value == target) return true;
        return false;
    }

    private static String message(Exception error) {
        String text = error.getMessage();
        return text == null || text.trim().isEmpty() ? error.getClass().getSimpleName() : text;
    }
}

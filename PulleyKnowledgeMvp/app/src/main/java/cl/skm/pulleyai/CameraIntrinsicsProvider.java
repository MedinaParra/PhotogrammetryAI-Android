package cl.skm.pulleyai;

import android.content.Context;
import android.hardware.camera2.CameraAccessException;
import android.hardware.camera2.CameraCharacteristics;
import android.hardware.camera2.CameraManager;
import android.util.SizeF;

/** Resolves per-frame focal lengths in pixels from Camera2 physical calibration data. */
public final class CameraIntrinsicsProvider {
    private final CameraManager manager;

    public CameraIntrinsicsProvider(Context context) {
        manager = (CameraManager) context.getApplicationContext()
                .getSystemService(Context.CAMERA_SERVICE);
    }

    public Resolution resolve(CaptureStore.Frame frame, int imageWidth, int imageHeight) {
        if (manager == null || frame == null || frame.cameraId == null
                || imageWidth <= 0 || imageHeight <= 0) {
            return Resolution.failed("CAMERA_METADATA_MISSING");
        }
        try {
            CameraCharacteristics characteristics = manager.getCameraCharacteristics(frame.cameraId);
            SizeF physical = characteristics.get(CameraCharacteristics.SENSOR_INFO_PHYSICAL_SIZE);
            Float focal = frame.focalLengthMm;
            if (focal == null || focal <= 0f) {
                float[] available = characteristics.get(
                        CameraCharacteristics.LENS_INFO_AVAILABLE_FOCAL_LENGTHS);
                focal = available == null || available.length == 0 ? null : available[0];
            }
            if (physical == null || focal == null || focal <= 0f
                    || physical.getWidth() <= 0f || physical.getHeight() <= 0f) {
                return Resolution.failed("PHYSICAL_CALIBRATION_UNAVAILABLE");
            }
            double sensorWidth = physical.getWidth();
            double sensorHeight = physical.getHeight();
            int orientation = frame.jpegOrientation == null ? 0
                    : LandscapeCaptureMath.normalize360(frame.jpegOrientation);
            if (orientation == 90 || orientation == 270) {
                double swap = sensorWidth;
                sensorWidth = sensorHeight;
                sensorHeight = swap;
            }
            double imageRatio = (double) imageWidth / imageHeight;
            double sensorRatio = sensorWidth / sensorHeight;
            double effectiveWidth = sensorWidth;
            double effectiveHeight = sensorHeight;
            if (imageRatio > sensorRatio) {
                effectiveHeight = sensorWidth / imageRatio;
            } else if (imageRatio < sensorRatio) {
                effectiveWidth = sensorHeight * imageRatio;
            }
            double fx = focal / effectiveWidth * imageWidth;
            double fy = focal / effectiveHeight * imageHeight;
            if (!plausible(fx, fy, imageWidth, imageHeight)) {
                return Resolution.failed("INTRINSICS_OUT_OF_RANGE");
            }
            return new Resolution(true, "CAMERA2_PHYSICAL", new EssentialPoseCore.Intrinsics(
                    fx, fy, imageWidth * 0.5, imageHeight * 0.5),
                    focal, physical.getWidth(), physical.getHeight());
        } catch (CameraAccessException error) {
            return Resolution.failed("CAMERA_ACCESS_" + error.getReason());
        } catch (RuntimeException error) {
            return Resolution.failed("CAMERA_CALIBRATION_ERROR");
        }
    }

    private static boolean plausible(double fx, double fy, int width, int height) {
        return Double.isFinite(fx) && Double.isFinite(fy)
                && fx > width * 0.35 && fx < width * 8.0
                && fy > height * 0.35 && fy < height * 8.0;
    }

    public static final class Resolution {
        public final boolean available;
        public final String source;
        public final EssentialPoseCore.Intrinsics intrinsics;
        public final Float focalLengthMm;
        public final Float sensorWidthMm;
        public final Float sensorHeightMm;

        Resolution(boolean available, String source, EssentialPoseCore.Intrinsics intrinsics,
                   Float focalLengthMm, Float sensorWidthMm, Float sensorHeightMm) {
            this.available = available;
            this.source = source;
            this.intrinsics = intrinsics;
            this.focalLengthMm = focalLengthMm;
            this.sensorWidthMm = sensorWidthMm;
            this.sensorHeightMm = sensorHeightMm;
        }

        static Resolution failed(String source) {
            return new Resolution(false, source, null, null, null, null);
        }
    }
}

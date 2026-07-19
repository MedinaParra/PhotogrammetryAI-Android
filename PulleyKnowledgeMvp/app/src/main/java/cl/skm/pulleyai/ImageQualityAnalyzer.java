package cl.skm.pulleyai;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Color;

/** Lightweight Android decoder feeding the pure deterministic quality gate. */
public final class ImageQualityAnalyzer {
    public static final class Result {
        public final int width;
        public final int height;
        public final double blurScore;
        public final double meanLuma;
        public final double darkFraction;
        public final double brightFraction;
        public final double motionScore;
        public final String status;
        public final String reason;

        Result(int width, int height, ImageQualityMath.Result quality) {
            this.width = width;
            this.height = height;
            this.blurScore = quality.blurScore;
            this.meanLuma = quality.meanLuma;
            this.darkFraction = quality.darkFraction;
            this.brightFraction = quality.brightFraction;
            this.motionScore = quality.motionScore;
            this.status = quality.status;
            this.reason = quality.reason;
        }

        public boolean accepted() {
            return "ACCEPTED".equals(status);
        }
    }

    private ImageQualityAnalyzer() {
    }

    public static Result analyze(byte[] jpegBytes, double motionScore) {
        if (jpegBytes == null || jpegBytes.length == 0) {
            return invalid(0, 0, motionScore, "JPEG vacío");
        }

        BitmapFactory.Options bounds = new BitmapFactory.Options();
        bounds.inJustDecodeBounds = true;
        BitmapFactory.decodeByteArray(jpegBytes, 0, jpegBytes.length, bounds);
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) {
            return invalid(0, 0, motionScore, "JPEG inválido");
        }

        int sample = 1;
        int largest = Math.max(bounds.outWidth, bounds.outHeight);
        while (largest / sample > 640) sample *= 2;

        BitmapFactory.Options options = new BitmapFactory.Options();
        options.inSampleSize = Math.max(1, sample);
        options.inPreferredConfig = Bitmap.Config.ARGB_8888;
        Bitmap bitmap = BitmapFactory.decodeByteArray(jpegBytes, 0, jpegBytes.length, options);
        if (bitmap == null) {
            return invalid(bounds.outWidth, bounds.outHeight, motionScore, "No se pudo decodificar la imagen");
        }

        try {
            int width = bitmap.getWidth();
            int height = bitmap.getHeight();
            int stride = Math.max(1, Math.min(width, height) / 220);
            int gridWidth = Math.max(3, (width + stride - 1) / stride);
            int gridHeight = Math.max(3, (height + stride - 1) / stride);
            double[] luma = new double[gridWidth * gridHeight];

            for (int gy = 0; gy < gridHeight; gy++) {
                int y = Math.min(height - 1, gy * stride);
                for (int gx = 0; gx < gridWidth; gx++) {
                    int x = Math.min(width - 1, gx * stride);
                    int color = bitmap.getPixel(x, y);
                    luma[gy * gridWidth + gx] = 0.2126 * Color.red(color)
                            + 0.7152 * Color.green(color)
                            + 0.0722 * Color.blue(color);
                }
            }

            ImageQualityMath.Result quality = ImageQualityMath.analyze(
                    luma,
                    gridWidth,
                    gridHeight,
                    motionScore
            );
            return new Result(bounds.outWidth, bounds.outHeight, quality);
        } finally {
            bitmap.recycle();
        }
    }

    private static Result invalid(int width, int height, double motionScore, String reason) {
        ImageQualityMath.Result quality = new ImageQualityMath.Result(
                0, 0, 1, 0, motionScore, "REJECTED", reason
        );
        return new Result(width, height, quality);
    }
}

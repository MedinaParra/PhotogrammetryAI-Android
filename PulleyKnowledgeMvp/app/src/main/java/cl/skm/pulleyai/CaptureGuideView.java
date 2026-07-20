package cl.skm.pulleyai;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.RectF;
import android.view.View;

/** Landscape overlay: crosshair, horizon and the twelve angular sectors of the active ring. */
public final class CaptureGuideView extends View {
    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private int mask;
    private int currentSector;
    private int nextSector;
    private boolean stable;
    private String band = "LOW";

    public CaptureGuideView(Context context) {
        super(context);
        setWillNotDraw(false);
    }

    public void update(int mask, int currentSector, int nextSector, String band, boolean stable) {
        this.mask = mask;
        this.currentSector = currentSector;
        this.nextSector = nextSector;
        this.band = band == null ? "LOW" : band;
        this.stable = stable;
        invalidate();
    }

    @Override protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        float width = getWidth();
        float height = getHeight();
        if (width <= 0 || height <= 0) return;

        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeWidth(dp(2));
        paint.setColor(stable ? Color.rgb(115, 245, 150) : Color.rgb(255, 184, 90));
        float cx = width / 2f;
        float cy = height / 2f;
        canvas.drawCircle(cx, cy, dp(34), paint);
        canvas.drawLine(cx - dp(52), cy, cx + dp(52), cy, paint);
        canvas.drawLine(cx, cy - dp(52), cx, cy + dp(52), paint);

        paint.setStrokeWidth(dp(1));
        paint.setColor(Color.argb(180, 255, 255, 255));
        canvas.drawLine(dp(22), cy, width - dp(22), cy, paint);

        float margin = dp(18);
        float gap = dp(5);
        float available = width - 2f * margin - 11f * gap;
        float cellWidth = available / CoveragePlanner.SECTOR_COUNT;
        float top = height - dp(42);
        float bottom = height - dp(16);
        paint.setStyle(Paint.Style.FILL);
        for (int sector = 0; sector < CoveragePlanner.SECTOR_COUNT; sector++) {
            float left = margin + sector * (cellWidth + gap);
            RectF rect = new RectF(left, top, left + cellWidth, bottom);
            if (CoveragePlanner.contains(mask, sector)) paint.setColor(Color.argb(220, 80, 190, 115));
            else if (sector == currentSector) paint.setColor(Color.argb(230, 255, 181, 72));
            else if (sector == nextSector) paint.setColor(Color.argb(230, 80, 165, 245));
            else paint.setColor(Color.argb(170, 45, 53, 59));
            canvas.drawRoundRect(rect, dp(4), dp(4), paint);
        }

        paint.setTextSize(dp(13));
        paint.setColor(Color.WHITE);
        paint.setStyle(Paint.Style.FILL);
        String label = "HIGH".equals(band) ? "ANILLO ALTO" : "ANILLO A EJE";
        canvas.drawText(label + " · sector " + (currentSector + 1) + "/12", margin, dp(28), paint);
    }

    private float dp(float value) {
        return value * getResources().getDisplayMetrics().density;
    }
}

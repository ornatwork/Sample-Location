package com.example.samplelocation;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.Rect;
import android.util.AttributeSet;
import android.view.View;

import androidx.annotation.Nullable;

public class CompassView extends View {

    private final Paint circlePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint tickPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint textPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint needlePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Rect textBounds = new Rect();
    private float headingDeg = 0f; // 0 = North

    public CompassView(Context context) { super(context); init(); }
    public CompassView(Context context, @Nullable AttributeSet attrs) { super(context, attrs); init(); }
    public CompassView(Context context, @Nullable AttributeSet attrs, int defStyleAttr) { super(context, attrs, defStyleAttr); init(); }

    private void init() {
        circlePaint.setStyle(Paint.Style.STROKE);
        circlePaint.setStrokeWidth(dp(2));

        tickPaint.setStyle(Paint.Style.STROKE);
        tickPaint.setStrokeWidth(dp(2));

        textPaint.setTextSize(dp(14));
        textPaint.setTextAlign(Paint.Align.CENTER);

        needlePaint.setStyle(Paint.Style.STROKE);
        needlePaint.setStrokeWidth(dp(3));
    }

    public void setHeading(float degrees) {
        // Normalize 0..360
        if (degrees < 0) degrees += 360f;
        if (degrees >= 360f) degrees -= 360f;
        this.headingDeg = degrees;
        invalidate();
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);

        int w = getWidth();
        int h = getHeight();
        int cx = w / 2;
        int cy = h / 2;
        float radius = Math.min(w, h) * 0.45f;

        // Colors inherit from theme (black by default); you can tweak if desired
        circlePaint.setColor(0xFF666666);
        tickPaint.setColor(0xFF666666);
        textPaint.setColor(0xFFFFFFFF); // white for N,E,S,W
        needlePaint.setColor(0xFFE53935); // red needle

        // Outer circle
        canvas.drawCircle(cx, cy, radius, circlePaint);

        // Ticks every 30°
        for (int deg = 0; deg < 360; deg += 30) {
            double rad = Math.toRadians(deg);
            float rOuter = radius;
            float rInner = radius - dp(deg % 90 == 0 ? 14 : 8);
            float sx = cx + (float) (rInner * Math.sin(rad));
            float sy = cy - (float) (rInner * Math.cos(rad));
            float ex = cx + (float) (rOuter * Math.sin(rad));
            float ey = cy - (float) (rOuter * Math.cos(rad));
            canvas.drawLine(sx, sy, ex, ey, tickPaint);
        }

        // Labels N E S W
        drawDir(canvas, "N", cx, cy, radius, 0);
        drawDir(canvas, "E", cx, cy, radius, 90);
        drawDir(canvas, "S", cx, cy, radius, 180);
        drawDir(canvas, "W", cx, cy, radius, 270);

        // Needle pointing to headingDeg (0 = North, increasing clockwise)
        canvas.save();
        canvas.translate(cx, cy);
        canvas.rotate(headingDeg); // rotate canvas so 0° points up

        // Draw needle: up (red) and tail (thin)
        Path p = new Path();
        float needleLen = radius * 0.85f;
        p.moveTo(0, -needleLen);
        p.lineTo(0, dp(10));
        canvas.drawPath(p, needlePaint);

        // Small center dot
        canvas.drawCircle(0, 0, dp(3), needlePaint);
        canvas.restore();
    }

    private void drawDir(Canvas c, String label, int cx, int cy, float radius, int deg) {
        double rad = Math.toRadians(deg);
        float rText = radius - dp(24);
        float tx = cx + (float) (rText * Math.sin(rad));
        float ty = cy - (float) (rText * Math.cos(rad));

        textPaint.getTextBounds(label, 0, label.length(), textBounds);
        // vertically center text on ty
        c.drawText(label, tx, ty + textBounds.height() / 2f, textPaint);
    }

    private float dp(float v) {
        return v * getResources().getDisplayMetrics().density;
    }
}

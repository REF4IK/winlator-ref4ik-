package com.winlator.cmod.widget;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;
import android.util.AttributeSet;
import android.util.TypedValue;
import android.view.View;

import java.util.Arrays;

public class FrameTimeGraphView extends View {
    private static final int DEFAULT_SAMPLE_COUNT = 40;
    private static final float TARGET_60_FPS_MS = 16.67f;
    private static final float TARGET_30_FPS_MS = 33.33f;
    private static final float MAX_FRAME_TIME_MS = 50.0f;

    private final float[] samples = new float[DEFAULT_SAMPLE_COUNT];
    private int nextIndex;
    private int sampleCount;

    private final Paint linePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint guide60Paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint guide30Paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint fillPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Path linePath = new Path();
    private final Path fillPath = new Path();

    public FrameTimeGraphView(Context context) {
        this(context, null);
    }

    public FrameTimeGraphView(Context context, AttributeSet attrs) {
        this(context, attrs, 0);
    }

    public FrameTimeGraphView(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        Arrays.fill(samples, TARGET_60_FPS_MS);

        linePaint.setStyle(Paint.Style.STROKE);
        linePaint.setStrokeWidth(dpToPx(1.8f));
        linePaint.setColor(0xFFFFC107);

        guide60Paint.setStyle(Paint.Style.STROKE);
        guide60Paint.setStrokeWidth(dpToPx(1f));
        guide60Paint.setColor(0x55FFFFFF);

        guide30Paint.setStyle(Paint.Style.STROKE);
        guide30Paint.setStrokeWidth(dpToPx(1f));
        guide30Paint.setColor(0x44FF5252);

        fillPaint.setStyle(Paint.Style.FILL);
        fillPaint.setColor(0x33FFC107);
    }

    public void addFrameTimeSample(float frameTimeMs) {
        if (Float.isNaN(frameTimeMs) || Float.isInfinite(frameTimeMs)) {
            return;
        }

        float clamped = Math.max(0f, Math.min(MAX_FRAME_TIME_MS, frameTimeMs));
        samples[nextIndex] = clamped;
        nextIndex = (nextIndex + 1) % samples.length;
        if (sampleCount < samples.length) {
            sampleCount++;
        }
        invalidate();
    }

    public void reset() {
        nextIndex = 0;
        sampleCount = 0;
        Arrays.fill(samples, TARGET_60_FPS_MS);
        invalidate();
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);

        int width = getWidth();
        int height = getHeight();
        if (width <= 0 || height <= 0) {
            return;
        }

        drawGuide(canvas, height, TARGET_60_FPS_MS, guide60Paint);
        drawGuide(canvas, height, TARGET_30_FPS_MS, guide30Paint);

        int points = sampleCount > 1 ? sampleCount : samples.length;
        if (points <= 1) {
            return;
        }

        float spacing = points > 1 ? (float) width / (points - 1) : width;
        linePath.reset();
        fillPath.reset();

        for (int i = 0; i < points; i++) {
            float sample = getOrderedSample(i, points);
            float x = i * spacing;
            float y = sampleToY(sample, height);

            if (i == 0) {
                linePath.moveTo(x, y);
                fillPath.moveTo(x, height);
                fillPath.lineTo(x, y);
            } else {
                linePath.lineTo(x, y);
                fillPath.lineTo(x, y);
            }
        }

        fillPath.lineTo(width, height);
        fillPath.close();

        canvas.drawPath(fillPath, fillPaint);
        canvas.drawPath(linePath, linePaint);
    }

    private void drawGuide(Canvas canvas, int height, float frameTimeMs, Paint paint) {
        float y = sampleToY(frameTimeMs, height);
        canvas.drawLine(0, y, getWidth(), y, paint);
    }

    private float getOrderedSample(int index, int points) {
        if (sampleCount == 0) {
            return TARGET_60_FPS_MS;
        }

        if (sampleCount < samples.length) {
            return samples[index];
        }

        int start = nextIndex;
        return samples[(start + index) % samples.length];
    }

    private float sampleToY(float sample, int height) {
        float normalized = sample / MAX_FRAME_TIME_MS;
        return Math.max(0f, Math.min(height, height - (normalized * height)));
    }

    private float dpToPx(float dp) {
        return TypedValue.applyDimension(
            TypedValue.COMPLEX_UNIT_DIP,
            dp,
            getResources().getDisplayMetrics()
        );
    }
}

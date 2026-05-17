package com.winlator.cmod.widget;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.DashPathEffect;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.Shader;
import android.util.AttributeSet;
import android.view.View;

import java.util.List;
import java.util.Locale;

/** Lightweight FPS-over-time line chart for profiling results. */
public class ProfilingChartView extends View {
    private final Paint linePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint fillPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint axisPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint markerPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint textPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Path linePath = new Path();
    private final Path fillPath = new Path();

    private List<Float> samples;
    private float avgFps = Float.NaN;
    private float low1Fps = Float.NaN;
    private float minFps = Float.NaN;
    private float maxFps = Float.NaN;

    public ProfilingChartView(Context context) { super(context); init(); }
    public ProfilingChartView(Context context, AttributeSet attrs) { super(context, attrs); init(); }
    public ProfilingChartView(Context context, AttributeSet attrs, int defStyle) { super(context, attrs, defStyle); init(); }

    private void init() {
        float density = getResources().getDisplayMetrics().density;
        linePaint.setStyle(Paint.Style.STROKE);
        linePaint.setStrokeWidth(2.0f * density);
        linePaint.setStrokeJoin(Paint.Join.ROUND);
        linePaint.setStrokeCap(Paint.Cap.ROUND);
        linePaint.setColor(0xFF4CAF50);

        fillPaint.setStyle(Paint.Style.FILL);

        axisPaint.setStyle(Paint.Style.STROKE);
        axisPaint.setStrokeWidth(1.0f * density);
        axisPaint.setColor(0x66FFFFFF);
        axisPaint.setPathEffect(new DashPathEffect(new float[]{4 * density, 4 * density}, 0));

        markerPaint.setStyle(Paint.Style.STROKE);
        markerPaint.setStrokeWidth(1.2f * density);

        textPaint.setColor(0xCCFFFFFF);
        textPaint.setTextSize(10 * density);
    }

    public void setData(ProfilingSession.Result result) {
        this.samples = result == null ? null : result.fpsTimeline;
        this.avgFps = result == null ? Float.NaN : result.avgFps;
        this.low1Fps = result == null ? Float.NaN : result.low1Fps;
        this.minFps = result == null ? Float.NaN : result.minFps;
        this.maxFps = result == null ? Float.NaN : result.maxFps;
        invalidate();
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        float density = getResources().getDisplayMetrics().density;
        int w = getWidth();
        int h = getHeight();
        if (w <= 0 || h <= 0) return;

        float padL = 32 * density;
        float padR = 8 * density;
        float padT = 8 * density;
        float padB = 16 * density;
        float plotW = w - padL - padR;
        float plotH = h - padT - padB;

        if (samples == null || samples.size() < 2 || Float.isNaN(maxFps) || maxFps <= 0f) {
            textPaint.setColor(0x99FFFFFF);
            canvas.drawText("—", w / 2f - 8 * density, h / 2f, textPaint);
            return;
        }

        // Y-axis range: 0 .. ceiling above max (to nearest 30 or 60)
        float yTop = Math.max(60f, (float) (Math.ceil(maxFps / 30.0) * 30.0));
        float yBot = 0f;

        // Grid lines at 30/60/120 fps
        for (float gridFps : new float[]{30f, 60f, 90f, 120f, 144f, 240f}) {
            if (gridFps > yTop) break;
            float y = padT + plotH - (gridFps - yBot) / (yTop - yBot) * plotH;
            canvas.drawLine(padL, y, padL + plotW, y, axisPaint);
            textPaint.setColor(0xCCFFFFFF);
            canvas.drawText(String.format(Locale.ENGLISH, "%d", (int) gridFps),
                    4 * density, y + 4 * density, textPaint);
        }

        // Build line + fill paths.
        linePath.reset();
        fillPath.reset();
        int n = samples.size();
        for (int i = 0; i < n; i++) {
            float v = Math.max(0f, samples.get(i));
            float x = padL + (i / (float) (n - 1)) * plotW;
            float y = padT + plotH - (v - yBot) / (yTop - yBot) * plotH;
            if (i == 0) {
                linePath.moveTo(x, y);
                fillPath.moveTo(x, padT + plotH);
                fillPath.lineTo(x, y);
            } else {
                linePath.lineTo(x, y);
                fillPath.lineTo(x, y);
            }
        }
        fillPath.lineTo(padL + plotW, padT + plotH);
        fillPath.close();

        fillPaint.setShader(new LinearGradient(0, padT, 0, padT + plotH,
                0x664CAF50, 0x114CAF50, Shader.TileMode.CLAMP));
        canvas.drawPath(fillPath, fillPaint);
        canvas.drawPath(linePath, linePaint);

        // Marker lines: avg (cyan), 1% low (orange)
        if (!Float.isNaN(avgFps)) {
            float y = padT + plotH - (avgFps - yBot) / (yTop - yBot) * plotH;
            markerPaint.setColor(0xFF03DAC5);
            canvas.drawLine(padL, y, padL + plotW, y, markerPaint);
            textPaint.setColor(0xFF03DAC5);
            canvas.drawText("avg " + String.format(Locale.ENGLISH, "%.1f", avgFps),
                    padL + plotW - 60 * density, y - 2 * density, textPaint);
        }
        if (!Float.isNaN(low1Fps)) {
            float y = padT + plotH - (low1Fps - yBot) / (yTop - yBot) * plotH;
            markerPaint.setColor(0xFFFF9800);
            canvas.drawLine(padL, y, padL + plotW, y, markerPaint);
            textPaint.setColor(0xFFFF9800);
            canvas.drawText("1% " + String.format(Locale.ENGLISH, "%.1f", low1Fps),
                    padL + plotW - 60 * density, y + 12 * density, textPaint);
        }
    }
}

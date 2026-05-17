package com.winlator.cmod.widget;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.util.AttributeSet;
import android.view.View;

import androidx.annotation.Nullable;

public class JoystickView extends View {
    private Paint circlePaint;
    private Paint crosshairPaint;
    private Paint dotPaint;
    private float posX = 0.5f; // Center (0.0 to 1.0)
    private float posY = 0.5f; // Center (0.0 to 1.0)
    private int circleColor = 0xFF444444;
    private int dotColor = 0xFF4CAF50;

    public JoystickView(Context context) {
        super(context);
        init();
    }

    public JoystickView(Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
        init();
    }

    public JoystickView(Context context, @Nullable AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        init();
    }

    private void init() {
        circlePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        circlePaint.setColor(circleColor);
        circlePaint.setStyle(Paint.Style.STROKE);
        circlePaint.setStrokeWidth(4f);

        crosshairPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        crosshairPaint.setColor(0xFF666666);
        crosshairPaint.setStyle(Paint.Style.STROKE);
        crosshairPaint.setStrokeWidth(2f);

        dotPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        dotPaint.setColor(dotColor);
        dotPaint.setStyle(Paint.Style.FILL);
    }

    public void setPosition(float x, float y) {
        // Convert from -1.0 to 1.0 range to 0.0 to 1.0 range
        this.posX = (x + 1.0f) / 2.0f;
        this.posY = (y + 1.0f) / 2.0f;
        invalidate();
    }

    public void setDotColor(int color) {
        this.dotColor = color;
        dotPaint.setColor(color);
        invalidate();
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);

        int width = getWidth();
        int height = getHeight();
        int size = Math.min(width, height);
        float centerX = width / 2f;
        float centerY = height / 2f;
        float radius = size / 2f - 20f;

        // Draw outer circle
        canvas.drawCircle(centerX, centerY, radius, circlePaint);

        // Draw crosshair
        canvas.drawLine(centerX - radius, centerY, centerX + radius, centerY, crosshairPaint);
        canvas.drawLine(centerX, centerY - radius, centerX, centerY + radius, crosshairPaint);

        // Draw center dot
        canvas.drawCircle(centerX, centerY, 4f, crosshairPaint);

        // Draw position indicator
        float dotX = centerX + (posX - 0.5f) * 2f * radius;
        float dotY = centerY + (posY - 0.5f) * 2f * radius;
        canvas.drawCircle(dotX, dotY, 12f, dotPaint);
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        int size = 200; // Default size
        setMeasuredDimension(size, size);
    }
}

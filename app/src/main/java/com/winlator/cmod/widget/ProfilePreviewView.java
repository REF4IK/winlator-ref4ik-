package com.winlator.cmod.widget;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.util.AttributeSet;
import android.view.View;

import com.winlator.cmod.R;
import com.winlator.cmod.inputcontrols.ControlsProfile;

public class ProfilePreviewView extends View {
    private ControlsProfile profile;
    private InputControlsView offscreenView;
    private Paint textPaint;
    private static final int PREVIEW_WIDTH = 1080;
    private static final int PREVIEW_HEIGHT = 540;

    public ProfilePreviewView(Context context) {
        super(context);
        init(context);
    }

    public ProfilePreviewView(Context context, AttributeSet attrs) {
        super(context, attrs);
        init(context);
    }

    public ProfilePreviewView(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        init(context);
    }

    private void init(Context context) {
        textPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        textPaint.setStyle(Paint.Style.FILL);
        textPaint.setTextAlign(Paint.Align.CENTER);
        textPaint.setTextSize(16f);

        offscreenView = new InputControlsView(context);
        offscreenView.setEditMode(false);
        offscreenView.setShowTouchscreenControls(true);
        offscreenView.setOverlayOpacity(0.6f);

        int widthSpec = View.MeasureSpec.makeMeasureSpec(PREVIEW_WIDTH, View.MeasureSpec.EXACTLY);
        int heightSpec = View.MeasureSpec.makeMeasureSpec(PREVIEW_HEIGHT, View.MeasureSpec.EXACTLY);
        offscreenView.measure(widthSpec, heightSpec);
        offscreenView.layout(0, 0, PREVIEW_WIDTH, PREVIEW_HEIGHT);
    }

    public void setProfile(ControlsProfile profile) {
        this.profile = profile;
        offscreenView.setProfile(profile);
        invalidate();
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);

        canvas.drawColor(0xFF1a1a1a);

        if (profile == null) {
            drawEmptyState(canvas);
            return;
        }

        float scaleX = (float) getWidth() / PREVIEW_WIDTH;
        float scaleY = (float) getHeight() / PREVIEW_HEIGHT;
        float scale = Math.min(scaleX, scaleY);

        float dx = (getWidth() - PREVIEW_WIDTH * scale) / 2f;
        float dy = (getHeight() - PREVIEW_HEIGHT * scale) / 2f;

        canvas.save();
        canvas.translate(dx, dy);
        canvas.scale(scale, scale);

        offscreenView.draw(canvas);

        canvas.restore();
    }

    private void drawEmptyState(Canvas canvas) {
        textPaint.setColor(0xFF999999);
        String emptyText = getContext().getString(R.string.no_items_to_display);
        canvas.drawText(emptyText, getWidth() / 2f, getHeight() / 2f, textPaint);
    }
}
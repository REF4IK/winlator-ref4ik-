package com.winlator.cmod.inputcontrols;

import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.PointF;
import com.winlator.cmod.inputcontrols.IconPackManager;
import android.graphics.Rect;
import android.graphics.RectF;

import androidx.core.graphics.ColorUtils;

import com.winlator.cmod.core.CubicBezierInterpolator;
import com.winlator.cmod.math.Mathf;
import com.winlator.cmod.widget.InputControlsView;
import com.winlator.cmod.widget.TouchpadView;
import com.winlator.cmod.winhandler.MouseEventFlags;
import com.winlator.cmod.xserver.XServer;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.Arrays;

public class ControlElement {
    public static final float STICK_DEAD_ZONE = 0.15f;
    public static final float DPAD_DEAD_ZONE = 0.3f;
    public static final float STICK_SENSITIVITY = 3.0f;
    public static final float TRACKPAD_MIN_SPEED = 0.8f;
    public static final float TRACKPAD_MAX_SPEED = 20.0f;
    public static final byte TRACKPAD_ACCELERATION_THRESHOLD = 4;
    public static final short BUTTON_MIN_TIME_TO_KEEP_PRESSED = 300;
    public enum Type {
        BUTTON, COMBO_BUTTON, D_PAD, RANGE_BUTTON, STICK, TRACKPAD, STEERING_WHEEL;

        public static String[] names() {
            Type[] types = values();
            String[] names = new String[types.length];
            for (int i = 0; i < types.length; i++) names[i] = types[i].name().replace("_", "-");
            return names;
        }
    }
    public enum Shape {
        CIRCLE, RECT, ROUND_RECT, SQUARE;

        public static String[] names() {
            Shape[] shapes = values();
            String[] names = new String[shapes.length];
            for (int i = 0; i < shapes.length; i++) names[i] = shapes[i].name().replace("_", " ");
            return names;
        }
    }
    public enum Range {
        FROM_A_TO_Z(26), FROM_0_TO_9(10), FROM_F1_TO_F12(12), FROM_NP0_TO_NP9(10);
        public final byte max;

        Range(int max) {
            this.max = (byte)max;
        }

        public static String[] names() {
            Range[] ranges = values();
            String[] names = new String[ranges.length];
            for (int i = 0; i < ranges.length; i++) names[i] = ranges[i].name().replace("_", " ");
            return names;
        }
    }
    private final InputControlsView inputControlsView;
    private Type type = Type.BUTTON;
    private Shape shape = Shape.CIRCLE;
    private Binding[] bindings = {Binding.NONE, Binding.NONE, Binding.NONE, Binding.NONE};
    private float scale = 1.0f;
    private short x;
    private short y;
    private boolean selected = false;
    private boolean toggleSwitch = false;
    private boolean hideBorder = false;
    private int currentPointerId = -1;
    private final Rect boundingBox = new Rect();
    private boolean[] states = new boolean[4];
    private boolean boundingBoxNeedsUpdate = true;
    private String text = "";
    private int iconId;
    private Range range;
    private byte orientation;
    private PointF currentPosition;
    private RangeScroller scroller;
    private CubicBezierInterpolator interpolator;
    private Object touchTime;
    private float iconScale = 1.0f; // Новое поле для размера иконки
    private float opacity = 0.5f; // Прозрачность кнопки (0.1 - 1.0)

    private float fillOpacity = 0.12f;
    private float rotation = 0.0f;
    private int borderColor = Color.TRANSPARENT;
    private int fillColor = Color.TRANSPARENT;
    private int textColor = Color.TRANSPARENT;

    public ControlElement(InputControlsView inputControlsView) {
        this.inputControlsView = inputControlsView;
    }

    private void reset() {
        setBinding(Binding.NONE);
        scroller = null;

        if (type == Type.D_PAD || type == Type.STICK) {
            bindings[0] = Binding.KEY_W;
            bindings[1] = Binding.KEY_D;
            bindings[2] = Binding.KEY_S;
            bindings[3] = Binding.KEY_A;
        }
        else if (type == Type.TRACKPAD) {
            bindings[0] = Binding.MOUSE_MOVE_UP;
            bindings[1] = Binding.MOUSE_MOVE_RIGHT;
            bindings[2] = Binding.MOUSE_MOVE_DOWN;
            bindings[3] = Binding.MOUSE_MOVE_LEFT;
        }
        else if (type == Type.STEERING_WHEEL) {
            bindings[0] = Binding.NONE;  // Up (not used)
            bindings[1] = Binding.KEY_D; // Right
            bindings[2] = Binding.NONE;  // Down (not used)
            bindings[3] = Binding.KEY_A; // Left
        }
        else if (type == Type.COMBO_BUTTON) {
            setBindingCount(Math.max(3, bindings.length));
        }
        else if (type == Type.RANGE_BUTTON) {
            scroller = new RangeScroller(inputControlsView, this);
        }

        text = "";
        iconId = 0;
        range = null;
        boundingBoxNeedsUpdate = true;
    }

    public Type getType() {
        return type;
    }

    public void setType(Type type) {
        this.type = type;
        reset();
    }

    public int getBindingCount() {
        return bindings.length;
    }

    public void setBindingCount(int bindingCount) {
        bindings = new Binding[bindingCount];
        setBinding(Binding.NONE);
        states = new boolean[bindingCount];
        boundingBoxNeedsUpdate = true;
    }

    public Shape getShape() {
        return shape;
    }

    public void setShape(Shape shape) {
        this.shape = shape;
        boundingBoxNeedsUpdate = true;
    }

    public Range getRange() {
        return range != null ? range : Range.FROM_A_TO_Z;
    }

    public void setRange(Range range) {
        this.range = range;
    }

    public byte getOrientation() {
        return orientation;
    }

    public void setOrientation(byte orientation) {
        this.orientation = orientation;
        boundingBoxNeedsUpdate = true;
    }

    public boolean isToggleSwitch() {
        return toggleSwitch;
    }

    public void setToggleSwitch(boolean toggleSwitch) {
        this.toggleSwitch = toggleSwitch;
    }

    public boolean isHideBorder() {
        return hideBorder;
    }

    public void setHideBorder(boolean hideBorder) {
        this.hideBorder = hideBorder;
    }

    public Binding getBindingAt(int index) {
        return index < bindings.length ? bindings[index] : Binding.NONE;
    }

    public void setBindingAt(int index, Binding binding) {
        if (index >= bindings.length) {
            int oldLength = bindings.length;
            bindings = Arrays.copyOf(bindings, index+1);
            Arrays.fill(bindings, oldLength-1, bindings.length, Binding.NONE);
            states = new boolean[bindings.length];
            boundingBoxNeedsUpdate = true;
        }
        bindings[index] = binding;
    }

    public void setBinding(Binding binding) {
        Arrays.fill(bindings, binding);
    }

    public float getScale() {
        return scale;
    }

    public void setScale(float scale) {
        this.scale = scale;
        boundingBoxNeedsUpdate = true;
    }

    public short getX() {
        return x;
    }

    public void setX(int x) {
        this.x = (short)x;
        boundingBoxNeedsUpdate = true;
    }

    public short getY() {
        return y;
    }

    public void setY(int y) {
        this.y = (short)y;
        boundingBoxNeedsUpdate = true;
    }

    public boolean isSelected() {
        return selected;
    }

    public void setSelected(boolean selected) {
        this.selected = selected;
    }

    public String getText() {
        return text;
    }

    public void setText(String text) {
        this.text = text != null ? text : "";
    }

    public int getIconId() {
        return iconId;
    }

    public void setIconId(int iconId) {
        this.iconId = iconId;
    }

    public float getIconScale() {
        return iconScale;
    }

    public void setIconScale(float iconScale) {
        this.iconScale = iconScale;
    }

    public float getOpacity() {
        return opacity;
    }

    public void setOpacity(float opacity) {
        this.opacity = opacity;
    }

    public float getFillOpacity() {
        return fillOpacity;
    }

    public void setFillOpacity(float fillOpacity) {
        this.fillOpacity = Mathf.clamp(fillOpacity, 0.0f, 1.0f);
    }

    public float getRotation() {
        return rotation;
    }

    public void setRotation(float rotation) {
        this.rotation = ((rotation % 360.0f) + 360.0f) % 360.0f;
    }

    public int getBorderColor() {
        return borderColor;
    }

    public void setBorderColor(int borderColor) {
        this.borderColor = borderColor;
    }

    public int getFillColor() {
        return fillColor;
    }

    public void setFillColor(int fillColor) {
        this.fillColor = fillColor;
    }

    public int getTextColor() {
        return textColor;
    }

    public void setTextColor(int textColor) {
        this.textColor = textColor;
    }

    public Rect getBoundingBox() {
        if (boundingBoxNeedsUpdate) computeBoundingBox();
        return boundingBox;
    }

    private Rect computeBoundingBox() {
        int snappingSize = inputControlsView.getSnappingSize();
        int halfWidth = 0;
        int halfHeight = 0;

        switch (type) {
            case BUTTON:
            case COMBO_BUTTON:
                switch (shape) {
                    case RECT:
                    case ROUND_RECT:
                        halfWidth = snappingSize * (type == Type.COMBO_BUTTON ? 7 : 4);
                        halfHeight = snappingSize * 2;
                        break;
                    case SQUARE:
                        halfWidth = (int)(snappingSize * 2.5f);
                        halfHeight = (int)(snappingSize * 2.5f);
                        break;
                    case CIRCLE:
                        halfWidth = snappingSize * 3;
                        halfHeight = snappingSize * 3;
                        break;
                }
                break;
            case D_PAD: {
                halfWidth = snappingSize * 7;
                halfHeight = snappingSize * 7;
                break;
            }
            case TRACKPAD:
            case STEERING_WHEEL:
            case STICK: {
                halfWidth = snappingSize * 6;
                halfHeight = snappingSize * 6;
                break;
            }
            case RANGE_BUTTON: {
                halfWidth = snappingSize * ((bindings.length * 4) / 2);
                halfHeight = snappingSize * 2;

                if (orientation == 1) {
                    int tmp = halfWidth;
                    halfWidth = halfHeight;
                    halfHeight = tmp;
                }
                break;
            }
        }

        halfWidth *= scale;
        halfHeight *= scale;
        boundingBox.set(x - halfWidth, y - halfHeight, x + halfWidth, y + halfHeight);
        boundingBoxNeedsUpdate = false;
        return boundingBox;
    }



    private String getDisplayText() {
        if (text != null && !text.isEmpty()) {
            return text;
        }
        else {
            Binding binding = getBindingAt(0);
            String text = binding.toString().replace("NUMPAD ", "NP").replace("BUTTON ", "");
            if (text.length() > 7) {
                String[] parts = text.split(" ");
                StringBuilder sb = new StringBuilder();
                for (String part : parts) sb.append(part.charAt(0));
                return (binding.isMouse() ? "M" : "")+ sb;
            }
            else return text;
        }
    }

    private static float getTextSizeForWidth(Paint paint, String text, float desiredWidth) {
        final byte testTextSize = 48;
        paint.setTextSize(testTextSize);
        return testTextSize * desiredWidth / paint.measureText(text);
    }

    private static String getRangeTextForIndex(Range range, int index) {
        String text = "";
        switch (range) {
            case FROM_A_TO_Z:
                text = String.valueOf((char)(65 + index));
                break;
            case FROM_0_TO_9:
                text = String.valueOf((index + 1) % 10);
                break;
            case FROM_F1_TO_F12:
                text = "F"+(index + 1);
                break;
            case FROM_NP0_TO_NP9:
                text = "NP"+((index + 1) % 10);
                break;
        }
        return text;
    }

    public void draw(Canvas canvas) {
        int snappingSize = inputControlsView.getSnappingSize();
        Paint paint = inputControlsView.getPaint();
        int primaryColor = textColor != Color.TRANSPARENT ? ColorUtils.setAlphaComponent(textColor, (int)(opacity * 255)) : inputControlsView.getPrimaryColor();
        int strokeColor = borderColor != Color.TRANSPARENT ? ColorUtils.setAlphaComponent(borderColor, (int)(opacity * 255)) : (selected ? inputControlsView.getSecondaryColor() : inputControlsView.getPrimaryColor());
        int currentFillColor = fillColor != Color.TRANSPARENT ? fillColor : Color.WHITE;

        paint.setColor(strokeColor);
        paint.setAlpha((int)(opacity * 255)); // Применяем прозрачность
        paint.setStyle(Paint.Style.STROKE);
        float strokeWidth = snappingSize * 0.25f;
        paint.setStrokeWidth(strokeWidth);
        Rect boundingBox = getBoundingBox();
        canvas.save();
        canvas.rotate(rotation, x, y);

        switch (type) {
            case BUTTON: {
                float cx = boundingBox.centerX();
                float cy = boundingBox.centerY();
                drawButtonFill(canvas, paint, boundingBox, currentFillColor, fillOpacity, shape, scale);

                // Рисовать границу только если hideBorder = false
                if (!hideBorder) {
                    switch (shape) {
                        case CIRCLE:
                            canvas.drawCircle(cx, cy, boundingBox.width() * 0.5f, paint);
                            break;
                        case RECT:
                            canvas.drawRect(boundingBox, paint);
                            break;
                        case ROUND_RECT: {
                            float radius = boundingBox.height() * 0.5f;
                            canvas.drawRoundRect(boundingBox.left, boundingBox.top, boundingBox.right, boundingBox.bottom, radius, radius, paint);
                            break;
                        }
                        case SQUARE: {
                            float radius = snappingSize * 0.75f * scale;
                            canvas.drawRoundRect(boundingBox.left, boundingBox.top, boundingBox.right, boundingBox.bottom, radius, radius, paint);
                            break;
                        }
                    }
                }

                if (iconId > 0) {
                    drawIcon(canvas, cx, cy, boundingBox.width(), boundingBox.height(), iconId);
                }
                else {
                    String text = getDisplayText();
                    paint.setTextSize(Math.min(getTextSizeForWidth(paint, text, boundingBox.width() - strokeWidth * 2), snappingSize * 2 * scale));
                    paint.setTextAlign(Paint.Align.CENTER);
                    paint.setStyle(Paint.Style.FILL);
                    paint.setColor(primaryColor);
                    canvas.drawText(text, x, (y - ((paint.descent() + paint.ascent()) * 0.5f)), paint);
                }
                break;
            }
            case COMBO_BUTTON: {
                float radius = snappingSize * 0.75f * scale;
                drawButtonFill(canvas, paint, boundingBox, currentFillColor, fillOpacity, Shape.ROUND_RECT, scale);
                if (!hideBorder) canvas.drawRoundRect(boundingBox.left, boundingBox.top, boundingBox.right, boundingBox.bottom, radius, radius, paint);

                float segmentWidth = boundingBox.width() / (float)Math.max(1, bindings.length);
                for (int i = 1; i < bindings.length; i++) {
                    float lineX = boundingBox.left + segmentWidth * i;
                    canvas.drawLine(lineX, boundingBox.top + strokeWidth, lineX, boundingBox.bottom - strokeWidth, paint);
                }

                paint.setStyle(Paint.Style.FILL);
                paint.setColor(primaryColor);
                paint.setTextAlign(Paint.Align.CENTER);
                for (int i = 0; i < bindings.length; i++) {
                    String itemText = getBindingAt(i).toString().replace("BUTTON ", "");
                    paint.setTextSize(Math.min(getTextSizeForWidth(paint, itemText, segmentWidth - strokeWidth * 2), snappingSize * 1.6f * scale));
                    float centerX = boundingBox.left + segmentWidth * i + segmentWidth * 0.5f;
                    canvas.drawText(itemText, centerX, y - ((paint.descent() + paint.ascent()) * 0.5f), paint);
                    if (i < bindings.length - 1) {
                        paint.setTextSize(snappingSize * 1.2f * scale);
                        canvas.drawText("+", boundingBox.left + segmentWidth * (i + 1), y - ((paint.descent() + paint.ascent()) * 0.5f), paint);
                    }
                }
                break;
            }
            case D_PAD: {
                float cx = boundingBox.centerX();
                float cy = boundingBox.centerY();
                float offsetX = snappingSize * 2 * scale;
                float offsetY = snappingSize * 3 * scale;
                float start = snappingSize * scale;
                Path path = inputControlsView.getPath();
                path.reset();

                path.moveTo(cx, cy - start);
                path.lineTo(cx - offsetX, cy - offsetY);
                path.lineTo(cx - offsetX, boundingBox.top);
                path.lineTo(cx + offsetX, boundingBox.top);
                path.lineTo(cx + offsetX, cy - offsetY);
                path.close();

                path.moveTo(cx - start, cy);
                path.lineTo(cx - offsetY, cy - offsetX);
                path.lineTo(boundingBox.left, cy - offsetX);
                path.lineTo(boundingBox.left, cy + offsetX);
                path.lineTo(cx - offsetY, cy + offsetX);
                path.close();

                path.moveTo(cx, cy + start);
                path.lineTo(cx - offsetX, cy + offsetY);
                path.lineTo(cx - offsetX, boundingBox.bottom);
                path.lineTo(cx + offsetX, boundingBox.bottom);
                path.lineTo(cx + offsetX, cy + offsetY);
                path.close();

                path.moveTo(cx + start, cy);
                path.lineTo(cx + offsetY, cy - offsetX);
                path.lineTo(boundingBox.right, cy - offsetX);
                path.lineTo(boundingBox.right, cy + offsetX);
                path.lineTo(cx + offsetY, cy + offsetX);
                path.close();

                canvas.drawPath(path, paint);
                break;
            }
            case RANGE_BUTTON: {
                Range range = getRange();
                int oldColor = paint.getColor();
                float radius = snappingSize * 0.75f * scale;
                float elementSize = scroller.getElementSize();
                float minTextSize = snappingSize * 2 * scale;
                float scrollOffset = scroller.getScrollOffset();
                byte[] rangeIndex = scroller.getRangeIndex();
                Path path = inputControlsView.getPath();
                path.reset();

                if (orientation == 0) {
                    float lineTop = boundingBox.top + strokeWidth * 0.5f;
                    float lineBottom = boundingBox.bottom - strokeWidth * 0.5f;
                    float startX = boundingBox.left;
                    canvas.drawRoundRect(startX, boundingBox.top, boundingBox.right, boundingBox.bottom, radius, radius, paint);

                    canvas.save();
                    path.addRoundRect(startX, boundingBox.top, boundingBox.right, boundingBox.bottom, radius, radius, Path.Direction.CW);
                    canvas.clipPath(path);
                    startX -= scrollOffset % elementSize;

                    for (byte i = rangeIndex[0]; i < rangeIndex[1]; i++) {
                        int index = i % range.max;
                        paint.setStyle(Paint.Style.STROKE);
                        paint.setColor(oldColor);

                        if (startX > boundingBox.left && startX  < boundingBox.right) canvas.drawLine(startX, lineTop, startX, lineBottom, paint);
                        String text = getRangeTextForIndex(range, index);

                        if (startX < boundingBox.right && startX + elementSize > boundingBox.left) {
                            paint.setStyle(Paint.Style.FILL);
                            paint.setColor(primaryColor);
                            paint.setTextSize(Math.min(getTextSizeForWidth(paint, text, elementSize - strokeWidth * 2), minTextSize));
                            paint.setTextAlign(Paint.Align.CENTER);
                            canvas.drawText(text, startX + elementSize * 0.5f, (y - ((paint.descent() + paint.ascent()) * 0.5f)), paint);
                        }
                        startX += elementSize;
                    }

                    paint.setStyle(Paint.Style.STROKE);
                    paint.setColor(oldColor);
                    canvas.restore();
                }
                else {
                    float lineLeft = boundingBox.left + strokeWidth * 0.5f;
                    float lineRight = boundingBox.right - strokeWidth * 0.5f;
                    float startY = boundingBox.top;
                    canvas.drawRoundRect(boundingBox.left, startY, boundingBox.right, boundingBox.bottom, radius, radius, paint);

                    canvas.save();
                    path.addRoundRect(boundingBox.left, startY, boundingBox.right, boundingBox.bottom, radius, radius, Path.Direction.CW);
                    canvas.clipPath(inputControlsView.getPath());
                    startY -= scrollOffset % elementSize;

                    for (byte i = rangeIndex[0]; i < rangeIndex[1]; i++) {
                        paint.setStyle(Paint.Style.STROKE);
                        paint.setColor(oldColor);

                        if (startY > boundingBox.top && startY < boundingBox.bottom) canvas.drawLine(lineLeft, startY, lineRight, startY, paint);
                        String text = getRangeTextForIndex(range, i);

                        if (startY < boundingBox.bottom && startY + elementSize > boundingBox.top) {
                            paint.setStyle(Paint.Style.FILL);
                            paint.setColor(primaryColor);
                            paint.setTextSize(Math.min(getTextSizeForWidth(paint, text, boundingBox.width() - strokeWidth * 2), minTextSize));
                            paint.setTextAlign(Paint.Align.CENTER);
                            canvas.drawText(text, x, startY + elementSize * 0.5f - ((paint.descent() + paint.ascent()) * 0.5f), paint);
                        }
                        startY += elementSize;
                    }

                    paint.setStyle(Paint.Style.STROKE);
                    paint.setColor(oldColor);
                    canvas.restore();
                }
                break;
            }
            case STICK: {
                int cx = boundingBox.centerX();  // Fixed outer circle center
                int cy = boundingBox.centerY();  // Fixed outer circle center
                int oldColor = paint.getColor();

                // Draw the outer circle (base of the stick)
                canvas.drawCircle(cx, cy, boundingBox.height() * 0.5f, paint);

                // Draw the inner thumbstick (current position based on gyroscope movement)
                float thumbstickX = getCurrentPosition().x;
                float thumbstickY = getCurrentPosition().y;

                short thumbRadius = (short) (snappingSize * 3.5f * scale); // Radius of the thumbstick
                paint.setStyle(Paint.Style.FILL);
                paint.setColor(ColorUtils.setAlphaComponent(primaryColor, 50)); // Semi-transparent fill for thumbstick
                canvas.drawCircle(thumbstickX, thumbstickY, thumbRadius, paint); // Draw thumbstick

                // Draw the thumbstick border
                paint.setStyle(Paint.Style.STROKE);
                paint.setColor(oldColor);
                canvas.drawCircle(thumbstickX, thumbstickY, thumbRadius + strokeWidth * 0.5f, paint);
                break;
            }

            case TRACKPAD: {
                float radius = boundingBox.height() * 0.15f;
                canvas.drawRoundRect(boundingBox.left, boundingBox.top, boundingBox.right, boundingBox.bottom, radius, radius, paint);
                float offset = strokeWidth * 2.5f;
                float innerStrokeWidth = strokeWidth * 2;
                float innerHeight = boundingBox.height() - offset * 2;
                radius = (innerHeight / boundingBox.height()) * radius - (innerStrokeWidth * 0.5f + strokeWidth * 0.5f);
                paint.setStrokeWidth(innerStrokeWidth);
                canvas.drawRoundRect(boundingBox.left + offset, boundingBox.top + offset, boundingBox.right - offset, boundingBox.bottom - offset, radius, radius, paint);
                break;
            }

            case STEERING_WHEEL: {
                float cx = boundingBox.centerX();
                float cy = boundingBox.centerY();
                float radius = boundingBox.width() * 0.4f;
                int oldColor = paint.getColor();

                // Draw outer circle (steering wheel rim)
                canvas.drawCircle(cx, cy, radius, paint);

                // Get current rotation angle from touch position
                float rotationAngle = 0f;
                if (currentPosition != null) {
                    float dx = currentPosition.x - cx;
                    float dy = currentPosition.y - cy;
                    rotationAngle = (float) Math.atan2(dy, dx);
                }

                // Draw steering wheel spokes (3 spokes at 120 degree intervals)
                for (int i = 0; i < 3; i++) {
                    float spokeAngle = rotationAngle + (i * 2.0f * (float) Math.PI / 3.0f);
                    float spokeStartX = cx + (float) Math.cos(spokeAngle) * radius * 0.3f;
                    float spokeStartY = cy + (float) Math.sin(spokeAngle) * radius * 0.3f;
                    float spokeEndX = cx + (float) Math.cos(spokeAngle) * radius * 0.9f;
                    float spokeEndY = cy + (float) Math.sin(spokeAngle) * radius * 0.9f;
                    canvas.drawLine(spokeStartX, spokeStartY, spokeEndX, spokeEndY, paint);
                }

                // Draw center hub
                paint.setStyle(Paint.Style.FILL);
                paint.setColor(ColorUtils.setAlphaComponent(primaryColor, 100));
                canvas.drawCircle(cx, cy, radius * 0.2f, paint);

                // Draw center hub border
                paint.setStyle(Paint.Style.STROKE);
                paint.setColor(oldColor);
                canvas.drawCircle(cx, cy, radius * 0.2f, paint);

                // Draw rotation indicator (small dot on the rim)
                float indicatorX = cx + (float) Math.cos(rotationAngle) * radius * 0.8f;
                float indicatorY = cy + (float) Math.sin(rotationAngle) * radius * 0.8f;
                paint.setStyle(Paint.Style.FILL);
                paint.setColor(inputControlsView.getSecondaryColor());
                canvas.drawCircle(indicatorX, indicatorY, strokeWidth * 2, paint);
                break;
            }
        }
        if (selected && inputControlsView.isEditMode()) drawSelectionOverlay(canvas, paint, boundingBox);
        canvas.restore();
    }

    private void drawButtonFill(Canvas canvas, Paint paint, Rect boundingBox, int fillColor, float fillOpacity, Shape shape, float scale) {
        if (fillOpacity <= 0.0f) return;

        int oldColor = paint.getColor();
        Paint.Style oldStyle = paint.getStyle();
        float radius = shape == Shape.ROUND_RECT ? boundingBox.height() * 0.5f : inputControlsView.getSnappingSize() * 0.75f * scale;

        paint.setStyle(Paint.Style.FILL);
        paint.setColor(ColorUtils.setAlphaComponent(fillColor, (int)(fillOpacity * opacity * 255)));
        switch (shape) {
            case CIRCLE:
                canvas.drawCircle(boundingBox.centerX(), boundingBox.centerY(), boundingBox.width() * 0.5f, paint);
                break;
            case RECT:
                canvas.drawRect(boundingBox, paint);
                break;
            case ROUND_RECT:
            case SQUARE:
                canvas.drawRoundRect(boundingBox.left, boundingBox.top, boundingBox.right, boundingBox.bottom, radius, radius, paint);
                break;
        }

        paint.setColor(oldColor);
        paint.setStyle(oldStyle);
    }

    private void drawSelectionOverlay(Canvas canvas, Paint paint, Rect boundingBox) {
        int snappingSize = inputControlsView.getSnappingSize();
        float handleRadius = snappingSize * 1.2f;

        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeWidth(snappingSize * 0.18f);
        paint.setColor(inputControlsView.getSecondaryColor());
        canvas.drawRoundRect(boundingBox.left, boundingBox.top, boundingBox.right, boundingBox.bottom, snappingSize, snappingSize, paint);

        paint.setStyle(Paint.Style.FILL);
        paint.setColor(ColorUtils.setAlphaComponent(inputControlsView.getSecondaryColor(), 230));
        RectF resize = getResizeHandleBounds();
        RectF rotate = getRotateHandleBounds();
        canvas.drawOval(resize, paint);
        canvas.drawOval(rotate, paint);

        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeWidth(snappingSize * 0.16f);
        paint.setColor(Color.WHITE);
        canvas.drawLine(resize.left + handleRadius * 0.55f, resize.bottom - handleRadius * 0.45f, resize.right - handleRadius * 0.45f, resize.top + handleRadius * 0.55f, paint);
        canvas.drawCircle(rotate.centerX(), rotate.centerY(), handleRadius * 0.45f, paint);
    }

    public RectF getResizeHandleBounds() {
        Rect box = getBoundingBox();
        float r = inputControlsView.getSnappingSize() * 1.2f;
        return new RectF(box.right - r, box.bottom - r, box.right + r, box.bottom + r);
    }

    public RectF getRotateHandleBounds() {
        Rect box = getBoundingBox();
        float r = inputControlsView.getSnappingSize() * 1.2f;
        return new RectF(box.centerX() - r, box.top - r * 3.0f, box.centerX() + r, box.top - r);
    }

    public boolean containsResizeHandle(float screenX, float screenY) {
        return getRotatedHandleBounds(getResizeHandleBounds()).contains(screenX, screenY);
    }

    public boolean containsRotateHandle(float screenX, float screenY) {
        return getRotatedHandleBounds(getRotateHandleBounds()).contains(screenX, screenY);
    }

    private RectF getRotatedHandleBounds(RectF localBounds) {
        float cx = localBounds.centerX();
        float cy = localBounds.centerY();
        if (rotation != 0.0f) {
            double radians = Math.toRadians(rotation);
            float dx = cx - x;
            float dy = cy - y;
            cx = x + (float)(dx * Math.cos(radians) - dy * Math.sin(radians));
            cy = y + (float)(dx * Math.sin(radians) + dy * Math.cos(radians));
        }

        float touchRadius = Math.max(localBounds.width(), inputControlsView.getSnappingSize() * 4.0f) * 0.5f;
        return new RectF(cx - touchRadius, cy - touchRadius, cx + touchRadius, cy + touchRadius);
    }

    private void drawIcon(Canvas canvas, float cx, float cy, float width, float height, int iconId) {
        Paint paint = inputControlsView.getPaint();
        Bitmap icon = inputControlsView.getIcon(iconId);
        
        // Only apply color filter to built-in icons, not custom or pack icons
        boolean isCustomOrPack = CustomIconManager.isCustomIcon(iconId) || IconPackManager.isPackIcon(iconId);
        if (!isCustomOrPack) {
            paint.setColorFilter(inputControlsView.getColorFilter());
        }
        
        int margin = (int)(inputControlsView.getSnappingSize() * (shape == Shape.CIRCLE || shape == Shape.SQUARE ? 2.0f : 1.0f) * scale);
        int halfSize = (int)((Math.min(width, height) - margin) * 0.5f);

        if (icon != null) {
            Rect srcRect = new Rect(0, 0, icon.getWidth(), icon.getHeight());
            int scaledHalfSize = (int)(halfSize * this.iconScale);
            Rect dstRect = new Rect((int)(cx - scaledHalfSize), (int)(cy - scaledHalfSize), (int)(cx + scaledHalfSize), (int)(cy + scaledHalfSize));
            canvas.drawBitmap(icon, srcRect, dstRect, paint);
        }
        
        paint.setColorFilter(null);
    }

    public JSONObject toJSONObject() {
        try {
            JSONObject elementJSONObject = new JSONObject();
            elementJSONObject.put("type", type.name());
            elementJSONObject.put("shape", shape.name());

            JSONArray bindingsJSONArray = new JSONArray();
            for (Binding binding : bindings) bindingsJSONArray.put(binding.name());
            elementJSONObject.put("bindings", bindingsJSONArray);

            elementJSONObject.put("scale", Float.valueOf(scale));
            elementJSONObject.put("x", (float)x / inputControlsView.getMaxWidth());
            elementJSONObject.put("y", (float)y / inputControlsView.getMaxHeight());
            elementJSONObject.put("toggleSwitch", toggleSwitch);
            elementJSONObject.put("hideBorder", hideBorder);
            elementJSONObject.put("text", text);
            elementJSONObject.put("iconId", iconId);
            elementJSONObject.put("iconScale", iconScale);
            elementJSONObject.put("opacity", opacity);
            elementJSONObject.put("fillOpacity", fillOpacity);
            elementJSONObject.put("rotation", rotation);
            elementJSONObject.put("borderColor", borderColor);
            elementJSONObject.put("fillColor", fillColor);
            elementJSONObject.put("textColor", textColor);

            if (type == Type.RANGE_BUTTON && range != null) {
                elementJSONObject.put("range", range.name());
                if (orientation != 0) elementJSONObject.put("orientation", orientation);
            }
            return elementJSONObject;
        }
        catch (JSONException e) {
            return null;
        }
    }

    public boolean containsPoint(float x, float y) {
        if (rotation != 0.0f) {
            double radians = Math.toRadians(-rotation);
            float dx = x - this.x;
            float dy = y - this.y;
            x = this.x + (float)(dx * Math.cos(radians) - dy * Math.sin(radians));
            y = this.y + (float)(dx * Math.sin(radians) + dy * Math.cos(radians));
        }
        return getBoundingBox().contains((int)(x + 0.5f), (int)(y + 0.5f));
    }

    private boolean isKeepButtonPressedAfterMinTime() {
        Binding binding = getBindingAt(0);
        return !toggleSwitch && (binding == Binding.GAMEPAD_BUTTON_L3 || binding == Binding.GAMEPAD_BUTTON_R3);
    }

    public boolean handleTouchDown(int pointerId, float x, float y) {
        if (currentPointerId == -1 && containsPoint(x, y)) {
            currentPointerId = pointerId;
            if (type == Type.BUTTON || type == Type.COMBO_BUTTON) {
                if (isKeepButtonPressedAfterMinTime()) touchTime = System.currentTimeMillis();
                if (!toggleSwitch || !selected) {
                    for (Binding binding : bindings) if (binding != Binding.NONE) inputControlsView.handleInputEvent(binding, true);
                }
                return true;
            }
            else if (type == Type.RANGE_BUTTON) {
                scroller.handleTouchDown(x, y);
                return true;
            }
            else {
                if (type == Type.TRACKPAD) {
                    if (currentPosition == null) currentPosition = new PointF();
                    currentPosition.set(x, y);
                }
                else if (type == Type.STEERING_WHEEL) {
                    if (currentPosition == null) currentPosition = new PointF();
                    Rect boundingBox = getBoundingBox();
                    currentPosition.set(x, y);
                }
                return handleTouchMove(pointerId, x, y);
            }
        }
        else return false;
    }

    public boolean handleTouchMove(int pointerId, float x, float y) {
        if (pointerId == currentPointerId && (type == Type.D_PAD || type == Type.STICK || type == Type.TRACKPAD || type == Type.STEERING_WHEEL)) {
            float deltaX, deltaY;
            Rect boundingBox = getBoundingBox();
            float radius = boundingBox.width() * 0.5f;
            TouchpadView touchpadView =  inputControlsView.getTouchpadView();

            if (type == Type.TRACKPAD) {
                if (currentPosition == null) currentPosition = new PointF();
                float[] deltaPoint = touchpadView.computeDeltaPoint(currentPosition.x, currentPosition.y, x, y);
                deltaX = deltaPoint[0];
                deltaY = deltaPoint[1];
                currentPosition.set(x, y);
            }
            else {
                float localX = x - boundingBox.left;
                float localY = y - boundingBox.top;
                float offsetX = localX - radius;
                float offsetY = localY - radius;

                float distance = Mathf.lengthSq(radius - localX, radius - localY);
                if (distance > radius * radius) {
                    float angle = (float)Math.atan2(offsetY, offsetX);
                    offsetX = (float)(Math.cos(angle) * radius);
                    offsetY = (float)(Math.sin(angle) * radius);
                }

                deltaX = Mathf.clamp(offsetX / radius, -1, 1);
                deltaY = Mathf.clamp(offsetY / radius, -1, 1);
            }

            if (type == Type.STICK) {
                if (currentPosition == null) currentPosition = new PointF();
                currentPosition.x = boundingBox.left + deltaX * radius + radius;
                currentPosition.y = boundingBox.top + deltaY * radius + radius;
                final boolean[] states = {deltaY <= -STICK_DEAD_ZONE, deltaX >= STICK_DEAD_ZONE, deltaY >= STICK_DEAD_ZONE, deltaX <= -STICK_DEAD_ZONE};

                for (byte i = 0; i < 4; i++) {
                    float value = i == 1 || i == 3 ? deltaX : deltaY;
                    Binding binding = getBindingAt(i);
                    if (binding.isGamepad()) {
                        value = Mathf.clamp(Math.max(0, Math.abs(value) - 0.01f) * Mathf.sign(value) * STICK_SENSITIVITY, -1, 1);
                        inputControlsView.handleInputEvent(binding, true, value);
                        this.states[i] = true;
                    }
                    else {
                        boolean state = binding.isMouseMove() ? (states[i] || states[(i+2)%4]) : states[i];
                        inputControlsView.handleInputEvent(binding, state, value);
                        this.states[i] = state;
                    }
                }

                inputControlsView.invalidate();
            }
            else if (type == Type.TRACKPAD) {
                final boolean[] states = {deltaY <= -TRACKPAD_MIN_SPEED, deltaX >= TRACKPAD_MIN_SPEED, deltaY >= TRACKPAD_MIN_SPEED, deltaX <= -TRACKPAD_MIN_SPEED};
                int cursorDx = 0;
                int cursorDy = 0;

                for (byte i = 0; i < 4; i++) {
                    float value = (i == 1 || i == 3 ? deltaX : deltaY);
                    Binding binding = getBindingAt(i);
                    if (binding.isGamepad()) {
                        if (interpolator == null) interpolator = new CubicBezierInterpolator();
                        if (Math.abs(value) > TRACKPAD_ACCELERATION_THRESHOLD) value *= STICK_SENSITIVITY;
                        interpolator.set(0.075f, 0.95f, 0.45f, 0.95f);
                        float interpolatedValue = interpolator.getInterpolation(Math.min(1.0f, Math.abs(value / TRACKPAD_MAX_SPEED)));
                        inputControlsView.handleInputEvent(binding, true, Mathf.clamp(interpolatedValue * Mathf.sign(value), -1, 1));
                        this.states[i] = true;
                    }
                    else {
                        if (Math.abs(value) > TouchpadView.CURSOR_ACCELERATION_THRESHOLD) value *= TouchpadView.CURSOR_ACCELERATION;
                        if (binding == Binding.MOUSE_MOVE_LEFT || binding == Binding.MOUSE_MOVE_RIGHT) {
                            cursorDx = Mathf.roundPoint(value);
                        }
                        else if (binding == Binding.MOUSE_MOVE_UP || binding == Binding.MOUSE_MOVE_DOWN) {
                            cursorDy = Mathf.roundPoint(value);
                        }
                        else {
                            inputControlsView.handleInputEvent(binding, states[i], value);
                            this.states[i] = states[i];
                        }
                    }
                }

                if (cursorDx != 0 || cursorDy != 0)  {
                    XServer xServer = inputControlsView.getXServer();
                    if (xServer.isRelativeMouseMovement())
                        xServer.getWinHandler().mouseEvent(MouseEventFlags.MOVE, cursorDx, cursorDy, 0);
                    else
                        inputControlsView.getXServer().injectPointerMoveDelta(cursorDx, cursorDy);
                }
            }
            else if (type == Type.STEERING_WHEEL) {
                // Update current position for wheel visualization
                if (currentPosition == null) currentPosition = new PointF();
                currentPosition.set(x, y);
                
                // Calculate rotation angle from center
                float cx = boundingBox.centerX();
                float cy = boundingBox.centerY();
                float angle = (float) Math.atan2(y - cy, x - cx);
                
                // Normalize angle to -1 to 1 range for horizontal steering
                float normalizedAngle = angle / (float) Math.PI; // -1 to 1
                
                // Apply sensitivity and dead zone
                float steerValue = Math.abs(normalizedAngle) > STICK_DEAD_ZONE ? normalizedAngle : 0f;
                
                // Map to left/right bindings (only horizontal steering)
                Binding leftBinding = getBindingAt(3);  // Left
                Binding rightBinding = getBindingAt(1); // Right
                
                // Reset previous states
                for (byte i = 0; i < states.length; i++) {
                    if (states[i]) {
                        inputControlsView.handleInputEvent(getBindingAt(i), false);
                        states[i] = false;
                    }
                }
                
                // Set new state based on steering direction
                if (steerValue > STICK_DEAD_ZONE) {
                    // Steering right
                    inputControlsView.handleInputEvent(rightBinding, true, steerValue);
                    states[1] = true;
                } else if (steerValue < -STICK_DEAD_ZONE) {
                    // Steering left
                    inputControlsView.handleInputEvent(leftBinding, true, Math.abs(steerValue));
                    states[3] = true;
                }
                
                inputControlsView.invalidate();
            }
            else {
                final boolean[] states = {deltaY <= -DPAD_DEAD_ZONE, deltaX >= DPAD_DEAD_ZONE, deltaY >= DPAD_DEAD_ZONE, deltaX <= -DPAD_DEAD_ZONE};

                for (byte i = 0; i < 4; i++) {
                    float value = i == 1 || i == 3 ? deltaX : deltaY;
                    Binding binding = getBindingAt(i);
                    boolean state = binding.isMouseMove() ? (states[i] || states[(i+2)%4]) : states[i];
                    inputControlsView.handleInputEvent(binding, state, value);
                    this.states[i] = state;
                }
            }

            return true;
        }
        else if (pointerId == currentPointerId && type == Type.RANGE_BUTTON) {
            scroller.handleTouchMove(x, y);
            return true;
        }
        else return false;
    }

    public boolean handleTouchUp(int pointerId) {
        if (pointerId == currentPointerId) {
            if (type == Type.BUTTON || type == Type.COMBO_BUTTON) {
                Binding binding = getBindingAt(0);
                if (isKeepButtonPressedAfterMinTime() && touchTime != null) {
                    selected = (System.currentTimeMillis() - (long)touchTime) > BUTTON_MIN_TIME_TO_KEEP_PRESSED;
                    if (!selected) inputControlsView.handleInputEvent(binding, false);
                    touchTime = null;
                    inputControlsView.invalidate();
                }
                else if (!toggleSwitch || selected) {
                    for (Binding comboBinding : bindings) if (comboBinding != Binding.NONE) inputControlsView.handleInputEvent(comboBinding, false);
                }

                if (toggleSwitch) {
                    selected = !selected;
                    inputControlsView.invalidate();
                }
            }
            else if (type == Type.RANGE_BUTTON || type == Type.D_PAD || type == Type.STICK || type == Type.TRACKPAD || type == Type.STEERING_WHEEL) {
                for (byte i = 0; i < states.length; i++) {
                    if (states[i]) inputControlsView.handleInputEvent(getBindingAt(i), false);
                    states[i] = false;
                }

                if (type == Type.RANGE_BUTTON) {
                    scroller.handleTouchUp();
                }
                else if (type == Type.STICK || type == Type.STEERING_WHEEL) {
                    inputControlsView.invalidate();
                }

                if (currentPosition != null) currentPosition = null;
            }
            currentPointerId = -1;
            return true;
        }
        return false;
    }

    public PointF getCurrentPosition() {
        if (currentPosition == null) {
            currentPosition = new PointF(x, y); // Initialize to the center (same as outer circle)
        }
        return currentPosition;
    }

    // New setter for current position to allow resetting
    public void setCurrentPosition(float x, float y) {
        if (currentPosition == null) {
            currentPosition = new PointF();
        }
        currentPosition.set(x, y);
        // Optionally invalidate the view to trigger a redraw
        inputControlsView.invalidate();
    }
}

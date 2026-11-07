package com.gigabyte.rgbfusion.ui;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.RadialGradient;
import android.graphics.Shader;
import android.graphics.SweepGradient;
import android.util.AttributeSet;
import android.view.MotionEvent;
import android.view.View;

/**
 * Colour disc of the original (ColorselActivity): a sweep gradient
 * red-magenta-blue-cyan-green-yellow-red, faded to white towards the centre.
 * The picked colour is computed with exactly the original formulas:
 * the outer ring (width = size/8) gives the pure hue, the inner area adds
 * (R' - r) * 255 / R' to every channel.
 */
public class ColorWheelView extends View {

    public interface OnColorPicked { void onColorPicked(int argb); }

    private static final int[] HUES = {
            0xFFFF0000, 0xFFFF00FF, 0xFF0000FF, 0xFF00FFFF, 0xFF00FF00, 0xFFFFFF00, 0xFFFF0000};

    private final Paint wheelPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint fadePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint markerPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint markerShadow = new Paint(Paint.ANTI_ALIAS_FLAG);

    private int size;
    private float markerX = Float.NaN, markerY = Float.NaN;
    private int pendingColor;
    private boolean hasPending;
    private OnColorPicked listener;

    public ColorWheelView(Context c) { this(c, null); }

    public ColorWheelView(Context c, AttributeSet a) {
        super(c, a);
        wheelPaint.setStyle(Paint.Style.FILL);
        fadePaint.setStyle(Paint.Style.FILL);
        markerPaint.setStyle(Paint.Style.STROKE);
        markerPaint.setColor(Color.WHITE);
        markerShadow.setStyle(Paint.Style.STROKE);
        markerShadow.setColor(Color.BLACK);
    }

    public void setOnColorPicked(OnColorPicked l) { listener = l; }

    /** Places the marker for an existing colour (original Color_to_angle). */
    public void setColor(int argb) {
        pendingColor = argb;
        hasPending = true;
        if (size > 0) placeMarkerFor(argb);
        invalidate();
    }

    private int stroke() { return size / 8; }
    private float ringRadius() { return size / 2 - stroke() / 2; }   // R' of the original

    @Override
    protected void onSizeChanged(int w, int h, int ow, int oh) {
        size = Math.min(w, h);
        float c = size / 2f;
        wheelPaint.setShader(new SweepGradient(c, c, HUES, null));
        fadePaint.setShader(new RadialGradient(c, c, size / 2f, 0xFFFFFFFF, 0x00FFFFFF, Shader.TileMode.CLAMP));
        float sw = Math.max(2f, stroke() / 10f);
        markerPaint.setStrokeWidth(sw);
        markerShadow.setStrokeWidth(sw * 2f);
        if (hasPending) placeMarkerFor(pendingColor);
    }

    @Override
    protected void onDraw(Canvas canvas) {
        float c = size / 2f;
        canvas.drawCircle(c, c, size / 2f, wheelPaint);
        canvas.drawCircle(c, c, size / 2f, fadePaint);
        if (!Float.isNaN(markerX)) {
            float r = stroke() * 0.3f;
            canvas.drawCircle(markerX, markerY, r, markerShadow);
            canvas.drawCircle(markerX, markerY, r, markerPaint);
        }
    }

    @Override
    public boolean onTouchEvent(MotionEvent e) {
        int a = e.getActionMasked();
        if (a == MotionEvent.ACTION_DOWN || a == MotionEvent.ACTION_MOVE) {
            getParent().requestDisallowInterceptTouchEvent(true);
            pick(e.getX(), e.getY());
        }
        return true;
    }

    // --------------------------------------------------------------- maths

    private void pick(float x, float y) {
        int xi = (int) x, yi = (int) y;
        if (xi > size - 10 || yi > size - 10 || xi <= 0 || yi <= 0) return;
        float c = size / 2f;
        float dx = x - c, dy = y - c;
        double r = Math.hypot(dx, dy);
        if ((int) r >= size / 2) return;                       // outside the disc

        int hue = angleToColor(dx, dy);
        int result;
        if ((int) r <= size / 2 - stroke()) {                  // inner area: fade towards white
            float rp = size / 2 - stroke() / 2;
            float f = (rp - (float) r) / (rp / 255f);
            int add = (int) f;
            result = Color.argb(Color.alpha(hue),
                    clamp(Color.red(hue) + add), clamp(Color.green(hue) + add), clamp(Color.blue(hue) + add));
            markerX = x;
            markerY = y;
        } else {                                               // outer ring: pure hue, marker snapped
            result = hue;
            float rp = ringRadius();
            markerX = c + (float) (dx / r * rp);
            markerY = c + (float) (dy / r * rp);
        }
        invalidate();
        if (listener != null) listener.onColorPicked(result);
    }

    private static int clamp(int v) { return v < 0 ? 0 : Math.min(v, 255); }

    /** Original angle_to_Color + interpColor. */
    static int angleToColor(float dx, float dy) {
        float unit = (float) (Math.atan2(dy, dx) / (2 * Math.PI));
        if (unit < 0) unit += 1f;
        return interp(unit);
    }

    static int interp(float p) {
        if (p <= 0) return HUES[0];
        if (p >= 1) return HUES[HUES.length - 1];
        float pos = p * (HUES.length - 1);
        int i = (int) pos;
        float t = pos - i;
        int c0 = HUES[i], c1 = HUES[i + 1];
        return Color.argb(
                avg(Color.alpha(c0), Color.alpha(c1), t), avg(Color.red(c0), Color.red(c1), t),
                avg(Color.green(c0), Color.green(c1), t), avg(Color.blue(c0), Color.blue(c1), t));
    }

    private static int avg(int a, int b, float t) { return Math.round((b - a) * t) + a; }

    private void placeMarkerFor(int argb) {
        int r = Color.red(argb), g = Color.green(argb), b = Color.blue(argb);
        int f = Math.min(r, Math.min(g, b));
        int hr = r - f, hg = g - f, hb = b - f;
        int max = Math.max(hr, Math.max(hg, hb));
        float c = size / 2f;
        if (max == 0) {                                        // white: centre of the disc
            markerX = c;
            markerY = c;
            return;
        }
        float scale = 255f / max;
        float[] hsv = new float[3];
        Color.RGBToHSV(Math.round(hr * scale), Math.round(hg * scale), Math.round(hb * scale), hsv);
        double unit = ((360.0 - hsv[0]) % 360.0) / 360.0;      // sweep runs the other way round
        double angle = unit * 2 * Math.PI;
        float rp = ringRadius();
        float dist = rp * (1f - f / 255f);
        markerX = c + (float) (Math.cos(angle) * dist);
        markerY = c + (float) (Math.sin(angle) * dist);
    }
}

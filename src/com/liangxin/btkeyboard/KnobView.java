package com.liangxin.btkeyboard;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RadialGradient;
import android.graphics.RectF;
import android.graphics.Shader;
import android.graphics.SweepGradient;
import android.view.HapticFeedbackConstants;
import android.view.MotionEvent;
import android.view.View;

/**
 * 旋钮：外观仿铝合金音量旋钮（滚花外圈 + 放射拉丝顶盖 + 发光指示线）。
 * 拖动旋转（每转 STEP 度触发一格），中心按钮可点击。
 */
public class KnobView extends View {

    public interface Listener {
        void onRotate(int direction);   // +1 顺时针, -1 逆时针

        void onPress();
    }

    private Listener listener;
    private String caption = "";
    private float density = 2f;
    private boolean darkBody = true;
    private float angle = 0f;          // 指针角度（度，0 = 正上方）
    private float lastAngle = 0f;
    private float accum = 0f;
    private boolean dragging = false;
    private float pressScale = 1f;
    private boolean showEnter = false;

    private static final float STEP = 42f;   // 每 42° 一格

    private final Paint fill = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint stroke = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint tickPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint pointerPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint textPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint shade = new Paint(Paint.ANTI_ALIAS_FLAG);

    private int bodyLight = 0xFF454E59, bodyDark = 0xFF15181D;
    private int ringLight = 0xFF5E6672, ringDark = 0xFF11141A;
    private int tickColor = 0xFF656D78;
    private int pointerColor = 0xFFCFEFFF;
    private int captionColor = 0xFF979DA6;
    private int capTop = 0xFF2C323A, capBottom = 0xFF1B1F25;

    public KnobView(Context ctx) {
        super(ctx);
        density = ctx.getResources().getDisplayMetrics().density;
        stroke.setStyle(Paint.Style.STROKE);
        tickPaint.setStyle(Paint.Style.STROKE);
        pointerPaint.setStrokeCap(Paint.Cap.ROUND);
        textPaint.setTextAlign(Paint.Align.CENTER);
        setClickable(true);
        applyThemeColors(true);
    }

    private void applyThemeColors(boolean dark) {
        darkBody = dark;
        if (dark) {
            bodyLight = 0xFF3E4650; bodyDark = 0xFF12151A;
            ringLight = 0xFF434B56; ringDark = 0xFF0E1114;
            tickColor = 0xFF656D78;
            pointerColor = 0xFFCFEFFF;
            captionColor = 0xFF979DA6;
            capTop = 0xFF515A66; capBottom = 0xFF272C33;
        } else {
            bodyLight = 0xFFFDFEFF; bodyDark = 0xFFCBD2DA;
            ringLight = 0xFFFFFFFF; ringDark = 0xFF8F98A3;
            tickColor = 0xFF98A0AA;
            pointerColor = 0xFF1287C2;
            captionColor = 0xFF5F666E;
            capTop = 0xFFF7F9FB; capBottom = 0xFFD6DCE3;
        }
        textPaint.setColor(captionColor);
        invalidate();
    }

    public void setTheme(Theme t) {
        applyThemeColors(t.rootBg == 0xFF181B21);
    }

    public void setListener(Listener l) {
        listener = l;
    }

    public void setCenterIcon(boolean show) {
        showEnter = show;
        invalidate();
    }

    public void setCaption(String s) {
        caption = s;
        invalidate();
    }

    private float dp(float v) {
        return v * density;
    }

    @Override
    protected void onDraw(Canvas c) {
        float cx = getWidth() / 2f;
        float cy = getHeight() / 2f;
        float r = Math.min(getWidth() / 2f, getHeight() / 2f) - dp(6);
        if (r <= dp(10)) return;
        r *= pressScale;

        // ---- 外圈投影
        shade.setStyle(Paint.Style.FILL);
        shade.setColor(0x00000000);
        shade.setShadowLayer(dp(7), 0, dp(4), 0x88000000);
        c.drawCircle(cx, cy, r, shade);

        // ---- 滚花金属外圈
        SweepGradient metal = new SweepGradient(cx, cy,
                new int[]{ringLight, ringDark, ringLight, ringDark,
                        ringLight, ringDark, ringLight},
                new float[]{0f, 0.16f, 0.33f, 0.5f, 0.66f, 0.83f, 1f});
        fill.setShader(metal);
        c.drawCircle(cx, cy, r, fill);
        fill.setShader(null);

        // ---- 滚花齿
        int teeth = 84;
        stroke.setStrokeWidth(dp(1.6f));
        for (int i = 0; i < teeth; i++) {
            float a = (float) (i * 2 * Math.PI / teeth);
            float c1 = (float) Math.cos(a), s1 = (float) Math.sin(a);
            stroke.setColor((i % 2 == 0) ? 0x44FFFFFF : 0x44000000);
            c.drawLine(cx + c1 * (r * 0.85f), cy + s1 * (r * 0.85f),
                    cx + c1 * (r * 0.995f), cy + s1 * (r * 0.995f), stroke);
        }

        // 外圈内缘暗边 + 左上高光弧
        stroke.setStrokeWidth(dp(2.4f));
        stroke.setColor(darkBody ? 0x77000000 : 0x44000000);
        c.drawCircle(cx, cy, r * 0.84f, stroke);
        stroke.setStrokeWidth(dp(3f));
        stroke.setColor(darkBody ? 0x66FFFFFF : 0xE6FFFFFF);
        RectF hi = new RectF(cx - r * 0.925f, cy - r * 0.925f, cx + r * 0.925f, cy + r * 0.925f);
        c.drawArc(hi, 200f, 140f, false, stroke);

        // ---- 顶盖（斜向渐变 + 放射拉丝）
        float capR = r * 0.80f;
        RadialGradient brush = new RadialGradient(cx - capR * 0.32f, cy - capR * 0.40f,
                capR * 2.0f, new int[]{bodyLight, bodyMid(), bodyDark},
                new float[]{0f, 0.5f, 1f}, Shader.TileMode.CLAMP);
        fill.setShader(brush);
        c.drawCircle(cx, cy, capR, fill);
        fill.setShader(null);

        SweepGradient brushed = new SweepGradient(cx, cy,
                new int[]{0x00FFFFFF, 0x2EFFFFFF, 0x00FFFFFF, 0x26000000,
                        0x00FFFFFF, 0x2EFFFFFF, 0x00FFFFFF},
                new float[]{0f, 0.14f, 0.28f, 0.5f, 0.72f, 0.86f, 1f});
        fill.setShader(brushed);
        c.drawCircle(cx, cy, capR, fill);
        fill.setShader(null);

        // 顶盖下半圈内阴影
        stroke.setStrokeWidth(dp(2.6f));
        stroke.setColor(0x44000000);
        c.drawCircle(cx, cy, capR, stroke);

        // ---- 刻度点（顶盖外缘，11 个圆点，当前附近高亮）
        fill.setStyle(Paint.Style.FILL);
        for (int i = 0; i < 11; i++) {
            int deg = -135 + i * 27;
            float a = (float) Math.toRadians(deg - 90);
            float rr = r * 0.905f;
            boolean near = Math.abs(normalize(angle - deg)) < 14;
            float dotR = (i == 5) ? dp(2.6f) : dp(1.9f);
            float dx = cx + (float) Math.cos(a) * rr;
            float dy = cy + (float) Math.sin(a) * rr;
            if (near) {
                fill.setColor(pointerColor);
                fill.setAlpha(60);
                c.drawCircle(dx, dy, dotR * 3.2f, fill);
                fill.setAlpha(255);
                c.drawCircle(dx, dy, dotR * 1.25f, fill);
            } else {
                fill.setColor(tickColor);
                fill.setAlpha(140);
                c.drawCircle(dx, dy, dotR, fill);
                fill.setAlpha(255);
            }
        }

        // ---- 指示线（发光）
        float pa = (float) Math.toRadians(angle - 90);
        float inner = r * 0.30f + dp(1.5f);
        float outer = capR - dp(1.5f);
        float px1 = cx + (float) Math.cos(pa) * inner;
        float py1 = cy + (float) Math.sin(pa) * inner;
        float px2 = cx + (float) Math.cos(pa) * outer;
        float py2 = cy + (float) Math.sin(pa) * outer;
        pointerPaint.setStyle(Paint.Style.STROKE);
        pointerPaint.setColor(pointerColor);
        pointerPaint.setStrokeWidth(dp(5f));
        pointerPaint.setAlpha(90);
        c.drawLine(px1, py1, px2, py2, pointerPaint);
        pointerPaint.setStrokeWidth(dp(2.2f));
        pointerPaint.setAlpha(255);
        c.drawLine(px1, py1, px2, py2, pointerPaint);
        fill.setShader(null);
        fill.setColor(darkBody ? 0xFFFFFFFF : 0xFF1287C2);
        c.drawCircle(px2, py2, dp(2.3f), fill);

        // ---- 中心按键（凸起小圆）
        float btnR = r * 0.30f;
        RadialGradient btn = new RadialGradient(cx - btnR * 0.35f, cy - btnR * 0.45f,
                btnR * 2.1f, new int[]{capTop, capBottom}, null, Shader.TileMode.CLAMP);
        fill.setShader(btn);
        c.drawCircle(cx, cy, btnR, fill);
        fill.setShader(null);
        stroke.setStrokeWidth(dp(1.8f));
        stroke.setColor(0x55FFFFFF);
        c.drawCircle(cx, cy, btnR, stroke);
        stroke.setColor(0x44000000);
        c.drawCircle(cx, cy, btnR + dp(1.2f), stroke);

        // 中心键上的回车符号
        if (showEnter) {
            // 回车箭头（↵）
            float s1 = dp(1.0f);
            float w = btnR * 0.62f;
            stroke.setColor(captionColor);
            stroke.setStyle(Paint.Style.STROKE);
            stroke.setStrokeWidth(dp(1.9f));
            stroke.setStrokeCap(Paint.Cap.ROUND);
            android.graphics.Path pth = new android.graphics.Path();
            pth.moveTo(cx + w, cy - w * 0.55f);
            pth.lineTo(cx + w, cy + w * 0.25f);
            pth.lineTo(cx - w * 0.85f, cy + w * 0.25f);
            c.drawPath(pth, stroke);
            android.graphics.Path arrow = new android.graphics.Path();
            arrow.moveTo(cx - w * 0.30f, cy + w * 0.25f - w * 0.42f);
            arrow.lineTo(cx - w * 0.85f, cy + w * 0.25f);
            arrow.lineTo(cx - w * 0.30f, cy + w * 0.25f + w * 0.42f);
            c.drawPath(arrow, stroke);
            stroke.setStrokeCap(Paint.Cap.BUTT);
            stroke.setStyle(Paint.Style.STROKE);
        }

        // ---- 标题（画在旋钮内部下半部）
        if (caption.length() > 0) {
            textPaint.setTextSize(dp(12f));
            textPaint.setColor(captionColor);
            textPaint.setAlpha(220);
            c.drawText(caption, cx, cy + r * 0.50f, textPaint);
            textPaint.setAlpha(255);
        }
    }

    private int bodyMid() {
        return darkBody ? 0xFF2C323A : 0xFFE9EDF2;
    }

    private float normalize(float deg) {
        while (deg > 180) deg -= 360;
        while (deg < -180) deg += 360;
        return deg;
    }

    private float angleAt(float x, float y) {
        float cx = getWidth() / 2f;
        float cy = getHeight() / 2f;
        return (float) Math.toDegrees(Math.atan2(y - cy, x - cx)) + 90f;
    }

    @Override
    public boolean onTouchEvent(MotionEvent e) {
        switch (e.getActionMasked()) {
            case MotionEvent.ACTION_DOWN:
                dragging = true;
                accum = 0;
                lastAngle = angleAt(e.getX(), e.getY());
                pressScale = 0.982f;
                invalidate();
                break;
            case MotionEvent.ACTION_MOVE: {
                if (!dragging) break;
                float cur = angleAt(e.getX(), e.getY());
                float d = cur - lastAngle;
                if (d > 180) d -= 360;
                if (d < -180) d += 360;
                lastAngle = cur;
                if (Math.abs(d) < 40) {              // 忽略跨过中心的跳变
                    angle += d;
                    accum += d;
                    while (Math.abs(accum) >= STEP) {
                        int dir = accum > 0 ? 1 : -1;  // +角度 = 顺时针
                        if (listener != null) listener.onRotate(dir);
                        performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY);
                        accum -= dir * STEP;
                    }
                    invalidate();
                }
                break;
            }
            case MotionEvent.ACTION_UP:
                pressScale = 1f;
                if (dragging && Math.abs(accum) < 6f) {
                    performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY);
                    if (listener != null) listener.onPress();
                }
                dragging = false;
                accum = 0;
                invalidate();
                break;
            case MotionEvent.ACTION_CANCEL:
                dragging = false;
                pressScale = 1f;
                invalidate();
                break;
        }
        return true;
    }
}

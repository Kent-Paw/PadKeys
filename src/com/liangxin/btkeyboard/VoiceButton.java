package com.liangxin.btkeyboard;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.RadialGradient;
import android.graphics.RectF;
import android.graphics.Shader;
import android.graphics.Typeface;
import android.os.SystemClock;
import android.view.MotionEvent;
import android.view.View;

/**
 * 语音输入大按钮：按住说话，松开结束。
 * 磨砂玻璃质感圆盘 + 现代麦克风图标。
 */
public class VoiceButton extends View {

    public interface Listener {
        void onHoldStart();

        void onHoldEnd();
    }

    private Listener listener;
    private float density = 2f;
    private boolean holding;

    private final Paint discPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint ringPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint glowPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint iconPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint textPaint = new Paint(Paint.ANTI_ALIAS_FLAG);

    private int accent = 0xFF5AC8FA;
    private int idleTop = 0xFF3A424C;
    private int idleBottom = 0xFF14181D;
    private int textColor = 0xFFECEEF1;
    private int subColor = 0xFF8A929C;

    public VoiceButton(Context ctx) {
        super(ctx);
        density = ctx.getResources().getDisplayMetrics().density;
        ringPaint.setStyle(Paint.Style.STROKE);
        glowPaint.setStyle(Paint.Style.STROKE);
        iconPaint.setStyle(Paint.Style.STROKE);
        iconPaint.setStrokeCap(Paint.Cap.ROUND);
        textPaint.setTextAlign(Paint.Align.CENTER);
    }

    public void setTheme(Theme t) {
        accent = t.keyPressed;
        idleTop = t.keyCap;
        idleBottom = t.zoneBg;
        textColor = t.textMain;
        subColor = t.textSub;
        invalidate();
    }

    public void setListener(Listener l) {
        listener = l;
    }

    public boolean isHolding() {
        return holding;
    }

    private float dp(float v) {
        return v * density;
    }

    @Override
    protected void onDraw(Canvas c) {
        float w = getWidth(), h = getHeight();
        float cx = w / 2f;
        float cy = h / 2f;
        float r = Math.min(w, h) / 2f - dp(8);

        // ---------- 外圈氛围 ----------
        glowPaint.setColor(holding ? 0xFF52D2F2 : accent);
        glowPaint.setAlpha(holding ? 90 : 22);
        glowPaint.setStrokeWidth(dp(3));
        c.drawCircle(cx, cy, r + dp(6), glowPaint);

        // ---------- 盘面 ----------
        if (holding) {
            discPaint.setShader(new LinearGradient(cx, cy - r, cx, cy + r,
                    mix(0xFF52D2F2, 0xFFFFFFFF, 0.30f), mix(0xFF0E7FA8, 0xFF000000, 0.12f),
                    Shader.TileMode.CLAMP));
            c.drawCircle(cx, cy, r, discPaint);
            discPaint.setShader(null);
        } else {
            int[] cols = {
                    mix(idleTop, 0xFFFFFFFF, 0.30f),
                    idleTop,
                    mix(idleBottom, 0xFF000000, 0.25f)
            };
            discPaint.setShader(new RadialGradient(cx - r * 0.22f, cy - r * 0.40f, r * 1.55f,
                    cols, new float[]{0f, 0.52f, 1f}, Shader.TileMode.CLAMP));
            c.drawCircle(cx, cy, r, discPaint);
            discPaint.setShader(null);
        }

        // ---------- 边缘亮圈 + 顶部高光 + 底部暗影 ----------
        ringPaint.setColor(0xFFFFFFFF);
        ringPaint.setAlpha(holding ? 90 : 46);
        ringPaint.setStrokeWidth(dp(1.2f));
        c.drawCircle(cx, cy, r - dp(0.6f), ringPaint);

        RectF hi = new RectF(cx - r + dp(3), cy - r + dp(3), cx + r - dp(3), cy + r - dp(3));
        ringPaint.setStrokeWidth(dp(3));
        ringPaint.setAlpha(holding ? 130 : 34);
        c.drawArc(hi, 196, 128, false, ringPaint);

        ringPaint.setColor(0xFF000000);
        ringPaint.setAlpha(holding ? 60 : 75);
        ringPaint.setStrokeWidth(dp(4));
        c.drawArc(hi, 18, 120, false, ringPaint);

        // ---------- 麦克风图标 ----------
        iconPaint.setColor(holding ? 0xFFFFFFFF : textColor);
        iconPaint.setStrokeWidth(dp(2.4f));

        float iconY = cy - r * 0.30f;
        float micW = r * 0.155f;
        float micH = r * 0.26f;

        RectF cap = new RectF(cx - micW, iconY - micH * 0.92f,
                cx + micW, iconY + micH * 0.52f);
        c.drawRoundRect(cap, micW, micW, iconPaint);

        RectF arcR = new RectF(cx - micW * 2.35f, iconY - micH * 0.40f,
                cx + micW * 2.35f, iconY + micH * 1.58f);
        c.drawArc(arcR, 0, 180, false, iconPaint);
        c.drawLine(cx, arcR.bottom, cx, arcR.bottom + r * 0.10f, iconPaint);
        c.drawLine(cx - micW * 1.9f, arcR.bottom + r * 0.10f,
                cx + micW * 1.9f, arcR.bottom + r * 0.10f, iconPaint);

        // ---------- 文案 ----------
        float labelY = cy + r * 0.47f;
        textPaint.setTextSize(dp(15));
        textPaint.setTypeface(Typeface.create("sans-serif-medium", 0));
        textPaint.setColor(holding ? 0xFFFFFFFF : textColor);
        c.drawText(holding ? "松开结束" : "按住说话", cx, labelY, textPaint);

        textPaint.setTextSize(dp(10.5f));
        textPaint.setTypeface(Typeface.create("sans-serif", 0));
        textPaint.setColor(holding ? 0xD9FFFFFF : subColor);
        c.drawText("语音输入", cx, labelY + dp(21), textPaint);

        if (holding) {
            long t = SystemClock.uptimeMillis() % 1300;
            float k = t / 1300f;
            glowPaint.setColor(0xFFFFFFFF);
            glowPaint.setAlpha((int) (60 * (1 - k)));
            glowPaint.setStrokeWidth(dp(3));
            c.drawCircle(cx, cy, r + dp(8) + dp(28) * k, glowPaint);
            invalidate();
        }
    }

    private static int mix(int c1, int c2, float k) {
        int a = (int) (((c1 >> 24) & 0xFF) * (1 - k) + ((c2 >> 24) & 0xFF) * k);
        int r = (int) (((c1 >> 16) & 0xFF) * (1 - k) + ((c2 >> 16) & 0xFF) * k);
        int g = (int) (((c1 >> 8) & 0xFF) * (1 - k) + ((c2 >> 8) & 0xFF) * k);
        int b = (int) ((c1 & 0xFF) * (1 - k) + (c2 & 0xFF) * k);
        return (a << 24) | (r << 16) | (g << 8) | b;
    }

    @Override
    public boolean onTouchEvent(MotionEvent e) {
        switch (e.getActionMasked()) {
            case MotionEvent.ACTION_DOWN:
                holding = true;
                performHapticFeedback(android.view.HapticFeedbackConstants.LONG_PRESS);
                if (listener != null) listener.onHoldStart();
                invalidate();
                return true;
            case MotionEvent.ACTION_UP:
            case MotionEvent.ACTION_CANCEL:
                if (holding) {
                    holding = false;
                    invalidate();
                    if (listener != null) listener.onHoldEnd();
                }
                return true;
        }
        return true;
    }
}

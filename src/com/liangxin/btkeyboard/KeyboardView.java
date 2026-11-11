package com.liangxin.btkeyboard;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.Shader;
import android.graphics.Typeface;
import android.os.Build;
import android.view.HapticFeedbackConstants;
import android.view.MotionEvent;
import android.view.View;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;

/**
 * 自绘键盘：支持多点触控（同时按住 Shift + 字母）、按住 fn 切换多媒体层。
 */
public class KeyboardView extends View {

    public interface Callback {
        void onKeyboardReport(int modifiers, int[] keyCodes);

        void onConsumer(int usage, boolean down);

        void onCombo(int modifiers, int usage);
    }

    private Callback callback;
    private boolean macMode = true;
    private boolean fnDown = false;
    private boolean capsOn = false;
    private boolean rgbOn = false;
    private boolean hapticOn = true;
    private boolean soundOn = false;
    private boolean flashOn = false;
    private android.media.SoundPool soundPool;
    private int tapSoundId;
    private KeyDef flashKey;
    private long flashStart;
    private final RectF flashRect = new RectF();
    private android.os.Vibrator vibrator;
    private long rgbStart = System.currentTimeMillis();
    private Theme theme = Theme.dark();
    private float maxHeightFrac = 1.0f;

    private final List<List<KeyDef>> rows = new ArrayList<>();
    private final List<KeyRect> keyRects = new ArrayList<>();
    private final LinkedHashMap<Integer, Press> pointers = new LinkedHashMap<>();
    private final LinkedHashSet<KeyDef> heldKeys = new LinkedHashSet<>();
    private int latchMods = 0;        // 单击锁存的修饰键（点一下 Shift 再点 A 出大写）
    private boolean fnLatched = false;

    private final Paint bgPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint keyPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint strokePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint textPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint subPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint shiftPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint ledPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint glowPaint = new Paint(Paint.ANTI_ALIAS_FLAG);

    private float density = 1f;
    private float gap = 6f;
    private float padH = 10f;
    private float padTop = 8f;
    private float padBottom = 10f;

    private final RectF tmpRect = new RectF();

    private static class KeyRect {
        RectF rect;
        KeyDef key;

        KeyRect(RectF r, KeyDef k) {
            rect = r;
            key = k;
        }
    }

    private static class Press {
        long downTime;          // 按下时刻，用于判断单击 vs 长按
        boolean usedWithKey;    // 按住期间又按了别的键 → 视为组合键用法
        KeyDef key;
        boolean consumerFired;
    }

    public KeyboardView(Context context) {
        super(context);
        init(context);
    }

    private void init(Context ctx) {
        density = ctx.getResources().getDisplayMetrics().density;
        gap = dp(6);
        padH = dp(10);
        padTop = dp(8);
        padBottom = dp(10);
        setFocusable(true);
        setHapticFeedbackEnabled(true);
        textPaint.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
        subPaint.setTypeface(Typeface.create("sans-serif", Typeface.NORMAL));
        shiftPaint.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
        strokePaint.setStyle(Paint.Style.STROKE);
        strokePaint.setColor(0x33000000);
        strokePaint.setStrokeWidth(dp(1));
        ledPaint.setColor(0xFF3DDC84);
        rows.clear();
        rows.addAll(Layouts.build(macMode));
    }

    public void setCallback(Callback cb) {
        this.callback = cb;
    }

    public void setMacMode(boolean mac) {
        if (macMode == mac) return;
        macMode = mac;
        rows.clear();
        rows.addAll(Layouts.build(macMode));
        for (KeyRect kr : keyRects) {
            if (kr.key.type == KeyDef.T_MOD) {
                if (kr.key.modBit == Layouts.M_LGUI) {
                    kr.key.label = mac ? "cmd" : "Win";
                    kr.key.sub = mac ? "Command" : "Windows";
                } else if (kr.key.modBit == Layouts.M_LALT || kr.key.modBit == Layouts.M_RALT) {
                    kr.key.sub = mac ? "Option" : "Alt";
                }
            }
        }
        invalidate();
    }

    public void setCapsOn(boolean on) {
        if (capsOn != on) {
            capsOn = on;
            invalidate();
        }
    }

    /** 限制键盘最多占屏幕高度的比例（给上方触控区留空间） */
    public void setMaxHeightFraction(float f) {
        maxHeightFrac = f;
        requestLayout();
    }

    public void setTheme(Theme t) {
        theme = t;
        strokePaint.setColor(t.strokeColor);
        invalidate();
    }

    /** RGB 灯效开关 */
    public void setRgbEnabled(boolean on) {
        if (rgbOn == on) return;
        rgbOn = on;
        invalidate();
    }

    public boolean isRgbEnabled() {
        return rgbOn;
    }

    /** 按键震动开关 */
    public void setHapticEnabled(boolean on) {
        hapticOn = on;
    }

    public boolean isHapticEnabled() {
        return hapticOn;
    }

    /** 按键音 + 屏幕视觉反馈（默认关闭） */
    public void setFeedbackEnabled(boolean sound, boolean visual) {
        soundOn = sound;
        flashOn = visual;
        if (sound && soundPool == null) {
            try {
                android.media.AudioAttributes attrs = new android.media.AudioAttributes.Builder()
                        .setUsage(android.media.AudioAttributes.USAGE_ASSISTANCE_SONIFICATION)
                        .setContentType(android.media.AudioAttributes.CONTENT_TYPE_SONIFICATION)
                        .build();
                soundPool = new android.media.SoundPool.Builder()
                        .setMaxStreams(4)
                        .setAudioAttributes(attrs)
                        .build();
                tapSoundId = soundPool.load(getContext(), R.raw.key_tap, 1);
            } catch (Throwable ignored) {
                soundPool = null;
            }
        }
    }

    private void playTap() {
        if (soundPool == null || tapSoundId <= 0) return;
        try {
            soundPool.play(tapSoundId, 0.42f, 0.42f, 1, 0, 1f);
        } catch (Throwable ignored) {
        }
    }

    /** 触发一次按键震动（本机没有马达时会自动无效） */
    private void tick() {
        if (!hapticOn) return;
        try {
            if (vibrator == null) {
                vibrator = (android.os.Vibrator) getContext()
                        .getSystemService(android.content.Context.VIBRATOR_SERVICE);
            }
            if (vibrator != null && vibrator.hasVibrator()) {
                if (android.os.Build.VERSION.SDK_INT >= 26) {
                    vibrator.vibrate(android.os.VibrationEffect.createOneShot(
                            14, android.os.VibrationEffect.DEFAULT_AMPLITUDE));
                } else {
                    vibrator.vibrate(14);
                }
                return;
            }
        } catch (Throwable ignored) {
        }
        performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY);
    }

    private float dp(float v) {
        return v * density;
    }

    @Override
    protected void onMeasure(int widthSpec, int heightSpec) {
        int w = MeasureSpec.getSize(widthSpec);
        int hMode = MeasureSpec.getMode(heightSpec);
        int hSize = MeasureSpec.getSize(heightSpec);
        float availW = w - padH * 2;
        float unitW = availW / 14.6f;
        float rowH = Math.min(unitW * 0.95f, dp(140));
        int nRows = Math.max(rows.size(), 1);
        float weightSum = 0.86f + (nRows - 1);
        int desired = (int) (padTop + padBottom + weightSum * rowH + gap * (nRows - 1));
        if (hMode == MeasureSpec.EXACTLY) {
            desired = hSize;
        } else {
            int screenH = getResources().getDisplayMetrics().heightPixels;
            desired = Math.min(desired, (int) (screenH * maxHeightFrac));
        }
        setMeasuredDimension(w, desired);
    }

    @Override
    protected void onSizeChanged(int w, int h, int oldw, int oldh) {
        super.onSizeChanged(w, h, oldw, oldh);
        layoutKeys(w, h);
    }

    private void layoutKeys(int w, int h) {
        keyRects.clear();
        float availW = w - padH * 2;
        float availH = h - padTop - padBottom;
        int nRows = rows.size();
        // 第一行（功能键行）矮一点
        float weightSum = 0.86f + (nRows - 1);
        float unitH = (availH - gap * (nRows - 1)) / weightSum;

        float y = padTop;
        for (int r = 0; r < nRows; r++) {
            float rowH = unitH * (r == 0 ? 0.86f : 1.0f);
            List<KeyDef> row = rows.get(r);
            float sum = 0;
            int cols = 0;
            for (KeyDef k : row) {
                if (k.sameColumn) continue;
                sum += k.units;
                cols++;
            }
            float unitW = (availW - gap * (cols - 1)) / sum;
            float x = padH;
            float prevStart = padH;
            for (int i = 0; i < row.size(); i++) {
                KeyDef k = row.get(i);
                float kw = unitW * k.units;
                float startX;
                if (k.sameColumn) {
                    startX = prevStart;
                } else {
                    startX = x;
                    prevStart = x;
                }
                if (k.halfTop) {
                    keyRects.add(new KeyRect(new RectF(startX, y, startX + kw, y + rowH / 2 - gap / 2), k));
                } else if (k.halfBottom) {
                    keyRects.add(new KeyRect(new RectF(startX, y + rowH / 2 + gap / 2, startX + kw, y + rowH), k));
                } else {
                    keyRects.add(new KeyRect(new RectF(startX, y, startX + kw, y + rowH), k));
                }
                if (!k.sameColumn) x = startX + kw + gap;
            }
            y += rowH + gap;
        }
    }

    @Override
    protected void onDraw(Canvas canvas) {
        int w = getWidth();
        int h = getHeight();
        // 机身（仿原图银灰色）
        LinearGradient lg = new LinearGradient(0, 0, 0, h,
                new int[]{theme.bodyTop, theme.bodyMid, theme.bodyBottom}, null, Shader.TileMode.CLAMP);
        bgPaint.setShader(lg);
        canvas.drawRect(0, 0, w, h, bgPaint);

        for (KeyRect kr : keyRects) {
            drawKey(canvas, kr);
        }
        if (rgbOn) postInvalidateOnAnimation();
    }

    private void drawKey(Canvas canvas, KeyRect kr) {
        KeyDef k = kr.key;
        RectF r = kr.rect;
        boolean pressed = isActive(k);
        float radius = Math.min(dp(9), r.height() * 0.16f);

        // 视觉反馈：按下的键在 150ms 内轻轻内缩 + 一次白光，然后回弹
        int flashAlpha = 0;
        RectF kr2 = r;
        if (flashOn && k == flashKey) {
            long dt = System.currentTimeMillis() - flashStart;
            if (dt < 150) {
                float k2 = 1f - dt / 150f;
                float inset = dp(3.2f) * k2;
                flashRect.set(r.left + inset, r.top + inset, r.right - inset, r.bottom - inset);
                kr2 = flashRect;
                flashAlpha = (int) (95 * k2);
                postInvalidateOnAnimation();
            }
        }

        if (rgbOn) {
            // 彩虹流动 + 呼吸：颜色沿键盘横向渐变，并随时间流动
            float t = (System.currentTimeMillis() - rgbStart) / 1000f;
            float pos = r.left / Math.max(1f, getWidth());
            float hue = (t * 52f + pos * 320f) % 360f;
            float breathe = 0.55f + 0.45f * (float) Math.sin(t * 2.0f + pos * 3.6f);
            int c = Color.HSVToColor(new float[]{hue, 0.85f, 1f});
            glowPaint.setShader(null);
            glowPaint.setColor(c);
            // 外圈柔光（用两层半透明圆角矩形模拟，避免 blur 在硬件层失效）
            glowPaint.setAlpha((int) (30 * breathe));
            tmpRect.set(r.left - dp(6), r.top - dp(6), r.right + dp(6), r.bottom + dp(6));
            canvas.drawRoundRect(tmpRect, radius + dp(6), radius + dp(6), glowPaint);
            glowPaint.setAlpha((int) (70 * breathe));
            tmpRect.set(r.left - dp(2.5f), r.top - dp(2.5f), r.right + dp(2.5f), r.bottom + dp(2.5f));
            canvas.drawRoundRect(tmpRect, radius + dp(2.5f), radius + dp(2.5f), glowPaint);
            // 键帽整体泛光
            glowPaint.setAlpha((int) (40 * breathe));
            canvas.drawRoundRect(r, radius, radius, glowPaint);
        }

        int base = pressed ? theme.keyPressed : theme.keyCap;
        if (k.type == KeyDef.T_MOD || k.type == KeyDef.T_FN) {
            base = pressed ? theme.keyPressed : theme.keyMod;
        }
        keyPaint.setShader(null);
        keyPaint.setColor(base);
        canvas.drawRoundRect(kr2, radius, radius, keyPaint);

        if (flashAlpha > 0) {
            keyPaint.setColor(0xFFFFFF);
            keyPaint.setAlpha(flashAlpha);
            canvas.drawRoundRect(kr2, radius, radius, keyPaint);
            keyPaint.setAlpha(255);
        }

        // 顶部高光
        keyPaint.setColor(pressed ? 0x33FFFFFF : 0x1FFFFFFF);
        tmpRect.set(kr2.left + dp(2), kr2.top + dp(1.5f), kr2.right - dp(2), kr2.top + dp(3));
        canvas.drawRoundRect(tmpRect, dp(1.5f), dp(1.5f), keyPaint);

        canvas.drawRoundRect(kr2, radius, radius, strokePaint);

        // ---- 文字 ----
        float hPad = dp(6);
        float textSize = Math.min(r.height() * 0.40f, dp(22));
        if (k.label.length() >= 5) textSize *= 0.85f;
        textSize *= k.textScale;
        textPaint.setTextSize(textSize);
        textPaint.setColor(pressed ? 0xFFFFFFFF : theme.textMain);
        textPaint.setTextAlign(Paint.Align.CENTER);

        boolean hasSub = k.sub != null && k.sub.length() > 0;
        float cy = r.centerY();
        if (k.secondLine != null) {
            cy = r.centerY() - textSize * 0.28f;
        } else if (hasSub) {
            cy = r.centerY() - textSize * 0.12f;
        }
        String label = k.label;
        drawFittedText(canvas, label, r, cy, textPaint, hPad);

        if (k.secondLine != null) {
            subPaint.setTextSize(Math.min(r.height() * 0.26f, dp(13)));
            subPaint.setColor(pressed ? 0xFFFFFFFF : theme.textSub);
            subPaint.setTextAlign(Paint.Align.CENTER);
            canvas.drawText(k.secondLine, r.centerX(),
                    Math.min(r.bottom - dp(4), r.centerY() + textSize * 0.60f + dp(11)), subPaint);
        }

        if (hasSub) {
            subPaint.setTextSize(Math.min(r.height() * 0.22f, dp(12)));
            subPaint.setColor(k.subColor);
            subPaint.setTextAlign(Paint.Align.CENTER);
            String sub = k.sub;
            if (k.type == KeyDef.T_KEY && fnDown && (k.fnConsumer != 0 || k.fnComboUsage != 0)
                    && k.label.startsWith("F")) {
                subPaint.setColor(0xFFFFC46B);
            }
            canvas.drawText(sub, r.centerX(), r.bottom - dp(5), subPaint);
        }

        if (k.shiftLabel != null) {
            shiftPaint.setTextSize(Math.min(r.height() * 0.26f, dp(13)));
            shiftPaint.setColor(pressed ? 0xFFFFFFFF : 0xFF8FB8FF);
            shiftPaint.setTextAlign(Paint.Align.RIGHT);
            canvas.drawText(k.shiftLabel, r.right - dp(6), r.top + dp(14), shiftPaint);
        }

        if (k.type == KeyDef.T_CAPS && capsOn) {
            canvas.drawCircle(r.left + dp(9), r.top + dp(11), dp(3.5f), ledPaint);
        }
        if (k.type == KeyDef.T_FN && fnDown) {
            ledPaint.setColor(0xFFFFC46B);
            canvas.drawCircle(r.left + dp(9), r.top + dp(11), dp(3.5f), ledPaint);
            ledPaint.setColor(0xFF3DDC84);
        }
    }

    private void drawFittedText(Canvas canvas, String text, RectF r, float baseline,
                                Paint p, float hPad) {
        if (text == null || text.isEmpty()) return;
        float maxW = r.width() - hPad * 2;
        float size = p.getTextSize();
        float measured = p.measureText(text);
        if (measured > maxW && measured > 0) {
            p.setTextSize(size * (maxW / measured));
        }
        canvas.drawText(text, r.centerX(), baseline, p);
        p.setTextSize(size);
    }

    private boolean isActive(KeyDef k) {
        if (isPressed(k)) return true;
        if (k.type == KeyDef.T_MOD && (latchMods & k.modBit) != 0) return true;
        return k.type == KeyDef.T_FN && fnLatched;
    }

    private boolean isPressed(KeyDef k) {
        for (Press p : pointers.values()) {
            if (p.key == k) return true;
        }
        return false;
    }

    private KeyDef keyAt(float x, float y) {
        for (KeyRect kr : keyRects) {
            if (kr.rect.contains(x, y)) return kr.key;
        }
        return null;
    }

    @Override
    public boolean onTouchEvent(MotionEvent e) {
        int action = e.getActionMasked();
        switch (action) {
            case MotionEvent.ACTION_DOWN:
            case MotionEvent.ACTION_POINTER_DOWN: {
                int idx = e.getActionIndex();
                int pid = e.getPointerId(idx);
                KeyDef k = keyAt(e.getX(idx), e.getY(idx));
                if (k != null) press(pid, k);
                break;
            }
            case MotionEvent.ACTION_UP:
            case MotionEvent.ACTION_POINTER_UP: {
                int idx = e.getActionIndex();
                int pid = e.getPointerId(idx);
                release(pid);
                break;
            }
            case MotionEvent.ACTION_CANCEL: {
                List<Integer> ids = new ArrayList<>(pointers.keySet());
                for (Integer id : ids) release(id);
                break;
            }
            default:
                break;
        }
        invalidate();
        return true;
    }

    private void press(int pointerId, KeyDef k) {
        // 同一个键已经按住了（比如两根手指压同一键），忽略
        for (Press p : pointers.values()) {
            if (p.key == k) return;
        }
        Press ps = new Press();
        ps.key = k;
        ps.downTime = System.currentTimeMillis();
        // 如果是普通按键，把当前按住的修饰键标记为“被组合使用”
        if (k.type != KeyDef.T_MOD && k.type != KeyDef.T_FN) {
            for (Press other : pointers.values()) {
                if (other.key.type == KeyDef.T_MOD || other.key.type == KeyDef.T_FN) {
                    other.usedWithKey = true;
                }
            }
        }
        pointers.put(pointerId, ps);
        tick();
        if (soundOn) playTap();
        if (flashOn) {
            flashKey = k;
            flashStart = System.currentTimeMillis();
            postInvalidateOnAnimation();
        }

        switch (k.type) {
            case KeyDef.T_FN:
                fnDown = true;
                break;
                // 单击可在松开时锁存
            case KeyDef.T_MOD:
                heldKeys.add(k);
                emitReport();
                break;
            case KeyDef.T_CONSUMER:
                if (callback != null) callback.onConsumer(k.usage, true);
                ps.consumerFired = true;
                break;
            case KeyDef.T_COMBO:
                if (callback != null) callback.onCombo(k.comboMods, k.comboUsage);
                break;
            case KeyDef.T_CAPS:
                heldKeys.add(k);
                capsOn = !capsOn;
                emitReport();
                break;
            default: {
                boolean useFn = fnDown || fnLatched;
                if (useFn && k.fnConsumer != 0) {
                    if (callback != null) callback.onConsumer(k.fnConsumer, true);
                    ps.consumerFired = true;
                } else if (useFn && k.fnComboUsage != 0) {
                    if (callback != null) callback.onCombo(k.fnComboMods, k.fnComboUsage);
                } else {
                    heldKeys.add(k);
                    emitReport();
                }
                if (fnLatched) {
                    fnLatched = false;
                    emitReport();
                }
                break;
            }
        }
    }

    private void release(int pointerId) {
        Press ps = pointers.remove(pointerId);
        if (ps == null) return;
        KeyDef k = ps.key;

        switch (k.type) {
            case KeyDef.T_FN:
                fnDown = false;
                if (!ps.usedWithKey && System.currentTimeMillis() - ps.downTime < 500) {
                    fnLatched = !fnLatched;   // 轻点一下 fn：锁存到下一个键
                }
                break;
            case KeyDef.T_MOD: {
                heldKeys.remove(k);
                if (!ps.usedWithKey && System.currentTimeMillis() - ps.downTime < 500) {
                    // 轻点一下修饰键：锁存（再点一下取消）
                    if ((latchMods & k.modBit) != 0) latchMods &= ~k.modBit;
                    else latchMods |= k.modBit;
                }
                emitReport();
                break;
            }
            case KeyDef.T_CAPS:
                heldKeys.remove(k);
                emitReport();
                break;
            case KeyDef.T_CONSUMER:
            case KeyDef.T_COMBO:
                if (ps.consumerFired && callback != null) callback.onConsumer(k.usage, false);
                break;
            default:
                if (ps.consumerFired) {
                    if (callback != null) callback.onConsumer(k.fnConsumer, false);
                } else {
                    heldKeys.remove(k);
                    emitReport();
                    if (latchMods != 0) {   // 一次组合用完就释放锁存
                        latchMods = 0;
                        emitReport();
                    }
                }
                break;
        }
    }

    private void emitReport() {
        if (callback == null) return;
        int mods = 0;
        int[] codes = new int[6];
        int n = 0;
        for (KeyDef k : heldKeys) {
            if (k.type == KeyDef.T_MOD) {
                mods |= k.modBit;
            } else if (n < 6) {
                codes[n++] = k.usage;
            }
        }
        mods |= latchMods;
        callback.onKeyboardReport(mods, Arrays.copyOf(codes, n));
    }
}

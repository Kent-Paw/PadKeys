package com.liangxin.btkeyboard;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;
import android.view.MotionEvent;
import android.view.View;

/**
 * 触控板：
 *  · 单指滑动 = 移动鼠标
 *  · 单指轻点 = 左键；双指轻点 = 右键
 *  · 单指按住拖 = 拖动（配合外部按住的左键即为"选文字"）
 *  · 双指上下滑 = 滚轮
 *  · 三指上/下/左/右滑 = 系统手势（由上层映射成快捷键）
 */
public class TouchpadView extends View {

    public static final int GESTURE_UP = 1;
    public static final int GESTURE_DOWN = 2;
    public static final int GESTURE_LEFT = 3;
    public static final int GESTURE_RIGHT = 4;

    public interface Listener {
        void onMouseMove(int dx, int dy);

        void onMouseButton(int buttons);   // 1=左键, 2=右键, 0=全部松开

        void onWheel(int amount);          // 正=向上滚

        void onGesture(int gesture);       // GESTURE_*
    }

    private Listener listener;
    private float density = 2f;

    private final Paint bgPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint borderPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint hintPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint dotPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint dragPaint = new Paint(Paint.ANTI_ALIAS_FLAG);

    private float lastX, lastY;
    private float accX, accY;
    private float scrollAcc;
    private long downTime;
    private float downX, downY;
    private int maxPointers;
    private boolean moved;
    private boolean darkPad = true;

    // 三指手势
    private float gestureX, gestureY;
    private boolean gestureFired;
    // 单指长按拖拽（只有按住不动一段时间才进入，避免普通移动误按左键）
    private boolean dragging;

    private final Runnable armDrag = new Runnable() {
        @Override
        public void run() {
            if (!dragging && listener != null) {
                dragging = true;
                listener.onMouseButton(1);
                invalidate();
            }
        }
    };

    /** 指针倍率：macOS 有系统指针加速，Windows 没有，需要更大的原始增益 */
    private static final float SENS_MAC = 1.35f;
    private static final float SENS_WIN = 2.30f;
    private float sens = SENS_MAC;

    private static final float WHEEL_STEP_PX = 24f;
    private static final float DRAG_START_PX = 8f;
    private static final long DRAG_HOLD_MS = 420;
    private static final float GESTURE_STEP_PX = 52f;

    public TouchpadView(Context ctx) {
        super(ctx);
        density = ctx.getResources().getDisplayMetrics().density;
        bgPaint.setColor(0xFF191C22);
        borderPaint.setStyle(Paint.Style.STROKE);
        borderPaint.setStrokeWidth(dp(1.2f));
        borderPaint.setColor(0xFF2C3138);
        hintPaint.setColor(0xFF4A5058);
        hintPaint.setTextSize(dp(11));
        hintPaint.setTextAlign(Paint.Align.CENTER);
        dotPaint.setColor(0xFF3A4049);
    }
    public void setTheme(Theme t) {
        bgPaint.setColor(t.padBg);
        darkPad = (t.rootBg == 0xFF181B21);
        borderPaint.setColor(t.strokeColor == 0x22000000 ? 0xFF2C3138 : 0xFFD3D8DF);
        hintPaint.setColor(t.strokeColor == 0x22000000 ? 0xFF4A5058 : 0xFF9AA0A8);
        dotPaint.setColor(t.strokeColor == 0x22000000 ? 0xFF3A4049 : 0xFFBFC5CC);
        dragPaint.setColor(t.strokeColor == 0x22000000 ? 0xFF4FA3FF : 0xFF1D6FD0);
        dragPaint.setTextSize(dp(12));
        dragPaint.setTextAlign(Paint.Align.CENTER);
        invalidate();
    }

    public void setListener(Listener l) {
        listener = l;
    }

    /** 按宿主操作系统调整指针增益 */
    public void setPlatformMac(boolean mac) {
        sens = mac ? SENS_MAC : SENS_WIN;
    }

    private float dp(float v) {
        return v * density;
    }

    @Override
    protected void onDraw(Canvas c) {
        float w = getWidth(), h = getHeight();
        RectF r = new RectF(dp(4), dp(4), w - dp(4), h - dp(4));
        int c1 = bgPaint.getColor();
        int c2 = darkPad ? 0xFF15181D : 0xFFF4F6F9;
        bgPaint.setShader(new android.graphics.RadialGradient(
                w * 0.5f, h * 0.45f, Math.max(w, h) * 0.75f,
                new int[]{c1, c2}, null,
                android.graphics.Shader.TileMode.CLAMP));
        c.drawRoundRect(r, dp(14), dp(14), bgPaint);
        bgPaint.setShader(null);
        c.drawRoundRect(r, dp(14), dp(14), borderPaint);

        // 四角小点，暗示这是触控区
        float inset = dp(14);
        float rad = dp(1.6f);
        for (int i = 0; i < 4; i++) {
            float x = (i % 2 == 0) ? r.left + inset : r.right - inset;
            float y = (i < 2) ? r.top + inset : r.bottom - inset;
            c.drawCircle(x, y, rad, dotPaint);
        }

        c.drawText("单指移动 · 轻点左键 · 按住拖动选字 · 双指滚轮 · 三指滑动 = 系统手势",
                w / 2f, h - dp(14), hintPaint);

        if (dragging) {
            c.drawText("左键按住 · 可以拖选", w / 2f, dp(26), dragPaint);
        }
    }

    @Override
    public boolean onTouchEvent(MotionEvent e) {
        switch (e.getActionMasked()) {
            case MotionEvent.ACTION_DOWN:
                maxPointers = 1;
                moved = false;
                dragging = false;
                gestureFired = false;
                downTime = System.currentTimeMillis();
                downX = e.getX();
                downY = e.getY();
                lastX = downX;
                lastY = downY;
                accX = accY = 0;
                scrollAcc = 0;
                gestureX = gestureY = 0;
                removeCallbacks(armDrag);
                postDelayed(armDrag, DRAG_HOLD_MS);
                break;

            case MotionEvent.ACTION_POINTER_DOWN:
                maxPointers = Math.max(maxPointers, e.getPointerCount());
                lastX = avg(e, 0);
                lastY = avg(e, 1);
                scrollAcc = 0;
                removeCallbacks(armDrag);
                break;

            case MotionEvent.ACTION_MOVE: {
                int count = e.getPointerCount();
                if (count >= 3) {
                    // ---- 三指：系统手势
                    float ax = avg(e, 0), ay = avg(e, 1);
                    if (!gestureFired) {
                        gestureX += ax - lastX;
                        gestureY += ay - lastY;
                        float th = dp(GESTURE_STEP_PX);
                        if (Math.abs(gestureY) > th || Math.abs(gestureX) > th) {
                            int g;
                            if (Math.abs(gestureY) >= Math.abs(gestureX)) {
                                g = gestureY < 0 ? GESTURE_UP : GESTURE_DOWN;
                            } else {
                                g = gestureX < 0 ? GESTURE_LEFT : GESTURE_RIGHT;
                            }
                            gestureFired = true;
                            if (listener != null) listener.onGesture(g);
                        }
                    }
                    lastX = ax;
                    lastY = ay;
                    moved = true;
                } else if (count == 2) {
                    // ---- 双指：滚轮
                    float ay = avg(e, 1);
                    float ay2 = avg(e, 0);
                    float dy = ay - lastY;
                    scrollAcc += dy;
                    float stepPx = dp(WHEEL_STEP_PX);
                    while (Math.abs(scrollAcc) >= stepPx) {
                        int dir = scrollAcc > 0 ? -1 : 1;   // 手指下滑 = 内容上滚
                        if (listener != null) listener.onWheel(dir);
                        scrollAcc -= Math.signum(scrollAcc) * stepPx;
                    }
                    lastY = ay;
                    lastX = ay2;
                    if (Math.abs(ay - downY) > dp(4) || Math.abs(ay2 - downX) > dp(4)) moved = true;
                } else {
                    // ---- 单指：移动 / 拖拽
                    float x = e.getX(), y = e.getY();
                    float dx = x - lastX, dy = y - lastY;
                    lastX = x;
                    lastY = y;
                    if (Math.abs(dx) > dp(0.6f) || Math.abs(dy) > dp(0.6f)) moved = true;

                    if (!dragging && (Math.abs(x - downX) > dp(DRAG_START_PX)
                            || Math.abs(y - downY) > dp(DRAG_START_PX))) {
                        removeCallbacks(armDrag);   // 手指已在移动，取消长按，不误按左键
                    }
                    accX += dx * sens;
                    accY += dy * sens;
                    int ix = clamp8((int) accX), iy = clamp8((int) accY);
                    if (ix != 0 || iy != 0) {
                        accX -= ix;      // 只扣除已上报的整数部分，保留亚像素余量
                        accY -= iy;
                        if (listener != null) listener.onMouseMove(ix, iy);
                    }
                }
                break;
            }

            case MotionEvent.ACTION_UP:
            case MotionEvent.ACTION_CANCEL: {
                removeCallbacks(armDrag);
                if (dragging) {
                    dragging = false;
                    if (listener != null) listener.onMouseButton(0);   // 松开左键
                    break;
                }
                long dur = System.currentTimeMillis() - downTime;
                float tx = e.getX() - downX, ty = e.getY() - downY;
                boolean tap = dur < 260 && Math.abs(tx) < dp(14) && Math.abs(ty) < dp(14)
                        && !moved && !gestureFired && maxPointers <= 2;
                if (tap && listener != null) {
                    final int btn = maxPointers >= 2 ? 2 : 1;   // 2=右键
                    listener.onMouseButton(btn);
                    postDelayed(new Runnable() {
                        @Override
                        public void run() {
                            if (listener != null) listener.onMouseButton(0);
                        }
                    }, 90);
                }
                maxPointers = 0;
                break;
            }
        }
        invalidate();
        return true;
    }

    private float avg(MotionEvent e, int axis) {
        int n = e.getPointerCount();
        float s = 0;
        for (int i = 0; i < n; i++) s += (axis == 0 ? e.getX(i) : e.getY(i));
        return s / n;
    }

    private static int clamp8(int v) {
        return Math.max(-120, Math.min(120, v));
    }
}

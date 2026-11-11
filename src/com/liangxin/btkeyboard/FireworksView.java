package com.liangxin.btkeyboard;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Path;
import android.view.MotionEvent;
import android.view.View;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Random;

/**
 * 全屏特效：在屏幕随机位置不断炸开的烟花（不拦截触摸，键盘/触控板照常可用）。
 */
public class FireworksView extends View {

    private static class Particle {
        float x, y, vx, vy, life, maxLife;
        float px, py;
        int color;
        float size;
    }

    private static class Shell {      // 上升的弹体
        float x, y, vy;
        float targetY;
        int color;
        boolean exploded;
    }

    private final List<Particle> particles = new ArrayList<>();
    private final List<Shell> shells = new ArrayList<>();
    private final Random rnd = new Random();
    private boolean running;

    private long lastNs = 0;
    private float spawnTimer = 0;

    private final Paint dotPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint trailPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint glowPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Path path = new Path();

    public FireworksView(Context ctx) {
        super(ctx);
        setLayerType(LAYER_TYPE_HARDWARE, null);
        trailPaint.setStrokeWidth(1.2f);
    }

    public void setRunning(boolean on) {
        running = on;
        if (on) {
            particles.clear();
            shells.clear();
            lastNs = 0;
            spawnTimer = 0;
            setVisibility(VISIBLE);
            invalidate();
        } else {
            setVisibility(GONE);
            particles.clear();
            shells.clear();
        }
    }

    public boolean isRunning() {
        return running;
    }

    @Override
    public boolean onTouchEvent(MotionEvent e) {
        return false;      // 不拦截触摸
    }

    @Override
    protected void onDraw(Canvas c) {
        if (!running) return;

        long now = System.nanoTime();
        float dt = lastNs == 0 ? 0.016f : Math.min((now - lastNs) / 1e9f, 0.05f);
        lastNs = now;

        float w = getWidth(), h = getHeight();
        float density = getResources().getDisplayMetrics().density;

        // 夜空底色（半透明，键盘仍可见）
        c.drawColor(0x3C081020);

        // ---- 生成新烟花 ----
        spawnTimer -= dt;
        if (spawnTimer <= 0) {
            spawnTimer = 0.40f + rnd.nextFloat() * 0.55f;
            spawnShell(w, h, density);
        }

        // ---- 推进弹体 ----
        Iterator<Shell> si = shells.iterator();
        while (si.hasNext()) {
            Shell s = si.next();
            s.y += s.vy * dt;
            s.vy += 90f * dt;      // 减速
            // 拖尾
            trailPaint.setColor(s.color);
            trailPaint.setAlpha(150);
            c.drawCircle(s.x, s.y, 1.8f * density, trailPaint);
            c.drawCircle(s.x, s.y + 6 * density, 1.2f * density, trailPaint);
            if (s.y <= s.targetY || Math.abs(s.vy) < 12f) {
                explode(s.x, s.y, s.color, density, w, h);
                si.remove();
            }
        }

        // ---- 推进粒子 ----
        Iterator<Particle> pi = particles.iterator();
        while (pi.hasNext()) {
            Particle p = pi.next();
            p.life -= dt;
            if (p.life <= 0) {
                pi.remove();
                continue;
            }
            p.px = p.x;
            p.py = p.y;
            p.x += p.vx * dt;
            p.y += p.vy * dt;
            p.vy += 150f * dt;         // 重力
            p.vx *= (1f - 2.2f * dt);  // 空气阻力
            p.vy *= (1f - 1.2f * dt);

            float k = p.life / p.maxLife;
            int alpha = (int) (255 * Math.min(1f, k * 1.6f));
            // 拖尾条纹
            dotPaint.setColor(p.color);
            dotPaint.setAlpha((int) (alpha * 0.5f));
            dotPaint.setStrokeWidth(p.size * 1.1f);
            dotPaint.setStyle(Paint.Style.STROKE);
            c.drawLine(p.px, p.py, p.x, p.y, dotPaint);
            dotPaint.setStyle(Paint.Style.FILL);
            dotPaint.setAlpha(alpha);
            c.drawCircle(p.x, p.y, p.size * (0.35f + 0.65f * k), dotPaint);

            if (p.size > 1.6f * density) {
                glowPaint.setColor(p.color);
                glowPaint.setAlpha((int) (60 * k));
                c.drawCircle(p.x, p.y, p.size * 3.2f, glowPaint);
            }
        }

        postInvalidateOnAnimation();
    }

    private void spawnShell(float w, float h, float density) {
        Shell s = new Shell();
        s.x = w * (0.12f + rnd.nextFloat() * 0.76f);
        s.y = h * (0.99f);
        s.vy = -(h * (0.55f + rnd.nextFloat() * 0.35f));   // 每秒上升几十个百分点
        s.targetY = h * (0.10f + rnd.nextFloat() * 0.38f); // 在屏幕上部某处炸开
        s.color = hueColor(rnd.nextInt(360));
        shells.add(s);
    }

    private void explode(float x, float y, int baseColor, float density, float w, float h) {
        int count = 46 + rnd.nextInt(28);
        float speed = w * 0.13f + rnd.nextFloat() * w * 0.06f;
        // 每次爆炸主色略作变化，看起来更像烟花
        int hueShift = rnd.nextInt(60) - 30;
        for (int i = 0; i < count; i++) {
            Particle p = new Particle();
            double ang = rnd.nextDouble() * Math.PI * 2;
            double r = Math.sqrt(rnd.nextDouble());
            p.x = x;
            p.y = y;
            p.px = x;
            p.py = y;
            p.vx = (float) Math.cos(ang) * speed * (float) r;
            p.vy = (float) Math.sin(ang) * speed * (float) r;
            p.maxLife = 1.1f + rnd.nextFloat() * 1.0f;
            p.life = p.maxLife;
            p.size = (2.0f + rnd.nextFloat() * 2.2f) * density;
            if (i % 9 == 0) {
                p.color = 0xFFFFFFFF;                 // 掺一点白色闪点
            } else {
                p.color = hueColor(hueOf(baseColor) + hueShift + (int) (rnd.nextFloat() * 24f - 12f));
            }
            particles.add(p);
        }
        // 中心闪光
        Particle core = new Particle();
        core.x = x;
        core.y = y;
        core.maxLife = 0.22f;
        core.life = core.maxLife;
        core.size = 9f * density;
        core.vx = core.vy = 0;
        core.color = 0xFFFFF6D8;
        particles.add(core);
    }

    private int hueColor(int hue) {
        float h = ((hue % 360) + 360) % 360;
        return Color.HSVToColor(new float[]{h, 0.85f, 1f});
    }

    private int hueOf(int color) {
        float[] hsv = new float[3];
        Color.colorToHSV(color, hsv);
        return (int) hsv[0];
    }
}

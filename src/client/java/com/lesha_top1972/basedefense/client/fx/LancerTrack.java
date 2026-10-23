package com.lesha_top1972.basedefense.client.fx;

import net.minecraft.util.math.Vec3d;

/**
 * Клиентская «история» одного улана за последние ~2 тика… 10 тиков: где было остриё копья
 * (для следа), где стоял он сам и куда смотрел (для фантомов‑послеобразов), где глаза (огонь 2‑й фазы).
 * Заполняется в {@code TatteredLancerRenderer} каждый кадр по настоящей позе модели.
 */
public final class LancerTrack {

    static final int N = 48;
    /** Новая точка — не чаще, чем раз в столько тиков. */
    private static final float STEP = 0.2f;

    final float[] time = new float[N];
    final Vec3d[] tip = new Vec3d[N];
    final Vec3d[] inner = new Vec3d[N];
    final Vec3d[] pos = new Vec3d[N];
    final float[] yaw = new float[N];
    int head = -1;
    int count;

    /** Последние положения (каждый кадр). */
    public Vec3d lastTip;
    public Vec3d eyeL;
    public Vec3d eyeR;
    public float lastTime = -1e9f;

    public void sample(float t, Vec3d p, float bodyYaw, Vec3d tipPos, Vec3d innerPos, Vec3d eyeLeft, Vec3d eyeRight) {
        this.lastTip = tipPos;
        this.eyeL = eyeLeft;
        this.eyeR = eyeRight;
        this.lastTime = t;
        if (this.count > 0) {
            float dt = t - this.time[this.head];
            if (dt < 0f || dt > 40f) {
                this.count = 0;                         // мир перезагрузился или долго не было видно
            } else if (dt < STEP) {
                return;
            }
        }
        this.head = (this.head + 1) % N;
        this.time[this.head] = t;
        this.tip[this.head] = tipPos;
        this.inner[this.head] = innerPos;
        this.pos[this.head] = p;
        this.yaw[this.head] = bodyYaw;
        this.count = Math.min(N, this.count + 1);
    }

    /** Индекс i‑й точки от новой к старой (0 — самая новая), или −1. */
    int index(int i) {
        if (i >= this.count) {
            return -1;
        }
        return ((this.head - i) % N + N) % N;
    }

    /** Самая новая точка не новее момента t (для фантома), или −1. */
    int atOrBefore(float t) {
        for (int i = 0; i < this.count; i++) {
            int k = this.index(i);
            if (this.time[k] <= t) {
                return k;
            }
        }
        return -1;
    }

    public Vec3d posAt(int k) {
        return this.pos[k];
    }

    public float yawAt(int k) {
        return this.yaw[k];
    }

    public int ghostIndex(float t) {
        return this.atOrBefore(t);
    }
}

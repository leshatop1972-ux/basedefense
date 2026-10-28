package com.lesha_top1972.basedefense.client.weapon;

import com.lesha_top1972.basedefense.combat.BloodSwordCombat;
import net.minecraft.util.math.MathHelper;

/**
 * Состояние анимации меча у одного игрока: замах, удар или отмена; время — тики клиента (с долей кадра).
 * Вес w: 0 — обычная поза с мечом в одной руке, 1 — поза из ключей (меч в обеих руках).
 */
final class BloodSwordAnim {

    static final int CHARGE = 1;
    static final int SLAM = 2;
    static final int CANCEL = 3;

    private static final float BLEND_IN = 4f;
    private static final float CANCEL_TICKS = 6f;
    private static final float SLAM_HOLD = 9f;

    int phase;
    float start;
    /** Насколько был поднят меч, когда замах отменили. */
    float from;
    /** Вес позы в момент отмены. */
    float fromWeight;
    boolean readyFx;

    /** Поза меча от 3‑го лица (система модели), её считает setAngles для рендера предмета. */
    final BloodSwordPose.Frame tpSword = new BloodSwordPose.Frame();
    float tpWeight;

    BloodSwordAnim(int phase, float start) {
        this.phase = phase;
        this.start = start;
    }

    float age(float now) {
        return now - this.start;
    }

    boolean finished(float now) {
        float s = age(now);
        return switch (this.phase) {
            case SLAM -> s >= BloodSwordCombat.SLAM_TICKS;
            case CANCEL -> s >= CANCEL_TICKS;
            default -> false;
        };
    }

    /** Доля замаха 0..1. */
    float charge(float now) {
        float s = age(now);
        return switch (this.phase) {
            case CHARGE -> MathHelper.clamp(s / BloodSwordCombat.CHARGE_TICKS, 0f, 1f);
            case CANCEL -> this.from * (1f - smooth(s / CANCEL_TICKS));
            default -> 1f;
        };
    }

    boolean ready(float now) {
        return this.phase == CHARGE && age(now) >= BloodSwordCombat.CHARGE_TICKS;
    }

    float weight(float now) {
        float s = age(now);
        return switch (this.phase) {
            case CHARGE -> smooth(s / BLEND_IN);
            case SLAM -> s < SLAM_HOLD ? 1f : 1f - smooth((s - SLAM_HOLD) / (BloodSwordCombat.SLAM_TICKS - SLAM_HOLD));
            case CANCEL -> (1f - smooth((s - 1f) / (CANCEL_TICKS - 1f))) * this.fromWeight;
            default -> 0f;
        };
    }

    /** Свечение клинка: сильнее по мере замаха, пульсирует, когда меч готов. */
    float glow(float now) {
        float s = age(now);
        return switch (this.phase) {
            case CHARGE -> ready(now) ? 0.85f + 0.15f * MathHelper.sin(now * 0.45f) : 0.5f + 0.4f * charge(now);
            case SLAM -> s < 3f ? 1f : 1f - 0.5f * smooth((s - 3f) / 11f);
            case CANCEL -> 0.5f + 0.4f * charge(now);
            default -> 0.5f;
        };
    }

    /** Ключи 3‑го лица в момент now → out (11 чисел). */
    float[] thirdPerson(float now, float[] out) {
        if (this.phase == SLAM) {
            return BloodSwordPose.sample(BloodSwordKeys.TP_SLAM, Math.min(age(now), SLAM_HOLD), out);
        }
        float p = charge(now);
        BloodSwordPose.sample(BloodSwordKeys.TP_CHARGE, p * (2f - p), out);
        if (ready(now)) {                       // меч поднят и ждёт: дышит, клинок чуть дрожит
            out[1] += 0.3f * MathHelper.sin(now * 0.22f);
            out[3] += 0.03f * MathHelper.sin(now * 1.9f);
        }
        return out;
    }

    /** Ключи 1‑го лица → out (13 чисел). */
    float[] firstPerson(float now, float[] out) {
        if (this.phase == SLAM) {
            return BloodSwordPose.sample(BloodSwordKeys.FP_SLAM, Math.min(age(now), SLAM_HOLD), out);
        }
        float p = charge(now);
        BloodSwordPose.sample(BloodSwordKeys.FP_CHARGE, p * (2f - p), out);
        if (ready(now)) {
            out[1] += 0.008f * MathHelper.sin(now * 0.22f);
            out[3] += 0.012f * MathHelper.sin(now * 1.9f);
        }
        return out;
    }

    /** Перейти в отмену из текущего состояния (меч плавно опускается). */
    void cancel(float now) {
        if (this.phase == CANCEL || this.phase == SLAM) {
            return;
        }
        this.from = charge(now);
        this.fromWeight = weight(now);
        this.phase = CANCEL;
        this.start = now;
    }

    static float smooth(float x) {
        x = MathHelper.clamp(x, 0f, 1f);
        return x * x * (3f - 2f * x);
    }
}

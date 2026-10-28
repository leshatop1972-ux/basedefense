package com.lesha_top1972.basedefense.client.entity.settler;

import com.lesha_top1972.basedefense.entity.settler.GuardEntity;
import net.minecraft.client.model.ModelPart;
import net.minecraft.util.math.MathHelper;

/**
 * Анимации стражника поверх обычной модели жителя (вызываются из SettlerEntityModel.setAngles).
 *
 * Поза — 9 чисел: правая рука (наклон, поворот, крен), левая рука (то же), поворот корпуса,
 * выпад (правая нога вперёд, левая назад) и наклон головы. Анимация — ключевые позы во времени;
 * между ключами — плавный переход (у самого удара — с разгоном, чтобы удар был резким).
 * Первый и последний ключ — «стойка», поэтому вход и выход из любого действия без рывков.
 *
 * Действия: два удара мечом по очереди (косой сверху справа налево и горизонтальный слева направо),
 * удар топором (двумя руками из‑за головы), выстрел из лука (стрела из колчана за спиной → на тетиву →
 * натяжение → выстрел с отмашкой), еда (еда в левой руке у рта, жуёт). В бою — боевая стойка.
 */
public final class GuardAnimations {

    private static final int RP = 0, RY = 1, RR = 2, LP = 3, LY = 4, LR = 5, BY = 6, LUNGE = 7, HP = 8;
    private static final int N = 9;

    private static final int SMOOTH = 0;
    private static final int IN = 1;     // разгон (удар)
    private static final int OUT = 2;    // торможение (после удара)

    /** Ключ: время (доля анимации), поза (NaN — «как в стойке»), плавность перехода к этому ключу. */
    private record Key(float t, float[] pose, int ease) {
    }

    private static final float S = Float.NaN;

    private GuardAnimations() {
    }

    private static Key key(float t, int ease, float... pose) {
        return new Key(t, pose, ease);
    }

    // ---------- Удары мечом ----------
    /** Косой удар сверху: замах над правым плечом, рубит вниз через корпус. */
    private static final Key[] SWORD_A = {
            key(0.00f, SMOOTH, S, S, S, S, S, S, S, S, S),
            key(0.36f, SMOOTH, -2.60f, -0.60f, -0.35f, -0.75f, 0.45f, -0.10f, -0.50f, 0.05f, -0.05f),
            key(0.52f, IN, -0.55f, 0.80f, 0.25f, -0.20f, 0.10f, -0.05f, 0.45f, 0.45f, 0.15f),
            key(0.66f, OUT, -0.40f, 1.00f, 0.30f, -0.15f, 0.08f, -0.05f, 0.55f, 0.40f, 0.12f),
            key(1.00f, SMOOTH, S, S, S, S, S, S, S, S, S),
    };
    /** Горизонтальный удар наотмашь: рука уходит влево за корпус, режет вправо. */
    private static final Key[] SWORD_B = {
            key(0.00f, SMOOTH, S, S, S, S, S, S, S, S, S),
            key(0.36f, SMOOTH, -1.30f, 1.10f, 0.45f, -0.30f, 0.20f, -0.05f, 0.50f, 0.05f, 0.00f),
            key(0.52f, IN, -1.35f, -0.90f, -0.25f, -0.65f, 0.45f, -0.15f, -0.50f, 0.45f, 0.05f),
            key(0.66f, OUT, -1.15f, -1.10f, -0.30f, -0.70f, 0.50f, -0.15f, -0.60f, 0.40f, 0.05f),
            key(1.00f, SMOOTH, S, S, S, S, S, S, S, S, S),
    };

    // ---------- Топор: тяжёлый удар двумя руками из‑за головы ----------
    private static final Key[] AXE = {
            key(0.00f, SMOOTH, S, S, S, S, S, S, S, S, S),
            key(0.40f, SMOOTH, -2.90f, -0.15f, -0.10f, -2.50f, 0.35f, 0.10f, -0.25f, -0.10f, -0.20f),
            key(0.50f, IN, -0.25f, 0.15f, 0.10f, -0.45f, 0.40f, 0.05f, 0.20f, 0.55f, 0.30f),
            key(0.58f, OUT, -0.50f, 0.10f, 0.08f, -0.60f, 0.35f, 0.05f, 0.18f, 0.50f, 0.25f),
            key(0.72f, SMOOTH, -0.45f, 0.05f, 0.05f, -0.55f, 0.30f, 0.00f, 0.10f, 0.40f, 0.15f),
            key(1.00f, SMOOTH, S, S, S, S, S, S, S, S, S),
    };

    // ---------- Еда: левая рука с едой у рта ----------
    private static final Key[] EAT = {
            key(0.00f, SMOOTH, S, S, S, S, S, S, S, S, S),
            key(0.15f, SMOOTH, -0.25f, 0.00f, 0.08f, -1.75f, 0.55f, 0.00f, 0.00f, 0.00f, 0.12f),
            key(0.80f, SMOOTH, -0.25f, 0.00f, 0.08f, -1.75f, 0.55f, 0.00f, 0.00f, 0.00f, 0.12f),
            key(1.00f, SMOOTH, S, S, S, S, S, S, S, S, S),
    };

    /**
     * @param ap   age + tickDelta
     * @param walk сила шага 0..1
     * @param swing текущий размах шага (cos фазы × сила), для покачивания рук в стойке
     */
    public static void apply(SettlerEntityModel m, GuardEntity guard, float ap, float walk, float swing) {
        ModelPart head = m.head();
        ModelPart rightArm = m.rightArm();
        ModelPart leftArm = m.leftArm();
        float tickDelta = MathHelper.clamp(ap - guard.age, 0.0f, 1.0f);
        float combat = guard.getCombatBlend(tickDelta);
        GuardEntity.Weapon weapon = GuardEntity.weaponOf(guard.getMainHandStack());

        // --- Стойка: обычная поза шага, в бою — смешивается с боевой ---
        float[] base = new float[N];
        base[RP] = rightArm.pitch;
        base[RY] = rightArm.yaw;
        base[RR] = rightArm.roll;
        base[LP] = leftArm.pitch;
        base[LY] = leftArm.yaw;
        base[LR] = leftArm.roll;
        base[BY] = m.body().yaw;
        if (weapon != GuardEntity.Weapon.NONE) {
            base[RP] = base[RP] * 0.5f - 0.31f;   // держит оружие — рука чуть вперёд
        }
        float[] stance = base.clone();
        if (combat > 0.0f && weapon != GuardEntity.Weapon.NONE) {
            float breath = MathHelper.sin(ap * 0.15f) * 0.04f;
            float[] fight = new float[N];
            switch (weapon) {
                case BOW -> {
                    fight[RP] = -0.55f + swing * 0.2f + breath;
                    fight[RY] = 0.10f;
                    fight[RR] = 0.0f;
                    fight[LP] = -0.25f - swing * 0.3f;
                    fight[LY] = 0.0f;
                    fight[LR] = -0.08f;
                    fight[BY] = 0.0f;
                }
                case AXE -> {
                    fight[RP] = -0.95f + swing * 0.2f + breath;
                    fight[RY] = 0.05f;
                    fight[RR] = 0.20f;
                    fight[LP] = -0.70f - swing * 0.2f + breath;
                    fight[LY] = 0.30f;
                    fight[LR] = -0.05f;
                    fight[BY] = -0.10f;
                }
                default -> {
                    fight[RP] = -0.85f + swing * 0.25f + breath;
                    fight[RY] = -0.10f;
                    fight[RR] = 0.12f;
                    fight[LP] = -0.35f - swing * 0.4f;
                    fight[LY] = 0.15f;
                    fight[LR] = -0.10f;
                    fight[BY] = -0.12f;
                }
            }
            for (int i = 0; i < N; i++) {
                stance[i] = MathHelper.lerp(combat, base[i], fight[i]);
            }
            stance[LUNGE] = 0.12f * combat * (1.0f - walk);   // ноги шире — стоит в стойке
        }

        // --- Действие ---
        float[] pose = stance;
        int action = guard.getClientAction();
        float t = (ap - guard.getClientActionStart()) / GuardEntity.actionLength(action);
        if (action != GuardEntity.ACT_NONE && t >= 0.0f && t < 1.0f) {
            pose = switch (action) {
                case GuardEntity.ACT_SWORD -> sample(t, stance, (guard.getClientActionSeq() & 1) == 0 ? SWORD_A : SWORD_B);
                case GuardEntity.ACT_AXE -> sample(t, stance, AXE);
                case GuardEntity.ACT_BOW -> sample(t, stance, bowKeys(head.pitch, head.yaw));
                case GuardEntity.ACT_EAT -> {
                    float[] p = sample(t, stance, EAT);
                    // Жуёт: рука и голова мелко двигаются
                    float chew = (t > 0.15f && t < 0.8f) ? MathHelper.sin(t * 46.0f) : 0.0f;
                    p[LP] += chew * 0.08f;
                    p[HP] += chew * 0.06f;
                    yield p;
                }
                default -> stance;
            };
        }
        applyPose(m, pose, walk);
    }

    /** Ключи выстрела: руки целятся туда, куда смотрит голова (headPitch/headYaw — уже в радианах). */
    private static Key[] bowKeys(float hp, float hy) {
        float aim = -MathHelper.HALF_PI + hp;
        return new Key[]{
                key(0.00f, SMOOTH, S, S, S, S, S, S, S, S, S),
                // Левая рука — за стрелой в колчан за спиной, лук поднимается
                key(0.17f, SMOOTH, aim * 0.55f, hy - 0.10f, 0.0f, -2.60f, 0.50f, -0.20f, 0.10f, 0.05f, 0.0f),
                // Стрела на тетиве: обе руки впереди
                key(0.30f, OUT, aim, hy - 0.10f, 0.0f, aim, hy + 0.10f, 0.0f, 0.10f, 0.10f, 0.0f),
                // Натяжение: левая рука тянет тетиву к лицу, корпус боком
                key(0.70f, SMOOTH, aim, hy - 0.12f, 0.0f, aim - 0.05f, hy + 0.65f, 0.0f, 0.20f, 0.15f, 0.0f),
                // Выстрел: левая рука отлетает назад, лук чуть подбрасывает
                key(0.74f, IN, aim + 0.12f, hy - 0.10f, 0.0f, aim + 0.25f, hy + 1.00f, -0.25f, 0.20f, 0.15f, -0.03f),
                key(0.86f, OUT, aim + 0.08f, hy - 0.10f, 0.0f, aim + 0.40f, hy + 1.05f, -0.30f, 0.18f, 0.12f, 0.0f),
                key(1.00f, SMOOTH, S, S, S, S, S, S, S, S, S),
        };
    }

    private static float ease(float x, int type) {
        return switch (type) {
            case IN -> x * x * x;
            case OUT -> 1.0f - (1.0f - x) * (1.0f - x) * (1.0f - x);
            default -> x * x * (3.0f - 2.0f * x);
        };
    }

    private static float[] sample(float t, float[] stance, Key[] keys) {
        int i = 0;
        while (i < keys.length - 2 && t > keys[i + 1].t()) {
            i++;
        }
        Key a = keys[i];
        Key b = keys[i + 1];
        float x = b.t() > a.t() ? MathHelper.clamp((t - a.t()) / (b.t() - a.t()), 0.0f, 1.0f) : 1.0f;
        float e = ease(x, b.ease());
        float[] out = new float[N];
        for (int k = 0; k < N; k++) {
            float va = Float.isNaN(a.pose()[k]) ? stance[k] : a.pose()[k];
            float vb = Float.isNaN(b.pose()[k]) ? stance[k] : b.pose()[k];
            out[k] = va + (vb - va) * e;
        }
        return out;
    }

    private static void applyPose(SettlerEntityModel m, float[] p, float walk) {
        ModelPart body = m.body();
        ModelPart rightArm = m.rightArm();
        ModelPart leftArm = m.leftArm();
        float by = p[BY];
        body.yaw = by;
        // Плечи поворачиваются вместе с корпусом
        rightArm.pivotZ = MathHelper.sin(by) * 5.0f;
        rightArm.pivotX = -MathHelper.cos(by) * 5.0f;
        leftArm.pivotZ = -MathHelper.sin(by) * 5.0f;
        leftArm.pivotX = MathHelper.cos(by) * 5.0f;
        rightArm.pitch = p[RP];
        rightArm.yaw = p[RY] + by;
        rightArm.roll = p[RR];
        leftArm.pitch = p[LP];
        leftArm.yaw = p[LY] + by;
        leftArm.roll = p[LR];
        // Выпад: правая нога вперёд, левая назад (шаг при ходьбе приглушается)
        float lunge = p[LUNGE];
        float keep = 1.0f - MathHelper.clamp(Math.abs(lunge) * 1.2f, 0.0f, 0.7f);
        m.rightLeg().pitch = m.rightLeg().pitch * keep - lunge;
        m.leftLeg().pitch = m.leftLeg().pitch * keep + lunge * 0.7f;
        m.rightLeg().roll = 0.05f * Math.min(1.0f, Math.abs(lunge) * 4.0f) * (1.0f - walk);
        m.leftLeg().roll = -m.rightLeg().roll;
        m.head().pitch += p[HP];
    }
}

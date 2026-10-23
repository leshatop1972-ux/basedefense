package com.lesha_top1972.basedefense.client.entity;

import com.lesha_top1972.basedefense.client.entity.bedrock.BedrockAnimations;
import com.lesha_top1972.basedefense.client.entity.bedrock.BedrockEntityModel;
import com.lesha_top1972.basedefense.entity.IronKnightEntity;
import net.minecraft.client.model.ModelPart;

import java.util.Map;

/**
 * Модель железного рыцаря из Blockbench (geo/iron_knight.geo.json).
 * <ul>
 *   <li>idle / walk / run — плавно сменяются по скорости движения;</li>
 *   <li>attack1 (удар с замахом) — ускорен в 2.5 раза: 2.95 с → 1.18 с, попадание на ~0.7 с;</li>
 *   <li>attack2 (вращение) разрезан: замах 0–0.667 с, затем 3 с вращения (поза из середины
 *       анимации, поворот корня считается кодом, 8 оборотов), затем возврат 1.5–2.65 с (ускорен в 1.5 раза).</li>
 * </ul>
 * Тайминги совпадают с IronKnightEntity (SWING_*, SPIN_*).
 */
public class IronKnightModel extends BedrockEntityModel<IronKnightEntity> {

    private static final Map<String, BedrockAnimations.Clip> CLIPS = BedrockAnimations.load("iron_knight");
    private static final BedrockAnimations.Clip IDLE = CLIPS.get("idle");
    private static final BedrockAnimations.Clip WALK = CLIPS.get("walk");
    private static final BedrockAnimations.Clip RUN = CLIPS.get("run");
    private static final BedrockAnimations.Clip SWING = CLIPS.get("attack1");
    private static final BedrockAnimations.Clip SPIN = CLIPS.get("attack2");

    private static final float WALK_SCALE = 2.2f;
    private static final float RUN_SCALE = 1.0f;
    /** Ускорение удара мечом. */
    public static final float SWING_SPEED = 2.5f;

    /** Части анимации вращения (секунды анимации). */
    private static final float SPIN_ANIM_WINDUP_END = 0.667f;
    private static final float SPIN_ANIM_HOLD = 1.0f;
    private static final float SPIN_ANIM_RECOVER = 1.5f;

    /** Части вращения в реальном времени (секунды) — из тиков сущности. */
    private static final float WINDUP_S = IronKnightEntity.SPIN_WINDUP / 20f;
    private static final float SPIN_S = IronKnightEntity.SPIN_TICKS / 20f;
    private static final float RECOVER_S = IronKnightEntity.SPIN_RECOVER / 20f;
    /** Поворот за всё вращение: от −20° до −2880° (8 оборотов) — стык с началом возврата. */
    private static final float SPIN_DEGREES = 2860f;

    private final ModelPart head;
    private final ModelPart base;

    public IronKnightModel(ModelPart root) {
        super(root);
        this.head = this.bone("head");
        this.base = this.bone("bone_root");
    }

    @Override
    public void setAngles(IronKnightEntity entity, float limbAngle, float limbDistance, float animationProgress,
                          float headYaw, float headPitch) {
        this.resetPose();

        // --- удар мечом ---
        float swingT = -1f;
        float swingW = 0f;
        entity.swingState.update(animationProgress, SWING_SPEED);
        if (entity.swingState.isRunning() && SWING != null) {
            swingT = entity.swingState.getTimeRunning() / 1000f;
            if (swingT > SWING.length()) {
                entity.swingState.stop();
                swingT = -1f;
            } else {
                swingW = clamp01(Math.min(swingT / 0.25f, (SWING.length() - swingT) / 0.35f));
            }
        }

        // --- вращение ---
        float spinAnimT = -1f;
        float spinW = 0f;
        float rootYaw = Float.NaN;
        float st = (animationProgress - entity.spinStartAge) / 20f;
        float total = WINDUP_S + SPIN_S + RECOVER_S;
        if (SPIN != null && st >= 0 && st < total) {
            if (st < WINDUP_S) {
                spinAnimT = st / WINDUP_S * SPIN_ANIM_WINDUP_END;
            } else if (st < WINDUP_S + SPIN_S) {
                float s = st - WINDUP_S;
                if (s < SPIN_ANIM_HOLD - SPIN_ANIM_WINDUP_END) {
                    spinAnimT = SPIN_ANIM_WINDUP_END + s;
                } else if (s > SPIN_S - (SPIN_ANIM_RECOVER - SPIN_ANIM_HOLD)) {
                    spinAnimT = SPIN_ANIM_RECOVER - (SPIN_S - s);
                } else {
                    spinAnimT = SPIN_ANIM_HOLD;
                }
                rootYaw = -(20f + s / SPIN_S * SPIN_DEGREES) * DEG;
            } else {
                float s = st - WINDUP_S - SPIN_S;
                spinAnimT = SPIN_ANIM_RECOVER + s / RECOVER_S * (SPIN.length() - SPIN_ANIM_RECOVER);
            }
            spinW = clamp01(Math.min(st / 0.15f, (total - st) / 0.2f));
        }

        float attackW = Math.max(swingW, spinW);
        float free = 1f - attackW;

        // --- ходьба / бег / покой ---
        float move = clamp01(limbDistance * 2.5f);
        float run = clamp01((limbDistance - 0.55f) / 0.35f);
        this.play(IDLE, animationProgress / 20f, free * (1f - move));
        this.play(WALK, limbAngle * 0.05f * WALK_SCALE, free * move * (1f - run));
        this.play(RUN, limbAngle * 0.05f * RUN_SCALE, free * move * run);

        if (swingT >= 0) {
            this.play(SWING, swingT, swingW * (1f - spinW));
        }
        if (spinAnimT >= 0) {
            this.play(SPIN, spinAnimT, spinW);
            if (!Float.isNaN(rootYaw)) {
                this.base.yaw = rootYaw;
            }
        }

        this.head.yaw += headYaw * DEG * (1f - spinW);
        this.head.pitch += headPitch * DEG * (1f - spinW);
    }
}

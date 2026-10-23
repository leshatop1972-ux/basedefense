package com.lesha_top1972.basedefense.client.entity;

import com.lesha_top1972.basedefense.client.entity.bedrock.BedrockAnimations;
import com.lesha_top1972.basedefense.client.entity.bedrock.BedrockEntityModel;
import com.lesha_top1972.basedefense.entity.FrostArcherEntity;
import net.minecraft.client.model.ModelPart;
import net.minecraft.util.math.MathHelper;

import java.util.Map;

/**
 * Модель морозной лучницы (geo/frost_archer.geo.json, создаётся tools/frost_archer_gen.py).
 * Анимации: idle (парение), move (полёт вперёд), move_back (полёт спиной), attack
 * (стрела из колчана на бедре → лук «на боку» → натяжение → выстрел → отлёт с оборотом вокруг себя → покой).
 * Стрела в руке видна, пока лучница достаёт её из колчана, на тетиве — пока натягивает; в остальное время
 * стрел нет (модель и анимации — tools/frost_archer_gen.py).
 */
public class FrostArcherModel extends BedrockEntityModel<FrostArcherEntity> {

    private static final Map<String, BedrockAnimations.Clip> CLIPS = BedrockAnimations.load("frost_archer");
    private static final BedrockAnimations.Clip IDLE = CLIPS.get("idle");
    private static final BedrockAnimations.Clip MOVE = CLIPS.get("move");
    private static final BedrockAnimations.Clip MOVE_BACK = CLIPS.get("move_back");
    private static final BedrockAnimations.Clip ATTACK = CLIPS.get("attack");

    /** Секунды анимации attack (как в генераторе): стрела в руке REACH..NOCK, на тетиве NOCK..выстрел. */
    private static final float REACH = 0.35f;
    private static final float NOCK = 0.85f;
    private static final float RELEASE = FrostArcherEntity.RELEASE_TICK / 20f;
    private static final float ATTACK_LEN = FrostArcherEntity.ATTACK_TICKS / 20f;

    private final ModelPart head;
    private final ModelPart bowArrow;
    private final ModelPart handArrow;

    public FrostArcherModel(ModelPart root) {
        super(root);
        this.head = this.bone("head");
        this.bowArrow = this.bone("bow_arrow");
        this.handArrow = this.bone("hand_arrow");
        this.bowArrow.visible = false;
        this.handArrow.visible = false;
    }

    @Override
    public void setAngles(FrostArcherEntity entity, float limbAngle, float limbDistance, float animationProgress,
                          float headYaw, float headPitch) {
        this.resetPose();
        float time = animationProgress / 20f;

        // атака
        float st = (animationProgress - entity.attackStartAge) / 20f;
        float attackW = 0f;
        boolean attacking = ATTACK != null && st >= 0 && st < ATTACK_LEN;
        if (attacking) {
            attackW = clamp01(Math.min(st / 0.1f, (ATTACK_LEN - st) / 0.15f));
        }
        float free = 1f - attackW;

        // движение: вперёд или спиной (по направлению скорости относительно тела)
        double dx = entity.getX() - entity.prevX;
        double dz = entity.getZ() - entity.prevZ;
        double speed = Math.sqrt(dx * dx + dz * dz);
        float yaw = entity.bodyYaw * MathHelper.RADIANS_PER_DEGREE;
        double dot = speed > 1.0e-4 ? (dx * -MathHelper.sin(yaw) + dz * MathHelper.cos(yaw)) / speed : 1.0;
        float back = clamp01((float) (-dot * 1.5));
        float move = clamp01(limbDistance * 3f);

        this.play(IDLE, time, free * (1f - move));
        this.play(MOVE, time, free * move * (1f - back));
        this.play(MOVE_BACK, time, free * move * back);
        if (attacking) {
            this.play(ATTACK, st, attackW);
        }

        this.handArrow.visible = attacking && st >= REACH && st < NOCK;
        this.bowArrow.visible = attacking && st >= NOCK && st < RELEASE;

        this.head.yaw += headYaw * DEG * free;
        this.head.pitch += headPitch * DEG * free;
    }
}

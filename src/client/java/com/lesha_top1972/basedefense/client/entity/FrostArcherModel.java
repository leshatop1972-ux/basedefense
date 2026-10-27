package com.lesha_top1972.basedefense.client.entity;

import com.lesha_top1972.basedefense.client.entity.bedrock.BedrockAnimations;
import com.lesha_top1972.basedefense.client.entity.bedrock.BedrockEntityModel;
import com.lesha_top1972.basedefense.entity.FrostArcherEntity;
import com.lesha_top1972.basedefense.client.entity.bedrock.WeaponTrack;
import com.lesha_top1972.basedefense.client.entity.nabat.WeaponHolder;
import com.lesha_top1972.basedefense.client.entity.nabat.WeaponRig;
import net.minecraft.client.model.ModelPart;
import org.joml.Quaternionf;
import net.minecraft.util.math.MathHelper;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Модель морозной лучницы (geo/frost_archer.geo.json, создаётся tools/frost_archer_gen.py).
 * Анимации: idle (парение), move (полёт вперёд), move_back (полёт спиной), attack
 * (стрела из колчана на бедре → лук «на боку» → натяжение → выстрел → отлёт с оборотом вокруг себя → покой).
 * Стрела в руке видна, пока лучница достаёт её из колчана, на тетиве — пока натягивает; в остальное время
 * стрел нет (модель и анимации — tools/frost_archer_gen.py).
 * Лук (с тетивой и стрелами на нём) — отдельная модель frost_archer_bow (tools/weaponize.py): поворот из дорожки
 * оружия, тетива и стрела — клипы оружия. «Ледяной веер» (volley) — три стрелы веером, натяжение дольше.
 */
public class FrostArcherModel extends BedrockEntityModel<FrostArcherEntity> implements WeaponHolder {

    private static final Map<String, BedrockAnimations.Clip> CLIPS = BedrockAnimations.load("frost_archer");
    private static final BedrockAnimations.Clip IDLE = CLIPS.get("idle");
    private static final BedrockAnimations.Clip MOVE = CLIPS.get("move");
    private static final BedrockAnimations.Clip MOVE_BACK = CLIPS.get("move_back");
    private static final BedrockAnimations.Clip ATTACK = CLIPS.get("attack");
    private static final BedrockAnimations.Clip VOLLEY = CLIPS.get("volley");
    private static final Map<String, BedrockAnimations.Clip> BOW_CLIPS = BedrockAnimations.load("frost_archer_bow");
    private static final float VOLLEY_RELEASE = FrostArcherEntity.VOLLEY_RELEASE / 20f;
    private static final float VOLLEY_LEN = FrostArcherEntity.VOLLEY_TICKS / 20f;

    /** Секунды анимации attack (как в генераторе): стрела в руке REACH..NOCK, на тетиве NOCK..выстрел. */
    private static final float REACH = 0.35f;
    private static final float NOCK = 0.85f;
    private static final float RELEASE = FrostArcherEntity.RELEASE_TICK / 20f;
    private static final float ATTACK_LEN = FrostArcherEntity.ATTACK_TICKS / 20f;

    private final ModelPart head;
    private final ModelPart handArrow;
    private final ModelPart handArrowFan;
    private final WeaponTrack bow = new WeaponTrack("frost_archer_bow");
    private final Quaternionf bowRot = new Quaternionf();
    private final ModelPart[] chain;
    private final List<Object[]> frame = new ArrayList<>();
    private boolean arrowOnBow;
    private boolean fanOnBow;
    private WeaponRig rig;

    public FrostArcherModel(ModelPart root) {
        super(root);
        this.head = this.bone("head");
        this.handArrow = this.bone("hand_arrow");
        this.handArrowFan = this.bone("hand_arrow_fan");
        this.handArrow.visible = false;
        this.handArrowFan.visible = false;
        this.chain = new ModelPart[]{root, this.bone("bone_root"), this.bone("body"), this.bone("torso"),
                this.bone("left_arm"), this.bone("left_fore")};
    }

    @Override
    public WeaponTrack weaponTrack() {
        return this.bow;
    }

    @Override
    public ModelPart[] handChain() {
        return this.chain;
    }

    @Override
    public Quaternionf weaponRotation() {
        return this.bowRot;
    }

    @Override
    public void poseWeapon(int slot, ModelPart weaponRoot) {
        if (this.rig == null || this.rig.getPart() != weaponRoot) {
            this.rig = new WeaponRig(weaponRoot);
        }
        this.rig.reset();
        for (Object[] f : this.frame) {
            BedrockAnimations.Clip c = BOW_CLIPS.get((String) f[0]);
            if (c != null) {
                this.rig.playClip(c, (Float) f[1], (Float) f[2]);
            }
        }
        if (this.rig.has("bow_arrow")) {
            this.rig.part("bow_arrow").visible = this.arrowOnBow;
            this.rig.part("bow_arrow_fan").visible = this.fanOnBow;
        }
    }

    /** Клип тела + та же точка в дорожке лука. */
    private void playW(String name, BedrockAnimations.Clip clip, float t, float w) {
        if (clip == null || w <= 0.001f) {
            return;
        }
        this.play(clip, t, w);
        this.bow.add(name, t, w);
        this.frame.add(new Object[]{name, t, w});
    }

    @Override
    public void setAngles(FrostArcherEntity entity, float limbAngle, float limbDistance, float animationProgress,
                          float headYaw, float headPitch) {
        this.resetPose();
        this.bow.reset();
        this.frame.clear();
        float time = animationProgress / 20f;

        // атака
        float st = (animationProgress - entity.attackStartAge) / 20f;
        boolean volley = entity.attackVolley;
        float len = volley ? VOLLEY_LEN : ATTACK_LEN;
        float release = volley ? VOLLEY_RELEASE : RELEASE;
        float attackW = 0f;
        boolean attacking = ATTACK != null && st >= 0 && st < len;
        if (attacking) {
            attackW = clamp01(Math.min(st / 0.1f, (len - st) / 0.15f));
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

        this.playW("idle", IDLE, time, free * (1f - move));
        this.playW("move", MOVE, time, free * move * (1f - back));
        this.playW("move_back", MOVE_BACK, time, free * move * back);
        if (attacking) {
            if (volley) {
                this.playW("volley", VOLLEY, st, attackW);
            } else {
                this.playW("attack", ATTACK, st, attackW);
            }
        }
        this.bow.result(this.bowRot);

        this.handArrow.visible = attacking && st >= REACH && st < NOCK;
        this.handArrowFan.visible = this.handArrow.visible && volley;
        this.arrowOnBow = attacking && st >= NOCK && st < release;
        this.fanOnBow = this.arrowOnBow && volley;

        this.head.yaw += headYaw * DEG * free;
        this.head.pitch += headPitch * DEG * free;
    }
}

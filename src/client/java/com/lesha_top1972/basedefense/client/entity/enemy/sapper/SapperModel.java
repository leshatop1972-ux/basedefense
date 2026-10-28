package com.lesha_top1972.basedefense.client.entity.enemy.sapper;

import com.lesha_top1972.basedefense.client.entity.bedrock.BedrockAnimations;
import com.lesha_top1972.basedefense.client.entity.bedrock.BedrockEntityModel;
import com.lesha_top1972.basedefense.client.entity.bedrock.HeadLook;
import com.lesha_top1972.basedefense.client.entity.bedrock.WeaponTrack;
import com.lesha_top1972.basedefense.client.entity.bedrock.WeaponHolder;
import com.lesha_top1972.basedefense.entity.enemy.sapper.SapperEntity;
import net.minecraft.client.model.ModelPart;
import net.minecraft.util.math.MathHelper;
import org.joml.Quaternionf;

import java.util.Map;

/**
 * Модель подкопщика (geo/sapper.geo.json, клипы — tools/sapper_gen.py). Циклы смешиваются по скорости шага
 * (idle / sneak / walk / run, под землёй — tunnel, в засаде — hidden), действия — по статусу с сервера.
 * Кирка — отдельная модель по дорожке sapper.weapon.json; бомба и шашка в левой руке видны только в своих клипах;
 * огонёк свечи гаснет плавно (в засаде).
 */
public class SapperModel extends BedrockEntityModel<SapperEntity> implements WeaponHolder {

    private static final String[] ACTIONS = {"dig_down", "emerge", "hide", "throw", "plant", "jab", "stagger", "smoke", "dig_down"};
    /** Нырок в открытый лаз — клип dig_down с этого момента. */
    private static final float DIVE_FROM = 0.95f;

    private final Map<String, BedrockAnimations.Clip> clips;
    private final WeaponTrack weapon = new WeaponTrack("sapper");
    private final Quaternionf weaponRot = new Quaternionf();
    private final ModelPart[] chain;
    private final ModelPart head;
    private final ModelPart flame;
    private final ModelPart heldBomb;
    private final ModelPart heldCharge;

    public SapperModel(ModelPart root) {
        super(root);
        this.clips = BedrockAnimations.load("sapper");
        this.chain = new ModelPart[]{root, this.bone("bone_root"), this.bone("body"), this.bone("torso"),
                this.bone("right_arm"), this.bone("right_fore"), this.bone("right_hand")};
        this.head = this.bone("head");
        this.flame = this.bone("candle_flame");
        this.heldBomb = this.bone("held_bomb");
        this.heldCharge = this.bone("held_charge");
    }

    @Override
    public WeaponTrack weaponTrack() {
        return this.weapon;
    }

    @Override
    public ModelPart[] handChain() {
        return this.chain;
    }

    @Override
    public Quaternionf weaponRotation() {
        return this.weaponRot;
    }

    private void play2(String name, float t, float w) {
        BedrockAnimations.Clip c = this.clips.get(name);
        if (c != null && w > 0.001f) {
            this.play(c, t, w);
            this.weapon.add(name, t, w);
        }
    }

    private static void show(ModelPart p, boolean on) {
        float s = on ? 1f : 0f;
        p.xScale = s;
        p.yScale = s;
        p.zScale = s;
    }

    @Override
    public void setAngles(SapperEntity e, float limbAngle, float limbDistance, float animationProgress,
                          float headYaw, float headPitch) {
        this.resetPose();
        this.weapon.reset();
        String clip = null;
        float ct = 0f;
        float w = 0f;
        int a = e.clientAction;
        float t = (animationProgress - e.clientActionAge) / 20f;
        BedrockAnimations.Clip c = a >= 0 && a < ACTIONS.length ? this.clips.get(ACTIONS[a]) : null;
        boolean under = e.isUnder();
        if (c != null && t >= 0) {
            float off = a == SapperEntity.DIVE ? DIVE_FROM : 0f;
            float tt = t + off;
            boolean diving = a == SapperEntity.DIG_DOWN || a == SapperEntity.DIVE;
            if (tt < c.length() || diving && !under && t < 3f) {
                clip = ACTIONS[a];
                ct = Math.min(tt, c.length());
                float in = a == SapperEntity.EMERGE || a == SapperEntity.DIVE ? 1f : clamp01(t / 0.08f);
                float out = diving ? 1f : clamp01((c.length() - tt) / 0.2f);
                w = Math.min(in, out);
            }
        }
        float free = 1f - w;
        if (free > 0.001f) {
            float sec = animationProgress / 20f;
            if (e.isHidden()) {
                this.play2("hidden", sec, free);
            } else if (under) {
                this.play2("tunnel", limbAngle * 0.35f, free);
            } else {
                float move = clamp01(limbDistance * 2.5f);
                float runK = clamp01((limbDistance - 0.5f) / 0.3f);
                this.play2("idle", sec, free * (1f - move));
                this.play2(e.isSneakingUp() ? "sneak" : "walk", limbAngle * (e.isSneakingUp() ? 0.1f : 0.12f),
                        free * move * (1f - runK));
                this.play2("run", limbAngle * 0.05f, free * move * runK);
            }
        }
        if (clip != null) {
            this.play2(clip, ct, w);
        }
        this.weapon.result(this.weaponRot);
        HeadLook.apply(this.head, headYaw, headPitch, free * 0.6f);
        // предметы в левой руке
        boolean bomb = clip != null && (a == SapperEntity.THROW && ct >= 0.12f && ct <= 0.55f
                || a == SapperEntity.SMOKE && ct >= 0.10f && ct <= 0.30f);
        boolean charge = clip != null && a == SapperEntity.PLANT && ct <= 0.85f;
        show(this.heldBomb, bomb);
        show(this.heldCharge, charge);
        // огонёк свечи: гаснет плавно, мерцает
        float delta = animationProgress - e.age;
        float k = MathHelper.lerp(MathHelper.clamp(delta, 0f, 1f), e.prevCandle, e.candle);
        float fl = 1f + 0.12f * MathHelper.sin(animationProgress * 1.7f) + 0.07f * MathHelper.sin(animationProgress * 4.3f);
        this.flame.xScale = k * (2f - fl);
        this.flame.zScale = k * (2f - fl);
        this.flame.yScale = k * fl;
    }
}

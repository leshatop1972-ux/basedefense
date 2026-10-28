package com.lesha_top1972.basedefense.client.entity.enemy.bloodthirster;

import com.lesha_top1972.basedefense.client.entity.bedrock.BedrockAnimations;
import com.lesha_top1972.basedefense.client.entity.bedrock.BedrockEntityModel;
import com.lesha_top1972.basedefense.client.entity.bedrock.HeadLook;
import com.lesha_top1972.basedefense.client.entity.bedrock.WeaponTrack;
import com.lesha_top1972.basedefense.client.entity.bedrock.WeaponHolder;
import com.lesha_top1972.basedefense.entity.enemy.bloodthirster.BloodthirsterEntity;
import net.minecraft.client.model.ModelPart;
import org.joml.Quaternionf;

import java.util.Map;

import static com.lesha_top1972.basedefense.entity.enemy.bloodthirster.BloodthirsterEntity.*;

/**
 * Модель босса «Кровожад» (geo/bloodthirster.geo.json, создаёт tools/bloodthirster_gen.py).
 * Движение по фазе: idle1/walk1/run1 (сгорбленный) или idle2/walk2/run2 (выпрямленный, с мечами).
 * Атаки по статусу с сервера; бросок — pounce_start → pounce_air (до приземления) → pounce_land,
 * «багровая казнь» — exec_start → exec_air (держит последнюю позу до приземления) → exec_land.
 * Мечи — отдельная модель (geo/bloodthirster_sword): в каждой руке своя дорожка оружия
 * (animations/bloodthirster_r/l.weapon.json), повороты смешиваются кватернионами — мечи не «прокручиваются».
 * Мечи и венец видны только во 2‑й фазе (и с середины смены облика; в смене облика мечи вырастают).
 */
public class BloodthirsterModel extends BedrockEntityModel<BloodthirsterEntity> implements WeaponHolder {

    private static final Map<String, BedrockAnimations.Clip> C = BedrockAnimations.load("bloodthirster");
    private static final String[] IDLE = {"idle1", "idle2"};
    private static final String[] WALK = {"walk1", "walk2"};
    private static final String[] RUN = {"run1", "run2"};
    private static final String[] ACTIONS = {
            "swing", "rift", null, "pounce_land", "transform", "slash", "thrust", "summon", "eggs", null, "exec_land"
    };
    private static final BedrockAnimations.Clip EXEC_AIR = C.get("exec_air");
    private static final float POUNCE_START_LEN = POUNCE_WINDUP / 20f;
    private static final float EXEC_START_LEN = EXEC_WINDUP / 20f;
    /** С этого момента смены облика видны мечи и венец (они вырастают в анимации). */
    private static final float SWORDS_APPEAR = 2.0f;

    private final ModelPart head;
    private final ModelPart halo;
    private final WeaponTrack[] tracks = {new WeaponTrack("bloodthirster_r"), new WeaponTrack("bloodthirster_l")};
    private final Quaternionf[] rots = {new Quaternionf(), new Quaternionf()};
    private final float[] scales = {1f, 1f};
    private final ModelPart[][] chains;
    private boolean blades;

    public BloodthirsterModel(ModelPart root) {
        super(root);
        this.head = this.bone("head");
        this.halo = this.bone("halo");
        ModelPart br = this.bone("bone_root");
        ModelPart body = this.bone("body");
        ModelPart torso = this.bone("torso");
        this.chains = new ModelPart[][]{
                {root, br, body, torso, this.bone("right_arm"), this.bone("right_fore"), this.bone("right_hand")},
                {root, br, body, torso, this.bone("left_arm"), this.bone("left_fore"), this.bone("left_hand")}};
    }

    // --- WeaponHolder: слот 0 — правый меч, 1 — левый ---
    @Override
    public WeaponTrack weaponTrack() {
        return this.tracks[0];
    }

    @Override
    public ModelPart[] handChain() {
        return this.chains[0];
    }

    @Override
    public Quaternionf weaponRotation() {
        return this.rots[0];
    }

    @Override
    public WeaponTrack weaponTrack(int slot) {
        return this.tracks[slot];
    }

    @Override
    public ModelPart[] handChain(int slot) {
        return this.chains[slot];
    }

    @Override
    public Quaternionf weaponRotation(int slot) {
        return this.rots[slot];
    }

    @Override
    public boolean weaponVisible(int slot) {
        return this.blades;
    }

    @Override
    public float weaponScale(int slot) {
        return this.scales[slot];
    }

    /** Клип тела + та же точка в дорожках обоих мечей. */
    private void playW(String name, float t, float w) {
        BedrockAnimations.Clip c = name != null ? C.get(name) : null;
        if (c != null && w > 0.001f) {
            this.play(c, t, w);
            this.tracks[0].add(name, t, w);
            this.tracks[1].add(name, t, w);
        }
    }

    @Override
    public void setAngles(BloodthirsterEntity entity, float limbAngle, float limbDistance, float animationProgress,
                          float headYaw, float headPitch) {
        this.resetPose();
        this.tracks[0].reset();
        this.tracks[1].reset();
        int a = entity.clientAction;
        float t = (animationProgress - entity.clientActionAge) / 20f;
        boolean p2 = entity.isPhase2();

        // --- атака ---
        String clip = null;
        float ct = 0f;
        float w = 0f;
        if (a == POUNCE) {
            if (t < 4f) {
                clip = t < POUNCE_START_LEN ? "pounce_start" : "pounce_air";
                ct = t < POUNCE_START_LEN ? t : t - POUNCE_START_LEN;
                w = t < POUNCE_START_LEN ? clamp01(t / 0.1f) : 1f;
            }
        } else if (a == EXEC) {
            if (t < 5f) {
                clip = t < EXEC_START_LEN ? "exec_start" : "exec_air";
                ct = t < EXEC_START_LEN ? t : Math.min(t - EXEC_START_LEN, EXEC_AIR.length());
                w = t < EXEC_START_LEN ? clamp01(t / 0.1f) : 1f;
            }
        } else if (a >= 0 && a < ACTIONS.length && ACTIONS[a] != null && C.get(ACTIONS[a]) != null && t >= 0
                && t < C.get(ACTIONS[a]).length()) {
            clip = ACTIONS[a];
            ct = t;
            float in = (a == LAND || a == EXEC_LAND) ? 1f : clamp01(t / 0.12f);
            w = Math.min(in, clamp01((C.get(clip).length() - t) / 0.25f));
        }
        float free = 1f - w;

        // --- покой / шаг / бег (по фазе) ---
        int ph = p2 ? 1 : 0;
        float move = clamp01(limbDistance * 2.5f);
        float run = clamp01((limbDistance - 0.5f) / 0.3f);
        this.playW(IDLE[ph], animationProgress / 20f, free * (1f - move));
        this.playW(WALK[ph], limbAngle * (p2 ? 0.07f : 0.06f), free * move * (1f - run));
        this.playW(RUN[ph], limbAngle * 0.035f, free * move * run);
        if (clip != null) {
            this.playW(clip, ct, w);
        }
        for (int i = 0; i < 2; i++) {
            this.tracks[i].result(this.rots[i]);
            this.scales[i] = this.tracks[i].scale();
        }

        this.blades = p2 || (a == TRANSFORM && t >= SWORDS_APPEAR && t < 3.5f);
        this.halo.visible = this.blades;

        HeadLook.apply(this.head, headYaw, headPitch, free * 0.7f);
    }
}

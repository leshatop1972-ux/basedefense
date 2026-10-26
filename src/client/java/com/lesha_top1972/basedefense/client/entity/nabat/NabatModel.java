package com.lesha_top1972.basedefense.client.entity.nabat;

import com.lesha_top1972.basedefense.client.entity.bedrock.BedrockAnimations;
import com.lesha_top1972.basedefense.client.entity.bedrock.BedrockEntityModel;
import com.lesha_top1972.basedefense.client.entity.bedrock.WeaponTrack;
import com.lesha_top1972.basedefense.entity.NabatEntity;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.model.ModelPart;
import net.minecraft.util.math.MathHelper;
import org.joml.Quaternionf;

import java.util.Map;

/**
 * Модель «Набата» (geo/nabat.geo.json, создаёт tools/nabat_gen.py).
 * Циклы: парение / полёт / скольжение (по скорости) в небе, «на земле» — после падения. Действия — по статусу с сервера.
 * Воздушные действия не трогают крылья: поверх них крылья продолжают махать циклом парения (слой «wings»).
 * Молот — отдельная модель (WeaponFeature) с поворотом из дорожки оружия.
 */
public class NabatModel extends BedrockEntityModel<NabatEntity> implements WeaponHolder {

    private static final String[] ACTIONS = {"intro", "toll", "censer", "gather", "bells", "rain", "mark", "eggs",
            "phantoms", "reliquary", "crack", "dive", "reap", "quake", "crash", "requiem", "requiem_break", "captive",
            "maul", "rise", "skyfall", "whirl", "knights", "lances", "exhaust"};
    /** Действия в воздухе, у которых крылья берутся из цикла парения. */
    private static final boolean[] AIR = new boolean[ACTIONS.length];

    static {
        for (int i : new int[]{NabatEntity.TOLL, NabatEntity.CENSER, NabatEntity.GATHER, NabatEntity.BELLS, NabatEntity.RAIN,
                NabatEntity.MARK, NabatEntity.EGGS, NabatEntity.PHANTOMS, NabatEntity.RELIQ, NabatEntity.CRACK}) {
            AIR[i] = true;
        }
    }

    private final Map<String, BedrockAnimations.Clip> clips;
    private final BedrockAnimations.Clip hoverWings;
    private final BedrockAnimations.Clip flyWings;
    private final BedrockAnimations.Clip glideWings;
    private final WeaponTrack weapon = new WeaponTrack("nabat");
    private final Quaternionf weaponRot = new Quaternionf();
    private final ModelPart[] chain;
    private final ModelPart halo;
    private final ModelPart head;
    private final ModelPart reliq;

    public NabatModel(ModelPart root) {
        super(root);
        this.clips = BedrockAnimations.load("nabat");
        Map<String, BedrockAnimations.Clip> w = BedrockAnimations.load("nabat",
                bone -> bone.contains("_wing") || (bone.matches(".*_p\\d+")));
        this.hoverWings = w.get("hover");
        this.flyWings = w.get("fly");
        this.glideWings = w.get("glide");
        this.chain = new ModelPart[]{root, this.bone("bone_root"), this.bone("body"), this.bone("torso"),
                this.bone("right_arm"), this.bone("right_fore"), this.bone("right_hand")};
        this.halo = this.bone("halo");
        this.head = this.bone("head");
        this.reliq = this.bone("reliq_chain");
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

    @Override
    public void setAngles(NabatEntity entity, float limbAngle, float limbDistance, float animationProgress,
                          float headYaw, float headPitch) {
        this.resetPose();
        this.weapon.reset();
        float tickDelta = MinecraftClient.getInstance().getTickDelta();
        float sec = animationProgress / 20f;

        if (entity.deathTime > 0) {
            float t = (entity.deathTime + tickDelta) / 20f;
            this.play2("death", Math.min(t, 3.59f), 1f);
            this.weapon.result(this.weaponRot);
            return;
        }

        // действие с сервера
        String clip = null;
        float ct = 0f;
        float w = 0f;
        int a = entity.clientAction;
        float t = (animationProgress - entity.clientActionAge) / 20f;
        BedrockAnimations.Clip c = a >= 0 && a < ACTIONS.length ? this.clips.get(ACTIONS[a]) : null;
        if (c != null && t >= 0 && t < c.length()) {
            clip = ACTIONS[a];
            ct = t;
            // падение и мощные атаки заканчиваются на земле; следом сразу идёт «выдохся»/«на земле»
            boolean toGround = a == NabatEntity.CRASH || a == NabatEntity.QUAKE || a == NabatEntity.SKYFALL
                    || a == NabatEntity.WHIRL || a == NabatEntity.KNIGHTS || a == NabatEntity.LANCES;
            boolean fromGround = a == NabatEntity.RISE || a == NabatEntity.REQUIEM || a == NabatEntity.REQ_BREAK
                    || a == NabatEntity.MAUL || a == NabatEntity.EXHAUST;
            float in = fromGround ? 1f : clamp01(t / 0.15f);
            float out = toGround ? 1f : clamp01((c.length() - t) / 0.25f);
            w = Math.min(in, out);
        }
        float free = 1f - w;

        // циклы
        boolean grounded = entity.isGrounded();
        if (grounded) {
            this.play2("ground_idle", sec, free);
        } else {
            double dx = entity.getX() - entity.prevX;
            double dz = entity.getZ() - entity.prevZ;
            float speed = (float) Math.sqrt(dx * dx + dz * dz);
            float flyK = clamp01((speed - 0.08f) / 0.14f);
            float glideK = clamp01((speed - 0.28f) / 0.12f);       // быстрый пролёт — крылья распахнуты
            this.play2("hover", sec, free * (1f - flyK));
            this.play2("fly", sec, free * flyK * (1f - glideK));
            this.play2("glide", sec, free * flyK * glideK);
            if (clip != null && AIR[a]) {
                // крылья в воздушных действиях — из цикла полёта
                this.play(this.hoverWings, sec, w * (1f - flyK));
                this.play(this.flyWings, sec, w * flyK * (1f - glideK));
                if (this.glideWings != null) {
                    this.play(this.glideWings, sec, w * flyK * glideK);
                }
            }
        }
        if (clip != null) {
            this.play2(clip, ct, w);
        }
        this.weapon.result(this.weaponRot);

        // душа поглощена — нимб вспыхивает
        float soul = (animationProgress - entity.clientSoulAge) / 8f;
        if (soul >= 0 && soul < 1) {
            float k = 1f + 0.25f * MathHelper.sin(soul * (float) Math.PI);
            this.halo.xScale *= k;
            this.halo.yScale *= k;
            this.halo.zScale *= k;
        }
        this.reliq.visible = !entity.isReliquaryGone();
        this.head.yaw += headYaw * DEG * free * 0.5f;
        this.head.pitch += headPitch * DEG * free * 0.4f;
    }
}

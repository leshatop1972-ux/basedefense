package com.lesha_top1972.basedefense.client.entity.enemy.bloodletter;

import com.lesha_top1972.basedefense.client.entity.bedrock.BedrockAnimations;
import com.lesha_top1972.basedefense.client.entity.bedrock.BedrockEntityModel;
import com.lesha_top1972.basedefense.client.entity.bedrock.HeadLook;
import com.lesha_top1972.basedefense.entity.enemy.bloodletter.BloodletterEntity;
import net.minecraft.client.model.ModelPart;

import java.util.Map;

/**
 * «Кровопускатель» (geo/bloodletter, клипы — tools/bloodletter_gen.py): idle / walk по сглаженной скорости шага
 * (скорость клипа — по limbAngle, ступни не скользят); «Кровавые копья» — summon → channel (цикл) с бросками
 * fling_r / fling_l поверх; «Вихрь» — whirl. Смена действий — плавная, голова следит за игроком.
 */
public class BloodletterModel extends BedrockEntityModel<BloodletterEntity> {

    /** Секунд клипа walk на единицу limbAngle: цикл 1.8 с ≈ 0.85 блока ≈ 3.4 limbAngle. */
    private static final float WALK_K = 0.53f;
    private static final float SUMMON = 1.5f;
    private static final float FLING = 0.6f;
    private static final float WHIRL = BloodletterEntity.WHIRL_TICKS / 20f;
    private static final float BARRAGE = BloodletterEntity.BARRAGE_TICKS / 20f;

    private final Map<String, BedrockAnimations.Clip> clips;
    private final ModelPart head;
    private float speed;
    private float lastAnim = -1f;

    public BloodletterModel(ModelPart root) {
        super(root);
        this.head = this.bone("head");
        this.clips = BedrockAnimations.load("bloodletter");
    }

    private void clip(String name, float t, float w) {
        this.play(this.clips.get(name), t, w);
    }

    /** Вес действия a, начатого в age (по его длине), с плавным входом и выходом. */
    private static float weight(int a, float t) {
        if (a == BloodletterEntity.BARRAGE) {
            return t < 0 || t >= BARRAGE ? 0f : Math.min(clamp01(t / 0.15f), clamp01((BARRAGE - t) / 0.45f));
        }
        if (a == BloodletterEntity.WHIRL) {
            return t < 0 || t >= WHIRL ? 0f : Math.min(clamp01(t / 0.08f), clamp01((WHIRL - t) / 0.2f));
        }
        return 0f;
    }

    /** Поза «Кровавых копий» в момент t (с бросками рукой поверх «удержания»). */
    private void barrage(BloodletterEntity e, float t, float now, float w) {
        if (w <= 0f) {
            return;
        }
        if (t < SUMMON) {
            this.clip("summon", t, w);
            return;
        }
        float wf = 0f;
        float tf = 0f;
        if (e.clientFling >= 0 && e.clientFlingAge >= e.clientActionAge) {
            tf = (now - e.clientFlingAge) / 20f;
            wf = tf >= 0 && tf < FLING ? Math.min(clamp01(tf / 0.05f), clamp01((FLING - tf) / 0.12f)) : 0f;
        }
        this.clip("channel", t - SUMMON, w * (1f - wf));
        if (wf > 0f) {
            this.clip(e.clientFling == BloodletterEntity.FLING_R ? "fling_r" : "fling_l", tf, w * wf);
        }
    }

    @Override
    public void setAngles(BloodletterEntity e, float limbAngle, float limbDistance, float animationProgress,
                          float headYaw, float headPitch) {
        this.resetPose();
        float dt = this.lastAnim < 0 ? 1f : Math.max(0f, Math.min(1f, animationProgress - this.lastAnim));
        this.lastAnim = animationProgress;
        this.speed += (limbDistance - this.speed) * Math.min(1f, 0.25f * dt + 0.02f);
        float now = animationProgress;
        int a = e.clientAction;
        float t = (now - e.clientActionAge) / 20f;
        float w = weight(a, t);
        // прошлое действие (вихрь прервал бросок) гаснет за 0.15 с
        float wp = 0f;
        int pa = e.clientPrevAction;
        if (pa == BloodletterEntity.BARRAGE && a == BloodletterEntity.WHIRL) {
            float since = (now - e.clientSwitchAge) / 20f;
            wp = clamp01(1f - since / 0.15f) * (1f - w);
        }
        float free = Math.max(0f, 1f - w - wp);
        float move = clamp01(this.speed * 3f);
        this.clip("idle", (animationProgress + e.getId() * 11) / 20f, free * (1f - move));
        this.clip("walk", limbAngle * WALK_K, free * move);
        if (wp > 0f) {
            this.clip("channel", 0.2f, wp);
        }
        if (a == BloodletterEntity.BARRAGE) {
            this.barrage(e, t, now, w);
        } else if (a == BloodletterEntity.WHIRL && w > 0f) {
            this.clip("whirl", t, w);
        }
        HeadLook.apply(this.head, headYaw, headPitch, free * 0.7f, 45f, 30f);
    }
}

package com.lesha_top1972.basedefense.client.entity.bedrock;

import net.minecraft.client.model.ModelPart;
import net.minecraft.client.render.entity.animation.AnimationHelper;
import net.minecraft.client.render.entity.model.SinglePartEntityModel;
import net.minecraft.entity.Entity;
import org.joml.Vector3f;

/**
 * Основа для моделей из Blockbench: корень модели, поиск костей по имени и проигрывание
 * анимации с весом (вес 0..1 — для плавного перехода между ходьбой, бегом и ударами).
 */
public abstract class BedrockEntityModel<T extends Entity> extends SinglePartEntityModel<T> {

    protected static final float DEG = (float) (Math.PI / 180.0);
    private static final Vector3f TMP = new Vector3f();

    private final ModelPart root;

    protected BedrockEntityModel(ModelPart root) {
        this.root = root;
    }

    @Override
    public ModelPart getPart() {
        return root;
    }

    /** Кость по имени (из .geo.json; «root» → «bone_root»). */
    protected ModelPart bone(String name) {
        return this.getChild(name).orElseThrow(() -> new IllegalStateException("Нет кости " + name));
    }

    /** Сбросить позу к исходной (вызывать в начале setAngles). */
    protected void resetPose() {
        root.traverse().forEach(ModelPart::resetTransform);
    }

    /** Добавить к позе анимацию в момент seconds с весом weight. */
    protected void play(BedrockAnimations.Clip clip, float seconds, float weight) {
        if (clip == null || weight <= 0.001f) {
            return;
        }
        AnimationHelper.animate(this, clip.animation(), (long) (Math.max(0f, seconds) * 1000f), weight, TMP);
    }

    protected static float clamp01(float v) {
        return v < 0 ? 0 : (v > 1 ? 1 : v);
    }
}

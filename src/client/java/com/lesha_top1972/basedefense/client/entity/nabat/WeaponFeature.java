package com.lesha_top1972.basedefense.client.entity.nabat;

import com.lesha_top1972.basedefense.client.entity.bedrock.WeaponTrack;
import net.minecraft.client.model.ModelPart;
import net.minecraft.client.render.OverlayTexture;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.entity.LivingEntityRenderer;
import net.minecraft.client.render.entity.feature.FeatureRenderer;
import net.minecraft.client.render.entity.feature.FeatureRendererContext;
import net.minecraft.client.render.entity.model.EntityModel;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.entity.LivingEntity;
import net.minecraft.util.Identifier;
import org.joml.Matrix4f;
import org.joml.Vector3f;

/**
 * Оружие как отдельная модель: ставится в точку кисти (по анимации тела), а поворот — из дорожки оружия,
 * смешанной между клипами по кватернионам. Поэтому на переходах между анимациями оружие не «прокручивается»
 * (у костей Эйлера при смешивании складываются углы — от этого раньше «вертелись» мечи кровожада).
 */
public class WeaponFeature<T extends LivingEntity, M extends EntityModel<T> & WeaponHolder> extends FeatureRenderer<T, M> {

    private final ModelPart weapon;
    private final Identifier texture;
    private final RenderLayer glow;
    private final Vector3f tmp = new Vector3f();

    public WeaponFeature(FeatureRendererContext<T, M> context, ModelPart weaponRoot, Identifier texture, Identifier glowmask) {
        super(context);
        this.weapon = weaponRoot;
        this.texture = texture;
        this.glow = glowmask != null ? RenderLayer.getEyes(glowmask) : null;
    }

    @Override
    public void render(MatrixStack matrices, VertexConsumerProvider vertexConsumers, int light, T entity, float limbAngle,
                       float limbDistance, float tickDelta, float animationProgress, float headYaw, float headPitch) {
        M model = this.getContextModel();
        if (!model.weaponVisible()) {
            return;
        }
        // точка кисти в системе модели — по костям текущего кадра
        MatrixStack chain = new MatrixStack();
        for (ModelPart p : model.handChain()) {
            p.rotate(chain);
        }
        Matrix4f m = chain.peek().getPositionMatrix();
        m.getTranslation(this.tmp);
        WeaponTrack tr = model.weaponTrack();
        matrices.push();
        matrices.translate(this.tmp.x, this.tmp.y, this.tmp.z);
        matrices.multiply(model.weaponRotation());
        matrices.translate(tr.gripX / 16f, tr.gripY / 16f - 1.5f, tr.gripZ / 16f);   // −1.5: загрузчик сдвигает y на 24 px
        int overlay = LivingEntityRenderer.getOverlay(entity, 0f);
        this.weapon.render(matrices, vertexConsumers.getBuffer(RenderLayer.getEntityCutoutNoCull(this.texture)), light,
                overlay, 1f, 1f, 1f, 1f);
        if (this.glow != null) {
            this.weapon.render(matrices, vertexConsumers.getBuffer(this.glow), 15728640, OverlayTexture.DEFAULT_UV,
                    1f, 1f, 1f, 1f);
        }
        matrices.pop();
    }
}

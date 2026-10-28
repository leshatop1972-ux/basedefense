package com.lesha_top1972.basedefense.mixin.client;

import com.lesha_top1972.basedefense.client.weapon.BloodSwordRender;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.entity.feature.FeatureRenderer;
import net.minecraft.client.render.entity.feature.HeldItemFeatureRenderer;
import net.minecraft.client.render.model.json.ModelTransformationMode;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.entity.LivingEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.util.Arm;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Меч кровожада от 3‑го лица во время замаха и удара — в обеих руках, по позе анимации. */
@Mixin(HeldItemFeatureRenderer.class)
public abstract class HeldItemFeatureRendererMixin {

    @Inject(method = "renderItem", at = @At("HEAD"), cancellable = true)
    private void basedefense$bloodSword(LivingEntity entity, ItemStack stack, ModelTransformationMode mode, Arm arm,
                                        MatrixStack matrices, VertexConsumerProvider vertexConsumers, int light,
                                        CallbackInfo ci) {
        Object model = ((FeatureRenderer<?, ?>) (Object) this).getContextModel();
        if (BloodSwordRender.renderThirdPerson(model, entity, stack, arm, matrices, vertexConsumers, light)) {
            ci.cancel();
        }
    }
}

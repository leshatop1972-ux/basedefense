package com.lesha_top1972.basedefense.mixin.client;

import com.lesha_top1972.basedefense.client.weapon.BloodSwordRender;
import net.minecraft.client.network.AbstractClientPlayerEntity;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.item.HeldItemRenderer;
import net.minecraft.client.render.model.json.ModelTransformationMode;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.entity.LivingEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.util.Hand;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Меч кровожада в руке: своя 3D‑модель вместо плоской картинки (покой), а от 1‑го лица во время
 * замаха и удара — меч и обе руки по анимации.
 */
@Mixin(HeldItemRenderer.class)
public abstract class HeldItemRendererMixin {

    @Inject(method = "renderFirstPersonItem", at = @At("HEAD"), cancellable = true)
    private void basedefense$bloodSwordFirstPerson(AbstractClientPlayerEntity player, float tickDelta, float pitch,
                                                   Hand hand, float swingProgress, ItemStack item, float equipProgress,
                                                   MatrixStack matrices, VertexConsumerProvider vertexConsumers,
                                                   int light, CallbackInfo ci) {
        if (BloodSwordRender.renderFirstPerson(player, hand, item, equipProgress, matrices, vertexConsumers, light)) {
            ci.cancel();
        }
    }

    @Inject(method = "renderItem(Lnet/minecraft/entity/LivingEntity;Lnet/minecraft/item/ItemStack;Lnet/minecraft/client/render/model/json/ModelTransformationMode;ZLnet/minecraft/client/util/math/MatrixStack;Lnet/minecraft/client/render/VertexConsumerProvider;I)V",
            at = @At("HEAD"), cancellable = true)
    private void basedefense$bloodSwordInHand(LivingEntity entity, ItemStack stack, ModelTransformationMode mode,
                                              boolean leftHanded, MatrixStack matrices,
                                              VertexConsumerProvider vertexConsumers, int light, CallbackInfo ci) {
        if (BloodSwordRender.renderRest(entity, stack, mode, leftHanded, matrices, vertexConsumers, light)) {
            ci.cancel();
        }
    }
}

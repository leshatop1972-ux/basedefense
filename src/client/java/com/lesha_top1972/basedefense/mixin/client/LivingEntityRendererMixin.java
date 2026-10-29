package com.lesha_top1972.basedefense.mixin.client;

import com.lesha_top1972.basedefense.entity.enemy.curb.CurbEntity;
import net.minecraft.client.render.OverlayTexture;
import net.minecraft.client.render.entity.LivingEntityRenderer;
import net.minecraft.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Боря228 после смерти ещё ~14 с стоит на колене (пока играет звук смерти) — красная подсветка урона
 * держится только первые полсекунды, а не всё это время.
 */
@Mixin(LivingEntityRenderer.class)
public abstract class LivingEntityRendererMixin {

    @Inject(method = "getOverlay", at = @At("HEAD"), cancellable = true)
    private static void basedefense$curbDeathOverlay(LivingEntity entity, float whiteOverlayProgress,
                                                     CallbackInfoReturnable<Integer> cir) {
        if (entity instanceof CurbEntity && entity.deathTime > 10 && entity.hurtTime <= 0) {
            cir.setReturnValue(OverlayTexture.packUv(OverlayTexture.getU(whiteOverlayProgress), OverlayTexture.getV(false)));
        }
    }
}

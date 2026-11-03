package com.lesha_top1972.basedefense.mixin.client;

import com.lesha_top1972.basedefense.client.weapon.KatanaClient;
import net.minecraft.client.render.GameRenderer;
import net.minecraft.client.util.math.MatrixStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Камера от 1‑го лица с катаной: крен, наклон и поворот из клипа (за ударом камера чуть «ведёт»).
 * tiltViewWhenHurt вызывается и для мира, и для рук — поворачивается весь вид.
 */
@Mixin(GameRenderer.class)
public abstract class GameRendererMixin {

    @Inject(method = "tiltViewWhenHurt", at = @At("HEAD"))
    private void basedefense$katanaCamera(MatrixStack matrices, float tickDelta, CallbackInfo ci) {
        KatanaClient.applyCamera(matrices, tickDelta);
    }
}

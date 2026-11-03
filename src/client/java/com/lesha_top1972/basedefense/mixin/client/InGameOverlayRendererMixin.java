package com.lesha_top1972.basedefense.mixin.client;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.hud.InGameOverlayRenderer;
import net.minecraft.client.util.math.MatrixStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Constant;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyConstant;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Огонь на экране, когда игрок горит, мешал видеть бой: языки пламени опущены к нижнему краю экрана
 * и сделаны полупрозрачнее (в ванилле они закрывают почти половину обзора).
 */
@Mixin(InGameOverlayRenderer.class)
public abstract class InGameOverlayRendererMixin {

    /** Насколько опустить пламя (доля экрана) и его прозрачность (в ванилле 0.9). */
    private static final float BASEDEFENSE$DROP = 0.32f;
    private static final float BASEDEFENSE$ALPHA = 0.45f;

    @Inject(method = "renderFireOverlay", at = @At("HEAD"))
    private static void basedefense$fireDown(MinecraftClient client, MatrixStack matrices, CallbackInfo ci) {
        matrices.push();
        matrices.translate(0.0f, -BASEDEFENSE$DROP, 0.0f);
    }

    @Inject(method = "renderFireOverlay", at = @At("RETURN"))
    private static void basedefense$fireDownEnd(MinecraftClient client, MatrixStack matrices, CallbackInfo ci) {
        matrices.pop();
    }

    @ModifyConstant(method = "renderFireOverlay", constant = @Constant(floatValue = 0.9f), require = 0)
    private static float basedefense$fireAlpha(float original) {
        return BASEDEFENSE$ALPHA;
    }
}

package com.lesha_top1972.basedefense.client.render;

import com.lesha_top1972.basedefense.effect.ModEffects;
import com.mojang.blaze3d.systems.RenderSystem;
import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.util.Identifier;

/**
 * Экран при «Заморозке» — как в рыхлом снегу: иней по краям экрана (ванильная текстура
 * powder_snow_outline), середина экрана не закрывается. Появляется за 0.15 с, тает в последние 0.5 с.
 */
public final class FreezeOverlay {

    private static final Identifier POWDER_SNOW = new Identifier("textures/misc/powder_snow_outline.png");

    private FreezeOverlay() {
    }

    public static void init() {
        HudRenderCallback.EVENT.register(FreezeOverlay::render);
    }

    private static void render(DrawContext context, float tickDelta) {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client.player == null || client.options.hudHidden) {
            return;
        }
        StatusEffectInstance effect = client.player.getStatusEffect(ModEffects.FREEZE);
        if (effect == null) {
            return;
        }
        float left = effect.getDuration() - tickDelta;
        float alpha = Math.max(0f, Math.min(1f, left / 10f));

        int w = context.getScaledWindowWidth();
        int h = context.getScaledWindowHeight();
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        RenderSystem.setShaderColor(1f, 1f, 1f, alpha);
        context.drawTexture(POWDER_SNOW, 0, 0, -90, 0f, 0f, w, h, w, h);
        RenderSystem.setShaderColor(1f, 1f, 1f, 1f);
        RenderSystem.disableBlend();
    }
}

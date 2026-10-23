package com.lesha_top1972.basedefense.client.render;

import com.lesha_top1972.basedefense.Basedefense;
import com.lesha_top1972.basedefense.effect.ModEffects;
import com.mojang.blaze3d.systems.RenderSystem;
import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.util.Identifier;

/**
 * Экран при «Заморозке»: бело‑голубая дымка и иней по краям (textures/misc/freeze_overlay.png).
 * Плавно появляется за 0.2 с и тает в последние 0.5 с эффекта.
 */
public final class FreezeOverlay {

    private static final Identifier FROST = new Identifier(Basedefense.MOD_ID, "textures/misc/freeze_overlay.png");

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
        int left = effect.getDuration();
        float fadeOut = Math.min(1f, (left - tickDelta) / 10f);
        float age = client.player.age + tickDelta;
        float alpha = Math.max(0f, Math.min(1f, fadeOut));
        float pulse = 0.92f + 0.08f * (float) Math.sin(age * 0.35f);

        int w = context.getScaledWindowWidth();
        int h = context.getScaledWindowHeight();

        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        // голубая дымка на весь экран
        int a = (int) (alpha * 70);
        context.fill(0, 0, w, h, (a << 24) | 0xBFE8FF);
        // иней по краям
        RenderSystem.setShaderColor(1f, 1f, 1f, alpha * pulse);
        context.drawTexture(FROST, 0, 0, 0, 0f, 0f, w, h, w, h);
        RenderSystem.setShaderColor(1f, 1f, 1f, 1f);
        RenderSystem.disableBlend();
    }
}

package com.lesha_top1972.basedefense.client.render;

import com.lesha_top1972.basedefense.Basedefense;
import com.lesha_top1972.basedefense.effect.ModEffects;
import com.mojang.blaze3d.systems.RenderSystem;
import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.MathHelper;

/**
 * Экран при «Оглушении»: I — по краям пульсирует тёмная дымка с кругом «звёздочек»
 * (textures/misc/stun_vignette.png), середина видна; II — то же, но экран сильно темнеет.
 * Появляется за 0.15 с, тает в последние 0.5 с.
 */
public final class StunOverlay {

    private static final Identifier VIGNETTE = new Identifier(Basedefense.MOD_ID, "textures/misc/stun_vignette.png");

    private StunOverlay() {
    }

    public static void init() {
        HudRenderCallback.EVENT.register(StunOverlay::render);
    }

    private static void render(DrawContext context, float tickDelta) {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client.player == null || client.options.hudHidden) {
            return;
        }
        StatusEffectInstance effect = client.player.getStatusEffect(ModEffects.STUN);
        if (effect == null) {
            return;
        }
        boolean strong = effect.getAmplifier() >= 1;
        float left = effect.getDuration() - tickDelta;
        float fade = Math.max(0f, Math.min(1f, left / 10f));
        float time = client.player.age + tickDelta;
        float pulse = 0.75f + 0.25f * MathHelper.sin(time * 0.35f);

        int w = context.getScaledWindowWidth();
        int h = context.getScaledWindowHeight();
        if (strong) {
            int a = (int) (165 * fade);
            context.fill(0, 0, w, h, a << 24);
        } else {
            int a = (int) (40 * fade);
            context.fill(0, 0, w, h, (a << 24) | 0x2A2618);
        }
        // дымка по краям слегка «плывёт» (раскачивается) — голова кружится
        int sway = (int) (MathHelper.sin(time * 0.21f) * 6);
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        RenderSystem.setShaderColor(1f, 1f, 1f, fade * (strong ? 1f : pulse));
        context.drawTexture(VIGNETTE, sway - 6, -4, -90, 0f, 0f, w + 12, h + 8, w + 12, h + 8);
        RenderSystem.setShaderColor(1f, 1f, 1f, 1f);
        RenderSystem.disableBlend();
    }
}

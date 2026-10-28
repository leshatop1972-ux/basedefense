package com.lesha_top1972.basedefense.client.effect;

import com.lesha_top1972.basedefense.effect.ModEffects;
import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.entity.effect.StatusEffectInstance;

/**
 * «Дуэлянт»: еле заметные синие полоски по краям экрана (слева и справа), слегка пульсируют.
 */
public final class DuelistOverlay {

    private DuelistOverlay() {
    }

    public static void init() {
        HudRenderCallback.EVENT.register(DuelistOverlay::render);
    }

    private static void render(DrawContext context, float tickDelta) {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client.player == null || client.options.hudHidden) {
            return;
        }
        StatusEffectInstance effect = client.player.getStatusEffect(ModEffects.DUELIST);
        if (effect == null) {
            return;
        }
        float left = effect.getDuration() - tickDelta;
        float fade = Math.max(0f, Math.min(1f, left / 20f));
        float pulse = 0.8f + 0.2f * (float) Math.sin((client.player.age + tickDelta) * 0.15f);
        int w = context.getScaledWindowWidth();
        int h = context.getScaledWindowHeight();
        int band = Math.max(6, w / 40);
        for (int i = 0; i < band; i++) {
            float k = 1f - i / (float) band;
            int a = (int) (55 * k * k * fade * pulse);
            if (a <= 0) {
                continue;
            }
            int color = (a << 24) | 0x3A78FF;
            context.fill(i, 0, i + 1, h, color);
            context.fill(w - i - 1, 0, w - i, h, color);
        }
        // тонкие яркие штрихи у самого края
        int a = (int) (90 * fade * pulse);
        for (int y = (int) ((client.player.age + tickDelta) * 1.5f) % 24 - 24; y < h; y += 24) {
            context.fill(0, y, 1, y + 10, (a << 24) | 0x8FB8FF);
            context.fill(w - 1, y + 12, w, y + 22, (a << 24) | 0x8FB8FF);
        }
    }
}

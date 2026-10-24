package com.lesha_top1972.basedefense.client.render;

import com.lesha_top1972.basedefense.Basedefense;
import com.lesha_top1972.basedefense.effect.ModEffects;
import com.mojang.blaze3d.systems.RenderSystem;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.sound.SoundEvents;
import net.minecraft.util.Identifier;

/**
 * «Шипы» ранили игрока: по краям экрана на полсекунды вспыхивают бело‑синие осколки
 * (textures/misc/spikes_flash.png). Сигнал приходит с сервера пакетом ModEffects.SPIKES_FLASH.
 */
public final class SpikesOverlay {

    private static final Identifier FLASH = new Identifier(Basedefense.MOD_ID, "textures/misc/spikes_flash.png");
    private static final int LENGTH = 10;
    private static long flashAt = -1000;

    private SpikesOverlay() {
    }

    public static void init() {
        ClientPlayNetworking.registerGlobalReceiver(ModEffects.SPIKES_FLASH, (client, handler, buf, sender) ->
                client.execute(() -> {
                    if (client.world != null) {
                        flashAt = client.world.getTime();
                        if (client.player != null) {
                            client.player.playSound(SoundEvents.BLOCK_AMETHYST_CLUSTER_BREAK, 0.8f, 1.4f);
                        }
                    }
                }));
        HudRenderCallback.EVENT.register(SpikesOverlay::render);
    }

    private static void render(DrawContext context, float tickDelta) {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client.world == null || client.options.hudHidden) {
            return;
        }
        float since = client.world.getTime() - flashAt + tickDelta;
        if (since < 0 || since > LENGTH) {
            return;
        }
        float k = 1f - since / LENGTH;
        int w = context.getScaledWindowWidth();
        int h = context.getScaledWindowHeight();
        context.fill(0, 0, w, h, ((int) (45 * k) << 24) | 0xCDEBFF);
        // осколки «влетают» к центру: картинка чуть сжимается за время вспышки
        int grow = (int) (12 * (1f - k));
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        RenderSystem.setShaderColor(1f, 1f, 1f, k);
        context.drawTexture(FLASH, -grow, -grow, -90, 0f, 0f, w + grow * 2, h + grow * 2, w + grow * 2, h + grow * 2);
        RenderSystem.setShaderColor(1f, 1f, 1f, 1f);
        RenderSystem.disableBlend();
    }
}

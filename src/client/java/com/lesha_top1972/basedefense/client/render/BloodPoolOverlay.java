package com.lesha_top1972.basedefense.client.render;

import com.lesha_top1972.basedefense.block.ModBlocks;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.network.ClientPlayerEntity;

/**
 * Кровавая лужа: когда она ранит игрока, экран на ~0.6 с чуть краснеет (сильнее по краям).
 * Клиент видит само ранение (hurtTime) и что игрок стоит в луже.
 */
public final class BloodPoolOverlay {

    private static final int FLASH_TICKS = 12;
    private static int flash;
    private static int lastHurt;

    private BloodPoolOverlay() {
    }

    public static void init() {
        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            ClientPlayerEntity p = client.player;
            if (p == null) {
                flash = 0;
                return;
            }
            if (flash > 0) {
                flash--;
            }
            boolean justHurt = p.hurtTime > lastHurt;
            lastHurt = p.hurtTime;
            if (justHurt && p.getWorld().getBlockState(p.getBlockPos()).isOf(ModBlocks.BLOOD_POOL)) {
                flash = FLASH_TICKS;
            }
        });
        HudRenderCallback.EVENT.register(BloodPoolOverlay::render);
    }

    private static void render(DrawContext context, float tickDelta) {
        MinecraftClient client = MinecraftClient.getInstance();
        if (flash <= 0 || client.options.hudHidden) {
            return;
        }
        float k = Math.max(0f, (flash - tickDelta) / FLASH_TICKS);
        int w = context.getScaledWindowWidth();
        int h = context.getScaledWindowHeight();
        context.fill(0, 0, w, h, ((int) (38 * k) << 24) | 0xB00010);
        int band = Math.max(8, Math.min(w, h) / 10);
        for (int i = 0; i < band; i++) {
            float e = 1f - i / (float) band;
            int a = (int) (70 * e * e * k);
            if (a <= 0) {
                continue;
            }
            int c = (a << 24) | 0x900010;
            context.fill(i, i, w - i, i + 1, c);
            context.fill(i, h - i - 1, w - i, h - i, c);
            context.fill(i, i + 1, i + 1, h - i - 1, c);
            context.fill(w - i - 1, i + 1, w - i, h - i - 1, c);
        }
    }
}

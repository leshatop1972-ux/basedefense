package com.lesha_top1972.basedefense.client.render;

import com.lesha_top1972.basedefense.effect.ModEffects;
import com.lesha_top1972.basedefense.entity.TatteredLancerEntity;
import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.entity.effect.StatusEffectInstance;

/**
 * «Подлая крыса»: в первую секунду после получения экран заливает мутно‑зелёным («блевотным»),
 * потом заливка тает и больше не видна.
 */
public final class RatOverlay {

    private static final int FLASH = 20;

    private RatOverlay() {
    }

    public static void init() {
        HudRenderCallback.EVENT.register(RatOverlay::render);
    }

    private static void render(DrawContext context, float tickDelta) {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client.player == null) {
            return;
        }
        StatusEffectInstance effect = client.player.getStatusEffect(ModEffects.SNEAKY_RAT);
        if (effect == null) {
            return;
        }
        float since = TatteredLancerEntity.RAT_TICKS - effect.getDuration() + tickDelta;
        if (since < 0 || since > FLASH) {
            return;
        }
        float k = 1f - since / FLASH;
        int a = (int) (150 * k);
        int w = context.getScaledWindowWidth();
        int h = context.getScaledWindowHeight();
        context.fill(0, 0, w, h, (a << 24) | 0x8C9A24);
        int a2 = (int) (90 * k);
        context.fillGradient(0, 0, w, h / 3, (a2 << 24) | 0x5A6614, 0);
        context.fillGradient(0, h * 2 / 3, w, h, 0, (a2 << 24) | 0x5A6614);
    }
}

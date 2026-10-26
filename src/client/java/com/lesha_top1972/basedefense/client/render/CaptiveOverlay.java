package com.lesha_top1972.basedefense.client.render;

import com.lesha_top1972.basedefense.Basedefense;
import com.lesha_top1972.basedefense.entity.NabatCaptiveEntity;
import com.mojang.blaze3d.systems.RenderSystem;
import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.MathHelper;

import java.util.List;

/**
 * Изнутри «Колокольного плена»: темнота с тусклым янтарным отсветом по краям; с каждым ударом колокола
 * отсвет вздрагивает. Снаружи ничего не видно.
 */
public final class CaptiveOverlay {

    private static final Identifier VIGNETTE = new Identifier(Basedefense.MOD_ID, "textures/misc/nabat_captive.png");

    private CaptiveOverlay() {
    }

    public static void init() {
        HudRenderCallback.EVENT.register(CaptiveOverlay::render);
    }

    private static void render(DrawContext context, float tickDelta) {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client.player == null || client.options.hudHidden) {
            return;
        }
        List<NabatCaptiveEntity> list = client.player.getWorld().getEntitiesByClass(NabatCaptiveEntity.class,
                client.player.getBoundingBox().expand(0.5), e -> e.isAlive() && e.age > 10
                        && e.squaredDistanceTo(client.player.getX(), e.getY(), client.player.getZ()) < 1.0);
        if (list.isEmpty()) {
            return;
        }
        NabatCaptiveEntity c = list.get(0);
        float t = c.age + tickDelta;
        float fade = MathHelper.clamp((t - 10) / 6f, 0f, 1f);
        float beat = (t - 10) % 20f;
        float pulse = beat < 6 ? 1f - beat / 6f : 0f;
        int w = context.getScaledWindowWidth();
        int h = context.getScaledWindowHeight();
        context.fill(0, 0, w, h, ((int) (150 * fade) << 24) | 0x0A0604);
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        RenderSystem.setShaderColor(1f, 1f, 1f, fade * (0.55f + 0.45f * pulse));
        context.drawTexture(VIGNETTE, 0, 0, -90, 0f, 0f, w, h, w, h);
        RenderSystem.setShaderColor(1f, 1f, 1f, 1f);
        RenderSystem.disableBlend();
    }
}

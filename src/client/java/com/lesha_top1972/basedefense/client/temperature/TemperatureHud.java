package com.lesha_top1972.basedefense.client.temperature;

import com.lesha_top1972.basedefense.Basedefense;
import com.lesha_top1972.basedefense.temperature.TempStatus;
import com.lesha_top1972.basedefense.client.needs.ClientNeeds;
import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.util.Identifier;

/**
 * Температура на экране: справа от полоски еды — значок самочувствия (снежинка / термометр / солнце / пламя)
 * и число °C (ощущаемая температура, меняется плавно). Значения раз в секунду присылает сервер (ClientNeeds). В творческом режиме и режиме наблюдателя не видно.
 */
public final class TemperatureHud {

    public static final Identifier ICONS = new Identifier(Basedefense.MOD_ID, "textures/gui/temperature.png");

    private TemperatureHud() {
    }

    public static void init() {
        HudRenderCallback.EVENT.register(TemperatureHud::render);
    }

    private static void render(DrawContext ctx, float tickDelta) {
        MinecraftClient c = MinecraftClient.getInstance();
        ClientPlayerEntity p = c.player;
        if (!ClientNeeds.has || p == null || c.options.hudHidden || c.interactionManager == null
                || !c.interactionManager.hasStatusBars()) {
            return;
        }
        int x = ctx.getScaledWindowWidth() / 2 + 91 + 4;
        int y = ctx.getScaledWindowHeight() - 39;
        draw(ctx, c.textRenderer, x, y, ClientNeeds.felt, ClientNeeds.status);
    }

    /** Значок 9×9 и число справа от него (общий для HUD и меню поселенца). */
    public static void draw(DrawContext ctx, TextRenderer tr, int x, int y, float t, TempStatus st) {
        ctx.drawTexture(ICONS, x, y, st.ordinal() * 9, 0, 9, 9, 45, 9);
        ctx.drawText(tr, Math.round(t) + "°", x + 11, y + 1, color(st), true);
    }

    public static int color(TempStatus st) {
        return switch (st) {
            case FREEZING -> 0xFF6A8CFF;
            case COLD -> 0xFF9ED8FF;
            case COMFORT -> 0xFFB8F0A8;
            case HOT -> 0xFFFFC860;
            case BURNING -> 0xFFFF6A50;
        };
    }
}

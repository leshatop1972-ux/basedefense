package com.lesha_top1972.basedefense.client.weapon;

import com.lesha_top1972.basedefense.Basedefense;
import com.lesha_top1972.basedefense.weapon.KatanaCombat;
import com.lesha_top1972.basedefense.weapon.KatanaPower;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.util.Identifier;

/**
 * «Древняя сила» на экране: маленький квадрат слева от ячеек и сердец — значок и число. Виден с древней катаной
 * в руке или пока сила не ушла. Полоска снизу — доля от максимума (20).
 */
public final class KatanaHud {

    private static final Identifier ICON = new Identifier(Basedefense.MOD_ID, "textures/gui/ancient_power.png");

    private KatanaHud() {
    }

    /** Перезарядка особой атаки — белая шторка на ячейках панели с катанами (как ванильная перезарядка). */
    private static void cooldownOverlay(DrawContext ctx, ClientPlayerEntity p, float tickDelta) {
        float k = KatanaClient.specialCooldown(tickDelta);
        if (k <= 0) {
            return;
        }
        int w = ctx.getScaledWindowWidth();
        int h = ctx.getScaledWindowHeight();
        for (int i = 0; i < 9; i++) {
            if (p.getInventory().getStack(i).getItem() instanceof com.lesha_top1972.basedefense.weapon.KatanaItem) {
                int x = w / 2 - 90 + i * 20 + 2;
                int y = h - 16 - 3;
                int top = y + Math.round(16 * (1 - k));
                ctx.fill(x, top, x + 16, y + 16, 0x7FFFFFFF);
            }
        }
    }

    static void render(DrawContext ctx, float tickDelta) {
        MinecraftClient c = MinecraftClient.getInstance();
        ClientPlayerEntity p = c.player;
        if (p == null || c.options.hudHidden || p.isSpectator()) {
            return;
        }
        cooldownOverlay(ctx, p, tickDelta);
        int power = KatanaClient.power;
        boolean ancient = KatanaCombat.holding(p) && KatanaCombat.variant(p).ancient;
        if (power <= 0 && !ancient) {
            return;
        }
        int w = ctx.getScaledWindowWidth();
        int h = ctx.getScaledWindowHeight();
        int x = w / 2 - 91 - 23;
        int y = h - 42;
        // рамка и фон
        ctx.fill(x, y, x + 20, y + 20, 0xC0100818);
        ctx.fill(x, y, x + 20, y + 1, 0xFF8A4CD8);
        ctx.fill(x, y + 19, x + 20, y + 20, 0xFF8A4CD8);
        ctx.fill(x, y, x + 1, y + 20, 0xFF8A4CD8);
        ctx.fill(x + 19, y, x + 20, y + 20, 0xFF8A4CD8);
        int bar = Math.round(18f * power / KatanaPower.MAX);
        if (bar > 0) {
            ctx.fill(x + 1, y + 17, x + 1 + bar, y + 19, 0xFFC890FF);
        }
        if (power >= KatanaPower.MAX) {                        // полная — мерцает
            float k = 0.5f + 0.5f * (float) Math.sin((p.age + tickDelta) * 0.3);
            int a = (int) (60 + 80 * k);
            ctx.fill(x + 1, y + 1, x + 19, y + 17, a << 24 | 0xB060FF);
        }
        ctx.drawTexture(ICON, x + 2, y + 1, 0, 0, 16, 16, 16, 16);
        String s = Integer.toString(power);
        ctx.drawText(c.textRenderer, s, x + 19 - c.textRenderer.getWidth(s), y + 9, power > 0 ? 0xFFF0D8FF : 0xFF807090, true);
    }
}

package com.lesha_top1972.basedefense.client.weapon;

import com.lesha_top1972.basedefense.weapon.CleaverCombat;
import com.lesha_top1972.basedefense.weapon.CleaverItem;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.network.ClientPlayerEntity;

/**
 * Тесак на экране: слева от ячеек — барабан (6 патронов по кругу, заряженные — золотые) с числом «Внутренней силы»
 * в центре и её полоской снизу; на ячейках с тесаком — шторка перезарядки «Тигриного выпада»; под барабаном —
 * полоска перезарядки «Тесака в узел». Виден с тесаком в руке.
 */
public final class CleaverHud {

    private static final int[][] DOTS = {{0, -8}, {7, -4}, {7, 4}, {0, 8}, {-7, 4}, {-7, -4}};

    private CleaverHud() {
    }

    static void render(DrawContext ctx, float tickDelta) {
        MinecraftClient c = MinecraftClient.getInstance();
        ClientPlayerEntity p = c.player;
        if (p == null || c.options.hudHidden || p.isSpectator()) {
            return;
        }
        int w = ctx.getScaledWindowWidth();
        int h = ctx.getScaledWindowHeight();
        float cd1 = CleaverClient.cooldown(1, tickDelta);
        if (cd1 > 0) {
            for (int i = 0; i < 9; i++) {
                if (p.getInventory().getStack(i).getItem() instanceof CleaverItem) {
                    int x = w / 2 - 90 + i * 20 + 2;
                    int y = h - 16 - 3;
                    ctx.fill(x, y + Math.round(16 * (1 - cd1)), x + 16, y + 16, 0x7FFFFFFF);
                }
            }
        }
        if (!CleaverCombat.holding(p)) {
            return;
        }
        int rounds = CleaverClient.rounds(p);
        int inner = CleaverClient.inner;
        int x = w / 2 - 91 - 29;
        int y = h - 74;
        int cx = x + 13;
        int cy = y + 13;
        // рамка и фон
        ctx.fill(x, y, x + 26, y + 30, 0xC0180808);
        ctx.fill(x, y, x + 26, y + 1, 0xFFF0AE0D);
        ctx.fill(x, y + 29, x + 26, y + 30, 0xFFF0AE0D);
        ctx.fill(x, y, x + 1, y + 30, 0xFFF0AE0D);
        ctx.fill(x + 25, y, x + 26, y + 30, 0xFFF0AE0D);
        boolean ready2 = inner >= CleaverCombat.S2_INNER_MIN && rounds > 0 && CleaverClient.cooldown(2, tickDelta) <= 0;
        if (ready2) {                                    // «Тесак в узел» готов — мерцает
            float k = 0.5f + 0.5f * (float) Math.sin((p.age + tickDelta) * 0.3);
            ctx.fill(x + 1, y + 1, x + 25, y + 26, (int) (40 + 70 * k) << 24 | 0xFF8A1E);
        }
        // барабан: 6 камор
        for (int i = 0; i < DOTS.length; i++) {
            int dx = cx + DOTS[i][0];
            int dy = cy + DOTS[i][1];
            boolean full = i < rounds;
            ctx.fill(dx - 2, dy - 2, dx + 2, dy + 2, full ? 0xFFA85900 : 0xFF2A1414);
            ctx.fill(dx - 1, dy - 1, dx + 1, dy + 1, full ? 0xFFFFE876 : 0xFF100808);
        }
        if (rounds == 0 && (p.age / 6) % 2 == 0) {       // пусто — рамка мигает красным
            ctx.fill(x + 1, y + 1, x + 25, y + 2, 0xFFD8402A);
            ctx.fill(x + 1, y + 25, x + 25, y + 26, 0xFFD8402A);
        }
        // внутренняя сила: число в центре и полоска
        String s = Integer.toString(inner);
        ctx.drawText(c.textRenderer, s, cx - c.textRenderer.getWidth(s) / 2 + 1, cy - 4,
                inner >= CleaverCombat.S2_INNER_MIN ? 0xFFFFD24A : (inner > 0 ? 0xFFE0C8A0 : 0xFF806060), true);
        int bar = Math.round(24f * inner / CleaverCombat.INNER_MAX);
        if (bar > 0) {
            ctx.fill(x + 1, y + 26, x + 1 + bar, y + 29, inner >= CleaverCombat.S2_INNER_MIN ? 0xFFFFD24A : 0xFFB07A28);
        }
        int mark = x + 1 + Math.round(24f * CleaverCombat.S2_INNER_MIN / CleaverCombat.INNER_MAX);
        ctx.fill(mark, y + 26, mark + 1, y + 29, 0xFFFFFFFF);
        float cd2 = CleaverClient.cooldown(2, tickDelta);
        if (cd2 > 0) {
            ctx.fill(x, y + 31, x + Math.round(26 * cd2), y + 32, 0xFFFF8A1E);
        }
    }
}

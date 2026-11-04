package com.lesha_top1972.basedefense.client.clothing;

import com.lesha_top1972.basedefense.client.artifact.ArtifactPanelHost;
import com.lesha_top1972.basedefense.clothing.Clothing;
import net.fabricmc.fabric.api.client.screen.v1.ScreenEvents;
import net.fabricmc.fabric.api.client.screen.v1.Screens;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.ingame.InventoryScreen;
import net.minecraft.client.gui.tooltip.Tooltip;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.text.Text;

/**
 * Панель одежды на клиенте: маленькая кнопка у брони открывает рядом отдельную панель — 4 слота одежды и слот рюкзака.
 * У игрока панель над окном инвентаря (в ряд), у поселенца — слева от его меню (столбиком). Броня остаётся на месте.
 * Открыта ли панель — общее для обоих меню, запоминается до выхода из игры.
 */
public final class ClothingClient {

    /** Кнопка: 9×9. */
    private static final int SIZE = 9;

    private static boolean panelOpen;

    private ClothingClient() {
    }

    public static boolean panelOpen() {
        return panelOpen;
    }

    public static void init() {
        Clothing.playerView = () -> panelOpen && MinecraftClient.getInstance().currentScreen instanceof InventoryScreen;
        Clothing.settlerView = () -> panelOpen;
        ScreenEvents.AFTER_INIT.register((client, screen, w, h) -> {
            if (!(screen instanceof InventoryScreen)) {
                return;
            }
            ButtonWidget b = toggleButton(0, 0);
            Screens.getButtons(screen).add(b);
            // Книга рецептов сдвигает окно — кнопка в левом верхнем углу окошка с игроком, справа от брони
            ScreenEvents.beforeRender(screen).register((s, ctx, mx, my, d) ->
                    b.setPosition(((ArtifactPanelHost) s).basedefense$left() + 27, (s.height - 166) / 2 + 9));
        });
    }

    // ---------- Игрок: панель над окном ----------

    private static int playerL() {
        return Clothing.PLAYER_X - 6;
    }

    private static int playerR() {
        return Clothing.PLAYER_BACKPACK_X + 16 + 6;
    }

    /** Фон панели (из InventoryScreenMixin.drawBackground). */
    public static void drawPlayerPanel(DrawContext ctx, int left, int top) {
        if (!panelOpen) {
            return;
        }
        int x0 = left + playerL();
        int y0 = top + Clothing.PLAYER_Y - 6;
        int x1 = left + playerR();
        int y1 = top + 2;
        vanillaPanel(ctx, x0, y0, x1, y1);
        for (int i = 0; i < Clothing.CLOTHES; i++) {
            slotCell(ctx, left + Clothing.PLAYER_X + i * 18, top + Clothing.PLAYER_Y);
        }
        slotCell(ctx, left + Clothing.PLAYER_BACKPACK_X, top + Clothing.PLAYER_Y);
    }

    /** Клик по панели — не «мимо окна». */
    public static boolean inPlayerPanel(double mx, double my, int left, int top) {
        return panelOpen && mx >= left + playerL() && mx < left + playerR()
                && my >= top + Clothing.PLAYER_Y - 6 && my < top;
    }

    // ---------- Поселенец: панель слева ----------

    public static void drawSettlerPanel(DrawContext ctx, int left, int top) {
        if (!panelOpen) {
            return;
        }
        int x0 = left + Clothing.SETTLER_X - 6;
        int y0 = top + Clothing.SETTLER_Y - 6;
        int x1 = left + 2;
        int y1 = top + Clothing.SETTLER_BACKPACK_Y + 16 + 6;
        ctx.fill(x0, y0, x1, y1, 0xC0181A1E);
        ctx.fill(x0, y0, x1, y0 + 1, 0x80FFFFFF);
        ctx.fill(x0, y0, x0 + 1, y1, 0x50FFFFFF);
        ctx.fill(x0, y1 - 1, x1, y1, 0x50FFFFFF);
        for (int i = 0; i < Clothing.CLOTHES; i++) {
            slotCell(ctx, left + Clothing.SETTLER_X, top + Clothing.SETTLER_Y + i * 18);
        }
        slotCell(ctx, left + Clothing.SETTLER_X, top + Clothing.SETTLER_BACKPACK_Y);
    }

    public static boolean inSettlerPanel(double mx, double my, int left, int top) {
        return panelOpen && mx >= left + Clothing.SETTLER_X - 6 && mx < left
                && my >= top + Clothing.SETTLER_Y - 6 && my < top + Clothing.SETTLER_BACKPACK_Y + 22;
    }

    // ---------- Рисование ----------

    private static void vanillaPanel(DrawContext ctx, int x0, int y0, int x1, int y1) {
        ctx.fill(x0, y0, x1, y1, 0xFF000000);
        ctx.fill(x0 + 1, y0 + 1, x1 - 1, y1 - 1, 0xFFC6C6C6);
        ctx.fill(x0 + 1, y0 + 1, x1 - 2, y0 + 3, 0xFFFFFFFF);
        ctx.fill(x0 + 1, y0 + 1, x0 + 3, y1 - 2, 0xFFFFFFFF);
        ctx.fill(x1 - 3, y0 + 3, x1 - 1, y1 - 1, 0xFF555555);
    }

    /** Ячейка слота (как в ванильном окне); x, y — угол предмета. */
    private static void slotCell(DrawContext ctx, int x, int y) {
        int sx = x - 1;
        int sy = y - 1;
        ctx.fill(sx, sy, sx + 18, sy + 18, 0xFF8B8B8B);
        ctx.fill(sx, sy, sx + 17, sy + 1, 0xFF373737);
        ctx.fill(sx, sy, sx + 1, sy + 17, 0xFF373737);
        ctx.fill(sx + 1, sy + 17, sx + 18, sy + 18, 0xFFFFFFFF);
        ctx.fill(sx + 17, sy + 1, sx + 18, sy + 18, 0xFFFFFFFF);
    }

    /** Кнопка «одежда» (рисуется сама): рубашка; панель открыта — подсвечена синим. */
    public static ButtonWidget toggleButton(int x, int y) {
        return new ToggleButton(x, y);
    }

    private static final class ToggleButton extends ButtonWidget {

        ToggleButton(int x, int y) {
            super(x, y, SIZE, SIZE, Text.empty(), b -> panelOpen = !panelOpen, DEFAULT_NARRATION_SUPPLIER);
        }

        @Override
        protected void renderButton(DrawContext c, int mouseX, int mouseY, float delta) {
            this.setTooltip(Tooltip.of(Text.translatable(panelOpen
                    ? "gui.basedefense.clothing.hide" : "gui.basedefense.clothing.show")));
            int x = getX();
            int y = getY();
            c.fill(x, y, x + SIZE, y + SIZE, 0xFF000000);
            c.fill(x + 1, y + 1, x + SIZE - 1, y + SIZE - 1,
                    this.isHovered() ? 0xFF8FA9C9 : (panelOpen ? 0xFF4F78B0 : 0xFF8B8B8B));
            c.fill(x + 1, y + 1, x + SIZE - 1, y + 2, 0x60FFFFFF);
            int ink = panelOpen ? 0xFFF2F2F2 : 0xFF3A3A3A;
            c.fill(x + 2, y + 2, x + 7, y + 4, ink);
            c.fill(x + 3, y + 4, x + 6, y + 7, ink);
        }
    }
}

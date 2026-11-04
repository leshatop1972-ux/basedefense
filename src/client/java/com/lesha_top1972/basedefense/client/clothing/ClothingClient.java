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
 * Слой одежды на клиенте: маленькая кнопка рядом со слотами брони (в инвентаре игрока и в меню поселенца)
 * переключает вид «броня» ⇄ «одежда». В режиме «одежда» на месте слотов брони — слоты одежды (подсвечены синим).
 * Режим общий для обоих меню и запоминается до выхода из игры.
 */
public final class ClothingClient {

    /** Подсветка слотов одежды. */
    public static final int TINT = 0x302A6FC0;
    /** Кнопка: 9×9. */
    private static final int SIZE = 9;

    private static boolean showClothes;

    private ClothingClient() {
    }

    public static boolean showClothes() {
        return showClothes;
    }

    public static void init() {
        // В инвентаре игрока — только в обычном окне (не в творческом)
        Clothing.playerView = () -> showClothes
                && MinecraftClient.getInstance().currentScreen instanceof InventoryScreen;
        Clothing.settlerView = () -> showClothes;
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

    /** Подсветка слотов одежды в инвентаре игрока (из InventoryScreenMixin.drawBackground). */
    public static void drawPlayerTint(DrawContext ctx, int left, int top) {
        if (!showClothes) {
            return;
        }
        for (int i = 0; i < Clothing.SLOTS; i++) {
            int x = left + 8;
            int y = top + 8 + i * 18;
            ctx.fill(x, y, x + 16, y + 16, TINT);
        }
    }

    /** Кнопка «одежда/броня» (рисуется сама, без текстуры): рубашка — показать одежду, щит — показать броню. */
    public static ButtonWidget toggleButton(int x, int y) {
        return new ToggleButton(x, y);
    }

    private static final class ToggleButton extends ButtonWidget {

        ToggleButton(int x, int y) {
            super(x, y, SIZE, SIZE, Text.empty(), b -> showClothes = !showClothes, DEFAULT_NARRATION_SUPPLIER);
        }

        @Override
        protected void renderButton(DrawContext c, int mouseX, int mouseY, float delta) {
            this.setTooltip(Tooltip.of(Text.translatable(showClothes
                    ? "gui.basedefense.clothing.show_armor" : "gui.basedefense.clothing.show_clothes")));
            int x = getX();
            int y = getY();
            boolean hover = this.isHovered();
            // рамка и фон
            c.fill(x, y, x + SIZE, y + SIZE, 0xFF000000);
            c.fill(x + 1, y + 1, x + SIZE - 1, y + SIZE - 1, hover ? 0xFF8FA9C9 : (showClothes ? 0xFF4F78B0 : 0xFF8B8B8B));
            c.fill(x + 1, y + 1, x + SIZE - 1, y + 2, 0x60FFFFFF);
            int ink = showClothes ? 0xFFF2F2F2 : 0xFF3A3A3A;
            if (showClothes) {
                // щит: показать броню
                c.fill(x + 2, y + 2, x + 7, y + 5, ink);
                c.fill(x + 3, y + 5, x + 6, y + 6, ink);
                c.fill(x + 4, y + 6, x + 5, y + 7, ink);
            } else {
                // рубашка: показать одежду
                c.fill(x + 2, y + 2, x + 7, y + 4, ink);
                c.fill(x + 3, y + 4, x + 6, y + 7, ink);
            }
        }
    }
}

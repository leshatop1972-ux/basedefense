package com.lesha_top1972.basedefense.client.screen;

import com.lesha_top1972.basedefense.artifact.AmuletRecipes;
import com.lesha_top1972.basedefense.block.AmuletTableBlockEntity;
import com.lesha_top1972.basedefense.block.ModBlocks;
import com.lesha_top1972.basedefense.item.ModItems;
import com.lesha_top1972.basedefense.screen.AmuletTableScreenHandler;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.ingame.HandledScreen;
import net.minecraft.client.sound.PositionedSoundInstance;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.inventory.Inventory;
import net.minecraft.item.ItemStack;
import net.minecraft.screen.slot.Slot;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.OrderedText;
import net.minecraft.text.Text;

import java.util.List;

import static com.lesha_top1972.basedefense.client.screen.UiKit.*;
import static com.lesha_top1972.basedefense.screen.AmuletTableScreenHandler.*;

/**
 * Меню стола амулетов (стиль «стекло»): слева схема «два амулета сверху → результат в центре ← кулон снизу»
 * и кнопка «Начать крафт» (во время работы — полоска и сколько осталось), справа — что может получиться
 * и с каким шансом (видно, только если игрок уже хоть раз крафтил эту пару), внизу — инвентарь.
 */
public class AmuletTableScreen extends HandledScreen<AmuletTableScreenHandler> {

    private static final int HEADER_H = 22;
    private static final int BTN_L = 30;
    private static final int BTN_R = 130;
    private static final int BTN_T = 128;
    private static final int BTN_B = 146;
    private static final int PANEL_L = 150;
    private static final int PANEL_R = 248;
    private static final int PANEL_T = 32;
    private static final int PANEL_B = 148;
    private static final int ROW_H = 26;

    private static final ItemStack ICON = new ItemStack(ModBlocks.AMULET_TABLE);
    private static final ItemStack GHOST_AMULET = new ItemStack(ModItems.LIFE_AMULET_1);
    private static final ItemStack GHOST_PENDANT = new ItemStack(ModItems.EMPTY_PENDANT);

    public AmuletTableScreen(AmuletTableScreenHandler handler, PlayerInventory inventory, Text title) {
        super(handler, inventory, title);
        this.backgroundWidth = W;
        this.backgroundHeight = H;
    }

    // ---------- Состояние ----------

    private Inventory table() {
        return handler.getTable();
    }

    /** Рецепт, о котором рассказывает правая панель: в работе — он, иначе — пара в слотах. */
    private int shownRecipe() {
        if (handler.isCrafting()) {
            return handler.craftingRecipe();
        }
        return AmuletRecipes.find(table().getStack(AmuletTableBlockEntity.LEFT), table().getStack(AmuletTableBlockEntity.RIGHT));
    }

    private boolean canStart() {
        return !handler.isCrafting()
                && table().getStack(AmuletTableBlockEntity.RESULT).isEmpty()
                && table().getStack(AmuletTableBlockEntity.PENDANT).isOf(ModItems.EMPTY_PENDANT)
                && shownRecipe() >= 0;
    }

    private boolean overButton(double mx, double my) {
        return !handler.isCrafting() && mx >= x + BTN_L && mx < x + BTN_R && my >= y + BTN_T && my < y + BTN_B;
    }

    // ---------- Рисование ----------

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        this.renderBackground(context);
        super.render(context, mouseX, mouseY, delta);
        drawRowTooltip(context, mouseX, mouseY);
        this.drawMouseoverTooltip(context, mouseX, mouseY);
    }

    @Override
    protected void drawBackground(DrawContext c, float delta, int mouseX, int mouseY) {
        int left = this.x;
        int top = this.y;
        int right = left + W;
        int bottom = top + H;

        panel(c, left, top, right, bottom);
        header(c, left + 4, top + 4, right - 4, HEADER_H);
        c.drawItem(ICON, left + 10, top + 7);
        embossed(c, this.textRenderer, this.title, left + 30, top + 11, TEXT_DARK);

        drawScheme(c, left, top, mouseX, mouseY);
        drawPanel(c, left, top);

        // Ячейки инвентаря игрока
        for (Slot slot : handler.slots) {
            if (slot.inventory != table()) {
                UiKit.slot(c, left + slot.x, top + slot.y, false);
            }
        }
        divider(c, left + 12, right - 12, top + INV_Y - 6);
    }

    /** Схема: линии от амулетов и кулона к центру, ячейки, кнопка или полоска работы. */
    private void drawScheme(DrawContext c, int left, int top, int mouseX, int mouseY) {
        inset(c, left + 8, top + PANEL_T, left + 142, top + PANEL_B, CONTENT_ALT);

        boolean crafting = handler.isCrafting();
        long time = this.client != null && this.client.world != null ? this.client.world.getTime() : 0;
        int pulse = (int) (Math.sin(time * 0.15) * 40 + 120);
        int lineColor = crafting ? (pulse << 24) | 0xC890FF : 0x50FFFFFF;

        int cx = left + RESULT_X + 8;
        int cy = top + RESULT_Y + 8;
        line(c, left + LEFT_X + 8, top + LEFT_Y + 8, cx, cy, lineColor);
        line(c, left + RIGHT_X + 8, top + RIGHT_Y + 8, cx, cy, lineColor);
        line(c, left + PENDANT_X + 8, top + PENDANT_Y + 8, cx, cy, lineColor);

        // Центр — крупная рамка результата
        int rl = left + RESULT_X - 5;
        int rt = top + RESULT_Y - 5;
        c.fill(rl, rt, rl + 26, rt + 26, 0xC0101216);
        outline(c, rl, rt, rl + 26, rt + 26, crafting ? (pulse << 24) | 0xD8A8FF : 0x70FFFFFF);
        corners(c, rl - 2, rt - 2, rl + 28, rt + 28, 5, crafting ? 0xC0D8A8FF : 0x60FFFFFF);

        slotWithGhost(c, left + LEFT_X, top + LEFT_Y, AmuletTableBlockEntity.LEFT, GHOST_AMULET);
        slotWithGhost(c, left + RIGHT_X, top + RIGHT_Y, AmuletTableBlockEntity.RIGHT, GHOST_AMULET);
        slotWithGhost(c, left + PENDANT_X, top + PENDANT_Y, AmuletTableBlockEntity.PENDANT, GHOST_PENDANT);
        UiKit.slot(c, left + RESULT_X, top + RESULT_Y, crafting);

        // Подписи
        c.drawText(this.textRenderer, Text.translatable("screen.basedefense.amulet_table.amulet"),
                left + LEFT_X - 14, top + LEFT_Y - 10, TEXT_MUTED, false);
        Text amulet2 = Text.translatable("screen.basedefense.amulet_table.amulet");
        c.drawText(this.textRenderer, amulet2, left + RIGHT_X + 30 - this.textRenderer.getWidth(amulet2),
                top + RIGHT_Y - 10, TEXT_MUTED, false);
        c.drawText(this.textRenderer, Text.translatable("screen.basedefense.amulet_table.pendant"),
                left + PENDANT_X + 22, top + PENDANT_Y + 4, TEXT_MUTED, false);

        if (crafting) {
            // Работа: полоска и сколько осталось
            int total = Math.max(1, handler.secondsTotal());
            int leftSec = handler.secondsLeft();
            float done = 1.0f - leftSec / (float) total;
            progress(c, left + BTN_L, top + BTN_T, left + BTN_R, top + BTN_T + 6, done, 0xFFD8A8FF, 0xFF8A5CC8);
            Text t = Text.translatable("screen.basedefense.amulet_table.left", leftSec / 60,
                    String.format("%02d", leftSec % 60));
            embossedCentered(c, this.textRenderer, t, left + (BTN_L + BTN_R) / 2, top + BTN_T + 10, TEXT_BODY);
        } else {
            boolean enabled = canStart();
            boolean hover = overButton(mouseX, mouseY);
            button(c, this.textRenderer, left + BTN_L, top + BTN_T, left + BTN_R, top + BTN_B,
                    Text.translatable("screen.basedefense.amulet_table.start"), !enabled ? 2 : hover ? 1 : 0);
        }
    }

    /** Ячейка; пустая — с бледным значком того, что сюда класть. */
    private void slotWithGhost(DrawContext c, int sx, int sy, int index, ItemStack ghost) {
        UiKit.slot(c, sx, sy, false);
        if (table().getStack(index).isEmpty()) {
            c.drawItem(ghost, sx, sy);
            c.getMatrices().push();
            c.getMatrices().translate(0, 0, 300);
            c.fill(sx, sy, sx + 16, sy + 16, 0xB0121418);
            c.getMatrices().pop();
        }
    }

    /** Правая панель: шансы того, что может получиться. */
    private void drawPanel(DrawContext c, int left, int top) {
        int pl = left + PANEL_L;
        int pr = left + PANEL_R;
        int pt = top + PANEL_T;
        int pb = top + PANEL_B;
        inset(c, pl, pt, pr, pb, CONTENT);
        List<OrderedText> title = this.textRenderer.wrapLines(
                Text.translatable("screen.basedefense.amulet_table.chances"), pr - pl - 8);
        int ty = pt + 4;
        for (OrderedText line : title) {
            c.drawText(this.textRenderer, line, pl + 4, ty, TEXT_DARK, true);
            ty += 10;
        }
        divider(c, pl + 4, pr - 4, ty + 1);
        int y0 = ty + 6;

        int recipe = shownRecipe();
        boolean bothIn = handler.isCrafting() || (!table().getStack(AmuletTableBlockEntity.LEFT).isEmpty()
                && !table().getStack(AmuletTableBlockEntity.RIGHT).isEmpty());
        if (!bothIn) {
            wrapped(c, Text.translatable("screen.basedefense.amulet_table.hint.put"), pl + 4, y0, pr - pl - 8, TEXT_MUTED);
            return;
        }
        if (recipe < 0) {
            wrapped(c, Text.translatable("screen.basedefense.amulet_table.hint.none"), pl + 4, y0, pr - pl - 8, BAD);
            return;
        }
        if (!handler.knows(recipe)) {
            embossedCentered(c, this.textRenderer, Text.literal("? ? ?"), (pl + pr) / 2, y0 + 2, WARN);
            wrapped(c, Text.translatable("screen.basedefense.amulet_table.hint.unknown"), pl + 4, y0 + 16,
                    pr - pl - 8, TEXT_MUTED);
            return;
        }
        AmuletRecipes.Recipe r = AmuletRecipes.get(recipe);
        if (r == null) {
            return;
        }
        int ry = y0;
        for (AmuletRecipes.Outcome o : r.outcomes()) {
            if (ry + ROW_H > pb - 2) {
                break;
            }
            card(c, pl + 3, ry, pr - 3, ry + ROW_H - 2, CARD, false);
            c.drawItem(new ItemStack(o.item()), pl + 6, ry + 4);
            String name = this.textRenderer.trimToWidth(new ItemStack(o.item()).getName().getString(), pr - pl - 32);
            c.drawText(this.textRenderer, name, pl + 25, ry + 3, TEXT_BODY, false);
            int barL = pl + 25;
            int barR = pr - 30;
            progress(c, barL, ry + 15, barR, ry + 19, o.chance() / 100.0f, chanceTop(o.chance()), chanceBottom(o.chance()));
            Text pct = Text.literal(o.chance() + "%");
            c.drawText(this.textRenderer, pct, pr - 6 - this.textRenderer.getWidth(pct), ry + 13, chanceTop(o.chance()), true);
            ry += ROW_H;
        }
    }

    private static int chanceTop(int chance) {
        return chance >= 50 ? GOOD_LIGHT : chance >= 10 ? WARN : 0xFFD8A8FF;
    }

    private static int chanceBottom(int chance) {
        return chance >= 50 ? GOOD : chance >= 10 ? 0xFFC89A30 : 0xFF8A5CC8;
    }

    private void wrapped(DrawContext c, Text text, int x, int y, int width, int color) {
        for (OrderedText line : this.textRenderer.wrapLines(text, width)) {
            c.drawText(this.textRenderer, line, x, y, color, false);
            y += 10;
        }
    }

    /** Подсказка над строкой шансов — полное название предмета. */
    private void drawRowTooltip(DrawContext c, int mouseX, int mouseY) {
        int recipe = shownRecipe();
        if (recipe < 0 || !handler.knows(recipe)) {
            return;
        }
        AmuletRecipes.Recipe r = AmuletRecipes.get(recipe);
        if (r == null) {
            return;
        }
        int pl = x + PANEL_L;
        int pr = x + PANEL_R;
        if (mouseX < pl || mouseX >= pr) {
            return;
        }
        int titleLines = this.textRenderer.wrapLines(Text.translatable("screen.basedefense.amulet_table.chances"),
                PANEL_R - PANEL_L - 8).size();
        int ry = y + PANEL_T + 4 + titleLines * 10 + 6;
        for (AmuletRecipes.Outcome o : r.outcomes()) {
            if (mouseY >= ry && mouseY < ry + ROW_H - 2) {
                c.drawItemTooltip(this.textRenderer, new ItemStack(o.item()), mouseX, mouseY);
                return;
            }
            ry += ROW_H;
        }
    }

    /** Линия толщиной 2 px (по точкам). */
    private static void line(DrawContext c, int x0, int y0, int x1, int y1, int color) {
        int steps = Math.max(Math.abs(x1 - x0), Math.abs(y1 - y0));
        for (int i = 0; i <= steps; i++) {
            int px = x0 + (x1 - x0) * i / Math.max(1, steps);
            int py = y0 + (y1 - y0) * i / Math.max(1, steps);
            c.fill(px - 1, py - 1, px + 1, py + 1, color);
        }
    }

    // ---------- Клики ----------

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button == 0 && overButton(mouseX, mouseY)) {
            MinecraftClient mc = MinecraftClient.getInstance();
            if (canStart()) {
                mc.getSoundManager().play(PositionedSoundInstance.master(SoundEvents.UI_BUTTON_CLICK, 1.0f));
                if (mc.interactionManager != null) {
                    mc.interactionManager.clickButton(this.handler.syncId, BUTTON_START);
                }
            } else {
                mc.getSoundManager().play(PositionedSoundInstance.master(SoundEvents.ENTITY_VILLAGER_NO, 1.0f));
            }
            return true;
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    protected void drawForeground(DrawContext context, int mouseX, int mouseY) {
        // Пусто: всё рисуем сами
    }

    @Override
    public boolean shouldPause() {
        return false;
    }
}

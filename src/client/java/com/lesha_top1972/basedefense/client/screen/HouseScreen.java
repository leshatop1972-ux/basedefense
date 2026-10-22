package com.lesha_top1972.basedefense.client.screen;

import com.lesha_top1972.basedefense.Basedefense;
import com.lesha_top1972.basedefense.entity.BuilderEntity;
import com.lesha_top1972.basedefense.screen.HouseScreenHandler;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.ingame.HandledScreen;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.registry.Registries;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.MathHelper;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Меню стройплощадки: чертёж-«накладная» со списком материалов.
 * Для каждого материала: сколько осталось поставить из скольких и сколько лежит в сундуках строителя.
 * Колёсико мыши прокручивает список, если он не помещается.
 */
public class HouseScreen extends HandledScreen<HouseScreenHandler> {

    // ---------- Цвета (как у меню Базы) ----------
    private static final int SHADOW      = 0x66000000;
    private static final int FRAME_DARK  = 0xFF5A4630;
    private static final int FRAME_LIGHT = 0xFFF6EBD0;
    private static final int PANEL       = 0xFFE6D3AB;
    private static final int HEADER      = 0xFFD8C08F;
    private static final int CONTENT     = 0xFFF2E7CC;
    private static final int EDGE        = 0xFF9C8058;
    private static final int CELL        = 0xFFFBF4E2;
    private static final int CELL_DONE   = 0xFFDCE6BE;
    private static final int TEXT_DARK   = 0xFF4A3622;
    private static final int TEXT_MUTED  = 0xFF8F7550;
    private static final int GOOD        = 0xFF4F7F2E;
    private static final int BAD         = 0xFFB03A2E;
    private static final int ACCENT      = 0xFFB0782F;

    private static final int COLS = 3;
    private static final int CELL_W = 104;
    private static final int CELL_H = 22;
    private static final int VISIBLE_ROWS = 7;
    private static final int LIST_TOP = 58;   // от верха окна

    private int scroll;
    private int cachedStep = -1;
    private Map<Item, Integer> remaining = Map.of();

    public HouseScreen(HouseScreenHandler handler, PlayerInventory inventory, Text title) {
        super(handler, inventory, title);
        this.backgroundWidth = 12 + COLS * CELL_W + (COLS - 1) * 4 + 12;
        this.backgroundHeight = LIST_TOP + VISIBLE_ROWS * CELL_H + 18;
    }

    private int rows() {
        return (handler.getItems().size() + COLS - 1) / COLS;
    }

    private int maxScroll() {
        return Math.max(0, rows() - VISIBLE_ROWS);
    }

    /** Остаток материалов пересчитываем, только когда стройка продвинулась. */
    private void updateRemaining() {
        int step = handler.getProp(HouseScreenHandler.PROP_STEP);
        if (step != cachedStep) {
            cachedStep = step;
            remaining = handler.remainingFrom(step);
        }
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        this.renderBackground(context);
        super.render(context, mouseX, mouseY, delta);
        int index = getCellAt(mouseX, mouseY);
        if (index >= 0) {
            Item item = handler.getItems().get(index);
            List<Text> lines = new ArrayList<>();
            lines.add(item.getName());
            lines.add(Text.translatable("screen.basedefense.house.tip.left",
                    remaining.getOrDefault(item, 0), handler.getTotal(index)).styled(s -> s.withColor(TEXT_MUTED & 0xFFFFFF)));
            lines.add(Text.translatable("screen.basedefense.house.tip.stock",
                    handler.getProp(HouseScreenHandler.PROP_AVAILABLE + index)).styled(s -> s.withColor(GOOD & 0xFFFFFF)));
            context.drawTooltip(this.textRenderer, lines, mouseX, mouseY);
        }
    }

    @Override
    protected void drawBackground(DrawContext context, float delta, int mouseX, int mouseY) {
        updateRemaining();
        int left = this.x;
        int top = this.y;
        int right = left + this.backgroundWidth;
        int bottom = top + this.backgroundHeight;

        // Рамка и заголовок
        context.fill(left + 3, top + 3, right + 3, bottom + 3, SHADOW);
        context.fill(left, top, right, bottom, FRAME_DARK);
        context.fill(left + 1, top + 1, right - 1, bottom - 1, FRAME_LIGHT);
        context.fill(left + 2, top + 2, right - 2, bottom - 2, PANEL);
        context.fill(left + 2, top + 2, right - 2, top + 20, HEADER);
        context.drawItem(new ItemStack(Registries.ITEM.get(
                new Identifier(Basedefense.MOD_ID, handler.getType().getId()))), left + 6, top + 3);
        context.drawText(this.textRenderer, this.title, left + 26, top + 7, TEXT_DARK, false);

        // Готовность
        int total = 0;
        int left2 = 0;
        for (int i = 0; i < handler.getItems().size(); i++) {
            total += handler.getTotal(i);
            left2 += remaining.getOrDefault(handler.getItems().get(i), 0);
        }
        int percent = total == 0 ? 100 : (total - left2) * 100 / total;
        Text pct = Text.translatable("screen.basedefense.house.progress", percent);
        context.drawText(this.textRenderer, pct, right - 8 - this.textRenderer.getWidth(pct), top + 7, TEXT_DARK, false);

        // Состояние стройки
        context.drawText(this.textRenderer, statusText(left2), left + 8, top + 26, statusColor(), false);

        // Полоска готовности
        int barL = left + 8, barR = right - 8, barT = top + 39;
        context.fill(barL - 1, barT - 1, barR + 1, barT + 7, EDGE);
        context.fill(barL, barT, barR, barT + 6, CONTENT);
        context.fill(barL, barT, barL + (barR - barL) * percent / 100, barT + 6, GOOD);

        // Список материалов
        int listL = left + 12;
        int listT = top + LIST_TOP;
        context.fill(listL - 3, listT - 3, right - 9, listT + VISIBLE_ROWS * CELL_H + 1, EDGE);
        context.fill(listL - 2, listT - 2, right - 10, listT + VISIBLE_ROWS * CELL_H, CONTENT);
        List<Item> items = handler.getItems();
        for (int i = 0; i < items.size(); i++) {
            int row = i / COLS - scroll;
            if (row < 0 || row >= VISIBLE_ROWS) {
                continue;
            }
            int cx = listL + (i % COLS) * (CELL_W + 4);
            int cy = listT + row * CELL_H;
            drawCell(context, i, items.get(i), cx, cy);
        }
        // Полоса прокрутки
        if (maxScroll() > 0) {
            int trackT = listT, trackB = listT + VISIBLE_ROWS * CELL_H - 2;
            int thumbH = Math.max(10, (trackB - trackT) * VISIBLE_ROWS / rows());
            int thumbT = trackT + (trackB - trackT - thumbH) * scroll / maxScroll();
            context.fill(right - 8, thumbT, right - 5, thumbT + thumbH, ACCENT);
        }

        // Подсказка внизу
        context.drawText(this.textRenderer, Text.translatable("screen.basedefense.house.hint"),
                left + 8, bottom - 13, TEXT_MUTED, false);
    }

    private void drawCell(DrawContext context, int index, Item item, int cx, int cy) {
        int left = remaining.getOrDefault(item, 0);
        int have = handler.getProp(HouseScreenHandler.PROP_AVAILABLE + index);
        boolean done = left == 0;
        context.fill(cx, cy, cx + CELL_W, cy + CELL_H - 2, done ? CELL_DONE : CELL);
        context.drawItem(new ItemStack(item), cx + 2, cy + 2);
        context.drawText(this.textRenderer, left + " / " + handler.getTotal(index), cx + 21, cy + 2,
                done ? GOOD : TEXT_DARK, false);
        Text stock = done ? Text.translatable("screen.basedefense.house.done")
                : Text.translatable("screen.basedefense.house.stock", have);
        context.drawText(this.textRenderer, stock, cx + 21, cy + 11,
                done ? GOOD : (have >= left ? GOOD : BAD), false);
    }

    private Text statusText(int blocksLeft) {
        int status = handler.getProp(HouseScreenHandler.PROP_STATUS);
        int level = handler.getProp(HouseScreenHandler.PROP_LEVEL);
        return switch (status) {
            case HouseScreenHandler.STATUS_NO_BUILDER -> Text.translatable("screen.basedefense.house.status.no_builder");
            case HouseScreenHandler.STATUS_QUEUE -> Text.translatable("screen.basedefense.house.status.queue",
                    handler.getProp(HouseScreenHandler.PROP_QUEUE));
            case HouseScreenHandler.STATUS_WALKING -> Text.translatable("screen.basedefense.house.status.walking");
            case HouseScreenHandler.STATUS_BUILDING -> Text.translatable("screen.basedefense.house.status.building",
                    level, formatTime(BuilderEntity.estimateSeconds(blocksLeft, level)));
            case HouseScreenHandler.STATUS_MISSING -> Text.translatable("screen.basedefense.house.status.missing",
                    missingName());
            default -> Text.translatable("screen.basedefense.house.status.no_site");
        };
    }

    private int statusColor() {
        int status = handler.getProp(HouseScreenHandler.PROP_STATUS);
        return switch (status) {
            case HouseScreenHandler.STATUS_BUILDING, HouseScreenHandler.STATUS_WALKING -> GOOD;
            case HouseScreenHandler.STATUS_QUEUE -> TEXT_MUTED;
            default -> BAD;
        };
    }

    private Text missingName() {
        int raw = handler.getProp(HouseScreenHandler.PROP_MISSING);
        return raw < 0 ? Text.literal("?") : Registries.ITEM.get(raw).getName();
    }

    private static String formatTime(int seconds) {
        int m = seconds / 60;
        int s = seconds % 60;
        return m + ":" + (s < 10 ? "0" : "") + s;
    }

    private int getCellAt(double mouseX, double mouseY) {
        int listL = this.x + 12;
        int listT = this.y + LIST_TOP;
        for (int i = 0; i < handler.getItems().size(); i++) {
            int row = i / COLS - scroll;
            if (row < 0 || row >= VISIBLE_ROWS) {
                continue;
            }
            int cx = listL + (i % COLS) * (CELL_W + 4);
            int cy = listT + row * CELL_H;
            if (mouseX >= cx && mouseX < cx + CELL_W && mouseY >= cy && mouseY < cy + CELL_H - 2) {
                return i;
            }
        }
        return -1;
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double amount) {
        scroll = MathHelper.clamp(scroll - (int) Math.signum(amount), 0, maxScroll());
        return true;
    }

    @Override
    protected void drawForeground(DrawContext context, int mouseX, int mouseY) {
        // Пусто: всё рисуем сами
    }
}

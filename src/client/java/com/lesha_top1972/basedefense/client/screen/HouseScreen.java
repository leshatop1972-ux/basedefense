package com.lesha_top1972.basedefense.client.screen;

import com.lesha_top1972.basedefense.Basedefense;
import com.lesha_top1972.basedefense.entity.BuilderEntity;
import com.lesha_top1972.basedefense.screen.HouseScreenHandler;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.ingame.HandledScreen;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.registry.Registries;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.MathHelper;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static com.lesha_top1972.basedefense.client.screen.UiKit.*;

/**
 * Меню стройплощадки (стиль «тёмное стекло») — «чертёж» дома: что делает строитель, общая готовность
 * и карточка на каждый материал: сколько уже поставлено из скольких и сколько лежит
 * в сундуках строителя. Колёсико мыши прокручивает список.
 */
public class HouseScreen extends HandledScreen<HouseScreenHandler> {

    private static final int COLS = 3;
    private static final int CELL_W = 108;
    private static final int CELL_H = 26;
    private static final int GAP = 4;
    private static final int VISIBLE_ROWS = 6;
    private static final int PAD = 12;
    private static final int HEADER_H = 22;
    private static final int LIST_TOP = 62;   // от верха окна

    private int scroll;
    private int cachedStep = -1;
    private Map<Item, Integer> remaining = Map.of();

    public HouseScreen(HouseScreenHandler handler, PlayerInventory inventory, Text title) {
        super(handler, inventory, title);
        this.backgroundWidth = PAD * 2 + COLS * CELL_W + (COLS - 1) * GAP + 6;
        this.backgroundHeight = LIST_TOP + VISIBLE_ROWS * (CELL_H + GAP) + 10;
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

    private int placed(int index) {
        return handler.getTotal(index) - remaining.getOrDefault(handler.getItems().get(index), 0);
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
            lines.add(Text.translatable("screen.basedefense.house.tip.placed", placed(index), handler.getTotal(index))
                    .styled(s -> s.withColor(0xBBBBBB)));
            int left = remaining.getOrDefault(item, 0);
            int have = handler.getProp(HouseScreenHandler.PROP_AVAILABLE + index);
            lines.add(Text.translatable("screen.basedefense.house.tip.stock", have)
                    .styled(s -> s.withColor(have >= left ? 0x8BD05A : 0xFF7A6A)));
            if (left > have) {
                lines.add(Text.translatable("screen.basedefense.house.tip.bring", left - have)
                        .styled(s -> s.withColor(0xFFD45A)));
            }
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

        panel(context, left, top, right, bottom);
        header(context, left + 4, top + 4, right - 4, HEADER_H);

        // Заголовок: иконка дома, название, процент готовности
        context.drawItem(new ItemStack(Registries.ITEM.get(
                new Identifier(Basedefense.MOD_ID, handler.getHouseType().getId()))), left + 10, top + 7);
        embossed(context, this.textRenderer, this.title, left + 30, top + 11, TEXT_DARK);

        int total = 0;
        int placedAll = 0;
        for (int i = 0; i < handler.getItems().size(); i++) {
            total += handler.getTotal(i);
            placedAll += placed(i);
        }
        int percent = total == 0 ? 100 : placedAll * 100 / total;
        Text pct = Text.translatable("screen.basedefense.house.progress", percent);
        int pillW = this.textRenderer.getWidth(pct) + 8;
        pill(context, this.textRenderer, right - 12 - pillW, top + 9, pct,
                percent >= 100 ? 0xC0234A33 : 0xC0123A38, percent >= 100 ? GOOD_LIGHT : TEAL_LIGHT);

        // Состояние стройки: значок + текст
        int statusY = top + HEADER_H + 11;
        context.drawItem(new ItemStack(statusIcon()), left + 10, statusY - 4);
        embossed(context, this.textRenderer, statusText(total - placedAll), left + 30, statusY, statusColor());

        // Полоска готовности
        progress(context, left + 12, top + 48, right - 12, top + 54, percent / 100.0f, GOOD_LIGHT, GOOD);

        // Список материалов
        int listL = left + PAD;
        int listT = top + LIST_TOP;
        int listR = listL + COLS * CELL_W + (COLS - 1) * GAP;
        int listB = listT + VISIBLE_ROWS * (CELL_H + GAP) - GAP;
        inset(context, listL - 3, listT - 3, listR + 3, listB + 3, CONTENT_ALT);
        List<Item> items = handler.getItems();
        for (int i = 0; i < items.size(); i++) {
            int row = i / COLS - scroll;
            if (row < 0 || row >= VISIBLE_ROWS) {
                continue;
            }
            int cx = listL + (i % COLS) * (CELL_W + GAP);
            int cy = listT + row * (CELL_H + GAP);
            boolean hover = mouseX >= cx && mouseX < cx + CELL_W && mouseY >= cy && mouseY < cy + CELL_H;
            drawCell(context, i, items.get(i), cx, cy, hover);
        }
        // Полоса прокрутки
        if (maxScroll() > 0) {
            int trackL = listR + 5;
            context.fill(trackL, listT - 2, trackL + 3, listB + 2, 0x80000000);
            int thumbH = Math.max(12, (listB - listT + 4) * VISIBLE_ROWS / rows());
            int thumbT = listT - 2 + (listB - listT + 4 - thumbH) * scroll / maxScroll();
            context.fill(trackL, thumbT, trackL + 3, thumbT + thumbH, GOLD);
            context.fill(trackL, thumbT, trackL + 1, thumbT + thumbH, GOLD_LIGHT);
        }
    }

    private void drawCell(DrawContext context, int index, Item item, int cx, int cy, boolean hover) {
        int total = handler.getTotal(index);
        int done = placed(index);
        int left = total - done;
        int have = handler.getProp(HouseScreenHandler.PROP_AVAILABLE + index);
        boolean complete = left == 0;

        card(context, cx, cy, cx + CELL_W, cy + CELL_H, complete ? 0xB01A3A2B : (hover ? CARD_HOVER : CARD), hover);
        // Иконка в маленьком «гнезде»
        context.fill(cx + 3, cy + 3, cx + 21, cy + 21, complete ? 0x403FAF6E : 0x60000000);
        context.drawItem(new ItemStack(item), cx + 4, cy + 4);

        // Сколько поставлено из скольких
        Text count = Text.literal(done + " / " + total);
        context.drawText(this.textRenderer, count, cx + 25, cy + 4, complete ? GOOD : TEXT_DARK, false);
        // Запас в сундуках строителя
        Text stock = complete ? Text.translatable("screen.basedefense.house.done")
                : Text.translatable("screen.basedefense.house.stock", have);
        int stockColor = complete ? GOOD : (have >= left ? GOOD : BAD);
        context.drawText(this.textRenderer, stock, cx + 25, cy + 14, stockColor, false);

        // Тонкая полоска внизу карточки: доля поставленного
        int barL = cx + 3, barR = cx + CELL_W - 3, barY = cy + CELL_H - 4;
        context.fill(barL, barY, barR, barY + 2, 0x80000000);
        int w = total == 0 ? 0 : (barR - barL) * done / total;
        if (w > 0) {
            context.fill(barL, barY, barL + w, barY + 2, complete ? GOOD_LIGHT : GOLD);
        }
    }

    private Item statusIcon() {
        return switch (handler.getProp(HouseScreenHandler.PROP_STATUS)) {
            case HouseScreenHandler.STATUS_BUILDING -> Items.IRON_SHOVEL;
            case HouseScreenHandler.STATUS_WALKING -> Items.LEATHER_BOOTS;
            case HouseScreenHandler.STATUS_MISSING -> {
                int raw = handler.getProp(HouseScreenHandler.PROP_MISSING);
                yield raw < 0 ? Items.BARRIER : Registries.ITEM.get(raw);
            }
            case HouseScreenHandler.STATUS_QUEUE -> Items.CLOCK;
            default -> Items.BARRIER;
        };
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
        return switch (handler.getProp(HouseScreenHandler.PROP_STATUS)) {
            case HouseScreenHandler.STATUS_BUILDING, HouseScreenHandler.STATUS_WALKING -> GOOD;
            case HouseScreenHandler.STATUS_QUEUE -> TEXT_BODY;
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
        int listL = this.x + PAD;
        int listT = this.y + LIST_TOP;
        for (int i = 0; i < handler.getItems().size(); i++) {
            int row = i / COLS - scroll;
            if (row < 0 || row >= VISIBLE_ROWS) {
                continue;
            }
            int cx = listL + (i % COLS) * (CELL_W + GAP);
            int cy = listT + row * (CELL_H + GAP);
            if (mouseX >= cx && mouseX < cx + CELL_W && mouseY >= cy && mouseY < cy + CELL_H) {
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

package com.lesha_top1972.basedefense.client.screen;

import com.lesha_top1972.basedefense.block.ModBlocks;
import com.lesha_top1972.basedefense.item.ModItems;
import com.lesha_top1972.basedefense.research.ResearchManager;
import com.lesha_top1972.basedefense.screen.BaseScreenHandler;
import com.lesha_top1972.basedefense.settlement.SettlementItems;
import com.lesha_top1972.basedefense.settlement.SettlementManager;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.ingame.HandledScreen;
import net.minecraft.client.sound.PositionedSoundInstance;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.screen.slot.Slot;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.OrderedText;
import net.minecraft.text.Text;
import net.minecraft.util.Util;

import java.util.ArrayList;
import java.util.List;

/**
 * Экран меню базы: заголовок «База» и вкладка «Потребности» — запасы поселения и слот,
 * куда кладут еду. Исследований больше нет: все постройки сразу доступны в строительном планшете.
 * Всё рисуется кодом (заливками, стиль «тёмное стекло» — UiKit), поэтому окно подстраивается под размер экрана.
 */
public class BaseScreen extends HandledScreen<BaseScreenHandler> {

    // ---------- Палитра (тёмное стекло + бирюза, основа — UiKit) ----------
    private static final int CONTENT       = UiKit.CONTENT;
    private static final int GRID_DOT      = 0x1433C9BE;
    private static final int TEXT_DARK     = UiKit.TEXT_DARK;
    private static final int TEXT_BODY     = UiKit.TEXT_BODY;
    private static final int TEXT_MUTED    = UiKit.TEXT_MUTED;
    private static final int ACCENT        = UiKit.TEAL;


    private static final int GOOD          = UiKit.GOOD;
    private static final int BAD           = UiKit.BAD;

    // ---------- Размеры ----------
    private static final int HEADER_H = 20;
    private static final int TAB_H = 18;
    private static final int TAB_W_MAX = 92;
    private static final int PAD = 9;

    /** Вкладки. Названия берутся из файлов перевода (lang). */
    private static final Text[] TABS = {
            Text.translatable("screen.basedefense.base.tab.needs")
    };
    /** Номер вкладки «Потребности». */
    private static final int NEEDS_TAB = 0;

    private int selectedTab = 0;

    // Прямоугольник области контента (считается в init)
    private int contentLeft, contentTop, contentRight, contentBottom;

    public BaseScreen(BaseScreenHandler handler, PlayerInventory inventory, Text title) {
        super(handler, inventory, title);
    }

    @Override
    protected void init() {
        this.backgroundWidth = Math.max(300, Math.min(this.width - 40, 420));
        this.backgroundHeight = Math.max(228, Math.min(this.height - 30, 264));
        super.init();
        contentLeft = this.x + PAD;
        contentTop = getTabsTop() + TAB_H;
        contentRight = this.x + this.backgroundWidth - PAD;
        contentBottom = this.y + this.backgroundHeight - PAD;
        handler.setSlotsVisible(selectedTab == NEEDS_TAB);
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        this.renderBackground(context);
        super.render(context, mouseX, mouseY, delta);

        this.drawMouseoverTooltip(context, mouseX, mouseY);
    }

    @Override
    protected void drawBackground(DrawContext context, float delta, int mouseX, int mouseY) {
        int left = this.x;
        int top = this.y;
        int right = left + this.backgroundWidth;
        int bottom = top + this.backgroundHeight;

        // Окно и заголовок «База»
        UiKit.panel(context, left, top, right, bottom);
        int headerTop = top + 4;
        UiKit.header(context, left + 4, headerTop, right - 4, HEADER_H);
        context.drawItem(new ItemStack(ModBlocks.BASE), left + 9, headerTop + 2);
        UiKit.embossed(context, this.textRenderer, this.title, left + 29, headerTop + 6, TEXT_DARK);

        // Монеты игрока — справа в заголовке, рядом — сколько жителей
        int coinsLeft = drawCoinCounter(context, right - PAD - 3, headerTop + 2);
        Text settlers = Text.translatable("screen.basedefense.base.settlers_short",
                handler.getProp(BaseScreenHandler.PROP_SETTLERS));
        UiKit.pill(context, this.textRenderer, coinsLeft - 12 - this.textRenderer.getWidth(settlers) - 8,
                headerTop + 4, settlers, UiKit.TEAL_LIGHT);

        // Область контента
        UiKit.inset(context, contentLeft, contentTop, contentRight, contentBottom, CONTENT);
        drawGrid(context);

        // Вкладки
        int tabsTop = getTabsTop();
        for (int i = 0; i < TABS.length; i++) {
            drawTab(context, i, tabsTop, mouseX, mouseY);
        }

        drawNeeds(context, mouseX, mouseY);
    }

    // ---------------------------------------------------------------------
    // Элементы
    // ---------------------------------------------------------------------

    /** @return левый край счётчика (чтобы рядом нарисовать что-то ещё) */
    private int drawCoinCounter(DrawContext context, int rightX, int y) {
        String count = String.valueOf(ResearchManager.countCoins(this.client.player));
        int textW = this.textRenderer.getWidth(count);
        UiKit.embossed(context, this.textRenderer, Text.literal(count), rightX - textW, y + 4, TEXT_DARK);
        context.drawItem(new ItemStack(ModItems.RESEARCH_COIN), rightX - textW - 18, y);
        return rightX - textW - 18;
    }

    private void drawTab(DrawContext context, int index, int tabsTop, int mouseX, int mouseY) {
        int tabLeft = getTabLeft(index);
        int tabW = getTabWidth();
        int tabRight = tabLeft + tabW;
        boolean selected = index == selectedTab;
        boolean hovered = isOverTab(index, mouseX, mouseY);

        int top = selected ? tabsTop - 2 : tabsTop;
        int bottom = tabsTop + TAB_H;

        if (selected) {
            // Выбранная вкладка «сливается» с областью контента
            context.fill(tabLeft - 1, top - 1, tabRight + 1, bottom, UiKit.INSET_DARK);
            context.fillGradient(tabLeft, top, tabRight, bottom, 0x6033C9BE, 0x18167A73);
            context.fill(tabLeft, top, tabRight, top + 2, UiKit.TEAL);
            context.fill(tabLeft, top, tabRight, top + 1, UiKit.TEAL_LIGHT);
        } else {
            context.fill(tabLeft - 1, top - 1, tabRight + 1, bottom - 1, UiKit.INSET_DARK);
            context.fillGradient(tabLeft, top, tabRight, bottom - 1,
                    hovered ? 0xD0323C46 : UiKit.HEADER_TOP, hovered ? 0xD0242B32 : UiKit.HEADER_BOTTOM);
            context.fill(tabLeft, top, tabRight, top + 1, hovered ? UiKit.TEAL_DARK : 0x14FFFFFF);
        }

        Text label = TABS[index];
        int textX = tabLeft + (tabW - this.textRenderer.getWidth(label)) / 2;
        int textY = top + (bottom - top - this.textRenderer.fontHeight) / 2 + 1;
        UiKit.embossed(context, this.textRenderer, label, textX, textY, selected ? 0xFFFFFFFF : (hovered ? TEXT_BODY : TEXT_MUTED));
    }

    private void drawGrid(DrawContext context) {
        for (int gx = contentLeft + 8; gx < contentRight - 2; gx += 12) {
            for (int gy = contentTop + 8; gy < contentBottom - 2; gy += 12) {
                context.fill(gx, gy, gx + 1, gy + 1, ((gx - contentLeft) / 12 + (gy - contentTop) / 12) % 4 == 0
                        ? 0x3033C9BE : GRID_DOT);
            }
        }
    }

    /** Чуть светлее цвет (для верха градиента); прозрачность сохраняется. */
    private static int lighten(int color) {
        int r = Math.min(255, ((color >> 16) & 0xFF) + 14);
        int g = Math.min(255, ((color >> 8) & 0xFF) + 14);
        int b = Math.min(255, (color & 0xFF) + 14);
        return (color & 0xFF000000) | (r << 16) | (g << 8) | b;
    }

    // ---------------------------------------------------------------------
    // Вкладка «Потребности»
    // ---------------------------------------------------------------------

    private void drawNeeds(DrawContext context, int mouseX, int mouseY) {
        int settlers = handler.getProp(BaseScreenHandler.PROP_SETTLERS);
        int food = handler.getProp(BaseScreenHandler.PROP_FOOD);
        boolean frozen = food <= SettlementManager.FREEZE_AT;
        int left = this.x + 18;
        int right = contentRight - 8;

        // Жители и общее состояние
        int y1 = this.y + 60;
        UiKit.card(context, left - 4, y1 - 6, right + 4, y1 + 40, UiKit.CARD, false);
        context.drawItem(new ItemStack(ModItems.FARMER_SPAWN_EGG), left, y1 - 4);
        UiKit.embossed(context, this.textRenderer, Text.translatable("screen.basedefense.needs.settlers", settlers),
                left + 20, y1, TEXT_DARK);
        Text status = settlers == 0 ? Text.translatable("screen.basedefense.needs.status.empty")
                : frozen ? Text.translatable("screen.basedefense.needs.status.frozen")
                : Text.translatable("screen.basedefense.needs.status.ok");
        context.drawText(this.textRenderer, status, right - this.textRenderer.getWidth(status), y1,
                settlers == 0 ? TEXT_MUTED : (frozen ? BAD : GOOD), false);

        // Запас еды + полоска
        int y2 = this.y + 78;
        int foodPerDay = handler.getProp(BaseScreenHandler.PROP_FOOD_DAY);
        context.drawItem(new ItemStack(Items.BREAD), left, y2 - 4);
        Text foodText = Text.translatable("screen.basedefense.needs.food", food);
        UiKit.embossed(context, this.textRenderer, foodText, left + 20, y2, counterColor(food));
        Text foodInfo = foodPerDay == 0 ? Text.empty()
                : food > 0 ? Text.translatable("screen.basedefense.needs.food_days", foodPerDay, food / foodPerDay)
                : Text.translatable("screen.basedefense.needs.per_day", foodPerDay);
        context.drawText(this.textRenderer, foodInfo, right - this.textRenderer.getWidth(foodInfo), y2, TEXT_MUTED, false);
        drawFoodBar(context, left + 20, y2 + 12, right, food);

        // Подпись над инвентарём
        context.drawText(this.textRenderer, Text.translatable("screen.basedefense.needs.inventory"),
                this.x + BaseScreenHandler.INV_X, this.y + BaseScreenHandler.INV_Y - 10, TEXT_MUTED, false);

        // Рамки слотов
        for (Slot slot : handler.slots) {
            drawSlotFrame(context, this.x + slot.x, this.y + slot.y, slot.id == 0);
        }

        // Подпись к слоту «положить»
        int dx = this.x + BaseScreenHandler.DEPOSIT_X;
        int dy = this.y + BaseScreenHandler.DEPOSIT_Y;
        UiKit.embossed(context, this.textRenderer, Text.translatable("screen.basedefense.needs.deposit"),
                dx - 2, dy - 12, TEXT_DARK);
        int hintY = dy + 22;
        for (OrderedText line : this.textRenderer.wrapLines(
                Text.translatable("screen.basedefense.needs.deposit_hint"), right - dx + 2)) {
            if (hintY + 9 > contentBottom - 2) {
                break;
            }
            context.drawText(this.textRenderer, line, dx - 2, hintY, TEXT_MUTED, false);
            hintY += 10;
        }
    }

    /** Цвет счётчика: зелёный — в плюсе, оранжевый — в минусе, красный — жители замерли. */
    private static int counterColor(int value) {
        if (value <= SettlementManager.FREEZE_AT) {
            return BAD;
        }
        return value < 0 ? UiKit.WARN : GOOD;
    }

    /** Полоска запаса еды: от -20 до максимума, отметка «0». */
    private void drawFoodBar(DrawContext context, int barLeft, int top, int barRight, int value) {
        int min = SettlementManager.FREEZE_AT;
        int max = Math.max(200, value);
        int h = 6;
        UiKit.inset(context, barLeft, top, barRight, top + h, 0xA0000000);
        int clamped = Math.max(min, Math.min(max, value));
        int zeroX = barLeft + (barRight - barLeft) * (-min) / (max - min);
        int valueX = barLeft + (barRight - barLeft) * (clamped - min) / (max - min);
        int color = counterColor(value);
        context.fillGradient(Math.min(zeroX, valueX), top, Math.max(zeroX, valueX), top + h, lighten(lighten(color)), color);
        context.fill(Math.min(zeroX, valueX), top, Math.max(zeroX, valueX), top + 1, 0x55FFFFFF);
        context.fill(zeroX, top - 1, zeroX + 1, top + h + 1, 0xFFFFFFFF); // отметка «0»
    }

    private void drawSlotFrame(DrawContext context, int sx, int sy, boolean highlight) {
        UiKit.slot(context, sx, sy, highlight);
    }

    /** В подсказке к предмету показываем, сколько он даёт Базе. */
    @Override
    protected List<Text> getTooltipFromItem(ItemStack stack) {
        List<Text> lines = new ArrayList<>(super.getTooltipFromItem(stack));
        if (selectedTab == NEEDS_TAB) {
            int food = SettlementItems.foodValue(stack);
            if (food > 0) {
                lines.add(Text.translatable("tooltip.basedefense.food_points", food)
                        .styled(s -> s.withColor(GOOD & 0xFFFFFF)));
            }
        }
        return lines;
    }

    // ---------------------------------------------------------------------
    // Координаты
    // ---------------------------------------------------------------------

    private int getTabsTop() {
        return this.y + 4 + HEADER_H + 8;
    }

    private int getTabLeft(int index) {
        return this.x + PAD + index * (getTabWidth() + 3);
    }

    /** Ширина вкладки: все вкладки должны влезть в окно. */
    private int getTabWidth() {
        int fit = (this.backgroundWidth - 2 * PAD - 3 * (TABS.length - 1)) / TABS.length;
        return Math.min(TAB_W_MAX, fit);
    }

    private boolean isOverTab(int index, double mouseX, double mouseY) {
        int tabLeft = getTabLeft(index);
        int tabsTop = getTabsTop();
        return mouseX >= tabLeft && mouseX < tabLeft + getTabWidth() && mouseY >= tabsTop - 2 && mouseY < tabsTop + TAB_H;
    }

    // ---------------------------------------------------------------------
    // Клики
    // ---------------------------------------------------------------------

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button == 0) {
            // Вкладки
            for (int i = 0; i < TABS.length; i++) {
                if (isOverTab(i, mouseX, mouseY)) {
                    if (selectedTab != i) {
                        selectedTab = i;
                        handler.setSlotsVisible(i == NEEDS_TAB);
                        playClick();
                    }
                    return true;
                }
            }

        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    private void playClick() {
        MinecraftClient.getInstance().getSoundManager()
                .play(PositionedSoundInstance.master(SoundEvents.UI_BUTTON_CLICK, 1.0f));
    }

    @Override
    protected void drawForeground(DrawContext context, int mouseX, int mouseY) {
        // Пусто: стандартные надписи («Инвентарь» и т.п.) не нужны, всё рисуем сами.
    }
}

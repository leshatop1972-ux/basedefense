package com.lesha_top1972.basedefense.client.screen;

import com.lesha_top1972.basedefense.block.ModBlocks;
import com.lesha_top1972.basedefense.item.ModItems;
import com.lesha_top1972.basedefense.research.ResearchManager;
import com.lesha_top1972.basedefense.research.Technology;
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
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

/**
 * Экран меню базы: заголовок «База», строка вкладок и область с древом исследований.
 * Клик по квадратику технологии открывает справа панель с описанием и кнопкой «Исследовать».
 * Вкладка «Потребности» — запасы поселения, склад и слот, куда кладут еду и ресурсы.
 * Всё рисуется кодом (заливками, стиль — UiKit), поэтому окно подстраивается под размер экрана.
 */
public class BaseScreen extends HandledScreen<BaseScreenHandler> {

    // ---------- Палитра (бежевые оттенки) ----------
    private static final int SHADOW        = 0x55000000;
    private static final int FRAME_DARK    = 0xFF5C4630;
    private static final int FRAME_LIGHT   = 0xFFFFF7E3;
    private static final int FRAME_MID     = 0xFFB89B6E;
    private static final int PANEL         = 0xFFE6D3AB;
    private static final int HEADER        = 0xFFD8C08F;
    private static final int TAB_IDLE      = 0xFFD2BA88;
    private static final int TAB_HOVER     = 0xFFE0CB9E;
    private static final int CONTENT       = 0xFFF2E7CC;
    private static final int CONTENT_EDGE  = 0xFF9C8058;
    private static final int GRID_DOT      = 0x22745A38;
    private static final int TEXT_DARK     = 0xFF4A3622;
    private static final int TEXT_BODY     = 0xFF6A5236;
    private static final int TEXT_MUTED    = 0xFF8F7550;
    private static final int ACCENT        = 0xFFB0782F;

    private static final int NODE_BG       = 0xFFE9D9B4;
    private static final int NODE_BG_HOVER = 0xFFF4E8CC;
    private static final int NODE_EDGE     = 0xFF8C6E45;
    private static final int NODE_DONE_BG  = 0xFFDCE6BE;
    private static final int NODE_DONE_EDGE = 0xFF5E8C3A;
    private static final int NODE_LOCKED_BG = 0xFFD9CDB2;
    private static final int NODE_LOCKED_EDGE = 0xFFA89A80;
    private static final int LOCK_OVERLAY  = 0x99E2D6BC;
    private static final int LINK_OPEN     = 0xFF7FA35A;
    private static final int LINK_CLOSED   = 0xFFB5A27F;

    private static final int INFO_BG       = 0xFFFBF4E2;
    private static final int BUTTON        = 0xFFC9A66B;
    private static final int BUTTON_HOVER  = 0xFFD9B67A;
    private static final int BUTTON_EDGE   = 0xFF7A5A30;
    private static final int GOOD          = 0xFF4F7F2E;
    private static final int BAD           = 0xFFB03A2E;

    // ---------- Размеры ----------
    private static final int HEADER_H = 20;
    private static final int TAB_H = 18;
    private static final int TAB_W_MAX = 92;
    private static final int PAD = 9;
    private static final int NODE = 28;          // размер квадратика технологии
    private static final int NODE_STEP = 44;     // шаг сетки древа
    private static final int INFO_W = 170;       // ширина панели описания
    private static final int BUTTON_H = 18;

    /** Вкладки. Названия берутся из файлов перевода (lang). */
    private static final Text[] TABS = {
            Text.translatable("screen.basedefense.base.tab.economy"),   // «Хозяйство» — древо исследований
            Text.translatable("screen.basedefense.base.tab.needs")
    };
    /** Номер вкладки «Потребности». */
    private static final int NEEDS_TAB = 1;
    private static final int INFO_CIRCLE = 0xFF3B7DD8;
    private static final int INFO_CIRCLE_HOVER = 0xFF5A9BF0;

    private int selectedTab = 0;
    @Nullable
    private Technology selectedTech = null;
    private long notEnoughUntil = 0;

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

        // Синий кружок «?» в панели описания — подробности о работе жителя
        if (selectedTech != null && isOverWorkInfo(mouseX, mouseY)) {
            context.drawOrderedTooltip(this.textRenderer,
                    this.textRenderer.wrapLines(selectedTech.getWorkInfo(), 230), mouseX, mouseY);
        }

        // Подсказка при наведении на квадратик
        Technology hovered = getNodeAt(mouseX, mouseY);
        if (hovered != null) {
            List<Text> lines = new ArrayList<>();
            lines.add(hovered.getName());
            if (handler.isResearched(hovered)) {
                lines.add(Text.translatable("screen.basedefense.base.researched").styled(s -> s.withColor(GOOD & 0xFFFFFF)));
            } else {
                lines.add(Text.translatable("screen.basedefense.base.cost", hovered.getCost())
                        .styled(s -> s.withColor(TEXT_MUTED & 0xFFFFFF)));
                if (!handler.isAvailable(hovered)) {
                    lines.add(Text.translatable("screen.basedefense.base.requires", hovered.getRequires().getName())
                            .styled(s -> s.withColor(BAD & 0xFFFFFF)));
                }
            }
            context.drawTooltip(this.textRenderer, lines, mouseX, mouseY);
        }
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
                headerTop + 4, settlers, 0xFFEAD7A8, TEXT_BODY);

        // Область контента
        UiKit.inset(context, contentLeft, contentTop, contentRight, contentBottom, CONTENT);
        drawGrid(context);

        // Вкладки
        int tabsTop = getTabsTop();
        for (int i = 0; i < TABS.length; i++) {
            drawTab(context, i, tabsTop, mouseX, mouseY);
        }

        // Вкладка «Потребности» рисуется отдельно
        if (selectedTab == NEEDS_TAB) {
            drawNeeds(context, mouseX, mouseY);
            return;
        }

        // Древо исследований текущей вкладки: сначала линии-связи, потом квадратики
        boolean anyNode = false;
        for (Technology tech : Technology.values()) {
            if (tech.getTab() == selectedTab && isNodeVisible(tech)) {
                drawLink(context, tech);
            }
        }
        for (Technology tech : Technology.values()) {
            if (tech.getTab() == selectedTab) {
                anyNode = true;
                if (isNodeVisible(tech)) {
                    drawNode(context, tech, mouseX, mouseY);
                }
            }
        }
        if (!anyNode) {
            Text hint = Text.translatable("screen.basedefense.base.coming_soon");
            int hintX = (contentLeft + contentRight - this.textRenderer.getWidth(hint)) / 2;
            int hintY = (contentTop + contentBottom - this.textRenderer.fontHeight) / 2;
            context.drawText(this.textRenderer, hint, hintX, hintY, TEXT_MUTED, false);
        }

        // Панель описания выбранной технологии
        if (selectedTech != null) {
            drawInfoPanel(context, selectedTech, mouseX, mouseY);
        }
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
            context.fill(tabLeft, top, tabRight, bottom + 1, CONTENT);
            context.fill(tabLeft, top, tabRight, top + 2, UiKit.GOLD);
            context.fill(tabLeft, top, tabRight, top + 1, UiKit.GOLD_LIGHT);
        } else {
            context.fill(tabLeft - 1, top - 1, tabRight + 1, bottom - 1, UiKit.INSET_DARK);
            context.fillGradient(tabLeft, top, tabRight, bottom - 1,
                    hovered ? 0xFFF0DFB4 : UiKit.HEADER_TOP, hovered ? 0xFFE0C792 : UiKit.HEADER_BOTTOM);
            context.fill(tabLeft, top, tabRight, top + 1, 0x66FFFFFF);
        }

        Text label = TABS[index];
        int textX = tabLeft + (tabW - this.textRenderer.getWidth(label)) / 2;
        int textY = top + (bottom - top - this.textRenderer.fontHeight) / 2 + 1;
        UiKit.embossed(context, this.textRenderer, label, textX, textY, selected ? TEXT_DARK : TEXT_MUTED);
    }

    private void drawGrid(DrawContext context) {
        for (int gx = contentLeft + 8; gx < contentRight - 2; gx += 12) {
            for (int gy = contentTop + 8; gy < contentBottom - 2; gy += 12) {
                context.fill(gx, gy, gx + 1, gy + 1, ((gx - contentLeft) / 12 + (gy - contentTop) / 12) % 4 == 0
                        ? 0x33745A38 : GRID_DOT);
            }
        }
    }

    private void drawNode(DrawContext context, Technology tech, int mouseX, int mouseY) {
        int nx = getNodeX(tech);
        int ny = getNodeY(tech);
        boolean done = handler.isResearched(tech);
        boolean locked = !done && !handler.isAvailable(tech);
        boolean hovered = isOverNode(tech, mouseX, mouseY);
        boolean selected = tech == selectedTech;

        int edge = selected ? ACCENT : (done ? NODE_DONE_EDGE : (locked ? NODE_LOCKED_EDGE : NODE_EDGE));
        int bg = done ? NODE_DONE_BG : (locked ? NODE_LOCKED_BG : (hovered ? NODE_BG_HOVER : NODE_BG));

        // Мягкая тень, рамка, фон с градиентом, фаска; у выбранного — золотая рамка с заклёпками
        context.fill(nx + 1, ny + 2, nx + NODE + 2, ny + NODE + 2, 0x2A000000);
        context.fill(nx, ny, nx + NODE, ny + NODE, edge);
        int inset = selected ? 2 : 1;
        context.fillGradient(nx + inset, ny + inset, nx + NODE - inset, ny + NODE - inset,
                lighten(bg), bg);
        context.fill(nx + inset, ny + inset, nx + NODE - inset, ny + inset + 1, 0x77FFFFFF);
        context.fill(nx + inset, ny + NODE - inset - 1, nx + NODE - inset, ny + NODE - inset, 0x1A000000);
        if (selected) {
            context.fill(nx + 1, ny + 1, nx + NODE - 1, ny + 2, UiKit.GOLD_LIGHT);
            UiKit.rivet(context, nx - 1, ny - 1);
            UiKit.rivet(context, nx + NODE - 2, ny - 1);
            UiKit.rivet(context, nx - 1, ny + NODE - 2);
            UiKit.rivet(context, nx + NODE - 2, ny + NODE - 2);
        } else if (hovered && !locked) {
            UiKit.outline(context, nx - 1, ny - 1, nx + NODE + 1, ny + NODE + 1, 0x88C99A3E);
        }

        context.drawItem(new ItemStack(tech.getIcon()), nx + (NODE - 16) / 2, ny + (NODE - 16) / 2);

        // Закрытая технология — иконка «выцветшая» и маленький замочек
        if (locked) {
            context.getMatrices().push();
            context.getMatrices().translate(0, 0, 300);
            context.fill(nx + 1, ny + 1, nx + NODE - 1, ny + NODE - 1, LOCK_OVERLAY);
            drawLock(context, nx + NODE - 8, ny + NODE - 9);
            context.getMatrices().pop();
        }

        // Зелёная галочка-метка в углу у исследованного
        if (done) {
            context.fill(nx + NODE - 7, ny + NODE - 7, nx + NODE - 1, ny + NODE - 1, NODE_DONE_EDGE);
            context.fill(nx + NODE - 6, ny + NODE - 4, nx + NODE - 5, ny + NODE - 3, 0xFFFFFFFF);
            context.fill(nx + NODE - 5, ny + NODE - 3, nx + NODE - 4, ny + NODE - 2, 0xFFFFFFFF);
            context.fill(nx + NODE - 4, ny + NODE - 5, nx + NODE - 3, ny + NODE - 3, 0xFFFFFFFF);
            context.fill(nx + NODE - 3, ny + NODE - 6, nx + NODE - 2, ny + NODE - 4, 0xFFFFFFFF);
        }
    }

    /** Чуть светлее цвет (для верха градиента). */
    private static int lighten(int color) {
        int r = Math.min(255, ((color >> 16) & 0xFF) + 14);
        int g = Math.min(255, ((color >> 8) & 0xFF) + 14);
        int b = Math.min(255, (color & 0xFF) + 14);
        return 0xFF000000 | (r << 16) | (g << 8) | b;
    }

    /** Линия от предыдущей технологии в цепочке к этой. */
    private void drawLink(DrawContext context, Technology tech) {
        Technology from = tech.getRequires();
        if (from == null || from.getTab() != tech.getTab() || !isNodeVisible(from)) {
            return;
        }
        int color = handler.isResearched(from) ? LINK_OPEN : LINK_CLOSED;
        int x1 = getNodeX(from) + NODE / 2;
        int y1 = getNodeY(from) + NODE / 2;
        int x2 = getNodeX(tech) + NODE / 2;
        int y2 = getNodeY(tech) + NODE / 2;
        // Г-образная линия толщиной 2 пикселя: сначала вниз/вверх, потом вбок
        // (так ветка на другой строке не проходит сквозь соседний квадратик)
        // Тонкая тень под линией, затем сама линия
        context.fill(x1, Math.min(y1, y2) + 1, x1 + 2, Math.max(y1, y2) + 2, 0x22000000);
        context.fill(Math.min(x1, x2) + 1, y2, Math.max(x1, x2) + 2, y2 + 2, 0x22000000);
        context.fill(x1 - 1, Math.min(y1, y2), x1 + 1, Math.max(y1, y2) + 1, color);
        context.fill(Math.min(x1, x2), y2 - 1, Math.max(x1, x2) + 1, y2 + 1, color);
        // Ромбик-стрелка у входа в квадратик
        if (handler.isResearched(from)) {
            UiKit.diamond(context, x2 > x1 ? getNodeX(tech) - 4 : x2 - 1, x2 > x1 ? y2 : getNodeY(tech) - 3, color);
        }
    }

    /** Маленький замочек 6×7 пикселей. */
    private void drawLock(DrawContext context, int x, int y) {
        int body = 0xFF7A6A50;
        context.fill(x + 1, y, x + 5, y + 1, body);
        context.fill(x + 1, y, x + 2, y + 3, body);
        context.fill(x + 4, y, x + 5, y + 3, body);
        context.fill(x, y + 3, x + 6, y + 7, body);
        context.fill(x + 2, y + 4, x + 4, y + 6, 0xFFE2D6BC);
    }

    /** Квадратик не рисуем, если его закрывает панель описания. */
    private boolean isNodeVisible(Technology tech) {
        return selectedTech == null || getNodeX(tech) + NODE < getInfoLeft() - 4;
    }

    private void drawInfoPanel(DrawContext context, Technology tech, int mouseX, int mouseY) {
        int pl = getInfoLeft();
        int pt = contentTop + 6;
        int pr = contentRight - 6;
        int pb = contentBottom - 6;
        int pw = pr - pl;

        // Фон панели: карточка с полосой-заголовком
        context.fill(pl + 2, pt + 3, pr + 3, pb + 3, 0x26000000);
        context.fill(pl - 1, pt - 1, pr + 1, pb + 1, UiKit.WOOD);
        context.fill(pl, pt, pr, pb, INFO_BG);
        context.fillGradient(pl, pt, pr, pt + 26, UiKit.HEADER_TOP, 0xFFF3E6C4);
        context.fill(pl, pt, pr, pt + 2, UiKit.GOLD);
        context.fill(pl, pt, pr, pt + 1, UiKit.GOLD_LIGHT);
        UiKit.rivet(context, pl - 1, pt - 1);
        UiKit.rivet(context, pr - 2, pt - 1);

        // Кнопка закрытия ×
        boolean closeHovered = isOverClose(mouseX, mouseY);
        context.drawText(this.textRenderer, "×", pr - 10, pt + 5, closeHovered ? BAD : TEXT_MUTED, false);

        // Синий кружок «?» — наведи, чтобы узнать про работу жителя
        drawInfoCircle(context, pr - 27, pt + 6, isOverWorkInfo(mouseX, mouseY));

        // Заголовок: иконка + название
        context.drawItem(new ItemStack(tech.getIcon()), pl + 6, pt + 6);
        UiKit.embossed(context, this.textRenderer, tech.getName(), pl + 26, pt + 10, TEXT_DARK);
        UiKit.divider(context, pl + 6, pr - 6, pt + 26);

        // Описание (переносится по словам)
        int textY = pt + 32;
        Technology requires = tech.getRequires();
        int descBottom = requires != null ? pb - 74 : pb - 62;
        for (OrderedText line : this.textRenderer.wrapLines(tech.getDescription(), pw - 12)) {
            if (textY + 9 > descBottom) {
                break;
            }
            context.drawText(this.textRenderer, line, pl + 6, textY, TEXT_BODY, false);
            textY += 10;
        }

        // Что нужно исследовать раньше
        if (requires != null) {
            boolean reqDone = handler.isResearched(requires);
            context.drawText(this.textRenderer, Text.translatable("screen.basedefense.base.requires", requires.getName()),
                    pl + 6, pb - 70, reqDone ? GOOD : BAD, false);
        }

        // Что открывает
        int unlockY = pb - 58;
        context.drawText(this.textRenderer, Text.translatable("screen.basedefense.base.unlocks", tech.getName()),
                pl + 6, unlockY, TEXT_MUTED, false);

        // Цена и сколько монет у игрока
        int coins = ResearchManager.countCoins(this.client.player);
        boolean done = handler.isResearched(tech);
        boolean locked = !done && !handler.isAvailable(tech);
        int costY = pb - 46;
        Text costText = Text.translatable("screen.basedefense.base.cost", tech.getCost());
        UiKit.inset(context, pl + 4, costY - 2, pr - 4, costY + 18, 0xFFF6ECD4);
        UiKit.embossed(context, this.textRenderer, costText, pl + 6, costY + 4, TEXT_DARK);
        int coinX = pl + 6 + this.textRenderer.getWidth(costText) + 2;
        context.drawItem(new ItemStack(ModItems.RESEARCH_COIN), coinX, costY);
        if (!done) {
            Text have = Text.translatable("screen.basedefense.base.have", coins);
            context.drawText(this.textRenderer, have, pr - 6 - this.textRenderer.getWidth(have), costY + 4,
                    coins >= tech.getCost() ? GOOD : BAD, false);
        }

        // Сообщение «недостаточно монет»
        if (!done && Util.getMeasuringTimeMs() < notEnoughUntil) {
            Text msg = Text.translatable("screen.basedefense.base.not_enough");
            int msgX = pl + (pw - this.textRenderer.getWidth(msg)) / 2;
            context.drawText(this.textRenderer, msg, msgX, pb - 34, BAD, false);
        }

        // Кнопка «Исследовать» или плашка «Исследовано»
        int bl = pl + 6, bt = pb - 6 - BUTTON_H, br = pr - 6, bb = pb - 6;
        if (done) {
            UiKit.button(context, this.textRenderer, bl, bt, br, bb,
                    Text.translatable("screen.basedefense.base.researched"), 3);
        } else if (locked) {
            UiKit.button(context, this.textRenderer, bl, bt, br, bb,
                    Text.translatable("screen.basedefense.base.locked"), 2);
        } else {
            UiKit.button(context, this.textRenderer, bl, bt, br, bb,
                    Text.translatable("screen.basedefense.base.research_button"), isOverButton(mouseX, mouseY) ? 1 : 0);
        }
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
        UiKit.card(context, left - 4, y1 - 6, right + 4, y1 + 40, 0xFFFBF4E2, false);
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
        return value < 0 ? ACCENT : GOOD;
    }

    /** Полоска запаса еды: от -20 до максимума, отметка «0». */
    private void drawFoodBar(DrawContext context, int barLeft, int top, int barRight, int value) {
        int min = SettlementManager.FREEZE_AT;
        int max = Math.max(200, value);
        int h = 6;
        UiKit.inset(context, barLeft, top, barRight, top + h, 0xFFE8DCC0);
        int clamped = Math.max(min, Math.min(max, value));
        int zeroX = barLeft + (barRight - barLeft) * (-min) / (max - min);
        int valueX = barLeft + (barRight - barLeft) * (clamped - min) / (max - min);
        int color = counterColor(value);
        context.fillGradient(Math.min(zeroX, valueX), top, Math.max(zeroX, valueX), top + h, lighten(lighten(color)), color);
        context.fill(Math.min(zeroX, valueX), top, Math.max(zeroX, valueX), top + 1, 0x55FFFFFF);
        context.fill(zeroX, top - 1, zeroX + 1, top + h + 1, TEXT_DARK); // отметка «0»
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

    private int getNodeX(Technology tech) {
        return contentLeft + 20 + tech.getGridX() * NODE_STEP;
    }

    private int getNodeY(Technology tech) {
        return contentTop + 20 + tech.getGridY() * NODE_STEP;
    }

    private int getInfoLeft() {
        return Math.max(contentLeft + 70, contentRight - 6 - INFO_W);
    }

    private boolean isOverTab(int index, double mouseX, double mouseY) {
        int tabLeft = getTabLeft(index);
        int tabsTop = getTabsTop();
        return mouseX >= tabLeft && mouseX < tabLeft + getTabWidth() && mouseY >= tabsTop - 2 && mouseY < tabsTop + TAB_H;
    }

    private boolean isOverNode(Technology tech, double mouseX, double mouseY) {
        int nx = getNodeX(tech);
        int ny = getNodeY(tech);
        return mouseX >= nx && mouseX < nx + NODE && mouseY >= ny && mouseY < ny + NODE;
    }

    @Nullable
    private Technology getNodeAt(double mouseX, double mouseY) {
        if (selectedTech != null && isOverInfoPanel(mouseX, mouseY)) {
            return null;
        }
        for (Technology tech : Technology.values()) {
            if (tech.getTab() == selectedTab && isNodeVisible(tech) && isOverNode(tech, mouseX, mouseY)) {
                return tech;
            }
        }
        return null;
    }

    private boolean isOverInfoPanel(double mouseX, double mouseY) {
        return mouseX >= getInfoLeft() && mouseX < contentRight - 6 && mouseY >= contentTop + 6 && mouseY < contentBottom - 6;
    }

    /** Кружок 11×11 с белым «?». */
    private void drawInfoCircle(DrawContext context, int x, int y, boolean hover) {
        int color = hover ? INFO_CIRCLE_HOVER : INFO_CIRCLE;
        // Ширина каждой строки круга (рисуем «пиксельный» круг)
        int[] inset = {3, 1, 1, 0, 0, 0, 0, 0, 1, 1, 3};
        for (int row = 0; row < 11; row++) {
            context.fill(x + inset[row], y + row, x + 11 - inset[row], y + row + 1, color);
        }
        context.drawText(this.textRenderer, "?", x + 3, y + 2, 0xFFFFFFFF, false);
    }

    private boolean isOverWorkInfo(double mouseX, double mouseY) {
        if (selectedTech == null) {
            return false;
        }
        int x = contentRight - 6 - 27;
        int y = contentTop + 6 + 6;
        return mouseX >= x && mouseX < x + 11 && mouseY >= y && mouseY < y + 11;
    }

    private boolean isOverClose(double mouseX, double mouseY) {
        int pr = contentRight - 6;
        int pt = contentTop + 6;
        return mouseX >= pr - 13 && mouseX < pr - 1 && mouseY >= pt + 2 && mouseY < pt + 15;
    }

    private boolean isOverButton(double mouseX, double mouseY) {
        int pb = contentBottom - 6;
        return mouseX >= getInfoLeft() + 6 && mouseX < contentRight - 12
                && mouseY >= pb - 6 - BUTTON_H && mouseY < pb - 6;
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
                        selectedTech = null;
                        handler.setSlotsVisible(i == NEEDS_TAB);
                        playClick();
                    }
                    return true;
                }
            }

            // Панель описания
            if (selectedTech != null && isOverInfoPanel(mouseX, mouseY)) {
                if (isOverClose(mouseX, mouseY)) {
                    selectedTech = null;
                    playClick();
                } else if (isOverButton(mouseX, mouseY) && !handler.isResearched(selectedTech)
                        && handler.isAvailable(selectedTech)) {
                    onResearchPressed(selectedTech);
                }
                return true;
            }

            // Квадратики технологий
            Technology node = getNodeAt(mouseX, mouseY);
            if (node != null) {
                selectedTech = node == selectedTech ? null : node;
                playClick();
                return true;
            }
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    private void onResearchPressed(Technology tech) {
        MinecraftClient mc = MinecraftClient.getInstance();
        if (ResearchManager.countCoins(mc.player) < tech.getCost()) {
            notEnoughUntil = Util.getMeasuringTimeMs() + 2500;
            mc.getSoundManager().play(PositionedSoundInstance.master(SoundEvents.ENTITY_VILLAGER_NO, 1.0f));
            return;
        }
        playClick();
        // Номер кнопки = номер технологии. Сервер проверит монеты ещё раз и проведёт исследование.
        if (mc.interactionManager != null) {
            mc.interactionManager.clickButton(this.handler.syncId, tech.ordinal());
        }
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

package com.lesha_top1972.basedefense.client.screen;

import com.lesha_top1972.basedefense.screen.BaseScreenHandler;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.ingame.HandledScreen;
import net.minecraft.client.sound.PositionedSoundInstance;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;

/**
 * Экран меню базы: заголовок «База», строка вкладок и большая область,
 * где позже появится древо исследований.
 * Всё рисуется кодом (заливками), поэтому окно подстраивается под размер экрана.
 */
public class BaseScreen extends HandledScreen<BaseScreenHandler> {

    // ---------- Палитра (бежевые оттенки) ----------
    private static final int SHADOW        = 0x55000000; // тень под окном
    private static final int FRAME_DARK    = 0xFF5C4630; // внешняя тёмная рамка
    private static final int FRAME_LIGHT   = 0xFFFFF7E3; // светлая фаска
    private static final int FRAME_MID     = 0xFFB89B6E; // нижняя/правая фаска
    private static final int PANEL         = 0xFFE6D3AB; // основной фон окна
    private static final int HEADER        = 0xFFD8C08F; // полоса заголовка
    private static final int TAB_IDLE      = 0xFFD2BA88; // неактивная вкладка
    private static final int TAB_HOVER     = 0xFFE0CB9E; // вкладка под мышкой
    private static final int CONTENT       = 0xFFF2E7CC; // область контента (= активная вкладка)
    private static final int CONTENT_EDGE  = 0xFF9C8058; // рамка области контента
    private static final int GRID_DOT      = 0x22745A38; // точки «пергаментной» сетки
    private static final int TEXT_DARK     = 0xFF4A3622;
    private static final int TEXT_MUTED    = 0xFF8F7550;
    private static final int ACCENT        = 0xFFB0782F; // золотистая полоска активной вкладки

    // ---------- Размеры ----------
    private static final int HEADER_H = 18;
    private static final int TAB_H = 18;
    private static final int TAB_W = 92;
    private static final int PAD = 6;

    /** Вкладки. Названия берутся из файлов перевода (lang). */
    private static final Text[] TABS = {
            Text.translatable("screen.basedefense.base.tab.technologies"),
            Text.translatable("screen.basedefense.base.tab.defense"),
            Text.translatable("screen.basedefense.base.tab.economy")
    };

    private int selectedTab = 0;

    public BaseScreen(BaseScreenHandler handler, PlayerInventory inventory, Text title) {
        super(handler, inventory, title);
    }

    @Override
    protected void init() {
        // Окно занимает большую часть экрана, как на эскизе.
        this.backgroundWidth = Math.max(240, Math.min(this.width - 40, 420));
        this.backgroundHeight = Math.max(160, Math.min(this.height - 30, 260));
        super.init();
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

        // Тень и рамка
        context.fill(left + 3, top + 3, right + 3, bottom + 3, SHADOW);
        context.fill(left, top, right, bottom, FRAME_DARK);
        context.fill(left + 1, top + 1, right - 1, bottom - 1, FRAME_MID);
        context.fill(left + 1, top + 1, right - 2, bottom - 2, FRAME_LIGHT);
        context.fill(left + 2, top + 2, right - 2, bottom - 2, PANEL);

        // Заголовок «База»
        int headerTop = top + 2;
        int headerBottom = headerTop + HEADER_H;
        context.fill(left + 2, headerTop, right - 2, headerBottom, HEADER);
        context.fill(left + 2, headerBottom, right - 2, headerBottom + 1, FRAME_MID);
        context.drawText(this.textRenderer, this.title, left + PAD + 2, headerTop + 5, TEXT_DARK, false);

        // Область контента
        int tabsTop = headerBottom + 5;
        int contentLeft = left + PAD;
        int contentTop = tabsTop + TAB_H;
        int contentRight = right - PAD;
        int contentBottom = bottom - PAD;
        context.fill(contentLeft - 1, contentTop - 1, contentRight + 1, contentBottom + 1, CONTENT_EDGE);
        context.fill(contentLeft, contentTop, contentRight, contentBottom, CONTENT);
        drawGrid(context, contentLeft, contentTop, contentRight, contentBottom);

        // Вкладки (рисуем после контента, чтобы активная «сливалась» с ним)
        for (int i = 0; i < TABS.length; i++) {
            drawTab(context, i, tabsTop, mouseX, mouseY);
        }

        // Временная подсказка, пока нет древа исследований
        Text hint = Text.translatable("screen.basedefense.base.coming_soon");
        int hintX = (contentLeft + contentRight - this.textRenderer.getWidth(hint)) / 2;
        int hintY = (contentTop + contentBottom - this.textRenderer.fontHeight) / 2;
        context.drawText(this.textRenderer, hint, hintX, hintY, TEXT_MUTED, false);
    }

    private void drawTab(DrawContext context, int index, int tabsTop, int mouseX, int mouseY) {
        int tabLeft = getTabLeft(index);
        int tabRight = tabLeft + TAB_W;
        boolean selected = index == selectedTab;
        boolean hovered = isOverTab(index, tabsTop, mouseX, mouseY);

        int top = selected ? tabsTop - 2 : tabsTop;   // активная вкладка чуть выше
        int bottom = tabsTop + TAB_H;

        context.fill(tabLeft - 1, top - 1, tabRight + 1, bottom, CONTENT_EDGE);
        context.fill(tabLeft, top, tabRight, selected ? bottom + 1 : bottom,
                selected ? CONTENT : (hovered ? TAB_HOVER : TAB_IDLE));
        if (selected) {
            context.fill(tabLeft, top, tabRight, top + 2, ACCENT);
        }

        Text label = TABS[index];
        int textX = tabLeft + (TAB_W - this.textRenderer.getWidth(label)) / 2;
        int textY = top + (bottom - top - this.textRenderer.fontHeight) / 2 + 1;
        context.drawText(this.textRenderer, label, textX, textY, selected ? TEXT_DARK : TEXT_MUTED, false);
    }

    /** Лёгкая сетка из точек — подложка для будущего древа исследований. */
    private void drawGrid(DrawContext context, int left, int top, int right, int bottom) {
        for (int gx = left + 8; gx < right - 2; gx += 12) {
            for (int gy = top + 8; gy < bottom - 2; gy += 12) {
                context.fill(gx, gy, gx + 1, gy + 1, GRID_DOT);
            }
        }
    }

    private int getTabLeft(int index) {
        return this.x + PAD + index * (TAB_W + 3);
    }

    private int getTabsTop() {
        return this.y + 2 + HEADER_H + 5;
    }

    private boolean isOverTab(int index, int tabsTop, double mouseX, double mouseY) {
        int tabLeft = getTabLeft(index);
        return mouseX >= tabLeft && mouseX < tabLeft + TAB_W
                && mouseY >= tabsTop - 2 && mouseY < tabsTop + TAB_H;
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button == 0) {
            int tabsTop = getTabsTop();
            for (int i = 0; i < TABS.length; i++) {
                if (isOverTab(i, tabsTop, mouseX, mouseY)) {
                    if (selectedTab != i) {
                        selectedTab = i;
                        MinecraftClient.getInstance().getSoundManager()
                                .play(PositionedSoundInstance.master(SoundEvents.UI_BUTTON_CLICK, 1.0f));
                    }
                    return true;
                }
            }
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    protected void drawForeground(DrawContext context, int mouseX, int mouseY) {
        // Пусто: стандартные надписи («Инвентарь» и т.п.) не нужны, всё рисуем сами.
    }
}

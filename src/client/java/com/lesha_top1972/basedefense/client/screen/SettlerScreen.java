package com.lesha_top1972.basedefense.client.screen;

import com.lesha_top1972.basedefense.entity.SettlerEntity;
import com.lesha_top1972.basedefense.item.ModItems;
import com.lesha_top1972.basedefense.research.ResearchManager;
import com.lesha_top1972.basedefense.screen.SettlerScreenHandler;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.ingame.HandledScreen;
import net.minecraft.client.sound.PositionedSoundInstance;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.item.ItemStack;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;

import java.util.ArrayList;
import java.util.List;

/**
 * Меню жителя: почти прозрачная панель посреди экрана (мир за ней не затемняется)
 * со строкой состояния и кнопками: «Улучшить» (у повара ещё «Отдать еду из руки»).
 * Работа и сбор добычи идут сами: житель складывает всё в сундук своего дома.
 */
public class SettlerScreen extends HandledScreen<SettlerScreenHandler> {

    // ---------- Цвета (полупрозрачные) ----------
    private static final int PANEL        = 0x40000000;
    private static final int PANEL_EDGE   = 0x50FFFFFF;
    private static final int BUTTON       = 0x60000000;
    private static final int BUTTON_HOVER = 0x90000000;
    private static final int BUTTON_EDGE  = 0x70FFFFFF;
    private static final int BUTTON_EDGE_HOVER = 0xE0FFE08A;
    private static final int TEXT         = 0xFFFFFFFF;
    private static final int TEXT_MUTED   = 0xFFB8B8B8;
    private static final int TEXT_GOOD    = 0xFF8FE07A;
    private static final int TEXT_BAD     = 0xFFFF7A6A;
    private static final int TEXT_GOLD    = 0xFFFFD45A;

    private static final int BUTTON_H = 20;
    private static final int BUTTON_GAP = 6;
    private static final int BUTTONS_TOP = 46;   // от верха панели

    public SettlerScreen(SettlerScreenHandler handler, PlayerInventory inventory, Text title) {
        super(handler, inventory, title);
        this.backgroundWidth = 210;
        this.backgroundHeight = heightFor(1);
    }

    private static int heightFor(int buttons) {
        return BUTTONS_TOP + buttons * BUTTON_H + (buttons - 1) * BUTTON_GAP + 22;
    }

    /** Какие кнопки показаны (по порядку сверху вниз): id кнопок из SettlerScreenHandler. */
    private int[] buttons() {
        return handler.getProp(SettlerScreenHandler.PROP_ACCEPTS) == 1
                ? new int[]{SettlerScreenHandler.BUTTON_INGREDIENTS, SettlerScreenHandler.BUTTON_UPGRADE}
                : new int[]{SettlerScreenHandler.BUTTON_UPGRADE};
    }

    private int buttonCount() {
        return buttons().length;
    }

    @Override
    protected void handledScreenTick() {
        super.handledScreenTick();
        // Данные о жителе приходят с сервера чуть позже открытия — подстраиваем высоту окна
        int wanted = heightFor(buttonCount());
        if (wanted != this.backgroundHeight) {
            this.backgroundHeight = wanted;
            this.y = (this.height - this.backgroundHeight) / 2;
        }
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        // Фон мира НЕ затемняем — меню почти прозрачное
        super.render(context, mouseX, mouseY, delta);
        int hovered = getButtonAt(mouseX, mouseY);
        if (hovered >= 0) {
            List<Text> tip = getTooltip(hovered);
            if (!tip.isEmpty()) {
                context.drawTooltip(this.textRenderer, tip, mouseX, mouseY);
            }
        }
    }

    @Override
    protected void drawBackground(DrawContext context, float delta, int mouseX, int mouseY) {
        int left = this.x;
        int top = this.y;
        int right = left + this.backgroundWidth;
        int bottom = top + this.backgroundHeight;

        // Панель
        context.fill(left - 1, top - 1, right + 1, bottom + 1, PANEL_EDGE);
        context.fill(left, top, right, bottom, PANEL);

        // Заголовок: имя и уровень
        int level = handler.getProp(SettlerScreenHandler.PROP_LEVEL);
        Text title = this.title.copy().append(Text.translatable("screen.basedefense.settler.level", level));
        context.drawCenteredTextWithShadow(this.textRenderer, title, left + this.backgroundWidth / 2, top + 6, TEXT_GOLD);

        // Строка состояния
        boolean frozen = handler.getProp(SettlerScreenHandler.PROP_FROZEN) == 1;
        boolean hasChest = handler.getProp(SettlerScreenHandler.PROP_HAS_CHEST) == 1;
        int stored = handler.getProp(SettlerScreenHandler.PROP_STORED);
        Text status;
        int statusColor;
        if (frozen) {
            status = Text.translatable("screen.basedefense.settler.status.frozen");
            statusColor = TEXT_BAD;
        } else if (!hasChest) {
            status = Text.translatable("screen.basedefense.settler.status.no_chest", stored);
            statusColor = TEXT_BAD;
        } else if (stored > 0) {
            status = Text.translatable("screen.basedefense.settler.status.chest_full", stored);
            statusColor = TEXT_BAD;
        } else if (handler.getProp(SettlerScreenHandler.PROP_ACCEPTS) == 1) {
            int have = handler.getProp(SettlerScreenHandler.PROP_INGREDIENTS);
            int need = handler.getProp(SettlerScreenHandler.PROP_INGREDIENTS_DAY);
            status = Text.translatable(have >= need ? "screen.basedefense.settler.status.cooking"
                    : "screen.basedefense.settler.status.no_ingredients", have, need);
            statusColor = have >= need ? TEXT_GOOD : TEXT_BAD;
        } else {
            status = Text.translatable("screen.basedefense.settler.status.working",
                    handler.getProp(SettlerScreenHandler.PROP_ROLLS));
            statusColor = TEXT_GOOD;
        }
        int centerX = left + this.backgroundWidth / 2;
        context.drawCenteredTextWithShadow(this.textRenderer, status, centerX, top + 19, statusColor);
        // Вторая строка: сколько ест в день
        context.drawCenteredTextWithShadow(this.textRenderer, Text.translatable("screen.basedefense.settler.food",
                handler.getProp(SettlerScreenHandler.PROP_WORK_FOOD)), centerX, top + 31, TEXT_MUTED);

        // Кнопки
        for (int i = 0; i < buttonCount(); i++) {
            drawButton(context, i, mouseX, mouseY);
        }

        // Монеты игрока внизу
        int coins = ResearchManager.countCoins(this.client.player);
        Text coinText = Text.translatable("screen.basedefense.settler.coins", coins);
        int tw = this.textRenderer.getWidth(coinText);
        int cx = left + (this.backgroundWidth - tw - 18) / 2;
        context.drawItem(new ItemStack(ModItems.RESEARCH_COIN), cx, bottom - 19);
        context.drawTextWithShadow(this.textRenderer, coinText, cx + 18, bottom - 15, TEXT_MUTED);
    }

    /** @param slot место кнопки сверху вниз (0, 1, …) */
    private void drawButton(DrawContext context, int slot, int mouseX, int mouseY) {
        int index = buttons()[slot];
        int bl = getButtonLeft();
        int bt = getButtonTop(slot);
        int br = bl + getButtonWidth();
        int bb = bt + BUTTON_H;
        boolean enabled = isButtonEnabled(index);
        boolean hover = enabled && getButtonAt(mouseX, mouseY) == index;

        context.fill(bl - 1, bt - 1, br + 1, bb + 1, hover ? BUTTON_EDGE_HOVER : BUTTON_EDGE);
        context.fill(bl, bt, br, bb, hover ? BUTTON_HOVER : BUTTON);

        Text label = getButtonLabel(index);
        int color = enabled ? (hover ? TEXT_GOLD : TEXT) : TEXT_MUTED;
        if (index == SettlerScreenHandler.BUTTON_UPGRADE && enabled
                && ResearchManager.countCoins(this.client.player) < upgradeCost()) {
            color = TEXT_BAD;
        }
        context.drawCenteredTextWithShadow(this.textRenderer, label, (bl + br) / 2, bt + 6, color);
    }

    // ---------------------------------------------------------------------
    // Кнопки: текст, доступность, подсказки
    // ---------------------------------------------------------------------

    private int upgradeCost() {
        return SettlerEntity.getUpgradeCost(handler.getProp(SettlerScreenHandler.PROP_LEVEL));
    }

    private boolean isMaxLevel() {
        return handler.getProp(SettlerScreenHandler.PROP_LEVEL) >= SettlerEntity.MAX_LEVEL;
    }

    private Text getButtonLabel(int index) {
        return switch (index) {
            case SettlerScreenHandler.BUTTON_INGREDIENTS -> Text.translatable("screen.basedefense.settler.ingredients",
                    handler.getProp(SettlerScreenHandler.PROP_INGREDIENTS), SettlerEntity.INGREDIENTS_MAX);
            default -> isMaxLevel()
                    ? Text.translatable("screen.basedefense.settler.max_level")
                    : Text.translatable("screen.basedefense.settler.upgrade",
                    handler.getProp(SettlerScreenHandler.PROP_LEVEL) + 1, upgradeCost());
        };
    }

    private boolean isButtonEnabled(int index) {
        return switch (index) {
            case SettlerScreenHandler.BUTTON_UPGRADE -> !isMaxLevel();
            default -> true;
        };
    }

    private List<Text> getTooltip(int index) {
        List<Text> lines = new ArrayList<>();
        switch (index) {
            case SettlerScreenHandler.BUTTON_INGREDIENTS -> {
                lines.add(Text.translatable("screen.basedefense.settler.tip.ingredients"));
                lines.add(Text.translatable("screen.basedefense.settler.tip.ingredients_day",
                        handler.getProp(SettlerScreenHandler.PROP_INGREDIENTS_DAY))
                        .styled(s -> s.withColor(TEXT_GOLD & 0xFFFFFF)));
            }
            case SettlerScreenHandler.BUTTON_UPGRADE -> {
                if (!isMaxLevel()) {
                    lines.add(Text.translatable("screen.basedefense.settler.tip.upgrade"));
                }
            }
        }
        return lines;
    }

    // ---------------------------------------------------------------------
    // Координаты и клики
    // ---------------------------------------------------------------------

    private int getButtonLeft() {
        return this.x + 12;
    }

    private int getButtonWidth() {
        return this.backgroundWidth - 24;
    }

    private int getButtonTop(int slot) {
        return this.y + BUTTONS_TOP + slot * (BUTTON_H + BUTTON_GAP);
    }

    /** id кнопки под мышью или -1. */
    private int getButtonAt(double mouseX, double mouseY) {
        int bl = getButtonLeft();
        for (int i = 0; i < buttonCount(); i++) {
            int bt = getButtonTop(i);
            if (mouseX >= bl && mouseX < bl + getButtonWidth() && mouseY >= bt && mouseY < bt + BUTTON_H) {
                return buttons()[i];
            }
        }
        return -1;
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        int index = getButtonAt(mouseX, mouseY);
        if (button == 0 && index >= 0) {
            if (isButtonEnabled(index)) {
                MinecraftClient mc = MinecraftClient.getInstance();
                if (index == SettlerScreenHandler.BUTTON_UPGRADE
                        && ResearchManager.countCoins(mc.player) < upgradeCost()) {
                    mc.getSoundManager().play(PositionedSoundInstance.master(SoundEvents.ENTITY_VILLAGER_NO, 1.0f));
                } else {
                    mc.getSoundManager().play(PositionedSoundInstance.master(SoundEvents.UI_BUTTON_CLICK, 1.0f));
                }
                // Сервер всё проверит ещё раз
                if (mc.interactionManager != null) {
                    mc.interactionManager.clickButton(this.handler.syncId, index);
                }
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

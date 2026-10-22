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
 * и три кнопки: работа, забрать добычу, улучшить.
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
    private static final int BUTTONS_TOP = 34;   // от верха панели

    public SettlerScreen(SettlerScreenHandler handler, PlayerInventory inventory, Text title) {
        super(handler, inventory, title);
        this.backgroundWidth = 200;
        this.backgroundHeight = BUTTONS_TOP + 3 * BUTTON_H + 2 * BUTTON_GAP + 22;
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
        boolean working = handler.getProp(SettlerScreenHandler.PROP_WORKING) == 1;
        Text status;
        int statusColor;
        if (frozen) {
            status = Text.translatable("screen.basedefense.settler.status.frozen");
            statusColor = TEXT_BAD;
        } else if (working) {
            status = Text.translatable("screen.basedefense.settler.status.working",
                    SettlerEntity.LOOT_ROLLS_PER_DAY);
            statusColor = TEXT_GOOD;
        } else {
            status = Text.translatable("screen.basedefense.settler.status.idle");
            statusColor = TEXT_MUTED;
        }
        context.drawCenteredTextWithShadow(this.textRenderer, status, left + this.backgroundWidth / 2, top + 19, statusColor);

        // Кнопки
        for (int i = 0; i < 3; i++) {
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

    private void drawButton(DrawContext context, int index, int mouseX, int mouseY) {
        int bl = getButtonLeft();
        int bt = getButtonTop(index);
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
            case SettlerScreenHandler.BUTTON_WORK -> Text.translatable(
                    handler.getProp(SettlerScreenHandler.PROP_WORKING) == 1
                            ? "screen.basedefense.settler.stop_work" : "screen.basedefense.settler.start_work");
            case SettlerScreenHandler.BUTTON_LOOT -> Text.translatable("screen.basedefense.settler.loot",
                    handler.getProp(SettlerScreenHandler.PROP_STORED));
            default -> isMaxLevel()
                    ? Text.translatable("screen.basedefense.settler.max_level")
                    : Text.translatable("screen.basedefense.settler.upgrade",
                    handler.getProp(SettlerScreenHandler.PROP_LEVEL) + 1, upgradeCost());
        };
    }

    private boolean isButtonEnabled(int index) {
        return switch (index) {
            case SettlerScreenHandler.BUTTON_LOOT -> handler.getProp(SettlerScreenHandler.PROP_STORED) > 0;
            case SettlerScreenHandler.BUTTON_UPGRADE -> !isMaxLevel();
            default -> true;
        };
    }

    private List<Text> getTooltip(int index) {
        List<Text> lines = new ArrayList<>();
        switch (index) {
            case SettlerScreenHandler.BUTTON_WORK -> {
                lines.add(Text.translatable("screen.basedefense.settler.tip.work"));
                lines.add(Text.translatable("screen.basedefense.settler.tip.work_food",
                        handler.getProp(SettlerScreenHandler.PROP_WORK_FOOD))
                        .styled(s -> s.withColor(TEXT_GOLD & 0xFFFFFF)));
            }
            case SettlerScreenHandler.BUTTON_LOOT -> lines.add(Text.translatable("screen.basedefense.settler.tip.loot"));
            default -> {
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

    private int getButtonTop(int index) {
        return this.y + BUTTONS_TOP + index * (BUTTON_H + BUTTON_GAP);
    }

    private int getButtonAt(double mouseX, double mouseY) {
        int bl = getButtonLeft();
        for (int i = 0; i < 3; i++) {
            int bt = getButtonTop(i);
            if (mouseX >= bl && mouseX < bl + getButtonWidth() && mouseY >= bt && mouseY < bt + BUTTON_H) {
                return i;
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

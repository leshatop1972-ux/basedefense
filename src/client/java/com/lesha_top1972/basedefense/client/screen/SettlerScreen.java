package com.lesha_top1972.basedefense.client.screen;

import com.lesha_top1972.basedefense.entity.BuilderEntity;
import com.lesha_top1972.basedefense.entity.SettlerEntity;
import com.lesha_top1972.basedefense.item.ModItems;
import com.lesha_top1972.basedefense.research.ResearchManager;
import com.lesha_top1972.basedefense.screen.SettlerScreenHandler;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.ingame.HandledScreen;
import net.minecraft.client.sound.PositionedSoundInstance;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.registry.Registries;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.OrderedText;
import net.minecraft.text.Text;

import java.util.ArrayList;
import java.util.List;

/**
 * Меню жителя в общем стиле мода (пергамент в деревянной рамке): имя и уровень,
 * карточка состояния, расход еды и кнопки «Улучшить» (у повара ещё «Отдать еду из руки»).
 * Работа и сбор добычи идут сами: житель складывает всё в сундук своего дома.
 */
public class SettlerScreen extends HandledScreen<SettlerScreenHandler> {

    private static final int BUTTON_H = 20;
    private static final int BUTTON_GAP = 6;
    private static final int HEADER_H = 22;
    private static final int STATUS_T = 32;      // карточка состояния (от верха окна)
    private static final int STATUS_B = 56;
    private static final int BUTTONS_TOP = 88;

    public SettlerScreen(SettlerScreenHandler handler, PlayerInventory inventory, Text title) {
        super(handler, inventory, title);
        this.backgroundWidth = 236;
        this.backgroundHeight = heightFor(1);
    }

    private static int heightFor(int buttons) {
        return BUTTONS_TOP + buttons * BUTTON_H + (buttons - 1) * BUTTON_GAP + 34;
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
        this.renderBackground(context);
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

        UiKit.panel(context, left, top, right, bottom);
        UiKit.header(context, left + 4, top + 4, right - 4, HEADER_H);

        // Заголовок: имя и уровень (ромбики — сколько уровней из трёх)
        int level = handler.getProp(SettlerScreenHandler.PROP_LEVEL);
        UiKit.embossed(context, this.textRenderer, this.title, left + 11, top + 11, UiKit.TEXT_DARK);
        for (int i = 0; i < SettlerEntity.MAX_LEVEL; i++) {
            int dx = right - 14 - (SettlerEntity.MAX_LEVEL - 1 - i) * 8;
            UiKit.diamond(context, dx, top + 14, i < level ? UiKit.GOLD : 0xFFBBA987);
        }

        // Карточка состояния: значок + текст
        Text status = statusText();
        int statusColor = statusColor();
        UiKit.inset(context, left + 12, top + STATUS_T, right - 12, top + STATUS_B, UiKit.CONTENT);
        context.fill(left + 14, top + STATUS_T + 2, left + 16, top + STATUS_B - 2, statusColor);
        context.drawItem(new ItemStack(statusIcon()), left + 20, top + STATUS_T + 4);
        List<OrderedText> lines = this.textRenderer.wrapLines(status, right - 12 - (left + 40) - 4);
        int ty = top + (STATUS_T + STATUS_B) / 2 - lines.size() * 5 + 1;
        for (OrderedText line : lines.subList(0, Math.min(2, lines.size()))) {
            context.drawText(this.textRenderer, line, left + 40, ty, statusColor, false);
            ty += 10;
        }

        // Еда в день
        context.drawItem(new ItemStack(Items.BREAD), left + 12, top + 60);
        UiKit.embossed(context, this.textRenderer, Text.translatable("screen.basedefense.settler.food",
                handler.getProp(SettlerScreenHandler.PROP_WORK_FOOD)), left + 32, top + 65, UiKit.TEXT_BODY);

        UiKit.divider(context, left + 12, right - 12, top + 80);

        // Кнопки
        for (int i = 0; i < buttonCount(); i++) {
            drawButton(context, i, mouseX, mouseY);
        }

        // Монеты игрока внизу
        UiKit.divider(context, left + 12, right - 12, bottom - 27);
        int coins = ResearchManager.countCoins(this.client.player);
        Text coinText = Text.translatable("screen.basedefense.settler.coins", coins);
        int tw = this.textRenderer.getWidth(coinText);
        int cx = left + (this.backgroundWidth - tw - 18) / 2;
        context.drawItem(new ItemStack(ModItems.RESEARCH_COIN), cx, bottom - 22);
        UiKit.embossed(context, this.textRenderer, coinText, cx + 18, bottom - 18, UiKit.TEXT_BODY);
    }

    /** Текст строки состояния. */
    private Text statusText() {
        boolean frozen = handler.getProp(SettlerScreenHandler.PROP_FROZEN) == 1;
        int stored = handler.getProp(SettlerScreenHandler.PROP_STORED);
        if (frozen) {
            return Text.translatable("screen.basedefense.settler.status.frozen");
        }
        if (handler.getProp(SettlerScreenHandler.PROP_BUILD_STATUS) > 0) {
            return builderStatus();
        }
        if (handler.getProp(SettlerScreenHandler.PROP_HAS_CHEST) != 1) {
            return Text.translatable("screen.basedefense.settler.status.no_chest", stored);
        }
        if (stored > 0) {
            return Text.translatable("screen.basedefense.settler.status.chest_full", stored);
        }
        if (handler.getProp(SettlerScreenHandler.PROP_ACCEPTS) == 1) {
            int have = handler.getProp(SettlerScreenHandler.PROP_INGREDIENTS);
            int need = handler.getProp(SettlerScreenHandler.PROP_INGREDIENTS_DAY);
            return Text.translatable(have >= need ? "screen.basedefense.settler.status.cooking"
                    : "screen.basedefense.settler.status.no_ingredients", have, need);
        }
        return Text.translatable("screen.basedefense.settler.status.working",
                handler.getProp(SettlerScreenHandler.PROP_ROLLS));
    }

    private int statusColor() {
        if (handler.getProp(SettlerScreenHandler.PROP_FROZEN) == 1) {
            return UiKit.BAD;
        }
        int bs = handler.getProp(SettlerScreenHandler.PROP_BUILD_STATUS);
        if (bs > 0) {
            return bs == BuilderEntity.STATUS_MISSING ? UiKit.BAD
                    : bs == BuilderEntity.STATUS_IDLE ? UiKit.TEXT_MUTED : UiKit.GOOD;
        }
        if (handler.getProp(SettlerScreenHandler.PROP_HAS_CHEST) != 1
                || handler.getProp(SettlerScreenHandler.PROP_STORED) > 0) {
            return UiKit.BAD;
        }
        if (handler.getProp(SettlerScreenHandler.PROP_ACCEPTS) == 1) {
            return handler.getProp(SettlerScreenHandler.PROP_INGREDIENTS)
                    >= handler.getProp(SettlerScreenHandler.PROP_INGREDIENTS_DAY) ? UiKit.GOOD : UiKit.BAD;
        }
        return UiKit.GOOD;
    }

    /** Значок в карточке состояния. */
    private Item statusIcon() {
        if (handler.getProp(SettlerScreenHandler.PROP_FROZEN) == 1) {
            return Items.PACKED_ICE;
        }
        int bs = handler.getProp(SettlerScreenHandler.PROP_BUILD_STATUS);
        if (bs > 0) {
            return switch (bs) {
                case BuilderEntity.STATUS_BUILDING -> Items.IRON_SHOVEL;
                case BuilderEntity.STATUS_WALKING -> Items.LEATHER_BOOTS;
                case BuilderEntity.STATUS_PATHS -> Items.DIRT_PATH;
                case BuilderEntity.STATUS_MISSING -> {
                    int raw = handler.getProp(SettlerScreenHandler.PROP_BUILD_MISSING);
                    yield raw < 0 ? Items.BARRIER : Registries.ITEM.get(raw);
                }
                default -> Items.CLOCK;
            };
        }
        if (handler.getProp(SettlerScreenHandler.PROP_HAS_CHEST) != 1
                || handler.getProp(SettlerScreenHandler.PROP_STORED) > 0) {
            return Items.CHEST;
        }
        return handler.getProp(SettlerScreenHandler.PROP_ACCEPTS) == 1 ? Items.PUMPKIN_PIE : Items.GOLDEN_HOE;
    }

    /** Строка состояния строителя: что строит и чего не хватает. */
    private Text builderStatus() {
        int bs = handler.getProp(SettlerScreenHandler.PROP_BUILD_STATUS);
        Text house = itemName(handler.getProp(SettlerScreenHandler.PROP_BUILD_HOUSE));
        return switch (bs) {
            case BuilderEntity.STATUS_WALKING -> Text.translatable("screen.basedefense.settler.build.walking", house);
            case BuilderEntity.STATUS_BUILDING -> Text.translatable("screen.basedefense.settler.build.building",
                    house, handler.getProp(SettlerScreenHandler.PROP_BUILD_PROGRESS));
            case BuilderEntity.STATUS_MISSING -> Text.translatable("screen.basedefense.settler.build.missing",
                    itemName(handler.getProp(SettlerScreenHandler.PROP_BUILD_MISSING)));
            case BuilderEntity.STATUS_PATHS -> Text.translatable("screen.basedefense.settler.build.paths");
            default -> Text.translatable("screen.basedefense.settler.build.idle");
        };
    }

    private static Text itemName(int rawId) {
        if (rawId < 0) {
            return Text.literal("?");
        }
        return Registries.ITEM.get(rawId).getName();
    }

    /** @param slot место кнопки сверху вниз (0, 1, …) */
    private void drawButton(DrawContext context, int slot, int mouseX, int mouseY) {
        int index = buttons()[slot];
        int bl = getButtonLeft();
        int bt = getButtonTop(slot);
        boolean enabled = isButtonEnabled(index);
        boolean hover = enabled && getButtonAt(mouseX, mouseY) == index;
        int state = !enabled ? (index == SettlerScreenHandler.BUTTON_UPGRADE && isMaxLevel() ? 3 : 2) : (hover ? 1 : 0);
        Text label = getButtonLabel(index);
        if (index == SettlerScreenHandler.BUTTON_UPGRADE && enabled
                && ResearchManager.countCoins(this.client.player) < upgradeCost()) {
            label = label.copy().styled(s -> s.withColor(UiKit.BAD & 0xFFFFFF));
        }
        UiKit.button(context, this.textRenderer, bl, bt, bl + getButtonWidth(), bt + BUTTON_H, label, state);
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
                        .styled(s -> s.withColor(0xFFD45A)));
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

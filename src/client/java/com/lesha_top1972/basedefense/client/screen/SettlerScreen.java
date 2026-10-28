package com.lesha_top1972.basedefense.client.screen;

import com.lesha_top1972.basedefense.entity.settler.BuilderEntity;
import com.lesha_top1972.basedefense.entity.settler.CourierEntity;
import com.lesha_top1972.basedefense.entity.settler.SettlerEntity;
import com.lesha_top1972.basedefense.item.ModItems;
import com.lesha_top1972.basedefense.research.ResearchManager;
import com.lesha_top1972.basedefense.screen.SettlerScreenHandler;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.ingame.HandledScreen;
import net.minecraft.client.gui.screen.ingame.InventoryScreen;
import net.minecraft.client.sound.PositionedSoundInstance;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
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
 * Меню жителя в общем стиле мода («тёмное стекло» с бирюзой): слева — портрет жителя
 * (он поворачивается за мышкой, как в MineColonies), справа — имя и уровень,
 * карточка состояния, расход еды и кнопки «Улучшить» (у повара ещё «Отдать еду из руки»).
 * Работа и сбор добычи идут сами: житель складывает всё в сундук своего дома.
 */
public class SettlerScreen extends HandledScreen<SettlerScreenHandler> {

    private static final int BUTTON_H = 20;
    private static final int BUTTON_GAP = 6;
    private static final int HEADER_H = 30;
    private static final int STATUS_T = 40;      // карточка состояния (от верха окна)
    private static final int STATUS_B = 64;
    private static final int BUTTONS_TOP = 96;
    /** Ширина панели-портрета слева от окна (вместе с зазором). */
    private static final int PORTRAIT_W = 78;

    public SettlerScreen(SettlerScreenHandler handler, PlayerInventory inventory, Text title) {
        super(handler, inventory, title);
        this.backgroundWidth = 236;
        this.backgroundHeight = heightFor(1);
    }

    @Override
    protected void init() {
        super.init();
        // Окно + портрет слева вместе стоят по центру экрана
        this.x += PORTRAIT_W / 2;
    }

    private static int heightFor(int buttons) {
        return BUTTONS_TOP + buttons * BUTTON_H + (buttons - 1) * BUTTON_GAP + 34;
    }

    /** Какие кнопки показаны (по порядку сверху вниз): id кнопок из SettlerScreenHandler. */
    private int[] buttons() {
        if (handler.getProp(SettlerScreenHandler.PROP_ACCEPTS) == 1) {
            return new int[]{SettlerScreenHandler.BUTTON_INGREDIENTS, SettlerScreenHandler.BUTTON_UPGRADE};
        }
        if (handler.getProp(SettlerScreenHandler.PROP_TOGGLE) >= 0) {
            return new int[]{SettlerScreenHandler.BUTTON_TOGGLE, SettlerScreenHandler.BUTTON_UPGRADE};
        }
        return new int[]{SettlerScreenHandler.BUTTON_UPGRADE};
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

        drawPortrait(context, left - PORTRAIT_W, top, left - 4, Math.min(bottom, top + 132), mouseX, mouseY);

        UiKit.panel(context, left, top, right, bottom);
        UiKit.header(context, left + 4, top + 4, right - 4, HEADER_H);

        // Заголовок: имя и фамилия, под ними профессия; справа уровень (ромбики — сколько из трёх)
        int level = handler.getProp(SettlerScreenHandler.PROP_LEVEL);
        UiKit.embossed(context, this.textRenderer, this.title, left + 11, top + 9, UiKit.TEXT_DARK);
        Text profession = Registries.ENTITY_TYPE.get(handler.getProp(SettlerScreenHandler.PROP_PROFESSION)).getName();
        context.drawText(this.textRenderer, profession, left + 11, top + 21, UiKit.TEXT_MUTED, false);
        for (int i = 0; i < SettlerEntity.MAX_LEVEL; i++) {
            int dx = right - 14 - (SettlerEntity.MAX_LEVEL - 1 - i) * 8;
            UiKit.diamond(context, dx, top + 13, i < level ? UiKit.TEAL : 0x50FFFFFF);
        }
        Text lv = Text.translatable("screen.basedefense.settler.level_short", level);
        context.drawText(this.textRenderer, lv, right - 10 - this.textRenderer.getWidth(lv), top + 21, UiKit.TEXT_MUTED, false);

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
        context.drawItem(new ItemStack(Items.BREAD), left + 12, top + 68);
        UiKit.embossed(context, this.textRenderer, Text.translatable("screen.basedefense.settler.food",
                handler.getProp(SettlerScreenHandler.PROP_WORK_FOOD)), left + 32, top + 73, UiKit.TEXT_BODY);

        UiKit.divider(context, left + 12, right - 12, top + 88);

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

    /** Панель с живым портретом жителя: стоит на светящемся «постаменте» и смотрит на мышку. */
    private void drawPortrait(DrawContext context, int l, int t, int r, int b, int mouseX, int mouseY) {
        UiKit.panel(context, l, t, r, b);
        UiKit.inset(context, l + 6, t + 6, r - 6, b - 6, 0x70000000);
        // Бирюзовый градиент снизу — как подсветка сцены
        context.fillGradient(l + 6, b - 46, r - 6, b - 6, 0x00000000, 0x40DCE6EA);
        int cx = (l + r) / 2;
        int feet = b - 16;
        // Светящийся «постамент» под ногами (эллипс из полосок)
        context.fill(cx - 22, feet + 1, cx + 22, feet + 2, 0x60DCE6EA);
        context.fill(cx - 17, feet, cx + 17, feet + 3, 0x50DCE6EA);
        context.fill(cx - 11, feet - 1, cx + 11, feet + 4, 0x40DCE6EA);
        context.fill(cx - 22, feet + 1, cx + 22, feet + 2, 0x509BF5EC);

        LivingEntity settler = findSettler();
        if (settler == null) {
            return;
        }
        // Имя над головой в портрете не нужно (оно есть в заголовке) — на время рисования прячем
        Text name = settler.getCustomName();
        boolean nameVisible = settler.isCustomNameVisible();
        settler.setCustomName(null);
        settler.setCustomNameVisible(false);
        try {
            int size = 44;
            InventoryScreen.drawEntity(context, cx, feet, size,
                    (float) (cx - mouseX), (float) (feet - size * 1.6f - mouseY), settler);
        } finally {
            settler.setCustomName(name);
            settler.setCustomNameVisible(nameVisible);
        }
    }

    /** Житель в мире клиента по номеру, который прислал сервер. */
    private LivingEntity findSettler() {
        if (this.client == null || this.client.world == null) {
            return null;
        }
        int lo = handler.getProp(SettlerScreenHandler.PROP_ENTITY_LO) & 0xFFFF;
        int hi = handler.getProp(SettlerScreenHandler.PROP_ENTITY_HI) & 0xFFFF;
        if (lo == 0 && hi == 0) {
            return null;   // данные с сервера ещё не пришли
        }
        Entity entity = this.client.world.getEntityById((hi << 16) | lo);
        return entity instanceof SettlerEntity living ? living : null;
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
        int courier = handler.getProp(SettlerScreenHandler.PROP_COURIER);
        if (courier > 0) {
            return Text.translatable("screen.basedefense.settler.courier." + courier);
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
        int courierStatus = handler.getProp(SettlerScreenHandler.PROP_COURIER);
        if (courierStatus > 0) {
            return courierStatus == CourierEntity.STATUS_NO_BASE ? UiKit.BAD
                    : courierStatus == CourierEntity.STATUS_IDLE ? UiKit.TEXT_MUTED : UiKit.GOOD;
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
        int courier = handler.getProp(SettlerScreenHandler.PROP_COURIER);
        if (courier > 0) {
            return switch (courier) {
                case CourierEntity.STATUS_COLLECT, CourierEntity.STATUS_DELIVER -> Items.CHEST;
                case CourierEntity.STATUS_FEED -> Items.BREAD;
                case CourierEntity.STATUS_COOK -> Items.PUMPKIN_PIE;
                case CourierEntity.STATUS_NO_BASE -> Items.BARRIER;
                default -> Items.CLOCK;
            };
        }
        int bs = handler.getProp(SettlerScreenHandler.PROP_BUILD_STATUS);
        if (bs > 0) {
            return switch (bs) {
                case BuilderEntity.STATUS_BUILDING -> Items.IRON_SHOVEL;
                case BuilderEntity.STATUS_WALKING -> Items.LEATHER_BOOTS;
                case BuilderEntity.STATUS_PATHS -> Items.DIRT_PATH;
                case BuilderEntity.STATUS_REPAIR -> Items.ANVIL;
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
            case BuilderEntity.STATUS_REPAIR -> Text.translatable("screen.basedefense.settler.build.repair");
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
            case SettlerScreenHandler.BUTTON_TOGGLE -> Text.translatable(
                    handler.getProp(SettlerScreenHandler.PROP_TOGGLE) == 1
                            ? "screen.basedefense.settler.chop.on" : "screen.basedefense.settler.chop.off");
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
            case SettlerScreenHandler.BUTTON_TOGGLE -> {
                for (String line : Text.translatable("screen.basedefense.settler.tip.chop").getString().split("\n")) {
                    lines.add(Text.literal(line));
                }
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

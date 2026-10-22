package com.lesha_top1972.basedefense.client.screen;

import com.lesha_top1972.basedefense.block.ModBlocks;
import com.lesha_top1972.basedefense.item.BuildingTabletItem;
import com.lesha_top1972.basedefense.item.ModItems;
import com.lesha_top1972.basedefense.screen.TabletScreenHandler;
import com.lesha_top1972.basedefense.structure.HouseType;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.ingame.HandledScreen;
import net.minecraft.client.sound.PositionedSoundInstance;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.item.ItemStack;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.OrderedText;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

import static com.lesha_top1972.basedefense.client.screen.UiKit.*;

/**
 * Меню строительного планшета: карточки всех построек.
 * Открытые (дом строителя и исследованные) можно выбрать, закрытые — серые с замком.
 * После выбора меню закрывается, а планшет «светится» — наводишь на место и жмёшь ПКМ.
 */
public class TabletScreen extends HandledScreen<TabletScreenHandler> {

    private static final int PAD = 12;
    private static final int HEADER_H = 22;
    private static final int COLS = 2;
    private static final int CARD_W = 160;
    private static final int CARD_H = 36;
    private static final int GAP = 6;
    private static final int LIST_TOP = 50;   // от верха окна

    public TabletScreen(TabletScreenHandler handler, PlayerInventory inventory, Text title) {
        super(handler, inventory, title);
        int rows = (HouseType.values().length + COLS - 1) / COLS;
        this.backgroundWidth = PAD * 2 + COLS * CARD_W + (COLS - 1) * GAP;
        this.backgroundHeight = LIST_TOP + rows * (CARD_H + GAP) - GAP + 26;
    }

    @Nullable
    private HouseType currentSelection() {
        MinecraftClient mc = MinecraftClient.getInstance();
        return mc.player == null ? null : BuildingTabletItem.getSelected(BuildingTabletItem.findInHands(mc.player));
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        this.renderBackground(context);
        super.render(context, mouseX, mouseY, delta);
        HouseType hovered = getCardAt(mouseX, mouseY);
        if (hovered != null) {
            List<OrderedText> lines = new ArrayList<>();
            lines.add(hovered.getName().copy().formatted(Formatting.GOLD).asOrderedText());
            lines.addAll(this.textRenderer.wrapLines(
                    Text.translatable("research.basedefense." + hovered.getId() + ".desc")
                            .formatted(Formatting.GRAY), 220));
            Text status;
            if (!handler.isUnlocked(hovered)) {
                status = Text.translatable("screen.basedefense.tablet.tip.locked").formatted(Formatting.RED);
            } else if (hovered.isSelfBuilt()) {
                status = Text.translatable("screen.basedefense.tablet.self_built").formatted(Formatting.GREEN);
            } else {
                status = Text.translatable("screen.basedefense.tablet.tip.cost", hovered.getCostlyBlocks())
                        .formatted(Formatting.YELLOW);
            }
            lines.add(status.asOrderedText());
            if (handler.isUnlocked(hovered)) {
                lines.add(Text.translatable("screen.basedefense.tablet.tip.click").formatted(Formatting.AQUA)
                        .asOrderedText());
            }
            context.drawOrderedTooltip(this.textRenderer, lines, mouseX, mouseY);
        }
    }

    @Override
    protected void drawBackground(DrawContext context, float delta, int mouseX, int mouseY) {
        int left = this.x;
        int top = this.y;
        int right = left + this.backgroundWidth;
        int bottom = top + this.backgroundHeight;

        panel(context, left, top, right, bottom);
        header(context, left + 4, top + 4, right - 4, HEADER_H);
        context.drawItem(new ItemStack(ModItems.BUILDING_TABLET), left + 10, top + 7);
        embossed(context, this.textRenderer, this.title, left + 30, top + 11, TEXT_DARK);

        // Подсказка / предупреждение под заголовком
        Text hint;
        int hintColor;
        if (!handler.hasBuilder()) {
            hint = Text.translatable("screen.basedefense.tablet.no_builder");
            hintColor = BAD;
        } else {
            hint = Text.translatable("screen.basedefense.tablet.hint");
            hintColor = TEXT_BODY;
        }
        embossedCentered(context, this.textRenderer, hint, (left + right) / 2, top + HEADER_H + 12, hintColor);

        // Карточки построек
        int listL = left + PAD;
        int listT = top + LIST_TOP;
        int listR = listL + COLS * CARD_W + (COLS - 1) * GAP;
        int rows = (HouseType.values().length + COLS - 1) / COLS;
        int listB = listT + rows * (CARD_H + GAP) - GAP;
        inset(context, listL - 4, listT - 4, listR + 4, listB + 4, CONTENT_ALT);

        HouseType selected = currentSelection();
        HouseType hovered = getCardAt(mouseX, mouseY);
        for (HouseType type : HouseType.values()) {
            drawCard(context, type, cardX(type), cardY(type), type == hovered, type == selected);
        }

        // Внизу: как пользоваться
        embossedCentered(context, this.textRenderer, Text.translatable("screen.basedefense.tablet.footer"),
                (left + right) / 2, bottom - 17, TEXT_MUTED);
    }

    private void drawCard(DrawContext c, HouseType type, int cx, int cy, boolean hover, boolean selected) {
        boolean unlocked = handler.isUnlocked(type);
        int fill = !unlocked ? 0xFFDCD0B4 : (selected ? 0xFFF6E7B8 : (hover ? 0xFFFBF3DE : CONTENT));
        card(c, cx, cy, cx + CARD_W, cy + CARD_H, fill, unlocked && (hover || selected));
        if (selected) {
            outline(c, cx - 1, cy - 1, cx + CARD_W + 1, cy + CARD_H + 1, GOLD);
        }

        // Иконка в «гнезде»
        slot(c, cx + 7, cy + 10, selected);
        c.drawItem(new ItemStack(ModBlocks.houseBlock(type)), cx + 7, cy + 10);

        int tx = cx + 30;
        c.drawText(this.textRenderer, type.getName(), tx, cy + 8, unlocked ? TEXT_DARK : TEXT_MUTED, false);
        Text sub;
        int subColor;
        if (!unlocked) {
            sub = Text.translatable("screen.basedefense.tablet.locked");
            subColor = TEXT_MUTED;
        } else if (type.isSelfBuilt()) {
            sub = Text.translatable("screen.basedefense.tablet.self_built");
            subColor = GOOD;
        } else {
            sub = Text.translatable("screen.basedefense.tablet.cost", type.getCostlyBlocks());
            subColor = TEXT_BODY;
        }
        c.drawText(this.textRenderer, sub, tx, cy + 21, subColor, false);

        if (!unlocked) {
            c.fill(cx + 1, cy + 1, cx + CARD_W - 1, cy + CARD_H - 1, 0x55E2D6BC);
            drawLock(c, cx + CARD_W - 13, cy + 5);
        } else if (selected) {
            drawCheck(c, cx + CARD_W - 13, cy + 5);
        }
    }

    /** Замочек 8×9. */
    private static void drawLock(DrawContext c, int x, int y) {
        int dark = 0xFF6B5A42;
        c.fill(x + 2, y, x + 6, y + 1, dark);
        c.fill(x + 1, y + 1, x + 2, y + 4, dark);
        c.fill(x + 6, y + 1, x + 7, y + 4, dark);
        c.fill(x, y + 4, x + 8, y + 9, dark);
        c.fill(x + 1, y + 5, x + 7, y + 8, 0xFFB59A62);
        c.fill(x + 3, y + 6, x + 5, y + 7, dark);
    }

    /** Зелёная галочка в кружке 8×8. */
    private static void drawCheck(DrawContext c, int x, int y) {
        c.fill(x + 1, y, x + 7, y + 8, GOOD);
        c.fill(x, y + 1, x + 8, y + 7, GOOD);
        c.fill(x + 1, y + 4, x + 2, y + 5, 0xFFFFFFFF);
        c.fill(x + 2, y + 5, x + 3, y + 6, 0xFFFFFFFF);
        c.fill(x + 3, y + 4, x + 4, y + 5, 0xFFFFFFFF);
        c.fill(x + 4, y + 3, x + 5, y + 4, 0xFFFFFFFF);
        c.fill(x + 5, y + 2, x + 6, y + 3, 0xFFFFFFFF);
    }

    private int cardX(HouseType type) {
        return this.x + PAD + (type.ordinal() % COLS) * (CARD_W + GAP);
    }

    private int cardY(HouseType type) {
        return this.y + LIST_TOP + (type.ordinal() / COLS) * (CARD_H + GAP);
    }

    @Nullable
    private HouseType getCardAt(double mouseX, double mouseY) {
        for (HouseType type : HouseType.values()) {
            int cx = cardX(type);
            int cy = cardY(type);
            if (mouseX >= cx && mouseX < cx + CARD_W && mouseY >= cy && mouseY < cy + CARD_H) {
                return type;
            }
        }
        return null;
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button == 0) {
            HouseType type = getCardAt(mouseX, mouseY);
            if (type != null) {
                MinecraftClient mc = MinecraftClient.getInstance();
                if (!handler.isUnlocked(type)) {
                    mc.getSoundManager().play(PositionedSoundInstance.master(SoundEvents.ENTITY_VILLAGER_NO, 1.0f));
                    return true;
                }
                mc.getSoundManager().play(PositionedSoundInstance.master(SoundEvents.UI_BUTTON_CLICK, 1.0f));
                // Номер кнопки = номер постройки. Сервер проверит исследование, запишет выбор и закроет меню.
                if (mc.interactionManager != null) {
                    mc.interactionManager.clickButton(this.handler.syncId, type.ordinal());
                }
                return true;
            }
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    protected void drawForeground(DrawContext context, int mouseX, int mouseY) {
        // Пусто: всё рисуем сами
    }
}

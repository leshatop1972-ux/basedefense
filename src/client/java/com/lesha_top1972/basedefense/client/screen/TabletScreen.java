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
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.OrderedText;
import net.minecraft.text.Text;
import net.minecraft.util.Util;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static com.lesha_top1972.basedefense.client.screen.UiKit.*;

/**
 * Меню строительного планшета в стиле «тёмное стекло».
 * Слева — список всех построек (открытые, закрытые с замком, выбранная с галочкой).
 * Справа — подробная панель отмеченной постройки: крупная иконка, статус, описание,
 * самые нужные материалы и кнопка «Выбрать для постройки».
 * Клик по строке отмечает постройку, двойной клик или кнопка — выбирает её в планшет.
 */
public class TabletScreen extends HandledScreen<TabletScreenHandler> {

    private static final int PAD = 10;
    private static final int HEADER_H = 22;
    private static final int LIST_W = 160;
    private static final int ROW_H = 22;
    private static final int ROW_GAP = 2;
    private static final int LIST_TOP = HEADER_H + 14;   // от верха окна
    private static final int DETAIL_W = 196;
    private static final int BUTTON_H = 18;
    private static final int MAT_COLS = 4;
    private static final int MAT_ROWS = 2;

    /** Отмеченная в списке постройка (то, что показано справа). */
    private HouseType focused = HouseType.BUILDER;
    private long lastClickTime;
    @Nullable
    private HouseType lastClicked;

    public TabletScreen(TabletScreenHandler handler, PlayerInventory inventory, Text title) {
        super(handler, inventory, title);
        int rows = HouseType.values().length;
        this.backgroundWidth = PAD * 2 + LIST_W + 8 + DETAIL_W;
        this.backgroundHeight = LIST_TOP + rows * (ROW_H + ROW_GAP) - ROW_GAP + 26;
    }

    @Override
    protected void init() {
        super.init();
        HouseType current = currentSelection();
        if (current != null) {
            focused = current;
        }
    }

    @Nullable
    private HouseType currentSelection() {
        MinecraftClient mc = MinecraftClient.getInstance();
        return mc.player == null ? null : BuildingTabletItem.getSelected(BuildingTabletItem.findInHands(mc.player));
    }

    // ---------------------------------------------------------------------
    // Отрисовка
    // ---------------------------------------------------------------------

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        this.renderBackground(context);
        super.render(context, mouseX, mouseY, delta);

        // Подсказка над материалом: название и количество
        Item mat = getMaterialAt(mouseX, mouseY);
        if (mat != null) {
            List<Text> lines = new ArrayList<>();
            lines.add(mat.getName());
            lines.add(Text.translatable("screen.basedefense.tablet.tip.count", focused.getRequirements().get(mat))
                    .styled(s -> s.withColor(TEXT_BODY & 0xFFFFFF)));
            context.drawTooltip(this.textRenderer, lines, mouseX, mouseY);
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
        context.drawItem(new ItemStack(ModItems.BUILDING_TABLET), left + 9, top + 7);
        embossed(context, this.textRenderer, this.title, left + 29, top + 11, TEXT_DARK);

        // Справа в заголовке: открыто построек / всего
        int open = 0;
        for (HouseType type : HouseType.values()) {
            if (handler.isUnlocked(type)) {
                open++;
            }
        }
        Text counter = Text.translatable("screen.basedefense.tablet.open", open, HouseType.values().length);
        pill(context, this.textRenderer, right - 12 - this.textRenderer.getWidth(counter) - 8, top + 9, counter, TEAL_LIGHT);

        // Список построек
        int listL = left + PAD;
        int listT = top + LIST_TOP;
        int listB = listT + HouseType.values().length * (ROW_H + ROW_GAP) - ROW_GAP;
        inset(context, listL - 3, listT - 3, listL + LIST_W + 3, listB + 3, CONTENT);
        HouseType selected = currentSelection();
        HouseType hovered = getRowAt(mouseX, mouseY);
        for (HouseType type : HouseType.values()) {
            drawRow(context, type, listL, rowY(type), type == hovered, type == selected);
        }

        drawDetail(context, mouseX, mouseY, selected);

        // Нижняя строка: подсказка или предупреждение
        Text hint;
        int hintColor;
        if (!handler.hasBuilder() && focused != HouseType.BUILDER) {
            hint = Text.translatable("screen.basedefense.tablet.no_builder");
            hintColor = WARN;
        } else {
            hint = Text.translatable("screen.basedefense.tablet.footer");
            hintColor = TEXT_MUTED;
        }
        embossedCentered(context, this.textRenderer, hint, (left + right) / 2, bottom - 16, hintColor);
    }

    /** Строка списка: иконка, название, статус справа. */
    private void drawRow(DrawContext c, HouseType type, int rl, int rt, boolean hover, boolean selected) {
        boolean unlocked = handler.isUnlocked(type);
        boolean isFocused = type == focused;
        int rr = rl + LIST_W;
        int rb = rt + ROW_H;

        // Фон строки
        if (isFocused) {
            c.fillGradient(rl, rt, rr, rb, 0x8033C9BE, 0x40167A73);
            c.fill(rl, rt, rl + 2, rb, TEAL_LIGHT);
            outline(c, rl, rt, rr, rb, 0x8033C9BE);
        } else if (hover && unlocked) {
            c.fill(rl, rt, rr, rb, 0x30FFFFFF);
            c.fill(rl, rt, rl + 2, rb, TEAL);
        } else {
            c.fill(rl, rt, rr, rb, unlocked ? 0x30222830 : 0x18000000);
        }

        c.drawItem(new ItemStack(ModBlocks.houseBlock(type)), rl + 4, rt + 3);
        if (!unlocked) {
            c.fill(rl + 4, rt + 3, rl + 20, rt + 19, 0x99101215);
        }
        int textColor = !unlocked ? TEXT_MUTED : (isFocused ? 0xFFFFFFFF : TEXT_DARK);
        c.drawText(this.textRenderer, type.getName(), rl + 24, rt + 7, textColor, true);

        if (selected) {
            check(c, rr - 12, rt + 7, GOOD);
        } else if (!unlocked) {
            lock(c, rr - 12, rt + 6);
        }
    }

    /** Правая панель: подробности отмеченной постройки. */
    private void drawDetail(DrawContext c, int mouseX, int mouseY, @Nullable HouseType selected) {
        int dl = detailLeft();
        int dt = this.y + LIST_TOP - 3;
        int dr = dl + DETAIL_W;
        int db = detailBottom();
        boolean unlocked = handler.isUnlocked(focused);

        // Подложка панели
        c.fill(dl, dt, dr, db, 0x70000000);
        outline(c, dl, dt, dr, db, 0xFF262D34);
        corners(c, dl, dt, dr, db, 5, TEAL_DARK);

        // Крупная иконка в рамке
        int il = dl + 8, it = dt + 8;
        c.fillGradient(il, it, il + 38, it + 38, 0x6033C9BE, 0x20167A73);
        outline(c, il, it, il + 38, it + 38, unlocked ? TEAL : 0xFF3A424A);
        c.getMatrices().push();
        c.getMatrices().translate(il + 3, it + 3, 0);
        c.getMatrices().scale(2.0f, 2.0f, 1.0f);
        c.drawItem(new ItemStack(ModBlocks.houseBlock(focused)), 0, 0);
        c.getMatrices().pop();
        if (!unlocked) {
            c.getMatrices().push();
            c.getMatrices().translate(0, 0, 300);
            c.fill(il + 1, it + 1, il + 37, it + 37, 0x99101215);
            lock(c, il + 15, it + 14);
            c.getMatrices().pop();
        }

        // Название и статус
        int tx = il + 46;
        embossed(c, this.textRenderer, focused.getName(), tx, it + 4, 0xFFFFFFFF);
        Text status;
        int statusColor;
        if (focused == selected) {
            status = Text.translatable("screen.basedefense.tablet.status.selected");
            statusColor = GOOD_LIGHT;
        } else if (!unlocked) {
            status = Text.translatable("screen.basedefense.tablet.locked");
            statusColor = BAD;
        } else if (focused.isSelfBuilt()) {
            status = Text.translatable("screen.basedefense.tablet.self_built");
            statusColor = TEAL_LIGHT;
        } else {
            status = Text.translatable("screen.basedefense.tablet.status.open");
            statusColor = TEAL_LIGHT;
        }
        pill(c, this.textRenderer, tx, it + 16, status, statusColor);
        if (!focused.isSelfBuilt()) {
            c.drawText(this.textRenderer, Text.translatable("screen.basedefense.tablet.cost", focused.getCostlyBlocks()),
                    tx, it + 30, TEXT_MUTED, false);
        }

        // Описание
        int textY = it + 46;
        int matTop = matTop();
        for (OrderedText line : this.textRenderer.wrapLines(
                Text.translatable("research.basedefense." + focused.getId() + ".desc"), DETAIL_W - 16)) {
            if (textY + 9 > matTop - 14) {
                break;
            }
            c.drawText(this.textRenderer, line, dl + 8, textY, TEXT_BODY, false);
            textY += 10;
        }

        // Материалы (самые нужные)
        c.drawText(this.textRenderer, Text.translatable(focused.isSelfBuilt()
                        ? "screen.basedefense.tablet.materials_free" : "screen.basedefense.tablet.materials"),
                dl + 8, matTop - 11, TEXT_MUTED, false);
        if (!focused.isSelfBuilt()) {
            List<Map.Entry<Item, Integer>> mats = topMaterials();
            for (int i = 0; i < MAT_COLS * MAT_ROWS; i++) {
                int sx = matX(i), sy = matY(i);
                slot(c, sx, sy, false);
                if (i < mats.size()) {
                    Map.Entry<Item, Integer> e = mats.get(i);
                    ItemStack stack = new ItemStack(e.getKey());
                    c.drawItem(stack, sx, sy);
                    c.drawItemInSlot(this.textRenderer, stack, sx, sy, String.valueOf(e.getValue()));
                }
            }
            int more = focused.getRequirements().size() - MAT_COLS * MAT_ROWS;
            if (more > 0) {
                c.drawText(this.textRenderer, Text.translatable("screen.basedefense.tablet.more", more),
                        matX(MAT_COLS - 1) + 22, matY(0) + 5, TEXT_MUTED, false);
            }
        }

        // Кнопка
        int bl = dl + 8, bt = db - 8 - BUTTON_H, br = dr - 8, bb = db - 8;
        if (focused == selected) {
            button(c, this.textRenderer, bl, bt, br, bb, Text.translatable("screen.basedefense.tablet.button.chosen"), 3);
        } else if (!unlocked) {
            button(c, this.textRenderer, bl, bt, br, bb, Text.translatable("screen.basedefense.tablet.button.locked"), 2);
        } else {
            boolean hover = mouseX >= bl && mouseX < br && mouseY >= bt && mouseY < bb;
            // Лёгкая «пульсация» рамки, чтобы кнопка привлекала внимание
            if (!hover && (Util.getMeasuringTimeMs() / 600) % 2 == 0) {
                outline(c, bl - 1, bt - 1, br + 1, bb + 1, TEAL_FAINT);
            }
            button(c, this.textRenderer, bl, bt, br, bb, Text.translatable("screen.basedefense.tablet.button.select"),
                    hover ? 1 : 0);
        }
    }

    /** Материалы постройки по убыванию количества. */
    private List<Map.Entry<Item, Integer>> topMaterials() {
        List<Map.Entry<Item, Integer>> list = new ArrayList<>(focused.getRequirements().entrySet());
        list.sort((a, b) -> Integer.compare(b.getValue(), a.getValue()));
        return list;
    }

    // ---------------------------------------------------------------------
    // Координаты
    // ---------------------------------------------------------------------

    private int rowY(HouseType type) {
        return this.y + LIST_TOP + type.ordinal() * (ROW_H + ROW_GAP);
    }

    private int detailLeft() {
        return this.x + PAD + LIST_W + 8;
    }

    private int detailBottom() {
        return this.y + LIST_TOP + HouseType.values().length * (ROW_H + ROW_GAP) - ROW_GAP + 3;
    }

    private int matTop() {
        return detailBottom() - 8 - BUTTON_H - 6 - MAT_ROWS * 19;
    }

    private int matX(int i) {
        return detailLeft() + 9 + (i % MAT_COLS) * 19;
    }

    private int matY(int i) {
        return matTop() + (i / MAT_COLS) * 19;
    }

    @Nullable
    private HouseType getRowAt(double mouseX, double mouseY) {
        int rl = this.x + PAD;
        for (HouseType type : HouseType.values()) {
            int rt = rowY(type);
            if (mouseX >= rl && mouseX < rl + LIST_W && mouseY >= rt && mouseY < rt + ROW_H) {
                return type;
            }
        }
        return null;
    }

    @Nullable
    private Item getMaterialAt(double mouseX, double mouseY) {
        if (focused.isSelfBuilt()) {
            return null;
        }
        List<Map.Entry<Item, Integer>> mats = topMaterials();
        for (int i = 0; i < Math.min(mats.size(), MAT_COLS * MAT_ROWS); i++) {
            int sx = matX(i), sy = matY(i);
            if (mouseX >= sx && mouseX < sx + 16 && mouseY >= sy && mouseY < sy + 16) {
                return mats.get(i).getKey();
            }
        }
        return null;
    }

    private boolean isOverButton(double mouseX, double mouseY) {
        int dl = detailLeft();
        int db = detailBottom();
        return mouseX >= dl + 8 && mouseX < dl + DETAIL_W - 8 && mouseY >= db - 8 - BUTTON_H && mouseY < db - 8;
    }

    // ---------------------------------------------------------------------
    // Клики
    // ---------------------------------------------------------------------

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button == 0) {
            HouseType row = getRowAt(mouseX, mouseY);
            if (row != null) {
                long now = Util.getMeasuringTimeMs();
                boolean doubleClick = row == lastClicked && now - lastClickTime < 300;
                lastClicked = row;
                lastClickTime = now;
                focused = row;
                if (doubleClick && handler.isUnlocked(row)) {
                    choose(row);
                } else {
                    playSound(SoundEvents.UI_BUTTON_CLICK, 1.4f);
                }
                return true;
            }
            if (isOverButton(mouseX, mouseY)) {
                if (handler.isUnlocked(focused) && focused != currentSelection()) {
                    choose(focused);
                } else if (!handler.isUnlocked(focused)) {
                    playSound(SoundEvents.ENTITY_VILLAGER_NO, 1.0f);
                }
                return true;
            }
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    /** Номер кнопки = номер постройки. Сервер проверит исследование, запишет выбор и закроет меню. */
    private void choose(HouseType type) {
        playSound(SoundEvents.UI_BUTTON_CLICK, 1.0f);
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.interactionManager != null) {
            mc.interactionManager.clickButton(this.handler.syncId, type.ordinal());
        }
    }

    private static void playSound(net.minecraft.sound.SoundEvent sound, float pitch) {
        MinecraftClient.getInstance().getSoundManager().play(PositionedSoundInstance.master(sound, pitch));
    }

    /** UI_BUTTON_CLICK в 1.20.1 — не SoundEvent, а ссылка на него (RegistryEntry). */
    private static void playSound(net.minecraft.registry.entry.RegistryEntry<net.minecraft.sound.SoundEvent> sound,
                                  float pitch) {
        MinecraftClient.getInstance().getSoundManager().play(PositionedSoundInstance.master(sound, pitch));
    }

    @Override
    protected void drawForeground(DrawContext context, int mouseX, int mouseY) {
        // Пусто: всё рисуем сами
    }
}

package com.lesha_top1972.basedefense.client.screen;

import com.lesha_top1972.basedefense.military.SquadState;
import com.lesha_top1972.basedefense.screen.SquadScreenHandler;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.ingame.HandledScreen;
import net.minecraft.client.sound.PositionedSoundInstance;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;
import net.minecraft.util.math.BlockPos;

import java.util.ArrayList;
import java.util.List;

import static com.lesha_top1972.basedefense.client.screen.UiKit.*;

/**
 * Меню Повестки (стиль «стекло»): слева три карточки отрядов — состав (до 3 стражников),
 * точка обороны и кнопка «Отозвать»; клик по заголовку отряда выбирает его для Повестки.
 * Справа — стражники без отряда с кнопками «I», «II», «III». Колёсико прокручивает список.
 */
public class SquadScreen extends HandledScreen<SquadScreenHandler> {

    private static final int W = 330;
    private static final int H = 232;
    private static final int HEADER_H = 22;
    private static final int CARD_L = 10;
    private static final int CARD_R = 162;
    private static final int CARD_T = 30;
    private static final int CARD_H = 58;
    private static final int CARD_STEP = 62;
    private static final int LIST_L = 170;
    private static final int LIST_R = 320;
    private static final int LIST_T = 44;
    private static final int ROW_H = 18;
    private static final int ROWS = 9;
    private static final int BTN = 14;

    private static final ItemStack NO_WEAPON = new ItemStack(Items.BARRIER);

    private int scroll;

    public SquadScreen(SquadScreenHandler handler, PlayerInventory inventory, Text title) {
        super(handler, inventory, title);
        this.backgroundWidth = W;
        this.backgroundHeight = H;
    }

    private List<Integer> members(int squad) {
        List<Integer> out = new ArrayList<>();
        List<SquadScreenHandler.Entry> all = handler.getGuards();
        for (int i = 0; i < all.size(); i++) {
            if (all.get(i).squad == squad) {
                out.add(i);
            }
        }
        return out;
    }

    private int cardTop(int s) {
        return this.y + CARD_T + s * CARD_STEP;
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        this.renderBackground(context);
        super.render(context, mouseX, mouseY, delta);
        // Подсказка: расстояние до свободного стражника
        int row = rowAt(mouseX, mouseY);
        if (row >= 0) {
            SquadScreenHandler.Entry e = handler.getGuards().get(members(-1).get(row));
            if (mouseX < this.x + LIST_R - 3 * (BTN + 2) - 2) {
                context.drawTooltip(this.textRenderer, List.of(Text.literal(e.name),
                        Text.translatable("screen.basedefense.squad.distance", e.distance)
                                .styled(st -> st.withColor(0xAAAAAA))), mouseX, mouseY);
            }
        }
    }

    @Override
    protected void drawBackground(DrawContext c, float delta, int mouseX, int mouseY) {
        int l = this.x;
        int t = this.y;
        int r = l + W;
        int b = t + H;
        panel(c, l, t, r, b);
        header(c, l + 4, t + 4, r - 4, HEADER_H);
        c.drawItem(new ItemStack(com.lesha_top1972.basedefense.item.ModItems.SUMMONS), l + 10, t + 7);
        embossed(c, this.textRenderer, this.title, l + 30, t + 11, TEXT_DARK);

        for (int s = 0; s < SquadState.SQUADS; s++) {
            drawCard(c, s, mouseX, mouseY);
        }

        // Свободные стражники
        c.drawText(this.textRenderer, Text.translatable("screen.basedefense.squad.free"), l + LIST_L, t + CARD_T + 1,
                TEXT_MUTED, false);
        inset(c, l + LIST_L - 2, t + LIST_T - 2, l + LIST_R + 2, t + LIST_T + ROWS * ROW_H + 1, CONTENT_ALT);
        List<Integer> free = members(-1);
        scroll = Math.max(0, Math.min(scroll, Math.max(0, free.size() - ROWS)));
        if (free.isEmpty()) {
            for (var line : this.textRenderer.wrapLines(Text.translatable("screen.basedefense.squad.no_free"),
                    LIST_R - LIST_L - 10)) {
                c.drawText(this.textRenderer, line, l + LIST_L + 4, t + LIST_T + 6, TEXT_MUTED, false);
                break;
            }
        }
        for (int k = 0; k < ROWS && k + scroll < free.size(); k++) {
            int index = free.get(k + scroll);
            SquadScreenHandler.Entry e = handler.getGuards().get(index);
            int rt = t + LIST_T + k * ROW_H;
            boolean hover = mouseX >= l + LIST_L && mouseX < l + LIST_R && mouseY >= rt && mouseY < rt + ROW_H - 1;
            c.fill(l + LIST_L, rt, l + LIST_R, rt + ROW_H - 1, hover ? 0x28FFFFFF : 0x14FFFFFF);
            c.drawItem(e.weapon.isEmpty() ? NO_WEAPON : e.weapon, l + LIST_L + 1, rt);
            int bx0 = l + LIST_R - 3 * (BTN + 2);
            String name = this.textRenderer.trimToWidth(e.name, bx0 - (l + LIST_L + 20) - 3);
            c.drawText(this.textRenderer, name, l + LIST_L + 20, rt + 5, TEXT_BODY, false);
            for (int s = 0; s < SquadState.SQUADS; s++) {
                int bx = bx0 + s * (BTN + 2);
                boolean full = handler.countIn(s) >= SquadState.MAX_MEMBERS;
                boolean bh = !full && mouseX >= bx && mouseX < bx + BTN && mouseY >= rt + 2 && mouseY < rt + 2 + BTN;
                button(c, this.textRenderer, bx, rt + 2, bx + BTN, rt + 2 + BTN,
                        Text.literal(SquadState.roman(s)), full ? 2 : bh ? 1 : 0);
            }
        }
        if (free.size() > ROWS) {
            int trackT = t + LIST_T;
            int trackH = ROWS * ROW_H;
            int thumbH = Math.max(10, trackH * ROWS / free.size());
            int thumbT = trackT + (trackH - thumbH) * scroll / Math.max(1, free.size() - ROWS);
            c.fill(l + LIST_R + 3, thumbT, l + LIST_R + 5, thumbT + thumbH, 0x80FFFFFF);
        }

        // Подсказка внизу
        embossedCentered(c, this.textRenderer, Text.translatable(handler.getSelected() >= 0
                        ? "screen.basedefense.squad.hint" : "screen.basedefense.squad.hint_pick",
                        handler.getSelected() >= 0 ? SquadState.roman(handler.getSelected()) : ""),
                l + W / 2, b - 15, handler.getSelected() >= 0 ? TEXT_BODY : WARN);
    }

    private void drawCard(DrawContext c, int s, int mouseX, int mouseY) {
        int l = this.x + CARD_L;
        int r = this.x + CARD_R;
        int t = cardTop(s);
        boolean selected = handler.getSelected() == s;
        boolean titleHover = mouseX >= l && mouseX < r && mouseY >= t && mouseY < t + 15;
        card(c, l, t, r, t + CARD_H, selected ? 0x30DCE6EA : CARD, titleHover || selected);
        if (selected) {
            c.fill(l, t, l + 2, t + CARD_H, TEAL_LIGHT);
        }
        embossed(c, this.textRenderer, Text.translatable("screen.basedefense.squad.name", SquadState.roman(s)),
                l + 6, t + 5, selected ? 0xFFFFFFFF : TEXT_DARK);
        List<Integer> mem = members(s);
        Text count = Text.literal(mem.size() + "/" + SquadState.MAX_MEMBERS);
        if (selected) {
            Text p = Text.translatable("screen.basedefense.squad.selected");
            pill(c, this.textRenderer, r - 6 - this.textRenderer.getWidth(p) - 8, t + 3, p, TEAL_LIGHT);
        } else {
            c.drawText(this.textRenderer, count, r - 6 - this.textRenderer.getWidth(count), t + 5, TEXT_MUTED, false);
        }

        // Точка обороны / патруль
        BlockPos point = handler.getPoint(s);
        if (point != null) {
            String coords = point.getX() + " " + point.getY() + " " + point.getZ();
            c.drawText(this.textRenderer, Text.translatable("screen.basedefense.squad.defending", coords),
                    l + 6, t + 18, TEAL_LIGHT, false);
            int bl = r - 50;
            boolean bh = mouseX >= bl && mouseX < r - 4 && mouseY >= t + 15 && mouseY < t + 27;
            button(c, this.textRenderer, bl, t + 15, r - 4, t + 27,
                    Text.translatable("screen.basedefense.squad.recall"), bh ? 1 : 0);
        } else {
            c.drawText(this.textRenderer, Text.translatable("screen.basedefense.squad.patrol"),
                    l + 6, t + 18, TEXT_MUTED, false);
        }

        // Состав
        for (int k = 0; k < SquadState.MAX_MEMBERS; k++) {
            int ry = t + 29 + k * 9;
            if (k >= mem.size()) {
                c.drawText(this.textRenderer, Text.literal("—"), l + 8, ry, 0x40FFFFFF, false);
                continue;
            }
            SquadScreenHandler.Entry e = handler.getGuards().get(mem.get(k));
            c.getMatrices().push();
            c.getMatrices().translate(l + 6, ry - 1, 0);
            c.getMatrices().scale(0.5f, 0.5f, 1.0f);
            c.drawItem(e.weapon.isEmpty() ? NO_WEAPON : e.weapon, 0, 0);
            c.getMatrices().pop();
            String name = this.textRenderer.trimToWidth(e.name, r - 22 - (l + 18));
            c.drawText(this.textRenderer, name, l + 18, ry, e.distance < 0 ? TEXT_MUTED : TEXT_BODY, false);
            int xl = r - 14;
            boolean xh = mouseX >= xl && mouseX < xl + 9 && mouseY >= ry - 1 && mouseY < ry + 8;
            c.drawText(this.textRenderer, Text.literal("×"), xl + 2, ry, xh ? BAD : TEXT_MUTED, false);
        }
    }

    /** Строка свободного стражника под мышкой (номер в списке свободных), −1 — нет. */
    private int rowAt(double mouseX, double mouseY) {
        int l = this.x + LIST_L;
        int t = this.y + LIST_T;
        if (mouseX < l || mouseX >= this.x + LIST_R || mouseY < t || mouseY >= t + ROWS * ROW_H) {
            return -1;
        }
        int k = (int) ((mouseY - t) / ROW_H) + scroll;
        return k < members(-1).size() ? k : -1;
    }

    private void press(int id) {
        MinecraftClient mc = MinecraftClient.getInstance();
        if (handler.apply(id)) {
            mc.getSoundManager().play(PositionedSoundInstance.master(SoundEvents.UI_BUTTON_CLICK, 1.0f));
            if (mc.interactionManager != null) {
                mc.interactionManager.clickButton(handler.syncId, id);
            }
        } else {
            mc.getSoundManager().play(PositionedSoundInstance.master(SoundEvents.ENTITY_VILLAGER_NO, 1.0f));
        }
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button != 0) {
            return super.mouseClicked(mouseX, mouseY, button);
        }
        // Карточки отрядов
        for (int s = 0; s < SquadState.SQUADS; s++) {
            int l = this.x + CARD_L;
            int r = this.x + CARD_R;
            int t = cardTop(s);
            if (mouseX < l || mouseX >= r || mouseY < t || mouseY >= t + CARD_H) {
                continue;
            }
            if (mouseY < t + 15) {
                press(SquadScreenHandler.BTN_SELECT + s);
                return true;
            }
            if (handler.getPoint(s) != null && mouseX >= r - 50 && mouseX < r - 4 && mouseY >= t + 15 && mouseY < t + 27) {
                press(SquadScreenHandler.BTN_RECALL + s);
                return true;
            }
            List<Integer> mem = members(s);
            for (int k = 0; k < mem.size(); k++) {
                int ry = t + 29 + k * 9;
                if (mouseX >= r - 14 && mouseX < r - 5 && mouseY >= ry - 1 && mouseY < ry + 8) {
                    press(SquadScreenHandler.BTN_ASSIGN + mem.get(k) * 4 + 3);
                    return true;
                }
            }
            return true;
        }
        // Кнопки «I», «II», «III» у свободных стражников
        int row = rowAt(mouseX, mouseY);
        if (row >= 0) {
            int index = members(-1).get(row);
            int rt = this.y + LIST_T + (row - scroll) * ROW_H;
            int bx0 = this.x + LIST_R - 3 * (BTN + 2);
            for (int s = 0; s < SquadState.SQUADS; s++) {
                int bx = bx0 + s * (BTN + 2);
                if (mouseX >= bx && mouseX < bx + BTN && mouseY >= rt + 2 && mouseY < rt + 2 + BTN) {
                    press(SquadScreenHandler.BTN_ASSIGN + index * 4 + s);
                    return true;
                }
            }
            return true;
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double amount) {
        scroll -= (int) Math.signum(amount);
        return true;
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

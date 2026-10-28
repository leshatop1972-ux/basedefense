package com.lesha_top1972.basedefense.client.screen;

import com.lesha_top1972.basedefense.entity.settler.GuardEntity;
import com.lesha_top1972.basedefense.military.SquadState;
import com.lesha_top1972.basedefense.screen.GuardScreenHandler;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.ingame.HandledScreen;
import net.minecraft.client.gui.screen.ingame.InventoryScreen;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.screen.slot.Slot;
import net.minecraft.text.Text;

import static com.lesha_top1972.basedefense.client.screen.UiKit.*;
import static com.lesha_top1972.basedefense.screen.GuardScreenHandler.*;

/**
 * Меню стражника (стиль «стекло»): слева живой портрет, рядом броня, оружие, стрелы, артефакты
 * (закрыты замочком), еда; здоровье полоской, броня, урон, отряд; внизу — инвентарь игрока.
 */
public class GuardScreen extends HandledScreen<GuardScreenHandler> {

    private static final int HEADER_H = 22;

    public GuardScreen(GuardScreenHandler handler, PlayerInventory inventory, Text title) {
        super(handler, inventory, title);
        this.backgroundWidth = W;
        this.backgroundHeight = H;
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        this.renderBackground(context);
        super.render(context, mouseX, mouseY, delta);
        Slot hovered = this.focusedSlot;
        if (hovered != null && !hovered.hasStack()) {
            int index = this.handler.slots.indexOf(hovered);
            Text tip = switch (index) {
                case 0 -> Text.translatable("screen.basedefense.guard.tip.weapon");
                case 1, 2, 3, 4 -> Text.translatable("screen.basedefense.guard.tip.armor");
                case 5, 6, 7 -> Text.translatable("screen.basedefense.guard.tip.food");
                case 8, 9 -> Text.translatable("screen.basedefense.guard.tip.arrows");
                case 10, 11 -> Text.translatable("screen.basedefense.guard.tip.artifact");
                default -> null;
            };
            if (tip != null) {
                context.drawTooltip(this.textRenderer, this.textRenderer.wrapLines(tip, 180), mouseX, mouseY);
                return;
            }
        }
        this.drawMouseoverTooltip(context, mouseX, mouseY);
    }

    @Override
    protected void drawBackground(DrawContext c, float delta, int mouseX, int mouseY) {
        int l = this.x;
        int t = this.y;
        int r = l + W;
        int b = t + H;
        panel(c, l, t, r, b);
        header(c, l + 4, t + 4, r - 4, HEADER_H);
        embossed(c, this.textRenderer, this.title, l + 10, t + 11, TEXT_DARK);

        // Режим — пилюля справа в заголовке
        int mode = handler.getProp(PROP_MODE);
        Text modeText = Text.translatable("screen.basedefense.guard.mode." + mode);
        int mw = this.textRenderer.getWidth(modeText) + 8;
        int modeColor = switch (mode) {
            case GuardEntity.MODE_FIGHT -> BAD;
            case GuardEntity.MODE_DEFEND -> TEAL_LIGHT;
            case GuardEntity.MODE_UNARMED -> WARN;
            case GuardEntity.MODE_EAT -> GOOD_LIGHT;
            default -> TEXT_BODY;
        };
        pill(c, this.textRenderer, r - 12 - mw, t + 9, modeText, modeColor);

        // Портрет
        drawPortrait(c, l + 8, t + 28, l + 76, t + 116, mouseX, mouseY);

        // Здоровье
        float hp = handler.getProp(PROP_HP) / 10.0f;
        float max = Math.max(1, handler.getProp(PROP_MAX_HP)) / 10.0f;
        Text hpText = Text.translatable("screen.basedefense.guard.hp", fmt(hp), fmt(max));
        c.drawText(this.textRenderer, hpText, l + 82, t + 29, TEXT_BODY, false);
        progress(c, l + 82 + this.textRenderer.getWidth(hpText) + 6, t + 30, r - 10, t + 36, hp / max,
                0xFFFF7A6A, 0xFFB8322A);

        // Подписи групп
        label(c, "screen.basedefense.guard.weapon", l + WEAPON_X - 1, t + WEAPON_Y - 10);
        label(c, "screen.basedefense.guard.arrows", l + ARROW_X - 1, t + ARROW_Y - 10);
        label(c, "screen.basedefense.guard.artifacts", l + ARTIFACT_X - 1, t + ARTIFACT_Y - 10);
        label(c, "screen.basedefense.guard.food", l + FOOD_X - 1, t + FOOD_Y - 10);

        // Ячейки слотов стражника; у артефактов — замочки
        for (int i = 0; i < 12; i++) {
            Slot s = handler.slots.get(i);
            slot(c, l + s.x, t + s.y, i == 0);
        }
        for (int i = 10; i < 12; i++) {
            Slot s = handler.slots.get(i);
            c.fill(l + s.x, t + s.y, l + s.x + 16, t + s.y + 16, 0x50000000);
            lock(c, l + s.x + 4, t + s.y + 4);
        }

        // Характеристики
        int sx = l + 184;
        int sy = t + 80;
        inset(c, sx - 3, sy - 3, r - 10, t + 114, CONTENT_ALT);
        c.drawText(this.textRenderer, Text.translatable("screen.basedefense.guard.armor_value", handler.getProp(PROP_ARMOR)),
                sx, sy, TEXT_BODY, false);
        c.drawText(this.textRenderer, Text.translatable("screen.basedefense.guard.damage",
                fmt(handler.getProp(PROP_DAMAGE) / 10.0f)), sx, sy + 11, TEXT_BODY, false);
        int squad = handler.getProp(PROP_SQUAD);
        Text squadText = squad > 0
                ? Text.translatable("screen.basedefense.guard.squad", SquadState.roman(squad - 1))
                : Text.translatable("screen.basedefense.guard.no_squad");
        c.drawText(this.textRenderer, squadText, sx, sy + 22, squad > 0 ? TEAL_LIGHT : TEXT_MUTED, false);

        // Чем дерётся
        int weapon = handler.getProp(PROP_WEAPON);
        Text wText = Text.translatable("screen.basedefense.guard.weapon_state." + weapon);
        c.drawText(this.textRenderer, wText, l + FOOD_X - 1, t + 111,
                weapon == 0 || weapon == 4 ? WARN : TEXT_MUTED, false);

        // Инвентарь игрока
        divider(c, l + 12, r - 12, t + INV_Y - 5);
        for (int i = 12; i < handler.slots.size(); i++) {
            Slot s = handler.slots.get(i);
            slot(c, l + s.x, t + s.y, false);
        }
    }

    private void label(DrawContext c, String key, int x, int y) {
        c.drawText(this.textRenderer, Text.translatable(key), x, y, TEXT_MUTED, false);
    }

    private static String fmt(float v) {
        return v == Math.floor(v) ? String.valueOf((int) v) : String.format(java.util.Locale.ROOT, "%.1f", v);
    }

    /** Живой портрет стражника (смотрит на мышку), имя над головой на время рисования спрятано. */
    private void drawPortrait(DrawContext c, int l, int t, int r, int b, int mouseX, int mouseY) {
        inset(c, l, t, r, b, 0x70000000);
        c.fillGradient(l, b - 34, r, b, 0x00000000, 0x40DCE6EA);
        int cx = (l + r) / 2;
        int feet = b - 10;
        c.fill(cx - 18, feet + 1, cx + 18, feet + 2, 0x60DCE6EA);
        c.fill(cx - 12, feet, cx + 12, feet + 3, 0x40DCE6EA);
        if (this.client == null || this.client.world == null) {
            return;
        }
        Entity entity = this.client.world.getEntityById(handler.getEntityId());
        if (!(entity instanceof LivingEntity living)) {
            return;
        }
        Text name = living.getCustomName();
        boolean visible = living.isCustomNameVisible();
        living.setCustomName(null);
        living.setCustomNameVisible(false);
        try {
            int size = 36;
            InventoryScreen.drawEntity(c, cx, feet, size, (float) (cx - mouseX), (float) (feet - size * 1.6f - mouseY),
                    living);
        } finally {
            living.setCustomName(name);
            living.setCustomNameVisible(visible);
        }
    }

    @Override
    protected void drawForeground(DrawContext context, int mouseX, int mouseY) {
        // Пусто: всё рисуем сами
    }
}

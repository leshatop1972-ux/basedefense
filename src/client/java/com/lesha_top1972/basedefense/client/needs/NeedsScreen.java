package com.lesha_top1972.basedefense.client.needs;

import com.lesha_top1972.basedefense.client.temperature.TemperatureHud;
import com.lesha_top1972.basedefense.entity.settler.SettlerEntity;
import com.lesha_top1972.basedefense.needs.ComfortLevel;
import com.lesha_top1972.basedefense.temperature.TempStatus;
import com.lesha_top1972.basedefense.temperature.Temperature;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.screen.ingame.InventoryScreen;
import net.minecraft.entity.Entity;
import net.minecraft.text.Text;

import java.util.Locale;

import static com.lesha_top1972.basedefense.client.screen.UiKit.*;

/**
 * Окно «Потребности» (вкладка справа от инвентаря): голод, сон (позже), комфорт со шкалой зон и эффектами,
 * температура (ощущаемая, воздух, комфортная в этой одежде, в помещении ли). entityId = −1 — свой игрок,
 * иначе — поселенец (данные из его сущности на клиенте).
 */
public class NeedsScreen extends Screen {

    private static final int W = 210;
    private static final int H = 166;
    private static final int BAR_L = 72;
    private static final int BAR_R = 168;

    private final int entityId;
    private int left;
    private int top;

    public NeedsScreen(int entityId) {
        super(Text.translatable("gui.basedefense.tab.needs"));
        this.entityId = entityId;
    }

    @Override
    protected void init() {
        left = (this.width - W) / 2;
        top = (this.height - H) / 2;
        NeedsTabs.add(this::addDrawableChild, left + W, top, true, () -> {
            if (entityId < 0) {
                if (this.client != null && this.client.player != null) {
                    this.client.setScreen(new InventoryScreen(this.client.player));
                }
            } else {
                NeedsTabs.openSettlerInventory(entityId);
            }
        }, () -> {
        });
    }

    @Override
    public boolean shouldPause() {
        return false;
    }

    @Override
    public void tick() {
        // поселенец ушёл далеко или погиб — закрываем
        if (entityId >= 0 && (settler() == null || this.client == null || this.client.player == null
                || this.client.player.squaredDistanceTo(settler()) > 10 * 10)) {
            this.close();
        }
    }

    private SettlerEntity settler() {
        if (this.client == null || this.client.world == null) {
            return null;
        }
        Entity e = this.client.world.getEntityById(entityId);
        return e instanceof SettlerEntity s && s.isAlive() ? s : null;
    }

    @Override
    public void render(DrawContext c, int mouseX, int mouseY, float delta) {
        this.renderBackground(c);
        if (this.client == null || this.client.player == null) {
            return;
        }
        SettlerEntity settler = entityId >= 0 ? settler() : null;
        if (entityId >= 0 && settler == null) {
            return;
        }
        Text name;
        int hunger;
        float comfort;
        int trend;
        float felt;
        TempStatus status;
        float cmin;
        float cmax;
        float enclosure;
        Float air = null;
        if (settler != null) {
            name = settler.getDisplayName();
            hunger = settler.getHunger();
            comfort = settler.getComfort();
            trend = settler.getComfortTrend();
            felt = settler.getTemperature();
            status = settler.getTempStatus();
            cmin = settler.getComfortMin();
            cmax = settler.getComfortMax();
            enclosure = settler.getEnclosure();
        } else {
            name = this.client.player.getDisplayName();
            hunger = this.client.player.getHungerManager().getFoodLevel();
            comfort = ClientNeeds.comfort;
            trend = ClientNeeds.trend;
            felt = ClientNeeds.felt;
            status = ClientNeeds.status;
            cmin = ClientNeeds.comfortMin;
            cmax = ClientNeeds.comfortMax;
            enclosure = ClientNeeds.enclosure;
            air = ClientNeeds.ambient;
        }

        int l = left;
        int t = top;
        int r = l + W;
        int b = t + H;
        panel(c, l, t, r, b);
        header(c, l + 4, t + 4, r - 4, 22);
        embossed(c, this.textRenderer, name, l + 10, t + 11, TEXT_DARK);
        pill(c, this.textRenderer, r - 12 - this.textRenderer.getWidth(this.title) - 8, t + 9, this.title, TEXT_BODY);

        // ---------- Шкалы ----------
        int y = t + 32;
        // Голод
        label(c, "needs.basedefense.hunger", l + 10, y);
        progress(c, l + BAR_L, y, l + BAR_R, y + 7, hunger / 20f, 0xFFE8B060, 0xFFA0602A);
        value(c, hunger + "/20", y, hunger <= 6 ? BAD : TEXT_BODY);
        y += 16;
        // Сон — позже
        label(c, "needs.basedefense.sleep", l + 10, y);
        inset(c, l + BAR_L, y, l + BAR_R, y + 7, CONTENT);
        value(c, Text.translatable("needs.basedefense.soon").getString(), y, TEXT_MUTED);
        y += 16;
        // Комфорт: зоны 0–15 / 15–35 / 35–75 / 75–100 и метка
        label(c, "needs.basedefense.comfort", l + 10, y);
        comfortBar(c, l + BAR_L, y, l + BAR_R, y + 7, comfort);
        String arrow = trend > 0 ? " ↑" : trend < 0 ? " ↓" : "";
        ComfortLevel level = ComfortLevel.of(comfort);
        value(c, Math.round(comfort) + arrow, y, levelColor(level));
        boolean overComfort = mouseX >= l + BAR_L && mouseX <= l + BAR_R && mouseY >= y - 2 && mouseY <= y + 9;
        y += 14;
        String lv = level.name().toLowerCase(Locale.ROOT);
        c.drawText(this.textRenderer, Text.translatable("needs.basedefense.level." + lv), l + 10, y, levelColor(level), false);
        y += 10;
        for (var line : this.textRenderer.wrapLines(Text.translatable("needs.basedefense.effect." + lv), W - 20)) {
            c.drawText(this.textRenderer, line, l + 10, y, TEXT_MUTED, false);
            y += 9;
        }

        // ---------- Температура ----------
        y = Math.max(y + 4, t + 112);
        divider(c, l + 10, r - 10, y - 4);
        TemperatureHud.draw(c, this.textRenderer, l + 10, y, felt, status);
        Text tLine = air != null
                ? Text.translatable("needs.basedefense.temp_felt_air", Math.round(felt), Math.round(air))
                : Text.translatable("needs.basedefense.temp_felt", Math.round(felt));
        c.drawText(this.textRenderer, tLine, l + 34, y + 1, TEXT_BODY, false);
        y += 12;
        c.drawText(this.textRenderer, Text.translatable("needs.basedefense.temp_comfort", Math.round(cmin), Math.round(cmax)),
                l + 10, y, TEXT_BODY, false);
        y += 11;
        String where = enclosure >= Temperature.INDOORS ? "indoors" : enclosure > 0.15f ? "shelter" : "outdoors";
        c.drawText(this.textRenderer, Text.translatable("needs.basedefense.place." + where), l + 10, y, TEXT_MUTED, false);
        c.drawText(this.textRenderer, Text.translatable("temperature.basedefense.status." + status.name().toLowerCase(Locale.ROOT)),
                r - 10 - this.textRenderer.getWidth(Text.translatable("temperature.basedefense.status."
                        + status.name().toLowerCase(Locale.ROOT))), y, TemperatureHud.color(status), false);

        super.render(c, mouseX, mouseY, delta);
        if (overComfort) {
            c.drawOrderedTooltip(this.textRenderer,
                    this.textRenderer.wrapLines(Text.translatable("needs.basedefense.comfort.tip"), 200), mouseX, mouseY);
        }
    }

    private void label(DrawContext c, String key, int x, int y) {
        c.drawText(this.textRenderer, Text.translatable(key), x, y, TEXT_BODY, false);
    }

    private void value(DrawContext c, String s, int y, int color) {
        c.drawText(this.textRenderer, s, left + BAR_R + 6, y, color, false);
    }

    private static void comfortBar(DrawContext c, int l, int t, int r, int b, float comfort) {
        int w = r - l;
        int x15 = l + Math.round(w * 0.15f);
        int x35 = l + Math.round(w * 0.35f);
        int x75 = l + Math.round(w * 0.75f);
        inset(c, l - 1, t - 1, r + 1, b + 1, CONTENT);
        c.fill(l, t, x15, b, 0x60C03028);
        c.fill(x15, t, x35, b, 0x60D08030);
        c.fill(x35, t, x75, b, 0x40FFFFFF);
        c.fill(x75, t, r, b, 0x6050C070);
        int fill = l + Math.round(w * Math.max(0, Math.min(100, comfort)) / 100f);
        c.fill(l, t + 2, fill, b - 2, levelColor(ComfortLevel.of(comfort)));
        c.fill(fill - 1, t - 1, fill + 1, b + 1, 0xFFFFFFFF);
    }

    static int levelColor(ComfortLevel l) {
        return switch (l) {
            case AWFUL -> BAD;
            case BAD -> WARN;
            case NORMAL -> TEXT_BODY;
            case GOOD -> GOOD;
        };
    }
}

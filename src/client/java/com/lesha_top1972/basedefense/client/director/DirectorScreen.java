package com.lesha_top1972.basedefense.client.director;

import com.lesha_top1972.basedefense.client.screen.UiKit;
import com.lesha_top1972.basedefense.director.DirectorEvent;
import com.lesha_top1972.basedefense.director.DirectorTables;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.text.OrderedText;
import net.minecraft.text.Text;
import net.minecraft.util.math.MathHelper;

import java.util.List;

/**
 * «Летопись» (кнопка в инвентаре): акт, шкала до босса (1000 очков), день выживания, событие сегодня
 * с описанием, усиление врагов на волнах и журнал прошлых дней. Стекло UiKit с лёгкими цветными акцентами:
 * цвет акта (рамка и цифра), цвет события (карточка), шкала от янтаря к красному.
 * Esc или клавиша инвентаря — назад в инвентарь.
 */
public class DirectorScreen extends Screen {

    private static final int W = 340;
    private static final int H = 204;
    private static final int HEADER = 20;
    private static final int PAD = 9;
    private static final int LEFT_W = 216;

    private final Screen parent;
    private int l, t, r, b;
    private int ticks;

    public DirectorScreen(Screen parent) {
        super(Text.translatable("gui.basedefense.director"));
        this.parent = parent;
    }

    @Override
    protected void init() {
        l = (width - W) / 2;
        t = (height - H) / 2;
        r = l + W;
        b = t + H;
    }

    @Override
    public void tick() {
        ticks++;
    }

    @Override
    public boolean shouldPause() {
        return false;
    }

    @Override
    public void close() {
        client.setScreen(parent);
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (client.options.inventoryKey.matchesKey(keyCode, scanCode)) {
            close();
            return true;
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    /** Цвет с заданной прозрачностью (0..255). */
    private static int alpha(int color, int a) {
        return (a << 24) | (color & 0xFFFFFF);
    }

    private static int mix(int c1, int c2, float k) {
        int r = (int) MathHelper.lerp(k, (c1 >> 16) & 0xFF, (c2 >> 16) & 0xFF);
        int g = (int) MathHelper.lerp(k, (c1 >> 8) & 0xFF, (c2 >> 8) & 0xFF);
        int bl = (int) MathHelper.lerp(k, c1 & 0xFF, c2 & 0xFF);
        return 0xFF000000 | (r << 16) | (g << 8) | bl;
    }

    @Override
    public void render(DrawContext c, int mouseX, int mouseY, float delta) {
        renderBackground(c);
        int actCol = DirectorClient.actColor(DirectorClient.act);
        UiKit.panel(c, l, t, r, b);
        UiKit.header(c, l, t, r, HEADER);
        c.fillGradient(l + 1, t + 1, r - 1, t + HEADER, alpha(actCol, 0x22), alpha(actCol, 0x00));   // лёгкий цветной отсвет шапки
        c.fill(l + 1, t, r - 1, t + 1, alpha(actCol, 0xA0));
        UiKit.diamond(c, l + PAD, t + 10, actCol);
        UiKit.embossed(c, textRenderer, Text.translatable("gui.basedefense.director"), l + PAD + 7, t + 6, UiKit.TEXT_DARK);
        Text dayText = Text.translatable("director.basedefense.screen.day", DirectorClient.day);
        UiKit.embossed(c, textRenderer, dayText, r - PAD - textRenderer.getWidth(dayText), t + 6, UiKit.TEXT_BODY);

        int x0 = l + PAD;
        int y = t + HEADER + 7;
        int x1 = l + LEFT_W;

        // ---------- Акт ----------
        UiKit.inset(c, x0, y, x0 + 44, y + 44);
        c.fillGradient(x0, y, x0 + 44, y + 44, alpha(actCol, 0x30), alpha(actCol, 0x08));
        UiKit.corners(c, x0, y, x0 + 44, y + 44, 5, alpha(actCol, 0xC0));
        String roman = DirectorTables.roman(DirectorClient.act);
        c.getMatrices().push();
        c.getMatrices().translate(x0 + 22, y + 9, 0);
        c.getMatrices().scale(2.5f, 2.5f, 1f);
        c.drawTextWithShadow(textRenderer, roman, -textRenderer.getWidth(roman) / 2, 0, mix(actCol, 0xFFFFFFFF, 0.35f));
        c.getMatrices().pop();
        c.drawCenteredTextWithShadow(textRenderer, Text.translatable("director.basedefense.screen.act"),
                x0 + 22, y + 33, UiKit.TEXT_MUTED);

        // ---------- Шкала ----------
        int sx0 = x0 + 52;
        int sx1 = x1;
        boolean full = DirectorClient.points >= DirectorTables.SCALE_MAX;
        float frac = MathHelper.clamp(DirectorClient.points / (float) DirectorTables.SCALE_MAX, 0f, 1f);
        UiKit.embossed(c, textRenderer, Text.translatable("director.basedefense.screen.scale"), sx0, y + 1, UiKit.TEXT_BODY);
        Text value = Text.literal(DirectorClient.points + " / " + DirectorTables.SCALE_MAX);
        UiKit.embossed(c, textRenderer, value, sx1 - textRenderer.getWidth(value), y + 1,
                full ? UiKit.BAD : mix(0xFFE3AE55, 0xFFFFFFFF, 0.4f));
        int by = y + 13;
        int barTop = full ? (ticks / 10 % 2 == 0 ? 0xFFFF6A5C : 0xFFC8473C) : mix(0xFFF0C062, 0xFFE8603F, frac);
        int barBottom = full ? 0xFF8A2A22 : mix(0xFF9C6E2A, 0xFF8E2E22, frac);
        UiKit.progress(c, sx0, by, sx1, by + 9, frac, barTop, barBottom);
        for (int k = 1; k < 4; k++) {                 // деления 250 / 500 / 750
            int mx = sx0 + (sx1 - sx0) * k / 4;
            c.fill(mx, by + 1, mx + 1, by + 8, 0x70000000);
        }
        Text boss = full ? Text.translatable("director.basedefense.screen.boss_next")
                : Text.translatable("director.basedefense.screen.boss_at");
        c.drawTextWithShadow(textRenderer, boss, sx0, by + 13, full ? UiKit.BAD : UiKit.TEXT_MUTED);
        c.getMatrices().push();                       // откуда берутся очки — мелко
        c.getMatrices().translate(sx0, by + 25, 0);
        c.getMatrices().scale(0.75f, 0.75f, 1f);
        c.drawTextWithShadow(textRenderer, Text.translatable("director.basedefense.screen.sources"), 0, 0, UiKit.TEXT_MUTED);
        c.getMatrices().pop();
        y += 52;

        // ---------- Событие сегодня ----------
        UiKit.divider(c, x0, x1, y);
        y += 6;
        UiKit.embossed(c, textRenderer, Text.translatable("director.basedefense.screen.today"), x0, y, UiKit.TEXT_MUTED);
        y += 12;
        DirectorEvent e = DirectorClient.today;
        int evCol = DirectorClient.color(e);
        UiKit.card(c, x0, y, x1, y + 64, UiKit.CARD, false);
        c.fillGradient(x0 + 1, y + 1, x1 - 1, y + 63, alpha(evCol, 0x1C), alpha(evCol, 0x06));
        c.fill(x0, y, x0 + 2, y + 64, alpha(evCol, 0xD0));                 // цветная полоска слева
        UiKit.slot(c, x0 + 7, y + 6, false);
        c.fill(x0 + 7, y + 6, x0 + 23, y + 22, alpha(evCol, 0x26));
        c.drawItem(DirectorClient.icon(e), x0 + 7, y + 6);
        UiKit.embossed(c, textRenderer, Text.translatable(e.key()), x0 + 29, y + 6, evCol);
        UiKit.pill(c, textRenderer, x0 + 29, y + 17, DirectorClient.whenText(e), alpha(evCol, 0x30), UiKit.TEXT_BODY);
        List<OrderedText> lines = textRenderer.wrapLines(Text.translatable(e.key() + ".desc"), x1 - x0 - 14);
        int ly = y + 31;
        for (int i = 0; i < lines.size() && i < 3; i++) {
            c.drawTextWithShadow(textRenderer, lines.get(i), x0 + 7, ly, UiKit.TEXT_BODY);
            ly += 10;
        }
        y += 70;

        // ---------- Усиление врагов ----------
        float hp = DirectorTables.hpBonus(DirectorClient.act);
        Text buff = hp <= 0f ? Text.translatable("director.basedefense.screen.buff_none")
                : Text.translatable("director.basedefense.screen.buff", Math.round(hp * 100),
                Math.round(DirectorTables.damageBonus(DirectorClient.act) * 100));
        UiKit.diamond(c, x0, y + 4, hp <= 0f ? UiKit.TEXT_MUTED : UiKit.BAD);
        c.drawTextWithShadow(textRenderer, buff, x0 + 7, y, hp <= 0f ? UiKit.TEXT_MUTED : 0xFFE89A8E);

        // ---------- Журнал ----------
        int jx0 = x1 + 10;
        int jx1 = r - PAD;
        int jy = t + HEADER + 7;
        UiKit.embossed(c, textRenderer, Text.translatable("director.basedefense.screen.journal"), jx0, jy + 1, UiKit.TEXT_BODY);
        jy += 13;
        UiKit.inset(c, jx0, jy, jx1, b - PAD);
        int row = jy + 2;
        int i = 0;
        for (DirectorClient.Entry h : DirectorClient.HISTORY) {
            if (row + 15 > b - PAD - 1) {
                break;
            }
            int hc = DirectorClient.color(h.event());
            boolean isToday = h.day() == DirectorClient.todayDay;
            if (isToday) {
                c.fill(jx0 + 1, row - 1, jx1 - 1, row + 14, alpha(hc, 0x22));
                c.fill(jx0, row - 1, jx0 + 1, row + 14, alpha(hc, 0xC0));
            } else if (i % 2 == 1) {
                c.fill(jx0 + 1, row - 1, jx1 - 1, row + 14, 0x10FFFFFF);
            }
            c.getMatrices().push();
            c.getMatrices().translate(jx0 + 3, row, 0);
            c.getMatrices().scale(0.75f, 0.75f, 1f);
            c.drawItem(DirectorClient.icon(h.event()), 0, 0);
            c.getMatrices().pop();
            String d = Integer.toString(h.day());
            c.drawTextWithShadow(textRenderer, d, jx0 + 17, row + 3, UiKit.TEXT_MUTED);
            int nx = jx0 + 31;
            int maxW = jx1 - nx - 3;
            String name = Text.translatable(h.event().key()).getString();
            if (textRenderer.getWidth(name) > maxW) {
                name = textRenderer.trimToWidth(name, maxW - textRenderer.getWidth("…")) + "…";
            }
            c.drawTextWithShadow(textRenderer, name, nx, row + 3, hc);
            row += 15;
            i++;
        }
        if (DirectorClient.HISTORY.isEmpty()) {
            c.drawTextWithShadow(textRenderer, Text.translatable("director.basedefense.screen.journal_empty"),
                    jx0 + 4, row + 3, UiKit.TEXT_MUTED);
        }
        super.render(c, mouseX, mouseY, delta);
    }
}

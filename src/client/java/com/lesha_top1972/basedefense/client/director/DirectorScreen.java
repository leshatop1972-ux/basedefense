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
 * «Летопись» (из вкладки в инвентаре): акт, шкала до босса (1000 очков), день выживания, событие сегодня
 * с описанием, усиление врагов на волнах и журнал прошлых дней. Esc или клавиша инвентаря — назад в инвентарь.
 */
public class DirectorScreen extends Screen {

    private static final int W = 330;
    private static final int H = 200;
    private static final int HEADER = 20;
    private static final int PAD = 9;
    private static final int LEFT_W = 206;

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

    @Override
    public void render(DrawContext c, int mouseX, int mouseY, float delta) {
        renderBackground(c);
        UiKit.panel(c, l, t, r, b);
        UiKit.header(c, l, t, r, HEADER);
        UiKit.embossed(c, textRenderer, Text.translatable("gui.basedefense.director"), l + PAD, t + 6, UiKit.TEXT_DARK);
        Text dayText = Text.translatable("director.basedefense.screen.day", DirectorClient.day);
        UiKit.embossed(c, textRenderer, dayText, r - PAD - textRenderer.getWidth(dayText), t + 6, UiKit.TEXT_BODY);

        int x0 = l + PAD;
        int y = t + HEADER + 7;
        int x1 = l + LEFT_W;

        // ---------- Акт ----------
        UiKit.inset(c, x0, y, x0 + 44, y + 44);
        String roman = DirectorTables.roman(DirectorClient.act);
        c.getMatrices().push();
        c.getMatrices().translate(x0 + 22, y + 9, 0);
        c.getMatrices().scale(2.5f, 2.5f, 1f);
        c.drawTextWithShadow(textRenderer, roman, -textRenderer.getWidth(roman) / 2, 0, UiKit.TEXT_DARK);
        c.getMatrices().pop();
        c.drawCenteredTextWithShadow(textRenderer, Text.translatable("director.basedefense.screen.act"),
                x0 + 22, y + 33, UiKit.TEXT_MUTED);

        // ---------- Шкала ----------
        int sx0 = x0 + 52;
        int sx1 = x1;
        boolean full = DirectorClient.points >= DirectorTables.SCALE_MAX;
        UiKit.embossed(c, textRenderer, Text.translatable("director.basedefense.screen.scale"), sx0, y + 1, UiKit.TEXT_BODY);
        Text value = Text.literal(DirectorClient.points + " / " + DirectorTables.SCALE_MAX);
        UiKit.embossed(c, textRenderer, value, sx1 - textRenderer.getWidth(value), y + 1,
                full ? UiKit.BAD : UiKit.TEXT_DARK);
        int by = y + 13;
        float frac = MathHelper.clamp(DirectorClient.points / (float) DirectorTables.SCALE_MAX, 0f, 1f);
        int top = full ? (ticks / 10 % 2 == 0 ? 0xFFFF6A5C : 0xFFB8443A) : 0xFFE0B25A;
        UiKit.progress(c, sx0, by, sx1, by + 9, frac, top, full ? 0xFF8A2A22 : 0xFF9C6E2A);
        for (int k = 1; k < 4; k++) {                 // деления 250 / 500 / 750
            int mx = sx0 + (sx1 - sx0) * k / 4;
            c.fill(mx, by + 1, mx + 1, by + 8, 0x60000000);
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
        UiKit.card(c, x0, y, x1, y + 64, UiKit.CARD, false);
        UiKit.slot(c, x0 + 6, y + 6, false);
        c.drawItem(DirectorClient.icon(e), x0 + 6, y + 6);
        UiKit.embossed(c, textRenderer, Text.translatable(e.key()), x0 + 28, y + 6, DirectorClient.color(e));
        UiKit.pill(c, textRenderer, x0 + 28, y + 17, DirectorClient.whenText(e), UiKit.TEXT_BODY);
        List<OrderedText> lines = textRenderer.wrapLines(Text.translatable(e.key() + ".desc"), x1 - x0 - 12);
        int ly = y + 31;
        for (int i = 0; i < lines.size() && i < 3; i++) {
            c.drawTextWithShadow(textRenderer, lines.get(i), x0 + 6, ly, UiKit.TEXT_BODY);
            ly += 10;
        }
        y += 70;

        // ---------- Усиление врагов ----------
        float hp = DirectorTables.hpBonus(DirectorClient.act);
        Text buff = hp <= 0f ? Text.translatable("director.basedefense.screen.buff_none")
                : Text.translatable("director.basedefense.screen.buff", Math.round(hp * 100),
                Math.round(DirectorTables.damageBonus(DirectorClient.act) * 100));
        c.drawTextWithShadow(textRenderer, buff, x0, y, UiKit.TEXT_MUTED);

        // ---------- Журнал ----------
        int jx0 = x1 + 10;
        int jx1 = r - PAD;
        int jy = t + HEADER + 7;
        UiKit.embossed(c, textRenderer, Text.translatable("director.basedefense.screen.journal"), jx0, jy + 1, UiKit.TEXT_BODY);
        jy += 13;
        UiKit.inset(c, jx0, jy, jx1, b - PAD);
        int row = jy + 3;
        for (DirectorClient.Entry h : DirectorClient.HISTORY) {
            if (row + 14 > b - PAD - 2) {
                break;
            }
            c.getMatrices().push();
            c.getMatrices().translate(jx0 + 3, row, 0);
            c.getMatrices().scale(0.75f, 0.75f, 1f);
            c.drawItem(DirectorClient.icon(h.event()), 0, 0);
            c.getMatrices().pop();
            String d = Integer.toString(h.day());
            c.drawTextWithShadow(textRenderer, d, jx0 + 17, row + 3, UiKit.TEXT_MUTED);
            Text name = Text.translatable(h.event().key());
            int maxW = jx1 - (jx0 + 31) - 3;
            OrderedText cut = textRenderer.wrapLines(name, maxW).get(0);
            c.drawTextWithShadow(textRenderer, cut, jx0 + 31, row + 3, DirectorClient.color(h.event()));
            row += 15;
        }
        if (DirectorClient.HISTORY.isEmpty()) {
            c.drawTextWithShadow(textRenderer, Text.translatable("director.basedefense.screen.journal_empty"),
                    jx0 + 4, row + 3, UiKit.TEXT_MUTED);
        }
        super.render(c, mouseX, mouseY, delta);
    }
}

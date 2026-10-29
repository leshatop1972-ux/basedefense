package com.lesha_top1972.basedefense.client.director;

import com.lesha_top1972.basedefense.client.screen.UiKit;
import com.lesha_top1972.basedefense.director.DirectorEvent;
import com.lesha_top1972.basedefense.director.DirectorTables;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.text.OrderedText;
import net.minecraft.text.Text;
import net.minecraft.util.math.MathHelper;

import java.util.List;

/**
 * Утренняя заставка во весь экран: «День N · Акт I», значок и название события, когда оно будет, описание.
 * Рисуется поверх игры (HUD) — играть она не мешает: появляется за 0.75 с, держится и гаснет за 1.5 с.
 */
public final class MorningOverlay {

    private static final int FADE_IN = 15;
    private static final int HOLD = 150;
    private static final int FADE_OUT = 30;
    private static final int TOTAL = FADE_IN + HOLD + FADE_OUT;

    private static DirectorEvent event;
    private static long eventDay;
    private static int eventAct;
    private static int age = -1;

    private MorningOverlay() {
    }

    static void start(DirectorEvent e, long day, int act) {
        event = e;
        eventDay = day;
        eventAct = act;
        age = 0;
    }

    static void stop() {
        age = -1;
    }

    static void tick(MinecraftClient client) {
        if (age >= 0 && ++age > TOTAL) {
            age = -1;
        }
    }

    private static float alpha(float t) {
        if (t < FADE_IN) {
            return t / FADE_IN;
        }
        if (t > FADE_IN + HOLD) {
            return Math.max(0f, 1f - (t - FADE_IN - HOLD) / FADE_OUT);
        }
        return 1f;
    }

    /** Цвет с прозрачностью; совсем прозрачный текст не рисуем (шрифт считает альфу 0 непрозрачной). */
    private static int withAlpha(int rgb, float a) {
        return (Math.max(5, (int) (a * 255f)) << 24) | (rgb & 0xFFFFFF);
    }

    static void render(DrawContext c, float tickDelta) {
        MinecraftClient client = MinecraftClient.getInstance();
        if (age < 0 || event == null || client.options.hudHidden) {
            return;
        }
        float t = age + tickDelta;
        float a = alpha(t);
        a = a * a * (3f - 2f * a);
        if (a <= 0.02f) {
            return;
        }
        TextRenderer tr = client.textRenderer;
        int w = c.getScaledWindowWidth();
        int h = c.getScaledWindowHeight();
        int cx = w / 2;

        // затемнение: темнее к центру по высоте, лёгкий цветной отсвет события снизу
        int dark = (int) (a * 0xB0);
        c.fillGradient(0, 0, w, h / 2, (dark / 2) << 24, dark << 24);
        c.fillGradient(0, h / 2, w, h, dark << 24, (dark / 2) << 24);
        int tint = DirectorClient.color(event) & 0xFFFFFF;
        c.fillGradient(0, h - h / 4, w, h, tint, ((int) (a * 0x30) << 24) | tint);
        if (a < 0.15f) {
            return;
        }

        int y = h / 2 - 62;
        // «ДЕНЬ 12 · АКТ I»
        Text top = Text.translatable("director.basedefense.morning.day", eventDay, DirectorTables.roman(eventAct));
        c.drawCenteredTextWithShadow(tr, top, cx, y, withAlpha(UiKit.TEXT_MUTED, a));
        y += 12;
        // тонкая линия
        int lw = (int) (70 * a);
        c.fill(cx - lw, y, cx + lw, y + 1, withAlpha(0xFFFFFF, a * 0.4f));
        y += 8;

        // значок ×2 (предметы не умеют прозрачность — показываем, когда заставка почти проявилась)
        if (a > 0.6f) {
            c.getMatrices().push();
            c.getMatrices().translate(cx - 16, y, 0);
            c.getMatrices().scale(2f, 2f, 1f);
            c.drawItem(DirectorClient.icon(event), 0, 0);
            c.getMatrices().pop();
        }
        y += 38;

        // название крупно; у боссовой волны — пульсирует
        int col = DirectorClient.color(event);
        if (event.kind == DirectorEvent.Kind.BOSS) {
            float p = 0.75f + 0.25f * MathHelper.sin(t * 0.25f);
            col = 0xFF000000 | ((int) (0xFF * p) << 16) | ((int) (0x3B * p) << 8) | (int) (0x3B * p);
        }
        Text name = Text.translatable(event.key());
        float scale = 2.5f;
        c.getMatrices().push();
        c.getMatrices().translate(cx, y, 0);
        c.getMatrices().scale(scale, scale, 1f);
        c.drawTextWithShadow(tr, name, -tr.getWidth(name) / 2, 0, withAlpha(col, a));
        c.getMatrices().pop();
        y += (int) (9 * scale) + 6;

        c.drawCenteredTextWithShadow(tr, DirectorClient.whenText(event), cx, y, withAlpha(UiKit.TEXT_BODY, a));
        y += 16;

        List<OrderedText> lines = tr.wrapLines(Text.translatable(event.key() + ".desc"), Math.min(300, w - 40));
        for (OrderedText line : lines) {
            c.drawTextWithShadow(tr, line, cx - tr.getWidth(line) / 2, y, withAlpha(UiKit.TEXT_BODY, a * 0.9f));
            y += 11;
        }
        y += 8;
        c.drawCenteredTextWithShadow(tr, Text.translatable("director.basedefense.morning.hint"), cx, y,
                withAlpha(UiKit.TEXT_MUTED, a * 0.8f));
    }
}

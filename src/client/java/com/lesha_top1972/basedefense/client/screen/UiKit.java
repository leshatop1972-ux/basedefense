package com.lesha_top1972.basedefense.client.screen;

import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.text.Text;

/**
 * Общий стиль всех меню мода: «пергамент в деревянной рамке» с латунными заклёпками.
 * Здесь палитра и готовые элементы: панель, заголовок, углубление, карточка, кнопка,
 * полоска прогресса, разделитель, ячейка слота, значок-«пилюля».
 * Меняешь цвет здесь — он меняется во всех меню сразу.
 */
public final class UiKit {

    // ---------- Палитра ----------
    public static final int FRAME_OUT    = 0xFF3B2B1B;   // тёмный край рамки
    public static final int WOOD         = 0xFF7A5A36;   // дерево рамки
    public static final int WOOD_LIGHT   = 0xFFA57D4C;
    public static final int WOOD_DARK    = 0xFF5A4027;
    public static final int PARCH_TOP    = 0xFFF2E5C6;   // пергамент (градиент сверху вниз)
    public static final int PARCH_BOTTOM = 0xFFE3CEA2;
    public static final int HEADER_TOP   = 0xFFE6D09E;
    public static final int HEADER_BOTTOM = 0xFFD3B57D;
    public static final int CONTENT      = 0xFFF7EEDA;   // светлая область внутри
    public static final int CONTENT_ALT  = 0xFFF1E4C6;
    public static final int INSET_DARK   = 0xFF9C8058;
    public static final int INSET_LIGHT  = 0xFFFFF9EA;
    public static final int RULE         = 0xFFB89B6E;
    public static final int GOLD         = 0xFFC99A3E;
    public static final int GOLD_LIGHT   = 0xFFF3DC9C;
    public static final int GOLD_DARK    = 0xFF8A6524;

    public static final int TEXT_DARK    = 0xFF4A3622;
    public static final int TEXT_BODY    = 0xFF6A5236;
    public static final int TEXT_MUTED   = 0xFF92785A;
    public static final int TEXT_EMBOSS  = 0x99FFFFFF;   // светлая «тень» для вдавленного текста
    public static final int GOOD         = 0xFF4F7F2E;
    public static final int GOOD_LIGHT   = 0xFF8BBF5A;
    public static final int BAD          = 0xFFB03A2E;
    public static final int ACCENT       = 0xFFB0782F;

    private UiKit() {
    }

    // =====================================================================
    // Панель окна
    // =====================================================================

    /** Окно: мягкая тень, деревянная рамка с заклёпками, пергамент с градиентом и тонкая золотая линия. */
    public static void panel(DrawContext c, int l, int t, int r, int b) {
        // Тень (два слоя — мягче)
        c.fill(l + 3, t + 4, r + 4, b + 4, 0x22000000);
        c.fill(l + 1, t + 2, r + 2, b + 2, 0x33000000);
        // Рамка
        c.fill(l, t, r, b, FRAME_OUT);
        c.fill(l + 1, t + 1, r - 1, b - 1, WOOD);
        c.fill(l + 1, t + 1, r - 1, t + 2, WOOD_LIGHT);
        c.fill(l + 1, t + 1, l + 2, b - 1, WOOD_LIGHT);
        c.fill(l + 2, b - 2, r - 1, b - 1, WOOD_DARK);
        c.fill(r - 2, t + 2, r - 1, b - 1, WOOD_DARK);
        // Пергамент
        c.fill(l + 3, t + 3, r - 3, b - 3, FRAME_OUT);
        c.fillGradient(l + 4, t + 4, r - 4, b - 4, PARCH_TOP, PARCH_BOTTOM);
        // Тонкая золотая линия по краю пергамента
        outline(c, l + 6, t + 6, r - 6, b - 6, 0x40B08A4A);
        // Заклёпки по углам
        rivet(c, l + 1, t + 1);
        rivet(c, r - 4, t + 1);
        rivet(c, l + 1, b - 4);
        rivet(c, r - 4, b - 4);
    }

    /** Латунная заклёпка 3×3 с бликом. */
    public static void rivet(DrawContext c, int x, int y) {
        c.fill(x, y, x + 3, y + 3, GOLD);
        c.fill(x, y, x + 1, y + 1, GOLD_LIGHT);
        c.fill(x + 2, y + 2, x + 3, y + 3, GOLD_DARK);
    }

    /** Полоса заголовка внутри панели (от l до r, высота h) с двойной линией снизу. */
    public static void header(DrawContext c, int l, int t, int r, int h) {
        c.fillGradient(l, t, r, t + h, HEADER_TOP, HEADER_BOTTOM);
        c.fill(l, t, r, t + 1, 0x66FFFFFF);
        c.fill(l, t + h, r, t + h + 1, INSET_DARK);
        c.fill(l, t + h + 1, r, t + h + 2, INSET_LIGHT);
        diamond(c, l + 3, t + h, GOLD);
        diamond(c, r - 4, t + h, GOLD);
    }

    /** Ромбик 5×5 (центр в x+1, y). */
    public static void diamond(DrawContext c, int x, int y, int color) {
        c.fill(x + 1, y - 2, x + 2, y + 3, color);
        c.fill(x, y - 1, x + 3, y + 2, color);
        c.fill(x + 1, y - 1, x + 2, y, GOLD_LIGHT);
    }

    // =====================================================================
    // Области и карточки
    // =====================================================================

    /** Вдавленная область: сверху-слева тёмный край, снизу-справа светлый, внутри — светлый фон. */
    public static void inset(DrawContext c, int l, int t, int r, int b) {
        inset(c, l, t, r, b, CONTENT);
    }

    public static void inset(DrawContext c, int l, int t, int r, int b, int fill) {
        c.fill(l - 1, t - 1, r + 1, t, INSET_DARK);
        c.fill(l - 1, t, l, b + 1, INSET_DARK);
        c.fill(l, b, r + 1, b + 1, INSET_LIGHT);
        c.fill(r, t, r + 1, b, INSET_LIGHT);
        c.fill(l, t, r, b, fill);
        c.fill(l, t, r, t + 1, 0x1F000000);   // внутренняя тень сверху
        c.fill(l, t, l + 1, b, 0x14000000);
    }

    /** Выпуклая карточка: светлый верх, тень снизу; при наведении — золотая рамка. */
    public static void card(DrawContext c, int l, int t, int r, int b, int fill, boolean hover) {
        c.fill(l + 1, b, r + 1, b + 1, 0x26000000);
        c.fill(l, t, r, b, hover ? GOLD : 0xFFCDB487);
        c.fill(l + 1, t + 1, r - 1, b - 1, fill);
        c.fill(l + 1, t + 1, r - 1, t + 2, 0x80FFFFFF);
        c.fill(l + 1, b - 2, r - 1, b - 1, 0x14000000);
    }

    /** Рамка в 1 пиксель. */
    public static void outline(DrawContext c, int l, int t, int r, int b, int color) {
        c.fill(l, t, r, t + 1, color);
        c.fill(l, b - 1, r, b, color);
        c.fill(l, t + 1, l + 1, b - 1, color);
        c.fill(r - 1, t + 1, r, b - 1, color);
    }

    /** Разделитель: двойная линия с ромбиком посередине. */
    public static void divider(DrawContext c, int l, int r, int y) {
        c.fill(l, y, r, y + 1, RULE);
        c.fill(l, y + 1, r, y + 2, INSET_LIGHT);
        diamond(c, (l + r) / 2 - 1, y, GOLD);
    }

    /** Ячейка слота как в обычных меню Minecraft, но в тёплых тонах. */
    public static void slot(DrawContext c, int x, int y, boolean highlight) {
        c.fill(x - 1, y - 1, x + 17, y, highlight ? GOLD_DARK : INSET_DARK);
        c.fill(x - 1, y, x, y + 17, highlight ? GOLD_DARK : INSET_DARK);
        c.fill(x, y + 16, x + 17, y + 17, INSET_LIGHT);
        c.fill(x + 16, y, x + 17, y + 16, INSET_LIGHT);
        c.fill(x, y, x + 16, y + 16, highlight ? 0xFFF3E2B4 : 0xFFDCCBA4);
    }

    // =====================================================================
    // Кнопка, полоска, значок
    // =====================================================================

    /** Кнопка с объёмом. state: 0 — обычная, 1 — наведена, 2 — выключена, 3 — «готово» (зелёная). */
    public static void button(DrawContext c, TextRenderer tr, int l, int t, int r, int b, Text label, int state) {
        int edge, top, bottom, textColor;
        switch (state) {
            case 1 -> { edge = GOLD_DARK; top = 0xFFF0D392; bottom = 0xFFD5A961; textColor = TEXT_DARK; }
            case 2 -> { edge = 0xFFAA9C80; top = 0xFFE0D5BC; bottom = 0xFFCFC2A6; textColor = TEXT_MUTED; }
            case 3 -> { edge = 0xFF4E7A30; top = 0xFFDDEBBE; bottom = 0xFFC2D99A; textColor = GOOD; }
            default -> { edge = 0xFF6E4E28; top = 0xFFE3C484; bottom = 0xFFC49A56; textColor = TEXT_DARK; }
        }
        c.fill(l + 1, b, r + 1, b + 1, 0x33000000);
        c.fill(l, t, r, b, edge);
        c.fillGradient(l + 1, t + 1, r - 1, b - 1, top, bottom);
        c.fill(l + 1, t + 1, r - 1, t + 2, 0x8CFFFFFF);
        c.fill(l + 1, b - 2, r - 1, b - 1, 0x2A000000);
        int tx = (l + r - tr.getWidth(label)) / 2;
        int ty = (t + b - 8) / 2;
        embossed(c, tr, label, tx, ty, textColor);
    }

    /** Полоска прогресса во вдавленном жёлобе, с бликом и делениями через 10 %. */
    public static void progress(DrawContext c, int l, int t, int r, int b, float fraction, int top, int bottom) {
        inset(c, l, t, r, b, 0xFFE8DCC0);
        int w = Math.round((r - l) * Math.max(0.0f, Math.min(1.0f, fraction)));
        if (w > 0) {
            c.fillGradient(l, t, l + w, b, top, bottom);
            c.fill(l, t, l + w, t + 1, 0x66FFFFFF);
        }
        for (int i = 1; i < 10; i++) {
            int x = l + (r - l) * i / 10;
            c.fill(x, b - 2, x + 1, b, 0x33000000);
        }
    }

    /** Значок-«пилюля» с текстом (например, уровень). @return ширина */
    public static int pill(DrawContext c, TextRenderer tr, int x, int y, Text text, int fill, int textColor) {
        int w = tr.getWidth(text) + 8;
        c.fill(x + 1, y, x + w - 1, y + 11, fill);
        c.fill(x, y + 1, x + w, y + 10, fill);
        c.fill(x + 1, y + 1, x + w - 1, y + 2, 0x55FFFFFF);
        c.drawText(tr, text, x + 4, y + 2, textColor, false);
        return w;
    }

    /** «Вдавленный» текст: светлая тень на пиксель ниже. */
    public static void embossed(DrawContext c, TextRenderer tr, Text text, int x, int y, int color) {
        c.drawText(tr, text, x, y + 1, TEXT_EMBOSS, false);
        c.drawText(tr, text, x, y, color, false);
    }

    public static void embossedCentered(DrawContext c, TextRenderer tr, Text text, int cx, int y, int color) {
        embossed(c, tr, text, cx - tr.getWidth(text) / 2, y, color);
    }
}

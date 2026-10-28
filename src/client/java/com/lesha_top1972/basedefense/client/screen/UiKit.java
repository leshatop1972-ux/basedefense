package com.lesha_top1972.basedefense.client.screen;

import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.text.Text;

/**
 * Общий стиль всех меню мода: «стекло» в духе MineColonies — полупрозрачные серо-чёрные панели,
 * сквозь которые виден мир, тонкие светлые рамки и блики. Цветной акцент (приглушённая бирюза)
 * — только у выбранного/важного, всё остальное — оттенки белого и серого.
 * Здесь палитра и готовые элементы: панель, заголовок, углубление, карточка, кнопка,
 * полоска прогресса, разделитель, ячейка слота, значок-«пилюля», замок, галочка.
 * Меняешь цвет здесь — он меняется во всех меню сразу.
 *
 * Цвета в формате 0xAARRGGBB: AA — непрозрачность (FF — сплошной, 00 — невидимый).
 */
public final class UiKit {

    // ---------- Палитра: стекло и рамки ----------
    public static final int GLASS_TOP    = 0xB42A2E33;   // фон окна: полупрозрачное стекло (градиент сверху вниз)
    public static final int GLASS_BOTTOM = 0xC4181A1E;
    public static final int FRAME_OUT    = 0x90000000;   // тень-край снаружи
    public static final int FRAME        = 0x50FFFFFF;   // светлая стеклянная кромка
    public static final int FRAME_LIGHT  = 0x80FFFFFF;   // блик по верхней кромке
    public static final int HEADER_TOP   = 0x28FFFFFF;
    public static final int HEADER_BOTTOM = 0x0AFFFFFF;
    public static final int CONTENT      = 0x50000000;   // утопленная область (чуть темнее стекла)
    public static final int CONTENT_ALT  = 0x38000000;
    public static final int CARD         = 0x1EFFFFFF;   // карточка — светлее стекла
    public static final int CARD_HOVER   = 0x34FFFFFF;
    public static final int INSET_DARK   = 0x60000000;
    public static final int INSET_LIGHT  = 0x26FFFFFF;
    public static final int RULE         = 0x30FFFFFF;

    // ---------- Акцент: приглушённая бирюза (только выбранное и важное) ----------
    public static final int TEAL         = 0xFF7FC4BC;
    public static final int TEAL_LIGHT   = 0xFFD4EEEA;
    public static final int TEAL_DARK    = 0xFF4E7F7A;
    public static final int TEAL_GLOW    = 0x30FFFFFF;   // мягкое свечение — белое
    public static final int TEAL_FAINT   = 0x18FFFFFF;
    // Белые блики стекла
    public static final int SHINE        = 0x60FFFFFF;
    public static final int SHINE_SOFT   = 0x20FFFFFF;

    // Старые имена (раньше — золото и дерево) ведут на новые цвета, чтобы весь код работал как раньше
    public static final int GOLD         = TEAL;
    public static final int GOLD_LIGHT   = TEAL_LIGHT;
    public static final int GOLD_DARK    = TEAL_DARK;
    public static final int WOOD         = FRAME;
    public static final int WOOD_LIGHT   = FRAME_LIGHT;
    public static final int WOOD_DARK    = 0xFF262C32;
    public static final int PARCH_TOP    = GLASS_TOP;
    public static final int PARCH_BOTTOM = GLASS_BOTTOM;

    // ---------- Текст ----------
    public static final int TEXT_DARK    = 0xFFFFFFFF;   // главный текст (белый на стекле)
    public static final int TEXT_BODY    = 0xFFD6DADE;
    public static final int TEXT_MUTED   = 0xFF9EA6AD;
    public static final int TEXT_EMBOSS  = 0x90000000;   // тень под текстом
    public static final int GOOD         = 0xFF5AD68C;
    public static final int GOOD_LIGHT   = 0xFF9CF2BD;
    public static final int BAD          = 0xFFFF6A5C;
    public static final int WARN         = 0xFFFFC857;
    public static final int ACCENT       = TEAL;

    private UiKit() {
    }

    // =====================================================================
    // Панель окна
    // =====================================================================

    /** Окно: мягкая тень, полупрозрачное стекло, светлая кромка с бликом сверху. */
    public static void panel(DrawContext c, int l, int t, int r, int b) {
        // Тень
        c.fill(l + 2, t + 3, r + 3, b + 4, 0x30000000);
        // Стекло со скруглёнными (срезанными) углами
        c.fillGradient(l + 1, t, r - 1, b, GLASS_TOP, GLASS_BOTTOM);
        c.fillGradient(l, t + 1, l + 1, b - 1, GLASS_TOP, GLASS_BOTTOM);
        c.fillGradient(r - 1, t + 1, r, b - 1, GLASS_TOP, GLASS_BOTTOM);
        // Кромка: светлая, сверху ярче (блик), снизу темнее
        c.fill(l + 1, t, r - 1, t + 1, FRAME_LIGHT);
        c.fill(l, t + 1, l + 1, b - 1, FRAME);
        c.fill(r - 1, t + 1, r, b - 1, 0x30FFFFFF);
        c.fill(l + 1, b - 1, r - 1, b, 0x24FFFFFF);
        // Блик в верхней части стекла
        c.fillGradient(l + 1, t + 1, r - 1, t + 14, 0x14FFFFFF, 0x00FFFFFF);
    }

    /** Г-образные уголки длиной len по четырём углам прямоугольника. */
    public static void corners(DrawContext c, int l, int t, int r, int b, int len, int color) {
        c.fill(l, t, l + len, t + 2, color);
        c.fill(l, t, l + 2, t + len, color);
        c.fill(r - len, t, r, t + 2, color);
        c.fill(r - 2, t, r, t + len, color);
        c.fill(l, b - 2, l + len, b, color);
        c.fill(l, b - len, l + 2, b, color);
        c.fill(r - len, b - 2, r, b, color);
        c.fill(r - 2, b - len, r, b, color);
    }

    /** Маленькая светлая точка 3×3 (раньше — латунная заклёпка). */
    public static void rivet(DrawContext c, int x, int y) {
        c.fill(x, y, x + 3, y + 3, 0x40FFFFFF);
        c.fill(x + 1, y + 1, x + 2, y + 2, SHINE);
    }

    /** Полоса заголовка: чуть светлее стекла, снизу тонкая светлая линия, гаснущая к краям. */
    public static void header(DrawContext c, int l, int t, int r, int h) {
        c.fillGradient(l, t, r, t + h, HEADER_TOP, HEADER_BOTTOM);
        int mid = (l + r) / 2;
        int half = (r - l) / 2;
        c.fill(l + 4, t + h, r - 4, t + h + 1, 0x18FFFFFF);
        c.fill(mid - half * 2 / 3, t + h, mid + half * 2 / 3, t + h + 1, 0x30FFFFFF);
        c.fill(mid - half / 3, t + h, mid + half / 3, t + h + 1, 0x50FFFFFF);
    }

    /** Ромбик 5×5 (центр в x+1, y). */
    public static void diamond(DrawContext c, int x, int y, int color) {
        c.fill(x + 1, y - 2, x + 2, y + 3, color);
        c.fill(x, y - 1, x + 3, y + 2, color);
    }

    // =====================================================================
    // Области и карточки
    // =====================================================================

    /** Утопленная область: тёмный полупрозрачный фон, чёрный край сверху-слева, слабый блик снизу-справа. */
    public static void inset(DrawContext c, int l, int t, int r, int b) {
        inset(c, l, t, r, b, CONTENT);
    }

    public static void inset(DrawContext c, int l, int t, int r, int b, int fill) {
        c.fill(l - 1, t - 1, r + 1, t, INSET_DARK);
        c.fill(l - 1, t, l, b + 1, INSET_DARK);
        c.fill(l, b, r + 1, b + 1, INSET_LIGHT);
        c.fill(r, t, r + 1, b, INSET_LIGHT);
        c.fill(l, t, r, b, fill);
        c.fill(l, t, r, t + 1, 0x20000000);   // внутренняя тень сверху
    }

    /**
     * Карточка: стеклянная плашка с серой рамкой; при наведении — бирюзовая рамка, свечение и полоска слева.
     * fill — цвет фона (обычно CARD / CARD_HOVER или свой полупрозрачный).
     */
    public static void card(DrawContext c, int l, int t, int r, int b, int fill, boolean hover) {
        c.fill(l, t, r, b, fill);
        outline(c, l, t, r, b, hover ? 0x90FFFFFF : 0x30FFFFFF);
        c.fill(l + 1, t + 1, r - 1, t + 2, hover ? 0x30FFFFFF : 0x14FFFFFF);
    }

    /** Рамка в 1 пиксель. */
    public static void outline(DrawContext c, int l, int t, int r, int b, int color) {
        c.fill(l, t, r, t + 1, color);
        c.fill(l, b - 1, r, b, color);
        c.fill(l, t + 1, l + 1, b - 1, color);
        c.fill(r - 1, t + 1, r, b - 1, color);
    }

    /** Разделитель: тонкая светлая линия, гаснущая к краям. */
    public static void divider(DrawContext c, int l, int r, int y) {
        int mid = (l + r) / 2;
        int q = (r - l) / 4;
        c.fill(l, y, r, y + 1, 0x14FFFFFF);
        c.fill(mid - 2 * q, y, mid + 2 * q, y + 1, 0x24FFFFFF);
        c.fill(mid - q, y, mid + q, y + 1, 0x3CFFFFFF);
    }

    /** Ячейка слота: тёмная утопленная клетка; подсвеченная — с бирюзовой рамкой. */
    public static void slot(DrawContext c, int x, int y, boolean highlight) {
        c.fill(x - 1, y - 1, x + 17, y + 17, highlight ? 0x60FFFFFF : 0x28FFFFFF);
        c.fill(x, y, x + 16, y + 16, highlight ? 0x50303A40 : 0x60000000);
    }

    // =====================================================================
    // Кнопка, полоска, значки
    // =====================================================================

    /** Кнопка-стекло. state: 0 — обычная, 1 — наведена, 2 — выключена, 3 — «готово» (зелёная). */
    public static void button(DrawContext c, TextRenderer tr, int l, int t, int r, int b, Text label, int state) {
        int edge, top, bottom, textColor;
        switch (state) {
            case 1 -> { edge = 0xC0FFFFFF; top = 0x50FFFFFF; bottom = 0x30FFFFFF; textColor = 0xFFFFFFFF; }
            case 2 -> { edge = 0x24FFFFFF; top = 0x14FFFFFF; bottom = 0x0AFFFFFF; textColor = TEXT_MUTED; }
            case 3 -> { edge = 0xA05AD68C; top = 0x405AD68C; bottom = 0x205AD68C; textColor = GOOD_LIGHT; }
            default -> { edge = 0x60FFFFFF; top = 0x30FFFFFF; bottom = 0x18FFFFFF; textColor = TEXT_DARK; }
        }
        c.fillGradient(l + 1, t + 1, r - 1, b - 1, top, bottom);
        outline(c, l, t, r, b, edge);
        c.fill(l + 1, t + 1, r - 1, t + 2, 0x28FFFFFF);
        int tx = (l + r - tr.getWidth(label)) / 2;
        int ty = (t + b - 8) / 2;
        embossed(c, tr, label, tx, ty, textColor);
    }

    /** Полоска прогресса в тёмном жёлобе, с бликом, ярким краем и делениями через 10 %. */
    public static void progress(DrawContext c, int l, int t, int r, int b, float fraction, int top, int bottom) {
        inset(c, l, t, r, b, 0x70000000);
        int w = Math.round((r - l) * Math.max(0.0f, Math.min(1.0f, fraction)));
        if (w > 0) {
            c.fillGradient(l, t, l + w, b, top, bottom);
            c.fill(l, t, l + w, t + 1, 0x55FFFFFF);
            c.fill(l + w - 1, t, l + w, b, 0xAAFFFFFF);
        }
        for (int i = 1; i < 10; i++) {
            int x = l + (r - l) * i / 10;
            c.fill(x, b - 2, x + 1, b, 0x44000000);
        }
    }

    /** Значок-«пилюля» со своим цветом фона. @return ширина */
    public static int pill(DrawContext c, TextRenderer tr, int x, int y, Text text, int fill, int textColor) {
        int w = tr.getWidth(text) + 8;
        c.fill(x + 1, y, x + w - 1, y + 11, fill);
        c.fill(x, y + 1, x + w, y + 10, fill);
        c.fill(x + 1, y + 1, x + w - 1, y + 2, 0x22FFFFFF);
        c.drawText(tr, text, x + 4, y + 2, textColor, false);
        return w;
    }

    /** Стандартная «пилюля»: полупрозрачная, с бирюзовой рамкой. @return ширина */
    public static int pill(DrawContext c, TextRenderer tr, int x, int y, Text text, int textColor) {
        int w = tr.getWidth(text) + 8;
        outline(c, x, y, x + w, y + 11, 0x50FFFFFF);
        c.fill(x + 1, y + 1, x + w - 1, y + 10, 0x40000000);
        c.drawText(tr, text, x + 4, y + 2, textColor, false);
        return w;
    }

    /** Текст с тёмной тенью (на стекле читается чётче). */
    public static void embossed(DrawContext c, TextRenderer tr, Text text, int x, int y, int color) {
        c.drawText(tr, text, x + 1, y + 1, TEXT_EMBOSS, false);
        c.drawText(tr, text, x, y, color, false);
    }

    public static void embossedCentered(DrawContext c, TextRenderer tr, Text text, int cx, int y, int color) {
        embossed(c, tr, text, cx - tr.getWidth(text) / 2, y, color);
    }

    /** Замочек 8×9. */
    public static void lock(DrawContext c, int x, int y) {
        int metal = 0xFF8B97A2;
        c.fill(x + 2, y, x + 6, y + 1, metal);
        c.fill(x + 1, y + 1, x + 2, y + 4, metal);
        c.fill(x + 6, y + 1, x + 7, y + 4, metal);
        c.fill(x, y + 4, x + 8, y + 9, metal);
        c.fill(x + 1, y + 5, x + 7, y + 8, 0xFF3A424A);
        c.fill(x + 3, y + 6, x + 5, y + 7, metal);
    }

    /** Галочка в кружке 8×8. */
    public static void check(DrawContext c, int x, int y, int color) {
        c.fill(x + 1, y, x + 7, y + 8, color);
        c.fill(x, y + 1, x + 8, y + 7, color);
        int w = 0xFFFFFFFF;
        c.fill(x + 1, y + 4, x + 2, y + 5, w);
        c.fill(x + 2, y + 5, x + 3, y + 6, w);
        c.fill(x + 3, y + 4, x + 4, y + 5, w);
        c.fill(x + 4, y + 3, x + 5, y + 4, w);
        c.fill(x + 5, y + 2, x + 6, y + 3, w);
    }
}

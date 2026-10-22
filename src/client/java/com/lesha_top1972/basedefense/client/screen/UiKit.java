package com.lesha_top1972.basedefense.client.screen;

import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.text.Text;

/**
 * Общий стиль всех меню мода: «тёмное стекло» — полупрозрачные чёрно-серые панели,
 * тонкие серые рамки и бирюзовые акценты (уголки, подсветка, линии), в духе меню MineColonies.
 * Здесь палитра и готовые элементы: панель, заголовок, углубление, карточка, кнопка,
 * полоска прогресса, разделитель, ячейка слота, значок-«пилюля», замок, галочка.
 * Меняешь цвет здесь — он меняется во всех меню сразу.
 *
 * Цвета в формате 0xAARRGGBB: AA — непрозрачность (FF — сплошной, 00 — невидимый).
 */
public final class UiKit {

    // ---------- Палитра: стекло и рамки ----------
    public static final int GLASS_TOP    = 0xE6181C21;   // фон окна (градиент сверху вниз)
    public static final int GLASS_BOTTOM = 0xEE0D1013;
    public static final int FRAME_OUT    = 0xFF050607;   // самый внешний тёмный край
    public static final int FRAME        = 0xFF3B434C;   // серая рамка
    public static final int FRAME_LIGHT  = 0xFF5A646F;   // светлая грань рамки
    public static final int HEADER_TOP   = 0xD0262D35;
    public static final int HEADER_BOTTOM = 0xD01A1F25;
    public static final int CONTENT      = 0x8C000000;   // утопленная область (полупрозрачная чернота)
    public static final int CONTENT_ALT  = 0x66060809;
    public static final int CARD         = 0xB01F252C;   // карточка
    public static final int CARD_HOVER   = 0xC82A333C;
    public static final int INSET_DARK   = 0xFF030405;
    public static final int INSET_LIGHT  = 0x553F4A55;
    public static final int RULE         = 0xFF2C333A;

    // ---------- Бирюза (акцент) ----------
    public static final int TEAL         = 0xFF33C9BE;
    public static final int TEAL_LIGHT   = 0xFF9BF5EC;
    public static final int TEAL_DARK    = 0xFF167A73;
    public static final int TEAL_GLOW    = 0x4033C9BE;   // мягкое свечение
    public static final int TEAL_FAINT   = 0x1F33C9BE;

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
    public static final int TEXT_DARK    = 0xFFECF2F4;   // главный текст (светлый на тёмном)
    public static final int TEXT_BODY    = 0xFFB9C3CB;
    public static final int TEXT_MUTED   = 0xFF78848F;
    public static final int TEXT_EMBOSS  = 0xAA000000;   // тень под текстом
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

    /** Окно: тень, тёмное стекло с градиентом, серая рамка с фаской и бирюзовые уголки. */
    public static void panel(DrawContext c, int l, int t, int r, int b) {
        // Мягкая тень
        c.fill(l + 4, t + 5, r + 5, b + 5, 0x30000000);
        c.fill(l + 2, t + 3, r + 3, b + 3, 0x40000000);
        // Рамка: чёрный край, серая полоса, светлая грань сверху-слева
        outline(c, l, t, r, b, FRAME_OUT);
        outline(c, l + 1, t + 1, r - 1, b - 1, FRAME);
        c.fill(l + 1, t + 1, r - 1, t + 2, FRAME_LIGHT);
        c.fill(l + 1, t + 2, l + 2, b - 1, 0xFF4B545E);
        outline(c, l + 2, t + 2, r - 2, b - 2, FRAME_OUT);
        // Стекло
        c.fillGradient(l + 3, t + 3, r - 3, b - 3, GLASS_TOP, GLASS_BOTTOM);
        // Еле заметные горизонтальные «сканлайны» — фактура стекла
        for (int y = t + 5; y < b - 4; y += 3) {
            c.fill(l + 3, y, r - 3, y + 1, 0x08FFFFFF);
        }
        // Бирюзовые уголки
        corners(c, l, t, r, b, 7, TEAL);
        // Бирюзовая «подсветка» у верхнего края стекла
        c.fill(l + 10, t + 3, r - 10, t + 4, TEAL_GLOW);
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

    /** Маленький бирюзовый «огонёк» 3×3 (раньше — латунная заклёпка). */
    public static void rivet(DrawContext c, int x, int y) {
        c.fill(x, y, x + 3, y + 3, TEAL_DARK);
        c.fill(x + 1, y + 1, x + 2, y + 2, TEAL_LIGHT);
    }

    /** Полоса заголовка внутри панели (от l до r, высота h): тёмная, снизу бирюзовая линия со свечением. */
    public static void header(DrawContext c, int l, int t, int r, int h) {
        c.fillGradient(l, t, r, t + h, HEADER_TOP, HEADER_BOTTOM);
        c.fill(l, t, r, t + 1, 0x14FFFFFF);
        // Бирюзовая линия: яркая в центре, темнее к краям
        int mid = (l + r) / 2;
        int half = (r - l) / 2;
        c.fill(l, t + h, r, t + h + 1, TEAL_DARK);
        c.fill(mid - half * 2 / 3, t + h, mid + half * 2 / 3, t + h + 1, TEAL);
        c.fill(mid - half / 3, t + h, mid + half / 3, t + h + 1, TEAL_LIGHT);
        c.fill(l, t + h + 1, r, t + h + 3, TEAL_FAINT);
        diamond(c, l + 3, t + h, TEAL);
        diamond(c, r - 4, t + h, TEAL);
    }

    /** Ромбик 5×5 (центр в x+1, y). */
    public static void diamond(DrawContext c, int x, int y, int color) {
        c.fill(x + 1, y - 2, x + 2, y + 3, color);
        c.fill(x, y - 1, x + 3, y + 2, color);
        c.fill(x + 1, y - 1, x + 2, y, 0xCCFFFFFF);
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
        c.fill(l, t, r, t + 1, 0x30000000);   // внутренняя тень сверху
    }

    /**
     * Карточка: стеклянная плашка с серой рамкой; при наведении — бирюзовая рамка, свечение и полоска слева.
     * fill — цвет фона (обычно CARD / CARD_HOVER или свой полупрозрачный).
     */
    public static void card(DrawContext c, int l, int t, int r, int b, int fill, boolean hover) {
        c.fill(l + 1, b, r + 1, b + 1, 0x50000000);
        if (hover) {
            outline(c, l - 1, t - 1, r + 1, b + 1, TEAL_GLOW);
        }
        outline(c, l, t, r, b, hover ? TEAL : 0xFF323A42);
        c.fill(l + 1, t + 1, r - 1, b - 1, fill);
        c.fill(l + 1, t + 1, r - 1, t + 2, 0x18FFFFFF);
        if (hover) {
            c.fill(l + 1, t + 1, l + 3, b - 1, TEAL);
        }
    }

    /** Рамка в 1 пиксель. */
    public static void outline(DrawContext c, int l, int t, int r, int b, int color) {
        c.fill(l, t, r, t + 1, color);
        c.fill(l, b - 1, r, b, color);
        c.fill(l, t + 1, l + 1, b - 1, color);
        c.fill(r - 1, t + 1, r, b - 1, color);
    }

    /** Разделитель: тонкая серая линия, бирюзовая в середине, с ромбиком. */
    public static void divider(DrawContext c, int l, int r, int y) {
        c.fill(l, y, r, y + 1, RULE);
        int mid = (l + r) / 2;
        int q = (r - l) / 4;
        c.fill(mid - q, y, mid + q, y + 1, TEAL_DARK);
        c.fill(l, y + 1, r, y + 2, 0x10FFFFFF);
        diamond(c, mid - 1, y, TEAL);
    }

    /** Ячейка слота: тёмная утопленная клетка; подсвеченная — с бирюзовой рамкой. */
    public static void slot(DrawContext c, int x, int y, boolean highlight) {
        c.fill(x - 1, y - 1, x + 17, y, highlight ? TEAL : INSET_DARK);
        c.fill(x - 1, y, x, y + 17, highlight ? TEAL : INSET_DARK);
        c.fill(x, y + 16, x + 17, y + 17, highlight ? TEAL_DARK : 0xFF414A54);
        c.fill(x + 16, y, x + 17, y + 16, highlight ? TEAL_DARK : 0xFF414A54);
        c.fill(x, y, x + 16, y + 16, highlight ? 0xB0123A38 : 0xB0111418);
    }

    // =====================================================================
    // Кнопка, полоска, значки
    // =====================================================================

    /** Кнопка. state: 0 — обычная, 1 — наведена, 2 — выключена, 3 — «готово» (зелёная). */
    public static void button(DrawContext c, TextRenderer tr, int l, int t, int r, int b, Text label, int state) {
        int edge, top, bottom, textColor;
        switch (state) {
            case 1 -> { edge = TEAL_LIGHT; top = 0xE0237A74; bottom = 0xE0175651; textColor = 0xFFFFFFFF; }
            case 2 -> { edge = 0xFF33393F; top = 0xA0262A2F; bottom = 0xA01C1F23; textColor = TEXT_MUTED; }
            case 3 -> { edge = 0xFF3D9A62; top = 0xC0234A33; bottom = 0xC0183625; textColor = GOOD_LIGHT; }
            default -> { edge = TEAL; top = 0xD0193F3D; bottom = 0xD0102B2A; textColor = TEXT_DARK; }
        }
        c.fill(l + 1, b, r + 1, b + 1, 0x60000000);
        if (state == 1) {
            outline(c, l - 1, t - 1, r + 1, b + 1, TEAL_GLOW);
        }
        outline(c, l, t, r, b, edge);
        c.fillGradient(l + 1, t + 1, r - 1, b - 1, top, bottom);
        c.fill(l + 1, t + 1, r - 1, t + 2, 0x22FFFFFF);
        int tx = (l + r - tr.getWidth(label)) / 2;
        int ty = (t + b - 8) / 2;
        embossed(c, tr, label, tx, ty, textColor);
    }

    /** Полоска прогресса в тёмном жёлобе, с бликом, ярким краем и делениями через 10 %. */
    public static void progress(DrawContext c, int l, int t, int r, int b, float fraction, int top, int bottom) {
        inset(c, l, t, r, b, 0xA0000000);
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
        outline(c, x, y, x + w, y + 11, TEAL_DARK);
        c.fill(x + 1, y + 1, x + w - 1, y + 10, 0xA0102624);
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

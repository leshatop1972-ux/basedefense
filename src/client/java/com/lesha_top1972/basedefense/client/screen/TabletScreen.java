package com.lesha_top1972.basedefense.client.screen;

import com.lesha_top1972.basedefense.Basedefense;
import com.lesha_top1972.basedefense.client.render.StructurePreview;
import com.lesha_top1972.basedefense.item.BuildingTabletItem;
import com.lesha_top1972.basedefense.item.ModItems;
import com.lesha_top1972.basedefense.screen.TabletScreenHandler;
import com.lesha_top1972.basedefense.structure.HouseDesign;
import com.lesha_top1972.basedefense.structure.HouseType;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.ingame.HandledScreen;
import net.minecraft.client.sound.PositionedSoundInstance;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.registry.entry.RegistryEntry;
import net.minecraft.sound.SoundEvent;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.OrderedText;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import net.minecraft.util.Util;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

import static com.lesha_top1972.basedefense.client.screen.UiKit.*;

/**
 * Меню строительного планшета («тёмное стекло» с бирюзой, в духе MineColonies).
 * Слева — список зданий: значок работы, название и выбранный дизайн.
 * Справа — объёмный предпросмотр здания в выбранном дизайне (вращается, можно крутить мышкой),
 * описание, главные материалы и кнопки «Дизайн» и «Выбрать для постройки».
 * Кнопка «Дизайн» открывает окно с 4 дизайнами: у каждого свой предпросмотр, название и цена.
 */
public class TabletScreen extends HandledScreen<TabletScreenHandler> {

    // ---------- Размеры ----------
    private static final int PAD = 10;
    private static final int HEADER_H = 22;
    private static final int TOP = 34;             // верх списка и панели (от верха окна)
    private static final int LIST_W = 150;
    private static final int ROW_H = 22;
    private static final int DETAIL_W = 232;
    private static final int PREVIEW_H = 112;
    private static final int BUTTON_H = 18;
    private static final int DESIGN_BTN_W = 78;
    private static final int MATS = 8;

    // Окно дизайнов
    private static final int CARD_W = 92;
    private static final int CARD_H = 152;
    private static final int CARD_GAP = 6;
    private static final int CARD_PREVIEW_H = 74;

    private static final int ROWS = HouseType.values().length;

    private HouseType focused = HouseType.BUILDER;
    /** Выбранный дизайн каждого здания (берётся из планшета, меняется в окне дизайнов). */
    private final Map<HouseType, HouseDesign> designs = new EnumMap<>(HouseType.class);
    /** Открыто окно дизайнов. */
    private boolean designWindow;

    // Вращение предпросмотра
    private float dragYaw;
    private boolean dragging;
    private long lastClickTime;
    @Nullable
    private HouseType lastClicked;

    public TabletScreen(TabletScreenHandler handler, PlayerInventory inventory, Text title) {
        super(handler, inventory, title);
        this.backgroundWidth = PAD * 2 + LIST_W + 8 + DETAIL_W;
        this.backgroundHeight = TOP + ROWS * ROW_H + 20;
    }

    @Override
    protected void init() {
        super.init();
        ItemStack tablet = tablet();
        for (HouseType type : HouseType.values()) {
            designs.put(type, BuildingTabletItem.getRememberedDesign(tablet, type));
        }
        HouseType current = BuildingTabletItem.getSelected(tablet);
        if (current != null) {
            focused = current;
        }
    }

    private ItemStack tablet() {
        MinecraftClient mc = MinecraftClient.getInstance();
        return mc.player == null ? ItemStack.EMPTY : BuildingTabletItem.findInHands(mc.player);
    }

    @Nullable
    private HouseType currentSelection() {
        return BuildingTabletItem.getSelected(tablet());
    }

    private HouseDesign designOf(HouseType type) {
        return designs.getOrDefault(type, HouseDesign.OAK);
    }

    private static Identifier icon(HouseType type) {
        return new Identifier(Basedefense.MOD_ID, "textures/gui/tablet/" + type.getId() + ".png");
    }

    /** Медленное вращение + поворот мышкой. */
    private float yaw(float offset) {
        return -30.0f + dragYaw + offset + (dragging ? 0 : (Util.getMeasuringTimeMs() % 36000L) / 1000.0f * 10.0f);
    }

    // =====================================================================
    // Отрисовка
    // =====================================================================

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        this.renderBackground(context);
        super.render(context, mouseX, mouseY, delta);

        context.getMatrices().push();
        context.getMatrices().translate(0, 0, 500);
        if (designWindow) {
            HouseDesign hover = getCardAt(mouseX, mouseY);
            if (hover != null) {
                List<OrderedText> lines = new ArrayList<>();
                lines.add(hover.getName().copy().styled(s -> s.withColor(hover.getColor() & 0xFFFFFF)).asOrderedText());
                lines.addAll(this.textRenderer.wrapLines(hover.getDescription(), 200));
                context.drawOrderedTooltip(this.textRenderer, lines, mouseX, mouseY);
            }
        } else {
            Item mat = getMaterialAt(mouseX, mouseY);
            if (mat != null) {
                List<Text> lines = new ArrayList<>();
                lines.add(mat.getName());
                lines.add(Text.translatable("screen.basedefense.tablet.tip.count",
                        focused.getRequirements(designOf(focused)).get(mat)).styled(s -> s.withColor(TEXT_BODY & 0xFFFFFF)));
                context.drawTooltip(this.textRenderer, lines, mouseX, mouseY);
            }
        }
        context.getMatrices().pop();
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
        Text counter = Text.translatable("screen.basedefense.tablet.count", ROWS);
        pill(context, this.textRenderer, right - 12 - this.textRenderer.getWidth(counter) - 8, top + 9, counter, TEAL_LIGHT);

        // Список зданий
        int listL = left + PAD;
        int listT = top + TOP;
        inset(context, listL - 3, listT - 3, listL + LIST_W + 3, listT + ROWS * ROW_H + 1, CONTENT);
        HouseType selected = currentSelection();
        HouseType hoveredRow = designWindow ? null : getRowAt(mouseX, mouseY);
        for (HouseType type : HouseType.values()) {
            drawRow(context, type, listL, rowY(type), type == hoveredRow, type == selected);
        }

        drawDetail(context, mouseX, mouseY, selected);

        // Нижняя строка
        Text hint;
        int hintColor;
        if (!handler.hasBuilder() && focused != HouseType.BUILDER) {
            hint = Text.translatable("screen.basedefense.tablet.no_builder");
            hintColor = WARN;
        } else {
            hint = Text.translatable("screen.basedefense.tablet.footer");
            hintColor = TEXT_MUTED;
        }
        embossedCentered(context, this.textRenderer, hint, (left + right) / 2, bottom - 15, hintColor);

        if (designWindow) {
            drawDesignWindow(context, mouseX, mouseY);
        }
    }

    /** Строка списка: значок в «гнезде», название и дизайн. */
    private void drawRow(DrawContext c, HouseType type, int rl, int rt, boolean hover, boolean selected) {
        boolean isFocused = type == focused;
        int rr = rl + LIST_W;
        int rb = rt + ROW_H - 2;
        HouseDesign design = designOf(type);

        if (isFocused) {
            c.fillGradient(rl, rt, rr, rb, 0x70DCE6EA, 0x307C8A92);
            c.fill(rl, rt, rl + 2, rb, TEAL_LIGHT);
            outline(c, rl, rt, rr, rb, 0x80DCE6EA);
        } else if (hover) {
            c.fill(rl, rt, rr, rb, 0x28FFFFFF);
            c.fill(rl, rt, rl + 2, rb, 0x80FFFFFF);
        } else {
            c.fill(rl, rt, rr, rb, 0x30222830);
        }

        // Значок: тёмное гнездо с бирюзовой подсветкой
        int ix = rl + 4;
        int iy = rt + 2;
        c.fill(ix, iy, ix + 19, iy + 19, isFocused ? 0x50000000 : 0x50000000);
        outline(c, ix, iy, ix + 19, iy + 19, isFocused ? 0x90FFFFFF : 0x30FFFFFF);
        c.drawTexture(icon(type), ix + 2, iy + 2, 0, 0, 16, 16, 16, 16);

        int tx = rl + 28;
        c.drawText(this.textRenderer, type.getName(), tx, rt + 3, isFocused ? 0xFFFFFFFF : TEXT_DARK, true);
        // Цветная точка и название дизайна
        c.fill(tx, rt + 15, tx + 4, rt + 19, design.getColor());
        c.drawText(this.textRenderer, design.getName(), tx + 7, rt + 13, TEXT_MUTED, false);

        if (selected) {
            check(c, rr - 12, rt + 7, GOOD);
        }
    }

    /** Правая панель: предпросмотр, описание, материалы, кнопки. */
    private void drawDetail(DrawContext c, int mouseX, int mouseY, @Nullable HouseType selected) {
        int dl = detailLeft();
        int dt = this.y + TOP - 3;
        int dr = dl + DETAIL_W;
        int db = detailBottom();
        HouseDesign design = designOf(focused);

        // Предпросмотр: тёмная «сцена» с бирюзовым полом
        int pt = dt;
        int pb = dt + PREVIEW_H;
        inset(c, dl, pt, dr, pb, 0x60000000);
        c.fillGradient(dl, pb - 40, dr, pb, 0x00000000, 0x40DCE6EA);
        for (int gx = dl + 8; gx < dr - 4; gx += 12) {          // сетка-«чертёж»
            c.fill(gx, pt + 1, gx + 1, pb, 0x0CDCE6EA);
        }
        for (int gy = pt + 8; gy < pb - 2; gy += 12) {
            c.fill(dl + 1, gy, dr, gy + 1, 0x0CDCE6EA);
        }
        outline(c, dl - 1, pt - 1, dr + 1, pb + 1, 0x30FFFFFF);
        if (!designWindow) {
            StructurePreview.draw(c, focused.getPlan(design), dl + 2, pt + 14, dr - 2, pb - 2, yaw(0), 28.0f);
        }
        // Подписи поверх сцены
        c.getMatrices().push();
        c.getMatrices().translate(0, 0, 400);
        embossed(c, this.textRenderer, focused.getName(), dl + 6, pt + 5, 0xFFFFFFFF);
        Text dName = design.getName();
        int pw = this.textRenderer.getWidth(dName) + 8;
        pill(c, this.textRenderer, dr - 6 - pw, pt + 4, dName, 0xC0101418, design.getColor());
        if (focused == selected && designOf(focused) == BuildingTabletItem.getSelectedDesign(tablet())) {
            pill(c, this.textRenderer, dl + 6, pb - 15, Text.translatable("screen.basedefense.tablet.status.selected"),
                    GOOD_LIGHT);
        } else {
            c.drawText(this.textRenderer, Text.translatable("screen.basedefense.tablet.drag"), dl + 6, pb - 12,
                    0x8078848F, false);
        }
        c.getMatrices().pop();

        // Описание
        int textY = pb + 5;
        List<OrderedText> desc = this.textRenderer.wrapLines(
                Text.translatable("research.basedefense." + focused.getId() + ".desc"), DETAIL_W - 4);
        for (int i = 0; i < Math.min(2, desc.size()); i++) {
            OrderedText line = desc.get(i);
            c.drawText(this.textRenderer, line, dl + 2, textY, TEXT_BODY, false);
            textY += 10;
        }
        if (desc.size() > 2) {
            c.drawText(this.textRenderer, "…", dr - 8, textY - 10, TEXT_MUTED, false);
        }

        // Материалы
        int matLabelY = matTop() - 11;
        Text matLabel = focused.isSelfBuilt() ? Text.translatable("screen.basedefense.tablet.materials_free")
                : Text.translatable("screen.basedefense.tablet.materials_n", focused.getCostlyBlocks(design));
        c.drawText(this.textRenderer, matLabel, dl + 2, matLabelY, TEXT_MUTED, false);
        if (!focused.isSelfBuilt()) {
            List<Map.Entry<Item, Integer>> mats = topMaterials(focused, design);
            for (int i = 0; i < MATS; i++) {
                int sx = matX(i);
                int sy = matTop();
                slot(c, sx, sy, false);
                if (i < mats.size()) {
                    ItemStack stack = new ItemStack(mats.get(i).getKey());
                    c.drawItem(stack, sx, sy);
                    c.drawItemInSlot(this.textRenderer, stack, sx, sy, compact(mats.get(i).getValue()));
                }
            }
            int more = mats.size() - MATS;
            if (more > 0) {
                c.drawText(this.textRenderer, Text.translatable("screen.basedefense.tablet.more", more),
                        matX(MATS - 1) + 21, matTop() + 5, TEXT_MUTED, false);
            }
        }

        // Кнопки: «Дизайн» и «Выбрать для постройки»
        int bt = db - BUTTON_H;
        boolean hoverDesign = !designWindow && isOverDesignButton(mouseX, mouseY);
        button(c, this.textRenderer, dl, bt, dl + DESIGN_BTN_W, db,
                Text.translatable("screen.basedefense.tablet.button.design"), hoverDesign ? 1 : 0);
        c.fill(dl + 5, bt + 7, dl + 9, bt + 11, design.getColor());   // цвет текущего дизайна
        int bl = dl + DESIGN_BTN_W + 4;
        boolean chosen = focused == selected && design == BuildingTabletItem.getSelectedDesign(tablet());
        if (chosen) {
            button(c, this.textRenderer, bl, bt, dr, db, Text.translatable("screen.basedefense.tablet.button.chosen"), 3);
        } else {
            boolean hover = !designWindow && isOverSelectButton(mouseX, mouseY);
            if (!hover && (Util.getMeasuringTimeMs() / 600) % 2 == 0) {
                outline(c, bl - 1, bt - 1, dr + 1, db + 1, TEAL_FAINT);
            }
            button(c, this.textRenderer, bl, bt, dr, db, Text.translatable("screen.basedefense.tablet.button.select"),
                    hover ? 1 : 0);
        }
    }

    /** Окно выбора дизайна: затемнение и 4 карточки. */
    private void drawDesignWindow(DrawContext c, int mouseX, int mouseY) {
        c.getMatrices().push();
        c.getMatrices().translate(0, 0, 300);
        c.fill(0, 0, this.width, this.height, 0x80000000);
        int wl = windowLeft(), wt = windowTop(), wr = wl + windowWidth(), wb = wt + windowHeight();
        panel(c, wl, wt, wr, wb);
        header(c, wl + 4, wt + 4, wr - 4, HEADER_H);
        c.drawTexture(icon(focused), wl + 10, wt + 7, 0, 0, 16, 16, 16, 16);
        embossed(c, this.textRenderer, Text.translatable("screen.basedefense.tablet.design_title", focused.getName()),
                wl + 30, wt + 11, TEXT_DARK);
        boolean closeHover = isOverClose(mouseX, mouseY);
        c.drawText(this.textRenderer, "×", wr - 14, wt + 10, closeHover ? BAD : TEXT_BODY, false);
        c.getMatrices().pop();

        int base = focused.getCostlyBlocks(HouseDesign.OAK);
        HouseDesign hovered = getCardAt(mouseX, mouseY);
        for (HouseDesign d : HouseDesign.values()) {
            int cl = cardX(d);
            int ct = cardY();
            int cr = cl + CARD_W;
            int cb = ct + CARD_H;
            boolean sel = d == designOf(focused);
            boolean hov = d == hovered;

            c.getMatrices().push();
            c.getMatrices().translate(0, 0, 300);
            // Карточка: рамка, полоска цвета дизайна сверху
            if (hov || sel) {
                outline(c, cl - 2, ct - 2, cr + 2, cb + 2, TEAL_GLOW);
            }
            c.fill(cl, ct, cr, cb, hov ? CARD_HOVER : CARD);
            outline(c, cl, ct, cr, cb, sel ? TEAL : (hov ? 0x90FFFFFF : 0x30FFFFFF));
            c.fill(cl + 1, ct + 1, cr - 1, ct + 3, d.getColor());
            // Сцена предпросмотра
            int pl = cl + 4, pt = ct + 6, pr = cr - 4, pb = ct + 6 + CARD_PREVIEW_H;
            c.fill(pl, pt, pr, pb, 0x70000000);
            c.fillGradient(pl, pb - 26, pr, pb, 0x00000000, 0x40DCE6EA);
            outline(c, pl - 1, pt - 1, pr + 1, pb + 1, 0x30FFFFFF);
            c.getMatrices().pop();

            c.getMatrices().push();
            c.getMatrices().translate(0, 0, 300);
            StructurePreview.draw(c, focused.getPlan(d), pl + 1, pt + 1, pr - 1, pb - 1, yaw(d.ordinal() * 20.0f), 26.0f);
            c.getMatrices().pop();

            c.getMatrices().push();
            c.getMatrices().translate(0, 0, 700);
            // Название (до двух строк)
            int ty = pb + 5;
            for (OrderedText line : this.textRenderer.wrapLines(d.getName(), CARD_W - 8)) {
                if (ty > pb + 16) {
                    break;
                }
                c.drawText(this.textRenderer, line, cl + 5, ty, sel ? 0xFFFFFFFF : TEXT_DARK, true);
                ty += 10;
            }
            // Цена и разница с дубовым
            int cost = focused.getCostlyBlocks(d);
            int costY = cb - 38;
            if (focused.isSelfBuilt()) {
                c.drawText(this.textRenderer, Text.translatable("screen.basedefense.tablet.free_short"), cl + 5, costY,
                        TEAL_LIGHT, false);
            } else {
                c.drawText(this.textRenderer, Text.translatable("screen.basedefense.tablet.blocks", cost), cl + 5, costY,
                        TEXT_BODY, false);
                if (base > 0 && d != HouseDesign.OAK) {
                    int pct = Math.round((cost - base) * 100.0f / base);
                    Text diff = Text.literal((pct >= 0 ? "+" : "") + pct + "%");
                    c.drawText(this.textRenderer, diff, cr - 5 - this.textRenderer.getWidth(diff), costY,
                            pct > 0 ? WARN : GOOD, false);
                }
            }
            // Кнопка/отметка
            int bt = cb - 22, bb = cb - 5;
            if (sel) {
                button(c, this.textRenderer, cl + 5, bt, cr - 5, bb, Text.translatable("screen.basedefense.tablet.status.selected"), 3);
            } else {
                button(c, this.textRenderer, cl + 5, bt, cr - 5, bb, Text.translatable("screen.basedefense.tablet.button.pick"),
                        hov ? 1 : 0);
            }
            c.getMatrices().pop();
        }

        c.getMatrices().push();
        c.getMatrices().translate(0, 0, 700);
        embossedCentered(c, this.textRenderer, Text.translatable("screen.basedefense.tablet.design_hint"),
                windowLeft() + windowWidth() / 2, windowTop() + windowHeight() - 15, TEXT_MUTED);
        c.getMatrices().pop();
    }

    /** Материалы по убыванию количества. */
    private static List<Map.Entry<Item, Integer>> topMaterials(HouseType type, HouseDesign design) {
        List<Map.Entry<Item, Integer>> list = new ArrayList<>(type.getRequirements(design).entrySet());
        list.sort((a, b) -> Integer.compare(b.getValue(), a.getValue()));
        return list;
    }

    /** Количество в ячейке: 1234 → «1.2k», чтобы влезало. */
    private static String compact(int n) {
        return n >= 1000 ? String.format("%.1fk", n / 1000.0f) : String.valueOf(n);
    }

    // =====================================================================
    // Координаты
    // =====================================================================

    private int rowY(HouseType type) {
        return this.y + TOP + type.ordinal() * ROW_H;
    }

    private int detailLeft() {
        return this.x + PAD + LIST_W + 8;
    }

    private int detailBottom() {
        return this.y + TOP + ROWS * ROW_H - 2;
    }

    private int matTop() {
        return detailBottom() - BUTTON_H - 6 - 17;
    }

    private int matX(int i) {
        return detailLeft() + 1 + i * 19;
    }

    private int windowWidth() {
        return 4 * CARD_W + 3 * CARD_GAP + 20;
    }

    private int windowHeight() {
        return HEADER_H + 14 + CARD_H + 24;
    }

    private int windowLeft() {
        return (this.width - windowWidth()) / 2;
    }

    private int windowTop() {
        return (this.height - windowHeight()) / 2;
    }

    private int cardX(HouseDesign d) {
        return windowLeft() + 10 + d.ordinal() * (CARD_W + CARD_GAP);
    }

    private int cardY() {
        return windowTop() + HEADER_H + 12;
    }

    @Nullable
    private HouseType getRowAt(double mouseX, double mouseY) {
        int rl = this.x + PAD;
        for (HouseType type : HouseType.values()) {
            int rt = rowY(type);
            if (mouseX >= rl && mouseX < rl + LIST_W && mouseY >= rt && mouseY < rt + ROW_H - 2) {
                return type;
            }
        }
        return null;
    }

    @Nullable
    private Item getMaterialAt(double mouseX, double mouseY) {
        if (focused.isSelfBuilt() || designWindow) {
            return null;
        }
        List<Map.Entry<Item, Integer>> mats = topMaterials(focused, designOf(focused));
        for (int i = 0; i < Math.min(mats.size(), MATS); i++) {
            int sx = matX(i), sy = matTop();
            if (mouseX >= sx && mouseX < sx + 16 && mouseY >= sy && mouseY < sy + 16) {
                return mats.get(i).getKey();
            }
        }
        return null;
    }

    @Nullable
    private HouseDesign getCardAt(double mouseX, double mouseY) {
        if (!designWindow) {
            return null;
        }
        for (HouseDesign d : HouseDesign.values()) {
            int cl = cardX(d), ct = cardY();
            if (mouseX >= cl && mouseX < cl + CARD_W && mouseY >= ct && mouseY < ct + CARD_H) {
                return d;
            }
        }
        return null;
    }

    private boolean isOverPreview(double mouseX, double mouseY) {
        int dl = detailLeft(), dt = this.y + TOP - 3;
        return mouseX >= dl && mouseX < dl + DETAIL_W && mouseY >= dt && mouseY < dt + PREVIEW_H;
    }

    private boolean isOverDesignButton(double mouseX, double mouseY) {
        int dl = detailLeft(), db = detailBottom();
        return mouseX >= dl && mouseX < dl + DESIGN_BTN_W && mouseY >= db - BUTTON_H && mouseY < db;
    }

    private boolean isOverSelectButton(double mouseX, double mouseY) {
        int bl = detailLeft() + DESIGN_BTN_W + 4, db = detailBottom();
        return mouseX >= bl && mouseX < detailLeft() + DETAIL_W && mouseY >= db - BUTTON_H && mouseY < db;
    }

    private boolean isOverClose(double mouseX, double mouseY) {
        int wr = windowLeft() + windowWidth(), wt = windowTop();
        return mouseX >= wr - 18 && mouseX < wr - 6 && mouseY >= wt + 6 && mouseY < wt + 20;
    }

    private boolean isInsideWindow(double mouseX, double mouseY) {
        return mouseX >= windowLeft() && mouseX < windowLeft() + windowWidth()
                && mouseY >= windowTop() && mouseY < windowTop() + windowHeight();
    }

    // =====================================================================
    // Мышь и клавиши
    // =====================================================================

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (designWindow) {
            if (button == 0) {
                HouseDesign card = getCardAt(mouseX, mouseY);
                if (card != null) {
                    pickDesign(card);
                } else if (isOverClose(mouseX, mouseY) || !isInsideWindow(mouseX, mouseY)) {
                    designWindow = false;
                    playSound(SoundEvents.UI_BUTTON_CLICK, 0.9f);
                }
            }
            return true;
        }
        if (button == 0) {
            HouseType row = getRowAt(mouseX, mouseY);
            if (row != null) {
                long now = Util.getMeasuringTimeMs();
                boolean doubleClick = row == lastClicked && now - lastClickTime < 300;
                lastClicked = row;
                lastClickTime = now;
                if (row != focused) {
                    dragYaw = 0;
                }
                focused = row;
                if (doubleClick) {
                    choose(row);
                } else {
                    playSound(SoundEvents.UI_BUTTON_CLICK, 1.4f);
                }
                return true;
            }
            if (isOverDesignButton(mouseX, mouseY)) {
                designWindow = true;
                playSound(SoundEvents.ITEM_BOOK_PAGE_TURN, 1.0f);
                return true;
            }
            if (isOverSelectButton(mouseX, mouseY)) {
                if (!(focused == currentSelection() && designOf(focused) == BuildingTabletItem.getSelectedDesign(tablet()))) {
                    choose(focused);
                }
                return true;
            }
            if (isOverPreview(mouseX, mouseY)) {
                dragging = true;
                // Фиксируем текущий угол, чтобы при перетаскивании не было скачка
                dragYaw += (Util.getMeasuringTimeMs() % 36000L) / 1000.0f * 10.0f;
                return true;
            }
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseDragged(double mouseX, double mouseY, int button, double deltaX, double deltaY) {
        if (dragging) {
            dragYaw += (float) deltaX * 2.0f;
            return true;
        }
        return super.mouseDragged(mouseX, mouseY, button, deltaX, deltaY);
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        if (dragging) {
            dragging = false;
            // Автовращение продолжится с того же угла
            dragYaw -= (Util.getMeasuringTimeMs() % 36000L) / 1000.0f * 10.0f;
            return true;
        }
        return super.mouseReleased(mouseX, mouseY, button);
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (designWindow && keyCode == 256) {   // Esc закрывает только окно дизайнов
            designWindow = false;
            return true;
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    /** Дизайн выбран в окне: запоминаем (и в планшете) и закрываем окно. */
    private void pickDesign(HouseDesign design) {
        designs.put(focused, design);
        designWindow = false;
        playSound(SoundEvents.UI_BUTTON_CLICK, 1.2f);
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.interactionManager != null) {
            mc.interactionManager.clickButton(this.handler.syncId,
                    TabletScreenHandler.REMEMBER_BASE + TabletScreenHandler.encode(focused, design));
        }
    }

    /** Выбрать здание (с его дизайном) для постройки. Сервер запишет выбор и закроет меню. */
    private void choose(HouseType type) {
        playSound(SoundEvents.UI_BUTTON_CLICK, 1.0f);
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.interactionManager != null) {
            mc.interactionManager.clickButton(this.handler.syncId, TabletScreenHandler.encode(type, designOf(type)));
        }
    }

    private static void playSound(SoundEvent sound, float pitch) {
        MinecraftClient.getInstance().getSoundManager().play(PositionedSoundInstance.master(sound, pitch));
    }

    /** UI_BUTTON_CLICK в 1.20.1 — не SoundEvent, а ссылка на него (RegistryEntry). */
    private static void playSound(RegistryEntry<SoundEvent> sound, float pitch) {
        MinecraftClient.getInstance().getSoundManager().play(PositionedSoundInstance.master(sound, pitch));
    }

    @Override
    protected void drawForeground(DrawContext context, int mouseX, int mouseY) {
        // Пусто: всё рисуем сами
    }
}

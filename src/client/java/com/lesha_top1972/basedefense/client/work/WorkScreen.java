package com.lesha_top1972.basedefense.client.work;

import com.lesha_top1972.basedefense.client.needs.NeedsTabs;
import com.lesha_top1972.basedefense.entity.settler.SettlerEntity;
import com.lesha_top1972.basedefense.work.Passion;
import com.lesha_top1972.basedefense.work.Skill;
import com.lesha_top1972.basedefense.work.SettlerSkills;
import com.lesha_top1972.basedefense.work.SettlerWork;
import com.lesha_top1972.basedefense.work.WorkNetwork;
import com.lesha_top1972.basedefense.work.WorkType;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.entity.Entity;
import net.minecraft.item.ItemStack;
import net.minecraft.text.Text;

import java.util.ArrayList;
import java.util.List;

import static com.lesha_top1972.basedefense.client.screen.UiKit.*;

/**
 * Вкладка «Работа» поселенца: навыки (уровень, опыт, увлечение) и работы — приоритет 1…4 / «—» (клик: +1,
 * правый клик: −1), кнопка «▶» — сделать сейчас (приказ). Внизу: что несёт в руках, радиус области работы,
 * «Центр — здесь» (центр там, где стоит игрок), отмена приказа. Данные обновляются раз в секунду.
 */
public class WorkScreen extends Screen {

    private static final int W = 256;
    private static final int H = 206;
    private static final int ROW_S = 20;
    private static final int ROW_W = 22;

    private final int entityId;
    private int left;
    private int top;
    private int ticks;

    /** Области кликов: x, y, w, h, действие. */
    private record Hit(int x, int y, int w, int h, Runnable left, Runnable right, Text tip) {
        boolean in(double mx, double my) {
            return mx >= x && mx < x + w && my >= y && my < y + h;
        }
    }

    private final List<Hit> hits = new ArrayList<>();

    public WorkScreen(int entityId) {
        super(Text.translatable("gui.basedefense.tab.work"));
        this.entityId = entityId;
    }

    @Override
    protected void init() {
        left = (this.width - W) / 2;
        top = (this.height - H) / 2;
        NeedsTabs.add(this::addDrawableChild, left + W, top, NeedsTabs.Tab.WORK, entityId);
        WorkClient.send(entityId, WorkNetwork.REQUEST, 0, 0);
    }

    @Override
    public boolean shouldPause() {
        return false;
    }

    @Override
    public void tick() {
        SettlerEntity s = settler();
        if (s == null || this.client == null || this.client.player == null || this.client.player.squaredDistanceTo(s) > 10 * 10) {
            this.close();
            return;
        }
        if (++ticks % 20 == 0) {
            WorkClient.send(entityId, WorkNetwork.REQUEST, 0, 0);
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
        SettlerEntity s = settler();
        if (s == null) {
            return;
        }
        WorkClient.Data d = WorkClient.data;
        boolean fresh = d.entityId == entityId;
        hits.clear();
        int l = left;
        int t = top;
        int r = l + W;
        int b = t + H;
        panel(c, l, t, r, b);
        header(c, l + 4, t + 4, r - 4, 22);
        embossed(c, this.textRenderer, s.getDisplayName(), l + 10, t + 11, TEXT_DARK);
        if (fresh) {
            Text st = d.onPost ? Text.translatable("work.basedefense.status.post")
                    : Text.translatable("work.basedefense.status." + d.status);
            pill(c, this.textRenderer, r - 12 - this.textRenderer.getWidth(st) - 8, t + 9, st,
                    d.status.equals("idle") ? TEXT_MUTED : GOOD_LIGHT);
        }
        if (!fresh) {
            super.render(c, mouseX, mouseY, delta);
            return;
        }

        // ---------- Навыки ----------
        int sx = l + 10;
        int sy = t + 34;
        c.drawText(this.textRenderer, Text.translatable("gui.basedefense.work.skills"), sx, sy - 2, TEXT_MUTED, false);
        sy += 10;
        for (Skill sk : Skill.values()) {
            int i = sk.ordinal();
            c.drawItem(new ItemStack(sk.icon), sx, sy);
            Text name = Text.translatable("skill.basedefense." + sk.id);
            c.drawText(this.textRenderer, name, sx + 20, sy, TEXT_BODY, false);
            flames(c, sx + 22 + this.textRenderer.getWidth(name), sy + 1, Passion.values()[d.passion[i]]);
            String lv = String.valueOf(d.level[i]);
            c.drawText(this.textRenderer, lv, sx + 112 - this.textRenderer.getWidth(lv), sy, levelColor(d.level[i]), false);
            float frac = d.level[i] >= SettlerSkills.MAX ? 1f : d.xp[i] / Math.max(1, d.need[i]);
            c.fill(sx + 20, sy + 11, sx + 112, sy + 13, 0x60000000);
            c.fill(sx + 20, sy + 11, sx + 20 + Math.round(92 * frac), sy + 13, 0xFF8FD06A);
            Text tip = Text.translatable("skill.basedefense." + sk.id + ".tip")
                    .append("\n").append(Text.translatable("gui.basedefense.work.xp", Math.round(d.xp[i]), d.need[i]))
                    .append("\n").append(Text.translatable("gui.basedefense.work.passion." + Passion.values()[d.passion[i]]
                            .name().toLowerCase(java.util.Locale.ROOT)));
            hits.add(new Hit(sx, sy - 2, 114, ROW_S, null, null, tip));
            sy += ROW_S;
        }

        // ---------- Работы ----------
        int wx = l + 134;
        int wy = t + 34;
        c.drawText(this.textRenderer, Text.translatable("gui.basedefense.work.jobs"), wx, wy - 2, TEXT_MUTED, false);
        wy += 10;
        for (WorkType wt : WorkType.values()) {
            int i = wt.ordinal();
            boolean forcedHere = d.forced == i;
            c.drawItem(new ItemStack(wt.icon), wx, wy);
            Text name = Text.translatable("work.basedefense." + wt.id);
            c.drawText(this.textRenderer, name, wx + 19, wy + 4, forcedHere ? GOOD_LIGHT : TEXT_BODY, false);
            if (d.problem[i] != null) {
                c.drawText(this.textRenderer, "!", wx + 21 + this.textRenderer.getWidth(name), wy + 4, WARN, false);
                hits.add(new Hit(wx + 19, wy, 70, 16, null, null,
                        Text.translatable("work.basedefense.problem." + d.problem[i])));
            }
            // Приоритет
            int px = l + W - 40;
            int p = d.priority[i];
            box(c, px, wy, 14, 14, p == 0 ? 0x40000000 : 0x80000000);
            String ps = p == 0 ? "–" : String.valueOf(p);
            c.drawText(this.textRenderer, ps, px + 7 - this.textRenderer.getWidth(ps) / 2, wy + 3, prioColor(p), false);
            hits.add(new Hit(px, wy, 14, 14,
                    () -> WorkClient.send(entityId, WorkNetwork.PRIORITY, i, p == 0 ? 1 : (p == 4 ? 0 : p + 1)),
                    () -> WorkClient.send(entityId, WorkNetwork.PRIORITY, i, p == 0 ? 4 : p - 1),
                    Text.translatable("gui.basedefense.work.priority.tip")));
            // Приказ «сейчас»
            int ox = px + 17;
            box(c, ox, wy, 14, 14, forcedHere ? 0xC040A060 : 0x80000000);
            c.drawText(this.textRenderer, "▶", ox + 4, wy + 3, forcedHere ? 0xFFFFFFFF : TEXT_BODY, false);
            hits.add(new Hit(ox, wy, 14, 14,
                    () -> WorkClient.send(entityId, WorkNetwork.ORDER, forcedHere ? -1 : i, 0), null,
                    Text.translatable(forcedHere ? "gui.basedefense.work.order.cancel" : "gui.basedefense.work.order.tip",
                            Text.translatable("work.basedefense." + wt.id))));
            wy += ROW_W;
        }

        // ---------- Низ: руки, радиус, центр ----------
        int by = t + 168;
        divider(c, l + 10, r - 10, by - 6);
        c.drawText(this.textRenderer, Text.translatable("gui.basedefense.work.carry"), l + 10, by + 4, TEXT_MUTED, false);
        int cx = l + 10 + this.textRenderer.getWidth(Text.translatable("gui.basedefense.work.carry")) + 4;
        for (int i = 0; i < SettlerWork.CARRY_SIZE; i++) {
            box(c, cx + i * 18, by, 18, 18, 0x60000000);
            ItemStack st = d.carry[i];
            if (st != null && !st.isEmpty()) {
                c.drawItem(st, cx + i * 18 + 1, by + 1);
                c.drawItemInSlot(this.textRenderer, st, cx + i * 18 + 1, by + 1);
            }
        }
        int rx = l + 134;
        Text rad = Text.translatable("gui.basedefense.work.radius", d.radius);
        button(c, rx, by + 2, 12, 14, "−", () -> WorkClient.send(entityId, WorkNetwork.RADIUS, -4, 0),
                Text.translatable("gui.basedefense.work.radius.tip"));
        c.drawText(this.textRenderer, rad, rx + 16, by + 5, TEXT_BODY, false);
        int rpx = rx + 18 + this.textRenderer.getWidth(rad);
        button(c, rpx, by + 2, 12, 14, "+", () -> WorkClient.send(entityId, WorkNetwork.RADIUS, 4, 0),
                Text.translatable("gui.basedefense.work.radius.tip"));
        Text here = Text.translatable("gui.basedefense.work.center");
        int hw = this.textRenderer.getWidth(here) + 8;
        button(c, r - 10 - hw, by + 2, hw, 14, here.getString(),
                () -> WorkClient.send(entityId, WorkNetwork.CENTER, 0, 0), Text.translatable("gui.basedefense.work.center.tip"));
        if (d.forced >= 0) {
            Text ord = Text.translatable("gui.basedefense.work.ordered",
                    Text.translatable("work.basedefense." + WorkType.values()[d.forced].id));
            c.drawText(this.textRenderer, ord, l + 10, b - 14, GOOD_LIGHT, false);
        }

        super.render(c, mouseX, mouseY, delta);
        for (Hit h : hits) {
            if (h.in(mouseX, mouseY) && h.tip() != null) {
                c.drawOrderedTooltip(this.textRenderer, this.textRenderer.wrapLines(h.tip(), 200), mouseX, mouseY);
                break;
            }
        }
    }

    @Override
    public boolean mouseClicked(double mx, double my, int button) {
        for (Hit h : hits) {
            if (h.in(mx, my)) {
                Runnable a = button == 1 ? h.right() : h.left();
                if (a != null) {
                    a.run();
                    if (this.client != null) {
                        this.client.getSoundManager().play(net.minecraft.client.sound.PositionedSoundInstance.master(
                                net.minecraft.sound.SoundEvents.UI_BUTTON_CLICK, 1.0f));
                    }
                    return true;
                }
            }
        }
        return super.mouseClicked(mx, my, button);
    }

    private void button(DrawContext c, int x, int y, int w, int h, String label, Runnable onClick, Text tip) {
        box(c, x, y, w, h, 0x80000000);
        c.drawText(this.textRenderer, label, x + w / 2 - this.textRenderer.getWidth(label) / 2, y + (h - 8) / 2,
                TEXT_BODY, false);
        hits.add(new Hit(x, y, w, h, onClick, null, tip));
    }

    private static void box(DrawContext c, int x, int y, int w, int h, int fill) {
        c.fill(x, y, x + w, y + h, fill);
        c.fill(x, y, x + w, y + 1, 0x50FFFFFF);
        c.fill(x, y + h - 1, x + w, y + h, 0x30000000);
    }

    /** Увлечение: один огонёк — малое, два — большое. */
    private static void flames(DrawContext c, int x, int y, Passion p) {
        int n = p == Passion.MAJOR ? 2 : p == Passion.MINOR ? 1 : 0;
        for (int i = 0; i < n; i++) {
            int fx = x + i * 6;
            c.fill(fx + 1, y, fx + 3, y + 2, 0xFFFFD040);
            c.fill(fx, y + 2, fx + 4, y + 6, 0xFFFF8A20);
            c.fill(fx + 1, y + 3, fx + 3, y + 6, 0xFFFFD040);
        }
    }

    private static int levelColor(int lvl) {
        return lvl >= 15 ? 0xFFFFD060 : lvl >= 8 ? GOOD_LIGHT : lvl >= 4 ? TEXT_BODY : TEXT_MUTED;
    }

    private static int prioColor(int p) {
        return switch (p) {
            case 1 -> 0xFFFF7A60;
            case 2 -> 0xFFFFC060;
            case 3 -> 0xFFE8E8E8;
            case 4 -> 0xFFA0A0A0;
            default -> 0xFF707070;
        };
    }
}

package com.lesha_top1972.basedefense.client.needs;

import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.tooltip.Tooltip;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.item.ItemStack;
import net.minecraft.text.Text;

import java.util.function.BooleanSupplier;

/** Вкладка справа от окна («Инвентарь» / «Потребности»): значок‑предмет, выбранная — светлее и шире. */
public class SideTab extends ButtonWidget {

    public static final int W = 26;
    public static final int H = 22;

    private final ItemStack icon;
    private final boolean selected;
    /** Когда вкладка видна (рюкзак — только если он надет). */
    private final BooleanSupplier shown;

    public SideTab(int x, int y, ItemStack icon, Text tooltip, boolean selected, BooleanSupplier shown,
                   PressAction onPress) {
        super(x, y, W, H, tooltip, onPress, DEFAULT_NARRATION_SUPPLIER);
        this.icon = icon;
        this.selected = selected;
        this.shown = shown;
        this.setTooltip(Tooltip.of(tooltip));
    }

    @Override
    public void render(DrawContext c, int mouseX, int mouseY, float delta) {
        this.visible = shown.getAsBoolean();
        super.render(c, mouseX, mouseY, delta);
    }

    @Override
    protected void renderButton(DrawContext c, int mouseX, int mouseY, float delta) {
        int x = getX();
        int y = getY();
        int r = x + W - (selected ? 0 : 3);
        int b = y + H;
        c.fill(x, y, r, b, 0xFF000000);
        c.fill(x, y + 1, r - 1, b - 1, selected ? 0xFFC6C6C6 : (this.isHovered() ? 0xFFA8B4BC : 0xFF8B8B8B));
        c.fill(x, y + 1, r - 1, y + 2, 0xFFFFFFFF);
        c.fill(r - 2, y + 2, r - 1, b - 1, 0xFF555555);
        c.fill(x, b - 2, r - 1, b - 1, 0xFF555555);
        c.drawItem(icon, x + 4, y + 3);
    }
}

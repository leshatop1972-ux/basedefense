package com.lesha_top1972.basedefense.client.clothing;

import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.tooltip.Tooltip;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.item.ItemStack;
import net.minecraft.text.Text;

import java.util.function.BooleanSupplier;

/** Кнопка‑значок 18×18 с предметом (кнопка «Рюкзак»); видна, пока shown == true. */
public class ItemButton extends ButtonWidget {

    public static final int SIZE = 18;

    private final ItemStack icon;
    private final BooleanSupplier shown;

    public ItemButton(int x, int y, ItemStack icon, Text tooltip, BooleanSupplier shown, PressAction onPress) {
        super(x, y, SIZE, SIZE, tooltip, onPress, DEFAULT_NARRATION_SUPPLIER);
        this.icon = icon;
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
        c.fill(x, y, x + SIZE, y + SIZE, 0xFF000000);
        c.fill(x + 1, y + 1, x + SIZE - 1, y + SIZE - 1, this.isHovered() ? 0xFFA8B4BC : 0xFF8B8B8B);
        c.fill(x + 1, y + 1, x + SIZE - 1, y + 2, 0xFFFFFFFF);
        c.fill(x + 1, y + SIZE - 2, x + SIZE - 1, y + SIZE - 1, 0xFF555555);
        c.drawItem(icon, x + 1, y + 1);
    }
}

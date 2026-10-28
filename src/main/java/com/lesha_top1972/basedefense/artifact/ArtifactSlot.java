package com.lesha_top1972.basedefense.artifact;

import net.minecraft.inventory.Inventory;
import net.minecraft.item.ItemStack;
import net.minecraft.screen.slot.Slot;

import java.util.function.BooleanSupplier;

/**
 * Слот артефакта в инвентаре игрока (PlayerScreenHandler, слоты 46–50). Принимает только артефакты, по одному.
 * На клиенте виден и нажимается, только когда открыта вкладка артефактов ({@link #visible} задаёт клиент).
 */
public class ArtifactSlot extends Slot {

    /** Положение вкладки: слева от инвентаря, у брони. */
    public static final int X = -23;
    public static final int Y0 = 8;

    /** Клиент: открыта ли вкладка. На сервере — null (слот всегда активен). */
    public static BooleanSupplier visible;

    public ArtifactSlot(Inventory inventory, int index) {
        super(inventory, index, X, Y0 + index * 18);
    }

    @Override
    public boolean canInsert(ItemStack stack) {
        return stack.getItem() instanceof ArtifactItem;
    }

    @Override
    public int getMaxItemCount() {
        return 1;
    }

    @Override
    public boolean isEnabled() {
        return visible == null || visible.getAsBoolean();
    }
}

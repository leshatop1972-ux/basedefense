package com.lesha_top1972.basedefense.clothing;

import com.lesha_top1972.basedefense.Basedefense;
import com.mojang.datafixers.util.Pair;
import net.minecraft.inventory.Inventory;
import net.minecraft.item.ItemStack;
import net.minecraft.screen.PlayerScreenHandler;
import net.minecraft.screen.slot.Slot;
import net.minecraft.util.Identifier;
import org.jetbrains.annotations.Nullable;

import java.util.function.BooleanSupplier;

/** Слот рюкзака на панели одежды (ячейка {@link Clothing#BACKPACK}). */
public class BackpackSlot extends Slot {

    @Nullable
    private final BooleanSupplier visible;

    public BackpackSlot(Inventory inventory, int x, int y, @Nullable BooleanSupplier visible) {
        super(inventory, Clothing.BACKPACK, x, y);
        this.visible = visible;
    }

    @Override
    public boolean canInsert(ItemStack stack) {
        return stack.getItem() instanceof BackpackItem;
    }

    @Override
    public int getMaxItemCount() {
        return 1;
    }

    @Override
    public boolean isEnabled() {
        return visible == null || visible.getAsBoolean();
    }

    @Override
    public Pair<Identifier, Identifier> getBackgroundSprite() {
        return Pair.of(PlayerScreenHandler.BLOCK_ATLAS_TEXTURE, new Identifier(Basedefense.MOD_ID, "item/empty_slot_backpack"));
    }
}

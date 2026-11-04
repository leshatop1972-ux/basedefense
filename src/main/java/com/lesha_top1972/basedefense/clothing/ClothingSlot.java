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

/**
 * Слот одежды (у игрока и у поселенца). Принимает только одежду своего типа, по одной.
 * На клиенте виден, только когда включён вид «одежда» ({@code visible}); на сервере visible == null — всегда активен.
 */
public class ClothingSlot extends Slot {

    private final ClothingType type;
    @Nullable
    private final BooleanSupplier visible;

    public ClothingSlot(Inventory inventory, int index, int x, int y, @Nullable BooleanSupplier visible) {
        super(inventory, index, x, y);
        this.type = ClothingType.values()[index];
        this.visible = visible;
    }

    public ClothingType getType() {
        return type;
    }

    @Override
    public boolean canInsert(ItemStack stack) {
        return stack.getItem() instanceof ClothingItem c && c.getType() == type;
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
        return Pair.of(PlayerScreenHandler.BLOCK_ATLAS_TEXTURE,
                new Identifier(Basedefense.MOD_ID, "item/empty_slot_clothing_" + type.id));
    }
}

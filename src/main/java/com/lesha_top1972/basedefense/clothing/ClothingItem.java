package com.lesha_top1972.basedefense.clothing;

import net.minecraft.client.item.TooltipContext;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.world.World;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/** Одежда: кладётся только в свой слот одежды ({@link ClothingType}). Пока ничего не даёт. */
public class ClothingItem extends Item {

    private final ClothingType type;

    public ClothingItem(ClothingType type, Settings settings) {
        super(settings);
        this.type = type;
    }

    public ClothingType getType() {
        return type;
    }

    @Override
    public void appendTooltip(ItemStack stack, @Nullable World world, List<Text> tooltip, TooltipContext context) {
        tooltip.add(Text.translatable("tooltip.basedefense.clothing." + type.id).formatted(Formatting.GRAY));
    }
}

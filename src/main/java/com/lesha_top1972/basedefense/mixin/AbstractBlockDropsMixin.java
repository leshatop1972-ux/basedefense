package com.lesha_top1972.basedefense.mixin;

import com.lesha_top1972.basedefense.farming.Crops;
import net.minecraft.block.AbstractBlock;
import net.minecraft.block.BlockState;
import net.minecraft.block.CropBlock;
import net.minecraft.item.ItemStack;
import net.minecraft.loot.context.LootContextParameterSet;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.ArrayList;
import java.util.List;

/** Спелые культуры дают вдвое больше (весь урожай, включая семена). */
@Mixin(AbstractBlock.class)
public abstract class AbstractBlockDropsMixin {

    @Inject(method = "getDroppedStacks", at = @At("RETURN"), cancellable = true)
    private void basedefense$doubleHarvest(BlockState state, LootContextParameterSet.Builder builder,
                                           CallbackInfoReturnable<List<ItemStack>> cir) {
        if (state.getBlock() instanceof CropBlock crop && crop.isMature(state)) {
            List<ItemStack> out = new ArrayList<>();
            for (ItemStack s : cir.getReturnValue()) {
                int n = s.getCount() * Crops.HARVEST_MULTIPLIER;
                while (n > 0) {
                    int part = Math.min(n, s.getMaxCount());
                    out.add(s.copyWithCount(part));
                    n -= part;
                }
            }
            cir.setReturnValue(out);
        }
    }
}

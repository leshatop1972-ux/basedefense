package com.lesha_top1972.basedefense.structure;

import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.block.FlowerPotBlock;
import net.minecraft.block.FluidBlock;
import net.minecraft.block.LeavesBlock;
import net.minecraft.block.enums.BedPart;
import net.minecraft.block.enums.DoubleBlockHalf;
import net.minecraft.item.Item;
import net.minecraft.item.Items;
import net.minecraft.state.property.Properties;
import net.minecraft.util.math.BlockPos;
import org.jetbrains.annotations.Nullable;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Какой предмет нужен строителю, чтобы поставить блок чертежа.
 * Бесплатно: воздух (расчистка), засыпка фундамента, вода, листва,
 * вторая половина кровати и двери, растение в горшке (нужен только горшок).
 *
 * Трава, пашня и тропинка во дворе тоже бесплатны (строитель просто обрабатывает землю).
 */
public final class BuildCost {

    private BuildCost() {
    }

    @Nullable
    public static Item itemFor(StructurePlan.Placement placement) {
        if (placement.onlyIfReplaceable()) {
            return null;   // засыпка ям под фундаментом
        }
        BlockState state = placement.state();
        if (state.isAir()) {
            return null;
        }
        Block block = state.getBlock();
        if (block instanceof FluidBlock || block instanceof LeavesBlock) {
            return null;
        }
        if (state.contains(Properties.BED_PART) && state.get(Properties.BED_PART) == BedPart.HEAD) {
            return null;
        }
        if (state.contains(Properties.DOUBLE_BLOCK_HALF) && state.get(Properties.DOUBLE_BLOCK_HALF) == DoubleBlockHalf.UPPER) {
            return null;
        }
        if (block == Blocks.BELL) {
            return null;   // колокол поселению выдаёт мод (в обычном Minecraft его не скрафтить)
        }
        if (block == com.lesha_top1972.basedefense.block.ModBlocks.RECRUITMENT_OFFICE) {
            return null;   // военкомат тоже выдаёт мод (его не скрафтить)
        }
        if (block instanceof FlowerPotBlock) {
            return Items.FLOWER_POT;
        }
        if (block == Blocks.GRASS_BLOCK || block == Blocks.FARMLAND || block == Blocks.DIRT_PATH) {
            return null;   // землю двора строитель просто вскапывает и утаптывает
        }
        Item item = block.asItem();
        return item == Items.AIR ? null : item;
    }

    /** Сколько каких материалов нужно на шаги с номера from до конца (клетку блока дома не считаем). */
    public static Map<Item, Integer> count(List<List<StructurePlan.Placement>> steps, int from, BlockPos origin) {
        Map<Item, Integer> result = new LinkedHashMap<>();
        for (int i = Math.max(0, from); i < steps.size(); i++) {
            for (StructurePlan.Placement p : steps.get(i)) {
                if (p.local().equals(origin)) {
                    continue;
                }
                Item item = itemFor(p);
                if (item != null) {
                    result.merge(item, 1, Integer::sum);
                }
            }
        }
        return result;
    }
}

package com.lesha_top1972.basedefense.entity.settler;

import com.lesha_top1972.basedefense.item.ModItems;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.mob.PathAwareEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.world.World;

import java.util.List;

/**
 * Житель-фермер. Появляется в доме фермера.
 * Работает на грядках: каждый день добывает 3 случайных варианта из списка своего уровня.
 */
public class FarmerEntity extends SettlerEntity {

    public FarmerEntity(EntityType<? extends PathAwareEntity> type, World world) {
        super(type, world);
    }

    /** Что добывает фермер: каждый день 3 случайных варианта из списка. */
    @Override
    public List<WorkLoot> getWorkLoot(int level) {
        // Список создаётся при вызове (а не заранее), чтобы предметы мода точно были зарегистрированы
        return switch (level) {
            case 2 -> List.of(
                    new WorkLoot(Items.BREAD, 1),
                    new WorkLoot(Items.BEETROOT, 5),
                    new WorkLoot(Items.POTATO, 4),
                    new WorkLoot(Items.CARROT, 5),
                    new WorkLoot(Items.MELON_SLICE, 6),
                    new WorkLoot(ModItems.RESEARCH_COIN, 6));
            case 3 -> List.of(
                    new WorkLoot(Items.BREAD, 2),
                    new WorkLoot(Items.BEETROOT, 8),
                    new WorkLoot(Items.POTATO, 6),
                    new WorkLoot(Items.CARROT, 7),
                    new WorkLoot(Items.MELON_SLICE, 9),
                    new WorkLoot(ModItems.RESEARCH_COIN, 10));
            default -> List.of(
                    new WorkLoot(Items.BREAD, 1),
                    new WorkLoot(Items.BEETROOT, 3),
                    new WorkLoot(Items.POTATO, 2),
                    new WorkLoot(Items.CARROT, 3),
                    new WorkLoot(Items.MELON_SLICE, 3),
                    new WorkLoot(ModItems.RESEARCH_COIN, 3));
        };
    }

    /** Сколько еды тратит работающий фермер в день. */
    @Override
    public int getWorkFood(int level) {
        return switch (level) {
            case 2 -> 16;
            case 3 -> 23;
            default -> 12;
        };
    }

    /** Работает на грядках (пашне). */
    @Override
    public boolean isWorkSpot(BlockState state) {
        return state.isOf(Blocks.FARMLAND);
    }

    @Override
    protected ItemStack getDefaultTool() {
        return new ItemStack(Items.IRON_HOE);
    }
}

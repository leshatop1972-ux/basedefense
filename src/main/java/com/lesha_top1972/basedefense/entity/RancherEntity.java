package com.lesha_top1972.basedefense.entity;

import com.lesha_top1972.basedefense.item.ModItems;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.mob.PathAwareEntity;
import net.minecraft.item.Items;
import net.minecraft.world.World;

import java.util.List;

/**
 * Житель-скотовод. Появляется в доме скотовода.
 * Работает в загоне (у сена): каждый день добывает 3 случайных варианта из списка своего уровня.
 * Мясо даёт больше очков еды, чем урожай фермера, поэтому и ест скотовод чуть больше.
 */
public class RancherEntity extends SettlerEntity {

    public RancherEntity(EntityType<? extends PathAwareEntity> type, World world) {
        super(type, world);
    }

    /** Что добывает скотовод: каждый день 3 случайных варианта из списка. */
    @Override
    public List<WorkLoot> getWorkLoot(int level) {
        // Список создаётся при вызове (а не заранее), чтобы предметы мода точно были зарегистрированы
        return switch (level) {
            case 2 -> List.of(
                    new WorkLoot(Items.BEEF, 3),
                    new WorkLoot(Items.PORKCHOP, 3),
                    new WorkLoot(Items.MUTTON, 3),
                    new WorkLoot(Items.CHICKEN, 3),
                    new WorkLoot(Items.EGG, 6),
                    new WorkLoot(Items.LEATHER, 3),
                    new WorkLoot(Items.WHITE_WOOL, 5),
                    new WorkLoot(ModItems.RESEARCH_COIN, 6));
            case 3 -> List.of(
                    new WorkLoot(Items.BEEF, 5),
                    new WorkLoot(Items.PORKCHOP, 5),
                    new WorkLoot(Items.MUTTON, 5),
                    new WorkLoot(Items.CHICKEN, 5),
                    new WorkLoot(Items.EGG, 10),
                    new WorkLoot(Items.LEATHER, 5),
                    new WorkLoot(Items.WHITE_WOOL, 8),
                    new WorkLoot(ModItems.RESEARCH_COIN, 10));
            default -> List.of(
                    new WorkLoot(Items.BEEF, 2),
                    new WorkLoot(Items.PORKCHOP, 2),
                    new WorkLoot(Items.MUTTON, 2),
                    new WorkLoot(Items.CHICKEN, 2),
                    new WorkLoot(Items.EGG, 3),
                    new WorkLoot(Items.LEATHER, 2),
                    new WorkLoot(Items.WHITE_WOOL, 3),
                    new WorkLoot(ModItems.RESEARCH_COIN, 3));
        };
    }

    /** Сколько еды тратит работающий скотовод в день. */
    @Override
    public int getWorkFood(int level) {
        return switch (level) {
            case 2 -> 18;
            case 3 -> 25;
            default -> 13;
        };
    }

    /** Работает у сена в загоне. */
    @Override
    public boolean isWorkSpot(BlockState state) {
        return state.isOf(Blocks.HAY_BLOCK);
    }
}

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
 * Житель-шахтёр. Появляется в маленьком домике со спуском в шахту.
 * Работает внизу шахты (у рельсов) и каждый день приносит 2 случайных варианта из списка своего уровня.
 */
public class MinerEntity extends SettlerEntity {

    public MinerEntity(EntityType<? extends PathAwareEntity> type, World world) {
        super(type, world);
    }

    /** Что приносит шахтёр (2 варианта в день). */
    @Override
    public List<WorkLoot> getWorkLoot(int level) {
        // Список создаётся при вызове (а не заранее), чтобы предметы мода точно были зарегистрированы
        return switch (level) {
            case 2 -> List.of(
                    new WorkLoot(Items.IRON_ORE, 6, 7),
                    new WorkLoot(Items.GOLD_ORE, 4, 5),
                    new WorkLoot(Items.COAL, 15, 18),
                    new WorkLoot(Items.DIAMOND, 1),
                    new WorkLoot(ModItems.RESEARCH_COIN, 10));
            case 3 -> List.of(
                    new WorkLoot(Items.IRON_ORE, 10, 14),
                    new WorkLoot(Items.GOLD_ORE, 6, 8),
                    new WorkLoot(Items.COAL, 20, 22),
                    new WorkLoot(Items.DIAMOND, 2),
                    new WorkLoot(ModItems.RESEARCH_COIN, 13));
            default -> List.of(
                    new WorkLoot(Items.IRON_ORE, 4, 5),
                    new WorkLoot(Items.GOLD_ORE, 2, 3),
                    new WorkLoot(Items.COPPER_ORE, 8, 10),
                    new WorkLoot(Items.COAL, 9, 12));
        };
    }

    /** Каждый день — 2 случайных варианта из списка (например, уголь и железо). */
    @Override
    public int getLootRolls() {
        return 2;
    }

    /** Сколько еды тратит работающий шахтёр в день. */
    @Override
    public int getWorkFood(int level) {
        return switch (level) {
            case 2 -> 18;
            case 3 -> 25;
            default -> 13;
        };
    }

    /** Работает у рельсов в шахте. */
    @Override
    public boolean isWorkSpot(BlockState state) {
        return state.isOf(Blocks.RAIL);
    }
}

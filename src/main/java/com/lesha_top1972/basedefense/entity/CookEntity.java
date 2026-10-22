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
 * Житель-повар. Появляется в доме повара.
 * Игрок отдаёт ему еду (кнопка «Отдать еду из руки») — она превращается в очки продуктов
 * по той же таблице, что и в Базе (хлеб = 10 и т.д.).
 * Каждый рабочий день, если накопилось 30 очков, повар тратит их и готовит одно более сытное блюдо.
 */
public class CookEntity extends SettlerEntity {

    /** Сколько очков продуктов уходит на один день готовки. */
    public static final int INGREDIENTS_PER_DAY = 30;

    public CookEntity(EntityType<? extends PathAwareEntity> type, World world) {
        super(type, world);
    }

    /** Что может приготовить повар (один вариант в день). */
    @Override
    public List<WorkLoot> getWorkLoot(int level) {
        // Список создаётся при вызове (а не заранее), чтобы предметы мода точно были зарегистрированы
        return switch (level) {
            case 2 -> List.of(
                    new WorkLoot(Items.PUMPKIN_PIE, 3, 4),
                    new WorkLoot(Items.CAKE, 2, 3),
                    new WorkLoot(Items.BEETROOT_SOUP, 3, 5),
                    new WorkLoot(Items.BREAD, 4, 6),
                    new WorkLoot(ModItems.RESEARCH_COIN, 10));
            case 3 -> List.of(
                    new WorkLoot(Items.PUMPKIN_PIE, 3, 4),
                    new WorkLoot(Items.CAKE, 2, 3),
                    new WorkLoot(Items.BEETROOT_SOUP, 3, 5),
                    new WorkLoot(Items.BREAD, 4, 6),
                    new WorkLoot(ModItems.RESEARCH_COIN, 15));
            default -> List.of(
                    new WorkLoot(Items.PUMPKIN_PIE, 2, 3),
                    new WorkLoot(Items.CAKE, 1, 2),
                    new WorkLoot(Items.BEETROOT_SOUP, 2, 3),
                    new WorkLoot(Items.BREAD, 3, 4));
        };
    }

    @Override
    public int getLootRolls() {
        return 1;
    }

    /** Сколько еды тратит работающий повар в день. */
    @Override
    public int getWorkFood(int level) {
        return switch (level) {
            case 2 -> 16;
            case 3 -> 23;
            default -> 12;
        };
    }

    @Override
    public boolean acceptsIngredients() {
        return true;
    }

    @Override
    public int getIngredientsPerDay() {
        return INGREDIENTS_PER_DAY;
    }

    /** Готовит, только если хватает продуктов. */
    @Override
    protected void produceOneDay() {
        if (getIngredients() < INGREDIENTS_PER_DAY) {
            return;
        }
        setIngredients(getIngredients() - INGREDIENTS_PER_DAY);
        super.produceOneDay();
    }

    /** Работает у кирпичных печей. */
    @Override
    public boolean isWorkSpot(BlockState state) {
        return state.isOf(Blocks.BRICKS);
    }
}

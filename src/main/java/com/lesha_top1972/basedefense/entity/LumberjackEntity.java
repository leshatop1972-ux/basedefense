package com.lesha_top1972.basedefense.entity;

import com.lesha_top1972.basedefense.item.ModItems;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.mob.PathAwareEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.registry.tag.BlockTags;
import net.minecraft.world.World;

import java.util.List;

/**
 * Житель-лесоруб. Появляется в маленьком домике с посадкой деревьев на участке.
 * Каждый день приносит брёвна одной случайной породы: 16 / 25 / 35 по уровням,
 * а на 3 уровне ещё и 6 монет.
 */
public class LumberjackEntity extends SettlerEntity {

    public LumberjackEntity(EntityType<? extends PathAwareEntity> type, World world) {
        super(type, world);
    }

    /** Брёвна: каждый день выбирается одна порода, количество зависит от уровня. */
    @Override
    public List<WorkLoot> getWorkLoot(int level) {
        int count = switch (level) {
            case 2 -> 25;
            case 3 -> 35;
            default -> 16;
        };
        return List.of(
                new WorkLoot(Items.OAK_LOG, count),
                new WorkLoot(Items.BIRCH_LOG, count),
                new WorkLoot(Items.SPRUCE_LOG, count),
                new WorkLoot(Items.DARK_OAK_LOG, count),
                new WorkLoot(Items.JUNGLE_LOG, count),
                new WorkLoot(Items.ACACIA_LOG, count));
    }

    @Override
    public int getLootRolls() {
        return 1;
    }

    /** На 3 уровне к брёвнам каждый день добавляются 6 монет. */
    @Override
    protected void produceOneDay() {
        super.produceOneDay();
        if (getLevel() >= 3) {
            addLoot(new WorkLoot(ModItems.RESEARCH_COIN, 6));
        }
    }

    /** Сколько еды тратит работающий лесоруб в день. */
    @Override
    public int getWorkFood(int level) {
        return switch (level) {
            case 2 -> 16;
            case 3 -> 23;
            default -> 12;
        };
    }

    /** Работает у деревьев: любые брёвна рядом, кроме еловых (из них углы домика и поленница). */
    @Override
    public boolean isWorkSpot(BlockState state) {
        return state.isIn(BlockTags.LOGS) && !state.isOf(Blocks.SPRUCE_LOG);
    }

    @Override
    protected ItemStack getDefaultTool() {
        return new ItemStack(Items.IRON_AXE);
    }
}

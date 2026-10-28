package com.lesha_top1972.basedefense.entity.settler;

import net.minecraft.item.Item;
import net.minecraft.util.math.random.Random;

/**
 * Один вариант добычи жителя: предмет и сколько штук (от min до max включительно).
 * Например, new WorkLoot(Items.IRON_ORE, 4, 5) — 4 или 5 железной руды.
 */
public record WorkLoot(Item item, int min, int max) {

    /** Ровно count штук. */
    public WorkLoot(Item item, int count) {
        this(item, count, count);
    }

    /** Случайное количество в пределах min..max. */
    public int rollCount(Random random) {
        return max <= min ? min : min + random.nextInt(max - min + 1);
    }
}

package com.lesha_top1972.basedefense.entity;

import net.minecraft.item.Item;

/** Один вариант добычи жителя: предмет и сколько штук. */
public record WorkLoot(Item item, int count) {
}

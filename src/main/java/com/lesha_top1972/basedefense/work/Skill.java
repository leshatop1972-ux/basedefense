package com.lesha_top1972.basedefense.work;

import net.minecraft.item.Item;
import net.minecraft.item.Items;

/** Навыки поселенца (0…20). Растут от дела: бой — от ударов и выстрелов, остальные — от работы. */
public enum Skill {
    MELEE("melee", Items.IRON_SWORD),
    SHOOTING("shooting", Items.BOW),
    FARMING("farming", Items.WHEAT),
    WOODCUTTING("woodcutting", Items.IRON_AXE),
    MINING("mining", Items.IRON_PICKAXE),
    COOKING("cooking", Items.BREAD);

    public final String id;
    public final Item icon;

    Skill(String id, Item icon) {
        this.id = id;
        this.icon = icon;
    }
}

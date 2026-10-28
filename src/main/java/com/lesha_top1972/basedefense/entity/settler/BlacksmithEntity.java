package com.lesha_top1972.basedefense.entity.settler;

import net.minecraft.entity.EntityType;
import net.minecraft.entity.mob.PathAwareEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.world.World;

/** Житель-кузнец. Появляется в кузнице. Работу добавим позже. */
public class BlacksmithEntity extends SettlerEntity {

    public BlacksmithEntity(EntityType<? extends PathAwareEntity> type, World world) {
        super(type, world);
    }

    @Override
    protected ItemStack getDefaultTool() {
        return new ItemStack(Items.STONE_PICKAXE);
    }
}

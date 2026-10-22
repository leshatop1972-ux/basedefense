package com.lesha_top1972.basedefense.entity;

import net.minecraft.entity.EntityType;
import net.minecraft.entity.mob.PathAwareEntity;
import net.minecraft.world.World;

/** Житель-фермер. Появляется в доме фермера. Работу добавим позже. */
public class FarmerEntity extends SettlerEntity {

    public FarmerEntity(EntityType<? extends PathAwareEntity> type, World world) {
        super(type, world);
    }
}

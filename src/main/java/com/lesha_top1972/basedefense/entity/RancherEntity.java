package com.lesha_top1972.basedefense.entity;

import net.minecraft.entity.EntityType;
import net.minecraft.entity.mob.PathAwareEntity;
import net.minecraft.world.World;

/** Житель-скотовод. Появляется в доме скотовода. Работу добавим позже. */
public class RancherEntity extends SettlerEntity {

    public RancherEntity(EntityType<? extends PathAwareEntity> type, World world) {
        super(type, world);
    }
}

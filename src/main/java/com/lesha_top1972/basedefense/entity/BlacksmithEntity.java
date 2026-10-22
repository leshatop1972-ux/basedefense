package com.lesha_top1972.basedefense.entity;

import net.minecraft.entity.EntityType;
import net.minecraft.entity.mob.PathAwareEntity;
import net.minecraft.world.World;

/** Житель-кузнец. Появляется в кузнице. Работу добавим позже. */
public class BlacksmithEntity extends SettlerEntity {

    public BlacksmithEntity(EntityType<? extends PathAwareEntity> type, World world) {
        super(type, world);
    }
}

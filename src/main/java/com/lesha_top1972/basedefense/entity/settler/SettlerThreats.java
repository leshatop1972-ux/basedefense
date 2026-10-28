package com.lesha_top1972.basedefense.entity.settler;

import com.lesha_top1972.basedefense.mixin.MobEntityAccessor;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerEntityEvents;
import net.minecraft.entity.Entity;
import net.minecraft.entity.ai.goal.ActiveTargetGoal;
import net.minecraft.entity.mob.Angerable;
import net.minecraft.entity.mob.CreeperEntity;
import net.minecraft.entity.mob.HostileEntity;

/**
 * Враждебные мобы теперь охотятся и на жителей поселения.
 * Когда моб появляется в мире (или загружается с чанком), ему добавляется цель «атаковать жителя».
 * Не трогаем: нейтральных (эндермен, зомби‑пиглин — они Angerable) и криперов (чтобы не взрывали дома).
 */
public final class SettlerThreats {

    private SettlerThreats() {
    }

    public static void init() {
        ServerEntityEvents.ENTITY_LOAD.register((entity, world) -> {
            if (isThreat(entity)) {
                HostileEntity mob = (HostileEntity) entity;
                ((MobEntityAccessor) mob).basedefense$getTargetSelector()
                        .add(3, new ActiveTargetGoal<>(mob, SettlerEntity.class, true));
            }
        });
    }

    /** Этот моб нападает на жителей (и жители от него убегают). */
    public static boolean isThreat(Entity entity) {
        return entity instanceof HostileEntity
                && !(entity instanceof Angerable)
                && !(entity instanceof CreeperEntity);
    }
}

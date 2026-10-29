package com.lesha_top1972.basedefense.mixin;

import net.minecraft.entity.ai.goal.GoalSelector;
import net.minecraft.entity.mob.MobEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/** Даёт доступ к целям мобов (поля targetSelector и goalSelector закрыты). */
@Mixin(MobEntity.class)
public interface MobEntityAccessor {

    @Accessor("targetSelector")
    GoalSelector basedefense$getTargetSelector();

    @Accessor("goalSelector")
    GoalSelector basedefense$getGoalSelector();
}

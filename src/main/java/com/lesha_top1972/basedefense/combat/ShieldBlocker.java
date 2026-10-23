package com.lesha_top1972.basedefense.combat;

/** Реализуется LivingEntityCombatMixin: был ли щитом заблокирован удар в этот тик (тогда без отброса). */
public interface ShieldBlocker {
    boolean basedefense$blockedThisTick();
}

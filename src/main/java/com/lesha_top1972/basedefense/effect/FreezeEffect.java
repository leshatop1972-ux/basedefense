package com.lesha_top1972.basedefense.effect;

import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.attribute.EntityAttributeModifier;
import net.minecraft.entity.attribute.EntityAttributes;
import net.minecraft.entity.effect.StatusEffect;
import net.minecraft.entity.effect.StatusEffectCategory;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.server.world.ServerWorld;

/**
 * «Заморозка»: нельзя двигаться (скорость ×0), прыгать (миксин LivingEntityMixin) и атаковать
 * (события в ModEffects, у мобов — MobEntityMixin). Экран белеет и синеет (клиент, FreezeOverlay).
 */
public class FreezeEffect extends StatusEffect {

    public FreezeEffect() {
        super(StatusEffectCategory.HARMFUL, 0x9FE3FF);
        this.addAttributeModifier(EntityAttributes.GENERIC_MOVEMENT_SPEED,
                "6d2f3b6c-4f0a-4a51-9a3c-1d6f2b7e0c11", -1.0, EntityAttributeModifier.Operation.MULTIPLY_TOTAL);
    }

    @Override
    public boolean canApplyUpdateEffect(int duration, int amplifier) {
        return duration % 4 == 0;
    }

    @Override
    public void applyUpdateEffect(LivingEntity entity, int amplifier) {
        if (entity.getWorld() instanceof ServerWorld sw) {
            sw.spawnParticles(ParticleTypes.SNOWFLAKE, entity.getX(), entity.getBodyY(0.5), entity.getZ(),
                    3, entity.getWidth() * 0.4, entity.getHeight() * 0.3, entity.getWidth() * 0.4, 0.01);
        }
    }
}

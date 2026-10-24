package com.lesha_top1972.basedefense.effect;

import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.attribute.EntityAttributeModifier;
import net.minecraft.entity.attribute.EntityAttributes;
import net.minecraft.entity.effect.StatusEffect;
import net.minecraft.entity.effect.StatusEffectCategory;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.server.world.ServerWorld;

/**
 * «Оглушение»: I — скорость −60%, II — −80%; оглушённый игрок не может атаковать никаким оружием
 * (ближний бой, лук, арбалет, трезубец — события в ModEffects). Над головой кружат искры.
 * Экран (клиент, StunOverlay): I — пульсирующая серая дымка по краям, II — экран сильно темнеет.
 */
public class StunEffect extends StatusEffect {

    public StunEffect() {
        super(StatusEffectCategory.HARMFUL, 0xC9B458);
        this.addAttributeModifier(EntityAttributes.GENERIC_MOVEMENT_SPEED,
                "3f5b2a90-0c1e-4b8e-9b77-6a1d2c4e8f21", -0.6, EntityAttributeModifier.Operation.MULTIPLY_TOTAL);
    }

    /** Уровень I — −60%, уровень II и выше — −80%. */
    @Override
    public double adjustModifierAmount(int amplifier, EntityAttributeModifier modifier) {
        return amplifier >= 1 ? -0.8 : -0.6;
    }

    @Override
    public boolean canApplyUpdateEffect(int duration, int amplifier) {
        return duration % 5 == 0;
    }

    @Override
    public void applyUpdateEffect(LivingEntity entity, int amplifier) {
        if (entity.getWorld() instanceof ServerWorld sw) {
            double a = entity.age * 0.6;
            sw.spawnParticles(ParticleTypes.CRIT, entity.getX() + Math.cos(a) * 0.45, entity.getY() + entity.getHeight() + 0.25,
                    entity.getZ() + Math.sin(a) * 0.45, 1, 0, 0, 0, 0);
        }
    }
}

package com.lesha_top1972.basedefense.entity;

import com.lesha_top1972.basedefense.sound.ModSounds;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.attribute.DefaultAttributeContainer;
import net.minecraft.entity.attribute.EntityAttributes;
import net.minecraft.entity.damage.DamageSource;
import net.minecraft.entity.mob.MobEntity;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundEvent;
import net.minecraft.sound.SoundEvents;
import net.minecraft.world.World;

/**
 * Яйцо осадного жука: кожистое, с хитиновыми пластинами, пульсирует тёплым светом. 20 HP, не двигается.
 * Через 15 с ({@link #HATCH_TICKS}) лопается — 2 маленьких жука.
 */
public class BeetleEggEntity extends MobEntity {

    public static final int HATCH_TICKS = 300;
    private int life;

    public BeetleEggEntity(EntityType<? extends MobEntity> type, World world) {
        super(type, world);
        this.setPersistent();
    }

    public static DefaultAttributeContainer.Builder createAttributes() {
        return MobEntity.createMobAttributes()
                .add(EntityAttributes.GENERIC_MAX_HEALTH, 20.0)
                .add(EntityAttributes.GENERIC_MOVEMENT_SPEED, 0.0)
                .add(EntityAttributes.GENERIC_KNOCKBACK_RESISTANCE, 1.0);
    }

    /** 0..1 — сколько прошло до вылупления (клиент пульсирует чаще к концу). */
    public int life() {
        return this.life;
    }

    @Override
    public void tick() {
        super.tick();
        World w = this.getWorld();
        this.life++;
        if (w.isClient) {
            boolean soon = this.life > HATCH_TICKS - 80;
            if (this.age % (soon ? 6 : 20) == 0) {
                w.addParticle(ParticleTypes.DRIPPING_HONEY, this.getX() + (this.random.nextDouble() - 0.5) * 0.6,
                        this.getY() + 0.5 + this.random.nextDouble() * 0.4, this.getZ() + (this.random.nextDouble() - 0.5) * 0.6, 0, 0, 0);
            }
            return;
        }
        if (this.life >= HATCH_TICKS) {
            this.hatch();
        }
    }

    private void hatch() {
        if (!(this.getWorld() instanceof ServerWorld s)) {
            return;
        }
        for (int i = 0; i < 2; i++) {
            BeetleMiteEntity mite = new BeetleMiteEntity(ModEntities.BEETLE_MITE, s);
            mite.refreshPositionAndAngles(this.getX() + (i == 0 ? -0.4 : 0.4), this.getY(), this.getZ(),
                    this.random.nextFloat() * 360f, 0f);
            s.spawnEntity(mite);
        }
        s.spawnParticles(ParticleTypes.ITEM_SLIME, this.getX(), this.getY() + 0.6, this.getZ(), 20, 0.35, 0.35, 0.35, 0.12);
        s.spawnParticles(ParticleTypes.CLOUD, this.getX(), this.getY() + 0.5, this.getZ(), 8, 0.3, 0.3, 0.3, 0.03);
        s.spawnParticles(ParticleTypes.CRIT, this.getX(), this.getY() + 0.6, this.getZ(), 10, 0.3, 0.3, 0.3, 0.2);
        this.playSound(ModSounds.BEETLE_HATCH, 1.0f, 1.0f);
        this.discard();
    }

    @Override
    protected SoundEvent getHurtSound(DamageSource source) {
        return SoundEvents.ENTITY_SLIME_HURT;
    }

    @Override
    protected SoundEvent getDeathSound() {
        return SoundEvents.ENTITY_SLIME_DEATH;
    }

    @Override
    public boolean isPushable() {
        return false;
    }

    @Override
    public void writeCustomDataToNbt(NbtCompound nbt) {
        super.writeCustomDataToNbt(nbt);
        nbt.putInt("Life", this.life);
    }

    @Override
    public void readCustomDataFromNbt(NbtCompound nbt) {
        super.readCustomDataFromNbt(nbt);
        this.life = nbt.getInt("Life");
    }
}

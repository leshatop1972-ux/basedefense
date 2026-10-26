package com.lesha_top1972.basedefense.entity;

import com.lesha_top1972.basedefense.sound.ModSounds;
import net.minecraft.block.BlockState;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.attribute.DefaultAttributeContainer;
import net.minecraft.entity.attribute.EntityAttributes;
import net.minecraft.entity.damage.DamageSource;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.entity.mob.MobEntity;
import net.minecraft.entity.mob.Monster;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.particle.BlockStateParticleEffect;
import net.minecraft.particle.DustParticleEffect;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvent;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;
import org.joml.Vector3f;

/**
 * Колокол‑опора Набата. Падает с неба (тень‑метка, удар 8 в радиусе 2), потом стоит на земле.
 * Пока жив хоть один — на Набате «Купол». Раз в 4 с гудит: слуги Набата рядом получают Сопротивление I.
 * Бить можно в любом порядке. 60 HP (в 3‑й фазе — 40).
 */
public class NabatBellEntity extends MobEntity {

    static final double FALL_SPEED = 1.1;
    static final float LAND_DAMAGE = 8f;
    static final double LAND_RADIUS = 2.0;
    static final int HUM_EVERY = 80;
    static final double HUM_RADIUS = 8.0;
    private static final DustParticleEffect AMBER = new DustParticleEffect(new Vector3f(1.0f, 0.7f, 0.3f), 1.0f);

    private boolean landed;
    private NabatEntity owner;

    public NabatBellEntity(EntityType<? extends MobEntity> type, World world) {
        super(type, world);
        this.setPersistent();
        this.addCommandTag(NabatHooks.TAG);
        this.addCommandTag(NabatHooks.NO_SOUL);
    }

    public static DefaultAttributeContainer.Builder createAttributes() {
        return MobEntity.createMobAttributes()
                .add(EntityAttributes.GENERIC_MAX_HEALTH, 60.0)
                .add(EntityAttributes.GENERIC_KNOCKBACK_RESISTANCE, 1.0)
                .add(EntityAttributes.GENERIC_MOVEMENT_SPEED, 0.0);
    }

    void setOwner(NabatEntity n, float hp) {
        this.owner = n;
        this.getAttributeInstance(EntityAttributes.GENERIC_MAX_HEALTH).setBaseValue(hp);
        this.setHealth(hp);
    }

    public boolean isLanded() {
        return this.landed;
    }

    @Override
    protected void initGoals() {
    }

    @Override
    public void tick() {
        super.tick();
        World w = this.getWorld();
        if (w.isClient) {
            return;
        }
        if (!this.landed) {
            this.setNoGravity(true);
            this.setVelocity(0, -FALL_SPEED, 0);
            this.velocityDirty = true;
            if (this.isOnGround() || this.verticalCollision || this.getY() < w.getBottomY()) {
                this.land();
            }
            return;
        }
        this.setVelocity(0, Math.min(0, this.getVelocity().y), 0);
        if ((this.age + this.getId()) % HUM_EVERY == 0) {
            this.hum();
        }
    }

    private void land() {
        this.landed = true;
        this.setNoGravity(false);
        this.setVelocity(Vec3d.ZERO);
        if (!(this.getWorld() instanceof ServerWorld s)) {
            return;
        }
        s.playSound(null, this.getX(), this.getY(), this.getZ(), ModSounds.NABAT_BELL_LAND, SoundCategory.HOSTILE, 2.2f, 1.0f);
        BlockState ground = s.getBlockState(BlockPos.ofFloored(this.getX(), this.getY() - 0.5, this.getZ()));
        for (int i = 0; i < 18; i++) {
            double a = i / 18.0 * Math.PI * 2;
            double x = this.getX() + Math.cos(a) * 1.3;
            double z = this.getZ() + Math.sin(a) * 1.3;
            if (!ground.isAir()) {
                s.spawnParticles(new BlockStateParticleEffect(ParticleTypes.BLOCK, ground), x, this.getY() + 0.1, z, 2,
                        0.1, 0.05, 0.1, 0.1);
            }
            s.spawnParticles(ParticleTypes.CLOUD, x, this.getY() + 0.15, z, 1, 0, 0.02, 0, 0.03);
        }
        s.spawnParticles(AMBER, this.getX(), this.getY() + 1.0, this.getZ(), 16, 0.5, 0.6, 0.5, 0.02);
        LivingEntity src = this.owner != null ? this.owner : this;
        for (LivingEntity e : s.getEntitiesByClass(LivingEntity.class, this.getBoundingBox().expand(LAND_RADIUS + 0.5, 1.5,
                LAND_RADIUS + 0.5), e -> e != this && e.isAlive() && !(e instanceof Monster) && !NabatHooks.isKin(e)
                && !(e instanceof PlayerEntity p && (p.isCreative() || p.isSpectator())))) {
            if (e.squaredDistanceTo(this) <= (LAND_RADIUS + 0.6) * (LAND_RADIUS + 0.6)) {
                e.damage(this.getDamageSources().mobAttack(src), LAND_DAMAGE);
                e.takeKnockback(0.8, this.getX() - e.getX(), this.getZ() - e.getZ());
            }
        }
    }

    /** Гул: слуги рядом крепче (Сопротивление I на 5 с), тусклое кольцо и низкий звук. */
    private void hum() {
        if (!(this.getWorld() instanceof ServerWorld s)) {
            return;
        }
        if ((this.age / HUM_EVERY) % 2 == 0) {
            s.playSound(null, this.getX(), this.getY(), this.getZ(), ModSounds.NABAT_BELL_HUM, SoundCategory.HOSTILE, 0.8f,
                    0.9f + this.random.nextFloat() * 0.15f);
        }
        for (int i = 0; i < 24; i++) {
            double a = i / 24.0 * Math.PI * 2;
            s.spawnParticles(AMBER, this.getX() + Math.cos(a) * 1.6, this.getY() + 0.2, this.getZ() + Math.sin(a) * 1.6, 1,
                    0, 0, 0, 0);
        }
        Box box = this.getBoundingBox().expand(HUM_RADIUS);
        for (LivingEntity e : s.getEntitiesByClass(LivingEntity.class, box, e -> e.isAlive() && NabatHooks.isKin(e)
                && !(e instanceof NabatEntity) && !(e instanceof NabatBellEntity))) {
            e.addStatusEffect(new StatusEffectInstance(StatusEffects.RESISTANCE, 100, 0, true, true, true));
        }
    }

    @Override
    public boolean damage(DamageSource source, float amount) {
        if (source.getAttacker() != null && NabatHooks.isKin(source.getAttacker())) {
            return false;
        }
        return super.damage(source, amount);
    }

    @Override
    protected void updatePostDeath() {
        if (this.deathTime == 0 && this.getWorld() instanceof ServerWorld s) {
            s.playSound(null, this.getX(), this.getY(), this.getZ(), ModSounds.NABAT_BELL_BREAK, SoundCategory.HOSTILE,
                    2.0f, 1.0f);
            s.spawnParticles(AMBER, this.getX(), this.getY() + 0.8, this.getZ(), 30, 0.6, 0.7, 0.6, 0.1);
            s.spawnParticles(ParticleTypes.END_ROD, this.getX(), this.getY() + 0.8, this.getZ(), 10, 0.4, 0.5, 0.4, 0.08);
            BlockState copper = net.minecraft.block.Blocks.COPPER_BLOCK.getDefaultState();
            s.spawnParticles(new BlockStateParticleEffect(ParticleTypes.BLOCK, copper), this.getX(), this.getY() + 0.8,
                    this.getZ(), 40, 0.5, 0.6, 0.5, 0.2);
            this.discard();
        }
        super.updatePostDeath();
    }

    @Override
    public boolean isPushable() {
        return false;
    }

    @Override
    protected void pushAway(net.minecraft.entity.Entity entity) {
    }

    @Override
    public boolean canImmediatelyDespawn(double distanceSquared) {
        return false;
    }

    @Override
    public boolean isFireImmune() {
        return true;
    }

    @Override
    public boolean handleFallDamage(float fallDistance, float damageMultiplier, DamageSource damageSource) {
        return false;
    }

    @Override
    protected SoundEvent getHurtSound(DamageSource source) {
        return ModSounds.NABAT_CHIME;
    }

    @Override
    protected SoundEvent getDeathSound() {
        return null;
    }

    @Override
    public void writeCustomDataToNbt(NbtCompound nbt) {
        super.writeCustomDataToNbt(nbt);
        nbt.putBoolean("Landed", this.landed);
    }

    @Override
    public void readCustomDataFromNbt(NbtCompound nbt) {
        super.readCustomDataFromNbt(nbt);
        this.landed = nbt.getBoolean("Landed");
    }
}

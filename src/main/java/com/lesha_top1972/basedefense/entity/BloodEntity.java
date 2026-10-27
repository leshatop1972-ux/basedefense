package com.lesha_top1972.basedefense.entity;

import com.lesha_top1972.basedefense.effect.ModEffects;
import com.lesha_top1972.basedefense.sound.ModSounds;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.attribute.DefaultAttributeContainer;
import net.minecraft.entity.attribute.EntityAttributes;
import net.minecraft.entity.damage.DamageSource;
import net.minecraft.entity.mob.HostileEntity;
import net.minecraft.particle.DustParticleEffect;
import net.minecraft.sound.SoundEvent;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;
import org.joml.Vector3f;

import java.util.Set;

/**
 * «Кровавая сущность»: как человек, чёрная в красных трещинах. 65 HP. Удар рукой (6) и «Бросок» — присед,
 * прыжок к цели и удар обеими руками сверху (9), раз в 8 с, если цель в 3–7 блоках. Каждый удар — «Кровотёк».
 */
public class BloodEntity extends HorrorEntity {

    static final int LUNGE_TICKS = 24;
    static final int LUNGE_JUMP = 9;
    static final int LUNGE_HIT = 11;
    static final int LUNGE_CD = 160;
    private static final DustParticleEffect DRIP = new DustParticleEffect(new Vector3f(0.55f, 0.03f, 0.06f), 0.8f);
    private int lungeCd = 60;

    public BloodEntity(EntityType<? extends HostileEntity> type, World world) {
        super(type, world);
    }

    public static DefaultAttributeContainer.Builder createAttributes() {
        return HostileEntity.createHostileAttributes()
                .add(EntityAttributes.GENERIC_MAX_HEALTH, 65.0)
                .add(EntityAttributes.GENERIC_MOVEMENT_SPEED, 0.27)
                .add(EntityAttributes.GENERIC_FOLLOW_RANGE, 32.0)
                .add(EntityAttributes.GENERIC_ATTACK_DAMAGE, 6.0);
    }

    @Override
    protected int attackTicks() {
        return 16;
    }

    @Override
    protected int attackHit() {
        return 7;
    }

    @Override
    protected float attackDamage() {
        return 6f;
    }

    @Override
    protected void onHit(LivingEntity target) {
        ModEffects.addBleed(target, this);
    }

    @Override
    protected void attackSound(int action) {
        this.playSound(action == LUNGE ? ModSounds.BLOOD_HISS : ModSounds.BLOOD_ATTACK, 1.0f, 0.9f + this.random.nextFloat() * 0.2f);
    }

    @Override
    protected boolean canLunge(double dist) {
        return this.lungeCd <= 0 && dist >= 3.0 && dist <= 7.0 && this.isOnGround();
    }

    @Override
    protected int lungeTicks() {
        return LUNGE_TICKS;
    }

    @Override
    protected void runLunge(int tick, LivingEntity target, Set<Integer> hit) {
        if (target != null && tick < LUNGE_JUMP) {
            this.faceTo(target);
        }
        if (tick == LUNGE_JUMP) {
            this.lungeCd = LUNGE_CD;
            Vec3d f = this.forward();
            double sp = target != null ? Math.min(1.0, 0.25 + this.distanceTo(target) * 0.12) : 0.7;
            this.setVelocity(f.x * sp, 0.38, f.z * sp);
            this.velocityModified = true;
            this.playSound(ModSounds.BLOOD_ATTACK, 1.2f, 0.8f);
        }
        if (tick >= LUNGE_HIT && tick <= LUNGE_HIT + 3) {
            this.coneHit(1.8, 9f, hit);
        }
    }

    @Override
    public void tick() {
        super.tick();
        if (this.lungeCd > 0) {
            this.lungeCd--;
        }
        if (this.getWorld().isClient && this.random.nextInt(8) == 0) {
            Vec3d f = this.forward();
            double side = this.random.nextBoolean() ? 0.35 : -0.35;
            this.getWorld().addParticle(DRIP, this.getX() + f.z * side, this.getY() + 0.75, this.getZ() - f.x * side, 0, -0.05, 0);
        }
    }

    @Override
    protected SoundEvent getAmbientSound() {
        return ModSounds.BLOOD_GROWL;
    }

    @Override
    public int getMinAmbientSoundDelay() {
        return 200;
    }

    @Override
    protected SoundEvent getHurtSound(DamageSource source) {
        return ModSounds.BLOOD_HURT;
    }

    @Override
    protected SoundEvent getDeathSound() {
        return ModSounds.BLOOD_DEATH;
    }
}

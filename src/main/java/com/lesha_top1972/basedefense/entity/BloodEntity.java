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
 * «Кровавая сущность»: как человек, чёрная в красных трещинах. 65 HP. Удар рукой (6) и «Двойной разрыв» — вплотную,
 * на месте, левой и правой по 5, раз в 6 с. Каждый удар — «Кровотёк». Тяжёлые мокрые шаги.
 */
public class BloodEntity extends HorrorEntity {

    /** «Двойной разрыв»: на месте, левой (тик 7) и правой (тик 13), по 5 урона, раз в 6 с. */
    static final int REND_TICKS = 20;
    static final int[] REND_HITS = {7, 13};
    static final float REND_DMG = 5f;
    static final int REND_CD = 120;
    private static final DustParticleEffect DRIP = new DustParticleEffect(new Vector3f(0.55f, 0.03f, 0.06f), 0.8f);
    private int rendCd = 60;

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
        return this.rendCd <= 0 && dist < 1.4;
    }

    @Override
    protected int lungeTicks() {
        return REND_TICKS;
    }

    @Override
    protected void runLunge(int tick, LivingEntity target, Set<Integer> hit) {
        if (target != null && tick < REND_HITS[1] - 1) {
            this.faceTo(target);
        }
        if (tick == 1) {
            this.rendCd = REND_CD;
        }
        for (int h : REND_HITS) {
            if (tick == h - 2) {
                hit.clear();
                this.playSound(ModSounds.BLOOD_ATTACK, 0.9f, 1.0f + this.random.nextFloat() * 0.2f);
            }
            if (tick >= h && tick <= h + 1) {
                this.coneHit(1.6, REND_DMG, hit);
            }
        }
    }

    @Override
    protected void playStepSound(net.minecraft.util.math.BlockPos pos, net.minecraft.block.BlockState state) {
        this.playSound(ModSounds.BLOOD_STEP, 0.4f, 0.85f + this.random.nextFloat() * 0.2f);
    }

    @Override
    public void tick() {
        super.tick();
        if (this.rendCd > 0) {
            this.rendCd--;
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

package com.lesha_top1972.basedefense.entity.enemy.bloodthirster;

import com.lesha_top1972.basedefense.effect.ModEffects;
import net.minecraft.block.BlockState;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.ai.goal.ActiveTargetGoal;
import net.minecraft.entity.ai.goal.LookAroundGoal;
import net.minecraft.entity.ai.goal.LookAtEntityGoal;
import net.minecraft.entity.ai.goal.MeleeAttackGoal;
import net.minecraft.entity.ai.goal.RevengeGoal;
import net.minecraft.entity.ai.goal.SwimGoal;
import net.minecraft.entity.ai.goal.WanderAroundFarGoal;
import net.minecraft.entity.attribute.DefaultAttributeContainer;
import net.minecraft.entity.attribute.EntityAttributes;
import net.minecraft.entity.damage.DamageSource;
import net.minecraft.entity.mob.HostileEntity;
import net.minecraft.entity.passive.MerchantEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.sound.SoundEvent;
import net.minecraft.sound.SoundEvents;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;

/** «Мелкий кровожад» — выводок босса: 45 HP, скорость зомби, бьёт когтями (4). */
public class BloodlingEntity extends HostileEntity implements BloodKin {

    public BloodlingEntity(EntityType<? extends HostileEntity> type, World world) {
        super(type, world);
        this.experiencePoints = 6;
    }

    public static DefaultAttributeContainer.Builder createAttributes() {
        return HostileEntity.createHostileAttributes()
                .add(EntityAttributes.GENERIC_MAX_HEALTH, 45.0)
                .add(EntityAttributes.GENERIC_MOVEMENT_SPEED, 0.23)
                .add(EntityAttributes.GENERIC_ATTACK_DAMAGE, 4.0)
                .add(EntityAttributes.GENERIC_FOLLOW_RANGE, 32.0);
    }

    @Override
    protected void initGoals() {
        this.goalSelector.add(0, new SwimGoal(this));
        this.goalSelector.add(2, new MeleeAttackGoal(this, 1.0, false));
        this.goalSelector.add(7, new WanderAroundFarGoal(this, 0.8));
        this.goalSelector.add(8, new LookAtEntityGoal(this, PlayerEntity.class, 8.0f));
        this.goalSelector.add(8, new LookAroundGoal(this));
        this.targetSelector.add(1, new RevengeGoal(this, BloodthirsterEntity.class));
        this.targetSelector.add(2, new ActiveTargetGoal<>(this, PlayerEntity.class, true));
        this.targetSelector.add(3, new ActiveTargetGoal<>(this, MerchantEntity.class, true));
    }

    @Override
    public boolean tryAttack(Entity target) {
        boolean hit = super.tryAttack(target);
        if (hit) {
            BloodFx.splash(this.getWorld(), target.getX(), target.getBodyY(0.6), target.getZ(), 6, 0.25);
            // кровотёк: 15% за попадание (повторное поднимает уровень, как у босса)
            if (target instanceof LivingEntity living && this.random.nextFloat() < 0.15f) {
                ModEffects.addBleed(living, this);
            }
        }
        return hit;
    }

    @Override
    public boolean damage(DamageSource source, float amount) {
        if (source.getAttacker() instanceof BloodKin) {
            return false;
        }
        boolean hurt = super.damage(source, amount);
        if (hurt) {
            BloodFx.splash(this.getWorld(), this.getX(), this.getBodyY(0.5), this.getZ(), 4, 0.2);
        }
        return hurt;
    }

    @Override
    public void onDeath(DamageSource source) {
        super.onDeath(source);
        BloodFx.splash(this.getWorld(), this.getX(), this.getBodyY(0.4), this.getZ(), 20, 0.5);
    }

    @Override
    protected SoundEvent getAmbientSound() {
        return SoundEvents.ENTITY_HUSK_AMBIENT;
    }

    @Override
    protected SoundEvent getHurtSound(DamageSource source) {
        return SoundEvents.ENTITY_HUSK_HURT;
    }

    @Override
    protected SoundEvent getDeathSound() {
        return SoundEvents.ENTITY_HUSK_DEATH;
    }

    @Override
    public float getSoundPitch() {
        return 1.5f + this.random.nextFloat() * 0.2f;
    }

    @Override
    protected void playStepSound(BlockPos pos, BlockState state) {
        this.playSound(SoundEvents.ENTITY_HUSK_STEP, 0.3f, 1.4f);
    }
}

package com.lesha_top1972.basedefense.entity;

import net.minecraft.block.BlockState;
import net.minecraft.entity.AnimationState;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.LivingEntity;
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
import net.minecraft.entity.passive.IronGolemEntity;
import net.minecraft.entity.passive.MerchantEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.sound.SoundEvent;
import net.minecraft.sound.SoundEvents;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;

/**
 * Железный зомби: 40 HP, удар ножом 5 урона раз в секунду, скорость обычного зомби.
 * Удар наносится не сразу, а в момент взмаха анимации (через 8 тиков после начала).
 * На солнце не горит. На жителей нападает (см. SettlerThreats).
 */
public class IronZombieEntity extends HostileEntity {

    /** Начать анимацию удара (сервер → клиент). */
    public static final byte STATUS_ATTACK = 4;
    /** Через сколько тиков после начала анимации нож попадает. */
    static final int HIT_DELAY = 8;
    /** Перезарядка удара: 1 секунда. */
    static final int ATTACK_COOLDOWN = 20;
    /** Дальность удара от края хитбокса (блоки). */
    static final double REACH = 1.8;

    /** Только на клиенте: анимация удара. */
    public final AnimationState attackState = new AnimationState();

    public IronZombieEntity(EntityType<? extends HostileEntity> type, World world) {
        super(type, world);
        this.experiencePoints = 8;
    }

    public static DefaultAttributeContainer.Builder createAttributes() {
        return HostileEntity.createHostileAttributes()
                .add(EntityAttributes.GENERIC_MAX_HEALTH, 40.0)
                .add(EntityAttributes.GENERIC_ATTACK_DAMAGE, 5.0)
                .add(EntityAttributes.GENERIC_MOVEMENT_SPEED, 0.23)
                .add(EntityAttributes.GENERIC_FOLLOW_RANGE, 35.0)
                .add(EntityAttributes.GENERIC_KNOCKBACK_RESISTANCE, 0.2);
    }

    @Override
    protected void initGoals() {
        this.goalSelector.add(0, new SwimGoal(this));
        this.goalSelector.add(2, new AttackGoal(this));
        this.goalSelector.add(7, new WanderAroundFarGoal(this, 1.0));
        this.goalSelector.add(8, new LookAtEntityGoal(this, PlayerEntity.class, 8.0f));
        this.goalSelector.add(8, new LookAroundGoal(this));

        this.targetSelector.add(1, new RevengeGoal(this));
        this.targetSelector.add(2, new ActiveTargetGoal<>(this, PlayerEntity.class, true));
        this.targetSelector.add(3, new ActiveTargetGoal<>(this, MerchantEntity.class, false));
        this.targetSelector.add(3, new ActiveTargetGoal<>(this, IronGolemEntity.class, true));
        // Жители поселения добавляются в SettlerThreats
    }

    @Override
    public void handleStatus(byte status) {
        if (status == STATUS_ATTACK) {
            this.attackState.start(this.age);
        } else {
            super.handleStatus(status);
        }
    }

    @Override
    protected SoundEvent getAmbientSound() {
        return SoundEvents.ENTITY_ZOMBIE_AMBIENT;
    }

    @Override
    protected SoundEvent getHurtSound(DamageSource source) {
        return SoundEvents.ENTITY_ZOMBIE_HURT;
    }

    @Override
    protected SoundEvent getDeathSound() {
        return SoundEvents.ENTITY_ZOMBIE_DEATH;
    }

    @Override
    protected void playStepSound(BlockPos pos, BlockState state) {
        this.playSound(SoundEvents.BLOCK_CHAIN_STEP, 0.5f, 0.9f);
    }

    /** Ближний бой: анимация, через 8 тиков — удар, если цель ещё рядом. */
    static class AttackGoal extends MeleeAttackGoal {
        private int cooldown;
        private int windup = -1;
        private LivingEntity pending;

        AttackGoal(IronZombieEntity mob) {
            super(mob, 1.0, false);
        }

        /** Дальность удара: ~1.8 блока от края хитбокса (у обычного зомби меньше). */
        @Override
        protected double getSquaredMaxAttackDistance(LivingEntity entity) {
            double r = this.mob.getWidth() / 2.0 + REACH + entity.getWidth() / 2.0;
            return r * r;
        }

        @Override
        public void stop() {
            super.stop();
            this.windup = -1;
            this.pending = null;
        }

        @Override
        protected void attack(LivingEntity target, double squaredDistance) {
            if (this.cooldown > 0) {
                this.cooldown--;
            }
            if (this.windup > 0 && --this.windup == 0) {
                LivingEntity t = this.pending;
                this.pending = null;
                if (t != null && t.isAlive()
                        && this.mob.squaredDistanceTo(t) <= this.getSquaredMaxAttackDistance(t) * 1.5) {
                    this.mob.tryAttack(t);
                }
            }
            if (this.cooldown <= 0 && squaredDistance <= this.getSquaredMaxAttackDistance(target)) {
                this.cooldown = ATTACK_COOLDOWN;
                this.windup = HIT_DELAY;
                this.pending = target;
                this.mob.getWorld().sendEntityStatus(this.mob, STATUS_ATTACK);
            }
        }
    }
}

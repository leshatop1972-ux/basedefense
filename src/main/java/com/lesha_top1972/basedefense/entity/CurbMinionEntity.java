package com.lesha_top1972.basedefense.entity;

import net.minecraft.block.BlockState;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.ai.goal.ActiveTargetGoal;
import net.minecraft.entity.ai.goal.LookAroundGoal;
import net.minecraft.entity.ai.goal.LookAtEntityGoal;
import net.minecraft.entity.ai.goal.RevengeGoal;
import net.minecraft.entity.ai.goal.SwimGoal;
import net.minecraft.entity.ai.goal.WanderAroundFarGoal;
import net.minecraft.entity.attribute.DefaultAttributeContainer;
import net.minecraft.entity.attribute.EntityAttributes;
import net.minecraft.entity.damage.DamageSource;
import net.minecraft.entity.data.DataTracker;
import net.minecraft.entity.data.TrackedData;
import net.minecraft.entity.data.TrackedDataHandlerRegistry;
import net.minecraft.entity.mob.HostileEntity;
import net.minecraft.entity.passive.IronGolemEntity;
import net.minecraft.entity.passive.MerchantEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.sound.SoundEvent;
import net.minecraft.sound.SoundEvents;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;

/**
 * Прислужник Бордюра: серый боец в капюшоне. Бегает со скоростью ≈85% бега игрока, бьёт «серией» (по 1.5)
 * и «реактивным ударом» (9.5) — реже, чем босс. Призванный — выбирается из‑под земли (1.6 с, неуязвим).
 */
public class CurbMinionEntity extends AbstractCurbEntity {

    private static final TrackedData<Boolean> SUMMONED =
            DataTracker.registerData(CurbMinionEntity.class, TrackedDataHandlerRegistry.BOOLEAN);
    public static final int EMERGE_TICKS = 32;

    public CurbMinionEntity(EntityType<? extends HostileEntity> type, World world) {
        super(type, world);
        this.experiencePoints = 12;
    }

    public static DefaultAttributeContainer.Builder createAttributes() {
        return HostileEntity.createHostileAttributes()
                .add(EntityAttributes.GENERIC_MAX_HEALTH, 70.0)
                .add(EntityAttributes.GENERIC_MOVEMENT_SPEED, 0.25)
                .add(EntityAttributes.GENERIC_FOLLOW_RANGE, 32.0)
                .add(EntityAttributes.GENERIC_ATTACK_DAMAGE, 1.5)
                .add(EntityAttributes.GENERIC_ARMOR, 4.0)
                .add(EntityAttributes.GENERIC_KNOCKBACK_RESISTANCE, 0.3);
    }

    @Override
    protected void initDataTracker() {
        super.initDataTracker();
        this.dataTracker.startTracking(SUMMONED, false);
    }

    void setSummoned() {
        this.dataTracker.set(SUMMONED, true);
    }

    /** Ещё выбирается из‑под земли (и сервер, и клиент: возраст считается одинаково с появления). */
    public boolean isEmerging() {
        return this.dataTracker.get(SUMMONED) && this.age < EMERGE_TICKS;
    }

    @Override
    float comboDamage() {
        return CurbEntity.COMBO_DAMAGE - 1.5f;
    }

    @Override
    float jetDamage() {
        return CurbEntity.JET_DAMAGE - 1.5f;
    }

    @Override
    public float modelScale() {
        return 1.0f;
    }

    @Override
    protected void initGoals() {
        this.goalSelector.add(0, new SwimGoal(this));
        this.goalSelector.add(2, new MinionGoal(this));
        this.goalSelector.add(7, new WanderAroundFarGoal(this, 0.8));
        this.goalSelector.add(8, new LookAtEntityGoal(this, PlayerEntity.class, 12.0f));
        this.goalSelector.add(8, new LookAroundGoal(this));

        this.targetSelector.add(1, new RevengeGoal(this, CurbEntity.class, CurbMinionEntity.class));
        this.targetSelector.add(2, new ActiveTargetGoal<>(this, PlayerEntity.class, true));
        this.targetSelector.add(3, new ActiveTargetGoal<>(this, MerchantEntity.class, true));
        this.targetSelector.add(3, new ActiveTargetGoal<>(this, IronGolemEntity.class, true));
    }

    @Override
    public void tick() {
        if (this.isEmerging()) {
            this.setVelocity(0, Math.min(0, this.getVelocity().y), 0);
            if (!this.getWorld().isClient && this.age % 4 == 0 && this.getWorld() instanceof net.minecraft.server.world.ServerWorld s) {
                BlockState ground = this.getWorld().getBlockState(this.getBlockPos().down());
                if (!ground.isAir()) {
                    s.spawnParticles(new net.minecraft.particle.BlockStateParticleEffect(ParticleTypes.BLOCK, ground),
                            this.getX(), this.getY() + 0.1, this.getZ(), 8, 0.35, 0.05, 0.35, 0.1);
                }
            }
        }
        super.tick();
    }

    @Override
    public boolean damage(DamageSource source, float amount) {
        if (this.isEmerging() && !source.isOf(net.minecraft.entity.damage.DamageTypes.OUT_OF_WORLD)) {
            return false;
        }
        return super.damage(source, amount);
    }

    @Override
    public void writeCustomDataToNbt(NbtCompound nbt) {
        super.writeCustomDataToNbt(nbt);
        nbt.putBoolean("Summoned", this.dataTracker.get(SUMMONED));
    }

    @Override
    public void readCustomDataFromNbt(NbtCompound nbt) {
        super.readCustomDataFromNbt(nbt);
        this.dataTracker.set(SUMMONED, nbt.getBoolean("Summoned"));
    }

    @Override
    protected SoundEvent getAmbientSound() {
        return SoundEvents.ENTITY_IRON_GOLEM_STEP;
    }

    @Override
    protected SoundEvent getHurtSound(DamageSource source) {
        return SoundEvents.ENTITY_IRON_GOLEM_HURT;
    }

    @Override
    protected SoundEvent getDeathSound() {
        return SoundEvents.ENTITY_IRON_GOLEM_DEATH;
    }

    @Override
    public float getSoundPitch() {
        return 1.25f + this.random.nextFloat() * 0.1f;
    }

    @Override
    protected void playStepSound(BlockPos pos, BlockState state) {
        this.playSound(SoundEvents.ENTITY_IRON_GOLEM_STEP, 0.2f, 1.6f);
    }

    /** ИИ прислужника: те же «серия» и «реактивный удар», но перерывы и перезарядка длиннее. */
    static class MinionGoal extends BrawlGoal<CurbMinionEntity> {
        MinionGoal(CurbMinionEntity mob) {
            super(mob);
            this.jetCooldown = 100;
        }

        @Override
        public boolean canStart() {
            return !this.mob.isEmerging() && super.canStart();
        }

        @Override
        double chaseSpeed() {
            return 1.29;                                   // 0.25 × 1.29 ≈ 85% бега игрока
        }

        @Override
        int restTicks(int finished) {
            return 40 + this.mob.random.nextInt(31);
        }

        @Override
        int jetCooldownTicks() {
            return 180;
        }

        @Override
        protected void options(java.util.List<int[]> out, double dist) {
            super.options(out, dist);
            for (int[] o : out) {
                if (o[0] == JET) {
                    o[1] = o[1] / 2;
                }
            }
        }

        @Override
        protected boolean preAction(LivingEntity target, double dist) {
            return this.mob.isEmerging();
        }
    }
}

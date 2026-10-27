package com.lesha_top1972.basedefense.entity;

import com.lesha_top1972.basedefense.sound.ModSounds;
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
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.entity.mob.HostileEntity;
import net.minecraft.entity.passive.IronGolemEntity;
import net.minecraft.entity.passive.MerchantEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.particle.BlockStateParticleEffect;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundEvent;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;

/**
 * Железный зомби: 40 HP, удар ножом 5 урона раз в секунду, скорость обычного зомби.
 * Удар наносится не сразу, а в момент взмаха анимации (через 8 тиков после начала).
 * На солнце не горит. На жителей нападает (см. SettlerThreats).
 * «Рывок‑хват» (раз в 12–15 с, цель в 3–6 блоках): замах 0.45 с, рывок (≈4 блока), при попадании держит
 * цель 1 с (без урона: тянет к себе и сильно замедляет), потом удар ножом обычной силы.
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

    /** Начать «Рывок‑хват» (клип lunge). */
    public static final byte STATUS_LUNGE = 5;
    /** Рывок попал — схватил (клип grab). */
    public static final byte STATUS_GRAB = 6;
    /** Тики рывка: замах до DASH_START, рывок до DASH_END, промах — восстановление до LUNGE_END. */
    static final int DASH_START = 9;
    static final int DASH_END = 16;
    static final int LUNGE_END = 30;
    /** Скорость рывка (блоков за тик): 7 тиков × 0.62 ≈ 4.3 блока. */
    static final double DASH_SPEED = 0.62;
    /** Тики хвата: держит HOLD тиков, удар на GRAB_HIT, конец на GRAB_END (клип grab 1.9 с). */
    static final int HOLD = 20;
    static final int GRAB_HIT = 27;
    static final int GRAB_END = 38;
    static final double RUSH_MIN = 3.0;
    static final double RUSH_MAX = 6.0;

    /** Только на клиенте: анимация удара. */
    public final AnimationState attackState = new AnimationState();
    /** Только на клиенте: рывок и хват. */
    public final AnimationState lungeState = new AnimationState();
    public final AnimationState grabState = new AnimationState();

    /** Сервер: −1 — рывка нет; иначе тик рывка (или хвата, если grabbing). */
    private int rushTick = -1;
    private boolean grabbing;
    private LivingEntity rushTarget;
    private int rushCooldown = 100;

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
        } else if (status == STATUS_LUNGE) {
            this.attackState.stop();
            this.grabState.stop();
            this.lungeState.start(this.age);
        } else if (status == STATUS_GRAB) {
            this.grabState.start(this.age);
        } else {
            super.handleStatus(status);
        }
    }

    @Override
    protected SoundEvent getAmbientSound() {
        return ModSounds.IRON_ZOMBIE_AMBIENT;
    }

    @Override
    protected SoundEvent getHurtSound(DamageSource source) {
        return ModSounds.IRON_ZOMBIE_HURT;
    }

    @Override
    protected SoundEvent getDeathSound() {
        return ModSounds.IRON_ZOMBIE_DEATH;
    }

    @Override
    protected void playStepSound(BlockPos pos, BlockState state) {
        this.playSound(ModSounds.IRON_ZOMBIE_STEP, 0.45f, 0.95f + this.random.nextFloat() * 0.1f);
    }

    @Override
    public boolean tryAttack(net.minecraft.entity.Entity target) {
        boolean hit = super.tryAttack(target);
        if (hit) {
            this.playSound(ModSounds.IRON_ZOMBIE_ATTACK, 0.9f, 0.95f + this.random.nextFloat() * 0.1f);
        }
        return hit;
    }

    /** Занят «Рывком‑хватом» — обычные удары и ходьба ждут. */
    boolean isRushing() {
        return this.rushTick >= 0;
    }

    /** Можно ли начать рывок по цели. */
    boolean canRush(LivingEntity target) {
        if (this.rushCooldown > 0 || this.rushTick >= 0 || !this.isOnGround() || !this.canSee(target)) {
            return false;
        }
        double d = Math.sqrt(this.squaredDistanceTo(target)) - target.getWidth() / 2.0;
        return d >= RUSH_MIN && d <= RUSH_MAX && Math.abs(target.getY() - this.getY()) < 2.0;
    }

    void startRush(LivingEntity target) {
        this.rushTarget = target;
        this.rushTick = 0;
        this.grabbing = false;
        this.rushCooldown = 240 + this.random.nextInt(61);   // 12–15 с
        this.getNavigation().stop();
        this.getWorld().sendEntityStatus(this, STATUS_LUNGE);
        this.playSound(ModSounds.IRON_ZOMBIE_LUNGE, 1.0f, 0.95f + this.random.nextFloat() * 0.1f);
    }

    private void endRush() {
        this.rushTick = -1;
        this.grabbing = false;
        this.rushTarget = null;
    }

    private void faceToward(LivingEntity t, float maxStep) {
        double dx = t.getX() - this.getX();
        double dz = t.getZ() - this.getZ();
        float want = (float) (MathHelper.atan2(dz, dx) * 180.0 / Math.PI) - 90f;
        float yaw = this.getYaw() + MathHelper.clamp(MathHelper.wrapDegrees(want - this.getYaw()), -maxStep, maxStep);
        this.setYaw(yaw);
        this.bodyYaw = yaw;
        this.headYaw = yaw;
    }

    @Override
    protected void mobTick() {
        super.mobTick();
        if (this.rushCooldown > 0) {
            this.rushCooldown--;
        }
        if (this.rushTick < 0) {
            return;
        }
        LivingEntity t = this.rushTarget;
        boolean valid = t != null && t.isAlive() && !t.isRemoved() && t.getWorld() == this.getWorld()
                && !(t instanceof PlayerEntity p && (p.isCreative() || p.isSpectator()));
        int tick = this.rushTick++;
        this.getNavigation().stop();
        if (!this.grabbing) {
            if (valid) {
                this.faceToward(t, tick < DASH_START ? 25f : 10f);
                this.getLookControl().lookAt(t, 30f, 30f);
            }
            if (tick >= DASH_START && tick < DASH_END && valid) {
                if (tick == DASH_START) {
                    this.playSound(ModSounds.IRON_ZOMBIE_SCRAPE, 0.8f, 1.0f);
                    this.dustBurst(6);
                }
                float yawRad = this.getYaw() * MathHelper.RADIANS_PER_DEGREE;
                Vec3d v = this.getVelocity();
                this.setVelocity(-MathHelper.sin(yawRad) * DASH_SPEED, Math.min(v.y, 0.0), MathHelper.cos(yawRad) * DASH_SPEED);
                this.velocityDirty = true;
                double reach = this.getWidth() / 2.0 + t.getWidth() / 2.0 + 0.8;
                if (this.squaredDistanceTo(t.getX(), this.getY(), t.getZ()) <= reach * reach
                        && Math.abs(t.getY() - this.getY()) < 1.8) {
                    this.grabbing = true;
                    this.rushTick = 0;
                    this.setVelocity(Vec3d.ZERO);
                    this.getWorld().sendEntityStatus(this, STATUS_GRAB);
                    this.playSound(ModSounds.IRON_ZOMBIE_GRAB, 1.0f, 1.0f);
                    if (this.getWorld() instanceof ServerWorld sw) {
                        sw.spawnParticles(ParticleTypes.CRIT, t.getX(), t.getBodyY(0.6), t.getZ(), 8, 0.3, 0.3, 0.3, 0.1);
                    }
                }
            } else if (tick >= DASH_END) {
                this.setVelocity(this.getVelocity().multiply(0.5, 1.0, 0.5));
            }
            if (!this.grabbing && (tick >= LUNGE_END || (!valid && tick >= DASH_START))) {
                this.endRush();
            }
            return;
        }
        // --- держит
        if (!valid) {
            this.endRush();
            return;
        }
        this.faceToward(t, 15f);
        this.getLookControl().lookAt(t, 30f, 30f);
        this.setVelocity(0, Math.min(this.getVelocity().y, 0.0), 0);
        if (tick < HOLD) {
            float yawRad = this.getYaw() * MathHelper.RADIANS_PER_DEGREE;
            double gap = this.getWidth() / 2.0 + t.getWidth() / 2.0 + 0.25;
            Vec3d hold = new Vec3d(this.getX() - MathHelper.sin(yawRad) * gap, t.getY(), this.getZ() + MathHelper.cos(yawRad) * gap);
            Vec3d d = hold.subtract(t.getPos());
            t.setVelocity(d.x * 0.5, Math.min(t.getVelocity().y, 0.1), d.z * 0.5);
            t.velocityModified = true;
            t.addStatusEffect(new StatusEffectInstance(StatusEffects.SLOWNESS, 3, 5, false, false, false));
            if (tick % 6 == 0) {
                this.playSound(ModSounds.IRON_ZOMBIE_SCRAPE, 0.35f, 1.2f + this.random.nextFloat() * 0.2f);
            }
        }
        if (tick == GRAB_HIT && this.squaredDistanceTo(t) <= 3.5 * 3.5) {
            t.timeUntilRegen = 0;
            this.tryAttack(t);
        }
        if (tick >= GRAB_END) {
            this.endRush();
        }
    }

    private void dustBurst(int n) {
        if (this.getWorld() instanceof ServerWorld sw) {
            BlockState ground = sw.getBlockState(this.getBlockPos().down());
            if (!ground.isAir()) {
                sw.spawnParticles(new BlockStateParticleEffect(ParticleTypes.BLOCK, ground), this.getX(), this.getY() + 0.1,
                        this.getZ(), n, 0.3, 0.05, 0.3, 0.1);
            }
            sw.spawnParticles(ParticleTypes.CLOUD, this.getX(), this.getY() + 0.2, this.getZ(), 3, 0.2, 0.05, 0.2, 0.02);
        }
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
        public void tick() {
            IronZombieEntity z = (IronZombieEntity) this.mob;
            if (z.isRushing()) {
                this.windup = -1;
                this.pending = null;
                return;
            }
            LivingEntity target = z.getTarget();
            if (target != null && this.windup <= 0 && z.canRush(target)) {
                z.startRush(target);
                return;
            }
            super.tick();
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

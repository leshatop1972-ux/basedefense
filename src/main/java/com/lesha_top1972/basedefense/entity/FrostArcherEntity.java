package com.lesha_top1972.basedefense.entity;

import net.minecraft.block.BlockState;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.ai.goal.ActiveTargetGoal;
import net.minecraft.entity.ai.goal.Goal;
import net.minecraft.entity.ai.goal.LookAroundGoal;
import net.minecraft.entity.ai.goal.LookAtEntityGoal;
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
import net.minecraft.entity.projectile.PersistentProjectileEntity;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundEvent;
import net.minecraft.sound.SoundEvents;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;

import java.util.EnumSet;

/**
 * Морозная лучница: парит над землёй (модель приподнята на HOVER блока, см. FrostArcherRenderer),
 * держится на расстоянии и стреляет ледяными стрелами.
 * <ul>
 *   <li>40 HP, стрелы из луков и арбалетов наносят на 60% меньше урона;</li>
 *   <li>видит игрока за 25 блоков; выстрел раз в 6.5 с: оборот вокруг себя → натяжение → выстрел с отдачей;</li>
 *   <li>если подойти близко — отлетает спиной, один раз за перезарядку делает рывок назад;</li>
 *   <li>точность: 60%, если цель стоит, идёт медленно или бежит прямо на неё; 18%, если бежит в сторону/прочь;</li>
 *   <li>стрела: 14 урона + «Заморозка» на 2 с (FrostArrowEntity).</li>
 * </ul>
 */
public class FrostArcherEntity extends HostileEntity {

    public static final byte STATUS_ATTACK = 4;
    public static final byte STATUS_CANCEL = 5;

    /** На сколько блоков модель парит над землёй (для рендера и точки выстрела). */
    public static final float HOVER = 0.4f;

    /** Тайминги (тики). Совпадают с анимацией attack (tools/frost_archer_gen.py). */
    public static final int ATTACK_TICKS = 50;      // 2.5 с: оборот 0–12, стрела из колчана 12–25, натяжение 25–37
    public static final int DRAW_START = 26;
    public static final int RELEASE_TICK = 39;      // 1.95 с
    /** Выстрел раз в 6.5 с (от начала до начала). */
    public static final int SHOT_CYCLE = 130;

    static final double SEE_RANGE = 25.0;
    static final double KEEP_AWAY = 7.0;
    static final double DASH_RANGE = 3.5;
    static final float ARROW_FACTOR = 0.4f;
    static final double CHANCE_GOOD = 0.60;
    static final double CHANCE_BAD = 0.18;

    /** Только на клиенте: возраст, когда началась атака (оборот + выстрел). */
    public int attackStartAge = -10000;

    public FrostArcherEntity(EntityType<? extends HostileEntity> type, World world) {
        super(type, world);
        this.experiencePoints = 12;
    }

    public static DefaultAttributeContainer.Builder createAttributes() {
        return HostileEntity.createHostileAttributes()
                .add(EntityAttributes.GENERIC_MAX_HEALTH, 40.0)
                .add(EntityAttributes.GENERIC_MOVEMENT_SPEED, 0.26)
                .add(EntityAttributes.GENERIC_FOLLOW_RANGE, SEE_RANGE)
                .add(EntityAttributes.GENERIC_ATTACK_DAMAGE, 2.0);
    }

    @Override
    protected void initGoals() {
        this.goalSelector.add(0, new SwimGoal(this));
        this.goalSelector.add(2, new ArcherGoal(this));
        this.goalSelector.add(7, new WanderAroundFarGoal(this, 0.9));
        this.goalSelector.add(8, new LookAtEntityGoal(this, PlayerEntity.class, 12.0f));
        this.goalSelector.add(8, new LookAroundGoal(this));

        this.targetSelector.add(1, new RevengeGoal(this));
        this.targetSelector.add(2, new ActiveTargetGoal<>(this, PlayerEntity.class, true));
        this.targetSelector.add(3, new ActiveTargetGoal<>(this, MerchantEntity.class, true));
        this.targetSelector.add(3, new ActiveTargetGoal<>(this, IronGolemEntity.class, true));
        // Жители поселения добавляются в SettlerThreats
    }

    @Override
    public boolean damage(DamageSource source, float amount) {
        if (source.getSource() instanceof PersistentProjectileEntity) {
            amount *= ARROW_FACTOR;
        }
        return super.damage(source, amount);
    }

    @Override
    public boolean handleFallDamage(float fallDistance, float damageMultiplier, DamageSource damageSource) {
        return false; // парит
    }

    @Override
    public void tickMovement() {
        super.tickMovement();
        // Парение: падает медленно, как пушинка
        Vec3d v = this.getVelocity();
        if (!this.isOnGround() && v.y < -0.12) {
            this.setVelocity(v.x, -0.12, v.z);
        }
        this.fallDistance = 0;
        if (this.getWorld().isClient && this.random.nextInt(4) == 0) {
            this.getWorld().addParticle(ParticleTypes.SNOWFLAKE,
                    this.getParticleX(0.6), this.getY() + HOVER * 0.5 + this.random.nextDouble() * 0.4, this.getParticleZ(0.6),
                    0, -0.02, 0);
        }
    }

    @Override
    public void handleStatus(byte status) {
        if (status == STATUS_ATTACK) {
            this.attackStartAge = this.age;
        } else if (status == STATUS_CANCEL) {
            this.attackStartAge = -10000;
        } else {
            super.handleStatus(status);
        }
    }

    @Override
    protected SoundEvent getAmbientSound() {
        return SoundEvents.ENTITY_STRAY_AMBIENT;
    }

    @Override
    protected SoundEvent getHurtSound(DamageSource source) {
        return SoundEvents.ENTITY_STRAY_HURT;
    }

    @Override
    protected SoundEvent getDeathSound() {
        return SoundEvents.ENTITY_STRAY_DEATH;
    }

    @Override
    protected void playStepSound(BlockPos pos, BlockState state) {
        // парит — шагов нет
    }

    /** Направление «вперёд» по повороту тела. */
    Vec3d forward() {
        float yaw = this.getYaw() * 0.017453292f;
        return new Vec3d(-MathHelper.sin(yaw), 0, MathHelper.cos(yaw));
    }

    void faceTarget(LivingEntity target) {
        double dx = target.getX() - this.getX();
        double dz = target.getZ() - this.getZ();
        float yaw = (float) (MathHelper.atan2(dz, dx) * 57.29577951308232) - 90.0f;
        this.setYaw(yaw);
        this.setBodyYaw(yaw);
        this.setHeadYaw(yaw);
        this.getLookControl().lookAt(target, 30.0f, 30.0f);
    }

    /** Точка вылета стрелы — у лука, перед лицом. */
    Vec3d bowPos() {
        return this.getPos().add(0, HOVER + 1.6, 0).add(this.forward().multiply(0.7));
    }

    /** Вероятность попадания по тому, как движется цель (velocity — блоков за тик). */
    double hitChance(LivingEntity target, Vec3d velocity) {
        Vec3d flat = new Vec3d(velocity.x, 0, velocity.z);
        double speed = flat.length();
        if (speed < 0.12) {
            return CHANCE_GOOD;                                   // стоит или идёт медленно
        }
        Vec3d toMe = new Vec3d(this.getX() - target.getX(), 0, this.getZ() - target.getZ()).normalize();
        if (flat.normalize().dotProduct(toMe) > 0.7) {
            return CHANCE_GOOD;                                   // бежит прямо на лучницу
        }
        // идёт/бежит в сторону: чем быстрее, тем хуже (обычный шаг ~0.22, бег ~0.28 блока/тик)
        double t = MathHelper.clamp((speed - 0.12) / 0.12, 0.0, 1.0);
        return MathHelper.lerp(t, CHANCE_GOOD, CHANCE_BAD);
    }

    void shoot(LivingEntity target, Vec3d targetVelocity) {
        Vec3d from = this.bowPos();
        Vec3d aim = target.getPos().add(0, target.getHeight() * 0.55, 0);
        // упреждение: куда цель придёт, пока летит стрела
        Vec3d predicted = aim;
        for (int i = 0; i < 3; i++) {
            double t = predicted.distanceTo(from) / FrostArrowEntity.SPEED;
            predicted = aim.add(targetVelocity.x * t, 0, targetVelocity.z * t);
        }
        boolean hit = this.random.nextDouble() < this.hitChance(target, targetVelocity);
        if (hit) {
            FrostArrowEntity.shoot(this.getWorld(), this, from, predicted.subtract(from), target, 0.12);
        } else {
            // промах: целимся рядом — в сторону и чуть выше/ниже
            Vec3d dir = predicted.subtract(from);
            Vec3d side = new Vec3d(-dir.z, 0, dir.x).normalize();
            double off = (1.6 + this.random.nextDouble() * 1.4) * (this.random.nextBoolean() ? 1 : -1);
            Vec3d miss = predicted.add(side.multiply(off)).add(0, (this.random.nextDouble() - 0.4) * 1.2, 0);
            FrostArrowEntity.shoot(this.getWorld(), this, from, miss.subtract(from), null, 0);
        }
        this.playSound(SoundEvents.ENTITY_ARROW_SHOOT, 1.0f, 1.3f);
        this.playSound(SoundEvents.BLOCK_AMETHYST_BLOCK_CHIME, 1.2f, 1.6f);
        // отдача: немного отлетает назад
        Vec3d back = this.forward().multiply(-0.45);
        this.addVelocity(back.x, 0.12, back.z);
        this.velocityModified = true;
    }

    void dashAway(LivingEntity from) {
        Vec3d away = new Vec3d(this.getX() - from.getX(), 0, this.getZ() - from.getZ());
        if (away.lengthSquared() < 1.0e-4) {
            away = this.forward().multiply(-1);
        }
        away = away.normalize();
        this.setVelocity(away.x * 1.05, 0.38, away.z * 1.05);
        this.velocityModified = true;
        this.playSound(SoundEvents.ENTITY_PHANTOM_FLAP, 1.0f, 1.4f);
        if (this.getWorld() instanceof ServerWorld sw) {
            sw.spawnParticles(ParticleTypes.SNOWFLAKE, this.getX(), this.getY() + 0.8, this.getZ(), 20, 0.4, 0.4, 0.4, 0.06);
            sw.spawnParticles(ParticleTypes.CLOUD, this.getX(), this.getY() + 0.3, this.getZ(), 6, 0.3, 0.1, 0.3, 0.02);
        }
    }

    /** Бой: держать дистанцию, отлетать спиной, раз в 6.5 с — оборот и выстрел. */
    static class ArcherGoal extends Goal {
        private final FrostArcherEntity archer;
        private final Vec3d[] history = new Vec3d[6];
        private int historyCount;
        private int cooldown = 20;
        private int attackTick = -1;
        private boolean dashUsed;
        private int dashPause;
        private int pathDelay;
        private int strafeTimer;
        private boolean strafeLeft;

        ArcherGoal(FrostArcherEntity archer) {
            this.archer = archer;
            this.setControls(EnumSet.of(Control.MOVE, Control.LOOK));
        }

        private static boolean valid(LivingEntity t) {
            return t != null && t.isAlive()
                    && !(t instanceof PlayerEntity p && (p.isCreative() || p.isSpectator()));
        }

        @Override
        public boolean canStart() {
            return valid(this.archer.getTarget());
        }

        @Override
        public boolean shouldContinue() {
            return valid(this.archer.getTarget());
        }

        @Override
        public boolean shouldRunEveryTick() {
            return true;
        }

        @Override
        public void start() {
            this.archer.setAttacking(true);
            this.historyCount = 0;
        }

        @Override
        public void stop() {
            if (this.attackTick >= 0) {
                this.archer.getWorld().sendEntityStatus(this.archer, STATUS_CANCEL);
                this.attackTick = -1;
                this.cooldown = 40;
            }
            this.archer.setAttacking(false);
            this.archer.getNavigation().stop();
            this.archer.getMoveControl().strafeTo(0, 0);
        }

        private Vec3d targetVelocity() {
            if (this.historyCount < 2) {
                return Vec3d.ZERO;
            }
            int n = Math.min(this.historyCount, this.history.length);
            Vec3d newest = this.history[(this.historyCount - 1) % this.history.length];
            Vec3d oldest = this.history[(this.historyCount - n) % this.history.length];
            return newest.subtract(oldest).multiply(1.0 / (n - 1));
        }

        @Override
        public void tick() {
            LivingEntity target = this.archer.getTarget();
            if (target == null) {
                return;
            }
            this.history[this.historyCount % this.history.length] = target.getPos();
            this.historyCount++;

            double dist = this.archer.distanceTo(target);
            boolean canSee = this.archer.getVisibilityCache().canSee(target);

            // --- атака: оборот → натяжение → выстрел ---
            if (this.attackTick >= 0) {
                this.attackTick++;
                this.archer.getNavigation().stop();
                this.archer.getMoveControl().strafeTo(0, 0);
                this.archer.faceTarget(target);
                if (this.attackTick == 1) {
                    this.archer.playSound(SoundEvents.ENTITY_PLAYER_ATTACK_SWEEP, 0.8f, 1.7f);
                } else if (this.attackTick == DRAW_START) {
                    this.archer.playSound(SoundEvents.ITEM_CROSSBOW_LOADING_MIDDLE, 1.0f, 1.25f);
                }
                if (this.attackTick < RELEASE_TICK && (!canSee || dist > SEE_RANGE + 4)) {
                    // цель пропала — отменить выстрел
                    this.archer.getWorld().sendEntityStatus(this.archer, STATUS_CANCEL);
                    this.attackTick = -1;
                    this.cooldown = 30;
                    return;
                }
                if (this.attackTick == RELEASE_TICK) {
                    this.archer.shoot(target, this.targetVelocity());
                    this.dashUsed = false;
                }
                if (this.attackTick >= ATTACK_TICKS) {
                    this.attackTick = -1;
                    this.cooldown = SHOT_CYCLE - ATTACK_TICKS;
                }
                return;
            }

            if (this.cooldown > 0) {
                this.cooldown--;
            }

            // --- рывок назад: один раз за перезарядку, если подошли вплотную ---
            if (this.dashPause > 0) {
                this.dashPause--;
                this.archer.lookAtEntity(target, 30.0f, 30.0f);
                return;
            }
            if (!this.dashUsed && dist < DASH_RANGE && canSee) {
                this.dashUsed = true;
                this.dashPause = 10;
                this.archer.getNavigation().stop();
                this.archer.dashAway(target);
                return;
            }

            // --- начать атаку ---
            if (this.cooldown <= 0 && canSee && dist <= SEE_RANGE) {
                this.attackTick = 0;
                this.archer.getWorld().sendEntityStatus(this.archer, STATUS_ATTACK);
                return;
            }

            // --- перемещение между выстрелами ---
            if (!canSee || dist > 18.0) {
                if (--this.pathDelay <= 0) {
                    this.pathDelay = 10;
                    this.archer.getNavigation().startMovingTo(target, 1.0);
                }
                this.archer.getLookControl().lookAt(target, 30.0f, 30.0f);
                return;
            }
            this.archer.getNavigation().stop();
            if (--this.strafeTimer <= 0) {
                this.strafeTimer = 40 + this.archer.random.nextInt(40);
                this.strafeLeft = this.archer.random.nextBoolean();
            }
            float side = this.strafeLeft ? 0.35f : -0.35f;
            if (dist < KEEP_AWAY) {
                // отлетает спиной, глядя на цель
                this.archer.getMoveControl().strafeTo(-0.9f, side * 0.6f);
            } else {
                this.archer.getMoveControl().strafeTo(0f, side);
            }
            this.archer.lookAtEntity(target, 30.0f, 30.0f);
        }
    }
}

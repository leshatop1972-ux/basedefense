package com.lesha_top1972.basedefense.entity;

import net.minecraft.block.BlockState;
import net.minecraft.entity.AnimationState;
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
import net.minecraft.entity.decoration.ArmorStandEntity;
import net.minecraft.entity.mob.HostileEntity;
import net.minecraft.entity.mob.Monster;
import net.minecraft.entity.passive.IronGolemEntity;
import net.minecraft.entity.passive.MerchantEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.projectile.PersistentProjectileEntity;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvent;
import net.minecraft.sound.SoundEvents;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.world.World;

import java.util.EnumSet;

/**
 * Железный рыцарь: 65 HP, стрелы наносят на 45% меньше урона.
 * Бой: 2 удара мечом с замахом (10 урона, достаёт на 2 блока, между ударами 1.2 с),
 * третьим — вращение: ~3 секунды крутится и идёт к цели, раз в секунду 5 урона всем рядом (2.5 блока).
 * Без цели ходит как зомби, за целью бежит (90% скорости бега игрока).
 */
public class IronKnightEntity extends HostileEntity {

    public static final byte STATUS_SWING = 4;
    public static final byte STATUS_SPIN = 64;

    /** Скорость атрибута — как у зомби. */
    static final double BASE_SPEED = 0.23;
    /** Множитель при погоне: 0.23 × 1.49 ≈ 0.342 → 90% бега игрока. */
    static final double RUN_SPEED = 1.49;
    /** Во время вращения: 0.23 × 1.3 ≈ быстрее шага, медленнее погони. */
    static final double SPIN_SPEED = 1.3;

    static final float SWING_DAMAGE = 10f;
    static final float SPIN_DAMAGE = 5f;
    static final double SWING_REACH = 2.0;
    static final double SPIN_RADIUS = 2.5;
    static final float ARROW_FACTOR = 0.55f;

    /** Тайминги (тики). Должны совпадать с IronKnightModel. */
    public static final int SWING_HIT = 14;
    public static final int SWING_TOTAL = 24;
    public static final int SPIN_WINDUP = 13;
    public static final int SPIN_TICKS = 60;
    public static final int SPIN_RECOVER = 20;

    /** Только на клиенте: анимация удара мечом. */
    public final AnimationState swingState = new AnimationState();
    /** Только на клиенте: возраст сущности, когда началось вращение. */
    public int spinStartAge = -10000;

    public IronKnightEntity(EntityType<? extends HostileEntity> type, World world) {
        super(type, world);
        this.experiencePoints = 15;
    }

    public static DefaultAttributeContainer.Builder createAttributes() {
        return HostileEntity.createHostileAttributes()
                .add(EntityAttributes.GENERIC_MAX_HEALTH, 65.0)
                .add(EntityAttributes.GENERIC_ATTACK_DAMAGE, SWING_DAMAGE)
                .add(EntityAttributes.GENERIC_MOVEMENT_SPEED, BASE_SPEED)
                .add(EntityAttributes.GENERIC_FOLLOW_RANGE, 35.0)
                .add(EntityAttributes.GENERIC_KNOCKBACK_RESISTANCE, 0.4);
    }

    @Override
    protected void initGoals() {
        this.goalSelector.add(0, new SwimGoal(this));
        this.goalSelector.add(2, new CombatGoal(this));
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
    public boolean damage(DamageSource source, float amount) {
        if (source.getSource() instanceof PersistentProjectileEntity) {
            amount *= ARROW_FACTOR;
        }
        return super.damage(source, amount);
    }

    @Override
    public void handleStatus(byte status) {
        if (status == STATUS_SWING) {
            this.swingState.start(this.age);
        } else if (status == STATUS_SPIN) {
            this.spinStartAge = this.age;
        } else {
            super.handleStatus(status);
        }
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
    protected void playStepSound(BlockPos pos, BlockState state) {
        this.playSound(SoundEvents.BLOCK_CHAIN_STEP, 0.6f, 0.8f);
    }

    /** Цель в пределах reach блоков от края хитбокса рыцаря. */
    boolean inReach(LivingEntity target, double reach) {
        double dx = Math.max(0, Math.abs(target.getX() - this.getX()) - (target.getWidth() + this.getWidth()) / 2);
        double dz = Math.max(0, Math.abs(target.getZ() - this.getZ()) - (target.getWidth() + this.getWidth()) / 2);
        boolean vertical = target.getY() < this.getY() + this.getHeight() + 0.5
                && target.getY() + target.getHeight() > this.getY() - 0.5;
        return vertical && dx * dx + dz * dz <= reach * reach;
    }

    /** Удар вращением: 5 урона всем живым рядом, кроме монстров. */
    void spinHit() {
        Box box = this.getBoundingBox().expand(SPIN_RADIUS, 0.25, SPIN_RADIUS);
        for (LivingEntity e : this.getWorld().getEntitiesByClass(LivingEntity.class, box,
                x -> x != this && x.isAlive() && !(x instanceof Monster) && !(x instanceof ArmorStandEntity))) {
            if (e.damage(this.getDamageSources().mobAttack(this), SPIN_DAMAGE)) {
                e.takeKnockback(0.5, this.getX() - e.getX(), this.getZ() - e.getZ());
            }
        }
    }

    void spinEffects() {
        this.getWorld().playSound(null, this.getX(), this.getY(), this.getZ(),
                SoundEvents.ENTITY_PLAYER_ATTACK_SWEEP, SoundCategory.HOSTILE, 1.0f, 0.7f);
        if (this.getWorld() instanceof ServerWorld sw) {
            for (int i = 0; i < 4; i++) {
                double a = this.random.nextDouble() * Math.PI * 2;
                double r = this.getWidth() / 2 + 0.6;
                sw.spawnParticles(ParticleTypes.SWEEP_ATTACK, this.getX() + Math.cos(a) * r,
                        this.getY() + 1.0, this.getZ() + Math.sin(a) * r, 1, 0, 0, 0, 0);
            }
        }
    }

    /** Погоня и серия: удар, удар, вращение. */
    static class CombatGoal extends Goal {
        private enum Phase { CHASE, SWING, WINDUP, SPIN, RECOVER }

        private final IronKnightEntity knight;
        private Phase phase = Phase.CHASE;
        private int ticks;
        private int cooldown;
        private int combo;
        private int pathDelay;
        private LivingEntity swingTarget;

        CombatGoal(IronKnightEntity knight) {
            this.knight = knight;
            this.setControls(EnumSet.of(Control.MOVE, Control.LOOK));
        }

        private static boolean valid(LivingEntity t) {
            return t != null && t.isAlive()
                    && !(t instanceof PlayerEntity p && (p.isCreative() || p.isSpectator()));
        }

        @Override
        public boolean canStart() {
            return valid(this.knight.getTarget());
        }

        @Override
        public boolean shouldContinue() {
            // Начатую атаку доводим до конца
            return this.phase != Phase.CHASE || valid(this.knight.getTarget());
        }

        @Override
        public boolean shouldRunEveryTick() {
            return true;
        }

        @Override
        public void start() {
            this.phase = Phase.CHASE;
            this.pathDelay = 0;
            this.knight.setAttacking(true);
        }

        @Override
        public void stop() {
            this.phase = Phase.CHASE;
            this.swingTarget = null;
            this.knight.setAttacking(false);
            this.knight.getNavigation().stop();
        }

        private void setPhase(Phase p) {
            this.phase = p;
            this.ticks = 0;
            this.pathDelay = 0;
        }

        private void moveTo(LivingEntity target, double speed) {
            if (--this.pathDelay <= 0) {
                this.pathDelay = 5;
                this.knight.getNavigation().startMovingTo(target, speed);
            }
        }

        @Override
        public void tick() {
            LivingEntity target = this.knight.getTarget();
            boolean hasTarget = valid(target);
            this.ticks++;
            if (hasTarget) {
                this.knight.getLookControl().lookAt(target, 30.0f, 30.0f);
            }
            switch (this.phase) {
                case CHASE -> {
                    if (!hasTarget) {
                        return;
                    }
                    this.moveTo(target, RUN_SPEED);
                    if (this.cooldown > 0) {
                        this.cooldown--;
                    } else if (this.knight.inReach(target, SWING_REACH)) {
                        this.knight.getNavigation().stop();
                        if (this.combo < 2) {
                            this.combo++;
                            this.swingTarget = target;
                            this.setPhase(Phase.SWING);
                            this.knight.getWorld().sendEntityStatus(this.knight, STATUS_SWING);
                        } else {
                            this.combo = 0;
                            this.setPhase(Phase.WINDUP);
                            this.knight.getWorld().sendEntityStatus(this.knight, STATUS_SPIN);
                        }
                    }
                }
                case SWING -> {
                    this.knight.getNavigation().stop();
                    if (this.ticks == SWING_HIT) {
                        LivingEntity t = this.swingTarget;
                        if (valid(t) && this.knight.inReach(t, SWING_REACH + 0.5)) {
                            this.knight.tryAttack(t);
                        }
                        this.knight.playSound(SoundEvents.ENTITY_PLAYER_ATTACK_STRONG, 1.0f, 0.7f);
                    }
                    if (this.ticks >= SWING_TOTAL) {
                        this.swingTarget = null;
                        this.setPhase(Phase.CHASE);
                    }
                }
                case WINDUP -> {
                    this.knight.getNavigation().stop();
                    if (this.ticks >= SPIN_WINDUP) {
                        this.setPhase(Phase.SPIN);
                    }
                }
                case SPIN -> {
                    if (hasTarget) {
                        this.moveTo(target, SPIN_SPEED);
                    }
                    if (this.ticks % 10 == 1) {
                        this.knight.spinEffects();
                    }
                    if (this.ticks % 20 == 10) {
                        this.knight.spinHit();
                    }
                    if (this.ticks >= SPIN_TICKS) {
                        this.knight.getNavigation().stop();
                        this.setPhase(Phase.RECOVER);
                    }
                }
                case RECOVER -> {
                    this.knight.getNavigation().stop();
                    if (this.ticks >= SPIN_RECOVER) {
                        this.setPhase(Phase.CHASE);
                        this.cooldown = 10;
                    }
                }
            }
        }
    }
}

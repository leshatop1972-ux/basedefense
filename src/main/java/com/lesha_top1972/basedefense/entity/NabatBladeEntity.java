package com.lesha_top1972.basedefense.entity;

import com.lesha_top1972.basedefense.combat.ShieldBlocker;
import com.lesha_top1972.basedefense.sound.ModSounds;
import net.minecraft.block.BlockState;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.ai.goal.ActiveTargetGoal;
import net.minecraft.entity.ai.goal.Goal;
import net.minecraft.entity.ai.goal.LookAroundGoal;
import net.minecraft.entity.ai.goal.LookAtEntityGoal;
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
import net.minecraft.nbt.NbtCompound;
import net.minecraft.particle.DustParticleEffect;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvent;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;
import org.joml.Vector3f;

import java.util.EnumSet;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Мечник Набата «Отзвук»: пустые бронзовые латы, внутри тлеет свет. Бьётся как обычный моб (без парирования).
 * Атаки: серия из трёх ударов (5/5/7) и рывок‑укол (8). Цель раздаёт сам Набат — слуги расходятся по разным игрокам.
 * Тайминги совпадают с анимациями tools/nabat_gen.py.
 */
public class NabatBladeEntity extends HostileEntity {

    public static final int NONE = -1;
    public static final int COMBO = 0;
    public static final int DASH = 1;
    public static final int PARRY = 2;
    public static final int ASSEMBLE = 3;
    public static final byte STATUS_BASE = 70;

    public static final int COMBO_TICKS = 44;
    static final int[] COMBO_HITS = {11, 20, 31};
    static final float[] COMBO_DMG = {5f, 5f, 7f};
    static final double COMBO_REACH = 2.7;
    public static final int DASH_TICKS = 32;
    static final int DASH_GO = 10;
    static final int DASH_HIT = 16;
    static final float DASH_DMG = 8f;
    static final int DASH_COOLDOWN = 160;
    public static final int ASSEMBLE_TICKS = 32;
    static final double DASH_SPEED = 0.6;
    private static final DustParticleEffect AMBER = new DustParticleEffect(new Vector3f(1.0f, 0.72f, 0.3f), 0.9f);

    public int clientAction = NONE;
    public int clientActionAge;
    private int assemble;

    public NabatBladeEntity(EntityType<? extends HostileEntity> type, World world) {
        super(type, world);
        this.experiencePoints = 8;
        this.addCommandTag(NabatHooks.TAG);
    }

    public static DefaultAttributeContainer.Builder createAttributes() {
        return HostileEntity.createHostileAttributes()
                .add(EntityAttributes.GENERIC_MAX_HEALTH, 55.0)
                .add(EntityAttributes.GENERIC_MOVEMENT_SPEED, 0.204)
                .add(EntityAttributes.GENERIC_FOLLOW_RANGE, 40.0)
                .add(EntityAttributes.GENERIC_ATTACK_DAMAGE, 5.0)
                .add(EntityAttributes.GENERIC_ARMOR, 6.0)
                .add(EntityAttributes.GENERIC_KNOCKBACK_RESISTANCE, 0.4);
    }

    /** Призван Набатом: первые 1.6 с собирается из дыма (стоит на месте, но его уже можно бить). */
    void setAssembling() {
        this.assemble = ASSEMBLE_TICKS;
    }

    public boolean isAssembling() {
        return this.assemble > 0;
    }

    @Override
    protected void initGoals() {
        this.goalSelector.add(0, new SwimGoal(this));
        this.goalSelector.add(2, new BladeGoal(this));
        this.goalSelector.add(7, new WanderAroundFarGoal(this, 0.8));
        this.goalSelector.add(8, new LookAtEntityGoal(this, PlayerEntity.class, 12.0f));
        this.goalSelector.add(8, new LookAroundGoal(this));
        this.targetSelector.add(2, new ActiveTargetGoal<>(this, PlayerEntity.class, true));
        this.targetSelector.add(3, new ActiveTargetGoal<>(this, MerchantEntity.class, true));
        this.targetSelector.add(3, new ActiveTargetGoal<>(this, IronGolemEntity.class, true));
    }

    @Override
    public void tick() {
        super.tick();
        if (this.assemble > 0) {
            this.assemble--;
            if (this.assemble == ASSEMBLE_TICKS - 1) {
                this.sendAction(ASSEMBLE);
                this.playSound(ModSounds.BLADE_ASSEMBLE, 0.8f, 1.0f);
            }
            this.setVelocity(0, this.getVelocity().y, 0);
            if (this.getWorld() instanceof ServerWorld s && this.assemble % 3 == 0) {
                s.spawnParticles(ParticleTypes.LARGE_SMOKE, this.getX(), this.getY() + 0.8, this.getZ(), 2, 0.25, 0.6, 0.25, 0.01);
                s.spawnParticles(AMBER, this.getX(), this.getY() + 1.0, this.getZ(), 2, 0.3, 0.6, 0.3, 0.01);
            }
        }
    }

    @Override
    public void handleStatus(byte status) {
        int a = status - STATUS_BASE;
        if (a >= COMBO && a <= ASSEMBLE) {
            this.clientAction = a;
            this.clientActionAge = this.age;
        } else {
            super.handleStatus(status);
        }
    }

    void sendAction(int a) {
        this.getWorld().sendEntityStatus(this, (byte) (STATUS_BASE + a));
    }

    Vec3d forward() {
        float yaw = this.bodyYaw * 0.017453292f;
        return new Vec3d(-MathHelper.sin(yaw), 0, MathHelper.cos(yaw));
    }

    @Override
    public boolean damage(DamageSource source, float amount) {
        Entity att = source.getAttacker();
        if (att != null && NabatHooks.isKin(att)) {
            return false;
        }
        return super.damage(source, amount);
    }

    List<LivingEntity> victims(double radius) {
        Box box = this.getBoundingBox().expand(radius + 1.0, 1.5, radius + 1.0);
        return this.getWorld().getEntitiesByClass(LivingEntity.class, box,
                e -> e != this && e.isAlive() && !(e instanceof Monster) && !(e instanceof ArmorStandEntity)
                        && !NabatHooks.isKin(e) && !(e instanceof PlayerEntity p && (p.isCreative() || p.isSpectator())));
    }

    double edgeDist(LivingEntity e) {
        Box b = e.getBoundingBox();
        double dx = Math.max(Math.max(b.minX - this.getX(), 0), this.getX() - b.maxX);
        double dz = Math.max(Math.max(b.minZ - this.getZ(), 0), this.getZ() - b.maxZ);
        return Math.sqrt(dx * dx + dz * dz);
    }

    int coneHit(double reach, double halfAngle, float damage, double knock, Set<Integer> hit) {
        Vec3d f = this.forward();
        double cos = Math.cos(Math.toRadians(halfAngle));
        int n = 0;
        for (LivingEntity e : this.victims(reach)) {
            if (hit.contains(e.getId())) {
                continue;
            }
            double d = this.edgeDist(e);
            if (d > reach || Math.abs(e.getY() - this.getY()) > 2.2) {
                continue;
            }
            Vec3d to = new Vec3d(e.getX() - this.getX(), 0, e.getZ() - this.getZ());
            if (d > 0.8 && to.lengthSquared() > 0.01 && to.normalize().dotProduct(f) < cos) {
                continue;
            }
            hit.add(e.getId());
            e.timeUntilRegen = 0;
            if (e.damage(this.getDamageSources().mobAttack(this), damage)) {
                n++;
                boolean shielded = e instanceof ShieldBlocker sb && sb.basedefense$blockedThisTick();
                if (!shielded && knock > 0) {
                    e.takeKnockback(knock, this.getX() - e.getX(), this.getZ() - e.getZ());
                }
            }
        }
        return n;
    }

    void faceTo(LivingEntity t) {
        float yaw = (float) (MathHelper.atan2(t.getZ() - this.getZ(), t.getX() - this.getX()) * 57.29577951308232) - 90.0f;
        this.setYaw(yaw);
        this.setBodyYaw(yaw);
        this.setHeadYaw(yaw);
    }

    @Override
    protected SoundEvent getAmbientSound() {
        return null;
    }

    @Override
    protected SoundEvent getHurtSound(DamageSource source) {
        return ModSounds.BLADE_HURT;
    }

    @Override
    protected SoundEvent getDeathSound() {
        return ModSounds.BLADE_DEATH;
    }

    @Override
    protected void playStepSound(BlockPos pos, BlockState state) {
        this.playSound(ModSounds.BLADE_STEP, 0.25f, 0.9f + this.random.nextFloat() * 0.2f);
    }

    @Override
    public void writeCustomDataToNbt(NbtCompound nbt) {
        super.writeCustomDataToNbt(nbt);
        nbt.putInt("Assemble", this.assemble);
    }

    @Override
    public void readCustomDataFromNbt(NbtCompound nbt) {
        super.readCustomDataFromNbt(nbt);
        this.assemble = nbt.getInt("Assemble");
    }

    // =====================================================================
    static class BladeGoal extends Goal {
        private final NabatBladeEntity mob;
        private int action = NONE;
        private int tick;
        private int cooldown = 20;
        private int dashCooldown = 60;
        private int pathDelay;
        private int circleTimer;
        private boolean circleLeft;
        private Vec3d dashDir = Vec3d.ZERO;
        private final Set<Integer> hitIds = new HashSet<>();

        BladeGoal(NabatBladeEntity mob) {
            this.mob = mob;
            this.setControls(EnumSet.of(Control.MOVE, Control.LOOK, Control.JUMP));
        }

        static boolean valid(LivingEntity t) {
            return t != null && t.isAlive() && !(t instanceof PlayerEntity p && (p.isCreative() || p.isSpectator()));
        }

        @Override
        public boolean canStart() {
            return !this.mob.isAssembling() && valid(this.mob.getTarget());
        }

        @Override
        public boolean shouldContinue() {
            return !this.mob.isAssembling() && (this.action != NONE || valid(this.mob.getTarget()));
        }

        @Override
        public boolean shouldRunEveryTick() {
            return true;
        }

        @Override
        public void start() {
            this.mob.setAttacking(true);
        }

        @Override
        public void stop() {
            this.mob.setAttacking(false);
            this.mob.getNavigation().stop();
            this.action = NONE;
        }

        private void begin(int a) {
            this.action = a;
            this.tick = 0;
            this.hitIds.clear();
            this.mob.getNavigation().stop();
            this.mob.sendAction(a);
            if (a == DASH) {
                this.dashCooldown = DASH_COOLDOWN;
            }
        }

        @Override
        public void tick() {
            if (this.dashCooldown > 0) {
                this.dashCooldown--;
            }
            LivingEntity target = this.mob.getTarget();
            boolean has = valid(target);
            if (this.action != NONE) {
                this.tick++;
                this.run(has ? target : null);
                return;
            }
            if (!has) {
                return;
            }
            double dist = this.mob.distanceTo(target);
            this.mob.getLookControl().lookAt(target, 30.0f, 30.0f);
            if (this.cooldown > 0) {
                this.cooldown--;
                this.maneuver(target, dist);
                return;
            }
            boolean see = this.mob.getVisibilityCache().canSee(target);
            if (see && dist < 3.0) {
                this.mob.faceTo(target);
                this.begin(COMBO);
            } else if (see && dist > 4.0 && dist < 9.0 && this.dashCooldown <= 0) {
                this.mob.faceTo(target);
                this.begin(DASH);
            } else {
                this.maneuver(target, dist);
            }
        }

        private void maneuver(LivingEntity target, double dist) {
            if (dist > 2.4 || !this.mob.getVisibilityCache().canSee(target)) {
                if (--this.pathDelay <= 0) {
                    this.pathDelay = 6;
                    this.mob.getNavigation().startMovingTo(target, dist > 6 ? 1.25 : 1.0);
                }
            } else {
                this.mob.getNavigation().stop();
                if (--this.circleTimer <= 0) {
                    this.circleTimer = 20 + this.mob.random.nextInt(25);
                    this.circleLeft = this.mob.random.nextBoolean();
                }
                this.mob.getMoveControl().strafeTo(-0.1f, this.circleLeft ? 0.4f : -0.4f);
                this.mob.lookAtEntity(target, 30.0f, 30.0f);
            }
        }

        private void run(LivingEntity target) {
            NabatBladeEntity m = this.mob;
            int t = this.tick;
            if (this.action == COMBO) {
                for (int k = 0; k < COMBO_HITS.length; k++) {
                    int h = COMBO_HITS[k];
                    if (t == h - 3 && target != null) {
                        m.faceTo(target);
                        Vec3d f = m.forward().multiply(0.25);
                        m.addVelocity(f.x, 0, f.z);
                        m.velocityModified = true;
                        m.playSound(ModSounds.BLADE_SWING, 1.0f, 0.95f + k * 0.08f);
                        this.hitIds.clear();
                    }
                    if (t >= h - 1 && t <= h + 1) {
                        m.coneHit(COMBO_REACH, 70, COMBO_DMG[k], k == 2 ? 0.6 : 0.3, this.hitIds);
                    }
                }
                if (t >= COMBO_TICKS) {
                    this.finish(16 + m.random.nextInt(14));
                }
            } else if (this.action == DASH) {
                if (target != null && t < DASH_GO) {
                    m.faceTo(target);
                }
                if (t == DASH_GO) {
                    this.dashDir = m.forward();
                    m.playSound(ModSounds.BLADE_SWING, 1.2f, 0.8f);
                }
                if (t >= DASH_GO && t < DASH_HIT + 2) {
                    m.setVelocity(this.dashDir.x * DASH_SPEED, m.getVelocity().y, this.dashDir.z * DASH_SPEED);
                    m.velocityModified = true;
                    if (t >= DASH_HIT - 2) {
                        m.coneHit(2.4, 45, DASH_DMG, 0.8, this.hitIds);
                    }
                }
                if (t == DASH_HIT + 2) {
                    m.setVelocity(m.getVelocity().multiply(0.2, 1, 0.2));
                    m.velocityModified = true;
                }
                if (t >= DASH_TICKS) {
                    this.finish(20 + m.random.nextInt(16));
                }
            } else {
                this.action = NONE;
            }
        }

        private void finish(int rest) {
            this.action = NONE;
            this.cooldown = rest;
        }
    }
}

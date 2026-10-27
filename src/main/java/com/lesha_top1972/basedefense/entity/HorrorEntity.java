package com.lesha_top1972.basedefense.entity;

import net.minecraft.block.BlockState;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.ai.goal.ActiveTargetGoal;
import net.minecraft.entity.ai.goal.Goal;
import net.minecraft.entity.ai.goal.LookAroundGoal;
import net.minecraft.entity.ai.goal.LookAtEntityGoal;
import net.minecraft.entity.ai.goal.SwimGoal;
import net.minecraft.entity.ai.goal.WanderAroundFarGoal;
import net.minecraft.entity.decoration.ArmorStandEntity;
import net.minecraft.entity.mob.HostileEntity;
import net.minecraft.entity.mob.Monster;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;

import java.util.EnumSet;
import java.util.HashSet;
import java.util.Set;

/**
 * Общее для «сущностей» (туманной и кровавой): модель как у игрока, шагов не слышно, ближний бой со своими
 * анимациями (статусы 70 + действие), удары — в тик анимации, по конусу перед собой.
 */
public abstract class HorrorEntity extends HostileEntity {

    public static final int NONE = -1;
    public static final int ATTACK = 0;
    public static final int LUNGE = 1;
    public static final byte STATUS_BASE = 70;

    public int clientAction = NONE;
    public int clientActionAge;

    protected HorrorEntity(EntityType<? extends HostileEntity> type, World world) {
        super(type, world);
        this.experiencePoints = 10;
    }

    @Override
    protected void initGoals() {
        this.goalSelector.add(0, new SwimGoal(this));
        this.goalSelector.add(2, new HorrorGoal(this));
        this.goalSelector.add(7, new WanderAroundFarGoal(this, 0.8));
        this.goalSelector.add(8, new LookAtEntityGoal(this, PlayerEntity.class, 16.0f));
        this.goalSelector.add(8, new LookAroundGoal(this));
        this.targetSelector.add(1, new ActiveTargetGoal<>(this, PlayerEntity.class, true));
    }

    // --- настройки наследников ---
    /** Длина удара рукой (тики) и тик попадания. */
    protected abstract int attackTicks();

    protected abstract int attackHit();

    protected abstract float attackDamage();

    /** Перерыв между атаками (тики). */
    protected int attackCooldown() {
        return 40;
    }

    /** Попадание: например, «Кровотёк». */
    protected void onHit(LivingEntity target) {
    }

    /** Звук замаха (в начале атаки). */
    protected void attackSound(int action) {
    }

    /** Вторая атака: можно ли начать при такой дистанции (по умолчанию — нет). */
    protected boolean canLunge(double dist) {
        return false;
    }

    protected void runLunge(int tick, LivingEntity target, Set<Integer> hit) {
    }

    protected int lungeTicks() {
        return 0;
    }

    // ---
    @Override
    public void handleStatus(byte status) {
        int a = status - STATUS_BASE;
        if (a == ATTACK || a == LUNGE) {
            this.clientAction = a;
            this.clientActionAge = this.age;
        } else {
            super.handleStatus(status);
        }
    }

    @Override
    protected void playStepSound(BlockPos pos, BlockState state) {
        // шагов не слышно
    }

    Vec3d forward() {
        float yaw = this.bodyYaw * MathHelper.RADIANS_PER_DEGREE;
        return new Vec3d(-MathHelper.sin(yaw), 0, MathHelper.cos(yaw));
    }

    void faceTo(LivingEntity t) {
        float yaw = (float) (MathHelper.atan2(t.getZ() - this.getZ(), t.getX() - this.getX()) * MathHelper.DEGREES_PER_RADIAN) - 90f;
        this.setYaw(yaw);
        this.setBodyYaw(yaw);
        this.setHeadYaw(yaw);
    }

    /** Удар по конусу 70° на reach блоков (от края хитбокса цели), каждого один раз. */
    protected void coneHit(double reach, float damage, Set<Integer> hit) {
        Vec3d f = this.forward();
        for (LivingEntity e : this.getWorld().getEntitiesByClass(LivingEntity.class, this.getBoundingBox().expand(reach + 1, 1.5, reach + 1),
                e -> e != this && e.isAlive() && !(e instanceof Monster) && !(e instanceof ArmorStandEntity)
                        && !(e instanceof PlayerEntity p && (p.isCreative() || p.isSpectator())))) {
            if (hit.contains(e.getId())) {
                continue;
            }
            Vec3d to = new Vec3d(e.getX() - this.getX(), 0, e.getZ() - this.getZ());
            double d = Math.max(0, to.length() - e.getWidth() / 2);
            if (d > reach || Math.abs(e.getY() - this.getY()) > 2.0) {
                continue;
            }
            if (d > 0.6 && to.normalize().dotProduct(f) < Math.cos(Math.toRadians(70))) {
                continue;
            }
            hit.add(e.getId());
            if (e.damage(this.getDamageSources().mobAttack(this), damage)) {
                e.takeKnockback(0.35, this.getX() - e.getX(), this.getZ() - e.getZ());
                this.onHit(e);
            }
        }
    }

    static class HorrorGoal extends Goal {
        private final HorrorEntity mob;
        private int action = NONE;
        private int tick;
        private int cd = 20;
        private int pathDelay;
        private final Set<Integer> hit = new HashSet<>();

        HorrorGoal(HorrorEntity mob) {
            this.mob = mob;
            this.setControls(EnumSet.of(Control.MOVE, Control.LOOK));
        }

        static boolean valid(LivingEntity t) {
            return t != null && t.isAlive() && !(t instanceof PlayerEntity p && (p.isCreative() || p.isSpectator()));
        }

        @Override
        public boolean canStart() {
            return valid(this.mob.getTarget());
        }

        @Override
        public boolean shouldContinue() {
            return this.action != NONE || this.canStart();
        }

        @Override
        public boolean shouldRunEveryTick() {
            return true;
        }

        private void begin(int a) {
            this.action = a;
            this.tick = 0;
            this.hit.clear();
            this.mob.getNavigation().stop();
            this.mob.getWorld().sendEntityStatus(this.mob, (byte) (STATUS_BASE + a));
            this.mob.attackSound(a);
        }

        @Override
        public void tick() {
            HorrorEntity m = this.mob;
            LivingEntity t = m.getTarget();
            if (!valid(t)) {
                t = null;
            }
            if (this.cd > 0) {
                this.cd--;
            }
            if (this.action != NONE) {
                this.tick++;
                if (this.action == ATTACK) {
                    if (t != null && this.tick < m.attackHit() - 1) {
                        m.faceTo(t);
                    }
                    if (this.tick >= m.attackHit() && this.tick <= m.attackHit() + 1) {
                        m.coneHit(2.6, m.attackDamage(), this.hit);
                    }
                    if (this.tick >= m.attackTicks()) {
                        this.action = NONE;
                        this.cd = m.attackCooldown();
                    }
                } else {
                    m.runLunge(this.tick, t, this.hit);
                    if (this.tick >= m.lungeTicks()) {
                        this.action = NONE;
                        this.cd = m.attackCooldown();
                    }
                }
                return;
            }
            if (t == null) {
                return;
            }
            m.getLookControl().lookAt(t, 30f, 30f);
            Vec3d to = new Vec3d(t.getX() - m.getX(), 0, t.getZ() - m.getZ());
            double d = Math.max(0, to.length() - t.getWidth() / 2 - m.getWidth() / 2);
            boolean see = m.getVisibilityCache().canSee(t);
            if (this.cd <= 0 && see && m.canLunge(d)) {
                m.faceTo(t);
                this.begin(LUNGE);
                return;
            }
            if (this.cd <= 0 && d < 2.2 && see) {
                m.faceTo(t);
                this.begin(ATTACK);
                return;
            }
            if (--this.pathDelay <= 0) {
                this.pathDelay = 6;
                m.getNavigation().startMovingTo(t, 1.1);
            }
        }
    }
}

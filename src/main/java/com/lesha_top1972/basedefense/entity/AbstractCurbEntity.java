package com.lesha_top1972.basedefense.entity;

import com.lesha_top1972.basedefense.combat.ShieldBlocker;
import com.lesha_top1972.basedefense.sound.ModSounds;
import net.minecraft.block.BlockState;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.ai.goal.Goal;
import net.minecraft.entity.damage.DamageSource;
import net.minecraft.entity.decoration.ArmorStandEntity;
import net.minecraft.entity.mob.HostileEntity;
import net.minecraft.entity.mob.Monster;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.particle.BlockStateParticleEffect;
import net.minecraft.particle.DustParticleEffect;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvent;
import net.minecraft.sound.SoundEvents;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;
import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Общее для «Бордюра» и его прислужников: серия ударов (COMBO), реактивный удар (JET), помощники боя,
 * синхронизация действий с клиентом и эффекты струи из локтя.
 * Тайминги совпадают с анимациями tools/curb_gen.py.
 */
public abstract class AbstractCurbEntity extends HostileEntity {

    public static final int NONE = -1;
    public static final int COMBO = 0;
    public static final int JET = 1;
    public static final int SLAM = 2;
    public static final int SPIN = 3;
    public static final int SUMMON = 4;
    public static final int EMERGE = 5;
    public static final byte STATUS_BASE = 70;

    /** «Серия»: удары рукой / ногой / рукой на 13, 23, 33 тике, всего 46 тиков (2.3 с). */
    public static final int COMBO_TICKS = 46;
    static final int[] COMBO_HITS = {13, 23, 33};
    /** «Реактивный удар»: замах 30 т (струя), рывок 30–37 т (≈7 блоков), всего 51 т. */
    public static final int JET_WINDUP = 30;
    public static final int JET_DASH_END = 37;
    public static final int JET_TICKS = 51;
    /** До этого тика удар доворачивает на цель (с небольшим упреждением), потом направление фиксировано. */
    static final int JET_AIM_LOCK = 27;
    static final double JET_SPEED = 1.0;

    // --- сопло в позе замаха (модель без масштаба, блоки: вправо / вверх / вперёд) — из curb_gen.py ---
    public static final double NOZZLE_RIGHT = 0.49;
    public static final double NOZZLE_UP = 1.61;
    public static final double NOZZLE_FWD = -0.41;

    /** Только на клиенте: текущее действие и когда оно началось. */
    public int clientAction = NONE;
    public int clientActionAge;

    protected AbstractCurbEntity(EntityType<? extends HostileEntity> type, World world) {
        super(type, world);
    }

    // --- параметры бойца (босс / прислужник) ---
    abstract float comboDamage();

    abstract float jetDamage();

    /** Масштаб модели (для точки сопла). */
    public abstract float modelScale();

    @Override
    public boolean handleFallDamage(float fallDistance, float damageMultiplier, DamageSource damageSource) {
        return false;
    }

    @Override
    public void handleStatus(byte status) {
        int a = status - STATUS_BASE;
        if (a >= COMBO && a <= EMERGE) {
            this.clientAction = a;
            this.clientActionAge = this.age;
        } else {
            super.handleStatus(status);
        }
    }

    @Override
    public void tick() {
        super.tick();
        if (this.getWorld().isClient) {
            this.clientEffects();
        }
    }

    // =====================================================================
    // Клиент: струя из локтя, пыль рывков
    // =====================================================================
    private static final DustParticleEffect JET_DUST = new DustParticleEffect(new Vector3f(0.35f, 0.62f, 1.0f), 0.9f);

    /** Насколько сильно бьёт струя из правого (0) / левого (1) локтя сейчас, 0..1 (клиент, и для модели). */
    public float jetPower(int side, float tickDelta) {
        float t = this.age + tickDelta - this.clientActionAge;
        if (this.clientAction == JET && side == 0) {
            if (t < 8) {
                return 0f;
            }
            if (t < JET_WINDUP) {
                return Math.min(1f, (t - 8) / 8f) * (0.55f + 0.45f * (t - 8) / (JET_WINDUP - 8));
            }
            if (t < JET_DASH_END + 2) {
                return 1f;
            }
            return Math.max(0f, 1f - (t - JET_DASH_END - 2) / 5f);
        }
        if (this.clientAction == SLAM) {
            if (t >= 12 && t < 30) {
                return Math.min(1f, (t - 12) / 6f) * (t < 22 ? 0.6f : 1f);
            }
        }
        return 0f;
    }

    /** Точка сопла в мире (приблизительно, по позе замаха). */
    public Vec3d nozzlePos(int side) {
        float yaw = this.bodyYaw * 0.017453292f;
        Vec3d fwd = new Vec3d(-MathHelper.sin(yaw), 0, MathHelper.cos(yaw));
        Vec3d right = new Vec3d(-fwd.z, 0, fwd.x);                // правая сторона моба
        double k = this.modelScale();
        double r = side == 0 ? NOZZLE_RIGHT : -NOZZLE_RIGHT;
        return this.getPos().add(right.multiply(r * k)).add(fwd.multiply(NOZZLE_FWD * k)).add(0, NOZZLE_UP * k, 0);
    }

    protected void clientEffects() {
        World w = this.getWorld();
        int t = this.age - this.clientActionAge;
        float yaw = this.bodyYaw * 0.017453292f;
        Vec3d back = new Vec3d(MathHelper.sin(yaw), 0, -MathHelper.cos(yaw));
        for (int side = 0; side < 2; side++) {
            float p = this.jetPower(side, 0f);
            if (p <= 0.05f) {
                continue;
            }
            Vec3d n = this.nozzlePos(side);
            double sp = 0.18 + 0.25 * p;
            w.addParticle(ParticleTypes.SOUL_FIRE_FLAME, n.x, n.y, n.z,
                    back.x * sp + (this.random.nextDouble() - 0.5) * 0.03, 0.02,
                    back.z * sp + (this.random.nextDouble() - 0.5) * 0.03);
            if (this.random.nextFloat() < p * 0.6f) {
                w.addParticle(JET_DUST, n.x + back.x * 0.5, n.y, n.z + back.z * 0.5, 0, 0, 0);
            }
        }
        boolean dash = (this.clientAction == JET && t >= JET_WINDUP && t < JET_DASH_END)
                || (this.clientAction == SLAM && t >= CurbEntity.SLAM_DASH && t < CurbEntity.SLAM_HIT);
        if (dash) {
            w.addParticle(ParticleTypes.CLOUD, this.getX(), this.getY() + 0.1, this.getZ(),
                    back.x * 0.1, 0.03, back.z * 0.1);
        }
    }

    // =====================================================================
    // Помощники боя (сервер)
    // =====================================================================
    Vec3d forward() {
        float yaw = this.getYaw() * 0.017453292f;
        return new Vec3d(-MathHelper.sin(yaw), 0, MathHelper.cos(yaw));
    }

    void faceTo(double x, double z) {
        float yaw = (float) (MathHelper.atan2(z - this.getZ(), x - this.getX()) * 57.29577951308232) - 90.0f;
        this.setYaw(yaw);
        this.setBodyYaw(yaw);
        this.setHeadYaw(yaw);
    }

    void faceTarget(LivingEntity target) {
        this.faceTo(target.getX(), target.getZ());
    }

    /** Повернуться к цели не быстрее maxDeg за тик (доворот между ударами). */
    void turnToward(LivingEntity target, float maxDeg) {
        float want = (float) (MathHelper.atan2(target.getZ() - this.getZ(), target.getX() - this.getX())
                * 57.29577951308232) - 90.0f;
        float d = MathHelper.wrapDegrees(want - this.getYaw());
        float yaw = this.getYaw() + MathHelper.clamp(d, -maxDeg, maxDeg);
        this.setYaw(yaw);
        this.setBodyYaw(yaw);
        this.setHeadYaw(yaw);
    }

    void sendAction(int action) {
        this.getWorld().sendEntityStatus(this, (byte) (STATUS_BASE + action));
    }

    /** Свои не задеваются: только не‑монстры (игроки, жители, големы). */
    List<LivingEntity> victims(double radius) {
        Box box = this.getBoundingBox().expand(radius, 1.5, radius);
        return this.getWorld().getEntitiesByClass(LivingEntity.class, box,
                e -> e != this && e.isAlive() && !(e instanceof Monster) && !(e instanceof ArmorStandEntity)
                        && !(e instanceof PlayerEntity p && (p.isCreative() || p.isSpectator())));
    }

    boolean strike(LivingEntity e, float damage, double knock, double up) {
        e.timeUntilRegen = 0;
        if (!e.damage(this.getDamageSources().mobAttack(this), damage)) {
            return false;
        }
        boolean shielded = e instanceof ShieldBlocker sb && sb.basedefense$blockedThisTick();
        if (!shielded) {
            if (knock > 0) {
                e.takeKnockback(knock, this.getX() - e.getX(), this.getZ() - e.getZ());
            }
            if (up > 0) {
                e.addVelocity(0, up, 0);
            }
            e.velocityModified = true;
        }
        return true;
    }

    /** Удар конусом вперёд: reach — от центра до края цели, halfAngle — половина угла. */
    int coneHit(double reach, double halfAngle, float damage, double knock, double up) {
        Vec3d f = this.forward();
        double cos = Math.cos(Math.toRadians(halfAngle));
        int n = 0;
        for (LivingEntity e : this.victims(reach)) {
            Vec3d to = new Vec3d(e.getX() - this.getX(), 0, e.getZ() - this.getZ());
            if (to.length() - e.getWidth() / 2 > reach) {
                continue;
            }
            if (to.lengthSquared() > 0.25 && to.normalize().dotProduct(f) < cos) {
                continue;
            }
            if (Math.abs(e.getY() - this.getY()) > 2.5) {
                continue;
            }
            if (this.strike(e, damage, knock, up)) {
                n++;
            }
        }
        return n;
    }

    void sound(SoundEvent s, float vol, float pitch) {
        this.getWorld().playSound(null, this.getX(), this.getY(), this.getZ(), s, SoundCategory.HOSTILE, vol, pitch);
    }

    ServerWorld sw() {
        return this.getWorld() instanceof ServerWorld s ? s : null;
    }

    /** Искры и облачко в точке удара. */
    void impactFx(double dist, double height, boolean heavy) {
        ServerWorld s = this.sw();
        if (s == null) {
            return;
        }
        Vec3d p = this.getPos().add(this.forward().multiply(dist)).add(0, height, 0);
        s.spawnParticles(ParticleTypes.CRIT, p.x, p.y, p.z, heavy ? 14 : 7, 0.2, 0.2, 0.2, 0.35);
        s.spawnParticles(ParticleTypes.CLOUD, p.x, p.y, p.z, heavy ? 6 : 2, 0.1, 0.1, 0.1, 0.06);
        if (heavy) {
            s.spawnParticles(ParticleTypes.EXPLOSION, p.x, p.y, p.z, 1, 0, 0, 0, 0);
        }
    }

    /** Кольцо пыли из земли (топот, призыв). */
    void groundRing(Vec3d c, double radius, int points) {
        ServerWorld s = this.sw();
        if (s == null) {
            return;
        }
        BlockState ground = this.getWorld().getBlockState(BlockPos.ofFloored(c.x, c.y - 0.5, c.z));
        for (int i = 0; i < points; i++) {
            double a = i / (double) points * Math.PI * 2;
            double x = c.x + Math.cos(a) * radius;
            double z = c.z + Math.sin(a) * radius;
            if (!ground.isAir()) {
                s.spawnParticles(new BlockStateParticleEffect(ParticleTypes.BLOCK, ground), x, c.y + 0.1, z, 3,
                        0.15, 0.05, 0.15, 0.12);
            }
            s.spawnParticles(ParticleTypes.CLOUD, x, c.y + 0.15, z, 1, 0, 0.03, 0, 0.02);
        }
    }

    // =====================================================================
    // Бой: серия и реактивный удар (общая часть ИИ)
    // =====================================================================
    abstract static class BrawlGoal<E extends AbstractCurbEntity> extends Goal {
        protected final E mob;
        protected int action = NONE;
        protected int tick;
        protected int cooldown = 20;
        protected int lastAction = NONE;
        protected int pathDelay;
        protected int circleTimer;
        protected boolean circleLeft;
        protected int jetCooldown = 60;
        protected Vec3d dashDir = Vec3d.ZERO;
        protected boolean dashHit;
        protected final Set<Integer> hitIds = new HashSet<>();

        BrawlGoal(E mob) {
            this.mob = mob;
            this.setControls(EnumSet.of(Control.MOVE, Control.LOOK, Control.JUMP));
        }

        static boolean valid(LivingEntity t) {
            return t != null && t.isAlive() && !(t instanceof PlayerEntity p && (p.isCreative() || p.isSpectator()));
        }

        /** Скорость погони (множитель к атрибуту). */
        abstract double chaseSpeed();

        /** Перерыв после атаки, тики. */
        abstract int restTicks(int finishedAction);

        /** Перезарядка реактивного удара, тики. */
        abstract int jetCooldownTicks();

        /** Выбор атаки: [действие, вес]. Основа — серия вблизи и реактивный удар с дистанции. */
        protected void options(List<int[]> out, double dist) {
            if (dist < 3.4) {
                out.add(new int[]{COMBO, 60});
                if (this.jetCooldown <= 0) {
                    out.add(new int[]{JET, 18});
                }
            } else if (dist < 8.5 && this.jetCooldown <= 0) {
                out.add(new int[]{JET, 55});
            }
        }

        /** Свои атаки наследника; true — действие обработано. */
        protected boolean runExtra(LivingEntity target) {
            return false;
        }

        /** Перед выбором атаки (смена фазы и т.п.); true — начато особое действие. */
        protected boolean preAction(LivingEntity target, double dist) {
            return false;
        }

        protected void tickCooldowns() {
            if (this.jetCooldown > 0) {
                this.jetCooldown--;
            }
        }

        @Override
        public boolean canStart() {
            return valid(this.mob.getTarget());
        }

        @Override
        public boolean shouldContinue() {
            return this.action != NONE || valid(this.mob.getTarget());
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

        protected void begin(int a) {
            this.action = a;
            this.tick = 0;
            this.hitIds.clear();
            this.dashHit = false;
            this.mob.getNavigation().stop();
            this.mob.sendAction(a);
            if (a == JET) {
                this.jetCooldown = this.jetCooldownTicks();
            }
        }

        protected void finish() {
            this.lastAction = this.action;
            this.cooldown = this.restTicks(this.action);
            this.action = NONE;
        }

        private int pick(double dist) {
            List<int[]> opts = new ArrayList<>();
            this.options(opts, dist);
            int total = 0;
            for (int[] o : opts) {
                if (o[0] == this.lastAction) {
                    o[1] = o[1] * 2 / 3;
                }
                total += o[1];
            }
            if (total <= 0) {
                return NONE;
            }
            int r = this.mob.random.nextInt(total);
            for (int[] o : opts) {
                r -= o[1];
                if (r < 0) {
                    return o[0];
                }
            }
            return opts.get(0)[0];
        }

        @Override
        public void tick() {
            this.tickCooldowns();
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
            if (this.preAction(target, dist)) {
                return;
            }
            if (this.cooldown > 0) {
                this.cooldown--;
                this.maneuver(target, dist);
                return;
            }
            if (!this.mob.getVisibilityCache().canSee(target) || dist > 20) {
                this.maneuver(target, dist);
                return;
            }
            int a = this.pick(dist);
            if (a == NONE) {
                this.maneuver(target, dist);
                return;
            }
            this.mob.faceTarget(target);
            this.begin(a);
        }

        protected void maneuver(LivingEntity target, double dist) {
            if (dist > 2.6 || !this.mob.getVisibilityCache().canSee(target)) {
                if (--this.pathDelay <= 0) {
                    this.pathDelay = 5;
                    this.mob.getNavigation().startMovingTo(target, dist > 5 ? this.chaseSpeed() : 1.0);
                }
            } else {
                this.mob.getNavigation().stop();
                if (--this.circleTimer <= 0) {
                    this.circleTimer = 15 + this.mob.random.nextInt(25);
                    this.circleLeft = this.mob.random.nextBoolean();
                }
                this.mob.getMoveControl().strafeTo(-0.15f, this.circleLeft ? 0.45f : -0.45f);
                this.mob.lookAtEntity(target, 30.0f, 30.0f);
            }
        }

        protected void dash(double speed) {
            this.mob.setVelocity(this.dashDir.x * speed, this.mob.getVelocity().y, this.dashDir.z * speed);
            this.mob.velocityModified = true;
        }

        protected void lunge(double speed) {
            Vec3d f = this.mob.forward().multiply(speed);
            this.mob.addVelocity(f.x, 0.0, f.z);
            this.mob.velocityModified = true;
        }

        private void run(LivingEntity target) {
            E m = this.mob;
            int t = this.tick;
            switch (this.action) {
                case COMBO -> {
                    // перед каждым ударом — доворот на цель (если она оказалась сбоку или сзади)
                    if (target != null && (t < 11 || (t > 15 && t < 21) || (t > 25 && t < 31))) {
                        m.turnToward(target, 40f);
                    }
                    if (t == 11 || t == 21 || t == 31) {
                        this.lunge(t == 21 ? 0.28 : 0.34);
                        m.sound(SoundEvents.ENTITY_PLAYER_ATTACK_SWEEP, 0.6f, 1.5f);
                    }
                    for (int k = 0; k < COMBO_HITS.length; k++) {
                        if (t == COMBO_HITS[k]) {
                            boolean kick = k == 1;
                            if (m.coneHit(kick ? 2.6 : 2.4, 50, m.comboDamage(), kick ? 0.6 : 0.35, 0) > 0) {
                                m.sound(kick ? SoundEvents.ENTITY_PLAYER_ATTACK_KNOCKBACK
                                        : SoundEvents.ENTITY_PLAYER_ATTACK_STRONG, 1.0f, 0.8f);
                            }
                            m.impactFx(1.6, kick ? 0.9 : 1.5, false);
                        }
                    }
                    if (t >= COMBO_TICKS) {
                        this.finish();
                    }
                }
                case JET -> {
                    if (t == 8) {
                        m.sound(ModSounds.CURB_JET_CHARGE, 1.4f, 1.0f);
                    }
                    if (target != null && t <= JET_AIM_LOCK) {
                        // упреждение: целится чуть впереди бегущей цели
                        Vec3d v = target.getVelocity();
                        double lead = t >= JET_AIM_LOCK - 4 ? 3.0 : 0.0;
                        m.faceTo(target.getX() + v.x * lead, target.getZ() + v.z * lead);
                    }
                    if (t == JET_AIM_LOCK) {
                        this.dashDir = m.forward();
                    }
                    if (t == JET_WINDUP) {
                        m.sound(ModSounds.CURB_JET_BURST, 1.6f, 1.0f);
                    }
                    if (t >= JET_WINDUP && t < JET_DASH_END) {
                        if (!this.dashHit) {
                            this.dash(JET_SPEED);
                            this.jetHits();
                        } else {
                            m.setVelocity(0, m.getVelocity().y, 0);
                            m.velocityModified = true;
                        }
                    }
                    if (t == JET_DASH_END) {
                        m.setVelocity(m.getVelocity().multiply(0.2, 1, 0.2));
                        m.velocityModified = true;
                    }
                    if (t >= JET_TICKS) {
                        this.finish();
                    }
                }
                default -> {
                    if (!this.runExtra(target)) {
                        this.finish();
                    }
                }
            }
        }

        /** Попадание рывка: кто впереди на 1.5 блока. Удар один — после него рывок гасится. */
        private void jetHits() {
            E m = this.mob;
            for (LivingEntity e : m.victims(1.6)) {
                Vec3d to = new Vec3d(e.getX() - m.getX(), 0, e.getZ() - m.getZ());
                if (to.lengthSquared() < 0.01 || to.normalize().dotProduct(this.dashDir) > 0.35) {
                    if (this.hitIds.add(e.getId()) && m.strike(e, m.jetDamage(), 1.4, 0.35)) {
                        this.dashHit = true;
                        m.impactFx(1.0, 1.4, true);
                        m.sound(SoundEvents.ENTITY_GENERIC_EXPLODE, 0.7f, 1.5f);
                        m.sound(SoundEvents.ENTITY_IRON_GOLEM_ATTACK, 1.2f, 0.8f);
                    }
                }
            }
        }
    }
}

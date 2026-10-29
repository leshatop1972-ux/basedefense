package com.lesha_top1972.basedefense.entity.enemy.curb;

import com.lesha_top1972.basedefense.combat.ShieldBlocker;
import com.lesha_top1972.basedefense.effect.ModEffects;
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
import net.minecraft.network.packet.s2c.play.StopSoundS2CPacket;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.Identifier;
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
 * Общее для «Бордюра» и его прислужников: серия ударов (COMBO), реактивный удар (JET), отскоки после атак,
 * точная регистрация ударов (по краю хитбокса и по высоте, окна в несколько тиков), синхронизация действий
 * с клиентом, реактивные струи (локти и ноги), отражение урона эффектом «Шипы».
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
    public static final int DODGE_BACK = 6;
    public static final int DODGE_LEFT = 7;
    public static final int DODGE_RIGHT = 8;
    public static final int PILLAR = 9;
    public static final int GUARD = 10;
    public static final int DIVE = 11;
    public static final int DIVE_LAND = 12;
    public static final int YIELD = 13;
    public static final int TAIL = 14;
    public static final int BEAM = 15;
    public static final int EJECT = 16;
    public static final byte STATUS_BASE = 70;

    /** «Серия»: удары рукой / ногой / рукой на 13, 23, 33 тике, всего 46 тиков (2.3 с). */
    public static final int COMBO_TICKS = 46;
    static final int[] COMBO_HITS = {13, 23, 33};
    /** Дальность ударов серии (от центра до края цели): рука / нога. */
    static final double PUNCH_REACH = 3.4;
    static final double KICK_REACH = 3.6;
    /** «Реактивный удар»: замах 30 т (струя), рывок 30–37 т (≈7 блоков), всего 51 т. */
    public static final int JET_WINDUP = 30;
    public static final int JET_DASH_END = 37;
    public static final int JET_TICKS = 51;
    /** До этого тика удар доворачивает на цель (с небольшим упреждением), потом направление фиксировано. */
    static final int JET_AIM_LOCK = 27;
    static final double JET_SPEED = 1.0;
    /** Насколько впереди корпуса достаёт кулак в рывке и ширина полосы удара (с запасом +1 блок). */
    static final double JET_TIP = 2.4;
    static final double JET_HALF_WIDTH = 1.0;
    /** Отскок после атаки: 13 тиков, толчок на 2 тике. */
    public static final int DODGE_TICKS = 13;

    // --- сопло в позе замаха (модель без масштаба, блоки: вправо / вверх / вперёд) — из curb_gen.py ---
    public static final double NOZZLE_RIGHT = 0.49;
    public static final double NOZZLE_UP = 1.61;
    public static final double NOZZLE_FWD = -0.41;

    /** Только на клиенте: текущее действие и когда оно началось. */
    public int clientAction = NONE;
    public int clientActionAge;

    protected AbstractCurbEntity(EntityType<? extends HostileEntity> type, World world) {
        super(type, world);
        this.setStepHeight(1.0f);               // ступеньку в блок проходит не прыгая (и в рывках)
    }

    // --- параметры бойца (босс / прислужник) ---
    abstract float comboDamage();

    abstract float jetDamage();

    /** Шанс отскочить после атаки. */
    abstract float dodgeChance();

    /** Масштаб модели (для точки сопла). */
    public abstract float modelScale();

    @Override
    public boolean handleFallDamage(float fallDistance, float damageMultiplier, DamageSource damageSource) {
        return false;
    }

    /** «Шипы»: ударивший получает 30% нанесённого урона чистым уроном. */
    @Override
    public boolean damage(DamageSource source, float amount) {
        float before = this.getHealth() + this.getAbsorptionAmount();
        boolean hurt = super.damage(source, amount);
        if (hurt && !this.getWorld().isClient) {
            ModEffects.reflectSpikes(this, source, before - (this.getHealth() + this.getAbsorptionAmount()));
        }
        return hurt;
    }

    @Override
    public void handleStatus(byte status) {
        int a = status - STATUS_BASE;
        if (a >= COMBO && a <= EJECT) {
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
    // Клиент: струи из локтей и ног, пыль рывков
    // =====================================================================
    private static final DustParticleEffect JET_DUST = new DustParticleEffect(new Vector3f(0.35f, 0.62f, 1.0f), 0.9f);

    /**
     * Сила струи 0..1 (клиент, и для модели): 0 — правый локоть, 1 — левый локоть, 2 — правая нога, 3 — левая нога.
     */
    public float jetPower(int side, float tickDelta) {
        if (this.deathTime > 0) {
            return 0f;
        }
        float t = this.age + tickDelta - this.clientActionAge;
        if (this.clientAction == YIELD) {
            // «Я не сдамся»: оба локтя на развороте и перед двойным ударом, ноги — в прыжке
            if (side < 2 && ((t >= 18 && t < 30) || (t >= 88 && t < 101))) {
                return t < 30 ? 0.8f : 1f;
            }
            if (side >= 2 && t >= 38 && t < 48) {
                return 0.9f;
            }
            return 0f;
        }
        if (side >= 2) {
            if (this.clientAction != DIVE) {
                return 0f;
            }
            if (t < CurbEntity.DIVE_LIFT - 2) {
                return 0f;
            }
            if (t < CurbEntity.DIVE_LIFT) {
                return 0.4f;
            }
            if (t < CurbEntity.DIVE_APEX) {
                return 1f;
            }
            if (t < CurbEntity.DIVE_FALL) {
                return 0.55f;
            }
            return 0.3f;
        }
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
        if (this.clientAction == DIVE && t >= CurbEntity.DIVE_APEX && t < CurbEntity.DIVE_FALL && side == 0) {
            return 0.6f;                                    // замах в воздухе — правый локоть подталкивает удар
        }
        return 0f;
    }

    Vec3d bodyForward() {
        float yaw = this.bodyYaw * 0.017453292f;
        return new Vec3d(-MathHelper.sin(yaw), 0, MathHelper.cos(yaw));
    }

    /** Точка сопла в мире (приблизительно, по позе замаха; ноги — под подошвой). */
    public Vec3d nozzlePos(int side) {
        Vec3d fwd = this.bodyForward();
        Vec3d right = new Vec3d(-fwd.z, 0, fwd.x);                // правая сторона моба
        double k = this.modelScale();
        if (side >= 2) {
            double r = side == 2 ? 0.13 : -0.13;
            return this.getPos().add(right.multiply(r * k)).add(0, -0.05, 0);
        }
        double r = side == 0 ? NOZZLE_RIGHT : -NOZZLE_RIGHT;
        return this.getPos().add(right.multiply(r * k)).add(fwd.multiply(NOZZLE_FWD * k)).add(0, NOZZLE_UP * k, 0);
    }

    protected void clientEffects() {
        World w = this.getWorld();
        int t = this.age - this.clientActionAge;
        Vec3d back = this.bodyForward().multiply(-1);
        for (int side = 0; side < 4; side++) {
            float p = this.jetPower(side, 0f);
            if (p <= 0.05f) {
                continue;
            }
            Vec3d n = this.nozzlePos(side);
            if (side >= 2) {
                w.addParticle(ParticleTypes.SOUL_FIRE_FLAME, n.x, n.y, n.z,
                        (this.random.nextDouble() - 0.5) * 0.05, -0.25 - 0.3 * p, (this.random.nextDouble() - 0.5) * 0.05);
                if (this.random.nextFloat() < p * 0.5f) {
                    w.addParticle(JET_DUST, n.x, n.y - 0.4, n.z, 0, -0.05, 0);
                }
                continue;
            }
            double sp = 0.18 + 0.25 * p;
            w.addParticle(ParticleTypes.SOUL_FIRE_FLAME, n.x, n.y, n.z,
                    back.x * sp + (this.random.nextDouble() - 0.5) * 0.03, 0.02,
                    back.z * sp + (this.random.nextDouble() - 0.5) * 0.03);
            if (this.random.nextFloat() < p * 0.6f) {
                w.addParticle(JET_DUST, n.x + back.x * 0.5, n.y, n.z + back.z * 0.5, 0, 0, 0);
            }
        }
        boolean dash = (this.clientAction == JET && t >= JET_WINDUP && t < JET_DASH_END)
                || (this.clientAction == SLAM && t >= CurbEntity.SLAM_DASH && t < CurbEntity.SLAM_HIT)
                || (this.clientAction >= DODGE_BACK && this.clientAction <= DODGE_RIGHT && t >= 2 && t < 7);
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

    /** Угол (градусы, со знаком) от взгляда до цели. */
    float angleTo(LivingEntity target) {
        float want = (float) (MathHelper.atan2(target.getZ() - this.getZ(), target.getX() - this.getX())
                * 57.29577951308232) - 90.0f;
        return MathHelper.wrapDegrees(want - this.getYaw());
    }

    /** Повернуться к цели не быстрее maxDeg за тик (доворот между ударами). */
    void turnToward(LivingEntity target, float maxDeg) {
        float yaw = this.getYaw() + MathHelper.clamp(this.angleTo(target), -maxDeg, maxDeg);
        this.setYaw(yaw);
        this.setBodyYaw(yaw);
        this.setHeadYaw(yaw);
    }

    void sendAction(int action) {
        this.getWorld().sendEntityStatus(this, (byte) (STATUS_BASE + action));
    }

    /** Свои не задеваются: только не‑монстры (игроки, жители, големы). Поиск с запасом по высоте. */
    List<LivingEntity> victims(double radius) {
        Box box = this.getBoundingBox().expand(radius + 1.0, 3.0, radius + 1.0);
        return this.getWorld().getEntitiesByClass(LivingEntity.class, box,
                e -> e != this && e.isAlive() && !(e instanceof Monster) && !(e instanceof ArmorStandEntity)
                        && !(e instanceof PlayerEntity p && (p.isCreative() || p.isSpectator())));
    }

    /** Горизонтальное расстояние от центра моба до ближайшей точки хитбокса цели. */
    double edgeDist(LivingEntity e) {
        Box b = e.getBoundingBox();
        double dx = Math.max(Math.max(b.minX - this.getX(), 0), this.getX() - b.maxX);
        double dz = Math.max(Math.max(b.minZ - this.getZ(), 0), this.getZ() - b.maxZ);
        return Math.sqrt(dx * dx + dz * dz);
    }

    /** Цель по высоте в досягаемости: хитбоксы перекрываются с запасом extra сверху и снизу. */
    boolean inHeight(LivingEntity e, double extra) {
        Box b = e.getBoundingBox();
        return b.maxY >= this.getY() - extra && b.minY <= this.getY() + this.getHeight() + extra;
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

    /**
     * Удар конусом вперёд: reach — от центра до края цели, halfAngle — половина угла (вплотную угол не важен),
     * hit — кого уже ударили этим ударом (окно в несколько тиков бьёт каждого один раз; может быть null).
     */
    int coneHit(double reach, double halfAngle, float damage, double knock, double up, Set<Integer> hit) {
        Vec3d f = this.forward();
        double cos = Math.cos(Math.toRadians(halfAngle));
        int n = 0;
        for (LivingEntity e : this.victims(reach)) {
            if (hit != null && hit.contains(e.getId())) {
                continue;
            }
            double d = this.edgeDist(e);
            if (d > reach || !this.inHeight(e, 1.2)) {
                continue;
            }
            Vec3d to = new Vec3d(e.getX() - this.getX(), 0, e.getZ() - this.getZ());
            if (d > 1.0 && to.lengthSquared() > 0.01 && to.normalize().dotProduct(f) < cos) {
                continue;
            }
            if (hit != null) {
                hit.add(e.getId());
            }
            if (this.strike(e, damage, knock, up)) {
                n++;
            }
        }
        return n;
    }

    /** Удар вокруг себя: radius — до края цели. */
    int radiusHit(double radius, double heightExtra, float damage, double knock, double up, Set<Integer> hit) {
        int n = 0;
        for (LivingEntity e : this.victims(radius)) {
            if (hit != null && hit.contains(e.getId())) {
                continue;
            }
            if (this.edgeDist(e) <= radius && this.inHeight(e, heightExtra)) {
                if (hit != null) {
                    hit.add(e.getId());
                }
                if (this.strike(e, damage, knock, up)) {
                    n++;
                }
            }
        }
        return n;
    }

    /** Сервоприводы жужжат не чаще раза в ~10 с. */
    @Override
    public int getMinAmbientSoundDelay() {
        return 200;
    }

    /** Звуки текущей атаки: когда атака кончается (или прерывается), они обрываются у всех игроков рядом. */
    private final Set<Identifier> actionSounds = new HashSet<>();
    boolean trackSounds;

    void sound(SoundEvent s, float vol, float pitch) {
        this.getWorld().playSound(null, this.getX(), this.getY(), this.getZ(), s, SoundCategory.HOSTILE, vol, pitch);
        if (this.trackSounds) {
            this.actionSounds.add(s.getId());
        }
    }

    /** Оборвать звуки атаки (чтобы не звучали дольше самой атаки). */
    void stopActionSounds() {
        this.trackSounds = false;
        if (this.actionSounds.isEmpty()) {
            return;
        }
        if (this.getWorld() instanceof ServerWorld s) {
            for (ServerPlayerEntity p : s.getPlayers(pl -> pl.squaredDistanceTo(this) < 80 * 80)) {
                for (Identifier id : this.actionSounds) {
                    p.networkHandler.sendPacket(new StopSoundS2CPacket(id, SoundCategory.HOSTILE));
                }
            }
        }
        this.actionSounds.clear();
    }

    @Override
    public void onDeath(DamageSource source) {
        if (!this.getWorld().isClient) {
            this.stopActionSounds();
        }
        super.onDeath(source);
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

    /** Кольцо пыли из земли (топот, призыв, приземление). */
    void groundRing(Vec3d c, double radius, int points) {
        ServerWorld s = this.sw();
        if (s == null) {
            return;
        }
        com.lesha_top1972.basedefense.fx.Fx.ring(s, c, radius, points, 0,
                com.lesha_top1972.basedefense.fx.Fx.F_BLOCK | com.lesha_top1972.basedefense.fx.Fx.F_CLOUD);
    }

    /** Безопасно ли отскочить на dist блоков в направлении dir (есть опора, нет стены, лавы и воды). */
    boolean safeLanding(Vec3d dir, double dist) {
        World w = this.getWorld();
        Vec3d p = this.getPos().add(dir.multiply(dist));
        BlockPos feet = BlockPos.ofFloored(p.x, this.getY() + 0.2, p.z);
        if (!w.getBlockState(feet).getCollisionShape(w, feet).isEmpty()
                || !w.getBlockState(feet.up()).getCollisionShape(w, feet.up()).isEmpty()) {
            return false;
        }
        for (int d = 1; d <= 3; d++) {
            BlockPos b = feet.down(d);
            BlockState st = w.getBlockState(b);
            if (!st.getFluidState().isEmpty()) {
                return false;
            }
            if (!st.getCollisionShape(w, b).isEmpty()) {
                return true;
            }
        }
        return false;
    }

    // =====================================================================
    // Бой: серия, реактивный удар, отскоки (общая часть ИИ)
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
        protected Vec3d dashPrev = Vec3d.ZERO;
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
            if (dist < 4.3) {
                out.add(new int[]{COMBO, 60});
                if (this.jetCooldown <= 0) {
                    out.add(new int[]{JET, 18});
                }
            } else if (dist < 9.5 && this.jetCooldown <= 0) {
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

        /** После каких действий можно отскочить. */
        protected boolean canDodgeAfter(int a) {
            return a == COMBO || a == JET || a == SLAM || a == DIVE_LAND || a == TAIL || a == BEAM;
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
            this.mob.setNoGravity(false);
            this.mob.stopActionSounds();
            this.action = NONE;
        }

        protected void begin(int a) {
            this.mob.stopActionSounds();                 // звуки прошлой атаки обрываются
            this.mob.trackSounds = true;
            this.action = a;
            this.tick = 0;
            this.hitIds.clear();
            this.dashHit = false;
            this.climbTicks = 0;
            this.mob.getNavigation().stop();
            this.mob.sendAction(a);
            if (a == JET) {
                this.jetCooldown = this.jetCooldownTicks();
            }
        }

        protected void finish() {
            int done = this.action;
            this.mob.stopActionSounds();
            this.lastAction = done;
            this.cooldown = this.restTicks(done);
            this.action = NONE;
            if (this.canDodgeAfter(done) && this.mob.random.nextFloat() < this.mob.dodgeChance()) {
                this.tryDodge();
            }
        }

        /** Отскок рывком назад или вбок на несколько блоков (если там есть куда приземлиться). */
        private void tryDodge() {
            E m = this.mob;
            LivingEntity target = m.getTarget();
            if (target != null) {
                m.faceTarget(target);
            }
            Vec3d f = m.forward();
            Vec3d left = new Vec3d(f.z, 0, -f.x);
            int[] order = m.random.nextFloat() < 0.5f
                    ? new int[]{DODGE_BACK, m.random.nextBoolean() ? DODGE_LEFT : DODGE_RIGHT}
                    : new int[]{m.random.nextBoolean() ? DODGE_LEFT : DODGE_RIGHT, DODGE_BACK};
            for (int a : order) {
                Vec3d dir = a == DODGE_BACK ? f.multiply(-1) : (a == DODGE_LEFT ? left : left.multiply(-1));
                if (m.safeLanding(dir, 3.5)) {
                    this.dashDir = dir;
                    this.begin(a);
                    return;
                }
            }
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

        /** Сколько тиков рывка уже ушло на подъём по препятствию (не больше CLIMB_MAX за действие). */
        protected int climbTicks;
        static final int CLIMB_MAX = 9;

        /**
         * Рывок в сторону dashDir. Упёрся в блок — не останавливается, а поднимается вверх
         * (≈0.5 блока за тик, до ~4 блоков за рывок) и продолжает рывок поверх препятствия.
         */
        protected void dash(double speed) {
            E m = this.mob;
            double vy = m.getVelocity().y;
            if (m.horizontalCollision && this.climbTicks < CLIMB_MAX) {
                this.climbTicks++;
                vy = Math.max(vy, 0.5);
                m.fallDistance = 0;
            }
            m.setVelocity(this.dashDir.x * speed, vy, this.dashDir.z * speed);
            m.velocityModified = true;
        }

        /** Рывок к точке (горизонтально, не быстрее maxSpeed за тик) с подъёмом по препятствиям. */
        protected void dashTo(double x, double z, double maxSpeed, double stopAt) {
            E m = this.mob;
            double dx = x - m.getX();
            double dz = z - m.getZ();
            double d = Math.sqrt(dx * dx + dz * dz);
            if (d <= stopAt) {
                m.setVelocity(0, m.getVelocity().y, 0);
                m.velocityModified = true;
                return;
            }
            this.dashDir = new Vec3d(dx / d, 0, dz / d);
            this.dash(Math.min(maxSpeed, d - stopAt));
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
                    for (int k = 0; k < COMBO_HITS.length; k++) {
                        int h = COMBO_HITS[k];
                        // за 2 тика до удара — довести взгляд точно на цель, если она в передней полусфере
                        if (t == h - 2 && target != null && Math.abs(m.angleTo(target)) < 80f) {
                            m.faceTarget(target);
                        }
                        if (t == h - 2) {
                            this.lunge(k == 1 ? 0.28 : 0.34);
                            m.sound(SoundEvents.ENTITY_PLAYER_ATTACK_SWEEP, 0.6f, 1.5f);
                            this.hitIds.clear();
                        }
                        // окно попадания: тик до удара, удар и тик после (каждого — один раз)
                        if (t >= h - 1 && t <= h + 1) {
                            boolean kick = k == 1;
                            if (m.coneHit(kick ? KICK_REACH : PUNCH_REACH, 55, m.comboDamage(), kick ? 0.6 : 0.35, 0,
                                    this.hitIds) > 0) {
                                m.sound(ModSounds.CURB_PUNCH, kick ? 1.0f : 0.85f,
                                        (m instanceof CurbMinionEntity ? 1.3f : 0.9f) + (kick ? -0.1f : 0f));
                            }
                            if (t == h) {
                                m.impactFx(1.8, kick ? 0.9 : 1.5, false);
                            }
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
                        this.dashPrev = m.getPos();
                    }
                    if (t > JET_WINDUP && t <= JET_DASH_END && !this.dashHit) {
                        this.jetHits();                 // проверка по отрезку, пройденному за прошлый тик
                    }
                    if (t >= JET_WINDUP && t < JET_DASH_END) {
                        if (!this.dashHit) {
                            this.dashPrev = m.getPos();
                            this.dash(JET_SPEED);
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
                case DODGE_BACK, DODGE_LEFT, DODGE_RIGHT -> {
                    if (t == 2) {
                        m.setVelocity(this.dashDir.x * 0.68, 0.3, this.dashDir.z * 0.68);
                        m.velocityModified = true;
                        m.sound(ModSounds.CURB_JET_BURST, 0.6f, 1.5f);
                    }
                    if (t > 2 && t < 8 && m.horizontalCollision && this.climbTicks < CLIMB_MAX) {
                        this.dash(0.5);                 // упёрся при отскоке — перескакивает
                    }
                    if (t >= DODGE_TICKS) {
                        m.stopActionSounds();
                        this.action = NONE;
                        this.lastAction = DODGE_BACK;
                    }
                }
                default -> {
                    if (!this.runExtra(target)) {
                        this.finish();
                    }
                }
            }
        }

        /**
         * Попадание рывка: цель в полосе от прошлой позиции до кулака (позиция + JET_TIP вперёд) шириной
         * ±JET_HALF_WIDTH — так быстрый рывок не «проскакивает» цель между тиками. Удар один, потом рывок гаснет.
         */
        private void jetHits() {
            E m = this.mob;
            Vec3d a = this.dashPrev;
            Vec3d b = m.getPos().add(this.dashDir.multiply(JET_TIP));
            double len = Math.sqrt((b.x - a.x) * (b.x - a.x) + (b.z - a.z) * (b.z - a.z));
            for (LivingEntity e : m.victims(len + 1.0)) {
                if (!m.inHeight(e, 1.0) || this.hitIds.contains(e.getId())) {
                    continue;
                }
                double ex = e.getX() - a.x;
                double ez = e.getZ() - a.z;
                double along = (ex * this.dashDir.x + ez * this.dashDir.z);
                double side = Math.abs(ex * this.dashDir.z - ez * this.dashDir.x);
                if (along > -0.6 && along < len + e.getWidth() / 2 && side < JET_HALF_WIDTH + e.getWidth() / 2) {
                    this.hitIds.add(e.getId());
                    if (m.strike(e, m.jetDamage(), 1.4, 0.35)) {
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

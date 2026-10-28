package com.lesha_top1972.basedefense.entity.enemy.bloodletter;

import com.lesha_top1972.basedefense.entity.BuildTargets;
import com.lesha_top1972.basedefense.entity.BuiltBlocks;
import com.lesha_top1972.basedefense.entity.enemy.beetle.SiegeBeetleEntity;
import com.lesha_top1972.basedefense.entity.enemy.bloodthirster.BloodKin;
import com.lesha_top1972.basedefense.entity.enemy.sapper.SapperBlast;
import com.lesha_top1972.basedefense.combat.ShieldBlocker;
import com.lesha_top1972.basedefense.fx.Fx;
import com.lesha_top1972.basedefense.sound.ModSounds;
import net.minecraft.block.BlockState;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.ai.control.MoveControl;
import net.minecraft.entity.ai.goal.Goal;
import net.minecraft.entity.ai.goal.LookAtEntityGoal;
import net.minecraft.entity.ai.goal.SwimGoal;
import net.minecraft.entity.attribute.DefaultAttributeContainer;
import net.minecraft.entity.attribute.EntityAttributes;
import net.minecraft.entity.damage.DamageSource;
import net.minecraft.entity.data.DataTracker;
import net.minecraft.entity.data.TrackedData;
import net.minecraft.entity.data.TrackedDataHandlerRegistry;
import net.minecraft.entity.decoration.ArmorStandEntity;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.entity.mob.HostileEntity;
import net.minecraft.entity.mob.MobEntity;
import net.minecraft.entity.mob.Monster;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.particle.DustParticleEffect;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundEvent;
import net.minecraft.util.math.BlockPos;
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
 * «Кровопускатель» — сгорбленный жрец крови ≈2.5 блока, медленный, 90 HP. Модель/анимации — tools/bloodletter_gen.py.
 * <ul>
 * <li>«Кровавые копья»: подходит к игроку на 20 блоков (или к постройке, как осадный жук), останавливается, воздевает
 * руки — над ним веером встают 5 копий из крови, и он по одному бросает их раз в 0.7 с в игрока (или в постройку).
 * Каждое копьё — 6 урона и «Кровотёк», немного ломает блоки и оставляет 1–2 клетки кровавой лужи (как у кровожада).
 * Уворот: стоишь или идёшь шагом — 20% (иначе копьё доводится до цели и попадает), бежишь — 80%. Перезарядка 11 с.</li>
 * <li>«Кровавый вихрь»: игрок ближе 4 блоков — присед, руки в стороны, вокруг на 3 блоках 1.5 с вращается кровавый
 * след (бьёт в радиусе 4): 4 урона, отброс ≈10 блоков, «Замедление II» на 4 с. Перезарядка 10 с.
 * Вихрь прерывает бросок копий (оставшиеся копья рассыпаются).</li>
 * </ul>
 */
public class BloodletterEntity extends HostileEntity implements BloodKin {

    public static final int NONE = -1;
    public static final int BARRAGE = 0;
    public static final int WHIRL = 1;
    public static final int FLING_R = 2;
    public static final int FLING_L = 3;
    public static final byte STATUS_BASE = 70;

    // «Кровавые копья» (тики от начала; совпадают с клипами summon 1.5 с, channel, fling_* 0.6 с — бросок на 0.3 с)
    public static final int SPEARS = 5;
    static final int[] FORM_AT = {8, 12, 16, 20, 24};
    static final int FIRST_LAUNCH = 36;
    static final int LAUNCH_GAP = 14;
    static final int PRIME = 6;
    public static final int BARRAGE_TICKS = FIRST_LAUNCH + LAUNCH_GAP * (SPEARS - 1) + 16;
    static final int BARRAGE_CD = 200;   // 10 с
    static final double BARRAGE_RANGE = 20.0;
    static final float HIT_STILL = 0.8f;
    static final float HIT_RUNNING = 0.2f;

    // «Кровавый вихрь» (клип whirl 2.3 с)
    public static final int WHIRL_TICKS = 46;
    public static final int WHIRL_START = 6;
    public static final int WHIRL_END = 36;
    public static final float WHIRL_SWEEP = 720f;
    public static final double TRAIL_RADIUS = 3.0;
    static final double WHIRL_RADIUS = 4.0;
    static final float WHIRL_DMG = 4f;
    static final int WHIRL_CD = 180;     // 9 с
    static final int SLOW_TICKS = 80;

    private static final TrackedData<Vector3f> AIM = DataTracker.registerData(BloodletterEntity.class,
            TrackedDataHandlerRegistry.VECTOR3F);
    private static final DustParticleEffect DRIP = new DustParticleEffect(new Vector3f(0.55f, 0.03f, 0.06f), 0.9f);
    private static final DustParticleEffect MIST = new DustParticleEffect(new Vector3f(0.42f, 0.02f, 0.05f), 1.8f);

    /** Клиент: текущее действие (по статусу), прошлое — для плавной смены, бросок рукой, угол начала вихря. */
    public int clientAction = NONE;
    public int clientActionAge;
    public int clientPrevAction = NONE;
    public int clientSwitchAge;
    public int clientFling = NONE;
    public int clientFlingAge;
    public float clientWhirlYaw;

    /** Сервер: копья, которые висят над ним (ещё не брошены). */
    final List<BloodSpearEntity> spears = new ArrayList<>();
    BlockPos build;

    public BloodletterEntity(EntityType<? extends HostileEntity> type, World world) {
        super(type, world);
        this.experiencePoints = 15;
        this.moveControl = new SmoothMoveControl(this);
    }

    public static DefaultAttributeContainer.Builder createAttributes() {
        return HostileEntity.createHostileAttributes()
                .add(EntityAttributes.GENERIC_MAX_HEALTH, 90.0)
                .add(EntityAttributes.GENERIC_ARMOR, 4.0)
                .add(EntityAttributes.GENERIC_KNOCKBACK_RESISTANCE, 0.5)
                .add(EntityAttributes.GENERIC_MOVEMENT_SPEED, 0.18)
                .add(EntityAttributes.GENERIC_FOLLOW_RANGE, 48.0)
                .add(EntityAttributes.GENERIC_ATTACK_DAMAGE, 6.0);
    }

    @Override
    protected void initDataTracker() {
        super.initDataTracker();
        this.dataTracker.startTracking(AIM, new Vector3f());
    }

    @Override
    protected void initGoals() {
        this.goalSelector.add(0, new SwimGoal(this));
        this.goalSelector.add(1, new LetterGoal(this));
        this.goalSelector.add(8, new LookAtEntityGoal(this, PlayerEntity.class, 20.0f));
    }

    // =====================================================================
    @Override
    public void handleStatus(byte status) {
        int a = status - STATUS_BASE;
        if (a == FLING_R || a == FLING_L) {
            this.clientFling = a;
            this.clientFlingAge = this.age;
        } else if (a == BARRAGE || a == WHIRL) {
            this.clientPrevAction = this.clientAction;
            this.clientSwitchAge = this.age;
            this.clientAction = a;
            this.clientActionAge = this.age;
            if (a == WHIRL) {
                this.clientWhirlYaw = this.getYaw();
                this.clientFling = NONE;
            }
        } else {
            super.handleStatus(status);
        }
    }

    void sendAction(int a) {
        this.getWorld().sendEntityStatus(this, (byte) (STATUS_BASE + a));
    }

    /** Куда смотрят копья (цель броска). */
    public Vec3d aim() {
        Vector3f v = this.dataTracker.get(AIM);
        return new Vec3d(v.x, v.y, v.z);
    }

    void setAim(Vec3d p) {
        Vector3f cur = this.dataTracker.get(AIM);
        if (Math.abs(cur.x - p.x) + Math.abs(cur.y - p.y) + Math.abs(cur.z - p.z) > 0.05) {
            this.dataTracker.set(AIM, new Vector3f((float) p.x, (float) p.y, (float) p.z));
        }
    }

    /**
     * Где висит копьё slot (0..4) над ним: веер полукругом позади‑над головой (в плоскости поперёк взгляда),
     * слегка покачивается. Общая формула для сервера и клиента (рендер берёт сглаженные позицию и поворот).
     */
    public static Vec3d hoverPos(Vec3d feet, float yawDeg, int slot, float age) {
        double phi = Math.toRadians(20 + 35 * slot);
        double lateral = Math.cos(phi) * 1.7;
        double up = 2.45 + Math.sin(phi) * 1.35 + 0.08 * Math.sin(age * 0.13 + slot * 1.7);
        double back = -0.45 - 0.15 * Math.sin(phi);
        float yaw = yawDeg * MathHelper.RADIANS_PER_DEGREE;
        double fx = -MathHelper.sin(yaw);
        double fz = MathHelper.cos(yaw);
        // «вправо» от взгляда: (−fz, fx)
        return new Vec3d(feet.x + fx * back - fz * lateral, feet.y + up, feet.z + fz * back + fx * lateral);
    }

    /** Угол головы кровавого следа «Вихря» (градусы, как yaw) через t тиков от начала действия. */
    public static float trailYaw(float startYaw, float t) {
        float x = MathHelper.clamp((t - WHIRL_START) / (float) (WHIRL_END - WHIRL_START), 0f, 1f);
        return startYaw + WHIRL_SWEEP * x;
    }

    Vec3d forward() {
        float yaw = this.getYaw() * MathHelper.RADIANS_PER_DEGREE;
        return new Vec3d(-MathHelper.sin(yaw), 0, MathHelper.cos(yaw));
    }

    /** Корпус всегда смотрит туда же, куда сам моб (без доворота клиентом — не дёргается). */
    @Override
    protected float turnHead(float bodyRotation, float headRotation) {
        this.bodyYaw = this.getYaw();
        return headRotation;
    }

    void turnSmooth(double x, double z, float maxDeg) {
        float want = (float) (MathHelper.atan2(z - this.getZ(), x - this.getX()) * MathHelper.DEGREES_PER_RADIAN) - 90f;
        float d = MathHelper.wrapDegrees(want - this.getYaw());
        float yaw = this.getYaw() + MathHelper.clamp(d * 0.2f, -maxDeg, maxDeg);
        this.setYaw(yaw);
        this.setBodyYaw(yaw);
        this.setHeadYaw(yaw);
    }

    static class SmoothMoveControl extends MoveControl {
        SmoothMoveControl(MobEntity e) {
            super(e);
        }

        @Override
        protected float wrapDegrees(float from, float to, float max) {
            float d = MathHelper.wrapDegrees(to - from);
            return from + MathHelper.clamp(d * 0.2f, -6f, 6f);
        }
    }

    static boolean valid(LivingEntity t) {
        return t != null && t.isAlive() && !(t instanceof PlayerEntity p && (p.isCreative() || p.isSpectator()));
    }

    /** Кого задевают копья и вихрь: не монстры, не родня кровожада, не подставки, не игроки в творческом. */
    static boolean hurtable(LivingEntity e) {
        return e.isAlive() && !(e instanceof Monster) && !(e instanceof BloodKin) && !(e instanceof ArmorStandEntity)
                && !(e instanceof PlayerEntity p && (p.isCreative() || p.isSpectator()));
    }

    /**
     * Ломается ли блок от копья. Блок постройки ({@link BuiltBlocks}) — всегда; при осаде постройки (siege) — любая
     * преграда на пути к ней ({@link BuiltBlocks#obstacle}); иначе (бросок в игрока) — только слабые не природные
     * блоки: твёрдость ≤ 3, без блок‑сущности, не листва.
     */
    static boolean spearBreakable(World w, BlockPos p, boolean siege) {
        BlockState st = w.getBlockState(p);
        if (st.isAir() || !st.getFluidState().isEmpty() || st.isOf(com.lesha_top1972.basedefense.block.ModBlocks.BLOOD_POOL)) {
            return false;
        }
        float h = st.getHardness(w, p);
        if (h < 0) {
            return false;
        }
        if (BuiltBlocks.isTarget(st)) {
            return true;
        }
        if (siege) {
            return BuiltBlocks.obstacle(w, p);
        }
        if (w.getBlockEntity(p) != null || SiegeBeetleEntity.natural(st) || st.isIn(net.minecraft.registry.tag.BlockTags.LEAVES)) {
            return false;
        }
        return h <= 3.0f;
    }

    // =====================================================================
    @Override
    protected void mobTick() {
        super.mobTick();
        if (this.age % 20 == 7) {
            if (this.getWorld() instanceof ServerWorld s && SapperBlast.griefing(s) && this.getTarget() == null) {
                this.build = BuildTargets.nearest(s, this.getBlockPos(), BuildTargets.RADIUS, 6);
            } else if (this.getTarget() != null) {
                this.build = null;
            }
        }
    }

    @Override
    public void tick() {
        super.tick();
        World w = this.getWorld();
        if (!w.isClient) {
            return;
        }
        // редкие капли с когтей и из раны маски
        if (this.random.nextInt(10) == 0) {
            Vec3d f = this.forward();
            double side = this.random.nextBoolean() ? 0.45 : -0.45;
            w.addParticle(DRIP, this.getX() + f.x * 0.35 - f.z * side, this.getY() + 0.55, this.getZ() + f.z * 0.35 + f.x * side,
                    0, -0.06, 0);
        }
        float t = this.age - this.clientActionAge;
        if (this.clientAction == BARRAGE && t >= 4 && t < 30) {
            // кровь поднимается струйками к копьям
            for (int k = 0; k < 2; k++) {
                double a = this.random.nextDouble() * Math.PI * 2;
                double r = 0.4 + this.random.nextDouble() * 0.9;
                w.addParticle(DRIP, this.getX() + Math.cos(a) * r, this.getY() + 0.1, this.getZ() + Math.sin(a) * r,
                        0, 0.18 + this.random.nextDouble() * 0.12, 0);
            }
        } else if (this.clientAction == WHIRL && t >= WHIRL_START && t <= WHIRL_END) {
            // голова следа роняет брызги и тёмную взвесь
            float yaw = trailYaw(this.clientWhirlYaw, t) * MathHelper.RADIANS_PER_DEGREE;
            double dx = -MathHelper.sin(yaw);
            double dz = MathHelper.cos(yaw);
            for (int k = 0; k < 3; k++) {
                double r = TRAIL_RADIUS + (this.random.nextDouble() - 0.5) * 0.5;
                double y = 0.2 + this.random.nextDouble() * 1.1;
                w.addParticle(k == 0 ? MIST : DRIP, this.getX() + dx * r, this.getY() + y, this.getZ() + dz * r,
                        dz * 0.08, -0.02, -dx * 0.08);
            }
        }
    }

    @Override
    public void remove(RemovalReason reason) {
        if (!this.getWorld().isClient) {
            this.dropSpears();
        }
        super.remove(reason);
    }

    /** Невыпущенные копья рассыпаются (вихрь прервал бросок, моб умер или исчез). */
    void dropSpears() {
        for (BloodSpearEntity s : this.spears) {
            if (s.isAlive()) {
                s.shatter();
            }
        }
        this.spears.clear();
    }

    // =====================================================================
    // «Кровавый вихрь»: попадания по ходу головы следа
    void whirlHits(int t, Set<Integer> hit) {
        if (t < WHIRL_START || t > WHIRL_END || !(this.getWorld() instanceof ServerWorld s)) {
            return;
        }
        float start = this.whirlYaw;
        float a0 = trailYaw(start, t - 1);
        float a1 = trailYaw(start, t);
        float sweep = a1 - a0 + 4f;
        for (LivingEntity e : s.getEntitiesByClass(LivingEntity.class, this.getBoundingBox().expand(WHIRL_RADIUS + 1, 2.0, WHIRL_RADIUS + 1),
                BloodletterEntity::hurtable)) {
            if (hit.contains(e.getId())) {
                continue;
            }
            double dx = e.getX() - this.getX();
            double dz = e.getZ() - this.getZ();
            double dist = Math.sqrt(dx * dx + dz * dz) - e.getWidth() / 2;
            if (dist > WHIRL_RADIUS || Math.abs(e.getY() - this.getY()) > 2.5) {
                continue;
            }
            float phi = (float) (MathHelper.atan2(dz, dx) * MathHelper.DEGREES_PER_RADIAN) - 90f;
            float d = MathHelper.wrapDegrees(phi - a0 + 2f);
            if (dist > 0.8 && (d < 0 || d > sweep)) {
                continue;
            }
            hit.add(e.getId());
            e.timeUntilRegen = 0;
            if (e.damage(this.getDamageSources().mobAttack(this), WHIRL_DMG)) {
                boolean shielded = e instanceof ShieldBlocker sb && sb.basedefense$blockedThisTick();
                if (!shielded) {
                    // ≈10 блоков: 1.2 по горизонтали + 0.55 вверх (как «Рогом» жука, но дальше)
                    Vec3d away = new Vec3d(dx, 0, dz);
                    away = away.lengthSquared() > 1e-4 ? away.normalize() : this.forward();
                    double kb = 1.2 * (1.0 - e.getAttributeValue(EntityAttributes.GENERIC_KNOCKBACK_RESISTANCE) * 0.5);
                    e.setVelocity(away.x * kb, 0.55, away.z * kb);
                    e.velocityModified = true;
                }
                e.addStatusEffect(new StatusEffectInstance(StatusEffects.SLOWNESS, SLOW_TICKS, 1), this);
                Fx.blood(s, e.getPos().add(0, e.getHeight() * 0.6, 0), 18, 0.5);
                this.playSound(ModSounds.BLOODLETTER_WHIRL_HIT, 1.0f, 0.9f + this.random.nextFloat() * 0.2f);
            }
        }
    }

    float whirlYaw;

    // =====================================================================
    static class LetterGoal extends Goal {
        private final BloodletterEntity mob;
        private int action = NONE;
        private int tick;
        private int barrageCd = 40;
        private int whirlCd = 0;
        private int rest;
        private int pathDelay;
        private int retarget;
        private int launched;
        private boolean nextRight = true;
        private Vec3d lastAim;
        private Vec3d lastTargetPos;
        private LivingEntity tracked;
        private double targetSpeed;
        private final Set<Integer> hit = new HashSet<>();

        LetterGoal(BloodletterEntity mob) {
            this.mob = mob;
            this.setControls(EnumSet.of(Control.MOVE, Control.LOOK));
        }

        @Override
        public boolean canStart() {
            return true;
        }

        @Override
        public boolean shouldContinue() {
            return true;
        }

        @Override
        public boolean shouldRunEveryTick() {
            return true;
        }

        @Override
        public void stop() {
            if (this.action == BARRAGE) {
                this.mob.dropSpears();
            }
            this.action = NONE;
        }

        private void begin(int a) {
            BloodletterEntity m = this.mob;
            this.action = a;
            this.tick = 0;
            this.hit.clear();
            m.getNavigation().stop();
            m.sendAction(a);
            if (a == BARRAGE) {
                this.launched = 0;
                m.spears.clear();
                m.playSound(ModSounds.BLOODLETTER_SUMMON, 1.2f, 0.95f + m.random.nextFloat() * 0.1f);
            } else if (a == WHIRL) {
                m.whirlYaw = m.getYaw();
            }
        }

        /** Цель — ближайший игрок в 32 блоках (держит до 48), пересматривается раз в 2 с. */
        private void pickTarget() {
            BloodletterEntity m = this.mob;
            LivingEntity t = m.getTarget();
            if (t != null && (!valid(t) || m.squaredDistanceTo(t) > 48 * 48)) {
                m.setTarget(null);
                t = null;
            }
            // Кто бьёт (стражник, игрок) — тому и отвечает, на игроков не переключается
            LivingEntity rev = com.lesha_top1972.basedefense.entity.settler.SettlerThreats.revengeTarget(m);
            if (rev != null && valid(rev)) {
                if (t != rev) {
                    m.setTarget(rev);
                }
                return;
            }
            if (t == null && m.getAttacker() instanceof PlayerEntity a && valid(a)) {
                m.setTarget(a);                        // ударили издалека — разворачивается к обидчику
                t = a;
            }
            if (--this.retarget <= 0) {
                this.retarget = 40;
                PlayerEntity p = m.getWorld().getClosestPlayer(m.getX(), m.getY(), m.getZ(), 32.0, e -> valid((LivingEntity) e));
                if (p != null && (t == null || m.squaredDistanceTo(p) + 36 < m.squaredDistanceTo(t))) {
                    m.setTarget(p);
                }
            }
        }

        /** Скорость цели по земле (блоков за тик), сглаженная. */
        private void trackSpeed(LivingEntity t) {
            if (t != this.tracked || this.lastTargetPos == null) {
                this.tracked = t;
                this.lastTargetPos = t == null ? null : t.getPos();
                this.targetSpeed = 0;
                return;
            }
            Vec3d p = t.getPos();
            double v = Math.sqrt((p.x - this.lastTargetPos.x) * (p.x - this.lastTargetPos.x)
                    + (p.z - this.lastTargetPos.z) * (p.z - this.lastTargetPos.z));
            this.targetSpeed = this.targetSpeed * 0.6 + Math.min(v, 1.0) * 0.4;
            this.lastTargetPos = p;
        }

        /** Бежит: спринт (или быстрее обычного шага) — уворот 80%; стоит/идёт шагом — 20%. */
        private boolean running(LivingEntity t) {
            if (t instanceof PlayerEntity p && p.isSprinting() && this.targetSpeed > 0.12) {
                return true;
            }
            return this.targetSpeed > 0.245;
        }

        /** Кто стоит вплотную (игрок, стражник, житель) — для «Кровавого вихря». */
        private LivingEntity closePlayer() {
            BloodletterEntity m = this.mob;
            LivingEntity best = null;
            double bestD = (WHIRL_RADIUS + 0.4) * (WHIRL_RADIUS + 0.4);
            for (LivingEntity e : m.getWorld().getEntitiesByClass(LivingEntity.class,
                    m.getBoundingBox().expand(WHIRL_RADIUS + 0.4, 2.5, WHIRL_RADIUS + 0.4),
                    e -> e != m && valid(e) && hurtable(e) && Math.abs(e.getY() - m.getY()) < 2.5)) {
                double d = m.squaredDistanceTo(e);
                if (d < bestD) {
                    bestD = d;
                    best = e;
                }
            }
            return best;
        }

        private Vec3d targetAim(LivingEntity t) {
            if (valid(t) && this.mob.squaredDistanceTo(t) < 30 * 30) {
                return t.getPos().add(0, t.getHeight() * 0.55, 0);
            }
            if (this.mob.build != null) {
                return Vec3d.ofCenter(this.mob.build);
            }
            return this.lastAim;
        }

        @Override
        public void tick() {
            BloodletterEntity m = this.mob;
            if (this.barrageCd > 0) {
                this.barrageCd--;
            }
            if (this.whirlCd > 0) {
                this.whirlCd--;
            }
            this.pickTarget();
            LivingEntity t = m.getTarget();
            this.trackSpeed(valid(t) ? t : null);
            if (this.action == BARRAGE) {
                if (this.whirlCd <= 0 && this.closePlayer() != null) {
                    m.dropSpears();
                    this.begin(WHIRL);
                    return;
                }
                this.barrageTick(t);
                return;
            }
            if (this.action == WHIRL) {
                this.tick++;
                m.getNavigation().stop();
                m.setVelocity(0, m.getVelocity().y, 0);
                if (this.tick == WHIRL_START) {
                    m.playSound(ModSounds.BLOODLETTER_WHIRL, 1.3f, 0.95f + m.random.nextFloat() * 0.1f);
                }
                m.whirlHits(this.tick, this.hit);
                if (this.tick >= WHIRL_TICKS) {
                    this.action = NONE;
                    this.whirlCd = WHIRL_CD;
                    this.rest = 16;
                }
                return;
            }
            if (this.rest > 0) {
                this.rest--;
            }
            if (this.whirlCd <= 0 && this.rest <= 0 && this.closePlayer() != null) {
                this.begin(WHIRL);
                return;
            }
            if (valid(t)) {
                this.fight(t);
            } else if (m.build != null) {
                this.siege(m.build);
            } else {
                m.getNavigation().stop();
            }
        }

        /** Игрок: подходит на 20 блоков, встаёт и бросает копья; на перезарядке держится в 8–20 блоках. */
        private void fight(LivingEntity t) {
            BloodletterEntity m = this.mob;
            double d = Math.sqrt(m.squaredDistanceTo(t.getX(), m.getY(), t.getZ()));
            boolean see = m.getVisibilityCache().canSee(t);
            m.getLookControl().lookAt(t, 20f, 20f);
            if (this.barrageCd <= 0 && this.rest <= 0 && see && d <= BARRAGE_RANGE) {
                this.lastAim = t.getPos().add(0, t.getHeight() * 0.55, 0);
                m.setAim(this.lastAim);
                this.begin(BARRAGE);
                return;
            }
            if (!see || d > BARRAGE_RANGE - 1.5) {
                if (--this.pathDelay <= 0) {
                    this.pathDelay = 10;
                    m.getNavigation().startMovingTo(t, 1.0);
                }
            } else if (d < 8.0) {
                if (--this.pathDelay <= 0) {
                    this.pathDelay = 10;
                    Vec3d away = m.getPos().subtract(t.getPos()).multiply(1, 0, 1);
                    away = away.lengthSquared() > 1e-3 ? away.normalize() : m.forward().multiply(-1);
                    Vec3d to = m.getPos().add(away.multiply(5));
                    m.getNavigation().startMovingTo(to.x, to.y, to.z, 0.9);
                }
                m.turnSmooth(t.getX(), t.getZ(), 4f);
            } else {
                m.getNavigation().stop();
                m.turnSmooth(t.getX(), t.getZ(), 4f);
            }
        }

        /** Постройка: подходит на 18 блоков и закидывает её копьями. */
        private void siege(BlockPos b) {
            BloodletterEntity m = this.mob;
            Vec3d c = Vec3d.ofCenter(b);
            double d = Math.sqrt(m.squaredDistanceTo(c.x, m.getY(), c.z));
            if (this.barrageCd <= 0 && this.rest <= 0 && d <= BARRAGE_RANGE - 2) {
                this.lastAim = c;
                m.setAim(c);
                this.begin(BARRAGE);
                return;
            }
            if (d > BARRAGE_RANGE - 6) {
                if (--this.pathDelay <= 0) {
                    this.pathDelay = 20;
                    BuildTargets.walkTo(m, c, 1.0);
                }
            } else {
                m.getNavigation().stop();
                m.turnSmooth(c.x, c.z, 4f);
            }
        }

        private void barrageTick(LivingEntity t) {
            BloodletterEntity m = this.mob;
            this.tick++;
            m.getNavigation().stop();
            m.setVelocity(0, m.getVelocity().y, 0);
            Vec3d aim = this.targetAim(t);
            if (aim != null) {
                this.lastAim = aim;
                m.setAim(aim);
                m.turnSmooth(aim.x, aim.z, 5f);
                m.getLookControl().lookAt(aim.x, aim.y, aim.z, 20f, 20f);
            }
            for (int k = 0; k < SPEARS; k++) {
                if (this.tick == FORM_AT[k]) {
                    BloodSpearEntity s = BloodSpearEntity.form(m, k);
                    m.spears.add(s);
                }
            }
            int k = this.launched;
            if (k < SPEARS) {
                int at = FIRST_LAUNCH + k * LAUNCH_GAP;
                BloodSpearEntity s = this.spear(k);
                if (this.tick == at - PRIME) {
                    if (s != null) {
                        s.prime();
                    }
                    m.sendAction(this.nextRight ? FLING_R : FLING_L);
                    this.nextRight = !this.nextRight;
                }
                if (this.tick == at) {
                    if (s != null) {
                        this.launch(s, t);
                        m.spears.remove(s);
                    }
                    this.launched++;
                }
            }
            if (this.tick >= BARRAGE_TICKS) {
                m.dropSpears();
                this.action = NONE;
                this.barrageCd = BARRAGE_CD;
                this.rest = 20;
            }
        }

        private BloodSpearEntity spear(int slot) {
            for (BloodSpearEntity s : this.mob.spears) {
                if (s.slot() == slot && s.isAlive()) {
                    return s;
                }
            }
            return null;
        }

        /** Бросок: в игрока — с броском на уворот, в постройку — в центр блока, иначе — в последнюю точку. */
        private void launch(BloodSpearEntity s, LivingEntity t) {
            BloodletterEntity m = this.mob;
            m.playSound(ModSounds.BLOODLETTER_SPEAR_LAUNCH, 1.1f, 0.9f + m.random.nextFloat() * 0.2f);
            if (valid(t) && m.squaredDistanceTo(t) < 30 * 30) {
                float chance = this.running(t) ? HIT_RUNNING : HIT_STILL;
                if (m.random.nextFloat() < chance) {
                    s.fly(t, t.getPos().add(0, t.getHeight() * 0.55, 0), -1);
                } else {
                    // промах: в землю в 1.6–2.6 блоках сбоку/позади от того места, где цель сейчас
                    Vec3d to = t.getPos().subtract(m.getPos()).multiply(1, 0, 1);
                    to = to.lengthSquared() > 1e-3 ? to.normalize() : m.forward();
                    Vec3d side = new Vec3d(-to.z, 0, to.x).multiply(m.random.nextBoolean() ? 1 : -1);
                    double off = 1.6 + m.random.nextDouble();
                    Vec3d p = t.getPos().add(side.multiply(off)).add(to.multiply((m.random.nextDouble() - 0.3) * 1.5));
                    s.fly(null, p.add(0, 0.1, 0), t.getId());
                }
                return;
            }
            Vec3d p = m.build != null ? Vec3d.ofCenter(m.build) : this.lastAim;
            s.fly(null, p != null ? p : m.getPos().add(m.forward().multiply(10)), -1);
            s.siege = m.build;          // осада: копьё пробивает любую преграду на пути к постройке
        }
    }

    // =====================================================================
    @Override
    protected void playStepSound(BlockPos pos, BlockState state) {
        this.playSound(ModSounds.BLOODLETTER_STEP, 0.45f, 0.85f + this.random.nextFloat() * 0.2f);
    }

    @Override
    protected SoundEvent getAmbientSound() {
        return ModSounds.BLOODLETTER_AMBIENT;
    }

    @Override
    public int getMinAmbientSoundDelay() {
        return 240;
    }

    @Override
    protected SoundEvent getHurtSound(DamageSource source) {
        return ModSounds.BLOODLETTER_HURT;
    }

    @Override
    protected SoundEvent getDeathSound() {
        return ModSounds.BLOODLETTER_DEATH;
    }
}

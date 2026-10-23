package com.lesha_top1972.basedefense.entity;

import com.lesha_top1972.basedefense.block.BloodPoolBlock;
import com.lesha_top1972.basedefense.block.ModBlocks;
import com.lesha_top1972.basedefense.combat.ShieldBlocker;
import net.minecraft.block.BlockState;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.SpawnReason;
import net.minecraft.entity.ai.goal.ActiveTargetGoal;
import net.minecraft.entity.ai.goal.Goal;
import net.minecraft.entity.ai.goal.LookAroundGoal;
import net.minecraft.entity.ai.goal.LookAtEntityGoal;
import net.minecraft.entity.ai.goal.RevengeGoal;
import net.minecraft.entity.ai.goal.SwimGoal;
import net.minecraft.entity.ai.goal.WanderAroundFarGoal;
import net.minecraft.entity.attribute.DefaultAttributeContainer;
import net.minecraft.entity.attribute.EntityAttributes;
import net.minecraft.entity.boss.BossBar;
import net.minecraft.entity.boss.ServerBossBar;
import net.minecraft.entity.damage.DamageSource;
import net.minecraft.entity.data.DataTracker;
import net.minecraft.entity.data.TrackedData;
import net.minecraft.entity.data.TrackedDataHandlerRegistry;
import net.minecraft.entity.decoration.ArmorStandEntity;
import net.minecraft.entity.mob.HostileEntity;
import net.minecraft.entity.passive.IronGolemEntity;
import net.minecraft.entity.passive.MerchantEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.particle.BlockStateParticleEffect;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvent;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Босс «Кровожад»: высокое алое чудовище, 600 HP, две фазы (вторая — с 300 HP).
 * Модель и анимации — tools/bloodthirster_gen.py, тайминги ниже совпадают с анимациями.
 * <p>1‑я фаза (сгорбленный зверь, когти):</p>
 * <ul>
 *   <li>SWING — неуклюжий замах лапой сверху вниз: 8;</li>
 *   <li>DEVOUR — «жадное пожирание»: 3 с быстро сгребает лапами, по 3 урона в секунду;</li>
 *   <li>POUNCE — распрямляется во весь рост и прыгает в точку, где цель была за 0.3 с до прыжка: 15
 *       (успел убежать — увернулся).</li>
 * </ul>
 * <p>Смена фазы (TRANSFORM, 3 с, неуязвим): выпрямляется, венец из шипов, два красно‑чёрных меча.</p>
 * <p>2‑я фаза:</p>
 * <ul>
 *   <li>SLASH — два быстрых разреза мечами: 7 + 7;</li>
 *   <li>THRUST — «пронзающий выпад»: рывок вперёд с двойным уколом: 10;</li>
 *   <li>SUMMON — «зов выводка»: 2 мелких кровожада;</li>
 *   <li>EGGS — «кровавая кладка»: 3 яйца кровожада вокруг (вокруг яиц растёт кровавая лужа);</li>
 *   <li>WALTZ — «багровый вальс» (мощная): 3 с вихря с мечами (по 4 каждые 0.4 с вокруг),
 *       затем крестовой разрез: 13 и сильный отброс.</li>
 * </ul>
 * Скорость в бою ≈ бег игрока − 15% (во 2‑й фазе +7%). Призыв и яйца — раз в 2 минуты.
 * Темп спокойнее, чем у улана.
 */
public class BloodthirsterEntity extends HostileEntity implements BloodKin {

    public static final int NONE = -1;
    public static final int SWING = 0;
    public static final int DEVOUR = 1;
    public static final int POUNCE = 2;
    public static final int LAND = 3;
    public static final int TRANSFORM = 4;
    public static final int SLASH = 5;
    public static final int THRUST = 6;
    public static final int SUMMON = 7;
    public static final int EGGS = 8;
    public static final int WALTZ = 9;
    public static final byte STATUS_BASE = 70;

    /** Длительности (тики), как у анимаций. */
    public static final int SWING_TICKS = 36;
    public static final int DEVOUR_TICKS = 72;
    public static final int POUNCE_WINDUP = 20;
    public static final int LAND_TICKS = 24;
    public static final int TRANSFORM_TICKS = 60;
    /** На этом тике смены фазы появляются мечи и венец (и меняется облик). */
    public static final int TRANSFORM_SWITCH = 44;
    public static final int SLASH_TICKS = 32;
    public static final int THRUST_TICKS = 26;
    public static final int SUMMON_TICKS = 40;
    public static final int EGGS_TICKS = 44;
    public static final int WALTZ_OPEN = 12;
    public static final int WALTZ_SPIN_END = 72;
    public static final int WALTZ_TICKS = 100;

    /** Скорость: 0.23 × 1.4 ≈ 0.32 → бег игрока − 15% (как у рыцаря 0.23 × 1.49 = 90%). */
    static final double BASE_SPEED = 0.23;
    static final double CHASE = 1.4;
    public static final float PHASE2_HEALTH = 300f;
    /** Во 2‑й фазе скорость +7%. */
    static final double PHASE2_SPEED = 1.07;
    /** Перезарядка «зова выводка» и «кровавой кладки» — по 2 минуты. */
    public static final int SUMMON_CD = 2400;
    public static final int EGGS_CD = 2400;

    private static final TrackedData<Boolean> PHASE2 =
            DataTracker.registerData(BloodthirsterEntity.class, TrackedDataHandlerRegistry.BOOLEAN);

    private final ServerBossBar bossBar = new ServerBossBar(this.getDisplayName(), BossBar.Color.RED,
            BossBar.Style.NOTCHED_10);
    private boolean transforming;
    /** Раз в 7 с в бою рядом появляется кровавая лужа. */
    public static final int POOL_EVERY = 140;
    private int poolTimer = POOL_EVERY;

    /** Только клиент: текущее действие и когда началось. */
    public int clientAction = NONE;
    public int clientActionAge;

    public BloodthirsterEntity(EntityType<? extends HostileEntity> type, World world) {
        super(type, world);
        this.experiencePoints = 150;
        this.setPersistent();
        this.setStepHeight(1.1f);
    }

    public static DefaultAttributeContainer.Builder createAttributes() {
        return HostileEntity.createHostileAttributes()
                .add(EntityAttributes.GENERIC_MAX_HEALTH, 600.0)
                .add(EntityAttributes.GENERIC_MOVEMENT_SPEED, BASE_SPEED)
                .add(EntityAttributes.GENERIC_FOLLOW_RANGE, 40.0)
                .add(EntityAttributes.GENERIC_ATTACK_DAMAGE, 8.0)
                .add(EntityAttributes.GENERIC_ARMOR, 6.0)
                .add(EntityAttributes.GENERIC_KNOCKBACK_RESISTANCE, 0.85);
    }

    @Override
    protected void initDataTracker() {
        super.initDataTracker();
        this.dataTracker.startTracking(PHASE2, false);
    }

    /** Вторая фаза (видно и на клиенте: другая текстура, мечи, венец). */
    public boolean isPhase2() {
        return this.dataTracker.get(PHASE2);
    }

    void setPhase2() {
        this.dataTracker.set(PHASE2, true);
        this.applyPhaseSpeed();
    }

    /** Скорость по фазе: во 2‑й на 7% быстрее (и шаг, и погоня). */
    private void applyPhaseSpeed() {
        var speed = this.getAttributeInstance(EntityAttributes.GENERIC_MOVEMENT_SPEED);
        if (speed != null) {
            speed.setBaseValue(this.isPhase2() ? BASE_SPEED * PHASE2_SPEED : BASE_SPEED);
        }
    }

    @Override
    protected void initGoals() {
        this.goalSelector.add(0, new SwimGoal(this));
        this.goalSelector.add(2, new BrawlGoal(this));
        this.goalSelector.add(7, new WanderAroundFarGoal(this, 0.7));
        this.goalSelector.add(8, new LookAtEntityGoal(this, PlayerEntity.class, 16.0f));
        this.goalSelector.add(8, new LookAroundGoal(this));
        this.targetSelector.add(1, new RevengeGoal(this));
        this.targetSelector.add(2, new ActiveTargetGoal<>(this, PlayerEntity.class, true));
        this.targetSelector.add(3, new ActiveTargetGoal<>(this, MerchantEntity.class, true));
        this.targetSelector.add(3, new ActiveTargetGoal<>(this, IronGolemEntity.class, true));
    }

    // --- полоска босса ---
    @Override
    public void onStartedTrackingBy(ServerPlayerEntity player) {
        super.onStartedTrackingBy(player);
        this.bossBar.addPlayer(player);
    }

    @Override
    public void onStoppedTrackingBy(ServerPlayerEntity player) {
        super.onStoppedTrackingBy(player);
        this.bossBar.removePlayer(player);
    }

    @Override
    public void setCustomName(Text name) {
        super.setCustomName(name);
        this.bossBar.setName(this.getDisplayName());
    }

    @Override
    protected void mobTick() {
        super.mobTick();
        this.bossBar.setPercent(this.getHealth() / this.getMaxHealth());
        if (this.getTarget() != null && this.getTarget().isAlive() && --this.poolTimer <= 0) {
            this.poolTimer = POOL_EVERY;
            this.spawnBossPool();
        }
    }

    /** Лужа крестом (5 клеток + 0–3 случайных) в 2.5–6 блоках от босса; высыхает сама через 15 с. */
    private void spawnBossPool() {
        World w = this.getWorld();
        double a = this.random.nextDouble() * Math.PI * 2;
        double r = 2.5 + this.random.nextDouble() * 3.5;
        BlockPos center = this.groundSpot(this.getX() + Math.cos(a) * r, this.getZ() + Math.sin(a) * r);
        if (center == null) {
            return;
        }
        List<BlockPos> cells = new ArrayList<>(List.of(center, center.north(), center.south(), center.east(), center.west()));
        for (int k = this.random.nextInt(4); k > 0; k--) {
            cells.add(center.add(this.random.nextInt(3) - 1 + (this.random.nextBoolean() ? 1 : -1), 0,
                    this.random.nextInt(3) - 1));
        }
        BlockState pool = ModBlocks.BLOOD_POOL.getDefaultState();
        for (BlockPos c : cells) {
            for (int dy = 1; dy >= -1; dy--) {
                BlockPos p = c.up(dy);
                if (w.isAir(p) && w.getBlockState(p.down()).isSideSolidFullSquare(w, p.down(), Direction.UP)) {
                    w.setBlockState(p, pool);
                    w.scheduleBlockTick(p, ModBlocks.BLOOD_POOL, BloodPoolBlock.BOSS_POOL_TICKS + this.random.nextInt(20));
                    break;
                }
            }
        }
        BloodFx.splash(w, center.getX() + 0.5, center.getY() + 0.3, center.getZ() + 0.5, 16, 0.8);
        this.sound(SoundEvents.ENTITY_SLIME_SQUISH, 1.0f, 0.5f);
    }

    @Override
    public void writeCustomDataToNbt(NbtCompound nbt) {
        super.writeCustomDataToNbt(nbt);
        nbt.putBoolean("Phase2", this.isPhase2());
    }

    @Override
    public void readCustomDataFromNbt(NbtCompound nbt) {
        super.readCustomDataFromNbt(nbt);
        this.dataTracker.set(PHASE2, nbt.getBoolean("Phase2"));
        this.applyPhaseSpeed();
        if (this.hasCustomName()) {
            this.bossBar.setName(this.getDisplayName());
        }
    }

    @Override
    public boolean handleFallDamage(float fallDistance, float damageMultiplier, DamageSource damageSource) {
        return false;
    }

    /** Во время смены облика неуязвим; свой выводок его не ранит. */
    @Override
    public boolean damage(DamageSource source, float amount) {
        if (this.transforming || source.getAttacker() instanceof BloodKin) {
            return false;
        }
        boolean hurt = super.damage(source, amount);
        if (hurt && amount >= 2f) {
            BloodFx.splash(this.getWorld(), this.getX(), this.getBodyY(0.6), this.getZ(), 6, 0.4);
        }
        return hurt;
    }

    @Override
    public void onDeath(DamageSource source) {
        super.onDeath(source);
        BloodFx.splash(this.getWorld(), this.getX(), this.getBodyY(0.5), this.getZ(), 60, 1.2);
    }

    @Override
    public void handleStatus(byte status) {
        int a = status - STATUS_BASE;
        if (a >= SWING && a <= WALTZ) {
            this.clientAction = a;
            this.clientActionAge = this.age;
        } else {
            super.handleStatus(status);
        }
    }

    @Override
    protected SoundEvent getAmbientSound() {
        return SoundEvents.ENTITY_HOGLIN_AMBIENT;
    }

    @Override
    protected SoundEvent getHurtSound(DamageSource source) {
        return SoundEvents.ENTITY_RAVAGER_HURT;
    }

    @Override
    protected SoundEvent getDeathSound() {
        return SoundEvents.ENTITY_RAVAGER_DEATH;
    }

    @Override
    public float getSoundPitch() {
        return 0.65f + this.random.nextFloat() * 0.1f;
    }

    @Override
    protected void playStepSound(BlockPos pos, BlockState state) {
        this.playSound(SoundEvents.ENTITY_RAVAGER_STEP, 0.35f, 1.3f);
    }

    // --- помощники боя (сервер) ---
    Vec3d forward() {
        float yaw = this.getYaw() * MathHelper.RADIANS_PER_DEGREE;
        return new Vec3d(-MathHelper.sin(yaw), 0, MathHelper.cos(yaw));
    }

    void faceTarget(LivingEntity target) {
        double dx = target.getX() - this.getX();
        double dz = target.getZ() - this.getZ();
        float yaw = (float) (MathHelper.atan2(dz, dx) * MathHelper.DEGREES_PER_RADIAN) - 90.0f;
        this.setYaw(yaw);
        this.setBodyYaw(yaw);
        this.setHeadYaw(yaw);
    }

    void sendAction(int action) {
        this.getWorld().sendEntityStatus(this, (byte) (STATUS_BASE + action));
    }

    List<LivingEntity> victims(double radius) {
        Box box = this.getBoundingBox().expand(radius, 1.0, radius);
        return this.getWorld().getEntitiesByClass(LivingEntity.class, box,
                e -> e != this && e.isAlive() && !(e instanceof BloodKin) && !(e instanceof ArmorStandEntity)
                        && !(e instanceof PlayerEntity p && (p.isCreative() || p.isSpectator())));
    }

    /** Попадание: урон, отброс (кроме заблокированного щитом), брызги крови. */
    void strike(LivingEntity e, float damage, double knock, double up, boolean multiHit) {
        if (multiHit) {
            e.timeUntilRegen = 0;
        }
        if (e.damage(this.getDamageSources().mobAttack(this), damage)) {
            boolean shielded = e instanceof ShieldBlocker sb && sb.basedefense$blockedThisTick();
            if (!shielded) {
                if (knock > 0) {
                    e.takeKnockback(knock, this.getX() - e.getX(), this.getZ() - e.getZ());
                }
                if (up > 0) {
                    e.addVelocity(0, up, 0);
                }
                e.velocityModified = true;
                BloodFx.splash(this.getWorld(), e.getX(), e.getBodyY(0.6), e.getZ(), 10, 0.3);
            }
        }
    }

    /** Удар конусом вперёд: reach — от центра до края цели, halfAngle — половина угла. */
    int coneHit(double reach, double halfAngle, float damage, double knock, double up, boolean multiHit) {
        Vec3d f = this.forward();
        double cos = Math.cos(Math.toRadians(halfAngle));
        int n = 0;
        for (LivingEntity e : this.victims(reach)) {
            Vec3d to = new Vec3d(e.getX() - this.getX(), 0, e.getZ() - this.getZ());
            if (to.length() - e.getWidth() / 2 > reach) {
                continue;
            }
            if (to.lengthSquared() > 0.36 && to.normalize().dotProduct(f) < cos) {
                continue;
            }
            this.strike(e, damage, knock, up, multiHit);
            n++;
        }
        return n;
    }

    int radiusHit(double radius, float damage, double knock, double up, boolean multiHit) {
        int n = 0;
        for (LivingEntity e : this.victims(radius)) {
            double dist = Math.sqrt(this.squaredDistanceTo(e.getX(), this.getY(), e.getZ())) - e.getWidth() / 2;
            if (dist <= radius) {
                this.strike(e, damage, knock, up, multiHit);
                n++;
            }
        }
        return n;
    }

    void push(double radius, double knock, double up) {
        for (LivingEntity e : this.victims(radius)) {
            e.takeKnockback(knock, this.getX() - e.getX(), this.getZ() - e.getZ());
            e.addVelocity(0, up, 0);
            e.velocityModified = true;
        }
    }

    void sound(SoundEvent s, float vol, float pitch) {
        this.getWorld().playSound(null, this.getX(), this.getY(), this.getZ(), s, SoundCategory.HOSTILE, vol, pitch);
    }

    /** Брызги крови в точке перед собой на расстоянии d и высоте y. */
    void bloodAhead(double d, double y, int count, double spread) {
        Vec3d p = this.getPos().add(this.forward().multiply(d));
        BloodFx.splash(this.getWorld(), p.x, this.getY() + y, p.z, count, spread);
    }

    void groundDust(double d, double radius, int count) {
        if (!(this.getWorld() instanceof ServerWorld s)) {
            return;
        }
        Vec3d p = this.getPos().add(this.forward().multiply(d));
        BlockState ground = this.getWorld().getBlockState(BlockPos.ofFloored(p.x, this.getY() - 0.5, p.z));
        if (!ground.isAir()) {
            s.spawnParticles(new BlockStateParticleEffect(ParticleTypes.BLOCK, ground), p.x, this.getY() + 0.1, p.z,
                    count, radius, 0.1, radius, 0.2);
        }
    }

    int countNearby(Class<? extends LivingEntity> type, double radius) {
        return this.getWorld().getEntitiesByClass(type, this.getBoundingBox().expand(radius), LivingEntity::isAlive).size();
    }

    /** Свободная точка на земле около (x, z) не дальше 4 блоков по высоте от улана, или null. */
    BlockPos groundSpot(double x, double z) {
        BlockPos base = BlockPos.ofFloored(x, this.getY(), z);
        for (int dy = 3; dy >= -4; dy--) {
            BlockPos p = base.up(dy);
            World w = this.getWorld();
            if (w.isAir(p) && w.isAir(p.up()) && w.getBlockState(p.down()).isSideSolidFullSquare(w, p.down(), Direction.UP)) {
                return p;
            }
        }
        return null;
    }

    void spawnBloodlings(LivingEntity target) {
        if (!(this.getWorld() instanceof ServerWorld s)) {
            return;
        }
        Vec3d f = this.forward();
        Vec3d side = new Vec3d(-f.z, 0, f.x);
        for (int k = -1; k <= 1; k += 2) {
            Vec3d p = this.getPos().add(f.multiply(1.6)).add(side.multiply(2.4 * k));
            BlockPos spot = this.groundSpot(p.x, p.z);
            if (spot == null) {
                spot = this.getBlockPos();
            }
            BloodlingEntity b = ModEntities.BLOODLING.create(s);
            if (b == null) {
                continue;
            }
            b.refreshPositionAndAngles(spot.getX() + 0.5, spot.getY(), spot.getZ() + 0.5, this.getYaw(), 0f);
            b.initialize(s, s.getLocalDifficulty(spot), SpawnReason.MOB_SUMMONED, null, null);
            if (target != null) {
                b.setTarget(target);
            }
            s.spawnEntity(b);
            BloodFx.splash(s, b.getX(), b.getY() + 0.4, b.getZ(), 24, 0.5);
        }
    }

    void spawnEggs() {
        if (!(this.getWorld() instanceof ServerWorld s)) {
            return;
        }
        double start = this.random.nextDouble() * Math.PI * 2;
        for (int k = 0; k < 3; k++) {
            double a = start + k * (Math.PI * 2 / 3) + (this.random.nextDouble() - 0.5) * 0.6;
            double r = 5.0 + this.random.nextDouble() * 3.0;
            BlockPos spot = this.groundSpot(this.getX() + Math.cos(a) * r, this.getZ() + Math.sin(a) * r);
            if (spot == null) {
                continue;
            }
            BloodEggEntity egg = ModEntities.BLOOD_EGG.create(s);
            if (egg == null) {
                continue;
            }
            egg.refreshPositionAndAngles(spot.getX() + 0.5, spot.getY(), spot.getZ() + 0.5,
                    this.random.nextFloat() * 360f, 0f);
            s.spawnEntity(egg);
            BloodFx.splash(s, egg.getX(), egg.getY() + 0.5, egg.getZ(), 30, 0.6);
        }
    }

    /** Бой: выбор атаки по расстоянию и фазе, тайминги ударов, прыжок, смена фазы. */
    static class BrawlGoal extends Goal {
        private final BloodthirsterEntity boss;
        private int action = NONE;
        private int tick;
        private int cooldown = 30;
        private int lastAction = NONE;
        private int pathDelay;
        private int pounceCd = 60;
        private int waltzCd = 200;
        private int summonCd = 160;
        private int eggsCd = 260;
        private Vec3d aim = Vec3d.ZERO;
        private Vec3d leapVel = Vec3d.ZERO;
        private Vec3d dashDir = Vec3d.ZERO;
        private final Set<Integer> hitIds = new HashSet<>();

        BrawlGoal(BloodthirsterEntity boss) {
            this.boss = boss;
            this.setControls(EnumSet.of(Control.MOVE, Control.LOOK, Control.JUMP));
        }

        private static boolean valid(LivingEntity t) {
            return t != null && t.isAlive() && !(t instanceof PlayerEntity p && (p.isCreative() || p.isSpectator()));
        }

        @Override
        public boolean canStart() {
            return valid(this.boss.getTarget());
        }

        @Override
        public boolean shouldContinue() {
            return this.action != NONE || valid(this.boss.getTarget());
        }

        @Override
        public boolean shouldRunEveryTick() {
            return true;
        }

        @Override
        public void start() {
            this.boss.setAttacking(true);
        }

        @Override
        public void stop() {
            this.boss.setAttacking(false);
            this.boss.getNavigation().stop();
            if (this.action == TRANSFORM) {
                this.finishTransform();
            }
            this.boss.transforming = false;
            this.action = NONE;
        }

        private void begin(int a) {
            this.action = a;
            this.tick = 0;
            this.hitIds.clear();
            this.boss.getNavigation().stop();
            this.boss.sendAction(a);
        }

        private void finish(int cooldownTicks) {
            this.lastAction = this.action;
            this.action = NONE;
            this.cooldown = cooldownTicks;
        }

        private int pick(double dist) {
            List<int[]> options = new ArrayList<>();
            BloodthirsterEntity b = this.boss;
            if (!b.isPhase2()) {
                if (dist < 4.6) {
                    options.add(new int[]{SWING, 50});
                    options.add(new int[]{DEVOUR, 35});
                }
                if (this.pounceCd <= 0 && dist > 3.0 && dist < 16) {
                    options.add(new int[]{POUNCE, dist < 4.6 ? 12 : 60});
                }
            } else {
                if (dist < 4.8) {
                    options.add(new int[]{SLASH, 45});
                    options.add(new int[]{THRUST, 15});
                } else if (dist < 11) {
                    options.add(new int[]{THRUST, 45});
                }
                if (this.waltzCd <= 0 && dist < 11) {
                    options.add(new int[]{WALTZ, 30});
                }
                if (this.summonCd <= 0 && dist < 20 && b.countNearby(BloodlingEntity.class, 32) < 4) {
                    options.add(new int[]{SUMMON, 25});
                }
                if (this.eggsCd <= 0 && dist < 20 && b.countNearby(BloodEggEntity.class, 32) < 6) {
                    options.add(new int[]{EGGS, 25});
                }
            }
            int total = 0;
            for (int[] o : options) {
                if (o[0] == this.lastAction) {
                    o[1] /= 3;
                }
                total += o[1];
            }
            if (total <= 0) {
                return NONE;
            }
            int r = b.random.nextInt(total);
            for (int[] o : options) {
                r -= o[1];
                if (r < 0) {
                    return o[0];
                }
            }
            return NONE;
        }

        @Override
        public void tick() {
            if (this.pounceCd > 0) {
                this.pounceCd--;
            }
            if (this.waltzCd > 0) {
                this.waltzCd--;
            }
            if (this.summonCd > 0) {
                this.summonCd--;
            }
            if (this.eggsCd > 0) {
                this.eggsCd--;
            }
            BloodthirsterEntity b = this.boss;
            LivingEntity target = b.getTarget();
            boolean hasTarget = valid(target);
            if (this.action != NONE) {
                this.tick++;
                this.runAction(hasTarget ? target : null);
                return;
            }
            if (!hasTarget) {
                return;
            }
            double dist = b.distanceTo(target);
            b.getLookControl().lookAt(target, 30.0f, 30.0f);

            if (!b.isPhase2() && b.getHealth() <= PHASE2_HEALTH) {
                b.faceTarget(target);
                b.transforming = true;
                this.begin(TRANSFORM);
                return;
            }
            if (this.cooldown > 0) {
                this.cooldown--;
                this.chase(target, dist);
                return;
            }
            if (!b.getVisibilityCache().canSee(target) || dist > 24) {
                this.chase(target, dist);
                return;
            }
            int a = this.pick(dist);
            if (a == NONE) {
                this.chase(target, dist);
                return;
            }
            b.faceTarget(target);
            this.begin(a);
        }

        private void chase(LivingEntity target, double dist) {
            BloodthirsterEntity b = this.boss;
            if (dist > 3.4 || !b.getVisibilityCache().canSee(target)) {
                if (--this.pathDelay <= 0) {
                    this.pathDelay = 8;
                    b.getNavigation().startMovingTo(target, CHASE);
                }
            } else {
                b.getNavigation().stop();
                b.lookAtEntity(target, 30.0f, 30.0f);
            }
        }

        private void dash(double speed) {
            BloodthirsterEntity b = this.boss;
            b.setVelocity(this.dashDir.x * speed, b.getVelocity().y, this.dashDir.z * speed);
            b.velocityModified = true;
        }

        private void finishTransform() {
            BloodthirsterEntity b = this.boss;
            b.setPhase2();
            b.transforming = false;
        }

        private void runAction(LivingEntity target) {
            BloodthirsterEntity b = this.boss;
            int t = this.tick;
            switch (this.action) {
                case SWING -> {
                    // замах 0–17, удар на 20, тяжёлое восстановление
                    if (target != null && t < 15) {
                        b.faceTarget(target);
                    }
                    if (t == 10) {
                        b.sound(SoundEvents.ENTITY_RAVAGER_ATTACK, 1.0f, 0.7f);
                    }
                    if (t == 20) {
                        b.coneHit(4.6, 70, 8f, 1.2, 0.3, false);
                        b.sound(SoundEvents.ENTITY_PLAYER_ATTACK_SWEEP, 1.2f, 0.55f);
                        b.groundDust(3.0, 0.8, 20);
                        b.bloodAhead(3.0, 0.6, 12, 0.6);
                    }
                    if (t >= SWING_TICKS) {
                        this.finish(34);
                    }
                }
                case DEVOUR -> {
                    // наклон 0–8, сгребает 8–66 (по 3 урона в 28, 48, 68), выпрямляется
                    if (target != null) {
                        b.faceTarget(target);
                        if (t >= 8 && t < 66 && b.distanceTo(target) > 2.4) {
                            Vec3d f = b.forward().multiply(0.06);
                            b.addVelocity(f.x, 0, f.z);
                        }
                    }
                    if (t >= 8 && t < 68 && t % 3 == 0) {
                        b.bloodAhead(2.0, 1.3 + b.random.nextDouble() * 0.6, 3, 0.5);
                    }
                    if (t >= 8 && t < 68 && t % 6 == 0) {
                        b.sound(SoundEvents.ENTITY_GENERIC_EAT, 1.0f, 0.5f + b.random.nextFloat() * 0.2f);
                    }
                    if (t == 28 || t == 48 || t == 68) {
                        b.coneHit(3.4, 60, 3f, 0.15, 0, true);
                        b.sound(SoundEvents.ENTITY_PLAYER_ATTACK_WEAK, 1.0f, 0.6f);
                    }
                    if (t >= DEVOUR_TICKS) {
                        this.finish(40);
                    }
                }
                case POUNCE -> this.runPounce(target);
                case LAND -> {
                    if (t >= LAND_TICKS) {
                        this.pounceCd = 140;
                        this.finish(30);
                    }
                }
                case TRANSFORM -> {
                    b.getNavigation().stop();
                    if (t == 1) {
                        b.sound(SoundEvents.ENTITY_RAVAGER_ROAR, 1.4f, 0.6f);
                        BloodFx.splash(b.getWorld(), b.getX(), b.getBodyY(0.6), b.getZ(), 30, 0.8);
                    }
                    if (t < TRANSFORM_SWITCH && t % 5 == 0) {
                        BloodFx.splash(b.getWorld(), b.getX(), b.getBodyY(0.3 + b.random.nextDouble() * 0.5),
                                b.getZ(), 8, 0.6);
                    }
                    if (t == TRANSFORM_SWITCH) {
                        this.finishTransform();
                        b.transforming = true;                       // неуязвим до конца анимации
                        b.sound(SoundEvents.ENTITY_WARDEN_ROAR, 1.2f, 1.25f);
                        BloodFx.splash(b.getWorld(), b.getX(), b.getBodyY(0.5), b.getZ(), 80, 1.4);
                        b.push(4.0, 1.4, 0.3);
                    }
                    if (t >= TRANSFORM_TICKS) {
                        b.transforming = false;
                        this.summonCd = 120;
                        this.eggsCd = 240;
                        this.waltzCd = 160;
                        this.finish(20);
                    }
                }
                case SLASH -> {
                    if (target != null && t < 8) {
                        b.faceTarget(target);
                    }
                    if (t == 7 || t == 14) {
                        b.sound(SoundEvents.ENTITY_PLAYER_ATTACK_SWEEP, 1.1f, t == 7 ? 0.8f : 0.95f);
                    }
                    if (t == 9 || t == 16) {
                        if (b.coneHit(4.6, 85, 7f, 0.6, 0.1, true) == 0) {
                            b.bloodAhead(2.6, 1.6, 4, 0.8);
                        }
                    }
                    if (t >= SLASH_TICKS) {
                        this.finish(32);
                    }
                }
                case THRUST -> {
                    if (target != null && t < 10) {
                        b.faceTarget(target);
                    }
                    if (t == 6) {
                        b.sound(SoundEvents.ITEM_TRIDENT_RIPTIDE_1, 0.9f, 0.6f);
                    }
                    if (t == 10) {
                        this.dashDir = b.forward();
                    }
                    if (t >= 10 && t < 16) {
                        this.dash(0.75);
                        for (LivingEntity e : b.victims(2.0)) {
                            Vec3d to = new Vec3d(e.getX() - b.getX(), 0, e.getZ() - b.getZ());
                            if ((to.lengthSquared() < 0.01 || to.normalize().dotProduct(this.dashDir) > 0.3)
                                    && this.hitIds.add(e.getId())) {
                                b.strike(e, 10f, 1.0, 0.2, false);
                                b.sound(SoundEvents.ENTITY_PLAYER_ATTACK_STRONG, 1.1f, 0.7f);
                            }
                        }
                        if (b.horizontalCollision && t > 11) {
                            this.tick = 16;
                        }
                    }
                    if (t >= 16 && t < 20) {
                        Vec3d v = b.getVelocity();
                        b.setVelocity(v.x * 0.5, v.y, v.z * 0.5);
                    }
                    if (t >= THRUST_TICKS) {
                        this.finish(30);
                    }
                }
                case SUMMON -> {
                    if (target != null && t < 20) {
                        b.faceTarget(target);
                    }
                    if (t == 8) {
                        b.sound(SoundEvents.ENTITY_EVOKER_PREPARE_SUMMON, 1.0f, 0.6f);
                    }
                    if (t == 26) {
                        b.spawnBloodlings(target);
                        b.groundDust(1.6, 1.0, 16);
                        b.sound(SoundEvents.ENTITY_SLIME_SQUISH, 1.4f, 0.5f);
                        b.sound(SoundEvents.ENTITY_ZOMBIE_INFECT, 1.0f, 0.6f);
                    }
                    if (t >= SUMMON_TICKS) {
                        this.summonCd = SUMMON_CD;
                        this.finish(26);
                    }
                }
                case EGGS -> {
                    if (t == 8) {
                        b.sound(SoundEvents.ENTITY_EVOKER_PREPARE_SUMMON, 1.0f, 0.5f);
                    }
                    if (t == 20) {
                        b.groundDust(1.2, 1.2, 24);
                        BloodFx.splash(b.getWorld(), b.getX(), b.getY() + 0.3, b.getZ(), 30, 1.2);
                        b.sound(SoundEvents.ENTITY_PLAYER_ATTACK_STRONG, 1.2f, 0.5f);
                    }
                    if (t == 22) {
                        b.spawnEggs();
                        b.sound(SoundEvents.ENTITY_SLIME_SQUISH, 1.5f, 0.4f);
                    }
                    if (t >= EGGS_TICKS) {
                        this.eggsCd = EGGS_CD;
                        this.finish(30);
                    }
                }
                case WALTZ -> this.runWaltz(target);
                default -> this.finish(20);
            }
        }

        /** «Голодный бросок»: распрямляется (0–14 следит за целью), запоминает точку, прыгает на 20. */
        private void runPounce(LivingEntity target) {
            BloodthirsterEntity b = this.boss;
            int t = this.tick;
            if (t < 14 && target != null) {
                b.faceTarget(target);
            }
            if (t == 6) {
                b.sound(SoundEvents.ENTITY_RAVAGER_ROAR, 1.2f, 0.9f);
            }
            if (t == 14) {
                this.aim = target != null ? target.getPos() : b.getPos().add(b.forward().multiply(6));
            }
            if (t == POUNCE_WINDUP) {
                Vec3d d = new Vec3d(this.aim.x - b.getX(), 0, this.aim.z - b.getZ());
                double speed = Math.min(1.25, d.length() / 19.0);
                this.leapVel = d.lengthSquared() > 0.01 ? d.normalize().multiply(speed) : Vec3d.ZERO;
                b.setVelocity(this.leapVel.x, 0.8, this.leapVel.z);
                b.velocityModified = true;
                b.groundDust(0, 0.8, 16);
                b.sound(SoundEvents.ENTITY_RAVAGER_STEP, 1.5f, 0.6f);
            }
            if (t > POUNCE_WINDUP) {
                Vec3d v = b.getVelocity();
                b.setVelocity(this.leapVel.x, v.y, this.leapVel.z);
                if ((b.isOnGround() && t > POUNCE_WINDUP + 3) || t > POUNCE_WINDUP + 60) {
                    this.action = LAND;
                    this.tick = 0;
                    b.setVelocity(0, b.getVelocity().y, 0);
                    b.sendAction(LAND);
                    b.radiusHit(2.4, 15f, 1.0, 0.4, false);
                    b.groundDust(0, 1.4, 40);
                    BloodFx.splash(b.getWorld(), b.getX(), b.getY() + 0.4, b.getZ(), 24, 1.0);
                    b.sound(SoundEvents.ENTITY_GENERIC_EXPLODE, 0.5f, 0.7f);
                    b.sound(SoundEvents.ENTITY_RAVAGER_ATTACK, 1.2f, 0.6f);
                }
            }
        }

        /** «Багровый вальс»: раскрытие 0–12, вихрь 12–72 (по 4 каждые 8 т), крестовой разрез на 82. */
        private void runWaltz(LivingEntity target) {
            BloodthirsterEntity b = this.boss;
            int t = this.tick;
            if (t < WALTZ_OPEN && target != null) {
                b.faceTarget(target);
            }
            if (t == 2) {
                b.sound(SoundEvents.ENTITY_WARDEN_ROAR, 0.8f, 1.6f);
            }
            if (t >= WALTZ_OPEN && t < WALTZ_SPIN_END) {
                if (target != null) {
                    Vec3d d = new Vec3d(target.getX() - b.getX(), 0, target.getZ() - b.getZ());
                    if (d.lengthSquared() > 2.0) {
                        Vec3d v = d.normalize().multiply(0.2);
                        b.setVelocity(v.x, b.getVelocity().y, v.z);
                        b.velocityModified = true;
                    }
                }
                if ((t - WALTZ_OPEN) % 8 == 0) {
                    b.radiusHit(3.4, 4f, 0.5, 0.1, true);
                    b.sound(SoundEvents.ENTITY_PLAYER_ATTACK_SWEEP, 1.0f, 0.7f + b.random.nextFloat() * 0.3f);
                    if (b.getWorld() instanceof ServerWorld s) {
                        double a = b.random.nextDouble() * Math.PI * 2;
                        BloodFx.splash(s, b.getX() + Math.cos(a) * 2.4, b.getBodyY(0.5), b.getZ() + Math.sin(a) * 2.4,
                                5, 0.4);
                    }
                }
            }
            if (t >= WALTZ_SPIN_END && t < 82 && target != null) {
                b.faceTarget(target);
                Vec3d v = b.getVelocity();
                b.setVelocity(v.x * 0.5, v.y, v.z * 0.5);
            }
            if (t == 82) {
                b.coneHit(5.2, 100, 13f, 1.8, 0.4, true);
                b.sound(SoundEvents.ENTITY_PLAYER_ATTACK_STRONG, 1.5f, 0.5f);
                b.sound(SoundEvents.ENTITY_RAVAGER_ATTACK, 1.2f, 0.8f);
                b.groundDust(2.5, 1.5, 30);
                b.bloodAhead(2.8, 1.0, 20, 1.0);
            }
            if (t >= WALTZ_TICKS) {
                this.waltzCd = 360;
                this.finish(40);
            }
        }
    }
}

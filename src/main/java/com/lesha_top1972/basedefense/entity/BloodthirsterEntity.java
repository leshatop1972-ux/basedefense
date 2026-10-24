package com.lesha_top1972.basedefense.entity;

import com.lesha_top1972.basedefense.block.BloodPoolBlock;
import com.lesha_top1972.basedefense.block.ModBlocks;
import com.lesha_top1972.basedefense.combat.ShieldBlocker;
import com.lesha_top1972.basedefense.effect.ModEffects;
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
 * Босс «Кровожад»: огромное (×1.3 модель, хитбокс 1.6×4.0) алое чудовище, 600 HP, две фазы (вторая — с 300 HP).
 * Модель и анимации — tools/bloodthirster_gen.py (проверка рывков — tools/anim_check.py), тайминги ниже
 * совпадают с анимациями (атаки растянуты на 20% — тяжелее и плавнее). Урон: +25% к первой версии и ещё +1,
 * дальность +1.2 блока.
 * <p>1‑я фаза (сгорбленный зверь, когти):</p>
 * <ul>
 *   <li>SWING — разворот, лапа высоко за головой и удар всем телом в землю: 11 (+ трещина вперёд);</li>
 *   <li>RIFT — «разрыв земли»: на дыбы, удар когтями в землю — по земле бежит волна кровавых гейзеров (10),
 *       второй удар — три волны веером;</li>
 *   <li>POUNCE — распрямляется во весь рост и прыгает в точку, где цель была за 0.3 с до прыжка: 20
 *       (успел убежать — увернулся).</li>
 * </ul>
 * <p>Смена фазы (TRANSFORM, 3 с, неуязвим): выпрямляется, венец из шипов, два огромных красно‑чёрных меча.</p>
 * <p>2‑я фаза:</p>
 * <ul>
 *   <li>SLASH — «жатва»: серия — разрез справа налево 9.75, взмах снизу вверх 9.75, оба меча сверху в землю 12
 *       (удары засчитываются по траектории лезвия — {@link #arcHit});</li>
 *   <li>THRUST — «пронзатель»: длинный выпад‑рывок с уколом 13.5 и вспарывающий взмах вверх 8.5 (подбрасывает);</li>
 *   <li>SUMMON — «зов выводка»: фонтан крови, из него 1 мелкий кровожад (раз в 2 мин);</li>
 *   <li>EGGS — «кровавая кладка»: земля вскипает кровью, 3 яйца (раз в 2 мин);</li>
 *   <li>EXEC — «багровая казнь» (мощная): взмывает и обрушивается мечами в точку цели — 21 и кольцо кровавых
 *       гейзеров, затем вырывает мечи широким разрезом в обе стороны — 11.</li>
 * </ul>
 * Скорость в бою ≈ бег игрока − 15% (во 2‑й фазе +7%). Раз в 7 с рядом появляется кровавая лужа.
 */
public class BloodthirsterEntity extends HostileEntity implements BloodKin {

    public static final int NONE = -1;
    public static final int SWING = 0;
    public static final int RIFT = 1;
    public static final int POUNCE = 2;
    public static final int LAND = 3;
    public static final int TRANSFORM = 4;
    public static final int SLASH = 5;
    public static final int THRUST = 6;
    public static final int SUMMON = 7;
    public static final int EGGS = 8;
    public static final int EXEC = 9;
    public static final int EXEC_LAND = 10;
    public static final byte STATUS_BASE = 70;

    /** Длительности (тики), как у анимаций. */
    public static final int SWING_TICKS = 57;
    public static final int RIFT_TICKS = 62;
    public static final int POUNCE_WINDUP = 29;
    public static final int LAND_TICKS = 38;
    public static final int TRANSFORM_TICKS = 60;
    /** На этом тике смены фазы появляются мечи и венец (и меняется облик). */
    public static final int TRANSFORM_SWITCH = 44;
    public static final int SLASH_TICKS = 101;
    public static final int THRUST_TICKS = 69;
    public static final int SUMMON_TICKS = 66;
    public static final int EGGS_TICKS = 71;
    public static final int EXEC_WINDUP = 29;
    public static final int EXEC_LAND_TICKS = 57;

    /** Урон (×1.25 к первой версии и ещё +1 каждой атаке). */
    static final float DMG_SWING = 11f;
    static final float DMG_RIFT = 10f;
    static final float DMG_POUNCE = 20f;
    static final float DMG_REAP = 9.75f;
    static final float DMG_REAP_SLAM = 12f;
    static final float DMG_THRUST = 13.5f;
    static final float DMG_RIP = 8.5f;
    static final float DMG_EXEC = 21f;
    static final float DMG_EXEC_RIP = 11f;
    /** Шанс наложить/усилить «Кровотёк» за попадание. */
    static final float BLEED_CHANCE = 0.25f;
    /** Масштаб модели в рендере (и всех расстояний ударов). */
    public static final float MODEL_SCALE = 1.3f;

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
        if (a >= SWING && a <= EXEC_LAND) {
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
        float before = e.getHealth() + e.getAbsorptionAmount();
        if (e.damage(this.getDamageSources().mobAttack(this), damage)) {
            // вампиризм: лечится ровно на столько, сколько здоровья потеряла цель (после брони и щита)
            float dealt = before - (e.getHealth() + e.getAbsorptionAmount());
            if (dealt > 0) {
                this.heal(dealt);
                if (this.getWorld() instanceof ServerWorld s) {
                    s.spawnParticles(ParticleTypes.DAMAGE_INDICATOR, this.getX(), this.getBodyY(0.7), this.getZ(),
                            Math.max(1, (int) dealt), 0.5, 0.6, 0.5, 0.05);
                }
                // кровотёк: 25% за попадание, повторное — +1 уровень (до IV) и снова 7 с
                if (this.random.nextFloat() < BLEED_CHANCE) {
                    ModEffects.addBleed(e, this);
                }
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
                BloodFx.splash(this.getWorld(), e.getX(), e.getBodyY(0.6), e.getZ(), 10, 0.3);
            }
        }
    }

    /**
     * Где цель относительно босса: [угол от направления взгляда (градусы, + — влево),
     * расстояние до края её хитбокса, угловая полуширина цели]. Так удары попадают «по телу», а не по центру.
     */
    double[] rel(LivingEntity e) {
        double dx = e.getX() - this.getX();
        double dz = e.getZ() - this.getZ();
        double dist = Math.sqrt(dx * dx + dz * dz);
        float yaw = this.getYaw() * MathHelper.RADIANS_PER_DEGREE;
        double fx = -MathHelper.sin(yaw);
        double fz = MathHelper.cos(yaw);
        double along = dx * fx + dz * fz;
        double left = dx * fz - dz * fx;
        double half = e.getWidth() * 0.6;
        double ang = Math.toDegrees(Math.atan2(left, along));
        double pad = dist > half ? Math.toDegrees(Math.asin(Math.min(1.0, half / dist))) : 90.0;
        return new double[]{ang, Math.max(0.0, dist - half), pad};
    }

    /** Цель по высоте в зоне удара (от земли под боссом до up блоков вверх). */
    boolean heightOk(LivingEntity e, double up) {
        return e.getY() < this.getY() + up && e.getY() + e.getHeight() > this.getY() - 1.0;
    }

    /** Удар конусом вперёд: reach — до края цели, halfAngle — половина угла. */
    int coneHit(double reach, double halfAngle, float damage, double knock, double up, boolean multiHit) {
        int n = 0;
        for (LivingEntity e : this.victims(reach + 1)) {
            double[] r = this.rel(e);
            if (r[1] <= reach && Math.abs(r[0]) <= halfAngle + r[2] && this.heightOk(e, 6.0)) {
                this.strike(e, damage, knock, up, multiHit);
                n++;
            }
        }
        return n;
    }

    /** Взмах клинка: попадает по всем, кого лезвие прошло между углами a0 и a1 (каждого — один раз за атаку). */
    int arcHit(Set<Integer> hit, double reach, double a0, double a1, float damage, double knock, double up) {
        double lo = Math.min(a0, a1);
        double hi = Math.max(a0, a1);
        int n = 0;
        for (LivingEntity e : this.victims(reach + 1)) {
            double[] r = this.rel(e);
            if (r[1] <= reach && r[0] >= lo - r[2] && r[0] <= hi + r[2] && this.heightOk(e, 6.5) && hit.add(e.getId())) {
                this.strike(e, damage, knock, up, true);
                n++;
            }
        }
        return n;
    }

    int radiusHit(double radius, float damage, double knock, double up, boolean multiHit) {
        int n = 0;
        for (LivingEntity e : this.victims(radius + 1)) {
            if (this.rel(e)[1] <= radius && this.heightOk(e, 5.0)) {
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

    /** Точка для мелкого кровожада (по одному за призыв): впереди, случайно слева или справа. */
    List<BlockPos> bloodlingSpots() {
        Vec3d f = this.forward();
        Vec3d side = new Vec3d(-f.z, 0, f.x);
        int k = this.random.nextBoolean() ? 1 : -1;
        Vec3d p = this.getPos().add(f.multiply(2.4)).add(side.multiply(2.8 * k));
        BlockPos spot = this.groundSpot(p.x, p.z);
        List<BlockPos> out = new ArrayList<>();
        out.add(spot != null ? spot : this.getBlockPos());
        return out;
    }

    /** Точки для яиц: три стороны, 5–8 блоков. */
    List<BlockPos> eggSpots() {
        List<BlockPos> out = new ArrayList<>();
        double start = this.random.nextDouble() * Math.PI * 2;
        for (int k = 0; k < 3; k++) {
            double a = start + k * (Math.PI * 2 / 3) + (this.random.nextDouble() - 0.5) * 0.6;
            double r = 5.0 + this.random.nextDouble() * 3.0;
            BlockPos spot = this.groundSpot(this.getX() + Math.cos(a) * r, this.getZ() + Math.sin(a) * r);
            if (spot != null) {
                out.add(spot);
            }
        }
        return out;
    }

    /** Фонтан крови из земли: столб брызг высотой h (растёт с силой k 0..1). */
    void geyser(BlockPos p, double h, int count) {
        if (!(this.getWorld() instanceof ServerWorld s)) {
            return;
        }
        double x = p.getX() + 0.5;
        double z = p.getZ() + 0.5;
        for (double y = 0.1; y < h; y += 0.5) {
            BloodFx.splash(s, x, p.getY() + y, z, count, 0.15 + y * 0.05);
        }
        s.spawnParticles(ParticleTypes.CRIMSON_SPORE, x, p.getY() + 0.3, z, 8, 0.4, 0.2, 0.4, 0.02);
    }

    void spawnBloodlings(List<BlockPos> spots, LivingEntity target) {
        if (!(this.getWorld() instanceof ServerWorld s)) {
            return;
        }
        for (BlockPos spot : spots) {
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
            this.geyser(spot, 3.0, 10);
            BloodFx.splash(s, b.getX(), b.getY() + 0.6, b.getZ(), 40, 0.7);
            this.placePool(spot, 2);
        }
    }

    void spawnEggs(List<BlockPos> spots) {
        if (!(this.getWorld() instanceof ServerWorld s)) {
            return;
        }
        for (BlockPos spot : spots) {
            BloodEggEntity egg = ModEntities.BLOOD_EGG.create(s);
            if (egg == null) {
                continue;
            }
            egg.refreshPositionAndAngles(spot.getX() + 0.5, spot.getY(), spot.getZ() + 0.5,
                    this.random.nextFloat() * 360f, 0f);
            s.spawnEntity(egg);
            this.geyser(spot, 4.0, 12);
            BloodFx.splash(s, egg.getX(), egg.getY() + 0.8, egg.getZ(), 50, 0.9);
        }
    }

    /** Временная кровавая лужа радиусом r вокруг точки (высыхает сама, как лужи раз в 7 с). */
    void placePool(BlockPos center, int r) {
        World w = this.getWorld();
        BlockState pool = ModBlocks.BLOOD_POOL.getDefaultState();
        for (int dx = -r; dx <= r; dx++) {
            for (int dz = -r; dz <= r; dz++) {
                if (dx * dx + dz * dz > r * r + 1 || this.random.nextFloat() < 0.25f) {
                    continue;
                }
                for (int dy = 1; dy >= -1; dy--) {
                    BlockPos p = center.add(dx, dy, dz);
                    if (w.isAir(p) && w.getBlockState(p.down()).isSideSolidFullSquare(w, p.down(), Direction.UP)) {
                        w.setBlockState(p, pool);
                        w.scheduleBlockTick(p, ModBlocks.BLOOD_POOL, BloodPoolBlock.BOSS_POOL_TICKS + this.random.nextInt(40));
                        break;
                    }
                }
            }
        }
    }

    /** Дуга брызг и «следов взмаха» от угла a0 до a1 (градусы относительно взгляда, + — влево) радиусом r. */
    void bloodArc(double a0, double a1, double r, double y, int steps) {
        if (!(this.getWorld() instanceof ServerWorld s)) {
            return;
        }
        float yaw = this.getYaw() * MathHelper.RADIANS_PER_DEGREE;
        for (int i = 0; i <= steps; i++) {
            double a = Math.toRadians(a0 + (a1 - a0) * i / steps);
            double fx = -Math.sin(yaw - a);
            double fz = Math.cos(yaw - a);
            double x = this.getX() + fx * r;
            double z = this.getZ() + fz * r;
            BloodFx.splash(s, x, this.getY() + y, z, 4, 0.2);
            if (i % 2 == 0) {
                s.spawnParticles(ParticleTypes.SWEEP_ATTACK, x, this.getY() + y, z, 1, 0, 0, 0, 0);
            }
        }
    }

    /** Трещина по земле вперёд от точки d на длину len (пыль блоков + кровь). */
    void crackLine(double d, double len) {
        if (!(this.getWorld() instanceof ServerWorld s)) {
            return;
        }
        Vec3d f = this.forward();
        for (double k = d; k <= d + len; k += 0.6) {
            Vec3d p = this.getPos().add(f.multiply(k));
            BlockState ground = this.getWorld().getBlockState(BlockPos.ofFloored(p.x, this.getY() - 0.5, p.z));
            if (!ground.isAir()) {
                s.spawnParticles(new BlockStateParticleEffect(ParticleTypes.BLOCK, ground), p.x, this.getY() + 0.1, p.z,
                        8, 0.3, 0.1, 0.3, 0.25);
            }
            BloodFx.splash(s, p.x, this.getY() + 0.2, p.z, 3, 0.25);
        }
    }

    /** Кольцо кровавых гейзеров вокруг себя (радиусы 1.5..r). */
    void geyserRing(double r) {
        if (!(this.getWorld() instanceof ServerWorld s)) {
            return;
        }
        for (double rr = 1.5; rr <= r; rr += 1.5) {
            int n = (int) (rr * 4);
            for (int i = 0; i < n; i++) {
                double a = i * Math.PI * 2 / n + rr;
                double x = this.getX() + Math.cos(a) * rr;
                double z = this.getZ() + Math.sin(a) * rr;
                BloodFx.splash(s, x, this.getY() + 0.4 + rr * 0.15, z, 6, 0.2);
            }
        }
        s.spawnParticles(ParticleTypes.EXPLOSION, this.getX(), this.getY() + 0.5, this.getZ(), 2, 0.6, 0.1, 0.6, 0);
    }

    @Override
    public Box getVisibilityBoundingBox() {
        return this.getBoundingBox().expand(3.0, 1.5, 3.0);      // плащ и мечи выходят за хитбокс
    }

    /** Волна кровавых гейзеров по земле («Разрыв земли»). */
    static final class Wave {
        Vec3d pos;
        final Vec3d dir;
        int life;
        final Set<Integer> hit = new HashSet<>();

        Wave(Vec3d pos, Vec3d dir, int life) {
            this.pos = pos;
            this.dir = dir;
            this.life = life;
        }
    }

    /** Бой: выбор атаки по расстоянию и фазе, тайминги ударов, прыжки, волны, смена фазы. */
    static class BrawlGoal extends Goal {
        private final BloodthirsterEntity boss;
        private int action = NONE;
        private int tick;
        private int cooldown = 40;
        private int lastAction = NONE;
        private int pathDelay;
        private int pounceCd = 80;
        private int riftCd = 60;
        private int execCd = 200;
        private int summonCd = 160;
        private int eggsCd = 260;
        private Vec3d aim = Vec3d.ZERO;
        private Vec3d leapVel = Vec3d.ZERO;
        private Vec3d dashDir = Vec3d.ZERO;
        private List<BlockPos> spots = new ArrayList<>();
        private final Set<Integer> hitIds = new HashSet<>();
        private final List<Wave> waves = new ArrayList<>();

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
            return this.action != NONE || !this.waves.isEmpty() || valid(this.boss.getTarget());
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
            if (this.action == TRANSFORM && !this.boss.isPhase2()) {
                this.boss.setPhase2();
            }
            this.boss.transforming = false;
            this.action = NONE;
            this.waves.clear();
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
                if (dist < 6.2) {
                    options.add(new int[]{SWING, 50});
                }
                if (this.riftCd <= 0 && dist < 11) {
                    options.add(new int[]{RIFT, dist < 6.2 ? 30 : 45});
                }
                if (this.pounceCd <= 0 && dist > 3.5 && dist < 18) {
                    options.add(new int[]{POUNCE, dist < 6.2 ? 12 : 50});
                }
            } else {
                if (dist < 6.2) {
                    options.add(new int[]{SLASH, 50});
                    options.add(new int[]{THRUST, 15});
                } else if (dist < 12) {
                    options.add(new int[]{THRUST, 45});
                }
                if (this.execCd <= 0 && dist > 3.0 && dist < 18) {
                    options.add(new int[]{EXEC, 35});
                }
                if (this.summonCd <= 0 && dist < 22 && b.countNearby(BloodlingEntity.class, 32) < 4) {
                    options.add(new int[]{SUMMON, 25});
                }
                if (this.eggsCd <= 0 && dist < 22 && b.countNearby(BloodEggEntity.class, 32) < 6) {
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
            if (this.riftCd > 0) {
                this.riftCd--;
            }
            if (this.execCd > 0) {
                this.execCd--;
            }
            if (this.summonCd > 0) {
                this.summonCd--;
            }
            if (this.eggsCd > 0) {
                this.eggsCd--;
            }
            this.tickWaves();
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
            if (!b.getVisibilityCache().canSee(target) || dist > 26) {
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
            if (dist > 4.6 || !b.getVisibilityCache().canSee(target)) {
                if (--this.pathDelay <= 0) {
                    this.pathDelay = 8;
                    b.getNavigation().startMovingTo(target, CHASE);
                }
            } else {
                b.getNavigation().stop();
                b.lookAtEntity(target, 30.0f, 30.0f);
            }
        }

        /** Волна гейзеров бежит по земле 0.75 блока за тик; задевает в радиусе 1.4 (каждого — один раз). */
        private void tickWaves() {
            BloodthirsterEntity b = this.boss;
            for (int i = this.waves.size() - 1; i >= 0; i--) {
                Wave w = this.waves.get(i);
                w.pos = w.pos.add(w.dir.multiply(0.75));
                BlockPos ground = b.groundSpot(w.pos.x, w.pos.z);
                if (ground == null || --w.life <= 0) {
                    this.waves.remove(i);
                    continue;
                }
                double gy = ground.getY();
                w.pos = new Vec3d(w.pos.x, gy, w.pos.z);
                BloodFx.splash(b.getWorld(), w.pos.x, gy + 0.4, w.pos.z, 8, 0.35);
                BloodFx.splash(b.getWorld(), w.pos.x, gy + 1.4, w.pos.z, 4, 0.2);
                if (b.getWorld() instanceof ServerWorld s) {
                    BlockState gs = b.getWorld().getBlockState(ground.down());
                    if (!gs.isAir()) {
                        s.spawnParticles(new BlockStateParticleEffect(ParticleTypes.BLOCK, gs), w.pos.x, gy + 0.1,
                                w.pos.z, 10, 0.4, 0.1, 0.4, 0.3);
                    }
                }
                if (w.life % 3 == 0) {
                    b.getWorld().playSound(null, w.pos.x, gy, w.pos.z, SoundEvents.BLOCK_POINTED_DRIPSTONE_LAND,
                            SoundCategory.HOSTILE, 0.8f, 0.6f);
                }
                for (LivingEntity e : b.getWorld().getEntitiesByClass(LivingEntity.class,
                        new Box(w.pos, w.pos).expand(1.6, 2.5, 1.6),
                        e -> e.isAlive() && !(e instanceof BloodKin) && !(e instanceof PlayerEntity p
                                && (p.isCreative() || p.isSpectator())))) {
                    double dx = e.getX() - w.pos.x;
                    double dz = e.getZ() - w.pos.z;
                    if (dx * dx + dz * dz <= 1.4 * 1.4 + e.getWidth() && w.hit.add(e.getId())) {
                        b.strike(e, DMG_RIFT, 0.5, 0.65, true);
                    }
                }
            }
        }

        private void wave(double angleDeg) {
            BloodthirsterEntity b = this.boss;
            float yaw = (b.getYaw() + (float) -angleDeg) * MathHelper.RADIANS_PER_DEGREE;
            Vec3d dir = new Vec3d(-MathHelper.sin(yaw), 0, MathHelper.cos(yaw));
            this.waves.add(new Wave(b.getPos().add(dir.multiply(2.6)), dir, 13));
        }

        private void dash(double speed) {
            BloodthirsterEntity b = this.boss;
            b.setVelocity(this.dashDir.x * speed, b.getVelocity().y, this.dashDir.z * speed);
            b.velocityModified = true;
        }

        private void brake() {
            Vec3d v = this.boss.getVelocity();
            this.boss.setVelocity(v.x * 0.5, v.y, v.z * 0.5);
        }

        /** Прыжок в точку aim: подъём vy, время полёта ≈ 2·vy/0.08 тиков. */
        private void leapTo(double vy, double maxSpeed) {
            BloodthirsterEntity b = this.boss;
            Vec3d d = new Vec3d(this.aim.x - b.getX(), 0, this.aim.z - b.getZ());
            double flight = 2 * vy / 0.08 - 1;
            double speed = Math.min(maxSpeed, d.length() / flight);
            this.leapVel = d.lengthSquared() > 0.01 ? d.normalize().multiply(speed) : Vec3d.ZERO;
            b.setVelocity(this.leapVel.x, vy, this.leapVel.z);
            b.velocityModified = true;
        }

        /** В полёте держим горизонтальную скорость; true — приземлился. */
        private boolean flying(int sinceJump) {
            BloodthirsterEntity b = this.boss;
            Vec3d v = b.getVelocity();
            b.setVelocity(this.leapVel.x, v.y, this.leapVel.z);
            if ((b.isOnGround() && sinceJump > 3) || sinceJump > 70) {
                b.setVelocity(0, b.getVelocity().y, 0);
                return true;
            }
            return false;
        }

        /** Окно взмаха: на тиках t0..t1 лезвие проходит углы a0→a1 (линейно). */
        private void sweep(int t, int t0, int t1, double a0, double a1, double reach, float dmg, double knock,
                           double up) {
            if (t < t0 || t > t1) {
                return;
            }
            double f0 = (t - 1 - t0) / (double) (t1 - t0);
            double f1 = (t - t0) / (double) (t1 - t0);
            double from = a0 + (a1 - a0) * Math.max(0.0, f0);
            double to = a0 + (a1 - a0) * f1;
            this.boss.arcHit(this.hitIds, reach, from, to, dmg, knock, up);
        }

        private void runAction(LivingEntity target) {
            BloodthirsterEntity b = this.boss;
            int t = this.tick;
            switch (this.action) {
                case SWING -> {
                    // разворот, лапа за головой (рёв на 17), удар всем телом в землю на 29, когти волочатся
                    if (target != null && t < 22) {
                        b.faceTarget(target);
                    }
                    if (t == 17) {
                        b.sound(SoundEvents.ENTITY_RAVAGER_ROAR, 1.2f, 0.75f);
                    }
                    if (t == 29) {
                        b.coneHit(6.0, 55, DMG_SWING, 1.4, 0.45, false);
                        b.sound(SoundEvents.ENTITY_GENERIC_EXPLODE, 0.5f, 0.6f);
                        b.sound(SoundEvents.ENTITY_RAVAGER_ATTACK, 1.3f, 0.6f);
                        b.groundDust(3.8, 1.0, 30);
                        b.crackLine(2.5, 4.5);
                        b.bloodAhead(3.8, 0.6, 22, 0.9);
                    }
                    if (t >= SWING_TICKS) {
                        this.finish(60);
                    }
                }
                case RIFT -> {
                    // на дыбы с рёвом, удар когтями в землю на 20 (волна вперёд), второй на 36 (три волны веером)
                    if (target != null && (t < 17 || (t > 25 && t < 33))) {
                        b.faceTarget(target);
                    }
                    if (t == 8) {
                        b.sound(SoundEvents.ENTITY_RAVAGER_ROAR, 1.4f, 0.65f);
                    }
                    if (t == 20) {
                        this.wave(0);
                        b.groundDust(2.8, 1.4, 36);
                        b.geyserRing(2.6);
                        b.sound(SoundEvents.ENTITY_GENERIC_EXPLODE, 0.6f, 0.55f);
                        b.radiusHit(3.0, DMG_RIFT, 1.0, 0.5, true);
                    }
                    if (t == 28) {
                        b.sound(SoundEvents.ENTITY_RAVAGER_ROAR, 1.0f, 0.9f);
                    }
                    if (t == 36) {
                        this.wave(-28);
                        this.wave(0);
                        this.wave(28);
                        b.groundDust(2.8, 1.6, 44);
                        b.geyserRing(3.2);
                        b.sound(SoundEvents.ENTITY_GENERIC_EXPLODE, 0.8f, 0.5f);
                        b.sound(SoundEvents.ENTITY_RAVAGER_ATTACK, 1.4f, 0.5f);
                        b.radiusHit(3.0, DMG_RIFT, 1.0, 0.5, true);
                    }
                    if (t >= RIFT_TICKS) {
                        this.riftCd = 160;
                        this.finish(60);
                    }
                }
                case POUNCE -> {
                    // во весь рост с воплем (следит до 23), прыжок на 29 в запомненную точку
                    if (t < 23 && target != null) {
                        b.faceTarget(target);
                    }
                    if (t == 10) {
                        b.sound(SoundEvents.ENTITY_RAVAGER_ROAR, 1.5f, 0.7f);
                    }
                    if (t == 23) {
                        this.aim = target != null ? target.getPos() : b.getPos().add(b.forward().multiply(7));
                    }
                    if (t == POUNCE_WINDUP) {
                        this.leapTo(0.85, 1.35);
                        b.groundDust(0, 1.0, 24);
                        b.sound(SoundEvents.ENTITY_RAVAGER_STEP, 1.6f, 0.5f);
                    }
                    if (t > POUNCE_WINDUP && this.flying(t - POUNCE_WINDUP)) {
                        this.action = LAND;
                        this.tick = 0;
                        b.sendAction(LAND);
                        b.radiusHit(3.6, DMG_POUNCE, 1.2, 0.5, false);
                        b.groundDust(0, 1.8, 50);
                        b.geyserRing(3.4);
                        b.sound(SoundEvents.ENTITY_GENERIC_EXPLODE, 0.7f, 0.6f);
                        b.sound(SoundEvents.ENTITY_RAVAGER_ATTACK, 1.4f, 0.5f);
                    }
                }
                case LAND -> {
                    if (t == 12) {
                        b.sound(SoundEvents.ENTITY_RAVAGER_ROAR, 1.0f, 0.8f);
                    }
                    if (t >= LAND_TICKS) {
                        this.pounceCd = 220;
                        this.finish(50);
                    }
                }
                case TRANSFORM -> {
                    b.getNavigation().stop();
                    if (t == 1) {
                        b.sound(SoundEvents.ENTITY_RAVAGER_ROAR, 1.5f, 0.55f);
                        BloodFx.splash(b.getWorld(), b.getX(), b.getBodyY(0.6), b.getZ(), 40, 1.0);
                    }
                    if (t < TRANSFORM_SWITCH && t % 4 == 0) {
                        BloodFx.splash(b.getWorld(), b.getX(), b.getBodyY(0.3 + b.random.nextDouble() * 0.5),
                                b.getZ(), 10, 0.8);
                    }
                    if (t == TRANSFORM_SWITCH) {
                        b.setPhase2();
                        b.sound(SoundEvents.ENTITY_WARDEN_ROAR, 1.4f, 1.2f);
                        BloodFx.splash(b.getWorld(), b.getX(), b.getBodyY(0.5), b.getZ(), 100, 1.8);
                        b.geyserRing(6.0);
                        b.push(5.0, 1.6, 0.35);
                    }
                    if (t >= TRANSFORM_TICKS) {
                        b.transforming = false;
                        this.summonCd = 160;
                        this.eggsCd = 300;
                        this.execCd = 200;
                        this.finish(30);
                    }
                }
                case SLASH -> {
                    // «Жатва»: долгий замах правым мечом (0–28), разрез справа налево (29–38);
                    // левый меч заводится назад‑вниз (39–47) и рассекает снизу вверх (48–57);
                    // оба меча медленно поднимаются над головой (58–70) и обрушиваются в землю (77)
                    if (target != null && (t < 26 || (t > 38 && t < 46) || (t > 58 && t < 70))) {
                        b.faceTarget(target);
                    }
                    if (t == 12) {
                        b.sound(SoundEvents.ENTITY_RAVAGER_ROAR, 0.9f, 1.1f);
                    }
                    if (t == 29 || t == 48) {
                        b.sound(SoundEvents.ENTITY_PLAYER_ATTACK_SWEEP, 1.5f, t == 29 ? 0.45f : 0.55f);
                    }
                    if (t == 33) {
                        b.bloodArc(-100, 70, 4.4, 1.8, 18);
                    }
                    this.sweep(t, 29, 38, -118, 72, 6.2, DMG_REAP, 1.0, 0.15);
                    if (t == 44) {
                        this.hitIds.clear();
                    }
                    this.sweep(t, 48, 57, 130, -70, 5.8, DMG_REAP, 0.8, 0.5);
                    if (t == 52) {
                        b.bloodArc(120, -60, 4.0, 2.6, 16);
                    }
                    if (t == 62) {
                        b.sound(SoundEvents.ENTITY_WARDEN_ROAR, 0.8f, 1.4f);
                    }
                    if (t == 72) {
                        b.sound(SoundEvents.ENTITY_PLAYER_ATTACK_SWEEP, 1.4f, 0.35f);
                    }
                    if (t == 77) {
                        b.coneHit(6.6, 32, DMG_REAP_SLAM, 1.3, 0.65, true);
                        b.crackLine(2.5, 6.0);
                        b.groundDust(4.2, 1.4, 40);
                        b.geyserRing(2.6);
                        b.bloodAhead(4.4, 0.5, 30, 1.1);
                        b.sound(SoundEvents.ENTITY_GENERIC_EXPLODE, 0.8f, 0.5f);
                        b.sound(SoundEvents.ENTITY_RAVAGER_ATTACK, 1.4f, 0.55f);
                    }
                    if (t >= SLASH_TICKS) {
                        this.finish(55);
                    }
                }
                case THRUST -> {
                    // «Пронзатель»: долго сжимается 0–28, выпад‑рывок 29–39 (укол), вспарывающий взмах вверх на 47
                    if (target != null && t < 28) {
                        b.faceTarget(target);
                    }
                    if (t == 14) {
                        b.sound(SoundEvents.ITEM_TRIDENT_RIPTIDE_1, 1.0f, 0.4f);
                    }
                    if (t == 29) {
                        this.dashDir = b.forward();
                        b.groundDust(-0.5, 0.8, 24);
                        b.sound(SoundEvents.ENTITY_RAVAGER_ROAR, 0.9f, 1.2f);
                    }
                    if (t >= 29 && t < 40) {
                        this.dash(t < 36 ? 0.75 : 0.45);
                        for (LivingEntity e : b.victims(5.0)) {
                            double[] r = b.rel(e);
                            if (r[1] <= 5.4 && Math.abs(r[0]) <= 16 + r[2] && b.heightOk(e, 5.0)
                                    && this.hitIds.add(e.getId())) {
                                b.strike(e, DMG_THRUST, 0.6, 0.15, false);
                                b.sound(SoundEvents.ENTITY_PLAYER_ATTACK_STRONG, 1.2f, 0.55f);
                            }
                        }
                        if (t % 2 == 0) {
                            b.bloodAhead(4.5, 1.6, 4, 0.3);
                        }
                        if (b.horizontalCollision && t > 30) {
                            this.tick = 40;
                        }
                    }
                    if (t >= 40 && t < 47) {
                        this.brake();
                    }
                    if (t == 47) {
                        b.coneHit(5.8, 40, DMG_RIP, 0.5, 0.85, true);
                        b.bloodArc(-30, 30, 3.0, 3.4, 12);
                        b.sound(SoundEvents.ENTITY_PLAYER_ATTACK_SWEEP, 1.3f, 0.7f);
                    }
                    if (t >= THRUST_TICKS) {
                        this.finish(55);
                    }
                }
                case SUMMON -> {
                    // мечи скрещены над головой, вопль; фонтан крови в точке 12–37; удар мечами в землю на 37
                    if (target != null && t < 24) {
                        b.faceTarget(target);
                    }
                    if (t == 12) {
                        this.spots = b.bloodlingSpots();
                        b.sound(SoundEvents.ENTITY_EVOKER_PREPARE_SUMMON, 1.2f, 0.5f);
                        b.sound(SoundEvents.ENTITY_RAVAGER_ROAR, 1.0f, 1.1f);
                    }
                    if (t > 12 && t < 37 && t % 3 == 0) {
                        for (BlockPos p : this.spots) {
                            b.geyser(p, 0.6 + (t - 12) * 0.1, 3);
                        }
                    }
                    if (t == 37) {
                        b.spawnBloodlings(this.spots, target);
                        b.groundDust(2.0, 1.4, 30);
                        b.crackLine(1.0, 3.0);
                        b.sound(SoundEvents.ENTITY_SLIME_SQUISH, 1.6f, 0.4f);
                        b.sound(SoundEvents.ENTITY_ZOMBIE_INFECT, 1.2f, 0.5f);
                        b.sound(SoundEvents.ENTITY_GENERIC_EXPLODE, 0.4f, 0.7f);
                    }
                    if (t >= SUMMON_TICKS) {
                        this.summonCd = SUMMON_CD;
                        this.finish(40);
                    }
                }
                case EGGS -> {
                    // руки с мечами раскинуты (вопль), мечи в землю на 25, земля кипит, яйца на 31
                    if (t == 7) {
                        this.spots = b.eggSpots();
                        b.sound(SoundEvents.ENTITY_EVOKER_PREPARE_SUMMON, 1.2f, 0.4f);
                    }
                    if (t == 14) {
                        b.sound(SoundEvents.ENTITY_WARDEN_ROAR, 0.9f, 1.4f);
                    }
                    if (t > 7 && t < 31 && t % 2 == 0) {
                        for (BlockPos p : this.spots) {
                            b.geyser(p, 0.4 + (t - 7) * 0.085, 2);
                        }
                    }
                    if (t == 25) {
                        b.groundDust(1.6, 1.6, 36);
                        b.geyserRing(4.5);
                        b.sound(SoundEvents.ENTITY_PLAYER_ATTACK_STRONG, 1.4f, 0.4f);
                    }
                    if (t == 31) {
                        b.spawnEggs(this.spots);
                        b.sound(SoundEvents.ENTITY_SLIME_SQUISH, 1.6f, 0.35f);
                        b.sound(SoundEvents.ENTITY_GENERIC_EXPLODE, 0.4f, 0.6f);
                    }
                    if (t >= EGGS_TICKS) {
                        this.eggsCd = EGGS_CD;
                        this.finish(45);
                    }
                }
                case EXEC -> {
                    // долгий присед с мечами‑крыльями (следит до 23), взмывает на 29, обрушивается в точку цели
                    if (t < 23 && target != null) {
                        b.faceTarget(target);
                    }
                    if (t == 5) {
                        b.sound(SoundEvents.ENTITY_WARDEN_ROAR, 1.0f, 1.5f);
                    }
                    if (t == 23) {
                        this.aim = target != null ? target.getPos() : b.getPos().add(b.forward().multiply(6));
                    }
                    if (t == EXEC_WINDUP) {
                        this.leapTo(1.15, 1.4);
                        b.groundDust(0, 1.2, 30);
                        BloodFx.splash(b.getWorld(), b.getX(), b.getY() + 0.3, b.getZ(), 30, 1.0);
                        b.sound(SoundEvents.ENTITY_ENDER_DRAGON_FLAP, 1.5f, 0.6f);
                    }
                    if (t > EXEC_WINDUP && t % 3 == 0) {
                        BloodFx.splash(b.getWorld(), b.getX(), b.getBodyY(0.5), b.getZ(), 4, 0.6);
                    }
                    if (t > EXEC_WINDUP && this.flying(t - EXEC_WINDUP)) {
                        this.action = EXEC_LAND;
                        this.tick = 0;
                        b.sendAction(EXEC_LAND);
                        b.radiusHit(4.8, DMG_EXEC, 1.0, 0.8, false);
                        b.groundDust(0, 2.2, 60);
                        b.geyserRing(5.0);
                        b.placePool(b.getBlockPos(), 2);
                        b.sound(SoundEvents.ENTITY_GENERIC_EXPLODE, 1.0f, 0.5f);
                        b.sound(SoundEvents.ENTITY_WARDEN_SONIC_BOOM, 0.6f, 0.6f);
                    }
                }
                case EXEC_LAND -> {
                    // стоит на колене с мечами в земле, на 30 вырывает их широким разрезом в обе стороны
                    if (t > 2 && t < 26 && t % 3 == 0) {
                        b.geyserRing(2.0 + t * 0.1);
                    }
                    if (t == 24) {
                        b.sound(SoundEvents.ENTITY_RAVAGER_ROAR, 1.2f, 0.7f);
                    }
                    if (t == 30) {
                        b.radiusHit(6.0, DMG_EXEC_RIP, 1.8, 0.4, true);
                        b.bloodArc(-100, 100, 4.4, 1.4, 20);
                        b.bloodArc(80, 280, 4.4, 1.4, 20);
                        b.sound(SoundEvents.ENTITY_PLAYER_ATTACK_SWEEP, 1.5f, 0.5f);
                        b.sound(SoundEvents.ENTITY_PLAYER_ATTACK_STRONG, 1.4f, 0.6f);
                    }
                    if (t >= EXEC_LAND_TICKS) {
                        this.execCd = 540;
                        this.finish(60);
                    }
                }
                default -> this.finish(20);
            }
        }
    }
}

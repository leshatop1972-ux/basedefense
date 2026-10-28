package com.lesha_top1972.basedefense.entity;

import com.lesha_top1972.basedefense.combat.ShieldBlocker;
import com.lesha_top1972.basedefense.effect.ModEffects;
import com.lesha_top1972.basedefense.fx.Fx;
import com.lesha_top1972.basedefense.sound.ModSounds;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.ai.NoPenaltyTargeting;
import net.minecraft.entity.ai.goal.ActiveTargetGoal;
import net.minecraft.entity.ai.goal.Goal;
import net.minecraft.entity.ai.goal.LookAroundGoal;
import net.minecraft.entity.ai.goal.LookAtEntityGoal;
import net.minecraft.entity.ai.goal.SwimGoal;
import net.minecraft.entity.ai.goal.WanderAroundFarGoal;
import net.minecraft.entity.ai.pathing.Path;
import net.minecraft.entity.attribute.DefaultAttributeContainer;
import net.minecraft.entity.attribute.EntityAttributes;
import net.minecraft.entity.damage.DamageSource;
import net.minecraft.entity.damage.DamageTypes;
import net.minecraft.entity.data.DataTracker;
import net.minecraft.entity.data.TrackedData;
import net.minecraft.entity.data.TrackedDataHandlerRegistry;
import net.minecraft.entity.decoration.ArmorStandEntity;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.mob.HostileEntity;
import net.minecraft.entity.mob.Monster;
import net.minecraft.entity.passive.IronGolemEntity;
import net.minecraft.entity.passive.MerchantEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtElement;
import net.minecraft.nbt.NbtList;
import net.minecraft.nbt.NbtLong;
import net.minecraft.particle.BlockStateParticleEffect;
import net.minecraft.particle.DustParticleEffect;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvent;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.Heightmap;
import net.minecraft.world.World;
import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;

/**
 * «Подкопщик» — сутулый карлик‑сапёр. Скрытность и скорость: держит дистанцию 8–14 блоков, бросает бомбы‑банки
 * и уходит под землю. Под землёй роет настоящий туннель 1×2 (по mobGriefing), выныривает из лазов, сидит в засаде,
 * у стен базы закладывает «Подрыв». Вблизи — единственная атака «Тычок» (5 + оглушение 1 с) и сразу побег.
 * Тайминги совпадают с анимациями tools/sapper_gen.py.
 */
public class SapperEntity extends HostileEntity {

    public static final int NONE = -1;
    public static final int DIG_DOWN = 0;
    public static final int EMERGE = 1;
    public static final int HIDE = 2;
    public static final int THROW = 3;
    public static final int PLANT = 4;
    public static final int JAB = 5;
    public static final int STAGGER = 6;
    public static final int SMOKE = 7;
    /** Нырок в уже открытый лаз (клиент играет dig_down с 0.95 с). */
    public static final int DIVE = 8;
    public static final byte STATUS_BASE = 70;

    // длительности действий (тики) и моменты событий — по клипам sapper_gen.py
    static final int DIG_TICKS = 28;
    static final int[] DIG_HITS = {5, 11, 17};
    static final int DIVE_TICKS = 9;
    static final int EMERGE_TICKS = 20;
    static final int HIDE_TICKS = 20;
    static final int HIDE_SNUFF = 12;
    static final int THROW_TICKS = 22;
    static final int THROW_LIGHT = 6;
    static final int THROW_RELEASE = 11;
    static final int PLANT_TICKS = 32;
    static final int[] PLANT_HITS = {9, 16};
    static final int PLANT_HOP = 19;
    static final int JAB_TICKS = 18;
    static final int JAB_HIT = 7;
    static final int STAGGER_TICKS = 20;
    static final int SMOKE_TICKS = 16;
    static final int SMOKE_DROP = 6;

    static final int BOMB_CD = 100;
    static final int JAB_CD = 120;
    static final int PLANT_CD = 240;
    static final int SMOKE_CD = 240;
    static final float JAB_DMG = 5f;
    static final int STUN_TICKS = 20;
    static final double JAB_REACH = 3.0;
    /** Не больше одного сломанного блока за 5 тиков. */
    static final int DIG_INTERVAL = 5;
    /** Под землёй: тиков на клетку по уже открытому ходу. */
    static final int STEP_TICKS = 5;
    static final float MAX_DIG_HARDNESS = 5f;
    static final int MAX_TARGET_TICKS = 600;
    static final int MAX_LAIRS = 8;

    private static final TrackedData<Boolean> UNDER = DataTracker.registerData(SapperEntity.class, TrackedDataHandlerRegistry.BOOLEAN);
    private static final TrackedData<Boolean> DIGGING = DataTracker.registerData(SapperEntity.class, TrackedDataHandlerRegistry.BOOLEAN);
    private static final TrackedData<Boolean> HIDDEN = DataTracker.registerData(SapperEntity.class, TrackedDataHandlerRegistry.BOOLEAN);
    private static final TrackedData<Boolean> SNEAK = DataTracker.registerData(SapperEntity.class, TrackedDataHandlerRegistry.BOOLEAN);

    private static final DustParticleEffect EMBER = new DustParticleEffect(new Vector3f(1.0f, 0.55f, 0.15f), 0.5f);

    // --- клиент ---
    public int clientAction = NONE;
    public int clientActionAge;
    public float candle = 1f;
    public float prevCandle = 1f;

    // --- сервер ---
    final List<BlockPos> lairs = new ArrayList<>();
    /** Ближайший найденный «не природный» блок (постройка) — главная цель подкопщика. */
    BlockPos build;

    public SapperEntity(EntityType<? extends HostileEntity> type, World world) {
        super(type, world);
        this.experiencePoints = 8;
    }

    public static DefaultAttributeContainer.Builder createAttributes() {
        return HostileEntity.createHostileAttributes()
                .add(EntityAttributes.GENERIC_MAX_HEALTH, 45.0)
                .add(EntityAttributes.GENERIC_MOVEMENT_SPEED, 0.33)
                .add(EntityAttributes.GENERIC_FOLLOW_RANGE, 48.0)
                .add(EntityAttributes.GENERIC_ATTACK_DAMAGE, 5.0)
                .add(EntityAttributes.GENERIC_ARMOR, 2.0);
    }

    @Override
    protected void initDataTracker() {
        super.initDataTracker();
        this.dataTracker.startTracking(UNDER, false);
        this.dataTracker.startTracking(DIGGING, false);
        this.dataTracker.startTracking(HIDDEN, false);
        this.dataTracker.startTracking(SNEAK, false);
    }

    public boolean isUnder() {
        return this.dataTracker.get(UNDER);
    }

    public boolean isDigging() {
        return this.dataTracker.get(DIGGING);
    }

    public boolean isHidden() {
        return this.dataTracker.get(HIDDEN);
    }

    public boolean isSneakingUp() {
        return this.dataTracker.get(SNEAK);
    }

    @Override
    protected void initGoals() {
        this.goalSelector.add(0, new SwimGoal(this));
        this.goalSelector.add(2, new SapperGoal(this));
        this.goalSelector.add(7, new WanderAroundFarGoal(this, 0.8));
        this.goalSelector.add(8, new LookAtEntityGoal(this, PlayerEntity.class, 12.0f));
        this.goalSelector.add(8, new LookAroundGoal(this));
        this.targetSelector.add(2, new ActiveTargetGoal<>(this, PlayerEntity.class, false));
        this.targetSelector.add(3, new ActiveTargetGoal<>(this, MerchantEntity.class, true));
        this.targetSelector.add(3, new ActiveTargetGoal<>(this, IronGolemEntity.class, true));
    }

    // =====================================================================
    // Общее
    // =====================================================================
    @Override
    public void handleStatus(byte status) {
        int a = status - STATUS_BASE;
        if (a >= DIG_DOWN && a <= DIVE) {
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
        float yaw = this.bodyYaw * MathHelper.RADIANS_PER_DEGREE;
        return new Vec3d(-MathHelper.sin(yaw), 0, MathHelper.cos(yaw));
    }

    void faceTo(double x, double z) {
        float yaw = (float) (MathHelper.atan2(z - this.getZ(), x - this.getX()) * MathHelper.DEGREES_PER_RADIAN) - 90.0f;
        this.setYaw(yaw);
        this.setBodyYaw(yaw);
        this.setHeadYaw(yaw);
    }

    @Override
    public boolean damage(DamageSource source, float amount) {
        if (source.isOf(DamageTypes.IN_WALL) || source.isOf(DamageTypes.DROWN) || source.isOf(DamageTypes.FALL)) {
            return false;          // в своём туннеле не задыхается и не разбивается
        }
        return super.damage(source, amount);
    }

    @Override
    public boolean isPushable() {
        return !this.isUnder() && super.isPushable();
    }

    @Override
    public boolean canImmediatelyDespawn(double distanceSquared) {
        return !this.isUnder() && super.canImmediatelyDespawn(distanceSquared);
    }

    @Override
    protected void mobTick() {
        super.mobTick();
        if (this.age % 20 == 0) {
            this.scanBuild();
        }
    }

    /**
     * Раз в секунду — блок постройки в 200 блоках (двери, стекло, функциональные блоки — в первую очередь; доски,
     * заборы, ступеньки, лестницы, факелы), только у поверхности. Преграды на пути взрывает зарядом.
     * Только при mobGriefing (без него ломать нечем).
     */
    void scanBuild() {
        World w = this.getWorld();
        if (!SapperBlast.griefing(w)) {
            this.build = null;
            return;
        }
        if (w instanceof ServerWorld s) {
            this.build = BuildTargets.nearest(s, this.getBlockPos(), BuildTargets.RADIUS, 6);
        }
    }

    @Override
    public void tick() {
        super.tick();
        if (this.getWorld().isClient) {
            this.clientTick();
        }
    }

    /** Клиент: свеча на каске, тлеющие фитили, пыль и трещинки над местом, где он копает под землёй. */
    private void clientTick() {
        World w = this.getWorld();
        this.prevCandle = this.candle;
        float want = this.isHidden() ? 0f : 1f;
        this.candle += (want - this.candle) * (want < this.candle ? 0.18f : 0.08f);
        Vec3d f = this.forward();
        boolean under = this.isUnder();
        if (this.prevCandle > 0.5f && this.candle <= 0.5f) {
            // задул свечу — дымок
            for (int i = 0; i < 4; i++) {
                w.addParticle(ParticleTypes.SMOKE, this.getX() + f.x * 0.3, this.getY() + 1.15, this.getZ() + f.z * 0.3,
                        0, 0.03 + i * 0.005, 0);
            }
        }
        if (this.candle > 0.8f && this.random.nextInt(28) == 0) {
            double cy = this.getY() + (this.isSneakingUp() ? 1.12 : 1.32);
            if (this.random.nextBoolean()) {
                w.addParticle(ParticleTypes.SMALL_FLAME, this.getX() + f.x * 0.32, cy + 0.05, this.getZ() + f.z * 0.32, 0, 0.01, 0);
            } else {
                w.addParticle(ParticleTypes.FALLING_HONEY, this.getX() + f.x * 0.34, cy - 0.12, this.getZ() + f.z * 0.34, 0, 0, 0);
            }
        }
        if (!under && this.random.nextInt(20) == 0) {
            // искорка с фитилей на мешке
            w.addParticle(EMBER, this.getX() - f.x * 0.3 + (this.random.nextDouble() - 0.5) * 0.15, this.getY() + 1.25,
                    this.getZ() - f.z * 0.3 + (this.random.nextDouble() - 0.5) * 0.15, 0, 0.03, 0);
        }
        if (under && this.isDigging() && this.age % 2 == 0) {
            int bx = MathHelper.floor(this.getX());
            int bz = MathHelper.floor(this.getZ());
            int top = w.getTopY(Heightmap.Type.MOTION_BLOCKING_NO_LEAVES, bx, bz);
            if (top - this.getY() < 6) {
                BlockState gs = w.getBlockState(new BlockPos(bx, top - 1, bz));
                if (!gs.isAir()) {
                    BlockStateParticleEffect crumbs = new BlockStateParticleEffect(ParticleTypes.BLOCK, gs);
                    for (int i = 0; i < 2; i++) {
                        w.addParticle(crumbs, this.getX() + (this.random.nextDouble() - 0.5) * 1.2, top + 0.05,
                                this.getZ() + (this.random.nextDouble() - 0.5) * 1.2, 0, 0.06, 0);
                    }
                    if (this.age % 8 == 0) {
                        w.addParticle(ParticleTypes.WHITE_ASH, this.getX(), top + 0.2, this.getZ(), 0, 0.02, 0);
                        w.addParticle(ParticleTypes.CLOUD, this.getX(), top + 0.1, this.getZ(), 0, 0.01, 0);
                    }
                }
            }
        }
    }

    // =====================================================================
    // Подземелье: вход/выход, блоки
    // =====================================================================
    static boolean passable(World w, BlockPos p) {
        BlockState st = w.getBlockState(p);
        return (st.isAir() || st.isReplaceable() && st.getCollisionShape(w, p).isEmpty()) && st.getFluidState().isEmpty();
    }

    static boolean solidFloor(World w, BlockPos p) {
        BlockState st = w.getBlockState(p);
        return !st.isAir() && st.getFluidState().isEmpty() && !st.getCollisionShape(w, p).isEmpty();
    }

    /** Можно пройти или прокопать (и там нет воды/лавы). */
    static boolean diggableOrOpen(World w, BlockPos p) {
        return passable(w, p) || SapperBlast.breakable(w, p, MAX_DIG_HARDNESS);
    }

    /**
     * Уровень поверхности над столбцом: открытая шахта (лаз) не считается «поверхностью» — берётся
     * вторая снизу высота соседних столбцов (так стена дома рядом или соседняя яма не мешают).
     */
    static int surfaceAt(World w, int x, int z) {
        int t0 = w.getTopY(Heightmap.Type.MOTION_BLOCKING_NO_LEAVES, x, z);
        int[] n = {
                w.getTopY(Heightmap.Type.MOTION_BLOCKING_NO_LEAVES, x + 1, z),
                w.getTopY(Heightmap.Type.MOTION_BLOCKING_NO_LEAVES, x - 1, z),
                w.getTopY(Heightmap.Type.MOTION_BLOCKING_NO_LEAVES, x, z + 1),
                w.getTopY(Heightmap.Type.MOTION_BLOCKING_NO_LEAVES, x, z - 1)};
        java.util.Arrays.sort(n);
        return Math.max(t0, n[1]);
    }

    /** Стоит в яме (≥ 2 блока ниже поверхности вокруг). */
    boolean inPit() {
        BlockPos f = this.getBlockPos();
        return surfaceAt(this.getWorld(), f.getX(), f.getZ()) - f.getY() >= 2;
    }

    static boolean wet(World w, BlockPos p) {
        return !w.getBlockState(p).getFluidState().isEmpty();
    }

    void enterUnder(BlockPos cell) {
        this.dataTracker.set(UNDER, true);
        this.noClip = true;
        this.setNoGravity(true);
        this.fallDistance = 0;
        this.setVelocity(Vec3d.ZERO);
        this.refreshPositionAfterTeleport(cell.getX() + 0.5, cell.getY(), cell.getZ() + 0.5);
        this.getNavigation().stop();
    }

    void leaveUnder(Vec3d pos) {
        this.dataTracker.set(UNDER, false);
        this.dataTracker.set(DIGGING, false);
        this.noClip = false;
        this.fallDistance = 0;
        this.setVelocity(Vec3d.ZERO);
        this.refreshPositionAfterTeleport(pos.x, pos.y, pos.z);
    }

    /** Ломает блок (без дропа), стук кирки слышен на поверхности над ним. */
    void digBlock(BlockPos p) {
        World w = this.getWorld();
        w.breakBlock(p, false, this);
        int top = w.getTopY(Heightmap.Type.MOTION_BLOCKING_NO_LEAVES, p.getX(), p.getZ());
        w.playSound(null, p.getX() + 0.5, top, p.getZ() + 0.5, ModSounds.SAPPER_DIG, SoundCategory.HOSTILE, 0.7f,
                0.85f + this.random.nextFloat() * 0.3f);
        if (this.random.nextInt(3) == 0) {
            w.playSound(null, p.getX() + 0.5, p.getY() + 1, p.getZ() + 0.5, ModSounds.SAPPER_CRUMBLE, SoundCategory.HOSTILE,
                    0.5f, 1.0f);
        }
    }

    /** Насыпь у лаза: 2–3 блока земли вокруг ямы (только в свободные клетки на твёрдом). */
    void mound(BlockPos hole, Direction keepFree) {
        World w = this.getWorld();
        List<Direction> dirs = new ArrayList<>(List.of(Direction.NORTH, Direction.SOUTH, Direction.EAST, Direction.WEST));
        dirs.remove(keepFree);
        java.util.Collections.shuffle(dirs, new java.util.Random(this.random.nextLong()));
        int n = 2 + this.random.nextInt(2);
        for (Direction d : dirs) {
            if (n <= 0) {
                break;
            }
            BlockPos p = hole.offset(d);
            if (passable(w, p) && solidFloor(w, p.down())) {
                w.setBlockState(p, Blocks.COARSE_DIRT.getDefaultState());
                n--;
            }
        }
    }

    void rememberLair(BlockPos top) {
        BlockPos t = top.toImmutable();
        this.lairs.remove(t);
        this.lairs.add(t);
        while (this.lairs.size() > MAX_LAIRS) {
            this.lairs.remove(0);
        }
    }

    /** Лаз ещё открыт: шахта до туннеля свободна. */
    boolean lairOpen(BlockPos top) {
        World w = this.getWorld();
        for (int k = 0; k <= 3; k++) {
            if (!passable(w, top.down(k))) {
                return false;
            }
        }
        return solidFloor(w, top.down(4));
    }

    // =====================================================================
    // Атаки
    // =====================================================================
    List<LivingEntity> victims(double radius) {
        Box box = this.getBoundingBox().expand(radius + 1.0, 1.5, radius + 1.0);
        return this.getWorld().getEntitiesByClass(LivingEntity.class, box,
                e -> e != this && e.isAlive() && !(e instanceof Monster) && !(e instanceof ArmorStandEntity)
                        && !(e instanceof PlayerEntity p && (p.isCreative() || p.isSpectator())));
    }

    double edgeDist(LivingEntity e) {
        Box b = e.getBoundingBox();
        double dx = Math.max(Math.max(b.minX - this.getX(), 0), this.getX() - b.maxX);
        double dz = Math.max(Math.max(b.minZ - this.getZ(), 0), this.getZ() - b.maxZ);
        return Math.sqrt(dx * dx + dz * dz);
    }

    /** «Тычок»: удар киркой о землю перед собой — 5 урона и «Оглушение» на 1 с всем в конусе 70° на 3 блока. */
    int jabHit() {
        Vec3d f = this.forward();
        int n = 0;
        for (LivingEntity e : this.victims(JAB_REACH)) {
            double d = this.edgeDist(e);
            if (d > JAB_REACH || Math.abs(e.getY() - this.getY()) > 2.0 || !this.canSee(e)) {
                continue;
            }
            Vec3d to = new Vec3d(e.getX() - this.getX(), 0, e.getZ() - this.getZ());
            if (d > 0.8 && to.lengthSquared() > 0.01 && to.normalize().dotProduct(f) < Math.cos(Math.toRadians(70))) {
                continue;
            }
            e.timeUntilRegen = 0;
            if (e.damage(this.getDamageSources().mobAttack(this), JAB_DMG)) {
                n++;
                boolean shielded = e instanceof ShieldBlocker sb && sb.basedefense$blockedThisTick();
                if (!shielded) {
                    e.addStatusEffect(new StatusEffectInstance(ModEffects.STUN, STUN_TICKS, 0), this);
                    e.takeKnockback(0.4, this.getX() - e.getX(), this.getZ() - e.getZ());
                }
            }
        }
        if (this.getWorld() instanceof ServerWorld s) {
            Vec3d p = this.getPos().add(f.multiply(1.1));
            Fx.ring(s, p.add(0, 0.05, 0), 0.6, 8, 0, Fx.F_BLOCK);
        }
        this.playSound(ModSounds.SAPPER_JAB, 1.0f, 0.9f + this.random.nextFloat() * 0.2f);
        return n;
    }

    /** Точка левой руки в момент броска. */
    Vec3d handPos() {
        Vec3d f = this.forward();
        Vec3d left = new Vec3d(f.z, 0, -f.x);
        return this.getPos().add(f.multiply(0.35)).add(left.multiply(0.25)).add(0, 1.05, 0);
    }

    /** Бомба в точку (по постройке). */
    void throwAt(Vec3d to) {
        if (!(this.getWorld() instanceof ServerWorld s)) {
            return;
        }
        Vec3d from = this.handPos();
        int flight = (int) (14 + Math.sqrt(this.squaredDistanceTo(to)) * 0.9);
        SapperBombEntity b = new SapperBombEntity(ModEntities.SAPPER_BOMB, s);
        b.launch(this, from, to, flight);
        s.spawnEntity(b);
        this.playSound(ModSounds.SAPPER_THROW, 0.9f, 1.0f);
    }

    void throwBomb(LivingEntity target) {
        if (!(this.getWorld() instanceof ServerWorld s)) {
            return;
        }
        Vec3d from = this.handPos();
        double dist = Math.sqrt(this.squaredDistanceTo(target));
        int flight = (int) (14 + dist * 0.9);
        Vec3d lead = target.getVelocity().multiply(1, 0, 1).multiply(flight * 0.6);
        Vec3d to = target.getPos().add(lead).add((this.random.nextDouble() - 0.5) * 1.2, 0, (this.random.nextDouble() - 0.5) * 1.2);
        SapperBombEntity b = new SapperBombEntity(ModEntities.SAPPER_BOMB, s);
        b.launch(this, from, to, flight);
        s.spawnEntity(b);
        this.playSound(ModSounds.SAPPER_THROW, 0.9f, 1.0f);
    }

    void dropSmoke() {
        if (this.getWorld() instanceof ServerWorld s) {
            SapperSmokeEntity sm = new SapperSmokeEntity(ModEntities.SAPPER_SMOKE, s);
            sm.toss(this.getPos().add(this.forward().multiply(0.5)).add(0, 0.3, 0), this.forward().multiply(0.1));
            s.spawnEntity(sm);
        }
    }

    // =====================================================================
    // Звуки, сохранение
    // =====================================================================
    @Override
    protected SoundEvent getAmbientSound() {
        return this.isHidden() ? null : ModSounds.SAPPER_AMBIENT;
    }

    @Override
    public int getMinAmbientSoundDelay() {
        return 300;
    }

    @Override
    protected float getSoundVolume() {
        return 0.6f;
    }

    @Override
    protected SoundEvent getHurtSound(DamageSource source) {
        return ModSounds.SAPPER_HURT;
    }

    @Override
    protected SoundEvent getDeathSound() {
        return ModSounds.SAPPER_DEATH;
    }

    @Override
    protected void playStepSound(BlockPos pos, BlockState state) {
        if (!this.isUnder()) {
            this.playSound(ModSounds.SAPPER_STEP, 0.3f, 0.9f + this.random.nextFloat() * 0.25f);
        }
    }

    @Override
    public void writeCustomDataToNbt(NbtCompound nbt) {
        super.writeCustomDataToNbt(nbt);
        nbt.putBoolean("Under", this.isUnder());
        NbtList list = new NbtList();
        for (BlockPos p : this.lairs) {
            list.add(NbtLong.of(p.asLong()));
        }
        nbt.put("Lairs", list);
    }

    @Override
    public void readCustomDataFromNbt(NbtCompound nbt) {
        super.readCustomDataFromNbt(nbt);
        this.lairs.clear();
        for (NbtElement e : nbt.getList("Lairs", NbtElement.LONG_TYPE)) {
            this.lairs.add(BlockPos.fromLong(((NbtLong) e).longValue()));
        }
        if (nbt.getBoolean("Under")) {
            this.dataTracker.set(UNDER, true);
            this.noClip = true;
            this.setNoGravity(true);
        }
    }

    // =====================================================================
    // ИИ
    // =====================================================================
    static class SapperGoal extends Goal {
        private final SapperEntity mob;
        private int action = NONE;
        private int tick;
        private int bombCd = 30;
        private int jabCd;
        private int plantCd = 100;
        private int smokeCd;
        private int staggerCd;
        private int pathDelay;
        private int fleeTicks;
        private int walledCheck;
        private boolean walled;
        private boolean emergeCombo;
        private LivingEntity lastTarget;
        private LivingEntity aimEntity;
        private Vec3d aim;
        private Vec3d fleeFrom;
        private int targetTicks;
        // копание вниз
        private BlockPos shaft;
        private int shaftY0;
        // под землёй
        private BlockPos cell;
        private BlockPos prevCell;
        private BlockPos goal;
        private boolean goalEmerge;
        private final List<BlockPos> toBreak = new ArrayList<>();
        private int digTimer;
        private Vec3d moveFrom;
        private Vec3d moveTo;
        private int moveT = -1;
        private int hideTimer;
        private int noTarget;
        private int retries;
        // подрыв
        private BlockPos wall;
        private Direction wallFace;

        SapperGoal(SapperEntity mob) {
            this.mob = mob;
            this.setControls(EnumSet.of(Control.MOVE, Control.LOOK, Control.JUMP));
        }

        static boolean valid(LivingEntity t) {
            return t != null && t.isAlive() && !(t instanceof PlayerEntity p && (p.isCreative() || p.isSpectator()));
        }

        @Override
        public boolean canStart() {
            return this.mob.isUnder() || valid(this.mob.getTarget()) || this.mob.build != null;
        }

        @Override
        public boolean shouldContinue() {
            return this.action != NONE || this.canStart();
        }

        @Override
        public boolean shouldRunEveryTick() {
            return true;
        }

        @Override
        public void stop() {
            this.mob.getNavigation().stop();
            this.mob.dataTracker.set(SNEAK, false);
        }

        private boolean griefing() {
            return SapperBlast.griefing(this.mob.getWorld());
        }

        private void begin(int a) {
            this.action = a;
            this.tick = 0;
            this.mob.getNavigation().stop();
            this.mob.sendAction(a);
        }

        @Override
        public void tick() {
            SapperEntity m = this.mob;
            if (this.bombCd > 0) this.bombCd--;
            if (this.jabCd > 0) this.jabCd--;
            if (this.plantCd > 0) this.plantCd--;
            if (this.smokeCd > 0) this.smokeCd--;
            if (this.staggerCd > 0) this.staggerCd--;
            LivingEntity target = m.getTarget();
            if (!valid(target)) {
                target = null;
            }
            this.trackTarget(target);
            // игроки — только угроза вблизи (14 бл) или кто его бьёт; главная цель — постройки
            if (target != null && m.squaredDistanceTo(target) > 14 * 14 && m.getAttacker() != target) {
                target = null;
            }
            if (this.action != NONE) {
                this.tick++;
                this.runAction(target);
                return;
            }
            if (m.isUnder()) {
                this.underTick(target);
            } else if (this.escapePit()) {
                return;
            } else {
                this.surfaceTick(target);
            }
        }

        /** Одну цель держит не дольше 30 с — потом переключается на другого игрока, если есть. */
        private void trackTarget(LivingEntity target) {
            if (target != this.lastTarget) {
                this.lastTarget = target;
                this.targetTicks = 0;
                return;
            }
            if (target != null && ++this.targetTicks > MAX_TARGET_TICKS) {
                this.targetTicks = 0;
                PlayerEntity other = this.mob.getWorld().getClosestPlayer(this.mob.getX(), this.mob.getY(), this.mob.getZ(), 40,
                        p -> p != target && valid((LivingEntity) p));
                if (other != null) {
                    this.mob.setTarget(other);
                }
            }
        }

        // ------------------------------------------------------------- поверхность
        private void surfaceTick(LivingEntity target) {
            SapperEntity m = this.mob;
            if (target == null) {
                m.dataTracker.set(SNEAK, false);
                if (m.build != null) {
                    this.buildTick();
                }
                return;
            }
            double d = Math.sqrt(m.squaredDistanceTo(target));
            boolean see = m.getVisibilityCache().canSee(target);
            m.getLookControl().lookAt(target, 30f, 30f);
            if (--this.walledCheck <= 0) {
                this.walledCheck = 40;
                Path p = m.getNavigation().findPathTo(target, 1);
                this.walled = p == null || !p.reachesTarget();
            }
            if (this.fleeTicks > 0) {
                this.fleeTicks--;
                m.dataTracker.set(SNEAK, false);
                if (--this.pathDelay <= 0) {
                    this.pathDelay = 10;
                    Vec3d away = NoPenaltyTargeting.findFrom(m, 16, 7, target.getPos());
                    if (away != null) {
                        m.getNavigation().startMovingTo(away.x, away.y, away.z, 1.45);
                    }
                }
                if (this.fleeTicks == 0 && this.griefing() && this.canDigDown()) {
                    this.startDigDown();
                }
                return;
            }
            // «Тычок» вблизи
            if (d < JAB_REACH + 0.4 && this.jabCd <= 0 && see) {
                m.faceTo(target.getX(), target.getZ());
                this.begin(JAB);
                return;
            }
            // слишком близко, «Тычок» на перезарядке — побег: под землю или дым и бегом
            if (d < 6.0 && this.jabCd > 0) {
                this.flee(target);
                return;
            }
            // «Подрыв»: цель за стеной — стена прямо перед ним
            if (this.walled && this.plantCd <= 0 && this.griefing() && this.findWall(target)) {
                m.faceTo(this.wall.getX() + 0.5, this.wall.getZ() + 0.5);
                this.begin(PLANT);
                return;
            }
            // бомба
            if (d >= 7.0 && d <= 18.0 && this.bombCd <= 0 && see) {
                m.faceTo(target.getX(), target.getZ());
                this.aimEntity = target;
                this.aim = target.getPos();
                this.begin(THROW);
                return;
            }
            // далеко или за стенами — под землю (подкоп)
            if (this.griefing() && (d > 22.0 || this.walled && this.plantCd > 0) && m.age % 10 == 0 && this.canDigDown()) {
                this.startDigDown();
                return;
            }
            // держит дистанцию 8–14
            if (d > 14.0 || !see) {
                m.dataTracker.set(SNEAK, d < 24.0);
                if (--this.pathDelay <= 0) {
                    this.pathDelay = 8;
                    m.getNavigation().startMovingTo(target, d < 24.0 ? 0.85 : 1.05);
                }
            } else if (d < 8.0) {
                m.dataTracker.set(SNEAK, false);
                if (--this.pathDelay <= 0) {
                    this.pathDelay = 10;
                    Vec3d away = NoPenaltyTargeting.findFrom(m, 8, 4, target.getPos());
                    if (away != null) {
                        m.getNavigation().startMovingTo(away.x, away.y, away.z, 1.1);
                    }
                }
            } else {
                m.dataTracker.set(SNEAK, true);
                m.getNavigation().stop();
                m.getLookControl().lookAt(target, 30f, 30f);
            }
        }

        /**
         * Режим «ломать постройки»: идёт к ближайшему не природному блоку; блок рядом — вешает заряд,
         * заряд на перезарядке — закидывает бомбами с 4–16 блоков; далеко или не пройти — подкапывается.
         */
        private void buildTick() {
            SapperEntity m = this.mob;
            BlockPos b = m.build;
            Vec3d bc = Vec3d.ofCenter(b);
            double d = Math.sqrt(m.squaredDistanceTo(bc.x, m.getY(), bc.z));
            m.getLookControl().lookAt(bc.x, bc.y, bc.z);
            if (this.fleeTicks > 0) {
                this.fleeTicks--;
                if (--this.pathDelay <= 0) {
                    this.pathDelay = 10;
                    Vec3d away = NoPenaltyTargeting.findFrom(m, 10, 5, this.fleeFrom != null ? this.fleeFrom : bc);
                    if (away != null) {
                        m.getNavigation().startMovingTo(away.x, away.y, away.z, 1.4);
                    }
                }
                return;
            }
            if (--this.walledCheck <= 0) {
                this.walledCheck = 40;
                Path p = m.getNavigation().findPathTo(b, 1);
                this.walled = p == null || !p.reachesTarget();
            }
            if (this.plantCd <= 0 && this.findBuildWall(b)) {
                m.faceTo(this.wall.getX() + 0.5, this.wall.getZ() + 0.5);
                this.begin(PLANT);
                return;
            }
            if (this.bombCd <= 0 && this.plantCd > 0 && d >= 4.0 && d <= 16.0) {
                m.faceTo(bc.x, bc.z);
                this.aimEntity = null;
                this.aim = bc.add(0, 0.5, 0);
                this.begin(THROW);
                return;
            }
            // далеко — идёт поверху (по точкам), ближе 32 — подкапывается к постройке
            if (d > 32.0) {
                m.dataTracker.set(SNEAK, false);
                if (--this.pathDelay <= 0) {
                    this.pathDelay = 20;
                    BuildTargets.walkTo(m, bc, 1.05);
                }
                return;
            }
            if ((d > 8.0 || this.walled && d > 3.0) && m.age % 10 == 0 && this.canDigDown()) {
                this.startDigDown();
                return;
            }
            if (d > 2.0) {
                if (--this.pathDelay <= 0) {
                    this.pathDelay = 10;
                    BuildTargets.walkTo(m, new Vec3d(bc.x, b.getY(), bc.z), 1.0);
                }
            } else {
                m.getNavigation().stop();
                // факел, лестница, открытая дверь — заряд не повесить: сбивает киркой
                World w = m.getWorld();
                if (passable(w, b) && Math.abs(b.getY() - m.getBlockY()) <= 2 && m.age % 10 == 0) {
                    w.breakBlock(b, false, m);
                    m.swingHand(net.minecraft.util.Hand.MAIN_HAND);
                    m.playSound(ModSounds.SAPPER_DIG, 0.8f, 1.1f);
                }
            }
        }

        /** Куда повесить заряд: сам блок постройки, если он рядом, иначе постройка прямо перед ним по пути. */
        private boolean findBuildWall(BlockPos b) {
            SapperEntity m = this.mob;
            World w = m.getWorld();
            int fy = m.getBlockY();
            if (b.getY() >= fy - 1 && b.getY() <= fy + 2 && m.squaredDistanceTo(Vec3d.ofCenter(b)) < 2.6 * 2.6) {
                Vec3d to = m.getPos().subtract(Vec3d.ofCenter(b));
                Direction face = Direction.getFacing(to.x, 0, to.z);
                if (passable(w, b.offset(face))) {
                    this.wall = b;
                    this.wallFace = face;
                    return true;
                }
            }
            Vec3d bc = Vec3d.ofCenter(b);
            Vec3d to = new Vec3d(bc.x - m.getX(), 0, bc.z - m.getZ());
            if (to.lengthSquared() < 1e-4) {
                return false;
            }
            Direction dir = Direction.getFacing(to.x, 0, to.z);
            for (int dy = 0; dy <= 1; dy++) {
                BlockPos p = m.getBlockPos().offset(dir).up(dy);
                // блок постройки — или любая преграда, если к постройке не пройти
                if ((BuiltBlocks.isTarget(w.getBlockState(p)) || this.walled) && !passable(w, p)
                        && SapperBlast.breakable(w, p, Float.MAX_VALUE)) {
                    this.wall = p.toImmutable();
                    this.wallFace = dir.getOpposite();
                    return true;
                }
            }
            return false;
        }

        private void flee(LivingEntity target) {
            if (this.griefing() && this.canDigDown()) {
                this.startDigDown();
            } else if (this.smokeCd <= 0) {
                this.mob.faceTo(target.getX(), target.getZ());
                this.begin(SMOKE);
            } else {
                this.fleeTicks = 40;
            }
        }

        /** Блок стены между ним и целью — прямо перед ним (ноги или голова). */
        private boolean findWall(LivingEntity target) {
            SapperEntity m = this.mob;
            World w = m.getWorld();
            Vec3d to = new Vec3d(target.getX() - m.getX(), 0, target.getZ() - m.getZ());
            if (to.lengthSquared() < 1e-4) {
                return false;
            }
            Direction dir = Direction.getFacing(to.x, 0, to.z);
            BlockPos feet = m.getBlockPos();
            for (int dy = 0; dy <= 1; dy++) {
                BlockPos p = feet.offset(dir).up(dy);
                if (SapperBlast.breakable(w, p, MAX_DIG_HARDNESS) && !passable(w, p)
                        && m.squaredDistanceTo(Vec3d.ofCenter(p)) < 2.6 * 2.6) {
                    this.wall = p.toImmutable();
                    this.wallFace = dir.getOpposite();
                    return true;
                }
            }
            return false;
        }

        /** Можно ли нырнуть здесь: шахта 3 блока впереди прокапывается, под ней твёрдое, без воды. */
        private boolean canDigDown() {
            SapperEntity m = this.mob;
            World w = m.getWorld();
            BlockPos feet = m.getBlockPos();
            if (m.inPit()) {
                return false;                       // уже в яме — глубже не роет, сначала выбирается
            }
            BlockPos f = BlockPos.ofFloored(m.getPos().add(m.forward().multiply(0.8)));
            BlockPos col = new BlockPos(f.getX(), feet.getY(), f.getZ());
            if (!passable(w, col)) {
                col = feet;
            }
            for (int k = 1; k <= 3; k++) {
                BlockPos p = col.down(k);
                if (!diggableOrOpen(w, p) || wet(w, p.up())) {
                    return false;
                }
            }
            if (!solidFloor(w, col.down(4))) {
                return false;
            }
            this.shaft = col;
            this.shaftY0 = feet.getY();
            return true;
        }

        private void startDigDown() {
            this.mob.dataTracker.set(SNEAK, false);
            this.mob.faceTo(this.shaft.getX() + 0.5, this.shaft.getZ() + 0.5);
            if (this.mob.lairs.contains(this.shaft) && this.mob.lairOpen(this.shaft)) {
                this.begin(DIVE);
            } else {
                this.begin(DIG_DOWN);
            }
        }

        private void diveIn() {
            SapperEntity m = this.mob;
            BlockPos cellPos = this.shaft.down(3);
            m.rememberLair(this.shaft);
            m.enterUnder(cellPos);
            this.cell = cellPos;
            this.prevCell = null;
            this.goal = null;
            this.toBreak.clear();
            this.moveT = -1;
            this.emergeCombo = false;
            // сразу отходит от лаза под землёй
            LivingEntity t = m.getTarget();
            this.hideTimer = 0;
            if (valid(t)) {
                this.planRetreat(t);
            }
        }

        // ------------------------------------------------------------- действия
        private void runAction(LivingEntity target) {
            SapperEntity m = this.mob;
            World w = m.getWorld();
            int t = this.tick;
            switch (this.action) {
                case DIG_DOWN -> {
                    m.setVelocity(0, 0, 0);
                    for (int k = 0; k < DIG_HITS.length; k++) {
                        if (t == DIG_HITS[k]) {
                            BlockPos p = this.shaft.down(k + 1);
                            if (!passable(w, p)) {
                                if (SapperBlast.breakable(w, p, MAX_DIG_HARDNESS)) {
                                    m.digBlock(p);
                                } else {
                                    this.finish(10);       // не прокопать — прерывает
                                    return;
                                }
                            } else {
                                m.playSound(ModSounds.SAPPER_DIG, 0.8f, 1.1f);
                            }
                        }
                    }
                    if (t == 20) {
                        m.setNoGravity(true);
                        m.mound(this.shaft, Direction.UP);
                    }
                    if (t >= DIG_TICKS) {
                        this.finish(0);
                        this.diveIn();
                    }
                }
                case DIVE -> {
                    m.setNoGravity(true);
                    m.setVelocity(0, 0, 0);
                    if (t >= DIVE_TICKS) {
                        this.finish(0);
                        this.diveIn();
                    }
                }
                case EMERGE -> {
                    m.setVelocity(0, 0, 0);
                    if (t >= EMERGE_TICKS) {
                        m.setNoGravity(false);
                        this.finish(4);
                        this.emergeCombo = true;
                        this.bombCd = Math.min(this.bombCd, 2);
                    }
                }
                case HIDE -> {
                    if (t == HIDE_SNUFF) {
                        m.dataTracker.set(HIDDEN, true);
                        m.playSound(ModSounds.SAPPER_SNUFF, 0.6f, 1.0f);
                    }
                    if (t >= HIDE_TICKS) {
                        this.finish(0);
                    }
                }
                case THROW -> {
                    LivingEntity ae = valid(this.aimEntity) ? this.aimEntity : null;
                    Vec3d at = ae != null ? ae.getPos() : this.aim;
                    if (at != null && t < THROW_RELEASE) {
                        m.getLookControl().lookAt(at.x, at.y + 0.5, at.z);
                        if (t > THROW_RELEASE - 4) {
                            m.faceTo(at.x, at.z);
                        }
                    }
                    if (t == THROW_LIGHT) {
                        m.playSound(ModSounds.SAPPER_LIGHT, 0.8f, 1.0f);
                    }
                    if (t == THROW_RELEASE && at != null) {
                        if (ae != null) {
                            m.throwBomb(ae);
                        } else {
                            m.throwAt(at);
                        }
                        this.bombCd = BOMB_CD;
                    }
                    if (t >= THROW_TICKS) {
                        this.finish(6);
                        // выскочил из лаза — бросил — ныряет обратно
                        if (this.emergeCombo && this.griefing() && this.canDigDown()) {
                            this.startDigDown();
                        } else if (this.griefing() && m.random.nextInt(2) == 0 && this.canDigDown()) {
                            this.startDigDown();
                        }
                        this.emergeCombo = false;
                    }
                }
                case PLANT -> {
                    m.setVelocity(0, m.getVelocity().y, 0);
                    for (int h : PLANT_HITS) {
                        if (t == h) {
                            m.playSound(ModSounds.SAPPER_JAB, 0.9f, 1.2f);
                        }
                    }
                    if (t == PLANT_HITS[1] && w instanceof ServerWorld s && this.wall != null) {
                        SapperChargeEntity c = new SapperChargeEntity(ModEntities.SAPPER_CHARGE, s);
                        c.setup(m, this.wall, this.wallFace);
                        s.spawnEntity(c);
                        m.playSound(ModSounds.SAPPER_LIGHT, 0.9f, 0.9f);
                        this.plantCd = PLANT_CD;
                    }
                    if (t == PLANT_HOP) {
                        Vec3d b = m.forward().multiply(-0.45);
                        m.setVelocity(b.x, 0.32, b.z);
                        m.velocityModified = true;
                    }
                    if (t >= PLANT_TICKS) {
                        this.finish(0);
                        if (this.griefing() && this.canDigDown()) {
                            this.startDigDown();
                        } else {
                            this.fleeFrom = this.wall != null ? Vec3d.ofCenter(this.wall) : m.getPos();
                            this.fleeTicks = 50;
                        }
                    }
                }
                case JAB -> {
                    if (target != null && t < JAB_HIT - 1) {
                        m.faceTo(target.getX(), target.getZ());
                    }
                    if (t == JAB_HIT) {
                        m.jabHit();
                        this.jabCd = JAB_CD;
                    }
                    if (t >= JAB_TICKS) {
                        this.finish(0);
                        if (m.isUnder()) {
                            m.dataTracker.set(HIDDEN, false);
                            if (target != null) {
                                this.planRetreat(target);
                            }
                        } else if (target != null) {
                            this.flee(target);
                        }
                    }
                }
                case STAGGER -> {
                    if (t >= STAGGER_TICKS) {
                        this.finish(0);
                    }
                }
                case SMOKE -> {
                    if (t == SMOKE_DROP) {
                        m.dropSmoke();
                        this.smokeCd = SMOKE_CD;
                    }
                    if (t >= SMOKE_TICKS) {
                        this.finish(0);
                        this.fleeTicks = 60;
                    }
                }
                default -> this.finish(0);
            }
        }

        private void finish(int rest) {
            this.action = NONE;
            this.pathDelay = rest;
        }

        /** Сильный удар по поверхности — шатается (не чаще раза в 5 с). */
        void onBigHit() {
            if (this.action == NONE && !this.mob.isUnder() && this.staggerCd <= 0) {
                this.staggerCd = 100;
                this.begin(STAGGER);
            }
        }

        // ------------------------------------------------------------- под землёй
        private void underTick(LivingEntity target) {
            SapperEntity m = this.mob;
            World w = m.getWorld();
            if (this.cell == null) {
                this.cell = m.getBlockPos();
            }
            m.setVelocity(Vec3d.ZERO);
            // залили водой/лавой — выбирается наверх
            if (wet(w, this.cell) || wet(w, this.cell.up())) {
                this.emergeHere(true);
                return;
            }
            // засыпали — откапывается
            for (int dy = 0; dy <= 1 && this.toBreak.isEmpty() && !this.queuedMove && this.moveT < 0; dy++) {
                BlockPos p = this.cell.up(dy);
                if (!passable(w, p)) {
                    if (SapperBlast.breakable(w, p, MAX_DIG_HARDNESS)) {
                        if (++this.digTimer >= DIG_INTERVAL) {
                            this.digTimer = 0;
                            m.digBlock(p);
                        }
                        m.dataTracker.set(DIGGING, true);
                        return;
                    }
                    this.emergeHere(true);
                    return;
                }
            }
            // засада: игрок подошёл на 3 блока — «Тычок»
            PlayerEntity near = w.getClosestPlayer(m.getX(), m.getY(), m.getZ(), JAB_REACH + 0.3, e -> valid((LivingEntity) e));
            if (near != null && this.jabCd <= 0 && m.canSee(near) && this.moveT < 0) {
                m.faceTo(near.getX(), near.getZ());
                this.begin(JAB);
                return;
            }
            if (target == null) {
                this.noTarget++;
            } else {
                this.noTarget = 0;
            }
            // сидит в засаде
            if (m.isHidden()) {
                if (--this.hideTimer <= 0 || target != null && this.bombCd <= 20 && m.squaredDistanceTo(target) > 36
                        || target == null && m.build != null && this.hideTimer > 40) {
                    m.dataTracker.set(HIDDEN, false);
                    this.goal = null;
                } else {
                    return;
                }
            }
            // движение по клетке
            if (this.moveT >= 0) {
                this.moveT++;
                float k = Math.min(1f, this.moveT / (float) STEP_TICKS);
                Vec3d p = this.moveFrom.lerp(this.moveTo, k);
                m.setPosition(p.x, p.y, p.z);
                if (k >= 1f) {
                    this.moveT = -1;
                }
                return;
            }
            if (!this.toBreak.isEmpty()) {
                m.dataTracker.set(DIGGING, true);
                if (++this.digTimer >= DIG_INTERVAL) {
                    this.digTimer = 0;
                    BlockPos p = this.toBreak.remove(0);
                    if (!passable(w, p)) {
                        if (SapperBlast.breakable(w, p, MAX_DIG_HARDNESS)) {
                            m.digBlock(p);
                        } else {
                            // не прокопать (кто‑то поставил обсидиан) — остаётся на месте, ищет другой путь
                            this.toBreak.clear();
                            this.goal = null;
                            this.pendingEmerge = false;
                            if (this.queuedMove && this.prevCell != null) {
                                this.cell = this.prevCell;
                            }
                            this.queuedMove = false;
                            return;
                        }
                    }
                    if (this.toBreak.isEmpty()) {
                        if (this.pendingEmerge) {
                            this.pendingEmerge = false;
                            this.emergeHere(false);
                            return;
                        }
                        if (this.queuedMove) {
                            this.queuedMove = false;
                            this.moveT = 0;
                        }
                    }
                }
                return;
            }
            m.dataTracker.set(DIGGING, false);
            if (this.goal == null) {
                this.chooseGoal(target);
                if (this.goal == null) {
                    return;
                }
            }
            int dx = this.goal.getX() - this.cell.getX();
            int dz = this.goal.getZ() - this.cell.getZ();
            if (Math.abs(dx) + Math.abs(dz) <= 1) {
                if (this.goalEmerge) {
                    this.startEmergeShaft();
                } else {
                    this.goal = null;
                    this.ambush(60 + m.random.nextInt(80));
                }
                return;
            }
            BlockPos next = this.nextCell(dx, dz);
            if (next == null) {
                if (this.goalEmerge) {
                    this.startEmergeShaft();
                } else {
                    this.goal = null;
                    this.ambush(60);
                }
                return;
            }
            // прокопать клетку (ноги и голова), потом шагнуть
            for (BlockPos p : new BlockPos[]{next, next.up()}) {
                if (!passable(w, p)) {
                    this.toBreak.add(p);
                }
            }
            this.prevCell = this.cell;
            this.moveFrom = m.getPos();
            this.moveTo = new Vec3d(next.getX() + 0.5, next.getY(), next.getZ() + 0.5);
            m.faceTo(this.moveTo.x, this.moveTo.z);
            this.cell = next;
            if (this.toBreak.isEmpty()) {
                this.moveT = 0;
            } else {
                this.digTimer = DIG_INTERVAL - 1;
                this.queuedMove = true;
            }
        }

        private int pitTicks;

        /** Застрял в яме (своём лазе) больше 1 с — выбирается наверх, как из туннеля. */
        private boolean escapePit() {
            SapperEntity m = this.mob;
            if (!m.isOnGround() || !m.inPit()) {
                this.pitTicks = 0;
                return false;
            }
            if (++this.pitTicks < 20) {
                return false;
            }
            this.pitTicks = 0;
            this.cell = m.getBlockPos();
            this.emergeHere(false);
            return true;
        }

        private boolean pendingEmerge;
        private boolean queuedMove;

        @Override
        public void start() {
            this.queuedMove = false;
        }

        /** Следующая клетка хода: шаг по большей оси, иначе по меньшей, иначе в обход; держит глубину. */
        private BlockPos nextCell(int dx, int dz) {
            World w = this.mob.getWorld();
            List<Direction> order = new ArrayList<>();
            Direction ax = dx > 0 ? Direction.EAST : Direction.WEST;
            Direction az = dz > 0 ? Direction.SOUTH : Direction.NORTH;
            if (Math.abs(dx) >= Math.abs(dz)) {
                order.add(ax);
                if (dz != 0) order.add(az);
            } else {
                order.add(az);
                if (dx != 0) order.add(ax);
            }
            order.add(order.get(0).rotateYClockwise());
            order.add(order.get(0).rotateYCounterclockwise());
            for (Direction d : order) {
                BlockPos base = this.cell.offset(d);
                for (int dy : this.depthPrefs(base)) {
                    BlockPos n = base.up(dy);
                    if (n.equals(this.prevCell)) {
                        continue;
                    }
                    if (diggableOrOpen(w, n) && diggableOrOpen(w, n.up()) && !wet(w, n.up(2)) && solidFloor(w, n.down())
                            && !wet(w, n) && !wet(w, n.up())) {
                        return n;
                    }
                }
            }
            return null;
        }

        /** Глубина хода: 2–3 блока под поверхностью; сверху открыто — ниже, слишком глубоко — выше. */
        private int[] depthPrefs(BlockPos col) {
            World w = this.mob.getWorld();
            int top = surfaceAt(w, col.getX(), col.getZ());
            int depth = top - col.getY();
            if (depth < 3) {
                return new int[]{-1, 0};
            }
            if (depth > 6) {
                return new int[]{1, 0};
            }
            return new int[]{0, 1, -1};
        }

        private void chooseGoal(LivingEntity target) {
            SapperEntity m = this.mob;
            if (target == null && m.build != null) {
                double bd = Math.sqrt(m.squaredDistanceTo(m.build.getX() + 0.5, m.getY(), m.build.getZ() + 0.5));
                if (bd > 36.0) {
                    this.emergeHere(false);          // далеко — выходит наверх и идёт поверху
                } else {
                    this.planEmergeNear(m.build);
                }
                return;
            }
            if (target == null) {
                if (this.noTarget > 600 && this.griefing()) {
                    this.emergeHere(false);         // долго никого — выходит наверх
                    return;
                }
                this.ambush(100 + m.random.nextInt(100));
                return;
            }
            double d = Math.sqrt(m.squaredDistanceTo(target.getX(), m.getY(), target.getZ()));
            if (this.bombCd <= 40) {
                this.planEmerge(target);
            } else if (d < 6.0) {
                this.planRetreat(target);
            } else {
                this.ambush(Math.max(20, this.bombCd - 30));
            }
        }

        /** Куда вынырнуть: старый лаз в 8–15 блоках от цели или новая точка в ≈10 (за стенами — прямо у цели). */
        private void planEmerge(LivingEntity target) {
            SapperEntity m = this.mob;
            BlockPos best = null;
            for (BlockPos l : m.lairs) {
                double d = Math.sqrt(l.getSquaredDistance(target.getX(), l.getY(), target.getZ()));
                if (d >= 8 && d <= 15 && m.lairOpen(l) && (best == null || m.random.nextBoolean())) {
                    best = l;
                }
            }
            if (best != null && !this.walled) {
                this.goal = best.down(3);
            } else {
                double r = this.walled ? 3.5 + m.random.nextDouble() * 2 : 9 + m.random.nextDouble() * 3;
                double base = Math.atan2(m.getZ() - target.getZ(), m.getX() - target.getX());
                double ang = base + (m.random.nextDouble() - 0.5) * Math.toRadians(100);
                this.goal = new BlockPos(MathHelper.floor(target.getX() + Math.cos(ang) * r), this.cell.getY(),
                        MathHelper.floor(target.getZ() + Math.sin(ang) * r));
            }
            this.goalEmerge = true;
            this.retries = 0;
        }

        /** Вынырнуть в 2–3 блоках от постройки, со своей стороны. */
        private void planEmergeNear(BlockPos b) {
            SapperEntity m = this.mob;
            double r = 2.0 + m.random.nextDouble();
            double ang = Math.atan2(m.getZ() - (b.getZ() + 0.5), m.getX() - (b.getX() + 0.5)) + (m.random.nextDouble() - 0.5);
            this.goal = new BlockPos(MathHelper.floor(b.getX() + 0.5 + Math.cos(ang) * r), this.cell.getY(),
                    MathHelper.floor(b.getZ() + 0.5 + Math.sin(ang) * r));
            this.goalEmerge = true;
            this.retries = 0;
        }

        /** Отходит под землёй от цели на ≈12 блоков. */
        private void planRetreat(LivingEntity target) {
            SapperEntity m = this.mob;
            double ang = Math.atan2(m.getZ() - target.getZ(), m.getX() - target.getX()) + (m.random.nextDouble() - 0.5);
            this.goal = new BlockPos(MathHelper.floor(m.getX() + Math.cos(ang) * 9), this.cell != null ? this.cell.getY() : m.getBlockY(),
                    MathHelper.floor(m.getZ() + Math.sin(ang) * 9));
            this.goalEmerge = false;
        }

        private void ambush(int ticks) {
            if (this.action == NONE) {
                this.hideTimer = ticks;
                this.begin(HIDE);
            }
        }

        /** Прокопать шахту вверх (по блоку за 5 тиков), потом выскочить. */
        private void startEmergeShaft() {
            World w = this.mob.getWorld();
            int top = surfaceAt(w, this.cell.getX(), this.cell.getZ());
            this.toBreak.clear();
            for (int y = this.cell.getY() + 2; y < top; y++) {
                BlockPos p = new BlockPos(this.cell.getX(), y, this.cell.getZ());
                if (passable(w, p)) {
                    continue;
                }
                if (!SapperBlast.breakable(w, p, MAX_DIG_HARDNESS) || wet(w, p)) {
                    this.toBreak.clear();
                    this.goal = null;
                    if (++this.retries < 3 && this.mob.getTarget() != null) {
                        this.planEmerge(this.mob.getTarget());
                    } else {
                        this.ambush(80);
                    }
                    return;
                }
                this.toBreak.add(p);
            }
            if (this.toBreak.isEmpty()) {
                this.emergeHere(false);
            } else {
                this.pendingEmerge = true;
                this.digTimer = DIG_INTERVAL - 1;
            }
        }

        /** Выскочить наверх над текущей клеткой: насыпь, фонтан земли, анимация выхода у края ямы. */
        private void emergeHere(boolean force) {
            SapperEntity m = this.mob;
            World w = m.getWorld();
            BlockPos c = this.cell != null ? this.cell : m.getBlockPos();
            int top = surfaceAt(w, c.getX(), c.getZ());
            if (force) {
                // аварийно: прокапывает всё до поверхности сразу (только прокапываемое)
                for (int y = c.getY(); y < top; y++) {
                    BlockPos p = new BlockPos(c.getX(), y, c.getZ());
                    if (!passable(w, p) && SapperBlast.breakable(w, p, MAX_DIG_HARDNESS)) {
                        w.breakBlock(p, false, m);
                    }
                }
            }
            BlockPos hole = new BlockPos(c.getX(), top, c.getZ());
            LivingEntity t = m.getTarget();
            Direction side = null;
            double best = 1e9;
            for (Direction d : new Direction[]{Direction.NORTH, Direction.SOUTH, Direction.EAST, Direction.WEST}) {
                BlockPos s = hole.offset(d);
                if (passable(w, s) && passable(w, s.up()) && solidFloor(w, s.down())) {
                    double score = t != null ? Vec3d.ofCenter(s).squaredDistanceTo(t.getPos())
                            : m.build != null ? Vec3d.ofCenter(s).squaredDistanceTo(Vec3d.ofCenter(m.build)) : m.random.nextDouble();
                    if (score < best) {
                        best = score;
                        side = d;
                    }
                }
            }
            Vec3d pos;
            if (side != null) {
                pos = new Vec3d(hole.getX() + 0.5 + side.getOffsetX() * 0.55, top, hole.getZ() + 0.5 + side.getOffsetZ() * 0.55);
                m.mound(hole, side);
            } else {
                pos = new Vec3d(hole.getX() + 0.5, top, hole.getZ() + 0.5);
            }
            m.leaveUnder(pos);
            m.setNoGravity(side != null);
            m.dataTracker.set(HIDDEN, false);
            m.rememberLair(hole);
            if (t != null) {
                m.faceTo(t.getX(), t.getZ());
            } else if (m.build != null) {
                m.faceTo(m.build.getX() + 0.5, m.build.getZ() + 0.5);
            }
            if (w instanceof ServerWorld s) {
                BlockState ground = w.getBlockState(hole.offset(side != null ? side : Direction.NORTH).down());
                Fx.fountain(s, Vec3d.ofBottomCenter(hole), ground.isAir() ? Blocks.DIRT.getDefaultState() : ground);
            }
            m.playSound(ModSounds.SAPPER_EMERGE, 1.0f, 1.0f);
            this.goal = null;
            this.toBreak.clear();
            this.moveT = -1;
            this.queuedMove = false;
            this.walledCheck = 0;
            this.begin(EMERGE);
        }
    }

    // ------------------------------------------------------------- перехват урона для «шатания»
    @Override
    protected void applyDamage(DamageSource source, float amount) {
        super.applyDamage(source, amount);
        if (amount >= 6f && !this.getWorld().isClient && this.random.nextInt(2) == 0) {
            this.goalSelector.getGoals().forEach(g -> {
                if (g.getGoal() instanceof SapperGoal sg) {
                    sg.onBigHit();
                }
            });
        }
    }
}

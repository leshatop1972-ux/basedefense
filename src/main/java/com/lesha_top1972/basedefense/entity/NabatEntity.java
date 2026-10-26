package com.lesha_top1972.basedefense.entity;

import com.lesha_top1972.basedefense.effect.BossEffectsSync;
import com.lesha_top1972.basedefense.effect.ModEffects;
import com.lesha_top1972.basedefense.sound.ModSounds;
import net.minecraft.block.BlockState;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityStatuses;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.SpawnReason;
import net.minecraft.entity.attribute.DefaultAttributeContainer;
import net.minecraft.entity.attribute.EntityAttributeInstance;
import net.minecraft.entity.attribute.EntityAttributeModifier;
import net.minecraft.entity.attribute.EntityAttributes;
import net.minecraft.entity.boss.BossBar;
import net.minecraft.entity.boss.ServerBossBar;
import net.minecraft.entity.damage.DamageSource;
import net.minecraft.entity.data.DataTracker;
import net.minecraft.entity.data.TrackedData;
import net.minecraft.entity.data.TrackedDataHandlerRegistry;
import net.minecraft.entity.decoration.ArmorStandEntity;
import net.minecraft.entity.effect.StatusEffect;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.entity.mob.HostileEntity;
import net.minecraft.entity.mob.MobEntity;
import net.minecraft.entity.mob.Monster;
import net.minecraft.entity.mob.PhantomEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.projectile.ProjectileEntity;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.particle.BlockStateParticleEffect;
import net.minecraft.particle.DustParticleEffect;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvent;
import net.minecraft.text.Text;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.Heightmap;
import net.minecraft.world.World;
import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Босс «Набат» — летучий страж‑звонарь. Почти весь бой висит в небе под «Куполом», который держат
 * колокола‑опоры; бьёт редко — основную работу делают его слуги (мечники, мелкие кровожады, яйца, фантомы).
 * Сломали все колокола — падает на 14 с: сначала звонит «Реквием» (лечится душами слуг — сбей звон ударами
 * по языку‑сердцу), потом «Колокольный плен» и удары молотом, затем взлетает с новой «Трещиной».
 * При 60% HP выпускает из реликвария улана (пока улан жив — купол держится), при 30% — «Треснувший колокол».
 * Летает по арене разными манёврами (круг, пролёт, восьмёрка, зависание над игроком); слуг раскидывает по разным игрокам.
 * Тайминги совпадают с анимациями tools/nabat_gen.py (там же печатаются точки ударов).
 */
public class NabatEntity extends HostileEntity {

    public static final float MODEL_SCALE = 1.6f;
    public static final byte STATUS_BASE = 70;

    // --- действия (номер = статус − 70; клипы в NabatModel) ---
    public static final int NONE = -1;
    public static final int INTRO = 0;
    public static final int TOLL = 1;
    public static final int CENSER = 2;
    public static final int GATHER = 3;
    public static final int BELLS = 4;
    public static final int RAIN = 5;
    public static final int MARK = 6;
    public static final int EGGS = 7;
    public static final int PHANTOMS = 8;
    public static final int RELIQ = 9;
    public static final int CRACK = 10;
    public static final int DIVE = 11;
    public static final int REAP = 12;
    public static final int QUAKE = 13;
    public static final int CRASH = 14;
    public static final int REQUIEM = 15;
    public static final int REQ_BREAK = 16;
    public static final int CAPTIVE = 17;
    public static final int MAUL = 18;
    public static final int RISE = 19;
    /** Не действие: рябь купола в месте попадания (клиент). */
    public static final int DOME_HIT = 40;
    /** Не действие: душа поглощена (клиент — вспышка нимба). */
    public static final int SOUL_IN = 41;

    // длительности (тики) — как клипы
    static final int[] LEN = {100, 48, 52, 84, 44, 40, 36, 40, 48, 80, 52, 64, 68, 106, 40, 60, 26, 44, 36, 36};
    static final int TOLL_HIT = 25;
    static final int CENSER_AT = 26;
    static final int[] GATHER_HITS = {33, 48, 63};
    static final int BELLS_AT = 20;
    static final int RAIN_AT = 18;
    static final int MARK_AT = 16;
    static final int EGGS_AT = 20;
    static final int PHANTOMS_AT = 24;
    static final int RELIQ_AT = 44;
    static final int CRACK_AT = 24;
    static final int DIVE_FALL = 18;
    static final int DIVE_HIT = 28;
    static final int DIVE_UP = 40;
    static final int REAP_IN = 16;
    static final int REAP_OUT = 48;
    static final int QUAKE_LAND = 20;
    static final int[] QUAKE_HITS = {31, 55, 79};
    static final int QUAKE_UP = 88;
    static final int CRASH_FALL = 10;
    static final int CRASH_HIT = 22;
    static final int CAPTIVE_AT = 22;
    static final int MAUL_HIT = 19;
    static final int RISE_LIFT = 8;
    static final int REQ_HITS_END = 60;

    public static final int DEATH_TICKS = 72;
    static final int GROUND_TICKS = 280;
    static final int STAGGER_BONUS = 80;

    // --- цифры (пока слабые; поднимать по просьбе) ---
    static final double BASE_HP = 900.0;
    static final float DOME_FACTOR = 0.2f;
    static final float PROJECTILE_FACTOR = 0.4f;
    static final float HEART_FACTOR = 2.0f;
    static final float REQUIEM_BREAK_DAMAGE = 40f;
    static final int MAX_REQUIEM = 20;
    static final float REQUIEM_HEAL_PER_STACK = 0.01f;
    static final float TOLL_DMG = 6f;
    static final double TOLL_R = 7.0;
    static final float DIVE_DMG = 11f;
    static final double DIVE_R = 4.5;
    static final float REAP_DMG = 8f;
    static final float CRASH_DMG = 6f;
    static final float MAUL_DMG = 6f;
    static final float RISE_DMG = 5f;
    static final double HOVER_H = 12.0;
    static final double HOVER_H3 = 8.0;
    static final double HOVER_LANCER = 14.0;
    static final double ARENA_R = 30.0;
    // манёвры полёта
    static final int FLY_ORBIT = 0;
    static final int FLY_SWEEP = 1;
    static final int FLY_EIGHT = 2;
    static final int FLY_STALK = 3;
    // усиление слуг (множители к базовым атрибутам)
    static final UUID BUFF_HP = UUID.fromString("6b1c2a0e-4f3d-4e21-9c55-1d9a7e3b5a01");
    static final UUID BUFF_DMG = UUID.fromString("6b1c2a0e-4f3d-4e21-9c55-1d9a7e3b5a02");

    // точка языка‑сердца на земле (блоки: вправо, вверх, вперёд; печатает nabat_gen.py)
    static final double HEART_UP = 0.34;
    static final double HEART_FWD = 0.2;
    // точка, куда бьёт молот (земля впереди)
    static final double MAUL_FWD = 4.1;

    private static final TrackedData<Boolean> GROUNDED = DataTracker.registerData(NabatEntity.class,
            TrackedDataHandlerRegistry.BOOLEAN);
    private static final TrackedData<Boolean> PHASE3 = DataTracker.registerData(NabatEntity.class,
            TrackedDataHandlerRegistry.BOOLEAN);
    private static final TrackedData<Boolean> RELIQ_GONE = DataTracker.registerData(NabatEntity.class,
            TrackedDataHandlerRegistry.BOOLEAN);
    private static final DustParticleEffect AMBER = new DustParticleEffect(new Vector3f(1.0f, 0.72f, 0.3f), 1.2f);
    private static final DustParticleEffect AMBER_S = new DustParticleEffect(new Vector3f(1.0f, 0.8f, 0.45f), 0.7f);
    private static final DustParticleEffect TEAL = new DustParticleEffect(new Vector3f(0.3f, 0.95f, 0.9f), 1.2f);
    private static final DustParticleEffect TRAIL = new DustParticleEffect(new Vector3f(1.0f, 0.78f, 0.42f), 0.8f);

    private final ServerBossBar bossBar = new ServerBossBar(this.getDisplayName(), BossBar.Color.WHITE,
            BossBar.Style.NOTCHED_10);

    /** Только на клиенте: текущее действие и когда оно началось. */
    public int clientAction = NONE;
    public int clientActionAge;
    public int clientDomeHitAge = -1000;
    public Vec3d clientDomeHit = Vec3d.ZERO;
    public int clientSoulAge = -1000;

    // --- состояние боя (сервер) ---
    private boolean started;
    private int action = NONE;
    private int actionTick;
    private int rest = 40;
    private Vec3d arena;
    private Vec3d center;
    private double orbit;
    private int players = 1;
    private final List<UUID> bells = new ArrayList<>();
    private boolean needBells = true;
    private boolean bellsPlaced;
    private int cycleTicks;
    private boolean gatherDone;
    private int groundTimer;
    private boolean captiveDone;
    private int requiemStacks;
    private float requiemHit;
    private int crack;
    private boolean reliqDone;
    /** Улан выпущен и жив — он держит купол. */
    private boolean lancerGuard;
    private UUID lancerId;
    private int bellsCd;
    // полёт
    private int flyMode = FLY_ORBIT;
    private int flyModeT;
    private double orbitR = 11.0;
    private double orbitDir = 1.0;
    private double f8Rot;
    private Vec3d flyGoal = Vec3d.ZERO;
    private UUID focusId;
    private int focusT;
    private int maulCd;
    private int censerCd = 300;
    private int approachCd = 260;
    private int quakeCd = 900;
    private int phantomsCd = 600;
    private int eggsCd = 900;
    private int tollCd = 120;
    private int rainCd = 220;
    private int markCd = 360;
    private int domeHitCd;
    private boolean forceCrash;
    // действия: запомненные точки
    private Vec3d actPoint = Vec3d.ZERO;
    private Vec3d actDir = Vec3d.ZERO;
    private NabatQuakeEntity quake;
    private final Set<Integer> hitIds = new HashSet<>();

    public NabatEntity(EntityType<? extends HostileEntity> type, World world) {
        super(type, world);
        this.experiencePoints = 250;
        this.setPersistent();
        this.setNoGravity(true);
        this.addCommandTag(NabatHooks.NO_SOUL);
    }

    public static DefaultAttributeContainer.Builder createAttributes() {
        return HostileEntity.createHostileAttributes()
                .add(EntityAttributes.GENERIC_MAX_HEALTH, BASE_HP)
                .add(EntityAttributes.GENERIC_MOVEMENT_SPEED, 0.25)
                .add(EntityAttributes.GENERIC_FOLLOW_RANGE, 64.0)
                .add(EntityAttributes.GENERIC_ATTACK_DAMAGE, 6.0)
                .add(EntityAttributes.GENERIC_ARMOR, 6.0)
                .add(EntityAttributes.GENERIC_KNOCKBACK_RESISTANCE, 1.0);
    }

    @Override
    protected void initDataTracker() {
        super.initDataTracker();
        this.dataTracker.startTracking(GROUNDED, false);
        this.dataTracker.startTracking(PHASE3, false);
        this.dataTracker.startTracking(RELIQ_GONE, false);
    }

    public boolean isGrounded() {
        return this.dataTracker.get(GROUNDED);
    }

    public boolean isPhase3() {
        return this.dataTracker.get(PHASE3);
    }

    public boolean isReliquaryGone() {
        return this.dataTracker.get(RELIQ_GONE);
    }

    @Override
    protected void initGoals() {
        // весь бой ведёт mobTick (полёт, расписание атак)
    }

    // =====================================================================
    // Полоска босса
    // =====================================================================
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

    // =====================================================================
    // Клиент
    // =====================================================================
    @Override
    public void handleStatus(byte status) {
        int a = status - STATUS_BASE;
        if (a == DOME_HIT) {
            this.clientDomeHitAge = this.age;
            this.clientDomeHit = new Vec3d((this.random.nextDouble() - 0.5) * 2.2, 1.5 + this.random.nextDouble() * 4.5,
                    (this.random.nextDouble() - 0.5) * 2.2);
        } else if (a == SOUL_IN) {
            this.clientSoulAge = this.age;
        } else if (a >= INTRO && a <= RISE) {
            this.clientAction = a;
            this.clientActionAge = this.age;
        } else {
            super.handleStatus(status);
        }
    }

    @Override
    public Box getVisibilityBoundingBox() {
        return this.getBoundingBox().expand(6.0, 3.0, 6.0);
    }

    @Override
    public boolean shouldRender(double distance) {
        return distance < 160 * 160;
    }

    // =====================================================================
    // Помощники
    // =====================================================================
    ServerWorld sw() {
        return this.getWorld() instanceof ServerWorld s ? s : null;
    }

    void sound(SoundEvent s, float vol, float pitch) {
        this.getWorld().playSound(null, this.getX(), this.getY(), this.getZ(), s, SoundCategory.HOSTILE, vol, pitch);
    }

    void soundAt(Vec3d p, SoundEvent s, float vol, float pitch) {
        this.getWorld().playSound(null, p.x, p.y, p.z, s, SoundCategory.HOSTILE, vol, pitch);
    }

    void sendAction(int a) {
        this.getWorld().sendEntityStatus(this, (byte) (STATUS_BASE + a));
    }

    Vec3d forward() {
        float yaw = this.bodyYaw * 0.017453292f;
        return new Vec3d(-MathHelper.sin(yaw), 0, MathHelper.cos(yaw));
    }

    double groundY(double x, double z) {
        return this.getWorld().getTopY(Heightmap.Type.MOTION_BLOCKING_NO_LEAVES, MathHelper.floor(x), MathHelper.floor(z));
    }

    void faceTo(double x, double z, float maxDeg) {
        float want = (float) (MathHelper.atan2(z - this.getZ(), x - this.getX()) * 57.29577951308232) - 90.0f;
        float yaw = this.getYaw() + MathHelper.clamp(MathHelper.wrapDegrees(want - this.getYaw()), -maxDeg, maxDeg);
        this.setYaw(yaw);
        this.setBodyYaw(yaw);
        this.setHeadYaw(yaw);
    }

    /** Игроки боя (живые, не в творческом) в 48 блоках. */
    List<PlayerEntity> fighters() {
        return this.getWorld().getEntitiesByClass(PlayerEntity.class, this.getBoundingBox().expand(48.0, 40.0, 48.0),
                p -> p.isAlive() && !p.isCreative() && !p.isSpectator());
    }

    PlayerEntity loudest() {
        ServerWorld s = this.sw();
        return s == null ? null : NabatNoise.loudest(s, this.getPos(), 48.0);
    }

    /** Кого бьют удары Набата: не монстры и не его свои. */
    List<LivingEntity> victimsNear(Vec3d c, double r, double h) {
        return this.getWorld().getEntitiesByClass(LivingEntity.class, new Box(c.x - r - 1, c.y - h, c.z - r - 1,
                c.x + r + 1, c.y + h, c.z + r + 1), e -> e.isAlive() && e != this && !(e instanceof Monster)
                && !(e instanceof ArmorStandEntity) && !NabatHooks.isKin(e)
                && !(e instanceof PlayerEntity p && (p.isCreative() || p.isSpectator())));
    }

    int radiusHit(Vec3d c, double r, double h, float dmg, double knock, double up) {
        int n = 0;
        for (LivingEntity e : this.victimsNear(c, r, h)) {
            double dx = e.getX() - c.x;
            double dz = e.getZ() - c.z;
            double d = Math.sqrt(dx * dx + dz * dz) - e.getWidth() / 2;
            if (d <= r && this.hitIds.add(e.getId())) {
                e.timeUntilRegen = 0;
                if (e.damage(this.getDamageSources().mobAttack(this), dmg)) {
                    n++;
                    if (knock > 0) {
                        e.takeKnockback(knock, c.x - e.getX(), c.z - e.getZ());
                    }
                    if (up > 0) {
                        e.addVelocity(0, up, 0);
                    }
                    e.velocityModified = true;
                }
            }
        }
        return n;
    }

    /** Кольцо пыли и янтарных искр на земле. */
    void groundRing(Vec3d c, double r, int pts, boolean amber) {
        ServerWorld s = this.sw();
        if (s == null) {
            return;
        }
        BlockState ground = s.getBlockState(BlockPos.ofFloored(c.x, c.y - 0.5, c.z));
        for (int i = 0; i < pts; i++) {
            double a = i / (double) pts * Math.PI * 2;
            double x = c.x + Math.cos(a) * r;
            double z = c.z + Math.sin(a) * r;
            if (!ground.isAir()) {
                s.spawnParticles(new BlockStateParticleEffect(ParticleTypes.BLOCK, ground), x, c.y + 0.1, z, 2, 0.15, 0.05,
                        0.15, 0.12);
            }
            if (i % 2 == 0) {
                s.spawnParticles(ParticleTypes.CLOUD, x, c.y + 0.15, z, 1, 0, 0.03, 0, 0.03);
            }
            if (amber && i % 3 == 0) {
                s.spawnParticles(AMBER_S, x, c.y + 0.3, z, 1, 0, 0.05, 0, 0.0);
            }
        }
    }

    Vec3d groundBelow() {
        return new Vec3d(this.getX(), this.groundY(this.getX(), this.getZ()), this.getZ());
    }

    // =====================================================================
    // Тик
    // =====================================================================
    @Override
    public void tick() {
        super.tick();
        if (this.getWorld().isClient) {
            this.clientFx();
        }
    }

    private void clientFx() {
        World w = this.getWorld();
        if (!this.isGrounded() && this.deathTime == 0) {
            double vx = this.getX() - this.prevX;
            double vz = this.getZ() - this.prevZ;
            double sp = Math.sqrt(vx * vx + vz * vz);
            // быстрый полёт: тонкий янтарный след с кончиков крыльев
            if (sp > 0.24 && this.age % 2 == 0) {
                double nx = vx / sp;
                double nz = vz / sp;
                for (int side = -1; side <= 1; side += 2) {
                    double x = this.getX() - nz * 5.2 * side - nx * 1.5;
                    double z = this.getZ() + nx * 5.2 * side - nz * 1.5;
                    w.addParticle(TRAIL, x, this.getY() + 4.6, z, 0, 0, 0);
                }
            }
            // из кадил сыплются редкие искры
            if (this.age % 7 == 0) {
                w.addParticle(AMBER_S, this.getX() + (this.random.nextDouble() - 0.5) * 3.0, this.getY() + 1.2,
                        this.getZ() + (this.random.nextDouble() - 0.5) * 3.0, 0, -0.04, 0);
            }
        }
        // язык‑сердце тлеет; у треснувшего колокола сыплются искры
        if (this.isPhase3() && this.random.nextInt(4) == 0) {
            w.addParticle(AMBER_S, this.getX() + (this.random.nextDouble() - 0.5) * 2.4, this.getY() + 2.6 + this.random.nextDouble() * 1.4,
                    this.getZ() + (this.random.nextDouble() - 0.5) * 2.4, 0, -0.02, 0);
        }
    }

    @Override
    protected void mobTick() {
        super.mobTick();
        ServerWorld s = this.sw();
        if (s == null) {
            return;
        }
        this.bossBar.setPercent(this.getHealth() / this.getMaxHealth());
        BossEffectsSync.tick(this, this.bossBar);
        if (!this.started) {
            this.startFight();
        }
        this.tickCooldowns();
        if (this.age % 20 == 0) {
            this.players = Math.max(1, this.fighters().size());
        }
        if (this.age % 10 == 0) {
            this.syncEffects();
        }
        this.tickLancer(s);
        if ((this.age + 7) % 40 == 0) {
            this.balanceTargets(s);
        }
        if (this.isGrounded() && this.action != CRASH && this.groundTimer > 0) {
            this.groundTimer--;
        }
        if (this.action != NONE) {
            this.actionTick++;
            this.runAction(s);
            if (this.action != NONE && this.actionTick >= LEN[this.action]) {
                this.finishAction();
            }
        } else if (this.isGrounded()) {
            this.groundLogic();
        } else {
            this.skyLogic();
        }
        if (!this.isGrounded() && this.isSkyMoving()) {
            this.flyTick();
        }
        if (!this.isGrounded() && this.action == NONE && this.age % 90 == 0) {
            this.sound(ModSounds.NABAT_WING, 1.1f, 0.9f + this.random.nextFloat() * 0.15f);
        }
    }

    private void startFight() {
        this.started = true;
        this.arena = this.getPos();
        this.center = this.arena;
        this.players = Math.max(1, this.fighters().size());
        double k = Math.min(2.5, 1.0 + 0.5 * (this.players - 1));
        this.getAttributeInstance(EntityAttributes.GENERIC_MAX_HEALTH).setBaseValue(BASE_HP * k);
        this.setHealth(this.getMaxHealth());
        // появляется высоко и спускается под звон
        this.setPosition(this.getX(), this.groundY(this.getX(), this.getZ()) + this.hoverH() + 22, this.getZ());
        this.begin(INTRO);
    }

    private void tickCooldowns() {
        if (this.rest > 0) {
            this.rest--;
        }
        if (this.action == NONE && !this.isGrounded()) {
            this.censerCd--;
            this.approachCd--;
            this.phantomsCd--;
            this.eggsCd--;
            this.tollCd--;
            this.rainCd--;
            this.markCd--;
            this.bellsCd--;
            this.cycleTicks++;
        }
        this.quakeCd--;
        if (this.maulCd > 0) {
            this.maulCd--;
        }
        if (this.domeHitCd > 0) {
            this.domeHitCd--;
        }
    }

    double hoverH() {
        return this.lancerGuard ? HOVER_LANCER : (this.isPhase3() ? HOVER_H3 : HOVER_H);
    }

    /** В полёте сейчас его ведёт обычное парение (а не действие, которое само задаёт движение). */
    private boolean isSkyMoving() {
        return this.action == NONE || this.action == TOLL || this.action == CENSER || this.action == GATHER
                || this.action == BELLS || this.action == RAIN || this.action == MARK || this.action == EGGS
                || this.action == PHANTOMS || this.action == RELIQ || this.action == CRACK;
    }

    /** Игрок, за которым сейчас следит Набат (меняется раз в 8–13 с: то самый шумный, то другой). */
    PlayerEntity focusPlayer() {
        PlayerEntity cur = this.focusId != null ? this.getWorld().getPlayerByUuid(this.focusId) : null;
        boolean ok = cur != null && cur.isAlive() && !cur.isCreative() && !cur.isSpectator() && cur.squaredDistanceTo(this) < 56 * 56;
        if (ok && this.focusT > 0) {
            return cur;
        }
        List<PlayerEntity> ps = this.fighters();
        if (ps.isEmpty()) {
            this.focusId = null;
            return null;
        }
        PlayerEntity next = this.loudest();
        if (ps.size() > 1 && (next == null || next == cur || this.random.nextFloat() < 0.5f)) {
            ps.remove(cur);
            next = ps.get(this.random.nextInt(ps.size()));
        }
        if (next == null) {
            next = ps.get(0);
        }
        this.focusId = next.getUuid();
        this.focusT = 160 + this.random.nextInt(100);
        return next;
    }

    /** Центр игроков боя (для манёвров), внутри арены. */
    private Vec3d playersCenter() {
        List<PlayerEntity> ps = this.fighters();
        if (ps.isEmpty()) {
            return this.arena;
        }
        double x = 0;
        double z = 0;
        for (PlayerEntity p : ps) {
            x += p.getX();
            z += p.getZ();
        }
        return new Vec3d(x / ps.size(), this.arena.y, z / ps.size());
    }

    private Vec3d clampArena(Vec3d p) {
        Vec3d off = p.subtract(this.arena);
        double ol = Math.sqrt(off.x * off.x + off.z * off.z);
        return ol > ARENA_R ? new Vec3d(this.arena.x + off.x / ol * ARENA_R, p.y, this.arena.z + off.z / ol * ARENA_R) : p;
    }

    /** Новый манёвр: круг (35%), пролёт сквозь бой (30%), восьмёрка (20%), зависание над игроком (15%). */
    private void pickFlyMode(PlayerEntity focus) {
        int prev = this.flyMode;
        float r = this.random.nextFloat();
        int m = r < 0.35f ? FLY_ORBIT : r < 0.65f ? FLY_SWEEP : r < 0.85f ? FLY_EIGHT : FLY_STALK;
        if (m == prev && m != FLY_ORBIT) {
            m = FLY_ORBIT;
        }
        if (focus == null && (m == FLY_SWEEP || m == FLY_STALK)) {
            m = FLY_EIGHT;
        }
        this.flyMode = m;
        switch (m) {
            case FLY_SWEEP -> {
                Vec3d d = new Vec3d(focus.getX() - this.getX(), 0, focus.getZ() - this.getZ());
                d = d.lengthSquared() > 1 ? d.normalize() : this.forward();
                this.flyGoal = this.clampArena(focus.getPos().add(d.multiply(9 + this.random.nextDouble() * 5)));
                this.flyModeT = 140;
                this.sound(ModSounds.NABAT_WING, 1.5f, 0.8f);
            }
            case FLY_STALK -> this.flyModeT = 70 + this.random.nextInt(50);
            case FLY_EIGHT -> {
                this.f8Rot = this.random.nextDouble() * Math.PI;
                this.orbitDir = this.random.nextBoolean() ? 1 : -1;
                this.flyModeT = 200 + this.random.nextInt(100);
            }
            default -> {
                this.orbitR = 9.0 + this.random.nextDouble() * 7.0;
                this.orbitDir = this.random.nextBoolean() ? 1 : -1;
                this.orbit = Math.atan2(this.getZ() - this.center.z, this.getX() - this.center.x);
                this.flyModeT = 140 + this.random.nextInt(100);
            }
        }
    }

    /** Полёт по арене: манёвры сменяют друг друга; во время заклинаний он летит медленнее. */
    private void flyTick() {
        if (this.focusT > 0) {
            this.focusT--;
        }
        PlayerEntity focus = this.focusPlayer();
        this.center = this.clampArena(this.center.lerp(this.playersCenter(), 0.02));
        if (--this.flyModeT <= 0) {
            this.pickFlyMode(focus);
        }
        boolean busy = this.action != NONE;
        Vec3d goal;
        double max;
        double lower = 0;
        switch (this.flyMode) {
            case FLY_SWEEP -> {
                goal = this.flyGoal;
                max = 0.46;
                double dx = goal.x - this.getX();
                double dz = goal.z - this.getZ();
                if (dx * dx + dz * dz < 9) {
                    this.flyModeT = 0;
                }
                lower = 2.0;
            }
            case FLY_STALK -> {
                Vec3d f = focus != null ? focus.getPos() : this.center;
                Vec3d away = new Vec3d(this.getX() - f.x, 0, this.getZ() - f.z);
                away = away.lengthSquared() > 0.01 ? away.normalize() : this.forward().multiply(-1);
                goal = f.add(away.multiply(4.0));
                max = 0.22;
                lower = 3.0;
            }
            case FLY_EIGHT -> {
                this.orbit += this.orbitDir * (busy ? 0.005 : 0.010);
                double a = Math.sin(this.orbit) * 13.0;
                double b = Math.sin(2 * this.orbit) * 6.0;
                double c = Math.cos(this.f8Rot);
                double sn = Math.sin(this.f8Rot);
                goal = this.center.add(a * c - b * sn, 0, a * sn + b * c);
                max = 0.34;
            }
            default -> {
                this.orbit += this.orbitDir * (busy ? 0.006 : 0.012);
                goal = this.center.add(Math.cos(this.orbit) * this.orbitR, 0, Math.sin(this.orbit) * this.orbitR);
                max = 0.3;
            }
        }
        if (busy) {
            max *= 0.45;
            lower = 0;
        }
        goal = this.clampArena(goal);
        double ty = Math.max(this.groundY(goal.x, goal.z), this.groundY(this.getX(), this.getZ())) + this.hoverH() - lower
                + 1.5 * Math.sin(this.age * 0.025);
        Vec3d to = new Vec3d(goal.x - this.getX(), ty - this.getY(), goal.z - this.getZ());
        Vec3d v = to.multiply(0.06);
        double h = Math.sqrt(v.x * v.x + v.z * v.z);
        if (h > max) {
            v = new Vec3d(v.x / h * max, v.y, v.z / h * max);
        }
        v = new Vec3d(v.x, MathHelper.clamp(v.y, -0.25, 0.3), v.z);
        Vec3d nv = this.getVelocity().lerp(v, 0.06);
        if (this.horizontalCollision) {
            nv = nv.add(0, 0.12, 0);
        }
        this.setVelocity(nv);
        this.velocityDirty = true;
        double hs = Math.sqrt(nv.x * nv.x + nv.z * nv.z);
        if (!busy && hs > 0.17) {
            this.faceTo(this.getX() + nv.x, this.getZ() + nv.z, 6f);       // быстрый полёт — смотрит по ходу
        } else if (focus != null) {
            this.faceTo(focus.getX(), focus.getZ(), 4f);
        }
        if (focus != null) {
            this.setTarget(focus);
        }
    }

    // =====================================================================
    // Расписание (небо)
    // =====================================================================
    private void skyLogic() {
        // все колокола сломаны (или улана не стало) → падение, не дожидаясь паузы
        if (this.forceCrash || (this.bellsPlaced && !this.lancerGuard && this.aliveBells() == 0)) {
            this.forceCrash = false;
            this.bellsPlaced = false;
            this.begin(CRASH);
            return;
        }
        if (this.rest > 0) {
            return;
        }
        float hp = this.getHealth() / this.getMaxHealth();
        if (!this.reliqDone && hp <= 0.6f) {
            this.begin(RELIQ);
            return;
        }
        if (!this.isPhase3() && hp <= 0.3f && !this.lancerGuard) {
            this.begin(CRACK);
            return;
        }
        // свои атаки — в первую очередь
        PlayerEntity focus = this.focusPlayer();
        if (this.approachCd <= 0 && focus != null) {
            if (this.quakeCd <= 0 && this.age > 900) {
                this.begin(QUAKE);
            } else {
                this.begin(this.random.nextBoolean() ? DIVE : REAP);
            }
            return;
        }
        if (!this.lancerGuard) {
            if (this.needBells && this.bellsCd <= 0) {
                this.begin(BELLS);
                return;
            }
            if ((this.cycleTicks >= 1100 && !this.gatherDone) || this.cycleTicks >= 2600) {
                this.gatherDone = true;
                if (this.cycleTicks >= 2600) {
                    this.cycleTicks = 1200;
                }
                this.begin(GATHER);
                return;
            }
        }
        if (this.tollCd <= 0) {
            this.begin(TOLL);
        } else if (this.rainCd <= 0) {
            this.begin(RAIN);
        } else if (this.markCd <= 0) {
            this.begin(MARK);
        } else if (this.lancerGuard) {
            return;
        } else if (this.censerCd <= 0) {
            this.begin(CENSER);
        } else if (this.phantomsCd <= 0) {
            this.begin(PHANTOMS);
        } else if (this.eggsCd <= 0) {
            this.begin(EGGS);
        }
    }

    /** Множитель перезарядок: 3‑я фаза и трещины — чаще. */
    private double cdK() {
        return (this.isPhase3() ? 0.8 : 1.0) * (1.0 - 0.1 * this.crack);
    }

    private int cd(int base) {
        return (int) (base * this.cdK() * (this.lancerGuard ? 1.2 : 1.0));
    }

    void begin(int a) {
        this.action = a;
        this.actionTick = 0;
        this.hitIds.clear();
        this.sendAction(a);
        switch (a) {
            case TOLL -> this.tollCd = this.cd(220);
            case CENSER -> this.censerCd = this.cd(this.isPhase3() ? 560 : 680);
            case RAIN -> this.rainCd = this.cd(340);
            case MARK -> this.markCd = this.cd(520);
            case EGGS -> this.eggsCd = this.cd(1500);
            case PHANTOMS -> this.phantomsCd = this.cd(1100);
            case DIVE, REAP -> this.approachCd = this.cd(this.isPhase3() ? 340 : 440);
            case QUAKE -> {
                this.approachCd = this.cd(this.isPhase3() ? 340 : 440);
                this.quakeCd = this.cd(this.isPhase3() ? 900 : 1300);
            }
            default -> {
            }
        }
    }

    private void finishAction() {
        int done = this.action;
        this.action = NONE;
        this.rest = (int) ((24 + this.random.nextInt(20)) * this.cdK());
        if (done == DIVE || done == REAP || done == QUAKE) {
            this.focusT = 0;                 // после своей атаки — следующий игрок
        }
        switch (done) {
            case INTRO -> {
                this.needBells = true;
                this.bellsCd = 160;
                this.rest = 20;
                this.begin(GATHER);
                this.gatherDone = false;
            }
            case CRASH -> this.enterGround();
            case RISE -> this.leaveGround();
            case REQUIEM -> this.finishRequiem();
            default -> {
            }
        }
    }

    // =====================================================================
    // Действия
    // =====================================================================
    private void runAction(ServerWorld s) {
        int t = this.actionTick;
        switch (this.action) {
            case INTRO -> this.runIntro(t);
            case TOLL -> {
                if (t == TOLL_HIT) {
                    this.toll(1.0f);
                }
            }
            case GATHER -> {
                for (int k = 0; k < GATHER_HITS.length; k++) {
                    if (t == GATHER_HITS[k]) {
                        this.sound(ModSounds.NABAT_GATHER, 4.0f, 0.9f + k * 0.06f);
                        this.groundRing(this.groundBelow(), 4 + k * 2, 30, true);
                    }
                }
                if (t == GATHER_HITS[2]) {
                    this.summonGather(s);
                }
            }
            case CENSER -> {
                if (t == 8) {
                    this.sound(ModSounds.NABAT_SUMMON, 3.0f, 1.0f);
                }
                if (t == CENSER_AT) {
                    this.summonCenser(s);
                }
            }
            case BELLS -> {
                if (t == BELLS_AT) {
                    this.dropBells(s);
                }
            }
            case RAIN -> {
                if (t == RAIN_AT) {
                    this.bellRain(s);
                }
            }
            case MARK -> {
                if (t == MARK_AT) {
                    this.marks(s);
                }
            }
            case EGGS -> {
                if (t == EGGS_AT) {
                    this.dropEggs(s);
                }
            }
            case PHANTOMS -> {
                if (t == PHANTOMS_AT) {
                    this.summonPhantoms(s, 2 + (this.players - 1));
                }
            }
            case RELIQ -> this.runReliquary(s, t);
            case CRACK -> {
                if (t == CRACK_AT) {
                    this.dataTracker.set(PHASE3, true);
                    this.sound(ModSounds.NABAT_CRACK, 4.0f, 0.9f);
                    s.spawnParticles(AMBER, this.getX(), this.getY() + 3.0, this.getZ(), 60, 1.2, 1.6, 1.2, 0.2);
                    s.spawnParticles(ParticleTypes.END_ROD, this.getX(), this.getY() + 3.0, this.getZ(), 20, 1.0, 1.4, 1.0, 0.1);
                    this.needBells = true;
                    this.bellsPlaced = false;
                    this.gatherDone = false;
                    this.cycleTicks = 1000;
                    this.bellsCd = 300;
                }
            }
            case DIVE -> this.runDive(s, t);
            case REAP -> this.runReap(s, t);
            case QUAKE -> this.runQuake(s, t);
            case CRASH -> this.runCrash(s, t);
            case REQUIEM -> this.runRequiem(s, t);
            case CAPTIVE -> {
                if (t == CAPTIVE_AT) {
                    this.captive(s);
                }
            }
            case MAUL -> this.runMaul(s, t);
            case RISE -> this.runRise(s, t);
            default -> {
            }
        }
    }

    private void runIntro(int t) {
        // спуск с высоты на высоту парения за 3.5 с, неуязвим
        double ty = this.groundY(this.getX(), this.getZ()) + this.hoverH();
        double k = MathHelper.clamp((70 - t) / 70.0, 0.0, 1.0);
        double vy = (ty - this.getY()) * (t < 70 ? 0.06 : 0.1);
        this.setVelocity(0, vy, 0);
        this.velocityDirty = true;
        if (t == 2 || t == 30) {
            this.sound(ModSounds.NABAT_INTRO, 5.0f, t == 2 ? 0.8f : 1.0f);
        }
        if (t == 49) {
            this.sound(ModSounds.NABAT_WING, 3.0f, 0.7f);
            ServerWorld s = this.sw();
            if (s != null) {
                s.spawnParticles(AMBER, this.getX(), this.getY() + 4, this.getZ(), 80, 3.0, 1.5, 3.0, 0.08);
            }
        }
        PlayerEntity p = this.focusPlayer();
        if (p != null) {
            this.faceTo(p.getX(), p.getZ(), 3f);
        }
    }

    /** «Звон»: волна в точке под ним. */
    private void toll(float k) {
        Vec3d c = this.groundBelow();
        this.sound(ModSounds.NABAT_TOLL, 4.5f, 1.0f);
        ServerWorld s = this.sw();
        if (s != null) {
            s.spawnParticles(ParticleTypes.SONIC_BOOM, c.x, c.y + 0.6, c.z, 1, 0, 0, 0, 0);
        }
        this.hitIds.clear();
        this.radiusHit(c.add(0, 1, 0), TOLL_R, 3.0, TOLL_DMG * k, 1.0, 0.25);
        for (double r = 1.5; r <= TOLL_R; r += 1.8) {
            this.groundRing(c, r, (int) (r * 5), true);
        }
        // яйца вылупляются быстрее
        for (BloodEggEntity egg : this.getWorld().getEntitiesByClass(BloodEggEntity.class, this.getBoundingBox().expand(48),
                e -> e.isAlive() && e.getCommandTags().contains(NabatHooks.TAG))) {
            egg.addLife(200);
        }
    }

    private void runDive(ServerWorld s, int t) {
        if (t < DIVE_FALL) {
            this.setVelocity(this.getVelocity().multiply(0.8).add(0, 0.03, 0));
            this.velocityDirty = true;
            PlayerEntity p = this.focusPlayer();
            if (p != null) {
                this.faceTo(p.getX(), p.getZ(), 8f);
                this.actPoint = new Vec3d(p.getX() + p.getVelocity().x * 6, 0, p.getZ() + p.getVelocity().z * 6);
            } else {
                this.actPoint = this.groundBelow();
            }
            if (t == 4) {
                this.sound(ModSounds.NABAT_WING, 2.5f, 0.75f);
            }
            // метка удара на земле — видно, куда он рухнет
            if (t % 5 == 2) {
                Vec3d g = new Vec3d(this.actPoint.x, this.groundY(this.actPoint.x, this.actPoint.z), this.actPoint.z);
                for (int i = 0; i < 18; i++) {
                    double a = i / 18.0 * Math.PI * 2;
                    s.spawnParticles(AMBER_S, g.x + Math.cos(a) * DIVE_R, g.y + 0.15, g.z + Math.sin(a) * DIVE_R, 1, 0, 0, 0, 0);
                }
            }
            return;
        }
        if (t < DIVE_HIT) {
            if (t == DIVE_FALL) {
                double gy = this.groundY(this.actPoint.x, this.actPoint.z);
                this.actPoint = new Vec3d(this.actPoint.x, gy, this.actPoint.z);
                this.sound(ModSounds.NABAT_SWING, 3.0f, 0.6f);
            }
            int left = DIVE_HIT - t;
            Vec3d to = this.actPoint.subtract(this.getPos());
            this.setVelocity(to.multiply(1.0 / left));
            this.velocityDirty = true;
            return;
        }
        if (t == DIVE_HIT) {
            this.setVelocity(Vec3d.ZERO);
            Vec3d c = this.getPos();
            this.hitIds.clear();
            this.radiusHit(c.add(0, 1, 0), DIVE_R, 3.0, DIVE_DMG, 1.4, 0.5);
            this.sound(ModSounds.NABAT_CRASH, 4.0f, 1.1f);
            s.spawnParticles(ParticleTypes.EXPLOSION_EMITTER, c.x, c.y + 0.5, c.z, 1, 0, 0, 0, 0);
            for (double r = 1.0; r <= DIVE_R + 1; r += 1.2) {
                this.groundRing(c, r, (int) (r * 6), true);
            }
            return;
        }
        if (t < DIVE_UP) {
            this.setVelocity(0, 0, 0);
            this.velocityDirty = true;
            return;
        }
        double ty = this.groundY(this.getX(), this.getZ()) + this.hoverH();
        this.setVelocity(0, MathHelper.clamp((ty - this.getY()) * 0.12, -0.2, 0.45), 0);
        this.velocityDirty = true;
        if (t == DIVE_UP) {
            this.sound(ModSounds.NABAT_WING, 2.5f, 0.85f);
        }
    }

    private void runReap(ServerWorld s, int t) {
        PlayerEntity p = this.focusPlayer();
        if (t == 0) {
            Vec3d tp = p != null ? p.getPos() : this.groundBelow();
            Vec3d d = new Vec3d(tp.x - this.getX(), 0, tp.z - this.getZ());
            this.actDir = d.lengthSquared() > 0.01 ? d.normalize() : this.forward();
            // старт — за 9 блоков до цели, на высоте 3 блока
            this.actPoint = tp.subtract(this.actDir.multiply(9.0));
            this.actPoint = new Vec3d(this.actPoint.x, this.groundY(this.actPoint.x, this.actPoint.z) + 3.0, this.actPoint.z);
            this.sound(ModSounds.NABAT_WING, 2.5f, 0.8f);
        }
        if (t < REAP_IN) {
            int left = REAP_IN - t;
            this.setVelocity(this.actPoint.subtract(this.getPos()).multiply(1.0 / left));
            this.velocityDirty = true;
            this.faceTo(this.getX() + this.actDir.x, this.getZ() + this.actDir.z, 10f);
            return;
        }
        if (t < REAP_OUT) {
            if (t == REAP_IN) {
                this.sound(ModSounds.NABAT_SWING, 3.5f, 0.7f);
            }
            double speed = 18.0 / (REAP_OUT - REAP_IN);
            double gy = this.groundY(this.getX() + this.actDir.x, this.getZ() + this.actDir.z) + 3.0;
            this.setVelocity(this.actDir.x * speed, (gy - this.getY()) * 0.3, this.actDir.z * speed);
            this.velocityDirty = true;
            // полоса ±2 по ходу крыльев
            for (LivingEntity e : this.victimsNear(this.getPos(), 4.0, 4.5)) {
                Vec3d rel = e.getPos().subtract(this.getPos());
                double side = Math.abs(rel.x * this.actDir.z - rel.z * this.actDir.x);
                double along = rel.x * this.actDir.x + rel.z * this.actDir.z;
                if (side < 2.0 + e.getWidth() / 2 && along > -1.5 && along < 2.0 && this.hitIds.add(e.getId())) {
                    e.timeUntilRegen = 0;
                    if (e.damage(this.getDamageSources().mobAttack(this), REAP_DMG)) {
                        e.takeKnockback(0.9, -this.actDir.x, -this.actDir.z);
                        s.spawnParticles(ParticleTypes.SWEEP_ATTACK, e.getX(), e.getBodyY(0.6), e.getZ(), 1, 0, 0, 0, 0);
                    }
                }
            }
            if (t % 4 == 0) {
                s.spawnParticles(AMBER_S, this.getX(), this.getY() + 3, this.getZ(), 6, 2.5, 0.6, 2.5, 0.01);
            }
            return;
        }
        double ty = this.groundY(this.getX(), this.getZ()) + this.hoverH();
        this.setVelocity(this.getVelocity().multiply(0.9, 0, 0.9).add(0, MathHelper.clamp((ty - this.getY()) * 0.1, -0.1, 0.4), 0));
        this.velocityDirty = true;
    }

    private void runQuake(ServerWorld s, int t) {
        if (t < QUAKE_LAND) {
            double gy = this.groundY(this.getX(), this.getZ());
            this.setVelocity(this.getVelocity().multiply(0.6, 0, 0.6).add(0, (gy - this.getY()) / Math.max(1, QUAKE_LAND - t), 0));
            this.velocityDirty = true;
            if (t == 2) {
                this.sound(ModSounds.NABAT_WING, 2.5f, 0.7f);
            }
            return;
        }
        if (t == QUAKE_LAND) {
            this.setVelocity(Vec3d.ZERO);
            this.sound(ModSounds.NABAT_SLAM, 3.0f, 1.2f);
            this.groundRing(this.getPos(), 2.5, 18, false);
            NabatQuakeEntity q = ModEntities.NABAT_QUAKE.create(s);
            if (q != null) {
                q.refreshPositionAndAngles(this.getX(), this.getY(), this.getZ(), 0f, 0f);
                q.setOwner(this);
                s.spawnEntity(q);
                this.quake = q;
            }
            this.sound(ModSounds.NABAT_QUAKE, 4.0f, 1.0f);
        }
        if (t < QUAKE_UP) {
            this.setVelocity(0, Math.min(0, this.getVelocity().y), 0);
            this.velocityDirty = true;
            for (int k = 0; k < QUAKE_HITS.length; k++) {
                if (t == QUAKE_HITS[k]) {
                    this.sound(ModSounds.NABAT_SLAM, 4.0f, 0.9f - k * 0.08f);
                    s.spawnParticles(ParticleTypes.EXPLOSION, this.getX(), this.getY() + 0.3, this.getZ(), 2, 1.0, 0.1, 1.0, 0);
                    if (this.quake != null && this.quake.isAlive()) {
                        this.quake.slam(k);
                    }
                }
            }
            return;
        }
        double ty = this.groundY(this.getX(), this.getZ()) + this.hoverH();
        this.setVelocity(0, MathHelper.clamp((ty - this.getY()) * 0.12, 0.0, 0.5), 0);
        this.velocityDirty = true;
        if (t == QUAKE_UP) {
            this.sound(ModSounds.NABAT_WING, 3.0f, 0.75f);
            this.quake = null;
        }
    }

    private void runCrash(ServerWorld s, int t) {
        if (t == 1) {
            this.sound(ModSounds.NABAT_REQUIEM_BREAK, 3.5f, 0.8f);
            s.spawnParticles(AMBER, this.getX(), this.getY() + 4, this.getZ(), 40, 1.5, 1.5, 1.5, 0.1);
        }
        if (t < CRASH_FALL) {
            this.setVelocity(this.getVelocity().multiply(0.7).add(0, 0.02, 0));
            this.velocityDirty = true;
            return;
        }
        if (t < CRASH_HIT) {
            double gy = this.groundY(this.getX(), this.getZ());
            this.setVelocity(0, (gy - this.getY()) / Math.max(1, CRASH_HIT - t), 0);
            this.velocityDirty = true;
            return;
        }
        if (t == CRASH_HIT) {
            this.setVelocity(Vec3d.ZERO);
            this.setNoGravity(false);
            this.dataTracker.set(GROUNDED, true);
            this.hitIds.clear();
            this.radiusHit(this.getPos().add(0, 1, 0), 3.0, 3.0, CRASH_DMG, 1.2, 0.3);
            this.sound(ModSounds.NABAT_CRASH, 5.0f, 0.85f);
            s.spawnParticles(ParticleTypes.EXPLOSION_EMITTER, this.getX(), this.getY() + 0.5, this.getZ(), 1, 0, 0, 0, 0);
            for (double r = 1.0; r <= 5.0; r += 1.0) {
                this.groundRing(this.getPos(), r, (int) (r * 7), r < 3);
            }
        }
    }

    /** Упал: 14 с на земле (с освобождённым уланом — 12 с без Реквиема). */
    private void enterGround() {
        this.dataTracker.set(GROUNDED, true);
        this.setNoGravity(false);
        this.groundTimer = GROUND_TICKS;
        this.captiveDone = false;
        this.maulCd = 100;
        this.rest = 0;
        if (this.requiemStacks > 0) {
            this.requiemHit = 0;
            this.begin(REQUIEM);
        }
    }

    private void groundLogic() {
        this.setVelocity(0, this.getVelocity().y, 0);
        if (this.focusT > 0) {
            this.focusT--;
        }
        PlayerEntity p = this.focusPlayer();
        if (p != null) {
            this.faceTo(p.getX(), p.getZ(), 3f);
            this.setTarget(p);
        }
        if (this.groundTimer <= 0) {
            this.begin(RISE);
            return;
        }
        if (this.rest > 0) {
            return;
        }
        int elapsed = GROUND_TICKS - this.groundTimer;
        if (!this.captiveDone && elapsed > 70 && p != null && p.squaredDistanceTo(this) < 14 * 14) {
            this.captiveDone = true;
            this.begin(CAPTIVE);
            return;
        }
        if (this.maulCd <= 0) {
            List<LivingEntity> near = this.victimsNear(this.getPos(), 6.0, 3.0);
            if (!near.isEmpty()) {
                LivingEntity e = near.get(0);
                this.faceTo(e.getX(), e.getZ(), 60f);
                this.maulCd = (int) (60 * this.cdK());
                this.begin(MAUL);
            }
        }
    }

    private void runRequiem(ServerWorld s, int t) {
        if (t == 1) {
            this.sound(ModSounds.NABAT_REQUIEM, 4.0f, 1.0f);
        }
        if (t % 16 == 2) {
            s.spawnParticles(AMBER, this.getX(), this.getY() + 4.5, this.getZ(), 10, 1.0, 0.6, 1.0, 0.02);
        }
        if (this.requiemHit >= REQUIEM_BREAK_DAMAGE) {
            // сорвали звон: стаки сгорают, Набат оглушён ещё на 4 с
            this.requiemStacks = 0;
            this.requiemHit = 0;
            this.groundTimer += STAGGER_BONUS;
            this.sound(ModSounds.NABAT_REQUIEM_BREAK, 4.0f, 1.0f);
            s.spawnParticles(ParticleTypes.END_ROD, this.getX(), this.getY() + 2, this.getZ(), 30, 1.0, 1.2, 1.0, 0.2);
            this.syncEffects();
            this.begin(REQ_BREAK);
        }
    }

    private void finishRequiem() {
        if (this.requiemStacks <= 0) {
            return;
        }
        float heal = this.getMaxHealth() * REQUIEM_HEAL_PER_STACK * this.requiemStacks;
        this.heal(heal);
        this.requiemStacks = 0;
        this.syncEffects();
        this.sound(ModSounds.NABAT_HEAL, 4.0f, 1.0f);
        ServerWorld s = this.sw();
        if (s != null) {
            s.spawnParticles(ParticleTypes.HEART, this.getX(), this.getY() + 4, this.getZ(), 8, 1.2, 1.0, 1.2, 0.1);
            s.spawnParticles(AMBER, this.getX(), this.getY() + 3.5, this.getZ(), 50, 1.4, 1.6, 1.4, 0.05);
        }
    }

    private void runMaul(ServerWorld s, int t) {
        if (t == 8) {
            this.sound(ModSounds.NABAT_SWING, 2.5f, 0.8f);
        }
        if (t == MAUL_HIT || t == MAUL_HIT + 1) {
            Vec3d f = this.forward();
            Vec3d c = this.getPos().add(f.multiply(MAUL_FWD * 0.6));
            for (LivingEntity e : this.victimsNear(this.getPos(), 5.5, 3.0)) {
                Vec3d to = new Vec3d(e.getX() - this.getX(), 0, e.getZ() - this.getZ());
                double d = to.length() - e.getWidth() / 2;
                if (d > 5.5 || (d > 1.5 && to.normalize().dotProduct(f) < Math.cos(Math.toRadians(70)))) {
                    continue;
                }
                if (this.hitIds.add(e.getId())) {
                    e.timeUntilRegen = 0;
                    if (e.damage(this.getDamageSources().mobAttack(this), MAUL_DMG)) {
                        e.takeKnockback(1.0, this.getX() - e.getX(), this.getZ() - e.getZ());
                    }
                }
            }
            if (t == MAUL_HIT) {
                Vec3d hit = this.getPos().add(f.multiply(MAUL_FWD));
                hit = new Vec3d(hit.x, this.groundY(hit.x, hit.z), hit.z);
                this.soundAt(hit, ModSounds.NABAT_TOLL, 2.5f, 1.25f);
                this.groundRing(hit, 1.2, 12, true);
                this.groundRing(hit, 2.2, 18, false);
                s.spawnParticles(ParticleTypes.CRIT, hit.x, hit.y + 0.4, hit.z, 14, 0.4, 0.2, 0.4, 0.3);
                this.groundRing(c, 0.6, 6, false);
            }
        }
    }

    private void runRise(ServerWorld s, int t) {
        if (t == RISE_LIFT) {
            this.setNoGravity(true);
            this.dataTracker.set(GROUNDED, false);
            this.hitIds.clear();
            this.radiusHit(this.getPos().add(0, 1, 0), 5.0, 3.0, RISE_DMG, 1.4, 0.4);
            this.sound(ModSounds.NABAT_WING, 3.5f, 0.7f);
            this.groundRing(this.getPos(), 3.0, 24, true);
            this.groundRing(this.getPos(), 5.0, 32, false);
        }
        if (t >= RISE_LIFT) {
            double ty = this.groundY(this.getX(), this.getZ()) + this.hoverH();
            this.setVelocity(0, MathHelper.clamp((ty - this.getY()) * 0.15, 0.05, 0.5), 0);
            this.velocityDirty = true;
        }
    }

    /** Взлетел: новая трещина, новые колокола, цикл заново. */
    private void leaveGround() {
        this.dataTracker.set(GROUNDED, false);
        this.setNoGravity(true);
        this.crack = Math.min(3, this.crack + 1);
        this.needBells = true;
        this.bellsPlaced = false;
        this.gatherDone = false;
        this.cycleTicks = 0;
        this.censerCd = 200;
        this.bellsCd = 600;                  // колокола — не сразу: ~30 с купола нет
        this.rest = 30;
        this.syncEffects();
    }

    // =====================================================================
    // Призыв
    // =====================================================================
    /** Сколько ещё слуг можно позвать (лимит живых: 10 + 6 за игрока, не больше 30). */
    private int room() {
        int cap = Math.min(30, 10 + 6 * (this.players - 1));
        int alive = this.getWorld().getEntitiesByClass(LivingEntity.class, this.getBoundingBox().expand(64.0),
                e -> e.isAlive() && e.getCommandTags().contains(NabatHooks.TAG)
                        && !e.getCommandTags().contains(NabatHooks.NO_SOUL)).size();
        return Math.max(0, cap - alive);
    }

    /** Точка на земле возле случайного игрока (5–11 блоков от него, внутри арены). */
    private Vec3d spot(ServerWorld s) {
        List<PlayerEntity> ps = this.fighters();
        Vec3d base = ps.isEmpty() ? this.center : ps.get(this.random.nextInt(ps.size())).getPos();
        for (int tries = 0; tries < 8; tries++) {
            double a = this.random.nextDouble() * Math.PI * 2;
            double r = 5.0 + this.random.nextDouble() * 6.0;
            double x = base.x + Math.cos(a) * r;
            double z = base.z + Math.sin(a) * r;
            double y = this.groundY(x, z);
            if (Math.abs(y - base.y) < 6) {
                return new Vec3d(x, y, z);
            }
        }
        return new Vec3d(base.x, this.groundY(base.x, base.z), base.z);
    }

    /** Столб дыма из кадила: от неба к точке призыва. */
    private void smokeColumn(ServerWorld s, Vec3d p) {
        for (int i = 0; i < 10; i++) {
            double y = p.y + i * 0.7;
            s.spawnParticles(ParticleTypes.LARGE_SMOKE, p.x, y, p.z, 1, 0.12, 0.2, 0.12, 0.005);
            if (i % 2 == 0) {
                s.spawnParticles(AMBER_S, p.x, y, p.z, 1, 0.2, 0.2, 0.2, 0.0);
            }
        }
        s.spawnParticles(ParticleTypes.CAMPFIRE_COSY_SMOKE, p.x, p.y + 0.2, p.z, 3, 0.3, 0.05, 0.3, 0.01);
    }

    private <T extends LivingEntity> T tagged(T e) {
        e.addCommandTag(NabatHooks.TAG);
        return e;
    }

    void spawnBlade(ServerWorld s, Vec3d p) {
        NabatBladeEntity b = ModEntities.NABAT_BLADE.create(s);
        if (b == null) {
            return;
        }
        b.refreshPositionAndAngles(p.x, p.y, p.z, this.random.nextFloat() * 360f, 0f);
        b.initialize(s, s.getLocalDifficulty(BlockPos.ofFloored(p)), SpawnReason.MOB_SUMMONED, null, null);
        b.setAssembling();
        s.spawnEntity(b);
        this.smokeColumn(s, p);
    }

    void spawnBloodling(ServerWorld s, Vec3d p) {
        BloodlingEntity b = ModEntities.BLOODLING.create(s);
        if (b == null) {
            return;
        }
        b.refreshPositionAndAngles(p.x, p.y, p.z, this.random.nextFloat() * 360f, 0f);
        b.initialize(s, s.getLocalDifficulty(BlockPos.ofFloored(p)), SpawnReason.MOB_SUMMONED, null, null);
        this.tagged(b);
        empower(b, 1.6, 1.3);
        s.spawnEntity(b);
        this.smokeColumn(s, p);
        BloodFx.splash(s, p.x, p.y + 0.5, p.z, 16, 0.4);
    }

    void summonPhantoms(ServerWorld s, int count) {
        int n = Math.min(count, this.room());
        for (int i = 0; i < n; i++) {
            PhantomEntity ph = EntityType.PHANTOM.create(s);
            if (ph == null) {
                continue;
            }
            double a = this.random.nextDouble() * Math.PI * 2;
            Vec3d p = this.getPos().add(Math.cos(a) * 4, 2 + this.random.nextDouble() * 2, Math.sin(a) * 4);
            ph.refreshPositionAndAngles(p.x, p.y, p.z, this.random.nextFloat() * 360f, 0f);
            ph.initialize(s, s.getLocalDifficulty(BlockPos.ofFloored(p)), SpawnReason.MOB_SUMMONED, null, null);
            ph.setPhantomSize(1);
            ph.setPersistent();
            ph.addStatusEffect(new StatusEffectInstance(StatusEffects.FIRE_RESISTANCE, StatusEffectInstance.INFINITE, 0,
                    false, false, false));
            this.tagged(ph);
            empower(ph, 1.8, 1.4);
            s.spawnEntity(ph);
            s.spawnParticles(AMBER_S, p.x, p.y, p.z, 8, 0.4, 0.4, 0.4, 0.02);
        }
        if (n > 0) {
            this.sound(ModSounds.NABAT_SUMMON, 3.0f, 1.3f);
            this.balanceTargets(s);
        }
    }

    private void summonCenser(ServerWorld s) {
        int blades = 2 + (this.players - 1);
        int lings = 3 + (this.players - 1);
        int room = this.room();
        for (int i = 0; i < blades && room > 0; i++, room--) {
            this.spawnBlade(s, this.spot(s));
        }
        for (int i = 0; i < lings && room > 0; i++, room--) {
            this.spawnBloodling(s, this.spot(s));
        }
        this.balanceTargets(s);
    }

    private void summonGather(ServerWorld s) {
        double k = 1.0 + 0.5 * (this.players - 1);
        int blades = (int) Math.round(4 * k);
        int lings = (int) Math.round(4 * k);
        int room = this.room();
        for (int i = 0; i < blades && room > 0; i++, room--) {
            this.spawnBlade(s, this.spot(s));
        }
        for (int i = 0; i < lings && room > 0; i++, room--) {
            this.spawnBloodling(s, this.spot(s));
        }
        this.summonPhantoms(s, (int) Math.round(3 * k));
        this.balanceTargets(s);
    }

    private void dropEggs(ServerWorld s) {
        int n = Math.min(2 + (this.players - 1), this.room());
        for (int i = 0; i < n; i++) {
            Vec3d p = this.spot(s);
            BloodEggEntity egg = ModEntities.BLOOD_EGG.create(s);
            if (egg == null) {
                continue;
            }
            egg.refreshPositionAndAngles(p.x, p.y, p.z, this.random.nextFloat() * 360f, 0f);
            this.tagged(egg);
            egg.addLife(BloodEggEntity.HATCH_TICKS - 800);              // вылупятся через 40 с
            s.spawnEntity(egg);
            BloodFx.splash(s, p.x, p.y + 0.8, p.z, 30, 0.7);
            this.smokeColumn(s, p);
        }
        this.sound(ModSounds.NABAT_SUMMON, 2.5f, 0.8f);
    }

    // --- колокола‑опоры ---
    private int aliveBells() {
        ServerWorld s = this.sw();
        if (s == null) {
            return 0;
        }
        int n = 0;
        for (UUID id : this.bells) {
            if (s.getEntity(id) instanceof NabatBellEntity b && b.isAlive()) {
                n++;
            }
        }
        return n;
    }

    private void dropBells(ServerWorld s) {
        this.needBells = false;
        this.bellsPlaced = true;
        this.bells.clear();
        int count = this.players >= 3 ? 4 : 3;
        double a0 = this.random.nextDouble() * Math.PI * 2;
        float hp = this.isPhase3() ? 40f : 60f;
        for (int i = 0; i < count; i++) {
            double a = a0 + i * Math.PI * 2 / count + (this.random.nextDouble() - 0.5) * 0.5;
            double r = 8.0 + this.random.nextDouble() * 5.0;
            double x = this.center.x + Math.cos(a) * r;
            double z = this.center.z + Math.sin(a) * r;
            double gy = this.groundY(x, z);
            NabatBellEntity b = ModEntities.NABAT_BELL.create(s);
            if (b == null) {
                continue;
            }
            b.refreshPositionAndAngles(x, gy + 16, z, this.random.nextFloat() * 360f, 0f);
            b.setOwner(this, hp);
            s.spawnEntity(b);
            this.bells.add(b.getUuid());
            if (i % 2 == 0 && this.room() > 0) {
                Vec3d g = new Vec3d(x + Math.cos(a) * 1.8, this.groundY(x + Math.cos(a) * 1.8, z + Math.sin(a) * 1.8),
                        z + Math.sin(a) * 1.8);
                this.spawnBlade(s, g);                      // «Колокольная стража»
            }
        }
        this.sound(ModSounds.NABAT_CHIME, 3.5f, 0.8f);
        this.syncEffects();
        this.balanceTargets(s);
    }

    private void bellRain(ServerWorld s) {
        for (PlayerEntity p : this.fighters()) {
            for (int i = 0; i < 2; i++) {
                double a = this.random.nextDouble() * Math.PI * 2;
                double r = i == 0 ? this.random.nextDouble() * 1.2 : 1.5 + this.random.nextDouble() * 2.0;
                double x = p.getX() + Math.cos(a) * r + p.getVelocity().x * 8;
                double z = p.getZ() + Math.sin(a) * r + p.getVelocity().z * 8;
                NabatFallingBellEntity b = ModEntities.NABAT_FALLING_BELL.create(s);
                if (b == null) {
                    continue;
                }
                b.refreshPositionAndAngles(x, this.groundY(x, z) + 14, z, this.random.nextFloat() * 360f, 0f);
                b.setup(this, NabatFallingBellEntity.RAIN, null);
                s.spawnEntity(b);
            }
        }
    }

    private void marks(ServerWorld s) {
        List<PlayerEntity> ps = this.fighters();
        PlayerEntity first = this.focusPlayer();
        List<PlayerEntity> chosen = new ArrayList<>();
        if (first != null) {
            chosen.add(first);
        }
        ps.remove(first);
        if (!ps.isEmpty()) {
            chosen.add(ps.get(this.random.nextInt(ps.size())));
        }
        for (PlayerEntity p : chosen) {
            NabatFallingBellEntity b = ModEntities.NABAT_FALLING_BELL.create(s);
            if (b == null) {
                continue;
            }
            b.refreshPositionAndAngles(p.getX(), p.getY() + p.getHeight() + 3.2, p.getZ(), 0f, 0f);
            b.setup(this, NabatFallingBellEntity.MARK, p);
            s.spawnEntity(b);
            p.addStatusEffect(new StatusEffectInstance(ModEffects.BELL_MARK, NabatFallingBellEntity.MARK_HOLD, 0, false, false, true));
        }
        this.sound(ModSounds.NABAT_MARK, 3.0f, 0.8f);
    }

    private void captive(ServerWorld s) {
        PlayerEntity p = this.focusPlayer();
        if (p == null || p.squaredDistanceTo(this) > 16 * 16) {
            return;
        }
        NabatCaptiveEntity c = ModEntities.NABAT_CAPTIVE.create(s);
        if (c == null) {
            return;
        }
        double gy = this.groundY(p.getX(), p.getZ());
        c.refreshPositionAndAngles(p.getX(), gy + 6, p.getZ(), this.random.nextFloat() * 360f, 0f);
        c.setup(this, p, gy);
        s.spawnEntity(c);
    }

    // =====================================================================
    // Реликварий и улан
    // =====================================================================
    private void runReliquary(ServerWorld s, int t) {
        if (t == 6) {
            this.sound(ModSounds.NABAT_RELIQUARY, 3.0f, 1.0f);
        }
        if (t > 10 && t < RELIQ_AT && t % 3 == 0) {
            Vec3d hand = this.getPos().add(this.forward().multiply(1.2)).add(0, 4.5, 0);
            s.spawnParticles(TEAL, hand.x, hand.y, hand.z, 3, 0.3, 0.3, 0.3, 0.02);
        }
        if (t != RELIQ_AT) {
            return;
        }
        this.reliqDone = true;
        this.dataTracker.set(RELIQ_GONE, true);
        // старые колокола рассыпаются — купол теперь держит улан
        for (UUID id : this.bells) {
            if (s.getEntity(id) instanceof NabatBellEntity b && b.isAlive()) {
                s.spawnParticles(AMBER, b.getX(), b.getY() + 0.8, b.getZ(), 16, 0.5, 0.6, 0.5, 0.05);
                b.discard();
            }
        }
        this.bells.clear();
        this.bellsPlaced = false;
        this.needBells = false;
        Vec3d g = this.groundBelow().add(this.forward().multiply(3.0));
        g = new Vec3d(g.x, this.groundY(g.x, g.z), g.z);
        TatteredLancerEntity lancer = ModEntities.TATTERED_LANCER.create(s);
        if (lancer == null) {
            return;
        }
        lancer.refreshPositionAndAngles(g.x, g.y, g.z, this.getYaw(), 0f);
        lancer.initialize(s, s.getLocalDifficulty(BlockPos.ofFloored(g)), SpawnReason.MOB_SUMMONED, null, null);
        lancer.addCommandTag(NabatHooks.TAG);
        // улан Набата крепче обычного: HP ×1.75 (×1.5 за каждого игрока сверх первого, до ×3.5), «Сопротивление I»
        empower(lancer, Math.min(3.5, 1.75 * (1.0 + 0.5 * (this.players - 1))), 1.0);
        lancer.addStatusEffect(new StatusEffectInstance(StatusEffects.RESISTANCE, StatusEffectInstance.INFINITE, 0, false, false, true));
        s.spawnEntity(lancer);
        this.lancerId = lancer.getUuid();
        this.lancerGuard = true;
        this.balanceTargets(s);
        this.soundAt(g, ModSounds.NABAT_BELL_LAND, 3.0f, 0.7f);
        s.spawnParticles(TEAL, g.x, g.y + 1, g.z, 50, 0.8, 1.2, 0.8, 0.1);
        s.spawnParticles(ParticleTypes.SOUL, g.x, g.y + 1, g.z, 24, 0.5, 1.0, 0.5, 0.05);
        this.groundRing(g, 2.0, 24, false);
        this.syncEffects();
    }

    /** Улан держит купол, пока жив; убили — душа на 8 стаков летит к Набату, а сам он падает. */
    private void tickLancer(ServerWorld s) {
        if (!this.lancerGuard) {
            return;
        }
        Entity l = this.lancerId != null ? s.getEntity(this.lancerId) : null;
        if (l == null && this.age % 20 != 0) {
            return;                          // чанк ещё грузится — проверим позже
        }
        if (!(l instanceof TatteredLancerEntity lancer) || !lancer.isAlive()) {
            this.lancerGuard = false;
            this.lancerId = null;
            this.needBells = false;
            this.bellsPlaced = false;
            this.forceCrash = true;
            this.syncEffects();
        } else if (this.age % 10 == 0) {
            // бирюзовая нить от улана к куполу — видно, кто его держит
            Vec3d a = lancer.getPos().add(0, 1.6, 0);
            Vec3d b = this.getPos().add(0, 3.0, 0);
            for (int i = 1; i < 8; i++) {
                Vec3d p = a.lerp(b, i / 8.0);
                s.spawnParticles(TEAL, p.x, p.y, p.z, 1, 0.05, 0.05, 0.05, 0);
            }
        }
    }

    /** Слуга Набата крепче обычного: множители к здоровью и урону. */
    static void empower(LivingEntity e, double hpMul, double dmgMul) {
        EntityAttributeInstance hp = e.getAttributeInstance(EntityAttributes.GENERIC_MAX_HEALTH);
        if (hp != null && hpMul != 1.0 && hp.getModifier(BUFF_HP) == null) {
            hp.addPersistentModifier(new EntityAttributeModifier(BUFF_HP, "Nabat servant health", hpMul - 1.0,
                    EntityAttributeModifier.Operation.MULTIPLY_TOTAL));
        }
        EntityAttributeInstance dmg = e.getAttributeInstance(EntityAttributes.GENERIC_ATTACK_DAMAGE);
        if (dmg != null && dmgMul != 1.0 && dmg.getModifier(BUFF_DMG) == null) {
            dmg.addPersistentModifier(new EntityAttributeModifier(BUFF_DMG, "Nabat servant damage", dmgMul - 1.0,
                    EntityAttributeModifier.Operation.MULTIPLY_TOTAL));
        }
        e.setHealth(e.getMaxHealth());
    }

    /**
     * Раскидать слуг по игрокам: у каждого игрока примерно поровну преследователей.
     * Слуги без цели (или с целью вне боя) получают самого «незанятого» игрока, перегруженные — переходят.
     */
    void balanceTargets(ServerWorld s) {
        List<PlayerEntity> ps = this.fighters();
        if (ps.isEmpty()) {
            return;
        }
        List<MobEntity> mobs = s.getEntitiesByClass(MobEntity.class, this.getBoundingBox().expand(64.0, 40.0, 64.0),
                e -> e != this && e.isAlive() && e.getCommandTags().contains(NabatHooks.TAG)
                        && !e.getCommandTags().contains(NabatHooks.NO_SOUL));
        if (mobs.isEmpty()) {
            return;
        }
        Map<PlayerEntity, Integer> cnt = new HashMap<>();
        for (PlayerEntity p : ps) {
            cnt.put(p, 0);
        }
        List<MobEntity> free = new ArrayList<>();
        for (MobEntity m : mobs) {
            LivingEntity t = m.getTarget();
            if (t instanceof PlayerEntity p && cnt.containsKey(p)) {
                cnt.merge(p, 1, Integer::sum);
            } else if (t == null || !t.isAlive() || t instanceof PlayerEntity) {
                free.add(m);
            }
        }
        int fair = (int) Math.ceil(mobs.size() / (double) ps.size());
        for (MobEntity m : mobs) {
            if (m.getTarget() instanceof PlayerEntity p && cnt.containsKey(p) && cnt.get(p) > fair) {
                PlayerEntity q = leastTargeted(cnt, m);
                if (q != p && cnt.get(q) + 1 < cnt.get(p)) {
                    m.setTarget(q);
                    cnt.merge(p, -1, Integer::sum);
                    cnt.merge(q, 1, Integer::sum);
                }
            }
        }
        for (MobEntity m : free) {
            PlayerEntity q = leastTargeted(cnt, m);
            m.setTarget(q);
            cnt.merge(q, 1, Integer::sum);
        }
    }

    private static PlayerEntity leastTargeted(Map<PlayerEntity, Integer> cnt, Entity from) {
        PlayerEntity best = null;
        double bs = Double.MAX_VALUE;
        for (Map.Entry<PlayerEntity, Integer> e : cnt.entrySet()) {
            double score = e.getValue() * 10000.0 + e.getKey().squaredDistanceTo(from);
            if (score < bs) {
                bs = score;
                best = e.getKey();
            }
        }
        return best;
    }

    // =====================================================================
    // Души
    // =====================================================================
    /** Куда летят души (центр колокола). */
    Vec3d soulTarget() {
        return this.getPos().add(0, this.isGrounded() ? 1.2 : 2.6, 0);
    }

    void absorbSoul(int stacks, Vec3d from) {
        this.requiemStacks = Math.min(MAX_REQUIEM, this.requiemStacks + stacks);
        this.syncEffects();
        this.sendAction(SOUL_IN);
        if (this.random.nextInt(3) == 0) {
            this.sound(ModSounds.NABAT_CHIME, 1.4f, 1.4f + this.random.nextFloat() * 0.2f);
        }
        ServerWorld s = this.sw();
        if (s != null) {
            s.spawnParticles(AMBER, from.x, from.y, from.z, 12, 0.3, 0.3, 0.3, 0.05);
        }
    }

    // =====================================================================
    // Эффекты под полоской
    // =====================================================================
    private void syncEffects() {
        boolean sky = !this.isGrounded() && this.action != CRASH;
        int dome = this.lancerGuard ? 1 : (sky && this.bellsPlaced ? this.aliveBells() : 0);
        this.setMarker(ModEffects.DOME, dome);
        this.setMarker(ModEffects.REQUIEM, this.requiemStacks);
        this.setMarker(ModEffects.CRACK, this.crack);
    }

    private void setMarker(StatusEffect effect, int level) {
        StatusEffectInstance cur = this.getStatusEffect(effect);
        if (level <= 0) {
            if (cur != null) {
                this.removeStatusEffect(effect);
            }
            return;
        }
        if (cur == null || cur.getAmplifier() != level - 1 || !cur.isInfinite()) {
            if (cur != null) {
                this.removeStatusEffect(effect);
            }
            this.addStatusEffect(new StatusEffectInstance(effect, StatusEffectInstance.INFINITE, level - 1, false, false, true));
        }
    }

    boolean domeUp() {
        StatusEffectInstance d = this.getStatusEffect(ModEffects.DOME);
        return d != null && !this.isGrounded() && this.action != CRASH;
    }

    // =====================================================================
    // Урон
    // =====================================================================
    /** Удар пришёлся в язык‑сердце (только на земле): луч взгляда игрока или снаряд рядом с ним. */
    private boolean heartHit(DamageSource source) {
        if (!this.isGrounded()) {
            return false;
        }
        Vec3d f = this.forward();
        Vec3d heart = this.getPos().add(f.multiply(HEART_FWD)).add(0, HEART_UP + 0.5, 0);
        Entity src = source.getSource();
        if (src instanceof ProjectileEntity) {
            return src.getPos().squaredDistanceTo(heart) < 1.6 * 1.6;
        }
        if (source.getAttacker() instanceof PlayerEntity p) {
            Vec3d eye = p.getEyePos();
            Vec3d look = p.getRotationVec(1.0f);
            Vec3d to = heart.subtract(eye);
            double along = to.dotProduct(look);
            if (along < 0 || along > 7) {
                return false;
            }
            return to.subtract(look.multiply(along)).length() < 1.0;
        }
        return false;
    }

    @Override
    public boolean damage(DamageSource source, float amount) {
        if (this.getWorld().isClient) {
            return false;
        }
        Entity att = source.getAttacker();
        if (this.action == INTRO || (att != null && NabatHooks.isKin(att))) {
            return false;
        }
        if (source.getSource() instanceof ProjectileEntity) {
            amount *= PROJECTILE_FACTOR;
        }
        if (this.domeUp()) {
            amount *= DOME_FACTOR;
            if (this.domeHitCd <= 0) {
                this.domeHitCd = 12;
                this.sendAction(DOME_HIT);
                this.sound(ModSounds.NABAT_DOME_HIT, 1.2f, 0.9f + this.random.nextFloat() * 0.25f);
            }
        } else if (this.heartHit(source)) {
            amount *= HEART_FACTOR;
            if (this.action == REQUIEM) {
                this.requiemHit += amount;
            }
            ServerWorld s = this.sw();
            if (s != null) {
                Vec3d h = this.getPos().add(this.forward().multiply(HEART_FWD)).add(0, HEART_UP + 0.5, 0);
                s.spawnParticles(AMBER, h.x, h.y, h.z, 10, 0.25, 0.25, 0.25, 0.1);
                this.sound(ModSounds.NABAT_CHIME, 1.8f, 0.7f);
            }
        }
        amount *= 1f + 0.1f * this.crack;
        return super.damage(source, amount);
    }

    @Override
    public boolean handleFallDamage(float fallDistance, float damageMultiplier, DamageSource damageSource) {
        return false;
    }

    @Override
    public boolean isFireImmune() {
        return true;
    }

    @Override
    public boolean isPushable() {
        return false;
    }

    @Override
    protected void pushAway(Entity entity) {
    }

    @Override
    public boolean canImmediatelyDespawn(double distanceSquared) {
        return false;
    }

    // =====================================================================
    // Смерть
    // =====================================================================
    @Override
    public void onDeath(DamageSource source) {
        super.onDeath(source);
        ServerWorld s = this.sw();
        if (s == null) {
            return;
        }
        this.setNoGravity(false);
        this.sound(ModSounds.NABAT_DEATH, 6.0f, 1.0f);
        // все слуги и колокола рассыпаются (душ нет)
        for (LivingEntity e : s.getEntitiesByClass(LivingEntity.class, this.getBoundingBox().expand(80.0),
                e -> e != this && e.isAlive() && e.getCommandTags().contains(NabatHooks.TAG))) {
            s.spawnParticles(AMBER, e.getX(), e.getBodyY(0.5), e.getZ(), 12, 0.3, 0.5, 0.3, 0.05);
            s.spawnParticles(ParticleTypes.LARGE_SMOKE, e.getX(), e.getBodyY(0.5), e.getZ(), 6, 0.3, 0.5, 0.3, 0.02);
            e.discard();
        }
        for (NabatSoulEntity soul : s.getEntitiesByClass(NabatSoulEntity.class, this.getBoundingBox().expand(80.0), x -> true)) {
            soul.discard();
        }
    }

    @Override
    protected void updatePostDeath() {
        this.deathTime++;
        ServerWorld s = this.sw();
        if (s != null) {
            if (this.deathTime == 32) {
                this.sound(ModSounds.NABAT_CRASH, 5.0f, 0.7f);
                for (double r = 1; r < 6; r += 1) {
                    this.groundRing(this.getPos(), r, (int) (r * 7), true);
                }
            }
            if (this.deathTime > 36 && this.deathTime % 4 == 0) {
                s.spawnParticles(AMBER, this.getX(), this.getY() + 1.5, this.getZ(), 6, 1.2, 0.8, 1.2, 0.03);
            }
            if (this.deathTime == 60) {
                this.sound(ModSounds.NABAT_BELL_BREAK, 5.0f, 0.6f);
                s.spawnParticles(ParticleTypes.END_ROD, this.getX(), this.getY() + 2, this.getZ(), 60, 1.5, 1.5, 1.5, 0.15);
            }
        }
        if (this.deathTime >= DEATH_TICKS && !this.getWorld().isClient && !this.isRemoved()) {
            this.getWorld().sendEntityStatus(this, EntityStatuses.ADD_DEATH_PARTICLES);
            this.remove(RemovalReason.KILLED);
        }
    }

    // =====================================================================
    // Звуки
    // =====================================================================
    @Override
    protected SoundEvent getAmbientSound() {
        return ModSounds.NABAT_AMBIENT;
    }

    @Override
    public int getMinAmbientSoundDelay() {
        return 480;
    }

    @Override
    protected SoundEvent getHurtSound(DamageSource source) {
        return this.domeUp() ? null : ModSounds.NABAT_HURT;
    }

    @Override
    protected SoundEvent getDeathSound() {
        return null;
    }

    @Override
    protected float getSoundVolume() {
        return 3.0f;
    }

    // =====================================================================
    // Сохранение
    // =====================================================================
    @Override
    public void writeCustomDataToNbt(NbtCompound nbt) {
        super.writeCustomDataToNbt(nbt);
        nbt.putBoolean("Started", this.started);
        if (this.arena != null) {
            nbt.putDouble("ArenaX", this.arena.x);
            nbt.putDouble("ArenaY", this.arena.y);
            nbt.putDouble("ArenaZ", this.arena.z);
        }
        nbt.putBoolean("Phase3", this.isPhase3());
        nbt.putBoolean("ReliqDone", this.reliqDone);
        nbt.putBoolean("LancerGuard", this.lancerGuard);
        if (this.lancerId != null) {
            nbt.putUuid("Lancer", this.lancerId);
        }
        nbt.putInt("BellsCd", this.bellsCd);
        nbt.putInt("Requiem", this.requiemStacks);
        nbt.putInt("Crack", this.crack);
        nbt.putBoolean("Grounded", this.isGrounded());
        nbt.putInt("GroundTimer", this.groundTimer);
        nbt.putBoolean("NeedBells", this.needBells);
        nbt.putBoolean("BellsPlaced", this.bellsPlaced);
        nbt.putInt("Cycle", this.cycleTicks);
        int[] ids = new int[this.bells.size() * 4];
        for (int i = 0; i < this.bells.size(); i++) {
            UUID u = this.bells.get(i);
            ids[i * 4] = (int) (u.getMostSignificantBits() >> 32);
            ids[i * 4 + 1] = (int) u.getMostSignificantBits();
            ids[i * 4 + 2] = (int) (u.getLeastSignificantBits() >> 32);
            ids[i * 4 + 3] = (int) u.getLeastSignificantBits();
        }
        nbt.putIntArray("Bells", ids);
    }

    @Override
    public void readCustomDataFromNbt(NbtCompound nbt) {
        super.readCustomDataFromNbt(nbt);
        this.started = nbt.getBoolean("Started");
        if (nbt.contains("ArenaX")) {
            this.arena = new Vec3d(nbt.getDouble("ArenaX"), nbt.getDouble("ArenaY"), nbt.getDouble("ArenaZ"));
            this.center = this.arena;
        }
        this.dataTracker.set(PHASE3, nbt.getBoolean("Phase3"));
        this.reliqDone = nbt.getBoolean("ReliqDone");
        this.dataTracker.set(RELIQ_GONE, this.reliqDone);
        this.lancerGuard = nbt.getBoolean("LancerGuard");
        this.lancerId = nbt.containsUuid("Lancer") ? nbt.getUuid("Lancer") : null;
        this.bellsCd = nbt.getInt("BellsCd");
        this.requiemStacks = nbt.getInt("Requiem");
        this.crack = nbt.getInt("Crack");
        this.dataTracker.set(GROUNDED, nbt.getBoolean("Grounded"));
        this.setNoGravity(!this.isGrounded());
        this.groundTimer = nbt.getInt("GroundTimer");
        this.needBells = nbt.getBoolean("NeedBells");
        this.bellsPlaced = nbt.getBoolean("BellsPlaced");
        this.cycleTicks = nbt.getInt("Cycle");
        this.bells.clear();
        int[] ids = nbt.getIntArray("Bells");
        for (int i = 0; i + 3 < ids.length; i += 4) {
            long most = ((long) ids[i] << 32) | (ids[i + 1] & 0xFFFFFFFFL);
            long least = ((long) ids[i + 2] << 32) | (ids[i + 3] & 0xFFFFFFFFL);
            this.bells.add(new UUID(most, least));
        }
        if (this.hasCustomName()) {
            this.bossBar.setName(this.getDisplayName());
        }
    }
}

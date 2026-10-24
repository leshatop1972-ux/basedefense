package com.lesha_top1972.basedefense.entity;

import com.lesha_top1972.basedefense.sound.ModSounds;
import net.minecraft.block.BlockState;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.ai.goal.ActiveTargetGoal;
import net.minecraft.entity.ai.goal.LookAroundGoal;
import net.minecraft.entity.ai.goal.LookAtEntityGoal;
import net.minecraft.entity.ai.goal.RevengeGoal;
import net.minecraft.entity.ai.goal.SwimGoal;
import net.minecraft.entity.ai.goal.WanderAroundFarGoal;
import net.minecraft.entity.attribute.DefaultAttributeContainer;
import net.minecraft.entity.data.DataTracker;
import net.minecraft.entity.data.TrackedData;
import net.minecraft.entity.data.TrackedDataHandlerRegistry;
import com.lesha_top1972.basedefense.effect.ModEffects;
import net.minecraft.particle.BlockStateParticleEffect;
import net.minecraft.particle.DustParticleEffect;
import net.minecraft.sound.SoundCategory;
import org.joml.Vector3f;
import net.minecraft.entity.attribute.EntityAttributes;
import net.minecraft.entity.boss.BossBar;
import net.minecraft.entity.boss.ServerBossBar;
import net.minecraft.entity.damage.DamageSource;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.entity.mob.HostileEntity;
import net.minecraft.entity.passive.IronGolemEntity;
import net.minecraft.entity.passive.MerchantEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.projectile.PersistentProjectileEntity;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundEvent;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.GameRules;
import net.minecraft.world.World;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Босс «Бордюр» — боец в чёрной броне с реактивными соплами в локтях. 500 HP, стрелы −55%,
 * раз в 25 с переключается на другого игрока.
 * <ul>
 *   <li>COMBO «Серия» — прямой правой, удар ногой, прямой правой (по 3, с рывками вперёд, доворот на цель), 2.3 с;</li>
 *   <li>JET «Реактивный удар» — замах 1.5 с, из локтя бьёт струя; рывок на 7 блоков, 11 урона (можно увернуться);</li>
 *   <li>SLAM «Двойной таран» — раз в 30 с: рывок на 4 блока, удар обеими руками (5, ломает блоки),
 *       топот: 3 в радиусе 4 + замедление I на 5 с; 3.2 с;</li>
 *   <li>в любой фазе: PILLAR «Столб» (раз в 45 с, цель ≤ 8 бл: столб из земли поднимает игрока на 10 блоков,
 *       без урона; поднял — переключается на другого), GUARD «Шипы» (раз в 72 с: руки крестом, 15 с отражает
 *       30% урона), DIVE «Пике» (взлёт на струях из ног на 8 блоков и удар кулаком вниз: 12 в радиусе 5 +
 *       оглушение I на 3 с);</li>
 *   <li>после атак иногда отскакивает назад или вбок;</li>
 *   <li>после потери 25% здоровья: SPIN «Вихрь» (нога вперёд, обороты, 5 всем вокруг, 2 с) и
 *       SUMMON «Подмога» (раз в 2 минуты — двое прислужников из‑под земли); старые атаки — реже.</li>
 * </ul>
 */
public class CurbEntity extends AbstractCurbEntity {

    public static final float MODEL_SCALE = 1.12f;

    public static final int SLAM_TICKS = 64;
    public static final int SLAM_DASH = 22;
    public static final int SLAM_HIT = 28;
    public static final int STOMP_HIT = 42;
    static final double SLAM_SPEED = 0.67;
    static final int SLAM_COOLDOWN = 600;

    public static final int SPIN_TICKS = 40;
    static final int SPIN_FROM = 9;
    static final int SPIN_TO = 30;
    static final double SPIN_RADIUS = 4.0;
    static final double SLAM_REACH = 3.9;
    static final double STOMP_RADIUS = 5.0;

    public static final int SUMMON_TICKS = 46;
    static final int SUMMON_HIT = 21;
    static final int SUMMON_COOLDOWN = 2400;
    static final int MAX_MINIONS = 4;

    /** «Столб»: метка под целью 4–25 т, место фиксируется на 25 т, столб — на 30 т; 56 т (2.8 с). */
    public static final int PILLAR_TICKS = 56;
    static final int PILLAR_LOCK = 25;
    static final int PILLAR_HIT = 30;
    static final int PILLAR_COOLDOWN = 900;
    static final double PILLAR_RANGE = 8.0;
    /** «Шипы»: эффект на 12 т, 29 т (1.45 с); эффект 15 с. */
    public static final int GUARD_TICKS = 29;
    static final int GUARD_APPLY = 12;
    static final int GUARD_COOLDOWN = 1440;
    static final int SPIKES_TICKS = 300;
    /** «Пике»: присед 0–10, взлёт 10–28 (8 блоков), зависание 28–37, падение с 37 до земли, приземление 22 т. */
    public static final int DIVE_LIFT = 10;
    public static final int DIVE_APEX = 28;
    public static final int DIVE_FALL = 37;
    static final int DIVE_AIM_LOCK = 35;
    static final int DIVE_TIMEOUT = 74;
    public static final int DIVE_LAND_TICKS = 22;
    static final double DIVE_HEIGHT = 8.0;
    static final double DIVE_FALL_SPEED = 1.6;
    static final double DIVE_RADIUS = 5.0;
    static final float DIVE_DAMAGE = 12f;
    static final int DIVE_STUN_TICKS = 60;
    static final int DIVE_COOLDOWN = 360;

    static final float COMBO_DAMAGE = 3f;
    static final float JET_DAMAGE = 11f;
    static final float SLAM_DAMAGE = 5f;
    static final float STOMP_DAMAGE = 3f;
    static final float SPIN_DAMAGE = 5f;
    static final float ARROW_FACTOR = 0.45f;
    static final int RETARGET_TICKS = 500;

    private final ServerBossBar bossBar = new ServerBossBar(this.getDisplayName(), BossBar.Color.PINK,
            BossBar.Style.NOTCHED_10);
    private static final TrackedData<Boolean> SPIKED =
            DataTracker.registerData(CurbEntity.class, TrackedDataHandlerRegistry.BOOLEAN);
    private static final DustParticleEffect SPIKE_DUST = new DustParticleEffect(new Vector3f(0.72f, 0.88f, 1.0f), 0.8f);
    private boolean phase2;
    private int retargetTimer = RETARGET_TICKS;

    public CurbEntity(EntityType<? extends HostileEntity> type, World world) {
        super(type, world);
        this.experiencePoints = 150;
        this.setPersistent();
    }

    public static DefaultAttributeContainer.Builder createAttributes() {
        return HostileEntity.createHostileAttributes()
                .add(EntityAttributes.GENERIC_MAX_HEALTH, 500.0)
                .add(EntityAttributes.GENERIC_MOVEMENT_SPEED, 0.26)
                .add(EntityAttributes.GENERIC_FOLLOW_RANGE, 32.0)
                .add(EntityAttributes.GENERIC_ATTACK_DAMAGE, 3.0)
                .add(EntityAttributes.GENERIC_ARMOR, 6.0)
                .add(EntityAttributes.GENERIC_KNOCKBACK_RESISTANCE, 0.8);
    }

    @Override
    float comboDamage() {
        return COMBO_DAMAGE;
    }

    @Override
    float jetDamage() {
        return JET_DAMAGE;
    }

    @Override
    public float modelScale() {
        return MODEL_SCALE;
    }

    @Override
    float dodgeChance() {
        return 0.35f;
    }

    @Override
    protected void initDataTracker() {
        super.initDataTracker();
        this.dataTracker.startTracking(SPIKED, false);
    }

    /** Действуют ли «Шипы» (и на клиенте — для частиц). */
    public boolean isSpiked() {
        return this.dataTracker.get(SPIKED);
    }

    /** Пока действуют «Шипы» — вокруг кружат бело‑синие искры. */
    @Override
    protected void clientEffects() {
        super.clientEffects();
        if (!this.isSpiked()) {
            return;
        }
        World w = this.getWorld();
        double a = this.age * 0.35;
        for (int i = 0; i < 2; i++) {
            double b = a + i * Math.PI;
            double y = this.getY() + 0.3 + ((this.age * 0.07 + i * 0.5) % 1.0) * this.getHeight() * 0.9;
            w.addParticle(i == 0 ? ParticleTypes.END_ROD : SPIKE_DUST, this.getX() + Math.cos(b) * 0.85, y,
                    this.getZ() + Math.sin(b) * 0.85, 0, 0.01, 0);
        }
    }

    @Override
    protected void initGoals() {
        this.goalSelector.add(0, new SwimGoal(this));
        this.goalSelector.add(2, new BossGoal(this));
        this.goalSelector.add(7, new WanderAroundFarGoal(this, 0.8));
        this.goalSelector.add(8, new LookAtEntityGoal(this, PlayerEntity.class, 12.0f));
        this.goalSelector.add(8, new LookAroundGoal(this));

        this.targetSelector.add(1, new RevengeGoal(this, CurbMinionEntity.class));
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
        boolean spiked = this.hasStatusEffect(ModEffects.SPIKES);
        if (spiked != this.dataTracker.get(SPIKED)) {
            this.dataTracker.set(SPIKED, spiked);
        }
        if (--this.retargetTimer <= 0) {
            this.retargetTimer = RETARGET_TICKS;
            this.switchTarget();
        }
    }

    /** Раз в 25 секунд — на другого видимого игрока поблизости (если есть). */
    private void switchTarget() {
        this.switchTarget(null);
    }

    /** На другого игрока (не текущего и не exclude); возвращает, нашёлся ли. */
    boolean switchTarget(LivingEntity exclude) {
        LivingEntity current = this.getTarget();
        List<PlayerEntity> others = new ArrayList<>();
        for (PlayerEntity p : this.getWorld().getEntitiesByClass(PlayerEntity.class, this.getBoundingBox().expand(32.0),
                p -> p.isAlive() && !p.isCreative() && !p.isSpectator() && p != current && p != exclude)) {
            if (this.getVisibilityCache().canSee(p)) {
                others.add(p);
            }
        }
        if (!others.isEmpty()) {
            this.setTarget(others.get(this.random.nextInt(others.size())));
            this.retargetTimer = RETARGET_TICKS;
            return true;
        }
        return false;
    }

    @Override
    public boolean damage(DamageSource source, float amount) {
        if (source.getSource() instanceof PersistentProjectileEntity) {
            amount *= ARROW_FACTOR;
        }
        return super.damage(source, amount);
    }

    @Override
    public void writeCustomDataToNbt(NbtCompound nbt) {
        super.writeCustomDataToNbt(nbt);
        nbt.putBoolean("Phase2", this.phase2);
    }

    @Override
    public void readCustomDataFromNbt(NbtCompound nbt) {
        super.readCustomDataFromNbt(nbt);
        this.phase2 = nbt.getBoolean("Phase2");
        if (this.hasCustomName()) {
            this.bossBar.setName(this.getDisplayName());
        }
    }

    @Override
    protected SoundEvent getAmbientSound() {
        return SoundEvents.ENTITY_IRON_GOLEM_STEP;
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
    public float getSoundPitch() {
        return 0.8f + this.random.nextFloat() * 0.1f;
    }

    @Override
    protected void playStepSound(BlockPos pos, BlockState state) {
        this.playSound(SoundEvents.ENTITY_IRON_GOLEM_STEP, 0.35f, 1.3f);
    }

    boolean isPhase2() {
        return this.phase2;
    }

    // --- атаки (сервер) ---

    /** «Двойной таран» немного ломает блоки перед собой (если разрешено правилом mobGriefing). */
    void breakBlocks() {
        World w = this.getWorld();
        if (!(w instanceof ServerWorld s) || !s.getGameRules().getBoolean(GameRules.DO_MOB_GRIEFING)) {
            return;
        }
        Vec3d f = this.forward();
        Vec3d side = new Vec3d(-f.z, 0, f.x);
        List<BlockPos> cand = new ArrayList<>();
        for (double d = 1.0; d <= 2.2; d += 0.6) {
            for (int k = -1; k <= 1; k++) {
                for (int dy = 0; dy <= 2; dy++) {
                    Vec3d p = this.getPos().add(f.multiply(d)).add(side.multiply(k * 0.8)).add(0, dy + 0.2, 0);
                    BlockPos bp = BlockPos.ofFloored(p);
                    if (!cand.contains(bp)) {
                        cand.add(bp);
                    }
                }
            }
        }
        Collections.shuffle(cand, new java.util.Random(this.random.nextLong()));
        int broken = 0;
        for (BlockPos bp : cand) {
            if (broken >= 5) {
                break;
            }
            BlockState st = w.getBlockState(bp);
            float hard = st.getHardness(w, bp);
            if (st.isAir() || hard < 0 || hard > 2.5f || st.hasBlockEntity() || !st.getFluidState().isEmpty()) {
                continue;
            }
            if (this.random.nextFloat() < 0.6f) {
                w.breakBlock(bp, true, this);
                broken++;
            }
        }
    }

    void stomp() {
        for (LivingEntity e : this.victims(STOMP_RADIUS)) {
            if (this.edgeDist(e) <= STOMP_RADIUS && this.inHeight(e, 1.0)) {
                if (this.strike(e, STOMP_DAMAGE, 0.5, 0.25)) {
                    e.addStatusEffect(new StatusEffectInstance(StatusEffects.SLOWNESS, 100, 0), this);
                }
            }
        }
        Vec3d c = this.getPos();
        this.groundRing(c, 1.6, 12);
        this.groundRing(c, 3.3, 22);
        this.groundRing(c, STOMP_RADIUS, 30);
        ServerWorld s = this.sw();
        if (s != null) {
            s.spawnParticles(ParticleTypes.EXPLOSION, c.x, c.y + 0.3, c.z, 1, 0, 0, 0, 0);
        }
        this.sound(SoundEvents.ENTITY_GENERIC_EXPLODE, 0.6f, 0.7f);
        this.sound(SoundEvents.ENTITY_RAVAGER_STEP, 1.4f, 0.6f);
    }

    void spinHits(java.util.Set<Integer> hitIds) {
        if (this.radiusHit(SPIN_RADIUS, 1.2, SPIN_DAMAGE, 0.9, 0.25, hitIds) > 0) {
            this.sound(SoundEvents.ENTITY_PLAYER_ATTACK_KNOCKBACK, 1.0f, 0.8f);
        }
    }

    void spinFx() {
        ServerWorld s = this.sw();
        if (s == null) {
            return;
        }
        double a = this.random.nextDouble() * Math.PI * 2;
        for (int i = 0; i < 3; i++) {
            double b = a + i * Math.PI * 2 / 3;
            s.spawnParticles(ParticleTypes.SWEEP_ATTACK, this.getX() + Math.cos(b) * 2.4, this.getY() + 0.9,
                    this.getZ() + Math.sin(b) * 2.4, 1, 0, 0, 0, 0);
        }
    }

    int minionsAround() {
        return this.getWorld().getEntitiesByClass(CurbMinionEntity.class, this.getBoundingBox().expand(32.0),
                LivingEntity::isAlive).size();
    }

    void summonMinions(LivingEntity target) {
        if (!(this.getWorld() instanceof ServerWorld s)) {
            return;
        }
        Vec3d f = this.forward();
        Vec3d side = new Vec3d(-f.z, 0, f.x);
        for (int k = -1; k <= 1; k += 2) {
            Vec3d p = this.getPos().add(side.multiply(k * 2.6)).add(f.multiply(0.8));
            CurbMinionEntity m = ModEntities.CURB_MINION.create(s);
            if (m == null) {
                continue;
            }
            m.refreshPositionAndAngles(p.x, this.getY(), p.z, this.getYaw(), 0f);
            if (!s.isSpaceEmpty(m)) {
                m.refreshPositionAndAngles(this.getX(), this.getY(), this.getZ(), this.getYaw(), 0f);
            }
            m.setSummoned();
            if (target != null) {
                m.setTarget(target);
            }
            s.spawnEntity(m);
            this.groundRing(m.getPos(), 0.8, 14);
            s.spawnParticles(ParticleTypes.SOUL, m.getX(), m.getY() + 0.3, m.getZ(), 8, 0.4, 0.2, 0.4, 0.03);
        }
        this.sound(SoundEvents.ENTITY_EVOKER_PREPARE_SUMMON, 1.0f, 0.7f);
        this.sound(SoundEvents.BLOCK_ROOTED_DIRT_BREAK, 1.5f, 0.6f);
    }

    // --- «Столб» ---

    /** Блок земли под существом (до 6 блоков вниз), или null. */
    BlockPos groundUnder(LivingEntity e) {
        BlockPos p = BlockPos.ofFloored(e.getX(), e.getY() - 0.01, e.getZ());
        for (int i = 0; i < 6; i++) {
            BlockState st = this.getWorld().getBlockState(p);
            if (!st.getCollisionShape(this.getWorld(), p).isEmpty()) {
                return p;
            }
            p = p.down();
        }
        return null;
    }

    private static final DustParticleEffect MARK_DUST = new DustParticleEffect(new Vector3f(0.8f, 0.92f, 1.0f), 1.0f);

    /** Метка будущего столба: бело‑синее кольцо и трещины земли под целью. */
    void pillarMark(BlockPos ground, boolean locked) {
        ServerWorld s = this.sw();
        if (s == null || ground == null) {
            return;
        }
        double cx = ground.getX() + 0.5;
        double cy = ground.getY() + 1.05;
        double cz = ground.getZ() + 0.5;
        int n = locked ? 14 : 8;
        double off = this.age * 0.2;
        for (int i = 0; i < n; i++) {
            double a = off + i * Math.PI * 2 / n;
            s.spawnParticles(MARK_DUST, cx + Math.cos(a) * 0.9, cy, cz + Math.sin(a) * 0.9, 1, 0, 0, 0, 0);
        }
        BlockState g = this.getWorld().getBlockState(ground);
        if (!g.isAir()) {
            s.spawnParticles(new BlockStateParticleEffect(ParticleTypes.BLOCK, g), cx, cy, cz, locked ? 6 : 2,
                    0.35, 0.02, 0.35, 0.05);
        }
    }

    /** Вырастить столб в точке ground; true — подняли того, в кого целились. */
    boolean pillarErupt(BlockPos ground, LivingEntity target) {
        if (!(this.getWorld() instanceof ServerWorld s) || ground == null) {
            return false;
        }
        boolean hitTarget = false;
        for (LivingEntity e : CurbPillarEntity.standingOn(s, ground)) {
            if (e == target) {
                hitTarget = true;
            }
        }
        CurbPillarEntity pillar = ModEntities.CURB_PILLAR.create(s);
        if (pillar != null) {
            pillar.setup(ground);
            s.spawnEntity(pillar);
        }
        s.spawnParticles(ParticleTypes.EXPLOSION, ground.getX() + 0.5, ground.getY() + 1.2, ground.getZ() + 0.5, 1, 0, 0, 0, 0);
        s.spawnParticles(ParticleTypes.END_ROD, ground.getX() + 0.5, ground.getY() + 1.5, ground.getZ() + 0.5, 12,
                0.3, 1.0, 0.3, 0.08);
        s.playSound(null, ground, SoundEvents.ENTITY_EVOKER_FANGS_ATTACK, SoundCategory.HOSTILE, 1.4f, 0.6f);
        s.playSound(null, ground, SoundEvents.ENTITY_GENERIC_EXPLODE, SoundCategory.HOSTILE, 0.5f, 1.3f);
        return hitTarget;
    }

    // --- «Шипы» ---
    void applySpikes() {
        this.addStatusEffect(new StatusEffectInstance(ModEffects.SPIKES, SPIKES_TICKS, 0, false, false, true));
        ServerWorld s = this.sw();
        if (s != null) {
            s.spawnParticles(ParticleTypes.END_ROD, this.getX(), this.getBodyY(0.6), this.getZ(), 24, 0.5, 0.7, 0.5, 0.12);
            s.spawnParticles(SPIKE_DUST, this.getX(), this.getBodyY(0.5), this.getZ(), 20, 0.6, 0.8, 0.6, 0.0);
        }
        this.sound(SoundEvents.BLOCK_ANVIL_LAND, 0.6f, 1.6f);
        this.sound(SoundEvents.BLOCK_AMETHYST_BLOCK_CHIME, 1.6f, 0.7f);
    }

    // --- «Пике» ---
    void diveLand() {
        for (LivingEntity e : this.victims(DIVE_RADIUS)) {
            if (this.edgeDist(e) <= DIVE_RADIUS && this.inHeight(e, 1.5)) {
                if (this.strike(e, DIVE_DAMAGE, 0.9, 0.45)) {
                    e.addStatusEffect(new StatusEffectInstance(ModEffects.STUN, DIVE_STUN_TICKS, 0), this);
                }
            }
        }
        Vec3d c = this.getPos();
        this.groundRing(c, 1.5, 12);
        this.groundRing(c, 3.2, 22);
        this.groundRing(c, DIVE_RADIUS, 32);
        ServerWorld s = this.sw();
        if (s != null) {
            s.spawnParticles(ParticleTypes.EXPLOSION_EMITTER, c.x, c.y + 0.2, c.z, 1, 0, 0, 0, 0);
            s.spawnParticles(ParticleTypes.SOUL_FIRE_FLAME, c.x, c.y + 0.2, c.z, 30, 1.2, 0.1, 1.2, 0.15);
        }
        this.sound(SoundEvents.ENTITY_GENERIC_EXPLODE, 1.0f, 0.8f);
        this.sound(SoundEvents.ENTITY_IRON_GOLEM_DAMAGE, 1.4f, 0.6f);
    }

    /** ИИ босса: общая часть (серия, реактивный удар) + таран, вихрь, призыв. */
    static class BossGoal extends BrawlGoal<CurbEntity> {
        private int slamCooldown = 160;
        private int summonCooldown = 0;
        private int pillarCooldown = 200;
        private int guardCooldown = 260;
        private int diveCooldown = 140;
        private BlockPos pillarAt;
        private Vec3d diveAim = Vec3d.ZERO;

        BossGoal(CurbEntity mob) {
            super(mob);
        }

        @Override
        double chaseSpeed() {
            return 1.25;                                   // 0.26 × 1.25 ≈ 85% бега игрока
        }

        @Override
        int restTicks(int finished) {
            return 12 + this.mob.random.nextInt(29);       // не больше 2 секунд
        }

        @Override
        int jetCooldownTicks() {
            return 80;
        }

        @Override
        protected void tickCooldowns() {
            super.tickCooldowns();
            if (this.slamCooldown > 0) {
                this.slamCooldown--;
            }
            if (this.summonCooldown > 0) {
                this.summonCooldown--;
            }
            if (this.pillarCooldown > 0) {
                this.pillarCooldown--;
            }
            if (this.guardCooldown > 0) {
                this.guardCooldown--;
            }
            if (this.diveCooldown > 0) {
                this.diveCooldown--;
            }
        }

        @Override
        protected void options(List<int[]> out, double dist) {
            super.options(out, dist);
            if (this.mob.isPhase2()) {
                for (int[] o : out) {
                    o[1] = o[1] * 3 / 5;                   // во 2‑й фазе старые атаки реже
                }
            }
            // новые атаки — в любой фазе
            if (this.pillarCooldown <= 0 && dist < PILLAR_RANGE) {
                out.add(new int[]{PILLAR, 45});
            }
            if (this.guardCooldown <= 0 && dist < 14) {
                out.add(new int[]{GUARD, 30});
            }
            if (this.diveCooldown <= 0 && dist > 3.0 && dist < 16) {
                out.add(new int[]{DIVE, 35});
            }
            if (!this.mob.isPhase2()) {
                return;
            }
            if (dist < 4.6) {
                out.add(new int[]{SPIN, 40});
            }
            if (this.summonCooldown <= 0 && dist < 16 && this.mob.minionsAround() < MAX_MINIONS) {
                out.add(new int[]{SUMMON, 45});
            }
        }

        @Override
        protected boolean preAction(LivingEntity target, double dist) {
            CurbEntity m = this.mob;
            if (!m.phase2 && m.getHealth() <= m.getMaxHealth() * 0.75f) {
                m.phase2 = true;
                m.sound(SoundEvents.ENTITY_IRON_GOLEM_REPAIR, 1.5f, 0.6f);
                if (m.getWorld() instanceof ServerWorld s) {
                    s.spawnParticles(ParticleTypes.SOUL_FIRE_FLAME, m.getX(), m.getBodyY(0.6), m.getZ(), 30,
                            0.6, 0.8, 0.6, 0.05);
                }
            }
            // «Двойной таран» — как только готов (раз в 30 с) и цель близко
            if (this.cooldown <= 0 && this.slamCooldown <= 0 && dist < 8.0 && m.getVisibilityCache().canSee(target)) {
                m.faceTarget(target);
                this.begin(SLAM);
                return true;
            }
            return false;
        }

        @Override
        protected void begin(int a) {
            super.begin(a);
            if (a == SLAM) {
                this.slamCooldown = SLAM_COOLDOWN;
            } else if (a == SUMMON) {
                this.summonCooldown = SUMMON_COOLDOWN;
            } else if (a == PILLAR) {
                this.pillarCooldown = PILLAR_COOLDOWN;
                this.pillarAt = null;
            } else if (a == GUARD) {
                this.guardCooldown = GUARD_COOLDOWN;
            } else if (a == DIVE) {
                this.diveCooldown = DIVE_COOLDOWN;
            }
        }

        @Override
        protected boolean runExtra(LivingEntity target) {
            CurbEntity m = this.mob;
            int t = this.tick;
            switch (this.action) {
                case SLAM -> {
                    if (target != null && t < SLAM_DASH) {
                        m.turnToward(target, 20f);
                    }
                    if (t == 12) {
                        m.sound(ModSounds.CURB_JET_CHARGE, 0.9f, 1.25f);
                    }
                    if (t == SLAM_DASH) {
                        this.dashDir = m.forward();
                        m.sound(ModSounds.CURB_JET_BURST, 1.2f, 0.85f);
                    }
                    if (t >= SLAM_DASH && t < SLAM_HIT) {
                        this.dash(SLAM_SPEED);
                    }
                    if (t == SLAM_HIT) {
                        m.setVelocity(0, m.getVelocity().y, 0);
                        m.velocityModified = true;
                        m.coneHit(SLAM_REACH, 60, SLAM_DAMAGE, 1.0, 0.3, this.hitIds);
                        m.breakBlocks();
                        m.impactFx(1.7, 1.3, true);
                        m.sound(SoundEvents.ENTITY_ZOMBIE_ATTACK_IRON_DOOR, 1.2f, 0.7f);
                    }
                    if (t == SLAM_HIT + 1) {
                        m.coneHit(SLAM_REACH, 60, SLAM_DAMAGE, 1.0, 0.3, this.hitIds);   // окно попадания
                    }
                    if (t == STOMP_HIT) {
                        m.stomp();
                    }
                    if (t >= SLAM_TICKS) {
                        this.finish();
                    }
                }
                case SPIN -> {
                    if (t == SPIN_FROM) {
                        m.sound(SoundEvents.ENTITY_PLAYER_ATTACK_SWEEP, 1.2f, 0.6f);
                    }
                    if (t >= SPIN_FROM && t <= SPIN_TO) {
                        m.spinHits(this.hitIds);
                        if (t % 3 == 0) {
                            m.spinFx();
                        }
                        if (t % 6 == 0) {
                            m.sound(SoundEvents.ENTITY_PLAYER_ATTACK_SWEEP, 0.8f, 0.8f + t * 0.01f);
                        }
                    }
                    if (t >= SPIN_TICKS) {
                        this.finish();
                    }
                }
                case PILLAR -> {
                    if (target != null && t < PILLAR_LOCK) {
                        m.turnToward(target, 15f);
                    }
                    if (t == 6) {
                        m.sound(SoundEvents.BLOCK_DEEPSLATE_BREAK, 1.5f, 0.5f);
                    }
                    if (t >= 4 && t < PILLAR_LOCK && target != null && t % 2 == 0
                            && m.distanceTo(target) < PILLAR_RANGE + 2) {
                        m.pillarMark(m.groundUnder(target), false);
                    }
                    if (t == PILLAR_LOCK && target != null) {
                        this.pillarAt = m.groundUnder(target);
                        m.sound(SoundEvents.BLOCK_BEACON_ACTIVATE, 0.8f, 1.8f);
                    }
                    if (t > PILLAR_LOCK && t < PILLAR_HIT) {
                        m.pillarMark(this.pillarAt, true);
                    }
                    if (t == PILLAR_HIT && this.pillarAt != null) {
                        if (m.pillarErupt(this.pillarAt, target)) {
                            m.switchTarget(target);           // поднял цель столбом — за другим игроком
                        }
                    }
                    if (t >= PILLAR_TICKS) {
                        this.finish();
                    }
                }
                case GUARD -> {
                    if (t == 4) {
                        m.sound(SoundEvents.BLOCK_CHAIN_PLACE, 1.4f, 0.6f);
                    }
                    if (t == GUARD_APPLY) {
                        m.applySpikes();
                    }
                    if (t >= GUARD_TICKS) {
                        this.finish();
                    }
                }
                case DIVE -> this.runDive(target);
                case DIVE_LAND -> {
                    if (t >= DIVE_LAND_TICKS) {
                        this.finish();
                    }
                }
                case SUMMON -> {
                    if (t == 8) {
                        m.sound(SoundEvents.ENTITY_EVOKER_CAST_SPELL, 1.0f, 0.6f);
                    }
                    if (t == SUMMON_HIT) {
                        m.groundRing(m.getPos().add(m.forward().multiply(1.2)), 1.2, 16);
                        m.sound(SoundEvents.ENTITY_GENERIC_EXPLODE, 0.4f, 1.2f);
                        m.summonMinions(target);
                    }
                    if (t >= SUMMON_TICKS) {
                        this.finish();
                    }
                }
                default -> {
                    return false;
                }
            }
            return true;
        }

        /** «Пике»: присед, взлёт на струях, зависание над целью, удар вниз до земли. */
        private void runDive(LivingEntity target) {
            CurbEntity m = this.mob;
            int t = this.tick;
            if (t == 4) {
                m.sound(ModSounds.CURB_JET_CHARGE, 1.2f, 0.8f);
            }
            if (t == DIVE_LIFT) {
                m.setNoGravity(true);
                m.sound(ModSounds.CURB_JET_BURST, 1.6f, 0.7f);
                m.groundRing(m.getPos(), 1.2, 14);
            }
            if (t >= DIVE_LIFT && t < DIVE_FALL) {
                // подъём на DIVE_HEIGHT блоков и подлёт поверх цели
                double vy = t < DIVE_APEX && !m.verticalCollision ? DIVE_HEIGHT / (DIVE_APEX - DIVE_LIFT) : 0.0;
                double vx = 0;
                double vz = 0;
                if (target != null) {
                    double dx = target.getX() - m.getX();
                    double dz = target.getZ() - m.getZ();
                    double d = Math.sqrt(dx * dx + dz * dz);
                    if (d > 0.8) {
                        double sp = Math.min(0.35, d / Math.max(1, DIVE_FALL - t));
                        vx = dx / d * sp;
                        vz = dz / d * sp;
                    }
                    m.faceTarget(target);
                }
                m.setVelocity(vx, vy, vz);
                m.velocityModified = true;
                m.fallDistance = 0;
            }
            if (t == DIVE_AIM_LOCK) {
                Vec3d v = target != null ? target.getVelocity() : Vec3d.ZERO;
                this.diveAim = target != null ? target.getPos().add(v.x * 3, 0, v.z * 3) : m.getPos().add(0, -DIVE_HEIGHT, 0);
            }
            if (t >= DIVE_FALL) {
                if (t == DIVE_FALL) {
                    m.sound(SoundEvents.ENTITY_PHANTOM_SWOOP, 1.4f, 0.7f);
                }
                Vec3d to = this.diveAim.subtract(m.getPos());
                if (m.horizontalCollision || to.y > -0.2) {
                    to = new Vec3d(0, -1, 0);
                }
                Vec3d dir = to.normalize();
                if (dir.y > -0.5) {
                    dir = new Vec3d(dir.x, -0.5, dir.z).normalize();     // всегда круто вниз
                }
                m.setVelocity(dir.multiply(DIVE_FALL_SPEED));
                m.velocityModified = true;
                m.fallDistance = 0;
                if (m.isOnGround() || (m.verticalCollision && m.getVelocity().y <= 0) || t >= DIVE_TIMEOUT) {
                    m.setNoGravity(false);
                    m.setVelocity(0, 0, 0);
                    m.velocityModified = true;
                    this.action = DIVE_LAND;
                    this.tick = 0;
                    this.hitIds.clear();
                    m.sendAction(DIVE_LAND);
                    m.diveLand();
                }
            }
        }
    }
}

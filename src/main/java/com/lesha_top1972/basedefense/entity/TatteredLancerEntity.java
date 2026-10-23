package com.lesha_top1972.basedefense.entity;

import com.lesha_top1972.basedefense.combat.ShieldBlocker;
import com.lesha_top1972.basedefense.effect.ModEffects;
import net.minecraft.block.BlockState;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.LivingEntity;
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
import net.minecraft.entity.decoration.ArmorStandEntity;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.entity.mob.HostileEntity;
import net.minecraft.entity.mob.Monster;
import net.minecraft.entity.passive.IronGolemEntity;
import net.minecraft.entity.passive.MerchantEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.Item;
import net.minecraft.item.Items;
import net.minecraft.item.ShieldItem;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.particle.BlockStateParticleEffect;
import net.minecraft.particle.DustParticleEffect;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvent;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;
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
import java.util.UUID;

/**
 * Босс «Оборванный улан» — мёртвый дуэлянт с турнирным копьём. 400 HP, полоска босса.
 * <p>Атаки (анимации — tools/lancer_gen.py, тайминги совпадают):</p>
 * <ul>
 *   <li>THRUST — три укола (5 + 5 + 7);</li>
 *   <li>CHARGE — таран на ~9 блоков (12);</li>
 *   <li>LEAP — прыжок с сальто и удар в землю (11 в радиусе 3.2, подбрасывает);</li>
 *   <li>SWEEP — круговой взмах (8 вокруг);</li>
 *   <li>UPPERCUT — «вихревой подъём»: закрутка, взлёт с оборотом, копьё снизу вверх (9, подбрасывает),
 *       часто следом прыжок‑добивание;</li>
 *   <li>BACKSTEP — отскок с вращением копья, часто следом таран;</li>
 *   <li>RAIN — «ливень копий» (обе фазы): взлетает на 6 блоков, под ним синяя зона радиусом 13 (замедление I),
 *       через 2.5 с с неба падают призрачные копья (по 6.5);</li>
 *   <li>GRAND — «тройной таран» (только 2‑я фаза): три рывка сквозь цель с разворотами
 *       (по 10.5) и финальный выпад с ударной волной на 12 блоков (13.5), выбивает щит;</li>
 *   <li>ROAR — на половине здоровья: салют шляпой, вторая фаза (атакует чаще).</li>
 * </ul>
 * Эффект «Дуэлянт»: первый удар по игроку вешает его на 30 с (только на одного за раз); бьёт дуэлянта —
 * лечится на 1, а дуэлянт наносит ему на 20% больше урона. Раз в 13 с переключается на другого игрока.
 * «Подлая крыса»: 8% за удар по улану — на атакующего (не на дуэлянта), 10 с. «Тройной таран» выбивает щит на 7 с.
 */
public class TatteredLancerEntity extends HostileEntity {

    // действия (статус клиенту = STATUS_BASE + номер)
    public static final int NONE = -1;
    public static final int THRUST = 0;
    public static final int CHARGE = 1;
    public static final int LEAP = 2;
    public static final int LAND = 3;
    public static final int SWEEP = 4;
    public static final int UPPERCUT = 5;
    public static final int BACKSTEP = 6;
    public static final int ROAR = 7;
    public static final int GRAND = 8;
    public static final int RAIN = 9;
    public static final int RAIN_END = 10;
    public static final byte STATUS_BASE = 70;

    /** Длительности (тики), как у анимаций. */
    public static final int THRUST_TICKS = 30;
    public static final int CHARGE_TICKS = 36;
    public static final int LEAP_WINDUP = 10;
    public static final int LAND_TICKS = 16;
    public static final int SWEEP_TICKS = 26;
    public static final int UPPERCUT_TICKS = 26;
    public static final int BACKSTEP_TICKS = 14;
    public static final int ROAR_TICKS = 30;
    public static final int GRAND_TICKS = 79;
    /** Ливень копий: взлёт 0–24, зона с 24, копья 74–134, висит до 194, потом спуск. */
    public static final int RAIN_RISE = 24;
    public static final int RAIN_START = 74;
    public static final int RAIN_STOP = 134;
    /** После ливня улан ещё 3 с висит в воздухе. */
    public static final int RAIN_HOVER_END = RAIN_STOP + 60;
    public static final double RAIN_RADIUS = 13.0;
    public static final double RAIN_HEIGHT = 6.0;

    public static final int DUELIST_TICKS = 600;
    public static final int RETARGET_TICKS = 260;
    public static final int SHIELD_BREAK_TICKS = 140;
    public static final int RAT_TICKS = 200;
    public static final float RAT_CHANCE = 0.08f;

    private final ServerBossBar bossBar = new ServerBossBar(this.getDisplayName(), BossBar.Color.YELLOW,
            BossBar.Style.NOTCHED_10);
    private boolean phase2;
    private UUID duelist;
    private long duelistUntil;
    private int retargetTimer = RETARGET_TICKS;

    /** Только на клиенте: текущее действие, когда началось, и зона ливня копий. */
    public int clientAction = NONE;
    public int clientActionAge;
    public int clientRainAge = -10000;
    public int clientRainEndAge = -10000;
    public Vec3d clientZone = Vec3d.ZERO;

    public TatteredLancerEntity(EntityType<? extends HostileEntity> type, World world) {
        super(type, world);
        this.experiencePoints = 120;
        this.setPersistent();
    }

    public static DefaultAttributeContainer.Builder createAttributes() {
        return HostileEntity.createHostileAttributes()
                .add(EntityAttributes.GENERIC_MAX_HEALTH, 400.0)
                .add(EntityAttributes.GENERIC_MOVEMENT_SPEED, 0.26)
                .add(EntityAttributes.GENERIC_FOLLOW_RANGE, 32.0)
                .add(EntityAttributes.GENERIC_ATTACK_DAMAGE, 6.0)
                .add(EntityAttributes.GENERIC_ARMOR, 4.0)
                .add(EntityAttributes.GENERIC_KNOCKBACK_RESISTANCE, 0.7);
    }

    @Override
    protected void initGoals() {
        this.goalSelector.add(0, new SwimGoal(this));
        this.goalSelector.add(2, new DuelGoal(this));
        this.goalSelector.add(7, new WanderAroundFarGoal(this, 0.8));
        this.goalSelector.add(8, new LookAtEntityGoal(this, PlayerEntity.class, 12.0f));
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
        if (--this.retargetTimer <= 0) {
            this.retargetTimer = RETARGET_TICKS;
            this.switchTarget();
        }
    }

    /** Раз в 20 секунд — переключиться на другого игрока поблизости (если он есть). */
    private void switchTarget() {
        LivingEntity current = this.getTarget();
        List<PlayerEntity> others = new ArrayList<>();
        for (PlayerEntity p : this.getWorld().getEntitiesByClass(PlayerEntity.class, this.getBoundingBox().expand(32.0),
                p -> p.isAlive() && !p.isCreative() && !p.isSpectator() && p != current)) {
            if (this.getVisibilityCache().canSee(p)) {
                others.add(p);
            }
        }
        if (!others.isEmpty()) {
            this.setTarget(others.get(this.random.nextInt(others.size())));
        }
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
    public boolean handleFallDamage(float fallDistance, float damageMultiplier, DamageSource damageSource) {
        return false;
    }

    /** Дуэлянт бьёт улана на 20% сильнее. */
    @Override
    public boolean damage(DamageSource source, float amount) {
        if (source.getAttacker() instanceof LivingEntity attacker && attacker.hasStatusEffect(ModEffects.DUELIST)) {
            amount *= 1.2f;
        }
        boolean hurt = super.damage(source, amount);
        // «Подлая крыса»: 8% за каждый удар по улану (дуэлянту — никогда)
        if (hurt && !this.getWorld().isClient && source.getAttacker() instanceof PlayerEntity p
                && !p.hasStatusEffect(ModEffects.DUELIST) && this.random.nextFloat() < RAT_CHANCE) {
            p.addStatusEffect(new StatusEffectInstance(ModEffects.SNEAKY_RAT, RAT_TICKS, 0, false, true, true), this);
            this.sound(SoundEvents.ENTITY_SILVERFISH_AMBIENT, 1.0f, 0.7f);
        }
        return hurt;
    }

    @Override
    public void handleStatus(byte status) {
        int a = status - STATUS_BASE;
        if (a >= THRUST && a <= RAIN_END) {
            if (a == RAIN) {
                this.clientRainAge = this.age;
                this.clientRainEndAge = -10000;
                this.clientZone = this.getPos();
            } else if (a == RAIN_END) {
                this.clientRainEndAge = this.age;
            }
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

    private static final DustParticleEffect ZONE_DUST = new DustParticleEffect(new Vector3f(0.35f, 0.6f, 1.0f), 1.2f);

    /** Видна ли сейчас зона ливня копий (клиент) и насколько ярко (0..1). */
    public float clientZoneAlpha(float tickDelta) {
        float t = this.age + tickDelta - this.clientRainAge;
        if (this.clientRainAge < 0 || t < RAIN_RISE || t > RAIN_STOP + 20) {
            return 0f;
        }
        float in = Math.min(1f, (t - RAIN_RISE) / 10f);
        float out = Math.min(1f, (RAIN_STOP + 20 - t) / 20f);
        return Math.max(0f, Math.min(in, out));
    }

    /** Следы на клиенте: пыль при таране, искры в прыжке, синие искры в зоне. */
    private void clientEffects() {
        World w = this.getWorld();
        int t = this.age - this.clientActionAge;
        boolean dash = (this.clientAction == CHARGE && t >= 11 && t < 24)
                || (this.clientAction == GRAND && ((t >= 16 && t < 25) || (t >= 31 && t < 40) || (t >= 46 && t < 55)));
        if (dash) {
            w.addParticle(ParticleTypes.CLOUD, this.getX(), this.getY() + 0.1, this.getZ(),
                    (this.random.nextDouble() - 0.5) * 0.1, 0.05, (this.random.nextDouble() - 0.5) * 0.1);
            w.addParticle(ParticleTypes.CRIT, this.getParticleX(0.6), this.getBodyY(0.5), this.getParticleZ(0.6), 0, 0, 0);
        }
        if (this.clientAction == LEAP && t >= LEAP_WINDUP && !this.isOnGround()) {
            w.addParticle(ParticleTypes.CRIT, this.getParticleX(0.5), this.getBodyY(0.5), this.getParticleZ(0.5), 0, 0, 0);
            w.addParticle(ParticleTypes.END_ROD, this.getX(), this.getBodyY(0.3), this.getZ(), 0, -0.02, 0);
        }
        float zone = this.clientZoneAlpha(0f);
        if (zone > 0f) {
            int count = (this.age - this.clientRainAge) >= RAIN_START ? 6 : 3;
            for (int i = 0; i < count; i++) {
                double a = this.random.nextDouble() * Math.PI * 2;
                double r = Math.sqrt(this.random.nextDouble()) * RAIN_RADIUS;
                w.addParticle(ZONE_DUST, this.clientZone.x + Math.cos(a) * r, this.clientZone.y + 0.15,
                        this.clientZone.z + Math.sin(a) * r, 0, 0.02, 0);
            }
            double a = this.random.nextDouble() * Math.PI * 2;
            w.addParticle(ParticleTypes.SOUL_FIRE_FLAME, this.clientZone.x + Math.cos(a) * RAIN_RADIUS,
                    this.clientZone.y + 0.1, this.clientZone.z + Math.sin(a) * RAIN_RADIUS, 0, 0.03, 0);
        }
    }

    @Override
    protected SoundEvent getAmbientSound() {
        return SoundEvents.ENTITY_ZOMBIE_VILLAGER_AMBIENT;
    }

    @Override
    protected SoundEvent getHurtSound(DamageSource source) {
        return SoundEvents.ENTITY_ZOMBIE_VILLAGER_HURT;
    }

    @Override
    protected SoundEvent getDeathSound() {
        return SoundEvents.ENTITY_ZOMBIE_VILLAGER_DEATH;
    }

    @Override
    protected void playStepSound(BlockPos pos, BlockState state) {
        this.playSound(SoundEvents.ENTITY_HORSE_STEP, 0.25f, 1.6f);
    }

    // --- помощники боя (сервер) ---
    Vec3d forward() {
        float yaw = this.getYaw() * 0.017453292f;
        return new Vec3d(-MathHelper.sin(yaw), 0, MathHelper.cos(yaw));
    }

    void faceTarget(LivingEntity target) {
        double dx = target.getX() - this.getX();
        double dz = target.getZ() - this.getZ();
        float yaw = (float) (MathHelper.atan2(dz, dx) * 57.29577951308232) - 90.0f;
        this.setYaw(yaw);
        this.setBodyYaw(yaw);
        this.setHeadYaw(yaw);
    }

    void sendAction(int action) {
        this.getWorld().sendEntityStatus(this, (byte) (STATUS_BASE + action));
    }

    List<LivingEntity> victims(double radius) {
        Box box = this.getBoundingBox().expand(radius, 1.5, radius);
        return this.getWorld().getEntitiesByClass(LivingEntity.class, box,
                e -> e != this && e.isAlive() && !(e instanceof Monster) && !(e instanceof ArmorStandEntity)
                        && !(e instanceof PlayerEntity p && (p.isCreative() || p.isSpectator())));
    }

    /** Есть ли сейчас дуэлянт (эффект ещё висит на том игроке). */
    private boolean duelistActive() {
        if (this.duelist == null || this.getWorld().getTime() >= this.duelistUntil) {
            return false;
        }
        PlayerEntity p = this.getWorld().getPlayerByUuid(this.duelist);
        return p != null && p.isAlive() && p.hasStatusEffect(ModEffects.DUELIST);
    }

    /** Попадание: урон, отброс; лечение от дуэлянта; эффект «Дуэлянт» на первого, если его ни на ком нет. */
    void strike(LivingEntity e, float damage, double knock, double up, boolean multiHit) {
        if (multiHit) {
            e.timeUntilRegen = 0;
        }
        if (e.damage(this.getDamageSources().mobAttack(this), damage)) {
            boolean shielded = e instanceof ShieldBlocker sb && sb.basedefense$blockedThisTick();
            if (knock > 0 && !shielded) {
                e.takeKnockback(knock, this.getX() - e.getX(), this.getZ() - e.getZ());
            }
            if (up > 0 && !shielded) {
                e.addVelocity(0, up, 0);
            }
            e.velocityModified = true;
            if (e.hasStatusEffect(ModEffects.DUELIST)) {
                this.heal(1.0f);
            } else if (e instanceof PlayerEntity p && !this.duelistActive()) {
                p.addStatusEffect(new StatusEffectInstance(ModEffects.DUELIST, DUELIST_TICKS, 0, false, true, true), this);
                this.duelist = p.getUuid();
                this.duelistUntil = this.getWorld().getTime() + DUELIST_TICKS;
                this.sound(SoundEvents.ENTITY_EVOKER_PREPARE_ATTACK, 1.0f, 1.4f);
            }
        }
    }

    /** «Тройной таран» выбивает щит: 7 секунд им нельзя пользоваться. */
    void breakShield(LivingEntity e) {
        if (!(e instanceof PlayerEntity p)) {
            return;
        }
        Set<Item> shields = new HashSet<>();
        shields.add(Items.SHIELD);
        if (p.getMainHandStack().getItem() instanceof ShieldItem) {
            shields.add(p.getMainHandStack().getItem());
        }
        if (p.getOffHandStack().getItem() instanceof ShieldItem) {
            shields.add(p.getOffHandStack().getItem());
        }
        for (Item item : shields) {
            p.getItemCooldownManager().set(item, SHIELD_BREAK_TICKS);
        }
        if (p.isBlocking()) {
            p.clearActiveItem();
        }
        this.getWorld().sendEntityStatus(p, (byte) 30);          // звук ломающегося щита
    }

    /** Удар конусом вперёд: reach — от центра улана до края цели, halfAngle — половина угла. */
    int coneHit(double reach, double halfAngle, float damage, double knock, double up, boolean multiHit) {
        Vec3d f = this.forward();
        double cos = Math.cos(Math.toRadians(halfAngle));
        int n = 0;
        for (LivingEntity e : this.victims(reach)) {
            Vec3d to = new Vec3d(e.getX() - this.getX(), 0, e.getZ() - this.getZ());
            double dist = to.length() - e.getWidth() / 2;
            if (dist > reach) {
                continue;
            }
            if (to.lengthSquared() > 0.25 && to.normalize().dotProduct(f) < cos) {
                continue;
            }
            this.strike(e, damage, knock, up, multiHit);
            n++;
        }
        return n;
    }

    int radiusHit(double radius, float damage, double knock, double up) {
        int n = 0;
        for (LivingEntity e : this.victims(radius)) {
            double dist = Math.sqrt(this.squaredDistanceTo(e.getX(), this.getY(), e.getZ())) - e.getWidth() / 2;
            if (dist <= radius) {
                this.strike(e, damage, knock, up, false);
                n++;
            }
        }
        return n;
    }

    /** Удар по линии вперёд: длина length, ширина ±halfWidth. */
    int lineHit(double length, double halfWidth, float damage, double knock, double up, boolean breakShield) {
        Vec3d f = this.forward();
        int n = 0;
        for (LivingEntity e : this.victims(length)) {
            Vec3d to = new Vec3d(e.getX() - this.getX(), 0, e.getZ() - this.getZ());
            double along = to.dotProduct(f);
            double side = Math.abs(to.x * f.z - to.z * f.x);
            if (along > -0.5 && along < length && side < halfWidth + e.getWidth() / 2) {
                this.strike(e, damage, knock, up, false);
                if (breakShield) {
                    this.breakShield(e);
                }
                n++;
            }
        }
        return n;
    }

    /** Оттолкнуть всех рядом без урона. */
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

    ServerWorld sw() {
        return this.getWorld() instanceof ServerWorld s ? s : null;
    }

    void tipParticles(double reach) {
        ServerWorld s = this.sw();
        if (s == null) {
            return;
        }
        Vec3d p = this.getPos().add(this.forward().multiply(reach)).add(0, 1.3, 0);
        s.spawnParticles(ParticleTypes.CRIT, p.x, p.y, p.z, 8, 0.2, 0.2, 0.2, 0.3);
        s.spawnParticles(ParticleTypes.SWEEP_ATTACK, p.x, p.y, p.z, 1, 0, 0, 0, 0);
    }

    void slamParticles(double radius) {
        ServerWorld s = this.sw();
        if (s == null) {
            return;
        }
        BlockState ground = this.getWorld().getBlockState(this.getBlockPos().down());
        s.spawnParticles(ParticleTypes.EXPLOSION, this.getX(), this.getY() + 0.2, this.getZ(), 1, 0, 0, 0, 0);
        for (int i = 0; i < 24; i++) {
            double a = i / 24.0 * Math.PI * 2;
            double x = this.getX() + Math.cos(a) * radius;
            double z = this.getZ() + Math.sin(a) * radius;
            if (!ground.isAir()) {
                s.spawnParticles(new BlockStateParticleEffect(ParticleTypes.BLOCK, ground), x, this.getY() + 0.1, z, 4,
                        0.2, 0.1, 0.2, 0.15);
            }
            s.spawnParticles(ParticleTypes.CLOUD, x, this.getY() + 0.2, z, 1, 0, 0.05, 0, 0.02);
        }
    }

    void sweepParticles() {
        ServerWorld s = this.sw();
        if (s == null) {
            return;
        }
        for (int i = 0; i < 10; i++) {
            double a = i / 10.0 * Math.PI * 2;
            s.spawnParticles(ParticleTypes.SWEEP_ATTACK, this.getX() + Math.cos(a) * 2.6, this.getY() + 1.1,
                    this.getZ() + Math.sin(a) * 2.6, 1, 0, 0, 0, 0);
        }
    }

    void shockwaveParticles(double length) {
        ServerWorld s = this.sw();
        if (s == null) {
            return;
        }
        Vec3d f = this.forward();
        BlockState ground = this.getWorld().getBlockState(this.getBlockPos().down());
        for (double d = 1.0; d <= length; d += 0.75) {
            double x = this.getX() + f.x * d;
            double z = this.getZ() + f.z * d;
            s.spawnParticles(ParticleTypes.SWEEP_ATTACK, x, this.getY() + 1.0, z, 1, 0, 0, 0, 0);
            s.spawnParticles(ParticleTypes.CRIT, x, this.getY() + 1.0, z, 4, 0.3, 0.3, 0.3, 0.2);
            if (!ground.isAir()) {
                s.spawnParticles(new BlockStateParticleEffect(ParticleTypes.BLOCK, ground), x, this.getY() + 0.1, z, 6,
                        0.4, 0.1, 0.4, 0.2);
            }
        }
        s.spawnParticles(ParticleTypes.EXPLOSION, this.getX() + f.x * 1.5, this.getY() + 1.0, this.getZ() + f.z * 1.5,
                1, 0, 0, 0, 0);
    }

    void roarParticles() {
        ServerWorld s = this.sw();
        if (s == null) {
            return;
        }
        s.spawnParticles(ParticleTypes.ENCHANTED_HIT, this.getX(), this.getBodyY(0.6), this.getZ(), 60, 1.2, 0.8, 1.2, 0.4);
        s.spawnParticles(ParticleTypes.LARGE_SMOKE, this.getX(), this.getY() + 0.2, this.getZ(), 30, 1.5, 0.1, 1.5, 0.05);
    }

    boolean isPhase2() {
        return this.phase2;
    }

    void enterPhase2() {
        this.phase2 = true;
    }

    /** Бой: выбор атаки по расстоянию, тайминги ударов, рывки и прыжки. */
    static class DuelGoal extends Goal {
        private final TatteredLancerEntity lancer;
        private int action = NONE;
        private int tick;
        private int cooldown = 20;
        private int queued = NONE;
        private int lastAction = NONE;
        private int pathDelay;
        private int circleTimer;
        private boolean circleLeft;
        private int rainCooldown = 200;
        private int grandCooldown = 100;
        private Vec3d dashDir = Vec3d.ZERO;
        private Vec3d leapVel = Vec3d.ZERO;
        private Vec3d zone = Vec3d.ZERO;
        private final Set<Integer> hitIds = new HashSet<>();

        DuelGoal(TatteredLancerEntity lancer) {
            this.lancer = lancer;
            this.setControls(EnumSet.of(Control.MOVE, Control.LOOK, Control.JUMP));
        }

        private static boolean valid(LivingEntity t) {
            return t != null && t.isAlive() && !(t instanceof PlayerEntity p && (p.isCreative() || p.isSpectator()));
        }

        @Override
        public boolean canStart() {
            return valid(this.lancer.getTarget());
        }

        @Override
        public boolean shouldContinue() {
            return this.action != NONE || valid(this.lancer.getTarget());
        }

        @Override
        public boolean shouldRunEveryTick() {
            return true;
        }

        @Override
        public void start() {
            this.lancer.setAttacking(true);
        }

        @Override
        public void stop() {
            this.lancer.setAttacking(false);
            this.lancer.getNavigation().stop();
            this.lancer.setNoGravity(false);
            this.action = NONE;
        }

        private void begin(int a) {
            this.action = a;
            this.tick = 0;
            this.hitIds.clear();
            this.lancer.getNavigation().stop();
            this.lancer.sendAction(a);
        }

        private void finish(int cooldownTicks) {
            this.lastAction = this.action;
            this.action = NONE;
            this.cooldown = this.lancer.isPhase2() ? cooldownTicks / 2 : cooldownTicks;
        }

        private int pick(double dist, int nearby) {
            List<int[]> options = new ArrayList<>();
            if (dist < 3.8) {
                options.add(new int[]{THRUST, 35});
                options.add(new int[]{SWEEP, nearby >= 2 ? 45 : 22});
                options.add(new int[]{UPPERCUT, 22});
                options.add(new int[]{BACKSTEP, 18});
            } else if (dist < 10) {
                options.add(new int[]{CHARGE, 45});
                options.add(new int[]{LEAP, 35});
                if (dist < 5) {
                    options.add(new int[]{THRUST, 20});
                }
            } else {
                options.add(new int[]{LEAP, dist < 18 ? 55 : 10});
                options.add(new int[]{CHARGE, 35});
            }
            if (this.rainCooldown <= 0 && dist < 14) {
                options.add(new int[]{RAIN, 30});
            }
            if (this.lancer.isPhase2() && this.grandCooldown <= 0 && dist > 3 && dist < 16) {
                options.add(new int[]{GRAND, 40});
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
            int r = this.lancer.random.nextInt(total);
            for (int[] o : options) {
                r -= o[1];
                if (r < 0) {
                    return o[0];
                }
            }
            return options.get(0)[0];
        }

        @Override
        public void tick() {
            if (this.rainCooldown > 0) {
                this.rainCooldown--;
            }
            if (this.grandCooldown > 0) {
                this.grandCooldown--;
            }
            LivingEntity target = this.lancer.getTarget();
            boolean hasTarget = valid(target);
            if (this.action != NONE) {
                this.tick++;
                this.runAction(hasTarget ? target : null);
                return;
            }
            if (!hasTarget) {
                return;
            }
            double dist = this.lancer.distanceTo(target);
            this.lancer.getLookControl().lookAt(target, 30.0f, 30.0f);

            if (!this.lancer.isPhase2() && this.lancer.getHealth() <= this.lancer.getMaxHealth() * 0.5f) {
                this.lancer.enterPhase2();
                this.lancer.faceTarget(target);
                this.grandCooldown = 60;
                this.begin(ROAR);
                return;
            }
            if (this.queued != NONE && this.cooldown <= 3) {
                int q = this.queued;
                this.queued = NONE;
                this.lancer.faceTarget(target);
                this.begin(q);
                return;
            }
            if (this.cooldown > 0) {
                this.cooldown--;
                this.maneuver(target, dist);
                return;
            }
            if (!this.lancer.getVisibilityCache().canSee(target) || dist > 24) {
                this.maneuver(target, dist);
                return;
            }
            int a = this.pick(dist, this.lancer.victims(4.0).size());
            if (a == NONE) {
                this.maneuver(target, dist);
                return;
            }
            this.lancer.faceTarget(target);
            this.begin(a);
        }

        /** Между атаками: бег к цели или кружение рядом. */
        private void maneuver(LivingEntity target, double dist) {
            if (dist > 3.2 || !this.lancer.getVisibilityCache().canSee(target)) {
                if (--this.pathDelay <= 0) {
                    this.pathDelay = 6;
                    this.lancer.getNavigation().startMovingTo(target, dist > 6 ? 1.35 : 1.0);
                }
            } else {
                this.lancer.getNavigation().stop();
                if (--this.circleTimer <= 0) {
                    this.circleTimer = 20 + this.lancer.random.nextInt(30);
                    this.circleLeft = this.lancer.random.nextBoolean();
                }
                this.lancer.getMoveControl().strafeTo(-0.2f, this.circleLeft ? 0.5f : -0.5f);
                this.lancer.lookAtEntity(target, 30.0f, 30.0f);
            }
        }

        private void dashHits(float damage, double knock, double up, boolean breakShield) {
            TatteredLancerEntity l = this.lancer;
            for (LivingEntity e : l.victims(1.6)) {
                Vec3d to = new Vec3d(e.getX() - l.getX(), 0, e.getZ() - l.getZ());
                if (to.lengthSquared() < 0.01 || to.normalize().dotProduct(this.dashDir) > 0.2) {
                    if (this.hitIds.add(e.getId())) {
                        l.strike(e, damage, knock, up, false);
                        if (breakShield) {
                            l.breakShield(e);
                        }
                        l.sound(SoundEvents.ENTITY_PLAYER_ATTACK_KNOCKBACK, 1.2f, 0.7f);
                    }
                }
            }
        }

        private void dash(double speed) {
            TatteredLancerEntity l = this.lancer;
            l.setVelocity(this.dashDir.x * speed, l.getVelocity().y, this.dashDir.z * speed);
            l.velocityModified = true;
        }

        private void landNow(float damage) {
            TatteredLancerEntity l = this.lancer;
            this.action = LAND;
            this.tick = 0;
            l.setNoGravity(false);
            l.setVelocity(0, l.getVelocity().y, 0);
            l.sendAction(LAND);
            if (damage > 0) {
                l.radiusHit(3.2, damage, 0.9, 0.6);
            }
            l.slamParticles(2.6);
            l.sound(SoundEvents.ENTITY_GENERIC_EXPLODE, 0.6f, 1.4f);
            l.sound(SoundEvents.ITEM_TRIDENT_HIT_GROUND, 1.2f, 0.8f);
        }

        private void runAction(LivingEntity target) {
            TatteredLancerEntity l = this.lancer;
            int t = this.tick;
            switch (this.action) {
                case THRUST -> {
                    if (target != null && t < 18) {
                        l.faceTarget(target);
                    }
                    if (t == 6 || t == 12 || t == 20) {
                        Vec3d f = l.forward().multiply(t == 20 ? 0.45 : 0.25);
                        l.addVelocity(f.x, 0, f.z);
                        l.coneHit(3.8, 35, t == 20 ? 7f : 5f, t == 20 ? 0.8 : 0.3, 0, true);
                        l.tipParticles(3.0);
                        l.sound(SoundEvents.ENTITY_PLAYER_ATTACK_STRONG, 1.0f, t == 20 ? 0.8f : 1.2f);
                    }
                    if (t >= THRUST_TICKS) {
                        this.finish(18);
                    }
                }
                case CHARGE -> {
                    if (t < 11 && target != null) {
                        l.faceTarget(target);
                    }
                    if (t == 2) {
                        l.sound(SoundEvents.ITEM_TRIDENT_RIPTIDE_1, 1.0f, 0.8f);
                    }
                    if (t == 11) {
                        this.dashDir = l.forward();
                    }
                    if (t >= 11 && t < 24) {
                        this.dash(0.78);
                        if (t % 3 == 0) {
                            l.sound(SoundEvents.ENTITY_HORSE_GALLOP, 0.8f, 1.2f);
                        }
                        this.dashHits(12f, 1.6, 0.45, false);
                        if (l.horizontalCollision && t > 13) {
                            this.tick = 24;
                        }
                    }
                    if (t >= 24 && t < 30) {
                        Vec3d v = l.getVelocity();
                        l.setVelocity(v.x * 0.6, v.y, v.z * 0.6);
                    }
                    if (t >= CHARGE_TICKS) {
                        this.finish(24);
                    }
                }
                case LEAP -> {
                    if (t < LEAP_WINDUP && target != null) {
                        l.faceTarget(target);
                    }
                    if (t == LEAP_WINDUP) {
                        Vec3d aim = target != null ? target.getPos().add(target.getVelocity().multiply(10)) : l.getPos();
                        Vec3d d = new Vec3d(aim.x - l.getX(), 0, aim.z - l.getZ());
                        double speed = Math.min(1.0, d.length() / 24.0);
                        this.leapVel = d.lengthSquared() > 0.01 ? d.normalize().multiply(speed) : Vec3d.ZERO;
                        l.setVelocity(this.leapVel.x, 1.0, this.leapVel.z);
                        l.velocityModified = true;
                        l.sound(SoundEvents.ITEM_TRIDENT_RIPTIDE_1, 1.2f, 1.1f);
                        ServerWorld s = l.sw();
                        if (s != null) {
                            s.spawnParticles(ParticleTypes.CLOUD, l.getX(), l.getY() + 0.1, l.getZ(), 16, 0.6, 0.05, 0.6, 0.05);
                        }
                    }
                    if (t > LEAP_WINDUP) {
                        Vec3d v = l.getVelocity();
                        l.setVelocity(this.leapVel.x, v.y, this.leapVel.z);
                        if (target != null) {
                            l.faceTarget(target);
                        }
                        if ((l.isOnGround() && t > LEAP_WINDUP + 3) || t > LEAP_WINDUP + 60) {
                            this.landNow(11f);
                        }
                    }
                }
                case LAND -> {
                    if (t >= LAND_TICKS) {
                        this.finish(22);
                    }
                }
                case SWEEP -> {
                    if (t == 8) {
                        l.sound(SoundEvents.ENTITY_PLAYER_ATTACK_SWEEP, 1.2f, 0.7f);
                    }
                    if (t == 12) {
                        l.radiusHit(3.8, 8f, 1.3, 0.2);
                        l.sweepParticles();
                    }
                    if (t >= SWEEP_TICKS) {
                        this.finish(20);
                    }
                }
                case UPPERCUT -> {
                    // «вихревой подъём»: закрутка 0–7, взлёт с оборотом 8–12 (удар на 12), приземление
                    if (t < 8 && target != null) {
                        l.faceTarget(target);
                    }
                    if (t == 6) {
                        l.sound(SoundEvents.ENTITY_PLAYER_ATTACK_SWEEP, 1.0f, 1.3f);
                    }
                    if (t == 8) {
                        Vec3d f = l.forward().multiply(0.25);
                        l.setVelocity(f.x, 0.55, f.z);
                        l.velocityModified = true;
                    }
                    if (t == 12) {
                        int n = l.coneHit(3.5, 50, 9f, 0.3, 1.0, false);
                        l.sound(SoundEvents.ITEM_TRIDENT_THROW, 1.2f, 0.8f);
                        ServerWorld s = l.sw();
                        if (s != null) {
                            Vec3d p = l.getPos().add(l.forward().multiply(1.8));
                            s.spawnParticles(ParticleTypes.CLOUD, p.x, p.y + 0.3, p.z, 12, 0.4, 0.8, 0.4, 0.06);
                            s.spawnParticles(ParticleTypes.CRIT, p.x, p.y + 1.5, p.z, 16, 0.4, 1.0, 0.4, 0.4);
                            s.spawnParticles(ParticleTypes.SWEEP_ATTACK, l.getX(), l.getY() + 1.4, l.getZ(), 3, 0.8, 0.3, 0.8, 0);
                        }
                        if (n > 0 && (l.isPhase2() || l.random.nextFloat() < 0.7f)) {
                            this.queued = LEAP;
                        }
                    }
                    if (t >= UPPERCUT_TICKS) {
                        this.finish(this.queued == LEAP ? 2 : 20);
                    }
                }
                case BACKSTEP -> {
                    if (t == 1) {
                        if (target != null) {
                            l.faceTarget(target);
                        }
                        Vec3d b = l.forward().multiply(-0.95);
                        l.setVelocity(b.x, 0.42, b.z);
                        l.velocityModified = true;
                        l.sound(SoundEvents.ENTITY_PHANTOM_FLAP, 1.0f, 1.3f);
                    }
                    if (t >= BACKSTEP_TICKS) {
                        if (l.isPhase2() || l.random.nextFloat() < 0.6f) {
                            this.queued = CHARGE;
                        }
                        this.finish(this.queued == CHARGE ? 2 : 12);
                    }
                }
                case ROAR -> {
                    if (t == 19) {
                        l.sound(SoundEvents.ENTITY_RAVAGER_ROAR, 1.5f, 1.3f);
                        l.roarParticles();
                        l.push(3.5, 1.6, 0.35);
                    }
                    if (t >= ROAR_TICKS) {
                        this.finish(6);
                    }
                }
                case GRAND -> this.runGrand(target);
                case RAIN -> this.runRain(target);
                case RAIN_END -> {
                    Vec3d v = l.getVelocity();
                    l.setVelocity(0, Math.max(v.y, -0.35), 0);
                    if (target != null) {
                        l.faceTarget(target);
                    }
                    if ((l.isOnGround() && t > 2) || t > 100) {
                        this.landNow(0f);
                    }
                }
                default -> this.finish(10);
            }
        }

        /** «Тройной таран»: рывки 16–25, 31–40, 46–55 с разворотами, финальный выпад с волной на 68. */
        private void runGrand(LivingEntity target) {
            TatteredLancerEntity l = this.lancer;
            int t = this.tick;
            if (t == 1) {
                l.sound(SoundEvents.ENTITY_RAVAGER_ROAR, 1.2f, 1.6f);
                ServerWorld s = l.sw();
                if (s != null) {
                    s.spawnParticles(ParticleTypes.ENCHANTED_HIT, l.getX(), l.getBodyY(0.8), l.getZ(), 30, 0.4, 0.8, 0.4, 0.3);
                }
            }
            boolean aim = t < 16 || (t >= 26 && t < 31) || (t >= 41 && t < 46) || (t >= 56 && t < 68);
            if (aim && target != null) {
                l.faceTarget(target);
            }
            if (t == 16 || t == 31 || t == 46) {
                this.dashDir = l.forward();
                this.hitIds.clear();
                l.sound(SoundEvents.ITEM_TRIDENT_RIPTIDE_1, 1.1f, 0.9f + t / 100f);
            }
            boolean dashing = (t >= 16 && t < 25) || (t >= 31 && t < 40) || (t >= 46 && t < 55);
            if (dashing) {
                this.dash(1.0);
                if (t % 3 == 0) {
                    l.sound(SoundEvents.ENTITY_HORSE_GALLOP, 0.9f, 1.3f);
                }
                this.dashHits(10.5f, 1.4, 0.4, true);
            } else if (t >= 25 && t < 68) {
                Vec3d v = l.getVelocity();
                l.setVelocity(v.x * 0.5, v.y, v.z * 0.5);
            }
            if (t == 68) {
                Vec3d f = l.forward().multiply(0.6);
                l.addVelocity(f.x, 0, f.z);
                l.lineHit(12.0, 1.6, 13.5f, 2.0, 0.5, true);
                l.shockwaveParticles(12.0);
                l.sound(SoundEvents.ENTITY_GENERIC_EXPLODE, 0.8f, 0.9f);
                l.sound(SoundEvents.ENTITY_PLAYER_ATTACK_STRONG, 1.5f, 0.6f);
            }
            if (t >= GRAND_TICKS) {
                this.grandCooldown = 300;
                this.finish(30);
            }
        }

        /** «Ливень копий»: взлёт 0–24, зона и замедление, копья 74–134, затем спуск. */
        private void runRain(LivingEntity target) {
            TatteredLancerEntity l = this.lancer;
            int t = this.tick;
            if (t == 1) {
                this.zone = l.getPos();
                l.setNoGravity(true);
                l.sound(SoundEvents.ITEM_TRIDENT_RIPTIDE_1, 1.2f, 0.7f);
            }
            if (target != null) {
                l.faceTarget(target);
            }
            double goalY = this.zone.y + RAIN_HEIGHT;
            double vy = MathHelper.clamp((goalY - l.getY()) * 0.25, -0.2, 0.6);
            l.setVelocity(0, t < 6 ? 0 : vy, 0);
            l.velocityModified = true;
            if (t == RAIN_RISE) {
                l.sound(SoundEvents.BLOCK_BEACON_ACTIVATE, 1.5f, 1.4f);
            }
            if (t >= RAIN_RISE && t < RAIN_STOP + 20 && t % 5 == 0) {
                for (PlayerEntity p : l.getWorld().getEntitiesByClass(PlayerEntity.class,
                        new Box(this.zone, this.zone).expand(RAIN_RADIUS, 3.0, RAIN_RADIUS), p -> p.isAlive() && !p.isSpectator())) {
                    if (p.squaredDistanceTo(this.zone.x, p.getY(), this.zone.z) <= RAIN_RADIUS * RAIN_RADIUS) {
                        p.addStatusEffect(new StatusEffectInstance(StatusEffects.SLOWNESS, 12, 0, false, true, true), l);
                    }
                }
            }
            if (t == RAIN_START - 10) {
                l.sound(SoundEvents.ITEM_TRIDENT_THUNDER, 1.0f, 1.5f);
            }
            if (t >= RAIN_START && t < RAIN_STOP && t % 3 == 0) {
                this.spawnLances(target);
            }
            if (t >= RAIN_HOVER_END) {
                this.action = RAIN_END;
                this.tick = 0;
                l.setNoGravity(false);
                l.sendAction(RAIN_END);
                this.rainCooldown = 400;
            }
        }

        /** Группа из 3 призрачных копий: чаще рядом с игроками в зоне, иначе в случайном месте зоны. */
        private void spawnLances(LivingEntity target) {
            TatteredLancerEntity l = this.lancer;
            List<LivingEntity> inZone = new ArrayList<>();
            for (LivingEntity e : l.getWorld().getEntitiesByClass(LivingEntity.class,
                    new Box(this.zone, this.zone).expand(RAIN_RADIUS, 4.0, RAIN_RADIUS),
                    e -> e.isAlive() && !(e instanceof Monster) && !(e instanceof ArmorStandEntity))) {
                if (e.squaredDistanceTo(this.zone.x, e.getY(), this.zone.z) <= RAIN_RADIUS * RAIN_RADIUS) {
                    inZone.add(e);
                }
            }
            double cx;
            double cz;
            if (!inZone.isEmpty() && l.random.nextFloat() < 0.6f) {
                LivingEntity e = inZone.get(l.random.nextInt(inZone.size()));
                cx = e.getX() + (l.random.nextDouble() - 0.5) * 3.0;
                cz = e.getZ() + (l.random.nextDouble() - 0.5) * 3.0;
            } else {
                double a = l.random.nextDouble() * Math.PI * 2;
                double r = Math.sqrt(l.random.nextDouble()) * RAIN_RADIUS;
                cx = this.zone.x + Math.cos(a) * r;
                cz = this.zone.z + Math.sin(a) * r;
            }
            for (int i = 0; i < 3; i++) {
                double x = cx + (l.random.nextDouble() - 0.5) * 2.4;
                double z = cz + (l.random.nextDouble() - 0.5) * 2.4;
                double y = this.zone.y + 13 + l.random.nextDouble() * 4;
                FallingLanceEntity.drop(l.getWorld(), l, x, y, z);
            }
        }
    }
}

package com.lesha_top1972.basedefense.entity.enemy.thunder_tiger;

import com.lesha_top1972.basedefense.combat.ShieldBlocker;
import com.lesha_top1972.basedefense.effect.BossEffectsSync;
import com.lesha_top1972.basedefense.effect.ModEffects;
import com.lesha_top1972.basedefense.fx.Fx;
import com.lesha_top1972.basedefense.fx.Shake;
import com.lesha_top1972.basedefense.sound.ModSounds;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.ai.goal.LookAroundGoal;
import net.minecraft.entity.ai.goal.LookAtEntityGoal;
import net.minecraft.entity.ai.goal.SwimGoal;
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
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.Item;
import net.minecraft.item.Items;
import net.minecraft.item.ShieldItem;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.registry.tag.DamageTypeTags;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvent;
import net.minecraft.text.Text;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Босс «Громовой тигр». Стиль боя — вальяжность и мгновенные резкие атаки: тесак‑ганблейд с барабаном на 6 патронов
 * (удар с патроном — выстрел из выхлопов: взрыв, +30% урона, поджог), «Дрожь» на целях и её разрыв, внутренняя сила
 * (копится от полученного урона, ульта тратит всё), «Добыча», шкала стойки и её слом, фазы, финальный шквал.
 * <p>
 * Здесь — состояние, урон по нему, действия (удары по тикам из {@link TigerTimings}). Выбор атак и движение между
 * ними — {@link TigerBrain}. Модель и эффекты на клиенте — client/entity/enemy/thunder_tiger.
 * Реплики ещё не написаны: {@link #speak(String)} — заготовка, тексты пришлёт автор мода.
 */
public class ThunderTigerEntity extends HostileEntity {

    // ===================================================================== баланс (стартовые цифры)
    public static final double MAX_HP = 3450.0;
    public static final double ARMOR = 8.0;
    public static final double SPEED = 0.30;
    public static final double FOLLOW = 48.0;
    /** Урон обычного разреза. (Все удары ослаблены втрое по просьбе автора, 03.11.2026.) */
    static final float DMG_SLASH = 4.1f;
    /** Тяжёлые удары (сверху двумя руками, «Сокрушающий разрыв»). */
    static final float DMG_HEAVY = 5.12f;
    /** Удар в землю «Двойного разреза — взрыв». */
    static final float DMG_SMASH = 4.61f;
    /** Огненная волна ульты. */
    static final float DMG_WAVE = 5.12f;
    /** Удар в землю ульты (в центре). */
    static final float DMG_TANGLE = 5.8f;
    /** «Один удар» (неблокируемый). */
    static final float DMG_ONE = 9.22f;
    static final float DMG_COUNTER = 5.8f;
    static final float DMG_FLURRY = 1.36f;
    static final float DMG_FLURRY_END = 8.01f;
    static final float DMG_KICK = 2.9f;
    /** «Тигриный выпад» (укол с выпадом). */
    static final float DMG_THRUST = 5.05f;
    /** «Вихрь» (оборот на месте). */
    static final float DMG_SPIN = 4.61f;
    /** «Восходящий разрез». */
    static final float DMG_RISING = 4.61f;
    /** «Залп выхлопов» — каждый из двух выстрелов. */
    static final float DMG_BURST = 3.86f;
    static final float DMG_BACKSHOT = 3.41f;
    /** Выстрел: обычный патрон +30%, свирепый +50%; поджог 3 / 6 с. */
    static final float SHOT_MUL = 1.3f;
    static final float SAVAGE_MUL = 1.5f;
    static final int SHOT_FIRE = 2;
    static final int SAVAGE_FIRE = 4;
    public static final int MAX_AMMO = 6;
    /** Стойка: 0..115; удар игрока даёт урон × 0.6, блок щитом его удара — +5; падает после 3 с без ударов
     *  (3.6 в секунду), за уклонение −5. Остыла до 0 после того, как была ≥ 10, — тигр лечится на 6% (не чаще раза в 20 с). */
    public static final float POSTURE_MAX = 115f;
    static final float POSTURE_PER_DMG = 0.6f;
    static final float POSTURE_SHIELD = 5f;
    static final int POSTURE_DELAY = 60;
    static final float POSTURE_DECAY = 0.18f;
    static final float RECOVER_HEAL = 0.06f;
    static final int RECOVER_CD = 400;
    static final float RECOVER_MIN = 10f;
    /** Во время слома стойки урон по нему ×1.5. */
    static final float STAGGER_TAKEN = 1.5f;
    /** Внутренняя сила: +1 за каждые 30 полученного урона, максимум 25, аура с 10. */
    public static final int INNER_MAX = 25;
    public static final int INNER_AURA = 10;
    static final float INNER_PER = 38f;
    /** Дрожь: стаки до 10, держится 8 с; разрыв — 0.5 × стаки, оглушение 0.4 с, стаки −3. */
    static final int TREMOR_MAX = 10;
    static final int TREMOR_TICKS = 160;
    static final float BURST_PER = 0.85f;
    static final int BURST_STUN = 8;
    /** Кольца силы (фаза 2): каждое +4% урона; слом стойки гасит одно. */
    public static final int RINGS_MAX = 5;
    static final float RING_BONUS = 0.04f;
    /** До финала здоровье не опускается ниже 10%. */
    static final float HP_FLOOR = 0.10f;
    /** Огненная волна: растёт на 0.55 бл/тик до 10 блоков; перепрыгнуть — быть в воздухе выше 0.5 бл. */
    static final double WAVE_SPEED = 0.5;
    static final double WAVE_MAX = 10.0;
    /** Шквал: число рывков. */
    static final int FLURRY_DASHES = 12;

    public static final int NONE = -1;
    public static final byte STATUS_BASE = 90;

    // --- DataTracker ---
    private static final TrackedData<Integer> AMMO = DataTracker.registerData(ThunderTigerEntity.class, TrackedDataHandlerRegistry.INTEGER);
    private static final TrackedData<Boolean> SAVAGE = DataTracker.registerData(ThunderTigerEntity.class, TrackedDataHandlerRegistry.BOOLEAN);
    /** 1 — первая фаза, 2 — вторая, 3 — финал. */
    private static final TrackedData<Integer> PHASE = DataTracker.registerData(ThunderTigerEntity.class, TrackedDataHandlerRegistry.INTEGER);
    private static final TrackedData<Integer> INNER = DataTracker.registerData(ThunderTigerEntity.class, TrackedDataHandlerRegistry.INTEGER);
    private static final TrackedData<Float> POSTURE = DataTracker.registerData(ThunderTigerEntity.class, TrackedDataHandlerRegistry.FLOAT);
    /** Свечение клинка: 0 — нет, 1 — золото (неостановимый удар), 2 — пламя (заряд ульты). */
    private static final TrackedData<Integer> GLOW = DataTracker.registerData(ThunderTigerEntity.class, TrackedDataHandlerRegistry.INTEGER);
    private static final TrackedData<Integer> RINGS = DataTracker.registerData(ThunderTigerEntity.class, TrackedDataHandlerRegistry.INTEGER);
    /** Вальяжный режим (тесак на плече, курит, ходит вразвалку) — для выбора анимаций. */
    private static final TrackedData<Boolean> LAZY = DataTracker.registerData(ThunderTigerEntity.class, TrackedDataHandlerRegistry.BOOLEAN);

    // --- клиент ---
    public int clientAction = NONE;
    public int clientActionAge;
    /** Точки клинка в мире (считает отрисовка тесака каждый кадр) — для частиц. */
    public Vec3d bladeTip = Vec3d.ZERO;
    public Vec3d bladeMid = Vec3d.ZERO;
    public Vec3d bladeBase = Vec3d.ZERO;
    public Vec3d bladeExhaust = Vec3d.ZERO;
    public Vec3d cigarTip = Vec3d.ZERO;
    public int bladeAge = -100;
    public int fxAge = -1;
    public Vec3d prevBladeTip = Vec3d.ZERO;

    // --- сервер ---
    private final ServerBossBar bossBar = new ServerBossBar(this.getDisplayName(), BossBar.Color.YELLOW, BossBar.Style.NOTCHED_10);
    private final ServerBossBar postureBar = new ServerBossBar(Text.translatable("boss.basedefense.thunder_tiger.posture"),
            BossBar.Color.WHITE, BossBar.Style.NOTCHED_10);
    final TigerBrain brain = new TigerBrain(this);
    int action = NONE;
    int actionTick;
    LivingEntity actionTarget;
    final Set<Integer> hitIds = new HashSet<>();
    int breaks;
    private float innerAcc;
    private long lastPostureGain;
    /** Стойка была заполнена хотя бы на RECOVER_MIN с последнего лечения/слома. */
    private boolean postureArmed;
    private int recoverCd;
    private final Map<UUID, Float> damageLog = new HashMap<>();
    UUID prey;
    private DamageSource deathSource;
    boolean struckDuringWait;
    // ульта: огненная волна
    Vec3d waveCenter;
    double waveRadius;
    float waveDamage;
    final Set<Integer> waveHit = new HashSet<>();
    // шквал
    int flurryLeft;
    int flurryPause;
    Vec3d dashDir = Vec3d.ZERO;
    double dashSpeed;
    Vec3d dashStart = Vec3d.ZERO;
    // арена: потеряв 20% здоровья, утаскивает игроков к себе (TigerArena)
    /** Доля здоровья, при которой тигр утаскивает игроков на арену. */
    static final float ARENA_AT = 0.8f;
    boolean arenaDone;
    boolean pendingArena;
    int arenaCooldown;
    String originDim;
    Vec3d originPos;

    public ThunderTigerEntity(EntityType<? extends HostileEntity> type, World world) {
        super(type, world);
        this.experiencePoints = 150;
        this.setPersistent();
        this.setStepHeight(1.0f);
    }

    public static DefaultAttributeContainer.Builder createAttributes() {
        return HostileEntity.createHostileAttributes()
                .add(EntityAttributes.GENERIC_MAX_HEALTH, MAX_HP)
                .add(EntityAttributes.GENERIC_MOVEMENT_SPEED, SPEED)
                .add(EntityAttributes.GENERIC_FOLLOW_RANGE, FOLLOW)
                .add(EntityAttributes.GENERIC_ATTACK_DAMAGE, DMG_SLASH)
                .add(EntityAttributes.GENERIC_ARMOR, ARMOR)
                .add(EntityAttributes.GENERIC_KNOCKBACK_RESISTANCE, 0.85);
    }

    @Override
    protected void initDataTracker() {
        super.initDataTracker();
        this.dataTracker.startTracking(AMMO, MAX_AMMO);
        this.dataTracker.startTracking(SAVAGE, false);
        this.dataTracker.startTracking(PHASE, 1);
        this.dataTracker.startTracking(INNER, 0);
        this.dataTracker.startTracking(POSTURE, 0f);
        this.dataTracker.startTracking(GLOW, 0);
        this.dataTracker.startTracking(RINGS, 0);
        this.dataTracker.startTracking(LAZY, true);
    }

    @Override
    protected void initGoals() {
        this.goalSelector.add(0, new SwimGoal(this));
        this.goalSelector.add(1, new TigerBrain.CombatGoal(this));
        this.goalSelector.add(8, new LookAtEntityGoal(this, PlayerEntity.class, 16.0f));
        this.goalSelector.add(9, new LookAroundGoal(this));
        this.targetSelector.add(1, new TigerBrain.PreyTargetGoal(this));
    }

    // ===================================================================== доступ
    public int getAmmo() {
        return this.dataTracker.get(AMMO);
    }

    void setAmmo(int v) {
        this.dataTracker.set(AMMO, MathHelper.clamp(v, 0, MAX_AMMO));
    }

    public boolean isSavage() {
        return this.dataTracker.get(SAVAGE);
    }

    void setSavage(boolean v) {
        this.dataTracker.set(SAVAGE, v);
    }

    public int getPhase() {
        return this.dataTracker.get(PHASE);
    }

    void setPhase(int p) {
        this.dataTracker.set(PHASE, p);
    }

    public boolean isFinal() {
        return this.getPhase() >= 3;
    }

    public int getInner() {
        return this.dataTracker.get(INNER);
    }

    void setInner(int v) {
        this.dataTracker.set(INNER, MathHelper.clamp(v, 0, INNER_MAX));
    }

    public float getPosture() {
        return this.dataTracker.get(POSTURE);
    }

    void setPosture(float v) {
        this.dataTracker.set(POSTURE, MathHelper.clamp(v, 0f, POSTURE_MAX));
    }

    public int getGlow() {
        return this.dataTracker.get(GLOW);
    }

    void setGlow(int g) {
        if (this.getGlow() != g) {
            this.dataTracker.set(GLOW, g);
        }
    }

    public int getRings() {
        return this.dataTracker.get(RINGS);
    }

    void setRings(int r) {
        this.dataTracker.set(RINGS, MathHelper.clamp(r, 0, RINGS_MAX));
    }

    public boolean isLazy() {
        return this.dataTracker.get(LAZY);
    }

    void setLazy(boolean v) {
        if (this.isLazy() != v) {
            this.dataTracker.set(LAZY, v);
        }
    }

    public int getAction() {
        return this.action;
    }

    // ===================================================================== полоски босса
    @Override
    public void onStartedTrackingBy(ServerPlayerEntity player) {
        super.onStartedTrackingBy(player);
        this.bossBar.addPlayer(player);
        this.postureBar.addPlayer(player);
    }

    @Override
    public void onStoppedTrackingBy(ServerPlayerEntity player) {
        super.onStoppedTrackingBy(player);
        this.bossBar.removePlayer(player);
        this.postureBar.removePlayer(player);
    }

    @Override
    public void setCustomName(Text name) {
        super.setCustomName(name);
        this.bossBar.setName(this.getDisplayName());
    }

    // ===================================================================== NBT
    @Override
    public void writeCustomDataToNbt(NbtCompound nbt) {
        super.writeCustomDataToNbt(nbt);
        nbt.putInt("Ammo", this.getAmmo());
        nbt.putBoolean("Savage", this.isSavage());
        nbt.putInt("Phase", this.getPhase());
        nbt.putInt("Inner", this.getInner());
        nbt.putInt("Rings", this.getRings());
        nbt.putInt("Breaks", this.breaks);
        nbt.putBoolean("ArenaDone", this.arenaDone);
        if (this.originDim != null && this.originPos != null) {
            nbt.putString("OriginDim", this.originDim);
            nbt.putDouble("OriginX", this.originPos.x);
            nbt.putDouble("OriginY", this.originPos.y);
            nbt.putDouble("OriginZ", this.originPos.z);
        }
    }

    @Override
    public void readCustomDataFromNbt(NbtCompound nbt) {
        super.readCustomDataFromNbt(nbt);
        if (nbt.contains("Ammo")) {
            this.setAmmo(nbt.getInt("Ammo"));
        }
        this.dataTracker.set(SAVAGE, nbt.getBoolean("Savage"));
        this.setPhase(Math.max(1, nbt.getInt("Phase")));
        this.setInner(nbt.getInt("Inner"));
        this.setRings(nbt.getInt("Rings"));
        this.breaks = nbt.getInt("Breaks");
        this.arenaDone = nbt.getBoolean("ArenaDone");
        if (nbt.contains("OriginDim")) {
            this.originDim = nbt.getString("OriginDim");
            this.originPos = new Vec3d(nbt.getDouble("OriginX"), nbt.getDouble("OriginY"), nbt.getDouble("OriginZ"));
        }
        if (this.hasCustomName()) {
            this.bossBar.setName(this.getDisplayName());
        }
    }

    // ===================================================================== статусы (клиент)
    void sendAction(int a) {
        this.getWorld().sendEntityStatus(this, (byte) (STATUS_BASE + a));
    }

    @Override
    public void handleStatus(byte status) {
        int a = status - STATUS_BASE;
        if (a >= 0 && a < TigerTimings.NAMES.length) {
            this.clientAction = a;
            this.clientActionAge = this.age;
        } else {
            super.handleStatus(status);
        }
    }

    // ===================================================================== тик
    @Override
    protected void mobTick() {
        super.mobTick();
        this.bossBar.setPercent(this.getHealth() / this.getMaxHealth());
        this.postureBar.setPercent(this.getPosture() / POSTURE_MAX);
        this.postureBar.setColor(this.action == TigerTimings.STAGGER ? BossBar.Color.RED : BossBar.Color.WHITE);
        BossEffectsSync.tick(this, this.bossBar);
        if (this.age % 10 == 0) {
            this.syncMarkers();
        }
        if (this.age % 20 == 0) {
            this.updatePrey();
        }
        // стойка остывает, если его давно не били
        if (this.recoverCd > 0) {
            this.recoverCd--;
        }
        if (this.getPosture() >= RECOVER_MIN) {
            this.postureArmed = true;
        }
        if (this.getWorld().getTime() - this.lastPostureGain > POSTURE_DELAY && this.action != TigerTimings.STAGGER
                && this.getPosture() > 0) {
            this.setPosture(this.getPosture() - POSTURE_DECAY);
            // стойку долго не заполняли — остыла до нуля: затяжка сигарой, лечение 6%
            if (this.getPosture() <= 0 && this.postureArmed && this.recoverCd == 0 && this.action != TigerTimings.DEATH) {
                this.postureArmed = false;
                this.recoverCd = RECOVER_CD;
                this.heal(this.getMaxHealth() * RECOVER_HEAL);
                this.sound(ModSounds.TIGER_PUFF, 1.4f, 1f);
                if (this.sw() != null) {
                    this.sw().spawnParticles(new net.minecraft.particle.DustParticleEffect(new org.joml.Vector3f(1.0f, 0.85f, 0.35f), 1.2f),
                            this.getX(), this.getBodyY(0.5), this.getZ(), 30, 0.5, 0.8, 0.5, 0.02);
                    this.sw().spawnParticles(net.minecraft.particle.ParticleTypes.HAPPY_VILLAGER,
                            this.getX(), this.getBodyY(0.6), this.getZ(), 10, 0.5, 0.6, 0.5, 0.0);
                }
            }
        }
        this.tickWave();
        this.updateAura();
        if (this.arenaCooldown > 0) {
            this.arenaCooldown--;
        }
        if (this.action != NONE) {
            TigerActions.tick(this);
        }
    }

    private static final UUID AURA_ID = UUID.fromString("3b7f1d2e-8a44-4c1b-9e6a-5d2c7f0a91b3");

    /** Аура внутренней силы (с 10 стаков): +15% к скорости. */
    private void updateAura() {
        EntityAttributeInstance inst = this.getAttributeInstance(EntityAttributes.GENERIC_MOVEMENT_SPEED);
        if (inst == null) {
            return;
        }
        boolean want = this.getInner() >= INNER_AURA;
        boolean has = inst.getModifier(AURA_ID) != null;
        if (want && !has) {
            inst.addTemporaryModifier(new EntityAttributeModifier(AURA_ID, "basedefense:tiger_aura", 0.15,
                    EntityAttributeModifier.Operation.MULTIPLY_TOTAL));
        } else if (!want && has) {
            inst.removeModifier(AURA_ID);
        }
    }

    /** Значки под полоской: патроны и внутренняя сила. */
    private void syncMarkers() {
        StatusEffect want = this.isSavage() ? ModEffects.SAVAGE_ROUNDS : ModEffects.TIGER_ROUNDS;
        StatusEffect other = this.isSavage() ? ModEffects.TIGER_ROUNDS : ModEffects.SAVAGE_ROUNDS;
        this.removeStatusEffect(other);
        this.marker(want, this.getAmmo());
        this.marker(ModEffects.INNER_STRENGTH, this.getInner());
    }

    private void marker(StatusEffect e, int level) {
        StatusEffectInstance cur = this.getStatusEffect(e);
        if (level <= 0) {
            if (cur != null) {
                this.removeStatusEffect(e);
            }
            return;
        }
        if (cur == null || cur.getAmplifier() != level - 1) {
            this.removeStatusEffect(e);
            this.addStatusEffect(new StatusEffectInstance(e, StatusEffectInstance.INFINITE, level - 1, false, false, true));
        }
    }

    /** «Добыча»: кто больше всех ранил за последнее время (урон тает ×0.9 в секунду). */
    private void updatePrey() {
        damageLog.replaceAll((k, v) -> v * 0.9f);
        damageLog.values().removeIf(v -> v < 1f);
        UUID best = null;
        float bestV = 5f;
        for (Map.Entry<UUID, Float> e : damageLog.entrySet()) {
            if (e.getValue() > bestV) {
                bestV = e.getValue();
                best = e.getKey();
            }
        }
        this.prey = best;
        PlayerEntity p = best != null ? this.getWorld().getPlayerByUuid(best) : null;
        if (p != null && p.isAlive() && p.squaredDistanceTo(this) < 48 * 48) {
            p.addStatusEffect(new StatusEffectInstance(ModEffects.PREY, 50, 0, false, false, true), this);
        }
    }

    // ===================================================================== урон по нему
    @Override
    public boolean damage(DamageSource source, float amount) {
        if (this.getWorld().isClient || this.isDead()) {
            return super.damage(source, amount);
        }
        if (source.isIn(DamageTypeTags.BYPASSES_INVULNERABILITY)) {
            return super.damage(source, amount);
        }
        int a = this.action;
        // неуязвим: переход фазы, финальный подъём, смерть, начало уклонения
        if (a == TigerTimings.PHASE2 || a == TigerTimings.FINAL_RISE || a == TigerTimings.DEATH
                || (TigerActions.isDodge(a) && this.actionTick < 6)) {
            return false;
        }
        LivingEntity attacker = source.getAttacker() instanceof LivingEntity l ? l : null;
        boolean ranged = source.isIn(DamageTypeTags.IS_PROJECTILE);
        // контрстойка: удар в ближнем бою — парирует и отвечает
        if (a == TigerTimings.STANCE && attacker != null && !ranged && this.actionTick > 3
                && attacker.squaredDistanceTo(this) < 6 * 6) {
            TigerActions.parry(this, attacker);
            return false;
        }
        // уклонение
        if (attacker != null && this.brain.tryDodge(attacker, ranged)) {
            return false;
        }
        if (a == TigerTimings.STAGGER) {
            amount *= STAGGER_TAKEN;
        }
        if (a == TigerTimings.RELOAD || a == TigerTimings.RELOAD_TAC) {
            amount *= 1.15f;
        }
        // до финала — не ниже 10% здоровья: дошли до порога — стойка ломается насовсем, начинается финал
        float floor = this.getMaxHealth() * HP_FLOOR;
        boolean forceFinal = false;
        if (!this.isFinal() && this.getHealth() - amount < floor) {
            amount = Math.max(0f, this.getHealth() - floor);
            forceFinal = true;
        }
        // финал: смертельный удар — сначала анимация смерти
        if (this.isFinal() && amount >= this.getHealth() && a != TigerTimings.DEATH) {
            boolean hurt = super.damage(source, Math.max(0f, this.getHealth() - 1f));
            this.deathSource = source;
            TigerActions.start(this, TigerTimings.DEATH, null);
            return true;
        }
        float before = this.getHealth();
        boolean hurt = amount > 0 && super.damage(source, amount);
        float dealt = Math.max(0f, before - this.getHealth());
        if (hurt || forceFinal) {
            this.onHurt(attacker, dealt, a);
        }
        if (forceFinal && !this.isFinal()) {
            if (a == TigerTimings.STAGGER) {
                this.breaks = Math.max(this.breaks, 3);            // после этого слома — сразу финал
            } else {
                this.breaks = Math.max(this.breaks, 2);
                this.breakPosture();
            }
        }
        return hurt;
    }

    private void onHurt(LivingEntity attacker, float dealt, int a) {
        // внутренняя сила
        this.innerAcc += dealt;
        while (this.innerAcc >= INNER_PER) {
            this.innerAcc -= INNER_PER;
            this.setInner(this.getInner() + 1);
        }
        // добыча
        if (attacker instanceof PlayerEntity p) {
            this.damageLog.merge(p.getUuid(), dealt, Float::sum);
        }
        // «Один удар»: ударили первым
        if (a == TigerTimings.ONE_WAIT && this.actionTick >= TigerTimings.ONE_WAIT_PLANT) {
            this.struckDuringWait = true;
        }
        // стойка
        if (a != TigerTimings.STAGGER) {
            float mul = TigerActions.unstoppable(a) ? 0.5f : 1f;
            if (a == TigerTimings.RELOAD || a == TigerTimings.RELOAD_TAC) {
                mul = 2f;
            }
            if (a == TigerTimings.ONE_WAIT) {
                mul = 3f;
            }
            this.addPosture(dealt * POSTURE_PER_DMG * mul);
        }
        // реакция: вне атаки — короткий разворот к обидчику
        if (attacker != null && this.action == NONE && this.getTarget() == null) {
            this.setTarget(attacker);
        }
    }

    void addPosture(float v) {
        if (v <= 0 || this.action == TigerTimings.STAGGER || this.action == TigerTimings.DEATH) {
            return;
        }
        this.lastPostureGain = this.getWorld().getTime();
        this.setPosture(this.getPosture() + v);
        if (this.getPosture() >= POSTURE_MAX) {
            this.breakPosture();
        }
    }

    /** Слом стойки: оглушён ~4 с, урон по нему ×1.5; гаснет одно кольцо. */
    void breakPosture() {
        this.breaks++;
        this.postureArmed = false;
        this.setPosture(0f);
        if (this.getRings() > 0) {
            this.setRings(this.getRings() - 1);
        }
        this.setGlow(0);
        this.waveCenter = null;
        this.flurryLeft = 0;
        if (this.getWorld() instanceof ServerWorld sw) {
            Fx.tigerBreak(sw, this.getPos().add(0, 1.4, 0));
            Shake.send(sw, this.getPos(), 20, 0.5f, 10);
        }
        this.sound(ModSounds.TIGER_BREAK, 1.6f, 1f);
        TigerActions.start(this, TigerTimings.STAGGER, null);
    }

    // ===================================================================== смерть
    void finishDeath() {
        DamageSource src = this.deathSource != null ? this.deathSource : this.getDamageSources().generic();
        this.setHealth(0f);
        this.onDeath(src);
    }

    @Override
    public void onDeath(DamageSource source) {
        super.onDeath(source);
        this.bossBar.clearPlayers();
        this.postureBar.clearPlayers();
    }

    @Override
    public boolean handleFallDamage(float fallDistance, float damageMultiplier, DamageSource damageSource) {
        return false;
    }

    @Override
    public boolean canImmediatelyDespawn(double distanceSquared) {
        return false;
    }

    @Override
    protected SoundEvent getHurtSound(DamageSource source) {
        return ModSounds.TIGER_HURT;
    }

    @Override
    protected SoundEvent getDeathSound() {
        return ModSounds.TIGER_DEATH;
    }

    @Override
    protected SoundEvent getAmbientSound() {
        return this.isLazy() && this.getPhase() == 1 ? ModSounds.TIGER_PUFF : null;
    }

    @Override
    public int getMinAmbientSoundDelay() {
        return 260;
    }

    @Override
    protected void playStepSound(net.minecraft.util.math.BlockPos pos, net.minecraft.block.BlockState state) {
        this.playSound(ModSounds.TIGER_STEP, 0.5f, 0.9f + this.random.nextFloat() * 0.2f);
    }

    /** Реплики: тексты пришлёт автор мода. Ключ — entity.basedefense.thunder_tiger.line.&lt;атака&gt;. */
    void speak(String key) {
        // Пока без фраз. Когда будут тексты — добавить в lang ключи "entity.basedefense.thunder_tiger.line." + key
        // и отправлять игрокам рядом: p.sendMessage(Text.translatable(...), true).
    }

    // ===================================================================== общие помощники боя
    void sound(SoundEvent s, float vol, float pitch) {
        this.getWorld().playSound(null, this.getX(), this.getY(), this.getZ(), s, SoundCategory.HOSTILE, vol, pitch);
    }

    ServerWorld sw() {
        return this.getWorld() instanceof ServerWorld s ? s : null;
    }

    Vec3d forward() {
        float yaw = this.getYaw() * MathHelper.RADIANS_PER_DEGREE;
        return new Vec3d(-MathHelper.sin(yaw), 0, MathHelper.cos(yaw));
    }

    Vec3d right() {
        float yaw = this.getYaw() * MathHelper.RADIANS_PER_DEGREE;
        return new Vec3d(-MathHelper.cos(yaw), 0, -MathHelper.sin(yaw));
    }

    void faceTarget(LivingEntity target, float maxTurn) {
        double dx = target.getX() - this.getX();
        double dz = target.getZ() - this.getZ();
        float want = (float) (MathHelper.atan2(dz, dx) * MathHelper.DEGREES_PER_RADIAN) - 90.0f;
        float yaw = this.getYaw() + MathHelper.clamp(MathHelper.wrapDegrees(want - this.getYaw()), -maxTurn, maxTurn);
        this.setYaw(yaw);
        this.setBodyYaw(yaw);
        this.setHeadYaw(yaw);
    }

    List<LivingEntity> victims(double radius) {
        Box box = this.getBoundingBox().expand(radius, 2.0, radius);
        return this.getWorld().getEntitiesByClass(LivingEntity.class, box,
                e -> e != this && e.isAlive() && !(e instanceof ThunderTigerEntity) && !(e instanceof ArmorStandEntity)
                        && !(e instanceof net.minecraft.entity.mob.Monster)
                        && !(e instanceof PlayerEntity p && (p.isCreative() || p.isSpectator())));
    }

    /** Множитель урона тигра: кольца, аура силы, «скорость решает». */
    float damageMul(LivingEntity target) {
        float m = 1f + RING_BONUS * this.getRings();
        if (this.getInner() >= INNER_AURA) {
            m *= 1.1f;
        }
        if (target != null) {
            if (ModEffects.isStunned(target) || ModEffects.isFrozen(target) || target.hasStatusEffect(StatusEffects.SLOWNESS)) {
                m *= 1.2f;
            } else if (!target.isSprinting() && target.getVelocity().horizontalLengthSquared() < 0.0025) {
                m *= 1.1f;
            }
        }
        return m;
    }

    /**
     * Попадание по цели: урон (× множители), отброс, дрожь; shot — выстрел патроном (взрыв, поджог);
     * unblockable — щит не спасает (выбивается на 2 с).
     */
    boolean strike(LivingEntity e, float base, double knock, double up, int tremor, boolean shot, boolean unblockable) {
        if (!this.hitIds.add(e.getId())) {
            return false;
        }
        float dmg = base * this.damageMul(e);
        boolean savage = this.isSavage();
        if (shot) {
            dmg *= savage ? SAVAGE_MUL : SHOT_MUL;
        }
        if (unblockable && e instanceof PlayerEntity p) {
            this.disableShield(p, 40);
        }
        e.timeUntilRegen = 0;
        boolean hit = e.damage(this.getDamageSources().mobAttack(this), dmg);
        boolean shielded = e instanceof ShieldBlocker sb && sb.basedefense$blockedThisTick();
        if (shielded) {
            this.addPosture(POSTURE_SHIELD);
            this.sound(ModSounds.TIGER_CLANG, 1f, 0.9f + this.random.nextFloat() * 0.2f);
        }
        if (hit) {
            if (!shielded) {
                if (knock > 0) {
                    e.takeKnockback(knock, this.getX() - e.getX(), this.getZ() - e.getZ());
                }
                if (up > 0) {
                    e.addVelocity(0, up, 0);
                }
                e.velocityModified = true;
                if (shot) {
                    e.setOnFireFor(savage ? SAVAGE_FIRE : SHOT_FIRE);
                }
                if (tremor > 0) {
                    addTremor(e, tremor, this);
                }
            }
            this.sound(ModSounds.TIGER_HIT, 0.9f, 0.85f + this.random.nextFloat() * 0.3f);
        }
        return hit;
    }

    void disableShield(PlayerEntity p, int ticks) {
        Set<Item> shields = new HashSet<>();
        shields.add(Items.SHIELD);
        if (p.getMainHandStack().getItem() instanceof ShieldItem) {
            shields.add(p.getMainHandStack().getItem());
        }
        if (p.getOffHandStack().getItem() instanceof ShieldItem) {
            shields.add(p.getOffHandStack().getItem());
        }
        boolean was = p.isBlocking();
        if (was) {
            p.clearActiveItem();
            this.getWorld().sendEntityStatus(p, (byte) 30);
        }
        for (Item item : shields) {
            p.getItemCooldownManager().set(item, ticks);
        }
    }

    /** Попасть всем в секторе перед собой (reach — от центра до края цели); вернуть, по кому попал. */
    List<LivingEntity> arcHit(double reach, double halfAngle, float dmg, double knock, double up, int tremor, boolean shot,
                              boolean unblockable) {
        Vec3d f = this.forward();
        double cos = Math.cos(Math.toRadians(halfAngle));
        List<LivingEntity> out = new ArrayList<>();
        for (LivingEntity e : this.victims(reach + 1)) {
            Vec3d to = new Vec3d(e.getX() - this.getX(), 0, e.getZ() - this.getZ());
            double dist = to.length() - e.getWidth() / 2;
            if (dist > reach || Math.abs(e.getY() - this.getY()) > 3.0) {
                continue;
            }
            if (to.lengthSquared() > 0.3 && to.normalize().dotProduct(f) < cos) {
                continue;
            }
            if (this.strike(e, dmg, knock, up, tremor, shot, unblockable)) {
                out.add(e);
            }
        }
        return out;
    }

    /** Попасть всем в круге radius вокруг точки c (к краю урон до −40%); вернуть, по кому попал. */
    List<LivingEntity> areaHit(Vec3d c, double radius, float dmg, double knock, double up, int tremor, boolean shot) {
        List<LivingEntity> out = new ArrayList<>();
        for (LivingEntity e : this.victims(radius + Math.sqrt(this.squaredDistanceTo(c)) + 1)) {
            double d = Math.sqrt(e.squaredDistanceTo(c.x, e.getY(), c.z)) - e.getWidth() / 2;
            if (d > radius || Math.abs(e.getY() - c.y) > 2.5) {
                continue;
            }
            float k = (float) MathHelper.clamp(1.0 - d / (radius + 0.5) * 0.4, 0.6, 1.0);
            if (this.strike(e, dmg * k, knock, up, tremor, shot, false)) {
                out.add(e);
            }
        }
        return out;
    }

    // ===================================================================== дрожь
    static int tremorOf(LivingEntity e) {
        StatusEffectInstance cur = e.getStatusEffect(ModEffects.TREMOR);
        return cur == null ? 0 : cur.getAmplifier() + 1;
    }

    static void addTremor(LivingEntity e, int n, LivingEntity source) {
        int s = Math.min(TREMOR_MAX, tremorOf(e) + n);
        e.removeStatusEffect(ModEffects.TREMOR);
        e.addStatusEffect(new StatusEffectInstance(ModEffects.TREMOR, TREMOR_TICKS, s - 1, false, true, true), source);
        StatusEffectInstance sc = e.getStatusEffect(ModEffects.SCORCH);
        if (sc != null) {
            e.removeStatusEffect(ModEffects.SCORCH);
            e.addStatusEffect(new StatusEffectInstance(ModEffects.SCORCH, TREMOR_TICKS, 0, false, true, true), source);
        }
    }

    /** Дрожь становится жгучей (после «Сокрушающего разрыва»). */
    static void scorch(LivingEntity e, LivingEntity source) {
        if (tremorOf(e) > 0) {
            e.addStatusEffect(new StatusEffectInstance(ModEffects.SCORCH, TREMOR_TICKS, 0, false, true, true), source);
        }
    }

    /** Разрыв дрожи: урон 1.5 × стаки (жгучая — +50% и поджог), оглушение 0.4 с, стаки −3. */
    void burst(LivingEntity e) {
        int s = tremorOf(e);
        if (s <= 0 || !e.isAlive()) {
            return;
        }
        boolean sc = e.hasStatusEffect(ModEffects.SCORCH);
        float dmg = BURST_PER * s * (sc ? 1.5f : 1f);
        e.timeUntilRegen = 0;
        e.damage(this.getDamageSources().indirectMagic(this, this), dmg);
        e.addStatusEffect(new StatusEffectInstance(ModEffects.STUN, BURST_STUN, 0, false, true, true), this);
        if (sc) {
            e.setOnFireFor(4);
        }
        e.removeStatusEffect(ModEffects.TREMOR);
        if (s > 3) {
            e.addStatusEffect(new StatusEffectInstance(ModEffects.TREMOR, TREMOR_TICKS, s - 4, false, true, true), this);
        } else {
            e.removeStatusEffect(ModEffects.SCORCH);
        }
        if (this.sw() != null) {
            Fx.tigerBurst(this.sw(), e.getPos().add(0, e.getHeight() * 0.5, 0), s, sc);
        }
        this.getWorld().playSound(null, e.getX(), e.getY(), e.getZ(), ModSounds.TIGER_BURST, SoundCategory.HOSTILE,
                1f, 0.9f + s * 0.03f);
    }

    // ===================================================================== огненная волна
    void startWave(Vec3d c, float dmg) {
        this.waveCenter = c;
        this.waveRadius = 1.2;
        this.waveDamage = dmg;
        this.waveHit.clear();
    }

    private void tickWave() {
        if (this.waveCenter == null) {
            return;
        }
        this.waveRadius += WAVE_SPEED;
        ServerWorld s = this.sw();
        if (s != null && this.age % 1 == 0) {
            Fx.tigerWave(s, this.waveCenter, this.waveRadius, this.isSavage());
        }
        for (LivingEntity e : this.victims(WAVE_MAX + 4)) {
            if (this.waveHit.contains(e.getId())) {
                continue;
            }
            double d = Math.sqrt(e.squaredDistanceTo(this.waveCenter.x, e.getY(), this.waveCenter.z));
            boolean air = !e.isOnGround() && e.getY() > this.waveCenter.y + 0.5;
            if (Math.abs(d - this.waveRadius) < 0.8 && !air && Math.abs(e.getY() - this.waveCenter.y) < 2.0) {
                this.waveHit.add(e.getId());
                float dmg = this.waveDamage * this.damageMul(e);
                e.timeUntilRegen = 0;
                if (e.damage(this.getDamageSources().mobAttack(this), dmg)) {
                    e.setOnFireFor(this.isSavage() ? SAVAGE_FIRE : 3);
                    e.takeKnockback(0.8, this.waveCenter.x - e.getX(), this.waveCenter.z - e.getZ());
                    e.addVelocity(0, 0.3, 0);
                    e.velocityModified = true;
                    addTremor(e, 2, this);
                }
            }
        }
        if (this.waveRadius >= WAVE_MAX) {
            this.waveCenter = null;
        }
    }

    /** Список игроков рядом (для уклонений и выбора цели). */
    List<PlayerEntity> playersNear(double r) {
        List<PlayerEntity> out = new ArrayList<>();
        for (PlayerEntity p : this.getWorld().getEntitiesByClass(PlayerEntity.class, this.getBoundingBox().expand(r),
                q -> q.isAlive() && !q.isCreative() && !q.isSpectator())) {
            out.add(p);
        }
        return out;
    }
}

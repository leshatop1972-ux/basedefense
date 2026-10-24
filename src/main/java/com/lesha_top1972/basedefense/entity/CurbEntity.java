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
    static final double SPIN_RADIUS = 3.0;

    public static final int SUMMON_TICKS = 46;
    static final int SUMMON_HIT = 21;
    static final int SUMMON_COOLDOWN = 2400;
    static final int MAX_MINIONS = 4;

    static final float COMBO_DAMAGE = 3f;
    static final float JET_DAMAGE = 11f;
    static final float SLAM_DAMAGE = 5f;
    static final float STOMP_DAMAGE = 3f;
    static final float SPIN_DAMAGE = 5f;
    static final float ARROW_FACTOR = 0.45f;
    static final int RETARGET_TICKS = 500;

    private final ServerBossBar bossBar = new ServerBossBar(this.getDisplayName(), BossBar.Color.PINK,
            BossBar.Style.NOTCHED_10);
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
        if (--this.retargetTimer <= 0) {
            this.retargetTimer = RETARGET_TICKS;
            this.switchTarget();
        }
    }

    /** Раз в 25 секунд — на другого видимого игрока поблизости (если есть). */
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
        for (LivingEntity e : this.victims(4.0)) {
            double dist = Math.sqrt(this.squaredDistanceTo(e.getX(), this.getY(), e.getZ())) - e.getWidth() / 2;
            if (dist <= 4.0 && Math.abs(e.getY() - this.getY()) < 2.0) {
                if (this.strike(e, STOMP_DAMAGE, 0.5, 0.25)) {
                    e.addStatusEffect(new StatusEffectInstance(StatusEffects.SLOWNESS, 100, 0), this);
                }
            }
        }
        Vec3d c = this.getPos();
        this.groundRing(c, 1.4, 12);
        this.groundRing(c, 2.7, 20);
        this.groundRing(c, 4.0, 28);
        ServerWorld s = this.sw();
        if (s != null) {
            s.spawnParticles(ParticleTypes.EXPLOSION, c.x, c.y + 0.3, c.z, 1, 0, 0, 0, 0);
        }
        this.sound(SoundEvents.ENTITY_GENERIC_EXPLODE, 0.6f, 0.7f);
        this.sound(SoundEvents.ENTITY_RAVAGER_STEP, 1.4f, 0.6f);
    }

    void spinHits(java.util.Set<Integer> hitIds) {
        for (LivingEntity e : this.victims(SPIN_RADIUS)) {
            double dist = Math.sqrt(this.squaredDistanceTo(e.getX(), this.getY(), e.getZ())) - e.getWidth() / 2;
            if (dist <= SPIN_RADIUS && Math.abs(e.getY() - this.getY()) < 2.2 && hitIds.add(e.getId())) {
                if (this.strike(e, SPIN_DAMAGE, 0.9, 0.25)) {
                    this.sound(SoundEvents.ENTITY_PLAYER_ATTACK_KNOCKBACK, 1.0f, 0.8f);
                }
            }
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
            s.spawnParticles(ParticleTypes.SWEEP_ATTACK, this.getX() + Math.cos(b) * 1.9, this.getY() + 0.9,
                    this.getZ() + Math.sin(b) * 1.9, 1, 0, 0, 0, 0);
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

    /** ИИ босса: общая часть (серия, реактивный удар) + таран, вихрь, призыв. */
    static class BossGoal extends BrawlGoal<CurbEntity> {
        private int slamCooldown = 160;
        private int summonCooldown = 0;

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
        }

        @Override
        protected void options(List<int[]> out, double dist) {
            super.options(out, dist);
            if (!this.mob.isPhase2()) {
                return;
            }
            for (int[] o : out) {
                o[1] = o[1] * 3 / 5;                       // во 2‑й фазе старые атаки реже
            }
            if (dist < 3.6) {
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
            if (this.cooldown <= 0 && this.slamCooldown <= 0 && dist < 7.0 && m.getVisibilityCache().canSee(target)) {
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
                        m.coneHit(2.9, 60, SLAM_DAMAGE, 1.0, 0.3);
                        m.breakBlocks();
                        m.impactFx(1.7, 1.3, true);
                        m.sound(SoundEvents.ENTITY_ZOMBIE_ATTACK_IRON_DOOR, 1.2f, 0.7f);
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
    }
}

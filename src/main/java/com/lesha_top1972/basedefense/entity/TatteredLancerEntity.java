package com.lesha_top1972.basedefense.entity;

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
import net.minecraft.entity.mob.HostileEntity;
import net.minecraft.entity.mob.Monster;
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
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;

import java.util.EnumSet;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Босс «Оборванный улан» — дуэлянт с турнирным копьём. 100 HP, полоска босса.
 * <p>Атаки (анимации — tools/lancer_gen.py, тайминги совпадают):</p>
 * <ul>
 *   <li>THRUST — три укола подряд (6 + 6 + 8 урона, достаёт на ~3.8 блока);</li>
 *   <li>CHARGE — разбег и таран на ~9 блоков (13 урона, сильно отбрасывает);</li>
 *   <li>LEAP — прыжок с сальто и удар копьём в землю (12 урона в радиусе 3.2, подбрасывает);</li>
 *   <li>SWEEP — круговой взмах (9 урона вокруг, радиус 3.8);</li>
 *   <li>UPPERCUT — удар снизу, подбрасывает цель в воздух (10 урона), часто следом прыжок;</li>
 *   <li>BACKSTEP — отскок назад с вращением копья, часто следом таран;</li>
 *   <li>ROAR — на половине здоровья: салют шляпой, дальше атакует чаще (вторая фаза).</li>
 * </ul>
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
    public static final byte STATUS_BASE = 70;

    /** Длительности (тики), как у анимаций. */
    public static final int THRUST_TICKS = 30;
    public static final int CHARGE_TICKS = 36;
    public static final int LEAP_WINDUP = 10;
    public static final int LAND_TICKS = 16;
    public static final int SWEEP_TICKS = 26;
    public static final int UPPERCUT_TICKS = 22;
    public static final int BACKSTEP_TICKS = 14;
    public static final int ROAR_TICKS = 30;

    private final ServerBossBar bossBar = new ServerBossBar(this.getDisplayName(), BossBar.Color.YELLOW,
            BossBar.Style.NOTCHED_10);
    private boolean phase2;

    /** Только на клиенте: текущее действие и с какого возраста идёт. */
    public int clientAction = NONE;
    public int clientActionAge;
    public int clientLeapAge = -10000;

    public TatteredLancerEntity(EntityType<? extends HostileEntity> type, World world) {
        super(type, world);
        this.experiencePoints = 60;
        this.setPersistent();
    }

    public static DefaultAttributeContainer.Builder createAttributes() {
        return HostileEntity.createHostileAttributes()
                .add(EntityAttributes.GENERIC_MAX_HEALTH, 100.0)
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

    @Override
    public void handleStatus(byte status) {
        int a = status - STATUS_BASE;
        if (a >= THRUST && a <= ROAR) {
            if (a == LEAP) {
                this.clientLeapAge = this.age;
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

    /** Следы на клиенте: пыль при таране, искры в прыжке, угли во второй фазе. */
    private void clientEffects() {
        World w = this.getWorld();
        int t = this.age - this.clientActionAge;
        if (this.clientAction == CHARGE && t >= 11 && t < 24) {
            w.addParticle(ParticleTypes.CLOUD, this.getX(), this.getY() + 0.1, this.getZ(),
                    (this.random.nextDouble() - 0.5) * 0.1, 0.05, (this.random.nextDouble() - 0.5) * 0.1);
            w.addParticle(ParticleTypes.CRIT, this.getParticleX(0.6), this.getBodyY(0.5), this.getParticleZ(0.6), 0, 0, 0);
        }
        if (this.clientAction == LEAP && t >= LEAP_WINDUP && !this.isOnGround()) {
            w.addParticle(ParticleTypes.CRIT, this.getParticleX(0.5), this.getBodyY(0.5), this.getParticleZ(0.5), 0, 0, 0);
            w.addParticle(ParticleTypes.END_ROD, this.getX(), this.getBodyY(0.3), this.getZ(), 0, -0.02, 0);
        }
    }

    @Override
    protected SoundEvent getAmbientSound() {
        return SoundEvents.ENTITY_VINDICATOR_AMBIENT;
    }

    @Override
    protected SoundEvent getHurtSound(DamageSource source) {
        return SoundEvents.ENTITY_VINDICATOR_HURT;
    }

    @Override
    protected SoundEvent getDeathSound() {
        return SoundEvents.ENTITY_VINDICATOR_DEATH;
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

    private List<LivingEntity> victims(double radius) {
        Box box = this.getBoundingBox().expand(radius, 1.5, radius);
        return this.getWorld().getEntitiesByClass(LivingEntity.class, box,
                e -> e != this && e.isAlive() && !(e instanceof Monster) && !(e instanceof ArmorStandEntity)
                        && !(e instanceof PlayerEntity p && (p.isCreative() || p.isSpectator())));
    }

    private void strike(LivingEntity e, float damage, double knock, double up, boolean multiHit) {
        if (multiHit) {
            e.timeUntilRegen = 0;
        }
        if (e.damage(this.getDamageSources().mobAttack(this), damage)) {
            if (knock > 0) {
                e.takeKnockback(knock, this.getX() - e.getX(), this.getZ() - e.getZ());
            }
            if (up > 0) {
                e.addVelocity(0, up, 0);
            }
            e.velocityModified = true;
        }
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

    void slamParticles() {
        ServerWorld s = this.sw();
        if (s == null) {
            return;
        }
        BlockState ground = this.getWorld().getBlockState(this.getBlockPos().down());
        s.spawnParticles(ParticleTypes.EXPLOSION, this.getX(), this.getY() + 0.2, this.getZ(), 1, 0, 0, 0, 0);
        for (int i = 0; i < 24; i++) {
            double a = i / 24.0 * Math.PI * 2;
            double x = this.getX() + Math.cos(a) * 2.6;
            double z = this.getZ() + Math.sin(a) * 2.6;
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
        private Vec3d dashDir = Vec3d.ZERO;
        private Vec3d leapVel = Vec3d.ZERO;
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
            // веса атак по дистанции; повтор одной и той же атаки подряд — реже
            int[] actions;
            int[] weights;
            if (dist < 3.8) {
                actions = new int[]{THRUST, SWEEP, UPPERCUT, BACKSTEP};
                weights = new int[]{35, nearby >= 2 ? 45 : 22, 22, 18};
            } else if (dist < 10) {
                actions = new int[]{CHARGE, LEAP, THRUST};
                weights = new int[]{45, 35, dist < 5 ? 20 : 0};
            } else {
                actions = new int[]{LEAP, CHARGE};
                weights = new int[]{dist < 18 ? 55 : 10, 35};
            }
            int total = 0;
            for (int i = 0; i < actions.length; i++) {
                if (actions[i] == this.lastAction) {
                    weights[i] /= 3;
                }
                total += weights[i];
            }
            if (total <= 0) {
                return NONE;
            }
            int r = this.lancer.random.nextInt(total);
            for (int i = 0; i < actions.length; i++) {
                r -= weights[i];
                if (r < 0) {
                    return actions[i];
                }
            }
            return actions[0];
        }

        @Override
        public void tick() {
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

            // вторая фаза: на половине здоровья — салют и ярость
            if (!this.lancer.isPhase2() && this.lancer.getHealth() <= this.lancer.getMaxHealth() * 0.5f) {
                this.lancer.enterPhase2();
                this.lancer.faceTarget(target);
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
            int nearby = this.lancer.victims(4.0).size();
            int a = this.pick(dist, nearby);
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
                        l.coneHit(3.8, 35, t == 20 ? 8f : 6f, t == 20 ? 0.8 : 0.3, 0, true);
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
                        l.setVelocity(this.dashDir.x * 0.78, l.getVelocity().y, this.dashDir.z * 0.78);
                        l.velocityModified = true;
                        if (t % 3 == 0) {
                            l.sound(SoundEvents.ENTITY_HORSE_GALLOP, 0.8f, 1.2f);
                        }
                        for (LivingEntity e : l.victims(1.6)) {
                            Vec3d to = new Vec3d(e.getX() - l.getX(), 0, e.getZ() - l.getZ());
                            if (to.lengthSquared() < 0.01 || to.normalize().dotProduct(this.dashDir) > 0.2) {
                                if (this.hitIds.add(e.getId())) {
                                    l.strike(e, 13f, 1.6, 0.45, false);
                                    l.sound(SoundEvents.ENTITY_PLAYER_ATTACK_KNOCKBACK, 1.2f, 0.7f);
                                }
                            }
                        }
                        if (l.horizontalCollision && t > 13) {
                            this.tick = 24;          // упёрся в стену — тормозит
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
                        double air = 24.0;
                        double speed = Math.min(1.0, d.length() / air);
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
                        boolean landed = l.isOnGround() && t > LEAP_WINDUP + 3;
                        if (landed || t > LEAP_WINDUP + 60) {
                            this.action = LAND;
                            this.tick = 0;
                            l.setVelocity(0, l.getVelocity().y, 0);
                            l.sendAction(LAND);
                            l.radiusHit(3.2, 12f, 0.9, 0.6);
                            l.slamParticles();
                            l.sound(SoundEvents.ENTITY_GENERIC_EXPLODE, 0.6f, 1.4f);
                            l.sound(SoundEvents.ITEM_TRIDENT_HIT_GROUND, 1.2f, 0.8f);
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
                        l.radiusHit(3.8, 9f, 1.3, 0.2);
                        l.sweepParticles();
                    }
                    if (t >= SWEEP_TICKS) {
                        this.finish(20);
                    }
                }
                case UPPERCUT -> {
                    if (t < 6 && target != null) {
                        l.faceTarget(target);
                    }
                    if (t == 8) {
                        int n = l.coneHit(3.3, 40, 10f, 0.3, 0.95, false);
                        l.addVelocity(0, 0.25, 0);
                        l.velocityModified = true;
                        l.sound(SoundEvents.ITEM_TRIDENT_THROW, 1.2f, 0.8f);
                        ServerWorld s = l.sw();
                        if (s != null) {
                            Vec3d p = l.getPos().add(l.forward().multiply(1.8));
                            s.spawnParticles(ParticleTypes.CLOUD, p.x, p.y + 0.3, p.z, 10, 0.3, 0.6, 0.3, 0.05);
                            s.spawnParticles(ParticleTypes.CRIT, p.x, p.y + 1.5, p.z, 12, 0.3, 0.8, 0.3, 0.3);
                        }
                        if (n > 0 && (l.isPhase2() || l.random.nextFloat() < 0.7f)) {
                            this.queued = LEAP;              // добивание в прыжке по подброшенной цели
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
                        l.push(3.5, 1.6, 0.35);              // волна отталкивает тех, кто рядом
                    }
                    if (t >= ROAR_TICKS) {
                        this.finish(6);
                    }
                }
                default -> this.finish(10);
            }
        }
    }
}

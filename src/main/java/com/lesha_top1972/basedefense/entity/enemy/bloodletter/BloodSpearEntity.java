package com.lesha_top1972.basedefense.entity.enemy.bloodletter;

import com.lesha_top1972.basedefense.entity.ModEntities;
import com.lesha_top1972.basedefense.entity.enemy.bloodthirster.BloodKin;
import com.lesha_top1972.basedefense.entity.enemy.sapper.SapperBlast;
import com.lesha_top1972.basedefense.block.BloodPoolBlock;
import com.lesha_top1972.basedefense.block.ModBlocks;
import com.lesha_top1972.basedefense.effect.ModEffects;
import com.lesha_top1972.basedefense.fx.Fx;
import com.lesha_top1972.basedefense.sound.ModSounds;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.data.DataTracker;
import net.minecraft.entity.data.TrackedData;
import net.minecraft.entity.data.TrackedDataHandlerRegistry;
import net.minecraft.entity.projectile.ProjectileEntity;
import net.minecraft.entity.projectile.ProjectileUtil;
import net.minecraft.particle.DustParticleEffect;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.hit.EntityHitResult;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;
import org.joml.Vector3f;

import java.util.HashSet;
import java.util.Set;

/**
 * Кровавое копьё «Кровопускателя». Появляется веером над ним (HOVER), перед броском отводится назад (PRIME),
 * летит 1.4 бл/т (FLY): «наведённое» доводится до цели и попадает, промах — прямо в точку рядом с целью
 * (увернувшуюся цель не задевает). Удар: 6 урона и «Кровотёк» в радиусе 1.2, ломает 1–2 слабых блока постройки
 * (по mobGriefing), оставляет 1–2 клетки кровавой лужи (высыхают через 15 с). В блоке торчит 1.2 с (STUCK).
 * Позицию в воздухе над мобом клиент рисует сам по общей формуле {@link BloodletterEntity#hoverPos}.
 */
public class BloodSpearEntity extends ProjectileEntity {

    public static final int HOVER = 0;
    public static final int PRIME = 1;
    public static final int FLY = 2;
    public static final int STUCK = 3;
    public static final float DAMAGE = 6f;
    static final double SPEED = 1.4;
    static final double SPLASH = 1.2;
    static final int STUCK_TICKS = 24;
    /** Половина длины копья (центр модели — посередине; при ударе остриё входит в блок). */
    public static final double HALF = 0.75;

    private static final TrackedData<Integer> STATE = DataTracker.registerData(BloodSpearEntity.class, TrackedDataHandlerRegistry.INTEGER);
    private static final TrackedData<Integer> SLOT = DataTracker.registerData(BloodSpearEntity.class, TrackedDataHandlerRegistry.INTEGER);
    private static final TrackedData<Integer> OWNER = DataTracker.registerData(BloodSpearEntity.class, TrackedDataHandlerRegistry.INTEGER);
    private static final DustParticleEffect DRIP = new DustParticleEffect(new Vector3f(0.6f, 0.03f, 0.06f), 0.8f);
    private static final DustParticleEffect GLOW = new DustParticleEffect(new Vector3f(1.0f, 0.18f, 0.12f), 0.7f);

    private LivingEntity homing;
    /** Осада: блок постройки, в который брошено копьё (преграды на пути ломаются любые, сама цель — рядом с ударом). */
    BlockPos siege;
    private int ignoreId = -1;
    private int stateTicks;
    /** Клиент: когда сменилось состояние (для отвода назад и угасания) и направление острия (сглаженное). */
    public int clientStateAge;
    public Vec3d clientDir;
    public Vec3d clientPrevDir;

    public BloodSpearEntity(EntityType<? extends BloodSpearEntity> type, World world) {
        super(type, world);
        this.setNoGravity(true);
    }

    static BloodSpearEntity form(BloodletterEntity owner, int slot) {
        World w = owner.getWorld();
        BloodSpearEntity s = new BloodSpearEntity(ModEntities.BLOOD_SPEAR, w);
        s.setOwner(owner);
        s.dataTracker.set(SLOT, slot);
        s.dataTracker.set(OWNER, owner.getId());
        Vec3d p = BloodletterEntity.hoverPos(owner.getPos(), owner.getYaw(), slot, 0);
        s.setPosition(p.x, p.y, p.z);
        w.spawnEntity(s);
        w.playSound(null, p.x, p.y, p.z, ModSounds.BLOODLETTER_SPEAR_FORM, SoundCategory.HOSTILE, 0.7f,
                0.9f + w.random.nextFloat() * 0.25f);
        return s;
    }

    @Override
    protected void initDataTracker() {
        this.dataTracker.startTracking(STATE, HOVER);
        this.dataTracker.startTracking(SLOT, 0);
        this.dataTracker.startTracking(OWNER, -1);
    }

    @Override
    public void onTrackedDataSet(TrackedData<?> data) {
        super.onTrackedDataSet(data);
        if (STATE.equals(data)) {
            this.clientStateAge = this.age;
        }
    }

    public int state() {
        return this.dataTracker.get(STATE);
    }

    public int slot() {
        return this.dataTracker.get(SLOT);
    }

    /** Хозяин (и на клиенте): по номеру сущности. */
    public BloodletterEntity letter() {
        Entity e = this.getWorld().getEntityById(this.dataTracker.get(OWNER));
        return e instanceof BloodletterEntity b ? b : null;
    }

    private void setState(int s) {
        this.dataTracker.set(STATE, s);
        this.stateTicks = 0;
    }

    void prime() {
        if (this.state() == HOVER) {
            this.setState(PRIME);
        }
    }

    /** Бросок: target — наведённое (доводится), иначе прямо в точку; ignore — увернувшаяся цель (не задевает). */
    void fly(LivingEntity target, Vec3d point, int ignore) {
        this.homing = target;
        this.ignoreId = ignore;
        Vec3d to = point.subtract(this.getPos());
        if (to.lengthSquared() < 1e-4) {
            to = new Vec3d(0, -1, 0);
        }
        Vec3d dir = to.normalize();
        if (target != null) {
            dir = dir.add(0, 0.22, 0).normalize();      // наведённое уходит чуть вверх и доворачивает на цель
        }
        this.setVelocity(dir.multiply(SPEED));
        this.setState(FLY);
    }

    /** Рассыпается в брызги (не выпущено или отжило). */
    void shatter() {
        if (this.getWorld() instanceof ServerWorld s) {
            Fx.blood(s, this.getPos(), 10, 0.4);
        }
        this.discard();
    }

    @Override
    public void tick() {
        super.tick();
        World w = this.getWorld();
        this.stateTicks++;
        if (w.isClient) {
            this.clientTick();
            return;
        }
        int st = this.state();
        if (st == HOVER || st == PRIME) {
            BloodletterEntity o = this.letter();
            if (o == null || !o.isAlive() || !o.spears.contains(this) || this.age > 200) {
                this.shatter();
                return;
            }
            Vec3d p = BloodletterEntity.hoverPos(o.getPos(), o.getYaw(), this.slot(), this.age);
            this.setPosition(p.x, p.y, p.z);
            this.setVelocity(Vec3d.ZERO);
            return;
        }
        if (st == STUCK) {
            if (this.stateTicks > STUCK_TICKS) {
                this.discard();
            }
            return;
        }
        // полёт
        Vec3d v = this.getVelocity();
        if (this.homing != null && this.homing.isAlive()) {
            Vec3d to = this.homing.getPos().add(0, this.homing.getHeight() * 0.55, 0).subtract(this.getPos());
            Vec3d want = to.normalize().multiply(SPEED);
            v = to.length() < 4.0 ? want : v.multiply(0.5).add(want.multiply(0.5)).normalize().multiply(SPEED);
            this.setVelocity(v);
        }
        HitResult hit = ProjectileUtil.getCollision(this, this::canHit);
        if (hit.getType() != HitResult.Type.MISS) {
            this.impact(hit, v);
            return;
        }
        this.setPosition(this.getX() + v.x, this.getY() + v.y, this.getZ() + v.z);
        if (this.stateTicks > 80) {
            this.shatter();
        }
    }

    private void clientTick() {
        World w = this.getWorld();
        int st = this.state();
        Vec3d dir;
        if (st == HOVER || st == PRIME) {
            BloodletterEntity o = this.letter();
            Vec3d p = o != null ? BloodletterEntity.hoverPos(o.getPos(), o.getYaw(), this.slot(), this.age) : this.getPos();
            Vec3d aim = o != null ? o.aim() : p.add(0, -1, 1);
            dir = aim.subtract(p);
            if (dir.lengthSquared() < 1e-3) {
                dir = new Vec3d(0, -1, 0);
            }
            dir = dir.normalize();
            if (this.age < 12 && this.random.nextInt(2) == 0) {
                // кровь стягивается в копьё
                double a = this.random.nextDouble() * Math.PI * 2;
                double r = 0.6 + this.random.nextDouble() * 0.5;
                w.addParticle(DRIP, p.x + Math.cos(a) * r, p.y + (this.random.nextDouble() - 0.5) * 0.8, p.z + Math.sin(a) * r,
                        -Math.cos(a) * 0.08, 0, -Math.sin(a) * 0.08);
            } else if (this.random.nextInt(14) == 0) {
                w.addParticle(DRIP, p.x, p.y - 0.1, p.z, 0, -0.05, 0);
            }
            if (st == PRIME && this.random.nextInt(2) == 0) {
                w.addParticle(GLOW, p.x + dir.x * 0.8, p.y + dir.y * 0.8, p.z + dir.z * 0.8, 0, 0, 0);
            }
        } else if (st == FLY) {
            Vec3d v = this.getVelocity();
            dir = v.lengthSquared() > 1e-4 ? v.normalize() : (this.clientDir != null ? this.clientDir : new Vec3d(0, -1, 0));
            // клиент сам двигает копьё по скорости между пакетами (как стрелы), след из капель
            this.setPosition(this.getX() + v.x, this.getY() + v.y, this.getZ() + v.z);
            for (int k = 0; k < 2; k++) {
                double f = this.random.nextDouble();
                w.addParticle(k == 0 ? GLOW : DRIP, this.getX() - v.x * f - dir.x * HALF, this.getY() - v.y * f - dir.y * HALF,
                        this.getZ() - v.z * f - dir.z * HALF, 0, -0.02, 0);
            }
        } else {
            dir = this.clientDir != null ? this.clientDir : new Vec3d(0, -1, 0);
            if (this.random.nextInt(6) == 0) {
                w.addParticle(DRIP, this.getX() - dir.x * HALF, this.getY() - dir.y * HALF, this.getZ() - dir.z * HALF, 0, -0.04, 0);
            }
        }
        this.clientPrevDir = this.clientDir == null ? dir : this.clientDir;
        // в воздухе — плавный доворот острия на цель, в полёте — сразу по скорости
        this.clientDir = this.clientDir == null || st == FLY ? dir
                : this.clientDir.multiply(0.7).add(dir.multiply(0.3)).normalize();
    }

    @Override
    protected boolean canHit(Entity entity) {
        return super.canHit(entity) && entity.getId() != this.ignoreId && !(entity instanceof BloodKin)
                && !(entity instanceof net.minecraft.entity.mob.Monster) && !(entity instanceof BloodSpearEntity);
    }

    private void impact(HitResult hit, Vec3d v) {
        ServerWorld s = (ServerWorld) this.getWorld();
        Vec3d p = hit.getPos();
        Vec3d dir = v.lengthSquared() > 1e-4 ? v.normalize() : new Vec3d(0, -1, 0);
        Entity owner = this.getOwner();
        LivingEntity attacker = owner instanceof LivingEntity l ? l : null;
        Set<Integer> done = new HashSet<>();
        if (hit instanceof EntityHitResult eh && eh.getEntity() instanceof LivingEntity le && BloodletterEntity.hurtable(le)) {
            this.strike(le, attacker);
            done.add(le.getId());
        }
        for (LivingEntity e : s.getEntitiesByClass(LivingEntity.class, new Box(p, p).expand(SPLASH + 0.5, 1.5, SPLASH + 0.5),
                BloodletterEntity::hurtable)) {
            if (done.contains(e.getId()) || e.getId() == this.ignoreId) {
                continue;
            }
            if (e.getBoundingBox().expand(SPLASH * 0.6).contains(p) || e.squaredDistanceTo(p.x, e.getY(), p.z) <= SPLASH * SPLASH
                    && Math.abs(e.getY() + e.getHeight() * 0.5 - p.y) < 1.6) {
                this.strike(e, attacker);
                done.add(e.getId());
            }
        }
        if (hit instanceof BlockHitResult bh) {
            this.breakBlocks(s, bh.getBlockPos(), bh.getSide());
        }
        placePools(s, p, this.random.nextInt(2) == 0 ? 1 : 2);
        Fx.blood(s, p, 26, 0.6);
        Fx.impact(s, p, 0.45f, false);
        s.playSound(null, p.x, p.y, p.z, ModSounds.BLOODLETTER_SPEAR_HIT, SoundCategory.HOSTILE, 1.0f,
                0.9f + this.random.nextFloat() * 0.25f);
        if (hit.getType() == HitResult.Type.ENTITY) {
            this.discard();
            return;
        }
        // остриё вошло в блок
        Vec3d c = p.subtract(dir.multiply(HALF - 0.35));
        this.setPosition(c.x, c.y, c.z);
        this.setVelocity(dir.multiply(1e-3));
        this.setState(STUCK);
    }

    private void strike(LivingEntity e, LivingEntity attacker) {
        if (e.damage(this.getDamageSources().mobProjectile(this, attacker), DAMAGE)) {
            ModEffects.addBleed(e, attacker);
        }
    }

    /**
     * Ломает блок попадания и с шансом 50% — один соседний (по mobGriefing). При осаде — любую преграду и саму
     * цель, если удар пришёлся рядом с ней (факелы и лестницы копьё не задевает — ломает их у стены).
     */
    private void breakBlocks(ServerWorld s, BlockPos p, Direction side) {
        if (!SapperBlast.griefing(s)) {
            return;
        }
        Entity owner = this.getOwner();
        boolean siege = this.siege != null;
        if (BloodletterEntity.spearBreakable(s, p, siege)) {
            s.breakBlock(p, false, owner);
        }
        if (siege && this.siege.getSquaredDistance(Vec3d.ofCenter(p)) <= 2.6 * 2.6
                && BloodletterEntity.spearBreakable(s, this.siege, true)) {
            s.breakBlock(this.siege, false, owner);
        }
        if (this.random.nextBoolean()) {
            Direction d = Direction.random(this.random);
            if (d == side) {
                d = side.getOpposite();
            }
            BlockPos q = p.offset(d);
            if (BloodletterEntity.spearBreakable(s, q, false)) {
                s.breakBlock(q, false, owner);
            }
        }
    }

    /** 1–2 клетки кровавой лужи на полу рядом с точкой (как лужи кровожада — высыхают сами). */
    static void placePools(ServerWorld w, Vec3d p, int n) {
        BlockPos c = BlockPos.ofFloored(p);
        int placed = 0;
        for (int attempt = 0; attempt < 6 && placed < n; attempt++) {
            BlockPos base = attempt == 0 ? c : c.add(w.random.nextInt(3) - 1, 0, w.random.nextInt(3) - 1);
            for (int dy = 1; dy >= -3; dy--) {
                BlockPos q = base.up(dy);
                if (w.isAir(q) && w.getBlockState(q.down()).isSideSolidFullSquare(w, q.down(), Direction.UP)) {
                    w.setBlockState(q, ModBlocks.BLOOD_POOL.getDefaultState());
                    w.scheduleBlockTick(q, ModBlocks.BLOOD_POOL, BloodPoolBlock.BOSS_POOL_TICKS + w.random.nextInt(40));
                    placed++;
                    break;
                }
            }
        }
    }

    @Override
    public boolean shouldRender(double distance) {
        return distance < 96 * 96;
    }
}

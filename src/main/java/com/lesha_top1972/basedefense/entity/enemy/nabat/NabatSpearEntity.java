package com.lesha_top1972.basedefense.entity.enemy.nabat;

import net.minecraft.block.BlockState;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.data.DataTracker;
import net.minecraft.entity.data.TrackedData;
import net.minecraft.entity.data.TrackedDataHandlerRegistry;
import net.minecraft.entity.decoration.ArmorStandEntity;
import net.minecraft.entity.mob.Monster;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.network.listener.ClientPlayPacketListener;
import net.minecraft.network.packet.Packet;
import net.minecraft.network.packet.s2c.play.EntitySpawnS2CPacket;
import net.minecraft.particle.BlockStateParticleEffect;
import net.minecraft.particle.DustParticleEffect;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.MathHelper;
import net.minecraft.world.World;
import org.joml.Vector3f;

import java.util.UUID;

/**
 * Золотое копьё «Копий небес» Набата. Появляется высоко в небе остриём вниз и висит, разгораясь
 * (на земле под ним — кольцо‑метка, оно сжимается), потом падает (2.6 бл/т):
 * 6 урона в радиусе 1.6 (большое копьё — 9 в радиусе 2.8), торчит из земли 1.5 с и рассыпается искрами.
 */
public class NabatSpearEntity extends Entity {

    public static final int HOLD = 20;
    public static final int HOLD_BIG = 28;
    static final double FALL_SPEED = 2.6;
    static final int STUCK_TICKS = 30;
    private static final DustParticleEffect AMBER = new DustParticleEffect(new Vector3f(1.0f, 0.78f, 0.35f), 1.1f);

    private static final TrackedData<Boolean> BIG = DataTracker.registerData(NabatSpearEntity.class,
            TrackedDataHandlerRegistry.BOOLEAN);
    private static final TrackedData<Integer> LIFE = DataTracker.registerData(NabatSpearEntity.class,
            TrackedDataHandlerRegistry.INTEGER);
    private static final TrackedData<Float> GROUND = DataTracker.registerData(NabatSpearEntity.class,
            TrackedDataHandlerRegistry.FLOAT);
    private static final TrackedData<Integer> STUCK = DataTracker.registerData(NabatSpearEntity.class,
            TrackedDataHandlerRegistry.INTEGER);

    private UUID ownerId;

    public NabatSpearEntity(EntityType<?> type, World world) {
        super(type, world);
        this.noClip = true;
        this.setNoGravity(true);
    }

    void setup(NabatEntity owner, boolean big, double groundY) {
        this.ownerId = owner.getUuid();
        this.dataTracker.set(BIG, big);
        this.dataTracker.set(GROUND, (float) groundY);
    }

    @Override
    protected void initDataTracker() {
        this.dataTracker.startTracking(BIG, false);
        this.dataTracker.startTracking(LIFE, 0);
        this.dataTracker.startTracking(GROUND, 0f);
        this.dataTracker.startTracking(STUCK, -1);
    }

    public boolean isBig() {
        return this.dataTracker.get(BIG);
    }

    public int hold() {
        return this.isBig() ? HOLD_BIG : HOLD;
    }

    public double radius() {
        return this.isBig() ? 2.8 : 1.6;
    }

    public double groundY() {
        return this.dataTracker.get(GROUND);
    }

    /** 0..1 — сколько прошло из ожидания перед падением. */
    public float charge(float tickDelta) {
        return MathHelper.clamp((this.dataTracker.get(LIFE) + tickDelta) / this.hold(), 0f, 1f);
    }

    /** Сколько тиков копьё уже торчит в земле (−1 — ещё не упало). */
    public int stuckTicks() {
        return this.dataTracker.get(STUCK);
    }

    @Override
    public void tick() {
        super.tick();
        World w = this.getWorld();
        int stuck = this.stuckTicks();
        if (w.isClient) {
            if (stuck < 0 && this.dataTracker.get(LIFE) >= this.hold()) {
                // след падения
                w.addParticle(ParticleTypes.END_ROD, this.getX(), this.getY() + 2.0, this.getZ(), 0, 0.15, 0);
                w.addParticle(AMBER, this.getX() + (this.random.nextDouble() - 0.5) * 0.3, this.getY() + 3.0,
                        this.getZ() + (this.random.nextDouble() - 0.5) * 0.3, 0, 0, 0);
            } else if (stuck < 0 && this.age % 4 == 0) {
                w.addParticle(AMBER, this.getX(), this.getY() + 3.4, this.getZ(), 0, -0.02, 0);
            } else if (stuck >= 0 && stuck % 5 == 0) {
                w.addParticle(ParticleTypes.SMALL_FLAME, this.getX(), this.getY() + 0.6, this.getZ(), 0, 0.03, 0);
            }
            return;
        }
        if (stuck >= 0) {
            this.dataTracker.set(STUCK, stuck + 1);
            if (stuck + 1 > STUCK_TICKS) {
                if (w instanceof ServerWorld s) {
                    s.spawnParticles(AMBER, this.getX(), this.getY() + 1.2, this.getZ(), 8, 0.1, 0.8, 0.1, 0.02);
                }
                this.discard();
            }
            return;
        }
        int life = this.dataTracker.get(LIFE) + 1;
        this.dataTracker.set(LIFE, life);
        if (life < this.hold()) {
            return;
        }
        double gy = this.groundY();
        double ny = Math.max(gy, this.getY() - FALL_SPEED);
        this.setPosition(this.getX(), ny, this.getZ());
        if (ny <= gy + 1e-3) {
            this.impact();
        }
        if (life > 200) {
            this.discard();
        }
    }

    private void impact() {
        this.dataTracker.set(STUCK, 0);
        if (!(this.getWorld() instanceof ServerWorld s)) {
            return;
        }
        double r = this.radius();
        float dmg = this.isBig() ? 9f : 6f;
        Entity o = this.ownerId != null ? s.getEntity(this.ownerId) : null;
        LivingEntity owner = o instanceof LivingEntity le ? le : null;
        for (LivingEntity e : s.getEntitiesByClass(LivingEntity.class, new Box(this.getX() - r - 1, this.getY() - 1,
                this.getZ() - r - 1, this.getX() + r + 1, this.getY() + 3, this.getZ() + r + 1),
                e -> e.isAlive() && !(e instanceof Monster) && !(e instanceof ArmorStandEntity) && !NabatHooks.isKin(e)
                        && !(e instanceof PlayerEntity p && (p.isCreative() || p.isSpectator())))) {
            if (e.squaredDistanceTo(this.getX(), e.getY(), this.getZ()) <= (r + e.getWidth() / 2) * (r + e.getWidth() / 2)) {
                e.timeUntilRegen = 0;
                if (e.damage(owner != null ? this.getDamageSources().mobAttack(owner) : this.getDamageSources().magic(), dmg)) {
                    e.takeKnockback(this.isBig() ? 0.8 : 0.4, this.getX() - e.getX(), this.getZ() - e.getZ());
                }
            }
        }
        BlockState ground = s.getBlockState(BlockPos.ofFloored(this.getX(), this.getY() - 0.3, this.getZ()));
        if (!ground.isAir()) {
            s.spawnParticles(new BlockStateParticleEffect(ParticleTypes.BLOCK, ground), this.getX(), this.getY() + 0.1,
                    this.getZ(), this.isBig() ? 24 : 12, r * 0.3, 0.1, r * 0.3, 0.2);
        }
        s.spawnParticles(ParticleTypes.END_ROD, this.getX(), this.getY() + 0.3, this.getZ(), this.isBig() ? 18 : 8,
                0.2, 0.2, 0.2, this.isBig() ? 0.2 : 0.12);
        com.lesha_top1972.basedefense.fx.Fx.ring(s, this.getPos().add(0, -0.1, 0), r, this.isBig() ? 20 : 10,
                com.lesha_top1972.basedefense.fx.Fx.dust(0xFFC759, 1.1f), com.lesha_top1972.basedefense.fx.Fx.F_DUST);
        if (this.isBig() || this.random.nextInt(3) == 0) {
            s.playSound(null, this.getX(), this.getY(), this.getZ(), SoundEvents.ITEM_TRIDENT_HIT_GROUND,
                    SoundCategory.HOSTILE, this.isBig() ? 1.6f : 0.9f, this.isBig() ? 0.6f : 0.9f + this.random.nextFloat() * 0.3f);
        }
    }

    @Override
    protected void readCustomDataFromNbt(NbtCompound nbt) {
        // копья живут секунды — после перезагрузки мира просто исчезают
        this.discard();
    }

    @Override
    protected void writeCustomDataToNbt(NbtCompound nbt) {
    }

    @Override
    public boolean shouldRender(double distance) {
        return distance < 128 * 128;
    }

    @Override
    public boolean isAttackable() {
        return false;
    }

    @Override
    public Packet<ClientPlayPacketListener> createSpawnPacket() {
        return new EntitySpawnS2CPacket(this);
    }
}

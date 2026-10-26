package com.lesha_top1972.basedefense.entity;

import com.lesha_top1972.basedefense.effect.ModEffects;
import com.lesha_top1972.basedefense.sound.ModSounds;
import net.minecraft.block.BlockState;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.data.DataTracker;
import net.minecraft.entity.data.TrackedData;
import net.minecraft.entity.data.TrackedDataHandlerRegistry;
import net.minecraft.entity.effect.StatusEffectInstance;
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
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.MathHelper;
import net.minecraft.world.Heightmap;
import net.minecraft.world.World;
import org.joml.Vector3f;

import java.util.UUID;

/**
 * Падающий колокол Набата.
 * <ul>
 *   <li>MARK — «Метка набата»: 4 с висит над головой игрока и следует за ним, потом падает: 9 урона, радиус 3
 *       (отбеги от друзей!);</li>
 *   <li>RAIN — «Колокольный дождь»: тень 1.5 с на земле, потом колокол падает: 5 урона, радиус 1.8.</li>
 * </ul>
 * Под колоколом на земле — тень‑кольцо (рисует клиент), к удару она сжимается и светлеет.
 */
public class NabatFallingBellEntity extends Entity {

    public static final int MARK = 0;
    public static final int RAIN = 1;
    public static final int MARK_HOLD = 72;
    public static final int RAIN_HOLD = 30;
    static final double FALL_SPEED = 1.5;
    private static final DustParticleEffect AMBER = new DustParticleEffect(new Vector3f(1.0f, 0.7f, 0.3f), 1.0f);

    private static final TrackedData<Integer> KIND = DataTracker.registerData(NabatFallingBellEntity.class,
            TrackedDataHandlerRegistry.INTEGER);
    private static final TrackedData<Integer> HOLD = DataTracker.registerData(NabatFallingBellEntity.class,
            TrackedDataHandlerRegistry.INTEGER);
    private static final TrackedData<Integer> LIFE = DataTracker.registerData(NabatFallingBellEntity.class,
            TrackedDataHandlerRegistry.INTEGER);

    private UUID ownerId;
    private UUID targetId;
    private boolean landed;
    private int landedTicks;

    public NabatFallingBellEntity(EntityType<?> type, World world) {
        super(type, world);
        this.noClip = true;
        this.setNoGravity(true);
    }

    void setup(NabatEntity owner, int kind, LivingEntity follow) {
        this.ownerId = owner.getUuid();
        this.dataTracker.set(KIND, kind);
        this.dataTracker.set(HOLD, kind == MARK ? MARK_HOLD : RAIN_HOLD);
        if (follow != null) {
            this.targetId = follow.getUuid();
        }
    }

    @Override
    protected void initDataTracker() {
        this.dataTracker.startTracking(KIND, RAIN);
        this.dataTracker.startTracking(HOLD, RAIN_HOLD);
        this.dataTracker.startTracking(LIFE, 0);
    }

    public int kind() {
        return this.dataTracker.get(KIND);
    }

    /** 0..1 — сколько осталось до падения (для тени); после начала падения — 1. */
    public float chargeProgress(float tickDelta) {
        return MathHelper.clamp((this.dataTracker.get(LIFE) + tickDelta) / this.dataTracker.get(HOLD), 0f, 1f);
    }

    public boolean isLanded() {
        return this.landed;
    }

    public double radius() {
        return this.kind() == MARK ? 3.0 : 1.8;
    }

    /** Высота земли под колоколом. */
    public int groundY() {
        return this.getWorld().getTopY(Heightmap.Type.MOTION_BLOCKING_NO_LEAVES, MathHelper.floor(this.getX()),
                MathHelper.floor(this.getZ()));
    }

    @Override
    public void tick() {
        super.tick();
        World w = this.getWorld();
        if (w.isClient) {
            return;
        }
        int life = this.dataTracker.get(LIFE) + 1;
        this.dataTracker.set(LIFE, life);
        int hold = this.dataTracker.get(HOLD);
        if (this.landed) {
            if (++this.landedTicks > 12) {
                this.discard();
            }
            return;
        }
        if (life < hold) {
            Entity t = this.targetId != null && w instanceof ServerWorld sw ? sw.getEntity(this.targetId) : null;
            if (this.kind() == MARK && t instanceof LivingEntity le && le.isAlive()) {
                // висит над головой и следует за целью (с небольшим отставанием)
                double tx = MathHelper.lerp(0.25, this.getX(), le.getX());
                double tz = MathHelper.lerp(0.25, this.getZ(), le.getZ());
                double ty = MathHelper.lerp(0.25, this.getY(), le.getY() + le.getHeight() + 3.2);
                this.setPosition(tx, ty, tz);
                if (life % 20 == 0) {
                    w.playSound(null, tx, ty, tz, ModSounds.NABAT_MARK, SoundCategory.HOSTILE, 1.1f, 0.9f + life / 200f);
                }
                if (!le.hasStatusEffect(ModEffects.BELL_MARK)) {
                    le.addStatusEffect(new StatusEffectInstance(ModEffects.BELL_MARK, hold - life + 2, 0, false, false, true));
                }
            }
            return;
        }
        // падение
        double gy = this.groundY();
        double ny = this.getY() - FALL_SPEED;
        if (ny <= gy) {
            this.setPosition(this.getX(), gy, this.getZ());
            this.impact();
        } else {
            this.setPosition(this.getX(), ny, this.getZ());
        }
    }

    private void impact() {
        this.landed = true;
        if (!(this.getWorld() instanceof ServerWorld s)) {
            return;
        }
        boolean mark = this.kind() == MARK;
        double r = this.radius();
        float dmg = mark ? 9f : 5f;
        Entity owner = this.ownerId != null ? s.getEntity(this.ownerId) : null;
        LivingEntity src = owner instanceof LivingEntity le ? le : null;
        s.playSound(null, this.getX(), this.getY(), this.getZ(), mark ? ModSounds.NABAT_CAPTIVE : ModSounds.NABAT_BELL_LAND,
                SoundCategory.HOSTILE, mark ? 2.0f : 1.3f, mark ? 1.15f : 1.3f);
        BlockState ground = s.getBlockState(BlockPos.ofFloored(this.getX(), this.getY() - 0.5, this.getZ()));
        int pts = mark ? 22 : 12;
        for (int i = 0; i < pts; i++) {
            double a = i / (double) pts * Math.PI * 2;
            double x = this.getX() + Math.cos(a) * r * 0.8;
            double z = this.getZ() + Math.sin(a) * r * 0.8;
            if (!ground.isAir()) {
                s.spawnParticles(new BlockStateParticleEffect(ParticleTypes.BLOCK, ground), x, this.getY() + 0.1, z, 2,
                        0.1, 0.05, 0.1, 0.1);
            }
            s.spawnParticles(AMBER, x, this.getY() + 0.2, z, 1, 0, 0.05, 0, 0.02);
        }
        s.spawnParticles(ParticleTypes.CLOUD, this.getX(), this.getY() + 0.2, this.getZ(), mark ? 8 : 4, 0.4, 0.1, 0.4, 0.04);
        for (LivingEntity e : s.getEntitiesByClass(LivingEntity.class, this.getBoundingBox().expand(r + 1.0, 2.5, r + 1.0),
                e -> e.isAlive() && !(e instanceof Monster) && !NabatHooks.isKin(e)
                        && !(e instanceof PlayerEntity p && (p.isCreative() || p.isSpectator())))) {
            double dx = e.getX() - this.getX();
            double dz = e.getZ() - this.getZ();
            if (dx * dx + dz * dz <= (r + e.getWidth() / 2) * (r + e.getWidth() / 2) && Math.abs(e.getY() - this.getY()) < 2.5) {
                e.timeUntilRegen = 0;
                e.damage(src != null ? this.getDamageSources().mobAttack(src) : this.getDamageSources().magic(), dmg);
                if (mark) {
                    e.takeKnockback(1.0, this.getX() - e.getX(), this.getZ() - e.getZ());
                }
            }
        }
    }

    @Override
    public boolean shouldRender(double distance) {
        return distance < 128 * 128;
    }

    @Override
    protected void readCustomDataFromNbt(NbtCompound nbt) {
        this.dataTracker.set(KIND, nbt.getInt("Kind"));
        this.dataTracker.set(HOLD, Math.max(1, nbt.getInt("Hold")));
        this.dataTracker.set(LIFE, nbt.getInt("Life"));
        if (nbt.containsUuid("Owner")) {
            this.ownerId = nbt.getUuid("Owner");
        }
        if (nbt.containsUuid("Target")) {
            this.targetId = nbt.getUuid("Target");
        }
    }

    @Override
    protected void writeCustomDataToNbt(NbtCompound nbt) {
        nbt.putInt("Kind", this.kind());
        nbt.putInt("Hold", this.dataTracker.get(HOLD));
        nbt.putInt("Life", this.dataTracker.get(LIFE));
        if (this.ownerId != null) {
            nbt.putUuid("Owner", this.ownerId);
        }
        if (this.targetId != null) {
            nbt.putUuid("Target", this.targetId);
        }
    }

    @Override
    public Packet<ClientPlayPacketListener> createSpawnPacket() {
        return new EntitySpawnS2CPacket(this);
    }
}

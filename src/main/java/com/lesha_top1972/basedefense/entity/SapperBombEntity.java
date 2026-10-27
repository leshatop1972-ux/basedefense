package com.lesha_top1972.basedefense.entity;

import com.lesha_top1972.basedefense.sound.ModSounds;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.MovementType;
import net.minecraft.entity.data.DataTracker;
import net.minecraft.entity.data.TrackedData;
import net.minecraft.entity.data.TrackedDataHandlerRegistry;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.network.listener.ClientPlayPacketListener;
import net.minecraft.network.packet.Packet;
import net.minecraft.network.packet.s2c.play.EntitySpawnS2CPacket;
import net.minecraft.particle.DustParticleEffect;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;
import org.joml.Vector3f;

import java.util.UUID;

/**
 * «Бомба‑банка» подкопщика: летит по дуге, вращаясь, с искрящим фитилём; упав, 0.6 с шипит
 * (на земле — кольцо‑метка, сжимается), потом взрывается: r 2.5, 8 урона, ломает слабые блоки.
 */
public class SapperBombEntity extends Entity {

    public static final int FUSE = 12;
    static final double GRAVITY = 0.05;
    static final float DAMAGE = 8f;
    private static final DustParticleEffect SPARK = new DustParticleEffect(new Vector3f(1.0f, 0.62f, 0.18f), 0.6f);

    private static final TrackedData<Integer> LANDED = DataTracker.registerData(SapperBombEntity.class,
            TrackedDataHandlerRegistry.INTEGER);

    private UUID ownerId;

    public SapperBombEntity(EntityType<?> type, World world) {
        super(type, world);
    }

    /** Бросок по дуге в точку to за flight тиков. */
    void launch(SapperEntity owner, Vec3d from, Vec3d to, int flight) {
        this.ownerId = owner.getUuid();
        this.setPosition(from);
        double t = Math.max(6, flight);
        // x(t) = v·(1−0.99^t)/0.01 ≈ сумма, приближение с учётом сопротивления
        double drag = (1 - Math.pow(0.99, t)) / 0.01;
        double vx = (to.x - from.x) / drag;
        double vz = (to.z - from.z) / drag;
        double vy = (to.y - from.y + 0.5 * GRAVITY * t * t) / t;
        this.setVelocity(vx, vy, vz);
    }

    @Override
    protected void initDataTracker() {
        this.dataTracker.startTracking(LANDED, -1);
    }

    /** −1 — в полёте, иначе сколько тиков шипит на земле. */
    public int landedTicks() {
        return this.dataTracker.get(LANDED);
    }

    /** 0..1 — сколько прошло от падения до взрыва. */
    public float fuse(float tickDelta) {
        int l = this.landedTicks();
        return l < 0 ? 0f : MathHelper.clamp((l + tickDelta) / FUSE, 0f, 1f);
    }

    @Override
    public void tick() {
        super.tick();
        World w = this.getWorld();
        int landed = this.landedTicks();
        if (w.isClient) {
            if (landed < 0) {
                // искры с фитиля и тонкий дымовой след
                w.addParticle(SPARK, this.getX(), this.getY() + 0.25, this.getZ(), 0, 0, 0);
                if (this.age % 2 == 0) {
                    w.addParticle(ParticleTypes.SMOKE, this.getX(), this.getY() + 0.2, this.getZ(), 0, 0.01, 0);
                }
            } else if (this.random.nextInt(2) == 0) {
                w.addParticle(ParticleTypes.SMALL_FLAME, this.getX(), this.getY() + 0.32, this.getZ(),
                        (this.random.nextDouble() - 0.5) * 0.04, 0.04, (this.random.nextDouble() - 0.5) * 0.04);
                w.addParticle(SPARK, this.getX(), this.getY() + 0.3, this.getZ(),
                        (this.random.nextDouble() - 0.5) * 0.2, 0.15, (this.random.nextDouble() - 0.5) * 0.2);
            }
            return;
        }
        if (landed >= 0) {
            this.setVelocity(Vec3d.ZERO);
            this.dataTracker.set(LANDED, landed + 1);
            if (landed + 1 >= FUSE) {
                this.explode();
            }
            return;
        }
        Vec3d v = this.getVelocity();
        this.move(MovementType.SELF, v);
        if (this.isOnGround() || this.horizontalCollision || this.verticalCollision || this.isTouchingWater()) {
            this.setVelocity(Vec3d.ZERO);
            this.dataTracker.set(LANDED, 0);
            this.playSound(ModSounds.SAPPER_FUSE, 0.9f, 1.0f);
        } else {
            this.setVelocity(v.x * 0.99, v.y * 0.99 - GRAVITY, v.z * 0.99);
        }
        if (this.age > 120) {
            this.explode();
        }
    }

    private void explode() {
        if (this.getWorld() instanceof ServerWorld s && !this.isRemoved()) {
            Entity owner = this.ownerId != null ? s.getEntity(this.ownerId) : null;
            SapperBlast.bomb(s, this.getPos().add(0, 0.3, 0), DAMAGE, owner);
        }
        this.discard();
    }

    @Override
    protected void readCustomDataFromNbt(NbtCompound nbt) {
        this.discard();          // живёт секунды
    }

    @Override
    protected void writeCustomDataToNbt(NbtCompound nbt) {
    }

    @Override
    public boolean shouldRender(double distance) {
        return distance < 96 * 96;
    }

    @Override
    public Packet<ClientPlayPacketListener> createSpawnPacket() {
        return new EntitySpawnS2CPacket(this);
    }
}

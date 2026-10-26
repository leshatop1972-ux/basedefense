package com.lesha_top1972.basedefense.entity;

import com.lesha_top1972.basedefense.sound.ModSounds;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.damage.DamageSource;
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
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.Heightmap;
import net.minecraft.world.World;
import org.joml.Vector3f;

import java.util.UUID;

/**
 * Душа «Реквиема»: выходит из убитого слуги Набата. 6 секунд медленно ползёт огоньком ПО ЗЕМЛЕ к Набату,
 * потом резко взлетает к нему. Долетела — у Набата +стаки «Реквиема» (мечник 2, улан 8, остальные 1).
 * Сбивается одним ударом или стрелой.
 */
public class NabatSoulEntity extends Entity {

    public static final int CRAWL_TICKS = 120;
    static final double CRAWL_SPEED = 0.06;
    static final double DASH_SPEED = 1.0;
    static final int MAX_LIFE = 400;

    private static final TrackedData<Boolean> DASH = DataTracker.registerData(NabatSoulEntity.class,
            TrackedDataHandlerRegistry.BOOLEAN);
    private static final TrackedData<Boolean> BIG = DataTracker.registerData(NabatSoulEntity.class,
            TrackedDataHandlerRegistry.BOOLEAN);
    private static final DustParticleEffect AMBER = new DustParticleEffect(new Vector3f(1.0f, 0.72f, 0.3f), 0.8f);
    private static final DustParticleEffect TEAL = new DustParticleEffect(new Vector3f(0.3f, 0.95f, 0.9f), 0.9f);

    private UUID nabat;
    private int stacks = 1;
    private int life;

    public NabatSoulEntity(EntityType<?> type, World world) {
        super(type, world);
        this.noClip = true;
        this.setNoGravity(true);
    }

    void setup(NabatEntity owner, int stacks, boolean lancer) {
        this.nabat = owner.getUuid();
        this.stacks = stacks;
        this.dataTracker.set(BIG, lancer);
    }

    @Override
    protected void initDataTracker() {
        this.dataTracker.startTracking(DASH, false);
        this.dataTracker.startTracking(BIG, false);
    }

    public boolean isDashing() {
        return this.dataTracker.get(DASH);
    }

    public boolean isBig() {
        return this.dataTracker.get(BIG);
    }

    private NabatEntity owner() {
        if (this.nabat != null && this.getWorld() instanceof ServerWorld sw && sw.getEntity(this.nabat) instanceof NabatEntity n
                && n.isAlive()) {
            return n;
        }
        return null;
    }

    @Override
    public void tick() {
        super.tick();
        World w = this.getWorld();
        if (w.isClient) {
            this.clientFx();
            return;
        }
        this.life++;
        NabatEntity n = this.owner();
        if (n == null || this.life > MAX_LIFE) {
            this.discard();
            return;
        }
        Vec3d target = n.soulTarget();
        if (this.life < CRAWL_TICKS) {
            // ползёт по земле: по горизонтали к Набату, высота — над верхним блоком в этой точке
            double dx = target.x - this.getX();
            double dz = target.z - this.getZ();
            double d = Math.sqrt(dx * dx + dz * dz);
            double step = Math.min(CRAWL_SPEED, d);
            double nx = this.getX() + (d > 1e-4 ? dx / d * step : 0);
            double nz = this.getZ() + (d > 1e-4 ? dz / d * step : 0);
            int top = w.getTopY(Heightmap.Type.MOTION_BLOCKING_NO_LEAVES, MathHelper.floor(nx), MathHelper.floor(nz));
            double ny = MathHelper.lerp(0.25, this.getY(), top + 0.25 + 0.08 * Math.sin(this.life * 0.25));
            this.setPosition(nx, ny, nz);
            if (this.life % 60 == 0 && this.random.nextBoolean()) {
                w.playSound(null, this.getX(), this.getY(), this.getZ(), ModSounds.NABAT_SOUL, SoundCategory.HOSTILE, 0.5f,
                        0.9f + this.random.nextFloat() * 0.2f);
            }
            if (this.life == CRAWL_TICKS - 1) {
                this.dataTracker.set(DASH, true);
                w.playSound(null, this.getX(), this.getY(), this.getZ(), ModSounds.NABAT_SOUL_DASH, SoundCategory.HOSTILE,
                        1.0f, 1.0f);
            }
            return;
        }
        Vec3d to = target.subtract(this.getPos());
        double d = to.length();
        if (d < 1.6) {
            n.absorbSoul(this.stacks, this.getPos());
            this.discard();
            return;
        }
        Vec3d v = to.multiply(Math.min(DASH_SPEED, d) / d);
        this.setPosition(this.getX() + v.x, this.getY() + v.y, this.getZ() + v.z);
    }

    private void clientFx() {
        World w = this.getWorld();
        DustParticleEffect dust = this.isBig() ? TEAL : AMBER;
        if (this.isDashing()) {
            w.addParticle(dust, this.getX(), this.getY() + 0.2, this.getZ(), 0, 0, 0);
            w.addParticle(ParticleTypes.END_ROD, this.getX(), this.getY() + 0.2, this.getZ(), 0, 0, 0);
        } else if (this.age % 3 == 0) {
            w.addParticle(dust, this.getX() + (this.random.nextDouble() - 0.5) * 0.2, this.getY() + 0.1,
                    this.getZ() + (this.random.nextDouble() - 0.5) * 0.2, 0, 0.01, 0);
        }
    }

    /** Сбить душу: любой удар или стрела. */
    @Override
    public boolean damage(DamageSource source, float amount) {
        if (this.isInvulnerableTo(source) || this.getWorld().isClient || this.isRemoved()) {
            return false;
        }
        if (this.getWorld() instanceof ServerWorld sw) {
            sw.spawnParticles(this.isBig() ? TEAL : AMBER, this.getX(), this.getY() + 0.2, this.getZ(), 14, 0.2, 0.2, 0.2, 0.05);
            sw.spawnParticles(ParticleTypes.END_ROD, this.getX(), this.getY() + 0.2, this.getZ(), 6, 0.1, 0.1, 0.1, 0.06);
            sw.playSound(null, this.getX(), this.getY(), this.getZ(), ModSounds.NABAT_SOUL_POP, SoundCategory.HOSTILE, 1.0f,
                    this.isBig() ? 0.7f : 1.0f);
        }
        if (this.isBig()) {
            this.stacks = Math.max(0, this.stacks - 2);       // душа улана крепче: 4 удара
            if (this.stacks > 0) {
                return true;
            }
        }
        this.discard();
        return true;
    }

    @Override
    public boolean canHit() {
        return !this.isRemoved();
    }

    @Override
    public boolean isAttackable() {
        return true;
    }

    @Override
    public boolean canBeHitByProjectile() {
        return true;
    }

    @Override
    public boolean shouldRender(double distance) {
        return distance < 96 * 96;
    }

    @Override
    protected void readCustomDataFromNbt(NbtCompound nbt) {
        if (nbt.containsUuid("Nabat")) {
            this.nabat = nbt.getUuid("Nabat");
        }
        this.stacks = nbt.getInt("Stacks");
        this.life = nbt.getInt("Life");
        this.dataTracker.set(BIG, nbt.getBoolean("Big"));
    }

    @Override
    protected void writeCustomDataToNbt(NbtCompound nbt) {
        if (this.nabat != null) {
            nbt.putUuid("Nabat", this.nabat);
        }
        nbt.putInt("Stacks", this.stacks);
        nbt.putInt("Life", this.life);
        nbt.putBoolean("Big", this.isBig());
    }

    @Override
    public Packet<ClientPlayPacketListener> createSpawnPacket() {
        return new EntitySpawnS2CPacket(this);
    }

    /** Блок под душой — для теней и т.п. */
    BlockPos below() {
        return BlockPos.ofFloored(this.getX(), this.getY() - 0.5, this.getZ());
    }
}

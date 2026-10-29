package com.lesha_top1972.basedefense.entity.enemy.curb;

import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.MovementType;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.network.listener.ClientPlayPacketListener;
import net.minecraft.network.packet.Packet;
import net.minecraft.network.packet.s2c.play.EntitySpawnS2CPacket;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;

/**
 * Чёрный чемодан, который вылетает из‑за спины Бори228 после «Я не сдамся» (израсходованный картридж).
 * Летит назад ≈3 блока, кувыркаясь, падает плашмя, лежит и исчезает в дыму. Только украшение: не бьёт,
 * не подбирается, не сохраняется в мире.
 */
public class CurbCaseEntity extends Entity {

    /** Сколько живёт всего (тики). */
    static final int LIFE = 70;

    /** Клиент: тик приземления (для отрисовки «плашмя»), −1 — ещё летит. */
    public int clientLandedAge = -1;
    private boolean landed;

    public CurbCaseEntity(EntityType<?> type, World world) {
        super(type, world);
    }

    @Override
    protected void initDataTracker() {
    }

    @Override
    public void tick() {
        super.tick();
        Vec3d v = this.getVelocity();
        if (!this.hasNoGravity()) {
            v = v.add(0, -0.04, 0);
        }
        this.setVelocity(v);
        this.move(MovementType.SELF, this.getVelocity());
        if (this.isOnGround()) {
            this.setVelocity(this.getVelocity().multiply(0.5, 0, 0.5));
            if (!this.landed) {
                this.landed = true;
                if (this.getWorld().isClient) {
                    this.clientLandedAge = this.age;
                } else {
                    this.getWorld().playSound(null, this.getX(), this.getY(), this.getZ(), SoundEvents.BLOCK_CHEST_CLOSE,
                            SoundCategory.HOSTILE, 0.8f, 1.4f);
                }
            }
        } else {
            this.setVelocity(this.getVelocity().multiply(0.98));
        }
        if (!this.getWorld().isClient && this.age >= LIFE && this.getWorld() instanceof ServerWorld s) {
            s.spawnParticles(ParticleTypes.LARGE_SMOKE, this.getX(), this.getY() + 0.2, this.getZ(), 8, 0.2, 0.1, 0.2, 0.02);
            s.spawnParticles(ParticleTypes.WITCH, this.getX(), this.getY() + 0.2, this.getZ(), 6, 0.2, 0.1, 0.2, 0.05);
            s.playSound(null, this.getX(), this.getY(), this.getZ(), SoundEvents.BLOCK_FIRE_EXTINGUISH,
                    SoundCategory.HOSTILE, 0.4f, 1.6f);
            this.discard();
        }
    }

    @Override
    public boolean shouldSave() {
        return false;
    }

    @Override
    protected void readCustomDataFromNbt(NbtCompound nbt) {
    }

    @Override
    protected void writeCustomDataToNbt(NbtCompound nbt) {
    }

    @Override
    public Packet<ClientPlayPacketListener> createSpawnPacket() {
        return new EntitySpawnS2CPacket(this);
    }

    @Override
    public boolean isAttackable() {
        return false;
    }

    @Override
    public boolean canHit() {
        return false;
    }
}

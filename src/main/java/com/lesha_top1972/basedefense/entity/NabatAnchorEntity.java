package com.lesha_top1972.basedefense.entity;

import com.lesha_top1972.basedefense.sound.ModSounds;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.attribute.DefaultAttributeContainer;
import net.minecraft.entity.attribute.EntityAttributes;
import net.minecraft.entity.damage.DamageSource;
import net.minecraft.entity.data.DataTracker;
import net.minecraft.entity.data.TrackedData;
import net.minecraft.entity.data.TrackedDataHandlerRegistry;
import net.minecraft.entity.mob.MobEntity;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.particle.DustParticleEffect;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvent;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;
import org.joml.Vector3f;

import java.util.UUID;

/**
 * Якорь узника: кол с открытым реликварием, к нему золотой цепью прикован улан.
 * Игроки его не повредят — его ломают только удары самого улана (таран, «Тройной таран», копья «Ливня»):
 * встань за якорем так, чтобы улан ударил сквозь него. Три попадания — цепь рвётся, улан «Освобождён».
 */
public class NabatAnchorEntity extends MobEntity {

    public static final int HITS = 3;
    static final int HIT_COOLDOWN = 12;
    private static final DustParticleEffect TEAL = new DustParticleEffect(new Vector3f(0.3f, 0.95f, 0.9f), 1.0f);
    private static final TrackedData<Integer> LANCER = DataTracker.registerData(NabatAnchorEntity.class,
            TrackedDataHandlerRegistry.INTEGER);
    private static final TrackedData<Integer> CRACKS = DataTracker.registerData(NabatAnchorEntity.class,
            TrackedDataHandlerRegistry.INTEGER);

    private UUID ownerId;
    private UUID lancerId;
    private int hits;
    private long lastHit = -100;

    public NabatAnchorEntity(EntityType<? extends MobEntity> type, World world) {
        super(type, world);
        this.setPersistent();
        this.addCommandTag(NabatHooks.NO_SOUL);
    }

    public static DefaultAttributeContainer.Builder createAttributes() {
        return MobEntity.createMobAttributes()
                .add(EntityAttributes.GENERIC_MAX_HEALTH, 100.0)
                .add(EntityAttributes.GENERIC_KNOCKBACK_RESISTANCE, 1.0)
                .add(EntityAttributes.GENERIC_MOVEMENT_SPEED, 0.0);
    }

    @Override
    protected void initDataTracker() {
        super.initDataTracker();
        this.dataTracker.startTracking(LANCER, -1);
        this.dataTracker.startTracking(CRACKS, 0);
    }

    void setup(NabatEntity owner, TatteredLancerEntity lancer) {
        this.ownerId = owner.getUuid();
        this.lancerId = lancer.getUuid();
        this.dataTracker.set(LANCER, lancer.getId());
    }

    /** Клиенту: номер сущности улана (для цепи) и сколько раз её уже ранили. */
    public int lancerNetId() {
        return this.dataTracker.get(LANCER);
    }

    public int cracks() {
        return this.dataTracker.get(CRACKS);
    }

    UUID lancerUuid() {
        return this.lancerId;
    }

    @Override
    protected void initGoals() {
    }

    @Override
    public void tick() {
        super.tick();
        this.setVelocity(0, Math.min(0, this.getVelocity().y), 0);
        Entity l = this.getWorld() instanceof ServerWorld s && this.lancerId != null ? s.getEntity(this.lancerId) : null;
        if (l != null) {
            if (this.dataTracker.get(LANCER) != l.getId()) {
                this.dataTracker.set(LANCER, l.getId());       // после перезагрузки мира номер другой
            }
        }
    }

    /** Повредить якорь может только улан. */
    @Override
    public boolean damage(DamageSource source, float amount) {
        if (this.getWorld().isClient || !(source.getAttacker() instanceof TatteredLancerEntity)) {
            return false;
        }
        long now = this.getWorld().getTime();
        if (now - this.lastHit < HIT_COOLDOWN) {
            return false;
        }
        this.lastHit = now;
        this.hits++;
        this.dataTracker.set(CRACKS, this.hits);
        if (this.getWorld() instanceof ServerWorld s) {
            s.spawnParticles(TEAL, this.getX(), this.getY() + 1.4, this.getZ(), 24, 0.4, 0.5, 0.4, 0.08);
            s.spawnParticles(ParticleTypes.ENCHANTED_HIT, this.getX(), this.getY() + 1.4, this.getZ(), 12, 0.3, 0.4, 0.3, 0.3);
            s.playSound(null, this.getX(), this.getY(), this.getZ(), ModSounds.NABAT_CHIME, SoundCategory.HOSTILE, 2.0f,
                    0.6f + this.hits * 0.15f);
        }
        if (this.hits >= HITS) {
            if (this.getWorld() instanceof ServerWorld s && this.ownerId != null && s.getEntity(this.ownerId) instanceof NabatEntity n) {
                n.onChainBroken(this);
            }
            this.breakApart();
        }
        return true;
    }

    void breakApart() {
        if (this.getWorld() instanceof ServerWorld s) {
            s.playSound(null, this.getX(), this.getY(), this.getZ(), ModSounds.NABAT_CHAIN_BREAK, SoundCategory.HOSTILE, 2.5f, 1.0f);
            s.spawnParticles(TEAL, this.getX(), this.getY() + 1.2, this.getZ(), 50, 0.6, 0.8, 0.6, 0.15);
            s.spawnParticles(ParticleTypes.END_ROD, this.getX(), this.getY() + 1.2, this.getZ(), 20, 0.4, 0.6, 0.4, 0.12);
        }
        this.discard();
    }

    /** Точка, к которой крепится цепь (кольцо на колу). */
    public Vec3d chainPoint() {
        return this.getPos().add(0, 0.8, 0);
    }

    @Override
    public boolean isPushable() {
        return false;
    }

    @Override
    protected void pushAway(Entity entity) {
    }

    @Override
    public boolean canImmediatelyDespawn(double distanceSquared) {
        return false;
    }

    @Override
    public boolean isFireImmune() {
        return true;
    }

    @Override
    protected SoundEvent getHurtSound(DamageSource source) {
        return null;
    }

    @Override
    protected SoundEvent getDeathSound() {
        return null;
    }

    @Override
    public void writeCustomDataToNbt(NbtCompound nbt) {
        super.writeCustomDataToNbt(nbt);
        if (this.ownerId != null) {
            nbt.putUuid("Owner", this.ownerId);
        }
        if (this.lancerId != null) {
            nbt.putUuid("Lancer", this.lancerId);
        }
        nbt.putInt("Hits", this.hits);
    }

    @Override
    public void readCustomDataFromNbt(NbtCompound nbt) {
        super.readCustomDataFromNbt(nbt);
        if (nbt.containsUuid("Owner")) {
            this.ownerId = nbt.getUuid("Owner");
        }
        if (nbt.containsUuid("Lancer")) {
            this.lancerId = nbt.getUuid("Lancer");
        }
        this.hits = nbt.getInt("Hits");
        this.dataTracker.set(CRACKS, this.hits);
    }
}

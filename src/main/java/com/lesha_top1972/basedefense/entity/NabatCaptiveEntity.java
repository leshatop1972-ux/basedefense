package com.lesha_top1972.basedefense.entity;

import com.lesha_top1972.basedefense.effect.ModEffects;
import com.lesha_top1972.basedefense.sound.ModSounds;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.attribute.DefaultAttributeContainer;
import net.minecraft.entity.attribute.EntityAttributes;
import net.minecraft.entity.damage.DamageSource;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.mob.MobEntity;
import net.minecraft.entity.player.PlayerEntity;
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
 * «Колокольный плен»: Набат накрывает самого шумного огромным колоколом. Внутри темно и громко:
 * урон 1.5 в секунду и +0.5 каждую следующую секунду. Колокол (120 HP) бьют снаружи друзья,
 * а пленник изнутри (его удары ×3). Не вскрыли за 6 с — «Оглушение II» на 3 с, колокол рассыпается.
 */
public class NabatCaptiveEntity extends MobEntity {

    static final int DROP_TICKS = 10;
    static final int TRAP_TICKS = 120;
    static final float INSIDE_FACTOR = 3f;
    private static final DustParticleEffect AMBER = new DustParticleEffect(new Vector3f(1.0f, 0.7f, 0.3f), 1.2f);

    private UUID ownerId;
    private UUID victimId;
    private int life;
    private double groundY;

    public NabatCaptiveEntity(EntityType<? extends MobEntity> type, World world) {
        super(type, world);
        this.setPersistent();
        this.setNoGravity(true);
        this.addCommandTag(NabatHooks.TAG);
        this.addCommandTag(NabatHooks.NO_SOUL);
    }

    public static DefaultAttributeContainer.Builder createAttributes() {
        return MobEntity.createMobAttributes()
                .add(EntityAttributes.GENERIC_MAX_HEALTH, 120.0)
                .add(EntityAttributes.GENERIC_KNOCKBACK_RESISTANCE, 1.0)
                .add(EntityAttributes.GENERIC_MOVEMENT_SPEED, 0.0);
    }

    void setup(NabatEntity owner, PlayerEntity victim, double groundY) {
        this.ownerId = owner.getUuid();
        this.victimId = victim.getUuid();
        this.groundY = groundY;
    }

    @Override
    protected void initGoals() {
    }

    /** Клиенту: сколько тиков прошло (для тряски модели). */
    public int life() {
        return this.age;
    }

    private PlayerEntity victim() {
        return this.victimId != null ? this.getWorld().getPlayerByUuid(this.victimId) : null;
    }

    @Override
    public void tick() {
        super.tick();
        if (this.getWorld().isClient) {
            return;
        }
        this.life++;
        this.setVelocity(Vec3d.ZERO);
        PlayerEntity v = this.victim();
        if (this.life <= DROP_TICKS) {
            double y = this.groundY + 6.0 * (1.0 - this.life / (double) DROP_TICKS);
            this.setPosition(this.getX(), y, this.getZ());
            if (this.life == DROP_TICKS) {
                this.sound(ModSounds.NABAT_CAPTIVE, 2.5f, 0.8f);
                if (this.getWorld() instanceof ServerWorld s) {
                    s.spawnParticles(ParticleTypes.CLOUD, this.getX(), this.groundY + 0.2, this.getZ(), 16, 1.0, 0.1, 1.0, 0.06);
                }
            }
            return;
        }
        if (v == null || !v.isAlive() || v.isSpectator()) {
            this.shatter(false);
            return;
        }
        // пленник не выйдет: держим его в центре колокола
        double dx = v.getX() - this.getX();
        double dz = v.getZ() - this.getZ();
        if (dx * dx + dz * dz > 0.45 * 0.45 || v.getY() > this.groundY + 1.0) {
            v.requestTeleport(this.getX(), Math.min(v.getY(), this.groundY + 0.05), this.getZ());
        }
        int inside = this.life - DROP_TICKS;
        if (inside % 20 == 0) {
            int sec = inside / 20;
            float dmg = 1.5f + 0.5f * (sec - 1);
            Entity o = this.ownerId != null && this.getWorld() instanceof ServerWorld s ? s.getEntity(this.ownerId) : null;
            v.timeUntilRegen = 0;
            v.damage(o instanceof LivingEntity le ? this.getDamageSources().mobAttack(le) : this.getDamageSources().magic(), dmg);
            this.sound(ModSounds.NABAT_CHIME, 1.6f, 0.6f + sec * 0.06f);
        }
        if (inside >= TRAP_TICKS) {
            v.addStatusEffect(new StatusEffectInstance(ModEffects.STUN, 60, 1, false, true, true), this);
            this.shatter(true);
        }
    }

    private void sound(SoundEvent s, float vol, float pitch) {
        this.getWorld().playSound(null, this.getX(), this.getY(), this.getZ(), s, SoundCategory.HOSTILE, vol, pitch);
    }

    private void shatter(boolean timeout) {
        if (this.getWorld() instanceof ServerWorld s) {
            this.sound(ModSounds.NABAT_BELL_BREAK, 2.5f, timeout ? 0.75f : 0.9f);
            s.spawnParticles(AMBER, this.getX(), this.getY() + 1.3, this.getZ(), 40, 0.9, 1.0, 0.9, 0.12);
            s.spawnParticles(ParticleTypes.END_ROD, this.getX(), this.getY() + 1.3, this.getZ(), 14, 0.6, 0.7, 0.6, 0.1);
        }
        this.discard();
    }

    @Override
    public boolean damage(DamageSource source, float amount) {
        if (this.life <= DROP_TICKS || (source.getAttacker() != null && NabatHooks.isKin(source.getAttacker()))) {
            return false;
        }
        PlayerEntity v = this.victim();
        if (v != null && source.getAttacker() == v) {
            amount *= INSIDE_FACTOR;            // изнутри — по языку колокола
        }
        boolean r = super.damage(source, amount);
        if (r) {
            this.sound(ModSounds.NABAT_CHIME, 1.2f, 0.7f + this.random.nextFloat() * 0.3f);
        }
        return r;
    }

    @Override
    protected void updatePostDeath() {
        if (this.deathTime == 0) {
            this.shatter(false);
        }
        super.updatePostDeath();
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
        if (this.victimId != null) {
            nbt.putUuid("Victim", this.victimId);
        }
        nbt.putInt("Life", this.life);
        nbt.putDouble("GroundY", this.groundY);
    }

    @Override
    public void readCustomDataFromNbt(NbtCompound nbt) {
        super.readCustomDataFromNbt(nbt);
        if (nbt.containsUuid("Owner")) {
            this.ownerId = nbt.getUuid("Owner");
        }
        if (nbt.containsUuid("Victim")) {
            this.victimId = nbt.getUuid("Victim");
        }
        this.life = nbt.getInt("Life");
        this.groundY = nbt.getDouble("GroundY");
    }
}

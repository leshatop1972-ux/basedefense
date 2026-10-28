package com.lesha_top1972.basedefense.entity.enemy.sapper;

import com.lesha_top1972.basedefense.sound.ModSounds;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.MovementType;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.network.listener.ClientPlayPacketListener;
import net.minecraft.network.packet.Packet;
import net.minecraft.network.packet.s2c.play.EntitySpawnS2CPacket;
import net.minecraft.particle.DustParticleEffect;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;
import org.joml.Vector3f;

/**
 * Дымовая банка: густое серое облако на 3 с, медленно расходится до радиуса 2.5.
 * Тем, кто внутри, — «Слепота I» на 1 с (обновляется). Частицы рисует клиент сам — пакетов нет.
 */
public class SapperSmokeEntity extends Entity {

    public static final int LIFE = 60;
    static final double RADIUS = 2.5;
    private static final DustParticleEffect GREY = new DustParticleEffect(new Vector3f(0.55f, 0.55f, 0.53f), 3.0f);
    private static final DustParticleEffect DARK = new DustParticleEffect(new Vector3f(0.32f, 0.31f, 0.3f), 2.6f);

    public SapperSmokeEntity(EntityType<?> type, World world) {
        super(type, world);
    }

    public double radius(float tickDelta) {
        return 0.8 + (RADIUS - 0.8) * Math.min(1.0, (this.age + tickDelta) / 30.0);
    }

    @Override
    protected void initDataTracker() {
    }

    @Override
    public void tick() {
        super.tick();
        World w = this.getWorld();
        if (!this.isOnGround()) {
            this.setVelocity(this.getVelocity().multiply(0.9).add(0, -0.06, 0));
            this.move(MovementType.SELF, this.getVelocity());
        }
        if (w.isClient) {
            if (this.age < LIFE - 8) {
                double r = this.radius(0f);
                int n = this.age < 10 ? 6 : 3;
                for (int i = 0; i < n; i++) {
                    double ang = this.random.nextDouble() * Math.PI * 2;
                    double d = Math.sqrt(this.random.nextDouble()) * r;
                    w.addParticle(i % 3 == 0 ? ParticleTypes.CAMPFIRE_COSY_SMOKE : (i % 2 == 0 ? GREY : DARK),
                            this.getX() + Math.cos(ang) * d, this.getY() + 0.2 + this.random.nextDouble() * 1.6,
                            this.getZ() + Math.sin(ang) * d, Math.cos(ang) * 0.01, 0.005, Math.sin(ang) * 0.01);
                }
                if (this.age % 3 == 0) {
                    w.addParticle(ParticleTypes.LARGE_SMOKE, this.getX(), this.getY() + 0.3, this.getZ(), 0, 0.05, 0);
                }
            }
            return;
        }
        if (this.age == 1) {
            this.playSound(ModSounds.SAPPER_SMOKE, 1.0f, 1.0f);
        }
        if (this.age % 5 == 0) {
            double r = this.radius(0f);
            for (PlayerEntity p : w.getEntitiesByClass(PlayerEntity.class, new Box(this.getPos(), this.getPos()).expand(r, 2.0, r),
                    p -> !p.isCreative() && !p.isSpectator())) {
                if (p.squaredDistanceTo(this.getX(), p.getY(), this.getZ()) <= r * r) {
                    p.addStatusEffect(new StatusEffectInstance(StatusEffects.BLINDNESS, 25, 0, false, false, true));
                }
            }
        }
        if (this.age >= LIFE) {
            this.discard();
        }
    }

    void toss(Vec3d at, Vec3d vel) {
        this.setPosition(at);
        this.setVelocity(vel);
    }

    @Override
    protected void readCustomDataFromNbt(NbtCompound nbt) {
        this.discard();
    }

    @Override
    protected void writeCustomDataToNbt(NbtCompound nbt) {
    }

    @Override
    public Packet<ClientPlayPacketListener> createSpawnPacket() {
        return new EntitySpawnS2CPacket(this);
    }
}

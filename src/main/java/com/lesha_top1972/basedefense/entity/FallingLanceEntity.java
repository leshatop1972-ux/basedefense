package com.lesha_top1972.basedefense.entity;

import net.minecraft.block.BlockState;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.decoration.ArmorStandEntity;
import net.minecraft.entity.mob.Monster;
import net.minecraft.entity.projectile.ProjectileEntity;
import net.minecraft.entity.projectile.ProjectileUtil;
import net.minecraft.particle.BlockStateParticleEffect;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;

/**
 * Призрачное копьё из «ливня копий» улана: падает с неба, при ударе о землю или цель —
 * 6.5 урона всем рядом (радиус 1.3), втыкается и через секунду рассыпается. Монстров не задевает.
 */
public class FallingLanceEntity extends ProjectileEntity {

    public static final float DAMAGE = 6.5f;
    private int life;
    private int stuck = -1;

    public FallingLanceEntity(EntityType<? extends FallingLanceEntity> type, World world) {
        super(type, world);
        this.setNoGravity(true);
    }

    public static void drop(World world, LivingEntity owner, double x, double y, double z) {
        FallingLanceEntity lance = new FallingLanceEntity(ModEntities.FALLING_LANCE, world);
        lance.setOwner(owner);
        lance.setPosition(x, y, z);
        Vec3d v = new Vec3d((world.random.nextDouble() - 0.5) * 0.12, -1.5, (world.random.nextDouble() - 0.5) * 0.12);
        lance.setVelocity(v);
        lance.face(v);
        lance.prevYaw = lance.getYaw();
        lance.prevPitch = lance.getPitch();
        world.spawnEntity(lance);
    }

    @Override
    protected void initDataTracker() {
    }

    public boolean isStuck() {
        return this.stuck >= 0;
    }

    private void face(Vec3d v) {
        double h = v.horizontalLength();
        this.setYaw((float) (MathHelper.atan2(v.x, v.z) * 57.29577951308232));
        this.setPitch((float) (MathHelper.atan2(v.y, h) * 57.29577951308232));
    }

    @Override
    public void tick() {
        super.tick();
        World world = this.getWorld();
        this.prevYaw = this.getYaw();
        this.prevPitch = this.getPitch();
        if (this.stuck >= 0) {
            this.stuck++;
            if (world.isClient && this.stuck % 4 == 0) {
                world.addParticle(ParticleTypes.SOUL_FIRE_FLAME, this.getX(), this.getY() + 0.3, this.getZ(), 0, 0.02, 0);
            }
            if (!world.isClient && this.stuck > 20) {
                if (world instanceof ServerWorld sw) {
                    sw.spawnParticles(ParticleTypes.SOUL, this.getX(), this.getY() + 0.5, this.getZ(), 3, 0.1, 0.4, 0.1, 0.02);
                }
                this.discard();
            }
            return;
        }
        Vec3d v = this.getVelocity();
        HitResult hit = ProjectileUtil.getCollision(this, this::canHit);
        if (hit.getType() != HitResult.Type.MISS) {
            Vec3d p = hit.getPos();
            this.setPosition(p.x, p.y, p.z);
            this.setVelocity(Vec3d.ZERO);
            this.stuck = 0;
            if (!world.isClient) {
                this.impact(p);
            }
            return;
        }
        this.setPosition(this.getX() + v.x, this.getY() + v.y, this.getZ() + v.z);
        this.face(v);
        if (world.isClient) {
            world.addParticle(ParticleTypes.END_ROD, this.getX(), this.getY() + 1.0, this.getZ(), 0, 0.05, 0);
        } else if (++this.life > 100) {
            this.discard();
        }
    }

    private void impact(Vec3d p) {
        World world = this.getWorld();
        Entity owner = this.getOwner();
        LivingEntity attacker = owner instanceof LivingEntity l ? l : null;
        for (LivingEntity e : world.getEntitiesByClass(LivingEntity.class, new Box(p, p).expand(1.3, 1.5, 1.3),
                e -> e.isAlive() && !(e instanceof Monster) && !(e instanceof ArmorStandEntity))) {
            if (e.squaredDistanceTo(p.x, e.getY(), p.z) <= 1.3 * 1.3 + e.getWidth()) {
                e.timeUntilRegen = 0;
                if (attacker instanceof TatteredLancerEntity lancer) {
                    lancer.strike(e, DAMAGE, 0.2, 0.15, true);
                } else {
                    e.damage(this.getDamageSources().mobProjectile(this, attacker), DAMAGE);
                }
            }
        }
        world.playSound(null, p.x, p.y, p.z, SoundEvents.ITEM_TRIDENT_HIT_GROUND, SoundCategory.HOSTILE, 0.9f,
                0.9f + this.random.nextFloat() * 0.3f);
        if (world instanceof ServerWorld sw) {
            BlockState ground = world.getBlockState(BlockPos.ofFloored(p.x, p.y - 0.2, p.z));
            if (!ground.isAir()) {
                sw.spawnParticles(new BlockStateParticleEffect(ParticleTypes.BLOCK, ground), p.x, p.y + 0.1, p.z, 10,
                        0.3, 0.1, 0.3, 0.15);
            }
            sw.spawnParticles(ParticleTypes.CRIT, p.x, p.y + 0.3, p.z, 6, 0.3, 0.2, 0.3, 0.2);
            sw.spawnParticles(ParticleTypes.SOUL_FIRE_FLAME, p.x, p.y + 0.2, p.z, 4, 0.2, 0.1, 0.2, 0.03);
        }
    }

    @Override
    protected boolean canHit(Entity entity) {
        return super.canHit(entity) && !(entity instanceof Monster);
    }

    @Override
    public boolean shouldRender(double distance) {
        return distance < 6400.0;
    }
}

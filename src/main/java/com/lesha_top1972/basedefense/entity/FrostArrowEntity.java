package com.lesha_top1972.basedefense.entity;

import com.lesha_top1972.basedefense.effect.ModEffects;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.mob.Monster;
import net.minecraft.entity.projectile.ProjectileEntity;
import net.minecraft.entity.projectile.ProjectileUtil;
import net.minecraft.particle.DustParticleEffect;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.hit.EntityHitResult;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;
import org.joml.Vector3f;

/**
 * Ледяная стрела морозной лучницы: летит прямо (без гравитации), оставляет бело‑синий след.
 * Попадание: 14 урона и «Заморозка» на 2 секунды. Монстров пролетает насквозь.
 * Если лучница «попала» (бросок точности), стрела слегка доворачивает к цели.
 */
public class FrostArrowEntity extends ProjectileEntity {

    public static final float DAMAGE = 14f;
    public static final int FREEZE_TICKS = 40;
    public static final double SPEED = 1.6;
    private static final int MAX_LIFE = 80;
    private static final DustParticleEffect BLUE = new DustParticleEffect(new Vector3f(0.45f, 0.78f, 1.0f), 1.0f);

    private int life;
    private Entity homingTarget;
    private double homing;

    public FrostArrowEntity(EntityType<? extends FrostArrowEntity> type, World world) {
        super(type, world);
        this.setNoGravity(true);
    }

    public static FrostArrowEntity shoot(World world, LivingEntity owner, Vec3d from, Vec3d direction,
                                         Entity homingTarget, double homing) {
        FrostArrowEntity arrow = new FrostArrowEntity(ModEntities.FROST_ARROW, world);
        arrow.setOwner(owner);
        arrow.setPosition(from);
        Vec3d v = direction.normalize().multiply(SPEED);
        arrow.setVelocity(v);
        arrow.homingTarget = homingTarget;
        arrow.homing = homing;
        arrow.updateFacing(v);
        arrow.prevYaw = arrow.getYaw();
        arrow.prevPitch = arrow.getPitch();
        world.spawnEntity(arrow);
        return arrow;
    }

    @Override
    protected void initDataTracker() {
    }

    private void updateFacing(Vec3d v) {
        double h = v.horizontalLength();
        this.setYaw((float) (MathHelper.atan2(v.x, v.z) * 57.29577951308232));
        this.setPitch((float) (MathHelper.atan2(v.y, h) * 57.29577951308232));
    }

    @Override
    public void tick() {
        super.tick();
        World world = this.getWorld();

        if (!world.isClient && this.homingTarget != null && this.homing > 0) {
            if (this.homingTarget.isAlive() && this.homingTarget.getWorld() == world) {
                Vec3d to = this.homingTarget.getPos().add(0, this.homingTarget.getHeight() * 0.55, 0).subtract(this.getPos());
                if (to.lengthSquared() > 0.5) {
                    Vec3d v = this.getVelocity().normalize().lerp(to.normalize(), this.homing).normalize().multiply(SPEED);
                    this.setVelocity(v);
                    this.velocityModified = true;
                }
            } else {
                this.homingTarget = null;
            }
        }

        Vec3d v = this.getVelocity();
        HitResult hit = ProjectileUtil.getCollision(this, this::canHit);
        if (hit.getType() != HitResult.Type.MISS && !world.isClient) {
            this.onCollision(hit);
            if (this.isRemoved()) {
                return;
            }
        }

        this.setPosition(this.getX() + v.x, this.getY() + v.y, this.getZ() + v.z);
        this.prevYaw = this.getYaw();
        this.prevPitch = this.getPitch();
        this.updateFacing(v);

        if (world.isClient) {
            // след: снежинки и голубая пыль позади стрелы
            for (int i = 0; i < 2; i++) {
                double k = this.random.nextDouble();
                world.addParticle(i == 0 ? ParticleTypes.SNOWFLAKE : BLUE,
                        this.getX() - v.x * k, this.getY() - v.y * k, this.getZ() - v.z * k, 0, 0, 0);
            }
            if (this.age % 2 == 0) {
                world.addParticle(ParticleTypes.END_ROD, this.getX(), this.getY(), this.getZ(), 0, 0, 0);
            }
        } else if (++this.life > MAX_LIFE) {
            this.discard();
        }
    }

    @Override
    protected boolean canHit(Entity entity) {
        return super.canHit(entity) && !(entity instanceof Monster);
    }

    @Override
    protected void onEntityHit(EntityHitResult result) {
        super.onEntityHit(result);
        Entity target = result.getEntity();
        Entity owner = this.getOwner();
        LivingEntity attacker = owner instanceof LivingEntity l ? l : null;
        if (target.damage(this.getDamageSources().mobProjectile(this, attacker), DAMAGE)
                && target instanceof LivingEntity living) {
            living.addStatusEffect(new StatusEffectInstance(ModEffects.FREEZE, FREEZE_TICKS, 0), owner);
            Vec3d push = this.getVelocity().multiply(1, 0, 1).normalize().multiply(0.3);
            living.addVelocity(push.x, 0.05, push.z);
        }
        this.burst();
        this.discard();
    }

    @Override
    protected void onBlockHit(BlockHitResult result) {
        super.onBlockHit(result);
        this.burst();
        this.discard();
    }

    private void burst() {
        World world = this.getWorld();
        world.playSound(null, this.getX(), this.getY(), this.getZ(), SoundEvents.BLOCK_GLASS_BREAK,
                SoundCategory.HOSTILE, 0.7f, 1.6f + this.random.nextFloat() * 0.2f);
        world.playSound(null, this.getX(), this.getY(), this.getZ(), SoundEvents.ENTITY_PLAYER_HURT_FREEZE,
                SoundCategory.HOSTILE, 0.8f, 1.2f);
        if (world instanceof ServerWorld sw) {
            sw.spawnParticles(ParticleTypes.SNOWFLAKE, this.getX(), this.getY(), this.getZ(), 18, 0.25, 0.25, 0.25, 0.08);
            sw.spawnParticles(BLUE, this.getX(), this.getY(), this.getZ(), 12, 0.3, 0.3, 0.3, 0.0);
            sw.spawnParticles(ParticleTypes.ITEM_SNOWBALL, this.getX(), this.getY(), this.getZ(), 8, 0.2, 0.2, 0.2, 0.05);
        }
    }

    @Override
    public boolean shouldRender(double distance) {
        return distance < 4096.0;
    }
}

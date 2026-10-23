package com.lesha_top1972.basedefense.entity;

import com.lesha_top1972.basedefense.block.ModBlocks;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.attribute.DefaultAttributeContainer;
import net.minecraft.entity.attribute.EntityAttributes;
import net.minecraft.entity.damage.DamageSource;
import net.minecraft.entity.mob.MobEntity;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvent;
import net.minecraft.sound.SoundEvents;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.world.World;

import java.util.ArrayList;
import java.util.List;

/**
 * «Яйцо кровожада»: 100 HP, ничего не делает, не двигается. Вокруг себя (радиус до 3 блоков)
 * постепенно растит «кровавую лужу» (до 14 блоков). Когда яйцо убито — лужа исчезает.
 */
public class BloodEggEntity extends MobEntity implements BloodKin {

    public static final int MAX_POOLS = 14;
    private final List<BlockPos> pools = new ArrayList<>();

    public BloodEggEntity(EntityType<? extends MobEntity> type, World world) {
        super(type, world);
        this.setPersistent();
    }

    public static DefaultAttributeContainer.Builder createAttributes() {
        return MobEntity.createMobAttributes()
                .add(EntityAttributes.GENERIC_MAX_HEALTH, 100.0)
                .add(EntityAttributes.GENERIC_MOVEMENT_SPEED, 0.0)
                .add(EntityAttributes.GENERIC_KNOCKBACK_RESISTANCE, 1.0);
    }

    @Override
    public void tick() {
        super.tick();
        World w = this.getWorld();
        if (w.isClient) {
            return;
        }
        if (this.age % 8 == 0 && this.pools.size() < MAX_POOLS) {
            this.growPool();
        }
        if (this.age % 24 == 0) {
            w.playSound(null, this.getX(), this.getY(), this.getZ(), SoundEvents.ENTITY_WARDEN_HEARTBEAT,
                    SoundCategory.HOSTILE, 0.6f, 0.8f);
        }
        if (this.age % 30 == 0) {
            BloodFx.splash(w, this.getX(), this.getY() + 1.0, this.getZ(), 3, 0.4);
        }
    }

    /** Одна клетка лужи на земле в радиусе 1–3 блока (только в воздухе над целым верхом блока). */
    private void growPool() {
        World w = this.getWorld();
        for (int attempt = 0; attempt < 4; attempt++) {
            int dx = this.random.nextInt(7) - 3;
            int dz = this.random.nextInt(7) - 3;
            if (dx * dx + dz * dz > 10) {
                continue;
            }
            BlockPos base = this.getBlockPos().add(dx, 0, dz);
            for (int dy = 1; dy >= -1; dy--) {
                BlockPos p = base.up(dy);
                if (w.isAir(p) && w.getBlockState(p.down()).isSideSolidFullSquare(w, p.down(), Direction.UP)) {
                    w.setBlockState(p, ModBlocks.BLOOD_POOL.getDefaultState());
                    this.pools.add(p.toImmutable());
                    return;
                }
            }
        }
    }

    private void clearPools() {
        World w = this.getWorld();
        for (BlockPos p : this.pools) {
            if (w.getBlockState(p).isOf(ModBlocks.BLOOD_POOL)) {
                w.removeBlock(p, false);
            }
        }
        this.pools.clear();
    }

    @Override
    public void remove(RemovalReason reason) {
        if (!this.getWorld().isClient && reason.shouldDestroy()) {
            this.clearPools();
        }
        super.remove(reason);
    }

    @Override
    public void onDeath(DamageSource source) {
        super.onDeath(source);
        BloodFx.splash(this.getWorld(), this.getX(), this.getY() + 0.7, this.getZ(), 40, 0.6);
    }

    @Override
    public boolean damage(DamageSource source, float amount) {
        if (source.getAttacker() instanceof BloodKin) {
            return false;
        }
        boolean hurt = super.damage(source, amount);
        if (hurt) {
            BloodFx.splash(this.getWorld(), this.getX(), this.getY() + 0.8, this.getZ(), 5, 0.3);
        }
        return hurt;
    }

    // неподвижное: не толкается и не отлетает
    @Override
    public boolean isPushable() {
        return false;
    }

    @Override
    protected void pushAway(Entity entity) {
    }

    @Override
    public void takeKnockback(double strength, double x, double z) {
    }

    @Override
    public boolean handleFallDamage(float fallDistance, float damageMultiplier, DamageSource damageSource) {
        return false;
    }

    @Override
    public void writeCustomDataToNbt(NbtCompound nbt) {
        super.writeCustomDataToNbt(nbt);
        long[] arr = new long[this.pools.size()];
        for (int i = 0; i < arr.length; i++) {
            arr[i] = this.pools.get(i).asLong();
        }
        nbt.putLongArray("Pools", arr);
    }

    @Override
    public void readCustomDataFromNbt(NbtCompound nbt) {
        super.readCustomDataFromNbt(nbt);
        this.pools.clear();
        for (long l : nbt.getLongArray("Pools")) {
            this.pools.add(BlockPos.fromLong(l));
        }
    }

    @Override
    protected SoundEvent getHurtSound(DamageSource source) {
        return SoundEvents.ENTITY_SLIME_HURT;
    }

    @Override
    protected SoundEvent getDeathSound() {
        return SoundEvents.ENTITY_SLIME_DEATH;
    }
}

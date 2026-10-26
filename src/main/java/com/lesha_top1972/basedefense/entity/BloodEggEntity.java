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
 * Если яйцо прожило 80 с ({@link #HATCH_TICKS}) — лопается, и из него вылезают 2 мелких кровожада.
 */
public class BloodEggEntity extends MobEntity implements BloodKin {

    public static final int MAX_POOLS = 14;
    public static final int HATCH_TICKS = 1600;
    private final List<BlockPos> pools = new ArrayList<>();
    /** Сколько тиков живёт яйцо (сохраняется в NBT). */
    private int life;

    /** Ускорить вылупление (Набат: «Звон» −10 с, яйца «Кладки» вылупляются за 40 с). */
    public void addLife(int ticks) {
        this.life = Math.min(HATCH_TICKS - 1, this.life + ticks);
    }

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
        this.life++;
        boolean soon = this.life > HATCH_TICKS - 100;              // последние 5 с — бьётся чаще
        if (this.age % (soon ? 6 : 30) == 0) {
            BloodFx.splash(w, this.getX(), this.getY() + 1.0, this.getZ(), soon ? 6 : 3, 0.4);
        }
        if (soon && this.age % 12 == 0) {
            w.playSound(null, this.getX(), this.getY(), this.getZ(), SoundEvents.ENTITY_WARDEN_HEARTBEAT,
                    SoundCategory.HOSTILE, 1.0f, 1.2f);
        }
        if (this.life >= HATCH_TICKS) {
            this.hatch();
        }
    }

    /** Яйцо лопается: 2 мелких кровожада, брызги, лужа исчезает. */
    private void hatch() {
        if (!(this.getWorld() instanceof net.minecraft.server.world.ServerWorld s)) {
            return;
        }
        for (int k = 0; k < 2; k++) {
            BloodlingEntity b = ModEntities.BLOODLING.create(s);
            if (b == null) {
                continue;
            }
            double a = this.random.nextDouble() * Math.PI * 2;
            b.refreshPositionAndAngles(this.getX() + Math.cos(a) * 0.6, this.getY(), this.getZ() + Math.sin(a) * 0.6,
                    this.random.nextFloat() * 360f, 0f);
            b.initialize(s, s.getLocalDifficulty(this.getBlockPos()), net.minecraft.entity.SpawnReason.MOB_SUMMONED,
                    null, null);
            s.spawnEntity(b);
        }
        BloodFx.splash(s, this.getX(), this.getY() + 0.7, this.getZ(), 50, 0.7);
        s.playSound(null, this.getX(), this.getY(), this.getZ(), SoundEvents.ENTITY_SLIME_DEATH,
                SoundCategory.HOSTILE, 1.4f, 0.5f);
        this.discard();
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
        nbt.putInt("Life", this.life);
    }

    @Override
    public void readCustomDataFromNbt(NbtCompound nbt) {
        super.readCustomDataFromNbt(nbt);
        this.life = nbt.getInt("Life");
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

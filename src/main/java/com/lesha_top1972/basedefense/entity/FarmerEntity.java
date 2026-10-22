package com.lesha_top1972.basedefense.entity;

import net.minecraft.entity.EntityType;
import net.minecraft.entity.ai.goal.Goal;
import net.minecraft.entity.ai.goal.GoToWalkTargetGoal;
import net.minecraft.entity.ai.goal.LookAroundGoal;
import net.minecraft.entity.ai.goal.LookAtEntityGoal;
import net.minecraft.entity.ai.goal.SwimGoal;
import net.minecraft.entity.ai.goal.WanderAroundFarGoal;
import net.minecraft.entity.attribute.DefaultAttributeContainer;
import net.minecraft.entity.attribute.EntityAttributes;
import net.minecraft.entity.damage.DamageSource;
import net.minecraft.entity.mob.MobEntity;
import net.minecraft.entity.mob.PathAwareEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtHelper;
import net.minecraft.sound.SoundEvent;
import net.minecraft.sound.SoundEvents;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;
import org.jetbrains.annotations.Nullable;

import java.util.EnumSet;

/**
 * Житель поселения — фермер. Дружелюбный, гуляет возле своего дома
 * и не уходит от него дальше чем на {@link #HOME_RADIUS} блоков.
 */
public class FarmerEntity extends PathAwareEntity {

    /** Максимальное расстояние от дома. */
    public static final int HOME_RADIUS = 50;

    @Nullable
    private BlockPos homePos;

    public FarmerEntity(EntityType<? extends PathAwareEntity> type, World world) {
        super(type, world);
    }

    public static DefaultAttributeContainer.Builder createFarmerAttributes() {
        return MobEntity.createMobAttributes()
                .add(EntityAttributes.GENERIC_MAX_HEALTH, 20.0)
                .add(EntityAttributes.GENERIC_MOVEMENT_SPEED, 0.3)
                .add(EntityAttributes.GENERIC_FOLLOW_RANGE, 64.0);
    }

    @Override
    protected void initGoals() {
        this.goalSelector.add(0, new SwimGoal(this));
        this.goalSelector.add(1, new ReturnHomeGoal(this));
        this.goalSelector.add(2, new GoToWalkTargetGoal(this, 0.8));
        this.goalSelector.add(3, new WanderAroundFarGoal(this, 0.6, 0.001f));
        this.goalSelector.add(4, new LookAtEntityGoal(this, PlayerEntity.class, 8.0f));
        this.goalSelector.add(5, new LookAroundGoal(this));
    }

    /** Назначить дом. Гуляет фермер только в пределах радиуса от него. */
    public void setHome(BlockPos pos) {
        this.homePos = pos.toImmutable();
        // Бродит чуть ближе границы, чтобы не упираться в неё
        this.setPositionTarget(this.homePos, HOME_RADIUS - 8);
    }

    @Nullable
    public BlockPos getHome() {
        return homePos;
    }

    @Override
    public void tick() {
        super.tick();
        // Если фермер появился из яйца — его дом там, где он появился
        if (!this.getWorld().isClient && this.homePos == null) {
            setHome(this.getBlockPos());
        }
    }

    @Override
    public boolean canImmediatelyDespawn(double distanceSquared) {
        return false;
    }

    @Override
    public void writeCustomDataToNbt(NbtCompound nbt) {
        super.writeCustomDataToNbt(nbt);
        if (homePos != null) {
            nbt.put("Home", NbtHelper.fromBlockPos(homePos));
        }
    }

    @Override
    public void readCustomDataFromNbt(NbtCompound nbt) {
        super.readCustomDataFromNbt(nbt);
        if (nbt.contains("Home")) {
            setHome(NbtHelper.toBlockPos(nbt.getCompound("Home")));
        }
    }

    // ---------- Звуки (пока как у обычного жителя) ----------

    @Override
    protected SoundEvent getAmbientSound() {
        return SoundEvents.ENTITY_VILLAGER_AMBIENT;
    }

    @Override
    protected SoundEvent getHurtSound(DamageSource source) {
        return SoundEvents.ENTITY_VILLAGER_HURT;
    }

    @Override
    protected SoundEvent getDeathSound() {
        return SoundEvents.ENTITY_VILLAGER_DEATH;
    }

    /** Возвращает фермера домой, если он ушёл дальше {@link #HOME_RADIUS} блоков. */
    static class ReturnHomeGoal extends Goal {

        private final FarmerEntity farmer;

        ReturnHomeGoal(FarmerEntity farmer) {
            this.farmer = farmer;
            this.setControls(EnumSet.of(Control.MOVE));
        }

        private double distanceSqToHome() {
            BlockPos home = farmer.getHome();
            return home == null ? 0 : farmer.squaredDistanceTo(Vec3d.ofBottomCenter(home));
        }

        @Override
        public boolean canStart() {
            return farmer.getHome() != null && distanceSqToHome() > HOME_RADIUS * HOME_RADIUS;
        }

        @Override
        public boolean shouldContinue() {
            return farmer.getHome() != null && distanceSqToHome() > 16 * 16 && !farmer.getNavigation().isIdle();
        }

        @Override
        public void start() {
            BlockPos home = farmer.getHome();
            if (home == null) {
                return;
            }
            boolean pathFound = farmer.getNavigation().startMovingTo(home.getX() + 0.5, home.getY(), home.getZ() + 0.5, 1.0);
            // Совсем далеко или дороги нет — просто переносим домой
            if (!pathFound || distanceSqToHome() > (HOME_RADIUS + 30) * (HOME_RADIUS + 30)) {
                farmer.getNavigation().stop();
                farmer.requestTeleport(home.getX() + 0.5, home.getY(), home.getZ() + 0.5);
            }
        }

        @Override
        public void stop() {
            farmer.getNavigation().stop();
        }
    }
}

package com.lesha_top1972.basedefense.entity;

import com.lesha_top1972.basedefense.settlement.SettlementManager;
import com.lesha_top1972.basedefense.settlement.SettlementState;
import net.minecraft.entity.Entity;
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
import net.minecraft.particle.ParticleTypes;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundEvent;
import net.minecraft.sound.SoundEvents;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;
import org.jetbrains.annotations.Nullable;

import java.util.EnumSet;

/**
 * Общий класс для жителей поселения (фермер, скотовод и т.д.).
 * Дружелюбный, гуляет возле своего дома и не уходит от него
 * дальше чем на {@link #HOME_RADIUS} блоков.
 * Каждый житель привязан к Базе: он входит в поселение и потребляет его запасы.
 * Если запасы поселения кончились (счётчик дошёл до -20) — житель замирает.
 */
public abstract class SettlerEntity extends PathAwareEntity {

    /** Максимальное расстояние от дома. */
    public static final int HOME_RADIUS = 50;

    @Nullable
    private BlockPos homePos;

    protected SettlerEntity(EntityType<? extends PathAwareEntity> type, World world) {
        super(type, world);
    }

    public static DefaultAttributeContainer.Builder createSettlerAttributes() {
        return MobEntity.createMobAttributes()
                .add(EntityAttributes.GENERIC_MAX_HEALTH, 20.0)
                .add(EntityAttributes.GENERIC_MOVEMENT_SPEED, 0.3)
                .add(EntityAttributes.GENERIC_FOLLOW_RANGE, 64.0);
    }

    @Override
    protected void initGoals() {
        this.goalSelector.add(0, new FreezeGoal(this));   // нет запасов — стоит на месте
        this.goalSelector.add(1, new SwimGoal(this));
        this.goalSelector.add(2, new ReturnHomeGoal(this));
        this.goalSelector.add(3, new GoToWalkTargetGoal(this, 0.8));
        this.goalSelector.add(4, new WanderAroundFarGoal(this, 0.6, 0.001f));
        this.goalSelector.add(5, new LookAtEntityGoal(this, PlayerEntity.class, 8.0f));
        this.goalSelector.add(6, new LookAroundGoal(this));
    }

    /** Назначить дом. Гуляет житель только в пределах радиуса от него. */
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
        // Если житель появился из яйца — его дом там, где он появился
        if (!this.getWorld().isClient && this.homePos == null) {
            setHome(this.getBlockPos());
        }
        if (this.getWorld() instanceof ServerWorld serverWorld) {
            // Привязка к Базе: житель записывается в поселение (раз в секунду — на всякий случай)
            if (this.age % 20 == 1 && this.isAlive()) {
                SettlementState.get(serverWorld.getServer()).addSettler(this.getUuid());
            }
            // Замерший житель — сердитые облачка над головой, чтобы было видно
            if (this.age % 60 == 0 && SettlementManager.isFrozen(serverWorld.getServer())) {
                serverWorld.spawnParticles(ParticleTypes.ANGRY_VILLAGER, this.getX(), this.getY() + 2.2, this.getZ(),
                        1, 0.2, 0.1, 0.2, 0.0);
            }
        }
    }

    /** Житель погиб или удалён — убираем его из поселения. */
    @Override
    public void remove(Entity.RemovalReason reason) {
        if (this.getWorld() instanceof ServerWorld serverWorld && reason.shouldDestroy()) {
            SettlementState.get(serverWorld.getServer()).removeSettler(this.getUuid());
        }
        super.remove(reason);
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

    /** Пока у поселения нет запасов (счётчик дошёл до -20) — житель стоит и ничего не делает. */
    static class FreezeGoal extends Goal {

        private final SettlerEntity settler;

        FreezeGoal(SettlerEntity settler) {
            this.settler = settler;
            // Плавать (прыгать) не мешаем, чтобы житель не утонул
            this.setControls(EnumSet.of(Control.MOVE, Control.LOOK));
        }

        private boolean frozen() {
            return settler.getWorld() instanceof ServerWorld serverWorld
                    && SettlementManager.isFrozen(serverWorld.getServer());
        }

        @Override
        public boolean canStart() {
            return frozen();
        }

        @Override
        public boolean shouldContinue() {
            return frozen();
        }

        @Override
        public void start() {
            settler.getNavigation().stop();
        }

        @Override
        public void tick() {
            settler.getNavigation().stop();
        }
    }

    /** Возвращает жителя домой, если он ушёл дальше {@link #HOME_RADIUS} блоков. */
    static class ReturnHomeGoal extends Goal {

        private final SettlerEntity settler;

        ReturnHomeGoal(SettlerEntity settler) {
            this.settler = settler;
            this.setControls(EnumSet.of(Control.MOVE));
        }

        private double distanceSqToHome() {
            BlockPos home = settler.getHome();
            return home == null ? 0 : settler.squaredDistanceTo(Vec3d.ofBottomCenter(home));
        }

        @Override
        public boolean canStart() {
            return settler.getHome() != null && distanceSqToHome() > HOME_RADIUS * HOME_RADIUS;
        }

        @Override
        public boolean shouldContinue() {
            return settler.getHome() != null && distanceSqToHome() > 16 * 16 && !settler.getNavigation().isIdle();
        }

        @Override
        public void start() {
            BlockPos home = settler.getHome();
            if (home == null) {
                return;
            }
            boolean pathFound = settler.getNavigation().startMovingTo(home.getX() + 0.5, home.getY(), home.getZ() + 0.5, 1.0);
            // Совсем далеко или дороги нет — просто переносим домой
            if (!pathFound || distanceSqToHome() > (HOME_RADIUS + 30) * (HOME_RADIUS + 30)) {
                settler.getNavigation().stop();
                settler.requestTeleport(home.getX() + 0.5, home.getY(), home.getZ() + 0.5);
            }
        }

        @Override
        public void stop() {
            settler.getNavigation().stop();
        }
    }
}

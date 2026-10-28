package com.lesha_top1972.basedefense.entity.settler;

import com.lesha_top1972.basedefense.entity.ModEntities;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.projectile.ArrowEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;
import org.jetbrains.annotations.Nullable;

/**
 * Стрела стражника: обычная (или с зельем) стрела, которая пролетает сквозь жителей и игроков.
 * Меткая стрела (половина выстрелов) понемногу доворачивает к цели — так шанс попадания ровно
 * такой, как задан, а не зависит от движения моба.
 */
public class GuardArrowEntity extends ArrowEntity {

    @Nullable
    private LivingEntity homing;

    public GuardArrowEntity(EntityType<? extends ArrowEntity> type, World world) {
        super(type, world);
    }

    public GuardArrowEntity(World world, LivingEntity owner, ItemStack arrowStack) {
        this(ModEntities.GUARD_ARROW, world);
        this.setOwner(owner);
        this.setPosition(owner.getX(), owner.getEyeY() - 0.1, owner.getZ());
        this.initFromStack(arrowStack);
    }

    public void setHoming(LivingEntity target) {
        this.homing = target;
    }

    @Override
    public void tick() {
        super.tick();
        if (!this.getWorld().isClient && homing != null && !this.inGround && this.age < 60) {
            if (!homing.isAlive()) {
                homing = null;
                return;
            }
            Vec3d v = this.getVelocity();
            double speed = v.length();
            Vec3d to = homing.getPos().add(0, homing.getHeight() * 0.55, 0).subtract(this.getPos());
            if (speed > 0.1 && to.lengthSquared() > 0.04) {
                Vec3d dir = v.normalize().lerp(to.normalize(), 0.3).normalize();
                this.setVelocity(dir.multiply(speed));
                this.velocityDirty = true;
            }
        }
    }

    @Override
    protected boolean canHit(Entity entity) {
        return super.canHit(entity) && !(entity instanceof SettlerEntity) && !(entity instanceof PlayerEntity);
    }
}

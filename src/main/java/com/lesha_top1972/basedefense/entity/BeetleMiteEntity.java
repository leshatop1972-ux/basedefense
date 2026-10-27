package com.lesha_top1972.basedefense.entity;

import com.lesha_top1972.basedefense.sound.ModSounds;
import net.minecraft.block.BlockState;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.ai.goal.ActiveTargetGoal;
import net.minecraft.entity.ai.goal.Goal;
import net.minecraft.entity.ai.goal.LookAroundGoal;
import net.minecraft.entity.ai.goal.LookAtEntityGoal;
import net.minecraft.entity.ai.goal.SwimGoal;
import net.minecraft.entity.ai.goal.WanderAroundFarGoal;
import net.minecraft.entity.attribute.DefaultAttributeContainer;
import net.minecraft.entity.attribute.EntityAttributes;
import net.minecraft.entity.damage.DamageSource;
import net.minecraft.entity.mob.HostileEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.sound.SoundEvent;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.MathHelper;
import net.minecraft.world.World;

import java.util.EnumSet;

/** Маленький жук: 30 HP, быстрый (0.32), агрится на игроков (48 бл), укус 7 урона раз в 2 с (бросок вперёд). */
public class BeetleMiteEntity extends HostileEntity {

    public static final byte STATUS_BITE = 70;
    static final int BITE_TICKS = 14;
    static final int BITE_HIT = 6;
    static final int BITE_CD = 40;
    static final float BITE_DMG = 7f;

    public int clientBiteAge = -1000;

    public BeetleMiteEntity(EntityType<? extends HostileEntity> type, World world) {
        super(type, world);
        this.experiencePoints = 4;
    }

    public static DefaultAttributeContainer.Builder createAttributes() {
        return HostileEntity.createHostileAttributes()
                .add(EntityAttributes.GENERIC_MAX_HEALTH, 30.0)
                .add(EntityAttributes.GENERIC_MOVEMENT_SPEED, 0.32)
                .add(EntityAttributes.GENERIC_FOLLOW_RANGE, 48.0)
                .add(EntityAttributes.GENERIC_ATTACK_DAMAGE, 7.0);
    }

    @Override
    protected void initGoals() {
        this.goalSelector.add(0, new SwimGoal(this));
        this.goalSelector.add(2, new MiteGoal(this));
        this.goalSelector.add(7, new WanderAroundFarGoal(this, 0.8));
        this.goalSelector.add(8, new LookAtEntityGoal(this, PlayerEntity.class, 12.0f));
        this.goalSelector.add(8, new LookAroundGoal(this));
        this.targetSelector.add(1, new ActiveTargetGoal<>(this, PlayerEntity.class, false));
    }

    @Override
    public void handleStatus(byte status) {
        if (status == STATUS_BITE) {
            this.clientBiteAge = this.age;
        } else {
            super.handleStatus(status);
        }
    }

    @Override
    protected SoundEvent getAmbientSound() {
        return ModSounds.MITE_AMBIENT;
    }

    @Override
    public int getMinAmbientSoundDelay() {
        return 200;
    }

    @Override
    protected SoundEvent getHurtSound(DamageSource source) {
        return ModSounds.MITE_AMBIENT;
    }

    @Override
    protected SoundEvent getDeathSound() {
        return ModSounds.MITE_DEATH;
    }

    @Override
    protected void playStepSound(BlockPos pos, BlockState state) {
        this.playSound(ModSounds.MITE_STEP, 0.3f, 1.0f + this.random.nextFloat() * 0.3f);
    }

    static class MiteGoal extends Goal {
        private final BeetleMiteEntity mob;
        private int bite = -1;
        private int cd;
        private int pathDelay;

        MiteGoal(BeetleMiteEntity mob) {
            this.mob = mob;
            this.setControls(EnumSet.of(Control.MOVE, Control.LOOK));
        }

        static boolean valid(LivingEntity t) {
            return t != null && t.isAlive() && !(t instanceof PlayerEntity p && (p.isCreative() || p.isSpectator()));
        }

        @Override
        public boolean canStart() {
            return valid(this.mob.getTarget());
        }

        @Override
        public boolean shouldContinue() {
            return this.bite >= 0 || this.canStart();
        }

        @Override
        public boolean shouldRunEveryTick() {
            return true;
        }

        @Override
        public void tick() {
            BeetleMiteEntity m = this.mob;
            LivingEntity t = m.getTarget();
            if (this.cd > 0) {
                this.cd--;
            }
            if (this.bite >= 0) {
                this.bite++;
                if (valid(t) && this.bite < BITE_HIT) {
                    m.getLookControl().lookAt(t, 30f, 30f);
                }
                if (this.bite == BITE_HIT - 2 && valid(t)) {
                    // бросок вперёд
                    float yaw = m.getYaw() * MathHelper.RADIANS_PER_DEGREE;
                    m.addVelocity(-MathHelper.sin(yaw) * 0.35, 0.12, MathHelper.cos(yaw) * 0.35);
                    m.velocityModified = true;
                }
                if (this.bite == BITE_HIT && valid(t) && m.squaredDistanceTo(t) < 2.6 * 2.6) {
                    t.damage(m.getDamageSources().mobAttack(m), BITE_DMG);
                    m.playSound(ModSounds.MITE_BITE, 0.8f, 1.0f);
                }
                if (this.bite >= BITE_TICKS) {
                    this.bite = -1;
                    this.cd = BITE_CD;
                }
                return;
            }
            if (!valid(t)) {
                return;
            }
            m.getLookControl().lookAt(t, 30f, 30f);
            double d2 = m.squaredDistanceTo(t);
            if (d2 < 2.0 * 2.0 && this.cd <= 0) {
                this.bite = 0;
                m.getNavigation().stop();
                m.getWorld().sendEntityStatus(m, STATUS_BITE);
                return;
            }
            if (--this.pathDelay <= 0) {
                this.pathDelay = 6;
                m.getNavigation().startMovingTo(t, 1.2);
            }
        }
    }
}

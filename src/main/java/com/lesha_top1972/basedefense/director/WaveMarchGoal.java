package com.lesha_top1972.basedefense.director;

import net.minecraft.entity.ai.goal.Goal;
import net.minecraft.entity.mob.MobEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.Heightmap;

import java.util.EnumSet;

/**
 * Моб волны идёт к Базе (без Базы — к ближайшему игроку), пока у него нет цели. Путь строится отрезками
 * по ~24 блока (дальний путь поиск не строит), у Базы бродит вокруг неё. Цель появилась — уступает своим атакам.
 */
public class WaveMarchGoal extends Goal {

    private static final double STEP = 24.0;
    private static final double NEAR = 10.0;

    private final MobEntity mob;
    private final double speed;
    private int ticks;
    private int cooldown;

    public WaveMarchGoal(MobEntity mob, double speed) {
        this.mob = mob;
        this.speed = speed;
        this.setControls(EnumSet.of(Control.MOVE));
    }

    @Override
    public boolean canStart() {
        if (mob.getTarget() != null || !(mob.getWorld() instanceof ServerWorld)) {
            return false;
        }
        if (cooldown > 0) {
            cooldown--;
            return false;
        }
        return true;
    }

    @Override
    public void start() {
        ticks = 0;
        Vec3d goal = goal();
        if (goal == null) {
            cooldown = 40;
            return;
        }
        Vec3d here = mob.getPos();
        Vec3d to = new Vec3d(goal.x - here.x, 0, goal.z - here.z);
        double dist = to.length();
        Vec3d point;
        if (dist <= NEAR) {
            // у Базы — бродит вокруг неё
            double a = mob.getRandom().nextDouble() * Math.PI * 2;
            double r = 4 + mob.getRandom().nextDouble() * 8;
            point = new Vec3d(goal.x + Math.cos(a) * r, goal.y, goal.z + Math.sin(a) * r);
        } else if (dist <= STEP + 4) {
            point = goal;
        } else {
            Vec3d d = to.multiply(1.0 / dist);
            double side = (mob.getRandom().nextDouble() - 0.5) * 6;
            point = here.add(d.multiply(STEP)).add(-d.z * side, 0, d.x * side);
        }
        int x = MathHelper.floor(point.x);
        int z = MathHelper.floor(point.z);
        int y = mob.getWorld().getTopY(Heightmap.Type.MOTION_BLOCKING_NO_LEAVES, x, z);
        if (!mob.getNavigation().startMovingTo(x + 0.5, y, z + 0.5, speed)) {
            cooldown = 20 + mob.getRandom().nextInt(20);
        }
    }

    @Override
    public boolean shouldContinue() {
        return mob.getTarget() == null && !mob.getNavigation().isIdle() && ++ticks < 240;
    }

    @Override
    public void stop() {
        cooldown = 10 + mob.getRandom().nextInt(20);
    }

    /** Куда идти: База в том же мире, иначе ближайший игрок. */
    private Vec3d goal() {
        ServerWorld w = (ServerWorld) mob.getWorld();
        BlockPos base = WaveManager.basePos(w);
        if (base != null) {
            return Vec3d.ofBottomCenter(base);
        }
        PlayerEntity p = w.getClosestPlayer(mob, 256);
        return p == null ? null : p.getPos();
    }
}

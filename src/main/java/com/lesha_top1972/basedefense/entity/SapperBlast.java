package com.lesha_top1972.basedefense.entity;

import com.lesha_top1972.basedefense.fx.Fx;
import com.lesha_top1972.basedefense.sound.ModSounds;
import net.minecraft.block.BlockState;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.decoration.ArmorStandEntity;
import net.minecraft.entity.mob.Monster;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.GameRules;
import net.minecraft.world.World;

/**
 * Свои взрывы подкопщика — без ванильного взрыва: урон с затуханием к краю, отброс, ломание блоков
 * по правилу mobGriefing (без дропа) и эффект {@link Fx#BLAST} одним пакетом.
 */
public final class SapperBlast {

    private SapperBlast() {
    }

    public static boolean griefing(World w) {
        return w.getGameRules().getBoolean(GameRules.DO_MOB_GRIEFING);
    }

    /** Можно ли подкопщику сломать блок: не воздух, не жидкость, не бедрок/обсидиан, твёрдость ≤ maxHardness, без блок‑сущности. */
    public static boolean breakable(World w, BlockPos p, float maxHardness) {
        BlockState st = w.getBlockState(p);
        if (st.isAir() || !st.getFluidState().isEmpty() || st.hasBlockEntity()) {
            return false;
        }
        float h = st.getHardness(w, p);
        return h >= 0 && h <= maxHardness;
    }

    /** Урон всем, кроме монстров, в радиусе r: dmg в центре, вдвое меньше на краю; отброс. */
    static void hurt(ServerWorld w, Vec3d c, double r, float dmg, double knock, Entity source) {
        LivingEntity owner = source instanceof LivingEntity le ? le : null;
        for (LivingEntity e : w.getEntitiesByClass(LivingEntity.class, new Box(c, c).expand(r + 1.0),
                e -> e.isAlive() && !(e instanceof Monster) && !(e instanceof ArmorStandEntity)
                        && !(e instanceof PlayerEntity p && (p.isCreative() || p.isSpectator())))) {
            Vec3d mid = e.getPos().add(0, e.getHeight() * 0.5, 0);
            double d = Math.max(0, mid.distanceTo(c) - e.getWidth() * 0.5);
            if (d > r) {
                continue;
            }
            float k = (float) (1.0 - 0.5 * d / r);
            e.timeUntilRegen = 0;
            boolean ok = owner != null
                    ? e.damage(w.getDamageSources().mobAttack(owner), dmg * k)
                    : e.damage(w.getDamageSources().explosion(null, null), dmg * k);
            if (ok && knock > 0) {
                e.takeKnockback(knock * k, c.x - e.getX(), c.z - e.getZ());
                e.addVelocity(0, 0.25 * k, 0);
                e.velocityModified = true;
            }
        }
    }

    /** Бомба: r 2.5, ломает слабые блоки (твёрдость ≤ 1.5) в шаре 1.5. */
    static void bomb(ServerWorld w, Vec3d c, float dmg, Entity owner) {
        hurt(w, c, 2.5, dmg, 0.6, owner);
        int block = Fx.groundId(w, c);
        if (griefing(w)) {
            BlockPos o = BlockPos.ofFloored(c);
            for (BlockPos p : BlockPos.iterate(o.add(-2, -2, -2), o.add(2, 2, 2))) {
                if (p.getSquaredDistance(c) <= 1.5 * 1.5 + 0.3 && breakable(w, p, 1.5f)) {
                    w.breakBlock(p.toImmutable(), false, owner);
                }
            }
        }
        Fx.blast(w, c, 2.5, false, block);
        w.playSound(null, c.x, c.y, c.z, ModSounds.SAPPER_BOMB, SoundCategory.HOSTILE, 1.4f,
                0.9f + w.random.nextFloat() * 0.2f);
    }

    /** Подрыв: стена 3×3 (и блок за ней по центру), урон 10 в r3. face — сторона стены, на которой висит шашка. */
    static void charge(ServerWorld w, BlockPos wall, Direction face, Vec3d c, float dmg, Entity owner) {
        int block = Fx.blockId(w.getBlockState(wall));
        hurt(w, c, 3.0, dmg, 1.1, owner);
        if (griefing(w)) {
            Direction a = face.getAxis() == Direction.Axis.X ? Direction.SOUTH : Direction.EAST;
            for (int u = -1; u <= 1; u++) {
                for (int v = -1; v <= 1; v++) {
                    for (int depth = 0; depth <= (u == 0 && v == 0 ? 1 : 0); depth++) {
                        BlockPos p = wall.offset(a, u).up(v).offset(face.getOpposite(), depth);
                        if (breakable(w, p, 5f)) {
                            w.breakBlock(p, false, owner);
                        }
                    }
                }
            }
        }
        Fx.blast(w, c, 3.0, true, block);
        w.playSound(null, c.x, c.y, c.z, ModSounds.SAPPER_BLAST, SoundCategory.HOSTILE, 2.0f, 0.95f);
    }
}

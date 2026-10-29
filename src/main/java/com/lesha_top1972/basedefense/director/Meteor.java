package com.lesha_top1972.basedefense.director;

import com.lesha_top1972.basedefense.fx.Fx;
import net.minecraft.block.AbstractFireBlock;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import net.minecraft.util.math.random.Random;
import net.minecraft.world.Heightmap;

import java.util.ArrayList;
import java.util.List;

/**
 * Падение метеорита (днём): в ~150 блоках от Базы (без Базы — от игрока) — воронка и каменный шар наполовину
 * в земле: магма, чернокамень, базальт, немного обсидиана; внутри руды — алмазы 1–4, железо 5–25, золото 5–15.
 * Каждый четвёртый — большой (радиус 4.5 вместо 3, руды ×2). Всем игрокам — сообщение с координатами.
 */
public final class Meteor {

    private static final double DIST = 150;

    private Meteor() {
    }

    static void strike(ServerWorld w) {
        Random r = w.getRandom();
        BlockPos from = WaveManager.basePos(w);
        if (from == null) {
            List<ServerPlayerEntity> players = w.getPlayers(p -> !p.isSpectator());
            if (players.isEmpty()) {
                return;
            }
            from = players.get(r.nextInt(players.size())).getBlockPos();
        }
        BlockPos center = null;
        for (int attempt = 0; attempt < 8 && center == null; attempt++) {
            double a = r.nextDouble() * Math.PI * 2;
            int x = MathHelper.floor(from.getX() + Math.cos(a) * DIST);
            int z = MathHelper.floor(from.getZ() + Math.sin(a) * DIST);
            w.getChunk(x >> 4, z >> 4);                  // загрузить/создать чанк
            int y = w.getTopY(Heightmap.Type.MOTION_BLOCKING_NO_LEAVES, x, z);
            BlockPos p = new BlockPos(x, y - 1, z);
            if (w.getFluidState(p).isEmpty() && w.getFluidState(p.up()).isEmpty() && y > w.getBottomY() + 8) {
                center = p;
            }
        }
        if (center == null) {
            return;
        }
        boolean big = r.nextFloat() < 0.25f;
        double radius = big ? 4.5 : 3.0;
        build(w, center, radius, big, r);

        Vec3d c = Vec3d.ofCenter(center);
        Fx.blast(w, c.add(0, radius * 0.5, 0), radius * 2.2, true, Block.getRawIdFromState(Blocks.MAGMA_BLOCK.getDefaultState()));
        Fx.column(w, c.add(0, radius, 0), 18, 0x3A3532);
        w.playSound(null, center, SoundEvents.ENTITY_GENERIC_EXPLODE, SoundCategory.BLOCKS, 6f, 0.6f);
        Text dir = dirFrom(from, center);
        for (ServerPlayerEntity p : w.getServer().getPlayerManager().getPlayerList()) {
            p.sendMessage(Text.translatable(big ? "director.basedefense.msg.meteor_big" : "director.basedefense.msg.meteor",
                    dir, center.getX(), center.getY(), center.getZ()).formatted(Formatting.GOLD), false);
            p.playSound(SoundEvents.ENTITY_LIGHTNING_BOLT_THUNDER, SoundCategory.WEATHER, 0.6f, 0.5f);
        }
    }

    private static void build(ServerWorld w, BlockPos c, double radius, boolean big, Random r) {
        int reach = (int) Math.ceil(radius * 2.0) + 1;
        BlockPos.Mutable p = new BlockPos.Mutable();
        // воронка: чаша над поверхностью вокруг шара
        double bowlR = radius * 1.9;
        double bowlY = c.getY() + radius * 1.3;
        for (int dx = -reach; dx <= reach; dx++) {
            for (int dz = -reach; dz <= reach; dz++) {
                for (int dy = -1; dy <= reach; dy++) {
                    p.set(c.getX() + dx, c.getY() + dy, c.getZ() + dz);
                    double d2 = dx * dx + dz * dz + (p.getY() - bowlY) * (p.getY() - bowlY);
                    if (d2 <= bowlR * bowlR && breakable(w, p)) {
                        w.setBlockState(p, Blocks.AIR.getDefaultState(), Block.NOTIFY_ALL);
                    }
                }
            }
        }
        // шар
        List<BlockPos> inner = new ArrayList<>();
        int rr = (int) Math.ceil(radius);
        for (int dx = -rr; dx <= rr; dx++) {
            for (int dy = -rr; dy <= rr; dy++) {
                for (int dz = -rr; dz <= rr; dz++) {
                    double d = Math.sqrt(dx * dx + dy * dy + dz * dz);
                    if (d > radius) {
                        continue;
                    }
                    p.set(c.getX() + dx, c.getY() + dy, c.getZ() + dz);
                    if (!breakable(w, p)) {
                        continue;
                    }
                    w.setBlockState(p, rock(r), Block.NOTIFY_ALL);
                    if (d < radius - 0.6) {
                        inner.add(p.toImmutable());
                    }
                }
            }
        }
        // руды внутри
        for (int i = inner.size() - 1; i > 0; i--) {
            int j = r.nextInt(i + 1);
            BlockPos t = inner.get(i);
            inner.set(i, inner.get(j));
            inner.set(j, t);
        }
        int k = big ? 2 : 1;
        int idx = 0;
        idx = place(w, inner, idx, (1 + r.nextInt(4)) * k, Blocks.DEEPSLATE_DIAMOND_ORE.getDefaultState());
        idx = place(w, inner, idx, (5 + r.nextInt(21)) * k, Blocks.DEEPSLATE_IRON_ORE.getDefaultState());
        place(w, inner, idx, (5 + r.nextInt(11)) * k, Blocks.DEEPSLATE_GOLD_ORE.getDefaultState());
        // огонь по краю воронки
        for (int i = 0; i < (big ? 14 : 8); i++) {
            double a = r.nextDouble() * Math.PI * 2;
            double d = bowlR * (0.8 + r.nextDouble() * 0.5);
            int x = MathHelper.floor(c.getX() + Math.cos(a) * d);
            int z = MathHelper.floor(c.getZ() + Math.sin(a) * d);
            int y = w.getTopY(Heightmap.Type.MOTION_BLOCKING_NO_LEAVES, x, z);
            BlockPos f = new BlockPos(x, y, z);
            if (w.getBlockState(f).isAir()) {
                w.setBlockState(f, AbstractFireBlock.getState(w, f), Block.NOTIFY_ALL);
            }
        }
    }

    private static int place(ServerWorld w, List<BlockPos> inner, int idx, int count, BlockState ore) {
        for (int i = 0; i < count && idx < inner.size(); i++, idx++) {
            w.setBlockState(inner.get(idx), ore, Block.NOTIFY_ALL);
        }
        return idx;
    }

    private static BlockState rock(Random r) {
        float f = r.nextFloat();
        if (f < 0.30f) {
            return Blocks.MAGMA_BLOCK.getDefaultState();
        }
        if (f < 0.70f) {
            return Blocks.BLACKSTONE.getDefaultState();
        }
        if (f < 0.90f) {
            return Blocks.BASALT.getDefaultState();
        }
        return Blocks.OBSIDIAN.getDefaultState();
    }

    private static boolean breakable(ServerWorld w, BlockPos p) {
        BlockState st = w.getBlockState(p);
        return !st.isOf(Blocks.BEDROCK) && w.getBlockEntity(p) == null && st.getHardness(w, p) >= 0;
    }

    private static Text dirFrom(BlockPos from, BlockPos at) {
        double a = Math.toDegrees(Math.atan2(at.getZ() - from.getZ(), at.getX() - from.getX()));
        String[] dirs = {"east", "southeast", "south", "southwest", "west", "northwest", "north", "northeast"};
        int i = Math.floorMod(Math.round((float) (a / 45.0)), 8);
        return Text.translatable("director.basedefense.dir2." + dirs[i]);
    }
}

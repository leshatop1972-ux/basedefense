package com.lesha_top1972.basedefense.entity;

import net.minecraft.block.Blocks;
import net.minecraft.particle.BlockStateParticleEffect;
import net.minecraft.particle.DustParticleEffect;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.world.World;
import org.joml.Vector3f;

/** Брызги крови (сервер рассылает всем): алые капли‑кусочки + тёмно‑бордовая взвесь. */
public final class BloodFx {

    private static final BlockStateParticleEffect DROPS =
            new BlockStateParticleEffect(ParticleTypes.BLOCK, Blocks.REDSTONE_BLOCK.getDefaultState());
    private static final BlockStateParticleEffect CLOTS =
            new BlockStateParticleEffect(ParticleTypes.BLOCK, Blocks.NETHER_WART_BLOCK.getDefaultState());
    private static final DustParticleEffect MIST = new DustParticleEffect(new Vector3f(0.5f, 0.02f, 0.06f), 1.6f);

    private BloodFx() {
    }

    /** count — сила брызг (≈ число капель), spread — разброс в блоках. */
    public static void splash(World world, double x, double y, double z, int count, double spread) {
        if (!(world instanceof ServerWorld s) || count <= 0) {
            return;
        }
        s.spawnParticles(DROPS, x, y, z, count, spread, spread * 0.6, spread, 0.2);
        s.spawnParticles(CLOTS, x, y, z, Math.max(1, count / 3), spread, spread * 0.5, spread, 0.15);
        s.spawnParticles(MIST, x, y, z, Math.max(1, count / 2), spread, spread * 0.6, spread, 0.0);
    }
}

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
        // одним пакетом: капли, сгустки и взвесь рисует клиент (FxClient.blood)
        com.lesha_top1972.basedefense.fx.Fx.blood(s, new net.minecraft.util.math.Vec3d(x, y, z), count, spread);
    }
}

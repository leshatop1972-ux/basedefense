package com.lesha_top1972.basedefense.block;

import com.lesha_top1972.basedefense.entity.enemy.nabat.NabatNoise;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.particle.DustParticleEffect;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.random.Random;
import net.minecraft.world.World;
import org.joml.Vector3f;

/**
 * «Гудящий камень» — холмы, которые вспучивает «Землетрясение» Набата. Стоишь на нём — твой «Шум» растёт
 * (+5 очков в секунду). Через 45 секунд после землетрясения холмы осыпаются сами. Ничего не выпадает.
 */
public class ResonantStoneBlock extends Block {

    private static final DustParticleEffect AMBER = new DustParticleEffect(new Vector3f(1.0f, 0.72f, 0.32f), 0.7f);

    public ResonantStoneBlock(Settings settings) {
        super(settings);
    }

    @Override
    public void onSteppedOn(World world, BlockPos pos, BlockState state, Entity entity) {
        if (!world.isClient && entity instanceof PlayerEntity p) {
            NabatNoise.add(p, 0.25f);
        }
        super.onSteppedOn(world, pos, state, entity);
    }

    @Override
    public void randomDisplayTick(BlockState state, World world, BlockPos pos, Random random) {
        if (random.nextInt(3) == 0 && world.getBlockState(pos.up()).isAir()) {
            world.addParticle(AMBER, pos.getX() + random.nextDouble(), pos.getY() + 1.02, pos.getZ() + random.nextDouble(),
                    0, 0.01, 0);
        }
    }
}

package com.lesha_top1972.basedefense.block;

import com.lesha_top1972.basedefense.structure.BuildManager;
import com.lesha_top1972.basedefense.structure.FarmerHousePlan;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.entity.LivingEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.world.World;
import org.jetbrains.annotations.Nullable;

/**
 * Блок «Дом фермера». Сразу после установки начинает возводить домик:
 * дверь смотрит на игрока, дом и огород строятся «вглубь» от него.
 */
public class FarmerHouseBlock extends Block {

    public FarmerHouseBlock(Settings settings) {
        super(settings);
    }

    @Override
    public void onPlaced(World world, BlockPos pos, BlockState state, @Nullable LivingEntity placer, ItemStack itemStack) {
        super.onPlaced(world, pos, state, placer, itemStack);
        if (world instanceof ServerWorld serverWorld) {
            Direction facing = placer != null ? placer.getHorizontalFacing() : Direction.NORTH;
            BuildManager.start(serverWorld, FarmerHousePlan.create(), pos, facing);
        }
    }
}

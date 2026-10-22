package com.lesha_top1972.basedefense.block;

import com.lesha_top1972.basedefense.structure.BuildManager;
import com.lesha_top1972.basedefense.structure.StructurePlan;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.entity.LivingEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.world.World;
import org.jetbrains.annotations.Nullable;

import java.util.function.Supplier;

/**
 * Блок-домик (дом фермера, дом скотовода и т.д.). Сразу после установки
 * начинает возводить постройку по чертежу: дверь смотрит на игрока,
 * дом и двор строятся «вглубь» от него.
 */
public class HouseBlock extends Block {

    private final Supplier<StructurePlan> plan;

    public HouseBlock(Settings settings, Supplier<StructurePlan> plan) {
        super(settings);
        this.plan = plan;
    }

    @Override
    public void onPlaced(World world, BlockPos pos, BlockState state, @Nullable LivingEntity placer, ItemStack itemStack) {
        super.onPlaced(world, pos, state, placer, itemStack);
        if (world instanceof ServerWorld serverWorld) {
            Direction facing = placer != null ? placer.getHorizontalFacing() : Direction.NORTH;
            BuildManager.start(serverWorld, plan.get(), pos, facing);
        }
    }
}

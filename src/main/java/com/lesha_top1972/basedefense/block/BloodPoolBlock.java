package com.lesha_top1972.basedefense.block;

import com.lesha_top1972.basedefense.entity.BloodKin;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.ShapeContext;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.random.Random;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.shape.VoxelShape;
import net.minecraft.world.BlockView;
import net.minecraft.world.World;
import net.minecraft.world.WorldAccess;
import net.minecraft.world.WorldView;

/**
 * «Кровавая лужа» — растёт вокруг яиц кровожада и раз в 7 с появляется рядом с самим боссом
 * (такая лужа сама высыхает через {@link #BOSS_POOL_TICKS} тиков — запланированный тик блока).
 * Тонкий слой (как ковёр); кто стоит на ней — получает 0.5 урона в секунду (кровожад и его выводок — нет),
 * у игрока при этом чуть краснеет экран (client/render/BloodPoolOverlay). Ничего не выпадает.
 */
public class BloodPoolBlock extends Block {

    private static final VoxelShape SHAPE = Block.createCuboidShape(0, 0, 0, 16, 1, 16);
    public static final float DAMAGE = 0.5f;
    /** Сколько живёт лужа, которую поставил босс (у яиц лужа живёт, пока живо яйцо). */
    public static final int BOSS_POOL_TICKS = 300;

    public BloodPoolBlock(Settings settings) {
        super(settings);
    }

    @Override
    public VoxelShape getOutlineShape(BlockState state, BlockView world, BlockPos pos, ShapeContext context) {
        return SHAPE;
    }

    @Override
    public void onEntityCollision(BlockState state, World world, BlockPos pos, Entity entity) {
        if (!world.isClient && entity instanceof LivingEntity living && !(entity instanceof BloodKin)
                && living.age % 20 == 0 && entity.getY() < pos.getY() + 0.5) {
            living.damage(world.getDamageSources().magic(), DAMAGE);
        }
    }

    /** Запланированный тик бывает только у луж босса: лужа высыхает. */
    @Override
    public void scheduledTick(BlockState state, ServerWorld world, BlockPos pos, Random random) {
        world.removeBlock(pos, false);
    }

    @Override
    public boolean canPlaceAt(BlockState state, WorldView world, BlockPos pos) {
        return world.getBlockState(pos.down()).isSideSolidFullSquare(world, pos.down(), Direction.UP);
    }

    @Override
    public BlockState getStateForNeighborUpdate(BlockState state, Direction direction, BlockState neighborState,
                                                WorldAccess world, BlockPos pos, BlockPos neighborPos) {
        if (direction == Direction.DOWN && !state.canPlaceAt(world, pos)) {
            return net.minecraft.block.Blocks.AIR.getDefaultState();
        }
        return super.getStateForNeighborUpdate(state, direction, neighborState, world, pos, neighborPos);
    }
}

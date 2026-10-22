package com.lesha_top1972.basedefense.structure;

import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.util.BlockRotation;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;

import java.util.List;

/** Постройка, которая возводится постепенно — несколько блоков за тик. */
public class BuildTask {

    /** Сколько «шагов» (блоков) ставится за один тик. 20 тиков = 1 секунда. */
    private static final int STEPS_PER_TICK = 10;

    private final ServerWorld world;
    private final StructurePlan plan;
    private final BlockPos placedPos;
    private final Direction facing;
    private final BlockRotation rotation;
    private final List<List<StructurePlan.Placement>> steps;
    private int index = 0;
    private int effectCounter = 0;

    public BuildTask(ServerWorld world, StructurePlan plan, BlockPos placedPos, Direction facing) {
        this.world = world;
        this.plan = plan;
        this.placedPos = placedPos;
        this.facing = facing;
        this.rotation = rotationFor(facing);
        this.steps = plan.toSteps();
    }

    public ServerWorld getWorld() {
        return world;
    }

    public Direction getFacing() {
        return facing;
    }

    /** Переводит локальные координаты чертежа в координаты мира. */
    public BlockPos toWorld(int x, int y, int z) {
        BlockPos origin = plan.getOrigin();
        Direction right = facing.rotateYClockwise();
        return placedPos
                .offset(right, x - origin.getX())
                .offset(facing, z - origin.getZ())
                .up(y - origin.getY());
    }

    /** @return true, когда постройка завершена */
    public boolean tick() {
        int budget = STEPS_PER_TICK;
        while (index < steps.size() && budget > 0) {
            boolean changed = false;
            for (StructurePlan.Placement p : steps.get(index)) {
                changed |= place(p);
            }
            index++;
            if (changed) {
                budget--;
            }
        }
        if (index >= steps.size()) {
            plan.getFinishAction().run(this);
            return true;
        }
        return false;
    }

    private boolean place(StructurePlan.Placement p) {
        BlockPos pos = toWorld(p.local().getX(), p.local().getY(), p.local().getZ());
        BlockState current = world.getBlockState(pos);
        BlockState target = p.state().rotate(rotation);

        if (current.getHardness(world, pos) < 0) {
            return false; // бедрок и прочие неразрушимые блоки не трогаем
        }
        if (p.onlyIfReplaceable() && !current.isReplaceable()) {
            return false;
        }
        if (current.equals(target)) {
            return false;
        }
        if (current.hasBlockEntity()) {
            world.breakBlock(pos, true); // сундуки и т.п. — содержимое выпадет, а не пропадёт
        }
        if (p.connect()) {
            target = Block.postProcessState(target, world, pos);
        }
        world.setBlockState(pos, target, Block.NOTIFY_ALL);

        if (!target.isAir() && ++effectCounter % 3 == 0) {
            world.spawnParticles(ParticleTypes.CLOUD, pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5,
                    2, 0.3, 0.3, 0.3, 0.01);
            world.playSound(null, pos, target.getSoundGroup().getPlaceSound(), SoundCategory.BLOCKS, 0.5f, 1.0f);
        }
        return true;
    }

    private static BlockRotation rotationFor(Direction facing) {
        return switch (facing) {
            case EAST -> BlockRotation.CLOCKWISE_90;
            case SOUTH -> BlockRotation.CLOCKWISE_180;
            case WEST -> BlockRotation.COUNTERCLOCKWISE_90;
            default -> BlockRotation.NONE;
        };
    }
}

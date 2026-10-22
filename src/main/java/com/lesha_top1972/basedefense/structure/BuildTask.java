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

/**
 * Постройка по чертежу. Два режима:
 * - сама собой, несколько блоков за тик ({@link #tick()}, дом строителя);
 * - строителем, по одному шагу ({@link #needsWork}, {@link #placeStep}) — см. BuilderEntity.
 */
public class BuildTask {

    /** Сколько «шагов» (блоков) ставится за один тик в режиме «сама собой». */
    private static final int STEPS_PER_TICK = 10;

    private final ServerWorld world;
    private final StructurePlan plan;
    private final BlockPos placedPos;
    private final Direction facing;
    private final BlockRotation rotation;
    private final List<List<StructurePlan.Placement>> steps;
    /** Не трогать клетку, где стоит блок дома (строитель убирает его в конце). */
    private final boolean keepPlacedBlock;
    private int index = 0;
    private int effectCounter = 0;
    /** Без частиц и звуков на каждый блок (мгновенная постройка в творческом). */
    private boolean quiet;

    public BuildTask(ServerWorld world, StructurePlan plan, BlockPos placedPos, Direction facing) {
        this(world, plan, plan.toSteps(), placedPos, facing, false);
    }

    public BuildTask(ServerWorld world, StructurePlan plan, List<List<StructurePlan.Placement>> steps,
                     BlockPos placedPos, Direction facing, boolean keepPlacedBlock) {
        this.world = world;
        this.plan = plan;
        this.placedPos = placedPos;
        this.facing = facing;
        this.rotation = rotationFor(facing);
        this.steps = steps;
        this.keepPlacedBlock = keepPlacedBlock;
    }

    public void setQuiet(boolean quiet) {
        this.quiet = quiet;
    }

    public ServerWorld getWorld() {
        return world;
    }

    public Direction getFacing() {
        return facing;
    }

    public StructurePlan getPlan() {
        return plan;
    }

    public List<List<StructurePlan.Placement>> getSteps() {
        return steps;
    }

    public int getIndex() {
        return index;
    }

    public void setIndex(int index) {
        this.index = Math.max(0, Math.min(steps.size(), index));
    }

    public boolean isDone() {
        return index >= steps.size();
    }

    /** Переводит локальные координаты чертежа в координаты мира. */
    public BlockPos toWorld(int x, int y, int z) {
        return plan.toWorld(placedPos, facing, x, y, z);
    }

    /** Режим «сама собой». @return true, когда постройка завершена */
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

    /** Нужно ли что-то менять в мире для этого блока чертежа (если нет — шаг пропускается бесплатно). */
    public boolean needsWork(StructurePlan.Placement p) {
        if (keepPlacedBlock && p.local().equals(plan.getOrigin())) {
            return false;
        }
        BlockPos pos = toWorld(p.local().getX(), p.local().getY(), p.local().getZ());
        if (!world.isChunkLoaded(pos)) {
            return false;
        }
        BlockState current = world.getBlockState(pos);
        BlockState target = p.state().rotate(rotation);
        if (current.getHardness(world, pos) < 0) {
            return false;
        }
        if (p.onlyIfReplaceable() && !current.isReplaceable()) {
            return false;
        }
        return !current.equals(target);
    }

    /** Поставить все блоки текущего шага и перейти к следующему. */
    public void placeStep() {
        if (index >= steps.size()) {
            return;
        }
        for (StructurePlan.Placement p : steps.get(index)) {
            if (keepPlacedBlock && p.local().equals(plan.getOrigin())) {
                continue;
            }
            place(p);
        }
        index++;
    }

    /** Мировые координаты блока чертежа. */
    public BlockPos worldPos(StructurePlan.Placement p) {
        return toWorld(p.local().getX(), p.local().getY(), p.local().getZ());
    }

    private boolean place(StructurePlan.Placement p) {
        BlockPos pos = worldPos(p);
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

        if (!quiet && !target.isAir() && ++effectCounter % 3 == 0) {
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

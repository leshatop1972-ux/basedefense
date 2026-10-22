package com.lesha_top1972.basedefense.structure;

import net.minecraft.block.BlockState;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Чертёж постройки в «локальных» координатах:
 * x — вправо от игрока, y — вверх, z — вглубь (от игрока).
 * Блоки описываются так, будто игрок смотрит на север, при постройке
 * всё поворачивается под реальное направление взгляда.
 */
public class StructurePlan {

    /** Один блок чертежа. */
    public record Placement(BlockPos local, BlockState state, boolean onlyIfReplaceable, boolean connect) {
    }

    /** Что сделать, когда постройка закончена (например, заселить жителя). */
    @FunctionalInterface
    public interface FinishAction {
        void run(BuildTask task);
    }

    private final Map<BlockPos, Placement> main = new LinkedHashMap<>();
    private final List<List<Placement>> late = new ArrayList<>();
    private final BlockPos origin;
    private FinishAction finishAction = task -> {
    };

    /** @param origin локальная точка, которая совпадает с поставленным блоком */
    public StructurePlan(BlockPos origin) {
        this.origin = origin;
    }

    public BlockPos getOrigin() {
        return origin;
    }

    /** Обычный блок (заменяет то, что там стояло). */
    public void set(int x, int y, int z, BlockState state) {
        BlockPos pos = new BlockPos(x, y, z);
        main.put(pos, new Placement(pos, state, false, false));
    }

    /** Заливка: ставится, только если там воздух/вода/трава (для фундамента). */
    public void fill(int x, int y, int z, BlockState state) {
        BlockPos pos = new BlockPos(x, y, z);
        main.put(pos, new Placement(pos, state, true, false));
    }

    /** Блок, который ставится в самом конце (факелы, ростки, ковры и т.п.). */
    public void late(int x, int y, int z, BlockState state) {
        late.add(List.of(new Placement(new BlockPos(x, y, z), state, false, false)));
    }

    /** Блок в конце, который соединяется с соседями (заборы, панели). */
    public void lateConnected(int x, int y, int z, BlockState state) {
        late.add(List.of(new Placement(new BlockPos(x, y, z), state, false, true)));
    }

    /** Двойной блок (дверь, кровать): обе половины ставятся одновременно. */
    public void lateDouble(int x1, int y1, int z1, BlockState first, int x2, int y2, int z2, BlockState second) {
        late.add(List.of(
                new Placement(new BlockPos(x1, y1, z1), first, false, false),
                new Placement(new BlockPos(x2, y2, z2), second, false, false)));
    }

    /** Самый маленький и самый большой угол чертежа (локальные координаты): {min, max}. */
    public BlockPos[] getBounds() {
        int minX = Integer.MAX_VALUE, minY = Integer.MAX_VALUE, minZ = Integer.MAX_VALUE;
        int maxX = Integer.MIN_VALUE, maxY = Integer.MIN_VALUE, maxZ = Integer.MIN_VALUE;
        List<BlockPos> all = new ArrayList<>(main.keySet());
        for (List<Placement> step : late) {
            for (Placement p : step) {
                all.add(p.local());
            }
        }
        all.add(origin);
        for (BlockPos p : all) {
            minX = Math.min(minX, p.getX());
            minY = Math.min(minY, p.getY());
            minZ = Math.min(minZ, p.getZ());
            maxX = Math.max(maxX, p.getX());
            maxY = Math.max(maxY, p.getY());
            maxZ = Math.max(maxZ, p.getZ());
        }
        return new BlockPos[]{new BlockPos(minX, minY, minZ), new BlockPos(maxX, maxY, maxZ)};
    }

    /**
     * Переводит локальные координаты чертежа в координаты мира
     * (блок постройки стоит в placedPos, игрок смотрел в сторону facing).
     */
    public BlockPos toWorld(BlockPos placedPos, Direction facing, int x, int y, int z) {
        Direction right = facing.rotateYClockwise();
        return placedPos
                .offset(right, x - origin.getX())
                .offset(facing, z - origin.getZ())
                .up(y - origin.getY());
    }

    public void onFinish(FinishAction action) {
        this.finishAction = action;
    }

    public FinishAction getFinishAction() {
        return finishAction;
    }

    /** Порядок постройки: основные блоки снизу вверх, потом «поздние». */
    public List<List<Placement>> toSteps() {
        List<Placement> sorted = new ArrayList<>(main.values());
        sorted.sort(Comparator.comparingInt(p -> p.local().getY()));
        List<List<Placement>> steps = new ArrayList<>(sorted.size() + late.size());
        for (Placement p : sorted) {
            steps.add(List.of(p));
        }
        steps.addAll(late);
        return steps;
    }
}

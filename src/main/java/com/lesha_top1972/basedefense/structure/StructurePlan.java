package com.lesha_top1972.basedefense.structure;

import net.minecraft.block.BlockState;
import net.minecraft.util.math.BlockPos;

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

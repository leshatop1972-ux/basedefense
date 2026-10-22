package com.lesha_top1972.basedefense.structure;

import com.lesha_top1972.basedefense.entity.ModEntities;
import com.lesha_top1972.basedefense.entity.SettlerEntity;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.block.LeveledCauldronBlock;
import net.minecraft.block.enums.BedPart;
import net.minecraft.block.enums.DoubleBlockHalf;
import net.minecraft.entity.EntityType;
import net.minecraft.state.property.Properties;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;

/**
 * Чертежи домов поселения. Все дома одного размера: дом 8×10 + двор 8×6 за ним.
 *
 * Локальные координаты (игрок смотрит «на север»):
 *   x = 0..7  — ширина дома (слева направо),
 *   z = 0..9  — дом (z = 0 — передняя стена с дверью), z = 10..15 — двор,
 *   y = 0     — пол (на уровне земли), y = 1..4 — стены, y = 5..9 — крыша.
 * Поставленный блок — это крыльцо перед дверью: (3, 1, -1).
 */
public final class HousePlans {

    private static final int W = 8;          // ширина дома
    private static final int D = 10;         // глубина дома
    private static final int YARD_END = 15;  // последний ряд двора

    /** Материалы, из которых строится коробка дома. */
    private record Materials(Block wall, Block cornerLog, Block floor, Block roofStairs, Block gable) {
    }

    private HousePlans() {
    }

    // =====================================================================
    // Дом фермера: светлый дуб, внутри компостер; за домом огород
    // =====================================================================
    public static StructurePlan farmer() {
        StructurePlan p = new StructurePlan(new BlockPos(3, 1, -1));
        buildShell(p, new Materials(Blocks.OAK_PLANKS, Blocks.OAK_LOG, Blocks.SPRUCE_PLANKS,
                Blocks.SPRUCE_STAIRS, Blocks.SPRUCE_PLANKS));

        // Мебель
        p.set(1, 1, 2, Blocks.CRAFTING_TABLE.getDefaultState());
        p.set(1, 1, 3, Blocks.BARREL.getDefaultState().with(Properties.FACING, Direction.UP));
        p.set(1, 1, 5, Blocks.CHEST.getDefaultState().with(Properties.HORIZONTAL_FACING, Direction.EAST));
        p.set(6, 1, 2, Blocks.COMPOSTER.getDefaultState());
        p.set(6, 1, 7, Blocks.BOOKSHELF.getDefaultState());
        addTable(p);
        addBed(p, Blocks.GREEN_BED);
        p.late(1, 2, 2, Blocks.POTTED_POPPY.getDefaultState());   // цветок на верстаке
        p.late(1, 2, 3, Blocks.LANTERN.getDefaultState());        // фонарь на бочке
        addCarpet(p, Blocks.GREEN_CARPET);

        // Огород: вспаханная земля и канавка с водой. Пока ничего не посажено.
        BlockState farmland = Blocks.FARMLAND.getDefaultState().with(Properties.MOISTURE, 7);
        for (int x = 0; x < W; x++) {
            for (int z = D; z <= YARD_END; z++) {
                BlockState ground;
                if (isYardEdge(x, z)) {
                    ground = Blocks.GRASS_BLOCK.getDefaultState();
                } else if (z == D) {
                    ground = Blocks.DIRT_PATH.getDefaultState();   // дорожка у задней двери
                } else if (z == 12) {
                    ground = Blocks.WATER.getDefaultState();
                } else {
                    ground = farmland;
                }
                p.set(x, 0, z, ground);
            }
        }
        addYardFence(p);

        settleResident(p, ModEntities.FARMER);
        return p;
    }

    // =====================================================================
    // Дом скотовода: тёмная ель, внутри коптильня; за домом загон для животных
    // =====================================================================
    public static StructurePlan rancher() {
        StructurePlan p = new StructurePlan(new BlockPos(3, 1, -1));
        buildShell(p, new Materials(Blocks.SPRUCE_PLANKS, Blocks.SPRUCE_LOG, Blocks.OAK_PLANKS,
                Blocks.DARK_OAK_STAIRS, Blocks.DARK_OAK_PLANKS));

        // Мебель
        p.set(1, 1, 2, Blocks.CRAFTING_TABLE.getDefaultState());
        p.set(1, 1, 3, Blocks.BARREL.getDefaultState().with(Properties.FACING, Direction.UP));
        p.set(1, 1, 5, Blocks.CHEST.getDefaultState().with(Properties.HORIZONTAL_FACING, Direction.EAST));
        p.set(6, 1, 2, Blocks.SMOKER.getDefaultState().with(Properties.HORIZONTAL_FACING, Direction.WEST));
        p.set(6, 1, 3, Blocks.CAULDRON.getDefaultState());
        p.set(6, 1, 7, Blocks.BARREL.getDefaultState().with(Properties.FACING, Direction.UP));
        addTable(p);
        addBed(p, Blocks.BROWN_BED);
        p.late(1, 2, 3, Blocks.LANTERN.getDefaultState());
        p.late(6, 2, 7, Blocks.LANTERN.getDefaultState());
        addCarpet(p, Blocks.BROWN_CARPET);

        // Загон: трава, корыто с водой и сено. Животных пока нет.
        for (int x = 0; x < W; x++) {
            for (int z = D; z <= YARD_END; z++) {
                p.set(x, 0, z, (z == D && !isYardEdge(x, z))
                        ? Blocks.DIRT_PATH.getDefaultState()
                        : Blocks.GRASS_BLOCK.getDefaultState());
            }
        }
        p.set(1, 1, 14, Blocks.HAY_BLOCK.getDefaultState());
        p.set(2, 1, 14, Blocks.HAY_BLOCK.getDefaultState());
        p.set(1, 2, 14, Blocks.HAY_BLOCK.getDefaultState());
        p.set(6, 1, 12, Blocks.WATER_CAULDRON.getDefaultState().with(LeveledCauldronBlock.LEVEL, 3));
        p.set(6, 1, 13, Blocks.WATER_CAULDRON.getDefaultState().with(LeveledCauldronBlock.LEVEL, 3));
        addYardFence(p);

        settleResident(p, ModEntities.RANCHER);
        return p;
    }

    // =====================================================================
    // Общие части
    // =====================================================================

    /** Расчистка, фундамент, пол, стены, окна, балки, крыша и двери. */
    private static void buildShell(StructurePlan p, Materials m) {
        BlockState air = Blocks.AIR.getDefaultState();
        BlockState cobble = Blocks.COBBLESTONE.getDefaultState();
        BlockState dirt = Blocks.DIRT.getDefaultState();
        BlockState floor = m.floor().getDefaultState();
        BlockState wall = m.wall().getDefaultState();
        BlockState logY = m.cornerLog().getDefaultState();
        BlockState logX = logY.with(Properties.AXIS, Direction.Axis.X);
        BlockState logZ = logY.with(Properties.AXIS, Direction.Axis.Z);
        BlockState glass = Blocks.GLASS_PANE.getDefaultState();
        BlockState gable = m.gable().getDefaultState();
        BlockState roofEast = m.roofStairs().getDefaultState().with(Properties.HORIZONTAL_FACING, Direction.EAST);
        BlockState roofWest = m.roofStairs().getDefaultState().with(Properties.HORIZONTAL_FACING, Direction.WEST);

        // Расчистка места (всё над землёй)
        for (int x = -1; x <= W; x++) {
            for (int z = -1; z <= YARD_END; z++) {
                for (int y = 1; y <= 11; y++) {
                    p.set(x, y, z, air);
                }
            }
        }
        // Фундамент: заполняем ямы под домом и двором
        for (int x = 0; x < W; x++) {
            for (int z = 0; z <= YARD_END; z++) {
                for (int y = -3; y <= -1; y++) {
                    p.fill(x, y, z, z < D ? cobble : dirt);
                }
            }
        }
        // Пол
        for (int x = 0; x < W; x++) {
            for (int z = 0; z < D; z++) {
                boolean edge = x == 0 || x == W - 1 || z == 0 || z == D - 1;
                p.set(x, 0, z, edge ? cobble : floor);
            }
        }
        p.set(3, 0, -1, Blocks.DIRT_PATH.getDefaultState()); // тропинка у крыльца

        // Стены
        for (int y = 1; y <= 4; y++) {
            for (int x = 0; x < W; x++) {
                for (int z = 0; z < D; z++) {
                    boolean sideX = x == 0 || x == W - 1;
                    boolean sideZ = z == 0 || z == D - 1;
                    if (!sideX && !sideZ) {
                        continue;
                    }
                    BlockState block = (sideX && sideZ) ? logY : (y == 1 ? cobble : wall);
                    p.set(x, y, z, block);
                }
            }
        }
        // Окна
        for (int y = 2; y <= 3; y++) {
            // Стекло ставится в самом конце и соединяется с соседями,
            // иначе панель без соседей выглядит как «крестик»
            for (int z : new int[]{3, 4, 6, 7}) {
                p.set(0, y, z, air);
                p.set(W - 1, y, z, air);
                p.lateConnected(0, y, z, glass);
                p.lateConnected(W - 1, y, z, glass);
            }
            for (int x : new int[]{1, 6}) {
                p.set(x, y, 0, air);
                p.set(x, y, D - 1, air);
                p.lateConnected(x, y, 0, glass);
                p.lateConnected(x, y, D - 1, glass);
            }
        }
        // Проёмы под двери
        p.set(3, 1, 0, air);
        p.set(3, 2, 0, air);
        p.set(4, 1, D - 1, air);
        p.set(4, 2, D - 1, air);

        // Балки поверх стен
        for (int z = 0; z < D; z++) {
            p.set(0, 5, z, logZ);
            p.set(W - 1, 5, z, logZ);
        }
        for (int x = 1; x < W - 1; x++) {
            p.set(x, 5, 0, logX);
            p.set(x, 5, D - 1, logX);
        }

        // Двускатная крыша с фронтонами
        for (int r = 0; r <= 4; r++) {
            int y = 5 + r;
            for (int z = -1; z <= D; z++) {
                p.set(-1 + r, y, z, roofEast);
                p.set(W - r, y, z, roofWest);
            }
            if (r >= 1 && r <= 3) {
                for (int x = r; x <= W - 1 - r; x++) {
                    p.set(x, y, 0, gable);
                    p.set(x, y, D - 1, gable);
                }
            }
        }
        // Окошко на чердаке
        p.set(3, 7, 0, air);
        p.set(4, 7, 0, air);
        p.set(3, 7, D - 1, air);
        p.set(4, 7, D - 1, air);
        p.lateConnected(3, 7, 0, glass);
        p.lateConnected(4, 7, 0, glass);
        p.lateConnected(3, 7, D - 1, glass);
        p.lateConnected(4, 7, D - 1, glass);

        // Двери, подвесные фонари, факелы у входа
        BlockState door = Blocks.OAK_DOOR.getDefaultState().with(Properties.HORIZONTAL_FACING, Direction.NORTH);
        p.lateDouble(3, 1, 0, door.with(Properties.DOUBLE_BLOCK_HALF, DoubleBlockHalf.LOWER),
                3, 2, 0, door.with(Properties.DOUBLE_BLOCK_HALF, DoubleBlockHalf.UPPER));
        p.lateDouble(4, 1, D - 1, door.with(Properties.DOUBLE_BLOCK_HALF, DoubleBlockHalf.LOWER),
                4, 2, D - 1, door.with(Properties.DOUBLE_BLOCK_HALF, DoubleBlockHalf.UPPER));
        p.late(3, 8, 3, Blocks.LANTERN.getDefaultState().with(Properties.HANGING, true));
        p.late(4, 8, 6, Blocks.LANTERN.getDefaultState().with(Properties.HANGING, true));
        BlockState torch = Blocks.WALL_TORCH.getDefaultState().with(Properties.HORIZONTAL_FACING, Direction.SOUTH);
        p.late(2, 3, -1, torch);
        p.late(4, 3, -1, torch);
    }

    /** Стол (столбик забора + нажимная плита) и два стула. */
    private static void addTable(StructurePlan p) {
        p.set(5, 1, 5, Blocks.OAK_FENCE.getDefaultState());
        p.set(4, 1, 5, Blocks.OAK_STAIRS.getDefaultState().with(Properties.HORIZONTAL_FACING, Direction.WEST));
        p.set(6, 1, 5, Blocks.OAK_STAIRS.getDefaultState().with(Properties.HORIZONTAL_FACING, Direction.EAST));
        p.late(5, 2, 5, Blocks.OAK_PRESSURE_PLATE.getDefaultState());
    }

    private static void addBed(StructurePlan p, Block bedBlock) {
        BlockState bed = bedBlock.getDefaultState().with(Properties.HORIZONTAL_FACING, Direction.NORTH);
        p.lateDouble(1, 1, 7, bed.with(Properties.BED_PART, BedPart.FOOT),
                1, 1, 8, bed.with(Properties.BED_PART, BedPart.HEAD));
    }

    private static void addCarpet(StructurePlan p, Block carpet) {
        for (int x = 2; x <= 3; x++) {
            for (int z = 3; z <= 7; z++) {
                p.late(x, 1, z, carpet.getDefaultState());
            }
        }
    }

    private static boolean isYardEdge(int x, int z) {
        return x == 0 || x == W - 1 || z == YARD_END;
    }

    /** Забор вокруг двора с калиткой и фонарями на углах. */
    private static void addYardFence(StructurePlan p) {
        BlockState fence = Blocks.OAK_FENCE.getDefaultState();
        for (int z = D; z <= YARD_END; z++) {
            p.lateConnected(0, 1, z, fence);
            p.lateConnected(W - 1, 1, z, fence);
        }
        for (int x = 1; x < W - 1; x++) {
            p.lateConnected(x, 1, YARD_END, x == 4
                    ? Blocks.OAK_FENCE_GATE.getDefaultState().with(Properties.HORIZONTAL_FACING, Direction.NORTH)
                    : fence);
        }
        p.late(0, 2, YARD_END, Blocks.LANTERN.getDefaultState());
        p.late(W - 1, 2, YARD_END, Blocks.LANTERN.getDefaultState());
    }

    /** Когда дом готов — в нём появляется житель. */
    private static void settleResident(StructurePlan p, EntityType<? extends SettlerEntity> type) {
        p.onFinish(task -> {
            SettlerEntity settler = type.create(task.getWorld());
            if (settler == null) {
                return;
            }
            BlockPos spawn = task.toWorld(3, 1, 4);
            settler.refreshPositionAndAngles(spawn.getX() + 0.5, spawn.getY(), spawn.getZ() + 0.5,
                    task.getFacing().getOpposite().asRotation(), 0.0f);
            settler.setHome(spawn);
            settler.setPersistent();
            task.getWorld().spawnEntity(settler);
        });
    }
}

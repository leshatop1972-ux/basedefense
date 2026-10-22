package com.lesha_top1972.basedefense.structure;

import com.lesha_top1972.basedefense.entity.ModEntities;
import com.lesha_top1972.basedefense.entity.SettlerEntity;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.block.enums.BedPart;
import net.minecraft.block.enums.DoubleBlockHalf;
import net.minecraft.entity.EntityType;
import net.minecraft.state.property.Properties;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;

/**
 * Чертежи домов поселения. ВСЕ дома в едином стиле: одна и та же коробка 8×10
 * (дубовые стены, еловые балки и крыша, булыжный цоколь) и двор 8×6 за ней.
 * Различаются только цвет кровати/ковра, мебель в двух углах и двор:
 * огород, загон, летняя кухня, горн, вход в шахту или посадка деревьев.
 *
 * В каждом доме сундук (6, 1, 8) — туда житель сам складывает добычу.
 * Баланс: в домах нет дорогих блоков и (кроме этого сундука) блоков из рецептов домов.
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

    /** Где в доме сундук для добычи. */
    private static final int CHEST_X = 6, CHEST_Y = 1, CHEST_Z = 8;

    /** Единые материалы всех домов. */
    private static final Block WALL = Blocks.OAK_PLANKS;
    private static final Block CORNER_LOG = Blocks.SPRUCE_LOG;   // ель: лесоруб еловые брёвна не трогает
    private static final Block FLOOR = Blocks.SPRUCE_PLANKS;
    private static final Block ROOF_STAIRS = Blocks.SPRUCE_STAIRS;
    private static final Block GABLE = Blocks.SPRUCE_PLANKS;

    private HousePlans() {
    }

    /** Начало любого дома: коробка, общая мебель, сундук. Цвет кровати и ковра — у каждой профессии свой. */
    private static StructurePlan baseHouse(Block bed, Block carpet) {
        StructurePlan p = new StructurePlan(new BlockPos(3, 1, -1));
        buildShell(p);
        addTable(p);
        addBed(p, bed);
        addCarpet(p, carpet);
        p.set(1, 1, 5, barrel());
        p.set(CHEST_X, CHEST_Y, CHEST_Z,
                Blocks.CHEST.getDefaultState().with(Properties.HORIZONTAL_FACING, Direction.WEST));
        return p;
    }

    private static BlockState barrel() {
        return Blocks.BARREL.getDefaultState().with(Properties.FACING, Direction.UP);
    }

    /** Земля двора: трава по краю, дорожка у задней двери, внутри — заданный блок. */
    private static void yardGround(StructurePlan p, BlockState inner) {
        for (int x = 0; x < W; x++) {
            for (int z = D; z <= YARD_END; z++) {
                BlockState ground;
                if (isYardEdge(x, z)) {
                    ground = Blocks.GRASS_BLOCK.getDefaultState();
                } else if (z == D) {
                    ground = Blocks.DIRT_PATH.getDefaultState();
                } else {
                    ground = inner;
                }
                p.set(x, 0, z, ground);
            }
        }
    }

    // =====================================================================
    // Фермер: во дворе огород (пашня — рабочее место)
    // =====================================================================
    public static StructurePlan farmer() {
        StructurePlan p = baseHouse(Blocks.GREEN_BED, Blocks.GREEN_CARPET);
        p.set(1, 1, 2, Blocks.FLETCHING_TABLE.getDefaultState());
        p.set(1, 1, 3, barrel());
        p.set(6, 1, 2, Blocks.COMPOSTER.getDefaultState());
        p.set(6, 1, 3, Blocks.HAY_BLOCK.getDefaultState());
        p.late(1, 2, 2, Blocks.POTTED_POPPY.getDefaultState());

        yardGround(p, Blocks.FARMLAND.getDefaultState().with(Properties.MOISTURE, 7));
        for (int x = 1; x < W - 1; x++) {
            p.set(x, 0, 12, Blocks.WATER.getDefaultState());   // канавка с водой
        }
        addYardFence(p);

        settleResident(p, ModEntities.FARMER);
        return p;
    }

    // =====================================================================
    // Скотовод: во дворе загон с сеном (рабочее место) и поилкой
    // =====================================================================
    public static StructurePlan rancher() {
        StructurePlan p = baseHouse(Blocks.BROWN_BED, Blocks.BROWN_CARPET);
        p.set(1, 1, 2, Blocks.COMPOSTER.getDefaultState());
        p.set(1, 1, 3, barrel());
        p.set(6, 1, 2, barrel());
        p.set(6, 1, 3, barrel());

        yardGround(p, Blocks.GRASS_BLOCK.getDefaultState());
        p.set(1, 1, 14, Blocks.HAY_BLOCK.getDefaultState());
        p.set(2, 1, 14, Blocks.HAY_BLOCK.getDefaultState());
        p.set(1, 2, 14, Blocks.HAY_BLOCK.getDefaultState());
        p.set(6, 0, 12, Blocks.WATER.getDefaultState());   // поилка — вода в земле
        p.set(6, 0, 13, Blocks.WATER.getDefaultState());
        addYardFence(p);

        settleResident(p, ModEntities.RANCHER);
        return p;
    }

    // =====================================================================
    // Повар: кирпичная печь в доме (рабочее место), во дворе летняя кухня
    // =====================================================================
    public static StructurePlan cook() {
        StructurePlan p = baseHouse(Blocks.WHITE_BED, Blocks.RED_CARPET);
        p.set(6, 1, 2, Blocks.BRICKS.getDefaultState());   // кирпичная печь
        p.set(6, 1, 3, Blocks.BRICKS.getDefaultState());
        p.set(1, 1, 2, Blocks.SMOOTH_STONE.getDefaultState());   // разделочный стол
        p.set(1, 1, 3, barrel());
        p.late(1, 2, 3, Blocks.POTTED_RED_MUSHROOM.getDefaultState());

        yardGround(p, Blocks.GRASS_BLOCK.getDefaultState());
        for (int z = D + 1; z < YARD_END; z++) {
            p.set(4, 0, z, Blocks.DIRT_PATH.getDefaultState());
        }
        p.set(2, 1, 12, Blocks.BRICKS.getDefaultState());   // уличная печь
        p.set(1, 1, 11, barrel());
        p.set(1, 1, 12, barrel());
        p.set(6, 1, 11, Blocks.PUMPKIN.getDefaultState());
        p.set(6, 1, 12, Blocks.CARVED_PUMPKIN.getDefaultState().with(Properties.HORIZONTAL_FACING, Direction.WEST));
        p.set(6, 1, 14, Blocks.HAY_BLOCK.getDefaultState());
        p.set(2, 1, 14, Blocks.COMPOSTER.getDefaultState());
        addYardFence(p);

        settleResident(p, ModEntities.COOK);
        return p;
    }

    // =====================================================================
    // Кузнец: в доме точило и верстак из камня, во дворе горн
    // =====================================================================
    public static StructurePlan blacksmith() {
        StructurePlan p = baseHouse(Blocks.GRAY_BED, Blocks.GRAY_CARPET);
        p.set(1, 1, 2, Blocks.CHISELED_STONE_BRICKS.getDefaultState());
        p.set(1, 1, 3, Blocks.GRINDSTONE.getDefaultState());
        p.set(6, 1, 2, Blocks.POLISHED_ANDESITE.getDefaultState());
        p.set(6, 1, 3, Blocks.SMOOTH_STONE.getDefaultState());

        yardGround(p, Blocks.COBBLESTONE.getDefaultState());
        for (int x = 1; x <= 3; x++) {
            p.set(x, 1, 13, Blocks.STONE_BRICKS.getDefaultState());
            p.set(x, 2, 13, Blocks.STONE_BRICK_SLAB.getDefaultState());
        }
        p.set(2, 1, 13, Blocks.MAGMA_BLOCK.getDefaultState());   // горн
        p.set(5, 1, 12, Blocks.POLISHED_ANDESITE.getDefaultState());
        p.set(6, 1, 12, barrel());
        addYardFence(p);

        settleResident(p, ModEntities.BLACKSMITH);
        return p;
    }

    // =====================================================================
    // Шахтёр: во дворе вход в шахту — спуск на 9 ступеней и забой внизу
    // =====================================================================
    private static final int MINE_DEPTH = 9;
    /** Левый край спуска (ширина 2: x = 3..4) и клетка начала спуска. */
    private static final int SHAFT_X = 3;
    private static final int SHAFT_Z = 11;

    public static StructurePlan miner() {
        StructurePlan p = baseHouse(Blocks.BLACK_BED, Blocks.LIGHT_GRAY_CARPET);
        p.set(1, 1, 2, Blocks.CARTOGRAPHY_TABLE.getDefaultState());
        p.set(1, 1, 3, barrel());
        p.set(6, 1, 2, barrel());
        p.set(6, 1, 3, Blocks.SMOOTH_STONE.getDefaultState());

        yardGround(p, Blocks.COARSE_DIRT.getDefaultState());
        addYardFence(p);
        buildMineShaft(p);   // после забора двора: спуск пробивает землю под ним

        settleResident(p, ModEntities.MINER);
        return p;
    }

    // =====================================================================
    // Лесоруб: во дворе посадка деревьев (их брёвна — рабочее место) и поленница
    // =====================================================================
    public static StructurePlan lumberjack() {
        StructurePlan p = baseHouse(Blocks.RED_BED, Blocks.ORANGE_CARPET);
        p.set(1, 1, 2, Blocks.FLETCHING_TABLE.getDefaultState());
        p.set(1, 1, 3, Blocks.GRINDSTONE.getDefaultState());
        p.set(6, 1, 2, barrel());
        p.set(6, 1, 3, barrel());

        yardGround(p, Blocks.GRASS_BLOCK.getDefaultState());
        addSmallTree(p, 2, 12, Blocks.OAK_LOG, Blocks.OAK_LEAVES);
        addSmallTree(p, 5, 12, Blocks.BIRCH_LOG, Blocks.BIRCH_LEAVES);
        p.late(2, 1, 14, Blocks.OAK_SAPLING.getDefaultState());
        p.late(4, 1, 14, Blocks.BIRCH_SAPLING.getDefaultState());
        // Поленница из ели (лесоруб её не рубит) и колода для колки дров
        BlockState pile = Blocks.SPRUCE_LOG.getDefaultState().with(Properties.AXIS, Direction.Axis.X);
        p.set(5, 1, 14, pile);
        p.set(6, 1, 14, pile);
        p.set(6, 2, 14, pile);
        p.set(1, 1, 14, Blocks.SPRUCE_LOG.getDefaultState());
        addYardFence(p);

        settleResident(p, ModEntities.LUMBERJACK);
        return p;
    }

    // =====================================================================
    // Шахта
    // =====================================================================

    /** Наклонный спуск в шахту (по ступенькам житель ходит сам) и комната внизу. */
    private static void buildMineShaft(StructurePlan p) {
        BlockState air = Blocks.AIR.getDefaultState();
        BlockState cobble = Blocks.COBBLESTONE.getDefaultState();
        BlockState planks = Blocks.SPRUCE_PLANKS.getDefaultState();
        BlockState log = Blocks.SPRUCE_LOG.getDefaultState();
        BlockState logX = log.with(Properties.AXIS, Direction.Axis.X);
        // Ступени поднимаются к входу (к игроку), поэтому «смотрят» на юг
        BlockState step = Blocks.COBBLESTONE_STAIRS.getDefaultState().with(Properties.HORIZONTAL_FACING, Direction.SOUTH);
        BlockState fence = Blocks.SPRUCE_FENCE.getDefaultState();

        for (int i = 0; i <= MINE_DEPTH; i++) {
            int z = SHAFT_Z + i;
            int floor = -i;
            for (int x = SHAFT_X; x <= SHAFT_X + 1; x++) {
                // Пол-ступенька и опора под ней
                p.set(x, floor, z, (i == 0 || i == MINE_DEPTH) ? cobble : step);
                p.fill(x, floor - 1, z, cobble);
                // Проход высотой 3
                for (int y = floor + 1; y <= floor + 3; y++) {
                    p.set(x, y, z, air);
                }
                // Потолок под землёй
                if (floor + 4 <= 0) {
                    p.set(x, floor + 4, z, planks);
                }
            }
            // Стены хода под землёй
            for (int y = floor; y <= Math.min(0, floor + 4); y++) {
                p.set(SHAFT_X - 1, y, z, cobble);
                p.set(SHAFT_X + 2, y, z, cobble);
            }
            // Ограждение над открытой частью спуска
            if (floor + 3 >= 0 && i > 0) {
                p.lateConnected(SHAFT_X - 1, 1, z, fence);
                p.lateConnected(SHAFT_X + 2, 1, z, fence);
            }
            // Факелы на стенах через каждые 3 ступеньки
            if (i % 3 == 2 && floor + 2 <= 0) {
                p.late(SHAFT_X, floor + 2, z, Blocks.WALL_TORCH.getDefaultState()
                        .with(Properties.HORIZONTAL_FACING, Direction.EAST));
            }
        }

        // Арка над входом (у начала спуска)
        for (int y = 1; y <= 3; y++) {
            p.set(SHAFT_X - 1, y, SHAFT_Z, log);
            p.set(SHAFT_X + 2, y, SHAFT_Z, log);
        }
        for (int x = SHAFT_X - 1; x <= SHAFT_X + 2; x++) {
            p.set(x, 4, SHAFT_Z, logX);
        }
        p.late(SHAFT_X - 1, 2, SHAFT_Z - 1, Blocks.WALL_TORCH.getDefaultState().with(Properties.HORIZONTAL_FACING, Direction.NORTH));
        p.late(SHAFT_X + 2, 2, SHAFT_Z - 1, Blocks.WALL_TORCH.getDefaultState().with(Properties.HORIZONTAL_FACING, Direction.NORTH));

        // Комната внизу (забой): 4×4 внутри, пол на глубине MINE_DEPTH
        int floor = -MINE_DEPTH;
        int rx = SHAFT_X - 2;              // левая стена комнаты
        int rz = SHAFT_Z + MINE_DEPTH;     // первая (передняя) стена комнаты = последняя клетка спуска
        for (int x = rx; x <= rx + 5; x++) {
            for (int z = rz; z <= rz + 5; z++) {
                boolean wall = x == rx || x == rx + 5 || z == rz + 5
                        || (z == rz && (x < SHAFT_X || x > SHAFT_X + 1));
                boolean corridor = z == rz && (x == SHAFT_X || x == SHAFT_X + 1);
                for (int y = floor; y <= floor + 4; y++) {
                    if (corridor) {
                        continue;   // это последняя клетка хода, уже построена
                    }
                    BlockState state;
                    if (y == floor) {
                        state = Blocks.STONE.getDefaultState();
                    } else if (y == floor + 4) {
                        state = planks;
                    } else if (wall) {
                        state = cobble;
                    } else {
                        state = air;
                    }
                    p.set(x, y, z, state);
                }
            }
        }
        // Разные камни в стенах для красоты
        p.set(rx + 0, floor + 2, rz + 2, Blocks.ANDESITE.getDefaultState());
        p.set(rx + 5, floor + 1, rz + 3, Blocks.GRAVEL.getDefaultState());
        p.set(rx + 5, floor + 3, rz + 1, Blocks.DIORITE.getDefaultState());
        p.set(rx + 2, floor + 2, rz + 5, Blocks.ANDESITE.getDefaultState());
        // Деревянные опоры по углам
        for (int y = floor + 1; y <= floor + 3; y++) {
            p.set(rx + 1, y, rz + 4, log);
            p.set(rx + 4, y, rz + 4, log);
        }
        // Кучи замшелого булыжника — «забой», рабочее место шахтёра
        BlockState rubble = Blocks.MOSSY_COBBLESTONE.getDefaultState();
        p.late(rx + 1, floor + 1, rz + 1, rubble);
        p.late(rx + 1, floor + 1, rz + 2, rubble);
        p.late(rx + 4, floor + 1, rz + 1, rubble);
        p.late(rx + 1, floor + 2, rz + 1, Blocks.MOSSY_COBBLESTONE_SLAB.getDefaultState());
        p.late(rx + 3, floor + 1, rz + 3, Blocks.BARREL.getDefaultState().with(Properties.FACING, Direction.UP));
        p.late(rx + 4, floor + 1, rz + 3, Blocks.BARREL.getDefaultState().with(Properties.FACING, Direction.UP));
        p.late(rx + 3, floor + 2, rz + 4, Blocks.WALL_TORCH.getDefaultState().with(Properties.HORIZONTAL_FACING, Direction.NORTH));
        p.late(rx + 4, floor + 2, rz + 2, Blocks.WALL_TORCH.getDefaultState().with(Properties.HORIZONTAL_FACING, Direction.WEST));
    }

    // =====================================================================
    // Общие части
    // =====================================================================

    /** Расчистка, фундамент, пол, стены, окна, балки, крыша и двери. */
    private static void buildShell(StructurePlan p) {
        BlockState air = Blocks.AIR.getDefaultState();
        BlockState cobble = Blocks.COBBLESTONE.getDefaultState();
        BlockState dirt = Blocks.DIRT.getDefaultState();
        BlockState floor = FLOOR.getDefaultState();
        BlockState wall = WALL.getDefaultState();
        BlockState logY = CORNER_LOG.getDefaultState();
        BlockState logX = logY.with(Properties.AXIS, Direction.Axis.X);
        BlockState logZ = logY.with(Properties.AXIS, Direction.Axis.Z);
        BlockState glass = Blocks.GLASS_PANE.getDefaultState();
        BlockState gable = GABLE.getDefaultState();
        BlockState roofEast = ROOF_STAIRS.getDefaultState().with(Properties.HORIZONTAL_FACING, Direction.EAST);
        BlockState roofWest = ROOF_STAIRS.getDefaultState().with(Properties.HORIZONTAL_FACING, Direction.WEST);

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
        p.late(1, 3, 5, Blocks.WALL_TORCH.getDefaultState().with(Properties.HORIZONTAL_FACING, Direction.EAST));
        p.late(6, 3, 5, Blocks.WALL_TORCH.getDefaultState().with(Properties.HORIZONTAL_FACING, Direction.WEST));
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
        p.late(0, 2, YARD_END, Blocks.TORCH.getDefaultState());
        p.late(W - 1, 2, YARD_END, Blocks.TORCH.getDefaultState());
    }

    /** Деревце: ствол 3 блока и шапка листвы (листва не опадает). */
    private static void addSmallTree(StructurePlan p, int x, int z, Block log, Block leaves) {
        BlockState leaf = leaves.getDefaultState().with(Properties.PERSISTENT, true);
        for (int y = 1; y <= 3; y++) {
            p.set(x, y, z, log.getDefaultState());
        }
        for (int dx = -1; dx <= 1; dx++) {
            for (int dz = -1; dz <= 1; dz++) {
                if (dx != 0 || dz != 0) {
                    p.late(x + dx, 3, z + dz, leaf);
                }
                p.late(x + dx, 4, z + dz, (dx == 0 || dz == 0) ? leaf : Blocks.AIR.getDefaultState());
            }
        }
        p.late(x, 5, z, leaf);
    }

    /** Когда дом готов — в нём появляется житель и запоминает сундук дома. */
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
            settler.setChest(task.toWorld(CHEST_X, CHEST_Y, CHEST_Z));
            settler.setPersistent();
            task.getWorld().spawnEntity(settler);
        });
    }
}

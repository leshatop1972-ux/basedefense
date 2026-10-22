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
 * Чертежи домов поселения. Обычный дом: 8×10 + двор 8×6 за ним
 * (у шахтёра свой маленький домик со спуском в шахту, см. {@link #miner()}).
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
    // Дом повара: берёза и кирпичная крыша, внутри печи; во дворе летняя кухня
    // =====================================================================
    public static StructurePlan cook() {
        StructurePlan p = new StructurePlan(new BlockPos(3, 1, -1));
        buildShell(p, new Materials(Blocks.BIRCH_PLANKS, Blocks.BIRCH_LOG, Blocks.OAK_PLANKS,
                Blocks.BRICK_STAIRS, Blocks.BRICKS));

        // Кухня: коптильня, печь, бочки, котёл с водой
        p.set(6, 1, 1, Blocks.BARREL.getDefaultState().with(Properties.FACING, Direction.UP));
        p.set(6, 1, 2, Blocks.SMOKER.getDefaultState().with(Properties.HORIZONTAL_FACING, Direction.WEST));
        p.set(6, 1, 3, Blocks.FURNACE.getDefaultState().with(Properties.HORIZONTAL_FACING, Direction.WEST));
        p.set(6, 1, 4, Blocks.WATER_CAULDRON.getDefaultState().with(LeveledCauldronBlock.LEVEL, 3));
        p.set(1, 1, 2, Blocks.CRAFTING_TABLE.getDefaultState());
        p.set(1, 1, 3, Blocks.BARREL.getDefaultState().with(Properties.FACING, Direction.UP));
        p.set(1, 1, 5, Blocks.CHEST.getDefaultState().with(Properties.HORIZONTAL_FACING, Direction.EAST));
        p.set(6, 1, 7, Blocks.BARREL.getDefaultState().with(Properties.FACING, Direction.UP));
        addTable(p);
        addBed(p, Blocks.WHITE_BED);
        p.late(6, 2, 1, Blocks.LANTERN.getDefaultState());
        p.late(6, 2, 7, Blocks.CAKE.getDefaultState());           // торт на бочке
        p.late(1, 2, 3, Blocks.POTTED_RED_MUSHROOM.getDefaultState());
        addCarpet(p, Blocks.RED_CARPET);

        // Двор: летняя кухня — костёр, бочки, тыквы и грядка с травами
        for (int x = 0; x < W; x++) {
            for (int z = D; z <= YARD_END; z++) {
                boolean path = z == D || (x == 4 && !isYardEdge(x, z));
                p.set(x, 0, z, path && !isYardEdge(x, z)
                        ? Blocks.DIRT_PATH.getDefaultState()
                        : Blocks.GRASS_BLOCK.getDefaultState());
            }
        }
        p.set(2, 1, 12, Blocks.CAMPFIRE.getDefaultState());
        p.set(1, 1, 11, Blocks.BARREL.getDefaultState().with(Properties.FACING, Direction.UP));
        p.set(1, 1, 12, Blocks.BARREL.getDefaultState().with(Properties.FACING, Direction.UP));
        p.set(6, 1, 11, Blocks.PUMPKIN.getDefaultState());
        p.set(6, 1, 12, Blocks.CARVED_PUMPKIN.getDefaultState().with(Properties.HORIZONTAL_FACING, Direction.WEST));
        p.set(6, 1, 14, Blocks.HAY_BLOCK.getDefaultState());
        p.set(2, 1, 14, Blocks.COMPOSTER.getDefaultState());
        addYardFence(p);

        settleResident(p, ModEntities.COOK);
        return p;
    }

    // =====================================================================
    // Кузница: камень и тёмный дуб, внутри наковальня и плавильня; во дворе горн
    // =====================================================================
    public static StructurePlan blacksmith() {
        StructurePlan p = new StructurePlan(new BlockPos(3, 1, -1));
        buildShell(p, new Materials(Blocks.STONE_BRICKS, Blocks.DARK_OAK_LOG, Blocks.SPRUCE_PLANKS,
                Blocks.DEEPSLATE_TILE_STAIRS, Blocks.DEEPSLATE_TILES));

        // Мастерская
        p.set(6, 1, 2, Blocks.BLAST_FURNACE.getDefaultState().with(Properties.HORIZONTAL_FACING, Direction.WEST));
        p.set(6, 1, 3, Blocks.ANVIL.getDefaultState().with(Properties.HORIZONTAL_FACING, Direction.NORTH));
        p.set(6, 1, 4, Blocks.SMITHING_TABLE.getDefaultState());
        p.set(1, 1, 2, Blocks.CRAFTING_TABLE.getDefaultState());
        p.set(1, 1, 3, Blocks.GRINDSTONE.getDefaultState());
        p.set(1, 1, 5, Blocks.CHEST.getDefaultState().with(Properties.HORIZONTAL_FACING, Direction.EAST));
        p.set(6, 1, 7, Blocks.BARREL.getDefaultState().with(Properties.FACING, Direction.UP));
        addTable(p);
        addBed(p, Blocks.GRAY_BED);
        p.late(6, 2, 7, Blocks.LANTERN.getDefaultState());
        addCarpet(p, Blocks.GRAY_CARPET);

        // Двор: горн с лавой, наковальня, бочка с водой для закалки
        for (int x = 0; x < W; x++) {
            for (int z = D; z <= YARD_END; z++) {
                p.set(x, 0, z, (z == D && !isYardEdge(x, z))
                        ? Blocks.DIRT_PATH.getDefaultState()
                        : Blocks.COBBLESTONE.getDefaultState());
            }
        }
        for (int x = 1; x <= 3; x++) {
            p.set(x, 1, 13, Blocks.BRICKS.getDefaultState());
            p.set(x, 2, 13, Blocks.BRICK_SLAB.getDefaultState());
        }
        p.set(2, 1, 13, Blocks.LAVA_CAULDRON.getDefaultState());
        p.set(5, 1, 12, Blocks.ANVIL.getDefaultState().with(Properties.HORIZONTAL_FACING, Direction.EAST));
        p.set(6, 1, 12, Blocks.WATER_CAULDRON.getDefaultState().with(LeveledCauldronBlock.LEVEL, 3));
        addYardFence(p);

        settleResident(p, ModEntities.BLACKSMITH);
        return p;
    }

    // =====================================================================
    // Домик шахтёра 6×6 и рядом спуск в шахту (лестница вниз на 9 блоков + комната)
    //   Домик:  x = 0..5, z = 0..5, дверь в (2, 1, 0).
    //   Шахта:  ход шириной 2 (x = 7..8) уходит вглубь и вниз, внизу комната с рельсами.
    // =====================================================================
    private static final int MINE_DEPTH = 9;

    public static StructurePlan miner() {
        StructurePlan p = new StructurePlan(new BlockPos(2, 1, -1));
        BlockState air = Blocks.AIR.getDefaultState();
        BlockState cobble = Blocks.COBBLESTONE.getDefaultState();
        BlockState planks = Blocks.SPRUCE_PLANKS.getDefaultState();
        BlockState log = Blocks.SPRUCE_LOG.getDefaultState();
        BlockState slab = Blocks.SPRUCE_SLAB.getDefaultState();
        BlockState glass = Blocks.GLASS_PANE.getDefaultState();

        // Расчистка места над землёй (домик и вход в шахту)
        for (int x = -1; x <= 10; x++) {
            for (int z = -1; z <= 6; z++) {
                for (int y = 1; y <= 6; y++) {
                    p.set(x, y, z, air);
                }
            }
        }
        // Фундамент и пол домика
        for (int x = 0; x <= 5; x++) {
            for (int z = 0; z <= 5; z++) {
                for (int y = -2; y <= -1; y++) {
                    p.fill(x, y, z, cobble);
                }
                boolean edge = x == 0 || x == 5 || z == 0 || z == 5;
                p.set(x, 0, z, edge ? cobble : planks);
            }
        }
        // Стены
        for (int y = 1; y <= 3; y++) {
            for (int x = 0; x <= 5; x++) {
                for (int z = 0; z <= 5; z++) {
                    boolean sideX = x == 0 || x == 5;
                    boolean sideZ = z == 0 || z == 5;
                    if (!sideX && !sideZ) {
                        continue;
                    }
                    p.set(x, y, z, (sideX && sideZ) ? log : (y == 1 ? cobble : planks));
                }
            }
        }
        // Окна (стекло ставится в конце, чтобы соединилось с соседями)
        for (int[] w : new int[][]{{0, 2, 2}, {0, 2, 3}, {5, 2, 2}, {5, 2, 3}, {2, 2, 5}, {3, 2, 5}, {4, 2, 0}}) {
            p.set(w[0], w[1], w[2], air);
            p.lateConnected(w[0], w[1], w[2], glass);
        }
        // Дверной проём
        p.set(2, 1, 0, air);
        p.set(2, 2, 0, air);
        // Крыша: плоская из досок с бортиком из полублоков
        for (int x = -1; x <= 6; x++) {
            for (int z = -1; z <= 6; z++) {
                boolean inside = x >= 0 && x <= 5 && z >= 0 && z <= 5;
                p.set(x, 4, z, inside ? planks : slab);
            }
        }
        for (int x = 1; x <= 4; x++) {
            for (int z = 1; z <= 4; z++) {
                p.set(x, 5, z, slab);
            }
        }
        // Внутри: кровать, верстак, сундук, печь, бочка
        BlockState bed = Blocks.BROWN_BED.getDefaultState().with(Properties.HORIZONTAL_FACING, Direction.NORTH);
        p.lateDouble(1, 1, 3, bed.with(Properties.BED_PART, BedPart.FOOT),
                1, 1, 4, bed.with(Properties.BED_PART, BedPart.HEAD));
        p.set(4, 1, 1, Blocks.CRAFTING_TABLE.getDefaultState());
        p.set(4, 1, 2, Blocks.FURNACE.getDefaultState().with(Properties.HORIZONTAL_FACING, Direction.WEST));
        p.set(4, 1, 4, Blocks.CHEST.getDefaultState().with(Properties.HORIZONTAL_FACING, Direction.WEST));
        p.set(1, 1, 1, Blocks.BARREL.getDefaultState().with(Properties.FACING, Direction.UP));
        p.late(1, 2, 1, Blocks.LANTERN.getDefaultState());
        BlockState door = Blocks.SPRUCE_DOOR.getDefaultState().with(Properties.HORIZONTAL_FACING, Direction.NORTH);
        p.lateDouble(2, 1, 0, door.with(Properties.DOUBLE_BLOCK_HALF, DoubleBlockHalf.LOWER),
                2, 2, 0, door.with(Properties.DOUBLE_BLOCK_HALF, DoubleBlockHalf.UPPER));
        p.late(1, 3, -1, Blocks.WALL_TORCH.getDefaultState().with(Properties.HORIZONTAL_FACING, Direction.SOUTH));

        buildMineShaft(p);

        settleResident(p, ModEntities.MINER, 2, 1, 2);
        return p;
    }

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
            int z = 1 + i;
            int floor = -i;
            for (int x = 7; x <= 8; x++) {
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
                p.set(6, y, z, cobble);
                p.set(9, y, z, cobble);
            }
            // Ограждение над открытой частью спуска
            if (floor + 3 >= 0) {
                p.lateConnected(6, 1, z, fence);
                p.lateConnected(9, 1, z, fence);
            }
            // Факелы на стенах через каждые 3 ступеньки
            if (i % 3 == 2 && floor + 2 <= 0) {
                p.late(7, floor + 2, z, Blocks.WALL_TORCH.getDefaultState()
                        .with(Properties.HORIZONTAL_FACING, Direction.EAST));
            }
        }

        // Арка над входом
        for (int y = 1; y <= 3; y++) {
            p.set(6, y, 0, log);
            p.set(9, y, 0, log);
        }
        for (int x = 6; x <= 9; x++) {
            p.set(x, 4, 0, logX);
        }
        p.set(7, 0, 0, cobble);
        p.set(8, 0, 0, cobble);
        p.late(7, 3, 0, Blocks.LANTERN.getDefaultState().with(Properties.HANGING, true));

        // Комната внизу: x = 6..9, z = 11..14, пол на глубине MINE_DEPTH
        int floor = -MINE_DEPTH;
        for (int x = 5; x <= 10; x++) {
            for (int z = 10; z <= 15; z++) {
                boolean wall = x == 5 || x == 10 || z == 15 || (z == 10 && (x < 7 || x > 8));
                boolean corridor = z == 10 && (x == 7 || x == 8);
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
        // Руда в стенах для красоты
        p.set(5, floor + 2, 12, Blocks.IRON_ORE.getDefaultState());
        p.set(10, floor + 1, 13, Blocks.COAL_ORE.getDefaultState());
        p.set(10, floor + 3, 11, Blocks.COPPER_ORE.getDefaultState());
        p.set(7, floor + 2, 15, Blocks.GOLD_ORE.getDefaultState());
        // Деревянные опоры по углам
        for (int y = floor + 1; y <= floor + 3; y++) {
            p.set(6, y, 14, log);
            p.set(9, y, 14, log);
        }
        // Рельсы — рабочее место шахтёра
        BlockState rail = Blocks.RAIL.getDefaultState();
        for (int z = 11; z <= 13; z++) {
            p.late(7, floor + 1, z, rail);
        }
        p.late(8, floor + 1, 13, Blocks.BARREL.getDefaultState().with(Properties.FACING, Direction.UP));
        p.late(8, floor + 1, 11, Blocks.CHEST.getDefaultState().with(Properties.HORIZONTAL_FACING, Direction.WEST));
        p.late(7, floor + 3, 14, Blocks.LANTERN.getDefaultState().with(Properties.HANGING, true));
        p.late(9, floor + 2, 12, Blocks.WALL_TORCH.getDefaultState().with(Properties.HORIZONTAL_FACING, Direction.WEST));
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
        settleResident(p, type, 3, 1, 4);
    }

    /** То же, но житель появляется в точке (x, y, z) чертежа. */
    private static void settleResident(StructurePlan p, EntityType<? extends SettlerEntity> type,
                                       int sx, int sy, int sz) {
        p.onFinish(task -> {
            SettlerEntity settler = type.create(task.getWorld());
            if (settler == null) {
                return;
            }
            BlockPos spawn = task.toWorld(sx, sy, sz);
            settler.refreshPositionAndAngles(spawn.getX() + 0.5, spawn.getY(), spawn.getZ() + 0.5,
                    task.getFacing().getOpposite().asRotation(), 0.0f);
            settler.setHome(spawn);
            settler.setPersistent();
            task.getWorld().spawnEntity(settler);
        });
    }
}

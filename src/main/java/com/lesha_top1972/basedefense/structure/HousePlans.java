package com.lesha_top1972.basedefense.structure;

import com.lesha_top1972.basedefense.entity.ModEntities;
import com.lesha_top1972.basedefense.entity.SettlerEntity;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.block.enums.BedPart;
import net.minecraft.block.enums.ChestType;
import net.minecraft.block.enums.DoubleBlockHalf;
import net.minecraft.block.enums.SlabType;
import net.minecraft.block.enums.WallMountLocation;
import net.minecraft.entity.EntityType;
import net.minecraft.state.property.Properties;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import org.jetbrains.annotations.Nullable;

/**
 * Чертежи домов поселения. Все дома в ОДНОМ стиле (одни и те же материалы:
 * дубовые доски, еловые брёвна-углы и балки, булыжный цоколь, еловая крыша, стеклянные панели),
 * но разных размеров и форм: ширина, глубина, высота стен, направление конька крыши,
 * у повара и кузнеца — дымоход. За каждым домом свой двор (огород, загон, шахта…).
 *
 * Баланс: в домах нет дорогих блоков и нет блоков из рецептов домов
 * (верстак, каменотёс, компостер, котёл, коптильня, плавильная печь, стол кузнеца).
 *
 * Локальные координаты (игрок смотрит «на север»):
 *   x = 0..w-1 — ширина дома (слева направо), z = 0..d-1 — дом (z = 0 — передняя стена с дверью),
 *   дальше по z — двор; y = 0 — пол (уровень земли), y = 1..h — стены.
 *   Направления блоков в локальных координатах: NORTH = +z (вглубь), SOUTH = -z, EAST = +x, WEST = -x.
 * Поставленный блок дома — это крыльцо перед дверью: (doorX, 1, -1).
 */
public final class HousePlans {

    // ---------- Единые материалы ----------
    private static final BlockState AIR = Blocks.AIR.getDefaultState();
    private static final BlockState COBBLE = Blocks.COBBLESTONE.getDefaultState();
    private static final BlockState DIRT = Blocks.DIRT.getDefaultState();
    private static final BlockState WALL = Blocks.OAK_PLANKS.getDefaultState();
    private static final BlockState FLOOR = Blocks.SPRUCE_PLANKS.getDefaultState();
    private static final BlockState LOG = Blocks.SPRUCE_LOG.getDefaultState();   // ель: лесоруб её не рубит
    private static final BlockState GABLE = Blocks.SPRUCE_PLANKS.getDefaultState();
    private static final Block ROOF = Blocks.SPRUCE_STAIRS;
    private static final BlockState GLASS = Blocks.GLASS_PANE.getDefaultState();
    private static final BlockState FENCE = Blocks.OAK_FENCE.getDefaultState();

    private HousePlans() {
    }

    // =====================================================================
    // Дом строителя: самый большой, 6 сундуков-склад; двор со стройматериалами
    // (строится сам, без ресурсов — ведь строителя ещё нет)
    // =====================================================================
    public static StructurePlan builder() {
        House h = new House(10, 8, 4, false, 6);
        StructurePlan p = h.p;
        // Склад: три двойных сундука
        doubleChest(p, 2, 1, 6, 1, 1, 6, Direction.SOUTH);   // у задней стены слева
        doubleChest(p, 8, 1, 6, 7, 1, 6, Direction.SOUTH);   // у задней стены справа
        doubleChest(p, 1, 1, 4, 1, 1, 3, Direction.EAST);    // у левой стены
        bed(p, 8, 1, Blocks.YELLOW_BED);
        table(p, 6, 3);
        p.set(8, 1, 4, barrel());
        p.set(8, 1, 5, Blocks.CARTOGRAPHY_TABLE.getDefaultState());
        carpet(p, 3, 2, 4, 4, Blocks.YELLOW_CARPET);
        p.late(8, 2, 4, Blocks.TORCH.getDefaultState());

        // Двор: стройматериалы
        h.yardGround(Blocks.DIRT_PATH.getDefaultState());
        BlockState pileX = LOG.with(Properties.AXIS, Direction.Axis.X);
        for (int x = 1; x <= 3; x++) {
            p.set(x, 1, h.yardStart + 2, pileX);
            p.set(x, 1, h.yardStart + 3, pileX);
            p.set(x, 2, h.yardStart + 2, pileX);
        }
        p.set(7, 1, h.yardStart + 2, COBBLE);
        p.set(8, 1, h.yardStart + 2, COBBLE);
        p.set(8, 1, h.yardStart + 3, COBBLE);
        p.set(8, 2, h.yardStart + 2, Blocks.COBBLESTONE_SLAB.getDefaultState());
        // «Козлы» для распилки: заборы + доска-полублок
        p.set(5, 1, h.yardStart + 3, FENCE);
        p.set(6, 1, h.yardStart + 3, FENCE);
        p.late(5, 2, h.yardStart + 3, Blocks.OAK_SLAB.getDefaultState());
        p.late(6, 2, h.yardStart + 3, Blocks.OAK_SLAB.getDefaultState());
        p.set(1, 1, h.yardEnd - 1, barrel());
        h.yardFence();

        h.settle(ModEntities.BUILDER, null);
        return p;
    }

    // =====================================================================
    // Фермер: маленький дом 8×6, конёк поперёк; огород во дворе
    // =====================================================================
    public static StructurePlan farmer() {
        House h = new House(8, 6, 3, true, 8);
        StructurePlan p = h.p;
        bed(p, 1, 3, Blocks.GREEN_BED);
        chest(p, 6, 1, 4, Direction.WEST);
        p.set(1, 1, 1, FENCE);                       // столик
        p.late(1, 2, 1, Blocks.POTTED_POPPY.getDefaultState());
        p.set(2, 1, 1, stairs(Direction.EAST));      // стул
        p.set(6, 1, 1, barrel());
        p.set(6, 1, 2, Blocks.HAY_BLOCK.getDefaultState());
        p.set(6, 1, 3, Blocks.FLETCHING_TABLE.getDefaultState());
        carpet(p, 2, 2, 4, 3, Blocks.GREEN_CARPET);

        // Огород: пашня и канавка с водой (пашня — рабочее место фермера)
        h.yardGround(Blocks.FARMLAND.getDefaultState().with(Properties.MOISTURE, 7));
        for (int x = 1; x < h.w - 1; x++) {
            p.set(x, 0, h.yardStart + 3, Blocks.WATER.getDefaultState());
        }
        h.yardFence();

        h.settle(ModEntities.FARMER, h.chestPos(6, 1, 4));
        return p;
    }

    // =====================================================================
    // Скотовод: длинный дом-«амбар» 8×10; загон с сеном (рабочее место) и поилкой
    // =====================================================================
    public static StructurePlan rancher() {
        House h = new House(8, 10, 3, false, 8);
        StructurePlan p = h.p;
        bed(p, 1, 7, Blocks.BROWN_BED);
        chest(p, 6, 1, 8, Direction.WEST);
        table(p, 5, 4);
        p.set(1, 1, 1, barrel());
        p.set(1, 1, 2, barrel());
        p.set(6, 1, 1, barrel());
        p.set(6, 1, 2, Blocks.HAY_BLOCK.getDefaultState());
        p.set(6, 2, 2, Blocks.HAY_BLOCK.getDefaultState());
        carpet(p, 2, 3, 3, 6, Blocks.BROWN_CARPET);

        h.yardGround(Blocks.GRASS_BLOCK.getDefaultState());
        int far = h.yardEnd - 1;
        p.set(1, 1, far, Blocks.HAY_BLOCK.getDefaultState());
        p.set(2, 1, far, Blocks.HAY_BLOCK.getDefaultState());
        p.set(1, 2, far, Blocks.HAY_BLOCK.getDefaultState());
        p.set(1, 1, far - 1, Blocks.HAY_BLOCK.getDefaultState());
        p.set(6, 0, h.yardStart + 3, Blocks.WATER.getDefaultState());   // поилка — вода в земле
        p.set(6, 0, h.yardStart + 4, Blocks.WATER.getDefaultState());
        h.yardFence();

        h.settle(ModEntities.RANCHER, h.chestPos(6, 1, 8));
        return p;
    }

    // =====================================================================
    // Повар: высокий дом 8×8, конёк поперёк, кирпичная печь с дымоходом
    // =====================================================================
    public static StructurePlan cook() {
        House h = new House(8, 8, 4, true, 6);
        StructurePlan p = h.p;
        // Печь (рабочее место повара — кирпичи) и дымоход сквозь крышу
        p.set(6, 1, 5, Blocks.BRICKS.getDefaultState());
        for (int y = 1; y <= h.roofTop + 1; y++) {
            p.set(6, y, 6, Blocks.BRICKS.getDefaultState());
        }
        p.set(5, 1, 6, Blocks.FURNACE.getDefaultState().with(Properties.HORIZONTAL_FACING, Direction.SOUTH));
        p.set(6, 1, 1, Blocks.SMOOTH_STONE.getDefaultState());   // разделочный стол
        p.set(6, 1, 2, Blocks.SMOOTH_STONE.getDefaultState());
        p.set(6, 1, 3, barrel());
        p.late(6, 2, 1, Blocks.POTTED_RED_MUSHROOM.getDefaultState());
        bed(p, 1, 5, Blocks.WHITE_BED);
        chest(p, 1, 1, 3, Direction.EAST);
        p.set(4, 1, 4, FENCE);
        p.late(4, 2, 4, Blocks.OAK_PRESSURE_PLATE.getDefaultState());
        p.set(5, 1, 4, stairs(Direction.EAST));
        carpet(p, 2, 1, 2, 2, Blocks.RED_CARPET);

        // Летняя кухня во дворе
        h.yardGround(Blocks.GRASS_BLOCK.getDefaultState());
        for (int z = h.yardStart + 1; z < h.yardEnd; z++) {
            p.set(h.backX, 0, z, Blocks.DIRT_PATH.getDefaultState());
        }
        p.set(2, 1, h.yardStart + 2, Blocks.BRICKS.getDefaultState());   // уличная печь
        p.set(2, 2, h.yardStart + 2, Blocks.BRICK_SLAB.getDefaultState());
        p.set(1, 1, h.yardStart + 1, barrel());
        p.set(1, 1, h.yardStart + 2, barrel());
        p.set(6, 1, h.yardStart + 1, Blocks.PUMPKIN.getDefaultState());
        p.set(6, 1, h.yardStart + 2, Blocks.CARVED_PUMPKIN.getDefaultState()
                .with(Properties.HORIZONTAL_FACING, Direction.WEST));
        p.set(6, 1, h.yardEnd - 1, Blocks.HAY_BLOCK.getDefaultState());
        h.yardFence();

        h.settle(ModEntities.COOK, h.chestPos(1, 1, 3));
        return p;
    }

    // =====================================================================
    // Шахтёр: маленький домик 6×6; во дворе вход в шахту, забой глубоко внизу
    // =====================================================================
    private static final int MINE_DEPTH = 9;

    public static StructurePlan miner() {
        House h = new House(6, 6, 3, false, 6);
        StructurePlan p = h.p;
        bed(p, 1, 3, Blocks.BLACK_BED);
        chest(p, 4, 1, 4, Direction.WEST);
        p.set(4, 1, 1, Blocks.CARTOGRAPHY_TABLE.getDefaultState());
        p.set(4, 1, 2, barrel());
        p.late(4, 2, 2, Blocks.TORCH.getDefaultState());

        h.yardGround(Blocks.DIRT_PATH.getDefaultState());
        h.yardFence();
        // Спуск начинается у задней двери: ход x = 2..3
        buildMineShaft(p, 2, h.yardStart + 1);

        h.settle(ModEntities.MINER, h.chestPos(4, 1, 4));
        return p;
    }

    // =====================================================================
    // Лесоруб: узкий дом 6×8; длинный двор с деревьями (брёвна — рабочее место)
    // =====================================================================
    public static StructurePlan lumberjack() {
        House h = new House(6, 8, 3, false, 9);
        StructurePlan p = h.p;
        bed(p, 1, 5, Blocks.RED_BED);
        chest(p, 4, 1, 6, Direction.WEST);
        p.set(4, 1, 1, Blocks.GRINDSTONE.getDefaultState()
                .with(Properties.WALL_MOUNT_LOCATION, WallMountLocation.FLOOR));
        p.set(4, 1, 2, Blocks.FLETCHING_TABLE.getDefaultState());
        p.set(1, 1, 1, barrel());
        carpet(p, 2, 2, 3, 4, Blocks.ORANGE_CARPET);

        h.yardGround(Blocks.GRASS_BLOCK.getDefaultState());
        int z0 = h.yardStart;
        smallTree(p, 2, z0 + 2, Blocks.OAK_LOG, Blocks.OAK_LEAVES);
        smallTree(p, 3, z0 + 6, Blocks.BIRCH_LOG, Blocks.BIRCH_LEAVES);
        p.late(4, 1, z0 + 2, Blocks.BIRCH_SAPLING.getDefaultState());
        p.late(1, 1, z0 + 5, Blocks.OAK_SAPLING.getDefaultState());
        // Поленница из ели (лесоруб её не рубит) и колода
        BlockState pileX = LOG.with(Properties.AXIS, Direction.Axis.X);
        p.set(1, 1, z0 + 7, pileX);
        p.set(1, 2, z0 + 7, pileX);
        p.set(1, 1, z0 + 6, pileX);
        p.set(4, 1, z0 + 4, LOG);
        h.yardFence();

        h.settle(ModEntities.LUMBERJACK, h.chestPos(4, 1, 6));
        return p;
    }

    // =====================================================================
    // Кузнец: широкий низкий дом 10×8, конёк поперёк, каменный дымоход над горном
    // =====================================================================
    public static StructurePlan blacksmith() {
        House h = new House(10, 8, 3, true, 6);
        StructurePlan p = h.p;
        // Горн в углу и дымоход сквозь крышу
        p.set(8, 1, 6, Blocks.MAGMA_BLOCK.getDefaultState());
        for (int y = 2; y <= h.roofTop + 1; y++) {
            p.set(8, y, 6, Blocks.STONE_BRICKS.getDefaultState());
        }
        p.set(7, 1, 6, Blocks.STONE_BRICKS.getDefaultState());
        p.set(8, 1, 5, Blocks.STONE_BRICKS.getDefaultState());
        p.set(1, 1, 1, Blocks.GRINDSTONE.getDefaultState()
                .with(Properties.WALL_MOUNT_LOCATION, WallMountLocation.FLOOR));
        p.set(2, 1, 1, Blocks.POLISHED_ANDESITE.getDefaultState());
        p.set(1, 1, 2, Blocks.SMOOTH_STONE.getDefaultState());
        bed(p, 1, 5, Blocks.GRAY_BED);
        chest(p, 8, 1, 2, Direction.WEST);
        p.set(8, 1, 3, barrel());
        table(p, 6, 3);
        carpet(p, 3, 4, 5, 5, Blocks.GRAY_CARPET);

        // Двор: уличный горн и бочка с водой для закалки
        h.yardGround(Blocks.COBBLESTONE.getDefaultState());
        int z = h.yardStart + 3;
        for (int x = 1; x <= 3; x++) {
            p.set(x, 1, z, Blocks.STONE_BRICKS.getDefaultState());
            p.set(x, 2, z, Blocks.STONE_BRICK_SLAB.getDefaultState());
        }
        p.set(2, 1, z, Blocks.MAGMA_BLOCK.getDefaultState());
        p.set(7, 1, z - 1, Blocks.POLISHED_ANDESITE.getDefaultState());
        p.set(8, 1, z - 1, barrel());
        h.yardFence();

        h.settle(ModEntities.BLACKSMITH, h.chestPos(8, 1, 2));
        return p;
    }

    // =====================================================================
    // Коробка дома
    // =====================================================================

    /** Один дом: коробка, крыша, двор. Создаётся с размерами, мебель ставится отдельно. */
    private static final class House {
        final StructurePlan p;
        final int w, d, h;           // ширина, глубина, высота стен
        final boolean ridgeAlongX;   // конёк поперёк дома (скаты на перед и зад)
        final int doorX, backX;      // передняя и задняя двери
        final int beamY, roofTop;
        final int yardStart, yardEnd;

        House(int w, int d, int h, boolean ridgeAlongX, int yardDepth) {
            this.w = w;
            this.d = d;
            this.h = h;
            this.ridgeAlongX = ridgeAlongX;
            this.doorX = w / 2 - 1;
            this.backX = w / 2;
            this.beamY = h + 1;
            int span = ridgeAlongX ? d : w;
            this.roofTop = beamY + span / 2;
            this.yardStart = d;
            this.yardEnd = d + yardDepth - 1;
            this.p = new StructurePlan(new BlockPos(doorX, 1, -1));
            build();
        }

        BlockPos chestPos(int x, int y, int z) {
            return new BlockPos(x, y, z);
        }

        private void build() {
            // Расчистка места над землёй
            for (int x = -1; x <= w; x++) {
                for (int z = -1; z <= yardEnd; z++) {
                    for (int y = 1; y <= roofTop + 2; y++) {
                        p.set(x, y, z, AIR);
                    }
                }
            }
            // Фундамент: засыпаем ямы под домом и двором (бесплатно)
            for (int x = 0; x < w; x++) {
                for (int z = 0; z <= yardEnd; z++) {
                    for (int y = -3; y <= -1; y++) {
                        p.fill(x, y, z, z < d ? COBBLE : DIRT);
                    }
                }
            }
            // Пол
            for (int x = 0; x < w; x++) {
                for (int z = 0; z < d; z++) {
                    boolean edge = x == 0 || x == w - 1 || z == 0 || z == d - 1;
                    p.set(x, 0, z, edge ? COBBLE : FLOOR);
                }
            }
            p.set(doorX, 0, -1, Blocks.DIRT_PATH.getDefaultState());

            // Стены: нижний ряд — булыжник, углы — брёвна
            for (int y = 1; y <= h; y++) {
                for (int x = 0; x < w; x++) {
                    for (int z = 0; z < d; z++) {
                        boolean sideX = x == 0 || x == w - 1;
                        boolean sideZ = z == 0 || z == d - 1;
                        if (sideX || sideZ) {
                            p.set(x, y, z, (sideX && sideZ) ? LOG : (y == 1 ? COBBLE : WALL));
                        }
                    }
                }
            }
            // Окна: на боковых стенах парами, спереди и сзади — у углов
            for (int y = 2; y <= h - 1; y++) {
                for (int z = 2; z <= d - 3; z++) {
                    if ((z - 2) % 3 != 2) {
                        window(0, y, z);
                        window(w - 1, y, z);
                    }
                }
                for (int x : new int[]{1, w - 2}) {
                    window(x, y, 0);
                    window(x, y, d - 1);
                }
            }
            // Проёмы под двери
            p.set(doorX, 1, 0, AIR);
            p.set(doorX, 2, 0, AIR);
            p.set(backX, 1, d - 1, AIR);
            p.set(backX, 2, d - 1, AIR);

            // Балки поверх стен
            BlockState logX = LOG.with(Properties.AXIS, Direction.Axis.X);
            BlockState logZ = LOG.with(Properties.AXIS, Direction.Axis.Z);
            for (int z = 0; z < d; z++) {
                p.set(0, beamY, z, logZ);
                p.set(w - 1, beamY, z, logZ);
            }
            for (int x = 1; x < w - 1; x++) {
                p.set(x, beamY, 0, logX);
                p.set(x, beamY, d - 1, logX);
            }
            buildRoof();

            // Двери и факелы
            BlockState door = Blocks.OAK_DOOR.getDefaultState().with(Properties.HORIZONTAL_FACING, Direction.NORTH);
            p.lateDouble(doorX, 1, 0, door.with(Properties.DOUBLE_BLOCK_HALF, DoubleBlockHalf.LOWER),
                    doorX, 2, 0, door.with(Properties.DOUBLE_BLOCK_HALF, DoubleBlockHalf.UPPER));
            p.lateDouble(backX, 1, d - 1, door.with(Properties.DOUBLE_BLOCK_HALF, DoubleBlockHalf.LOWER),
                    backX, 2, d - 1, door.with(Properties.DOUBLE_BLOCK_HALF, DoubleBlockHalf.UPPER));
            p.late(1, h, d / 2, wallTorch(Direction.EAST));
            p.late(w - 2, h, d / 2, wallTorch(Direction.WEST));
            p.late(doorX - 1, h, -1, wallTorch(Direction.SOUTH));
            p.late(doorX + 1, h, -1, wallTorch(Direction.SOUTH));
        }

        /** Двускатная крыша со свесами, фронтонами и окошком на чердаке. */
        private void buildRoof() {
            int span = ridgeAlongX ? d : w;
            for (int r = 0; r <= span / 2; r++) {
                int y = beamY + r;
                if (ridgeAlongX) {
                    // Скаты на перед и на зад, фронтоны на боковых стенах
                    BlockState front = ROOF.getDefaultState().with(Properties.HORIZONTAL_FACING, Direction.NORTH);
                    BlockState back = ROOF.getDefaultState().with(Properties.HORIZONTAL_FACING, Direction.SOUTH);
                    for (int x = -1; x <= w; x++) {
                        p.set(x, y, -1 + r, front);
                        p.set(x, y, d - r, back);
                    }
                    if (r >= 1 && r < span / 2) {
                        for (int z = r; z <= d - 1 - r; z++) {
                            p.set(0, y, z, GABLE);
                            p.set(w - 1, y, z, GABLE);
                        }
                    }
                } else {
                    // Скаты влево и вправо, фронтоны спереди и сзади
                    BlockState left = ROOF.getDefaultState().with(Properties.HORIZONTAL_FACING, Direction.EAST);
                    BlockState right = ROOF.getDefaultState().with(Properties.HORIZONTAL_FACING, Direction.WEST);
                    for (int z = -1; z <= d; z++) {
                        p.set(-1 + r, y, z, left);
                        p.set(w - r, y, z, right);
                    }
                    if (r >= 1 && r < span / 2) {
                        for (int x = r; x <= w - 1 - r; x++) {
                            p.set(x, y, 0, GABLE);
                            p.set(x, y, d - 1, GABLE);
                        }
                    }
                }
            }
            // Окошко на чердаке (если фронтон достаточно высокий)
            if (span / 2 - 1 >= 2) {
                int y = beamY + 2;
                if (ridgeAlongX) {
                    int z = d / 2 - 1;
                    window(0, y, z);
                    window(0, y, z + 1);
                    window(w - 1, y, z);
                    window(w - 1, y, z + 1);
                } else {
                    int x = w / 2 - 1;
                    window(x, y, 0);
                    window(x + 1, y, 0);
                    window(x, y, d - 1);
                    window(x + 1, y, d - 1);
                }
            }
        }

        /** Стекло ставится в конце, чтобы соединиться с соседями. */
        private void window(int x, int y, int z) {
            p.set(x, y, z, AIR);
            p.lateConnected(x, y, z, GLASS);
        }

        /** Земля двора: трава по краю, дорожка у задней двери, внутри — заданный блок. */
        void yardGround(BlockState inner) {
            for (int x = 0; x < w; x++) {
                for (int z = yardStart; z <= yardEnd; z++) {
                    BlockState ground;
                    if (isYardEdge(x, z)) {
                        ground = Blocks.GRASS_BLOCK.getDefaultState();
                    } else if (z == yardStart) {
                        ground = Blocks.DIRT_PATH.getDefaultState();
                    } else {
                        ground = inner;
                    }
                    p.set(x, 0, z, ground);
                }
            }
        }

        boolean isYardEdge(int x, int z) {
            return x == 0 || x == w - 1 || z == yardEnd;
        }

        /** Забор вокруг двора с калиткой напротив задней двери и факелами на углах. */
        void yardFence() {
            for (int z = yardStart; z <= yardEnd; z++) {
                p.lateConnected(0, 1, z, FENCE);
                p.lateConnected(w - 1, 1, z, FENCE);
            }
            for (int x = 1; x < w - 1; x++) {
                p.lateConnected(x, 1, yardEnd, x == backX
                        ? Blocks.OAK_FENCE_GATE.getDefaultState().with(Properties.HORIZONTAL_FACING, Direction.NORTH)
                        : FENCE);
            }
            p.late(0, 2, yardEnd, Blocks.TORCH.getDefaultState());
            p.late(w - 1, 2, yardEnd, Blocks.TORCH.getDefaultState());
        }

        /** Когда дом готов — в нём появляется житель и запоминает сундук дома. */
        void settle(EntityType<? extends SettlerEntity> type, @Nullable BlockPos chestLocal) {
            int sx = doorX;
            int sz = 2;
            int dx = doorX;
            p.onFinish(task -> {
                // Запоминаем вход в дом — к нему строитель проложит тропинку
                TownState.get(task.getWorld().getServer()).addEntrance(task.toWorld(dx, 0, -1),
                        task.getFacing().getOpposite(), dimensionOf(task));
                SettlerEntity settler = type.create(task.getWorld());
                if (settler == null) {
                    return;
                }
                BlockPos spawn = task.toWorld(sx, 1, sz);
                settler.refreshPositionAndAngles(spawn.getX() + 0.5, spawn.getY(), spawn.getZ() + 0.5,
                        task.getFacing().getOpposite().asRotation(), 0.0f);
                settler.setHome(spawn);
                if (chestLocal != null) {
                    settler.setChest(task.toWorld(chestLocal.getX(), chestLocal.getY(), chestLocal.getZ()));
                }
                settler.setPersistent();
                task.getWorld().spawnEntity(settler);
            });
        }
    }

    private static String dimensionOf(BuildTask task) {
        return task.getWorld().getRegistryKey().getValue().toString();
    }

    // =====================================================================
    // Центральная площадь 11×11: мощение кольцами, звонница с колоколом,
    // скамейки, фонари и клумбы по углам. Жителя нет — сюда ходят все жители.
    // =====================================================================
    public static final int SQUARE_RADIUS = 5;

    public static StructurePlan townSquare() {
        int c = SQUARE_RADIUS;            // центр площади
        int size = 2 * c + 1;
        StructurePlan p = new StructurePlan(new BlockPos(c, 1, -1));
        // Расчистка и засыпка
        for (int x = -1; x <= size; x++) {
            for (int z = -1; z <= size; z++) {
                for (int y = 1; y <= 6; y++) {
                    p.set(x, y, z, AIR);
                }
            }
        }
        for (int x = 0; x < size; x++) {
            for (int z = 0; z < size; z++) {
                for (int y = -2; y <= -1; y++) {
                    p.fill(x, y, z, DIRT);
                }
                // Мощение кольцами от центра
                int ring = Math.max(Math.abs(x - c), Math.abs(z - c));
                BlockState ground = switch (ring) {
                    case 0, 1 -> Blocks.STONE_BRICKS.getDefaultState();
                    case 2 -> Blocks.POLISHED_ANDESITE.getDefaultState();
                    case 4 -> COBBLE;
                    default -> Blocks.DIRT_PATH.getDefaultState();
                };
                p.set(x, 0, z, ground);
            }
        }
        // Клумбы в углах: трава и цветы
        int[][] corners = {{0, 0}, {size - 1, 0}, {0, size - 1}, {size - 1, size - 1}};
        for (int i = 0; i < corners.length; i++) {
            int cx = corners[i][0];
            int cz = corners[i][1];
            int sx = cx == 0 ? 1 : -1;
            int sz = cz == 0 ? 1 : -1;
            p.set(cx, 0, cz, Blocks.GRASS_BLOCK.getDefaultState());
            p.set(cx + sx, 0, cz, Blocks.GRASS_BLOCK.getDefaultState());
            p.set(cx, 0, cz + sz, Blocks.GRASS_BLOCK.getDefaultState());
            BlockState flower = (i % 2 == 0 ? Blocks.POPPY : Blocks.DANDELION).getDefaultState();
            p.late(cx, 1, cz, flower);
            p.late(cx + sx, 1, cz, (i % 2 == 0 ? Blocks.DANDELION : Blocks.POPPY).getDefaultState());
            p.late(cx, 1, cz + sz, Blocks.AZURE_BLUET.getDefaultState());
        }
        // Звонница: два столба, перекладина и колокол
        BlockState logX = LOG.with(Properties.AXIS, Direction.Axis.X);
        for (int y = 1; y <= 3; y++) {
            p.set(c - 1, y, c, LOG);
            p.set(c + 1, y, c, LOG);
        }
        for (int x = c - 1; x <= c + 1; x++) {
            p.set(x, 4, c, logX);
        }
        p.set(c, 5, c, Blocks.SPRUCE_SLAB.getDefaultState());
        p.late(c, 3, c, Blocks.BELL.getDefaultState()
                .with(Properties.ATTACHMENT, net.minecraft.block.enums.Attachment.CEILING)
                .with(Properties.HORIZONTAL_FACING, Direction.NORTH));
        // Скамейки лицом к колоколу (проходы по центру каждой стороны свободны)
        for (int d : new int[]{-1, 1}) {
            p.set(c - 3, 1, c + d, stairs(Direction.WEST));
            p.set(c + 3, 1, c + d, stairs(Direction.EAST));
            p.set(c + d, 1, c + 3, stairs(Direction.NORTH));
            p.set(c + d, 1, c - 3, stairs(Direction.SOUTH));
        }
        // Фонари на столбах
        for (int[] lp : new int[][]{{1, 1}, {size - 2, 1}, {1, size - 2}, {size - 2, size - 2}}) {
            p.set(lp[0], 1, lp[1], FENCE);
            p.set(lp[0], 2, lp[1], FENCE);
            p.late(lp[0], 3, lp[1], Blocks.LANTERN.getDefaultState());
        }
        // Готово — запоминаем площадь
        p.onFinish(task -> TownState.get(task.getWorld().getServer()).setSquare(
                task.toWorld(c, 0, c), task.toWorld(c, 3, c), SQUARE_RADIUS, dimensionOf(task)));
        return p;
    }

    // =====================================================================
    // Мебель
    // =====================================================================

    private static BlockState barrel() {
        return Blocks.BARREL.getDefaultState().with(Properties.FACING, Direction.UP);
    }

    private static BlockState stairs(Direction facing) {
        return Blocks.OAK_STAIRS.getDefaultState().with(Properties.HORIZONTAL_FACING, facing);
    }

    private static BlockState wallTorch(Direction facing) {
        return Blocks.WALL_TORCH.getDefaultState().with(Properties.HORIZONTAL_FACING, facing);
    }

    /** Сундук; facing — куда смотрит его крышка-передняя сторона. */
    private static void chest(StructurePlan p, int x, int y, int z, Direction facing) {
        p.set(x, y, z, Blocks.CHEST.getDefaultState().with(Properties.HORIZONTAL_FACING, facing));
    }

    /** Двойной сундук: половина LEFT в (x1, z1), RIGHT в (x2, z2) — она стоит от LEFT по часовой стрелке от facing. */
    private static void doubleChest(StructurePlan p, int x1, int y, int z1, int x2, int y2, int z2, Direction facing) {
        BlockState chest = Blocks.CHEST.getDefaultState().with(Properties.HORIZONTAL_FACING, facing);
        p.lateDouble(x1, y, z1, chest.with(Properties.CHEST_TYPE, ChestType.LEFT),
                x2, y2, z2, chest.with(Properties.CHEST_TYPE, ChestType.RIGHT));
    }

    /** Кровать изголовьем к задней стене: ножки (x, z), изголовье (x, z + 1). */
    private static void bed(StructurePlan p, int x, int z, Block bedBlock) {
        BlockState bed = bedBlock.getDefaultState().with(Properties.HORIZONTAL_FACING, Direction.NORTH);
        p.lateDouble(x, 1, z, bed.with(Properties.BED_PART, BedPart.FOOT),
                x, 1, z + 1, bed.with(Properties.BED_PART, BedPart.HEAD));
    }

    /** Стол (столбик забора + нажимная плита) и два стула по бокам. */
    private static void table(StructurePlan p, int x, int z) {
        p.set(x, 1, z, FENCE);
        p.set(x - 1, 1, z, stairs(Direction.WEST));
        p.set(x + 1, 1, z, stairs(Direction.EAST));
        p.late(x, 2, z, Blocks.OAK_PRESSURE_PLATE.getDefaultState());
    }

    private static void carpet(StructurePlan p, int x1, int z1, int x2, int z2, Block carpet) {
        for (int x = x1; x <= x2; x++) {
            for (int z = z1; z <= z2; z++) {
                p.late(x, 1, z, carpet.getDefaultState());
            }
        }
    }

    /** Деревце: ствол 3 блока и шапка листвы (листва не опадает). */
    private static void smallTree(StructurePlan p, int x, int z, Block log, Block leaves) {
        BlockState leaf = leaves.getDefaultState().with(Properties.PERSISTENT, true);
        for (int y = 1; y <= 3; y++) {
            p.set(x, y, z, log.getDefaultState());
        }
        for (int dx = -1; dx <= 1; dx++) {
            for (int dz = -1; dz <= 1; dz++) {
                if (dx != 0 || dz != 0) {
                    p.late(x + dx, 3, z + dz, leaf);
                }
                if (dx == 0 || dz == 0) {
                    p.late(x + dx, 4, z + dz, leaf);
                }
            }
        }
        p.late(x, 5, z, leaf);
    }

    // =====================================================================
    // Шахта
    // =====================================================================

    /**
     * Наклонный спуск в шахту (ширина 2: x = sx..sx+1) от клетки z = sz вглубь,
     * на {@link #MINE_DEPTH} ступеней вниз, и комната-забой внизу.
     */
    private static void buildMineShaft(StructurePlan p, int sx, int sz) {
        BlockState planks = Blocks.SPRUCE_PLANKS.getDefaultState();
        BlockState logX = LOG.with(Properties.AXIS, Direction.Axis.X);
        // Ступени поднимаются к входу, поэтому смотрят на SOUTH (к игроку)
        BlockState step = Blocks.COBBLESTONE_STAIRS.getDefaultState().with(Properties.HORIZONTAL_FACING, Direction.SOUTH);
        BlockState rail = Blocks.SPRUCE_FENCE.getDefaultState();

        for (int i = 0; i <= MINE_DEPTH; i++) {
            int z = sz + i;
            int floor = -i;
            for (int x = sx; x <= sx + 1; x++) {
                p.set(x, floor, z, (i == 0 || i == MINE_DEPTH) ? COBBLE : step);
                p.fill(x, floor - 1, z, COBBLE);
                for (int y = floor + 1; y <= floor + 3; y++) {
                    p.set(x, y, z, AIR);
                }
                if (floor + 4 <= 0) {
                    p.set(x, floor + 4, z, planks);   // потолок под землёй
                }
            }
            for (int y = floor; y <= Math.min(0, floor + 4); y++) {
                p.set(sx - 1, y, z, COBBLE);
                p.set(sx + 2, y, z, COBBLE);
            }
            if (floor + 3 >= 0 && i > 0) {   // перила вдоль открытой части
                p.lateConnected(sx - 1, 1, z, rail);
                p.lateConnected(sx + 2, 1, z, rail);
            }
            if (i % 3 == 2 && floor + 2 <= 0) {   // факелы на левой стене
                p.late(sx, floor + 2, z, wallTorch(Direction.EAST));
            }
        }
        // Арка над входом
        for (int y = 1; y <= 3; y++) {
            p.set(sx - 1, y, sz, LOG);
            p.set(sx + 2, y, sz, LOG);
        }
        for (int x = sx - 1; x <= sx + 2; x++) {
            p.set(x, 4, sz, logX);
        }

        // Комната-забой 4×4 внизу
        int floor = -MINE_DEPTH;
        int rx = sx - 2;              // левая стена комнаты
        int rz = sz + MINE_DEPTH;     // передняя стена = последняя клетка спуска
        for (int x = rx; x <= rx + 5; x++) {
            for (int z = rz; z <= rz + 5; z++) {
                boolean corridor = z == rz && (x == sx || x == sx + 1);
                if (corridor) {
                    continue;
                }
                boolean wall = x == rx || x == rx + 5 || z == rz || z == rz + 5;
                for (int y = floor; y <= floor + 4; y++) {
                    BlockState state;
                    if (y == floor) {
                        state = Blocks.STONE.getDefaultState();
                    } else if (y == floor + 4) {
                        state = planks;
                    } else {
                        state = wall ? COBBLE : AIR;
                    }
                    p.set(x, y, z, state);
                }
            }
        }
        p.set(rx, floor + 2, rz + 2, Blocks.ANDESITE.getDefaultState());
        p.set(rx + 5, floor + 1, rz + 3, Blocks.GRAVEL.getDefaultState());
        p.set(rx + 5, floor + 3, rz + 1, Blocks.DIORITE.getDefaultState());
        for (int y = floor + 1; y <= floor + 3; y++) {   // опоры
            p.set(rx + 1, y, rz + 4, LOG);
            p.set(rx + 4, y, rz + 4, LOG);
        }
        // Замшелый булыжник — «забой», рабочее место шахтёра
        BlockState rubble = Blocks.MOSSY_COBBLESTONE.getDefaultState();
        p.late(rx + 1, floor + 1, rz + 1, rubble);
        p.late(rx + 1, floor + 1, rz + 2, rubble);
        p.late(rx + 4, floor + 1, rz + 1, rubble);
        p.late(rx + 1, floor + 2, rz + 1, Blocks.MOSSY_COBBLESTONE_SLAB.getDefaultState()
                .with(Properties.SLAB_TYPE, SlabType.BOTTOM));
        p.late(rx + 3, floor + 1, rz + 3, barrel());
        p.late(rx + 3, floor + 2, rz + 4, wallTorch(Direction.SOUTH));   // висит на задней стене
        p.late(rx + 4, floor + 2, rz + 2, wallTorch(Direction.WEST));    // висит на правой стене
    }
}

package com.lesha_top1972.basedefense.structure;

import com.lesha_top1972.basedefense.block.BaseLocationState;
import com.lesha_top1972.basedefense.block.ModBlocks;
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
 * Чертежи домов поселения. Каждый дом строится в одном из 4 дизайнов ({@link HouseDesign}, класс Style ниже):
 * дубовая слобода (классика), таёжный сруб (лежачие еловые брёвна, торчащие концы, широкие свесы, крыльцо),
 * каменная застава (каменный кирпич, стены выше, плоская крыша с зубцами и башенками, бойницы),
 * берёзовый терем (белые стены в тёмном фахверке, крутая крыша с коньком).
 * Размер «коробки» (ширина и глубина) у дизайнов одинаковый, поэтому мебель и двор общие.
 * Дома разных размеров и форм: ширина, глубина, высота стен, направление конька крыши,
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
    private static final BlockState LOG = Blocks.SPRUCE_LOG.getDefaultState();   // ель: лесоруб её не рубит
    private static final BlockState FENCE = Blocks.OAK_FENCE.getDefaultState();

    private HousePlans() {
    }

    // =====================================================================
    // Дом строителя: самый большой, 6 сундуков-склад; двор со стройматериалами
    // (строится сам, без ресурсов — ведь строителя ещё нет)
    // =====================================================================
    public static StructurePlan builder(HouseDesign design) {
        House h = new House(10, 8, 4, false, 6, design);
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
    public static StructurePlan farmer(HouseDesign design) {
        House h = new House(8, 6, 3, true, 8, design);
        StructurePlan p = h.p;
        bed(p, 1, 3, Blocks.GREEN_BED);
        chest(p, 6, 1, 4, Direction.WEST);
        p.set(1, 1, 1, FENCE);                       // столик
        p.late(1, 2, 1, Blocks.POTTED_POPPY.getDefaultState());
        p.set(2, 1, 1, h.stairs(Direction.EAST));      // стул
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
    public static StructurePlan rancher(HouseDesign design) {
        House h = new House(8, 10, 3, false, 8, design);
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
    public static StructurePlan cook(HouseDesign design) {
        House h = new House(8, 8, 4, true, 6, design);
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
        p.set(5, 1, 4, h.stairs(Direction.EAST));
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

    public static StructurePlan miner(HouseDesign design) {
        House h = new House(6, 6, 3, false, 6, design);
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
    public static StructurePlan lumberjack(HouseDesign design) {
        House h = new House(6, 8, 3, false, 9, design);
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
    public static StructurePlan blacksmith(HouseDesign design) {
        House h = new House(10, 8, 3, true, 6, design);
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
    // База: самый большой дом 12×10 — склад из 10 сундуков, в центре блок «База»
    // (переносится сюда со старого места, когда дом достроен), живёт курьер
    // =====================================================================
    /** Где внутри здания Базы встаёт блок «База» (локальные координаты). */
    public static final BlockPos BASE_BLOCK_LOCAL = new BlockPos(5, 1, 5);

    public static StructurePlan baseHouse(HouseDesign design) {
        House h = new House(12, 10, 4, false, 5, design);
        StructurePlan p = h.p;
        // Склад: 5 двойных сундуков вдоль задней и левой стены
        doubleChest(p, 2, 1, 8, 1, 1, 8, Direction.SOUTH);
        doubleChest(p, 4, 1, 8, 3, 1, 8, Direction.SOUTH);
        doubleChest(p, 8, 1, 8, 7, 1, 8, Direction.SOUTH);
        doubleChest(p, 10, 1, 8, 9, 1, 8, Direction.SOUTH);
        doubleChest(p, 1, 1, 5, 1, 1, 4, Direction.EAST);
        // Место под блок «База» и ковёр вокруг
        BlockPos b = BASE_BLOCK_LOCAL;
        p.set(b.getX(), 1, b.getZ(), AIR);
        for (int x = b.getX() - 1; x <= b.getX() + 1; x++) {
            for (int z = b.getZ() - 1; z <= b.getZ() + 1; z++) {
                if (x != b.getX() || z != b.getZ()) {
                    p.late(x, 1, z, Blocks.RED_CARPET.getDefaultState());
                }
            }
        }
        // Комната курьера: кровать, стол, бочки
        bed(p, 10, 1, Blocks.BLUE_BED);
        table(p, 9, 5);
        p.set(1, 1, 1, barrel());
        p.set(2, 1, 1, barrel());
        p.set(1, 1, 2, barrel());

        // Флагшток с флагом поселения у входа — Базу видно издалека
        for (int y = 1; y <= 7; y++) {
            p.set(-1, y, -2, h.s.fence);
        }
        p.set(-2, 7, -2, Blocks.CYAN_WOOL.getDefaultState());
        p.set(-2, 6, -2, Blocks.CYAN_WOOL.getDefaultState());
        p.set(-2, 7, -3, Blocks.WHITE_WOOL.getDefaultState());
        p.set(-2, 6, -3, Blocks.CYAN_WOOL.getDefaultState());
        p.late(-1, 8, -2, Blocks.LANTERN.getDefaultState());

        // Двор: площадка для повозок — бочки, сено, ящики
        h.yardGround(Blocks.DIRT_PATH.getDefaultState());
        p.set(1, 1, h.yardStart + 2, barrel());
        p.set(1, 1, h.yardStart + 3, barrel());
        p.set(2, 1, h.yardStart + 3, Blocks.HAY_BLOCK.getDefaultState());
        p.set(9, 1, h.yardStart + 2, Blocks.HAY_BLOCK.getDefaultState());
        p.set(10, 1, h.yardStart + 2, barrel());
        p.set(10, 2, h.yardStart + 2, barrel());
        h.yardFence();

        h.settle(ModEntities.COURIER, h.chestPos(2, 1, 8));
        StructurePlan.FinishAction settle = p.getFinishAction();
        p.onFinish(task -> {
            settle.run(task);
            moveBaseBlock(task, task.toWorld(b.getX(), b.getY(), b.getZ()));
        });
        return p;
    }

    /**
     * Переносит блок «База» внутрь здания Базы. Все данные поселения (еда, жители, исследования)
     * хранятся не в блоке, а в мире — поэтому при переносе ничего не теряется.
     */
    private static void moveBaseBlock(BuildTask task, BlockPos target) {
        net.minecraft.server.world.ServerWorld world = task.getWorld();
        net.minecraft.server.MinecraftServer server = world.getServer();
        BaseLocationState base = BaseLocationState.get(server);
        if (base.hasBase()) {
            net.minecraft.server.world.ServerWorld old = server.getWorld(base.getDimension());
            BlockPos oldPos = base.getPos();
            if (old == world && target.equals(oldPos)) {
                return;
            }
            if (old != null && oldPos != null && old.getBlockState(oldPos).isOf(ModBlocks.BASE)) {
                old.setBlockState(oldPos, AIR);      // старое место освобождается (Base убирает запись о себе)
                old.spawnParticles(net.minecraft.particle.ParticleTypes.PORTAL, oldPos.getX() + 0.5, oldPos.getY() + 0.5,
                        oldPos.getZ() + 0.5, 40, 0.4, 0.6, 0.4, 0.2);
            }
            base.clear();
        }
        world.setBlockState(target, ModBlocks.BASE.getDefaultState());
        base.set(world, target);
        world.spawnParticles(net.minecraft.particle.ParticleTypes.PORTAL, target.getX() + 0.5, target.getY() + 0.5,
                target.getZ() + 0.5, 60, 0.4, 0.8, 0.4, 0.3);
        server.getPlayerManager().broadcast(net.minecraft.text.Text.translatable("message.basedefense.base.moved",
                target.getX(), target.getY(), target.getZ()).formatted(net.minecraft.util.Formatting.GOLD), false);
    }

    // =====================================================================
    // Стиль (дизайн): материалы и форма
    // =====================================================================

    private enum Roof { GABLE, STEEP, FLAT }

    /** Всё, чем дизайны отличаются друг от друга. */
    private static final class Style {
        HouseDesign design;
        BlockState wall, plinth, corner, floor, floorEdge, gable, roofBlock, window, fence, gate, ridge;
        @Nullable
        BlockState post;              // фахверк: столбы между окнами
        Block roofStairs, door, furnitureStairs;
        Roof roof = Roof.GABLE;
        int extraHeight;              // на сколько стены выше обычного
        int eave = 1;                 // свес крыши (блоков за стену)
        boolean porch;                // крытое крыльцо со столбами и фонарями
        boolean logEnds;              // торчащие концы брёвен по углам (сруб)
        boolean horizontalLogs;       // стены из лежачих брёвен

        static Style of(HouseDesign design) {
            Style s = new Style();
            s.design = design;
            switch (design) {
                case SPRUCE -> {
                    s.wall = Blocks.SPRUCE_LOG.getDefaultState();
                    s.horizontalLogs = true;
                    s.plinth = s.wall;
                    s.corner = Blocks.STRIPPED_SPRUCE_LOG.getDefaultState();
                    s.floor = Blocks.SPRUCE_PLANKS.getDefaultState();
                    s.floorEdge = Blocks.MOSSY_COBBLESTONE.getDefaultState();
                    s.gable = Blocks.SPRUCE_PLANKS.getDefaultState();
                    s.roofStairs = Blocks.SPRUCE_STAIRS;
                    s.roofBlock = Blocks.SPRUCE_PLANKS.getDefaultState();
                    s.window = Blocks.GLASS_PANE.getDefaultState();
                    s.fence = Blocks.SPRUCE_FENCE.getDefaultState();
                    s.gate = Blocks.SPRUCE_FENCE_GATE.getDefaultState();
                    s.door = Blocks.SPRUCE_DOOR;
                    s.furnitureStairs = Blocks.SPRUCE_STAIRS;
                    s.ridge = Blocks.SPRUCE_SLAB.getDefaultState();
                    s.eave = 2;
                    s.porch = true;
                    s.logEnds = true;
                }
                case STONE -> {
                    s.wall = Blocks.STONE_BRICKS.getDefaultState();
                    s.plinth = Blocks.COBBLESTONE.getDefaultState();
                    s.corner = Blocks.CHISELED_STONE_BRICKS.getDefaultState();
                    s.floor = Blocks.POLISHED_ANDESITE.getDefaultState();
                    s.floorEdge = Blocks.COBBLESTONE.getDefaultState();
                    s.gable = s.wall;
                    s.roofStairs = Blocks.STONE_BRICK_STAIRS;
                    s.roofBlock = Blocks.STONE_BRICK_SLAB.getDefaultState().with(Properties.SLAB_TYPE, SlabType.TOP);
                    s.window = Blocks.IRON_BARS.getDefaultState();
                    s.fence = Blocks.COBBLESTONE_WALL.getDefaultState();
                    s.gate = Blocks.SPRUCE_FENCE_GATE.getDefaultState();
                    s.door = Blocks.SPRUCE_DOOR;
                    s.furnitureStairs = Blocks.SPRUCE_STAIRS;
                    s.ridge = Blocks.STONE_BRICK_SLAB.getDefaultState();
                    s.roof = Roof.FLAT;
                    s.extraHeight = 1;
                }
                case BIRCH -> {
                    s.wall = Blocks.BIRCH_PLANKS.getDefaultState();
                    s.plinth = Blocks.STRIPPED_DARK_OAK_LOG.getDefaultState();
                    s.corner = Blocks.DARK_OAK_LOG.getDefaultState();
                    s.post = Blocks.STRIPPED_DARK_OAK_LOG.getDefaultState();
                    s.floor = Blocks.BIRCH_PLANKS.getDefaultState();
                    s.floorEdge = Blocks.STONE_BRICKS.getDefaultState();
                    s.gable = Blocks.BIRCH_PLANKS.getDefaultState();
                    s.roofStairs = Blocks.DARK_OAK_STAIRS;
                    s.roofBlock = Blocks.DARK_OAK_PLANKS.getDefaultState();
                    s.window = Blocks.GLASS_PANE.getDefaultState();
                    s.fence = Blocks.BIRCH_FENCE.getDefaultState();
                    s.gate = Blocks.BIRCH_FENCE_GATE.getDefaultState();
                    s.door = Blocks.BIRCH_DOOR;
                    s.furnitureStairs = Blocks.BIRCH_STAIRS;
                    s.ridge = Blocks.DARK_OAK_SLAB.getDefaultState();
                    s.roof = Roof.STEEP;
                }
                default -> {
                    s.wall = Blocks.OAK_PLANKS.getDefaultState();
                    s.plinth = COBBLE;
                    s.corner = LOG;
                    s.floor = Blocks.SPRUCE_PLANKS.getDefaultState();
                    s.floorEdge = COBBLE;
                    s.gable = Blocks.SPRUCE_PLANKS.getDefaultState();
                    s.roofStairs = Blocks.SPRUCE_STAIRS;
                    s.roofBlock = Blocks.SPRUCE_PLANKS.getDefaultState();
                    s.window = Blocks.GLASS_PANE.getDefaultState();
                    s.fence = Blocks.OAK_FENCE.getDefaultState();
                    s.gate = Blocks.OAK_FENCE_GATE.getDefaultState();
                    s.door = Blocks.OAK_DOOR;
                    s.furnitureStairs = Blocks.OAK_STAIRS;
                    s.ridge = null;
                }
            }
            return s;
        }

        /** Балка вдоль оси (бревно лежит; у камня — просто каменный кирпич). */
        BlockState beam(Direction.Axis axis) {
            return switch (design) {
                case STONE -> Blocks.STONE_BRICKS.getDefaultState();
                case BIRCH -> Blocks.DARK_OAK_LOG.getDefaultState().with(Properties.AXIS, axis);
                default -> LOG.with(Properties.AXIS, axis);
            };
        }

        /** Блок стены: у сруба брёвна лежат вдоль стены. */
        BlockState wallFor(boolean sideX) {
            if (horizontalLogs) {
                return wall.with(Properties.AXIS, sideX ? Direction.Axis.Z : Direction.Axis.X);
            }
            return wall;
        }

        BlockState stairs(Direction facing) {
            return furnitureStairs.getDefaultState().with(Properties.HORIZONTAL_FACING, facing);
        }
    }

    // =====================================================================
    // Коробка дома
    // =====================================================================

    /** Один дом: коробка, крыша, двор. Создаётся с размерами и дизайном, мебель ставится отдельно. */
    private static final class House {
        final StructurePlan p;
        final Style s;
        final int w, d, h;           // ширина, глубина, высота стен
        final boolean ridgeAlongX;   // конёк поперёк дома (скаты на перед и зад)
        final int doorX, backX;      // передняя и задняя двери
        final int beamY, roofTop;
        final int yardStart, yardEnd;

        House(int w, int d, int h, boolean ridgeAlongX, int yardDepth, HouseDesign design) {
            this.s = Style.of(design);
            this.w = w;
            this.d = d;
            this.h = h + s.extraHeight;
            this.ridgeAlongX = ridgeAlongX;
            this.doorX = w / 2 - 1;
            this.backX = w / 2;
            this.beamY = this.h + 1;
            int span = ridgeAlongX ? d : w;
            this.roofTop = switch (s.roof) {
                case GABLE -> beamY + span / 2;
                case STEEP -> beamY + 2 * (span / 2) + 1;
                case FLAT -> beamY + 4;
            };
            this.yardStart = d;
            this.yardEnd = d + yardDepth - 1;
            this.p = new StructurePlan(new BlockPos(doorX, 1, -1));
            build();
        }

        BlockPos chestPos(int x, int y, int z) {
            return new BlockPos(x, y, z);
        }

        BlockState stairs(Direction facing) {
            return s.stairs(facing);
        }

        private void build() {
            // Расчистка места над землёй (с запасом под свесы и крыльцо)
            for (int x = -2; x <= w + 1; x++) {
                for (int z = -3; z <= yardEnd; z++) {
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
                    p.set(x, 0, z, edge ? s.floorEdge : s.floor);
                }
            }
            p.set(doorX, 0, -1, Blocks.DIRT_PATH.getDefaultState());

            // Стены: нижний ряд — цоколь, углы — столбы
            for (int y = 1; y <= h; y++) {
                for (int x = 0; x < w; x++) {
                    for (int z = 0; z < d; z++) {
                        boolean sideX = x == 0 || x == w - 1;
                        boolean sideZ = z == 0 || z == d - 1;
                        if (sideX && sideZ) {
                            p.set(x, y, z, s.corner);
                        } else if (sideX || sideZ) {
                            BlockState wall;
                            if (y == 1 && !s.horizontalLogs) {
                                wall = s.plinth.contains(Properties.AXIS)
                                        ? s.plinth.with(Properties.AXIS, sideX ? Direction.Axis.Z : Direction.Axis.X)
                                        : s.plinth;
                            } else {
                                wall = s.wallFor(sideX);
                            }
                            p.set(x, y, z, wall);
                        }
                    }
                }
            }
            // Фахверк: тёмные столбы между окнами на боковых стенах
            if (s.post != null) {
                for (int z = 2; z <= d - 3; z++) {
                    if ((z - 2) % 3 == 2) {
                        for (int y = 2; y <= h; y++) {
                            p.set(0, y, z, s.post);
                            p.set(w - 1, y, z, s.post);
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
            for (int z = 0; z < d; z++) {
                p.set(0, beamY, z, s.beam(Direction.Axis.Z));
                p.set(w - 1, beamY, z, s.beam(Direction.Axis.Z));
            }
            for (int x = 1; x < w - 1; x++) {
                p.set(x, beamY, 0, s.beam(Direction.Axis.X));
                p.set(x, beamY, d - 1, s.beam(Direction.Axis.X));
            }
            switch (s.roof) {
                case FLAT -> buildFlatRoof();
                default -> buildRoof(s.roof == Roof.STEEP ? 2 : 1);
            }
            if (s.logEnds) {
                logEnds();
            }
            if (s.porch) {
                porch();
            }

            // Двери и свет
            BlockState door = s.door.getDefaultState().with(Properties.HORIZONTAL_FACING, Direction.NORTH);
            p.lateDouble(doorX, 1, 0, door.with(Properties.DOUBLE_BLOCK_HALF, DoubleBlockHalf.LOWER),
                    doorX, 2, 0, door.with(Properties.DOUBLE_BLOCK_HALF, DoubleBlockHalf.UPPER));
            p.lateDouble(backX, 1, d - 1, door.with(Properties.DOUBLE_BLOCK_HALF, DoubleBlockHalf.LOWER),
                    backX, 2, d - 1, door.with(Properties.DOUBLE_BLOCK_HALF, DoubleBlockHalf.UPPER));
            p.late(1, h, d / 2, wallTorch(Direction.EAST));
            p.late(w - 2, h, d / 2, wallTorch(Direction.WEST));
            if (!s.porch) {
                p.late(doorX - 1, h, -1, wallTorch(Direction.SOUTH));
                p.late(doorX + 1, h, -1, wallTorch(Direction.SOUTH));
            }
        }

        /**
         * Двускатная крыша со свесами и фронтонами. step — сколько блоков вверх на шаг ската:
         * 1 — обычная, 2 — крутая (терем). Вдоль конька свес s.eave, у сруба ещё нижний ряд свеса.
         */
        private void buildRoof(int step) {
            int span = ridgeAlongX ? d : w;
            int other = ridgeAlongX ? w : d;
            int ov = s.eave;
            for (int r = 0; r <= span / 2; r++) {
                int y = beamY + step * r;
                for (int b = -ov; b <= other - 1 + ov; b++) {
                    roofCell(-1 + r, y, b, true);
                    roofCell(span - r, y, b, false);
                    if (step == 2 && r >= 1) {
                        put(-1 + r, y - 1, b, s.roofBlock);
                        put(span - r, y - 1, b, s.roofBlock);
                    }
                }
                // Фронтоны
                int gableTop = step == 2 ? 2 : 1;
                if (r >= 1 && r <= (span - 1) / 2 && (step == 2 || r < span / 2)) {
                    for (int a = r; a <= span - 1 - r; a++) {
                        for (int k = 0; k < gableTop; k++) {
                            int gy = y - k;
                            if (gy > beamY) {
                                put(a, gy, 0, s.gable);
                                put(a, gy, other - 1, s.gable);
                            }
                        }
                    }
                }
            }
            // Нижний ряд свеса (у сруба)
            if (ov >= 2) {
                for (int b = -ov; b <= other - 1 + ov; b++) {
                    roofCell(-2, beamY - 1, b, true);
                    roofCell(span + 1, beamY - 1, b, false);
                }
            }
            // Конёк
            if (s.ridge != null) {
                int topY = beamY + step * (span / 2) + 1;
                for (int b = -ov; b <= other - 1 + ov; b++) {
                    put(span / 2 - 1, topY, b, s.ridge);
                    put(span / 2, topY, b, s.ridge);
                }
            }
            // Окошко на чердаке (если фронтон достаточно высокий)
            if (span / 2 - 1 >= 2 || step == 2 && span / 2 >= 2) {
                int y = beamY + 2;
                int a = span / 2 - 1;
                if (ridgeAlongX) {
                    window(0, y, a);
                    window(0, y, a + 1);
                    window(w - 1, y, a);
                    window(w - 1, y, a + 1);
                } else {
                    window(a, y, 0);
                    window(a + 1, y, 0);
                    window(a, y, d - 1);
                    window(a + 1, y, d - 1);
                }
            }
        }

        /** Ступенька ската: a — поперёк конька (0 — край дома), b — вдоль конька. */
        private void roofCell(int a, int y, int b, boolean nearSide) {
            Direction facing;
            if (ridgeAlongX) {
                facing = nearSide ? Direction.NORTH : Direction.SOUTH;
            } else {
                facing = nearSide ? Direction.EAST : Direction.WEST;
            }
            put(a, y, b, s.roofStairs.getDefaultState().with(Properties.HORIZONTAL_FACING, facing));
        }

        /** Поставить блок по координатам «поперёк конька / вдоль конька». */
        private void put(int a, int y, int b, BlockState state) {
            if (ridgeAlongX) {
                p.set(b, y, a, state);
            } else {
                p.set(a, y, b, state);
            }
        }

        /** Плоская крыша с зубцами по краю и башенками по углам (каменная застава). */
        private void buildFlatRoof() {
            for (int x = 1; x < w - 1; x++) {
                for (int z = 1; z < d - 1; z++) {
                    p.set(x, beamY, z, s.roofBlock);
                }
            }
            // Сплошной парапет по краю, сверху зубцы через один
            for (int x = 0; x < w; x++) {
                for (int z = 0; z < d; z++) {
                    boolean edge = x == 0 || x == w - 1 || z == 0 || z == d - 1;
                    if (edge) {
                        p.set(x, beamY + 1, z, s.wall);
                        if ((x + z) % 2 == 0) {
                            p.set(x, beamY + 2, z, s.wall);
                        }
                    }
                }
            }
            // Башенки по углам: на блок выше зубцов, сверху фонарь
            for (int[] c : new int[][]{{0, 0}, {w - 1, 0}, {0, d - 1}, {w - 1, d - 1}}) {
                for (int y = beamY + 1; y <= beamY + 3; y++) {
                    p.set(c[0], y, c[1], s.corner);
                }
                p.late(c[0], beamY + 4, c[1], Blocks.LANTERN.getDefaultState());
            }
            // Козырёк над входом: перевёрнутые ступени
            BlockState hood = Blocks.STONE_BRICK_STAIRS.getDefaultState()
                    .with(Properties.HORIZONTAL_FACING, Direction.NORTH)
                    .with(Properties.BLOCK_HALF, net.minecraft.block.enums.BlockHalf.TOP);
            for (int x = doorX - 1; x <= doorX + 1; x++) {
                p.set(x, 3, -1, hood);
            }
        }

        /** Сруб: концы брёвен торчат из углов (через ряд — то вдоль, то поперёк). */
        private void logEnds() {
            BlockState alongX = s.wall.with(Properties.AXIS, Direction.Axis.X);
            BlockState alongZ = s.wall.with(Properties.AXIS, Direction.Axis.Z);
            for (int y = 1; y <= h; y++) {
                boolean odd = y % 2 == 1;
                if (odd) {
                    p.set(-1, y, 0, alongX);
                    p.set(w, y, 0, alongX);
                    p.set(-1, y, d - 1, alongX);
                    p.set(w, y, d - 1, alongX);
                } else {
                    p.set(0, y, -1, alongZ);
                    p.set(w - 1, y, -1, alongZ);
                }
            }
        }

        /** Крытое крыльцо: столбы, навес из полублоков и два фонаря. */
        private void porch() {
            int top = h;
            for (int x : new int[]{doorX - 2, doorX + 2}) {
                for (int y = 1; y < top; y++) {
                    p.set(x, y, -2, s.fence);
                }
            }
            BlockState slab = s.ridge != null ? s.ridge : Blocks.SPRUCE_SLAB.getDefaultState();
            for (int x = doorX - 2; x <= doorX + 2; x++) {
                for (int z = -2; z <= -1; z++) {
                    p.set(x, top, z, slab);
                }
            }
            BlockState lantern = Blocks.LANTERN.getDefaultState().with(Properties.HANGING, true);
            p.late(doorX - 1, top - 1, -2, lantern);
            p.late(doorX + 1, top - 1, -2, lantern);
        }

        /** Окно ставится в конце, чтобы соединиться с соседями. */
        private void window(int x, int y, int z) {
            p.set(x, y, z, AIR);
            p.lateConnected(x, y, z, s.window);
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
                p.lateConnected(0, 1, z, s.fence);
                p.lateConnected(w - 1, 1, z, s.fence);
            }
            for (int x = 1; x < w - 1; x++) {
                p.lateConnected(x, 1, yardEnd, x == backX
                        ? s.gate.with(Properties.HORIZONTAL_FACING, Direction.NORTH)
                        : s.fence);
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
    // Центральная площадь 15×15: мощение кольцами, звонница с колоколом в центре,
    // скамейки, фонари, клумбы у проходов и 4 рыночных прилавка по углам.
    // Жителя нет — сюда ходят все жители (постоять у колокола, у прилавков).
    // =====================================================================
    public static final int SQUARE_RADIUS = 7;
    /** Где стоят покупатели у прилавков (смещение от центра: x — вправо, z — вглубь). */
    public static final int[][] MARKET_SPOTS = {{-5, -4}, {5, -4}, {-5, 4}, {5, 4}};

    public static StructurePlan townSquare(HouseDesign design) {
        int c = SQUARE_RADIUS;            // центр площади
        int size = 2 * c + 1;
        StructurePlan p = new StructurePlan(new BlockPos(c, 1, -1));
        // Мощение кольцами (от центра наружу) — своё у каждого дизайна
        BlockState[] paving = switch (design) {
            case SPRUCE -> new BlockState[]{Blocks.SPRUCE_PLANKS.getDefaultState(), Blocks.MOSSY_COBBLESTONE.getDefaultState(),
                    Blocks.COARSE_DIRT.getDefaultState(), Blocks.DIRT_PATH.getDefaultState()};
            case STONE -> new BlockState[]{Blocks.CHISELED_STONE_BRICKS.getDefaultState(), Blocks.STONE_BRICKS.getDefaultState(),
                    Blocks.POLISHED_ANDESITE.getDefaultState(), COBBLE};
            case BIRCH -> new BlockState[]{Blocks.POLISHED_DIORITE.getDefaultState(), Blocks.BIRCH_PLANKS.getDefaultState(),
                    Blocks.SMOOTH_STONE.getDefaultState(), Blocks.DIRT_PATH.getDefaultState()};
            default -> new BlockState[]{Blocks.STONE_BRICKS.getDefaultState(), Blocks.POLISHED_ANDESITE.getDefaultState(),
                    COBBLE, Blocks.DIRT_PATH.getDefaultState()};
        };
        // Расчистка и засыпка
        for (int x = -1; x <= size; x++) {
            for (int z = -1; z <= size; z++) {
                for (int y = 1; y <= 8; y++) {
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
                    case 0, 1 -> paving[0];
                    case 2 -> paving[1];
                    case 4 -> paving[2];
                    default -> paving[3];
                };
                p.set(x, 0, z, ground);
            }
        }
        // Клумбы по краям, по обе стороны от проходов
        int[][] beds = {{c - 2, 0}, {c + 2, 0}, {c - 2, size - 1}, {c + 2, size - 1},
                {0, c - 2}, {0, c + 2}, {size - 1, c - 2}, {size - 1, c + 2}};
        for (int i = 0; i < beds.length; i++) {
            p.set(beds[i][0], 0, beds[i][1], Blocks.GRASS_BLOCK.getDefaultState());
            Block flower = switch (i % 3) {
                case 0 -> Blocks.POPPY;
                case 1 -> Blocks.DANDELION;
                default -> Blocks.AZURE_BLUET;
            };
            p.late(beds[i][0], 1, beds[i][1], flower.getDefaultState());
        }
        belfry(p, c, design);
        // Скамейки лицом к колоколу (проходы по центру каждой стороны свободны)
        for (int d : new int[]{-1, 1}) {
            p.set(c - 3, 1, c + d, stairs(Direction.WEST));
            p.set(c + 3, 1, c + d, stairs(Direction.EAST));
            p.set(c + d, 1, c + 3, stairs(Direction.NORTH));
            p.set(c + d, 1, c - 3, stairs(Direction.SOUTH));
        }
        // Фонари на столбах у внутренних углов
        BlockState lampPost = switch (design) {
            case SPRUCE -> Blocks.SPRUCE_FENCE.getDefaultState();
            case STONE -> Blocks.STONE_BRICK_WALL.getDefaultState();
            case BIRCH -> Blocks.BIRCH_FENCE.getDefaultState();
            default -> FENCE;
        };
        for (int[] lp : new int[][]{{c - 3, c - 3}, {c + 3, c - 3}, {c - 3, c + 3}, {c + 3, c + 3}}) {
            p.set(lp[0], 1, lp[1], lampPost);
            p.set(lp[0], 2, lp[1], lampPost);
            p.late(lp[0], 3, lp[1], Blocks.LANTERN.getDefaultState());
        }
        // Рынок: 4 прилавка с полосатыми навесами (прилавком к центру площади)
        stall(p, 1, 2, 1, Blocks.RED_WOOL, Blocks.HAY_BLOCK.getDefaultState(), Blocks.POTTED_POPPY);
        stall(p, size - 4, 2, 1, Blocks.YELLOW_WOOL, Blocks.STRIPPED_SPRUCE_LOG.getDefaultState().with(Properties.AXIS, Direction.Axis.X), Blocks.POTTED_DANDELION);
        stall(p, 1, size - 3, size - 2, Blocks.LIGHT_BLUE_WOOL, Blocks.BROWN_WOOL.getDefaultState(), Blocks.POTTED_AZURE_BLUET);
        stall(p, size - 4, size - 3, size - 2, Blocks.GREEN_WOOL, barrel(), Blocks.POTTED_FERN);

        // Готово — запоминаем площадь
        p.onFinish(task -> TownState.get(task.getWorld().getServer()).setSquare(
                task.toWorld(c, 0, c), task.toWorld(c, 3, c), SQUARE_RADIUS, task.getFacing(), dimensionOf(task)));
        return p;
    }

    /**
     * Звонница в центре площади (колокол всегда в (c, 3, c), висит на перекладине (c, 4, c)).
     * Дуб — два столба и перекладина; ель — с двускатной крышей; камень — каменные столбы с плитами
     * и фонарём; берёза — четыре столба и шатёр.
     */
    private static void belfry(StructurePlan p, int c, HouseDesign design) {
        BlockState postBlock = switch (design) {
            case STONE -> Blocks.STONE_BRICKS.getDefaultState();
            case BIRCH -> Blocks.BIRCH_LOG.getDefaultState();
            default -> LOG;
        };
        BlockState beam = switch (design) {
            case STONE -> Blocks.STONE_BRICKS.getDefaultState();
            case BIRCH -> Blocks.DARK_OAK_LOG.getDefaultState().with(Properties.AXIS, Direction.Axis.X);
            default -> LOG.with(Properties.AXIS, Direction.Axis.X);
        };
        if (design == HouseDesign.BIRCH) {
            // Четыре столба по углам 3×3
            for (int[] q : new int[][]{{c - 1, c - 1}, {c + 1, c - 1}, {c - 1, c + 1}, {c + 1, c + 1}}) {
                for (int y = 1; y <= 3; y++) {
                    p.set(q[0], y, q[1], postBlock);
                }
            }
            for (int x = c - 1; x <= c + 1; x++) {
                p.set(x, 4, c - 1, beam);
                p.set(x, 4, c + 1, beam);
                p.set(x, 4, c, beam);
            }
            // Шатёр: кольцо ступеней и маковка
            for (int x = c - 2; x <= c + 2; x++) {
                for (int z = c - 2; z <= c + 2; z++) {
                    int ring = Math.max(Math.abs(x - c), Math.abs(z - c));
                    if (ring == 2) {
                        p.set(x, 5, z, tentStair(x - c, z - c));
                    } else if (ring == 1) {
                        p.set(x, 6, z, tentStair(x - c, z - c));
                        p.set(x, 5, z, Blocks.DARK_OAK_PLANKS.getDefaultState());
                    }
                }
            }
            p.set(c, 5, c, Blocks.DARK_OAK_PLANKS.getDefaultState());
            p.set(c, 6, c, Blocks.DARK_OAK_PLANKS.getDefaultState());
            p.set(c, 7, c, Blocks.DARK_OAK_FENCE.getDefaultState());
        } else {
            int postTop = design == HouseDesign.STONE ? 4 : 3;
            for (int y = 1; y <= postTop; y++) {
                p.set(c - 1, y, c, postBlock);
                p.set(c + 1, y, c, postBlock);
            }
            for (int x = c - 1; x <= c + 1; x++) {
                p.set(x, 4, c, beam);
            }
            switch (design) {
                case SPRUCE -> {
                    // Двускатная крыша над колоколом
                    for (int x = c - 2; x <= c + 2; x++) {
                        p.set(x, 5, c - 1, Blocks.SPRUCE_STAIRS.getDefaultState()
                                .with(Properties.HORIZONTAL_FACING, Direction.NORTH));
                        p.set(x, 5, c + 1, Blocks.SPRUCE_STAIRS.getDefaultState()
                                .with(Properties.HORIZONTAL_FACING, Direction.SOUTH));
                        p.set(x, 5, c, Blocks.SPRUCE_PLANKS.getDefaultState());
                        p.set(x, 6, c, Blocks.SPRUCE_SLAB.getDefaultState());
                    }
                }
                case STONE -> {
                    for (int x = c - 2; x <= c + 2; x++) {
                        p.set(x, 5, c, Blocks.STONE_BRICK_SLAB.getDefaultState());
                    }
                    p.set(c - 1, 5, c, Blocks.CHISELED_STONE_BRICKS.getDefaultState());
                    p.set(c + 1, 5, c, Blocks.CHISELED_STONE_BRICKS.getDefaultState());
                    p.late(c - 1, 6, c, Blocks.LANTERN.getDefaultState());
                    p.late(c + 1, 6, c, Blocks.LANTERN.getDefaultState());
                }
                default -> p.set(c, 5, c, Blocks.SPRUCE_SLAB.getDefaultState());
            }
        }
        p.late(c, 3, c, Blocks.BELL.getDefaultState()
                .with(Properties.ATTACHMENT, net.minecraft.block.enums.Attachment.CEILING)
                .with(Properties.HORIZONTAL_FACING, Direction.NORTH));
    }

    /** Ступень шатра (dx, dz — смещение от центра): высокой стороной к центру. */
    private static BlockState tentStair(int dx, int dz) {
        Direction facing;
        if (Math.abs(dz) >= Math.abs(dx)) {
            facing = dz < 0 ? Direction.NORTH : Direction.SOUTH;
        } else {
            facing = dx < 0 ? Direction.EAST : Direction.WEST;
        }
        // Высокая часть ступеньки смотрит к центру (локально NORTH = +z, как у ската крыши)
        return Blocks.DARK_OAK_STAIRS.getDefaultState().with(Properties.HORIZONTAL_FACING, facing);
    }

    /**
     * Рыночный прилавок 3×2: спереди прилавок (бочка — доска — бочка), сзади товар,
     * на четырёх столбиках-заборах полосатый навес (цвет + белый).
     */
    private static void stall(StructurePlan p, int x0, int counterZ, int backZ, Block awning,
                              BlockState goods, Block pot) {
        BlockState top = Blocks.SPRUCE_SLAB.getDefaultState().with(Properties.SLAB_TYPE, SlabType.TOP);
        p.set(x0, 1, counterZ, barrel());
        p.set(x0 + 1, 1, counterZ, top);
        p.set(x0 + 2, 1, counterZ, barrel());
        p.late(x0 + 1, 2, counterZ, pot.getDefaultState());
        p.set(x0 + 1, 1, backZ, goods);
        for (int x : new int[]{x0, x0 + 2}) {
            p.set(x, 2, counterZ, FENCE);
            p.set(x, 1, backZ, FENCE);
            p.set(x, 2, backZ, FENCE);
        }
        for (int x = x0; x <= x0 + 2; x++) {
            BlockState wool = ((x - x0) % 2 == 0 ? awning : Blocks.WHITE_WOOL).getDefaultState();
            p.set(x, 3, counterZ, wool);
            p.set(x, 3, backZ, wool);
        }
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

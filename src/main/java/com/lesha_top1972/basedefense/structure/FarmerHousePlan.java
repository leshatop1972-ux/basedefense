package com.lesha_top1972.basedefense.structure;

import com.lesha_top1972.basedefense.entity.FarmerEntity;
import com.lesha_top1972.basedefense.entity.ModEntities;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.block.CropBlock;
import net.minecraft.block.enums.BedPart;
import net.minecraft.block.enums.DoubleBlockHalf;
import net.minecraft.state.property.Properties;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;

import java.util.Random;

/**
 * Чертёж дома фермера: дом 8×10 с интерьером + огород 8×6 за домом.
 *
 * Локальные координаты (игрок смотрит «на север»):
 *   x = 0..7  — ширина дома (слева направо),
 *   z = 0..9  — дом (z = 0 — передняя стена с дверью), z = 10..15 — огород,
 *   y = 0     — пол (на уровне земли), y = 1..4 — стены, y = 5..9 — крыша.
 * Поставленный блок — это крыльцо перед дверью: (3, 1, -1).
 */
public final class FarmerHousePlan {

    private static final int W = 8;      // ширина дома
    private static final int D = 10;     // глубина дома
    private static final int GARDEN_END = 15;

    private FarmerHousePlan() {
    }

    public static StructurePlan create() {
        StructurePlan p = new StructurePlan(new BlockPos(3, 1, -1));
        Random random = new Random();

        BlockState air = Blocks.AIR.getDefaultState();
        BlockState cobble = Blocks.COBBLESTONE.getDefaultState();
        BlockState dirt = Blocks.DIRT.getDefaultState();
        BlockState floor = Blocks.SPRUCE_PLANKS.getDefaultState();
        BlockState wall = Blocks.OAK_PLANKS.getDefaultState();
        BlockState logY = Blocks.OAK_LOG.getDefaultState();
        BlockState logX = logY.with(Properties.AXIS, Direction.Axis.X);
        BlockState logZ = logY.with(Properties.AXIS, Direction.Axis.Z);
        BlockState glass = Blocks.GLASS_PANE.getDefaultState();
        BlockState gable = Blocks.SPRUCE_PLANKS.getDefaultState();
        BlockState roofEast = Blocks.SPRUCE_STAIRS.getDefaultState().with(Properties.HORIZONTAL_FACING, Direction.EAST);
        BlockState roofWest = Blocks.SPRUCE_STAIRS.getDefaultState().with(Properties.HORIZONTAL_FACING, Direction.WEST);

        // ---------- 1. Расчистка места (всё над землёй) ----------
        for (int x = -1; x <= W; x++) {
            for (int z = -1; z <= GARDEN_END; z++) {
                for (int y = 1; y <= 11; y++) {
                    p.set(x, y, z, air);
                }
            }
        }

        // ---------- 2. Фундамент: заполняем ямы под домом и огородом ----------
        for (int x = 0; x < W; x++) {
            for (int z = 0; z <= GARDEN_END; z++) {
                for (int y = -3; y <= -1; y++) {
                    p.fill(x, y, z, z < D ? cobble : dirt);
                }
            }
        }

        // ---------- 3. Пол дома ----------
        for (int x = 0; x < W; x++) {
            for (int z = 0; z < D; z++) {
                boolean edge = x == 0 || x == W - 1 || z == 0 || z == D - 1;
                p.set(x, 0, z, edge ? cobble : floor);
            }
        }
        p.set(3, 0, -1, Blocks.DIRT_PATH.getDefaultState()); // тропинка у крыльца

        // ---------- 4. Стены ----------
        for (int y = 1; y <= 4; y++) {
            for (int x = 0; x < W; x++) {
                for (int z = 0; z < D; z++) {
                    boolean sideX = x == 0 || x == W - 1;
                    boolean sideZ = z == 0 || z == D - 1;
                    if (!sideX && !sideZ) {
                        continue;
                    }
                    BlockState block;
                    if (sideX && sideZ) {
                        block = logY;                       // угловые столбы
                    } else if (y == 1) {
                        block = cobble;                     // нижний ряд из булыжника
                    } else {
                        block = wall;
                    }
                    p.set(x, y, z, block);
                }
            }
        }
        // Окна (y = 2..3)
        for (int y = 2; y <= 3; y++) {
            for (int z : new int[]{3, 4, 6, 7}) {
                p.set(0, y, z, glass);
                p.set(W - 1, y, z, glass);
            }
            for (int x : new int[]{1, 6}) {
                p.set(x, y, 0, glass);
                p.set(x, y, D - 1, glass);
            }
        }
        // Проёмы под двери (сами двери ставятся в конце)
        p.set(3, 1, 0, air);
        p.set(3, 2, 0, air);
        p.set(4, 1, D - 1, air);
        p.set(4, 2, D - 1, air);

        // Балки поверх стен (y = 5)
        for (int z = 0; z < D; z++) {
            p.set(0, 5, z, logZ);
            p.set(W - 1, 5, z, logZ);
        }
        for (int x = 1; x < W - 1; x++) {
            p.set(x, 5, 0, logX);
            p.set(x, 5, D - 1, logX);
        }

        // ---------- 5. Двускатная крыша ----------
        for (int r = 0; r <= 4; r++) {
            int y = 5 + r;
            for (int z = -1; z <= D; z++) {
                p.set(-1 + r, y, z, roofEast);
                p.set(W - r, y, z, roofWest);
            }
            // Фронтоны (треугольные стенки спереди и сзади)
            if (r >= 1 && r <= 3) {
                for (int x = r; x <= W - 1 - r; x++) {
                    p.set(x, y, 0, gable);
                    p.set(x, y, D - 1, gable);
                }
            }
        }
        // Круглое окошко на чердаке
        p.set(3, 7, 0, glass);
        p.set(4, 7, 0, glass);
        p.set(3, 7, D - 1, glass);
        p.set(4, 7, D - 1, glass);

        // ---------- 6. Мебель ----------
        p.set(1, 1, 2, Blocks.CRAFTING_TABLE.getDefaultState());
        p.set(1, 1, 3, Blocks.BARREL.getDefaultState().with(Properties.FACING, Direction.UP));
        p.set(1, 1, 5, Blocks.CHEST.getDefaultState().with(Properties.HORIZONTAL_FACING, Direction.EAST));
        p.set(6, 1, 2, Blocks.COMPOSTER.getDefaultState());
        p.set(6, 1, 3, Blocks.SMOKER.getDefaultState().with(Properties.HORIZONTAL_FACING, Direction.WEST));
        p.set(6, 1, 6, Blocks.BOOKSHELF.getDefaultState());
        p.set(6, 1, 7, Blocks.HAY_BLOCK.getDefaultState());
        p.set(6, 1, 8, Blocks.HAY_BLOCK.getDefaultState());
        p.set(6, 2, 8, Blocks.HAY_BLOCK.getDefaultState());
        // Стол со стульями
        p.set(5, 1, 5, Blocks.OAK_FENCE.getDefaultState());
        p.set(4, 1, 5, Blocks.OAK_STAIRS.getDefaultState().with(Properties.HORIZONTAL_FACING, Direction.WEST));
        p.set(6, 1, 5, Blocks.OAK_STAIRS.getDefaultState().with(Properties.HORIZONTAL_FACING, Direction.EAST));

        // ---------- 7. Огород за домом ----------
        BlockState farmland = Blocks.FARMLAND.getDefaultState().with(Properties.MOISTURE, 7);
        for (int x = 0; x < W; x++) {
            for (int z = D; z <= GARDEN_END; z++) {
                boolean edge = x == 0 || x == W - 1 || z == GARDEN_END;
                BlockState ground;
                if (edge) {
                    ground = Blocks.GRASS_BLOCK.getDefaultState();
                } else if (z == D) {
                    ground = Blocks.DIRT_PATH.getDefaultState();   // дорожка у задней двери
                } else if (z == 12) {
                    ground = Blocks.WATER.getDefaultState();       // канавка с водой
                } else {
                    ground = farmland;
                }
                p.set(x, 0, z, ground);
            }
        }

        // ---------- 8. Поздние блоки: двери, кровать, свет, декор, грядки ----------
        BlockState door = Blocks.OAK_DOOR.getDefaultState().with(Properties.HORIZONTAL_FACING, Direction.NORTH);
        p.lateDouble(3, 1, 0, door.with(Properties.DOUBLE_BLOCK_HALF, DoubleBlockHalf.LOWER),
                3, 2, 0, door.with(Properties.DOUBLE_BLOCK_HALF, DoubleBlockHalf.UPPER));
        p.lateDouble(4, 1, D - 1, door.with(Properties.DOUBLE_BLOCK_HALF, DoubleBlockHalf.LOWER),
                4, 2, D - 1, door.with(Properties.DOUBLE_BLOCK_HALF, DoubleBlockHalf.UPPER));

        BlockState bed = Blocks.GREEN_BED.getDefaultState().with(Properties.HORIZONTAL_FACING, Direction.NORTH);
        p.lateDouble(1, 1, 7, bed.with(Properties.BED_PART, BedPart.FOOT),
                1, 1, 8, bed.with(Properties.BED_PART, BedPart.HEAD));

        p.late(5, 2, 5, Blocks.OAK_PRESSURE_PLATE.getDefaultState());           // столешница
        p.late(1, 2, 2, Blocks.POTTED_POPPY.getDefaultState());                 // цветок на верстаке
        p.late(1, 2, 3, Blocks.LANTERN.getDefaultState());                      // фонарь на бочке
        p.late(3, 8, 3, Blocks.LANTERN.getDefaultState().with(Properties.HANGING, true));
        p.late(4, 8, 6, Blocks.LANTERN.getDefaultState().with(Properties.HANGING, true));
        for (int x = 2; x <= 3; x++) {
            for (int z = 3; z <= 7; z++) {
                p.late(x, 1, z, Blocks.GREEN_CARPET.getDefaultState());
            }
        }
        // Факелы у входа
        BlockState torch = Blocks.WALL_TORCH.getDefaultState().with(Properties.HORIZONTAL_FACING, Direction.SOUTH);
        p.late(2, 3, -1, torch);
        p.late(4, 3, -1, torch);

        // Грядки: пшеница, морковь, картофель
        for (int x = 1; x < W - 1; x++) {
            p.late(x, 1, 11, Blocks.WHEAT.getDefaultState().with(CropBlock.AGE, 2 + random.nextInt(6)));
            p.late(x, 1, 13, Blocks.CARROTS.getDefaultState().with(CropBlock.AGE, 2 + random.nextInt(6)));
            p.late(x, 1, 14, Blocks.POTATOES.getDefaultState().with(CropBlock.AGE, 2 + random.nextInt(6)));
        }
        // Забор вокруг огорода с калиткой
        BlockState fence = Blocks.OAK_FENCE.getDefaultState();
        for (int z = D; z <= GARDEN_END; z++) {
            p.lateConnected(0, 1, z, fence);
            p.lateConnected(W - 1, 1, z, fence);
        }
        for (int x = 1; x < W - 1; x++) {
            if (x == 4) {
                p.lateConnected(x, 1, GARDEN_END,
                        Blocks.OAK_FENCE_GATE.getDefaultState().with(Properties.HORIZONTAL_FACING, Direction.NORTH));
            } else {
                p.lateConnected(x, 1, GARDEN_END, fence);
            }
        }
        p.late(0, 2, GARDEN_END, Blocks.LANTERN.getDefaultState());
        p.late(W - 1, 2, GARDEN_END, Blocks.LANTERN.getDefaultState());

        // ---------- 9. Когда дом готов — заселяем фермера ----------
        p.onFinish(task -> {
            FarmerEntity farmer = ModEntities.FARMER.create(task.getWorld());
            if (farmer == null) {
                return;
            }
            BlockPos spawn = task.toWorld(3, 1, 4);
            farmer.refreshPositionAndAngles(spawn.getX() + 0.5, spawn.getY(), spawn.getZ() + 0.5,
                    task.getFacing().getOpposite().asRotation(), 0.0f);
            farmer.setHome(spawn);
            farmer.setPersistent();
            task.getWorld().spawnEntity(farmer);
        });

        return p;
    }
}

package com.lesha_top1972.basedefense.structure;

import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtElement;
import net.minecraft.nbt.NbtHelper;
import net.minecraft.nbt.NbtList;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.world.PersistentState;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

/**
 * Планировка поселения: где центральная площадь (и её колокол) и где входы в построенные дома.
 * По этим данным строитель прокладывает тропинки, а жители ходят на площадь.
 * Сохраняется в data/basedefense_town.dat в папке мира.
 */
public class TownState extends PersistentState {

    private static final String SAVE_ID = "basedefense_town";

    /** Вход в дом: клетка земли перед дверью и направление «из дома наружу». */
    public record Entrance(BlockPos ground, Direction outward, String dimension) {
    }

    /** Площадь: центр на уровне мощения, колокол, полуразмер, направление «вглубь» (как ставили блок). */
    public record Square(BlockPos center, BlockPos bell, int radius, Direction facing, String dimension) {

        /** Точка площади по смещению от центра: x — вправо, z — вглубь (как в чертеже). */
        public BlockPos offset(int x, int y, int z) {
            return center.offset(facing.rotateYClockwise(), x).offset(facing, z).up(y);
        }
    }

    @Nullable
    private Square square;
    private final List<Entrance> entrances = new ArrayList<>();
    /** Меняется при каждом изменении планировки — строитель понимает, что пора пересчитать тропинки. */
    private int revision;

    public static TownState get(MinecraftServer server) {
        return server.getOverworld().getPersistentStateManager()
                .getOrCreate(TownState::fromNbt, TownState::new, SAVE_ID);
    }

    @Nullable
    public Square getSquare(String dimension) {
        return square != null && square.dimension().equals(dimension) ? square : null;
    }

    public void setSquare(BlockPos center, BlockPos bell, int radius, Direction facing, String dimension) {
        square = new Square(center.toImmutable(), bell.toImmutable(), radius, facing, dimension);
        revision++;
        markDirty();
    }

    public void addEntrance(BlockPos ground, Direction outward, String dimension) {
        BlockPos pos = ground.toImmutable();
        entrances.removeIf(e -> e.ground().equals(pos) && e.dimension().equals(dimension));
        entrances.add(new Entrance(pos, outward, dimension));
        revision++;
        markDirty();
    }

    public List<Entrance> getEntrances(String dimension) {
        List<Entrance> result = new ArrayList<>();
        for (Entrance e : entrances) {
            if (e.dimension().equals(dimension)) {
                result.add(e);
            }
        }
        return result;
    }

    public int getRevision() {
        return revision;
    }

    // ---------- Сохранение ----------

    private static TownState fromNbt(NbtCompound nbt) {
        TownState state = new TownState();
        if (nbt.contains("Square")) {
            NbtCompound s = nbt.getCompound("Square");
            Direction facing = Direction.byName(s.getString("Facing"));
            state.square = new Square(NbtHelper.toBlockPos(s.getCompound("Center")),
                    NbtHelper.toBlockPos(s.getCompound("Bell")), s.getInt("Radius"),
                    facing == null || facing.getAxis().isVertical() ? Direction.NORTH : facing, s.getString("Dim"));
        }
        NbtList list = nbt.getList("Entrances", NbtElement.COMPOUND_TYPE);
        for (int i = 0; i < list.size(); i++) {
            NbtCompound e = list.getCompound(i);
            Direction dir = Direction.byName(e.getString("Out"));
            state.entrances.add(new Entrance(NbtHelper.toBlockPos(e.getCompound("Pos")),
                    dir == null || dir.getAxis().isVertical() ? Direction.SOUTH : dir, e.getString("Dim")));
        }
        state.revision = nbt.getInt("Revision");
        return state;
    }

    @Override
    public NbtCompound writeNbt(NbtCompound nbt) {
        if (square != null) {
            NbtCompound s = new NbtCompound();
            s.put("Center", NbtHelper.fromBlockPos(square.center()));
            s.put("Bell", NbtHelper.fromBlockPos(square.bell()));
            s.putInt("Radius", square.radius());
            s.putString("Facing", square.facing().getName());
            s.putString("Dim", square.dimension());
            nbt.put("Square", s);
        }
        NbtList list = new NbtList();
        for (Entrance e : entrances) {
            NbtCompound c = new NbtCompound();
            c.put("Pos", NbtHelper.fromBlockPos(e.ground()));
            c.putString("Out", e.outward().getName());
            c.putString("Dim", e.dimension());
            list.add(c);
        }
        nbt.put("Entrances", list);
        nbt.putInt("Revision", revision);
        return nbt;
    }
}

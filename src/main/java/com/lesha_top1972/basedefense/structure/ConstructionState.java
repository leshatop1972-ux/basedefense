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
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Стройплощадки (поставленные блоки домов, которые ждут строителя) и строители поселения.
 * Сохраняется в data/basedefense_construction.dat в папке мира.
 */
public class ConstructionState extends PersistentState {

    private static final String SAVE_ID = "basedefense_construction";
    /** Если назначенный строитель столько тиков не работал на площадке (5 минут), её может взять другой. */
    private static final long STALE_TICKS = 6000L;

    /** Одна стройплощадка. */
    public static final class Site {
        public final BlockPos pos;
        public final String dimension;
        public final HouseType type;
        public final HouseDesign design;
        public final Direction facing;
        public int step;
        @Nullable
        public UUID builder;
        public long lastWorked;

        Site(BlockPos pos, String dimension, HouseType type, HouseDesign design, Direction facing) {
            this.pos = pos.toImmutable();
            this.dimension = dimension;
            this.type = type;
            this.design = design;
            this.facing = facing;
        }

        public StructurePlan plan() {
            return type.getPlan(design);
        }
    }

    /** Строитель: где его дом (там сундуки с материалами). */
    public record BuilderInfo(BlockPos home, String dimension, int level) {
    }

    private final List<Site> sites = new ArrayList<>();
    private final Map<UUID, BuilderInfo> builders = new HashMap<>();

    public static ConstructionState get(MinecraftServer server) {
        return server.getOverworld().getPersistentStateManager()
                .getOrCreate(ConstructionState::fromNbt, ConstructionState::new, SAVE_ID);
    }

    // ---------- Площадки ----------

    public void addSite(BlockPos pos, String dimension, HouseType type, HouseDesign design, Direction facing) {
        removeSite(pos, dimension);
        sites.add(new Site(pos, dimension, type, design, facing));
        markDirty();
    }

    public void removeSite(BlockPos pos, String dimension) {
        if (sites.removeIf(s -> s.pos.equals(pos) && s.dimension.equals(dimension))) {
            markDirty();
        }
    }

    @Nullable
    public Site getSite(BlockPos pos, String dimension) {
        for (Site site : sites) {
            if (site.pos.equals(pos) && site.dimension.equals(dimension)) {
                return site;
            }
        }
        return null;
    }

    /** Номер в очереди (0 — первая) среди площадок без строителя. */
    public int queuePosition(Site target) {
        int n = 0;
        for (Site site : sites) {
            if (site == target) {
                return n;
            }
            if (site.builder == null) {
                n++;
            }
        }
        return n;
    }

    /**
     * Площадка для строителя: уже назначенная ему, иначе первая свободная в очереди
     * (или брошенная другим строителем). Null — заказов нет.
     */
    @Nullable
    public Site claim(UUID builder, String dimension, long now) {
        for (Site site : sites) {
            if (builder.equals(site.builder) && site.dimension.equals(dimension)) {
                return site;
            }
        }
        for (Site site : sites) {
            if (!site.dimension.equals(dimension)) {
                continue;
            }
            boolean free = site.builder == null || !builders.containsKey(site.builder)
                    || now - site.lastWorked > STALE_TICKS;
            if (free) {
                site.builder = builder;
                site.lastWorked = now;
                markDirty();
                return site;
            }
        }
        return null;
    }

    /** Строитель отказался от площадки (например, погиб). */
    public void release(UUID builder) {
        for (Site site : sites) {
            if (builder.equals(site.builder)) {
                site.builder = null;
                markDirty();
            }
        }
    }

    public void setStep(Site site, int step, long now) {
        site.step = step;
        site.lastWorked = now;
        markDirty();
    }

    public void touch(Site site, long now) {
        site.lastWorked = now;
    }

    // ---------- Строители ----------

    public void registerBuilder(UUID id, BlockPos home, String dimension, int level) {
        BuilderInfo info = new BuilderInfo(home.toImmutable(), dimension, level);
        if (!info.equals(builders.put(id, info))) {
            markDirty();
        }
    }

    public void removeBuilder(UUID id) {
        if (builders.remove(id) != null) {
            release(id);
            markDirty();
        }
    }

    @Nullable
    public BuilderInfo getBuilder(@Nullable UUID id) {
        return id == null ? null : builders.get(id);
    }

    public boolean hasBuilders(String dimension) {
        return builders.values().stream().anyMatch(b -> b.dimension().equals(dimension));
    }

    /** Любой строитель в этом измерении (чтобы показать, что лежит в его сундуках). */
    @Nullable
    public BuilderInfo anyBuilder(String dimension) {
        for (BuilderInfo info : builders.values()) {
            if (info.dimension().equals(dimension)) {
                return info;
            }
        }
        return null;
    }

    // ---------- Сохранение ----------

    private static ConstructionState fromNbt(NbtCompound nbt) {
        ConstructionState state = new ConstructionState();
        NbtList list = nbt.getList("Sites", NbtElement.COMPOUND_TYPE);
        for (int i = 0; i < list.size(); i++) {
            NbtCompound e = list.getCompound(i);
            HouseType type = HouseType.byId(e.getString("Type"));
            if (type == null) {
                continue;
            }
            Direction facing = Direction.byName(e.getString("Facing"));
            Site site = new Site(NbtHelper.toBlockPos(e.getCompound("Pos")), e.getString("Dim"), type,
                    HouseDesign.byId(e.getString("Design")), facing == null || facing.getAxis().isVertical() ? Direction.NORTH : facing);
            site.step = e.getInt("Step");
            if (e.containsUuid("Builder")) {
                site.builder = e.getUuid("Builder");
            }
            site.lastWorked = e.getLong("LastWorked");
            state.sites.add(site);
        }
        NbtList bl = nbt.getList("Builders", NbtElement.COMPOUND_TYPE);
        for (int i = 0; i < bl.size(); i++) {
            NbtCompound e = bl.getCompound(i);
            if (e.containsUuid("Id")) {
                state.builders.put(e.getUuid("Id"), new BuilderInfo(NbtHelper.toBlockPos(e.getCompound("Home")),
                        e.getString("Dim"), Math.max(1, e.getInt("Level"))));
            }
        }
        return state;
    }

    @Override
    public NbtCompound writeNbt(NbtCompound nbt) {
        NbtList list = new NbtList();
        for (Site site : sites) {
            NbtCompound e = new NbtCompound();
            e.put("Pos", NbtHelper.fromBlockPos(site.pos));
            e.putString("Dim", site.dimension);
            e.putString("Type", site.type.getId());
            e.putString("Design", site.design.getId());
            e.putString("Facing", site.facing.getName());
            e.putInt("Step", site.step);
            if (site.builder != null) {
                e.putUuid("Builder", site.builder);
            }
            e.putLong("LastWorked", site.lastWorked);
            list.add(e);
        }
        nbt.put("Sites", list);
        NbtList bl = new NbtList();
        builders.forEach((id, info) -> {
            NbtCompound e = new NbtCompound();
            e.putUuid("Id", id);
            e.put("Home", NbtHelper.fromBlockPos(info.home()));
            e.putString("Dim", info.dimension());
            e.putInt("Level", info.level());
            bl.add(e);
        });
        nbt.put("Builders", bl);
        return nbt;
    }
}

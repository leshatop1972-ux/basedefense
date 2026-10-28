package com.lesha_top1972.basedefense.screen;

import com.lesha_top1972.basedefense.block.HouseBlock;
import com.lesha_top1972.basedefense.entity.BuilderEntity;
import com.lesha_top1972.basedefense.structure.BuildCost;
import com.lesha_top1972.basedefense.structure.ConstructionState;
import com.lesha_top1972.basedefense.structure.HouseDesign;
import com.lesha_top1972.basedefense.structure.HouseType;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.registry.Registries;
import net.minecraft.screen.ArrayPropertyDelegate;
import net.minecraft.screen.PropertyDelegate;
import net.minecraft.screen.ScreenHandler;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Меню блока дома (стройплощадки): какие материалы нужны, сколько уже поставлено,
 * сколько лежит в сундуках строителя и что сейчас делает строитель.
 * Список материалов клиент считает сам по чертежу (он одинаковый на сервере и клиенте),
 * а сервер присылает только числа: шаг стройки, состояние и запасы.
 */
public class HouseScreenHandler extends ScreenHandler {

    public static final int PROP_STEP = 0;      // до какого шага чертежа дошла стройка
    public static final int PROP_STATUS = 1;
    public static final int PROP_MISSING = 2;   // номер предмета, которого не хватает (-1 — нет)
    public static final int PROP_LEVEL = 3;     // уровень назначенного строителя (0 — нет)
    public static final int PROP_QUEUE = 4;     // место в очереди
    public static final int PROP_AVAILABLE = 5; // дальше — сколько каждого материала в сундуках строителя
    public static final int MAX_ITEMS = 64;
    public static final int PROP_COUNT = PROP_AVAILABLE + MAX_ITEMS;

    public static final int STATUS_NO_SITE = 0;     // стройплощадки нет
    public static final int STATUS_NO_BUILDER = 1;  // в поселении нет строителя
    public static final int STATUS_QUEUE = 2;       // ждёт своей очереди
    public static final int STATUS_WALKING = 3;     // строитель идёт сюда
    public static final int STATUS_BUILDING = 4;    // строится
    public static final int STATUS_MISSING = 5;     // не хватает материала

    private final BlockPos pos;
    private final HouseType type;
    private final HouseDesign design;
    private final List<Item> items;
    private final List<Integer> totals;
    private final PropertyDelegate props;
    @Nullable
    private final ServerWorld world;
    private final int[] values = new int[PROP_COUNT];

    /** Конструктор для клиента: позиция, вид дома и дизайн приходят от сервера. */
    public HouseScreenHandler(int syncId, PlayerInventory inventory, PacketByteBuf buf) {
        this(syncId, buf.readBlockPos(), typeOrDefault(buf.readString()), HouseDesign.byId(buf.readString()), null);
    }

    /** Конструктор для сервера. */
    public HouseScreenHandler(int syncId, PlayerInventory inventory, ServerWorld world, BlockPos pos, HouseType type,
                              HouseDesign design) {
        this(syncId, pos, type, design, world);
        refresh();
    }

    private HouseScreenHandler(int syncId, BlockPos pos, HouseType type, HouseDesign design, @Nullable ServerWorld world) {
        super(ModScreenHandlers.HOUSE, syncId);
        this.pos = pos;
        this.type = type;
        this.design = design;
        this.world = world;
        this.items = new ArrayList<>();
        this.totals = new ArrayList<>();
        for (Map.Entry<Item, Integer> e : type.getRequirements(design).entrySet()) {
            if (items.size() >= MAX_ITEMS) {
                break;
            }
            items.add(e.getKey());
            totals.add(e.getValue());
        }
        this.props = world == null ? new ArrayPropertyDelegate(PROP_COUNT) : new PropertyDelegate() {
            @Override
            public int get(int index) {
                return values[index];
            }

            @Override
            public void set(int index, int value) {
                values[index] = value;
            }

            @Override
            public int size() {
                return PROP_COUNT;
            }
        };
        this.addProperties(props);
    }

    private static HouseType typeOrDefault(String id) {
        HouseType type = HouseType.byId(id);
        return type != null ? type : HouseType.FARMER;
    }

    // ---------- Для экрана ----------

    public HouseDesign getDesign() {
        return design;
    }

    public HouseType getHouseType() {
        return type;
    }

    public List<Item> getItems() {
        return items;
    }

    public int getTotal(int index) {
        return totals.get(index);
    }

    public int getProp(int index) {
        return props.get(index);
    }

    // ---------- Сервер: раз в секунду пересчитываем состояние ----------

    @Override
    public void sendContentUpdates() {
        if (world != null && world.getTime() % 20 == 0) {
            refresh();
        }
        super.sendContentUpdates();
    }

    private void refresh() {
        if (world == null) {
            return;
        }
        ConstructionState cs = ConstructionState.get(world.getServer());
        String dim = HouseBlock.dimensionId(world);
        ConstructionState.Site site = cs.getSite(pos, dim);
        int status;
        int missing = -1;
        int level = 0;
        ConstructionState.BuilderInfo info = null;
        if (site == null) {
            status = STATUS_NO_SITE;
            values[PROP_STEP] = 0;
        } else {
            values[PROP_STEP] = site.step;
            info = cs.getBuilder(site.builder);
            if (info == null) {
                status = cs.hasBuilders(dim) ? STATUS_QUEUE : STATUS_NO_BUILDER;
                values[PROP_QUEUE] = cs.queuePosition(site) + 1;
            } else {
                level = info.level();
                status = STATUS_WALKING;
                Entity entity = site.builder != null ? world.getEntity(site.builder) : null;
                if (entity instanceof BuilderEntity builder) {
                    level = builder.getLevel();
                    switch (builder.getBuildStatus()) {
                        case BuilderEntity.STATUS_BUILDING -> status = STATUS_BUILDING;
                        case BuilderEntity.STATUS_MISSING -> {
                            status = STATUS_MISSING;
                            Item item = builder.getMissingItem();
                            missing = item != null ? Registries.ITEM.getRawId(item) : -1;
                        }
                        default -> status = STATUS_WALKING;
                    }
                }
            }
        }
        if (info == null) {
            info = cs.anyBuilder(dim);
        }
        values[PROP_STATUS] = status;
        values[PROP_MISSING] = missing;
        values[PROP_LEVEL] = level;

        // Что лежит в сундуках строителя
        Map<Item, Integer> stock = info != null
                ? BuilderEntity.countItems(world, withBaseStorage(BuilderEntity.findChests(world, info.home()))) : Map.of();
        for (int i = 0; i < MAX_ITEMS; i++) {
            values[PROP_AVAILABLE + i] = i < items.size()
                    ? Math.min(Short.MAX_VALUE, stock.getOrDefault(items.get(i), 0)) : 0;
        }
    }

    /** Сундуки строителя + склад Базы (строитель берёт материалы и оттуда). */
    private List<BlockPos> withBaseStorage(List<BlockPos> chests) {
        List<BlockPos> all = new ArrayList<>(chests);
        if (world != null) {
            for (BlockPos pos : com.lesha_top1972.basedefense.block.BaseStorage.chests(world)) {
                if (!all.contains(pos)) {
                    all.add(pos);
                }
            }
        }
        return all;
    }

    /** Сколько ещё нужно каждого материала, если стройка дошла до шага step (работает и на клиенте). */
    public Map<Item, Integer> remainingFrom(int step) {
        return BuildCost.count(type.getSteps(design), step, type.getPlan(design).getOrigin());
    }

    @Override
    public ItemStack quickMove(PlayerEntity player, int slot) {
        return ItemStack.EMPTY;
    }

    @Override
    public boolean canUse(PlayerEntity player) {
        return player.getWorld().getBlockState(pos).getBlock() instanceof HouseBlock
                && player.squaredDistanceTo(Vec3d.ofCenter(pos)) < 8 * 8;
    }
}

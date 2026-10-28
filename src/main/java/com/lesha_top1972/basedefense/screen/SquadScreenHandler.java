package com.lesha_top1972.basedefense.screen;

import com.lesha_top1972.basedefense.entity.ModEntities;
import com.lesha_top1972.basedefense.entity.settler.GuardEntity;
import com.lesha_top1972.basedefense.item.SummonsItem;
import com.lesha_top1972.basedefense.military.SquadState;
import net.fabricmc.fabric.api.screenhandler.v1.ExtendedScreenHandlerFactory;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.item.ItemStack;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.screen.ScreenHandler;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;
import net.minecraft.util.Hand;
import net.minecraft.util.math.BlockPos;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Меню Повестки: отряды стражников. Список стражников и отрядов собирается на сервере при открытии
 * и передаётся клиенту целиком; клиент меняет свою копию сразу (без ожидания), сервер повторяет
 * то же действие и проверяет. Кнопки:
 *   0..2 — выбрать отряд для Повестки; 10..12 — отозвать отряд с точки (снова патруль);
 *   100 + номер стражника × 4 + s — s = 0..2 записать в отряд, 3 — выписать.
 */
public class SquadScreenHandler extends ScreenHandler {

    public static final int BTN_SELECT = 0;
    public static final int BTN_RECALL = 10;
    public static final int BTN_ASSIGN = 100;
    /** Больше стражников в списке не показываем. */
    public static final int MAX_LISTED = 60;

    /** Стражник в списке меню. */
    public static final class Entry {
        public final UUID uuid;
        public final String name;
        public final ItemStack weapon;
        public int squad;
        /** Расстояние до игрока (−1 — стражник далеко, мир его не загрузил). */
        public final int distance;

        Entry(UUID uuid, String name, ItemStack weapon, int squad, int distance) {
            this.uuid = uuid;
            this.name = name;
            this.weapon = weapon;
            this.squad = squad;
            this.distance = distance;
        }
    }

    private final Hand hand;
    private int selected;
    private final List<Entry> guards;
    @Nullable
    private final BlockPos[] points = new BlockPos[SquadState.SQUADS];

    /** Клиент. */
    public SquadScreenHandler(int syncId, PlayerInventory inventory, PacketByteBuf buf) {
        super(ModScreenHandlers.SQUAD, syncId);
        this.hand = buf.readBoolean() ? Hand.OFF_HAND : Hand.MAIN_HAND;
        this.selected = buf.readVarInt();
        for (int i = 0; i < SquadState.SQUADS; i++) {
            points[i] = buf.readBoolean() ? buf.readBlockPos() : null;
        }
        int n = buf.readVarInt();
        this.guards = new ArrayList<>(n);
        for (int i = 0; i < n; i++) {
            guards.add(new Entry(buf.readUuid(), buf.readString(), buf.readItemStack(), buf.readVarInt(), buf.readVarInt()));
        }
    }

    /** Сервер. */
    private SquadScreenHandler(int syncId, Hand hand, int selected, List<Entry> guards, BlockPos[] points) {
        super(ModScreenHandlers.SQUAD, syncId);
        this.hand = hand;
        this.selected = selected;
        this.guards = guards;
        System.arraycopy(points, 0, this.points, 0, points.length);
    }

    /** Открыть меню (сервер): собрать отряды и стражников рядом. */
    public static void open(ServerPlayerEntity player, Hand hand) {
        ServerWorld world = player.getServerWorld();
        SquadState state = SquadState.get(player.getServer());
        String dim = world.getRegistryKey().getValue().toString();
        Map<UUID, GuardEntity> loaded = new HashMap<>();
        for (GuardEntity g : world.getEntitiesByType(ModEntities.GUARD, g -> g.isAlive())) {
            loaded.put(g.getUuid(), g);
        }
        List<Entry> list = new ArrayList<>();
        // Сначала участники отрядов (в том числе далёкие)
        for (int s = 0; s < SquadState.SQUADS; s++) {
            for (Map.Entry<UUID, String> m : state.squad(s).members().entrySet()) {
                GuardEntity g = loaded.remove(m.getKey());
                list.add(g != null ? entry(g, s, player)
                        : new Entry(m.getKey(), m.getValue(), ItemStack.EMPTY, s, -1));
            }
        }
        // Потом свободные — ближние раньше
        List<GuardEntity> free = new ArrayList<>(loaded.values());
        free.sort(Comparator.comparingDouble(g -> player.squaredDistanceTo(g)));
        for (GuardEntity g : free) {
            if (list.size() >= MAX_LISTED) {
                break;
            }
            list.add(entry(g, -1, player));
        }
        BlockPos[] points = new BlockPos[SquadState.SQUADS];
        for (int s = 0; s < SquadState.SQUADS; s++) {
            SquadState.Squad sq = state.squad(s);
            points[s] = sq.point() != null && sq.dimension().equals(dim) ? sq.point() : null;
        }
        int selected = SummonsItem.getSquad(player.getStackInHand(hand));
        player.openHandledScreen(new ExtendedScreenHandlerFactory() {
            @Override
            public void writeScreenOpeningData(ServerPlayerEntity p, PacketByteBuf buf) {
                buf.writeBoolean(hand == Hand.OFF_HAND);
                buf.writeVarInt(selected);
                for (BlockPos pos : points) {
                    buf.writeBoolean(pos != null);
                    if (pos != null) {
                        buf.writeBlockPos(pos);
                    }
                }
                buf.writeVarInt(list.size());
                for (Entry e : list) {
                    buf.writeUuid(e.uuid);
                    buf.writeString(e.name);
                    buf.writeItemStack(e.weapon);
                    buf.writeVarInt(e.squad);
                    buf.writeVarInt(e.distance);
                }
            }

            @Override
            public Text getDisplayName() {
                return Text.translatable("container.basedefense.squads");
            }

            @Override
            public ScreenHandler createMenu(int syncId, PlayerInventory inventory, PlayerEntity p) {
                return new SquadScreenHandler(syncId, hand, selected, list, points);
            }
        });
    }

    private static Entry entry(GuardEntity g, int squad, ServerPlayerEntity player) {
        return new Entry(g.getUuid(), g.getName().getString(), g.getMainHandStack().copy(), squad,
                (int) Math.round(Math.sqrt(player.squaredDistanceTo(g))));
    }

    // ---------- Данные для экрана ----------

    public int getSelected() {
        return selected;
    }

    public List<Entry> getGuards() {
        return guards;
    }

    @Nullable
    public BlockPos getPoint(int squad) {
        return points[squad];
    }

    public int countIn(int squad) {
        int n = 0;
        for (Entry e : guards) {
            if (e.squad == squad) {
                n++;
            }
        }
        return n;
    }

    /** Применить кнопку к своей копии данных (клиент — сразу, сервер — после проверки). */
    public boolean apply(int id) {
        if (id >= BTN_SELECT && id < BTN_SELECT + SquadState.SQUADS) {
            selected = selected == id ? -1 : id;
            return true;
        }
        if (id >= BTN_RECALL && id < BTN_RECALL + SquadState.SQUADS) {
            points[id - BTN_RECALL] = null;
            return true;
        }
        if (id >= BTN_ASSIGN) {
            int index = (id - BTN_ASSIGN) / 4;
            int s = (id - BTN_ASSIGN) % 4;
            if (index >= guards.size()) {
                return false;
            }
            Entry e = guards.get(index);
            if (s == 3) {
                e.squad = -1;
                return true;
            }
            if (e.squad != s && countIn(s) >= SquadState.MAX_MEMBERS) {
                return false;
            }
            e.squad = s;
            return true;
        }
        return false;
    }

    @Override
    public boolean onButtonClick(PlayerEntity player, int id) {
        if (!(player instanceof ServerPlayerEntity sp)) {
            return false;
        }
        ItemStack stack = player.getStackInHand(hand);
        if (!(stack.getItem() instanceof SummonsItem) || !apply(id)) {
            return false;
        }
        SquadState state = SquadState.get(sp.getServer());
        if (id < BTN_RECALL) {
            SummonsItem.setSquad(stack, selected);
        } else if (id < BTN_ASSIGN) {
            state.clearPoint(id - BTN_RECALL);
        } else {
            Entry e = guards.get((id - BTN_ASSIGN) / 4);
            if (e.squad < 0) {
                state.remove(e.uuid);
            } else {
                state.assign(e.uuid, e.name, e.squad);
            }
        }
        sp.getWorld().playSound(null, sp.getBlockPos(), SoundEvents.ITEM_BOOK_PAGE_TURN, SoundCategory.PLAYERS,
                0.6f, 1.2f);
        return true;
    }

    @Override
    public ItemStack quickMove(PlayerEntity player, int slot) {
        return ItemStack.EMPTY;
    }

    @Override
    public boolean canUse(PlayerEntity player) {
        return player.getStackInHand(hand).getItem() instanceof SummonsItem;
    }
}

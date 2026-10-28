package com.lesha_top1972.basedefense.military;

import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtElement;
import net.minecraft.nbt.NbtHelper;
import net.minecraft.nbt.NbtList;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.PersistentState;
import org.jetbrains.annotations.Nullable;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Отряды стражников (общие на сервер, хранятся в мире). Отрядов {@link #SQUADS}, в каждом до
 * {@link #MAX_MEMBERS} стражников. У отряда может быть точка обороны (её ставят Повесткой):
 * стражники отряда бегут туда и держатся не дальше 8 блоков от неё. Без точки — патрулируют город.
 */
public class SquadState extends PersistentState {

    public static final int SQUADS = 3;
    public static final int MAX_MEMBERS = 3;
    private static final String SAVE_ID = "basedefense_squads";

    public static final class Squad {
        /** Участники: UUID стражника → имя (имя нужно, чтобы показать в меню, даже если стражник далеко). */
        final LinkedHashMap<UUID, String> members = new LinkedHashMap<>();
        @Nullable
        BlockPos point;
        String dimension = "";

        public Map<UUID, String> members() {
            return members;
        }

        @Nullable
        public BlockPos point() {
            return point;
        }

        public String dimension() {
            return dimension;
        }
    }

    private final Squad[] squads = new Squad[SQUADS];

    public SquadState() {
        for (int i = 0; i < SQUADS; i++) {
            squads[i] = new Squad();
        }
    }

    public static SquadState get(MinecraftServer server) {
        return server.getOverworld().getPersistentStateManager()
                .getOrCreate(SquadState::fromNbt, SquadState::new, SAVE_ID);
    }

    public Squad squad(int index) {
        return squads[index];
    }

    /** Номер отряда стражника (−1 — не в отряде). */
    public int squadOf(UUID uuid) {
        for (int i = 0; i < SQUADS; i++) {
            if (squads[i].members.containsKey(uuid)) {
                return i;
            }
        }
        return -1;
    }

    /** Переводит стражника в отряд index (из другого отряда — тоже). false — отряд полон. */
    public boolean assign(UUID uuid, String name, int index) {
        if (index < 0 || index >= SQUADS) {
            return false;
        }
        Squad target = squads[index];
        if (target.members.containsKey(uuid)) {
            return true;
        }
        if (target.members.size() >= MAX_MEMBERS) {
            return false;
        }
        remove(uuid);
        target.members.put(uuid, name);
        markDirty();
        return true;
    }

    public void remove(UUID uuid) {
        for (Squad s : squads) {
            if (s.members.remove(uuid) != null) {
                markDirty();
            }
        }
    }

    /** Стражник сменил имя (его переименовали биркой) — обновим в списке. */
    public void updateName(UUID uuid, String name) {
        for (Squad s : squads) {
            String old = s.members.get(uuid);
            if (old != null && !old.equals(name)) {
                s.members.put(uuid, name);
                markDirty();
            }
        }
    }

    public void setPoint(int index, BlockPos pos, String dimension) {
        squads[index].point = pos.toImmutable();
        squads[index].dimension = dimension;
        markDirty();
    }

    public void clearPoint(int index) {
        if (squads[index].point != null) {
            squads[index].point = null;
            markDirty();
        }
    }

    public static SquadState fromNbt(NbtCompound nbt) {
        SquadState state = new SquadState();
        NbtList list = nbt.getList("Squads", NbtElement.COMPOUND_TYPE);
        for (int i = 0; i < Math.min(SQUADS, list.size()); i++) {
            NbtCompound c = list.getCompound(i);
            Squad s = state.squads[i];
            NbtList members = c.getList("Members", NbtElement.COMPOUND_TYPE);
            for (int j = 0; j < members.size(); j++) {
                NbtCompound m = members.getCompound(j);
                if (m.containsUuid("Id")) {
                    s.members.put(m.getUuid("Id"), m.getString("Name"));
                }
            }
            if (c.contains("Point")) {
                s.point = NbtHelper.toBlockPos(c.getCompound("Point"));
                s.dimension = c.getString("Dim");
            }
        }
        return state;
    }

    @Override
    public NbtCompound writeNbt(NbtCompound nbt) {
        NbtList list = new NbtList();
        for (Squad s : squads) {
            NbtCompound c = new NbtCompound();
            NbtList members = new NbtList();
            for (Map.Entry<UUID, String> e : s.members.entrySet()) {
                NbtCompound m = new NbtCompound();
                m.putUuid("Id", e.getKey());
                m.putString("Name", e.getValue());
                members.add(m);
            }
            c.put("Members", members);
            if (s.point != null) {
                c.put("Point", NbtHelper.fromBlockPos(s.point));
                c.putString("Dim", s.dimension);
            }
            list.add(c);
        }
        nbt.put("Squads", list);
        return nbt;
    }

    /** «I», «II», «III». */
    public static String roman(int index) {
        return switch (index) {
            case 0 -> "I";
            case 1 -> "II";
            case 2 -> "III";
            default -> String.valueOf(index + 1);
        };
    }
}

package com.lesha_top1972.basedefense.research;

import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtElement;
import net.minecraft.nbt.NbtList;
import net.minecraft.nbt.NbtString;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.PersistentState;

import java.util.HashSet;
import java.util.Set;

/**
 * Хранит, что исследовано на сервере (общее для всех игроков).
 * Сохраняется вместе с миром: data/basedefense_research.dat в папке мира.
 */
public class ResearchState extends PersistentState {

    private static final String SAVE_ID = "basedefense_research";

    private final Set<String> researched = new HashSet<>();

    public static ResearchState get(MinecraftServer server) {
        return server.getOverworld().getPersistentStateManager()
                .getOrCreate(ResearchState::fromNbt, ResearchState::new, SAVE_ID);
    }

    public boolean has(Technology tech) {
        return researched.contains(tech.getId());
    }

    public void add(Technology tech) {
        researched.add(tech.getId());
        markDirty();
    }

    /** Все исследования одним числом (по биту на технологию). */
    public int mask() {
        int mask = 0;
        for (Technology tech : Technology.values()) {
            if (has(tech)) {
                mask |= tech.bit();
            }
        }
        return mask;
    }

    private static ResearchState fromNbt(NbtCompound nbt) {
        ResearchState state = new ResearchState();
        readList(nbt.getList("Researched", NbtElement.STRING_TYPE), state.researched);
        // Старый формат (исследования у каждого игрока свои) — объединяем в общие
        NbtCompound players = nbt.getCompound("Players");
        for (String key : players.getKeys()) {
            readList(players.getList(key, NbtElement.STRING_TYPE), state.researched);
        }
        return state;
    }

    private static void readList(NbtList list, Set<String> into) {
        for (int i = 0; i < list.size(); i++) {
            into.add(list.getString(i));
        }
    }

    @Override
    public NbtCompound writeNbt(NbtCompound nbt) {
        NbtList list = new NbtList();
        researched.forEach(id -> list.add(NbtString.of(id)));
        nbt.put("Researched", list);
        return nbt;
    }
}

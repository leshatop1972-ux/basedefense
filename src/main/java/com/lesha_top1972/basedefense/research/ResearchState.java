package com.lesha_top1972.basedefense.research;

import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtElement;
import net.minecraft.nbt.NbtList;
import net.minecraft.nbt.NbtString;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.PersistentState;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Хранит, кто что исследовал. Сохраняется вместе с миром
 * (файл data/basedefense_research.dat в папке мира).
 */
public class ResearchState extends PersistentState {

    private static final String SAVE_ID = "basedefense_research";

    private final Map<UUID, Set<String>> researched = new HashMap<>();

    public static ResearchState get(MinecraftServer server) {
        return server.getOverworld().getPersistentStateManager()
                .getOrCreate(ResearchState::fromNbt, ResearchState::new, SAVE_ID);
    }

    public boolean has(UUID player, Technology tech) {
        Set<String> set = researched.get(player);
        return set != null && set.contains(tech.getId());
    }

    public void add(UUID player, Technology tech) {
        researched.computeIfAbsent(player, k -> new HashSet<>()).add(tech.getId());
        markDirty();
    }

    /** Все исследования игрока одним числом (по биту на технологию). */
    public int mask(UUID player) {
        int mask = 0;
        for (Technology tech : Technology.values()) {
            if (has(player, tech)) {
                mask |= tech.bit();
            }
        }
        return mask;
    }

    private static ResearchState fromNbt(NbtCompound nbt) {
        ResearchState state = new ResearchState();
        NbtCompound players = nbt.getCompound("Players");
        for (String key : players.getKeys()) {
            UUID uuid;
            try {
                uuid = UUID.fromString(key);
            } catch (IllegalArgumentException e) {
                continue;
            }
            Set<String> set = new HashSet<>();
            NbtList list = players.getList(key, NbtElement.STRING_TYPE);
            for (int i = 0; i < list.size(); i++) {
                set.add(list.getString(i));
            }
            state.researched.put(uuid, set);
        }
        return state;
    }

    @Override
    public NbtCompound writeNbt(NbtCompound nbt) {
        NbtCompound players = new NbtCompound();
        researched.forEach((uuid, set) -> {
            NbtList list = new NbtList();
            set.forEach(id -> list.add(NbtString.of(id)));
            players.put(uuid.toString(), list);
        });
        nbt.put("Players", players);
        return nbt;
    }
}

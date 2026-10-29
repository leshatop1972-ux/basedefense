package com.lesha_top1972.basedefense.artifact;

import net.minecraft.nbt.NbtCompound;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.PersistentState;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Какие рецепты стола амулетов игрок уже хоть раз скрафтил (тогда в меню видны шансы).
 * Хранится на сервере: игрок → битовая маска номеров рецептов ({@link AmuletRecipes}).
 */
public class AmuletKnowledge extends PersistentState {

    private static final String SAVE_ID = "basedefense_amulet_recipes";

    private final Map<UUID, Integer> known = new HashMap<>();

    public static AmuletKnowledge get(MinecraftServer server) {
        return server.getOverworld().getPersistentStateManager()
                .getOrCreate(AmuletKnowledge::fromNbt, AmuletKnowledge::new, SAVE_ID);
    }

    public int mask(UUID player) {
        return known.getOrDefault(player, 0);
    }

    public boolean knows(UUID player, int recipe) {
        return recipe >= 0 && (mask(player) & (1 << recipe)) != 0;
    }

    /** true — рецепт новый для игрока. */
    public boolean learn(UUID player, int recipe) {
        if (recipe < 0 || recipe >= AmuletRecipes.MAX_RECIPES || knows(player, recipe)) {
            return false;
        }
        known.put(player, mask(player) | (1 << recipe));
        markDirty();
        return true;
    }

    @Override
    public NbtCompound writeNbt(NbtCompound nbt) {
        NbtCompound players = new NbtCompound();
        known.forEach((id, mask) -> players.putInt(id.toString(), mask));
        nbt.put("Players", players);
        return nbt;
    }

    public static AmuletKnowledge fromNbt(NbtCompound nbt) {
        AmuletKnowledge state = new AmuletKnowledge();
        NbtCompound players = nbt.getCompound("Players");
        for (String key : players.getKeys()) {
            try {
                state.known.put(UUID.fromString(key), players.getInt(key));
            } catch (IllegalArgumentException ignored) {
                // битая запись — пропускаем
            }
        }
        return state;
    }
}

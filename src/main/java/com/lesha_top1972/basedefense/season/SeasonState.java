package com.lesha_top1972.basedefense.season;

import net.minecraft.nbt.NbtCompound;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.PersistentState;

/**
 * Сдвиг календаря сезонов (в днях), хранится в сохранении мира. Номер «сезонного дня» = день мира + offset.
 * Новый мир (или старый, где сезонов ещё не было) начинается с 1‑го дня лета.
 */
public class SeasonState extends PersistentState {

    private static final String SAVE_ID = "basedefense_seasons";

    long offset;

    public static SeasonState get(MinecraftServer server) {
        return server.getOverworld().getPersistentStateManager().getOrCreate(SeasonState::fromNbt, () -> {
            SeasonState s = new SeasonState();
            s.offset = -Seasons.worldDay(server.getOverworld().getTimeOfDay());   // сегодня — 1‑й день лета
            s.markDirty();
            return s;
        }, SAVE_ID);
    }

    public static SeasonState fromNbt(NbtCompound nbt) {
        SeasonState s = new SeasonState();
        s.offset = nbt.getLong("Offset");
        return s;
    }

    @Override
    public NbtCompound writeNbt(NbtCompound nbt) {
        nbt.putLong("Offset", offset);
        return nbt;
    }
}

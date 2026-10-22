package com.lesha_top1972.basedefense.settlement;

import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtElement;
import net.minecraft.nbt.NbtList;
import net.minecraft.nbt.NbtString;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.PersistentState;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

/**
 * Поселение при Базе (одно на сервер): кто в нём живёт, запасы еды и эксплуатации,
 * склад ресурсов. Сохраняется в data/basedefense_settlement.dat в папке мира.
 */
public class SettlementState extends PersistentState {

    private static final String SAVE_ID = "basedefense_settlement";

    private final Set<UUID> settlers = new HashSet<>();
    private int food = SettlementManager.START_FOOD;
    private int upkeep = SettlementManager.START_UPKEEP;
    private int nuggets;
    private int planks;
    private int cobblestone;
    /** Номер последнего обработанного игрового дня (-1 — ещё не начинали). */
    private long lastDay = -1;

    public static SettlementState get(MinecraftServer server) {
        return server.getOverworld().getPersistentStateManager()
                .getOrCreate(SettlementState::fromNbt, SettlementState::new, SAVE_ID);
    }

    // ---------- Жители ----------

    public void addSettler(UUID id) {
        if (settlers.add(id)) {
            markDirty();
        }
    }

    public void removeSettler(UUID id) {
        if (settlers.remove(id)) {
            markDirty();
        }
    }

    public int getSettlerCount() {
        return settlers.size();
    }

    // ---------- Счётчики ----------

    public int getFood() {
        return food;
    }

    public void setFood(int food) {
        this.food = food;
        markDirty();
    }

    public int getUpkeep() {
        return upkeep;
    }

    public void setUpkeep(int upkeep) {
        this.upkeep = upkeep;
        markDirty();
    }

    /** Жители замерли: один из запасов опустился до -20. */
    public boolean isFrozen() {
        return food <= SettlementManager.FREEZE_AT || upkeep <= SettlementManager.FREEZE_AT;
    }

    // ---------- Склад ----------

    public int getNuggets() {
        return nuggets;
    }

    public void setNuggets(int nuggets) {
        this.nuggets = nuggets;
        markDirty();
    }

    public int getPlanks() {
        return planks;
    }

    public void setPlanks(int planks) {
        this.planks = planks;
        markDirty();
    }

    public int getCobblestone() {
        return cobblestone;
    }

    public void setCobblestone(int cobblestone) {
        this.cobblestone = cobblestone;
        markDirty();
    }

    // ---------- Дни ----------

    public long getLastDay() {
        return lastDay;
    }

    public void setLastDay(long lastDay) {
        this.lastDay = lastDay;
        markDirty();
    }

    // ---------- Сохранение ----------

    private static SettlementState fromNbt(NbtCompound nbt) {
        SettlementState state = new SettlementState();
        NbtList list = nbt.getList("Settlers", NbtElement.STRING_TYPE);
        for (int i = 0; i < list.size(); i++) {
            try {
                state.settlers.add(UUID.fromString(list.getString(i)));
            } catch (IllegalArgumentException ignored) {
                // битая запись — пропускаем
            }
        }
        if (nbt.contains("Food")) {
            state.food = nbt.getInt("Food");
        }
        if (nbt.contains("Upkeep")) {
            state.upkeep = nbt.getInt("Upkeep");
        }
        state.nuggets = nbt.getInt("Nuggets");
        state.planks = nbt.getInt("Planks");
        state.cobblestone = nbt.getInt("Cobblestone");
        state.lastDay = nbt.contains("LastDay") ? nbt.getLong("LastDay") : -1;
        return state;
    }

    @Override
    public NbtCompound writeNbt(NbtCompound nbt) {
        NbtList list = new NbtList();
        settlers.forEach(id -> list.add(NbtString.of(id.toString())));
        nbt.put("Settlers", list);
        nbt.putInt("Food", food);
        nbt.putInt("Upkeep", upkeep);
        nbt.putInt("Nuggets", nuggets);
        nbt.putInt("Planks", planks);
        nbt.putInt("Cobblestone", cobblestone);
        nbt.putLong("LastDay", lastDay);
        return nbt;
    }
}

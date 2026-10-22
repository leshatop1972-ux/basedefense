package com.lesha_top1972.basedefense.settlement;

import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtElement;
import net.minecraft.nbt.NbtList;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.PersistentState;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Поселение при Базе (одно на сервер): кто в нём живёт, запасы еды и эксплуатации,
 * склад ресурсов. Сохраняется в data/basedefense_settlement.dat в папке мира.
 */
public class SettlementState extends PersistentState {

    private static final String SAVE_ID = "basedefense_settlement";

    /** Житель → сколько еды он тратит в день (зависит от работы и уровня). */
    private final Map<UUID, Integer> settlers = new HashMap<>();
    private int food = SettlementManager.START_FOOD;
    private int upkeep = SettlementManager.START_UPKEEP;
    private int nuggets;
    private int planks;
    private int cobblestone;
    private int denseWood;
    private int constructors;
    /** Номер последнего обработанного игрового дня (-1 — ещё не начинали). */
    private long lastDay = -1;

    public static SettlementState get(MinecraftServer server) {
        return server.getOverworld().getPersistentStateManager()
                .getOrCreate(SettlementState::fromNbt, SettlementState::new, SAVE_ID);
    }

    // ---------- Жители ----------

    /** Записать жителя в поселение (или обновить, сколько еды он тратит в день). */
    public void addSettler(UUID id, int foodPerDay) {
        Integer old = settlers.put(id, foodPerDay);
        if (old == null || old != foodPerDay) {
            markDirty();
        }
    }

    public void removeSettler(UUID id) {
        if (settlers.remove(id) != null) {
            markDirty();
        }
    }

    public int getSettlerCount() {
        return settlers.size();
    }

    /** Сколько еды всё поселение тратит за день. */
    public int getDailyFood() {
        int sum = 0;
        for (int food : settlers.values()) {
            sum += food;
        }
        return sum;
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

    public int getDenseWood() {
        return denseWood;
    }

    public void setDenseWood(int denseWood) {
        this.denseWood = denseWood;
        markDirty();
    }

    /** Конструкторские блоки на складе. */
    public int getConstructors() {
        return constructors;
    }

    public void setConstructors(int constructors) {
        this.constructors = constructors;
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
        // Новый формат: {Id, Food}
        NbtList list = nbt.getList("SettlerList", NbtElement.COMPOUND_TYPE);
        for (int i = 0; i < list.size(); i++) {
            NbtCompound entry = list.getCompound(i);
            if (entry.containsUuid("Id")) {
                state.settlers.put(entry.getUuid("Id"), entry.getInt("Food"));
            }
        }
        // Старый формат: просто список UUID
        NbtList old = nbt.getList("Settlers", NbtElement.STRING_TYPE);
        for (int i = 0; i < old.size(); i++) {
            try {
                state.settlers.putIfAbsent(UUID.fromString(old.getString(i)), SettlementManager.FOOD_PER_SETTLER);
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
        state.denseWood = nbt.getInt("DenseWood");
        state.constructors = nbt.getInt("Constructors");
        state.lastDay = nbt.contains("LastDay") ? nbt.getLong("LastDay") : -1;
        return state;
    }

    @Override
    public NbtCompound writeNbt(NbtCompound nbt) {
        NbtList list = new NbtList();
        settlers.forEach((id, food) -> {
            NbtCompound entry = new NbtCompound();
            entry.putUuid("Id", id);
            entry.putInt("Food", food);
            list.add(entry);
        });
        nbt.put("SettlerList", list);
        nbt.putInt("Food", food);
        nbt.putInt("Upkeep", upkeep);
        nbt.putInt("Nuggets", nuggets);
        nbt.putInt("Planks", planks);
        nbt.putInt("Cobblestone", cobblestone);
        nbt.putInt("DenseWood", denseWood);
        nbt.putInt("Constructors", constructors);
        nbt.putLong("LastDay", lastDay);
        return nbt;
    }
}

package com.lesha_top1972.basedefense.director;

import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtElement;
import net.minecraft.nbt.NbtList;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.PersistentState;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Состояние «Режиссёра мира» (одно на сервер, общее для всех игроков): день, акт, шкала, событие дня,
 * журнал прошлых дней и кому уже показана утренняя заставка. Файл data/basedefense_director.dat.
 */
public class DirectorState extends PersistentState {

    private static final String SAVE_ID = "basedefense_director";
    public static final int HISTORY = 10;

    /** День в журнале: номер и событие. */
    public record Entry(int day, DirectorEvent event) {
    }

    /** Последний обработанный день (-1 — ещё не начинали). */
    long lastDay = -1;
    int act = 1;
    int points;
    DirectorEvent today = DirectorEvent.PEACE;
    /** День, на который выпало событие {@link #today}. */
    long todayDay = -1;
    final List<Entry> history = new ArrayList<>();
    /** Игрок → последний день, за который ему показали утреннюю заставку. */
    final Map<UUID, Long> shown = new HashMap<>();

    // ---------- Волна (WaveManager) ----------
    /** День текущей/последней волны (-1 — не было). */
    long waveDay = -1;
    DirectorEvent waveEvent = DirectorEvent.WAVE;
    /** Сколько частей уже вышло. */
    int waveNext;
    int waveTotal;
    boolean waveOver = true;
    boolean bossDefeated;
    /** Ещё не вышедшие части: id сущностей. */
    final List<List<String>> waveParts = new ArrayList<>();
    /** Живые мобы волны (по ним — «осталось врагов» и победа). */
    final java.util.Set<UUID> waveMobs = new java.util.HashSet<>();

    // ---------- События мира (WorldEvents) ----------
    /** День, когда уже упал метеорит. */
    long meteorDay = -1;

    public static DirectorState get(MinecraftServer server) {
        return server.getOverworld().getPersistentStateManager()
                .getOrCreate(DirectorState::fromNbt, DirectorState::new, SAVE_ID);
    }

    public int act() {
        return act;
    }

    public int points() {
        return points;
    }

    public DirectorEvent today() {
        return today;
    }

    public long todayDay() {
        return todayDay;
    }

    public List<Entry> history() {
        return history;
    }

    void addHistory(int day, DirectorEvent e) {
        history.removeIf(h -> h.day() == day);
        history.add(0, new Entry(day, e));
        while (history.size() > HISTORY) {
            history.remove(history.size() - 1);
        }
    }

    @Override
    public NbtCompound writeNbt(NbtCompound nbt) {
        nbt.putLong("LastDay", lastDay);
        nbt.putInt("Act", act);
        nbt.putInt("Points", points);
        nbt.putString("Today", today.id);
        nbt.putLong("TodayDay", todayDay);
        NbtList list = new NbtList();
        for (Entry h : history) {
            NbtCompound c = new NbtCompound();
            c.putInt("Day", h.day());
            c.putString("Event", h.event().id);
            list.add(c);
        }
        nbt.put("History", list);
        NbtList seen = new NbtList();
        for (Map.Entry<UUID, Long> s : shown.entrySet()) {
            NbtCompound c = new NbtCompound();
            c.putUuid("Id", s.getKey());
            c.putLong("Day", s.getValue());
            seen.add(c);
        }
        nbt.put("Shown", seen);
        nbt.putLong("WaveDay", waveDay);
        nbt.putString("WaveEvent", waveEvent.id);
        nbt.putInt("WaveNext", waveNext);
        nbt.putInt("WaveTotal", waveTotal);
        nbt.putBoolean("WaveOver", waveOver);
        nbt.putBoolean("BossDefeated", bossDefeated);
        NbtList parts = new NbtList();
        for (List<String> part : waveParts) {
            NbtList one = new NbtList();
            for (String id : part) {
                one.add(net.minecraft.nbt.NbtString.of(id));
            }
            parts.add(one);
        }
        nbt.put("WaveParts", parts);
        NbtList mobs = new NbtList();
        for (UUID id : waveMobs) {
            mobs.add(net.minecraft.nbt.NbtHelper.fromUuid(id));
        }
        nbt.put("WaveMobs", mobs);
        nbt.putLong("MeteorDay", meteorDay);
        return nbt;
    }

    public static DirectorState fromNbt(NbtCompound nbt) {
        DirectorState s = new DirectorState();
        s.lastDay = nbt.getLong("LastDay");
        s.act = Math.max(1, nbt.getInt("Act"));
        s.points = nbt.getInt("Points");
        DirectorEvent t = DirectorEvent.byId(nbt.getString("Today"));
        s.today = t == null ? DirectorEvent.PEACE : t;
        s.todayDay = nbt.getLong("TodayDay");
        NbtList list = nbt.getList("History", NbtElement.COMPOUND_TYPE);
        for (int i = 0; i < list.size(); i++) {
            NbtCompound c = list.getCompound(i);
            DirectorEvent e = DirectorEvent.byId(c.getString("Event"));
            if (e != null) {
                s.history.add(new Entry(c.getInt("Day"), e));
            }
        }
        NbtList seen = nbt.getList("Shown", NbtElement.COMPOUND_TYPE);
        for (int i = 0; i < seen.size(); i++) {
            NbtCompound c = seen.getCompound(i);
            if (c.containsUuid("Id")) {
                s.shown.put(c.getUuid("Id"), c.getLong("Day"));
            }
        }
        s.waveDay = nbt.contains("WaveDay") ? nbt.getLong("WaveDay") : -1;
        DirectorEvent we = DirectorEvent.byId(nbt.getString("WaveEvent"));
        s.waveEvent = we == null ? DirectorEvent.WAVE : we;
        s.waveNext = nbt.getInt("WaveNext");
        s.waveTotal = nbt.getInt("WaveTotal");
        s.waveOver = !nbt.contains("WaveOver") || nbt.getBoolean("WaveOver");
        s.bossDefeated = nbt.getBoolean("BossDefeated");
        NbtList parts = nbt.getList("WaveParts", NbtElement.LIST_TYPE);
        for (int i = 0; i < parts.size(); i++) {
            NbtList one = parts.getList(i);
            List<String> part = new ArrayList<>();
            for (int k = 0; k < one.size(); k++) {
                part.add(one.getString(k));
            }
            s.waveParts.add(part);
        }
        NbtList mobs = nbt.getList("WaveMobs", NbtElement.INT_ARRAY_TYPE);
        for (int i = 0; i < mobs.size(); i++) {
            s.waveMobs.add(net.minecraft.nbt.NbtHelper.toUuid(mobs.get(i)));
        }
        s.meteorDay = nbt.contains("MeteorDay") ? nbt.getLong("MeteorDay") : -1;
        return s;
    }
}

package com.lesha_top1972.basedefense.settlement;

import com.lesha_top1972.basedefense.block.BaseLocationState;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.item.ItemStack;
import net.minecraft.server.MinecraftServer;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;

/**
 * Потребности поселения. Каждый игровой день (на рассвете) каждый житель тратит:
 * - очки запаса еды: 10, а работающий — больше (см. SettlerEntity#getFoodPerDay);
 * - 2 железных самородка, 5 досок и 5 булыжника со склада Базы
 *   (с 6 жителей ещё 1 конструкторский блок в день на всё поселение;
 *    больше 13 жителей — 4 самородка и 2 плотного дерева вместо досок).
 *   Если ресурсов хватает — счётчик эксплуатации немного растёт (до 30),
 *   если нет — падает на 10 за каждого необеспеченного жителя.
 * Если любой счётчик дошёл до -20, все жители замирают.
 */
public final class SettlementManager {

    // ---------- Настройки (можно менять) ----------
    public static final int START_FOOD = 20;          // запас еды в новом мире
    public static final int START_UPKEEP = 20;        // запас эксплуатации в новом мире
    public static final int FOOD_PER_SETTLER = 6;     // еды в день на неработающего жителя
    public static final int UPKEEP_PER_SETTLER = 10;  // эксплуатации в день на жителя (если нет ресурсов)
    public static final int UPKEEP_GROWTH = 5;        // рост эксплуатации за день, если ресурсов хватило
    public static final int UPKEEP_MAX = 30;
    public static final int FOOD_MAX = 1000;
    public static final int FREEZE_AT = -20;          // ниже счётчики не опускаются, жители замирают
    public static final int STORAGE_MAX = 9999;       // максимум каждого ресурса на складе

    public static final int NUGGETS_PER_SETTLER = 2;
    public static final int PLANKS_PER_SETTLER = 5;
    public static final int COBBLE_PER_SETTLER = 5;

    // Большое поселение требует больше
    public static final int CONSTRUCTOR_FROM = 6;          // с 6 жителей: +1 конструкторский блок в день
    public static final int BIG_SETTLEMENT_FROM = 14;      // больше 13 жителей:
    public static final int NUGGETS_PER_SETTLER_BIG = 4;   //   самородков 4 на жителя
    public static final int DENSE_WOOD_PER_SETTLER = 2;    //   и 2 плотного дерева вместо досок

    private static final long DAY_TICKS = 24000L;
    private static final int MAX_DAYS_AT_ONCE = 7;

    private SettlementManager() {
    }

    public static void init() {
        ServerTickEvents.END_SERVER_TICK.register(server -> {
            if (server.getTicks() % 20 == 0) {
                checkNewDay(server);
            }
        });
    }

    /** Новый день наступает на рассвете (когда время мира проходит отметку 0). Сон тоже считается. */
    private static void checkNewDay(MinecraftServer server) {
        SettlementState state = SettlementState.get(server);
        long day = server.getOverworld().getTimeOfDay() / DAY_TICKS;
        long last = state.getLastDay();

        if (last < 0 || day < last) {      // первый запуск или время перевели назад командой
            state.setLastDay(day);
            return;
        }
        if (day == last) {
            return;
        }
        long days = Math.min(day - last, MAX_DAYS_AT_ONCE);
        state.setLastDay(day);

        if (!BaseLocationState.get(server).hasBase() || state.getSettlerCount() == 0) {
            return;                        // нет Базы или жителей — ничего не тратится
        }
        boolean wasFrozen = state.isFrozen();
        for (int i = 0; i < days; i++) {
            consumeOneDay(state);
        }
        announce(server, state, wasFrozen);
    }

    private static void consumeOneDay(SettlementState state) {
        int settlers = state.getSettlerCount();

        // Еда
        state.setFood(Math.max(FREEZE_AT, state.getFood() - state.getDailyFood()));

        // Эксплуатация: сколько жителей можно обеспечить ресурсами со склада
        int nuggetsEach = nuggetsPerSettler(settlers);
        boolean dense = usesDenseWood(settlers);
        int woodEach = dense ? DENSE_WOOD_PER_SETTLER : PLANKS_PER_SETTLER;
        int woodHave = dense ? state.getDenseWood() : state.getPlanks();
        int served = Math.min(settlers, Math.min(state.getNuggets() / nuggetsEach,
                Math.min(woodHave / woodEach, state.getCobblestone() / COBBLE_PER_SETTLER)));
        state.setNuggets(state.getNuggets() - served * nuggetsEach);
        if (dense) {
            state.setDenseWood(state.getDenseWood() - served * woodEach);
        } else {
            state.setPlanks(state.getPlanks() - served * woodEach);
        }
        state.setCobblestone(state.getCobblestone() - served * COBBLE_PER_SETTLER);

        // Конструкторский блок — один на всё поселение в день (с 6 жителей)
        boolean constructorMissing = false;
        int constructorsNeeded = constructorsPerDay(settlers);
        if (constructorsNeeded > 0) {
            if (state.getConstructors() >= constructorsNeeded) {
                state.setConstructors(state.getConstructors() - constructorsNeeded);
            } else {
                constructorMissing = true;
            }
        }

        int unserved = settlers - served;
        if (unserved > 0 || constructorMissing) {
            int loss = UPKEEP_PER_SETTLER * unserved + (constructorMissing ? UPKEEP_PER_SETTLER : 0);
            state.setUpkeep(Math.max(FREEZE_AT, state.getUpkeep() - loss));
        } else {
            state.setUpkeep(Math.min(UPKEEP_MAX, state.getUpkeep() + UPKEEP_GROWTH));
        }
    }

    // ---------- Сколько нужно в день (используется и на клиенте) ----------

    public static int nuggetsPerSettler(int settlers) {
        return settlers >= BIG_SETTLEMENT_FROM ? NUGGETS_PER_SETTLER_BIG : NUGGETS_PER_SETTLER;
    }

    /** Больше 13 жителей — вместо досок нужно плотное дерево. */
    public static boolean usesDenseWood(int settlers) {
        return settlers >= BIG_SETTLEMENT_FROM;
    }

    public static int constructorsPerDay(int settlers) {
        return settlers >= CONSTRUCTOR_FROM ? 1 : 0;
    }

    /** Сообщение в чат всем игрокам, если с запасами плохо. */
    private static void announce(MinecraftServer server, SettlementState state, boolean wasFrozen) {
        Text msg = null;
        if (state.isFrozen()) {
            msg = Text.translatable("message.basedefense.settlement.frozen").formatted(Formatting.RED);
        } else if (wasFrozen) {
            msg = Text.translatable("message.basedefense.settlement.unfrozen").formatted(Formatting.GREEN);
        } else if (state.getFood() < 0 || state.getUpkeep() < 0) {
            msg = Text.translatable("message.basedefense.settlement.low",
                    state.getFood(), state.getUpkeep()).formatted(Formatting.GOLD);
        }
        if (msg != null) {
            server.getPlayerManager().broadcast(msg, false);
        }
    }

    public static boolean isFrozen(MinecraftServer server) {
        return SettlementState.get(server).isFrozen();
    }

    /**
     * Сдать предметы в Базу. Возвращает, сколько штук из стопки забрано
     * (остаток — если склад или запас еды заполнен).
     */
    public static int deposit(MinecraftServer server, ItemStack stack) {
        if (stack.isEmpty()) {
            return 0;
        }
        SettlementState state = SettlementState.get(server);
        boolean wasFrozen = state.isFrozen();
        int count = stack.getCount();
        int used = 0;

        int food = SettlementItems.foodValue(stack);
        int nugget = SettlementItems.nuggetValue(stack);
        if (food > 0) {
            int space = FOOD_MAX - state.getFood();
            used = Math.min(count, Math.max(0, (space + food - 1) / food));
            state.setFood(Math.min(FOOD_MAX, state.getFood() + used * food));
        } else if (nugget > 0) {
            int space = STORAGE_MAX - state.getNuggets();
            used = Math.min(count, Math.max(0, space / nugget));
            state.setNuggets(state.getNuggets() + used * nugget);
        } else if (SettlementItems.isPlanks(stack)) {
            used = Math.min(count, Math.max(0, STORAGE_MAX - state.getPlanks()));
            state.setPlanks(state.getPlanks() + used);
        } else if (SettlementItems.isCobblestone(stack)) {
            used = Math.min(count, Math.max(0, STORAGE_MAX - state.getCobblestone()));
            state.setCobblestone(state.getCobblestone() + used);
        } else if (SettlementItems.isDenseWood(stack)) {
            used = Math.min(count, Math.max(0, STORAGE_MAX - state.getDenseWood()));
            state.setDenseWood(state.getDenseWood() + used);
        } else if (SettlementItems.isConstructor(stack)) {
            used = Math.min(count, Math.max(0, STORAGE_MAX - state.getConstructors()));
            state.setConstructors(state.getConstructors() + used);
        }

        if (wasFrozen && !state.isFrozen()) {
            server.getPlayerManager().broadcast(
                    Text.translatable("message.basedefense.settlement.unfrozen").formatted(Formatting.GREEN), false);
        }
        return used;
    }
}

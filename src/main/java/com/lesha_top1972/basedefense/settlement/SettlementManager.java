package com.lesha_top1972.basedefense.settlement;

import com.lesha_top1972.basedefense.block.BaseLocationState;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.item.ItemStack;
import net.minecraft.server.MinecraftServer;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;

/**
 * Потребности поселения — только еда. Каждый игровой день (на рассвете)
 * каждый житель тратит очки запаса еды (см. SettlerEntity#getFoodPerDay).
 * Еду игрок кладёт в Базу (вкладка «Потребности»).
 * Если запас еды дошёл до -20, все жители замирают.
 */
public final class SettlementManager {

    // ---------- Настройки (можно менять) ----------
    public static final int START_FOOD = 20;          // запас еды в новом мире
    public static final int FOOD_PER_SETTLER = 6;     // еды в день на жителя без профессии
    public static final int FOOD_MAX = 1000;
    public static final int FREEZE_AT = -20;          // ниже запас не опускается, жители замирают

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
            state.setFood(Math.max(FREEZE_AT, state.getFood() - state.getDailyFood()));
        }
        announce(server, state, wasFrozen);
    }

    /** Сообщение в чат всем игрокам, если с едой плохо. */
    private static void announce(MinecraftServer server, SettlementState state, boolean wasFrozen) {
        Text msg = null;
        if (state.isFrozen()) {
            msg = Text.translatable("message.basedefense.settlement.frozen").formatted(Formatting.RED);
        } else if (wasFrozen) {
            msg = Text.translatable("message.basedefense.settlement.unfrozen").formatted(Formatting.GREEN);
        } else if (state.getFood() < 0) {
            msg = Text.translatable("message.basedefense.settlement.low", state.getFood()).formatted(Formatting.GOLD);
        }
        if (msg != null) {
            server.getPlayerManager().broadcast(msg, false);
        }
    }

    public static boolean isFrozen(MinecraftServer server) {
        return SettlementState.get(server).isFrozen();
    }

    /**
     * Сдать еду в Базу. Возвращает, сколько штук из стопки забрано
     * (остаток — если запас еды заполнен).
     */
    public static int deposit(MinecraftServer server, ItemStack stack) {
        int food = SettlementItems.foodValue(stack);
        if (stack.isEmpty() || food <= 0) {
            return 0;
        }
        SettlementState state = SettlementState.get(server);
        boolean wasFrozen = state.isFrozen();
        int space = FOOD_MAX - state.getFood();
        int used = Math.min(stack.getCount(), Math.max(0, (space + food - 1) / food));
        state.setFood(Math.min(FOOD_MAX, state.getFood() + used * food));

        if (wasFrozen && !state.isFrozen()) {
            server.getPlayerManager().broadcast(
                    Text.translatable("message.basedefense.settlement.unfrozen").formatted(Formatting.GREEN), false);
        }
        return used;
    }
}

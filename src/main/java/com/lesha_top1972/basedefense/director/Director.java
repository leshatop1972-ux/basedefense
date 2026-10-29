package com.lesha_top1972.basedefense.director;

import com.lesha_top1972.basedefense.Basedefense;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.PacketByteBufs;
import net.fabricmc.fabric.api.networking.v1.PlayerLookup;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.command.CommandManager;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.Identifier;

/**
 * «Режиссёр мира»: считает дни, с 4-го утра каждое утро бросает рулетку событий (DirectorTables), копит шкалу
 * (100 за день, 2 за опыт, 50 за жителя); заполнилась до 1000 — на следующее утро боссовая волна.
 * Всё на сервере; клиенту уходит один пакет {@link #SYNC} (состояние + флаг «показать утреннюю заставку»).
 * Само выполнение событий (волны, погода и т.д.) — следующие этапы; здесь — объявление и учёт.
 */
public final class Director {

    public static final Identifier SYNC = new Identifier(Basedefense.MOD_ID, "director_sync");
    public static final long DAY_TICKS = 24000L;
    private static final int CHECK_EVERY = 20;

    private static boolean syncDirty;

    private Director() {
    }

    public static void init() {
        ServerTickEvents.END_SERVER_TICK.register(Director::tick);
        WaveManager.init();
        WorldEvents.init();
        ServerPlayConnectionEvents.JOIN.register((handler, sender, server) -> {
            DirectorState s = DirectorState.get(server);
            ServerPlayerEntity p = handler.player;
            boolean show = isActive(server) && s.todayDay == day(server)
                    && s.shown.getOrDefault(p.getUuid(), -1L) < s.todayDay;
            send(server, p, show);
        });
        CommandRegistrationCallback.EVENT.register((dispatcher, registry, env) -> dispatcher.register(
                CommandManager.literal("basedefense").requires(src -> src.hasPermissionLevel(2))
                        .then(CommandManager.literal("director")
                                .executes(Director::cmdInfo)
                                .then(CommandManager.literal("roll").executes(Director::cmdRoll))
                                .then(CommandManager.literal("wave").executes(Director::cmdWave))
                                .then(CommandManager.literal("drain").executes(Director::cmdDrain))
                                .then(CommandManager.literal("show").executes(Director::cmdShow))
                                .then(CommandManager.literal("set")
                                        .then(CommandManager.argument("event", StringArgumentType.word())
                                                .suggests((ctx, b) -> {
                                                    for (DirectorEvent e : DirectorEvent.values()) {
                                                        if (e != DirectorEvent.FLOOD) {
                                                            b.suggest(e.id);
                                                        }
                                                    }
                                                    return b.buildFuture();
                                                })
                                                .executes(Director::cmdSet)))
                                .then(CommandManager.literal("points")
                                        .then(CommandManager.argument("value",
                                                        IntegerArgumentType.integer(0, DirectorTables.SCALE_MAX))
                                                .executes(Director::cmdPoints)))
                                .then(CommandManager.literal("act")
                                        .then(CommandManager.argument("value",
                                                        IntegerArgumentType.integer(1, DirectorTables.MAX_ACT))
                                                .executes(Director::cmdAct))))));
    }

    // =====================================================================
    // Дни
    // =====================================================================

    /** Номер игрового дня, с 1 (время мира 0 — утро первого дня). */
    public static long day(MinecraftServer server) {
        return server.getOverworld().getTimeOfDay() / DAY_TICKS + 1;
    }

    /** Режиссёр работает (с 4-го дня). */
    public static boolean isActive(MinecraftServer server) {
        return day(server) >= DirectorTables.START_DAY;
    }

    private static void tick(MinecraftServer server) {
        if (server.getTicks() % CHECK_EVERY != 0) {
            return;
        }
        DirectorState s = DirectorState.get(server);
        long day = day(server);
        if (s.lastDay < 0) {
            s.lastDay = day;
            if (day >= DirectorTables.START_DAY) {
                startDay(server, s, day);
            }
            s.markDirty();
            syncDirty = true;
        } else if (day > s.lastDay) {
            // за каждый пережитый день с событиями (сон и /time add могут пропустить несколько) — очки шкалы
            int survived = 0;
            for (long d = s.lastDay + 1; d <= day; d++) {
                if (d - 1 >= DirectorTables.START_DAY) {
                    survived++;
                }
            }
            s.lastDay = day;
            s.markDirty();
            // конец прошлого дня: волна отступает, события мира заканчиваются
            WaveManager.dawn(server, s);
            WorldEvents.endDay(server, s);
            if (survived > 0) {
                addPointsRaw(server, s, survived * DirectorTables.POINTS_DAY);
            }
            if (day == DirectorTables.START_DAY - 1) {
                broadcast(server, Text.translatable("director.basedefense.msg.soon").formatted(Formatting.GOLD));
            }
            if (day >= DirectorTables.START_DAY) {
                startDay(server, s, day);
            }
            syncDirty = true;
        } else if (day < s.lastDay) {
            s.lastDay = day;                         // время перевели назад — просто запоминаем
            s.markDirty();
            syncDirty = true;
        }
        if (syncDirty) {
            syncDirty = false;
            for (ServerPlayerEntity p : PlayerLookup.all(server)) {
                send(server, p, false);
            }
        }
    }

    /** Утро: выбрать событие дня и показать его всем. */
    private static void startDay(MinecraftServer server, DirectorState s, long day) {
        DirectorEvent e = s.points >= DirectorTables.SCALE_MAX
                ? DirectorEvent.BOSS_WAVE
                : DirectorTables.roll(s.act, server.getOverworld().getRandom());
        setToday(server, s, day, e, true);
    }

    private static void setToday(MinecraftServer server, DirectorState s, long day, DirectorEvent e, boolean show) {
        if (s.todayDay == day && s.today != e) {
            // событие дня сменили командой — прошлое заканчивается сразу
            WaveManager.dawn(server, s);
            WorldEvents.endDay(server, s);
            s.waveDay = -1;
        }
        s.today = e;
        s.todayDay = day;
        s.addHistory((int) day, e);
        s.markDirty();
        for (ServerPlayerEntity p : PlayerLookup.all(server)) {
            if (show) {
                s.shown.put(p.getUuid(), day);
            }
            send(server, p, show);
        }
    }

    // =====================================================================
    // Шкала
    // =====================================================================

    /** Добавить очки шкалы (только когда режиссёр уже работает). */
    public static void addPoints(MinecraftServer server, int amount) {
        if (server == null || amount <= 0 || !isActive(server)) {
            return;
        }
        addPointsRaw(server, DirectorState.get(server), amount);
    }

    private static void addPointsRaw(MinecraftServer server, DirectorState s, int amount) {
        if (s.points >= DirectorTables.SCALE_MAX) {
            return;
        }
        s.points = Math.min(DirectorTables.SCALE_MAX, s.points + amount);
        s.markDirty();
        syncDirty = true;
        if (s.points >= DirectorTables.SCALE_MAX) {
            broadcast(server, Text.translatable("director.basedefense.msg.boss_soon").formatted(Formatting.RED));
        }
    }

    /** Босс акта повержен: следующий акт, шкала с нуля. */
    static void nextAct(MinecraftServer server) {
        DirectorState s = DirectorState.get(server);
        s.act = Math.min(DirectorTables.MAX_ACT, s.act + 1);
        s.points = 0;
        s.markDirty();
        syncDirty = true;
        broadcast(server, Text.translatable("director.basedefense.msg.act", DirectorTables.roman(s.act))
                .formatted(Formatting.GOLD, Formatting.BOLD));
        for (ServerPlayerEntity p : PlayerLookup.all(server)) {
            p.playSound(net.minecraft.sound.SoundEvents.UI_TOAST_CHALLENGE_COMPLETE,
                    net.minecraft.sound.SoundCategory.MASTER, 1.0f, 0.8f);
        }
    }

    // =====================================================================
    // Сеть
    // =====================================================================

    private static void send(MinecraftServer server, ServerPlayerEntity p, boolean show) {
        DirectorState s = DirectorState.get(server);
        PacketByteBuf buf = PacketByteBufs.create();
        buf.writeBoolean(show);
        buf.writeVarLong(day(server));
        buf.writeBoolean(isActive(server));
        buf.writeVarInt(s.act);
        buf.writeVarInt(s.points);
        buf.writeVarInt(s.today.ordinal());
        buf.writeVarLong(Math.max(0, s.todayDay));
        buf.writeVarInt(s.history.size());
        for (DirectorState.Entry h : s.history) {
            buf.writeVarInt(h.day());
            buf.writeVarInt(h.event().ordinal());
        }
        ServerPlayNetworking.send(p, SYNC, buf);
    }

    private static void broadcast(MinecraftServer server, Text text) {
        server.getPlayerManager().broadcast(text, false);
    }

    // =====================================================================
    // Команды (для проверки): /basedefense director [roll | show | set <событие> | points <n> | act <n>]
    // =====================================================================

    private static int cmdInfo(CommandContext<ServerCommandSource> ctx) {
        MinecraftServer server = ctx.getSource().getServer();
        DirectorState s = DirectorState.get(server);
        ctx.getSource().sendFeedback(() -> Text.literal("День " + day(server) + ", акт " + DirectorTables.roman(s.act)
                + ", шкала " + s.points + "/" + DirectorTables.SCALE_MAX + ", событие: ")
                .append(Text.translatable(s.today.key()))
                .append(isActive(server) ? "" : " (режиссёр начнёт с " + DirectorTables.START_DAY + "-го дня)"), false);
        return 1;
    }

    /** Осушить: убрать воду и лёд на высоте моря…+5 в 64 блоках от игрока (остатки наводнения). */
    private static int cmdDrain(CommandContext<ServerCommandSource> ctx) {
        ServerPlayerEntity p = ctx.getSource().getPlayer();
        if (p == null) {
            return 0;
        }
        int n = Flood.get(ctx.getSource().getServer()).drain(p.getServerWorld(), p.getBlockPos(), 64);
        ctx.getSource().sendFeedback(() -> Text.literal("Убрано блоков воды и льда: " + n), false);
        return 1;
    }

    /** Начать волну сейчас: если сегодня событие без волны — ставится обычная волна. */
    private static int cmdWave(CommandContext<ServerCommandSource> ctx) {
        MinecraftServer server = ctx.getSource().getServer();
        DirectorState s = DirectorState.get(server);
        long day = day(server);
        if (!s.today.hasNightWave() || s.todayDay != day) {
            setToday(server, s, day, DirectorEvent.WAVE, false);
        }
        WaveManager.startNow(server);
        return 1;
    }

    private static int cmdRoll(CommandContext<ServerCommandSource> ctx) {
        MinecraftServer server = ctx.getSource().getServer();
        startDay(server, DirectorState.get(server), day(server));
        return 1;
    }

    private static int cmdShow(CommandContext<ServerCommandSource> ctx) {
        ServerPlayerEntity p = ctx.getSource().getPlayer();
        if (p != null) {
            send(ctx.getSource().getServer(), p, true);
        }
        return 1;
    }

    private static int cmdSet(CommandContext<ServerCommandSource> ctx) {
        DirectorEvent e = DirectorEvent.byId(StringArgumentType.getString(ctx, "event"));
        if (e == null || e == DirectorEvent.FLOOD) {
            ctx.getSource().sendError(Text.literal("Нет такого события"));
            return 0;
        }
        MinecraftServer server = ctx.getSource().getServer();
        setToday(server, DirectorState.get(server), day(server), e, true);
        return 1;
    }

    private static int cmdPoints(CommandContext<ServerCommandSource> ctx) {
        MinecraftServer server = ctx.getSource().getServer();
        DirectorState s = DirectorState.get(server);
        s.points = IntegerArgumentType.getInteger(ctx, "value");
        s.markDirty();
        syncDirty = true;
        return cmdInfo(ctx);
    }

    private static int cmdAct(CommandContext<ServerCommandSource> ctx) {
        MinecraftServer server = ctx.getSource().getServer();
        DirectorState s = DirectorState.get(server);
        s.act = IntegerArgumentType.getInteger(ctx, "value");
        s.markDirty();
        syncDirty = true;
        return cmdInfo(ctx);
    }
}

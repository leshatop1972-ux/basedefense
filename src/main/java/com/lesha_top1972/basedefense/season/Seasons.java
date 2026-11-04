package com.lesha_top1972.basedefense.season;

import com.lesha_top1972.basedefense.Basedefense;
import com.lesha_top1972.basedefense.director.VisualHooks;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.PacketByteBufs;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.block.SnowBlock;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.command.CommandManager;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.random.Random;
import net.minecraft.world.GameRules;
import net.minecraft.world.Heightmap;

/**
 * Времена года: лето → осень → зима → весна, по {@link #DAYS} дней. Календарь — день мира + сдвиг
 * ({@link SeasonState}); клиенту при входе и после команд приходит сдвиг, сезон он считает сам.
 * <ul>
 *   <li>Лето — погода как в ванилле.</li>
 *   <li>Осень — листва и трава желтеют/краснеют (клиент), частые дожди, в тайге и горах может пойти снег.</li>
 *   <li>Зима — листва в снегу, вместо дождя снег почти везде, снег ложится, вода замерзает (ванильная механика
 *       через холодную «температуру биома»), грозы нет.</li>
 *   <li>Весна — яркая молодая зелень, короткие дожди, снег и лёд тают (там, где биом не снежный).</li>
 * </ul>
 * Команды (ОП): /season — какой сезон; /season set &lt;сезон&gt; [день]; /season next.
 */
public final class Seasons {

    public static final int DAYS = 6;
    public static final Identifier SYNC = new Identifier(Basedefense.MOD_ID, "season_sync");

    /** Сезон по эту сторону: сервер (и потоки генерации мира) и клиент — для «температуры биома». */
    private static volatile Season serverSeason = Season.SUMMER;
    private static volatile Season clientSeason = Season.SUMMER;
    private static volatile long serverOffset;

    /** Таяние: сколько столбцов вокруг игрока проверять за секунду (весной / летом). */
    private static final int MELT_SPRING = 14;
    private static final int MELT_SUMMER = 28;
    private static final int MELT_RADIUS = 56;

    private Seasons() {
    }

    // =====================================================================
    // Календарь
    // =====================================================================

    public static long worldDay(long timeOfDay) {
        return Math.floorDiv(timeOfDay, 24000L);
    }

    public static Season seasonAt(long timeOfDay, long offset) {
        return Season.values()[(int) Math.floorMod(Math.floorDiv(worldDay(timeOfDay) + offset, DAYS), 4L)];
    }

    /** День сезона: 0…DAYS−1. */
    public static int dayInSeason(long timeOfDay, long offset) {
        return (int) Math.floorMod(worldDay(timeOfDay) + offset, (long) DAYS);
    }

    /** Сезон для этой стороны (сервер / клиент). */
    public static Season get(boolean client) {
        return client ? clientSeason : serverSeason;
    }

    public static long serverOffset() {
        return serverOffset;
    }

    /** Клиент: сезон пересчитан (SeasonClient). */
    public static void setClientSeason(Season s) {
        clientSeason = s;
    }

    /** Сдвиг «температуры биома» (BiomeMixin): по потоку — клиентский или серверный сезон. */
    public static float biomeShift() {
        return (VisualHooks.onClient() ? clientSeason : serverSeason).biomeShift;
    }

    // =====================================================================
    // Сервер
    // =====================================================================

    public static void init() {
        ServerLifecycleEvents.SERVER_STARTED.register(server -> {
            serverOffset = SeasonState.get(server).offset;
            serverSeason = seasonAt(server.getOverworld().getTimeOfDay(), serverOffset);
        });
        ServerLifecycleEvents.SERVER_STOPPED.register(server -> {
            serverOffset = 0;
            serverSeason = Season.SUMMER;
        });
        ServerPlayConnectionEvents.JOIN.register((handler, sender, server) -> send(handler.player));
        ServerTickEvents.END_SERVER_TICK.register(Seasons::tick);
        CommandRegistrationCallback.EVENT.register((dispatcher, registry, env) -> dispatcher.register(
                CommandManager.literal("season")
                        .executes(Seasons::cmdInfo)
                        .then(CommandManager.literal("next").requires(src -> src.hasPermissionLevel(2))
                                .executes(ctx -> setSeason(ctx, serverSeason.next(), 1)))
                        .then(CommandManager.literal("set").requires(src -> src.hasPermissionLevel(2))
                                .then(CommandManager.argument("season", StringArgumentType.word())
                                        .suggests((ctx, b) -> {
                                            for (Season s : Season.values()) {
                                                b.suggest(s.id);
                                            }
                                            return b.buildFuture();
                                        })
                                        .executes(ctx -> setSeason(ctx, null, 1))
                                        .then(CommandManager.argument("day", IntegerArgumentType.integer(1, DAYS))
                                                .executes(ctx -> setSeason(ctx, null,
                                                        IntegerArgumentType.getInteger(ctx, "day"))))))));
    }

    private static void tick(MinecraftServer server) {
        ServerWorld w = server.getOverworld();
        Season now = seasonAt(w.getTimeOfDay(), serverOffset);
        if (now != serverSeason) {
            serverSeason = now;
            announce(server, now);
        }
        int t = server.getTicks();
        if (t % 1000 == 7) {
            weather(w, now);
        }
        if (t % 20 == 11 && (now == Season.SPRING || now == Season.SUMMER)) {
            melt(w, now == Season.SPRING ? MELT_SPRING : MELT_SUMMER);
        }
        // Зимой грозы нет — только снегопад
        if (t % 100 == 13 && now == Season.WINTER && w.isThundering()) {
            w.setWeather(0, 6000, true, false);
        }
    }

    private static void announce(MinecraftServer server, Season s) {
        Text msg = Text.translatable("message.basedefense.season.started",
                Text.translatable("season.basedefense." + s.id)).formatted(color(s));
        for (ServerPlayerEntity p : server.getPlayerManager().getPlayerList()) {
            p.sendMessage(msg, false);
        }
    }

    public static Formatting color(Season s) {
        return switch (s) {
            case SUMMER -> Formatting.GOLD;
            case AUTUMN -> Formatting.RED;
            case WINTER -> Formatting.AQUA;
            case SPRING -> Formatting.GREEN;
        };
    }

    /** Раз в игровой час: в ясную погоду с шансом сезона начинается дождь (зимой — снег) на 2.5–7.5 минут. */
    private static void weather(ServerWorld w, Season s) {
        if (s.rainChance <= 0 || w.isRaining() || !w.getGameRules().getBoolean(GameRules.DO_WEATHER_CYCLE)) {
            return;
        }
        Random r = w.getRandom();
        if (r.nextFloat() < s.rainChance) {
            w.setWeather(0, 3000 + r.nextInt(6000), true, false);
        }
    }

    /** Весной и летом снег (по слою) и лёд тают — там, где биом сейчас не снежный. */
    private static void melt(ServerWorld w, int columns) {
        Random r = w.getRandom();
        for (ServerPlayerEntity p : w.getPlayers()) {
            for (int i = 0; i < columns; i++) {
                int x = p.getBlockX() + r.nextInt(MELT_RADIUS * 2 + 1) - MELT_RADIUS;
                int z = p.getBlockZ() + r.nextInt(MELT_RADIUS * 2 + 1) - MELT_RADIUS;
                if (!w.isChunkLoaded(x >> 4, z >> 4)) {
                    continue;
                }
                BlockPos top = new BlockPos(x, w.getTopY(Heightmap.Type.MOTION_BLOCKING, x, z), z);
                if (!w.getBiome(top).value().doesNotSnow(top)) {
                    continue;   // снежный биом — снег лежит всегда
                }
                for (BlockPos pos : new BlockPos[]{top, top.down()}) {
                    BlockState st = w.getBlockState(pos);
                    if (st.isOf(Blocks.SNOW)) {
                        int layers = st.get(SnowBlock.LAYERS);
                        w.setBlockState(pos, layers > 1 ? st.with(SnowBlock.LAYERS, layers - 1)
                                : Blocks.AIR.getDefaultState(), Block.NOTIFY_ALL);
                        break;
                    }
                    if (st.isOf(Blocks.ICE)) {
                        w.setBlockState(pos, Blocks.WATER.getDefaultState(), Block.NOTIFY_ALL);
                        break;
                    }
                }
            }
        }
    }

    // =====================================================================
    // Синхронизация и команды
    // =====================================================================

    private static void send(ServerPlayerEntity p) {
        PacketByteBuf buf = PacketByteBufs.create();
        buf.writeLong(serverOffset);
        ServerPlayNetworking.send(p, SYNC, buf);
    }

    private static int cmdInfo(CommandContext<ServerCommandSource> ctx) {
        long time = ctx.getSource().getServer().getOverworld().getTimeOfDay();
        Season s = seasonAt(time, serverOffset);
        int day = dayInSeason(time, serverOffset) + 1;
        ctx.getSource().sendFeedback(() -> Text.translatable("message.basedefense.season.info",
                Text.translatable("season.basedefense." + s.id).formatted(color(s)), day, DAYS,
                (int) s.tMin, (int) s.tMax), false);
        return 1;
    }

    /** Поставить сезон (season == null — из аргумента) и день сезона (1…DAYS) — сдвигом календаря, время не трогаем. */
    private static int setSeason(CommandContext<ServerCommandSource> ctx, Season season, int day) {
        if (season == null) {
            season = Season.byId(StringArgumentType.getString(ctx, "season"));
            if (season == null) {
                ctx.getSource().sendError(Text.translatable("message.basedefense.season.unknown"));
                return 0;
            }
        }
        MinecraftServer server = ctx.getSource().getServer();
        long today = worldDay(server.getOverworld().getTimeOfDay());
        SeasonState state = SeasonState.get(server);
        state.offset = season.ordinal() * (long) DAYS + (day - 1) - today;
        state.markDirty();
        serverOffset = state.offset;
        for (ServerPlayerEntity p : server.getPlayerManager().getPlayerList()) {
            send(p);
        }
        // tick() заметит смену сезона и объявит её; если сезон тот же — просто сообщаем день
        Season shown = season;
        ctx.getSource().sendFeedback(() -> Text.translatable("message.basedefense.season.set",
                Text.translatable("season.basedefense." + shown.id).formatted(color(shown)), day, DAYS), true);
        return 1;
    }
}

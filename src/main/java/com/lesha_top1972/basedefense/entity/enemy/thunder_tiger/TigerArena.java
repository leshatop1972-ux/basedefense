package com.lesha_top1972.basedefense.entity.enemy.thunder_tiger;

import com.lesha_top1972.basedefense.Basedefense;
import com.lesha_top1972.basedefense.entity.ModEntities;
import com.lesha_top1972.basedefense.fx.Fx;
import com.lesha_top1972.basedefense.sound.ModSounds;
import net.fabricmc.fabric.api.dimension.v1.FabricDimensions;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.block.CampfireBlock;
import net.minecraft.block.LanternBlock;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.registry.RegistryKey;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.TeleportTarget;
import net.minecraft.world.World;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Арена «Громового тигра» — отдельное измерение basedefense:tiger_arena (вечная ночь, пустота вокруг).
 * Потеряв 20% здоровья, тигр крутит тесак и утаскивает всех игроков рядом (48 бл) к себе на арену.
 * Арена строится кодом при первом использовании: «Двор тигра» — круглый плоский двор радиусом 30 блоков из
 * полированного чернокамня с золотыми и красными кольцами и лучами, светильники в полу, стена из чернокамня
 * и тёмного дерева, красные лакированные столбы с фонарями, жаровни у стены.
 * Тигр погиб — через 10 с все возвращаются туда, откуда их забрали. На арене не осталось игроков 20 с —
 * тигр возвращается на прежнее место (и через минуту снова может утащить).
 */
public final class TigerArena {

    public static final RegistryKey<World> WORLD = RegistryKey.of(RegistryKeys.WORLD, new Identifier(Basedefense.MOD_ID, "tiger_arena"));
    /** Пол — y = 64, стоят на y = 65. Двор радиусом FLOOR_R, стена до WALL_R. */
    public static final int FLOOR_Y = 64;
    static final double FLOOR_R = 30.5;
    static final double WALL_R = 33.5;
    static final int WALL_TOP = 71;
    /** Метка «арена построена» (под полом в центре). */
    static final BlockPos MARK = new BlockPos(0, 60, 0);
    /** Насколько далеко от тигра забирает игроков. */
    static final double PULL_RANGE = 48.0;
    static final int RETURN_DELAY = 200;
    static final int EMPTY_DELAY = 400;

    private record Spot(RegistryKey<World> dim, Vec3d pos, float yaw, float pitch) {
    }

    private static final Map<UUID, Spot> ORIGINS = new HashMap<>();
    private static int victoryTimer = -1;
    private static int emptyTimer;

    private TigerArena() {
    }

    public static void init() {
        ServerTickEvents.END_SERVER_TICK.register(TigerArena::tick);
    }

    public static boolean isArena(World w) {
        return w.getRegistryKey() == WORLD;
    }

    // ================================================================= забрать на арену
    static void pull(ThunderTigerEntity boss) {
        if (!(boss.getWorld() instanceof ServerWorld from) || isArena(from)) {
            return;
        }
        MinecraftServer server = from.getServer();
        ServerWorld arena = server.getWorld(WORLD);
        if (arena == null) {
            org.slf4j.LoggerFactory.getLogger(Basedefense.MOD_ID).warn("Нет измерения арены тигра (basedefense:tiger_arena)");
            boss.arenaDone = true;
            return;
        }
        ensureBuilt(arena);
        boss.arenaDone = true;
        boss.originDim = from.getRegistryKey().getValue().toString();
        boss.originPos = boss.getPos();
        List<ServerPlayerEntity> players = new ArrayList<>();
        for (ServerPlayerEntity p : from.getPlayers()) {
            if (p.isAlive() && !p.isSpectator() && p.squaredDistanceTo(boss) < PULL_RANGE * PULL_RANGE) {
                players.add(p);
            }
        }
        Fx.tigerRings(from, boss.getPos());
        from.playSound(null, boss.getX(), boss.getY(), boss.getZ(), ModSounds.TIGER_PHASE, SoundCategory.HOSTILE, 2f, 0.7f);
        int n = Math.max(1, players.size());
        for (int i = 0; i < players.size(); i++) {
            ServerPlayerEntity p = players.get(i);
            ORIGINS.put(p.getUuid(), new Spot(from.getRegistryKey(), p.getPos(), p.getYaw(), p.getPitch()));
            double ang = i * Math.PI * 2 / n + Math.PI / 2;
            double x = Math.cos(ang) * 14 + 0.5;
            double z = Math.sin(ang) * 14 + 0.5;
            float yaw = (float) (MathHelper.atan2(-z, -x) * MathHelper.DEGREES_PER_RADIAN) - 90f;
            p.teleport(arena, x, FLOOR_Y + 1, z, yaw, 0f);
            p.addStatusEffect(new StatusEffectInstance(StatusEffects.BLINDNESS, 30, 0, false, false, false));
            p.sendMessage(Text.translatable("message.basedefense.tiger_arena.pull").formatted(Formatting.GOLD), true);
        }
        victoryTimer = -1;
        emptyTimer = 0;
        ThunderTigerEntity moved = FabricDimensions.teleport(boss, arena,
                new TeleportTarget(new Vec3d(0.5, FLOOR_Y + 1, 0.5), Vec3d.ZERO, 0f, 0f));
        if (moved != null) {
            arena.playSound(null, 0.5, FLOOR_Y + 1, 0.5, ModSounds.TIGER_PHASE, SoundCategory.HOSTILE, 2f, 1f);
            Fx.tigerRings(arena, moved.getPos());
        }
    }

    // ================================================================= возврат
    private static void tick(MinecraftServer server) {
        if (server.getTicks() % 20 != 0) {
            return;
        }
        ServerWorld arena = server.getWorld(WORLD);
        if (arena == null) {
            return;
        }
        List<ServerPlayerEntity> players = new ArrayList<>(arena.getPlayers());
        List<? extends ThunderTigerEntity> bosses = arena.getEntitiesByType(ModEntities.THUNDER_TIGER, e -> e.isAlive());
        if (!players.isEmpty() && bosses.isEmpty()) {
            // тигр повержен (или его нет) — через 10 с домой
            if (victoryTimer < 0) {
                victoryTimer = RETURN_DELAY;
            }
            victoryTimer -= 20;
            for (ServerPlayerEntity p : players) {
                p.sendMessage(Text.translatable("message.basedefense.tiger_arena.return", Math.max(0, victoryTimer / 20))
                        .formatted(Formatting.GOLD), true);
            }
            if (victoryTimer <= 0) {
                for (ServerPlayerEntity p : players) {
                    sendBack(server, p);
                }
                victoryTimer = -1;
            }
            return;
        }
        victoryTimer = -1;
        if (players.isEmpty() && !bosses.isEmpty()) {
            emptyTimer += 20;
            if (emptyTimer >= EMPTY_DELAY) {
                emptyTimer = 0;
                for (ThunderTigerEntity b : new ArrayList<ThunderTigerEntity>(bosses)) {
                    bossHome(server, b);
                }
            }
        } else {
            emptyTimer = 0;
        }
    }

    private static void sendBack(MinecraftServer server, ServerPlayerEntity p) {
        Spot s = ORIGINS.remove(p.getUuid());
        ServerWorld w = s != null ? server.getWorld(s.dim()) : null;
        if (w == null) {
            w = server.getOverworld();
            BlockPos sp = w.getSpawnPos();
            p.teleport(w, sp.getX() + 0.5, sp.getY(), sp.getZ() + 0.5, p.getYaw(), p.getPitch());
            return;
        }
        p.teleport(w, s.pos().x, s.pos().y, s.pos().z, s.yaw(), s.pitch());
    }

    private static void bossHome(MinecraftServer server, ThunderTigerEntity b) {
        ServerWorld w = null;
        if (b.originDim != null) {
            w = server.getWorld(RegistryKey.of(RegistryKeys.WORLD, new Identifier(b.originDim)));
        }
        if (w == null) {
            w = server.getOverworld();
        }
        Vec3d pos = b.originPos != null ? b.originPos : Vec3d.ofBottomCenter(w.getSpawnPos());
        b.arenaDone = false;
        b.arenaCooldown = 1200;
        b.setTarget(null);
        FabricDimensions.teleport(b, w, new TeleportTarget(pos, Vec3d.ZERO, b.getYaw(), 0f));
    }

    // ================================================================= постройка
    static void ensureBuilt(ServerWorld w) {
        if (w.getBlockState(MARK).isOf(Blocks.BEDROCK)) {
            return;
        }
        build(w);
        w.setBlockState(MARK, Blocks.BEDROCK.getDefaultState(), Block.NOTIFY_LISTENERS);
    }

    private static void set(ServerWorld w, int x, int y, int z, BlockState st) {
        w.setBlockState(new BlockPos(x, y, z), st, Block.NOTIFY_LISTENERS);
    }

    private static void build(ServerWorld w) {
        int r = (int) Math.ceil(WALL_R) + 1;
        BlockState support = Blocks.DEEPSLATE_TILES.getDefaultState();
        BlockState base = Blocks.POLISHED_BLACKSTONE.getDefaultState();
        BlockState bricks = Blocks.POLISHED_BLACKSTONE_BRICKS.getDefaultState();
        BlockState gold = Blocks.GILDED_BLACKSTONE.getDefaultState();
        BlockState red = Blocks.RED_NETHER_BRICKS.getDefaultState();
        BlockState spoke = Blocks.POLISHED_DEEPSLATE.getDefaultState();
        BlockState light = Blocks.SHROOMLIGHT.getDefaultState();
        BlockState panel = Blocks.DARK_OAK_PLANKS.getDefaultState();
        BlockState beam = Blocks.STRIPPED_DARK_OAK_LOG.getDefaultState();
        for (int x = -r; x <= r; x++) {
            for (int z = -r; z <= r; z++) {
                double d = Math.sqrt(x * x + z * z);
                if (d > WALL_R) {
                    continue;
                }
                double ang = Math.toDegrees(Math.atan2(z, x));
                // основание
                set(w, x, FLOOR_Y - 1, z, support);
                set(w, x, FLOOR_Y - 2, z, support);
                if (d <= FLOOR_R) {
                    // пол двора
                    BlockState st = ((Math.floorDiv(x, 3) + Math.floorDiv(z, 3)) & 1) == 0 ? base : bricks;
                    double sp = Math.abs(((ang % 45) + 45) % 45 - 22.5);       // 22.5 — на луче
                    boolean onSpoke = sp > 22.5 - Math.toDegrees(0.55 / Math.max(d, 1));
                    if (onSpoke && d > 6.5 && d < 24) {
                        st = spoke;
                    }
                    if (Math.abs(d - 6) < 0.55 || Math.abs(d - 18) < 0.55) {
                        st = gold;
                    }
                    if (Math.abs(d - 12) < 0.55 || Math.abs(d - 24) < 0.55) {
                        st = red;
                    }
                    if (d > 29.4) {
                        st = bricks;
                    }
                    if (d < 2.5) {
                        st = d < 1.2 ? light : gold;                           // медальон в центре
                    }
                    // светильники в полу: на кольце 21 каждые 30°
                    double a30 = ((ang % 30) + 30) % 30;
                    if (Math.abs(d - 21) < 0.5 && (a30 < 1.5 || a30 > 28.5)) {
                        st = light;
                    }
                    set(w, x, FLOOR_Y, z, st);
                    for (int y = FLOOR_Y + 1; y <= WALL_TOP + 2; y++) {
                        set(w, x, y, z, Blocks.AIR.getDefaultState());
                    }
                } else {
                    // стена: чернокамень внизу, тёмное дерево с балками, чернокамень сверху, зубцы
                    set(w, x, FLOOR_Y, z, bricks);
                    for (int y = FLOOR_Y + 1; y <= WALL_TOP; y++) {
                        BlockState st;
                        if (y <= FLOOR_Y + 2 || y >= WALL_TOP - 1) {
                            st = bricks;
                        } else {
                            double a15 = ((ang % 15) + 15) % 15;
                            st = (a15 < 1.6 || a15 > 13.4) ? beam : panel;
                        }
                        set(w, x, y, z, st);
                    }
                    double a6 = ((ang % 6) + 6) % 6;
                    if (d > WALL_R - 1.2 && a6 < 3) {
                        set(w, x, WALL_TOP + 1, z, bricks);                    // зубцы по внешнему краю
                    }
                }
            }
        }
        // красные столбы с фонарями (каждые 15°) и жаровни у стены
        BlockState pillar = Blocks.STRIPPED_MANGROVE_LOG.getDefaultState();
        for (int k = 0; k < 24; k++) {
            double a = Math.toRadians(k * 15);
            int x = (int) Math.round(Math.cos(a) * 29.6);
            int z = (int) Math.round(Math.sin(a) * 29.6);
            for (int y = FLOOR_Y + 1; y <= WALL_TOP - 1; y++) {
                set(w, x, y, z, pillar);
            }
            set(w, x, WALL_TOP, z, gold);
            set(w, x, WALL_TOP + 1, z, Blocks.LANTERN.getDefaultState().with(LanternBlock.HANGING, false));
        }
        for (int k = 0; k < 8; k++) {
            double a = Math.toRadians(k * 45 + 22.5);
            int x = (int) Math.round(Math.cos(a) * 28.0);
            int z = (int) Math.round(Math.sin(a) * 28.0);
            set(w, x, FLOOR_Y + 1, z, Blocks.CAMPFIRE.getDefaultState().with(CampfireBlock.LIT, true)
                    .with(CampfireBlock.SIGNAL_FIRE, false));
        }
    }
}

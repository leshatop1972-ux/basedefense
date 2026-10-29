package com.lesha_top1972.basedefense.director;

import com.lesha_top1972.basedefense.Basedefense;
import com.lesha_top1972.basedefense.block.BaseLocationState;
import com.lesha_top1972.basedefense.entity.ModEntities;
import com.lesha_top1972.basedefense.mixin.MobEntityAccessor;
import net.fabricmc.fabric.api.entity.event.v1.EntitySleepEvents;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.SpawnReason;
import net.minecraft.entity.attribute.EntityAttributeInstance;
import net.minecraft.entity.attribute.EntityAttributeModifier;
import net.minecraft.entity.attribute.EntityAttributes;
import net.minecraft.entity.boss.BossBar;
import net.minecraft.entity.boss.ServerBossBar;
import net.minecraft.entity.mob.Angerable;
import net.minecraft.entity.mob.HostileEntity;
import net.minecraft.entity.mob.MobEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.registry.Registries;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import net.minecraft.util.math.random.Random;
import net.minecraft.world.Heightmap;
import net.minecraft.world.World;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * Волны «Режиссёра мира». В ночь события с волной (с 13 000 тиков) — части на всю ночь, каждая со своей стороны
 * за краем поселения (40–72 бл от Базы, не вплотную к домам, жителям и игрокам; совсем нигде — у игрока).
 * Застрявших и «уснувших» (чанк не тикает) мобов слежка переставляет ближе к Базе.
 * Мобы помечены тегом, не исчезают, идут к Базе ({@link WaveMarchGoal}), на них действует усиление акта.
 * Полоска сверху: часть и сколько врагов осталось. Все убиты — волна отбита (боссовая — следующий акт).
 * Рассвет — оставшиеся отступают (исчезают). В ночь волны спать нельзя. Из ванильных враждебных мобов
 * у Базы на неё идёт лишь «капля»: каждый десятый, не больше 4 за ночь.
 */
public final class WaveManager {

    /**
     * Волна растянута на всю ночь: 12 частей через 750 т (~37 с), с 13 000 до 21 250 т; каждая — со своей
     * стороны вокруг Базы (12 направлений через 30°).
     */
    public static final int PARTS = 12;
    public static final int START = 13000;
    public static final int GAP = 750;
    /** С какой частью приходит босс — около 2/3 ночи (~18 250 т), чтобы до рассвета хватило времени на бой. */
    public static final int BOSS_PART = 7;
    public static final String TAG = "basedefense.wave";
    public static final String BOSS_TAG = "basedefense.wave_boss";
    /** Яйца волны: не считаются во «врагах», но на рассвете тоже исчезают. */
    public static final String EXTRA_TAG = "basedefense.wave_extra";
    private static final String DRAWN_TAG = "basedefense.drawn";
    /** Точка появления: за краем поселения на столько блоков (но в пределах MIN_R…MAX_R от Базы). */
    private static final int TOWN_GAP = 26;
    private static final int MIN_R = 40;
    private static final int MAX_R = 72;
    /** Не ближе стольких блоков к жителям/стражникам и игрокам. */
    private static final double SAFE_SETTLER = 20;
    private static final double SAFE_PLAYER = 16;
    /** Не ближе стольких блоков к угловой точке здания (здания до ~12×10). */
    private static final double SAFE_BUILDING = 26;
    static final double MARCH_SPEED = 1.1;
    /** Слежка за мобами волны: застрял/выпал из «живой» зоны — переставить ближе. */
    private static final Map<UUID, Long> MISSING = new HashMap<>();
    private static final Map<UUID, Vec3d> LAST_POS = new HashMap<>();
    private static final Map<UUID, Long> LAST_MOVE = new HashMap<>();
    private static final UUID HP_ID = UUID.fromString("4b0f0d3c-6a8e-4c1e-9a52-0d1a7c3e5b11");
    private static final UUID RANGE_ID = UUID.fromString("9d2c7e41-1f3b-4a6d-8c25-6e0b4f9a2c77");

    private static ServerBossBar bar;

    private WaveManager() {
    }

    public static void init() {
        ServerTickEvents.END_SERVER_TICK.register(WaveManager::tick);
        ServerLivingEntityEvents.AFTER_DEATH.register((entity, source) -> {
            if (entity.getWorld().isClient || !entity.getCommandTags().contains(TAG)) {
                return;
            }
            DirectorState s = DirectorState.get(entity.getServer());
            if (s.waveMobs.remove(entity.getUuid())) {
                s.markDirty();
            }
            if (entity.getCommandTags().contains(BOSS_TAG) && s.waveDay >= 0) {
                s.bossDefeated = true;
                s.markDirty();
            }
        });
        // моб прошлой волны загрузился из сохранения (на рассвете его чанк был выгружен) — убираем
        ServerEntityEvents.ENTITY_LOAD.register((entity, world) -> {
            Set<String> tags = entity.getCommandTags();
            if (!(tags.contains(TAG) || tags.contains(EXTRA_TAG))) {
                return;
            }
            DirectorState s = DirectorState.get(world.getServer());
            boolean stale = s.waveOver || (tags.contains(TAG) && !s.waveMobs.contains(entity.getUuid()));
            if (stale) {
                world.getServer().execute(entity::discard);   // не посреди загрузки чанка
            } else if (tags.contains(TAG) && entity instanceof MobEntity mob) {
                // цели мобов не сохраняются — «идти к Базе» навешиваем при каждой загрузке (и при появлении)
                ((MobEntityAccessor) mob).basedefense$getGoalSelector().add(5, new WaveMarchGoal(mob, MARCH_SPEED));
            }
        });
        EntitySleepEvents.ALLOW_SLEEPING.register((player, pos) -> {
            if (!player.getWorld().isClient && waveTonight(player.getServer())) {
                player.sendMessage(Text.translatable("director.basedefense.msg.no_sleep").formatted(Formatting.RED), true);
                return PlayerEntity.SleepFailureReason.NOT_SAFE;
            }
            return null;
        });
    }

    // =====================================================================
    // Тик
    // =====================================================================

    private static void tick(MinecraftServer server) {
        if (server.getTicks() % 20 != 0) {
            return;
        }
        DirectorState s = DirectorState.get(server);
        long day = Director.day(server);
        long t = server.getOverworld().getTimeOfDay() % Director.DAY_TICKS;
        if (s.today.hasNightWave() && s.todayDay == day && s.waveDay != day && t >= START && t < 23000) {
            begin(server, s, day);
        }
        if (s.waveDay == day && !s.waveOver) {
            if (s.waveNext < PARTS && server.getOverworld().getTime() >= s.waveStart + (long) s.waveNext * GAP) {
                spawnPart(server, s);
            }
            if (server.getTicks() % 40 == 0) {
                drawVanilla(server);
            }
            if (server.getTicks() % 100 == 0) {
                watchdog(server, s);
            }
            if (s.waveNext >= PARTS && s.waveMobs.isEmpty()) {
                finish(server, s);
            }
        }
        updateBar(server, s);
    }

    /** Команда: начать волну сегодняшнего события прямо сейчас (в любое время суток). */
    static void startNow(MinecraftServer server) {
        DirectorState s = DirectorState.get(server);
        begin(server, s, Director.day(server));
    }

    /** Этой ночью волна (ещё не отбита) — спать нельзя. */
    public static boolean waveTonight(MinecraftServer server) {
        DirectorState s = DirectorState.get(server);
        long day = Director.day(server);
        return s.today.hasNightWave() && s.todayDay == day && !(s.waveDay == day && s.waveOver);
    }

    // =====================================================================
    // Начало, части, конец
    // =====================================================================

    private static void begin(MinecraftServer server, DirectorState s, long day) {
        ServerWorld w = waveWorld(server);
        int players = Math.max(1, w.getPlayers().size());
        DirectorEvent e = s.today;
        List<List<EntityType<?>>> parts = WaveComposer.compose(e, s.points, players, w.getRandom());
        s.waveDay = day;
        s.waveEvent = e;
        s.waveNext = 0;
        s.waveStart = server.getOverworld().getTime();
        s.waveOver = false;
        s.bossDefeated = false;
        s.waveMobs.clear();
        s.waveParts.clear();
        s.waveTotal = 0;
        for (List<EntityType<?>> part : parts) {
            List<String> ids = new ArrayList<>();
            for (EntityType<?> type : part) {
                ids.add(Registries.ENTITY_TYPE.getId(type).toString());
                if (type != ModEntities.BLOOD_EGG && type != ModEntities.BEETLE_EGG) {
                    s.waveTotal++;
                }
            }
            s.waveParts.add(ids);
        }
        if (e == DirectorEvent.BOSS_WAVE) {
            s.waveTotal++;
        }
        s.markDirty();
        for (ServerPlayerEntity p : server.getPlayerManager().getPlayerList()) {
            p.sendMessage(Text.translatable("director.basedefense.msg.wave_start", Text.translatable(e.key()))
                    .formatted(Formatting.RED), false);
            p.playSound(SoundEvents.BLOCK_BELL_USE, SoundCategory.MASTER, 1.0f, 0.5f);
            p.playSound(SoundEvents.BLOCK_BELL_RESONATE, SoundCategory.MASTER, 0.8f, 0.7f);
        }
    }

    private static void spawnPart(MinecraftServer server, DirectorState s) {
        ServerWorld w = waveWorld(server);
        int index = s.waveNext;
        s.waveNext++;
        s.markDirty();
        List<String> ids = index < s.waveParts.size() ? s.waveParts.get(index) : List.of();
        BlockPos center = spawnPoint(w, partAngle(s, index));
        if (center == null) {
            return;
        }
        for (String id : ids) {
            EntityType<?> type = Registries.ENTITY_TYPE.get(new Identifier(id));
            spawnMob(w, s, type, center, 7, false);
        }
        if (index == BOSS_PART && s.waveEvent == DirectorEvent.BOSS_WAVE) {
            EntityType<?> boss = Registries.ENTITY_TYPE.get(new Identifier(Basedefense.MOD_ID, DirectorTables.boss(s.act)));
            BlockPos at = center;                       // точка части уже за краем поселения, недалеко
            spawnMob(w, s, boss, at, 1, true);
            for (ServerPlayerEntity p : server.getPlayerManager().getPlayerList()) {
                p.sendMessage(Text.translatable("director.basedefense.msg.boss_arrives",
                        boss.getName()).formatted(Formatting.DARK_RED, Formatting.BOLD), false);
                p.playSound(SoundEvents.ENTITY_WITHER_SPAWN, SoundCategory.HOSTILE, 0.5f, 0.8f);
            }
        }
        for (ServerPlayerEntity p : w.getPlayers()) {
            p.sendMessage(Text.translatable("director.basedefense.msg.wave_part", index + 1, PARTS,
                    direction(w, center)).formatted(Formatting.GOLD), true);
            p.playSound(SoundEvents.BLOCK_BELL_USE, SoundCategory.MASTER, 0.7f, 0.6f);
        }
    }

    private static void spawnMob(ServerWorld w, DirectorState s, EntityType<?> type, BlockPos center, int spread, boolean boss) {
        Random r = w.getRandom();
        Entity entity = type.create(w);
        if (entity == null) {
            return;
        }
        BlockPos pos = center;
        for (int i = 0; i < 6; i++) {
            int x = center.getX() + r.nextInt(spread * 2 + 1) - spread;
            int z = center.getZ() + r.nextInt(spread * 2 + 1) - spread;
            BlockPos probe = new BlockPos(x, center.getY(), z);
            if (!w.shouldTickEntity(probe)) {
                continue;                              // незагруженный/«спящий» чанк — моб бы там замер
            }
            int y = w.getTopY(Heightmap.Type.MOTION_BLOCKING_NO_LEAVES, x, z);
            BlockPos p = new BlockPos(x, y, z);
            if (w.getFluidState(p.down()).isEmpty() && w.getFluidState(p).isEmpty()) {
                pos = p;
                break;
            }
        }
        entity.refreshPositionAndAngles(pos.getX() + 0.5, pos.getY(), pos.getZ() + 0.5, r.nextFloat() * 360f, 0f);
        boolean egg = type == ModEntities.BLOOD_EGG || type == ModEntities.BEETLE_EGG;
        if (entity instanceof MobEntity mob) {
            mob.initialize(w, w.getLocalDifficulty(pos), SpawnReason.EVENT, null, null);
            mob.setPersistent();
        }
        if (entity instanceof LivingEntity living) {
            float hp = DirectorTables.hpBonus(s.act);
            EntityAttributeInstance max = living.getAttributeInstance(EntityAttributes.GENERIC_MAX_HEALTH);
            if (hp > 0f && max != null) {
                max.addPersistentModifier(new EntityAttributeModifier(HP_ID, "Director act health", hp,
                        EntityAttributeModifier.Operation.MULTIPLY_TOTAL));
                living.setHealth(living.getMaxHealth());
            }
        }
        if (egg) {
            entity.addCommandTag(EXTRA_TAG);
        } else {
            entity.addCommandTag(TAG);
            s.waveMobs.add(entity.getUuid());          // до spawnEntity — иначе ENTITY_LOAD сочтёт моба чужим
        }
        if (boss) {
            entity.addCommandTag(BOSS_TAG);
        }
        w.spawnEntity(entity);
        w.spawnParticles(ParticleTypes.LARGE_SMOKE, entity.getX(), entity.getY() + 0.5, entity.getZ(),
                6, 0.4, 0.5, 0.4, 0.02);
        s.markDirty();
    }

    /** Волна отбита: все враги убиты. */
    private static void finish(MinecraftServer server, DirectorState s) {
        s.waveOver = true;
        s.markDirty();
        boolean bossWin = s.waveEvent == DirectorEvent.BOSS_WAVE && s.bossDefeated;
        for (ServerPlayerEntity p : server.getPlayerManager().getPlayerList()) {
            p.sendMessage(Text.translatable("director.basedefense.msg.wave_won").formatted(Formatting.GREEN), false);
            p.playSound(SoundEvents.UI_TOAST_CHALLENGE_COMPLETE, SoundCategory.MASTER, 0.6f, 1.0f);
        }
        if (bossWin) {
            Director.nextAct(server);
        }
    }

    /** Рассвет (новый день): несломленная волна отступает. Победа над боссом засчитывается и тут. */
    static void dawn(MinecraftServer server, DirectorState s) {
        if (s.waveOver) {
            return;
        }
        s.waveOver = true;
        s.markDirty();
        removeAll(server);
        int left = s.waveMobs.size();
        s.waveMobs.clear();
        if (s.waveEvent == DirectorEvent.BOSS_WAVE && s.bossDefeated) {
            Director.nextAct(server);
        } else if (left > 0) {
            Text msg = Text.translatable(s.waveEvent == DirectorEvent.BOSS_WAVE
                    ? "director.basedefense.msg.boss_retreat" : "director.basedefense.msg.wave_retreat")
                    .formatted(Formatting.GRAY);
            server.getPlayerManager().broadcast(msg, false);
        }
    }

    /** Убрать всех мобов волны (загруженных) — с облачком дыма. */
    static void removeAll(MinecraftServer server) {
        for (ServerWorld w : server.getWorlds()) {
            List<Entity> list = new ArrayList<>();
            for (Entity e : w.iterateEntities()) {
                Set<String> tags = e.getCommandTags();
                if (tags.contains(TAG) || tags.contains(EXTRA_TAG)) {
                    list.add(e);
                }
            }
            for (Entity e : list) {
                w.spawnParticles(ParticleTypes.CLOUD, e.getX(), e.getY() + e.getHeight() / 2, e.getZ(),
                        12, e.getWidth() / 2, e.getHeight() / 3, e.getWidth() / 2, 0.02);
                e.discard();
            }
        }
    }

    // =====================================================================
    // Полоска волны
    // =====================================================================

    private static void updateBar(MinecraftServer server, DirectorState s) {
        boolean active = s.waveDay == Director.day(server) && !s.waveOver;
        if (!active) {
            if (bar != null) {
                bar.clearPlayers();
                bar = null;
            }
            return;
        }
        if (bar == null) {
            bar = new ServerBossBar(Text.empty(), BossBar.Color.RED, BossBar.Style.NOTCHED_10);
        }
        int alive = s.waveMobs.size();
        int part = Math.max(1, Math.min(PARTS, s.waveNext));
        bar.setName(Text.translatable("director.basedefense.bar", Text.translatable(s.waveEvent.key()), part, PARTS, alive));
        bar.setPercent(s.waveTotal <= 0 ? 0f : MathHelper.clamp(alive / (float) s.waveTotal, 0f, 1f));
        ServerWorld w = waveWorld(server);
        Set<ServerPlayerEntity> now = new HashSet<>(w.getPlayers());
        for (ServerPlayerEntity p : new ArrayList<>(bar.getPlayers())) {
            if (!now.contains(p)) {
                bar.removePlayer(p);
            }
        }
        for (ServerPlayerEntity p : now) {
            bar.addPlayer(p);
        }
    }

    // =====================================================================
    // Ванильные мобы в ночь волны
    // =====================================================================

    /** Ванильных мобов на Базу за ночь — совсем немного (пользователь: «прям каплю»). */
    private static final int VANILLA_MAX = 4;
    private static final float VANILLA_CHANCE = 0.1f;
    private static long vanillaDay = -1;
    private static int vanillaDrawn;

    private static void drawVanilla(MinecraftServer server) {
        ServerWorld w = waveWorld(server);
        BlockPos base = basePos(w);
        if (base == null) {
            return;
        }
        DirectorState s = DirectorState.get(server);
        if (vanillaDay != s.waveDay) {
            vanillaDay = s.waveDay;
            vanillaDrawn = 0;
        }
        if (vanillaDrawn >= VANILLA_MAX) {
            return;
        }
        Box box = new Box(base).expand(96, 48, 96);
        for (HostileEntity mob : w.getEntitiesByClass(HostileEntity.class, box, m -> !(m instanceof Angerable)
                && Registries.ENTITY_TYPE.getId(m.getType()).getNamespace().equals("minecraft")
                && !m.getCommandTags().contains(DRAWN_TAG))) {
            mob.addCommandTag(DRAWN_TAG);               // каждого проверяем один раз
            if (vanillaDrawn >= VANILLA_MAX || w.getRandom().nextFloat() >= VANILLA_CHANCE) {
                continue;
            }
            vanillaDrawn++;
            EntityAttributeInstance range = mob.getAttributeInstance(EntityAttributes.GENERIC_FOLLOW_RANGE);
            if (range != null && range.getModifier(RANGE_ID) == null) {
                range.addTemporaryModifier(new EntityAttributeModifier(RANGE_ID, "Wave night", 16.0,
                        EntityAttributeModifier.Operation.ADDITION));
            }
            ((MobEntityAccessor) mob).basedefense$getGoalSelector().add(5, new WaveMarchGoal(mob, 1.0));
        }
    }

    // =====================================================================
    // Помощники
    // =====================================================================

    /** Мир волны: мир Базы, без Базы — обычный мир. */
    static ServerWorld waveWorld(MinecraftServer server) {
        BaseLocationState b = BaseLocationState.get(server);
        if (b.hasBase()) {
            ServerWorld w = server.getWorld(b.getDimension());
            if (w != null) {
                return w;
            }
        }
        return server.getOverworld();
    }

    /** Блок «База», если он в этом мире. */
    @Nullable
    public static BlockPos basePos(World w) {
        if (w.getServer() == null) {
            return null;
        }
        BaseLocationState b = BaseLocationState.get(w.getServer());
        return b.hasBase() && w.getRegistryKey().equals(b.getDimension()) ? b.getPos() : null;
    }

    /**
     * Сторона части: 12 направлений через 30° с общим сдвигом (от дня волны), порядок перемешан так,
     * что соседние части приходят с разных сторон (через 150°).
     */
    private static double partAngle(DirectorState s, int index) {
        double offset = Math.floorMod(s.waveDay * 2654435761L, 360L);
        int slot = (index * 5) % PARTS;                  // 5 и 12 взаимно просты — все стороны по разу
        return Math.toRadians(offset + slot * (360.0 / PARTS));
    }

    /** Точка появления части: за краем поселения (40–72 бл от Базы) в своей стороне; иначе у игрока. */
    @Nullable
    private static BlockPos spawnPoint(ServerWorld w, double angle) {
        Random r = w.getRandom();
        BlockPos base = basePos(w);
        if (base != null) {
            int ring = MathHelper.clamp(townRadius(w, base) + TOWN_GAP, MIN_R, MAX_R);
            BlockPos p = ringPoint(w, base, r, angle, ring);
            if (p != null) {
                return p;
            }
        }
        List<ServerPlayerEntity> players = w.getPlayers(p -> !p.isSpectator());
        if (!players.isEmpty()) {
            BlockPos p = ringPoint(w, players.get(r.nextInt(players.size())).getBlockPos(), r, angle, MIN_R);
            if (p != null) {
                return p;
            }
        }
        return base;
    }

    /** Радиус поселения от Базы: самые дальние здания и жители (не больше 64). */
    private static int townRadius(ServerWorld w, BlockPos base) {
        double max = 0;
        String dim = w.getRegistryKey().getValue().toString();
        for (com.lesha_top1972.basedefense.structure.TownState.Building b
                : com.lesha_top1972.basedefense.structure.TownState.get(w.getServer()).getBuildings(dim)) {
            double d = Math.sqrt(b.pos().getSquaredDistance(base.getX(), b.pos().getY(), base.getZ())) + 10;
            if (d < 128) {
                max = Math.max(max, d);
            }
        }
        for (com.lesha_top1972.basedefense.entity.settler.SettlerEntity e : w.getEntitiesByClass(
                com.lesha_top1972.basedefense.entity.settler.SettlerEntity.class, new Box(base).expand(96, 32, 96), x -> true)) {
            max = Math.max(max, Math.sqrt(e.squaredDistanceTo(base.getX(), e.getY(), base.getZ())));
        }
        return (int) Math.min(64, max);
    }

    /** Точка у земли в своей стороне (±40°) на расстоянии ring…ring+24, вдали от домов, жителей и игроков. */
    @Nullable
    private static BlockPos ringPoint(ServerWorld w, BlockPos center, Random r, double angle, int ring) {
        for (int attempt = 0; attempt < 24; attempt++) {
            double spreadDeg = attempt < 12 ? 40 : 180;   // сначала своя сторона, потом любая
            double a = angle + Math.toRadians((r.nextDouble() - 0.5) * 2 * spreadDeg);
            double dist = ring + r.nextDouble() * 24;
            int x = MathHelper.floor(center.getX() + Math.cos(a) * dist);
            int z = MathHelper.floor(center.getZ() + Math.sin(a) * dist);
            BlockPos probe = new BlockPos(x, center.getY(), z);
            if (!w.shouldTickEntity(probe)) {
                continue;
            }
            int y = w.getTopY(Heightmap.Type.MOTION_BLOCKING_NO_LEAVES, x, z);
            BlockPos p = new BlockPos(x, y, z);
            if (y > w.getBottomY() + 1 && w.getFluidState(p.down()).isEmpty() && w.getFluidState(p).isEmpty()
                    && safeSpot(w, p)) {
                return p;
            }
        }
        return null;
    }

    /** Не вплотную к домам, жителям/стражникам и игрокам. */
    private static boolean safeSpot(ServerWorld w, BlockPos p) {
        if (!w.getEntitiesByClass(com.lesha_top1972.basedefense.entity.settler.SettlerEntity.class,
                new Box(p).expand(SAFE_SETTLER), e -> e.squaredDistanceTo(Vec3d.ofCenter(p)) < SAFE_SETTLER * SAFE_SETTLER).isEmpty()) {
            return false;
        }
        for (ServerPlayerEntity pl : w.getPlayers()) {
            if (!pl.isSpectator() && pl.squaredDistanceTo(Vec3d.ofCenter(p)) < SAFE_PLAYER * SAFE_PLAYER) {
                return false;
            }
        }
        String dim = w.getRegistryKey().getValue().toString();
        for (com.lesha_top1972.basedefense.structure.TownState.Building b
                : com.lesha_top1972.basedefense.structure.TownState.get(w.getServer()).getBuildings(dim)) {
            if (b.pos().getSquaredDistance(p.getX(), b.pos().getY(), p.getZ()) < SAFE_BUILDING * SAFE_BUILDING) {
                return false;
            }
        }
        return true;
    }

    // =====================================================================
    // Слежка: моб волны застрял или ушёл туда, где мир «спит» — переставить ближе к Базе
    // =====================================================================

    private static void watchdog(MinecraftServer server, DirectorState s) {
        ServerWorld w = waveWorld(server);
        BlockPos base = basePos(w);
        long now = w.getTime();
        boolean changed = false;
        for (UUID id : new ArrayList<>(s.waveMobs)) {
            Entity e = w.getEntity(id);
            if (!(e instanceof MobEntity mob)) {
                // не загружен: больше минуты — не считаем (при загрузке он исчезнет сам)
                long since = MISSING.computeIfAbsent(id, k -> now);
                if (now - since > 1200) {
                    s.waveMobs.remove(id);
                    MISSING.remove(id);
                    changed = true;
                }
                continue;
            }
            MISSING.remove(id);
            if (base == null) {
                continue;
            }
            boolean sleeping = !w.shouldTickEntity(mob.getBlockPos());
            Vec3d pos = mob.getPos();
            Vec3d last = LAST_POS.get(id);
            if (last == null || last.squaredDistanceTo(pos) > 2.5 * 2.5 || mob.getTarget() != null) {
                LAST_POS.put(id, pos);
                LAST_MOVE.put(id, now);
            }
            double toBase = Math.sqrt(mob.squaredDistanceTo(base.getX() + 0.5, mob.getY(), base.getZ() + 0.5));
            boolean stuck = now - LAST_MOVE.getOrDefault(id, now) > 400 && toBase > 18;   // 20 с на месте
            if (!sleeping && !stuck) {
                continue;
            }
            // при игроке рядом не телепортируем (было бы видно) — кроме «спящих»
            if (!sleeping && w.getClosestPlayer(mob, 24) != null) {
                continue;
            }
            double angle = Math.atan2(mob.getZ() - base.getZ(), mob.getX() - base.getX());
            BlockPos to = spawnPoint(w, angle);
            if (to != null && to.getSquaredDistance(base) < mob.getBlockPos().getSquaredDistance(base)) {
                w.spawnParticles(ParticleTypes.LARGE_SMOKE, mob.getX(), mob.getY() + 0.5, mob.getZ(), 6, 0.4, 0.5, 0.4, 0.02);
                mob.refreshPositionAfterTeleport(to.getX() + 0.5, to.getY(), to.getZ() + 0.5);
                mob.getNavigation().stop();
                w.spawnParticles(ParticleTypes.LARGE_SMOKE, mob.getX(), mob.getY() + 0.5, mob.getZ(), 6, 0.4, 0.5, 0.4, 0.02);
                LAST_POS.put(id, mob.getPos());
                LAST_MOVE.put(id, now);
            }
        }
        if (changed) {
            s.markDirty();
        }
        LAST_POS.keySet().retainAll(s.waveMobs);
        LAST_MOVE.keySet().retainAll(s.waveMobs);
        MISSING.keySet().retainAll(s.waveMobs);
    }

    /** Сторона света от Базы (для подсказки «враги с севера»). */
    private static Text direction(ServerWorld w, BlockPos at) {
        BlockPos base = basePos(w);
        if (base == null) {
            return Text.translatable("director.basedefense.dir.near");
        }
        double a = Math.toDegrees(Math.atan2(at.getZ() - base.getZ(), at.getX() - base.getX()));
        // 0° — восток (+x), 90° — юг (+z)
        String[] dirs = {"east", "southeast", "south", "southwest", "west", "northwest", "north", "northeast"};
        int i = Math.floorMod(Math.round((float) (a / 45.0)), 8);
        return Text.translatable("director.basedefense.dir." + dirs[i]);
    }

    /** Множитель урона: мобы волны бьют сильнее по акту. */
    public static float damageMul(LivingEntity attacker) {
        if (attacker.getWorld().isClient || attacker.getServer() == null) {
            return 1f;
        }
        Set<String> tags = attacker.getCommandTags();
        if (!tags.contains(TAG)) {
            return 1f;
        }
        return 1f + DirectorTables.damageBonus(DirectorState.get(attacker.getServer()).act);
    }
}

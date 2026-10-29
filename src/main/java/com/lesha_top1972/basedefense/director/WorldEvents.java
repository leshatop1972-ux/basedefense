package com.lesha_top1972.basedefense.director;

import com.lesha_top1972.basedefense.entity.ModEntities;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.block.AbstractFireBlock;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.block.SnowBlock;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.LightningEntity;
import net.minecraft.entity.SpawnReason;
import net.minecraft.entity.mob.MobEntity;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.registry.tag.BlockTags;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import net.minecraft.util.math.random.Random;
import net.minecraft.world.Heightmap;
import net.minecraft.world.LightType;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

/**
 * События мира «Режиссёра» (идут весь день события, кончаются следующим утром):
 * наводнение и ливень (вода поднимается — {@link Flood}), снегопад, жара (деревья и трава загораются),
 * ливень с частыми молниями, вечный туман (туманные сущности вне Базы), кровавая вечная ночь
 * (днём понемногу кровавые сущности), метеорит. Работа — вокруг игроков (там, где мир «живёт»).
 */
public final class WorldEvents {

    /** Туманные сущности тумана: не нападают на жителей, тают у Базы и на свету. */
    public static final String FOG_TAG = "basedefense.fog";
    /** Кровавые сущности кровавой ночи (дневные). */
    public static final String BLOOD_TAG = "basedefense.blood_night";
    /** Вокруг Базы туман и его сущности «не заходят». */
    private static final double BASE_SAFE = 48;
    private static final int FOG_PER_PLAYER = 6;

    private static long weatherDay = -1;

    private WorldEvents() {
    }

    static void init() {
        ServerTickEvents.END_SERVER_TICK.register(WorldEvents::tick);
    }

    private static void tick(MinecraftServer server) {
        DirectorState s = DirectorState.get(server);
        ServerWorld w = server.getOverworld();
        Flood flood = Flood.get(server);
        flood.tickRecede(w);
        long day = Director.day(server);
        if (s.todayDay != day || !Director.isActive(server)) {
            return;
        }
        long t = w.getTimeOfDay() % Director.DAY_TICKS;
        DirectorEvent e = s.today;
        int ticks = server.getTicks();
        if (weatherDay != day && ticks % 20 == 0) {
            weatherDay = day;
            applyWeather(w, e, t);
        }
        switch (e) {
            case STORM -> {
                if (ticks % 20 == 0) {
                    lightning(w);
                }
            }
            case SNOWFALL -> snow(w, t);
            case HEATWAVE -> {
                if (ticks % 10 == 0) {
                    heat(w);
                }
            }
            case ETERNAL_FOG -> {
                if (ticks % 20 == 0) {
                    fogTick(w, ticks);
                }
            }
            case BLOOD_NIGHT -> {
                if (ticks % 100 == 0 && t < WaveManager.START) {
                    bloodDay(w);
                }
            }
            case METEOR -> {
                if (s.meteorDay != day && t >= meteorTime(day)) {
                    s.meteorDay = day;
                    s.markDirty();
                    Meteor.strike(w);
                }
            }
            default -> {
            }
        }
    }

    /** Конец дня события (следующее утро или смена события командой). */
    static void endDay(MinecraftServer server, DirectorState s) {
        DirectorEvent e = s.today;
        ServerWorld w = server.getOverworld();
        Flood.get(server).startRecede();              // остатки воды старого наводнения (если были) уходят
        if (e == DirectorEvent.SNOWFALL || e == DirectorEvent.STORM || e == DirectorEvent.HEATWAVE) {
            w.setWeather(12000, 0, false, false);
        }
        if (e == DirectorEvent.ETERNAL_FOG || e == DirectorEvent.BLOOD_NIGHT) {
            vanishTagged(server, e == DirectorEvent.ETERNAL_FOG ? FOG_TAG : BLOOD_TAG);
        }
        weatherDay = -1;
    }

    private static void applyWeather(ServerWorld w, DirectorEvent e, long t) {
        int rest = (int) Math.max(1200, Director.DAY_TICKS - t);
        switch (e) {
            case SNOWFALL -> w.setWeather(0, rest, true, false);
            case STORM -> w.setWeather(0, rest, true, true);
            case HEATWAVE -> w.setWeather(rest, 0, false, false);
            default -> {
            }
        }
    }

    /** Метеорит падает в случайное время с утра до полудня. */
    private static long meteorTime(long day) {
        return 1500 + Math.floorMod(day * 7919L, 4500L);
    }

    // =====================================================================
    // Снегопад: снег ложится слоями, к вечеру до 3 слоёв
    // =====================================================================

    private static void snow(ServerWorld w, long t) {
        int maxLayers = 1 + (int) Math.min(2, t / 5000);
        Random r = w.getRandom();
        for (ServerPlayerEntity p : w.getPlayers()) {
            for (int i = 0; i < 6; i++) {
                int x = p.getBlockX() + r.nextInt(129) - 64;
                int z = p.getBlockZ() + r.nextInt(129) - 64;
                if (!w.isChunkLoaded(x >> 4, z >> 4)) {
                    continue;
                }
                int y = w.getTopY(Heightmap.Type.MOTION_BLOCKING, x, z);
                BlockPos pos = new BlockPos(x, y, z);
                BlockState here = w.getBlockState(pos);
                BlockState below = w.getBlockState(pos.down());
                if (here.isOf(Blocks.SNOW)) {
                    grow(w, pos, here, maxLayers);
                } else if (below.isOf(Blocks.SNOW)) {
                    grow(w, pos.down(), below, maxLayers);
                } else if (here.isAir() && below.getFluidState().isEmpty()
                        && Blocks.SNOW.getDefaultState().canPlaceAt(w, pos)) {
                    w.setBlockState(pos, Blocks.SNOW.getDefaultState(), Block.NOTIFY_ALL);
                }
            }
        }
    }

    private static void grow(ServerWorld w, BlockPos pos, BlockState st, int max) {
        int layers = st.get(SnowBlock.LAYERS);
        if (layers < max) {
            w.setBlockState(pos, st.with(SnowBlock.LAYERS, layers + 1), Block.NOTIFY_ALL);
        }
    }

    // =====================================================================
    // Жара: листва и трава вспыхивают (у самой Базы — нет)
    // =====================================================================

    private static void heat(ServerWorld w) {
        Random r = w.getRandom();
        BlockPos base = WaveManager.basePos(w);
        for (ServerPlayerEntity p : w.getPlayers()) {
            int x = p.getBlockX() + r.nextInt(129) - 64;
            int z = p.getBlockZ() + r.nextInt(129) - 64;
            if (!w.isChunkLoaded(x >> 4, z >> 4)
                    || (base != null && base.getSquaredDistance(x, base.getY(), z) < 16 * 16)) {
                continue;
            }
            int y = w.getTopY(Heightmap.Type.WORLD_SURFACE, x, z) - 1;
            BlockPos top = new BlockPos(x, y, z);
            BlockState st = w.getBlockState(top);
            BlockPos fire = null;
            if (st.isIn(BlockTags.LEAVES)) {
                BlockPos up = top.up();
                if (AbstractFireBlock.canPlaceAt(w, up, net.minecraft.util.math.Direction.UP)) {
                    fire = up;
                }
            } else if (st.isOf(Blocks.GRASS) || st.isOf(Blocks.FERN) || st.isOf(Blocks.DEAD_BUSH)
                    || st.isIn(BlockTags.SMALL_FLOWERS)) {
                fire = top;                                // трава сгорает на месте
            }
            if (fire != null) {
                w.setBlockState(fire, AbstractFireBlock.getState(w, fire), Block.NOTIFY_ALL);
                w.spawnParticles(ParticleTypes.LARGE_SMOKE, fire.getX() + 0.5, fire.getY() + 0.6, fire.getZ() + 0.5,
                        4, 0.3, 0.2, 0.3, 0.01);
            }
        }
    }

    // =====================================================================
    // Ливень: частые молнии (не ближе 24 блоков к Базе)
    // =====================================================================

    private static void lightning(ServerWorld w) {
        Random r = w.getRandom();
        BlockPos base = WaveManager.basePos(w);
        for (ServerPlayerEntity p : w.getPlayers()) {
            if (r.nextInt(6) != 0) {                      // в среднем раз в 6 с у каждого игрока
                continue;
            }
            int x = p.getBlockX() + r.nextInt(113) - 56;
            int z = p.getBlockZ() + r.nextInt(113) - 56;
            if (!w.isChunkLoaded(x >> 4, z >> 4)
                    || (base != null && base.getSquaredDistance(x, base.getY(), z) < 24 * 24)) {
                continue;
            }
            int y = w.getTopY(Heightmap.Type.MOTION_BLOCKING, x, z);
            LightningEntity bolt = EntityType.LIGHTNING_BOLT.create(w);
            if (bolt != null) {
                bolt.refreshPositionAfterTeleport(Vec3d.ofBottomCenter(new BlockPos(x, y, z)));
                w.spawnEntity(bolt);
            }
        }
    }

    // =====================================================================
    // Вечный туман: туманные сущности вокруг игроков вне Базы и в темноте
    // =====================================================================

    private static void fogTick(ServerWorld w, int ticks) {
        BlockPos base = WaveManager.basePos(w);
        Random r = w.getRandom();
        for (ServerPlayerEntity p : w.getPlayers()) {
            if (p.isSpectator() || p.isCreative()) {
                continue;
            }
            Box around = p.getBoundingBox().expand(64);
            List<MobEntity> mists = w.getEntitiesByClass(MobEntity.class, around,
                    m -> m.getCommandTags().contains(FOG_TAG));
            // у Базы и на свету туманные сущности тают; на игрока у Базы не нападают
            for (MobEntity m : mists) {
                BlockPos at = m.getBlockPos();
                if (nearBase(base, at) || w.getLightLevel(LightType.BLOCK, at) >= 9) {
                    vanish(w, m);
                } else if (m.getTarget() instanceof ServerPlayerEntity tp && nearBase(base, tp.getBlockPos())) {
                    m.setTarget(null);
                }
            }
            if (ticks % 60 != 0 || nearBase(base, p.getBlockPos())
                    || w.getLightLevel(LightType.BLOCK, p.getBlockPos()) >= 8) {
                continue;
            }
            long close = mists.stream().filter(m -> m.isAlive() && m.squaredDistanceTo(p) < 40 * 40).count();
            if (close >= FOG_PER_PLAYER) {
                continue;
            }
            BlockPos at = groundAround(w, p.getBlockPos(), 16, 26, r);
            if (at != null && !nearBase(base, at) && w.getLightLevel(LightType.BLOCK, at) < 6) {
                spawnTagged(w, ModEntities.MIST_ENTITY, at, FOG_TAG);
            }
        }
    }

    private static boolean nearBase(@Nullable BlockPos base, BlockPos at) {
        return base != null && base.getSquaredDistance(at) < BASE_SAFE * BASE_SAFE;
    }

    // =====================================================================
    // Кровавая вечная ночь: днём — понемногу кровавые сущности (до 2 у игрока)
    // =====================================================================

    private static void bloodDay(ServerWorld w) {
        Random r = w.getRandom();
        for (ServerPlayerEntity p : w.getPlayers()) {
            if (p.isSpectator() || p.isCreative() || r.nextInt(6) != 0) {     // примерно раз в 30 с
                continue;
            }
            long count = w.getEntitiesByClass(MobEntity.class, p.getBoundingBox().expand(48),
                    m -> m.getCommandTags().contains(BLOOD_TAG)).size();
            if (count >= 2) {
                continue;
            }
            BlockPos at = groundAround(w, p.getBlockPos(), 20, 30, r);
            if (at != null) {
                Entity e = spawnTagged(w, ModEntities.BLOOD_ENTITY, at, BLOOD_TAG);
                if (e instanceof MobEntity m) {
                    m.setTarget(p);
                }
            }
        }
    }

    // =====================================================================
    // Помощники
    // =====================================================================

    @Nullable
    private static BlockPos groundAround(ServerWorld w, BlockPos c, int min, int max, Random r) {
        for (int i = 0; i < 6; i++) {
            double a = r.nextDouble() * Math.PI * 2;
            double d = min + r.nextDouble() * (max - min);
            int x = MathHelper.floor(c.getX() + Math.cos(a) * d);
            int z = MathHelper.floor(c.getZ() + Math.sin(a) * d);
            BlockPos probe = new BlockPos(x, c.getY(), z);
            if (!w.shouldTickEntity(probe)) {
                continue;
            }
            int y = w.getTopY(Heightmap.Type.MOTION_BLOCKING_NO_LEAVES, x, z);
            BlockPos p = new BlockPos(x, y, z);
            if (w.getFluidState(p.down()).isEmpty() && w.getFluidState(p).isEmpty()) {
                return p;
            }
        }
        return null;
    }

    @Nullable
    private static Entity spawnTagged(ServerWorld w, EntityType<?> type, BlockPos at, String tag) {
        Entity e = type.create(w);
        if (e == null) {
            return null;
        }
        e.refreshPositionAndAngles(at.getX() + 0.5, at.getY(), at.getZ() + 0.5, w.getRandom().nextFloat() * 360f, 0f);
        if (e instanceof MobEntity m) {
            m.initialize(w, w.getLocalDifficulty(at), SpawnReason.EVENT, null, null);
        }
        e.addCommandTag(tag);
        w.spawnEntity(e);
        return e;
    }

    private static void vanish(ServerWorld w, Entity e) {
        w.spawnParticles(ParticleTypes.CLOUD, e.getX(), e.getY() + e.getHeight() / 2, e.getZ(),
                10, e.getWidth() / 2, e.getHeight() / 3, e.getWidth() / 2, 0.02);
        e.discard();
    }

    private static void vanishTagged(MinecraftServer server, String tag) {
        for (ServerWorld w : server.getWorlds()) {
            List<Entity> list = new ArrayList<>();
            for (Entity e : w.iterateEntities()) {
                if (e.getCommandTags().contains(tag)) {
                    list.add(e);
                }
            }
            for (Entity e : list) {
                vanish(w, e);
            }
        }
    }
}

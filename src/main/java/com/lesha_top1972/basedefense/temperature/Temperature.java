package com.lesha_top1972.basedefense.temperature;

import com.lesha_top1972.basedefense.Basedefense;
import com.lesha_top1972.basedefense.clothing.Clothing;
import com.lesha_top1972.basedefense.clothing.ClothingItem;
import com.lesha_top1972.basedefense.clothing.Warmth;
import com.lesha_top1972.basedefense.entity.settler.SettlerEntity;
import com.lesha_top1972.basedefense.season.Season;
import com.lesha_top1972.basedefense.season.Seasons;
import com.mojang.brigadier.context.CommandContext;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.PacketByteBufs;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.block.AbstractFurnaceBlock;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.block.CampfireBlock;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.inventory.Inventory;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.registry.tag.FluidTags;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.command.CommandManager;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.MathHelper;
import net.minecraft.world.World;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Температура воздуха (°C) у игроков и поселенцев. Считает сервер: игроку раз в секунду приходит пакет
 * (значок и число справа от еды), у поселенца — в его данных (видно в меню).
 * <p>
 * Как считается:
 * <ol>
 *   <li>Сезон задаёт промежуток для обычного леса (лето 22…35, осень 0…18, зима −30…0, весна 3…22). Внутри него
 *       температура гуляет: день теплее ночи (пик в полдень), у каждого дня своя «погода» (тёплый/холодный день),
 *       плюс небольшие колебания по часам. В 1‑й день сезона промежуток плавно переходит от прошлого сезона.</li>
 *   <li>Биом: ({базовая температура биома} − 0.7) × 12 °C — пустыня/саванна ≈ +16, джунгли +3, равнины +1,
 *       тайга −5, горы −6, снежные равнины −8, ледяные пики −17.</li>
 *   <li>Место: высоко (выше Y 80) −1° на каждые 8 блоков; в пещере (без неба, ниже уровня моря) — к постоянным 12°;
 *       под крышей +3; под дождём/снегом −3; в воде −6; рядом огонь, костёр, лава, горящая печь, магма, факелы —
 *       теплее (до +25).</li>
 *   <li>Незер — 45°, Энд — −12°.</li>
 * </ol>
 * Комфорт зависит от одежды ({@link Warmth}). Дебафы за холод/жару — позже.
 * Команда /temperature — подробно, откуда взялась температура.
 */
public final class Temperature {

    public static final Identifier SYNC = new Identifier(Basedefense.MOD_ID, "temperature_sync");

    /** Биом: градусов на единицу «температуры биома» от леса (0.7). */
    private static final float BIOME_K = 12f;
    private static final float BIOME_MIN = -25f;
    private static final float BIOME_MAX = 18f;
    /** Высота: выше этого Y холодает на 1° каждые HEIGHT_STEP блоков. */
    private static final int HEIGHT_FROM = 80;
    private static final float HEIGHT_STEP = 8f;
    /** Пещеры: постоянная температура глубоко под землёй. */
    private static final float CAVE_TEMP = 12f;
    private static final float ROOF_BONUS = 3f;
    private static final float RAIN = -3f;
    private static final float WATER = -6f;
    private static final float HEAT_MAX = 25f;
    private static final float NETHER = 45f;
    private static final float END = -12f;
    /** Доли: «погода дня», ход дня/ночи, колебания по часам. */
    private static final double DAY_WEIGHT = 0.5;
    private static final double DIURNAL_WEIGHT = 0.5;
    private static final double WOBBLE = 0.12;

    private Temperature() {
    }

    public static void init() {
        ServerTickEvents.END_SERVER_TICK.register(Temperature::tick);
        CommandRegistrationCallback.EVENT.register((dispatcher, registry, env) -> dispatcher.register(
                CommandManager.literal("temperature").executes(Temperature::cmdInfo)));
    }

    // =====================================================================
    // Расчёт
    // =====================================================================

    /** Температура воздуха в точке. why != null — сюда пишутся слагаемые (для команды). */
    public static float ambient(ServerWorld w, BlockPos pos, @Nullable List<Text> why) {
        if (w.getRegistryKey() == World.NETHER) {
            return NETHER;
        }
        if (w.getRegistryKey() == World.END) {
            return END;
        }
        long time = w.getServer().getOverworld().getTimeOfDay();
        long offset = Seasons.serverOffset();
        Season season = Seasons.seasonAt(time, offset);
        int dayIn = Seasons.dayInSeason(time, offset);
        double dayFrac = Math.floorMod(time, 24000L) / 24000.0;

        // Промежуток сезона; в 1‑й день — плавно от прошлого сезона
        float min = season.tMin;
        float max = season.tMax;
        if (dayIn == 0) {
            Season prev = season.prev();
            min = (float) MathHelper.lerp(dayFrac, prev.tMin, season.tMin);
            max = (float) MathHelper.lerp(dayFrac, prev.tMax, season.tMax);
        }
        long day = Seasons.worldDay(time);
        double dayNoise = MathHelper.lerp(dayFrac, noise(day), noise(day + 1));
        // 6000 — полдень (теплее всего), 18000 — полночь
        double diurnal = (Math.cos(2 * Math.PI * (Math.floorMod(time, 24000L) - 6000) / 24000.0) + 1) / 2;
        long hour = Math.floorDiv(time, 1000L);
        double wobble = (MathHelper.lerp((Math.floorMod(time, 1000L)) / 1000.0, noise(hour * 31 + 7), noise(hour * 31 + 38))
                - 0.5) * 2 * WOBBLE;
        double f = MathHelper.clamp(DAY_WEIGHT * dayNoise + DIURNAL_WEIGHT * diurnal + wobble, 0, 1);
        float t = (float) (min + (max - min) * f);
        add(why, "season", t, Text.translatable("season.basedefense." + season.id));

        float biome = MathHelper.clamp((w.getBiome(pos).value().getTemperature() - 0.7f) * BIOME_K, BIOME_MIN, BIOME_MAX);
        t += biome;
        add(why, "biome", biome, null);

        if (pos.getY() > HEIGHT_FROM) {
            float h = -(pos.getY() - HEIGHT_FROM) / HEIGHT_STEP;
            t += h;
            add(why, "height", h, null);
        }
        boolean sky = w.isSkyVisible(pos.up());
        if (!sky) {
            int sea = w.getSeaLevel();
            float deep = MathHelper.clamp((sea - 4 - pos.getY()) / 16f, 0f, 1f);
            if (deep > 0) {
                float before = t;
                t = MathHelper.lerp(deep, t, CAVE_TEMP);
                add(why, "cave", t - before, null);
            } else {
                t += ROOF_BONUS;
                add(why, "roof", ROOF_BONUS, null);
            }
        } else if (w.hasRain(pos.up())) {
            t += RAIN;
            add(why, "rain", RAIN, null);
        }
        if (w.getFluidState(pos).isIn(FluidTags.WATER) || w.getFluidState(pos.up()).isIn(FluidTags.WATER)) {
            t += WATER;
            add(why, "water", WATER, null);
        }
        float heat = heat(w, pos);
        if (heat > 0) {
            t += heat;
            add(why, "heat", heat, null);
        }
        return t;
    }

    /** Тепло от огня, костров, лавы, горящих печей, магмы и факелов рядом (до HEAT_MAX). */
    private static float heat(ServerWorld w, BlockPos pos) {
        float sum = 0;
        BlockPos.Mutable m = new BlockPos.Mutable();
        for (int dx = -3; dx <= 3; dx++) {
            for (int dy = -2; dy <= 2; dy++) {
                for (int dz = -3; dz <= 3; dz++) {
                    m.set(pos.getX() + dx, pos.getY() + dy, pos.getZ() + dz);
                    float v = heatOf(w.getBlockState(m));
                    if (v > 0) {
                        double d = Math.sqrt(dx * dx + dy * dy + dz * dz);
                        sum += v * (float) Math.max(0, 1 - d / 4.5);
                    }
                }
            }
        }
        return Math.min(HEAT_MAX, sum);
    }

    private static float heatOf(BlockState st) {
        if (st.isAir()) {
            return 0;
        }
        if (st.isOf(Blocks.LAVA)) {
            return 14;
        }
        if (st.isOf(Blocks.FIRE) || st.isOf(Blocks.SOUL_FIRE)) {
            return 10;
        }
        if (st.getBlock() instanceof CampfireBlock) {
            return st.get(CampfireBlock.LIT) ? 12 : 0;
        }
        if (st.getBlock() instanceof AbstractFurnaceBlock) {
            return st.get(AbstractFurnaceBlock.LIT) ? 7 : 0;
        }
        if (st.isOf(Blocks.MAGMA_BLOCK)) {
            return 4;
        }
        if (st.isOf(Blocks.TORCH) || st.isOf(Blocks.WALL_TORCH) || st.isOf(Blocks.LANTERN)) {
            return 1.5f;
        }
        return 0;
    }

    /** Псевдослучайное число 0…1 для дня/часа (одинаковое для всех игроков). */
    private static double noise(long k) {
        long h = k * 0x9E3779B97F4A7C15L;
        h ^= h >>> 32;
        h *= 0xD6E8FEB86659FD93L;
        h ^= h >>> 32;
        return (h >>> 11) * 0x1.0p-53;
    }

    private static void add(@Nullable List<Text> why, String key, float v, @Nullable Text arg) {
        if (why != null) {
            String num = String.format(Locale.ROOT, "%+.1f", v);
            why.add(arg == null
                    ? Text.translatable("message.basedefense.temperature.part." + key, num)
                    : Text.translatable("message.basedefense.temperature.part." + key, arg, String.format(Locale.ROOT, "%.1f", v)));
        }
    }

    // =====================================================================
    // Комфорт
    // =====================================================================

    /** Самая тёплая надетая одежда (у игрока и поселенца). */
    public static Warmth warmth(LivingEntity e) {
        Inventory inv = e instanceof PlayerEntity p ? Clothing.of(p)
                : e instanceof SettlerEntity s ? s.getClothing() : null;
        Warmth best = Warmth.NONE;
        if (inv != null) {
            for (int i = 0; i < inv.size(); i++) {
                if (inv.getStack(i).getItem() instanceof ClothingItem c && c.getWarmth().ordinal() > best.ordinal()) {
                    best = c.getWarmth();
                }
            }
        }
        return best;
    }

    public static TempStatus status(LivingEntity e, float temp) {
        Warmth w = warmth(e);
        return TempStatus.of(temp, w.comfortMin, w.comfortMax);
    }

    // =====================================================================
    // Игроки: раз в секунду — пакет на клиент
    // =====================================================================

    private static void tick(MinecraftServer server) {
        if (server.getTicks() % 20 != 3) {
            return;
        }
        for (ServerPlayerEntity p : server.getPlayerManager().getPlayerList()) {
            float t = ambient(p.getServerWorld(), p.getBlockPos(), null);
            Warmth w = warmth(p);
            PacketByteBuf buf = PacketByteBufs.create();
            buf.writeFloat(t);
            buf.writeByte(TempStatus.of(t, w.comfortMin, w.comfortMax).ordinal());
            buf.writeByte((int) w.comfortMin);
            buf.writeByte((int) w.comfortMax);
            ServerPlayNetworking.send(p, SYNC, buf);
        }
    }

    private static int cmdInfo(CommandContext<ServerCommandSource> ctx) {
        ServerCommandSource src = ctx.getSource();
        BlockPos pos = BlockPos.ofFloored(src.getPosition());
        List<Text> why = new ArrayList<>();
        float t = ambient(src.getWorld(), pos, why);
        Warmth w = src.getEntity() instanceof LivingEntity le ? warmth(le) : Warmth.NONE;
        TempStatus st = TempStatus.of(t, w.comfortMin, w.comfortMax);
        src.sendFeedback(() -> Text.translatable("message.basedefense.temperature.info",
                String.format(Locale.ROOT, "%.1f", t),
                Text.translatable("temperature.basedefense.status." + st.name().toLowerCase(Locale.ROOT))
                        .formatted(color(st)),
                (int) w.comfortMin, (int) w.comfortMax), false);
        for (Text line : why) {
            src.sendFeedback(() -> Text.literal("  ").append(line).formatted(Formatting.GRAY), false);
        }
        return 1;
    }

    public static Formatting color(TempStatus s) {
        return switch (s) {
            case FREEZING -> Formatting.BLUE;
            case COLD -> Formatting.AQUA;
            case COMFORT -> Formatting.GREEN;
            case HOT -> Formatting.GOLD;
            case BURNING -> Formatting.RED;
        };
    }
}

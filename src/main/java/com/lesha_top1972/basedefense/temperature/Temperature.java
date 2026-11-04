package com.lesha_top1972.basedefense.temperature;

import com.lesha_top1972.basedefense.clothing.Clothing;
import com.lesha_top1972.basedefense.clothing.ClothingItem;
import com.lesha_top1972.basedefense.entity.settler.SettlerEntity;
import com.lesha_top1972.basedefense.needs.Needs;
import com.lesha_top1972.basedefense.season.Season;
import com.lesha_top1972.basedefense.season.Seasons;
import com.mojang.brigadier.context.CommandContext;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.minecraft.block.AbstractFurnaceBlock;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.block.CampfireBlock;
import net.minecraft.block.LeavesBlock;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.inventory.Inventory;
import net.minecraft.registry.tag.FluidTags;
import net.minecraft.server.command.CommandManager;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.MathHelper;
import net.minecraft.world.World;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Температура воздуха (°C) там, где стоит игрок или поселенец. Считает сервер раз в секунду (см. {@link Needs});
 * «ощущаемая» температура персонажа тянется к ней плавно — вышел из дома на мороз, и холодает за ~минуту.
 * <h3>Как считается</h3>
 * <ol>
 *   <li><b>Сезон</b> — промежуток для обычного леса (лето 22…35, осень 0…18, зима −30…0, весна 3…22). Внутри него:
 *       день теплее ночи (пик в полдень), у каждого дня своя «погода», небольшие колебания по часам; в 1‑й день
 *       сезона промежуток плавно переходит от прошлого.</li>
 *   <li><b>Биом</b>: (температура биома − 0.7) × 12 — пустыня/саванна ≈ +16, джунгли +3, равнины +1, тайга −5,
 *       горы −6, снежные равнины −8, ледяные пики −17.</li>
 *   <li><b>Высота</b>: выше Y 80 — −1° на каждые 8 блоков. <b>Пещера</b> (без неба, ниже уровня моря) — к 12°.</li>
 *   <li><b>Укрытие</b> (см. {@link #shelter}): крыша над головой (до 16 блоков вверх; листва — только частично)
 *       и стены вокруг (16 лучей по сторонам на двух высотах до 10 блоков; открытая дверь — дыра). Под крышей нет
 *       дождя и снега. Закрытое помещение «держит» температуру: тянет её на 45% к 14° (зимой теплее, летом прохладнее),
 *       а тепло от огня внутри собирается — радиус и сила обогрева растут вдвое.</li>
 *   <li><b>Осадки</b> под открытым небом −3°, <b>ветер</b> на открытом месте зимой/осенью −2°, <b>вода</b> −6°.</li>
 *   <li><b>Огонь рядом</b>: лава 14, костёр 12, огонь 10, горящая печь 7, магма 4, факел/фонарь 1.5 — слабеет
 *       с расстоянием, вместе не больше +30.</li>
 *   <li>Незер — 45°, Энд — −12°.</li>
 * </ol>
 * <b>Комфорт</b> зависит от одежды: без неё 15…27 °C, каждая вещь сдвигает границы (см. {@link ClothingItem}).
 */
public final class Temperature {

    // ---------- Биом и место ----------
    private static final float BIOME_K = 12f;
    private static final float BIOME_MIN = -25f;
    private static final float BIOME_MAX = 18f;
    private static final int HEIGHT_FROM = 80;
    private static final float HEIGHT_STEP = 8f;
    private static final float CAVE_TEMP = 12f;
    public static final float RAIN = -3f;
    private static final float WIND = -2f;
    private static final float WATER = -6f;
    private static final float NETHER = 45f;
    private static final float END = -12f;
    // ---------- Укрытие ----------
    /** Закрытое помещение тянет температуру к HOUSE_TEMP на эту долю. */
    private static final float HOUSE_PULL = 0.45f;
    private static final float HOUSE_TEMP = 14f;
    private static final int ROOF_SCAN = 16;
    private static final int WALL_SCAN = 10;
    /** С какого «закрытости» место считается помещением (для комфорта и подписи). */
    public static final float INDOORS = 0.6f;
    // ---------- Огонь ----------
    private static final float HEAT_MAX = 30f;
    private static final float HEAT_RADIUS = 4.5f;
    // ---------- Колебания ----------
    private static final double DAY_WEIGHT = 0.5;
    private static final double DIURNAL_WEIGHT = 0.5;
    private static final double WOBBLE = 0.12;
    // ---------- Комфорт без одежды ----------
    public static final float NAKED_MIN = 15f;
    public static final float NAKED_MAX = 27f;

    /** Температура и насколько место закрыто (0 — чистое поле, 1 — комната с крышей и стенами). */
    public record Sample(float temp, float enclosure, boolean rain) {
    }

    private Temperature() {
    }

    public static void init() {
        CommandRegistrationCallback.EVENT.register((dispatcher, registry, env) -> dispatcher.register(
                CommandManager.literal("temperature").executes(Temperature::cmdInfo)));
    }

    // =====================================================================
    // Расчёт
    // =====================================================================

    public static Sample sample(ServerWorld w, BlockPos pos, @Nullable List<Text> why) {
        if (w.getRegistryKey() == World.NETHER) {
            return new Sample(NETHER, 0f, false);
        }
        if (w.getRegistryKey() == World.END) {
            return new Sample(END, 0f, false);
        }
        long time = w.getServer().getOverworld().getTimeOfDay();
        long offset = Seasons.serverOffset();
        Season season = Seasons.seasonAt(time, offset);
        int dayIn = Seasons.dayInSeason(time, offset);
        double dayFrac = Math.floorMod(time, 24000L) / 24000.0;

        // 1. Сезон (в 1‑й день — плавно от прошлого)
        float min = season.tMin;
        float max = season.tMax;
        if (dayIn == 0) {
            Season prev = season.prev();
            min = (float) MathHelper.lerp(dayFrac, prev.tMin, season.tMin);
            max = (float) MathHelper.lerp(dayFrac, prev.tMax, season.tMax);
        }
        long day = Seasons.worldDay(time);
        double dayNoise = MathHelper.lerp(dayFrac, noise(day), noise(day + 1));
        double diurnal = (Math.cos(2 * Math.PI * (Math.floorMod(time, 24000L) - 6000) / 24000.0) + 1) / 2;
        long hour = Math.floorDiv(time, 1000L);
        double wobble = (MathHelper.lerp(Math.floorMod(time, 1000L) / 1000.0, noise(hour * 31 + 7),
                noise(hour * 31 + 38)) - 0.5) * 2 * WOBBLE;
        double f = MathHelper.clamp(DAY_WEIGHT * dayNoise + DIURNAL_WEIGHT * diurnal + wobble, 0, 1);
        float t = (float) (min + (max - min) * f);
        add(why, "season", t, Text.translatable("season.basedefense." + season.id));

        // 2. Биом
        float biome = MathHelper.clamp((w.getBiome(pos).value().getTemperature() - 0.7f) * BIOME_K, BIOME_MIN, BIOME_MAX);
        t += biome;
        add(why, "biome", biome, null);

        // 3. Высота
        if (pos.getY() > HEIGHT_FROM) {
            float h = -(pos.getY() - HEIGHT_FROM) / HEIGHT_STEP;
            t += h;
            add(why, "height", h, null);
        }

        // 4. Укрытие / пещера / погода
        BlockPos head = pos.up();
        float roof = roof(w, head);
        float walls = walls(w, pos);
        float enclosure = roof * walls;
        float deep = roof >= 1f ? MathHelper.clamp((w.getSeaLevel() - 4 - pos.getY()) / 16f, 0f, 1f) : 0f;
        if (deep > 0) {
            float before = t;
            t = MathHelper.lerp(deep, t, CAVE_TEMP);
            add(why, "cave", t - before, null);
            enclosure = Math.max(enclosure, deep);
        } else if (enclosure > 0.05f) {
            float before = t;
            t = MathHelper.lerp(enclosure * HOUSE_PULL, t, HOUSE_TEMP);
            add(why, "house", t - before, Text.literal(Math.round(enclosure * 100) + "%"));
        }
        boolean rained = false;
        if (roof < 0.5f) {
            if (w.hasRain(head)) {
                rained = true;
                t += RAIN;
                add(why, "rain", RAIN, null);
            }
            if ((season == Season.WINTER || season == Season.AUTUMN) && walls < 0.4f) {
                t += WIND;
                add(why, "wind", WIND, null);
            }
        }
        if (w.getFluidState(pos).isIn(FluidTags.WATER) || w.getFluidState(head).isIn(FluidTags.WATER)) {
            t += WATER;
            add(why, "water", WATER, null);
        }

        // 5. Огонь (в помещении — дальше и сильнее)
        float heat = heat(w, pos, enclosure);
        if (heat > 0) {
            t += heat;
            add(why, "heat", heat, null);
        }
        return new Sample(t, enclosure, rained);
    }

    public static float ambient(ServerWorld w, BlockPos pos) {
        return sample(w, pos, null).temp();
    }

    /** Крыша: 1 — сплошной блок над головой (до 16 вверх), 0.4 — только листва, 0 — открытое небо. */
    private static float roof(ServerWorld w, BlockPos head) {
        BlockPos.Mutable m = head.mutableCopy();
        float best = 0;
        for (int i = 1; i <= ROOF_SCAN; i++) {
            m.move(Direction.UP);
            BlockState st = w.getBlockState(m);
            if (st.isAir()) {
                continue;
            }
            if (st.getBlock() instanceof LeavesBlock) {
                best = Math.max(best, 0.4f);
            } else if (blocksAir(w, m, st)) {
                return 1f;
            }
        }
        return best;
    }

    /** Стены: доля из 16 лучей (8 сторон × 2 высоты), упёршихся в стену не дальше 10 блоков. */
    private static float walls(ServerWorld w, BlockPos pos) {
        int hits = 0;
        BlockPos.Mutable m = new BlockPos.Mutable();
        for (int dy = 0; dy <= 1; dy++) {
            for (int dir = 0; dir < 8; dir++) {
                int dx = switch (dir) { case 0, 1, 7 -> 1; case 3, 4, 5 -> -1; default -> 0; };
                int dz = switch (dir) { case 1, 2, 3 -> 1; case 5, 6, 7 -> -1; default -> 0; };
                for (int i = 1; i <= WALL_SCAN; i++) {
                    m.set(pos.getX() + dx * i, pos.getY() + dy, pos.getZ() + dz * i);
                    BlockState st = w.getBlockState(m);
                    if (!st.isAir() && !(st.getBlock() instanceof LeavesBlock) && blocksAir(w, m, st)) {
                        hits++;
                        break;
                    }
                }
            }
        }
        return hits / 16f;
    }

    /** Не пропускает воздух: блок с непустой формой столкновения (стекло, панели, закрытые двери — да; открытая дверь — нет). */
    private static boolean blocksAir(ServerWorld w, BlockPos p, BlockState st) {
        if (st.contains(net.minecraft.state.property.Properties.OPEN) && st.get(net.minecraft.state.property.Properties.OPEN)) {
            return false;   // открытая дверь, калитка, люк
        }
        return !st.getCollisionShape(w, p).isEmpty() || st.getFluidState().isIn(FluidTags.LAVA);
    }

    /** Тепло от огня рядом: в помещении радиус и сила растут (до ×2). */
    private static float heat(ServerWorld w, BlockPos pos, float enclosure) {
        float radius = HEAT_RADIUS * (1 + enclosure * 0.7f);
        int r = MathHelper.ceil(radius);
        float sum = 0;
        BlockPos.Mutable m = new BlockPos.Mutable();
        for (int dx = -r; dx <= r; dx++) {
            for (int dy = -2; dy <= 2; dy++) {
                for (int dz = -r; dz <= r; dz++) {
                    m.set(pos.getX() + dx, pos.getY() + dy, pos.getZ() + dz);
                    float v = heatOf(w.getBlockState(m));
                    if (v > 0) {
                        double d = Math.sqrt(dx * dx + dy * dy + dz * dz);
                        sum += v * (float) Math.max(0, 1 - d / radius);
                    }
                }
            }
        }
        return Math.min(HEAT_MAX, sum * (1 + enclosure));
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
                    : Text.translatable("message.basedefense.temperature.part." + key, arg, num));
        }
    }

    // =====================================================================
    // Одежда и комфорт
    // =====================================================================

    @Nullable
    public static Inventory clothing(LivingEntity e) {
        return e instanceof PlayerEntity p ? Clothing.of(p) : e instanceof SettlerEntity s ? s.getClothing() : null;
    }

    /** Комфортная температура {мин, макс}: без одежды 15…27, вещи складываются. */
    public static float[] comfort(LivingEntity e) {
        float min = NAKED_MIN;
        float max = NAKED_MAX;
        Inventory inv = clothing(e);
        if (inv != null) {
            for (int i = 0; i < inv.size(); i++) {
                if (inv.getStack(i).getItem() instanceof ClothingItem c) {
                    min -= c.getCold();
                    max -= c.getHeat();
                }
            }
        }
        return new float[]{min, max};
    }

    /** Сколько слотов одежды занято. */
    public static int clothingPieces(LivingEntity e) {
        Inventory inv = clothing(e);
        int n = 0;
        if (inv != null) {
            for (int i = 0; i < inv.size(); i++) {
                if (inv.getStack(i).getItem() instanceof ClothingItem) {
                    n++;
                }
            }
        }
        return n;
    }

    // =====================================================================
    // Команда
    // =====================================================================

    private static int cmdInfo(CommandContext<ServerCommandSource> ctx) {
        ServerCommandSource src = ctx.getSource();
        BlockPos pos = BlockPos.ofFloored(src.getPosition());
        List<Text> why = new ArrayList<>();
        Sample s = sample(src.getWorld(), pos, why);
        float[] c = src.getEntity() instanceof LivingEntity le ? comfort(le) : new float[]{NAKED_MIN, NAKED_MAX};
        float felt = src.getEntity() instanceof PlayerEntity p ? Needs.felt(p, s.temp()) : s.temp();
        TempStatus st = TempStatus.of(felt, c[0], c[1]);
        src.sendFeedback(() -> Text.translatable("message.basedefense.temperature.info",
                fmt(s.temp()), fmt(felt),
                Text.translatable("temperature.basedefense.status." + st.name().toLowerCase(Locale.ROOT)).formatted(color(st)),
                fmt(c[0]), fmt(c[1])), false);
        for (Text line : why) {
            src.sendFeedback(() -> Text.literal("  ").append(line).formatted(Formatting.GRAY), false);
        }
        return 1;
    }

    private static String fmt(float v) {
        return String.format(Locale.ROOT, "%.1f", v);
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

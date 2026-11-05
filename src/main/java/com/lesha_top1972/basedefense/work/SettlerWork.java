package com.lesha_top1972.basedefense.work;

import com.lesha_top1972.basedefense.clothing.BackpackItem;
import com.lesha_top1972.basedefense.clothing.BackpackScreenHandler;
import com.lesha_top1972.basedefense.clothing.Clothing;
import com.lesha_top1972.basedefense.entity.settler.GuardEntity;
import com.lesha_top1972.basedefense.farming.Crops;
import com.lesha_top1972.basedefense.temperature.Temperature;
import net.fabricmc.fabric.api.tag.convention.v1.ConventionalBlockTags;
import net.minecraft.block.AbstractFurnaceBlock;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.block.CampfireBlock;
import net.minecraft.block.CropBlock;
import net.minecraft.block.LeavesBlock;
import net.minecraft.block.SmokerBlock;
import net.minecraft.block.entity.AbstractFurnaceBlockEntity;
import net.minecraft.block.entity.BarrelBlockEntity;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.block.entity.ChestBlockEntity;
import net.minecraft.entity.ItemEntity;
import net.minecraft.inventory.Inventory;
import net.minecraft.inventory.SimpleInventory;
import net.minecraft.item.AxeItem;
import net.minecraft.item.BlockItem;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.PickaxeItem;
import net.minecraft.item.ShovelItem;
import net.minecraft.item.ToolItem;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtElement;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.recipe.AbstractCookingRecipe;
import net.minecraft.recipe.RecipeType;
import net.minecraft.registry.tag.BlockTags;
import net.minecraft.registry.tag.ItemTags;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;
import net.minecraft.util.Hand;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.Heightmap;
import net.minecraft.world.chunk.WorldChunk;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.WeakHashMap;
import java.util.function.Predicate;

/**
 * Работа поселенца (как в RimWorld): приоритеты работ 1…4 (0 — не делает), приказ «сделать сейчас», навыки.
 * <p>
 * <b>Область работы</b> — круг радиусом {@link #radius} вокруг «дома» поселенца (в меню: «Центр — здесь»).
 * <b>Склад</b> — все сундуки и бочки в этой области: туда относит добытое, оттуда берёт семена, саженцы, сырую
 * еду, топливо, инструменты и еду для себя.
 * <b>Руки</b> — 4 ячейки под добытое (видно в меню работы); если надет рюкзак — ещё и он.
 * <p>
 * Порядок решения (раз в 2 с, когда нет врагов, не на посту и меню закрыто):
 * <ol>
 *   <li>нет еды в слотах еды и голоден — берёт еду со склада;</li>
 *   <li>руки и рюкзак полны — относит на склад;</li>
 *   <li>приказ (если есть) — только эта работа; иначе работы по приоритету;</li>
 *   <li>делать нечего, но что‑то несёт — относит на склад.</li>
 * </ol>
 */
public class SettlerWork {

    public static final int RADIUS_MIN = 8;
    public static final int RADIUS_MAX = 48;
    public static final int RADIUS_DEFAULT = 24;
    public static final int CARRY_SIZE = 4;

    // ---------- Баланс: время в тиках при скорости 1.0 ----------
    private static final int HARVEST_TICKS = 30;
    private static final int PLANT_TICKS = 15;
    /** На бревно при «скорости топора» 4 (каменный); железный 6 → ×0.67, алмазный 8 → ×0.5. */
    private static final int CHOP_TICKS = 40;
    /** Блок: твёрдость × MINE_K / скорость инструмента (камень железной киркой ≈ 20 тиков). */
    private static final float MINE_K = 80f;
    private static final int COOK_TICKS = 60;
    private static final int STORE_TICKS = 10;
    // ---------- Опыт ----------
    private static final float XP_HARVEST = 40f;
    private static final float XP_PLANT = 12f;
    private static final float XP_LOG = 20f;
    private static final float XP_BLOCK = 12f;
    private static final float XP_ORE = 30f;
    private static final float XP_COOK = 25f;
    // ---------- Прочее ----------
    private static final double SPEED = 0.7;
    private static final int STUCK_TICKS = 300;
    private static final int SEARCH_EVERY = 40;
    private static final int FORCED_GIVE_UP = 400;
    private static final int MAX_TREE_LOGS = 96;
    private static final int FARM_DY = 4;
    private static final int FOOD_WANT = 4;

    private final GuardEntity g;
    public final SettlerSkills skills = new SettlerSkills();
    private final int[] priority = new int[WorkType.values().length];
    /** Почему работа сейчас не делается (ключ перевода или null). */
    private final String[] problem = new String[WorkType.values().length];
    private int radius = RADIUS_DEFAULT;
    @Nullable
    private WorkType forced;
    private int forcedIdle;
    private final SimpleInventory carry = new SimpleInventory(CARRY_SIZE);

    @Nullable
    private Job job;
    private int searchCooldown;
    private String status = "idle";
    private final Map<BlockPos, Long> blacklist = new HashMap<>();
    private List<BlockPos> storageCache = List.of();
    private long storageAge = -1000;
    private List<BlockPos> farmCache = List.of();
    private long farmAge = -1000;

    private static final Map<ServerWorld, Map<BlockPos, long[]>> RESERVED = new WeakHashMap<>();

    public SettlerWork(GuardEntity g) {
        this.g = g;
        for (WorkType t : WorkType.values()) {
            priority[t.ordinal()] = t.defaultPriority;
        }
    }

    // =====================================================================
    // Доступ для меню
    // =====================================================================

    public int priority(WorkType t) {
        return priority[t.ordinal()];
    }

    public void setPriority(WorkType t, int p) {
        priority[t.ordinal()] = Math.max(0, Math.min(4, p));
    }

    public int radius() {
        return radius;
    }

    public void setRadius(int r) {
        radius = Math.max(RADIUS_MIN, Math.min(RADIUS_MAX, r));
        storageAge = -1000;
        farmAge = -1000;
    }

    @Nullable
    public WorkType forced() {
        return forced;
    }

    /** Приказ «сделать сейчас» (null — отменить). */
    public void order(@Nullable WorkType t) {
        forced = t;
        forcedIdle = 0;
        cancel();
        searchCooldown = 0;
    }

    public String status() {
        return status;
    }

    @Nullable
    public String problem(WorkType t) {
        return problem[t.ordinal()];
    }

    public SimpleInventory carry() {
        return carry;
    }

    public BlockPos center() {
        return g.getHome() != null ? g.getHome() : g.getBlockPos();
    }

    /** Прервать текущее дело (враг, меню, приказ). */
    public void cancel() {
        if (job != null) {
            job.end();
            job = null;
        }
    }

    private ServerWorld world() {
        return (ServerWorld) g.getWorld();
    }

    private long now() {
        return world().getTime();
    }

    // =====================================================================
    // Главный цикл (из GuardBrain, когда поселенец свободен)
    // =====================================================================

    /** @return true — занят работой (не гулять). */
    public boolean tick() {
        if (job != null) {
            if (job.tick()) {
                return true;
            }
            job.end();
            job = null;
            searchCooldown = 5;
        }
        if (--searchCooldown > 0) {
            return false;
        }
        searchCooldown = SEARCH_EVERY;
        job = pick();
        if (job != null) {
            return true;
        }
        status = forced != null ? "forced_none" : "idle";
        if (forced != null) {
            forcedIdle += SEARCH_EVERY;
            if (forcedIdle >= FORCED_GIVE_UP) {
                tell(Text.translatable("message.basedefense.work.order_done", g.getDisplayName(),
                        Text.translatable("work.basedefense." + forced.id)));
                forced = null;
            }
        }
        return false;
    }

    @Nullable
    private Job pick() {
        Job j;
        if (wantsFood() && (j = FetchFood.find(this)) != null) {
            status = "eat";
            return j;
        }
        if (!hasFreeSpace() && (j = Unload.find(this)) != null) {
            status = "unload";
            return j;
        }
        List<WorkType> order = new ArrayList<>();
        if (forced != null) {
            order.add(forced);
        } else {
            for (int p = 1; p <= 4; p++) {
                for (WorkType t : WorkType.values()) {
                    if (priority[t.ordinal()] == p) {
                        order.add(t);
                    }
                }
            }
        }
        for (WorkType t : order) {
            problem[t.ordinal()] = null;
            j = switch (t) {
                case COOKING -> Cook.find(this);
                case FARMING -> Farm.find(this);
                case WOODCUTTING -> Chop.find(this);
                case MINING -> Mine.find(this);
                case HAULING -> Haul.find(this);
            };
            if (j != null) {
                status = t.id;
                if (forced == t) {
                    forcedIdle = 0;
                }
                return j;
            }
        }
        if (hasProduce() && (j = Unload.find(this)) != null) {
            status = "unload";
            return j;
        }
        return null;
    }

    private void tell(Text msg) {
        for (ServerPlayerEntity p : world().getPlayers(p -> p.squaredDistanceTo(g) < 48 * 48)) {
            p.sendMessage(msg, false);
        }
    }

    // =====================================================================
    // Руки, рюкзак, инструменты
    // =====================================================================

    @Nullable
    private BackpackScreenHandler.Contents backpack() {
        ItemStack bp = Clothing.backpack(g.getClothing());
        if (bp.getItem() instanceof BackpackItem b) {
            return new BackpackScreenHandler.Contents(bp, b.getRows() * 9);
        }
        return null;
    }

    private List<Inventory> holds() {
        List<Inventory> l = new ArrayList<>();
        l.add(carry);
        Inventory bp = backpack();
        if (bp != null) {
            l.add(bp);
        }
        return l;
    }

    private static boolean isTool(ItemStack s) {
        return s.getItem() instanceof ToolItem;
    }

    boolean hasFreeSpace() {
        for (Inventory inv : holds()) {
            for (int i = 0; i < inv.size(); i++) {
                if (inv.getStack(i).isEmpty()) {
                    return true;
                }
            }
        }
        return false;
    }

    boolean hasProduce() {
        for (Inventory inv : holds()) {
            for (int i = 0; i < inv.size(); i++) {
                ItemStack s = inv.getStack(i);
                if (!s.isEmpty() && !isTool(s)) {
                    return true;
                }
            }
        }
        return false;
    }

    /** Положить в руки/рюкзак; остаток — на землю. */
    void store(ItemStack s) {
        for (Inventory inv : holds()) {
            if (s.isEmpty()) {
                return;
            }
            s = insert(inv, s);
        }
        if (!s.isEmpty()) {
            g.dropStack(s);
        }
    }

    /** Найти в руках/рюкзаке (или в руке‑оружии) предмет; null — нет. */
    @Nullable
    ItemStack held(Predicate<ItemStack> p) {
        if (p.test(g.getMainHandStack())) {
            return g.getMainHandStack();
        }
        for (Inventory inv : holds()) {
            for (int i = 0; i < inv.size(); i++) {
                ItemStack s = inv.getStack(i);
                if (!s.isEmpty() && p.test(s)) {
                    return s;
                }
            }
        }
        return null;
    }

    /** Забрать из рук/рюкзака count предметов (возвращает, сколько забрал). */
    int consume(Predicate<ItemStack> p, int count) {
        int got = 0;
        for (Inventory inv : holds()) {
            for (int i = 0; i < inv.size() && got < count; i++) {
                ItemStack s = inv.getStack(i);
                if (!s.isEmpty() && p.test(s)) {
                    int n = Math.min(count - got, s.getCount());
                    s.decrement(n);
                    got += n;
                    inv.markDirty();
                }
            }
        }
        return got;
    }

    int count(Predicate<ItemStack> p) {
        int n = 0;
        for (Inventory inv : holds()) {
            for (int i = 0; i < inv.size(); i++) {
                ItemStack s = inv.getStack(i);
                if (!s.isEmpty() && p.test(s)) {
                    n += s.getCount();
                }
            }
        }
        return n;
    }

    /** Износ инструмента; сломался — пропадает. */
    void wear(ItemStack tool) {
        if (tool.isDamageable() && tool.damage(1, g.getRandom(), null)) {
            tool.decrement(1);
            g.playSound(SoundEvents.ENTITY_ITEM_BREAK, 0.8f, 1.0f);
        }
    }

    // =====================================================================
    // Склад
    // =====================================================================

    /** Сундуки и бочки в области работы (кеш на 5 с). */
    List<BlockPos> storage() {
        if (now() - storageAge < 100) {
            return storageCache;
        }
        storageAge = now();
        BlockPos c = center();
        List<BlockPos> out = new ArrayList<>();
        int r = radius;
        for (int cx = (c.getX() - r) >> 4; cx <= (c.getX() + r) >> 4; cx++) {
            for (int cz = (c.getZ() - r) >> 4; cz <= (c.getZ() + r) >> 4; cz++) {
                WorldChunk ch = world().getChunkManager().getWorldChunk(cx, cz);
                if (ch == null) {
                    continue;
                }
                for (BlockEntity be : ch.getBlockEntities().values()) {
                    BlockPos p = be.getPos();
                    if ((be instanceof ChestBlockEntity || be instanceof BarrelBlockEntity)
                            && horiz2(p, c) <= r * r && Math.abs(p.getY() - c.getY()) <= 12) {
                        out.add(p.toImmutable());
                    }
                }
            }
        }
        storageCache = out;
        return out;
    }

    @Nullable
    Inventory container(BlockPos p) {
        return world().getBlockEntity(p) instanceof Inventory inv ? inv : null;
    }

    /** Ближайший сундук, где есть предмет по условию. */
    @Nullable
    BlockPos storageWith(Predicate<ItemStack> p) {
        return nearest(storage(), pos -> {
            Inventory inv = container(pos);
            if (inv == null) {
                return false;
            }
            for (int i = 0; i < inv.size(); i++) {
                if (!inv.getStack(i).isEmpty() && p.test(inv.getStack(i))) {
                    return true;
                }
            }
            return false;
        });
    }

    /** Ближайший сундук, куда влезет этот предмет. */
    @Nullable
    BlockPos storageFor(ItemStack s) {
        return nearest(storage(), pos -> {
            Inventory inv = container(pos);
            if (inv == null) {
                return false;
            }
            for (int i = 0; i < inv.size(); i++) {
                ItemStack t = inv.getStack(i);
                if (t.isEmpty() || (ItemStack.canCombine(t, s) && t.getCount() < t.getMaxCount())) {
                    return true;
                }
            }
            return false;
        });
    }

    /** Взять из сундука до max предметов по условию (в руки/рюкзак). */
    int takeFrom(BlockPos pos, Predicate<ItemStack> p, int max) {
        Inventory inv = container(pos);
        if (inv == null) {
            return 0;
        }
        int got = 0;
        for (int i = 0; i < inv.size() && got < max; i++) {
            ItemStack s = inv.getStack(i);
            if (!s.isEmpty() && p.test(s)) {
                ItemStack part = s.split(Math.min(max - got, s.getCount()));
                got += part.getCount();
                store(part);
            }
        }
        inv.markDirty();
        return got;
    }

    static ItemStack insert(Inventory inv, ItemStack s) {
        s = s.copy();
        int cap = inv.getMaxCountPerStack();
        for (int i = 0; i < inv.size() && !s.isEmpty(); i++) {
            ItemStack t = inv.getStack(i);
            if (!t.isEmpty() && ItemStack.canCombine(t, s)) {
                int move = Math.min(s.getCount(), Math.min(t.getMaxCount(), cap) - t.getCount());
                if (move > 0) {
                    t.increment(move);
                    s.decrement(move);
                }
            }
        }
        for (int i = 0; i < inv.size() && !s.isEmpty(); i++) {
            if (inv.getStack(i).isEmpty() && inv.isValid(i, s)) {
                inv.setStack(i, s.split(Math.min(s.getCount(), Math.min(s.getMaxCount(), cap))));
            }
        }
        inv.markDirty();
        return s;
    }

    // =====================================================================
    // Поиск, резерв, движение
    // =====================================================================

    static double horiz2(BlockPos a, BlockPos b) {
        double dx = a.getX() - b.getX();
        double dz = a.getZ() - b.getZ();
        return dx * dx + dz * dz;
    }

    boolean inArea(BlockPos p) {
        return horiz2(p, center()) <= (double) radius * radius;
    }

    boolean blocked(BlockPos p) {
        Long until = blacklist.get(p);
        if (until != null && until > now()) {
            return true;
        }
        if (until != null) {
            blacklist.remove(p);
        }
        Map<BlockPos, long[]> res = RESERVED.get(world());
        if (res != null) {
            long[] r = res.get(p);
            if (r != null && r[0] != g.getId() && r[1] > now()) {
                return true;
            }
        }
        return false;
    }

    void reserve(BlockPos p) {
        RESERVED.computeIfAbsent(world(), k -> new HashMap<>()).put(p.toImmutable(), new long[]{g.getId(), now() + 100});
        if (RESERVED.get(world()).size() > 4096) {
            long t = now();
            RESERVED.get(world()).values().removeIf(r -> r[1] < t);
        }
    }

    void unreserve(BlockPos p) {
        Map<BlockPos, long[]> res = RESERVED.get(world());
        if (res != null) {
            long[] r = res.get(p);
            if (r != null && r[0] == g.getId()) {
                res.remove(p);
            }
        }
    }

    void skip(BlockPos p, int ticks) {
        blacklist.put(p.toImmutable(), now() + ticks);
        if (blacklist.size() > 512) {
            long t = now();
            blacklist.values().removeIf(v -> v < t);
        }
    }

    @Nullable
    BlockPos nearest(List<BlockPos> list, Predicate<BlockPos> ok) {
        BlockPos best = null;
        double bd = Double.MAX_VALUE;
        for (BlockPos p : list) {
            double d = g.getBlockPos().getSquaredDistance(p);
            if (d < bd && !blocked(p) && ok.test(p)) {
                bd = d;
                best = p;
            }
        }
        return best;
    }

    // =====================================================================
    // Дела
    // =====================================================================

    /** Дело: tick() — true, пока идёт. Общие части: подойти к цели, «застрял» — бросить и не брать 1 мин. */
    abstract class Job {
        private int repath;
        private int walking;
        protected int work;

        abstract boolean tick();

        void end() {
            g.getNavigation().stop();
        }

        /** Подойти на reach блоков (от глаз до центра блока). true — уже рядом. */
        boolean approach(BlockPos target, double reach) {
            Vec3d c = Vec3d.ofCenter(target);
            if (g.getEyePos().squaredDistanceTo(c) <= reach * reach
                    || g.getPos().squaredDistanceTo(c) <= reach * reach) {
                g.getNavigation().stop();
                g.getLookControl().lookAt(c.x, c.y, c.z);
                walking = 0;
                return true;
            }
            walking++;
            if (--repath <= 0 || g.getNavigation().isIdle()) {
                repath = 20;
                g.getNavigation().startMovingTo(target.getX() + 0.5, target.getY(), target.getZ() + 0.5, SPEED);
            }
            return false;
        }

        boolean stuck() {
            return walking > STUCK_TICKS;
        }

        /** Взмах рукой и звук раз в period тиков. */
        void swing(int period, @Nullable net.minecraft.sound.SoundEvent sound, BlockPos at) {
            if (work % period == 0) {
                g.swingHand(Hand.MAIN_HAND);
                if (sound != null) {
                    world().playSound(null, at, sound, SoundCategory.BLOCKS, 0.6f, 0.9f + g.getRandom().nextFloat() * 0.2f);
                }
            }
        }
    }

    /** Подойти к сундуку и взять (подзадача). */
    final class Fetch extends Job {
        private final BlockPos chest;
        private final Predicate<ItemStack> what;
        private final int max;
        boolean done;

        Fetch(BlockPos chest, Predicate<ItemStack> what, int max) {
            this.chest = chest;
            this.what = what;
            this.max = max;
        }

        @Override
        boolean tick() {
            if (done) {
                return false;
            }
            if (!approach(chest, 3.0)) {
                if (stuck()) {
                    skip(chest, 1200);
                    return false;
                }
                return true;
            }
            if (++work >= STORE_TICKS) {
                takeFrom(chest, what, max);
                world().playSound(null, chest, SoundEvents.BLOCK_BARREL_OPEN, SoundCategory.BLOCKS, 0.4f, 1.1f);
                done = true;
                return false;
            }
            return true;
        }
    }

    // ---------- Отнести на склад ----------

    static final class Unload {
        static Job find(SettlerWork w) {
            ItemStack first = null;
            for (Inventory inv : w.holds()) {
                for (int i = 0; i < inv.size() && first == null; i++) {
                    ItemStack s = inv.getStack(i);
                    if (!s.isEmpty() && !isTool(s)) {
                        first = s;
                    }
                }
            }
            if (first == null) {
                return null;
            }
            BlockPos chest = w.storageFor(first);
            if (chest == null) {
                w.status = w.storage().isEmpty() ? "no_storage" : "storage_full";
                return null;
            }
            return w.new Job() {
                @Override
                boolean tick() {
                    if (!approach(chest, 3.0)) {
                        if (stuck()) {
                            w.skip(chest, 1200);
                            return false;
                        }
                        return true;
                    }
                    if (++work < STORE_TICKS) {
                        return true;
                    }
                    Inventory target = w.container(chest);
                    if (target != null) {
                        for (Inventory inv : w.holds()) {
                            for (int i = 0; i < inv.size(); i++) {
                                ItemStack s = inv.getStack(i);
                                if (!s.isEmpty() && !isTool(s)) {
                                    inv.setStack(i, insert(target, s));
                                }
                            }
                            inv.markDirty();
                        }
                        w.world().playSound(null, chest, SoundEvents.BLOCK_BARREL_CLOSE, SoundCategory.BLOCKS, 0.4f, 1.0f);
                    }
                    return false;
                }
            };
        }
    }

    // ---------- Еда для себя ----------

    private boolean wantsFood() {
        if (g.getHunger() > 14) {
            return false;
        }
        for (int i = GuardEntity.FOOD_START; i < GuardEntity.FOOD_START + GuardEntity.FOOD_SLOTS; i++) {
            if (!g.getBag().getStack(i).isEmpty()) {
                return false;
            }
        }
        return true;
    }

    static boolean goodFood(ItemStack s) {
        return s.isFood() && s.getItem().getFoodComponent() != null
                && s.getItem().getFoodComponent().getStatusEffects().isEmpty() && !s.isOf(net.minecraft.item.Items.ROTTEN_FLESH);
    }

    static final class FetchFood {
        static Job find(SettlerWork w) {
            BlockPos chest = w.storageWith(SettlerWork::goodFood);
            if (chest == null) {
                return null;
            }
            return w.new Job() {
                @Override
                boolean tick() {
                    if (!approach(chest, 3.0)) {
                        if (stuck()) {
                            w.skip(chest, 1200);
                            return false;
                        }
                        return true;
                    }
                    if (++work < STORE_TICKS) {
                        return true;
                    }
                    Inventory inv = w.container(chest);
                    if (inv != null) {
                        int want = FOOD_WANT;
                        for (int i = 0; i < inv.size() && want > 0; i++) {
                            ItemStack s = inv.getStack(i);
                            if (!s.isEmpty() && goodFood(s)) {
                                ItemStack part = s.split(Math.min(want, s.getCount()));
                                want -= part.getCount();
                                ItemStack left = insert(foodSlots(w.g), part);
                                if (!left.isEmpty()) {
                                    w.store(left);
                                }
                            }
                        }
                        inv.markDirty();
                    }
                    return false;
                }
            };
        }

        /** Слоты еды сумки как отдельный «инвентарь» для insert(). */
        private static Inventory foodSlots(GuardEntity g) {
            SimpleInventory bag = g.getBag();
            return new SimpleInventory(GuardEntity.FOOD_SLOTS) {
                @Override
                public ItemStack getStack(int slot) {
                    return bag.getStack(GuardEntity.FOOD_START + slot);
                }

                @Override
                public void setStack(int slot, ItemStack stack) {
                    bag.setStack(GuardEntity.FOOD_START + slot, stack);
                }

                @Override
                public void markDirty() {
                    bag.markDirty();
                }
            };
        }
    }

    // ---------- Переноска ----------

    static final class Haul {
        static Job find(SettlerWork w) {
            if (w.storage().isEmpty()) {
                w.problem[WorkType.HAULING.ordinal()] = "no_storage";
                return null;
            }
            if (!w.hasFreeSpace()) {
                return null;
            }
            BlockPos c = w.center();
            Box area = new Box(c).expand(w.radius, 10, w.radius);
            List<ItemEntity> items = w.world().getEntitiesByClass(ItemEntity.class, area, e -> e.isAlive()
                    && e.age > 60 && !e.cannotPickup() && !e.isInLava() && w.inArea(e.getBlockPos())
                    && !w.blocked(e.getBlockPos()));
            ItemEntity target = items.stream().min(Comparator.comparingDouble(w.g::squaredDistanceTo)).orElse(null);
            if (target == null) {
                return null;
            }
            BlockPos at = target.getBlockPos().toImmutable();
            w.reserve(at);
            return w.new Job() {
                @Override
                boolean tick() {
                    if (!target.isAlive()) {
                        return false;
                    }
                    w.reserve(at);
                    if (!approach(target.getBlockPos(), 1.6)) {
                        if (stuck()) {
                            w.skip(at, 1200);
                            return false;
                        }
                        return true;
                    }
                    ItemStack s = target.getStack().copy();
                    int before = s.getCount();
                    for (Inventory inv : w.holds()) {
                        s = insert(inv, s);
                    }
                    int taken = before - s.getCount();
                    if (taken > 0) {
                        w.g.sendPickup(target, taken);
                    }
                    if (s.isEmpty()) {
                        target.discard();
                    } else {
                        target.setStack(s);
                    }
                    return false;
                }

                @Override
                void end() {
                    super.end();
                    w.unreserve(at);
                }
            };
        }
    }

    // ---------- Земледелие ----------

    static boolean isSeed(ItemStack s) {
        return s.getItem() instanceof BlockItem bi && bi.getBlock() instanceof CropBlock;
    }

    /** Пашня в области (кеш на 20 с): Y дома ±4. */
    private List<BlockPos> farmland() {
        if (now() - farmAge < 400) {
            return farmCache;
        }
        farmAge = now();
        BlockPos c = center();
        List<BlockPos> out = new ArrayList<>();
        BlockPos.Mutable m = new BlockPos.Mutable();
        for (int dx = -radius; dx <= radius; dx++) {
            for (int dz = -radius; dz <= radius; dz++) {
                if (dx * dx + dz * dz > radius * radius) {
                    continue;
                }
                for (int dy = -FARM_DY; dy <= FARM_DY; dy++) {
                    m.set(c.getX() + dx, c.getY() + dy, c.getZ() + dz);
                    if (world().getBlockState(m).isOf(Blocks.FARMLAND)) {
                        out.add(m.up().toImmutable());   // место для культуры
                    }
                }
            }
        }
        farmCache = out;
        return out;
    }

    static final class Farm {
        static Job find(SettlerWork w) {
            ServerWorld world = w.world();
            List<BlockPos> plots = w.farmland();
            if (plots.isEmpty()) {
                w.problem[WorkType.FARMING.ordinal()] = "no_farmland";
                return null;
            }
            if (!w.hasFreeSpace()) {
                w.problem[WorkType.FARMING.ordinal()] = "full";
                return null;
            }
            // 1) Спелое — собрать
            BlockPos ripe = w.nearest(plots, p -> world.getBlockState(p).getBlock() instanceof CropBlock crop
                    && crop.isMature(world.getBlockState(p)));
            if (ripe != null) {
                return w.new Harvest(ripe);
            }
            // 2) Пустая пашня — засеять (если не слишком холодно)
            BlockPos empty = w.nearest(plots, p -> world.getBlockState(p).isAir()
                    && world.getBlockState(p.down()).isOf(Blocks.FARMLAND));
            if (empty == null) {
                return null;
            }
            if (Temperature.ambient(world, empty) < Crops.SLOW_MIN) {
                w.problem[WorkType.FARMING.ordinal()] = "too_cold";
                return null;
            }
            if (w.count(SettlerWork::isSeed) == 0) {
                BlockPos chest = w.storageWith(SettlerWork::isSeed);
                if (chest == null) {
                    w.problem[WorkType.FARMING.ordinal()] = "no_seeds";
                    return null;
                }
                return w.new Fetch(chest, SettlerWork::isSeed, 32);
            }
            return w.new Plant(empty);
        }
    }

    final class Harvest extends Job {
        private final BlockPos pos;

        Harvest(BlockPos pos) {
            this.pos = pos;
            reserve(pos);
        }

        @Override
        boolean tick() {
            BlockState st = world().getBlockState(pos);
            if (!(st.getBlock() instanceof CropBlock crop) || !crop.isMature(st)) {
                return false;
            }
            reserve(pos);
            if (!approach(pos, 2.5)) {
                if (stuck()) {
                    skip(pos, 1200);
                    return false;
                }
                return true;
            }
            work++;
            swing(10, SoundEvents.BLOCK_CROP_BREAK, pos);
            if (work < HARVEST_TICKS / skills.workSpeed(g, Skill.FARMING)) {
                return true;
            }
            ServerWorld w = world();
            List<ItemStack> drops = Block.getDroppedStacks(st, w, pos, null, g, ItemStack.EMPTY);
            ItemStack seed = crop.getPickStack(w, pos, st);
            w.breakBlock(pos, false, g);
            int lvl = skills.level(Skill.FARMING);
            float fail = Math.max(0f, 0.15f - 0.04f * lvl);
            if (g.getRandom().nextFloat() < fail) {
                drops = List.of();   // неумелый — испортил урожай
                w.spawnParticles(ParticleTypes.SMOKE, pos.getX() + 0.5, pos.getY() + 0.4, pos.getZ() + 0.5, 5, 0.2, 0.2, 0.2, 0.01);
            } else if (!drops.isEmpty() && g.getRandom().nextFloat() < skills.bonusChance(Skill.FARMING)) {
                drops = new ArrayList<>(drops);
                drops.add(drops.get(0).copyWithCount(1));
            }
            boolean replanted = false;
            for (ItemStack d : drops) {
                ItemStack s = d.copy();
                if (!replanted && !seed.isEmpty() && s.isOf(seed.getItem())) {
                    s.decrement(1);
                    replanted = true;
                }
                if (!s.isEmpty()) {
                    store(s);
                }
            }
            if (!replanted && consume(x -> x.isOf(seed.getItem()), 1) > 0) {
                replanted = true;
            }
            if (replanted) {
                w.setBlockState(pos, crop.getDefaultState(), Block.NOTIFY_ALL);
            }
            skills.addXp(g, Skill.FARMING, XP_HARVEST);
            return false;
        }

        @Override
        void end() {
            super.end();
            unreserve(pos);
        }
    }

    final class Plant extends Job {
        private final BlockPos pos;

        Plant(BlockPos pos) {
            this.pos = pos;
            reserve(pos);
        }

        @Override
        boolean tick() {
            ServerWorld w = world();
            if (!w.getBlockState(pos).isAir() || !w.getBlockState(pos.down()).isOf(Blocks.FARMLAND)) {
                return false;
            }
            reserve(pos);
            if (!approach(pos, 2.5)) {
                if (stuck()) {
                    skip(pos, 1200);
                    return false;
                }
                return true;
            }
            work++;
            swing(8, null, pos);
            if (work < PLANT_TICKS / skills.workSpeed(g, Skill.FARMING)) {
                return true;
            }
            // Сажаем то же, что растёт рядом, иначе любые семена
            Item want = null;
            for (Direction d : Direction.Type.HORIZONTAL) {
                BlockState n = w.getBlockState(pos.offset(d));
                if (n.getBlock() instanceof CropBlock c) {
                    want = c.getPickStack(w, pos.offset(d), n).getItem();
                    break;
                }
            }
            ItemStack seed = null;
            if (want != null) {
                Item fw = want;
                seed = held(s -> s.isOf(fw));
            }
            if (seed == null) {
                seed = held(SettlerWork::isSeed);
            }
            if (seed == null || !(seed.getItem() instanceof BlockItem bi)) {
                return false;
            }
            BlockState crop = bi.getBlock().getDefaultState();
            if (crop.canPlaceAt(w, pos)) {
                w.setBlockState(pos, crop, Block.NOTIFY_ALL);
                w.playSound(null, pos, SoundEvents.ITEM_CROP_PLANT, SoundCategory.BLOCKS, 0.8f, 1.0f);
                seed.decrement(1);
                for (Inventory inv : holds()) {
                    inv.markDirty();
                }
                skills.addXp(g, Skill.FARMING, XP_PLANT);
            }
            return false;
        }

        @Override
        void end() {
            super.end();
            unreserve(pos);
        }
    }

    // ---------- Рубка ----------

    /** Скорость инструмента по блоку (1 — не тот инструмент). */
    static float toolSpeed(ItemStack tool, BlockState st) {
        return tool == null ? 1f : Math.max(1f, tool.getMiningSpeedMultiplier(st));
    }

    /** Природное дерево: столб брёвен на земле, сверху живая (не поставленная) листва. Возвращает низ ствола. */
    @Nullable
    private BlockPos treeBase(BlockPos topLog) {
        ServerWorld w = world();
        BlockPos.Mutable m = topLog.mutableCopy();
        int n = 0;
        while (w.getBlockState(m.down()).isIn(BlockTags.LOGS) && n++ < 32) {
            m.move(Direction.DOWN);
        }
        if (!w.getBlockState(m.down()).isIn(BlockTags.DIRT)) {
            return null;
        }
        BlockPos base = m.toImmutable();
        // живая листва у верхушки
        for (BlockPos p : BlockPos.iterate(topLog.add(-2, -1, -2), topLog.add(2, 2, 2))) {
            BlockState st = w.getBlockState(p);
            if (st.getBlock() instanceof LeavesBlock && !st.get(LeavesBlock.PERSISTENT)) {
                return base;
            }
        }
        return null;
    }

    static final class Chop {
        static Job find(SettlerWork w) {
            ServerWorld world = w.world();
            if (!w.hasFreeSpace()) {
                w.problem[WorkType.WOODCUTTING.ordinal()] = "full";
                return null;
            }
            BlockPos c = w.center();
            BlockPos best = null;
            double bd = Double.MAX_VALUE;
            for (int dx = -w.radius; dx <= w.radius; dx++) {
                for (int dz = -w.radius; dz <= w.radius; dz++) {
                    if (dx * dx + dz * dz > w.radius * w.radius) {
                        continue;
                    }
                    int x = c.getX() + dx;
                    int z = c.getZ() + dz;
                    int top = world.getTopY(Heightmap.Type.MOTION_BLOCKING_NO_LEAVES, x, z) - 1;
                    BlockPos p = new BlockPos(x, top, z);
                    if (!world.getBlockState(p).isIn(BlockTags.LOGS)) {
                        continue;
                    }
                    double d = w.g.getBlockPos().getSquaredDistance(p);
                    if (d < bd) {
                        BlockPos base = w.treeBase(p);
                        if (base != null && !w.blocked(base)) {
                            bd = d;
                            best = base;
                        }
                    }
                }
            }
            if (best == null) {
                w.problem[WorkType.WOODCUTTING.ordinal()] = "no_trees";
                return null;
            }
            if (w.held(s -> s.getItem() instanceof AxeItem) == null) {
                BlockPos chest = w.storageWith(s -> s.getItem() instanceof AxeItem);
                if (chest == null) {
                    w.problem[WorkType.WOODCUTTING.ordinal()] = "no_axe";
                    return null;
                }
                return w.new Fetch(chest, s -> s.getItem() instanceof AxeItem, 1);
            }
            return w.new Fell(best);
        }
    }

    final class Fell extends Job {
        private final BlockPos base;
        private List<BlockPos> logs;
        private int need;

        Fell(BlockPos base) {
            this.base = base;
            reserve(base);
        }

        @Override
        boolean tick() {
            ServerWorld w = world();
            if (!w.getBlockState(base).isIn(BlockTags.LOGS)) {
                return false;
            }
            reserve(base);
            ItemStack axe = held(s -> s.getItem() instanceof AxeItem);
            if (axe == null) {
                return false;
            }
            if (!approach(base, 3.0)) {
                if (stuck()) {
                    skip(base, 2400);
                    return false;
                }
                return true;
            }
            if (logs == null) {
                logs = collectTree(base);
                float per = CHOP_TICKS * 4f / toolSpeed(axe, w.getBlockState(base)) / skills.workSpeed(g, Skill.WOODCUTTING);
                need = Math.max(10, Math.round(per * logs.size()));
            }
            work++;
            swing(8, SoundEvents.BLOCK_WOOD_HIT, base);
            if (work % 8 == 0) {
                w.spawnParticles(new net.minecraft.particle.BlockStateParticleEffect(ParticleTypes.BLOCK, w.getBlockState(base)),
                        base.getX() + 0.5, base.getY() + 0.6, base.getZ() + 0.5, 4, 0.3, 0.3, 0.3, 0.05);
            }
            if (work < need) {
                return true;
            }
            // Дерево падает: все брёвна — в руки/рюкзак (лишнее на землю), листва осыпется сама
            float bonus = skills.bonusChance(Skill.WOODCUTTING);
            for (BlockPos p : logs) {
                BlockState st = w.getBlockState(p);
                if (!st.isIn(BlockTags.LOGS)) {
                    continue;
                }
                for (ItemStack d : Block.getDroppedStacks(st, w, p, null, g, axe)) {
                    store(d.copy());
                }
                if (g.getRandom().nextFloat() < bonus) {
                    store(new ItemStack(st.getBlock().asItem()));
                }
                w.breakBlock(p, false, g);
                wear(axe);
                skills.addXp(g, Skill.WOODCUTTING, XP_LOG);
                if (axe.isEmpty()) {
                    break;
                }
            }
            w.playSound(null, base, SoundEvents.BLOCK_WOOD_BREAK, SoundCategory.BLOCKS, 1.0f, 0.6f);
            // Посадить саженец на пень
            ItemStack sapling = held(s -> s.isIn(ItemTags.SAPLINGS));
            if (sapling != null && sapling.getItem() instanceof BlockItem bi) {
                BlockState sp = bi.getBlock().getDefaultState();
                if (w.getBlockState(base).isAir() && sp.canPlaceAt(w, base)) {
                    w.setBlockState(base, sp, Block.NOTIFY_ALL);
                    sapling.decrement(1);
                    for (Inventory inv : holds()) {
                        inv.markDirty();
                    }
                }
            }
            return false;
        }

        @Override
        void end() {
            super.end();
            unreserve(base);
        }
    }

    /** Все брёвна дерева (связанные, вверх от низа), не больше MAX_TREE_LOGS. */
    private List<BlockPos> collectTree(BlockPos base) {
        ServerWorld w = world();
        List<BlockPos> out = new ArrayList<>();
        Set<BlockPos> seen = new HashSet<>();
        ArrayDeque<BlockPos> q = new ArrayDeque<>();
        q.add(base);
        seen.add(base);
        while (!q.isEmpty() && out.size() < MAX_TREE_LOGS) {
            BlockPos p = q.poll();
            out.add(p);
            for (int dx = -1; dx <= 1; dx++) {
                for (int dy = 0; dy <= 1; dy++) {
                    for (int dz = -1; dz <= 1; dz++) {
                        BlockPos n = p.add(dx, dy, dz);
                        if (n.getY() >= base.getY() && seen.add(n) && w.getBlockState(n).isIn(BlockTags.LOGS)) {
                            q.add(n);
                        }
                    }
                }
            }
        }
        return out;
    }

    // ---------- Добыча ----------

    static boolean isMiningTool(ItemStack s) {
        return s.getItem() instanceof PickaxeItem;
    }

    /** Лучший инструмент для блока из того, что при себе (кирка, лопата, топор). */
    @Nullable
    ItemStack bestTool(BlockState st) {
        ItemStack best = null;
        float bs = 0;
        for (Predicate<ItemStack> p : List.<Predicate<ItemStack>>of(s -> s.getItem() instanceof PickaxeItem,
                s -> s.getItem() instanceof ShovelItem, s -> s.getItem() instanceof AxeItem)) {
            ItemStack t = held(p);
            if (t != null && (!st.isToolRequired() || t.isSuitableFor(st))) {
                float sp = toolSpeed(t, st);
                if (sp > bs) {
                    bs = sp;
                    best = t;
                }
            }
        }
        return best;
    }

    private boolean minable(BlockPos p, BlockState st, ItemStack pick) {
        ServerWorld w = world();
        if (st.isAir() || !st.getFluidState().isEmpty() || st.getHardness(w, p) < 0 || st.hasBlockEntity()
                || st.isOf(Blocks.BEDROCK)) {
            return false;
        }
        if (st.isToolRequired() && !pick.isSuitableFor(st) && bestTool(st) == null) {
            return false;
        }
        // Рядом вода или лава — не трогаем (затопит шахту)
        for (Direction d : Direction.values()) {
            if (d != Direction.DOWN && !w.getFluidState(p.offset(d)).isEmpty()) {
                return false;
            }
        }
        // Открыт хотя бы с одной стороны — до него можно дотянуться
        for (Direction d : Direction.values()) {
            if (!w.getBlockState(p.offset(d)).isOpaqueFullCube(w, p.offset(d))) {
                return true;
            }
        }
        return false;
    }

    static final class Mine {
        static Job find(SettlerWork w) {
            ServerWorld world = w.world();
            List<MiningZones.Zone> zones = new ArrayList<>();
            for (MiningZones.Zone z : MiningZones.get(world).zones()) {
                if (horiz2(z.center(), w.center()) <= (double) (w.radius + 32) * (w.radius + 32)) {
                    zones.add(z);
                }
            }
            if (zones.isEmpty()) {
                w.problem[WorkType.MINING.ordinal()] = "no_mines";
                return null;
            }
            if (!w.hasFreeSpace()) {
                w.problem[WorkType.MINING.ordinal()] = "full";
                return null;
            }
            ItemStack pick = w.held(SettlerWork::isMiningTool);
            if (pick == null) {
                BlockPos chest = w.storageWith(SettlerWork::isMiningTool);
                if (chest == null) {
                    w.problem[WorkType.MINING.ordinal()] = "no_pickaxe";
                    return null;
                }
                return w.new Fetch(chest, SettlerWork::isMiningTool, 1);
            }
            // Сверху вниз: ближайший доступный блок в самом верхнем непустом слое шахты
            zones.sort(Comparator.comparingDouble(z -> w.g.getBlockPos().getSquaredDistance(z.center())));
            for (MiningZones.Zone z : zones) {
                for (int y = z.max().getY(); y >= z.min().getY(); y--) {
                    BlockPos best = null;
                    double bd = Double.MAX_VALUE;
                    boolean layerHasBlocks = false;
                    for (int x = z.min().getX(); x <= z.max().getX(); x++) {
                        for (int zz = z.min().getZ(); zz <= z.max().getZ(); zz++) {
                            BlockPos p = new BlockPos(x, y, zz);
                            BlockState st = world.getBlockState(p);
                            if (st.isAir() || !st.getFluidState().isEmpty()) {
                                continue;
                            }
                            if (!w.minable(p, st, pick) || w.blocked(p)) {
                                continue;
                            }
                            layerHasBlocks = true;
                            double d = w.g.getBlockPos().getSquaredDistance(p);
                            if (d < bd) {
                                bd = d;
                                best = p;
                            }
                        }
                    }
                    if (best != null) {
                        return w.new Dig(best);
                    }
                    if (layerHasBlocks) {
                        break;
                    }
                }
            }
            w.problem[WorkType.MINING.ordinal()] = "mines_done";
            return null;
        }
    }

    final class Dig extends Job {
        private final BlockPos pos;
        private int need = -1;

        Dig(BlockPos pos) {
            this.pos = pos;
            reserve(pos);
        }

        @Override
        boolean tick() {
            ServerWorld w = world();
            BlockState st = w.getBlockState(pos);
            if (st.isAir()) {
                return false;
            }
            reserve(pos);
            ItemStack tool = bestTool(st);
            if (tool == null) {
                return false;
            }
            if (!approach(pos, 4.0)) {
                if (stuck()) {
                    skip(pos, 1200);
                    return false;
                }
                return true;
            }
            if (need < 0) {
                float t = Math.max(6f, st.getHardness(w, pos) * MINE_K / toolSpeed(tool, st));
                need = Math.max(4, Math.round(t / skills.workSpeed(g, Skill.MINING)));
            }
            work++;
            swing(6, st.getSoundGroup().getHitSound(), pos);
            w.setBlockBreakingInfo(g.getId(), pos, Math.min(9, work * 10 / need));
            if (work < need) {
                return true;
            }
            w.setBlockBreakingInfo(g.getId(), pos, -1);
            List<ItemStack> drops = Block.getDroppedStacks(st, w, pos, null, g, tool);
            boolean ore = st.isIn(ConventionalBlockTags.ORES);
            if (ore && !drops.isEmpty() && g.getRandom().nextFloat() < skills.bonusChance(Skill.MINING)) {
                drops = new ArrayList<>(drops);
                drops.add(drops.get(0).copy());
            }
            w.breakBlock(pos, false, g);
            for (ItemStack d : drops) {
                store(d.copy());
            }
            wear(tool);
            skills.addXp(g, Skill.MINING, ore ? XP_ORE : XP_BLOCK);
            return false;
        }

        @Override
        void end() {
            super.end();
            world().setBlockBreakingInfo(g.getId(), pos, -1);
            unreserve(pos);
        }
    }

    // ---------- Готовка ----------

    /** Печь, коптильня или горящий костёр в области (ближайшие). */
    private List<BlockPos> stations() {
        List<BlockPos> out = new ArrayList<>();
        BlockPos c = center();
        int r = radius;
        for (int cx = (c.getX() - r) >> 4; cx <= (c.getX() + r) >> 4; cx++) {
            for (int cz = (c.getZ() - r) >> 4; cz <= (c.getZ() + r) >> 4; cz++) {
                WorldChunk ch = world().getChunkManager().getWorldChunk(cx, cz);
                if (ch == null) {
                    continue;
                }
                for (BlockEntity be : ch.getBlockEntities().values()) {
                    BlockPos p = be.getPos();
                    BlockState st = be.getCachedState();
                    boolean ok = st.getBlock() instanceof AbstractFurnaceBlock
                            || (st.getBlock() instanceof CampfireBlock && st.get(CampfireBlock.LIT));
                    if (ok && horiz2(p, c) <= r * r && Math.abs(p.getY() - c.getY()) <= 12) {
                        out.add(p.toImmutable());
                    }
                }
            }
        }
        return out;
    }

    private RecipeType<? extends AbstractCookingRecipe> recipeType(BlockState station) {
        if (station.getBlock() instanceof CampfireBlock) {
            return RecipeType.CAMPFIRE_COOKING;
        }
        return station.getBlock() instanceof SmokerBlock ? RecipeType.SMOKING : RecipeType.SMELTING;
    }

    /** Что получится из сырой еды на этой станции (пусто — не готовится / не еда). */
    ItemStack cooked(ItemStack raw, RecipeType<? extends AbstractCookingRecipe> type) {
        ServerWorld w = world();
        Optional<? extends AbstractCookingRecipe> r = w.getRecipeManager().getFirstMatch(type,
                (Inventory) new SimpleInventory(raw.copyWithCount(1)), w);
        if (r.isEmpty()) {
            return ItemStack.EMPTY;
        }
        ItemStack out = r.get().getOutput(w.getRegistryManager());
        return out.isFood() ? out.copy() : ItemStack.EMPTY;
    }

    private static Map<Item, Integer> fuelMap;

    static int fuelTime(ItemStack s) {
        if (fuelMap == null) {
            fuelMap = AbstractFurnaceBlockEntity.createFuelTimeMap();
        }
        return fuelMap.getOrDefault(s.getItem(), 0);
    }

    static final class Cook {
        static Job find(SettlerWork w) {
            List<BlockPos> st = w.stations();
            if (st.isEmpty()) {
                w.problem[WorkType.COOKING.ordinal()] = "no_station";
                return null;
            }
            if (!w.hasFreeSpace()) {
                w.problem[WorkType.COOKING.ordinal()] = "full";
                return null;
            }
            ServerWorld world = w.world();
            BlockPos station = w.nearest(st, p -> true);
            if (station == null) {
                return null;
            }
            BlockState sst = world.getBlockState(station);
            RecipeType<? extends AbstractCookingRecipe> type = w.recipeType(sst);
            Predicate<ItemStack> raw = s -> !w.cooked(s, type).isEmpty();
            boolean needsFuel = !(sst.getBlock() instanceof CampfireBlock);
            if (w.count(raw) == 0) {
                BlockPos chest = w.storageWith(raw);
                if (chest == null) {
                    w.problem[WorkType.COOKING.ordinal()] = "no_raw";
                    return null;
                }
                return w.new Fetch(chest, raw, 8);
            }
            if (needsFuel && w.count(s -> fuelTime(s) > 0) == 0) {
                BlockPos chest = w.storageWith(s -> fuelTime(s) > 0 && !(s.getItem() instanceof ToolItem));
                if (chest == null) {
                    w.problem[WorkType.COOKING.ordinal()] = "no_fuel";
                    return null;
                }
                return w.new Fetch(chest, s -> fuelTime(s) > 0 && !(s.getItem() instanceof ToolItem), 8);
            }
            return w.new CookAt(station, type, raw, needsFuel, sst.getBlock() instanceof SmokerBlock);
        }
    }

    final class CookAt extends Job {
        private final BlockPos station;
        private final RecipeType<? extends AbstractCookingRecipe> type;
        private final Predicate<ItemStack> raw;
        private final boolean needsFuel;
        private final boolean fast;
        /** Остаток топлива (в тиках печи); на одно блюдо — 200 (в коптильне 100). */
        private int fuel;

        CookAt(BlockPos station, RecipeType<? extends AbstractCookingRecipe> type, Predicate<ItemStack> raw,
               boolean needsFuel, boolean fast) {
            this.station = station;
            this.type = type;
            this.raw = raw;
            this.needsFuel = needsFuel;
            this.fast = fast;
            reserve(station);
        }

        @Override
        boolean tick() {
            ServerWorld w = world();
            reserve(station);
            if (!approach(station, 2.8)) {
                if (stuck()) {
                    skip(station, 1200);
                    return false;
                }
                return true;
            }
            ItemStack in = held(raw);
            if (in == null) {
                return false;
            }
            int cost = fast ? 100 : 200;
            if (needsFuel && fuel < cost) {
                ItemStack f = held(s -> fuelTime(s) > 0 && !(s.getItem() instanceof ToolItem) && !raw.test(s));
                if (f == null) {
                    return false;
                }
                fuel += fuelTime(f);
                f.decrement(1);
                for (Inventory inv : holds()) {
                    inv.markDirty();
                }
            }
            work++;
            if (work % 10 == 0) {
                w.spawnParticles(ParticleTypes.SMOKE, station.getX() + 0.5, station.getY() + 1.0, station.getZ() + 0.5,
                        2, 0.2, 0.05, 0.2, 0.01);
                w.playSound(null, station, SoundEvents.BLOCK_FURNACE_FIRE_CRACKLE, SoundCategory.BLOCKS, 0.5f, 1.0f);
                g.swingHand(Hand.MAIN_HAND);
            }
            float t = (fast || !needsFuel ? COOK_TICKS * 0.67f : COOK_TICKS) / skills.workSpeed(g, Skill.COOKING);
            if (work < t) {
                return true;
            }
            work = 0;
            ItemStack out = cooked(in, type);
            in.decrement(1);
            if (needsFuel) {
                fuel -= cost;
            }
            int lvl = skills.level(Skill.COOKING);
            float burn = Math.max(0f, 0.1f - 0.03f * lvl);
            if (g.getRandom().nextFloat() < burn) {
                w.spawnParticles(ParticleTypes.LARGE_SMOKE, station.getX() + 0.5, station.getY() + 1.0,
                        station.getZ() + 0.5, 4, 0.2, 0.1, 0.2, 0.01);   // подгорело
            } else {
                if (g.getRandom().nextFloat() < skills.bonusChance(Skill.COOKING)) {
                    out.increment(1);
                }
                store(out);
            }
            skills.addXp(g, Skill.COOKING, XP_COOK);
            for (Inventory inv : holds()) {
                inv.markDirty();
            }
            return hasFreeSpace();
        }

        @Override
        void end() {
            super.end();
            unreserve(station);
        }
    }

    // =====================================================================
    // Сохранение
    // =====================================================================

    public void dropAll() {
        for (int i = 0; i < carry.size(); i++) {
            if (!carry.getStack(i).isEmpty()) {
                g.dropStack(carry.getStack(i).copy());
            }
        }
        carry.clear();
    }

    public NbtCompound toNbt() {
        NbtCompound n = new NbtCompound();
        n.put("Skills", skills.toNbt());
        for (WorkType t : WorkType.values()) {
            n.putInt("P_" + t.id, priority[t.ordinal()]);
        }
        n.putInt("Radius", radius);
        n.put("Carry", Clothing.toNbt(carry));
        return n;
    }

    public void fromNbt(NbtCompound n) {
        skills.fromNbt(n.getCompound("Skills"), g);
        for (WorkType t : WorkType.values()) {
            if (n.contains("P_" + t.id)) {
                priority[t.ordinal()] = Math.max(0, Math.min(4, n.getInt("P_" + t.id)));
            }
        }
        if (n.contains("Radius")) {
            setRadius(n.getInt("Radius"));
        }
        if (n.contains("Carry", NbtElement.LIST_TYPE)) {
            Clothing.fromNbt(carry, n.getList("Carry", NbtElement.COMPOUND_TYPE));
        }
    }
}

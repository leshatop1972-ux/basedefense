package com.lesha_top1972.basedefense.entity;

import com.lesha_top1972.basedefense.item.ModItems;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.block.LeavesBlock;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.ai.goal.Goal;
import net.minecraft.entity.mob.PathAwareEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.registry.Registries;
import net.minecraft.registry.tag.BlockTags;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.state.property.Properties;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.Hand;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.Heightmap;
import net.minecraft.world.World;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Житель-лесоруб. Появляется в маленьком домике с посадкой деревьев на участке.
 * Обычный режим: каждый день приносит брёвна одной случайной породы: 16 / 25 / 35 по уровням,
 * а на 3 уровне ещё и 6 монет.
 * Режим «Рубка деревьев» (переключатель в меню): днём уходит из дома к ближайшему дикому дереву
 * (не дальше 100 блоков от дома), рубит его целиком, сажает саженец и идёт к следующему.
 * Брёвна попадают в сундук дома. В этом режиме ежедневных брёвен нет (монеты на 3 уровне остаются),
 * а рубит он не больше 32 / 64 / 128 брёвен в день. Деревья с «вечной» листвой (посаженные во дворах)
 * не трогает. Ночью спит.
 */
public class LumberjackEntity extends SettlerEntity {

    /** Дальше этого от дома деревья не рубит. */
    public static final int MAX_TREE_DISTANCE = 100;
    private static final int[] DAILY_CAP = {0, 32, 64, 128};
    private static final int[] CHOP_INTERVAL = {0, 20, 14, 9};

    private boolean chopping;
    @Nullable
    private BlockPos treeBase;
    private final ArrayDeque<BlockPos> logs = new ArrayDeque<>();
    private int searchCooldown;
    private int searchRadius = 24;
    private int chopTimer;
    private int walkTicks;
    private int choppedToday;
    private long chopDay = -1;

    public LumberjackEntity(EntityType<? extends PathAwareEntity> type, World world) {
        super(type, world);
    }

    @Override
    protected void initGoals() {
        super.initGoals();
        this.goalSelector.add(2, new GoToTreeGoal(this));
    }

    // ---------- Переключатель «Рубка деревьев» ----------

    @Override
    public boolean hasWorkToggle() {
        return true;
    }

    @Override
    public boolean isWorkToggleOn() {
        return chopping;
    }

    @Override
    public void flipWorkToggle(PlayerEntity player) {
        chopping = !chopping;
        treeBase = null;
        logs.clear();
        player.sendMessage(Text.translatable(chopping ? "message.basedefense.lumberjack.chop_on"
                : "message.basedefense.lumberjack.chop_off", this.getDisplayName())
                .formatted(chopping ? Formatting.GREEN : Formatting.GRAY), true);
    }

    @Override
    protected boolean isAwayOnDuty() {
        return super.isAwayOnDuty() || (chopping && treeBase != null);
    }

    // ---------- Добыча ----------

    /** Брёвна: каждый день выбирается одна порода, количество зависит от уровня. */
    @Override
    public List<WorkLoot> getWorkLoot(int level) {
        int count = switch (level) {
            case 2 -> 25;
            case 3 -> 35;
            default -> 16;
        };
        return List.of(
                new WorkLoot(Items.OAK_LOG, count),
                new WorkLoot(Items.BIRCH_LOG, count),
                new WorkLoot(Items.SPRUCE_LOG, count),
                new WorkLoot(Items.DARK_OAK_LOG, count),
                new WorkLoot(Items.JUNGLE_LOG, count),
                new WorkLoot(Items.ACACIA_LOG, count));
    }

    @Override
    public int getLootRolls() {
        return 1;
    }

    /** Брёвна за день — только в обычном режиме; на 3 уровне к ним добавляются 6 монет. */
    @Override
    protected void produceOneDay() {
        if (!chopping) {
            super.produceOneDay();
        }
        if (getLevel() >= 3) {
            addLoot(new WorkLoot(ModItems.RESEARCH_COIN, 6));
        }
    }

    /** Сколько еды тратит работающий лесоруб в день. */
    @Override
    public int getWorkFood(int level) {
        return switch (level) {
            case 2 -> 16;
            case 3 -> 23;
            default -> 12;
        };
    }

    /** Работает у деревьев: любые брёвна рядом, кроме еловых (из них углы домика и поленница). */
    @Override
    public boolean isWorkSpot(BlockState state) {
        return state.isIn(BlockTags.LOGS) && !state.isOf(Blocks.SPRUCE_LOG);
    }

    @Override
    protected ItemStack getDefaultTool() {
        return new ItemStack(Items.IRON_AXE);
    }

    // =====================================================================
    // Рубка деревьев
    // =====================================================================

    @Override
    public void tick() {
        super.tick();
        if (!(this.getWorld() instanceof ServerWorld world) || !this.isAlive()) {
            return;
        }
        if (!chopping || getHome() == null || isResting() || isSettlementFrozen()) {
            return;
        }
        long day = world.getTimeOfDay() / 24000L;
        if (day != chopDay) {
            chopDay = day;
            choppedToday = 0;
        }
        int level = Math.max(1, Math.min(MAX_LEVEL, getLevel()));
        if (choppedToday >= DAILY_CAP[level]) {
            treeBase = null;
            logs.clear();
            return;
        }
        if (treeBase == null) {
            if (--searchCooldown <= 0) {
                searchCooldown = 20;
                treeBase = findTree(world);
                if (treeBase == null) {
                    searchRadius = Math.min(MAX_TREE_DISTANCE, searchRadius + 12);
                } else {
                    logs.clear();
                    walkTicks = 0;
                }
            }
            return;
        }
        if (logs.isEmpty() && !world.getBlockState(treeBase).isIn(BlockTags.LOGS)) {
            treeBase = null;   // дерево уже срубили (игрок или кто-то ещё)
            return;
        }
        double dx = this.getX() - (treeBase.getX() + 0.5);
        double dz = this.getZ() - (treeBase.getZ() + 0.5);
        if (dx * dx + dz * dz > 3.2 * 3.2) {
            if (++walkTicks > 900) {
                treeBase = null;   // не дойти — ищем другое
                logs.clear();
            }
            return;
        }
        if (logs.isEmpty()) {
            collectLogs(world, treeBase);
        }
        this.getLookControl().lookAt(treeBase.getX() + 0.5, treeBase.getY() + 1.5, treeBase.getZ() + 0.5);
        if (++chopTimer < CHOP_INTERVAL[level]) {
            if (chopTimer % 7 == 0) {
                this.swingHand(Hand.MAIN_HAND);
            }
            return;
        }
        chopTimer = 0;
        BlockPos next = logs.pollLast();   // сверху вниз
        if (next != null) {
            BlockState state = world.getBlockState(next);
            if (state.isIn(BlockTags.LOGS)) {
                Block block = state.getBlock();
                world.breakBlock(next, false, this);
                storeItem(new ItemStack(block.asItem()));
                choppedToday++;
                this.swingHand(Hand.MAIN_HAND);
            }
        }
        if (logs.isEmpty()) {
            replant(world, treeBase);
            world.playSound(null, treeBase, SoundEvents.BLOCK_WOOD_BREAK, SoundCategory.NEUTRAL, 1.0f, 0.8f);
            treeBase = null;
            searchCooldown = 60;
        }
    }

    /** Ближайшее дикое дерево: случайные колонки вокруг дома, верхний «не листвяной» блок — бревно. */
    @Nullable
    private BlockPos findTree(ServerWorld world) {
        BlockPos home = getHome();
        if (home == null) {
            return null;
        }
        BlockPos best = null;
        double bestDist = Double.MAX_VALUE;
        for (int i = 0; i < 48; i++) {
            double angle = this.random.nextDouble() * Math.PI * 2;
            double r = Math.sqrt(this.random.nextDouble()) * searchRadius;
            int x = home.getX() + (int) Math.round(Math.cos(angle) * r);
            int z = home.getZ() + (int) Math.round(Math.sin(angle) * r);
            BlockPos column = new BlockPos(x, home.getY(), z);
            if (!world.isChunkLoaded(column)) {
                continue;
            }
            int top = world.getTopY(Heightmap.Type.MOTION_BLOCKING_NO_LEAVES, x, z);
            BlockPos log = new BlockPos(x, top - 1, z);
            if (!world.getBlockState(log).isIn(BlockTags.LOGS) || !isNaturalTree(world, log)) {
                continue;
            }
            // Спускаемся к основанию ствола
            BlockPos base = log;
            for (int k = 0; k < 32 && world.getBlockState(base.down()).isIn(BlockTags.LOGS); k++) {
                base = base.down();
            }
            if (!world.getBlockState(base.down()).isIn(BlockTags.DIRT)) {
                continue;   // ствол не на земле — это не дерево (постройка, мост…)
            }
            if (base.getSquaredDistance(home) > (double) MAX_TREE_DISTANCE * MAX_TREE_DISTANCE) {
                continue;
            }
            double d = base.getSquaredDistance(this.getBlockPos());
            if (d < bestDist) {
                bestDist = d;
                best = base.toImmutable();
            }
        }
        return best;
    }

    /** У дикого дерева рядом с верхушкой есть «природная» листва (посаженная по чертежу — вечная). */
    private static boolean isNaturalTree(World world, BlockPos top) {
        for (BlockPos p : BlockPos.iterate(top.add(-2, -1, -2), top.add(2, 2, 2))) {
            BlockState state = world.getBlockState(p);
            if (state.getBlock() instanceof LeavesBlock && !state.get(Properties.PERSISTENT)) {
                return true;
            }
        }
        return false;
    }

    /** Все брёвна этого дерева (той же породы, не дальше 3 блоков от ствола), снизу вверх. */
    private void collectLogs(World world, BlockPos base) {
        Block kind = world.getBlockState(base).getBlock();
        Set<BlockPos> seen = new HashSet<>();
        ArrayDeque<BlockPos> queue = new ArrayDeque<>();
        List<BlockPos> found = new ArrayList<>();
        queue.add(base);
        seen.add(base);
        while (!queue.isEmpty() && found.size() < 96) {
            BlockPos pos = queue.poll();
            found.add(pos);
            for (BlockPos n : BlockPos.iterate(pos.add(-1, 0, -1), pos.add(1, 1, 1))) {
                BlockPos np = n.toImmutable();
                if (seen.contains(np) || Math.abs(np.getX() - base.getX()) > 3 || Math.abs(np.getZ() - base.getZ()) > 3
                        || np.getY() - base.getY() > 32) {
                    continue;
                }
                seen.add(np);
                if (world.getBlockState(np).isOf(kind)) {
                    queue.add(np);
                }
            }
        }
        found.sort((a, b) -> Integer.compare(a.getY(), b.getY()));
        logs.clear();
        logs.addAll(found);
    }

    /** Сажает саженец той же породы на место пня (если там земля). Породу узнаёт по соседней листве. */
    private static void replant(ServerWorld world, BlockPos base) {
        if (!world.getBlockState(base).isAir() || !world.getBlockState(base.down()).isIn(BlockTags.DIRT)) {
            return;
        }
        for (BlockPos p : BlockPos.iterate(base.add(-3, 1, -3), base.add(3, 8, 3))) {
            BlockState leaf = world.getBlockState(p);
            if (leaf.getBlock() instanceof LeavesBlock) {
                Identifier id = Registries.BLOCK.getId(leaf.getBlock());
                Identifier sapling = new Identifier(id.getNamespace(), id.getPath().replace("_leaves", "_sapling"));
                if (Registries.BLOCK.containsId(sapling)) {
                    world.setBlockState(base, Registries.BLOCK.get(sapling).getDefaultState());
                }
                return;
            }
        }
    }

    @Override
    public void writeCustomDataToNbt(NbtCompound nbt) {
        super.writeCustomDataToNbt(nbt);
        nbt.putBoolean("Chopping", chopping);
    }

    @Override
    public void readCustomDataFromNbt(NbtCompound nbt) {
        super.readCustomDataFromNbt(nbt);
        chopping = nbt.getBoolean("Chopping");
    }

    /** Идёт к выбранному дереву. */
    static class GoToTreeGoal extends Goal {

        private final LumberjackEntity lumberjack;

        GoToTreeGoal(LumberjackEntity lumberjack) {
            this.lumberjack = lumberjack;
            this.setControls(EnumSet.of(Control.MOVE, Control.LOOK));
        }

        @Override
        public boolean canStart() {
            return lumberjack.chopping && lumberjack.treeBase != null && !lumberjack.isResting()
                    && !lumberjack.isSettlementFrozen();
        }

        @Override
        public boolean shouldContinue() {
            return canStart();
        }

        @Override
        public void stop() {
            lumberjack.getNavigation().stop();
        }

        @Override
        public void tick() {
            BlockPos tree = lumberjack.treeBase;
            if (tree == null) {
                return;
            }
            double dx = lumberjack.getX() - (tree.getX() + 0.5);
            double dz = lumberjack.getZ() - (tree.getZ() + 0.5);
            if (dx * dx + dz * dz > 2.5 * 2.5) {
                if (lumberjack.getNavigation().isIdle() || lumberjack.age % 40 == 0) {
                    lumberjack.getNavigation().startMovingTo(tree.getX() + 0.5, tree.getY(), tree.getZ() + 0.5, 0.8);
                }
            } else {
                lumberjack.getNavigation().stop();
                lumberjack.getLookControl().lookAt(Vec3d.ofCenter(tree.up()));
            }
        }
    }
}

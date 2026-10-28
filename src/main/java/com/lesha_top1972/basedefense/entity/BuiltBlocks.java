package com.lesha_top1972.basedefense.entity;

import net.minecraft.block.AbstractCauldronBlock;
import net.minecraft.block.AbstractChestBlock;
import net.minecraft.block.AbstractFurnaceBlock;
import net.minecraft.block.AnvilBlock;
import net.minecraft.block.BarrelBlock;
import net.minecraft.block.BeaconBlock;
import net.minecraft.block.BedBlock;
import net.minecraft.block.BellBlock;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.block.BrewingStandBlock;
import net.minecraft.block.CampfireBlock;
import net.minecraft.block.ComposterBlock;
import net.minecraft.block.CraftingTableBlock;
import net.minecraft.block.DispenserBlock;
import net.minecraft.block.DoorBlock;
import net.minecraft.block.EnchantingTableBlock;
import net.minecraft.block.GrindstoneBlock;
import net.minecraft.block.HopperBlock;
import net.minecraft.block.JukeboxBlock;
import net.minecraft.block.LadderBlock;
import net.minecraft.block.LecternBlock;
import net.minecraft.block.LoomBlock;
import net.minecraft.block.NoteBlock;
import net.minecraft.block.ShulkerBoxBlock;
import net.minecraft.block.StonecutterBlock;
import net.minecraft.block.TorchBlock;
import net.minecraft.registry.Registries;
import net.minecraft.registry.tag.BlockTags;

import java.util.IdentityHashMap;
import java.util.Map;

/**
 * Блоки построек, на которые агрятся мобы‑разрушители (подкопщик, осадный жук, кровопускатель; поиск —
 * {@link BuildTargets}, радиус 200).
 * <ul>
 * <li>{@link #PRIORITY} — главные цели: двери, стекло (блоки и панели), функциональные блоки (верстак, печки,
 * сундуки, бочки, кровати, наковальни, столы, станки, котлы, кафедры и т.п.);</li>
 * <li>{@link #TARGET} — обычные цели: доски, заборы и калитки, ступеньки, лестницы, факелы.</li>
 * </ul>
 * Остальные блоки — не цель, но ломаются, если загораживают путь к цели (см. {@link #obstacle}).
 */
public final class BuiltBlocks {

    public static final int NONE = 0;
    /** Обычная цель. */
    public static final int TARGET = 1;
    /** Главная цель: двери, стекло, функциональные блоки. */
    public static final int PRIORITY = 2;

    private static final Map<Block, Integer> KIND = new IdentityHashMap<>();

    private BuiltBlocks() {
    }

    public static synchronized int kind(Block b) {
        Integer k = KIND.get(b);
        if (k == null) {
            k = compute(b);
            KIND.put(b, k);
        }
        return k;
    }

    private static boolean functional(Block b) {
        return b instanceof CraftingTableBlock || b instanceof AbstractFurnaceBlock || b instanceof AbstractChestBlock
                || b instanceof BarrelBlock || b instanceof BedBlock || b instanceof AnvilBlock
                || b instanceof GrindstoneBlock || b instanceof StonecutterBlock || b instanceof LoomBlock
                || b instanceof BrewingStandBlock || b instanceof EnchantingTableBlock || b instanceof LecternBlock
                || b instanceof ComposterBlock || b instanceof AbstractCauldronBlock || b instanceof BellBlock
                || b instanceof BeaconBlock || b instanceof JukeboxBlock || b instanceof NoteBlock
                || b instanceof HopperBlock || b instanceof DispenserBlock || b instanceof ShulkerBoxBlock
                || b instanceof CampfireBlock || b == Blocks.RESPAWN_ANCHOR || b == Blocks.LODESTONE;
    }

    private static int compute(Block b) {
        BlockState d = b.getDefaultState();
        String id = Registries.BLOCK.getId(b).getPath();
        if (b instanceof DoorBlock || d.isIn(BlockTags.DOORS) || id.contains("glass") || functional(b)) {
            return PRIORITY;
        }
        if (d.isIn(BlockTags.PLANKS) || d.isIn(BlockTags.FENCES) || d.isIn(BlockTags.FENCE_GATES)
                || d.isIn(BlockTags.STAIRS) || b instanceof LadderBlock || b instanceof TorchBlock) {
            return TARGET;
        }
        return NONE;
    }

    public static boolean isTarget(BlockState st) {
        return kind(st.getBlock()) != NONE;
    }

    public static boolean isPriority(BlockState st) {
        return kind(st.getBlock()) == PRIORITY;
    }

    /** Старое имя (подкопщик): то же, что {@link #isTarget}. */
    public static boolean artificial(BlockState st) {
        return isTarget(st);
    }

    public static boolean isObsidian(BlockState st) {
        return st.isOf(Blocks.OBSIDIAN) || st.isOf(Blocks.CRYING_OBSIDIAN);
    }

    /**
     * Можно ли сломать блок, который загораживает путь к цели: любой твёрдый блок, кроме неразрушимых (бедрок,
     * «База»), жидкостей и чужих блок‑сущностей (сундуки и т.п. ломаются только как цели).
     */
    public static boolean obstacle(net.minecraft.world.World w, net.minecraft.util.math.BlockPos p) {
        BlockState st = w.getBlockState(p);
        if (st.isAir() || !st.getFluidState().isEmpty()) {
            return false;
        }
        if (st.getHardness(w, p) < 0) {
            return false;
        }
        return isTarget(st) || !st.hasBlockEntity();
    }
}

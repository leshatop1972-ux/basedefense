package com.lesha_top1972.basedefense.entity;

import net.minecraft.block.AbstractFurnaceBlock;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.registry.Registries;
import net.minecraft.registry.tag.BlockTags;

import java.util.IdentityHashMap;
import java.util.Map;

/**
 * Блоки построек, на которые агрятся подкопщик и осадный жук (поиск — {@link BuildTargets}, радиус 200):
 * заборы и калитки, стекло (блоки и панели), печки (обычная, плавильная, коптильня), доски (и деревянные
 * ступеньки/плиты), булыжник (и его ступеньки/плиты/стены) — их можно ломать;
 * обсидиан и кирпичи (любые) — тоже цель, но подкопщик их сломать не может (жук не может только обсидиан).
 */
public final class BuiltBlocks {

    public static final int NONE = 0;
    /** Цель, ломается. */
    public static final int TARGET = 1;
    /** Цель, но подкопщику не по силам (обсидиан, кирпичи). */
    public static final int HARD = 2;

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

    private static int compute(Block b) {
        if (b == Blocks.OBSIDIAN || b == Blocks.CRYING_OBSIDIAN) {
            return HARD;
        }
        BlockState d = b.getDefaultState();
        String path = Registries.BLOCK.getId(b).getPath();
        if (d.isIn(BlockTags.FENCES) || d.isIn(BlockTags.FENCE_GATES)) {
            return TARGET;
        }
        if (path.contains("glass") || b instanceof AbstractFurnaceBlock) {
            return TARGET;
        }
        if (d.isIn(BlockTags.PLANKS) || d.isIn(BlockTags.WOODEN_STAIRS) || d.isIn(BlockTags.WOODEN_SLABS)) {
            return TARGET;
        }
        if (path.contains("cobblestone")) {
            return TARGET;
        }
        if (path.contains("brick")) {
            return HARD;
        }
        return NONE;
    }

    public static boolean isTarget(BlockState st) {
        return kind(st.getBlock()) != NONE;
    }

    /** Старое имя (подкопщик): то же, что {@link #isTarget}. */
    public static boolean artificial(BlockState st) {
        return isTarget(st);
    }

    public static boolean isObsidian(BlockState st) {
        return st.isOf(Blocks.OBSIDIAN) || st.isOf(Blocks.CRYING_OBSIDIAN);
    }
}

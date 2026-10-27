package com.lesha_top1972.basedefense.entity;

import com.lesha_top1972.basedefense.Basedefense;
import net.minecraft.block.BlockState;
import net.minecraft.registry.Registries;
import net.minecraft.registry.tag.BlockTags;
import net.minecraft.util.Identifier;

/**
 * «Не природные» блоки — то, что построили игроки: доски, кирпич, булыжник, стекло, шерсть, лестницы, плиты,
 * заборы, двери, бетон, полированный камень и т.п. Земля, камень, песок, руды, деревья, листва — природные.
 * Ими интересуется подкопщик: ищет такие блоки и ломает их.
 */
public final class BuiltBlocks {

    private static final String[] KEYS = {"planks", "brick", "cobblestone", "glass", "polished_", "smooth_", "concrete",
            "glazed", "cut_", "chiseled_", "stripped_", "_tiles", "bookshelf", "hay_block", "lantern", "iron_bars",
            "ladder", "scaffolding", "carpet", "quartz", "_block"};
    /** Природные блоки, которые иначе попали бы под ключи (например, «_block» у глины и снега нет — но мох/грибы есть). */
    private static final String[] NATURAL = {"moss_block", "mushroom_block", "snow_block", "clay", "dripstone_block",
            "magma_block", "bone_block", "honeycomb_block", "infested", "amethyst_block", "budding", "slime_block",
            "sculk", "nether_wart_block", "warped_wart_block", "packed_ice", "blue_ice", "dried_kelp_block", "powder_snow"};

    private BuiltBlocks() {
    }

    public static boolean artificial(BlockState st) {
        if (st.isAir() || !st.getFluidState().isEmpty()) {
            return false;
        }
        if (st.isIn(BlockTags.PLANKS) || st.isIn(BlockTags.WOOL) || st.isIn(BlockTags.STAIRS) || st.isIn(BlockTags.SLABS)
                || st.isIn(BlockTags.WALLS) || st.isIn(BlockTags.FENCES) || st.isIn(BlockTags.FENCE_GATES)
                || st.isIn(BlockTags.DOORS) || st.isIn(BlockTags.TRAPDOORS) || st.isIn(BlockTags.BEDS)
                || st.isIn(BlockTags.STONE_BRICKS)) {
            return true;
        }
        Identifier id = Registries.BLOCK.getId(st.getBlock());
        String path = id.getPath();
        if (Basedefense.MOD_ID.equals(id.getNamespace())) {
            return !path.equals("blood_pool") && !path.equals("resonant_stone");
        }
        for (String n : NATURAL) {
            if (path.contains(n)) {
                return false;
            }
        }
        for (String k : KEYS) {
            if (path.contains(k)) {
                return true;
            }
        }
        return false;
    }
}

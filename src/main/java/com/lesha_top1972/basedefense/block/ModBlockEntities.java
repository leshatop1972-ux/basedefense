package com.lesha_top1972.basedefense.block;

import com.lesha_top1972.basedefense.Basedefense;
import net.minecraft.block.entity.BlockEntityType;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.util.Identifier;

/** Блок-сущности мода (блоки, которые хранят предметы и работают сами). */
public final class ModBlockEntities {

    /** Стол амулетов. */
    public static final BlockEntityType<AmuletTableBlockEntity> AMULET_TABLE = Registry.register(
            Registries.BLOCK_ENTITY_TYPE,
            new Identifier(Basedefense.MOD_ID, "amulet_table"),
            BlockEntityType.Builder.create(AmuletTableBlockEntity::new, ModBlocks.AMULET_TABLE).build(null));

    private ModBlockEntities() {
    }

    public static void init() {
        // Пустой метод: вызов нужен, чтобы класс загрузился и тип зарегистрировался.
    }
}

package com.lesha_top1972.basedefense.artifact;

import net.minecraft.inventory.SimpleInventory;

/** Игрок с 5 слотами артефактов (добавлено миксином в PlayerEntity, хранится в NBT игрока). */
public interface ArtifactHolder {

    SimpleInventory basedefense$artifacts();
}

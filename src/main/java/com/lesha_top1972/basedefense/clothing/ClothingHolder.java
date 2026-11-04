package com.lesha_top1972.basedefense.clothing;

import net.minecraft.inventory.SimpleInventory;

/** Игрок со слоем одежды (4 слота; добавлено миксином в PlayerEntity, хранится в NBT игрока). */
public interface ClothingHolder {

    SimpleInventory basedefense$clothing();
}

package com.lesha_top1972.basedefense.client.entity.nabat;

import com.lesha_top1972.basedefense.client.entity.bedrock.WeaponTrack;
import net.minecraft.client.model.ModelPart;
import org.joml.Quaternionf;

/** Модель с отдельным оружием: дорожка оружия, цепочка костей до кисти и итоговый поворот кадра. */
public interface WeaponHolder {

    WeaponTrack weaponTrack();

    /** Кости от корня модели до кисти (включительно), в порядке вложенности. */
    ModelPart[] handChain();

    /** Поворот оружия в этом кадре (заполняет setAngles). */
    Quaternionf weaponRotation();

    /** Видно ли оружие. */
    default boolean weaponVisible() {
        return true;
    }
}

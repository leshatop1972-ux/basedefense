package com.lesha_top1972.basedefense.client.entity.bedrock;

import net.minecraft.client.model.ModelPart;
import org.joml.Quaternionf;

/**
 * Модель с отдельным оружием: дорожка оружия, цепочка костей до кисти и итоговый поворот кадра.
 * Оружий может быть несколько (слоты: 0 — правая рука, 1 — левая); по умолчанию одно.
 */
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

    /** Масштаб оружия в этом кадре. */
    default float weaponScale() {
        return 1f;
    }

    default WeaponTrack weaponTrack(int slot) {
        return this.weaponTrack();
    }

    default ModelPart[] handChain(int slot) {
        return this.handChain();
    }

    default Quaternionf weaponRotation(int slot) {
        return this.weaponRotation();
    }

    default boolean weaponVisible(int slot) {
        return this.weaponVisible();
    }

    default float weaponScale(int slot) {
        return this.weaponScale();
    }

    /** Поставить кости внутри модели оружия (тетива, вымпел…) перед отрисовкой; по умолчанию — ничего. */
    default void poseWeapon(int slot, ModelPart weaponRoot) {
    }
}

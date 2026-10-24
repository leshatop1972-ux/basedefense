package com.lesha_top1972.basedefense.sound;

import com.lesha_top1972.basedefense.Basedefense;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.sound.SoundEvent;
import net.minecraft.util.Identifier;

/** Свои звуки мода (файлы — assets/basedefense/sounds, список — sounds.json; создаёт tools/curb_sounds.py). */
public final class ModSounds {

    /** Реактивная струя из локтя Бордюра разгоняется (≈1.6 с). */
    public static final SoundEvent CURB_JET_CHARGE = register("entity.curb.jet_charge");
    /** Рывок на реактивной тяге: хлопок и свист. */
    public static final SoundEvent CURB_JET_BURST = register("entity.curb.jet_burst");

    private ModSounds() {
    }

    private static SoundEvent register(String name) {
        Identifier id = new Identifier(Basedefense.MOD_ID, name);
        return Registry.register(Registries.SOUND_EVENT, id, SoundEvent.of(id));
    }

    public static void init() {
        // загрузка класса регистрирует звуки
    }
}

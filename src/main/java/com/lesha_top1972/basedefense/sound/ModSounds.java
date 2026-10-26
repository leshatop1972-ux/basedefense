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

    // --- Набат (синтез — tools/nabat_sounds.py) ---
    public static final SoundEvent NABAT_TOLL = register("entity.nabat.toll");
    public static final SoundEvent NABAT_GATHER = register("entity.nabat.gather");
    public static final SoundEvent NABAT_CHIME = register("entity.nabat.chime");
    public static final SoundEvent NABAT_SUMMON = register("entity.nabat.summon");
    public static final SoundEvent NABAT_WING = register("entity.nabat.wing");
    public static final SoundEvent NABAT_CRASH = register("entity.nabat.crash");
    public static final SoundEvent NABAT_SLAM = register("entity.nabat.slam");
    public static final SoundEvent NABAT_QUAKE = register("entity.nabat.quake");
    public static final SoundEvent NABAT_REQUIEM = register("entity.nabat.requiem");
    public static final SoundEvent NABAT_REQUIEM_BREAK = register("entity.nabat.requiem_break");
    public static final SoundEvent NABAT_HEAL = register("entity.nabat.heal");
    public static final SoundEvent NABAT_DOME_HIT = register("entity.nabat.dome_hit");
    public static final SoundEvent NABAT_MARK = register("entity.nabat.mark");
    public static final SoundEvent NABAT_BELL_LAND = register("entity.nabat.bell_land");
    public static final SoundEvent NABAT_BELL_HUM = register("entity.nabat.bell_hum");
    public static final SoundEvent NABAT_BELL_BREAK = register("entity.nabat.bell_break");
    public static final SoundEvent NABAT_CAPTIVE = register("entity.nabat.captive");
    public static final SoundEvent NABAT_SOUL = register("entity.nabat.soul");
    public static final SoundEvent NABAT_SOUL_DASH = register("entity.nabat.soul_dash");
    public static final SoundEvent NABAT_SOUL_POP = register("entity.nabat.soul_pop");
    public static final SoundEvent NABAT_RELIQUARY = register("entity.nabat.reliquary");
    public static final SoundEvent NABAT_CHAIN_BREAK = register("entity.nabat.chain_break");
    public static final SoundEvent NABAT_CRACK = register("entity.nabat.crack");
    public static final SoundEvent NABAT_INTRO = register("entity.nabat.intro");
    public static final SoundEvent NABAT_DEATH = register("entity.nabat.death");
    public static final SoundEvent NABAT_AMBIENT = register("entity.nabat.ambient");
    public static final SoundEvent NABAT_HURT = register("entity.nabat.hurt");
    public static final SoundEvent NABAT_SWING = register("entity.nabat.swing");
    public static final SoundEvent BLADE_SWING = register("entity.nabat_blade.swing");
    public static final SoundEvent BLADE_PARRY = register("entity.nabat_blade.parry");
    public static final SoundEvent BLADE_STEP = register("entity.nabat_blade.step");
    public static final SoundEvent BLADE_HURT = register("entity.nabat_blade.hurt");
    public static final SoundEvent BLADE_DEATH = register("entity.nabat_blade.death");
    public static final SoundEvent BLADE_ASSEMBLE = register("entity.nabat_blade.assemble");

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

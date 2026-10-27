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

    // Подкопщик (tools/sapper_sounds.py)
    public static final SoundEvent SAPPER_DIG = register("entity.sapper.dig");
    public static final SoundEvent SAPPER_CRUMBLE = register("entity.sapper.crumble");
    public static final SoundEvent SAPPER_EMERGE = register("entity.sapper.emerge");
    public static final SoundEvent SAPPER_LIGHT = register("entity.sapper.light");
    public static final SoundEvent SAPPER_FUSE = register("entity.sapper.fuse");
    public static final SoundEvent SAPPER_BOMB = register("entity.sapper.bomb");
    public static final SoundEvent SAPPER_BLAST = register("entity.sapper.blast");
    public static final SoundEvent SAPPER_SMOKE = register("entity.sapper.smoke");
    public static final SoundEvent SAPPER_AMBIENT = register("entity.sapper.ambient");
    public static final SoundEvent SAPPER_STEP = register("entity.sapper.step");
    public static final SoundEvent SAPPER_JAB = register("entity.sapper.jab");
    public static final SoundEvent SAPPER_SNUFF = register("entity.sapper.snuff");
    public static final SoundEvent SAPPER_THROW = register("entity.sapper.throw");
    public static final SoundEvent SAPPER_HURT = register("entity.sapper.hurt");
    public static final SoundEvent SAPPER_DEATH = register("entity.sapper.death");

    // Осадный жук, маленький жук, яйцо (tools/beetle_sounds.py)
    public static final SoundEvent BEETLE_STEP = register("entity.siege_beetle.step");
    public static final SoundEvent BEETLE_SCRAPE = register("entity.siege_beetle.scrape");
    public static final SoundEvent BEETLE_AMBIENT = register("entity.siege_beetle.ambient");
    public static final SoundEvent BEETLE_ROAR = register("entity.siege_beetle.roar");
    public static final SoundEvent BEETLE_RAM = register("entity.siege_beetle.ram");
    public static final SoundEvent BEETLE_HORN_SWING = register("entity.siege_beetle.horn_swing");
    public static final SoundEvent BEETLE_HORN_HIT = register("entity.siege_beetle.horn_hit");
    public static final SoundEvent BEETLE_MANDIBLE = register("entity.siege_beetle.mandible");
    public static final SoundEvent BEETLE_LAY = register("entity.siege_beetle.lay");
    public static final SoundEvent BEETLE_HURT = register("entity.siege_beetle.hurt");
    public static final SoundEvent BEETLE_DEATH = register("entity.siege_beetle.death");
    public static final SoundEvent BEETLE_HATCH = register("entity.beetle_egg.hatch");
    public static final SoundEvent MITE_AMBIENT = register("entity.beetle_mite.ambient");
    public static final SoundEvent MITE_STEP = register("entity.beetle_mite.step");
    public static final SoundEvent MITE_BITE = register("entity.beetle_mite.bite");
    public static final SoundEvent MITE_DEATH = register("entity.beetle_mite.death");

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

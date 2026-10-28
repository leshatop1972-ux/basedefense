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

    // Железный зомби, железный рыцарь (урон), Бордюр и прислужник (tools/iron_sounds.py)
    public static final SoundEvent IRON_ZOMBIE_STEP = register("entity.iron_zombie.step");
    public static final SoundEvent IRON_ZOMBIE_AMBIENT = register("entity.iron_zombie.ambient");
    public static final SoundEvent IRON_ZOMBIE_SCRAPE = register("entity.iron_zombie.scrape");
    public static final SoundEvent IRON_ZOMBIE_ATTACK = register("entity.iron_zombie.attack");
    public static final SoundEvent IRON_ZOMBIE_HURT = register("entity.iron_zombie.hurt");
    public static final SoundEvent IRON_ZOMBIE_DEATH = register("entity.iron_zombie.death");
    public static final SoundEvent IRON_ZOMBIE_GRAB = register("entity.iron_zombie.grab");
    public static final SoundEvent IRON_ZOMBIE_LUNGE = register("entity.iron_zombie.lunge");
    public static final SoundEvent IRON_KNIGHT_HURT = register("entity.iron_knight.hurt");
    public static final SoundEvent CURB_STEP = register("entity.curb.step");
    public static final SoundEvent CURB_PUNCH = register("entity.curb.punch");
    public static final SoundEvent CURB_SERVO = register("entity.curb.servo");
    public static final SoundEvent CURB_HURT = register("entity.curb.hurt");
    public static final SoundEvent CURB_DEATH = register("entity.curb.death");
    public static final SoundEvent CURB_MINION_STEP = register("entity.curb_minion.step");
    public static final SoundEvent CURB_MINION_SERVO = register("entity.curb_minion.servo");
    public static final SoundEvent CURB_MINION_HURT = register("entity.curb_minion.hurt");
    public static final SoundEvent CURB_MINION_DEATH = register("entity.curb_minion.death");

    // Туманная и кровавая сущности (tools/entity_sounds.py)
    public static final SoundEvent MIST_SCREECH = register("entity.mist_entity.screech");
    public static final SoundEvent MIST_GLITCH = register("entity.mist_entity.glitch");
    public static final SoundEvent MIST_WHISPER = register("entity.mist_entity.whisper");
    public static final SoundEvent MIST_STING = register("entity.mist_entity.sting");
    public static final SoundEvent MIST_HURT = register("entity.mist_entity.hurt");
    public static final SoundEvent MIST_DEATH = register("entity.mist_entity.death");
    public static final SoundEvent BLOOD_GROWL = register("entity.blood_entity.growl");
    public static final SoundEvent BLOOD_ATTACK = register("entity.blood_entity.attack");
    public static final SoundEvent BLOOD_HISS = register("entity.blood_entity.hiss");
    public static final SoundEvent BLOOD_HURT = register("entity.blood_entity.hurt");
    public static final SoundEvent BLOOD_DEATH = register("entity.blood_entity.death");
    public static final SoundEvent BLOOD_STEP = register("entity.blood_entity.step");

    // Голоса жителей: мужские и женские (tools/settler_sounds.py)
    public static final SoundEvent SETTLER_MALE_AMBIENT = register("entity.settler.male.ambient");
    public static final SoundEvent SETTLER_MALE_GREET = register("entity.settler.male.greet");
    public static final SoundEvent SETTLER_MALE_YES = register("entity.settler.male.yes");
    public static final SoundEvent SETTLER_MALE_HURT = register("entity.settler.male.hurt");
    public static final SoundEvent SETTLER_MALE_DEATH = register("entity.settler.male.death");
    public static final SoundEvent SETTLER_FEMALE_AMBIENT = register("entity.settler.female.ambient");
    public static final SoundEvent SETTLER_FEMALE_GREET = register("entity.settler.female.greet");
    public static final SoundEvent SETTLER_FEMALE_YES = register("entity.settler.female.yes");
    public static final SoundEvent SETTLER_FEMALE_HURT = register("entity.settler.female.hurt");
    public static final SoundEvent SETTLER_FEMALE_DEATH = register("entity.settler.female.death");

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

package com.lesha_top1972.basedefense;

import com.lesha_top1972.basedefense.block.ModBlocks;
import com.lesha_top1972.basedefense.weapon.BloodSwordCombat;
import com.lesha_top1972.basedefense.effect.ModEffects;
import com.lesha_top1972.basedefense.entity.ModEntities;
import com.lesha_top1972.basedefense.entity.settler.SettlerThreats;
import com.lesha_top1972.basedefense.item.ModItems;
import com.lesha_top1972.basedefense.screen.ModScreenHandlers;
import com.lesha_top1972.basedefense.sound.ModSounds;
import com.lesha_top1972.basedefense.entity.enemy.nabat.NabatNoise;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;

public class Basedefense implements ModInitializer {

    public static final String MOD_ID = "basedefense";

    @Override
    public void onInitialize() {
        // Порядок важен: мобы раньше предметов (яйцо призыва ссылается на моба)
        ModSounds.init();
        ModEffects.init();
        ModEntities.init();
        com.lesha_top1972.basedefense.entity.enemy.thunder_tiger.TigerArena.init();   // арена «Громового тигра»
        ModBlocks.init();
        com.lesha_top1972.basedefense.block.ModBlockEntities.init();
        ModItems.init();
        com.lesha_top1972.basedefense.clothing.ModClothing.init();   // одежда (обычная и тёплая)
        ModScreenHandlers.init();
        SettlerThreats.init();
        com.lesha_top1972.basedefense.military.GuardEvents.init();   // Повестка: ЛКМ по блоку — точка обороны отряда
        com.lesha_top1972.basedefense.artifact.Artifacts.init();   // слоты артефактов, амулет рывка
        com.lesha_top1972.basedefense.clothing.Clothing.init();   // слой одежды у игроков (под бронёй)
        com.lesha_top1972.basedefense.clothing.Backpacks.init();  // вкладка «Рюкзак»
        com.lesha_top1972.basedefense.season.Seasons.init();          // времена года, команда /season
        com.lesha_top1972.basedefense.temperature.Temperature.init(); // температура, команда /temperature
        com.lesha_top1972.basedefense.needs.Needs.init();             // потребности: комфорт, ощущаемая температура
        com.lesha_top1972.basedefense.needs.NeedsNetwork.init();      // вкладка «Инвентарь» у поселенца — снова открыть его меню
        com.lesha_top1972.basedefense.director.Director.init();   // «Режиссёр мира»: дни, акты, шкала, события
        BloodSwordCombat.init();       // «Кровавый разруб» меча кровожада
        com.lesha_top1972.basedefense.weapon.KatanaCombat.init();
        com.lesha_top1972.basedefense.weapon.KatanaPower.init();   // катана: серия, блок, «Иссэн»; древняя сила
        // «Шум» игроков в бою с Набатом
        ServerTickEvents.END_SERVER_TICK.register(NabatNoise::tick);
    }
}

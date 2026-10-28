package com.lesha_top1972.basedefense;

import com.lesha_top1972.basedefense.block.ModBlocks;
import com.lesha_top1972.basedefense.weapon.BloodSwordCombat;
import com.lesha_top1972.basedefense.effect.ModEffects;
import com.lesha_top1972.basedefense.entity.ModEntities;
import com.lesha_top1972.basedefense.entity.settler.SettlerThreats;
import com.lesha_top1972.basedefense.item.ModItems;
import com.lesha_top1972.basedefense.research.ResearchManager;
import com.lesha_top1972.basedefense.screen.ModScreenHandlers;
import com.lesha_top1972.basedefense.settlement.SettlementManager;
import com.lesha_top1972.basedefense.sound.ModSounds;
import com.lesha_top1972.basedefense.structure.BuildManager;
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
        ModBlocks.init();
        ModItems.init();
        ModScreenHandlers.init();
        BuildManager.init();
        ResearchManager.init();
        SettlementManager.init();
        SettlerThreats.init();
        com.lesha_top1972.basedefense.military.GuardEvents.init();   // Повестка: ЛКМ по блоку — точка обороны отряда
        com.lesha_top1972.basedefense.artifact.Artifacts.init();   // слоты артефактов, амулет рывка
        BloodSwordCombat.init();       // «Кровавый разруб» меча кровожада
        com.lesha_top1972.basedefense.weapon.KatanaCombat.init();   // катана: серия из трёх ударов
        // «Шум» игроков в бою с Набатом
        ServerTickEvents.END_SERVER_TICK.register(NabatNoise::tick);
    }
}

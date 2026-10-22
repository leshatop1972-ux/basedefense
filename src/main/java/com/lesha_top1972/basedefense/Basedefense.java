package com.lesha_top1972.basedefense;

import com.lesha_top1972.basedefense.block.ModBlocks;
import com.lesha_top1972.basedefense.entity.ModEntities;
import com.lesha_top1972.basedefense.entity.SettlerThreats;
import com.lesha_top1972.basedefense.item.ModItems;
import com.lesha_top1972.basedefense.research.ResearchManager;
import com.lesha_top1972.basedefense.screen.ModScreenHandlers;
import com.lesha_top1972.basedefense.settlement.SettlementManager;
import com.lesha_top1972.basedefense.structure.BuildManager;
import net.fabricmc.api.ModInitializer;

public class Basedefense implements ModInitializer {

    public static final String MOD_ID = "basedefense";

    @Override
    public void onInitialize() {
        // Порядок важен: мобы раньше предметов (яйцо призыва ссылается на моба)
        ModEntities.init();
        ModBlocks.init();
        ModItems.init();
        ModScreenHandlers.init();
        BuildManager.init();
        ResearchManager.init();
        SettlementManager.init();
        SettlerThreats.init();
    }
}

package com.lesha_top1972.basedefense;

import com.lesha_top1972.basedefense.block.ModBlocks;
import com.lesha_top1972.basedefense.screen.ModScreenHandlers;
import net.fabricmc.api.ModInitializer;

public class Basedefense implements ModInitializer {

    public static final String MOD_ID = "basedefense";

    @Override
    public void onInitialize() {
        ModBlocks.init();
        ModScreenHandlers.init();
    }
}

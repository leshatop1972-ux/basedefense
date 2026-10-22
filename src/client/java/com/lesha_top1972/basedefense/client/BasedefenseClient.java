package com.lesha_top1972.basedefense.client;

import com.lesha_top1972.basedefense.client.screen.BaseScreen;
import com.lesha_top1972.basedefense.screen.ModScreenHandlers;
import net.fabricmc.api.ClientModInitializer;
import net.minecraft.client.gui.screen.ingame.HandledScreens;

public class BasedefenseClient implements ClientModInitializer {

    @Override
    public void onInitializeClient() {
        // Связываем меню базы с его экраном
        HandledScreens.register(ModScreenHandlers.BASE, BaseScreen::new);
    }
}

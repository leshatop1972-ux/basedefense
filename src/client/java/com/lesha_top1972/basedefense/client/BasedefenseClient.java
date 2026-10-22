package com.lesha_top1972.basedefense.client;

import com.lesha_top1972.basedefense.client.entity.FarmerEntityModel;
import com.lesha_top1972.basedefense.client.entity.FarmerEntityRenderer;
import com.lesha_top1972.basedefense.client.screen.BaseScreen;
import com.lesha_top1972.basedefense.entity.ModEntities;
import com.lesha_top1972.basedefense.screen.ModScreenHandlers;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.rendering.v1.EntityModelLayerRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.EntityRendererRegistry;
import net.minecraft.client.gui.screen.ingame.HandledScreens;

public class BasedefenseClient implements ClientModInitializer {

    @Override
    public void onInitializeClient() {
        // Связываем меню базы с его экраном
        HandledScreens.register(ModScreenHandlers.BASE, BaseScreen::new);

        // Модель и отрисовка жителя-фермера
        EntityModelLayerRegistry.registerModelLayer(FarmerEntityRenderer.LAYER, FarmerEntityModel::getTexturedModelData);
        EntityRendererRegistry.register(ModEntities.FARMER, FarmerEntityRenderer::new);
    }
}

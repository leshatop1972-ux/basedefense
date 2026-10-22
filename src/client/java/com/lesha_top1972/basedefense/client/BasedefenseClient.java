package com.lesha_top1972.basedefense.client;

import com.lesha_top1972.basedefense.client.entity.SettlerEntityModel;
import com.lesha_top1972.basedefense.client.entity.SettlerEntityRenderer;
import com.lesha_top1972.basedefense.client.screen.BaseScreen;
import com.lesha_top1972.basedefense.client.screen.HouseScreen;
import com.lesha_top1972.basedefense.client.screen.SettlerScreen;
import com.lesha_top1972.basedefense.entity.ModEntities;
import com.lesha_top1972.basedefense.screen.ModScreenHandlers;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.rendering.v1.EntityModelLayerRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.EntityRendererRegistry;
import net.minecraft.client.gui.screen.ingame.HandledScreens;

public class BasedefenseClient implements ClientModInitializer {

    @Override
    public void onInitializeClient() {
        // Меню: База, житель, стройплощадка
        HandledScreens.register(ModScreenHandlers.BASE, BaseScreen::new);
        HandledScreens.register(ModScreenHandlers.SETTLER, SettlerScreen::new);
        HandledScreens.register(ModScreenHandlers.HOUSE, HouseScreen::new);

        // Жители: случайная внешность (5 мужчин, 3 женщины), одна модель на все профессии
        EntityModelLayerRegistry.registerModelLayer(SettlerEntityRenderer.LAYER, SettlerEntityModel::getTexturedModelData);
        EntityRendererRegistry.register(ModEntities.BUILDER, SettlerEntityRenderer::new);
        EntityRendererRegistry.register(ModEntities.FARMER, SettlerEntityRenderer::new);
        EntityRendererRegistry.register(ModEntities.RANCHER, SettlerEntityRenderer::new);
        EntityRendererRegistry.register(ModEntities.COOK, SettlerEntityRenderer::new);
        EntityRendererRegistry.register(ModEntities.MINER, SettlerEntityRenderer::new);
        EntityRendererRegistry.register(ModEntities.BLACKSMITH, SettlerEntityRenderer::new);
        EntityRendererRegistry.register(ModEntities.LUMBERJACK, SettlerEntityRenderer::new);
    }
}

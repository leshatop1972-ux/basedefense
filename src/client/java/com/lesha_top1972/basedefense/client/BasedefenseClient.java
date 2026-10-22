package com.lesha_top1972.basedefense.client;

import com.lesha_top1972.basedefense.client.entity.SettlerEntityModel;
import com.lesha_top1972.basedefense.client.entity.SettlerEntityRenderer;
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

        // Модель и отрисовка жителей поселения
        EntityModelLayerRegistry.registerModelLayer(SettlerEntityRenderer.LAYER, SettlerEntityModel::getTexturedModelData);
        EntityRendererRegistry.register(ModEntities.FARMER, ctx -> new SettlerEntityRenderer(ctx, "farmer"));
        EntityRendererRegistry.register(ModEntities.RANCHER, ctx -> new SettlerEntityRenderer(ctx, "rancher"));
    }
}

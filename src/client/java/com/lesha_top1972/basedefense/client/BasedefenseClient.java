package com.lesha_top1972.basedefense.client;

import com.lesha_top1972.basedefense.client.entity.SettlerEntityModel;
import com.lesha_top1972.basedefense.client.entity.SettlerEntityModel.Headwear;
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

        // Модель и отрисовка жителей поселения: текстура и головной убор
        EntityModelLayerRegistry.registerModelLayer(SettlerEntityRenderer.LAYER, SettlerEntityModel::getTexturedModelData);
        EntityRendererRegistry.register(ModEntities.BUILDER, ctx -> new SettlerEntityRenderer(ctx, "builder", Headwear.HARD_HAT));
        EntityRendererRegistry.register(ModEntities.FARMER, ctx -> new SettlerEntityRenderer(ctx, "farmer", Headwear.BRIM_HAT));
        EntityRendererRegistry.register(ModEntities.RANCHER, ctx -> new SettlerEntityRenderer(ctx, "rancher", Headwear.BRIM_HAT));
        EntityRendererRegistry.register(ModEntities.COOK, ctx -> new SettlerEntityRenderer(ctx, "cook", Headwear.TOQUE));
        EntityRendererRegistry.register(ModEntities.MINER, ctx -> new SettlerEntityRenderer(ctx, "miner", Headwear.MINER_HELMET));
        EntityRendererRegistry.register(ModEntities.BLACKSMITH, ctx -> new SettlerEntityRenderer(ctx, "blacksmith", Headwear.NONE));
        EntityRendererRegistry.register(ModEntities.LUMBERJACK, ctx -> new SettlerEntityRenderer(ctx, "lumberjack", Headwear.BEANIE));
    }
}

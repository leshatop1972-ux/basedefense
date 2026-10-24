package com.lesha_top1972.basedefense.client;

import com.lesha_top1972.basedefense.client.entity.BloodEggRenderer;
import com.lesha_top1972.basedefense.client.entity.BloodlingRenderer;
import com.lesha_top1972.basedefense.client.entity.BloodthirsterRenderer;
import com.lesha_top1972.basedefense.client.entity.CurbMinionRenderer;
import com.lesha_top1972.basedefense.client.entity.CurbRenderer;
import com.lesha_top1972.basedefense.client.entity.FrostArcherRenderer;
import com.lesha_top1972.basedefense.client.entity.FallingLanceRenderer;
import com.lesha_top1972.basedefense.client.entity.FrostArrowRenderer;
import com.lesha_top1972.basedefense.client.entity.IronKnightRenderer;
import com.lesha_top1972.basedefense.client.entity.TatteredLancerRenderer;
import com.lesha_top1972.basedefense.client.entity.IronZombieRenderer;
import com.lesha_top1972.basedefense.client.entity.SettlerEntityModel;
import com.lesha_top1972.basedefense.client.entity.bedrock.BedrockModelLoader;
import com.lesha_top1972.basedefense.client.fx.LancerFx;
import com.lesha_top1972.basedefense.client.entity.SettlerEntityRenderer;
import com.lesha_top1972.basedefense.client.render.BloodPoolOverlay;
import com.lesha_top1972.basedefense.client.render.SpikesOverlay;
import com.lesha_top1972.basedefense.client.render.StunOverlay;
import net.minecraft.client.render.entity.EmptyEntityRenderer;
import com.lesha_top1972.basedefense.client.render.DuelistOverlay;
import com.lesha_top1972.basedefense.client.render.FreezeOverlay;
import com.lesha_top1972.basedefense.client.render.LancerZoneRenderer;
import com.lesha_top1972.basedefense.client.render.RatOverlay;
import com.lesha_top1972.basedefense.client.render.TabletPreview;
import com.lesha_top1972.basedefense.client.screen.BaseScreen;
import com.lesha_top1972.basedefense.client.screen.HouseScreen;
import com.lesha_top1972.basedefense.client.screen.SettlerScreen;
import com.lesha_top1972.basedefense.client.screen.TabletScreen;
import com.lesha_top1972.basedefense.entity.ModEntities;
import com.lesha_top1972.basedefense.screen.ModScreenHandlers;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.rendering.v1.EntityModelLayerRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.EntityRendererRegistry;
import net.minecraft.client.gui.screen.ingame.HandledScreens;

public class BasedefenseClient implements ClientModInitializer {

    @Override
    public void onInitializeClient() {
        // Меню: База, житель, стройплощадка, строительный планшет
        HandledScreens.register(ModScreenHandlers.BASE, BaseScreen::new);
        HandledScreens.register(ModScreenHandlers.SETTLER, SettlerScreen::new);
        HandledScreens.register(ModScreenHandlers.HOUSE, HouseScreen::new);
        HandledScreens.register(ModScreenHandlers.TABLET, TabletScreen::new);

        // Рамка места постройки, пока в руке планшет с выбранной постройкой
        TabletPreview.init();

        // Жители: случайная внешность (5 мужчин, 3 женщины), одна модель на все профессии
        EntityModelLayerRegistry.registerModelLayer(SettlerEntityRenderer.LAYER, SettlerEntityModel::getTexturedModelData);
        EntityRendererRegistry.register(ModEntities.BUILDER, SettlerEntityRenderer::new);
        EntityRendererRegistry.register(ModEntities.FARMER, SettlerEntityRenderer::new);
        EntityRendererRegistry.register(ModEntities.RANCHER, SettlerEntityRenderer::new);
        EntityRendererRegistry.register(ModEntities.COOK, SettlerEntityRenderer::new);
        EntityRendererRegistry.register(ModEntities.MINER, SettlerEntityRenderer::new);
        EntityRendererRegistry.register(ModEntities.BLACKSMITH, SettlerEntityRenderer::new);
        EntityRendererRegistry.register(ModEntities.LUMBERJACK, SettlerEntityRenderer::new);

        // Враги: модели и анимации из Blockbench (assets/basedefense/geo и animations)
        EntityModelLayerRegistry.registerModelLayer(IronZombieRenderer.LAYER, () -> BedrockModelLoader.load("iron_zombie"));
        EntityModelLayerRegistry.registerModelLayer(IronKnightRenderer.LAYER, () -> BedrockModelLoader.load("iron_knight"));
        EntityRendererRegistry.register(ModEntities.IRON_ZOMBIE, IronZombieRenderer::new);
        EntityRendererRegistry.register(ModEntities.IRON_KNIGHT, IronKnightRenderer::new);
        EntityModelLayerRegistry.registerModelLayer(FrostArcherRenderer.LAYER, () -> BedrockModelLoader.load("frost_archer"));
        EntityRendererRegistry.register(ModEntities.FROST_ARCHER, FrostArcherRenderer::new);
        EntityRendererRegistry.register(ModEntities.FROST_ARROW, FrostArrowRenderer::new);
        EntityModelLayerRegistry.registerModelLayer(TatteredLancerRenderer.LAYER, () -> BedrockModelLoader.load("tattered_lancer"));
        EntityRendererRegistry.register(ModEntities.TATTERED_LANCER, TatteredLancerRenderer::new);
        EntityRendererRegistry.register(ModEntities.FALLING_LANCE, FallingLanceRenderer::new);
        LancerZoneRenderer.init();
        LancerFx.init();               // эффекты «ливня копий» улана
        // Босс «Кровожад», мелкие кровожады и яйца
        EntityModelLayerRegistry.registerModelLayer(BloodthirsterRenderer.LAYER, () -> BedrockModelLoader.load("bloodthirster"));
        EntityModelLayerRegistry.registerModelLayer(BloodlingRenderer.LAYER, () -> BedrockModelLoader.load("bloodling"));
        EntityModelLayerRegistry.registerModelLayer(BloodEggRenderer.LAYER, () -> BedrockModelLoader.load("blood_egg"));
        EntityRendererRegistry.register(ModEntities.BLOODTHIRSTER, BloodthirsterRenderer::new);
        EntityRendererRegistry.register(ModEntities.BLOODLING, BloodlingRenderer::new);
        EntityRendererRegistry.register(ModEntities.BLOOD_EGG, BloodEggRenderer::new);
        EntityModelLayerRegistry.registerModelLayer(CurbRenderer.LAYER, () -> BedrockModelLoader.load("curb"));
        EntityModelLayerRegistry.registerModelLayer(CurbMinionRenderer.LAYER, () -> BedrockModelLoader.load("curb_minion"));
        EntityRendererRegistry.register(ModEntities.CURB, CurbRenderer::new);
        EntityRendererRegistry.register(ModEntities.CURB_MINION, CurbMinionRenderer::new);
        EntityRendererRegistry.register(ModEntities.CURB_PILLAR, EmptyEntityRenderer::new);
        StunOverlay.init();            // «Оглушение»: дымка (I) или сильное затемнение (II)
        SpikesOverlay.init();          // «Шипы»: вспышка осколков, когда шипы ранили игрока
        BloodPoolOverlay.init();       // кровавая лужа ранит — экран чуть краснеет
        DuelistOverlay.init();
        RatOverlay.init();

        // Эффект «Заморозка»: бело‑голубой экран
        FreezeOverlay.init();
    }
}

package com.lesha_top1972.basedefense.client;

import com.lesha_top1972.basedefense.client.entity.enemy.bloodthirster.BloodEggRenderer;
import com.lesha_top1972.basedefense.client.entity.enemy.bloodthirster.BloodlingRenderer;
import com.lesha_top1972.basedefense.client.entity.enemy.bloodthirster.BloodthirsterRenderer;
import com.lesha_top1972.basedefense.client.entity.enemy.curb.CurbCaseRenderer;
import com.lesha_top1972.basedefense.client.entity.enemy.curb.CurbMinionRenderer;
import com.lesha_top1972.basedefense.client.entity.enemy.curb.CurbRenderer;
import com.lesha_top1972.basedefense.client.entity.enemy.frost_archer.FrostArcherRenderer;
import com.lesha_top1972.basedefense.client.entity.enemy.lancer.FallingLanceRenderer;
import com.lesha_top1972.basedefense.client.entity.enemy.frost_archer.FrostArrowRenderer;
import com.lesha_top1972.basedefense.client.entity.enemy.iron_knight.IronKnightRenderer;
import com.lesha_top1972.basedefense.client.entity.enemy.lancer.TatteredLancerRenderer;
import com.lesha_top1972.basedefense.client.entity.enemy.iron_zombie.IronZombieRenderer;
import com.lesha_top1972.basedefense.client.entity.settler.SettlerEntityModel;
import com.lesha_top1972.basedefense.client.entity.bedrock.BedrockModelLoader;
import com.lesha_top1972.basedefense.client.entity.enemy.lancer.LancerFx;
import com.lesha_top1972.basedefense.client.entity.settler.SettlerEntityRenderer;
import com.lesha_top1972.basedefense.client.effect.BloodPoolOverlay;
import com.lesha_top1972.basedefense.client.entity.enemy.nabat.NabatBellRenderer;
import com.lesha_top1972.basedefense.client.entity.enemy.nabat.NabatBladeRenderer;
import com.lesha_top1972.basedefense.client.entity.enemy.nabat.NabatSpearRenderer;
import com.lesha_top1972.basedefense.client.entity.enemy.nabat.NabatFallingBellRenderer;
import com.lesha_top1972.basedefense.client.entity.enemy.nabat.NabatRenderer;
import com.lesha_top1972.basedefense.client.entity.enemy.nabat.NabatSoulRenderer;
import com.lesha_top1972.basedefense.client.effect.SpikesOverlay;
import com.lesha_top1972.basedefense.client.effect.BossEffectsHud;
import com.lesha_top1972.basedefense.client.effect.StunOverlay;
import net.minecraft.client.render.entity.EmptyEntityRenderer;
import com.lesha_top1972.basedefense.client.effect.DuelistOverlay;
import com.lesha_top1972.basedefense.client.effect.FreezeOverlay;
import com.lesha_top1972.basedefense.client.entity.enemy.lancer.LancerZoneRenderer;
import com.lesha_top1972.basedefense.client.effect.RatOverlay;
import com.lesha_top1972.basedefense.entity.ModEntities;
import com.lesha_top1972.basedefense.screen.ModScreenHandlers;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.rendering.v1.EntityModelLayerRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.EntityRendererRegistry;
import net.minecraft.client.gui.screen.ingame.HandledScreens;

public class BasedefenseClient implements ClientModInitializer {

    @Override
    public void onInitializeClient() {
        // Меню: поселенец, Повестка, стол амулетов
        com.lesha_top1972.basedefense.client.clothing.ClothingClient.init();   // кнопка «одежда/броня» в инвентаре
        com.lesha_top1972.basedefense.client.season.SeasonClient.init();       // сезон, цвет листвы и травы
        com.lesha_top1972.basedefense.client.temperature.TemperatureHud.init(); // значок температуры у еды
        HandledScreens.register(ModScreenHandlers.GUARD, com.lesha_top1972.basedefense.client.screen.GuardScreen::new);
        HandledScreens.register(ModScreenHandlers.SQUAD, com.lesha_top1972.basedefense.client.screen.SquadScreen::new);
        HandledScreens.register(ModScreenHandlers.AMULET_TABLE,
                com.lesha_top1972.basedefense.client.screen.AmuletTableScreen::new);

        // Поселенцы: случайная внешность, одна модель
        EntityModelLayerRegistry.registerModelLayer(SettlerEntityRenderer.LAYER, SettlerEntityModel::getTexturedModelData);
        EntityRendererRegistry.register(ModEntities.GUARD, com.lesha_top1972.basedefense.client.entity.settler.GuardEntityRenderer::new);
        EntityRendererRegistry.register(ModEntities.GUARD_ARROW, net.minecraft.client.render.entity.ArrowEntityRenderer::new);

        // Враги: модели и анимации из Blockbench (assets/basedefense/geo и animations)
        EntityModelLayerRegistry.registerModelLayer(IronZombieRenderer.LAYER, () -> BedrockModelLoader.load("iron_zombie"));
        EntityModelLayerRegistry.registerModelLayer(IronKnightRenderer.LAYER, () -> BedrockModelLoader.load("iron_knight"));
        EntityRendererRegistry.register(ModEntities.IRON_ZOMBIE, IronZombieRenderer::new);
        EntityRendererRegistry.register(ModEntities.IRON_KNIGHT, IronKnightRenderer::new);
        EntityModelLayerRegistry.registerModelLayer(FrostArcherRenderer.LAYER, () -> BedrockModelLoader.load("frost_archer"));
        EntityModelLayerRegistry.registerModelLayer(FrostArcherRenderer.BOW, () -> BedrockModelLoader.load("frost_archer_bow"));
        EntityRendererRegistry.register(ModEntities.FROST_ARCHER, FrostArcherRenderer::new);
        EntityRendererRegistry.register(ModEntities.FROST_ARROW, FrostArrowRenderer::new);
        EntityModelLayerRegistry.registerModelLayer(TatteredLancerRenderer.LAYER, () -> BedrockModelLoader.load("tattered_lancer"));
        EntityModelLayerRegistry.registerModelLayer(TatteredLancerRenderer.LANCE, () -> BedrockModelLoader.load("tattered_lancer_lance"));
        EntityRendererRegistry.register(ModEntities.TATTERED_LANCER, TatteredLancerRenderer::new);
        EntityRendererRegistry.register(ModEntities.FALLING_LANCE, FallingLanceRenderer::new);
        LancerZoneRenderer.init();
        LancerFx.init();               // эффекты «ливня копий» улана
        // Босс «Кровожад», мелкие кровожады и яйца
        EntityModelLayerRegistry.registerModelLayer(BloodthirsterRenderer.LAYER, () -> BedrockModelLoader.load("bloodthirster"));
        EntityModelLayerRegistry.registerModelLayer(BloodthirsterRenderer.SWORD, () -> BedrockModelLoader.load("bloodthirster_sword"));
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
        EntityRendererRegistry.register(ModEntities.CURB_CASE, CurbCaseRenderer::new);
        // Босс «Набат», его мечники, колокола, якорь улана, души (оружие — отдельные модели)
        EntityModelLayerRegistry.registerModelLayer(NabatRenderer.LAYER, () -> BedrockModelLoader.load("nabat"));
        EntityModelLayerRegistry.registerModelLayer(NabatRenderer.MAUL, () -> BedrockModelLoader.load("nabat_maul"));
        EntityModelLayerRegistry.registerModelLayer(NabatBladeRenderer.LAYER, () -> BedrockModelLoader.load("nabat_blade"));
        EntityModelLayerRegistry.registerModelLayer(NabatBladeRenderer.SWORD, () -> BedrockModelLoader.load("nabat_sword"));
        EntityModelLayerRegistry.registerModelLayer(NabatSpearRenderer.LAYER, () -> BedrockModelLoader.load("nabat_spear"));
        EntityModelLayerRegistry.registerModelLayer(NabatBellRenderer.LAYER, () -> BedrockModelLoader.load("nabat_bell"));
        EntityRendererRegistry.register(ModEntities.NABAT, NabatRenderer::new);
        EntityRendererRegistry.register(ModEntities.NABAT_BLADE, NabatBladeRenderer::new);
        EntityRendererRegistry.register(ModEntities.NABAT_BELL, NabatBellRenderer::new);
        EntityRendererRegistry.register(ModEntities.NABAT_SPEAR, NabatSpearRenderer::new);
        EntityRendererRegistry.register(ModEntities.NABAT_FALLING_BELL, NabatFallingBellRenderer::new);
        EntityRendererRegistry.register(ModEntities.NABAT_SOUL, NabatSoulRenderer::new);
        EntityRendererRegistry.register(ModEntities.NABAT_QUAKE, EmptyEntityRenderer::new);
        // подкопщик
        EntityModelLayerRegistry.registerModelLayer(com.lesha_top1972.basedefense.client.entity.enemy.sapper.SapperRenderer.LAYER, () -> BedrockModelLoader.load("sapper"));
        EntityModelLayerRegistry.registerModelLayer(com.lesha_top1972.basedefense.client.entity.enemy.sapper.SapperRenderer.PICK, () -> BedrockModelLoader.load("sapper_pick"));
        EntityModelLayerRegistry.registerModelLayer(com.lesha_top1972.basedefense.client.entity.enemy.sapper.SapperPropRenderer.BOMB, () -> BedrockModelLoader.load("sapper_bomb"));
        EntityModelLayerRegistry.registerModelLayer(com.lesha_top1972.basedefense.client.entity.enemy.sapper.SapperPropRenderer.CHARGE, () -> BedrockModelLoader.load("sapper_charge"));
        EntityModelLayerRegistry.registerModelLayer(com.lesha_top1972.basedefense.client.entity.enemy.sapper.SapperPropRenderer.SMOKE, () -> BedrockModelLoader.load("sapper_smoke"));
        EntityRendererRegistry.register(ModEntities.SAPPER, com.lesha_top1972.basedefense.client.entity.enemy.sapper.SapperRenderer::new);
        EntityRendererRegistry.register(ModEntities.SAPPER_BOMB, com.lesha_top1972.basedefense.client.entity.enemy.sapper.SapperPropRenderer.bomb());
        EntityRendererRegistry.register(ModEntities.SAPPER_CHARGE, com.lesha_top1972.basedefense.client.entity.enemy.sapper.SapperPropRenderer.charge());
        EntityRendererRegistry.register(ModEntities.SAPPER_SMOKE, com.lesha_top1972.basedefense.client.entity.enemy.sapper.SapperPropRenderer.smoke());
        // босс «Громовой тигр»: модель автора мода, тесак отдельной моделью; тряска камеры от его ударов
        EntityModelLayerRegistry.registerModelLayer(com.lesha_top1972.basedefense.client.entity.enemy.thunder_tiger.ThunderTigerRenderer.LAYER, () -> BedrockModelLoader.load("thunder_tiger"));
        EntityModelLayerRegistry.registerModelLayer(com.lesha_top1972.basedefense.client.entity.enemy.thunder_tiger.ThunderTigerRenderer.BLADE, () -> BedrockModelLoader.load("thunder_tiger_blade"));
        EntityRendererRegistry.register(ModEntities.THUNDER_TIGER, com.lesha_top1972.basedefense.client.entity.enemy.thunder_tiger.ThunderTigerRenderer::new);
        com.lesha_top1972.basedefense.client.fx.CameraShake.init();
        com.lesha_top1972.basedefense.client.entity.enemy.thunder_tiger.TigerMusic.init();   // музыка боя
        // осадный жук, маленький жук, яйцо
        EntityModelLayerRegistry.registerModelLayer(com.lesha_top1972.basedefense.client.entity.enemy.beetle.SiegeBeetleRenderer.LAYER, () -> BedrockModelLoader.load("siege_beetle"));
        EntityModelLayerRegistry.registerModelLayer(com.lesha_top1972.basedefense.client.entity.enemy.beetle.BeetleMiteRenderer.LAYER, () -> BedrockModelLoader.load("beetle_mite"));
        EntityModelLayerRegistry.registerModelLayer(com.lesha_top1972.basedefense.client.entity.enemy.beetle.BeetleEggRenderer.LAYER, () -> BedrockModelLoader.load("beetle_egg"));
        EntityRendererRegistry.register(ModEntities.SIEGE_BEETLE, com.lesha_top1972.basedefense.client.entity.enemy.beetle.SiegeBeetleRenderer::new);
        EntityRendererRegistry.register(ModEntities.BEETLE_MITE, com.lesha_top1972.basedefense.client.entity.enemy.beetle.BeetleMiteRenderer::new);
        EntityRendererRegistry.register(ModEntities.BEETLE_EGG, com.lesha_top1972.basedefense.client.entity.enemy.beetle.BeetleEggRenderer::new);
        // туманная и кровавая сущности
        EntityModelLayerRegistry.registerModelLayer(com.lesha_top1972.basedefense.client.entity.enemy.horror.MistEntityRenderer.LAYER, () -> BedrockModelLoader.load("mist_entity"));
        EntityModelLayerRegistry.registerModelLayer(com.lesha_top1972.basedefense.client.entity.enemy.horror.BloodEntityRenderer.LAYER, () -> BedrockModelLoader.load("blood_entity"));
        EntityRendererRegistry.register(ModEntities.MIST_ENTITY, com.lesha_top1972.basedefense.client.entity.enemy.horror.MistEntityRenderer::new);
        EntityRendererRegistry.register(ModEntities.BLOOD_ENTITY, com.lesha_top1972.basedefense.client.entity.enemy.horror.BloodEntityRenderer::new);
        // кровопускатель и его кровавые копья
        EntityModelLayerRegistry.registerModelLayer(com.lesha_top1972.basedefense.client.entity.enemy.bloodletter.BloodletterRenderer.LAYER, () -> BedrockModelLoader.load("bloodletter"));
        EntityModelLayerRegistry.registerModelLayer(com.lesha_top1972.basedefense.client.entity.enemy.bloodletter.BloodSpearRenderer.LAYER, () -> BedrockModelLoader.load("bloodletter_spear"));
        EntityRendererRegistry.register(ModEntities.BLOODLETTER, com.lesha_top1972.basedefense.client.entity.enemy.bloodletter.BloodletterRenderer::new);
        EntityRendererRegistry.register(ModEntities.BLOOD_SPEAR, com.lesha_top1972.basedefense.client.entity.enemy.bloodletter.BloodSpearRenderer::new);
        // меч кровожада: модель в руке, замах и удар (1‑е и 3‑е лицо), частицы
        EntityModelLayerRegistry.registerModelLayer(com.lesha_top1972.basedefense.client.weapon.BloodSwordRender.LAYER,
                () -> BedrockModelLoader.load("blood_greatsword"));
        com.lesha_top1972.basedefense.client.weapon.BloodSwordClient.init();
        // катана: модель и ножны, серия из трёх ударов
        com.lesha_top1972.basedefense.client.weapon.KatanaClient.init();
        // артефакты: вкладка с 5 слотами в инвентаре, рывок на R
        com.lesha_top1972.basedefense.client.artifact.ArtifactClient.init();
        com.lesha_top1972.basedefense.client.fx.FxClient.init();   // кольца, лучи, линии — одним пакетом
        BossEffectsHud.init();         // эффекты боссов под их полоской здоровья
        com.lesha_top1972.basedefense.client.director.DirectorClient.init();   // «Режиссёр мира»: вкладка «Летопись», утренняя заставка
        StunOverlay.init();            // «Оглушение»: дымка (I) или сильное затемнение (II)
        SpikesOverlay.init();          // «Шипы»: вспышка осколков, когда шипы ранили игрока
        BloodPoolOverlay.init();       // кровавая лужа ранит — экран чуть краснеет
        DuelistOverlay.init();
        RatOverlay.init();

        // Эффект «Заморозка»: бело‑голубой экран
        FreezeOverlay.init();
    }
}

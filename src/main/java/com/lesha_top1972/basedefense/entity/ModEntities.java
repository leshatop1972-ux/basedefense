package com.lesha_top1972.basedefense.entity;

import com.lesha_top1972.basedefense.entity.enemy.beetle.BeetleEggEntity;
import com.lesha_top1972.basedefense.entity.enemy.beetle.BeetleMiteEntity;
import com.lesha_top1972.basedefense.entity.enemy.beetle.SiegeBeetleEntity;
import com.lesha_top1972.basedefense.entity.enemy.bloodletter.BloodSpearEntity;
import com.lesha_top1972.basedefense.entity.enemy.bloodletter.BloodletterEntity;
import com.lesha_top1972.basedefense.entity.enemy.bloodthirster.BloodEggEntity;
import com.lesha_top1972.basedefense.entity.enemy.bloodthirster.BloodlingEntity;
import com.lesha_top1972.basedefense.entity.enemy.bloodthirster.BloodthirsterEntity;
import com.lesha_top1972.basedefense.entity.enemy.curb.CurbCaseEntity;
import com.lesha_top1972.basedefense.entity.enemy.curb.CurbEntity;
import com.lesha_top1972.basedefense.entity.enemy.curb.CurbMinionEntity;
import com.lesha_top1972.basedefense.entity.enemy.curb.CurbPillarEntity;
import com.lesha_top1972.basedefense.entity.enemy.frost_archer.FrostArcherEntity;
import com.lesha_top1972.basedefense.entity.enemy.frost_archer.FrostArrowEntity;
import com.lesha_top1972.basedefense.entity.enemy.horror.BloodEntity;
import com.lesha_top1972.basedefense.entity.enemy.horror.MistEntity;
import com.lesha_top1972.basedefense.entity.enemy.iron_knight.IronKnightEntity;
import com.lesha_top1972.basedefense.entity.enemy.iron_zombie.IronZombieEntity;
import com.lesha_top1972.basedefense.entity.enemy.lancer.FallingLanceEntity;
import com.lesha_top1972.basedefense.entity.enemy.lancer.TatteredLancerEntity;
import com.lesha_top1972.basedefense.entity.enemy.nabat.NabatBellEntity;
import com.lesha_top1972.basedefense.entity.enemy.nabat.NabatBladeEntity;
import com.lesha_top1972.basedefense.entity.enemy.nabat.NabatEntity;
import com.lesha_top1972.basedefense.entity.enemy.nabat.NabatFallingBellEntity;
import com.lesha_top1972.basedefense.entity.enemy.nabat.NabatQuakeEntity;
import com.lesha_top1972.basedefense.entity.enemy.nabat.NabatSoulEntity;
import com.lesha_top1972.basedefense.entity.enemy.nabat.NabatSpearEntity;
import com.lesha_top1972.basedefense.entity.enemy.sapper.SapperBombEntity;
import com.lesha_top1972.basedefense.entity.enemy.sapper.SapperChargeEntity;
import com.lesha_top1972.basedefense.entity.enemy.sapper.SapperEntity;
import com.lesha_top1972.basedefense.entity.enemy.sapper.SapperSmokeEntity;
import com.lesha_top1972.basedefense.entity.settler.BlacksmithEntity;
import com.lesha_top1972.basedefense.entity.settler.BuilderEntity;
import com.lesha_top1972.basedefense.entity.settler.CookEntity;
import com.lesha_top1972.basedefense.entity.settler.CourierEntity;
import com.lesha_top1972.basedefense.entity.settler.GuardArrowEntity;
import com.lesha_top1972.basedefense.entity.settler.GuardEntity;
import com.lesha_top1972.basedefense.entity.settler.FarmerEntity;
import com.lesha_top1972.basedefense.entity.settler.LumberjackEntity;
import com.lesha_top1972.basedefense.entity.settler.MinerEntity;
import com.lesha_top1972.basedefense.entity.settler.RancherEntity;
import com.lesha_top1972.basedefense.entity.settler.SettlerEntity;
import com.lesha_top1972.basedefense.Basedefense;
import net.fabricmc.fabric.api.object.builder.v1.entity.FabricDefaultAttributeRegistry;
import net.fabricmc.fabric.api.object.builder.v1.entity.FabricEntityTypeBuilder;
import net.minecraft.entity.EntityDimensions;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.SpawnGroup;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.util.Identifier;

/** Все мобы мода. */
public final class ModEntities {

    public static final EntityType<BuilderEntity> BUILDER = Registry.register(
            Registries.ENTITY_TYPE,
            new Identifier(Basedefense.MOD_ID, "builder"),
            FabricEntityTypeBuilder.create(SpawnGroup.MISC, BuilderEntity::new)
                    .dimensions(EntityDimensions.fixed(0.6f, 1.95f))
                    .build());

    public static final EntityType<FarmerEntity> FARMER = Registry.register(
            Registries.ENTITY_TYPE,
            new Identifier(Basedefense.MOD_ID, "farmer"),
            FabricEntityTypeBuilder.create(SpawnGroup.MISC, FarmerEntity::new)
                    .dimensions(EntityDimensions.fixed(0.6f, 1.95f))
                    .build());

    public static final EntityType<RancherEntity> RANCHER = Registry.register(
            Registries.ENTITY_TYPE,
            new Identifier(Basedefense.MOD_ID, "rancher"),
            FabricEntityTypeBuilder.create(SpawnGroup.MISC, RancherEntity::new)
                    .dimensions(EntityDimensions.fixed(0.6f, 1.95f))
                    .build());

    public static final EntityType<CookEntity> COOK = Registry.register(
            Registries.ENTITY_TYPE,
            new Identifier(Basedefense.MOD_ID, "cook"),
            FabricEntityTypeBuilder.create(SpawnGroup.MISC, CookEntity::new)
                    .dimensions(EntityDimensions.fixed(0.6f, 1.95f))
                    .build());

    public static final EntityType<MinerEntity> MINER = Registry.register(
            Registries.ENTITY_TYPE,
            new Identifier(Basedefense.MOD_ID, "miner"),
            FabricEntityTypeBuilder.create(SpawnGroup.MISC, MinerEntity::new)
                    .dimensions(EntityDimensions.fixed(0.6f, 1.95f))
                    .build());

    public static final EntityType<BlacksmithEntity> BLACKSMITH = Registry.register(
            Registries.ENTITY_TYPE,
            new Identifier(Basedefense.MOD_ID, "blacksmith"),
            FabricEntityTypeBuilder.create(SpawnGroup.MISC, BlacksmithEntity::new)
                    .dimensions(EntityDimensions.fixed(0.6f, 1.95f))
                    .build());

    public static final EntityType<LumberjackEntity> LUMBERJACK = Registry.register(
            Registries.ENTITY_TYPE,
            new Identifier(Basedefense.MOD_ID, "lumberjack"),
            FabricEntityTypeBuilder.create(SpawnGroup.MISC, LumberjackEntity::new)
                    .dimensions(EntityDimensions.fixed(0.6f, 1.95f))
                    .build());

    /** Курьер (живёт в здании Базы, разносит ресурсы). */
    public static final EntityType<CourierEntity> COURIER = Registry.register(
            Registries.ENTITY_TYPE,
            new Identifier(Basedefense.MOD_ID, "courier"),
            FabricEntityTypeBuilder.create(SpawnGroup.MISC, CourierEntity::new)
                    .dimensions(EntityDimensions.fixed(0.6f, 1.95f))
                    .build());

    /** Стражник (военный поселенец; вербуется в военкомате казармы). */
    public static final EntityType<GuardEntity> GUARD = Registry.register(
            Registries.ENTITY_TYPE,
            new Identifier(Basedefense.MOD_ID, "guard"),
            FabricEntityTypeBuilder.create(SpawnGroup.MISC, GuardEntity::new)
                    .dimensions(EntityDimensions.fixed(0.6f, 1.95f))
                    .build());

    /** Стрела стражника (пролетает сквозь жителей и игроков). */
    public static final EntityType<GuardArrowEntity> GUARD_ARROW = Registry.register(
            Registries.ENTITY_TYPE,
            new Identifier(Basedefense.MOD_ID, "guard_arrow"),
            FabricEntityTypeBuilder.<GuardArrowEntity>create(SpawnGroup.MISC, GuardArrowEntity::new)
                    .dimensions(EntityDimensions.fixed(0.5f, 0.5f))
                    .trackRangeBlocks(64).trackedUpdateRate(1).forceTrackedVelocityUpdates(true)
                    .build());

    /** Железный зомби (враг). */
    public static final EntityType<IronZombieEntity> IRON_ZOMBIE = Registry.register(
            Registries.ENTITY_TYPE,
            new Identifier(Basedefense.MOD_ID, "iron_zombie"),
            FabricEntityTypeBuilder.create(SpawnGroup.MONSTER, IronZombieEntity::new)
                    .dimensions(EntityDimensions.fixed(0.7f, 2.1f))
                    .build());

    /** Железный рыцарь (враг). */
    public static final EntityType<IronKnightEntity> IRON_KNIGHT = Registry.register(
            Registries.ENTITY_TYPE,
            new Identifier(Basedefense.MOD_ID, "iron_knight"),
            FabricEntityTypeBuilder.create(SpawnGroup.MONSTER, IronKnightEntity::new)
                    .dimensions(EntityDimensions.fixed(0.8f, 2.3f))
                    .build());

    /** Морозная лучница (враг). Хитбокс от земли до макушки (модель парит на 0.45 блока). */
    public static final EntityType<FrostArcherEntity> FROST_ARCHER = Registry.register(
            Registries.ENTITY_TYPE,
            new Identifier(Basedefense.MOD_ID, "frost_archer"),
            FabricEntityTypeBuilder.create(SpawnGroup.MONSTER, FrostArcherEntity::new)
                    .dimensions(EntityDimensions.fixed(0.7f, 2.4f))
                    .build());

    /** Ледяная стрела лучницы. */
    public static final EntityType<FrostArrowEntity> FROST_ARROW = Registry.register(
            Registries.ENTITY_TYPE,
            new Identifier(Basedefense.MOD_ID, "frost_arrow"),
            FabricEntityTypeBuilder.<FrostArrowEntity>create(SpawnGroup.MISC, FrostArrowEntity::new)
                    .dimensions(EntityDimensions.fixed(0.3f, 0.3f))
                    .trackRangeBlocks(64)
                    .trackedUpdateRate(2)
                    .build());

    /** Босс «Оборванный улан». */
    public static final EntityType<TatteredLancerEntity> TATTERED_LANCER = Registry.register(
            Registries.ENTITY_TYPE,
            new Identifier(Basedefense.MOD_ID, "tattered_lancer"),
            FabricEntityTypeBuilder.create(SpawnGroup.MONSTER, TatteredLancerEntity::new)
                    .dimensions(EntityDimensions.fixed(0.7f, 2.2f))
                    .trackRangeBlocks(80)
                    .build());

    /** Призрачное копьё «ливня копий». */
    public static final EntityType<FallingLanceEntity> FALLING_LANCE = Registry.register(
            Registries.ENTITY_TYPE,
            new Identifier(Basedefense.MOD_ID, "falling_lance"),
            FabricEntityTypeBuilder.<FallingLanceEntity>create(SpawnGroup.MISC, FallingLanceEntity::new)
                    .dimensions(EntityDimensions.fixed(0.4f, 0.4f))
                    .trackRangeBlocks(80)
                    .trackedUpdateRate(2)
                    .build());

    /** Босс «Кровожад» (огромный: модель ×1.3). */
    public static final EntityType<BloodthirsterEntity> BLOODTHIRSTER = Registry.register(
            Registries.ENTITY_TYPE,
            new Identifier(Basedefense.MOD_ID, "bloodthirster"),
            FabricEntityTypeBuilder.create(SpawnGroup.MONSTER, BloodthirsterEntity::new)
                    .dimensions(EntityDimensions.fixed(1.6f, 4.0f))
                    .trackRangeBlocks(80)
                    .build());

    /** Мелкий кровожад (выводок босса). */
    public static final EntityType<BloodlingEntity> BLOODLING = Registry.register(
            Registries.ENTITY_TYPE,
            new Identifier(Basedefense.MOD_ID, "bloodling"),
            FabricEntityTypeBuilder.create(SpawnGroup.MONSTER, BloodlingEntity::new)
                    .dimensions(EntityDimensions.fixed(0.7f, 1.2f))
                    .build());

    /** Яйцо кровожада (растит кровавую лужу). */
    public static final EntityType<BloodEggEntity> BLOOD_EGG = Registry.register(
            Registries.ENTITY_TYPE,
            new Identifier(Basedefense.MOD_ID, "blood_egg"),
            FabricEntityTypeBuilder.<BloodEggEntity>create(SpawnGroup.MISC, BloodEggEntity::new)
                    .dimensions(EntityDimensions.fixed(1.0f, 1.4f))
                    .build());

    /** Босс «Боря228» (бывший «Бордюр», модель ×1.12). */
    public static final EntityType<CurbEntity> CURB = Registry.register(
            Registries.ENTITY_TYPE,
            new Identifier(Basedefense.MOD_ID, "curb"),
            FabricEntityTypeBuilder.create(SpawnGroup.MONSTER, CurbEntity::new)
                    .dimensions(EntityDimensions.fixed(0.8f, 2.4f))
                    .trackRangeBlocks(80)
                    .build());

    /** Прислужник Бордюра. */
    public static final EntityType<CurbMinionEntity> CURB_MINION = Registry.register(
            Registries.ENTITY_TYPE,
            new Identifier(Basedefense.MOD_ID, "curb_minion"),
            FabricEntityTypeBuilder.create(SpawnGroup.MONSTER, CurbMinionEntity::new)
                    .dimensions(EntityDimensions.fixed(0.7f, 2.15f))
                    .trackRangeBlocks(64)
                    .build());

    /** Каменный столб Бордюра (невидимый, управляет блоками столба). */
    public static final EntityType<CurbPillarEntity> CURB_PILLAR = Registry.register(
            Registries.ENTITY_TYPE,
            new Identifier(Basedefense.MOD_ID, "curb_pillar"),
            FabricEntityTypeBuilder.<CurbPillarEntity>create(SpawnGroup.MISC, CurbPillarEntity::new)
                    .dimensions(EntityDimensions.fixed(0.5f, 0.5f))
                    .trackRangeBlocks(32)
                    .build());

    /** Чёрный чемодан, вылетающий из‑за спины Бори228 после «Я не сдамся» (украшение). */
    public static final EntityType<CurbCaseEntity> CURB_CASE = Registry.register(
            Registries.ENTITY_TYPE,
            new Identifier(Basedefense.MOD_ID, "curb_case"),
            FabricEntityTypeBuilder.<CurbCaseEntity>create(SpawnGroup.MISC, CurbCaseEntity::new)
                    .dimensions(EntityDimensions.fixed(0.6f, 0.3f))
                    .trackRangeBlocks(48)
                    .trackedUpdateRate(2)
                    .forceTrackedVelocityUpdates(true)
                    .build());

    /** Босс «Набат» (модель ×1.6, хитбокс без крыльев). */
    public static final EntityType<NabatEntity> NABAT = Registry.register(
            Registries.ENTITY_TYPE,
            new Identifier(Basedefense.MOD_ID, "nabat"),
            FabricEntityTypeBuilder.create(SpawnGroup.MONSTER, NabatEntity::new)
                    .dimensions(EntityDimensions.fixed(2.6f, 7.0f))
                    .trackRangeBlocks(128)
                    .trackedUpdateRate(1)
                    .fireImmune()
                    .build());

    /** Мечник Набата «Отзвук». */
    public static final EntityType<NabatBladeEntity> NABAT_BLADE = Registry.register(
            Registries.ENTITY_TYPE,
            new Identifier(Basedefense.MOD_ID, "nabat_blade"),
            FabricEntityTypeBuilder.create(SpawnGroup.MONSTER, NabatBladeEntity::new)
                    .dimensions(EntityDimensions.fixed(0.7f, 2.5f))
                    .trackRangeBlocks(64)
                    .build());

    /** Колокол‑опора Набата (держит «Купол»). */
    public static final EntityType<NabatBellEntity> NABAT_BELL = Registry.register(
            Registries.ENTITY_TYPE,
            new Identifier(Basedefense.MOD_ID, "nabat_bell"),
            FabricEntityTypeBuilder.<NabatBellEntity>create(SpawnGroup.MISC, NabatBellEntity::new)
                    .dimensions(EntityDimensions.fixed(1.3f, 1.7f))
                    .trackRangeBlocks(96)
                    .fireImmune()
                    .build());

    /** Золотое копьё «Копий небес» Набата. */
    public static final EntityType<NabatSpearEntity> NABAT_SPEAR = Registry.register(
            Registries.ENTITY_TYPE,
            new Identifier(Basedefense.MOD_ID, "nabat_spear"),
            FabricEntityTypeBuilder.<NabatSpearEntity>create(SpawnGroup.MISC, NabatSpearEntity::new)
                    .dimensions(EntityDimensions.fixed(0.5f, 0.5f))
                    .trackRangeBlocks(96)
                    .trackedUpdateRate(1)
                    .build());

    /** Падающий колокол («Метка набата», «Колокольный дождь»). */
    public static final EntityType<NabatFallingBellEntity> NABAT_FALLING_BELL = Registry.register(
            Registries.ENTITY_TYPE,
            new Identifier(Basedefense.MOD_ID, "nabat_falling_bell"),
            FabricEntityTypeBuilder.<NabatFallingBellEntity>create(SpawnGroup.MISC, NabatFallingBellEntity::new)
                    .dimensions(EntityDimensions.fixed(1.0f, 1.2f))
                    .trackRangeBlocks(96)
                    .trackedUpdateRate(2)
                    .build());

    /** Душа «Реквиема». */
    public static final EntityType<NabatSoulEntity> NABAT_SOUL = Registry.register(
            Registries.ENTITY_TYPE,
            new Identifier(Basedefense.MOD_ID, "nabat_soul"),
            FabricEntityTypeBuilder.<NabatSoulEntity>create(SpawnGroup.MISC, NabatSoulEntity::new)
                    .dimensions(EntityDimensions.fixed(0.6f, 0.6f))
                    .trackRangeBlocks(96)
                    .trackedUpdateRate(2)
                    .build());

    /** «Землетрясение» (невидимое, управляет блоками). */
    public static final EntityType<NabatQuakeEntity> NABAT_QUAKE = Registry.register(
            Registries.ENTITY_TYPE,
            new Identifier(Basedefense.MOD_ID, "nabat_quake"),
            FabricEntityTypeBuilder.<NabatQuakeEntity>create(SpawnGroup.MISC, NabatQuakeEntity::new)
                    .dimensions(EntityDimensions.fixed(0.5f, 0.5f))
                    .trackRangeBlocks(32)
                    .build());

    /** «Подкопщик» — карлик‑сапёр (модель ×0.85). */
    public static final EntityType<SapperEntity> SAPPER = Registry.register(
            Registries.ENTITY_TYPE,
            new Identifier(Basedefense.MOD_ID, "sapper"),
            FabricEntityTypeBuilder.create(SpawnGroup.MONSTER, SapperEntity::new)
                    .dimensions(EntityDimensions.fixed(0.7f, 1.3f))
                    .trackRangeBlocks(64)
                    .trackedUpdateRate(2)
                    .build());

    /** «Бомба‑банка» подкопщика. */
    public static final EntityType<SapperBombEntity> SAPPER_BOMB = Registry.register(
            Registries.ENTITY_TYPE,
            new Identifier(Basedefense.MOD_ID, "sapper_bomb"),
            FabricEntityTypeBuilder.<SapperBombEntity>create(SpawnGroup.MISC, SapperBombEntity::new)
                    .dimensions(EntityDimensions.fixed(0.3f, 0.3f))
                    .trackRangeBlocks(64)
                    .trackedUpdateRate(1)
                    .build());

    /** «Подрыв» — связка шашек на стене. */
    public static final EntityType<SapperChargeEntity> SAPPER_CHARGE = Registry.register(
            Registries.ENTITY_TYPE,
            new Identifier(Basedefense.MOD_ID, "sapper_charge"),
            FabricEntityTypeBuilder.<SapperChargeEntity>create(SpawnGroup.MISC, SapperChargeEntity::new)
                    .dimensions(EntityDimensions.fixed(0.4f, 0.5f))
                    .trackRangeBlocks(64)
                    .trackedUpdateRate(10)
                    .build());

    /** Дымовая банка. */
    public static final EntityType<SapperSmokeEntity> SAPPER_SMOKE = Registry.register(
            Registries.ENTITY_TYPE,
            new Identifier(Basedefense.MOD_ID, "sapper_smoke"),
            FabricEntityTypeBuilder.<SapperSmokeEntity>create(SpawnGroup.MISC, SapperSmokeEntity::new)
                    .dimensions(EntityDimensions.fixed(0.25f, 0.3f))
                    .trackRangeBlocks(48)
                    .trackedUpdateRate(2)
                    .build());

    /** «Осадный жук» (модель ×1.3). */
    public static final EntityType<SiegeBeetleEntity> SIEGE_BEETLE = Registry.register(
            Registries.ENTITY_TYPE,
            new Identifier(Basedefense.MOD_ID, "siege_beetle"),
            FabricEntityTypeBuilder.create(SpawnGroup.MONSTER, SiegeBeetleEntity::new)
                    .dimensions(EntityDimensions.fixed(3.0f, 2.6f))
                    .trackRangeBlocks(80)
                    .trackedUpdateRate(1)
                    .build());

    /** «Маленький жук». */
    public static final EntityType<BeetleMiteEntity> BEETLE_MITE = Registry.register(
            Registries.ENTITY_TYPE,
            new Identifier(Basedefense.MOD_ID, "beetle_mite"),
            FabricEntityTypeBuilder.create(SpawnGroup.MONSTER, BeetleMiteEntity::new)
                    .dimensions(EntityDimensions.fixed(0.9f, 0.5f))
                    .trackRangeBlocks(48)
                    .build());

    /** Яйцо осадного жука. */
    public static final EntityType<BeetleEggEntity> BEETLE_EGG = Registry.register(
            Registries.ENTITY_TYPE,
            new Identifier(Basedefense.MOD_ID, "beetle_egg"),
            FabricEntityTypeBuilder.<BeetleEggEntity>create(SpawnGroup.MISC, BeetleEggEntity::new)
                    .dimensions(EntityDimensions.fixed(0.8f, 1.0f))
                    .trackRangeBlocks(48)
                    .build());

    /** «Туманная сущность». */
    public static final EntityType<MistEntity> MIST_ENTITY = Registry.register(
            Registries.ENTITY_TYPE,
            new Identifier(Basedefense.MOD_ID, "mist_entity"),
            FabricEntityTypeBuilder.create(SpawnGroup.MONSTER, MistEntity::new)
                    .dimensions(EntityDimensions.fixed(0.6f, 1.95f))
                    .trackRangeBlocks(64)
                    .build());

    /** «Кровавая сущность». */
    public static final EntityType<BloodEntity> BLOOD_ENTITY = Registry.register(
            Registries.ENTITY_TYPE,
            new Identifier(Basedefense.MOD_ID, "blood_entity"),
            FabricEntityTypeBuilder.create(SpawnGroup.MONSTER, BloodEntity::new)
                    .dimensions(EntityDimensions.fixed(0.6f, 1.95f))
                    .trackRangeBlocks(64)
                    .build());

    /** «Кровопускатель». */
    public static final EntityType<BloodletterEntity> BLOODLETTER = Registry.register(
            Registries.ENTITY_TYPE,
            new Identifier(Basedefense.MOD_ID, "bloodletter"),
            FabricEntityTypeBuilder.create(SpawnGroup.MONSTER, BloodletterEntity::new)
                    .dimensions(EntityDimensions.fixed(0.8f, 2.5f))
                    .trackRangeBlocks(80)
                    .trackedUpdateRate(1)
                    .build());

    /** Кровавое копьё «Кровопускателя». */
    public static final EntityType<BloodSpearEntity> BLOOD_SPEAR = Registry.register(
            Registries.ENTITY_TYPE,
            new Identifier(Basedefense.MOD_ID, "blood_spear"),
            FabricEntityTypeBuilder.<BloodSpearEntity>create(SpawnGroup.MISC, BloodSpearEntity::new)
                    .dimensions(EntityDimensions.fixed(0.4f, 0.4f))
                    .trackRangeBlocks(96)
                    .trackedUpdateRate(1)
                    .forceTrackedVelocityUpdates(true)
                    .build());

    private ModEntities() {
    }

    public static void init() {
        FabricDefaultAttributeRegistry.register(BUILDER, SettlerEntity.createSettlerAttributes());
        FabricDefaultAttributeRegistry.register(FARMER, SettlerEntity.createSettlerAttributes());
        FabricDefaultAttributeRegistry.register(RANCHER, SettlerEntity.createSettlerAttributes());
        FabricDefaultAttributeRegistry.register(COOK, SettlerEntity.createSettlerAttributes());
        FabricDefaultAttributeRegistry.register(MINER, SettlerEntity.createSettlerAttributes());
        FabricDefaultAttributeRegistry.register(BLACKSMITH, SettlerEntity.createSettlerAttributes());
        FabricDefaultAttributeRegistry.register(LUMBERJACK, SettlerEntity.createSettlerAttributes());
        FabricDefaultAttributeRegistry.register(COURIER, SettlerEntity.createSettlerAttributes());
        FabricDefaultAttributeRegistry.register(GUARD, GuardEntity.createGuardAttributes());
        FabricDefaultAttributeRegistry.register(IRON_ZOMBIE, IronZombieEntity.createAttributes());
        FabricDefaultAttributeRegistry.register(IRON_KNIGHT, IronKnightEntity.createAttributes());
        FabricDefaultAttributeRegistry.register(FROST_ARCHER, FrostArcherEntity.createAttributes());
        FabricDefaultAttributeRegistry.register(TATTERED_LANCER, TatteredLancerEntity.createAttributes());
        FabricDefaultAttributeRegistry.register(BLOODTHIRSTER, BloodthirsterEntity.createAttributes());
        FabricDefaultAttributeRegistry.register(BLOODLING, BloodlingEntity.createAttributes());
        FabricDefaultAttributeRegistry.register(BLOOD_EGG, BloodEggEntity.createAttributes());
        FabricDefaultAttributeRegistry.register(CURB, CurbEntity.createAttributes());
        FabricDefaultAttributeRegistry.register(CURB_MINION, CurbMinionEntity.createAttributes());
        FabricDefaultAttributeRegistry.register(NABAT, NabatEntity.createAttributes());
        FabricDefaultAttributeRegistry.register(NABAT_BLADE, NabatBladeEntity.createAttributes());
        FabricDefaultAttributeRegistry.register(SAPPER, SapperEntity.createAttributes());
        FabricDefaultAttributeRegistry.register(SIEGE_BEETLE, SiegeBeetleEntity.createAttributes());
        FabricDefaultAttributeRegistry.register(MIST_ENTITY, MistEntity.createAttributes());
        FabricDefaultAttributeRegistry.register(BLOOD_ENTITY, BloodEntity.createAttributes());
        FabricDefaultAttributeRegistry.register(BEETLE_MITE, BeetleMiteEntity.createAttributes());
        FabricDefaultAttributeRegistry.register(BEETLE_EGG, BeetleEggEntity.createAttributes());
        FabricDefaultAttributeRegistry.register(NABAT_BELL, NabatBellEntity.createAttributes());
        FabricDefaultAttributeRegistry.register(BLOODLETTER, BloodletterEntity.createAttributes());
    }
}

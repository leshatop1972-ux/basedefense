package com.lesha_top1972.basedefense.entity;

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
        FabricDefaultAttributeRegistry.register(IRON_ZOMBIE, IronZombieEntity.createAttributes());
        FabricDefaultAttributeRegistry.register(IRON_KNIGHT, IronKnightEntity.createAttributes());
        FabricDefaultAttributeRegistry.register(FROST_ARCHER, FrostArcherEntity.createAttributes());
    }
}

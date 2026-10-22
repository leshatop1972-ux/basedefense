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

    private ModEntities() {
    }

    public static void init() {
        FabricDefaultAttributeRegistry.register(FARMER, SettlerEntity.createSettlerAttributes());
        FabricDefaultAttributeRegistry.register(RANCHER, SettlerEntity.createSettlerAttributes());
    }
}

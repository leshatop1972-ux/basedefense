package com.lesha_top1972.basedefense.client.entity.settler;

import com.lesha_top1972.basedefense.entity.settler.GuardEntity;
import com.lesha_top1972.basedefense.entity.settler.SettlerEntity;
import net.minecraft.client.render.OverlayTexture;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.entity.feature.FeatureRenderer;
import net.minecraft.client.render.entity.feature.FeatureRendererContext;
import net.minecraft.client.render.entity.model.BipedEntityModel;
import net.minecraft.client.render.entity.model.EntityModelLoader;
import net.minecraft.client.render.entity.model.EntityModelLayers;
import net.minecraft.client.render.item.ItemRenderer;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.item.ArmorItem;
import net.minecraft.item.DyeableArmorItem;
import net.minecraft.item.ItemStack;
import net.minecraft.util.Identifier;

import java.util.HashMap;
import java.util.Map;

/**
 * Броня на стражнике. Модель жителя — не «двуногая» модель игрока, поэтому ванильный слой брони
 * не подходит: берём модели брони игрока и копируем в них положение головы, тела, рук и ног жителя
 * (с анимациями ударов броня двигается вместе с ним). Кожаная броня красится, зачарованная — блестит.
 */
public class GuardArmorFeature extends FeatureRenderer<SettlerEntity, SettlerEntityModel> {

    private static final Map<String, Identifier> TEXTURES = new HashMap<>();

    private final BipedEntityModel<SettlerEntity> inner;
    private final BipedEntityModel<SettlerEntity> outer;

    public GuardArmorFeature(FeatureRendererContext<SettlerEntity, SettlerEntityModel> context, EntityModelLoader loader) {
        super(context);
        this.inner = new BipedEntityModel<>(loader.getModelPart(EntityModelLayers.PLAYER_INNER_ARMOR));
        this.outer = new BipedEntityModel<>(loader.getModelPart(EntityModelLayers.PLAYER_OUTER_ARMOR));
    }

    @Override
    public void render(MatrixStack matrices, VertexConsumerProvider vertexConsumers, int light, SettlerEntity entity,
                       float limbAngle, float limbDistance, float tickDelta, float animationProgress,
                       float headYaw, float headPitch) {
        if (!(entity instanceof GuardEntity)) {
            return;
        }
        renderPiece(matrices, vertexConsumers, entity, EquipmentSlot.CHEST, light, outer);
        renderPiece(matrices, vertexConsumers, entity, EquipmentSlot.LEGS, light, inner);
        renderPiece(matrices, vertexConsumers, entity, EquipmentSlot.FEET, light, outer);
        renderPiece(matrices, vertexConsumers, entity, EquipmentSlot.HEAD, light, outer);
    }

    private void renderPiece(MatrixStack matrices, VertexConsumerProvider vertexConsumers, SettlerEntity entity,
                             EquipmentSlot slot, int light, BipedEntityModel<SettlerEntity> model) {
        ItemStack stack = entity.getEquippedStack(slot);
        if (!(stack.getItem() instanceof ArmorItem armor) || armor.getSlotType() != slot) {
            return;
        }
        SettlerEntityModel src = this.getContextModel();
        // У новой модели по умолчанию child = true (детский размер: тело вдвое меньше) — отсюда
        // и «половинная» броня на животе и у ног. Ванильный слой брони копирует это из модели игрока.
        model.child = entity.isBaby();
        model.riding = entity.hasVehicle();
        model.sneaking = false;
        model.head.copyTransform(src.head());
        model.hat.copyTransform(src.head());
        model.body.copyTransform(src.body());
        model.rightArm.copyTransform(src.rightArm());
        model.leftArm.copyTransform(src.leftArm());
        model.rightLeg.copyTransform(src.rightLeg());
        model.leftLeg.copyTransform(src.leftLeg());
        model.setVisible(false);
        switch (slot) {
            case HEAD -> {
                model.head.visible = true;
                model.hat.visible = true;
            }
            case CHEST -> {
                model.body.visible = true;
                model.rightArm.visible = true;
                model.leftArm.visible = true;
            }
            case LEGS -> {
                model.body.visible = true;
                model.rightLeg.visible = true;
                model.leftLeg.visible = true;
            }
            case FEET -> {
                model.rightLeg.visible = true;
                model.leftLeg.visible = true;
            }
            default -> {
            }
        }
        boolean legs = slot == EquipmentSlot.LEGS;
        boolean glint = stack.hasGlint();
        if (armor instanceof DyeableArmorItem dyeable) {
            int color = dyeable.getColor(stack);
            float r = (color >> 16 & 255) / 255.0f;
            float g = (color >> 8 & 255) / 255.0f;
            float b = (color & 255) / 255.0f;
            draw(matrices, vertexConsumers, light, model, glint, texture(armor, legs, null), r, g, b);
            draw(matrices, vertexConsumers, light, model, false, texture(armor, legs, "overlay"), 1, 1, 1);
        } else {
            draw(matrices, vertexConsumers, light, model, glint, texture(armor, legs, null), 1, 1, 1);
        }
    }

    private static void draw(MatrixStack matrices, VertexConsumerProvider vertexConsumers, int light,
                             BipedEntityModel<SettlerEntity> model, boolean glint, Identifier texture,
                             float r, float g, float b) {
        model.render(matrices, ItemRenderer.getArmorGlintConsumer(vertexConsumers,
                        RenderLayer.getArmorCutoutNoCull(texture), false, glint),
                light, OverlayTexture.DEFAULT_UV, r, g, b, 1.0f);
    }

    private static Identifier texture(ArmorItem item, boolean legs, String overlay) {
        String path = "textures/models/armor/" + item.getMaterial().getName() + "_layer_" + (legs ? 2 : 1)
                + (overlay == null ? "" : "_" + overlay) + ".png";
        return TEXTURES.computeIfAbsent(path, Identifier::new);
    }
}

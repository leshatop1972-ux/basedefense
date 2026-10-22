package com.lesha_top1972.basedefense.client.entity;

import com.lesha_top1972.basedefense.entity.SettlerEntity;
import net.minecraft.client.model.Dilation;
import net.minecraft.client.model.ModelData;
import net.minecraft.client.model.ModelPart;
import net.minecraft.client.model.ModelPartBuilder;
import net.minecraft.client.model.ModelPartData;
import net.minecraft.client.model.ModelTransform;
import net.minecraft.client.model.TexturedModelData;
import net.minecraft.client.render.entity.model.SinglePartEntityModel;
import net.minecraft.util.math.MathHelper;

/**
 * Модель жителя поселения: голова с носом и шляпой, туловище, руки и ноги.
 * Общая для всех жителей — отличаются только текстурой 64×64
 * (textures/entity/farmer.png, textures/entity/rancher.png).
 */
public class SettlerEntityModel extends SinglePartEntityModel<SettlerEntity> {

    private final ModelPart root;
    private final ModelPart head;
    private final ModelPart rightArm;
    private final ModelPart leftArm;
    private final ModelPart rightLeg;
    private final ModelPart leftLeg;

    public SettlerEntityModel(ModelPart root) {
        this.root = root;
        this.head = root.getChild("head");
        this.rightArm = root.getChild("right_arm");
        this.leftArm = root.getChild("left_arm");
        this.rightLeg = root.getChild("right_leg");
        this.leftLeg = root.getChild("left_leg");
    }

    public static TexturedModelData getTexturedModelData() {
        ModelData data = new ModelData();
        ModelPartData root = data.getRoot();

        ModelPartData head = root.addChild("head",
                ModelPartBuilder.create().uv(0, 0).cuboid(-4.0f, -8.0f, -4.0f, 8, 8, 8),
                ModelTransform.pivot(0.0f, 0.0f, 0.0f));
        head.addChild("nose",
                ModelPartBuilder.create().uv(56, 16).cuboid(-1.0f, -1.0f, -6.0f, 2, 4, 2),
                ModelTransform.pivot(0.0f, -2.0f, 0.0f));
        head.addChild("hat_crown",
                ModelPartBuilder.create().uv(32, 0).cuboid(-4.0f, -11.0f, -4.0f, 8, 3, 8, new Dilation(0.3f)),
                ModelTransform.NONE);
        head.addChild("hat_brim",
                ModelPartBuilder.create().uv(0, 32).cuboid(-7.0f, -8.6f, -7.0f, 14, 1, 14),
                ModelTransform.NONE);

        root.addChild("body",
                ModelPartBuilder.create().uv(16, 16).cuboid(-4.0f, 0.0f, -2.0f, 8, 12, 4),
                ModelTransform.pivot(0.0f, 0.0f, 0.0f));
        root.addChild("right_arm",
                ModelPartBuilder.create().uv(40, 16).cuboid(-3.0f, -2.0f, -2.0f, 4, 12, 4),
                ModelTransform.pivot(-5.0f, 2.0f, 0.0f));
        root.addChild("left_arm",
                ModelPartBuilder.create().uv(40, 16).mirrored().cuboid(-1.0f, -2.0f, -2.0f, 4, 12, 4),
                ModelTransform.pivot(5.0f, 2.0f, 0.0f));
        root.addChild("right_leg",
                ModelPartBuilder.create().uv(0, 16).cuboid(-2.0f, 0.0f, -2.0f, 4, 12, 4),
                ModelTransform.pivot(-1.9f, 12.0f, 0.0f));
        root.addChild("left_leg",
                ModelPartBuilder.create().uv(0, 16).mirrored().cuboid(-2.0f, 0.0f, -2.0f, 4, 12, 4),
                ModelTransform.pivot(1.9f, 12.0f, 0.0f));

        return TexturedModelData.of(data, 64, 64);
    }

    @Override
    public ModelPart getPart() {
        return root;
    }

    @Override
    public void setAngles(SettlerEntity entity, float limbAngle, float limbDistance, float animationProgress,
                          float headYaw, float headPitch) {
        // Голова смотрит туда, куда смотрит моб
        head.yaw = headYaw * MathHelper.RADIANS_PER_DEGREE;
        head.pitch = headPitch * MathHelper.RADIANS_PER_DEGREE;

        // Ходьба: ноги шагают, руки машут в противофазе
        float swing = MathHelper.cos(limbAngle * 0.6662f) * limbDistance;
        rightLeg.pitch = swing * 1.4f;
        leftLeg.pitch = -swing * 1.4f;
        rightArm.pitch = -swing * 0.9f;
        leftArm.pitch = swing * 0.9f;

        // Лёгкое «дыхание» руками, когда стоит
        float idle = MathHelper.cos(animationProgress * 0.09f) * 0.05f + 0.05f;
        rightArm.roll = idle;
        leftArm.roll = -idle;
    }
}

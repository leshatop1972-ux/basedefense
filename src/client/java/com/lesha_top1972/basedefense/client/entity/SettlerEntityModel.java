package com.lesha_top1972.basedefense.client.entity;

import com.lesha_top1972.basedefense.entity.SettlerEntity;
import net.minecraft.client.model.Dilation;
import net.minecraft.client.model.ModelData;
import net.minecraft.client.model.ModelPart;
import net.minecraft.client.model.ModelPartBuilder;
import net.minecraft.client.model.ModelPartData;
import net.minecraft.client.model.ModelTransform;
import net.minecraft.client.model.TexturedModelData;
import net.minecraft.client.render.entity.model.ModelWithArms;
import net.minecraft.client.render.entity.model.SinglePartEntityModel;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.Arm;
import net.minecraft.util.math.MathHelper;
import org.jetbrains.annotations.Nullable;

/**
 * Модель жителя поселения — «человек» с пропорциями игрока. Используется в двух видах:
 * - основа ({@link #getTexturedModelData()}): голова, туловище, руки, ноги и второй слой
 *   (волосы, куртка, рукава, штаны); текстура 64×64 — внешность жителя (5 мужчин, 3 женщины),
 *   с профессией не связана;
 * - наряд профессии ({@link #getOutfitModelData()}): те же части чуть крупнее (поверх одежды)
 *   и головные уборы; текстура 128×64 — левая половина как скин, правая — шляпы/каски.
 * Анимации: шаг с покачиванием корпуса, взмах руками, «дыхание», рабочий удар инструментом,
 * держание инструмента в руке.
 */
public class SettlerEntityModel extends SinglePartEntityModel<SettlerEntity> implements ModelWithArms {

    /** Головные уборы. */
    public enum Headwear {
        NONE, BRIM_HAT, TOQUE, MINER_HELMET, HARD_HAT, BEANIE
    }

    private final ModelPart root;
    private final ModelPart head;
    private final ModelPart body;
    private final ModelPart rightArm;
    private final ModelPart leftArm;
    private final ModelPart rightLeg;
    private final ModelPart leftLeg;
    // Головные уборы есть только у модели наряда (у основы — null)
    @Nullable
    private final ModelPart brim;
    @Nullable
    private final ModelPart crown;
    @Nullable
    private final ModelPart toque;
    @Nullable
    private final ModelPart helmet;
    @Nullable
    private final ModelPart visor;
    @Nullable
    private final ModelPart lamp;

    public SettlerEntityModel(ModelPart root) {
        this.root = root;
        this.head = root.getChild("head");
        this.body = root.getChild("body");
        this.rightArm = root.getChild("right_arm");
        this.leftArm = root.getChild("left_arm");
        this.rightLeg = root.getChild("right_leg");
        this.leftLeg = root.getChild("left_leg");
        boolean outfit = head.hasChild("brim");
        this.brim = outfit ? head.getChild("brim") : null;
        this.crown = outfit ? head.getChild("crown") : null;
        this.toque = outfit ? head.getChild("toque") : null;
        this.helmet = outfit ? head.getChild("helmet") : null;
        this.visor = outfit ? head.getChild("visor") : null;
        this.lamp = outfit ? head.getChild("lamp") : null;
    }

    public static TexturedModelData getTexturedModelData() {
        ModelData data = new ModelData();
        ModelPartData root = data.getRoot();
        Dilation outer = new Dilation(0.25f);

        // Голова и волосы (второй слой)
        ModelPartData head = root.addChild("head",
                ModelPartBuilder.create().uv(0, 0).cuboid(-4.0f, -8.0f, -4.0f, 8, 8, 8),
                ModelTransform.pivot(0.0f, 0.0f, 0.0f));
        head.addChild("hair",
                ModelPartBuilder.create().uv(32, 0).cuboid(-4.0f, -8.0f, -4.0f, 8, 8, 8, new Dilation(0.5f)),
                ModelTransform.NONE);
        // Туловище и куртка
        ModelPartData body = root.addChild("body",
                ModelPartBuilder.create().uv(16, 16).cuboid(-4.0f, 0.0f, -2.0f, 8, 12, 4),
                ModelTransform.pivot(0.0f, 0.0f, 0.0f));
        body.addChild("jacket",
                ModelPartBuilder.create().uv(16, 32).cuboid(-4.0f, 0.0f, -2.0f, 8, 12, 4, outer),
                ModelTransform.NONE);

        // Руки и рукава
        ModelPartData rightArm = root.addChild("right_arm",
                ModelPartBuilder.create().uv(40, 16).cuboid(-3.0f, -2.0f, -2.0f, 4, 12, 4),
                ModelTransform.pivot(-5.0f, 2.0f, 0.0f));
        rightArm.addChild("right_sleeve",
                ModelPartBuilder.create().uv(40, 32).cuboid(-3.0f, -2.0f, -2.0f, 4, 12, 4, outer),
                ModelTransform.NONE);
        ModelPartData leftArm = root.addChild("left_arm",
                ModelPartBuilder.create().uv(32, 48).cuboid(-1.0f, -2.0f, -2.0f, 4, 12, 4),
                ModelTransform.pivot(5.0f, 2.0f, 0.0f));
        leftArm.addChild("left_sleeve",
                ModelPartBuilder.create().uv(48, 48).cuboid(-1.0f, -2.0f, -2.0f, 4, 12, 4, outer),
                ModelTransform.NONE);

        // Ноги и штанины
        ModelPartData rightLeg = root.addChild("right_leg",
                ModelPartBuilder.create().uv(0, 16).cuboid(-2.0f, 0.0f, -2.0f, 4, 12, 4),
                ModelTransform.pivot(-1.9f, 12.0f, 0.0f));
        rightLeg.addChild("right_pants",
                ModelPartBuilder.create().uv(0, 32).cuboid(-2.0f, 0.0f, -2.0f, 4, 12, 4, outer),
                ModelTransform.NONE);
        ModelPartData leftLeg = root.addChild("left_leg",
                ModelPartBuilder.create().uv(16, 48).cuboid(-2.0f, 0.0f, -2.0f, 4, 12, 4),
                ModelTransform.pivot(1.9f, 12.0f, 0.0f));
        leftLeg.addChild("left_pants",
                ModelPartBuilder.create().uv(0, 48).cuboid(-2.0f, 0.0f, -2.0f, 4, 12, 4, outer),
                ModelTransform.NONE);

        return TexturedModelData.of(data, 64, 64);
    }

    /** Наряд профессии: одежда поверх (чуть больше основы) и головные уборы. Текстура 128×64. */
    public static TexturedModelData getOutfitModelData() {
        ModelData data = new ModelData();
        ModelPartData root = data.getRoot();
        Dilation over = new Dilation(0.45f);

        ModelPartData head = root.addChild("head",
                ModelPartBuilder.create().uv(0, 0).cuboid(-4.0f, -8.0f, -4.0f, 8, 8, 8, new Dilation(0.62f)),
                ModelTransform.pivot(0.0f, 0.0f, 0.0f));
        // Головные уборы (правая половина текстуры)
        head.addChild("brim",
                ModelPartBuilder.create().uv(64, 0).cuboid(-7.0f, -7.6f, -7.0f, 14, 1, 14),
                ModelTransform.NONE);
        head.addChild("crown",
                ModelPartBuilder.create().uv(64, 16).cuboid(-4.0f, -10.6f, -4.0f, 8, 3, 8, new Dilation(0.45f)),
                ModelTransform.NONE);
        head.addChild("toque",
                ModelPartBuilder.create().uv(96, 16).cuboid(-4.0f, -14.0f, -4.0f, 8, 6, 8, new Dilation(0.6f)),
                ModelTransform.NONE);
        head.addChild("helmet",
                ModelPartBuilder.create().uv(64, 32).cuboid(-4.0f, -9.0f, -4.0f, 8, 3, 8, new Dilation(0.7f)),
                ModelTransform.NONE);
        head.addChild("visor",
                ModelPartBuilder.create().uv(96, 32).cuboid(-4.0f, -6.0f, -7.4f, 8, 1, 3),
                ModelTransform.NONE);
        head.addChild("lamp",
                ModelPartBuilder.create().uv(96, 40).cuboid(-1.0f, -8.6f, -5.6f, 2, 2, 1),
                ModelTransform.NONE);

        root.addChild("body",
                ModelPartBuilder.create().uv(16, 16).cuboid(-4.0f, 0.0f, -2.0f, 8, 12, 4, over),
                ModelTransform.pivot(0.0f, 0.0f, 0.0f));
        root.addChild("right_arm",
                ModelPartBuilder.create().uv(40, 16).cuboid(-3.0f, -2.0f, -2.0f, 4, 12, 4, over),
                ModelTransform.pivot(-5.0f, 2.0f, 0.0f));
        root.addChild("left_arm",
                ModelPartBuilder.create().uv(32, 48).cuboid(-1.0f, -2.0f, -2.0f, 4, 12, 4, over),
                ModelTransform.pivot(5.0f, 2.0f, 0.0f));
        root.addChild("right_leg",
                ModelPartBuilder.create().uv(0, 16).cuboid(-2.0f, 0.0f, -2.0f, 4, 12, 4, over),
                ModelTransform.pivot(-1.9f, 12.0f, 0.0f));
        root.addChild("left_leg",
                ModelPartBuilder.create().uv(16, 48).cuboid(-2.0f, 0.0f, -2.0f, 4, 12, 4, over),
                ModelTransform.pivot(1.9f, 12.0f, 0.0f));
        return TexturedModelData.of(data, 128, 64);
    }

    /** Какие головные уборы показать (вызывается рендером перед отрисовкой). */
    public void setHeadwear(Headwear headwear) {
        if (brim == null || crown == null || toque == null || helmet == null || visor == null || lamp == null) {
            return;
        }
        brim.visible = headwear == Headwear.BRIM_HAT;
        crown.visible = headwear == Headwear.BRIM_HAT;
        toque.visible = headwear == Headwear.TOQUE;
        helmet.visible = headwear == Headwear.MINER_HELMET || headwear == Headwear.HARD_HAT
                || headwear == Headwear.BEANIE;
        visor.visible = headwear == Headwear.MINER_HELMET || headwear == Headwear.HARD_HAT;
        lamp.visible = headwear == Headwear.MINER_HELMET;
    }

    @Override
    public ModelPart getPart() {
        return root;
    }

    @Override
    public void setAngles(SettlerEntity entity, float limbAngle, float limbDistance, float animationProgress,
                          float headYaw, float headPitch) {
        resetPose();
        float walk = Math.min(1.0f, limbDistance);

        // Голова смотрит туда, куда смотрит житель; лёгкий наклон головы в покое
        head.yaw = headYaw * MathHelper.RADIANS_PER_DEGREE;
        head.pitch = headPitch * MathHelper.RADIANS_PER_DEGREE;
        head.roll = MathHelper.sin(animationProgress * 0.04f) * 0.04f * (1.0f - walk);

        // Шаг: ноги и руки в противофазе, корпус чуть поворачивается, всё тело слегка подпрыгивает
        float phase = limbAngle * 0.6662f;
        float swing = MathHelper.cos(phase) * limbDistance;
        rightLeg.pitch = swing * 1.4f;
        leftLeg.pitch = -swing * 1.4f;
        rightArm.pitch = -swing * 1.0f;
        leftArm.pitch = swing * 1.0f;
        body.yaw = MathHelper.sin(phase) * 0.08f * walk;
        rightArm.yaw = body.yaw;
        leftArm.yaw = body.yaw;
        float bob = Math.abs(MathHelper.cos(phase)) * walk * 0.8f;
        for (ModelPart part : new ModelPart[]{head, body, rightArm, leftArm}) {
            part.pivotY += bob;
        }

        // «Дыхание»: руки чуть отходят от тела, плечи поднимаются
        float breath = MathHelper.cos(animationProgress * 0.08f);
        rightArm.roll = 0.05f + breath * 0.03f;
        leftArm.roll = -0.05f - breath * 0.03f;
        rightArm.pivotY += breath * 0.15f;
        leftArm.pivotY += breath * 0.15f;

        // Держит инструмент — рука немного вперёд
        if (!entity.getMainHandStack().isEmpty()) {
            rightArm.pitch = rightArm.pitch * 0.5f - 0.31f;
        }

        // Удар инструментом (работа, стройка): замах с поворотом корпуса
        if (this.handSwingProgress > 0.0f) {
            float p = this.handSwingProgress;
            body.yaw = MathHelper.sin(MathHelper.sqrt(p) * MathHelper.TAU) * 0.2f;
            rightArm.pivotZ = MathHelper.sin(body.yaw) * 5.0f;
            rightArm.pivotX = -MathHelper.cos(body.yaw) * 5.0f;
            leftArm.pivotZ = -MathHelper.sin(body.yaw) * 5.0f;
            leftArm.pivotX = MathHelper.cos(body.yaw) * 5.0f;
            rightArm.yaw += body.yaw;
            leftArm.yaw += body.yaw;
            leftArm.pitch += body.yaw;
            float f = 1.0f - p;
            f = 1.0f - f * f * f * f;
            float g = MathHelper.sin(f * MathHelper.PI);
            float h = MathHelper.sin(p * MathHelper.PI) * -(head.pitch - 0.7f) * 0.75f;
            rightArm.pitch -= g * 1.2f + h;
            rightArm.yaw += body.yaw * 2.0f;
            rightArm.roll += MathHelper.sin(p * MathHelper.PI) * -0.4f;
        }
    }

    /** Возвращает части в исходное положение (setAngles вызывается каждый кадр). */
    private void resetPose() {
        head.setPivot(0.0f, 0.0f, 0.0f);
        body.setPivot(0.0f, 0.0f, 0.0f);
        rightArm.setPivot(-5.0f, 2.0f, 0.0f);
        leftArm.setPivot(5.0f, 2.0f, 0.0f);
        rightLeg.setPivot(-1.9f, 12.0f, 0.0f);
        leftLeg.setPivot(1.9f, 12.0f, 0.0f);
        for (ModelPart part : new ModelPart[]{head, body, rightArm, leftArm, rightLeg, leftLeg}) {
            part.pitch = 0.0f;
            part.yaw = 0.0f;
            part.roll = 0.0f;
        }
    }

    /** Куда рисовать предмет в руке. */
    @Override
    public void setArmAngle(Arm arm, MatrixStack matrices) {
        (arm == Arm.LEFT ? leftArm : rightArm).rotate(matrices);
    }
}

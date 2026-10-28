package com.lesha_top1972.basedefense.client.entity.settler;

import com.lesha_top1972.basedefense.entity.settler.SettlerEntity;
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

/**
 * Модель жителя поселения (по образцу жителей MineColonies): человек с пропорциями игрока,
 * у каждой части второй слой (волосы, воротник/жилет, рукава, штанины).
 * Женская внешность включает свои части: тонкие руки (3 пикселя), грудь, юбку-платье,
 * пряди волос по спине и хвост. Что показывать — задаёт {@link #setLook(Look)} перед отрисовкой.
 *
 * Текстура 128×64:
 *   левая половина — как скин игрока (голова, тело, широкие руки, ноги и их вторые слои);
 *   (64,0) пряди по спине, (82,0) хвост, (96,0) юбка, (96,13) низ юбки,
 *   (64,16)/(64,32) тонкая правая рука и рукав, (80,16)/(80,32) тонкая левая, (64,48)/(64,52) грудь;
 *   (64,9) козырёк кепки, (86,9) сумочка на поясе,
 *   (96,24)/(96,27) поля шляпы спереди/сзади, (96,30)/(96,39) справа/слева, (96,48) тулья.
 * Аксессуары (шляпа, кепка, сумочка) — тоже часть внешности, а не профессии.
 * Предпросмотр без игры: tools/settler_preview.py (там тот же список кубов — менять вместе).
 */
public class SettlerEntityModel extends SinglePartEntityModel<SettlerEntity> implements ModelWithArms {

    public static final int HAT_NONE = 0;
    public static final int HAT_BRIM = 1;   // шляпа с полями
    public static final int HAT_CAP = 2;    // кепка с козырьком

    /** Как выглядит житель: фигура, длинные волосы по спине, хвост, головной убор, сумочка на поясе. */
    public record Look(boolean female, boolean longHair, boolean ponytail, int hat, boolean pouch) {
    }

    private final ModelPart root;
    private final ModelPart head;
    private final ModelPart body;
    private final ModelPart rightArm;
    private final ModelPart leftArm;
    private final ModelPart rightLeg;
    private final ModelPart leftLeg;
    private final ModelPart rightWide;
    private final ModelPart rightSlim;
    private final ModelPart leftWide;
    private final ModelPart leftSlim;
    private final ModelPart hairBack;
    private final ModelPart ponytail;
    private final ModelPart breast;
    private final ModelPart skirt;
    private final ModelPart pouch;
    private final ModelPart[] brimHat;
    private final ModelPart visor;
    private Look look = new Look(false, false, false, HAT_NONE, false);

    public SettlerEntityModel(ModelPart root) {
        this.root = root;
        this.head = root.getChild("head");
        this.body = root.getChild("body");
        this.rightArm = root.getChild("right_arm");
        this.leftArm = root.getChild("left_arm");
        this.rightLeg = root.getChild("right_leg");
        this.leftLeg = root.getChild("left_leg");
        this.rightWide = rightArm.getChild("wide");
        this.rightSlim = rightArm.getChild("slim");
        this.leftWide = leftArm.getChild("wide");
        this.leftSlim = leftArm.getChild("slim");
        this.hairBack = head.getChild("hair_back");
        this.ponytail = head.getChild("ponytail");
        this.breast = body.getChild("breast");
        this.skirt = body.getChild("skirt");
        this.pouch = body.getChild("pouch");
        this.brimHat = new ModelPart[]{head.getChild("hat_crown"), head.getChild("brim_front"),
                head.getChild("brim_back"), head.getChild("brim_right"), head.getChild("brim_left")};
        this.visor = head.getChild("visor");
    }

    public static TexturedModelData getTexturedModelData() {
        ModelData data = new ModelData();
        ModelPartData root = data.getRoot();
        Dilation outer = new Dilation(0.25f);

        // ---------- Голова ----------
        ModelPartData head = root.addChild("head",
                ModelPartBuilder.create()
                        .uv(0, 0).cuboid(-4.0f, -8.0f, -4.0f, 8, 8, 8)
                        .uv(32, 0).cuboid(-4.0f, -8.0f, -4.0f, 8, 8, 8, new Dilation(0.5f)),
                ModelTransform.pivot(0.0f, 0.0f, 0.0f));
        // Пряди по спине (длинные волосы)
        head.addChild("hair_back",
                ModelPartBuilder.create().uv(64, 0).cuboid(-4.0f, 0.0f, 0.0f, 8, 7, 1, new Dilation(0.5f)),
                ModelTransform.pivot(0.0f, -1.0f, 3.0f));
        // Хвост, перевязанный на затылке
        head.addChild("ponytail",
                ModelPartBuilder.create().uv(82, 0).cuboid(-1.0f, 0.0f, 0.0f, 2, 5, 2),
                ModelTransform.of(0.0f, -6.0f, 4.0f, 0.55f, 0.0f, 0.0f));
        // Шляпа с полями: тулья + 4 полосы полей вокруг головы
        head.addChild("hat_crown",
                ModelPartBuilder.create().uv(96, 48).cuboid(-4.0f, -10.0f, -4.0f, 8, 3, 8, new Dilation(0.6f)),
                ModelTransform.NONE);
        head.addChild("brim_front",
                ModelPartBuilder.create().uv(96, 24).cuboid(-6.0f, -7.2f, -6.0f, 12, 1, 2), ModelTransform.NONE);
        head.addChild("brim_back",
                ModelPartBuilder.create().uv(96, 27).cuboid(-6.0f, -7.2f, 4.0f, 12, 1, 2), ModelTransform.NONE);
        head.addChild("brim_right",
                ModelPartBuilder.create().uv(96, 30).cuboid(-6.0f, -7.2f, -4.0f, 2, 1, 8), ModelTransform.NONE);
        head.addChild("brim_left",
                ModelPartBuilder.create().uv(96, 39).cuboid(4.0f, -7.2f, -4.0f, 2, 1, 8), ModelTransform.NONE);
        // Козырёк кепки (сама кепка нарисована на втором слое головы)
        head.addChild("visor",
                ModelPartBuilder.create().uv(64, 9).cuboid(-4.0f, -8.1f, -7.0f, 8, 1, 3), ModelTransform.NONE);

        // ---------- Туловище ----------
        ModelPartData body = root.addChild("body",
                ModelPartBuilder.create()
                        .uv(16, 16).cuboid(-4.0f, 0.0f, -2.0f, 8, 12, 4)
                        .uv(16, 32).cuboid(-4.0f, 0.0f, -2.0f, 8, 12, 4, outer),
                ModelTransform.pivot(0.0f, 0.0f, 0.0f));
        body.addChild("breast",
                ModelPartBuilder.create()
                        .uv(64, 48).cuboid(-3.5f, 0.0f, -1.0f, 7, 3, 1)
                        .uv(64, 52).cuboid(-3.5f, 0.0f, -1.0f, 7, 3, 1, outer),
                ModelTransform.pivot(0.0f, 2.5f, -2.0f));
        // Юбка-платье: от пояса до колен, внизу шире
        body.addChild("skirt",
                ModelPartBuilder.create()
                        .uv(96, 0).cuboid(-4.5f, 0.0f, -2.5f, 9, 8, 5)
                        .uv(96, 13).cuboid(-5.0f, 4.0f, -3.0f, 10, 4, 6),
                ModelTransform.pivot(0.0f, 10.0f, 0.0f));
        // Кожаная сумочка на поясе, спереди справа
        body.addChild("pouch",
                ModelPartBuilder.create().uv(86, 9).cuboid(-3.6f, 0.0f, -1.0f, 3, 3, 1),
                ModelTransform.pivot(0.0f, 8.4f, -2.0f));

        // ---------- Руки: широкие (мужские) и тонкие (женские) ----------
        ModelPartData rightArm = root.addChild("right_arm", ModelPartBuilder.create(),
                ModelTransform.pivot(-5.0f, 2.0f, 0.0f));
        rightArm.addChild("wide",
                ModelPartBuilder.create()
                        .uv(40, 16).cuboid(-3.0f, -2.0f, -2.0f, 4, 12, 4)
                        .uv(40, 32).cuboid(-3.0f, -2.0f, -2.0f, 4, 12, 4, outer),
                ModelTransform.NONE);
        rightArm.addChild("slim",
                ModelPartBuilder.create()
                        .uv(64, 16).cuboid(-2.0f, -2.0f, -2.0f, 3, 12, 4)
                        .uv(64, 32).cuboid(-2.0f, -2.0f, -2.0f, 3, 12, 4, outer),
                ModelTransform.NONE);
        ModelPartData leftArm = root.addChild("left_arm", ModelPartBuilder.create(),
                ModelTransform.pivot(5.0f, 2.0f, 0.0f));
        leftArm.addChild("wide",
                ModelPartBuilder.create()
                        .uv(32, 48).cuboid(-1.0f, -2.0f, -2.0f, 4, 12, 4)
                        .uv(48, 48).cuboid(-1.0f, -2.0f, -2.0f, 4, 12, 4, outer),
                ModelTransform.NONE);
        leftArm.addChild("slim",
                ModelPartBuilder.create()
                        .uv(80, 16).cuboid(-1.0f, -2.0f, -2.0f, 3, 12, 4)
                        .uv(80, 32).cuboid(-1.0f, -2.0f, -2.0f, 3, 12, 4, outer),
                ModelTransform.NONE);

        // ---------- Ноги ----------
        root.addChild("right_leg",
                ModelPartBuilder.create()
                        .uv(0, 16).cuboid(-2.0f, 0.0f, -2.0f, 4, 12, 4)
                        .uv(0, 32).cuboid(-2.0f, 0.0f, -2.0f, 4, 12, 4, outer),
                ModelTransform.pivot(-1.9f, 12.0f, 0.0f));
        root.addChild("left_leg",
                ModelPartBuilder.create()
                        .uv(16, 48).cuboid(-2.0f, 0.0f, -2.0f, 4, 12, 4)
                        .uv(0, 48).cuboid(-2.0f, 0.0f, -2.0f, 4, 12, 4, outer),
                ModelTransform.pivot(1.9f, 12.0f, 0.0f));

        return TexturedModelData.of(data, 128, 64);
    }

    /** Какие части показать (вызывается рендером перед отрисовкой каждого жителя). */
    public void setLook(Look look) {
        this.look = look;
        rightWide.visible = !look.female();
        leftWide.visible = !look.female();
        rightSlim.visible = look.female();
        leftSlim.visible = look.female();
        breast.visible = look.female();
        skirt.visible = look.female();
        hairBack.visible = look.longHair();
        ponytail.visible = look.ponytail();
        pouch.visible = look.pouch();
        for (ModelPart part : brimHat) {
            part.visible = look.hat() == HAT_BRIM;
        }
        visor.visible = look.hat() == HAT_CAP;
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
        float stride = look.female() ? 1.1f : 1.4f;          // женщины шагают мельче
        rightLeg.pitch = swing * stride;
        leftLeg.pitch = -swing * stride;
        rightArm.pitch = -swing * 0.9f;
        leftArm.pitch = swing * 0.9f;
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

        // Юбка и волосы покачиваются при ходьбе
        skirt.pitch = MathHelper.sin(phase * 2.0f) * 0.04f * walk;
        skirt.roll = MathHelper.sin(phase) * 0.05f * walk;
        pouch.pitch = -MathHelper.sin(phase * 2.0f) * 0.06f * walk;   // сумочка подпрыгивает на шаге
        hairBack.pitch = 0.08f * walk + Math.max(0.0f, -head.pitch) * 0.5f;
        ponytail.pitch = 0.55f + MathHelper.sin(phase) * 0.18f * walk + breath * 0.02f;
        ponytail.roll = MathHelper.cos(phase) * 0.12f * walk;

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
        pouch.setPivot(0.0f, 8.4f, -2.0f);
        rightArm.setPivot(-5.0f, 2.0f, 0.0f);
        leftArm.setPivot(5.0f, 2.0f, 0.0f);
        rightLeg.setPivot(-1.9f, 12.0f, 0.0f);
        leftLeg.setPivot(1.9f, 12.0f, 0.0f);
        for (ModelPart part : new ModelPart[]{head, body, rightArm, leftArm, rightLeg, leftLeg, skirt, hairBack, pouch}) {
            part.pitch = 0.0f;
            part.yaw = 0.0f;
            part.roll = 0.0f;
        }
    }

    /** Куда рисовать предмет в руке (у тонкой руки — на полпикселя ближе к телу, как у игрока). */
    @Override
    public void setArmAngle(Arm arm, MatrixStack matrices) {
        ModelPart part = arm == Arm.LEFT ? leftArm : rightArm;
        float shift = look.female() ? 0.5f * (arm == Arm.RIGHT ? 1 : -1) : 0.0f;
        part.pivotX += shift;
        part.rotate(matrices);
        part.pivotX -= shift;
    }
}

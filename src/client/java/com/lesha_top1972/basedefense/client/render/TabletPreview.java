package com.lesha_top1972.basedefense.client.render;

import com.lesha_top1972.basedefense.item.BuildingTabletItem;
import com.lesha_top1972.basedefense.structure.HouseType;
import com.lesha_top1972.basedefense.structure.StructurePlan;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderContext;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderEvents;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.render.WorldRenderer;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Vec3d;

/**
 * Пока в руке строительный планшет с выбранной постройкой, показывает рамку,
 * где встанет постройка (весь дом со двором): зелёная — можно ставить, красная — нельзя.
 * Жёлтый кубик — место, где появится блок стройплощадки.
 */
public final class TabletPreview {

    private TabletPreview() {
    }

    public static void init() {
        WorldRenderEvents.BEFORE_DEBUG_RENDER.register(TabletPreview::render);
    }

    private static void render(WorldRenderContext context) {
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.player == null || mc.world == null || context.consumers() == null) {
            return;
        }
        HouseType type = BuildingTabletItem.getSelected(BuildingTabletItem.findInHands(mc.player));
        if (type == null || !(mc.crosshairTarget instanceof BlockHitResult hit)
                || hit.getType() != HitResult.Type.BLOCK) {
            return;
        }

        // Та же логика, что и на сервере: в саму клетку, если она заменяемая, иначе — рядом
        BlockPos clicked = hit.getBlockPos();
        BlockPos pos = mc.world.getBlockState(clicked).isReplaceable() ? clicked : clicked.offset(hit.getSide());
        boolean ok = mc.world.getBlockState(pos).isReplaceable();
        Direction facing = mc.player.getHorizontalFacing();

        StructurePlan plan = type.getPlan(BuildingTabletItem.getSelectedDesign(BuildingTabletItem.findInHands(mc.player)));
        BlockPos[] bounds = plan.getBounds();
        BlockPos a = plan.toWorld(pos, facing, bounds[0].getX(), bounds[0].getY(), bounds[0].getZ());
        BlockPos b = plan.toWorld(pos, facing, bounds[1].getX(), bounds[1].getY(), bounds[1].getZ());
        Box area = new Box(
                Math.min(a.getX(), b.getX()), Math.min(a.getY(), b.getY()), Math.min(a.getZ(), b.getZ()),
                Math.max(a.getX(), b.getX()) + 1, Math.max(a.getY(), b.getY()) + 1, Math.max(a.getZ(), b.getZ()) + 1);

        Vec3d cam = context.camera().getPos();
        MatrixStack matrices = context.matrixStack();
        matrices.push();
        matrices.translate(-cam.x, -cam.y, -cam.z);
        VertexConsumer lines = context.consumers().getBuffer(RenderLayer.getLines());
        float r = ok ? 0.35f : 0.95f;
        float g = ok ? 0.9f : 0.25f;
        float bl = ok ? 0.4f : 0.2f;
        WorldRenderer.drawBox(matrices, lines, area, r, g, bl, 1.0f);
        WorldRenderer.drawBox(matrices, lines, new Box(pos).expand(0.002), 1.0f, 0.85f, 0.2f, 1.0f);
        matrices.pop();
    }
}

package com.lesha_top1972.basedefense.client.structure;

import com.lesha_top1972.basedefense.structure.StructurePlan;
import net.minecraft.block.BlockRenderType;
import net.minecraft.block.BlockState;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.render.DiffuseLighting;
import net.minecraft.client.render.LightmapTextureManager;
import net.minecraft.client.render.OverlayTexture;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.block.BlockRenderManager;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.RotationAxis;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;

/**
 * Объёмный предпросмотр постройки в меню (как чертёж в MineColonies): настоящие блоки чертежа,
 * повёрнутые и уменьшенные, с медленным вращением. Подземные части (фундамент, шахта) не рисуются,
 * блоки, со всех сторон закрытые другими, пропускаются — так быстрее.
 * Блоки с «сущностью» (сундуки, кровати, колокол) и вода в предпросмотре не видны.
 */
public final class StructurePreview {

    /** Что рисовать для каждого чертежа (считается один раз). */
    private static final Map<StructurePlan, Model> CACHE = new IdentityHashMap<>();

    private record Block(int x, int y, int z, BlockState state) {
    }

    private record Model(List<Block> blocks, float cx, float cy, float cz, float radius, float height) {
    }

    private StructurePreview() {
    }

    private static Model model(StructurePlan plan) {
        return CACHE.computeIfAbsent(plan, StructurePreview::build);
    }

    private static Model build(StructurePlan plan) {
        // Итоговое состояние каждой клетки (поздние шаги перекрывают ранние, воздух — убирает)
        Map<BlockPos, BlockState> cells = new HashMap<>();
        for (List<StructurePlan.Placement> step : plan.toSteps()) {
            for (StructurePlan.Placement p : step) {
                if (p.onlyIfReplaceable() || p.local().getY() < 0) {
                    continue;
                }
                if (p.state().isAir()) {
                    cells.remove(p.local());
                } else {
                    cells.put(p.local(), p.state());
                }
            }
        }
        List<Block> blocks = new ArrayList<>();
        int minX = Integer.MAX_VALUE, minY = Integer.MAX_VALUE, minZ = Integer.MAX_VALUE;
        int maxX = Integer.MIN_VALUE, maxY = Integer.MIN_VALUE, maxZ = Integer.MIN_VALUE;
        for (Map.Entry<BlockPos, BlockState> e : cells.entrySet()) {
            BlockPos pos = e.getKey();
            BlockState state = e.getValue();
            if (state.getRenderType() != BlockRenderType.MODEL || hidden(cells, pos)) {
                continue;
            }
            // Локально NORTH = +z, в мире NORTH = −z: переворачиваем z (это поворот, а не зеркало)
            blocks.add(new Block(pos.getX(), pos.getY(), -pos.getZ(), state));
            minX = Math.min(minX, pos.getX());
            maxX = Math.max(maxX, pos.getX());
            minY = Math.min(minY, pos.getY());
            maxY = Math.max(maxY, pos.getY());
            minZ = Math.min(minZ, -pos.getZ());
            maxZ = Math.max(maxZ, -pos.getZ());
        }
        if (blocks.isEmpty()) {
            return new Model(blocks, 0, 0, 0, 1, 1);
        }
        float sx = maxX - minX + 1;
        float sz = maxZ - minZ + 1;
        return new Model(blocks, (minX + maxX + 1) / 2.0f, (minY + maxY + 1) / 2.0f, (minZ + maxZ + 1) / 2.0f,
                (float) Math.sqrt(sx * sx + sz * sz) / 2.0f, maxY - minY + 1);
    }

    /** Блок со всех 6 сторон закрыт непрозрачными блоками — его не видно. */
    private static boolean hidden(Map<BlockPos, BlockState> cells, BlockPos pos) {
        for (BlockPos n : new BlockPos[]{pos.up(), pos.down(), pos.north(), pos.south(), pos.east(), pos.west()}) {
            BlockState s = cells.get(n);
            if (s == null || !s.isOpaque()) {
                return false;
            }
        }
        return true;
    }

    /**
     * Нарисовать постройку в прямоугольнике (l, t)–(r, b) экрана.
     * @param yaw   поворот вокруг вертикали, градусы
     * @param pitch наклон «камеры», градусы (30 — красивый вид сверху-сбоку)
     */
    public static void draw(DrawContext context, StructurePlan plan, int l, int t, int r, int b, float yaw, float pitch) {
        Model m = model(plan);
        if (m.blocks().isEmpty()) {
            return;
        }
        int w = r - l;
        int h = b - t;
        // Масштаб: постройка целиком влезает в рамку при любом повороте
        float rad = (float) Math.toRadians(pitch);
        float visW = m.radius() * 2.0f;
        float visH = m.radius() * 2.0f * (float) Math.sin(rad) + m.height() * (float) Math.cos(rad);
        float scale = Math.min(w / visW, h / visH) * 0.92f;

        MinecraftClient mc = MinecraftClient.getInstance();
        BlockRenderManager blocks = mc.getBlockRenderManager();
        VertexConsumerProvider.Immediate consumers = context.getVertexConsumers();
        MatrixStack ms = context.getMatrices();

        context.enableScissor(l, t, r, b);
        ms.push();
        ms.translate(l + w / 2.0f, t + h / 2.0f, 200.0f);
        ms.scale(scale, -scale, scale);
        ms.multiply(RotationAxis.POSITIVE_X.rotationDegrees(pitch));
        ms.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(yaw));
        ms.translate(-m.cx(), -m.cy(), -m.cz());
        DiffuseLighting.enableGuiDepthLighting();
        for (Block block : m.blocks()) {
            ms.push();
            ms.translate(block.x(), block.y(), block.z());
            blocks.renderBlockAsEntity(block.state(), ms, consumers, LightmapTextureManager.MAX_LIGHT_COORDINATE,
                    OverlayTexture.DEFAULT_UV);
            ms.pop();
        }
        consumers.draw();
        DiffuseLighting.disableGuiDepthLighting();
        ms.pop();
        context.disableScissor();
    }
}

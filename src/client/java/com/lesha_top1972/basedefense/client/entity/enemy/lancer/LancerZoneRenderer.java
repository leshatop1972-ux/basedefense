package com.lesha_top1972.basedefense.client.entity.enemy.lancer;

import com.lesha_top1972.basedefense.Basedefense;
import com.lesha_top1972.basedefense.entity.enemy.lancer.TatteredLancerEntity;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderContext;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderEvents;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.OverlayTexture;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.entity.Entity;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.Vec3d;
import org.joml.Matrix3f;
import org.joml.Matrix4f;

/**
 * Синяя зона «ливня копий» на земле под уланом: светящийся круг радиусом 8 блоков
 * (textures/misc/lance_zone.png), медленно вращается и мерцает.
 */
public final class LancerZoneRenderer {

    private static final Identifier ZONE = new Identifier(Basedefense.MOD_ID, "textures/misc/lance_zone.png");

    private LancerZoneRenderer() {
    }

    public static void init() {
        WorldRenderEvents.AFTER_ENTITIES.register(LancerZoneRenderer::render);
    }

    private static void render(WorldRenderContext context) {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client.world == null || context.consumers() == null) {
            return;
        }
        float tickDelta = context.tickDelta();
        Vec3d cam = context.camera().getPos();
        MatrixStack matrices = context.matrixStack();
        VertexConsumerProvider consumers = context.consumers();
        for (Entity e : client.world.getEntities()) {
            if (!(e instanceof TatteredLancerEntity lancer)) {
                continue;
            }
            float alpha = lancer.clientZoneAlpha(tickDelta);
            if (alpha <= 0f) {
                continue;
            }
            float time = lancer.age + tickDelta - lancer.clientRainAge;
            boolean raining = time >= TatteredLancerEntity.RAIN_START;
            float flicker = 0.85f + 0.15f * (float) Math.sin(time * (raining ? 0.9f : 0.35f));
            int a = (int) (255 * alpha * flicker * (raining ? 0.95f : 0.7f));
            Vec3d c = lancer.clientZone;
            float r = (float) TatteredLancerEntity.RAIN_RADIUS;
            matrices.push();
            matrices.translate(c.x - cam.x, c.y - cam.y + 0.03, c.z - cam.z);
            matrices.multiply(net.minecraft.util.math.RotationAxis.POSITIVE_Y.rotationDegrees(time * 1.2f));
            VertexConsumer vc = consumers.getBuffer(RenderLayer.getEntityTranslucentEmissive(ZONE));
            MatrixStack.Entry entry = matrices.peek();
            Matrix4f m = entry.getPositionMatrix();
            Matrix3f n = entry.getNormalMatrix();
            quad(vc, m, n, r, a);
            matrices.pop();
        }
    }

    private static void quad(VertexConsumer vc, Matrix4f m, Matrix3f n, float r, int a) {
        int light = 0xF000F0;
        vc.vertex(m, -r, 0, -r).color(255, 255, 255, a).texture(0, 0).overlay(OverlayTexture.DEFAULT_UV).light(light).normal(n, 0, 1, 0).next();
        vc.vertex(m, -r, 0, r).color(255, 255, 255, a).texture(0, 1).overlay(OverlayTexture.DEFAULT_UV).light(light).normal(n, 0, 1, 0).next();
        vc.vertex(m, r, 0, r).color(255, 255, 255, a).texture(1, 1).overlay(OverlayTexture.DEFAULT_UV).light(light).normal(n, 0, 1, 0).next();
        vc.vertex(m, r, 0, -r).color(255, 255, 255, a).texture(1, 0).overlay(OverlayTexture.DEFAULT_UV).light(light).normal(n, 0, 1, 0).next();
    }
}

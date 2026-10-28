package com.lesha_top1972.basedefense.client.entity.bedrock;

import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.entity.feature.EyesFeatureRenderer;
import net.minecraft.client.render.entity.feature.FeatureRendererContext;
import net.minecraft.client.render.entity.model.EntityModel;
import net.minecraft.entity.Entity;
import net.minecraft.util.Identifier;

/** Светящиеся части моба (глаза и т.п.) — текстура *_glowmask.png, видна в темноте. */
public class GlowLayer<T extends Entity, M extends EntityModel<T>> extends EyesFeatureRenderer<T, M> {

    private final RenderLayer layer;

    public GlowLayer(FeatureRendererContext<T, M> context, Identifier texture) {
        super(context);
        this.layer = RenderLayer.getEyes(texture);
    }

    @Override
    public RenderLayer getEyesTexture() {
        return this.layer;
    }
}

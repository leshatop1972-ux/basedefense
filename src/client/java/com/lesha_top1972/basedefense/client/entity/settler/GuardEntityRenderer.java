package com.lesha_top1972.basedefense.client.entity.settler;

import net.minecraft.client.render.entity.EntityRendererFactory;

/** Стражник: как житель, плюс броня (GuardArmorFeature). Анимации — в GuardAnimations. */
public class GuardEntityRenderer extends SettlerEntityRenderer {

    public GuardEntityRenderer(EntityRendererFactory.Context context) {
        super(context);
        this.addFeature(new GuardArmorFeature(this, context.getModelLoader()));
    }
}

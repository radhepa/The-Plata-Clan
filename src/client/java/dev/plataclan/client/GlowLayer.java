package dev.plataclan.client;

import dev.plataclan.PlataClan;
import net.minecraft.client.model.EntityModel;
import net.minecraft.client.renderer.entity.RenderLayerParent;
import net.minecraft.client.renderer.entity.layers.EyesLayer;
import net.minecraft.client.renderer.entity.state.EntityRenderState;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.client.renderer.rendertype.RenderTypes;

/** Draws a model's {@code <id>_glow.png} at full brightness: the Silver Mother's cold light. */
public final class GlowLayer<S extends EntityRenderState, M extends EntityModel<S>> extends EyesLayer<S, M> {
    private final RenderType type;

    public GlowLayer(RenderLayerParent<S, M> renderer, String textureId) {
        super(renderer);
        this.type = RenderTypes.eyes(PlataClan.id("textures/entity/" + textureId + "_glow.png"));
    }

    @Override
    public RenderType renderType() { return this.type; }
}

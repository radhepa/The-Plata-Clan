package dev.plataclan.client;

import com.mojang.blaze3d.vertex.PoseStack;
import dev.plataclan.PlataClan;
import dev.plataclan.Rank;
import dev.plataclan.entity.PlataMember;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.HumanoidMobRenderer;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.HumanoidArm;

/** Draws a Plata of one rank, or nothing at all while they're under the ground. */
public final class PlataRenderer extends HumanoidMobRenderer<PlataMember, PlataRenderState, PlataModel> {
    private final Identifier texture;

    public PlataRenderer(EntityRendererProvider.Context context, Rank rank) {
        super(context, new PlataModel(context.bakeLayer(Geometry.layer("plata_" + rank.id)), "plata_" + rank.id), .5F);
        this.texture = PlataClan.id("textures/entity/plata_" + rank.id + ".png");
    }

    @Override public PlataRenderState createRenderState() { return new PlataRenderState(); }
    @Override public Identifier getTextureLocation(PlataRenderState state) { return this.texture; }
    /** Their weapons are part of the costume, so the arms never take vanilla's holding poses. */
    @Override protected HumanoidModel.ArmPose getArmPose(PlataMember mob, HumanoidArm arm) { return HumanoidModel.ArmPose.EMPTY; }

    @Override
    public void extractRenderState(PlataMember mob, PlataRenderState s, float partialTick) {
        super.extractRenderState(mob, s, partialTick);
        s.rank = mob.rank;
        s.move = mob.move();
        s.moveTime = mob.moveTime(partialTick);
        s.hidden = s.move.hidden();
        s.speed = Mth.clamp(s.walkAnimationSpeed * 1.4F, 0, 1);
        if (mob.deathTime > 0) {
            // no tipping over: the defeat clip curls them up instead, and no red flash
            s.dying = (mob.deathTime + partialTick) / 20F;
            s.deathTime = 0;
            s.hasRedOverlay = false;
        } else {
            s.dying = -1;
        }
    }

    @Override
    public void submit(PlataRenderState state, PoseStack pose, SubmitNodeCollector collector, CameraRenderState camera) {
        if (!state.hidden) super.submit(state, pose, collector, camera);
    }

    @Override
    protected float getShadowRadius(PlataRenderState state) {
        return state.hidden ? 0 : super.getShadowRadius(state);
    }
}

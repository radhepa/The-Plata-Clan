package dev.plataclan.client;

import com.mojang.blaze3d.vertex.PoseStack;
import dev.plataclan.PlataClan;
import dev.plataclan.mother.SilverMother;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.HumanoidMobRenderer;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.HumanoidArm;

/** Draws the Silver Mother: her children's mesh at half again their height, her light on top; nothing while she's under the floor. */
public final class MotherRenderer extends HumanoidMobRenderer<SilverMother, MotherRenderState, MotherModel> {
    public static final String ID = "silver_mother";
    public static final float SCALE = 1.5F;
    private static final Identifier TEXTURE = PlataClan.id("textures/entity/" + ID + ".png");

    public MotherRenderer(EntityRendererProvider.Context context) {
        super(context, new MotherModel(context.bakeLayer(Geometry.layer(ID))), .9F);
        this.addLayer(new GlowLayer<>(this, ID));
    }

    @Override public MotherRenderState createRenderState() { return new MotherRenderState(); }
    @Override public Identifier getTextureLocation(MotherRenderState state) { return TEXTURE; }
    @Override protected HumanoidModel.ArmPose getArmPose(SilverMother mob, HumanoidArm arm) { return HumanoidModel.ArmPose.EMPTY; }

    @Override
    protected void scale(MotherRenderState state, PoseStack pose) {
        pose.scale(SCALE, SCALE, SCALE);
    }

    @Override
    public void extractRenderState(SilverMother mob, MotherRenderState s, float partialTick) {
        super.extractRenderState(mob, s, partialTick);
        s.move = mob.move();
        s.moveTime = mob.moveTime(partialTick);
        s.hidden = s.move.hidden();
        s.phase = mob.phase();
        s.speed = Mth.clamp(s.walkAnimationSpeed * 1.6F, 0, 1);
        if (mob.deathTime > 0) {
            s.dying = (mob.deathTime + partialTick) / 20F;
            s.deathTime = 0;
            s.hasRedOverlay = false;
        } else {
            s.dying = -1;
        }
    }

    @Override
    public void submit(MotherRenderState state, PoseStack pose, SubmitNodeCollector collector, CameraRenderState camera) {
        if (!state.hidden) super.submit(state, pose, collector, camera);
    }

    @Override
    protected float getShadowRadius(MotherRenderState state) {
        return state.hidden ? 0 : super.getShadowRadius(state);
    }
}

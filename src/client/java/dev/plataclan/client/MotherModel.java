package dev.plataclan.client;

import java.util.ArrayList;
import java.util.List;

import dev.plataclan.mother.MotherMove;
import dev.villagefriends.animation.AnimationClip;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.util.Mth;

/**
 * The Silver Mother: her children's mesh, her own costume (tools/plata/mother.py). Vanilla's humanoid walk (a slow,
 * stately version of it), then her clips ({@link PlataPoser}, from plata_moves/mother.json), then the parts that move
 * on their own: her four feelers, the tabard over her legs, and her train, which hangs from her shoulders and lies
 * along the floor behind her whatever she does with her body.
 */
public final class MotherModel extends HumanoidModel<MotherRenderState> {
    private static final String GEO = MotherRenderer.ID;
    private final ModelPart[] antennaR, antennaL, feelerR, feelerL, train, cerci;
    private final ModelPart tabard;
    private final float[] trainRest;

    public MotherModel(ModelPart root) {
        super(root);
        this.antennaR = chain(root, "antenna_r_", 1);
        this.antennaL = chain(root, "antenna_l_", 1);
        this.feelerR = chain(root, "feeler_r_", 1);
        this.feelerL = chain(root, "feeler_l_", 1);
        this.train = chain(root, "train_", 1);
        this.cerci = new ModelPart[]{Geometry.part(root, GEO, "cercus_r"), Geometry.part(root, GEO, "cercus_m"), Geometry.part(root, GEO, "cercus_l")};
        this.tabard = Geometry.part(root, GEO, "tabard");
        this.trainRest = new float[this.train.length];
        for (int i = 0; i < this.train.length; i++) this.trainRest[i] = this.train[i].getInitialPose().xRot();
    }

    private static ModelPart[] chain(ModelPart root, String prefix, int first) {
        List<ModelPart> parts = new ArrayList<>();
        for (int i = first; ; i++) {
            ModelPart p = Geometry.optional(root, GEO, prefix + i);
            if (p == null) break;
            parts.add(p);
        }
        return parts.toArray(ModelPart[]::new);
    }

    @Override
    public void setupAnim(MotherRenderState s) {
        super.setupAnim(s);
        // she walks, she doesn't stride: a shorter swing of the legs and arms than vanilla's
        this.rightLeg.xRot *= .5F; this.leftLeg.xRot *= .5F;
        this.rightArm.xRot *= .4F; this.leftArm.xRot *= .4F;
        PlataPoser.begin();
        if (s.dying >= 0) {
            PlataPoser.add(PlataClips.mother("defeat"), s.dying, 1);
        } else if (s.move == MotherMove.DORMANT) {
            AnimationClip curl = PlataClips.mother("curl");
            if (curl != null) PlataPoser.add(curl, (s.ageInTicks / 20F) % curl.length(), 1);
        } else {
            AnimationClip clip = s.move == MotherMove.STANCE || s.move.clip == null ? null : PlataClips.mother(s.move.clip);
            float w = 0;
            if (clip != null) w = s.move == MotherMove.RISE ? fadeOutOnly(clip, s.moveTime) : clip.envelope(s.moveTime);
            AnimationClip stance = PlataClips.mother("stance");
            if (stance != null) PlataPoser.add(stance, (s.ageInTicks / 20F) % stance.length(), (1 - w) * (1 - .6F * s.speed));
            if (clip != null) PlataPoser.add(clip, s.moveTime, w);
        }
        PlataPoser.apply(this);
        this.loose(s);
    }

    /** Rising starts exactly where her sleep left off (curled up), so it can't fade in from standing. */
    private static float fadeOutOnly(AnimationClip clip, float t) {
        if (t < 0 || t > clip.length()) return 0;
        return clip.blendOut() <= 0 ? 1 : AnimationClip.smooth((clip.length() - t) / clip.blendOut());
    }

    private void loose(MotherRenderState s) {
        float t = s.ageInTicks, run = s.speed;
        float flare = s.phase >= 3 ? .18F : s.phase == 2 ? .08F : 0;
        for (int i = 0; i < this.antennaR.length; i++) {
            float k = i == 0 ? 1 : .5F;
            this.antennaR[i].xRot += Mth.sin(t * .09F + i * .7F) * .06F + run * .2F * k;
            this.antennaR[i].zRot += Mth.sin(t * .08F + i) * .04F - (i == 0 ? flare : 0);
            this.antennaL[i].xRot += Mth.sin(t * .09F + i * .7F + 1.3F) * .06F + run * .2F * k;
            this.antennaL[i].zRot -= Mth.sin(t * .08F + i + 1.3F) * .04F - (i == 0 ? flare : 0);
        }
        for (int i = 0; i < this.feelerR.length; i++) {
            this.feelerR[i].xRot += Mth.sin(t * .12F + i) * .08F;
            this.feelerL[i].xRot += Mth.sin(t * .12F + i + 2F) * .08F;
        }
        // the tabard swings out with whichever leg is forward, and hangs straight when she bends
        float forward = Math.min(0, Math.min(this.rightLeg.xRot, this.leftLeg.xRot));
        this.tabard.xRot = forward * .9F - Math.max(0, this.body.xRot) * .6F;
        // the train hangs from her shoulders: undo most of the body's lean, then lay the rest along the floor
        float lean = this.root().xRot + this.body.xRot;
        if (this.train.length > 0) {
            this.train[0].xRot = this.trainRest[0] - lean * .65F + run * .25F;
            this.train[0].zRot = Mth.sin(t * .07F) * .03F;
            float hang = lean + this.train[0].xRot;   // its angle from straight down
            if (this.train.length > 1) this.train[1].xRot = Mth.clamp(1.47F - hang, .2F, 1.6F);
            for (int i = 2; i < this.train.length; i++) {
                this.train[i].xRot = this.trainRest[i];
                this.train[i].zRot = Mth.sin(t * .1F - i * .8F) * (.04F + run * .1F);
            }
        }
        for (int i = 0; i < this.cerci.length; i++) {
            this.cerci[i].zRot = this.cerci[i].getInitialPose().zRot() + Mth.sin(t * .13F + i * 1.7F) * (.06F + run * .15F);
        }
    }
}

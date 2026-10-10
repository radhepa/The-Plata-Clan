package dev.plataclan.client;

import java.util.ArrayList;
import java.util.List;

import dev.plataclan.entity.Move;
import dev.villagefriends.animation.AnimationClip;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.util.Mth;

/**
 * A Plata: the player's own mesh (the body Village Friends draws its residents on) wearing the clan's
 * costume. Vanilla's humanoid walk, then the clan's clips ({@link PlataPoser}), then the loose parts
 * that move on their own: feelers, the three tail cords, the Warden's cape and the Silvertail whip.
 */
public final class PlataModel extends HumanoidModel<PlataRenderState> {
    private final ModelPart[] antennaR, antennaL, tailR, tailM, tailL, cape, whip;

    public PlataModel(ModelPart root, String geometry) {
        super(root);
        this.antennaR = chain(root, geometry, "antenna_r_", 1);
        this.antennaL = chain(root, geometry, "antenna_l_", 1);
        this.tailR = chain(root, geometry, "tail_r_", 1);
        this.tailM = chain(root, geometry, "tail_m_", 1);
        this.tailL = chain(root, geometry, "tail_l_", 1);
        this.cape = chain(root, geometry, "cape_", 1);
        this.whip = chain(root, geometry, "whip_", 0);
    }

    /** prefix + first, prefix + (first + 1)... for as long as the model has them. */
    private static ModelPart[] chain(ModelPart root, String geometry, String prefix, int first) {
        List<ModelPart> parts = new ArrayList<>();
        for (int i = first; ; i++) {
            ModelPart p = Geometry.optional(root, geometry, prefix + i);
            if (p == null) break;
            parts.add(p);
        }
        return parts.toArray(ModelPart[]::new);
    }

    @Override
    public void setupAnim(PlataRenderState s) {
        super.setupAnim(s);
        PlataPoser.begin();
        if (s.dying >= 0) {
            PlataPoser.add(PlataClips.move("defeat"), s.dying, 1);
        } else {
            AnimationClip clip = s.move == Move.STANCE ? null : PlataClips.move(s.move.clip);
            float w = 0;
            if (clip != null) w = s.move == Move.UNFURL ? fadeOutOnly(clip, s.moveTime) : clip.envelope(s.moveTime);
            AnimationClip stance = PlataClips.move("stance");
            if (stance != null) PlataPoser.add(stance, (s.ageInTicks / 20F) % stance.length(), (1 - w) * (1 - .55F * s.speed));
            if (clip != null) PlataPoser.add(clip, s.moveTime, w);
        }
        PlataPoser.apply(this);
        this.loose(s);
    }

    /** The entrance starts exactly where the Molt left off (curled up), so it can't fade in from standing. */
    private static float fadeOutOnly(AnimationClip clip, float t) {
        if (t < 0 || t > clip.length()) return 0;
        return clip.blendOut() <= 0 ? 1 : AnimationClip.smooth((clip.length() - t) / clip.blendOut());
    }

    private void loose(PlataRenderState s) {
        float t = s.ageInTicks, run = s.speed;
        for (int i = 0; i < this.antennaR.length; i++) {
            float k = i == 0 ? 1 : .55F;
            this.antennaR[i].xRot += Mth.sin(t * .13F + i * .7F) * .07F + run * .22F * k;
            this.antennaR[i].zRot += Mth.sin(t * .11F + i) * .05F;
        }
        for (int i = 0; i < this.antennaL.length; i++) {
            float k = i == 0 ? 1 : .55F;
            this.antennaL[i].xRot += Mth.sin(t * .13F + i * .7F + 1.3F) * .07F + run * .22F * k;
            this.antennaL[i].zRot -= Mth.sin(t * .11F + i + 1.3F) * .05F;
        }
        sway(this.tailR, t, run, 0F);
        sway(this.tailM, t, run, 1.1F);
        sway(this.tailL, t, run, 2.3F);
        for (int i = 0; i < this.cape.length; i++) this.cape[i].xRot += run * (i == 0 ? .55F : .25F) + Mth.sin(t * .09F + i) * .03F;
        if (this.whip.length > 1) {
            float straight = whipStraight(s);
            for (int i = 1; i < this.whip.length; i++) {
                ModelPart seg = this.whip[i];
                float sway = Mth.sin(t * .17F - i * .6F) * .05F * (1 - straight);
                seg.xRot = seg.xRot * (1 - straight) + sway + run * .08F * (1 - straight);
                seg.zRot = seg.zRot * (1 - straight);
                seg.yRot = seg.yRot * (1 - straight) + (s.move == Move.LASH ? -.06F * straight * i : 0);
            }
        }
    }

    /** Cords hang and trail behind at a run, each a beat behind the one above. */
    private static void sway(ModelPart[] chain, float t, float run, float phase) {
        for (int i = 0; i < chain.length; i++) {
            chain[i].xRot += run * (i == 0 ? .8F : .35F) + Mth.sin(t * .2F + i * .8F + phase) * .06F;
            chain[i].zRot += Mth.sin(t * .15F + phase + i * .5F) * .07F;
        }
    }

    /** The Silvertail snaps straight for the lash and the slam, and hangs coiled the rest of the time. */
    private static float whipStraight(PlataRenderState s) {
        float m = s.moveTime;
        return switch (s.move) {
            case LASH -> m < 1.0F ? AnimationClip.smooth((m - .55F) / .15F) : 1 - AnimationClip.smooth((m - 1.0F) / .25F);
            case SLAM -> m < .9F ? AnimationClip.smooth((m - .3F) / .3F) : 1 - AnimationClip.smooth((m - .9F) / .4F);
            default -> 0;
        };
    }
}

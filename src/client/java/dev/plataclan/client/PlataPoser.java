package dev.plataclan.client;

import dev.villagefriends.animation.AnimationClip;
import dev.villagefriends.animation.AnimationClip.Bone;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.util.Mth;
import org.joml.Matrix3f;
import org.joml.Vector3f;

/**
 * Adds clips in Village Friends' format on top of a humanoid's live pose, by the same rules as Village
 * Friends' resident poser: rotations in degrees, positions in model pixels, the waist bending the
 * upper body at the hips and the root pivoting the whole body at the feet. Single threaded; the
 * scratch buffers are reused so posing allocates nothing.
 */
public final class PlataPoser {
    private static final int BONES = Bone.ALL.length;
    private static final float[] ROT = new float[BONES * 3], POS = new float[BONES * 3], SAMPLE = new float[3];
    private static final Matrix3f TURN = new Matrix3f(), PART = new Matrix3f();
    private static final Vector3f POINT = new Vector3f(), EULER = new Vector3f();
    private static boolean any;

    private PlataPoser() {}

    public static void begin() {
        java.util.Arrays.fill(ROT, 0);
        java.util.Arrays.fill(POS, 0);
        any = false;
    }

    /** Accumulate one clip at a time (seconds) and weight. */
    public static void add(AnimationClip clip, float time, float weight) {
        if (clip == null || weight <= 0) return;
        any = true;
        for (Bone bone : Bone.ALL) {
            var rotation = clip.rotations()[bone.ordinal()];
            var position = clip.positions()[bone.ordinal()];
            int i = bone.ordinal() * 3;
            if (rotation != null) {
                rotation.sample(time, SAMPLE, 0);
                ROT[i] += SAMPLE[0] * weight; ROT[i + 1] += SAMPLE[1] * weight; ROT[i + 2] += SAMPLE[2] * weight;
            }
            if (position != null) {
                position.sample(time, SAMPLE, 0);
                POS[i] += SAMPLE[0] * weight; POS[i + 1] += SAMPLE[1] * weight; POS[i + 2] += SAMPLE[2] * weight;
            }
        }
    }

    /** Pose the model with everything accumulated since {@link #begin}. */
    public static void apply(HumanoidModel<?> model) {
        if (!any) return;
        offset(model.head, Bone.HEAD); offset(model.body, Bone.BODY);
        offset(model.rightArm, Bone.RIGHT_ARM); offset(model.leftArm, Bone.LEFT_ARM);
        offset(model.rightLeg, Bone.RIGHT_LEG); offset(model.leftLeg, Bone.LEFT_LEG);
        if (turns(Bone.WAIST)) {
            float hip = (model.rightLeg.getInitialPose().y() + model.leftLeg.getInitialPose().y()) / 2;
            rotation(Bone.WAIST);
            pivot(model.body, 0, hip, 0); pivot(model.head, 0, hip, 0);
            pivot(model.rightArm, 0, hip, 0); pivot(model.leftArm, 0, hip, 0);
        }
        if (turns(Bone.ROOT)) { rotation(Bone.ROOT); pivot(model.root(), 0, 24, 0); }
        int r = Bone.ROOT.ordinal() * 3;
        model.root().x += POS[r]; model.root().y += POS[r + 1]; model.root().z += POS[r + 2];
    }

    private static boolean turns(Bone bone) {
        int i = bone.ordinal() * 3;
        return ROT[i] != 0 || ROT[i + 1] != 0 || ROT[i + 2] != 0;
    }

    private static void offset(ModelPart part, Bone bone) {
        int i = bone.ordinal() * 3;
        part.xRot += ROT[i] * Mth.DEG_TO_RAD; part.yRot += ROT[i + 1] * Mth.DEG_TO_RAD; part.zRot += ROT[i + 2] * Mth.DEG_TO_RAD;
        part.x += POS[i]; part.y += POS[i + 1]; part.z += POS[i + 2];
    }

    private static void rotation(Bone bone) {
        int i = bone.ordinal() * 3;
        TURN.rotationZYX(ROT[i + 2] * Mth.DEG_TO_RAD, ROT[i + 1] * Mth.DEG_TO_RAD, ROT[i] * Mth.DEG_TO_RAD);
    }

    /** Turns a part about a fixed point in its parent's space, composing with its own rotation. */
    private static void pivot(ModelPart part, float px, float py, float pz) {
        POINT.set(part.x - px, part.y - py, part.z - pz); TURN.transform(POINT);
        part.x = POINT.x + px; part.y = POINT.y + py; part.z = POINT.z + pz;
        PART.rotationZYX(part.zRot, part.yRot, part.xRot);
        TURN.mul(PART, PART).getEulerAnglesZYX(EULER);
        part.setRotation(EULER.x, EULER.y, EULER.z);
    }
}

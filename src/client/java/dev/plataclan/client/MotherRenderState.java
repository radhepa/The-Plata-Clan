package dev.plataclan.client;

import dev.plataclan.mother.MotherMove;
import net.minecraft.client.renderer.entity.state.HumanoidRenderState;

/** What the Silver Mother looks like this frame. */
public final class MotherRenderState extends HumanoidRenderState {
    public MotherMove move = MotherMove.STANCE;
    /** Seconds into the current move. */
    public float moveTime;
    /** Under the floor: nothing to draw. */
    public boolean hidden;
    /** Seconds since she started dying (the defeat clip), or -1. */
    public float dying = -1;
    /** 0 standing, 1 at her fastest stride: trails the train and the feelers. */
    public float speed;
    /** 1, 2 after she sheds her shell, 3 after her last molt. */
    public int phase = 1;
}

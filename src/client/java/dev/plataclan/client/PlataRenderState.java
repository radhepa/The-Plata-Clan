package dev.plataclan.client;

import dev.plataclan.Rank;
import dev.plataclan.entity.Move;
import net.minecraft.client.renderer.entity.state.HumanoidRenderState;

/** What a Plata looks like this frame. */
public final class PlataRenderState extends HumanoidRenderState {
    public Rank rank = Rank.SCUTTLER;
    public Move move = Move.STANCE;
    /** Seconds into the current move. */
    public float moveTime;
    /** Under the ground: nothing to draw. */
    public boolean hidden;
    /** Seconds since they started dying (the defeat clip), or -1. */
    public float dying = -1;
    /** 0 at rest, 1 at a run: trails the antennae, tails and cape. */
    public float speed;
}

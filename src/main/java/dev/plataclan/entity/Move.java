package dev.plataclan.entity;

/**
 * What a Plata is doing, synced to clients, each with the clip it plays (from
 * {@code assets/plataclan/plata_moves/plata.json}) and its length in ticks. The server times hits to
 * the same beats the clips are keyed for.
 */
public enum Move {
    STANCE("stance", 40),
    READY("ready", 8),
    FLURRY_1("flurry_1", 9), FLURRY_2("flurry_2", 9), FLURRY_3("flurry_3", 9), FLURRY_4("flurry_4", 10),
    LUNGE("lunge", 18),
    BACKFLIP("backflip", 14),
    SIDEROLL("sideroll", 12),
    BURROW_DIVE("burrow_dive", 12),
    /** Travelling under the ground: nothing to see but a trail of grit. */
    UNDER(null, 100),
    /** Stopped under someone: the ground shakes before the eruption. */
    TREMOR(null, 12),
    EMERGE("emerge", 18),
    THROW_FAN("throw_fan", 16),
    KICK("kick", 12),
    LASH("lash", 26),
    SLAM("slam", 28),
    CALL("call", 24),
    STAGGER("stagger", 20),
    TAUNT("taunt", 32),
    UNFURL("unfurl", 22),
    DEFEAT("defeat", 28);

    public static final Move[] ALL = values();

    /** Clip id in the plata_moves pack, or null for moves with nothing to draw. */
    public final String clip;
    public final int ticks;

    Move(String clip, int ticks) { this.clip = clip; this.ticks = ticks; }

    public boolean hidden() { return this == UNDER || this == TREMOR; }
    public boolean flurry() { return this == FLURRY_1 || this == FLURRY_2 || this == FLURRY_3 || this == FLURRY_4; }

    public static Move flurry(int n) {
        return switch (n) { case 1 -> FLURRY_1; case 2 -> FLURRY_2; case 3 -> FLURRY_3; default -> FLURRY_4; };
    }

    public static Move byOrdinal(int i) { return i >= 0 && i < ALL.length ? ALL[i] : STANCE; }
}

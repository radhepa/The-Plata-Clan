package dev.plataclan.mother;

/**
 * What the Silver Mother is doing, synced to clients, each with its clip in
 * {@code assets/plataclan/plata_moves/mother.json} and its length in ticks. The server lands every blow on the beat
 * the clip is keyed for (see the {@code *_AT} constants in {@link SilverMother}).
 */
public enum MotherMove {
    /** Asleep, curled up on her dais. */
    DORMANT("curl", 80),
    RISE("rise", 50),
    STANCE("stance", 48),
    /** Both gauntlets into the floor; stuck there (and open to anything) for a second after. */
    CRUSH("crush", 48),
    /** A full turn: the train whips round her. */
    SWEEP("sweep", 32),
    /** The Silver Keen: a shriek in a cone in front of her that goes through armor. */
    KEEN("keen", 44),
    /** A handful of scales flung high; they come down round whoever she fights. */
    RAIN("rain", 32),
    /** Palms to the floor: her children wake, and silverfish come up out of the stone. */
    BROOD("brood", 32),
    BURROW("burrow", 20),
    /** Travelling under the floor: nothing to see but a trail of grit. */
    UNDER(null, 120),
    /** Under someone: a wide ring of floor shakes before she comes up. */
    TREMOR(null, 22),
    ERUPT("erupt", 24),
    /** Between phases: she bursts out of her old shell. */
    SHED("shed", 52),
    STAGGER("stagger", 28),
    DEFEAT("defeat", 60);

    public static final MotherMove[] ALL = values();

    public final String clip;
    public final int ticks;

    MotherMove(String clip, int ticks) { this.clip = clip; this.ticks = ticks; }

    public boolean hidden() { return this == UNDER || this == TREMOR; }

    public static MotherMove byOrdinal(int i) { return i >= 0 && i < ALL.length ? ALL[i] : STANCE; }
}

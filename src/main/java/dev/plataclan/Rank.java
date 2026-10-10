package dev.plataclan;

/** The three ranks of the clan a disguised traveler can turn out to be. */
public enum Rank {
    /** The footsoldier: twin fang knives, flurries, lunges and backflips. */
    SCUTTLER("scuttler", "Scuttler"),
    /** The skirmisher: throws fans of silver shards and keeps you at arm's length. */
    SHARDCASTER("shardcaster", "Shardcaster"),
    /** The elder enforcer: the Silvertail whip and a slam that shakes silverfish out of the ground. */
    WARDEN("warden", "Molt Warden");

    public final String id, title;

    Rank(String id, String title) { this.id = id; this.title = title; }

    public static Rank byId(String id) {
        for (Rank r : values()) if (r.id.equals(id)) return r;
        return SCUTTLER;
    }
}

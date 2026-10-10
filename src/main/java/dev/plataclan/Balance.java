package dev.plataclan;

/**
 * Every number of the clan, pure and unit-tested (BalanceTest). A Plata's level comes from the player
 * who found them; their health and damage come from that player's own numbers, so the fight is a
 * challenge at every stage of the game and never a slaughter either way:
 * <ul>
 *   <li>health = the player's expected melee hit x a hits-to-kill budget for the rank;</li>
 *   <li>damage is solved so that, after the player's armor and toughness (and half of their
 *       Protection), each kind of hit takes a set share of the player's max health.</li>
 * </ul>
 * The level also decides how cleverly they fight (dodges, combo length, cooldowns, reactions).
 */
public final class Balance {
    private Balance() {}

    public static final int MAX_LEVEL = 100;
    public static final float MIN_HEALTH = 20, MAX_HEALTH = 1024;

    /** What the clan reads off the player at the reveal. */
    public record Foe(int level, float maxHealth, float armor, float toughness, float protection, float meleeHit) {}

    /** Kinds of hit a Plata can land, each aimed at a share of the player's max health. */
    public enum Hit {
        CUT(.07F), LUNGE(.09F), KICK(.06F), SHARD(.05F), LASH(.13F), SLAM(.10F), ERUPT(.11F);
        public final float share;
        Hit(float share) { this.share = share; }
    }

    /**
     * The power level of a player without the RPG add-on, on the RPG's 1–100 scale, from what they
     * wear and wield: a fresh player is 1, leather and a stone sword about 17, full iron about 29,
     * diamond about 49, enchanted netherite 65–75.
     */
    public static int estimateLevel(float armor, float toughness, float meleeHit, float maxHealth, int xpLevel) {
        double defense = armor * 1.6 + toughness * 2.0;
        double offense = Math.max(0, meleeHit - 1) * 2.4;
        double vigor = Math.max(0, maxHealth - 20);
        double experience = Math.min(30, Math.max(0, xpLevel)) * .25;
        return clampLevel((int) Math.round(1 + defense * .62 + offense * .9 + vigor + experience));
    }

    public static int clampLevel(int level) { return Math.clamp(level, 1, MAX_LEVEL); }

    /** The member's own level: the player's, give or take two (from the seed), three more for a Warden. */
    public static int memberLevel(int playerLevel, Rank rank, long seed) {
        int jitter = (int) Math.floorMod(seed ^ (seed >>> 17), 5L) - 2;
        return clampLevel(playerLevel + jitter + (rank == Rank.WARDEN ? 3 : 0));
    }

    /** Which rank a disguise hides, from a roll in [0, 1). Wardens only from level 20. */
    public static Rank rankFor(double roll, int playerLevel) {
        if (roll < .60) return Rank.SCUTTLER;
        if (roll < .85) return Rank.SHARDCASTER;
        return playerLevel >= 20 ? Rank.WARDEN : Rank.SCUTTLER;
    }

    /** Hits of the player's melee it takes to bring a member down. */
    public static float hitsToKill(Rank rank, int level) {
        return switch (rank) {
            case SCUTTLER -> 10 + .06F * level;
            case SHARDCASTER -> 8 + .05F * level;
            case WARDEN -> 16 + .08F * level;
        };
    }

    /** Max health: the budget times the player's hit, +60% per extra player in the fight. */
    public static float health(Rank rank, int level, float meleeHit, int players, float scale) {
        float hit = Math.max(3, meleeHit);
        float group = 1 + .6F * Math.max(0, players - 1);
        return Math.clamp(hit * hitsToKill(rank, level) * group * scale, MIN_HEALTH, MAX_HEALTH);
    }

    /** Vanilla's armor formula (CombatRules.getDamageAfterAbsorb without armor-breaking weapons). */
    public static float afterArmor(float damage, float armor, float toughness) {
        float t = 2 + toughness / 4;
        float real = Math.clamp(armor - damage / t, armor * .2F, 20F);
        return damage * (1 - real / 25);
    }

    /** What the player actually takes from a raw hit: armor, then half of their Protection. */
    public static float taken(float damage, float armor, float toughness, float protection) {
        float half = Math.clamp(protection, 0, 20) / 25 * .5F;
        return afterArmor(damage, armor, toughness) * (1 - half);
    }

    /** The raw damage for a kind of hit, solved so the player takes its share of their max health. */
    public static float damage(Hit hit, Foe foe, int level, float scale) {
        float share = (hit.share + .0001F * level) * scale;
        float want = Math.max(.5F, foe.maxHealth() * share);
        float lo = 0, hi = 400;
        if (taken(hi, foe.armor(), foe.toughness(), foe.protection()) < want) return hi;
        for (int i = 0; i < 40; i++) {
            float mid = (lo + hi) / 2;
            if (taken(mid, foe.armor(), foe.toughness(), foe.protection()) < want) lo = mid; else hi = mid;
        }
        return hi;
    }

    // -- how cleverly they fight -------------------------------------------------------------------
    public static float dodgeChance(int level) { return Math.min(.27F, .12F + .0015F * level); }
    public static int flurryLength(int level) { return level >= 70 ? 4 : level >= 30 ? 3 : 2; }
    public static int burrowCooldown(int level) { return Math.round(280 - 1.4F * level); }
    public static int reaction(int level) { return Math.max(6, Math.round(16 - .1F * level)); }
    public static int swarm(int level) { return 1 + (level >= 40 ? 1 : 0) + (level >= 75 ? 1 : 0); }
    public static int shardsPerFan(int level) { return level >= 40 ? 5 : 3; }
    public static boolean molts(int level) { return level >= 15; }
    public static boolean callsSilverfish(Rank rank, int level) { return rank == Rank.WARDEN || rank == Rank.SCUTTLER && level >= 20; }

    public static double speed(Rank rank) {
        return switch (rank) { case SCUTTLER -> .33; case SHARDCASTER -> .31; case WARDEN -> .27; };
    }
    public static double armor(Rank rank, int level) { return 2 + .06 * level + (rank == Rank.WARDEN ? 4 : 0); }
    public static double knockbackResistance(Rank rank) { return rank == Rank.WARDEN ? .6 : .1; }
    public static int experience(int level) { return 10 + level / 2; }

    // -- loot ----------------------------------------------------------------------------------------
    public static int emeralds(int level, int roll3) { return 1 + Math.floorMod(roll3, 3) + level / 20; }
    public static int scales(int level, int roll3) { return 2 + Math.floorMod(roll3, 3) + level / 25; }
    public static double fangChance(int level) { return .08 + .001 * level; }
    public static double maskChance(Rank rank, int level) { return (.025 + .0005 * level) * (rank == Rank.WARDEN ? 2 : 1); }

    // -- hideouts ------------------------------------------------------------------------------------
    /** How close a stranger in sight has to come to wake a sleeper (an elder sleeps lighter; sneaking halves it). */
    public static final double WAKE_RANGE = 7, ELDER_WAKE_RANGE = 10;
    /** Never more than this many sleepers up at once around a husk; the rest wait their turn. */
    public static final int AWAKE_CAP = 3;

    public static double wakeRange(boolean elder, boolean sneaking) {
        double r = elder ? ELDER_WAKE_RANGE : WAKE_RANGE;
        return sneaking ? r / 2 : r;
    }

    /** Sleepers wake groggy, on 70% of a member's health; the elder guarding a vault is its boss and wakes whole. */
    public static float garrisonHealth(boolean elder) { return elder ? 1F : .7F; }

    /** Sleepers are footsoldiers (two Scuttlers in three, else a Shardcaster); an elder is a Molt Warden from level 20. */
    public static Rank sleeperRank(double roll, int playerLevel, boolean elder) {
        if (elder) return playerLevel >= 20 ? Rank.WARDEN : Rank.SHARDCASTER;
        return roll < .67 ? Rank.SCUTTLER : Rank.SHARDCASTER;
    }

    // -- encounters ----------------------------------------------------------------------------------
    /** Chance per check (every {@code checkSeconds}) for an average gap of {@code minutes}. */
    public static double encounterChance(double minutes, double checkSeconds, double biome, int silverfishKilled) {
        if (minutes <= 0 || biome <= 0) return 0;
        double grudge = 1 + Math.min(1, silverfishKilled / 200.0);
        double rate = checkSeconds / (minutes * 60) * biome * grudge;
        return 1 - Math.exp(-rate);
    }
}

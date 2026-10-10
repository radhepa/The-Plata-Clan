package dev.plataclan;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class BalanceTest {
    @Test
    void powerLevelsClimbWithGear() {
        int fresh = Balance.estimateLevel(0, 0, 1, 20, 0);
        int leather = Balance.estimateLevel(7, 0, 5, 20, 3);
        int iron = Balance.estimateLevel(15, 0, 6, 20, 10);
        int diamond = Balance.estimateLevel(20, 8, 7, 20, 20);
        int netherite = Balance.estimateLevel(20, 12, 11, 20, 30);
        assertEquals(1, fresh);
        assertTrue(leather > fresh && iron > leather && diamond > iron && netherite > diamond,
                fresh + " " + leather + " " + iron + " " + diamond + " " + netherite);
        assertTrue(iron >= 25 && iron <= 35, "iron " + iron);
        assertTrue(diamond >= 42 && diamond <= 56, "diamond " + diamond);
        assertTrue(netherite >= 60 && netherite <= 80, "netherite " + netherite);
        assertEquals(100, Balance.estimateLevel(40, 30, 40, 80, 100));
    }

    @Test
    void memberLevelsStayNearThePlayer() {
        for (long seed = 0; seed < 500; seed++) {
            int l = Balance.memberLevel(40, Rank.SCUTTLER, seed * 7919);
            assertTrue(l >= 38 && l <= 42, "level " + l);
            int w = Balance.memberLevel(40, Rank.WARDEN, seed * 7919);
            assertTrue(w >= 41 && w <= 45, "warden " + w);
        }
        assertEquals(1, Balance.memberLevel(1, Rank.SCUTTLER, 0));
        assertTrue(Balance.memberLevel(100, Rank.WARDEN, 3) <= 100);
    }

    @Test
    void wardensWaitForLevelTwenty() {
        assertEquals(Rank.SCUTTLER, Balance.rankFor(.1, 50));
        assertEquals(Rank.SHARDCASTER, Balance.rankFor(.7, 50));
        assertEquals(Rank.WARDEN, Balance.rankFor(.9, 50));
        assertEquals(Rank.SCUTTLER, Balance.rankFor(.9, 19));
    }

    @Test
    void healthIsAHitBudget() {
        // A diamond-sword player (7) needs about 10–13 hits for a Scuttler at level 40.
        float hp = Balance.health(Rank.SCUTTLER, 40, 7, 1, 1);
        float hits = hp / 7;
        assertTrue(hits > 10 && hits < 14, "hits " + hits);
        assertTrue(Balance.health(Rank.WARDEN, 40, 7, 1, 1) > hp);
        assertTrue(Balance.health(Rank.SHARDCASTER, 40, 7, 1, 1) < hp);
        // Two players: +60%. Bounds hold.
        assertEquals(hp * 1.6F, Balance.health(Rank.SCUTTLER, 40, 7, 2, 1), .01F);
        assertEquals(Balance.MIN_HEALTH, Balance.health(Rank.SHARDCASTER, 1, 1, 1, .1F));
        assertEquals(Balance.MAX_HEALTH, Balance.health(Rank.WARDEN, 100, 200, 4, 1));
    }

    @Test
    void armorFormulaMatchesVanilla() {
        // No armor: everything goes through. 20 armor, 0 toughness vs a 5 hit: armor 17.5 -> 70% blocked.
        assertEquals(10, Balance.afterArmor(10, 0, 0), 1e-4);
        assertEquals(5 * (1 - 17.5F / 25), Balance.afterArmor(5, 20, 0), 1e-4);
        // Armor never drops below 20% of itself.
        assertEquals(100 * (1 - 4F / 25), Balance.afterArmor(100, 20, 0), 1e-3);
    }

    @Test
    void damageTakesTheShareOfMaxHealthWhateverTheGear() {
        Balance.Foe naked = new Balance.Foe(1, 20, 0, 0, 0, 1);
        Balance.Foe diamond = new Balance.Foe(50, 20, 20, 8, 0, 7);
        Balance.Foe protectedNetherite = new Balance.Foe(70, 20, 20, 12, 16, 11);
        for (Balance.Foe f : new Balance.Foe[]{naked, diamond, protectedNetherite}) {
            for (Balance.Hit h : Balance.Hit.values()) {
                float raw = Balance.damage(h, f, f.level(), 1);
                float taken = Balance.taken(raw, f.armor(), f.toughness(), f.protection());
                float want = Math.max(.5F, f.maxHealth() * (h.share + .0001F * f.level()));
                assertEquals(want, taken, .02F, h + " vs " + f);
            }
        }
        // Better armor means the raw hit is bigger, but what lands is the same share.
        assertTrue(Balance.damage(Balance.Hit.CUT, diamond, 50, 1) > Balance.damage(Balance.Hit.CUT, naked, 50, 1));
        // A Scuttler cut never takes more than a tenth of a full-health player.
        assertTrue(Balance.taken(Balance.damage(Balance.Hit.CUT, naked, 100, 1), 0, 0, 0) <= 2.0F);
    }

    @Test
    void smarterWithLevel() {
        assertEquals(2, Balance.flurryLength(1));
        assertEquals(3, Balance.flurryLength(30));
        assertEquals(4, Balance.flurryLength(70));
        assertTrue(Balance.dodgeChance(100) <= .27F && Balance.dodgeChance(1) >= .12F);
        assertTrue(Balance.burrowCooldown(100) < Balance.burrowCooldown(1));
        assertEquals(280 - 1, Balance.burrowCooldown(1), 1);
        assertEquals(6, Balance.reaction(100));
        assertEquals(3, Balance.swarm(80));
        assertEquals(5, Balance.shardsPerFan(40));
    }

    @Test
    void encountersAverageOutToTheConfiguredGap() {
        double p = Balance.encounterChance(25, 30, 1, 0);
        // One check every 30 s: expected gap = 30 s / p, about 25 minutes.
        assertEquals(25 * 60, 30 / p, 40);
        assertTrue(Balance.encounterChance(25, 30, 2, 0) > p);
        assertTrue(Balance.encounterChance(25, 30, 1, 400) > Balance.encounterChance(25, 30, 1, 100));
        assertEquals(0, Balance.encounterChance(25, 30, 0, 0));
    }
}

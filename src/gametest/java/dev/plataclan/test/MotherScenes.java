package dev.plataclan.test;

import static dev.plataclan.test.HideoutClientTest.center;
import static dev.plataclan.test.HideoutClientTest.fly;
import static dev.plataclan.test.HideoutClientTest.husks;
import static dev.plataclan.test.HideoutClientTest.look;
import static dev.plataclan.test.PlataClientTest.Y;
import static dev.plataclan.test.PlataClientTest.check;
import static dev.plataclan.test.PlataClientTest.clear;
import static dev.plataclan.test.PlataClientTest.disguise;
import static dev.plataclan.test.PlataClientTest.hud;
import static dev.plataclan.test.PlataClientTest.members;
import static dev.plataclan.test.PlataClientTest.onServer;
import static dev.plataclan.test.PlataClientTest.player;
import static dev.plataclan.test.PlataClientTest.shoot;

import java.util.List;

import dev.plataclan.Balance;
import dev.plataclan.PlataClan;
import dev.plataclan.PlataLines;
import dev.plataclan.client.RevealHud;
import dev.plataclan.client.TruceScreen;
import dev.plataclan.disguise.Reveal;
import dev.plataclan.entity.PlataMember;
import dev.plataclan.item.PlataItems;
import dev.plataclan.mother.MotherMove;
import dev.plataclan.mother.Mothers;
import dev.plataclan.mother.SilverMother;
import dev.villagefriends.ActionPayload;
import dev.villagefriends.VillageFriends;
import dev.villagefriends.client.FriendshipScreen;
import net.fabricmc.fabric.api.attachment.v1.AttachmentTarget;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.monster.Silverfish;
import net.minecraft.world.entity.npc.villager.Villager;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.Vec3;

/**
 * The Silver Mother, in a real Warren (Mother: she sleeps on her dais, rises for a survival player, lands her blows at
 * their shares of the player's health, sheds her shell twice, dies, and her Warren goes quiet), and what the clan makes
 * of her killer afterwards out in the wild (Slayer: they know; some let you be and talk, some avenge her).
 *   gradlew runClientGameTest -PplataTests=Mother,Slayer
 */
@SuppressWarnings("UnstableApiUsage")
final class MotherScenes {
    private MotherScenes() {}

    // -- the Mother in her vault ------------------------------------------------------------------------

    static SilverMother mother(ServerLevel level, BoundingBox vault) {
        List<SilverMother> found = level.getEntitiesOfClass(SilverMother.class, AABB.of(vault).inflate(4));
        return found.isEmpty() ? null : found.getFirst();
    }

    static void mother(ClientGameTestContext ctx, TestSingleplayerContext w, HideoutClientTest.Site site) {
        BoundingBox vault = site.piece("warren/vault");
        check(vault != null, "the Warren has its vault: " + site.pieces().keySet());
        fly(w);
        w.getServer().runCommand("effect clear @a");
        Vec3 c = center(vault);
        // from the stair's foot, the Hollow and the dais at the far end (the seat puts her there as we come near)
        look(ctx, w, new Vec3(c.x, vault.minY() + 5.5, vault.minZ() + 17.5), new Vec3(c.x, vault.minY() + 2, vault.maxZ() - 4), 30);
        SilverMother m = null;
        for (int i = 0; i < 40 && (m = onServer(w, l -> mother(l, vault))) == null; i++) ctx.waitTicks(5);
        check(m != null, "a Silver Mother sleeps in the Warren's vault");
        int id = m.getId();
        String[] name = onServer(w, l -> {
            SilverMother mm = (SilverMother) l.getEntity(id);
            check(mm.dormant(), "asleep on her dais: " + mm.move());
            check(!mm.motherName().isEmpty() && mm.title().startsWith("Mother "), "she has a name: " + mm.title());
            check(mm.home() != null && vault.isInside(mm.home()), "her home is her dais: " + mm.home());
            check(l.getBlockState(mm.home()).isAir(), "the seat mark is gone");
            return new String[]{mm.motherName(), mm.title()};
        });
        PlataClan.LOG.info("[mother test] {} sleeps at {}", name[1], onServer(w, l -> l.getEntity(id).blockPosition()));
        Vec3 face = onServer(w, l -> l.getEntity(id).position());
        BlockPos home = onServer(w, l -> ((SilverMother) l.getEntity(id)).home());
        float yaw = onServer(w, l -> l.getEntity(id).getYRot());
        Vec3 front = Vec3.directionFromRotation(0, yaw);
        look(ctx, w, face.add(front.scale(6)).add(2.5, 3.2, 0), face.add(0, 1.2, 0), 10);
        shoot(ctx, "mother-01-asleep");

        // a survival player walks up the Hollow: she wakes and rises, and her name fills the screen
        w.getServer().runCommand("gamemode survival @a");
        w.getServer().runCommand("effect give @a minecraft:resistance 600 4 true");
        w.getServer().runCommand("effect give @a minecraft:regeneration 600 4 true");
        hud(ctx, true);
        Vec3 stand = face.add(front.scale(9)).add(0, 1.62, 0);
        look(ctx, w, stand, face.add(0, 2, 0), 2);
        for (int i = 0; i < 20 && onServer(w, l -> ((SilverMother) l.getEntity(id)).dormant()); i++) ctx.waitTicks(2);
        check(onServer(w, l -> ((SilverMother) l.getEntity(id)).move() == MotherMove.RISE), "she rises for a survival player");
        ctx.waitTicks(24);
        shoot(ctx, "mother-02-rise");
        ctx.waitTicks(10);
        check(RevealHud.cardTitle() != null && RevealHud.cardTitle().equals(name[1].toUpperCase(java.util.Locale.ROOT)),
                "her name fills the screen: " + RevealHud.cardTitle());
        shoot(ctx, "mother-03-name");
        ctx.waitTicks(20);
        portrait(ctx, w, id, new double[]{5.5, 6.5, 7.5}, new double[]{.8, 1.6, 2.4}, 10, "mother-03b-portrait");
        ctx.waitTicks(20);
        float[] sized = onServer(w, l -> {
            SilverMother mm = (SilverMother) l.getEntity(id);
            return new float[]{mm.getMaxHealth(), mm.plataLevel(), mm.move() == MotherMove.RISE ? 1 : 0};
        });
        check(sized[0] >= Balance.MOTHER_MIN_HEALTH * .99F, "a Warden's health at least: " + sized[0]);
        PlataClan.LOG.info("[mother test] {}: {} health, level {}", name[1], sized[0], sized[1]);

        // her blows, each at its share of the player's health (resistance off, full iron on)
        w.getServer().runCommand("effect clear @a");
        for (String slot : List.of("armor.head iron_helmet", "armor.chest iron_chestplate", "armor.legs iron_leggings", "armor.feet iron_boots")) {
            String[] s = slot.split(" ");
            w.getServer().runCommand("item replace entity @a " + s[0] + " with minecraft:" + s[1]);
        }
        ctx.waitTicks(2);
        // size her up again to the armored player, so each blow is aimed through that armor
        w.getServer().runOnServer(s -> ((SilverMother) s.overworld().getEntity(id)).configure(player(w)));
        blow(ctx, w, id, MotherMove.CRUSH, 2.2, Balance.MotherHit.CRUSH, "mother-04-crush");
        blow(ctx, w, id, MotherMove.SWEEP, 3.0, Balance.MotherHit.SWEEP, "mother-05-sweep");
        blow(ctx, w, id, MotherMove.KEEN, 8.0, Balance.MotherHit.KEEN, "mother-06-keen");
        // the rain and the brood, for the pictures
        stage(ctx, w, id, MotherMove.RAIN, 6.0, 26, "mother-07-rain");
        stage(ctx, w, id, MotherMove.BROOD, 6.0, 20, "mother-08-brood");

        // she sheds her shell at two thirds, and makes her last molt at one third: the walls wake
        fly(w);
        Vec3 chest = onServer(w, l -> l.getEntity(id).position().add(0, 1.8, 0));
        look(ctx, w, HideoutClientTest.clearView(w, chest, new double[]{7, 8.5, 10}, new double[]{1.5, 2.5, 3.5}), chest, 2);
        for (int phase : new int[]{2, 3}) {
            float at = phase == 2 ? .6F : .3F;
            onServer(w, l -> {
                SilverMother mm = (SilverMother) l.getEntity(id);
                mm.setNoAi(false);
                mm.start(MotherMove.STANCE);
                mm.setHealth(mm.getMaxHealth() * (at + .02F));
                mm.setInvulnerableTime(0);
                mm.hurtServer(l, player(w).damageSources().playerAttack(player(w)), mm.getMaxHealth() * .03F);
                check(mm.move() == MotherMove.SHED, "below " + at + " she sheds: " + mm.move());
                return null;
            });
            ctx.waitTicks(36);
            if (phase == 3) shoot(ctx, "mother-09-last-molt");
            ctx.waitTicks(20);
            check(onServer(w, l -> ((SilverMother) l.getEntity(id)).phase()) == phase, "phase " + phase);
        }
        int fish = onServer(w, l -> l.getEntitiesOfClass(Silverfish.class, AABB.of(vault).inflate(8)).size());
        check(fish > 0, "her children and the walls answer her: " + fish + " silverfish");

        // she falls; the clan will remember who did it
        hud(ctx, true);
        Vec3 fell = onServer(w, l -> l.getEntity(id).position());
        onServer(w, l -> {
            SilverMother mm = (SilverMother) l.getEntity(id);
            mm.start(MotherMove.STANCE);
            mm.setHealth(2);
            mm.setInvulnerableTime(0);
            ServerPlayer p = player(w);
            mm.hurtServer(l, p.damageSources().playerAttack(p), 100);
            check(mm.isDeadOrDying(), "down");
            return null;
        });
        Vec3 low = fell.add(0, 1, 0);
        look(ctx, w, HideoutClientTest.clearView(w, low, new double[]{5, 6.5, 8}, new double[]{1.5, 2.5, 3.5}), low, 2);
        ctx.waitTicks(30);
        check(RevealHud.cardTitle() != null && RevealHud.cardTitle().endsWith("HAS FALLEN"), "her fall fills the screen: " + RevealHud.cardTitle());
        shoot(ctx, "mother-10-fallen");
        ctx.waitTicks(40);
        onServer(w, l -> {
            check(l.getEntity(id) == null, "gone in a burst of silver");
            ServerPlayer p = player(w);
            check(name[1].equals(Mothers.latest(p)), "the clan remembers who killed her: " + Mothers.latest(p));
            var drops = l.getEntitiesOfClass(ItemEntity.class, new AABB(BlockPos.containing(fell)).inflate(8));
            var crest = drops.stream().filter(d -> d.getItem().is(PlataItems.MOTHER_CREST)).findFirst();
            check(crest.isPresent(), "she leaves her crest");
            String crestName = crest.get().getItem().get(DataComponents.ITEM_NAME).getString();
            check(crestName.contains(name[1]), "named for her: " + crestName);
            check(drops.stream().anyMatch(d -> d.getItem().is(PlataItems.PLATA_MASK)), "and a Plata Mask");
            check(husks(l, vault, false).isEmpty(), "her Warren's husks have all gone quiet: " + husks(l, vault, false).size());
            check(husks(l, site.piece("warren/hall"), false).isEmpty(), "the hall's too");
            return null;
        });
        w.getServer().runCommand("effect clear @a");
        w.getServer().runCommand("clear @a");
        hud(ctx, false);
        fly(w);
        // leave the vault quiet (her children, the silverfish, her drops), so the world closes on a still server
        w.getServer().runOnServer(s -> {
            for (var e : s.overworld().getEntitiesOfClass(net.minecraft.world.entity.Entity.class, AABB.of(vault).inflate(64),
                    e -> !(e instanceof net.minecraft.world.entity.player.Player))) e.discard();
        });
        ctx.waitTicks(40);
    }

    /** Stand the player {@code dist} blocks in front of her, start {@code move}, and measure what the blow takes. */
    static void blow(ClientGameTestContext ctx, TestSingleplayerContext w, int id, MotherMove move, double dist, Balance.MotherHit hit, String shot) {
        w.getServer().runCommand("gamemode survival @a");
        Vec3[] at = place(w, id, dist);
        look(ctx, w, at[0], at[1], 2);
        float max = onServer(w, l -> player(w).getMaxHealth());
        w.getServer().runOnServer(s -> player(w).setHealth(player(w).getMaxHealth()));
        onServer(w, l -> { SilverMother mm = (SilverMother) l.getEntity(id); mm.setTarget(player(w)); mm.start(move); return null; });
        float before = max, worst = 0;
        for (int t = 0; t < move.ticks + 2; t++) {
            ctx.waitTicks(1);
            float hp = onServer(w, l -> player(w).getHealth());
            worst = Math.max(worst, before - hp);
            before = hp;
            if (t == beat(move)) shoot(ctx, shot);
        }
        onServer(w, l -> { SilverMother mm = (SilverMother) l.getEntity(id); mm.setTarget(null); mm.start(MotherMove.STANCE); return null; });
        float want = max * (hit.share + .0001F * onServer(w, l -> ((SilverMother) l.getEntity(id)).plataLevel()));
        PlataClan.LOG.info("[mother test] {} took {} of {} (aimed at {})", move, worst, max, want);
        check(worst > 0, move + " lands");
        if (dev.plataclan.PlayerPower.rpgInstalled()) {
            // Village Friends RPG has defences of its own on top of armor: the blow can only come in lighter
            check(worst <= want + max * .05F && worst >= want * .5F, move + " takes no more than its share: " + worst + " vs " + want + " of " + max);
        } else {
            check(Math.abs(worst - want) <= max * .05F, move + " takes about its share: " + worst + " vs " + want + " of " + max);
        }
        w.getServer().runOnServer(s -> player(w).setHealth(player(w).getMaxHealth()));
    }

    static void stage(ClientGameTestContext ctx, TestSingleplayerContext w, int id, MotherMove move, double dist, int ticks, String shot) {
        fly(w);
        Vec3 chest = onServer(w, l -> l.getEntity(id).position().add(0, 1.8, 0));
        look(ctx, w, HideoutClientTest.clearView(w, chest, new double[]{dist + 1, dist + 2.5, dist + 4}, new double[]{1.2, 2.2, 3.2}), chest, 2);
        onServer(w, l -> { SilverMother mm = (SilverMother) l.getEntity(id); mm.start(move); return null; });
        ctx.waitTicks(ticks);
        shoot(ctx, shot);
        ctx.waitTicks(Math.max(0, move.ticks - ticks + 4));
        onServer(w, l -> { SilverMother mm = (SilverMother) l.getEntity(id); mm.setTarget(null); mm.start(MotherMove.STANCE); return null; });
    }

    /** A picture of her from a clear spot round her, the camera flying. */
    static void portrait(ClientGameTestContext ctx, TestSingleplayerContext w, int id, double[] distances, double[] heights, int ticks, String shot) {
        fly(w);
        Vec3 chest = onServer(w, l -> l.getEntity(id).position().add(0, 1.9, 0));
        look(ctx, w, HideoutClientTest.clearView(w, chest, distances, heights), chest, ticks);
        shoot(ctx, shot);
    }

    private static int beat(MotherMove move) {
        return switch (move) { case CRUSH -> 21; case SWEEP -> 18; case KEEN -> 26; default -> 10; };
    }

    /** The player's eye and her chest: {@code dist} blocks in front of her, facing her, on her floor. */
    private static Vec3[] place(TestSingleplayerContext w, int id, double dist) {
        return onServer(w, l -> {
            SilverMother mm = (SilverMother) l.getEntity(id);
            Vec3 front = Vec3.directionFromRotation(0, mm.getYRot());
            Vec3 feet = mm.position().add(front.scale(dist));
            return new Vec3[]{feet.add(0, 1.62, 0), mm.position().add(0, 2.0, 0)};
        });
    }

    // -- out in the wild, afterwards ------------------------------------------------------------------------

    static void slayer(ClientGameTestContext ctx, TestSingleplayerContext w) {
        clear(w, ctx);
        w.getServer().runCommand("gamemode creative @a");
        w.getServer().runCommand("fill -8 " + Y + " -10 8 " + (Y + 4) + " 12 air");
        w.getServer().runOnServer(s -> Mothers.credit(player(w), "Ysolde", 1));
        check(onServer(w, l -> "Mother Ysolde".equals(Mothers.latest(player(w)))), "the player killed Mother Ysolde");

        // a traveler who knows: their greeting names her, and the replies are the slayer's
        int id = disguise(w, 0, 0);
        w.getServer().runCommand("tp @a 0.5 " + Y + " -2.2 0 10");
        ctx.waitTicks(10);
        hud(ctx, true);
        ctx.runOnClient(client -> {
            var e = client.level.getEntity(id);
            client.gameMode.interact(client.player, e, new EntityHitResult(e), InteractionHand.MAIN_HAND);
        });
        for (int i = 0; i < 20 && ctx.computeOnClient(client -> !(client.gui.screen() instanceof FriendshipScreen)); i++) ctx.waitTicks(5);
        ctx.waitTicks(30);
        String said = ctx.computeOnClient(client -> client.gui.screen() instanceof FriendshipScreen s ? s.getNarrationMessage().getString() : "");
        String traveler = onServer(w, l -> VillageFriends.name((Villager) l.getEntity(id)));
        String playerName = onServer(w, l -> player(w).getName().getString());
        boolean greeted = PlataLines.pool("mother.greet").stream()
                .map(line -> PlataLines.fill(line.text(), null, playerName, traveler).replace("{mother}", "Mother Ysolde"))
                .anyMatch(said::contains);
        check(greeted, "the traveler lets on that they know: " + said);
        List<String> replies = onServer(w, l -> Reveal.choices((Villager) l.getEntity(id), player(w), Reveal.TAB).stream().map(c -> c.id()).toList());
        check(replies.equals(List.of("plataclan_proud", "plataclan_sorry", "plataclan_deny")), "the slayer's replies: " + replies);
        shoot(ctx, "slayer-01-greeting");

        // "I didn't want to": they molt, and let you be
        Reveal.forceAvenge = false;
        ctx.runOnClient(client -> ClientPlayNetworking.send(new ActionPayload(id, client.level.getEntity(id).getUUID(), "plataclan_sorry")));
        for (int i = 0; i < 60 && onServer(w, l -> members(l).isEmpty()); i++) ctx.waitTicks(1);
        ctx.waitTicks(24);
        int yielded = onServer(w, l -> {
            List<PlataMember> m = members(l);
            check(m.size() == 1, "one Plata in the traveler's place: " + m.size());
            PlataMember p = m.getFirst();
            check(p.truce() && p.getTarget() == null, "they let you be: truce " + p.truce() + ", target " + p.getTarget());
            return p.getId();
        });
        String yieldLine = RevealHud.showing();
        check(yieldLine != null && PlataLines.pool("mother.yield").stream()
                .anyMatch(line -> PlataLines.fill(line.text(), null, playerName, traveler).replace("{mother}", "Mother Ysolde").equals(yieldLine)),
                "and say so: " + yieldLine);
        w.getServer().runCommand("tp @a 0.5 " + Y + " -2.4 0 8");
        ctx.waitTicks(10);
        shoot(ctx, "slayer-02-let-be");

        // talk to them
        ctx.runOnClient(client -> {
            var e = client.level.getEntity(yielded);
            client.gameMode.interact(client.player, e, new EntityHitResult(e), InteractionHand.MAIN_HAND);
        });
        for (int i = 0; i < 20 && ctx.computeOnClient(client -> !(client.gui.screen() instanceof TruceScreen)); i++) ctx.waitTicks(5);
        ctx.runOnClient(client -> check(client.gui.screen() instanceof TruceScreen t && t.entityId() == yielded, "the truce window opens: " + client.gui.screen()));
        ctx.waitTicks(60);
        shoot(ctx, "slayer-03-talk");
        String greet = ctx.computeOnClient(client -> ((TruceScreen) client.gui.screen()).data().line());
        ctx.runOnClient(client -> ((TruceScreen) client.gui.screen()).press("ask_mother"));
        for (int i = 0; i < 20 && ctx.computeOnClient(client -> client.gui.screen() instanceof TruceScreen t && t.data().line().equals(greet)); i++) ctx.waitTicks(2);
        String answer = ctx.computeOnClient(client -> ((TruceScreen) client.gui.screen()).data().line());
        check(!answer.equals(greet) && !answer.contains("{"), "they answer: " + answer);
        ctx.runOnClient(client -> ((TruceScreen) client.gui.screen()).press("ask_hideout"));
        ctx.waitTicks(80);
        shoot(ctx, "slayer-04-answer");
        ctx.runOnClient(client -> client.gui.setScreen(null));

        // strike someone who let you be, and they fight after all
        w.getServer().runCommand("gamemode survival @a");
        w.getServer().runCommand("effect give @a minecraft:resistance 600 4 true");
        onServer(w, l -> {
            PlataMember p = (PlataMember) l.getEntity(yielded);
            player(w).attack(p);
            if (p.truce()) { p.setInvulnerableTime(0); p.hurtServer(l, player(w).damageSources().playerAttack(player(w)), 1); }
            check(!p.truce() && p.getTarget() == player(w), "provoked, they fight: truce " + p.truce() + ", target " + p.getTarget());
            return null;
        });
        ctx.waitTicks(10);
        shoot(ctx, "slayer-05-provoked");
        clear(w, ctx);
        w.getServer().runCommand("gamemode creative @a");

        // another who will not let it go: "You killed Mother Ysolde, prepare to die."
        Reveal.forceAvenge = true;
        int id2 = disguise(w, 0, 0);
        w.getServer().runCommand("tp @a 0.5 " + Y + " -2.2 0 10");
        ctx.waitTicks(5);
        w.getServer().runCommand("gamemode survival @a");
        onServer(w, l -> { Reveal.start(l, (Villager) l.getEntity(id2), player(w), "proud"); return null; });
        for (int i = 0; i < 60 && onServer(w, l -> members(l).isEmpty()); i++) ctx.waitTicks(1);
        ctx.waitTicks(16);
        onServer(w, l -> {
            PlataMember p = members(l).getFirst();
            check(!p.truce() && p.getTarget() == player(w), "they come for the Mother's killer");
            return null;
        });
        String avenge = RevealHud.showing();
        String traveler2 = onServer(w, l -> members(l).getFirst().disguise());
        check(avenge != null && PlataLines.pool("mother.avenge").stream()
                .anyMatch(line -> PlataLines.fill(line.text(), null, playerName, traveler2).replace("{mother}", "Mother Ysolde").equals(avenge)),
                "and say why: " + avenge);
        w.getServer().runCommand("tp @a 0.5 " + Y + " -4.5 0 8");
        ctx.waitTicks(4);
        shoot(ctx, "slayer-06-avenge");
        clear(w, ctx);
        w.getServer().runCommand("gamemode creative @a");

        // one who lets you be goes back into the stone when the time runs out
        Reveal.forceAvenge = false;
        int id3 = disguise(w, 0, 0);
        onServer(w, l -> { Reveal.start(l, (Villager) l.getEntity(id3), player(w), "deny"); return null; });
        for (int i = 0; i < 60 && onServer(w, l -> members(l).isEmpty()); i++) ctx.waitTicks(1);
        onServer(w, l -> { PlataMember p = members(l).getFirst(); check(p.truce(), "let be"); p.truceTicks(30); return null; });
        for (int i = 0; i < 40 && onServer(w, l -> !members(l).isEmpty()); i++) ctx.waitTicks(5);
        check(onServer(w, l -> members(l).isEmpty()), "after a while they go back into the stone");

        Reveal.forceAvenge = null;
        w.getServer().runOnServer(s -> ((AttachmentTarget) player(w)).removeAttached(Mothers.SLAIN));
        hud(ctx, false);
        w.getServer().runCommand("effect clear @a");
    }
}

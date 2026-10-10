package dev.plataclan.test;

import static dev.plataclan.test.PlataClientTest.Y;
import static dev.plataclan.test.PlataClientTest.check;
import static dev.plataclan.test.PlataClientTest.clear;
import static dev.plataclan.test.PlataClientTest.disguise;
import static dev.plataclan.test.PlataClientTest.hud;
import static dev.plataclan.test.PlataClientTest.members;
import static dev.plataclan.test.PlataClientTest.onServer;
import static dev.plataclan.test.PlataClientTest.player;
import static dev.plataclan.test.PlataClientTest.shoot;
import static dev.plataclan.test.PlataClientTest.spawnMember;

import java.util.List;

import dev.plataclan.Balance;
import dev.plataclan.PlayerPower;
import dev.plataclan.Rank;
import dev.plataclan.client.RevealHud;
import dev.plataclan.disguise.Disguises;
import dev.plataclan.entity.Move;
import dev.plataclan.entity.PlataMember;
import dev.plataclan.item.PlataItems;
import dev.villagefriends.ActionPayload;
import dev.villagefriends.VillageFriends;
import dev.villagefriends.client.FriendshipScreen;
import dev.villagefriends.homestead.Homesteads;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.npc.villager.Villager;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.EntityHitResult;

/** The scenes of {@link PlataClientTest}, one method each. */
@SuppressWarnings("UnstableApiUsage")
final class PlataScenes {
    private PlataScenes() {}

    // -- where a traveler turns up ----------------------------------------------------------------

    static void encounter(ClientGameTestContext ctx, TestSingleplayerContext w) {
        w.getServer().runCommand("tp @a 0 " + Y + " 0 0 0");
        ctx.waitTicks(20);
        onServer(w, level -> {
            ServerPlayer p = player(w);
            BlockPos spot = Disguises.findSpot(level, p);
            check(spot != null, "a spot ahead of the player");
            double d = Math.sqrt(spot.distSqr(p.blockPosition()));
            check(d >= 34 && d <= 58, "36-56 blocks out, was " + d);
            check(Disguises.open(level, spot), "open ground");
            Villager v = Disguises.spawn(level, spot);
            check(v != null && Disguises.disguised(v), "spawned in disguise");
            var dweller = Homesteads.dweller(v);
            check(dweller != null && dweller.role().equals(Disguises.ROLE), "a Village Friends dweller of no village: " + dweller);
            check(v.hasCustomName() && !VillageFriends.name(v).isBlank(), "a Village Friends name: " + VillageFriends.name(v));
            check(Disguises.findSpot(level, p) == null || Disguises.loaded().size() == 1, "one traveler");
            v.discard();
            return null;
        });
    }

    // -- the talk and the Molt ------------------------------------------------------------------------

    static void talk(ClientGameTestContext ctx, TestSingleplayerContext w) {
        clear(w, ctx);
        w.getServer().runCommand("fill -6 " + Y + " -10 6 " + (Y + 4) + " 10 air");
        int id = disguise(w, 0, 0);
        w.getServer().runCommand("tp @a 0.5 " + Y + " -6.5 0 4");
        ctx.waitTicks(30);
        shoot(ctx, "plata-01-traveler");
        String name = onServer(w, level -> VillageFriends.name((Villager) level.getEntity(id)));

        // Up close and talk, through Village Friends' own window.
        w.getServer().runCommand("tp @a 0.5 " + Y + " -2.2 0 10");
        ctx.waitTicks(5);
        hud(ctx, true);
        ctx.runOnClient(client -> {
            var e = client.level.getEntity(id);
            client.gameMode.interact(client.player, e, new EntityHitResult(e), InteractionHand.MAIN_HAND);
        });
        for (int i = 0; i < 20 && ctx.computeOnClient(client -> !(client.gui.screen() instanceof FriendshipScreen)); i++) ctx.waitTicks(5);
        ctx.runOnClient(client -> check(client.gui.screen() instanceof FriendshipScreen s && s.residentId() == id,
                "talking opens Village Friends' window: " + client.gui.screen()));
        ctx.waitTicks(30);
        shoot(ctx, "plata-02-talk");
        check(onServer(w, level -> !Disguises.molting((Villager) level.getEntity(id))), "no Molt until they answer");

        // Any reply drops the disguise: the window closes and the Molt plays.
        ctx.runOnClient(client -> ClientPlayNetworking.send(new ActionPayload(id, client.level.getEntity(id).getUUID(), "plataclan_suspicious")));
        for (int i = 0; i < 10 && onServer(w, level -> level.getEntity(id) instanceof Villager v && !Disguises.molting(v)); i++) ctx.waitTicks(1);
        w.getServer().runCommand("tp @a 0.5 " + Y + " -5.5 0 6");
        ctx.waitTicks(2);
        ctx.runOnClient(client -> check(!(client.gui.screen() instanceof FriendshipScreen), "the window closed for the Molt"));
        hud(ctx, false);
        shoot(ctx, "plata-03-molt-1");
        ctx.waitTicks(8);
        shoot(ctx, "plata-03-molt-2");
        ctx.waitTicks(7);
        shoot(ctx, "plata-03-molt-3");
        for (int i = 0; i < 40 && onServer(w, level -> members(level).isEmpty()); i++) ctx.waitTicks(1);
        hud(ctx, true);
        shoot(ctx, "plata-04-unfurl");
        ctx.waitTicks(8);
        shoot(ctx, "plata-04-unfurl-2");
        onServer(w, level -> {
            check(level.getEntity(id) == null, "the traveler is gone");
            List<PlataMember> m = members(level);
            check(m.size() == 1, "one Plata in their place: " + m.size());
            PlataMember p = m.getFirst();
            check(p.disguise().equals(name), "they keep the name they wore: " + p.disguise());
            int lvl = PlayerPower.level(player(w));
            check(Math.abs(p.plataLevel() - lvl) <= 5, "their level follows yours: " + p.plataLevel() + " vs " + lvl);
            check(Disguises.log(player(w)).seenCard() && Disguises.log(player(w)).reveals() == 1, "the clan remembers the encounter");
            return null;
        });
        ctx.waitTicks(16);
        check(RevealHud.card() != null, "the first encounter shows the title card");
        check(RevealHud.showing() != null, "and they say something: " + RevealHud.showing());
        shoot(ctx, "plata-05-revealed");
        hud(ctx, false);
    }

    // -- the three ranks ------------------------------------------------------------------------------

    static void ranks(ClientGameTestContext ctx, TestSingleplayerContext w) {
        clear(w, ctx);
        onServer(w, level -> {
            Rank[] all = Rank.values();
            for (int i = 0; i < all.length; i++) spawnMember(level, all[i], 30, -2.5 + i * 2.5, 0.5, 180, false);
            return null;
        });
        ctx.waitTicks(30);
        w.getServer().runCommand("tp @a 0.5 " + Y + " -5 0 8");
        ctx.waitTicks(10);
        shoot(ctx, "plata-06-lineup");
        w.getServer().runCommand("tp @a 0.5 " + Y + " 6 180 8");
        ctx.waitTicks(10);
        shoot(ctx, "plata-07-lineup-back");
        Rank[] all = Rank.values();
        for (int i = 0; i < all.length; i++) {
            double x = -2.5 + i * 2.5;
            w.getServer().runCommand(String.format("tp @a %.2f %d %.2f 35 8", x + 2.1, Y, -2.6));
            ctx.waitTicks(8);
            shoot(ctx, "plata-08-" + all[i].id);
        }
    }

    // -- moves, frozen at their beats -----------------------------------------------------------------

    record Beat(Rank rank, Move move, int ticks) {}

    static void moves(ClientGameTestContext ctx, TestSingleplayerContext w) {
        List<Beat> beats = List.of(new Beat(Rank.SCUTTLER, Move.READY, 6), new Beat(Rank.SCUTTLER, Move.FLURRY_1, 4),
                new Beat(Rank.SCUTTLER, Move.FLURRY_3, 4), new Beat(Rank.SCUTTLER, Move.LUNGE, 10), new Beat(Rank.SCUTTLER, Move.BACKFLIP, 6),
                new Beat(Rank.SCUTTLER, Move.BURROW_DIVE, 8), new Beat(Rank.SCUTTLER, Move.EMERGE, 5), new Beat(Rank.SCUTTLER, Move.CALL, 10),
                new Beat(Rank.SCUTTLER, Move.STAGGER, 6), new Beat(Rank.SCUTTLER, Move.TAUNT, 14), new Beat(Rank.SHARDCASTER, Move.THROW_FAN, 8),
                new Beat(Rank.SHARDCASTER, Move.KICK, 5), new Beat(Rank.WARDEN, Move.LASH, 12), new Beat(Rank.WARDEN, Move.LASH, 17),
                new Beat(Rank.WARDEN, Move.SLAM, 8), new Beat(Rank.WARDEN, Move.SLAM, 14), new Beat(Rank.WARDEN, Move.UNFURL, 8));
        for (Beat b : beats) {
            clear(w, ctx);
            int id = onServer(w, level -> spawnMember(level, b.rank(), 40, .5, .5, 160, false).getId());
            w.getServer().runCommand("tp @a 2.2 " + Y + " -3.2 25 8");
            ctx.waitTicks(12);
            w.getServer().runOnServer(s -> ((PlataMember) s.overworld().getEntity(id)).start(b.move()));
            ctx.waitTicks(b.ticks());
            shoot(ctx, "plata-move-" + b.rank().id + "-" + b.move().name().toLowerCase() + "-" + b.ticks());
        }
    }

    // -- the numbers in a real fight ----------------------------------------------------------------

    static void fight(ClientGameTestContext ctx, TestSingleplayerContext w) {
        clear(w, ctx);
        w.getServer().runCommand("gamemode survival @a");
        w.getServer().runCommand("tp @a 0.5 " + Y + " 0.5 0 0");
        for (String slot : List.of("armor.head iron_helmet", "armor.chest iron_chestplate", "armor.legs iron_leggings", "armor.feet iron_boots",
                "weapon.mainhand iron_sword")) {
            String[] s = slot.split(" ");
            w.getServer().runCommand("item replace entity @a " + s[0] + " with minecraft:" + s[1]);
        }
        ctx.waitTicks(5);
        int level = onServer(w, l -> PlayerPower.level(player(w)));
        int rpg = onServer(w, l -> PlayerPower.rpgLevel(player(w)));
        if (rpg > 0) check(level == rpg, "with the RPG, the character level: " + level + " vs " + rpg);
        else check(level >= 22 && level <= 38, "full iron and an iron sword sizes up around 29: " + level);
        int id = onServer(w, l -> {
            PlataMember m = spawnMember(l, Rank.SCUTTLER, level, 4.5, 4.5, 0, true);
            m.setTarget(player(w));
            float hits = m.getMaxHealth() / Math.max(3, PlayerPower.read(player(w), m).meleeHit());
            check(hits > 9 && hits < 14, "a Scuttler takes 10-13 of your hits: " + hits);
            return m.getId();
        });
        // measure from the player's real health (the RPG starts a character on 6 hearts)
        float max = onServer(w, l -> player(w).getMaxHealth());
        w.getServer().runOnServer(s -> player(w).setHealth(player(w).getMaxHealth()));
        ctx.waitTicks(2);
        float worst = 0, before = onServer(w, l -> player(w).getHealth());
        boolean burrowed = false, emerged = false, hit = false;
        for (int t = 0; t < 1000 && !(burrowed && emerged && hit); t++) {
            ctx.waitTicks(1);
            float hp = onServer(w, l -> player(w).getHealth());
            if (hp < before) { worst = Math.max(worst, before - hp); hit = true; }
            if (hp < max * .5F) w.getServer().runOnServer(s -> player(w).setHealth(player(w).getMaxHealth()));
            before = onServer(w, l -> player(w).getHealth());
            Move m = onServer(w, l -> l.getEntity(id) instanceof PlataMember p ? p.move() : Move.STANCE);
            if (m == Move.BURROW_DIVE || m == Move.UNDER || m == Move.TREMOR) burrowed = true;
            if (burrowed && m == Move.EMERGE) emerged = true;
            if (t % 250 == 200 && !burrowed) {   // far away: they burrow to you
                int z = t % 500 == 200 ? -18 : 18;
                w.getServer().runCommand("tp @a 0.5 " + Y + " " + z + " 0 0");
            }
        }
        check(hit, "the Scuttler landed a blow");
        check(worst <= max * .16F, "no single blow takes more than ~15% of your health: " + worst + " of " + max);
        check(burrowed && emerged, "they burrowed and came up again (" + burrowed + ", " + emerged + ")");

        // Dodges, staggers and the Molt, with their thinking paused between blows.
        onServer(w, l -> {
            PlataMember m = (PlataMember) l.getEntity(id);
            m.setNoAi(true);
            m.start(Move.STANCE);
            ServerPlayer p = player(w);
            int dodged = 0;
            for (int i = 0; i < 40; i++) {
                m.setInvulnerableTime(0);
                m.start(Move.STANCE);
                if (!m.hurtServer(l, p.damageSources().playerAttack(p), .01F) && (m.move() == Move.BACKFLIP || m.move() == Move.SIDEROLL)) dodged++;
            }
            check(dodged > 0 && dodged < 25, "they dodge some blows, not all: " + dodged + "/40");
            // Low on health: the next blow that lands makes them Molt (reeling, so they can't dodge it).
            // Each blow is bigger than the last, as vanilla's damage cooldown only lets the difference through.
            m.setHealth(m.getMaxHealth() * .3F);
            m.setTarget(p);
            for (int i = 0; i < 6 && m.move() != Move.BURROW_DIVE; i++) {
                m.start(Move.STAGGER);
                m.hurtServer(l, p.damageSources().playerAttack(p), 1 + i);
            }
            if (Balance.molts(m.plataLevel())) check(m.move() == Move.BURROW_DIVE, "low on health they Molt and burrow: " + m.move());
            else check(m.move() != Move.BURROW_DIVE, "below level 15 there's no Molt: " + m.move());
            return null;
        });
        w.getServer().runCommand("gamemode creative @a");
        w.getServer().runCommand("clear @a");
    }

    // -- the mask: kin, not prey --------------------------------------------------------------------

    static void mask(ClientGameTestContext ctx, TestSingleplayerContext w) {
        clear(w, ctx);
        int id = disguise(w, 0, 0);
        w.getServer().runOnServer(s -> player(w).setItemSlot(EquipmentSlot.HEAD, new ItemStack(PlataItems.PLATA_MASK)));
        w.getServer().runCommand("tp @a 0.5 " + Y + " -2.2 0 10");
        ctx.waitTicks(10);
        hud(ctx, true);
        ctx.runOnClient(client -> {
            var e = client.level.getEntity(id);
            client.gameMode.interact(client.player, e, new EntityHitResult(e), InteractionHand.MAIN_HAND);
        });
        for (int i = 0; i < 20 && ctx.computeOnClient(client -> !(client.gui.screen() instanceof FriendshipScreen)); i++) ctx.waitTicks(5);
        ctx.waitTicks(20);
        shoot(ctx, "plata-09-kin");
        ctx.runOnClient(client -> ClientPlayNetworking.send(new ActionPayload(id, client.level.getEntity(id).getUUID(), "plataclan_kin_0")));
        ctx.waitTicks(10);
        ctx.runOnClient(client -> check(!(client.gui.screen() instanceof FriendshipScreen), "the kin reply closes the window"));
        check(onServer(w, l -> l.getEntity(id) instanceof Villager v && Disguises.disguised(v) && !Disguises.molting(v)), "kin are left in peace");
        w.getServer().runOnServer(s -> player(w).setItemSlot(EquipmentSlot.HEAD, ItemStack.EMPTY));
        hud(ctx, false);
    }

    // -- hit them instead of talking ----------------------------------------------------------------

    static void rude(ClientGameTestContext ctx, TestSingleplayerContext w) {
        clear(w, ctx);
        int id = disguise(w, 0, 0);
        w.getServer().runCommand("gamemode survival @a");
        w.getServer().runCommand("tp @a 0.5 " + Y + " -2 0 10");
        ctx.waitTicks(10);
        float hp = onServer(w, l -> {
            Villager v = (Villager) l.getEntity(id);
            float before = v.getHealth();
            player(w).attack(v);
            check(Disguises.molting(v), "a blow drops the disguise at once");
            return before - v.getHealth();
        });
        check(hp == 0, "and does them no harm: " + hp);
        for (int i = 0; i < 40 && onServer(w, l -> members(l).isEmpty()); i++) ctx.waitTicks(1);
        check(onServer(w, l -> members(l).size() == 1), "a Plata springs out");
        w.getServer().runCommand("gamemode creative @a");
    }

    // -- the end of a Plata ---------------------------------------------------------------------------

    static void death(ClientGameTestContext ctx, TestSingleplayerContext w) {
        clear(w, ctx);
        int id = onServer(w, l -> spawnMember(l, Rank.WARDEN, 40, .5, 3.5, 180, false).getId());
        w.getServer().runCommand("tp @a 0.5 " + Y + " -1.5 0 10");
        ctx.waitTicks(15);
        onServer(w, l -> {
            PlataMember m = (PlataMember) l.getEntity(id);
            ServerPlayer p = player(w);
            m.hurtServer(l, p.damageSources().playerAttack(p), 100000);
            check(m.isDeadOrDying(), "down");
            return null;
        });
        ctx.waitTicks(12);
        shoot(ctx, "plata-10-defeat");
        ctx.waitTicks(20);
        onServer(w, l -> {
            check(l.getEntity(id) == null, "gone in a burst of dust");
            var drops = l.getEntitiesOfClass(ItemEntity.class, new AABB(-4, Y - 2, 0, 5, Y + 4, 8));
            check(drops.stream().anyMatch(d -> d.getItem().is(PlataItems.SILVER_SCALE)), "silver scales dropped");
            check(drops.stream().anyMatch(d -> d.getItem().is(Items.EMERALD)), "emeralds dropped");
            return null;
        });
    }

    // -- with Village Friends RPG ---------------------------------------------------------------------

    static void compat(ClientGameTestContext ctx, TestSingleplayerContext w) {
        if (!FabricLoader.getInstance().isModLoaded("villagefriends_rpg")) return;
        check(PlayerPower.rpgInstalled(), "the RPG is seen");
        String name = onServer(w, l -> player(w).getName().getString());
        w.getServer().runCommand("rpg admin " + name + " level 57");
        ctx.waitTicks(5);
        check(onServer(w, l -> PlayerPower.level(player(w))) == 57, "the RPG character level is the clan's level");
        try {
            Class<?> bestiary = Class.forName("dev.villagefriends.rpg.Bestiary");
            Object family = bestiary.getMethod("ofMob", String.class).invoke(null, "plata_warden");
            check(family != null && family.toString().contains("vermin"), "Plata count as Vermin in the Bestiary: " + family);
        } catch (ReflectiveOperationException e) {
            throw new AssertionError("RPG Bestiary", e);
        }
        check(Balance.clampLevel(57) == 57, "levels pass through");
    }
}

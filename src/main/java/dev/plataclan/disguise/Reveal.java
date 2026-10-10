package dev.plataclan.disguise;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import dev.plataclan.Balance;
import dev.plataclan.PlataConfig;
import dev.plataclan.PlataLines;
import dev.plataclan.PlataNet;
import dev.plataclan.PlayerPower;
import dev.plataclan.Rank;
import dev.plataclan.entity.PlataEntities;
import dev.plataclan.entity.PlataMember;
import dev.plataclan.item.PlataItems;
import dev.plataclan.mother.Mothers;
import dev.villagefriends.FriendshipPayload;
import dev.villagefriends.VillageFriends;
import net.fabricmc.fabric.api.attachment.v1.AttachmentTarget;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.npc.villager.Villager;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.EntityHitResult;
import org.jspecify.annotations.Nullable;

/**
 * Talking to a disguised Plata, and the Molt: the disguise drops, the resident curls up, and in a
 * burst of stone dust a Plata springs out in their place, sized up to whoever found them.
 */
public final class Reveal {
    private Reveal() {}

    public static final String TAB = "plataclan", KIN_TAB = "plataclan_kin", ACTION = "plataclan_";
    /** The Molt clip is keyed to swap at 1.2 s. */
    public static final int SWAP_TICKS = 24;
    private static final int TALK_TICKS = 200;
    private static final String[] KINDS = {"polite", "curious", "suspicious"};
    /** The replies open to someone who killed a Silver Mother, when a traveler lets on that they know. */
    private static final String[] SLAYER_KINDS = {"proud", "sorry", "deny"};
    /** Tests only: force the next slayer reveal to avenge (true) or let the player be (false). */
    public static Boolean forceAvenge;

    /** A conversation waiting for a reply; {@code slayer}: the player killed a Mother, and the traveler knows it. */
    private record Talk(UUID player, long since, int seed, boolean slayer) {}
    /** A Molt in progress. */
    private record Molt(Villager villager, UUID player, String kind, long start) {}

    private static final Map<UUID, Talk> talks = new HashMap<>();
    private static final Map<UUID, Molt> molts = new HashMap<>();

    // -- talking ----------------------------------------------------------------------------------

    /** Right-click on a disguised traveler (runs before Village Friends' own listener). */
    static InteractionResult use(Player player, Level world, InteractionHand hand, Entity entity, @Nullable EntityHitResult hit) {
        if (!(entity instanceof Villager v) || !Disguises.disguised(v) || player.isSpectator()) return InteractionResult.PASS;
        if (hand != InteractionHand.MAIN_HAND || world.isClientSide()) return InteractionResult.PASS;
        ServerPlayer p = (ServerPlayer) player;
        if (Disguises.molting(v)) return InteractionResult.SUCCESS_SERVER;
        mark(v);
        if (PlataItems.wearsMask(p)) { kin(p, v); return InteractionResult.SUCCESS_SERVER; }
        if (!PlataConfig.get().talkFirst) { start((ServerLevel) world, v, p, "curious"); return InteractionResult.SUCCESS_SERVER; }
        boolean slayer = Mothers.slayer(p);
        talks.put(v.getUUID(), new Talk(p.getUUID(), world.getGameTime(), world.getRandom().nextInt(1 << 16), slayer));
        String country = Disguises.country((ServerLevel) world, v.blockPosition());
        String pool = slayer ? "mother.greet" : world.getRandom().nextFloat() < .6F ? "disguise.greet." + country : "disguise.greet.any";
        String line = Mothers.fill(PlataLines.pick(pool, null, world.getRandom(), p.getName().getString(), VillageFriends.name(v)), p);
        PlataNet.talkOpened(p, v);
        VillageFriends.show(p, v, TAB, line, "A traveler, far from any village.", true, null);
        return InteractionResult.SUCCESS_SERVER;
    }

    private static void mark(Villager v) {
        Disguises.Disguise d = Disguises.disguise(v);
        if (d != null && !d.met()) ((AttachmentTarget) v).setAttached(Disguises.DISGUISE, d.meet());
    }

    /**
     * Wearing the clan's face: they take you for kin and whisper instead of fighting. To a Mother's killer they
     * whisper about the Mother's killer, none the wiser.
     */
    static void kin(ServerPlayer p, Villager v) {
        String pool = Mothers.slayer(p) && p.getRandom().nextFloat() < .7F ? "kin.mother" : "kin.whisper";
        String line = Mothers.fill(PlataLines.pick(pool, null, p.getRandom(), p.getName().getString(), VillageFriends.name(v)), p);
        VillageFriends.show(p, v, KIN_TAB, line, "They lean in close.", true, null);
    }

    /** The reply bubbles for the disguise talk and the kin whisper. */
    public static @Nullable List<FriendshipPayload.Choice> choices(Villager v, ServerPlayer p, String tab) {
        if (tab.equals(KIN_TAB)) {
            int seed = v.getUUID().hashCode();
            return List.of(new FriendshipPayload.Choice(ACTION + "kin_0", PlataLines.nth("kin.reply", seed, null, null, null), true, ""),
                    new FriendshipPayload.Choice(ACTION + "kin_1", PlataLines.nth("kin.reply", seed + 1, null, null, null), true, ""));
        }
        if (!tab.equals(TAB)) return null;
        Talk t = talks.get(v.getUUID());
        int seed = t == null ? v.getUUID().hashCode() : t.seed();
        boolean slayer = t != null && t.slayer();
        List<FriendshipPayload.Choice> out = new ArrayList<>();
        for (String kind : slayer ? SLAYER_KINDS : KINDS) {
            String pool = (slayer ? "mother.reply." : "disguise.reply.") + kind;
            out.add(new FriendshipPayload.Choice(ACTION + kind, PlataLines.nth(pool, seed + kind.length(), null, null, null), true, ""));
        }
        return out;
    }

    /**
     * Something was clicked in the window of a disguised traveler: one of the replies, or any of Village
     * Friends' own buttons (gift, trade, story...). Either way the act is over; kin are simply let go.
     * Returns whether the click was theirs to handle.
     */
    public static boolean reply(ServerPlayer p, int entityId, String action) {
        if (!(p.level() instanceof ServerLevel level) || !(level.getEntity(entityId) instanceof Villager v) || !Disguises.disguised(v)) return false;
        if (action.startsWith(ACTION + "kin") || PlataItems.wearsMask(p)) { PlataNet.closeTalk(p, v); return true; }
        String kind = action.startsWith(ACTION) ? action.substring(ACTION.length()) : "curious";
        start(level, v, p, List.of(KINDS).contains(kind) || List.of(SLAYER_KINDS).contains(kind) ? kind : "curious");
        return true;
    }

    /** The player shut the window without answering. */
    public static void closed(ServerPlayer p, int entityId) {
        if (p.level() instanceof ServerLevel level && level.getEntity(entityId) instanceof Villager v && talks.containsKey(v.getUUID())) {
            start(level, v, p, "timeout");
        }
    }

    /** Hitting a disguised traveler reveals them at once and does nothing else; nothing else can hurt them either. */
    static boolean allowDamage(LivingEntity entity, DamageSource source, float amount) {
        if (!(entity instanceof Villager v) || !Disguises.disguised(v) || source.is(DamageTypeTags.BYPASSES_INVULNERABILITY)) return true;
        if (source.getEntity() instanceof ServerPlayer p && !p.isSpectator() && v.level() instanceof ServerLevel level) {
            mark(v);
            start(level, v, p, "rude");
        }
        return false;
    }

    // -- the Molt ---------------------------------------------------------------------------------

    /** The disguise drops: the window closes, they freeze and turn to you, and the Molt begins. */
    public static void start(ServerLevel level, Villager v, ServerPlayer p, String kind) {
        talks.remove(v.getUUID());
        if (Disguises.molting(v)) return;
        for (ServerPlayer near : level.players()) if (near.distanceToSqr(v) < 16 * 16) PlataNet.closeTalk(near, v);
        v.setNoAi(true);
        v.getNavigation().stop();
        float yaw = (float) (Mth.atan2(p.getZ() - v.getZ(), p.getX() - v.getX()) * Mth.RAD_TO_DEG) - 90F;
        v.setYRot(yaw); v.yBodyRot = yaw; v.yHeadRot = yaw;
        ((AttachmentTarget) v).setAttached(Disguises.MOLT, level.getGameTime());
        molts.put(v.getUUID(), new Molt(v, p.getUUID(), kind, level.getGameTime()));
        sound(level, v, SoundEvents.VILLAGER_AMBIENT, .8F, .55F);
    }

    static void tick(MinecraftServer server) {
        for (Molt m : new ArrayList<>(molts.values())) {
            Villager v = m.villager();
            if (v.isRemoved() || !(v.level() instanceof ServerLevel level)) { molts.remove(v.getUUID()); continue; }
            long age = level.getGameTime() - m.start();
            if (age == 7) sound(level, v, SoundEvents.SILVERFISH_AMBIENT, .8F, .5F);
            if (age >= 7 && age < SWAP_TICKS && age % 3 == 0) {
                level.sendParticles(new BlockParticleOption(ParticleTypes.BLOCK, Blocks.STONE.defaultBlockState()),
                        v.getX(), v.getY() + 1.0, v.getZ(), 4, .2, .4, .2, .02);
            }
            if (age == 16) sound(level, v, SoundEvents.ROOTED_DIRT_BREAK, .9F, .7F);
            if (age >= SWAP_TICKS) { molts.remove(v.getUUID()); swap(level, m); }
        }
        if (talks.isEmpty()) return;
        for (var e : new ArrayList<>(talks.entrySet())) {
            ServerPlayer p = server.getPlayerList().getPlayer(e.getValue().player());
            Entity found = p == null ? null : ((ServerLevel) p.level()).getEntity(e.getKey());
            if (!(found instanceof Villager v) || p == null) { talks.remove(e.getKey()); continue; }
            long age = p.level().getGameTime() - e.getValue().since();
            if (age > TALK_TICKS || p.distanceToSqr(v) > 8 * 8) start((ServerLevel) p.level(), v, p, "timeout");
        }
    }

    /** In a burst of stone dust the resident is gone and a Plata springs out, sized up to the player. */
    static void swap(ServerLevel level, Molt m) {
        Villager v = m.villager();
        ServerPlayer p = level.getServer().getPlayerList().getPlayer(m.player());
        if (p == null || p.level() != level) p = level.getNearestPlayer(v, 64) instanceof ServerPlayer near ? near : null;
        int playerLevel = p == null ? 10 : PlayerPower.level(p);
        Rank rank = Balance.rankFor(level.getRandom().nextDouble(), playerLevel);
        PlataMember member = PlataEntities.of(rank).create(level, EntitySpawnReason.EVENT);
        String name = VillageFriends.name(v);
        if (member != null) {
            member.snapTo(v.getX(), v.getY(), v.getZ(), v.getYRot(), 0);
            member.yBodyRot = member.yHeadRot = v.getYRot();
            int players = 0;
            for (ServerPlayer near : level.players()) if (!near.isSpectator() && !near.isCreative() && near.distanceToSqr(v) < 32 * 32) players++;
            int memberLevel = Balance.memberLevel(playerLevel, rank, member.getUUID().getLeastSignificantBits());
            if (p != null) member.configure(memberLevel, PlayerPower.read(p, member), Math.max(1, players), name);
            else member.configure(memberLevel, new Balance.Foe(memberLevel, 20, 8, 0, 0, 6), 1, name);
            if (p != null && !p.isCreative() && !p.isSpectator()) member.setTarget(p);
            member.unfurl();
            level.addFreshEntity(member);
        }
        Disguises.Disguise wore = Disguises.disguise(v);
        burst(level, v);
        forget(level, v);
        v.discard();
        if (member == null) return;
        if (p != null) {
            Disguises.PlayerLog log = Disguises.log(p);
            if (!log.seenCard()) {
                member.speak(level, PlataLines.pick("reveal.first", rank, level.getRandom(), p.getName().getString(), name), true);
            }
            Disguises.log(p, new Disguises.PlayerLog(log.lastEncounter(), log.reveals() + 1, true));
        }
        if (p != null && Mothers.slayer(p)) {
            // they know who you are: some fight to avenge her, the rest let you be
            boolean post = wore != null && wore.post();
            boolean avenge = forceAvenge != null ? forceAvenge : Balance.avenges(level.getRandom().nextDouble(), rank, m.kind(), post);
            if (avenge) {
                member.say(level, "mother.avenge", 0);
            } else {
                member.yieldTo(p, Balance.truceTicks(level.getRandom().nextDouble()));
                member.say(level, "mother.yield", 0);
            }
            return;
        }
        member.say(level, "reveal.taunt." + m.kind(), 0);
    }

    private static void burst(ServerLevel level, Villager v) {
        level.sendParticles(new BlockParticleOption(ParticleTypes.BLOCK, Blocks.STONE.defaultBlockState()), v.getX(), v.getY() + .9, v.getZ(),
                90, .45, .7, .45, .25);
        level.sendParticles(new BlockParticleOption(ParticleTypes.BLOCK, Blocks.COBBLESTONE.defaultBlockState()), v.getX(), v.getY() + .4, v.getZ(),
                30, .5, .3, .5, .2);
        level.sendParticles(ParticleTypes.POOF, v.getX(), v.getY() + 1, v.getZ(), 24, .4, .6, .4, .05);
        sound(level, v, SoundEvents.STONE_BREAK, 1.4F, .6F);
        sound(level, v, SoundEvents.SILVERFISH_HURT, 1.2F, .5F);
        sound(level, v, SoundEvents.PLAYER_ATTACK_SWEEP, 1F, .7F);
    }

    /** The traveler never was: take them out of everyone's list of acquaintances. */
    private static void forget(ServerLevel level, Villager v) {
        String id = VillageFriends.profile(v).id();
        for (ServerPlayer p : level.players()) {
            var known = ((AttachmentTarget) p).getAttached(VillageFriends.ACQUAINTANCES);
            if (known == null || !known.containsKey(id)) continue;
            var next = new HashMap<>(known);
            next.remove(id);
            ((AttachmentTarget) p).setAttached(VillageFriends.ACQUAINTANCES, Map.copyOf(next));
        }
    }

    private static void sound(ServerLevel level, Entity at, SoundEvent sound, float volume, float pitch) {
        level.playSound(null, at.getX(), at.getY(), at.getZ(), sound, SoundSource.HOSTILE, volume, pitch);
    }

    static void clear() { talks.clear(); molts.clear(); }
}

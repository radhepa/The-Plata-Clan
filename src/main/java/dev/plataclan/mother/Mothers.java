package dev.plataclan.mother;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import dev.plataclan.PlataClan;
import dev.plataclan.PlataNet;
import dev.plataclan.block.MotherSeatBlock;
import dev.plataclan.block.PlataBlocks;
import dev.plataclan.block.SilverHuskBlock;
import dev.plataclan.entity.PlataEntities;
import dev.plataclan.entity.PlataMember;
import dev.plataclan.hideout.Sleepers;
import dev.plataclan.item.PlataItems;
import net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry;
import net.fabricmc.fabric.api.attachment.v1.AttachmentTarget;
import net.fabricmc.fabric.api.attachment.v1.AttachmentType;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.ItemLore;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import org.jspecify.annotations.Nullable;

/**
 * The Silver Mothers: one asleep in the vault of every Warren, each under the name she took at her last molt. This
 * keeps what the clan remembers about them: which Mothers each player has killed (it follows the player through death),
 * and what happens in her Warren when one falls. Every Plata out in the wild knows that list; see
 * {@link dev.plataclan.disguise.Reveal} for what they make of it.
 */
public final class Mothers {
    private Mothers() {}

    /** A Mother a player helped bring down, and when (game time). */
    public record Slain(String name, long time) {
        public static final Codec<Slain> CODEC = RecordCodecBuilder.create(i -> i.group(
                Codec.STRING.fieldOf("name").forGetter(Slain::name),
                Codec.LONG.optionalFieldOf("time", 0L).forGetter(Slain::time)).apply(i, Slain::new));
    }

    public static final AttachmentType<List<Slain>> SLAIN = AttachmentRegistry.create(PlataClan.id("mothers_slain"),
            b -> b.persistent(Slain.CODEC.listOf()).copyOnDeath());

    /** The names a Mother may take at her last molt: old names, worn smooth like river stones. */
    static final List<String> NAMES = List.of(
            "Ysolde", "Grisel", "Agathe", "Morwen", "Hesper", "Tamsin", "Orla", "Briony", "Ilse", "Maud", "Sabine", "Wren",
            "Edda", "Rowena", "Petra", "Opaline", "Clemence", "Dagny", "Ottilie", "Hollis", "Verity", "Isaura", "Neve",
            "Sigrun", "Lisbet", "Ondine", "Cressida", "Elowen", "Fenna", "Gisla", "Hedda", "Iolanthe", "Jessamy", "Ketta",
            "Linnea", "Mireille", "Nell", "Odalys", "Philippa", "Quilla", "Saskia", "Thea", "Ulla", "Vesna", "Winifred",
            "Xanthe", "Ione", "Bettany", "Carys", "Aldith");

    private static AttachmentTarget target(Object o) { return (AttachmentTarget) o; }

    public static List<Slain> slain(Player p) {
        List<Slain> l = target(p).getAttached(SLAIN);
        return l == null ? List.of() : l;
    }

    /** Whether this player has killed a Silver Mother. */
    public static boolean slayer(Player p) { return !slain(p).isEmpty(); }

    /** "Mother Ysolde": the Mother this player killed most recently, or null. */
    public static @Nullable String latest(Player p) {
        List<Slain> l = slain(p);
        return l.isEmpty() ? null : "Mother " + l.getLast().name();
    }

    /** Fills in {@code {mother}} with the Mother this player killed most recently. */
    public static String fill(String text, @Nullable Player p) {
        if (!text.contains("{mother}")) return text;
        String m = p == null ? null : latest(p);
        return text.replace("{mother}", m == null ? "the Mother" : m);
    }

    /** Remembers that {@code p} helped kill the Mother called {@code name}. */
    public static void credit(ServerPlayer p, String name, long time) {
        List<Slain> next = new ArrayList<>(slain(p));
        next.removeIf(s -> s.name().equals(name));
        next.add(new Slain(name, time));
        target(p).setAttached(SLAIN, List.copyOf(next));
    }

    /** A name for a new Mother: never one an online player already killed, if that can be helped. */
    public static String newName(RandomSource random, ServerLevel level) {
        Set<String> taken = new HashSet<>();
        for (ServerPlayer p : level.getServer().getPlayerList().getPlayers()) for (Slain s : slain(p)) taken.add(s.name());
        List<String> free = new ArrayList<>(NAMES);
        free.removeAll(taken);
        List<String> from = free.isEmpty() ? NAMES : free;
        return from.get(random.nextInt(from.size()));
    }

    // -- her dais -------------------------------------------------------------------------------------

    public static final int SEAT_RANGE = 40;

    /** The mark on a vault's dais: once a player comes within range, the Mother is there, asleep. */
    public static void seat(ServerLevel level, BlockPos pos, BlockState state) {
        if (Math.floorMod(level.getGameTime() + pos.asLong(), 20) != 0) return;
        Player p = level.getNearestPlayer(pos.getX() + .5, pos.getY(), pos.getZ() + .5, SEAT_RANGE, e -> !e.isSpectator());
        if (p == null) return;
        level.setBlock(pos, Blocks.AIR.defaultBlockState(), 3);
        SilverMother m = PlataEntities.SILVER_MOTHER.create(level, EntitySpawnReason.STRUCTURE);
        if (m == null) return;
        m.settleAt(pos, state.getValue(MotherSeatBlock.FACING).toYRot(), newName(level.getRandom(), level));
        m.finalizeSpawn(level, level.getCurrentDifficultyAt(pos), EntitySpawnReason.STRUCTURE, null);
        level.addFreshEntity(m);
        PlataClan.LOG.debug("{} sleeps in her vault at {}", m.title(), pos);
    }

    // -- when one falls ---------------------------------------------------------------------------------

    /**
     * A Mother is dead. Everyone who fought her is remembered for it; everyone near sees her name fall; her Warren
     * goes silent (the husks still sealed split open and empty, their sleepers gone into the stone), and her children
     * still standing cry out for her.
     */
    public static void fallen(ServerLevel level, SilverMother m, Set<UUID> fighters) {
        long now = level.getGameTime();
        for (UUID id : fighters) {
            if (level.getServer().getPlayerList().getPlayer(id) instanceof ServerPlayer p) credit(p, m.motherName(), now);
        }
        String title = m.title().toUpperCase(java.util.Locale.ROOT) + " HAS FALLEN";
        for (ServerPlayer p : level.players()) {
            if (p.distanceToSqr(m) < 64 * 64) PlataNet.say(p, m, m.title(), SilverMother.TITLE, "Every Plata in the stone will know who did this.", title);
        }
        BlockPos c = m.home() != null ? m.home() : m.blockPosition();
        int silenced = 0;
        for (BlockPos q : BlockPos.betweenClosed(c.offset(-64, -16, -64), c.offset(64, 24, 64))) {
            BlockState s = level.getBlockState(q);
            if (!s.is(PlataBlocks.SILVER_HUSK) || !SilverHuskBlock.sealed(s)) continue;
            Sleepers.split(level, q.immutable(), s, true);
            level.sendParticles(new BlockParticleOption(ParticleTypes.BLOCK, Blocks.CALCITE.defaultBlockState()),
                    q.getX() + .5, q.getY() + 1, q.getZ() + .5, 16, .3, .6, .3, .05);
            silenced++;
        }
        boolean cried = false;
        for (PlataMember child : level.getEntitiesOfClass(PlataMember.class, new AABB(c).inflate(64), PlataMember::isAlive)) {
            if (!cried || level.getRandom().nextFloat() < .3F) { child.say(level, "mother.grief", 0); cried = true; }
            child.orphan();   // nothing to crawl back into: the Warren stays quiet
        }
        level.playSound(null, c, SoundEvents.SILVERFISH_DEATH, SoundSource.HOSTILE, 2F, .3F);
        PlataClan.LOG.info("{} has fallen at {} ({} fought her, {} husks went quiet)", m.title(), c, fighters.size(), silenced);
    }

    /** The trophy she leaves: her crest, with her name on it. */
    public static ItemStack crest(String title) {
        ItemStack s = new ItemStack(PlataItems.MOTHER_CREST);
        s.set(DataComponents.ITEM_NAME, Component.translatable("item.plataclan.mother_crest.named", title));
        s.set(DataComponents.LORE, new ItemLore(List.of(
                Component.translatable("item.plataclan.mother_crest.lore").withStyle(ChatFormatting.GRAY))));
        return s;
    }
}

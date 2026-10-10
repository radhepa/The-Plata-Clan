package dev.plataclan.disguise;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import dev.plataclan.Balance;
import dev.plataclan.PlataClan;
import dev.plataclan.PlataConfig;
import dev.villagefriends.VillageAlarm;
import dev.villagefriends.VillageFriends;
import dev.villagefriends.VillageSettlements;
import dev.villagefriends.homestead.Dwelling;
import dev.villagefriends.homestead.Homesteads;
import net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry;
import net.fabricmc.fabric.api.attachment.v1.AttachmentSyncPredicate;
import net.fabricmc.fabric.api.attachment.v1.AttachmentTarget;
import net.fabricmc.fabric.api.attachment.v1.AttachmentType;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Holder;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.stats.Stats;
import net.minecraft.tags.BiomeTags;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.StructureTags;
import net.minecraft.tags.TagKey;
import net.minecraft.util.Mth;
import net.minecraft.world.Difficulty;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.ai.memory.WalkTarget;
import net.minecraft.world.entity.npc.villager.Villager;
import net.minecraft.world.entity.npc.villager.VillagerType;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.Biomes;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/**
 * Lone travelers in the wild who are not what they seem. They are real Village Friends residents
 * (a name, a look, their biome's villager type) that belong to no village: Village Friends'
 * {@code dweller} attachment with the role {@code plata} keeps them out of village life and routines.
 */
public final class Disguises {
    private Disguises() {}

    public static final String ROLE = "plata";
    private static final int CHECK_TICKS = 600, TETHER = 10, MIN_GAP_TICKS = 8 * 1200;
    private static final TagKey<Structure> HOMESTEADS = TagKey.create(Registries.STRUCTURE, Identifier.fromNamespaceAndPath("villagefriends", "homesteads"));

    /** Where the disguise waits, and when it arrived. */
    public record Disguise(BlockPos origin, long born, boolean met) {
        public static final Codec<Disguise> CODEC = RecordCodecBuilder.create(i -> i.group(
                BlockPos.CODEC.fieldOf("origin").forGetter(Disguise::origin),
                Codec.LONG.fieldOf("born").forGetter(Disguise::born),
                Codec.BOOL.optionalFieldOf("met", false).forGetter(Disguise::met)).apply(i, Disguise::new));
        public Disguise meet() { return new Disguise(this.origin, this.born, true); }
    }

    /** What the clan remembers about a player: the last encounter, how many reveals, whether they've seen the title card. */
    public record PlayerLog(long lastEncounter, int reveals, boolean seenCard) {
        public static final PlayerLog NEW = new PlayerLog(-1, 0, false);
        public static final Codec<PlayerLog> CODEC = RecordCodecBuilder.create(i -> i.group(
                Codec.LONG.optionalFieldOf("last", -1L).forGetter(PlayerLog::lastEncounter),
                Codec.INT.optionalFieldOf("reveals", 0).forGetter(PlayerLog::reveals),
                Codec.BOOL.optionalFieldOf("card", false).forGetter(PlayerLog::seenCard)).apply(i, PlayerLog::new));
    }

    public static final AttachmentType<Disguise> DISGUISE = AttachmentRegistry.create(PlataClan.id("disguise"), b -> b.persistent(Disguise.CODEC));
    /** Game time the Molt began: synced so clients play it on the resident. */
    public static final AttachmentType<Long> MOLT = AttachmentRegistry.create(PlataClan.id("molt"),
            b -> b.syncWith(ByteBufCodecs.VAR_LONG, AttachmentSyncPredicate.all()));
    public static final AttachmentType<PlayerLog> LOG = AttachmentRegistry.create(PlataClan.id("encounters"),
            b -> b.persistent(PlayerLog.CODEC).copyOnDeath());

    private static final Set<Villager> loaded = new LinkedHashSet<>();
    private static final Map<UUID, Integer> unattended = new HashMap<>();

    private static AttachmentTarget target(Object o) { return (AttachmentTarget) o; }

    public static boolean disguised(Villager v) { return target(v).hasAttached(DISGUISE); }
    public static @Nullable Disguise disguise(Villager v) { return target(v).getAttached(DISGUISE); }
    public static boolean molting(Villager v) { return target(v).hasAttached(MOLT); }

    public static PlayerLog log(ServerPlayer p) {
        PlayerLog l = target(p).getAttached(LOG);
        return l == null ? PlayerLog.NEW : l;
    }
    public static void log(ServerPlayer p, PlayerLog l) { target(p).setAttached(LOG, l); }

    static void loaded(Villager v) { if (disguised(v)) loaded.add(v); }
    static void unloaded(Villager v) { loaded.remove(v); unattended.remove(v.getUUID()); }
    public static Set<Villager> loaded() { return loaded; }

    // -- encounters -------------------------------------------------------------------------------

    /** Every 30 s per player: maybe someone is waiting up the road. */
    static void tick(MinecraftServer server) {
        ServerLevel level = server.overworld();
        long now = level.getGameTime();
        for (ServerPlayer p : level.players()) {
            if ((now + p.getId() * 37L) % CHECK_TICKS == 0) tryEncounter(level, p, now);
        }
        if (now % 20 == 0) tend(level, now);
    }

    static void tryEncounter(ServerLevel level, ServerPlayer p, long now) {
        PlataConfig cfg = PlataConfig.get();
        if (cfg.minutesBetweenEncounters <= 0 || p.isSpectator() || p.isCreative() || level.getDifficulty() == Difficulty.PEACEFUL) return;
        if (p.getStats().getValue(Stats.CUSTOM.get(Stats.PLAY_TIME)) / 1200.0 < cfg.graceMinutes) return;
        if (nearOther(p.blockPosition(), 128)) return;
        // never two travelers back to back, however the dice fall
        long last = log(p).lastEncounter();
        if (last >= 0 && now - last < MIN_GAP_TICKS) return;
        // out in the open: not down a mine
        if (!level.canSeeSky(p.blockPosition().above()) && p.getY() < level.getSeaLevel() - 6) return;
        double weight = weight(level.getBiome(p.blockPosition()));
        int silverfish = p.getStats().getValue(Stats.ENTITY_KILLED.get(EntityTypes.SILVERFISH));
        if (level.getRandom().nextDouble() >= Balance.encounterChance(cfg.minutesBetweenEncounters, CHECK_TICKS / 20.0, weight, silverfish)) return;
        BlockPos spot = findSpot(level, p);
        if (spot == null) return;
        spawn(level, spot);
        log(p, new PlayerLog(now, log(p).reveals(), log(p).seenCard()));
    }

    /** How much the clan likes this country: open sand most, stone next, never water. */
    public static double weight(Holder<Biome> b) {
        if (b.is(BiomeTags.IS_OCEAN) || b.is(BiomeTags.IS_RIVER) || b.is(Biomes.MUSHROOM_FIELDS)) return 0;
        if (b.is(Biomes.DESERT) || b.is(BiomeTags.IS_BADLANDS)) return 2;
        if (b.is(BiomeTags.IS_MOUNTAIN) || b.is(Biomes.WINDSWEPT_HILLS) || b.is(Biomes.WINDSWEPT_GRAVELLY_HILLS)
                || b.is(Biomes.WINDSWEPT_FOREST) || b.is(Biomes.STONY_SHORE) || b.is(Biomes.STONY_PEAKS)) return 1.5;
        return 1;
    }

    /** Which greeting pool suits the country: sand, stone, cold or green. */
    public static String country(ServerLevel level, BlockPos pos) {
        Holder<Biome> b = level.getBiome(pos);
        if (b.is(Biomes.DESERT) || b.is(BiomeTags.IS_BADLANDS) || b.is(BiomeTags.IS_BEACH) || b.is(BiomeTags.IS_SAVANNA)) return "sand";
        if (b.value().coldEnoughToSnow(pos, level.getSeaLevel())) return "cold";
        if (b.is(BiomeTags.IS_MOUNTAIN) || b.is(Biomes.WINDSWEPT_HILLS) || b.is(Biomes.WINDSWEPT_GRAVELLY_HILLS)
                || b.is(Biomes.STONY_SHORE) || b.is(Biomes.STONY_PEAKS)) return "stone";
        return "green";
    }

    static boolean nearOther(BlockPos pos, double radius) {
        for (Villager v : loaded) if (v.isAlive() && v.blockPosition().distSqr(pos) < radius * radius) return true;
        return false;
    }

    /** Somewhere ahead of the player, 36–56 blocks out, out of sight if possible, on open dry ground far from towns. */
    public static @Nullable BlockPos findSpot(ServerLevel level, ServerPlayer p) {
        PlataConfig cfg = PlataConfig.get();
        Vec3 motion = p.getDeltaMovement();
        float heading = motion.horizontalDistanceSqr() > .002 ? (float) (Mth.atan2(motion.z, motion.x) * Mth.RAD_TO_DEG) - 90F : p.getYRot();
        BlockPos best = null;
        double bestScore = -1;
        for (int i = 0; i < 16; i++) {
            double angle = Math.toRadians(heading + level.getRandom().nextDouble() * 160 - 80);
            double dist = cfg.minDistance + level.getRandom().nextDouble() * Math.max(1, cfg.maxDistance - cfg.minDistance);
            int x = Mth.floor(p.getX() - Math.sin(angle) * dist), z = Mth.floor(p.getZ() + Math.cos(angle) * dist);
            if (!level.hasChunkAt(new BlockPos(x, p.getBlockY(), z))) continue;
            BlockPos pos = new BlockPos(x, level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z), z);
            if (!open(level, pos) || Math.abs(pos.getY() - p.getY()) > 20 || !farFromTowns(level, pos, false)) continue;
            boolean hidden = level.clip(new ClipContext(p.getEyePosition(), Vec3.atCenterOf(pos.above()), ClipContext.Block.COLLIDER,
                    ClipContext.Fluid.NONE, p)).getType() == HitResult.Type.BLOCK;
            double score = (hidden ? 2 : 0) + dist / cfg.maxDistance;
            if (score > bestScore) { best = pos; bestScore = score; }
            if (hidden && i >= 3) break;
        }
        return best != null && farFromTowns(level, best, true) ? best : null;
    }

    /** Dry, solid, open to the sky, room to stand, and country the clan walks. */
    public static boolean open(ServerLevel level, BlockPos pos) {
        BlockState below = level.getBlockState(pos.below());
        if (!below.isFaceSturdy(level, pos.below(), Direction.UP) || below.is(BlockTags.LEAVES) || !below.getFluidState().isEmpty()) return false;
        for (BlockPos p : new BlockPos[]{pos, pos.above()}) {
            BlockState s = level.getBlockState(p);
            if (!s.getCollisionShape(level, p).isEmpty() || !s.getFluidState().isEmpty()) return false;
        }
        return level.canSeeSky(pos) && weight(level.getBiome(pos)) > 0;
    }

    /** No village (known to Village Friends, or a village or homestead structure nearby) and no other disguise. */
    static boolean farFromTowns(ServerLevel level, BlockPos pos, boolean structures) {
        PlataConfig cfg = PlataConfig.get();
        if (VillageAlarm.villageAt(level, pos) != null || nearOther(pos, 96)) return false;
        long far = (long) cfg.villageDistance * cfg.villageDistance;
        for (var village : VillageSettlements.book(level).villages().values()) if (village.distance(pos) < far) return false;
        if (!structures) return true;
        return level.findNearestMapStructure(StructureTags.VILLAGE, pos, cfg.structureChunks, false) == null
                && level.findNearestMapStructure(HOMESTEADS, pos, Math.max(2, cfg.structureChunks / 2), false) == null;
    }

    /** A real Village Friends resident, standing in the wild, belonging nowhere. */
    public static @Nullable Villager spawn(ServerLevel level, BlockPos spot) {
        Villager v = EntityTypes.VILLAGER.create(level, EntitySpawnReason.EVENT);
        if (v == null) return null;
        v.snapTo(spot.getX() + .5, spot.getY(), spot.getZ() + .5, level.getRandom().nextFloat() * 360, 0);
        v.finalizeSpawn(level, level.getCurrentDifficultyAt(spot), EntitySpawnReason.EVENT, null);
        v.setVillagerData(v.getVillagerData().withType(level.registryAccess(), VillagerType.byBiome(level.getBiome(spot))));
        VillageFriends.ensureIdentity(v);
        String name = VillageFriends.name(v);
        target(v).setAttached(Homesteads.DWELLER, new Dwelling.Dweller(ROLE, name, "the road", "plata:" + v.getUUID(), spot.getX(), spot.getY(), spot.getZ()));
        target(v).setAttached(DISGUISE, new Disguise(spot, level.getGameTime(), false));
        level.addFreshEntity(v);
        PlataClan.LOG.debug("A traveler waits at {} ({})", spot, name);
        return v;
    }

    // -- while they wait ----------------------------------------------------------------------------

    /** Once a second: keep them near their spot, let slip a tell now and then, and send off the unvisited. */
    static void tend(ServerLevel level, long now) {
        for (Villager v : new ArrayList<>(loaded)) {
            if (!v.isAlive() || v.isRemoved() || v.level() != level) { loaded.remove(v); continue; }
            Disguise d = disguise(v);
            if (d == null || molting(v)) continue;
            Player near = level.getNearestPlayer(v.getX(), v.getY(), v.getZ(), 128, p -> !p.isSpectator());
            int alone = near == null ? unattended.merge(v.getUUID(), 1, Integer::sum) : 0;
            if (near != null) unattended.remove(v.getUUID());
            if (alone > 60 || !d.met() && now - d.born() > 48000) { leave(v); continue; }
            if (v.blockPosition().distSqr(d.origin()) > TETHER * TETHER) {
                v.getBrain().setMemory(MemoryModuleType.WALK_TARGET, new WalkTarget(d.origin(), .5F, 2));
            }
            if (level.getRandom().nextFloat() < .06F) tell(level, v);
        }
    }

    /** A puff of grey dust at their feet and the faint skitter of something small. */
    static void tell(ServerLevel level, Villager v) {
        level.sendParticles(new BlockParticleOption(ParticleTypes.BLOCK, Blocks.STONE.defaultBlockState()),
                v.getX(), v.getY() + .1, v.getZ(), 5, .25, .02, .25, .02);
        level.playSound(null, v.getX(), v.getY(), v.getZ(), SoundEvents.SILVERFISH_AMBIENT, SoundSource.NEUTRAL, .25F, 1.6F);
    }

    /** Nobody came: they move on, quietly. */
    static void leave(Villager v) {
        unattended.remove(v.getUUID());
        loaded.remove(v);
        v.discard();
    }
}

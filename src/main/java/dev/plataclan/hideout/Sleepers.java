package dev.plataclan.hideout;

import dev.plataclan.Balance;
import dev.plataclan.PlayerPower;
import dev.plataclan.Rank;
import dev.plataclan.block.PlataBlocks;
import dev.plataclan.block.SilverHuskBlock;
import dev.plataclan.block.SilverHuskBlockEntity;
import dev.plataclan.entity.PlataEntities;
import dev.plataclan.entity.PlataMember;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.Difficulty;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/**
 * The hideouts' sleepers. A sealed husk listens for strangers: a player in sight within
 * {@link Balance#WAKE_RANGE} blocks (half that while sneaking, more for an elder) wakes its sleeper, who
 * bursts out sized up to that player, and the husks around stir and follow a moment later. Never more
 * than {@link Balance#AWAKE_CAP} sleepers are up at once near a husk; the rest wait their turn. A sleeper
 * left with nobody to fight crawls back into its shell, which seals again.
 */
public final class Sleepers {
    private Sleepers() {}

    private static final int CHECK_TICKS = 5, STIR_RADIUS = 6, CROWD_RADIUS = 32;

    /** Every few ticks per sealed husk: is anyone close enough, or has a neighbour's waking stirred it? */
    public static void tick(ServerLevel level, BlockPos pos, BlockState state, SilverHuskBlockEntity husk) {
        long now = level.getGameTime();
        if (Math.floorMod(now + pos.asLong(), CHECK_TICKS) != 0 || level.getDifficulty() == Difficulty.PEACEFUL) return;
        boolean elder = state.getValue(SilverHuskBlock.KIND) == SilverHuskBlock.Kind.ELDER;
        boolean stirred = husk.stirAt() >= 0 && now >= husk.stirAt();
        ServerPlayer p = intruder(level, pos, state.getValue(SilverHuskBlock.FACING), elder, stirred);
        if (p == null) {
            if (stirred && now - husk.stirAt() > 600) husk.settle();  // whatever stirred it went away
            return;
        }
        if (awake(level, pos) >= Balance.AWAKE_CAP) {
            husk.stir(now + 20);  // wait for a slot
            return;
        }
        hatch(level, pos, state, p, stirred ? "stir" : "wake");
    }

    /** The nearest player this husk notices: in range and in sight, or anyone near once stirred. */
    static @Nullable ServerPlayer intruder(ServerLevel level, BlockPos pos, Direction facing, boolean elder, boolean stirred) {
        // the sleeper looks out through the front of its shell (a ray from inside would only hit the shell)
        Vec3 eye = Vec3.atCenterOf(pos.above()).add(facing.getStepX() * .65, .3, facing.getStepZ() * .65);
        ServerPlayer best = null;
        double bestD = Double.MAX_VALUE;
        for (ServerPlayer p : level.players()) {
            if (p.isSpectator() || p.isCreative() || !p.isAlive()) continue;
            double d = p.getEyePosition().distanceTo(eye);
            double range = stirred ? 20 : Balance.wakeRange(elder, p.isShiftKeyDown());
            if (d > range || d >= bestD) continue;
            if (!stirred && d > 2.5 && !inSight(level, eye, p)) continue;
            best = p;
            bestD = d;
        }
        return best;
    }

    private static boolean inSight(ServerLevel level, Vec3 from, Player p) {
        return level.clip(new ClipContext(from, p.getEyePosition(), ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, p))
                .getType() == HitResult.Type.MISS;
    }

    /** Sleepers from this hideout already up and about near pos. */
    static int awake(ServerLevel level, BlockPos pos) {
        return level.getEntitiesOfClass(PlataMember.class, new AABB(pos).inflate(CROWD_RADIUS),
                m -> m.isAlive() && m.garrison() && !m.fleeing()).size();
    }

    /**
     * The husk splits and its sleeper springs out, sized up to {@code who} (or the nearest player).
     * Husks within a few blocks stir and wake one after another.
     */
    public static @Nullable PlataMember hatch(ServerLevel level, BlockPos pos, BlockState state, @Nullable Player who, String why) {
        if (!SilverHuskBlock.sealed(state)) return null;
        Player p = who != null ? who : level.getNearestPlayer(pos.getX() + .5, pos.getY(), pos.getZ() + .5, 48, e -> !e.isSpectator());
        boolean elder = state.getValue(SilverHuskBlock.KIND) == SilverHuskBlock.Kind.ELDER;
        Direction facing = state.getValue(SilverHuskBlock.FACING);
        split(level, pos, state, true);
        int playerLevel = p instanceof ServerPlayer sp ? PlayerPower.level(sp) : 10;
        Rank rank = Balance.sleeperRank(level.getRandom().nextDouble(), playerLevel, elder);
        PlataMember m = PlataEntities.of(rank).create(level, EntitySpawnReason.EVENT);
        if (m == null) return null;
        Vec3 at = Vec3.atBottomCenterOf(pos).add(facing.getStepX() * .55, 0, facing.getStepZ() * .55);
        m.snapTo(at.x, at.y, at.z, facing.toYRot(), 0);
        m.yBodyRot = m.yHeadRot = facing.toYRot();
        int players = 0;
        for (ServerPlayer near : level.players()) if (!near.isSpectator() && !near.isCreative() && near.distanceToSqr(at) < 32 * 32) players++;
        int memberLevel = Balance.memberLevel(playerLevel, rank, m.getUUID().getLeastSignificantBits());
        if (p instanceof ServerPlayer sp) m.configure(memberLevel, PlayerPower.read(sp, m), Math.max(1, players), "");
        else m.configure(memberLevel, new Balance.Foe(memberLevel, 20, 8, 0, 0, 6), 1, "");
        m.guard(pos, Balance.garrisonHealth(elder));
        if (p != null && !p.isCreative() && !p.isSpectator()) m.setTarget(p);
        m.unfurl();
        level.addFreshEntity(m);
        burst(level, pos, elder);
        m.say(level, elder ? "hideout.elder" : "hideout.wake", 0);
        stirNeighbours(level, pos, level.getGameTime());
        return m;
    }

    /** Wakes every sealed husk within a few blocks, a beat apart. */
    static void stirNeighbours(ServerLevel level, BlockPos from, long now) {
        int beat = 0;
        for (BlockPos q : BlockPos.betweenClosed(from.offset(-STIR_RADIUS, -2, -STIR_RADIUS), from.offset(STIR_RADIUS, 2, STIR_RADIUS))) {
            if (q.equals(from) || !SilverHuskBlock.sealed(level.getBlockState(q))) continue;
            if (level.getBlockEntity(q) instanceof SilverHuskBlockEntity husk) husk.stir(now + 14 + 12L * beat++);
        }
    }

    /** Both halves split (or sealed again). */
    public static void split(ServerLevel level, BlockPos lower, BlockState state, boolean split) {
        level.setBlock(lower, state.setValue(SilverHuskBlock.SPLIT, split), 3);
        BlockState up = level.getBlockState(lower.above());
        if (up.is(PlataBlocks.SILVER_HUSK)) level.setBlock(lower.above(), up.setValue(SilverHuskBlock.SPLIT, split), 3);
    }

    /** A sleeper with nothing left to fight crawls back into its own husk, which seals behind it. */
    public static void reseal(ServerLevel level, BlockPos home) {
        BlockState state = level.getBlockState(home);
        if (!(state.getBlock() instanceof SilverHuskBlock) || state.getValue(SilverHuskBlock.HALF) != DoubleBlockHalf.LOWER
                || !state.getValue(SilverHuskBlock.SPLIT)) return;
        split(level, home, state, false);
        if (level.getBlockEntity(home) instanceof SilverHuskBlockEntity husk) husk.settle();
        level.sendParticles(new BlockParticleOption(ParticleTypes.BLOCK, Blocks.CALCITE.defaultBlockState()),
                home.getX() + .5, home.getY() + 1, home.getZ() + .5, 30, .3, .6, .3, .05);
        level.playSound(null, home, SoundEvents.SILVERFISH_STEP, SoundSource.HOSTILE, 1F, .6F);
        level.playSound(null, home, SoundEvents.DEEPSLATE_TILES_PLACE, SoundSource.HOSTILE, 1F, .8F);
    }

    private static void burst(ServerLevel level, BlockPos pos, boolean elder) {
        double x = pos.getX() + .5, y = pos.getY() + 1, z = pos.getZ() + .5;
        level.sendParticles(new BlockParticleOption(ParticleTypes.BLOCK, Blocks.CALCITE.defaultBlockState()), x, y, z, elder ? 90 : 60, .35, .7, .35, .2);
        level.sendParticles(new BlockParticleOption(ParticleTypes.BLOCK, Blocks.STONE.defaultBlockState()), x, y - .4, z, 30, .4, .3, .4, .15);
        level.sendParticles(ParticleTypes.POOF, x, y, z, 16, .3, .5, .3, .04);
        level.playSound(null, pos, SoundEvents.SNIFFER_EGG_HATCH, SoundSource.HOSTILE, 1.2F, elder ? .6F : .9F);
        level.playSound(null, pos, SoundEvents.SILVERFISH_HURT, SoundSource.HOSTILE, 1F, .55F);
        level.playSound(null, pos, SoundEvents.DEEPSLATE_TILES_BREAK, SoundSource.HOSTILE, 1.2F, .7F);
    }
}

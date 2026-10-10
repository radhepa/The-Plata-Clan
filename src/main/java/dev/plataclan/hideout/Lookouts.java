package dev.plataclan.hideout;

import dev.plataclan.disguise.Disguises;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.block.Blocks;

/**
 * Every hideout has a lookout: a disguised Plata at the camp fire or on the bench by the ruin. The
 * hideout's template only marks the spot; the first time a player comes within {@link #RANGE} blocks
 * the lookout is put there (a real Village Friends resident, like any disguised traveler, who keeps
 * to their post) and the mark is gone.
 */
public final class Lookouts {
    private Lookouts() {}

    public static final int RANGE = 72;

    public static void tick(ServerLevel level, BlockPos pos) {
        if (Math.floorMod(level.getGameTime() + pos.asLong(), 20) != 0) return;
        Player p = level.getNearestPlayer(pos.getX() + .5, pos.getY(), pos.getZ() + .5, RANGE, e -> !e.isSpectator());
        if (p == null) return;
        level.setBlock(pos, Blocks.AIR.defaultBlockState(), 3);
        Disguises.spawn(level, pos, true);
    }
}

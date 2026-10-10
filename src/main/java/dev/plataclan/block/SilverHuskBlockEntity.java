package dev.plataclan.block;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Who sleeps in a husk is decided when they wake (they are sized up to whoever woke them), so the
 * husk keeps only one thing: when a neighbour's waking stirred it (game time it should wake, or -1).
 */
public class SilverHuskBlockEntity extends BlockEntity {
    private long stirAt = -1;

    public SilverHuskBlockEntity(BlockPos pos, BlockState state) { super(PlataBlocks.SILVER_HUSK_ENTITY, pos, state); }

    public long stirAt() { return this.stirAt; }
    public void stir(long at) { if (this.stirAt < 0 || at < this.stirAt) this.stirAt = at; }
    public void settle() { this.stirAt = -1; }
}

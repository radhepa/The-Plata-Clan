package dev.plataclan.block;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

/** Only there so the lookout mark ticks. */
public class LookoutBlockEntity extends BlockEntity {
    public LookoutBlockEntity(BlockPos pos, BlockState state) { super(PlataBlocks.LOOKOUT_ENTITY, pos, state); }
}

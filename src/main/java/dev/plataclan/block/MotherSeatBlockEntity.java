package dev.plataclan.block;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

/** Only so the Mother's mark can tick. */
public class MotherSeatBlockEntity extends BlockEntity {
    public MotherSeatBlockEntity(BlockPos pos, BlockState state) { super(PlataBlocks.MOTHER_SEAT_ENTITY, pos, state); }
}

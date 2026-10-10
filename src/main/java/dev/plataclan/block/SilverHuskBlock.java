package dev.plataclan.block;

import java.util.EnumMap;
import java.util.Map;

import dev.plataclan.hideout.Sleepers;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.util.StringRepresentable;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Explosion;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.ScheduledTickAccess;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jspecify.annotations.Nullable;

/**
 * A silver husk: the shed shell a Plata sleeps standing up in, two blocks tall. A sealed one has a
 * member asleep inside, who bursts out when a stranger comes near (see {@link Sleepers}); a split one
 * is empty, either because its sleeper woke or because they are out on the road in disguise. Breaking
 * a sealed husk wakes its sleeper too.
 */
public class SilverHuskBlock extends HorizontalDirectionalBlock implements EntityBlock {
    public static final EnumProperty<DoubleBlockHalf> HALF = BlockStateProperties.DOUBLE_BLOCK_HALF;
    public static final EnumProperty<Kind> KIND = EnumProperty.create("kind", Kind.class);
    public static final BooleanProperty SPLIT = BooleanProperty.create("split");

    /** A footsoldier's husk, or an elder's (larger, bronze-trimmed: a Molt Warden sleeps in it). */
    public enum Kind implements StringRepresentable {
        SLEEPER, ELDER;
        @Override public String getSerializedName() { return this == SLEEPER ? "sleeper" : "elder"; }
    }

    private final Map<Direction, VoxelShape[]> shapes = new EnumMap<>(Direction.class);

    public SilverHuskBlock(Properties properties) {
        super(properties);
        this.registerDefaultState(this.stateDefinition.any().setValue(FACING, Direction.NORTH).setValue(HALF, DoubleBlockHalf.LOWER)
                .setValue(KIND, Kind.SLEEPER).setValue(SPLIT, false));
        for (Direction d : Direction.Plane.HORIZONTAL) {
            // [kind][half][split]: a round shell, its front half gone once split
            VoxelShape[] s = new VoxelShape[8];
            for (int k = 0; k < 2; k++) for (int h = 0; h < 2; h++) for (int split = 0; split < 2; split++) {
                double w = k == 1 ? 2 : 3, top = h == 0 ? 16 : 13;
                double[] box = {w, 0, split == 1 ? 8 : w, 16 - w, top, 16 - w};
                s[k * 4 + h * 2 + split] = rotated(box, d);
            }
            this.shapes.put(d, s);
        }
    }

    /** A box drawn facing north (front at low z), turned to face d. */
    private static VoxelShape rotated(double[] b, Direction d) {
        double[] r = switch (d) {
            case SOUTH -> new double[]{16 - b[3], b[1], 16 - b[5], 16 - b[0], b[4], 16 - b[2]};
            case EAST -> new double[]{16 - b[5], b[1], b[0], 16 - b[2], b[4], b[3]};
            case WEST -> new double[]{b[2], b[1], 16 - b[3], b[5], b[4], 16 - b[0]};
            default -> b;
        };
        return Block.box(r[0], r[1], r[2], r[3], r[4], r[5]);
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING, HALF, KIND, SPLIT);
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        int i = state.getValue(KIND).ordinal() * 4 + (state.getValue(HALF) == DoubleBlockHalf.UPPER ? 2 : 0) + (state.getValue(SPLIT) ? 1 : 0);
        return this.shapes.get(state.getValue(FACING))[i];
    }

    @Override
    public @Nullable BlockState getStateForPlacement(BlockPlaceContext context) {
        BlockPos pos = context.getClickedPos();
        Level level = context.getLevel();
        if (pos.getY() >= level.getMaxY() || !level.getBlockState(pos.above()).canBeReplaced(context)) return null;
        return this.defaultBlockState().setValue(FACING, context.getHorizontalDirection().getOpposite());
    }

    @Override
    public void setPlacedBy(Level level, BlockPos pos, BlockState state, @Nullable LivingEntity by, ItemStack stack) {
        level.setBlockAndUpdate(pos.above(), state.setValue(HALF, DoubleBlockHalf.UPPER));
    }

    @Override
    protected boolean canSurvive(BlockState state, LevelReader level, BlockPos pos) {
        if (state.getValue(HALF) == DoubleBlockHalf.LOWER) return true;
        BlockState below = level.getBlockState(pos.below());
        return below.is(this) && below.getValue(HALF) == DoubleBlockHalf.LOWER;
    }

    @Override
    protected BlockState updateShape(BlockState state, LevelReader level, ScheduledTickAccess ticks, BlockPos pos, Direction toNeighbour,
                                     BlockPos neighbourPos, BlockState neighbour, RandomSource random) {
        DoubleBlockHalf half = state.getValue(HALF);
        if (toNeighbour.getAxis() == Direction.Axis.Y && (half == DoubleBlockHalf.LOWER) == (toNeighbour == Direction.UP)) {
            // the other half went: so does this one; the other half changing state (split, sealed) carries over
            if (!neighbour.is(this) || neighbour.getValue(HALF) == half) return Blocks.AIR.defaultBlockState();
            return state.setValue(SPLIT, neighbour.getValue(SPLIT));
        }
        return super.updateShape(state, level, ticks, pos, toNeighbour, neighbourPos, neighbour, random);
    }

    /** Breaking a sealed husk wakes whoever is inside; a creative player's break never drops the lower half twice. */
    @Override
    public BlockState playerWillDestroy(Level level, BlockPos pos, BlockState state, Player player) {
        if (level instanceof ServerLevel server) {
            BlockPos lower = state.getValue(HALF) == DoubleBlockHalf.LOWER ? pos : pos.below();
            BlockState base = level.getBlockState(lower);
            if (base.is(this) && !base.getValue(SPLIT) && !player.isCreative()) Sleepers.hatch(server, lower, base, player, "break");
            if (player.preventsBlockDrops() && state.getValue(HALF) == DoubleBlockHalf.UPPER && base.is(this)) {
                level.setBlock(lower, Blocks.AIR.defaultBlockState(), 35);  // update all, no drops
                level.levelEvent(player, 2001, lower, Block.getId(base));
            }
        }
        return super.playerWillDestroy(level, pos, state, player);
    }

    /** Blown open: the sleeper comes out angry. */
    @Override
    protected void onExplosionHit(BlockState state, ServerLevel level, BlockPos pos, Explosion explosion,
                                  java.util.function.BiConsumer<ItemStack, BlockPos> drops) {
        if (sealed(state)) Sleepers.hatch(level, pos, state, null, "blast");
        super.onExplosionHit(state, level, pos, explosion, drops);
    }

    @Override
    public @Nullable BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return state.getValue(HALF) == DoubleBlockHalf.LOWER ? new SilverHuskBlockEntity(pos, state) : null;
    }

    @Override
    public <T extends BlockEntity> @Nullable BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type) {
        if (level.isClientSide() || state.getValue(HALF) != DoubleBlockHalf.LOWER || state.getValue(SPLIT) || type != PlataBlocks.SILVER_HUSK_ENTITY) return null;
        return (l, p, s, be) -> Sleepers.tick((ServerLevel) l, p, s, (SilverHuskBlockEntity) be);
    }

    public static boolean sealed(BlockState state) {
        return state.getBlock() instanceof SilverHuskBlock && state.getValue(HALF) == DoubleBlockHalf.LOWER && !state.getValue(SPLIT);
    }
}

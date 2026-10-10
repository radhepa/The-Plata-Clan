package dev.plataclan.hideout;

import java.util.List;
import java.util.Optional;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.Holder;
import net.minecraft.core.Vec3i;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.chunk.ChunkGeneratorStructureState;
import net.minecraft.world.level.levelgen.structure.StructureSet;
import net.minecraft.world.level.levelgen.structure.placement.RandomSpreadStructurePlacement;
import net.minecraft.world.level.levelgen.structure.placement.RandomSpreadType;

/**
 * Vanilla's random spread, kept clear of other structure sets: {@code avoid} lists sets (by id) and how
 * many chunks to keep from any place they could stand. Unlike vanilla's single exclusion zone it takes
 * several sets, and a set that isn't loaded (another mod's, say) is simply skipped instead of failing
 * the world. It stays a random spread, so /locate and explorer maps find it.
 */
public class AwayFrom extends RandomSpreadStructurePlacement {
    public record Avoid(ResourceKey<StructureSet> set, int chunks) {
        public static final Codec<Avoid> CODEC = RecordCodecBuilder.create(i -> i.group(
                ResourceKey.codec(Registries.STRUCTURE_SET).fieldOf("set").forGetter(Avoid::set),
                Codec.intRange(1, 16).fieldOf("chunks").forGetter(Avoid::chunks)).apply(i, Avoid::new));
    }

    public static final MapCodec<AwayFrom> CODEC = RecordCodecBuilder.<AwayFrom>mapCodec(i -> placementCodec(i).and(i.group(
            Codec.intRange(0, 4096).fieldOf("spacing").forGetter(AwayFrom::spacing),
            Codec.intRange(0, 4096).fieldOf("separation").forGetter(AwayFrom::separation),
            RandomSpreadType.CODEC.optionalFieldOf("spread_type", RandomSpreadType.LINEAR).forGetter(AwayFrom::spreadType),
            Avoid.CODEC.listOf().optionalFieldOf("avoid", List.of()).forGetter(a -> a.avoid)
    )).apply(i, AwayFrom::new)).validate(a -> a.spacing() <= a.separation()
            ? com.mojang.serialization.DataResult.error(() -> "Spacing has to be larger than separation")
            : com.mojang.serialization.DataResult.success(a));

    private final List<Avoid> avoid;

    public AwayFrom(Vec3i locateOffset, FrequencyReductionMethod frequencyReductionMethod, float frequency, int salt,
                    Optional<ExclusionZone> exclusionZone, int spacing, int separation, RandomSpreadType spreadType, List<Avoid> avoid) {
        super(locateOffset, frequencyReductionMethod, frequency, salt, exclusionZone, spacing, separation, spreadType);
        this.avoid = List.copyOf(avoid);
    }

    @Override
    protected boolean isPlacementChunk(ChunkGeneratorStructureState state, int x, int z) {
        if (!super.isPlacementChunk(state, x, z)) return false;
        for (Avoid a : this.avoid) {
            for (Holder<StructureSet> set : state.possibleStructureSets()) {
                if (set.is(a.set()) && state.hasStructureChunkInRange(set, x, z, a.chunks())) return false;
            }
        }
        return true;
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    @Override
    public MapCodec<RandomSpreadStructurePlacement> codec() {
        return (MapCodec) CODEC;
    }
}

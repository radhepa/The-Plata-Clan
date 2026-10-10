package dev.plataclan.hideout;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.QuartPos;
import net.minecraft.resources.Identifier;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.levelgen.structure.PoolElementStructurePiece;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.world.level.levelgen.structure.StructurePiece;
import net.minecraft.world.level.levelgen.structure.StructureType;
import net.minecraft.world.level.levelgen.structure.pieces.StructurePiecesBuilder;
import net.minecraft.world.level.levelgen.structure.pools.JigsawPlacement;
import net.minecraft.world.level.levelgen.structure.pools.SinglePoolElement;
import net.minecraft.world.level.levelgen.structure.pools.StructureTemplatePool;
import net.minecraft.world.level.levelgen.structure.pools.alias.PoolAliasLookup;
import net.minecraft.world.level.levelgen.structure.structures.JigsawStructure;
import net.minecraft.world.level.levelgen.structure.templatesystem.LiquidSettings;

/**
 * A Plata Clan hideout: a jigsaw structure that is a disguise on the surface and a burrow under it.
 *
 * <p>Before it grows it surveys the ground where the surface piece will stand: dry, fairly level, and
 * in the right country, and sets the piece at the median height there. The start jigsaw is the
 * surface piece's hatch at its floor (template y = 0), so that floor lands on the ground. Once the
 * burrow is assembled, every piece under the surface must keep {@code cover} blocks of ground over it:
 * an exposed room at the end of a branch is dropped (its tunnel ends in rock), and a hideout whose
 * shaft or hall would show is not built at all. The clan does not live in hillsides.
 */
public final class HideoutStructure extends Structure {
    /** Where the surface piece may stand: samples {@code step} apart out to {@code radius}. */
    public record Survey(int radius, int step, int maxHeightRange) {
        public static final Survey DEFAULT = new Survey(8, 4, 4);
        public static final Codec<Survey> CODEC = RecordCodecBuilder.create(i -> i.group(
                Codec.intRange(2, 64).optionalFieldOf("radius", DEFAULT.radius).forGetter(Survey::radius),
                Codec.intRange(1, 32).optionalFieldOf("step", DEFAULT.step).forGetter(Survey::step),
                Codec.intRange(0, 64).optionalFieldOf("max_height_range", DEFAULT.maxHeightRange).forGetter(Survey::maxHeightRange)
        ).apply(i, Survey::new));
    }

    public static final MapCodec<HideoutStructure> CODEC = RecordCodecBuilder.mapCodec(i -> i.group(
            settingsCodec(i),
            StructureTemplatePool.CODEC.fieldOf("start_pool").forGetter(s -> s.startPool),
            Identifier.CODEC.fieldOf("start_jigsaw_name").forGetter(s -> s.startJigsaw),
            Codec.intRange(0, 20).fieldOf("size").forGetter(s -> s.size),
            JigsawStructure.MaxDistance.CODEC.fieldOf("max_distance_from_center").forGetter(s -> s.maxDistance),
            Survey.CODEC.optionalFieldOf("survey", Survey.DEFAULT).forGetter(s -> s.survey),
            Codec.intRange(0, 32).optionalFieldOf("cover", 3).forGetter(s -> s.cover),
            Identifier.CODEC.optionalFieldOf("require").forGetter(s -> s.require)
    ).apply(i, HideoutStructure::new));
    public static final StructureType<HideoutStructure> TYPE = () -> CODEC;

    private final Holder<StructureTemplatePool> startPool;
    private final Identifier startJigsaw;
    private final int size;
    private final JigsawStructure.MaxDistance maxDistance;
    private final Survey survey;
    private final int cover;
    /** A piece the hideout can't do without (a Warren's vault, where its Silver Mother sleeps), or empty. */
    private final Optional<Identifier> require;

    public HideoutStructure(StructureSettings settings, Holder<StructureTemplatePool> startPool, Identifier startJigsaw, int size,
                            JigsawStructure.MaxDistance maxDistance, Survey survey, int cover, Optional<Identifier> require) {
        super(settings);
        this.startPool = startPool;
        this.startJigsaw = startJigsaw;
        this.size = size;
        this.maxDistance = maxDistance;
        this.survey = survey;
        this.cover = cover;
        this.require = require;
    }

    @Override public StructureType<?> type() { return TYPE; }

    private static int surface(GenerationContext c, int x, int z) {
        return c.chunkGenerator().getFirstOccupiedHeight(x, z, Heightmap.Types.WORLD_SURFACE_WG, c.heightAccessor(), c.randomState());
    }

    private static int floor(GenerationContext c, int x, int z) {
        return c.chunkGenerator().getFirstOccupiedHeight(x, z, Heightmap.Types.OCEAN_FLOOR_WG, c.heightAccessor(), c.randomState());
    }

    private static boolean biomeAt(GenerationContext c, int x, int y, int z) {
        return c.validBiome().test(c.biomeResolver().getNoiseBiome(QuartPos.fromBlock(x), QuartPos.fromBlock(y), QuartPos.fromBlock(z)));
    }

    /** The ground height for the surface piece, or empty when the site is wet, steep or foreign. */
    private Optional<Integer> survey(GenerationContext c, int cx, int cz) {
        if (!biomeAt(c, cx, surface(c, cx, cz), cz)) return Optional.empty();
        List<Integer> heights = new ArrayList<>();
        int r = this.survey.radius(), step = this.survey.step(), low = Integer.MAX_VALUE, high = Integer.MIN_VALUE;
        for (int dx = -r; dx <= r; dx += step) for (int dz = -r; dz <= r; dz += step) {
            int top = surface(c, cx + dx, cz + dz);
            if (top > floor(c, cx + dx, cz + dz)) return Optional.empty();  // water
            low = Math.min(low, top);
            high = Math.max(high, top);
            heights.add(top);
        }
        if (high - low > this.survey.maxHeightRange()) return Optional.empty();
        int[] sorted = heights.stream().mapToInt(Integer::intValue).sorted().toArray();
        return Optional.of(sorted[sorted.length / 2]);
    }

    @Override
    protected Optional<GenerationStub> findGenerationPoint(GenerationContext context) {
        int cx = context.chunkPos().getMiddleBlockX(), cz = context.chunkPos().getMiddleBlockZ();
        Optional<Integer> ground = this.survey(context, cx, cz);
        if (ground.isEmpty()) return Optional.empty();
        // The hatch (the start jigsaw) is at the surface piece's floor, template y = 0: without a heightmap
        // projection JigsawPlacement sets the piece one below the position, so the floor lands on the ground.
        BlockPos start = new BlockPos(cx, ground.get() + 1, cz);
        Optional<GenerationStub> stub = JigsawPlacement.addPieces(context, this.startPool, Optional.of(this.startJigsaw), this.size, start, false,
                Optional.empty(), this.maxDistance, PoolAliasLookup.EMPTY, JigsawStructure.DEFAULT_DIMENSION_PADDING,
                // never waterlog: a stair set into an aquifer would become a spring inside the hall
                LiquidSettings.IGNORE_WATERLOGGING);
        if (stub.isEmpty()) return Optional.empty();
        List<StructurePiece> pieces = stub.get().getPiecesBuilder().build().pieces();
        List<StructurePiece> kept = this.buried(context, pieces);
        if (kept == null) return Optional.empty();
        return Optional.of(new GenerationStub(stub.get().position(), (StructurePiecesBuilder builder) -> kept.forEach(builder::addPiece)));
    }

    /**
     * The pieces that stay hidden under enough ground, or null if a piece the burrow can't do without would show (or
     * the required piece never fitted at all).
     */
    private List<StructurePiece> buried(GenerationContext c, List<StructurePiece> pieces) {
        List<StructurePiece> out = new ArrayList<>(pieces.size());
        boolean found = this.require.isEmpty();
        for (int i = 0; i < pieces.size(); i++) {
            StructurePiece piece = pieces.get(i);
            boolean required = this.required(piece);
            found |= required;
            // the surface piece and the shaft or stair under its hatch are meant to meet the air
            if (i <= 1 || this.covered(c, piece.getBoundingBox())) { out.add(piece); continue; }
            boolean leaf = piece instanceof PoolElementStructurePiece p && p.getJunctions().size() <= 1;
            if (!leaf || required) return null;
        }
        return found ? out : null;
    }

    private boolean required(StructurePiece piece) {
        return this.require.isPresent() && piece instanceof PoolElementStructurePiece p && p.getElement() instanceof SinglePoolElement single
                && single.getTemplateLocation().equals(this.require.get());
    }

    private boolean covered(GenerationContext c, BoundingBox box) {
        for (int x : axis(box.minX(), box.maxX())) for (int z : axis(box.minZ(), box.maxZ())) {
            if (surface(c, x, z) - box.maxY() < this.cover || surface(c, x, z) > floor(c, x, z) && floor(c, x, z) - box.maxY() < this.cover) return false;
        }
        return true;
    }

    private static int[] axis(int min, int max) {
        int span = max - min, n = Math.max(2, Math.min(4, span / 6 + 1));
        int[] values = new int[n];
        for (int i = 0; i < n; i++) values[i] = min + span * i / (n - 1);
        return Arrays.stream(values).distinct().toArray();
    }
}

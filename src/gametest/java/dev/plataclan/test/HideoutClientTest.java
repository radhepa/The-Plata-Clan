package dev.plataclan.test;

import static dev.plataclan.test.PlataClientTest.check;
import static dev.plataclan.test.PlataClientTest.hud;
import static dev.plataclan.test.PlataClientTest.onServer;
import static dev.plataclan.test.PlataClientTest.player;
import static dev.plataclan.test.PlataClientTest.shoot;
import static dev.plataclan.test.PlataClientTest.wanted;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import com.mojang.datafixers.util.Pair;
import dev.plataclan.Balance;
import dev.plataclan.PlataClan;
import dev.plataclan.block.SilverHuskBlock;
import dev.plataclan.disguise.Disguises;
import dev.plataclan.entity.PlataMember;
import dev.plataclan.hideout.Hideouts;
import dev.plataclan.item.PlataItems;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.gui.screens.worldselection.WorldCreationUiState;
import net.minecraft.commands.arguments.blocks.BlockStateParser;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.HolderSet;
import net.minecraft.core.registries.Registries;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.TagKey;
import net.minecraft.world.entity.npc.villager.Villager;
import net.minecraft.world.level.block.entity.BannerBlockEntity;
import net.minecraft.world.level.block.entity.ChestBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.status.ChunkStatus;
import net.minecraft.world.level.levelgen.presets.WorldPresets;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.levelgen.structure.PoolElementStructurePiece;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.world.level.levelgen.structure.StructurePiece;
import net.minecraft.world.level.levelgen.structure.StructureStart;
import net.minecraft.world.level.levelgen.structure.pools.SinglePoolElement;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * The hideouts in a real, generated world: a Silver Warren and a Scale Den found the way a map finds
 * them, their lookouts, a tour with screenshots (surface, hall, vault, every room it has), the coffer
 * and the banners, then sleepers woken by a survival player, the cap on how many are up, and the
 * shells sealing again when the player leaves. A block dump of the Warren goes to hideout_dump/ for the
 * offline cutaway (tools/hideouts/cutaway.py).
 * The Silver Mother in the Warren's vault is MotherScenes.mother.
 *   gradlew runClientGameTest -PplataTests=Hideouts,Sleepers,Palettes,Mother
 */
@SuppressWarnings("UnstableApiUsage")
public class HideoutClientTest implements FabricClientGameTest {
    static final List<String> SCENES = List.of("Hideouts", "Sleepers", "Palettes", "Mother");

    @Override
    public void runTest(ClientGameTestContext ctx) {
        if (SCENES.stream().noneMatch(PlataClientTest::wanted)) return;
        ctx.getInput().resizeWindow(1280, 800);
        ctx.runOnClient(client -> {
            client.options.guiScale().set(2);
            client.options.renderDistance().set(5);
            client.options.gamma().set(.8);
            client.resizeGui();
        });
        try (TestSingleplayerContext w = ctx.worldBuilder().adjustSettings(ui -> {
            var normal = ui.getSettings().worldgenLoadContext().lookupOrThrow(Registries.WORLD_PRESET).getOrThrow(WorldPresets.NORMAL);
            ui.setWorldType(new WorldCreationUiState.WorldTypeEntry(normal));
            ui.setGenerateStructures(true);
            ui.setSeed("plata hideouts");
        }).create()) {
            settle(ctx, w);
            for (String cmd : List.of("gamemode creative @a", "gamerule advance_time false", "gamerule advance_weather false",
                    "gamerule spawn_mobs false", "time set 6000", "weather clear", "difficulty normal")) {
                w.getServer().runCommand(cmd);
            }
            hud(ctx, false);
            fly(w);
            Site warren = find(w, Hideouts.WARRENS, "warren");
            Site den = find(w, Hideouts.DENS, "den");
            if (wanted("Hideouts")) {
                tourWarren(ctx, w, warren);
                tourDen(ctx, w, den);
            }
            if (wanted("Sleepers")) sleepers(ctx, w, warren);
            if (wanted("Palettes")) palettes(ctx, w);
            if (wanted("Mother")) MotherScenes.mother(ctx, w, warren);
        }
    }

    /** Let the chunks around the camera draw; on a slow machine, give up after one wait and carry on. */
    static void settle(ClientGameTestContext ctx, TestSingleplayerContext w) {
        try { w.getConnection().waitForChunksRender(); } catch (AssertionError slow) { ctx.waitTicks(40); }
    }

    /** Creative and flying, so the camera stays where it's put. */
    static void fly(TestSingleplayerContext w) {
        w.getServer().runCommand("gamemode creative @a");
        onServer(w, level -> {
            ServerPlayer p = player(w);
            p.getAbilities().flying = true;
            p.onUpdateAbilities();
            return null;
        });
    }

    // -- finding hideouts --------------------------------------------------------------------------

    /** A generated hideout: its structure, its start (with every piece) and the pieces by template. */
    record Site(String structure, StructureStart start, Map<String, List<BoundingBox>> pieces) {
        BoundingBox surface() { return this.start.getPieces().getFirst().getBoundingBox(); }
        BoundingBox piece(String template) {
            List<BoundingBox> boxes = this.pieces.get(template);
            return boxes == null || boxes.isEmpty() ? null : boxes.getFirst();
        }
    }

    static String template(StructurePiece piece) {
        return piece instanceof PoolElementStructurePiece p && p.getElement() instanceof SinglePoolElement single
                ? single.getTemplateLocation().getPath() : piece.getClass().getSimpleName();
    }

    /** The nearest hideout in the tag, the way an explorer's map finds it, with its start loaded. */
    static Site find(TestSingleplayerContext w, TagKey<Structure> tag, String what) {
        BlockPos found = onServer(w, level -> level.findNearestMapStructure(tag, BlockPos.ZERO, 100, false));
        check(found != null, "a " + what + " generates in the world");
        Site site = onServer(w, level -> {
            ChunkAccess chunk = level.getChunk(found.getX() >> 4, found.getZ() >> 4, ChunkStatus.STRUCTURE_STARTS, true);
            var registry = level.registryAccess().lookupOrThrow(Registries.STRUCTURE);
            for (var e : chunk.getAllStarts().entrySet()) {
                Holder<Structure> holder = registry.wrapAsHolder(e.getKey());
                if (!holder.is(tag) || !e.getValue().isValid()) continue;
                Map<String, List<BoundingBox>> pieces = new LinkedHashMap<>();
                for (StructurePiece piece : e.getValue().getPieces())
                    pieces.computeIfAbsent(template(piece), k -> new ArrayList<>()).add(piece.getBoundingBox());
                return new Site(holder.unwrapKey().orElseThrow().identifier().getPath(), e.getValue(), pieces);
            }
            return null;
        });
        check(site != null, "the " + what + "'s start at " + found);
        PlataClan.LOG.info("[hideout test] {} at {}: {} pieces {}", site.structure(), found, site.start().getPieces().size(), site.pieces().keySet());
        return site;
    }

    // -- the camera ----------------------------------------------------------------------------------

    /** Put the (creative, flying) player at `eye`, looking at `at`, and wait for the world to draw. */
    static void look(ClientGameTestContext ctx, TestSingleplayerContext w, Vec3 eye, Vec3 at, int ticks) {
        double dx = at.x - eye.x, dy = at.y - eye.y, dz = at.z - eye.z;
        float yaw = (float) Math.toDegrees(Math.atan2(-dx, dz));
        float pitch = (float) -Math.toDegrees(Math.atan2(dy, Math.sqrt(dx * dx + dz * dz)));
        w.getServer().runCommand(String.format(java.util.Locale.ROOT, "tp @a %.2f %.2f %.2f %.1f %.1f", eye.x, eye.y - 1.62, eye.z, yaw, pitch));
        ctx.waitTicks(ticks);
        settle(ctx, w);
        ctx.waitTicks(10);
    }

    /**
     * A camera spot with a clear view of `at`: tried round a circle at a few distances and heights; the
     * first spot in open air with nothing (no trunk, no leaves, no wall) between it and the target.
     */
    static Vec3 clearView(TestSingleplayerContext w, Vec3 at, double[] distances, double[] heights) {
        return onServer(w, level -> {
            for (double d : distances) for (double h : heights) for (int a = 0; a < 360; a += 20) {
                double r = Math.toRadians(a + 45);
                Vec3 eye = at.add(Math.cos(r) * d, h, Math.sin(r) * d);
                BlockPos cell = BlockPos.containing(eye);
                if (!level.getBlockState(cell).getCollisionShape(level, cell).isEmpty() || !level.getBlockState(cell).getFluidState().isEmpty()) continue;
                var hit = level.clip(new net.minecraft.world.level.ClipContext(eye, at, net.minecraft.world.level.ClipContext.Block.VISUAL,
                        net.minecraft.world.level.ClipContext.Fluid.NONE, net.minecraft.world.phys.shapes.CollisionContext.empty()));
                if (hit.getType() == net.minecraft.world.phys.HitResult.Type.MISS || hit.getLocation().distanceTo(at) < 1.5) return eye;
            }
            return at.add(distances[0], heights[0], distances[0]);
        });
    }

    static Vec3 center(BoundingBox b) { return new Vec3((b.minX() + b.maxX() + 1) / 2.0, b.minY(), (b.minZ() + b.maxZ() + 1) / 2.0); }

    /** Inside a room: near one corner at head height, looking across to the far corner's floor. */
    static void inside(ClientGameTestContext ctx, TestSingleplayerContext w, BoundingBox b, String shot) {
        Vec3 eye = new Vec3(b.minX() + 2.5, b.minY() + 3.2, b.minZ() + 2.5);
        Vec3 at = new Vec3(b.maxX() - 1.5, b.minY() + 1, b.maxZ() - 1.5);
        look(ctx, w, eye, at, 30);
        shoot(ctx, shot);
    }

    // -- the Warren ------------------------------------------------------------------------------------

    static void tourWarren(ClientGameTestContext ctx, TestSingleplayerContext w, Site site) {
        check(site.piece("warren/hall") != null, "the Warren has its hall: " + site.pieces().keySet());
        check(site.piece("warren/stair") != null, "and its spiral stair");
        BoundingBox top = site.surface();
        Vec3 c = center(top).add(0, 2, 0);
        look(ctx, w, c.add(14, 8, 14), c, 40);  // get the chunks loaded first
        look(ctx, w, clearView(w, c, new double[]{14, 11, 17}, new double[]{7, 5, 10}), c, 20);
        shoot(ctx, "hideout-01-warren-surface");
        Villager lookout = onServer(w, level -> lookout(level, top));
        check(lookout != null, "the Warren's lookout waits by the ruin");
        Vec3 face = lookout.getEyePosition().add(0, -.2, 0);
        look(ctx, w, clearView(w, face, new double[]{3.5, 4.5, 5.5}, new double[]{.3, 1, 1.8}), face, 10);
        shoot(ctx, "hideout-02-warren-lookout");
        // the old cistern in the floor, close up: a broken cover over the top of the spiral stair
        BoundingBox stair = site.piece("warren/stair");
        Vec3 s = center(stair).add(0, stair.getYSpan(), 0);
        look(ctx, w, s.add(3.2, 3.4, 3.2), s.add(0, -1, 0), 20);
        shoot(ctx, "hideout-03-warren-cistern");
        BoundingBox hall = site.piece("warren/hall");
        inside(ctx, w, hall, "hideout-04-warren-hall");
        int marks = onServer(w, level -> banners(level, hall));
        check(marks >= 2, "the hall hangs the clan's banners: " + marks);
        int sleepers = onServer(w, level -> husks(level, hall, false).size());
        check(sleepers == 4, "four sleepers stand in the hall: " + sleepers);
        BoundingBox vault = site.piece("warren/vault");
        check(vault != null, "the hall's south door leads to the vault");
        BlockPos coffer = onServer(w, level -> coffer(level, vault));
        check(coffer != null, "the vault keeps the Clan Coffer");
        look(ctx, w, center(vault).add(0, 4, 0), Vec3.atCenterOf(coffer).add(0, 1.5, 0), 30);
        shoot(ctx, "hideout-05-warren-vault");
        int elders = onServer(w, level -> (int) husks(level, vault, false).stream()
                .filter(p -> level.getBlockState(p).getValue(SilverHuskBlock.KIND) == SilverHuskBlock.Kind.ELDER).count());
        check(elders == 1, "an elder sleeps over the coffer: " + elders);
        boolean pattern = onServer(w, level -> {
            ChestBlockEntity chest = (ChestBlockEntity) level.getBlockEntity(coffer);
            check(chest.getLootTable() != null && chest.getLootTable().identifier().equals(PlataClan.id("chests/clan_coffer")),
                    "the coffer rolls the coffer's loot: " + chest.getLootTable());
            chest.unpackLootTable(player(w));
            for (int i = 0; i < chest.getContainerSize(); i++) if (chest.getItem(i).is(PlataItems.PLATA_MARK_PATTERN)) return true;
            return false;
        });
        check(pattern, "the coffer always holds the Plata Mark banner pattern");
        int n = 6;
        for (String room : List.of("gallery", "pit", "forge", "nest", "shrine", "store")) {
            BoundingBox b = site.piece("warren/" + room);
            if (b == null) continue;
            inside(ctx, w, b, String.format("hideout-%02d-warren-%s", n++, room));
        }
        BoundingBox tunnel = site.piece("warren/tunnel_long") != null ? site.piece("warren/tunnel_long") : site.piece("warren/tunnel_short");
        if (tunnel != null) {
            Vec3 t = center(tunnel);
            boolean alongZ = tunnel.getZSpan() > tunnel.getXSpan();
            Vec3 eye = alongZ ? new Vec3(t.x, tunnel.minY() + 2.4, tunnel.minZ() + .8) : new Vec3(tunnel.minX() + .8, tunnel.minY() + 2.4, t.z);
            look(ctx, w, eye, alongZ ? new Vec3(t.x, tunnel.minY() + 1.8, tunnel.maxZ()) : new Vec3(tunnel.maxX(), tunnel.minY() + 1.8, t.z), 20);
            shoot(ctx, String.format("hideout-%02d-warren-tunnel", n));
        }
        dump(w, site, "warren");
    }

    // -- the Den ---------------------------------------------------------------------------------------

    static void tourDen(ClientGameTestContext ctx, TestSingleplayerContext w, Site site) {
        BoundingBox top = site.surface();
        Vec3 c = center(top).add(0, 1.5, 0);
        look(ctx, w, c.add(10, 6, 10), c, 40);
        look(ctx, w, clearView(w, c, new double[]{10, 8, 13}, new double[]{4, 6, 3}), c, 20);
        shoot(ctx, "hideout-20-den-surface");
        Villager lookout = onServer(w, level -> lookout(level, top));
        check(lookout != null, "the Den's lookout waits by the fire");
        Vec3 face = lookout.getEyePosition().add(0, -.2, 0);
        look(ctx, w, clearView(w, face, new double[]{3.5, 4.5, 5.5}, new double[]{.3, 1, 1.8}), face, 10);
        shoot(ctx, "hideout-22-den-lookout");
        BoundingBox room = site.piece("den/room_a") != null ? site.piece("den/room_a") : site.piece("den/room_b");
        check(room != null, "the Den has its room: " + site.pieces().keySet());
        inside(ctx, w, room, "hideout-21-den-room");
        int sleepers = onServer(w, level -> husks(level, room, false).size());
        check(sleepers >= 2, "two or three sleepers in the den: " + sleepers);
        dump(w, site, "den");
    }

    // -- sleepers --------------------------------------------------------------------------------------

    static void sleepers(ClientGameTestContext ctx, TestSingleplayerContext w, Site site) {
        BoundingBox hall = site.piece("warren/hall");
        BlockPos husk = onServer(w, level -> husks(level, hall, false).getFirst());
        BlockState state = onServer(w, level -> level.getBlockState(husk));
        var facing = state.getValue(SilverHuskBlock.FACING);
        Vec3 front = Vec3.atBottomCenterOf(husk).add(facing.getStepX() * 4.5, 0, facing.getStepZ() * 4.5);
        // a survival player walks up to a sleeper: it bursts out
        w.getServer().runCommand("gamemode survival @a");
        w.getServer().runCommand("effect give @a minecraft:resistance 600 4 true");
        w.getServer().runCommand("effect give @a minecraft:regeneration 600 4 true");
        look(ctx, w, front.add(0, 1.62, 0), Vec3.atCenterOf(husk.above()), 2);
        ctx.waitTicks(4);
        shoot(ctx, "hideout-30-sleeper-wakes");
        ctx.waitTicks(16);
        shoot(ctx, "hideout-31-sleeper-out");
        int[] seen = onServer(w, level -> {
            boolean split = level.getBlockState(husk).getValue(SilverHuskBlock.SPLIT);
            List<PlataMember> up = level.getEntitiesOfClass(PlataMember.class, new AABB(husk).inflate(40), PlataMember::garrison);
            boolean home = up.stream().anyMatch(m -> husk.equals(m.home()));
            return new int[]{split ? 1 : 0, home ? 1 : 0, up.size()};
        });
        check(seen[0] == 1, "the woken husk splits");
        check(seen[1] == 1, "its sleeper is out and remembers its husk");
        // its neighbours stir; never more than the cap are up at once
        int most = 0;
        for (int i = 0; i < 6; i++) {
            ctx.waitTicks(20);
            most = Math.max(most, onServer(w, level -> level.getEntitiesOfClass(PlataMember.class, new AABB(husk).inflate(40),
                    m -> m.garrison() && m.isAlive()).size()));
        }
        check(most >= 2, "the hall's other sleepers stir and wake: " + most);
        check(most <= Balance.AWAKE_CAP, "never more than " + Balance.AWAKE_CAP + " up at once: " + most);
        shoot(ctx, "hideout-32-sleepers-up");
        // sneaking past a sleeper in the gallery (if there is one) does not wake it from 5 blocks away
        // the player leaves (creative: nobody to fight); the sleepers crawl back into their shells
        fly(w);
        w.getServer().runCommand("effect clear @a");
        for (int i = 0; i < 30; i++) {
            ctx.waitTicks(20);
            int up = onServer(w, level -> level.getEntitiesOfClass(PlataMember.class, new AABB(husk).inflate(40), PlataMember::garrison).size());
            if (up == 0) break;
        }
        int[] after = onServer(w, level -> new int[]{
                level.getEntitiesOfClass(PlataMember.class, new AABB(husk).inflate(40), PlataMember::garrison).size(),
                husks(level, hall, false).size()});
        check(after[0] == 0, "with nobody to fight the sleepers go back: " + after[0] + " still up");
        check(after[1] == 4, "and their husks seal again: " + after[1] + " of 4 sealed");
        shoot(ctx, "hideout-33-sealed-again");
    }

    // -- one hideout of each style ----------------------------------------------------------------------

    static void palettes(ClientGameTestContext ctx, TestSingleplayerContext w) {
        int n = 40;
        for (String kind : List.of("warren", "den")) {
            for (String style : List.of("green", "cold", "sand", "mesa", "dry", "stone")) {
                String id = kind + "_" + style;
                BlockPos at = onServer(w, level -> {
                    var holder = level.registryAccess().lookupOrThrow(Registries.STRUCTURE).getOrThrow(
                            net.minecraft.resources.ResourceKey.create(Registries.STRUCTURE, PlataClan.id(id)));
                    Pair<BlockPos, Holder<Structure>> found = level.getChunkSource().getGenerator()
                            .findNearestMapStructure(level, HolderSet.direct(holder), BlockPos.ZERO, 60, false);
                    return found == null ? null : found.getFirst();
                });
                if (at == null) { PlataClan.LOG.warn("[hideout test] no {} within reach of this seed", id); continue; }
                Site site = onServer(w, level -> {
                    ChunkAccess chunk = level.getChunk(at.getX() >> 4, at.getZ() >> 4, ChunkStatus.STRUCTURE_STARTS, true);
                    for (var e : chunk.getAllStarts().entrySet()) {
                        var key = level.registryAccess().lookupOrThrow(Registries.STRUCTURE).getKey(e.getKey());
                        if (key != null && key.getPath().equals(id)) return new Site(id, e.getValue(), Map.of());
                    }
                    return null;
                });
                if (site == null) continue;
                Vec3 c = center(site.surface()).add(0, 2, 0);
                look(ctx, w, c.add(12, 7, 12), c, 40);
                look(ctx, w, clearView(w, c, new double[]{12, 10, 15}, new double[]{6, 4, 9}), c, 20);
                shoot(ctx, String.format("hideout-%02d-%s", n++, id.replace('_', '-')));
            }
        }
    }

    // -- looking things up ------------------------------------------------------------------------------

    static Villager lookout(ServerLevel level, BoundingBox surface) {
        AABB area = AABB.of(surface).inflate(6, 4, 6);
        for (Villager v : Disguises.loaded()) {
            var d = Disguises.disguise(v);
            if (v.level() == level && d != null && d.post() && area.contains(v.position())) return v;
        }
        return null;
    }

    /** Lower halves of the husks in the box (sealed only, or all). */
    static List<BlockPos> husks(ServerLevel level, BoundingBox box, boolean all) {
        List<BlockPos> out = new ArrayList<>();
        for (BlockPos p : BlockPos.betweenClosed(box.minX(), box.minY(), box.minZ(), box.maxX(), box.maxY(), box.maxZ())) {
            BlockState s = level.getBlockState(p);
            if (s.getBlock() instanceof SilverHuskBlock && s.getValue(SilverHuskBlock.HALF) == DoubleBlockHalf.LOWER
                    && (all || !s.getValue(SilverHuskBlock.SPLIT))) out.add(p.immutable());
        }
        return out;
    }

    static int banners(ServerLevel level, BoundingBox box) {
        int n = 0;
        for (BlockPos p : BlockPos.betweenClosed(box.minX(), box.minY(), box.minZ(), box.maxX(), box.maxY(), box.maxZ())) {
            if (level.getBlockEntity(p) instanceof BannerBlockEntity banner
                    && banner.getPatterns().layers().stream().anyMatch(l -> l.pattern().is(PlataClan.id("plata_mark")))) n++;
        }
        return n;
    }

    static BlockPos coffer(ServerLevel level, BoundingBox box) {
        for (BlockPos p : BlockPos.betweenClosed(box.minX(), box.minY(), box.minZ(), box.maxX(), box.maxY(), box.maxZ())) {
            if (level.getBlockEntity(p) instanceof ChestBlockEntity chest && chest.getLootTable() != null
                    && chest.getLootTable().identifier().getPath().equals("chests/clan_coffer")) return p.immutable();
        }
        return null;
    }

    /** Every non-air block inside the hideout's pieces, for the offline cutaway. */
    static void dump(TestSingleplayerContext w, Site site, String name) {
        String json = onServer(w, level -> {
            StringBuilder sb = new StringBuilder("{\"structure\":\"").append(site.structure()).append("\",\"pieces\":[");
            boolean first = true;
            for (StructurePiece piece : site.start().getPieces()) {
                BoundingBox b = piece.getBoundingBox();
                sb.append(first ? "" : ",").append(String.format("{\"template\":\"%s\",\"min\":[%d,%d,%d],\"max\":[%d,%d,%d]}",
                        template(piece), b.minX(), b.minY(), b.minZ(), b.maxX(), b.maxY(), b.maxZ()));
                first = false;
            }
            sb.append("],\"blocks\":[");
            first = true;
            java.util.Set<BlockPos> seen = new java.util.HashSet<>();
            for (StructurePiece piece : site.start().getPieces()) {
                BoundingBox b = piece.getBoundingBox();
                for (BlockPos p : BlockPos.betweenClosed(b.minX(), b.minY(), b.minZ(), b.maxX(), b.maxY(), b.maxZ())) {
                    BlockState s = level.getBlockState(p);
                    if (s.isAir() || !seen.add(p.immutable())) continue;
                    sb.append(first ? "" : ",").append('[').append(p.getX()).append(',').append(p.getY()).append(',').append(p.getZ())
                            .append(",\"").append(BlockStateParser.serialize(s)).append("\"]");
                    first = false;
                }
            }
            return sb.append("]}").toString();
        });
        try {
            Path out = FabricLoader.getInstance().getGameDir().resolve("hideout_dump").resolve(name + ".json");
            Files.createDirectories(out.getParent());
            Files.writeString(out, json, StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new AssertionError("couldn't write the " + name + " dump", e);
        }
    }
}

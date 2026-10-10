package dev.plataclan.test;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;

import dev.plataclan.Balance;
import dev.plataclan.PlayerPower;
import dev.plataclan.Rank;
import dev.plataclan.client.RevealHud;
import dev.plataclan.disguise.Disguises;
import dev.plataclan.entity.Move;
import dev.plataclan.entity.PlataEntities;
import dev.plataclan.entity.PlataMember;
import dev.plataclan.item.PlataItems;
import dev.villagefriends.ActionPayload;
import dev.villagefriends.client.FriendshipScreen;
import dev.villagefriends.homestead.Homesteads;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.gui.screens.worldselection.WorldCreationUiState;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.npc.villager.Villager;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.levelgen.presets.WorldPresets;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.EntityHitResult;

/**
 * The Plata Clan in a real game, with Village Friends: where a disguise turns up, the talk in Village
 * Friends' own window, the Molt (screenshots frame by frame), the three ranks, their moves, the
 * numbers in a fight, the mask, the rude reveal, death and loot, and (with -PcompatMods) the RPG.
 *   gradlew runClientGameTest -PtestHeap=2g [-PplataTests=Talk,Ranks,Moves,Fight,Mask,Rude,Death,Compat]
 * Screenshots land in build/run/clientGameTest/screenshots.
 */
@SuppressWarnings("UnstableApiUsage")
public class PlataClientTest implements FabricClientGameTest {
    static final int GROUND = -61, Y = -60;
    static final AABB ARENA = new AABB(-120, -70, -120, 120, -30, 120);

    static void check(boolean ok, String what) { if (!ok) throw new AssertionError(what); }

    static boolean wanted(String scene) {
        String only = System.getProperty("plataclan.tests");
        return only == null || only.isBlank() || List.of(only.split(",")).contains(scene);
    }

    static void shoot(ClientGameTestContext ctx, String name) {
        ctx.runOnClient(client -> client.gui.toastManager().clear());
        ctx.takeScreenshot(name);
    }

    static void hud(ClientGameTestContext ctx, boolean shown) {
        ctx.runOnClient(client -> { if (client.gui.hud.isHidden() == shown) client.gui.hud.toggle(); });
    }

    static <T> T onServer(TestSingleplayerContext w, Function<ServerLevel, T> fn) {
        return w.getServer().computeOnServer(s -> fn.apply(s.overworld()));
    }

    static ServerPlayer player(TestSingleplayerContext w) { return w.getConnection().getServerPlayer(); }

    static List<PlataMember> members(ServerLevel level) { return level.getEntitiesOfClass(PlataMember.class, ARENA); }

    /** Everything but the player gone, quietly: no deaths, no loot in the next picture. */
    static void clear(TestSingleplayerContext w, ClientGameTestContext ctx) {
        w.getServer().runOnServer(s -> {
            for (var e : s.overworld().getEntitiesOfClass(net.minecraft.world.entity.Entity.class, ARENA.inflate(64), e -> !(e instanceof net.minecraft.world.entity.player.Player))) e.discard();
        });
        ctx.waitTicks(5);
    }

    /** A disguised traveler at (x, Y, z), facing the camera at the south, frozen in place. */
    static int disguise(TestSingleplayerContext w, int x, int z) {
        return onServer(w, level -> {
            Villager v = Disguises.spawn(level, new BlockPos(x, Y, z));
            check(v != null, "a disguise spawned");
            v.setNoAi(true);
            v.snapTo(x + .5, Y, z + .5, 180, 0);
            v.yHeadRot = v.yBodyRot = 180;
            return v.getId();
        });
    }

    static PlataMember spawnMember(ServerLevel level, Rank rank, int lvl, double x, double z, float yaw, boolean ai) {
        PlataMember m = PlataEntities.of(rank).create(level, EntitySpawnReason.COMMAND);
        m.snapTo(x, Y, z, yaw, 0);
        m.yHeadRot = m.yBodyRot = yaw;
        m.configure(lvl, PlayerPower.read(level.getServer().getPlayerList().getPlayers().getFirst(), m), 1, "");
        m.setNoAi(!ai);
        m.setPersistenceRequired();
        level.addFreshEntity(m);
        return m;
    }

    @Override
    public void runTest(ClientGameTestContext ctx) {
        ctx.getInput().resizeWindow(1280, 800);
        ctx.runOnClient(client -> { client.options.guiScale().set(2); client.resizeGui(); });
        try (TestSingleplayerContext w = ctx.worldBuilder().adjustSettings(ui -> {
            var flat = ui.getSettings().worldgenLoadContext().lookupOrThrow(Registries.WORLD_PRESET).getOrThrow(WorldPresets.FLAT);
            ui.setWorldType(new WorldCreationUiState.WorldTypeEntry(flat));
            ui.setGenerateStructures(false);
            ui.setSeed("23");
        }).create()) {
            for (int attempt = 0; attempt < 3; attempt++) {
                try { w.getConnection().waitForChunksRender(); break; } catch (AssertionError slow) { ctx.waitTicks(100); }
            }
            for (String cmd : List.of("gamemode creative @a", "gamerule advance_time false", "gamerule advance_weather false",
                    "gamerule spawn_mobs false", "time set 6000", "weather clear", "difficulty normal")) {
                w.getServer().runCommand(cmd);
            }
            hud(ctx, false);
            if (wanted("Encounter")) PlataScenes.encounter(ctx, w);
            if (wanted("Talk")) PlataScenes.talk(ctx, w);
            if (wanted("Ranks")) PlataScenes.ranks(ctx, w);
            if (wanted("Moves")) PlataScenes.moves(ctx, w);
            if (wanted("Fight")) PlataScenes.fight(ctx, w);
            if (wanted("Mask")) PlataScenes.mask(ctx, w);
            if (wanted("Rude")) PlataScenes.rude(ctx, w);
            if (wanted("Death")) PlataScenes.death(ctx, w);
            if (wanted("Compat")) PlataScenes.compat(ctx, w);
        }
    }
}

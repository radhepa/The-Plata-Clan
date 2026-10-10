package dev.plataclan;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import dev.plataclan.disguise.Disguises;
import dev.plataclan.disguise.Reveal;
import dev.plataclan.entity.PlataEntities;
import dev.plataclan.entity.PlataMember;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.npc.villager.Villager;
import net.minecraft.world.level.levelgen.Heightmap;

/**
 * {@code /plata level} shows how the clan sizes you up. Operators, for testing:
 * {@code /plata encounter} puts a disguised traveler a few blocks ahead of you,
 * {@code /plata spawn <rank> [level]} a revealed member, {@code /plata reveal} drops the nearest disguise.
 */
public final class PlataCommands {
    private PlataCommands() {}

    static void register(CommandDispatcher<CommandSourceStack> d) {
        d.register(Commands.literal("plata")
                .then(Commands.literal("level").executes(c -> level(c.getSource(), c.getSource().getPlayerOrException())))
                .then(Commands.literal("encounter").requires(Commands.hasPermission(Commands.LEVEL_GAMEMASTERS))
                        .executes(c -> encounter(c.getSource(), c.getSource().getPlayerOrException(), 6)))
                .then(Commands.literal("reveal").requires(Commands.hasPermission(Commands.LEVEL_GAMEMASTERS))
                        .executes(c -> reveal(c.getSource(), c.getSource().getPlayerOrException())))
                .then(Commands.literal("spawn").requires(Commands.hasPermission(Commands.LEVEL_GAMEMASTERS))
                        .then(Commands.argument("rank", StringArgumentType.word())
                                .suggests((c, b) -> { for (Rank r : Rank.values()) b.suggest(r.id); return b.buildFuture(); })
                                .executes(c -> spawn(c, -1))
                                .then(Commands.argument("level", IntegerArgumentType.integer(1, Balance.MAX_LEVEL))
                                        .executes(c -> spawn(c, IntegerArgumentType.getInteger(c, "level")))))));
    }

    private static int level(CommandSourceStack src, ServerPlayer p) {
        int level = PlayerPower.level(p);
        String from = PlayerPower.rpgLevel(p) > 0 ? "your character level" : "your gear";
        src.sendSuccess(() -> Component.literal("The Plata Clan sizes you up at level " + level + " (from " + from + ")."), false);
        return level;
    }

    private static BlockPos ahead(ServerPlayer p, double dist) {
        double yaw = Math.toRadians(p.getYRot());
        int x = Mth.floor(p.getX() - Math.sin(yaw) * dist), z = Mth.floor(p.getZ() + Math.cos(yaw) * dist);
        return new BlockPos(x, ((ServerLevel) p.level()).getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z), z);
    }

    private static int encounter(CommandSourceStack src, ServerPlayer p, double dist) {
        Villager v = Disguises.spawn((ServerLevel) p.level(), ahead(p, dist));
        if (v == null) return 0;
        src.sendSuccess(() -> Component.literal("A traveler waits ahead: " + v.getName().getString() + "."), true);
        return 1;
    }

    private static int reveal(CommandSourceStack src, ServerPlayer p) {
        ServerLevel level = (ServerLevel) p.level();
        Villager best = null;
        for (Villager v : Disguises.loaded()) if (v.level() == level && (best == null || v.distanceToSqr(p) < best.distanceToSqr(p))) best = v;
        if (best == null || best.distanceToSqr(p) > 64 * 64) { src.sendFailure(Component.literal("No disguised traveler within 64 blocks.")); return 0; }
        Reveal.start(level, best, p, "curious");
        return 1;
    }

    private static int spawn(CommandContext<CommandSourceStack> c, int level) throws CommandSyntaxException {
        ServerPlayer p = c.getSource().getPlayerOrException();
        Rank rank = Rank.byId(StringArgumentType.getString(c, "rank"));
        ServerLevel world = (ServerLevel) p.level();
        PlataMember m = PlataEntities.of(rank).create(world, EntitySpawnReason.COMMAND);
        if (m == null) return 0;
        BlockPos at = ahead(p, 5);
        m.snapTo(at.getX() + .5, at.getY(), at.getZ() + .5, p.getYRot() + 180, 0);
        int lvl = level > 0 ? level : Balance.memberLevel(PlayerPower.level(p), rank, m.getUUID().getLeastSignificantBits());
        m.configure(lvl, PlayerPower.read(p, m), 1, "");
        if (!p.isCreative() && !p.isSpectator()) m.setTarget(p);
        world.addFreshEntity(m);
        c.getSource().sendSuccess(() -> Component.literal("A " + rank.title + " of level " + lvl + " (" + Math.round(m.getMaxHealth()) + " health)."), true);
        return 1;
    }
}

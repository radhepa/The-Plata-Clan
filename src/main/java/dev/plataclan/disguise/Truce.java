package dev.plataclan.disguise;

import java.util.ArrayList;
import java.util.List;

import dev.plataclan.PlataLines;
import dev.plataclan.PlataNet;
import dev.plataclan.entity.PlataMember;
import dev.plataclan.hideout.Hideouts;
import dev.plataclan.mother.Mothers;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;

/**
 * Talking with a Plata who lets a Mother's killer be (see {@link PlataMember#yieldTo}). Right-click them and the
 * clan's own talk window opens: they say their piece, and you may ask who the Mother was, why they let you be, about
 * the clan, or where it sleeps (they point you at the nearest hideout). After four answers, or a farewell, they go
 * back into the stone.
 */
public final class Truce {
    private Truce() {}

    public static final int MAX_ASKS = 4, REACH = 8;
    private static final List<String> ASKS = List.of("mother", "why", "clan", "hideout");

    public static void open(ServerPlayer p, PlataMember m) {
        send(p, m, line("truce.greet", m, p), false);
    }

    /** A button in the truce window: "ask_mother", "ask_why", "ask_clan", "ask_hideout", "bye", or "closed". */
    public static void act(ServerPlayer p, int entityId, String action) {
        if (!(p.level() instanceof ServerLevel level) || !(level.getEntity(entityId) instanceof PlataMember m) || !m.truce()
                || m.distanceToSqr(p) > REACH * REACH) return;
        if (action.equals("closed")) return;
        if (action.equals("bye")) {
            send(p, m, line("truce.bye", m, p), true);
            m.partIn(50);
            return;
        }
        String topic = action.startsWith("ask_") ? action.substring(4) : "";
        if (!ASKS.contains(topic)) return;
        String answer = topic.equals("hideout") ? hideout(level, m, p) : line("truce." + topic, m, p);
        if (m.ask() >= MAX_ASKS) {
            send(p, m, answer + " " + line("truce.enough", m, p), true);
            m.partIn(80);
        } else {
            send(p, m, answer, false);
        }
    }

    private static String line(String pool, PlataMember m, ServerPlayer p) {
        String text = PlataLines.pick(pool, m.rank, m.getRandom(), p.getName().getString(), m.disguise().isEmpty() ? null : m.disguise());
        return Mothers.fill(text, p);
    }

    /** Which way to the nearest hideout, roughly, and how far. */
    private static String hideout(ServerLevel level, PlataMember m, ServerPlayer p) {
        BlockPos found = level.findNearestMapStructure(Hideouts.ALL, m.blockPosition(), 32, false);
        double dx = found == null ? 0 : found.getX() - m.getX(), dz = found == null ? 0 : found.getZ() - m.getZ();
        double dist = Math.sqrt(dx * dx + dz * dz);
        if (found == null || dist > 2400) return line("truce.nohideout", m, p);
        String[] dirs = {"north", "north-east", "east", "south-east", "south", "south-west", "west", "north-west"};
        int sector = Math.floorMod(Math.round((float) (Mth.atan2(dx, -dz) * Mth.RAD_TO_DEG) / 45F), 8);
        String text = line("truce.hideout", m, p)
                .replace("{where}", "off to the " + dirs[sector])
                .replace("{far}", dist < 250 ? "not far" : dist < 700 ? "a fair walk" : "a long walk");
        return text.isEmpty() ? text : Character.toUpperCase(text.charAt(0)) + text.substring(1);
    }

    private static void send(ServerPlayer p, PlataMember m, String line, boolean closing) {
        List<String> ids = new ArrayList<>(), labels = new ArrayList<>();
        if (!closing) {
            String mother = Mothers.latest(p);
            ids.add("ask_mother"); labels.add("Who was " + (mother == null ? "the Mother" : mother) + "?");
            ids.add("ask_why"); labels.add("Why let me be?");
            ids.add("ask_clan"); labels.add("Tell me about the clan.");
            ids.add("ask_hideout"); labels.add("Where does the clan sleep?");
        }
        ids.add("bye"); labels.add(closing ? "Let them go." : "Farewell.");
        String speaker = m.disguise().isEmpty() ? m.getType().getDescription().getString() : m.disguise();
        PlataNet.truce(p, new PlataNet.TruceTalk(m.getId(), speaker, "Plata " + m.rank.title, line, ids, labels, closing));
    }
}

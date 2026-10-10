package dev.plataclan;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.minecraft.util.RandomSource;

/**
 * Everything the clan says, compiled from {@code tools/lines/*.txt} into
 * {@code data/plataclan/plata_lines.json} (read from the jar, like Village Friends' wardrobe).
 * A line may belong to one rank; {@code {player}}, {@code {name}} and {@code {rank}} are filled in.
 */
public final class PlataLines {
    public record Line(String text, Rank rank) {}

    private static final Map<String, List<Line>> POOLS = load();

    private PlataLines() {}

    public static boolean has(String pool) { return POOLS.containsKey(pool); }
    public static List<Line> pool(String pool) { return POOLS.getOrDefault(pool, List.of()); }

    /** A random line from the pool that suits the rank (rank lines and untagged ones), filled in. */
    public static String pick(String pool, Rank rank, RandomSource random, String player, String name) {
        List<Line> fits = new ArrayList<>();
        for (Line l : pool(pool)) if (l.rank() == null || l.rank() == rank) fits.add(l);
        if (fits.isEmpty()) return "";
        return fill(fits.get(random.nextInt(fits.size())).text(), rank, player, name);
    }

    /** The n-th line of a pool (wrapping), filled in: stable choices such as reply labels. */
    public static String nth(String pool, int n, Rank rank, String player, String name) {
        List<Line> lines = pool(pool);
        return lines.isEmpty() ? "" : fill(lines.get(Math.floorMod(n, lines.size())).text(), rank, player, name);
    }

    public static String fill(String text, Rank rank, String player, String name) {
        return text.replace("{player}", player == null ? "stranger" : player)
                .replace("{name}", name == null ? "the traveler" : name)
                .replace("{rank}", rank == null ? "Plata" : rank.title);
    }

    private static Map<String, List<Line>> load() {
        String path = "/data/plataclan/plata_lines.json";
        Map<String, List<Line>> pools = new HashMap<>();
        try (InputStream in = PlataLines.class.getResourceAsStream(path)) {
            if (in == null) throw new IllegalStateException("missing " + path);
            JsonObject root = JsonParser.parseReader(new InputStreamReader(in, StandardCharsets.UTF_8)).getAsJsonObject();
            if (root.get("format").getAsInt() != 1) throw new IllegalStateException(path + " has an unknown format");
            for (var pool : root.getAsJsonObject("pools").entrySet()) {
                List<Line> lines = new ArrayList<>();
                for (JsonElement e : pool.getValue().getAsJsonArray()) {
                    JsonObject o = e.getAsJsonObject();
                    JsonElement rank = o.get("rank");
                    lines.add(new Line(o.get("text").getAsString(), rank == null || rank.isJsonNull() ? null : Rank.byId(rank.getAsString())));
                }
                pools.put(pool.getKey(), List.copyOf(lines));
            }
        } catch (java.io.IOException e) {
            throw new IllegalStateException("can't read " + path, e);
        }
        return Map.copyOf(pools);
    }
}

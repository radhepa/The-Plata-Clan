package dev.plataclan;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import net.fabricmc.loader.api.FabricLoader;

/** {@code config/plataclan.json}. Missing or broken files fall back to the defaults (and are rewritten). */
public final class PlataConfig {
    /** Average minutes of eligible play between encounters (0 turns encounters off). */
    public double minutesBetweenEncounters = 25;
    /** No encounters in a player's first minutes of play. */
    public double graceMinutes = 20;
    /** True: talking opens Village Friends' conversation window with a disguise line first. False: right-click reveals at once. */
    public boolean talkFirst = true;
    /** Multiplies every Plata's health. */
    public float healthScale = 1;
    /** Multiplies every Plata's damage. */
    public float damageScale = 1;
    /** Encounters spawn this far from the player (blocks). */
    public int minDistance = 36, maxDistance = 56;
    /** No encounter this close to a known village (blocks) or within this many chunks of a village structure. */
    public int villageDistance = 160, structureChunks = 6;

    private static PlataConfig current = new PlataConfig();
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    public static PlataConfig get() { return current; }

    public static void load() {
        Path file = FabricLoader.getInstance().getConfigDir().resolve("plataclan.json");
        PlataConfig read = null;
        try {
            if (Files.exists(file)) read = GSON.fromJson(Files.readString(file), PlataConfig.class);
        } catch (Exception e) {
            PlataClan.LOG.warn("Couldn't read {}, using defaults: {}", file, e.toString());
        }
        current = read == null ? new PlataConfig() : read;
        try {
            Files.createDirectories(file.getParent());
            Files.writeString(file, GSON.toJson(current));
        } catch (IOException e) {
            PlataClan.LOG.warn("Couldn't write {}: {}", file, e.toString());
        }
    }
}

package dev.plataclan.client;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;

import com.google.gson.JsonParser;
import dev.plataclan.PlataClan;
import dev.villagefriends.animation.AnimationClip;
import dev.villagefriends.animation.AnimationLibrary;
import dev.villagefriends.animation.AnimationPack;
import org.jspecify.annotations.Nullable;

/**
 * The Plata fighter's clips ({@code assets/plataclan/plata_moves/plata.json}), in Village Friends'
 * animation-pack format and parsed by its own parser, and the Molt clip Village Friends loads as a
 * resident pack ({@code plataclan.plata:molt}).
 */
public final class PlataClips {
    private PlataClips() {}

    public static final String MOLT = "plataclan.plata:molt";
    private static Map<String, AnimationClip> moves;

    public static @Nullable AnimationClip move(String id) {
        if (moves == null) moves = load();
        return id == null ? null : moves.get(id);
    }

    /** The resident Molt clip from Village Friends' current animation library. */
    public static @Nullable AnimationClip molt() {
        AnimationLibrary library = AnimationLibrary.current();
        return library == null ? null : library.clip(MOLT);
    }

    private static Map<String, AnimationClip> load() {
        String path = "/assets/plataclan/plata_moves/plata.json";
        Map<String, AnimationClip> out = new HashMap<>();
        try (InputStream in = PlataClips.class.getResourceAsStream(path)) {
            if (in == null) {
                PlataClan.LOG.error("Missing {}: the clan will fight without their moves", path);
                return out;
            }
            AnimationPack pack = AnimationPack.parse("plataclan.moves", JsonParser.parseReader(new InputStreamReader(in, StandardCharsets.UTF_8)).getAsJsonObject());
            for (AnimationClip c : pack.clips()) out.put(local(c.id()), c);
        } catch (Exception e) {
            PlataClan.LOG.error("Couldn't read {}", path, e);
        }
        return out;
    }

    /** Clip ids come back as "pack:id"; moves look them up by the bare id. */
    private static String local(String id) {
        int colon = id.indexOf(':');
        return colon < 0 ? id : id.substring(colon + 1);
    }
}

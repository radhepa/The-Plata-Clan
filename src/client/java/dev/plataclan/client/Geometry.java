package dev.plataclan.client;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import net.minecraft.client.model.geom.ModelLayerLocation;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.PartPose;
import net.minecraft.client.model.geom.builders.CubeDeformation;
import net.minecraft.client.model.geom.builders.CubeListBuilder;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.minecraft.client.model.geom.builders.MeshDefinition;
import net.minecraft.client.model.geom.builders.PartDefinition;
import net.minecraft.resources.Identifier;

/**
 * Builds a model layer from {@code assets/plataclan/geometry/<id>.json}, written by tools/mobkit.py.
 * Parts come parent-first; each has a pivot, a rotation (radians) and box-UV cubes.
 */
public final class Geometry {
    private Geometry() {}

    public static ModelLayerLocation layer(String id) {
        return new ModelLayerLocation(Identifier.fromNamespaceAndPath("plataclan", id), "main");
    }

    public static LayerDefinition load(String id) {
        return build(read(id));
    }

    private static final Map<String, Map<String, String>> PARENTS = new HashMap<>();

    /** A named part anywhere in a baked model, found through the parent chain in the geometry file. */
    public static ModelPart part(ModelPart root, String id, String name) {
        return path(root, id, name).getLast();
    }

    /** The parts from {@code root} down to the named part (both included), to transform through them. */
    public static java.util.List<ModelPart> path(ModelPart root, String id, String name) {
        Map<String, String> parents = PARENTS.computeIfAbsent(id, k -> {
            Map<String, String> m = new HashMap<>();
            for (JsonElement el : read(k).getAsJsonArray("parts")) {
                JsonObject p = el.getAsJsonObject();
                m.put(p.get("name").getAsString(), p.get("parent").getAsString());
            }
            return m;
        });
        if (!parents.containsKey(name)) throw new IllegalArgumentException(id + " has no part " + name);
        java.util.ArrayDeque<String> names = new java.util.ArrayDeque<>();
        for (String n = name; !n.isEmpty(); n = parents.get(n)) names.push(n);
        java.util.List<ModelPart> chain = new java.util.ArrayList<>();
        ModelPart part = root;
        chain.add(part);
        for (String n : names) {
            part = part.getChild(n);
            chain.add(part);
        }
        return chain;
    }

    /** Like {@link #part} but null when the model doesn't have that (optional) part. */
    public static ModelPart optional(ModelPart root, String id, String name) {
        try {
            return part(root, id, name);
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    private static JsonObject read(String id) {
        String path = "/assets/plataclan/geometry/" + id + ".json";
        try (InputStream in = Geometry.class.getResourceAsStream(path)) {
            if (in == null) throw new IllegalStateException("missing " + path);
            return JsonParser.parseReader(new InputStreamReader(in, StandardCharsets.UTF_8)).getAsJsonObject();
        } catch (java.io.IOException e) {
            throw new IllegalStateException("can't read " + path, e);
        }
    }

    static LayerDefinition build(JsonObject json) {
        MeshDefinition mesh = new MeshDefinition();
        Map<String, PartDefinition> parts = new HashMap<>();
        parts.put("", mesh.getRoot());
        for (JsonElement el : json.getAsJsonArray("parts")) {
            JsonObject p = el.getAsJsonObject();
            CubeListBuilder cubes = CubeListBuilder.create();
            for (JsonElement ce : p.getAsJsonArray("cubes")) {
                JsonObject c = ce.getAsJsonObject();
                float[] uv = floats(c.getAsJsonArray("uv")), from = floats(c.getAsJsonArray("from")), size = floats(c.getAsJsonArray("size"));
                cubes.texOffs((int) uv[0], (int) uv[1]).mirror(c.get("mirror").getAsBoolean())
                        .addBox(from[0], from[1], from[2], size[0], size[1], size[2], new CubeDeformation(c.get("inflate").getAsFloat()));
            }
            float[] pivot = floats(p.getAsJsonArray("pivot")), rot = floats(p.getAsJsonArray("rotation"));
            PartDefinition parent = parts.get(p.get("parent").getAsString());
            if (parent == null) throw new IllegalStateException("part " + p.get("name") + " comes before its parent");
            parts.put(p.get("name").getAsString(), parent.addOrReplaceChild(p.get("name").getAsString(), cubes,
                    PartPose.offsetAndRotation(pivot[0], pivot[1], pivot[2], rot[0], rot[1], rot[2])));
        }
        JsonArray tex = json.getAsJsonArray("texture");
        return LayerDefinition.create(mesh, tex.get(0).getAsInt(), tex.get(1).getAsInt());
    }

    private static float[] floats(JsonArray a) {
        float[] out = new float[a.size()];
        for (int i = 0; i < out.length; i++) out[i] = a.get(i).getAsFloat();
        return out;
    }
}

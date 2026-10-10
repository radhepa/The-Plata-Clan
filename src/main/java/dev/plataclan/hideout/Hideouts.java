package dev.plataclan.hideout;

import dev.plataclan.PlataClan;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.tags.TagKey;
import net.minecraft.world.level.levelgen.structure.Structure;

/**
 * The Plata Clan's hideouts. Two kinds, each in six regional styles (data/plataclan/worldgen, built by
 * tools/hideouts/build.py):
 * <ul>
 *   <li><b>Scale Den</b>: a traveler's camp with a ladder under the tent down to one burrow room.</li>
 *   <li><b>Silver Warren</b>: a ruined toll-house over a spiral stair to the Molting Hall, ribbed tunnels,
 *       rooms (shell gallery, sparring pit, scale forge, the brood, the shrine, the stores) and a vault.</li>
 * </ul>
 * Members sleep in {@linkplain Sleepers silver husks}; each hideout has a {@linkplain Lookouts lookout}.
 */
public final class Hideouts {
    private Hideouts() {}

    public static final TagKey<Structure> ALL = TagKey.create(Registries.STRUCTURE, PlataClan.id("hideouts"));
    public static final TagKey<Structure> WARRENS = TagKey.create(Registries.STRUCTURE, PlataClan.id("warrens"));
    public static final TagKey<Structure> DENS = TagKey.create(Registries.STRUCTURE, PlataClan.id("dens"));

    public static void register() {
        Registry.register(BuiltInRegistries.STRUCTURE_TYPE, PlataClan.id("hideout"), HideoutStructure.TYPE);
        Registry.register(BuiltInRegistries.STRUCTURE_PLACEMENT, PlataClan.id("away_from"), AwayFrom.CODEC);
    }
}

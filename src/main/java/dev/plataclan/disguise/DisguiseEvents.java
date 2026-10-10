package dev.plataclan.disguise;

import dev.plataclan.PlataClan;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.event.Event;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.event.player.UseEntityCallback;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.npc.villager.Villager;

/**
 * The hooks: a right-click listener and a damage listener that both run in an early phase, so the
 * disguise is handled before Village Friends opens its window or counts a blow, and the server ticks.
 */
public final class DisguiseEvents {
    private DisguiseEvents() {}

    public static void register() {
        Identifier early = PlataClan.id("before_village_friends");
        UseEntityCallback.EVENT.addPhaseOrdering(early, Event.DEFAULT_PHASE);
        UseEntityCallback.EVENT.register(early, Reveal::use);
        ServerLivingEntityEvents.ALLOW_DAMAGE.addPhaseOrdering(early, Event.DEFAULT_PHASE);
        ServerLivingEntityEvents.ALLOW_DAMAGE.register(early, Reveal::allowDamage);

        ServerEntityEvents.ENTITY_LOAD.register((entity, level) -> { if (entity instanceof Villager v) Disguises.loaded(v); });
        ServerEntityEvents.ENTITY_UNLOAD.register((entity, level) -> { if (entity instanceof Villager v) Disguises.unloaded(v); });
        ServerTickEvents.END_SERVER_TICK.register(server -> {
            Disguises.tick(server);
            Reveal.tick(server);
        });
        ServerLifecycleEvents.SERVER_STOPPED.register(server -> { Reveal.clear(); Disguises.loaded().clear(); });
    }
}

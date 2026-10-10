package dev.plataclan;

import dev.plataclan.disguise.DisguiseEvents;
import dev.plataclan.disguise.Disguises;
import dev.plataclan.entity.PlataEntities;
import dev.plataclan.item.PlataItems;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.minecraft.resources.Identifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * The Plata Clan: lone travelers in the wild who are not what they seem. An add-on for Village
 * Friends (which it needs); Village Friends never needs it.
 */
public final class PlataClan implements ModInitializer {
    public static final String MOD_ID = "plataclan";
    public static final Logger LOG = LoggerFactory.getLogger("The Plata Clan");

    public static Identifier id(String path) { return Identifier.fromNamespaceAndPath(MOD_ID, path); }

    @Override
    public void onInitialize() {
        PlataConfig.load();
        PlataEntities.register();
        PlataItems.register();
        java.util.Objects.requireNonNull(Disguises.DISGUISE);
        PlataNet.register();
        DisguiseEvents.register();
        CommandRegistrationCallback.EVENT.register((dispatcher, registries, environment) -> PlataCommands.register(dispatcher));
        LOG.info("The Plata Clan is in the stone ({} lines to say)", PlataLines.pool("defeat").size() > 0 ? "plenty of" : "no");
    }
}

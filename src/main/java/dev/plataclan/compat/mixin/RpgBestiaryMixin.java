package dev.plataclan.compat.mixin;

import dev.plataclan.compat.CompatMixinPlugin;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

/**
 * Village Friends RPG keys its Bestiary by entity path. Every Plata counts toward the Vermin family
 * (silverfish and endermites), whose Legend ability is Burrow Rush. Applied only when the RPG add-on
 * is installed (see {@link CompatMixinPlugin}).
 */
@Pseudo
@Mixin(targets = "dev.villagefriends.rpg.Bestiary", remap = false)
public abstract class RpgBestiaryMixin {
    @ModifyVariable(method = "ofMob", at = @At("HEAD"), argsOnly = true, remap = false)
    private static String plataclan$vermin(String path) {
        return path != null && path.startsWith("plata_") ? "silverfish" : path;
    }
}

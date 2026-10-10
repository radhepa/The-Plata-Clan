package dev.plataclan.mixin;

import dev.plataclan.disguise.Disguises;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.LightningBolt;
import net.minecraft.world.entity.npc.villager.Villager;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Lightning doesn't make a witch of a disguised Plata: they were never quite a villager. */
@Mixin(Villager.class)
public abstract class VillagerThunderMixin {
    @Inject(method = "thunderHit", at = @At("HEAD"), cancellable = true)
    private void plataclan$notAWitch(ServerLevel level, LightningBolt bolt, CallbackInfo ci) {
        if (Disguises.disguised((Villager) (Object) this)) ci.cancel();
    }
}

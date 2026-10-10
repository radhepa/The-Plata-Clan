package dev.plataclan.client.mixin;

import dev.plataclan.client.PlataClips;
import dev.plataclan.disguise.Disguises;
import dev.villagefriends.animation.AnimationClip;
import dev.villagefriends.client.ResidentRenderState;
import dev.villagefriends.client.ResidentRenderer;
import net.fabricmc.fabric.api.attachment.v1.AttachmentTarget;
import net.minecraft.world.entity.npc.villager.Villager;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * The Molt, played on the disguised resident itself: once the server marks the moment it began, the
 * Molt clip replaces whatever the resident was doing, in step with the server's swap at 1.2 s.
 */
@Mixin(ResidentRenderer.class)
public abstract class ResidentMoltMixin {
    @Inject(method = "extractRenderState(Lnet/minecraft/world/entity/npc/villager/Villager;Ldev/villagefriends/client/ResidentRenderState;F)V",
            at = @At("TAIL"))
    private void plataclan$molt(Villager villager, ResidentRenderState state, float delta, CallbackInfo ci) {
        Long start = ((AttachmentTarget) villager).getAttached(Disguises.MOLT);
        if (start == null) return;
        AnimationClip clip = PlataClips.molt();
        if (clip == null) return;
        float t = (villager.level().getGameTime() - start + delta) / 20F;
        state.layers[0].clear();
        state.layers[2].clear();
        state.layers[1].set(clip, Math.min(t, clip.length()), t < .15F ? AnimationClip.smooth(t / .15F) : 1, false, 1);
        state.bubble = null;
    }
}

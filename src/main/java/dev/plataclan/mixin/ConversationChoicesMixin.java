package dev.plataclan.mixin;

import java.util.List;

import dev.plataclan.disguise.Reveal;
import dev.villagefriends.FriendshipPayload;
import dev.villagefriends.NarrativeEngine;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.npc.villager.Villager;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** The reply bubbles of a disguise talk (Village Friends' own window, the plataclan tab). */
@Mixin(NarrativeEngine.class)
public abstract class ConversationChoicesMixin {
    @Inject(method = "choices", at = @At("HEAD"), cancellable = true)
    private static void plataclan$disguise(Villager v, ServerPlayer p, String tab, CallbackInfoReturnable<List<FriendshipPayload.Choice>> cir) {
        if (!tab.startsWith(Reveal.TAB)) return;
        List<FriendshipPayload.Choice> choices = Reveal.choices(v, p, tab);
        if (choices != null) cir.setReturnValue(choices);
    }
}

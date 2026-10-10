package dev.plataclan.mixin;

import dev.plataclan.disguise.Reveal;
import dev.villagefriends.ActionPayload;
import dev.villagefriends.VillageFriends;
import net.minecraft.server.level.ServerPlayer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Any click in a disguised traveler's window (our replies or Village Friends' own buttons) drops the disguise. */
@Mixin(VillageFriends.class)
public abstract class ConversationActionMixin {
    @Inject(method = "handleAction", at = @At("HEAD"), cancellable = true)
    private static void plataclan$reply(ServerPlayer player, ActionPayload action, CallbackInfo ci) {
        if (Reveal.reply(player, action.entityId(), action.action()) || action.action().startsWith(Reveal.ACTION)) ci.cancel();
    }
}

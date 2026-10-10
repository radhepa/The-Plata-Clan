package dev.plataclan.client;

import dev.plataclan.PlataNet;
import dev.plataclan.Rank;
import dev.plataclan.entity.PlataEntities;
import dev.villagefriends.client.FriendshipScreen;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.client.rendering.v1.EntityRendererRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.ModelLayerRegistry;
import net.fabricmc.fabric.api.client.screen.v1.ScreenEvents;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.ThrownItemRenderer;

/**
 * Models and renderers for the three ranks, the shard and the Silver Mother, the speech line and title cards, the
 * talk window hooks, and the truce window.
 */
public final class PlataClanClient implements ClientModInitializer {
    /** The resident whose disguise talk is open on this client, or -1. */
    private static int talking = -1;

    @Override
    public void onInitializeClient() {
        for (Rank rank : Rank.values()) {
            String id = "plata_" + rank.id;
            ModelLayerRegistry.registerModelLayer(Geometry.layer(id), () -> Geometry.load(id));
            EntityRendererRegistry.register(PlataEntities.of(rank), (EntityRendererProvider.Context ctx) -> new PlataRenderer(ctx, rank));
        }
        EntityRendererRegistry.register(PlataEntities.SILVER_SHARD, ThrownItemRenderer::new);
        ModelLayerRegistry.registerModelLayer(Geometry.layer(MotherRenderer.ID), () -> Geometry.load(MotherRenderer.ID));
        EntityRendererRegistry.register(PlataEntities.SILVER_MOTHER, MotherRenderer::new);
        RevealHud.register();

        ClientPlayNetworking.registerGlobalReceiver(PlataNet.Speech.TYPE, (payload, context) -> {
            if (!payload.card().isEmpty()) RevealHud.card(payload.card(), payload.text());
            else RevealHud.speech(payload.speaker(), payload.rank(), payload.text());
        });
        // A Plata who lets a Mother's killer be has something to say.
        ClientPlayNetworking.registerGlobalReceiver(PlataNet.TruceTalk.TYPE, (payload, context) -> {
            if (context.client().gui.screen() instanceof TruceScreen open && open.entityId() == payload.entityId()) open.update(payload);
            else context.client().gui.setScreen(new TruceScreen(payload));
        });
        ClientPlayNetworking.registerGlobalReceiver(PlataNet.TalkOpened.TYPE, (payload, context) -> talking = payload.entityId());
        ClientPlayNetworking.registerGlobalReceiver(PlataNet.CloseTalk.TYPE, (payload, context) -> {
            if (talking == payload.entityId()) talking = -1;
            if (context.client().gui.screen() instanceof FriendshipScreen s && s.residentId() == payload.entityId()) context.client().gui.setScreen(null);
        });
        // Shutting the window on a disguise without answering is an answer too.
        ScreenEvents.AFTER_INIT.register((client, screen, width, height) -> {
            if (!(screen instanceof FriendshipScreen talk)) return;
            ScreenEvents.remove(screen).register(closed -> {
                int id = talk.residentId();
                if (id == talking && !(client.gui.screen() instanceof FriendshipScreen next && next.residentId() == id)) {
                    talking = -1;
                    ClientPlayNetworking.send(new PlataNet.TalkClosed(id));
                }
            });
        });
    }
}

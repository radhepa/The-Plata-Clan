package dev.plataclan;

import dev.plataclan.disguise.Reveal;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;

/** What the server and clients tell each other that vanilla and Village Friends packets can't. */
public final class PlataNet {
    private PlataNet() {}

    /**
     * A line someone of the clan says out loud, shown as a speech line at the bottom of the screen.
     * {@code card} shows the big THE PLATA CLAN title card with the line under it instead (a
     * player's first encounter).
     */
    public record Speech(int entityId, String speaker, String rank, String text, boolean card) implements CustomPacketPayload {
        public static final Type<Speech> TYPE = new Type<>(PlataClan.id("speech"));
        public static final StreamCodec<FriendlyByteBuf, Speech> CODEC = StreamCodec.of(
                (buf, s) -> { buf.writeVarInt(s.entityId); buf.writeUtf(s.speaker, 64); buf.writeUtf(s.rank, 32); buf.writeUtf(s.text, 256); buf.writeBoolean(s.card); },
                buf -> new Speech(buf.readVarInt(), buf.readUtf(64), buf.readUtf(32), buf.readUtf(256), buf.readBoolean()));
        @Override public Type<Speech> type() { return TYPE; }
    }

    /** The disguise is dropping: close Village Friends' conversation window so the player sees the Molt. */
    public record CloseTalk(int entityId) implements CustomPacketPayload {
        public static final Type<CloseTalk> TYPE = new Type<>(PlataClan.id("close_talk"));
        public static final StreamCodec<FriendlyByteBuf, CloseTalk> CODEC = StreamCodec.of(
                (buf, c) -> buf.writeVarInt(c.entityId), buf -> new CloseTalk(buf.readVarInt()));
        @Override public Type<CloseTalk> type() { return TYPE; }
    }

    /** A disguise talk opened on this resident, so the client can say when its window is shut unanswered. */
    public record TalkOpened(int entityId) implements CustomPacketPayload {
        public static final Type<TalkOpened> TYPE = new Type<>(PlataClan.id("talk_opened"));
        public static final StreamCodec<FriendlyByteBuf, TalkOpened> CODEC = StreamCodec.of(
                (buf, c) -> buf.writeVarInt(c.entityId), buf -> new TalkOpened(buf.readVarInt()));
        @Override public Type<TalkOpened> type() { return TYPE; }
    }

    /** The player shut a disguise talk without answering. */
    public record TalkClosed(int entityId) implements CustomPacketPayload {
        public static final Type<TalkClosed> TYPE = new Type<>(PlataClan.id("talk_closed"));
        public static final StreamCodec<FriendlyByteBuf, TalkClosed> CODEC = StreamCodec.of(
                (buf, c) -> buf.writeVarInt(c.entityId), buf -> new TalkClosed(buf.readVarInt()));
        @Override public Type<TalkClosed> type() { return TYPE; }
    }

    static void register() {
        PayloadTypeRegistry.clientboundPlay().register(Speech.TYPE, Speech.CODEC);
        PayloadTypeRegistry.clientboundPlay().register(CloseTalk.TYPE, CloseTalk.CODEC);
        PayloadTypeRegistry.clientboundPlay().register(TalkOpened.TYPE, TalkOpened.CODEC);
        PayloadTypeRegistry.serverboundPlay().register(TalkClosed.TYPE, TalkClosed.CODEC);
        ServerPlayNetworking.registerGlobalReceiver(TalkClosed.TYPE, (payload, ctx) -> Reveal.closed(ctx.player(), payload.entityId()));
    }

    public static void say(ServerPlayer p, Entity who, String speaker, Rank rank, String text, boolean card) {
        if (text == null || text.isEmpty() || !ServerPlayNetworking.canSend(p, Speech.TYPE)) return;
        ServerPlayNetworking.send(p, new Speech(who.getId(), speaker, rank == null ? "" : rank.title, text, card));
    }

    public static void closeTalk(ServerPlayer p, Entity who) {
        if (ServerPlayNetworking.canSend(p, CloseTalk.TYPE)) ServerPlayNetworking.send(p, new CloseTalk(who.getId()));
    }

    public static void talkOpened(ServerPlayer p, Entity who) {
        if (ServerPlayNetworking.canSend(p, TalkOpened.TYPE)) ServerPlayNetworking.send(p, new TalkOpened(who.getId()));
    }
}

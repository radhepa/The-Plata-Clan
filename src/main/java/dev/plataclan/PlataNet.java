package dev.plataclan;

import java.util.ArrayList;
import java.util.List;

import dev.plataclan.disguise.Reveal;
import dev.plataclan.disguise.Truce;
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
     * A line someone of the clan says out loud, shown as a speech line at the bottom of the screen. A non-empty
     * {@code card} shows a big title card with that title and the line under it instead (THE PLATA CLAN on a
     * player's first encounter; a Silver Mother's name when she rises, and again when she falls).
     */
    public record Speech(int entityId, String speaker, String rank, String text, String card) implements CustomPacketPayload {
        public static final Type<Speech> TYPE = new Type<>(PlataClan.id("speech"));
        public static final StreamCodec<FriendlyByteBuf, Speech> CODEC = StreamCodec.of(
                (buf, s) -> { buf.writeVarInt(s.entityId); buf.writeUtf(s.speaker, 64); buf.writeUtf(s.rank, 32); buf.writeUtf(s.text, 256); buf.writeUtf(s.card, 64); },
                buf -> new Speech(buf.readVarInt(), buf.readUtf(64), buf.readUtf(32), buf.readUtf(256), buf.readUtf(64)));
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

    /**
     * A revealed Plata who lets a Mother's killer be is talking to them: opens (or updates) the clan's own talk
     * window with what they said and the questions the player may ask. {@code closing}: their last word.
     */
    public record TruceTalk(int entityId, String speaker, String title, String line, List<String> ids, List<String> labels,
                            boolean closing) implements CustomPacketPayload {
        public static final Type<TruceTalk> TYPE = new Type<>(PlataClan.id("truce_talk"));
        public static final StreamCodec<FriendlyByteBuf, TruceTalk> CODEC = StreamCodec.of(
                (buf, t) -> {
                    buf.writeVarInt(t.entityId); buf.writeUtf(t.speaker, 64); buf.writeUtf(t.title, 64); buf.writeUtf(t.line, 512);
                    buf.writeVarInt(t.ids.size());
                    for (int i = 0; i < t.ids.size(); i++) { buf.writeUtf(t.ids.get(i), 32); buf.writeUtf(t.labels.get(i), 64); }
                    buf.writeBoolean(t.closing);
                },
                buf -> {
                    int id = buf.readVarInt();
                    String speaker = buf.readUtf(64), title = buf.readUtf(64), line = buf.readUtf(512);
                    int n = Math.min(8, buf.readVarInt());
                    List<String> ids = new ArrayList<>(), labels = new ArrayList<>();
                    for (int i = 0; i < n; i++) { ids.add(buf.readUtf(32)); labels.add(buf.readUtf(64)); }
                    return new TruceTalk(id, speaker, title, line, ids, labels, buf.readBoolean());
                });
        @Override public Type<TruceTalk> type() { return TYPE; }
    }

    /** The player asked something in the truce window (or said goodbye, or shut it: "closed"). */
    public record TruceAction(int entityId, String action) implements CustomPacketPayload {
        public static final Type<TruceAction> TYPE = new Type<>(PlataClan.id("truce_action"));
        public static final StreamCodec<FriendlyByteBuf, TruceAction> CODEC = StreamCodec.of(
                (buf, a) -> { buf.writeVarInt(a.entityId); buf.writeUtf(a.action, 32); },
                buf -> new TruceAction(buf.readVarInt(), buf.readUtf(32)));
        @Override public Type<TruceAction> type() { return TYPE; }
    }

    static void register() {
        PayloadTypeRegistry.clientboundPlay().register(Speech.TYPE, Speech.CODEC);
        PayloadTypeRegistry.clientboundPlay().register(CloseTalk.TYPE, CloseTalk.CODEC);
        PayloadTypeRegistry.clientboundPlay().register(TalkOpened.TYPE, TalkOpened.CODEC);
        PayloadTypeRegistry.clientboundPlay().register(TruceTalk.TYPE, TruceTalk.CODEC);
        PayloadTypeRegistry.serverboundPlay().register(TalkClosed.TYPE, TalkClosed.CODEC);
        PayloadTypeRegistry.serverboundPlay().register(TruceAction.TYPE, TruceAction.CODEC);
        ServerPlayNetworking.registerGlobalReceiver(TalkClosed.TYPE, (payload, ctx) -> Reveal.closed(ctx.player(), payload.entityId()));
        ServerPlayNetworking.registerGlobalReceiver(TruceAction.TYPE, (payload, ctx) -> Truce.act(ctx.player(), payload.entityId(), payload.action()));
    }

    /** A speech line from a member of the clan (the rank's title under their name). */
    public static void say(ServerPlayer p, Entity who, String speaker, Rank rank, String text, boolean card) {
        say(p, who, speaker, rank == null ? "" : rank.title, text, card ? "THE PLATA CLAN" : "");
    }

    /** A speech line under any title, or with {@code card} a title card. */
    public static void say(ServerPlayer p, Entity who, String speaker, String title, String text, String card) {
        if (text == null || text.isEmpty() || !ServerPlayNetworking.canSend(p, Speech.TYPE)) return;
        ServerPlayNetworking.send(p, new Speech(who.getId(), speaker, title, text, card));
    }

    public static void closeTalk(ServerPlayer p, Entity who) {
        if (ServerPlayNetworking.canSend(p, CloseTalk.TYPE)) ServerPlayNetworking.send(p, new CloseTalk(who.getId()));
    }

    public static void talkOpened(ServerPlayer p, Entity who) {
        if (ServerPlayNetworking.canSend(p, TalkOpened.TYPE)) ServerPlayNetworking.send(p, new TalkOpened(who.getId()));
    }

    public static void truce(ServerPlayer p, TruceTalk talk) {
        if (ServerPlayNetworking.canSend(p, TruceTalk.TYPE)) ServerPlayNetworking.send(p, talk);
    }
}

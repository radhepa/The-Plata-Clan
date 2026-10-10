package dev.plataclan.client;

import java.util.ArrayList;
import java.util.List;

import dev.plataclan.PlataNet;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.LivingEntity;

/**
 * Talking with a Plata who lets a Mother's killer be: the clan's own window, in its slate and silver. They stay in
 * view in the world; a box at the bottom holds their likeness, their name and what they say (typed out, with the
 * faint skitter of a silverfish); the questions you may ask sit above it on the right. Their last word leaves only
 * one button: let them go.
 */
public final class TruceScreen extends Screen {
    private static final int SLATE = 0xF024282D, SLATE_LIGHT = 0xFF34393F, EDGE = 0xFF858C95, SILVER = 0xFFE6EAEE, PLATE = 0xFFA9B0B8,
            GLINT = 0xFFBFE6EE, MUTED = 0xFF6E767F, LINE = 10, KEY_1 = 49, SPACE = 32;
    private static final float CHARS_PER_SECOND = 40F;

    private PlataNet.TruceTalk data;
    private final List<Button> buttons = new ArrayList<>();
    private List<String> lines = List.of();
    private String laidOut = "";
    private int boxX, boxY, boxW, boxH, textX, textW, portraitX, portraitY, portraitW = 56, portraitH;
    private float shown;
    private long lastFrame;
    private boolean waiting;
    private int ticks, waitTicks;

    public TruceScreen(PlataNet.TruceTalk data) {
        super(Component.literal(data.speaker()));
        this.data = data;
    }

    public PlataNet.TruceTalk data() { return this.data; }
    public int entityId() { return this.data.entityId(); }

    public void update(PlataNet.TruceTalk next) {
        boolean changed = !next.line().equals(this.data.line());
        this.data = next;
        this.waiting = false;
        if (changed) this.shown = 0;
        this.rebuildWidgets();
    }

    /** Presses a question or the goodbye, as a click would (for tests). */
    public void press(String action) { this.choose(action); }

    private void choose(String action) {
        if (this.waiting) return;
        if (this.data.closing()) { this.onClose(); return; }
        if (!ClientPlayNetworking.canSend(PlataNet.TruceAction.TYPE)) return;
        ClientPlayNetworking.send(new PlataNet.TruceAction(this.data.entityId(), action));
        this.waiting = true;
        this.waitTicks = 0;
        this.buttons.forEach(b -> b.active = false);
    }

    // -- layout -------------------------------------------------------------------------------------------

    @Override
    protected void init() {
        this.buttons.clear();
        this.boxW = Math.min(420, this.width - 16);
        this.boxX = (this.width - this.boxW) / 2;
        this.textX = this.boxX + this.portraitW + 16;
        this.textW = this.boxX + this.boxW - 10 - this.textX;
        int n = this.font.splitIgnoringLanguage(Component.literal(this.data.line()), this.textW).size();
        this.boxH = Math.clamp(n, 3, 9) * LINE + 22;
        this.boxY = this.height - 10 - this.boxH;
        this.portraitH = Math.min(this.boxH + 26, 104);
        this.portraitX = this.boxX + 6;
        this.portraitY = this.boxY + this.boxH - 6 - this.portraitH;
        int row = 18, w = Math.clamp(this.width * 2 / 5, 140, 200);
        int right = this.boxX + this.boxW, bottom = this.boxY - 16;
        int count = this.data.ids().size();
        for (int i = 0; i < count; i++) {
            String id = this.data.ids().get(i);
            int y = bottom - (count - i) * row;
            var b = new Reply(right - w, y, w, row - 3, i + 1, this.data.labels().get(i), pressed -> this.choose(id));
            b.active = !this.waiting;
            this.buttons.add(b);
            this.addRenderableWidget(b);
        }
    }

    // -- input --------------------------------------------------------------------------------------------

    @Override
    public boolean keyPressed(KeyEvent event) {
        if (event.key() == SPACE && this.typing()) { this.shown = Float.MAX_VALUE; return true; }
        int i = event.key() - KEY_1;
        if (i >= 0 && i < this.data.ids().size() && !this.waiting) { this.choose(this.data.ids().get(i)); return true; }
        return super.keyPressed(event);
    }

    @Override
    public void tick() {
        this.ticks++;
        var mc = this.minecraft;
        if (mc.player == null || mc.level == null) { this.onClose(); return; }
        var who = mc.level.getEntity(this.data.entityId());
        if (who == null || !who.isAlive() || who.isInvisible() || mc.player.distanceToSqr(who) > 10 * 10 || !mc.player.isAlive()) {
            this.onClose();
            return;
        }
        if (this.waiting && ++this.waitTicks > 100) { this.waiting = false; this.rebuildWidgets(); }
    }

    @Override public boolean isPauseScreen() { return false; }
    @Override public boolean isInGameUi() { return true; }

    @Override
    public Component getNarrationMessage() {
        return Component.literal(this.data.speaker() + ", " + this.data.title() + ". " + this.data.line());
    }

    // -- drawing ------------------------------------------------------------------------------------------

    private boolean typing() { return this.shown < String.join("", this.lines).length(); }

    @Override
    public void extractBackground(GuiGraphicsExtractor g, int mouseX, int mouseY, float delta) {
        g.fillGradient(0, this.height / 2, this.width, this.height, 0x00000000, 0x66080A0C);
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor g, int mouseX, int mouseY, float delta) {
        if (!this.laidOut.equals(this.data.line())) {
            this.laidOut = this.data.line();
            this.lines = this.font.splitIgnoringLanguage(Component.literal(this.data.line()), this.textW).stream().map(t -> t.getString()).toList();
        }
        this.type();
        this.panel(g, this.boxX, this.boxY, this.boxW, this.boxH);
        // their likeness
        g.fill(this.portraitX - 2, this.portraitY - 2, this.portraitX + this.portraitW + 2, this.portraitY + this.portraitH + 2, EDGE);
        g.fillGradient(this.portraitX, this.portraitY, this.portraitX + this.portraitW, this.portraitY + this.portraitH, 0xFF464C54, 0xFF24282D);
        if (this.minecraft.level != null && this.minecraft.level.getEntity(this.data.entityId()) instanceof LivingEntity who) {
            float h = who.getBbHeight(), eye = who.getEyeHeight(), span = Math.clamp(h * .55F, 1.0F, 1.9F);
            InventoryScreen.extractEntityInInventoryFollowsMouse(g, this.portraitX, this.portraitY, this.portraitX + this.portraitW,
                    this.portraitY + this.portraitH, (int) (this.portraitH / span), (eye - span * .32F) - h / 2F,
                    this.portraitX + this.portraitW / 2F - 10, this.portraitY + this.portraitH / 3F, who);
        }
        // name ribbon
        String name = this.font.plainSubstrByWidth(this.data.speaker(), this.boxW - this.portraitW - 40);
        int rw = this.font.width(name) + 14, rx = this.textX - 4, ry = this.boxY - 12;
        g.fill(rx, ry, rx + rw, ry + 13, 0xFF5E646C);
        g.fill(rx, ry + 11, rx + rw, ry + 13, 0xFF464C54);
        g.text(this.font, name, rx + 7, ry + 3, SILVER, true);
        g.text(this.font, this.font.plainSubstrByWidth(this.data.title(), this.boxX + this.boxW - rx - rw - 12), rx + rw + 6, ry + 3, PLATE, true);
        // what they say
        int left = (int) Math.min(this.shown, Integer.MAX_VALUE), y = this.boxY + 9;
        for (String l : this.lines) {
            if (left <= 0) break;
            String part = l.substring(0, Math.min(l.length(), left));
            g.text(this.font, part, this.textX, y, SILVER, false);
            left -= l.length();
            y += LINE;
        }
        if (this.waiting) g.text(this.font, "...", this.boxX + this.boxW - 18, this.boxY + this.boxH - 12, MUTED, false);
        else if (!this.typing()) {
            int bounce = (int) (Math.abs(Mth.sin((this.ticks + delta) * .25F)) * 2);
            g.fill(this.boxX + this.boxW - 12, this.boxY + this.boxH - 9 + bounce, this.boxX + this.boxW - 8, this.boxY + this.boxH - 7 + bounce, GLINT);
        }
        super.extractRenderState(g, mouseX, mouseY, delta);
    }

    private void type() {
        long now = System.nanoTime();
        float dt = this.lastFrame == 0 ? 0 : Math.min(.25F, (now - this.lastFrame) / 1.0E9F);
        this.lastFrame = now;
        int before = (int) this.shown;
        if (this.typing()) this.shown += dt * CHARS_PER_SECOND;
        if ((int) this.shown / 3 != before / 3 && this.typing() && this.minecraft != null) {
            this.minecraft.getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.SILVERFISH_STEP, 1.6F + (before % 5) * .05F, .12F));
        }
    }

    private void panel(GuiGraphicsExtractor g, int x, int y, int w, int h) {
        g.fill(x + 1, y + h, x + w - 1, y + h + 2, 0x55000000);
        g.fill(x, y, x + w, y + h, EDGE);
        g.fill(x + 1, y + 1, x + w - 1, y + h - 1, SLATE);
        g.fill(x + 1, y + 1, x + w - 1, y + 2, 0xFF5C636B);
    }

    /** A numbered question, slate with a silver edge. */
    static final class Reply extends Button {
        private final int number;

        Reply(int x, int y, int w, int h, int number, String label, OnPress action) {
            super(x, y, w, h, Component.literal(label), action, DEFAULT_NARRATION);
            this.number = number;
        }

        @Override
        protected void extractContents(GuiGraphicsExtractor g, int mouseX, int mouseY, float delta) {
            boolean hover = this.active && this.isHoveredOrFocused();
            int x = this.getX() + (hover ? -2 : 0), y = this.getY(), w = this.getWidth(), h = this.getHeight();
            g.fill(x, y, x + w, y + h, this.active ? EDGE : 0xFF464C54);
            g.fill(x + 1, y + 1, x + w - 1, y + h - 1, hover ? SLATE_LIGHT : SLATE);
            g.fill(x + 2, y + 2, x + 11, y + h - 2, hover ? GLINT : 0xFF5E646C);
            var font = Minecraft.getInstance().font;
            g.centeredText(font, String.valueOf(this.number), x + 7, y + (h - 8) / 2, hover ? 0xFF24282D : SILVER);
            g.text(font, font.plainSubstrByWidth(this.getMessage().getString(), w - 18), x + 15, y + (h - 8) / 2, this.active ? SILVER : MUTED, false);
        }
    }
}

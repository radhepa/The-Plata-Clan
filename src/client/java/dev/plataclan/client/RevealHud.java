package dev.plataclan.client;

import dev.plataclan.PlataClan;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.hud.VanillaHudElements;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.resources.Identifier;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.Util;

/**
 * What the clan says, on screen: a speech line above the hotbar (who's speaking, in silver, and what
 * they say), and on a player's first encounter a title card in the style of Village Friends' arrival
 * banner: the clan's mark, THE PLATA CLAN in large spaced letters, and the creed under a rule.
 */
public final class RevealHud {
    private static final long CARD_IN = 600, CARD_HOLD = 3800, CARD_OUT = 1400;
    private static final int SILVER = 0xE6EAEE, PLATE = 0xA9B0B8, GLINT = 0xBFE6EE, SLATE = 0x24282D;
    private static final Identifier MARK = PlataClan.id("textures/gui/plata_mark.png");

    private static String speaker, rank, line, cardLine;
    private static long lineStart, lineLength, cardStart;

    private RevealHud() {}

    public static void register() {
        HudElementRegistry.attachElementBefore(VanillaHudElements.TITLE_AND_SUBTITLE, PlataClan.id("speech"), RevealHud::draw);
    }

    public static void speech(String who, String title, String text) {
        speaker = who; rank = title; line = text;
        lineStart = Util.getMillis();
        lineLength = 2600 + 45L * text.length();
    }

    public static void card(String text) {
        cardLine = text;
        cardStart = Util.getMillis();
        var client = Minecraft.getInstance();
        client.getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.SILVERFISH_AMBIENT, .55F, .8F));
        client.getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.AMETHYST_BLOCK_CHIME, .5F, .7F));
    }

    /** Tests: what's on screen. */
    public static String showing() { return line; }
    public static String card() { return cardLine; }

    private static float ease(float t) { t = Math.clamp(t, 0F, 1F); return t * t * (3 - 2 * t); }
    private static int argb(float alpha, int rgb) { return Math.round(Math.clamp(alpha, 0F, 1F) * 255) << 24 | rgb; }

    private static void draw(GuiGraphicsExtractor g, DeltaTracker delta) {
        var font = Minecraft.getInstance().font;
        long now = Util.getMillis();
        if (cardLine != null) {
            long age = now - cardStart;
            if (age > CARD_IN + CARD_HOLD + CARD_OUT) cardLine = null;
            else drawCard(g, font, age);
        }
        if (line != null) {
            long age = now - lineStart;
            if (age > lineLength) line = null;
            else drawLine(g, font, age);
        }
    }

    private static void drawLine(GuiGraphicsExtractor g, Font font, long age) {
        float alpha = Math.min(ease(age / 180F), 1 - ease((age - (lineLength - 500)) / 500F));
        if (alpha <= .02F) return;
        int w = g.guiWidth(), h = g.guiHeight(), cx = w / 2;
        String who = rank.isEmpty() || speaker.equals(rank) ? speaker : speaker + "  ·  " + rank;
        int width = Math.min(w - 40, Math.max(font.width(who), font.width(line)) + 24);
        int top = h - 92, bottom = top + 30;
        g.fill(cx - width / 2, top, cx + width / 2, bottom, argb(alpha * .62F, SLATE));
        g.fill(cx - width / 2, top, cx + width / 2, top + 1, argb(alpha * .9F, PLATE));
        g.centeredText(font, who, cx, top + 4, argb(alpha, GLINT));
        g.centeredText(font, "“" + line + "”", cx, top + 17, argb(alpha, SILVER));
    }

    private static void drawCard(GuiGraphicsExtractor g, Font font, long age) {
        float in = ease(age / (float) CARD_IN), out = 1 - ease((age - CARD_IN - CARD_HOLD) / (float) CARD_OUT);
        float alpha = Math.min(in, out);
        if (alpha <= .02F) return;
        int w = g.guiWidth(), h = g.guiHeight(), cx = w / 2;
        String title = "THE PLATA CLAN";
        float scale = Math.clamp(w * .6F / Math.max(1, spacedWidth(font, title)), 1.5F, 3.5F);
        int titleH = Math.round(font.lineHeight * scale);
        int top = Math.round(h * .2F) + Math.round((1 - in) * 6);
        int bandTop = top - 8, bandBottom = top + 32 + titleH + 30;
        int mid = (bandTop + bandBottom) / 2, shade = Math.round(alpha * 0x80);
        g.fillGradient(0, bandTop, w, mid, 0, shade << 24);
        g.fillGradient(0, mid, w, bandBottom, shade << 24, 0);
        g.blit(RenderPipelines.GUI_TEXTURED, MARK, cx - 16, top, 0, 0, 32, 32, 32, 32, argb(alpha, 0xFFFFFF));
        int y = top + 36;
        g.pose().pushMatrix();
        g.pose().translate(cx, y);
        g.pose().scale(scale, scale);
        spaced(g, font, title, 0, 0, argb(alpha, SILVER));
        g.pose().popMatrix();
        y += titleH + 6;
        int half = Math.round(Math.min(w * .4F, spacedWidth(font, title) * scale / 2 + 10) * ease((age - 200) / 900F));
        if (half > 4) {
            g.fill(cx - half, y, cx - 4, y + 1, argb(alpha * .8F, PLATE));
            g.fill(cx + 4, y, cx + half, y + 1, argb(alpha * .8F, PLATE));
            g.fill(cx - 1, y - 1, cx + 2, y + 2, argb(alpha, GLINT));
        }
        y += 8;
        if (cardLine != null && !cardLine.isEmpty()) g.centeredText(font, cardLine, cx, y, argb(alpha, PLATE));
    }

    private static int spacedWidth(Font font, String text) {
        int width = 0;
        for (int i = 0; i < text.length(); i++) width += font.width(String.valueOf(text.charAt(i))) + (i + 1 < text.length() ? 2 : 0);
        return width;
    }

    /** Capitals with extra letter spacing, centred on {@code cx}. */
    private static void spaced(GuiGraphicsExtractor g, Font font, String text, int cx, int y, int color) {
        int x = cx - spacedWidth(font, text) / 2;
        for (int i = 0; i < text.length(); i++) {
            String c = String.valueOf(text.charAt(i));
            g.text(font, c, x, y, color, true);
            x += font.width(c) + 2;
        }
    }
}

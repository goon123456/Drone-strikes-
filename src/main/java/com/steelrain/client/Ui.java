package com.steelrain.client;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractButton;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;

/**
 * Shared instruments for Steel Rain's command screens and in-world optics. Charcoal glass, silver labels,
 * green readiness, cyan sensor information and amber/red warnings. Panels occupy the edges of the view;
 * aiming and camera imagery stay visible in the centre.
 */
public final class Ui {
    // palette
    public static final int BG = 0xFA050A0B, PANEL = 0xFF0B1213, PANEL_2 = 0xFF121E1B, PANEL_3 = 0xFF1B3027,
            EDGE = 0xFF30423D, EDGE_LIGHT = 0xFF587466, OLIVE = 0xFF1B3027, OLIVE_LIGHT = 0xFF587466,
            AMBER = 0xFFF5C451, AMBER_DARK = 0xFF8A6418, GREEN = 0xFF8DF29A, RED = 0xFFFF5F52, CYAN = 0xFF6FE0F0,
            TEXT = 0xFFDBE5DF, DIM = 0xFF8BABA0, FAINT = 0xFF526B60, BLACK = 0xFF000000;
    /** Backing keeps readouts legible against snow, sky and dark terrain. */
    public static final int HUD_BG = 0xDA050B0D, HUD_EDGE = 0xB0587466;

    /** A raised panel: dark fill, thin edge, a faint highlight along the top. */
    public static void panel(GuiGraphics g, int x, int y, int w, int h) {
        g.fill(x, y, x + w, y + h, EDGE);
        g.fillGradient(x + 1, y + 1, x + w - 1, y + h - 1, 0xFF14201C, PANEL);
        g.fill(x + 2, y + 1, x + w - 2, y + 2, EDGE_LIGHT);
    }

    /** A sunken well (map frames, input boxes, gauges). */
    public static void well(GuiGraphics g, int x, int y, int w, int h) {
        g.fill(x - 1, y - 1, x + w + 1, y + h + 1, 0xFF0A0E10);
        g.fill(x, y, x + w, y + h, 0xFF0F1417);
    }

    /** A compact container header. Its height stays twelve pixels to preserve slot layouts. */
    public static void header(GuiGraphics g, Font font, int x, int y, int w, Component title, Component right) {
        g.fillGradient(x, y, x + w, y + 12, 0xFF1A2C23, PANEL);
        g.fill(x, y, x + 2, y + 10, GREEN);
        g.fill(x, y + 11, x + w, y + 12, EDGE);
        String tag = right == null ? "" : font.plainSubstrByWidth(right.getString(), Math.max(0, w / 3));
        String name = font.plainSubstrByWidth(upper(title).getString(), Math.max(0, w - 12 - (tag.isEmpty() ? 0 : font.width(tag) + 8)));
        g.drawString(font, name, x + 6, y + 2, TEXT, false);
        if (!tag.isEmpty()) g.drawString(font, tag, x + w - 4 - font.width(tag), y + 2, GREEN, false);
    }

    /** HUD card with a fifteen-pixel title band. Body content starts at y + 20. */
    public static void instrument(GuiGraphics g, Font font, int x, int y, int w, int h, String title, String right) {
        if (w < 12 || h < 16) return;
        g.fill(x, y, x + w, y + h, HUD_BG);
        g.fillGradient(x + 1, y + 1, x + w - 1, y + 15, 0xEB1A2B24, 0xD00B1411);
        g.fill(x + 1, y + 15, x + w - 1, y + 16, EDGE);
        brackets(g, x, y, w, h, 5, HUD_EDGE);
        g.fill(x + 5, y + 5, x + 7, y + 10, GREEN);
        String tag = font.plainSubstrByWidth(right == null ? "" : right, Math.max(0, w / 3));
        int tagWidth = tag.isEmpty() ? 0 : font.width(tag) + 8;
        String name = font.plainSubstrByWidth(title.toUpperCase(java.util.Locale.ROOT), Math.max(0, w - 18 - tagWidth));
        g.drawString(font, name, x + 11, y + 4, TEXT, false);
        if (!tag.isEmpty()) g.drawString(font, tag, x + w - 7 - font.width(tag), y + 4, GREEN, false);
    }

    /** Bounded label/value pair; long translated names cannot overwrite neighbouring instruments. */
    public static void readout(GuiGraphics g, Font font, int x, int y, int w, String label, String value, int colour) {
        if (w <= 0) return;
        String v = font.plainSubstrByWidth(value, Math.max(0, w * 2 / 3));
        String l = font.plainSubstrByWidth(label.toUpperCase(java.util.Locale.ROOT), Math.max(0, w - font.width(v) - 8));
        g.drawString(font, l, x, y, DIM, false);
        g.drawString(font, v, x + w - font.width(v), y, colour, false);
    }

    /** Compact labels follow the actual binding and retain any configured modifier. */
    public static String keyName(net.minecraft.client.KeyMapping mapping) {
        String translated = mapping.getTranslatedKeyMessage().getString().toUpperCase(java.util.Locale.ROOT);
        if (mapping.getKeyModifier() == net.neoforged.neoforge.client.settings.KeyModifier.NONE
                && mapping.getKey().getType() == com.mojang.blaze3d.platform.InputConstants.Type.MOUSE) {
            return switch (mapping.getKey().getValue()) {
                case 0 -> "LMB"; case 1 -> "RMB"; case 2 -> "MMB"; default -> translated;
            };
        }
        return translated;
    }

    /** A real key binding displayed as a keycap and a short action. Returns the occupied width. */
    public static int keyHint(GuiGraphics g, Font font, int x, int y, String key, String action) {
        int available = Math.max(0, g.guiWidth() - x - 6);
        if (available < 12) return 0;
        String k = font.plainSubstrByWidth(key.toUpperCase(java.util.Locale.ROOT), Math.min(64, available - 6));
        int kw = Math.max(10, font.width(k) + 6);
        g.fill(x, y - 1, x + kw, y + 10, EDGE);
        g.fill(x + 1, y, x + kw - 1, y + 9, PANEL);
        g.drawString(font, k, x + 3, y + 1, GREEN, false);
        String a = font.plainSubstrByWidth(action, Math.max(0, available - kw - 5));
        g.drawString(font, a, x + kw + 5, y + 1, DIM, false);
        return kw + 5 + font.width(a);
    }

    public static Component upper(Component c) { return Component.literal(c.getString().toUpperCase(java.util.Locale.ROOT)); }

    /** Corner brackets round a box, as on a targeting display. */
    public static void brackets(GuiGraphics g, int x, int y, int w, int h, int len, int colour) {
        g.fill(x, y, x + len, y + 1, colour); g.fill(x, y, x + 1, y + len, colour);
        g.fill(x + w - len, y, x + w, y + 1, colour); g.fill(x + w - 1, y, x + w, y + len, colour);
        g.fill(x, y + h - 1, x + len, y + h, colour); g.fill(x, y + h - len, x + 1, y + h, colour);
        g.fill(x + w - len, y + h - 1, x + w, y + h, colour); g.fill(x + w - 1, y + h - len, x + w, y + h, colour);
    }

    /** A segmented gauge filled to a fraction, colour by level unless one is given. */
    public static void gauge(GuiGraphics g, int x, int y, int w, int h, double frac, int colour, int segments) {
        if (w <= 0 || h <= 0) return;
        segments = Mth.clamp(segments, 1, w);
        frac = Mth.clamp(frac, 0, 1);
        g.fill(x, y, x + w, y + h, 0xFF0B0F11);
        int filled = (int) Math.round(frac * segments);
        double seg = (double) w / segments;
        for (int i = 0; i < segments; i++) {
            int sx = x + (int) Math.round(i * seg), ex = x + (int) Math.round((i + 1) * seg) - 1;
            g.fill(sx, y, Math.max(sx + 1, ex), y + h, i < filled ? colour : 0xFF253036);
        }
    }

    /** The colour a level should be shown in: green when healthy, amber when getting low, red when nearly gone. */
    public static int level(double frac) { return frac > 0.5 ? GREEN : frac > 0.2 ? AMBER : RED; }

    /** A small filled tag with text, for statuses (READY, BUSY 12s, NO SIGNAL). */
    public static int chip(GuiGraphics g, Font font, int x, int y, Component text, int colour) {
        int w = font.width(text) + 6;
        g.fill(x, y, x + w, y + 10, (colour & 0x00FFFFFF) | 0x40000000);
        g.fill(x, y, x + 1, y + 10, colour);
        g.drawString(font, text, x + 4, y + 1, colour, false);
        return w;
    }

    /** "LABEL   value" on one line, label dim and value bright. */
    public static void field(GuiGraphics g, Font font, int x, int y, int w, Component label, Component value, int colour) {
        readout(g, font, x, y, w, label.getString(), value.getString(), colour);
    }

    /** Draws a 7x7 icon from a row pattern ('#' set). */
    public static void icon(GuiGraphics g, int x, int y, String[] rows, int colour) {
        for (int r = 0; r < rows.length; r++) {
            String row = rows[r];
            for (int c = 0; c < row.length(); c++) if (row.charAt(c) == '#') g.fill(x + c, y + r, x + c + 1, y + r + 1, colour);
        }
    }

    public static final String[] ICON_ROCKET = {"   #   ", "  ###  ", "  ###  ", "  ###  ", "  ###  ", " ##### ", " #   # "};
    public static final String[] ICON_SHELL = {"   #   ", "  ###  ", "  ###  ", "  ###  ", "  ###  ", "  ###  ", "  ###  "};
    public static final String[] ICON_MISSILE = {"   #   ", "  ###  ", "  ###  ", " ##### ", "  ###  ", "  ###  ", " ## ## "};
    public static final String[] ICON_PLANE = {"   #   ", "   #   ", "#######", " ##### ", "   #   ", "  ###  ", "       "};
    public static final String[] ICON_DRONE = {"##   ##", " #   # ", "  ###  ", "  ###  ", "  ###  ", " #   # ", "##   ##"};
    public static final String[] ICON_SIGNAL = {"      #", "      #", "    # #", "    # #", "  # # #", "  # # #", "# # # #"};
    public static final String[] ICON_FUEL = {" ##### ", " #   ##", " #   # ", " ##### ", " ##### ", " ##### ", " ##### "};
    public static final String[] ICON_BOLT = {"   ### ", "  ###  ", " ###   ", "#######", "   ### ", "  ###  ", " ##    "};
    public static final String[] ICON_HEART = {" ## ## ", "#######", "#######", "#######", " ##### ", "  ###  ", "   #   "};
    public static final String[] ICON_WARN = {"   #   ", "  ###  ", "  # #  ", " ## ## ", " ##### ", "### ###", "#######"};

    /** A plain HUD box over the world: translucent fill, thin light edge, corner ticks. */
    public static void hudBox(GuiGraphics g, int x, int y, int w, int h) {
        g.fill(x, y, x + w, y + h, HUD_BG);
        g.fill(x, y, x + w, y + 1, 0x40A8BCC8);
        brackets(g, x, y, w, h, 4, HUD_EDGE);
    }

    /** A compass tape centred on a bearing, as on a gun sight: ticks every 5 degrees, labels every 30. */
    public static void compass(GuiGraphics g, Font font, int cx, int y, int w, double bearing, int colour) {
        hudBox(g, cx - w / 2, y, w, 22);
        double perDeg = 2.0;
        g.enableScissor(cx - w / 2 + 1, y + 1, cx + w / 2 - 1, y + 21);
        int first = (int) Math.floor((bearing - w / 2.0 / perDeg) / 5) * 5;
        for (int d = first; d <= bearing + w / 2.0 / perDeg; d += 5) {
            int x = cx + (int) Math.round((d - bearing) * perDeg);
            int norm = Math.floorMod(d, 360);
            boolean major = norm % 30 == 0;
            g.fill(x, y + 13, x + 1, y + (major ? 21 : 18), (colour & 0x00FFFFFF) | (major ? 0xFF000000 : 0x90000000));
            if (major) {
                String label = switch (norm) { case 0 -> "N"; case 90 -> "E"; case 180 -> "S"; case 270 -> "W"; default -> Integer.toString(norm / 10); };
                g.drawString(font, label, x - font.width(label) / 2 + 1, y + 3, norm % 90 == 0 ? AMBER : colour, false);
            }
        }
        g.disableScissor();
        g.fill(cx, y - 3, cx + 1, y + 22, AMBER);
        g.fill(cx - 2, y - 3, cx + 3, y - 2, AMBER);
    }

    /** A vertical scale for elevation with the current value marked, from {@code min} to {@code max} degrees. */
    public static void elevationScale(GuiGraphics g, Font font, int x, int y, int h, double min, double max, double value, double want, int colour) {
        hudBox(g, x, y, 26, h);
        for (int d = (int) Math.ceil(min / 5) * 5; d <= max; d += 5) {
            int py = y + h - 4 - (int) Math.round((d - min) / (max - min) * (h - 8));
            boolean major = d % 15 == 0;
            g.fill(x + 2, py, x + (major ? 9 : 6), py + 1, (colour & 0x00FFFFFF) | (major ? 0xFF000000 : 0x90000000));
            if (major) g.drawString(font, Integer.toString(d), x + 11, py - 4, colour, false);
        }
        int vy = y + h - 4 - (int) Math.round((Mth.clamp(value, min, max) - min) / (max - min) * (h - 8));
        g.fill(x - 3, vy - 1, x + 3, vy + 2, AMBER);
        if (!Double.isNaN(want)) {
            int wy = y + h - 4 - (int) Math.round((Mth.clamp(want, min, max) - min) / (max - min) * (h - 8));
            g.fill(x - 3, wy, x + 1, wy + 1, GREEN);
        }
    }

    /** Label, segmented gauge and value on one row, for HUD panels. */
    public static void meter(GuiGraphics g, Font font, int x, int y, int w, String[] icon, double frac, String value, int colour) {
        icon(g, x, y, icon, colour);
        gauge(g, x + 10, y + 2, w - 14 - font.width(value) - 4, 4, frac, colour, 12);
        g.drawString(font, value, x + w - font.width(value), y, TEXT, false);
    }

    // ------------------------------------------------------------------ bare HUD text

    /** Off-white for HUD text over the world, and a dimmer grey for secondary details. */
    public static final int HUD_TEXT = 0xFFF0F0EA, HUD_DIM = 0xFFB4B8B4;

    /** A colour with its alpha scaled by {@code alpha} (0..1). */
    public static int fade(int colour, float alpha) {
        int a = (int) (((colour >>> 24) & 0xFF) * Mth.clamp(alpha, 0, 1));
        return (a << 24) | (colour & 0xFFFFFF);
    }

    /**
     * Bare text with a soft shadow, as over the world in a modern shooter: no box behind it. Nothing is drawn when it
     * is nearly transparent, because the font draws very low alphas as fully opaque.
     */
    public static void text(GuiGraphics g, Font font, String s, int x, int y, int colour, float alpha) {
        if (alpha < 0.04f || s.isEmpty()) return;
        g.drawString(font, s, x, y, fade(colour, alpha), true);
    }

    /** {@link #text} lined up on its right edge. */
    public static void textRight(GuiGraphics g, Font font, String s, int right, int y, int colour, float alpha) {
        text(g, font, s, right - font.width(s), y, colour, alpha);
    }

    /** {@link #text} centred on {@code cx}. */
    public static void textCentre(GuiGraphics g, Font font, String s, int cx, int y, int colour, float alpha) {
        text(g, font, s, cx - font.width(s) / 2, y, colour, alpha);
    }

    /** Larger text (1.5x) lined up on its right edge, for the one number that matters most (speed, rounds). */
    public static void bigRight(GuiGraphics g, Font font, String s, int right, int y, int colour, float alpha) {
        if (alpha < 0.04f) return;
        g.pose().pushPose();
        g.pose().translate(right - font.width(s) * 1.5f, y, 0);
        g.pose().scale(1.5f, 1.5f, 1);
        g.drawString(font, s, 0, 0, fade(colour, alpha), true);
        g.pose().popPose();
    }

    /**
     * Shows a group of readouts fully for a few seconds after anything in it changes, then fades them away, so the
     * screen is clear while nothing is happening. Pass the values that matter as the state each frame.
     */
    public static final class Fader {
        private static final long HOLD_MS = 3500, FADE_MS = 900;
        private Object last = new Object();
        private long at;

        public float update(Object state) {
            if (!java.util.Objects.equals(state, last)) {
                last = state;
                at = net.minecraft.Util.getMillis();
            }
            long t = net.minecraft.Util.getMillis() - at;
            return t < HOLD_MS ? 1 : Mth.clamp(1 - (t - HOLD_MS) / (float) FADE_MS, 0, 1);
        }

        /** Show everything again now, as on getting in. */
        public void wake() { last = new Object(); }
    }

    /**
     * A bare compass tape centred on a bearing: ticks every 5 degrees, numbers every 30, fading out towards its
     * ends, with a small caret and the exact bearing below the centre.
     */
    public static void compassBare(GuiGraphics g, Font font, int cx, int y, int w, double bearing, float alpha) {
        if (alpha < 0.04f) return;
        double perDeg = 2.0;
        int first = (int) Math.floor((bearing - w / 2.0 / perDeg) / 5) * 5;
        for (int d = first; d <= bearing + w / 2.0 / perDeg; d += 5) {
            double off = (d - bearing) * perDeg;
            if (Math.abs(off) > w / 2.0) continue;
            int x = cx + (int) Math.round(off);
            float edge = alpha * (float) Mth.clamp((1 - Math.abs(off) / (w / 2.0)) * 2.5, 0, 1);
            int norm = Math.floorMod(d, 360);
            boolean major = norm % 30 == 0;
            g.fill(x, y + 10, x + 1, y + (major ? 15 : 13), fade(major ? HUD_TEXT : 0xB0F0F0EA, edge));
            if (major) {
                String label = switch (norm) { case 0 -> "N"; case 90 -> "E"; case 180 -> "S"; case 270 -> "W"; default -> Integer.toString(norm / 10); };
                text(g, font, label, x - font.width(label) / 2 + 1, y, norm % 90 == 0 ? AMBER : HUD_TEXT, edge);
            }
        }
        g.fill(cx, y + 16, cx + 1, y + 18, fade(AMBER, alpha));
        textCentre(g, font, String.format("%05.1f", bearing), cx + 1, y + 20, AMBER, alpha);
    }

    /** Fraction 0..1 of a slow pulse, for blinking warnings. */
    public static boolean blink() { return (System.currentTimeMillis() / 300) % 2 == 0; }

    /** Flat tablet button: dark with an edge, amber edge on hover, or filled amber for the main action. */
    public static class TabletButton extends AbstractButton {
        private final Runnable press;
        private final boolean primary;

        public TabletButton(int x, int y, int w, int h, Component text, boolean primary, Runnable press) {
            super(x, y, w, h, text);
            this.press = press;
            this.primary = primary;
        }

        @Override public void onPress() { press.run(); }

        @Override
        protected void renderWidget(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
            Font font = Minecraft.getInstance().font;
            int x = getX(), y = getY(), w = width, h = height;
            boolean hot = active && isHoveredOrFocused();
            int edge = !active ? EDGE : hot || primary ? GREEN : EDGE_LIGHT;
            g.fill(x, y, x + w, y + h, edge);
            g.fillGradient(x + 1, y + 1, x + w - 1, y + h - 1,
                    !active ? PANEL : hot || primary ? PANEL_3 : PANEL_2, PANEL);
            if (active && (hot || primary)) g.fill(x + 2, y + h - 2, x + w - 2, y + h - 1, GREEN);
            String text = font.plainSubstrByWidth(upper(getMessage()).getString(), Math.max(0, w - 8));
            g.drawString(font, text, x + (w - font.width(text)) / 2, y + (h - 8) / 2,
                    !active ? FAINT : primary || hot ? GREEN : TEXT, false);
        }

        @Override
        protected void updateWidgetNarration(NarrationElementOutput out) { defaultButtonNarrationText(out); }
    }

    private Ui() {}
}

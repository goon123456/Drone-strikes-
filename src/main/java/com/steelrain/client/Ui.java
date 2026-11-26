package com.steelrain.client;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractButton;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;

/**
 * Steel Rain's look: a rugged military tablet. Dark slate panels with thin edges, olive header bars, amber for
 * things to act on, green for ready, red for trouble, cyan for aircraft and recon. Everything is drawn from plain
 * rectangles, so no textures are needed and it stays sharp at any GUI scale.
 */
public final class Ui {
    // palette
    public static final int BG = 0xF0121719, PANEL = 0xFF182025, PANEL_2 = 0xFF1F292F, PANEL_3 = 0xFF283540,
            EDGE = 0xFF3A4A54, EDGE_LIGHT = 0xFF56697A, OLIVE = 0xFF59663A, OLIVE_LIGHT = 0xFF7C8B4E,
            AMBER = 0xFFF2B33D, AMBER_DARK = 0xFF8A6418, GREEN = 0xFF7CD67C, RED = 0xFFE5534B, CYAN = 0xFF4FC3F7,
            TEXT = 0xFFE6EDF0, DIM = 0xFF8A9AA3, FAINT = 0xFF56656E, BLACK = 0xFF000000;
    /** Translucent backing for things drawn over the world. */
    public static final int HUD_BG = 0x9A0E1316, HUD_EDGE = 0x80A8BCC8;

    /** A raised panel: dark fill, thin edge, a faint highlight along the top. */
    public static void panel(GuiGraphics g, int x, int y, int w, int h) {
        g.fill(x, y, x + w, y + h, EDGE);
        g.fill(x + 1, y + 1, x + w - 1, y + h - 1, PANEL);
        g.fill(x + 1, y + 1, x + w - 1, y + 2, 0x18FFFFFF);
    }

    /** A sunken well (map frames, input boxes, gauges). */
    public static void well(GuiGraphics g, int x, int y, int w, int h) {
        g.fill(x - 1, y - 1, x + w + 1, y + h + 1, 0xFF0A0E10);
        g.fill(x, y, x + w, y + h, 0xFF0F1417);
    }

    /** A section header: olive bar with a bright tab at the left, title in capitals, optional text on the right. */
    public static void header(GuiGraphics g, Font font, int x, int y, int w, Component title, Component right) {
        g.fill(x, y, x + w, y + 11, OLIVE);
        g.fill(x, y, x + 3, y + 11, AMBER);
        g.fill(x, y + 11, x + w, y + 12, 0xFF2E3520);
        g.drawString(font, upper(title), x + 6, y + 2, TEXT, false);
        if (right != null) g.drawString(font, right, x + w - 4 - font.width(right), y + 2, 0xFFDCE3C8, false);
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
        g.drawString(font, upper(label), x, y, DIM, false);
        g.drawString(font, value, x + w - font.width(value), y, colour, false);
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
            int fill = !active ? 0xFF1A2126 : primary ? (hot ? 0xFFFFC85A : AMBER) : hot ? PANEL_3 : PANEL_2;
            int edge = !active ? 0xFF2A353C : primary ? AMBER_DARK : hot ? AMBER : EDGE_LIGHT;
            g.fill(x, y, x + w, y + h, edge);
            g.fill(x + 1, y + 1, x + w - 1, y + h - 1, fill);
            if (primary && active) g.fill(x + 1, y + 1, x + w - 1, y + 2, 0x50FFFFFF);
            int colour = !active ? FAINT : primary ? 0xFF1A1205 : TEXT;
            Component text = upper(getMessage());
            g.drawString(font, text, x + (w - font.width(text)) / 2, y + (h - 8) / 2, colour, false);
        }

        @Override
        protected void updateWidgetNarration(NarrationElementOutput out) { defaultButtonNarrationText(out); }
    }

    private Ui() {}
}

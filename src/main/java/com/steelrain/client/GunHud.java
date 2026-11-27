package com.steelrain.client;

import com.steelrain.gun.Attachment;
import com.steelrain.gun.Caliber;
import com.steelrain.gun.GunItem;
import com.steelrain.gun.GunType;
import com.steelrain.gun.MagazineItem;
import net.minecraft.Util;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.List;

/**
 * What the firer is shown: the weapon, selector and rounds at the bottom right with the magazines still in the
 * pack, the range the sights are set to, scatter marks in place of a crosshair when firing from the hip, a mark when
 * a round strikes home, and the picture through a magnifying scope with its reticle.
 */
public final class GunHud {
    private static final Ui.Fader ZERO_INFO = new Ui.Fader(), GUN_INFO = new Ui.Fader();
    private static long hitAt;
    private static boolean hitHead, hitKill;

    static void hit(boolean head, boolean kill) {
        hitAt = Util.getMillis(); hitHead = head; hitKill = kill;
    }

    static void render(GuiGraphics g, DeltaTracker delta) {
        Minecraft mc = Minecraft.getInstance();
        GunType type = GunClient.held();
        if (type == null || mc.player == null || mc.options.hideGui) return;
        ItemStack stack = mc.player.getMainHandItem();
        if (!(stack.getItem() instanceof GunItem)) return;
        Font font = mc.font;
        int w = g.guiWidth(), h = g.guiHeight();
        float a = GunClient.ads();
        Attachment optic = GunItem.part(stack, Attachment.Slot.OPTIC);

        if (GunClient.scoped() && optic != null) scope(g, font, w, h, stack, type, optic);
        else if (a < 0.5f && !GunClient.reloading() && GunClient.sprint < 0.5f && mc.options.getCameraType().isFirstPerson()) scatter(g, w, h, type, 1 - a * 2);

        // a round struck: a small cross, red for a kill
        long since = Util.getMillis() - hitAt;
        if (since < 260) {
            float f = 1 - since / 260f;
            int c = Ui.fade(hitKill ? Ui.RED : hitHead ? Ui.AMBER : 0xFFFFFFFF, f);
            int cx = w / 2, cy = h / 2;
            for (int i = 4; i < 9; i++) {
                g.fill(cx - i - 1, cy - i - 1, cx - i, cy - i, c); g.fill(cx + i, cy - i - 1, cx + i + 1, cy - i, c);
                g.fill(cx - i - 1, cy + i, cx - i, cy + i + 1, c); g.fill(cx + i, cy + i, cx + i + 1, cy + i + 1, c);
            }
        }

        // weapon panel, bottom right
        int mode = GunItem.mode(stack);
        int rounds = GunItem.rounds(stack);
        boolean chambered = GunItem.chambered(stack);
        int zero = GunItem.zeroRange(stack);
        List<Integer> spare = new ArrayList<>();
        int loose = 0;
        var inv = mc.player.getInventory();
        for (int i = 0; i < inv.getContainerSize(); i++) {
            ItemStack s = inv.getItem(i);
            if (s.getItem() instanceof MagazineItem m && m.gun == type) spare.add(MagazineItem.rounds(s));
            else if (s.is(com.steelrain.ModRegistry.AMMO.get(type.caliber).get())) loose += s.getCount();
        }
        spare.sort((x, y) -> y - x);
        float show = Math.max(GUN_INFO.update(List.of(type, mode, rounds, chambered, spare, loose, GunClient.reloading())), GunClient.triggerHeld ? 1 : 0.55f);
        int right = w - 8, y = h - 46;
        String count;
        if (type.magazineFed() && rounds < 0) count = "--";
        else if (type.action == GunType.Action.OPEN_BOLT) count = Integer.toString(rounds);
        else count = Math.max(0, rounds) + (chambered ? "+1" : "+0");
        int total = GunItem.loaded(stack);
        int colour = total == 0 ? Ui.RED : total <= type.capacity / 5 ? Ui.AMBER : Ui.HUD_TEXT;
        Ui.bigRight(g, font, count, right, y, colour, show);
        Ui.textRight(g, font, Component.translatable("item.steelrain." + type.id).getString().toUpperCase(java.util.Locale.ROOT), right, y - 11, Ui.HUD_DIM, show);
        String sel = Component.translatable("gun.steelrain.mode." + GunType.Mode.KEY[mode]).getString().toUpperCase(java.util.Locale.ROOT);
        Ui.textRight(g, font, sel + "  " + type.caliber.label, right, y + 15, mode == GunType.Mode.SAFE ? Ui.AMBER : Ui.HUD_DIM, show);
        // the magazines in the pack, fullest first, each a small bar showing how full it is
        int mx = right;
        if (type.magazineFed()) {
            for (int i = 0; i < Math.min(8, spare.size()); i++) {
                mx -= 6;
                int fill = Math.round(9f * spare.get(i) / type.capacity);
                g.fill(mx, y + 26, mx + 4, y + 36, Ui.fade(0x90000000, show));
                g.fill(mx + 1, y + 35 - fill, mx + 3, y + 35, Ui.fade(spare.get(i) * 4 < type.capacity ? Ui.AMBER : Ui.HUD_TEXT, show));
            }
            if (spare.size() > 8) Ui.textRight(g, font, "+" + (spare.size() - 8), mx - 3, y + 27, Ui.HUD_DIM, show);
            if (spare.isEmpty()) Ui.textRight(g, font, Component.translatable("gui.steelrain.gun.no_spare").getString(), right, y + 27, Ui.AMBER, show);
        } else {
            Ui.textRight(g, font, Component.translatable("gui.steelrain.gun.shells", loose).getString(), right, y + 27, loose == 0 ? Ui.AMBER : Ui.HUD_DIM, show);
        }

        // the sights' range setting: shown when it changes and while aiming through adjustable sights
        float zshow = ZERO_INFO.update(List.of(type, zero, optic == null ? -1 : optic.ordinal()));
        if (GunItem.zeros(stack).length > 1) {
            Ui.textRight(g, font, Component.translatable("gui.steelrain.gun.zero", zero).getString(), right, y - 22, Ui.HUD_TEXT, zshow);
        }
        if (GunClient.bipod > 0.5f) Ui.textRight(g, font, Component.translatable("gui.steelrain.gun.bipod").getString(), right, y - 33, Ui.GREEN, 1);

        // the breath being held, under the sight picture
        if (a > 0.8f && (GunClient.holding || GunClient.breath < 0.98f)) {
            int bw = 40, bx = w / 2 - bw / 2, by = h / 2 + (GunClient.scoped() ? (int) (h * 0.40f) : 34);
            g.fill(bx - 1, by - 1, bx + bw + 1, by + 3, 0x80000000);
            g.fill(bx, by, bx + Math.round(bw * GunClient.breath), by + 2, GunClient.breath < 0.25f ? Ui.RED : Ui.HUD_TEXT);
        }
    }

    /** Four marks that open out with how far the round may stray: wider on the move and in the air. */
    private static void scatter(GuiGraphics g, int w, int h, GunType type, float alpha) {
        Minecraft mc = Minecraft.getInstance();
        double sigma = Math.toRadians(type.pistol() ? 1.1 : 1.6) + Math.min(0.05, mc.player.getDeltaMovement().horizontalDistance() * 0.12);
        if (!mc.player.onGround()) sigma += Math.toRadians(2.5);
        double pxPerRad = (h / 2.0) / Math.tan(Math.toRadians(GunClient.worldFov / 2));
        int gap = (int) Math.round(Mth.clamp(sigma * 1.6 * pxPerRad + GunClient.jolt * 3, 4, 60));
        int cx = w / 2, cy = h / 2, c = Ui.fade(0xD0F0F0EA, alpha), len = 5;
        g.fill(cx - gap - len, cy, cx - gap, cy + 1, c); g.fill(cx + gap + 1, cy, cx + gap + len + 1, cy + 1, c);
        g.fill(cx, cy - gap - len, cx + 1, cy - gap, c); g.fill(cx, cy + gap + 1, cx + 1, cy + gap + len + 1, c);
        g.fill(cx, cy, cx + 1, cy + 1, c);
    }

    /** The view through a telescopic sight: everything outside the eyepiece is black, with the reticle over it. */
    private static void scope(GuiGraphics g, Font font, int w, int h, ItemStack stack, GunType type, Attachment optic) {
        int cx = w / 2, cy = h / 2;
        int r = (int) (h * 0.46f);
        // a breath of movement in the eye box, so the picture is not nailed to the screen
        int black = 0xFF000000;
        g.fill(0, 0, w, cy - r, black); g.fill(0, cy + r, w, h, black);
        for (int dy = -r; dy < r; dy++) {
            int half = (int) Math.sqrt((double) r * r - (double) dy * dy);
            g.fill(0, cy + dy, cx - half, cy + dy + 1, black);
            g.fill(cx + half, cy + dy, w, cy + dy + 1, black);
        }
        // soft dark ring just inside the edge
        for (int i = 0; i < 6; i++) ring(g, cx, cy, r - i, Ui.fade(black, 0.5f - i * 0.08f));
        double pxPerMil = (h / 2.0) / Math.tan(Math.toRadians(GunClient.worldFov / 2)) / 1000.0;
        Caliber cal = type.caliber;
        double zeroLift = cal.elevation(GunItem.zeroRange(stack), type.velocityFactor);
        if (optic == Attachment.PSO1) {
            int ink = 0xFF101010;
            // centre chevron, three more below it for longer ranges, and the 10-mil lead marks either side
            for (int k = 0; k < 4; k++) chevron(g, cx, cy + (int) Math.round(k * 4.2 * pxPerMil), k == 0 ? 3 : 2, ink);
            for (int i = 1; i <= 10; i++) {
                int x = (int) Math.round(i * pxPerMil);
                int tall = i % 5 == 0 ? 3 : 1;
                g.fill(cx + x, cy - tall, cx + x + 1, cy + 1, ink); g.fill(cx - x, cy - tall, cx - x + 1, cy + 1, ink);
            }
            g.fill(cx + (int) Math.round(11 * pxPerMil), cy, cx + r, cy + 1, ink); g.fill(cx - r, cy, cx - (int) Math.round(11 * pxPerMil), cy + 1, ink);
            // stadiametric rangefinder, lower left: stand a 1.7 m man between the base line and the curve
            int bx = cx - (int) Math.round(9 * pxPerMil), by = cy + (int) Math.round(5 * pxPerMil);
            g.fill(bx - 2, by, bx + (int) Math.round(7.5 * pxPerMil), by + 1, ink);
            int prevX = -1, prevY = -1;
            for (int range = 200; range <= 1000; range += 100) {
                int x = bx + (int) Math.round((range - 200) / 800.0 * 7 * pxPerMil);
                int top = by - (int) Math.round(1.7 / range * 1000 * pxPerMil);
                if (prevX >= 0) line(g, prevX, prevY, x, top, ink);
                prevX = x; prevY = top;
                if (range % 200 == 0) {
                    g.pose().pushPose();
                    g.pose().translate(x - 2, top - 6, 0);
                    g.pose().scale(0.5f, 0.5f, 1);
                    g.drawString(font, Integer.toString(range / 100), 0, 0, ink, false);
                    g.pose().popPose();
                }
            }
        } else {
            // 4x prism sight: a lit chevron with hold-over bars below it for each further hundred metres,
            // worked out for this gun's round
            int red = 0xFFE03A2A, ink = 0xFF101010;
            chevron(g, cx, cy, 4, red);
            for (int range = 200; range <= 600; range += 100) {
                double drop = cal.elevation(range, type.velocityFactor) - zeroLift;
                int y = cy + (int) Math.round(drop * 1000 * pxPerMil);
                int half = (int) Math.max(2, Math.round(0.25 / range * 1000 * pxPerMil));           // as wide as a man's shoulders
                g.fill(cx - half, y, cx + half + 1, y + 1, ink);
                if (range % 200 == 0) {
                    g.pose().pushPose();
                    g.pose().translate(cx + half + 3, y - 2, 0);
                    g.pose().scale(0.5f, 0.5f, 1);
                    g.drawString(font, Integer.toString(range / 100), 0, 0, ink, false);
                    g.pose().popPose();
                }
            }
            g.fill(cx, cy + (int) Math.round(9 * pxPerMil), cx + 1, cy + r, ink);
        }
    }

    private static void chevron(GuiGraphics g, int cx, int y, int size, int colour) {
        for (int i = 0; i <= size; i++) {
            g.fill(cx - i, y + i, cx - i + 1, y + i + 2, colour);
            g.fill(cx + i, y + i, cx + i + 1, y + i + 2, colour);
        }
    }

    private static void ring(GuiGraphics g, int cx, int cy, int r, int colour) {
        int px = r, py = 0;
        for (int deg = 2; deg <= 360; deg += 2) {
            int x = (int) Math.round(r * Math.cos(Math.toRadians(deg))), y = (int) Math.round(r * Math.sin(Math.toRadians(deg)));
            line(g, cx + px, cy + py, cx + x, cy + y, colour);
            px = x; py = y;
        }
    }

    private static void line(GuiGraphics g, int x0, int y0, int x1, int y1, int colour) {
        int n = Math.max(Math.abs(x1 - x0), Math.abs(y1 - y0));
        for (int i = 0; i <= n; i++) {
            int x = n == 0 ? x0 : x0 + (x1 - x0) * i / n, y = n == 0 ? y0 : y0 + (y1 - y0) * i / n;
            g.fill(x, y, x + 1, y + 1, colour);
        }
    }

    private GunHud() {}
}

package com.steelrain.client;

import com.steelrain.entity.vehicle.SrAirDefence;
import com.steelrain.entity.vehicle.SrGun;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;

import javax.annotation.Nullable;

/**
 * Gun sights, in the rugged-tablet style. Artillery: compass tape and elevation scale for where the barrel points,
 * the charge, shell and breech, a range table, and a marker for the barrel catching up with the eye. Air defence:
 * the same tape and scale, radar state, a box round the tracked target and a diamond on the lead, rounds and belts.
 */
public final class ArtilleryHud {
    static void render(GuiGraphics g, DeltaTracker delta) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.options.hideGui) return;
        float pt = delta.getGameTimeDeltaPartialTick(false);
        if (mc.player.getVehicle() instanceof SrGun sr && sr.isGunner(mc.player)) artillery(g, sr, mc);
        if (mc.player.getVehicle() instanceof SrAirDefence ad && ad.isGunner(mc.player)) airDefence(g, ad, mc, pt);
    }

    /** Screen position of a direction from the eye, or null when it is behind or far off to the side. */
    @Nullable
    private static int[] onScreen(GuiGraphics g, Minecraft mc, Vec3 dir) {
        var player = mc.player;
        double yaw = Math.toDegrees(Math.atan2(-dir.x, dir.z)), pitch = Math.toDegrees(Math.asin(Mth.clamp(dir.y, -1, 1)));
        double dyaw = Mth.wrapDegrees(yaw - player.getYRot()), dpitch = pitch + player.getXRot();
        if (Math.abs(dyaw) > 75 || Math.abs(dpitch) > 75) return null;
        double scale = g.guiHeight() / 2.0 / Math.tan(Math.toRadians(mc.options.fov().get() / 2.0));
        return new int[]{(int) (g.guiWidth() / 2.0 + Math.tan(Math.toRadians(dyaw)) * scale), (int) (g.guiHeight() / 2.0 - Math.tan(Math.toRadians(dpitch)) * scale)};
    }

    private static final int PANEL_W = 136;

    /** The compass tape fills the gap between the left panels and the elevation scale. */
    private static int compassWidth(int w) { return Mth.clamp(w - 2 * (PANEL_W + 14), 80, 220); }

    private static double bearingOf(Vec3 d) { return (Math.toDegrees(Math.atan2(d.x, -d.z)) + 360) % 360; }

    private static double elevationOf(Vec3 d) { return Math.toDegrees(Math.asin(Mth.clamp(d.y, -1, 1))); }

    /** A cross where the barrels point now: green once on the crosshair, amber while still moving. */
    private static void barrelMarker(GuiGraphics g, Minecraft mc, Vec3 dir) {
        int[] m = onScreen(g, mc, dir);
        if (m == null) return;
        boolean laid = Math.abs(m[0] - g.guiWidth() / 2) <= 1 && Math.abs(m[1] - g.guiHeight() / 2) <= 1;
        int c = laid ? Ui.GREEN : Ui.AMBER;
        g.fill(m[0] - 7, m[1], m[0] - 2, m[1] + 1, c); g.fill(m[0] + 3, m[1], m[0] + 8, m[1] + 1, c);
        g.fill(m[0], m[1] - 7, m[0] + 1, m[1] - 2, c); g.fill(m[0], m[1] + 3, m[0] + 1, m[1] + 8, c);
    }

    private static void keys(GuiGraphics g, Font font, Component text) {
        int w = font.width(text) + 10, x = (g.guiWidth() - w) / 2, y = g.guiHeight() - 62;
        Ui.hudBox(g, x, y, w, 13);
        g.drawString(font, text, x + 5, y + 3, Ui.DIM, false);
    }

    // ------------------------------------------------------------------ artillery

    private static void artillery(GuiGraphics g, SrGun gun, Minecraft mc) {
        Font font = mc.font;
        int w = g.guiWidth(), h = g.guiHeight();
        Vec3 barrel = gun.gunDirection();
        double bearing = bearingOf(barrel), elevation = elevationOf(barrel);
        Ui.compass(g, font, w / 2, 6, compassWidth(w), bearing, Ui.TEXT);
        String bt = String.format("%05.1f°", bearing);
        g.drawString(font, bt, w / 2 - font.width(bt) / 2, 31, Ui.AMBER, true);
        double want = Mth.clamp(-mc.player.getXRot(), gun.minElevation(), gun.maxElevation());
        Ui.elevationScale(g, font, w - 40, h / 2 - 60, 120, gun.minElevation(), gun.maxElevation(), elevation, want, Ui.TEXT);
        String et = String.format("%.1f°", elevation);
        g.drawString(font, et, w - 40 + 13 - font.width(et) / 2, h / 2 + 64, Ui.AMBER, true);

        // gun state, top left
        int x = 6, y = 6, pw = PANEL_W;
        Ui.hudBox(g, x, y, pw, 58);
        g.drawString(font, Ui.upper(Component.translatable("hud.steelrain.gun_title")), x + 5, y + 4, Ui.DIM, false);
        double[] charges = gun.charges();
        int cx = x + 5;
        for (int c = 0; c < charges.length; c++) cx += Ui.chip(g, font, cx, y + 15, Component.literal("C" + (c + 1)), c == gun.charge() ? Ui.AMBER : Ui.FAINT) + 3;
        g.drawString(font, "⇅", cx + 2, y + 16, Ui.FAINT, false);
        var shells = gun.shells();
        Component shell = shells.get(Math.min(gun.selectedShell(), shells.size() - 1)).getDescription();
        g.drawString(font, font.plainSubstrByWidth(shell.getString(), pw - 30), x + 5, y + 29, Ui.TEXT, false);
        g.drawString(font, "[B]", x + pw - 5 - font.width("[B]"), y + 29, Ui.FAINT, false);
        if (gun.loaded() >= 0) {
            Ui.chip(g, font, x + 5, y + 42, Component.translatable("hud.steelrain.breech_loaded"), Ui.GREEN);
        } else if (gun.loading() > 0) {
            double frac = 1 - (double) gun.loading() / Math.max(1, gun.reloadTotal());
            int cw = Ui.chip(g, font, x + 5, y + 42, Component.translatable("hud.steelrain.breech_loading"), Ui.AMBER);
            Ui.gauge(g, x + 9 + cw, y + 45, pw - 18 - cw, 4, frac, Ui.AMBER, 12);
        } else {
            Ui.chip(g, font, x + 5, y + 42, Component.translatable("hud.steelrain.breech_empty"), Ui.blink() ? Ui.RED : 0xFFB04040);
        }

        // range table, below
        int ty = y + 64, rows = Mth.clamp((h - 66 - ty - 24) / 9, 3, 12), step = gun.rangeStep();
        Ui.hudBox(g, x, ty, pw, 24 + rows * 9);
        g.drawString(font, Ui.upper(Component.translatable("hud.steelrain.range_table")), x + 5, ty + 4, Ui.DIM, false);
        g.drawString(font, "M", x + 5, ty + 14, Ui.FAINT, false);
        for (int c = 0; c < charges.length; c++) g.drawString(font, "C" + (c + 1), x + 36 + c * 32, ty + 14, c == gun.charge() ? Ui.AMBER : Ui.FAINT, false);
        for (int r = 0; r < rows; r++) {
            int range = step * (r + 1), ry = ty + 24 + r * 9;
            if (r % 2 == 0) g.fill(x + 2, ry - 1, x + pw - 2, ry + 8, 0x18FFFFFF);
            g.drawString(font, Integer.toString(range), x + 5, ry, Ui.DIM, false);
            for (int c = 0; c < charges.length; c++) {
                double e = SrGun.elevationFor(charges[c], range, 0, gun.minElevation(), gun.maxElevation());
                String s = Double.isNaN(e) ? "--" : String.format("%.1f", e);
                g.drawString(font, s, x + 36 + c * 32, ry, Double.isNaN(e) ? 0xFF3C4850 : c == gun.charge() ? Ui.TEXT : Ui.DIM, false);
            }
        }
        barrelMarker(g, mc, barrel);
        keys(g, font, Component.translatable("hud.steelrain.gun_keys"));
    }

    // ------------------------------------------------------------------ air defence

    private static void airDefence(GuiGraphics g, SrAirDefence ad, Minecraft mc, float pt) {
        Font font = mc.font;
        int w = g.guiWidth(), h = g.guiHeight();
        Vec3 guns = ad.gunDirection();
        Ui.compass(g, font, w / 2, 6, compassWidth(w), bearingOf(guns), Ui.TEXT);
        Ui.elevationScale(g, font, w - 40, h / 2 - 60, 120, ad.minPitch(), ad.maxPitch(), elevationOf(guns),
                Mth.clamp(-mc.player.getXRot(), ad.minPitch(), ad.maxPitch()), Ui.TEXT);
        var target = ad.target();

        int x = 6, y = 6, pw = PANEL_W;
        int ph = 40 + (ad.hasGuns() ? 22 : 0) + (ad.missileRange() > 0 ? 12 : 0);
        Ui.hudBox(g, x, y, pw, ph);
        // radar line
        Component radarState = !ad.hasRadar() ? Component.translatable("hud.steelrain.radar_none")
                : ad.locked() ? Component.translatable("hud.steelrain.radar_lock_short")
                : target != null ? Component.translatable("hud.steelrain.radar_track_short")
                : Component.translatable("hud.steelrain.radar_search_short");
        int rc = !ad.hasRadar() ? Ui.FAINT : ad.locked() ? Ui.RED : target != null ? Ui.AMBER : Ui.GREEN;
        Ui.icon(g, x + 5, y + 5, Ui.ICON_SIGNAL, rc);
        Ui.chip(g, font, x + 16, y + 4, radarState, rc);
        if (target != null && ad.hasRadar()) {
            String rng = (int) target.distanceTo(ad) + " m";
            g.drawString(font, rng, x + pw - 5 - font.width(rng), y + 5, Ui.TEXT, false);
            g.drawString(font, font.plainSubstrByWidth(target.getName().getString(), pw - 10), x + 5, y + 17, Ui.TEXT, false);
        } else {
            g.drawString(font, Component.translatable("hud.steelrain.no_contact"), x + 5, y + 17, Ui.FAINT, false);
        }
        int ry = y + 29;
        if (ad.hasGuns()) {
            Ui.chip(g, font, x + 5, ry, Component.literal(ad.beltAp() ? "AP" : "HE"), ad.beltAp() ? Ui.CYAN : Ui.AMBER);
            double frac = (double) ad.rounds() / Math.max(1, ad.beltSize());
            Ui.gauge(g, x + 26, ry + 3, pw - 66, 4, frac, ad.rounds() > 0 ? Ui.level(frac) : Ui.RED, 10);
            String rounds = Integer.toString(ad.rounds());
            g.drawString(font, rounds, x + pw - 5 - font.width(rounds), ry + 1, ad.rounds() > 0 ? Ui.TEXT : Ui.RED, false);
            g.drawString(font, Component.translatable("hud.steelrain.belts", ad.stock(0), ad.stock(1)), x + 5, ry + 12, Ui.DIM, false);
            ry += 22;
        }
        if (ad.missileRange() > 0) {
            Ui.icon(g, x + 5, ry + 1, Ui.ICON_MISSILE, Ui.CYAN);
            g.drawString(font, Component.translatable("hud.steelrain.missiles_short", ad.stock(2)), x + 16, ry + 1, ad.stock(2) > 0 ? Ui.TEXT : Ui.RED, false);
            ry += 12;
        }
        Ui.chip(g, font, x + 5, ry, Component.translatable(ad.auto() ? "hud.steelrain.auto_chip_on" : "hud.steelrain.auto_chip_off"), ad.auto() ? Ui.GREEN : Ui.FAINT);

        barrelMarker(g, mc, guns);
        if (target != null && ad.hasRadar()) {
            Vec3 eye = mc.player.getEyePosition(pt);
            Vec3 tp = new Vec3(Mth.lerp(pt, target.xo, target.getX()), Mth.lerp(pt, target.yo, target.getY()) + target.getBbHeight() / 2,
                    Mth.lerp(pt, target.zo, target.getZ()));
            int[] box = onScreen(g, mc, tp.subtract(eye).normalize());
            int c = ad.locked() ? Ui.RED : Ui.AMBER;
            if (box != null) {
                Ui.brackets(g, box[0] - 10, box[1] - 10, 21, 21, 5, c);
                if (ad.locked() && Ui.blink()) Ui.brackets(g, box[0] - 13, box[1] - 13, 27, 27, 3, c);
            }
            if (ad.hasGuns()) {
                int[] d = onScreen(g, mc, ad.leadOn(target, true));
                if (d != null) {
                    for (int i = 0; i <= 5; i++) {
                        g.fill(d[0] - 5 + i, d[1] - i, d[0] - 4 + i, d[1] - i + 1, Ui.GREEN); g.fill(d[0] + 5 - i, d[1] - i, d[0] + 6 - i, d[1] - i + 1, Ui.GREEN);
                        g.fill(d[0] - 5 + i, d[1] + i, d[0] - 4 + i, d[1] + i + 1, Ui.GREEN); g.fill(d[0] + 5 - i, d[1] + i, d[0] + 6 - i, d[1] + i + 1, Ui.GREEN);
                    }
                    if (box != null) {   // a faint line from the target to where to aim
                        int steps = Math.max(Math.abs(d[0] - box[0]), Math.abs(d[1] - box[1]));
                        for (int i = 0; i < steps; i += 3) {
                            int lx = box[0] + (d[0] - box[0]) * i / Math.max(1, steps), ly = box[1] + (d[1] - box[1]) * i / Math.max(1, steps);
                            g.fill(lx, ly, lx + 1, ly + 1, 0x807CD67C);
                        }
                    }
                }
            }
        }
        keys(g, font, Component.translatable(ad.hasRadar() ? "hud.steelrain.air_keys_radar" : "hud.steelrain.air_keys"));
    }

    private ArtilleryHud() {}
}

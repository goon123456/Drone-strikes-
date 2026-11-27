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
import java.util.Locale;

/** Instrument-style gun sights, with persistent ammunition state and a clear centre for aiming. */
public final class ArtilleryHud {
    static void render(GuiGraphics g, DeltaTracker delta) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.options.hideGui || mc.screen != null) return;
        float pt = delta.getGameTimeDeltaPartialTick(false);
        if (mc.player.getVehicle() instanceof SrGun sr && sr.isGunner(mc.player)) artillery(g, sr, mc);
        if (mc.player.getVehicle() instanceof SrAirDefence ad && ad.isGunner(mc.player)) airDefence(g, ad, mc, pt);
    }

    /** Screen position of a direction from the eye, or null when it falls outside the viewport. */
    @Nullable
    private static int[] onScreen(GuiGraphics g, Minecraft mc, Vec3 dir) {
        var player = mc.player;
        double yaw = Math.toDegrees(Math.atan2(-dir.x, dir.z)), pitch = Math.toDegrees(Math.asin(Mth.clamp(dir.y, -1, 1)));
        double dyaw = Mth.wrapDegrees(yaw - player.getYRot()), dpitch = pitch + player.getXRot();
        if (Math.abs(dyaw) > 75 || Math.abs(dpitch) > 75) return null;
        double scale = g.guiHeight() / 2.0 / Math.tan(Math.toRadians(mc.options.fov().get() / 2.0));
        int x = (int) (g.guiWidth() / 2.0 + Math.tan(Math.toRadians(dyaw)) * scale);
        int y = (int) (g.guiHeight() / 2.0 - Math.tan(Math.toRadians(dpitch)) * scale);
        return x < 0 || y < 0 || x >= g.guiWidth() || y >= g.guiHeight() ? null : new int[]{x, y};
    }

    /** Range table shown, toggled with its configured key. */
    static boolean table;

    private static double bearingOf(Vec3 d) { return (Math.toDegrees(Math.atan2(d.x, -d.z)) + 360) % 360; }
    private static double elevationOf(Vec3 d) { return Math.toDegrees(Math.asin(Mth.clamp(d.y, -1, 1))); }
    private static String key(net.minecraft.client.KeyMapping mapping) { return Ui.keyName(mapping); }

    /** Side cards leave a corridor around the reticle, including at the smallest normal GUI size. */
    private static int cardWidth(int w) { return Mth.clamp((w - 80) / 2, 108, 188); }

    private static void sightHeader(GuiGraphics g, Font font, int w, String name, String role, double bearing) {
        Ui.instrument(g, font, 8, 8, w - 16, 55, name, role);
        Ui.compassBare(g, font, w / 2, 28, Math.min(w - 48, 240), bearing, 1);
    }

    /** A cross where the barrels point now: green once on the crosshair, amber while still moving. */
    private static void barrelMarker(GuiGraphics g, Minecraft mc, Vec3 dir) {
        int[] m = onScreen(g, mc, dir);
        if (m == null) return;
        boolean laid = Math.abs(m[0] - g.guiWidth() / 2) <= 1 && Math.abs(m[1] - g.guiHeight() / 2) <= 1;
        int c = Ui.fade(laid ? Ui.GREEN : Ui.AMBER, 0.85f);
        g.fill(m[0] - 7, m[1], m[0] - 2, m[1] + 1, c); g.fill(m[0] + 3, m[1], m[0] + 8, m[1] + 1, c);
        g.fill(m[0], m[1] - 7, m[0] + 1, m[1] - 2, c); g.fill(m[0], m[1] + 3, m[0] + 1, m[1] + 8, c);
    }

    // ------------------------------------------------------------------ artillery

    private static void artillery(GuiGraphics g, SrGun gun, Minecraft mc) {
        Font font = mc.font;
        int w = g.guiWidth(), h = g.guiHeight(), cw = cardWidth(w), bottom = h - 52, y = bottom - 90;
        Vec3 barrel = gun.gunDirection();
        double bearing = bearingOf(barrel), elevation = elevationOf(barrel);
        sightHeader(g, font, w, Ui.upper(gun.getName()).getString(), "ARTILLERY", bearing);

        double[] charges = gun.charges();
        int charge = Mth.clamp(gun.charge(), 0, charges.length - 1);
        double v = charges[charge];
        double range = elevation > 0 ? v * v * Math.sin(Math.toRadians(2 * elevation)) / com.steelrain.entity.MissileEntity.GRAVITY : 0;
        if (table) {
            rangeTable(g, font, gun, charges, charge, cw, bottom);
        } else {
            Ui.instrument(g, font, 8, y, cw, 90, "FIRE SOLUTION", "");
            Ui.readout(g, font, 16, y + 22, cw - 16, "ELEV", String.format(Locale.ROOT, "%.1f DEG", elevation), Ui.TEXT);
            Ui.readout(g, font, 16, y + 36, cw - 16, "LEVEL RNG", range > 1 ? Math.round(range) + " m" : "--", Ui.CYAN);
            Ui.readout(g, font, 16, y + 50, cw - 16, "MUZZLE", Math.round(v * 20) + " m/s", Ui.DIM);
            g.fill(16, y + 64, cw, y + 65, Ui.EDGE);
            g.enableScissor(16, y + 70, cw, y + 85);
            int used = Ui.keyHint(g, font, 16, y + 72, key(ClientSetup.SHELL_TYPE), "SHELL");
            if (used + font.width(key(ClientSetup.RANGE_TABLE)) + 48 <= cw - 16)
                Ui.keyHint(g, font, 20 + used, y + 72, key(ClientSetup.RANGE_TABLE), "TABLE");
            g.disableScissor();
        }

        int x = w - cw - 8;
        Ui.instrument(g, font, x, y, cw, 90, "BREECH", "");
        var shells = gun.shells();
        int selected = Mth.clamp(gun.selectedShell(), 0, shells.size() - 1);
        int loaded = gun.loaded() < 0 ? -1 : Mth.clamp(gun.loaded(), 0, shells.size() - 1);
        String shell = shells.get(loaded >= 0 ? loaded : selected).getDescription().getString();
        g.drawString(font, font.plainSubstrByWidth(shell, cw - 16), x + 8, y + 22, Ui.TEXT, false);
        String state = loaded >= 0 ? Component.translatable("hud.steelrain.breech_loaded").getString()
                : gun.loading() > 0 ? String.format(Locale.ROOT, "LOADING %.1f s", gun.loading() / 20.0)
                : Component.translatable("hud.steelrain.breech_empty").getString();
        int stateColour = loaded >= 0 ? Ui.GREEN : gun.loading() > 0 ? Ui.AMBER : Ui.RED;
        g.drawString(font, font.plainSubstrByWidth(state.toUpperCase(Locale.ROOT), cw - 16), x + 8, y + 36, stateColour, false);
        Ui.readout(g, font, x + 8, y + 50, cw - 16, "CHARGE", (charge + 1) + " / " + charges.length, Ui.AMBER);
        double reload = loaded >= 0 ? 1 : gun.loading() > 0 ? 1 - gun.loading() / (double) Math.max(1, gun.reloadTotal()) : 0;
        Ui.gauge(g, x + 8, y + 64, cw - 16, 4, reload, stateColour, 20);
        g.drawString(font, font.plainSubstrByWidth("SCROLL: CHARGE", cw - 16), x + 8, y + 76, Ui.DIM, false);
        controlHints(g, font, mc, w, h, false, true, false);
        barrelMarker(g, mc, barrel);
    }

    /** Level-ground firing table; the current propellant column stays highlighted. */
    private static void rangeTable(GuiGraphics g, Font font, SrGun gun, double[] charges, int charge, int cw, int bottom) {
        int rows = Mth.clamp((bottom - 85 - 48) / 11, 3, 14), height = 48 + rows * 11;
        int x = 8, y = bottom - height, firstColumn = 0;
        int columns = Math.min(charges.length, Math.max(1, (cw - 42) / 25));
        if (charge >= columns) firstColumn = charge - columns + 1;
        int colWidth = (cw - 40) / columns;
        Ui.instrument(g, font, x, y, cw, height, "RANGE TABLE", "");
        g.drawString(font, "m", x + 8, y + 22, Ui.DIM, false);
        int selectedX = x + 36 + (charge - firstColumn) * colWidth;
        g.fill(selectedX - 2, y + 19, selectedX + colWidth - 3, y + 33 + rows * 11, 0x402C5D47);
        for (int c = 0; c < columns; c++) {
            int idx = firstColumn + c;
            g.drawString(font, "C" + (idx + 1), x + 36 + c * colWidth, y + 22, idx == charge ? Ui.GREEN : Ui.DIM, false);
        }
        for (int row = 0; row < rows; row++) {
            int range = gun.rangeStep() * (row + 1), rowY = y + 35 + row * 11;
            if ((row & 1) == 0) g.fill(x + 4, rowY - 1, x + cw - 4, rowY + 9, 0x13000000);
            g.drawString(font, Integer.toString(range), x + 8, rowY, Ui.DIM, false);
            for (int c = 0; c < columns; c++) {
                int idx = firstColumn + c;
                double e = SrGun.elevationFor(charges[idx], range, 0, gun.minElevation(), gun.maxElevation());
                String value = Double.isNaN(e) ? "--" : String.format(Locale.ROOT, "%.1f", e);
                g.drawString(font, font.plainSubstrByWidth(value, colWidth - 2), x + 36 + c * colWidth, rowY,
                        Double.isNaN(e) ? Ui.FAINT : idx == charge ? Ui.TEXT : Ui.DIM, false);
            }
        }
        String footer = key(ClientSetup.RANGE_TABLE) + ": CLOSE / LEVEL GROUND";
        g.drawString(font, font.plainSubstrByWidth(footer, cw - 16), x + 8, y + height - 11, Ui.FAINT, false);
    }

    // ------------------------------------------------------------------ air defence

    private static void airDefence(GuiGraphics g, SrAirDefence ad, Minecraft mc, float pt) {
        Font font = mc.font;
        int w = g.guiWidth(), h = g.guiHeight(), cw = cardWidth(w), y = h - 142;
        Vec3 guns = ad.gunDirection();
        sightHeader(g, font, w, Ui.upper(ad.getName()).getString(), "AIR DEFENCE", bearingOf(guns));
        var target = ad.target();
        String track = target == null ? "NO TRACK" : ad.locked() ? "LOCKED" : "TRACKING";
        int trackColour = target == null ? Ui.DIM : ad.locked() ? Ui.RED : Ui.AMBER;

        Ui.instrument(g, font, 8, y, cw, 90, ad.hasRadar() ? "RADAR / TRACK" : "GUN LAYING", "");
        g.drawString(font, font.plainSubstrByWidth(ad.hasRadar() ? track : "OPTICAL SIGHT", cw - 16), 16, y + 22,
                ad.hasRadar() ? trackColour : Ui.DIM, false);
        if (target != null && ad.hasRadar()) {
            g.drawString(font, font.plainSubstrByWidth(target.getName().getString(), cw - 16), 16, y + 36, Ui.TEXT, false);
            Ui.readout(g, font, 16, y + 50, cw - 16, "RANGE", (int) target.distanceTo(ad) + " m", trackColour);
        } else {
            Ui.readout(g, font, 16, y + 36, cw - 16, "ELEV", String.format(Locale.ROOT, "%.1f DEG", elevationOf(guns)), Ui.TEXT);
            Ui.readout(g, font, 16, y + 50, cw - 16, "RANGE", (int) Math.max(ad.gunRange(), ad.missileRange()) + " m", Ui.DIM);
        }
        Ui.readout(g, font, 16, y + 65, cw - 16, "MODE", ad.auto() ? "AUTO" : "MANUAL", ad.auto() ? Ui.GREEN : Ui.TEXT);
        g.drawString(font, font.plainSubstrByWidth(key(ClientSetup.AUTO_MODE) + ": AUTO MODE", cw - 16), 16, y + 78, Ui.DIM, false);

        int x = w - cw - 8;
        Ui.instrument(g, font, x, y, cw, 90, "AMMUNITION", "");
        int row = y + 22;
        if (ad.hasGuns()) {
            g.pose().pushPose();
            g.pose().translate(x + 8, row, 0);
            g.pose().scale(1.7f, 1.7f, 1);
            g.drawString(font, Integer.toString(ad.rounds()), 0, 0, ad.rounds() > 0 ? Ui.TEXT : Ui.RED, false);
            g.pose().popPose();
            String belt = ad.beltAp() ? "AP" : "HE";
            g.drawString(font, belt, x + cw - 8 - font.width(belt), row + 4, ad.beltAp() ? Ui.CYAN : Ui.AMBER, false);
            row += 21;
            Ui.readout(g, font, x + 8, row, cw - 16, "HE / AP", ad.stock(0) + " / " + ad.stock(1), Ui.DIM);
            row += 14;
        }
        if (ad.missileRange() > 0) {
            Ui.readout(g, font, x + 8, row, cw - 16, "MISSILES", Integer.toString(ad.stock(2)), ad.stock(2) > 0 ? Ui.CYAN : Ui.RED);
            row += 14;
        }
        if (ad.hasGuns()) {
            Ui.gauge(g, x + 8, y + 65, cw - 16, 4, ad.rounds() / (double) Math.max(1, ad.beltSize()), ad.rounds() > 0 ? Ui.GREEN : Ui.RED, 20);
            g.drawString(font, font.plainSubstrByWidth(key(ClientSetup.SHELL_TYPE) + ": BELT TYPE", cw - 16), x + 8, y + 78, Ui.DIM, false);
        } else {
            Ui.readout(g, font, x + 8, row, cw - 16, "ENVELOPE", (int) ad.missileRange() + " m", Ui.DIM);
            g.drawString(font, font.plainSubstrByWidth("RADAR-GUIDED SYSTEM", cw - 16), x + 8, y + 78, Ui.FAINT, false);
        }
        controlHints(g, font, mc, w, h, ad.hasRadar(), ad.hasGuns(), ad.missileRange() > 0);
        barrelMarker(g, mc, guns);
        if (target != null && ad.hasRadar()) drawTrackedTarget(g, mc, ad, target, pt);
    }

    private static void controlHints(GuiGraphics g, Font font, Minecraft mc, int w, int h, boolean radar, boolean guns, boolean missiles) {
        int x = 8, y = h - 48;
        if (guns) x += Ui.keyHint(g, font, x, y, key(mc.options.keyAttack), "FIRE") + 8;
        if (!radar && !missiles) {
            String tableKey = key(ClientSetup.RANGE_TABLE);
            if (x + font.width(tableKey) + font.width("TABLE") + 16 < w - 8)
                x += Ui.keyHint(g, font, x, y, tableKey, "TABLE") + 8;
        }
        if (radar) {
            String radarKey = key(mc.options.keyPickItem);
            if (x + font.width(radarKey) + font.width("LOCK") + 16 < w - 8)
                x += Ui.keyHint(g, font, x, y, radarKey, "LOCK") + 8;
        }
        if (missiles && x + font.width(key(mc.options.keyUse)) + font.width("MISSILE") + 16 < w - 8)
            Ui.keyHint(g, font, x, y, key(mc.options.keyUse), "MISSILE");
    }

    /** Radar target projection and lead retain the original sight's calculation. */
    private static void drawTrackedTarget(GuiGraphics g, Minecraft mc, SrAirDefence ad, net.minecraft.world.entity.Entity target, float pt) {
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
                if (box != null) {
                    int steps = Math.max(Math.abs(d[0] - box[0]), Math.abs(d[1] - box[1]));
                    for (int i = 0; i < steps; i += 3) {
                        int lx = box[0] + (d[0] - box[0]) * i / Math.max(1, steps), ly = box[1] + (d[1] - box[1]) * i / Math.max(1, steps);
                        g.fill(lx, ly, lx + 1, ly + 1, 0x807CD67C);
                    }
                }
            }
        }
    }

    private ArtilleryHud() {}
}

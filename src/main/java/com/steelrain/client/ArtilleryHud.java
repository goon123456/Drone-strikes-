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
 * Gun sights as bare text over the world, kept to what the gunner needs at a glance: a compass tape, the barrel's
 * elevation by the crosshair, a marker for the barrels catching up with the eye, and the ammunition in the bottom
 * right corner. Details fade out a few seconds after they last changed.
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

    /** Range table shown, toggled with its key. */
    static boolean table;

    private static final Ui.Fader GUN = new Ui.Fader(), AIR = new Ui.Fader();

    private static int compassWidth(int w) { return Mth.clamp(w / 3, 120, 200); }

    private static double bearingOf(Vec3 d) { return (Math.toDegrees(Math.atan2(d.x, -d.z)) + 360) % 360; }

    private static double elevationOf(Vec3 d) { return Math.toDegrees(Math.asin(Mth.clamp(d.y, -1, 1))); }

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

    /**
     * Bare readouts only. Always: the compass with the barrel's bearing at the top, and beside the crosshair the
     * barrel's elevation and where a shell would land on level ground. Bottom right: the breech state always, the
     * shell and charge for a few seconds after they change. The range table only while toggled on.
     */
    private static void artillery(GuiGraphics g, SrGun gun, Minecraft mc) {
        Font font = mc.font;
        int w = g.guiWidth(), h = g.guiHeight();
        Vec3 barrel = gun.gunDirection();
        double bearing = bearingOf(barrel), elevation = elevationOf(barrel);
        Ui.compassBare(g, font, w / 2, 8, compassWidth(w), bearing, 0.9f);

        double[] charges = gun.charges();
        int charge = Mth.clamp(gun.charge(), 0, charges.length - 1);
        double v = charges[charge];
        double range = elevation > 0 ? v * v * Math.sin(Math.toRadians(2 * elevation)) / com.steelrain.entity.MissileEntity.GRAVITY : 0;
        int cx = w / 2 + 14, cy = h / 2 - 9;
        Ui.text(g, font, String.format("EL %.1f", elevation), cx, cy, Ui.HUD_TEXT, 0.9f);
        Ui.text(g, font, range > 1 ? String.format("%,d m", Math.round(range)) : "-- m", cx, cy + 10, Ui.HUD_DIM, 0.9f);

        // breech, shell and charge, bottom right
        var shells = gun.shells();
        String shell = shells.get(Math.min(gun.selectedShell(), shells.size() - 1)).getDescription().getString();
        int state = gun.loaded() >= 0 ? 1 : gun.loading() > 0 ? 2 : 0;
        float a = GUN.update(java.util.List.of(charge, gun.selectedShell(), state));
        int right = w - 10, y = h - 14;
        if (state == 1) Ui.textRight(g, font, Component.translatable("hud.steelrain.breech_loaded").getString(), right, y, Ui.GREEN, 1);
        else if (state == 2) Ui.bigRight(g, font, String.format("%.1fs", gun.loading() / 20.0), right, y - 4, Ui.AMBER, 1);
        else Ui.textRight(g, font, Component.translatable("hud.steelrain.breech_empty").getString(), right, y, Ui.RED, 0.85f);
        y -= state == 2 ? 14 : 11;
        Ui.textRight(g, font, shell, right, y, Ui.HUD_TEXT, Math.max(a, 0.5f));
        y -= 10;
        Ui.textRight(g, font, "CHARGE " + (charge + 1) + "/" + charges.length, right, y, Ui.HUD_DIM, Math.max(a, 0.5f));

        if (table) rangeTable(g, font, gun, charges, charge, h);
        barrelMarker(g, mc, barrel);
    }

    /** Elevation for each charge at each range on level ground, as bare columns at the left edge. */
    private static void rangeTable(GuiGraphics g, Font font, SrGun gun, double[] charges, int charge, int h) {
        int step = gun.rangeStep(), rows = Mth.clamp((h - 120) / 9, 4, 14);
        int x = 10, y = Math.max(40, h / 2 - (rows * 9 + 12) / 2);
        Ui.text(g, font, Ui.upper(Component.translatable("hud.steelrain.range_table")).getString(), x, y, Ui.HUD_DIM, 0.9f);
        y += 12;
        Ui.text(g, font, "m", x, y, Ui.HUD_DIM, 0.7f);
        for (int c = 0; c < charges.length; c++) Ui.text(g, font, "C" + (c + 1), x + 32 + c * 30, y, c == charge ? Ui.AMBER : Ui.HUD_DIM, c == charge ? 1 : 0.7f);
        y += 10;
        for (int r = 0; r < rows; r++, y += 9) {
            int range = step * (r + 1);
            Ui.text(g, font, Integer.toString(range), x, y, Ui.HUD_DIM, 0.8f);
            for (int c = 0; c < charges.length; c++) {
                double e = SrGun.elevationFor(charges[c], range, 0, gun.minElevation(), gun.maxElevation());
                Ui.text(g, font, Double.isNaN(e) ? "-" : String.format("%.1f", e), x + 32 + c * 30, y,
                        c == charge ? Ui.HUD_TEXT : Ui.HUD_DIM, Double.isNaN(e) ? 0.3f : c == charge ? 0.95f : 0.6f);
            }
        }
    }

    // ------------------------------------------------------------------ air defence

    /**
     * Compass at the top, elevation by the crosshair, the radar's contact under the compass, rounds bottom right.
     * The box round the tracked target and the diamond on the lead stay as they were.
     */
    private static void airDefence(GuiGraphics g, SrAirDefence ad, Minecraft mc, float pt) {
        Font font = mc.font;
        int w = g.guiWidth(), h = g.guiHeight();
        Vec3 guns = ad.gunDirection();
        Ui.compassBare(g, font, w / 2, 8, compassWidth(w), bearingOf(guns), 0.9f);
        Ui.text(g, font, String.format("EL %.0f", elevationOf(guns)), w / 2 + 14, h / 2 - 4, Ui.HUD_TEXT, 0.75f);
        var target = ad.target();

        // radar, under the compass
        if (ad.hasRadar()) {
            String radar;
            int rc;
            if (target != null) {
                radar = Component.translatable(ad.locked() ? "hud.steelrain.radar_lock_short" : "hud.steelrain.radar_track_short").getString()
                        + "  " + (int) target.distanceTo(ad) + " m  " + target.getName().getString();
                rc = ad.locked() ? Ui.RED : Ui.AMBER;
            } else {
                radar = Component.translatable("hud.steelrain.radar_search_short").getString();
                rc = Ui.HUD_DIM;
            }
            Ui.textCentre(g, font, radar, w / 2, 40, rc, target != null ? 1 : 0.5f);
        }

        // rounds, belts, missiles, auto: bottom right
        float a = AIR.update(java.util.List.of(ad.beltAp(), ad.stock(0), ad.stock(1), ad.stock(2), ad.auto()));
        int right = w - 10, y = h - 14;
        if (ad.hasGuns()) {
            String belt = ad.beltAp() ? "AP" : "HE";
            Ui.textRight(g, font, belt, right, y, ad.beltAp() ? Ui.CYAN : Ui.AMBER, 1);
            Ui.bigRight(g, font, Integer.toString(ad.rounds()), right - font.width(belt) - 4, y - 4, ad.rounds() > 0 ? Ui.HUD_TEXT : Ui.RED, 1);
            y -= 14;
            Ui.textRight(g, font, Component.translatable("hud.steelrain.belts", ad.stock(0), ad.stock(1)).getString(), right, y, Ui.HUD_DIM, a);
            y -= 10;
        }
        if (ad.missileRange() > 0) {
            Ui.textRight(g, font, Component.translatable("hud.steelrain.missiles_short", ad.stock(2)).getString(), right, y,
                    ad.stock(2) > 0 ? Ui.HUD_TEXT : Ui.RED, ad.stock(2) > 0 ? Math.max(a, 0.6f) : 1);
            y -= 10;
        }
        if (ad.auto()) Ui.textRight(g, font, Component.translatable("hud.steelrain.auto_chip_on").getString(), right, y, Ui.GREEN, 1);

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
    }

    private ArtilleryHud() {}
}

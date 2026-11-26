package com.steelrain.client;

import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.world.phys.Vec3;

/**
 * Gun sight for hand-laid artillery: where the barrel is pointing (compass bearing and elevation) and a range table
 * giving the elevation each charge needs for a distance on level ground. There is no aiming aid beyond that.
 */
public final class ArtilleryHud {
    static void render(GuiGraphics g, DeltaTracker delta) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player != null && !mc.options.hideGui && mc.player.getVehicle() instanceof com.steelrain.entity.vehicle.SrGun sr
                && sr.isGunner(mc.player)) renderOwn(g, sr, mc);
        if (mc.player != null && !mc.options.hideGui && mc.player.getVehicle() instanceof com.steelrain.entity.vehicle.SrAirDefence ad
                && ad.isGunner(mc.player)) renderAir(g, ad, mc, delta.getGameTimeDeltaPartialTick(false));
    }

    /** Screen position of a direction from the eye, or null when it is behind or far off to the side. */
    @javax.annotation.Nullable
    private static int[] onScreen(GuiGraphics g, Minecraft mc, Vec3 dir) {
        var player = mc.player;
        double yaw = Math.toDegrees(Math.atan2(-dir.x, dir.z)), pitch = Math.toDegrees(Math.asin(Math.max(-1, Math.min(1, dir.y))));
        double dyaw = net.minecraft.util.Mth.wrapDegrees(yaw - player.getYRot()), dpitch = pitch + player.getXRot();
        if (Math.abs(dyaw) > 75 || Math.abs(dpitch) > 75) return null;
        double scale = g.guiHeight() / 2.0 / Math.tan(Math.toRadians(mc.options.fov().get() / 2.0));
        return new int[]{(int) (g.guiWidth() / 2.0 + Math.tan(Math.toRadians(dyaw)) * scale), (int) (g.guiHeight() / 2.0 - Math.tan(Math.toRadians(dpitch)) * scale)};
    }

    /**
     * Air defence sight: the radar's state, a box round the tracked target, a diamond where to aim so the shells meet
     * it (radar-laid guns only), a cross where the guns point now, rounds and belts, missiles.
     */
    private static void renderAir(GuiGraphics g, com.steelrain.entity.vehicle.SrAirDefence ad, Minecraft mc, float pt) {
        Font font = mc.font;
        int green = 0xFF8CE08C, amber = 0xFFE8C060, red = 0xFFE07060, white = 0xFFFFFFFF, dim = 0xFFB8C8B0;
        Vec3 eye = mc.player.getEyePosition(pt);
        var target = ad.target();
        int x = 8, y = 8;
        g.fill(x - 4, y - 4, x + 190, y + 66, 0x90101410);
        Component radar;
        if (!ad.hasRadar()) {
            radar = Component.translatable("hud.steelrain.radar_none");
        } else if (target == null) {
            radar = Component.translatable("hud.steelrain.radar_search");
        } else {
            radar = Component.translatable(ad.locked() ? "hud.steelrain.radar_lock" : "hud.steelrain.radar_track",
                    target.getName(), (int) target.distanceTo(ad));
        }
        g.drawString(font, radar, x, y, ad.locked() ? red : target != null ? amber : green, false);
        if (ad.hasGuns()) {
            g.drawString(font, Component.translatable(ad.beltAp() ? "hud.steelrain.belt_ap" : "hud.steelrain.belt_he", ad.rounds(),
                    ad.stock(0), ad.stock(1)), x, y + 12, ad.rounds() > 0 ? white : red, false);
        }
        if (ad.missileRange() > 0) g.drawString(font, Component.translatable("hud.steelrain.missiles", ad.stock(2)), x, y + 24, white, false);
        g.drawString(font, Component.translatable(ad.auto() ? "hud.steelrain.auto_on" : "hud.steelrain.auto_off"), x, y + 36, dim, false);
        g.drawString(font, Component.translatable(ad.hasRadar() ? "hud.steelrain.air_keys_radar" : "hud.steelrain.air_keys"), x, y + 50, 0xFF808880, false);

        // where the guns point right now
        int[] gun = onScreen(g, mc, ad.gunDirection());
        if (gun != null) {
            g.fill(gun[0] - 5, gun[1], gun[0] + 6, gun[1] + 1, 0xC0FFFFFF);
            g.fill(gun[0], gun[1] - 5, gun[0] + 1, gun[1] + 6, 0xC0FFFFFF);
        }
        if (target == null || !ad.hasRadar()) return;
        Vec3 tp = new Vec3(net.minecraft.util.Mth.lerp(pt, target.xo, target.getX()), net.minecraft.util.Mth.lerp(pt, target.yo, target.getY()) + target.getBbHeight() / 2,
                net.minecraft.util.Mth.lerp(pt, target.zo, target.getZ()));
        int[] box = onScreen(g, mc, tp.subtract(eye).normalize());
        int c = ad.locked() ? red : amber;
        if (box != null) {
            int r = 9;
            g.fill(box[0] - r, box[1] - r, box[0] + r, box[1] - r + 1, c); g.fill(box[0] - r, box[1] + r, box[0] + r + 1, box[1] + r + 1, c);
            g.fill(box[0] - r, box[1] - r, box[0] - r + 1, box[1] + r, c); g.fill(box[0] + r, box[1] - r, box[0] + r + 1, box[1] + r, c);
        }
        if (ad.hasGuns()) {
            // the lead: where to put the guns so the shells and the target arrive together
            Vec3 lead = ad.leadOn(target, true);
            int[] d = onScreen(g, mc, lead);
            if (d != null) {
                for (int i = 0; i <= 5; i++) {
                    g.fill(d[0] - 5 + i, d[1] - i, d[0] - 4 + i, d[1] - i + 1, green); g.fill(d[0] + 5 - i, d[1] - i, d[0] + 6 - i, d[1] - i + 1, green);
                    g.fill(d[0] - 5 + i, d[1] + i, d[0] - 4 + i, d[1] + i + 1, green); g.fill(d[0] + 5 - i, d[1] + i, d[0] + 6 - i, d[1] + i + 1, green);
                }
            }
        }
    }

    /**
     * Sight for Steel Rain's own guns: barrel bearing and elevation, the charge and shell, what is in the breech,
     * the range table, and a marker showing where the barrel points while it catches up with the gunner's eye.
     */
    private static void renderOwn(GuiGraphics g, com.steelrain.entity.vehicle.SrGun gun, Minecraft mc) {
        Font font = mc.font;
        Vec3 barrel = gun.gunDirection();
        double bearing = (Math.toDegrees(Math.atan2(barrel.x, -barrel.z)) + 360) % 360;
        double elevation = Math.toDegrees(Math.asin(Math.max(-1, Math.min(1, barrel.y))));
        double[] charges = gun.charges();
        int x = 8, y = 8, rows = 12, green = 0xFF8CE08C, dim = 0xFFB8C8B0, white = 0xFFFFFFFF, amber = 0xFFE8C060;
        int step = gun.rangeStep();
        g.fill(x - 4, y - 4, x + 170, y + 84 + rows * 9, 0x90101410);
        g.drawString(font, Component.translatable("hud.steelrain.gun_bearing", String.format("%05.1f", bearing)), x, y, white, false);
        g.drawString(font, Component.translatable("hud.steelrain.gun_elevation", String.format("%.1f", elevation)), x, y + 10, white, false);
        var shells = gun.shells();
        int charge = gun.charge();
        g.drawString(font, Component.translatable("hud.steelrain.gun_charge", charge + 1, charges.length), x, y + 24, amber, false);
        g.drawString(font, Component.translatable("hud.steelrain.gun_shell", shells.get(Math.min(gun.selectedShell(), shells.size() - 1)).getDescription()), x, y + 34, amber, false);
        Component breech = gun.loaded() >= 0
                ? Component.translatable("hud.steelrain.gun_loaded", shells.get(Math.min(gun.loaded(), shells.size() - 1)).getDescription())
                : gun.loading() > 0 ? Component.translatable("hud.steelrain.gun_loading", (gun.loading() + 19) / 20)
                : Component.translatable("hud.steelrain.gun_empty");
        g.drawString(font, breech, x, y + 44, gun.loaded() >= 0 ? green : 0xFFE07060, false);
        int ty = y + 64;
        g.drawString(font, Component.translatable("hud.steelrain.range_table"), x, ty - 10, green, false);
        g.drawString(font, "RNG", x, ty, dim, false);
        for (int c = 0; c < charges.length; c++) g.drawString(font, "C" + (c + 1), x + 34 + c * 38, ty, c == charge ? amber : dim, false);
        for (int r = 0; r < rows; r++) {
            int range = step * (r + 1), ry = ty + 10 + r * 9;
            g.drawString(font, Integer.toString(range), x, ry, dim, false);
            for (int c = 0; c < charges.length; c++) {
                double e = com.steelrain.entity.vehicle.SrGun.elevationFor(charges[c], range, 0, gun.minElevation(), gun.maxElevation());
                g.drawString(font, Double.isNaN(e) ? "--" : String.format("%.1f", e), x + 34 + c * 38, ry, Double.isNaN(e) ? 0xFF606860 : c == charge ? white : dim, false);
            }
        }
        g.drawString(font, Component.translatable("hud.steelrain.gun_keys"), x, ty + 14 + rows * 9, 0xFF808880, false);

        // where the barrel points now, drawn over the view: it slides onto the crosshair as the gun is laid
        var player = mc.player;
        double gunYaw = Math.toDegrees(Math.atan2(-barrel.x, barrel.z));
        double dyaw = net.minecraft.util.Mth.wrapDegrees(gunYaw - player.getYRot());
        double dpitch = elevation + player.getXRot();
        double half = Math.toRadians(mc.options.fov().get() / 2.0);
        if (Math.abs(dyaw) < 80 && Math.abs(dpitch) < 80) {
            int w = g.guiWidth(), h = g.guiHeight();
            double scale = h / 2.0 / Math.tan(half);
            int mx = (int) (w / 2.0 + Math.tan(Math.toRadians(dyaw)) * scale), my = (int) (h / 2.0 - Math.tan(Math.toRadians(dpitch)) * scale);
            int c = Math.abs(dyaw) < 0.3 && Math.abs(dpitch) < 0.3 ? green : amber;
            g.fill(mx - 6, my, mx - 2, my + 1, c); g.fill(mx + 2, my, mx + 6, my + 1, c);
            g.fill(mx, my - 6, mx + 1, my - 2, c); g.fill(mx, my + 2, mx + 1, my + 6, c);
        }
    }

    private ArtilleryHud() {}
}

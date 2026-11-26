package com.steelrain.client;

import com.steelrain.entity.StrikeVehicle;
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
        if (mc.player != null && !mc.options.hideGui && mc.player.getVehicle() instanceof com.steelrain.entity.vehicle.SrGun sr) {
            if (sr.isGunner(mc.player)) renderOwn(g, sr, mc);
            return;
        }
        if (mc.player == null || mc.options.hideGui || !(mc.player.getVehicle() instanceof StrikeVehicle gun) || !gun.kind().manual()) return;
        Font font = mc.font;
        Vec3 barrel;
        try {
            barrel = gun.getShootVec("Charge1", delta.getGameTimeDeltaPartialTick(false));
        } catch (RuntimeException e) {
            return;
        }
        if (barrel == null || barrel.lengthSqr() < 1e-6) return;
        barrel = barrel.normalize();
        double bearing = (Math.toDegrees(Math.atan2(barrel.x, -barrel.z)) + 360) % 360;
        double elevation = Math.toDegrees(Math.asin(Math.max(-1, Math.min(1, barrel.y))));

        int x = 8, y = 8, green = 0xFF8CE08C, dim = 0xFFB8C8B0, white = 0xFFFFFFFF;
        StrikeVehicle.Kind kind = gun.kind();
        double[] charges = kind.charges();
        int rows = 12;
        int step = kind == StrikeVehicle.Kind.MORTAR ? 35 : 50;
        g.fill(x - 4, y - 4, x + 150, y + 44 + rows * 9, 0x90101410);
        g.drawString(font, Component.translatable("hud.steelrain.gun_bearing", String.format("%05.1f", bearing)), x, y, white, false);
        g.drawString(font, Component.translatable("hud.steelrain.gun_elevation", String.format("%.1f", elevation)), x, y + 10, white, false);
        g.drawString(font, Component.translatable("hud.steelrain.range_table"), x, y + 24, green, false);
        g.drawString(font, "RNG", x, y + 34, dim, false);
        for (int c = 0; c < charges.length; c++) g.drawString(font, "C" + (c + 1), x + 34 + c * 38, y + 34, dim, false);
        for (int r = 0; r < rows; r++) {
            int range = step * (r + 1);
            int ry = y + 44 + r * 9;
            g.drawString(font, Integer.toString(range), x, ry, dim, false);
            for (int c = 0; c < charges.length; c++) {
                double e = StrikeVehicle.elevationFor(kind, charges[c], range, 0);
                g.drawString(font, Double.isNaN(e) ? "--" : String.format("%.1f", e), x + 34 + c * 38, ry, Double.isNaN(e) ? 0xFF606860 : white, false);
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

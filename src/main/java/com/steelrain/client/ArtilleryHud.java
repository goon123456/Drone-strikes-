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

    private ArtilleryHud() {}
}

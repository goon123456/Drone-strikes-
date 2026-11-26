package com.steelrain.client;

import com.steelrain.ModRegistry;
import com.steelrain.entity.vehicle.Jammer;
import com.steelrain.entity.vehicle.SrAirDefence;
import com.steelrain.entity.vehicle.SrGun;
import com.steelrain.entity.vehicle.SrVehicle;
import net.minecraft.Util;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.contents.TranslatableContents;
import net.minecraft.util.Mth;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientChatReceivedEvent;

import java.util.ArrayDeque;
import java.util.Deque;

/**
 * The vehicle dashboard (seat, speed and heading, engine, fuel, battery, damage, and what the vehicle is doing) and
 * Steel Rain's status messages shown as short notices in the same style instead of plain action-bar text.
 */
@EventBusSubscriber(modid = ModRegistry.MODID, value = Dist.CLIENT)
public final class VehicleHud {
    private record Notice(Component text, int colour, long at) {}

    private static final Deque<Notice> NOTICES = new ArrayDeque<>();
    private static final long NOTICE_MS = 2600;
    private static double speedShown;

    // ------------------------------------------------------------------ notices

    @SubscribeEvent
    public static void message(ClientChatReceivedEvent.System e) {
        if (!e.isOverlay() || !(e.getMessage().getContents() instanceof TranslatableContents t) || !t.getKey().startsWith("message.steelrain.")) return;
        e.setCanceled(true);
        String key = t.getKey();
        int colour = key.matches(".*(no_|too_|not_|full|refused|busy|moving|unknown|_off).*") ? Ui.RED
                : key.matches(".*(_on|linked|refuel|loaded|charged|launched|fitted|seat_).*") ? Ui.GREEN : Ui.AMBER;
        if (key.endsWith("unlinked")) colour = Ui.AMBER;
        NOTICES.removeIf(n -> n.text().getString().equals(e.getMessage().getString()));
        NOTICES.addLast(new Notice(e.getMessage(), colour, Util.getMillis()));
        while (NOTICES.size() > 3) NOTICES.removeFirst();
    }

    private static void notices(GuiGraphics g, Font font) {
        long now = Util.getMillis();
        NOTICES.removeIf(n -> now - n.at() > NOTICE_MS);
        int y = g.guiHeight() / 2 + 22;
        for (Notice n : NOTICES) {
            float age = (now - n.at()) / (float) NOTICE_MS;
            int alpha = (int) (255 * Mth.clamp((1 - age) * 4, 0, 1));
            if (alpha < 8) continue;
            int w = font.width(n.text()) + 22, x = (g.guiWidth() - w) / 2;
            g.fill(x, y, x + w, y + 14, (alpha * 0x9A / 255) << 24 | 0x0E1316);
            g.fill(x, y, x + 2, y + 14, (alpha << 24) | (n.colour() & 0xFFFFFF));
            Ui.icon(g, x + 6, y + 3, n.colour() == Ui.RED ? Ui.ICON_WARN : Ui.ICON_SIGNAL, (alpha << 24) | (n.colour() & 0xFFFFFF));
            g.drawString(font, n.text(), x + 16, y + 3, (alpha << 24) | 0xE6EDF0, false);
            y += 16;
        }
    }

    // ------------------------------------------------------------------ dashboard

    static void render(GuiGraphics g, DeltaTracker delta) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.options.hideGui) return;
        notices(g, mc.font);
        if (!(mc.player.getVehicle() instanceof SrVehicle v)) return;
        Font font = mc.font;
        int seat = v.seatOf(mc.player);
        boolean gunner = (v instanceof SrGun gun && gun.isGunner(mc.player)) || (v instanceof SrAirDefence ad && ad.isGunner(mc.player));
        if (gunner) return;                                 // the gun sight shows what the gunner needs
        int pw = 156, x = 6;
        int rows = (v.tankLitres() > 0 ? 1 : 0) + (v.batteryFe() > 0 ? 1 : 0) + 1;
        int ph = 30 + rows * 11 + (v.drivable() ? 12 : 0) + 14;
        int y = g.guiHeight() - ph - 6;
        if (x + pw > g.guiWidth() / 2 - 92) y = g.guiHeight() - ph - 44;     // narrow screen: sit above the hotbar's hearts
        Ui.hudBox(g, x, y, pw, ph);
        // name and seat
        g.drawString(font, font.plainSubstrByWidth(Ui.upper(v.getName()).getString(), pw - 70), x + 5, y + 4, Ui.TEXT, false);
        Component seatName = Component.translatable("hud.steelrain.seat_" + (gunner ? "gunner" : seat == 0 && v.drivable() ? "driver" : "passenger"));
        int sw = font.width(seatName) + 6;
        Ui.chip(g, font, x + pw - 5 - sw, y + 3, seatName, Ui.CYAN);
        int ry = y + 17;
        if (v.drivable()) {
            double moved = Math.sqrt(Mth.square(v.getX() - v.xo) + Mth.square(v.getZ() - v.zo)) * 20 * 3.6;
            speedShown += (moved - speedShown) * 0.2;
            String spd = String.format("%3d km/h", Math.round(speedShown));
            g.drawString(font, spd, x + 5, ry, Ui.TEXT, false);
            double heading = (v.getYRot() + 180 + 360) % 360;
            String hd = String.format("%03d° %s", Math.round(heading) % 360, compassPoint(heading));
            g.drawString(font, hd, x + pw - 5 - font.width(hd), ry, Ui.DIM, false);
            ry += 12;
        }
        // engine state
        Component engine;
        int ec;
        if (!v.drivable()) { engine = Component.translatable("hud.steelrain.engine_static"); ec = Ui.FAINT; }
        else if (v.flooded()) { engine = Component.translatable("hud.steelrain.engine_flooded"); ec = Ui.RED; }
        else if (v.fuel() <= 0) { engine = Component.translatable("hud.steelrain.engine_no_fuel"); ec = Ui.RED; }
        else if (v.engineRunning()) { engine = Component.translatable("hud.steelrain.engine_running"); ec = Ui.GREEN; }
        else { engine = Component.translatable("hud.steelrain.engine_off"); ec = Ui.FAINT; }
        int cx = x + 5 + Ui.chip(g, font, x + 5, ry, engine, ec) + 4;
        if (v instanceof SrAirDefence ad) Ui.chip(g, font, cx, ry, Component.translatable(ad.auto() ? "hud.steelrain.auto_chip_on" : "hud.steelrain.auto_chip_off"), ad.auto() ? Ui.GREEN : Ui.FAINT);
        if (v instanceof Jammer j) Ui.chip(g, font, cx, ry, Component.translatable(j.switchedOn() ? "hud.steelrain.jamming_on" : "hud.steelrain.jamming_off"), j.switchedOn() ? Ui.AMBER : Ui.FAINT);
        ry += 14;
        if (v.tankLitres() > 0) {
            double f = v.fuel() / v.tankLitres();
            Ui.meter(g, font, x + 5, ry, pw - 10, Ui.ICON_FUEL, f, Math.round(v.fuel()) + " L", Ui.level(f));
            ry += 11;
        }
        if (v.batteryFe() > 0) {
            double f = (double) v.energy() / v.batteryFe();
            Ui.meter(g, font, x + 5, ry, pw - 10, Ui.ICON_BOLT, f, v.energy() / 1000 + "k FE", f > 0.2 ? Ui.CYAN : Ui.RED);
            ry += 11;
        }
        double hp = v.health() / v.maxHealth();
        Ui.meter(g, font, x + 5, ry, pw - 10, Ui.ICON_HEART, hp, Math.round(hp * 100) + "%", Ui.level(hp));
        // keys, along the bottom edge of the panel
        String keys = v.seats().length > 1 ? "Z " + Component.translatable("hud.steelrain.key_seat").getString() : "";
        if (!keys.isEmpty()) g.drawString(font, keys, x + 5, y + ph - 10, Ui.FAINT, false);
    }

    private static String compassPoint(double deg) {
        String[] p = {"N", "NE", "E", "SE", "S", "SW", "W", "NW"};
        return p[(int) Math.round(deg / 45) % 8];
    }

    private VehicleHud() {}
}

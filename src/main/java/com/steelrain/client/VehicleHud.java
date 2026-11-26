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

    /** Notices sit just below the crosshair as bare coloured text, fading out after a couple of seconds. */
    private static void notices(GuiGraphics g, Font font) {
        long now = Util.getMillis();
        NOTICES.removeIf(n -> now - n.at() > NOTICE_MS);
        int y = g.guiHeight() / 2 + 24;
        for (Notice n : NOTICES) {
            float age = (now - n.at()) / (float) NOTICE_MS;
            Ui.textCentre(g, font, n.text().getString(), g.guiWidth() / 2, y, n.colour() == Ui.GREEN ? Ui.HUD_TEXT : n.colour(), Mth.clamp((1 - age) * 4, 0, 1));
            y += 11;
        }
    }

    // ------------------------------------------------------------------ dashboard

    private static final Ui.Fader DETAILS = new Ui.Fader();
    private static SrVehicle lastVehicle;

    /**
     * Bottom right, bare text. Speed is always there while moving and warnings (no fuel, flooded, low fuel, heavy
     * damage) stay up while they last; everything else shows for a few seconds after it changes, then fades away.
     */
    static void render(GuiGraphics g, DeltaTracker delta) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.options.hideGui) return;
        notices(g, mc.font);
        if (!(mc.player.getVehicle() instanceof SrVehicle v)) { lastVehicle = null; return; }
        if (v != lastVehicle) { lastVehicle = v; DETAILS.wake(); }
        boolean gunner = (v instanceof SrGun gun && gun.isGunner(mc.player)) || (v instanceof SrAirDefence ad && ad.isGunner(mc.player));
        if (gunner) return;                                 // the gun sight shows what the gunner needs
        Font font = mc.font;
        int seat = v.seatOf(mc.player);
        double fuel = v.tankLitres() > 0 ? v.fuel() / v.tankLitres() : 1;
        double battery = v.batteryFe() > 0 ? (double) v.energy() / v.batteryFe() : 1;
        double hp = v.health() / v.maxHealth();
        String engine = !v.drivable() ? "static" : v.flooded() ? "flooded" : v.fuel() <= 0 ? "no_fuel" : v.engineRunning() ? "running" : "off";
        boolean jamming = v instanceof Jammer j && j.switchedOn(), auto = v instanceof SrAirDefence ad && ad.auto();
        float a = DETAILS.update(java.util.List.of(seat, engine, (int) (fuel * 10), (int) (battery * 10), (int) Math.round(hp * 100), jamming, auto));

        int right = g.guiWidth() - 10, y = g.guiHeight() - 12;
        // speed, the one big number
        if (v.drivable()) {
            double moved = Math.sqrt(Mth.square(v.getX() - v.xo) + Mth.square(v.getZ() - v.zo)) * 20 * 3.6;
            speedShown += (moved - speedShown) * 0.2;
            float sa = Math.max(a, (float) Mth.clamp((speedShown - 0.5) / 2, 0, 1));
            Ui.textRight(g, font, "km/h", right, y, Ui.HUD_DIM, sa);
            Ui.bigRight(g, font, Long.toString(Math.round(speedShown)), right - font.width("km/h") - 4, y - 4, Ui.HUD_TEXT, sa);
            double heading = (v.getYRot() + 180 + 360) % 360;
            y -= 12;
            Ui.textRight(g, font, String.format("%03d %s", Math.round(heading) % 360, compassPoint(heading)), right, y, Ui.HUD_DIM, sa);
            y -= 14;
        }
        // warnings stay up; the rest fades
        String warn = switch (engine) {
            case "flooded" -> Component.translatable("hud.steelrain.engine_flooded").getString();
            case "no_fuel" -> Component.translatable("hud.steelrain.engine_no_fuel").getString();
            default -> null;
        };
        if (warn != null) { Ui.textRight(g, font, warn, right, y, Ui.blink() ? Ui.RED : 0xFFB04040, 1); y -= 10; }
        if (v.tankLitres() > 0) {
            boolean low = fuel < 0.2;
            Ui.textRight(g, font, "FUEL  " + Math.round(v.fuel()) + " L", right, y, low ? Ui.AMBER : Ui.HUD_TEXT, low ? 1 : a);
            y -= 10;
        }
        if (v.batteryFe() > 0) {
            boolean low = battery < 0.2;
            Ui.textRight(g, font, "BATT  " + Math.round(battery * 100) + "%", right, y, low ? Ui.AMBER : Ui.HUD_TEXT, low ? 1 : a);
            y -= 10;
        }
        boolean hurt = hp < 0.35;
        Ui.textRight(g, font, "HULL  " + Math.round(hp * 100) + "%", right, y, hurt ? Ui.RED : hp < 0.6 ? Ui.AMBER : Ui.HUD_TEXT, hurt ? 1 : a);
        y -= 10;
        if (jamming) { Ui.textRight(g, font, Component.translatable("hud.steelrain.jamming_on").getString(), right, y, Ui.AMBER, Math.max(a, 0.55f)); y -= 10; }
        if (auto) { Ui.textRight(g, font, Component.translatable("hud.steelrain.auto_chip_on").getString(), right, y, Ui.GREEN, Math.max(a, 0.55f)); y -= 10; }
        if (warn == null && v.drivable()) {
            Ui.textRight(g, font, Component.translatable("hud.steelrain.engine_" + engine).getString(), right, y, Ui.HUD_DIM, a);
            y -= 10;
        }
        y -= 3;
        String seatName = Component.translatable("hud.steelrain.seat_" + (seat == 0 && v.drivable() ? "driver" : "passenger")).getString();
        Ui.textRight(g, font, Ui.upper(v.getName()).getString() + "  ·  " + seatName, right, y, Ui.HUD_DIM, a);
    }

    private static String compassPoint(double deg) {
        String[] p = {"N", "NE", "E", "SE", "S", "SW", "W", "NW"};
        return p[(int) Math.round(deg / 45) % 8];
    }

    private VehicleHud() {}
}

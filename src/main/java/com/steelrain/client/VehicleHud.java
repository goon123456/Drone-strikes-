package com.steelrain.client;

import com.steelrain.ModRegistry;
import com.steelrain.entity.vehicle.Jammer;
import com.steelrain.entity.vehicle.SrAirDefence;
import com.steelrain.entity.vehicle.SrGun;
import com.steelrain.entity.vehicle.SrVehicle;
import net.minecraft.Util;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.KeyMapping;
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
import java.util.Locale;

/** Driver/passenger instruments and brief system notices, using the tablet's command-display language. */
@EventBusSubscriber(modid = ModRegistry.MODID, value = Dist.CLIENT)
public final class VehicleHud {
    private record Notice(Component text, int colour, long at) {}

    private static final Deque<Notice> NOTICES = new ArrayDeque<>();
    private static final long NOTICE_MS = 3200;
    private static SrVehicle lastVehicle;
    private static double speedShown;
    private static long sampledAt;

    @SubscribeEvent
    public static void message(ClientChatReceivedEvent.System e) {
        if (!e.isOverlay() || !(e.getMessage().getContents() instanceof TranslatableContents t)
                || !t.getKey().startsWith("message.steelrain.")) return;
        e.setCanceled(true);
        String key = t.getKey();
        int colour = key.matches(".*(no_|too_|not_|full|refused|busy|moving|unknown|_off).*") ? Ui.RED
                : key.matches(".*(_on|linked|refuel|loaded|charged|launched|fitted|seat_).*") ? Ui.GREEN : Ui.AMBER;
        if (key.endsWith("unlinked")) colour = Ui.AMBER;
        NOTICES.removeIf(n -> n.text().getString().equals(e.getMessage().getString()));
        NOTICES.addLast(new Notice(e.getMessage(), colour, Util.getMillis()));
        while (NOTICES.size() > 2) NOTICES.removeFirst();
    }

    /** Camera notices temporarily replace its title row; gun notices use the reserved gap below the compass. */
    private static void notices(GuiGraphics g, Font font, int y, boolean latestOnly, boolean replaceTitle) {
        long now = Util.getMillis();
        NOTICES.removeIf(n -> now - n.at() > NOTICE_MS);
        if (latestOnly) {
            Notice latest = NOTICES.peekLast();
            if (latest != null) {
                int width = replaceTitle ? g.guiWidth() - 18 : Math.min(260, g.guiWidth() - 24);
                drawNotice(g, font, latest, (g.guiWidth() - width) / 2, y, width, now, replaceTitle);
            }
            return;
        }
        int width = Math.min(260, g.guiWidth() - 24), x = (g.guiWidth() - width) / 2;
        for (Notice n : NOTICES) {
            drawNotice(g, font, n, x, y, width, now, false);
            y += 16;
        }
    }

    private static void drawNotice(GuiGraphics g, Font font, Notice notice, int x, int y, int width, long now, boolean replaceTitle) {
        // A replacement title stays opaque until expiry so the underlying title never bleeds through the message.
        float alpha = replaceTitle ? 1 : Mth.clamp((NOTICE_MS - (now - notice.at())) / 500f, 0, 1);
        if (alpha < 0.04f) return;
        g.fill(x, y, x + width, y + 13, replaceTitle ? 0xFF0A1410 : Ui.fade(0xEC081110, alpha));
        g.fill(x, y, x + 2, y + 13, Ui.fade(notice.colour(), alpha));
        g.fill(x + 2, y + 12, x + width, y + 13, Ui.fade(0xFF354C41, alpha));
        String text = font.plainSubstrByWidth(notice.text().getString(), width - 14);
        Ui.text(g, font, text, x + 7, y + 3, notice.colour() == Ui.GREEN ? Ui.TEXT : notice.colour(), alpha);
    }

    static void render(GuiGraphics g, DeltaTracker delta) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.options.hideGui || mc.screen != null) return;
        SrVehicle v = mc.player.getVehicle() instanceof SrVehicle vehicle ? vehicle : null;
        boolean gunner = (v instanceof SrGun gun && gun.isGunner(mc.player))
                || (v instanceof SrAirDefence ad && ad.isGunner(mc.player));
        boolean camera = FpvClient.viewing();
        // These slots are shared with the gun/drone HUDs; none covers their live telemetry or the reticle.
        notices(g, mc.font, camera ? 9 : gunner ? 68 : v != null ? 8 : 50, camera || gunner, camera);
        if (v == null) { lastVehicle = null; return; }
        if (gunner || camera) return; // Dedicated sights have their own instruments.

        long now = Util.getMillis();
        double moved = Math.hypot(v.getX() - v.xo, v.getZ() - v.zo) * 72;
        if (v != lastVehicle) {
            lastVehicle = v;
            speedShown = moved;
            sampledAt = now;
        }
        // Time-based smoothing behaves consistently at different frame rates.
        double blend = 1 - Math.exp(-Math.min(250, now - sampledAt) / 150.0);
        speedShown += (moved - speedShown) * blend;
        sampledAt = now;

        int panelWidth = Math.min(156, (g.guiWidth() - 44) / 2);
        int panelHeight = 122, y = g.guiHeight() - 52 - panelHeight;
        mobility(g, mc, v, 8, y, panelWidth, panelHeight);
        resources(g, mc, v, g.guiWidth() - panelWidth - 8, y, panelWidth, panelHeight);
    }

    private static void mobility(GuiGraphics g, Minecraft mc, SrVehicle v, int x, int y, int width, int height) {
        Font font = mc.font;
        int seat = v.seatOf(mc.player);
        String role = seat == 0 && v.drivable() ? "DRIVER" : "CREW";
        Ui.instrument(g, font, x, y, width, height, "MOBILITY", role);
        g.drawString(font, font.plainSubstrByWidth(Ui.upper(v.getName()).getString(), width - 14), x + 7, y + 21, Ui.TEXT, false);

        if (v.drivable()) {
            String speed = Long.toString(Math.round(speedShown));
            g.pose().pushPose();
            g.pose().translate(x + 7, y + 34, 0);
            g.pose().scale(2f, 2f, 1);
            g.drawString(font, speed, 0, 0, Ui.TEXT, false);
            g.pose().popPose();
            g.drawString(font, "km/h", x + 7, y + 53, Ui.DIM, false);
        } else {
            g.drawString(font, "EMPLACED", x + 7, y + 40, Ui.DIM, false);
        }
        double heading = Mth.positiveModulo(v.getYRot() + 180, 360);
        String degrees = String.format(Locale.ROOT, "%03d", Math.round(heading) % 360);
        int hx = x + width - 42;
        g.pose().pushPose();
        g.pose().translate(hx, y + 36, 0);
        g.pose().scale(1.5f, 1.5f, 1);
        g.drawString(font, degrees, 0, 0, Ui.CYAN, false);
        g.pose().popPose();
        g.drawString(font, "HDG " + compassPoint(heading), hx - 2, y + 53, Ui.DIM, false);
        g.fill(x + 6, y + 65, x + width - 6, y + 66, Ui.EDGE);

        String engine = !v.drivable() ? "STATIC" : v.flooded() ? "FLOODED" : v.fuel() <= 0 ? "NO FUEL" : v.engineRunning() ? "RUNNING" : "OFF";
        int engineColour = v.drivable() && (v.flooded() || v.fuel() <= 0) ? Ui.RED : v.engineRunning() ? Ui.GREEN : Ui.DIM;
        Ui.readout(g, font, x + 7, y + 71, width - 14, "ENGINE", engine, engineColour);
        Ui.readout(g, font, x + 7, y + 84, width - 14, "SEAT", seat < 0 ? "SYNCING" : (seat + 1) + " / " + v.seats().length, Ui.TEXT);
        hint(g, font, x + 7, y + 98, width - 14, ClientSetup.SWITCH_SEAT, "SEAT");
        hint(g, font, x + 7, y + 110, width - 14, mc.options.keyShift, "EXIT");
    }

    private static void resources(GuiGraphics g, Minecraft mc, SrVehicle v, int x, int y, int width, int height) {
        Font font = mc.font;
        double fuel = v.tankLitres() > 0 ? v.fuel() / v.tankLitres() : 1;
        double battery = v.batteryFe() > 0 ? (double) v.energy() / v.batteryFe() : 1;
        double hull = v.maxHealth() > 0 ? v.health() / v.maxHealth() : 0;
        boolean warning = hull < 0.35 || fuel < 0.2 || battery < 0.2 || v.flooded();
        Ui.instrument(g, font, x, y, width, height, "SYSTEMS", "");
        String state = warning ? "CHECK" : "OK";
        g.drawString(font, state, x + width - 7 - font.width(state), y + 4,
                hull < 0.35 || v.flooded() || (v.tankLitres() > 0 && v.fuel() <= 0) ? Ui.RED : warning ? Ui.AMBER : Ui.GREEN, false);
        int row = y + 21;
        if (v.tankLitres() > 0) {
            resource(g, font, x + 7, row, width - 14, "FUEL", Math.round(v.fuel()) + " L", fuel, resourceColour(fuel));
            row += 22;
        }
        if (v.batteryFe() > 0) {
            resource(g, font, x + 7, row, width - 14, "BATT", Math.round(battery * 100) + "%", battery, resourceColour(battery));
            row += 22;
        }
        resource(g, font, x + 7, row, width - 14, "HULL", Math.round(hull * 100) + "%", hull, hull < 0.35 ? Ui.RED : hull < 0.6 ? Ui.AMBER : Ui.GREEN);
        row += 24;
        if (v instanceof Jammer jammer) {
            Ui.readout(g, font, x + 7, row, width - 14, "EW SWITCH", jammer.switchedOn() ? "ON" : "OFF", jammer.switchedOn() ? Ui.AMBER : Ui.DIM);
        } else if (v instanceof SrAirDefence ad) {
            Ui.readout(g, font, x + 7, row, width - 14, "AUTO MODE", ad.auto() ? "ON" : "OFF", ad.auto() ? Ui.GREEN : Ui.DIM);
            hint(g, font, x + 7, y + 110, width - 14, ClientSetup.AUTO_MODE, "AUTO");
        }
        String warningText = v.flooded() ? "ENGINE FLOODED" : hull < 0.35 ? "HULL CRITICAL"
                : v.tankLitres() > 0 && v.fuel() <= 0 ? "FUEL EMPTY" : v.batteryFe() > 0 && v.energy() <= 0 ? "BATTERY EMPTY"
                : fuel < 0.2 ? "LOW FUEL" : battery < 0.2 ? "LOW BATTERY" : "";
        if (!warningText.isEmpty() && !(v instanceof SrAirDefence)) {
            g.drawString(font, font.plainSubstrByWidth(warningText, width - 14), x + 7, y + 110,
                    hull < 0.35 || v.flooded() || (v.tankLitres() > 0 && v.fuel() <= 0) ? Ui.RED : Ui.AMBER, false);
        }
    }

    private static void resource(GuiGraphics g, Font font, int x, int y, int width, String label, String value, double fraction, int colour) {
        Ui.readout(g, font, x, y, width, label, value, colour);
        Ui.gauge(g, x, y + 11, width, 4, fraction, colour, 16);
    }

    private static int resourceColour(double fraction) {
        return fraction <= 0.1 ? Ui.RED : fraction < 0.25 ? Ui.AMBER : Ui.GREEN;
    }

    private static void hint(GuiGraphics g, Font font, int x, int y, int width, KeyMapping mapping, String action) {
        String key = font.plainSubstrByWidth(Ui.keyName(mapping), Math.max(12, width - font.width(action) - 15));
        Ui.keyHint(g, font, x, y, key, action);
    }

    private static String compassPoint(double deg) {
        String[] points = {"N", "NE", "E", "SE", "S", "SW", "W", "NW"};
        return points[(int) Math.round(deg / 45) % 8];
    }

    private VehicleHud() {}
}

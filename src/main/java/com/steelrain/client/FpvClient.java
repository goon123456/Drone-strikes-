package com.steelrain.client;

import com.steelrain.ModRegistry;
import com.steelrain.entity.AircraftEntity;
import com.steelrain.entity.FpvDroneEntity;
import com.steelrain.net.Payloads;
import com.steelrain.weapon.AircraftType;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.player.Input;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.InputEvent;
import net.neoforged.neoforge.client.event.MovementInputUpdateEvent;
import net.neoforged.neoforge.client.event.RenderHandEvent;
import net.neoforged.neoforge.client.event.ViewportEvent;
import net.neoforged.neoforge.network.PacketDistributor;

/**
 * Client half of every drone camera: hand-flown FPV / Mavic (camera switch and stick input) and the remote
 * feed of autopilot aircraft (free-look gimbal). Both share the zoom and the goggles HUD.
 */
@EventBusSubscriber(modid = ModRegistry.MODID, value = Dist.CLIENT)
public final class FpvClient {
    // hand-flown drone
    private static int droneId = -1;
    private static int maxBattery = 3000;
    private static int waitTicks;
    private static boolean attached;
    private static final java.util.Random STATIC = new java.util.Random();
    private static float forward, strafe, vertical;
    private static boolean detonate;
    private static float roll;
    private static double speed;
    // remote feed
    private static int feedId = -1;
    private static int feedType;
    // shared
    private static float zoom = 1f;

    public static boolean piloting() { return droneId >= 0; }

    /** Only an attached, live camera owns the sensor overlay. */
    static boolean viewing() { return (attached && drone() != null) || feed() != null; }

    static void start(int id, int battery) {
        droneId = id;
        maxBattery = Math.max(1, battery);
        waitTicks = 100;
        attached = false;
        detonate = false;
        zoom = 1f;
    }

    static void stopIf(int id) {
        if (droneId == id) stop();
    }

    private static void stop() {
        // The server puts the camera back when it returns the pilot to their body.
        droneId = -1;
        attached = false;
        zoom = 1f;
    }

    static void startFeed(int id, int aircraftType) {
        feedId = id;
        feedType = aircraftType;
        zoom = 1f;
        Minecraft mc = Minecraft.getInstance();
        if (mc.player != null) mc.player.setXRot(50f); // start looking down at the ground
    }

    static void stopFeed() {
        feedId = -1;
        zoom = 1f;
    }

    /** The drone we are flying, once the server has put our camera inside it. */
    private static FpvDroneEntity drone() {
        Minecraft mc = Minecraft.getInstance();
        return droneId >= 0 && mc.getCameraEntity() instanceof FpvDroneEntity d && d.getId() == droneId && !d.isRemoved() ? d : null;
    }

    private static AircraftEntity feed() {
        Minecraft mc = Minecraft.getInstance();
        return feedId >= 0 && mc.getCameraEntity() instanceof AircraftEntity a && a.getId() == feedId ? a : null;
    }

    @SubscribeEvent
    public static void tick(ClientTickEvent.Post event) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.level == null) {
            droneId = -1;
            feedId = -1;
            return;
        }
        boolean exit = false;
        while (ClientSetup.EXIT_DRONE.consumeClick()) exit = true;
        if (feedId >= 0 && exit) PacketDistributor.sendToServer(new Payloads.StopFeed());

        // A jammed link hisses, whether we are flying the drone or watching an aircraft's feed.
        int jam = drone() != null ? drone().jamPercent() : feed() != null ? feed().jamPercent() : 0;
        ClientHooks.staticNoise(jam / 100f);

        if (droneId < 0) return;
        FpvDroneEntity d = drone();
        if (d == null) {
            // Either the server has not moved us into it yet, or it is gone.
            if (attached || --waitTicks < 0) stop();
            return;
        }
        attached = true;
        speed = Math.sqrt(Mth.square(d.getX() - d.xo) + Mth.square(d.getY() - d.yo) + Mth.square(d.getZ() - d.zo)) * 20;
        PacketDistributor.sendToServer(new Payloads.Control(droneId, forward, strafe, vertical,
                mc.player.getYRot(), mc.player.getXRot(), detonate));
        detonate = false;
        if (exit) PacketDistributor.sendToServer(new Payloads.StopPilot(droneId));
    }

    /** The player's own body stays put: movement keys go to the drone instead. */
    @SubscribeEvent
    public static void input(MovementInputUpdateEvent event) {
        if (droneId < 0) return;
        Input in = event.getInput();
        forward = Math.signum(in.forwardImpulse);
        strafe = Math.signum(in.leftImpulse);
        vertical = (in.jumping ? 1 : 0) - (in.shiftKeyDown ? 1 : 0);
        in.forwardImpulse = 0;
        in.leftImpulse = 0;
        in.up = in.down = in.left = in.right = false;
        in.jumping = false;
        in.shiftKeyDown = false;
    }

    @SubscribeEvent
    public static void click(InputEvent.InteractionKeyMappingTriggered event) {
        if (droneId < 0 && feed() == null) return;
        if (droneId >= 0 && event.isAttack()) detonate = true;
        event.setSwingHand(false);
        event.setCanceled(true);
    }

    /** Scroll wheel zooms any drone camera. */
    @SubscribeEvent
    public static void scroll(InputEvent.MouseScrollingEvent event) {
        if ((droneId < 0 || !attached) && feed() == null) return;
        zoom = Mth.clamp(zoom * (event.getScrollDeltaY() > 0 ? 1.3f : 1 / 1.3f), 1f, 12f);
        event.setCanceled(true);
    }

    @SubscribeEvent
    public static void fov(ViewportEvent.ComputeFov event) {
        if (zoom > 1.01f && ((droneId >= 0 && attached) || feed() != null)) {
            event.setFOV(event.getFOV() / zoom);
        }
    }

    /** From a drone the ground can be further away than the normal fog distance: push the fog back. */
    @SubscribeEvent
    public static void fog(ViewportEvent.RenderFog event) {
        Minecraft mc = Minecraft.getInstance();
        if (((droneId < 0 || !attached) && feed() == null) || mc.getCameraEntity() == null || mc.level == null) return;
        float height = (float) (mc.getCameraEntity().getY() - mc.level.getMinBuildHeight());
        float far = Math.max(event.getFarPlaneDistance(), height * 1.6f + 96f);
        event.setFarPlaneDistance(far);
        event.setNearPlaneDistance(far * 0.85f);
        event.setCanceled(true);
    }

    @SubscribeEvent
    public static void hand(RenderHandEvent event) {
        if ((droneId >= 0 && attached) || feed() != null) event.setCanceled(true);
    }

    @SubscribeEvent
    public static void camera(ViewportEvent.ComputeCameraAngles event) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || event.getCamera().isDetached()) return;
        boolean flying = droneId >= 0 && attached;
        if (!flying && feed() == null) return;
        float pt = (float) event.getPartialTick();
        // Use the local look direction directly so the view reacts without network delay.
        event.setYaw(mc.player.getViewYRot(pt));
        event.setPitch(mc.player.getViewXRot(pt));
        if (flying) {
            FpvDroneEntity d = drone();
            float lean = d != null && d.mavic() ? 3f : 9f;
            roll += (-strafe * lean - roll) * 0.08f;
            event.setRoll(roll);
        }
    }

    @SubscribeEvent
    public static void logout(ClientPlayerNetworkEvent.LoggingOut event) {
        droneId = -1;
        feedId = -1;
        zoom = 1f;
        ReconClient.clear();
        CableClient.clear();
    }

    /** Fine corner marks and an open reticle leave the centre of the video unobstructed. */
    private static void frame(GuiGraphics g, int w, int h, int colour) {
        Ui.brackets(g, 4, 4, w - 8, h - 8, 14, Ui.fade(colour, 0.65f));
        int cx = w / 2, cy = h / 2;
        g.fill(cx - 13, cy, cx - 5, cy + 1, colour);
        g.fill(cx + 6, cy, cx + 14, cy + 1, colour);
        g.fill(cx, cy - 13, cx + 1, cy - 5, colour);
        g.fill(cx, cy + 6, cx + 1, cy + 14, colour);
        g.fill(cx, cy, cx + 1, cy + 1, Ui.TEXT);
    }

    /** Video noise that thickens as the link gets worse. */
    private static void noise(GuiGraphics g, int w, int h, int jamPercent) {
        if (jamPercent <= 2) return;
        float j = Mth.clamp(jamPercent / 100f, 0, 1);
        g.fill(0, 0, w, h, ((int) (j * 110) << 24) | 0x808080);
        int n = (int) (j * j * 500) + (int) (j * 60);
        for (int i = 0; i < n; i++) {
            int x = STATIC.nextInt(w), y = STATIC.nextInt(h);
            int len = 4 + STATIC.nextInt(60), shade = STATIC.nextInt(256);
            int alpha = 60 + STATIC.nextInt((int) (j * 190) + 1);
            g.fill(x, y, Math.min(w, x + len), y + 1 + STATIC.nextInt(2), (alpha << 24) | (shade << 16) | (shade << 8) | shade);
        }
        if (j > 0.6f) {
            for (int i = 0; i < 3; i++) {
                int y = STATIC.nextInt(h);
                g.fill(0, y, w, y + 3 + STATIC.nextInt(10), 0xC0101010);
            }
        }
    }

    /** Framed sensor telemetry uses only the active camera entity and synchronized drone data. */
    static void renderHud(GuiGraphics g, DeltaTracker delta) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.options.hideGui || mc.screen != null) return;
        AircraftEntity aircraft = feed();
        FpvDroneEntity d = drone();
        if (aircraft == null && (d == null || !attached)) return;
        Entity camera = aircraft != null ? aircraft : d;
        Font font = mc.font;
        int w = g.guiWidth(), h = g.guiHeight();
        int panelWidth = Math.min(164, Math.max(100, (w - 48) / 2));
        int right = w - 8 - panelWidth;
        int jam = aircraft != null ? aircraft.jamPercent() : d.jamPercent();
        int reticle = aircraft != null ? Ui.CYAN : Ui.GREEN;
        noise(g, w, h, jam);
        frame(g, w, h, reticle);

        String mode = aircraft != null ? "REMOTE GIMBAL" : d.mavic() ? "MAVIC / MANUAL" : d.fiber() ? "FPV / FIBRE" : "FPV / RADIO";
        String name = aircraft != null
                ? Component.translatable(AircraftType.values()[Mth.clamp(feedType, 0, AircraftType.values().length - 1)].translationKey()).getString()
                : d.getName().getString();
        sensorHeader(g, font, w, mode, name, camera, jam);

        double heading = (mc.player.getYRot() + 180.0) % 360;
        if (heading < 0) heading += 360;
        // At small GUI sizes the heading stays in the flight panel instead of overlapping corner panels.
        int tapeWidth = Math.min(220, w - panelWidth * 2 - 48);
        if (tapeWidth >= 120) Ui.compass(g, font, w / 2, 51, tapeWidth, heading, reticle);

        Ui.instrument(g, font, 8, 50, panelWidth, 48, aircraft != null ? "GIMBAL" : "PILOT INPUT", aircraft != null ? "LOOK" : "MANUAL");
        if (aircraft != null) {
            Ui.readout(g, font, 15, 70, panelWidth - 14, "PAN", String.format(java.util.Locale.ROOT, "%05.1f DEG", heading), Ui.TEXT);
            Ui.readout(g, font, 15, 83, panelWidth - 14, "TILT", String.format(java.util.Locale.ROOT, "%+.1f DEG", -mc.player.getXRot()), Ui.TEXT);
        } else {
            String move = key(mc.options.keyUp) + "/" + key(mc.options.keyDown) + "  " + key(mc.options.keyLeft) + "/" + key(mc.options.keyRight);
            Ui.readout(g, font, 15, 70, panelWidth - 14, "MOVE", move, Ui.TEXT);
            Ui.readout(g, font, 15, 83, panelWidth - 14, "UP/DN", key(mc.options.keyJump) + "/" + key(mc.options.keyShift), Ui.TEXT);
        }

        double actualSpeed = aircraft != null
                ? Math.sqrt(Mth.square(camera.getX() - camera.xo) + Mth.square(camera.getY() - camera.yo) + Mth.square(camera.getZ() - camera.zo)) * 20
                : speed;
        flight(g, font, right, 50, panelWidth, camera, actualSpeed, heading);
        if (aircraft != null) feedLink(g, font, 8, h - 121, panelWidth, jam);
        else dronePower(g, font, 8, h - (d.mavic() ? 135 : 121), panelWidth, d, jam);
        controls(g, font, right, h - 93, panelWidth, d, aircraft != null);
    }

    private static String key(net.minecraft.client.KeyMapping mapping) {
        return Ui.keyName(mapping);
    }

    private static void sensorHeader(GuiGraphics g, Font font, int w, String mode, String name, Entity camera, int jam) {
        String title = jam > 30 ? Component.translatable("hud.steelrain.jammed").getString() + " / " + jam + "%" : mode;
        Ui.instrument(g, font, 8, 8, w - 16, 34, title, "LIVE");
        int status = jam > 60 ? Ui.RED : jam > 30 ? Ui.AMBER : Ui.GREEN;
        g.fill(w - 49, 14, w - 45, 18, status);
        if (jam > 30) g.fill(8, 8, 10, 42, Ui.blink() ? status : Ui.fade(status, 0.5f));
        String grid = "GRID " + Mth.floor(camera.getX()) + " " + Mth.floor(camera.getZ());
        int gridWidth = Math.min(font.width(grid), Math.max(0, (w - 40) / 2));
        String gridText = font.plainSubstrByWidth(grid, gridWidth);
        int gx = w - 15 - font.width(gridText);
        g.drawString(font, font.plainSubstrByWidth(name.toUpperCase(java.util.Locale.ROOT), Math.max(0, gx - 24)), 15, 28, Ui.TEXT, false);
        g.drawString(font, gridText, gx, 28, Ui.DIM, false);
    }

    /** World Y is shown explicitly; no above-ground altitude is inferred from unavailable terrain. */
    private static void flight(GuiGraphics g, Font font, int x, int y, int width, Entity camera, double spd, double heading) {
        Ui.instrument(g, font, x, y, width, 76, "FLIGHT", "TELEMETRY");
        int iy = y + 20, iw = width - 14;
        Ui.readout(g, font, x + 7, iy, iw, "ALT Y", Math.round(camera.getY()) + " m", Ui.TEXT);
        Ui.readout(g, font, x + 7, iy + 10, iw, "SPD", String.format(java.util.Locale.ROOT, "%.1f m/s", spd), Ui.TEXT);
        Ui.readout(g, font, x + 7, iy + 20, iw, "V/S", String.format(java.util.Locale.ROOT, "%+.1f m/s", (camera.getY() - camera.yo) * 20), Ui.DIM);
        Ui.readout(g, font, x + 7, iy + 30, iw, "HDG", String.format(java.util.Locale.ROOT, "%03d DEG", Math.round(heading) % 360), Ui.TEXT);
        Ui.readout(g, font, x + 7, iy + 40, iw, "ZOOM", String.format(java.util.Locale.ROOT, "%.1fx", zoom), zoom > 1.01f ? Ui.CYAN : Ui.DIM);
    }

    private static void dronePower(GuiGraphics g, Font font, int x, int y, int width, FpvDroneEntity d, int jam) {
        boolean blink = Ui.blink();
        double battery = Mth.clamp(d.battery() / (double) maxBattery, 0, 1);
        int powerColour = battery < 0.2 ? Ui.RED : Ui.level(battery);
        Ui.instrument(g, font, x, y, width, d.mavic() ? 100 : 86, "POWER / LINK", d.fiber() ? "FIBRE" : "RADIO");
        Ui.readout(g, font, x + 7, y + 20, width - 14, "BATTERY", Math.round(battery * 100) + "%", battery < 0.2 && !blink ? Ui.AMBER : powerColour);
        Ui.gauge(g, x + 7, y + 33, width - 14, 4, battery, powerColour, 20);
        if (d.fiber()) {
            int left = Math.max(0, d.cableLeft());
            Ui.readout(g, font, x + 7, y + 45, width - 14, "CABLE", left + " m", left < 30 ? Ui.RED : Ui.CYAN);
            g.drawString(font, "OPTICAL DATALINK", x + 7, y + 58, Ui.DIM, false);
        } else {
            int signal = Mth.clamp(d.signal(), 0, 100);
            int signalColour = signal < 25 ? Ui.RED : signal < 50 ? Ui.AMBER : Ui.GREEN;
            Ui.readout(g, font, x + 7, y + 45, width - 14, "SIGNAL", signal + "%", signalColour);
            Ui.gauge(g, x + 7, y + 58, width - 14, 4, signal / 100.0, signalColour, 20);
        }
        Ui.readout(g, font, x + 7, y + 71, width - 14, "BODY", d.distance() + " m", Ui.TEXT);
        if (d.mavic()) Ui.readout(g, font, x + 7, y + 85, width - 14, "GRENADE", d.hasGrenade() ? "READY" : "EMPTY", d.hasGrenade() ? Ui.AMBER : Ui.DIM);
    }

    private static void feedLink(GuiGraphics g, Font font, int x, int y, int width, int jam) {
        int colour = jam > 60 ? Ui.RED : jam > 30 ? Ui.AMBER : Ui.GREEN;
        Ui.instrument(g, font, x, y, width, 86, "SENSOR LINK", "REMOTE");
        Ui.readout(g, font, x + 7, y + 20, width - 14, "STATE", jam > 60 ? "HEAVY JAM" : jam > 30 ? "DEGRADED" : "ONLINE", colour);
        Ui.readout(g, font, x + 7, y + 35, width - 14, "JAM", Mth.clamp(jam, 0, 100) + "%", colour);
        // This gauge is interference, not an invented radio strength estimate.
        Ui.gauge(g, x + 7, y + 48, width - 14, 4, Mth.clamp(jam / 100.0, 0, 1), colour, 20);
        g.drawString(font, "LIVE CAMERA FEED", x + 7, y + 62, Ui.DIM, false);
        g.drawString(font, "GIMBAL / FREE LOOK", x + 7, y + 73, Ui.DIM, false);
    }

    private static void controls(GuiGraphics g, Font font, int x, int y, int width, FpvDroneEntity d, boolean remote) {
        Minecraft mc = Minecraft.getInstance();
        Ui.instrument(g, font, x, y, width, 58, "CONTROLS", remote ? "CAM" : "FPV");
        keyRow(g, font, x + 7, y + 19, width - 14, key(ClientSetup.EXIT_DRONE), remote ? "EXIT FEED" : "EXIT DRONE");
        keyRow(g, font, x + 7, y + 31, width - 14, "WHEEL", "ZOOM");
        if (remote) keyRow(g, font, x + 7, y + 43, width - 14, "MOUSE", "GIMBAL");
        else keyRow(g, font, x + 7, y + 43, width - 14, key(mc.options.keyAttack), d.mavic() ? "DROP" : "DETONATE");
    }

    private static void keyRow(GuiGraphics g, Font font, int x, int y, int width, String key, String action) {
        String boundedKey = font.plainSubstrByWidth(key, Math.max(12, width - font.width(action) - 16));
        Ui.keyHint(g, font, x, y, boundedKey, action);
    }

    private FpvClient() {}
}

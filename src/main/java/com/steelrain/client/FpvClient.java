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

    private static void frame(GuiGraphics g, int w, int h, int colour) {
        int m = 12, len = 18;
        g.fill(m, m, m + len, m + 1, colour); g.fill(m, m, m + 1, m + len, colour);
        g.fill(w - m - len, m, w - m, m + 1, colour); g.fill(w - m - 1, m, w - m, m + len, colour);
        g.fill(m, h - m - 1, m + len, h - m, colour); g.fill(m, h - m - len, m + 1, h - m, colour);
        g.fill(w - m - len, h - m - 1, w - m, h - m, colour); g.fill(w - m - 1, h - m - len, w - m, h - m, colour);
        int cx = w / 2, cy = h / 2;
        g.fill(cx - 12, cy, cx - 4, cy + 1, colour); g.fill(cx + 5, cy, cx + 13, cy + 1, colour);
        g.fill(cx, cy - 12, cx + 1, cy - 4, colour); g.fill(cx, cy + 5, cx + 1, cy + 13, colour);
    }

    /** Video noise that thickens as the link gets worse. */
    private static void noise(GuiGraphics g, int w, int h, int jamPercent) {
        if (jamPercent <= 2) return;
        float j = jamPercent / 100f;
        g.fill(0, 0, w, h, ((int) (j * 110) << 24) | 0x808080);
        int n = (int) (j * j * 500) + (int) (j * 60);
        for (int i = 0; i < n; i++) {
            int x = STATIC.nextInt(w), y = STATIC.nextInt(h);
            int len = 4 + STATIC.nextInt(60), shade = STATIC.nextInt(256);
            int alpha = 60 + STATIC.nextInt((int) (j * 190) + 1);
            g.fill(x, y, Math.min(w, x + len), y + 1 + STATIC.nextInt(2), (alpha << 24) | (shade << 16) | (shade << 8) | shade);
        }
        if (j > 0.6f) { // rolling tear bars
            for (int i = 0; i < 3; i++) {
                int y = STATIC.nextInt(h);
                g.fill(0, y, w, y + 3 + STATIC.nextInt(10), 0xC0101010);
            }
        }
    }

    private static void right(GuiGraphics g, Font font, Component text, int w, int y, int colour) {
        g.drawString(font, text, w - 18 - font.width(text), y, colour, true);
    }

    private static final Ui.Fader FEED_INFO = new Ui.Fader();

    /**
     * The goggles view as bare text: what you are flying and a recording dot top left, altitude, speed and range top
     * right, battery and link bottom left. Warnings take over the centre only while the link is being jammed.
     */
    static void renderHud(GuiGraphics g, DeltaTracker delta) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.options.hideGui) return;
        Font font = mc.font;
        int w = g.guiWidth(), h = g.guiHeight();
        boolean blink = Ui.blink();

        AircraftEntity aircraft = feed();
        if (aircraft != null) {
            AircraftType type = AircraftType.values()[Mth.clamp(feedType, 0, AircraftType.values().length - 1)];
            noise(g, w, h, aircraft.jamPercent());
            frame(g, w, h, 0x907CD67C);
            Ui.text(g, font, Ui.upper(Component.translatable(type.translationKey())).getString(), 22, 22, Ui.HUD_TEXT, 0.9f);
            Ui.text(g, font, "● LIVE", 22, 32, blink ? Ui.RED : 0xFFB04040, 1);
            float a = FEED_INFO.update(feedType);
            Ui.text(g, font, Component.translatable("hud.steelrain.position", Mth.floor(aircraft.getX()), Mth.floor(aircraft.getZ())).getString(), 22, h - 30, Ui.HUD_DIM, Math.max(a, 0.6f));
            flight(g, font, w, (int) Math.round(aircraft.getY()), -1, -1);
            jammedWarning(g, font, w, h, aircraft.jamPercent());
            return;
        }

        FpvDroneEntity d = drone();
        if (d == null || !attached) return;
        noise(g, w, h, d.jamPercent());
        frame(g, w, h, 0x907CD67C);
        Ui.text(g, font, d.mavic() ? "MAVIC" : d.fiber() ? "FPV  FIBRE" : "FPV", 22, 22, Ui.HUD_TEXT, 0.9f);
        Ui.text(g, font, "● REC", 22, 32, blink ? Ui.RED : 0xFFB04040, 1);

        double battery = Mth.clamp(d.battery() / (double) maxBattery, 0, 1);
        int y = h - 30;
        Ui.text(g, font, "BAT  " + Math.round(battery * 100) + "%", 22, y, battery < 0.2 ? (blink ? Ui.RED : 0xFFB04040) : battery < 0.5 ? Ui.AMBER : Ui.HUD_TEXT, 0.9f);
        y -= 10;
        if (d.fiber()) {
            int left = Math.max(0, d.cableLeft());
            Ui.text(g, font, "CABLE  " + left + " m", 22, y, left < 30 ? (blink ? Ui.RED : 0xFFB04040) : Ui.HUD_TEXT, 0.9f);
        } else {
            Ui.text(g, font, "SIG  " + d.signal() + "%", 22, y, d.signal() < 25 ? (blink ? Ui.RED : 0xFFB04040) : d.signal() < 50 ? Ui.AMBER : Ui.HUD_TEXT, 0.9f);
        }
        if (d.mavic()) {
            y -= 10;
            Ui.text(g, font, Component.translatable(d.hasGrenade() ? "hud.steelrain.grenade_ready" : "hud.steelrain.grenade_none").getString(), 22, y,
                    d.hasGrenade() ? Ui.AMBER : Ui.HUD_DIM, d.hasGrenade() ? 0.9f : 0.5f);
        }
        flight(g, font, w, (int) Math.round(d.getY()), speed, d.distance());
        jammedWarning(g, font, w, h, d.jamPercent());
    }

    /** Altitude, speed, distance home and zoom (when zoomed), top right. */
    private static void flight(GuiGraphics g, Font font, int w, int altitude, double spd, int dist) {
        int right = w - 22, y = 22;
        Ui.textRight(g, font, "ALT  " + altitude + " m", right, y, Ui.HUD_TEXT, 0.9f); y += 10;
        if (spd >= 0) { Ui.textRight(g, font, String.format("SPD  %.0f m/s", spd), right, y, Ui.HUD_TEXT, 0.9f); y += 10; }
        if (dist >= 0) { Ui.textRight(g, font, "HOME  " + dist + " m", right, y, Ui.HUD_DIM, 0.9f); y += 10; }
        if (zoom > 1.01f) Ui.textRight(g, font, String.format("%.1fx", zoom), right, y, Ui.AMBER, 0.9f);
    }

    private static void jammedWarning(GuiGraphics g, Font font, int w, int h, int jam) {
        if (jam <= 30) return;
        Ui.textCentre(g, font, Component.translatable("hud.steelrain.jammed").getString(), w / 2, h / 2 - 40, Ui.RED, Ui.blink() ? 1 : 0.55f);
    }

    private FpvClient() {}
}

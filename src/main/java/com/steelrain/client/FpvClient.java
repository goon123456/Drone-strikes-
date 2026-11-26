package com.steelrain.client;

import com.steelrain.ModRegistry;
import com.steelrain.entity.AircraftEntity;
import com.steelrain.entity.FpvDroneEntity;
import com.steelrain.entity.SeatEntity;
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
    private static boolean gunFiring;
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

        // Gunner's trigger: tell the server while the attack key is held on a manned gun.
        boolean onGun = mc.player.getVehicle() instanceof SeatEntity;
        boolean trigger = onGun && mc.screen == null && mc.options.keyAttack.isDown();
        if (trigger != gunFiring) {
            gunFiring = trigger;
            PacketDistributor.sendToServer(new Payloads.GunFire(trigger));
        }

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
        Minecraft mc = Minecraft.getInstance();
        boolean onGun = mc.player != null && mc.player.getVehicle() instanceof SeatEntity;
        if (droneId < 0 && feed() == null && !(onGun && event.isAttack())) return;
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

    static void renderHud(GuiGraphics g, DeltaTracker delta) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.options.hideGui) return;
        Font font = mc.font;
        int w = g.guiWidth(), h = g.guiHeight();
        int green = 0xFF6CFF6C, red = 0xFFFF5555;
        boolean blink = (mc.player.tickCount / 5) % 2 == 0;
        Component zoomText = Component.translatable("hud.steelrain.zoom", String.format("%.1f", zoom));

        AircraftEntity aircraft = feed();
        if (aircraft != null) {
            AircraftType type = AircraftType.values()[Mth.clamp(feedType, 0, AircraftType.values().length - 1)];
            noise(g, w, h, aircraft.jamPercent());
            frame(g, w, h, green);
            if (aircraft.jamPercent() > 30) {
                Component jammed = Component.translatable("hud.steelrain.jammed");
                g.drawString(font, jammed, w / 2 - font.width(jammed) / 2, h / 2 - 30, blink ? red : 0xFFFFFFFF, true);
            }
            g.drawString(font, Component.translatable(type.translationKey()).append("  ● LIVE"), 18, 18, blink ? red : green, true);
            g.drawString(font, Component.translatable("hud.steelrain.position",
                    Mth.floor(aircraft.getX()), Mth.floor(aircraft.getZ())), 18, 30, green, true);
            right(g, font, Component.translatable("hud.steelrain.altitude", (int) Math.round(aircraft.getY())), w, 18, green);
            right(g, font, zoomText, w, 30, green);
            Component hint = Component.translatable("hud.steelrain.feed_hint", ClientSetup.EXIT_DRONE.getTranslatedKeyMessage());
            g.drawString(font, hint, w / 2 - font.width(hint) / 2, h - 26, 0xFFCCFFCC, true);
            return;
        }

        FpvDroneEntity d = drone();
        if (d == null || !attached) return;
        noise(g, w, h, d.jamPercent());
        frame(g, w, h, green);
        int battery = Mth.clamp(d.battery() * 100 / maxBattery, 0, 100);
        int dist = d.distance();
        int signal = d.signal();

        g.drawString(font, (d.mavic() ? "MAVIC" : d.fiber() ? "FPV FIBER" : "FPV") + "  \u25CF REC", 18, 18, blink ? red : green, true);
        g.drawString(font, Component.translatable("hud.steelrain.battery", battery), 18, 30,
                battery < 20 ? (blink ? red : 0xFFFFFFFF) : green, true);
        if (d.fiber()) {
            int left = Math.max(0, d.cableLeft());
            g.drawString(font, Component.translatable("hud.steelrain.cable", left), 18, 42,
                    left < 30 ? (blink ? red : 0xFFFFFFFF) : 0xFF40C8FF, true);
        } else {
            g.drawString(font, Component.translatable("hud.steelrain.signal", signal), 18, 42,
                    signal < 25 ? (blink ? red : 0xFFFFFFFF) : green, true);
            if (d.jamPercent() > 30) {
                Component jammed = Component.translatable("hud.steelrain.jammed");
                g.drawString(font, jammed, w / 2 - font.width(jammed) / 2, h / 2 - 30, blink ? red : 0xFFFFFFFF, true);
            }
        }
        if (d.mavic()) {
            g.drawString(font, Component.translatable(d.hasGrenade() ? "hud.steelrain.grenade_ready" : "hud.steelrain.grenade_none"),
                    18, 54, d.hasGrenade() ? 0xFFFFD040 : 0xFF88AA88, true);
        }
        right(g, font, Component.translatable("hud.steelrain.altitude", (int) Math.round(d.getY())), w, 18, green);
        right(g, font, Component.translatable("hud.steelrain.speed", String.format("%.1f", speed)), w, 30, green);
        right(g, font, Component.translatable("hud.steelrain.distance", dist), w, 42, green);
        right(g, font, zoomText, w, 54, green);

        Component hint = Component.translatable(d.mavic() ? "hud.steelrain.hint_mavic" : "hud.steelrain.hint",
                ClientSetup.EXIT_DRONE.getTranslatedKeyMessage());
        g.drawString(font, hint, w / 2 - font.width(hint) / 2, h - 26, 0xFFCCFFCC, true);
    }

    private FpvClient() {}
}

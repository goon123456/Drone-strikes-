package com.steelrain.client;

import com.steelrain.ModRegistry;
import com.steelrain.entity.FpvDroneEntity;
import com.steelrain.net.Payloads;
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
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.InputEvent;
import net.neoforged.neoforge.client.event.MovementInputUpdateEvent;
import net.neoforged.neoforge.client.event.RenderHandEvent;
import net.neoforged.neoforge.client.event.ViewportEvent;
import net.neoforged.neoforge.network.PacketDistributor;

/** Client half of FPV piloting: camera switch, input capture and the goggles HUD. */
@EventBusSubscriber(modid = ModRegistry.MODID, value = Dist.CLIENT)
public final class FpvClient {
    private static int droneId = -1;
    private static int range = 200;
    private static int maxBattery = 3000;
    private static int waitTicks;
    private static boolean attached;
    private static float forward, strafe, vertical;
    private static boolean detonate;
    private static float roll;
    private static double speed;

    public static boolean piloting() { return droneId >= 0; }

    static void start(int id, int controlRange, int battery) {
        droneId = id;
        range = Math.max(1, controlRange);
        maxBattery = Math.max(1, battery);
        waitTicks = 60;
        attached = false;
        detonate = false;
    }

    static void stopIf(int id) {
        if (droneId == id) stop();
    }

    private static void stop() {
        droneId = -1;
        attached = false;
        Minecraft mc = Minecraft.getInstance();
        if (mc.player != null && mc.getCameraEntity() != mc.player) mc.setCameraEntity(mc.player);
    }

    private static FpvDroneEntity drone() {
        Minecraft mc = Minecraft.getInstance();
        if (droneId < 0 || mc.level == null) return null;
        Entity e = mc.level.getEntity(droneId);
        return e instanceof FpvDroneEntity d && !d.isRemoved() ? d : null;
    }

    @SubscribeEvent
    public static void tick(ClientTickEvent.Post event) {
        if (droneId < 0) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.level == null) {
            droneId = -1;
            return;
        }
        FpvDroneEntity d = drone();
        if (d == null) {
            // Either it has not been sent to us yet, or it blew up.
            if (attached || --waitTicks < 0) stop();
            return;
        }
        if (mc.getCameraEntity() != d) mc.setCameraEntity(d);
        attached = true;
        speed = Math.sqrt(Mth.square(d.getX() - d.xo) + Mth.square(d.getY() - d.yo) + Mth.square(d.getZ() - d.zo)) * 20;
        PacketDistributor.sendToServer(new Payloads.Control(droneId, forward, strafe, vertical,
                mc.player.getYRot(), mc.player.getXRot(), detonate));
        detonate = false;
        boolean exit = false;
        while (ClientSetup.EXIT_DRONE.consumeClick()) exit = true;
        if (exit) {
            PacketDistributor.sendToServer(new Payloads.StopPilot(droneId));
            stop();
        }
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
        if (droneId < 0) return;
        if (event.isAttack()) detonate = true;
        event.setSwingHand(false);
        event.setCanceled(true);
    }

    @SubscribeEvent
    public static void hand(RenderHandEvent event) {
        if (droneId >= 0 && attached) event.setCanceled(true);
    }

    @SubscribeEvent
    public static void camera(ViewportEvent.ComputeCameraAngles event) {
        Minecraft mc = Minecraft.getInstance();
        if (droneId < 0 || !attached || mc.player == null || event.getCamera().isDetached()) return;
        float pt = (float) event.getPartialTick();
        // Use the local look direction directly so the view reacts without network delay.
        event.setYaw(mc.player.getViewYRot(pt));
        event.setPitch(mc.player.getViewXRot(pt));
        roll += (-strafe * 9f - roll) * 0.08f;
        event.setRoll(roll);
    }

    static void renderHud(GuiGraphics g, DeltaTracker delta) {
        FpvDroneEntity d = drone();
        Minecraft mc = Minecraft.getInstance();
        if (d == null || !attached || mc.player == null || mc.options.hideGui) return;
        Font font = mc.font;
        int w = g.guiWidth(), h = g.guiHeight();
        int green = 0xFF6CFF6C, red = 0xFFFF5555;

        // Frame corners and reticle.
        int m = 12, len = 18;
        g.fill(m, m, m + len, m + 1, green); g.fill(m, m, m + 1, m + len, green);
        g.fill(w - m - len, m, w - m, m + 1, green); g.fill(w - m - 1, m, w - m, m + len, green);
        g.fill(m, h - m - 1, m + len, h - m, green); g.fill(m, h - m - len, m + 1, h - m, green);
        g.fill(w - m - len, h - m - 1, w - m, h - m, green); g.fill(w - m - 1, h - m - len, w - m, h - m, green);
        int cx = w / 2, cy = h / 2;
        g.fill(cx - 12, cy, cx - 4, cy + 1, green); g.fill(cx + 5, cy, cx + 13, cy + 1, green);
        g.fill(cx, cy - 12, cx + 1, cy - 4, green); g.fill(cx, cy + 5, cx + 1, cy + 13, green);

        int battery = Mth.clamp(d.battery() * 100 / maxBattery, 0, 100);
        double dist = Math.sqrt(d.distanceToSqr(mc.player));
        int signal = Mth.clamp((int) Math.round(100 * (1 - dist / range)), 0, 100);
        boolean blink = (mc.player.tickCount / 5) % 2 == 0;

        g.drawString(font, "FPV  ● REC", m + 6, m + 6, blink ? red : green, true);
        g.drawString(font, Component.translatable("hud.steelrain.battery", battery), m + 6, m + 18,
                battery < 20 ? (blink ? red : 0xFFFFFFFF) : green, true);
        g.drawString(font, Component.translatable("hud.steelrain.signal", signal), m + 6, m + 30,
                signal < 20 ? (blink ? red : 0xFFFFFFFF) : green, true);

        Component alt = Component.translatable("hud.steelrain.altitude", (int) Math.round(d.getY()));
        Component spd = Component.translatable("hud.steelrain.speed", String.format("%.1f", speed));
        Component dst = Component.translatable("hud.steelrain.distance", (int) Math.round(dist));
        g.drawString(font, alt, w - m - 6 - font.width(alt), m + 6, green, true);
        g.drawString(font, spd, w - m - 6 - font.width(spd), m + 18, green, true);
        g.drawString(font, dst, w - m - 6 - font.width(dst), m + 30, green, true);

        Component hint = Component.translatable("hud.steelrain.hint", ClientSetup.EXIT_DRONE.getTranslatedKeyMessage());
        g.drawString(font, hint, cx - font.width(hint) / 2, h - m - 14, 0xFFCCFFCC, true);
    }

    private FpvClient() {}
}

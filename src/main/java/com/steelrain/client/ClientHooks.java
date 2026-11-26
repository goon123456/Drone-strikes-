package com.steelrain.client;

import com.steelrain.net.Payloads;
import net.minecraft.client.Minecraft;

/** Entry points for server -> client packets. Only ever touched on a physical client. */
public final class ClientHooks {
    public static void onPilot(Payloads.Pilot p) {
        if (p.start()) FpvClient.start(p.entityId(), p.maxBattery());
        else FpvClient.stopIf(p.entityId());
    }

    public static void onLaptopData(Payloads.LaptopData p) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.screen instanceof LaptopScreen screen) {
            screen.update(p);
        } else if (p.open()) {
            mc.setScreen(new LaptopScreen(p));
        }
    }

    public static void onMapTile(Payloads.MapTile p) { ReconClient.tile(p); }

    public static void onMarkers(Payloads.Markers p) { ReconClient.markers(p); }

    public static void onFeed(Payloads.Feed p) {
        Minecraft mc = Minecraft.getInstance();
        if (p.start()) {
            if (mc.screen instanceof LaptopScreen) mc.setScreen(null);
            FpvClient.startFeed(p.entityId(), p.aircraft());
        } else {
            FpvClient.stopFeed();
        }
    }

    public static void onCable(Payloads.Cable p) { CableClient.update(p); }

    public static void startLoop(com.steelrain.entity.FlyingWeaponEntity entity) { LoopSound.start(entity); }

    /** How badly the video link being watched is jammed right now, 0 to 1. Drives the static hiss. */
    public static float jam;
    private static boolean staticPlaying;

    /** Starts the three static layers if they are not already running; they fade themselves by {@link #jam}. */
    public static void staticNoise(float level) {
        jam = level;
        if (level > 0.08f && !staticPlaying) {
            staticPlaying = true;
            for (int i = 0; i < 3; i++) Minecraft.getInstance().getSoundManager().play(new LoopSound.Static(i));
        }
    }

    static void staticStopped() { staticPlaying = false; }

    private ClientHooks() {}
}

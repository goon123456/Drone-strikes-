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

    private ClientHooks() {}
}

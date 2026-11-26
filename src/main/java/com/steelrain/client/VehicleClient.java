package com.steelrain.client;

import com.steelrain.ModRegistry;
import com.steelrain.entity.vehicle.SrVehicle;
import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.sounds.AbstractTickableSoundInstance;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.CalculateDetachedCameraDistanceEvent;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.RenderPlayerEvent;

/** The driver's keys, the camera pulled back to see the whole vehicle, the crew hidden inside, engine noise. */
@EventBusSubscriber(modid = ModRegistry.MODID, value = Dist.CLIENT)
public final class VehicleClient {
    @SubscribeEvent
    public static void tick(ClientTickEvent.Post e) {
        var player = Minecraft.getInstance().player;
        if (player != null && player.getVehicle() instanceof SrVehicle v && v.getControllingPassenger() == player) {
            v.setInput(player.input.forwardImpulse, player.input.leftImpulse, player.input.jumping);
        }
    }

    @SubscribeEvent
    public static void camera(CalculateDetachedCameraDistanceEvent e) {
        if (e.getCamera().getEntity().getVehicle() instanceof SrVehicle) e.setDistance(Math.max(e.getDistance(), 10f));
    }

    @SubscribeEvent
    public static void hideCrew(RenderPlayerEvent.Pre e) {
        if (e.getEntity().getVehicle() instanceof SrVehicle v && v.hidesSeat(v.seatOf(e.getEntity()))) e.setCanceled(true);
    }

    static void startSound(SrVehicle v) {
        SoundEvent sound = BuiltInRegistries.SOUND_EVENT.get(ResourceLocation.fromNamespaceAndPath("superbwarfare", v.tracked() ? "plz_05_engine" : "truck_engine"));
        if (sound != null) Minecraft.getInstance().getSoundManager().play(new Engine(v, sound));
    }

    /** Diesel rumble while someone drives, rising in pitch with speed. */
    private static final class Engine extends AbstractTickableSoundInstance {
        private final SrVehicle v;

        Engine(SrVehicle v, SoundEvent sound) {
            super(sound, SoundSource.NEUTRAL, SoundInstance.createUnseededRandom());
            this.v = v;
            this.looping = true;
            this.delay = 0;
            this.volume = 0;
            this.x = v.getX(); this.y = v.getY(); this.z = v.getZ();
        }

        @Override
        public void tick() {
            if (v.isRemoved()) { stop(); return; }
            x = v.getX(); y = v.getY() + 1; z = v.getZ();
            double speed = Math.sqrt(Mth.square(v.getX() - v.xo) + Mth.square(v.getZ() - v.zo));
            float want = v.engineRunning() ? 1.2f : 0f;
            volume += (want - volume) * 0.1f;
            pitch = 0.75f + (float) Math.min(0.6, speed * 0.9);
        }

        @Override public boolean canStartSilent() { return true; }
    }

    private VehicleClient() {}
}

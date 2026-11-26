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

import javax.annotation.Nullable;

/** The driver's keys, the camera pulled back to see the whole vehicle, the crew hidden inside, engine noise. */
@EventBusSubscriber(modid = ModRegistry.MODID, value = Dist.CLIENT)
public final class VehicleClient {
    @SubscribeEvent
    public static void tick(ClientTickEvent.Post e) {
        var player = Minecraft.getInstance().player;
        if (player == null || !(player.getVehicle() instanceof SrVehicle v)) return;
        if (v.getControllingPassenger() == player) {
            v.setInput(player.input.forwardImpulse, player.input.leftImpulse, player.input.jumping);
        }
        while (ClientSetup.SWITCH_SEAT.consumeClick()) send(v, SrVehicle.ACTION_SWITCH_SEAT);
        while (ClientSetup.SHELL_TYPE.consumeClick()) {
            if (v instanceof com.steelrain.entity.vehicle.SrGun g && g.isGunner(player)) send(v, com.steelrain.entity.vehicle.SrGun.ACTION_NEXT_SHELL);
        }
    }

    private static void send(SrVehicle v, int action) {
        net.neoforged.neoforge.network.PacketDistributor.sendToServer(new com.steelrain.net.Payloads.VehicleAction(v.getId(), action));
    }

    @Nullable
    private static com.steelrain.entity.vehicle.SrGun gunnerOf() {
        var player = Minecraft.getInstance().player;
        return player != null && player.getVehicle() instanceof com.steelrain.entity.vehicle.SrGun g && g.isGunner(player) ? g : null;
    }

    /** The gunner's left click fires the gun instead of swinging an arm. */
    @SubscribeEvent
    public static void click(net.neoforged.neoforge.client.event.InputEvent.InteractionKeyMappingTriggered e) {
        var g = gunnerOf();
        if (g == null || !e.isAttack()) return;
        e.setCanceled(true);
        e.setSwingHand(false);
        send(g, com.steelrain.entity.vehicle.SrGun.ACTION_FIRE);
    }

    /** Looking through the gun sight: no arm in front of it. */
    @SubscribeEvent
    public static void hand(net.neoforged.neoforge.client.event.RenderHandEvent e) {
        if (gunnerOf() != null) e.setCanceled(true);
    }

    /** The gunner's scroll wheel picks the propellant charge. */
    @SubscribeEvent
    public static void scroll(net.neoforged.neoforge.client.event.InputEvent.MouseScrollingEvent e) {
        var g = gunnerOf();
        if (g == null || e.getScrollDeltaY() == 0) return;
        e.setCanceled(true);
        send(g, e.getScrollDeltaY() > 0 ? com.steelrain.entity.vehicle.SrGun.ACTION_CHARGE_UP : com.steelrain.entity.vehicle.SrGun.ACTION_CHARGE_DOWN);
    }

    @SubscribeEvent
    public static void camera(CalculateDetachedCameraDistanceEvent e) {
        if (e.getCamera().getEntity().getVehicle() instanceof SrVehicle v) e.setDistance(Math.max(e.getDistance(), v.cameraDistance()));
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

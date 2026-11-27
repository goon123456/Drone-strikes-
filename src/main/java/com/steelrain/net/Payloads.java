package com.steelrain.net;

import com.steelrain.Config;
import com.steelrain.ModRegistry;
import com.steelrain.entity.AircraftEntity;
import com.steelrain.entity.FpvDroneEntity;
import com.steelrain.util.FeedManager;
import com.steelrain.weapon.Launcher;
import com.steelrain.item.LaptopItem;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.ComponentSerialization;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.neoforged.fml.loading.FMLEnvironment;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public final class Payloads {
    private static <T extends CustomPacketPayload> CustomPacketPayload.Type<T> id(String name) {
        return new CustomPacketPayload.Type<>(ResourceLocation.fromNamespaceAndPath(ModRegistry.MODID, name));
    }

    /** Server -> client: start or stop looking through a drone's camera. */
    public record Pilot(int entityId, boolean start, int maxBattery) implements CustomPacketPayload {
        public static final Type<Pilot> TYPE = id("pilot");
        public static final StreamCodec<FriendlyByteBuf, Pilot> CODEC = StreamCodec.ofMember(Pilot::write, Pilot::new);
        Pilot(FriendlyByteBuf b) { this(b.readVarInt(), b.readBoolean(), b.readVarInt()); }
        void write(FriendlyByteBuf b) { b.writeVarInt(entityId); b.writeBoolean(start); b.writeVarInt(maxBattery); }
        @Override public Type<Pilot> type() { return TYPE; }
    }

    /** Client -> server: stick inputs for the piloted drone, sent every tick. */
    public record Control(int entityId, float forward, float strafe, float vertical, float yaw, float pitch,
                          boolean detonate) implements CustomPacketPayload {
        public static final Type<Control> TYPE = id("control");
        public static final StreamCodec<FriendlyByteBuf, Control> CODEC = StreamCodec.ofMember(Control::write, Control::new);
        Control(FriendlyByteBuf b) {
            this(b.readVarInt(), b.readFloat(), b.readFloat(), b.readFloat(), b.readFloat(), b.readFloat(), b.readBoolean());
        }
        void write(FriendlyByteBuf b) {
            b.writeVarInt(entityId); b.writeFloat(forward); b.writeFloat(strafe); b.writeFloat(vertical);
            b.writeFloat(yaw); b.writeFloat(pitch); b.writeBoolean(detonate);
        }
        @Override public Type<Control> type() { return TYPE; }
    }

    /** Client -> server: the pilot let go of the drone. */
    public record StopPilot(int entityId) implements CustomPacketPayload {
        public static final Type<StopPilot> TYPE = id("stop_pilot");
        public static final StreamCodec<FriendlyByteBuf, StopPilot> CODEC = StreamCodec.ofMember(StopPilot::write, StopPilot::new);
        StopPilot(FriendlyByteBuf b) { this(b.readVarInt()); }
        void write(FriendlyByteBuf b) { b.writeVarInt(entityId); }
        @Override public Type<StopPilot> type() { return TYPE; }
    }

    /**
     * One linked launcher as shown on the laptop. {@code vehicle} is set for Superb Warfare vehicles (pos is then
     * where it currently stands). {@code name} is empty when it cannot be reached. {@code aircraft} is the
     * AircraftType ordinal of the drone a station has in the air (-1 if none), with its position in ax / az.
     */
    public record Entry(BlockPos pos, Optional<UUID> vehicle, String name, boolean station, boolean second, int ammo, int ammo2,
                        int fuel, int minRange, int maxRange, int busy, boolean reachable, int aircraft, int ax, int az, boolean manual) {
        Entry(FriendlyByteBuf b) {
            this(b.readBlockPos(), b.readOptional(buf -> buf.readUUID()), b.readUtf(), b.readBoolean(), b.readBoolean(),
                    b.readVarInt(), b.readVarInt(), b.readVarInt(), b.readVarInt(), b.readVarInt(), b.readVarInt(),
                    b.readBoolean(), b.readVarInt(), b.readInt(), b.readInt(), b.readBoolean());
        }
        void write(FriendlyByteBuf b) {
            b.writeBlockPos(pos); b.writeOptional(vehicle, (buf, u) -> buf.writeUUID(u)); b.writeUtf(name);
            b.writeBoolean(station); b.writeBoolean(second);
            b.writeVarInt(ammo); b.writeVarInt(ammo2); b.writeVarInt(fuel);
            b.writeVarInt(minRange); b.writeVarInt(maxRange); b.writeVarInt(busy); b.writeBoolean(reachable);
            b.writeVarInt(aircraft); b.writeInt(ax); b.writeInt(az); b.writeBoolean(manual);
        }
    }

    /** Server -> client: terrain colours of one chunk seen by a recon drone (vanilla packed map colours). */
    public record MapTile(int cx, int cz, byte[] colours) implements CustomPacketPayload {
        public static final Type<MapTile> TYPE = id("map_tile");
        public static final StreamCodec<FriendlyByteBuf, MapTile> CODEC = StreamCodec.ofMember(MapTile::write, MapTile::new);
        MapTile(FriendlyByteBuf b) { this(b.readInt(), b.readInt(), b.readByteArray(256)); }
        void write(FriendlyByteBuf b) { b.writeInt(cx); b.writeInt(cz); b.writeByteArray(colours); }
        @Override public Type<MapTile> type() { return TYPE; }
    }

    /** Server -> client: where a recon drone is, how far it sees, and what it sees as x, z, kind triples. */
    public record Markers(int droneId, int x, int z, int radius, int[] data) implements CustomPacketPayload {
        public static final Type<Markers> TYPE = id("markers");
        public static final StreamCodec<FriendlyByteBuf, Markers> CODEC = StreamCodec.ofMember(Markers::write, Markers::new);
        Markers(FriendlyByteBuf b) { this(b.readVarInt(), b.readInt(), b.readInt(), b.readVarInt(), b.readVarIntArray(1024)); }
        void write(FriendlyByteBuf b) { b.writeVarInt(droneId); b.writeInt(x); b.writeInt(z); b.writeVarInt(radius); b.writeVarIntArray(data); }
        @Override public Type<Markers> type() { return TYPE; }
    }

    /** Server -> client: a remote camera feed started or ended. */
    public record Feed(int entityId, boolean start, int aircraft) implements CustomPacketPayload {
        public static final Type<Feed> TYPE = id("feed");
        public static final StreamCodec<FriendlyByteBuf, Feed> CODEC = StreamCodec.ofMember(Feed::write, Feed::new);
        Feed(FriendlyByteBuf b) { this(b.readVarInt(), b.readBoolean(), b.readVarInt()); }
        void write(FriendlyByteBuf b) { b.writeVarInt(entityId); b.writeBoolean(start); b.writeVarInt(aircraft); }
        @Override public Type<Feed> type() { return TYPE; }
    }

    /** Server -> client: the path of a fibre-optic cable (empty = gone). The drone itself is the last point. */
    public record Cable(int droneId, List<net.minecraft.world.phys.Vec3> points) implements CustomPacketPayload {
        public static final Type<Cable> TYPE = id("cable");
        public static final StreamCodec<FriendlyByteBuf, Cable> CODEC = StreamCodec.ofMember(Cable::write, Cable::new);
        Cable(FriendlyByteBuf b) { this(b.readVarInt(), b.readList(FriendlyByteBuf::readVec3)); }
        void write(FriendlyByteBuf b) { b.writeVarInt(droneId); b.writeCollection(points, FriendlyByteBuf::writeVec3); }
        @Override public Type<Cable> type() { return TYPE; }
    }

    /** Server -> client: this vehicle has a Jammer Dome fitted, so draw one on its roof. */
    public record Dome(int entityId) implements CustomPacketPayload {
        public static final Type<Dome> TYPE = id("dome");
        public static final StreamCodec<FriendlyByteBuf, Dome> CODEC = StreamCodec.ofMember(Dome::write, Dome::new);
        Dome(FriendlyByteBuf b) { this(b.readVarInt()); }
        void write(FriendlyByteBuf b) { b.writeVarInt(entityId); }
        @Override public Type<Dome> type() { return TYPE; }
    }

    /** Client -> server: a rider of a Steel Rain vehicle pressed a key (change seat, fire, charge, shell type). */
    public record VehicleAction(int entityId, int action) implements CustomPacketPayload {
        public static final Type<VehicleAction> TYPE = id("vehicle_action");
        public static final StreamCodec<FriendlyByteBuf, VehicleAction> CODEC = StreamCodec.ofMember(VehicleAction::write, VehicleAction::new);
        VehicleAction(FriendlyByteBuf b) { this(b.readVarInt(), b.readVarInt()); }
        void write(FriendlyByteBuf b) { b.writeVarInt(entityId); b.writeVarInt(action); }
        @Override public Type<VehicleAction> type() { return TYPE; }
    }

    /** Client -> server: leave the remote camera feed. */
    public record StopFeed() implements CustomPacketPayload {
        public static final Type<StopFeed> TYPE = id("stop_feed");
        public static final StreamCodec<FriendlyByteBuf, StopFeed> CODEC = StreamCodec.unit(new StopFeed());
        @Override public Type<StopFeed> type() { return TYPE; }
    }

    /** Client -> server: laptop buttons for a station's airborne aircraft. */
    public record AircraftOrder(BlockPos pos, Optional<UUID> vehicle, int action) implements CustomPacketPayload {
        public static final int CAMERA = 0, RECALL = 1;
        public static final Type<AircraftOrder> TYPE = id("aircraft_order");
        public static final StreamCodec<FriendlyByteBuf, AircraftOrder> CODEC = StreamCodec.ofMember(AircraftOrder::write, AircraftOrder::new);
        AircraftOrder(FriendlyByteBuf b) { this(b.readBlockPos(), b.readOptional(buf -> buf.readUUID()), b.readVarInt()); }
        void write(FriendlyByteBuf b) { b.writeBlockPos(pos); b.writeOptional(vehicle, (buf, u) -> buf.writeUUID(u)); b.writeVarInt(action); }
        @Override public Type<AircraftOrder> type() { return TYPE; }
    }

    /** Server -> client: state of every linked launcher; {@code open} also opens the screen. */
    public record LaptopData(boolean open, List<Entry> entries, Optional<Component> message) implements CustomPacketPayload {
        public static final Type<LaptopData> TYPE = id("laptop_data");
        public static final StreamCodec<RegistryFriendlyByteBuf, LaptopData> CODEC = StreamCodec.ofMember(LaptopData::write, LaptopData::new);
        LaptopData(RegistryFriendlyByteBuf b) {
            this(b.readBoolean(), b.readList(Entry::new), ComponentSerialization.OPTIONAL_STREAM_CODEC.decode(b));
        }
        void write(RegistryFriendlyByteBuf b) {
            b.writeBoolean(open);
            b.writeCollection(entries, (buf, e) -> e.write(buf));
            ComponentSerialization.OPTIONAL_STREAM_CODEC.encode(b, message);
        }
        @Override public Type<LaptopData> type() { return TYPE; }
    }

    /** Client -> server: please send fresh launcher data. */
    public record LaptopRefresh() implements CustomPacketPayload {
        public static final Type<LaptopRefresh> TYPE = id("laptop_refresh");
        public static final StreamCodec<FriendlyByteBuf, LaptopRefresh> CODEC = StreamCodec.unit(new LaptopRefresh());
        @Override public Type<LaptopRefresh> type() { return TYPE; }
    }

    /** Client -> server: fire a linked launcher at a column. */
    public record Launch(BlockPos pos, Optional<UUID> vehicle, int x, int z, int count) implements CustomPacketPayload {
        public static final Type<Launch> TYPE = id("launch");
        public static final StreamCodec<FriendlyByteBuf, Launch> CODEC = StreamCodec.ofMember(Launch::write, Launch::new);
        Launch(FriendlyByteBuf b) { this(b.readBlockPos(), b.readOptional(buf -> buf.readUUID()), b.readInt(), b.readInt(), b.readVarInt()); }
        void write(FriendlyByteBuf b) { b.writeBlockPos(pos); b.writeOptional(vehicle, (buf, u) -> buf.writeUUID(u)); b.writeInt(x); b.writeInt(z); b.writeVarInt(count); }
        @Override public Type<Launch> type() { return TYPE; }
    }

    /** Client -> server: what the fingers are doing on a held gun (trigger pulled, sights up). */
    public record GunInput(boolean trigger, boolean aiming) implements CustomPacketPayload {
        public static final Type<GunInput> TYPE = id("gun_input");
        public static final StreamCodec<FriendlyByteBuf, GunInput> CODEC = StreamCodec.ofMember(GunInput::write, GunInput::new);
        GunInput(FriendlyByteBuf b) { this(b.readBoolean(), b.readBoolean()); }
        void write(FriendlyByteBuf b) { b.writeBoolean(trigger); b.writeBoolean(aiming); }
        @Override public Type<GunInput> type() { return TYPE; }
    }

    /** Client -> server: reload, move the selector, change the zero, fit or remove an attachment. */
    public record GunAction(int action, int arg) implements CustomPacketPayload {
        public static final Type<GunAction> TYPE = id("gun_action");
        public static final StreamCodec<FriendlyByteBuf, GunAction> CODEC = StreamCodec.ofMember(GunAction::write, GunAction::new);
        GunAction(FriendlyByteBuf b) { this(b.readVarInt(), b.readInt()); }
        void write(FriendlyByteBuf b) { b.writeVarInt(action); b.writeInt(arg); }
        @Override public Type<GunAction> type() { return TYPE; }
    }

    /** Server -> client: a bullet left a gun. Clients fly their own copy for the tracer, flash, sound and recoil. */
    public record GunShot(int shooter, int gun, int flags, net.minecraft.world.phys.Vec3 from, net.minecraft.world.phys.Vec3 velocity) implements CustomPacketPayload {
        public static final int PRIMARY = 1, TRACER = 2, SUPPRESSED = 4;
        /** The round's calibre sits above the flag bits. */
        public static final int CALIBER_SHIFT = 4;
        public static final Type<GunShot> TYPE = id("gun_shot");
        public static final StreamCodec<FriendlyByteBuf, GunShot> CODEC = StreamCodec.ofMember(GunShot::write, GunShot::new);
        GunShot(FriendlyByteBuf b) { this(b.readVarInt(), b.readVarInt(), b.readVarInt(), b.readVec3(), b.readVec3()); }
        void write(FriendlyByteBuf b) { b.writeVarInt(shooter); b.writeVarInt(gun); b.writeVarInt(flags); b.writeVec3(from); b.writeVec3(velocity); }
        @Override public Type<GunShot> type() { return TYPE; }
    }

    /** Server -> client: someone started a reload, worked a pump, moved a selector or pulled a dead trigger. */
    public record GunEvent(int entityId, int kind, int arg) implements CustomPacketPayload {
        public static final Type<GunEvent> TYPE = id("gun_event");
        public static final StreamCodec<FriendlyByteBuf, GunEvent> CODEC = StreamCodec.ofMember(GunEvent::write, GunEvent::new);
        GunEvent(FriendlyByteBuf b) { this(b.readVarInt(), b.readVarInt(), b.readVarInt()); }
        void write(FriendlyByteBuf b) { b.writeVarInt(entityId); b.writeVarInt(kind); b.writeVarInt(arg); }
        @Override public Type<GunEvent> type() { return TYPE; }
    }

    /** Server -> client: one of your bullets hit somebody. */
    public record GunHit(boolean head, boolean kill) implements CustomPacketPayload {
        public static final Type<GunHit> TYPE = id("gun_hit");
        public static final StreamCodec<FriendlyByteBuf, GunHit> CODEC = StreamCodec.ofMember(GunHit::write, GunHit::new);
        GunHit(FriendlyByteBuf b) { this(b.readBoolean(), b.readBoolean()); }
        void write(FriendlyByteBuf b) { b.writeBoolean(head); b.writeBoolean(kill); }
        @Override public Type<GunHit> type() { return TYPE; }
    }

    public static void register(RegisterPayloadHandlersEvent event) {
        PayloadRegistrar r = event.registrar("1");
        r.playToServer(GunInput.TYPE, GunInput.CODEC, (p, ctx) -> {
            if (ctx.player() instanceof ServerPlayer sp) com.steelrain.gun.GunLogic.input(sp, p.trigger(), p.aiming());
        });
        r.playToServer(GunAction.TYPE, GunAction.CODEC, (p, ctx) -> {
            if (ctx.player() instanceof ServerPlayer sp) com.steelrain.gun.GunLogic.action(sp, p.action(), p.arg());
        });
        r.playToClient(GunShot.TYPE, GunShot.CODEC, (p, ctx) -> {
            if (FMLEnvironment.dist.isClient()) com.steelrain.client.ClientHooks.onGunShot(p);
        });
        r.playToClient(GunEvent.TYPE, GunEvent.CODEC, (p, ctx) -> {
            if (FMLEnvironment.dist.isClient()) com.steelrain.client.ClientHooks.onGunEvent(p);
        });
        r.playToClient(GunHit.TYPE, GunHit.CODEC, (p, ctx) -> {
            if (FMLEnvironment.dist.isClient()) com.steelrain.client.ClientHooks.onGunHit(p);
        });
        r.playToClient(Pilot.TYPE, Pilot.CODEC, (p, ctx) -> {
            if (FMLEnvironment.dist.isClient()) com.steelrain.client.ClientHooks.onPilot(p);
        });
        r.playToClient(LaptopData.TYPE, LaptopData.CODEC, (p, ctx) -> {
            if (FMLEnvironment.dist.isClient()) com.steelrain.client.ClientHooks.onLaptopData(p);
        });
        r.playToClient(MapTile.TYPE, MapTile.CODEC, (p, ctx) -> {
            if (FMLEnvironment.dist.isClient()) com.steelrain.client.ClientHooks.onMapTile(p);
        });
        r.playToClient(Markers.TYPE, Markers.CODEC, (p, ctx) -> {
            if (FMLEnvironment.dist.isClient()) com.steelrain.client.ClientHooks.onMarkers(p);
        });
        r.playToClient(Feed.TYPE, Feed.CODEC, (p, ctx) -> {
            if (FMLEnvironment.dist.isClient()) com.steelrain.client.ClientHooks.onFeed(p);
        });
        r.playToClient(Cable.TYPE, Cable.CODEC, (p, ctx) -> {
            if (FMLEnvironment.dist.isClient()) com.steelrain.client.ClientHooks.onCable(p);
        });
        r.playToClient(Dome.TYPE, Dome.CODEC, (p, ctx) -> {
            if (FMLEnvironment.dist.isClient()) com.steelrain.client.ClientHooks.onDome(p);
        });
        r.playToServer(StopFeed.TYPE, StopFeed.CODEC, (p, ctx) -> {
            if (ctx.player() instanceof ServerPlayer sp) FeedManager.stop(sp);
        });
        r.playToServer(AircraftOrder.TYPE, AircraftOrder.CODEC, Payloads::onAircraftOrder);
        r.playToServer(Control.TYPE, Control.CODEC, Payloads::onControl);
        r.playToServer(StopPilot.TYPE, StopPilot.CODEC, Payloads::onStopPilot);
        r.playToServer(LaptopRefresh.TYPE, LaptopRefresh.CODEC, (p, ctx) -> {
            if (ctx.player() instanceof ServerPlayer sp) sendLaptop(sp, false, null);
        });
        r.playToServer(Launch.TYPE, Launch.CODEC, Payloads::onLaunch);
        r.playToServer(VehicleAction.TYPE, VehicleAction.CODEC, (p, ctx) -> {
            if (ctx.player() instanceof ServerPlayer sp && sp.getVehicle() instanceof com.steelrain.entity.vehicle.SrVehicle v
                    && v.getId() == p.entityId()) v.action(sp, p.action());
        });
    }

    private static void onControl(Control p, IPayloadContext ctx) {
        if (ctx.player() instanceof ServerPlayer sp && sp.level().getEntity(p.entityId()) instanceof FpvDroneEntity drone) {
            drone.control(sp, p.forward(), p.strafe(), p.vertical(), p.yaw(), p.pitch(), p.detonate());
        }
    }

    private static void onStopPilot(StopPilot p, IPayloadContext ctx) {
        if (ctx.player() instanceof ServerPlayer sp && sp.level().getEntity(p.entityId()) instanceof FpvDroneEntity drone
                && drone.isPilot(sp)) {
            drone.releasePilot(null);
        }
    }

    /**
     * Finds the linked launcher an order is for and checks the laptop may command it.
     * @return the launcher, or a Component saying why not
     */
    private static Object resolve(ServerPlayer sp, BlockPos pos, Optional<UUID> vehicle) {
        ItemStack laptop = heldLaptop(sp);
        if (laptop.isEmpty()) return Component.translatable("message.steelrain.not_linked");
        if (jammed(sp)) return Component.translatable("message.steelrain.laptop_jammed");
        Launcher launcher = null;
        if (vehicle.isPresent()) {
            if (!LaptopItem.vehicles(laptop).contains(vehicle.get())) return Component.translatable("message.steelrain.not_linked");
            if (sp.serverLevel().getEntity(vehicle.get()) instanceof Launcher l) launcher = l;
        } else {
            if (!LaptopItem.links(laptop).contains(pos)) return Component.translatable("message.steelrain.not_linked");
            if (sp.level().isLoaded(pos) && sp.level().getBlockEntity(pos) instanceof Launcher l) launcher = l;
        }
        if (launcher == null || !inRange(sp, launcher.launcherPos())) return Component.translatable("message.steelrain.no_signal");
        return launcher;
    }

    private static void onLaunch(Launch p, IPayloadContext ctx) {
        if (!(ctx.player() instanceof ServerPlayer sp)) return;
        Object found = resolve(sp, p.pos(), p.vehicle());
        Component result;
        if (found instanceof Launcher launcher) {
            result = launcher.tryLaunch(sp, p.x(), p.z(), p.count());
            if (result == null) result = Component.translatable("message.steelrain.launched", p.x(), p.z());
        } else {
            result = (Component) found;
        }
        sendLaptop(sp, false, result);
    }

    private static void onAircraftOrder(AircraftOrder p, IPayloadContext ctx) {
        if (!(ctx.player() instanceof ServerPlayer sp)) return;
        Object found = resolve(sp, p.pos(), p.vehicle());
        Component result;
        if (found instanceof Launcher launcher) {
            AircraftEntity aircraft = launcher.airborne();
            if (aircraft == null) {
                result = Component.translatable("message.steelrain.not_airborne");
            } else if (p.action() == AircraftOrder.RECALL) {
                result = Component.translatable(aircraft.recall() ? "message.steelrain.recalled" : "message.steelrain.busy");
            } else {
                result = FeedManager.start(sp, aircraft);
                if (result == null) return; // the screen closes itself when the feed starts
            }
        } else {
            result = (Component) found;
        }
        sendLaptop(sp, false, result);
    }

    /** A laptop inside an enemy jamming bubble cannot reach its launchers. */
    private static boolean jammed(ServerPlayer player) {
        return com.steelrain.util.Jamming.level(player.serverLevel(), player.position(), player.getUUID(),
                player.getGameProfile().getName()) > 0.5f;
    }

    private static ItemStack heldLaptop(ServerPlayer player) {
        for (InteractionHand hand : InteractionHand.values()) {
            ItemStack s = player.getItemInHand(hand);
            if (s.is(ModRegistry.FIELD_LAPTOP.get())) return s;
        }
        return ItemStack.EMPTY;
    }

    private static boolean inRange(ServerPlayer player, net.minecraft.world.phys.Vec3 pos) {
        double range = Config.LAPTOP_LINK_RANGE.get();
        return player.position().distanceToSqr(pos) <= range * range;
    }

    public static void sendLaptop(ServerPlayer player, boolean open) {
        sendLaptop(player, open, null);
    }

    private static Entry entry(ServerPlayer player, BlockPos pos, Optional<UUID> vehicle, Launcher l) {
        AircraftEntity air = l.airborne();
        return new Entry(pos, vehicle, l.nameKey(), l.aircraftStation(), l.hasSecondAmmo(), l.ammoCount(), l.secondCount(),
                l.energyK(), l.minRange(), l.maxRangeNow(), l.busyTicks(), inRange(player, l.launcherPos()),
                air == null ? -1 : air.type().ordinal(),
                air == null ? 0 : air.blockPosition().getX(), air == null ? 0 : air.blockPosition().getZ(), l.manual());
    }

    public static void sendLaptop(ServerPlayer player, boolean open, @Nullable Component message) {
        ItemStack laptop = heldLaptop(player);
        List<Entry> entries = new ArrayList<>();
        if (!laptop.isEmpty()) {
            // Superb Warfare vehicles first: they move, so they are found by id.
            for (UUID id : LaptopItem.vehicles(laptop)) {
                if (player.serverLevel().getEntity(id) instanceof Launcher l && l instanceof net.minecraft.world.entity.Entity e && e.isAlive()) {
                    entries.add(entry(player, e.blockPosition(), Optional.of(id), l));
                } else {
                    entries.add(new Entry(BlockPos.ZERO, Optional.of(id), "", false, false, 0, 0, 0, 0, 0, 0, false, -1, 0, 0, false));
                }
            }
            List<BlockPos> links = LaptopItem.links(laptop);
            List<BlockPos> kept = new ArrayList<>(links);
            for (BlockPos pos : links) {
                boolean loaded = player.level().isLoaded(pos);
                if (loaded && player.level().getBlockEntity(pos) instanceof Launcher l) {
                    entries.add(entry(player, pos, Optional.empty(), l));
                } else if (loaded) {
                    kept.remove(pos); // the emplacement is gone: forget the link
                } else {
                    entries.add(new Entry(pos, Optional.empty(), "", false, false, 0, 0, 0, 0, 0, 0, false, -1, 0, 0, false));
                }
            }
            if (kept.size() != links.size()) laptop.set(ModRegistry.LINKS.get(), List.copyOf(kept));
        }
        PacketDistributor.sendToPlayer(player, new LaptopData(open, entries, Optional.ofNullable(message)));
    }

    private Payloads() {}
}

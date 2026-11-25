package com.steelrain.net;

import com.steelrain.Config;
import com.steelrain.ModRegistry;
import com.steelrain.block.WeaponBlockEntity;
import com.steelrain.entity.AircraftEntity;
import com.steelrain.entity.FpvDroneEntity;
import com.steelrain.util.FeedManager;
import com.steelrain.weapon.AircraftType;
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

public final class Payloads {
    private static <T extends CustomPacketPayload> CustomPacketPayload.Type<T> id(String name) {
        return new CustomPacketPayload.Type<>(ResourceLocation.fromNamespaceAndPath(ModRegistry.MODID, name));
    }

    /** Server -> client: start or stop looking through a drone's camera. */
    public record Pilot(int entityId, boolean start, int range, int maxBattery) implements CustomPacketPayload {
        public static final Type<Pilot> TYPE = id("pilot");
        public static final StreamCodec<FriendlyByteBuf, Pilot> CODEC = StreamCodec.ofMember(Pilot::write, Pilot::new);
        Pilot(FriendlyByteBuf b) { this(b.readVarInt(), b.readBoolean(), b.readVarInt(), b.readVarInt()); }
        void write(FriendlyByteBuf b) { b.writeVarInt(entityId); b.writeBoolean(start); b.writeVarInt(range); b.writeVarInt(maxBattery); }
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
     * One linked launcher as shown on the laptop. {@code aircraft} is the AircraftType ordinal of the drone this
     * station has in the air (-1 if none), with its position in ax / az.
     */
    public record Entry(BlockPos pos, int weapon, int ammo, int ammo2, int fuel, int minRange, int maxRange, int busy,
                        boolean reachable, int aircraft, int ax, int az) {
        Entry(FriendlyByteBuf b) {
            this(b.readBlockPos(), b.readVarInt(), b.readVarInt(), b.readVarInt(), b.readVarInt(), b.readVarInt(),
                    b.readVarInt(), b.readVarInt(), b.readBoolean(), b.readVarInt(), b.readInt(), b.readInt());
        }
        void write(FriendlyByteBuf b) {
            b.writeBlockPos(pos); b.writeVarInt(weapon); b.writeVarInt(ammo); b.writeVarInt(ammo2); b.writeVarInt(fuel);
            b.writeVarInt(minRange); b.writeVarInt(maxRange); b.writeVarInt(busy); b.writeBoolean(reachable);
            b.writeVarInt(aircraft); b.writeInt(ax); b.writeInt(az);
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

    /** Client -> server: leave the remote camera feed. */
    public record StopFeed() implements CustomPacketPayload {
        public static final Type<StopFeed> TYPE = id("stop_feed");
        public static final StreamCodec<FriendlyByteBuf, StopFeed> CODEC = StreamCodec.unit(new StopFeed());
        @Override public Type<StopFeed> type() { return TYPE; }
    }

    /** Client -> server: laptop buttons for a station's airborne aircraft. */
    public record AircraftOrder(BlockPos pos, int action) implements CustomPacketPayload {
        public static final int CAMERA = 0, RECALL = 1;
        public static final Type<AircraftOrder> TYPE = id("aircraft_order");
        public static final StreamCodec<FriendlyByteBuf, AircraftOrder> CODEC = StreamCodec.ofMember(AircraftOrder::write, AircraftOrder::new);
        AircraftOrder(FriendlyByteBuf b) { this(b.readBlockPos(), b.readVarInt()); }
        void write(FriendlyByteBuf b) { b.writeBlockPos(pos); b.writeVarInt(action); }
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
    public record Launch(BlockPos pos, int x, int z, int count) implements CustomPacketPayload {
        public static final Type<Launch> TYPE = id("launch");
        public static final StreamCodec<FriendlyByteBuf, Launch> CODEC = StreamCodec.ofMember(Launch::write, Launch::new);
        Launch(FriendlyByteBuf b) { this(b.readBlockPos(), b.readInt(), b.readInt(), b.readVarInt()); }
        void write(FriendlyByteBuf b) { b.writeBlockPos(pos); b.writeInt(x); b.writeInt(z); b.writeVarInt(count); }
        @Override public Type<Launch> type() { return TYPE; }
    }

    public static void register(RegisterPayloadHandlersEvent event) {
        PayloadRegistrar r = event.registrar("1");
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

    private static void onLaunch(Launch p, IPayloadContext ctx) {
        if (!(ctx.player() instanceof ServerPlayer sp)) return;
        ItemStack laptop = heldLaptop(sp);
        Component result;
        if (laptop.isEmpty() || !LaptopItem.links(laptop).contains(p.pos())) {
            result = Component.translatable("message.steelrain.not_linked");
        } else if (!reachable(sp, p.pos()) || !(sp.level().getBlockEntity(p.pos()) instanceof WeaponBlockEntity be)) {
            result = Component.translatable("message.steelrain.no_signal");
        } else {
            result = be.tryLaunch(sp, p.x(), p.z(), p.count());
            if (result == null) {
                result = Component.translatable("message.steelrain.launched", p.x(), p.z());
            }
        }
        sendLaptop(sp, false, result);
    }

    private static void onAircraftOrder(AircraftOrder p, IPayloadContext ctx) {
        if (!(ctx.player() instanceof ServerPlayer sp)) return;
        ItemStack laptop = heldLaptop(sp);
        Component result;
        if (laptop.isEmpty() || !LaptopItem.links(laptop).contains(p.pos())) {
            result = Component.translatable("message.steelrain.not_linked");
        } else if (!reachable(sp, p.pos()) || !(sp.level().getBlockEntity(p.pos()) instanceof WeaponBlockEntity be)) {
            result = Component.translatable("message.steelrain.no_signal");
        } else {
            AircraftEntity aircraft = be.airborne();
            if (aircraft == null) {
                result = Component.translatable("message.steelrain.not_airborne");
            } else if (p.action() == AircraftOrder.RECALL) {
                result = Component.translatable(aircraft.recall() ? "message.steelrain.recalled" : "message.steelrain.busy");
            } else {
                result = FeedManager.start(sp, aircraft);
                if (result == null) return; // the screen closes itself when the feed starts
            }
        }
        sendLaptop(sp, false, result);
    }

    private static ItemStack heldLaptop(ServerPlayer player) {
        for (InteractionHand hand : InteractionHand.values()) {
            ItemStack s = player.getItemInHand(hand);
            if (s.is(ModRegistry.FIELD_LAPTOP.get())) return s;
        }
        return ItemStack.EMPTY;
    }

    private static boolean reachable(ServerPlayer player, BlockPos pos) {
        double range = Config.LAPTOP_LINK_RANGE.get();
        return player.level().isLoaded(pos) && player.distanceToSqr(pos.getCenter()) <= range * range;
    }

    public static void sendLaptop(ServerPlayer player, boolean open) {
        sendLaptop(player, open, null);
    }

    public static void sendLaptop(ServerPlayer player, boolean open, @Nullable Component message) {
        ItemStack laptop = heldLaptop(player);
        List<Entry> entries = new ArrayList<>();
        if (!laptop.isEmpty()) {
            List<BlockPos> links = LaptopItem.links(laptop);
            List<BlockPos> kept = new ArrayList<>(links);
            for (BlockPos pos : links) {
                boolean loaded = player.level().isLoaded(pos);
                if (loaded && player.level().getBlockEntity(pos) instanceof WeaponBlockEntity be) {
                    AircraftEntity air = be.airborne();
                    int maxRange = be.type().maxRange();
                    if (be.type().launchesAircraft()) {
                        AircraftType parked = AircraftType.of(be.items.getStackInSlot(WeaponBlockEntity.SLOT_PRIMARY));
                        maxRange = air != null ? air.type().maxRange() : parked != null ? parked.maxRange() : 0;
                    }
                    entries.add(new Entry(pos, be.type().ordinal(), be.primaryCount(), be.secondaryCount(), be.fuel(),
                            be.type().minRange(), maxRange, be.busyTicks(), reachable(player, pos),
                            air == null ? -1 : air.type().ordinal(),
                            air == null ? 0 : air.blockPosition().getX(), air == null ? 0 : air.blockPosition().getZ()));
                } else if (loaded) {
                    kept.remove(pos); // the launcher is gone: forget the link
                } else {
                    entries.add(new Entry(pos, -1, 0, 0, 0, 0, 0, 0, false, -1, 0, 0));
                }
            }
            if (kept.size() != links.size()) laptop.set(ModRegistry.LINKS.get(), List.copyOf(kept));
        }
        PacketDistributor.sendToPlayer(player, new LaptopData(open, entries, Optional.ofNullable(message)));
    }

    private Payloads() {}
}

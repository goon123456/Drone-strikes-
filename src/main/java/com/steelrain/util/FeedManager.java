package com.steelrain.util;

import com.steelrain.ModRegistry;
import com.steelrain.entity.AircraftEntity;
import com.steelrain.entity.OperatorBodyEntity;
import com.steelrain.net.Payloads;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.network.PacketDistributor;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Remote drone camera. Minecraft only shows a player the world around their own body, so watching a far-away
 * drone means riding along with it: the player becomes a hidden spectator attached to the aircraft, a stand-in
 * body is left where they stood, and everything is put back when the feed ends for any reason.
 */
public final class FeedManager {
    private static final String TAG = "SteelRainFeed";

    private record Session(ResourceKey<Level> dim, Vec3 pos, float yaw, float pitch, GameType mode, int aircraftId, UUID body) {}

    private static final Map<UUID, Session> SESSIONS = new HashMap<>();

    public static boolean watching(ServerPlayer player) { return SESSIONS.containsKey(player.getUUID()); }

    public static boolean ownsBody(OperatorBodyEntity body) {
        for (Session s : SESSIONS.values()) {
            if (s.body.equals(body.getUUID())) return true;
        }
        return false;
    }

    /** @return null on success, otherwise why the feed could not start. */
    @Nullable
    public static Component start(ServerPlayer player, AircraftEntity aircraft) {
        if (watching(player)) return Component.translatable("message.steelrain.feed_busy");
        if (player.level() != aircraft.level() || !player.isAlive() || player.isSpectator()) {
            return Component.translatable("message.steelrain.feed_unavailable");
        }
        ServerLevel level = player.serverLevel();
        player.stopRiding();
        player.closeContainer();

        OperatorBodyEntity body = new OperatorBodyEntity(ModRegistry.OPERATOR_BODY.get(), level);
        body.copyFrom(player);
        level.addFreshEntity(body);

        Session s = new Session(level.dimension(), player.position(), player.getYRot(), player.getXRot(),
                player.gameMode.getGameModeForPlayer(), aircraft.getId(), body.getUUID());
        SESSIONS.put(player.getUUID(), s);
        player.getPersistentData().put(TAG, write(s));

        player.setGameMode(GameType.SPECTATOR);
        player.teleportTo(level, aircraft.getX(), aircraft.getY(), aircraft.getZ(), player.getYRot(), 45f);
        player.setCamera(aircraft);
        PacketDistributor.sendToPlayer(player, new Payloads.Feed(aircraft.getId(), true, aircraft.type().ordinal()));
        return null;
    }

    public static void stop(ServerPlayer player) {
        Session s = SESSIONS.remove(player.getUUID());
        if (s == null) return;
        restore(player, s);
    }

    private static void restore(ServerPlayer player, Session s) {
        MinecraftServer server = player.getServer();
        if (server == null) return;
        player.setCamera(player);
        player.setGameMode(s.mode);
        ServerLevel home = server.getLevel(s.dim);
        if (home == null) home = server.overworld();
        player.teleportTo(home, s.pos.x, s.pos.y, s.pos.z, s.yaw, s.pitch);
        player.resetFallDistance();
        player.getPersistentData().remove(TAG);
        Entity body = home.getEntity(s.body);
        if (body != null) body.discard();
        PacketDistributor.sendToPlayer(player, new Payloads.Feed(s.aircraftId, false, 0));
    }

    /** Ends feeds whose aircraft is gone or whose viewer let go (sneak). */
    public static void tick(MinecraftServer server) {
        if (SESSIONS.isEmpty()) return;
        for (UUID id : new ArrayList<>(SESSIONS.keySet())) {
            Session s = SESSIONS.get(id);
            ServerPlayer player = server.getPlayerList().getPlayer(id);
            if (player == null) {
                SESSIONS.remove(id);
                continue;
            }
            Entity aircraft = player.level().getEntity(s.aircraftId);
            if (aircraft == null || !aircraft.isAlive() || player.getCamera() != aircraft) stop(player);
        }
    }

    /** The stand-in was hit: the player snaps back into their body and takes the damage themselves. */
    public static void bodyHurt(OperatorBodyEntity body, DamageSource source, float amount) {
        MinecraftServer server = body.getServer();
        UUID owner = null;
        for (Map.Entry<UUID, Session> e : SESSIONS.entrySet()) {
            if (e.getValue().body.equals(body.getUUID())) owner = e.getKey();
        }
        ServerPlayer player = owner == null || server == null ? null : server.getPlayerList().getPlayer(owner);
        if (player == null) {
            body.discard();
            return;
        }
        stop(player);
        player.displayClientMessage(Component.translatable("message.steelrain.feed_attacked"), false);
        player.hurt(source, amount);
    }

    public static void onLogout(ServerPlayer player) {
        stop(player);
    }

    /** Crash recovery: if the server died mid-feed the saved player is still a spectator far from home. */
    public static void onLogin(ServerPlayer player) {
        CompoundTag data = player.getPersistentData();
        if (!data.contains(TAG) || watching(player)) return;
        CompoundTag t = data.getCompound(TAG);
        Session s = new Session(
                ResourceKey.create(Registries.DIMENSION, ResourceLocation.parse(t.getString("Dim"))),
                new Vec3(t.getDouble("X"), t.getDouble("Y"), t.getDouble("Z")), t.getFloat("Yaw"), t.getFloat("Pitch"),
                GameType.byId(t.getInt("Mode")), -1, new UUID(0, 0));
        restore(player, s);
    }

    private static CompoundTag write(Session s) {
        CompoundTag t = new CompoundTag();
        t.putString("Dim", s.dim.location().toString());
        t.putDouble("X", s.pos.x);
        t.putDouble("Y", s.pos.y);
        t.putDouble("Z", s.pos.z);
        t.putFloat("Yaw", s.yaw);
        t.putFloat("Pitch", s.pitch);
        t.putInt("Mode", s.mode.getId());
        return t;
    }

    private FeedManager() {}
}

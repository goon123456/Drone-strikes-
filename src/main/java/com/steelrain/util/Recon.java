package com.steelrain.util;

import com.steelrain.Config;
import com.steelrain.block.WeaponBlockEntity;
import com.steelrain.entity.FlyingWeaponEntity;
import com.steelrain.entity.OperatorBodyEntity;
import com.steelrain.net.Payloads;
import it.unimi.dsi.fastutil.ints.IntArrayList;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.material.MapColor;
import net.minecraft.world.phys.AABB;
import net.neoforged.neoforge.network.PacketDistributor;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** Sensors of recon drones: terrain for the laptop map, target markers, and artillery spotting. */
public final class Recon {
    public static final int HOSTILE = 0, NEUTRAL = 1, ENEMY_PLAYER = 2, FRIENDLY_PLAYER = 3, ENEMY_WEAPON = 4;
    private static final int TILES_PER_TICK = 3;
    private static final int MAX_MARKERS = 256;

    private record Source(ResourceKey<Level> dim, double x, double z, double radius, @Nullable UUID owner,
                          @Nullable String ownerName, long expiry) {}

    private static final Map<Integer, Source> SOURCES = new HashMap<>();

    /** Call every tick from a drone that is looking at the ground. */
    public static void scan(ServerLevel level, FlyingWeaponEntity drone, int radius, boolean wide) {
        long time = level.getGameTime();
        boolean slow = (time + drone.getId()) % 20 == 0;
        if (slow) {
            SOURCES.put(drone.getId(), new Source(level.dimension(), drone.getX(), drone.getZ(), radius,
                    drone.ownerId(), drone.ownerName(), time + 40));
        }
        List<ServerPlayer> viewers = viewers(level, drone);
        if (viewers.isEmpty()) return;
        sendTiles(level, drone, radius, viewers);
        if (slow) sendMarkers(level, drone, radius, viewers);
    }

    /** The owner and their team mates who are in this dimension. */
    private static List<ServerPlayer> viewers(ServerLevel level, FlyingWeaponEntity drone) {
        List<ServerPlayer> out = new ArrayList<>();
        for (ServerPlayer p : level.players()) {
            if (Teams.friendly(level, drone.ownerId(), drone.ownerName(), p.getUUID(), p.getGameProfile().getName())) out.add(p);
        }
        return out;
    }

    private static void sendTiles(ServerLevel level, FlyingWeaponEntity drone, int radius, List<ServerPlayer> viewers) {
        int r = Mth.ceil(radius / 16.0);
        int side = 2 * r + 1;
        int cx = Mth.floor(drone.getX()) >> 4, cz = Mth.floor(drone.getZ()) >> 4;
        int sent = 0;
        for (int tries = 0; tries < side * 2 && sent < TILES_PER_TICK; tries++) {
            int i = Math.floorMod(drone.scanIndex++, side * side);
            int dx = i % side - r, dz = i / side - r;
            if (dx * dx + dz * dz > r * r + 1) continue;
            LevelChunk chunk = level.getChunkSource().getChunkNow(cx + dx, cz + dz);
            if (chunk == null) continue;
            Payloads.MapTile tile = new Payloads.MapTile(cx + dx, cz + dz, colours(level, chunk));
            for (ServerPlayer p : viewers) PacketDistributor.sendToPlayer(p, tile);
            sent++;
        }
    }

    /** One byte per column, in the same packed format vanilla maps use. */
    private static byte[] colours(ServerLevel level, LevelChunk chunk) {
        byte[] out = new byte[256];
        int[] heights = new int[256];
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
        int x0 = chunk.getPos().getMinBlockX(), z0 = chunk.getPos().getMinBlockZ();
        MapColor[] cols = new MapColor[256];
        for (int z = 0; z < 16; z++) {
            for (int x = 0; x < 16; x++) {
                int y = chunk.getHeight(Heightmap.Types.WORLD_SURFACE, x, z);
                MapColor c = MapColor.NONE;
                while (y >= level.getMinBuildHeight()) {
                    BlockState state = chunk.getBlockState(pos.set(x0 + x, y, z0 + z));
                    c = state.getMapColor(level, pos);
                    if (c != MapColor.NONE) break;
                    y--;
                }
                heights[z * 16 + x] = y;
                cols[z * 16 + x] = c;
            }
        }
        for (int z = 0; z < 16; z++) {
            for (int x = 0; x < 16; x++) {
                int h = heights[z * 16 + x], north = heights[Math.max(0, z - 1) * 16 + x];
                MapColor.Brightness b = h > north ? MapColor.Brightness.HIGH
                        : h < north ? MapColor.Brightness.LOW : MapColor.Brightness.NORMAL;
                out[z * 16 + x] = cols[z * 16 + x].getPackedId(b);
            }
        }
        return out;
    }

    private static void sendMarkers(ServerLevel level, FlyingWeaponEntity drone, int radius, List<ServerPlayer> viewers) {
        IntArrayList data = new IntArrayList();
        double x = drone.getX(), z = drone.getZ();
        AABB box = new AABB(x - radius, level.getMinBuildHeight(), z - radius, x + radius, level.getMaxBuildHeight() + 64, z + radius);
        for (LivingEntity e : level.getEntitiesOfClass(LivingEntity.class, box, e -> e.isAlive() && !e.isSpectator())) {
            if (data.size() >= MAX_MARKERS * 3) break;
            double dx = e.getX() - x, dz = e.getZ() - z;
            if (dx * dx + dz * dz > radius * radius || !underOpenSky(level, e.getX(), e.getY() + e.getBbHeight(), e.getZ())) continue;
            int kind;
            if (e instanceof Player || e instanceof OperatorBodyEntity) {
                UUID id = e instanceof OperatorBodyEntity body ? body.operator().orElse(null) : e.getUUID();
                String name = e instanceof Player p ? p.getGameProfile().getName()
                        : e.getCustomName() != null ? e.getCustomName().getString() : null;
                boolean friend = Teams.friendly(level, drone.ownerId(), drone.ownerName(), id, name);
                if (!friend && !Config.MARK_PLAYERS.get()) continue;
                kind = friend ? FRIENDLY_PLAYER : ENEMY_PLAYER;
            } else {
                kind = e instanceof Enemy ? HOSTILE : NEUTRAL;
            }
            data.add(Mth.floor(e.getX()));
            data.add(Mth.floor(e.getZ()));
            data.add(kind);
        }
        for (WeaponBlockEntity be : WeaponBlockEntity.loadedIn(level)) {
            if (data.size() >= MAX_MARKERS * 3) break;
            BlockPos p = be.getBlockPos();
            double dx = p.getX() - x, dz = p.getZ() - z;
            if (dx * dx + dz * dz > radius * radius) continue;
            if (Teams.friendly(level, drone.ownerId(), drone.ownerName(), be.owner(), be.ownerName())) continue;
            if (!underOpenSky(level, p.getX(), p.getY() + be.type().height, p.getZ())) continue;
            data.add(p.getX());
            data.add(p.getZ());
            data.add(ENEMY_WEAPON);
        }
        Payloads.Markers packet = new Payloads.Markers(drone.getId(), Mth.floor(x), Mth.floor(z), radius, data.toIntArray());
        for (ServerPlayer p : viewers) PacketDistributor.sendToPlayer(p, packet);
    }

    /** Nothing solid (roof, cave ceiling, tree canopy) above this point. */
    private static boolean underOpenSky(ServerLevel level, double x, double top, double z) {
        int bx = Mth.floor(x), bz = Mth.floor(z);
        if (!level.hasChunk(bx >> 4, bz >> 4)) return false;
        return level.getHeight(Heightmap.Types.MOTION_BLOCKING, bx, bz) <= top + 0.5;
    }

    /** Is a recon drone friendly to this owner watching the given spot right now? */
    public static boolean observed(ServerLevel level, double x, double z, @Nullable UUID owner, @Nullable String ownerName) {
        long time = level.getGameTime();
        SOURCES.values().removeIf(s -> s.expiry < time);
        for (Source s : SOURCES.values()) {
            if (s.dim != level.dimension()) continue;
            double dx = s.x - x, dz = s.z - z;
            if (dx * dx + dz * dz <= s.radius * s.radius && Teams.friendly(level, owner, ownerName, s.owner, s.ownerName)) return true;
        }
        return false;
    }

    private Recon() {}
}

package com.steelrain.client;

import com.steelrain.net.Payloads;
import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.material.MapColor;

import java.util.HashMap;
import java.util.Map;

/** What this player's recon drones have reported: remembered terrain and the latest contacts. */
public final class ReconClient {
    private static final int MAX_TILES = 16000;
    private static final Long2ObjectOpenHashMap<byte[]> TILES = new Long2ObjectOpenHashMap<>();

    public record Report(Payloads.Markers markers, long receivedAt) {}

    private static final Map<Integer, Report> REPORTS = new HashMap<>();

    static void tile(Payloads.MapTile t) {
        if (TILES.size() > MAX_TILES) TILES.clear();
        TILES.put(ChunkPos.asLong(t.cx(), t.cz()), t.colours());
    }

    static void markers(Payloads.Markers m) {
        REPORTS.put(m.droneId(), new Report(m, System.currentTimeMillis()));
    }

    /** Map colour (ABGR) a drone has seen at this column, or 0 if nobody has looked there. */
    public static int colour(int x, int z) {
        byte[] tile = TILES.get(ChunkPos.asLong(x >> 4, z >> 4));
        if (tile == null) return 0;
        int packed = tile[(z & 15) * 16 + (x & 15)] & 255;
        return packed >> 2 == 0 ? 0 : MapColor.getColorFromPackedId(packed);
    }

    /** Reports from the last three seconds. */
    public static Iterable<Report> fresh() {
        long now = System.currentTimeMillis();
        REPORTS.values().removeIf(r -> now - r.receivedAt > 3000);
        return REPORTS.values();
    }

    public static void clear() {
        TILES.clear();
        REPORTS.clear();
    }

    private ReconClient() {}
}

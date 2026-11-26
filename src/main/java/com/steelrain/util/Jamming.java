package com.steelrain.util;

import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Mth;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Every active jammer on the server. Jammers announce themselves a few times a second; radio-linked things ask
 * how strongly they are being jammed where they are. Strength fades in from the edge of a bubble and reaches
 * full (link lost) at 40% of its radius. A jammer never affects its owner's or their team's equipment.
 */
public final class Jamming {
    public record Emitter(ResourceKey<Level> dim, Vec3 pos, double radius, @Nullable UUID owner,
                          @Nullable String ownerName, long expiry) {}

    private static final Map<Object, Emitter> EMITTERS = new HashMap<>();

    /** @param key anything unique to the jammer (its block position, the wearer's UUID...) */
    public static void emit(ServerLevel level, Object key, Vec3 pos, double radius, @Nullable UUID owner, @Nullable String ownerName) {
        EMITTERS.put(key, new Emitter(level.dimension(), pos, radius, owner, ownerName, level.getGameTime() + 30));
    }

    private static void prune(ServerLevel level) {
        long now = level.getGameTime();
        EMITTERS.values().removeIf(e -> e.dim == level.dimension() && e.expiry < now);
    }

    /** 0 = clean signal, 1 = completely jammed. */
    public static float level(ServerLevel level, Vec3 pos, @Nullable UUID owner, @Nullable String ownerName) {
        if (EMITTERS.isEmpty()) return 0;
        prune(level);
        float worst = 0;
        for (Emitter e : EMITTERS.values()) {
            if (e.dim != level.dimension()) continue;
            double d = Math.sqrt(e.pos.distanceToSqr(pos));
            if (d >= e.radius || Teams.friendly(level, owner, ownerName, e.owner, e.ownerName)) continue;
            worst = Math.max(worst, (float) Mth.clamp((1 - d / e.radius) / 0.6, 0, 1));
        }
        return worst;
    }

    /** Hostile jammers whose bubble comes within {@code range} of a point: jammers are loud and easy to locate. */
    public static List<Emitter> hostileNear(ServerLevel level, double x, double z, double range, @Nullable UUID owner, @Nullable String ownerName) {
        List<Emitter> out = new ArrayList<>();
        if (EMITTERS.isEmpty()) return out;
        prune(level);
        for (Emitter e : EMITTERS.values()) {
            if (e.dim != level.dimension()) continue;
            double dx = e.pos.x - x, dz = e.pos.z - z, r = range + e.radius;
            if (dx * dx + dz * dz <= r * r && !Teams.friendly(level, owner, ownerName, e.owner, e.ownerName)) out.add(e);
        }
        return out;
    }

    private Jamming() {}
}

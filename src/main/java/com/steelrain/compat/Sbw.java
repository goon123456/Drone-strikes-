package com.steelrain.compat;

import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.neoforged.fml.ModList;

import javax.annotation.Nullable;
import java.util.List;
import java.util.UUID;

/**
 * Optional Superb Warfare compatibility. Steel Rain runs on its own; when Superb Warfare is installed as well, our
 * air defence also engages its crewed aircraft and guided weapons, its vehicles can carry a Jammer Dome and show up
 * on recon drones. Everything that touches Superb Warfare's classes lives in {@link SbwHooks}, which is only loaded
 * when the mod is present.
 */
public final class Sbw {
    public static final boolean LOADED = ModList.get().isLoaded("superbwarfare");

    /** Whether an SBW aircraft or guided weapon is a hostile target; false for anything else or without SBW. */
    public static boolean isAirThreat(Level level, Entity e, boolean highValueOnly, @Nullable UUID owner, @Nullable String ownerName) {
        return LOADED && SbwHooks.isAirThreat(level, e, highValueOnly, owner, ownerName);
    }

    /** An SBW vehicle (high-value target for missiles). */
    public static boolean isVehicle(Entity e) { return LOADED && SbwHooks.isVehicle(e); }

    /** Intact SBW vehicles in a box, for recon markers. */
    public static List<Entity> vehicles(Level level, AABB box) { return LOADED ? SbwHooks.vehicles(level, box) : List.of(); }

    /** A Jammer Dome fitted to an SBW vehicle draws from its battery: false if it has no power left. */
    public static boolean domePower(Entity e, int fe, boolean draw) { return !LOADED || SbwHooks.domePower(e, fe, draw); }

    private Sbw() {}
}

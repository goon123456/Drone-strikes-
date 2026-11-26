package com.steelrain.weapon;

import com.steelrain.entity.AircraftEntity;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.phys.Vec3;

import javax.annotation.Nullable;

/** Anything the Field Laptop can command: Superb Warfare strike vehicles and the remaining emplacements. */
public interface Launcher {
    String nameKey();

    /** Launches aircraft rather than munitions (shows camera / recall, hides the round counter). */
    default boolean aircraftStation() { return false; }

    default boolean hasSecondAmmo() { return false; }

    int ammoCount();

    default int secondCount() { return 0; }

    /** Stored energy in thousands of FE. */
    int energyK();

    int minRange();

    /** Maximum range with what is loaded right now. */
    int maxRangeNow();

    int busyTicks();

    Vec3 launcherPos();

    @Nullable
    default AircraftEntity airborne() { return null; }

    default boolean recall() { return false; }

    /** @return null when the order was accepted, otherwise the reason it was refused. */
    @Nullable
    Component tryLaunch(ServerPlayer player, int x, int z, int count);
}

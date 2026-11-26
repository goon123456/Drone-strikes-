package com.steelrain.weapon;

import com.steelrain.Config;
import com.steelrain.ModRegistry;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

import java.util.function.Supplier;

/** Autopilot aircraft. Sizes are real-world metres = blocks. */
public enum AircraftType {
    //            id            speed alt  span  len  runway sensor endurance(t)  hp  gear  fuel  maxRange orbit
    REAPER("mq9_reaper", 1.3, 70, 20, 11, 40, 48, 20 * 45, 16f, 1.6, 1000, 3000, 50),
    TB2("bayraktar_tb2", 1.0, 90, 12, 6.5, 30, 80, 20 * 60 * 8, 12f, 1.1, 1500, 4000, 45),
    GLOBAL_HAWK("global_hawk", 2.2, 120, 40, 14.5, 60, 128, 20 * 60 * 15, 30f, 1.7, 4000, 10000, 70),
    ORLAN("orlan_10", 0.9, 50, 3, 2, 0, 48, 20 * 60 * 4, 4f, 0.25, 0, 1500, 30);

    public final String id;
    public final double speed;
    /** Cruise height above the terrain. */
    public final double altitude;
    public final double span;
    public final double length;
    /** Blocks of runway needed; 0 = catapult launched, parachute recovery. */
    public final int runway;
    public final int sensorRadius;
    /** Ticks it circles the target before coming home on its own. */
    public final int endurance;
    public final float health;
    /** Height of the fuselage axis above the ground when parked. */
    public final double gear;
    /** FE drawn from the station to launch it. */
    public final int energy;
    private final int maxRange;
    public final double orbitRadius;

    AircraftType(String id, double speed, double altitude, double span, double length, int runway, int sensorRadius,
                 int endurance, float health, double gear, int fuel, int maxRange, double orbitRadius) {
        this.id = id;
        this.speed = speed;
        this.altitude = altitude;
        this.span = span;
        this.length = length;
        this.runway = runway;
        this.sensorRadius = sensorRadius;
        this.endurance = endurance;
        this.health = health;
        this.gear = gear;
        this.energy = fuel * 250;
        this.maxRange = maxRange;
        this.orbitRadius = orbitRadius;
    }

    public int maxRange() { return (int) Math.round(maxRange * Config.RANGE_MULTIPLIER.get()); }

    public boolean armed() { return this == REAPER; }

    public Supplier<? extends Item> item() {
        return switch (this) {
            case REAPER -> ModRegistry.MQ9_REAPER;
            case TB2 -> ModRegistry.BAYRAKTAR_TB2;
            case GLOBAL_HAWK -> ModRegistry.GLOBAL_HAWK;
            case ORLAN -> ModRegistry.ORLAN_10;
        };
    }

    public static AircraftType of(ItemStack stack) {
        for (AircraftType t : values()) {
            if (stack.is(t.item().get())) return t;
        }
        return null;
    }

    public String translationKey() { return "entity.steelrain." + id; }
}

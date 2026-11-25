package com.steelrain.weapon;

/** Flight and warhead characteristics of one munition type. */
public record MissileProfile(
        String id,
        FlightMode mode,
        double speed,          // blocks per tick
        float blastPower,      // vanilla explosion power
        int craterRadius,      // extra spherical crater, 0 = none
        float damage,          // max damage to entities at the centre
        double damageRadius,
        boolean fire,
        float health,
        boolean interceptable, // air defence can engage it
        boolean jammable,      // jammers affect it
        double cruiseAltitude, // height above terrain for CRUISE
        double spread,         // random aiming error in blocks
        boolean smokeTrail) {

    public enum FlightMode { CRUISE, BALLISTIC, DIRECT, DROP }

    public static final MissileProfile SHAHED = new MissileProfile("shahed_136", FlightMode.CRUISE,
            1.1, 5f, 3, 45f, 9, false, 6f, true, true, 45, 1.5, false);
    public static final MissileProfile TOMAHAWK = new MissileProfile("tomahawk", FlightMode.CRUISE,
            2.4, 7f, 5, 90f, 14, false, 10f, true, false, 32, 0.5, true);
    public static final MissileProfile ISKANDER = new MissileProfile("iskander", FlightMode.BALLISTIC,
            3.6, 8f, 8, 140f, 20, true, 20f, true, false, 0, 1.0, true);
    public static final MissileProfile GMLRS = new MissileProfile("gmlrs", FlightMode.BALLISTIC,
            3.0, 5f, 3, 55f, 9, false, 6f, true, false, 0, 2.5, true);
    public static final MissileProfile SHELL = new MissileProfile("shell_155mm", FlightMode.BALLISTIC,
            3.0, 4f, 2, 35f, 7, false, 100f, false, false, 0, 6.0, false);
    public static final MissileProfile HELLFIRE = new MissileProfile("hellfire", FlightMode.DIRECT,
            2.6, 4f, 2, 45f, 6, false, 4f, false, false, 0, 0.5, true);
    /** Small bomblet released by the Mavic: falls under gravity, no crater. */
    public static final MissileProfile GRENADE = new MissileProfile("drone_grenade", FlightMode.DROP,
            0, 2.2f, 0, 26f, 5, false, 100f, false, false, 0, 0, false);

    /** Cruise and ballistic missiles are worth an expensive long-range interceptor. */
    public boolean highValue() { return this == TOMAHAWK || this == ISKANDER; }
}

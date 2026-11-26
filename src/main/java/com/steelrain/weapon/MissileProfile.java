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
            3.0, 5f, 3, 55f, 9, false, 6f, true, true, 0, 2.5, true);
    public static final MissileProfile SHELL = new MissileProfile("shell_155mm", FlightMode.BALLISTIC,
            3.0, 4f, 2, 35f, 7, false, 100f, false, false, 0, 6.0, false);
    public static final MissileProfile HELLFIRE = new MissileProfile("hellfire", FlightMode.DIRECT,
            2.6, 4f, 2, 45f, 6, false, 4f, false, false, 0, 0.5, true);
    /** Bursts above the target and scatters bomblets over an area. */
    public static final MissileProfile SHELL_CLUSTER = new MissileProfile("shell_cluster", FlightMode.BALLISTIC,
            3.0, 2f, 0, 10f, 4, false, 100f, false, false, 0, 6.0, false);
    /** Lays a smoke screen that hides what is under it from recon drones. No damage. */
    public static final MissileProfile SHELL_SMOKE = new MissileProfile("shell_smoke", FlightMode.BALLISTIC,
            3.0, 0f, 0, 0f, 0, false, 100f, false, false, 0, 6.0, false);
    /** Small blast, then sets the area alight. */
    public static final MissileProfile SHELL_INCENDIARY = new MissileProfile("shell_incendiary", FlightMode.BALLISTIC,
            3.0, 2f, 0, 12f, 6, true, 100f, false, false, 0, 6.0, false);
    /** One big missile in place of six rockets: inertial guidance, so jammers do not bother it. */
    public static final MissileProfile ATACMS = new MissileProfile("atacms", FlightMode.BALLISTIC,
            3.4, 8f, 6, 110f, 16, false, 16f, true, false, 0, 1.0, true);
    /** 120 mm mortar bomb. Unguided, like every shell fired from a hand-laid gun. */
    public static final MissileProfile MORTAR_SHELL = new MissileProfile("mortar_shell", FlightMode.BALLISTIC,
            3.0, 3f, 1, 30f, 6, false, 100f, false, false, 0, 0, false);
    /** Cluster submunition. */
    public static final MissileProfile BOMBLET = new MissileProfile("bomblet", FlightMode.DROP,
            0, 2.2f, 0, 22f, 5, false, 100f, false, false, 0, 0, false);
    /** Small bomblet released by the Mavic: falls under gravity, no crater. */
    public static final MissileProfile GRENADE = new MissileProfile("drone_grenade", FlightMode.DROP,
            0, 2.2f, 0, 26f, 5, false, 100f, false, false, 0, 0, false);

    /** Cruise and ballistic missiles are worth an expensive long-range interceptor. */
    public boolean highValue() { return this == TOMAHAWK || this == ISKANDER || this == ATACMS; }
}

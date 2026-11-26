package com.steelrain;

import net.neoforged.neoforge.common.ModConfigSpec;

/** Server-side balance and safety settings (config/steelrain-common.toml). */
public final class Config {
    private static final ModConfigSpec.Builder B = new ModConfigSpec.Builder();

    public static final ModConfigSpec.BooleanValue BLOCK_DAMAGE = B
            .comment("If false, explosions from this mod hurt entities but never break blocks.")
            .define("blockDamage", true);
    public static final ModConfigSpec.BooleanValue DEBRIS = B
            .comment("Throw a few blocks out of craters as falling debris.")
            .define("debris", true);
    public static final ModConfigSpec.DoubleValue EXPLOSION_SCALE = B
            .comment("Multiplier for crater size and blast power of every weapon.")
            .defineInRange("explosionScale", 1.0, 0.1, 3.0);
    public static final ModConfigSpec.DoubleValue DAMAGE_SCALE = B
            .comment("Multiplier for damage dealt to entities.")
            .defineInRange("damageScale", 1.0, 0.0, 10.0);
    public static final ModConfigSpec.DoubleValue RANGE_MULTIPLIER = B
            .comment("Multiplier for the maximum range of every launcher.")
            .defineInRange("rangeMultiplier", 1.0, 0.1, 10.0);
    public static final ModConfigSpec.BooleanValue CHUNK_LOADING = B
            .comment("Missiles and drones keep the chunks around them loaded while flying.",
                    "Needed for long range strikes. Turn off on weak servers; weapons then only work near players.")
            .define("chunkLoading", true);
    public static final ModConfigSpec.IntValue LAPTOP_LINK_RANGE = B
            .comment("How far (blocks) the Field Laptop can be from a linked launcher.")
            .defineInRange("laptopLinkRange", 128, 8, 100000);
    public static final ModConfigSpec.IntValue FPV_RANGE = B
            .comment("Radio control range (blocks) of the FPV drone and Mavic, measured from where the pilot stands.",
                    "Fibre-optic drones are limited by their cable instead.")
            .defineInRange("radioRange", 500, 16, 4000);
    public static final ModConfigSpec.IntValue FPV_BATTERY_SECONDS = B
            .comment("Flight time of an FPV drone battery, in seconds.")
            .defineInRange("fpvBatterySeconds", 150, 10, 3600);
    public static final ModConfigSpec.IntValue MAVIC_BATTERY_SECONDS = B
            .comment("Flight time of a Mavic recon drone battery, in seconds.")
            .defineInRange("mavicBatterySeconds", 360, 10, 3600);
    public static final ModConfigSpec.IntValue AIR_DEFENCE_RANGE = B
            .comment("Engagement radius (blocks) of the Pantsir.")
            .defineInRange("airDefenceRange", 128, 16, 256);
    public static final ModConfigSpec.IntValue AIR_DEFENCE_CEILING = B
            .comment("How far above itself (blocks) the Pantsir can reach. The Global Hawk cruises above this; only the Patriot can hit it.")
            .defineInRange("airDefenceCeiling", 100, 16, 512);
    public static final ModConfigSpec.IntValue PATRIOT_RANGE = B
            .comment("Engagement radius (blocks) of the Patriot. It only fires at aircraft, cruise and ballistic missiles.")
            .defineInRange("patriotRange", 400, 32, 1024);
    public static final ModConfigSpec.BooleanValue MARK_PLAYERS = B
            .comment("Recon drones show players on the laptop map. Turn off for PvP servers that want stealth.")
            .define("reconMarksPlayers", true);
    public static final ModConfigSpec.DoubleValue SPOTTED_SPREAD = B
            .comment("Aiming error multiplier for rounds landing in an area one of your recon drones is watching.")
            .defineInRange("spottedSpreadMultiplier", 0.2, 0.0, 1.0);
    public static final ModConfigSpec.IntValue JAMMER_RANGE = B
            .comment("Bubble radius (blocks) of the EW Jammer vehicle.")
            .defineInRange("jammerRange", 64, 8, 256);
    public static final ModConfigSpec.IntValue TRENCH_JAMMER_RANGE = B
            .comment("Bubble radius of the single-block Trench Jammer.")
            .defineInRange("trenchJammerRange", 32, 4, 128);
    public static final ModConfigSpec.IntValue STRATEGIC_JAMMER_RANGE = B
            .comment("Bubble radius of the Krasukha-4 long-range jammer.")
            .defineInRange("strategicJammerRange", 200, 32, 512);
    public static final ModConfigSpec.IntValue BACKPACK_JAMMER_RANGE = B
            .comment("Bubble radius of the Jammer Backpack.")
            .defineInRange("backpackJammerRange", 24, 4, 64);
    public static final ModConfigSpec.IntValue DOME_JAMMER_RANGE = B
            .comment("Bubble radius of a Jammer Dome fitted to a vehicle.")
            .defineInRange("domeJammerRange", 14, 4, 64);
    public static final ModConfigSpec.IntValue FIBER_MAX = B
            .comment("Most cable (blocks) a Fiber Spool Backpack can hold.")
            .defineInRange("fiberMaxLength", 1000, 100, 10000);
    public static final ModConfigSpec.BooleanValue REQUIRE_FUEL = B
            .comment("If false, launchers and the jammer work without fuel.")
            .define("requireFuel", true);

    public static final ModConfigSpec.IntValue FE_PER_MB = B
            .comment("Forge Energy needed to make 1 mB of vehicle fuel when a vehicle is charged from a power cable.")
            .defineInRange("fePerMb", 40, 1, 100000);

    public static final ModConfigSpec SPEC = B.build();

    private Config() {}
}

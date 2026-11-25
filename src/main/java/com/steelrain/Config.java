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
            .comment("Control range (blocks) of the FPV drone. Beyond it the signal is lost and the drone falls.",
                    "Values above the server view distance will not work well.")
            .defineInRange("fpvRange", 200, 16, 512);
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
    public static final ModConfigSpec.DoubleValue AIR_DEFENCE_HIT_CHANCE = B
            .comment("Chance for one Pantsir interceptor to destroy its target.")
            .defineInRange("airDefenceHitChance", 0.8, 0.0, 1.0);
    public static final ModConfigSpec.IntValue PATRIOT_RANGE = B
            .comment("Engagement radius (blocks) of the Patriot. It only fires at aircraft, cruise and ballistic missiles.")
            .defineInRange("patriotRange", 400, 32, 1024);
    public static final ModConfigSpec.DoubleValue PATRIOT_HIT_CHANCE = B
            .comment("Chance for one Patriot interceptor to destroy its target.")
            .defineInRange("patriotHitChance", 0.9, 0.0, 1.0);
    public static final ModConfigSpec.BooleanValue MARK_PLAYERS = B
            .comment("Recon drones show players on the laptop map. Turn off for PvP servers that want stealth.")
            .define("reconMarksPlayers", true);
    public static final ModConfigSpec.DoubleValue SPOTTED_SPREAD = B
            .comment("Aiming error multiplier for rounds landing in an area one of your recon drones is watching.")
            .defineInRange("spottedSpreadMultiplier", 0.2, 0.0, 1.0);
    public static final ModConfigSpec.IntValue JAMMER_RANGE = B
            .comment("Radius (blocks) of the EW jammer.")
            .defineInRange("jammerRange", 48, 8, 256);
    public static final ModConfigSpec.BooleanValue REQUIRE_FUEL = B
            .comment("If false, launchers and the jammer work without fuel.")
            .define("requireFuel", true);

    public static final ModConfigSpec SPEC = B.build();

    private Config() {}
}

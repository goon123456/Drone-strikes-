package com.steelrain.client;

import net.neoforged.neoforge.common.ModConfigSpec;

/** Settings that belong to one player's own game (config/steelrain-client.toml). */
public final class ClientConfig {
    private static final ModConfigSpec.Builder B = new ModConfigSpec.Builder();

    public static final ModConfigSpec.BooleanValue REDUCED_MOTION = B
            .comment("Less movement on screen with a gun in the hands: recoil kicks the view less than half as far,",
                    "the sights drift far less, the gun does not bob or dip, and there is no muzzle flash.")
            .define("reducedMotion", false);

    public static final ModConfigSpec SPEC = B.build();

    public static boolean calm() { return SPEC.isLoaded() && REDUCED_MOTION.get(); }

    private ClientConfig() {}
}

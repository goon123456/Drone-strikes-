package com.steelrain.client;

import com.steelrain.ModRegistry;
import com.steelrain.weapon.AircraftType;
import com.steelrain.weapon.WeaponType;
import net.minecraft.resources.ResourceLocation;

import java.util.EnumMap;
import java.util.Map;

/**
 * Every model in the mod, built from boxes at real-world scale (1 block = 1 metre).
 * Flying things: origin on the fuselage axis, +Z is the nose. Vehicles: origin at the rear-centre-bottom of
 * the footprint, +Z towards the cab. Groups named "turret" (yaw) and "gun" (pitch) are animated.
 */
public final class Models {
    public static final ResourceLocation PAINT = ResourceLocation.fromNamespaceAndPath(ModRegistry.MODID, "textures/entity/paint.png");

    private static final int OLIVE = 0x5C683E, OLIVE_D = 0x424C2E, OLIVE_L = 0x6E7C4C, TYRE = 0x242426,
            STEEL = 0x8A8E94, STEEL_D = 0x3E4246, GLASS = 0x28405A, WHITE = 0xD8D8D2, GREY = 0x9A9EA4,
            GREY_D = 0x70747A, RED = 0xBE2824, SAND = 0xB8AC8C, SAND_D = 0x8E846A, ORANGE = 0xD6582C,
            BRASS = 0xC4A64C, CYAN = 0x40C8FF, COPPER = 0xB87040, BLACK = 0x18181A, CARBON = 0x2C2C30, DUNE = 0xC6C2B4;

    private static final float D18 = (float) Math.toRadians(18), D15 = (float) Math.toRadians(15);

    // ------------------------------------------------------------------ munitions

    public static final BoxModel TOMAHAWK = BoxModel.load("tomahawk");
    public static final BoxModel ISKANDER = BoxModel.load("iskander");
    public static final BoxModel ATACMS = BoxModel.load("atacms");
    public static final BoxModel GMLRS = BoxModel.load("gmlrs");
    public static final BoxModel SHELL = BoxModel.load("shell_155");
    public static final BoxModel MORTAR_BOMB = BoxModel.load("mortar_bomb");
    public static final BoxModel HELLFIRE = BoxModel.load("hellfire");
    public static final BoxModel PANTSIR_MISSILE = BoxModel.load("pantsir_missile");
    public static final BoxModel PATRIOT_MISSILE = BoxModel.load("patriot_missile");
    public static final BoxModel GRENADE = BoxModel.load("grenade");

    public static final BoxModel SHAHED = BoxModel.load("shahed_136");
    public static final BoxModel FPV = BoxModel.load("fpv_drone");
    public static final BoxModel FIBER_FPV = BoxModel.load("fiber_fpv_drone");

    /** Tracer: a short bright streak along the direction of flight. */
    public static final BoxModel BULLET = new BoxModel()
            .box(0xFFE27A, -0.04, -0.04, -0.9, 0.08, 0.08, 1.4)
            .box(0xFFFFFF, -0.03, -0.03, 0.5, 0.06, 0.06, 0.25);

    /** Drone jammer fitted on top of a vehicle. */
    public static final BoxModel DOME = BoxModel.load("jammer_dome");

    /** Worn on the back. Origin at the middle of the wearer's back, +Z away from them. */
    public static final BoxModel FIBER_PACK = BoxModel.load("fiber_pack");
    public static final BoxModel JAMMER_PACK = BoxModel.load("jammer_pack");

    public static final BoxModel MAVIC = BoxModel.load("mavic");

    // ------------------------------------------------------------------ aircraft

    private static final Map<AircraftType, BoxModel> AIRCRAFT = new EnumMap<>(AircraftType.class);
    static {
        for (AircraftType type : AircraftType.values()) AIRCRAFT.put(type, BoxModel.load(type.id));
    }

    public static BoxModel aircraft(AircraftType type) { return AIRCRAFT.get(type); }

    // ------------------------------------------------------------------ vehicles

    private static final Map<WeaponType, BoxModel> WEAPONS = new EnumMap<>(WeaponType.class);
    static {
        for (WeaponType type : WeaponType.values()) WEAPONS.put(type, BoxModel.load(type.id));
    }

    public static BoxModel weapon(WeaponType type) { return WEAPONS.get(type); }

    private Models() {}
}

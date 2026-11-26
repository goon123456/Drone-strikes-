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

    public static final BoxModel TOMAHAWK = new BoxModel()
            .box(GREY, -0.26, -0.26, -2.8, 0.52, 0.52, 5.2)
            .box(GREY_D, -0.18, -0.18, 2.4, 0.36, 0.36, 0.4)
            .box(RED, -0.27, -0.27, 1.8, 0.54, 0.54, 0.12)
            .box(GREY_D, -1.33, -0.03, -0.2, 2.66, 0.06, 0.5)
            .box(GREY_D, -0.6, -0.03, -2.8, 1.2, 0.06, 0.4)
            .box(GREY_D, -0.03, -0.6, -2.8, 0.06, 1.2, 0.4)
            .box(GREY_D, -0.14, -0.4, -1.9, 0.28, 0.15, 0.7);

    public static final BoxModel ISKANDER = new BoxModel()
            .box(OLIVE_L, -0.46, -0.46, -3.65, 0.92, 0.92, 5.6)
            .box(OLIVE_L, -0.36, -0.36, 1.95, 0.72, 0.72, 0.9)
            .box(WHITE, -0.22, -0.22, 2.85, 0.44, 0.44, 0.6)
            .box(WHITE, -0.1, -0.1, 3.45, 0.2, 0.2, 0.3)
            .box(OLIVE_D, -0.47, -0.47, -1.5, 0.94, 0.94, 0.15)
            .box(OLIVE_D, -0.76, -0.05, -3.65, 1.52, 0.1, 0.8)
            .box(OLIVE_D, -0.05, -0.76, -3.65, 0.1, 1.52, 0.8)
            .box(BLACK, -0.3, -0.3, -3.75, 0.6, 0.6, 0.1);

    public static final BoxModel ATACMS = new BoxModel()
            .box(OLIVE, -0.3, -0.3, -2.0, 0.6, 0.6, 3.3)
            .box(OLIVE, -0.22, -0.22, 1.3, 0.44, 0.44, 0.4)
            .box(BLACK, -0.12, -0.12, 1.7, 0.24, 0.24, 0.3)
            .box(OLIVE_D, -0.65, -0.03, -2.0, 1.3, 0.06, 0.5)
            .box(OLIVE_D, -0.03, -0.65, -2.0, 0.06, 1.3, 0.5)
            .box(BLACK, -0.2, -0.2, -2.08, 0.4, 0.4, 0.08);

    public static final BoxModel GMLRS = new BoxModel()
            .box(OLIVE, -0.125, -0.125, -1.97, 0.25, 0.25, 3.6)
            .box(BLACK, -0.08, -0.08, 1.63, 0.16, 0.16, 0.34)
            .box(OLIVE_D, -0.3, -0.02, -1.97, 0.6, 0.04, 0.35)
            .box(OLIVE_D, -0.02, -0.3, -1.97, 0.04, 0.6, 0.35);

    public static final BoxModel SHELL = new BoxModel()
            .box(OLIVE, -0.1, -0.1, -0.42, 0.2, 0.2, 0.6)
            .box(BRASS, -0.06, -0.06, 0.18, 0.12, 0.12, 0.24)
            .box(COPPER, -0.105, -0.105, -0.36, 0.21, 0.21, 0.06);

    public static final BoxModel HELLFIRE = new BoxModel()
            .box(STEEL_D, -0.1, -0.1, -0.8, 0.2, 0.2, 1.5)
            .box(GLASS, -0.08, -0.08, 0.7, 0.16, 0.16, 0.13)
            .box(BLACK, -0.18, -0.015, -0.8, 0.36, 0.03, 0.3)
            .box(BLACK, -0.015, -0.18, -0.8, 0.03, 0.36, 0.3)
            .box(BLACK, -0.16, -0.015, 0.1, 0.32, 0.03, 0.2)
            .box(BLACK, -0.015, -0.16, 0.1, 0.03, 0.32, 0.2);

    public static final BoxModel PANTSIR_MISSILE = new BoxModel()
            .box(WHITE, -0.1, -0.1, -1.6, 0.2, 0.2, 1.4)
            .box(OLIVE, -0.06, -0.06, -0.2, 0.12, 0.12, 1.8)
            .box(STEEL_D, -0.25, -0.015, -1.6, 0.5, 0.03, 0.3)
            .box(STEEL_D, -0.015, -0.25, -1.6, 0.03, 0.5, 0.3)
            .box(STEEL_D, -0.14, -0.015, -0.2, 0.28, 0.03, 0.2)
            .box(STEEL_D, -0.015, -0.14, -0.2, 0.03, 0.28, 0.2);

    public static final BoxModel PATRIOT_MISSILE = new BoxModel()
            .box(WHITE, -0.2, -0.2, -2.65, 0.4, 0.4, 4.6)
            .box(SAND, -0.14, -0.14, 1.95, 0.28, 0.28, 0.5)
            .box(SAND, -0.07, -0.07, 2.45, 0.14, 0.14, 0.2)
            .box(STEEL_D, -0.43, -0.02, -2.65, 0.86, 0.04, 0.5)
            .box(STEEL_D, -0.02, -0.43, -2.65, 0.04, 0.86, 0.5)
            .box(BLACK, -0.21, -0.21, -1.0, 0.42, 0.42, 0.08);

    public static final BoxModel GRENADE = new BoxModel()
            .box(OLIVE, -0.06, -0.06, -0.08, 0.12, 0.12, 0.16)
            .box(BRASS, -0.035, -0.035, 0.08, 0.07, 0.07, 0.05)
            .box(STEEL_D, -0.09, -0.01, -0.15, 0.18, 0.02, 0.07)
            .box(STEEL_D, -0.01, -0.09, -0.15, 0.02, 0.18, 0.07);

    public static final BoxModel SHAHED = new BoxModel()
            .box(DUNE, -0.2, -0.2, -1.5, 0.4, 0.4, 3.0)
            .box(DUNE, -0.13, -0.13, 1.5, 0.26, 0.26, 0.3)
            .box(DUNE, -1.25, -0.05, -1.6, 2.5, 0.1, 0.7)
            .box(DUNE, -0.9, -0.05, -0.9, 1.8, 0.1, 0.7)
            .box(DUNE, -0.55, -0.05, -0.2, 1.1, 0.1, 0.7)
            .pair(GREY_D, 1.2, -0.3, -1.6, 0.06, 0.6, 0.7)
            .box(STEEL_D, -0.12, -0.12, -1.7, 0.24, 0.24, 0.2)
            .box(BLACK, -0.4, -0.03, -1.76, 0.8, 0.06, 0.04);

    // ------------------------------------------------------------------ small drones

    private static BoxModel fpv(boolean fiber) {
        BoxModel m = new BoxModel()
                .box(CARBON, -0.09, -0.03, -0.13, 0.18, 0.07, 0.26)
                .box(STEEL_D, -0.07, 0.04, -0.1, 0.14, 0.05, 0.2)
                .box(BLACK, -0.04, -0.01, 0.13, 0.08, 0.07, 0.04)
                .box(OLIVE, -0.045, -0.12, -0.12, 0.09, 0.08, 0.34)
                .box(BRASS, -0.03, -0.105, 0.22, 0.06, 0.05, 0.06)
                .pair(fiber ? CYAN : ORANGE, 0.1, 0.03, 0.1, 0.22, 0.012, 0.22)
                .pair(fiber ? CYAN : ORANGE, 0.1, 0.03, -0.32, 0.22, 0.012, 0.22);
        if (fiber) {
            // the cable reel slung under the tail
            m.box(WHITE, -0.08, -0.16, -0.3, 0.16, 0.14, 0.16).box(STEEL_D, -0.1, -0.11, -0.26, 0.2, 0.04, 0.08);
        }
        return m.group("a1", "root", 0, 0, 0).tilt(0, Math.PI / 4, 0).box(BLACK, -0.02, -0.01, -0.3, 0.04, 0.025, 0.6)
                .group("a2", "root", 0, 0, 0).tilt(0, -Math.PI / 4, 0).box(BLACK, -0.02, -0.01, -0.3, 0.04, 0.025, 0.6);
    }

    public static final BoxModel FPV = fpv(false);
    public static final BoxModel FIBER_FPV = fpv(true);

    /** Tracer: a short bright streak along the direction of flight. */
    public static final BoxModel BULLET = new BoxModel()
            .box(0xFFE27A, -0.04, -0.04, -0.9, 0.08, 0.08, 1.4)
            .box(0xFFFFFF, -0.03, -0.03, 0.5, 0.06, 0.06, 0.25);

    /** Small radome fitted on top of a vehicle. */
    public static final BoxModel DOME = new BoxModel()
            .box(GREY_D, -0.35, 0, -0.35, 0.7, 0.12, 0.7)
            .box(GREY, -0.28, 0.12, -0.28, 0.56, 0.3, 0.56)
            .box(STEEL_D, -0.03, 0.42, -0.03, 0.06, 0.6, 0.06)
            .box(STEEL_D, 0.2, 0.42, 0.2, 0.04, 0.4, 0.04);

    /** Worn on the back. Origin at the middle of the wearer's back, +Z away from them. */
    public static final BoxModel FIBER_PACK = new BoxModel()
            .box(OLIVE_D, -0.2, -0.28, 0, 0.4, 0.56, 0.16)
            .box(WHITE, -0.16, -0.16, 0.16, 0.32, 0.32, 0.14)
            .box(STEEL_D, -0.05, -0.05, 0.3, 0.1, 0.1, 0.04)
            .box(CYAN, -0.17, -0.02, 0.18, 0.34, 0.04, 0.1);

    public static final BoxModel JAMMER_PACK = new BoxModel()
            .box(OLIVE, -0.2, -0.28, 0, 0.4, 0.56, 0.2)
            .box(OLIVE_D, -0.16, -0.2, 0.2, 0.32, 0.3, 0.05)
            .box(STEEL_D, -0.17, 0.28, 0.05, 0.03, 0.55, 0.03)
            .box(STEEL_D, 0.14, 0.28, 0.05, 0.03, 0.4, 0.03)
            .box(STEEL_D, -0.015, 0.28, 0.12, 0.03, 0.7, 0.03)
            .box(RED, -0.03, 0.1, 0.25, 0.06, 0.06, 0.02);

    public static final BoxModel MAVIC = new BoxModel()
            .box(GREY_D, -0.1, -0.04, -0.15, 0.2, 0.09, 0.32)
            .box(BLACK, -0.05, -0.1, 0.13, 0.1, 0.07, 0.07)
            .pair(BLACK, 0.06, 0.05, 0.06, 0.22, 0.01, 0.22)
            .pair(BLACK, 0.06, 0.05, -0.28, 0.22, 0.01, 0.22)
            .pair(STEEL_D, 0.03, -0.1, -0.1, 0.02, 0.07, 0.02)
            .group("a1", "root", 0, 0, 0).tilt(0, Math.PI / 4, 0).box(STEEL_D, -0.02, 0, -0.24, 0.04, 0.03, 0.48)
            .group("a2", "root", 0, 0, 0).tilt(0, -Math.PI / 4, 0).box(STEEL_D, -0.02, 0, -0.24, 0.04, 0.03, 0.48)
            .group("grenade", "root", 0, 0, 0).box(OLIVE, -0.05, -0.17, -0.06, 0.1, 0.12, 0.14);

    // ------------------------------------------------------------------ aircraft

    private static final Map<AircraftType, BoxModel> AIRCRAFT = new EnumMap<>(AircraftType.class);
    static {
        AIRCRAFT.put(AircraftType.REAPER, new BoxModel()
                .box(GREY, -0.55, -0.5, -5.0, 1.1, 1.0, 9.0)
                .box(GREY, -0.6, -0.35, 3.6, 1.2, 1.25, 1.9)
                .box(GREY, -0.4, -0.3, 5.5, 0.8, 0.9, 0.5)
                .pair(GREY, 0.55, 0.0, -0.7, 5.0, 0.14, 1.4)
                .pair(GREY, 5.55, 0.03, -0.5, 4.45, 0.1, 1.0)
                .box(GREY, -0.05, -1.5, -4.9, 0.1, 1.0, 0.9)
                .box(GREY_D, -1.3, -0.05, -5.15, 2.6, 0.1, 0.1)
                .box(GREY_D, -0.05, -1.3, -5.15, 0.1, 2.6, 0.1)
                .box(BLACK, -0.3, -0.95, 3.6, 0.6, 0.5, 0.6)
                .pair(STEEL_D, 2.8, -0.35, -0.6, 0.2, 0.2, 1.6)
                .pair(STEEL_D, 4.3, -0.35, -0.6, 0.2, 0.2, 1.6)
                .box(STEEL_D, -0.05, -1.5, 3.2, 0.1, 1.0, 0.1)
                .box(BLACK, -0.08, -1.6, 3.1, 0.16, 0.3, 0.3)
                .pair(STEEL_D, 0.9, -1.5, -0.6, 0.1, 1.2, 0.1)
                .pair(BLACK, 0.87, -1.6, -0.7, 0.16, 0.3, 0.3)
                .group("vl", "root", 0, 0.3, -4.4).tilt(0, 0, 0.78).box(GREY, -0.05, 0.3, -5.0, 0.1, 2.6, 1.1)
                .group("vr", "root", 0, 0.3, -4.4).tilt(0, 0, -0.78).box(GREY, -0.05, 0.3, -5.0, 0.1, 2.6, 1.1));

        AIRCRAFT.put(AircraftType.TB2, new BoxModel()
                .box(GREY, -0.4, -0.4, -2.2, 0.8, 0.8, 5.0)
                .box(GREY, -0.3, -0.3, 2.8, 0.6, 0.6, 0.7)
                .pair(GREY, 0.4, 0.25, -0.4, 5.6, 0.1, 0.9)
                .pair(GREY_D, 1.45, 0.1, -3.3, 0.12, 0.12, 3.0)
                .box(GREY_D, -0.9, -0.04, -2.35, 1.8, 0.08, 0.08)
                .box(GREY_D, -0.04, -0.9, -2.35, 0.08, 1.8, 0.08)
                .box(BLACK, -0.22, -0.75, 1.6, 0.44, 0.4, 0.44)
                .box(STEEL_D, -0.04, -1.0, 2.2, 0.08, 0.65, 0.08)
                .box(BLACK, -0.07, -1.1, 2.1, 0.14, 0.25, 0.25)
                .pair(STEEL_D, 0.6, -1.0, -0.5, 0.08, 0.65, 0.08)
                .pair(BLACK, 0.57, -1.1, -0.6, 0.14, 0.25, 0.25)
                .group("tl", "root", 1.5, 0.15, -3.0).tilt(0, 0, 0.916).box(GREY, 1.47, 0.15, -3.3, 0.06, 1.9, 0.6)
                .group("tr", "root", -1.5, 0.15, -3.0).tilt(0, 0, -0.916).box(GREY, -1.53, 0.15, -3.3, 0.06, 1.9, 0.6));

        AIRCRAFT.put(AircraftType.GLOBAL_HAWK, new BoxModel()
                .box(WHITE, -0.75, -0.7, -6.0, 1.5, 1.4, 10.0)
                .box(WHITE, -1.0, -0.6, 3.6, 2.0, 1.9, 3.0)
                .box(WHITE, -0.7, -0.5, 6.6, 1.4, 1.4, 0.9)
                .box(GREY_D, -0.7, -0.76, -5.0, 1.4, 0.08, 9.0)
                .box(WHITE, -0.65, 0.7, -5.6, 1.3, 1.2, 4.2)
                .box(BLACK, -0.5, 0.85, -1.42, 1.0, 0.9, 0.04)
                .box(BLACK, -0.4, 0.9, -5.63, 0.8, 0.8, 0.04)
                .pair(WHITE, 0.75, -0.25, -1.2, 9.6, 0.2, 1.9)
                .pair(WHITE, 10.35, -0.2, -0.8, 9.6, 0.14, 1.2)
                .box(STEEL_D, -0.06, -1.6, 3.5, 0.12, 0.95, 0.12)
                .box(BLACK, -0.1, -1.7, 3.35, 0.2, 0.36, 0.36)
                .pair(STEEL_D, 1.0, -1.6, -1.2, 0.12, 0.95, 0.12)
                .pair(BLACK, 0.95, -1.7, -1.35, 0.2, 0.36, 0.36)
                .group("vl", "root", 0, 0.5, -5.2).tilt(0, 0, 0.65).box(WHITE, -0.07, 0.5, -5.9, 0.14, 3.4, 1.4)
                .group("vr", "root", 0, 0.5, -5.2).tilt(0, 0, -0.65).box(WHITE, -0.07, 0.5, -5.9, 0.14, 3.4, 1.4));

        AIRCRAFT.put(AircraftType.ORLAN, new BoxModel()
                .box(GREY, -0.13, -0.13, -0.6, 0.26, 0.26, 1.5)
                .box(GREY_D, -0.1, -0.1, 0.9, 0.2, 0.2, 0.15)
                .box(BLACK, -0.35, -0.02, 1.05, 0.7, 0.04, 0.03)
                .box(GREY, -1.55, 0.13, -0.1, 3.1, 0.05, 0.45)
                .pair(RED, 1.4, 0.131, -0.1, 0.15, 0.055, 0.45)
                .box(GREY_D, -0.04, -0.02, -1.1, 0.08, 0.08, 0.6)
                .box(GREY, -0.5, 0.3, -1.15, 1.0, 0.04, 0.25)
                .box(GREY, -0.02, 0.0, -1.15, 0.04, 0.32, 0.25)
                .box(BLACK, -0.08, -0.25, 0.1, 0.16, 0.12, 0.16));
    }

    public static BoxModel aircraft(AircraftType type) { return AIRCRAFT.get(type); }

    // ------------------------------------------------------------------ vehicles

    private static final Map<WeaponType, BoxModel> WEAPONS = new EnumMap<>(WeaponType.class);
    static {
        BoxModel himars = new BoxModel()
                .pair(TYRE, 0.85, 0, 0.6, 0.35, 1.0, 1.0).pair(TYRE, 0.85, 0, 1.9, 0.35, 1.0, 1.0).pair(TYRE, 0.85, 0, 5.3, 0.35, 1.0, 1.0)
                .box(OLIVE_D, -1.0, 0.5, 0, 2.0, 0.7, 7.0)
                .box(OLIVE, -1.2, 1.2, 4.8, 2.4, 1.5, 2.2)
                .box(GLASS, -1.0, 2.0, 6.98, 2.0, 0.55, 0.05)
                .pair(GLASS, 1.18, 2.0, 5.4, 0.04, 0.5, 1.2)
                .box(STEEL_D, -1.2, 0.6, 6.9, 2.4, 0.4, 0.15)
                .group("turret", "root", 0, 1.2, 2.0).box(OLIVE_D, -1.0, 1.2, 1.0, 2.0, 0.4, 2.2)
                .group("gun", "turret", 0, 1.7, 1.0).box(OLIVE, -1.15, 1.6, 0.2, 2.3, 1.1, 4.4);
        for (int i = 0; i < 3; i++) {
            for (int j = 0; j < 2; j++) {
                himars.box(BLACK, -0.95 + i * 0.68, 1.68 + j * 0.52, 4.58, 0.55, 0.44, 0.04);
                himars.box(BLACK, -0.95 + i * 0.68, 1.68 + j * 0.52, 0.18, 0.55, 0.44, 0.04);
            }
        }
        WEAPONS.put(WeaponType.HIMARS, himars);

        WEAPONS.put(WeaponType.M109, new BoxModel()
                .pair(TYRE, 1.05, 0, 0.1, 0.5, 1.0, 6.0)
                .box(OLIVE, -1.5, 0.5, 0, 3.0, 1.2, 6.2)
                .box(OLIVE_D, -1.4, 0.9, 6.2, 2.8, 0.6, 0.4)
                .group("turret", "root", 0, 1.7, 2.2)
                .box(OLIVE_L, -1.4, 1.7, 0.3, 2.8, 1.25, 3.7)
                .box(OLIVE_D, 0.5, 2.95, 1.0, 0.7, 0.3, 0.7)
                .box(OLIVE_D, -1.3, 1.8, 0.0, 2.6, 0.8, 0.3)
                .group("gun", "turret", 0, 2.3, 4.0)
                .box(OLIVE_D, -0.4, 2.0, 3.9, 0.8, 0.6, 0.5)
                .box(STEEL_D, -0.12, 2.18, 4.4, 0.24, 0.24, 4.6)
                .box(STEEL_D, -0.17, 2.13, 6.5, 0.34, 0.34, 0.6)
                .box(STEEL_D, -0.2, 2.1, 8.6, 0.4, 0.4, 0.5));

        BoxModel tomahawk = new BoxModel()
                .pair(TYRE, 0.85, 0, 0.8, 0.35, 1.0, 1.0).pair(TYRE, 0.85, 0, 2.0, 0.35, 1.0, 1.0)
                .pair(STEEL_D, 0.8, 0, 7.6, 0.2, 0.9, 0.2)
                .box(OLIVE_D, -1.2, 0.9, 0, 2.4, 0.3, 9.0)
                .box(OLIVE, -1.1, 1.2, 8.0, 2.2, 1.2, 0.95)
                .group("gun", "root", 0, 1.2, 0.6)
                .box(OLIVE, -1.1, 1.2, 0.6, 2.2, 1.9, 7.3)
                .box(OLIVE_D, -1.14, 1.2, 2.5, 2.28, 1.94, 0.12)
                .box(OLIVE_D, -1.14, 1.2, 5.0, 2.28, 1.94, 0.12);
        for (int i = 0; i < 2; i++) {
            for (int j = 0; j < 2; j++) tomahawk.box(STEEL, -0.95 + i * 1.0, 1.35 + j * 0.85, 7.9, 0.9, 0.75, 0.04);
        }
        WEAPONS.put(WeaponType.TOMAHAWK_LAUNCHER, tomahawk);

        WEAPONS.put(WeaponType.ISKANDER_LAUNCHER, new BoxModel()
                .pair(TYRE, 1.0, 0, 1.0, 0.5, 1.3, 1.2).pair(TYRE, 1.0, 0, 2.6, 0.5, 1.3, 1.2)
                .pair(TYRE, 1.0, 0, 8.2, 0.5, 1.3, 1.2).pair(TYRE, 1.0, 0, 9.8, 0.5, 1.3, 1.2)
                .box(OLIVE, -1.5, 0.8, 0, 3.0, 1.4, 13.0)
                .box(OLIVE, -1.5, 2.2, 10.8, 3.0, 0.9, 2.2)
                .box(GLASS, -1.3, 2.4, 12.98, 2.6, 0.55, 0.05)
                .pair(GLASS, 1.48, 2.4, 11.3, 0.04, 0.5, 1.2)
                .pair(OLIVE_D, 1.2, 2.2, 0.3, 0.3, 0.9, 10.2)
                .box(OLIVE_D, -1.5, 2.2, 0, 3.0, 0.9, 0.3)
                .group("gun", "root", 0, 2.25, 1.0)
                .box(STEEL_D, -0.6, 2.2, 1.0, 1.2, 0.08, 5.0));

        WEAPONS.put(WeaponType.SHAHED_LAUNCHER, new BoxModel()
                .pair(STEEL_D, 0.9, 0, 0, 0.2, 0.2, 5.0)
                .box(STEEL_D, -1.1, 0.1, 0.3, 2.2, 0.15, 0.2)
                .box(STEEL_D, -1.1, 0.1, 4.5, 2.2, 0.15, 0.2)
                .pair(STEEL_D, 0.3, 0.2, 4.3, 0.12, 1.6, 0.12)
                .box(STEEL_D, -0.42, 1.7, 4.3, 0.84, 0.1, 0.12)
                .group("rail", "root", 0, 0.4, 0.3).tilt(-D18, 0, 0)
                .box(STEEL, -0.15, 0.4, 0.3, 0.3, 0.2, 4.6));

        WEAPONS.put(WeaponType.GROUND_STATION, new BoxModel()
                .pair(STEEL_D, 1.0, 0, 0.2, 0.2, 0.2, 0.2).pair(STEEL_D, 1.0, 0, 5.6, 0.2, 0.2, 0.2)
                .box(SAND, -1.2, 0.2, 0, 2.4, 2.5, 6.0)
                .box(SAND_D, -0.45, 0.3, -0.03, 0.9, 2.0, 0.04)
                .box(SAND_D, -1.22, 1.3, 0.5, 2.44, 0.15, 5.0)
                .box(STEEL, -1.0, 1.4, 6.0, 0.9, 0.9, 0.4)
                .box(STEEL_D, -0.05, 2.7, 4.8, 0.1, 1.6, 0.1)
                .box(STEEL_D, 0.9, 2.7, 0.5, 0.05, 2.2, 0.05)
                .group("dish", "root", 0, 4.3, 4.8).tilt(-0.6, 0, 0)
                .box(WHITE, -0.9, 3.6, 4.7, 1.8, 1.4, 0.15)
                .box(STEEL_D, -0.05, 4.25, 4.85, 0.1, 0.1, 0.6));

        WEAPONS.put(WeaponType.ORLAN_CATAPULT, new BoxModel()
                .box(STEEL_D, -0.3, 0, 0.2, 0.6, 0.3, 0.15)
                .pair(STEEL_D, 0.2, 0, 3.3, 0.08, 1.15, 0.08)
                .box(STEEL_D, -0.28, 1.1, 3.3, 0.56, 0.06, 0.08)
                .group("rail", "root", 0, 0.3, 0.2).tilt(-D15, 0, 0)
                .box(STEEL, -0.08, 0.3, 0.2, 0.16, 0.12, 3.7));



        WEAPONS.put(WeaponType.JAMMER, new BoxModel()
                .box(OLIVE, -1.4, 0.2, 0.1, 2.8, 2.4, 2.8)
                .box(OLIVE_D, -0.4, 0.3, 0.07, 0.8, 1.9, 0.04)
                .pair(STEEL_D, 1.1, 0, 0.3, 0.2, 0.2, 0.2).pair(STEEL_D, 1.1, 0, 2.5, 0.2, 0.2, 0.2)
                .box(STEEL_D, -0.1, 2.6, 1.4, 0.2, 6.4, 0.2)
                .box(STEEL_D, -1.5, 7.0, 1.45, 3.0, 0.1, 0.1)
                .box(STEEL_D, -0.05, 8.2, 0.0, 0.1, 0.1, 3.0)
                .pair(OLIVE_D, 1.3, 6.6, 1.2, 0.12, 0.9, 0.6)
                .box(OLIVE_D, -0.3, 7.8, 0.0, 0.6, 0.9, 0.12)
                .box(OLIVE_D, -0.3, 7.8, 2.88, 0.6, 0.9, 0.12)
                .box(RED, -0.08, 9.0, 1.42, 0.16, 0.16, 0.16));



        WEAPONS.put(WeaponType.TRENCH_JAMMER, new BoxModel()
                .box(OLIVE, -0.35, 0, 0.15, 0.7, 0.6, 0.7)
                .box(OLIVE_D, -0.3, 0.6, 0.2, 0.6, 0.06, 0.6)
                .box(STEEL_D, -0.04, 0.66, 0.46, 0.08, 1.2, 0.08)
                .box(STEEL_D, -0.3, 1.5, 0.48, 0.6, 0.04, 0.04)
                .box(STEEL_D, -0.02, 1.3, 0.2, 0.04, 0.04, 0.6)
                .box(RED, -0.05, 1.86, 0.45, 0.1, 0.1, 0.1));

        WEAPONS.put(WeaponType.KRASUKHA, new BoxModel()
                .pair(TYRE, 0.9, 0, 0.8, 0.4, 1.2, 1.2).pair(TYRE, 0.9, 0, 2.2, 0.4, 1.2, 1.2)
                .pair(TYRE, 0.9, 0, 7.0, 0.4, 1.2, 1.2).pair(TYRE, 0.9, 0, 8.4, 0.4, 1.2, 1.2)
                .box(OLIVE_D, -1.0, 0.6, 0, 2.0, 0.8, 11.0)
                .box(OLIVE, -1.25, 1.4, 9.0, 2.5, 1.6, 2.0)
                .box(GLASS, -1.05, 2.2, 10.98, 2.1, 0.6, 0.05)
                .pair(GLASS, 1.23, 2.2, 9.5, 0.04, 0.55, 1.0)
                .box(OLIVE, -1.25, 1.4, 3.0, 2.5, 1.9, 5.6)
                .box(OLIVE_D, -0.6, 3.3, 5.0, 1.2, 0.5, 1.2)
                .box(STEEL_D, -0.12, 1.4, 1.4, 0.24, 2.4, 0.24)
                .group("dish", "root", 0, 3.6, 1.5).tilt(-0.45, 0, 0)
                .box(OLIVE_L, -1.5, 2.4, 1.2, 3.0, 2.6, 0.25)
                .box(OLIVE_D, -1.3, 2.6, 1.45, 2.6, 2.2, 0.06)
                .box(STEEL_D, -0.06, 3.6, 1.45, 0.12, 0.12, 0.9));
    }

    public static BoxModel weapon(WeaponType type) { return WEAPONS.get(type); }

    private Models() {}
}

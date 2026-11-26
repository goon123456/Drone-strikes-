package com.steelrain.client;

import com.steelrain.weapon.AircraftType;

/**
 * Drones and aircraft, modelled from photographs at real-world scale (1 block = 1 metre) and painted as the
 * real ones are. Origin on the fuselage axis, +Z is the nose, +X the left wing.
 * Animated groups: "prop" spins around Z, "r1".."r4" spin around Y, "gearN/L/R" fold back around X.
 */
final class AirModels {
    private static final int REAPER_GREY = 0xA2A8AE, REAPER_DARK = 0x7C8288, TB2_GREY = 0xB9BDC0, TB2_DARK = 0x8C9196,
            HAWK_GREY = 0x6B7178, HAWK_WHITE = 0xDCDDD8, ORLAN_GREY = 0x9EA29E, SHAHED_WHITE = 0xD2D0C6,
            SHAHED_DARK = 0x9C9A90, STEEL = 0x8A8E94, STEEL_D = 0x3E4246, TYRE = 0x1E1E20, BLACK = 0x161618,
            GLASS = 0x1C2C3C, CARBON = 0x2A2A2E, CARBON_L = 0x3A3A40, HELLFIRE = 0x4C5444, YELLOW = 0xC8A82C,
            ORANGE = 0xD6582C, CYAN = 0x40C8FF, MAVIC_GREY = 0x6E7074, MAVIC_DARK = 0x4A4C50, OLIVE = 0x5C683E,
            BRASS = 0xC4A64C, RED = 0xBE2824, LIPO = 0x2C3A8C, SPOOL = 0xE4E4DC, COPPER = 0xB87040;

    // ------------------------------------------------------------------ shared parts

    /** A landing-gear leg that folds backwards: strut from {@code top} down to a wheel resting at {@code ground}. */
    private static BoxModel gear(BoxModel m, String name, double x, double z, double top, double ground, double wheel, boolean twin) {
        m.group(name, "root", x, top, z);
        m.box(STEEL, x - 0.05, ground + wheel / 2, z - 0.05, 0.1, top - ground - wheel / 2, 0.1);
        m.box(STEEL_D, x - 0.07, top - 0.25, z - 0.07, 0.14, 0.25, 0.14);
        double w = wheel * 0.38;
        if (twin) {
            m.wheel(TYRE, x - w - 0.07, x - 0.07, ground + wheel / 2, z, wheel);
            m.wheel(TYRE, x + 0.07, x + w + 0.07, ground + wheel / 2, z, wheel);
            m.wheel(STEEL, x - 0.07, x + 0.07, ground + wheel / 2, z, wheel * 0.3);
        } else {
            m.wheel(TYRE, x - w / 2, x + w / 2, ground + wheel / 2, z, wheel);
            m.wheel(STEEL, x - w / 2 - 0.01, x + w / 2 + 0.01, ground + wheel / 2, z, wheel * 0.45);
        }
        return m.in("root");
    }

    /** A propeller that spins around the fuselage axis. */
    private static BoxModel prop(BoxModel m, double y, double z, double diameter, int blades, int colour) {
        m.group("prop", "root", 0, y, z);
        double r = diameter / 2, w = diameter * 0.045, t = Math.max(0.015, diameter * 0.012);
        for (int i = 0; i < blades; i++) {
            double a = Math.PI * 2 * i / blades, s = Math.sin(a), c = Math.cos(a);
            // a blade from the hub out to the tip, wider in the middle than at the tip
            m.hex(colour, new double[][]{
                    {-c * w, y + s * w, z - t}, {c * w, y - s * w, z - t}, {c * w, y - s * w, z + t}, {-c * w, y + s * w, z + t},
                    {s * r - c * w * 0.6, y + c * r + s * w * 0.6, z - t}, {s * r + c * w * 0.6, y + c * r - s * w * 0.6, z - t},
                    {s * r + c * w * 0.6, y + c * r - s * w * 0.6, z + t}, {s * r - c * w * 0.6, y + c * r + s * w * 0.6, z + t}});
        }
        return m.in("root");
    }

    /** A small rotor that spins around a vertical motor shaft. */
    private static BoxModel rotor(BoxModel m, String name, double x, double y, double z, double diameter, int colour) {
        m.group(name, "root", x, y, z);
        double r = diameter / 2;
        m.box(colour, x - r, y, z - 0.012, diameter, 0.006, 0.024);
        m.box(colour, x - 0.012, y + 0.001, z - r, 0.024, 0.006, diameter);
        return m.in("root");
    }

    private static void hellfire(BoxModel m, double x, double y) {
        for (int side = -1; side <= 1; side += 2) {
            double sx = x * side;
            m.box(REAPER_DARK, sx - 0.05, y, -0.75, 0.1, 0.32, 0.9);            // pylon
            m.box(STEEL_D, sx - 0.2, y - 0.06, -0.8, 0.4, 0.06, 1.0);           // twin rail
            for (double off : new double[]{-0.13, 0.13}) {
                m.tube(HELLFIRE, sx + off, y - 0.15, -1.0, 0.45, 0.17);
                m.tube(BLACK, 0.45, sx + off, y - 0.15, 0.17, 0.17, 0.62, sx + off, y - 0.15, 0.07, 0.07);
                m.box(HELLFIRE, sx + off - 0.16, y - 0.16, -0.98, 0.32, 0.02, 0.22);
                m.box(HELLFIRE, sx + off - 0.01, y - 0.31, -0.98, 0.02, 0.32, 0.22);
            }
        }
    }

    // ------------------------------------------------------------------ MQ-9 Reaper: 11 m long, 20 m span

    static BoxModel reaper() {
        int g = REAPER_GREY;
        BoxModel m = new BoxModel()
                .tube(REAPER_DARK, 5.5, 0, -0.1, 0.22, 0.26, 5.0, 0, 0.0, 0.7, 0.75)       // nose cap
                .tube(g, 5.0, 0, 0.0, 0.7, 0.75, 3.8, 0, 0.1, 1.0, 1.2)                    // the bulge over the satellite dish
                .tube(g, 3.8, 0, 0.1, 1.0, 1.2, 1.8, 0, 0.02, 0.98, 0.98)
                .tube(g, 1.8, 0, 0.02, 0.98, 0.98, -3.4, 0, 0.0, 0.92, 0.9)
                .tube(g, -3.4, 0, 0.0, 0.92, 0.9, -4.9, 0, 0.03, 0.62, 0.62)               // engine cowl
                .tube(STEEL_D, -4.9, 0, 0.03, 0.4, 0.4, -5.3, 0, 0.03, 0.12, 0.12)         // spinner
                .loft(REAPER_DARK, -2.3, 0, 0.5, 0.4, 0.22, -3.7, 0, 0.42, 0.32, 0.12)     // engine air scoop
                .box(BLACK, -0.16, 0.46, -2.3, 0.32, 0.14, 0.02)
                .box(STEEL_D, -0.12, -0.52, -4.6, 0.24, 0.1, 0.5)                          // exhaust
                // long, slender, slightly raised wings
                .wings(g, 0.42, -0.2, 0.6, 1.5, 0.2, 5.5, 0.12, 0.3, 1.05, 0.13)
                .wings(g, 5.5, 0.12, 0.3, 1.05, 0.13, 10.0, 0.5, 0.0, 0.6, 0.06)
                .wings(REAPER_DARK, 10.0, 0.5, 0.0, 0.6, 0.06, 10.15, 0.95, -0.2, 0.3, 0.04)   // winglets
                // V tail and the fin underneath
                .wings(g, 0.3, 0.25, -3.2, 1.45, 0.14, 2.3, 2.35, -4.2, 0.75, 0.07)
                .wing(g, 0, -0.35, -3.6, 1.2, 0.12, 0, -1.4, -4.2, 0.6, 0.06)
                // camera ball under the nose
                .drum(STEEL_D, 0, 4.25, -0.6, 0.42, -0.45, 0.42)
                .drum(BLACK, 0, 4.25, -0.98, 0.3, -0.82, 0.56).drum(BLACK, 0, 4.25, -0.82, 0.56, -0.6, 0.5)
                .box(GLASS, -0.1, -0.9, 4.48, 0.2, 0.2, 0.06)
                // aerials
                .box(STEEL_D, -0.02, 0.5, 0.6, 0.04, 0.3, 0.25).box(STEEL_D, -0.02, -0.75, 1.2, 0.04, 0.3, 0.2);
        hellfire(m, 2.4, -0.42);
        hellfire(m, 3.9, -0.32);
        gear(m, "gearN", 0, 3.5, -0.4, -1.6, 0.42, false);
        gear(m, "gearL", 1.25, -0.9, -0.25, -1.6, 0.5, false);
        gear(m, "gearR", -1.25, -0.9, -0.25, -1.6, 0.5, false);
        return prop(m, 0.03, -5.15, 2.7, 3, STEEL_D);
    }

    // ------------------------------------------------------------------ Bayraktar TB2: 6.5 m long, 12 m span

    static BoxModel tb2() {
        int g = TB2_GREY;
        BoxModel m = new BoxModel()
                .tube(TB2_DARK, 3.25, 0, -0.05, 0.18, 0.2, 2.8, 0, 0.0, 0.6, 0.5)
                .tube(g, 2.8, 0, 0.0, 0.6, 0.5, 1.4, 0, 0.05, 0.9, 0.72)                   // wide, flattened body
                .tube(g, 1.4, 0, 0.05, 0.9, 0.72, -0.6, 0, 0.05, 0.95, 0.7)
                .tube(g, -0.6, 0, 0.05, 0.95, 0.7, -1.9, 0, 0.1, 0.6, 0.55)
                .tube(STEEL_D, -1.9, 0, 0.1, 0.5, 0.5, -2.3, 0, 0.1, 0.38, 0.38)           // pusher engine
                .box(STEEL_D, -0.3, -0.05, -2.2, 0.6, 0.12, 0.3)                           // cylinders
                .tube(STEEL_D, -2.3, 0, 0.1, 0.2, 0.2, -2.5, 0, 0.1, 0.08, 0.08)
                .box(STEEL_D, -0.02, 0.4, 1.0, 0.04, 0.5, 0.06)                            // mast aerial
                // blended wing roots, then long tapered panels with upturned tips
                .wings(g, 0.4, 0.15, 0.75, 1.5, 0.22, 1.5, 0.2, 0.45, 1.05, 0.16)
                .wings(g, 1.5, 0.2, 0.45, 1.05, 0.16, 5.7, 0.38, 0.2, 0.55, 0.07)
                .wings(g, 5.7, 0.38, 0.2, 0.55, 0.07, 6.0, 0.6, 0.05, 0.3, 0.04)
                // twin booms carrying the inverted-V tail
                .pair(g, 1.44, 0.14, -3.5, 0.12, 0.12, 3.3)
                .wings(g, 1.5, 0.2, -2.75, 0.8, 0.07, 0.0, 1.35, -3.0, 0.55, 0.06)
                // camera turret
                .drum(STEEL_D, 0, 1.9, -0.42, 0.34, -0.3, 0.34)
                .drum(BLACK, 0, 1.9, -0.74, 0.26, -0.62, 0.44).drum(BLACK, 0, 1.9, -0.62, 0.44, -0.42, 0.4)
                .box(GLASS, -0.08, -0.68, 2.08, 0.16, 0.16, 0.05);
        // four small laser-guided munitions under the wings
        for (int side = -1; side <= 1; side += 2) {
            for (double x : new double[]{2.0, 2.9}) {
                double sx = x * side;
                m.box(TB2_DARK, sx - 0.04, -0.02, -0.25, 0.08, 0.22, 0.5);
                m.tube(HAWK_GREY, sx, -0.12, -0.5, 0.35, 0.16);
                m.tube(STEEL_D, 0.35, sx, -0.12, 0.16, 0.16, 0.5, sx, -0.12, 0.06, 0.06);
                m.box(HAWK_GREY, sx - 0.15, -0.13, -0.5, 0.3, 0.02, 0.18).box(HAWK_GREY, sx - 0.01, -0.27, -0.5, 0.02, 0.3, 0.18);
            }
        }
        gear(m, "gearN", 0, 2.2, -0.2, -1.1, 0.3, false);
        gear(m, "gearL", 0.75, -0.5, -0.15, -1.1, 0.36, false);
        gear(m, "gearR", -0.75, -0.5, -0.15, -1.1, 0.36, false);
        return prop(m, 0.1, -2.42, 1.5, 2, STEEL_D);
    }

    // ------------------------------------------------------------------ RQ-4 Global Hawk: 14.5 m long, 40 m span

    static BoxModel globalHawk() {
        int g = HAWK_GREY, w = HAWK_WHITE;
        BoxModel m = new BoxModel()
                // the whale-shaped nose hides a big satellite dish
                .tube(g, 7.25, 0, 0.0, 0.5, 0.5, 6.6, 0, 0.2, 1.4, 1.45)
                .tube(g, 6.6, 0, 0.2, 1.4, 1.45, 5.2, 0, 0.38, 1.75, 2.0)
                .tube(g, 5.2, 0, 0.38, 1.75, 2.0, 3.2, 0, 0.25, 1.6, 1.7)
                .tube(g, 3.2, 0, 0.25, 1.6, 1.7, 1.0, 0, 0.0, 1.45, 1.3)
                .tube(g, 1.0, 0, 0.0, 1.45, 1.3, -4.6, 0, 0.0, 1.35, 1.2)
                .tube(g, -4.6, 0, 0.0, 1.35, 1.2, -7.2, 0, 0.15, 0.55, 0.5)
                .tube(w, 5.0, 0, -0.35, 1.2, 0.5, -4.5, 0, -0.42, 1.0, 0.4)                // pale belly
                // jet engine on the back, between the tail fins
                .tube(g, 0.3, 0, 1.3, 1.25, 1.25, -1.0, 0, 1.3, 1.4, 1.4)
                .tube(g, -1.0, 0, 1.3, 1.4, 1.4, -4.6, 0, 1.2, 1.2, 1.2)
                .tube(g, -4.6, 0, 1.2, 1.2, 1.2, -5.4, 0, 1.15, 0.85, 0.85)
                .tube(BLACK, 0.34, 0, 1.3, 1.0, 1.0, 0.2, 0, 1.3, 1.0, 1.0)                // intake
                .tube(STEEL_D, -5.3, 0, 1.15, 0.7, 0.7, -5.5, 0, 1.15, 0.6, 0.6)           // exhaust
                .loft(g, 0.6, 0, 0.75, 0.9, 0.5, -4.6, 0, 0.7, 0.8, 0.4)                   // pylon joining engine and body
                // enormous glider-like wings
                .wings(w, 0.6, -0.35, 0.9, 2.4, 0.3, 4.0, -0.15, 0.6, 1.9, 0.22)
                .wings(w, 4.0, -0.15, 0.6, 1.9, 0.22, 12.0, 0.4, -0.1, 1.2, 0.13)
                .wings(w, 12.0, 0.4, -0.1, 1.2, 0.13, 19.9, 1.1, -0.8, 0.65, 0.06)
                // V tail
                .wings(g, 0.45, 0.35, -4.4, 1.9, 0.16, 2.9, 3.3, -5.9, 0.95, 0.08)
                // sensor fairings under the chin and belly
                .tube(STEEL_D, 6.0, 0, -0.62, 0.7, 0.35, 5.0, 0, -0.68, 0.8, 0.4)
                .drum(BLACK, 0, 5.6, -1.0, 0.3, -0.84, 0.5)
                .box(STEEL_D, -0.03, 1.9, -2.5, 0.06, 0.4, 0.3);
        gear(m, "gearN", 0, 4.3, -0.5, -1.7, 0.45, true);
        gear(m, "gearL", 1.6, -1.2, -0.3, -1.7, 0.6, true);
        gear(m, "gearR", -1.6, -1.2, -0.3, -1.7, 0.6, true);
        return m;
    }

    // ------------------------------------------------------------------ Orlan-10: 2 m long, 3.1 m span

    static BoxModel orlan() {
        int g = ORLAN_GREY;
        BoxModel m = new BoxModel()
                .tube(STEEL_D, 0.98, 0, 0.0, 0.1, 0.1, 0.8, 0, 0.0, 0.2, 0.2)              // engine
                .box(BLACK, -0.17, -0.04, 0.8, 0.34, 0.08, 0.1)                            // its two cylinders
                .tube(g, 0.8, 0, 0.0, 0.22, 0.24, 0.45, 0, -0.01, 0.27, 0.32)
                .tube(g, 0.45, 0, -0.01, 0.27, 0.32, -0.15, 0, -0.01, 0.26, 0.3)
                .tube(g, -0.15, 0, -0.01, 0.26, 0.3, -1.0, 0, 0.06, 0.08, 0.09)            // slim tail boom
                .box(BLACK, -0.07, -0.26, 0.15, 0.14, 0.1, 0.2)                            // camera window
                .box(GLASS, -0.05, -0.27, 0.2, 0.1, 0.02, 0.1)
                .box(STEEL_D, -0.01, 0.16, -0.2, 0.02, 0.22, 0.02)                         // aerial
                // high wing with a kink and raised tips
                .box(g, -0.14, 0.14, -0.05, 0.28, 0.06, 0.42)
                .wings(g, 0.0, 0.19, 0.38, 0.44, 0.05, 0.75, 0.2, 0.36, 0.38, 0.045)
                .wings(g, 0.75, 0.2, 0.36, 0.38, 0.045, 1.55, 0.31, 0.3, 0.24, 0.03)
                // tail
                .wing(g, 0, 0.08, -0.72, 0.3, 0.025, 0, 0.42, -0.86, 0.18, 0.02)
                .wings(g, 0.0, 0.1, -0.74, 0.26, 0.022, 0.45, 0.1, -0.78, 0.18, 0.018);
        return prop(m, 0.0, 1.0, 0.55, 2, BLACK);
    }

    // ------------------------------------------------------------------ Shahed-136: 3.5 m long, 2.5 m span

    static BoxModel shahed() {
        int g = SHAHED_WHITE;
        BoxModel m = new BoxModel()
                .tube(SHAHED_DARK, 1.75, 0, 0.0, 0.1, 0.1, 1.55, 0, 0.0, 0.3, 0.3)         // nose
                .tube(g, 1.55, 0, 0.0, 0.3, 0.3, 0.9, 0, 0.0, 0.46, 0.46)
                .tube(g, 0.9, 0, 0.0, 0.46, 0.46, -1.2, 0, 0.0, 0.44, 0.42)
                .tube(STEEL_D, -1.2, 0, 0.0, 0.36, 0.36, -1.55, 0, 0.0, 0.3, 0.3)          // engine
                .box(STEEL_D, -0.3, -0.06, -1.5, 0.6, 0.12, 0.25)                          // flat-four cylinders
                .box(BLACK, -0.32, -0.04, -1.45, 0.64, 0.08, 0.15)
                // cropped delta wing blended into the body
                .wings(g, 0.0, 0.0, 1.05, 2.45, 0.2, 0.55, 0.0, 0.2, 1.6, 0.12)
                .wings(g, 0.55, 0.0, 0.2, 1.6, 0.12, 1.25, 0.0, -0.9, 0.5, 0.05)
                .wings(SHAHED_DARK, 0.55, 0.005, -1.15, 0.25, 0.1, 1.25, 0.005, -1.2, 0.2, 0.05)   // elevons
                // fins on the wing tips, above and below
                .wings(g, 1.25, -0.32, -0.95, 0.45, 0.04, 1.25, 0.38, -0.95, 0.45, 0.04)
                .wings(g, 1.25, 0.38, -0.95, 0.45, 0.04, 1.25, 0.5, -1.1, 0.28, 0.03)
                .box(STEEL_D, -0.01, 0.2, 0.3, 0.02, 0.12, 0.06);
        return prop(m, 0.0, -1.62, 0.8, 2, COPPER);
    }

    // ------------------------------------------------------------------ racing-style attack quadcopter

    static BoxModel fpv(boolean fiber) {
        int propColour = fiber ? CYAN : ORANGE;
        BoxModel m = new BoxModel()
                .box(CARBON, -0.05, 0.0, -0.13, 0.1, 0.012, 0.28)                          // bottom plate
                .box(CARBON, -0.045, 0.045, -0.11, 0.09, 0.01, 0.24)                       // top plate
                .pair(STEEL, 0.035, 0.012, 0.09, 0.008, 0.033, 0.008).pair(STEEL, 0.035, 0.012, -0.09, 0.008, 0.033, 0.008)
                .box(CARBON_L, -0.03, 0.014, -0.06, 0.06, 0.028, 0.1)                      // flight electronics
                .box(LIPO, -0.04, 0.056, -0.09, 0.08, 0.04, 0.17)                          // battery on top
                .box(BLACK, -0.045, 0.07, -0.03, 0.09, 0.03, 0.03)                         // strap
                .box(BLACK, -0.028, 0.012, 0.13, 0.056, 0.04, 0.035)                       // camera
                .box(GLASS, -0.014, 0.022, 0.165, 0.028, 0.022, 0.012)
                .box(STEEL_D, -0.004, 0.045, -0.15, 0.008, 0.11, 0.008)                    // video aerial
                .drum(BLACK, 0, -0.146, 0.155, 0.035, 0.185, 0.035)
                .pair(STEEL_D, 0.03, 0.03, -0.2, 0.004, 0.004, 0.09)                       // control aerials
                // shaped-charge warhead slung underneath, nose forward
                .tube(OLIVE, 0, -0.055, -0.14, 0.02, 0.05)
                .tube(OLIVE, 0.02, 0, -0.055, 0.05, 0.05, 0.1, 0, -0.055, 0.09, 0.09)
                .tube(OLIVE, 0.1, 0, -0.055, 0.09, 0.09, 0.2, 0, -0.055, 0.085, 0.085)
                .tube(BRASS, 0.2, 0, -0.055, 0.085, 0.085, 0.3, 0, -0.055, 0.015, 0.015)
                .box(BLACK, -0.05, -0.04, 0.0, 0.1, 0.045, 0.012).box(BLACK, -0.05, -0.04, 0.13, 0.1, 0.045, 0.012);
        if (fiber) {
            // the cable reel hangs behind the warhead and pays out backwards
            m.tube(SPOOL, 0, -0.07, -0.33, -0.17, 0.13)
                    .tube(STEEL_D, 0, -0.07, -0.34, -0.33, 0.15).tube(STEEL_D, 0, -0.07, -0.17, -0.16, 0.15)
                    .box(STEEL_D, -0.01, -0.02, -0.3, 0.02, 0.03, 0.12);
        }
        // four arms in an X, each with a motor
        double a = 0.2;
        int i = 1;
        for (int sx = -1; sx <= 1; sx += 2) {
            for (int sz = -1; sz <= 1; sz += 2) {
                double x = a * sx, z = a * sz + 0.01;
                m.hex(CARBON, new double[][]{
                        {0.02 * sx - 0.015 * sz, 0.0, 0.02 * sz + 0.015 * sx + 0.01}, {0.02 * sx + 0.015 * sz, 0.0, 0.02 * sz - 0.015 * sx + 0.01},
                        {0.02 * sx + 0.015 * sz, 0.014, 0.02 * sz - 0.015 * sx + 0.01}, {0.02 * sx - 0.015 * sz, 0.014, 0.02 * sz + 0.015 * sx + 0.01},
                        {x - 0.013 * sz, 0.0, z + 0.013 * sx}, {x + 0.013 * sz, 0.0, z - 0.013 * sx},
                        {x + 0.013 * sz, 0.014, z - 0.013 * sx}, {x - 0.013 * sz, 0.014, z + 0.013 * sx}});
                m.drum(STEEL_D, x, z, 0.014, 0.045, 0.04, 0.045);
                m.drum(RED, x, z, 0.04, 0.02, 0.048, 0.02);
                rotor(m, "r" + i++, x, 0.048, z, 0.18, propColour);
            }
        }
        return m;
    }

    // ------------------------------------------------------------------ DJI Mavic 3 carrying one grenade

    static BoxModel mavic() {
        int g = MAVIC_GREY;
        BoxModel m = new BoxModel()
                .loft(g, 0.17, 0, 0.0, 0.085, 0.06, 0.1, 0, 0.005, 0.11, 0.085)            // nose
                .loft(g, 0.1, 0, 0.005, 0.11, 0.085, -0.12, 0, 0.005, 0.105, 0.09)
                .loft(MAVIC_DARK, -0.12, 0, 0.005, 0.105, 0.09, -0.19, 0, 0.01, 0.08, 0.065)   // battery tail
                .box(MAVIC_DARK, -0.04, 0.05, -0.1, 0.08, 0.008, 0.16)
                .pair(BLACK, 0.02, 0.012, 0.171, 0.022, 0.012, 0.004)                      // obstacle sensors
                // gimbal camera under the nose
                .box(BLACK, -0.03, -0.065, 0.12, 0.06, 0.03, 0.04)
                .box(BLACK, -0.035, -0.1, 0.13, 0.07, 0.045, 0.06).box(GLASS, -0.022, -0.092, 0.19, 0.044, 0.03, 0.006);
        // front arms reach forwards and out, rear arms sit lower and reach back
        int i = 1;
        for (int sx = -1; sx <= 1; sx += 2) {
            double fx = 0.2 * sx, fz = 0.16, rx = 0.2 * sx, rz = -0.19;
            m.hex(MAVIC_DARK, arm(0.05 * sx, 0.02, 0.1, fx, 0.035, fz, 0.016));
            m.hex(MAVIC_DARK, arm(0.05 * sx, -0.01, -0.1, rx, -0.005, rz, 0.016));
            m.drum(g, fx, fz, 0.035, 0.04, 0.062, 0.04).drum(g, rx, rz, -0.005, 0.04, 0.022, 0.04);
            m.box(MAVIC_DARK, fx - 0.008, -0.07, fz - 0.008, 0.016, 0.105, 0.016);         // landing legs
            m.box(MAVIC_DARK, rx - 0.006, -0.07, rz - 0.006, 0.012, 0.065, 0.012);
            rotor(m, "r" + i++, fx, 0.062, fz, 0.24, BLACK);
            rotor(m, "r" + i++, rx, 0.022, rz, 0.24, BLACK);
        }
        // drop mechanism and its grenade
        m.box(ORANGE, -0.025, -0.055, -0.04, 0.05, 0.015, 0.07);
        m.group("grenade", "root", 0, 0, 0)
                .drum(OLIVE, 0, -0.005, -0.15, 0.03, -0.13, 0.055).drum(OLIVE, 0, -0.005, -0.13, 0.055, -0.075, 0.055)
                .drum(BRASS, 0, -0.005, -0.075, 0.03, -0.055, 0.03)
                .box(YELLOW, -0.029, -0.115, -0.034, 0.058, 0.008, 0.058);
        return m.in("root");
    }

    /** A straight arm of square section between two points. */
    private static double[][] arm(double x0, double y0, double z0, double x1, double y1, double z1, double t) {
        double dx = x1 - x0, dz = z1 - z0, len = Math.hypot(dx, dz), nx = -dz / len * t / 2, nz = dx / len * t / 2;
        return new double[][]{
                {x0 - nx, y0, z0 - nz}, {x0 + nx, y0, z0 + nz}, {x0 + nx, y0 + t, z0 + nz}, {x0 - nx, y0 + t, z0 - nz},
                {x1 - nx, y1, z1 - nz}, {x1 + nx, y1, z1 + nz}, {x1 + nx, y1 + t, z1 + nz}, {x1 - nx, y1 + t, z1 - nz}};
    }

    static BoxModel of(AircraftType type) {
        return switch (type) {
            case REAPER -> reaper();
            case TB2 -> tb2();
            case GLOBAL_HAWK -> globalHawk();
            case ORLAN -> orlan();
        };
    }

    private AirModels() {}
}

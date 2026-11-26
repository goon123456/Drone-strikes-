package com.steelrain.client;

/**
 * Missiles, rockets, shells and bombs at real-world scale. Origin on the body axis at roughly the middle,
 * +Z is the nose.
 */
final class MunitionModels {
    private static final int NAVY_GREY = 0x9BA1A6, NAVY_DARK = 0x6E7479, RUS_GREEN = 0x56634A, RUS_DARK = 0x3C4634,
            ARMY_OLIVE = 0x5A6340, ARMY_DARK = 0x3F4630, WHITE = 0xDAD9D2, SAND = 0xB9AD8E, STEEL_D = 0x3E4246,
            BLACK = 0x18181A, YELLOW = 0xC8A82C, BROWN = 0x6A4A2C, BRASS = 0xC4A64C, COPPER = 0xB87040,
            GLASS = 0x28405A, RED = 0xBE2824, SHELL_GREEN = 0x4E5A3A, STEEL = 0x8A8E94;

    /** Fins spaced evenly around the body, from the body surface out to {@code reach} from the axis. */
    private static BoxModel fins(BoxModel m, int colour, int count, double startAngle, double body, double reach,
                                 double lead, double rootChord, double tipLead, double tipChord, double thick) {
        for (int i = 0; i < count; i++) {
            double a = startAngle + Math.PI * 2 * i / count, c = Math.cos(a), s = Math.sin(a);
            m.wing(colour, c * body * 0.9, s * body * 0.9, lead, rootChord, thick, c * reach, s * reach, tipLead, tipChord, thick * 0.6);
        }
        return m;
    }

    private static final double X = Math.PI / 4;   // fins set as an X rather than a +

    /** BGM-109 Tomahawk: 5.6 m, 0.52 m body, straight pop-out wings. */
    static BoxModel tomahawk() {
        BoxModel m = new BoxModel()
                .tube(NAVY_DARK, 2.8, 0, 0, 0.12, 0.12, 2.6, 0, 0, 0.34, 0.34)
                .tube(NAVY_GREY, 2.6, 0, 0, 0.34, 0.34, 2.1, 0, 0, 0.52, 0.52)
                .tube(NAVY_GREY, 0, 0, 2.1, -2.3, 0.52)
                .tube(NAVY_GREY, -2.3, 0, 0, 0.52, 0.52, -2.8, 0, 0, 0.36, 0.36)
                .tube(BLACK, -2.8, 0, 0, 0.26, 0.26, -2.86, 0, 0, 0.26, 0.26)
                .tube(NAVY_DARK, 0, 0, 1.55, 1.45, 0.535)                                    // paint bands
                .tube(YELLOW, 0, 0, 1.2, 1.14, 0.53)
                .loft(NAVY_DARK, -1.3, 0, -0.3, 0.26, 0.14, -2.1, 0, -0.33, 0.3, 0.2)        // engine air scoop
                .box(BLACK, -0.12, -0.39, -1.3, 0.24, 0.1, 0.02)
                .wings(NAVY_GREY, 0.24, -0.1, 0.05, 0.55, 0.05, 1.33, -0.1, -0.05, 0.4, 0.03);
        return fins(m, NAVY_GREY, 4, 0, 0.2, 0.62, -2.25, 0.5, -2.4, 0.3, 0.04);
    }

    /** 9M723 Iskander: 7.3 m, 0.92 m body, a long cone with four small fins. */
    static BoxModel iskander() {
        BoxModel m = new BoxModel()
                .tube(RUS_DARK, 3.75, 0, 0, 0.1, 0.1, 3.3, 0, 0, 0.3, 0.3)
                .tube(RUS_GREEN, 3.3, 0, 0, 0.3, 0.3, 1.9, 0, 0, 0.66, 0.66)
                .tube(RUS_GREEN, 1.9, 0, 0, 0.66, 0.66, 0.2, 0, 0, 0.92, 0.92)
                .tube(RUS_GREEN, 0, 0, 0.2, -3.2, 0.92)
                .tube(RUS_GREEN, -3.2, 0, 0, 0.92, 0.92, -3.55, 0, 0, 0.8, 0.8)
                .tube(BLACK, -3.55, 0, 0, 0.62, 0.62, -3.7, 0, 0, 0.7, 0.7)                  // nozzle
                .tube(RUS_DARK, 0, 0, 0.2, 0.08, 0.94)
                .tube(RUS_DARK, 0, 0, -1.6, -1.72, 0.94)
                .box(STEEL_D, -0.03, 0.44, -0.9, 0.06, 0.05, 1.6);                           // cable duct
        return fins(m, RUS_DARK, 4, X, 0.44, 0.82, -2.9, 0.65, -3.2, 0.35, 0.05);
    }

    /** MGM-140 ATACMS: 4 m, 0.61 m body. */
    static BoxModel atacms() {
        BoxModel m = new BoxModel()
                .tube(BLACK, 2.0, 0, 0, 0.08, 0.08, 1.8, 0, 0, 0.26, 0.26)
                .tube(ARMY_OLIVE, 1.8, 0, 0, 0.26, 0.26, 1.2, 0, 0, 0.5, 0.5)
                .tube(ARMY_OLIVE, 1.2, 0, 0, 0.5, 0.5, 0.6, 0, 0, 0.61, 0.61)
                .tube(ARMY_OLIVE, 0, 0, 0.6, -1.9, 0.61)
                .tube(BLACK, -1.9, 0, 0, 0.5, 0.5, -2.0, 0, 0, 0.44, 0.44)
                .tube(YELLOW, 0, 0, 0.5, 0.42, 0.625)
                .tube(BROWN, 0, 0, -0.6, -0.68, 0.625);
        return fins(m, ARMY_DARK, 4, X, 0.3, 0.68, -1.35, 0.55, -1.6, 0.3, 0.04);
    }

    /** M31 GMLRS: 3.9 m, 0.23 m body, small nose fins and a fin ring at the tail. */
    static BoxModel gmlrs() {
        BoxModel m = new BoxModel()
                .tube(STEEL_D, 1.97, 0, 0, 0.05, 0.05, 1.6, 0, 0, 0.2, 0.2)
                .tube(ARMY_OLIVE, 1.6, 0, 0, 0.2, 0.2, 1.3, 0, 0, 0.23, 0.23)
                .tube(ARMY_OLIVE, 0, 0, 1.3, -1.9, 0.23)
                .tube(BLACK, -1.9, 0, 0, 0.19, 0.19, -1.97, 0, 0, 0.19, 0.19)
                .tube(YELLOW, 0, 0, 1.05, 1.0, 0.24)
                .tube(BROWN, 0, 0, -0.3, -0.36, 0.24);
        fins(m, STEEL_D, 4, X, 0.1, 0.2, 1.5, 0.12, 1.47, 0.07, 0.015);
        return fins(m, ARMY_DARK, 4, X, 0.11, 0.3, -1.6, 0.34, -1.72, 0.2, 0.02);
    }

    /** AGM-114 Hellfire: 1.6 m, 0.18 m body, glass seeker nose. */
    static BoxModel hellfire() {
        BoxModel m = new BoxModel()
                .tube(GLASS, 0.82, 0, 0, 0.08, 0.08, 0.72, 0, 0, 0.17, 0.17)
                .tube(STEEL_D, 0, 0, 0.72, 0.62, 0.18)
                .tube(ARMY_OLIVE, 0, 0, 0.62, -0.78, 0.18)
                .tube(BLACK, 0, 0, -0.78, -0.82, 0.13)
                .tube(YELLOW, 0, 0, 0.3, 0.27, 0.185);
        fins(m, ARMY_DARK, 4, X, 0.09, 0.17, 0.45, 0.14, 0.42, 0.08, 0.015);
        return fins(m, ARMY_DARK, 4, X, 0.09, 0.2, -0.45, 0.33, -0.55, 0.22, 0.02);
    }

    /** 155 mm howitzer shell, drawn a little oversize so it can be seen in flight. */
    static BoxModel shell() {
        return new BoxModel()
                .tube(BRASS, 0.42, 0, 0, 0.04, 0.04, 0.3, 0, 0, 0.09, 0.09)                  // fuze
                .tube(SHELL_GREEN, 0.3, 0, 0, 0.09, 0.09, 0.05, 0, 0, 0.19, 0.19)
                .tube(SHELL_GREEN, 0, 0, 0.05, -0.3, 0.2)
                .tube(SHELL_GREEN, -0.3, 0, 0, 0.2, 0.2, -0.42, 0, 0, 0.16, 0.16)
                .tube(YELLOW, 0, 0, 0.0, -0.04, 0.205)
                .tube(COPPER, 0, 0, -0.24, -0.29, 0.21);                                     // driving band
    }

    /** 120 mm mortar bomb: a teardrop with a finned tail. */
    static BoxModel mortarBomb() {
        BoxModel m = new BoxModel()
                .tube(STEEL, 0.36, 0, 0, 0.03, 0.03, 0.28, 0, 0, 0.07, 0.07)
                .tube(SHELL_GREEN, 0.28, 0, 0, 0.07, 0.07, 0.1, 0, 0, 0.15, 0.15)
                .tube(SHELL_GREEN, 0, 0, 0.1, -0.02, 0.15)
                .tube(SHELL_GREEN, -0.02, 0, 0, 0.15, 0.15, -0.2, 0, 0, 0.05, 0.05)
                .tube(STEEL_D, 0, 0, -0.2, -0.36, 0.04)
                .tube(YELLOW, 0, 0, 0.07, 0.04, 0.155);
        return fins(m, STEEL_D, 6, 0, 0.02, 0.075, -0.24, 0.12, -0.26, 0.1, 0.008);
    }

    /** Pantsir 57E6: a fat booster that carries a thin dart. */
    static BoxModel pantsirMissile() {
        BoxModel m = new BoxModel()
                .tube(STEEL_D, 1.6, 0, 0, 0.02, 0.02, 1.3, 0, 0, 0.09, 0.09)
                .tube(RUS_GREEN, 0, 0, 1.3, -0.1, 0.09)
                .tube(RUS_GREEN, -0.1, 0, 0, 0.09, 0.09, -0.3, 0, 0, 0.17, 0.17)
                .tube(WHITE, 0, 0, -0.3, -1.55, 0.17)
                .tube(BLACK, 0, 0, -1.55, -1.6, 0.13);
        fins(m, STEEL_D, 4, X, 0.045, 0.13, -0.02, 0.16, -0.1, 0.08, 0.012);
        return fins(m, STEEL_D, 4, X, 0.085, 0.24, -1.3, 0.25, -1.42, 0.13, 0.015);
    }

    /** Patriot PAC-2: 5.3 m, 0.41 m body, pointed white nose. */
    static BoxModel patriotMissile() {
        BoxModel m = new BoxModel()
                .tube(WHITE, 2.65, 0, 0, 0.04, 0.04, 2.2, 0, 0, 0.22, 0.22)
                .tube(WHITE, 2.2, 0, 0, 0.22, 0.22, 1.5, 0, 0, 0.41, 0.41)
                .tube(SAND, 0, 0, 1.5, -2.6, 0.41)
                .tube(BLACK, 0, 0, -2.6, -2.65, 0.33)
                .tube(YELLOW, 0, 0, 1.2, 1.14, 0.42)
                .tube(BROWN, 0, 0, -0.8, -0.87, 0.42);
        return fins(m, SAND, 4, X, 0.2, 0.46, -2.0, 0.6, -2.3, 0.3, 0.035);
    }

    /** Hand grenade with a printed tail, as dropped by small drones; also used for cluster bomblets. */
    static BoxModel grenade() {
        BoxModel m = new BoxModel()
                .tube(BRASS, 0.13, 0, 0, 0.03, 0.03, 0.09, 0, 0, 0.05, 0.05)
                .tube(SHELL_GREEN, 0.09, 0, 0, 0.07, 0.07, 0.05, 0, 0, 0.12, 0.12)
                .tube(SHELL_GREEN, 0, 0, 0.05, -0.04, 0.12)
                .tube(SHELL_GREEN, -0.04, 0, 0, 0.12, 0.12, -0.08, 0, 0, 0.05, 0.05)
                .tube(WHITE, 0, 0, -0.08, -0.17, 0.035);
        return fins(m, WHITE, 4, X, 0.015, 0.075, -0.1, 0.07, -0.11, 0.06, 0.008);
    }

    private MunitionModels() {}
}

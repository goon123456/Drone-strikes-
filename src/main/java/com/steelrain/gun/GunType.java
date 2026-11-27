package com.steelrain.gun;

import java.util.EnumSet;
import java.util.Set;

/** Every small arm in the mod and the numbers that make it behave like itself. */
public enum GunType {
    AK74M("ak74m", Caliber.R545, 30, 650, new int[]{Mode.SAFE, Mode.AUTO, Mode.SEMI}, Action.CLOSED_BOLT,
            0.62f, 0.30f, 3.0f, 230, 50, 66, range(100, 1000), 1.0, 3.4f,
            EnumSet.of(Attachment.RED_DOT, Attachment.PSO1, Attachment.SUPPRESSOR, Attachment.FOREGRIP)),
    M4A1("m4a1", Caliber.R556, 30, 800, new int[]{Mode.SAFE, Mode.SEMI, Mode.AUTO}, Action.CLOSED_BOLT,
            0.55f, 0.26f, 2.0f, 210, 44, 54, range(100, 600), 1.0, 2.9f,
            EnumSet.of(Attachment.RED_DOT, Attachment.ACOG, Attachment.SCOPE10, Attachment.SUPPRESSOR, Attachment.FOREGRIP)),
    SVD("svd", Caliber.R762, 10, 260, new int[]{Mode.SAFE, Mode.SEMI}, Action.CLOSED_BOLT,
            1.70f, 0.40f, 1.2f, 320, 56, 70, range(100, 1200), 1.0, 4.3f,
            EnumSet.of(Attachment.PSO1, Attachment.SCOPE10, Attachment.SUPPRESSOR)),
    PKM("pkm", Caliber.R762, 100, 650, new int[]{Mode.SAFE, Mode.AUTO}, Action.OPEN_BOLT,
            0.80f, 0.48f, 4.0f, 380, 130, 130, range(100, 1500), 0.995, 7.5f,
            EnumSet.noneOf(Attachment.class)),
    M17("m17", Caliber.P9, 17, 420, new int[]{Mode.SAFE, Mode.SEMI}, Action.CLOSED_BOLT,
            1.35f, 0.55f, 6.0f, 150, 32, 40, new int[]{25}, 1.0, 0.8f,
            EnumSet.of(Attachment.PISTOL_SUPPRESSOR)),
    M590("m590", Caliber.G12, 8, 70, new int[]{Mode.SAFE, Mode.SEMI}, Action.PUMP,
            4.50f, 1.20f, 10.0f, 250, 11, 11, new int[]{50}, 1.0, 3.3f,
            EnumSet.noneOf(Attachment.class)),
    M107("m107", Caliber.B50, 10, 90, new int[]{Mode.SAFE, Mode.SEMI}, Action.CLOSED_BOLT,
            6.00f, 1.10f, 1.5f, 480, 72, 90, range(100, 1800), 1.0, 14.0f,
            EnumSet.of(Attachment.SCOPE10)),
    RPG7("rpg7", Caliber.PG7V, 1, 60, new int[]{Mode.SAFE, Mode.SEMI}, Action.SINGLE,
            2.20f, 0.60f, 14.0f, 300, 70, 70, range(100, 500), 1.0, 6.3f,
            EnumSet.noneOf(Attachment.class)),
    M18("m18", Caliber.P9, 17, 430, new int[]{Mode.SAFE, Mode.SEMI}, Action.CLOSED_BOLT,
            1.45f, 0.60f, 6.5f, 140, 32, 40, new int[]{25}, 0.97, 0.75f,
            EnumSet.of(Attachment.PISTOL_SUPPRESSOR)),
    M9("m9", Caliber.P9, 15, 400, new int[]{Mode.SAFE, Mode.SEMI}, Action.CLOSED_BOLT,
            1.30f, 0.55f, 6.0f, 150, 34, 42, new int[]{25}, 1.0, 0.95f,
            EnumSet.of(Attachment.PISTOL_SUPPRESSOR)),
    HK416("hk416", Caliber.R556, 30, 850, new int[]{Mode.SAFE, Mode.SEMI, Mode.AUTO}, Action.CLOSED_BOLT,
            0.52f, 0.24f, 1.8f, 215, 46, 56, range(100, 600), 0.99, 3.5f,
            EnumSet.of(Attachment.RED_DOT, Attachment.ACOG, Attachment.SCOPE10, Attachment.SUPPRESSOR, Attachment.FOREGRIP)),
    G36("g36", Caliber.R556, 30, 750, new int[]{Mode.SAFE, Mode.SEMI, Mode.AUTO}, Action.CLOSED_BOLT,
            0.50f, 0.24f, 2.2f, 220, 48, 60, range(100, 600), 1.02, 3.6f,
            EnumSet.of(Attachment.RED_DOT, Attachment.ACOG, Attachment.SUPPRESSOR, Attachment.FOREGRIP)),
    SCARH("scarh", Caliber.R76251, 20, 600, new int[]{Mode.SAFE, Mode.SEMI, Mode.AUTO}, Action.CLOSED_BOLT,
            1.00f, 0.42f, 1.6f, 260, 52, 64, range(100, 800), 0.94, 3.9f,
            EnumSet.of(Attachment.RED_DOT, Attachment.ACOG, Attachment.SCOPE10, Attachment.SUPPRESSOR, Attachment.FOREGRIP)),
    M249("m249", Caliber.R556, 200, 800, new int[]{Mode.SAFE, Mode.AUTO}, Action.OPEN_BOLT,
            0.45f, 0.34f, 3.5f, 360, 110, 110, range(100, 1000), 1.03, 7.5f,
            EnumSet.of(Attachment.RED_DOT, Attachment.ACOG)),
    M240("m240", Caliber.R76251, 100, 650, new int[]{Mode.SAFE, Mode.AUTO}, Action.OPEN_BOLT,
            0.78f, 0.45f, 3.0f, 400, 124, 124, range(100, 1100), 1.02, 12.5f,
            EnumSet.of(Attachment.RED_DOT, Attachment.ACOG)),
    M110("m110", Caliber.R76251, 20, 300, new int[]{Mode.SAFE, Mode.SEMI}, Action.CLOSED_BOLT,
            1.40f, 0.36f, 1.0f, 300, 60, 72, range(100, 1000), 0.94, 4.9f,
            EnumSet.of(Attachment.RED_DOT, Attachment.ACOG, Attachment.SCOPE10, Attachment.SUPPRESSOR)),
    // ---- V5 pistols ----
    GLOCK17("glock17", Caliber.P9, 17, 400, new int[]{Mode.SAFE, Mode.SEMI}, Action.CLOSED_BOLT, 1.30f, 0.55f, 6.0f, 140, 32, 40, new int[]{25}, 1.0, 0.63f, EnumSet.of(Attachment.PISTOL_SUPPRESSOR)),
    GLOCK19("glock19", Caliber.P9, 15, 400, new int[]{Mode.SAFE, Mode.SEMI}, Action.CLOSED_BOLT, 1.45f, 0.60f, 6.5f, 135, 32, 40, new int[]{25}, 0.97, 0.60f, EnumSet.of(Attachment.PISTOL_SUPPRESSOR)),
    P99("p99", Caliber.P9, 15, 400, new int[]{Mode.SAFE, Mode.SEMI}, Action.CLOSED_BOLT, 1.35f, 0.56f, 6.0f, 145, 34, 42, new int[]{25}, 1.0, 0.63f, EnumSet.of(Attachment.PISTOL_SUPPRESSOR)),
    HK_USP("hk_usp", Caliber.P9, 15, 380, new int[]{Mode.SAFE, Mode.SEMI}, Action.CLOSED_BOLT, 1.30f, 0.55f, 5.8f, 150, 34, 42, new int[]{25}, 1.0, 0.75f, EnumSet.of(Attachment.PISTOL_SUPPRESSOR)),
    FIVE_SEVEN("five_seven", Caliber.P57, 20, 430, new int[]{Mode.SAFE, Mode.SEMI}, Action.CLOSED_BOLT, 1.00f, 0.45f, 5.0f, 145, 32, 40, new int[]{50}, 1.0, 0.62f, EnumSet.of(Attachment.PISTOL_SUPPRESSOR)),
    M1911A1("m1911a1", Caliber.ACP45, 7, 360, new int[]{Mode.SAFE, Mode.SEMI}, Action.CLOSED_BOLT, 1.70f, 0.65f, 6.0f, 150, 36, 44, new int[]{25}, 1.0, 1.1f, EnumSet.of(Attachment.PISTOL_SUPPRESSOR)),
    HI_POWER("hi_power", Caliber.P9, 13, 400, new int[]{Mode.SAFE, Mode.SEMI}, Action.CLOSED_BOLT, 1.35f, 0.56f, 6.0f, 150, 36, 44, new int[]{25}, 1.0, 0.9f, EnumSet.of(Attachment.PISTOL_SUPPRESSOR)),
    CZ75("cz75", Caliber.P9, 16, 400, new int[]{Mode.SAFE, Mode.SEMI}, Action.CLOSED_BOLT, 1.25f, 0.52f, 5.8f, 150, 36, 44, new int[]{25}, 1.0, 1.0f, EnumSet.of(Attachment.PISTOL_SUPPRESSOR)),
    P226("p226", Caliber.P9, 15, 400, new int[]{Mode.SAFE, Mode.SEMI}, Action.CLOSED_BOLT, 1.30f, 0.55f, 5.8f, 148, 36, 44, new int[]{25}, 1.0, 0.96f, EnumSet.of(Attachment.PISTOL_SUPPRESSOR)),
    P228("p228", Caliber.P9, 13, 420, new int[]{Mode.SAFE, Mode.SEMI}, Action.CLOSED_BOLT, 1.40f, 0.58f, 6.2f, 140, 34, 42, new int[]{25}, 0.98, 0.83f, EnumSet.of(Attachment.PISTOL_SUPPRESSOR));

    /** Fire selector positions. */
    public static final class Mode {
        public static final int SAFE = 0, SEMI = 1, AUTO = 2;
        public static final String[] KEY = {"safe", "semi", "auto"};
        private Mode() {}
    }

    public enum Action {
        /** Fires with a round already chambered; a fresh magazine on an empty gun needs the bolt worked. */
        CLOSED_BOLT,
        /** The bolt stays back between shots and strips each round off the belt as it fires. */
        OPEN_BOLT,
        /** A tube magazine loaded one shell at a time, worked by hand after every shot. */
        PUMP,
        /** One round pushed in at the muzzle and fired from where it sits: the rocket launcher. */
        SINGLE
    }

    public final String id;
    public final Caliber caliber;
    public final int capacity, rpm;
    public final int[] modes;
    public final Action action;
    /** Muzzle climb and sideways wander per shot, in degrees. */
    public final float recoilUp, recoilSide;
    /** Mechanical accuracy: the size of a group in minutes of angle. */
    public final float moa;
    /** Milliseconds to bring the sights up. */
    public final int adsMs;
    /** Ticks to change a magazine with a round still chambered, and from empty. For a pump gun, ticks per shell. */
    public final int reloadTicks, reloadEmptyTicks;
    /** Ranges (m) the iron sights can be set to. */
    public final int[] zeros;
    public final double velocityFactor;
    public final float weightKg;
    public final Set<Attachment> fits;

    GunType(String id, Caliber caliber, int capacity, int rpm, int[] modes, Action action, float recoilUp, float recoilSide, float moa,
            int adsMs, int reloadTicks, int reloadEmptyTicks, int[] zeros, double velocityFactor, float weightKg, Set<Attachment> fits) {
        this.id = id; this.caliber = caliber; this.capacity = capacity; this.rpm = rpm; this.modes = modes; this.action = action;
        this.recoilUp = recoilUp; this.recoilSide = recoilSide; this.moa = moa; this.adsMs = adsMs;
        this.reloadTicks = reloadTicks; this.reloadEmptyTicks = reloadEmptyTicks; this.zeros = zeros;
        this.velocityFactor = velocityFactor; this.weightKg = weightKg; this.fits = fits;
    }

    private static int[] range(int from, int to) {
        int[] r = new int[(to - from) / 100 + 1];
        for (int i = 0; i < r.length; i++) r[i] = from + i * 100;
        return r;
    }

    /** Ticks between shots. */
    public double interval() { return 1200.0 / rpm; }

    /** Whether a fitting goes on this gun: what it is listed with, and what shares a mount with those. */
    public boolean accepts(Attachment a) {
        if (fits.contains(a)) return true;
        return switch (a) {
            case HOLO, MAGNIFIER -> fits.contains(Attachment.RED_DOT);
            case LPVO -> fits.contains(Attachment.ACOG) || this == AK74M;
            case NV_SCOPE, THERMAL -> fits.contains(Attachment.ACOG) || fits.contains(Attachment.PSO1) || fits.contains(Attachment.SCOPE10);
            case ANGLED_GRIP -> fits.contains(Attachment.FOREGRIP);
            case BIPOD -> fits.contains(Attachment.FOREGRIP) || this == SVD || this == M110;
            case FLASHLIGHT, LASER -> fits.contains(Attachment.RED_DOT) || pistol() || this == M590 || this == M110;
            default -> false;
        };
    }

    public boolean pistol() {
        return switch (this) {
            case M17, M18, M9, GLOCK17, GLOCK19, P99, HK_USP, FIVE_SEVEN, M1911A1, HI_POWER, CZ75, P226, P228 -> true;
            default -> false;
        };
    }
    /** Fed from a belt in a box hung on the gun, under a feed cover that has to be lifted. */
    public boolean beltFed() { return this == PKM || this == M249 || this == M240; }
    /** Which side the belt goes in: 1 for the firer's left, -1 for the right (the PKM). */
    public int feedSide() { return this == PKM ? -1 : 1; }
    public boolean bipod() { return beltFed() || this == M107; }
    /** After an empty reload the bolt is drawn back by hand (others drop it with a catch). */
    public boolean handRacked() { return this == AK74M || this == SVD || this == SCARH || this == G36 || beltFed(); }
    /** The bolt or slide stays back when the last round has gone. */
    public boolean holdsOpen() { return pistol() || this == M4A1 || this == HK416 || this == M110 || this == SCARH || this == G36 || this == M107; }
    /** The magazine is rocked in on a front lug, as on the Kalashnikov. */
    public boolean rocksMagazine() { return this == AK74M || this == SVD; }
    /** Fires straight from what feeds it, with no chamber to keep a round in. */
    public boolean firesFromFeed() { return action == Action.OPEN_BOLT || action == Action.SINGLE; }
    /** The kinds of round this gun can be loaded with, the first being its usual one. */
    public Caliber[] loads() { return this == RPG7 ? new Caliber[]{Caliber.PG7V, Caliber.OG7V} : new Caliber[]{caliber}; }
    /** Feeds from a detachable magazine or belt box (the pump gun is loaded shell by shell). */
    public boolean magazineFed() { return action != Action.PUMP && action != Action.SINGLE; }
    /** Which rounds of a magazine are tracers: every n-th (machine gun belts), or none. */
    public int tracerEvery() { return beltFed() ? 5 : 0; }
    /** The last few rounds of a rifle magazine are tracers, to warn that it is nearly empty. */
    public int tracerLast() { return this == AK74M || this == M4A1 || this == HK416 || this == G36 || this == SCARH ? 3 : 0; }
    /** When in a reload the old magazine comes out and the new one goes in, as fractions of the whole. */
    public float magOutAt() { return beltFed() ? 0.30f : 0.22f; }
    public float magInAt(boolean empty) { return beltFed() ? 0.62f : empty ? 0.56f : 0.70f; }
    /** When the bolt is worked at the end of a reload from empty. */
    public float boltAt() { return beltFed() ? 0.88f : 0.80f; }
    /** How far the bolt, slide or pump travels (m). */
    public float boltTravel() {
        return switch (this) { case M17, M18, M9, GLOCK17, GLOCK19, P99, HK_USP, FIVE_SEVEN, M1911A1, HI_POWER, CZ75, P226, P228 -> 0.042f; case HK416, M110 -> 0.070f; case M249, M240 -> 0.100f; case M590 -> 0.090f; case M4A1 -> 0.070f; case PKM -> 0.110f; case M107 -> 0.120f; case RPG7 -> 0f; default -> 0.090f; };
    }

    public static final GunType[] VALUES = values();
}

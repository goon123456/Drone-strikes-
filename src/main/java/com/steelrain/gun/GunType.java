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
            EnumSet.of(Attachment.RED_DOT, Attachment.ACOG, Attachment.SUPPRESSOR, Attachment.FOREGRIP)),
    SVD("svd", Caliber.R762, 10, 260, new int[]{Mode.SAFE, Mode.SEMI}, Action.CLOSED_BOLT,
            1.70f, 0.40f, 1.2f, 320, 56, 70, range(100, 1200), 1.0, 4.3f,
            EnumSet.of(Attachment.PSO1, Attachment.SUPPRESSOR)),
    PKM("pkm", Caliber.R762, 100, 650, new int[]{Mode.SAFE, Mode.AUTO}, Action.OPEN_BOLT,
            0.80f, 0.48f, 4.0f, 380, 130, 130, range(100, 1500), 0.995, 7.5f,
            EnumSet.noneOf(Attachment.class)),
    M17("m17", Caliber.P9, 17, 420, new int[]{Mode.SAFE, Mode.SEMI}, Action.CLOSED_BOLT,
            1.35f, 0.55f, 6.0f, 150, 32, 40, new int[]{25}, 1.0, 0.8f,
            EnumSet.of(Attachment.PISTOL_SUPPRESSOR)),
    M590("m590", Caliber.G12, 8, 70, new int[]{Mode.SAFE, Mode.SEMI}, Action.PUMP,
            4.50f, 1.20f, 10.0f, 250, 11, 11, new int[]{50}, 1.0, 3.3f,
            EnumSet.noneOf(Attachment.class));

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
        PUMP
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

    public boolean pistol() { return this == M17; }
    public boolean bipod() { return this == PKM; }
    /** Feeds from a detachable magazine or belt box (the pump gun is loaded shell by shell). */
    public boolean magazineFed() { return action != Action.PUMP; }
    /** Which rounds of a magazine are tracers: every n-th (machine gun belts), or none. */
    public int tracerEvery() { return this == PKM ? 5 : 0; }
    /** The last few rounds of a rifle magazine are tracers, to warn that it is nearly empty. */
    public int tracerLast() { return this == AK74M || this == M4A1 ? 3 : 0; }
    /** When in a reload the old magazine comes out and the new one goes in, as fractions of the whole. */
    public float magOutAt() { return this == PKM ? 0.30f : 0.22f; }
    public float magInAt(boolean empty) { return this == PKM ? 0.62f : empty ? 0.56f : 0.70f; }
    /** When the bolt is worked at the end of a reload from empty. */
    public float boltAt() { return this == PKM ? 0.88f : 0.80f; }
    /** How far the bolt, slide or pump travels (m). */
    public float boltTravel() {
        return switch (this) { case M17 -> 0.042f; case M590 -> 0.090f; case M4A1 -> 0.070f; case PKM -> 0.110f; default -> 0.090f; };
    }

    public static final GunType[] VALUES = values();
}

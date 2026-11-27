package com.steelrain.gun;

/**
 * A cartridge: how fast its bullet leaves a standard barrel, how quickly the air slows it, how hard it hits and how
 * much cover it goes through. Distances are in blocks (metres), times in ticks.
 */
public enum Caliber {
    //       name         m/s   drag/m   damage  wood it pierces (blocks)  pellets
    R545("5.45x39", "ammo_545", 880, 0.00130, 7.5f, 0.40f, 1),
    R556("5.56x45", "ammo_556", 890, 0.00145, 7.5f, 0.40f, 1),
    R762("7.62x54R", "ammo_762", 830, 0.00100, 12.5f, 1.10f, 1),
    P9("9x19", "ammo_9mm", 375, 0.00220, 5.0f, 0.12f, 1),
    G12("12 gauge", "ammo_12g", 400, 0.00900, 3.4f, 0.05f, 8);

    /** Gravity in blocks per tick squared (9.81 m/s2). */
    public static final double GRAVITY = 9.81 / 400.0;

    public final String label, ammoId;
    public final int metresPerSecond, pellets;
    public final double drag;
    public final float damage, pierce;

    Caliber(String label, String ammoId, int mps, double drag, float damage, float pierce, int pellets) {
        this.label = label; this.ammoId = ammoId; this.metresPerSecond = mps; this.drag = drag;
        this.damage = damage; this.pierce = pierce; this.pellets = pellets;
    }

    /** Muzzle velocity in blocks per tick. */
    public double speed() { return metresPerSecond / 20.0; }

    /** The velocity one tick later: air drag takes a fixed share per metre flown, gravity pulls down. */
    public net.minecraft.world.phys.Vec3 advance(net.minecraft.world.phys.Vec3 v) {
        double keep = Math.exp(-drag * v.length());
        return new net.minecraft.world.phys.Vec3(v.x * keep, v.y * keep - GRAVITY, v.z * keep);
    }

    /**
     * How far above the line of sight (radians) the barrel must point for the bullet to cross it again at
     * {@code range} blocks, found by flying the same steps the real bullet does.
     */
    public double elevation(double range, double velocityFactor) {
        long key = Math.round(range) * 1000 + Math.round(velocityFactor * 100);
        Double known = ELEVATION[ordinal()].get(key);
        if (known != null) return known;
        double lo = 0, hi = Math.toRadians(8);
        for (int i = 0; i < 40; i++) {
            double mid = (lo + hi) / 2;
            if (heightAt(range, mid, velocityFactor) < 0) lo = mid; else hi = mid;
        }
        ELEVATION[ordinal()].put(key, hi);
        return hi;
    }

    /** Height of the bullet relative to the line of sight when it has gone {@code range} blocks forward. */
    public double heightAt(double range, double angle, double velocityFactor) {
        double s = speed() * velocityFactor, x = 0, y = 0;
        net.minecraft.world.phys.Vec3 v = new net.minecraft.world.phys.Vec3(Math.cos(angle) * s, Math.sin(angle) * s, 0);
        for (int t = 0; t < 400; t++) {
            if (x + v.x >= range) return y + v.y * (range - x) / v.x;
            x += v.x; y += v.y;
            v = advance(v);
            if (v.x < 0.5) break;
        }
        return -1e6;
    }

    /** Remaining speed (m/s) and time of flight (s) at a range, for the tooltips and the range card. */
    public double[] at(double range, double velocityFactor) {
        double s = speed() * velocityFactor, x = 0;
        int t = 0;
        while (x < range && t < 400 && s > 0.5) { x += s; s *= Math.exp(-drag * s); t++; }
        return new double[]{s * 20, t / 20.0};
    }

    @SuppressWarnings("unchecked")
    private static final java.util.Map<Long, Double>[] ELEVATION = new java.util.Map[5];
    static { for (int i = 0; i < ELEVATION.length; i++) ELEVATION[i] = new java.util.concurrent.ConcurrentHashMap<>(); }
}
